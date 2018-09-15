/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package androidx.animation;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.TypedArray;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.InflateException;

import androidx.annotation.FloatRange;
import androidx.core.graphics.PathParser;


/**
 * An interpolator that can traverse a Path that extends from <code>Point</code>
 * <code>(0, 0)</code> to <code>(1, 1)</code>. The x coordinate along the <code>Path</code>
 * is the input value and the output is the y coordinate of the line at that point.
 * This means that the Path must conform to a function <code>y = f(x)</code>.
 *
 * <p>The <code>Path</code> must not have gaps in the x direction and must not
 * loop back on itself such that there can be two points sharing the same x coordinate.
 * It is alright to have a disjoint line in the vertical direction:</p>
 * <p><blockquote><pre>
 *     Path path = new Path();
 *     path.lineTo(0.25f, 0.25f);
 *     path.moveTo(0.25f, 0.5f);
 *     path.lineTo(1f, 1f);
 * </pre></blockquote></p>
 */
public class PathInterpolator implements Interpolator {

    // This governs how accurate the approximation of the Path is.
    private static final float PRECISION = 0.002f;
    private static final float EPSILON = 0.01f;

    private float[] mData;

    /**
     * Create an interpolator for an arbitrary <code>Path</code>. The <code>Path</code>
     * must begin at <code>(0, 0)</code> and end at <code>(1, 1)</code>.
     *
     * @param path The <code>Path</code> to use to make the line representing the interpolator.
     */
    public PathInterpolator(Path path) {
        initPath(path);
    }

    /**
     * Create an interpolator for a quadratic Bezier curve. The end points
     * <code>(0, 0)</code> and <code>(1, 1)</code> are assumed.
     *
     * @param controlX The x coordinate of the quadratic Bezier control point.
     * @param controlY The y coordinate of the quadratic Bezier control point.
     */
    public PathInterpolator(float controlX, float controlY) {
        initQuad(controlX, controlY);
    }

    /**
     * Create an interpolator for a cubic Bezier curve.  The end points
     * <code>(0, 0)</code> and <code>(1, 1)</code> are assumed.
     *
     * @param controlX1 The x coordinate of the first control point of the cubic Bezier.
     * @param controlY1 The y coordinate of the first control point of the cubic Bezier.
     * @param controlX2 The x coordinate of the second control point of the cubic Bezier.
     * @param controlY2 The y coordinate of the second control point of the cubic Bezier.
     */
    public PathInterpolator(float controlX1, float controlY1, float controlX2, float controlY2) {
        initCubic(controlX1, controlY1, controlX2, controlY2);
    }

    public PathInterpolator(Context context, AttributeSet attrs) {
        this(context.getResources(), context.getTheme(), attrs);
    }

    PathInterpolator(Resources res, Theme theme, AttributeSet attrs) {
        TypedArray a;
        if (theme != null) {
            a = theme.obtainStyledAttributes(attrs, AndroidResources.STYLEABLE_PATH_INTERPOLATOR,
                    0, 0);
        } else {
            a = res.obtainAttributes(attrs, AndroidResources.STYLEABLE_PATH_INTERPOLATOR);
        }
        parseInterpolatorFromTypeArray(a);
        a.recycle();
    }

    private void parseInterpolatorFromTypeArray(TypedArray a) {
        // If there is pathData defined in the xml file, then the controls points
        // will be all coming from pathData.
        if (a.hasValue(AndroidResources.STYLEABLE_PATH_INTERPOLATOR_PATH_DATA)) {
            String pathData = a.getString(AndroidResources.STYLEABLE_PATH_INTERPOLATOR_PATH_DATA);
            Path path = PathParser.createPathFromPathData(pathData);
            if (path == null) {
                throw new InflateException("The path is null, which is created"
                        + " from " + pathData);
            }
            initPath(path);
        } else {
            if (!a.hasValue(AndroidResources.STYLEABLE_PATH_INTERPOLATOR_CONTROL_X_1)) {
                throw new InflateException("pathInterpolator requires the controlX1 attribute");
            } else if (!a.hasValue(AndroidResources.STYLEABLE_PATH_INTERPOLATOR_CONTROL_Y_1)) {
                throw new InflateException("pathInterpolator requires the controlY1 attribute");
            }
            float x1 = a.getFloat(AndroidResources.STYLEABLE_PATH_INTERPOLATOR_CONTROL_X_1, 0);
            float y1 = a.getFloat(AndroidResources.STYLEABLE_PATH_INTERPOLATOR_CONTROL_Y_1, 0);

            boolean hasX2 = a.hasValue(AndroidResources.STYLEABLE_PATH_INTERPOLATOR_CONTROL_X_2);
            boolean hasY2 = a.hasValue(AndroidResources.STYLEABLE_PATH_INTERPOLATOR_CONTROL_Y_2);

            if (hasX2 != hasY2) {
                throw new InflateException(
                        "pathInterpolator requires both controlX2 and controlY2 for cubic Beziers."
                );
            }

            if (!hasX2) {
                initQuad(x1, y1);
            } else {
                float x2 = a.getFloat(AndroidResources.STYLEABLE_PATH_INTERPOLATOR_CONTROL_X_2, 0);
                float y2 = a.getFloat(AndroidResources.STYLEABLE_PATH_INTERPOLATOR_CONTROL_Y_2, 0);
                initCubic(x1, y1, x2, y2);
            }
        }
    }

    private void initQuad(float controlX, float controlY) {
        Path path = new Path();
        path.moveTo(0, 0);
        path.quadTo(controlX, controlY, 1f, 1f);
        initPath(path);
    }

    private void initCubic(float x1, float y1, float x2, float y2) {
        Path path = new Path();
        path.moveTo(0, 0);
        path.cubicTo(x1, y1, x2, y2, 1f, 1f);
        initPath(path);
    }

    private void initPath(Path path) {
        mData = PathUtils.createKeyFrameData(path, PRECISION);

        int numPoints = getNumOfPoints();

        // Sanity check
        if (!floatEquals(getXAtIndex(0), 0f) || !floatEquals(getYAtIndex(0), 0f)
                || !floatEquals(getXAtIndex(numPoints - 1), 1f)
                || !floatEquals(getYAtIndex(numPoints - 1), 1f)) {
            throw new IllegalArgumentException("The Path must start at (0,0) and end at (1,1)");
        }

        float prevX = 0;
        float prevFraction = 0;
        for (int i = 0; i < numPoints; i++) {
            float fraction = getFractionAtIndex(i);
            float x = getXAtIndex(i);
            if (fraction == prevFraction && x != prevX) {
                throw new IllegalArgumentException(
                        "The Path cannot have discontinuity in the X axis.");
            }
            if (x < prevX) {
                throw new IllegalArgumentException("The Path cannot loop back on itself.");
            }
            prevFraction = fraction;
            prevX = x;
        }
    }

    /**
     * Using the line in the Path in this interpolator that can be described as
     * <code>y = f(x)</code>, finds the y coordinate of the line given <code>t</code>
     * as the x coordinate. Values less than 0 will always return 0 and values greater
     * than 1 will always return 1.
     *
     * @param t Treated as the x coordinate along the line.
     * @return The y coordinate of the Path along the line where x = <code>t</code>.
     * @see Interpolator#getInterpolation(float)
     */
    @Override
    public float getInterpolation(@FloatRange(from = 0, to = 1) float t) {
        if (t <= 0) {
            return 0;
        } else if (t >= 1) {
            return 1;
        }
        // Do a binary search for the correct x to interpolate between.
        int startIndex = 0;
        int endIndex = getNumOfPoints() - 1;

        while (endIndex - startIndex > 1) {
            int midIndex = (startIndex + endIndex) / 2;
            if (t < getXAtIndex(midIndex)) {
                endIndex = midIndex;
            } else {
                startIndex = midIndex;
            }
        }

        float xRange = getXAtIndex(endIndex) - getXAtIndex(startIndex);
        if (xRange == 0) {
            return getYAtIndex(startIndex);
        }

        float tInRange = t - getXAtIndex(startIndex);
        float fraction = tInRange / xRange;

        float startY = getYAtIndex(startIndex);
        float endY = getYAtIndex(endIndex);
        return startY + (fraction * (endY - startY));
    }

    private float getFractionAtIndex(int index) {
        return mData[3 * index];
    }

    private float getXAtIndex(int index) {
        return mData[3 * index + 1];
    }

    private float getYAtIndex(int index) {
        return mData[3 * index + 2];
    }

    private int getNumOfPoints() {
        return mData.length / 3;
    }

    private static boolean floatEquals(float a, float b) {
        return Math.abs(a - b) < EPSILON;
    }

}

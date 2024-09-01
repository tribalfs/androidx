/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.appcompat.util;

import static android.util.TypedValue.TYPE_FIRST_COLOR_INT;
import static android.util.TypedValue.TYPE_LAST_COLOR_INT;

import static androidx.core.graphics.PathParser.nodesToPath;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.R;
import androidx.core.graphics.Insets;
import androidx.core.graphics.PathParser;
import java.util.Locale;


/*
 * Original code by Samsung, all rights reserved to the original author.
 */

/**
 * Samsung Rounded Corners class.
 */
//@RequiresApi(api = 23)
public class SeslRoundedCorner {
    public static final int ROUNDED_CORNER_ALL = 15;
    public static final int ROUNDED_CORNER_BOTTOM_LEFT = 4;
    public static final int ROUNDED_CORNER_BOTTOM_RIGHT = 8;
    public static final int ROUNDED_CORNER_NONE = 0;
    public static final int ROUNDED_CORNER_TOP_LEFT = 1;
    public static final int ROUNDED_CORNER_TOP_RIGHT = 2;
    private static final String TAG = "SeslRoundedCorner";

    private static final Locale LOCALE = Locale.ENGLISH;
    private static final String CUBIC_BEZIER_CURVE_FORMAT = "C %f %f %f %f %f %f ";
    private static final String LINE_TO_FORMAT = "L %f %f ";
    private static final String PATH_SEGMENT_CLOSE = "Z";
    private static final String MOVE_TO_START = "M 0 0 ";

    private static final Float CONTROL_POINT_X_1 = 4.64f;
    private static final Float CONTROL_POINT_Y_1 = 67.45f;
    private static final Float CURVE_END_X = 13.36f;
    private static final Float CURVE_END_Y = 51.16f;
    private static final Float TANGENT_X_1 = 22.07f;
    private static final Float TANGENT_Y_1 = 34.86f;

    private static final float SHRINK_FACTOR_THRESHOLD = 0.5f;
    private static final float SHRINK_FACTOR_DENOMINATOR = 0.4f;
    private static final float SHRINK_FACTOR_MULTIPLIER = 0.13877845f;

    private static final float SCALE_FACTOR_THRESHOLD = 0.6f;
    private static final float SCALE_FACTOR_DENOMINATOR = 0.3f;
    private static final float SCALE_FACTOR_MULTIPLIER = 0.042454004f;

    @NonNull
    final SeslRoundedChunkingDrawable mTopLeftRound;
    @NonNull
    final SeslRoundedChunkingDrawable mTopRightRound;
    @NonNull
    final SeslRoundedChunkingDrawable mBottomLeftRound;
    @NonNull
    final SeslRoundedChunkingDrawable mBottomRightRound;
    @ColorInt
    private int mTopLeftRoundColor;
    @ColorInt
    private int mTopRightRoundColor;
    @ColorInt
    private int mBottomLeftRoundColor;
    @ColorInt
    private int mBottomRightRoundColor;
   
    final Rect mRoundedCornerBounds= new Rect();
    final int mRoundRadius;
    int mRoundedCornerMode;
    @Nullable
    private Insets mInsets= null;

    public SeslRoundedCorner(@NonNull Context context) {
        this(context, false);
    }
    
    public SeslRoundedCorner(@NonNull Context context, boolean unused) {
        Resources resources = context.getResources();

        mRoundRadius = resources.getDimensionPixelSize(R.dimen.sesl_rounded_corner_radius);

        final boolean isDarkMode = !SeslMisc.isLightTheme(context);

        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(androidx.appcompat.R.attr.roundedCornerColor,
                typedValue, true);
        final int roundColor;

        if (typedValue.resourceId > 0 && isColorType(typedValue.type)) {
            roundColor = resources.getColor(typedValue.resourceId);
        } else if (typedValue.data > 0 && isColorType(typedValue.type)) {
            roundColor = typedValue.data;
        } else{
            if (isDarkMode) {
                roundColor = resources.getColor(R.color.sesl_round_and_bgcolor_dark);
            } else {
                roundColor = resources.getColor(R.color.sesl_round_and_bgcolor_light);
            }
        }
        mBottomRightRoundColor = roundColor;
        mBottomLeftRoundColor = roundColor;
        mTopRightRoundColor = roundColor;
        mTopLeftRoundColor = roundColor;

        Paint paint = new Paint();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(mTopLeftRoundColor);

        mTopLeftRound = new SeslRoundedChunkingDrawable(mRoundRadius, paint, 0f);
        mTopRightRound = new SeslRoundedChunkingDrawable(mRoundRadius, paint, 90f);
        mBottomLeftRound = new SeslRoundedChunkingDrawable(mRoundRadius, paint, 270f);
        mBottomRightRound = new SeslRoundedChunkingDrawable(mRoundRadius, paint, 180f);
    }

    public void setRoundedCorners(int corners) {
        if ((corners & (-16 )) == 0) {
            mRoundedCornerMode = corners;
        } else {
            throw new IllegalArgumentException("Use wrong rounded corners to the param, corners ="
                    + " " + corners);
        }
    }

    public int getRoundedCorners() {
        return mRoundedCornerMode;
    }

    public void setRoundedCornerColor(int corners, @ColorInt int color) {
        if (corners == ROUNDED_CORNER_NONE) {
            throw new IllegalArgumentException("There is no rounded corner on = " + this);
        } else if ((corners & (-16)) == 0) {
            PorterDuffColorFilter pdcf = new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN);
            if ((corners & ROUNDED_CORNER_TOP_LEFT) != 0) {
                mTopLeftRoundColor = color;
                mTopLeftRound.setColorFilter(pdcf);
            }
            if ((corners & ROUNDED_CORNER_TOP_RIGHT) != 0) {
                mTopRightRoundColor = color;
                mTopRightRound.setColorFilter(pdcf);
            }
            if ((corners & ROUNDED_CORNER_BOTTOM_LEFT) != 0) {
                mBottomLeftRoundColor = color;
                mBottomLeftRound.setColorFilter(pdcf);
            }
            if ((corners & ROUNDED_CORNER_BOTTOM_RIGHT) != 0) {
                mBottomRightRoundColor = color;
                mBottomRightRound.setColorFilter(pdcf);
            }
        } else {
            throw new IllegalArgumentException("Use wrong rounded corners to the param, corners ="
                    + " " + corners);
        }
    }

    @ColorInt
    public int getRoundedCornerColor(int corner) {
        if (corner == ROUNDED_CORNER_NONE) {
            throw new IllegalArgumentException("There is no rounded corner on = " + this);
        } else if (corner != ROUNDED_CORNER_TOP_LEFT
                && corner != ROUNDED_CORNER_TOP_RIGHT
                && corner != ROUNDED_CORNER_BOTTOM_LEFT
                && corner != ROUNDED_CORNER_BOTTOM_RIGHT) {
            throw new IllegalArgumentException("Use multiple rounded corner as param on = " + this);
        }

        if ((corner & ROUNDED_CORNER_TOP_LEFT) != 0) {
            return mTopLeftRoundColor;
        }
        if ((corner & ROUNDED_CORNER_TOP_RIGHT) != 0) {
            return mTopRightRoundColor;
        }
        if ((corner & ROUNDED_CORNER_BOTTOM_LEFT) != 0) {
            return mBottomLeftRoundColor;
        }
        return mBottomRightRoundColor;
    }

    @RestrictTo({RestrictTo.Scope.LIBRARY_GROUP_PREFIX})
    public int getRoundedCornerRadius() {
        return mRoundRadius;
    }

    public void drawRoundedCorner(@NonNull Canvas canvas) {
        canvas.getClipBounds(mRoundedCornerBounds);
        drawRoundedCornerInternal(canvas);
    }

    private void drawRoundedCornerInternal(Canvas canvas) {
        final int left = mRoundedCornerBounds.left + (mInsets != null ? mInsets.left : 0);
        final int right = mRoundedCornerBounds.right - (mInsets != null ? mInsets.right : 0);
        final int top = mRoundedCornerBounds.top + (mInsets != null ? mInsets.top : 0);
        final int bottom = mRoundedCornerBounds.bottom - (mInsets != null ? mInsets.bottom : 0);

        if ((mRoundedCornerMode & ROUNDED_CORNER_TOP_LEFT) != 0) {
            mTopLeftRound.setBounds(left, top, left + mRoundRadius, mRoundRadius + top);
            mTopLeftRound.draw(canvas);
        }

        if ((mRoundedCornerMode & ROUNDED_CORNER_TOP_RIGHT) != 0) {
            mTopRightRound.setBounds(right - mRoundRadius, top, right, mRoundRadius + top);
            mTopRightRound.draw(canvas);
        }

        if ((mRoundedCornerMode & ROUNDED_CORNER_BOTTOM_LEFT) != 0) {
            mBottomLeftRound.setBounds(left, bottom - mRoundRadius, mRoundRadius + left, bottom);
            mBottomLeftRound.draw(canvas);
        }

        if ((mRoundedCornerMode & ROUNDED_CORNER_BOTTOM_RIGHT) != 0) {
            mBottomRightRound.setBounds(right - mRoundRadius, bottom - mRoundRadius, right, bottom);
            mBottomRightRound.draw(canvas);
        }

        if (mTopLeftRoundColor == mTopRightRoundColor && mTopLeftRoundColor == mBottomLeftRoundColor && mTopLeftRoundColor == mBottomRightRoundColor) {
            Paint paint = new Paint();
            paint.setColor(mTopLeftRoundColor);

            if (mInsets != null && mInsets.top > 0) {
                canvas.drawRect(new Rect(left - mInsets.left, top - mInsets.top, mInsets.right + right, top), paint);
            }

            if (mInsets != null && mInsets.bottom > 0) {
                canvas.drawRect(new Rect(left - mInsets.left, bottom, mInsets.right + right, mInsets.bottom + bottom), paint);
            }

            if (mInsets != null && mInsets.left > 0) {
                canvas.drawRect(new Rect(left - mInsets.left, top - mInsets.top, left, mInsets.bottom + bottom), paint);
            }

            if (mInsets == null || mInsets.right <= 0) {
                return;
            }

            canvas.drawRect(new Rect(right, top - mInsets.top, mInsets.right + right, bottom + mInsets.bottom), paint);
        }
    }

    @NonNull
    @Deprecated
    public static Path getSmoothCornerRectPath(float cornerRadius, float rectWidth, float rectHeight) {
        Log.w(TAG, "This method is deprecated. Use getSmoothCornerRectPath(float, float, float, float, float) instead.");
        return getSmoothCornerRectPath(cornerRadius, 0f, 0f, rectWidth, rectHeight);
    }

    @NonNull
    private static Path getSmoothCornerRectPath(float cornerRadius, float left, float top, float width, float height) {
        Path path = new Path();

        if (width > 0f && height > 0f) {
            // Calculate center points and smallest dimension
            final float recCenterX = width / 2f;
            final float recCenterY = height / 2f;
            final float smallestHalfDimension = Math.min(recCenterX, recCenterY);

            // Clamp the corner radius to a valid range
            final float clampedCornerRadius = Math.min(Math.max(cornerRadius, 0f), smallestHalfDimension);
            final float cornerScaleFactor = clampedCornerRadius / smallestHalfDimension;

            // Calculate smoothing factors based on the corner scale factor
            final float smoothingFactor1 = cornerScaleFactor > 0.5f
                    ? 1f - (Math.min(1f, (cornerScaleFactor - 0.5f) / 0.4f) * 0.13877845f)
                    : 1f;
            final float smoothingFactor2 = cornerScaleFactor > 0.6f
                    ? 1f + (Math.min(1f, (cornerScaleFactor - 0.6f) / 0.3f) * 0.042454004f)
                    : 1f;

            // Move to the starting point at the top middle of the rectangle
            path.moveTo(left + recCenterX, top);

            final float radiusFactor = clampedCornerRadius / 100f;

            // Calculate parameters for cubic bezier curves
            final float scaledCornerRadius = 128.19f * radiusFactor * smoothingFactor1;
            final float controlPointDistance = 83.62f * radiusFactor * smoothingFactor2;
            final float arcRadius = radiusFactor * 67.45f;
            final float controlPointOffsetY = radiusFactor * 4.64f;
            final float startControlPointX = radiusFactor * 51.16f;
            final float startControlPointY = radiusFactor * 13.36f;
            final float endControlPointX = radiusFactor * 34.86f;
            final float endControlPointY = radiusFactor * 22.07f;

            // Draw top right corner
            final float topRightCornerX = width - scaledCornerRadius;
            path.lineTo(Math.max(recCenterX, topRightCornerX) + left, top);

            // Draw top right side of the rectangle
            final float endX = left + width;
            final float topRightControlX1 = endX - controlPointDistance;
            final float topRightControlX2 = endX - arcRadius;
            final float topRightControlY2 = top + controlPointOffsetY;
            final float topRightControlX3 = endX - startControlPointX;
            final float topRightControlY3 = top + startControlPointY;
            path.cubicTo(topRightControlX1, top, topRightControlX2, topRightControlY2, topRightControlX3, topRightControlY3);

            final float topRightEndControlX1 = endX - endControlPointX;
            final float topRightEndControlY1 = top + endControlPointY;
            final float topRightEndControlX2 = endX - endControlPointY;
            final float topRightEndControlY2 = top + endControlPointX;
            final float topRightEndControlX3 = endX - startControlPointY;
            final float topRightEndControlY3 = top + startControlPointX;
            path.cubicTo(topRightEndControlX1, topRightEndControlY1, topRightEndControlX2,
                    topRightEndControlY2, topRightEndControlX3, topRightEndControlY3);

            // Draw right edge of the rectangle
            final float rightEdgeControlX1 = endX - controlPointOffsetY;
            final float rightEdgeControlY1 = top + arcRadius;
            final float rightEdgeControlY2 = top + controlPointDistance;
            path.cubicTo(rightEdgeControlX1, rightEdgeControlY1, endX,
                    rightEdgeControlY2, endX, Math.min(recCenterY, scaledCornerRadius) + top);

            // Draw bottom right of the rectangle
            final float bottomRightCornerY = height - scaledCornerRadius;
            path.lineTo(endX, Math.max(recCenterY, bottomRightCornerY) + top);

            final float bottomY = top + height;
            final float bottomRightControlY1 = bottomY - controlPointDistance;
            final float bottomRightControlY2 = bottomY - arcRadius;
            final float bottomRightControlY3 = bottomY - startControlPointX;
            path.cubicTo(endX, bottomRightControlY1, rightEdgeControlX1,
                    bottomRightControlY2, topRightEndControlX3, bottomRightControlY3);

            // Draw left edge of the rectangle
            final float leftEdgeYFull = bottomY - endControlPointX;
            final float leftEdgeY2 = bottomY - endControlPointY;
            final float leftEdgeY3 = bottomY - startControlPointY;
            path.cubicTo(topRightEndControlX2, leftEdgeYFull, topRightEndControlX1,
                    leftEdgeY2, topRightControlX3, leftEdgeY3);

            // Draw bottom edge with control points
            final float bottomEdgeControlY = bottomY - controlPointOffsetY;
            path.cubicTo(topRightControlX2, bottomEdgeControlY, topRightControlX1, bottomY, Math.max(recCenterX, topRightCornerX) + left, bottomY);

            // Draw left side
            path.lineTo(Math.min(recCenterX, scaledCornerRadius) + left, bottomY);

            final float leftControlX1 = left + controlPointDistance;
            final float leftControlX2 = left + arcRadius;
            final float leftControlX3 = left + startControlPointX;
            path.cubicTo(leftControlX1, bottomY, leftControlX2, bottomEdgeControlY, leftControlX3, leftEdgeY3);

            final float leftControlX4 = left + endControlPointX;
            final float leftControlY1 = endControlPointY + left;
            final float leftControlY2 = left + startControlPointY;
            path.cubicTo(leftControlX4, leftEdgeY2, leftControlY1,
                    leftEdgeYFull, leftControlY2, bottomRightControlY3);

            // Draw top left edge
            final float leftEdgeControlX1 = left + controlPointOffsetY;
            path.cubicTo(leftEdgeControlX1, bottomRightControlY2, left,
                    bottomRightControlY1, left, Math.max(recCenterY, bottomRightCornerY) + top);

            path.lineTo(left, Math.min(recCenterY, scaledCornerRadius) + top);
            path.cubicTo(left, rightEdgeControlY2, leftEdgeControlX1,
                    rightEdgeControlY1, leftControlY2, topRightEndControlY3);
            path.cubicTo(leftControlY1, topRightEndControlY2, leftControlX4,
                    topRightEndControlY1, leftControlX3, topRightControlY3);
            path.cubicTo(leftControlX2, topRightControlY2, leftControlX1,
                    top, Math.min(recCenterX, scaledCornerRadius) + left, top);

            // Close the path to form the rectangle with smooth corners
            path.close();
            return path;
        }
        return new Path();
    }

    private boolean isColorType(int i) {
        return i >= TYPE_FIRST_COLOR_INT && i <= TYPE_LAST_COLOR_INT;
    }

    public void drawRoundedCorner(@NonNull View view, @NonNull Canvas canvas) {
        int left;
        int top;
        if (view.getTranslationY() != 0f) {
            left = Math.round(view.getX());
            top = Math.round(view.getY());
            canvas.translate((view.getX() - left) + 0.5f, (view.getY() - top) + 0.5f);
        } else {
            left = view.getLeft();
            top = view.getTop();
        }
        mRoundedCornerBounds.set(left, top, view.getWidth() + left, view.getHeight() + top);
        drawRoundedCornerInternal(canvas);
    }

    public static class SeslRoundedChunkingDrawable extends Drawable {
        private final float mAngle;
        private final Paint mPaint;
        private final int mRoundRadius;

        public SeslRoundedChunkingDrawable(int radius, @NonNull Paint paint, float angle) {
            mRoundRadius = radius;
            mPaint = paint;
            mAngle = angle;
        }

        private Path getSmoothCornerRectPath(float cornerRadius, int rectWidth, int rectHeight) {
            if (rectWidth > 0 && rectHeight > 0) {
                // Maximum radius is half of the smaller dimension
                float maxCornerRadius = Math.min(rectWidth / 2f, rectHeight / 2f);
                // Adjust the provided corner radius to be within valid bounds
                float clampedCornerRadius = Math.min(Math.max(cornerRadius, 0f), maxCornerRadius);
                // Calculate the fraction of the adjusted radius relative to the maximum radius
                float cornerRadiusFraction = clampedCornerRadius / maxCornerRadius;

                // Calculate shrinkage factor for radius adjustment
                float shrinkageFactor;
                if (cornerRadiusFraction > SHRINK_FACTOR_THRESHOLD) {
                    float fractionExcess = cornerRadiusFraction - SHRINK_FACTOR_THRESHOLD;
                    float normalizedExcess = Math.min(1f, fractionExcess / SHRINK_FACTOR_DENOMINATOR);
                    shrinkageFactor = 1f - (normalizedExcess * SHRINK_FACTOR_MULTIPLIER);
                }else{
                    shrinkageFactor = 1f;
                }

                float scaleFactor;
                if (cornerRadiusFraction > SCALE_FACTOR_THRESHOLD) {
                    float fractionExcess = cornerRadiusFraction - SCALE_FACTOR_THRESHOLD;
                    float normalizedExcess = Math.min(1f, fractionExcess / SCALE_FACTOR_DENOMINATOR);
                    scaleFactor = 1f + (normalizedExcess * SCALE_FACTOR_MULTIPLIER);
                }else{
                    scaleFactor = 1f;
                }

                return getSmoothCornerRectPath(
                        clampedCornerRadius,
                        (float) rectWidth,
                        (float) rectHeight,
                        shrinkageFactor,
                        scaleFactor
                );
            }
            return new Path();
        }


        private Path getTopLeftSmoothCornerPath(float cornerRadius, float width, float height, float adjustScale, float scale) {
            final float horizontalRadiusFraction = ((width / 2f) / cornerRadius) * 100f;

            final float maxRadiusFraction = adjustScale * 128.19f;

            final String pathSegmentTop = String.format(LOCALE, LINE_TO_FORMAT, 0f,
                    Math.min(((height / 2f) / cornerRadius) * 100f, maxRadiusFraction));

            final Float controlPointOffsetY = scale * 83.62f;

            final String pathSegmentCurve1 = String.format(LOCALE, CUBIC_BEZIER_CURVE_FORMAT, 0f,
                    controlPointOffsetY,
                    CONTROL_POINT_X_1, CONTROL_POINT_Y_1, CURVE_END_X, CURVE_END_Y);

            final String pathSegmentCurve2 = String.format(LOCALE, CUBIC_BEZIER_CURVE_FORMAT,
                    TANGENT_X_1, TANGENT_Y_1,
                    TANGENT_Y_1, TANGENT_X_1, CURVE_END_Y, CURVE_END_X);

            final String pathSegmentBottom = String.format(LOCALE, CUBIC_BEZIER_CURVE_FORMAT,
                    CONTROL_POINT_Y_1,
                    CONTROL_POINT_X_1,
                    controlPointOffsetY, 0f, Math.min(horizontalRadiusFraction, maxRadiusFraction), 0f);

            final String pathSegmentEnd = String.format(
                    LOCALE, LINE_TO_FORMAT, Math.min(horizontalRadiusFraction, maxRadiusFraction), 0f);

            final String pathData = MOVE_TO_START
                    + pathSegmentTop
                    + pathSegmentCurve1
                    + pathSegmentCurve2
                    + pathSegmentBottom
                    + pathSegmentEnd
                    + PATH_SEGMENT_CLOSE;

            PathParser.PathDataNode[] pathNodes = PathParser.createNodesFromPathData(pathData);

            Path smoothCornerPath = new Path();
            nodesToPath(pathNodes, smoothCornerPath);

            final Matrix scaleMatrix = new Matrix();
            final float scaleFactor = cornerRadius / 100.0f;
            scaleMatrix.setScale(scaleFactor, scaleFactor);
            smoothCornerPath.transform(scaleMatrix);

            return smoothCornerPath;
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            canvas.drawPath(getSmoothCornerRectPath(mRoundRadius, canvas.getWidth(), canvas.getHeight()), mPaint);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.OPAQUE;
        }

        @Override
        public void setAlpha(@IntRange(from = 0, to = 255) int alpha) {
            mPaint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
            mPaint.setColorFilter(colorFilter);
        }

        @NonNull
        public Path getSmoothCornerRectPath(float cornerRadius, float width, float height, float adjustScale, float scale) {
            // Get the path for the top-left smooth corner of the rectangle
            final Path topLeftCornerPath = getTopLeftSmoothCornerPath(cornerRadius, width, height, adjustScale, scale);

            // Get the bounding rectangle for transformations
            final Rect boundingRect = getBounds();

            // Create a matrix for rotation
            final Matrix rotationMatrix = new Matrix();
            rotationMatrix.setRotate(mAngle, boundingRect.width() / 2.0f, boundingRect.height() / 2.0f);

            // Apply rotation transformation to the top-left corner path
            topLeftCornerPath.transform(rotationMatrix);

            // Create a matrix for translation
            final Matrix translationMatrix = new Matrix();
            translationMatrix.setTranslate(boundingRect.left, boundingRect.top);

            // Apply translation transformation to the top-left corner path
            topLeftCornerPath.transform(translationMatrix);

            return topLeftCornerPath;
        }
    }

    public void drawRoundedCorner(@NonNull Canvas canvas, @Nullable Insets insets) {
        mInsets = insets;
        drawRoundedCorner(canvas);
    }

    public void drawRoundedCorner(@NonNull Rect rect, @NonNull Canvas canvas) {
        mRoundedCornerBounds.set(rect);
        drawRoundedCornerInternal(canvas);
    }
}
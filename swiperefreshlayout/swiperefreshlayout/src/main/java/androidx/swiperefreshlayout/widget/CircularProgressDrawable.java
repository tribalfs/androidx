/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.swiperefreshlayout.widget;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;
import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.util.Preconditions;
import androidx.swiperefreshlayout.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * <b>SESL variant</b><br><br>
 *
 * Drawable that renders the animated indeterminate progress indicator in the Material design style
 * without depending on API level 11.
 *
 * <p>To use one of the pre-defined sizes instead of using your own, {@link #setStyle(int)} should
 * be called with one of the {@link #DEFAULT} or {@link #LARGE} styles as its parameter. Doing it
 * so will update the arrow dimensions, ring size and stroke width to fit the one specified.
 *
 * <p>If no center radius is set via {@link #setCenterRadius(float)} or {@link #setStyle(int)}
 * methods, CircularProgressDrawable will fill the bounds set via {@link #setBounds(Rect)}.
 */
public class CircularProgressDrawable extends Drawable implements Animatable {
    private static final Interpolator LINEAR_INTERPOLATOR = new LinearInterpolator();

    @RestrictTo(LIBRARY_GROUP_PREFIX)
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({LARGE, DEFAULT})
    public @interface ProgressDrawableSize {
    }

    /** Maps to ProgressBar.Large style. */
    public static final int LARGE = 0;

    private static final float CENTER_RADIUS_LARGE = 20f/*sesl*/;

    /** Maps to ProgressBar default style. */
    public static final int DEFAULT = 1;

    private static final float CENTER_RADIUS = 14f/*sesl*/;

    /** The duration of a single progress spin in milliseconds. */
    private static final int ANIMATION_DURATION = 200/*sesl*/;

    /** The indicator ring, used to manage animation state. */
    private final FourDot mFourDot;
    interface OnAnimationEndCallback {
        void OnAnimationEnd();
    }
    OnAnimationEndCallback mAnimationEndCallback = null;
    private static final Interpolator SINE_OUT_60 = new PathInterpolator(0.17f,
            0.17f,
            0.4f,
            1.0f);
    Drawable mDotAnimation;
    final float mScreenDensity;
    private Animator mRotateAnimtior;


    /** Canvas rotation in degrees. */
    private float mRotation;

    private Resources mResources;
    private Animator mAnimator;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    float mRotationCount;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    boolean mFinishing;

    /**
     * @param context application context
     */
    public CircularProgressDrawable(@NonNull Context context) {
        mResources = Preconditions.checkNotNull(context).getResources();

        //Sesl
        mFourDot = new FourDot();

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.swipeRefreshLayoutTheme, outValue, true);
        ContextThemeWrapper contextThemeWrapper = new ContextThemeWrapper(context, outValue.resourceId == 0 ?
                R.style.SwipeRefreshLayoutThemeOverlay : outValue.resourceId);

        TypedArray a = contextThemeWrapper.obtainStyledAttributes(null, R.styleable.SwipeRefreshLayoutProgress);
        final int[] COLORS = {
                a.getColor(R.styleable.SwipeRefreshLayoutProgress_swipeRefreshCircleDotColor1,
                        context.getResources().getColor(R.color.sesl_swipe_refresh_color1)),
                a.getColor(R.styleable.SwipeRefreshLayoutProgress_swipeRefreshCircleDotColor2,
                        context.getResources().getColor(R.color.sesl_swipe_refresh_color2))
        };
        mFourDot.setColors(COLORS);

        mDotAnimation = contextThemeWrapper.getResources().getDrawable(R.drawable.sesl_swipe_refresh_animated,
                contextThemeWrapper.getTheme());
        mScreenDensity = mResources.getDisplayMetrics().density;

        mDotAnimation.setAlpha(0);
        mFourDot.setDotAnimtion(mDotAnimation);
        //sesl

        setupAnimators();
    }

    /** Sets all parameters at once in dp. */
    private void setSizeParameters(float centerRadius) {
        mFourDot.setDotRadius(mScreenDensity * 2.25f);//sesl
        mFourDot.setCenterRadius(centerRadius * mScreenDensity);//sesl
    }

    /**
     * Sets the overall size for the progress spinner. This updates the radius
     * .
     *
     * @param size one of {@link #LARGE} or {@link #DEFAULT}
     */
    public void setStyle(@ProgressDrawableSize int size) {
        if (size == LARGE) {
            setSizeParameters(CENTER_RADIUS_LARGE);
        } else {
            setSizeParameters(CENTER_RADIUS);
        }
        invalidateSelf();
    }

    /**
     * Returns the center radius for the progress spinner in pixels.
     *
     * @return center radius in pixels
     */
    public float getCenterRadius() {
        return mFourDot.getCenterRadius();
    }

    /**
     * Sets the center radius for the progress spinner in pixels. If set to 0, this drawable will
     * fill the bounds when drawn.
     *
     * @param centerRadius center radius in pixels
     */
    public void setCenterRadius(float centerRadius) {
        mFourDot.setCenterRadius(centerRadius);
        invalidateSelf();
    }


    /**
     * Returns the amount of rotation applied to the progress spinner.
     *
     * @return amount of rotation from [0..1]
     */
    public float getProgressRotation() {
        return mFourDot.getRotation();//sesl
    }

    /**
     * Sets the amount of rotation to apply to the progress spinner.
     *
     * @param rotation rotation from [0..1]
     */
    public void setProgressRotation(float rotation) {
        mFourDot.setRotation(rotation);//sesl
        invalidateSelf();
    }

    /**
     * Returns the colors used in the progress animation
     *
     * @return list of ARGB colors
     */
    @NonNull
    public int[] getColorSchemeColors() {
        return mFourDot.getColors();//sesl
    }

    /**
     * Sets the colors used in the progress animation from a color list. The first color will also
     * be the color to be used if animation is not started yet.
     *
     * @param colors list of ARGB colors to be used in the spinner
     */
    public void setColorSchemeColors(@NonNull int... colors) {
        mFourDot.setColors(colors);//sesl
        invalidateSelf();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        final Rect bounds = getBounds();
        canvas.save();
        canvas.rotate(mRotation, bounds.exactCenterX(), bounds.exactCenterY());
        mFourDot.draw(canvas, bounds);//sesl
        canvas.restore();
    }

    @Override
    public void setAlpha(int alpha) {
        mFourDot.setAlpha(alpha);//sesl
        invalidateSelf();
    }

    @Override
    public int getAlpha() {
        return mFourDot.getAlpha();//sesl
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mFourDot.setColorFilter(colorFilter);//sesl
        invalidateSelf();
    }

    private void setRotation(float rotation) {
        mRotation = rotation;
    }

    @SuppressWarnings("UnusedMethod") // TODO(b/141954576): Suppressed during upgrade to AGP 3.6.
    private float getRotation() {
        return mRotation;
    }

    @Override
    @SuppressWarnings("deprecation")
    // Remove suppression was b/120985527 is addressed.
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    @Override
    public boolean isRunning() {
        return mAnimator.isRunning()
                || ((AnimatedVectorDrawable) mDotAnimation).isRunning()/*sesl*/;
    }

    /**
     * Starts the animation for the spinner.
     */
    @Override
    public void start() {
        mAnimator.cancel();
        //Sesl
        mRotateAnimtior.cancel();
        mAnimator.start();
        mRotateAnimtior.start();
        //sesl
    }

    /**
     * Stops the animation for the spinner.
     */
    @Override
    public void stop() {
        //Sesl
        ((AnimatedVectorDrawable) mDotAnimation).stop();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ((AnimatedVectorDrawable) mDotAnimation).clearAnimationCallbacks();
        }
        mDotAnimation.setAlpha(0);
        mFourDot.setPosition(0);
        mFourDot.setIsRunning(false);
        mRotateAnimtior.cancel();
        //sesl;
        mAnimator.cancel();
        setRotation(0);
        invalidateSelf();
    }

    private void setupAnimators() {
        //Sesl
        final FourDot fourDot = mFourDot;
        final ValueAnimator animator = ValueAnimator.ofFloat(0f, 10f);
        final ValueAnimator rotateAnimator = ValueAnimator.ofInt(0, 90);
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float interpolatedTime = (float) animation.getAnimatedValue();
                fourDot.setPosition(mScreenDensity * interpolatedTime);
                fourDot.setScale((mScreenDensity * 11.0f) + (interpolatedTime * 0.75f * mScreenDensity / 10.0f));
                invalidateSelf();
            }
        });
        rotateAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int interpolatedTime = (int) valueAnimator.getAnimatedValue();
                fourDot.setRotation(interpolatedTime);
                invalidateSelf();
            }
        });
        rotateAnimator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animator) {
                // do nothing
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                fourDot.setRotation(0.0f);
            }

            @Override
            public void onAnimationCancel(Animator animator) {
                // do nothing
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
                // do nothing
            }
        });
        //sesl
        animator.addListener(new Animator.AnimatorListener() {

            @Override
            public void onAnimationStart(Animator animator) {
                mRotationCount = 0;
            }

            @Override
            public void onAnimationEnd(Animator animator) {
                //Sesl
                fourDot.setRotation(0);
                fourDot.setIsRunning(true);
                mDotAnimation.setAlpha(255);
                fourDot.setAlpha(0);
                startDotAnimation();
                //sesl
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                // do nothing
            }

            @Override
            public void onAnimationRepeat(Animator animator) {
                // do nothing //sesl
            }
        });
        mAnimator = animator;
        //Sesl
        animator.setInterpolator(SINE_OUT_60);
        animator.setDuration(ANIMATION_DURATION);
        rotateAnimator.setInterpolator(LINEAR_INTERPOLATOR);
        rotateAnimator.setDuration(ANIMATION_DURATION);
        mRotateAnimtior = rotateAnimator;
        //sesl
    }

    /**
     * A private class to do all the drawing of CircularProgressDrawable, which includes background,
     * progress spinner. This class is to separate drawing from animation.
     */
    private static class FourDot {
        final Paint mPaint = new Paint();
        float mRotation = 0f;
        //Sesl
        final Paint mDotPaint = new Paint();
        Drawable mDotAnimation;
        float mPosition = 0f;
        float mDotRadius;
        float mCenterRadius;
        boolean mIsRunning;
        float mScale = 1;
        //sesl

        int[] mColors;
        // mColorIndex represents the offset into the available mColors that the
        // progress circle should currently display. As the progress circle is
        // animating, the mColorIndex moves by one to the next available color.
        int mColorIndex;
        int mAlpha = 255;

        FourDot() {
            mPaint.setStrokeCap(Paint.Cap.SQUARE);
            mPaint.setAntiAlias(true);
            mPaint.setStyle(Style.FILL);
            mDotPaint.setStrokeCap(Paint.Cap.SQUARE);
            mDotPaint.setAntiAlias(true);
            mDotPaint.setStyle(Style.FILL);
        }

        /**
         * Draw the progress spinner
         */
        void draw(Canvas c, Rect bounds) {
            final RectF dotBounds = new RectF();
            dotBounds.set(bounds.centerX() - mCenterRadius,
                    bounds.centerY() - mCenterRadius,
                    bounds.centerX() + mCenterRadius,
                    bounds.centerY() + mCenterRadius);

            mPaint.setColor(mColors[0]);
            mDotPaint.setColor(mColors[1]);
            mPaint.setAlpha(mAlpha);
            mDotPaint.setAlpha(mAlpha);

            c.rotate(mRotation, dotBounds.centerX(), dotBounds.centerY());

            if (mScale != 0) {
                c.drawCircle(dotBounds.centerX(),
                        dotBounds.centerY() + mPosition,
                        mDotRadius,
                        mDotPaint);
                c.drawCircle(dotBounds.centerX() - mPosition,
                        dotBounds.centerY(),
                        mDotRadius,
                        mDotPaint);
                c.drawCircle(dotBounds.centerX() + mPosition,
                        dotBounds.centerY(),
                        mDotRadius,
                        mDotPaint);
            }

            c.drawCircle(dotBounds.centerX(),
                    dotBounds.centerY() - mPosition,
                    mCenterRadius - mScale,
                    mPaint);

            if (mIsRunning) {
                mDotAnimation.setBounds((int) dotBounds.left,
                        (int) dotBounds.top,
                        (int) dotBounds.right,
                        (int) dotBounds.bottom);
                mDotAnimation.draw(c);
            }
        }

        /**
         * Sets the colors the progress spinner alternates between.
         *
         * @param colors array of ARGB colors. Must be non-{@code null}.
         */
        void setColors(@NonNull int[] colors) {
            mColors = colors;
        }

        int[] getColors() {
            return mColors;
        }

        void setColorFilter(ColorFilter filter) {
            mPaint.setColorFilter(filter);
        }

        /**
         * @param alpha alpha of the progress spinner and associated arrowhead.
         */
        void setAlpha(int alpha) {
            mAlpha = alpha;
        }

        /**
         * @return current alpha of the progress spinner and arrowhead
         */
        int getAlpha() {
            return mAlpha;
        }

        int getStartingColor() {
            return mColors[mColorIndex];
        }

        void setRotation(float rotation) {
            mRotation = rotation;
        }

        float getRotation() {
            return mRotation;
        }

        /**
         * @param centerRadius inner radius in px of the circle the progress spinner arc traces
         */
        void setCenterRadius(float centerRadius) {
            mCenterRadius = centerRadius;
        }

        float getCenterRadius() {
            return mCenterRadius;
        }

        //Sesl
        void setPosition(float position) {
            mPosition = position;
        }

        void setDotAnimtion(Drawable dotAnimtion) {
            mDotAnimation = dotAnimtion;
        }


        void setIsRunning(boolean isRunning) {
            mIsRunning = isRunning;
        }

        void setDotRadius(float Radius) {
            mDotRadius = Radius;
        }

        void setScale(float scale) {
            if (scale != mScale) {
                mScale = scale;
            }
        }
        //sesl
    }

    //Sesl
    void startDotAnimation() {
        ((AnimatedVectorDrawable) mDotAnimation).start();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ((AnimatedVectorDrawable) mDotAnimation).registerAnimationCallback(new Animatable2.AnimationCallback() {
                @Override
                public void onAnimationEnd(Drawable drawable) {
                    if (mAnimationEndCallback != null) {
                        mAnimationEndCallback.OnAnimationEnd();
                    }
                    ((AnimatedVectorDrawable) mDotAnimation).start();
                    invalidateSelf();
                }
            });
        }
    }

    @RestrictTo(LIBRARY)
    void setOnAnimationEndCallback(OnAnimationEndCallback callback) {
        mAnimationEndCallback = callback;
    }

    /**
     * Sets the scale of the spinner.
     *
     * @param scale scaling that will be applied to the dot.
     */
    public void setScale(float scale) {
        if (scale == 0) {
            mFourDot.setScale(0);
        } else {
            mFourDot.setScale(Math.min(scale * 11.0f * mScreenDensity,
                    mScreenDensity * 11.0f));
        }
        invalidateSelf();
    }
    //sesl
}

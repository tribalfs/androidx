/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.picker.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathDashPathEffect;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RectF;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.picker.R;

import androidx.picker.util.SeslSleepTimePickerUtil;

//TODO: rework
public class SeslCircularSeekBarView extends View {
    public final float DPTOPX_SCALE;
    public final PathInterpolator EXPAND_COLLAPSE_PATH_INTERPOLATOR;
    public AttributeSet mAttributeSet;
    public Drawable mBedTimeDrawable;
    public final RectF mBedTimeIconRectF;
    public int mCircleColor;
    public int mCircleFillColor;
    public Paint mCircleFillPaint;
    public Path mCircleFirstPointerPath;
    public int mCircleGridMedium;
    public int mCircleGridSmall;
    public float mCircleHeight;
    public Paint mCircleLineProgressPaint;
    public Path mCircleLineProgressPath;
    public Paint mCirclePaint;
    public Path mCirclePath;
    public Paint mCircleProgressPaint;
    public Path mCircleProgressPath;
    public final RectF mCircleRectF;
    public Path mCircleSecondPointerPath;
    public float mCircleStrokeWidth;
    public Paint.Cap mCircleStyle;
    public float mCircleWidth;
    public SeslCircularSeekBarRevealAnimation mCircularSeekBarRevealAnimation;
    public float mDashLineStrokeWidth;
    public int mDefStyle;
    public float mEndAngle;
    public float mFirstPointerAngle;
    public int mFirstPointerColor;
    public int mFirstPointerHaloColor;
    public Paint mFirstPointerHaloPaint;
    public Paint mFirstPointerPaint;
    public float mFirstPointerPosition;
    public Paint mGridPaintMedium;
    public Paint mGridPaintSmall;
    public float mHandlerTouchPosition;
    public boolean mHideProgressWhenEmpty;
    public float mIconSize;
    public boolean mIsExpandCollapseAnimation;
    public int mLastPointerTouched;
    public boolean mLockAtEnd;
    public boolean mLockAtStart;
    public boolean mLockEnabled;
    public boolean mMaintainEqualCircle;
    public float mMax;
    public int mMiddleGradientColor;
    public boolean mMoveOutsideCircle;
    public float mPointerHaloWidth;
    public float mPointerStrokeWidth;
    public float mProgress;
    public float mProgressDegrees;
    public float mRadiusIn;
    public float mRadiusOut;
    public float mSecondPointerAngle;
    public int mSecondPointerColor;
    public int mSecondPointerHaloColor;
    public Paint mSecondPointerHaloPaint;
    public Paint mSecondPointerPaint;
    public float mSecondPointerPosition;
    public boolean mSleepGoalWheelEnable;
    public float mSleepGoalWheelExtendDegree;
    public float mSleepGoalWheelExtendStart;
    public Paint mSleepGoalWheelPaint;
    public Path mSleepGoalWheelPath;
    public final RectF mSleepGoalWheelRectF;
    public float mSleepGoalWheelStrokeWidth;
    public float mStartAngle;
    public SweepGradientVariable mSweepGradientVariable;
    public float mTotalCircleDegrees;
    public float mTouchDistanceFromFirstPointer;
    public float mTouchDistanceFromSecondPointer;
    public TouchEventVariable mTouchEventVariable;
    public boolean mUserIsMovingFirstPointer;
    public boolean mUserIsMovingMiddleHandler;
    public boolean mUserIsMovingSecondPointer;
    public Drawable mWakeUpDrawable;
    public final RectF mWakeUpTimeIconRectF;
    public static final int DEFAULT_CIRCLE_STYLE = Paint.Cap.ROUND.ordinal();
    public static final int DEFAULT_FIRST_POINTER_COLOR = Color.argb(255, 133, 135, 254);
    public static final int DEFAULT_MIDDLE_COLOR = Color.argb(255, 133, 135, 254);
    public static final int DEFAULT_FIRST_POINTER_HALO_COLOR = Color.argb(255, 133, 135, 254);
    public static final int DEFAULT_SECOND_POINTER_COLOR = Color.argb(255, 255, 167, 0);
    public static final int DEFAULT_SECOND_POINTER_HALO_COLOR = Color.argb(255, 255, 167, 0);

    /* loaded from: classes.dex */
    public class TouchEventVariable {
        public float additionalRadius;
        public float ccwDistanceFromEnd;
        public float ccwDistanceFromStart;
        public float cwDistanceFromEnd;
        public float cwDistanceFromStart;
        public float cwPointerFromStart;
        public float distanceX;
        public float distanceY;
        public float innerRadius;
        public float minimumTouchTarget;
        public float outerRadius;
        public boolean pointerNearEnd;
        public boolean pointerNearStart;
        public float smallInCircle;
        public float touchAngle;
        public float touchEventRadius;
        public boolean touchOverEnd;
        public boolean touchOverStart;
        public float x;
        public float y;
    }

    public final void addPointerTouchListener() {
    }

    public final void initTouchOnFirstPointer() {
    }

    public final void initTouchOnSecondPointer() {
    }

    public SeslCircularSeekBarView(Context context) {
        super(context);
        this.DPTOPX_SCALE = getResources().getDisplayMetrics().density;
        this.mCircleRectF = new RectF();
        this.mBedTimeIconRectF = new RectF();
        this.mWakeUpTimeIconRectF = new RectF();
        this.mSleepGoalWheelRectF = new RectF();
        this.mLockEnabled = true;
        this.mLockAtStart = true;
        this.mLockAtEnd = false;
        this.mUserIsMovingFirstPointer = false;
        this.mUserIsMovingSecondPointer = false;
        this.mUserIsMovingMiddleHandler = false;
        this.mSleepGoalWheelEnable = false;
        this.mIsExpandCollapseAnimation = false;
        this.EXPAND_COLLAPSE_PATH_INTERPOLATOR = new PathInterpolator(0.22f, 0.25f, 0.0f, 1.0f);
        this.mAttributeSet = null;
        this.mDefStyle = 0;
        init();
    }

    public SeslCircularSeekBarView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        this.DPTOPX_SCALE = getResources().getDisplayMetrics().density;
        this.mCircleRectF = new RectF();
        this.mBedTimeIconRectF = new RectF();
        this.mWakeUpTimeIconRectF = new RectF();
        this.mSleepGoalWheelRectF = new RectF();
        this.mLockEnabled = true;
        this.mLockAtStart = true;
        this.mLockAtEnd = false;
        this.mUserIsMovingFirstPointer = false;
        this.mUserIsMovingSecondPointer = false;
        this.mUserIsMovingMiddleHandler = false;
        this.mSleepGoalWheelEnable = false;
        this.mIsExpandCollapseAnimation = false;
        this.EXPAND_COLLAPSE_PATH_INTERPOLATOR = new PathInterpolator(0.22f, 0.25f, 0.0f, 1.0f);
        this.mAttributeSet = attributeSet;
        this.mDefStyle = 0;
        init();
    }

    public SeslCircularSeekBarView(Context context, AttributeSet attributeSet, int i) {
        super(context, attributeSet, i);
        this.DPTOPX_SCALE = getResources().getDisplayMetrics().density;
        this.mCircleRectF = new RectF();
        this.mBedTimeIconRectF = new RectF();
        this.mWakeUpTimeIconRectF = new RectF();
        this.mSleepGoalWheelRectF = new RectF();
        this.mLockEnabled = true;
        this.mLockAtStart = true;
        this.mLockAtEnd = false;
        this.mUserIsMovingFirstPointer = false;
        this.mUserIsMovingSecondPointer = false;
        this.mUserIsMovingMiddleHandler = false;
        this.mSleepGoalWheelEnable = false;
        this.mIsExpandCollapseAnimation = false;
        this.EXPAND_COLLAPSE_PATH_INTERPOLATOR = new PathInterpolator(0.22f, 0.25f, 0.0f, 1.0f);
        this.mAttributeSet = attributeSet;
        this.mDefStyle = i;
        init();
    }

    public final void initAttributes(TypedArray typedArray) {
        this.mPointerStrokeWidth =
                typedArray.getDimension(R.styleable.seslCircularSeekBar_csPointerStrokeWidth,
                        65.0f);
        this.mIconSize = typedArray.getDimension(R.styleable.seslCircularSeekBar_csIconWidth,
                50.0f);
        this.mPointerHaloWidth =
                typedArray.getDimension(R.styleable.seslCircularSeekBar_csPointerHaloWidth, 15.0f);
        this.mCircleStrokeWidth =
                typedArray.getDimension(R.styleable.seslCircularSeekBar_csCircleStrokeWidth, 15.0f);
        this.mSleepGoalWheelStrokeWidth =
                getResources().getDimension(R.dimen.sesl_sleep_goal_wheel_width);
        this.mDashLineStrokeWidth = getResources().getDimension(R.dimen.sesl_dot_line_stroke_width);
        this.mCircleStyle =
                Paint.Cap.values()[typedArray.getInt(R.styleable.seslCircularSeekBar_CircleStyle,
                        DEFAULT_CIRCLE_STYLE)];
        this.mMiddleGradientColor =
                typedArray.getColor(R.styleable.seslCircularSeekBar_csMiddleColor,
                        DEFAULT_MIDDLE_COLOR);
        this.mFirstPointerColor =
                typedArray.getColor(R.styleable.seslCircularSeekBar_csFirstPointerColor,
                        DEFAULT_FIRST_POINTER_COLOR);
        this.mFirstPointerHaloColor =
                typedArray.getColor(R.styleable.seslCircularSeekBar_csFirstPointerHaloColor,
                        DEFAULT_FIRST_POINTER_HALO_COLOR);
        this.mSecondPointerColor =
                typedArray.getColor(R.styleable.seslCircularSeekBar_csSecondPointerColor,
                        DEFAULT_SECOND_POINTER_COLOR);
        this.mSecondPointerHaloColor =
                typedArray.getColor(R.styleable.seslCircularSeekBar_csSecondPointerHaloColor,
                        DEFAULT_SECOND_POINTER_HALO_COLOR);
        this.mCircleColor = typedArray.getColor(R.styleable.seslCircularSeekBar_csCircleColor,
                -3355444);
        this.mCircleFillColor = typedArray.getColor(R.styleable.seslCircularSeekBar_csCircleFill,
                0);
        this.mCircleGridSmall =
                typedArray.getColor(R.styleable.seslCircularSeekBar_csCircleGridSmallColor,
                        -3355444);
        this.mCircleGridMedium =
                typedArray.getColor(R.styleable.seslCircularSeekBar_csCircleGridMediumColor,
                        -7829368);
        this.mMax = typedArray.getInt(R.styleable.seslCircularSeekBar_csMax, 100);
        this.mProgress = typedArray.getInt(R.styleable.seslCircularSeekBar_csProgress, 40);
        this.mMaintainEqualCircle =
                typedArray.getBoolean(R.styleable.seslCircularSeekBar_csMaintainEqualCircle, true);
        this.mMoveOutsideCircle =
                typedArray.getBoolean(R.styleable.seslCircularSeekBar_csMoveOutsideCircle, true);
        this.mLockEnabled = typedArray.getBoolean(R.styleable.seslCircularSeekBar_csLockEnabled,
                true);
        this.mHideProgressWhenEmpty =
                typedArray.getBoolean(R.styleable.seslCircularSeekBar_csHideProgressWhenEmpty,
                        false);
        this.mSecondPointerPosition = 7.5f;
        this.mFirstPointerPosition = 225.0f;
        this.mStartAngle = ((typedArray.getFloat(R.styleable.seslCircularSeekBar_csStartAngle,
                270.0f) % 360.0f) + 360.0f) % 360.0f;
        float f =
                ((typedArray.getFloat(R.styleable.seslCircularSeekBar_csEndAngle, 270.0f) % 360.0f) + 360.0f) % 360.0f;
        this.mEndAngle = f;
        if (this.mStartAngle % 360.0f == f % 360.0f) {
            this.mEndAngle = f - 0.1f;
        }
        int i = R.styleable.seslCircularSeekBar_csPointerAngle;
        float f2 = ((typedArray.getFloat(i, 0.0f) % 360.0f) + 360.0f) % 360.0f;
        this.mSecondPointerAngle = f2;
        if (f2 == 0.0f) {
            this.mSecondPointerAngle = 0.1f;
        }
        float f3 = ((typedArray.getFloat(i, 0.0f) % 360.0f) + 360.0f) % 360.0f;
        this.mFirstPointerAngle = f3;
        if (f3 == 0.0f) {
            this.mFirstPointerAngle = 0.1f;
        }
        this.mCircularSeekBarRevealAnimation = new SeslCircularSeekBarRevealAnimation(this);
    }

    public final void initDrawableIcons() {
        Drawable drawable;
        this.mBedTimeDrawable =
                getResources().getDrawable(R.drawable.sesl_bedtime, null).mutate().getConstantState().newDrawable().mutate();
        this.mWakeUpDrawable =
                getResources().getDrawable(R.drawable.sesl_wakeup, null).mutate().getConstantState().newDrawable().mutate();
        PorterDuffColorFilter porterDuffColorFilter =
                new PorterDuffColorFilter(getContext().getResources().getColor(R.color.sesl_picker_thumb_icon_color), PorterDuff.Mode.SRC_ATOP);
        if (this.mBedTimeDrawable == null || (drawable = this.mWakeUpDrawable) == null) {
            return;
        }
        drawable.setColorFilter(porterDuffColorFilter);
        this.mBedTimeDrawable.setColorFilter(porterDuffColorFilter);
    }

    public final void initPaints() {
        setCirclePaint();
        setCircleFillPaint();
        setCircleProgressPaint();
        setSleepGoalWheelPaint();
        setSecondPointerPaint();
        setFirstPointerPaint();
        setClockGridPaint();
        setDotLinePaint();
    }

    public final void setCirclePaint() {
        Paint paint = new Paint();
        this.mCirclePaint = paint;
        paint.setAntiAlias(true);
        this.mCirclePaint.setDither(true);
        this.mCirclePaint.setColor(this.mCircleColor);
        this.mCirclePaint.setStrokeWidth(this.mCircleStrokeWidth);
        this.mCirclePaint.setStyle(Paint.Style.STROKE);
        this.mCirclePaint.setStrokeJoin(Paint.Join.ROUND);
        this.mCirclePaint.setStrokeCap(this.mCircleStyle);
    }

    public final void setCircleFillPaint() {
        Paint paint = new Paint();
        this.mCircleFillPaint = paint;
        paint.setAntiAlias(true);
        this.mCircleFillPaint.setDither(true);
        this.mCircleFillPaint.setColor(this.mCircleFillColor);
        this.mCircleFillPaint.setStyle(Paint.Style.FILL);
    }

    public final void setCircleProgressPaint() {
        Paint paint = new Paint();
        this.mCircleProgressPaint = paint;
        paint.setAntiAlias(true);
        this.mCircleProgressPaint.setDither(true);
        this.mCircleProgressPaint.setStrokeWidth(this.mCircleStrokeWidth);
        this.mCircleProgressPaint.setStyle(Paint.Style.STROKE);
        this.mCircleProgressPaint.setStrokeJoin(Paint.Join.ROUND);
        this.mCircleProgressPaint.setStrokeCap(this.mCircleStyle);
    }

    public final void setSleepGoalWheelPaint() {
        Paint paint = new Paint();
        this.mSleepGoalWheelPaint = paint;
        paint.setAntiAlias(true);
        this.mSleepGoalWheelPaint.setDither(true);
        this.mSleepGoalWheelPaint.setStrokeWidth(this.mSleepGoalWheelStrokeWidth);
        this.mSleepGoalWheelPaint.setStyle(Paint.Style.STROKE);
        this.mSleepGoalWheelPaint.setStrokeJoin(Paint.Join.ROUND);
        this.mSleepGoalWheelPaint.setStrokeCap(Paint.Cap.ROUND);
        this.mSleepGoalWheelPaint.setColor(getResources().getColor(R.color.sesl_sleep_goal_wheel_color));
    }

    public final void setFirstPointerPaint() {
        Paint paint = new Paint();
        this.mFirstPointerPaint = paint;
        paint.set(this.mSecondPointerPaint);
        this.mFirstPointerPaint.setColor(this.mFirstPointerColor);
        Paint paint2 = new Paint();
        this.mFirstPointerHaloPaint = paint2;
        paint2.set(this.mSecondPointerHaloPaint);
        this.mFirstPointerHaloPaint.setColor(this.mFirstPointerHaloColor);
        this.mFirstPointerHaloPaint.setStrokeWidth(this.mPointerStrokeWidth);
    }

    public final void setSecondPointerPaint() {
        Paint paint = new Paint();
        this.mSecondPointerPaint = paint;
        paint.setAntiAlias(true);
        this.mSecondPointerPaint.setDither(true);
        this.mSecondPointerPaint.setColor(this.mSecondPointerColor);
        this.mSecondPointerPaint.setStrokeWidth(this.mPointerStrokeWidth);
        this.mSecondPointerPaint.setStyle(Paint.Style.STROKE);
        this.mSecondPointerPaint.setStrokeJoin(Paint.Join.ROUND);
        this.mSecondPointerPaint.setStrokeCap(this.mCircleStyle);
        Paint paint2 = new Paint();
        this.mSecondPointerHaloPaint = paint2;
        paint2.set(this.mSecondPointerPaint);
        this.mSecondPointerHaloPaint.setColor(this.mSecondPointerHaloColor);
        this.mSecondPointerHaloPaint.setStrokeWidth(this.mPointerStrokeWidth);
    }

    public final void setClockGridPaint() {
        Paint paint = new Paint(1);
        this.mGridPaintSmall = paint;
        paint.setStrokeWidth(this.DPTOPX_SCALE * 1.0f);
        this.mGridPaintSmall.setColor(this.mCircleGridSmall);
        this.mGridPaintSmall.setStyle(Paint.Style.STROKE);
        Paint paint2 = new Paint(1);
        this.mGridPaintMedium = paint2;
        paint2.setStrokeWidth(this.DPTOPX_SCALE * 1.0f);
        this.mGridPaintMedium.setColor(this.mCircleGridMedium);
        this.mGridPaintMedium.setStyle(Paint.Style.STROKE);
    }

    public final void setDotLinePaint() {
        Path path = new Path();
        float f = this.mDashLineStrokeWidth / 2.0f;
        path.addCircle(f, 0.0f, f, Path.Direction.CW);
        Paint paint = new Paint();
        this.mCircleLineProgressPaint = paint;
        paint.setStyle(Paint.Style.STROKE);
        this.mCircleLineProgressPaint.setStrokeWidth(this.mDashLineStrokeWidth);
        this.mCircleLineProgressPaint.setColor(getResources().getColor(R.color.sesl_dotted_line_color));
        this.mCircleLineProgressPaint.setPathEffect(new PathDashPathEffect(path,
                this.mDashLineStrokeWidth + getResources().getDimension(R.dimen.sesl_dot_line_gap_width), 0.0f, PathDashPathEffect.Style.ROTATE));
    }

    public final void calculateTotalDegrees() {
        float f = (360.0f - (this.mStartAngle - this.mEndAngle)) % 360.0f;
        this.mTotalCircleDegrees = f;
        if (f <= 0.0f) {
            this.mTotalCircleDegrees = 360.0f;
        }
    }

    public final void calculateProgressDegrees() {
        float f = this.mSecondPointerPosition - this.mFirstPointerPosition;
        this.mProgressDegrees = f;
        if (f < 0.0f) {
            f += 360.0f;
        }
        this.mProgressDegrees = f;
    }

    public final void calculatePointerPosition(int i) {
        float f = (this.mProgress / this.mMax) * 360.0f;
        if (i == 1) {
            float f2 = this.mSecondPointerPosition - f;
            this.mFirstPointerPosition = f2;
            if (f2 < 0.0f) {
                f2 += 360.0f;
            }
            this.mFirstPointerPosition = f2 % 360.0f;
        } else if (i == 0) {
            float f3 = this.mFirstPointerPosition + f;
            this.mSecondPointerPosition = f3;
            if (f3 < 0.0f) {
                f3 += 360.0f;
            }
            this.mSecondPointerPosition = f3 % 360.0f;
        }
    }

    public final void calculateHandlerPosition() {
        float f = this.mHandlerTouchPosition;
        float f2 = f - this.mTouchDistanceFromFirstPointer;
        this.mFirstPointerPosition = f2;
        if (f2 < 0.0f) {
            f2 += 360.0f;
        }
        this.mFirstPointerPosition = f2 % 360.0f;
        float f3 = f + this.mTouchDistanceFromSecondPointer;
        this.mSecondPointerPosition = f3;
        if (f3 < 0.0f) {
            f3 += 360.0f;
        }
        this.mSecondPointerPosition = f3 % 360.0f;
    }

    public final void initPaths() {
        this.mCirclePath = new Path();
        this.mCircleProgressPath = new Path();
        this.mCircleLineProgressPath = new Path();
        this.mCircleSecondPointerPath = new Path();
        this.mCircleFirstPointerPath = new Path();
        this.mSleepGoalWheelPath = new Path();
    }

    public final void resetPaths() {
        this.mCirclePath.reset();
        this.mCirclePath.addArc(this.mCircleRectF, this.mStartAngle, this.mTotalCircleDegrees);
        float f = this.mFirstPointerPosition;
        float f2 = this.mSecondPointerAngle;
        float f3 = f - (f2 / 2.0f);
        float f4 = this.mSecondPointerPosition - (this.mFirstPointerAngle / 2.0f);
        float f5 = this.mProgressDegrees + f2;
        if (f5 >= 360.0f) {
            f5 = 359.9f;
        }
        this.mCircleProgressPath.reset();
        this.mCircleProgressPath.addArc(this.mCircleRectF, f3, f5);
        if (this.mSleepGoalWheelEnable) {
            this.mSleepGoalWheelPath.reset();
            this.mSleepGoalWheelPath.addArc(this.mSleepGoalWheelRectF,
                    this.mSleepGoalWheelExtendStart, this.mSleepGoalWheelExtendDegree);
        }
        this.mCircleLineProgressPath.reset();
        if (this.mProgress > 6.5d) {
            if (this.mUserIsMovingSecondPointer) {
                this.mCircleLineProgressPath.addArc(this.mCircleRectF, f4, -f5);
            } else {
                this.mCircleLineProgressPath.addArc(this.mCircleRectF, f3, f5);
            }
        }
        float f6 = this.mSecondPointerPosition - (this.mSecondPointerAngle / 2.0f);
        this.mCircleSecondPointerPath.reset();
        this.mCircleSecondPointerPath.addArc(this.mCircleRectF, f6, this.mSecondPointerAngle);
        float f7 = this.mFirstPointerPosition - (this.mFirstPointerAngle / 2.0f);
        this.mCircleFirstPointerPath.reset();
        this.mCircleFirstPointerPath.addArc(this.mCircleRectF, f7, this.mFirstPointerAngle);
    }

    public final void resetRects() {
        RectF rectF = this.mCircleRectF;
        float f = this.mCircleWidth;
        float f2 = this.mCircleHeight;
        rectF.set(-f, -f2, f, f2);
        this.mSleepGoalWheelRectF.left = this.mCircleRectF.centerX() - (this.mRadiusIn - 5.0f);
        this.mSleepGoalWheelRectF.top = this.mCircleRectF.centerY() - (this.mRadiusIn - 5.0f);
        this.mSleepGoalWheelRectF.right = this.mCircleRectF.centerY() + (this.mRadiusIn - 5.0f);
        this.mSleepGoalWheelRectF.bottom = this.mCircleRectF.centerY() + (this.mRadiusIn - 5.0f);
    }

    /* loaded from: classes.dex */
    public class SweepGradientVariable {
        public int[] color = new int[5];
        public float[] pos = new float[5];

        public SweepGradientVariable() {
        }
    }

    @Override
    public void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.translate(getWidth() / 2.0f, getHeight() / 2.0f);
        if (this.mSleepGoalWheelEnable) {
            canvas.drawPath(this.mSleepGoalWheelPath, this.mSleepGoalWheelPaint);
        }
        canvas.drawPath(this.mCirclePath, this.mCircleFillPaint);
        canvas.drawPath(this.mCirclePath, this.mCirclePaint);
        drawClockGrid(canvas);
        SweepGradientVariable sweepGradientVariable = this.mSweepGradientVariable;
        int[] iArr = sweepGradientVariable.color;
        int i = this.mFirstPointerColor;
        iArr[0] = i;
        iArr[1] = i;
        iArr[2] = this.mMiddleGradientColor;
        int i2 = this.mSecondPointerColor;
        iArr[3] = i2;
        iArr[4] = i2;
        float[] fArr = sweepGradientVariable.pos;
        fArr[0] = 0.0f;
        float f = this.mProgress / this.mMax;
        fArr[1] = 0.1f * f;
        fArr[2] = 0.5f * f;
        fArr[3] = 0.9f * f;
        fArr[4] = f;
        float centerX = this.mCircleRectF.centerX();
        float centerY = this.mCircleRectF.centerY();
        SweepGradientVariable sweepGradientVariable2 = this.mSweepGradientVariable;
        SweepGradient sweepGradient = new SweepGradient(centerX, centerY,
                sweepGradientVariable2.color, sweepGradientVariable2.pos);
        Matrix matrix = new Matrix();
        matrix.setRotate(this.mFirstPointerPosition, this.mCircleRectF.centerX(),
                this.mCircleRectF.centerY());
        sweepGradient.setLocalMatrix(matrix);
        this.mCircleProgressPaint.setShader(sweepGradient);
        canvas.drawPath(this.mCircleProgressPath, this.mCircleProgressPaint);
        canvas.drawPath(this.mCircleLineProgressPath, this.mCircleLineProgressPaint);
        if (this.mLastPointerTouched == 0) {
            drawSecondPointer(canvas);
            drawFirstPointer(canvas);
            return;
        }
        drawFirstPointer(canvas);
        drawSecondPointer(canvas);
    }

    public final void drawFirstPointer(Canvas canvas) {
        RectF rectF;
        canvas.drawPath(this.mCircleFirstPointerPath, this.mFirstPointerPaint);
        if (this.mIsExpandCollapseAnimation || this.mUserIsMovingFirstPointer) {
            canvas.drawPath(this.mCircleFirstPointerPath, this.mFirstPointerHaloPaint);
        }
        Drawable drawable = this.mBedTimeDrawable;
        if (drawable == null || (rectF = this.mBedTimeIconRectF) == null) {
            return;
        }
        drawable.setBounds((int) rectF.left, (int) rectF.top, (int) rectF.right,
                (int) rectF.bottom);
        this.mBedTimeDrawable.draw(canvas);
    }

    public final void drawSecondPointer(Canvas canvas) {
        RectF rectF;
        canvas.drawPath(this.mCircleSecondPointerPath, this.mSecondPointerPaint);
        if (this.mIsExpandCollapseAnimation || this.mUserIsMovingSecondPointer) {
            canvas.drawPath(this.mCircleSecondPointerPath, this.mSecondPointerHaloPaint);
        }
        Drawable drawable = this.mWakeUpDrawable;
        if (drawable == null || (rectF = this.mWakeUpTimeIconRectF) == null) {
            return;
        }
        drawable.setBounds((int) rectF.left, (int) rectF.top, (int) rectF.right,
                (int) rectF.bottom);
        this.mWakeUpDrawable.draw(canvas);
    }

    public void findBedTimeIconLocation() {
        double d = (this.mFirstPointerPosition / 180.0f) * 3.141592653589793d;
        this.mBedTimeIconRectF.left =
                ((float) (this.mCircleRectF.centerX() + (this.mRadiusOut * Math.cos(d)))) - (this.mIconSize / 2.0f);
        RectF rectF = this.mBedTimeIconRectF;
        float f = this.mIconSize;
        rectF.top =
                ((float) (this.mCircleRectF.centerY() + (this.mRadiusOut * Math.sin(d)))) - (f / 2.0f);
        RectF rectF2 = this.mBedTimeIconRectF;
        rectF2.right = rectF2.left + f;
        rectF2.bottom = rectF2.top + f;
    }

    public void findWakeUpTimeIconLocation() {
        double d = (this.mSecondPointerPosition / 180.0f) * 3.141592653589793d;
        this.mWakeUpTimeIconRectF.left =
                ((float) (this.mCircleRectF.centerX() + (this.mRadiusOut * Math.cos(d)))) - (this.mIconSize / 2.0f);
        RectF rectF = this.mWakeUpTimeIconRectF;
        float f = this.mIconSize;
        rectF.top =
                ((float) (this.mCircleRectF.centerY() + (this.mRadiusOut * Math.sin(d)))) - (f / 2.0f);
        RectF rectF2 = this.mWakeUpTimeIconRectF;
        rectF2.right = rectF2.left + f;
        rectF2.bottom = rectF2.top + f;
    }

    public void drawClockGrid(@NonNull Canvas canvas) {
        for (double d = 0.0d; d <= 360.0d; d += 2.5d) {
            double d2 = ((this.mStartAngle + d) / 180.0d) * 3.141592653589793d;
            float centerX =
                    (float) (this.mCircleRectF.centerX() + ((this.mRadiusIn - (this.DPTOPX_SCALE * 2.5f)) * Math.cos(d2)));
            float centerY =
                    (float) (this.mCircleRectF.centerY() + ((this.mRadiusIn - (this.DPTOPX_SCALE * 2.5f)) * Math.sin(d2)));
            float centerX2 =
                    (float) (this.mCircleRectF.centerX() + ((this.mRadiusIn + (this.DPTOPX_SCALE * 2.5f)) * Math.cos(d2)));
            float centerY2 =
                    (float) (this.mCircleRectF.centerY() + ((this.mRadiusIn + (this.DPTOPX_SCALE * 2.5f)) * Math.sin(d2)));
            double d3 = d % 90.0d;
            if (d3 != Double.longBitsToDouble(1) && d3 != 2.5d && d3 != 3.0d && d3 != 87.0d && d3 != 87.5d && d != 175.0d && d != 185.0d) {
                if (d % 15.0d == Double.longBitsToDouble(1)) {
                    canvas.drawLine(centerX, centerY, centerX2, centerY2, this.mGridPaintMedium);
                } else {
                    canvas.drawLine(centerX, centerY, centerX2, centerY2, this.mGridPaintSmall);
                }
            }
        }
    }

    public final void recalculateAll() {
        calculateTotalDegrees();
        if (this.mUserIsMovingSecondPointer) {
            calculatePointerPosition(0);
        } else if (this.mUserIsMovingFirstPointer) {
            calculatePointerPosition(1);
        } else if (this.mUserIsMovingMiddleHandler) {
            calculateHandlerPosition();
        }
        calculateProgressDegrees();
        resetRects();
        resetPaths();
        findBedTimeIconLocation();
        findWakeUpTimeIconLocation();
    }

    public final void setProgressBasedOnAngle(float f, int i) {
        if (i == 0) {
            this.mSecondPointerPosition = f;
        } else if (i == 1) {
            this.mFirstPointerPosition = f;
        } else if (i == 2) {
            this.mHandlerTouchPosition = f;
            float f2 = f - this.mTouchDistanceFromFirstPointer;
            this.mFirstPointerPosition = f2;
            if (f2 < 0.0f) {
                f2 += 360.0f;
            }
            this.mFirstPointerPosition = f2 % 360.0f;
            float f3 = f + this.mTouchDistanceFromSecondPointer;
            this.mSecondPointerPosition = f3;
            if (f3 < 0.0f) {
                f3 += 360.0f;
            }
            this.mSecondPointerPosition = f3 % 360.0f;
        }
        calculateProgressDegrees();
        this.mProgress = (this.mMax * this.mProgressDegrees) / this.mTotalCircleDegrees;
    }

    @Override
    public void onSizeChanged(int i, int i2, int i3, int i4) {
        super.onSizeChanged(i, i2, i3, i4);
    }

    @Override
    public void onMeasure(int i, int i2) {
        int defaultSize = View.getDefaultSize(getSuggestedMinimumHeight(), i2);
        int defaultSize2 = View.getDefaultSize(getSuggestedMinimumWidth(), i);
        if (defaultSize == 0) {
            defaultSize = defaultSize2;
        }
        if (defaultSize2 == 0) {
            defaultSize2 = defaultSize;
        }
        if (this.mMaintainEqualCircle) {
            int min = Math.min(defaultSize2, defaultSize);
            setMeasuredDimension(min, min);
        } else {
            setMeasuredDimension(defaultSize2, defaultSize);
        }
        this.mPointerStrokeWidth =
                getResources().getDimension(R.dimen.sesl_sleep_time_pointer_size);
        float dimension = getResources().getDimension(R.dimen.sesl_sleep_time_icon_touch_width);
        this.mPointerHaloWidth = dimension;
        float f = (this.mPointerStrokeWidth / 2.0f) + dimension;
        float f2 =
                getResources().getConfiguration().screenWidthDp * getResources().getDisplayMetrics().density;
        float dimension2 =
                getResources().getDimension(R.dimen.sesl_sleep_visual_edit_outer_circle_size);
        if (SeslSleepTimePickerUtil.needBedTimePickerAdjustment(getResources().getConfiguration().screenHeightDp)) {
            dimension2 =
                    (int) getResources().getDimension(R.dimen.sesl_sleep_visual_edit_outer_circle_min_size);
        }
        float f3 = (f2 / 2.0f) - f;
        this.mCircleWidth = f3;
        float f4 = (dimension2 / 2.0f) - f;
        this.mCircleHeight = f4;
        if (this.mMaintainEqualCircle) {
            float min2 = Math.min(f4, f3);
            this.mCircleHeight = min2;
            this.mCircleWidth = min2;
        }
        TypedValue typedValue = new TypedValue();
        getResources().getValue(R.dimen.sesl_time_picker_inner_circle_container_ratio, typedValue
                , true);
        float f5 = this.mCircleHeight;
        this.mRadiusOut = f5;
        this.mRadiusIn = f5 * typedValue.getFloat();
        recalculateAll();
    }

    public boolean onActionDown(float f, float f2, float f3, float f4) {
        float max =
                Math.max((float) ((this.mPointerStrokeWidth * 180.0f) / (Math.max(this.mCircleHeight, this.mCircleWidth) * 3.141592653589793d)), this.mSecondPointerAngle / 2.0f);
        float f5 = f - this.mSecondPointerPosition;
        if (f5 < 0.0f) {
            f5 += 360.0f;
        }
        float f6 = 360.0f - f5;
        float f7 = this.mFirstPointerPosition;
        float f8 = f - f7;
        if (f8 < 0.0f) {
            f8 += 360.0f;
        }
        float f9 = 360.0f - f8;
        boolean z = f2 >= f3 && f2 <= f4;
        boolean z2 = f5 <= max || f6 <= max;
        boolean z3 = f8 <= max || f9 <= max;
        boolean z4 = isZ4(f, f7);
        if (z && z2 && z3) {
            if (this.mLastPointerTouched == 0) {
                initTouchOnFirstPointer();
            } else {
                initTouchOnSecondPointer();
            }
        } else if (z && z2) {
            initTouchOnSecondPointer();
        } else if (z && z3) {
            initTouchOnFirstPointer();
        } else if (z && z4) {
            this.mHandlerTouchPosition = f;
            initTouchOnMiddleHandler();
        } else {
            this.mUserIsMovingSecondPointer = false;
            this.mUserIsMovingFirstPointer = false;
            this.mUserIsMovingMiddleHandler = false;
            return false;
        }
        return true;
    }

    private boolean isZ4(float f, float f7) {
        float convertToTime = SeslSleepTimePickerUtil.convertToTime(f7);
        float convertToTime2 = SeslSleepTimePickerUtil.convertToTime(this.mSecondPointerPosition);
        float convertToTime3 = SeslSleepTimePickerUtil.convertToTime(f);
        return convertToTime >= convertToTime2
                ?
                !(convertToTime <= convertToTime2 || ((convertToTime3 <= convertToTime || convertToTime3 > 1440.0f) && (convertToTime3 >= convertToTime2 || convertToTime3 <= 0.0f)))
                : !(convertToTime3 <= convertToTime || convertToTime3 >= convertToTime2);
    }

    @Override 
    public boolean onTouchEvent(MotionEvent motionEvent) {
        if (!isEnabled() || this.mCircularSeekBarRevealAnimation.isRevealAnimation()) {
            return false;
        }
        this.mTouchEventVariable.x = motionEvent.getX() - (getWidth() / 2.0f);
        this.mTouchEventVariable.y = motionEvent.getY() - (getHeight() / 2.0f);
        TouchEventVariable touchEventVariable = this.mTouchEventVariable;
        float centerX = this.mCircleRectF.centerX();
        TouchEventVariable touchEventVariable2 = this.mTouchEventVariable;
        touchEventVariable.distanceX = centerX - touchEventVariable2.x;
        float centerY = this.mCircleRectF.centerY();
        TouchEventVariable touchEventVariable3 = this.mTouchEventVariable;
        touchEventVariable2.distanceY = centerY - touchEventVariable3.y;
        touchEventVariable3.touchEventRadius =
                (float) Math.sqrt(Math.pow(touchEventVariable3.distanceX, 2.0d) + Math.pow(this.mTouchEventVariable.distanceY, 2.0d));
        TouchEventVariable touchEventVariable4 = this.mTouchEventVariable;
        float f = this.DPTOPX_SCALE * 48.0f;
        touchEventVariable4.minimumTouchTarget = f;
        float f2 = this.mCircleStrokeWidth;
        touchEventVariable4.additionalRadius = f2 < f ? f / 2.0f : f2 / 2.0f;
        float max = Math.max(this.mCircleHeight, this.mCircleWidth);
        TouchEventVariable touchEventVariable5 = this.mTouchEventVariable;
        touchEventVariable4.outerRadius = max + touchEventVariable5.additionalRadius;
        float min = Math.min(this.mCircleHeight, this.mCircleWidth);
        TouchEventVariable touchEventVariable6 = this.mTouchEventVariable;
        touchEventVariable5.innerRadius = min - touchEventVariable6.additionalRadius;
        touchEventVariable6.touchAngle = (float) (((Math.atan2(touchEventVariable6.y,
                touchEventVariable6.x) / 3.141592653589793d) * 180.0d) % 360.0d);
        TouchEventVariable touchEventVariable7 = this.mTouchEventVariable;
        float f3 = touchEventVariable7.touchAngle;
        if (f3 < 0.0f) {
            f3 += 360.0f;
        }
        touchEventVariable7.touchAngle = f3;
        int action = motionEvent.getAction();
        if (action == 0) {
            TouchEventVariable touchEventVariable8 = this.mTouchEventVariable;
            return onActionDown(touchEventVariable8.touchAngle,
                    touchEventVariable8.touchEventRadius, touchEventVariable8.innerRadius,
                    touchEventVariable8.outerRadius);
        } else if (action != 1) {
            if (action == 2) {
                TouchEventVariable touchEventVariable9 = this.mTouchEventVariable;
                return onActionMove(touchEventVariable9.outerRadius,
                        touchEventVariable9.touchEventRadius, touchEventVariable9.touchAngle);
            } else if (action != 3) {
                return true;
            } else {
                Log.d("CircularSeekBar", "MotionEvent.ACTION_CANCEL");
                return onActionUpCancel();
            }
        } else {
            return onActionUpCancel();
        }
    }

    public final boolean onActionUpCancel() {
        boolean userIsMovingSecondPointer = this.mUserIsMovingSecondPointer;
        if (userIsMovingSecondPointer || this.mUserIsMovingFirstPointer || this.mUserIsMovingMiddleHandler) {
            this.mUserIsMovingSecondPointer = false;
            this.mUserIsMovingFirstPointer = false;
            this.mUserIsMovingMiddleHandler = false;
            throw null;
        }
        return false;
    }

    public final boolean onActionMove(float f, float f2, float f3) {
        TouchEventVariable touchEventVariable = this.mTouchEventVariable;
        float f4 = this.mTotalCircleDegrees;
        float f5 = f4 / 3.0f;
        touchEventVariable.smallInCircle = f5;
        float f6 = this.mSecondPointerPosition;
        float f7 = this.mFirstPointerPosition;
        float f8 = f6 - f7;
        touchEventVariable.cwPointerFromStart = f8;
        if (f8 < 0.0f) {
            f8 += 360.0f;
        }
        touchEventVariable.cwPointerFromStart = f8;
        boolean z = f8 < f5;
        touchEventVariable.pointerNearStart = z;
        boolean z2 = f8 > f4 - f5;
        touchEventVariable.pointerNearEnd = z2;
        if (this.mUserIsMovingSecondPointer) {
            float f9 = f3 - ((f7 + 2.5f) % 360.0f);
            touchEventVariable.cwDistanceFromStart = f9;
            if (f9 < 0.0f) {
                f9 += 360.0f;
            }
            touchEventVariable.cwDistanceFromStart = f9;
            float f10 = 360.0f - f9;
            touchEventVariable.ccwDistanceFromStart = f10;
            float f11 = f3 - (((f7 - 2.5f) + 360.0f) % 360.0f);
            touchEventVariable.cwDistanceFromEnd = f11;
            if (f11 < 0.0f) {
                f11 += 360.0f;
            }
            touchEventVariable.cwDistanceFromEnd = f11;
            touchEventVariable.touchOverStart = f10 < f5;
            touchEventVariable.touchOverEnd = f11 < f5;
            this.mLockEnabled = true;
        } else if (this.mUserIsMovingFirstPointer) {
            float f12 = f3 - (((f6 - 2.5f) + 360.0f) % 360.0f);
            touchEventVariable.cwDistanceFromStart = f12;
            if (f12 < 0.0f) {
                f12 += 360.0f;
            }
            touchEventVariable.cwDistanceFromStart = f12;
            float f13 = f3 - ((f6 + 2.5f) % 360.0f);
            touchEventVariable.cwDistanceFromEnd = f13;
            if (f13 < 0.0f) {
                f13 += 360.0f;
            }
            touchEventVariable.cwDistanceFromEnd = f13;
            float f14 = 360.0f - f13;
            touchEventVariable.ccwDistanceFromEnd = f14;
            touchEventVariable.touchOverStart = f12 < f5;
            touchEventVariable.touchOverEnd = f14 < f5;
            this.mLockEnabled = true;
        } else if (!this.mUserIsMovingMiddleHandler) {
            return false;
        } else {
            this.mLockAtEnd = false;
            this.mLockAtStart = false;
            this.mLockEnabled = false;
        }
        if (z2) {
            this.mLockAtEnd = touchEventVariable.touchOverEnd;
        } else if (z) {
            this.mLockAtStart = touchEventVariable.touchOverStart;
        }
        if (this.mLockAtStart && this.mLockEnabled) {
            if (this.mProgress != 0.6944445f) {
                this.mProgress = 0.6944445f;
                recalculateAll();
                invalidate();
                addPointerTouchListener();
                SeslSleepTimePickerUtil.performHapticFeedback(this, 49);
            }
        } else if (this.mLockAtEnd && this.mLockEnabled) {
            float f15 = this.mProgress;
            float f16 = this.mMax;
            if (f15 != f16 - 0.6944445f) {
                this.mProgress = f16 - 0.6944445f;
                recalculateAll();
                invalidate();
                addPointerTouchListener();
                SeslSleepTimePickerUtil.performHapticFeedback(this, 49);
            }
        } else if (this.mMoveOutsideCircle || f2 <= f) {
            boolean z3 = this.mUserIsMovingFirstPointer;
            if (this.mUserIsMovingMiddleHandler) {
                setProgressBasedOnAngle(f3, 2);
            } else {
                setProgressBasedOnAngle(f3, z3 ? 1 : 0);
            }
            recalculateAll();
            invalidate();
            addPointerTouchListener();
        }
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
    }

    public final void initTouchOnMiddleHandler() {
        float f = this.mHandlerTouchPosition;
        this.mTouchDistanceFromFirstPointer = f - this.mFirstPointerPosition;
        this.mTouchDistanceFromSecondPointer = this.mSecondPointerPosition - f;
    }

    public final void init() {
        TypedArray obtainStyledAttributes =
                getContext().obtainStyledAttributes(this.mAttributeSet,
                        R.styleable.seslCircularSeekBar, this.mDefStyle, 0);
        initAttributes(obtainStyledAttributes);
        obtainStyledAttributes.recycle();
        initDrawableIcons();
        initPaints();
        initPaths();
        this.mTouchEventVariable = new TouchEventVariable();
        this.mSweepGradientVariable = new SweepGradientVariable();
    }

    @Override
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        requestLayout();
        invalidate();
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable onSaveInstanceState = super.onSaveInstanceState();
        Bundle bundle = new Bundle();
        bundle.putParcelable("PARENT", onSaveInstanceState);
        bundle.putFloat("MAX", this.mMax);
        bundle.putFloat("PROGRESS", this.mProgress);
        bundle.putFloat("mProgressDegrees", this.mProgressDegrees);
        bundle.putFloat("mSecondPointerPosition", this.mSecondPointerPosition);
        bundle.putFloat("mFirstPointerPosition", this.mFirstPointerPosition);
        bundle.putFloat("mSecondPointerAngle", this.mSecondPointerAngle);
        bundle.putBoolean("mLockEnabled", this.mLockEnabled);
        bundle.putBoolean("mLockAtStart", this.mLockAtStart);
        bundle.putBoolean("mLockAtEnd", this.mLockAtEnd);
        bundle.putInt("mCircleStyle", this.mCircleStyle.ordinal());
        bundle.putInt("mLastPointerTouched", this.mLastPointerTouched);
        bundle.putBoolean("mHideProgressWhenEmpty", this.mHideProgressWhenEmpty);
        return bundle;
    }

    @Override
    public void onRestoreInstanceState(Parcelable parcelable) {
        Bundle bundle = (Bundle) parcelable;
        super.onRestoreInstanceState(bundle.getParcelable("PARENT"));
        this.mMax = bundle.getFloat("MAX");
        this.mProgress = bundle.getFloat("PROGRESS");
        this.mProgressDegrees = bundle.getFloat("mProgressDegrees");
        this.mSecondPointerPosition = bundle.getFloat("mSecondPointerPosition");
        this.mFirstPointerPosition = bundle.getFloat("mFirstPointerPosition");
        this.mSecondPointerAngle = bundle.getFloat("mSecondPointerAngle");
        this.mLockEnabled = bundle.getBoolean("mLockEnabled");
        this.mLockAtStart = bundle.getBoolean("mLockAtStart");
        this.mLockAtEnd = bundle.getBoolean("mLockAtEnd");
        this.mCircleStyle = Paint.Cap.values()[bundle.getInt("mCircleStyle")];
        this.mLastPointerTouched = bundle.getInt("mLastPointerTouched");
        this.mHideProgressWhenEmpty = bundle.getBoolean("mHideProgressWhenEmpty");
        initPaints();
        recalculateAll();
    }
}
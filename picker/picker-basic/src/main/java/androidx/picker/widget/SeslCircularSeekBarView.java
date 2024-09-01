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

import static android.view.MotionEvent.ACTION_CANCEL;
import static android.view.MotionEvent.ACTION_DOWN;
import static android.view.MotionEvent.ACTION_MOVE;
import static android.view.MotionEvent.ACTION_UP;

import static androidx.picker.util.SeslSleepTimePickerUtil.needBedTimePickerAdjustment;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
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
import android.os.Bundle;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.picker.R;
import androidx.picker.util.SeslSleepTimePickerUtil;


public class SeslCircularSeekBarView extends View {

    public interface OnCircularSeekBarChangeListener {
        void onProgressChangedBedTime(@NonNull SeslCircularSeekBarView seslCircularSeekBarView,
                float bedTimePosition);
        void onProgressChangedWakeupTime(@NonNull SeslCircularSeekBarView seslCircularSeekBarView,
                float wakeupPosition);
        void onSelectBedTimeIcon();
        void onSelectMiddleHandler();
        void onSelectWakeUpTimeIcon();
        void onStartTrackingTouch(@NonNull SeslCircularSeekBarView seslCircularSeekBarView);
        void onStopTrackingTouch(@NonNull SeslCircularSeekBarView seslCircularSeekBarView);
        void onUnselectBedTimeIcon();
        void onUnselectMiddleHandler();
        void onUnselectWakeUpTimeIcon();
    }

    private static final int DEFAULT_CIRCLE_STYLE = Paint.Cap.ROUND.ordinal();
    private static final int DEFAULT_FIRST_POINTER_COLOR = Color.argb(255, 133, 135, 254);
    private static final int DEFAULT_MIDDLE_COLOR = Color.argb(255, 133, 135, 254);
    private static final int DEFAULT_FIRST_POINTER_HALO_COLOR = Color.argb(255, 133, 135, 254);
    private static final int DEFAULT_SECOND_POINTER_COLOR = Color.argb(255, 255, 167, 0);
    private static final int DEFAULT_SECOND_POINTER_HALO_COLOR = Color.argb(255, 255, 167, 0);
    private final PathInterpolator EXPAND_COLLAPSE_PATH_INTERPOLATOR = new PathInterpolator(0.22f, 0.25f, 0.0f, 1.0f);

    public static final class SweepGradientVariable {
        public final int[] color = new int[5];
        public final float[] pos = new float[5];
    }

    private final float DPTOPX_SCALE;
    private final AttributeSet mAttributeSet;
    private Drawable mBedTimeDrawable;
    private final RectF mBedTimeIconRectF = new RectF();

    private int mCircleColor;
    private int mCircleFillColor;
    private Paint mCircleFillPaint;
    private Path mCircleFirstPointerPath;
    private int mCircleGridMedium;
    private int mCircleGridSmall;
    private float mCircleHeight;
    private Paint mCircleLineProgressPaint;
    private Path mCircleLineProgressPath;
    private Paint mCirclePaint;
    private Path mCirclePath;
    private Paint mCircleProgressPaint;
    private Path mCircleProgressPath;
    private final RectF mCircleRectF= new RectF();
    private Path mCircleSecondPointerPath;
    private float mCircleStrokeWidth;
    private Paint.Cap mCircleStyle;
    private float mCircleWidth;

    private float mDashLineStrokeWidth;
    private final int mDefStyle;
    private float mEndAngle;
    private float mFirstPointerAngle;
    private int mFirstPointerColor;
    private int mFirstPointerHaloColor;
    Paint mFirstPointerHaloPaint;
    private Paint mFirstPointerPaint;
    float mFirstPointerPosition;
    private Paint mGridPaintMedium;
    private Paint mGridPaintSmall;
    private float mHandlerTouchPosition;
    private boolean mHideProgressWhenEmpty;
    private float mIconSize;
    private boolean mIsExpandCollapseAnimation = false;
    private int mLastPointerTouched;

    private boolean mLockEnabled = true;
    private boolean mLockAtStart = true;
    private boolean mLockAtEnd = false;

    private boolean mMaintainEqualCircle;
    private float mMax;
    private int mMiddleGradientColor;
    private boolean mMoveOutsideCircle;
    float mPointerHaloWidth;
    float mPointerStrokeWidth;
    private float mProgress;
    float mProgressDegrees;
    private float mRadiusIn;
    private float mRadiusOut;
    private float mSecondPointerAngle;
    private int mSecondPointerColor;
    private int mSecondPointerHaloColor;
    Paint mSecondPointerHaloPaint;
    private Paint mSecondPointerPaint;
    private float mSecondPointerPosition;
    private boolean mSleepGoalWheelEnable = false;
    private float mSleepGoalWheelExtendDegree;
    private float mSleepGoalWheelExtendStart;
    private Paint mSleepGoalWheelPaint;
    private Path mSleepGoalWheelPath;
    private final RectF mSleepGoalWheelRectF = new RectF();
    private float mSleepGoalWheelStrokeWidth;
    private float mStartAngle;
    private final SweepGradientVariable mSweepGradientVariable = new SweepGradientVariable();
    private float mTotalCircleDegrees;
    private float mTouchDistanceFromFirstPointer;
    private float mTouchDistanceFromSecondPointer;
    private final TouchEventVariable mTouchEventVariable = new TouchEventVariable();
    private boolean mUserIsMovingFirstPointer = false;
    private boolean mUserIsMovingMiddleHandler = false;
    private boolean mUserIsMovingSecondPointer = false;
    private Drawable mWakeUpDrawable;
    private final RectF mWakeUpTimeIconRectF = new RectF();
    private float mInnerCircleRatio;
    private int mOuterCircleSize;
    private int mOuterCircleMinSize;

    private OnCircularSeekBarChangeListener mOnCircularSeekBarChangeListener;

    private final SeslCircularSeekBarRevealAnimation mCircularSeekBarRevealAnimation = new SeslCircularSeekBarRevealAnimation(this);

    public void setOnSeekBarChangeListener(@NonNull OnCircularSeekBarChangeListener onCircularSeekBarChangeListener) {
        this.mOnCircularSeekBarChangeListener = onCircularSeekBarChangeListener;
    }

    public SeslCircularSeekBarView(@NonNull Context context) {
        this(context, null);
    }

    public SeslCircularSeekBarView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SeslCircularSeekBarView(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        DPTOPX_SCALE = getResources().getDisplayMetrics().density;
        mAttributeSet = attrs;
        mDefStyle = defStyleAttr;
        init();
    }

    private void dispatchCallback() {
        OnCircularSeekBarChangeListener listener = this.mOnCircularSeekBarChangeListener;
        if (listener != null) {
            if (mUserIsMovingSecondPointer) {
                listener.onProgressChangedWakeupTime(this,
                        mSecondPointerPosition);
            } else if (mUserIsMovingFirstPointer) {
                listener.onProgressChangedBedTime(this,
                        mFirstPointerPosition);
            } else if (mUserIsMovingMiddleHandler) {
                listener.onProgressChangedWakeupTime(this,
                        mSecondPointerPosition);
                listener.onProgressChangedBedTime(this,
                        mFirstPointerPosition);
            }
        }
    }

    private void calculatePointerPosition(int pointer) {
        float activeDegrees = (mProgress / mMax) * 360.0f;
        if (pointer == 1) {
            float adjFirstPointerDegree = mSecondPointerPosition - activeDegrees;
            if (adjFirstPointerDegree < 0.0f) {
                adjFirstPointerDegree += 360.0f;
            }
            mFirstPointerPosition = adjFirstPointerDegree % 360.0f;
        } else if (pointer == 0) {
            float adjSecondPointerDegree = mFirstPointerPosition + activeDegrees;
            if (adjSecondPointerDegree < 0.0f) {
                adjSecondPointerDegree += 360.0f;
            }
            mSecondPointerPosition = adjSecondPointerDegree % 360.0f;
        }
    }

    final void calculateProgressDegrees() {
        float adjProgressDegrees = mSecondPointerPosition - mFirstPointerPosition;
        if (adjProgressDegrees < 0.0f) {
            adjProgressDegrees += 360.0f;
        }
        mProgressDegrees = adjProgressDegrees;
        Log.d("CSBV", "calculateProgressDegrees: " + mProgressDegrees + " mSecondPointerPosition"
                + ":"+mSecondPointerPosition + " mFirstPointerPosition:" + mFirstPointerPosition);
    }

    private void drawFirstPointer(@NonNull Canvas canvas) {
        RectF rectF;
        canvas.drawPath(mCircleFirstPointerPath, mFirstPointerPaint);
        if (mIsExpandCollapseAnimation || mUserIsMovingFirstPointer) {
            canvas.drawPath(mCircleFirstPointerPath, mFirstPointerHaloPaint);
        }
        Drawable drawable = mBedTimeDrawable;
        if (drawable == null || (rectF = mBedTimeIconRectF) == null) {
            return;
        }
        drawable.setBounds((int) rectF.left, (int) rectF.top, (int) rectF.right,
                (int) rectF.bottom);
        mBedTimeDrawable.draw(canvas);
    }

    private void drawSecondPointer(Canvas canvas) {
        RectF rectF;
        canvas.drawPath(mCircleSecondPointerPath, mSecondPointerPaint);
        if (mIsExpandCollapseAnimation || mUserIsMovingSecondPointer) {
            canvas.drawPath(mCircleSecondPointerPath, mSecondPointerHaloPaint);
        }
        Drawable drawable = mWakeUpDrawable;
        if (drawable == null || (rectF = mWakeUpTimeIconRectF) == null) {
            return;
        }
        drawable.setBounds((int) rectF.left, (int) rectF.top, (int) rectF.right,
                (int) rectF.bottom);
        mWakeUpDrawable.draw(canvas);
    }

    protected static class TouchEventVariable {
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

    private void init() {
        Resources res = getResources();
        initAttributes(res);
        initDrawableIcons(res);
        initPaints();
        initPaths();
    }

    private void initAttributes(Resources res) {
        TypedArray ta = getContext().obtainStyledAttributes(mAttributeSet,
                R.styleable.seslCircularSeekBar, mDefStyle, 0);

        mPointerStrokeWidth =
                ta.getDimension(R.styleable.seslCircularSeekBar_csPointerStrokeWidth,
                        res.getDimension(R.dimen.sesl_sleep_time_pointer_size));
        mIconSize = ta.getDimension(R.styleable.seslCircularSeekBar_csIconWidth, 50.0f);
        mPointerHaloWidth = ta.getDimension(R.styleable.seslCircularSeekBar_csPointerHaloWidth,
                res.getDimension(R.dimen.sesl_sleep_time_icon_touch_width));
        mCircleStrokeWidth = ta.getDimension(R.styleable.seslCircularSeekBar_csCircleStrokeWidth,
                15.0f);
        mCircleStyle = Paint.Cap.values()[ta.getInt(R.styleable.seslCircularSeekBar_CircleStyle,
                DEFAULT_CIRCLE_STYLE)];
        mMiddleGradientColor = ta.getColor(R.styleable.seslCircularSeekBar_csMiddleColor,
                DEFAULT_MIDDLE_COLOR);
        mFirstPointerColor = ta.getColor(R.styleable.seslCircularSeekBar_csFirstPointerColor,
                DEFAULT_FIRST_POINTER_COLOR);
        mFirstPointerHaloColor =
                ta.getColor(R.styleable.seslCircularSeekBar_csFirstPointerHaloColor,
                        DEFAULT_FIRST_POINTER_HALO_COLOR);
        mSecondPointerColor = ta.getColor(R.styleable.seslCircularSeekBar_csSecondPointerColor,
                DEFAULT_SECOND_POINTER_COLOR);
        mSecondPointerHaloColor =
                ta.getColor(R.styleable.seslCircularSeekBar_csSecondPointerHaloColor,
                        DEFAULT_SECOND_POINTER_HALO_COLOR);
        mCircleColor = ta.getColor(R.styleable.seslCircularSeekBar_csCircleColor, -3355444);
        mCircleFillColor = ta.getColor(R.styleable.seslCircularSeekBar_csCircleFill, 0);
        mCircleGridSmall = ta.getColor(R.styleable.seslCircularSeekBar_csCircleGridSmallColor,
                -3355444);
        mCircleGridMedium = ta.getColor(R.styleable.seslCircularSeekBar_csCircleGridMediumColor,
                -7829368);
        mMax = ta.getInt(R.styleable.seslCircularSeekBar_csMax, 100);
        mProgress = ta.getInt(R.styleable.seslCircularSeekBar_csProgress, 40);
        mMaintainEqualCircle =
                ta.getBoolean(R.styleable.seslCircularSeekBar_csMaintainEqualCircle, true);
        mMoveOutsideCircle = ta.getBoolean(R.styleable.seslCircularSeekBar_csMoveOutsideCircle,
                true);
        mLockEnabled = ta.getBoolean(R.styleable.seslCircularSeekBar_csLockEnabled, true);
        mHideProgressWhenEmpty =
                ta.getBoolean(R.styleable.seslCircularSeekBar_csHideProgressWhenEmpty, false);
        mSecondPointerPosition = 7.5f;
        mFirstPointerPosition = 225.0f;

        mStartAngle =
                ((ta.getFloat(R.styleable.seslCircularSeekBar_csStartAngle, 270.0f) % 360.0f) + 360.0f) % 360.0f;

        mEndAngle =
                ((ta.getFloat(R.styleable.seslCircularSeekBar_csEndAngle, 270.0f) % 360.0f) + 360.0f) % 360.0f;
        if (mStartAngle % 360.0f == mEndAngle % 360.0f) {
            mEndAngle = mEndAngle- 0.1f;
        }

        int i = R.styleable.seslCircularSeekBar_csPointerAngle;

        mSecondPointerAngle = ((ta.getFloat(i, 0.0f) % 360.0f) + 360.0f) % 360.0f;
        if (mSecondPointerAngle == 0.0f) {
            mSecondPointerAngle = 0.1f;
        }
        mFirstPointerAngle = ((ta.getFloat(i, 0.0f) % 360.0f) + 360.0f) % 360.0f;
        if (mFirstPointerAngle == 0.0f) {
            mFirstPointerAngle = 0.1f;
        }
        ta.recycle();


        mSleepGoalWheelStrokeWidth = res.getDimension(R.dimen.sesl_sleep_goal_wheel_width);
        mDashLineStrokeWidth = res.getDimension(R.dimen.sesl_dot_line_stroke_width);
        mInnerCircleRatio = getInnerCircleRatio(res);
        mOuterCircleSize = (int) res.getDimension(R.dimen.sesl_sleep_visual_edit_outer_circle_size);
        mOuterCircleMinSize =
                (int) res.getDimension(R.dimen.sesl_sleep_visual_edit_outer_circle_min_size);
    }

    private float getInnerCircleRatio(Resources res){
        TypedValue typedValue = new TypedValue();
        res.getValue(R.dimen.sesl_time_picker_inner_circle_container_ratio, typedValue, true);
        return typedValue.getFloat();
    }

    private void initDrawableIcons(Resources res) {
        Drawable drawable;
        mBedTimeDrawable = ResourcesCompat.getDrawable(res, R.drawable.sesl_bedtime, null)
                .mutate().getConstantState().newDrawable().mutate();
        mWakeUpDrawable = ResourcesCompat.getDrawable(res, R.drawable.sesl_wakeup, null)
                .mutate().getConstantState().newDrawable().mutate();

        if (mBedTimeDrawable == null || (drawable = mWakeUpDrawable) == null) {
            return;
        }

        PorterDuffColorFilter colorFilter =
                new PorterDuffColorFilter(res.getColor(R.color.sesl_picker_thumb_icon_color),
                        PorterDuff.Mode.SRC_ATOP);
        drawable.setColorFilter(colorFilter);
        mBedTimeDrawable.setColorFilter(colorFilter);
    }

    private void initPaints() {
        setCirclePaint();
        setCircleFillPaint();
        setCircleProgressPaint();
        setSleepGoalWheelPaint();
        setSecondPointerPaint();
        setFirstPointerPaint();
        setClockGridPaint();
        setDotLinePaint();
    }

    private void setCirclePaint() {
        Paint paint = new Paint();
        mCirclePaint = paint;
        paint.setAntiAlias(true);
        mCirclePaint.setDither(true);
        mCirclePaint.setColor(mCircleColor);
        mCirclePaint.setStrokeWidth(mCircleStrokeWidth);
        mCirclePaint.setStyle(Paint.Style.STROKE);
        mCirclePaint.setStrokeJoin(Paint.Join.ROUND);
        mCirclePaint.setStrokeCap(mCircleStyle);
    }

    private void setCircleFillPaint() {
        Paint paint = new Paint();
        mCircleFillPaint = paint;
        paint.setAntiAlias(true);
        mCircleFillPaint.setDither(true);
        mCircleFillPaint.setColor(mCircleFillColor);
        mCircleFillPaint.setStyle(Paint.Style.FILL);
    }

    private void setCircleProgressPaint() {
        Paint paint = new Paint();
        mCircleProgressPaint = paint;
        paint.setAntiAlias(true);
        mCircleProgressPaint.setDither(true);
        mCircleProgressPaint.setStrokeWidth(mCircleStrokeWidth);
        mCircleProgressPaint.setStyle(Paint.Style.STROKE);
        mCircleProgressPaint.setStrokeJoin(Paint.Join.ROUND);
        mCircleProgressPaint.setStrokeCap(mCircleStyle);
    }

    private void setSleepGoalWheelPaint() {
        Paint paint = new Paint();
        mSleepGoalWheelPaint = paint;
        paint.setAntiAlias(true);
        mSleepGoalWheelPaint.setDither(true);
        mSleepGoalWheelPaint.setStrokeWidth(mSleepGoalWheelStrokeWidth);
        mSleepGoalWheelPaint.setStyle(Paint.Style.STROKE);
        mSleepGoalWheelPaint.setStrokeJoin(Paint.Join.ROUND);
        mSleepGoalWheelPaint.setStrokeCap(Paint.Cap.ROUND);
        mSleepGoalWheelPaint.setColor(getResources().getColor(R.color.sesl_sleep_goal_wheel_color));
    }

    private void setFirstPointerPaint() {
        mFirstPointerPaint = new Paint();
        mFirstPointerPaint.set(mSecondPointerPaint);
        mFirstPointerPaint.setColor(mFirstPointerColor);

        mFirstPointerHaloPaint = new Paint();
        mFirstPointerHaloPaint.set(mSecondPointerHaloPaint);
        mFirstPointerHaloPaint.setColor(mFirstPointerHaloColor);
        mFirstPointerHaloPaint.setStrokeWidth(mPointerStrokeWidth);
    }

    private void setSecondPointerPaint() {
        mSecondPointerPaint = new Paint();
        mSecondPointerPaint.setAntiAlias(true);
        mSecondPointerPaint.setDither(true);
        mSecondPointerPaint.setColor(mSecondPointerColor);
        mSecondPointerPaint.setStrokeWidth(mPointerStrokeWidth);
        mSecondPointerPaint.setStyle(Paint.Style.STROKE);
        mSecondPointerPaint.setStrokeJoin(Paint.Join.ROUND);
        mSecondPointerPaint.setStrokeCap(mCircleStyle);

        mSecondPointerHaloPaint = new Paint();
        mSecondPointerHaloPaint.set(mSecondPointerPaint);
        mSecondPointerHaloPaint.setColor(mSecondPointerHaloColor);
        mSecondPointerHaloPaint.setStrokeWidth(mPointerStrokeWidth);
    }

    private void setClockGridPaint() {
        mGridPaintSmall = new Paint(1);
        mGridPaintSmall.setStrokeWidth(DPTOPX_SCALE);
        mGridPaintSmall.setColor(mCircleGridSmall);
        mGridPaintSmall.setStyle(Paint.Style.STROKE);

        mGridPaintMedium = new Paint(1);
        mGridPaintMedium.setStrokeWidth(DPTOPX_SCALE);
        mGridPaintMedium.setColor(mCircleGridMedium);
        mGridPaintMedium.setStyle(Paint.Style.STROKE);
    }

    private void setDotLinePaint() {
        Path path = new Path();
        float f = mDashLineStrokeWidth / 2.0f;
        path.addCircle(f, 0.0f, f, Path.Direction.CW);

        mCircleLineProgressPaint = new Paint();
        mCircleLineProgressPaint.setStyle(Paint.Style.STROKE);
        mCircleLineProgressPaint.setStrokeWidth(mDashLineStrokeWidth);
        mCircleLineProgressPaint.setColor(getResources().getColor(R.color.sesl_dotted_line_color));
        mCircleLineProgressPaint.setPathEffect(new PathDashPathEffect(path,
                mDashLineStrokeWidth + getResources().getDimension(R.dimen.sesl_dot_line_gap_width), 0.0f, PathDashPathEffect.Style.ROTATE));
    }

    private void initPaths() {
        mCirclePath = new Path();
        mCircleProgressPath = new Path();
        mCircleLineProgressPath = new Path();
        mCircleSecondPointerPath = new Path();
        mCircleFirstPointerPath = new Path();
        mSleepGoalWheelPath = new Path();
    }

    private void initTouchOnFirstPointer() {
        OnCircularSeekBarChangeListener listener = this.mOnCircularSeekBarChangeListener;
        if (listener != null) {
            listener.onSelectBedTimeIcon();
            setProgressBasedOnAngle(this.mFirstPointerPosition, 1);
            setPointerExpandCollapseAnimation( true, 1);
            recalculateAll();
            invalidate();
            listener.onStartTrackingTouch(this);
            mUserIsMovingSecondPointer = false;
            mUserIsMovingFirstPointer = true;
            mLastPointerTouched = 0;
            mLockAtEnd = false;
            mLockAtStart = false;
        }
    }

    private void initTouchOnMiddleHandler() {
        float touchAngle = this.mHandlerTouchPosition;
        mTouchDistanceFromFirstPointer = touchAngle - mFirstPointerPosition;
        mTouchDistanceFromSecondPointer = mSecondPointerPosition - touchAngle;

        OnCircularSeekBarChangeListener listener = this.mOnCircularSeekBarChangeListener;
        if (listener != null) {
            listener.onSelectMiddleHandler();
            recalculateAll();
            setProgressBasedOnAngle(mHandlerTouchPosition, 2);
            invalidate();
            listener.onStartTrackingTouch(this);
            mUserIsMovingMiddleHandler = true;
            mUserIsMovingSecondPointer = false;
            mUserIsMovingFirstPointer = false;
            mLockAtEnd = false;
            mLockAtStart = false;
        }
    }

    private void initTouchOnSecondPointer() {
        OnCircularSeekBarChangeListener listener = this.mOnCircularSeekBarChangeListener;
        if (listener != null) {
            listener.onSelectWakeUpTimeIcon();
            setPointerExpandCollapseAnimation(true, 0);
            setProgressBasedOnAngle(mSecondPointerPosition, 0);
            recalculateAll();
            invalidate();
            listener.onStartTrackingTouch(this);
            mUserIsMovingSecondPointer = true;
            mUserIsMovingFirstPointer = false;
            mLastPointerTouched = 1;
            mLockAtEnd = false;
            mLockAtStart = false;
        }
    }

    private boolean onActionUpCancel() {
        if (mUserIsMovingSecondPointer || mUserIsMovingFirstPointer || mUserIsMovingMiddleHandler) {
            if (mUserIsMovingSecondPointer) {
                mOnCircularSeekBarChangeListener.onUnselectWakeUpTimeIcon();
                setPointerExpandCollapseAnimation(false, 0);
            } else if (mUserIsMovingFirstPointer) {
                mOnCircularSeekBarChangeListener.onUnselectBedTimeIcon();
                setPointerExpandCollapseAnimation(false, 1);
            } else if (mUserIsMovingMiddleHandler) {
                mOnCircularSeekBarChangeListener.onUnselectMiddleHandler();
            }
            mUserIsMovingSecondPointer = false;
            mUserIsMovingFirstPointer = false;
            mUserIsMovingMiddleHandler = false;

            mOnCircularSeekBarChangeListener.onStopTrackingTouch(this);
            invalidate();
            return true;
        }
        return false;
    }

    void recalculateAll() {
        calculateTotalDegrees();
        if (mUserIsMovingSecondPointer) {
            calculatePointerPosition(0);
        } else if (mUserIsMovingFirstPointer) {
            calculatePointerPosition(1);
        } else if (mUserIsMovingMiddleHandler) {
            calculateHandlerPosition();
        }
        calculateProgressDegrees();
        resetRects();
        resetPaths();
        findBedTimeIconLocation();
        findWakeUpTimeIconLocation();
    }

    private void calculateHandlerPosition() {
        float handlerTouchPosition = mHandlerTouchPosition;
        float adjFirstPointerDegrees = handlerTouchPosition - mTouchDistanceFromFirstPointer;
        if (adjFirstPointerDegrees < 0.0f) {
            adjFirstPointerDegrees += 360.0f;
        }
        mFirstPointerPosition = adjFirstPointerDegrees % 360.0f;

        float adjSecondPointerDegrees = handlerTouchPosition + mTouchDistanceFromSecondPointer;
        if (adjSecondPointerDegrees < 0.0f) {
            adjSecondPointerDegrees += 360.0f;
        }
        mSecondPointerPosition = adjSecondPointerDegrees % 360.0f;
    }

    private void calculateTotalDegrees() {
        float degrees = (360.0f - (mStartAngle - mEndAngle)) % 360.0f;
        if (degrees <= 0.0f) {
            mTotalCircleDegrees = 360.0f;
        }else{
            mTotalCircleDegrees = degrees;
        }
    }

    private void findBedTimeIconLocation() {
        double d = (mFirstPointerPosition / 180.0f) * Math.PI;
        float left =
                ((float) (mCircleRectF.centerX() + (mRadiusOut * Math.cos(d)))) - (mIconSize / 2.0f);
        float top =
                ((float) (mCircleRectF.centerY() + (mRadiusOut * Math.sin(d)))) - (mIconSize / 2.0f);
        mBedTimeIconRectF.left = left;
        mBedTimeIconRectF.top = top;
        mBedTimeIconRectF.right = left + mIconSize;
        mBedTimeIconRectF.bottom = top + mIconSize;
    }


    private void findWakeUpTimeIconLocation() {
        double d = (mSecondPointerPosition / 180.0f) * Math.PI;
        float left =
                ((float) (mCircleRectF.centerX() + (mRadiusOut * Math.cos(d)))) - (mIconSize / 2.0f);
        float top =
                ((float) (mCircleRectF.centerY() + (mRadiusOut * Math.sin(d)))) - (mIconSize / 2.0f);
        mWakeUpTimeIconRectF.left = left;
        mWakeUpTimeIconRectF.top = top;
        mWakeUpTimeIconRectF.right = left + mIconSize;
        mWakeUpTimeIconRectF.bottom = top + mIconSize;
    }

    private void resetRects() {
        float circleWidth = mCircleWidth;
        float circleHeight = mCircleHeight;

        mCircleRectF.set(-circleWidth, -circleHeight, circleWidth, circleHeight);

        mSleepGoalWheelRectF.left = mCircleRectF.centerX() - (mRadiusIn - 5.0f);
        mSleepGoalWheelRectF.right = mCircleRectF.centerX() + (mRadiusIn - 5.0f);

        mSleepGoalWheelRectF.top = mCircleRectF.centerY() - (mRadiusIn - 5.0f);
        mSleepGoalWheelRectF.bottom = mCircleRectF.centerY() + (mRadiusIn - 5.0f);
    }

    private void resetPaths() {
        mCirclePath.reset();
        mCirclePath.addArc(mCircleRectF, mStartAngle, mTotalCircleDegrees);

        float secondPointerAngle = mSecondPointerAngle;
        float startAngle = mFirstPointerPosition - (secondPointerAngle / 2.0f);

        float sweepAngle = mProgressDegrees + secondPointerAngle;
        if (sweepAngle >= 360.0f) {
            sweepAngle = 359.9f;
        }

        if (mSleepGoalWheelEnable) {
            mSleepGoalWheelPath.reset();
            mSleepGoalWheelPath.addArc(mSleepGoalWheelRectF, mSleepGoalWheelExtendStart,
                    mSleepGoalWheelExtendDegree);
        }

        mCircleProgressPath.reset();
        mCircleProgressPath.addArc(mCircleRectF, startAngle, sweepAngle);

        mCircleLineProgressPath.reset();
        if (mProgress > 6.5f) {
            if (mUserIsMovingSecondPointer) {
                float startAngleLineProgressPath =
                        mSecondPointerPosition - (mFirstPointerAngle / 2.0f);
                mCircleLineProgressPath.addArc(mCircleRectF, startAngleLineProgressPath,
                        -sweepAngle);
            } else {
                mCircleLineProgressPath.addArc(mCircleRectF, startAngle, sweepAngle);
            }
        }

        float startAngleSecondPointerPath = mSecondPointerPosition - (mSecondPointerAngle / 2.0f);
        mCircleSecondPointerPath.reset();
        mCircleSecondPointerPath.addArc(mCircleRectF, startAngleSecondPointerPath,
                mSecondPointerAngle);

        float startAngleFirstPointerPath = mFirstPointerPosition - (mFirstPointerAngle / 2.0f);
        mCircleFirstPointerPath.reset();
        mCircleFirstPointerPath.addArc(mCircleRectF, startAngleFirstPointerPath,
                mFirstPointerAngle);
    }

    private void setPointerExpandCollapseAnimation(boolean expand, int whichPointer) {
        mIsExpandCollapseAnimation = true;
        ValueAnimator animation = expand ? ValueAnimator.ofFloat(0.0f, 1.0f) : ValueAnimator.ofFloat(1.0f, 0.0f);
        animation.setDuration(300L).setInterpolator(EXPAND_COLLAPSE_PATH_INTERPOLATOR);
        animation.addUpdateListener(valueAnimator -> {
            float floatValue = (Float) valueAnimator.getAnimatedValue();
            if (whichPointer == 1) {
                mFirstPointerHaloPaint.setStrokeWidth((mPointerHaloWidth * 2.0f * floatValue) + mPointerStrokeWidth);
            } else {
                mSecondPointerHaloPaint.setStrokeWidth((mPointerHaloWidth * 2.0f * floatValue) + mPointerStrokeWidth);
            }
            requestLayout();
            invalidate();
        });
        animation.start();
    }

    final void setProgressBasedOnAngle(float angle, int pointer) {
        if (pointer == 0) {
            this.mSecondPointerPosition = angle;
        } else if (pointer == 1) {
            this.mFirstPointerPosition = angle;
        } else if (pointer == 2) {
            this.mHandlerTouchPosition = angle;
            float firstPointerDegree = angle - this.mTouchDistanceFromFirstPointer;
            if (firstPointerDegree < 0.0f) {
                firstPointerDegree += 360.0f;
            }
            this.mFirstPointerPosition = firstPointerDegree % 360.0f;
            float secondPointerDegree = angle + this.mTouchDistanceFromSecondPointer;
            if (secondPointerDegree < 0.0f) {
                secondPointerDegree += 360.0f;
            }
            this.mSecondPointerPosition = secondPointerDegree % 360.0f;
        }
        calculateProgressDegrees();
        this.mProgress = (this.mMax * this.mProgressDegrees) / this.mTotalCircleDegrees;
    }

    @Override
    public final void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        mInnerCircleRatio = getInnerCircleRatio(getResources());
        requestLayout();
        invalidate();
    }

    @Override
    public final void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        canvas.translate(getWidth() / 2.0f, getHeight() / 2.0f);
        if (mSleepGoalWheelEnable) {
            canvas.drawPath(mSleepGoalWheelPath, mSleepGoalWheelPaint);
        }
        canvas.drawPath(mCirclePath, mCircleFillPaint);
        canvas.drawPath(mCirclePath, mCirclePaint);

        drawClockGrid(canvas);

        int[] sweepColors = mSweepGradientVariable.color;
        int mFirstPointerColor = this.mFirstPointerColor;
        sweepColors[0] = mFirstPointerColor;
        sweepColors[1] = mFirstPointerColor;
        sweepColors[2] = mMiddleGradientColor;

        sweepColors[3] = mSecondPointerColor;
        sweepColors[4] = mSecondPointerColor;

        final float[] sweepPos = mSweepGradientVariable.pos;
        sweepPos[0] = 0.0f;
        final float progressPercent = this.mProgress / this.mMax;
        sweepPos[1] = 0.1f * progressPercent;
        sweepPos[2] = 0.5f * progressPercent;
        sweepPos[3] = 0.9f * progressPercent;
        sweepPos[4] = progressPercent;

        final float centerX2 = mCircleRectF.centerX();
        final float centerY = mCircleRectF.centerY();

        final SweepGradientVariable sweepGradientVariable = mSweepGradientVariable;
        final SweepGradient shader = new SweepGradient(centerX2, centerY, sweepGradientVariable.color, sweepGradientVariable.pos);
        final Matrix matrix = new Matrix();
        matrix.setRotate(this.mFirstPointerPosition, mCircleRectF.centerX(),
                mCircleRectF.centerY());
        shader.setLocalMatrix(matrix);
        mCircleProgressPaint.setShader(shader);

        canvas.drawPath(mCircleProgressPath, mCircleProgressPaint);
        canvas.drawPath(mCircleLineProgressPath, mCircleLineProgressPaint);

        if (mLastPointerTouched == 0) {
            drawSecondPointer(canvas);
            drawFirstPointer(canvas);
        }else{
            drawFirstPointer(canvas);
            drawSecondPointer(canvas);
        }
    }

    private void drawClockGrid(@NonNull Canvas canvas) {
        double d = 0.0f;
        while (true) {
            int i = (Double.compare(d, 360.0f));
            if (i > 0) {
                break;
            }
            double d2 = ((mStartAngle + d) / 180.0f) * Math.PI;
            double centerX = mCircleRectF.centerX();
            float f = this.mRadiusIn;
            float f2 = this.DPTOPX_SCALE * 2.5f;

            float startX = (float) ((Math.cos(d2) * (f - f2)) + centerX);
            float startY =
                    (float) (mCircleRectF.centerY() + ((mRadiusIn - (DPTOPX_SCALE * 2.5f)) * Math.sin(d2)));
            float stopX = (float) ((Math.cos(d2) * (this.mRadiusIn + f2)) + mCircleRectF.centerX());
            float stopY =
                    (float) (mCircleRectF.centerY() + ((mRadiusIn + (DPTOPX_SCALE * 2.5f)) * Math.sin(d2)));
            double qrtExtra = d % 90.0f;
            if (qrtExtra != 0.0f && qrtExtra != 2.5f && qrtExtra != 3.0f && qrtExtra != 87.0f && qrtExtra != 87.5f && d != 175.0f && d != 185.0f) {
                if (d % 15.0f == 0.0f) {
                    canvas.drawLine(startX, startY, stopX, stopY, this.mGridPaintMedium);
                } else {
                    canvas.drawLine(startX, startY, stopX, stopY, this.mGridPaintSmall);
                }
            }
            d += 2.5f;
        }
    }

    @Override
    public final void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int height = View.getDefaultSize(getSuggestedMinimumHeight(), heightMeasureSpec);
        int width = View.getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        if (height == 0) {
            height = width;
        }
        if (width == 0) {
            width = height;
        }
        if (mMaintainEqualCircle) {
            int min = Math.min(width, height);
            setMeasuredDimension(min, min);
        } else {
            setMeasuredDimension(width, height);
        }

        Resources res = getResources();
        Configuration conf = res.getConfiguration();

        final float f = (mPointerStrokeWidth / 2.0f) + mPointerHaloWidth;

        final float screenWidth = conf.screenWidthDp * res.getDisplayMetrics().density;

        final float outerSize = needBedTimePickerAdjustment(conf.screenHeightDp)
                ? mOuterCircleMinSize
                : mOuterCircleSize;

        mCircleWidth = (screenWidth / 2.0f) - f;
        mCircleHeight = (outerSize / 2.0f) - f;

        if (this.mMaintainEqualCircle) {
            float shorterSide = Math.min(mCircleHeight, mCircleWidth);
            mCircleHeight = shorterSide;
            mCircleWidth = shorterSide;
        }

        mRadiusOut = mCircleHeight;
        mRadiusIn =  mCircleHeight * mInnerCircleRatio;
        recalculateAll();
    }

    @Override
    public final void onRestoreInstanceState(Parcelable parcelable) {
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

    @NonNull
    @Override
    public final Parcelable onSaveInstanceState() {
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
    public final boolean onTouchEvent(MotionEvent ev) {
        if (!isEnabled() || mCircularSeekBarRevealAnimation.mIsRevealAnimation) {
            return false;
        }

        mTouchEventVariable.x = ev.getX() - (getWidth() / 2.0f);
        mTouchEventVariable.y = ev.getY() - (getHeight() / 2.0f);

        mTouchEventVariable.distanceX = mCircleRectF.centerX() - mTouchEventVariable.x;
        mTouchEventVariable.distanceY = mCircleRectF.centerY() - mTouchEventVariable.y;
        mTouchEventVariable.touchEventRadius =
                (float) Math.sqrt(Math.pow(this.mTouchEventVariable.distanceY, 2.0d)
                        + Math.pow(mTouchEventVariable.distanceX, 2.0d));

        float minTouchTarget = this.DPTOPX_SCALE * 48.0f;
        mTouchEventVariable.additionalRadius = mCircleStrokeWidth < minTouchTarget
                ? minTouchTarget / 2.0f
                : mCircleStrokeWidth / 2.0f;

        mTouchEventVariable.outerRadius = Math.max(this.mCircleHeight, this.mCircleWidth)
                + mTouchEventVariable.additionalRadius;

        mTouchEventVariable.innerRadius =
                Math.min(this.mCircleHeight, this.mCircleWidth) - mTouchEventVariable.additionalRadius;
        mTouchEventVariable.touchAngle =
                (float) (((Math.atan2(mTouchEventVariable.y, mTouchEventVariable.x) / Math.PI) * 180.0d) % 360.0d);

        float adjTouchAngle = mTouchEventVariable.touchAngle;
        if (adjTouchAngle < 0.0f) {
            adjTouchAngle += 360.0f;
        }
        mTouchEventVariable.touchAngle = adjTouchAngle;

        switch(ev.getAction()){
            case ACTION_DOWN -> {
                return onActionDown(//0
                        mTouchEventVariable.touchAngle,
                        mTouchEventVariable.touchEventRadius,
                        mTouchEventVariable.innerRadius,
                        mTouchEventVariable.outerRadius
                );
            }
            case ACTION_MOVE ->{//2
                return onActionMove();
            }

            case ACTION_UP ->{//1
                return onActionUpCancel();
            }
            case ACTION_CANCEL -> {//3
                Log.d("CircularSeekBar", "MotionEvent.ACTION_CANCEL");
                return onActionUpCancel();
            }
            default -> {
                return true;
            }
        }
    }

    private boolean onActionMove() {
        TouchEventVariable eventVariable = mTouchEventVariable;

        final float touchAngle = eventVariable.touchAngle;
        final float totalCircleDegrees = this.mTotalCircleDegrees;

        final float degreeThreshold = totalCircleDegrees / 3.0f;

        final float secondPointerPosition = mSecondPointerPosition;
        final float firstPointerPosition2 = mFirstPointerPosition;
        float firstToSecondDistanec = secondPointerPosition - firstPointerPosition2;
        if (firstToSecondDistanec < 0.0f) {
            firstToSecondDistanec += 360.0f;
        }

        final boolean isNearStart = firstToSecondDistanec < degreeThreshold;
        final boolean isNearEnd = firstToSecondDistanec > totalCircleDegrees - degreeThreshold;

        if (mUserIsMovingSecondPointer) {
            float cwDistance = touchAngle - ((firstPointerPosition2 + 2.5f) % 360.0f);
            if (cwDistance < 0.0f) {
                cwDistance += 360.0f;
            }
            final float cwDistanceComplement = 360.0f - cwDistance;
            float cwDistanceFromEnd =
                    touchAngle - (((firstPointerPosition2 - 2.5f) + 360.0f) % 360.0f);
            if (cwDistanceFromEnd < 0.0f) {
                cwDistanceFromEnd += 360.0f;
            }
            eventVariable.touchOverStart = cwDistanceComplement < degreeThreshold;
            eventVariable.touchOverEnd = cwDistanceFromEnd < degreeThreshold;
            mLockEnabled = true;

        } else if (this.mUserIsMovingFirstPointer) {
            float cwDistanceFromStart =
                    touchAngle - (((secondPointerPosition - 2.5f) + 360.0f) % 360.0f);
            if (cwDistanceFromStart < 0.0f) {
                cwDistanceFromStart += 360.0f;
            }
            float cwDistanceFromEnd2 =
                    touchAngle - ((secondPointerPosition + 2.5f) % 360.0f);
            if (cwDistanceFromEnd2 < 0.0f) {
                cwDistanceFromEnd2 += 360.0f;
            }
            final float cwDistanceFromEndComplement = 360.0f - cwDistanceFromEnd2;
            eventVariable.touchOverStart = cwDistanceFromStart < degreeThreshold;
            eventVariable.touchOverEnd =
                    cwDistanceFromEndComplement < degreeThreshold;
            mLockEnabled = true;

        } else if (!mUserIsMovingMiddleHandler) {
            return false;
        } else {
            mLockAtEnd = false;
            mLockAtStart = false;
            mLockEnabled = false;
        }

        if (isNearEnd) {
            mLockAtEnd = eventVariable.touchOverEnd;
        } else if (isNearStart) {
            mLockAtStart = eventVariable.touchOverStart;
        }
        if (mLockAtStart && this.mLockEnabled) {
            if (mProgress != 0.6944445f) {
                mProgress = 0.6944445f;
                recalculateAll();
                invalidate();
                dispatchCallback();
                performHapticFeedback(50073);
            }
        } else if (this.mLockAtEnd && this.mLockEnabled) {
            float progress = mProgress;
            float progressLockAtEnd = mMax - 0.6944445f;
            if (progress != progressLockAtEnd) {
                mProgress = progressLockAtEnd;
                recalculateAll();
                invalidate();
                dispatchCallback();
                performHapticFeedback(50073);
            }
        } else if (mMoveOutsideCircle ||  eventVariable.touchEventRadius <= eventVariable.outerRadius) {
            boolean userIsMovingFirstPointer = this.mUserIsMovingFirstPointer;
            if (mUserIsMovingMiddleHandler) {
                setProgressBasedOnAngle(touchAngle, 2);
            } else {
                setProgressBasedOnAngle(touchAngle, userIsMovingFirstPointer ? 1 : 0);
            }
            recalculateAll();
            invalidate();
            dispatchCallback();
        }
        if (getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return true;
    }

    private boolean onActionDown(
            float touchAngle,
            float touchEventRadius,
            float innerRadius,
            float outerRadius) {

        final float circleMaxDimension = Math.max(mCircleHeight, mCircleWidth);
        final float circumferencePart =
                (float) ((mPointerStrokeWidth * 180.0f) / (circleMaxDimension * Math.PI));
        final float halfSecondPointerAngle = mSecondPointerAngle / 2.0f;
        final float max = Math.max(circumferencePart, halfSecondPointerAngle);

        float angleDifferenceSecondPointer = touchAngle - this.mSecondPointerPosition;
        if (angleDifferenceSecondPointer < 0.0f) {
            angleDifferenceSecondPointer += 360.0f;
        }
        final float complementAngleSecondPointer = 360.0f - angleDifferenceSecondPointer;

        final float firstPointerPosition = this.mFirstPointerPosition;
        float angleDifferenceFirstPointer = touchAngle - firstPointerPosition;
        if (angleDifferenceFirstPointer < 0.0f) {
            angleDifferenceFirstPointer += 360.0f;
        }

        final float complementAngleFirstPointer = 360.0f - angleDifferenceFirstPointer;
        final boolean isWithinRadius = touchEventRadius >= innerRadius && touchEventRadius <= outerRadius;
        final boolean isWithinSecondPointerRange =
                angleDifferenceSecondPointer <= max || complementAngleSecondPointer <= max;
        final boolean isWithinFirstPointerRange =
                angleDifferenceFirstPointer <= max || complementAngleFirstPointer <= max;

        if (isWithinRadius && isWithinSecondPointerRange && isWithinFirstPointerRange) {
            if (mLastPointerTouched == 0) {
                initTouchOnFirstPointer();
            } else {
                initTouchOnSecondPointer();
            }
        } else if (isWithinRadius && isWithinSecondPointerRange) {
            initTouchOnSecondPointer();
        } else if (isWithinRadius && isWithinFirstPointerRange) {
            initTouchOnFirstPointer();
        } else if (isWithinRadius && isTimeInRange(touchAngle, firstPointerPosition)) {
            mHandlerTouchPosition = touchAngle;
            initTouchOnMiddleHandler();
        } else {
            mUserIsMovingSecondPointer = false;
            mUserIsMovingFirstPointer = false;
            mUserIsMovingMiddleHandler = false;
        }
        return true;
    }

    private boolean isTimeInRange(float touchAngle, float firstPointerPosition) {
        final float firstPointerTime = SeslSleepTimePickerUtil.convertToTime(firstPointerPosition);
        final float secondPointerTime =
                SeslSleepTimePickerUtil.convertToTime(this.mSecondPointerPosition);
        final float touchAngleTime = SeslSleepTimePickerUtil.convertToTime(touchAngle);
        return firstPointerTime >= secondPointerTime
                ? !(firstPointerTime <= secondPointerTime
                        || ((touchAngleTime <= firstPointerTime || touchAngleTime > 1440.0f)
                        && (touchAngleTime >= secondPointerTime || touchAngleTime <= 0.0f)))
                : !(touchAngleTime <= firstPointerTime || touchAngleTime >= secondPointerTime);
    }

    /**
     * Sets the wake-up time position based on the given angle.
     *
     * @param angleDegrees The angle in degrees (0-360).
     */
    void setWakeUpTimePosition(float angleDegrees) {
        final float normalizedAngle = Math.floorMod((int) angleDegrees, 360);
        setProgressBasedOnAngle(normalizedAngle, 0);
        recalculateAll();
        invalidate();
    }

    /**
     * Sets the bedtime position based on the given angle.
     *
     * @param angleDegrees The angle in degrees (0-360).
     */
    void setBedTimePosition(float angleDegrees) {
        final float normalizedAngle = Math.floorMod((int) angleDegrees, 360);
        setProgressBasedOnAngle(normalizedAngle, 1);
        recalculateAll();
        invalidate();
    }

    void startRevealAnimation() {
        calculateProgressDegrees();
        mCircularSeekBarRevealAnimation.setmSweepProgress(this.mProgressDegrees);
        mCircularSeekBarRevealAnimation.startAnimators();
    }

    void setRevealAnimationValue(float animationProgress) {
        setProgressBasedOnAngle(calculateRevealAngle(animationProgress), 0);
        recalculateAll();
    }

    private float calculateRevealAngle(float animationProgress) {
        float sweepProgress = (mCircularSeekBarRevealAnimation.getmSweepProgress() + 360.0f) % 360.0f;
        return (this.mFirstPointerPosition + (sweepProgress * animationProgress)) % 360.0f;
    }


    public void setSleepGoalWheel(float startAngle, float endAngle) {
        this.mSleepGoalWheelEnable = true;

        // Calculate half of the second pointer angle for offsetting the start.
        final float halfSecondPointerAngle = this.mSecondPointerAngle/ 2.0f;

        // Calculate the starting angle of the sleep goal wheel.
        final float sleepGoalWheelExtendStart = startAngle - halfSecondPointerAngle;

        // Calculate the sweep angle (endAngle - startAngle).
        float sweepAngle = endAngle - startAngle;

        // If sweepAngle is negative, it means the wheel extends past 360 degrees.
        // Add 360 to make it positive for correct rendering.
        if (sweepAngle < 0.0f) {
            sweepAngle += 360.0f;
        }
        this.mSleepGoalWheelExtendStart = sleepGoalWheelExtendStart;

        // The total extend degree includes the sweep angle and the second pointer angle.
        this.mSleepGoalWheelExtendDegree = sweepAngle + mSecondPointerAngle;

        resetRects();
        resetPaths();
        invalidate();
    }

    public void setSleepGoalWheelEnabled(boolean enable) {
        mSleepGoalWheelEnable = enable;
    }

    public boolean getSleepGoalWheelEnable() {
        return  mSleepGoalWheelEnable;
    }

}
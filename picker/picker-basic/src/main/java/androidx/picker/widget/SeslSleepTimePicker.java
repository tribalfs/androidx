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

import static android.util.TypedValue.COMPLEX_UNIT_PX;

import static androidx.picker.util.SeslSleepTimePickerUtil.getFontFromOpenTheme;
import static androidx.picker.util.SeslSleepTimePickerUtil.getTimeSeparatorText;
import static androidx.picker.util.SeslSleepTimePickerUtil.needBedTimePickerAdjustment;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Parcelable;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StyleRes;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.picker.R;
import androidx.picker.util.SeslSleepTimePickerUtil;

import java.text.DateFormatSymbols;
import java.util.Locale;


public class SeslSleepTimePicker extends LinearLayout {

    private static final String TAG = "SleepTimePicker";

    private static final int ANIMATION_DURATION = 400;
    private static final int BED_WAKEUP_FADE_IN_DURATION = 200;
    private static final int BED_WAKEUP_FADE_OUT_DURATION = 100;
    private static final int BED_WAKEUP_SRC_FADE_OUT_DURATION = 66;
    private static final float DEFAULT_BED_TIME_MINUTE = 1320.0f;
    private static final float DEFAULT_WAKEUP_TIME_MINUTE = 420.0f;
    private static final int FONT_WEIGHT_LIGHT = 300;
    private static final int MINIMUM_DIMEN_MULTIWINDOW = 290;
    private static final float SIZE_RATIO = 0.75f;
    private static final int SLEEP_PICKER_VIBRATION = 41;
    private static final float TOTAL_DEGREE = 360.0f;
    private static final float TOTAL_MINUTES = 1440.0f;


    static final class SleepDurationFormatterImpl implements SleepDurationFormatter {
        @Override
        public int format(float bedTime, float wakeupTime) {
            return (int) (((wakeupTime - bedTime) + TOTAL_MINUTES) % TOTAL_MINUTES);
        }
    }

    final TextView mWakeUpTimeTextRightAmPm;
    final TextView mWakeUpTimeCenterTextRightAmPm;

    private final RelativeLayout editOuterCircleContainer;
    private final FrameLayout editInnerCircleContainer;

    private final FrameLayout sleepTimePickerContainer;

    private final PathInterpolator CENTER_LAYOUT_PATH_INTERPOLATOR = new PathInterpolator(0.22f, 0.25f, 0.0f, 1.0f);

    private final ImageView mWakeUpBottomIcon;
    private final ImageView mWakeUpBottomIconRight;

    private SleepDurationFormatter durationFormatter = new SleepDurationFormatterImpl();

    private final ImageView mBedTimeTopIcon;
    private final ImageView mBedTimeTopIconRight;
    private final ImageView mWakeUpCenterIcon;
    private final ImageView mBedTimeCenterIcon;
    private final ImageView mWakeUpCenterIconRight;
    private final ImageView mBedTimeCenterIconRight;

    OnSleepTimeChangedListener mOnSleepTimeChangedListener;
    final SeslCircularSeekBarView mCircularSeekBar;

    private final Context mContext;

    float mBedTimeInMinute;
    float mWakeupTimeInMinute;
    LinearLayout mBedTimeView;
    LinearLayout mWakeUpTimeView;
    LinearLayout mBedTimeTargetLayout;
    final TextView mBedTimeTargetText;

    final LinearLayout mWakeUpTimeTargetLayout;

    final TextView mWakeUpTimeTargetText;
    Animator mCurrentAnimator;
    private final TextView mSleepDuration;
    final TextView mBedTimeText;
    final TextView mWakeUpTimeText;
    final TextView mBedTimeTextLeftAmPm;
    final TextView mBedTimeCenterTextLeftAmPm;
    final TextView mBedTimeTextRightAmPm;
    final TextView mBedTimeCenterTextRightAmPm;
    final TextView mWakeUpTimeTextLeftAmPm;
    final TextView mWakeUpTimeCenterTextLeftAmPm;

    private final int mOuterCircleSize;
    private final int mOuterCircleMinSize;
    private float mInnerCircleRatio;


    public interface OnSleepTimeChangedListener {
        void onSleepTimeChanged(float bedTimeInMinutes, float wakeupTimeInMinutes);

        void onStartSleepTimeChanged(float bedTimeInMinutes, float wakeupTimeInMinutes);

        void onStopSleepTimeChanged(float bedTimeInMinutes, float wakeupTimeInMinutes);

    }


    public interface SleepDurationFormatter {
        int format(float bedTime, float wakeupTime);
    }

    public SeslSleepTimePicker(@NonNull Context context, @Nullable AttributeSet attributeSet) {
        super(context, attributeSet);

        this.mContext = getContext();
        LayoutInflater.from(context).inflate(R.layout.sesl_sleep_time_picker, this);

        mCircularSeekBar = findViewById(R.id.circular_seekbar);
        mBedTimeText = findViewById(R.id.sleep_top_center_duration_bedtime);
        mBedTimeTextLeftAmPm = findViewById(R.id.bedtime_am_pm_left);
        mBedTimeTextRightAmPm = findViewById(R.id.bedtime_am_pm_right);
        mBedTimeCenterTextLeftAmPm = findViewById(R.id.bedtime_center_am_pm_left);
        mBedTimeCenterTextRightAmPm = findViewById(R.id.bedtime_center_am_pm_right);

        mWakeUpTimeText =
                findViewById(R.id.sleep_bottom_center_duration_wakeuptime);
        mWakeUpTimeTextLeftAmPm = findViewById(R.id.wakeuptime_am_pm_left);
        mWakeUpTimeTextRightAmPm = findViewById(R.id.wakeuptime_am_pm_right);
        mWakeUpTimeCenterTextLeftAmPm = findViewById(R.id.wakeuptime_center_am_pm_left);
        mWakeUpTimeCenterTextRightAmPm = findViewById(R.id.wakeuptime_center_am_pm_right);

        mBedTimeTargetLayout = findViewById(R.id.sleep_record_center_bedtime);
        mBedTimeTargetText = findViewById(R.id.sleep_center_duration_bedtime);

        mWakeUpTimeTargetLayout = findViewById(R.id.sleep_record_center_wakeuptime);
        mWakeUpTimeTargetText = findViewById(R.id.sleep_center_duration_wakeuptime);

        mSleepDuration = findViewById(R.id.sleep_duration_text_id);
        SeslSleepTimePickerUtil.setLargeTextSize(mContext, new TextView[]{mSleepDuration}, 1.3f);

        mWakeUpBottomIcon =
                findViewById(R.id.sleep_bottom_center_duration_wakeupimage);
        mWakeUpBottomIconRight =
                findViewById(R.id.sleep_bottom_center_duration_wakeupimage_right);
        mBedTimeTopIcon = findViewById(R.id.sleep_top_center_duration_bedimage);
        mBedTimeTopIconRight =
                findViewById(R.id.sleep_top_center_duration_bedimage_right);

        mWakeUpCenterIcon = findViewById(R.id.sleep_center_icon_wakeuptime);
        mBedTimeCenterIcon = findViewById(R.id.sleep_center_icon_bedtime);
        mWakeUpCenterIconRight = findViewById(R.id.sleep_center_icon_wakeuptime_right);
        mBedTimeCenterIconRight = findViewById(R.id.sleep_center_icon_bedtime_right);

        mWakeUpBottomIcon.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        mWakeUpBottomIconRight.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        mBedTimeTopIcon.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        mBedTimeTopIconRight.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        mWakeUpCenterIcon.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        mBedTimeCenterIcon.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        mWakeUpCenterIconRight.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        mBedTimeCenterIconRight.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        mWakeUpTimeTargetLayout.setAlpha(0.0f);
        mBedTimeTargetLayout.setAlpha(0.0f);

        mBedTimeView = findViewById(R.id.sleep_record_top_bed_time_layout);
        mWakeUpTimeView = findViewById(R.id.sleep_record_bottom_wakeup_time_layout);

        editOuterCircleContainer = findViewById(R.id.sleep_visual_edit_outer_circle_container);
        editInnerCircleContainer = findViewById(R.id.sleep_visual_edit_inner_circle_container);
        sleepTimePickerContainer =  findViewById(R.id.sleepTimePicker);

        Resources res = context.getResources();
        mOuterCircleSize = (int) res.getDimension(R.dimen.sesl_sleep_visual_edit_outer_circle_size);
        mOuterCircleMinSize =
                (int) res.getDimension(R.dimen.sesl_sleep_visual_edit_outer_circle_min_size);
        mInnerCircleRatio = getInnerCircleRatio(res);

        initListeners();

        setBedTimeInMinute(DEFAULT_BED_TIME_MINUTE);
        setWakeUpTimeInMinute(DEFAULT_WAKEUP_TIME_MINUTE);
    }

    private void initListeners(){
        final TextView[] textViewArrBedTimeCenter = {mBedTimeTargetText, mBedTimeCenterTextLeftAmPm, this.mBedTimeCenterTextRightAmPm};
        final TextView[] textViewArrWakeUpTimeCenter = {this.mWakeUpTimeTargetText, this.mWakeUpTimeCenterTextLeftAmPm, this.mWakeUpTimeCenterTextRightAmPm};
        final TextView[] textViewArrBedTime = {this.mBedTimeText, this.mBedTimeTextLeftAmPm, this.mBedTimeTextRightAmPm};
        final TextView[] textViewArrWakeUpTime = {this.mWakeUpTimeText, this.mWakeUpTimeTextLeftAmPm, this.mWakeUpTimeTextRightAmPm};

        SeslCircularSeekBarView.OnCircularSeekBarChangeListener listener =  new SeslCircularSeekBarView.OnCircularSeekBarChangeListener() {

            @Override
            public void onProgressChangedBedTime(@NonNull SeslCircularSeekBarView seslCircularSeekBarView,
                    float bedTimePosition) {
                Log.d(TAG, "onProgressChangedBedTime : BedTimePosition " + bedTimePosition);
                mBedTimeInMinute = SeslSleepTimePickerUtil.convertToTime(bedTimePosition);
                if (updateBedTimeText()) {
                    SeslSleepTimePickerUtil.performHapticFeedback(seslCircularSeekBarView, SLEEP_PICKER_VIBRATION);
                }
                if (mOnSleepTimeChangedListener != null) {
                    mOnSleepTimeChangedListener.onSleepTimeChanged(getBedTimeInMinute(), getWakeUpTimeInMinute());
                }
            }

            @Override
            public void onProgressChangedWakeupTime(@NonNull SeslCircularSeekBarView seslCircularSeekBarView,
                    float wakeupPosition) {
                Log.d(TAG, "onProgressChangedWakeupTime : WakeUpTimePosition " + wakeupPosition);
                mWakeupTimeInMinute = SeslSleepTimePickerUtil.convertToTime(wakeupPosition);
                if (updateWakeUpTimeText()) {
                    SeslSleepTimePickerUtil.performHapticFeedback(seslCircularSeekBarView, SLEEP_PICKER_VIBRATION);
                }
                if (mOnSleepTimeChangedListener != null) {
                    mOnSleepTimeChangedListener.onSleepTimeChanged(getBedTimeInMinute(), getWakeUpTimeInMinute());
                }
            }

            @Override
            public void onSelectBedTimeIcon() {
                Log.d(TAG, "onSelectedBedTimeIcon");
                int color = ContextCompat.getColor(getContext(), R.color.sesl_bed_wakeup_time_color);
                int primaryColor = getPrimaryColor();
                animateText(textViewArrBedTime, color, primaryColor, BED_WAKEUP_FADE_OUT_DURATION);
                animateCenter(mBedTimeView, mBedTimeTargetLayout, mWakeUpTimeView, 100.0f);
                animateText(textViewArrBedTimeCenter, color, primaryColor, 50L);
            }

            @Override
            public void onSelectMiddleHandler() {
                Log.d(TAG, "onSelectedMiddleHandler");
                int color = ContextCompat.getColor(getContext(), R.color.sesl_bed_wakeup_time_color);
                int primaryColor = getPrimaryColor();
                animateText(textViewArrBedTime, color, primaryColor, BED_WAKEUP_FADE_OUT_DURATION);
                animateText(textViewArrBedTimeCenter, color, primaryColor, BED_WAKEUP_FADE_OUT_DURATION);
                animateText(textViewArrWakeUpTime, color, primaryColor, BED_WAKEUP_FADE_OUT_DURATION);
                animateText(textViewArrWakeUpTimeCenter, color, primaryColor, BED_WAKEUP_FADE_OUT_DURATION);
            }

            @Override
            public void onSelectWakeUpTimeIcon() {
                Log.d(TAG, "onSelectWakeupTimeIcon");
                int color = ContextCompat.getColor(getContext(), R.color.sesl_bed_wakeup_time_color);
                int primaryColor = SeslSleepTimePicker.this.getPrimaryColor();
                animateText(textViewArrWakeUpTime, color, primaryColor, 100L);
                animateCenter(mWakeUpTimeView, mWakeUpTimeTargetLayout, mBedTimeView, -50.0f);
                animateText(textViewArrWakeUpTimeCenter, color, primaryColor, 50L);
            }

            @Override
            public void onStartTrackingTouch(@NonNull SeslCircularSeekBarView seslCircularSeekBarView) {
                Log.d(TAG, "onStartTrackingTouch");
                if (mOnSleepTimeChangedListener != null) {
                    mOnSleepTimeChangedListener.onStartSleepTimeChanged(getBedTimeInMinute(), getWakeUpTimeInMinute());
                }
            }

            @Override
            public void onStopTrackingTouch(@NonNull SeslCircularSeekBarView seslCircularSeekBarView) {
                Log.d(TAG, "onStopTrackingTouch");
                setSleepTimeDurationText();
                if (mOnSleepTimeChangedListener != null) {
                    mOnSleepTimeChangedListener.onStopSleepTimeChanged(getBedTimeInMinute(), getWakeUpTimeInMinute());
                }
            }

            @Override
            public void onUnselectBedTimeIcon() {
                Log.d(TAG, "onUnselectBedTimeIcon");
                int color = ContextCompat.getColor(getContext(),
                        R.color.sesl_bed_wakeup_time_color);
                int primaryColor = getPrimaryColor();
                animateText(textViewArrBedTimeCenter, primaryColor, color, 50L);
                reverseAnimateCenter(mBedTimeTargetLayout, mBedTimeView, mWakeUpTimeView, 50.0f);
                animateText(textViewArrBedTime, primaryColor, color, BED_WAKEUP_FADE_IN_DURATION);
            }

            @Override
            public void onUnselectMiddleHandler() {
                Log.d(TAG, "onUnselectMiddleHandler");
                int color = ContextCompat.getColor(getContext(),
                        R.color.sesl_bed_wakeup_time_color);
                int primaryColor = getPrimaryColor();
                animateText(textViewArrBedTime, primaryColor, color, BED_WAKEUP_FADE_IN_DURATION);
                animateText(textViewArrBedTimeCenter, primaryColor, color, BED_WAKEUP_FADE_IN_DURATION);
                animateText(textViewArrWakeUpTime, primaryColor, color, BED_WAKEUP_FADE_IN_DURATION);
                animateText(textViewArrWakeUpTimeCenter, primaryColor, color, BED_WAKEUP_FADE_IN_DURATION);
            }

            @Override
            public void onUnselectWakeUpTimeIcon() {
                Log.d(TAG, "onUnselectWakeUpTimeIcon");
                int primaryColor = getPrimaryColor();
                int color = ContextCompat.getColor(getContext(), R.color.sesl_bed_wakeup_time_color);
                animateText(textViewArrWakeUpTimeCenter, primaryColor, color, 50L);
                reverseAnimateCenter(mWakeUpTimeTargetLayout, mWakeUpTimeView, mBedTimeView, -50.0f);
                animateText(textViewArrWakeUpTime, primaryColor, color, BED_WAKEUP_FADE_IN_DURATION);
            }
        };

        mCircularSeekBar.setOnSeekBarChangeListener(listener);
    }

    private static float getInnerCircleRatio(Resources res){
        TypedValue typedValue = new TypedValue();
        res.getValue(R.dimen.sesl_time_picker_inner_circle_container_ratio, typedValue, true);
        return typedValue.getFloat();
    }

    void animateCenter(
            @NonNull LinearLayout layoutToCenter,
            @NonNull LinearLayout centerLayout,
            @NonNull LinearLayout layoutToHide,
            float f
    ) {
        cancelCurrentAnimator();
        Rect layoutToCenterRec = getRect(layoutToCenter);
        Rect centerLayoutRec = getRect(centerLayout);
        centerLayout.setAlpha(1.0f);

        ObjectAnimator hideAnimation = ObjectAnimator.ofFloat(layoutToHide, View.ALPHA, 1.0f,
                0.0f).setDuration(BED_WAKEUP_FADE_OUT_DURATION);
        ObjectAnimator fasterHideAnimation = ObjectAnimator.ofFloat(layoutToCenter, View.ALPHA,
                1.0f, 0.0f).setDuration(BED_WAKEUP_SRC_FADE_OUT_DURATION);
        ObjectAnimator centerAnimation = ObjectAnimator.ofFloat(
                centerLayout,
                View.TRANSLATION_Y,
                layoutToCenterRec.top - centerLayoutRec.top, 0.0f
        ).setDuration(ANIMATION_DURATION);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(centerAnimation).with(fasterHideAnimation);
        animatorSet.play(hideAnimation);
        animatorSet.setInterpolator(CENTER_LAYOUT_PATH_INTERPOLATOR);

        animatorSet.addListener(
                new AnimatorListenerAdapter(){
                    @Override
                    public void onAnimationCancel(@NonNull Animator animator) {
                        super.onAnimationCancel(animator);
                        SeslSleepTimePicker.this.mCurrentAnimator = null;
                    }

                    @Override
                    public void onAnimationEnd(@NonNull Animator animator) {
                        super.onAnimationEnd(animator);
                        centerLayout.setTranslationY(0.0f);
                        centerLayout.setAlpha(1.0f);
                        layoutToHide.setTranslationY(0.0f);
                        layoutToHide.setAlpha(0.0f);
                        layoutToCenter.setAlpha(0.0f);
                    }
                });
        animatorSet.start();
        mCurrentAnimator = animatorSet;
    }

    void animateText(
            @NonNull TextView[] textViewArr,
            int fromValue,
            int toValue,
            long duration) {

        for (TextView textView : textViewArr) {
            ValueAnimator animator = ValueAnimator.ofObject(new ArgbEvaluator(), fromValue,
                    toValue);
            animator.setDuration(duration).setInterpolator(new LinearInterpolator());
            animator.addUpdateListener(animation ->
                    textView.setTextColor((Integer) animation.getAnimatedValue())
            );
            animator.start();
        }
    }

    private void cancelCurrentAnimator() {
        Animator animator = this.mCurrentAnimator;
        if (animator != null) {
            animator.cancel();
        }
    }

    void reverseAnimateCenter(
            @NonNull LinearLayout resumeToCenter,
            @NonNull LinearLayout layoutFromCenter,
            @NonNull LinearLayout layoutToShow,
            float f
    ) {
        cancelCurrentAnimator();
        ObjectAnimator unhideAnimation = ObjectAnimator.ofFloat(layoutToShow, View.ALPHA, 0.0f,
                1.0f).setDuration(BED_WAKEUP_FADE_IN_DURATION);

        Rect resumeToCenterLayoutRec = getRect(resumeToCenter);
        Rect layoutFromCenterRec = getRect(layoutFromCenter);
        ObjectAnimator recenterAnimation = ObjectAnimator.ofFloat(
                resumeToCenter,
                View.TRANSLATION_Y,
                0.0f, -(resumeToCenterLayoutRec.top - layoutFromCenterRec.top)
        ).setDuration(ANIMATION_DURATION);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(recenterAnimation);
        animatorSet.play(unhideAnimation);
        animatorSet.setInterpolator(CENTER_LAYOUT_PATH_INTERPOLATOR);

        animatorSet.addListener(
                new AnimatorListenerAdapter(){
                    @Override
                    public void onAnimationCancel(@NonNull Animator animator) {
                        super.onAnimationCancel(animator);
                        SeslSleepTimePicker.this.mCurrentAnimator = null;
                    }

                    @Override
                    public void onAnimationEnd(@NonNull Animator animator) {
                        super.onAnimationEnd(animator);
                        layoutToShow.setTranslationY(0.0f);
                        layoutToShow.setAlpha(1.0f);
                        layoutFromCenter.setTranslationY(0.0f);
                        layoutFromCenter.setAlpha(1.0f);
                        resumeToCenter.setAlpha(0.0f);
                        resumeToCenter.setTranslationY(0.0f);

                    }
                });
        animatorSet.start();
        mCurrentAnimator = animatorSet;
    }

    static Rect getRect(LinearLayout linearLayout) {
        int[] iArr = new int[2];
        linearLayout.getLocationOnScreen(iArr);
        int l = iArr[0];
        int t = iArr[1];
        return new Rect(l, t, l + linearLayout.getWidth() , t + linearLayout.getHeight());
    }

    int getPrimaryColor() {
        TypedValue typedValue = new TypedValue();
        getContext().getTheme().resolveAttribute(androidx.appcompat.R.attr.colorPrimary,
                typedValue, true);
        return typedValue.data;
    }

    private static void setTimeIconSize(Resources resources, ViewGroup.LayoutParams lp, float f) {
        lp.height =
                (int) (resources.getDimensionPixelSize(R.dimen.sesl_sleep_record_bed_image_icon_size) * f);
        lp.width =
                (int) (resources.getDimensionPixelOffset(R.dimen.sesl_sleep_record_bed_image_icon_size) * f);
    }

    private void setSleepOuterCircleContainerSize(float screenSize) {
        int dimension = needBedTimePickerAdjustment(screenSize) ? mOuterCircleMinSize :  mOuterCircleSize;
        LayoutParams lp = (LayoutParams) this.editOuterCircleContainer.getLayoutParams();
        lp.height = dimension;
        lp.width = dimension;
    }

    private void setSleepTimePickerFrameSize(float screenSize) {
        int dimension = needBedTimePickerAdjustment(screenSize) ? mOuterCircleMinSize :  mOuterCircleSize;
        ViewGroup.LayoutParams layoutParams = this.sleepTimePickerContainer.getLayoutParams();
        layoutParams.height = dimension;
        layoutParams.width = dimension;
    }

    private void initSleepTimePickerData() {
        float f = getResources().getConfiguration().screenHeightDp;
        setSleepOuterCircleContainerSize(f);
        setInnerCircleContainerSize();
        setTimeTextSize();
        setTimeTypeFace();
        setSleepTimePickerFrameSize(f);
        updateWakeUpTimeText();
        updateWakeUpTimePointer();
        updateBedTimeText();
        updateBedTimePointer();
        setSleepTimeDurationText();
        setBedTimeIconVisibility();
        updateWakeUpBedTimeIcon();
        mCircularSeekBar.startRevealAnimation();
    }

    private void updateWakeUpTimePointer() {
        mCircularSeekBar.setWakeUpTimePosition(convertToAngle(mWakeupTimeInMinute));
    }

    private float convertToAngle(float f) {
        return ((((f - TOTAL_DEGREE) + TOTAL_MINUTES) % TOTAL_MINUTES) * TOTAL_DEGREE) / TOTAL_MINUTES;
    }

    private void setTimeTextSize() {
        Resources resources = this.mContext.getResources();
        if (resources.getConfiguration().screenWidthDp < MINIMUM_DIMEN_MULTIWINDOW) {
            setTimeTextSizeRatio(resources, SIZE_RATIO);
        } else {
            setTimeTextSizeRatio(resources, 1.0f);
        }
    }

    private void updateBedTimePointer() {
        mCircularSeekBar.setBedTimePosition(convertToAngle(mBedTimeInMinute));
    }

    private void updateWakeUpBedTimeIcon() {
        setBedTimeIconVisibility();
    }

    private void setBedTimeIconVisibility(){
        if (SeslSleepTimePickerUtil.isLeftAmPm() && TextUtils.getLayoutDirectionFromLocale(
                Locale.getDefault()) == LAYOUT_DIRECTION_RTL) {
            mBedTimeCenterIconRight.setVisibility(View.VISIBLE);
            mBedTimeTopIconRight.setVisibility(View.VISIBLE);
            mBedTimeTopIcon.setVisibility(View.GONE);
            mBedTimeCenterIcon.setVisibility(View.GONE);
            mWakeUpCenterIconRight.setVisibility(View.VISIBLE);
            mWakeUpBottomIconRight.setVisibility(View.VISIBLE);
            mWakeUpBottomIcon.setVisibility(View.GONE);
            mWakeUpCenterIcon.setVisibility(View.GONE);
        } else {
            mBedTimeCenterIconRight.setVisibility(View.GONE);
            mBedTimeTopIconRight.setVisibility(View.GONE);
            mBedTimeTopIcon.setVisibility(View.VISIBLE);
            mBedTimeCenterIcon.setVisibility(View.VISIBLE);
            mWakeUpCenterIconRight.setVisibility(View.GONE);
            mWakeUpBottomIconRight.setVisibility(View.GONE);
            mWakeUpBottomIcon.setVisibility(View.VISIBLE);
            mWakeUpCenterIcon.setVisibility(View.VISIBLE);
        }
    }

    private void setBedTimeTextVisibility(
            int centerRightViewVisibility,
            int rightViewVisibility,
            int centerLeftViewVisibility,
            int leftViewVisibility
    ) {
        mBedTimeCenterTextRightAmPm.setVisibility(centerRightViewVisibility);
        mBedTimeTextRightAmPm.setVisibility(rightViewVisibility);
        mBedTimeCenterTextLeftAmPm.setVisibility(centerLeftViewVisibility);
        mBedTimeTextLeftAmPm.setVisibility(leftViewVisibility);
    }

    private void setInnerCircleContainerSize() {
        Resources res = mContext.getResources();

        // Calculate the pointer dimension
        float pointerRadius = res.getDimension(R.dimen.sesl_sleep_time_pointer_size) / 2.0f;
        float touchWidth = res.getDimension(R.dimen.sesl_sleep_time_icon_touch_width);
        float totalPointerDimension = pointerRadius + touchWidth;

        // Get screen sizes
        Configuration conf = res.getConfiguration();
        float screenWidthPixels = conf.screenWidthDp * res.getDisplayMetrics().density;
        float screenHeightDp = conf.screenHeightDp;

        // Determine the outer circle size
        float outerCircleSize ;
        if (needBedTimePickerAdjustment(screenHeightDp)) {
            outerCircleSize =
                    res.getDimension(R.dimen.sesl_sleep_visual_edit_outer_circle_min_size);
        }else{
            outerCircleSize = res.getDimension(R.dimen.sesl_sleep_visual_edit_outer_circle_size);
        }

        // Calculate the minimum size for the inner circle
        float minCircleSize = Math.min(outerCircleSize - totalPointerDimension,
                screenWidthPixels - totalPointerDimension);


        // Set the dimensions for the inner circle container
        RelativeLayout.LayoutParams innerCircleLayoutParams =
                (RelativeLayout.LayoutParams) editInnerCircleContainer.getLayoutParams();
        int innerCircleSize = (int) (mInnerCircleRatio * minCircleSize);
        innerCircleLayoutParams.height = innerCircleSize;
        innerCircleLayoutParams.width = innerCircleSize;
    }

    void setSleepTimeDurationText() {
        final int totalMinutes = durationFormatter.format(mBedTimeInMinute, mWakeupTimeInMinute);
        mSleepDuration.setText(makeSleepDurationText(totalMinutes));
    }

    private String makeSleepDurationText(int totalMinutes) {
        String sleepDurationSummary;

        Resources res = mContext.getResources();
        if (totalMinutes > 60) {
            int minutes = totalMinutes % 60;
            int hours = totalMinutes / 60;

            if (minutes != 0) {
                if (minutes == 1) {
                    sleepDurationSummary = (hours == 1) ?
                            res.getString(R.string.sesl_sleep_duration_one_hour_one_minute) :
                            res.getString(R.string.sesl_sleep_duration_in_hours_one_minute, hours);
                } else {
                    sleepDurationSummary = (hours == 1) ?
                            res.getString(R.string.sesl_sleep_duration_in_one_hour_minutes,
                                    minutes) :
                            res.getString(R.string.sesl_sleep_duration_in_hours_minutes, hours,
                                    minutes);
                }
            } else {
                sleepDurationSummary =
                        res.getQuantityString(R.plurals.sesl_sleep_duration_in_hour_plurals,
                                hours, hours);
            }
        } else {
            sleepDurationSummary = (totalMinutes > 1) ?
                    res.getQuantityString(R.plurals.sesl_sleep_duration_in_min_plurals,
                            totalMinutes, totalMinutes) : "";
        }
        return sleepDurationSummary;
    }


    public final void setTimeTextSizeRatio(@NonNull Resources resources, float textSizeRatio) {
        mWakeUpTimeText.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_text_size) * textSizeRatio);
        mBedTimeText.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_text_size) * textSizeRatio);
        mWakeUpTimeTargetText.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_text_size) * textSizeRatio);
        mBedTimeTargetText.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_text_size) * textSizeRatio);
        mWakeUpTimeTextLeftAmPm.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * SIZE_RATIO);
        mBedTimeTextLeftAmPm.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * SIZE_RATIO);
        mWakeUpTimeCenterTextLeftAmPm.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * SIZE_RATIO);
        mBedTimeCenterTextLeftAmPm.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * SIZE_RATIO);
        mWakeUpTimeTextRightAmPm.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * SIZE_RATIO);
        mBedTimeTextRightAmPm.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * SIZE_RATIO);
        mWakeUpTimeCenterTextRightAmPm.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * SIZE_RATIO);
        mBedTimeCenterTextRightAmPm.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * SIZE_RATIO);
        setTimeIconSize(resources, mWakeUpBottomIcon.getLayoutParams(), textSizeRatio);
        setTimeIconSize(resources, mWakeUpBottomIconRight.getLayoutParams(), textSizeRatio);
        setTimeIconSize(resources, mWakeUpCenterIcon.getLayoutParams(), textSizeRatio);
        setTimeIconSize(resources, mWakeUpCenterIconRight.getLayoutParams(), textSizeRatio);
        setTimeIconSize(resources, mBedTimeTopIcon.getLayoutParams(), textSizeRatio);
        setTimeIconSize(resources, mBedTimeTopIconRight.getLayoutParams(), textSizeRatio);
        setTimeIconSize(resources, mBedTimeCenterIcon.getLayoutParams(), textSizeRatio);
        setTimeIconSize(resources, mBedTimeCenterIconRight.getLayoutParams(), textSizeRatio);
    }

    private void setTimeTypeFace() {
        Typeface fontFromOpenTheme = getFontFromOpenTheme(mContext);

        if (fontFromOpenTheme == null) {
            try {
                fontFromOpenTheme = Build.VERSION.SDK_INT >= 33 ?
                        Typeface.create(Typeface.create("sec", Typeface.NORMAL), FONT_WEIGHT_LIGHT, false) :
                        Typeface.create("roboto-num3L", Typeface.NORMAL);
            } catch (Exception e) {
                Log.e("SeslSleepTimePicker", "setTimeTypeFace exception : " + e);
                return;
            }
        }
        mBedTimeText.setTypeface(fontFromOpenTheme);
        mWakeUpTimeText.setTypeface(fontFromOpenTheme);
        mBedTimeTargetText.setTypeface(fontFromOpenTheme);
        mWakeUpTimeTargetText.setTypeface(fontFromOpenTheme);
    }

    @SuppressLint("SetTextI18n")
    private boolean setTimeViewInTimePicker(
            float minutes,
            TextView hourTextView,
            TextView minuteTextView,
            TextView amPmTextView,
            TextView contentDescriptionTextView
    ) {
        int totalMinutes  = (int) minutes;
        int hours  = totalMinutes  / 60;
        int minutesRemainder  = totalMinutes  % 60;

        String[] timeTextString = getTimeTextString(hours, minutesRemainder);

        String minuteStr = timeTextString[0];
        String amPmStr = timeTextString[1];
        String hourStr = timeTextString[2];

        String finalTimeSeparator;
        if (Locale.getDefault().equals(Locale.CANADA_FRENCH)) {
            finalTimeSeparator = "h";
        } else {
            finalTimeSeparator = getTimeSeparatorText(mContext);
        }
        if (!DateFormat.is24HourFormat(mContext)) {
            minuteTextView.setText(amPmStr);
            if (contentDescriptionTextView != null) {
                contentDescriptionTextView.setText(amPmStr);
            }
        }
        hourTextView.setText(hourStr + finalTimeSeparator + minuteStr);
        hourTextView.setContentDescription(hourStr + finalTimeSeparator + minuteStr + amPmStr);

        String charSequence = amPmTextView.getText().toString();
        amPmTextView.setText(hourStr + finalTimeSeparator + minuteStr);
        amPmTextView.setContentDescription(hourStr + finalTimeSeparator + minuteStr + amPmStr);
        return !amPmTextView.getText().toString().equals(charSequence);
    }

    private String[] getTimeTextString(int hours, int minutesRemainder) {
        String minuteStr;
        String amPmStr;
        String hourStr;

        String[] strArr = new String[3];
        Locale locale = new Locale("es", "ES");
        if (DateFormat.is24HourFormat(mContext)) {
            hourStr = Locale.getDefault().equals(locale) ?
                    SeslSleepTimePickerUtil.toDigitString(hours  % 24) :
                    SeslSleepTimePickerUtil.toTwoDigitString(hours );
            minuteStr = SeslSleepTimePickerUtil.toTwoDigitString(minutesRemainder );
            amPmStr = "";
        } else {
            int hours12 = hours  % 12;
            hourStr = hours12 == 0 ? "ja".equals(Locale.getDefault().getLanguage()) ?
                    SeslSleepTimePickerUtil.toDigitString(0) :
                    SeslSleepTimePickerUtil.getHourFormatData(false) ?
                            SeslSleepTimePickerUtil.toTwoDigitString(12) :
                            SeslSleepTimePickerUtil.toDigitString(12) :
                    SeslSleepTimePickerUtil.getHourFormatData(false) ?
                            SeslSleepTimePickerUtil.toTwoDigitString(hours12) :
                            SeslSleepTimePickerUtil.toDigitString(hours12);
            minuteStr = SeslSleepTimePickerUtil.toTwoDigitString(minutesRemainder );
            String[] amPmStrings = new DateFormatSymbols().getAmPmStrings();
            amPmStr = amPmStrings != null ? hours  >= 12 ? amPmStrings[1] : amPmStrings[0] : "";
        }
        strArr[0] = minuteStr;
        strArr[1] = amPmStr;
        strArr[2] = hourStr;

        return strArr;
    }

    private void setWakeUpTimeTextVisibility(int i, int i2, int i3, int i4) {
        mWakeUpTimeCenterTextRightAmPm.setVisibility(i);
        mWakeUpTimeTextRightAmPm.setVisibility(i2);
        mWakeUpTimeCenterTextLeftAmPm.setVisibility(i3);
        mWakeUpTimeTextLeftAmPm.setVisibility(i4);
    }

    boolean updateBedTimeText() {
        if (DateFormat.is24HourFormat(mContext)) {
            setBedTimeTextVisibility(GONE, GONE, GONE, GONE);
            return setTimeViewInTimePicker(mBedTimeInMinute, mBedTimeText, null,
                    mBedTimeTargetText, null);

        } else if (SeslSleepTimePickerUtil.isLeftAmPm()) {
            setBedTimeTextVisibility(GONE, GONE, VISIBLE, VISIBLE);
            return setTimeViewInTimePicker(mBedTimeInMinute, mBedTimeText, mBedTimeTextLeftAmPm,
                    mBedTimeTargetText, mBedTimeCenterTextLeftAmPm);

        } else {
            setBedTimeTextVisibility(VISIBLE, VISIBLE, GONE, GONE);
            return setTimeViewInTimePicker(mBedTimeInMinute, mBedTimeText,
                    mBedTimeTextRightAmPm,
                    mBedTimeTargetText, mBedTimeCenterTextRightAmPm);
        }
    }

    @Override
    public final void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        float f = getResources().getConfiguration().screenHeightDp;
        setSleepOuterCircleContainerSize(f);
        setInnerCircleContainerSize();
        setTimeTextSize();
        setTimeTypeFace();
        setSleepTimePickerFrameSize(f);
    }

    @Override
    public final void onRestoreInstanceState(Parcelable parcelable) {
        Bundle bundle = (Bundle) parcelable;
        super.onRestoreInstanceState(bundle.getParcelable("PARENT"));
        mBedTimeInMinute = bundle.getFloat("mBedTime");
        mWakeupTimeInMinute = bundle.getFloat("mWakeUpTime");
        initSleepTimePickerData();
    }

    @NonNull
    @Override
    public final Parcelable onSaveInstanceState() {
        Parcelable onSaveInstanceState = super.onSaveInstanceState();
        Bundle bundle = new Bundle();
        bundle.putParcelable("PARENT", onSaveInstanceState);
        bundle.putFloat("mBedTime", mBedTimeInMinute);
        bundle.putFloat("mWakeUpTime", mWakeupTimeInMinute);
        return bundle;
    }

    boolean updateWakeUpTimeText() {
        if (DateFormat.is24HourFormat(this.mContext)) {
            setWakeUpTimeTextVisibility(GONE, GONE, GONE, GONE);
            return setTimeViewInTimePicker(mWakeupTimeInMinute, mWakeUpTimeText, null,
                    mWakeUpTimeTargetText, null);
        } else if (SeslSleepTimePickerUtil.isLeftAmPm()) {
            setWakeUpTimeTextVisibility(GONE, GONE, VISIBLE, VISIBLE);
            return setTimeViewInTimePicker(mWakeupTimeInMinute, mWakeUpTimeText,
                    mWakeUpTimeTextLeftAmPm, mWakeUpTimeTargetText,
                    mWakeUpTimeCenterTextLeftAmPm);
        } else {
            setWakeUpTimeTextVisibility(VISIBLE, VISIBLE, GONE, GONE);
            return setTimeViewInTimePicker(mWakeupTimeInMinute, mWakeUpTimeText,
                    mWakeUpTimeTextRightAmPm, mWakeUpTimeTargetText,
                    mWakeUpTimeCenterTextRightAmPm);
        }
    }

    @NonNull
    public LinearLayout getBedTimeView() {
        return mBedTimeView;
    }

    @NonNull
    public LinearLayout getWakeUpTimeView() {
        return mWakeUpTimeView;
    }

    public void setBedTimeInMinute(float timeInMinute) {
        mBedTimeInMinute = timeInMinute;
        initSleepTimePickerData();
    }

    public float getBedTimeInMinute() {
        return this.mBedTimeInMinute;
    }

    public void setWakeUpTimeInMinute(float wakeUpTimeInMinute) {
        this.mWakeupTimeInMinute = wakeUpTimeInMinute;
        initSleepTimePickerData();
    }

    public float getWakeUpTimeInMinute() {
        return mWakeupTimeInMinute;
    }

    public void setOnSleepTimeChangeListener(@Nullable OnSleepTimeChangedListener onSleepTimeChangedListener) {
        this.mOnSleepTimeChangedListener = onSleepTimeChangedListener;
    }

    public void setSleepDurationFormatter(@NonNull SleepDurationFormatter sleepDurationFormatter) {
        durationFormatter = sleepDurationFormatter;
    }

    public void setSleepDurationTextStyle(@StyleRes int style) {
        TextViewCompat.setTextAppearance(mSleepDuration, style);
    }

    public void setSleepGoal(float sleepTimeMinutes, float wakeupTimeMinutes) {
        TextView textView = findViewById(R.id.sleep_goal_text_id);
        textView.setVisibility(View.VISIBLE);
        float convertToAngle = convertToAngle(sleepTimeMinutes);
        float convertToAngle2 = convertToAngle(wakeupTimeMinutes);
        mCircularSeekBar.setSleepGoalWheel(
                ((convertToAngle % TOTAL_DEGREE) + TOTAL_DEGREE) % TOTAL_DEGREE,
                ((convertToAngle2 % TOTAL_DEGREE) + TOTAL_DEGREE) % TOTAL_DEGREE);
        setSleepGoalTimeDurationText(sleepTimeMinutes, wakeupTimeMinutes, textView);
    }

    private void setSleepGoalTimeDurationText(float sleepTimeMinutes, float wakeupTimeMinutes, TextView textView) {
        String durationText;
        Resources resources = mContext.getResources();
        int i = (int) (((wakeupTimeMinutes - sleepTimeMinutes) + TOTAL_MINUTES) % TOTAL_MINUTES);
        if (i > 60) {
            int i2 = i % 60;
            int i3 = i / 60;
            durationText = i2 != 0 ? i2 != 1 ? i3 == 1
                    ? resources.getString(R.string.sesl_sleep_goal_duration_in_one_hour_minutes, i2)
                    : resources.getString(R.string.sesl_sleep_goal_duration_in_hours_minutes, i3,
                            i2) : i3 == 1 ? resources.getString(R.string.sesl_sleep_goal_duration_one_hour_one_minute)
                    : resources.getString(R.string.sesl_sleep_goal_duration_in_hours_one_minute,
                            i3) : resources.getQuantityString(R.plurals.sesl_sleep_goal_duration_in_hour_plurals, i3,
                    i3);
        } else {
            durationText = i > 1 ? resources.getQuantityString(R.plurals.sesl_sleep_goal_duration_in_min_plurals, i,
                    i) : "";
        }
        textView.setText(durationText);
    }

}
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

import static androidx.picker.util.SeslSleepTimePickerUtil.isSmallDisplay;

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
import android.provider.Settings;
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
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.picker.R;
import androidx.picker.util.SeslSleepTimePickerUtil;
import androidx.picker.widget.SeslCircularSeekBarView.SeslCircularSeekBarRevealAnimation;

import java.text.DateFormatSymbols;
import java.util.Locale;


public class SeslSleepTimePicker extends LinearLayout {

    private static final String TAG = "SeslSleepTimePicker";

    static final class SleepDurationFormatterImpl implements SleepDurationFormatter {
        @Override
        public int format(float bedTime, float wakeupTime) {
            return (int) (((wakeupTime - bedTime) + 1440.0f) % 1440.0f);
        }
    }

    final TextView tvWakeupTimeAmPmRight;
    final TextView tvWakeupTimeAmPmCenterRight;

    private final RelativeLayout editOuterCircleContainer;
    private final FrameLayout editInnerCircleContainer;

    private final FrameLayout sleepTimePickerContainer;

    private final PathInterpolator interpolator = new PathInterpolator(0.22f, 0.25f, 0.0f, 1.0f);

    private final ImageView bottomCenterDurationWakeupImage;
    private final ImageView bottomCenterDurationWakeupImageRight;

    private SleepDurationFormatter durationFormatter = new SleepDurationFormatterImpl();

    private final ImageView topCenterDurationBedImage;
    private final ImageView topCenterDurationBedImageRight;
    private final ImageView centerIconWakeupTime;
    private final ImageView centerIconBedTime;
    private final ImageView centerIconWakeupTimeRight;
    private final ImageView centerIconBedTimeRight;

    OnSleepTimeChangedListener onChangedListener;
    final SeslCircularSeekBarView circularSeekBarView;

    private final Context context;

    float bedTimeInMinute;
    float wakeupTimeInMinute;
    LinearLayout topBedtimeLayout;
    LinearLayout bottomWakeUpTimeLayout;
    LinearLayout tvSleepRecordCenterBedTime;
    final TextView tvSleepCenterDurationBedtime;

    final LinearLayout tvSleepRecordCenterWakeupTime;

    final TextView tvSleepCenterDurationWakeuptime;
    Animator animator;
    private final TextView tvSleepDurationText;
    final TextView tvTopCenterDurationBedtime;
    final TextView tvBottomCenterDurationWakeuptime;
    final TextView tvBedtimeAmPmLeft;
    final TextView tvBedtimeAmPmCenterLeft;
    final TextView tvBedtimeAmPmRight;
    final TextView tvBedtimeAmPmCenterRight;
    final TextView tvWakeupTimeAmPmLeft;
    final TextView tvWakeupTimeAmPmCenterLeft;

    private final int mOuterCircleSize;
    private final int mOuterCircleMinSize;
    private float mInnerCircleRatio;


    /* loaded from: classes.dex */
    public interface OnSleepTimeChangedListener {
        /* renamed from: a */
        void onDurationChanged(float f, float f2);

        /* renamed from: b */
        void onTrackingStarted();

        /* renamed from: c */
        void onProgress();
    }


    public interface SleepDurationFormatter {
        int format(float bedTime, float wakeupTime);
    }

    final class SleepTimePickerListener implements SeslCircularSeekBarView.CircularSeekBarViewListener {

        public final SeslSleepTimePicker picker;

        SleepTimePickerListener(SeslSleepTimePicker timePicker) {
            this.picker = timePicker;
        }

        void onSelectedWakeupTimeIcon() {
            Log.d("SleepTimePicker", "onSelectedWakeUpTimeIcon");
            int color = ContextCompat.getColor(getContext(), R.color.sesl_bed_wakeup_time_color);
            int primaryColor = getPrimaryColor();
            TextView[] textViewArr = new TextView[]{tvBottomCenterDurationWakeuptime,
                    tvWakeupTimeAmPmLeft, tvWakeupTimeAmPmRight};
            TextView[] textViewArr2 = new TextView[]{tvSleepCenterDurationWakeuptime,
                    tvWakeupTimeAmPmCenterLeft, tvWakeupTimeAmPmCenterRight};
            animateColor( textViewArr, color, primaryColor,  100L);
            animateToCenter(
                    SeslSleepTimePicker.this,
                    bottomWakeUpTimeLayout,
                    tvSleepRecordCenterWakeupTime,
                    topBedtimeLayout
            );
            animateColor( textViewArr2, color, primaryColor,  50L);

        }

        void onSelectedBedTimeIcon() {
            Log.d(TAG, "onSelectedBedTimeIcon");
            int color = ContextCompat.getColor(getContext(), R.color.sesl_bed_wakeup_time_color);
            int primaryColor = getPrimaryColor();
            TextView[] textViewArr = new TextView[]{tvTopCenterDurationBedtime, tvBedtimeAmPmLeft
                    , tvBedtimeAmPmRight};
            TextView[] textViewArr2 = new TextView[]{tvSleepCenterDurationBedtime,
                    tvBedtimeAmPmCenterLeft, tvBedtimeAmPmCenterRight};
            SeslSleepTimePicker.animateColor(  textViewArr, color, primaryColor,  100L);
            SeslSleepTimePicker.animateToCenter(
                    SeslSleepTimePicker.this,
                    topBedtimeLayout,
                    tvSleepRecordCenterBedTime,
                    bottomWakeUpTimeLayout
            );
            SeslSleepTimePicker.animateColor(  textViewArr2, color, primaryColor,  50L);
        }

        void onProgressChangedBedTime(SeslCircularSeekBarView seslCircularSeekBarView,
                float bedTimePosition) {
            Log.d("SleepTimePicker",
                    "onProgressChangedBedTime : BedTimePosition " + bedTimePosition);
            float pointToTime = SeslSleepTimePickerUtil.pointToTime(bedTimePosition);
            SeslSleepTimePicker picker = this.picker;
            picker.bedTimeInMinute = pointToTime;
            if (picker.updateBedTimeDisplay()) {
                seslCircularSeekBarView.performHapticFeedback(50065);
            }
            OnSleepTimeChangedListener listener = picker.onChangedListener;
            if (listener != null) {
                listener.onProgress();
            }
        }

        void onProgressChangedWakeupTime(SeslCircularSeekBarView seslCircularSeekBarView,
                float wakeupPosition) {
            Log.d("SleepTimePicker",
                    "onProgressChangedWakeupTime : WakeUpTimePosition " + wakeupPosition);
            float pointToTime = SeslSleepTimePickerUtil.pointToTime(wakeupPosition);
            SeslSleepTimePicker picker = this.picker;
            picker.wakeupTimeInMinute = pointToTime;
            if (picker.updateWakeupTimeDisplay()) {
                seslCircularSeekBarView.performHapticFeedback(50065);
            }
            OnSleepTimeChangedListener listener = picker.onChangedListener;
            if (listener != null) {
                listener.onProgress();
            }
        }

        void onStartTrackingTouch() {
            Log.d("SleepTimePicker", "onStartTrackingTouch");
            SeslSleepTimePicker picker = this.picker;
            OnSleepTimeChangedListener listener = picker.onChangedListener;
            if (listener != null) {
                listener.onTrackingStarted();
            }
        }

        void onUnselectedWakeupTimeIcon() {
            Log.d(TAG, "onUnselectedWakeupTimeIcon");
            int color = ContextCompat.getColor(getContext(), R.color.sesl_bed_wakeup_time_color);
            int primaryColor = getPrimaryColor();
            TextView[] textViewArr2 = new TextView[]{tvSleepCenterDurationWakeuptime,
                    tvWakeupTimeAmPmCenterLeft, tvWakeupTimeAmPmCenterRight};
            SeslSleepTimePicker.animateColor( textViewArr2, primaryColor, color,  50L);
            SeslSleepTimePicker.animateBackToPosition(
                    SeslSleepTimePicker.this,
                    tvSleepRecordCenterWakeupTime,
                    bottomWakeUpTimeLayout,
                    topBedtimeLayout
            );
            TextView[] textViewArr = new TextView[]{tvBottomCenterDurationWakeuptime,
                    tvWakeupTimeAmPmLeft,
                    tvWakeupTimeAmPmRight};
            SeslSleepTimePicker.animateColor(  textViewArr, primaryColor, color,  200L);
        }

        void onUnselectedBedTimeIcon() {
            Log.d(TAG, "onUnselectedBedTimeIcon");
            int color = ContextCompat.getColor(getContext(), R.color.sesl_bed_wakeup_time_color);
            int primaryColor = getPrimaryColor();
            TextView[] textViewArr2 = new TextView[]{tvSleepCenterDurationBedtime,
                    tvBedtimeAmPmCenterLeft, tvBedtimeAmPmCenterRight};
            SeslSleepTimePicker.animateColor(  textViewArr2, primaryColor, color,  50L);
            SeslSleepTimePicker.animateBackToPosition(
                    SeslSleepTimePicker.this,
                    tvSleepRecordCenterBedTime,
                    topBedtimeLayout,
                    bottomWakeUpTimeLayout
            );
            TextView[] textViewArr = new TextView[]{tvTopCenterDurationBedtime, tvBedtimeAmPmLeft,
                    tvBedtimeAmPmRight};
            SeslSleepTimePicker.animateColor( textViewArr, primaryColor, color,  200L);

        }

        public void onUnselectedMiddleHandler() {
            Log.d(TAG, "onUnselectedMiddleHandler");
            int color = ContextCompat.getColor(getContext(), R.color.sesl_bed_wakeup_time_color);
            int primaryColor = getPrimaryColor();
            TextView[] textViewArr = new TextView[]{tvBottomCenterDurationWakeuptime,
                    tvWakeupTimeAmPmLeft,
                    tvWakeupTimeAmPmRight};
            TextView[] textViewArr2 = new TextView[]{tvSleepCenterDurationWakeuptime,
                    tvWakeupTimeAmPmCenterLeft, tvWakeupTimeAmPmCenterRight};
            TextView[] textViewArr3 = new TextView[]{tvTopCenterDurationBedtime, tvBedtimeAmPmLeft,
                    tvBedtimeAmPmRight};
            TextView[] textViewArr4 = new TextView[]{tvSleepCenterDurationBedtime,
                    tvBedtimeAmPmCenterLeft, tvBedtimeAmPmCenterRight};
            SeslSleepTimePicker.animateColor( textViewArr, primaryColor,color,200L);
            SeslSleepTimePicker.animateColor( textViewArr2, primaryColor,color,200L);
            SeslSleepTimePicker.animateColor(  textViewArr3, primaryColor,color,200L);
            SeslSleepTimePicker.animateColor(  textViewArr4, primaryColor,color,200L);
        }

        void onStopTrackingTouch() {
            Log.d(TAG, "onStopTrackingTouch");
            updateSleepDurationText();
            if (onChangedListener != null) {
                onChangedListener.onDurationChanged(getBedTimeInMinute(), getWakeUpTimeInMinute());
            }
        }

        void onSelectedMiddleHandler() {
            Log.d(TAG, "onSelectedMiddleHandler");
            int color = ContextCompat.getColor(getContext(), R.color.sesl_bed_wakeup_time_color);
            int primaryColor = getPrimaryColor();
            TextView[] textViewArr = new TextView[]{tvBottomCenterDurationWakeuptime,
                    tvWakeupTimeAmPmLeft, tvWakeupTimeAmPmRight};
            TextView[] textViewArr2 = new TextView[]{tvSleepCenterDurationWakeuptime,
                    tvWakeupTimeAmPmCenterLeft, tvWakeupTimeAmPmCenterRight};
            TextView[] textViewArr3 = new TextView[]{tvTopCenterDurationBedtime,
                    tvBedtimeAmPmLeft, tvBedtimeAmPmRight};
            TextView[] textViewArr4 = new TextView[]{tvSleepCenterDurationBedtime,
                    tvBedtimeAmPmCenterLeft, tvBedtimeAmPmCenterRight};
            SeslSleepTimePicker.animateColor(  textViewArr, color, primaryColor,  100L);
            SeslSleepTimePicker.animateColor(  textViewArr2, color, primaryColor,  100L);
            SeslSleepTimePicker.animateColor(  textViewArr3, color, primaryColor,  100L);
            SeslSleepTimePicker.animateColor( textViewArr4, color, primaryColor,  100L);
        }
    }

    public SeslSleepTimePicker(@NonNull Context context, @Nullable AttributeSet attributeSet) {
        super(context, attributeSet);

        this.context = getContext();
        LayoutInflater.from(context).inflate(R.layout.sesl_sleep_time_picker, this);

        circularSeekBarView = findViewById(R.id.circular_seekbar);
        tvTopCenterDurationBedtime = findViewById(R.id.sleep_top_center_duration_bedtime);
        tvBedtimeAmPmLeft = findViewById(R.id.bedtime_am_pm_left);
        tvBedtimeAmPmRight = findViewById(R.id.bedtime_am_pm_right);
        tvBedtimeAmPmCenterLeft = findViewById(R.id.bedtime_center_am_pm_left);
        tvBedtimeAmPmCenterRight = findViewById(R.id.bedtime_center_am_pm_right);

        tvBottomCenterDurationWakeuptime =
                findViewById(R.id.sleep_bottom_center_duration_wakeuptime);
        tvWakeupTimeAmPmLeft = findViewById(R.id.wakeuptime_am_pm_left);
        tvWakeupTimeAmPmRight = findViewById(R.id.wakeuptime_am_pm_right);
        tvWakeupTimeAmPmCenterLeft = findViewById(R.id.wakeuptime_center_am_pm_left);
        tvWakeupTimeAmPmCenterRight = findViewById(R.id.wakeuptime_center_am_pm_right);

        tvSleepRecordCenterBedTime = findViewById(R.id.sleep_record_center_bedtime);
        tvSleepCenterDurationBedtime = findViewById(R.id.sleep_center_duration_bedtime);

        tvSleepRecordCenterWakeupTime = findViewById(R.id.sleep_record_center_wakeuptime);
        tvSleepCenterDurationWakeuptime = findViewById(R.id.sleep_center_duration_wakeuptime);

        tvSleepDurationText = findViewById(R.id.sleep_duration_text_id);

        Resources res = context.getResources();
        Configuration conf = res.getConfiguration();
        TextView textView = tvSleepDurationText;
        if (conf.fontScale > 1.3f && textView!= null) {
            tvSleepDurationText.setTextSize(1,
                    (textView.getTextSize() / res.getDisplayMetrics().scaledDensity) * 1.3f);
        }

        bottomCenterDurationWakeupImage =
                findViewById(R.id.sleep_bottom_center_duration_wakeupimage);
        bottomCenterDurationWakeupImageRight =
                findViewById(R.id.sleep_bottom_center_duration_wakeupimage_right);
        topCenterDurationBedImage = findViewById(R.id.sleep_top_center_duration_bedimage);
        topCenterDurationBedImageRight =
                findViewById(R.id.sleep_top_center_duration_bedimage_right);

        centerIconWakeupTime = findViewById(R.id.sleep_center_icon_wakeuptime);
        centerIconBedTime = findViewById(R.id.sleep_center_icon_bedtime);
        centerIconWakeupTimeRight = findViewById(R.id.sleep_center_icon_wakeuptime_right);
        centerIconBedTimeRight = findViewById(R.id.sleep_center_icon_bedtime_right);

        bottomCenterDurationWakeupImage.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        bottomCenterDurationWakeupImageRight.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        topCenterDurationBedImage.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        topCenterDurationBedImageRight.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        centerIconWakeupTime.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        centerIconBedTime.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        centerIconWakeupTimeRight.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        centerIconBedTimeRight.setColorFilter(ContextCompat.getColor(context,
                R.color.sesl_bed_wakeup_time_icon_color), PorterDuff.Mode.SRC_ATOP);
        tvSleepRecordCenterWakeupTime.setAlpha(0.0f);
        tvSleepRecordCenterBedTime.setAlpha(0.0f);

        topBedtimeLayout = findViewById(R.id.sleep_record_top_bed_time_layout);
        bottomWakeUpTimeLayout = findViewById(R.id.sleep_record_bottom_wakeup_time_layout);

        editOuterCircleContainer = findViewById(R.id.sleep_visual_edit_outer_circle_container);
        editInnerCircleContainer = findViewById(R.id.sleep_visual_edit_inner_circle_container);
        sleepTimePickerContainer =  findViewById(R.id.sleepTimePicker);

        mOuterCircleSize = (int) res.getDimension(R.dimen.sesl_sleep_visual_edit_outer_circle_size);
        mOuterCircleMinSize =
                (int) res.getDimension(R.dimen.sesl_sleep_visual_edit_outer_circle_min_size);
        mInnerCircleRatio = getInnerCircleRatio(res);

        circularSeekBarView.listener = new SleepTimePickerListener(this);

        setBedTimeInMinute(1320.0f);
        setWakeUpTimeInMinute(420.0f);
    }

    private static float getInnerCircleRatio(Resources res){
        TypedValue typedValue = new TypedValue();
        res.getValue(R.dimen.sesl_time_picker_inner_circle_container_ratio, typedValue, true);
        return typedValue.getFloat();
    }

    static void animateToCenter(
            @NonNull SeslSleepTimePicker timePicker,
            @NonNull LinearLayout layoutToCenter,
            @NonNull LinearLayout centerLayout,
            @NonNull LinearLayout layoutToHide
    ) {
        Animator animator = timePicker.animator;
        if (animator != null) {
            animator.cancel();
        }
        Rect layoutToCenterRec = getRect(layoutToCenter);
        Rect centerLayoutRec = getRect(centerLayout);
        centerLayout.setAlpha(1.0f);

        ObjectAnimator hideAnimation = ObjectAnimator.ofFloat(layoutToHide, View.ALPHA, 1.0f,
                0.0f).setDuration(100L);
        ObjectAnimator fasterHideAnimation = ObjectAnimator.ofFloat(layoutToCenter, View.ALPHA,
                1.0f, 0.0f).setDuration(66L);
        ObjectAnimator centerAnimation = ObjectAnimator.ofFloat(
                centerLayout,
                View.TRANSLATION_Y,
                layoutToCenterRec.top - centerLayoutRec.top, 0.0f
        ).setDuration(400L);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(centerAnimation).with(fasterHideAnimation);
        animatorSet.play(hideAnimation);
        animatorSet.setInterpolator(timePicker.interpolator);

        animatorSet.addListener(
                new AnimatorListenerAdapter(){
                    @Override
                    public void onAnimationCancel(@NonNull Animator animator) {
                        super.onAnimationCancel(animator);
                        timePicker.animator = null;
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

        timePicker.animator = animatorSet;
    }

    static void animateColor(
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

    static void animateBackToPosition(
            @NonNull SeslSleepTimePicker seslSleepTimePicker,
            @NonNull LinearLayout resumeToCenter,
            @NonNull LinearLayout layoutFromCenter,
            @NonNull LinearLayout layoutToShow
    ) {
        Animator animator = seslSleepTimePicker.animator;
        if (animator != null) {
            animator.cancel();
        }
        ObjectAnimator unhideAnimation = ObjectAnimator.ofFloat(layoutToShow, View.ALPHA, 0.0f,
                1.0f).setDuration(200L);

        Rect resumeToCenterLayoutRec = getRect(resumeToCenter);
        Rect layoutFromCenterRec = getRect(layoutFromCenter);
        ObjectAnimator recenterAnimation = ObjectAnimator.ofFloat(
                resumeToCenter,
                View.TRANSLATION_Y,
                0.0f, -(resumeToCenterLayoutRec.top - layoutFromCenterRec.top)
        ).setDuration(400L);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.play(recenterAnimation);
        animatorSet.play(unhideAnimation);
        animatorSet.setInterpolator(seslSleepTimePicker.interpolator);

        animatorSet.addListener(
                new AnimatorListenerAdapter(){
                    @Override
                    public void onAnimationCancel(@NonNull Animator animator) {
                        super.onAnimationCancel(animator);
                        seslSleepTimePicker.animator = null;
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
        seslSleepTimePicker.animator = animatorSet;
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

    private static void resizeIcon(Resources resources, ViewGroup.LayoutParams lp, float f) {
        lp.height =
                (int) (resources.getDimensionPixelSize(R.dimen.sesl_sleep_record_bed_image_icon_size) * f);
        lp.width =
                (int) (resources.getDimensionPixelOffset(R.dimen.sesl_sleep_record_bed_image_icon_size) * f);
    }

    private void setSleepOuterCircleContainerSize(float screenSize) {
        int dimension = isSmallDisplay(screenSize) ? mOuterCircleMinSize :  mOuterCircleSize;
        LayoutParams lp = (LayoutParams) this.editOuterCircleContainer.getLayoutParams();
        lp.height = dimension;
        lp.width = dimension;
    }

    private void setSleepTimePickerFrameSize(float screenSize) {
        int dimension = isSmallDisplay(screenSize) ? mOuterCircleMinSize :  mOuterCircleSize;
        ViewGroup.LayoutParams layoutParams = this.sleepTimePickerContainer.getLayoutParams();
        layoutParams.height = dimension;
        layoutParams.width = dimension;
    }

    private void updateSleepTimePicker() {
        float f = getResources().getConfiguration().screenHeightDp;
        setSleepOuterCircleContainerSize(f);
        updateInnerCircleSize();
        Resources resources = this.context.getResources();
        if (resources.getConfiguration().screenWidthDp < 290) {
            resizeLabelsAndIcons(resources, 0.75f);
        } else {
            resizeLabelsAndIcons(resources, 1.0f);
        }
        updateTypeFace();
        setSleepTimePickerFrameSize(f);
        updateWakeupTimeDisplay();

        SeslCircularSeekBarView seekbar = this.circularSeekBarView;

        seekbar.setProgressBasedOnAngle(
                (((((((wakeupTimeInMinute - 360.0f) + 1440.0f) % 1440.0f) * 360.0f) / 1440.0f) % 360.0f) + 360.0f) % 360.0f,
                0
        );
        seekbar.recalculateAll();
        seekbar.invalidate();
        updateBedTimeDisplay();

        seekbar.setProgressBasedOnAngle(
                (((((((bedTimeInMinute - 360.0f) + 1440.0f) % 1440.0f) * 360.0f) / 1440.0f) % 360.0f) + 360.0f) % 360.0f,
                1
        );
        seekbar.recalculateAll();
        seekbar.invalidate();
        updateSleepDurationText();

        if (SeslSleepTimePickerUtil.isMorning() && TextUtils.getLayoutDirectionFromLocale(
                Locale.getDefault()) == LAYOUT_DIRECTION_RTL) {
            centerIconBedTimeRight.setVisibility(View.VISIBLE);
            topCenterDurationBedImageRight.setVisibility(View.VISIBLE);
            topCenterDurationBedImage.setVisibility(View.GONE);
            centerIconBedTime.setVisibility(View.GONE);
            centerIconWakeupTimeRight.setVisibility(View.VISIBLE);
            bottomCenterDurationWakeupImageRight.setVisibility(View.VISIBLE);
            bottomCenterDurationWakeupImage.setVisibility(View.GONE);
            centerIconWakeupTime.setVisibility(View.GONE);
        } else {
            centerIconBedTimeRight.setVisibility(View.GONE);
            topCenterDurationBedImageRight.setVisibility(View.GONE);
            topCenterDurationBedImage.setVisibility(View.VISIBLE);
            centerIconBedTime.setVisibility(View.VISIBLE);
            centerIconWakeupTimeRight.setVisibility(View.GONE);
            bottomCenterDurationWakeupImageRight.setVisibility(View.GONE);
            bottomCenterDurationWakeupImage.setVisibility(View.VISIBLE);
            centerIconWakeupTime.setVisibility(View.VISIBLE);
        }

        circularSeekBarView.calculateProgressDegrees();

        SeslCircularSeekBarRevealAnimation revealAnimation = circularSeekBarView.mCircularSeekBarRevealAnimation;
        revealAnimation.progress = circularSeekBarView.mProgressDegrees;

        ValueAnimator animator = ValueAnimator.ofFloat(0.0f, 1.0f);
        animator.setDuration(800L);
        animator.setInterpolator(revealAnimation.interpolator);
        animator.addListener(revealAnimation.animatorListener);
        animator.addUpdateListener(animation -> {
            float animatedValue = (Float) animation.getAnimatedValue();
            circularSeekBarView.setProgressBasedOnAngle(
                    (circularSeekBarView.mFirstPointerPosition
                            + (((revealAnimation.progress + 360.0f) % 360.0f) * animatedValue))
                            % 360.0f, 0
            );
            circularSeekBarView.recalculateAll();
            circularSeekBarView.invalidate();
        });
        animator.start();

    }

    private void updateBedtimeAmPmViewsVisibility(
            int centerRightViewVisibility,
            int rightViewVisibility,
            int centerLeftViewVisibility,
            int leftViewVisibility
    ) {
        tvBedtimeAmPmCenterRight.setVisibility(centerRightViewVisibility);
        tvBedtimeAmPmRight.setVisibility(rightViewVisibility);
        tvBedtimeAmPmCenterLeft.setVisibility(centerLeftViewVisibility);
        tvBedtimeAmPmLeft.setVisibility(leftViewVisibility);
    }

    private void updateInnerCircleSize() {
        Resources res = context.getResources();

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
        if (isSmallDisplay(screenHeightDp)) {
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

    private void updateSleepDurationText() {
        Resources res = context.getResources();
        int totalMinutes = durationFormatter.format(bedTimeInMinute, wakeupTimeInMinute);
        String sleepDurationSummary;

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

        tvSleepDurationText.setText(sleepDurationSummary);
    }

    public final void resizeLabelsAndIcons(@NonNull Resources resources, float sizePercent) {
        tvBottomCenterDurationWakeuptime.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_text_size) * sizePercent);
        tvTopCenterDurationBedtime.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_text_size) * sizePercent);
        tvSleepCenterDurationWakeuptime.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_text_size) * sizePercent);
        tvSleepCenterDurationBedtime.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_text_size) * sizePercent);
        tvWakeupTimeAmPmLeft.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * 0.75f);
        tvBedtimeAmPmLeft.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * 0.75f);
        tvWakeupTimeAmPmCenterLeft.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * 0.75f);
        tvBedtimeAmPmCenterLeft.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * 0.75f);
        tvWakeupTimeAmPmRight.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * 0.75f);
        tvBedtimeAmPmRight.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * 0.75f);
        tvWakeupTimeAmPmCenterRight.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * 0.75f);
        tvBedtimeAmPmCenterRight.setTextSize(COMPLEX_UNIT_PX,
                resources.getDimensionPixelSize(R.dimen.sesl_sleep_time_am_pm_size) * 0.75f);
        resizeIcon(resources, bottomCenterDurationWakeupImage.getLayoutParams(), sizePercent);
        resizeIcon(resources, bottomCenterDurationWakeupImageRight.getLayoutParams(), sizePercent);
        resizeIcon(resources, centerIconWakeupTime.getLayoutParams(), sizePercent);
        resizeIcon(resources, centerIconWakeupTimeRight.getLayoutParams(), sizePercent);
        resizeIcon(resources, topCenterDurationBedImage.getLayoutParams(), sizePercent);
        resizeIcon(resources, topCenterDurationBedImageRight.getLayoutParams(), sizePercent);
        resizeIcon(resources, centerIconBedTime.getLayoutParams(), sizePercent);
        resizeIcon(resources, centerIconBedTimeRight.getLayoutParams(), sizePercent);
    }

    private void updateTypeFace() {
        Typeface createFromFile = null;
        String string = Settings.System.getString(context.getContentResolver(), "theme_font_clock");

        if (string != null) {
            if (!TextUtils.isEmpty(string)) {
                try {
                    createFromFile = Typeface.createFromFile(string);
                } catch (Exception ignored) {
                }
            }
        }

        if (createFromFile == null) {
            try {
                createFromFile = Build.VERSION.SDK_INT >= 28 ?
                        Typeface.create(Typeface.create("sec", Typeface.NORMAL), 300, false) :
                        Typeface.create("roboto-num3L", Typeface.NORMAL);
            } catch (Exception e) {
                Log.e("SeslSleepTimePicker", "setTimeTypeFace exception : " + e);
                return;
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private boolean updateTimeDisplay(
            float minutes,
            TextView hourTextView,
            TextView minuteTextView,
            TextView amPmTextView,
            TextView contentDescriptionTextView
    ) {

        int totalMinutes  = (int) minutes;
        int hours  = totalMinutes  / 60;
        int minutesRemainder  = totalMinutes  % 60;
        Locale locale = new Locale("es", "ES");
        Context context = this.context;

        String minuteStr;
        String amPmStr;
        String hourStr;

        if (DateFormat.is24HourFormat(context)) {
            hourStr = Locale.getDefault().equals(locale) ?
                    SeslSleepTimePickerUtil.formatToInteger(hours  % 24) :
                    SeslSleepTimePickerUtil.formatTwoDigitNumber(hours );
            minuteStr = SeslSleepTimePickerUtil.formatTwoDigitNumber(minutesRemainder );
            amPmStr = "";
        } else {
            int hours12 = hours  % 12;
            hourStr = hours12 == 0 ? "ja".equals(Locale.getDefault().getLanguage()) ?
                    SeslSleepTimePickerUtil.formatToInteger(0) :
                    SeslSleepTimePickerUtil.hasDuplicateHourMarkers() ?
                            SeslSleepTimePickerUtil.formatTwoDigitNumber(12) :
                            SeslSleepTimePickerUtil.formatToInteger(12) :
                    SeslSleepTimePickerUtil.hasDuplicateHourMarkers() ?
                            SeslSleepTimePickerUtil.formatTwoDigitNumber(hours12) :
                            SeslSleepTimePickerUtil.formatToInteger(hours12);
            minuteStr = SeslSleepTimePickerUtil.formatTwoDigitNumber(minutesRemainder );
            String[] amPmStrings = new DateFormatSymbols().getAmPmStrings();
            amPmStr = amPmStrings != null ? hours  >= 12 ? amPmStrings[1] : amPmStrings[0] : "";
        }

        String finalTimeSeparator;
        if (Locale.getDefault().equals(Locale.CANADA_FRENCH)) {
            finalTimeSeparator = "h";
        } else {
            String bestDateTimePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                    DateFormat.is24HourFormat(context) ? "Hm" : "hm");
            int lastIndexOfH = bestDateTimePattern.lastIndexOf(72);
            if (lastIndexOfH == -1) {
                lastIndexOfH = bestDateTimePattern.lastIndexOf(104);
            }
            if (lastIndexOfH == -1) {
                finalTimeSeparator = ":";
            } else {
                int lastIndexOfTimeSeparator = lastIndexOfH + 1;
                int indexOf = bestDateTimePattern.indexOf(109, lastIndexOfTimeSeparator);
                finalTimeSeparator = indexOf == -1 ?
                        Character.toString(bestDateTimePattern.charAt(lastIndexOfTimeSeparator)) :
                        bestDateTimePattern.substring(lastIndexOfTimeSeparator, indexOf);
            }
            finalTimeSeparator = Locale.getDefault().equals(Locale.CANADA_FRENCH) ? ":" :
                    finalTimeSeparator.replace("'", "");
        }
        if (!DateFormat.is24HourFormat(context)) {
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


    private void updateWakeupTimeAmPmVisibility(int i, int i2, int i3, int i4) {
        tvWakeupTimeAmPmCenterRight.setVisibility(i);
        tvWakeupTimeAmPmRight.setVisibility(i2);
        tvWakeupTimeAmPmCenterLeft.setVisibility(i3);
        tvWakeupTimeAmPmLeft.setVisibility(i4);
    }

    boolean updateBedTimeDisplay() {
        if (DateFormat.is24HourFormat(context)) {
            updateBedtimeAmPmViewsVisibility(GONE, GONE, GONE, GONE);
            return updateTimeDisplay(bedTimeInMinute, tvTopCenterDurationBedtime, null,
                    tvSleepCenterDurationBedtime, null);

        } else if (SeslSleepTimePickerUtil.isMorning()) {
            updateBedtimeAmPmViewsVisibility(GONE, GONE, VISIBLE, VISIBLE);
            return updateTimeDisplay(bedTimeInMinute, tvTopCenterDurationBedtime, tvBedtimeAmPmLeft,
                    tvSleepCenterDurationBedtime, tvBedtimeAmPmCenterLeft);

        } else {
            updateBedtimeAmPmViewsVisibility(VISIBLE, VISIBLE, GONE, GONE);
            return updateTimeDisplay(bedTimeInMinute, tvTopCenterDurationBedtime,
                    tvBedtimeAmPmRight,
                    tvSleepCenterDurationBedtime, tvBedtimeAmPmCenterRight);
        }
    }

    @Override
    public final void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        float f = getResources().getConfiguration().screenHeightDp;
        setSleepOuterCircleContainerSize(f);
        updateInnerCircleSize();
        Resources resources = context.getResources();
        if (resources.getConfiguration().screenWidthDp < 290) {
            resizeLabelsAndIcons(resources, 0.75f);
        } else {
            resizeLabelsAndIcons(resources, 1.0f);
        }
        updateTypeFace();
        setSleepTimePickerFrameSize(f);
    }

    @Override
    public final void onRestoreInstanceState(Parcelable parcelable) {
        Bundle bundle = (Bundle) parcelable;
        super.onRestoreInstanceState(bundle.getParcelable("PARENT"));
        bedTimeInMinute = bundle.getFloat("mBedTime");
        wakeupTimeInMinute = bundle.getFloat("mWakeUpTime");
        updateSleepTimePicker();
    }

    @NonNull
    @Override
    public final Parcelable onSaveInstanceState() {
        Parcelable onSaveInstanceState = super.onSaveInstanceState();
        Bundle bundle = new Bundle();
        bundle.putParcelable("PARENT", onSaveInstanceState);
        bundle.putFloat("mBedTime", bedTimeInMinute);
        bundle.putFloat("mWakeUpTime", wakeupTimeInMinute);
        return bundle;
    }


    boolean updateWakeupTimeDisplay() {
        if (DateFormat.is24HourFormat(this.context)) {
            updateWakeupTimeAmPmVisibility(GONE, GONE, GONE, GONE);
            return updateTimeDisplay(wakeupTimeInMinute, tvBottomCenterDurationWakeuptime, null,
                    tvSleepCenterDurationWakeuptime, null);
        } else if (SeslSleepTimePickerUtil.isMorning()) {
            updateWakeupTimeAmPmVisibility(GONE, GONE, VISIBLE, VISIBLE);
            return updateTimeDisplay(wakeupTimeInMinute, tvBottomCenterDurationWakeuptime,
                    tvWakeupTimeAmPmLeft, tvSleepCenterDurationWakeuptime,
                    tvWakeupTimeAmPmCenterLeft);
        } else {
            updateWakeupTimeAmPmVisibility(VISIBLE, VISIBLE, GONE, GONE);
            return updateTimeDisplay(wakeupTimeInMinute, tvBottomCenterDurationWakeuptime,
                    tvWakeupTimeAmPmRight, tvSleepCenterDurationWakeuptime,
                    tvWakeupTimeAmPmCenterRight);
        }
    }

    public float getBedTimeInMinute() {
        return this.bedTimeInMinute;
    }

    public LinearLayout getBedTimeView() {
        return this.topBedtimeLayout;
    }

    public float getWakeUpTimeInMinute() {
        return this.wakeupTimeInMinute;
    }

    public LinearLayout getWakeUpTimeView() {
        return bottomWakeUpTimeLayout;
    }

    public void setBedTimeInMinute(float timeInMinute) {
        bedTimeInMinute = timeInMinute;
        updateSleepTimePicker();
    }

    public void setOnSleepTimeChangeListener(@Nullable OnSleepTimeChangedListener onSleepTimeChangedListener) {
        this.onChangedListener = onSleepTimeChangedListener;
    }

    public void setSleepDurationFormatter(@Nullable SleepDurationFormatter sleepDurationFormatter) {
        durationFormatter = sleepDurationFormatter;
    }

    public void setSleepDurationTextStyle(int style) {
        TextViewCompat.setTextAppearance(tvSleepDurationText, style);
    }

    public void setWakeUpTimeInMinute(float wakeUpTimeInMinute) {
        this.wakeupTimeInMinute = wakeUpTimeInMinute;
        updateSleepTimePicker();
    }
}
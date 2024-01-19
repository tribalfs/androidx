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

package androidx.preference;

import static androidx.annotation.RestrictTo.Scope.LIBRARY;
import static androidx.appcompat.widget.SwitchCompat.SUPPORT_TOUCH_FEEDBACK;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Checkable;
import android.widget.CompoundButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.widget.SwitchCompat;
import androidx.core.content.res.TypedArrayUtils;
import androidx.core.view.ViewCompat;
import androidx.reflect.view.SeslHapticFeedbackConstantsReflector;
import androidx.reflect.view.SeslViewReflector;

/**
 * <p><b>SESL variant</b></p><br>
 *
 * A {@link Preference} that provides a two-state toggleable option.
 *
 * <p>This preference will save a boolean value to {@link android.content.SharedPreferences}.
 *
 * @attr name android:summaryOff
 * @attr name android:summaryOn
 * @attr name android:switchTextOff
 * @attr name android:switchTextOn
 * @attr name android:disableDependentsState
 */
public class SwitchPreferenceCompat extends TwoStatePreference {
    private final Listener mListener = new Listener();

    // Switch text for on and off states
    private CharSequence mSwitchOn;
    private CharSequence mSwitchOff;

    //Sesl
    public int mWidth = 0;
    public int mIsLargeLayout = 0;
    private static final int SWITCH_PREFERENCE_LAYOUT = 2;
    private static final int SWITCH_PREFERENCE_LAYOUT_LARGE = 1;
    private final DummyClickListener mClickListener = new DummyClickListener();
    //sesl

    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context      The {@link Context} that will style this preference
     * @param attrs        Style attributes that differ from the default
     * @param defStyleAttr An attribute in the current theme that contains a reference to a style
     *                     resource that supplies default values for the view. Can be 0 to not
     *                     look for defaults.
     * @param defStyleRes  A resource identifier of a style resource that supplies default values
     *                     for the view, used only if defStyleAttr is 0 or can not be found in the
     *                     theme. Can be 0 to not look for defaults.
     */
    public SwitchPreferenceCompat(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        TypedArray a = context.obtainStyledAttributes(attrs,
                R.styleable.SwitchPreferenceCompat, defStyleAttr, defStyleRes);

        setSummaryOn(TypedArrayUtils.getString(a, R.styleable.SwitchPreferenceCompat_summaryOn,
                R.styleable.SwitchPreferenceCompat_android_summaryOn));

        setSummaryOff(TypedArrayUtils.getString(a, R.styleable.SwitchPreferenceCompat_summaryOff,
                R.styleable.SwitchPreferenceCompat_android_summaryOff));

        setSwitchTextOn(TypedArrayUtils.getString(a,
                R.styleable.SwitchPreferenceCompat_switchTextOn,
                R.styleable.SwitchPreferenceCompat_android_switchTextOn));

        setSwitchTextOff(TypedArrayUtils.getString(a,
                R.styleable.SwitchPreferenceCompat_switchTextOff,
                R.styleable.SwitchPreferenceCompat_android_switchTextOff));

        setDisableDependentsState(TypedArrayUtils.getBoolean(a,
                R.styleable.SwitchPreferenceCompat_disableDependentsState,
                R.styleable.SwitchPreferenceCompat_android_disableDependentsState, false));

        a.recycle();
    }

    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context      The {@link Context} that will style this preference
     * @param attrs        Style attributes that differ from the default
     * @param defStyleAttr An attribute in the current theme that contains a reference to a style
     *                     resource that supplies default values for the view. Can be 0 to not
     *                     look for defaults.
     */
    public SwitchPreferenceCompat(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    /**
     * Construct a new SwitchPreference with the given style options.
     *
     * @param context The {@link Context} that will style this preference
     * @param attrs   Style attributes that differ from the default
     */
    public SwitchPreferenceCompat(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, R.attr.switchPreferenceCompatStyle);
    }

    /**
     * Construct a new SwitchPreference with default style options.
     *
     * @param context The {@link Context} that will style this preference
     */
    public SwitchPreferenceCompat(@NonNull Context context) {
        this(context, null);
    }

    @Override
    public void onBindViewHolder(@NonNull PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        View switchView = holder.findViewById(AndroidResources.ANDROID_R_SWITCH_WIDGET/*sesl*/);
        if (mIsLargeLayout != SWITCH_PREFERENCE_LAYOUT_LARGE) syncSwitchView(switchView);//sesl
        syncSummaryView(holder);
    }

    /**
     * Set the text displayed on the switch widget in the on state.
     * This should be a very short string, one word if possible.
     *
     * @param onText Text to display in the on state
     */
    public void setSwitchTextOn(@Nullable CharSequence onText) {
        mSwitchOn = onText;
        notifyChanged();
    }

    /**
     * Set the text displayed on the switch widget in the off state.
     * This should be a very short string, one word if possible.
     *
     * @param offText Text to display in the off state
     */
    public void setSwitchTextOff(@Nullable CharSequence offText) {
        mSwitchOff = offText;
        notifyChanged();
    }

    /**
     * @return The text that will be displayed on the switch widget in the on state
     */
    @Nullable
    public CharSequence getSwitchTextOn() {
        return mSwitchOn;
    }

    /**
     * Set the text displayed on the switch widget in the on state.
     * This should be a very short string, one word if possible.
     *
     * @param resId The text as a string resource ID
     */
    public void setSwitchTextOn(int resId) {
        setSwitchTextOn(getContext().getString(resId));
    }

    /**
     * @return The text that will be displayed on the switch widget in the off state
     */
    @Nullable
    public CharSequence getSwitchTextOff() {
        return mSwitchOff;
    }

    /**
     * Set the text displayed on the switch widget in the off state.
     * This should be a very short string, one word if possible.
     *
     * @param resId The text as a string resource ID
     */
    public void setSwitchTextOff(int resId) {
        setSwitchTextOff(getContext().getString(resId));
    }

    /**
     * @param view
     */
    @RestrictTo(LIBRARY)
    @Override
    protected void performClick(@NonNull View view) {
        super.performClick(view);
        syncViewIfAccessibilityEnabled(view);
    }

    private void syncViewIfAccessibilityEnabled(View view) {
        AccessibilityManager accessibilityManager = (AccessibilityManager)
                getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (!accessibilityManager.isEnabled()) {
            return;
        }

        View switchView = view.findViewById(AndroidResources.ANDROID_R_SWITCH_WIDGET/*sesl*/);
        if (mIsLargeLayout != 1) syncSwitchView(switchView);//sesl

        if (isTalkBackIsRunning()) return;//sesl

        View summaryView = view.findViewById(android.R.id.summary);
        syncSummaryView(summaryView);
    }

    private void syncSwitchView(View view) {
        if (view instanceof SwitchCompat) {
            final SwitchCompat switchView = (SwitchCompat) view;
            switchView.setOnCheckedChangeListener(null);
        }
        if (view instanceof Checkable) {
            ((Checkable) view).setChecked(mChecked);
        }
        if (view instanceof SwitchCompat) {
            final SwitchCompat switchView = (SwitchCompat) view;
            switchView.setTextOn(mSwitchOn);
            switchView.setTextOff(mSwitchOff);
            switchView.setOnCheckedChangeListener(mListener);
            //Sesl
            if (switchView.isClickable()) {
                switchView.setOnClickListener(mClickListener);
            }
            if (isTalkBackIsRunning() && !(this instanceof SeslSwitchPreferenceScreen)) {
                ViewCompat.setBackground(switchView, null);
                switchView.setClickable(false);
            }
            //sesl
        }
    }

    private class Listener implements CompoundButton.OnCheckedChangeListener {
        Listener() {}

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (!callChangeListener(isChecked)) {
                // Listener didn't like it, change it back.
                // CompoundButton will make sure we don't recurse.
                buttonView.setChecked(!isChecked);
                return;
            }

            SwitchPreferenceCompat.this.setChecked(isChecked);
        }
    }

    //Sesl
    public void onBindViewHolder(@NonNull PreferenceViewHolder preferenceViewHolder, int width) {
        mWidth = width;
        onBindViewHolder(preferenceViewHolder);
        updateLayout(preferenceViewHolder.itemView);
    }

    private void updateLayout(View view) {
        View widgetFrame = view.findViewById(R.id.widget_frame);
        @SuppressLint("CutPasteId")
        View androidWidgetFrame = view.findViewById(android.R.id.widget_frame);

        View switchWidget = view.findViewById(R.id.switch_widget);
        View androidSwitchWidget = view.findViewById(AndroidResources.ANDROID_R_SWITCH_WIDGET);

        Configuration configuration = getContext().getResources().getConfiguration();
        final int swDp = configuration.screenWidthDp;
        final int isLargeLayout = ((swDp > 320 || configuration.fontScale < 1.1f)
                && (swDp >= 411 || configuration.fontScale < 1.3f)) ? SWITCH_PREFERENCE_LAYOUT : SWITCH_PREFERENCE_LAYOUT_LARGE;

        TextView titleView = (TextView) view.findViewById(android.R.id.title);

        if (isLargeLayout != 1) {
            if (mIsLargeLayout != isLargeLayout) {
                mIsLargeLayout = isLargeLayout;

                androidWidgetFrame.setVisibility(View.VISIBLE);
                widgetFrame.setVisibility(View.GONE);
                titleView.requestLayout();
            }
            syncSwitchView(androidSwitchWidget);
            return;
        }

        mIsLargeLayout = isLargeLayout;
        float titleLen = titleView.getPaint().measureText(titleView.getText().toString());

        TextView summaryView = (TextView) view.findViewById(android.R.id.summary);
        float summaryLen = summaryView.getPaint().measureText(summaryView.getText().toString());

        if (summaryView.getVisibility() == View.GONE) {
            summaryLen = 0.0f;
        }

        final int switchSize;
        if (this instanceof SeslSwitchPreferenceScreen) {
            switchSize = getContext().getResources().getDimensionPixelSize(R.dimen.sesl_preference_screen_item_switch_size)
                    +  androidWidgetFrame.getPaddingEnd();
        } else {
            switchSize = getContext().getResources().getDimensionPixelSize(R.dimen.sesl_preference_item_switch_size)
                    +  androidWidgetFrame.getPaddingEnd();
        }
        float availableWidth = ((mWidth - view.getPaddingEnd()) - view.getPaddingStart()) - switchSize;

        if (titleLen >= availableWidth || summaryLen >= availableWidth) {
            widgetFrame.setVisibility(View.VISIBLE);
            androidWidgetFrame.setVisibility(View.GONE);
            titleView.requestLayout();

            SwitchCompat switchCompat = (SwitchCompat) switchWidget;
            if (!switchCompat.canHapticFeedback(mChecked)
                    && canHapticFeedback(mChecked, view, switchCompat)) {
                switchCompat.performHapticFeedback(SeslHapticFeedbackConstantsReflector.semGetVibrationIndex(27));
            }

            syncSwitchView(switchWidget);

            SwitchCompat switchCompatAndroidSwitch = (SwitchCompat) androidSwitchWidget;
            switchCompatAndroidSwitch.setOnCheckedChangeListener(null);
            switchCompatAndroidSwitch.setCheckedWithoutAnimation(mChecked);
            return;
        }

        androidWidgetFrame.setVisibility(View.VISIBLE);
        widgetFrame.setVisibility(View.GONE);
        titleView.requestLayout();

        SwitchCompat switchCompatAndroidSwitch = (SwitchCompat) androidSwitchWidget;
        if (!switchCompatAndroidSwitch.canHapticFeedback(mChecked)
                && canHapticFeedback(mChecked, view, switchCompatAndroidSwitch)) {
            switchCompatAndroidSwitch.performHapticFeedback(SeslHapticFeedbackConstantsReflector.semGetVibrationIndex(27));
        }
        syncSwitchView(androidSwitchWidget);
        SwitchCompat switchCompatWidget = (SwitchCompat) switchWidget;
        switchCompatWidget.setOnCheckedChangeListener(null);
        switchCompatWidget.setCheckedWithoutAnimation(mChecked);
    }

    @SuppressLint("NewApi")
    private boolean canHapticFeedback(boolean isChecked, View view, SwitchCompat switchCompat) {
        return SUPPORT_TOUCH_FEEDBACK
                && isChecked != switchCompat.isChecked()
                && view.hasWindowFocus()
                && SeslViewReflector.isVisibleToUser(view)
                && !view.isTemporarilyDetached();
    }

    private class DummyClickListener implements View.OnClickListener {
        private DummyClickListener() {}

        @Override
        public void onClick(View v) {
            callClickListener();
        }
    }
    //sesl
}

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

import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO;
import static android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES;

import static androidx.picker.util.SeslDatePickerFontUtil.getRegularFontTypeface;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.icu.text.DateFormatSymbols;
import android.icu.util.GregorianCalendar;
import android.os.Build;
import android.os.Handler;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.text.Editable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.FocusFinder;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.picker.R;
import androidx.picker.util.SeslAnimationListener;
import androidx.reflect.icu.SeslLocaleDataReflector;

import java.io.File;
import java.util.Calendar;
import java.util.Locale;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */
class SeslTimePickerSpinnerDelegate extends SeslTimePicker.AbsTimePickerDelegate {
    private static final String TAG = "SeslTimePickerSpinner";
    private static final boolean DEBUG = false;

    private static final boolean DEFAULT_ENABLED_STATE = true;
    private static final int DEFAULT_MINUTE_INTERVAL = 1;

    public static final char[] DIGIT_CHARACTERS = {
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
            1632, 1633, 1634, 1635, 1636, 1637, 1638, 1639, 1640, 1641,
            1776, 1777, 1778, 1779, 1780, 1781, 1782, 1783, 1784, 1785,
            2406, 2407, 2408, 2409, 2410, 2411, 2412, 2413, 2414, 2415,
            2534, 2535, 2536, 2537, 2538, 2539, 2540, 2541, 2542, 2543,
            3302, 3303, 3304, 3305, 3306, 3307, 3308, 3309, 3310, 3311,
            4160, 4161, 4162, 4163, 4164, 4165, 4166, 4167, 4168, 4169
    };

    private static final char QUOTE = '\'';

    private static final int HOURS_IN_HALF_DAY = 12;
    private static final int LAST_HOUR_IN_DAY = 23;

    private static final int LAYOUT_MODE_DEFAULT = 0;
    private static final int LAYOUT_MODE_MULTIPANE = 2;
    private static final int LAYOUT_MODE_PHONE = 1;

    private final View mAmPmMarginInside;
    final SeslNumberPicker mAmPmSpinner;
    private final EditText mAmPmSpinnerInput;
    private final String[] mAmPmStrings;
    private final View mDTPaddingLeft;
    private final View mDTPaddingRight;
    private final TextView mDivider;
    private final SeslNumberPicker mHourSpinner;
    private final EditText mHourSpinnerInput;
    final SeslNumberPicker mMinuteSpinner;
    private final EditText mMinuteSpinnerInput;
    private final View mPaddingLeft;
    private final View mPaddingRight;
    private Calendar mTempCalendar;
    private final LinearLayout mTimeLayout;
    private EditText[] mPickerTexts = new EditText[3];

    char mHourFormat;

    private final int mDefaultHeight;
    private final int mDefaultWidth;
    private int mLayoutMode;
    private int mMinuteInterval = DEFAULT_MINUTE_INTERVAL;

    private boolean mHourWithTwoDigit;
    private boolean mIs24HourView;
    boolean mIsAm;
    private boolean mIsDateTimeMode;
    boolean mIsEditTextMode;
    private boolean mIsMarginLeftShown;
    private boolean mIsInvalidMinute = false;
    boolean mSkipToChangeInterval = false;
    private boolean mIsEnabled = DEFAULT_ENABLED_STATE;
    boolean mIsAmPmAutoFlipped = false;

    private SeslNumberPicker.OnEditTextModeChangedListener mModeChangeListener = (numberPicker, isEditTextMode) -> {
                setEditTextMode(isEditTextMode);
                updateModeState(isEditTextMode);
            };

    private TextView.OnEditorActionListener mEditorActionListener = new TextView.OnEditorActionListener() {
        @Override
        public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                if (!mSkipToChangeInterval && !mMinuteSpinner.isChangedDefaultInterval()
                        && mMinuteSpinner.getValue() % 5 != 0) {
                    mMinuteSpinner.applyWheelCustomInterval(false);
                }
                updateInputState();
                setEditTextMode(false);
            }
            return false;
        }
    };


    SeslTimePickerSpinnerDelegate(SeslTimePicker delegator, Context context,
                           AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(delegator, context);

        TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.TimePicker, defStyleAttr, defStyleRes);
        mIsDateTimeMode = a.getBoolean(R.styleable.TimePicker_dateTimeMode, false);
        mLayoutMode = a.getInt(R.styleable.TimePicker_timeLayoutMode, LAYOUT_MODE_DEFAULT);
        a.recycle();

        LayoutInflater inflater = LayoutInflater.from(mContext);
        if (isDateTimeMode()) {
            if (mLayoutMode == LAYOUT_MODE_PHONE) {
                inflater.inflate(R.layout.sesl_spinning_datepicker_time_picker_spinner_phone, mDelegator, true);
            } else if (mLayoutMode == LAYOUT_MODE_MULTIPANE) {
                inflater.inflate(R.layout.sesl_spinning_datepicker_time_picker_spinner_multipane, mDelegator, true);
            } else {
                inflater.inflate(R.layout.sesl_spinning_datepicker_time_picker_spinner, mDelegator, true);
            }
        } else {
            inflater.inflate(R.layout.sesl_time_picker_spinner, mDelegator, true);
        }

        mHourSpinner = delegator.findViewById(R.id.sesl_timepicker_hour);
        mHourSpinner.setPickerContentDescription(context.getResources().getString(R.string.sesl_time_picker_hour));
        mHourSpinner.setOnEditTextModeChangedListener(mModeChangeListener);
        mHourSpinner.setOnValueChangedListener(new SeslNumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange( @NonNull SeslNumberPicker spinner, int oldVal, int newVal) {
                if (!is24Hour() && !mIsEditTextMode) {
                    final int newValueNeedAmPmChange = mHourFormat == 'K' ? 0 : 12;
                    if ((oldVal == HOURS_IN_HALF_DAY - 1 && newVal == newValueNeedAmPmChange)
                            || (oldVal == newValueNeedAmPmChange && newVal == HOURS_IN_HALF_DAY - 1)) {
                        mIsAm = mAmPmSpinner.getValue() != 0;
                        mAmPmSpinner.setEnabled(false);
                        mAmPmSpinner.performClick(false);
                        mIsAmPmAutoFlipped = true;

                        new Handler().postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                mIsAmPmAutoFlipped = false;
                                if (mAmPmSpinner != null) {
                                    mAmPmSpinner.setEnabled(true);
                                }
                            }
                        }, 500);
                    }
                }

                onTimeChanged();
            }
        });
        mHourSpinnerInput = mHourSpinner.findViewById(R.id.numberpicker_input);
        mHourSpinner.setYearDateTimeInputMode();
        mHourSpinnerInput.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_ACTION_NEXT);
        mHourSpinner.setMaxInputLength(2);
        mHourSpinnerInput.setOnEditorActionListener(mEditorActionListener);

        mDivider = mDelegator.findViewById(R.id.sesl_timepicker_divider);
        if (mDivider != null) {
            setDividerText();
        }

        final Resources res = mDelegator.getResources();

        final int smallestScreenWidthDp = res.getConfiguration().smallestScreenWidthDp;
        if (smallestScreenWidthDp >= 600) {
            mDefaultWidth = res.getDimensionPixelSize(R.dimen.sesl_time_picker_dialog_min_width);
        } else {
            mDefaultWidth = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                    smallestScreenWidthDp, res.getDisplayMetrics()) + 0.5f);
        }
        mDefaultHeight = res.getDimensionPixelSize(R.dimen.sesl_time_picker_spinner_height);

        mMinuteSpinner = mDelegator.findViewById(R.id.sesl_timepicker_minute);
        mMinuteSpinner.setYearDateTimeInputMode();
        mMinuteSpinner.setMinValue(0);
        mMinuteSpinner.setMaxValue(59);
        mMinuteSpinner.setOnLongPressUpdateInterval(100);
        mMinuteSpinner.setSkipValuesOnLongPressEnabled(true);
        mMinuteSpinner.setFormatter(SeslNumberPicker.getTwoDigitFormatter());
        mMinuteSpinner.setPickerContentDescription(context.getResources()
                .getString(R.string.sesl_time_picker_minute));
        mMinuteSpinner.setOnEditTextModeChangedListener(mModeChangeListener);
        mMinuteSpinner.setOnValueChangedListener(new SeslNumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(@NonNull SeslNumberPicker spinner, int oldVal, int newVal) {
                onTimeChanged();
            }
        });
        mMinuteSpinnerInput = mMinuteSpinner.findViewById(R.id.numberpicker_input);
        mMinuteSpinnerInput.setImeOptions(EditorInfo.IME_FLAG_NO_FULLSCREEN
                | EditorInfo.IME_ACTION_DONE);
        mMinuteSpinner.setMaxInputLength(2);
        mMinuteSpinnerInput.setOnEditorActionListener(mEditorActionListener);
        
        mAmPmStrings = getAmPmStrings(context);
        mDTPaddingRight = mDelegator.findViewById(R.id.sesl_datetimepicker_padding_right);
        mDTPaddingLeft = mDelegator.findViewById(R.id.sesl_datetimepicker_padding_left);
        mIsMarginLeftShown = false;
        mPaddingLeft = mDelegator.findViewById(R.id.sesl_timepicker_padding_left);
        mPaddingRight = mDelegator.findViewById(R.id.sesl_timepicker_padding_right);
        mTimeLayout = mDelegator.findViewById(R.id.sesl_timepicker_hour_minute_layout);
        mAmPmMarginInside = mDelegator.findViewById(R.id.sesl_timepicker_ampm_picker_margin);

        mAmPmSpinner = mDelegator.findViewById(R.id.sesl_timepicker_ampm);
        mAmPmSpinner.setAmPm();
        mAmPmSpinner.setMinValue(0);
        mAmPmSpinner.setMaxValue(1);
        mAmPmSpinner.setDisplayedValues(mAmPmStrings);
        mAmPmSpinner.setOnValueChangedListener(new SeslNumberPicker.OnValueChangeListener() {
            @Override
            public void onValueChange(@NonNull SeslNumberPicker spinner, int oldVal, int newVal) {
                if (!mAmPmSpinner.isEnabled()) {
                    mAmPmSpinner.setEnabled(true);
                }

                if (mIsAmPmAutoFlipped) {
                    mIsAmPmAutoFlipped = false;
                } else {
                    if (mIsAm && newVal == 0 || !mIsAm && newVal == 1) {
                        return;
                    }
                    mIsAm = newVal == 0;
                    updateAmPmControl();
                    onTimeChanged();
                    validCheck();
                }
            }
        });
        mAmPmSpinnerInput = mAmPmSpinner.findViewById(R.id.numberpicker_input);
        mAmPmSpinnerInput.setInputType(EditorInfo.IME_ACTION_UNSPECIFIED);
        mAmPmSpinnerInput.setCursorVisible(false);
        mAmPmSpinnerInput.setFocusable(false);
        mAmPmSpinnerInput.setFocusableInTouchMode(false);

        final byte defAmPmStringDirectionality
                = Character.getDirectionality(mAmPmStrings[0].charAt(0));
        final boolean isAmPmStringRTL
                = defAmPmStringDirectionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                || defAmPmStringDirectionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;

        final byte defLocaleDirectionality
                = Character.getDirectionality(mCurrentLocale.getDisplayName(mCurrentLocale).charAt(0));
        final boolean isLocaleRTL
                = defLocaleDirectionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT
                || defLocaleDirectionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;

        final boolean isAmPmAtStart = isAmPmAtStart();
        if ((isAmPmAtStart && isLocaleRTL == isAmPmStringRTL)
                || (!isAmPmAtStart && isLocaleRTL != isAmPmStringRTL)) {
            changeAmPmView();
        }

        getHourFormatData();
        updateHourControl();
        updateAmPmControl();

        setHour(mTempCalendar.get(Calendar.HOUR_OF_DAY));
        setMinute(mTempCalendar.get(Calendar.MINUTE));

        if (!isEnabled()) {
            setEnabled(false);
        }

        if (mDelegator.getImportantForAccessibility() == IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            mDelegator.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        setTextWatcher();

        if (isDateTimeMode()) {
            final float size = (((float) res
                    .getDimensionPixelSize(R.dimen.sesl_spinning_date_picker_date_spinner_text_size))
                    * 160.0f)
                        / ((float) res.getDisplayMetrics().densityDpi);
            setNumberPickerTextSize(SeslTimePicker.PICKER_HOUR, size);
            setNumberPickerTextSize(SeslTimePicker.PICKER_MINUTE, size);
            setNumberPickerTextSize(SeslTimePicker.PICKER_DIVIDER, size);
            setNumberPickerTextSize(SeslTimePicker.PICKER_AMPM, size);
        }
    }

    @Override
    public void requestLayout() {
        if (mAmPmSpinner != null) {
            mAmPmSpinner.requestLayout();
        }
        if (mHourSpinner != null) {
            mHourSpinner.requestLayout();
        }
        if (mMinuteSpinner != null) {
            mMinuteSpinner.requestLayout();
        }
    }

    private void changeAmPmView() {
        ViewGroup amPmParent = mDelegator.findViewById(R.id.sesl_timepicker_layout);

        amPmParent.removeView(mAmPmSpinner);
        amPmParent.removeView(mAmPmMarginInside);

        final int index = isDateTimeMode() ?
                1 + amPmParent.indexOfChild(mDTPaddingLeft) : 1;
        amPmParent.addView(mAmPmMarginInside, index);
        amPmParent.addView(mAmPmSpinner, index);
    }

    void updateModeState(boolean mode) {
        if (mIsEditTextMode != mode && !mode) {
            if (mHourSpinner.isEditTextMode()) {
                mHourSpinner.setEditTextMode(false);
            }
            if (mMinuteSpinner.isEditTextMode()) {
                mMinuteSpinner.setEditTextMode(false);
            }
        }
    }

    private void getHourFormatData() {
        final String bestDateTimePattern = DateFormat.getBestDateTimePattern(mCurrentLocale,
                (mIs24HourView) ? "Hm" : "hm");
        final int lengthPattern = bestDateTimePattern.length();
        mHourWithTwoDigit = false;
        for (int i = 0; i < lengthPattern; i++) {
            final char c = bestDateTimePattern.charAt(i);
            if (c == 'H' || c == 'h' || c == 'K' || c == 'k') {
                mHourFormat = c;
                if (i + 1 < lengthPattern && c == bestDateTimePattern.charAt(i + 1)) {
                    mHourWithTwoDigit = true;
                }
                break;
            }
        }
    }

    private boolean isAmPmAtStart() {
        final String bestDateTimePattern = DateFormat.getBestDateTimePattern(mCurrentLocale,
                "hm");
        return bestDateTimePattern.startsWith("a");
    }

    private void setDividerText() {
        final String skeleton = mIs24HourView ? "Hm" : "hm";
        final String bestDateTimePattern = DateFormat.getBestDateTimePattern(mCurrentLocale, skeleton);
        final String separatorText = getHourMinSeparatorFromPattern(bestDateTimePattern);
        mDivider.setText(separatorText);

        Typeface defaultTypeface = Typeface.defaultFromStyle(Typeface.NORMAL);
        Typeface legacyTypeface = Typeface.create("sec-roboto-condensed-light", Typeface.NORMAL);
        Typeface pickerTypeface = getRegularFontTypeface();
        if (!defaultTypeface.equals(pickerTypeface)) {
            legacyTypeface = pickerTypeface;
        } else if (legacyTypeface.equals(pickerTypeface)) {
            legacyTypeface = Typeface.create("sans-serif-thin", Typeface.NORMAL);
        }

        final String themeTypeFace = Settings.System.getString(mContext.getContentResolver(), "theme_font_clock");
        if (themeTypeFace != null && !themeTypeFace.equals("")) {
            mDivider.setTypeface(getFontTypeface(themeTypeFace));
        }
        mDivider.setTypeface(legacyTypeface);
    }

    private static String getHourMinSeparatorFromPattern(String dateTimePattern) {
        boolean foundHourPattern = false;

        for (int i = 0; i < dateTimePattern.length(); i++) {
            switch (dateTimePattern.charAt(i)) {
                case ' ':
                    break;
                case QUOTE:
                    if (!foundHourPattern) {
                        break;
                    } else {
                        SpannableStringBuilder quotedSubstring
                                = new SpannableStringBuilder(dateTimePattern.substring(i));
                        int quotedTextLength = appendQuotedText(quotedSubstring, 0);
                        return quotedSubstring.subSequence(0, quotedTextLength).toString();
                    }
                case 'H':
                case 'K':
                case 'h':
                case 'k':
                    foundHourPattern = true;
                    break;
                default:
                    if (!foundHourPattern) {
                        break;
                    } else {
                        return Character.toString(dateTimePattern.charAt(i));
                    }
            }
        }
        return ":";
    }

    //Returns number of quoted chars
    private static int appendQuotedText(SpannableStringBuilder spannableStringBuilder, int startIndex) {
        final int strLength = spannableStringBuilder.length();
        final int endIndex = startIndex + 1;

        if (endIndex < strLength && spannableStringBuilder.charAt(endIndex) == QUOTE) {
            spannableStringBuilder.delete(startIndex, endIndex);
            return 1;
        } else {
            spannableStringBuilder.delete(startIndex, endIndex);
            int quotedCount = 0;
            int lastIndex = strLength - 1;
            while (startIndex < lastIndex) {
                if (spannableStringBuilder.charAt(startIndex) == QUOTE) {
                    final int testEndIndex = startIndex + 1;
                    if (testEndIndex < lastIndex && spannableStringBuilder.charAt(testEndIndex) == QUOTE) {
                        spannableStringBuilder.delete(startIndex, testEndIndex);
                        lastIndex--;
                        quotedCount++;
                        startIndex = testEndIndex;
                    }else{
                        spannableStringBuilder.delete(startIndex, testEndIndex);
                        break;
                    }
                } else {
                    startIndex++;
                    quotedCount++;
                }
            }
            return quotedCount;
        }
    }

    private static Typeface getFontTypeface(String ft) {
        File fontFile = new File(ft);
        if (!fontFile.exists()) {
            return null;
        }
        try {
            return Typeface.createFromFile(ft);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void setHour(int hour) {
        setCurrentHour(hour, true);
    }

    private void setCurrentHour(int currentHour, boolean notifyTimeChanged) {
        if (currentHour == getHour()) {
            return;
        }
        if (!is24Hour()) {
            if (currentHour >= HOURS_IN_HALF_DAY) {
                mIsAm = false;
                if (currentHour > HOURS_IN_HALF_DAY) {
                    currentHour = currentHour - HOURS_IN_HALF_DAY;
                }
            } else {
                mIsAm = true;
                if (currentHour == 0) {
                    currentHour = HOURS_IN_HALF_DAY;
                }
            }
            updateAmPmControl();
        }
        mHourSpinner.setValue(currentHour);
        if (notifyTimeChanged) {
            onTimeChanged();
        }
    }

    @Override
    public int getHour() {
        int currentHour = mHourSpinner.getValue();
        if (is24Hour()) {
            return currentHour;
        } else if (mIsAm) {
            return currentHour % HOURS_IN_HALF_DAY;
        } else {
            return (currentHour % HOURS_IN_HALF_DAY) + HOURS_IN_HALF_DAY;
        }
    }

    @Override
    public void setMinute(int minute) {
        final int interval = getMinuteInterval();

        if (interval != DEFAULT_MINUTE_INTERVAL) {
            if (mIsEditTextMode) {
                mMinuteSpinner.setValue(minute);
            } else {
                if (minute % interval == 0) {
                    mMinuteSpinner.applyWheelCustomInterval(true);
                } else {
                    mMinuteSpinner.applyWheelCustomInterval(false);
                }
                mMinuteSpinner.setValue(minute);
            }
        } else {
            if (minute == getMinute()) {
                if (isCharacterNumberLanguage()) {
                    mMinuteSpinner.setValue(minute);
                }
                return;
            }

            mMinuteSpinner.setValue(minute);
        }

        onTimeChanged();
    }

    @Override
    public int getMinute() {
        return mMinuteSpinner.getValue();
    }

    @Override
    public void setIs24Hour(boolean is24HourView) {
        if (mIs24HourView == is24HourView) {
            return;
        }
        int currentHour = getHour();
        mIs24HourView = is24HourView;
        getHourFormatData();
        updateHourControl();
        setCurrentHour(currentHour, false);
        updateAmPmControl();
    }

    @Override
    public void showMarginLeft(boolean show) {
        mIsMarginLeftShown = show;
        if (isDateTimeMode()) {
            updateAmPmControl();
        }
    }

    @Override
    public boolean is24Hour() {
        return mIs24HourView;
    }

    @Override
    public void set5MinuteInterval(boolean interval) {
        if (interval) {
            if (getMinute() >= 58) {
                final int hour = getHour();
                setCurrentHour(hour == LAST_HOUR_IN_DAY ? 0 : hour + 1, false);
            }
            mMinuteSpinner.setCustomIntervalValue(5);
            mMinuteSpinner.applyWheelCustomInterval(true);
            setMinuteInterval(5);
        } else {
            mMinuteSpinner.setCustomIntervalValue(5);
        }
    }

    @Override
    public void setOnTimeChangedListener(SeslTimePicker.OnTimeChangedListener onTimeChangedListener) {
        mOnTimeChangedListener = onTimeChangedListener;
    }

    @Override
    public void setEnabled(boolean enabled) {
        mMinuteSpinner.setEnabled(enabled);
        if (mDivider != null) {
            mDivider.setEnabled(enabled);
        }
        mHourSpinner.setEnabled(enabled);
        mAmPmSpinner.setEnabled(enabled);
        mIsEnabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    @Override
    public int getBaseline() {
        return mHourSpinner.getBaseline();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setCurrentLocale(newConfig.locale);
    }

    @Override
    public Parcelable onSaveInstanceState(Parcelable superState) {
        return new SavedState(superState, getHour(), getMinute());
    }

    @Override
    public void onRestoreInstanceState(Parcelable state) {
        final SavedState ss = (SavedState) state;
        setHour(ss.getHour());
        setMinute(ss.getMinute());
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        onPopulateAccessibilityEvent(event);
        return true;
    }

    @Override
    public void onPopulateAccessibilityEvent(AccessibilityEvent event) {
        int flags = DateUtils.FORMAT_SHOW_TIME;
        if (mIs24HourView) {
            flags |= DateUtils.FORMAT_24HOUR;
        } else {
            flags |= DateUtils.FORMAT_12HOUR;
        }
        mTempCalendar.set(Calendar.HOUR_OF_DAY, getHour());
        mTempCalendar.set(Calendar.MINUTE, getMinute());
        String selectedDateUtterance = DateUtils.formatDateTime(mContext,
                mTempCalendar.getTimeInMillis(), flags);
        event.getText().add(selectedDateUtterance);
    }

    @Override
    public void onInitializeAccessibilityEvent(AccessibilityEvent event) {
        event.setClassName(android.widget.TimePicker.class.getName());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        info.setClassName(android.widget.TimePicker.class.getName());
    }

    private int getMinuteInterval() {
        return mMinuteInterval;
    }

    private void setMinuteInterval(int interval) {
        mMinuteInterval = interval;
    }

    void updateInputState() {
        InputMethodManager inputMethodManager
                = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager != null) {
            if (inputMethodManager.isActive(mHourSpinnerInput)) {
                inputMethodManager.hideSoftInputFromWindow(mDelegator.getWindowToken(), 0);
                if (mHourSpinnerInput != null) {
                    mHourSpinnerInput.clearFocus();
                }
            } else if (inputMethodManager.isActive(mMinuteSpinnerInput)) {
                inputMethodManager.hideSoftInputFromWindow(mDelegator.getWindowToken(), 0);
                if (mMinuteSpinnerInput != null) {
                    mMinuteSpinnerInput.clearFocus();
                }
            }
        }
    }

    private void updateAmPmControl() {
        if (is24Hour()) {
            mAmPmMarginInside.setVisibility(View.GONE);
            mAmPmSpinner.setVisibility(View.GONE);

            if (!isDateTimeMode()) {
                LinearLayout.LayoutParams lp
                        = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 560.0f);
                mPaddingLeft.setLayoutParams(lp);
                mPaddingRight.setLayoutParams(lp);
                mDivider.setLayoutParams(lp);
                mTimeLayout.setLayoutParams(
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3080.0f));
            } else {
                mPaddingRight.setVisibility(View.VISIBLE);
                if (mIsMarginLeftShown) {
                    mPaddingLeft.setVisibility(View.VISIBLE);
                }
                mDTPaddingRight.setVisibility(View.GONE);
                mDTPaddingLeft.setVisibility(View.GONE);
            }
        } else {
            mAmPmSpinner.setValue(!mIsAm ? 1 : 0);
            mAmPmSpinner.setVisibility(View.VISIBLE);
            mAmPmMarginInside.setVisibility(View.VISIBLE);

            if (!isDateTimeMode()) {
                LinearLayout.LayoutParams lp
                        = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 270.0f);
                mPaddingLeft.setLayoutParams(lp);

                LinearLayout.LayoutParams lp2
                        = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 180.0f);
                mPaddingRight.setLayoutParams(lp2);
                mDivider.setLayoutParams(lp2);

                LinearLayout.LayoutParams lp3
                        = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2700.0f);
                mTimeLayout.setLayoutParams(lp3);
            } else {
                mPaddingLeft.setVisibility(View.GONE);
                mPaddingRight.setVisibility(View.GONE);
                mDTPaddingRight.setVisibility(View.VISIBLE);
                if (mIsMarginLeftShown) {
                    mDTPaddingLeft.setVisibility(View.VISIBLE);
                }
            }
        }
    }

    private void validCheck() {
        if (mIsEditTextMode) {
            if (mHourSpinnerInput != null && mHourSpinnerInput.hasFocus()) {
                if (!TextUtils.isEmpty(mHourSpinnerInput.getText())) {
                    int hour = Integer.parseInt(String.valueOf(mHourSpinnerInput.getText()));
                    if (!is24Hour()) {
                        if (!mIsAm && hour != 12) {
                            hour += 12;
                        } else if (mIsAm && hour == 12) {
                            hour = 0;
                        }
                    }
                    setHour(hour);
                    mHourSpinnerInput.selectAll();
                }
            } else {
                if (mMinuteSpinnerInput != null && mMinuteSpinnerInput.hasFocus()) {
                    if (!TextUtils.isEmpty(mMinuteSpinnerInput.getText())) {
                        setMinute(Integer.parseInt(String.valueOf(mMinuteSpinnerInput.getText())));
                        mMinuteSpinnerInput.selectAll();
                    }
                }
            }
        }
    }

    @Override
    public void setCurrentLocale(Locale locale) {
        super.setCurrentLocale(locale);
        mTempCalendar = Calendar.getInstance(locale);
    }

    void onTimeChanged() {
        if (mOnTimeChangedListener != null) {
            mOnTimeChangedListener.onTimeChanged(mDelegator, getHour(), getMinute());
        }
    }

    private void updateHourControl() {
        if (is24Hour()) {
            if (mHourFormat == 'k') {
                mHourSpinner.setMinValue(1);
                mHourSpinner.setMaxValue(24);
            } else {
                mHourSpinner.setMinValue(0);
                mHourSpinner.setMaxValue(23);
            }
        } else if (mHourFormat == 'K') {
            mHourSpinner.setMinValue(0);
            mHourSpinner.setMaxValue(11);
        } else {
            mHourSpinner.setMinValue(1);
            mHourSpinner.setMaxValue(12);
        }
        mHourSpinner.setFormatter(mHourWithTwoDigit ?
                SeslNumberPicker.getTwoDigitFormatter() : null);
    }

    private static class SavedState extends View.BaseSavedState {
        private final int mHour;
        private final int mMinute;

        private SavedState(Parcelable superState, int hour, int minute) {
            super(superState);
            mHour = hour;
            mMinute = minute;
        }

        private SavedState(Parcel source) {
            super(source);
            mHour = source.readInt();
            mMinute = source.readInt();
        }

        public int getHour() {
            return mHour;
        }

        public int getMinute() {
            return mMinute;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(mHour);
            dest.writeInt(mMinute);
        }

        public static final Parcelable.Creator<SavedState> CREATOR
                = new Parcelable.Creator<SavedState>() {
            @Override
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            @Override
            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    public static String[] getAmPmStrings(Context context) {
        String[] result = new String[2];

        if (Build.VERSION.SDK_INT >= 31) {
            Locale locale = context.getResources().getConfiguration().locale;
            DateFormatSymbols dfs = new DateFormatSymbols(GregorianCalendar.class, locale);

            String[] amPm = dfs.getAmPmStrings();
            String[] narrowAmPm = SeslLocaleDataReflector.getAmpmNarrowStrings(dfs);
            if (isMeaLanguage()) {
                result[0] = amPm[0];
                result[1] = amPm[1];
                return result;
            } else {
                result[0] = amPm[0].length() > 4 ? narrowAmPm[0] : amPm[0];
                result[1] = amPm[1].length() > 4 ? narrowAmPm[1] : amPm[1];
                return result;
            }
        } else {
            Locale locale = context.getResources().getConfiguration().locale;
            Object localeData = SeslLocaleDataReflector.get(locale);
            if (localeData != null) {
                String[] amPm = SeslLocaleDataReflector.getField_amPm(localeData);
                String narrowAm = SeslLocaleDataReflector.getField_narrowAm(localeData);
                String narrowPm = SeslLocaleDataReflector.getField_narrowPm(localeData);

                String am = amPm[0];
                String pm = amPm[1];

                if (isMeaLanguage()) {
                    result[0] = am;
                    result[1] = pm;
                    return result;
                }

                if (am.length() <= 4) {
                    narrowAm = am;
                }
                result[0] = narrowAm;

                if (pm.length() <= 4) {
                    narrowPm = pm;
                }
                result[1] = narrowPm;

                return result;
            } else {
                Log.e(TAG, "LocaleData failed. Use DateFormatSymbols for ampm");
                return new java.text.DateFormatSymbols().getAmPmStrings();
            }
        }
    }

    private boolean isDateTimeMode() {
        return mIsDateTimeMode;
    }

    private static boolean isMeaLanguage() {
        final String language = Locale.getDefault().getLanguage();
        return "lo".equals(language) || "ar".equals(language)
                || "fa".equals(language) || "ur".equals(language);
    }

    private static boolean isCharacterNumberLanguage() {
        final String language = Locale.getDefault().getLanguage();
        return "lo".equals(language) || "ar".equals(language) || "fa".equals(language)
                || "ur".equals(language) || "my".equals(language);
    }

    @Override
    public void setOnEditTextModeChangedListener(
            SeslTimePicker.OnEditTextModeChangedListener onEditTextModeChangedListener) {
        mOnEditTextModeChangedListener = onEditTextModeChangedListener;
    }

    @Override
    public void setEditTextMode(boolean editTextMode) {
        if (mIsEditTextMode != editTextMode) {
            mIsEditTextMode = editTextMode;

            InputMethodManager inputMethodManager
                    = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
            mHourSpinner.setEditTextMode(editTextMode);
            mMinuteSpinner.setEditTextMode(editTextMode);
            if (inputMethodManager != null) {
                if (!mIsEditTextMode) {
                    inputMethodManager.hideSoftInputFromWindow(mDelegator.getWindowToken(), 0);
                } else {
                    if (!inputMethodManager.showSoftInput(mMinuteSpinnerInput.hasFocus() ?
                            mMinuteSpinnerInput : mHourSpinnerInput, 0)) {
                        mDelegator.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                InputMethodManager inputMethodManager
                                        = (InputMethodManager) mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
                                if (mIsEditTextMode && inputMethodManager != null) {
                                    inputMethodManager.showSoftInput(mMinuteSpinnerInput.hasFocus() ?
                                            mMinuteSpinnerInput : mHourSpinnerInput, 0);
                                }
                            }
                        }, 20);
                    }
                }
            }

            if (mOnEditTextModeChangedListener != null) {
                mOnEditTextModeChangedListener.onEditTextModeChanged(mDelegator, editTextMode);
            }
        }
    }

    @Override
    public boolean isEditTextMode() {
        return mIsEditTextMode;
    }

    @Override
    public void startAnimation(int delayMillis, SeslAnimationListener listener) {
        if (isAmPmAtStart()) {
            mAmPmSpinner.startAnimation(delayMillis, null);
            mHourSpinner.startAnimation(delayMillis + 55, null);
            mMinuteSpinner.startAnimation(delayMillis + 110, listener);
        } else {
            mHourSpinner.startAnimation(delayMillis, null);
            mMinuteSpinner.startAnimation(delayMillis + 55, listener);
            mAmPmSpinner.startAnimation(delayMillis + 110, null);
        }
    }

    @Override
    public EditText getEditText(int picker) {
        switch (picker) {
            case SeslTimePicker.PICKER_HOUR:
                return mHourSpinner.getEditText();
            case SeslTimePicker.PICKER_MINUTE:
            default:
                return mMinuteSpinner.getEditText();
            case SeslTimePicker.PICKER_AMPM:
                return mAmPmSpinner.getEditText();
        }
    }

    @Override
    public SeslNumberPicker getNumberPicker(int picker) {
        switch (picker) {
            case SeslTimePicker.PICKER_HOUR:
                return mHourSpinner;
            case SeslTimePicker.PICKER_MINUTE:
            default:
                return mMinuteSpinner;
            case SeslTimePicker.PICKER_AMPM:
                return mAmPmSpinner;
        }
    }

    @Override
    public void setNumberPickerTextSize(int picker, float size) {
        switch (picker) {
            case SeslTimePicker.PICKER_HOUR:
                mHourSpinner.setTextSize(size);
                break;
            case SeslTimePicker.PICKER_MINUTE:
                mMinuteSpinner.setTextSize(size);
                break;
            case SeslTimePicker.PICKER_AMPM:
                mAmPmSpinner.setTextSize(size);
                break;
            case SeslTimePicker.PICKER_DIVIDER:
                mDivider.setTextSize(TypedValue.COMPLEX_UNIT_DIP, size);
                break;
            default:
                mMinuteSpinner.setTextSize(size);
                break;
        }
    }

    @Override
    public void setNumberPickerTextTypeface(int picker, Typeface typeface) {
        switch (picker) {
            case SeslTimePicker.PICKER_HOUR:
                mHourSpinner.setTextTypeface(typeface);
                break;
            case SeslTimePicker.PICKER_MINUTE:
                mMinuteSpinner.setTextTypeface(typeface);
                break;
            case SeslTimePicker.PICKER_AMPM:
                mAmPmSpinner.setTextTypeface(typeface);
                break;
            case SeslTimePicker.PICKER_DIVIDER:
                mDivider.setTypeface(typeface);
                break;
            default:
                mMinuteSpinner.setTextTypeface(typeface);
                break;
        }
    }

    @Override
    public int getDefaultWidth() {
        return mDefaultWidth;
    }

    @Override
    public int getDefaultHeight() {
        return mDefaultHeight;
    }

    private void setTextWatcher() {
        mPickerTexts[SeslTimePicker.PICKER_HOUR] = mHourSpinner.getEditText();
        mPickerTexts[SeslTimePicker.PICKER_MINUTE] = mMinuteSpinner.getEditText();
        mPickerTexts[SeslTimePicker.PICKER_HOUR]
                .addTextChangedListener(new SeslTextWatcher(2, SeslTimePicker.PICKER_HOUR));
        mPickerTexts[SeslTimePicker.PICKER_MINUTE]
                .addTextChangedListener(new SeslTextWatcher(2, SeslTimePicker.PICKER_MINUTE));
        mPickerTexts[SeslTimePicker.PICKER_HOUR].setOnKeyListener(new SeslKeyListener());
        mPickerTexts[SeslTimePicker.PICKER_MINUTE].setOnKeyListener(new SeslKeyListener());
    }

    private class SeslKeyListener implements View.OnKeyListener {
        public SeslKeyListener() {
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (DEBUG) {
                Log.d(TAG, event.toString());
            }

            if (event.getAction() != KeyEvent.ACTION_UP) {
                return false;
            }

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    Configuration config = mDelegator.getResources().getConfiguration();
                    return config.keyboard != Configuration.KEYBOARD_12KEY;
                case KeyEvent.KEYCODE_TAB:
                    return true;
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    if (isEditTextMode()) {
                        if ((((EditText) v).getImeOptions() & EditorInfo.IME_ACTION_NEXT)
                                == EditorInfo.IME_ACTION_NEXT) {
                            View next = FocusFinder.getInstance()
                                    .findNextFocus(mDelegator, v, View.FOCUS_FORWARD);
                            if (next == null) {
                                return true;
                            }
                            next.requestFocus();
                        } else if ((((EditText) v).getImeOptions() & EditorInfo.IME_ACTION_DONE)
                                == EditorInfo.IME_ACTION_DONE) {
                            updateInputState();
                            setEditTextMode(false);
                        }
                    }
                    return true;
                default:
                    return false;
            }
        }
    }

    public class SeslTextWatcher implements TextWatcher {
        private int changedLen = 0;
        private int mId;
        private int mMaxLen;
        private int mNext;
        private String prevText;

        public SeslTextWatcher(int maxLen, int id) {
            mMaxLen = maxLen;
            mId = id;
            mNext = id + 1 >= 2 ? -1 : id + 1;
        }

        @Override
        public void afterTextChanged(Editable view) {
            if (DEBUG) {
                Log.d(TAG, "aftertextchanged: " + view.toString());
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            if (DEBUG) {
                Log.d(TAG, "beforeTextChanged: " + ((Object) s)
                        + ", " + start + ", " + count + ", " + after);
            }
            prevText = s.toString();
            changedLen = after;
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (DEBUG) {
                Log.d(TAG, "onTextChanged: " + prevText);
                Log.d(TAG, "onTextChanged: " + ((Object) s)
                        + ", " + start + ", " + before + ", " + count);
            }

            String tag = (String) mPickerTexts[mId].getTag();
            if (tag == null || !tag.equals("onClick") && !tag.equals("onLongClick")) {
                switch (mId) {
                    case SeslTimePicker.PICKER_HOUR:
                        if (changedLen != 1) {
                            break;
                        }
                        if (s.length() == mMaxLen) {
                            if (mPickerTexts[mId].isFocused()) {
                                changeFocus();
                            }
                        } else if (s.length() > 0) {
                            final int number = convertDigitCharacterToNumber(s.toString());
                            if ((number > 2 || (number > 1 && !is24Hour()))
                                    && mPickerTexts[mId].isFocused()) {
                                changeFocus();
                            }
                        }
                        break;
                    case SeslTimePicker.PICKER_MINUTE:
                        if (changedLen != 1) {
                            break;
                        }
                        if (s.length() == mMaxLen) {
                            if (mPickerTexts[mId].isFocused()) {
                                changeFocus();
                            }
                        } else if (s.length() > 0) {
                            final int number = convertDigitCharacterToNumber(s.toString());
                            if (number >= 6 && number <= 9) {
                                if (mPickerTexts[mId].isFocused()) {
                                    mIsInvalidMinute = true;
                                    changeFocus();
                                }
                            } else if (!mIsInvalidMinute || !(number == 5 || number == 0)) {
                                mIsInvalidMinute = false;
                                mSkipToChangeInterval = false;
                            } else {
                                mIsInvalidMinute = false;
                                mSkipToChangeInterval = true;
                            }
                        }
                        break;
                    default:
                        if (prevText.length() < s.length()
                                && s.length() == mMaxLen && mPickerTexts[mId].isFocused()) {
                            changeFocus();
                        }
                        break;
                }
            } else {
                mPickerTexts[mId].setTag("");
            }
        }

        private void changeFocus() {
            AccessibilityManager manager
                    = (AccessibilityManager) mContext.getSystemService(Context.ACCESSIBILITY_SERVICE);
            if (manager.isTouchExplorationEnabled()) {
                if (mId == SeslTimePicker.PICKER_HOUR) {
                    setHour(Integer.parseInt(String.valueOf(mPickerTexts[mId].getText())));
                    mPickerTexts[mId].selectAll();
                } else if (mId == SeslTimePicker.PICKER_MINUTE) {
                    setMinute(Integer.parseInt(String.valueOf(mPickerTexts[mId].getText())));
                    mPickerTexts[mId].selectAll();
                }
            } else {
                if (mNext >= 0) {
                    mPickerTexts[mNext].requestFocus();
                    if (mPickerTexts[mId].isFocused()) {
                        mPickerTexts[mId].clearFocus();
                    }
                } else if (mId == SeslTimePicker.PICKER_MINUTE) {
                    setMinute(Integer.parseInt(String.valueOf(mPickerTexts[mId].getText())));
                    mPickerTexts[mId].selectAll();
                }
            }
        }

        private int convertDigitCharacterToNumber(String digitCharacter) {
            int idx = 0;
            for (char val : DIGIT_CHARACTERS) {
                if (digitCharacter.equals(Character.toString(val))) {
                    return idx % 10;
                }
                idx++;
            }
            return -1;
        }
    }
}

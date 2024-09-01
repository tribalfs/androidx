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

import static androidx.annotation.RestrictTo.Scope.LIBRARY;
import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static androidx.picker.util.SeslDatePickerFontUtil.getRegularFontTypeface;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.Window;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.PathInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.ViewAnimator;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.appcompat.widget.TooltipCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.picker.R;
import androidx.reflect.feature.SeslCscFeatureReflector;
import androidx.reflect.feature.SeslFloatingFeatureReflector;
import androidx.reflect.lunarcalendar.SeslFeatureReflector;
import androidx.reflect.lunarcalendar.SeslSolarLunarConverterReflector;
import androidx.reflect.lunarcalendar.SeslSolarLunarTablesReflector;
import androidx.reflect.os.SeslSystemPropertiesReflector;
import androidx.reflect.view.SeslViewReflector;
import androidx.reflect.widget.SeslHoverPopupWindowReflector;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import dalvik.system.PathClassLoader;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Formatter;
import java.util.Locale;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

/** @noinspection unused*/
public class SeslDatePicker extends LinearLayout
        implements SeslSimpleMonthView.OnDayClickListener,
        View.OnClickListener,
        View.OnLongClickListener,
        SeslSimpleMonthView.OnDeactivatedDayClickListener {
    private static final String TAG = "SeslDatePicker";
    private static final boolean DEBUG = false;

    private static final String TAG_CSCFEATURE_CALENDAR_SETCOLOROFDAYS =
            "CscFeature_Calendar_SetColorOfDays";

    private static final int UAE_MCC = 424;
    private static final String UAE_SALES_CODE = "XSG";
    private static final String UAE_WEEK_DAY_STRING_FEATURE = "XXXXXBR";

    private static final int USE_LOCALE = 0;

    public static final int VIEW_TYPE_CALENDAR = 0;
    public static final int VIEW_TYPE_SPINNER = 1;

    public static final int DATE_MODE_NONE = 0;
    public static final int DATE_MODE_START = 1;
    public static final int DATE_MODE_END = 2;
    public static final int DATE_MODE_WEEK_SELECT = 3;

    @IntDef({
            DATE_MODE_NONE,
            DATE_MODE_START,
            DATE_MODE_END,
            DATE_MODE_WEEK_SELECT
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface DateMode {
    }

    private static final int DEFAULT_START_YEAR = 1902;
    private static final int DEFAULT_END_YEAR = 2100;
    private static final int DEFAULT_MONTH_PER_YEAR = 12;

    private static final int NOT_LEAP_MONTH = 0;
    private static final int LEAP_MONTH = 1;

    private static final long DEFAULT_LONG_PRESS_UPDATE_INTERVAL = 300;

    private static final int SIZE_UNSPECIFIED = -1;
    private static final float MAX_FONT_SCALE = 1.2f;

    private static final int MESSAGE_CALENDAR_HEADER_TEXT_VALUE_SET = 1000;
    private static final int MESSAGE_CALENDAR_HEADER_MONTH_BUTTON_SET = 1001;

    private static PackageManager mPackageManager;
    private final ViewAnimator mAnimator;
    private final RelativeLayout mCalendarHeader;
    private final RelativeLayout mCalendarHeaderLayout;
    final TextView mCalendarHeaderText;

    private final LinearLayout mCalendarHeaderTextSpinnerLayout;
    private final View mCalendarHeaderSpinner;

    final CalendarPagerAdapter mCalendarPagerAdapter;
    final ViewPager mCalendarViewPager;
    private ChangeCurrentByOneFromLongPressCommand mChangeCurrentByOneFromLongPressCommand;
    private FrameLayout mContentFrame;
    final Context mContext;
    Calendar mCurrentDate;
    private Locale mCurrentLocale;
    private RelativeLayout mCustomButtonLayout;
    private View mCustomButtonView;
    private final LinearLayout mDatePickerLayout;
    SimpleDateFormat mDayFormatter;
    private final LinearLayout mDayOfTheWeekLayout;
    private final DayOfTheWeekView mDayOfTheWeekView;
    private Window mDialogWindow;
    final Calendar mEndDate;
    private final View mFirstBlankSpace;
    Calendar mMaxDate;
    Calendar mMinDate;
    String mMonthViewColor = null;
    final ImageButton mNextButton;
    private OnDateChangedListener mOnDateChangedListener;
    private OnViewTypeChangedListener mOnViewTypeChangedListener;
    PathClassLoader mPathClassLoader = null;
    final ImageButton mPrevButton;
    private final View mSecondBlankSpace;
    private Object mSolarLunarConverter;
    private Object mSolarLunarTables;
    private final SeslDatePickerSpinnerLayout mSpinnerLayout;
    final Calendar mStartDate;
    final Calendar mTempDate;
    private final Calendar mTempMinMaxDate;
    private ValidationCallback mValidationCallback;

    private int mBackgroundBorderlessResId = -1;
    private int mCalendarHeaderLayoutHeight;
    private final int mCalendarViewMargin;
    private int mCalendarViewPagerHeight;
    private int mCalendarViewPagerWidth;
    int mCurrentPosition;
    int mCurrentViewType = -1;
    private int mDatePickerHeight;
    int mDayOfTheWeekLayoutHeight;
    int mDayOfTheWeekLayoutWidth;
    private int mDayOfWeekStart;
    private int mDialogPaddingVertical;
    private int mFirstBlankSpaceHeight;
    private int mFirstDayOfWeek = 0;
    int mIsLeapEndMonth;
    int mIsLeapStartMonth;
    int mLunarCurrentDay;
    int mLunarCurrentMonth;
    int mLunarCurrentYear;
    int mLunarEndDay;
    int mLunarEndMonth;
    int mLunarEndYear;
    int mLunarStartDay;
    int mLunarStartMonth;
    int mLunarStartYear;
    private int mMeasureSpecHeight;
    int mMode = DATE_MODE_NONE;
    int mNumDays;
    private int mOldCalendarViewPagerWidth;
    private int mOldSelectedDay = -1;
    int mPadding;
    int mPositionCount;
    private int mSecondBlankSpaceHeight;
    private int[] mTotalMonthCountWithLeap;
    int mWeekStart;

    private boolean mIsCalledFromDeactivatedDayClick;
    boolean mIsConfigurationChanged = false;
    private boolean mIsCustomButtonSeparate = false;
    private boolean mIsEnabled = true;
    private boolean mIsFarsiLanguage;
    private boolean mIsFirstMeasure = true;
    boolean mIsFromSetLunar = false;
    private final boolean mIsInDialog;
    boolean mIsLeapMonth = false;
    boolean mIsLunar = false;
    boolean mIsLunarSupported = false;
    boolean mIsRTL;
    private boolean mIsSimplifiedChinese;
    private boolean mIsWeekRangeSet;
    private boolean mLunarChanged = false;

    private final boolean useLegacyLayout = Build.VERSION.SDK_INT < 23;

    //sesl6
    private final ObjectAnimator mAntiClockwiseAnim;
    private final ObjectAnimator mClockwiseAnim;
    private static final PathInterpolator CALENDAR_HEADER_SPINNER_INTERPOLATOR
            = new PathInterpolator(0.22f, 0.25f, 0.0f, 1.0f);

    private boolean mIsCalendarViewDisabled = false;
    //sesl6

    private final LinearLayout.LayoutParams mLpPreAllocated = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

    private final View.OnClickListener mCalendarHeaderClickListener = v -> {
        setCurrentViewType((mCurrentViewType + VIEW_TYPE_SPINNER) % 2);
        if (!useLegacyLayout) startCalendarHeaderSpinnerAnimation();

    };

    private  void startCalendarHeaderSpinnerAnimation() {
        if (this.mCurrentViewType == 0) {
            if (mAntiClockwiseAnim.isRunning()) {
                mAntiClockwiseAnim.cancel();
            }
            mClockwiseAnim.start();
            return;
        }
        if (mClockwiseAnim.isRunning()) {
            mClockwiseAnim.cancel();
        }
        mAntiClockwiseAnim.start();
    }


    final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case MESSAGE_CALENDAR_HEADER_TEXT_VALUE_SET: //1000
                    if (mTempDate.get(Calendar.YEAR) <= getMaxYear()
                            && mTempDate.get(Calendar.YEAR) >= getMinYear()) {
                        String monthAndYearString = getMonthAndYearString(mTempDate);
                        mCalendarHeaderText.setText(monthAndYearString);
                        mCalendarHeaderText.setContentDescription(monthAndYearString
                                + ", "
                                + mContext.getString(mCurrentViewType == VIEW_TYPE_CALENDAR ?
                                R.string.sesl_date_picker_switch_to_month_day_year_view_description
                                : R.string.sesl_date_picker_switch_to_calendar_description));
                    }
                    break;
                case MESSAGE_CALENDAR_HEADER_MONTH_BUTTON_SET://1001
                    if (mCurrentViewType == VIEW_TYPE_SPINNER) {
                        setPrevButtonProperties(0.0f, false);
                        setNextButtonProperties(0.0f, false);
                        final int TYPE_NONE = SeslHoverPopupWindowReflector.getField_TYPE_NONE();
                        if (TYPE_NONE != -1) {
                            SeslViewReflector.semSetHoverPopupType(mPrevButton, TYPE_NONE);
                            SeslViewReflector.semSetHoverPopupType(mNextButton, TYPE_NONE);
                        }
                    } else {
                        final int TYPE_TOOLTIP =
                                SeslHoverPopupWindowReflector.getField_TYPE_TOOLTIP();
                        if (TYPE_TOOLTIP != -1) {
                            SeslViewReflector.semSetHoverPopupType(mPrevButton, TYPE_TOOLTIP);
                            SeslViewReflector.semSetHoverPopupType(mNextButton, TYPE_TOOLTIP);
                        }
                        Resources res = getResources();
                        TooltipCompat.setTooltipText(mPrevButton,
                                res.getString(R.string.sesl_date_picker_decrement_month));
                        TooltipCompat.setTooltipText(mNextButton,
                                res.getString(R.string.sesl_date_picker_increment_month));
                        if (mCurrentPosition > 0 && mCurrentPosition < mPositionCount - 1) {
                            setPrevButtonProperties(1.0f, true);
                            setNextButtonProperties(1.0f, true);
                        } else if (mPositionCount == 1) {
                            setPrevButtonProperties(0.4f, false);
                            setNextButtonProperties(0.4f, false);
                            removeAllCallbacks();
                        } else if (mCurrentPosition == 0) {
                            setPrevButtonProperties(0.4f, false);
                            setNextButtonProperties(1.0f, true);
                            removeAllCallbacks();
                        } else if (mCurrentPosition == mPositionCount - 1) {
                            setPrevButtonProperties(1.0f, true);
                            setNextButtonProperties(0.4f, false);
                            removeAllCallbacks();
                        }
                    }
                    break;
            }
        }
    };

    public interface OnDateChangedListener {
        void onDateChanged(@NonNull SeslDatePicker view, int year, int month, int day);
    }

    public interface OnEditTextModeChangedListener {
        void onEditTextModeChanged(@NonNull SeslDatePicker view, boolean editTextMode);
    }

    public interface OnViewTypeChangedListener {
        void onViewTypeChanged(@NonNull SeslDatePicker view);
    }

    @RestrictTo(LIBRARY)
    public interface ValidationCallback {
        void onValidationChanged(boolean valid);
    }

    void setPrevButtonProperties(float alpha, boolean enabled) {
        mPrevButton.setAlpha(alpha);
        if (enabled) {
            mPrevButton.setBackgroundResource(mBackgroundBorderlessResId);
            mPrevButton.setEnabled(true);
            mPrevButton.setFocusable(true);
        } else {
            mPrevButton.setBackground(null);
            mPrevButton.setEnabled(false);
            mPrevButton.setFocusable(false);
        }
    }

    void setNextButtonProperties(float alpha, boolean enabled) {
        mNextButton.setAlpha(alpha);
        if (enabled) {
            mNextButton.setBackgroundResource(mBackgroundBorderlessResId);
            mNextButton.setEnabled(true);
            mNextButton.setFocusable(true);
        } else {
            mNextButton.setBackground(null);
            mNextButton.setEnabled(false);
            mNextButton.setFocusable(false);
        }
    }

    public SeslDatePicker(@NonNull Context context) {
        this(context, null);
    }

    public SeslDatePicker(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, android.R.attr.datePickerStyle);
    }

    public SeslDatePicker(@NonNull Context context, @Nullable AttributeSet attrs,
            int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    @SuppressLint("ClickableViewAccessibility")
    public SeslDatePicker(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        mContext = context;
        mCurrentLocale = Locale.getDefault();
        mIsRTL = isRTL();
        mIsFarsiLanguage = isFarsiLanguage();
        mIsSimplifiedChinese = isSimplifiedChinese();
        if (mIsSimplifiedChinese) {
            mDayFormatter = new SimpleDateFormat("EEEEE", mCurrentLocale);
        } else {
            mDayFormatter = new SimpleDateFormat("EEE", mCurrentLocale);
        }
        mMinDate = getCalendarForLocale(mMinDate, mCurrentLocale);
        mMaxDate = getCalendarForLocale(mMaxDate, mCurrentLocale);
        mTempMinMaxDate = getCalendarForLocale(mMaxDate, mCurrentLocale);
        mCurrentDate = getCalendarForLocale(mCurrentDate, mCurrentLocale);
        mTempDate = getCalendarForLocale(mCurrentDate, mCurrentLocale);

        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.DatePicker, defStyleAttr, defStyleRes);
        mMinDate.set(a.getInt(R.styleable.DatePicker_android_startYear, DEFAULT_START_YEAR),
                Calendar.JANUARY, 1);
        mMaxDate.set(a.getInt(R.styleable.DatePicker_android_endYear, DEFAULT_END_YEAR),
                Calendar.DECEMBER, 31);

        LayoutInflater inflater =
                (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        inflater.inflate(useLegacyLayout ? R.layout.sesl_date_picker_legacy :
                        R.layout.sesl_date_picker,
                this, true);

        int firstDayOfWeek = a.getInt(R.styleable.DatePicker_android_firstDayOfWeek, 0);
        if (firstDayOfWeek != 0) {
            setFirstDayOfWeek(firstDayOfWeek);
        }

        a.recycle();

        mMonthViewColor = getMonthViewColorStringForSpecific();

        TypedArray seslArray = mContext.obtainStyledAttributes(attrs, R.styleable.DatePicker,
                defStyleAttr, defStyleRes);
        mDayOfTheWeekView = new DayOfTheWeekView(mContext, seslArray);

        final Resources res = getResources();

        final int calendarHeaderTextColor =
                seslArray.getColor(R.styleable.DatePicker_headerTextColor,
                        res.getColor(R.color.sesl_date_picker_header_text_color_light));
        final int btnTintColor = seslArray.getColor(R.styleable.DatePicker_buttonTintColor,
                res.getColor(R.color.sesl_date_picker_button_tint_color_light));

        seslArray.recycle();

        mCalendarPagerAdapter = new CalendarPagerAdapter();
        mCalendarViewPager = findViewById(R.id.sesl_date_picker_calendar);
        mCalendarViewPager.setAdapter(mCalendarPagerAdapter);
        mCalendarViewPager.setOnPageChangeListener(new CalendarPageChangeListener());
        mCalendarViewPager.seslSetSupportedMouseWheelEvent(true);
        mCalendarViewPager.canSupportLayoutDirectionForDatePicker(true);

        mPadding = res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_view_padding);
        mCalendarHeader = findViewById(R.id.sesl_date_picker_calendar_header);

        mCalendarHeaderText = findViewById(R.id.sesl_date_picker_calendar_header_text);
        mCalendarHeaderText.setTextColor(calendarHeaderTextColor);

        mStartDate = getCalendarForLocale(mCurrentDate, mCurrentLocale);
        mEndDate = getCalendarForLocale(mCurrentDate, mCurrentLocale);

        mAnimator = findViewById(R.id.sesl_date_picker_view_animator);

        mSpinnerLayout = findViewById(R.id.sesl_date_picker_spinner_view);
        mSpinnerLayout.setOnSpinnerDateChangedListener(this,
                (view, year, month, day) -> {
                    mCurrentDate.set(Calendar.YEAR, year);
                    mCurrentDate.set(Calendar.MONTH, month);
                    mCurrentDate.set(Calendar.DAY_OF_MONTH, day);
                    if (mIsLunar) {
                        mLunarCurrentYear = year;
                        mLunarCurrentMonth = month;
                        mLunarCurrentDay = day;
                    }

                    switch (mMode) {
                        case DATE_MODE_START:
                            if (mStartDate.compareTo(mEndDate) == 0
                                    || mCurrentDate.compareTo(mEndDate) > 0) {
                                clearCalendar(mEndDate, year, month, day);
                            }
                            clearCalendar(mStartDate, year, month, day);
                            if (mIsLunar) {
                                if (mStartDate.compareTo(mEndDate) == 0
                                        || mCurrentDate.compareTo(mEndDate) > 0) {
                                    mLunarEndYear = year;
                                    mLunarEndMonth = month;
                                    mLunarEndDay = day;
                                    mIsLeapEndMonth = NOT_LEAP_MONTH;
                                }
                                mLunarStartYear = year;
                                mLunarStartMonth = month;
                                mLunarStartDay = day;
                                mIsLeapStartMonth = NOT_LEAP_MONTH;
                            }
                            break;
                        case DATE_MODE_END:
                            if (mCurrentDate.compareTo(mStartDate) < 0) {
                                clearCalendar(mStartDate, year, month, day);
                            }
                            clearCalendar(mEndDate, year, month, day);
                            if (mIsLunar) {
                                if (mCurrentDate.compareTo(mStartDate) < 0) {
                                    mLunarStartYear = year;
                                    mLunarStartMonth = month;
                                    mLunarStartDay = day;
                                    mIsLeapStartMonth = NOT_LEAP_MONTH;
                                }
                                mLunarEndYear = year;
                                mLunarEndMonth = month;
                                mLunarEndDay = day;
                                mIsLeapEndMonth = NOT_LEAP_MONTH;
                            }
                            break;
                        default:
                            clearCalendar(mStartDate, year, month, day);
                            clearCalendar(mEndDate, year, month, day);
                            if (mIsLunar) {
                                mLunarStartYear = year;
                                mLunarStartMonth = month;
                                mLunarStartDay = day;
                                mIsLeapStartMonth = NOT_LEAP_MONTH;
                                mLunarEndYear = year;
                                mLunarEndMonth = month;
                                mLunarEndDay = day;
                                mIsLeapEndMonth = NOT_LEAP_MONTH;
                                mIsLeapMonth = false;
                            }
                            break;
                    }

                    onValidationChanged(!mStartDate.after(mEndDate));
                    updateSimpleMonthView(false);
                    if (mMode == DATE_MODE_WEEK_SELECT && mIsWeekRangeSet) {
                        updateStartEndDateRange(getDayOffset(), year, month, day);
                    }
                    onDateChanged();
                });

        mCurrentViewType = VIEW_TYPE_CALENDAR;

        //Sesl6
        if (useLegacyLayout){
            mCalendarHeaderText.setOnClickListener(mCalendarHeaderClickListener);
            mCalendarHeaderText.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && mCurrentViewType == VIEW_TYPE_SPINNER) {
                    setEditTextMode(false);
                }
            });
            mCalendarHeaderText.setFocusable(true);
            mCalendarHeaderText.setNextFocusRightId(
                    R.id.sesl_date_picker_calendar_header_next_button);
            mCalendarHeaderText.setNextFocusLeftId(
                    R.id.sesl_date_picker_calendar_header_prev_button);

            mCalendarHeaderTextSpinnerLayout = null;
            mCalendarHeaderSpinner = null;
            mClockwiseAnim = null;
            mAntiClockwiseAnim = null;
        }else {
            mCalendarHeaderTextSpinnerLayout =
                    findViewById(R.id.sesl_date_picker_calendar_header_text_spinner_layout);
            mCalendarHeaderSpinner =
                    mCalendarHeaderTextSpinnerLayout.findViewById(R.id.sesl_date_picker_calendar_header_spinner);

            mCalendarHeaderTextSpinnerLayout.setOnClickListener(mCalendarHeaderClickListener);
            mCalendarHeaderTextSpinnerLayout.setOnFocusChangeListener((v, hasFocus) -> {
                if (hasFocus && mCurrentViewType == VIEW_TYPE_SPINNER) {
                    setEditTextMode(false);
                }
            });
            mCalendarHeaderTextSpinnerLayout.setFocusable(true);
            mCalendarHeaderTextSpinnerLayout.setNextFocusRightId(
                    R.id.sesl_date_picker_calendar_header_next_button);
            mCalendarHeaderTextSpinnerLayout.setNextFocusLeftId(
                    R.id.sesl_date_picker_calendar_header_prev_button);

            mClockwiseAnim = ObjectAnimator.ofFloat(mCalendarHeaderSpinner, View.ROTATION,
                    -180.0f, 0.0f);
            mClockwiseAnim.setDuration(350L);
            mClockwiseAnim.setInterpolator(CALENDAR_HEADER_SPINNER_INTERPOLATOR);

            mAntiClockwiseAnim = ObjectAnimator.ofFloat(mCalendarHeaderSpinner, View.ROTATION,
                    0.0f, -180.0f);
            mAntiClockwiseAnim.setDuration(350L);
            mAntiClockwiseAnim.setInterpolator(CALENDAR_HEADER_SPINNER_INTERPOLATOR);
        }
        //sesl6

        mDayOfTheWeekLayoutHeight =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_day_height);
        checkMaxFontSize();
        mCalendarViewPagerWidth =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_view_width);
        mCalendarViewMargin =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_view_margin);
        mDayOfTheWeekLayoutWidth =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_view_width);

        mDayOfTheWeekLayout = findViewById(R.id.sesl_date_picker_day_of_the_week);
        mDayOfTheWeekLayout.addView(mDayOfTheWeekView);
        mDatePickerLayout = findViewById(R.id.sesl_date_picker_layout);
        mCalendarHeaderLayout = findViewById(R.id.sesl_date_picker_calendar_header_layout);

        if (mIsRTL) {
            mPrevButton = findViewById(R.id.sesl_date_picker_calendar_header_next_button);
            mNextButton = findViewById(R.id.sesl_date_picker_calendar_header_prev_button);
            mPrevButton.setContentDescription(mContext.getString(R.string.sesl_date_picker_decrement_month));
            mNextButton.setContentDescription(mContext.getString(R.string.sesl_date_picker_increment_month));
        } else {
            mPrevButton = findViewById(R.id.sesl_date_picker_calendar_header_prev_button);
            mNextButton = findViewById(R.id.sesl_date_picker_calendar_header_next_button);
        }

        mPrevButton.setOnClickListener(this);
        mNextButton.setOnClickListener(this);
        mPrevButton.setOnLongClickListener(this);
        mNextButton.setOnLongClickListener(this);

        OnTouchListener mMonthBtnTouchListener = (v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                removeAllCallbacks();
            }
            return false;
        };
        mPrevButton.setOnTouchListener(mMonthBtnTouchListener);
        mNextButton.setOnTouchListener(mMonthBtnTouchListener);

        OnKeyListener mMonthBtnKeyListener = (v, keyCode, event) -> {
            if (mIsRTL) {
                mIsConfigurationChanged = false;
            }
            if (event.getAction() == KeyEvent.ACTION_UP
                    || event.getAction() == 3 /*?*/) {
                removeAllCallbacks();
            }
            return false;
        };
        mPrevButton.setOnKeyListener(mMonthBtnKeyListener);
        mNextButton.setOnKeyListener(mMonthBtnKeyListener);

        OnFocusChangeListener btnFocusChangeListener = (v, hasFocus) -> {
            if (!hasFocus) {
                removeAllCallbacks();
            }
        };
        mPrevButton.setOnFocusChangeListener(btnFocusChangeListener);
        mNextButton.setOnFocusChangeListener(btnFocusChangeListener);
        mPrevButton.setColorFilter(btnTintColor);
        mNextButton.setColorFilter(btnTintColor);

        TypedValue outValue = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless,
                outValue, true);
        mBackgroundBorderlessResId = outValue.resourceId;

        mCalendarHeaderLayoutHeight =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_header_height);
        mCalendarViewPagerHeight =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_view_height);
        mOldCalendarViewPagerWidth = mCalendarViewPagerWidth;
        mCalendarHeaderText.setFocusable(true);

        mPrevButton.setNextFocusRightId(R.id.sesl_date_picker_calendar_header_text);
        mNextButton.setNextFocusLeftId(R.id.sesl_date_picker_calendar_header_text);
        mCalendarHeaderText.setNextFocusRightId(R.id.sesl_date_picker_calendar_header_next_button);
        mCalendarHeaderText.setNextFocusLeftId(R.id.sesl_date_picker_calendar_header_prev_button);

        mFirstBlankSpace = findViewById(R.id.sesl_date_picker_between_header_and_weekend);
        mFirstBlankSpaceHeight =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_gap_between_header_and_weekend);
        mSecondBlankSpace = findViewById(R.id.sesl_date_picker_between_weekend_and_calender);
        mSecondBlankSpaceHeight =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_gap_between_weekend_and_calender);

        mDatePickerHeight = mCalendarHeaderLayoutHeight + mFirstBlankSpaceHeight
                + mDayOfTheWeekLayoutHeight + mSecondBlankSpaceHeight + mCalendarViewPagerHeight;

        updateSimpleMonthView(true);

        TypedValue outValue2 = new TypedValue();
        mContext.getTheme().resolveAttribute(android.R.attr.windowIsFloating,
                outValue2, true);
        mIsInDialog = outValue2.data != 0;

        Activity activity = scanForActivity(mContext);
        if (activity != null && !mIsInDialog) {
            mContentFrame = activity.getWindow().getDecorView().findViewById(android.R.id.content);
        } else if (activity == null) {
            Log.e(TAG, "Cannot get window of this context. context:" + mContext);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
    }

    @RestrictTo(LIBRARY)
    public void setValidationCallback(@Nullable ValidationCallback callback) {
        mValidationCallback = callback;
    }

    @RestrictTo(LIBRARY)
    protected void onValidationChanged(boolean valid) {
        if (mValidationCallback != null) {
            mValidationCallback.onValidationChanged(valid);
        }
    }

    private Calendar getCalendarForLocale(Calendar oldCalendar, Locale locale) {
        if (oldCalendar == null) {
            return Calendar.getInstance(locale);
        } else {
            final long currentTimeMillis = oldCalendar.getTimeInMillis();
            Calendar newCalendar = Calendar.getInstance(locale);
            newCalendar.setTimeInMillis(currentTimeMillis);
            return newCalendar;
        }
    }

    public void setOnViewTypeChangedListener(@Nullable OnViewTypeChangedListener listener) {
        mOnViewTypeChangedListener = listener;
    }

    public void init(int year, int monthOfYear, int dayOfMonth,
            @Nullable OnDateChangedListener onDateChangedListener) {
        mCurrentDate.set(Calendar.YEAR, year);
        mCurrentDate.set(Calendar.MONTH, monthOfYear);
        mCurrentDate.set(Calendar.DAY_OF_MONTH, dayOfMonth);

        if (mIsLunar) {
            mLunarCurrentYear = year;
            mLunarCurrentMonth = monthOfYear;
            mLunarCurrentDay = dayOfMonth;
        }

        if (mCurrentDate.before(mMinDate)) {
            mCurrentDate = getCalendarForLocale(mMinDate, mCurrentLocale);
        }
        if (mCurrentDate.after(mMaxDate)) {
            mCurrentDate = getCalendarForLocale(mMaxDate, mCurrentLocale);
        }

        mOnDateChangedListener = onDateChangedListener;

        updateSimpleMonthView(true);
        onDateChanged();

        mSpinnerLayout.setMinDate(mMinDate.getTimeInMillis());
        mSpinnerLayout.setMaxDate(mMaxDate.getTimeInMillis());

        if (mCurrentViewType == VIEW_TYPE_CALENDAR) {
            mSpinnerLayout.setVisibility(INVISIBLE);
            mSpinnerLayout.setEnabled(false);
        }

        clearCalendar(mStartDate, year, monthOfYear, dayOfMonth);
        clearCalendar(mEndDate, year, monthOfYear, dayOfMonth);

        if (mIsLunar) {
            mLunarStartYear = year;
            mLunarStartMonth = monthOfYear;
            mLunarStartDay = dayOfMonth;
            mLunarEndYear = year;
            mLunarEndMonth = monthOfYear;
            mLunarEndDay = dayOfMonth;
        }
    }

    private void clearCalendar(Calendar calendar, int year, int monthOfYear, int dayOfMonth) {
        calendar.clear();
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, monthOfYear);
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
    }

    public void updateDate(int year, int month, int dayOfMonth) {
        mTempDate.set(Calendar.YEAR, year);
        mTempDate.set(Calendar.MONTH, month);
        mTempDate.set(Calendar.DAY_OF_MONTH,
                Math.min(dayOfMonth, mTempDate.getActualMaximum(Calendar.DAY_OF_MONTH)));
        mCurrentDate = getCalendarForLocale(mTempDate, mCurrentLocale);
        if (mIsLunar) {
            mLunarCurrentYear = year;
            mLunarCurrentMonth = month;
            mLunarCurrentDay = dayOfMonth;
        }

        switch (mMode) {
            case DATE_MODE_START:
                if (mStartDate.compareTo(mEndDate) == 0
                        || mCurrentDate.compareTo(mEndDate) > 0) {
                    clearCalendar(mEndDate, year, month, dayOfMonth);
                }
                clearCalendar(mStartDate, year, month, dayOfMonth);
                if (mIsLunar) {
                    if (mStartDate.compareTo(mEndDate) == 0
                            || mCurrentDate.compareTo(mEndDate) > 0) {
                        mLunarEndYear = year;
                        mLunarEndMonth = month;
                        mLunarEndDay = dayOfMonth;
                    }
                    mLunarStartYear = year;
                    mLunarStartMonth = month;
                    mLunarStartDay = dayOfMonth;
                }
                break;
            case DATE_MODE_END:
                if (mCurrentDate.compareTo(mStartDate) < 0) {
                    clearCalendar(mStartDate, year, month, dayOfMonth);
                }
                clearCalendar(mEndDate, year, month, dayOfMonth);
                if (mIsLunar) {
                    if (mCurrentDate.compareTo(mStartDate) < 0) {
                        mLunarStartYear = year;
                        mLunarStartMonth = month;
                        mLunarStartDay = dayOfMonth;
                    }
                    mLunarEndYear = year;
                    mLunarEndMonth = month;
                    mLunarEndDay = dayOfMonth;
                }
                break;
            default:
                clearCalendar(mStartDate, year, month, dayOfMonth);
                clearCalendar(mEndDate, year, month, dayOfMonth);
                if (mIsLunar) {
                    mLunarStartYear = year;
                    mLunarStartMonth = month;
                    mLunarStartDay = dayOfMonth;
                    mLunarEndYear = year;
                    mLunarEndMonth = month;
                    mLunarEndDay = dayOfMonth;
                }
                break;
        }

        updateSimpleMonthView(true);
        onDateChanged();

        SeslSimpleMonthView view = mCalendarPagerAdapter.views.get(mCurrentPosition);
        if (view != null) {
            final int enabledDayRangeStart = (getMinMonth() == month && getMinYear() == year) ?
                    getMinDay() : 1;
            final int enabledDayRangeEnd = (getMaxMonth() == month && getMaxYear() == year) ?
                    getMaxDay() : 31;
            if (mIsLunarSupported) {
                view.setLunar(mIsLunar, mIsLeapMonth, mPathClassLoader);
            }
            if (mMode == DATE_MODE_WEEK_SELECT && mIsWeekRangeSet) {
                updateStartEndDateRange(getDayOffset(), year, month, dayOfMonth);
            }

            int startYear, startMonth, startDay, endYear, endMonth, endDay;
            if (mIsLunar) {
                startYear = mLunarStartYear;
                startMonth = mLunarStartMonth;
                startDay = mLunarStartDay;
                endYear = mLunarEndYear;
                endMonth = mLunarEndMonth;
                endDay = mLunarEndDay;
            } else {
                startYear = mStartDate.get(Calendar.YEAR);
                startMonth = mStartDate.get(Calendar.MONTH);
                startDay = mStartDate.get(Calendar.DAY_OF_MONTH);
                endYear = mEndDate.get(Calendar.YEAR);
                endMonth = mEndDate.get(Calendar.MONTH);
                endDay = mEndDate.get(Calendar.DAY_OF_MONTH);
            }
            view.setMonthParams(dayOfMonth, month, year,
                    getFirstDayOfWeek(), enabledDayRangeStart, enabledDayRangeEnd,
                    mMinDate, mMaxDate, startYear, startMonth, startDay, mIsLeapStartMonth,
                    endYear, endMonth, endDay, mIsLeapEndMonth, mMode);
            view.invalidate();

            if (!mIsLunar) {
                final int prevPos = mCurrentPosition - 1;
                if (prevPos >= 0) {
                    SeslSimpleMonthView prevMonth = mCalendarPagerAdapter.views.get(prevPos);
                    if (prevMonth != null) {
                        prevMonth.setStartDate(mStartDate, mIsLeapStartMonth);
                        prevMonth.setEndDate(mEndDate, mIsLeapEndMonth);
                    }
                }

                final int nextPos = mCurrentPosition + 1;
                if (nextPos < mPositionCount) {
                    SeslSimpleMonthView nextMonth = mCalendarPagerAdapter.views.get(nextPos);
                    if (nextMonth != null) {
                        nextMonth.setStartDate(mStartDate, mIsLeapStartMonth);
                        nextMonth.setEndDate(mEndDate, mIsLeapEndMonth);
                    }
                }
            }
        }

        if (mSpinnerLayout != null) {
            mSpinnerLayout.updateDate(year, month, dayOfMonth);
        }
    }

    private void onDateChanged() {
        if (mOnDateChangedListener != null) {
            int year = mCurrentDate.get(Calendar.YEAR);
            int monthOfYear = mCurrentDate.get(Calendar.MONTH);
            int dayOfMonth = mCurrentDate.get(Calendar.DAY_OF_MONTH);
            if (mIsLunar) {
                year = mLunarCurrentYear;
                monthOfYear = mLunarCurrentMonth;
                dayOfMonth = mLunarCurrentDay;
            }
            mOnDateChangedListener.onDateChanged(this, year, monthOfYear, dayOfMonth);
        }
    }

    public int getYear() {
        if (mIsLunar) {
            return mLunarCurrentYear;
        }
        return mCurrentDate.get(Calendar.YEAR);
    }

    public int getMonth() {
        if (mIsLunar) {
            return mLunarCurrentMonth;
        }
        return mCurrentDate.get(Calendar.MONTH);
    }

    public int getDayOfMonth() {
        if (mIsLunar) {
            return mLunarCurrentDay;
        }
        return mCurrentDate.get(Calendar.DAY_OF_MONTH);
    }

    public long getMinDate() {
        return mMinDate.getTimeInMillis();
    }

    public void setMinDate(long minDate) {
        mTempMinMaxDate.setTimeInMillis(minDate);
        if (mTempMinMaxDate.get(Calendar.YEAR) != mMinDate.get(Calendar.YEAR)
                || mTempMinMaxDate.get(Calendar.DAY_OF_YEAR) == mMinDate.get(Calendar.DAY_OF_YEAR)) {
            if (mIsLunar) {
                setTotalMonthCountWithLeap();
            }
            if (mCurrentDate.before(mTempMinMaxDate)) {
                mCurrentDate.setTimeInMillis(minDate);
                onDateChanged();
            }
            mMinDate.setTimeInMillis(minDate);
            mSpinnerLayout.setMinDate(mMinDate.getTimeInMillis());
            mCalendarPagerAdapter.notifyDataSetChanged();
            updateSimpleMonthView(false);
        }
    }

    public long getMaxDate() {
        return mMaxDate.getTimeInMillis();
    }

    public void setMaxDate(long maxDate) {
        mTempMinMaxDate.setTimeInMillis(maxDate);
        if (mTempMinMaxDate.get(Calendar.YEAR) != mMaxDate.get(Calendar.YEAR)
                || mTempMinMaxDate.get(Calendar.DAY_OF_YEAR) == mMaxDate.get(Calendar.DAY_OF_YEAR)) {
            if (mIsLunar) {
                setTotalMonthCountWithLeap();
            }
            if (mCurrentDate.after(mTempMinMaxDate)) {
                mCurrentDate.setTimeInMillis(maxDate);
                onDateChanged();
            }
            mMaxDate.setTimeInMillis(maxDate);
            mSpinnerLayout.setMaxDate(mMaxDate.getTimeInMillis());
            mCalendarPagerAdapter.notifyDataSetChanged();
            updateSimpleMonthView(false);
        }
    }

    @RestrictTo(LIBRARY)
    int getMinYear() {
        return mMinDate.get(Calendar.YEAR);
    }

    @RestrictTo(LIBRARY)
    int getMaxYear() {
        return mMaxDate.get(Calendar.YEAR);
    }

    @RestrictTo(LIBRARY)
    int getMinMonth() {
        return mMinDate.get(Calendar.MONTH);
    }

    @RestrictTo(LIBRARY)
    int getMaxMonth() {
        return mMaxDate.get(Calendar.MONTH);
    }

    @RestrictTo(LIBRARY)
    int getMinDay() {
        return mMinDate.get(Calendar.DAY_OF_MONTH);
    }

    @RestrictTo(LIBRARY)
    int getMaxDay() {
        return mMaxDate.get(Calendar.DAY_OF_MONTH);
    }

    @Override
    public void setEnabled(boolean enabled) {
        if (isEnabled() != enabled) {
            super.setEnabled(enabled);
            mIsEnabled = enabled;
        }
    }

    @Override
    public boolean isEnabled() {
        return mIsEnabled;
    }

    public void setCalendarViewDisabled(@NonNull Boolean disable){
        mIsCalendarViewDisabled = disable;
        requestLayout();

    }

    @NonNull
    public Boolean getCalendarViewDisabled(){
        return mIsCalendarViewDisabled;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        super.onPopulateAccessibilityEvent(event);
        event.getText().add(getFormattedCurrentDate());
        return true;
    }

    private String getFormattedCurrentDate() {
        return DateUtils.formatDateTime(mContext,
                mCurrentDate.getTimeInMillis(),
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR);
    }

    @Override
    protected void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mIsRTL = isRTL();
        mIsFarsiLanguage = isFarsiLanguage();

        Locale newLocale;
        if (Build.VERSION.SDK_INT >= 24) {
            newLocale = newConfig.getLocales().get(USE_LOCALE);
        } else {
            newLocale = newConfig.locale;
        }
        if (!mCurrentLocale.equals(newLocale)) {
            mCurrentLocale = newLocale;
            mIsSimplifiedChinese = isSimplifiedChinese();
            if (mIsSimplifiedChinese) {
                mDayFormatter = new SimpleDateFormat("EEEEE", newLocale);
            } else {
                mDayFormatter = new SimpleDateFormat("EEE", newLocale);
            }
        }

        final Resources res = mContext.getResources();
        mDatePickerLayout.setGravity(Gravity.CENTER_HORIZONTAL);
        mIsFirstMeasure = true;
        mCalendarHeaderLayoutHeight =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_header_height);
        mCalendarViewPagerHeight =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_view_height);
        mDayOfTheWeekLayoutHeight =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_day_height);
        mFirstBlankSpaceHeight =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_gap_between_header_and_weekend);
        mSecondBlankSpaceHeight =
                res.getDimensionPixelOffset(R.dimen.sesl_date_picker_gap_between_weekend_and_calender);
        mDatePickerHeight = mCalendarHeaderLayoutHeight + mFirstBlankSpaceHeight
                + mDayOfTheWeekLayoutHeight + mSecondBlankSpaceHeight + mCalendarViewPagerHeight;

        if (mIsRTL) {
            mIsConfigurationChanged = true;
        }

        checkMaxFontSize();
    }

    public void setFirstDayOfWeek(int firstDayOfWeek) {
        if (firstDayOfWeek < 1 || firstDayOfWeek > 7) {
            throw new IllegalArgumentException("firstDayOfWeek must be between 1 and 7");
        }
        mFirstDayOfWeek = firstDayOfWeek;
    }

    public int getFirstDayOfWeek() {
        return mFirstDayOfWeek != 0 ? mFirstDayOfWeek : mCurrentDate.getFirstDayOfWeek();
    }

    @Override
    protected void dispatchRestoreInstanceState(SparseArray<Parcelable> container) {
        dispatchThawSelfOnly(container);
    }

    @NonNull
    @Override
    protected Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        int year = mCurrentDate.get(Calendar.YEAR);
        int month = mCurrentDate.get(Calendar.MONTH);
        int day = mCurrentDate.get(Calendar.DAY_OF_MONTH);
        if (mIsLunar) {
            year = mLunarCurrentYear;
            month = mLunarCurrentMonth;
            day = mLunarCurrentDay;
        }
        return new SavedState(superState, year, month, day,
                mMinDate.getTimeInMillis(), mMaxDate.getTimeInMillis());
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(((View.BaseSavedState) state).getSuperState());
        SavedState ss = (SavedState) state;
        mCurrentDate.set(ss.getSelectedYear(), ss.getSelectedMonth(), ss.getSelectedDay());
        if (mIsLunar) {
            mLunarCurrentYear = ss.getSelectedYear();
            mLunarCurrentMonth = ss.getSelectedMonth();
            mLunarCurrentDay = ss.getSelectedDay();
        }
        mMinDate.setTimeInMillis(ss.getMinDate());
        mMaxDate.setTimeInMillis(ss.getMaxDate());
    }

    @RestrictTo(LIBRARY)
    void onDayOfMonthSelected(int year, int month, int day) {
        mCurrentDate.set(Calendar.YEAR, year);
        mCurrentDate.set(Calendar.MONTH, month);
        mCurrentDate.set(Calendar.DAY_OF_MONTH, day);

        if (mIsLunar) {
            mLunarCurrentYear = year;
            mLunarCurrentMonth = month;
            mLunarCurrentDay = day;
        }

        Message msg = mHandler.obtainMessage();
        msg.what = MESSAGE_CALENDAR_HEADER_TEXT_VALUE_SET;
        mHandler.sendMessage(msg);

        switch (mMode) {
            case DATE_MODE_START:
                if (mStartDate.compareTo(mEndDate) == 0
                        || mCurrentDate.compareTo(mEndDate) >= 0) {
                    clearCalendar(mEndDate, year, month, day);
                }
                clearCalendar(mStartDate, year, month, day);
                if (mIsLunar) {
                    if (mStartDate.compareTo(mEndDate) == 0
                            || mCurrentDate.compareTo(mEndDate) >= 0) {
                        mLunarEndYear = year;
                        mLunarEndMonth = month;
                        mLunarEndDay = day;
                        mIsLeapEndMonth = mIsLeapMonth ? LEAP_MONTH : NOT_LEAP_MONTH;
                    }
                    mLunarStartYear = year;
                    mLunarStartMonth = month;
                    mLunarStartDay = day;
                    mIsLeapStartMonth = mIsLeapMonth ? LEAP_MONTH : NOT_LEAP_MONTH;
                }
                break;
            case DATE_MODE_END:
                if (mCurrentDate.compareTo(mStartDate) < 0) {
                    clearCalendar(mStartDate, year, month, day);
                }
                clearCalendar(mEndDate, year, month, day);
                if (mIsLunar) {
                    if (mCurrentDate.compareTo(mStartDate) < 0) {
                        mLunarStartYear = year;
                        mLunarStartMonth = month;
                        mLunarStartDay = day;
                        mIsLeapStartMonth = mIsLeapMonth ? LEAP_MONTH : NOT_LEAP_MONTH;
                    }
                    mLunarEndYear = year;
                    mLunarEndMonth = month;
                    mLunarEndDay = day;
                    mIsLeapEndMonth = mIsLeapMonth ? LEAP_MONTH : NOT_LEAP_MONTH;
                }
                break;
            case DATE_MODE_WEEK_SELECT:
                mIsWeekRangeSet = true;
                final int dayOfWeekStart = (day % 7 + mDayOfWeekStart - 1) % 7;
                final int weekEnd = dayOfWeekStart != 0 ? dayOfWeekStart : 7;
                updateStartEndDateRange(weekEnd, year, month, day);
                break;
            default:
                clearCalendar(mStartDate, year, month, day);
                clearCalendar(mEndDate, year, month, day);
                if (mIsLunar) {
                    mLunarStartYear = year;
                    mLunarStartMonth = month;
                    mLunarStartDay = day;
                    mIsLeapStartMonth = mIsLeapMonth ? LEAP_MONTH : NOT_LEAP_MONTH;
                    mLunarEndYear = year;
                    mLunarEndMonth = month;
                    mLunarEndDay = day;
                    mIsLeapEndMonth = mIsLeapMonth ? LEAP_MONTH : NOT_LEAP_MONTH;
                }
                break;
        }

        if (mMode != DATE_MODE_NONE) {
            onValidationChanged(!mStartDate.after(mEndDate));
        }

        onDateChanged();
    }


    private void updateStartEndDateRange(int weekEnd, int year, int monthOfYear, int dayOfMonth) {
        clearCalendar(mStartDate, year, monthOfYear, (dayOfMonth - weekEnd) + 1);
        int i = 7 - weekEnd;
        clearCalendar(mEndDate, year, monthOfYear, dayOfMonth + i);

        if (mIsLunar) {
            Calendar calendar = convertLunarToSolar(getCalendarForLocale(null, mCurrentLocale),
                    year, monthOfYear, dayOfMonth);

            Calendar calendarClone = (Calendar) calendar.clone();
            calendarClone.add(Calendar.DAY_OF_YEAR, (-weekEnd) + 1);

            LunarDate lunarDate = new LunarDate();
            convertSolarToLunar(calendarClone, lunarDate);

            mLunarStartYear = lunarDate.year;
            mLunarStartMonth = lunarDate.month;
            mLunarStartDay = lunarDate.day;
            mIsLeapStartMonth = NOT_LEAP_MONTH;
            calendar.add(Calendar.DAY_OF_YEAR, i);

            convertSolarToLunar(calendar, lunarDate);

            mLunarEndYear = lunarDate.year;
            mLunarEndMonth = lunarDate.month;
            mLunarEndDay = lunarDate.day;
            mIsLeapEndMonth = NOT_LEAP_MONTH;
        }
    }

    private Calendar convertLunarToSolar(Calendar calendar, int year, int monthOfYear,
            int dayOfMonth) {
        Calendar newCalendar = (Calendar) calendar.clone();
        SeslSolarLunarConverterReflector
                .convertLunarToSolar(mPathClassLoader, mSolarLunarConverter, year, monthOfYear,
                        dayOfMonth, mIsLeapMonth);
        newCalendar.set(SeslSolarLunarConverterReflector.getYear(mPathClassLoader,
                        mSolarLunarConverter),
                SeslSolarLunarConverterReflector.getMonth(mPathClassLoader, mSolarLunarConverter),
                SeslSolarLunarConverterReflector.getDay(mPathClassLoader, mSolarLunarConverter));
        return newCalendar;
    }

    private Calendar convertSolarToLunar(Calendar calendar, LunarDate lunarDate) {
        Calendar newCalendar = (Calendar) calendar.clone();
        SeslSolarLunarConverterReflector
                .convertSolarToLunar(mPathClassLoader, mSolarLunarConverter,
                        calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH));
        newCalendar.set(SeslSolarLunarConverterReflector.getYear(mPathClassLoader,
                        mSolarLunarConverter),
                SeslSolarLunarConverterReflector.getMonth(mPathClassLoader, mSolarLunarConverter),
                SeslSolarLunarConverterReflector.getDay(mPathClassLoader, mSolarLunarConverter));
        if (lunarDate != null) {
            lunarDate.day = SeslSolarLunarConverterReflector.getDay(mPathClassLoader,
                    mSolarLunarConverter);
            lunarDate.month = SeslSolarLunarConverterReflector.getMonth(mPathClassLoader,
                    mSolarLunarConverter);
            lunarDate.year = SeslSolarLunarConverterReflector.getYear(mPathClassLoader,
                    mSolarLunarConverter);
            lunarDate.isLeapMonth = SeslSolarLunarConverterReflector.isLeapMonth(mPathClassLoader
                    , mSolarLunarConverter);
        }
        return newCalendar;
    }

    @NonNull
    public Calendar getStartDate() {
        return mStartDate;
    }

    @NonNull
    public Calendar getEndDate() {
        return mEndDate;
    }

    private static class SavedState extends View.BaseSavedState {
        private final long mMaxDate;
        private final long mMinDate;
        private final int mSelectedDay;
        private final int mSelectedMonth;
        private final int mSelectedYear;

        SavedState(Parcelable superState, int year, int month, int day, long minDate,
                long maxDate) {
            super(superState);
            mSelectedYear = year;
            mSelectedMonth = month;
            mSelectedDay = day;
            mMinDate = minDate;
            mMaxDate = maxDate;
        }

        SavedState(Parcel in) {
            super(in);
            mSelectedYear = in.readInt();
            mSelectedMonth = in.readInt();
            mSelectedDay = in.readInt();
            mMinDate = in.readLong();
            mMaxDate = in.readLong();
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(mSelectedYear);
            dest.writeInt(mSelectedMonth);
            dest.writeInt(mSelectedDay);
            dest.writeLong(mMinDate);
            dest.writeLong(mMaxDate);
        }

        int getSelectedDay() {
            return mSelectedDay;
        }

        int getSelectedMonth() {
            return mSelectedMonth;
        }

        int getSelectedYear() {
            return mSelectedYear;
        }

        long getMinDate() {
            return mMinDate;
        }

        long getMaxDate() {
            return mMaxDate;
        }

        public static final @NonNull Parcelable.Creator<SavedState> CREATOR = new Creator<>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

    void debugLog(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }
    }

    private boolean isRTL() {
        if ("ur".equals(mCurrentLocale.getLanguage())) {
            return false;
        }
        byte directionality =
                Character.getDirectionality(mCurrentLocale.getDisplayName(mCurrentLocale).charAt(0));
        return directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT || directionality == Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC;
    }

    @Override
    public void onDayClick(@NonNull SeslSimpleMonthView view, int year, int month, int day) {
        debugLog("onDayClick day : " + day);

        if (!mIsCalledFromDeactivatedDayClick) {
            mDayOfWeekStart = view.getDayOfWeekStart();
        }

        int currentYear = mCurrentDate.get(Calendar.YEAR);
        int currentMonth = mCurrentDate.get(Calendar.MONTH);
        if (mIsLunar) {
            currentYear = mLunarCurrentYear;
            currentMonth = mLunarCurrentMonth;
        }

        onDayOfMonthSelected(year, month, day);

        final int selectedPos = (month - getMinMonth()) + ((year - getMinYear()) * 12);
        final boolean isNotSamePos = mCurrentPosition != selectedPos;
        if (year != currentYear || month != currentMonth || day != mOldSelectedDay || mIsLunar || isNotSamePos) {
            mOldSelectedDay = day;
            mCalendarPagerAdapter.notifyDataSetChanged();
        }

        final int enabledDayRangeStart = (getMinMonth() == month && getMinYear() == year) ?
                getMinDay() : 1;
        final int enabledDayRangeEnd = (getMaxMonth() == month && getMaxYear() == year) ?
                getMaxDay() : 31;

        if (mIsLunarSupported) {
            view.setLunar(mIsLunar, mIsLeapMonth, mPathClassLoader);
        }

        int startYear, startMonth, startDay, endYear, endMonth, endDay;
        if (mIsLunar) {
            startYear = mLunarStartYear;
            startMonth = mLunarStartMonth;
            startDay = mLunarStartDay;
            endYear = mLunarEndYear;
            endMonth = mLunarEndMonth;
            endDay = mLunarEndDay;
        } else {
            startYear = mStartDate.get(Calendar.YEAR);
            startMonth = mStartDate.get(Calendar.MONTH);
            startDay = mStartDate.get(Calendar.DAY_OF_MONTH);
            endYear = mEndDate.get(Calendar.YEAR);
            endMonth = mEndDate.get(Calendar.MONTH);
            endDay = mEndDate.get(Calendar.DAY_OF_MONTH);
        }

        view.setMonthParams(day, month, year,
                getFirstDayOfWeek(), enabledDayRangeStart, enabledDayRangeEnd,
                mMinDate, mMaxDate, startYear, startMonth, startDay, mIsLeapStartMonth,
                endYear, endMonth, endDay, mIsLeapEndMonth, mMode);
        view.invalidate();
        mIsCalledFromDeactivatedDayClick = false;
    }

    @Override
    public void onDeactivatedDayClick(@NonNull SeslSimpleMonthView view, int year, int month,
            int selectedDay,
            boolean isLeapMonth, boolean isPrevMonth) {
        mIsCalledFromDeactivatedDayClick = true;
        if (mIsLunar) {
            LunarDate lunarDate = getLunarDateByPosition(isPrevMonth ?
                    mCurrentPosition - 1 : mCurrentPosition + 1);
            mIsLeapMonth = lunarDate.isLeapMonth;
            mCurrentPosition = isPrevMonth ? mCurrentPosition - 1 : mCurrentPosition + 1;
            mCalendarViewPager.setCurrentItem(mCurrentPosition);
            mDayOfWeekStart = mCalendarPagerAdapter.views.get(mCurrentPosition).getDayOfWeekStart();
            onDayClick(view, lunarDate.year, lunarDate.month, selectedDay);
        } else {
            mDayOfWeekStart =
                    mCalendarPagerAdapter.views.get(((year - getMinYear()) * DEFAULT_MONTH_PER_YEAR) + (month - getMinMonth())).getDayOfWeekStart();
            onDayClick(view, year, month, selectedDay);
            updateSimpleMonthView(true);
        }
    }


    private int getDayOffset() {
        SeslSimpleMonthView monthView = mCalendarPagerAdapter.views.get(mCurrentPosition);
        mDayOfWeekStart = monthView == null ? 1 : monthView.getDayOfWeekStart();
        int i = (((mCurrentDate.get(5) % 7) + mDayOfWeekStart) - 1) % 7;
        if (i == 0) {
            return 7;
        }
        return i;
    }

    private void updateSimpleMonthView(boolean animate) {

        int month ;
        int year;
        if (mIsLunar) {
            month = mLunarCurrentMonth;
            year = mLunarCurrentYear;
        }else if (mLunarChanged) {
            month = mTempDate.get(Calendar.MONTH);
            year = mTempDate.get(Calendar.YEAR);
        } else {
            month = mCurrentDate.get(Calendar.MONTH);
            year = mCurrentDate.get(Calendar.YEAR);
        }

        int position = (year - getMinYear()) * 12 + (month - getMinMonth());
        if (mIsLunar) {
            if (month < getIndexOfleapMonthOfYear(year)) {
                position = month;
            } else {
                position = month + 1;
            }

            if (year == getMinYear()) {
                position -= getMinMonth();
            } else {
                position += getTotalMonthCountWithLeap(year - 1);
            }

            if (( (mMode == DATE_MODE_START || mMode == DATE_MODE_WEEK_SELECT)
                    && month == mLunarStartMonth
                    && mIsLeapStartMonth == 1 )
                    || ( ( (mMode == DATE_MODE_END || mMode == DATE_MODE_WEEK_SELECT)
                    && month == mLunarEndMonth
                    && mIsLeapEndMonth == 1) || (mMode == DATE_MODE_NONE && mIsLeapMonth))
            ) {
                position++;
            }
        }

        mCurrentPosition = position;
        if (isSystemAnimationsRemoved()) {
            mCalendarViewPager.setCurrentItem(position, false);
        } else {
            mCalendarViewPager.setCurrentItem(position, animate);
        }

        Message msgTextSet = mHandler.obtainMessage();
        msgTextSet.what = MESSAGE_CALENDAR_HEADER_TEXT_VALUE_SET;
        msgTextSet.obj = true;
        mHandler.sendMessage(msgTextSet);

        Message monthButtonSet = mHandler.obtainMessage();
        monthButtonSet.what = MESSAGE_CALENDAR_HEADER_MONTH_BUTTON_SET;
        mHandler.sendMessage(monthButtonSet);
    }

    private boolean isSystemAnimationsRemoved() {
        return Settings.Global.getFloat(mContext.getContentResolver(),
                "animator_duration_scale", 1.0f) == 0.0f;
    }

    private class CalendarPagerAdapter extends PagerAdapter {
        SparseArray<SeslSimpleMonthView> views = new SparseArray<>();

        public CalendarPagerAdapter() {
        }

        @Override
        public int getItemPosition(@NonNull Object object) {
            return POSITION_NONE;
        }

        @Override
        public Parcelable saveState() {
            return null;
        }

        @Override
        public int getCount() {
            final int diffYear = getMaxYear() - getMinYear();
            mPositionCount = (getMaxMonth() - getMinMonth()) + 1 + (diffYear * 12);
            if (mIsLunar) {
                mPositionCount = getTotalMonthCountWithLeap(getMaxYear());
            }
            return mPositionCount;
        }

        @NonNull
        public Object instantiateItem(@NonNull View pager, int position) {
            SeslSimpleMonthView v = new SeslSimpleMonthView(mContext);
            debugLog("instantiateItem : " + position);
            v.setClickable(true);
            v.setOnDayClickListener(SeslDatePicker.this);
            v.setOnDeactivatedDayClickListener(SeslDatePicker.this);
            v.setTextColor(mMonthViewColor);

            final int currentMonth = getMinMonth() + position;

            int year = (currentMonth / DEFAULT_MONTH_PER_YEAR) + getMinYear();
            int month;
            boolean isLeapMonth;
            if (mIsLunar) {
                LunarDate lunarDate = getLunarDateByPosition(position);
                month = lunarDate.month;
                isLeapMonth = lunarDate.isLeapMonth;
                year = lunarDate.year;
            } else {
                month = currentMonth % DEFAULT_MONTH_PER_YEAR;
                isLeapMonth = false;
            }

            int selectedDay = -1;
            if (mIsLunar) {
                if (mLunarCurrentYear == year && mLunarCurrentMonth == month) {
                    selectedDay = mLunarCurrentDay;
                }
            } else {
                if (mCurrentDate.get(Calendar.YEAR) == year
                        && mCurrentDate.get(Calendar.MONTH) == month) {
                    selectedDay = mCurrentDate.get(Calendar.DAY_OF_MONTH);
                }
            }

            if (mIsLunarSupported) {
                v.setLunar(mIsLunar, isLeapMonth, mPathClassLoader);
            }

            int startYear, startMonth, startDay, endYear, endMonth, endDay;
            if (mIsLunar) {
                startYear = mLunarStartYear;
                startMonth = mLunarStartMonth;
                startDay = mLunarStartDay;
                endYear = mLunarEndYear;
                endMonth = mLunarEndMonth;
                endDay = mLunarEndDay;
            } else {
                startYear = mStartDate.get(Calendar.YEAR);
                startMonth = mStartDate.get(Calendar.MONTH);
                startDay = mStartDate.get(Calendar.DAY_OF_MONTH);
                endYear = mEndDate.get(Calendar.YEAR);
                endMonth = mEndDate.get(Calendar.MONTH);
                endDay = mEndDate.get(Calendar.DAY_OF_MONTH);
            }
            v.setMonthParams(selectedDay, month, year,
                    getFirstDayOfWeek(), 1, 31,
                    mMinDate, mMaxDate, startYear, startMonth, startDay, mIsLeapStartMonth,
                    endYear, endMonth, endDay, mIsLeapEndMonth, mMode);

            if (position == 0) {
                v.setFirstMonth();
            }
            if (position == mPositionCount - 1) {
                v.setLastMonth();
            }

            if (mIsLunar) {
                if (position != 0 && getLunarDateByPosition(position - 1).isLeapMonth) {
                    v.setPrevMonthLeap();
                }
                if (position != mPositionCount - 1 && getLunarDateByPosition(position + 1).isLeapMonth) {
                    v.setNextMonthLeap();
                }
            }

            mNumDays = v.getNumDays();
            mWeekStart = v.getWeekStart();

            ((ViewPager) pager).addView(v, 0);
            views.put(position, v);
            return v;
        }

        @Override
        public void destroyItem(@NonNull View pager, int position, @NonNull Object view) {
            debugLog("destroyItem : " + position);
            ((ViewPager) pager).removeView((View) view);
            views.remove(position);
        }

        @Override
        public boolean isViewFromObject(@NonNull View pager, @NonNull Object obj) {
            return pager.equals(obj);
        }

        @Override
        public void startUpdate(@NonNull View view) {
            debugLog("startUpdate");
        }

        @Override
        public void finishUpdate(@NonNull View view) {
            debugLog("finishUpdate");
        }
    }

    class CalendarPageChangeListener implements ViewPager.OnPageChangeListener {
        @Override
        public void onPageScrollStateChanged(int state) {
        }

        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        }

        @Override
        public void onPageSelected(int position) {
            if (mIsRTL) {
                mIsConfigurationChanged = false;
            }

            if (mIsFromSetLunar) {
                mIsFromSetLunar = false;
                return;
            }

            mCurrentPosition = position;

            final int currentMonth = getMinMonth() + position;

            int year = (currentMonth / DEFAULT_MONTH_PER_YEAR) + getMinYear();
            int month = currentMonth % 12;
            int day = mCurrentDate.get(Calendar.DAY_OF_MONTH);
            if (mIsLunar) {
                LunarDate lunarDate = getLunarDateByPosition(position);
                year = lunarDate.year;
                month = lunarDate.month;
                day = mLunarCurrentDay;
                mIsLeapMonth = lunarDate.isLeapMonth;
            }

            final boolean isYearChanged = year != mTempDate.get(Calendar.YEAR);

            mTempDate.set(Calendar.YEAR, year);
            mTempDate.set(Calendar.MONTH, month);
            mTempDate.set(Calendar.DAY_OF_MONTH, 1);
            if (day > mTempDate.getActualMaximum(Calendar.DAY_OF_MONTH)) {
                day = mTempDate.getActualMaximum(Calendar.DAY_OF_MONTH);
            }
            mTempDate.set(Calendar.DAY_OF_MONTH, day);

            Message msg = mHandler.obtainMessage();
            msg.what = MESSAGE_CALENDAR_HEADER_TEXT_VALUE_SET;
            msg.obj = isYearChanged;
            mHandler.sendMessage(msg);
            Message msg1 = mHandler.obtainMessage();
            msg1.what = MESSAGE_CALENDAR_HEADER_MONTH_BUTTON_SET;
            mHandler.sendMessage(msg1);

            SparseArray<SeslSimpleMonthView> views = mCalendarPagerAdapter.views;
            if (views.get(position) != null) {
                views.get(position).clearAccessibilityFocus();
                views.get(position).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
            }
            if (position != 0) {
                int previousPos = position - 1;
                if (views.get(previousPos) != null) {
                    views.get(previousPos).clearAccessibilityFocus();
                    views.get(previousPos).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                }
            }
            if (position != mPositionCount - 1) {
                int nextPos = position + 1;
                if (views.get(nextPos) != null) {
                    views.get(nextPos).clearAccessibilityFocus();
                    views.get(nextPos).setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
                }
            }
        }
    }

    private static Activity scanForActivity(Context cont) {
        if (cont instanceof Activity) {
            return (Activity) cont;
        }
        if (cont instanceof ContextWrapper) {
            return scanForActivity(((ContextWrapper) cont).getBaseContext());
        }
        return null;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mMeasureSpecHeight = View.MeasureSpec.getSize(heightMeasureSpec);
        final int newWidthMeasureSpec = makeMeasureSpec(widthMeasureSpec, mCalendarViewPagerWidth);

        if (mIsFirstMeasure || mOldCalendarViewPagerWidth != mCalendarViewPagerWidth) {
            mIsFirstMeasure = false;

            mOldCalendarViewPagerWidth = mCalendarViewPagerWidth;

            if (mCustomButtonLayout != null) {
                mLpPreAllocated.height = mCalendarHeaderLayoutHeight;
                mCustomButtonLayout.setLayoutParams(mLpPreAllocated);
            }
            mCalendarHeaderLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, mCalendarHeaderLayoutHeight));
            mDayOfTheWeekLayout.setLayoutParams(new LinearLayout.LayoutParams(
                    mDayOfTheWeekLayoutWidth, mDayOfTheWeekLayoutHeight));
            mDayOfTheWeekView.setLayoutParams(new LinearLayout.LayoutParams(
                    mDayOfTheWeekLayoutWidth, mDayOfTheWeekLayoutHeight));
            mCalendarViewPager.setLayoutParams(new LinearLayout.LayoutParams(
                    mCalendarViewPagerWidth, mCalendarViewPagerHeight));

            if (mIsRTL && mIsConfigurationChanged) {
                mCalendarViewPager.seslSetConfigurationChanged(true);
            }

            mFirstBlankSpace.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, mFirstBlankSpaceHeight));
            mSecondBlankSpace.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, mSecondBlankSpaceHeight));
        }

        super.onMeasure(newWidthMeasureSpec, heightMeasureSpec);
    }

    private int makeMeasureSpec(int widthMeasureSpec, int heightMeasureSpec) {
        if (heightMeasureSpec == SIZE_UNSPECIFIED) {
            return widthMeasureSpec;
        }

        final int mode = View.MeasureSpec.getMode(widthMeasureSpec);

        final int size;
        if (mode == MeasureSpec.AT_MOST) {
            int smallestScreenWidthDp = getResources().getConfiguration().smallestScreenWidthDp;
            if (smallestScreenWidthDp >= 600) {
                size =
                        getResources().getDimensionPixelSize(R.dimen.sesl_date_picker_dialog_min_width);
            } else {
                size = (int) (TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                        (float) smallestScreenWidthDp, getResources().getDisplayMetrics()) + 0.5f);
            }
        } else {
            size = View.MeasureSpec.getSize(widthMeasureSpec);
        }

        switch (mode) {
            case MeasureSpec.AT_MOST:
                mCalendarViewPagerWidth = size - (mCalendarViewMargin * 2);
                mDayOfTheWeekLayoutWidth = size - (mCalendarViewMargin * 2);
                return View.MeasureSpec.makeMeasureSpec(size, MeasureSpec.EXACTLY);
            case MeasureSpec.UNSPECIFIED:
                return View.MeasureSpec.makeMeasureSpec(heightMeasureSpec, MeasureSpec.EXACTLY);
            case MeasureSpec.EXACTLY:
                mCalendarViewPagerWidth = size - (mCalendarViewMargin * 2);
                mDayOfTheWeekLayoutWidth = size - (mCalendarViewMargin * 2);
                return widthMeasureSpec;
        }

        throw new IllegalArgumentException("Unknown measure mode: " + mode);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        calculateContentHeight();
    }

    private void calculateContentHeight() {
        if (getLayoutParams().height == ViewGroup.LayoutParams.WRAP_CONTENT || getMeasuredHeight() <= mDatePickerHeight) {
            if (mContentFrame == null && mDialogWindow != null) {
                mContentFrame = mDialogWindow.findViewById(androidx.appcompat.R.id.customPanel);
            }

            int availableHeight = mMeasureSpecHeight;
            if (mContentFrame != null) {
                availableHeight = mContentFrame.getMeasuredHeight();
                if (mDialogWindow != null) {
                    availableHeight -= mDialogPaddingVertical;
                }
            }

            updateViewType(availableHeight);
        }
    }

    private void updateViewType(int height) {
        //sesl6
        if (mIsCalendarViewDisabled) {
            setCurrentViewType(VIEW_TYPE_SPINNER);
            if (!useLegacyLayout){
                mCalendarHeaderTextSpinnerLayout.setOnClickListener(null);
                mCalendarHeaderTextSpinnerLayout.setClickable(false);
                removeCalendarHeaderPadding();
                mCalendarHeaderSpinner.setVisibility(View.GONE);
            }else{
                mCalendarHeaderText.setOnClickListener(null);
                mCalendarHeaderText.setClickable(false);
            }
            mAnimator.setMeasureAllChildren(false);
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Activity activity = scanForActivity(mContext);
                if (activity != null && activity.isInMultiWindowMode()) {
                    if (height < mDatePickerHeight) {
                        setCurrentViewType(VIEW_TYPE_SPINNER);
                        if (!useLegacyLayout) {
                            mCalendarHeaderTextSpinnerLayout.setOnClickListener(null);
                            mCalendarHeaderTextSpinnerLayout.setClickable(false);
                            removeCalendarHeaderPadding();
                            mCalendarHeaderSpinner.setVisibility(View.GONE);
                        }
                        mAnimator.setMeasureAllChildren(false);
                    } else {
                        if (!useLegacyLayout) {
                            if (!mCalendarHeaderTextSpinnerLayout.hasOnClickListeners()) {
                                mCalendarHeaderTextSpinnerLayout.setOnClickListener(mCalendarHeaderClickListener);
                                mCalendarHeaderTextSpinnerLayout.setClickable(true);
                            }
                        }else{
                            if (!mCalendarHeaderText.hasOnClickListeners()) {
                                mCalendarHeaderText.setOnClickListener(mCalendarHeaderClickListener);
                                mCalendarHeaderText.setClickable(true);
                            }
                        }
                    }
                }
            }
        }
    }

    private void removeCalendarHeaderPadding() {
        mCalendarHeaderTextSpinnerLayout.setPadding(0, 0, 0, 0);
    }

    @RestrictTo(LIBRARY_GROUP)
    public void setDialogWindow(@NonNull Window window) {
        if (window != null) {
            mDialogWindow = window;
        }
    }

    @RestrictTo(LIBRARY_GROUP)
    public void setDialogPaddingVertical(int paddingVertical) {
        mDialogPaddingVertical = paddingVertical;
    }

    String getMonthAndYearString(Calendar calendar) {
        if (mIsFarsiLanguage) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("LLLL y", mCurrentLocale);
            return simpleDateFormat.format(calendar.getTime());
        }

        StringBuilder stringBuilder = new StringBuilder(50);
        Formatter formatter = new Formatter(stringBuilder, mCurrentLocale);
        stringBuilder.setLength(0);

        final long millis = calendar.getTimeInMillis();
        return DateUtils.formatDateRange(getContext(), formatter, millis, millis,
                DateUtils.FORMAT_NO_MONTH_DAY | DateUtils.FORMAT_SHOW_YEAR,
                Time.getCurrentTimezone()).toString();
    }


    @Override
    public void onClick(View view) {
        final int viewId = view.getId();
        if (viewId == R.id.sesl_date_picker_calendar_header_prev_button) {
            if (mIsRTL) {
                if (mCurrentPosition != mPositionCount - 1) {
                    mCalendarViewPager.setCurrentItem(mCurrentPosition + 1);
                }
            } else {
                if (mCurrentPosition != 0) {
                    mCalendarViewPager.setCurrentItem(mCurrentPosition - 1);
                }
            }
        } else if (viewId == R.id.sesl_date_picker_calendar_header_next_button) {
            if (mIsRTL) {
                if (mCurrentPosition != 0) {
                    mCalendarViewPager.setCurrentItem(mCurrentPosition - 1);
                }
            } else {
                if (mCurrentPosition != mPositionCount - 1) {
                    mCalendarViewPager.setCurrentItem(mCurrentPosition + 1);
                }
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeAllCallbacks();
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onLongClick(View view) {
        final int id = view.getId();
        if (id == R.id.sesl_date_picker_calendar_header_prev_button
                && mCurrentPosition != 0) {
            postChangeCurrentByOneFromLongPress(false, ViewConfiguration.getLongPressTimeout());
        } else if (id == R.id.sesl_date_picker_calendar_header_next_button
                && mCurrentPosition != mPositionCount - 1) {
            postChangeCurrentByOneFromLongPress(true, ViewConfiguration.getLongPressTimeout());
        }
        return false;
    }

    private void postChangeCurrentByOneFromLongPress(boolean increment, long delayMillis) {
        if (mChangeCurrentByOneFromLongPressCommand == null) {
            mChangeCurrentByOneFromLongPressCommand = new ChangeCurrentByOneFromLongPressCommand();
        } else {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
        }
        mChangeCurrentByOneFromLongPressCommand.setStep(increment);
        postDelayed(mChangeCurrentByOneFromLongPressCommand, delayMillis);
    }

    void removeAllCallbacks() {
        if (mChangeCurrentByOneFromLongPressCommand != null) {
            removeCallbacks(mChangeCurrentByOneFromLongPressCommand);
            new Handler().postDelayed(() -> mCalendarViewPager.setCurrentItem(mCurrentPosition,
                    false), 200);
        }
    }

    class ChangeCurrentByOneFromLongPressCommand implements Runnable {
        private boolean mIncrement;

        void setStep(boolean increment) {
            mIncrement = increment;
        }

        @Override
        public void run() {
            if (mIncrement) {
                mCalendarViewPager.setCurrentItem(mCurrentPosition + 1);
            } else {
                mCalendarViewPager.setCurrentItem(mCurrentPosition - 1);
            }
            postDelayed(this, DEFAULT_LONG_PRESS_UPDATE_INTERVAL);
        }
    }

    public void setDateMode(int mode) {
        mMode = mode;
        mIsWeekRangeSet = false;

        switch (mode) {
            case DATE_MODE_START:
                int startYear = mIsLunar ? mLunarStartYear: mStartDate.get(Calendar.YEAR);
                int startMonth = mIsLunar ? mLunarStartMonth: mStartDate.get(Calendar.MONTH);
                int startDay = mIsLunar ? mLunarStartDay: mStartDate.get(Calendar.DAY_OF_MONTH);
                mSpinnerLayout.updateDate(startYear, startMonth, startDay);
                break;
            case DATE_MODE_END:
                int endYear = mIsLunar ? mLunarEndYear: mEndDate.get(Calendar.YEAR);
                int endMonth = mIsLunar ? mLunarEndMonth: mEndDate.get(Calendar.MONTH);
                int endDay = mIsLunar ? mLunarEndDay: mEndDate.get(Calendar.DAY_OF_MONTH);
                mSpinnerLayout.updateDate(endYear, endMonth, endDay);
                break;
        }

        if (mCurrentViewType == VIEW_TYPE_SPINNER) {
            mSpinnerLayout.setVisibility(VISIBLE);
            mSpinnerLayout.setEnabled(true);
        }

        SeslSimpleMonthView currentMonthView = mCalendarPagerAdapter.views.get(mCurrentPosition);
        if (currentMonthView != null) {
            int day, month, year;
            if (mIsLunar) {
                year = mLunarCurrentYear;
                month = mLunarCurrentMonth;
                day = mLunarCurrentDay;
            } else {
                year = mCurrentDate.get(Calendar.YEAR);
                month = mCurrentDate.get(Calendar.MONTH);
                day = mCurrentDate.get(Calendar.DAY_OF_MONTH);
            }

            int minDay = (getMinMonth() == month && getMinYear() == year) ? getMinDay() : 1;
            int maxDay = (getMaxMonth() == month && getMaxYear() == year) ? getMaxDay() : 31;

            int startYear, startMonth, startDay, endYear, endMonth, endDay;
            if (mIsLunar) {
                startYear = mLunarStartYear;
                startMonth = mLunarStartMonth;
                startDay = mLunarStartDay;
                endYear = mLunarEndYear;
                endMonth = mLunarEndMonth;
                endDay = mLunarEndDay;
            } else {
                startYear = mStartDate.get(Calendar.YEAR);
                startMonth = mStartDate.get(Calendar.MONTH);
                startDay = mStartDate.get(Calendar.DAY_OF_MONTH);
                endYear = mEndDate.get(Calendar.YEAR);
                endMonth = mEndDate.get(Calendar.MONTH);
                endDay = mEndDate.get(Calendar.DAY_OF_MONTH);
            }
            currentMonthView.setMonthParams(day, month, year,
                    getFirstDayOfWeek(), minDay, maxDay, mMinDate, mMaxDate,
                    startYear, startMonth, startDay, mIsLeapStartMonth,
                    endYear, endMonth, endDay, mIsLeapEndMonth, mMode);
            currentMonthView.invalidate();
        }

        if (mIsLunar) {
            updateSimpleMonthView(false);
        }

        mCalendarPagerAdapter.notifyDataSetChanged();
    }

    public int getDateMode() {
        return mMode;
    }

    private void checkMaxFontSize() {
        final float currentFontScale = mContext.getResources().getConfiguration().fontScale;
        final int calendarHeaderTextSize = getResources().getDimensionPixelOffset(
                R.dimen.sesl_date_picker_calendar_header_month_text_size);
        if (currentFontScale > MAX_FONT_SCALE) {
            mCalendarHeaderText.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                    (float) Math.floor(Math.ceil(calendarHeaderTextSize / currentFontScale) * (double) 1.2f));
        }
    }

    public void setCurrentViewType(int type) {
        boolean typeChanged = false;

        switch (type) {
            case VIEW_TYPE_CALENDAR://0
                if (mCurrentViewType != type) {
                    mSpinnerLayout.updateInputState();
                    mSpinnerLayout.setEditTextMode(false);

                    mAnimator.setDisplayedChild(0);

                    mSpinnerLayout.setVisibility(INVISIBLE);
                    mSpinnerLayout.setEnabled(false);

                    mCurrentViewType = type;

                    Message msg = mHandler.obtainMessage();
                    msg.what = MESSAGE_CALENDAR_HEADER_TEXT_VALUE_SET;
                    mHandler.sendMessage(msg);

                    mCalendarPagerAdapter.notifyDataSetChanged();
                }

                if (mOnViewTypeChangedListener != null) {
                    mOnViewTypeChangedListener.onViewTypeChanged(this);
                }

                Message msg = mHandler.obtainMessage();
                msg.what = MESSAGE_CALENDAR_HEADER_MONTH_BUTTON_SET;
                mHandler.sendMessage(msg);
                break;

            case VIEW_TYPE_SPINNER://1
                if (mCurrentViewType != type) {
                    if (mCalendarHeaderSpinner != null) {
                        mCalendarHeaderSpinner.setRotation(-180.0f);
                    }

                    switch (mMode) {
                        case DATE_MODE_START:
                            int startYear = mStartDate.get(Calendar.YEAR);
                            int startMonth = mStartDate.get(Calendar.MONTH);
                            int startDayOfMonth = mStartDate.get(Calendar.DAY_OF_MONTH);
                            if (mIsLunar) {
                                startYear = mLunarStartYear;
                                startMonth = mLunarStartMonth;
                                startDayOfMonth = mLunarStartDay;
                            }
                            mSpinnerLayout.updateDate(startYear, startMonth, startDayOfMonth);
                            break;

                        case DATE_MODE_END:
                            int endYear = mEndDate.get(Calendar.YEAR);
                            int endMonth = mEndDate.get(Calendar.MONTH);
                            int endDayOfMonth = mEndDate.get(Calendar.DAY_OF_MONTH);
                            if (mIsLunar) {
                                endYear = mLunarEndYear;
                                endMonth = mLunarEndMonth;
                                endDayOfMonth = mLunarEndDay;
                            }
                            mSpinnerLayout.updateDate(endYear, endMonth, endDayOfMonth);
                            break;

                        default:
                            int year = mCurrentDate.get(Calendar.YEAR);
                            int month = mCurrentDate.get(Calendar.MONTH);
                            int dayOfMonth = mCurrentDate.get(Calendar.DAY_OF_MONTH);
                            if (mIsLunar) {
                                year = mLunarCurrentYear;
                                month = mLunarCurrentMonth;
                                dayOfMonth = mLunarCurrentDay;
                            }
                            mSpinnerLayout.updateDate(year, month, dayOfMonth);
                    }

                    mAnimator.setDisplayedChild(VIEW_TYPE_SPINNER);

                    mSpinnerLayout.setEnabled(true);

                    mCurrentViewType = type;

                    Message msg1 = mHandler.obtainMessage();
                    msg1.what = MESSAGE_CALENDAR_HEADER_TEXT_VALUE_SET;
                    mHandler.sendMessage(msg1);

                    typeChanged = true;
                }

                if (mOnViewTypeChangedListener != null && typeChanged) {
                    mOnViewTypeChangedListener.onViewTypeChanged(this);
                }

                Message msg1 = mHandler.obtainMessage();
                msg1.what = MESSAGE_CALENDAR_HEADER_MONTH_BUTTON_SET;
                mHandler.sendMessage(msg1);
                break;
        }
    }

    public int getCurrentViewType() {
        return mCurrentViewType;
    }

    public void setEditTextMode(boolean editTextMode) {
        if (mCurrentViewType != VIEW_TYPE_CALENDAR) {
            mSpinnerLayout.setEditTextMode(editTextMode);
        }
    }

    public boolean isEditTextMode() {
        return mCurrentViewType != VIEW_TYPE_CALENDAR
                && mSpinnerLayout.isEditTextMode();
    }

    public void setOnEditTextModeChangedListener(@Nullable OnEditTextModeChangedListener onEditModeChangedListener) {
        mSpinnerLayout.setOnEditTextModeChangedListener(this, onEditModeChangedListener);
    }

    @NonNull
    public EditText getEditText(int picker) {
        return mSpinnerLayout.getEditText(picker);
    }

    @NonNull
    public SeslNumberPicker getNumberPicker(int picker) {
        return mSpinnerLayout.getNumberPicker(picker);
    }

    public void setLunarSupported(boolean supported, @Nullable View switchButton) {
        mIsLunarSupported = supported;

        if (!supported) {
            mIsLunar = false;
            mIsLeapMonth = false;
            mCustomButtonView = null;
        } else {
            removeCustomViewFromParent();
            mCustomButtonView = switchButton;
            if (switchButton != null) {
                removeCustomViewFromParent();
                mCustomButtonView.setId(android.R.id.custom);
                RelativeLayout.LayoutParams buttonParams;
                ViewGroup.LayoutParams layoutParams = mCustomButtonView.getLayoutParams();
                if (layoutParams instanceof RelativeLayout.LayoutParams) {
                    buttonParams = (RelativeLayout.LayoutParams) layoutParams;
                } else {
                    if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
                        buttonParams =
                                new RelativeLayout.LayoutParams((ViewGroup.MarginLayoutParams) layoutParams);
                    } else if (layoutParams != null) {
                        buttonParams = new RelativeLayout.LayoutParams(layoutParams);
                    } else {
                        buttonParams = new RelativeLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                    }
                }
                buttonParams.addRule(RelativeLayout.CENTER_VERTICAL);
                buttonParams.addRule(RelativeLayout.ALIGN_PARENT_END);
                addCustomButtonInHeader();
            }
        }

        if (mIsLunarSupported && mPathClassLoader == null) {
            mPackageManager = mContext.getApplicationContext().getPackageManager();
            mPathClassLoader = LunarUtils.getPathClassLoader(getContext());
            if (mPathClassLoader != null) {
                mSolarLunarConverter =
                        SeslFeatureReflector.getSolarLunarConverter(mPathClassLoader);
                mSolarLunarTables = SeslFeatureReflector.getSolarLunarTables(mPathClassLoader);
            }
        }
    }

    public void setLunar(boolean isLunar, boolean isLeapMonth) {
        if (mIsLunarSupported && mIsLunar != isLunar) {
            mIsLunar = isLunar;
            mIsLeapMonth = isLeapMonth;
            mSpinnerLayout.setLunar(isLunar, isLeapMonth, mPathClassLoader);

            if (isLunar) {
                setTotalMonthCountWithLeap();
                if (mMode == DATE_MODE_NONE) {
                    mIsLeapStartMonth = isLeapMonth ? LEAP_MONTH : NOT_LEAP_MONTH;
                    mIsLeapEndMonth = isLeapMonth ? LEAP_MONTH : NOT_LEAP_MONTH;
                }
            }

            mIsFromSetLunar = true;
            mCalendarPagerAdapter.notifyDataSetChanged();
            mLunarChanged = true;
            updateSimpleMonthView(true);
            mLunarChanged = false;
        }
    }

    public boolean isLunar() {
        return mIsLunar;
    }

    public boolean isLeapMonth() {
        return mIsLeapMonth;
    }

    public void setSeparateLunarButton(boolean separate) {
        if (mIsCustomButtonSeparate != separate) {
            if (separate) {
                removeCustomButtonInHeader();
                addCustomButtonSeparateLayout();
            } else {
                removeCustomButtonSeparateLayout();
                addCustomButtonInHeader();
            }
            mIsCustomButtonSeparate = separate;
        }
    }

    private void removeCustomViewFromParent() {
        if (mCustomButtonView != null) {
            ViewParent parent = mCustomButtonView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(mCustomButtonView);
            }
        }
    }

    private void addCustomButtonInHeader() {
        if (mCustomButtonView != null) {
            removeCustomViewFromParent();
            RelativeLayout.LayoutParams lp =
                    (RelativeLayout.LayoutParams) mCalendarHeader.getLayoutParams();
            lp.addRule(RelativeLayout.START_OF, mCustomButtonView.getId());
            lp.leftMargin = mContext.getResources().getDimensionPixelOffset(
                    R.dimen.sesl_date_picker_lunar_calendar_header_margin);
            ((RelativeLayout.LayoutParams) mPrevButton.getLayoutParams()).leftMargin = 0;
            ((RelativeLayout.LayoutParams) mNextButton.getLayoutParams()).rightMargin = 0;
            mCalendarHeaderLayout.addView(mCustomButtonView);
        }
    }

    private void removeCustomButtonInHeader() {
        final Resources res = mContext.getResources();
        RelativeLayout.LayoutParams lp =
                (RelativeLayout.LayoutParams) mCalendarHeader.getLayoutParams();
        lp.removeRule(RelativeLayout.START_OF);
        lp.leftMargin = 0;
        ((RelativeLayout.LayoutParams) mPrevButton.getLayoutParams()).leftMargin
                = res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_view_margin);
        ((RelativeLayout.LayoutParams) mNextButton.getLayoutParams()).rightMargin
                = res.getDimensionPixelOffset(R.dimen.sesl_date_picker_calendar_view_margin);
        removeCustomViewFromParent();
    }

    private void addCustomButtonSeparateLayout() {
        if (mCustomButtonView != null) {
            if (mCustomButtonLayout == null) {
                mCustomButtonLayout = new RelativeLayout(mContext);
                mCustomButtonLayout.setLayoutParams(
                        new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                                mCalendarHeaderLayoutHeight));
            } else {
                mCustomButtonLayout.getLayoutParams().height =
                        mCalendarHeaderLayoutHeight;
            }
            removeCustomViewFromParent();
            mCustomButtonLayout.addView(mCustomButtonView);
            mDatePickerLayout.addView(mCustomButtonLayout, 0);
            mDatePickerHeight += mCalendarHeaderLayoutHeight;
        }
    }

    private void removeCustomButtonSeparateLayout() {
        removeCustomViewFromParent();
        mDatePickerLayout.removeView(mCustomButtonLayout);
        mDatePickerHeight -= mCalendarHeaderLayoutHeight;
    }


    private void setTotalMonthCountWithLeap() {
        if (mSolarLunarTables != null && mPathClassLoader != null) {
            int nextLeapMonth = 0;
            mTotalMonthCountWithLeap = new int[getMaxYear() - getMinYear() + 1];

            for(int i = getMinYear(); i <= getMaxYear(); ++i) {
                int leapMonthIdx;
                getLeapMonth: {
                    int minYear = this.getMinYear();
                    leapMonthIdx = 12;
                    if (i == minYear) {
                        minYear = this.getMinMonth() + 1;
                        leapMonthIdx = this.getIndexOfleapMonthOfYear(i);
                        if (leapMonthIdx <= 12 && leapMonthIdx >= minYear) {
                            leapMonthIdx = 13 - minYear;
                        } else {
                            leapMonthIdx = 12 - minYear;
                        }
                    } else {
                        if (i != getMaxYear()) {
                            if (getIndexOfleapMonthOfYear(i) <= 12) {
                                leapMonthIdx = 13;
                            }
                            break getLeapMonth;
                        }

                        minYear = this.getMaxMonth() + 1;
                        leapMonthIdx = minYear;
                        int leapMonthIdx2 = this.getIndexOfleapMonthOfYear(i);
                        if (leapMonthIdx2 > 12) {
                            break getLeapMonth;
                        }

                        if (minYear < leapMonthIdx2) {
                            break getLeapMonth;
                        }
                    }

                    ++leapMonthIdx;
                }

                nextLeapMonth += leapMonthIdx;
                mTotalMonthCountWithLeap[i - getMinYear()] = nextLeapMonth;
            }
        }
    }


    int getTotalMonthCountWithLeap(int year) {
        if (mTotalMonthCountWithLeap == null || year < getMinYear()) {
            return 0;
        }
        return mTotalMonthCountWithLeap[year - getMinYear()];
    }


    LunarDate getLunarDateByPosition(int position) {

        LunarDate lunarDate = new LunarDate();
        int year;
        int minYear;
        year = minYear = getMinYear();
        int month = position;

        boolean isLeap;
        while(true) {
            int maxYear = getMaxYear();
            if (year > maxYear) {
                isLeap = false;
                month = 0;
                year = minYear;
                break;
            }

            if (month < getTotalMonthCountWithLeap(year)) {
                if (year == getMinYear()) {
                    minYear = -getMinMonth();
                } else {
                    minYear = getTotalMonthCountWithLeap(year - 1);
                }

                int d = month - minYear;
                maxYear = this.getIndexOfleapMonthOfYear(year);
                byte m = 12;
                if (maxYear <= 12) {
                    m = 13;
                }

                if (d < maxYear) {
                    month = d;
                } else {
                    month = d - 1;
                }

                isLeap = false;
                if (m == 13) {
                    if (maxYear == d) {
                        isLeap = true;
                    }
                }
                break;
            }

            ++year;
        }

        lunarDate.set(year, month, 1, isLeap);
        return lunarDate;
    }

    private int getIndexOfleapMonthOfYear(int year) {
        if (mSolarLunarTables == null) {
            return 127;
        }

        final int startOfLunarYear = SeslSolarLunarTablesReflector
                .getField_START_OF_LUNAR_YEAR(mPathClassLoader, mSolarLunarTables);
        final int widthPerYear = SeslSolarLunarTablesReflector
                .getField_WIDTH_PER_YEAR(mPathClassLoader, mSolarLunarTables);
        final int indexOfLeapMonth = SeslSolarLunarTablesReflector
                .getField_INDEX_OF_LEAP_MONTH(mPathClassLoader, mSolarLunarTables);
        return SeslSolarLunarTablesReflector.getLunar(mPathClassLoader, mSolarLunarTables,
                ((year - startOfLunarYear) * widthPerYear) + indexOfLeapMonth);
    }

    private static class LunarDate {
        public int day;
        boolean isLeapMonth;
        public int month;
        public int year;

        LunarDate() {
            year = 1900;
            month = 1;
            day = 1;
            isLeapMonth = false;
        }

        LunarDate(int year, int month, int day, boolean isLeap) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.isLeapMonth = isLeap;
        }

        public void set(int year, int month, int day, boolean isLeap) {
            this.year = year;
            this.month = month;
            this.day = day;
            this.isLeapMonth = isLeap;
        }
    }

    private static class LunarUtils {
        private static PathClassLoader mClassLoader;

        public static PathClassLoader getPathClassLoader(Context context) {
            if (mClassLoader == null) {
                try {
                    ApplicationInfo appInfo = context.getPackageManager()
                            .getApplicationInfo(getCalendarPackageName(),
                                    PackageManager.GET_META_DATA);
                    if (appInfo == null) {
                        Log.e(TAG, "getPathClassLoader, appInfo is null");
                        return null;
                    }

                    String calendarPkgPath = appInfo.sourceDir;
                    if (calendarPkgPath == null || TextUtils.isEmpty(calendarPkgPath)) {
                        Log.e(SeslDatePicker.TAG, "getPathClassLoader, calendar package source " +
                                "directory is null or empty");
                        return null;
                    }

                    mClassLoader = new PathClassLoader(calendarPkgPath,
                            ClassLoader.getSystemClassLoader());
                } catch (PackageManager.NameNotFoundException unused) {
                    Log.e(SeslDatePicker.TAG, "getPathClassLoader, calendar package name not "
                            + "found");
                    return null;
                }
            }

            return mClassLoader;
        }
    }

    static String getCalendarPackageName() {
        String packageName = SeslFloatingFeatureReflector
                .getString("SEC_FLOATING_FEATURE_CALENDAR_CONFIG_PACKAGE_NAME",
                        "com.android.calendar");
        if ("com.android.calendar".equals(packageName)) {
            return packageName;
        }

        try {
            mPackageManager.getPackageInfo(packageName, 0);
            return packageName;
        } catch (PackageManager.NameNotFoundException e) {
            return "com.android.calendar";
        }
    }

    public void setLunarStartDate(int year, int month, int day, boolean isLeapStartMonth) {
        mLunarStartYear = year;
        mLunarStartMonth = month;
        mLunarStartDay = day;
        mIsLeapStartMonth = isLeapStartMonth ? LEAP_MONTH : NOT_LEAP_MONTH;
    }

    @NonNull
    public int[] getLunarStartDate() {
        return new int[]{mLunarStartYear, mLunarStartMonth, mLunarStartDay, mIsLeapStartMonth};
    }

    public void setLunarEndDate(int year, int month, int day, boolean isLeapEndMonth) {
        mLunarEndYear = year;
        mLunarEndMonth = month;
        mLunarEndDay = day;
        mIsLeapEndMonth = isLeapEndMonth ? LEAP_MONTH : NOT_LEAP_MONTH;
    }

    @NonNull
    public int[] getLunarEndDate() {
        return new int[]{mLunarEndYear, mLunarEndMonth, mLunarEndDay, mIsLeapEndMonth};
    }

    private boolean isFarsiLanguage() {
        return "fa".equals(mCurrentLocale.getLanguage());
    }

    private boolean isSimplifiedChinese() {
        return mCurrentLocale.getLanguage().equals(Locale.SIMPLIFIED_CHINESE.getLanguage())
                && mCurrentLocale.getCountry().equals(Locale.SIMPLIFIED_CHINESE.getCountry());
    }

    @Override
    public void requestLayout() {
        super.requestLayout();
        if (mSpinnerLayout != null && mSpinnerLayout.getVisibility() == View.VISIBLE) {
            mSpinnerLayout.requestLayout();
        }
    }

    private String getMonthViewColorStringForSpecific() {
        try {
            if ("wifi-only"
                    .equalsIgnoreCase(SeslSystemPropertiesReflector
                            .getStringProperties("ro.carrier"))) {
                String countryIsoCode = SeslSystemPropertiesReflector
                        .getStringProperties("persist.sys.selected_country_iso");
                if (TextUtils.isEmpty(countryIsoCode)
                        && UAE_SALES_CODE.equals(SeslSystemPropertiesReflector.getSalesCode())) {
                    return null;
                }
                if (TextUtils.isEmpty(countryIsoCode)) {
                    countryIsoCode = SeslSystemPropertiesReflector
                            .getStringProperties("ro.csc.countryiso_code");
                }
                if ("AE".equals(countryIsoCode)) {
                    return UAE_WEEK_DAY_STRING_FEATURE;
                }
            } else if (UAE_SALES_CODE.equals(SeslSystemPropertiesReflector.getSalesCode())) {
                TelephonyManager manager
                        = (TelephonyManager) mContext.getSystemService(Context.TELEPHONY_SERVICE);
                final String simOperator = manager.getSimOperator();
                if (simOperator != null && simOperator.length() > 3
                        && Integer.parseInt(simOperator.substring(0, 3)) == UAE_MCC) {
                    return UAE_WEEK_DAY_STRING_FEATURE;
                }
            }
            return null;
        } catch (NoClassDefFoundError e) {
            Log.e(TAG, "msg : " + e.getMessage());
            return null;
        }
    }

    private class DayOfTheWeekView extends View {
        private final Paint mMonthDayLabelPaint;
        private final int mNormalDayTextColor;
        private final int mSaturdayTextColor;
        private final int mSundayTextColor;
        private final int[] mDayColorSet = new int[7];
        private final String mDefaultWeekdayFeatureString = "XXXXXXR";
        private final Calendar mDayLabelCalendar = Calendar.getInstance();
        private final String mWeekdayFeatureString;

        public DayOfTheWeekView(Context context, TypedArray array) {
            super(context);

            final Resources res = context.getResources();

            final int monthDayLabelTextSize =
                    res.getDimensionPixelSize(R.dimen.sesl_date_picker_month_day_label_text_size);

            mNormalDayTextColor = array.getColor(R.styleable.DatePicker_dayTextColor,
                    res.getColor(R.color.sesl_date_picker_normal_text_color_light));
            mSundayTextColor = array.getColor(R.styleable.DatePicker_sundayTextColor,
                    res.getColor(R.color.sesl_date_picker_sunday_text_color_light));
            mSaturdayTextColor = ResourcesCompat.getColor(res,
                    R.color.sesl_date_picker_saturday_week_text_color_light, null);

            if (mMonthViewColor != null) {
                mWeekdayFeatureString = mMonthViewColor;
            } else {
                mWeekdayFeatureString = SeslCscFeatureReflector.getString(
                        TAG_CSCFEATURE_CALENDAR_SETCOLOROFDAYS, mDefaultWeekdayFeatureString);
            }

            mMonthDayLabelPaint = new Paint();
            mMonthDayLabelPaint.setAntiAlias(true);
            mMonthDayLabelPaint.setColor(mNormalDayTextColor);
            mMonthDayLabelPaint.setTextSize(monthDayLabelTextSize);
            mMonthDayLabelPaint.setTypeface(getRegularFontTypeface());
            mMonthDayLabelPaint.setTextAlign(Paint.Align.CENTER);
            mMonthDayLabelPaint.setStyle(Paint.Style.FILL);
            mMonthDayLabelPaint.setFakeBoldText(false);
        }

        @Override
        protected void onDraw(@NonNull Canvas c) {
            super.onDraw(c);
            if (mNumDays != 0) {
                int posY = mDayOfTheWeekLayoutHeight * 2 / 3;
                int scale = mDayOfTheWeekLayoutWidth / (mNumDays * 2);
                int posX = 0;

                while(true) {
                    int dayIndex = 0;
                    int dayOfWeek;
                    if (posX >= mNumDays) {
                        while(dayIndex < mNumDays) {
                            dayOfWeek = (mWeekStart + dayIndex) % mNumDays;
                            mDayLabelCalendar.set(Calendar.DAY_OF_WEEK, dayOfWeek);
                            String dayChar =
                                    mDayFormatter.format(mDayLabelCalendar.getTime()).toUpperCase();
                            int sidePadding;
                            if (mIsRTL) {
                                posX = ((mNumDays - 1 - dayIndex) * 2 + 1) * scale;
                            } else {
                                posX = (dayIndex * 2 + 1) * scale;
                            }
                            sidePadding = mPadding;

                            mMonthDayLabelPaint.setColor(mDayColorSet[dayOfWeek]);
                            c.drawText(dayChar, (float)(posX + sidePadding), (float)posY,
                                    mMonthDayLabelPaint);
                            ++dayIndex;
                        }
                        return;
                    }

                    final char colorCode = mWeekdayFeatureString.charAt(posX);
                    dayOfWeek = (posX + 2) % mNumDays;
                    if (colorCode != 'B') {
                        if (colorCode != 'R') {
                            mDayColorSet[dayOfWeek] = mNormalDayTextColor;
                        } else {
                            mDayColorSet[dayOfWeek] = mSundayTextColor;
                        }
                    } else {
                        mDayColorSet[dayOfWeek] = mSaturdayTextColor;
                    }

                    ++posX;
                }
            }
        }
    }
}

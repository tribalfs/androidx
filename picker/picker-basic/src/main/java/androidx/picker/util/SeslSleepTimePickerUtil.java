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

package androidx.picker.util;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.util.Locale;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class SeslSleepTimePickerUtil {
    private static final float DEGREE_BIAS = 2.5f;
    private static final float POP_VIEW_NORMAL_HEIGHT = 420.0f;
    private static final String TAG = "SeslSleepPickerUtil";
    private static final float TOTAL_DEGREE = 360.0f;
    private static final float TOTAL_MINUTES = 1440.0f;
    private static final int INTERNAL_INDEX_OFFSET = 50024;


    public static float convertToTime(float point) {
        return (((((((int) (point / DEGREE_BIAS)) * DEGREE_BIAS) - 270.0f) + TOTAL_DEGREE) % TOTAL_DEGREE) * TOTAL_MINUTES) / TOTAL_DEGREE;
    }

    @Nullable
    public static Typeface getFontFromOpenTheme(@NonNull Context context) {
        String string = Settings.System.getString(context.getContentResolver(), "theme_font_clock");
        if (string != null) {
            try {
                if (TextUtils.isEmpty(string)) {
                    return null;
                }
                return Typeface.createFromFile(string);
            } catch (RuntimeException e) {
                e.printStackTrace();
                return null;
            }
        }
        return null;
    }

    public static boolean needBedTimePickerAdjustment(float screenSize) {
        return screenSize < POP_VIEW_NORMAL_HEIGHT;
    }


    public static void setDefaultTextSize(@NonNull Context context, TextView textView, float f) {
        setTextSize(context, textView, f, 1.0f);
    }

    public static void setLargeTextSize(@NonNull Context context, TextView[] textViewArr,
            float fontScale) {
        if (context.getResources().getConfiguration().fontScale > fontScale) {
            for (TextView textView : textViewArr) {
                if (textView != null) {
                    textView.setTextSize(1,
                            (textView.getTextSize() / context.getResources().getDisplayMetrics().scaledDensity) * fontScale);
                }
            }
        }
    }

    private static void setTextSize(Context context, TextView textView, float f, float f2) {
        if (textView != null) {
            float fontScale = context.getResources().getConfiguration().fontScale;
            float f4 = f / fontScale;
            Log.d(TAG, "setLargeTextSize fontScale : " + fontScale + ", " + f + ", " + f4);
            if (fontScale <= f2) {
                f2 = fontScale;
            }
            setTextSize(textView, f4 * f2);
        }
    }

    public static void setTextSize(@Nullable TextView textView, float f) {
        if (textView != null) {
            try {
                textView.setTextSize(0, (float) Math.ceil(f));
            } catch (Exception unused) {
                Log.e(TAG, "Exception");
            }
        }
    }


    public static boolean isLeftAmPm() {
        return DateFormat.getBestDateTimePattern(Locale.getDefault(), "hm").startsWith("a");
    }

    public static String toDigitString(int i) {
        return String.format("%d", i);
    }

    public static String toTwoDigitString(int i) {
        return String.format("%02d", i);
    }

    public static boolean getHourFormatData(boolean firstCharUpperCase) {
        String bestDateTimePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(),
                firstCharUpperCase ? "Hm" : "hm");
        int length = bestDateTimePattern.length();
        for (int index = 0; index < length; index++) {
            char charAt = bestDateTimePattern.charAt(index);
            if (charAt == 'H' || charAt == 'h' || charAt == 'K' || charAt == 'k') {
                int nextCharIndex = index + 1;
                return nextCharIndex < length && charAt == bestDateTimePattern.charAt(nextCharIndex);
            }
        }
        return false;
    }

    @NonNull
    public static String getTimeSeparatorText(@NonNull Context context) {
        String finalTimeSeparator;
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
        return Locale.getDefault().equals(Locale.CANADA_FRENCH) ? ":" :
                finalTimeSeparator.replace("'", "");
    }

    public static void performHapticFeedback(View view, int i) {
        if (Build.VERSION.SDK_INT > 28) {
            view.performHapticFeedback(i + INTERNAL_INDEX_OFFSET);
        } else {
            view.performHapticFeedback(1 + INTERNAL_INDEX_OFFSET);
        }
    }
}
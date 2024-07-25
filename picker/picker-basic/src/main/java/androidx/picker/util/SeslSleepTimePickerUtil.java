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

import android.text.format.DateFormat;

import androidx.annotation.RestrictTo;

import java.util.Locale;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class SeslSleepTimePickerUtil {

    public static float pointToTime(float point) {
        return (((((((int) (point / 2.5f)) * 2.5f) - 270.0f) + 360.0f) % 360.0f) * 1440.0f) / 360.0f;
    }

    public static boolean isSmallDisplay(float screenSize) {
        return screenSize < 420.0f;
    }

    public static boolean isMorning() {
        return DateFormat.getBestDateTimePattern(Locale.getDefault(), "hm").startsWith("a");
    }

    public static String formatToInteger(int i) {
        return String.format("%d", i);
    }

    public static String formatTwoDigitNumber(int i) {
        return String.format("%02d", i);
    }

    public static boolean hasDuplicateHourMarkers() {
        String bestDateTimePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "hm");
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

}
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

import android.graphics.Typeface;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

@RestrictTo(RestrictTo.Scope.LIBRARY)
public class SeslDatePickerFontUtil {
    @NonNull
    public static Typeface getRegularFontTypeface() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(Typeface.create("sec", Typeface.NORMAL), 400, false);
        }else{
            return Typeface.create("sec-roboto-light", Typeface.NORMAL);
        }
    }

    @NonNull
    public static Typeface getBoldFontTypeface() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(Typeface.create("sec", Typeface.NORMAL), 600, false);
        }else{
            return Typeface.create("sec-roboto-light", Typeface.BOLD);
        }
    }
}

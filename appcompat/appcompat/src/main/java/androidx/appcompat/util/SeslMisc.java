/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.appcompat.util;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP_PREFIX;

import android.content.Context;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.appcompat.R;

/*
 * Original code by Samsung, all rights reserved to the original author.
 */

/**
 * Samsung Misc class.
 */
@RestrictTo(LIBRARY_GROUP_PREFIX)
public class SeslMisc {
    public static boolean isDefaultTheme(@NonNull Context context) {
        return TextUtils.isEmpty(Settings.System.getString(context.getContentResolver(), "current_sec_active_themepackage"));
    }

    public static boolean isLightTheme(@NonNull Context context) {
        TypedValue typedValue = new TypedValue();
        context.getTheme().resolveAttribute(R.attr.isLightTheme, typedValue, true);
        return typedValue.data != 0;
    }

    public static float computeEasingFactor(float density, float pixelsPerSecond, float distance, float maxDistance) {
        return (distance - (density * pixelsPerSecond)) / maxDistance;
    }

}

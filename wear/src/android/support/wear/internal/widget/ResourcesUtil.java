/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.support.wear.internal.widget;

import android.content.Context;
import android.support.annotation.FractionRes;
import android.support.annotation.RestrictTo;
import android.support.annotation.RestrictTo.Scope;

/**
 * Utility methods to help with resource calculations.
 *
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public final class ResourcesUtil {

    /**
     * Returns the screen width in pixels.
     */
    public static int getScreenWidthPx(Context context) {
        return context.getResources().getDisplayMetrics().widthPixels;
    }

    /**
     * Returns the screen height in pixels.
     */
    public static int getScreenHeightPx(Context context) {
        return context.getResources().getDisplayMetrics().heightPixels;
    }

    /**
     * Returns the number of pixels equivalent to the percentage of {@code resId} to the current
     * screen.
     */
    public static int getFractionOfScreenPx(Context context, int screenPx, @FractionRes int resId) {
        float marginPercent = context.getResources().getFraction(resId, 1, 1);
        return (int) (marginPercent * screenPx);
    }

    private ResourcesUtil() {}
}

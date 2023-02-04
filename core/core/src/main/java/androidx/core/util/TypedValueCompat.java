/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.core.util;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;
import static android.util.TypedValue.COMPLEX_UNIT_IN;
import static android.util.TypedValue.COMPLEX_UNIT_MM;
import static android.util.TypedValue.COMPLEX_UNIT_PT;
import static android.util.TypedValue.COMPLEX_UNIT_PX;
import static android.util.TypedValue.COMPLEX_UNIT_SP;

import android.os.Build;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.DoNotInline;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

/**
 * Container for a dynamically typed data value.  Primarily used with
 * {@link android.content.res.Resources} for holding resource values.
 *
 * <p>Used to convert between dimension values like DP and SP to pixels, and vice versa.
 */
public class TypedValueCompat {
    private static final float INCHES_PER_PT = (1.0f / 72);
    private static final float INCHES_PER_MM = (1.0f / 25.4f);

    private TypedValueCompat() {}

    /**
     * Converts a pixel value to the given dimension, e.g. PX to DP.
     *
     * <p>This is the inverse of {@link TypedValue#applyDimension(int, float, DisplayMetrics)}
     *
     * @param unitToConvertTo The unit to convert to.
     * @param pixelValue The raw pixels value to convert from.
     * @param metrics Current display metrics to use in the conversion --
     *                supplies display density and scaling information.
     *
     * @return A dimension value equivalent to the given number of pixels
     * @throws IllegalArgumentException if unitToConvertTo is not valid.
     */
    public static float deriveDimension(
            int unitToConvertTo,
            float pixelValue,
            @NonNull DisplayMetrics metrics) {
        if (Build.VERSION.SDK_INT >= 34) {
            return Api34Impl.deriveDimension(unitToConvertTo, pixelValue, metrics);
        }

        switch (unitToConvertTo) {
            case COMPLEX_UNIT_PX:
                return pixelValue;
            case COMPLEX_UNIT_DIP: {
                // Avoid divide-by-zero, and return 0 since that's what the inverse function will do
                if (metrics.density == 0) {
                    return 0;
                }
                return pixelValue / metrics.density;
            }
            case COMPLEX_UNIT_SP:
                // Versions earlier than U don't get the fancy non-linear scaling
                if (metrics.scaledDensity == 0) {
                    return 0;
                }
                return pixelValue / metrics.scaledDensity;
            case COMPLEX_UNIT_PT: {
                if (metrics.xdpi == 0) {
                    return 0;
                }
                return pixelValue / metrics.xdpi / INCHES_PER_PT;
            }
            case COMPLEX_UNIT_IN: {
                if (metrics.xdpi == 0) {
                    return 0;
                }
                return pixelValue / metrics.xdpi;
            }
            case COMPLEX_UNIT_MM: {
                if (metrics.xdpi == 0) {
                    return 0;
                }
                return pixelValue / metrics.xdpi / INCHES_PER_MM;
            }
            default:
                throw new IllegalArgumentException("Invalid unitToConvertTo " + unitToConvertTo);
        }
    }

    @RequiresApi(34)
    private static class Api34Impl {
        @DoNotInline
        public static float deriveDimension(int unitToConvertTo, float pixelValue,
                DisplayMetrics metrics) {
            return TypedValue.deriveDimension(unitToConvertTo, pixelValue, metrics);
        }
    }
}

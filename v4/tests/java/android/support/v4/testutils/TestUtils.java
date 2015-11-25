/*
 * Copyright (C) 2015 The Android Open Source Project
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


package android.support.v4.testutils;

import java.lang.IllegalArgumentException;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.util.DisplayMetrics;
import android.util.TypedValue;

public class TestUtils {
    /**
     * Converts the specified value from dips to pixels for use as view size.
     */
    public static int convertSizeDipsToPixels(DisplayMetrics displayMetrics, int dipValue) {
        // Round to the nearest int value. This follows the logic in
        // TypedValue.complexToDimensionPixelSize
        final int res = (int) (dipValue * displayMetrics.density + 0.5f);
        if (res != 0) {
            return res;
        }
        if (dipValue == 0) {
            return 0;
        }
        if (dipValue > 0) {
            return 1;
        }
        return -1;
    }

    /**
     * Converts the specified value from dips to pixels for use as view offset.
     */
    public static int convertOffsetDipsToPixels(DisplayMetrics displayMetrics, int dipValue) {
        // Round to the nearest int value.
        return (int) (dipValue * displayMetrics.density);
    }

    /**
     * Returns <code>true</code> iff all the pixels in the specified drawable are of the same
     * specified color.
     */
    public static boolean areAllPixelsOfColor(@NonNull Drawable drawable, @ColorInt int color) {
        int drawableWidth = drawable.getIntrinsicWidth();
        int drawableHeight = drawable.getIntrinsicHeight();

        if ((drawableWidth <= 0) || (drawableHeight <= 0)) {
            throw new IllegalArgumentException("Drawable must be configured to have non-zero size");
        }

        // Create a bitmap
        Bitmap bitmap = Bitmap.createBitmap(drawableWidth, drawableHeight, Bitmap.Config.ARGB_8888);
        // Create a canvas that wraps the bitmap
        Canvas canvas = new Canvas(bitmap);
        // Configure the drawable to have bounds that match its intrinsic size
        drawable.setBounds(0, 0, drawableWidth, drawableHeight);
        // And ask the drawable to draw itself to the canvas / bitmap
        drawable.draw(canvas);

        try {
            int[] rowPixels = new int[drawableWidth];
            for (int row = 0; row < drawableHeight; row++) {
                bitmap.getPixels(rowPixels, 0, drawableWidth, 0, row, drawableWidth, 1);
                for (int column = 0; column < drawableWidth; column++) {
                    if (rowPixels[column] != color) {
                        return false;
                    }
                }
            }

            return true;
        } finally {
            bitmap.recycle();
        }
    }
}
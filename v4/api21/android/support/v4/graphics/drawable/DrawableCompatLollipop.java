/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.support.v4.graphics.drawable;

import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.GradientDrawable;

/**
 * Implementation of drawable compatibility that can call L APIs.
 */
class DrawableCompatLollipop {

    public static void setHotspot(Drawable drawable, float x, float y) {
        drawable.setHotspot(x, y);
    }

    public static void setHotspotBounds(Drawable drawable, int left, int top,
            int right, int bottom) {
        drawable.setHotspotBounds( left, top, right, bottom);
    }

    public static void setTint(Drawable drawable, int tint) {
        drawable.setTint(tint);
    }

    public static void setTintList(Drawable drawable, ColorStateList tint) {
        drawable.setTintList(tint);
    }

    public static void setTintMode(Drawable drawable, PorterDuff.Mode tintMode) {
        drawable.setTintMode(tintMode);
    }

    public static Drawable wrapForTinting(Drawable drawable) {
        return new DrawableWrapperLollipop(drawable, shouldForceCompatTinting(drawable));
    }

    private static boolean shouldForceCompatTinting(Drawable drawable) {
        // GradientDrawable on Lollipop does not support tinting, so we'll use our compatible
        // functionality instead. We also do the same for DrawableContainers since they may
        // contain GradientDrawable instances.
        return drawable instanceof GradientDrawable || drawable instanceof DrawableContainer;
    }
}

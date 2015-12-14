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

package android.support.v4.content.res;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import static android.os.Build.VERSION.SDK_INT;

/**
 * Helper for accessing features in {@link android.content.res.Resources}
 * introduced after API level 4 in a backwards compatible fashion.
 */
public final class ResourcesCompat {
    /**
     * Return a drawable object associated with a particular resource ID and
     * styled for the specified theme. Various types of objects will be
     * returned depending on the underlying resource -- for example, a solid
     * color, PNG image, scalable image, etc.
     * <p>
     * Prior to API level 21, the theme will not be applied and this method
     * simply calls through to {@link Resources#getDrawable(int)}.
     *
     * @param id The desired resource identifier, as generated by the aapt
     *           tool. This integer encodes the package, type, and resource
     *           entry. The value 0 is an invalid identifier.
     * @param theme The theme used to style the drawable attributes, may be
     *              {@code null}.
     * @return Drawable An object that can be used to draw this resource.
     * @throws NotFoundException Throws NotFoundException if the given ID does
     *         not exist.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static Drawable getDrawable(@NonNull Resources res, @DrawableRes int id,
            @Nullable Theme theme) throws NotFoundException {
        if (SDK_INT >= 21) {
            return ResourcesCompatApi21.getDrawable(res, id, theme);
        } else {
            return res.getDrawable(id);
        }
    }


    /**
     * Return a drawable object associated with a particular resource ID for
     * the given screen density in DPI and styled for the specified theme.
     * <p>
     * Prior to API level 15, the theme and density will not be applied and
     * this method simply calls through to {@link Resources#getDrawable(int)}.
     * <p>
     * Prior to API level 21, the theme will not be applied and this method
     * calls through to Resources#getDrawableForDensity(int, int).
     *
     * @param id The desired resource identifier, as generated by the aapt
     *           tool. This integer encodes the package, type, and resource
     *           entry. The value 0 is an invalid identifier.
     * @param density The desired screen density indicated by the resource as
     *                found in {@link android.util.DisplayMetrics}.
     * @param theme The theme used to style the drawable attributes, may be
     *              {@code null}.
     * @return Drawable An object that can be used to draw this resource.
     * @throws NotFoundException Throws NotFoundException if the given ID does
     *         not exist.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static Drawable getDrawableForDensity(@NonNull Resources res, @DrawableRes int id,
            int density, @Nullable Theme theme) throws NotFoundException {
        if (SDK_INT >= 21) {
            return ResourcesCompatApi21.getDrawableForDensity(res, id, density, theme);
        } else if (SDK_INT >= 15) {
            return ResourcesCompatIcsMr1.getDrawableForDensity(res, id, density);
        } else {
            return res.getDrawable(id);
        }
    }

    /**
     * Returns a themed color integer associated with a particular resource ID.
     * If the resource holds a complex {@link ColorStateList}, then the default
     * color from the set is returned.
     * <p>
     * Prior to API level 23, the theme will not be applied and this method
     * calls through to {@link Resources#getColor(int)}.
     *
     * @param id The desired resource identifier, as generated by the aapt
     *           tool. This integer encodes the package, type, and resource
     *           entry. The value 0 is an invalid identifier.
     * @param theme The theme used to style the color attributes, may be
     *              {@code null}.
     * @return A single color value in the form {@code 0xAARRGGBB}.
     * @throws NotFoundException Throws NotFoundException if the given ID does
     *         not exist.
     */
    @ColorInt
    @SuppressWarnings("deprecation")
    public static int getColor(@NonNull Resources res, @ColorRes int id, @Nullable Theme theme)
            throws NotFoundException {
        if (SDK_INT >= 23) {
            return ResourcesCompatApi23.getColor(res, id, theme);
        } else {
            return res.getColor(id);
        }
    }

    /**
     * Returns a themed color state list associated with a particular resource
     * ID. The resource may contain either a single raw color value or a
     * complex {@link ColorStateList} holding multiple possible colors.
     * <p>
     * Prior to API level 23, the theme will not be applied and this method
     * calls through to {@link Resources#getColorStateList(int)}.
     *
     * @param id The desired resource identifier of a {@link ColorStateList},
     *           as generated by the aapt tool. This integer encodes the
     *           package, type, and resource entry. The value 0 is an invalid
     *           identifier.
     * @param theme The theme used to style the color attributes, may be
     *              {@code null}.
     * @return A themed ColorStateList object containing either a single solid
     *         color or multiple colors that can be selected based on a state.
     * @throws NotFoundException Throws NotFoundException if the given ID does
     *         not exist.
     */
    @Nullable
    @SuppressWarnings("deprecation")
    public static ColorStateList getColorStateList(@NonNull Resources res, @ColorRes int id,
            @Nullable Theme theme) throws NotFoundException {
        if (SDK_INT >= 23) {
            return ResourcesCompatApi23.getColorStateList(res, id, theme);
        } else {
            return res.getColorStateList(id);
        }
    }

    private ResourcesCompat() {}
}

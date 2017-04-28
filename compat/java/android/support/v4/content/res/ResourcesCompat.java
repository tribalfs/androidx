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

import static android.os.Build.VERSION.SDK_INT;
import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.content.res.Resources.Theme;
import android.content.res.XmlResourceParser;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.DrawableRes;
import android.support.annotation.FontRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.v4.content.res.FontResourcesParserCompat.FamilyResourceEntry;
import android.support.v4.graphics.TypefaceCompat;
import android.support.v4.graphics.TypefaceCompat.TypefaceHolder;
import android.support.v4.os.BuildCompat;
import android.util.Log;
import android.util.TypedValue;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Helper for accessing features in {@link android.content.res.Resources}
 * introduced after API level 4 in a backwards compatible fashion.
 */
public final class ResourcesCompat {
    private static final String TAG = "ResourcesCompat";

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
            return res.getDrawable(id, theme);
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
            return res.getDrawableForDensity(id, density, theme);
        } else if (SDK_INT >= 15) {
            return res.getDrawableForDensity(id, density);
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
            return res.getColor(id, theme);
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
            return res.getColorStateList(id, theme);
        } else {
            return res.getColorStateList(id);
        }
    }

    /**
     * Returns a font Typeface associated with a particular resource ID.
     * <p>
     * Prior to API level 23, font resources with more than one font in a family will only load the
     * first font in that family.
     *
     * @param context A context to retrieve the Resources from.
     * @param id The desired resource identifier of a {@link Typeface},
     *           as generated by the aapt tool. This integer encodes the
     *           package, type, and resource entry. The value 0 is an invalid
     *           identifier.
     * @return A font Typeface object.
     * @throws NotFoundException Throws NotFoundException if the given ID does
     *         not exist.
     */
    @Nullable
    public static Typeface getFont(@NonNull Context context, @FontRes int id)
            throws NotFoundException {
        if (context.isRestricted()) {
            return null;
        }
        if (BuildCompat.isAtLeastO()) {
            // Use framework support.
            return context.getResources().getFont(id);
        }
        return loadFont(context, id, Typeface.NORMAL).getTypeface();
    }

    /** @hide */
    @RestrictTo(LIBRARY_GROUP)
    public static TypefaceHolder getFont(@NonNull Context context, @FontRes int id, int style)
            throws NotFoundException {
        if (context.isRestricted()) {
            return null;
        }
        if (BuildCompat.isAtLeastO()) {
            // Use framework support.
            Typeface typeface = context.getResources().getFont(id);
            return new TypefaceHolder(
                Typeface.create(typeface, style), (style & Typeface.BOLD) == 0 ? 400 : 700,
                (style & Typeface.ITALIC) != 0);
        }
        return loadFont(context, id, style);
    }

    private static TypefaceHolder loadFont(@NonNull Context context, int id, int style) {
        final TypedValue value = new TypedValue();
        final Resources resources = context.getResources();
        resources.getValue(id, value, true);
        TypefaceHolder typeface = loadFont(context, resources, value, id, style);
        if (typeface != null) {
            return typeface;
        }
        throw new NotFoundException("Font resource ID #0x"
                + Integer.toHexString(id));
    }

    private static TypefaceHolder loadFont(
            @NonNull Context context, Resources wrapper, TypedValue value, int id, int style) {
        if (value.string == null) {
            throw new NotFoundException("Resource \"" + wrapper.getResourceName(id) + "\" ("
                    + Integer.toHexString(id) + ") is not a Font: " + value);
        }

        TypefaceHolder cached = TypefaceCompat.findFromCache(wrapper, id, style);
        if (cached != null) {
            return cached;
        }

        final String file = value.string.toString();
        try {
            if (file.toLowerCase().endsWith(".xml")) {
                final XmlResourceParser rp = wrapper.getXml(id);
                final FamilyResourceEntry familyEntry =
                        FontResourcesParserCompat.parse(rp, wrapper);
                if (familyEntry == null) {
                    Log.e(TAG, "Failed to find font-family tag");
                    return null;
                }
                return TypefaceCompat.createFromResourcesFamilyXml(
                        context, familyEntry, wrapper, id, style);
            }
            return TypefaceCompat.createFromResourcesFontFile(context, wrapper, id, style);
        } catch (XmlPullParserException e) {
            Log.e(TAG, "Failed to parse xml resource " + file, e);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read xml resource " + file, e);
        }
        return null;
    }

    private ResourcesCompat() {}
}

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
package android.support.v4.content.res;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.support.annotation.AnyRes;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.annotation.StyleableRes;
import android.util.TypedValue;

import org.xmlpull.v1.XmlPullParser;

/**
 * Compat methods for accessing TypedArray values.
 *
 * @hide
 */
@RestrictTo(LIBRARY_GROUP)
public class TypedArrayUtils {

    private static final String NAMESPACE = "http://schemas.android.com/apk/res/android";

    /**
     * @return Whether the current node ofthe  {@link XmlPullParser} has an attribute with the
     * specified {@code attrName}.
     */
    public static boolean hasAttribute(@NonNull XmlPullParser parser, @NonNull String attrName) {
        return parser.getAttributeValue(NAMESPACE, attrName) != null;
    }

    /**
     * Retrieves a float attribute value. In addition to the styleable resource ID, we also make
     * sure that the attribute name matches.
     *
     * @return a float value in the {@link TypedArray} with the specified {@code resId}, or
     * {@code defaultValue} if it does not exist.
     */
    public static float getNamedFloat(@NonNull TypedArray a, @NonNull XmlPullParser parser,
            @NonNull String attrName, @StyleableRes int resId, float defaultValue) {
        final boolean hasAttr = hasAttribute(parser, attrName);
        if (!hasAttr) {
            return defaultValue;
        } else {
            return a.getFloat(resId, defaultValue);
        }
    }

    /**
     * Retrieves a boolean attribute value. In addition to the styleable resource ID, we also make
     * sure that the attribute name matches.
     *
     * @return a boolean value in the {@link TypedArray} with the specified {@code resId}, or
     * {@code defaultValue} if it does not exist.
     */
    public static boolean getNamedBoolean(@NonNull TypedArray a, @NonNull XmlPullParser parser,
            String attrName, @StyleableRes int resId, boolean defaultValue) {
        final boolean hasAttr = hasAttribute(parser, attrName);
        if (!hasAttr) {
            return defaultValue;
        } else {
            return a.getBoolean(resId, defaultValue);
        }
    }

    /**
     * Retrieves an int attribute value. In addition to the styleable resource ID, we also make
     * sure that the attribute name matches.
     *
     * @return an int value in the {@link TypedArray} with the specified {@code resId}, or
     * {@code defaultValue} if it does not exist.
     */
    public static int getNamedInt(@NonNull TypedArray a, @NonNull XmlPullParser parser,
            String attrName, @StyleableRes int resId, int defaultValue) {
        final boolean hasAttr = hasAttribute(parser, attrName);
        if (!hasAttr) {
            return defaultValue;
        } else {
            return a.getInt(resId, defaultValue);
        }
    }

    /**
     * Retrieves a color attribute value. In addition to the styleable resource ID, we also make
     * sure that the attribute name matches.
     *
     * @return a color value in the {@link TypedArray} with the specified {@code resId}, or
     * {@code defaultValue} if it does not exist.
     */
    @ColorInt
    public static int getNamedColor(@NonNull TypedArray a, @NonNull XmlPullParser parser,
            String attrName, @StyleableRes int resId, @ColorInt int defaultValue) {
        final boolean hasAttr = hasAttribute(parser, attrName);
        if (!hasAttr) {
            return defaultValue;
        } else {
            return a.getColor(resId, defaultValue);
        }
    }

    /**
     * Retrieves a resource ID attribute value. In addition to the styleable resource ID, we also
     * make sure that the attribute name matches.
     *
     * @return a resource ID value in the {@link TypedArray} with the specified {@code resId}, or
     * {@code defaultValue} if it does not exist.
     */
    @AnyRes
    public static int getNamedResourceId(@NonNull TypedArray a, @NonNull XmlPullParser parser,
            String attrName, @StyleableRes int resId, @AnyRes int defaultValue) {
        final boolean hasAttr = hasAttribute(parser, attrName);
        if (!hasAttr) {
            return defaultValue;
        } else {
            return a.getResourceId(resId, defaultValue);
        }
    }

    /**
     * Retrieves a string attribute value. In addition to the styleable resource ID, we also
     * make sure that the attribute name matches.
     *
     * @return a string value in the {@link TypedArray} with the specified {@code resId}, or
     * null if it does not exist.
     */
    public static String getNamedString(@NonNull TypedArray a, @NonNull XmlPullParser parser,
            String attrName, @StyleableRes int resId) {
        final boolean hasAttr = hasAttribute(parser, attrName);
        if (!hasAttr) {
            return null;
        } else {
            return a.getString(resId);
        }
    }

    /**
     * @return a boolean value of {@code index}. If it does not exist, a boolean value of
     * {@code fallbackIndex}. If it still does not exist, {@code defaultValue}.
     */
    public static boolean getBoolean(TypedArray a, @StyleableRes int index,
            @StyleableRes int fallbackIndex, boolean defaultValue) {
        boolean val = a.getBoolean(fallbackIndex, defaultValue);
        return a.getBoolean(index, val);
    }

    /**
     * @return a drawable value of {@code index}. If it does not exist, a drawable value of
     * {@code fallbackIndex}. If it still does not exist, {@code null}.
     */
    public static Drawable getDrawable(TypedArray a, @StyleableRes int index,
            @StyleableRes int fallbackIndex) {
        Drawable val = a.getDrawable(index);
        if (val == null) {
            val = a.getDrawable(fallbackIndex);
        }
        return val;
    }

    /**
     * @return an int value of {@code index}. If it does not exist, an int value of
     * {@code fallbackIndex}. If it still does not exist, {@code defaultValue}.
     */
    public static int getInt(TypedArray a, @StyleableRes int index,
            @StyleableRes int fallbackIndex, int defaultValue) {
        int val = a.getInt(fallbackIndex, defaultValue);
        return a.getInt(index, val);
    }

    /**
     * @return a resource ID value of {@code index}. If it does not exist, a resource ID value of
     * {@code fallbackIndex}. If it still does not exist, {@code defaultValue}.
     */
    @AnyRes
    public static int getResourceId(TypedArray a, @StyleableRes int index,
            @StyleableRes int fallbackIndex, @AnyRes int defaultValue) {
        int val = a.getResourceId(fallbackIndex, defaultValue);
        return a.getResourceId(index, val);
    }

    /**
     * @return a string value of {@code index}. If it does not exist, a string value of
     * {@code fallbackIndex}. If it still does not exist, {@code null}.
     */
    public static String getString(TypedArray a, @StyleableRes int index,
            @StyleableRes int fallbackIndex) {
        String val = a.getString(index);
        if (val == null) {
            val = a.getString(fallbackIndex);
        }
        return val;
    }

    /**
     * Retrieves a text attribute value with the specified fallback ID.
     *
     * @return a text value of {@code index}. If it does not exist, a text value of
     * {@code fallbackIndex}. If it still does not exist, {@code null}.
     */
    public static CharSequence getText(TypedArray a, @StyleableRes int index,
            @StyleableRes int fallbackIndex) {
        CharSequence val = a.getText(index);
        if (val == null) {
            val = a.getText(fallbackIndex);
        }
        return val;
    }

    /**
     * Retrieves a string array attribute value with the specified fallback ID.
     *
     * @return a string array value of {@code index}. If it does not exist, a string array value
     * of {@code fallbackIndex}. If it still does not exist, {@code null}.
     */
    public static CharSequence[] getTextArray(TypedArray a, @StyleableRes int index,
            @StyleableRes int fallbackIndex) {
        CharSequence[] val = a.getTextArray(index);
        if (val == null) {
            val = a.getTextArray(fallbackIndex);
        }
        return val;
    }

    /**
     * @return The resource ID value in the {@code context} specified by {@code attr}. If it does
     * not exist, {@code fallbackAttr}.
     */
    public static int getAttr(Context context, int attr, int fallbackAttr) {
        TypedValue value = new TypedValue();
        context.getTheme().resolveAttribute(attr, value, true);
        if (value.resourceId != 0) {
            return attr;
        }
        return fallbackAttr;
    }
}

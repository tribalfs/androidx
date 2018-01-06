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

package android.support.v4.widget;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.DrawableRes;
import android.support.annotation.IntDef;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.support.annotation.RestrictTo;
import android.support.annotation.StyleRes;
import android.support.v4.os.BuildCompat;
import android.text.Editable;
import android.util.Log;
import android.util.TypedValue;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Helper for accessing features in {@link TextView}.
 */
public final class TextViewCompat {

    /**
     * The TextView does not auto-size text (default).
     */
    public static final int AUTO_SIZE_TEXT_TYPE_NONE = TextView.AUTO_SIZE_TEXT_TYPE_NONE;

    /**
     * The TextView scales text size both horizontally and vertically to fit within the
     * container.
     */
    public static final int AUTO_SIZE_TEXT_TYPE_UNIFORM = TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM;

    /** @hide */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef({AUTO_SIZE_TEXT_TYPE_NONE, AUTO_SIZE_TEXT_TYPE_UNIFORM})
    @Retention(RetentionPolicy.SOURCE)
    public @interface AutoSizeTextType {}

    // Hide constructor
    private TextViewCompat() {}

    static class TextViewCompatBaseImpl {
        private static final String LOG_TAG = "TextViewCompatBase";
        private static final int LINES = 1;

        private static Field sMaximumField;
        private static boolean sMaximumFieldFetched;
        private static Field sMaxModeField;
        private static boolean sMaxModeFieldFetched;

        private static Field sMinimumField;
        private static boolean sMinimumFieldFetched;
        private static Field sMinModeField;
        private static boolean sMinModeFieldFetched;

        public void setCompoundDrawablesRelative(@NonNull TextView textView,
                @Nullable Drawable start, @Nullable Drawable top, @Nullable Drawable end,
                @Nullable Drawable bottom) {
            textView.setCompoundDrawables(start, top, end, bottom);
        }

        public void setCompoundDrawablesRelativeWithIntrinsicBounds(@NonNull TextView textView,
                @Nullable Drawable start, @Nullable Drawable top, @Nullable Drawable end,
                @Nullable Drawable bottom) {
            textView.setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom);
        }

        public void setCompoundDrawablesRelativeWithIntrinsicBounds(@NonNull TextView textView,
                @DrawableRes int start, @DrawableRes int top, @DrawableRes int end,
                @DrawableRes int bottom) {
            textView.setCompoundDrawablesWithIntrinsicBounds(start, top, end, bottom);
        }

        private static Field retrieveField(String fieldName) {
            Field field = null;
            try {
                field = TextView.class.getDeclaredField(fieldName);
                field.setAccessible(true);
            } catch (NoSuchFieldException e) {
                Log.e(LOG_TAG, "Could not retrieve " + fieldName + " field.");
            }
            return field;
        }

        private static int retrieveIntFromField(Field field, TextView textView) {
            try {
                return field.getInt(textView);
            } catch (IllegalAccessException e) {
                Log.d(LOG_TAG, "Could not retrieve value of " + field.getName() + " field.");
            }
            return -1;
        }

        public int getMaxLines(TextView textView) {
            if (!sMaxModeFieldFetched) {
                sMaxModeField = retrieveField("mMaxMode");
                sMaxModeFieldFetched = true;
            }
            if (sMaxModeField != null && retrieveIntFromField(sMaxModeField, textView) == LINES) {
                // If the max mode is using lines, we can grab the maximum value
                if (!sMaximumFieldFetched) {
                    sMaximumField = retrieveField("mMaximum");
                    sMaximumFieldFetched = true;
                }
                if (sMaximumField != null) {
                    return retrieveIntFromField(sMaximumField, textView);
                }
            }
            return -1;
        }

        public int getMinLines(TextView textView) {
            if (!sMinModeFieldFetched) {
                sMinModeField = retrieveField("mMinMode");
                sMinModeFieldFetched = true;
            }
            if (sMinModeField != null && retrieveIntFromField(sMinModeField, textView) == LINES) {
                // If the min mode is using lines, we can grab the maximum value
                if (!sMinimumFieldFetched) {
                    sMinimumField = retrieveField("mMinimum");
                    sMinimumFieldFetched = true;
                }
                if (sMinimumField != null) {
                    return retrieveIntFromField(sMinimumField, textView);
                }
            }
            return -1;
        }

        @SuppressWarnings("deprecation")
        public void setTextAppearance(TextView textView, @StyleRes int resId) {
            textView.setTextAppearance(textView.getContext(), resId);
        }

        public Drawable[] getCompoundDrawablesRelative(@NonNull TextView textView) {
            return textView.getCompoundDrawables();
        }

        public void setAutoSizeTextTypeWithDefaults(TextView textView, int autoSizeTextType) {
            if (textView instanceof AutoSizeableTextView) {
                ((AutoSizeableTextView) textView).setAutoSizeTextTypeWithDefaults(autoSizeTextType);
            }
        }

        public void setAutoSizeTextTypeUniformWithConfiguration(
                TextView textView,
                int autoSizeMinTextSize,
                int autoSizeMaxTextSize,
                int autoSizeStepGranularity,
                int unit) throws IllegalArgumentException {
            if (textView instanceof AutoSizeableTextView) {
                ((AutoSizeableTextView) textView).setAutoSizeTextTypeUniformWithConfiguration(
                        autoSizeMinTextSize, autoSizeMaxTextSize, autoSizeStepGranularity, unit);
            }
        }

        public void setAutoSizeTextTypeUniformWithPresetSizes(TextView textView,
                @NonNull int[] presetSizes, int unit) throws IllegalArgumentException {
            if (textView instanceof AutoSizeableTextView) {
                ((AutoSizeableTextView) textView).setAutoSizeTextTypeUniformWithPresetSizes(
                        presetSizes, unit);
            }
        }

        public int getAutoSizeTextType(TextView textView) {
            if (textView instanceof AutoSizeableTextView) {
                return ((AutoSizeableTextView) textView).getAutoSizeTextType();
            }
            return AUTO_SIZE_TEXT_TYPE_NONE;
        }

        public int getAutoSizeStepGranularity(TextView textView) {
            if (textView instanceof AutoSizeableTextView) {
                return ((AutoSizeableTextView) textView).getAutoSizeStepGranularity();
            }
            return -1;
        }

        public int getAutoSizeMinTextSize(TextView textView) {
            if (textView instanceof AutoSizeableTextView) {
                return ((AutoSizeableTextView) textView).getAutoSizeMinTextSize();
            }
            return -1;
        }

        public int getAutoSizeMaxTextSize(TextView textView) {
            if (textView instanceof AutoSizeableTextView) {
                return ((AutoSizeableTextView) textView).getAutoSizeMaxTextSize();
            }
            return -1;
        }

        public int[] getAutoSizeTextAvailableSizes(TextView textView) {
            if (textView instanceof AutoSizeableTextView) {
                return ((AutoSizeableTextView) textView).getAutoSizeTextAvailableSizes();
            }
            return new int[0];
        }

        public void setCustomSelectionActionModeCallback(TextView textView,
                ActionMode.Callback callback) {
            textView.setCustomSelectionActionModeCallback(callback);
        }
    }

    @RequiresApi(16)
    static class TextViewCompatApi16Impl extends TextViewCompatBaseImpl {
        @Override
        public int getMaxLines(TextView textView) {
            return textView.getMaxLines();
        }

        @Override
        public int getMinLines(TextView textView) {
            return textView.getMinLines();
        }
    }

    @RequiresApi(17)
    static class TextViewCompatApi17Impl extends TextViewCompatApi16Impl {
        @Override
        public void setCompoundDrawablesRelative(@NonNull TextView textView,
                @Nullable Drawable start, @Nullable Drawable top, @Nullable Drawable end,
                @Nullable Drawable bottom) {
            boolean rtl = textView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            textView.setCompoundDrawables(rtl ? end : start, top, rtl ? start : end, bottom);
        }

        @Override
        public void setCompoundDrawablesRelativeWithIntrinsicBounds(@NonNull TextView textView,
                @Nullable Drawable start, @Nullable Drawable top, @Nullable Drawable end,
                @Nullable Drawable bottom) {
            boolean rtl = textView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            textView.setCompoundDrawablesWithIntrinsicBounds(rtl ? end : start, top,
                    rtl ? start : end,  bottom);
        }

        @Override
        public void setCompoundDrawablesRelativeWithIntrinsicBounds(@NonNull TextView textView,
                @DrawableRes int start, @DrawableRes int top, @DrawableRes int end,
                @DrawableRes int bottom) {
            boolean rtl = textView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            textView.setCompoundDrawablesWithIntrinsicBounds(rtl ? end : start, top,
                    rtl ? start : end, bottom);
        }

        @Override
        public Drawable[] getCompoundDrawablesRelative(@NonNull TextView textView) {
            final boolean rtl = textView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
            final Drawable[] compounds = textView.getCompoundDrawables();
            if (rtl) {
                // If we're on RTL, we need to invert the horizontal result like above
                final Drawable start = compounds[2];
                final Drawable end = compounds[0];
                compounds[0] = start;
                compounds[2] = end;
            }
            return compounds;
        }
    }

    @RequiresApi(18)
    static class TextViewCompatApi18Impl extends TextViewCompatApi17Impl {
        @Override
        public void setCompoundDrawablesRelative(@NonNull TextView textView,
                @Nullable Drawable start, @Nullable Drawable top, @Nullable Drawable end,
                @Nullable Drawable bottom) {
            textView.setCompoundDrawablesRelative(start, top, end, bottom);
        }

        @Override
        public void setCompoundDrawablesRelativeWithIntrinsicBounds(@NonNull TextView textView,
                @Nullable Drawable start, @Nullable Drawable top, @Nullable Drawable end,
                @Nullable Drawable bottom) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom);
        }

        @Override
        public void setCompoundDrawablesRelativeWithIntrinsicBounds(@NonNull TextView textView,
                @DrawableRes int start, @DrawableRes int top, @DrawableRes int end,
                @DrawableRes int bottom) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(start, top, end, bottom);
        }

        @Override
        public Drawable[] getCompoundDrawablesRelative(@NonNull TextView textView) {
            return textView.getCompoundDrawablesRelative();
        }
    }

    @RequiresApi(23)
    static class TextViewCompatApi23Impl extends TextViewCompatApi18Impl {
        @Override
        public void setTextAppearance(@NonNull TextView textView, @StyleRes int resId) {
            textView.setTextAppearance(resId);
        }
    }

    @RequiresApi(26)
    static class TextViewCompatApi26Impl extends TextViewCompatApi23Impl {
        @Override
        public void setCustomSelectionActionModeCallback(final TextView textView,
                final ActionMode.Callback callback) {
            if (Build.VERSION.SDK_INT != Build.VERSION_CODES.O
                    && Build.VERSION.SDK_INT != Build.VERSION_CODES.O_MR1) {
                super.setCustomSelectionActionModeCallback(textView, callback);
                return;
            }


            // A bug in O and O_MR1 causes a number of options for handling the ACTION_PROCESS_TEXT
            // intent after selection to not be displayed in the menu, although they should be.
            // Here we fix this, by removing the menu items created by the framework code, and
            // adding them (and the missing ones) back correctly.
            textView.setCustomSelectionActionModeCallback(new ActionMode.Callback() {
                // This constant should be correlated with its definition in the
                // android.widget.Editor class.
                private static final int MENU_ITEM_ORDER_PROCESS_TEXT_INTENT_ACTIONS_START = 100;

                // References to the MenuBuilder class and its removeItemAt(int) method.
                // Since in most cases the menu instance processed by this callback is going
                // to be a MenuBuilder, we keep these references to avoid querying for them
                // frequently by reflection in recomputeProcessTextMenuItems.
                private Class mMenuBuilderClass;
                private Method mMenuBuilderRemoveItemAtMethod;
                private boolean mCanUseMenuBuilderReferences;
                private boolean mInitializedMenuBuilderReferences = false;

                @Override
                public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                    return callback.onCreateActionMode(mode, menu);
                }

                @Override
                public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                    recomputeProcessTextMenuItems(menu);
                    return callback.onPrepareActionMode(mode, menu);
                }

                @Override
                public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                    return callback.onActionItemClicked(mode, item);
                }

                @Override
                public void onDestroyActionMode(ActionMode mode) {
                    callback.onDestroyActionMode(mode);
                }

                private void recomputeProcessTextMenuItems(final Menu menu) {
                    final Context context = textView.getContext();
                    final PackageManager packageManager = context.getPackageManager();

                    if (!mInitializedMenuBuilderReferences) {
                        mInitializedMenuBuilderReferences = true;
                        try {
                            mMenuBuilderClass =
                                    Class.forName("com.android.internal.view.menu.MenuBuilder");
                            mMenuBuilderRemoveItemAtMethod = mMenuBuilderClass
                                    .getDeclaredMethod("removeItemAt", Integer.TYPE);
                            mCanUseMenuBuilderReferences = true;
                        } catch (ClassNotFoundException | NoSuchMethodException e) {
                            mMenuBuilderClass = null;
                            mMenuBuilderRemoveItemAtMethod = null;
                            mCanUseMenuBuilderReferences = false;
                        }
                    }
                    // Remove the menu items created for ACTION_PROCESS_TEXT handlers.
                    try {
                        final Method removeItemAtMethod =
                                (mCanUseMenuBuilderReferences && mMenuBuilderClass.isInstance(menu))
                                        ? mMenuBuilderRemoveItemAtMethod
                                        : menu.getClass()
                                                .getDeclaredMethod("removeItemAt", Integer.TYPE);
                        for (int i = menu.size() - 1; i >= 0; --i) {
                            final MenuItem item = menu.getItem(i);
                            if (item.getIntent() != null && Intent.ACTION_PROCESS_TEXT
                                    .equals(item.getIntent().getAction())) {
                                removeItemAtMethod.invoke(menu, i);
                            }
                        }
                    } catch (NoSuchMethodException | IllegalAccessException
                            | InvocationTargetException e) {
                        // There is a menu custom implementation used which is not providing
                        // a removeItemAt(int) menu. There is nothing we can do in this case.
                        return;
                    }

                    // Populate the menu again with the ACTION_PROCESS_TEXT handlers.
                    final List<ResolveInfo> supportedActivities =
                            getSupportedActivities(context, packageManager);
                    for (int i = 0; i < supportedActivities.size(); ++i) {
                        final ResolveInfo info = supportedActivities.get(i);
                        menu.add(Menu.NONE, Menu.NONE,
                                MENU_ITEM_ORDER_PROCESS_TEXT_INTENT_ACTIONS_START + i,
                                info.loadLabel(packageManager))
                                .setIntent(createProcessTextIntentForResolveInfo(info, textView))
                                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);
                    }
                }

                private List<ResolveInfo> getSupportedActivities(final Context context,
                        final PackageManager packageManager) {
                    final List<ResolveInfo> supportedActivities = new ArrayList<>();
                    boolean canStartActivityForResult = context instanceof Activity;
                    if (!canStartActivityForResult) {
                        return supportedActivities;
                    }
                    final List<ResolveInfo> unfiltered =
                            packageManager.queryIntentActivities(createProcessTextIntent(), 0);
                    for (ResolveInfo info : unfiltered) {
                        if (isSupportedActivity(info, context)) {
                            supportedActivities.add(info);
                        }
                    }
                    return supportedActivities;
                }

                private boolean isSupportedActivity(final ResolveInfo info, final Context context) {
                    if (context.getPackageName().equals(info.activityInfo.packageName)) {
                        return true;
                    }
                    if (!info.activityInfo.exported) {
                        return false;
                    }
                    return info.activityInfo.permission == null
                            || context.checkSelfPermission(info.activityInfo.permission)
                                == PackageManager.PERMISSION_GRANTED;
                }

                private Intent createProcessTextIntentForResolveInfo(final ResolveInfo info,
                        final TextView textView) {
                    return createProcessTextIntent()
                            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, !isEditable(textView))
                            .setClassName(info.activityInfo.packageName, info.activityInfo.name);
                }

                private boolean isEditable(final TextView textView) {
                    return textView instanceof Editable
                            && textView.onCheckIsTextEditor()
                            && textView.isEnabled();
                }

                private Intent createProcessTextIntent() {
                    return new Intent().setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain");
                }
            });
        }
    }

    @RequiresApi(27)
    static class TextViewCompatApi27Impl extends TextViewCompatApi26Impl {
        @Override
        public void setAutoSizeTextTypeWithDefaults(TextView textView, int autoSizeTextType) {
            textView.setAutoSizeTextTypeWithDefaults(autoSizeTextType);
        }

        @Override
        public void setAutoSizeTextTypeUniformWithConfiguration(
                TextView textView,
                int autoSizeMinTextSize,
                int autoSizeMaxTextSize,
                int autoSizeStepGranularity,
                int unit) throws IllegalArgumentException {
            textView.setAutoSizeTextTypeUniformWithConfiguration(
                    autoSizeMinTextSize, autoSizeMaxTextSize, autoSizeStepGranularity, unit);
        }

        @Override
        public void setAutoSizeTextTypeUniformWithPresetSizes(TextView textView,
                @NonNull int[] presetSizes, int unit) throws IllegalArgumentException {
            textView.setAutoSizeTextTypeUniformWithPresetSizes(presetSizes, unit);
        }

        @Override
        public int getAutoSizeTextType(TextView textView) {
            return textView.getAutoSizeTextType();
        }

        @Override
        public int getAutoSizeStepGranularity(TextView textView) {
            return textView.getAutoSizeStepGranularity();
        }

        @Override
        public int getAutoSizeMinTextSize(TextView textView) {
            return textView.getAutoSizeMinTextSize();
        }

        @Override
        public int getAutoSizeMaxTextSize(TextView textView) {
            return textView.getAutoSizeMaxTextSize();
        }

        @Override
        public int[] getAutoSizeTextAvailableSizes(TextView textView) {
            return textView.getAutoSizeTextAvailableSizes();
        }
    }

    static final TextViewCompatBaseImpl IMPL;

    static {
        if (BuildCompat.isAtLeastOMR1()) {
            IMPL = new TextViewCompatApi27Impl();
        } else if (Build.VERSION.SDK_INT >= 26) {
            IMPL = new TextViewCompatApi26Impl();
        } else if (Build.VERSION.SDK_INT >= 23) {
            IMPL = new TextViewCompatApi23Impl();
        } else if (Build.VERSION.SDK_INT >= 18) {
            IMPL = new TextViewCompatApi18Impl();
        } else if (Build.VERSION.SDK_INT >= 17) {
            IMPL = new TextViewCompatApi17Impl();
        } else if (Build.VERSION.SDK_INT >= 16) {
            IMPL = new TextViewCompatApi16Impl();
        } else {
            IMPL = new TextViewCompatBaseImpl();
        }
    }

    /**
     * Sets the Drawables (if any) to appear to the start of, above, to the end
     * of, and below the text. Use {@code null} if you do not want a Drawable
     * there. The Drawables must already have had {@link Drawable#setBounds}
     * called.
     * <p/>
     * Calling this method will overwrite any Drawables previously set using
     * {@link TextView#setCompoundDrawables} or related methods.
     *
     * @param textView The TextView against which to invoke the method.
     * @attr name android:drawableStart
     * @attr name android:drawableTop
     * @attr name android:drawableEnd
     * @attr name android:drawableBottom
     */
    public static void setCompoundDrawablesRelative(@NonNull TextView textView,
            @Nullable Drawable start, @Nullable Drawable top, @Nullable Drawable end,
            @Nullable Drawable bottom) {
        IMPL.setCompoundDrawablesRelative(textView, start, top, end, bottom);
    }

    /**
     * Sets the Drawables (if any) to appear to the start of, above, to the end
     * of, and below the text. Use {@code null} if you do not want a Drawable
     * there. The Drawables' bounds will be set to their intrinsic bounds.
     * <p/>
     * Calling this method will overwrite any Drawables previously set using
     * {@link TextView#setCompoundDrawables} or related methods.
     *
     * @param textView The TextView against which to invoke the method.
     * @attr name android:drawableStart
     * @attr name android:drawableTop
     * @attr name android:drawableEnd
     * @attr name android:drawableBottom
     */
    public static void setCompoundDrawablesRelativeWithIntrinsicBounds(@NonNull TextView textView,
            @Nullable Drawable start, @Nullable Drawable top, @Nullable Drawable end,
            @Nullable Drawable bottom) {
        IMPL.setCompoundDrawablesRelativeWithIntrinsicBounds(textView, start, top, end, bottom);
    }

    /**
     * Sets the Drawables (if any) to appear to the start of, above, to the end
     * of, and below the text. Use 0 if you do not want a Drawable there. The
     * Drawables' bounds will be set to their intrinsic bounds.
     * <p/>
     * Calling this method will overwrite any Drawables previously set using
     * {@link TextView#setCompoundDrawables} or related methods.
     *
     * @param textView The TextView against which to invoke the method.
     * @param start    Resource identifier of the start Drawable.
     * @param top      Resource identifier of the top Drawable.
     * @param end      Resource identifier of the end Drawable.
     * @param bottom   Resource identifier of the bottom Drawable.
     * @attr name android:drawableStart
     * @attr name android:drawableTop
     * @attr name android:drawableEnd
     * @attr name android:drawableBottom
     */
    public static void setCompoundDrawablesRelativeWithIntrinsicBounds(@NonNull TextView textView,
            @DrawableRes int start, @DrawableRes int top, @DrawableRes int end,
            @DrawableRes int bottom) {
        IMPL.setCompoundDrawablesRelativeWithIntrinsicBounds(textView, start, top, end, bottom);
    }

    /**
     * Returns the maximum number of lines displayed in the given TextView, or -1 if the maximum
     * height was set in pixels instead.
     */
    public static int getMaxLines(@NonNull TextView textView) {
        return IMPL.getMaxLines(textView);
    }

    /**
     * Returns the minimum number of lines displayed in the given TextView, or -1 if the minimum
     * height was set in pixels instead.
     */
    public static int getMinLines(@NonNull TextView textView) {
        return IMPL.getMinLines(textView);
    }

    /**
     * Sets the text appearance from the specified style resource.
     * <p>
     * Use a framework-defined {@code TextAppearance} style like
     * {@link android.R.style#TextAppearance_Material_Body1 @android:style/TextAppearance.Material.Body1}.
     *
     * @param textView The TextView against which to invoke the method.
     * @param resId    The resource identifier of the style to apply.
     */
    public static void setTextAppearance(@NonNull TextView textView, @StyleRes int resId) {
        IMPL.setTextAppearance(textView, resId);
    }

    /**
     * Returns drawables for the start, top, end, and bottom borders from the given text view.
     */
    @NonNull
    public static Drawable[] getCompoundDrawablesRelative(@NonNull TextView textView) {
        return IMPL.getCompoundDrawablesRelative(textView);
    }

    /**
     * Specify whether this widget should automatically scale the text to try to perfectly fit
     * within the layout bounds by using the default auto-size configuration.
     *
     * @param autoSizeTextType the type of auto-size. Must be one of
     *        {@link TextViewCompat#AUTO_SIZE_TEXT_TYPE_NONE} or
     *        {@link TextViewCompat#AUTO_SIZE_TEXT_TYPE_UNIFORM}
     *
     * @attr name android:autoSizeTextType
     */
    public static void setAutoSizeTextTypeWithDefaults(@NonNull TextView textView,
            int autoSizeTextType) {
        IMPL.setAutoSizeTextTypeWithDefaults(textView, autoSizeTextType);
    }

    /**
     * Specify whether this widget should automatically scale the text to try to perfectly fit
     * within the layout bounds. If all the configuration params are valid the type of auto-size is
     * set to {@link TextViewCompat#AUTO_SIZE_TEXT_TYPE_UNIFORM}.
     *
     * @param autoSizeMinTextSize the minimum text size available for auto-size
     * @param autoSizeMaxTextSize the maximum text size available for auto-size
     * @param autoSizeStepGranularity the auto-size step granularity. It is used in conjunction with
     *                                the minimum and maximum text size in order to build the set of
     *                                text sizes the system uses to choose from when auto-sizing
     * @param unit the desired dimension unit for all sizes above. See {@link TypedValue} for the
     *             possible dimension units
     *
     * @throws IllegalArgumentException if any of the configuration params are invalid.
     *
     * @attr name android:autoSizeTextType
     * @attr name android:autoSizeTextType
     * @attr name android:autoSizeMinTextSize
     * @attr name android:autoSizeMaxTextSize
     * @attr name android:autoSizeStepGranularity
     */
    public static void setAutoSizeTextTypeUniformWithConfiguration(
            @NonNull TextView textView,
            int autoSizeMinTextSize,
            int autoSizeMaxTextSize,
            int autoSizeStepGranularity,
            int unit) throws IllegalArgumentException {
        IMPL.setAutoSizeTextTypeUniformWithConfiguration(textView, autoSizeMinTextSize,
                autoSizeMaxTextSize, autoSizeStepGranularity, unit);
    }

    /**
     * Specify whether this widget should automatically scale the text to try to perfectly fit
     * within the layout bounds. If at least one value from the <code>presetSizes</code> is valid
     * then the type of auto-size is set to {@link TextViewCompat#AUTO_SIZE_TEXT_TYPE_UNIFORM}.
     *
     * @param presetSizes an {@code int} array of sizes in pixels
     * @param unit the desired dimension unit for the preset sizes above. See {@link TypedValue} for
     *             the possible dimension units
     *
     * @throws IllegalArgumentException if all of the <code>presetSizes</code> are invalid.
     *_
     * @attr name android:autoSizeTextType
     * @attr name android:autoSizePresetSizes
     */
    public static void setAutoSizeTextTypeUniformWithPresetSizes(@NonNull TextView textView,
            @NonNull int[] presetSizes, int unit) throws IllegalArgumentException {
        IMPL.setAutoSizeTextTypeUniformWithPresetSizes(textView, presetSizes, unit);
    }

    /**
     * Returns the type of auto-size set for this widget.
     *
     * @return an {@code int} corresponding to one of the auto-size types:
     *         {@link TextViewCompat#AUTO_SIZE_TEXT_TYPE_NONE} or
     *         {@link TextViewCompat#AUTO_SIZE_TEXT_TYPE_UNIFORM}
     *
     * @attr name android:autoSizeTextType
     */
    public static int getAutoSizeTextType(@NonNull TextView textView) {
        return IMPL.getAutoSizeTextType(textView);
    }

    /**
     * @return the current auto-size step granularity in pixels.
     *
     * @attr name android:autoSizeStepGranularity
     */
    public static int getAutoSizeStepGranularity(@NonNull TextView textView) {
        return IMPL.getAutoSizeStepGranularity(textView);
    }

    /**
     * @return the current auto-size minimum text size in pixels (the default is 12sp). Note that
     *         if auto-size has not been configured this function returns {@code -1}.
     *
     * @attr name android:autoSizeMinTextSize
     */
    public static int getAutoSizeMinTextSize(@NonNull TextView textView) {
        return IMPL.getAutoSizeMinTextSize(textView);
    }

    /**
     * @return the current auto-size maximum text size in pixels (the default is 112sp). Note that
     *         if auto-size has not been configured this function returns {@code -1}.
     *
     * @attr name android:autoSizeMaxTextSize
     */
    public static int getAutoSizeMaxTextSize(@NonNull TextView textView) {
        return IMPL.getAutoSizeMaxTextSize(textView);
    }

    /**
     * @return the current auto-size {@code int} sizes array (in pixels).
     *
     * @attr name android:autoSizePresetSizes
     */
    @NonNull
    public static int[] getAutoSizeTextAvailableSizes(@NonNull TextView textView) {
        return IMPL.getAutoSizeTextAvailableSizes(textView);
    }

    /**
     * Sets a selection action mode callback on a TextView.
     *
     * Also this method can be used to fix a bug in framework SDK 26. On these affected devices,
     * the bug causes the menu containing the options for handling ACTION_PROCESS_TEXT after text
     * selection to miss a number of items. This method can be used to fix this wrong behaviour for
     * a text view, by passing any custom callback implementation. If no custom callback is desired,
     * a no-op implementation should be provided.
     *
     * Note that, by default, the bug will only be fixed when the default floating toolbar menu
     * implementation is used. If a custom implementation of {@link Menu} is provided, this should
     * provide the method Menu#removeItemAt(int) which removes a menu item by its position,
     * as given by Menu#getItem(int). Also, the following post condition should hold: a call
     * to removeItemAt(i), should not modify the results of getItem(j) for any j < i. Intuitively,
     * removing an element from the menu should behave as removing an element from a list.
     * Note that this method does not exist in the {@link Menu} interface. However, it is required,
     * and going to be called by reflection, in order to display the correct process text items in
     * the menu.
     *
     * @param textView The TextView to set the action selection mode callback on.
     * @param callback The action selection mode callback to set on textView.
     */
    public static void setCustomSelectionActionModeCallback(@NonNull TextView textView,
                @NonNull ActionMode.Callback callback) {
        IMPL.setCustomSelectionActionModeCallback(textView, callback);
    }
}

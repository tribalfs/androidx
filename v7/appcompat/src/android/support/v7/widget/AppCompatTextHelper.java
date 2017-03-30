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

package android.support.v7.widget;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.support.annotation.RestrictTo;
import android.support.v7.appcompat.R;
import android.support.v7.text.AllCapsTransformationMethod;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.widget.TextView;

@RequiresApi(9)
class AppCompatTextHelper {

    static AppCompatTextHelper create(TextView textView) {
        if (Build.VERSION.SDK_INT >= 17) {
            return new AppCompatTextHelperV17(textView);
        }
        return new AppCompatTextHelper(textView);
    }

    final TextView mView;

    private TintInfo mDrawableLeftTint;
    private TintInfo mDrawableTopTint;
    private TintInfo mDrawableRightTint;
    private TintInfo mDrawableBottomTint;

    private final AppCompatTextViewAutoSizeHelper mAutoSizeTextHelper;

    AppCompatTextHelper(TextView view) {
        mView = view;
        // Auto-size is supported by the framework starting from Android O.
        if (Build.VERSION.SDK_INT < 26) {
            mAutoSizeTextHelper = new AppCompatTextViewAutoSizeHelper(mView);
        } else {
            mAutoSizeTextHelper = null;
        }
    }

    void loadFromAttributes(AttributeSet attrs, int defStyleAttr) {
        final Context context = mView.getContext();
        final AppCompatDrawableManager drawableManager = AppCompatDrawableManager.get();

        // First read the TextAppearance style id
        TintTypedArray a = TintTypedArray.obtainStyledAttributes(context, attrs,
                R.styleable.AppCompatTextHelper, defStyleAttr, 0);
        final int ap = a.getResourceId(R.styleable.AppCompatTextHelper_android_textAppearance, -1);
        // Now read the compound drawable and grab any tints
        if (a.hasValue(R.styleable.AppCompatTextHelper_android_drawableLeft)) {
            mDrawableLeftTint = createTintInfo(context, drawableManager,
                    a.getResourceId(R.styleable.AppCompatTextHelper_android_drawableLeft, 0));
        }
        if (a.hasValue(R.styleable.AppCompatTextHelper_android_drawableTop)) {
            mDrawableTopTint = createTintInfo(context, drawableManager,
                    a.getResourceId(R.styleable.AppCompatTextHelper_android_drawableTop, 0));
        }
        if (a.hasValue(R.styleable.AppCompatTextHelper_android_drawableRight)) {
            mDrawableRightTint = createTintInfo(context, drawableManager,
                    a.getResourceId(R.styleable.AppCompatTextHelper_android_drawableRight, 0));
        }
        if (a.hasValue(R.styleable.AppCompatTextHelper_android_drawableBottom)) {
            mDrawableBottomTint = createTintInfo(context, drawableManager,
                    a.getResourceId(R.styleable.AppCompatTextHelper_android_drawableBottom, 0));
        }
        a.recycle();

        // PasswordTransformationMethod wipes out all other TransformationMethod instances
        // in TextView's constructor, so we should only set a new transformation method
        // if we don't have a PasswordTransformationMethod currently...
        final boolean hasPwdTm =
                mView.getTransformationMethod() instanceof PasswordTransformationMethod;
        boolean allCaps = false;
        boolean allCapsSet = false;
        ColorStateList textColor = null;
        ColorStateList textColorHint = null;
        ColorStateList textColorLink = null;

        // First check TextAppearance's textAllCaps value
        if (ap != -1) {
            a = TintTypedArray.obtainStyledAttributes(context, ap, R.styleable.TextAppearance);
            if (!hasPwdTm && a.hasValue(R.styleable.TextAppearance_textAllCaps)) {
                allCapsSet = true;
                allCaps = a.getBoolean(R.styleable.TextAppearance_textAllCaps, false);
            }
            if (Build.VERSION.SDK_INT < 23) {
                // If we're running on < API 23, the text color may contain theme references
                // so let's re-set using our own inflater
                if (a.hasValue(R.styleable.TextAppearance_android_textColor)) {
                    textColor = a.getColorStateList(R.styleable.TextAppearance_android_textColor);
                }
                if (a.hasValue(R.styleable.TextAppearance_android_textColorHint)) {
                    textColorHint = a.getColorStateList(
                            R.styleable.TextAppearance_android_textColorHint);
                }
                if (a.hasValue(R.styleable.TextAppearance_android_textColorLink)) {
                    textColorLink = a.getColorStateList(
                            R.styleable.TextAppearance_android_textColorLink);
                }
            }
            a.recycle();
        }

        // Now read the style's values
        a = TintTypedArray.obtainStyledAttributes(context, attrs, R.styleable.TextAppearance,
                defStyleAttr, 0);
        if (!hasPwdTm && a.hasValue(R.styleable.TextAppearance_textAllCaps)) {
            allCapsSet = true;
            allCaps = a.getBoolean(R.styleable.TextAppearance_textAllCaps, false);
        }
        if (Build.VERSION.SDK_INT < 23) {
            // If we're running on < API 23, the text color may contain theme references
            // so let's re-set using our own inflater
            if (a.hasValue(R.styleable.TextAppearance_android_textColor)) {
                textColor = a.getColorStateList(R.styleable.TextAppearance_android_textColor);
            }
            if (a.hasValue(R.styleable.TextAppearance_android_textColorHint)) {
                textColorHint = a.getColorStateList(
                        R.styleable.TextAppearance_android_textColorHint);
            }
            if (a.hasValue(R.styleable.TextAppearance_android_textColorLink)) {
                textColorLink = a.getColorStateList(
                        R.styleable.TextAppearance_android_textColorLink);
            }
        }
        a.recycle();

        if (textColor != null) {
            mView.setTextColor(textColor);
        }
        if (textColorHint != null) {
            mView.setHintTextColor(textColorHint);
        }
        if (textColorLink != null) {
            mView.setLinkTextColor(textColorLink);
        }
        if (!hasPwdTm && allCapsSet) {
            setAllCaps(allCaps);
        }

        if (mAutoSizeTextHelper != null) {
            mAutoSizeTextHelper.loadFromAttributes(attrs, defStyleAttr);
        }
    }

    void onSetTextAppearance(Context context, int resId) {
        final TintTypedArray a = TintTypedArray.obtainStyledAttributes(context,
                resId, R.styleable.TextAppearance);
        if (a.hasValue(R.styleable.TextAppearance_textAllCaps)) {
            // This breaks away slightly from the logic in TextView.setTextAppearance that serves
            // as an "overlay" on the current state of the TextView. Since android:textAllCaps
            // may have been set to true in this text appearance, we need to make sure that
            // app:textAllCaps has the chance to override it
            setAllCaps(a.getBoolean(R.styleable.TextAppearance_textAllCaps, false));
        }
        if (Build.VERSION.SDK_INT < 23
                && a.hasValue(R.styleable.TextAppearance_android_textColor)) {
            // If we're running on < API 23, the text color may contain theme references
            // so let's re-set using our own inflater
            final ColorStateList textColor
                    = a.getColorStateList(R.styleable.TextAppearance_android_textColor);
            if (textColor != null) {
                mView.setTextColor(textColor);
            }
        }
        a.recycle();
    }

    void setAllCaps(boolean allCaps) {
        mView.setTransformationMethod(allCaps
                ? new AllCapsTransformationMethod(mView.getContext())
                : null);
    }

    void applyCompoundDrawablesTints() {
        if (mDrawableLeftTint != null || mDrawableTopTint != null ||
                mDrawableRightTint != null || mDrawableBottomTint != null) {
            final Drawable[] compoundDrawables = mView.getCompoundDrawables();
            applyCompoundDrawableTint(compoundDrawables[0], mDrawableLeftTint);
            applyCompoundDrawableTint(compoundDrawables[1], mDrawableTopTint);
            applyCompoundDrawableTint(compoundDrawables[2], mDrawableRightTint);
            applyCompoundDrawableTint(compoundDrawables[3], mDrawableBottomTint);
        }
    }

    final void applyCompoundDrawableTint(Drawable drawable, TintInfo info) {
        if (drawable != null && info != null) {
            AppCompatDrawableManager.tintDrawable(drawable, info, mView.getDrawableState());
        }
    }

    protected static TintInfo createTintInfo(Context context,
            AppCompatDrawableManager drawableManager, int drawableId) {
        final ColorStateList tintList = drawableManager.getTintList(context, drawableId);
        if (tintList != null) {
            final TintInfo tintInfo = new TintInfo();
            tintInfo.mHasTintList = true;
            tintInfo.mTintList = tintList;
            return tintInfo;
        }
        return null;
    }

    /** @hide */
    @RestrictTo(LIBRARY_GROUP)
    void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // Auto-size is supported by the framework starting from Android O.
        if (Build.VERSION.SDK_INT < 26) {
            if (isAutoSizeEnabled()) {
                if (getNeedsAutoSizeText()) {
                    // Call auto-size after the width and height have been calculated.
                    autoSizeText();
                }
                // Always try to auto-size if enabled. Functions that do not want to trigger
                // auto-sizing after the next layout round should set this to false.
                setNeedsAutoSizeText(true);
            }
        }
    }

    /** @hide */
    @RestrictTo(LIBRARY_GROUP)
    void setTextSize(int unit, float size) {
        if (Build.VERSION.SDK_INT < 26) {
            if (!isAutoSizeEnabled()) {
                setTextSizeInternal(unit, size);
            }
        } else {
            mView.setTextSize(unit, size);
        }
    }

    private boolean isAutoSizeEnabled() {
        return mAutoSizeTextHelper != null && mAutoSizeTextHelper.isAutoSizeEnabled();
    }

    private boolean getNeedsAutoSizeText() {
        return mAutoSizeTextHelper != null && mAutoSizeTextHelper.getNeedsAutoSizeText();
    }

    private void setNeedsAutoSizeText(boolean needsAutoSizeText) {
        if (mAutoSizeTextHelper != null) {
            mAutoSizeTextHelper.setNeedsAutoSizeText(needsAutoSizeText);
        }
    }

    private void autoSizeText() {
        if (mAutoSizeTextHelper != null) {
            mAutoSizeTextHelper.autoSizeText();
        }
    }

    private void setTextSizeInternal(int unit, float size) {
        if (mAutoSizeTextHelper != null) {
            mAutoSizeTextHelper.setTextSizeInternal(unit, size);
        }
    }
}
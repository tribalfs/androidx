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

package android.support.v7.widget;

import static android.support.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.RectF;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.appcompat.R;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristic;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

/**
 * Utility class which encapsulates the logic for the TextView auto-size text feature added to
 * the Android Framework in {@link android.os.Build.VERSION_CODES#O}.
 *
 * <p>A TextView can be instructed to let the size of the text expand or contract automatically to
 * fill its layout based on the TextView's characteristics and boundaries.
 */
class AppCompatTextViewAutoSizeHelper {
    private static final RectF TEMP_RECTF = new RectF();
    // Default minimum size for auto-sizing text in scaled pixels.
    private static final int DEFAULT_AUTO_SIZE_MIN_TEXT_SIZE_IN_SP = 12;
    // Default maximum size for auto-sizing text in scaled pixels.
    private static final int DEFAULT_AUTO_SIZE_MAX_TEXT_SIZE_IN_SP = 112;
    // Default value for the step size in pixels.
    private static final int DEFAULT_AUTO_SIZE_GRANULARITY_IN_PX = 1;
    // Use this to specify that any of the auto-size configuration int values have not been set.
    static final int UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE = -1;
    // Auto-size text type.
    private int mAutoSizeTextType = TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE;
    // Specify if auto-size text is needed.
    private boolean mNeedsAutoSizeText = false;
    // Step size for auto-sizing in pixels.
    private int mAutoSizeStepGranularityInPx = UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE;
    // Minimum text size for auto-sizing in pixels.
    private int mAutoSizeMinTextSizeInPx = UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE;
    // Maximum text size for auto-sizing in pixels.
    private int mAutoSizeMaxTextSizeInPx = UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE;
    // Contains a (specified or computed) distinct sorted set of text sizes in pixels to pick from
    // when auto-sizing text.
    private int[] mAutoSizeTextSizesInPx = new int[0];
    // Specifies whether auto-size should use the provided auto size steps set or if it should
    // build the steps set using mAutoSizeMinTextSizeInPx, mAutoSizeMaxTextSizeInPx and
    // mAutoSizeStepGranularityInPx.
    private boolean mHasPresetAutoSizeValues = false;

    private TextPaint mTempTextPaint;
    private Hashtable<String, Method> mMethodByNameCache = new Hashtable<>();

    private final TextView mTextView;
    private final Context mContext;

    AppCompatTextViewAutoSizeHelper(TextView textView) {
        mTextView = textView;
        mContext = mTextView.getContext();
    }

    void loadFromAttributes(AttributeSet attrs, int defStyleAttr) {
        int autoSizeMinTextSizeInPx = UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE;
        int autoSizeMaxTextSizeInPx = UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE;
        int autoSizeStepGranularityInPx = UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE;

        TypedArray a = mContext.obtainStyledAttributes(attrs, R.styleable.AppCompatTextView,
                defStyleAttr, 0);
        if (a.hasValue(R.styleable.AppCompatTextView_autoSizeTextType)) {
            mAutoSizeTextType = a.getInt(R.styleable.AppCompatTextView_autoSizeTextType,
                    TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE);
        }
        if (a.hasValue(R.styleable.AppCompatTextView_autoSizeStepGranularity)) {
            autoSizeStepGranularityInPx = a.getDimensionPixelSize(
                    R.styleable.AppCompatTextView_autoSizeStepGranularity,
                    UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE);
        }
        if (a.hasValue(R.styleable.AppCompatTextView_autoSizeMinTextSize)) {
            autoSizeMinTextSizeInPx = a.getDimensionPixelSize(
                    R.styleable.AppCompatTextView_autoSizeMinTextSize,
                    UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE);
        }
        if (a.hasValue(R.styleable.AppCompatTextView_autoSizeMaxTextSize)) {
            autoSizeMaxTextSizeInPx = a.getDimensionPixelSize(
                    R.styleable.AppCompatTextView_autoSizeMaxTextSize,
                    UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE);
        }
        if (a.hasValue(R.styleable.AppCompatTextView_autoSizePresetSizes)) {
            final int autoSizeStepSizeArrayResId = a.getResourceId(
                    R.styleable.AppCompatTextView_autoSizePresetSizes, 0);
            if (autoSizeStepSizeArrayResId > 0) {
                final TypedArray autoSizePreDefTextSizes = a.getResources()
                        .obtainTypedArray(autoSizeStepSizeArrayResId);
                setupAutoSizeUniformPresetSizes(autoSizePreDefTextSizes);
                autoSizePreDefTextSizes.recycle();
            }
        }
        a.recycle();

        if (supportsAutoSizeText()) {
            if (mAutoSizeTextType == TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM) {
                // If uniform auto-size has been specified but preset values have not been set then
                // replace the auto-size configuration values that have not been specified with the
                // defaults.
                if (!mHasPresetAutoSizeValues) {
                    final DisplayMetrics displayMetrics =
                            mContext.getResources().getDisplayMetrics();

                    if (autoSizeMinTextSizeInPx == UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE) {
                        autoSizeMinTextSizeInPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_SP,
                                DEFAULT_AUTO_SIZE_MIN_TEXT_SIZE_IN_SP,
                                displayMetrics);
                    }

                    if (autoSizeMaxTextSizeInPx == UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE) {
                        autoSizeMaxTextSizeInPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_SP,
                                DEFAULT_AUTO_SIZE_MAX_TEXT_SIZE_IN_SP,
                                displayMetrics);
                    }

                    if (autoSizeStepGranularityInPx
                            == UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE) {
                        autoSizeStepGranularityInPx = DEFAULT_AUTO_SIZE_GRANULARITY_IN_PX;
                    }

                    validateAndSetAutoSizeTextTypeUniformConfiguration(autoSizeMinTextSizeInPx,
                            autoSizeMaxTextSizeInPx,
                            autoSizeStepGranularityInPx);
                }

                setupAutoSizeText();
            }
        } else {
            mAutoSizeTextType = TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE;
        }
    }

    /**
     * Specify whether this widget should automatically scale the text to try to perfectly fit
     * within the layout bounds by using the default auto-size configuration.
     *
     * @param autoSizeTextType the type of auto-size. Must be one of
     *        {@link TextViewCompat#AUTO_SIZE_TEXT_TYPE_NONE} or
     *        {@link TextViewCompat#AUTO_SIZE_TEXT_TYPE_UNIFORM}
     *
     * @attr ref R.styleable#AppCompatTextView_autoSizeTextType
     *
     * @see #getAutoSizeTextType()
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    void setAutoSizeTextTypeWithDefaults(@TextViewCompat.AutoSizeTextType int autoSizeTextType) {
        if (supportsAutoSizeText()) {
            switch (autoSizeTextType) {
                case TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE:
                    clearAutoSizeConfiguration();
                    break;
                case TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM:
                    final DisplayMetrics displayMetrics =
                            mContext.getResources().getDisplayMetrics();
                    final int autoSizeMinTextSizeInPx = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_SP,
                            DEFAULT_AUTO_SIZE_MIN_TEXT_SIZE_IN_SP,
                            displayMetrics);
                    final int autoSizeMaxTextSizeInPx = (int) TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_SP,
                            DEFAULT_AUTO_SIZE_MAX_TEXT_SIZE_IN_SP,
                            displayMetrics);

                    validateAndSetAutoSizeTextTypeUniformConfiguration(
                            autoSizeMinTextSizeInPx,
                            autoSizeMaxTextSizeInPx,
                            DEFAULT_AUTO_SIZE_GRANULARITY_IN_PX);
                    setupAutoSizeText();
                    break;
                default:
                    throw new IllegalArgumentException(
                            "Unknown auto-size text type: " + autoSizeTextType);
            }
        }
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
     * @attr ref R.styleable#AppCompatTextView_autoSizeTextType
     * @attr ref R.styleable#AppCompatTextView_autoSizeMinTextSize
     * @attr ref R.styleable#AppCompatTextView_autoSizeMaxTextSize
     * @attr ref R.styleable#AppCompatTextView_autoSizeStepGranularity
     *
     * @see #setAutoSizeTextTypeWithDefaults(int)
     * @see #setAutoSizeTextTypeUniformWithPresetSizes(int[], int)
     * @see #getAutoSizeMinTextSize()
     * @see #getAutoSizeMaxTextSize()
     * @see #getAutoSizeStepGranularity()
     * @see #getAutoSizeTextAvailableSizes()
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    void setAutoSizeTextTypeUniformWithConfiguration(
            int autoSizeMinTextSize,
            int autoSizeMaxTextSize,
            int autoSizeStepGranularity,
            int unit) throws IllegalArgumentException {
        if (supportsAutoSizeText()) {
            final DisplayMetrics displayMetrics = mContext.getResources().getDisplayMetrics();
            final int autoSizeMinTextSizeInPx = (int) TypedValue.applyDimension(
                    unit, autoSizeMinTextSize, displayMetrics);
            final int autoSizeMaxTextSizeInPx = (int) TypedValue.applyDimension(
                    unit, autoSizeMaxTextSize, displayMetrics);
            final int autoSizeStepGranularityInPx = (int) TypedValue.applyDimension(
                    unit, autoSizeStepGranularity, displayMetrics);

            validateAndSetAutoSizeTextTypeUniformConfiguration(autoSizeMinTextSizeInPx,
                    autoSizeMaxTextSizeInPx,
                    autoSizeStepGranularityInPx);
            setupAutoSizeText();
        }
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
     * @attr ref R.styleable#AppCompatTextView_autoSizeTextType
     * @attr ref R.styleable#AppCompatTextView_autoSizePresetSizes
     *
     * @see #setAutoSizeTextTypeWithDefaults(int)
     * @see #setAutoSizeTextTypeUniformWithConfiguration(int, int, int, int)
     * @see #getAutoSizeMinTextSize()
     * @see #getAutoSizeMaxTextSize()
     * @see #getAutoSizeTextAvailableSizes()
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    void setAutoSizeTextTypeUniformWithPresetSizes(@NonNull int[] presetSizes, int unit)
            throws IllegalArgumentException {
        if (supportsAutoSizeText()) {
            final int presetSizesLength = presetSizes.length;
            if (presetSizesLength > 0) {
                int[] presetSizesInPx = new int[presetSizesLength];

                if (unit == TypedValue.COMPLEX_UNIT_PX) {
                    presetSizesInPx = Arrays.copyOf(presetSizes, presetSizesLength);
                } else {
                    final DisplayMetrics displayMetrics =
                            mContext.getResources().getDisplayMetrics();
                    // Convert all to sizes to pixels.
                    for (int i = 0; i < presetSizesLength; i++) {
                        presetSizesInPx[i] = (int) TypedValue.applyDimension(unit, presetSizes[i],
                                displayMetrics);
                    }
                }

                mAutoSizeTextSizesInPx = cleanupAutoSizePresetSizes(presetSizesInPx);
                if (!setupAutoSizeUniformPresetSizesConfiguration()) {
                    throw new IllegalArgumentException("None of the preset sizes is valid: "
                            + Arrays.toString(presetSizes));
                }
            } else {
                mHasPresetAutoSizeValues = false;
            }
            setupAutoSizeText();
        }
    }

    /**
     * Returns the type of auto-size set for this widget.
     *
     * @return an {@code int} corresponding to one of the auto-size types:
     *         {@link TextViewCompat#AUTO_SIZE_TEXT_TYPE_NONE} or
     *         {@link TextViewCompat#AUTO_SIZE_TEXT_TYPE_UNIFORM}
     *
     * @attr ref R.styleable#AppCompatTextView_autoSizeTextType
     *
     * @see #setAutoSizeTextTypeWithDefaults(int)
     * @see #setAutoSizeTextTypeUniformWithConfiguration(int, int, int, int)
     * @see #setAutoSizeTextTypeUniformWithPresetSizes(int[], int)
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @TextViewCompat.AutoSizeTextType
    int getAutoSizeTextType() {
        return mAutoSizeTextType;
    }

    /**
     * @return the current auto-size step granularity in pixels.
     *
     * @attr ref R.styleable#AppCompatTextView_autoSizeStepGranularity
     *
     * @see #setAutoSizeTextTypeUniformWithConfiguration(int, int, int, int)
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    int getAutoSizeStepGranularity() {
        return mAutoSizeStepGranularityInPx;
    }

    /**
     * @return the current auto-size minimum text size in pixels (the default is 12sp). Note that
     *         if auto-size has not been configured this function returns {@code -1}.
     *
     * @attr ref R.styleable#AppCompatTextView_autoSizeMinTextSize
     *
     * @see #setAutoSizeTextTypeUniformWithConfiguration(int, int, int, int)
     * @see #setAutoSizeTextTypeUniformWithPresetSizes(int[], int)
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    int getAutoSizeMinTextSize() {
        return mAutoSizeMinTextSizeInPx;
    }

    /**
     * @return the current auto-size maximum text size in pixels (the default is 112sp). Note that
     *         if auto-size has not been configured this function returns {@code -1}.
     *
     * @attr ref R.styleable#AppCompatTextView_autoSizeMaxTextSize
     *
     * @see #setAutoSizeTextTypeUniformWithConfiguration(int, int, int, int)
     * @see #setAutoSizeTextTypeUniformWithPresetSizes(int[], int)
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    int getAutoSizeMaxTextSize() {
        return mAutoSizeMaxTextSizeInPx;
    }

    /**
     * @return the current auto-size {@code int} sizes array (in pixels).
     *
     * @see #setAutoSizeTextTypeUniformWithConfiguration(int, int, int, int)
     * @see #setAutoSizeTextTypeUniformWithPresetSizes(int[], int)
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    int[] getAutoSizeTextAvailableSizes() {
        return mAutoSizeTextSizesInPx;
    }

    private void setupAutoSizeUniformPresetSizes(TypedArray textSizes) {
        final int textSizesLength = textSizes.length();
        final int[] parsedSizes = new int[textSizesLength];

        if (textSizesLength > 0) {
            for (int i = 0; i < textSizesLength; i++) {
                parsedSizes[i] = textSizes.getDimensionPixelSize(i, -1);
            }
            mAutoSizeTextSizesInPx = cleanupAutoSizePresetSizes(parsedSizes);
            setupAutoSizeUniformPresetSizesConfiguration();
        }
    }

    private boolean setupAutoSizeUniformPresetSizesConfiguration() {
        final int sizesLength = mAutoSizeTextSizesInPx.length;
        mHasPresetAutoSizeValues = sizesLength > 0;
        if (mHasPresetAutoSizeValues) {
            mAutoSizeTextType = TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM;
            mAutoSizeMinTextSizeInPx = mAutoSizeTextSizesInPx[0];
            mAutoSizeMaxTextSizeInPx = mAutoSizeTextSizesInPx[sizesLength - 1];
            mAutoSizeStepGranularityInPx = UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE;
        }
        return mHasPresetAutoSizeValues;
    }

    // Returns distinct sorted positive values.
    private int[] cleanupAutoSizePresetSizes(int[] presetValues) {
        final int presetValuesLength = presetValues.length;
        if (presetValuesLength == 0) {
            return presetValues;
        }
        Arrays.sort(presetValues);

        final List<Integer> uniqueValidSizes = new ArrayList<>();
        for (int i = 0; i < presetValuesLength; i++) {
            final int currentPresetValue = presetValues[i];

            if (currentPresetValue > 0
                    && Collections.binarySearch(uniqueValidSizes, currentPresetValue) < 0) {
                uniqueValidSizes.add(currentPresetValue);
            }
        }

        if (presetValuesLength == uniqueValidSizes.size()) {
            return presetValues;
        } else {
            final int uniqueValidSizesLength = uniqueValidSizes.size();
            final int[] cleanedUpSizes = new int[uniqueValidSizesLength];
            for (int i = 0; i < uniqueValidSizesLength; i++) {
                cleanedUpSizes[i] = uniqueValidSizes.get(i);
            }
            return cleanedUpSizes;
        }
    }

    /**
     * If all params are valid then save the auto-size configuration.
     *
     * @throws IllegalArgumentException if any of the params are invalid
     */
    private void validateAndSetAutoSizeTextTypeUniformConfiguration(
            int autoSizeMinTextSizeInPx,
            int autoSizeMaxTextSizeInPx,
            int autoSizeStepGranularityInPx) throws IllegalArgumentException {
        // First validate.
        if (autoSizeMinTextSizeInPx <= 0) {
            throw new IllegalArgumentException("Minimum auto-size text size ("
                    + autoSizeMinTextSizeInPx  + "px) is less or equal to (0px)");
        }

        if (autoSizeMaxTextSizeInPx <= autoSizeMinTextSizeInPx) {
            throw new IllegalArgumentException("Maximum auto-size text size ("
                    + autoSizeMaxTextSizeInPx + "px) is less or equal to minimum auto-size "
                    + "text size (" + autoSizeMinTextSizeInPx + "px)");
        }

        if (autoSizeStepGranularityInPx <= 0) {
            throw new IllegalArgumentException("The auto-size step granularity ("
                    + autoSizeStepGranularityInPx + "px) is less or equal to (0px)");
        }

        // All good, persist the configuration.
        mAutoSizeTextType = TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM;
        mAutoSizeMinTextSizeInPx = autoSizeMinTextSizeInPx;
        mAutoSizeMaxTextSizeInPx = autoSizeMaxTextSizeInPx;
        mAutoSizeStepGranularityInPx = autoSizeStepGranularityInPx;
        mHasPresetAutoSizeValues = false;
    }

    private void setupAutoSizeText() {
        if (supportsAutoSizeText()
                && mAutoSizeTextType == TextViewCompat.AUTO_SIZE_TEXT_TYPE_UNIFORM) {
            // Calculate the sizes set based on minimum size, maximum size and step size if we do
            // not have a predefined set of sizes or if the current sizes array is empty.
            if (!mHasPresetAutoSizeValues || mAutoSizeTextSizesInPx.length == 0) {
                // Calculate sizes to choose from based on the current auto-size configuration.
                int autoSizeValuesLength = (int) Math.ceil(
                        (mAutoSizeMaxTextSizeInPx - mAutoSizeMinTextSizeInPx)
                                / (float) mAutoSizeStepGranularityInPx);
                // Also reserve a slot for the max size if it fits.
                if ((mAutoSizeMaxTextSizeInPx - mAutoSizeMinTextSizeInPx)
                        % mAutoSizeStepGranularityInPx == 0) {
                    autoSizeValuesLength++;
                }
                mAutoSizeTextSizesInPx = new int[autoSizeValuesLength];
                int sizeToAdd = mAutoSizeMinTextSizeInPx;
                for (int i = 0; i < autoSizeValuesLength; i++) {
                    mAutoSizeTextSizesInPx[i] = sizeToAdd;
                    sizeToAdd += mAutoSizeStepGranularityInPx;
                }
            }

            mNeedsAutoSizeText = true;
            // If the build version is at least 26 there is no need to auto-size using this
            // helper because the job has been delegated to the actual TextView but the
            // configuration still needs to be done for the case where this function is called
            // from {@link #loadFromAttributes}, in which case the auto-size configuration
            // attributes set up in this function will be read by {@link AppCompatTextHelper}
            // and after passed on to the actual TextView which will take care of auto-sizing.
            if (Build.VERSION.SDK_INT < 26) {
                autoSizeText();
            }
        }
    }

    /**
     * Automatically computes and sets the text size.
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    void autoSizeText() {
        final int maxWidth = mTextView.getWidth() - mTextView.getTotalPaddingLeft()
                - mTextView.getTotalPaddingRight();
        final int maxHeight = Build.VERSION.SDK_INT >= 21
                ? mTextView.getHeight() - mTextView.getExtendedPaddingBottom()
                        - mTextView.getExtendedPaddingBottom()
                : mTextView.getHeight() - mTextView.getCompoundPaddingBottom()
                        - mTextView.getCompoundPaddingTop();

        if (maxWidth <= 0 || maxHeight <= 0) {
            return;
        }

        synchronized (TEMP_RECTF) {
            TEMP_RECTF.setEmpty();
            TEMP_RECTF.right = maxWidth;
            TEMP_RECTF.bottom = maxHeight;
            final float optimalTextSize = findLargestTextSizeWhichFits(TEMP_RECTF);
            if (optimalTextSize != mTextView.getTextSize()) {
                setTextSizeInternal(TypedValue.COMPLEX_UNIT_PX, optimalTextSize);
            }
        }
    }

    private void clearAutoSizeConfiguration() {
        mAutoSizeTextType = TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE;
        mAutoSizeMinTextSizeInPx = UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE;
        mAutoSizeMaxTextSizeInPx = UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE;
        mAutoSizeStepGranularityInPx = UNSET_AUTO_SIZE_UNIFORM_CONFIGURATION_VALUE;
        mAutoSizeTextSizesInPx = new int[0];
        mNeedsAutoSizeText = false;
    }

    /** @hide */
    @RestrictTo(LIBRARY_GROUP)
    void setTextSizeInternal(int unit, float size) {
        Resources res = mContext == null
                ? Resources.getSystem()
                : mContext.getResources();

        setRawTextSize(TypedValue.applyDimension(unit, size, res.getDisplayMetrics()));
    }

    private void setRawTextSize(float size) {
        if (size != mTextView.getPaint().getTextSize()) {
            mTextView.getPaint().setTextSize(size);

            if (mTextView.getLayout() != null) {
                // Do not auto-size right after setting the text size.
                mNeedsAutoSizeText = false;

                try {
                    final String methodName = "nullLayouts";
                    Method method = mMethodByNameCache.get(methodName);
                    if (method == null) {
                        method = TextView.class.getDeclaredMethod(methodName);
                        if (method != null) {
                            method.setAccessible(true);
                            // Cache update.
                            mMethodByNameCache.put(methodName, method);
                        }
                    }

                    if (method != null) {
                        method.invoke(mTextView);
                    }
                } catch (Exception ex) {
                    // Nothing to do.
                }

                mTextView.requestLayout();
                mTextView.invalidate();
            }
        }
    }

    /**
     * Performs a binary search to find the largest text size that will still fit within the size
     * available to this view.
     */
    private int findLargestTextSizeWhichFits(RectF availableSpace) {
        final int sizesCount = mAutoSizeTextSizesInPx.length;
        if (sizesCount == 0) {
            throw new IllegalStateException("No available text sizes to choose from.");
        }

        int bestSizeIndex = 0;
        int lowIndex = bestSizeIndex + 1;
        int highIndex = sizesCount - 1;
        int sizeToTryIndex;
        while (lowIndex <= highIndex) {
            sizeToTryIndex = (lowIndex + highIndex) / 2;
            if (suggestedSizeFitsInSpace(mAutoSizeTextSizesInPx[sizeToTryIndex], availableSpace)) {
                bestSizeIndex = lowIndex;
                lowIndex = sizeToTryIndex + 1;
            } else {
                highIndex = sizeToTryIndex - 1;
                bestSizeIndex = highIndex;
            }
        }

        return mAutoSizeTextSizesInPx[bestSizeIndex];
    }

    private boolean suggestedSizeFitsInSpace(int suggestedSizeInPx, RectF availableSpace) {
        final CharSequence text = mTextView.getText();
        final int maxLines = Build.VERSION.SDK_INT >= 16 ? mTextView.getMaxLines() : -1;
        final int availableWidth = mTextView.getMeasuredWidth() - mTextView.getTotalPaddingLeft()
                - mTextView.getTotalPaddingRight();
        if (mTempTextPaint == null) {
            mTempTextPaint = new TextPaint();
        } else {
            mTempTextPaint.reset();
        }
        mTempTextPaint.set(mTextView.getPaint());
        mTempTextPaint.setTextSize(suggestedSizeInPx);

        // Needs reflection call due to being private.
        Layout.Alignment alignment = invokeAndReturnWithDefault(
                mTextView, "getLayoutAlignment", Layout.Alignment.ALIGN_NORMAL);
        final StaticLayout layout = Build.VERSION.SDK_INT >= 23
                ? createStaticLayoutForMeasuring(text, alignment, availableWidth, maxLines)
                : createStaticLayoutForMeasuringPre23(text, alignment, availableWidth);

        // Lines overflow.
        if (maxLines != -1 && layout.getLineCount() > maxLines) {
            return false;
        }

        // Height overflow.
        if (layout.getHeight() > availableSpace.bottom) {
            return false;
        }

        return true;
    }

    @TargetApi(23)
    private StaticLayout createStaticLayoutForMeasuring(CharSequence text,
            Layout.Alignment alignment, int availableWidth, int maxLines) {
        // Can use the StaticLayout.Builder (along with TextView params added in or after
        // API 23) to construct the layout.
        final TextDirectionHeuristic textDirectionHeuristic = invokeAndReturnWithDefault(
                mTextView, "getTextDirectionHeuristic",
                TextDirectionHeuristics.FIRSTSTRONG_LTR);

        final StaticLayout.Builder layoutBuilder = StaticLayout.Builder.obtain(
                text, 0, text.length(),  mTempTextPaint, availableWidth);

        return layoutBuilder.setAlignment(alignment)
                .setLineSpacing(
                        mTextView.getLineSpacingExtra(),
                        mTextView.getLineSpacingMultiplier())
                .setIncludePad(mTextView.getIncludeFontPadding())
                .setBreakStrategy(mTextView.getBreakStrategy())
                .setHyphenationFrequency(mTextView.getHyphenationFrequency())
                .setMaxLines(maxLines == -1 ? Integer.MAX_VALUE : maxLines)
                .setTextDirection(textDirectionHeuristic)
                .build();
    }

    @TargetApi(14)
    private StaticLayout createStaticLayoutForMeasuringPre23(CharSequence text,
            Layout.Alignment alignment, int availableWidth) {
        // Setup defaults.
        float lineSpacingMultiplier = 1.0f;
        float lineSpacingAdd = 0.0f;
        boolean includePad = true;

        if (Build.VERSION.SDK_INT >= 16) {
            // Call public methods.
            lineSpacingMultiplier = mTextView.getLineSpacingMultiplier();
            lineSpacingAdd = mTextView.getLineSpacingExtra();
            includePad = mTextView.getIncludeFontPadding();
        } else {
            // Call private methods and make sure to provide fallback defaults in case something
            // goes wrong. The default values have been inlined with the StaticLayout defaults.
            lineSpacingMultiplier = invokeAndReturnWithDefault(mTextView,
                    "getLineSpacingMultiplier", lineSpacingMultiplier);
            lineSpacingAdd = invokeAndReturnWithDefault(mTextView,
                    "getLineSpacingExtra", lineSpacingAdd);
            includePad = invokeAndReturnWithDefault(mTextView,
                    "getIncludeFontPadding", includePad);
        }

        // The layout could not be constructed using the builder so fall back to the
        // most broad constructor.
        return new StaticLayout(text, mTempTextPaint, availableWidth,
                alignment,
                lineSpacingMultiplier,
                lineSpacingAdd,
                includePad);
    }

    private <T> T invokeAndReturnWithDefault(@NonNull Object object, @NonNull String methodName,
            @NonNull T defaultValue) {
        T result = null;
        boolean exceptionThrown = false;

        try {
            // Cache lookup.
            Method method = mMethodByNameCache.get(methodName);
            if (method == null) {
                method = TextView.class.getDeclaredMethod(methodName);
                if (method != null) {
                    method.setAccessible(true);
                    // Cache update.
                    mMethodByNameCache.put(methodName, method);
                }
            }
            result = (T) method.invoke(object);
        } catch (Exception e) {
            exceptionThrown = true;
        } finally {
            if (result == null && exceptionThrown) {
                result = defaultValue;
            }
        }

        return result;
    }

    /**
     * @return {@code true} if this widget supports auto-sizing text and has been configured to
     * auto-size.
     *
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    boolean isAutoSizeEnabled() {
        return supportsAutoSizeText()
                && mAutoSizeTextType != TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE;
    }

    /** @hide */
    @RestrictTo(LIBRARY_GROUP)
    boolean getNeedsAutoSizeText() {
        return mNeedsAutoSizeText;
    }

    /** @hide */
    @RestrictTo(LIBRARY_GROUP)
    void setNeedsAutoSizeText(boolean needsAutoSizeText) {
        mNeedsAutoSizeText = needsAutoSizeText;
    }

    /**
     * @return {@code true} if this TextView supports auto-sizing text to fit within its container.
     */
    private boolean supportsAutoSizeText() {
        // Auto-size only supports TextView and all siblings but EditText.
        return !(mTextView instanceof AppCompatEditText);
    }
}

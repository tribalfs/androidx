/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.support.v17.leanback.widget.picker;

/**
 * Picker column class used by {@link Picker}, defines a contiguous value ranges and associated
 * labels.  A PickerColumn has a minValue and maxValue to choose between.  The Picker column has
 * a current value.
 * The labels can be dynamically generated from value by {@link #setEntryFormat(String)} or
 * a list of static labels set by {@link #setEntries(CharSequence[])}.
 */
public class PickerColumn {

    private int mCurrentValue;
    private int mMinValue;
    private int mMaxValue;
    private CharSequence[] mStaticEntrys;
    private String mEntryFormat;

    public PickerColumn() {
    }

    /**
     * Set string format to display label for value. For example "%02d".
     * {@link #setEntries(CharSequence[])} overrides the format.
     *
     * @param valueFormat String format to display label for value between minValue and maxValue.
     */
    public void setEntryFormat(String valueFormat) {
        mEntryFormat = valueFormat;
    }

    /**
     * Return string format to display label for value.  For example "%02d".
     * @return String format to display label for value.
     */
    public String getEntryFormat() {
        return mEntryFormat;
    }

    /**
     * Set static labels for each value, minValue maps to labels[0], maxValue maps to
     * labels[labels.length - 1].
     * @param labels Static labels for each value between minValue and maxValue.
     */
    public void setEntries(CharSequence[] labels) {
        mStaticEntrys = labels;
    }

    /**
     * Get a label for value. The label can be static ({@link #setEntries(CharSequence[])}
     * or dynamically generated (@link {@link #setEntryFormat(String)}.
     * 
     * @param value Value between minValue and maxValue.
     * @return Label for the value.
     */
    public CharSequence getEntryAt(int value) {
        if (mStaticEntrys == null) {
            return String.format(mEntryFormat, value);
        }
        return mStaticEntrys[value];
    }

    /**
     * Returns current value of the Column.
     * @return Current value of the Column.
     */
    public int getCurrentValue() {
        return mCurrentValue;
    }

    /**
     * Sets current value of the Column.
     */
    public void setCurrentValue(int value) {
        mCurrentValue = value;
    }

    /**
     * Get total items count between minValue and maxValue.
     * @return Total items count between minValue and maxValue.
     */
    public int getItemCount() {
        return mMaxValue - mMinValue + 1;
    }

    /**
     * Returns minimal value of the Column.
     * @return Minimal value of the Column.
     */
    public int getMinValue() {
        return mMinValue;
    }

    /**
     * Returns maximum value of the Column.
     * @return Maximum value of the Column.
     */
    public int getMaxValue() {
        return mMaxValue;
    }

    /**
     * Sets minimal value of the Column.
     * @param minValue New minimal value to set.
     */
    public void setMinValue(int minValue) {
        mMinValue = minValue;
    }

    /**
     * Sets maximum value of the Column.
     * @param maxValue New maximum value to set.
     */
    public void setMaxValue(int maxValue) {
        mMaxValue = maxValue;
    }

}

/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.app.slice.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;

import java.util.List;

import androidx.app.slice.Slice;
import androidx.app.slice.SliceItem;
import androidx.app.slice.view.R;

/**
 * Base class for children views of {@link SliceView}.
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public abstract class SliceChildView extends FrameLayout {

    protected SliceView.OnSliceActionListener mObserver;
    protected int mMode;
    protected int mTintColor = -1;
    protected int mTitleColor;
    protected int mSubtitleColor;
    protected int mHeaderTitleSize;
    protected int mHeaderSubtitleSize;
    protected int mTitleSize;
    protected int mSubtitleSize;
    protected int mGridTitleSize;
    protected int mGridSubtitleSize;

    public SliceChildView(@NonNull Context context) {
        super(context);
    }

    public SliceChildView(Context context, AttributeSet attributeSet) {
        this(context);
    }


    /**
     * Set the mode of the slice being presented.
     */
    public void setMode(int mode) {
        mMode = mode;
    }

    /**
     * @return the mode of the slice being presented.
     */
    @SliceView.SliceMode
    public int getMode() {
        return mMode;
    }

    /**
     * @param slice the slice to show in this view.
     */
    public abstract void setSlice(Slice slice);

    /**
     * Called when the view should be reset.
     */
    public abstract void resetView();

    /**
     * @return the view.
     */
    public View getView() {
        return this;
    }

    /**
     * Sets a custom color to use for tinting elements like icons for this view.
     */
    public void setTint(@ColorInt int tintColor) {
        mTintColor = tintColor;
    }

    /**
     * Sets the observer to notify when an interaction events occur on the view.
     */
    public void setSliceActionListener(SliceView.OnSliceActionListener observer) {
        mObserver = observer;
    }

    /**
     * Populates style information for this view.
     */
    public void setStyle(AttributeSet attrs) {
        TypedArray a = getContext().getTheme().obtainStyledAttributes(attrs, R.styleable.SliceView,
                R.attr.sliceViewStyle, R.style.Widget_SliceView);
        try {
            int themeColor = a.getColor(R.styleable.SliceView_tintColor, -1);
            mTintColor = themeColor != -1 ? themeColor : mTintColor;
            mTitleColor = a.getColor(R.styleable.SliceView_titleColor, 0);
            mSubtitleColor = a.getColor(R.styleable.SliceView_subtitleColor, 0);
            mHeaderTitleSize = (int) a.getDimension(
                    R.styleable.SliceView_headerTitleSize, 0);
            mHeaderSubtitleSize = (int) a.getDimension(
                    R.styleable.SliceView_headerSubtitleSize, 0);
            mTitleSize = (int) a.getDimension(R.styleable.SliceView_titleSize, 0);
            mSubtitleSize = (int) a.getDimension(
                    R.styleable.SliceView_subtitleSize, 0);
            mGridTitleSize = (int) a.getDimension(R.styleable.SliceView_gridTitleSize, 0);
            mGridSubtitleSize = (int) a.getDimension(
                    R.styleable.SliceView_gridSubtitleSize, 0);
        } finally {
            a.recycle();
        }
    }

    /**
     * Called when the slice being displayed in this view is an element of a larger list.
     */
    public void setSliceItem(SliceItem slice, boolean isHeader, int rowIndex,
            SliceView.OnSliceActionListener observer) {
        // Do nothing
    }

    /**
     * Sets the slice actions for this view.
     */
    public void setSliceActions(List<SliceItem> actions) {
        // Do nothing
    }
}

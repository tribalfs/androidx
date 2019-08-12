/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.slice.widget;

import android.content.Context;
import android.content.res.TypedArray;

import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.slice.view.R;

/**
 * Holds style information shared between child views of a row
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@RequiresApi(19)
public class RowStyle {
    public static final int UNBOUNDED = -1;

    private int mTitleItemStartPadding;
    private int mTitleItemEndPadding;
    private int mContentStartPadding;
    private int mContentEndPadding;
    private int mEndItemStartPadding;
    private int mEndItemEndPadding;
    private int mBottomDividerStartPadding;
    private int mBottomDividerEndPadding;
    private int mActionDividerHeight;

    public RowStyle(Context context, int resId) {
        TypedArray a = context.getTheme().obtainStyledAttributes(resId, R.styleable.RowStyle);
        try {
            mTitleItemStartPadding = (int) a.getDimension(
                    R.styleable.RowStyle_titleItemStartPadding, UNBOUNDED);
            mTitleItemEndPadding = (int) a.getDimension(
                    R.styleable.RowStyle_titleItemEndPadding, UNBOUNDED);
            mContentStartPadding = (int) a.getDimension(
                    R.styleable.RowStyle_contentStartPadding, UNBOUNDED);
            mContentEndPadding = (int) a.getDimension(
                    R.styleable.RowStyle_contentEndPadding, UNBOUNDED);
            mEndItemStartPadding = (int) a.getDimension(
                    R.styleable.RowStyle_endItemStartPadding, UNBOUNDED);
            mEndItemEndPadding = (int) a.getDimension(
                    R.styleable.RowStyle_endItemEndPadding, UNBOUNDED);
            mBottomDividerStartPadding = (int) a.getDimension(
                    R.styleable.RowStyle_bottomDividerStartPadding, UNBOUNDED);
            mBottomDividerEndPadding = (int) a.getDimension(
                    R.styleable.RowStyle_bottomDividerEndPadding, UNBOUNDED);
            mActionDividerHeight = (int) a.getDimension(
                    R.styleable.RowStyle_actionDividerHeight, UNBOUNDED);
        } finally {
            a.recycle();
        }
    }

    public int getTitleItemStartPadding() {
        return mTitleItemStartPadding;
    }

    public int getTitleItemEndPadding() {
        return mTitleItemEndPadding;
    }

    public int getContentStartPadding() {
        return mContentStartPadding;
    }

    public int getContentEndPadding() {
        return mContentEndPadding;
    }

    public int getEndItemStartPadding() {
        return mEndItemStartPadding;
    }

    public int getEndItemEndPadding() {
        return mEndItemEndPadding;
    }

    public int getBottomDividerStartPadding() {
        return mBottomDividerStartPadding;
    }

    public int getBottomDividerEndPadding() {
        return mBottomDividerEndPadding;
    }

    public int getActionDividerHeight() {
        return mActionDividerHeight;
    }
}

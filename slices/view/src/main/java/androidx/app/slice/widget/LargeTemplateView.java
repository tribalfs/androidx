/*
 * Copyright 2017 The Android Open Source Project
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

import static android.app.slice.Slice.HINT_PARTIAL;
import static android.view.ViewGroup.LayoutParams.WRAP_CONTENT;

import android.annotation.TargetApi;
import android.content.Context;
import android.support.annotation.RestrictTo;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;

import androidx.app.slice.Slice;
import androidx.app.slice.core.SliceQuery;
import androidx.app.slice.view.R;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
@TargetApi(24)
public class LargeTemplateView extends SliceChildView {

    private final LargeSliceAdapter mAdapter;
    private final RecyclerView mRecyclerView;
    private final int mDefaultHeight;
    private Slice mSlice;
    private boolean mIsScrollable;

    public LargeTemplateView(Context context) {
        super(context);
        mRecyclerView = new RecyclerView(getContext());
        mRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        mAdapter = new LargeSliceAdapter(context);
        mRecyclerView.setAdapter(mAdapter);
        addView(mRecyclerView);
        mDefaultHeight = getResources().getDimensionPixelSize(R.dimen.abc_slice_large_height);
    }

    @Override
    public @SliceView.SliceMode int getMode() {
        return SliceView.MODE_LARGE;
    }

    @Override
    public void setSliceActionListener(SliceView.OnSliceActionListener observer) {
        mObserver = observer;
        if (mAdapter != null) {
            mAdapter.setSliceObserver(mObserver);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        mRecyclerView.getLayoutParams().height = WRAP_CONTENT;
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (mRecyclerView.getMeasuredHeight() > width
                || (mSlice != null && SliceQuery.hasHints(mSlice, HINT_PARTIAL))) {
            mRecyclerView.getLayoutParams().height = width;
        } else {
            mRecyclerView.getLayoutParams().height = mRecyclerView.getMeasuredHeight();
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setSlice(Slice slice) {
        mSlice = slice;
        populate();
    }

    @Override
    public void setStyle(AttributeSet attrs) {
        super.setStyle(attrs);
        mAdapter.setStyle(attrs);
    }

    private void populate() {
        if (mSlice == null) {
            return;
        }
        ListContent lc = new ListContent(mSlice);
        mAdapter.setSliceItems(lc.getRowItems(), mTintColor);
    }

    /**
     * Whether or not the content in this template should be scrollable.
     */
    public void setScrollable(boolean isScrollable) {
        // TODO -- restrict / enable how much this view can show
        mIsScrollable = isScrollable;
    }

    @Override
    public void resetView() {
        mSlice = null;
        mAdapter.setSliceItems(null, -1);
    }
}

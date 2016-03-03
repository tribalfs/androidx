/* This file is auto-generated from RowsFragment.java.  DO NOT MODIFY. */

/*
 * Copyright (C) 2014 The Android Open Source Project
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
package android.support.v17.leanback.app;

import android.animation.TimeAnimator;
import android.animation.TimeAnimator.TimeListener;
import android.os.Bundle;
import android.support.v17.leanback.R;
import android.support.v17.leanback.widget.HorizontalGridView;
import android.support.v17.leanback.widget.ItemBridgeAdapter;
import android.support.v17.leanback.widget.ListRowPresenter;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.OnItemViewSelectedListener;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.RowPresenter;
import android.support.v17.leanback.widget.VerticalGridView;
import android.support.v17.leanback.widget.ViewHolderTask;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

import java.util.ArrayList;

/**
 * An ordered set of rows of leanback widgets.
 * <p>
 * A RowsSupportFragment renders the elements of its
 * {@link android.support.v17.leanback.widget.ObjectAdapter} as a set
 * of rows in a vertical list. The elements in this adapter must be subclasses
 * of {@link android.support.v17.leanback.widget.Row}.
 * </p>
 */
public class RowsSupportFragment extends BaseRowSupportFragment {

    /**
     * Internal helper class that manages row select animation and apply a default
     * dim to each row.
     */
    final class RowViewHolderExtra implements TimeListener {
        final RowPresenter mRowPresenter;
        final Presenter.ViewHolder mRowViewHolder;

        final TimeAnimator mSelectAnimator = new TimeAnimator();

        int mSelectAnimatorDurationInUse;
        Interpolator mSelectAnimatorInterpolatorInUse;
        float mSelectLevelAnimStart;
        float mSelectLevelAnimDelta;

        RowViewHolderExtra(ItemBridgeAdapter.ViewHolder ibvh) {
            mRowPresenter = (RowPresenter) ibvh.getPresenter();
            mRowViewHolder = ibvh.getViewHolder();
            mSelectAnimator.setTimeListener(this);
        }

        @Override
        public void onTimeUpdate(TimeAnimator animation, long totalTime, long deltaTime) {
            if (mSelectAnimator.isRunning()) {
                updateSelect(totalTime, deltaTime);
            }
        }

        void updateSelect(long totalTime, long deltaTime) {
            float fraction;
            if (totalTime >= mSelectAnimatorDurationInUse) {
                fraction = 1;
                mSelectAnimator.end();
            } else {
                fraction = (float) (totalTime / (double) mSelectAnimatorDurationInUse);
            }
            if (mSelectAnimatorInterpolatorInUse != null) {
                fraction = mSelectAnimatorInterpolatorInUse.getInterpolation(fraction);
            }
            float level = mSelectLevelAnimStart + fraction * mSelectLevelAnimDelta;
            mRowPresenter.setSelectLevel(mRowViewHolder, level);
        }

        void animateSelect(boolean select, boolean immediate) {
            mSelectAnimator.end();
            final float end = select ? 1 : 0;
            if (immediate) {
                mRowPresenter.setSelectLevel(mRowViewHolder, end);
            } else if (mRowPresenter.getSelectLevel(mRowViewHolder) != end) {
                mSelectAnimatorDurationInUse = mSelectAnimatorDuration;
                mSelectAnimatorInterpolatorInUse = mSelectAnimatorInterpolator;
                mSelectLevelAnimStart = mRowPresenter.getSelectLevel(mRowViewHolder);
                mSelectLevelAnimDelta = end - mSelectLevelAnimStart;
                mSelectAnimator.start();
            }
        }

    }

    private static final String TAG = "RowsSupportFragment";
    private static final boolean DEBUG = false;

    private ItemBridgeAdapter.ViewHolder mSelectedViewHolder;
    private int mSubPosition;
    private boolean mExpand = true;
    private boolean mViewsCreated;
    private int mAlignedTop;
    private boolean mAfterEntranceTransition = true;

    private OnItemViewSelectedListener mOnItemViewSelectedListener;
    private OnItemViewClickedListener mOnItemViewClickedListener;

    // Select animation and interpolator are not intended to be
    // exposed at this moment. They might be synced with vertical scroll
    // animation later.
    int mSelectAnimatorDuration;
    Interpolator mSelectAnimatorInterpolator = new DecelerateInterpolator(2);

    private RecyclerView.RecycledViewPool mRecycledViewPool;
    private ArrayList<Presenter> mPresenterMapper;

    private ItemBridgeAdapter.AdapterListener mExternalAdapterListener;

    @Override
    protected VerticalGridView findGridViewFromRoot(View view) {
        return (VerticalGridView) view.findViewById(R.id.container_list);
    }

    /**
     * Sets an item clicked listener on the fragment.
     * OnItemViewClickedListener will override {@link View.OnClickListener} that
     * item presenter sets during {@link Presenter#onCreateViewHolder(ViewGroup)}.
     * So in general, developer should choose one of the listeners but not both.
     */
    public void setOnItemViewClickedListener(OnItemViewClickedListener listener) {
        mOnItemViewClickedListener = listener;
        if (mViewsCreated) {
            throw new IllegalStateException(
                    "Item clicked listener must be set before views are created");
        }
    }

    /**
     * Returns the item clicked listener.
     */
    public OnItemViewClickedListener getOnItemViewClickedListener() {
        return mOnItemViewClickedListener;
    }

    /**
     * @deprecated use {@link BrowseSupportFragment#enableRowScaling(boolean)} instead.
     *
     * @param enable true to enable row scaling
     */
    public void enableRowScaling(boolean enable) {
    }

    /**
     * Set the visibility of titles/hovercard of browse rows.
     */
    public void setExpand(boolean expand) {
        mExpand = expand;
        VerticalGridView listView = getVerticalGridView();
        if (listView != null) {
            final int count = listView.getChildCount();
            if (DEBUG) Log.v(TAG, "setExpand " + expand + " count " + count);
            for (int i = 0; i < count; i++) {
                View view = listView.getChildAt(i);
                ItemBridgeAdapter.ViewHolder vh
                        = (ItemBridgeAdapter.ViewHolder) listView.getChildViewHolder(view);
                setRowViewExpanded(vh, mExpand);
            }
        }
    }

    /**
     * Sets an item selection listener.
     */
    public void setOnItemViewSelectedListener(OnItemViewSelectedListener listener) {
        mOnItemViewSelectedListener = listener;
        VerticalGridView listView = getVerticalGridView();
        if (listView != null) {
            final int count = listView.getChildCount();
            for (int i = 0; i < count; i++) {
                View view = listView.getChildAt(i);
                ItemBridgeAdapter.ViewHolder ibvh = (ItemBridgeAdapter.ViewHolder)
                        listView.getChildViewHolder(view);
                getRowViewHolder(ibvh).setOnItemViewSelectedListener(mOnItemViewSelectedListener);
            }
        }
    }

    /**
     * Returns an item selection listener.
     */
    public OnItemViewSelectedListener getOnItemViewSelectedListener() {
        return mOnItemViewSelectedListener;
    }

    @Override
    void onRowSelected(RecyclerView parent, RecyclerView.ViewHolder viewHolder,
            int position, int subposition) {
        if (mSelectedViewHolder != viewHolder || mSubPosition != subposition) {
            if (DEBUG) Log.v(TAG, "new row selected position " + position + " subposition "
                    + subposition + " view " + viewHolder.itemView);
            mSubPosition = subposition;
            if (mSelectedViewHolder != null) {
                setRowViewSelected(mSelectedViewHolder, false, false);
            }
            mSelectedViewHolder = (ItemBridgeAdapter.ViewHolder) viewHolder;
            if (mSelectedViewHolder != null) {
                setRowViewSelected(mSelectedViewHolder, true, false);
            }
        }
    }

    /**
     * Get row ViewHolder at adapter position.  Returns null if the row object is not in adapter or
     * the row object has not been bound to a row view.
     *
     * @param position Position of row in adapter.
     * @return Row ViewHolder at a given adapter position.
     */
    public RowPresenter.ViewHolder getRowViewHolder(int position) {
        VerticalGridView verticalView = getVerticalGridView();
        if (verticalView == null) {
            return null;
        }
        return getRowViewHolder((ItemBridgeAdapter.ViewHolder)
                verticalView.findViewHolderForAdapterPosition(position));
    }

    @Override
    int getLayoutResourceId() {
        return R.layout.lb_rows_fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSelectAnimatorDuration = getResources().getInteger(
                R.integer.lb_browse_rows_anim_duration);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        if (DEBUG) Log.v(TAG, "onViewCreated");
        super.onViewCreated(view, savedInstanceState);
        // Align the top edge of child with id row_content.
        // Need set this for directly using RowsSupportFragment.
        getVerticalGridView().setItemAlignmentViewId(R.id.row_content);
        getVerticalGridView().setSaveChildrenPolicy(VerticalGridView.SAVE_LIMITED_CHILD);

        setAlignment(mAlignedTop);

        mRecycledViewPool = null;
        mPresenterMapper = null;
    }

    @Override
    public void onDestroyView() {
        mViewsCreated = false;
        super.onDestroyView();
    }

    void setExternalAdapterListener(ItemBridgeAdapter.AdapterListener listener) {
        mExternalAdapterListener = listener;
    }

    private static void setRowViewExpanded(ItemBridgeAdapter.ViewHolder vh, boolean expanded) {
        ((RowPresenter) vh.getPresenter()).setRowViewExpanded(vh.getViewHolder(), expanded);
    }

    private static void setRowViewSelected(ItemBridgeAdapter.ViewHolder vh, boolean selected,
            boolean immediate) {
        RowViewHolderExtra extra = (RowViewHolderExtra) vh.getExtraObject();
        extra.animateSelect(selected, immediate);
        ((RowPresenter) vh.getPresenter()).setRowViewSelected(vh.getViewHolder(), selected);
    }

    private final ItemBridgeAdapter.AdapterListener mBridgeAdapterListener =
            new ItemBridgeAdapter.AdapterListener() {
        @Override
        public void onAddPresenter(Presenter presenter, int type) {
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onAddPresenter(presenter, type);
            }
        }

        @Override
        public void onCreate(ItemBridgeAdapter.ViewHolder vh) {
            VerticalGridView listView = getVerticalGridView();
            if (listView != null) {
                // set clip children false for slide animation
                listView.setClipChildren(false);
            }
            setupSharedViewPool(vh);
            mViewsCreated = true;
            vh.setExtraObject(new RowViewHolderExtra(vh));
            // selected state is initialized to false, then driven by grid view onChildSelected
            // events.  When there is rebind, grid view fires onChildSelected event properly.
            // So we don't need do anything special later in onBind or onAttachedToWindow.
            setRowViewSelected(vh, false, true);
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onCreate(vh);
            }
        }

        @Override
        public void onAttachedToWindow(ItemBridgeAdapter.ViewHolder vh) {
            if (DEBUG) Log.v(TAG, "onAttachToWindow");
            // All views share the same mExpand value.  When we attach a view to grid view,
            // we should make sure it pick up the latest mExpand value we set early on other
            // attached views.  For no-structure-change update,  the view is rebound to new data,
            // but again it should use the unchanged mExpand value,  so we don't need do any
            // thing in onBind.
            setRowViewExpanded(vh, mExpand);
            RowPresenter rowPresenter = (RowPresenter) vh.getPresenter();
            RowPresenter.ViewHolder rowVh = rowPresenter.getRowViewHolder(vh.getViewHolder());
            rowVh.setOnItemViewSelectedListener(mOnItemViewSelectedListener);
            rowVh.setOnItemViewClickedListener(mOnItemViewClickedListener);
            rowPresenter.setEntranceTransitionState(rowVh, mAfterEntranceTransition);
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onAttachedToWindow(vh);
            }
        }

        @Override
        public void onDetachedFromWindow(ItemBridgeAdapter.ViewHolder vh) {
            if (mSelectedViewHolder == vh) {
                setRowViewSelected(mSelectedViewHolder, false, true);
                mSelectedViewHolder = null;
            }
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onDetachedFromWindow(vh);
            }
        }

        @Override
        public void onBind(ItemBridgeAdapter.ViewHolder vh) {
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onBind(vh);
            }
        }

        @Override
        public void onUnbind(ItemBridgeAdapter.ViewHolder vh) {
            setRowViewSelected(vh, false, true);
            if (mExternalAdapterListener != null) {
                mExternalAdapterListener.onUnbind(vh);
            }
        }
    };

    private void setupSharedViewPool(ItemBridgeAdapter.ViewHolder bridgeVh) {
        RowPresenter rowPresenter = (RowPresenter) bridgeVh.getPresenter();
        RowPresenter.ViewHolder rowVh = rowPresenter.getRowViewHolder(bridgeVh.getViewHolder());

        if (rowVh instanceof ListRowPresenter.ViewHolder) {
            HorizontalGridView view = ((ListRowPresenter.ViewHolder) rowVh).getGridView();
            // Recycled view pool is shared between all list rows
            if (mRecycledViewPool == null) {
                mRecycledViewPool = view.getRecycledViewPool();
            } else {
                view.setRecycledViewPool(mRecycledViewPool);
            }

            ItemBridgeAdapter bridgeAdapter =
                    ((ListRowPresenter.ViewHolder) rowVh).getBridgeAdapter();
            if (mPresenterMapper == null) {
                mPresenterMapper = bridgeAdapter.getPresenterMapper();
            } else {
                bridgeAdapter.setPresenterMapper(mPresenterMapper);
            }
        }
    }

    @Override
    void updateAdapter() {
        super.updateAdapter();
        mSelectedViewHolder = null;
        mViewsCreated = false;

        ItemBridgeAdapter adapter = getBridgeAdapter();
        if (adapter != null) {
            adapter.setAdapterListener(mBridgeAdapterListener);
        }
    }

    @Override
    public boolean onTransitionPrepare() {
        boolean prepared = super.onTransitionPrepare();
        if (prepared) {
            freezeRows(true);
        }
        return prepared;
    }

    @Override
    public void onTransitionEnd() {
        super.onTransitionEnd();
        freezeRows(false);
    }

    private void freezeRows(boolean freeze) {
        VerticalGridView verticalView = getVerticalGridView();
        if (verticalView != null) {
            final int count = verticalView.getChildCount();
            for (int i = 0; i < count; i++) {
                ItemBridgeAdapter.ViewHolder ibvh = (ItemBridgeAdapter.ViewHolder)
                        verticalView.getChildViewHolder(verticalView.getChildAt(i));
                RowPresenter rowPresenter = (RowPresenter) ibvh.getPresenter();
                RowPresenter.ViewHolder vh = rowPresenter.getRowViewHolder(ibvh.getViewHolder());
                rowPresenter.freeze(vh, freeze);
            }
        }
    }

    /**
     * For rows that willing to participate entrance transition,  this function
     * hide views if afterTransition is true,  show views if afterTransition is false.
     */
    public void setEntranceTransitionState(boolean afterTransition) {
        mAfterEntranceTransition = afterTransition;
        VerticalGridView verticalView = getVerticalGridView();
        if (verticalView != null) {
            final int count = verticalView.getChildCount();
            for (int i = 0; i < count; i++) {
                ItemBridgeAdapter.ViewHolder ibvh = (ItemBridgeAdapter.ViewHolder)
                        verticalView.getChildViewHolder(verticalView.getChildAt(i));
                RowPresenter rowPresenter = (RowPresenter) ibvh.getPresenter();
                RowPresenter.ViewHolder vh = rowPresenter.getRowViewHolder(ibvh.getViewHolder());
                rowPresenter.setEntranceTransitionState(vh, mAfterEntranceTransition);
            }
        }
    }

    /**
     * Selects a Row and perform an optional task on the Row. For example
     * <code>setSelectedPosition(10, true, new ListRowPresenterSelectItemViewHolderTask(5))</code>
     * Scroll to 11th row and selects 6th item on that row.  The method will be ignored if
     * RowsSupportFragment has not been created (i.e. before {@link #onCreateView(LayoutInflater,
     * ViewGroup, Bundle)}).
     *
     * @param rowPosition Which row to select.
     * @param smooth True to scroll to the row, false for no animation.
     * @param rowHolderTask Task to perform on the Row.
     */
    public void setSelectedPosition(int rowPosition, boolean smooth,
            final Presenter.ViewHolderTask rowHolderTask) {
        VerticalGridView verticalView = getVerticalGridView();
        if (verticalView == null) {
            return;
        }
        ViewHolderTask task = null;
        if (rowHolderTask != null) {
            task = new ViewHolderTask() {
                @Override
                public void run(RecyclerView.ViewHolder rvh) {
                    rowHolderTask.run(getRowViewHolder((ItemBridgeAdapter.ViewHolder) rvh));
                }
            };
        }
        if (smooth) {
            verticalView.setSelectedPositionSmooth(rowPosition, task);
        } else {
            verticalView.setSelectedPosition(rowPosition, task);
        }
    }

    static RowPresenter.ViewHolder getRowViewHolder(ItemBridgeAdapter.ViewHolder ibvh) {
        if (ibvh == null) {
            return null;
        }
        RowPresenter rowPresenter = (RowPresenter) ibvh.getPresenter();
        return rowPresenter.getRowViewHolder(ibvh.getViewHolder());
    }

    public boolean isScrolling() {
        return getVerticalGridView().getScrollState() != HorizontalGridView.SCROLL_STATE_IDLE;
    }

    @Override
    public void setAlignment(int windowAlignOffsetFromTop) {
        mAlignedTop = windowAlignOffsetFromTop;
        final VerticalGridView gridView = getVerticalGridView();

        if (gridView != null) {
            gridView.setItemAlignmentOffset(0);
            gridView.setItemAlignmentOffsetPercent(
                    VerticalGridView.ITEM_ALIGN_OFFSET_PERCENT_DISABLED);
            gridView.setItemAlignmentOffsetWithPadding(true);
            gridView.setWindowAlignmentOffset(mAlignedTop);
            // align to a fixed position from top
            gridView.setWindowAlignmentOffsetPercent(
                    VerticalGridView.WINDOW_ALIGN_OFFSET_PERCENT_DISABLED);
            gridView.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_NO_EDGE);
        }
    }
}

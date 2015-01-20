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
package android.support.v17.leanback.widget;

import android.support.v17.leanback.app.HeadersFragment;
import android.support.v17.leanback.graphics.ColorOverlayDimmer;
import android.view.View;
import android.view.ViewGroup;

/**
 * An abstract {@link Presenter} that renders a {@link Row}.
 *
 * <h3>Customize UI widgets</h3>
 * When a subclass of RowPresenter adds UI widgets, it should subclass
 * {@link RowPresenter.ViewHolder} and override {@link #createRowViewHolder(ViewGroup)}
 * and {@link #initializeRowViewHolder(ViewHolder)}. The subclass must use layout id
 * "row_content" for the widget that will be aligned to the title of any {@link HeadersFragment}
 * that may exist in the parent fragment. RowPresenter contains an optional and
 * replaceable {@link RowHeaderPresenter} that renders the header. You can disable
 * the default rendering or replace the Presenter with a new header presenter
 * by calling {@link #setHeaderPresenter(RowHeaderPresenter)}.
 *
 * <h3>UI events from fragments</h3>
 * RowPresenter receives calls from its parent (typically a Fragment) when:
 * <ul>
 * <li>
 * A row is selected via {@link #setRowViewSelected(Presenter.ViewHolder, boolean)}.  The event
 * is triggered immediately when there is a row selection change before the selection
 * animation is started.  Selected status may control activated status of the row (see
 * "Activated status" below).
 * Subclasses of RowPresenter may override {@link #onRowViewSelected(ViewHolder, boolean)}.
 * </li>
 * <li>
 * A row is expanded to full height via {@link #setRowViewExpanded(Presenter.ViewHolder, boolean)}
 * when BrowseFragment hides fast lane on the left.
 * The event is triggered immediately before the expand animation is started.
 * Row title is shown when row is expanded.  Expanded status may control activated status
 * of the row (see "Activated status" below).
 * Subclasses of RowPresenter may override {@link #onRowViewExpanded(ViewHolder, boolean)}.
 * </li>
 * </ul>
 *
 * <h3>Activated status</h3>
 * The activated status of a row is applied to the row view and it's children via
 * {@link View#setActivated(boolean)}.
 * The activated status is typically used to control {@link BaseCardView} info region visibility.
 * The row's activated status can be controlled by selected status and/or expanded status.
 * Call {@link #setSyncActivatePolicy(int)} and choose one of the four policies:
 * <ul>
 * <li>{@link #SYNC_ACTIVATED_TO_EXPANDED} Activated status is synced with row expanded status</li>
 * <li>{@link #SYNC_ACTIVATED_TO_SELECTED} Activated status is synced with row selected status</li>
 * <li>{@link #SYNC_ACTIVATED_TO_EXPANDED_AND_SELECTED} Activated status is set to true
 *     when both expanded and selected status are true</li>
 * <li>{@link #SYNC_ACTIVATED_CUSTOM} Activated status is not controlled by selected status
 *     or expanded status, application can control activated status by its own.
 *     Application should call {@link RowPresenter.ViewHolder#setActivated(boolean)} to change
 *     activated status of row view.
 * </li>
 * </ul>
 *
 * <h3>User events</h3>
 * RowPresenter provides {@link OnItemSelectedListener} and {@link OnItemClickedListener}.
 * If a subclass wants to add its own {@link View.OnFocusChangeListener} or
 * {@link View.OnClickListener}, it must do that in {@link #createRowViewHolder(ViewGroup)}
 * to be properly chained by the library.  Adding View listeners after
 * {@link #createRowViewHolder(ViewGroup)} is undefined and may result in
 * incorrect behavior by the library's listeners.
 *
 * <h3>Selection animation</h3>
 * <p>
 * When a user scrolls through rows, a fragment will initiate animation and call
 * {@link #setSelectLevel(Presenter.ViewHolder, float)} with float value between
 * 0 and 1.  By default, the RowPresenter draws a dim overlay on top of the row
 * view for views that are not selected. Subclasses may override this default effect
 * by having {@link #isUsingDefaultSelectEffect()} return false and overriding
 * {@link #onSelectLevelChanged(ViewHolder)} to apply a different selection effect.
 * </p>
 * <p>
 * Call {@link #setSelectEffectEnabled(boolean)} to enable/disable the select effect,
 * This will not only enable/disable the default dim effect but also subclasses must
 * respect this flag as well.
 * </p>
 */
public abstract class RowPresenter extends Presenter {

    /**
     * Don't synchronize row view activated status with selected status or expanded status,
     * application will do its own through {@link RowPresenter.ViewHolder#setActivated(boolean)}.
     */
    public static final int SYNC_ACTIVATED_CUSTOM = 0;

    /**
     * Synchronizes row view's activated status to expand status of the row view holder.
     */
    public static final int SYNC_ACTIVATED_TO_EXPANDED = 1;

    /**
     * Synchronizes row view's activated status to selected status of the row view holder.
     */
    public static final int SYNC_ACTIVATED_TO_SELECTED = 2;

    /**
     * Sets the row view's activated status to true when both expand and selected are true.
     */
    public static final int SYNC_ACTIVATED_TO_EXPANDED_AND_SELECTED = 3;

    static class ContainerViewHolder extends Presenter.ViewHolder {
        /**
         * wrapped row view holder
         */
        final ViewHolder mRowViewHolder;

        public ContainerViewHolder(RowContainerView containerView, ViewHolder rowViewHolder) {
            super(containerView);
            containerView.addRowView(rowViewHolder.view);
            if (rowViewHolder.mHeaderViewHolder != null) {
                containerView.addHeaderView(rowViewHolder.mHeaderViewHolder.view);
            }
            mRowViewHolder = rowViewHolder;
            mRowViewHolder.mContainerViewHolder = this;
        }
    }

    /**
     * A view holder for a {@link Row}.
     */
    public static class ViewHolder extends Presenter.ViewHolder {
        private static final int ACTIVATED_NOT_ASSIGNED = 0;
        private static final int ACTIVATED = 1;
        private static final int NOT_ACTIVATED = 2;

        ContainerViewHolder mContainerViewHolder;
        RowHeaderPresenter.ViewHolder mHeaderViewHolder;
        Row mRow;
        int mActivated = ACTIVATED_NOT_ASSIGNED;
        boolean mSelected;
        boolean mExpanded;
        boolean mInitialzed;
        float mSelectLevel = 0f; // initially unselected
        protected final ColorOverlayDimmer mColorDimmer;

        /**
         * Constructor for ViewHolder.
         *
         * @param view The View bound to the Row.
         */
        public ViewHolder(View view) {
            super(view);
            mColorDimmer = ColorOverlayDimmer.createDefault(view.getContext());
        }

        /**
         * Returns the Row bound to the View in this ViewHolder.
         */
        public final Row getRow() {
            return mRow;
        }

        /**
         * Returns whether the Row is in its expanded state.
         *
         * @return true if the Row is expanded, false otherwise.
         */
        public final boolean isExpanded() {
            return mExpanded;
        }

        /**
         * Returns whether the Row is selected.
         *
         * @return true if the Row is selected, false otherwise.
         */
        public final boolean isSelected() {
            return mSelected;
        }

        /**
         * Returns the current selection level of the Row.
         */
        public final float getSelectLevel() {
            return mSelectLevel;
        }

        /**
         * Returns the view holder for the Row header for this Row.
         */
        public final RowHeaderPresenter.ViewHolder getHeaderViewHolder() {
            return mHeaderViewHolder;
        }

        /**
         * Sets the row view's activated status.  The status will be applied to children through
         * {@link #syncActivatedStatus(View)}.  Application should only call this function
         * when {@link RowPresenter#getSyncActivatePolicy()} is
         * {@link RowPresenter#SYNC_ACTIVATED_CUSTOM}; otherwise the value will
         * be overwritten when expanded or selected status changes.
         */
        public final void setActivated(boolean activated) {
            mActivated = activated ? ACTIVATED : NOT_ACTIVATED;
        }

        /**
         * Synchronizes the activated status of view to the last value passed through
         * {@link RowPresenter.ViewHolder#setActivated(boolean)}. No operation if
         * {@link RowPresenter.ViewHolder#setActivated(boolean)} is never called.  Normally
         * application does not need to call this method,  {@link ListRowPresenter} automatically
         * calls this method when a child is attached to list row.   However if
         * application writes its own custom RowPresenter, it should call this method
         * when attaches a child to the row view.
         */
        public final void syncActivatedStatus(View view) {
            if (mActivated == ACTIVATED) {
                view.setActivated(true);
            } else if (mActivated == NOT_ACTIVATED) {
                view.setActivated(false);
            }
        }
    }

    private RowHeaderPresenter mHeaderPresenter = new RowHeaderPresenter();
    private OnItemSelectedListener mOnItemSelectedListener;
    private OnItemClickedListener mOnItemClickedListener;
    private OnItemViewSelectedListener mOnItemViewSelectedListener;
    private OnItemViewClickedListener mOnItemViewClickedListener;

    boolean mSelectEffectEnabled = true;
    int mSyncActivatePolicy = SYNC_ACTIVATED_TO_EXPANDED;

    @Override
    public final Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        ViewHolder vh = createRowViewHolder(parent);
        vh.mInitialzed = false;
        Presenter.ViewHolder result;
        if (needsRowContainerView()) {
            RowContainerView containerView = new RowContainerView(parent.getContext());
            if (mHeaderPresenter != null) {
                vh.mHeaderViewHolder = (RowHeaderPresenter.ViewHolder)
                        mHeaderPresenter.onCreateViewHolder((ViewGroup) vh.view);
            }
            result = new ContainerViewHolder(containerView, vh);
        } else {
            result = vh;
        }
        initializeRowViewHolder(vh);
        if (!vh.mInitialzed) {
            throw new RuntimeException("super.initializeRowViewHolder() must be called");
        }
        return result;
    }

    /**
     * Called to create a ViewHolder object for a Row. Subclasses will override
     * this method to return a different concrete ViewHolder object. 
     *
     * @param parent The parent View for the Row's view holder.
     * @return A ViewHolder for the Row's View.
     */
    protected abstract ViewHolder createRowViewHolder(ViewGroup parent);

    /**
     * Called after a {@link RowPresenter.ViewHolder} is created for a Row.
     * Subclasses may override this method and start by calling
     * super.initializeRowViewHolder(ViewHolder).
     *
     * @param vh The ViewHolder to initialize for the Row.
     */
    protected void initializeRowViewHolder(ViewHolder vh) {
        vh.mInitialzed = true;
        // set clip children to false for slide transition
        ((ViewGroup) vh.view).setClipChildren(false);
        if (vh.mContainerViewHolder != null) {
            ((ViewGroup) vh.mContainerViewHolder.view).setClipChildren(false);
        }
    }

    /**
     * Set the Presenter used for rendering the header. Can be null to disable
     * header rendering. The method must be called before creating any Row Views.
     */
    public final void setHeaderPresenter(RowHeaderPresenter headerPresenter) {
        mHeaderPresenter = headerPresenter;
    }

    /**
     * Get the Presenter used for rendering the header, or null if none has been
     * set.
     */
    public final RowHeaderPresenter getHeaderPresenter() {
        return mHeaderPresenter;
    }

    /**
     * Get the {@link RowPresenter.ViewHolder} from the given Presenter
     * ViewHolder.
     */
    public final ViewHolder getRowViewHolder(Presenter.ViewHolder holder) {
        if (holder instanceof ContainerViewHolder) {
            return ((ContainerViewHolder) holder).mRowViewHolder;
        } else {
            return (ViewHolder) holder;
        }
    }

    /**
     * Set the expanded state of a Row view.
     *
     * @param holder The Row ViewHolder to set expanded state on.
     * @param expanded True if the Row is expanded, false otherwise.
     */
    public final void setRowViewExpanded(Presenter.ViewHolder holder, boolean expanded) {
        ViewHolder rowViewHolder = getRowViewHolder(holder);
        rowViewHolder.mExpanded = expanded;
        onRowViewExpanded(rowViewHolder, expanded);
    }

    /**
     * Set the selected state of a Row view.
     *
     * @param holder The Row ViewHolder to set expanded state on.
     * @param selected True if the Row is expanded, false otherwise.
     */
    public final void setRowViewSelected(Presenter.ViewHolder holder, boolean selected) {
        ViewHolder rowViewHolder = getRowViewHolder(holder);
        rowViewHolder.mSelected = selected;
        onRowViewSelected(rowViewHolder, selected);
    }

    /**
     * Subclass may override this to respond to expanded state changes of a Row.
     * The default implementation will hide/show the header view. Subclasses may
     * make visual changes to the Row View but must not create animation on the
     * Row view.
     */
    protected void onRowViewExpanded(ViewHolder vh, boolean expanded) {
        updateHeaderViewVisibility(vh);
        updateActivateStatus(vh, vh.view);
    }

    /**
     * Update view's activate status according to {@link #getSyncActivatePolicy()} and the
     * selected status and expanded status of the RowPresenter ViewHolder.
     */
    private void updateActivateStatus(ViewHolder vh, View view) {
        switch (mSyncActivatePolicy) {
            case SYNC_ACTIVATED_TO_EXPANDED:
                vh.setActivated(vh.isExpanded());
                break;
            case SYNC_ACTIVATED_TO_SELECTED:
                vh.setActivated(vh.isSelected());
                break;
            case SYNC_ACTIVATED_TO_EXPANDED_AND_SELECTED:
                vh.setActivated(vh.isExpanded() && vh.isSelected());
                break;
        }
        vh.syncActivatedStatus(view);
    }

    /**
     * Sets policy of updating row view activated status.  Can be one of:
     * <li> Default value {@link #SYNC_ACTIVATED_TO_EXPANDED}
     * <li> {@link #SYNC_ACTIVATED_TO_SELECTED}
     * <li> {@link #SYNC_ACTIVATED_TO_EXPANDED_AND_SELECTED}
     * <li> {@link #SYNC_ACTIVATED_CUSTOM}
     */
    public final void setSyncActivatePolicy(int syncActivatePolicy) {
        mSyncActivatePolicy = syncActivatePolicy;
    }

    /**
     * Returns policy of updating row view activated status.  Can be one of:
     * <li> Default value {@link #SYNC_ACTIVATED_TO_EXPANDED}
     * <li> {@link #SYNC_ACTIVATED_TO_SELECTED}
     * <li> {@link #SYNC_ACTIVATED_TO_EXPANDED_AND_SELECTED}
     * <li> {@link #SYNC_ACTIVATED_CUSTOM}
     */
    public final int getSyncActivatePolicy() {
        return mSyncActivatePolicy;
    }

    /**
     * Subclass may override this to respond to selected state changes of a Row.
     * Subclass may make visual changes to Row view but must not create
     * animation on the Row view.
     */
    protected void onRowViewSelected(ViewHolder vh, boolean selected) {
        if (selected) {
            if (mOnItemViewSelectedListener != null) {
                mOnItemViewSelectedListener.onItemSelected(null, null, vh, vh.getRow());
            }
            if (mOnItemSelectedListener != null) {
                mOnItemSelectedListener.onItemSelected(null, vh.getRow());
            }
        }
        updateHeaderViewVisibility(vh);
        updateActivateStatus(vh, vh.view);
    }

    private void updateHeaderViewVisibility(ViewHolder vh) {
        if (mHeaderPresenter != null && vh.mHeaderViewHolder != null) {
            RowContainerView containerView = ((RowContainerView) vh.mContainerViewHolder.view);
            containerView.showHeader(vh.isExpanded());
        }
    }

    /**
     * Set the current select level to a value between 0 (unselected) and 1 (selected).
     * Subclasses may override {@link #onSelectLevelChanged(ViewHolder)} to
     * respond to changes in the selected level.
     */
    public final void setSelectLevel(Presenter.ViewHolder vh, float level) {
        ViewHolder rowViewHolder = getRowViewHolder(vh);
        rowViewHolder.mSelectLevel = level;
        onSelectLevelChanged(rowViewHolder);
    }

    /**
     * Get the current select level. The value will be between 0 (unselected) 
     * and 1 (selected).
     */
    public final float getSelectLevel(Presenter.ViewHolder vh) {
        return getRowViewHolder(vh).mSelectLevel;
    }

    /**
     * Callback when select level is changed. The default implementation applies
     * the select level to {@link RowHeaderPresenter#setSelectLevel(RowHeaderPresenter.ViewHolder, float)}
     * when {@link #getSelectEffectEnabled()} is true. Subclasses may override
     * this function and implement a different select effect. In this case, you
     * should also override {@link #isUsingDefaultSelectEffect()} to disable
     * the default dimming effect applied by the library.
     */
    protected void onSelectLevelChanged(ViewHolder vh) {
        if (getSelectEffectEnabled()) {
            vh.mColorDimmer.setActiveLevel(vh.mSelectLevel);
            if (vh.mHeaderViewHolder != null) {
                mHeaderPresenter.setSelectLevel(vh.mHeaderViewHolder, vh.mSelectLevel);
            }
            if (isUsingDefaultSelectEffect()) {
                ((RowContainerView) vh.mContainerViewHolder.view).setForegroundColor(
                        vh.mColorDimmer.getPaint().getColor());
            }
        }
    }

    /**
     * Enables or disables the row selection effect.
     * This will not only affect the default dim effect, but subclasses must
     * respect this flag as well.
     */
    public final void setSelectEffectEnabled(boolean applyDimOnSelect) {
        mSelectEffectEnabled = applyDimOnSelect;
    }

    /**
     * Returns true if the row selection effect is enabled.
     * This value not only determines whether the default dim implementation is
     * used, but subclasses must also respect this flag.
     */
    public final boolean getSelectEffectEnabled() {
        return mSelectEffectEnabled;
    }

    /**
     * Return whether this RowPresenter is using the default dimming effect
     * provided by the library.  Subclasses may(most likely) return false and
     * override {@link #onSelectLevelChanged(ViewHolder)}.
     */
    public boolean isUsingDefaultSelectEffect() {
        return true;
    }

    final boolean needsDefaultSelectEffect() {
        return isUsingDefaultSelectEffect() && getSelectEffectEnabled();
    }

    final boolean needsRowContainerView() {
        return mHeaderPresenter != null || needsDefaultSelectEffect();
    }

    /**
     * Return true if the Row view can draw outside its bounds.
     */
    public boolean canDrawOutOfBounds() {
        return false;
    }

    @Override
    public final void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        onBindRowViewHolder(getRowViewHolder(viewHolder), item);
    }

    protected void onBindRowViewHolder(ViewHolder vh, Object item) {
        vh.mRow = (Row) item;
        if (vh.mHeaderViewHolder != null) {
            mHeaderPresenter.onBindViewHolder(vh.mHeaderViewHolder, item);
        }
    }

    @Override
    public final void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        onUnbindRowViewHolder(getRowViewHolder(viewHolder));
    }

    protected void onUnbindRowViewHolder(ViewHolder vh) {
        if (vh.mHeaderViewHolder != null) {
            mHeaderPresenter.onUnbindViewHolder(vh.mHeaderViewHolder);
        }
        vh.mRow = null;
    }

    @Override
    public final void onViewAttachedToWindow(Presenter.ViewHolder holder) {
        onRowViewAttachedToWindow(getRowViewHolder(holder));
    }

    protected void onRowViewAttachedToWindow(ViewHolder vh) {
        if (vh.mHeaderViewHolder != null) {
            mHeaderPresenter.onViewAttachedToWindow(vh.mHeaderViewHolder);
        }
    }

    @Override
    public final void onViewDetachedFromWindow(Presenter.ViewHolder holder) {
        onRowViewDetachedFromWindow(getRowViewHolder(holder));
    }

    protected void onRowViewDetachedFromWindow(ViewHolder vh) {
        if (vh.mHeaderViewHolder != null) {
            mHeaderPresenter.onViewDetachedFromWindow(vh.mHeaderViewHolder);
        }
        cancelAnimationsRecursive(vh.view);
    }

    /**
     * Set the listener for item or row selection. A RowPresenter fires a row
     * selection event with a null item. Subclasses (e.g. {@link ListRowPresenter})
     * can fire a selection event with the selected item.
     */
    public final void setOnItemSelectedListener(OnItemSelectedListener listener) {
        mOnItemSelectedListener = listener;
    }

    /**
     * Get the listener for item or row selection.
     */
    public final OnItemSelectedListener getOnItemSelectedListener() {
        return mOnItemSelectedListener;
    }

    /**
     * Set the listener for item click events. A RowPresenter does not use this
     * listener, but a subclass may fire an item click event if it has the concept
     * of an item. The {@link OnItemClickedListener} will override any
     * {@link View.OnClickListener} that an item's Presenter sets during
     * {@link Presenter#onCreateViewHolder(ViewGroup)}. So in general, you
     * should choose to use an OnItemClickedListener or a {@link
     * View.OnClickListener}, but not both.
     */
    public final void setOnItemClickedListener(OnItemClickedListener listener) {
        mOnItemClickedListener = listener;
    }

    /**
     * Get the listener for item click events.
     */
    public final OnItemClickedListener getOnItemClickedListener() {
        return mOnItemClickedListener;
    }

    /**
     * Set listener for item or row selection.  RowPresenter fires row selection
     * event with null item, subclass of RowPresenter e.g. {@link ListRowPresenter} can
     * fire a selection event with selected item.
     */
    public final void setOnItemViewSelectedListener(OnItemViewSelectedListener listener) {
        mOnItemViewSelectedListener = listener;
    }

    /**
     * Get listener for item or row selection.
     */
    public final OnItemViewSelectedListener getOnItemViewSelectedListener() {
        return mOnItemViewSelectedListener;
    }

    /**
     * Set listener for item click event.  RowPresenter does nothing but subclass of
     * RowPresenter may fire item click event if it does have a concept of item.
     * OnItemViewClickedListener will override {@link View.OnClickListener} that
     * item presenter sets during {@link Presenter#onCreateViewHolder(ViewGroup)}.
     * So in general,  developer should choose one of the listeners but not both.
     */
    public final void setOnItemViewClickedListener(OnItemViewClickedListener listener) {
        mOnItemViewClickedListener = listener;
    }

    /**
     * Set listener for item click event.
     */
    public final OnItemViewClickedListener getOnItemViewClickedListener() {
        return mOnItemViewClickedListener;
    }

    /**
     * Freeze/Unfreeze the row, typically used when transition starts/ends.
     * This method is called by fragment, app should not call it directly.
     */
    public void freeze(ViewHolder holder, boolean freeze) {
    }

    /**
     * Change visibility of views, entrance transition will be run against the views that
     * change visibilities.  Subclass may override and begin with calling
     * super.setEntranceTransitionState().  This method is called by fragment,
     * app should not call it directly.
     */
    public void setEntranceTransitionState(ViewHolder holder, boolean afterTransition) {
        if (holder.mHeaderViewHolder != null) {
            holder.mHeaderViewHolder.view.setVisibility(afterTransition ?
                    View.VISIBLE : View.INVISIBLE);
        }
    }
}

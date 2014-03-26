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
import android.view.View;
import android.view.ViewGroup;

/**
 * A presenter that renders {@link Row}.
 *
 * <h3>Customize UI widgets</h3>
 * When subclass of RowPresenter adds UI widgets,  it should subclass
 * {@link RowPresenter.ViewHolder} and override {@link #createRowViewHolder(ViewGroup)}
 * and {@link #initializeRowViewHolder(ViewHolder)}.  Subclass must use layout id
 * "row_content" for the widget that will be aligned to title of {@link HeadersFragment}.
 * RowPresenter contains an optional and replaceable {@link RowHeaderPresenter} that
 * renders header.  User can disable default rendering or replace with a new header presenter
 * by calling {@link #setHeaderPresenter(RowHeaderPresenter)}.
 *
 * <h3>UI events from fragments</h3>
 * In addition to {@link Presenter} which defines how to render and bind data to row view,
 * RowPresenter receives calls from upper level(typically a fragment) when:
 * <ul>
 * <li>
 * Row is selected via {@link #setRowViewSelected(ViewHolder, boolean)}.  The event
 * is triggered immediately when there is a row selection change before the selection
 * animation is started.
 * Subclass of RowPresenter may override and add more works in
 * {@link #onRowViewSelected(ViewHolder, boolean)}.
 * </li>
 * <li>
 * Row is expanded to full width via {@link #setRowViewExpanded(ViewHolder, boolean)}.
 * The event is triggered immediately before the expand animation is started.
 * Subclass of RowPresenter may override and add more works in
 * {@link #onRowViewExpanded(ViewHolder, boolean)}.
 * </li>
 * </ul>
 *
 * <h3>User events:</h3>
 * RowPresenter provides {@link OnItemSelectedListener} and {@link OnItemClickedListener}.
 * If subclass wants to add its own {@link View.OnFocusChangeListener} or
 * {@link View.OnClickListener}, it must do that in {@link #createRowViewHolder(ViewGroup)}
 * to be properly chained by framework.  Adding view listeners after
 * {@link #createRowViewHolder(ViewGroup)} will interfere framework's listeners.
 *
 * <h3>Selection animation</h3>
 * <p>
 * When user scrolls through rows,  fragment will initiate animation and call
 * {@link #setSelectLevel(ViewHolder, float)} with float value 0~1.  By default, fragment
 * draws a dim overlay on top of row view for views not selected.  Subclass may override
 * this default effect by having {@link #isUsingDefaultSelectEffect()} return false
 * and override {@link #onSelectLevelChanged(ViewHolder)} to apply its own selection effect.
 * </p>
 * <p>
 * Call {@link #setSelectEffectEnabled(boolean)} to enable/disable select effect,
 * This is not only for enable/disable default dim implementation but also subclass must
 * respect this flag.
 * </p>
 */
public abstract class RowPresenter extends Presenter {

    public static class ViewHolder extends Presenter.ViewHolder {
        RowHeaderPresenter.ViewHolder mHeaderViewHolder;
        Row mRow;
        boolean mSelected;
        boolean mExpanded;
        boolean mInitialzed;
        float mSelectLevel = 0f; // initially unselected
        public ViewHolder(View view) {
            super(view);
        }
        public final Row getRow() {
            return mRow;
        }
        public final boolean isExpanded() {
            return mExpanded;
        }
        public final boolean isSelected() {
            return mSelected;
        }
        public final float getSelectLevel() {
            return mSelectLevel;
        }
        public final RowHeaderPresenter.ViewHolder getHeaderViewHolder() {
            return mHeaderViewHolder;
        }
    }

    private RowHeaderPresenter mHeaderPresenter = new RowHeaderPresenter();
    private OnItemSelectedListener mOnItemSelectedListener;
    private OnItemClickedListener mOnItemClickedListener;

    boolean mSelectEffectEnabled = true;

    @Override
    public final Presenter.ViewHolder onCreateViewHolder(ViewGroup parent) {
        ViewHolder vh = createRowViewHolder(parent);
        vh.mInitialzed = false;
        initializeRowViewHolder(vh);
        if (!vh.mInitialzed) {
            throw new RuntimeException("super.initializeRowViewHolder() must be called");
        }
        return vh;
    }

    /**
     * Called to create a ViewHolder object for row,  subclass of {@link RowPresenter}
     * should override and return a different concrete ViewHolder object. 
     */
    protected abstract ViewHolder createRowViewHolder(ViewGroup parent);

    /**
     * Called after a {@link RowPresenter.ViewHolder} is created,
     * subclass of {@link RowPresenter} may override this method and start with calling
     * super.initializeRowViewHolder(ViewHolder).
     */
    protected void initializeRowViewHolder(ViewHolder vh) {
        if (mHeaderPresenter != null) {
            vh.mHeaderViewHolder = (RowHeaderPresenter.ViewHolder)
                    mHeaderPresenter.onCreateViewHolder((ViewGroup) vh.view);
            ((ViewGroup) vh.view).addView(vh.mHeaderViewHolder.view, 0);
        }
        vh.mInitialzed = true;
    }

    /**
     * Change the presenter used for rendering header. Can be null to disable header rendering.
     * The method must be called before creating any row view.
     */
    public final void setHeaderPresenter(RowHeaderPresenter headerPresenter) {
        mHeaderPresenter = headerPresenter;
    }

    /**
     * Get optional presenter used for rendering header.  May return null.
     */
    public final RowHeaderPresenter getHeaderPresenter() {
        return mHeaderPresenter;
    }

    /**
     * Change expanded state of row view.
     */
    public final void setRowViewExpanded(ViewHolder holder, boolean expanded) {
        holder.mExpanded = expanded;
        onRowViewExpanded(holder, expanded);
    }

    /**
     * Change select state of row view.
     */
    public final void setRowViewSelected(ViewHolder holder, boolean selected) {
        holder.mSelected = selected;
        onRowViewSelected(holder, selected);
    }

    /**
     * Subclass may override and respond to expanded state change of row in fragment.
     * Default implementation hide/show header view depending on expanded state.
     * Subclass may make visual changes to row view but not allowed to create
     * animation on the row view.
     */
    protected void onRowViewExpanded(ViewHolder vh, boolean expanded) {
        if (mHeaderPresenter != null && vh.mHeaderViewHolder != null) {
            mHeaderPresenter.setHidden(vh.mHeaderViewHolder, !expanded);
        }
    }

    /**
     * Subclass may override and respond to event Row is selected.
     * Subclass may make visual changes to row view but not allowed to create
     * animation on the row view.
     */
    protected void onRowViewSelected(ViewHolder vh, boolean selected) {
        if (selected && mOnItemSelectedListener != null) {
            mOnItemSelectedListener.onItemSelected(null, vh.getRow());
        }
    }

    /**
     * Set current select level from 0(unselected) to 1(selected).
     * Subclass should override {@link #onSelectLevelChanged(ViewHolder)}.
     */
    public final void setSelectLevel(ViewHolder vh, float level) {
        vh.mSelectLevel = level;
        onSelectLevelChanged(vh);
    }

    /**
     * Get current select level from 0(unselected) to 1(selected).
     */
    public final float getSelectLevel(ViewHolder vh) {
        return vh.mSelectLevel;
    }

    /**
     * Callback when select level is changed.  Default implementation applies select level
     * to {@link RowHeaderPresenter#setSelectLevel(RowHeaderPresenter.ViewHolder, float)}
     * when {@link #getSelectEffectEnabled()} is true.
     * Subclass may override this function and implements its own select effect.  When it
     * overrides,  it should also override {@link #isUsingDefaultSelectEffect()} to disable
     * the default dimming effect applied by framework.
     */
    protected void onSelectLevelChanged(ViewHolder vh) {
        if (getSelectEffectEnabled() && vh.mHeaderViewHolder != null) {
            mHeaderPresenter.setSelectLevel(vh.mHeaderViewHolder, vh.mSelectLevel);
        }
    }

    /**
     * Enables or disables the row selection effect.
     * This is not only for enable/disable default dim implementation but also subclass must
     * respect this flag.
     */
    public final void setSelectEffectEnabled(boolean applyDimOnSelect) {
        mSelectEffectEnabled = applyDimOnSelect;
    }

    /**
     * Returns true if row selection effect is enabled.
     * This is not only for enable/disable default dim implementation but also subclass must
     * respect this flag.
     */
    public final boolean getSelectEffectEnabled() {
        return mSelectEffectEnabled;
    }

    /**
     * Return if using default dimming effect provided by framework (fragment).  Subclass
     * may(most likely) return false and override {@link #onSelectLevelChanged(ViewHolder)}.
     */
    public boolean isUsingDefaultSelectEffect() {
        return true;
    }

    @Override
    public void onBindViewHolder(Presenter.ViewHolder viewHolder, Object item) {
        ViewHolder vh = (ViewHolder) viewHolder;
        vh.mRow = (Row) item;
        if (vh.mHeaderViewHolder != null) {
            mHeaderPresenter.onBindViewHolder(vh.mHeaderViewHolder, item);
        }
        vh.mSelected = false;
        vh.mExpanded = false;
        onRowViewExpanded(vh, false);
        onRowViewSelected(vh, false);
    }

    @Override
    public void onUnbindViewHolder(Presenter.ViewHolder viewHolder) {
        ViewHolder vh = (ViewHolder) viewHolder;
        if (vh.mHeaderViewHolder != null) {
            mHeaderPresenter.onUnbindViewHolder(vh.mHeaderViewHolder);
        }
        vh.mRow = null;
    }

    @Override
    public void onViewAttachedToWindow(Presenter.ViewHolder holder) {
        ViewHolder vh = (ViewHolder) holder;
        if (vh.mHeaderViewHolder != null) {
            mHeaderPresenter.onViewAttachedToWindow(vh.mHeaderViewHolder);
        }
    }

    @Override
    public void onViewDetachedFromWindow(Presenter.ViewHolder holder) {
        ViewHolder vh = (ViewHolder) holder;
        if (vh.mHeaderViewHolder != null) {
            mHeaderPresenter.onViewDetachedFromWindow(vh.mHeaderViewHolder);
        }
    }

    /**
     * Set listener for item or row selection.  RowPresenter fires row selection
     * event with null item, subclass of RowPresenter e.g. {@link ListRowPresenter} can
     * fire a selection event with selected item.
     */
    public final void setOnItemSelectedListener(OnItemSelectedListener listener) {
        mOnItemSelectedListener = listener;
    }

    /**
     * Get listener for item or row selection.
     */
    public final OnItemSelectedListener getOnItemSelectedListener() {
        return mOnItemSelectedListener;
    }

    /**
     * Set listener for item click event.  RowPresenter does nothing but subclass of
     * RowPresenter may fire item click event if it does have a concept of item.
     * OnItemClickedListener will override {@link View.OnClickListener} that
     * item presenter sets during {@link Presenter#onCreateViewHolder(ViewGroup)}.
     * So in general,  developer should choose one of the listeners but not both.
     */
    public final void setOnItemClickedListener(OnItemClickedListener listener) {
        mOnItemClickedListener = listener;
    }

    /**
     * Set listener for item click event.
     */
    public final OnItemClickedListener getOnItemClickedListener() {
        return mOnItemClickedListener;
    }
}

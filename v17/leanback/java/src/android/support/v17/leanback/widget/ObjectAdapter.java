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

import android.database.Observable;

/**
 * Adapter for leanback activities.  Provides access to a data model and is
 * decoupled from the presentation of the items via {@link PresenterSelector}.
 */
public abstract class ObjectAdapter {
    /**
     * A DataObserver can be notified when an ObjectAdapter's underlying data
     * changes. Separate methods provide notifications about different types of
     * changes.
     */
    public static abstract class DataObserver {
        /**
         * Called whenever the ObjectAdapter's data has changed in some manner
         * outside of the set of changes covered by the other range based change
         * notification methods.
         */
        public void onChanged() {
        }

        /**
         * Called when a range of items in the ObjectAdapter has changed. The
         * basic ordering and structure of the ObjectAdapter has not changed.
         *
         * @param positionStart The position of the first item that changed.
         * @param itemCount The number of items changed.
         */
        public void onItemRangeChanged(int positionStart, int itemCount) {
            onChanged();
        }

        /**
         * Called when a range of items is inserted into the ObjectAdapter.
         *
         * @param positionStart The position of the first inserted item.
         * @param itemCount The number of items inserted.
         */
        public void onItemRangeInserted(int positionStart, int itemCount) {
            onChanged();
        }

        /**
         * Called when a range of items is removed from the ObjectAdapter.
         *
         * @param positionStart The position of the first removed item.
         * @param itemCount The number of items removed.
         */
        public void onItemRangeRemoved(int positionStart, int itemCount) {
            onChanged();
        }
    }

    private static class DataObservable extends Observable<DataObserver> {
        public boolean hasObservers() {
            return !mObservers.isEmpty();
        }

        public void notifyChanged() {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onChanged();
            }
        }

        public void notifyItemRangeChanged(int positionStart, int itemCount) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeChanged(positionStart, itemCount);
            }
        }

        public void notifyItemRangeInserted(int positionStart, int itemCount) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeInserted(positionStart, itemCount);
            }
        }

        public void notifyItemRangeRemoved(int positionStart, int itemCount) {
            for (int i = mObservers.size() - 1; i >= 0; i--) {
                mObservers.get(i).onItemRangeRemoved(positionStart, itemCount);
            }
        }
    }

    private static class SinglePresenterSelector extends PresenterSelector {
        private final Presenter mPresenter;
        public SinglePresenterSelector(Presenter presenter) {
            mPresenter = presenter;
        }
        public Presenter getPresenter(Object item) {
            return mPresenter;
        }
    }

    private final DataObservable mObservable = new DataObservable();
    private boolean mHasStableIds;
    private PresenterSelector mPresenterSelector;

    /**
     * Construct an adapter with the given {@link PresenterSelector}.
     */
    public ObjectAdapter(PresenterSelector presenterSelector) {
        setPresenterSelector(presenterSelector);
    }

    /**
     * Construct an adapter that uses the given {@link Presenter} for all items.
     */
    public ObjectAdapter(Presenter presenter) {
        setPresenterSelector(new SinglePresenterSelector(presenter));
    }

    /**
     * Construct an adapter.
     */
    public ObjectAdapter() {
    }

    /**
     * Set the presenter selector.  May not be null.
     */
    public void setPresenterSelector(PresenterSelector presenterSelector) {
        if (presenterSelector == null) {
            throw new IllegalArgumentException("Presenter selector must not be null");
        }
        final boolean update = (mPresenterSelector != null);
        mPresenterSelector = presenterSelector;
        if (update) {
            notifyChanged();
        }
    }

    /**
     * Returns the presenter selector;
     */
    public PresenterSelector getPresenterSelector() {
        return mPresenterSelector;
    }

    /**
     * Register a DataObserver for data change notifications.
     */
    public void registerObserver(DataObserver observer) {
        mObservable.registerObserver(observer);
    }

    /**
     * Unregister a DataObserver for data change notifications.
     */
    public void unregisterObserver(DataObserver observer) {
        mObservable.unregisterObserver(observer);
    }

    /**
     * Unregister all DataObservers for this ObservableList.
     */
    public void unregisterAllObservers() {
        mObservable.unregisterAll();
    }

    final protected void notifyItemRangeChanged(int positionStart, int itemCount) {
        mObservable.notifyItemRangeChanged(positionStart, itemCount);
    }

    final protected void notifyItemRangeInserted(int positionStart, int itemCount) {
        mObservable.notifyItemRangeInserted(positionStart, itemCount);
    }

    final protected void notifyItemRangeRemoved(int positionStart, int itemCount) {
        mObservable.notifyItemRangeRemoved(positionStart, itemCount);
    }

    final protected void notifyChanged() {
        mObservable.notifyChanged();
    }

    /**
     * Indicates whether the item ids are stable across changes to the
     * underlying data.
     */
    public boolean hasStableIds() {
        return mHasStableIds;
    }

    /**
     * Sets whether the item ids are stable across changes to the underlying
     * data.
     */
    public void setHasStableIds(boolean hasStableIds) {
        mHasStableIds = hasStableIds;
    }

    /**
     * Returns the {@link Presenter} for the given item from the adapter.
     */
    public Presenter getPresenter(Object item) {
        if (mPresenterSelector == null) {
            throw new IllegalStateException("Presenter selector must not be null");
        }
        return mPresenterSelector.getPresenter(item);
    }

    /**
     * Returns the number of items in the adapter.
     */
    public abstract int size();

    /**
     * Returns the item for the given index.
     */
    public abstract Object get(int index);
}

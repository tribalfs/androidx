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

package android.arch.util.paging;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.WorkerThread;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
class TiledPagedList<T> extends PageArrayList<T> {

    private final TiledDataSource<T> mDataSource;
    private final Executor mMainThreadExecutor;
    private final Executor mBackgroundThreadExecutor;
    private final Config mConfig;

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final List<T> mLoadingPlaceholder = new AbstractList<T>() {
        @Override
        public T get(int i) {
            return null;
        }

        @Override
        public int size() {
            return 0;
        }
    };

    private int mLastLoad = -1;
    private T mLastItem;

    private AtomicBoolean mDetached = new AtomicBoolean(false);

    private ArrayList<Callback> mCallbacks = new ArrayList<>();

    @WorkerThread
    TiledPagedList(@NonNull TiledDataSource<T> dataSource,
            @NonNull Executor mainThreadExecutor,
            @NonNull Executor backgroundThreadExecutor,
            Config config,
            int position) {
        super(config.mPageSize, dataSource.loadCount());

        mDataSource = dataSource;
        mMainThreadExecutor = mainThreadExecutor;
        mBackgroundThreadExecutor = backgroundThreadExecutor;
        mConfig = config;

        position = Math.min(Math.max(0, position), mCount);

        int firstPage = position / mPageSize;
        List<T> firstPageData = dataSource.loadRange(firstPage * mPageSize, mPageSize);
        if (firstPageData != null) {
            mPageIndexOffset = firstPage;
            mPages.add(firstPageData);
            mLastLoad = position;
            mLastItem = firstPageData.get(position % mPageSize);
        } else {
            detach();
            return;
        }

        int secondPage = (position % mPageSize < mPageSize / 2) ? firstPage - 1 : firstPage + 1;
        if (secondPage < 0 || secondPage > mMaxPageCount) {
            // no second page to load
            return;
        }
        List<T> secondPageData = dataSource.loadRange(secondPage * mPageSize, mPageSize);
        if (secondPageData != null) {
            boolean before = secondPage < firstPage;
            mPages.add(before ? 0 : 1, secondPageData);
            if (before) {
                mPageIndexOffset--;
            }
            return;
        }
        detach();
    }

    @Override
    public T get(int index) {
        T item = super.get(index);
        if (item != null) {
            mLastItem = item;
        }
        return item;
    }

    @Override
    public void loadAround(int index) {
        mLastLoad = index;

        int minimumPage = Math.max((index - mConfig.mPrefetchDistance) / mPageSize, 0);
        int maximumPage = Math.min((index + mConfig.mPrefetchDistance) / mPageSize,
                mMaxPageCount - 1);

        if (minimumPage < mPageIndexOffset) {
            for (int i = 0; i < mPageIndexOffset - minimumPage; i++) {
                mPages.add(0, null);
            }
            mPageIndexOffset = minimumPage;
        }
        if (maximumPage >= mPageIndexOffset + mPages.size()) {
            for (int i = mPages.size(); i <= maximumPage - mPageIndexOffset; i++) {
                mPages.add(mPages.size(), null);
            }
        }
        for (int i = minimumPage; i <= maximumPage; i++) {
            scheduleLoadPage(i);
        }
    }

    private void scheduleLoadPage(final int pageIndex) {
        final int localPageIndex = pageIndex - mPageIndexOffset;

        if (mPages.get(localPageIndex) != null) {
            // page is present in list, and non-null - don't need to load
            return;
        }
        mPages.set(localPageIndex, mLoadingPlaceholder);

        mBackgroundThreadExecutor.execute(new Runnable() {
            @Override
            public void run() {
                if (mDetached.get()) {
                    return;
                }
                final List<T> data = mDataSource.loadRange(pageIndex * mPageSize, mPageSize);
                if (data != null) {
                    mMainThreadExecutor.execute(new Runnable() {
                        @Override
                        public void run() {
                            if (mDetached.get()) {
                                return;
                            }
                            loadPageImpl(pageIndex, data);
                        }
                    });
                } else {
                    detach();
                }
            }
        });

    }

    private void loadPageImpl(int pageIndex, List<T> data) {
        int localPageIndex = pageIndex - mPageIndexOffset;

        if (mPages.get(localPageIndex) != mLoadingPlaceholder) {
            throw new IllegalStateException("Data inserted before requested.");
        }
        mPages.set(localPageIndex, data);
        for (Callback callback : mCallbacks) {
            callback.onChanged(pageIndex * mPageSize, data.size());
        }
    }

    @Override
    public boolean isImmutable() {
        // TODO: consider counting loaded pages, return true if mLoadedPages == mMaxPageCount
        // Note: could at some point want to support growing past max count, or grow dynamically
        return isDetached();
    }

    @Override
    public void addCallback(@Nullable PagedList<T> previousSnapshot, @NonNull Callback callback) {
        PageArrayList<T> snapshot = (PageArrayList<T>) previousSnapshot;
        if (snapshot != this && snapshot != null) {
            // loop through each page and signal the callback for any pages that are present now,
            // but not in the snapshot.
            for (int i = 0; i < mPages.size(); i++) {
                int pageIndex = i + mPageIndexOffset;
                int pageCount = 0;
                // count number of consecutive pages that were added since the snapshot...
                while (pageCount < mPages.size()
                        && hasPage(pageIndex + pageCount)
                        && !snapshot.hasPage(pageIndex + pageCount)) {
                    pageCount++;
                }
                // and signal them all at once to the callback
                if (pageCount > 0) {
                    callback.onChanged(pageIndex * mPageSize, mPageSize * pageCount);
                    i += pageCount - 1;
                }
            }
        }
        mCallbacks.add(callback);
    }

    @Override
    public void removeCallback(@NonNull Callback callback) {
        mCallbacks.remove(callback);
    }

    /**
     * Returns the last position accessed by the PagedList. Can be used to initialize loads in
     * subsequent PagedList versions.
     *
     * @return Last position accessed by the PagedList.
     */
    @SuppressWarnings("WeakerAccess")
    public int getLastLoad() {
        return mLastLoad;
    }

    public T getLastItem() {
        return mLastItem;
    }

    /**
     * True if the PagedList has detached the DataSource it was loading from, and will no longer
     * load new data.
     *
     * @return True if the data source is detached.
     */
    @SuppressWarnings("WeakerAccess")
    public boolean isDetached() {
        return mDetached.get();
    }

    /**
     * Detach the PagedList from its DataSource, and attempt to load no more data.
     * <p>
     * This is called automatically when a DataSource load returns <code>null</code>, which is a
     * signal to stop loading. The PagedList will continue to present existing data, but will not
     * load new items.
     */
    @SuppressWarnings("WeakerAccess")
    public void detach() {
        mDetached.set(true);
    }
}

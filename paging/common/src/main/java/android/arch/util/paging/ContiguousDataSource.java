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

import java.util.List;

/**
 * @param <Type> type loaded by the ContiguousDataSource.
 *
 * @hide
 * */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public abstract class ContiguousDataSource<Type> extends DataSource<Type> {
    int mCount = Integer.MIN_VALUE;

    /**
     * Number of items that this DataSource can provide in total, or COUNT_UNDEFINED.
     *
     * @return number of items that this DataSource can provide in total, or COUNT_UNDEFINED
     * if difficult or undesired to compute.
     */
    public int loadCount() {
        return COUNT_UNDEFINED;
    }

    /**
     * Load initial data, starting after the passed position.
     *
     * @param position Index just before the data to be loaded.
     * @param initialLoadSize Suggested number of items to load.
     * @return List of initial items, representing data starting at position. Null if the
     *         DataSource is no longer valid, and should not be queried again.
     */
    public abstract List<Type> loadAfterInitial(int position, int initialLoadSize);

    /**
     * Load data after the given position / item.
     * <p>
     * It's valid to return a different list size than the page size, if it's easier for this data
     * source. It is generally safer to increase number loaded than reduce.
     *
     * @param currentEndIndex Load items after this index, starting with currentEndIndex + 1.
     * @param currentEndItem  Load items after this item, can be used for precise querying based on
     *                        item contents.
     * @param pageSize        Suggested number of items to load.
     * @return List of items, starting at position currentEndIndex + 1. Null if the data source is
     * no longer valid, and should not be queried again.
     */
    @WorkerThread
    @Nullable
    public abstract List<Type> loadAfter(int currentEndIndex,
            @NonNull Type currentEndItem, int pageSize);

    /**
     * Load data before the given position / item.
     * <p>
     * It's valid to return a different list size than the page size, if it's easier for this data
     * source. It is generally safer to increase number loaded than reduce.
     *
     * @param currentBeginIndex Load items before this index, starting with currentBeginIndex - 1.
     * @param currentBeginItem  Load items after this item, can be used for precise querying based
     *                          on item contents.
     * @param pageSize          Suggested number of items to load.
     * @return List of items, in descending order, starting at position currentBeginIndex - 1.
     */
    @WorkerThread
    @Nullable
    public abstract List<Type> loadBefore(int currentBeginIndex,
            @NonNull Type currentBeginItem, int pageSize);

    @WorkerThread
    @Nullable
    NullPaddedList<Type> loadAfterInitialInternal(int position, int initialLoadSize) {
        mCount = loadCount();
        if (mCount == COUNT_UNDEFINED) {
            return new NullPaddedList<>(position,
                    loadAfterInitial(position, initialLoadSize));
        } else {
            return new NullPaddedList<>(position,
                    mCount,
                    loadAfterInitial(position, initialLoadSize));
        }
    }
}

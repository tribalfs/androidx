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

package android.arch.paging.integration.testapp.pagedlist;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.paging.integration.testapp.Item;
import android.arch.paging.integration.testapp.ItemDataSource;
import android.arch.util.paging.DataSource;
import android.arch.util.paging.ListConfig;
import android.arch.util.paging.LivePagedListProvider;
import android.arch.util.paging.PagedList;

/**
 * Sample ViewModel backed by an artificial data source
 */
public class PagedListItemViewModel extends ViewModel {
    private final LiveData<PagedList<Item>> mLivePagedList;
    private ItemDataSource mDataSource;

    public PagedListItemViewModel() {
        mLivePagedList = new LivePagedListProvider<Integer, Item>() {
            @Override
            protected DataSource<Integer, Item> createDataSource() {
                mDataSource = new ItemDataSource();
                return mDataSource;
            }
        }.create(ListConfig.builder().pageSize(20).prefetchDistance(40).create());
    }

    void invalidateList() {
        if (mDataSource != null) {
            mDataSource.invalidate();
        }
    }

    LiveData<PagedList<Item>> getPagedList() {
        return mLivePagedList;
    }
}

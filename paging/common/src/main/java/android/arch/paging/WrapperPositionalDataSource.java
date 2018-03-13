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

package android.arch.paging;

import android.arch.core.util.Function;
import android.support.annotation.NonNull;

import java.util.List;

class WrapperPositionalDataSource<A, B> extends PositionalDataSource<B> {
    private final PositionalDataSource<A> mSource;
    private final Function<List<A>, List<B>> mListFunction;

    private final InvalidatedCallback mInvalidatedCallback = new DataSource.InvalidatedCallback() {
        @Override
        public void onInvalidated() {
            invalidate();
            removeCallback();
        }
    };

    WrapperPositionalDataSource(PositionalDataSource<A> source,
            Function<List<A>, List<B>> listFunction) {
        mSource = source;
        mListFunction = listFunction;
        mSource.addInvalidatedCallback(mInvalidatedCallback);
    }

    private void removeCallback() {
        mSource.removeInvalidatedCallback(mInvalidatedCallback);
    }

    @Override
    public void loadInitial(@NonNull LoadInitialParams params,
            final @NonNull LoadInitialCallback<B> callback) {
        mSource.loadInitial(params, new LoadInitialCallback<A>() {
            @Override
            public void onResult(@NonNull List<A> data, int position, int totalCount) {
                callback.onResult(convert(mListFunction, data), position, totalCount);
            }

            @Override
            public void onResult(@NonNull List<A> data, int position) {
                callback.onResult(convert(mListFunction, data), position);
            }
        });
    }

    @Override
    public void loadRange(@NonNull LoadRangeParams params,
            final @NonNull LoadRangeCallback<B> callback) {
        mSource.loadRange(params, new LoadRangeCallback<A>() {
            @Override
            public void onResult(@NonNull List<A> data) {
                callback.onResult(convert(mListFunction, data));
            }
        });
    }
}

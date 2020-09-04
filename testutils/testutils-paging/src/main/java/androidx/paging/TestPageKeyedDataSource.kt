/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.paging

import java.util.ArrayList

@Suppress("DEPRECATION")
class TestPageKeyedDataSource<T : Any>(list: List<T>) : PageKeyedDataSource<Int, T>() {
    private val list: List<T> = ArrayList(list)

    override fun loadInitial(
        params: LoadInitialParams<Int>,
        callback: LoadInitialCallback<Int, T>
    ) {
        val totalCount = list.size
        val sublist = list.subList(0, minOf(params.requestedLoadSize, totalCount))
        callback.onResult(sublist, null, params.requestedLoadSize)
    }

    override fun loadBefore(params: LoadParams<Int>, callback: LoadCallback<Int, T>) {
        callback.onResult(
            list.subList(params.key - params.requestedLoadSize + 1, params.key + 1),
            params.key - 1
        )
    }

    override fun loadAfter(params: LoadParams<Int>, callback: LoadCallback<Int, T>) {
        callback.onResult(
            list.subList(params.key, params.key + params.requestedLoadSize),
            params.key + params.requestedLoadSize
        )
    }
}

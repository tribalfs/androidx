/*
 * Copyright 2019 The Android Open Source Project
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

import androidx.testutils.DirectDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking

private class FakeSource<Value : Any>(
    private val leadingNulls: Int,
    private val trailingNulls: Int,
    private val data: List<Value>
) : PagedSource<Any, Value>() {
    override suspend fun load(params: LoadParams<Any>): LoadResult<Any, Value> {
        if (params.loadType == LoadType.REFRESH) {
            return LoadResult(
                data = data,
                itemsBefore = leadingNulls,
                itemsAfter = trailingNulls)
        }
        throw IllegalArgumentException("This test source only supports initial load")
    }
}

@Suppress("TestFunctionName")
fun StringPagedList(
    leadingNulls: Int,
    trailingNulls: Int,
    vararg items: String
): PagedList<String> = runBlocking {
    PagedList.create(
        pagedSource = FakeSource(leadingNulls, trailingNulls, items.toList()),
        coroutineScope = GlobalScope,
        notifyDispatcher = DirectDispatcher,
        fetchDispatcher = DirectDispatcher,
        initialFetchDispatcher = DirectDispatcher,
        boundaryCallback = null,
        config = Config(1, prefetchDistance = 0),
        key = null
    )
}
/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.paging.testing

import androidx.paging.DifferCallback
import androidx.paging.ItemSnapshotList
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.NullPaddedList
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.PagingDataDiffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs the [SnapshotLoader] load operations that are passed in and returns a List of data
 * that would be presented to the UI after all load operations are complete.
 *
 * @param coroutineScope The [CoroutineScope] to collect from this Flow<PagingData> and contains
 * the [CoroutineScope.coroutineContext] to load data from.
 *
 * @param loadOperations The block containing [SnapshotLoader] load operations.
 */
public suspend fun <Value : Any> Flow<PagingData<Value>>.asSnapshot(
    coroutineScope: CoroutineScope,
    loadOperations: suspend SnapshotLoader<Value>.() -> @JvmSuppressWildcards Unit
): @JvmSuppressWildcards List<Value> {

    lateinit var loader: SnapshotLoader<Value>

    val callback = object : DifferCallback {
        override fun onChanged(position: Int, count: Int) {
            loader.onDataSetChanged(loader.generations.value)
        }
        override fun onInserted(position: Int, count: Int) {
            loader.onDataSetChanged(loader.generations.value)
        }
        override fun onRemoved(position: Int, count: Int) {
            loader.onDataSetChanged(loader.generations.value)
        }
    }

    // PagingDataDiffer automatically switches to Dispatchers.Main to call presentNewList
    val differ = object : PagingDataDiffer<Value>(callback) {
        override suspend fun presentNewList(
            previousList: NullPaddedList<Value>,
            newList: NullPaddedList<Value>,
            lastAccessedIndex: Int,
            onListPresentable: () -> Unit
        ): Int? {
            onListPresentable()
            /**
             * On new generation, SnapshotLoader needs the latest [ItemSnapshotList]
             * index last updated by the initial refresh so that it can
             * prepend/append from then on based on that index.
             *
             * This last updated index is necessary because initial load
             * key may not be 0, for example when [Pager].initialKey != 0
             *
             * Any subsequent SnapshotLoader loads are based on the index tracked by
             * [SnapshotLoader] internally.
             */
            val lastLoadedIndex = snapshot().placeholdersBefore + snapshot().items.size - 1
            loader.generations.value.lastAccessedIndex.set(lastLoadedIndex)
            return null
        }
    }

    loader = SnapshotLoader(differ)

    /**
     * Launches collection on this [Pager.flow].
     *
     * The collection job is cancelled automatically after [loadOperations] completes.
      */
    val job = coroutineScope.launch {
        this@asSnapshot.collectLatest {
            incrementGeneration(loader)
            differ.collectFrom(it)
        }
    }

    /**
     * Runs the input [loadOperations].
     *
     * Awaits for initial refresh to complete before invoking [loadOperations]. Automatically
     * cancels the collection on this [Pager.flow] after [loadOperations] completes and Paging
     * is idle.
     *
     * Returns a List of loaded data.
     */
    return withContext(coroutineScope.coroutineContext) {
        differ.awaitNotLoading()
        loader.loadOperations()
        differ.awaitNotLoading()

        job.cancelAndJoin()

        differ.snapshot().items
    }
}

/**
 * Awaits until both source and mediator states are NotLoading. We do not care about the state of
 * endOfPaginationReached. Source and mediator states need to be checked individually because
 * the aggregated LoadStates can reflect `NotLoading` when source states are `Loading`.
 */
internal suspend fun <Value : Any> PagingDataDiffer<Value>.awaitNotLoading() {
    loadStateFlow.filter {
        it.source.isIdle() && it.mediator?.isIdle() ?: true
    }.firstOrNull()
}

private fun LoadStates.isIdle(): Boolean {
    return refresh is LoadState.NotLoading && append is LoadState.NotLoading &&
        prepend is LoadState.NotLoading
}

private fun <Value : Any> incrementGeneration(loader: SnapshotLoader<Value>) {
    val currGen = loader.generations.value
    if (currGen.id == loader.generations.value.id) {
        loader.generations.value = Generation(
            id = currGen.id + 1
        )
    }
}
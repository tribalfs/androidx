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

import androidx.paging.PagedList.LoadState
import androidx.paging.PagedList.LoadType
import androidx.paging.PagedSource.Companion.COUNT_UNDEFINED
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

internal class Pager<K : Any, V : Any>(
    private val pagedListScope: CoroutineScope,
    val config: PagedList.Config,
    val source: PagedSource<K, V>,
    val notifyExecutor: Executor,
    private val fetchExecutor: Executor,
    val pageConsumer: PageConsumer<V>,
    result: PagedSource.LoadResult<K, V>,
    private val adjacentProvider: AdjacentProvider<V> = SimpleAdjacentProvider()
) {
    private val totalCount: Int
    private var prevKey: K? = null
    private var nextKey: K? = null
    private val detached = AtomicBoolean(false)

    var loadStateManager = object : PagedList.LoadStateManager() {
        override fun onStateChanged(type: LoadType, state: LoadState, error: Throwable?) =
            pageConsumer.onStateChanged(type, state, error)
    }

    val isDetached
        get() = detached.get()

    init {
        prevKey = result.prevKey
        nextKey = result.nextKey
        this.adjacentProvider.onPageResultResolution(LoadType.REFRESH, result)
        totalCount = when (result.counted) {
            // only one of leadingNulls / offset may be used
            true -> result.itemsBefore + result.offset + result.data.size + result.itemsAfter
            else -> COUNT_UNDEFINED
        }
    }

    private fun listenTo(type: LoadType, future: suspend () -> PagedSource.LoadResult<K, V>) {
        // Listen on the BG thread if the paged source is invalid, since it can be expensive.
        pagedListScope.launch(fetchExecutor.asCoroutineDispatcher()) {
            try {
                val value = future()

                // if invalid, drop result on the floor
                if (source.invalid) {
                    detach()
                    return@launch
                }

                // Source has been verified to be valid after producing data, so sent data to UI
                launch(notifyExecutor.asCoroutineDispatcher()) {
                    onLoadSuccess(type, value)
                }
            } catch (throwable: Throwable) {
                onLoadError(type, throwable)
            }
        }
    }

    internal interface PageConsumer<V : Any> {
        /**
         * @return `true` if we need to fetch more
         */
        fun onPageResult(type: LoadType, pageResult: PagedSource.LoadResult<*, V>): Boolean

        fun onStateChanged(type: LoadType, state: LoadState, error: Throwable?)
    }

    internal interface AdjacentProvider<V : Any> {
        val firstLoadedItem: V?
        val lastLoadedItem: V?
        val firstLoadedItemIndex: Int
        val lastLoadedItemIndex: Int

        /**
         * Notify the [AdjacentProvider] of new loaded data, to update first/last item/index.
         *
         * NOTE: this data may not be committed (e.g. it may be dropped due to max size). Up to the
         * implementation of the AdjacentProvider to handle this (generally by ignoring this call if
         * dropping is supported).
         */
        fun onPageResultResolution(type: LoadType, result: PagedSource.LoadResult<*, V>)
    }

    private fun onLoadSuccess(type: LoadType, value: PagedSource.LoadResult<K, V>) {
        if (isDetached) return // abort!

        adjacentProvider.onPageResultResolution(type, value)

        if (pageConsumer.onPageResult(type, value)) {
            when (type) {
                LoadType.START -> {
                    prevKey = value.prevKey
                    schedulePrepend()
                }
                LoadType.END -> {
                    nextKey = value.nextKey
                    scheduleAppend()
                }
                else -> throw IllegalStateException("Can only fetch more during append/prepend")
            }
        } else {
            val state = if (value.data.isEmpty()) LoadState.DONE else LoadState.IDLE
            loadStateManager.setState(type, state, null)
        }
    }

    private fun onLoadError(type: LoadType, throwable: Throwable) {
        if (isDetached) return // abort!

        // TODO: handle nesting
        val state = when {
            source.isRetryableError(throwable) -> LoadState.RETRYABLE_ERROR
            else -> LoadState.ERROR
        }
        loadStateManager.setState(type, state, throwable)
    }

    fun trySchedulePrepend() {
        if (loadStateManager.start == LoadState.IDLE) schedulePrepend()
    }

    fun tryScheduleAppend() {
        if (loadStateManager.end == LoadState.IDLE) scheduleAppend()
    }

    private fun canPrepend() = when (totalCount) {
        // don't know count / position from initial load, so be conservative, return true
        COUNT_UNDEFINED -> true
        // position is known, do we have space left?
        else -> adjacentProvider.firstLoadedItemIndex > 0
    }

    private fun canAppend() = when (totalCount) {
        // don't know count / position from initial load, so be conservative, return true
        COUNT_UNDEFINED -> true
        // count is known, do we have space left?
        else -> adjacentProvider.lastLoadedItemIndex < totalCount - 1
    }

    private fun schedulePrepend() {
        if (!canPrepend()) {
            onLoadSuccess(LoadType.START, PagedSource.LoadResult.empty())
            return
        }

        val key = when (val keyProvider = source.keyProvider) {
            is PagedSource.KeyProvider.Positional -> {
                @Suppress("UNCHECKED_CAST")
                (adjacentProvider.firstLoadedItemIndex - 1) as K
            }
            is PagedSource.KeyProvider.PageKey -> prevKey
            is PagedSource.KeyProvider.ItemKey -> keyProvider.getKey(
                adjacentProvider.firstLoadedItem!!
            )
        }

        loadStateManager.setState(LoadType.START, LoadState.LOADING, null)
        listenTo(LoadType.START) {
            source.load(
                PagedSource.LoadParams(
                    PagedSource.LoadType.START,
                    key,
                    config.initialLoadSizeHint,
                    config.enablePlaceholders,
                    config.pageSize
                )
            )
        }
    }

    private fun scheduleAppend() {
        if (!canAppend()) {
            onLoadSuccess(LoadType.END, PagedSource.LoadResult.empty())
            return
        }

        val key = when (val keyProvider = source.keyProvider) {
            is PagedSource.KeyProvider.Positional ->
                @Suppress("UNCHECKED_CAST")
                (adjacentProvider.lastLoadedItemIndex + 1) as K
            is PagedSource.KeyProvider.PageKey -> nextKey
            is PagedSource.KeyProvider.ItemKey -> keyProvider.getKey(
                adjacentProvider.lastLoadedItem!!
            )
        }

        loadStateManager.setState(LoadType.END, LoadState.LOADING, null)
        listenTo(LoadType.END) {
            source.load(
                PagedSource.LoadParams(
                    PagedSource.LoadType.END,
                    key,
                    config.initialLoadSizeHint,
                    config.enablePlaceholders,
                    config.pageSize
                )
            )
        }
    }

    fun retry() {
        if (loadStateManager.start == LoadState.RETRYABLE_ERROR) schedulePrepend()
        if (loadStateManager.end == LoadState.RETRYABLE_ERROR) scheduleAppend()
    }

    fun detach() = detached.set(true)

    internal class SimpleAdjacentProvider<V : Any> : AdjacentProvider<V> {
        override var firstLoadedItemIndex: Int = 0
            private set
        override var lastLoadedItemIndex: Int = 0
            private set
        override var firstLoadedItem: V? = null
            private set
        override var lastLoadedItem: V? = null
            private set

        private var counted: Boolean = false
        private var leadingUnloadedCount: Int = 0
        private var trailingUnloadedCount: Int = 0

        override fun onPageResultResolution(type: LoadType, result: PagedSource.LoadResult<*, V>) {
            if (result.data.isEmpty()) return

            if (type == LoadType.START) {
                firstLoadedItemIndex -= result.data.size
                firstLoadedItem = result.data[0]
                if (counted) {
                    leadingUnloadedCount -= result.data.size
                }
            } else if (type == LoadType.END) {
                lastLoadedItemIndex += result.data.size
                lastLoadedItem = result.data.last()
                if (counted) {
                    trailingUnloadedCount -= result.data.size
                }
            } else {
                firstLoadedItemIndex = result.itemsBefore + result.offset
                lastLoadedItemIndex = firstLoadedItemIndex + result.data.size - 1
                firstLoadedItem = result.data[0]
                lastLoadedItem = result.data.last()

                if (result.counted) {
                    counted = true
                    leadingUnloadedCount = result.itemsBefore
                    trailingUnloadedCount = result.itemsAfter
                }
            }
        }
    }
}

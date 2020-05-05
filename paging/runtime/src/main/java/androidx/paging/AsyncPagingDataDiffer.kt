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

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
import androidx.paging.LoadType.APPEND
import androidx.paging.LoadType.PREPEND
import androidx.paging.LoadType.REFRESH
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

open class AsyncPagingDataDiffer<T : Any>(
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val diffCallback: DiffUtil.ItemCallback<T>,
    private val updateCallback: ListUpdateCallback
) {
    internal val callback = object : PresenterCallback {
        override fun onInserted(position: Int, count: Int) {
            updateCallback.onInserted(position, count)
        }

        override fun onRemoved(position: Int, count: Int) =
            updateCallback.onRemoved(position, count)

        override fun onChanged(position: Int, count: Int) {
            // NOTE: pass a null payload to convey null -> item, or item -> null
            updateCallback.onChanged(position, count, null)
        }

        override fun onStateUpdate(loadType: LoadType, loadState: LoadState) {
            when (loadType) {
                REFRESH -> {
                    if (loadState != loadStates[REFRESH]) {
                        loadStates[REFRESH] = loadState
                        dispatchLoadState(REFRESH, loadState)
                    }
                }
                PREPEND -> {
                    if (loadState != loadStates[PREPEND]) {
                        loadStates[PREPEND] = loadState
                        dispatchLoadState(PREPEND, loadState)
                    }
                }
                APPEND -> {
                    if (loadState != loadStates[APPEND]) {
                        loadStates[APPEND] = loadState
                        dispatchLoadState(APPEND, loadState)
                    }
                }
            }
        }

        private fun dispatchLoadState(type: LoadType, state: LoadState) {
            loadStateListeners.forEach { it(type, state) }
        }
    }

    /** True if we're currently executing [getItem] */
    internal var inGetItem: Boolean = false

    private val differBase = object : PagingDataDiffer<T>(mainDispatcher) {
        override suspend fun performDiff(
            previousList: NullPaddedList<T>,
            newList: NullPaddedList<T>,
            newLoadStates: Map<LoadType, LoadState>,
            lastAccessedIndex: Int
        ): Int? {
            return withContext(mainDispatcher) {
                when {
                    previousList.size == 0 -> {
                        // fast path for no items -> some items
                        callback.onInserted(0, newList.size)
                        newLoadStates.entries.forEach { callback.onStateUpdate(it.key, it.value) }
                        return@withContext null
                    }
                    newList.size == 0 -> {
                        // fast path for some items -> no items
                        callback.onRemoved(0, previousList.size)
                        newLoadStates.entries.forEach { callback.onStateUpdate(it.key, it.value) }
                        return@withContext null
                    }
                    else -> { // full diff
                        val diffResult = withContext(workerDispatcher) {
                            previousList.computeDiff(newList, diffCallback)
                        }

                        previousList.dispatchDiff(updateCallback, newList, diffResult)
                        newLoadStates.entries.forEach { callback.onStateUpdate(it.key, it.value) }

                        return@withContext previousList.transformAnchorIndex(
                            diffResult = diffResult,
                            newList = newList,
                            oldPosition = lastAccessedIndex
                        )
                    }
                }
            }
        }

        /**
         * Return if [getItem] is running to post any data modifications.
         *
         * This must be done because RecyclerView can't be modified during an onBind, when
         * [getItem] is generally called.
         */
        override fun postEvents(): Boolean {
            return inGetItem
        }
    }

    private val job = AtomicReference<Job?>(null)

    suspend fun presentData(pagingData: PagingData<T>) {
        try {
            job.get()?.cancelAndJoin()
        } finally {
            differBase.collectFrom(pagingData, callback)
        }
    }

    fun submitData(lifecycle: Lifecycle, pagingData: PagingData<T>) {
        var oldJob: Job?
        var newJob: Job
        do {
            oldJob = job.get()
            newJob = lifecycle.coroutineScope.launch(start = CoroutineStart.LAZY) {
                oldJob?.cancelAndJoin()
                differBase.collectFrom(pagingData, callback)
            }
        } while (!job.compareAndSet(oldJob, newJob))
        newJob.start()
    }

    /**
     * Retry any failed load requests that would result in a [LoadState.Error] update to this
     * [AsyncPagingDataDiffer].
     *
     * [LoadState.Error] can be generated from two types of load requests:
     *  * [PagingSource.load] returning [PagingSource.LoadResult.Error]
     *  * [RemoteMediator.load] returning [RemoteMediator.MediatorResult.Error]
     */
    fun retry() {
        differBase.retry()
    }

    fun refresh() {
        differBase.refresh()
    }

    /**
     * Get the item from the current PagedList at the specified index.
     *
     * Note that this operates on both loaded items and null padding within the PagedList.
     *
     * @param index Index of item to get, must be >= 0, and < [itemCount]
     * @return The item, or null, if a null placeholder is at the specified position.
     */
    open fun getItem(index: Int): T? {
        try {
            inGetItem = true
            return differBase[index]
        } finally {
            inGetItem = false
        }
    }

    /**
     * Get the number of items currently presented by this Differ. This value can be directly
     * returned to [androidx.recyclerview.widget.RecyclerView.Adapter.getItemCount].
     *
     * @return Number of items being presented.
     */
    open val itemCount: Int
        get() = differBase.size

    internal val loadStateListeners: MutableList<(LoadType, LoadState) -> Unit> =
        CopyOnWriteArrayList()

    internal val loadStates = mutableMapOf<LoadType, LoadState>(
        REFRESH to LoadState.NotLoading(endOfPaginationReached = false, fromMediator = false),
        PREPEND to LoadState.NotLoading(endOfPaginationReached = false, fromMediator = false),
        APPEND to LoadState.NotLoading(endOfPaginationReached = false, fromMediator = false)
    )

    /**
     * Add a listener to observe the loading state.
     *
     * As new [PagingData] generations are submitted and displayed, the listener will be notified to
     * reflect current [LoadType.REFRESH], [LoadType.PREPEND], and [LoadType.APPEND] states.
     *
     * @param listener to receive [LoadState] updates.
     *
     * @see removeLoadStateListener
     */
    open fun addLoadStateListener(listener: (LoadType, LoadState) -> Unit) {
        // Note: Important to add the listener first before sending off events, in case the
        // callback triggers removal, which could lead to a leak if the listener is added
        // afterwards.
        loadStateListeners.add(listener)
        listener(REFRESH, loadStates[REFRESH]!!)
        if (loadStateListeners.contains(listener)) listener(PREPEND, loadStates[PREPEND]!!)
        if (loadStateListeners.contains(listener)) listener(APPEND, loadStates[APPEND]!!)
    }

    /**
     * Remove a previously registered load state listener.
     *
     * @param listener Previously registered listener.
     * @see addLoadStateListener
     */
    open fun removeLoadStateListener(listener: (LoadType, LoadState) -> Unit) {
        loadStateListeners.remove(listener)
    }
}
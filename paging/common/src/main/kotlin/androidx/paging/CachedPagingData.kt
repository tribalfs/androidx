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

import androidx.annotation.VisibleForTesting
import androidx.paging.ActiveFlowTracker.FlowType.PAGED_DATA_FLOW
import androidx.paging.ActiveFlowTracker.FlowType.PAGE_EVENT_FLOW
import androidx.paging.multicast.Multicaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.scan

@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
private class MulticastedPagingData<T : Any>(
    val scope: CoroutineScope,
    val parent: PagingData<T>,
    // used in tests
    val tracker: ActiveFlowTracker? = null
) {
    private val accumulated = CachedPageEventFlow(
        src = parent.flow.onStart {
            tracker?.onStart(PAGE_EVENT_FLOW)
        }.onCompletion {
            tracker?.onComplete(PAGE_EVENT_FLOW)
        },
        scope = scope
    )

    fun asPagingData() = PagingData(
        flow = accumulated.downstreamFlow,
        receiver = parent.receiver
    )

    @FlowPreview
    suspend fun close() = accumulated.close()
}

/**
 * Caches the [PagingData] such that any downstream collection from this flow will share the same
 * paging data.
 *
 * The flow is kept active as long as the given [scope] is active. To avoid leaks, make sure to
 * use a [scope] that is already managed (like a ViewModel scope) or manually cancel it when you
 * don't need paging anymore.
 *
 * A common use case for this caching is to cache [PagingData] in a ViewModel. This can ensure that,
 * upon configuration change (e.g. rotation), then new Activity will receive the existing data
 * immediately rather than fetching it from scratch.
 *
 * Note that this does not turn the `Flow<PagingData>` into a hot stream. It won't execute any
 * unnecessary code unless it is being collected.
 *
 * ```
 * class MyViewModel : ViewModel() {
 *     val pagingData : Flow<PagingData<Item>> = PagingDataFlowBuilder(
 *         pagingSourceFactory = <factory>,
 *         config = <config>)
 *     ).build()
 *     .cached(viewModelScope)
 * }
 *
 * class MyActivity : Activity() {
 *     override fun onCreate() {
 *         val pages = myViewModel.pagingData
 *     }
 * }
 * ```
 *
 * @param scope The coroutine scope where this page cache will be kept alive.
 */
fun <T : Any> Flow<PagingData<T>>.cachedIn(
    scope: CoroutineScope
) = cachedIn(scope, null)

@UseExperimental(ExperimentalCoroutinesApi::class, FlowPreview::class)
internal fun <T : Any> Flow<PagingData<T>>.cachedIn(
    scope: CoroutineScope,
    // used in tests
    tracker: ActiveFlowTracker? = null
): Flow<PagingData<T>> {
    val multicastedFlow = this.map {
        MulticastedPagingData(
            scope = scope,
            parent = it
        )
    }.scan(null as MulticastedPagingData<T>?) { prev, next ->
        prev?.close()
        next
    }.mapNotNull {
        it?.asPagingData()
    }.onStart {
        tracker?.onStart(PAGED_DATA_FLOW)
    }.onCompletion {
        tracker?.onComplete(PAGED_DATA_FLOW)
    }
    return Multicaster(
        scope = scope,
        bufferSize = 1,
        source = multicastedFlow,
        onEach = {},
        keepUpstreamAlive = true
    ).flow
}

/**
 * This is only used for testing to ensure we don't leak resources
 */
@VisibleForTesting
internal interface ActiveFlowTracker {
    suspend fun onStart(flowType: FlowType)
    suspend fun onComplete(flowType: FlowType)

    enum class FlowType {
        PAGED_DATA_FLOW,
        PAGE_EVENT_FLOW
    }
}
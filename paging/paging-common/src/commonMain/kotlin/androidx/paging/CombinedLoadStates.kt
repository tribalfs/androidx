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

import androidx.paging.LoadState.NotLoading
import kotlin.jvm.JvmName
import kotlin.jvm.JvmSuppressWildcards
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.firstOrNull

/**
 * Collection of pagination [LoadState]s for both a [PagingSource], and [RemoteMediator].
 *
 * Note: The [LoadType] [REFRESH][LoadType.REFRESH] always has [LoadState.endOfPaginationReached]
 * set to `false`.
 */
public class CombinedLoadStates(
    /**
     * Convenience for combined behavior of [REFRESH][LoadType.REFRESH] [LoadState], which generally
     * defers to [mediator] if it exists, but if previously was [LoadState.Loading], awaits for both
     * [source] and [mediator] to become [LoadState.NotLoading] to ensure the remote load was
     * applied.
     *
     * For use cases that require reacting to [LoadState] of [source] and [mediator] specifically,
     * e.g., showing cached data when network loads via [mediator] fail, [LoadStates] exposed via
     * [source] and [mediator] should be used directly.
     */
    public val refresh: LoadState,
    /**
     * Convenience for combined behavior of [PREPEND][LoadType.REFRESH] [LoadState], which generally
     * defers to [mediator] if it exists, but if previously was [LoadState.Loading], awaits for both
     * [source] and [mediator] to become [LoadState.NotLoading] to ensure the remote load was
     * applied.
     *
     * For use cases that require reacting to [LoadState] of [source] and [mediator] specifically,
     * e.g., showing cached data when network loads via [mediator] fail, [LoadStates] exposed via
     * [source] and [mediator] should be used directly.
     */
    public val prepend: LoadState,
    /**
     * Convenience for combined behavior of [APPEND][LoadType.REFRESH] [LoadState], which generally
     * defers to [mediator] if it exists, but if previously was [LoadState.Loading], awaits for both
     * [source] and [mediator] to become [LoadState.NotLoading] to ensure the remote load was
     * applied.
     *
     * For use cases that require reacting to [LoadState] of [source] and [mediator] specifically,
     * e.g., showing cached data when network loads via [mediator] fail, [LoadStates] exposed via
     * [source] and [mediator] should be used directly.
     */
    public val append: LoadState,
    /** [LoadStates] corresponding to loads from a [PagingSource]. */
    public val source: LoadStates,

    /**
     * [LoadStates] corresponding to loads from a [RemoteMediator], or `null` if [RemoteMediator]
     * not present.
     */
    public val mediator: LoadStates? = null,
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as CombinedLoadStates

        if (refresh != other.refresh) return false
        if (prepend != other.prepend) return false
        if (append != other.append) return false
        if (source != other.source) return false
        if (mediator != other.mediator) return false

        return true
    }

    override fun hashCode(): Int {
        var result = refresh.hashCode()
        result = 31 * result + prepend.hashCode()
        result = 31 * result + append.hashCode()
        result = 31 * result + source.hashCode()
        result = 31 * result + (mediator?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "CombinedLoadStates(refresh=$refresh, prepend=$prepend, append=$append, " +
            "source=$source, mediator=$mediator)"
    }

    internal fun forEach(op: (LoadType, Boolean, LoadState) -> Unit) {
        source.forEach { type, state -> op(type, false, state) }
        mediator?.forEach { type, state -> op(type, true, state) }
    }

    /** Returns true when [source] and [mediator] is in [NotLoading] for all [LoadType] */
    public val isIdle = source.isIdle && mediator?.isIdle ?: true

    /**
     * Returns true if either [source] or [mediator] has a [LoadType] that is in [LoadState.Error]
     */
    @get:JvmName("hasError") public val hasError = source.hasError || mediator?.hasError ?: false
}

/**
 * Function to wait on a Flow<CombinedLoadStates> until a load has completed.
 *
 * It collects on the Flow<CombinedLoadStates> and suspends until it collects and returns the
 * firstOrNull [CombinedLoadStates] where all [LoadStates] have settled into a non-loading state
 * i.e. [LoadState.NotLoading] or [LoadState.Error].
 *
 * A use case could be scrolling to a position after refresh has completed:
 * ```
 * override fun onCreate(savedInstanceState: Bundle?) {
 *     ...
 *     refreshButton.setOnClickListener {
 *         pagingAdapter.refresh()
 *         lifecycleScope.launch {
 *             // wait for refresh to complete
 *             pagingAdapter.loadStateFlow.awaitNotLoading()
 *             // do work after refresh
 *             recyclerView.scrollToPosition(position)
 *         }
 *    }
 * }
 * ```
 */
@OptIn(FlowPreview::class)
public suspend fun Flow<CombinedLoadStates>.awaitNotLoading():
    @JvmSuppressWildcards CombinedLoadStates? {

    return debounce(1).filter { it.isIdle || it.hasError }.firstOrNull()
}

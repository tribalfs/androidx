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

/**
 * LoadState of a PagedList load - associated with a [LoadType]
 *
 * You can use a [LoadStateListener] to observe [LoadState] of any [LoadType]. For UI
 * purposes (swipe refresh, loading spinner, retry button), this is typically done by
 * registering a callback with the `PagedListAdapter` or `AsyncPagedListDiffer`.
 *
 * @see LoadType
 */
sealed class LoadState {
    /**
     * Indicates the PagedList is not currently loading, and no error currently observed.
     */
    object Idle : LoadState()

    /**
     * Loading is in progress.
     */
    object Loading : LoadState()

    /**
     * Loading is complete.
     */
    object Done : LoadState()

    /**
     * Loading hit an error.
     *
     * @param error [Throwable] that caused the load operation to generate this error state.
     *
     * @see androidx.paging.PagedList.retry
     */
    data class Error(val error: Throwable) : LoadState()
}

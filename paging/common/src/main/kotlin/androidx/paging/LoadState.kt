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
 * [LoadState] of any [LoadType] may be observed for UI purposes by registering a listener via
 * [androidx.paging.PagingDataAdapter.addLoadStateListener] or
 * [androidx.paging.AsyncPagingDataDiffer.addLoadStateListener]
 *
 * @param endOfPaginationReached `false` if there is more data to load in the [LoadType] this
 * [LoadState] is associated with, `true` otherwise. This parameter informs [Pager] if it
 * should continue to make requests for additional data in this direction or if it should
 * halt as the end of the dataset has been reached.
 *
 * @param fromMediator `true` if this [LoadState] was generated from a request to [RemoteMediator].
 * Otherwise `false`, when indicating the state of requests to [PagingSource.load].
 *
 * @see LoadType
 */
sealed class LoadState(
    val endOfPaginationReached: Boolean,
    @get:JvmName("isFromMediator")
    val fromMediator: Boolean
) {
    /**
     * Indicates the [PagingData] is not currently loading, and no error currently observed.
     *
     * @param endOfPaginationReached `false` if there is more data to load in the [LoadType] this
     * [LoadState] is associated with, `true` otherwise. This parameter informs [Pager] if it
     * should continue to make requests for additional data in this direction or if it should
     * halt as the end of the dataset has been reached.
     *
     * @param fromMediator `true` if this [LoadState] was generated from a request to
     * [RemoteMediator]. Otherwise `false`, when indicating the state of requests to
     * [PagingSource.load].
     */
    class NotLoading(
        endOfPaginationReached: Boolean,
        fromMediator: Boolean
    ) : LoadState(endOfPaginationReached, fromMediator) {
        override fun toString(): String {
            return "NotLoading(endOfPaginationReached=$endOfPaginationReached, " +
                    "isRemoteError=$fromMediator)"
        }

        override fun equals(other: Any?): Boolean {
            return other is NotLoading &&
                    endOfPaginationReached == other.endOfPaginationReached &&
                    fromMediator == other.fromMediator
        }

        override fun hashCode(): Int {
            return endOfPaginationReached.hashCode() + fromMediator.hashCode()
        }

        internal companion object {
            internal fun instance(
                endOfPaginationReached: Boolean,
                fromMediator: Boolean
            ): NotLoading = when {
                fromMediator -> when {
                    endOfPaginationReached -> DoneRemote
                    else -> IdleRemote
                }
                else -> when {
                    endOfPaginationReached -> Done
                    else -> Idle
                }
            }

            internal val Done = NotLoading(true, fromMediator = false)
            internal val Idle = NotLoading(false, fromMediator = false)

            @Suppress("MemberVisibilityCanBePrivate") // synthetic access
            internal val DoneRemote = NotLoading(true, fromMediator = true)

            @Suppress("MemberVisibilityCanBePrivate") // synthetic access
            internal val IdleRemote = NotLoading(false, fromMediator = true)
        }
    }

    /**
     * Loading is in progress.
     *
     * @param fromMediator `true` if this [LoadState] was generated from a request to
     * [RemoteMediator]. Otherwise `false`, when indicating the state of requests to
     * [PagingSource.load].
     */
    class Loading(fromMediator: Boolean) : LoadState(false, fromMediator) {
        override fun toString(): String {
            return "Loading(endOfPaginationReached=$endOfPaginationReached, " +
                    "isRemoteError=$fromMediator)"
        }

        override fun equals(other: Any?): Boolean {
            return other is Loading &&
                    endOfPaginationReached == other.endOfPaginationReached &&
                    fromMediator == other.fromMediator
        }

        override fun hashCode(): Int {
            return endOfPaginationReached.hashCode() + fromMediator.hashCode()
        }

        internal companion object {
            internal fun instance(fromMediator: Boolean): Loading {
                return if (fromMediator) Remote else Local
            }

            @Suppress("MemberVisibilityCanBePrivate") // synthetic access
            internal val Remote = Loading(fromMediator = true)

            @Suppress("MemberVisibilityCanBePrivate") // synthetic access
            internal val Local = Loading(fromMediator = false)
        }
    }

    /**
     * Loading hit an error.
     *
     * @param error [Throwable] that caused the load operation to generate this error state.
     *
     * @param fromMediator `true` if this [LoadState] was generated from a request to
     * [RemoteMediator]. Otherwise `false`, when indicating the state of requests to
     * [PagingSource.load].
     *
     * @see androidx.paging.PagedList.retry
     */
    class Error(
        val error: Throwable,
        fromMediator: Boolean
    ) : LoadState(false, fromMediator) {
        override fun equals(other: Any?): Boolean {
            return other is Error &&
                    endOfPaginationReached == other.endOfPaginationReached &&
                    fromMediator == other.fromMediator &&
                    error == other.error
        }

        override fun hashCode(): Int {
            return endOfPaginationReached.hashCode() + fromMediator.hashCode() + error.hashCode()
        }

        override fun toString(): String {
            return "Error(endOfPaginationReached=$endOfPaginationReached, " +
                    "isRemoteError=$fromMediator, " +
                    "error=$error)"
        }
    }
}

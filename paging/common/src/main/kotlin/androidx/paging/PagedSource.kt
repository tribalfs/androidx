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

import androidx.annotation.IntRange
import androidx.annotation.RestrictTo
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Factory for [PagedSource]s.
 *
 * Data-loading systems of an application or library can implement provide this type to allow
 * `LiveData<PagedList>`s to be created.
 *
 * @param Key Key identifying items in PagedSource.
 * @param Value Type of items in the list loaded by the PagedSources.
 */
typealias PagedSourceFactory<Key, Value> = () -> PagedSource<Key, Value>

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
fun <Key : Any> PagedList.Config.toRefreshLoadParams(key: Key?): PagedSource.LoadParams<Key> =
    PagedSource.LoadParams(
        LoadType.REFRESH,
        key,
        initialLoadSizeHint,
        enablePlaceholders,
        pageSize
    )

/**
 * Base class for an abstraction of pageable static data from some source, where loading pages data
 * is typically an expensive operation. Some examples of common [PagedSource]s might be from network
 * or from a database.
 *
 * This class was designed with the intent of being used as input into a [PagedList], which queries
 * snapshots of pages of data from a [PagedSource]. A [PagedList] can grow as it loads more data,
 * but the data loaded cannot be updated. If the underlying data set is modified, a new [PagedList]
 * must be created to represent the new data.
 *
 * <h4>Loading Pages</h4>
 *
 * [PagedList] queries data from its [PagedSource] in response to loading hints.
 * [androidx.paging.PagedListAdapter] calls [PagedList.loadAround] to load content as the user
 * scrolls in a `RecyclerView`.
 *
 * To control how and when a [PagedList] queries data from its [PagedSource], see
 * [PagedList.Config]. The [Config][PagedList.Config] object defines things like load sizes and
 * prefetch distance.
 *
 * <h4>Updating Paged Data</h4>
 *
 * A [PagedList] is a snapshot of the data set. A new [PagedList] must be created if an update
 * occurs, such as a reorder, insert, delete, or content update occurs. A [PagedSource] must detect
 * that it cannot continue loading its snapshot (for instance, when Database query notices a table
 * being invalidated), and call [invalidate]. Then a new [PagedList] would be created to represent
 * data from the new state of the database query.
 *
 * To page in data that doesn't update, you can create a single [PagedSource], and pass it to a
 * single [PagedList]. For example, loading from network when the network's paging API doesn't
 * provide updates.
 *
 * If you have granular update signals, such as a network API signaling an update to a single
 * item in the list, it's recommended to load data from network into memory. Then present that
 * data to the [PagedList] via a [PagedSource] that wraps an in-memory snapshot. Each time the
 * in-memory copy changes, invalidate the [PagedSource], and a new [PagedList] wrapping the new
 * state of the snapshot can be created.
 *
 * @param Key Type for unique identifier for items loaded from [PagedSource]. E.g., [Int] to
 * represent an item's position in a [PagedSource] that is keyed by item position. Note that this is
 * distinct from e.g. Room's `<Value> Value type loaded by the [PagedSource].
 * @param Value Type of data loaded in by this [PagedSource]. E.g., the type of data that will be
 * passed to a [PagedList] to be displayed in a `RecyclerView`
 */
@Suppress("KDocUnresolvedReference")
abstract class PagedSource<Key : Any, Value : Any> {

    /**
     * Params for generic load request on a [PagedSource].
     */
    data class LoadParams<Key : Any>(
        /**
         * [LoadType], for different behavior, e.g. only count initial load
         */
        val loadType: LoadType,
        /**
         * Key for the page to be loaded
         */
        val key: Key?,
        /**
         * Number of items to load
         */
        val loadSize: Int,
        /**
         * Whether placeholders are enabled - if false, can skip counting
         */
        val placeholdersEnabled: Boolean,
        val pageSize: Int
    )

    sealed class LoadResult<Key : Any, Value : Any> {
        data class Error<Key : Any, Value : Any>(
            val throwable: Throwable
        ) : LoadResult<Key, Value>()

        /**
         * Success result object for [PagedSource.load]
         */
        data class Page<Key : Any, Value : Any>(
            /**
             * Loaded data
             */
            val data: List<Value>,
            /**
             * Key for previous page.
             */
            val prevKey: Key?,
            /**
             * Key for next page.
             */
            val nextKey: Key?,
            /**
             * Optional count of items before the loaded data.
             */
            @IntRange(from = COUNT_UNDEFINED.toLong())
            val itemsBefore: Int = COUNT_UNDEFINED,
            /**
             * Optional count of items after the loaded data.
             */
            @IntRange(from = COUNT_UNDEFINED.toLong())
            val itemsAfter: Int = COUNT_UNDEFINED
        ) : LoadResult<Key, Value>() {
            init {
                if (itemsBefore != COUNT_UNDEFINED && itemsBefore < 0) {
                    throw IllegalArgumentException("itemsBefore cannot be negative")
                }

                if (itemsAfter != COUNT_UNDEFINED && itemsAfter < 0) {
                    throw IllegalArgumentException("itemsAfter cannot be negative")
                }
            }

            companion object {
                const val COUNT_UNDEFINED = Int.MIN_VALUE

                @Suppress("MemberVisibilityCanBePrivate") // Prevent synthetic accessor generation.
                private val EMPTY = Page(emptyList(), null, null, 0, 0)

                @Suppress("UNCHECKED_CAST") // Can safely ignore, since the list is empty.
                internal fun <Key : Any, Value : Any> empty() = EMPTY as Page<Key, Value>
            }
        }
    }

    /**
     * Request a refresh key from the Source, given a Page and internal position within the page.
     *
     * If the user's viewport is (approximately) centered around the `N`th item in a page, the
     * page and `N` will be passed as [indexInPage]. This key will be passed into the initial
     * load that occurs during refresh.
     *
     * For example, if items are loaded based on position, and keys are positions, the source
     * should return the position of the item.
     *
     * Alternately, if items contain a key used to load, get the key from the item in the page at
     * index [indexInPage].
     *
     * If this operation cannot be supported (generally, because keys cannot be reused across
     * refresh) return `null` - this is the default behavior.
     */
    open fun getRefreshKeyFromPage(indexInPage: Int, page: LoadResult.Page<Key, Value>): Key? = null

    private val onInvalidatedCallbacks = CopyOnWriteArrayList<() -> Unit>()

    private val _invalid = AtomicBoolean(false)
    /**
     * Whether this [PagedSource] has been invalidated, which should happen when the data this
     * [PagedSource] represents changes since it was first instantiated.
     */
    val invalid: Boolean
        get() = _invalid.get()

    /**
     * Signal the [PagedSource] to stop loading.
     *
     * This method is idempotent. i.e., If [invalidate] has already been called, subsequent calls to
     * this method should have no effect.
     *
     * TODO(b/137971356): Investigate making this not open when able to remove [LegacyPagedSource].
     */
    open fun invalidate() {
        if (_invalid.compareAndSet(false, true)) {
            onInvalidatedCallbacks.forEach { it.invoke() }
        }
    }

    /**
     * Add a callback to invoke when the [PagedSource] is first invalidated.
     *
     * Once invalidated, a [PagedSource] will not become valid again.
     *
     * A [PagedSource] will only invoke its callbacks once - the first time [invalidate] is called,
     * on that thread.
     *
     * @param onInvalidatedCallback The callback that will be invoked on thread that invalidates the
     * [PagedSource].
     */
    fun registerInvalidatedCallback(onInvalidatedCallback: () -> Unit) {
        onInvalidatedCallbacks.add(onInvalidatedCallback)
    }

    /**
     * Remove a previously added invalidate callback.
     *
     * @param onInvalidatedCallback The previously added callback.
     */
    fun unregisterInvalidatedCallback(onInvalidatedCallback: () -> Unit) {
        onInvalidatedCallbacks.remove(onInvalidatedCallback)
    }

    /**
     * Loading API for [PagedSource].
     *
     * Implement this method to trigger your async load (e.g. from database or network).
     */
    abstract suspend fun load(params: LoadParams<Key>): LoadResult<Key, Value>
}

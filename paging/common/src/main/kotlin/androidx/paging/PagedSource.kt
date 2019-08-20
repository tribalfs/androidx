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
import androidx.paging.PagedSource.KeyProvider
import androidx.paging.PagedSource.KeyProvider.ItemKey
import androidx.paging.PagedSource.KeyProvider.PageKey
import androidx.paging.PagedSource.KeyProvider.Positional
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
 * Base class for an abstraction of pageable static data from some source, where loading pages data
 * is typically an expensive operation. Some examples of common [PagedSource]s might be from network
 * or DB.
 *
 * This class was designed with the intent of being used as input into a [PagedList], which queries
 * snapshots of pages of data from a [PagedSource]. A [PagedList] can grow as it loads more data,
 * but the data loaded cannot be updated. If the underlying data set is modified, a new
 * [PagedList] / [PagedSource] pair must be created to represent the new data.
 *
 * <h4>Loading Pages</h4>
 *
 * [PagedList] queries data from its [PagedSource] in response to loading hints. `PagedListAdapter`
 * calls [PagedList.loadAround] to load content as the user scrolls in a `RecyclerView`.
 *
 * To control how and when a [PagedList] queries data from its [PagedSource], see
 * [PagedList.Config]. The [Config][PagedList.Config] object defines things like load sizes and
 * prefetch distance.
 *
 * <h4>Updating Paged Data</h4>
 *
 * A [PagedList] / [PagedSource] pair are a snapshot of the data set. A new pair of [PagedList] /
 * [PagedSource] must be created if an update occurs, such as a reorder, insert, delete,
 * or content update occurs. A [PagedSource] must detect that it cannot continue loading its
 * snapshot (for instance, when Database query notices a table being invalidated), and call
 * [invalidate]. Then a new [PagedList] / [PagedSource] pair would be created to load data from the
 * new state of the Database query.
 *
 * To page in data that doesn't update, you can create a single [PagedSource], and pass it to a
 * single [PagedList]. For example, loading from network when the network's paging API doesn't
 * provide updates.
 *
 * If you have granular update signals, such as a network API signaling an update to a single
 * item in the list, it's recommended to load data from network into memory. Then present that
 * data to the [PagedList] via a [PagedSource] that wraps an in-memory snapshot. Each time the
 * in-memory copy changes, invalidate the previous [PagedSource], and a new one wrapping the new
 * state of the snapshot can be created.
 *
 * <h4>Implementing a PagedSource</h4>
 *
 * When implementing a [PagedSource] the [keyProvider] choice should reflect the available API you
 * have for loading paged data:
 * * [KeyProvider.PageKey] if pages you load embed keys for loading adjacent pages. For example a
 * network response that returns some items, and a next/previous page links.
 * * [KeyProvider.ItemKey] if you need to use data from item `N-1` to load item `N`. For example, if
 * requesting the backend for the next comments in the list requires the ID or timestamp of the most
 * recent loaded comment, or if querying the next users from a name-sorted database query requires
 * the name and unique ID of the previous.
 * * [KeyProvider.Positional] if you can load pages of a requested size at arbitrary positions, and
 * provide a fixed item count. [KeyProvider.Positional] supports querying pages at arbitrary
 * positions, so can provide data to [PagedList]s in arbitrary order.
 *
 * Because a `null` item indicates a placeholder in [PagedList], [PagedSource] may not return `null`
 * items in lists that it loads. This is so that users of the [PagedList] can differentiate unloaded
 * placeholder items from content that has been paged in.
 *
 * @param Key Type for unique identifier for items loaded from [PagedSource]. E.g., [Int] to
 * represent an item's position in a [PagedSource] implemented with a [KeyProvider.Positional]. Note
 * that this is distinct from e.g. Room's `<Value> Value type loaded by the [PagedSource].
 * @param Value Type of data loaded in by this [PagedSource]. E.g., the type of data that will be
 * passed to a [PagedList] to be displayed in a `RecyclerView`
 */
abstract class PagedSource<Key : Any, Value : Any> {

    /**
     * Params for generic load request on a [PagedSource].
     *
     * TODO: Builder for Java (also consider @JvmOverloads)
     */
    data class LoadParams<Key : Any>(
        /**
         * [PageLoadType], for different behavior, e.g. only count initial load
         */
        val loadType: PageLoadType,
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

    /**
     * Result object for a generic load request on a [PagedSource].
     *
     * TODO: Builder for Java (also consider @JvmOverloads)
     */
    data class LoadResult<Key : Any, Value : Any>(
        /**
         * Loaded data
         */
        val data: List<Value>,
        /**
         * Optional count of items before the loaded data.
         */
        @IntRange(from = COUNT_UNDEFINED.toLong())
        val itemsBefore: Int = COUNT_UNDEFINED,
        /**
         * Optional count of items after the loaded data.
         */
        @IntRange(from = COUNT_UNDEFINED.toLong())
        val itemsAfter: Int = COUNT_UNDEFINED,
        /**
         * Key for next page - ignored unless you're using [KeyProvider.PageKey]
         */
        val nextKey: Key? = null,
        /**
         * Key for previous page - ignored unless you're using [KeyProvider.PageKey]
         */
        val prevKey: Key? = null
    ) {
        internal val counted = itemsBefore != COUNT_UNDEFINED && itemsAfter != COUNT_UNDEFINED

        companion object {
            const val COUNT_UNDEFINED = -1

            @Suppress("MemberVisibilityCanBePrivate") // Prevent synthetic accessor generation.
            internal val EMPTY = LoadResult(emptyList(), 0, 0, null, null)

            @Suppress("UNCHECKED_CAST") // Can safely ignore, since the list is empty.
            internal fun <Key : Any, Value : Any> empty() = EMPTY as LoadResult<Key, Value>
        }
    }

    /**
     * Used to define how pages are indexed, one of:
     * * [Positional] (Items are loaded using their position in the query as the [Key]).
     * * [PageKey] Standard for network pagination. Each page loaded has a next/previous page link
     * or token.
     * * [ItemKey] Data in the last item in the page is used to load the next. Ideal for DB
     * pagination; offers granular continuation after [invalidate].
     */
    sealed class KeyProvider<Key : Any, in Value : Any> {
        object Positional : KeyProvider<Int, Any>()
        class PageKey<Key : Any, Value : Any> : KeyProvider<Key, Value>()
        abstract class ItemKey<Key : Any, Value : Any> : KeyProvider<Key, Value>() {
            abstract fun getKey(item: Value): Key
        }

        internal companion object {
            val pageKeyGlobal = PageKey<Any, Any>()
            @Suppress("UNCHECKED_CAST")
            fun <Key : Any, Value : Any> PageKeyGlobal() = pageKeyGlobal as PageKey<Key, Value>
        }
    }

    open val keyProvider: KeyProvider<Key, Value> = KeyProvider.PageKeyGlobal()

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
     * TODO(b/137971356): Investigate making this not open when able to remove [PagedSourceWrapper].
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

    /**
     * Determine whether an error passed to a loading method is retryable.
     *
     * @param error Throwable returned from an attempted load from this [PagedSource].
     * @return `false` if the observed error should never be retried, `true` otherwise.
     */
    open fun isRetryableError(error: Throwable): Boolean = true
}

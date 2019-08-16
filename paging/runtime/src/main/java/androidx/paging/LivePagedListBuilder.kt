/*
 * Copyright (C) 2017 The Android Open Source Project
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

import androidx.lifecycle.LiveData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executor

/**
 * Builder for `LiveData<PagedList>` for Java users, given a [androidx.paging.DataSource.Factory]
 * and a [androidx.paging.PagedList.Config].
 *
 * The required parameters are in the constructor, so you can simply construct and build, or
 * optionally enable extra features (such as initial load key, or BoundaryCallback).
 *
 * @param Key Type of input valued used to load data from the [DataSource]. Must be [Int] if you're
 * using [PositionalDataSource].
 * @param Value Item type being presented.
 *
 * @see toLiveData
 */
class LivePagedListBuilder<Key : Any, Value : Any> {
    private val pagedSourceFactory: PagedSourceFactory<Key, Value>
    private val config: PagedList.Config
    private var coroutineScope: CoroutineScope = GlobalScope
    private var initialLoadKey: Key? = null
    private var boundaryCallback: PagedList.BoundaryCallback<Value>? = null
    private var fetchDispatcher = Dispatchers.IO

    /**
     * Creates a [LivePagedListBuilder] with required parameters.
     *
     * @param dataSourceFactory [DataSource] factory providing DataSource generations.
     * @param config Paging configuration.
     */
    @Deprecated("DataSource is deprecated and has been replaced by PagedSource")
    constructor(dataSourceFactory: DataSource.Factory<Key, Value>, config: PagedList.Config) {
        this.pagedSourceFactory = { PagedSourceWrapper(dataSourceFactory.create()) }
        this.config = config
    }

    /**
     * Creates a [LivePagedListBuilder] with required parameters.
     *
     * This method is a convenience for:
     * ```
     * LivePagedListBuilder(dataSourceFactory,
     *         new PagedList.Config.Builder().setPageSize(pageSize).build())
     * ```
     *
     * @param dataSourceFactory [DataSource.Factory] providing DataSource generations.
     * @param pageSize Size of pages to load.
     */
    @Suppress("DEPRECATION")
    @Deprecated("DataSource is deprecated and has been replaced by PagedSource")
    constructor(dataSourceFactory: DataSource.Factory<Key, Value>, pageSize: Int) : this(
        dataSourceFactory,
        PagedList.Config.Builder().setPageSize(pageSize).build()
    )

    /**
     * Creates a [LivePagedListBuilder] with required parameters.
     *
     * @param pagedSourceFactory [PagedSource] factory providing [PagedSource] generations.
     *
     * The returned [PagedSource] should invalidate itself if the snapshot is no longer valid. If a
     * [PagedSource] becomes invalid, the only way to query more data is to create a new
     * [PagedSource] by invoking the supplied [pagedSourceFactory].
     *
     * [pagedSourceFactory] will invoked to construct a new [PagedList] and [PagedSource] when the
     * current [PagedSource] is invalidated, and pass the new [PagedList] through the
     * `LiveData<PagedList>` to observers.
     * @param config Paging configuration.
     */
    constructor(pagedSourceFactory: PagedSourceFactory<Key, Value>, config: PagedList.Config) {
        this.pagedSourceFactory = pagedSourceFactory
        this.config = config
    }

    /**
     * Creates a [LivePagedListBuilder] with required parameters.
     *
     * This method is a convenience for:
     * ```
     * LivePagedListBuilder(pagedSourceFactory,
     *         new PagedList.Config.Builder().setPageSize(pageSize).build())
     * ```
     *
     * @param pagedSourceFactory [PagedSource] factory providing [PagedSource] generations.
     *
     * The returned [PagedSource] should invalidate itself if the snapshot is no longer valid. If a
     * [PagedSource] becomes invalid, the only way to query more data is to create a new
     * [PagedSource] by invoking the supplied [pagedSourceFactory].
     *
     * [pagedSourceFactory] will invoked to construct a new [PagedList] and [PagedSource] when the
     * current [PagedSource] is invalidated, and pass the new [PagedList] through the
     * `LiveData<PagedList>` to observers.
     * @param pageSize Size of pages to load.
     */
    constructor(pagedSourceFactory: PagedSourceFactory<Key, Value>, pageSize: Int) : this(
        pagedSourceFactory,
        PagedList.Config.Builder().setPageSize(pageSize).build()
    )

    /**
     * Set the [CoroutineScope] that page loads should be launched within. The set [coroutineScope]
     * allows a [PagedSource] to cancel running load operations when the results are no longer
     * needed - for example, when the containing activity is destroyed.
     *
     * Defaults to [GlobalScope].
     *
     * @param coroutineScope
     * @return this
     */
    @Suppress("unused") // Public API
    fun setCoroutineScope(coroutineScope: CoroutineScope) = this.apply {
        this.coroutineScope = coroutineScope
    }

    /**
     * First loading key passed to the first PagedList/DataSource.
     *
     * When a new PagedList/DataSource pair is created after the first, it acquires a load key from
     * the previous generation so that data is loaded around the position already being observed.
     *
     * @param key Initial load key passed to the first PagedList/DataSource.
     * @return this
     */
    fun setInitialLoadKey(key: Key?) = this.apply {
        initialLoadKey = key
    }

    /**
     * Sets a [androidx.paging.PagedList.BoundaryCallback] on each PagedList created,
     * typically used to load additional data from network when paging from local storage.
     *
     * Pass a [PagedList.BoundaryCallback] to listen to when the PagedList runs out of data to load.
     * If this method is not called, or `null` is passed, you will not be notified when each
     * [PagedSource] runs out of data to provide to its [PagedList].
     *
     * If you are paging from a DataSource.Factory backed by local storage, you can set a
     * BoundaryCallback to know when there is no more information to page from local storage.
     * This is useful to page from the network when local storage is a cache of network data.
     *
     * Note that when using a BoundaryCallback with a `LiveData<PagedList>`, method calls
     * on the callback may be dispatched multiple times - one for each PagedList/DataSource
     * pair. If loading network data from a BoundaryCallback, you should prevent multiple
     * dispatches of the same method from triggering multiple simultaneous network loads.
     *
     * @param boundaryCallback The boundary callback for listening to PagedList load state.
     * @return this
     */
    fun setBoundaryCallback(boundaryCallback: PagedList.BoundaryCallback<Value>?) = this.apply {
        this.boundaryCallback = boundaryCallback
    }

    /**
     * Sets [Executor] used for background fetching of [PagedList]s, and the pages within.
     *
     * The library will wrap this as a [kotlinx.coroutines.CoroutineDispatcher].
     *
     * If not set, defaults to [Dispatchers.IO].
     *
     * @param fetchExecutor [Executor] for fetching data from [PagedSource]s.
     * @return this
     */
    fun setFetchExecutor(fetchExecutor: Executor) = this.apply {
        this.fetchDispatcher = fetchExecutor.asCoroutineDispatcher()
    }

    /**
     * Constructs the `LiveData<PagedList>`.
     *
     * No work (such as loading) is done immediately, the creation of the first PagedList is is
     * deferred until the LiveData is observed.
     *
     * @return The [LiveData] of [PagedList]s
     */
    fun build(): LiveData<PagedList<Value>> {
        return LivePagedList(
            coroutineScope,
            initialLoadKey,
            config,
            boundaryCallback,
            pagedSourceFactory,
            Dispatchers.Main,
            fetchDispatcher
        )
    }
}

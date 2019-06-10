/*
 * Copyright 2017 The Android Open Source Project
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

import androidx.arch.core.util.Function
import androidx.paging.futures.DirectExecutor
import androidx.testutils.TestExecutor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyZeroInteractions

@RunWith(JUnit4::class)
class PageKeyedDataSourceTest {
    private val mainThread = TestExecutor()
    private val backgroundThread = TestExecutor()

    internal data class Item(val name: String)

    internal data class Page(val prev: String?, val data: List<Item>, val next: String?)

    internal class ItemDataSource(val data: Map<String, Page> = PAGE_MAP) :
        PageKeyedDataSource<String, Item>() {
        private var error = false

        private fun getPage(key: String): Page = data[key]!!

        override fun loadInitial(
            params: LoadInitialParams<String>,
            callback: LoadInitialCallback<String, Item>
        ) {
            if (error) {
                callback.onError(EXCEPTION)
                error = false
                return
            }

            val page = getPage(INIT_KEY)
            callback.onResult(page.data, page.prev, page.next)
        }

        override fun loadBefore(params: LoadParams<String>, callback: LoadCallback<String, Item>) {
            if (error) {
                callback.onError(EXCEPTION)
                error = false
                return
            }

            val page = getPage(params.key)
            callback.onResult(page.data, page.prev)
        }

        override fun loadAfter(params: LoadParams<String>, callback: LoadCallback<String, Item>) {
            if (error) {
                callback.onError(EXCEPTION)
                error = false
                return
            }

            val page = getPage(params.key)
            callback.onResult(page.data, page.next)
        }

        fun enqueueError() {
            error = true
        }
    }

    @Test
    fun loadFullVerify() {
        // validate paging entire ItemDataSource results in full, correctly ordered data
        val pagedListFuture = PagedList.create(
            ItemDataSource(),
            mainThread,
            backgroundThread,
            backgroundThread,
            null,
            PagedList.Config.Builder().setPageSize(100).build(),
            null
        )
        backgroundThread.executeAll()
        val pagedList = pagedListFuture.get()

        // validate initial load
        assertEquals(PAGE_MAP[INIT_KEY]!!.data, pagedList)

        // flush the remaining loads
        for (i in 0..PAGE_MAP.keys.size) {
            pagedList.loadAround(0)
            pagedList.loadAround(pagedList.size - 1)
            drain()
        }

        // validate full load
        assertEquals(ITEM_LIST, pagedList)
    }

    private fun performLoadInitial(
        invalidateDataSource: Boolean = false,
        callbackInvoker:
            (callback: PageKeyedDataSource.LoadInitialCallback<String, String>) -> Unit
    ) {
        val dataSource = object : PageKeyedDataSource<String, String>() {
            override fun loadInitial(
                params: LoadInitialParams<String>,
                callback: LoadInitialCallback<String, String>
            ) {
                if (invalidateDataSource) {
                    // invalidate data source so it's invalid when onResult() called
                    invalidate()
                }
                callbackInvoker(callback)
            }

            override fun loadBefore(
                params: LoadParams<String>,
                callback: LoadCallback<String, String>
            ) {
                fail("loadBefore not expected")
            }

            override fun loadAfter(
                params: LoadParams<String>,
                callback: LoadCallback<String, String>
            ) {
                fail("loadAfter not expected")
            }
        }

        PagedList.create(
            dataSource,
            FailExecutor(),
            DirectExecutor,
            DirectExecutor,
            null,
            PagedList.Config.Builder()
                .setPageSize(10)
                .build(),
            ""
        ).get()
    }

    @Test
    fun loadInitialCallbackSuccess() = performLoadInitial {
        // LoadInitialCallback correct usage
        it.onResult(listOf("a", "b"), 0, 2, null, null)
    }

    @Test
    fun loadInitialCallbackNotPageSizeMultiple() = performLoadInitial {
        // Keyed LoadInitialCallback *can* accept result that's not a multiple of page size
        val elevenLetterList = List(11) { index -> "" + ('a' + index) }
        it.onResult(elevenLetterList, 0, 12, null, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun loadInitialCallbackListTooBig() = performLoadInitial {
        // LoadInitialCallback can't accept pos + list > totalCount
        it.onResult(listOf("a", "b", "c"), 0, 2, null, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun loadInitialCallbackPositionTooLarge() = performLoadInitial {
        // LoadInitialCallback can't accept pos + list > totalCount
        it.onResult(listOf("a", "b"), 1, 2, null, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun loadInitialCallbackPositionNegative() = performLoadInitial {
        // LoadInitialCallback can't accept negative position
        it.onResult(listOf("a", "b", "c"), -1, 2, null, null)
    }

    @Test(expected = IllegalArgumentException::class)
    fun loadInitialCallbackEmptyCannotHavePlaceholders() = performLoadInitial {
        // LoadInitialCallback can't accept empty result unless data set is empty
        it.onResult(emptyList(), 0, 2, null, null)
    }

    @Test
    fun pageDroppingNotSupported() {
        assertFalse(ItemDataSource().supportsPageDropping)
    }

    @Test
    fun testBoundaryCallback() {
        val dataSource = object : PageKeyedDataSource<String, String>() {
            override fun loadInitial(
                params: LoadInitialParams<String>,
                callback: LoadInitialCallback<String, String>
            ) {
                callback.onResult(listOf("B"), "a", "c")
            }

            override fun loadBefore(
                params: LoadParams<String>,
                callback: LoadCallback<String, String>
            ) {
                assertEquals("a", params.key)
                callback.onResult(listOf("A"), null)
            }

            override fun loadAfter(
                params: LoadParams<String>,
                callback: LoadCallback<String, String>
            ) {
                assertEquals("c", params.key)
                callback.onResult(listOf("C"), null)
            }
        }

        @Suppress("UNCHECKED_CAST")
        val boundaryCallback =
            mock(PagedList.BoundaryCallback::class.java) as PagedList.BoundaryCallback<String>
        val executor = TestExecutor()

        val pagedList = PagedList.create(
            dataSource,
            executor,
            executor,
            executor,
            boundaryCallback,
            PagedList.Config.Builder()
                .setPageSize(10)
                .build(),
            ""
        ).apply { executor.executeAll() }.get()

        pagedList.loadAround(0)

        verifyZeroInteractions(boundaryCallback)

        executor.executeAll()

        // verify boundary callbacks are triggered
        verify(boundaryCallback).onItemAtFrontLoaded("A")
        verify(boundaryCallback).onItemAtEndLoaded("C")
        verifyNoMoreInteractions(boundaryCallback)
    }

    @Test
    fun testBoundaryCallbackJustInitial() {
        val dataSource = object : PageKeyedDataSource<String, String>() {
            override fun loadInitial(
                params: LoadInitialParams<String>,
                callback: LoadInitialCallback<String, String>
            ) {
                // just the one load, but boundary callbacks should still be triggered
                callback.onResult(listOf("B"), null, null)
            }

            override fun loadBefore(
                params: LoadParams<String>,
                callback: LoadCallback<String, String>
            ) {
                fail("loadBefore not expected")
            }

            override fun loadAfter(
                params: LoadParams<String>,
                callback: LoadCallback<String, String>
            ) {
                fail("loadBefore not expected")
            }
        }

        @Suppress("UNCHECKED_CAST")
        val boundaryCallback =
            mock(PagedList.BoundaryCallback::class.java) as PagedList.BoundaryCallback<String>
        val executor = TestExecutor()

        val pagedList = PagedList.create(
            dataSource,
            executor,
            executor,
            executor,
            boundaryCallback,
            PagedList.Config.Builder()
                .setPageSize(10)
                .build(),
            ""
        ).apply { executor.executeAll() }.get()

        pagedList.loadAround(0)

        verifyZeroInteractions(boundaryCallback)

        executor.executeAll()

        // verify boundary callbacks are triggered
        verify(boundaryCallback).onItemAtFrontLoaded("B")
        verify(boundaryCallback).onItemAtEndLoaded("B")
        verifyNoMoreInteractions(boundaryCallback)
    }

    private abstract class WrapperDataSource<K : Any, A : Any, B : Any>(
        private val source: PageKeyedDataSource<K, A>
    ) : PageKeyedDataSource<K, B>() {
        override fun addInvalidatedCallback(onInvalidatedCallback: InvalidatedCallback) {
            source.addInvalidatedCallback(onInvalidatedCallback)
        }

        override fun removeInvalidatedCallback(onInvalidatedCallback: InvalidatedCallback) {
            source.removeInvalidatedCallback(onInvalidatedCallback)
        }

        override fun invalidate() {
            source.invalidate()
        }

        override val isInvalid
            get() = source.isInvalid

        override fun loadInitial(
            params: LoadInitialParams<K>,
            callback: LoadInitialCallback<K, B>
        ) {
            source.loadInitial(params, object : LoadInitialCallback<K, A>() {
                override fun onResult(
                    data: List<A>,
                    position: Int,
                    totalCount: Int,
                    previousPageKey: K?,
                    nextPageKey: K?
                ) {
                    callback.onResult(
                        convert(data),
                        position,
                        totalCount,
                        previousPageKey,
                        nextPageKey
                    )
                }

                override fun onResult(data: List<A>, previousPageKey: K?, nextPageKey: K?) {
                    callback.onResult(convert(data), previousPageKey, nextPageKey)
                }

                override fun onError(error: Throwable) {
                    callback.onError(error)
                }
            })
        }

        override fun loadBefore(params: LoadParams<K>, callback: LoadCallback<K, B>) {
            source.loadBefore(params, object : LoadCallback<K, A>() {
                override fun onResult(data: List<A>, adjacentPageKey: K?) {
                    callback.onResult(convert(data), adjacentPageKey)
                }

                override fun onError(error: Throwable) {
                    callback.onError(error)
                }
            })
        }

        override fun loadAfter(params: LoadParams<K>, callback: LoadCallback<K, B>) {
            source.loadAfter(params, object : LoadCallback<K, A>() {
                override fun onResult(data: List<A>, adjacentPageKey: K?) {
                    callback.onResult(convert(data), adjacentPageKey)
                }

                override fun onError(error: Throwable) {
                    callback.onError(error)
                }
            })
        }

        protected abstract fun convert(source: List<A>): List<B>
    }

    private class StringWrapperDataSource<K : Any, V : Any>(source: PageKeyedDataSource<K, V>) :
        WrapperDataSource<K, V, String>(source) {
        override fun convert(source: List<V>): List<String> {
            return source.map { it.toString() }
        }
    }

    private fun verifyWrappedDataSource(
        createWrapper: (PageKeyedDataSource<String, Item>) -> PageKeyedDataSource<String, String>
    ) {
        // verify that it's possible to wrap a PageKeyedDataSource, and add info to its data
        val orig = ItemDataSource(data = PAGE_MAP)
        val wrapper = createWrapper(orig)

        // load initial
        @Suppress("UNCHECKED_CAST")
        val loadInitialCallback = mock(PageKeyedDataSource.LoadInitialCallback::class.java)
                as PageKeyedDataSource.LoadInitialCallback<String, String>

        val initParams = PageKeyedDataSource.LoadInitialParams<String>(4, true)
        wrapper.loadInitial(initParams, loadInitialCallback)
        val expectedInitial = PAGE_MAP.getValue(INIT_KEY)
        verify(loadInitialCallback).onResult(
            expectedInitial.data.map { it.toString() },
            expectedInitial.prev, expectedInitial.next
        )
        verifyNoMoreInteractions(loadInitialCallback)

        @Suppress("UNCHECKED_CAST")
        // load after
        var loadCallback = mock(PageKeyedDataSource.LoadCallback::class.java)
                as PageKeyedDataSource.LoadCallback<String, String>
        wrapper.loadAfter(PageKeyedDataSource.LoadParams(expectedInitial.next!!, 4), loadCallback)
        val expectedAfter = PAGE_MAP[expectedInitial.next]!!
        verify(loadCallback).onResult(expectedAfter.data.map { it.toString() }, expectedAfter.next)
        // load after - error
        orig.enqueueError()
        wrapper.loadAfter(PageKeyedDataSource.LoadParams(expectedInitial.next, 4), loadCallback)
        verify(loadCallback).onError(EXCEPTION)
        verifyNoMoreInteractions(loadCallback)

        // load before
        @Suppress("UNCHECKED_CAST")
        loadCallback = mock(PageKeyedDataSource.LoadCallback::class.java)
                as PageKeyedDataSource.LoadCallback<String, String>
        wrapper.loadBefore(PageKeyedDataSource.LoadParams(expectedAfter.prev!!, 4), loadCallback)
        verify(loadCallback).onResult(
            expectedInitial.data.map { it.toString() },
            expectedInitial.prev
        )
        verifyNoMoreInteractions(loadCallback)
        // load before - error
        orig.enqueueError()
        wrapper.loadBefore(PageKeyedDataSource.LoadParams(expectedAfter.prev, 4), loadCallback)
        verify(loadCallback).onError(EXCEPTION)
        verifyNoMoreInteractions(loadCallback)

        // verify invalidation
        orig.invalidate()
        assertTrue(wrapper.isInvalid)
    }

    @Test
    fun testManualWrappedDataSource() = verifyWrappedDataSource {
        StringWrapperDataSource(it)
    }

    @Test
    fun testListConverterWrappedDataSource() = verifyWrappedDataSource { dataSource ->
        dataSource.mapByPage(Function { page -> page.map { it.toString() } })
    }

    @Test
    fun testItemConverterWrappedDataSource() = verifyWrappedDataSource { dataSource ->
        dataSource.map(Function { it.toString() })
    }

    @Test
    fun testInvalidateToWrapper() {
        val orig = ItemDataSource()
        val wrapper = orig.map<String>(Function { it.toString() })

        orig.invalidate()
        assertTrue(wrapper.isInvalid)
    }

    @Test
    fun testInvalidateFromWrapper() {
        val orig = ItemDataSource()
        val wrapper = orig.map<String>(Function { it.toString() })

        wrapper.invalidate()
        assertTrue(orig.isInvalid)
    }

    companion object {
        // first load is 2nd page to ensure we test prepend as well as append behavior
        private const val INIT_KEY: String = "key 2"
        private val PAGE_MAP: Map<String, Page>
        private val ITEM_LIST: List<Item>
        private val EXCEPTION = Exception()

        init {
            val map = HashMap<String, Page>()
            val list = ArrayList<Item>()
            val pageCount = 5
            for (i in 1..pageCount) {
                val data = List(4) { Item("name $i $it") }
                list.addAll(data)

                val key = "key $i"
                val prev = if (i > 1) ("key " + (i - 1)) else null
                val next = if (i < pageCount) ("key " + (i + 1)) else null
                map[key] = Page(prev, data, next)
            }
            PAGE_MAP = map
            ITEM_LIST = list
        }
    }

    private fun drain() {
        var executed: Boolean
        do {
            executed = backgroundThread.executeAll()
            executed = mainThread.executeAll() || executed
        } while (executed)
    }
}

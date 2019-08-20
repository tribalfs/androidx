/*
 * Copyright 2018 The Android Open Source Project
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

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import androidx.paging.PagedList.LoadState.Error
import androidx.paging.PagedList.LoadState.Idle
import androidx.paging.PagedList.LoadState.Loading
import androidx.paging.PageLoadType.REFRESH
import androidx.test.filters.SmallTest
import androidx.testutils.TestDispatcher
import androidx.testutils.TestExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class LivePagedListBuilderTest {
    private val mainDispatcher = TestDispatcher()
    private val backgroundExecutor = TestExecutor()
    private val lifecycleOwner = object : LifecycleOwner {
        private val lifecycle = LifecycleRegistry(this)

        override fun getLifecycle(): Lifecycle {
            return lifecycle
        }

        fun handleEvent(event: Lifecycle.Event) {
            lifecycle.handleLifecycleEvent(event)
        }
    }

    private data class LoadState(
        val type: PageLoadType,
        val state: PagedList.LoadState
    )

    @ExperimentalCoroutinesApi
    @Before
    fun setup() {
        Dispatchers.setMain(mainDispatcher)
        ArchTaskExecutor.getInstance().setDelegate(object : TaskExecutor() {
            override fun executeOnDiskIO(runnable: Runnable) {
                fail("IO executor should be overwritten")
            }

            override fun postToMainThread(runnable: Runnable) {
                runnable.run()
            }

            override fun isMainThread(): Boolean {
                return true
            }
        })
        lifecycleOwner.handleEvent(Lifecycle.Event.ON_START)
    }

    @ExperimentalCoroutinesApi
    @After
    fun teardown() {
        lifecycleOwner.handleEvent(Lifecycle.Event.ON_STOP)
        ArchTaskExecutor.getInstance().setDelegate(null)
        Dispatchers.resetMain()
    }

    class MockDataSourceFactory {
        fun create(): PagedSource<Int, String> {
            return MockPagedSource()
        }

        var throwable: Throwable? = null

        fun enqueueRetryableError() {
            throwable = RETRYABLE_EXCEPTION
        }

        private inner class MockPagedSource : PagedSource<Int, String>() {
            override val keyProvider = KeyProvider.Positional

            override suspend fun load(params: LoadParams<Int>) = when (params.loadType) {
                PageLoadType.REFRESH -> loadInitial(params)
                else -> loadRange()
            }

            override fun isRetryableError(error: Throwable) = error === RETRYABLE_EXCEPTION

            private fun loadInitial(params: LoadParams<Int>): LoadResult<Int, String> {
                assertEquals(2, params.pageSize)

                throwable?.let { error ->
                    throwable = null
                    throw error
                }

                val data = listOf("a", "b")
                return LoadResult(
                    data = data,
                    itemsBefore = 0,
                    itemsAfter = 4 - data.size
                )
            }

            private fun loadRange(): LoadResult<Int, String> {
                return LoadResult(listOf("c", "d"))
            }
        }
    }

    @Test
    fun executorBehavior() {
        // specify a background dispatcher via builder, and verify it gets used for all loads,
        // overriding default IO dispatcher
        val livePagedList = LivePagedListBuilder(MockDataSourceFactory()::create, 2)
            .setFetchExecutor(backgroundExecutor)
            .build()

        val pagedListHolder: Array<PagedList<String>?> = arrayOfNulls(1)

        livePagedList.observe(lifecycleOwner, Observer<PagedList<String>> { newList ->
            pagedListHolder[0] = newList
        })

        // initially, immediately get passed empty initial list
        assertNotNull(pagedListHolder[0])
        assertTrue(pagedListHolder[0] is InitialPagedList<*, *>)

        // flush loadInitial, done with passed executor
        drain()

        val pagedList = pagedListHolder[0]
        assertNotNull(pagedList)
        assertEquals(listOf("a", "b", null, null), pagedList)

        // flush loadRange
        pagedList!!.loadAround(2)
        drain()

        assertEquals(listOf("a", "b", "c", "d"), pagedList)
    }

    @Test
    fun failedLoad() {
        val factory = MockDataSourceFactory()
        factory.enqueueRetryableError()

        val livePagedList = LivePagedListBuilder(factory::create, 2)
            .setFetchExecutor(backgroundExecutor)
            .build()

        val pagedListHolder: Array<PagedList<String>?> = arrayOfNulls(1)

        livePagedList.observe(lifecycleOwner, Observer<PagedList<String>> { newList ->
            pagedListHolder[0] = newList
        })

        val loadStates = mutableListOf<LoadState>()

        // initially, immediately get passed empty initial list
        val initPagedList = pagedListHolder[0]
        assertNotNull(initPagedList!!)
        assertTrue(initPagedList is InitialPagedList<*, *>)

        val loadStateChangedCallback: LoadStateListener = { type, state ->
            if (type == REFRESH) {
                loadStates.add(LoadState(type, state))
            }
        }
        initPagedList.addWeakLoadStateListener(loadStateChangedCallback)

        // flush loadInitial, done with passed dispatcher
        drain()

        assertSame(initPagedList, pagedListHolder[0])
        // TODO: Investigate removing initial IDLE state from callback updates.
        assertEquals(
            listOf(
                LoadState(REFRESH, Idle),
                LoadState(REFRESH, Loading),
                LoadState(REFRESH, Error(RETRYABLE_EXCEPTION, true))
            ), loadStates
        )

        initPagedList.retry()
        assertSame(initPagedList, pagedListHolder[0])

        // flush loadInitial, should succeed now
        drain()

        assertNotSame(initPagedList, pagedListHolder[0])
        assertEquals(listOf("a", "b", null, null), pagedListHolder[0])

        assertEquals(
            listOf(
                LoadState(REFRESH, Idle),
                LoadState(REFRESH, Loading),
                LoadState(REFRESH, Error(RETRYABLE_EXCEPTION, true)),
                LoadState(REFRESH, Loading)
            ), loadStates
        )

        // the IDLE result shows up on the next PagedList
        initPagedList.removeWeakLoadStateListener(loadStateChangedCallback)
        pagedListHolder[0]!!.addWeakLoadStateListener(loadStateChangedCallback)
        assertEquals(
            listOf(
                LoadState(REFRESH, Idle),
                LoadState(REFRESH, Loading),
                LoadState(REFRESH, Error(RETRYABLE_EXCEPTION, true)),
                LoadState(REFRESH, Loading),
                LoadState(REFRESH, Idle)
            ),
            loadStates
        )
    }

    private fun drain() {
        var executed: Boolean
        do {
            executed = backgroundExecutor.executeAll()
            mainDispatcher.executeAll()
        } while (executed || mainDispatcher.queue.isNotEmpty())
    }

    companion object {
        val RETRYABLE_EXCEPTION = Exception("retryable")
    }
}

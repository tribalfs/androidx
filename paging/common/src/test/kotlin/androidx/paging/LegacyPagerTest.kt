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

@file:Suppress("DEPRECATION")

package androidx.paging

import androidx.paging.LoadType.END
import androidx.paging.LoadType.REFRESH
import androidx.paging.LoadType.START
import androidx.paging.PagedList.Config
import androidx.paging.PagingSource.LoadResult
import androidx.paging.PagingSource.LoadResult.Page
import androidx.testutils.TestDispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class LegacyPagerTest {
    private val testDispatcher = TestDispatcher()
    private val data = List(9) { "$it" }

    inner class ImmediateListDataSource(val data: List<String>) : PagingSource<Int, String>() {
        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, String> {
            val key = params.key ?: 0

            val start = when (params.loadType) {
                REFRESH -> key
                START -> key - params.loadSize
                END -> key
            }.coerceAtLeast(0)

            val end = when (params.loadType) {
                REFRESH -> key + params.loadSize
                START -> key
                END -> key + params.loadSize
            }.coerceAtMost(data.size)

            return Page(
                data = data.subList(start, end),
                prevKey = if (start > 0) start else null,
                nextKey = if (end < data.size) end else null,
                itemsBefore = start,
                itemsAfter = data.size - end
            )
        }
    }

    private fun rangeResult(start: Int, end: Int) = Page(
        data = data.subList(start, end),
        prevKey = if (start > 0) start else null,
        nextKey = if (end < data.size) end else null,
        itemsBefore = start,
        itemsAfter = data.size - end
    )

    private data class Result(
        val type: LoadType,
        val pageResult: LoadResult<*, String>
    )

    private class MockConsumer : LegacyPager.PageConsumer<String> {
        private val results: MutableList<Result> = arrayListOf()
        private val stateChanges: MutableList<StateChange> = arrayListOf()

        var storage: PagedStorage<String>? = null

        fun takeResults(): List<Result> {
            val ret = results.map { it }
            results.clear()
            return ret
        }

        fun takeStateChanges(): List<StateChange> {
            val ret = stateChanges.map { it }
            stateChanges.clear()
            return ret
        }

        override fun onPageResult(type: LoadType, page: Page<*, String>): Boolean {
            @Suppress("NON_EXHAUSTIVE_WHEN")
            when (type) {
                START -> storage?.prependPage(page)
                END -> storage?.appendPage(page)
            }

            results.add(Result(type, page))
            return false
        }

        override fun onStateChanged(type: LoadType, state: LoadState) {
            stateChanges.add(StateChange(type, state))
        }
    }

    private fun createPager(
        consumer: MockConsumer,
        start: Int = 0,
        end: Int = 10
    ): LegacyPager<Int, String> {
        val config = Config(2, 2, true, 10, Config.MAX_SIZE_UNBOUNDED)
        val pagingSource = ImmediateListDataSource(data)

        val initialResult = runBlocking {
            pagingSource.load(
                PagingSource.LoadParams(
                    loadType = REFRESH,
                    key = start,
                    loadSize = end - start,
                    placeholdersEnabled = config.enablePlaceholders,
                    pageSize = config.pageSize
                )
            )
        }

        val initialData = (initialResult as Page).data
        val storage = PagedStorage(
            start,
            initialResult,
            data.size - initialData.size - start
        )
        consumer.storage = storage

        @Suppress("UNCHECKED_CAST")
        return LegacyPager(
            GlobalScope,
            config,
            pagingSource,
            DirectDispatcher,
            testDispatcher,
            consumer,
            storage as LegacyPager.KeyProvider<Int>
        )
    }

    @Test
    fun simplePagerAppend() {
        val consumer = MockConsumer()
        val pager = createPager(consumer, 2, 6)

        assertTrue(consumer.takeResults().isEmpty())
        assertTrue(consumer.takeStateChanges().isEmpty())

        pager.tryScheduleAppend()

        assertTrue(consumer.takeResults().isEmpty())
        assertEquals(
            listOf(StateChange(END, LoadState.Loading)),
            consumer.takeStateChanges()
        )

        testDispatcher.executeAll()

        assertEquals(listOf(Result(END, rangeResult(6, 8))), consumer.takeResults())
        assertEquals(
            listOf(StateChange(END, LoadState.NotLoading(endOfPaginationReached = false))),
            consumer.takeStateChanges()
        )
    }

    @Test
    fun simplePagerPrepend() {
        val consumer = MockConsumer()
        val pager = createPager(consumer, 4, 8)

        pager.trySchedulePrepend()

        assertTrue(consumer.takeResults().isEmpty())
        assertEquals(
            listOf(StateChange(START, LoadState.Loading)),
            consumer.takeStateChanges()
        )

        testDispatcher.executeAll()

        assertEquals(
            listOf(Result(START, rangeResult(2, 4))),
            consumer.takeResults()
        )
        assertEquals(
            listOf(StateChange(START, LoadState.NotLoading(endOfPaginationReached = false))),
            consumer.takeStateChanges()
        )
    }

    @Test
    fun doubleAppend() {
        val consumer = MockConsumer()
        val pager = createPager(consumer, 2, 6)

        pager.tryScheduleAppend()
        testDispatcher.executeAll()
        pager.tryScheduleAppend()
        testDispatcher.executeAll()

        assertEquals(
            listOf(
                Result(END, rangeResult(6, 8)),
                Result(END, rangeResult(8, 9))
            ), consumer.takeResults()
        )
        assertEquals(
            listOf(
                StateChange(END, LoadState.Loading),
                StateChange(END, LoadState.NotLoading(endOfPaginationReached = false)),
                StateChange(END, LoadState.Loading),
                StateChange(END, LoadState.NotLoading(endOfPaginationReached = false))
            ),
            consumer.takeStateChanges()
        )
    }

    @Test
    fun doublePrepend() {
        val consumer = MockConsumer()
        val pager = createPager(consumer, 4, 8)

        pager.trySchedulePrepend()
        testDispatcher.executeAll()
        pager.trySchedulePrepend()
        testDispatcher.executeAll()

        assertEquals(
            listOf(
                Result(START, rangeResult(2, 4)),
                Result(START, rangeResult(0, 2))
            ), consumer.takeResults()
        )
        assertEquals(
            listOf(
                StateChange(START, LoadState.Loading),
                StateChange(START, LoadState.NotLoading(endOfPaginationReached = false)),
                StateChange(START, LoadState.Loading),
                StateChange(START, LoadState.NotLoading(endOfPaginationReached = false))
            ),
            consumer.takeStateChanges()
        )
    }

    @Test
    fun emptyAppend() {
        val consumer = MockConsumer()
        val pager = createPager(consumer, 0, 9)

        pager.tryScheduleAppend()

        // Pager triggers an immediate empty response here, so we don't need to flush the executor
        assertEquals(
            listOf(Result(END, Page.empty<Int, String>())),
            consumer.takeResults()
        )
        assertEquals(
            listOf(StateChange(END, LoadState.NotLoading(endOfPaginationReached = true))),
            consumer.takeStateChanges()
        )
    }

    @Test
    fun emptyPrepend() {
        val consumer = MockConsumer()
        val pager = createPager(consumer, 0, 9)

        pager.trySchedulePrepend()

        // Pager triggers an immediate empty response here, so we don't need to flush the executor
        assertEquals(
            listOf(Result(START, Page.empty<Int, String>())),
            consumer.takeResults()
        )
        assertEquals(
            listOf(StateChange(START, LoadState.NotLoading(endOfPaginationReached = true))),
            consumer.takeStateChanges()
        )
    }
}

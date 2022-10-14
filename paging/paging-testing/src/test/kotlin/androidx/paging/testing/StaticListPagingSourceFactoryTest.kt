/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.paging.testing

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingSource.LoadResult.Page
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class StaticListPagingSourceFactoryTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())
    private val CONFIG = PagingConfig(
        pageSize = 3,
        initialLoadSize = 5
    )

    @Test
    fun emptyFlow() {
        val factory: () -> PagingSource<Int, Int> =
            flowOf<List<Int>>().asPagingSourceFactory(testScope)
        val pagingSource = factory()
        val pager = TestPager(pagingSource, CONFIG)

        runTest {
            val result = pager.refresh() as Page
            assertThat(result.data.isEmpty()).isTrue()
        }
    }

    @Test
    fun simpleCollect_singleGen() {
        val flow = flowOf(
            List(20) { it }
        )

        val factory: () -> PagingSource<Int, Int> =
            flow.asPagingSourceFactory(testScope)
        val pagingSource = factory()
        val pager = TestPager(pagingSource, CONFIG)

        runTest {
            val result = pager.refresh() as Page
            assertThat(result.data).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4)
            )
        }
    }

    @Test
    fun simpleCollect_multiGeneration() = testScope.runTest {
        val flow = flow {
            emit(List(20) { it }) // first gen
            delay(1500)
            emit(List(15) { it + 30 }) // second gen
        }

        val factory: () -> PagingSource<Int, Int> =
            flow.asPagingSourceFactory(testScope)

        advanceTimeBy(1000)

        // first gen
        val pagingSource1 = factory()
        val pager1 = TestPager(pagingSource1, CONFIG)
        val result1 = pager1.refresh() as Page
        assertThat(result1.data).containsExactlyElementsIn(
            listOf(0, 1, 2, 3, 4)
        )

        // second list emits -- this should invalidate original pagingSource and trigger new gen
        advanceUntilIdle()

        assertThat(pagingSource1.invalid).isTrue()

        // second gen
        val pagingSource2 = factory()
        val pager2 = TestPager(pagingSource2, CONFIG)
        val result2 = pager2.refresh() as Page
        assertThat(result2.data).containsExactlyElementsIn(
            listOf(30, 31, 32, 33, 34)
        )
    }

    @Test
    fun collection_cancellation() = testScope.runTest {
        val mutableFlow = MutableSharedFlow<List<Int>>()
        val collectionScope = this.backgroundScope

        val factory: () -> PagingSource<Int, Int> =
            mutableFlow.asPagingSourceFactory(collectionScope)

        mutableFlow.emit(List(10) { it })

        advanceUntilIdle()

        val pagingSource = factory()
        val pager = TestPager(pagingSource, CONFIG)
        val result = pager.refresh() as Page
        assertThat(result.data).containsExactlyElementsIn(
            listOf(0, 1, 2, 3, 4)
        )

        // cancel collection scope inside the pagingSourceFactory
        collectionScope.cancel()

        mutableFlow.emit(List(10) { it })

        advanceUntilIdle()

        // new list should not be collected, meaning the previous generation should still be valid
        assertThat(pagingSource.invalid).isFalse()
    }

    @Test
    fun multipleFactories_fromSameFlow() = testScope.runTest {
        val mutableFlow = MutableSharedFlow<List<Int>>()

        val factory1: () -> PagingSource<Int, Int> =
            mutableFlow.asPagingSourceFactory(testScope.backgroundScope)

        val factory2: () -> PagingSource<Int, Int> =
            mutableFlow.asPagingSourceFactory(testScope.backgroundScope)

        mutableFlow.emit(List(10) { it })

        advanceUntilIdle()

        // factory 1 first gen
        val pagingSource = factory1()
        val pager = TestPager(pagingSource, CONFIG)
        val result = pager.refresh() as Page
        assertThat(result.data).containsExactlyElementsIn(
            listOf(0, 1, 2, 3, 4)
        )

        // factory 2 first gen
        val pagingSource2 = factory2()
        val pager2 = TestPager(pagingSource2, CONFIG)
        val result2 = pager2.refresh() as Page
        assertThat(result2.data).containsExactlyElementsIn(
            listOf(0, 1, 2, 3, 4)
        )

        // trigger second generation
        mutableFlow.emit(List(10) { it + 30 })

        advanceUntilIdle()

        assertThat(pagingSource.invalid).isTrue()
        assertThat(pagingSource2.invalid).isTrue()

        // factory 1 second gen
        val pagingSource3 = factory1()
        val pager3 = TestPager(pagingSource3, CONFIG)
        val result3 = pager3.refresh() as Page
        assertThat(result3.data).containsExactlyElementsIn(
            listOf(30, 31, 32, 33, 34)
        )

        // factory 2 second gen
        val pagingSource4 = factory2()
        val pager4 = TestPager(pagingSource4, CONFIG)
        val result4 = pager4.refresh() as Page
        assertThat(result4.data).containsExactlyElementsIn(
            listOf(30, 31, 32, 33, 34)
        )
    }
}
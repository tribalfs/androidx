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

package android.arch.paging

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PositionalDataSourceTest {
    private fun computeInitialLoadPos(
            requestedStartPosition: Int,
            requestedLoadSize: Int,
            pageSize: Int,
            totalCount: Int): Int {
        val params = PositionalDataSource.LoadInitialParams(
                requestedStartPosition, requestedLoadSize, pageSize, true)
        return PositionalDataSource.computeInitialLoadPosition(params, totalCount)
    }

    @Test
    fun computeInitialLoadPositionZero() {
        assertEquals(0, computeInitialLoadPos(
                requestedStartPosition = 0,
                requestedLoadSize = 30,
                pageSize = 10,
                totalCount = 100))
    }

    @Test
    fun computeInitialLoadPositionRequestedPositionIncluded() {
        assertEquals(10, computeInitialLoadPos(
                requestedStartPosition = 10,
                requestedLoadSize = 10,
                pageSize = 10,
                totalCount = 100))
    }

    @Test
    fun computeInitialLoadPositionRound() {
        assertEquals(10, computeInitialLoadPos(
                requestedStartPosition = 13,
                requestedLoadSize = 30,
                pageSize = 10,
                totalCount = 100))
    }

    @Test
    fun computeInitialLoadPositionEndAdjusted() {
        assertEquals(70, computeInitialLoadPos(
                requestedStartPosition = 99,
                requestedLoadSize = 30,
                pageSize = 10,
                totalCount = 100))
    }

    @Test
    fun computeInitialLoadPositionEndAdjustedAndAligned() {
        assertEquals(70, computeInitialLoadPos(
                requestedStartPosition = 99,
                requestedLoadSize = 35,
                pageSize = 10,
                totalCount = 100))
    }

    private fun performInitialLoad(
            callbackInvoker: (callback: PositionalDataSource.LoadInitialCallback<String>) -> Unit) {
        val dataSource = object : PositionalDataSource<String>() {
            override fun loadInitial(
                    params: LoadInitialParams,
                    callback: LoadInitialCallback<String>) {
                callbackInvoker(callback)
            }

            override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<String>) {
                fail("loadRange not expected")
            }
        }

        TiledPagedList(
                dataSource, FailExecutor(), FailExecutor(), null,
                PagedList.Config.Builder()
                        .setPageSize(10)
                        .build(),
                0)
    }

    @Test
    fun initialLoadCallbackSuccess() = performInitialLoad {
        // LoadInitialCallback correct usage
        it.onResult(listOf("a", "b"), 0, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun initialLoadCallbackNotPageSizeMultiple() = performInitialLoad {
        // Positional LoadInitialCallback can't accept result that's not a multiple of page size
        val elevenLetterList = List(11) { "" + 'a' + it }
        it.onResult(elevenLetterList, 0, 12)
    }

    @Test(expected = IllegalArgumentException::class)
    fun initialLoadCallbackListTooBig() = performInitialLoad {
        // LoadInitialCallback can't accept pos + list > totalCount
        it.onResult(listOf("a", "b", "c"), 0, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun initialLoadCallbackPositionTooLarge() = performInitialLoad {
        // LoadInitialCallback can't accept pos + list > totalCount
        it.onResult(listOf("a", "b"), 1, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun initialLoadCallbackPositionNegative() = performInitialLoad {
        // LoadInitialCallback can't accept negative position
        it.onResult(listOf("a", "b", "c"), -1, 2)
    }

    @Test(expected = IllegalArgumentException::class)
    fun initialLoadCallbackEmptyCannotHavePlaceholders() = performInitialLoad {
        // LoadInitialCallback can't accept empty result unless data set is empty
        it.onResult(emptyList(), 0, 2)
    }
}

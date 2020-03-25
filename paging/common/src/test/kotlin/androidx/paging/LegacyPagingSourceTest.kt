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

import androidx.paging.PagingSource.LoadResult.Page
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(JUnit4::class)
class LegacyPagingSourceTest {
    private val fakePagingState = PagingState(
        pages = listOf(
            Page<Int, String>(
                data = listOf("fakeData"),
                prevKey = null,
                nextKey = null
            )
        ),
        anchorPosition = 0,
        config = PagingConfig(
            pageSize = 1,
            prefetchDistance = 1
        ),
        placeholdersStart = 0
    )

    @Test
    fun item() {
        val dataSource = object : ItemKeyedDataSource<Int, String>() {
            override fun loadInitial(
                params: LoadInitialParams<Int>,
                callback: LoadInitialCallback<String>
            ) {
                Assert.fail("loadInitial not expected")
            }

            override fun loadAfter(params: LoadParams<Int>, callback: LoadCallback<String>) {
                Assert.fail("loadAfter not expected")
            }

            override fun loadBefore(params: LoadParams<Int>, callback: LoadCallback<String>) {
                Assert.fail("loadBefore not expected")
            }

            override fun getKey(item: String) = item.hashCode()
        }
        val pagingSource = LegacyPagingSource(dataSource)
        val refreshKey = pagingSource.getRefreshKey(fakePagingState)
        assertEquals("fakeData".hashCode(), refreshKey)

        assertFalse { pagingSource.invalid }
        assertFalse { dataSource.isInvalid }

        pagingSource.invalidate()

        assertTrue { pagingSource.invalid }
        assertTrue { dataSource.isInvalid }
    }

    @Test
    fun page() {
        val dataSource = object : PageKeyedDataSource<Int, String>() {
            override fun loadInitial(
                params: LoadInitialParams<Int>,
                callback: LoadInitialCallback<Int, String>
            ) {
                Assert.fail("loadInitial not expected")
            }

            override fun loadBefore(params: LoadParams<Int>, callback: LoadCallback<Int, String>) {
                Assert.fail("loadBefore not expected")
            }

            override fun loadAfter(params: LoadParams<Int>, callback: LoadCallback<Int, String>) {
                Assert.fail("loadAfter not expected")
            }
        }
        val pagingSource = LegacyPagingSource(dataSource)
        val refreshKey = pagingSource.getRefreshKey(fakePagingState)
        assertEquals(refreshKey, null)

        assertFalse { pagingSource.invalid }
        assertFalse { dataSource.isInvalid }

        pagingSource.invalidate()

        assertTrue { pagingSource.invalid }
        assertTrue { dataSource.isInvalid }
    }

    @Test
    fun positional() {
        val dataSource = createTestPositionalDataSource()
        val pagingSource = LegacyPagingSource(dataSource)

        assertEquals(
            3,
            pagingSource.getRefreshKey(
                PagingState(
                    pages = listOf(
                        Page(
                            data = listOf("fakeData"),
                            prevKey = 3,
                            nextKey = 8
                        )
                    ),
                    anchorPosition = 3,
                    config = PagingConfig(
                        pageSize = 1,
                        prefetchDistance = 1
                    ),
                    placeholdersStart = 0
                )
            )
        )
        assertEquals(
            4,
            pagingSource.getRefreshKey(
                PagingState(
                    pages = listOf(
                        Page(
                            data = listOf("fakeData"),
                            prevKey = 3,
                            nextKey = 8
                        )
                    ),
                    anchorPosition = 4,
                    config = PagingConfig(
                        pageSize = 1,
                        prefetchDistance = 1
                    ),
                    placeholdersStart = 0
                )
            )
        )
    }

    @Test
    fun invalidateFromPagingSource() {
        val dataSource = createTestPositionalDataSource()
        val pagingSource = LegacyPagingSource(dataSource)

        assertFalse { pagingSource.invalid }
        assertFalse { dataSource.isInvalid }

        pagingSource.invalidate()

        assertTrue { pagingSource.invalid }
        assertTrue { dataSource.isInvalid }
    }

    @Test
    fun invalidateFromDataSource() {
        val dataSource = createTestPositionalDataSource()
        val pagingSource = LegacyPagingSource(dataSource)

        assertFalse { pagingSource.invalid }
        assertFalse { dataSource.isInvalid }

        dataSource.invalidate()

        assertTrue { pagingSource.invalid }
        assertTrue { dataSource.isInvalid }
    }

    private fun createTestPositionalDataSource() = object : PositionalDataSource<String>() {
        override fun loadInitial(
            params: LoadInitialParams,
            callback: LoadInitialCallback<String>
        ) {
            Assert.fail("loadInitial not expected")
        }

        override fun loadRange(params: LoadRangeParams, callback: LoadRangeCallback<String>) {
            Assert.fail("loadRange not expected")
        }
    }
}

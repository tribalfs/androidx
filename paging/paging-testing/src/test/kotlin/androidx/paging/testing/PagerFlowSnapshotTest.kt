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

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.cachedIn
import androidx.paging.testing.SnapshotLoader.ScrollBehavior
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class PagerFlowSnapshotTest {

    private val testScope = TestScope(UnconfinedTestDispatcher())

    @Before
    fun init() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @Test
    fun initialRefresh() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)

        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = factory
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {}
            // first page + prefetched page
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7)
            )
        }
    }

    @Test
    fun initialRefresh_withoutPrefetch() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            pagingSourceFactory = factory
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {}

            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4)
            )
        }
    }

    @Test
    fun initialRefresh_withInitialKey() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG,
            initialKey = 10,
            pagingSourceFactory = factory
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {}

            assertThat(snapshot).containsExactlyElementsIn(
                listOf(7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)
            )
        }
    }

    @Test
    fun initialRefresh_withInitialKey_withoutPrefetch() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            initialKey = 10,
            pagingSourceFactory = factory
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {}

            assertThat(snapshot).containsExactlyElementsIn(
                listOf(10, 11, 12, 13, 14)
            )
        }
    }

    @Test
    fun emptyInitialRefresh() {
        val dataFlow = emptyFlow<List<Int>>()
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = factory
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {}

            assertThat(snapshot).containsExactlyElementsIn(
                emptyList<Int>()
            )
        }
    }

    @Test
    fun manualRefresh() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            pagingSourceFactory = factory
        ).flow.cachedIn(testScope.backgroundScope)

        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                refresh()
            }
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4),
            )
        }
    }

    @Test
    fun manualEmptyRefresh() {
        val dataFlow = emptyFlow<List<Int>>()
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            pagingSourceFactory = factory
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                refresh()
            }
            assertThat(snapshot).containsExactlyElementsIn(
                emptyList<Int>()
            )
        }
    }

    @Test
    fun appendWhile() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                appendScrollWhile { item: Int ->
                    item < 14
                }
            }
            assertThat(snapshot).containsExactlyElementsIn(
                // initial load [0-4]
                // prefetched [5-7]
                // appended [8-16]
                // prefetched [17-19]
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
            )
        }
    }

    @Test
    fun appendWhile_withoutPrefetch() {
        val dataFlow = flowOf(List(50) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            pagingSourceFactory = factory,
        ).flow
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                appendScrollWhile { item: Int ->
                    item < 14
                }
            }
            assertThat(snapshot).containsExactlyElementsIn(
                // initial load [0-4]
                // appended [5-16]
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
            )
        }
    }

    @Test
    fun appendWhile_withoutPlaceholders() {
        val dataFlow = flowOf(List(50) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG_NO_PLACEHOLDERS,
            pagingSourceFactory = factory,
        ).flow
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                appendScrollWhile { item: Int ->
                    item != 14
                }
            }
            assertThat(snapshot).containsExactlyElementsIn(
                // initial load [0-4]
                // prefetched [5-7]
                // appended [8-16]
                // prefetched [17-19]
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
            )
        }
    }

    @Test
    fun prependWhile() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = CONFIG,
            initialKey = 20,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                prependScrollWhile { item: Int ->
                    item > 14
                }
            }
            // initial load [20-24]
            // prefetched [17-19], [25-27]
            // prepended [14-16]
            // prefetched [11-13]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27)
            )
        }
    }

    @Test
    fun prependWhile_withoutPrefetch() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            initialKey = 20,
            pagingSourceFactory = factory,
        ).flow
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                prependScrollWhile { item: Int ->
                    item > 14
                }
            }
            assertThat(snapshot).containsExactlyElementsIn(
                // initial load [20-24]
                // prepended [14-19]
                listOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
            )
        }
    }

    @Test
    fun prependWhile_withoutPlaceholders() {
        val dataFlow = flowOf(List(50) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG_NO_PLACEHOLDERS,
            initialKey = 30,
            pagingSourceFactory = factory,
        ).flow
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                prependScrollWhile { item: Int ->
                    item != 22
                }
            }
            assertThat(snapshot).containsExactlyElementsIn(
                // initial load [30-34]
                // prefetched [27-29], [35-37]
                // prepended [21-26]
                // prefetched [18-20]
                listOf(
                    18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 36, 37
                )
            )
        }
    }

    @Test
    fun appendWhile_withInitialKey() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG,
            initialKey = 10,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                appendScrollWhile { item: Int ->
                    item < 18
                }
            }
            // initial load [10-14]
            // prefetched [7-9], [15-17]
            // appended [18-20]
            // prefetched [21-23]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)
            )
        }
    }

    @Test
    fun appendWhile_withInitialKey_withoutPlaceholders() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG_NO_PLACEHOLDERS,
            initialKey = 10,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                appendScrollWhile { item: Int ->
                    item != 19
                }
            }
            // initial load [10-14]
            // prefetched [7-9], [15-17]
            // appended [18-20]
            // prefetched [21-23]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23)
            )
        }
    }

    @Test
    fun appendWhile_withInitialKey_withoutPrefetch() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            initialKey = 10,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                appendScrollWhile { item: Int ->
                    item < 18
                }
            }
            // initial load [10-14]
            // appended [15-20]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20)
            )
        }
    }

    @Test
    fun prependWhile_withoutInitialKey() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                prependScrollWhile { item: Int ->
                    item > -3
                }
            }
            // initial load [0-4]
            // prefetched [5-7]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7)
            )
        }
    }

    @Test
    fun consecutiveAppendWhile() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = factory,
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot1 = pager.asSnapshot(this) {
                appendScrollWhile { item: Int ->
                    item < 7
                }
            }

            val snapshot2 = pager.asSnapshot(this) {
                appendScrollWhile { item: Int ->
                    item < 22
                }
            }

            // includes initial load, 1st page, 2nd page (from prefetch)
            assertThat(snapshot1).containsExactlyElementsIn(
                List(11) { it }
            )

            // includes extra page from prefetch
            assertThat(snapshot2).containsExactlyElementsIn(
                List(26) { it }
            )
        }
    }

    @Test
    fun consecutivePrependWhile() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            initialKey = 20,
            pagingSourceFactory = factory,
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot1 = pager.asSnapshot(this) {
                prependScrollWhile { item: Int ->
                    item > 17
                }
            }
            assertThat(snapshot1).containsExactlyElementsIn(
                listOf(17, 18, 19, 20, 21, 22, 23, 24)
            )
            val snapshot2 = pager.asSnapshot(this) {
                prependScrollWhile { item: Int ->
                    item > 11
                }
            }
            assertThat(snapshot2).containsExactlyElementsIn(
                listOf(11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24)
            )
        }
    }

    @Test
    fun appendWhile_outOfBounds_returnsCurrentlyLoadedItems() {
        val dataFlow = flowOf(List(10) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                appendScrollWhile { item: Int ->
                    // condition scrolls till end of data since we only have 10 items
                    item < 18
                }
            }

            // returns the items loaded before index becomes out of bounds
            assertThat(snapshot).containsExactlyElementsIn(
                List(10) { it }
            )
        }
    }

    @Test
    fun prependWhile_outOfBounds_returnsCurrentlyLoadedItems() {
        val dataFlow = flowOf(List(20) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG,
            initialKey = 10,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                prependScrollWhile { item: Int ->
                    // condition scrolls till index = 0
                    item > -3
                }
            }
            // returns the items loaded before index becomes out of bounds
            assertThat(snapshot).containsExactlyElementsIn(
                // initial load [10-14]
                // prefetched [7-9], [15-17]
                // prepended [0-6]
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17)
            )
        }
    }

    @Test
    fun refreshAndAppendWhile() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = factory,
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                refresh() // triggers second gen
                appendScrollWhile { item: Int ->
                    item < 10
                }
            }
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
            )
        }
    }

    @Test
    fun refreshAndPrependWhile() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG,
            initialKey = 20,
            pagingSourceFactory = factory,
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                // this prependScrollWhile does not cause paging to load more items
                // but it helps this test register a non-null anchorPosition so the upcoming
                // refresh doesn't start at index 0
                prependScrollWhile { item -> item > 20 }
                // triggers second gen
                refresh()
                prependScrollWhile { item: Int ->
                    item > 12
                }
            }
            // second gen initial load, anchorPos = 20, refreshKey = 18, loaded
            // initial load [18-22]
            // prefetched [15-17], [23-25]
            // prepended [12-14]
            // prefetched [9-11]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25)
            )
        }
    }

    @Test
    fun appendWhileAndRefresh() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = factory,
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                appendScrollWhile { item: Int ->
                    item < 10
                }
                refresh()
            }
            assertThat(snapshot).containsExactlyElementsIn(
                // second gen initial load, anchorPos = 10, refreshKey = 8
                // initial load [8-12]
                // prefetched [5-7], [13-15]
                listOf(5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15)
            )
        }
    }

    @Test
    fun prependWhileAndRefresh() {
        val dataFlow = flowOf(List(30) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope)
        val pager = Pager(
            config = CONFIG,
            initialKey = 15,
            pagingSourceFactory = factory,
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                prependScrollWhile { item: Int ->
                    item > 8
                }
                refresh()
            }
            assertThat(snapshot).containsExactlyElementsIn(
                // second gen initial load, anchorPos = 8, refreshKey = 6
                // initial load [6-10]
                // prefetched [3-5], [11-13]
                listOf(3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13)
            )
        }
    }

    @Test
    fun consecutiveGenerations_fromSharedFlow() {
        val dataFlow = MutableSharedFlow<List<Int>>()
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot1 = pager.asSnapshot(this) { }
            assertThat(snapshot1).containsExactlyElementsIn(
                emptyList<Int>()
            )

            val snapshot2 = pager.asSnapshot(this) {
                dataFlow.emit(List(30) { it })
            }
            assertThat(snapshot2).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4)
            )

            val snapshot3 = pager.asSnapshot(this) {
                dataFlow.emit(List(30) { it + 30 })
            }
            assertThat(snapshot3).containsExactlyElementsIn(
                listOf(30, 31, 32, 33, 34)
            )
        }
    }

    @Test
    fun consecutiveGenerations_fromFlow() {
        val dataFlow = flow {
            // first gen
            emit(emptyList())
            delay(500)
            // second gen
            emit(List(30) { it })
            delay(500)
            // third gen
            emit(List(30) { it + 30 })
        }
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot1 = pager.asSnapshot(this) { }
            assertThat(snapshot1).containsExactlyElementsIn(
                emptyList<Int>()
            )

            val snapshot2 = pager.asSnapshot(this) {
                delay(500)
            }
            assertThat(snapshot2).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4)
            )

            val snapshot3 = pager.asSnapshot(this) {
                delay(500)
            }
            assertThat(snapshot3).containsExactlyElementsIn(
                listOf(30, 31, 32, 33, 34)
            )
        }
    }

    @Test
    fun consecutiveGenerations_withInitialKey_nullRefreshKey() {
        val dataFlow = flow {
            // first gen
            emit(List(20) { it })
            delay(500)
            // second gen
            emit(List(20) { it })
        }
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            initialKey = 10,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot1 = pager.asSnapshot(this) { }
            assertThat(snapshot1).containsExactlyElementsIn(
                listOf(10, 11, 12, 13, 14)
            )

            val snapshot2 = pager.asSnapshot(this) {
                // wait for second gen to complete
                delay(500)
            }
            assertThat(snapshot2).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4)
            )
        }
    }

    @Test
    fun consecutiveGenerations_withInitialKey_nonNullRefreshKey() {
        val dataFlow = flow {
            // first gen
            emit(List(20) { it })
            delay(500)
            // second gen
            emit(List(20) { it })
        }
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            initialKey = 10,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot1 = pager.asSnapshot(this) {
                // we scroll to register a non-null anchorPos
                appendScrollWhile { item: Int ->
                    item < 15
                }
            }
            assertThat(snapshot1).containsExactlyElementsIn(
                listOf(10, 11, 12, 13, 14, 15, 16, 17)
            )

            val snapshot2 = pager.asSnapshot(this) {
                delay(500)
            }
            // anchorPos = 15, refreshKey = 13
            assertThat(snapshot2).containsExactlyElementsIn(
                listOf(13, 14, 15, 16, 17)
            )
        }
    }

    @Test
    fun prependToAwait() {
        val dataFlow = flowOf(List(100) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = CONFIG,
            initialKey = 50,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                scrollTo(42, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [50-54]
            // prefetched [47-49], [55-57]
            // prepended [41-46]
            // prefetched [38-40]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(
                    38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57
                )
            )
        }
    }

    @Test
    fun consecutivePrependToAwait() {
        val dataFlow = flowOf(List(100) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = CONFIG,
            initialKey = 50,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                scrollTo(42, ScrollBehavior.WaitForPlaceholdersToLoad)
                scrollTo(38, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [50-54]
            // prefetched [47-49], [55-57]
            // prepended [38-46]
            // prefetched [35-37]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(
                    35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50,
                    51, 52, 53, 54, 55, 56, 57
                )
            )
        }
    }

    @Test
    fun consecutivePrependToAwait_multiSnapshots() {
        val dataFlow = flowOf(List(100) { it })
        val pager = Pager(
            config = CONFIG,
            initialKey = 50,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                scrollTo(42, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [50-54]
            // prefetched [47-49], [55-57]
            // prepended [41-46]
            // prefetched [38-40]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(
                    38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57
                )
            )

            val snapshot2 = pager.flow.asSnapshot(this) {
                scrollTo(38, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // prefetched [35-37]
            assertThat(snapshot2).containsExactlyElementsIn(
                listOf(
                    35, 36, 37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50,
                    51, 52, 53, 54, 55, 56, 57
                )
            )
        }
    }

    @Test
    fun prependToAwait_withoutPrefetch() {
        val dataFlow = flowOf(List(100) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            initialKey = 50,
            pagingSourceFactory = factory,
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                scrollTo(42, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [50-54]
            // prepended [41-49]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54)
            )
        }
    }

    @Test
    fun prependToAwait_withoutPlaceholders() {
        val dataFlow = flowOf(List(100) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = CONFIG_NO_PLACEHOLDERS,
            initialKey = 50,
            pagingSourceFactory = factory,
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                // Without placeholders, first loaded page always starts at index[0]
                scrollTo(0, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [50-54]
            // prefetched [47-49], [55-57]
            // scrollTo prepended [44-46]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57)
            )
        }
    }

    @Test
    fun consecutivePrependToAwait_withoutPlaceholders() {
        val dataFlow = flowOf(List(100) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = CONFIG_NO_PLACEHOLDERS,
            initialKey = 50,
            pagingSourceFactory = factory,
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                // Without placeholders, first loaded page always starts at index[0]
                scrollTo(0, ScrollBehavior.WaitForPlaceholdersToLoad)
                // Without placeholders, first loaded page always starts at index[0]
                scrollTo(0, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [50-54]
            // prefetched [47-49], [55-57]
            // first scrollTo(0) prepended [44-46]
            // second scrollTo(0) prepended [41-43]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57)
            )
        }
    }

    @Test
    fun consecutivePrependToAwait_withoutPlaceholders_multiSnapshot() {
        val dataFlow = flowOf(List(100) { it })
        val pager = Pager(
            config = CONFIG_NO_PLACEHOLDERS,
            initialKey = 50,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                // Without placeholders, first loaded page always starts at index[0]
                scrollTo(0, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [50-54]
            // prefetched [47-49], [55-57]
            // scrollTo prepended [44-46]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57)
            )

            val snapshot2 = pager.asSnapshot(this) {
                // Without placeholders, first loaded page always starts at index[0]
                scrollTo(0, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // scrollTo prepended [41-43]
            assertThat(snapshot2).containsExactlyElementsIn(
                listOf(41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57)
            )
        }
    }

    @Test
    fun prependToAwait_withoutPlaceholders_noPrefetchTriggered() {
        val dataFlow = flowOf(List(100) { it })
        val factory = dataFlow.asPagingSourceFactory(testScope.backgroundScope)
        val pager = Pager(
            config = PagingConfig(
                pageSize = 4,
                initialLoadSize = 8,
                enablePlaceholders = false,
                // a small prefetchDistance to prevent prefetch until we scroll to boundary
                prefetchDistance = 1
            ),
            initialKey = 50,
            pagingSourceFactory = factory,
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                // Without placeholders, first loaded page always starts at index[0]
                scrollTo(0)
            }
            // initial load [50-57]
            // no prefetch after initial load because it didn't hit prefetch distance
            // scrollTo prepended [46-49]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57)
            )
        }
    }

    @Test
    fun appendToAwait() {
        val dataFlow = flowOf(List(100) { it })
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                scrollTo(12, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [0-4]
            // prefetched [5-7]
            // appended [8-13]
            // prefetched [14-16]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
            )
        }
    }

    @Test
    fun consecutiveAppendToAwait() {
        val dataFlow = flowOf(List(100) { it })
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                scrollTo(12, ScrollBehavior.WaitForPlaceholdersToLoad)
                scrollTo(18, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [0-4]
            // prefetched [5-7]
            // appended [8-19]
            // prefetched [20-22]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22)
            )
        }
    }

    @Test
    fun consecutiveAppendToAwait_multiSnapshots() {
        val dataFlow = flowOf(List(100) { it })
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                scrollTo(12, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [0-4]
            // prefetched [5-7]
            // appended [8-13]
            // prefetched [14-16]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16)
            )

            val snapshot2 = pager.flow.asSnapshot(this) {
                scrollTo(18, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // appended [17-19]
            // prefetched [20-22]
            assertThat(snapshot2).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22
                )
            )
        }
    }

    @Test
    fun appendToAwait_indexOutOfBounds() {
        val dataFlow = flowOf(List(15) { it })
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                // index out of bounds
                scrollTo(50, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [0-4]
            // prefetched [5-7]
            // scrollTo appended [8-10]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14)
            )
        }
    }

    @Test
    fun appendToAwait_accessBoundaryIndex() {
        val dataFlow = flowOf(List(100) { it })
        val pager = Pager(
            config = CONFIG,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                // after initial Load and prefetch, max loaded index is 7
                scrollTo(7, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // ensure that SnapshotLoader waited for last prefetch before returning
            // initial load [0-4]
            // prefetched [5-7]
            // scrollTo appended [8-10]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            )
        }
    }

    @Test
    fun appendToAwait_withoutPrefetch() {
        val dataFlow = flowOf(List(100) { it })
        val pager = Pager(
            config = CONFIG_NO_PREFETCH,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        )
        testScope.runTest {
            val snapshot = pager.flow.asSnapshot(this) {
                scrollTo(10, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [0-4]
            // appended [5-10]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
            )
        }
    }

    @Test
    fun appendToAwait_withoutPlaceholders() {
        val dataFlow = flowOf(List(100) { it })
        val pager = Pager(
            config = CONFIG_NO_PLACEHOLDERS,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                // Without placeholders, SnapshotLoader will only scroll to max loaded
                // index (differ.size - 1) regardless of what the requested index is
                scrollTo(15, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [0-4]
            // prefetched [5-7]
            // scrollTo appended [8-16]
            // prefetched [17-19]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
            )
        }
    }

    @Test
    fun consecutiveAppendToAwait_withoutPlaceholders() {
        val dataFlow = flowOf(List(100) { it })
        val pager = Pager(
            config = CONFIG_NO_PLACEHOLDERS,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                scrollTo(12, ScrollBehavior.WaitForPlaceholdersToLoad)
                scrollTo(17, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // initial load [0-4]
            // prefetched [5-7]
            // first scrollTo appended [8-13]
            // second scrollTo appended [14-19]
            // prefetched [19-22]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20,
                21, 22)
            )
        }
    }

    @Test
    fun appendToAwait_withoutPlaceholders_indexOutOfBounds() {
        val dataFlow = flowOf(List(20) { it })
        val pager = Pager(
            config = CONFIG_NO_PLACEHOLDERS,
            pagingSourceFactory = dataFlow.asPagingSourceFactory(testScope.backgroundScope),
        ).flow.cachedIn(testScope.backgroundScope)
        testScope.runTest {
            val snapshot = pager.asSnapshot(this) {
                scrollTo(50, ScrollBehavior.WaitForPlaceholdersToLoad)
            }
            // ensure index is still capped to max index available
            // initial load [0-4]
            // prefetched [5-7]
            // scrollTo appended [8-19]
            assertThat(snapshot).containsExactlyElementsIn(
                listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19)
            )
        }
    }

    val CONFIG = PagingConfig(
        pageSize = 3,
        initialLoadSize = 5,
    )

    val CONFIG_NO_PLACEHOLDERS = PagingConfig(
        pageSize = 3,
        initialLoadSize = 5,
        enablePlaceholders = false,
        prefetchDistance = 3
    )

    val CONFIG_NO_PREFETCH = PagingConfig(
        pageSize = 3,
        initialLoadSize = 5,
        prefetchDistance = 0
    )
}
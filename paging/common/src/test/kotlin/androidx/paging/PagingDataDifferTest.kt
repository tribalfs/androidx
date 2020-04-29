/*
 * Copyright 2020 The Android Open Source Project
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

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runBlockingTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.coroutines.ContinuationInterceptor
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(JUnit4::class)
class PagingDataDifferTest {
    private val testScope = TestCoroutineScope()

    @Before
    fun setup() {
        Dispatchers.setMain(
            testScope.coroutineContext[ContinuationInterceptor] as CoroutineDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun collectFrom_static() = testScope.runBlockingTest {
        pauseDispatcher {
            val differ = SimpleDiffer()
            val receiver = object : UiReceiver {
                val hintsAdded = mutableListOf<ViewportHint>()
                var didRetry = false
                var didRefresh = false

                override fun addHint(hint: ViewportHint) {
                    hintsAdded.add(hint)
                }

                override fun retry() {
                    didRetry = true
                }

                override fun refresh() {
                    didRefresh = true
                }
            }

            val job1 = launch {
                differ.collectFrom(infinitelySuspendingPagingData(receiver), dummyPresenterCallback)
            }
            advanceUntilIdle()
            job1.cancel()

            val job2 = launch {
                differ.collectFrom(PagingData.empty(), dummyPresenterCallback)
            }
            advanceUntilIdle()
            job2.cancel()

            // Static replacement should also replace the UiReceiver from previous generation.
            differ.retry()
            differ.refresh()
            advanceUntilIdle()

            assertFalse { receiver.didRetry }
            assertFalse { receiver.didRefresh }
        }
    }

    @Test
    fun collectFrom_twice() = testScope.runBlockingTest {
        val differ = SimpleDiffer()

        launch { differ.collectFrom(infinitelySuspendingPagingData(), dummyPresenterCallback) }
            .cancel()
        launch { differ.collectFrom(infinitelySuspendingPagingData(), dummyPresenterCallback) }
            .cancel()
    }

    @Test
    fun collectFrom_twiceConcurrently() = testScope.runBlockingTest {
        val differ = SimpleDiffer()

        val job = launch {
            differ.collectFrom(infinitelySuspendingPagingData(), dummyPresenterCallback)
        }
        assertFailsWith<IllegalStateException> {
            differ.collectFrom(infinitelySuspendingPagingData(), dummyPresenterCallback)
        }

        job.cancel()
    }

    @Test
    fun retry() = testScope.runBlockingTest {
        val differ = SimpleDiffer()
        val receiver = UiReceiverFake()

        val job = launch {
            differ.collectFrom(infinitelySuspendingPagingData(receiver), dummyPresenterCallback)
        }

        differ.retry()

        assertEquals(1, receiver.retryEvents.size)

        job.cancel()
    }

    @Test
    fun refresh() = testScope.runBlockingTest {
        val differ = SimpleDiffer()
        val receiver = UiReceiverFake()

        val job = launch {
            differ.collectFrom(infinitelySuspendingPagingData(receiver), dummyPresenterCallback)
        }

        differ.refresh()

        assertEquals(1, receiver.refreshEvents.size)

        job.cancel()
    }
}

private fun infinitelySuspendingPagingData(receiver: UiReceiver = dummyReceiver) = PagingData<Int>(
    flow { emit(suspendCancellableCoroutine { }) },
    receiver
)

private class UiReceiverFake : UiReceiver {
    val hints = mutableListOf<ViewportHint>()
    val retryEvents = mutableListOf<Unit>()
    val refreshEvents = mutableListOf<Unit>()

    override fun addHint(hint: ViewportHint) {
        hints.add(hint)
    }

    override fun retry() {
        retryEvents.add(Unit)
    }

    override fun refresh() {
        refreshEvents.add(Unit)
    }
}

private class SimpleDiffer : PagingDataDiffer<Int>() {
    override suspend fun performDiff(
        previousList: NullPaddedList<Int>,
        newList: NullPaddedList<Int>,
        newLoadStates: Map<LoadType, LoadState>,
        lastAccessedIndex: Int
    ): Int? = null
}

private val dummyReceiver = object : UiReceiver {
    override fun addHint(hint: ViewportHint) {}

    override fun retry() {}

    override fun refresh() {}
}

private val dummyPresenterCallback = object : PresenterCallback {
    override fun onInserted(position: Int, count: Int) {}

    override fun onChanged(position: Int, count: Int) {}

    override fun onRemoved(position: Int, count: Int) {}

    override fun onStateUpdate(loadType: LoadType, loadState: LoadState) {}
}

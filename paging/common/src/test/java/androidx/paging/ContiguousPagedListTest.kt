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

import androidx.arch.core.util.Function
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.Mockito.mock
import org.mockito.Mockito.reset
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.verifyZeroInteractions
import java.util.concurrent.Executor

@RunWith(Parameterized::class)
class ContiguousPagedListTest(private val mCounted: Boolean) {
    private val mMainThread = TestExecutor()
    private val mBackgroundThread = TestExecutor()

    private class Item(position: Int) {
        val name: String = "Item $position"

        override fun toString(): String {
            return name
        }
    }

    private inner class TestSource(val listData: List<Item> = ITEMS)
            : ContiguousDataSource<Int, Item>() {
        override fun dispatchLoadInitial(
            key: Int?,
            initialLoadSize: Int,
            pageSize: Int,
            enablePlaceholders: Boolean,
            mainThreadExecutor: Executor,
            receiver: PageResult.Receiver<Item>
        ) {
            val convertPosition = key ?: 0
            val position = Math.max(0, (convertPosition - initialLoadSize / 2))
            val data = getClampedRange(position, position + initialLoadSize)
            val trailingUnloadedCount = listData.size - position - data.size

            if (enablePlaceholders && mCounted) {
                receiver.onPageResult(PageResult.INIT,
                        PageResult(data, position, trailingUnloadedCount, 0))
            } else {
                // still must pass offset, even if not counted
                receiver.onPageResult(PageResult.INIT,
                        PageResult(data, position))
            }
        }

        override fun dispatchLoadAfter(
            currentEndIndex: Int,
            currentEndItem: Item,
            pageSize: Int,
            mainThreadExecutor: Executor,
            receiver: PageResult.Receiver<Item>
        ) {
            val startIndex = currentEndIndex + 1
            val data = getClampedRange(startIndex, startIndex + pageSize)

            mainThreadExecutor.execute {
                receiver.onPageResult(PageResult.APPEND, PageResult(data, 0, 0, 0))
            }
        }

        override fun dispatchLoadBefore(
            currentBeginIndex: Int,
            currentBeginItem: Item,
            pageSize: Int,
            mainThreadExecutor: Executor,
            receiver: PageResult.Receiver<Item>
        ) {

            val startIndex = currentBeginIndex - 1
            val data = getClampedRange(startIndex - pageSize + 1, startIndex + 1)

            mainThreadExecutor.execute {
                receiver.onPageResult(PageResult.PREPEND, PageResult(data, 0, 0, 0))
            }
        }

        override fun getKey(position: Int, item: Item?): Int {
            return 0
        }

        private fun getClampedRange(startInc: Int, endExc: Int): List<Item> {
            return listData.subList(Math.max(0, startInc), Math.min(listData.size, endExc))
        }

        override fun <ToValue : Any?> mapByPage(function: Function<List<Item>, List<ToValue>>):
                DataSource<Int, ToValue> {
            throw UnsupportedOperationException()
        }

        override fun <ToValue : Any?> map(function: Function<Item, ToValue>):
                DataSource<Int, ToValue> {
            throw UnsupportedOperationException()
        }
    }

    private fun verifyRange(start: Int, count: Int, actual: PagedStorage<Item>) {
        if (mCounted) {
            // assert nulls + content
            val expected = arrayOfNulls<Item>(ITEMS.size)
            System.arraycopy(ITEMS.toTypedArray(), start, expected, start, count)
            assertArrayEquals(expected, actual.toTypedArray())

            val expectedTrailing = ITEMS.size - start - count
            assertEquals(ITEMS.size, actual.size)
            assertEquals((ITEMS.size - start - expectedTrailing),
                    actual.storageCount)
            assertEquals(start, actual.leadingNullCount)
            assertEquals(expectedTrailing, actual.trailingNullCount)
        } else {
            assertEquals(ITEMS.subList(start, start + count), actual)

            assertEquals(count, actual.size)
            assertEquals(actual.size, actual.storageCount)
            assertEquals(0, actual.leadingNullCount)
            assertEquals(0, actual.trailingNullCount)
        }
        assertEquals(count, actual.loadedCount)
    }

    private fun verifyRange(start: Int, count: Int, actual: PagedList<Item>) {
        verifyRange(start, count, actual.mStorage)
        assertEquals(count, actual.loadedCount)
    }

    private fun createCountedPagedList(
        initialPosition: Int,
        pageSize: Int = 20,
        initLoadSize: Int = 40,
        prefetchDistance: Int = 20,
        listData: List<Item> = ITEMS,
        boundaryCallback: PagedList.BoundaryCallback<Item>? = null,
        lastLoad: Int = ContiguousPagedList.LAST_LOAD_UNSPECIFIED,
        maxSize: Int = PagedList.Config.MAX_SIZE_UNBOUNDED
    ): ContiguousPagedList<Int, Item> {
        return ContiguousPagedList(
                TestSource(listData), mMainThread, mBackgroundThread, boundaryCallback,
                PagedList.Config.Builder()
                        .setPageSize(pageSize)
                        .setInitialLoadSizeHint(initLoadSize)
                        .setPrefetchDistance(prefetchDistance)
                        .setMaxSize(maxSize)
                        .build(),
                initialPosition,
                lastLoad)
    }

    @Test
    fun construct() {
        val pagedList = createCountedPagedList(0)
        verifyRange(0, 40, pagedList)
    }

    @Test
    fun getDataSource() {
        val pagedList = createCountedPagedList(0)
        assertTrue(pagedList.dataSource is TestSource)

        // snapshot keeps same DataSource
        assertSame(pagedList.dataSource,
                (pagedList.snapshot() as SnapshotPagedList<Item>).dataSource)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun loadAroundNegative() {
        val pagedList = createCountedPagedList(0)
        pagedList.loadAround(-1)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun loadAroundTooLarge() {
        val pagedList = createCountedPagedList(0)
        pagedList.loadAround(pagedList.size)
    }

    private fun verifyCallback(
        callback: PagedList.Callback,
        countedPosition: Int,
        uncountedPosition: Int
    ) {
        if (mCounted) {
            verify(callback).onChanged(countedPosition, 20)
        } else {
            verify(callback).onInserted(uncountedPosition, 20)
        }
    }

    private fun verifyCallback(callback: PagedList.Callback, position: Int) {
        verifyCallback(callback, position, position)
    }

    private fun verifyDropCallback(
        callback: PagedList.Callback,
        countedPosition: Int,
        uncountedPosition: Int
    ) {
        if (mCounted) {
            verify(callback).onChanged(countedPosition, 20)
        } else {
            verify(callback).onRemoved(uncountedPosition, 20)
        }
    }

    private fun verifyDropCallback(callback: PagedList.Callback, position: Int) {
        verifyDropCallback(callback, position, position)
    }

    @Test
    fun append() {
        val pagedList = createCountedPagedList(0)
        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(null, callback)
        verifyRange(0, 40, pagedList)
        verifyZeroInteractions(callback)

        pagedList.loadAround(35)
        drain()

        verifyRange(0, 60, pagedList)
        verifyCallback(callback, 40)
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun prepend() {
        val pagedList = createCountedPagedList(80)
        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(null, callback)
        verifyRange(60, 40, pagedList)
        verifyZeroInteractions(callback)

        pagedList.loadAround(if (mCounted) 65 else 5)
        drain()

        verifyRange(40, 60, pagedList)
        verifyCallback(callback, 40, 0)
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun outwards() {
        val pagedList = createCountedPagedList(50)
        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(null, callback)
        verifyRange(30, 40, pagedList)
        verifyZeroInteractions(callback)

        pagedList.loadAround(if (mCounted) 65 else 35)
        drain()

        verifyRange(30, 60, pagedList)
        verifyCallback(callback, 70, 40)
        verifyNoMoreInteractions(callback)

        pagedList.loadAround(if (mCounted) 35 else 5)
        drain()

        verifyRange(10, 80, pagedList)
        verifyCallback(callback, 10, 0)
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun prefetchRequestedPrepend() {
        assertEquals(10, ContiguousPagedList.getPrependItemsRequested(10, 0, 0))
        assertEquals(15, ContiguousPagedList.getPrependItemsRequested(10, 0, 5))
        assertEquals(0, ContiguousPagedList.getPrependItemsRequested(1, 41, 40))
        assertEquals(1, ContiguousPagedList.getPrependItemsRequested(1, 40, 40))
    }

    @Test
    fun prefetchRequestedAppend() {
        assertEquals(10, ContiguousPagedList.getAppendItemsRequested(10, 9, 10))
        assertEquals(15, ContiguousPagedList.getAppendItemsRequested(10, 9, 5))
        assertEquals(0, ContiguousPagedList.getAppendItemsRequested(1, 8, 10))
        assertEquals(1, ContiguousPagedList.getAppendItemsRequested(1, 9, 10))
    }

    @Test
    fun prefetchFront() {
        val pagedList = createCountedPagedList(
                initialPosition = 50,
                pageSize = 20,
                initLoadSize = 20,
                prefetchDistance = 1)
        verifyRange(40, 20, pagedList)

        // access adjacent to front, shouldn't trigger prefetch
        pagedList.loadAround(if (mCounted) 41 else 1)
        drain()
        verifyRange(40, 20, pagedList)

        // access front item, should trigger prefetch
        pagedList.loadAround(if (mCounted) 40 else 0)
        drain()
        verifyRange(20, 40, pagedList)
    }

    @Test
    fun prefetchEnd() {
        val pagedList = createCountedPagedList(
                initialPosition = 50,
                pageSize = 20,
                initLoadSize = 20,
                prefetchDistance = 1)
        verifyRange(40, 20, pagedList)

        // access adjacent from end, shouldn't trigger prefetch
        pagedList.loadAround(if (mCounted) 58 else 18)
        drain()
        verifyRange(40, 20, pagedList)

        // access end item, should trigger prefetch
        pagedList.loadAround(if (mCounted) 59 else 19)
        drain()
        verifyRange(40, 40, pagedList)
    }

    @Test
    fun pageDropEnd() {
        val pagedList = createCountedPagedList(
                initialPosition = 0,
                pageSize = 20,
                initLoadSize = 20,
                prefetchDistance = 1,
                maxSize = 70)
        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(null, callback)
        verifyRange(0, 20, pagedList)
        verifyZeroInteractions(callback)

        // load 2nd page
        pagedList.loadAround(19)
        drain()
        verifyRange(0, 40, pagedList)
        verifyCallback(callback, 20)
        verifyNoMoreInteractions(callback)

        // load 3rd page
        pagedList.loadAround(39)
        drain()
        verifyRange(0, 60, pagedList)
        verifyCallback(callback, 40)
        verifyNoMoreInteractions(callback)

        // load 4th page, drop 1st
        pagedList.loadAround(59)
        drain()
        verifyRange(20, 60, pagedList)
        verifyCallback(callback, 60)
        verifyDropCallback(callback, 0)
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun pageDropFront() {
        val pagedList = createCountedPagedList(
                initialPosition = 90,
                pageSize = 20,
                initLoadSize = 20,
                prefetchDistance = 1,
                maxSize = 70)
        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(null, callback)
        verifyRange(80, 20, pagedList)
        verifyZeroInteractions(callback)

        // load 4th page
        pagedList.loadAround(if (mCounted) 80 else 0)
        drain()
        verifyRange(60, 40, pagedList)
        verifyCallback(callback, 60, 0)
        verifyNoMoreInteractions(callback)
        reset(callback)

        // load 3rd page
        pagedList.loadAround(if (mCounted) 60 else 0)
        drain()
        verifyRange(40, 60, pagedList)
        verifyCallback(callback, 40, 0)
        verifyNoMoreInteractions(callback)
        reset(callback)

        // load 2nd page, drop 5th
        pagedList.loadAround(if (mCounted) 40 else 0)
        drain()
        verifyRange(20, 60, pagedList)
        verifyCallback(callback, 20, 0)
        verifyDropCallback(callback, 80, 60)
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun pageDropCancelPrepend() {
        // verify that, based on most recent load position, a prepend can be dropped as it arrives
        val pagedList = createCountedPagedList(
                initialPosition = 2,
                pageSize = 1,
                initLoadSize = 1,
                prefetchDistance = 1,
                maxSize = 3)

        // load 3 pages - 2nd, 3rd, 4th
        pagedList.loadAround(if (mCounted) 2 else 0)
        drain()

        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(null, callback)

        // start a load at the beginning...
        pagedList.loadAround(if (mCounted) 1 else 0)

        mBackgroundThread.executeAll()

        // but before page received, access near end of list
        pagedList.loadAround(if (mCounted) 3 else 2)
        verifyZeroInteractions(callback)
        mMainThread.executeAll()
        // and the load at the end is dropped without signaling callback
        verifyNoMoreInteractions(callback)
        verifyRange(1, 3, pagedList)

        drain()
        if (mCounted) {
            verify(callback).onChanged(4, 1)
            verify(callback).onChanged(1, 1)
        } else {
            verify(callback).onInserted(3, 1)
            verify(callback).onRemoved(0, 1)
        }
        verifyRange(2, 3, pagedList)
    }

    @Test
    fun pageDropCancelAppend() {
        // verify that, based on most recent load position, an append can be dropped as it arrives
        val pagedList = createCountedPagedList(
                initialPosition = 2,
                pageSize = 1,
                initLoadSize = 1,
                prefetchDistance = 1,
                maxSize = 3)

        // load 3 pages - 2nd, 3rd, 4th
        pagedList.loadAround(if (mCounted) 2 else 0)
        drain()

        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(null, callback)

        // start a load at the end...
        pagedList.loadAround(if (mCounted) 3 else 2)

        mBackgroundThread.executeAll()

        // but before page received, access near front of list
        pagedList.loadAround(if (mCounted) 1 else 0)
        verifyZeroInteractions(callback)
        mMainThread.executeAll()
        // and the load at the end is dropped without signaling callback
        verifyNoMoreInteractions(callback)
        verifyRange(1, 3, pagedList)

        drain()
        if (mCounted) {
            verify(callback).onChanged(0, 1)
            verify(callback).onChanged(3, 1)
        } else {
            verify(callback).onInserted(0, 1)
            verify(callback).onRemoved(3, 1)
        }
        verifyRange(0, 3, pagedList)
    }

    @Test
    fun distantPrefetch() {
        val pagedList = createCountedPagedList(0,
                initLoadSize = 10, pageSize = 10, prefetchDistance = 30)
        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(null, callback)
        verifyRange(0, 10, pagedList)
        verifyZeroInteractions(callback)

        pagedList.loadAround(5)
        drain()

        verifyRange(0, 40, pagedList)

        pagedList.loadAround(6)
        drain()

        // although our prefetch window moves forward, no new load triggered
        verifyRange(0, 40, pagedList)
    }

    @Test
    fun appendCallbackAddedLate() {
        val pagedList = createCountedPagedList(0)
        verifyRange(0, 40, pagedList)

        pagedList.loadAround(35)
        drain()
        verifyRange(0, 60, pagedList)

        // snapshot at 60 items
        val snapshot = pagedList.snapshot() as PagedList<Item>
        verifyRange(0, 60, snapshot)

        // load more items...
        pagedList.loadAround(55)
        drain()
        verifyRange(0, 80, pagedList)
        verifyRange(0, 60, snapshot)

        // and verify the snapshot hasn't received them
        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(snapshot, callback)
        verifyCallback(callback, 60)
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun prependCallbackAddedLate() {
        val pagedList = createCountedPagedList(80)
        verifyRange(60, 40, pagedList)

        pagedList.loadAround(if (mCounted) 65 else 5)
        drain()
        verifyRange(40, 60, pagedList)

        // snapshot at 60 items
        val snapshot = pagedList.snapshot() as PagedList<Item>
        verifyRange(40, 60, snapshot)

        pagedList.loadAround(if (mCounted) 45 else 5)
        drain()
        verifyRange(20, 80, pagedList)
        verifyRange(40, 60, snapshot)

        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(snapshot, callback)
        verifyCallback(callback, 40, 0)
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun initialLoad_lastLoad() {
        val pagedList = createCountedPagedList(
                initialPosition = 0,
                initLoadSize = 20,
                lastLoad = 4)
        // last load is param passed
        assertEquals(4, pagedList.mLastLoad)
        verifyRange(0, 20, pagedList)
    }

    @Test
    fun initialLoad_lastLoadComputed() {
        val pagedList = createCountedPagedList(
                initialPosition = 0,
                initLoadSize = 20,
                lastLoad = ContiguousPagedList.LAST_LOAD_UNSPECIFIED)
        // last load is middle of initial load
        assertEquals(10, pagedList.mLastLoad)
        verifyRange(0, 20, pagedList)
    }

    @Test
    fun initialLoadAsync() {
        // Note: ignores Parameterized param
        val asyncDataSource = AsyncListDataSource(ITEMS)
        val dataSource = asyncDataSource.wrapAsContiguousWithoutPlaceholders()
        val pagedList = ContiguousPagedList(
                dataSource, mMainThread, mBackgroundThread, null,
                PagedList.Config.Builder().setPageSize(10).build(), null,
                ContiguousPagedList.LAST_LOAD_UNSPECIFIED)
        val callback = mock(PagedList.Callback::class.java)
        pagedList.addWeakCallback(null, callback)

        assertTrue(pagedList.isEmpty())
        drain()
        assertTrue(pagedList.isEmpty())
        asyncDataSource.flush()
        assertTrue(pagedList.isEmpty())
        mBackgroundThread.executeAll()
        assertTrue(pagedList.isEmpty())
        verifyZeroInteractions(callback)

        // Data source defers callbacks until flush, which posts result to main thread
        mMainThread.executeAll()
        assertFalse(pagedList.isEmpty())
        // callback onInsert called once with initial size
        verify(callback).onInserted(0, pagedList.size)
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun addWeakCallbackEmpty() {
        // Note: ignores Parameterized param
        val asyncDataSource = AsyncListDataSource(ITEMS)
        val dataSource = asyncDataSource.wrapAsContiguousWithoutPlaceholders()
        val pagedList = ContiguousPagedList(
                dataSource, mMainThread, mBackgroundThread, null,
                PagedList.Config.Builder().setPageSize(10).build(), null,
                ContiguousPagedList.LAST_LOAD_UNSPECIFIED)
        val callback = mock(PagedList.Callback::class.java)

        // capture empty snapshot
        val emptySnapshot = pagedList.snapshot()
        assertTrue(pagedList.isEmpty())
        assertTrue(emptySnapshot.isEmpty())

        // verify that adding callback notifies nothing going from empty -> empty
        pagedList.addWeakCallback(emptySnapshot, callback)
        verifyZeroInteractions(callback)
        pagedList.removeWeakCallback(callback)

        // data added in asynchronously
        asyncDataSource.flush()
        drain()
        assertFalse(pagedList.isEmpty())

        // verify that adding callback notifies insert going from empty -> content
        pagedList.addWeakCallback(emptySnapshot, callback)
        verify(callback).onInserted(0, pagedList.size)
        verifyNoMoreInteractions(callback)
    }

    @Test
    fun boundaryCallback_empty() {
        @Suppress("UNCHECKED_CAST")
        val boundaryCallback =
                mock(PagedList.BoundaryCallback::class.java) as PagedList.BoundaryCallback<Item>
        val pagedList = createCountedPagedList(0,
                listData = ArrayList(), boundaryCallback = boundaryCallback)
        assertEquals(0, pagedList.size)

        // nothing yet
        verifyNoMoreInteractions(boundaryCallback)

        // onZeroItemsLoaded posted, since creation often happens on BG thread
        drain()
        verify(boundaryCallback).onZeroItemsLoaded()
        verifyNoMoreInteractions(boundaryCallback)
    }

    @Test
    fun boundaryCallback_singleInitialLoad() {
        val shortList = ITEMS.subList(0, 4)
        @Suppress("UNCHECKED_CAST")
        val boundaryCallback =
                mock(PagedList.BoundaryCallback::class.java) as PagedList.BoundaryCallback<Item>
        val pagedList = createCountedPagedList(0, listData = shortList,
                initLoadSize = shortList.size, boundaryCallback = boundaryCallback)
        assertEquals(shortList.size, pagedList.size)

        // nothing yet
        verifyNoMoreInteractions(boundaryCallback)

        // onItemAtFrontLoaded / onItemAtEndLoaded posted, since creation often happens on BG thread
        drain()
        pagedList.loadAround(0)
        drain()
        verify(boundaryCallback).onItemAtFrontLoaded(shortList.first())
        verify(boundaryCallback).onItemAtEndLoaded(shortList.last())
        verifyNoMoreInteractions(boundaryCallback)
    }

    @Test
    fun boundaryCallback_delayed() {
        @Suppress("UNCHECKED_CAST")
        val boundaryCallback =
                mock(PagedList.BoundaryCallback::class.java) as PagedList.BoundaryCallback<Item>
        val pagedList = createCountedPagedList(90,
                initLoadSize = 20, prefetchDistance = 5, boundaryCallback = boundaryCallback)
        verifyRange(80, 20, pagedList)

        // nothing yet
        verifyZeroInteractions(boundaryCallback)
        drain()
        verifyZeroInteractions(boundaryCallback)

        // loading around last item causes onItemAtEndLoaded
        pagedList.loadAround(if (mCounted) 99 else 19)
        drain()
        verifyRange(80, 20, pagedList)
        verify(boundaryCallback).onItemAtEndLoaded(ITEMS.last())
        verifyNoMoreInteractions(boundaryCallback)

        // prepending doesn't trigger callback...
        pagedList.loadAround(if (mCounted) 80 else 0)
        drain()
        verifyRange(60, 40, pagedList)
        verifyZeroInteractions(boundaryCallback)

        // ...load rest of data, still no dispatch...
        pagedList.loadAround(if (mCounted) 60 else 0)
        drain()
        pagedList.loadAround(if (mCounted) 40 else 0)
        drain()
        pagedList.loadAround(if (mCounted) 20 else 0)
        drain()
        verifyRange(0, 100, pagedList)
        verifyZeroInteractions(boundaryCallback)

        // ... finally try prepend, see 0 items, which will dispatch front callback
        pagedList.loadAround(0)
        drain()
        verify(boundaryCallback).onItemAtFrontLoaded(ITEMS.first())
        verifyNoMoreInteractions(boundaryCallback)
    }

    private fun drain() {
        var executed: Boolean
        do {
            executed = mBackgroundThread.executeAll()
            executed = mMainThread.executeAll() || executed
        } while (executed)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "counted:{0}")
        fun parameters(): Array<Array<Boolean>> {
            return arrayOf(arrayOf(true), arrayOf(false))
        }

        private val ITEMS = List(100) { Item(it) }
    }
}

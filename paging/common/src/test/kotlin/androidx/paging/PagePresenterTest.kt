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

import androidx.paging.PagedSource.LoadResult.Page.Companion.COUNT_UNDEFINED
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.fail

@Suppress("TestFunctionName")
internal fun <T : Any> PagePresenter(
    pages: List<List<T>>,
    leadingNullCount: Int = COUNT_UNDEFINED,
    trailingNullCount: Int = COUNT_UNDEFINED,
    indexOfInitialPage: Int = 0
) = PagePresenter(
    PageEvent.Insert(
        loadType = LoadType.REFRESH,
        pages = pages.mapIndexed { index, list ->
            TransformedPage(
                sourcePageIndex = index - indexOfInitialPage,
                data = list,
                sourcePageSize = list.size,
                originalIndices = null)
        },
        placeholdersBefore = leadingNullCount,
        placeholdersAfter = trailingNullCount
    )
)

internal fun <T : Any> PagePresenter<T>.insertPage(
    isPrepend: Boolean,
    page: List<T>,
    remainingNulls: Int,
    callback: PagedList.Callback
) = processEvent(
        PageEvent.Insert(
            loadType = if (isPrepend) LoadType.START else LoadType.END,
            pages = listOf(TransformedPage(
                sourcePageIndex = 0,
                data = page,
                sourcePageSize = page.size,
                originalIndices = null
            )),
            placeholdersBefore = if (isPrepend) remainingNulls else 0,
            placeholdersAfter = if (isPrepend) 0 else remainingNulls
        ),
        callback
    )

internal fun <T : Any> PagePresenter<T>.dropPages(
    isPrepend: Boolean,
    pagesToDrop: Int,
    placeholdersRemaining: Int,
    callback: PagedList.Callback
) = processEvent(
    PageEvent.Drop(
        loadType = if (isPrepend) LoadType.START else LoadType.END,
        count = pagesToDrop,
        placeholdersRemaining = placeholdersRemaining
    ),
    callback
)

internal fun <T : Any> PagePresenter<T>.asList() = List(size) { get(it) }

@Suppress("SameParameterValue")
@RunWith(JUnit4::class)
class PagePresenterTest {
    @Test
    fun copy() {
        val original = PagePresenter(
            pages = mutableListOf(listOf('a', 'b', 'c')),
            leadingNullCount = 2,
            trailingNullCount = 2,
            indexOfInitialPage = 0
        )
        val copy = original.copy()
        val expected = original.asList()
        assertEquals(expected, copy.asList())

        original.insertPage(
            isPrepend = false,
            page = listOf('d', 'e'),
            remainingNulls = 0,
            callback = CallbackCapture() // ignored
        )

        assertEquals(expected, copy.asList())
        assertNotEquals(expected, original.asList())
    }

    private fun verifyAppend(
        initialItems: Int,
        initialNulls: Int,
        newItems: Int,
        newNulls: Int = COUNT_UNDEFINED,
        events: List<CallbackEvent>
    ) {
        val data = PagePresenter(
            pages = mutableListOf(List(initialItems) { 'a' + it }),
            leadingNullCount = 0,
            trailingNullCount = initialNulls,
            indexOfInitialPage = 0
        )

        val callback = CallbackCapture()
        val page: List<Char> = List(newItems) { 'a' + it + initialItems }
        data.insertPage(
            isPrepend = false,
            page = page,
            remainingNulls = newNulls,
            callback = callback
        )

        // Assert list contents first (since this shows more obvious errors)...
        val expectedNulls = if (newNulls != COUNT_UNDEFINED) {
            newNulls
        } else {
            maxOf(initialNulls - newItems, 0)
        }
        val expectedData =
            List(initialItems + newItems) { 'a' + it } + List(expectedNulls) { null }
        assertEquals(expectedData, data.asList())

        // ... then assert events
        assertEquals(events, callback.getAllAndClear())
    }

    private fun verifyPrepend(
        initialItems: Int,
        initialNulls: Int,
        newItems: Int,
        newNulls: Int,
        events: List<CallbackEvent>
    ) {
        val data = PagePresenter(
            pages = mutableListOf(List(initialItems) { 'z' + it - initialItems - 1 }),
            leadingNullCount = initialNulls,
            trailingNullCount = 0,
            indexOfInitialPage = 0
        )

        val endItemCount = newItems + initialItems
        val callback = CallbackCapture()
        data.insertPage(
            isPrepend = true,
            page = List(newItems) { 'z' + it - endItemCount - 1 },
            remainingNulls = newNulls,
            callback = callback
        )

        // Assert list contents first (since this shows more obvious errors)...
        val expectedNulls = if (newNulls != COUNT_UNDEFINED) {
            newNulls
        } else {
            maxOf(initialNulls - newItems, 0)
        }
        val expectedData =
            List(expectedNulls) { null } + List(endItemCount) { 'z' + it - endItemCount - 1 }
        assertEquals(expectedData, data.asList())

        // ... then assert events
        assertEquals(events, callback.getAllAndClear())
    }

    private fun verifyPrependAppend(
        initialItems: Int,
        initialNulls: Int,
        newItems: Int,
        newNulls: Int,
        prependEvents: List<CallbackEvent>,
        appendEvents: List<CallbackEvent>
    ) {
        verifyPrepend(initialItems, initialNulls, newItems, newNulls, prependEvents)
        verifyAppend(initialItems, initialNulls, newItems, newNulls, appendEvents)
    }

    @Test
    fun insertPageEmpty() = verifyPrependAppend(
        initialItems = 2,
        initialNulls = 0,
        newItems = 0,
        newNulls = 0,
        prependEvents = emptyList(),
        appendEvents = emptyList()
    )

    @Test
    fun insertPageSimple() = verifyPrependAppend(
        initialItems = 2,
        initialNulls = 0,
        newItems = 2,
        newNulls = 0,
        prependEvents = listOf(
            InsertEvent(0, 2)
        ),
        appendEvents = listOf(
            InsertEvent(2, 2)
        )
    )

    @Test
    fun insertPageSimplePlaceholders() = verifyPrependAppend(
        initialItems = 2,
        initialNulls = 4,
        newItems = 2,
        newNulls = 2,
        prependEvents = listOf(
            ChangeEvent(2, 2)
        ),
        appendEvents = listOf(
            ChangeEvent(2, 2)
        )
    )

    @Test
    fun insertPageInitPlaceholders() = verifyPrependAppend(
        initialItems = 2,
        initialNulls = 0,
        newItems = 2,
        newNulls = 3,
        prependEvents = listOf(
            InsertEvent(0, 2),
            InsertEvent(0, 3)
        ),
        appendEvents = listOf(
            // NOTE: theoretically these could be combined
            InsertEvent(2, 2),
            InsertEvent(4, 3)
        )
    )

    @Test
    fun insertPageInitJustPlaceholders() = verifyPrependAppend(
        initialItems = 2,
        initialNulls = 0,
        newItems = 0,
        newNulls = 3,
        prependEvents = listOf(
            InsertEvent(0, 3)
        ),
        appendEvents = listOf(
            InsertEvent(2, 3)
        )
    )

    @Test
    fun insertPageInsertNulls() = verifyPrependAppend(
        initialItems = 2,
        initialNulls = 3,
        newItems = 2,
        newNulls = 2,
        prependEvents = listOf(
            ChangeEvent(1, 2),
            InsertEvent(0, 1)
        ),
        appendEvents = listOf(
            ChangeEvent(2, 2),
            InsertEvent(5, 1)
        )
    )

    @Test
    fun insertPageRemoveNulls() = verifyPrependAppend(
        initialItems = 2,
        initialNulls = 7,
        newItems = 2,
        newNulls = 0,
        prependEvents = listOf(
            ChangeEvent(5, 2),
            RemoveEvent(0, 5)
        ),
        appendEvents = listOf(
            ChangeEvent(2, 2),
            RemoveEvent(4, 5)
        )
    )

    @Test
    fun insertPageReduceNulls() = verifyPrependAppend(
        initialItems = 2,
        initialNulls = 10,
        newItems = 3,
        newNulls = 4,
        prependEvents = listOf(
            ChangeEvent(7, 3),
            RemoveEvent(0, 3)
        ),
        appendEvents = listOf(
            ChangeEvent(2, 3),
            RemoveEvent(9, 3)
        )
    )

    private fun verifyDropEnd(
        initialPages: List<List<Char>>,
        initialNulls: Int = 0,
        newNulls: Int,
        pagesToDrop: Int,
        events: List<CallbackEvent>
    ) {
        if (initialPages.size < 2) {
            fail("require at least 2 pages")
        }

        val data = PagePresenter(
            pages = initialPages.toMutableList(),
            leadingNullCount = 0,
            trailingNullCount = initialNulls,
            indexOfInitialPage = 0
        )

        assertEquals(initialPages.flatten() + List<Char?>(initialNulls) { null }, data.asList())

        val callback = CallbackCapture()
        data.dropPages(false, pagesToDrop, newNulls, callback)

        assertEquals(events, callback.getAllAndClear())

        // assert final list state
        val finalData = initialPages.subList(0, initialPages.size - pagesToDrop).flatten()
        assertEquals(finalData + List<Char?>(newNulls) { null }, data.asList())
    }

    private fun verifyDropStart(
        initialPages: List<List<Char>>,
        initialNulls: Int = 0,
        newNulls: Int,
        pagesToDrop: Int,
        events: List<CallbackEvent>
    ) {
        if (initialPages.size < 2) {
            fail("require at least 2 pages")
        }

        val data = PagePresenter(
            pages = initialPages.reversed().toMutableList(),
            leadingNullCount = initialNulls,
            trailingNullCount = 0,
            indexOfInitialPage = 0
        )

        assertEquals(
            List<Char?>(initialNulls) { null } + initialPages.reversed().flatten(),
            data.asList()
        )

        val callback = CallbackCapture()
        data.dropPages(true, pagesToDrop, newNulls, callback)

        assertEquals(events, callback.getAllAndClear())

        // assert final list state
        val finalData = initialPages.take(initialPages.size - pagesToDrop).reversed().flatten()
        assertEquals(List<Char?>(newNulls) { null } + finalData, data.asList())
    }

    private fun verifyDrop(
        initialPages: List<List<Char>>,
        initialNulls: Int = 0,
        newNulls: Int,
        pagesToDrop: Int,
        startEvents: List<CallbackEvent>,
        endEvents: List<CallbackEvent>
    ) {
        verifyDropStart(initialPages, initialNulls, newNulls, pagesToDrop, startEvents)
        verifyDropEnd(initialPages, initialNulls, newNulls, pagesToDrop, endEvents)
    }

    @Test
    fun dropPageNoop() = verifyDrop(
        initialPages = listOf(
            listOf('a', 'b'),
            listOf('c', 'd')
        ),
        initialNulls = 0,
        newNulls = 0,
        pagesToDrop = 0,
        startEvents = emptyList(),
        endEvents = emptyList()
    )

    @Test
    fun dropPageMulti() = verifyDrop(
        initialPages = listOf(
            listOf('a', 'b'),
            listOf('c', 'd'),
            listOf('e')
        ),
        initialNulls = 0,
        newNulls = 0,
        pagesToDrop = 2,
        startEvents = listOf(RemoveEvent(0, 3)),
        endEvents = listOf(RemoveEvent(2, 3))
    )

    @Test
    fun dropPageReturnNulls() = verifyDrop(
        initialPages = listOf(
            listOf('a', 'b'),
            listOf('c', 'd'),
            listOf('e')
        ),
        initialNulls = 1,
        newNulls = 4,
        pagesToDrop = 2,
        startEvents = listOf(ChangeEvent(1, 3)),
        endEvents = listOf(ChangeEvent(2, 3))
    )

    @Test
    fun dropPageFromNoNullsToHavingNulls() = verifyDrop(
        initialPages = listOf(
            listOf('a', 'b'),
            listOf('c', 'd'),
            listOf('e')
        ),
        initialNulls = 0,
        newNulls = 3,
        pagesToDrop = 2,
        startEvents = listOf(ChangeEvent(0, 3)),
        endEvents = listOf(ChangeEvent(2, 3))
    )

    @Test
    fun dropPageChangeRemovePlaceholders() = verifyDrop(
        initialPages = listOf(
            listOf('a', 'b'),
            listOf('c', 'd'),
            listOf('e')
        ),
        initialNulls = 2,
        newNulls = 4,
        pagesToDrop = 2,
        startEvents = listOf(
            ChangeEvent(2, 3),
            RemoveEvent(0, 1)
        ),
        endEvents = listOf(
            ChangeEvent(2, 3),
            RemoveEvent(6, 1)
        )
    )

    @Test
    fun dropPageChangeRemoveItems() = verifyDrop(
        initialPages = listOf(
            listOf('a', 'b'),
            listOf('c', 'd'),
            listOf('e')
        ),
        initialNulls = 0,
        newNulls = 1,
        pagesToDrop = 2,
        startEvents = listOf(
            ChangeEvent(2, 1),
            RemoveEvent(0, 2)
        ),
        endEvents = listOf(
            ChangeEvent(2, 1),
            RemoveEvent(3, 2)
        )
    )

    @Test
    fun dropPageChangeDoubleRemove() = verifyDrop(
        initialPages = listOf(
            listOf('a', 'b'),
            listOf('c', 'd'),
            listOf('e')
        ),
        initialNulls = 3,
        newNulls = 1,
        pagesToDrop = 2,
        startEvents = listOf(
            ChangeEvent(5, 1),
            RemoveEvent(0, 2),
            RemoveEvent(0, 3)
        ),
        endEvents = listOf(
            ChangeEvent(2, 1),
            RemoveEvent(3, 2),
            RemoveEvent(3, 3)
        )
    )

    @Test
    fun getOutOfBounds() {
        val presenter = PagePresenter(
            pages = mutableListOf(listOf('a')),
            leadingNullCount = 1,
            trailingNullCount = 1,
            indexOfInitialPage = 0
        )
        assertFailsWith<IndexOutOfBoundsException> {
            presenter.get(-1)
        }
        assertFailsWith<IndexOutOfBoundsException> {
            presenter.get(4)
        }
    }

    @Test
    fun loadAroundOutOfBounds() {
        val presenter = PagePresenter(
            pages = mutableListOf(listOf('a')),
            leadingNullCount = 1,
            trailingNullCount = 1,
            indexOfInitialPage = 0
        )
        assertFailsWith<IndexOutOfBoundsException> {
            presenter.loadAround(-1)
        }
        assertFailsWith<IndexOutOfBoundsException> {
            presenter.loadAround(4)
        }
    }

    @Test
    fun loadAroundSimple() {
        val pagePresenter = PagePresenter(
            pages = listOf(listOf('a')),
            leadingNullCount = 1,
            trailingNullCount = 1,
            indexOfInitialPage = 0
        )
        assertEquals(LoadHint(0, -1), pagePresenter.loadAround(0))
        assertEquals(LoadHint(0, 0), pagePresenter.loadAround(1))
        assertEquals(LoadHint(0, 1), pagePresenter.loadAround(2))
    }

    @Test
    fun loadAround() {
        val pagePresenter = PagePresenter(
            pages = listOf(
                listOf('a'),
                listOf('b', 'c'),
                listOf('d')
            ),
            leadingNullCount = 1,
            trailingNullCount = 2,
            indexOfInitialPage = 1
        )
        assertEquals(LoadHint(-1, -1), pagePresenter.loadAround(0))
        assertEquals(LoadHint(-1, 0), pagePresenter.loadAround(1))
        assertEquals(LoadHint(0, 0), pagePresenter.loadAround(2))
        assertEquals(LoadHint(0, 1), pagePresenter.loadAround(3))
        assertEquals(LoadHint(1, 0), pagePresenter.loadAround(4))
        assertEquals(LoadHint(1, 1), pagePresenter.loadAround(5))
        assertEquals(LoadHint(1, 2), pagePresenter.loadAround(6))
    }
}
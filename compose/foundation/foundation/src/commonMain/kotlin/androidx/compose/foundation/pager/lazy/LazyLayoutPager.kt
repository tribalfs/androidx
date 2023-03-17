/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.foundation.pager.lazy

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.snapping.SnapFlingBehavior
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListBeyondBoundsInfo
import androidx.compose.foundation.lazy.NearestItemsExtraItemCount
import androidx.compose.foundation.lazy.NearestItemsSlidingWindowSize
import androidx.compose.foundation.lazy.layout.IntervalList
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutIntervalContent
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.LazyLayoutKeyIndexMap
import androidx.compose.foundation.lazy.layout.MutableIntervalList
import androidx.compose.foundation.lazy.layout.NearestRangeKeyIndexMapState
import androidx.compose.foundation.lazy.layout.PinnableItem
import androidx.compose.foundation.lazy.layout.lazyLayoutSemantics
import androidx.compose.foundation.overscroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filter

@ExperimentalFoundationApi
@Composable
internal fun Pager(
    /** Modifier to be applied for the inner layout */
    modifier: Modifier,
    /** The amount of Pages that will be present in this Pager **/
    pageCount: Int,
    /** State controlling the scroll position */
    state: PagerState,
    /** The inner padding to be added for the whole content(not for each individual page) */
    contentPadding: PaddingValues,
    /** reverse the direction of scrolling and layout */
    reverseLayout: Boolean,
    /** The layout orientation of the Pager */
    orientation: Orientation,
    /** fling behavior to be used for flinging */
    flingBehavior: SnapFlingBehavior,
    /** Whether scrolling via the user gestures is allowed. */
    userScrollEnabled: Boolean,
    /** Number of pages to layout before and after the visible pages */
    beyondBoundsPageCount: Int = 0,
    /** Space between pages **/
    pageSpacing: Dp = 0.dp,
    /** Allows to change how to calculate the Page size **/
    pageSize: PageSize,
    /** A [NestedScrollConnection] that dictates how this [Pager] behaves with nested lists.  **/
    pageNestedScrollConnection: NestedScrollConnection,
    /** a stable and unique key representing the Page **/
    key: ((index: Int) -> Any)?,
    /** The alignment to align pages horizontally. Required when isVertical is true */
    horizontalAlignment: Alignment.Horizontal,
    /** The alignment to align pages vertically. Required when isVertical is false */
    verticalAlignment: Alignment.Vertical,
    /** The content of the list */
    pageContent: @Composable (page: Int) -> Unit
) {
    require(beyondBoundsPageCount >= 0) {
        "beyondBoundsPageCount should be greater than or equal to 0, " +
            "you selected $beyondBoundsPageCount"
    }

    val overscrollEffect = ScrollableDefaults.overscrollEffect()

    // TODO(levima) Add test for pageContent change
    val pagerItemProvider = remember(state, pageCount, key, pageContent as Any?) {
        val intervalList = MutableIntervalList<PagerIntervalContent>().apply {
            addInterval(pageCount, PagerIntervalContent(key = key, item = pageContent))
        }
        PagerLazyLayoutItemProvider(
            state = state,
            intervals = intervalList
        )
    }

    val beyondBoundsInfo = remember { LazyListBeyondBoundsInfo() }

    val measurePolicy = rememberPagerMeasurePolicy(
        state = state,
        contentPadding = contentPadding,
        reverseLayout = reverseLayout,
        orientation = orientation,
        beyondBoundsPageCount = beyondBoundsPageCount,
        pageSpacing = pageSpacing,
        pageSize = pageSize,
        horizontalAlignment = horizontalAlignment,
        verticalAlignment = verticalAlignment,
        itemProvider = pagerItemProvider,
        pageCount = pageCount,
        beyondBoundsInfo = beyondBoundsInfo
    )

    val pagerFlingBehavior = remember(flingBehavior, state) {
        PagerWrapperFlingBehavior(flingBehavior, state)
    }

    // TODO(levima) Move this logic to measure pass
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .drop(1) // Initial scroll is false
            .collect { state.updateOnScrollStopped() }
    }

    val pagerSemantics = if (userScrollEnabled) {
        Modifier.pagerSemantics(state, orientation == Orientation.Vertical)
    } else {
        Modifier
    }

    val semanticState = rememberPagerSemanticState(
        state,
        pagerItemProvider,
        reverseLayout,
        orientation == Orientation.Vertical
    )

    LazyLayout(
        modifier = modifier
            .then(state.remeasurementModifier)
            .then(state.awaitLayoutModifier)
            .then(pagerSemantics)
            .lazyLayoutSemantics(
                itemProvider = pagerItemProvider,
                state = semanticState,
                orientation = orientation,
                userScrollEnabled = userScrollEnabled,
                reverseScrolling = reverseLayout
            )
            .clipScrollableContainer(orientation)
            .pagerBeyondBoundsModifier(state, beyondBoundsInfo, reverseLayout, orientation)
            .overscroll(overscrollEffect)
            .scrollable(
                orientation = orientation,
                reverseDirection = ScrollableDefaults.reverseDirection(
                    LocalLayoutDirection.current,
                    orientation,
                    reverseLayout
                ),
                interactionSource = state.internalInteractionSource,
                flingBehavior = pagerFlingBehavior,
                state = state,
                overscrollEffect = overscrollEffect,
                enabled = userScrollEnabled
            )
            .nestedScroll(pageNestedScrollConnection),
        measurePolicy = measurePolicy,
        prefetchState = state.prefetchState,
        itemProvider = pagerItemProvider
    )
}

@ExperimentalFoundationApi
internal class PagerLazyLayoutItemProvider(
    intervals: IntervalList<PagerIntervalContent>,
    val state: PagerState
) : LazyLayoutItemProvider {
    private val pagerContent = PagerLayoutIntervalContent(intervals)
    private val keyToIndexMap: LazyLayoutKeyIndexMap by NearestRangeKeyIndexMapState(
        firstVisibleItemIndex = { state.firstVisiblePage },
        slidingWindowSize = { NearestItemsSlidingWindowSize },
        extraItemCount = { NearestItemsExtraItemCount },
        content = { pagerContent }
    )
    override val itemCount: Int
        get() = pagerContent.itemCount

    @Composable
    override fun Item(index: Int) {
        pagerContent.PinnableItem(index, state.pinnedPages) { localIndex ->
            item(localIndex)
        }
    }

    override fun getKey(index: Int): Any = pagerContent.getKey(index)

    override fun getIndex(key: Any): Int = keyToIndexMap[key]
}

@OptIn(ExperimentalFoundationApi::class)
private class PagerLayoutIntervalContent(
    override val intervals: IntervalList<PagerIntervalContent>
) : LazyLayoutIntervalContent<PagerIntervalContent>()

@OptIn(ExperimentalFoundationApi::class)
internal class PagerIntervalContent(
    override val key: ((page: Int) -> Any)?,
    val item: @Composable (page: Int) -> Unit
) : LazyLayoutIntervalContent.Interval

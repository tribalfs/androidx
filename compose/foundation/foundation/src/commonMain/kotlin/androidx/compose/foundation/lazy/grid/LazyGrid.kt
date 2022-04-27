/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.foundation.lazy.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.assertNotNestingScrollableContainers
import androidx.compose.foundation.clipScrollableContainer
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.OverScrollController
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.rememberOverScrollController
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.compose.ui.util.fastForEach

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LazyGrid(
    /** Modifier to be applied for the inner layout */
    modifier: Modifier = Modifier,
    /** State controlling the scroll position */
    state: LazyGridState,
    /** Prefix sums of cross axis sizes of slots per line, e.g. the columns for vertical grid. */
    slotSizesSums: Density.(Constraints) -> List<Int>,
    /** The inner padding to be added for the whole content (not for each individual item) */
    contentPadding: PaddingValues = PaddingValues(0.dp),
    /** reverse the direction of scrolling and layout */
    reverseLayout: Boolean = false,
    /** The layout orientation of the grid */
    isVertical: Boolean,
    /** fling behavior to be used for flinging */
    flingBehavior: FlingBehavior = ScrollableDefaults.flingBehavior(),
    /** Whether scrolling via the user gestures is allowed. */
    userScrollEnabled: Boolean,
    /** The vertical arrangement for items/lines. */
    verticalArrangement: Arrangement.Vertical,
    /** The horizontal arrangement for items/lines. */
    horizontalArrangement: Arrangement.Horizontal,
    /** The content of the grid */
    content: LazyGridScope.() -> Unit
) {
    val overScrollController = rememberOverScrollController()

    val itemProvider = rememberItemProvider(state, content)

    val spanLayoutProvider = remember(itemProvider) {
        derivedStateOf { LazyGridSpanLayoutProvider(itemProvider) }
    }

    val scope = rememberCoroutineScope()
    val placementAnimator = remember(state, isVertical) {
        LazyGridItemPlacementAnimator(scope, isVertical)
    }
    state.placementAnimator = placementAnimator

    val measurePolicy = rememberLazyGridMeasurePolicy(
        itemProvider,
        state,
        overScrollController,
        spanLayoutProvider,
        slotSizesSums,
        contentPadding,
        reverseLayout,
        isVertical,
        horizontalArrangement,
        verticalArrangement,
        placementAnimator
    )

    state.isVertical = isVertical

    ScrollPositionUpdater(itemProvider, state)

    LazyLayout(
        modifier = modifier
            .then(state.remeasurementModifier)
            .lazyGridSemantics(
                itemProvider = itemProvider,
                state = state,
                coroutineScope = scope,
                isVertical = isVertical,
                reverseScrolling = reverseLayout,
                userScrollEnabled = userScrollEnabled
            )
            .clipScrollableContainer(isVertical)
            .scrollable(
                orientation = if (isVertical) Orientation.Vertical else Orientation.Horizontal,
                reverseDirection = run {
                    // A finger moves with the content, not with the viewport. Therefore,
                    // always reverse once to have "natural" gesture that goes reversed to layout
                    var reverseDirection = !reverseLayout
                    // But if rtl and horizontal, things move the other way around
                    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                    if (isRtl && !isVertical) {
                        reverseDirection = !reverseDirection
                    }
                    reverseDirection
                },
                interactionSource = state.internalInteractionSource,
                flingBehavior = flingBehavior,
                state = state,
                overScrollController = overScrollController,
                enabled = userScrollEnabled
            ),
        prefetchState = state.prefetchState,
        measurePolicy = measurePolicy,
        itemProvider = itemProvider
    )
}

/** Extracted to minimize the recomposition scope */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ScrollPositionUpdater(
    itemProvider: LazyGridItemProvider,
    state: LazyGridState
) {
    if (itemProvider.itemCount > 0) {
        state.updateScrollPositionIfTheFirstItemWasMoved(itemProvider)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun rememberLazyGridMeasurePolicy(
    /** Items provider of the list. */
    itemProvider: LazyGridItemProvider,
    /** The state of the list. */
    state: LazyGridState,
    /** The overscroll controller. */
    overScrollController: OverScrollController,
    /** Cache based provider for spans. */
    stateOfSpanLayoutProvider: State<LazyGridSpanLayoutProvider>,
    /** Prefix sums of cross axis sizes of slots of the grid. */
    slotSizesSums: Density.(Constraints) -> List<Int>,
    /** The inner padding to be added for the whole content(nor for each individual item) */
    contentPadding: PaddingValues,
    /** reverse the direction of scrolling and layout */
    reverseLayout: Boolean,
    /** The layout orientation of the list */
    isVertical: Boolean,
    /** The horizontal arrangement for items. Required when isVertical is false */
    horizontalArrangement: Arrangement.Horizontal? = null,
    /** The vertical arrangement for items. Required when isVertical is true */
    verticalArrangement: Arrangement.Vertical? = null,
    /** Item placement animator. Should be notified with the measuring result */
    placementAnimator: LazyGridItemPlacementAnimator
) = remember<LazyLayoutMeasureScope.(Constraints) -> MeasureResult>(
    state,
    overScrollController,
    slotSizesSums,
    contentPadding,
    reverseLayout,
    isVertical,
    horizontalArrangement,
    verticalArrangement,
    placementAnimator
) {
    { constraints ->
        constraints.assertNotNestingScrollableContainers(isVertical)

        // resolve content paddings
        val startPadding = contentPadding.calculateStartPadding(layoutDirection).roundToPx()
        val endPadding = contentPadding.calculateEndPadding(layoutDirection).roundToPx()
        val topPadding = contentPadding.calculateTopPadding().roundToPx()
        val bottomPadding = contentPadding.calculateBottomPadding().roundToPx()
        val totalVerticalPadding = topPadding + bottomPadding
        val totalHorizontalPadding = startPadding + endPadding
        val totalMainAxisPadding = if (isVertical) totalVerticalPadding else totalHorizontalPadding
        val beforeContentPadding = when {
            isVertical && !reverseLayout -> topPadding
            isVertical && reverseLayout -> bottomPadding
            !isVertical && !reverseLayout -> startPadding
            else -> endPadding // !isVertical && reverseLayout
        }
        val afterContentPadding = totalMainAxisPadding - beforeContentPadding
        val contentConstraints = constraints.offset(-totalHorizontalPadding, -totalVerticalPadding)

        state.updateScrollPositionIfTheFirstItemWasMoved(itemProvider)

        val spanLayoutProvider = stateOfSpanLayoutProvider.value
        val resolvedSlotSizesSums = slotSizesSums(constraints)
        spanLayoutProvider.slotsPerLine = resolvedSlotSizesSums.size

        // Update the state's cached Density and slotsPerLine
        state.density = this
        state.slotsPerLine = resolvedSlotSizesSums.size

        val spaceBetweenLinesDp = if (isVertical) {
            requireNotNull(verticalArrangement).spacing
        } else {
            requireNotNull(horizontalArrangement).spacing
        }
        val spaceBetweenLines = spaceBetweenLinesDp.roundToPx()
        val spaceBetweenSlotsDp = if (isVertical) {
            horizontalArrangement?.spacing ?: 0.dp
        } else {
            verticalArrangement?.spacing ?: 0.dp
        }
        val spaceBetweenSlots = spaceBetweenSlotsDp.roundToPx()

        val itemsCount = itemProvider.itemCount

        val measuredItemProvider = LazyMeasuredItemProvider(
            itemProvider,
            this,
            spaceBetweenLines
        ) { index, key, crossAxisSize, mainAxisSpacing, placeables ->
            LazyMeasuredItem(
                index = index,
                key = key,
                isVertical = isVertical,
                crossAxisSize = crossAxisSize,
                mainAxisSpacing = mainAxisSpacing,
                reverseLayout = reverseLayout,
                layoutDirection = layoutDirection,
                beforeContentPadding = beforeContentPadding,
                afterContentPadding = afterContentPadding,
                visualOffset = IntOffset(startPadding, topPadding),
                placeables = placeables,
                placementAnimator = placementAnimator
            )
        }
        val measuredLineProvider = LazyMeasuredLineProvider(
            isVertical,
            resolvedSlotSizesSums,
            spaceBetweenSlots,
            itemsCount,
            spaceBetweenLines,
            measuredItemProvider,
            spanLayoutProvider
        ) { index, items, spans, mainAxisSpacing ->
            LazyMeasuredLine(
                index = index,
                items = items,
                spans = spans,
                isVertical = isVertical,
                slotsPerLine = resolvedSlotSizesSums.size,
                layoutDirection = layoutDirection,
                mainAxisSpacing = mainAxisSpacing,
                crossAxisSpacing = spaceBetweenSlots
            )
        }
        state.prefetchInfoRetriever = { line ->
            val lineConfiguration = spanLayoutProvider.getLineConfiguration(line.value)
            var index = ItemIndex(lineConfiguration.firstItemIndex)
            var slot = 0
            val result = ArrayList<Pair<Int, Constraints>>(lineConfiguration.spans.size)
            lineConfiguration.spans.fastForEach {
                val span = it.currentLineSpan
                result.add(index.value to measuredLineProvider.childConstraints(slot, span))
                ++index
                slot += span
            }
            result
        }

        // can be negative if the content padding is larger than the max size from constraints
        val mainAxisAvailableSize = if (isVertical) {
            constraints.maxHeight - totalVerticalPadding
        } else {
            constraints.maxWidth - totalHorizontalPadding
        }

        val firstVisibleLineIndex: LineIndex
        val firstVisibleLineScrollOffset: Int
        if (state.firstVisibleItemIndexNonObservable.value < itemsCount || itemsCount <= 0) {
            firstVisibleLineIndex = spanLayoutProvider.getLineIndexOfItem(
                state.firstVisibleItemIndexNonObservable.value
            )
            firstVisibleLineScrollOffset = state.firstVisibleItemScrollOffsetNonObservable
        } else {
            // the data set has been updated and now we have less items that we were
            // scrolled to before
            firstVisibleLineIndex = spanLayoutProvider.getLineIndexOfItem(itemsCount - 1)
            firstVisibleLineScrollOffset = 0
        }
        measureLazyGrid(
            itemsCount = itemsCount,
            measuredLineProvider = measuredLineProvider,
            measuredItemProvider = measuredItemProvider,
            mainAxisAvailableSize = mainAxisAvailableSize,
            slotsPerLine = resolvedSlotSizesSums.size,
            beforeContentPadding = beforeContentPadding,
            afterContentPadding = afterContentPadding,
            firstVisibleLineIndex = firstVisibleLineIndex,
            firstVisibleLineScrollOffset = firstVisibleLineScrollOffset,
            scrollToBeConsumed = state.scrollToBeConsumed,
            constraints = contentConstraints,
            isVertical = isVertical,
            verticalArrangement = verticalArrangement,
            horizontalArrangement = horizontalArrangement,
            reverseLayout = reverseLayout,
            density = this,
            layoutDirection = layoutDirection,
            placementAnimator = placementAnimator,
            layout = { width, height, placement ->
                layout(
                    constraints.constrainWidth(width + totalHorizontalPadding),
                    constraints.constrainHeight(height + totalVerticalPadding),
                    emptyMap(),
                    placement
                )
            }
        ).also {
            state.applyMeasureResult(it)
            refreshOverScrollInfo(
                overScrollController,
                it,
                constraints,
                totalHorizontalPadding,
                totalVerticalPadding
            )
        }
    }
}

private fun refreshOverScrollInfo(
    overScrollController: OverScrollController,
    result: LazyGridMeasureResult,
    constraints: Constraints,
    totalHorizontalPadding: Int,
    totalVerticalPadding: Int
) {
    val canScrollForward = result.canScrollForward
    val canScrollBackward = (result.firstVisibleLine?.items?.firstOrNull() ?: 0) != 0 ||
        result.firstVisibleLineScrollOffset != 0

    overScrollController.refreshContainerInfo(
        Size(
            constraints.constrainWidth(result.width + totalHorizontalPadding).toFloat(),
            constraints.constrainHeight(result.height + totalVerticalPadding).toFloat()
        ),
        canScrollForward || canScrollBackward
    )
}

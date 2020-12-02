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

package androidx.compose.foundation.lazy

import androidx.compose.animation.asDisposableClock
import androidx.compose.animation.core.AnimationClockObservable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Interaction
import androidx.compose.foundation.InteractionState
import androidx.compose.foundation.animation.FlingConfig
import androidx.compose.foundation.animation.defaultFlingConfig
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableController
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.savedinstancestate.Saver
import androidx.compose.runtime.savedinstancestate.listSaver
import androidx.compose.runtime.savedinstancestate.rememberSavedInstanceState
import androidx.compose.ui.layout.Remeasurement
import androidx.compose.ui.layout.RemeasurementModifier
import androidx.compose.ui.platform.AmbientAnimationClock
import androidx.compose.ui.util.annotation.IntRange
import androidx.compose.ui.util.annotation.VisibleForTesting
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Creates a [LazyListState] that is remembered across compositions.
 *
 * Changes to the provided initial values will **not** result in the state being recreated or
 * changed in any way if it has already been created.
 *
 * @param initialFirstVisibleItemIndex the initial value for [LazyListState.firstVisibleItemIndex]
 * @param initialFirstVisibleItemScrollOffset the initial value for
 * [LazyListState.firstVisibleItemScrollOffset]
 * @param interactionState [InteractionState] that will be updated when the element with this
 * state is being scrolled by dragging, using [Interaction.Dragged]. If you want to know whether
 * the fling (or smooth scroll) is in progress, use [LazyListState.isAnimationRunning].
 */
@Composable
fun rememberLazyListState(
    initialFirstVisibleItemIndex: Int = 0,
    initialFirstVisibleItemScrollOffset: Int = 0,
    interactionState: InteractionState? = null
): LazyListState {
    val clock = AmbientAnimationClock.current.asDisposableClock()
    val config = defaultFlingConfig()

    // Avoid creating a new instance every invocation
    val saver = remember(config, clock, interactionState) {
        LazyListState.Saver(config, clock, interactionState)
    }

    return rememberSavedInstanceState(config, clock, interactionState, saver = saver) {
        LazyListState(
            initialFirstVisibleItemIndex,
            initialFirstVisibleItemScrollOffset,
            interactionState,
            config,
            clock
        )
    }
}

/**
 * A state object that can be hoisted to control and observe scrolling
 *
 * In most cases, this will be created via [rememberLazyListState].
 *
 * @param firstVisibleItemIndex the initial value for [LazyListState.firstVisibleItemIndex]
 * @param firstVisibleItemScrollOffset the initial value for
 * @param interactionState [InteractionState] that will be updated when the element with this
 * state is being scrolled by dragging, using [Interaction.Dragged]. If you want to know whether
 * the fling (or smooth scroll) is in progress, use [LazyListState.isAnimationRunning].
 * @param flingConfig fling configuration to use for flinging
 * @param animationClock animation clock to run flinging and smooth scrolling on
 */
@Stable
class LazyListState constructor(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0,
    interactionState: InteractionState? = null,
    flingConfig: FlingConfig,
    animationClock: AnimationClockObservable
) {
    /**
     * The holder class for the current scroll position.
     */
    private val scrollPosition =
        ItemRelativeScrollPosition(firstVisibleItemIndex, firstVisibleItemScrollOffset)

    /**
     * The index of the first item that is visible
     */
    val firstVisibleItemIndex: Int get() = scrollPosition.observableIndex

    /**
     * The scroll offset of the first visible item. Scrolling forward is positive - i.e., the
     * amount that the item is offset backwards
     */
    val firstVisibleItemScrollOffset: Int get() = scrollPosition.observableScrollOffset

    /**
     * whether this [LazyListState] is currently scrolling via [scroll] or via an
     * animation/fling.
     *
     * Note: **all** scrolls initiated via [scroll] are considered to be animations, regardless of
     * whether they are actually performing an animation.
     */
    val isAnimationRunning
        get() = scrollableController.isAnimationRunning

    /**
     * The amount of scroll to be consumed in the next layout pass.  Scrolling forward is negative
     * - that is, it is the amount that the items are offset in y
     */
    private var scrollToBeConsumed = 0f

    /**
     * The ScrollableController instance. We keep it as we need to call stopAnimation on it once
     * we reached the end of the list.
     */
    internal val scrollableController =
        ScrollableController(
            flingConfig = flingConfig,
            animationClock = animationClock,
            consumeScrollDelta = { -onScroll(-it) },
            interactionState = interactionState
        )

    /**
     * The [Remeasurement] object associated with our layout. It allows us to remeasure
     * synchronously during scroll.
     */
    private lateinit var remeasurement: Remeasurement

    /**
     * Only used for testing to confirm that we're not making too many measure passes
     */
    @VisibleForTesting
    internal var numMeasurePasses: Int = 0

    /**
     * The modifier which provides [remeasurement].
     */
    internal val remeasurementModifier = object : RemeasurementModifier {
        override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
            this@LazyListState.remeasurement = remeasurement
        }
    }

    /**
     * Instantly brings the item at [index] to the top of the viewport, offset by [scrollOffset]
     * pixels.
     *
     * Cancels the currently running scroll, if any, and suspends until the cancellation is
     * complete.
     *
     * @param index the data index to snap to
     * @param scrollOffset the number of pixels past the start of the item to snap to
     */
    @OptIn(ExperimentalFoundationApi::class)
    suspend fun snapToItemIndex(
        @IntRange(from = 0)
        index: Int,
        @IntRange(from = 0)
        scrollOffset: Int = 0
    ) = scrollableController.scroll {
        scrollPosition.update(
            index = DataIndex(index),
            scrollOffset = scrollOffset,
            // `true` will be replaced with the real value during the forceRemeasure() execution
            canScrollForward = true
        )
        remeasurement.forceRemeasure()
    }

    /**
     * Call this function to take control of scrolling and gain the ability to send scroll events
     * via [ScrollScope.scrollBy]. All actions that change the logical scroll position must be
     * performed within a [scroll] block (even if they don't call any other methods on this
     * object) in order to guarantee that mutual exclusion is enforced.
     *
     * Cancels the currently running scroll, if any, and suspends until the cancellation is
     * complete.
     *
     * If [scroll] is called from elsewhere, this will be canceled.
     */
    @OptIn(ExperimentalFoundationApi::class)
    suspend fun scroll(
        block: suspend ScrollScope.() -> Unit
    ): Unit = scrollableController.scroll(block)

    /**
     * Smooth scroll by [value] pixels.
     *
     * Cancels the currently running scroll, if any, and suspends until the cancellation is
     * complete.
     *
     * @param value delta to scroll by
     * @param spec [AnimationSpec] to be used for this smooth scrolling
     *
     * @return the amount of scroll consumed
     */
    suspend fun smoothScrollBy(
        value: Float,
        spec: AnimationSpec<Float> = spring()
    ): Float = scrollableController.smoothScrollBy(value, spec)

    // TODO: Coroutine scrolling APIs will allow this to be private again once we have more
    //  fine-grained control over scrolling
    @VisibleForTesting
    internal fun onScroll(distance: Float): Float {
        if (distance < 0 && !scrollPosition.canScrollForward ||
            distance > 0 && !scrollPosition.canScrollBackward
        ) {
            return 0f
        }
        check(abs(scrollToBeConsumed) < 0.5f) {
            "entered drag with non-zero pending scroll: $scrollToBeConsumed"
        }
        scrollToBeConsumed += distance

        // scrollToBeConsumed will be consumed synchronously during the forceRemeasure invocation
        // inside measuring we do scrollToBeConsumed.roundToInt() so there will be no scroll if
        // we have less than 0.5 pixels
        if (abs(scrollToBeConsumed) >= 0.5f) {
            remeasurement.forceRemeasure()
        }

        // here scrollToBeConsumed is already consumed during the forceRemeasure invocation
        if (abs(scrollToBeConsumed) < 0.5f) {
            // We consumed all of it - we'll hold onto the fractional scroll for later, so report
            // that we consumed the whole thing
            return distance
        } else {
            val scrollConsumed = distance - scrollToBeConsumed
            // We did not consume all of it - return the rest to be consumed elsewhere (e.g.,
            // nested scrolling)
            scrollToBeConsumed = 0f // We're not consuming the rest, give it back
            scrollableController.stopFlingAnimation()
            return scrollConsumed
        }
    }

    /**
     * Measures and positions currently visible items using [LazyMeasuredItemProvider].
     */
    internal fun measure(
        itemsCount: Int,
        itemProvider: LazyMeasuredItemProvider,
        mainAxisMaxSize: Int,
        startContentPadding: Int,
        endContentPadding: Int
    ): LazyListMeasureResult {
        numMeasurePasses++
        require(startContentPadding >= 0)
        require(endContentPadding >= 0)
        if (itemsCount <= 0) {
            // empty data set. reset the current scroll and report zero size
            scrollPosition.update(
                index = DataIndex(0),
                scrollOffset = 0,
                canScrollForward = false
            )
            return LazyListMeasureResult(0, 0, emptyList(), 0)
        } else {
            var currentFirstItemIndex = scrollPosition.index
            var currentFirstItemScrollOffset = scrollPosition.scrollOffset

            if (currentFirstItemIndex.value >= itemsCount) {
                // the data set has been updated and now we have less items that we were
                // scrolled to before
                currentFirstItemIndex = DataIndex(itemsCount - 1)
                currentFirstItemScrollOffset = 0
            }

            // represents the real amount of scroll we applied as a result of this measure pass.
            var scrollDelta = scrollToBeConsumed.roundToInt()

            // applying the whole requested scroll offset. we will figure out if we can't consume
            // all of it later
            currentFirstItemScrollOffset -= scrollDelta

            // if the current scroll offset is less than minimally possible
            if (currentFirstItemIndex == DataIndex(0) && currentFirstItemScrollOffset < 0) {
                scrollDelta += currentFirstItemScrollOffset
                currentFirstItemScrollOffset = 0
            }

            // saving it into the field as we first go backward and after that want to go forward
            // again from the initial position
            val goingForwardInitialIndex = currentFirstItemIndex
            var goingForwardInitialScrollOffset = currentFirstItemScrollOffset

            // this will contain all the MeasuredItems representing the visible items
            val visibleItems = mutableListOf<LazyMeasuredItem>()

            // include the start padding so we compose items in the padding area. in the end we
            // will remove it back from the currentFirstItemScrollOffset calculation
            currentFirstItemScrollOffset -= startContentPadding

            // define min and max offsets (min offset currently includes startPadding)
            val minOffset = -startContentPadding
            val maxOffset = mainAxisMaxSize

            // we had scrolled backward or we compose items in the start padding area, which means
            // items before current firstItemScrollOffset should be visible. compose them and update
            // firstItemScrollOffset
            while (currentFirstItemScrollOffset < 0 && currentFirstItemIndex > DataIndex(0)) {
                val previous = DataIndex(currentFirstItemIndex.value - 1)
                val measuredItem = itemProvider.getAndMeasure(previous)
                visibleItems.add(0, measuredItem)
                currentFirstItemScrollOffset += measuredItem.mainAxisSize
                currentFirstItemIndex = previous
            }
            // if we were scrolled backward, but there were not enough items before. this means
            // not the whole scroll was consumed
            if (currentFirstItemScrollOffset < minOffset) {
                scrollDelta += currentFirstItemScrollOffset
                goingForwardInitialScrollOffset += currentFirstItemScrollOffset
                currentFirstItemScrollOffset = minOffset
            }

            // remembers the composed MeasuredItem which we are not currently placing as they are out
            // of screen. it is possible we will need to place them if the remaining items will
            // not fill the whole viewport and we will need to scroll back
            var notUsedButComposedItems: MutableList<LazyMeasuredItem>? = null

            // composing visible items starting from goingForwardInitialIndex until we fill the
            // whole viewport
            var index = goingForwardInitialIndex
            val maxMainAxis = maxOffset + endContentPadding
            var mainAxisUsed = -goingForwardInitialScrollOffset
            var maxCrossAxis = 0
            while (mainAxisUsed <= maxMainAxis && index.value < itemsCount) {
                val measuredItem = itemProvider.getAndMeasure(index)
                mainAxisUsed += measuredItem.mainAxisSize
                maxCrossAxis = maxOf(maxCrossAxis, measuredItem.crossAxisSize)

                if (mainAxisUsed < minOffset) {
                    // this item is offscreen and will not be placed. advance firstVisibleItemIndex
                    currentFirstItemIndex = index + 1
                    currentFirstItemScrollOffset -= measuredItem.mainAxisSize
                    // but remember the corresponding placeables in case we will be forced to
                    // scroll back as there were not enough items to fill the viewport
                    if (notUsedButComposedItems == null) {
                        notUsedButComposedItems = mutableListOf()
                    }
                    notUsedButComposedItems.add(measuredItem)
                } else {
                    visibleItems.add(measuredItem)
                }

                index++
            }

            // we didn't fill the whole viewport with items starting from firstVisibleItemIndex.
            // lets try to scroll back if we have enough items before firstVisibleItemIndex.
            if (mainAxisUsed < maxOffset) {
                val toScrollBack = maxOffset - mainAxisUsed
                currentFirstItemScrollOffset -= toScrollBack
                mainAxisUsed += toScrollBack
                while (currentFirstItemScrollOffset < 0 && currentFirstItemIndex > DataIndex(0)) {
                    val previous = DataIndex(currentFirstItemIndex.value - 1)
                    val alreadyComposedIndex = notUsedButComposedItems?.lastIndex ?: -1
                    val measuredItem = if (alreadyComposedIndex >= 0) {
                        notUsedButComposedItems!!.removeAt(alreadyComposedIndex)
                    } else {
                        itemProvider.getAndMeasure(previous)
                    }
                    visibleItems.add(0, measuredItem)
                    currentFirstItemScrollOffset += measuredItem.mainAxisSize
                    currentFirstItemIndex = previous
                }
                scrollDelta += toScrollBack
                if (currentFirstItemScrollOffset < minOffset) {
                    scrollDelta += currentFirstItemScrollOffset
                    mainAxisUsed += currentFirstItemScrollOffset
                    currentFirstItemScrollOffset = minOffset
                }
            }

            // report the amount of pixels we consumed. scrollDelta can be smaller than
            // scrollToBeConsumed if there were not enough items to fill the offered space or it
            // can be larger if items were resized, or if, for example, we were previously
            // displaying the item 15, but now we have only 10 items in total in the data set.
            if (scrollToBeConsumed.roundToInt().sign == scrollDelta.sign &&
                abs(scrollToBeConsumed.roundToInt()) >= abs(scrollDelta)
            ) {
                scrollToBeConsumed -= scrollDelta
            } else {
                scrollToBeConsumed = 0f
            }

            // the initial offset for items from visibleItems list
            val firstItemOffset = -(currentFirstItemScrollOffset + startContentPadding)

            // compensate the content padding we initially added in currentFirstItemScrollOffset.
            // if the item is fully located in the start padding area we  need to use the next
            // item as a value for currentFirstItemIndex
            if (startContentPadding > 0) {
                currentFirstItemScrollOffset += startContentPadding
                var startPaddingItems = 0
                while (startPaddingItems < visibleItems.lastIndex) {
                    val size = visibleItems[startPaddingItems].mainAxisSize
                    if (size <= currentFirstItemScrollOffset) {
                        startPaddingItems++
                        currentFirstItemScrollOffset -= size
                        currentFirstItemIndex++
                    } else {
                        break
                    }
                }
            }

            // update state with the new calculated scroll position
            scrollPosition.update(
                index = currentFirstItemIndex,
                scrollOffset = currentFirstItemScrollOffset,
                canScrollForward = mainAxisUsed > maxOffset
            )

            return LazyListMeasureResult(
                mainAxisSize = mainAxisUsed + startContentPadding,
                crossAxisSize = maxCrossAxis,
                items = visibleItems,
                itemsScrollOffset = firstItemOffset
            )
        }
    }

    companion object {
        /**
         * The default [Saver] implementation for [LazyListState].
         */
        fun Saver(
            flingConfig: FlingConfig,
            animationClock: AnimationClockObservable,
            interactionState: InteractionState?
        ): Saver<LazyListState, *> = listSaver(
            save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
            restore = {
                LazyListState(
                    firstVisibleItemIndex = it[0],
                    firstVisibleItemScrollOffset = it[1],
                    flingConfig = flingConfig,
                    animationClock = animationClock,
                    interactionState = interactionState
                )
            }
        )
    }
}

/**
 * Contains the current scroll position represented by the first visible item index and the first
 * visible item scroll offset.
 *
 * Allows reading the values without recording the state read: [index] and [scrollOffset].
 * And with recording the state read which makes such reads observable: [observableIndex] and
 * [observableScrollOffset].
 *
 * To update the values use [update].
 *
 * The whole purpose of this class is to allow reading the scroll position without recording the
 * model read as if we do so inside the measure block the extra remeasurement will be scheduled
 * once we update the values in the end of the measure block. Abstracting the variables
 * duplication into a separate class allows us maintain the contract of keeping them in sync.
 */
private class ItemRelativeScrollPosition(
    initialIndex: Int = 0,
    initialScrollOffset: Int = 0
) {
    var index = DataIndex(initialIndex)
        private set

    var scrollOffset = initialScrollOffset
        private set

    private val indexState = mutableStateOf(index.value)
    val observableIndex get() = indexState.value

    private val scrollOffsetState = mutableStateOf(scrollOffset)
    val observableScrollOffset get() = scrollOffsetState.value

    val canScrollBackward: Boolean get() = index.value != 0 || scrollOffset != 0
    var canScrollForward: Boolean = false
        private set

    fun update(index: DataIndex, scrollOffset: Int, canScrollForward: Boolean) {
        require(index.value >= 0f) { "Index should be non-negative (${index.value})" }
        require(scrollOffset >= 0f) { "scrollOffset should be non-negative ($scrollOffset)" }
        this.index = index
        indexState.value = index.value
        this.scrollOffset = scrollOffset
        scrollOffsetState.value = scrollOffset
        this.canScrollForward = canScrollForward
    }
}

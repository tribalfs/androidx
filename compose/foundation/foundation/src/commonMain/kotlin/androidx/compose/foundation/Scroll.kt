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

package androidx.compose.foundation

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.SpringSpec
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.ScrollAxisRange
import androidx.compose.ui.semantics.horizontalScrollAxisRange
import androidx.compose.ui.semantics.scrollBy
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.verticalScrollAxisRange
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Create and [remember] the [ScrollState] based on the currently appropriate scroll
 * configuration to allow changing scroll position or observing scroll behavior.
 *
 * Learn how to control the state of [Modifier.verticalScroll] or [Modifier.horizontalScroll]:
 * @sample androidx.compose.foundation.samples.ControlledScrollableRowSample
 *
 * @param initial initial scroller position to start with
 */
@Composable
fun rememberScrollState(initial: Int = 0): ScrollState {
    return rememberSaveable(saver = ScrollState.Saver) {
        ScrollState(initial = initial)
    }
}

/**
 * State of the scroll. Allows the developer to change the scroll position or get current state by
 * calling methods on this object. To be hosted and passed to [Modifier.verticalScroll] or
 * [Modifier.horizontalScroll]
 *
 * To create and automatically remember [ScrollState] with default parameters use
 * [rememberScrollState].
 *
 * Learn how to control the state of [Modifier.verticalScroll] or [Modifier.horizontalScroll]:
 * @sample androidx.compose.foundation.samples.ControlledScrollableRowSample
 *
 * @param initial value of the scroll
 */
@Stable
class ScrollState(initial: Int) : ScrollableState {

    /**
     * current scroll position value in pixels
     */
    var value: Int by mutableStateOf(initial, structuralEqualityPolicy())
        private set

    /**
     * maximum bound for [value], or [Int.MAX_VALUE] if still unknown
     */
    var maxValue: Int
        get() = _maxValueState.value
        internal set(newMax) {
            _maxValueState.value = newMax
            if (value > newMax) {
                value = newMax
            }
        }

    /**
     * [InteractionSource] that will be used to dispatch drag events when this
     * list is being dragged. If you want to know whether the fling (or smooth scroll) is in
     * progress, use [isScrollInProgress].
     */
    val interactionSource: InteractionSource get() = internalInteractionSource

    internal val internalInteractionSource: MutableInteractionSource = MutableInteractionSource()

    private var _maxValueState = mutableStateOf(Int.MAX_VALUE, structuralEqualityPolicy())

    /**
     * We receive scroll events in floats but represent the scroll position in ints so we have to
     * manually accumulate the fractional part of the scroll to not completely ignore it.
     */
    private var accumulator: Float = 0f

    private val scrollableState = ScrollableState {
        val absolute = (value + it + accumulator)
        val newValue = absolute.coerceIn(0f, maxValue.toFloat())
        val changed = absolute != newValue
        val consumed = newValue - value
        val consumedInt = consumed.roundToInt()
        value += consumedInt
        accumulator = consumed - consumedInt

        // Avoid floating-point rounding error
        if (changed) consumed else it
    }

    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit
    ): Unit = scrollableState.scroll(scrollPriority, block)

    override fun dispatchRawDelta(delta: Float): Float =
        scrollableState.dispatchRawDelta(delta)

    override val isScrollInProgress: Boolean
        get() = scrollableState.isScrollInProgress

    /**
     * Scroll to position in pixels with animation.
     *
     * @param value target value in pixels to smooth scroll to, value will be coerced to
     * 0..maxPosition
     * @param animationSpec animation curve for smooth scroll animation
     */
    suspend fun animateScrollTo(
        value: Int,
        animationSpec: AnimationSpec<Float> = SpringSpec()
    ) {
        this.animateScrollBy((value - this.value).toFloat(), animationSpec)
    }

    /**
     * Instantly jump to the given position in pixels.
     *
     * Cancels the currently running scroll, if any, and suspends until the cancellation is
     * complete.
     *
     * @see animateScrollTo for an animated version
     *
     * @param value number of pixels to scroll by
     * @return the amount of scroll consumed
     */
    suspend fun scrollTo(value: Int): Float = this.scrollBy((value - this.value).toFloat())

    companion object {
        /**
         * The default [Saver] implementation for [ScrollState].
         */
        val Saver: Saver<ScrollState, *> = Saver(
            save = { it.value },
            restore = { ScrollState(it) }
        )
    }
}

/**
 * Modify element to allow to scroll vertically when height of the content is bigger than max
 * constraints allow.
 *
 * @sample androidx.compose.foundation.samples.VerticalScrollExample
 *
 * In order to use this modifier, you need to create and own [ScrollState]
 * @see [rememberScrollState]
 *
 * @param state state of the scroll
 * @param enabled whether or not scrolling via touch input is enabled
 * @param flingBehavior logic describing fling behavior when drag has finished with velocity. If
 * `null`, default from [ScrollableDefaults.flingBehavior] will be used.
 * @param reverseScrolling reverse the direction of scrolling, when `true`, 0 [ScrollState.value]
 * will mean bottom, when `false`, 0 [ScrollState.value] will mean top
 */
fun Modifier.verticalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
) = scroll(
    state = state,
    isScrollable = enabled,
    reverseScrolling = reverseScrolling,
    flingBehavior = flingBehavior,
    isVertical = true
)

/**
 * Modify element to allow to scroll horizontally when width of the content is bigger than max
 * constraints allow.
 *
 * @sample androidx.compose.foundation.samples.HorizontalScrollSample
 *
 * In order to use this modifier, you need to create and own [ScrollState]
 * @see [rememberScrollState]
 *
 * @param state state of the scroll
 * @param enabled whether or not scrolling via touch input is enabled
 * @param flingBehavior logic describing fling behavior when drag has finished with velocity. If
 * `null`, default from [ScrollableDefaults.flingBehavior] will be used.
 * @param reverseScrolling reverse the direction of scrolling, when `true`, 0 [ScrollState.value]
 * will mean right, when `false`, 0 [ScrollState.value] will mean left
 */
fun Modifier.horizontalScroll(
    state: ScrollState,
    enabled: Boolean = true,
    flingBehavior: FlingBehavior? = null,
    reverseScrolling: Boolean = false
) = scroll(
    state = state,
    isScrollable = enabled,
    reverseScrolling = reverseScrolling,
    flingBehavior = flingBehavior,
    isVertical = false
)

private fun Modifier.scroll(
    state: ScrollState,
    reverseScrolling: Boolean,
    flingBehavior: FlingBehavior?,
    isScrollable: Boolean,
    isVertical: Boolean
) = composed(
    factory = {
        val coroutineScope = rememberCoroutineScope()
        val semantics = Modifier.semantics {
            if (isScrollable) {
                val accessibilityScrollState = ScrollAxisRange(
                    value = { state.value.toFloat() },
                    maxValue = { state.maxValue.toFloat() },
                    reverseScrolling = reverseScrolling
                )
                if (isVertical) {
                    this.verticalScrollAxisRange = accessibilityScrollState
                } else {
                    this.horizontalScrollAxisRange = accessibilityScrollState
                }
                // when b/156389287 is fixed, this should be proper scrollTo with reverse handling
                scrollBy(
                    action = { x: Float, y: Float ->
                        coroutineScope.launch {
                            if (isVertical) {
                                (state as ScrollableState).scrollBy(y)
                            } else {
                                (state as ScrollableState).scrollBy(x)
                            }
                        }
                        return@scrollBy true
                    }
                )
            }
        }
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        val scrolling = Modifier.scrollable(
            orientation = if (isVertical) Orientation.Vertical else Orientation.Horizontal,
            // reverse scroll by default, to have "natural" gesture that goes reversed to layout
            // if rtl and horizontal, do not reverse to make it right-to-left
            reverseDirection = if (!isVertical && isRtl) reverseScrolling else !reverseScrolling,
            enabled = isScrollable,
            interactionSource = state.internalInteractionSource,
            flingBehavior = flingBehavior,
            state = state
        )
        val layout = ScrollingLayoutModifier(state, reverseScrolling, isVertical)
        semantics.then(scrolling).clipToBounds().then(layout)
    },
    inspectorInfo = debugInspectorInfo {
        name = "scroll"
        properties["state"] = state
        properties["reverseScrolling"] = reverseScrolling
        properties["flingBehavior"] = flingBehavior
        properties["isScrollable"] = isScrollable
        properties["isVertical"] = isVertical
    }
)

private data class ScrollingLayoutModifier(
    val scrollerState: ScrollState,
    val isReversed: Boolean,
    val isVertical: Boolean
) : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        constraints.assertNotNestingScrollableContainers(isVertical)
        val childConstraints = constraints.copy(
            maxHeight = if (isVertical) Constraints.Infinity else constraints.maxHeight,
            maxWidth = if (isVertical) constraints.maxWidth else Constraints.Infinity
        )
        val placeable = measurable.measure(childConstraints)
        val width = placeable.width.coerceAtMost(constraints.maxWidth)
        val height = placeable.height.coerceAtMost(constraints.maxHeight)
        val scrollHeight = placeable.height - height
        val scrollWidth = placeable.width - width
        val side = if (isVertical) scrollHeight else scrollWidth
        return layout(width, height) {
            scrollerState.maxValue = side
            val scroll = scrollerState.value.coerceIn(0, side)
            val absScroll = if (isReversed) scroll - side else -scroll
            val xOffset = if (isVertical) 0 else absScroll
            val yOffset = if (isVertical) absScroll else 0
            placeable.placeRelativeWithLayer(xOffset, yOffset)
        }
    }
}

internal fun Constraints.assertNotNestingScrollableContainers(isVertical: Boolean) {
    if (isVertical) {
        check(maxHeight != Constraints.Infinity) {
            "Nesting scrollable in the same direction layouts like ScrollableContainer and " +
                "LazyColumn is not allowed. If you want to add a header before the list of" +
                " items please take a look on LazyColumn component which has a DSL api which" +
                " allows to first add a header via item() function and then the list of " +
                "items via items()."
        }
    } else {
        check(maxWidth != Constraints.Infinity) {
            "Nesting scrollable in the same direction layouts like ScrollableRow and " +
                "LazyRow is not allowed. If you want to add a header before the list of " +
                "items please take a look on LazyRow component which has a DSL api which " +
                "allows to first add a fixed element via item() function and then the " +
                "list of items via items()."
        }
    }
}

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

package androidx.compose.foundation.lazy.list

import androidx.compose.animation.core.FloatSpringSpec
import androidx.compose.foundation.AutoTestFrameClock
import androidx.compose.foundation.BaseLazyLayoutTestWithOrientation.Companion.FrameDuration
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
class LazyScrollTest(private val orientation: Orientation) {
    @get:Rule
    val rule = createComposeRule()

    private val vertical: Boolean
        get() = orientation == Orientation.Vertical

    private val itemsCount = 20
    private lateinit var state: LazyListState

    private val itemSizePx = 100
    private var itemSizeDp = Dp.Unspecified
    private var containerSizeDp = Dp.Unspecified

    lateinit var scope: CoroutineScope

    @Before
    fun setup() {
        with(rule.density) {
            itemSizeDp = itemSizePx.toDp()
            containerSizeDp = itemSizeDp * 3
        }
    }

    private fun testScroll(spacingPx: Int = 0, assertBlock: suspend () -> Unit) {
        rule.setContent {
            state = rememberLazyListState()
            scope = rememberCoroutineScope()
            TestContent(with(rule.density) { spacingPx.toDp() })
        }
        runBlocking {
            assertBlock()
        }
    }

    @Test
    fun setupWorks() = testScroll {
        assertThat(state.firstVisibleItemIndex).isEqualTo(0)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(0)
    }

    @Test
    fun scrollToItem() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.scrollToItem(3)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(3)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(0)
    }

    @Test
    fun scrollToItemWithOffset() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.scrollToItem(3, 10)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(3)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(10)
    }

    @Test
    fun scrollToItemWithNegativeOffset() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.scrollToItem(3, -10)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(2)
        val item3Offset = state.layoutInfo.visibleItemsInfo.first { it.index == 3 }.offset
        assertThat(item3Offset).isEqualTo(10)
    }

    @Test
    fun scrollToItemWithPositiveOffsetLargerThanAvailableSize() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.scrollToItem(itemsCount - 3, 10)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(itemsCount - 3)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(0) // not 10
    }

    @Test
    fun scrollToItemWithNegativeOffsetLargerThanAvailableSize() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.scrollToItem(1, -(itemSizePx + 10))
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(0)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(0) // not -10
    }

    @Test
    fun scrollToItemWithIndexLargerThanItemsCount() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.scrollToItem(itemsCount + 2)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(itemsCount - 3)
    }

    @Test
    fun animateScrollBy() = testScroll {
        val scrollDistance = 320

        val expectedIndex = scrollDistance / itemSizePx // resolves to 3
        val expectedOffset = scrollDistance % itemSizePx // resolves to 20px

        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.animateScrollBy(scrollDistance.toFloat())
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(expectedIndex)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(expectedOffset)
    }

    @Test
    fun animateScrollToItem() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.animateScrollToItem(5, 10)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(5)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(10)
    }

    @Test
    fun animateScrollToItemWithOffset() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.animateScrollToItem(3, 10)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(3)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(10)
    }

    @Test
    fun animateScrollToItemWithNegativeOffset() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.animateScrollToItem(3, -10)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(2)
        val item3Offset = state.layoutInfo.visibleItemsInfo.first { it.index == 3 }.offset
        assertThat(item3Offset).isEqualTo(10)
    }

    @Test
    fun animateScrollToItemWithPositiveOffsetLargerThanAvailableSize() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.animateScrollToItem(itemsCount - 3, 10)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(itemsCount - 3)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(0) // not 10
    }

    @Test
    fun animateScrollToItemWithNegativeOffsetLargerThanAvailableSize() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.animateScrollToItem(1, -(itemSizePx + 10))
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(0)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(0) // not -10
    }

    @Test
    fun animateScrollToItemWithIndexLargerThanItemsCount() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.animateScrollToItem(itemsCount + 2)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(itemsCount - 3)
    }

    @Test
    fun animatePerFrameForwardToVisibleItem() = testScroll {
        assertSpringAnimation(toIndex = 2)
    }

    @Test
    fun animatePerFrameForwardToVisibleItemWithOffset() = testScroll {
        assertSpringAnimation(toIndex = 2, toOffset = 35)
    }

    @Test
    fun animatePerFrameForwardToNotVisibleItem() = testScroll {
        assertSpringAnimation(toIndex = 8)
    }

    @Test
    fun animatePerFrameForwardToNotVisibleItemWithOffset() = testScroll {
        assertSpringAnimation(toIndex = 10, toOffset = 35)
    }

    @Test
    fun animatePerFrameBackward() = testScroll {
        assertSpringAnimation(toIndex = 1, fromIndex = 6)
    }

    @Test
    fun animatePerFrameBackwardWithOffset() = testScroll {
        assertSpringAnimation(toIndex = 1, fromIndex = 5, fromOffset = 58)
    }

    @Test
    fun animatePerFrameBackwardWithInitialOffset() = testScroll {
        assertSpringAnimation(toIndex = 0, toOffset = 20, fromIndex = 8)
    }

    @Test
    fun animateScrollToItemWithOffsetLargerThanItemSize_forward() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.animateScrollToItem(10, -itemSizePx * 3)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(7)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(0)
    }

    @Test
    fun animateScrollToItemWithOffsetLargerThanItemSize_backward() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.scrollToItem(10)
            state.animateScrollToItem(0, itemSizePx * 3)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(3)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(0)
    }

    @Test
    fun canScrollForward() = testScroll {
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(0)
        assertThat(state.canScrollForward).isTrue()
        assertThat(state.canScrollBackward).isFalse()
    }

    @Test
    fun canScrollBackward() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.scrollToItem(itemsCount)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(itemsCount - 3)
        assertThat(state.canScrollForward).isFalse()
        assertThat(state.canScrollBackward).isTrue()
    }

    @Test
    fun canScrollForwardAndBackward() = testScroll {
        withContext(Dispatchers.Main + AutoTestFrameClock()) {
            state.scrollToItem(1)
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(1)
        assertThat(state.canScrollForward).isTrue()
        assertThat(state.canScrollBackward).isTrue()
    }

    @Test
    fun animatePerFrameWithSpacing() = testScroll(spacingPx = 10) {
        assertSpringAnimation(toIndex = 8, spacingPx = 10)
    }

    @Test
    fun animatePerFrameWithNegativeSpacing() = testScroll(spacingPx = -10) {
        assertSpringAnimation(toIndex = 8, spacingPx = -10)
    }

    @Test
    fun animatePerFrameBackwardWithSpacing() = testScroll(spacingPx = 10) {
        assertSpringAnimation(toIndex = 1, fromIndex = 6, spacingPx = 10)
    }

    @Test
    fun animatePerFrameBackwardWithNegativeSpacing() = testScroll(spacingPx = -10) {
        assertSpringAnimation(toIndex = 1, fromIndex = 6, spacingPx = -10)
    }

    private fun assertSpringAnimation(
        toIndex: Int,
        toOffset: Int = 0,
        fromIndex: Int = 0,
        fromOffset: Int = 0,
        spacingPx: Int = 0
    ) {
        if (fromIndex != 0 || fromOffset != 0) {
            rule.runOnIdle {
                runBlocking {
                    state.scrollToItem(fromIndex, fromOffset)
                }
            }
        }
        rule.waitForIdle()

        assertThat(state.firstVisibleItemIndex).isEqualTo(fromIndex)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(fromOffset)

        rule.mainClock.autoAdvance = false

        scope.launch {
            state.animateScrollToItem(toIndex, toOffset)
        }

        while (!state.isScrollInProgress) {
            Thread.sleep(5)
        }

        val itemSizeWSpacing = itemSizePx + spacingPx
        val startOffset = (fromIndex * itemSizeWSpacing + fromOffset).toFloat()
        val endOffset = (toIndex * itemSizeWSpacing + toOffset).toFloat()
        val spec = FloatSpringSpec()

        val duration =
            TimeUnit.NANOSECONDS.toMillis(spec.getDurationNanos(startOffset, endOffset, 0f))
        rule.mainClock.advanceTimeByFrame()
        var expectedTime = rule.mainClock.currentTime
        for (i in 0..duration step FrameDuration) {
            val nanosTime = TimeUnit.MILLISECONDS.toNanos(i)
            val expectedValue =
                spec.getValueFromNanos(nanosTime, startOffset, endOffset, 0f)
            val actualValue = (
                state.firstVisibleItemIndex * itemSizeWSpacing + state.firstVisibleItemScrollOffset
                )
            assertWithMessage(
                "On animation frame at $i index=${state.firstVisibleItemIndex} " +
                    "offset=${state.firstVisibleItemScrollOffset} expectedValue=$expectedValue"
            ).that(actualValue).isEqualTo(expectedValue.roundToInt(), tolerance = 1)

            rule.mainClock.advanceTimeBy(FrameDuration)
            expectedTime += FrameDuration
            assertThat(expectedTime).isEqualTo(rule.mainClock.currentTime)
            rule.waitForIdle()
        }
        assertThat(state.firstVisibleItemIndex).isEqualTo(toIndex)
        assertThat(state.firstVisibleItemScrollOffset).isEqualTo(toOffset)
    }

    @Composable
    private fun TestContent(spacingDp: Dp) {
        if (vertical) {
            LazyColumn(
                Modifier.height(containerSizeDp),
                state,
                verticalArrangement = Arrangement.spacedBy(spacingDp)
            ) {
                items(itemsCount) {
                    ItemContent()
                }
            }
        } else {
            LazyRow(
                Modifier.width(containerSizeDp), state,
                horizontalArrangement = Arrangement.spacedBy(spacingDp)
            ) {
                items(itemsCount) {
                    ItemContent()
                }
            }
        }
    }

    @Composable
    private fun ItemContent() {
        val modifier = if (vertical) {
            Modifier.height(itemSizeDp)
        } else {
            Modifier.width(itemSizeDp)
        }
        Spacer(modifier)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun params() = arrayOf(Orientation.Vertical, Orientation.Horizontal)
    }
}
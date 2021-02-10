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

import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.advanceClockMillis
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.testutils.assertPixels
import androidx.compose.testutils.runBlockingWithManualClock
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.GestureScope
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ScrollTest {

    @get:Rule
    val rule = createComposeRule()

    private val scrollerTag = "ScrollerTest"

    private val defaultCrossAxisSize = 45
    private val defaultMainAxisSize = 40
    private val defaultCellSize = 5

    private val colors = listOf(
        Color(red = 0xFF, green = 0, blue = 0, alpha = 0xFF),
        Color(red = 0xFF, green = 0xA5, blue = 0, alpha = 0xFF),
        Color(red = 0xFF, green = 0xFF, blue = 0, alpha = 0xFF),
        Color(red = 0xA5, green = 0xFF, blue = 0, alpha = 0xFF),
        Color(red = 0, green = 0xFF, blue = 0, alpha = 0xFF),
        Color(red = 0, green = 0xFF, blue = 0xA5, alpha = 0xFF),
        Color(red = 0, green = 0, blue = 0xFF, alpha = 0xFF),
        Color(red = 0xA5, green = 0, blue = 0xFF, alpha = 0xFF)
    )

    @Before
    fun before() {
        isDebugInspectorInfoEnabled = true
    }

    @After
    fun after() {
        isDebugInspectorInfoEnabled = false
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun verticalScroller_SmallContent() {
        val height = 40

        composeVerticalScroller(height = height)

        validateVerticalScroller(height = height)
    }

    @Test
    fun verticalScroller_SmallContent_Unscrollable() = runBlocking {
        val scrollState = ScrollState(initial = 0)

        composeVerticalScroller(scrollState)

        rule.runOnIdle {
            assertTrue(scrollState.maxValue == 0)
        }
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun verticalScroller_LargeContent_NoScroll() {
        val height = 30

        composeVerticalScroller(height = height)

        validateVerticalScroller(height = height)
    }

    @OptIn(ExperimentalTestApi::class)
    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun verticalScroller_LargeContent_ScrollToEnd() = runBlocking {
        val scrollState = ScrollState(initial = 0)
        val height = 30
        val scrollDistance = 10

        composeVerticalScroller(scrollState, height = height)

        validateVerticalScroller(height = height)

        rule.awaitIdle()
        assertEquals(scrollDistance, scrollState.maxValue)
        scrollState.scrollTo(scrollDistance)

        rule.runOnIdle {} // Just so the block below is correct
        validateVerticalScroller(offset = scrollDistance, height = height)
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun verticalScroller_Reversed() = runBlocking {
        val scrollState = ScrollState(initial = 0)
        val height = 30
        val expectedOffset = defaultCellSize * colors.size - height

        composeVerticalScroller(scrollState, height = height, isReversed = true)

        validateVerticalScroller(offset = expectedOffset, height = height)
    }

    @OptIn(ExperimentalTestApi::class)
    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun verticalScroller_LargeContent_Reversed_ScrollToEnd() = runBlocking {
        val scrollState = ScrollState(initial = 0)
        val height = 20
        val scrollDistance = 10
        val expectedOffset = defaultCellSize * colors.size - height - scrollDistance

        composeVerticalScroller(scrollState, height = height, isReversed = true)

        rule.awaitIdle()
        scrollState.scrollTo(scrollDistance)

        rule.awaitIdle()
        validateVerticalScroller(offset = expectedOffset, height = height)
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun horizontalScroller_SmallContent() {
        val width = 40

        composeHorizontalScroller(width = width)

        validateHorizontalScroller(width = width)
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun horizontalScroller_rtl_SmallContent() {
        val width = 40

        composeHorizontalScroller(width = width, isRtl = true)

        validateHorizontalScroller(width = width, checkInRtl = true)
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun horizontalScroller_LargeContent_NoScroll() {
        val width = 30

        composeHorizontalScroller(width = width)

        validateHorizontalScroller(width = width)
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun horizontalScroller_rtl_LargeContent_NoScroll() {
        val width = 30

        composeHorizontalScroller(width = width, isRtl = true)

        validateHorizontalScroller(width = width, checkInRtl = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun horizontalScroller_LargeContent_ScrollToEnd() = runBlocking {
        val width = 30
        val scrollDistance = 10

        val scrollState = ScrollState(initial = 0)

        composeHorizontalScroller(scrollState, width = width)

        validateHorizontalScroller(width = width)

        rule.awaitIdle()
        assertEquals(scrollDistance, scrollState.maxValue)
        scrollState.scrollTo(scrollDistance)

        rule.runOnIdle {} // Just so the block below is correct
        validateHorizontalScroller(offset = scrollDistance, width = width)
    }

    @OptIn(ExperimentalTestApi::class)
    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun horizontalScroller_rtl_LargeContent_ScrollToEnd() = runBlocking {
        val width = 30
        val scrollDistance = 10

        val scrollState = ScrollState(initial = 0)

        composeHorizontalScroller(scrollState, width = width, isRtl = true)

        validateHorizontalScroller(width = width, checkInRtl = true)

        rule.awaitIdle()
        assertEquals(scrollDistance, scrollState.maxValue)
        scrollState.scrollTo(scrollDistance)

        rule.awaitIdle()
        validateHorizontalScroller(offset = scrollDistance, width = width, checkInRtl = true)
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun horizontalScroller_reversed() = runBlocking {
        val scrollState = ScrollState(initial = 0)
        val width = 30
        val expectedOffset = defaultCellSize * colors.size - width

        composeHorizontalScroller(scrollState, width = width, isReversed = true)

        validateHorizontalScroller(offset = expectedOffset, width = width)
    }

    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun horizontalScroller_rtl_reversed() = runBlocking {
        val scrollState = ScrollState(initial = 0)
        val width = 30
        val expectedOffset = defaultCellSize * colors.size - width

        composeHorizontalScroller(scrollState, width = width, isReversed = true, isRtl = true)

        validateHorizontalScroller(offset = expectedOffset, width = width, checkInRtl = true)
    }

    @OptIn(ExperimentalTestApi::class)
    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun horizontalScroller_LargeContent_Reversed_ScrollToEnd() = runBlocking {
        val width = 30
        val scrollDistance = 10

        val scrollState = ScrollState(initial = 0)

        val expectedOffset = defaultCellSize * colors.size - width - scrollDistance

        composeHorizontalScroller(scrollState, width = width, isReversed = true)

        rule.awaitIdle()
        scrollState.scrollTo(scrollDistance)

        rule.runOnIdle {} // Just so the block below is correct
        validateHorizontalScroller(offset = expectedOffset, width = width)
    }

    @OptIn(ExperimentalTestApi::class)
    @SdkSuppress(minSdkVersion = 26)
    @Test
    fun horizontalScroller_rtl_LargeContent_Reversed_ScrollToEnd() = runBlocking {
        val width = 30
        val scrollDistance = 10

        val scrollState = ScrollState(initial = 0)

        val expectedOffset = defaultCellSize * colors.size - width - scrollDistance

        composeHorizontalScroller(scrollState, width = width, isReversed = true, isRtl = true)

        rule.awaitIdle()

        scrollState.scrollTo(scrollDistance)

        rule.runOnIdle {} // Just so the block below is correct
        validateHorizontalScroller(offset = expectedOffset, width = width, checkInRtl = true)
    }

    @Test
    fun verticalScroller_scrollTo_scrollForward() {
        createScrollableContent(isVertical = true)

        rule.onNodeWithText("50")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun horizontalScroller_scrollTo_scrollForward() {
        createScrollableContent(isVertical = false)

        rule.onNodeWithText("50")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Ignore("Unignore when b/156389287 is fixed for proper reverse and rtl delegation")
    @Test
    fun horizontalScroller_rtl_scrollTo_scrollForward() {
        createScrollableContent(isVertical = false, isRtl = true)

        rule.onNodeWithText("50")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Ignore("Unignore when b/156389287 is fixed for proper reverse delegation")
    @Test
    fun verticalScroller_reversed_scrollTo_scrollForward(): Unit = runBlocking {
        createScrollableContent(
            isVertical = true,
            scrollState = ScrollState(initial = 0),
            isReversed = true
        )

        rule.onNodeWithText("50")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Ignore("Unignore when b/156389287 is fixed for proper reverse and rtl delegation")
    @Test
    fun horizontalScroller_reversed_scrollTo_scrollForward(): Unit = runBlocking {
        createScrollableContent(
            isVertical = false,
            scrollState = ScrollState(initial = 0),
            isReversed = true
        )

        rule.onNodeWithText("50")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Ignore("When b/157687898 is fixed, performScrollTo must be adjusted to use semantic bounds")
    fun verticalScroller_scrollTo_scrollBack() {
        createScrollableContent(isVertical = true)

        rule.onNodeWithText("50")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()

        rule.onNodeWithText("20")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @Ignore("When b/157687898 is fixed, performScrollTo must be adjusted to use semantic bounds")
    fun horizontalScroller_scrollTo_scrollBack() {
        createScrollableContent(isVertical = false)

        rule.onNodeWithText("50")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()

        rule.onNodeWithText("20")
            .assertIsNotDisplayed()
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    @LargeTest
    fun verticalScroller_swipeUp_swipeDown() {
        swipeScrollerAndBack(true, GestureScope::swipeUp, GestureScope::swipeDown)
    }

    @Test
    @LargeTest
    fun horizontalScroller_swipeLeft_swipeRight() {
        swipeScrollerAndBack(false, GestureScope::swipeLeft, GestureScope::swipeRight)
    }

    @Test
    @LargeTest
    fun horizontalScroller_rtl_swipeLeft_swipeRight() {
        swipeScrollerAndBack(
            false,
            GestureScope::swipeRight,
            GestureScope::swipeLeft,
            isRtl = true
        )
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scroller_coerce_whenScrollTo() = runBlocking {
        val scrollState = ScrollState(initial = 0)

        createScrollableContent(isVertical = true, scrollState = scrollState)

        rule.awaitIdle()

        assertThat(scrollState.value).isEqualTo(0)
        assertThat(scrollState.maxValue).isGreaterThan(0)

        scrollState.scrollTo(-100)
        assertThat(scrollState.value).isEqualTo(0)

        scrollState.scrollBy(-100f)
        assertThat(scrollState.value).isEqualTo(0)

        scrollState.scrollTo(scrollState.maxValue)
        assertThat(scrollState.value).isEqualTo(scrollState.maxValue)

        scrollState.scrollTo(scrollState.maxValue + 1000)
        assertThat(scrollState.value).isEqualTo(scrollState.maxValue)

        scrollState.scrollBy(100f)
        assertThat(scrollState.value).isEqualTo(scrollState.maxValue)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun verticalScroller_LargeContent_coerceWhenMaxChanges() = runBlocking {
        val scrollState = ScrollState(initial = 0)
        val itemCount = mutableStateOf(100)
        rule.setContent {
            Box {
                Column(
                    Modifier
                        .size(100.dp)
                        .testTag(scrollerTag)
                        .verticalScroll(scrollState)
                ) {
                    for (i in 0..itemCount.value) {
                        BasicText(i.toString())
                    }
                }
            }
        }

        rule.awaitIdle()

        assertThat(scrollState.value).isEqualTo(0)
        assertThat(scrollState.maxValue).isGreaterThan(0)
        val max = scrollState.maxValue

        scrollState.scrollTo(max)
        itemCount.value -= 2

        rule.awaitIdle()
        val newMax = scrollState.maxValue
        assertThat(newMax).isLessThan(max)
        assertThat(scrollState.value).isEqualTo(newMax)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scroller_coerce_whenScrollSmoothTo() = runBlocking {
        val scrollState = ScrollState(initial = 0)

        createScrollableContent(isVertical = true, scrollState = scrollState)

        rule.awaitIdle()
        assertThat(scrollState.value).isEqualTo(0)
        assertThat(scrollState.maxValue).isGreaterThan(0)
        val max = scrollState.maxValue

        scrollState.animateScrollTo(-100)
        assertThat(scrollState.value).isEqualTo(0)

        scrollState.animateScrollBy(-100f)
        assertThat(scrollState.value).isEqualTo(0)

        scrollState.animateScrollTo(scrollState.maxValue)
        assertThat(scrollState.value).isEqualTo(max)

        scrollState.animateScrollTo(scrollState.maxValue + 1000)
        assertThat(scrollState.value).isEqualTo(max)

        scrollState.animateScrollBy(100f)
        assertThat(scrollState.value).isEqualTo(max)
    }

    @Test
    fun scroller_whenFling_stopsByTouchDown() = runBlockingWithManualClock { clock ->
        val scrollState = ScrollState(initial = 0)

        createScrollableContent(isVertical = true, scrollState = scrollState)

        assertThat(scrollState.value).isEqualTo(0)
        assertThat(scrollState.isScrollInProgress).isEqualTo(false)

        rule.onNodeWithTag(scrollerTag)
            .performGesture { swipeUp() }

        clock.advanceClockMillis(100)
        assertThat(scrollState.isScrollInProgress).isEqualTo(true)

        rule.onNodeWithTag(scrollerTag)
            .performGesture { down(center) }

        assertThat(scrollState.isScrollInProgress).isEqualTo(false)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun scroller_restoresScrollerPosition() = runBlocking {
        val restorationTester = StateRestorationTester(rule)
        var scrollState: ScrollState? = null

        restorationTester.setContent {
            scrollState = rememberScrollState()
            Column(Modifier.verticalScroll(scrollState!!)) {
                repeat(50) {
                    Box(Modifier.height(100.dp))
                }
            }
        }

        rule.awaitIdle()
        scrollState!!.scrollTo(70)
        scrollState = null

        restorationTester.emulateSavedInstanceStateRestore()

        rule.runOnIdle {
            assertThat(scrollState!!.value).isEqualTo(70)
        }
    }

    private fun swipeScrollerAndBack(
        isVertical: Boolean,
        firstSwipe: GestureScope.() -> Unit,
        secondSwipe: GestureScope.() -> Unit,
        isRtl: Boolean = false
    ) = runBlockingWithManualClock { clock ->
        val scrollState = ScrollState(initial = 0)

        createScrollableContent(isVertical, scrollState = scrollState, isRtl = isRtl)

        rule.runOnIdle {
            assertThat(scrollState.value).isEqualTo(0)
        }

        rule.onNodeWithTag(scrollerTag)
            .performGesture { firstSwipe() }

        rule.runOnIdle {
            clock.advanceClockMillis(5000)
        }

        rule.onNodeWithTag(scrollerTag)
            .awaitScrollAnimation(scrollState)

        val scrolledValue = rule.runOnIdle {
            scrollState.value
        }
        assertThat(scrolledValue).isGreaterThan(0)

        rule.onNodeWithTag(scrollerTag)
            .performGesture { secondSwipe() }

        rule.runOnIdle {
            clock.advanceClockMillis(5000)
        }

        rule.onNodeWithTag(scrollerTag)
            .awaitScrollAnimation(scrollState)

        rule.runOnIdle {
            assertThat(scrollState.value).isLessThan(scrolledValue)
        }
    }

    private fun composeVerticalScroller(
        scrollState: ScrollState? = null,
        isReversed: Boolean = false,
        width: Int = defaultCrossAxisSize,
        height: Int = defaultMainAxisSize,
        rowHeight: Int = defaultCellSize
    ) = runBlocking {
        val resolvedState = scrollState ?: ScrollState(initial = 0)
        // We assume that the height of the device is more than 45 px
        with(rule.density) {
            rule.setContent {
                Box {
                    Column(
                        modifier = Modifier
                            .size(width.toDp(), height.toDp())
                            .testTag(scrollerTag)
                            .verticalScroll(
                                resolvedState,
                                reverseScrolling = isReversed
                            )
                    ) {
                        colors.forEach { color ->
                            Box(
                                Modifier
                                    .size(width.toDp(), rowHeight.toDp())
                                    .background(color)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun composeHorizontalScroller(
        scrollState: ScrollState? = null,
        isReversed: Boolean = false,
        width: Int = defaultMainAxisSize,
        height: Int = defaultCrossAxisSize,
        isRtl: Boolean = false
    ) = runBlocking {
        val resolvedState = scrollState ?: ScrollState(initial = 0)
        // We assume that the height of the device is more than 45 px
        with(rule.density) {
            rule.setContent {
                val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                CompositionLocalProvider(LocalLayoutDirection provides direction) {
                    Box {
                        Row(
                            modifier = Modifier
                                .size(width.toDp(), height.toDp())
                                .testTag(scrollerTag)
                                .horizontalScroll(
                                    resolvedState,
                                    reverseScrolling = isReversed
                                )
                        ) {
                            colors.forEach { color ->
                                Box(
                                    Modifier
                                        .size(defaultCellSize.toDp(), height.toDp())
                                        .background(color)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @RequiresApi(api = 26)
    private fun validateVerticalScroller(
        offset: Int = 0,
        width: Int = 45,
        height: Int = 40,
        rowHeight: Int = 5
    ) {
        rule.onNodeWithTag(scrollerTag)
            .captureToImage()
            .assertPixels(expectedSize = IntSize(width, height)) { pos ->
                val colorIndex = (offset + pos.y) / rowHeight
                colors[colorIndex]
            }
    }

    @RequiresApi(api = 26)
    private fun validateHorizontalScroller(
        offset: Int = 0,
        width: Int = 40,
        height: Int = 45,
        checkInRtl: Boolean = false
    ) {
        val scrollerWidth = colors.size * defaultCellSize
        val absoluteOffset = if (checkInRtl) scrollerWidth - width - offset else offset
        rule.onNodeWithTag(scrollerTag)
            .captureToImage()
            .assertPixels(expectedSize = IntSize(width, height)) { pos ->
                val colorIndex = (absoluteOffset + pos.x) / defaultCellSize
                if (checkInRtl) colors[colors.size - 1 - colorIndex] else colors[colorIndex]
            }
    }

    private fun createScrollableContent(
        isVertical: Boolean,
        itemCount: Int = 100,
        width: Dp = 100.dp,
        height: Dp = 100.dp,
        isReversed: Boolean = false,
        scrollState: ScrollState? = null,
        isRtl: Boolean = false
    ) = runBlocking {
        val resolvedState = scrollState ?: ScrollState(initial = 0)
        rule.setContent {
            val content = @Composable {
                repeat(itemCount) {
                    BasicText(text = "$it")
                }
            }
            Box {
                Box(
                    Modifier.size(width, height).background(Color.White)
                ) {
                    if (isVertical) {
                        Column(
                            Modifier
                                .testTag(scrollerTag)
                                .verticalScroll(
                                    resolvedState,
                                    reverseScrolling = isReversed
                                )
                        ) {
                            content()
                        }
                    } else {
                        val direction = if (isRtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                        CompositionLocalProvider(LocalLayoutDirection provides direction) {
                            Row(
                                Modifier.testTag(scrollerTag)
                                    .horizontalScroll(
                                        resolvedState,
                                        reverseScrolling = isReversed
                                    )
                            ) {
                                content()
                            }
                        }
                    }
                }
            }
        }
    }

    // TODO(b/147291885): This should not be needed in the future.
    private fun SemanticsNodeInteraction.awaitScrollAnimation(
        scroller: ScrollState
    ): SemanticsNodeInteraction {
        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        handler.post(object : Runnable {
            override fun run() {
                if (scroller.isScrollInProgress) {
                    handler.post(this)
                } else {
                    latch.countDown()
                }
            }
        })
        assertWithMessage("Scroll didn't finish after 20 seconds")
            .that(latch.await(20, TimeUnit.SECONDS)).isTrue()
        return this
    }

    @Test
    fun testInspectorValue() = runBlocking {
        val state = ScrollState(initial = 0)
        rule.setContent {
            val modifier = Modifier.verticalScroll(state) as InspectableValue
            assertThat(modifier.nameFallback).isEqualTo("scroll")
            assertThat(modifier.valueOverride).isNull()
            assertThat(modifier.inspectableElements.map { it.name }.asIterable()).containsExactly(
                "state",
                "reverseScrolling",
                "flingBehavior",
                "isScrollable",
                "isVertical"
            )
        }
    }
}

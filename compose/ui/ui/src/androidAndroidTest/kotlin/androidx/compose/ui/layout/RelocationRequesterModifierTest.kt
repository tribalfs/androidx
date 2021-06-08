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

package androidx.compose.ui.layout

import android.os.Build.VERSION_CODES.O
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.Orientation.Horizontal
import androidx.compose.foundation.gestures.Orientation.Vertical
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.testutils.assertAgainstGolden
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.GOLDEN_UI
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.graphics.Color.Companion.Blue
import androidx.compose.ui.graphics.Color.Companion.Green
import androidx.compose.ui.graphics.Color.Companion.LightGray
import androidx.compose.ui.graphics.Color.Companion.Red
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.screenshot.AndroidXScreenshotTestRule
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalComposeUiApi::class)
@MediumTest
@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = O)
class RelocationRequesterModifierTest(private val orientation: Orientation) {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val screenshotRule = AndroidXScreenshotTestRule(GOLDEN_UI)

    val parentBox = "parent box"

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun initParameters(): Array<Orientation> = arrayOf(Horizontal, Vertical)
    }

    @Test
    fun noScrollableParent_noChange() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        rule.setContent {
            Box(
                Modifier
                    .then(
                        when (orientation) {
                            Horizontal -> Modifier.size(100.dp, 50.dp)
                            Vertical -> Modifier.size(50.dp, 100.dp)
                        }
                    )
                    .testTag(parentBox)
                    .background(LightGray)
            ) {
                Box(
                    Modifier
                        .size(50.dp)
                        .background(Blue)
                        .relocationRequester(relocationRequester)
                )
            }
        }

        // Act.
        rule.runOnIdle { relocationRequester.bringIntoView() }

        // Assert.
        assertScreenshot(if (horizontal) "blueBoxLeft" else "blueBoxTop")
    }

    @Test
    fun noScrollableParent_itemNotVisible_noChange() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        rule.setContent {
            Box(
                Modifier
                    .then(
                        when (orientation) {
                            Horizontal -> Modifier.size(100.dp, 50.dp)
                            Vertical -> Modifier.size(50.dp, 100.dp)
                        }
                    )
                    .testTag(parentBox)
                    .background(LightGray)
            ) {
                Box(
                    Modifier
                        .then(
                            when (orientation) {
                                Horizontal -> Modifier.offset(x = 150.dp)
                                Vertical -> Modifier.offset(y = 150.dp)
                            }
                        )
                        .size(50.dp)
                        .background(Blue)
                        .relocationRequester(relocationRequester)
                )
            }
        }

        // Act.
        rule.runOnIdle { relocationRequester.bringIntoView() }

        // Assert.
        assertScreenshot(if (horizontal) "grayRectangleHorizontal" else "grayRectangleVertical")
    }

    @Test
    fun itemAtLeadingEdge_alreadyVisible_noChange() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        rule.setContent {
            Box(
                Modifier
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal ->
                                Modifier
                                    .size(100.dp, 50.dp)
                                    .horizontalScroll(rememberScrollState())
                            Vertical ->
                                Modifier
                                    .size(50.dp, 100.dp)
                                    .verticalScroll(rememberScrollState())
                        }
                    )
            ) {
                Box(
                    Modifier
                        .size(50.dp)
                        .background(Blue)
                        .relocationRequester(relocationRequester)
                )
            }
        }

        // Act.
        rule.runOnIdle { relocationRequester.bringIntoView() }

        // Assert.
        assertScreenshot(if (horizontal) "blueBoxLeft" else "blueBoxTop")
    }

    @Test
    fun itemAtTrailingEdge_alreadyVisible_noChange() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        rule.setContent {
            Box(
                Modifier
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal ->
                                Modifier
                                    .size(100.dp, 50.dp)
                                    .horizontalScroll(rememberScrollState())
                            Vertical ->
                                Modifier
                                    .size(50.dp, 100.dp)
                                    .verticalScroll(rememberScrollState())
                        }
                    )
            ) {
                Box(
                    Modifier
                        .then(
                            when (orientation) {
                                Horizontal -> Modifier.offset(x = 50.dp)
                                Vertical -> Modifier.offset(y = 50.dp)
                            }
                        )
                        .size(50.dp)
                        .background(Blue)
                        .relocationRequester(relocationRequester)
                )
            }
        }

        // Act.
        rule.runOnIdle { relocationRequester.bringIntoView() }

        // Assert.
        assertScreenshot(if (horizontal) "blueBoxRight" else "blueBoxBottom")
    }

    @Test
    fun itemAtCenter_alreadyVisible_noChange() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        rule.setContent {
            Box(
                Modifier
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal ->
                                Modifier
                                    .size(100.dp, 50.dp)
                                    .horizontalScroll(rememberScrollState())
                            Vertical ->
                                Modifier
                                    .size(50.dp, 100.dp)
                                    .verticalScroll(rememberScrollState())
                        }
                    )
            ) {
                Box(
                    Modifier
                        .then(
                            when (orientation) {
                                Horizontal -> Modifier.offset(x = 25.dp)
                                Vertical -> Modifier.offset(y = 25.dp)
                            }
                        )
                        .size(50.dp)
                        .background(Blue)
                        .relocationRequester(relocationRequester)
                )
            }
        }

        // Act.
        rule.runOnIdle { relocationRequester.bringIntoView() }

        // Assert.
        assertScreenshot(if (horizontal) "blueBoxCenterHorizontal" else "blueBoxCenterVertical")
    }

    @Test
    fun itemBiggerThanParentAtLeadingEdge_alreadyVisible_noChange() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        rule.setContent {
            Box(
                Modifier
                    .size(50.dp)
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal -> Modifier.horizontalScroll(rememberScrollState())
                            Vertical -> Modifier.verticalScroll(rememberScrollState())
                        }
                    )
            ) {
                // Using a multi-colored item to make sure we can assert that the right part of
                // the item is visible.
                RowOrColumn(Modifier.relocationRequester(relocationRequester)) {
                    Box(Modifier.size(50.dp).background(Blue))
                    Box(Modifier.size(50.dp).background(Green))
                    Box(Modifier.size(50.dp).background(Red))
                }
            }
        }

        // Act.
        rule.runOnIdle { relocationRequester.bringIntoView() }

        // Assert.
        assertScreenshot("blueBox")
    }

    @Test
    fun itemBiggerThanParentAtTrailingEdge_alreadyVisible_noChange() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        lateinit var scrollState: ScrollState
        rule.setContent {
            scrollState = rememberScrollState()
            Box(
                Modifier
                    .size(50.dp)
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal -> Modifier.horizontalScroll(scrollState)
                            Vertical -> Modifier.verticalScroll(scrollState)
                        }
                    )
            ) {
                // Using a multi-colored item to make sure we can assert that the right part of
                // the item is visible.
                RowOrColumn(Modifier.relocationRequester(relocationRequester)) {
                    Box(Modifier.size(50.dp).background(Red))
                    Box(Modifier.size(50.dp).background(Green))
                    Box(Modifier.size(50.dp).background(Blue))
                }
            }
        }
        rule.waitForIdle()
        runBlocking {
            scrollState.scrollTo(scrollState.maxValue)
        }
        rule.waitForIdle()

        // Act.
        relocationRequester.bringIntoView()

        // Assert.
        assertScreenshot("blueBox")
    }

    @Test
    fun itemBiggerThanParentAtCenter_alreadyVisible_noChange() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        lateinit var scrollState: ScrollState
        rule.setContent {
            scrollState = rememberScrollState()
            Box(
                Modifier
                    .size(50.dp)
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal -> Modifier.horizontalScroll(scrollState)
                            Vertical -> Modifier.verticalScroll(scrollState)
                        }
                    )
            ) {
                // Using a multi-colored item to make sure we can assert that the right part of
                // the item is visible.
                RowOrColumn(Modifier.relocationRequester(relocationRequester)) {
                    Box(Modifier.size(50.dp).background(Green))
                    Box(Modifier.size(50.dp).background(Blue))
                    Box(Modifier.size(50.dp).background(Red))
                }
            }
        }
        rule.waitForIdle()
        runBlocking {
            scrollState.scrollTo(scrollState.maxValue / 2)
        }
        rule.waitForIdle()

        // Act.
        relocationRequester.bringIntoView()

        // Assert.
        assertScreenshot("blueBox")
    }

    @Test
    fun childBeforeVisibleBounds_parentIsScrolledSoThatLeadingEdgeOfChildIsVisible() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        lateinit var scrollState: ScrollState
        rule.setContent {
            scrollState = rememberScrollState()
            Box(
                Modifier
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal ->
                                Modifier
                                    .size(100.dp, 50.dp)
                                    .horizontalScroll(scrollState)
                            Vertical ->
                                Modifier
                                    .size(50.dp, 100.dp)
                                    .verticalScroll(scrollState)
                        }
                    )
            ) {
                Box(
                    when (orientation) {
                        Horizontal -> Modifier.size(200.dp, 50.dp)
                        Vertical -> Modifier.size(50.dp, 200.dp)
                    }
                ) {
                    Box(
                        Modifier
                            .then(
                                when (orientation) {
                                    Horizontal -> Modifier.offset(x = 50.dp)
                                    Vertical -> Modifier.offset(y = 50.dp)
                                }
                            )
                            .size(50.dp)
                            .background(Blue)
                            .relocationRequester(relocationRequester)
                    )
                }
            }
        }
        rule.waitForIdle()
        runBlocking {
            scrollState.scrollTo(scrollState.maxValue)
        }
        rule.waitForIdle()

        // Act.
        relocationRequester.bringIntoView()

        // Assert.
        assertScreenshot(if (horizontal) "blueBoxLeft" else "blueBoxTop")
    }

    @Test
    fun childAfterVisibleBounds_parentIsScrolledSoThatTrailingEdgeOfChildIsVisible() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        lateinit var scrollState: ScrollState
        rule.setContent {
            scrollState = rememberScrollState()
            Box(
                Modifier
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal ->
                                Modifier
                                    .size(100.dp, 50.dp)
                                    .horizontalScroll(scrollState)
                            Vertical ->
                                Modifier
                                    .size(50.dp, 100.dp)
                                    .verticalScroll(scrollState)
                        }
                    )
            ) {
                Box(
                    when (orientation) {
                        Horizontal -> Modifier.size(200.dp, 50.dp)
                        Vertical -> Modifier.size(50.dp, 200.dp)
                    }
                ) {
                    Box(
                        Modifier
                            .then(
                                when (orientation) {
                                    Horizontal -> Modifier.offset(x = 150.dp)
                                    Vertical -> Modifier.offset(y = 150.dp)
                                }
                            )
                            .size(50.dp)
                            .background(Blue)
                            .relocationRequester(relocationRequester)
                    )
                }
            }
        }
        rule.waitForIdle()
        runBlocking {
            scrollState.scrollTo(scrollState.maxValue)
        }
        rule.waitForIdle()

        // Act.
        relocationRequester.bringIntoView()

        // Assert.
        assertScreenshot(if (horizontal) "blueBoxRight" else "blueBoxBottom")
    }

    @Test
    fun childPartiallyVisible_parentIsScrolledSoThatLeadingEdgeOfChildIsVisible() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        lateinit var scrollState: ScrollState
        rule.setContent {
            scrollState = rememberScrollState()
            Box(
                Modifier
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal ->
                                Modifier
                                    .size(100.dp, 50.dp)
                                    .horizontalScroll(scrollState)
                            Vertical ->
                                Modifier
                                    .size(50.dp, 100.dp)
                                    .verticalScroll(scrollState)
                        }
                    )
            ) {
                Box(Modifier.size(200.dp)) {
                    Box(
                        Modifier
                            .then(
                                when (orientation) {
                                    Horizontal -> Modifier.offset(x = 25.dp)
                                    Vertical -> Modifier.offset(y = 25.dp)
                                }
                            )
                            .size(50.dp)
                            .background(Blue)
                            .relocationRequester(relocationRequester)
                    )
                }
            }
        }
        rule.waitForIdle()
        runBlocking {
            scrollState.scrollTo(scrollState.maxValue / 2)
        }
        rule.waitForIdle()

        // Act.
        relocationRequester.bringIntoView()

        // Assert.
        assertScreenshot(if (horizontal) "blueBoxLeft" else "blueBoxTop")
    }

    @Test
    fun childPartiallyVisible_parentIsScrolledSoThatTrailingEdgeOfChildIsVisible() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        lateinit var scrollState: ScrollState
        rule.setContent {
            scrollState = rememberScrollState()
            Box(
                Modifier
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal ->
                                Modifier
                                    .size(100.dp, 50.dp)
                                    .horizontalScroll(scrollState)
                            Vertical ->
                                Modifier
                                    .size(50.dp, 100.dp)
                                    .verticalScroll(scrollState)
                        }
                    )
            ) {
                Box(
                    when (orientation) {
                        Horizontal -> Modifier.size(200.dp, 50.dp)
                        Vertical -> Modifier.size(50.dp, 200.dp)
                    }
                ) {
                    Box(
                        Modifier
                            .then(
                                when (orientation) {
                                    Horizontal -> Modifier.offset(x = 150.dp)
                                    Vertical -> Modifier.offset(y = 150.dp)
                                }
                            )
                            .size(50.dp)
                            .background(Blue)
                            .relocationRequester(relocationRequester)
                    )
                }
            }
        }
        rule.waitForIdle()
        runBlocking {
            scrollState.scrollTo(scrollState.maxValue)
        }
        rule.waitForIdle()

        // Act.
        relocationRequester.bringIntoView()

        // Assert.
        assertScreenshot(if (horizontal) "blueBoxRight" else "blueBoxBottom")
    }

    @Test
    fun multipleParentsAreScrolledSoThatChildIsVisible() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        lateinit var parentScrollState: ScrollState
        lateinit var grandParentScrollState: ScrollState
        rule.setContent {
            parentScrollState = rememberScrollState()
            grandParentScrollState = rememberScrollState()
            Box(
                Modifier
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal ->
                                Modifier
                                    .size(100.dp, 50.dp)
                                    .horizontalScroll(grandParentScrollState)
                            Vertical ->
                                Modifier
                                    .size(50.dp, 100.dp)
                                    .verticalScroll(grandParentScrollState)
                        }
                    )
            ) {
                Box(
                    Modifier
                        .background(LightGray)
                        .then(
                            when (orientation) {
                                Horizontal ->
                                    Modifier
                                        .size(200.dp, 50.dp)
                                        .horizontalScroll(parentScrollState)
                                Vertical ->
                                    Modifier
                                        .size(50.dp, 200.dp)
                                        .verticalScroll(parentScrollState)
                            }
                        )
                ) {
                    Box(
                        when (orientation) {
                            Horizontal -> Modifier.size(400.dp, 50.dp)
                            Vertical -> Modifier.size(50.dp, 400.dp)
                        }
                    ) {
                        Box(
                            Modifier
                                .then(
                                    when (orientation) {
                                        Horizontal -> Modifier.offset(x = 25.dp)
                                        Vertical -> Modifier.offset(y = 25.dp)
                                    }
                                )
                                .size(50.dp)
                                .background(Blue)
                                .relocationRequester(relocationRequester)
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        runBlocking {
            parentScrollState.scrollTo(parentScrollState.maxValue)
        }
        rule.waitForIdle()
        runBlocking {
            grandParentScrollState.scrollTo(grandParentScrollState.maxValue)
        }
        rule.waitForIdle()

        // Act.
        relocationRequester.bringIntoView()

        // Assert.
        assertScreenshot(if (horizontal) "blueBoxLeft" else "blueBoxTop")
    }

    @Test
    fun multipleParentsAreScrolledInDifferentDirectionsSoThatChildIsVisible() {
        // Arrange.
        val relocationRequester = RelocationRequester()
        lateinit var parentScrollState: ScrollState
        lateinit var grandParentScrollState: ScrollState
        rule.setContent {
            parentScrollState = rememberScrollState()
            grandParentScrollState = rememberScrollState()
            Box(
                Modifier
                    .testTag(parentBox)
                    .background(LightGray)
                    .then(
                        when (orientation) {
                            Horizontal ->
                                Modifier
                                    .size(100.dp, 50.dp)
                                    .verticalScroll(grandParentScrollState)
                            Vertical ->
                                Modifier
                                    .size(50.dp, 100.dp)
                                    .horizontalScroll(grandParentScrollState)
                        }
                    )
            ) {
                Box(
                    Modifier
                        .size(100.dp)
                        .background(LightGray)
                        .then(
                            when (orientation) {
                                Horizontal -> Modifier.horizontalScroll(parentScrollState)
                                Vertical -> Modifier.verticalScroll(parentScrollState)
                            }
                        )
                ) {
                    Box(Modifier.size(200.dp)) {
                        Box(
                            Modifier
                                .offset(x = 25.dp, y = 25.dp)
                                .size(50.dp)
                                .background(Blue)
                                .relocationRequester(relocationRequester)
                        )
                    }
                }
            }
        }
        rule.waitForIdle()
        runBlocking {
            parentScrollState.scrollTo(parentScrollState.maxValue)
        }
        rule.waitForIdle()
        runBlocking {
            grandParentScrollState.scrollTo(grandParentScrollState.maxValue)
        }
        rule.waitForIdle()

        // Act.
        relocationRequester.bringIntoView()

        // Assert.
        assertScreenshot(if (horizontal) "blueBoxLeft" else "blueBoxTop")
    }

    private val horizontal: Boolean get() = (orientation == Horizontal)

    @Composable
    private fun RowOrColumn(
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit
    ) {
        when (orientation) {
            Horizontal -> Row(modifier) { content() }
            Vertical -> Column(modifier) { content() }
        }
    }

    @RequiresApi(O)
    private fun assertScreenshot(screenshot: String) {
        rule.onNodeWithTag(parentBox)
            .captureToImage()
            .assertAgainstGolden(screenshotRule, "bringIntoParentBounds_$screenshot")
    }
}

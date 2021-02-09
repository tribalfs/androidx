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

package androidx.compose.foundation

import androidx.compose.foundation.gestures.ZoomableController
import androidx.compose.foundation.gestures.rememberZoomableController
import androidx.compose.foundation.gestures.zoomable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.center
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val TEST_TAG = "zoomableTestTag"

private const val EDGE_FUZZ_FACTOR = 0.2f

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ZoomableTest {
    @Suppress("DEPRECATION")
    @get:Rule
    val rule = createComposeRule()

    @Before
    fun before() {
        isDebugInspectorInfoEnabled = true
    }

    @After
    fun after() {
        isDebugInspectorInfoEnabled = false
    }

    @Test
    fun zoomable_zoomIn() {
        var cumulativeScale = 1.0f

        setZoomableContent { Modifier.zoomable(onZoomDelta = { cumulativeScale *= it }) }

        rule.onNodeWithTag(TEST_TAG).performGesture {
            val leftStartX = center.x - 10
            val leftEndX = visibleSize.toSize().width * EDGE_FUZZ_FACTOR
            val rightStartX = center.x + 10
            val rightEndX = visibleSize.toSize().width * (1 - EDGE_FUZZ_FACTOR)

            pinch(
                Offset(leftStartX, center.y),
                Offset(leftEndX, center.y),
                Offset(rightStartX, center.y),
                Offset(rightEndX, center.y)
            )
        }

        rule.mainClock.advanceTimeBy(milliseconds = 1000)

        rule.runOnIdle {
            assertWithMessage("Should have scaled at least 4x").that(cumulativeScale).isAtLeast(4f)
        }
    }

    @Test
    fun zoomable_zoomOut() {
        var cumulativeScale = 1.0f

        setZoomableContent { Modifier.zoomable(onZoomDelta = { cumulativeScale *= it }) }

        rule.onNodeWithTag(TEST_TAG).performGesture {
            val leftStartX = visibleSize.toSize().width * EDGE_FUZZ_FACTOR
            val leftEndX = center.x - 10
            val rightStartX = visibleSize.toSize().width * (1 - EDGE_FUZZ_FACTOR)
            val rightEndX = center.x + 10

            pinch(
                Offset(leftStartX, center.y),
                Offset(leftEndX, center.y),
                Offset(rightStartX, center.y),
                Offset(rightEndX, center.y)
            )
        }

        rule.mainClock.advanceTimeBy(milliseconds = 1000)

        rule.runOnIdle {
            assertWithMessage("Should have scaled down at least 4x")
                .that(cumulativeScale)
                .isAtMost(0.25f)
        }
    }

    @Test
    fun zoomable_startStop_notify() {
        var cumulativeScale = 1.0f
        var startTriggered = 0f
        var stopTriggered = 0f

        setZoomableContent {
            Modifier
                .zoomable(
                    onZoomStarted = { startTriggered++ },
                    onZoomStopped = { stopTriggered++ },
                    onZoomDelta = { cumulativeScale *= it }
                )
        }

        rule.runOnIdle {
            assertThat(startTriggered).isEqualTo(0)
            assertThat(stopTriggered).isEqualTo(0)
        }

        rule.onNodeWithTag(TEST_TAG).performGesture {
            val leftStartX = visibleSize.toSize().width * EDGE_FUZZ_FACTOR
            val leftEndX = center.x - 10
            val rightStartX = visibleSize.toSize().width * (1 - EDGE_FUZZ_FACTOR)
            val rightEndX = center.x + 10

            pinch(
                Offset(leftStartX, center.y),
                Offset(leftEndX, center.y),
                Offset(rightStartX, center.y),
                Offset(rightEndX, center.y)
            )
        }

        rule.mainClock.advanceTimeBy(milliseconds = 1000)

        rule.runOnIdle {
            assertThat(startTriggered).isEqualTo(1)
            assertThat(stopTriggered).isEqualTo(1)
        }
    }

    @Test
    fun zoomable_disabledWontCallLambda() {
        val enabled = mutableStateOf(true)
        var cumulativeScale = 1.0f

        setZoomableContent {
            Modifier
                .zoomable(enabled = enabled.value, onZoomDelta = { cumulativeScale *= it })
        }

        rule.onNodeWithTag(TEST_TAG).performGesture {
            val leftStartX = center.x - 10
            val leftEndX = visibleSize.toSize().width * EDGE_FUZZ_FACTOR
            val rightStartX = center.x + 10
            val rightEndX = visibleSize.toSize().width * (1 - EDGE_FUZZ_FACTOR)

            pinch(
                Offset(leftStartX, center.y),
                Offset(leftEndX, center.y),
                Offset(rightStartX, center.y),
                Offset(rightEndX, center.y)
            )
        }

        rule.mainClock.advanceTimeBy(milliseconds = 1000)

        val prevScale = rule.runOnIdle {
            assertWithMessage("Should have scaled at least 4x").that(cumulativeScale).isAtLeast(4f)
            enabled.value = false
            cumulativeScale
        }

        rule.onNodeWithTag(TEST_TAG).performGesture {
            val leftStartX = visibleSize.toSize().width * EDGE_FUZZ_FACTOR
            val leftEndX = center.x - 10
            val rightStartX = visibleSize.toSize().width * (1 - EDGE_FUZZ_FACTOR)
            val rightEndX = center.x + 10

            pinch(
                Offset(leftStartX, center.y),
                Offset(leftEndX, center.y),
                Offset(rightStartX, center.y),
                Offset(rightEndX, center.y)
            )
        }

        rule.runOnIdle {
            assertWithMessage("When enabled = false, scale should stay the same")
                .that(cumulativeScale)
                .isEqualTo(prevScale)
        }
    }

    @Test
    fun zoomable_callsStop_whenRemoved() {
        var cumulativeScale = 1.0f
        var stopTriggered = 0f

        setZoomableContent {
            if (cumulativeScale < 2f) {
                Modifier
                    .zoomable(
                        onZoomStopped = { stopTriggered++ },
                        onZoomDelta = { cumulativeScale *= it }
                    )
            } else {
                Modifier
            }
        }

        rule.runOnIdle {
            assertThat(stopTriggered).isEqualTo(0)
        }

        rule.onNodeWithTag(TEST_TAG).performGesture {
            val leftStartX = center.x - 10
            val leftEndX = visibleSize.toSize().width * EDGE_FUZZ_FACTOR
            val rightStartX = center.x + 10
            val rightEndX = visibleSize.toSize().width * (1 - EDGE_FUZZ_FACTOR)

            pinch(
                Offset(leftStartX, center.y),
                Offset(leftEndX, center.y),
                Offset(rightStartX, center.y),
                Offset(rightEndX, center.y)
            )
        }

        rule.mainClock.advanceTimeBy(milliseconds = 1000)

        rule.runOnIdle {
            assertThat(cumulativeScale).isAtLeast(2f)
            assertThat(stopTriggered).isEqualTo(1f)
        }
    }

    @Test
    fun zoomable_animateTo() {
        var cumulativeScale = 1.0f
        var callbackCount = 0

        lateinit var state: ZoomableController
        lateinit var coroutineScope: CoroutineScope
        setZoomableContent {
            state = rememberZoomableController(
                onZoomDelta = {
                    cumulativeScale *= it
                    callbackCount += 1
                }
            )
            coroutineScope = rememberCoroutineScope()

            Modifier.zoomable(state)
        }

        rule.runOnUiThread {
            coroutineScope.launch {
                state.smoothScaleBy(4f)
            }
        }

        rule.mainClock.advanceTimeBy(milliseconds = 10)

        rule.runOnIdle {
            assertWithMessage("Scrolling should have been smooth").that(callbackCount).isAtLeast(1)
        }

        rule.mainClock.advanceTimeBy(milliseconds = 10)

        rule.runOnIdle {
            assertWithMessage("Scrolling should have been smooth").that(callbackCount).isAtLeast(2)
        }

        rule.mainClock.advanceTimeBy(milliseconds = 1000)

        rule.runOnIdle {
            assertWithMessage("Scrolling should have been smooth").that(callbackCount).isAtLeast(3)
            // Include a bit of tolerance for floating point discrepancies.
            assertWithMessage("Should have scaled ~4x").that(cumulativeScale).isAtLeast(3.9f)
        }
    }

    @Test
    fun testInspectorValue() {
        rule.setContent {
            val state = rememberZoomableController {}
            val modifier = Modifier.zoomable(state) as InspectableValue
            assertThat(modifier.nameFallback).isEqualTo("zoomable")
            assertThat(modifier.valueOverride).isNull()
            assertThat(modifier.inspectableElements.map { it.name }.asIterable()).containsExactly(
                "controller",
                "enabled",
                "onZoomStarted",
                "onZoomStopped"
            )
        }
    }

    @Test
    fun testInspectorValueWithoutController() {
        rule.setContent {
            val modifier = Modifier.zoomable {} as InspectableValue
            assertThat(modifier.nameFallback).isEqualTo("zoomable")
            assertThat(modifier.valueOverride).isNull()
            assertThat(modifier.inspectableElements.map { it.name }.asIterable()).containsExactly(
                "enabled",
                "onZoomStarted",
                "onZoomStopped",
                "onZoomDelta"
            )
        }
    }

    private fun setZoomableContent(getModifier: @Composable () -> Modifier) {
        rule.setContent {
            Box(Modifier.size(600.dp).testTag(TEST_TAG).then(getModifier()))
        }
    }
}

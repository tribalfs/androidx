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

package androidx.compose.animation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@LargeTest
@OptIn(ExperimentalTestApi::class)
class AnimatedVisibilityTest {

    @get:Rule
    val rule = createComposeRule()

    private val frameDuration = 16

    @OptIn(ExperimentalAnimationApi::class)
    @Test
    fun animateVisibilityExpandShrinkTest() {
        val testModifier by mutableStateOf(TestModifier())
        var visible by mutableStateOf(false)
        var density = 0f
        var offset by mutableStateOf(Offset(0f, 0f))
        var disposed by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AnimatedVisibility(
                visible, testModifier,
                enter = expandIn(
                    Alignment.BottomEnd,
                    { fullSize -> IntSize(fullSize.width / 4, fullSize.height / 2) },
                    tween(160, easing = LinearOutSlowInEasing)
                ),
                exit = shrinkOut(
                    Alignment.CenterStart,
                    { fullSize -> IntSize(fullSize.width / 10, fullSize.height / 5) },
                    tween(160, easing = FastOutSlowInEasing)
                )
            ) {
                Box(
                    Modifier.onGloballyPositioned {
                        offset = it.localToRoot(Offset.Zero)
                    }.requiredSize(100.dp, 100.dp)
                ) {
                    DisposableEffect(Unit) {
                        onDispose {
                            disposed = true
                        }
                    }
                }
            }
            density = LocalDensity.current.density
        }

        rule.runOnIdle {
            visible = true
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        val startWidth = density * 100 / 4f
        val startHeight = density * 100 / 2f
        val fullSize = density * 100
        assertFalse(disposed)

        for (i in 0..160 step frameDuration) {
            val fraction = LinearOutSlowInEasing.transform(i / 160f)
            val animWidth = lerp(startWidth, fullSize, fraction)
            val animHeight = lerp(startHeight, fullSize, fraction)
            // Check size
            assertEquals(animWidth, testModifier.width.toFloat(), 2f)
            assertEquals(animHeight, testModifier.height.toFloat(), 2f)
            // Check offset
            assertEquals(animWidth - fullSize, offset.x, 2f)
            assertEquals(animHeight - fullSize, offset.y, 2f)
            rule.mainClock.advanceTimeBy(frameDuration.toLong())
            rule.waitForIdle()
        }

        rule.runOnIdle {
            visible = false
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        val endWidth = density * 100 / 10f
        val endHeight = density * 100 / 5f
        for (i in 0..160 step frameDuration) {
            val fraction = FastOutSlowInEasing.transform(i / 160f)
            val animWidth = lerp(fullSize, endWidth, fraction)
            val animHeight = lerp(fullSize, endHeight, fraction)
            // Check size
            assertEquals(animWidth, testModifier.width.toFloat(), 2f)
            assertEquals(animHeight, testModifier.height.toFloat(), 2f)
            // Check offset
            assertEquals(0f, offset.x, 2f)
            assertEquals((animHeight - fullSize) / 2f, offset.y, 2f)
            rule.mainClock.advanceTimeBy(frameDuration.toLong())
            rule.waitForIdle()
        }

        // Check that the composable children in AnimatedVisibility are skipped after exit animation
        rule.mainClock.autoAdvance = true
        rule.waitUntil { disposed }
        rule.mainClock.autoAdvance = false

        // Make it visible again, and test that it behaves the same as before
        rule.runOnIdle {
            visible = true
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        for (i in 0..160 step frameDuration) {
            val fraction = LinearOutSlowInEasing.transform(i / 160f)
            val animWidth = lerp(startWidth, fullSize, fraction)
            val animHeight = lerp(startHeight, fullSize, fraction)
            // Check size
            assertEquals(animWidth, testModifier.width.toFloat(), 2f)
            assertEquals(animHeight, testModifier.height.toFloat(), 2f)
            // Check offset
            assertEquals(animWidth - fullSize, offset.x, 2f)
            assertEquals(animHeight - fullSize, offset.y, 2f)
            rule.mainClock.advanceTimeBy(frameDuration.toLong())
            rule.waitForIdle()
        }
    }

    @OptIn(ExperimentalAnimationApi::class)
    @Test
    fun animateVisibilitySlideTest() {
        val testModifier by mutableStateOf(TestModifier())
        var visible by mutableStateOf(false)
        var density = 0f
        var offset by mutableStateOf(Offset(0f, 0f))
        var disposed by mutableStateOf(false)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            AnimatedVisibility(
                visible, testModifier,
                enter = slideIn(
                    { fullSize -> IntOffset(fullSize.width / 4, -fullSize.height / 2) },
                    tween(160, easing = LinearOutSlowInEasing)
                ),
                exit = slideOut(
                    { fullSize -> IntOffset(-fullSize.width / 10, fullSize.height / 5) },
                    tween(160, easing = FastOutSlowInEasing)
                )
            ) {
                Box(
                    Modifier.onGloballyPositioned {
                        offset = it.localToRoot(Offset.Zero)
                    }.requiredSize(100.dp, 100.dp)
                ) {
                    DisposableEffect(Unit) {
                        onDispose {
                            disposed = true
                        }
                    }
                }
            }
            density = LocalDensity.current.density
        }

        rule.runOnIdle {
            visible = true
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        val startX = density * 100 / 4f
        val startY = -density * 100 / 2f
        val fullSize = density * 100
        assertFalse(disposed)

        for (i in 0..160 step frameDuration) {
            val fraction = LinearOutSlowInEasing.transform(i / 160f)
            val animX = lerp(startX, 0f, fraction)
            val animY = lerp(startY, 0f, fraction)
            // Check size
            assertEquals(fullSize, testModifier.width.toFloat(), 2f)
            assertEquals(fullSize, testModifier.height.toFloat(), 2f)
            // Check offset
            assertEquals(animX, offset.x, 2f)
            assertEquals(animY, offset.y, 2f)
            rule.mainClock.advanceTimeBy(frameDuration.toLong())
            rule.waitForIdle()
        }

        rule.runOnIdle {
            visible = false
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        val endX = -density * 100 / 10f
        val endY = density * 100 / 5f
        for (i in 0..160 step frameDuration) {
            val fraction = FastOutSlowInEasing.transform(i / 160f)
            val animX = lerp(0f, endX, fraction)
            val animY = lerp(0f, endY, fraction)
            // Check size
            assertEquals(fullSize, testModifier.width.toFloat(), 2f)
            assertEquals(fullSize, testModifier.height.toFloat(), 2f)
            // Check offset
            assertEquals(animX, offset.x, 2f)
            assertEquals(animY, offset.y, 2f)
            rule.mainClock.advanceTimeBy(frameDuration.toLong())
            rule.waitForIdle()
        }

        // Check that the composable children in AnimatedVisibility are skipped after exit animation
        rule.mainClock.autoAdvance = true
        rule.waitUntil { disposed }
        rule.mainClock.autoAdvance = false

        // Make it visible again, and test that it behaves the same as before
        rule.runOnIdle {
            visible = true
        }
        rule.mainClock.advanceTimeByFrame()
        rule.mainClock.advanceTimeByFrame()

        for (i in 0..160 step frameDuration) {
            val fraction = LinearOutSlowInEasing.transform(i / 160f)
            val animX = lerp(startX, 0f, fraction)
            val animY = lerp(startY, 0f, fraction)
            // Check size
            assertEquals(fullSize, testModifier.width.toFloat(), 2f)
            assertEquals(fullSize, testModifier.height.toFloat(), 2f)
            // Check offset
            assertEquals(animX, offset.x, 2f)
            assertEquals(animY, offset.y, 2f)
            rule.mainClock.advanceTimeBy(frameDuration.toLong())
            rule.waitForIdle()
        }
    }
}

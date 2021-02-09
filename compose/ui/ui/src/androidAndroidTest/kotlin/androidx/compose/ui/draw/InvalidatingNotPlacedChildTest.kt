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

package androidx.compose.ui.draw

import android.os.Build
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.assertCenterPixelColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class InvalidatingNotPlacedChildTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun childIsDisplayedWhenItWasNotPlacedOriginallyButPlacedLater() {
        val shouldPlace = mutableStateOf(false)
        composeTestRule.setContent {
            ConditionallyPlacedChild(
                shouldPlace,
                Modifier.background(Color.Blue)
                    .testTag("node")
            ) {
                Spacer(
                    Modifier.requiredSize(5.dp)
                        .graphicsLayer()
                        .background(Color.Red)
                )
            }
        }

        composeTestRule.onNodeWithTag("node")
            .captureToImage()
            .assertCenterPixelColor(Color.Blue)

        composeTestRule.runOnIdle {
            shouldPlace.value = true
        }

        composeTestRule.onNodeWithTag("node")
            .captureToImage()
            .assertCenterPixelColor(Color.Red)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun grandChildIsDisplayedWhenItWasNotPlacedOriginallyButPlacedLater() {
        val shouldPlace = mutableStateOf(false)
        composeTestRule.setContent {
            ConditionallyPlacedChild(
                shouldPlace,
                Modifier.background(Color.Blue)
                    .testTag("node")
            ) {
                MeasureInLayoutBlock {
                    Spacer(
                        Modifier.fillMaxSize()
                            .graphicsLayer()
                            .background(Color.Gray)
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("node")
            .captureToImage()
            .assertCenterPixelColor(Color.Blue)

        composeTestRule.runOnIdle {
            shouldPlace.value = true
        }

        composeTestRule.onNodeWithTag("node")
            .captureToImage()
            .assertCenterPixelColor(Color.Gray)
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun grandChildIsDisplayedCorrectlyWhenTheColorWasChangedWhileNotPlaced() {
        val shouldPlace = mutableStateOf(false)
        var color by mutableStateOf(Color.Gray)
        composeTestRule.setContent {
            ConditionallyPlacedChild(
                shouldPlace,
                Modifier.background(Color.Blue)
                    .testTag("node")
            ) {
                MeasureInLayoutBlock {
                    Spacer(
                        Modifier.fillMaxSize()
                            .graphicsLayer()
                            .background(color)
                    )
                }
            }
        }

        composeTestRule.runOnIdle {
            shouldPlace.value = false
        }

        composeTestRule.runOnIdle {
            color = Color.Red
        }

        composeTestRule.runOnIdle {
            shouldPlace.value = true
        }

        composeTestRule.onNodeWithTag("node")
            .captureToImage()
            .assertCenterPixelColor(Color.Red)
    }
}

@Composable
private fun ConditionallyPlacedChild(
    shouldPlace: State<Boolean>,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val placeable = measurables.first().measure(constraints)
        layout(placeable.width, placeable.height) {
            if (shouldPlace.value) {
                placeable.place(0, 0)
            }
        }
    }
}

@Composable
private fun MeasureInLayoutBlock(content: @Composable () -> Unit) {
    Layout(content = content) { measurables, constraints ->
        val size = 5.dp.roundToPx()
        layout(size, size) {
            measurables.first().measure(constraints).place(0, 0)
        }
    }
}

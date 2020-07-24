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

package androidx.compose.foundation.layout

import android.os.Build
import androidx.compose.runtime.state
import androidx.test.filters.SmallTest
import androidx.ui.core.Alignment
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.Modifier
import androidx.ui.core.testTag
import androidx.ui.core.positionInRoot
import androidx.ui.core.onPositioned
import androidx.compose.foundation.Box
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithTag
import androidx.ui.test.runOnIdle
import androidx.compose.ui.unit.dp
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.roundToInt

@SmallTest
@RunWith(JUnit4::class)
class LayoutOffsetTest : LayoutTest() {
    @get:Rule
    val composeTestRule = createComposeRule()

    @Before
    fun before() {
        // b/151728444
        Assume.assumeFalse(
            Build.MODEL.contains("Nexus 5") && Build.VERSION.SDK_INT == Build.VERSION_CODES.M
        )
    }

    @Test
    fun positionIsModified() = with(density) {
        val offsetX = 10.dp
        val offsetY = 20.dp
        var positionX = 0
        var positionY = 0
        composeTestRule.setContent {
            Stack(
                Modifier.testTag("stack")
                    .wrapContentSize(Alignment.TopStart)
                    .offset(offsetX, offsetY)
                    .onPositioned { coordinates: LayoutCoordinates ->
                        positionX = coordinates.positionInRoot.x.roundToInt()
                        positionY = coordinates.positionInRoot.y.roundToInt()
                    }
            ) {
            }
        }

        onNodeWithTag("stack").assertExists()
        runOnIdle {
            assertEquals(offsetX.toIntPx(), positionX)
            assertEquals(offsetY.toIntPx(), positionY)
        }
    }

    @Test
    fun positionIsModified_rtl() = with(density) {
        val containerWidth = 30.dp
        val boxSize = 1
        val offsetX = 10.dp
        val offsetY = 20.dp
        var positionX = 0
        var positionY = 0
        composeTestRule.setContent {
            Stack(
                Modifier.testTag("stack")
                    .rtl
                    .wrapContentSize(Alignment.TopEnd)
                    .preferredWidth(containerWidth)
                    .wrapContentSize(Alignment.TopStart)
                    .offset(offsetX, offsetY)
                    .onPositioned { coordinates: LayoutCoordinates ->
                        positionX = coordinates.positionInRoot.x.roundToInt()
                        positionY = coordinates.positionInRoot.y.roundToInt()
                    }
            ) {
                // TODO(popam): this box should not be needed after b/154758475 is fixed.
                Box(Modifier.size(boxSize.toDp()))
            }
        }

        onNodeWithTag("stack").assertExists()
        runOnIdle {
            assertEquals(containerWidth.toIntPx() - offsetX.toIntPx() - boxSize, positionX)
            assertEquals(offsetY.toIntPx(), positionY)
        }
    }

    @Test
    fun positionIsModified_px() = with(density) {
        val offsetX = 10f
        val offsetY = 20f
        var positionX = 0f
        var positionY = 0f
        composeTestRule.setContent {
            Stack(
                Modifier.testTag("stack")
                    .wrapContentSize(Alignment.TopStart)
                    .offsetPx(state { offsetX }, state { offsetY })
                    .onPositioned { coordinates: LayoutCoordinates ->
                        positionX = coordinates.positionInRoot.x
                        positionY = coordinates.positionInRoot.y
                    }
            ) {
            }
        }

        onNodeWithTag("stack").assertExists()
        runOnIdle {
            Assert.assertEquals(offsetX, positionX)
            Assert.assertEquals(offsetY, positionY)
        }
    }

    @Test
    fun positionIsModified_px_rtl() = with(density) {
        val containerWidth = 30.dp
        val boxSize = 1f
        val offsetX = 10f
        val offsetY = 20f
        var positionX = 0f
        var positionY = 0f
        composeTestRule.setContent {
            Stack(
                Modifier.testTag("stack")
                    .rtl
                    .wrapContentSize(Alignment.TopEnd)
                    .preferredWidth(containerWidth)
                    .wrapContentSize(Alignment.TopStart)
                    .offsetPx(state { offsetX }, state { offsetY })
                    .onPositioned { coordinates: LayoutCoordinates ->
                        positionX = coordinates.positionInRoot.x
                        positionY = coordinates.positionInRoot.y
                    }
            ) {
                // TODO(popam): this box should not be needed after b/154758475 is fixed.
                Box(Modifier.size(boxSize.toDp()))
            }
        }

        onNodeWithTag("stack").assertExists()
        runOnIdle {
            Assert.assertEquals(
                containerWidth.toIntPx() - offsetX.roundToInt() - boxSize,
                positionX
            )
            Assert.assertEquals(offsetY, positionY)
        }
    }
}

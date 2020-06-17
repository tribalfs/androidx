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

package androidx.ui.foundation.lazy

import androidx.compose.getValue
import androidx.compose.mutableStateOf
import androidx.compose.onCommit
import androidx.compose.onDispose
import androidx.compose.setValue
import androidx.test.filters.LargeTest
import androidx.ui.core.Modifier
import androidx.ui.core.gesture.TouchSlop
import androidx.ui.core.testTag
import androidx.ui.geometry.Offset
import androidx.ui.foundation.Box
import androidx.ui.foundation.Text
import androidx.ui.layout.Spacer
import androidx.ui.layout.fillMaxSize
import androidx.ui.layout.fillMaxWidth
import androidx.ui.layout.height
import androidx.ui.layout.preferredHeight
import androidx.ui.layout.size
import androidx.ui.test.SemanticsNodeInteraction
import androidx.ui.test.assertCountEquals
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.assertIsNotDisplayed
import androidx.ui.test.center
import androidx.ui.test.children
import androidx.ui.test.createComposeRule
import androidx.ui.test.doGesture
import androidx.ui.test.findByTag
import androidx.ui.test.findByText
import androidx.ui.test.runOnIdleCompose
import androidx.ui.test.sendSwipeUp
import androidx.ui.test.sendSwipeWithVelocity
import androidx.ui.test.waitForIdle
import androidx.ui.unit.Density
import androidx.ui.unit.Dp
import androidx.ui.unit.dp
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch

@LargeTest
@RunWith(JUnit4::class)
class LazyColumnItemsTest {
    private val LazyColumnItemsTag = "TestLazyColumnItems"

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun compositionsAreDisposed_whenNodesAreScrolledOff() {
        var composed: Boolean
        var disposed = false
        // Ten 31dp spacers in a 300dp list
        val latch = CountDownLatch(10)
        // Make it long enough that it's _definitely_ taller than the screen
        val data = (1..50).toList()

        composeTestRule.setContent {
            // Fixed height to eliminate device size as a factor
            Box(Modifier.testTag(LazyColumnItemsTag).preferredHeight(300.dp)) {
                LazyColumnItems(items = data, modifier = Modifier.fillMaxSize()) {
                    onCommit {
                        composed = true
                        // Signal when everything is done composing
                        latch.countDown()
                        onDispose {
                            disposed = true
                        }
                    }

                    // There will be 10 of these in the 300dp box
                    Spacer(Modifier.preferredHeight(31.dp))
                }
            }
        }

        latch.await()
        composed = false

        assertWithMessage("Compositions were disposed before we did any scrolling")
            .that(disposed).isFalse()

        // Mostly a sanity check, this is not part of the behavior under test
        assertWithMessage("Additional composition occurred for no apparent reason")
            .that(composed).isFalse()

        findByTag(LazyColumnItemsTag)
            .doGesture { sendSwipeUp() }

        waitForIdle()

        assertWithMessage("No additional items were composed after scroll, scroll didn't work")
            .that(composed).isTrue()

        // We may need to modify this test once we prefetch/cache items outside the viewport
        assertWithMessage(
            "No compositions were disposed after scrolling, compositions were leaked"
        ).that(disposed).isTrue()
    }

    @Test
    fun compositionsAreDisposed_whenDataIsChanged() {
        var composed: Boolean
        var disposals = 0
        val latch1 = CountDownLatch(3)
        val latch2 = CountDownLatch(2)
        val data1 = (1..3).toList()
        val data2 = (4..5).toList() // smaller, to ensure removal is handled properly

        var part2 by mutableStateOf(false)

        composeTestRule.setContent {
            LazyColumnItems(
                items = if (!part2) data1 else data2,
                modifier = Modifier.testTag(LazyColumnItemsTag).fillMaxSize()
            ) {
                onCommit {
                    composed = true
                    // Signal when everything is done composing
                    if (!part2) {
                        latch1.countDown()
                    } else {
                        latch2.countDown()
                    }
                    onDispose {
                        disposals++
                    }
                }

                Spacer(Modifier.height(50.dp))
            }
        }

        latch1.await()

        composed = false

        runOnIdleCompose { part2 = true }

        latch2.await()

        assertWithMessage(
            "No additional items were composed after data change, something didn't work"
        ).that(composed).isTrue()

        // We may need to modify this test once we prefetch/cache items outside the viewport
        assertWithMessage(
            "Not enough compositions were disposed after scrolling, compositions were leaked"
        ).that(disposals).isEqualTo(data1.size)
    }

    @Test
    fun compositionsAreDisposed_whenAdapterListIsDisposed() {
        var emitAdapterList by mutableStateOf(true)
        var disposeCalledOnFirstItem = false
        var disposeCalledOnSecondItem = false

        composeTestRule.setContent {
            if (emitAdapterList) {
                LazyColumnItems(
                    items = listOf(0, 1),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(Modifier.size(100.dp))
                    onDispose {
                        if (it == 1) {
                            disposeCalledOnFirstItem = true
                        } else {
                            disposeCalledOnSecondItem = true
                        }
                    }
                }
            }
        }

        runOnIdleCompose {
            assertWithMessage("First item is not immediately disposed")
                .that(disposeCalledOnFirstItem).isFalse()
            assertWithMessage("Second item is not immediately disposed")
                .that(disposeCalledOnFirstItem).isFalse()
            emitAdapterList = false
        }

        runOnIdleCompose {
            assertWithMessage("First item is correctly disposed")
                .that(disposeCalledOnFirstItem).isTrue()
            assertWithMessage("Second item is correctly disposed")
                .that(disposeCalledOnSecondItem).isTrue()
        }
    }

    @Test
    fun removeItemsTest() {
        val startingNumItems = 3
        var numItems = startingNumItems
        var numItemsModel by mutableStateOf(numItems)
        val tag = "List"
        composeTestRule.setContent {
            LazyColumnItems((1..numItemsModel).toList(), modifier = Modifier.testTag(tag)) {
                Text("$it")
            }
        }

        while (numItems >= 0) {
            // Confirm the number of children to ensure there are no extra items
            findByTag(tag)
                .children()
                .assertCountEquals(numItems)

            // Confirm the children's content
            for (i in 1..3) {
                findByText("$i").apply {
                    if (i <= numItems) {
                        assertExists()
                    } else {
                        assertDoesNotExist()
                    }
                }
            }
            numItems--
            if (numItems >= 0) {
                // Don't set the model to -1
                runOnIdleCompose { numItemsModel = numItems }
            }
        }
    }

    @Test
    fun changingDataTest() {
        val dataLists = listOf(
            (1..3).toList(),
            (4..8).toList(),
            (3..4).toList()
        )
        var dataModel by mutableStateOf(dataLists[0])
        val tag = "List"
        composeTestRule.setContent {
            LazyColumnItems(dataModel, modifier = Modifier.testTag(tag)) {
                Text("$it")
            }
        }

        for (data in dataLists) {
            runOnIdleCompose { dataModel = data }

            // Confirm the number of children to ensure there are no extra items
            val numItems = data.size
            findByTag(tag)
                .children()
                .assertCountEquals(numItems)

            // Confirm the children's content
            for (item in data) {
                findByText("$item").assertExists()
            }
        }
    }

    @Test
    fun whenItemsAreInitiallyCreatedWith0SizeWeCanScrollWhenTheyExpanded() {
        val thirdTag = "third"
        val items = (1..3).toList()
        var thirdHasSize by mutableStateOf(false)

        composeTestRule.setContent {
            LazyColumnItems(
                items = items,
                modifier = Modifier.fillMaxWidth()
                    .preferredHeight(100.dp)
                    .testTag(LazyColumnItemsTag)
            ) {
                if (it == 3) {
                    Spacer(Modifier.testTag(thirdTag)
                        .fillMaxWidth()
                        .preferredHeight(if (thirdHasSize) 60.dp else 0.dp))
                } else {
                    Spacer(Modifier.fillMaxWidth().preferredHeight(60.dp))
                }
            }
        }

        findByTag(LazyColumnItemsTag)
            .scrollBy(y = 21.dp, density = composeTestRule.density)

        findByTag(thirdTag)
            .assertExists()
            .assertIsNotDisplayed()

        runOnIdleCompose {
            thirdHasSize = true
        }

        waitForIdle()

        findByTag(LazyColumnItemsTag)
            .scrollBy(y = 10.dp, density = composeTestRule.density)

        findByTag(thirdTag)
            .assertIsDisplayed()
    }
}

internal fun SemanticsNodeInteraction.scrollBy(x: Dp = 0.dp, y: Dp = 0.dp, density: Density) =
    doGesture {
        with(density) {
            val touchSlop = TouchSlop.toIntPx()
            val xPx = x.toIntPx()
            val yPx = y.toIntPx()
            val offsetX = if (xPx > 0) xPx + touchSlop else if (xPx < 0) xPx - touchSlop else 0
            val offsetY = if (yPx > 0) yPx + touchSlop else if (yPx < 0) xPx - touchSlop else 0
            sendSwipeWithVelocity(
                start = center,
                end = Offset(center.x - offsetX, center.y - offsetY),
                endVelocity = 0f
            )
        }
    }

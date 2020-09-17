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

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.foundation.layout.preferredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Providers
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LayoutDirectionAmbient
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.filters.MediumTest
import androidx.ui.test.SemanticsNodeInteraction
import androidx.ui.test.assertHeightIsEqualTo
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.assertIsEqualTo
import androidx.ui.test.assertPositionInRootIsEqualTo
import androidx.ui.test.assertWidthIsEqualTo
import androidx.ui.test.createComposeRule
import androidx.ui.test.getUnclippedBoundsInRoot
import androidx.ui.test.onNodeWithTag
import com.google.common.truth.Truth
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class LazyRowForTest {
    private val LazyRowForTag = "LazyRowForTag"

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun lazyRowOnlyVisibleItemsAdded() {
        val items = (1..4).map { it.toString() }

        rule.setContent {
            Box(Modifier.preferredWidth(200.dp)) {
                LazyRowFor(items) {
                    Spacer(Modifier.preferredWidth(101.dp).fillParentMaxHeight().testTag(it))
                }
            }
        }

        rule.onNodeWithTag("1")
            .assertIsDisplayed()

        rule.onNodeWithTag("2")
            .assertIsDisplayed()

        rule.onNodeWithTag("3")
            .assertDoesNotExist()

        rule.onNodeWithTag("4")
            .assertDoesNotExist()
    }

    @Test
    fun lazyRowScrollToShowItems123() {
        val items = (1..4).map { it.toString() }

        rule.setContent {
            Box(Modifier.preferredWidth(200.dp)) {
                LazyRowFor(items, Modifier.testTag(LazyRowForTag)) {
                    Spacer(Modifier.preferredWidth(101.dp).fillParentMaxHeight().testTag(it))
                }
            }
        }

        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = 50.dp, density = rule.density)

        rule.onNodeWithTag("1")
            .assertIsDisplayed()

        rule.onNodeWithTag("2")
            .assertIsDisplayed()

        rule.onNodeWithTag("3")
            .assertIsDisplayed()

        rule.onNodeWithTag("4")
            .assertDoesNotExist()
    }

    @Test
    fun lazyRowScrollToHideFirstItem() {
        val items = (1..4).map { it.toString() }

        rule.setContent {
            Box(Modifier.preferredWidth(200.dp)) {
                LazyRowFor(items, Modifier.testTag(LazyRowForTag)) {
                    Spacer(Modifier.preferredWidth(101.dp).fillParentMaxHeight().testTag(it))
                }
            }
        }

        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = 102.dp, density = rule.density)

        rule.onNodeWithTag("1")
            .assertDoesNotExist()

        rule.onNodeWithTag("2")
            .assertIsDisplayed()

        rule.onNodeWithTag("3")
            .assertIsDisplayed()
    }

    @Test
    fun lazyRowScrollToShowItems234() {
        val items = (1..4).map { it.toString() }

        rule.setContent {
            Box(Modifier.preferredWidth(200.dp)) {
                LazyRowFor(items, Modifier.testTag(LazyRowForTag)) {
                    Spacer(Modifier.preferredWidth(101.dp).fillParentMaxHeight().testTag(it))
                }
            }
        }

        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = 150.dp, density = rule.density)

        rule.onNodeWithTag("1")
            .assertDoesNotExist()

        rule.onNodeWithTag("2")
            .assertIsDisplayed()

        rule.onNodeWithTag("3")
            .assertIsDisplayed()

        rule.onNodeWithTag("4")
            .assertIsDisplayed()
    }

    @Ignore("This test is not fully working. To be fixed in b/167913500")
    @Test
    fun contentPaddingIsApplied() = with(rule.density) {
        val itemTag = "item"

        rule.setContent {
            LazyRowFor(
                items = listOf(1),
                modifier = Modifier.size(100.dp)
                    .testTag(LazyRowForTag),
                contentPadding = PaddingValues(
                    start = 50.dp,
                    top = 10.dp,
                    end = 50.dp,
                    bottom = 10.dp
                )
            ) {
                Spacer(Modifier.fillParentMaxHeight().preferredWidth(50.dp).testTag(itemTag))
            }
        }

        var itemBounds = rule.onNodeWithTag(itemTag)
            .getUnclippedBoundsInRoot()

        Truth.assertThat(itemBounds.left.toIntPx()).isWithin1PixelFrom(50.dp.toIntPx())
        Truth.assertThat(itemBounds.right.toIntPx()).isWithin1PixelFrom(100.dp.toIntPx())
        Truth.assertThat(itemBounds.top.toIntPx()).isWithin1PixelFrom(10.dp.toIntPx())
        Truth.assertThat(itemBounds.bottom.toIntPx())
            .isWithin1PixelFrom(100.dp.toIntPx() - 10.dp.toIntPx())

        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = 51.dp, density = rule.density)

        itemBounds = rule.onNodeWithTag(itemTag)
            .getUnclippedBoundsInRoot()

        Truth.assertThat(itemBounds.left.toIntPx()).isWithin1PixelFrom(0)
        Truth.assertThat(itemBounds.right.toIntPx()).isWithin1PixelFrom(50.dp.toIntPx())
    }

    @Test
    fun lazyRowWrapsContent() = with(rule.density) {
        val itemInsideLazyRow = "itemInsideLazyRow"
        val itemOutsideLazyRow = "itemOutsideLazyRow"
        var sameSizeItems by mutableStateOf(true)

        rule.setContent {
            Column {
                LazyRowFor(
                    items = listOf(1, 2),
                    modifier = Modifier.testTag(LazyRowForTag)
                ) {
                    if (it == 1) {
                        Spacer(Modifier.preferredSize(50.dp).testTag(itemInsideLazyRow))
                    } else {
                        Spacer(Modifier.preferredSize(if (sameSizeItems) 50.dp else 70.dp))
                    }
                }
                Spacer(Modifier.preferredSize(50.dp).testTag(itemOutsideLazyRow))
            }
        }

        rule.onNodeWithTag(itemInsideLazyRow)
            .assertIsDisplayed()

        rule.onNodeWithTag(itemOutsideLazyRow)
            .assertIsDisplayed()

        var lazyRowBounds = rule.onNodeWithTag(LazyRowForTag)
            .getUnclippedBoundsInRoot()

        Truth.assertThat(lazyRowBounds.left.toIntPx()).isWithin1PixelFrom(0.dp.toIntPx())
        Truth.assertThat(lazyRowBounds.right.toIntPx()).isWithin1PixelFrom(100.dp.toIntPx())
        Truth.assertThat(lazyRowBounds.top.toIntPx()).isWithin1PixelFrom(0.dp.toIntPx())
        Truth.assertThat(lazyRowBounds.bottom.toIntPx()).isWithin1PixelFrom(50.dp.toIntPx())

        rule.runOnIdle {
            sameSizeItems = false
        }

        rule.waitForIdle()

        rule.onNodeWithTag(itemInsideLazyRow)
            .assertIsDisplayed()

        rule.onNodeWithTag(itemOutsideLazyRow)
            .assertIsDisplayed()

        lazyRowBounds = rule.onNodeWithTag(LazyRowForTag)
            .getUnclippedBoundsInRoot()

        Truth.assertThat(lazyRowBounds.left.toIntPx()).isWithin1PixelFrom(0.dp.toIntPx())
        Truth.assertThat(lazyRowBounds.right.toIntPx()).isWithin1PixelFrom(120.dp.toIntPx())
        Truth.assertThat(lazyRowBounds.top.toIntPx()).isWithin1PixelFrom(0.dp.toIntPx())
        Truth.assertThat(lazyRowBounds.bottom.toIntPx()).isWithin1PixelFrom(70.dp.toIntPx())
    }

    private val firstItemTag = "firstItemTag"
    private val secondItemTag = "secondItemTag"

    private fun prepareLazyRowForAlignment(verticalGravity: Alignment.Vertical) {
        rule.setContent {
            LazyRowFor(
                items = listOf(1, 2),
                modifier = Modifier.testTag(LazyRowForTag).height(100.dp),
                verticalAlignment = verticalGravity
            ) {
                if (it == 1) {
                    Spacer(Modifier.preferredSize(50.dp).testTag(firstItemTag))
                } else {
                    Spacer(Modifier.preferredSize(70.dp).testTag(secondItemTag))
                }
            }
        }

        rule.onNodeWithTag(firstItemTag)
            .assertIsDisplayed()

        rule.onNodeWithTag(secondItemTag)
            .assertIsDisplayed()

        val lazyRowBounds = rule.onNodeWithTag(LazyRowForTag)
            .getUnclippedBoundsInRoot()

        with(rule.density) {
            // Verify the height of the row
            Truth.assertThat(lazyRowBounds.top.toIntPx()).isWithin1PixelFrom(0.dp.toIntPx())
            Truth.assertThat(lazyRowBounds.bottom.toIntPx()).isWithin1PixelFrom(100.dp.toIntPx())
        }
    }

    @Test
    fun lazyRowAlignmentCenterVertically() {
        prepareLazyRowForAlignment(Alignment.CenterVertically)

        rule.onNodeWithTag(firstItemTag)
            .assertPositionInRootIsEqualTo(0.dp, 25.dp)

        rule.onNodeWithTag(secondItemTag)
            .assertPositionInRootIsEqualTo(50.dp, 15.dp)
    }

    @Test
    fun lazyRowAlignmentTop() {
        prepareLazyRowForAlignment(Alignment.Top)

        rule.onNodeWithTag(firstItemTag)
            .assertPositionInRootIsEqualTo(0.dp, 0.dp)

        rule.onNodeWithTag(secondItemTag)
            .assertPositionInRootIsEqualTo(50.dp, 0.dp)
    }

    @Test
    fun lazyRowAlignmentBottom() {
        prepareLazyRowForAlignment(Alignment.Bottom)

        rule.onNodeWithTag(firstItemTag)
            .assertPositionInRootIsEqualTo(0.dp, 50.dp)

        rule.onNodeWithTag(secondItemTag)
            .assertPositionInRootIsEqualTo(50.dp, 30.dp)
    }

    @Test
    fun itemFillingParentWidth() {
        rule.setContent {
            LazyRowFor(
                items = listOf(0),
                modifier = Modifier.size(width = 100.dp, height = 150.dp)
            ) {
                Spacer(Modifier.fillParentMaxWidth().height(50.dp).testTag(firstItemTag))
            }
        }

        rule.onNodeWithTag(firstItemTag)
            .assertWidthIsEqualTo(100.dp)
            .assertHeightIsEqualTo(50.dp)
    }

    @Test
    fun itemFillingParentHeight() {
        rule.setContent {
            LazyRowFor(
                items = listOf(0),
                modifier = Modifier.size(width = 100.dp, height = 150.dp)
            ) {
                Spacer(Modifier.width(50.dp).fillParentMaxHeight().testTag(firstItemTag))
            }
        }

        rule.onNodeWithTag(firstItemTag)
            .assertWidthIsEqualTo(50.dp)
            .assertHeightIsEqualTo(150.dp)
    }

    @Test
    fun itemFillingParentSize() {
        rule.setContent {
            LazyRowFor(
                items = listOf(0),
                modifier = Modifier.size(width = 100.dp, height = 150.dp)
            ) {
                Spacer(Modifier.fillParentMaxSize().testTag(firstItemTag))
            }
        }

        rule.onNodeWithTag(firstItemTag)
            .assertWidthIsEqualTo(100.dp)
            .assertHeightIsEqualTo(150.dp)
    }

    @Test
    fun itemFillingParentSizeParentResized() {
        var parentSize by mutableStateOf(100.dp)
        rule.setContent {
            LazyRowFor(
                items = listOf(0),
                modifier = Modifier.size(parentSize)
            ) {
                Spacer(Modifier.fillParentMaxSize().testTag(firstItemTag))
            }
        }

        rule.runOnIdle {
            parentSize = 150.dp
        }

        rule.onNodeWithTag(firstItemTag)
            .assertWidthIsEqualTo(150.dp)
            .assertHeightIsEqualTo(150.dp)
    }

    @Test
    fun scrollsLeftInRtl() {
        val items = (1..4).map { it.toString() }

        rule.setContent {
            Providers(LayoutDirectionAmbient provides LayoutDirection.Rtl) {
                Box(Modifier.preferredWidth(100.dp)) {
                    LazyRowFor(items, Modifier.testTag(LazyRowForTag)) {
                        Spacer(Modifier.preferredWidth(101.dp).fillParentMaxHeight().testTag(it))
                    }
                }
            }
        }

        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = (-150).dp, density = rule.density)

        rule.onNodeWithTag("1")
            .assertDoesNotExist()

        rule.onNodeWithTag("2")
            .assertIsDisplayed()
    }

    @Test
    fun whenNotAnymoreAvailableItemWasDisplayed() {
        var items by mutableStateOf ((1..30).toList())
        rule.setContent {
            LazyRowFor(
                items = items,
                modifier = Modifier.size(100.dp).testTag(LazyRowForTag)
            ) {
                Spacer(Modifier.size(20.dp).testTag("$it"))
            }
        }

        // after scroll we will display items 16-20
        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = 300.dp, density = rule.density)

        rule.runOnIdle {
            items = (1..10).toList()
        }

        // there is no item 16 anymore so we will just display the last items 6-10
        rule.onNodeWithTag("6")
            .assertLeftPositionIsAlmost(0.dp)
    }

    @Test
    fun whenFewDisplayedItemsWereRemoved() {
        var items by mutableStateOf ((1..10).toList())
        rule.setContent {
            LazyRowFor(
                items = items,
                modifier = Modifier.size(100.dp).testTag(LazyRowForTag)
            ) {
                Spacer(Modifier.size(20.dp).testTag("$it"))
            }
        }

        // after scroll we will display items 6-10
        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = 100.dp, density = rule.density)

        rule.runOnIdle {
            items = (1..8).toList()
        }

        // there are no more items 9 and 10, so we have to scroll back
        rule.onNodeWithTag("4")
            .assertLeftPositionIsAlmost(0.dp)
    }

    @Test
    fun whenItemsBecameEmpty() {
        var items by mutableStateOf ((1..10).toList())
        rule.setContent {
            LazyRowFor(
                items = items,
                modifier = Modifier.sizeIn(maxHeight = 100.dp).testTag(LazyRowForTag)
            ) {
                Spacer(Modifier.size(20.dp).testTag("$it"))
            }
        }

        // after scroll we will display items 2-6
        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = 20.dp, density = rule.density)

        rule.runOnIdle {
            items = emptyList()
        }

        // there are no more items so the LazyRow is zero sized
        rule.onNodeWithTag(LazyRowForTag)
            .assertWidthIsEqualTo(0.dp)
            .assertHeightIsEqualTo(0.dp)

        // and has no children
        rule.onNodeWithTag("1")
            .assertDoesNotExist()
        rule.onNodeWithTag("2")
            .assertDoesNotExist()
    }

    @Test
    fun scrollBackAndForth() {
        val items by mutableStateOf ((1..20).toList())
        rule.setContent {
            LazyRowFor(
                items = items,
                modifier = Modifier.size(100.dp).testTag(LazyRowForTag)
            ) {
                Spacer(Modifier.size(20.dp).testTag("$it"))
            }
        }

        // after scroll we will display items 6-10
        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = 100.dp, density = rule.density)

        // and scroll back
        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = (-100).dp, density = rule.density)

        rule.onNodeWithTag("1")
            .assertLeftPositionIsAlmost(0.dp)
    }

    @Test
    fun tryToScrollBackwardWhenAlreadyOnTop() {
        val items by mutableStateOf ((1..20).toList())
        rule.setContent {
            LazyRowFor(
                items = items,
                modifier = Modifier.size(100.dp).testTag(LazyRowForTag)
            ) {
                Spacer(Modifier.size(20.dp).testTag("$it"))
            }
        }

        // we already displaying the first item, so this should do nothing
        rule.onNodeWithTag(LazyRowForTag)
            .scrollBy(x = (-50).dp, density = rule.density)

        rule.onNodeWithTag("1")
            .assertLeftPositionIsAlmost(0.dp)
        rule.onNodeWithTag("5")
            .assertLeftPositionIsAlmost(80.dp)
    }

    private fun SemanticsNodeInteraction.assertLeftPositionIsAlmost(expected: Dp) {
        getUnclippedBoundsInRoot().left.assertIsEqualTo(expected, tolerance = 1.dp)
    }
}

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

package androidx.compose.material

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.TabConstants.defaultTabIndicatorOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ExperimentalSubcomposeLayoutApi
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.util.fastMap
import androidx.compose.ui.util.fastMaxBy

/**
 * A TabRow contains a row of [Tab]s, and displays an indicator underneath the currently
 * selected tab. A TabRow places its tabs evenly spaced along the entire row, with each tab
 * taking up an equal amount of space. See [ScrollableTabRow] for a tab row that does not enforce
 * equal size, and allows scrolling to tabs that do not fit on screen.
 *
 * A simple example with text tabs looks like:
 *
 * @sample androidx.compose.material.samples.TextTabs
 *
 * You can also provide your own custom tab, such as:
 *
 * @sample androidx.compose.material.samples.FancyTabs
 *
 * Where the custom tab itself could look like:
 *
 * @sample androidx.compose.material.samples.FancyTab
 *
 * As well as customizing the tab, you can also provide a custom [indicator], to customize
 * the indicator displayed for a tab. [indicator] will be placed to fill the entire TabRow, so it
 * should internally take care of sizing and positioning the indicator to match changes to
 * [selectedTabIndex].
 *
 * For example, given an indicator that draws a rounded rectangle near the edges of the [Tab]:
 *
 * @sample androidx.compose.material.samples.FancyIndicator
 *
 * We can reuse [TabConstants.defaultTabIndicatorOffset] and just provide this indicator,
 * as we aren't changing how the size and position of the indicator changes between tabs:
 *
 * @sample androidx.compose.material.samples.FancyIndicatorTabs
 *
 * You may also want to use a custom transition, to allow you to dynamically change the
 * appearance of the indicator as it animates between tabs, such as changing its color or size.
 * [indicator] is stacked on top of the entire TabRow, so you just need to provide a custom
 * transition that animates the offset of the indicator from the start of the TabRow. For
 * example, take the following example that uses a transition to animate the offset, width, and
 * color of the same FancyIndicator from before, also adding a physics based 'spring' effect to
 * the indicator in the direction of motion:
 *
 * @sample androidx.compose.material.samples.FancyAnimatedIndicator
 *
 * We can now just pass this indicator directly to TabRow:
 *
 * @sample androidx.compose.material.samples.FancyIndicatorContainerTabs
 *
 * @param selectedTabIndex the index of the currently selected tab
 * @param modifier optional [Modifier] for this TabRow
 * @param backgroundColor The background color for the TabRow. Use [Color.Transparent] to have
 * no color.
 * @param contentColor The preferred content color provided by this TabRow to its children.
 * Defaults to either the matching `onFoo` color for [backgroundColor], or if [backgroundColor] is
 * not a color from the theme, this will keep the same value set above this TabRow.
 * @param indicator the indicator that represents which tab is currently selected. By default this
 * will be a [TabConstants.DefaultIndicator], using a [TabConstants.defaultTabIndicatorOffset]
 * modifier to animate its position. Note that this indicator will be forced to fill up the
 * entire TabRow, so you should use [TabConstants.defaultTabIndicatorOffset] or similar to
 * animate the actual drawn indicator inside this space, and provide an offset from the start.
 * @param divider the divider displayed at the bottom of the TabRow. This provides a layer of
 * separation between the TabRow and the content displayed underneath.
 * @param tabs the tabs inside this TabRow. Typically this will be multiple [Tab]s. Each element
 * inside this lambda will be measured and placed evenly across the TabRow, each taking up equal
 * space.
 */
@OptIn(ExperimentalSubcomposeLayoutApi::class)
@Composable
fun TabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.primarySurface,
    contentColor: Color = contentColorFor(backgroundColor),
    indicator: @Composable (tabPositions: List<TabPosition>) -> Unit = { tabPositions ->
        TabConstants.DefaultIndicator(
            Modifier.defaultTabIndicatorOffset(tabPositions[selectedTabIndex])
        )
    },
    divider: @Composable () -> Unit = {
        TabConstants.DefaultDivider()
    },
    tabs: @Composable () -> Unit
) {
    Surface(modifier = modifier, color = backgroundColor, contentColor = contentColor) {
        SubcomposeLayout<TabSlots>(Modifier.fillMaxWidth()) { constraints ->
            val tabRowWidth = constraints.maxWidth
            val tabMeasurables = subcompose(TabSlots.Tabs, tabs)
            val tabCount = tabMeasurables.size
            val tabWidth = (tabRowWidth / tabCount)
            val tabPlaceables = tabMeasurables.fastMap {
                it.measure(constraints.copy(minWidth = tabWidth, maxWidth = tabWidth))
            }

            val tabRowHeight = tabPlaceables.fastMaxBy { it.height }?.height ?: 0

            val tabPositions = List(tabCount) { index ->
                TabPosition(tabWidth.toDp() * index, tabWidth.toDp())
            }

            layout(tabRowWidth, tabRowHeight) {
                tabPlaceables.fastForEachIndexed { index, placeable ->
                    placeable.place(index * tabWidth, 0)
                }

                subcompose(TabSlots.Divider, divider).fastForEach {
                    val placeable = it.measure(constraints)
                    placeable.place(0, tabRowHeight - placeable.height)
                }

                subcompose(TabSlots.Indicator) {
                    indicator(tabPositions)
                }.fastForEach {
                    it.measure(Constraints.fixed(tabRowWidth, tabRowHeight)).place(0, 0)
                }
            }
        }
    }
}

/**
 * A ScrollableTabRow contains a row of [Tab]s, and displays an indicator underneath the currently
 * selected tab. A ScrollableTabRow places its tabs offset from the starting edge, and allows
 * scrolling to tabs that are placed off screen. For a fixed tab row that does not allow
 * scrolling, and evenly places its tabs, see [TabRow].
 *
 * @param selectedTabIndex the index of the currently selected tab
 * @param modifier optional [Modifier] for this ScrollableTabRow
 * @param backgroundColor The background color for the ScrollableTabRow. Use [Color.Transparent] to
 * have no color.
 * @param contentColor The preferred content color provided by this ScrollableTabRow to its
 * children. Defaults to either the matching `onFoo` color for [backgroundColor], or if
 * [backgroundColor] is not a color from the theme, this will keep the same value set above this
 * ScrollableTabRow.
 * @param edgePadding the padding between the starting and ending edge of ScrollableTabRow, and
 * the tabs inside the ScrollableTabRow. This padding helps inform the user that this tab row can
 * be scrolled, unlike a [TabRow].
 * @param indicator the indicator that represents which tab is currently selected. By default this
 * will be a [TabConstants.DefaultIndicator], using a [TabConstants.defaultTabIndicatorOffset]
 * modifier to animate its position. Note that this indicator will be forced to fill up the
 * entire ScrollableTabRow, so you should use [TabConstants.defaultTabIndicatorOffset] or similar to
 * animate the actual drawn indicator inside this space, and provide an offset from the start.
 * @param divider the divider displayed at the bottom of the ScrollableTabRow. This provides a layer
 * of separation between the ScrollableTabRow and the content displayed underneath.
 * @param tabs the tabs inside this ScrollableTabRow. Typically this will be multiple [Tab]s. Each
 * element inside this lambda will be measured and placed evenly across the TabRow, each taking
 * up equal space.
 */
@OptIn(ExperimentalSubcomposeLayoutApi::class)
@Composable
fun ScrollableTabRow(
    selectedTabIndex: Int,
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colors.primarySurface,
    contentColor: Color = contentColorFor(backgroundColor),
    edgePadding: Dp = TabConstants.DefaultScrollableTabRowPadding,
    indicator: @Composable (tabPositions: List<TabPosition>) -> Unit = { tabPositions ->
        TabConstants.DefaultIndicator(
            Modifier.defaultTabIndicatorOffset(tabPositions[selectedTabIndex])
        )
    },
    divider: @Composable () -> Unit = {
        TabConstants.DefaultDivider()
    },
    tabs: @Composable () -> Unit
) {
    Surface(modifier = modifier, color = backgroundColor, contentColor = contentColor) {
        val scrollState = rememberScrollState()
        val scrollableTabData = remember(scrollState) {
            ScrollableTabData(
                scrollState = scrollState,
                selectedTab = selectedTabIndex
            )
        }
        SubcomposeLayout<TabSlots>(
            Modifier.fillMaxWidth()
                .wrapContentSize(align = Alignment.CenterStart)
                .horizontalScroll(scrollState)
                .clipToBounds()
        ) { constraints ->
            val minTabWidth = ScrollableTabRowMinimumTabWidth.toIntPx()
            val padding = edgePadding.toIntPx()
            val tabConstraints = constraints.copy(minWidth = minTabWidth)

            val tabPlaceables = subcompose(TabSlots.Tabs, tabs)
                .fastMap { it.measure(tabConstraints) }

            var layoutWidth = padding * 2
            var layoutHeight = 0
            tabPlaceables.fastForEach {
                layoutWidth += it.width
                layoutHeight = maxOf(layoutHeight, it.height)
            }

            // Position the children.
            layout(layoutWidth, layoutHeight) {
                // Place the tabs
                val tabPositions = mutableListOf<TabPosition>()
                var left = padding
                tabPlaceables.fastForEach {
                    it.place(left, 0)
                    tabPositions.add(TabPosition(left = left.toDp(), width = it.width.toDp()))
                    left += it.width
                }

                // The divider is measured with its own height, and width equal to the total width
                // of the tab row, and then placed on top of the tabs.
                subcompose(TabSlots.Divider, divider).fastForEach {
                    val placeable = it.measure(
                        constraints.copy(minWidth = layoutWidth, maxWidth = layoutWidth)
                    )
                    placeable.place(0, layoutHeight - placeable.height)
                }

                // The indicator container is measured to fill the entire space occupied by the tab
                // row, and then placed on top of the divider.
                subcompose(TabSlots.Indicator) {
                    indicator(tabPositions)
                }.fastForEach {
                    it.measure(Constraints.fixed(layoutWidth, layoutHeight)).place(0, 0)
                }

                scrollableTabData.onLaidOut(
                    density = this@SubcomposeLayout,
                    edgeOffset = padding,
                    tabPositions = tabPositions,
                    selectedTab = selectedTabIndex
                )
            }
        }
    }
}

/**
 * Data class that contains information about a tab's position on screen, used for calculating
 * where to place the indicator that shows which tab is selected.
 *
 * @property left the left edge's x position from the start of the [TabRow]
 * @property right the right edge's x position from the start of the [TabRow]
 * @property width the width of this tab
 */
@Immutable
data class TabPosition internal constructor(val left: Dp, val width: Dp) {
    val right: Dp get() = left + width
}

private enum class TabSlots {
    Tabs,
    Divider,
    Indicator
}

/**
 * Class holding onto state needed for [ScrollableTabRow]
 */
private class ScrollableTabData(
    private val scrollState: ScrollState,
    private var selectedTab: Int
) {
    fun onLaidOut(
        density: Density,
        edgeOffset: Int,
        tabPositions: List<TabPosition>,
        selectedTab: Int
    ) {
        if (this.selectedTab != selectedTab) {
            this.selectedTab = selectedTab
            tabPositions.getOrNull(selectedTab)?.let {
                // Scrolls to the tab with [tabPosition], trying to place it in the center of the
                // screen or as close to the center as possible.
                val calculatedOffset = it.calculateTabOffset(density, edgeOffset, tabPositions)
                scrollState.smoothScrollTo(calculatedOffset)
            }
        }
    }

    /**
     * @return the offset required to horizontally center the tab inside this TabRow.
     * If the tab is at the start / end, and there is not enough space to fully centre the tab, this
     * will just clamp to the min / max position given the max width.
     */
    private fun TabPosition.calculateTabOffset(
        density: Density,
        edgeOffset: Int,
        tabPositions: List<TabPosition>
    ): Float = with(density) {
        val totalTabRowWidth = tabPositions.last().right.toIntPx() + edgeOffset
        val visibleWidth = totalTabRowWidth - scrollState.maxValue.toInt()
        val tabOffset = left.toIntPx()
        val scrollerCenter = visibleWidth / 2
        val tabWidth = width.toIntPx()
        val centeredTabOffset = tabOffset - (scrollerCenter - tabWidth / 2)
        // How much space we have to scroll. If the visible width is <= to the total width, then
        // we have no space to scroll as everything is always visible.
        val availableSpace = (totalTabRowWidth - visibleWidth).coerceAtLeast(0)
        return centeredTabOffset.coerceIn(0, availableSpace).toFloat()
    }
}

private val ScrollableTabRowMinimumTabWidth = 90.dp

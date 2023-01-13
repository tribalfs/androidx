/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.tv.material3

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.compose.ui.zIndex

/**
 * TV-Material Design Horizontal TabRow
 *
 * Display all tabs in a set simultaneously and if the tabs exceed the container size, it has
 * scrolling to navigate to next tab. They are best for switching between related content quickly,
 * such as between transportation methods in a map. To navigate between tabs, use d-pad left or
 * d-pad right when focused.
 *
 * A TvTabRow contains a row of []s, and displays an indicator underneath the currently selected
 * tab. A TvTabRow places its tabs offset from the starting edge, and allows scrolling to tabs that
 * are placed off screen.
 *
 * Examples:
 * @sample androidx.tv.samples.PillIndicatorTabRow
 * @sample androidx.tv.samples.UnderlinedIndicatorTabRow
 * @sample androidx.tv.samples.TabRowWithDebounce
 * @sample androidx.tv.samples.OnClickNavigation
 *
 * @param selectedTabIndex the index of the currently selected tab
 * @param modifier the [Modifier] to be applied to this tab row
 * @param containerColor the color used for the background of this tab row
 * @param contentColor the primary color used in the tabs
 * @param separator use this composable to add a separator between the tabs
 * @param indicator used to indicate which tab is currently selected and/or focused
 * @param tabs a composable which will render all the tabs
 */
@ExperimentalTvMaterial3Api // TODO (b/263353219): Remove this before launching beta
@Composable
fun TabRow(
  selectedTabIndex: Int,
  modifier: Modifier = Modifier,
  containerColor: Color = TabRowDefaults.ContainerColor,
  contentColor: Color = TabRowDefaults.contentColor(),
  separator: @Composable () -> Unit = { TabRowDefaults.TabSeparator() },
  indicator: @Composable (tabPositions: List<DpRect>) -> Unit =
    @Composable { tabPositions ->
      tabPositions.getOrNull(selectedTabIndex)?.let {
        TabRowDefaults.PillIndicator(currentTabPosition = it)
      }
    },
  tabs: @Composable () -> Unit
) {
  val scrollState = rememberScrollState()
  var isAnyTabFocused by remember { mutableStateOf(false) }

  CompositionLocalProvider(
    LocalTabRowHasFocus provides isAnyTabFocused,
    LocalContentColor provides contentColor
  ) {
    SubcomposeLayout(
      modifier =
        modifier
          .background(containerColor)
          .clipToBounds()
          .horizontalScroll(scrollState)
          .onFocusChanged { isAnyTabFocused = it.hasFocus }
          .selectableGroup()
    ) { constraints ->
      // Tab measurables
      val tabMeasurables = subcompose(TabRowSlots.Tabs, tabs)

      // Tab placeables
      val tabPlaceables =
        tabMeasurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
      val tabsCount = tabMeasurables.size
      val separatorsCount = tabsCount - 1

      // Separators
      val separators = @Composable { repeat(separatorsCount) { separator() } }
      val separatorMeasurables = subcompose(TabRowSlots.Separator, separators)
      val separatorPlaceables =
        separatorMeasurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
      val separatorWidth = separatorPlaceables.firstOrNull()?.width ?: 0

      val layoutWidth = tabPlaceables.sumOf { it.width } + separatorsCount * separatorWidth
      val layoutHeight =
        (tabMeasurables.maxOfOrNull { it.maxIntrinsicHeight(Constraints.Infinity) } ?: 0)
          .coerceAtLeast(0)

      // Position the children
      layout(layoutWidth, layoutHeight) {

        // Place the tabs
        val tabPositions = mutableListOf<DpRect>()
        var left = 0
        tabPlaceables.forEachIndexed { index, tabPlaceable ->
          // place the tab
          tabPlaceable.placeRelative(left, 0)

          tabPositions.add(
            this@SubcomposeLayout.buildTabPosition(placeable = tabPlaceable, initialLeft = left)
          )
          left += tabPlaceable.width

          // place the separator
          if (tabPlaceables.lastIndex != index) {
            separatorPlaceables[index].placeRelative(left, 0)
          }

          left += separatorWidth
        }

        // Place the indicator
        subcompose(TabRowSlots.Indicator) { indicator(tabPositions) }
          .forEach { it.measure(Constraints.fixed(layoutWidth, layoutHeight)).placeRelative(0, 0) }
      }
    }
  }
}

@ExperimentalTvMaterial3Api // TODO (b/263353219): Remove this before launching beta
object TabRowDefaults {
  /** Color of the background of a tab */
  val ContainerColor = Color.Transparent

  /** Space between tabs in the tab row */
  @Composable
  fun TabSeparator() {
    Spacer(modifier = Modifier.width(20.dp))
  }

  /** Default accent color for the TabRow */
  // TODO: Use value from a theme
  @Composable fun contentColor(): Color = Color(0xFFC9C5D0)

  /**
   * Adds a pill indicator behind the tab
   *
   * @param currentTabPosition position of the current selected tab
   * @param modifier modifier to be applied to the indicator
   * @param activeColor color of indicator when [TabRow] is active
   * @param inactiveColor color of indicator when [TabRow] is inactive
   */
  @Composable
  fun PillIndicator(
    currentTabPosition: DpRect,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFE5E1E6),
    inactiveColor: Color = Color(0xFF484362).copy(alpha = 0.4f)
  ) {
    val anyTabFocused = LocalTabRowHasFocus.current
    val width by animateDpAsState(targetValue = currentTabPosition.width)
    val height = currentTabPosition.height
    val leftOffset by animateDpAsState(targetValue = currentTabPosition.left)
    val topOffset = currentTabPosition.top

    val pillColor by
      animateColorAsState(targetValue = if (anyTabFocused) activeColor else inactiveColor)

    Box(
      modifier
        .fillMaxWidth()
        .wrapContentSize(Alignment.BottomStart)
        .offset(x = leftOffset, y = topOffset)
        .width(width)
        .height(height)
        .background(color = pillColor, shape = RoundedCornerShape(50))
        .zIndex(-1f)
    )
  }

  /**
   * Adds an underlined indicator below the tab
   *
   * @param currentTabPosition position of the current selected tab
   * @param modifier modifier to be applied to the indicator
   * @param activeColor color of indicator when [TabRow] is active
   * @param inactiveColor color of indicator when [TabRow] is inactive
   */
  @Composable
  fun UnderlinedIndicator(
    currentTabPosition: DpRect,
    modifier: Modifier = Modifier,
    activeColor: Color = Color(0xFFC9BFFF),
    inactiveColor: Color = Color(0xFFC9C2E8)
  ) {
    val anyTabFocused = LocalTabRowHasFocus.current
    val unfocusedUnderlineWidth = 10.dp
    val indicatorHeight = 2.dp
    val width by
      animateDpAsState(
        targetValue = if (anyTabFocused) currentTabPosition.width else unfocusedUnderlineWidth
      )
    val leftOffset by
      animateDpAsState(
        targetValue =
          if (anyTabFocused) {
            currentTabPosition.left
          } else {
            val tabCenter = currentTabPosition.left + currentTabPosition.width / 2
            tabCenter - unfocusedUnderlineWidth / 2
          }
      )

    val underlineColor by
      animateColorAsState(targetValue = if (anyTabFocused) activeColor else inactiveColor)

    Box(
      modifier
        .fillMaxWidth()
        .wrapContentSize(Alignment.BottomStart)
        .offset(x = leftOffset)
        .width(width)
        .height(indicatorHeight)
        .background(color = underlineColor)
    )
  }
}

/** A provider to store whether any [Tab] is focused inside the [TabRow] */
internal val LocalTabRowHasFocus = compositionLocalOf { false }

/** Slots for [TabRow]'s content */
private enum class TabRowSlots {
  Tabs,
  Indicator,
  Separator
}

/** Builds TabPosition based on placeable */
private fun Density.buildTabPosition(
  placeable: Placeable,
  initialLeft: Int = 0,
  initialTop: Int = 0,
): DpRect =
  DpRect(
    left = initialLeft.toDp(),
    right = (initialLeft + placeable.width).toDp(),
    top = initialTop.toDp(),
    bottom = (initialTop + placeable.height).toDp(),
  )

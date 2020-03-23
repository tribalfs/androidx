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

package androidx.ui.material

import androidx.compose.Composable
import androidx.ui.core.Alignment
import androidx.ui.core.FirstBaseline
import androidx.ui.core.LastBaseline
import androidx.ui.core.Layout
import androidx.ui.core.Modifier
import androidx.ui.foundation.Box
import androidx.ui.foundation.Clickable
import androidx.ui.foundation.ContentGravity
import androidx.ui.foundation.Image
import androidx.ui.foundation.ProvideTextStyle
import androidx.ui.foundation.Text
import androidx.ui.graphics.ImageAsset
import androidx.ui.layout.LayoutGravity
import androidx.ui.layout.LayoutHeight
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutSize
import androidx.ui.layout.LayoutWidth
import androidx.ui.layout.Row
import androidx.ui.material.ripple.ripple
import androidx.ui.text.TextStyle
import androidx.ui.text.style.TextOverflow
import androidx.ui.unit.Dp
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.dp
import androidx.ui.unit.ipx
import androidx.ui.unit.max

/**
 * Material Design implementation of [list items](https://material.io/components/lists).
 *
 * This component can be used to achieve the list item templates existing in the spec. For example:
 * - one-line items
 * @sample androidx.ui.material.samples.OneLineListItems
 * - two-line items
 * @sample androidx.ui.material.samples.TwoLineListItems
 * - three-line items
 * @sample androidx.ui.material.samples.ThreeLineListItems
 *
 * @param text The primary text of the list item
 * @param icon The leading supporting visual of the list item
 * @param secondaryText The secondary text of the list item
 * @param singleLineSecondaryText Whether the secondary text is single line
 * @param overlineText The text displayed above the primary text
 * @param metaText The meta text to be displayed in the trailing position
 * @param onClick Callback to be invoked when the list item is clicked
 */
@Composable
fun ListItem(
    text: String,
    icon: ImageAsset? = null,
    secondaryText: String? = null,
    // TODO(popam): find a way to remove this
    singleLineSecondaryText: Boolean = true,
    overlineText: String? = null,
    metaText: String? = null,
    onClick: (() -> Unit)? = null
) {
    val iconComposable: @Composable() (() -> Unit)? = icon?.let {
        { Image(it) }
    }
    val textComposable: @Composable() () -> Unit = text.let {
        { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    val secondaryTextComposable: @Composable() (() -> Unit)? = secondaryText?.let {
        {
            val maxLines = if (!singleLineSecondaryText && overlineText == null) 2 else 1
            Text(it, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
        }
    }
    val overlineTextComposable: @Composable() (() -> Unit)? = overlineText?.let {
        { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    val metaTextComposable: @Composable() (() -> Unit)? = metaText?.let {
        { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
    ListItem(
        textComposable,
        iconComposable,
        secondaryTextComposable,
        singleLineSecondaryText,
        overlineTextComposable,
        metaTextComposable,
        onClick
    )
}

/**
 * Material Design implementation of [list items](https://material.io/components/lists).
 *
 * This component can be used to achieve the list item templates existing in the spec. For example:
 * - one-line items
 * @sample androidx.ui.material.samples.OneLineListItems
 * - two-line items
 * @sample androidx.ui.material.samples.TwoLineListItems
 * - three-line items
 * @sample androidx.ui.material.samples.ThreeLineListItems
 *
 * @param text The primary text of the list item
 * @param icon The leading supporting visual of the list item
 * @param secondaryText The secondary text of the list item
 * @param singleLineSecondaryText Whether the secondary text is single line
 * @param overlineText The text displayed above the primary text
 * @param trailing The trailing meta text or meta icon of the list item
 * @param onClick Callback to be invoked when the list item is clicked
 */
@Composable
fun ListItem(
    text: @Composable() (() -> Unit),
    icon: @Composable() (() -> Unit)? = null,
    secondaryText: @Composable() (() -> Unit)? = null,
    singleLineSecondaryText: Boolean = true,
    overlineText: @Composable() (() -> Unit)? = null,
    trailing: @Composable() (() -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    val emphasisLevels = MaterialTheme.emphasisLevels
    val typography = MaterialTheme.typography

    val styledText = applyTextStyle(typography.subtitle1, emphasisLevels.high, text)!!
    val styledSecondaryText = applyTextStyle(typography.body2, emphasisLevels.medium, secondaryText)
    val styledOverlineText = applyTextStyle(typography.overline, emphasisLevels.high, overlineText)
    val styledTrailing = applyTextStyle(typography.caption, emphasisLevels.high, trailing)

    val item = @Composable {
        if (styledSecondaryText == null && styledOverlineText == null) {
            OneLine.ListItem(icon, styledText, styledTrailing)
        } else if ((styledOverlineText == null && singleLineSecondaryText) ||
            styledSecondaryText == null
        ) {
            TwoLine.ListItem(
                icon,
                styledText,
                styledSecondaryText,
                styledOverlineText,
                styledTrailing
            )
        } else {
            ThreeLine.ListItem(
                icon,
                styledText,
                styledSecondaryText,
                styledOverlineText,
                styledTrailing
            )
        }
    }

    if (onClick != null) {
        val rippleColor = MaterialTheme.colors.onSurface.copy(alpha = RippleOpacity)
        Clickable(onClick = onClick, children = item, modifier = ripple(color = rippleColor))
    } else {
        item()
    }
}

private object OneLine {
    // TODO(popam): support wide icons
    // TODO(popam): convert these to sp
    // List item related constants.
    private val MinHeight = 48.dp
    private val MinHeightWithIcon = 56.dp
    // Icon related constants.
    private val IconMinPaddedWidth = 40.dp
    private val IconLeftPadding = 16.dp
    private val IconVerticalPadding = 8.dp
    // Content related constants.
    private val ContentLeftPadding = 16.dp
    private val ContentRightPadding = 16.dp
    // Trailing related constants.
    private val TrailingRightPadding = 16.dp

    @Composable
    fun ListItem(
        icon: @Composable() (() -> Unit)?,
        text: @Composable() (() -> Unit),
        trailing: @Composable() (() -> Unit)?
    ) {
        val minHeight = if (icon == null) MinHeight else MinHeightWithIcon
        Row(LayoutHeight.Min(minHeight)) {
            if (icon != null) {
                Box(
                    modifier = LayoutGravity.Center +
                            LayoutWidth.Min(IconLeftPadding + IconMinPaddedWidth),
                    gravity = ContentGravity.CenterStart,
                    paddingStart = IconLeftPadding,
                    paddingTop = IconVerticalPadding,
                    paddingBottom = IconVerticalPadding,
                    children = icon
                )
            }
            Box(
                modifier = LayoutWeight(1f) + LayoutGravity.Center +
                        LayoutPadding(start = ContentLeftPadding, end = ContentRightPadding),
                gravity = ContentGravity.CenterStart,
                children = text
            )
            if (trailing != null) {
                Box(
                    LayoutGravity.Center,
                    paddingEnd = TrailingRightPadding,
                    children = trailing
                )
            }
        }
    }
}

private object TwoLine {
    // List item related constants.
    private val MinHeight = 64.dp
    private val MinHeightWithIcon = 72.dp
    // Icon related constants.
    private val IconMinPaddedWidth = 40.dp
    private val IconLeftPadding = 16.dp
    private val IconVerticalPadding = 16.dp
    // Content related constants.
    private val ContentLeftPadding = 16.dp
    private val ContentRightPadding = 16.dp
    private val OverlineBaselineOffset = 24.dp
    private val OverlineToPrimaryBaselineOffset = 20.dp
    private val PrimaryBaselineOffsetNoIcon = 28.dp
    private val PrimaryBaselineOffsetWithIcon = 32.dp
    private val PrimaryToSecondaryBaselineOffsetNoIcon = 20.dp
    private val PrimaryToSecondaryBaselineOffsetWithIcon = 20.dp
    // Trailing related constants.
    private val TrailingRightPadding = 16.dp

    @Composable
    fun ListItem(
        icon: @Composable() (() -> Unit)?,
        text: @Composable() (() -> Unit),
        secondaryText: @Composable() (() -> Unit)?,
        overlineText: @Composable() (() -> Unit)?,
        trailing: @Composable() (() -> Unit)?
    ) {
        val minHeight = if (icon == null) MinHeight else MinHeightWithIcon
        Row(LayoutHeight.Min(minHeight)) {
            val modifier = LayoutWeight(1f) + LayoutPadding(
                start = ContentLeftPadding,
                end = ContentRightPadding
            )

            if (icon != null) {
                Box(
                    LayoutSize.Min(IconLeftPadding + IconMinPaddedWidth, minHeight),
                    gravity = ContentGravity.TopStart,
                    paddingStart = IconLeftPadding,
                    paddingTop = IconVerticalPadding,
                    paddingBottom = IconVerticalPadding,
                    children = icon
                )
            }

            if (overlineText != null) {
                BaselinesOffsetColumn(
                    listOf(OverlineBaselineOffset, OverlineToPrimaryBaselineOffset),
                    modifier
                ) {
                    overlineText()
                    text()
                }
            } else {
                BaselinesOffsetColumn(
                    listOf(
                        if (icon != null) {
                            PrimaryBaselineOffsetWithIcon
                        } else {
                            PrimaryBaselineOffsetNoIcon
                        },
                        if (icon != null) {
                            PrimaryToSecondaryBaselineOffsetWithIcon
                        } else {
                            PrimaryToSecondaryBaselineOffsetNoIcon
                        }
                    ),
                    modifier
                ) {
                    text()
                    secondaryText!!()
                }
            }
            if (trailing != null) {
                OffsetToBaselineOrCenter(
                    if (icon != null) {
                        PrimaryBaselineOffsetWithIcon
                    } else {
                        PrimaryBaselineOffsetNoIcon
                    }
                ) {
                    Box(
                        // TODO(popam): find way to center and wrap content without minHeight
                        LayoutHeight.Min(minHeight) + LayoutPadding(end = TrailingRightPadding),
                        gravity = ContentGravity.Center,
                        children = trailing
                    )
                }
            }
        }
    }
}

private object ThreeLine {
    // List item related constants.
    private val MinHeight = 88.dp
    // Icon related constants.
    private val IconMinPaddedWidth = 40.dp
    private val IconLeftPadding = 16.dp
    private val IconThreeLineVerticalPadding = 16.dp
    // Content related constants.
    private val ContentLeftPadding = 16.dp
    private val ContentRightPadding = 16.dp
    private val ThreeLineBaselineFirstOffset = 28.dp
    private val ThreeLineBaselineSecondOffset = 20.dp
    private val ThreeLineBaselineThirdOffset = 20.dp
    private val ThreeLineTrailingTopPadding = 16.dp
    // Trailing related constants.
    private val TrailingRightPadding = 16.dp

    @Composable
    fun ListItem(
        icon: @Composable() (() -> Unit)?,
        text: @Composable() (() -> Unit),
        secondaryText: @Composable() (() -> Unit),
        overlineText: @Composable() (() -> Unit)?,
        trailing: @Composable() (() -> Unit)?
    ) {
        Row(LayoutHeight.Min(MinHeight)) {
            if (icon != null) {
                Box(
                    LayoutSize.Min(IconLeftPadding + IconMinPaddedWidth),
                    gravity = ContentGravity.CenterStart,
                    paddingStart = IconLeftPadding,
                    paddingTop = IconThreeLineVerticalPadding,
                    paddingBottom = IconThreeLineVerticalPadding,
                    children = icon
                )
            }
            BaselinesOffsetColumn(
                listOf(
                    ThreeLineBaselineFirstOffset,
                    ThreeLineBaselineSecondOffset,
                    ThreeLineBaselineThirdOffset
                ),
                LayoutWeight(1f) +
                        LayoutPadding(start = ContentLeftPadding, end = ContentRightPadding)
            ) {
                if (overlineText != null) overlineText()
                text()
                secondaryText()
            }
            if (trailing != null) {
                OffsetToBaselineOrCenter(
                    ThreeLineBaselineFirstOffset - ThreeLineTrailingTopPadding,
                    LayoutPadding(top = ThreeLineTrailingTopPadding, end = TrailingRightPadding),
                    trailing
                )
            }
        }
    }
}

/**
 * Layout that expects [Text] children, and positions them with specific offsets between the
 * top of the layout and the first text, as well as the last baseline and first baseline
 * for subsequent pairs of texts.
 */
// TODO(popam): consider making this a layout composable in ui-layout.
@Composable
private fun BaselinesOffsetColumn(
    offsets: List<Dp>,
    modifier: Modifier = Modifier.None,
    children: @Composable() () -> Unit
) {
    Layout(children, modifier) { measurables, constraints, _ ->
        val childConstraints = constraints.copy(minHeight = 0.ipx, maxHeight = IntPx.Infinity)
        val placeables = measurables.map { it.measure(childConstraints) }

        val containerWidth = placeables.fold(0.ipx) { maxWidth, placeable ->
            max(maxWidth, placeable.width)
        }
        val y = Array(placeables.size) { 0.ipx }
        var containerHeight = 0.ipx
        placeables.forEachIndexed { index, placeable ->
            val toPreviousBaseline = if (index > 0) {
                placeables[index - 1].height - placeables[index - 1][LastBaseline]!!
            } else 0.ipx
            val topPadding = max(
                0.ipx,
                offsets[index].toIntPx() - placeable[FirstBaseline]!! - toPreviousBaseline
            )
            y[index] = topPadding + containerHeight
            containerHeight += topPadding + placeable.height
        }

        layout(containerWidth, containerHeight) {
            placeables.forEachIndexed { index, placeable ->
                placeable.place(0.ipx, y[index])
            }
        }
    }
}

/**
 * Layout that takes a child and adds the necessary padding such that the first baseline of the
 * child is at a specific offset from the top of the container. If the child does not have
 * a first baseline, the layout will match the minHeight constraint and will center the
 * child.
 */
// TODO(popam): support fallback alignment in AlignmentLineOffset, and use that here.
@Composable
private fun OffsetToBaselineOrCenter(
    offset: Dp,
    modifier: Modifier = Modifier.None,
    children: @Composable() () -> Unit
) {
    Layout(children, modifier) { measurables, constraints, _ ->
        val placeable = measurables[0].measure(constraints.copy(minHeight = 0.ipx))
        val baseline = placeable[FirstBaseline]
        val y: IntPx
        val containerHeight: IntPx
        if (baseline != null) {
            y = offset.toIntPx() - baseline
            containerHeight = max(constraints.minHeight, y + placeable.height)
        } else {
            containerHeight = max(constraints.minHeight, placeable.height)
            y = Alignment.Center
                .align(IntPxSize(0.ipx, containerHeight - placeable.height)).y
        }
        layout(placeable.width, containerHeight) {
            placeable.place(0.ipx, y)
        }
    }
}

private fun applyTextStyle(
    textStyle: TextStyle,
    emphasis: Emphasis,
    children: @Composable() (() -> Unit)?
): @Composable() (() -> Unit)? {
    if (children == null) return null
    return {
        ProvideEmphasis(emphasis) {
            ProvideTextStyle(textStyle, children)
        }
    }
}

// Material spec values.
private const val RippleOpacity = 0.16f

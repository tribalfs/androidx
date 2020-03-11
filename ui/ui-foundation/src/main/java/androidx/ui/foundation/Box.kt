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

package androidx.ui.foundation

import androidx.compose.Composable
import androidx.compose.emptyContent
import androidx.ui.core.Alignment
import androidx.ui.core.Layout
import androidx.ui.core.Modifier
import androidx.ui.core.offset
import androidx.ui.foundation.shape.RectangleShape
import androidx.ui.graphics.Color
import androidx.ui.graphics.Shape
import androidx.ui.unit.Dp
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.dp
import androidx.ui.unit.ipx
import androidx.ui.unit.max

/**
 * A convenience composable that combines common layout and draw logic.
 *
 * In order to define the size of the [Box], the [androidx.ui.layout.LayoutWidth],
 * [androidx.ui.layout.LayoutHeight] and [androidx.ui.layout.LayoutSize] modifiers can be used.
 * The [Box] will try to be only as small as its content. However, if it is constrained
 * otherwise, [Box] will allow its content to be smaller and will position the content inside,
 * according to [gravity].
 *
 * The specified [padding] will be applied inside the [Box]. In order to apply padding outside
 * the [Box], the [androidx.ui.layout.LayoutPadding] modifier should be used.
 *
 * @sample androidx.ui.foundation.samples.SimpleCircleBox
 *
 * @param modifier The modifier to be applied to the Box
 * @param shape The shape of the box
 * @param backgroundColor The [Color] for background with. If [Color.Transparent], there will be no
 * background
 * @param border [Border] object that specifies border appearance, such as size and color. If
 * `null`, there will be no border
 * @param padding The padding to be applied inside Box, along its edges. Unless otherwise
 * specified, content will be padded by the [Border.size], if [border] is provided
 * @param paddingStart sets the padding of the start edge. Setting this will override [padding]
 * for the start edge
 * @param paddingTop sets the padding of the top edge. Setting this will override [padding] for
 * the top edge
 * @param paddingEnd sets the padding of the end edge. Setting this will override [padding] for
 * the end edge
 * @param paddingBottom sets the padding of the bottom edge. Setting this will override [padding]
 * for the bottom edge
 * @param gravity The gravity of the content inside Box
 */
@Composable
fun Box(
    modifier: Modifier = Modifier.None,
    shape: Shape = RectangleShape,
    backgroundColor: Color = Color.Transparent,
    border: Border? = null,
    padding: Dp = border?.size ?: 0.dp,
    paddingStart: Dp = Dp.Unspecified,
    paddingTop: Dp = Dp.Unspecified,
    paddingEnd: Dp = Dp.Unspecified,
    paddingBottom: Dp = Dp.Unspecified,
    gravity: ContentGravity = ContentGravity.TopStart,
    children: @Composable() () -> Unit = emptyContent()
) {
    val borderModifier =
        if (border != null) DrawBorder(border, shape) else Modifier.None
    val backgroundModifier =
        if (backgroundColor == Color.Transparent) {
            Modifier.None
        } else {
            Modifier.drawBackground(backgroundColor, shape)
        }
    // TODO(malkov): support ContentColor prorogation (b/148129218)
    // TODO(popam): there should be no custom layout, use Column instead (b/148809177)
    Layout(
        children,
        modifier + backgroundModifier + borderModifier
    ) { measurables, constraints, _ ->
        val startPadding = if (paddingStart != Dp.Unspecified) paddingStart else padding
        val topPadding = if (paddingTop != Dp.Unspecified) paddingTop else padding
        val endPadding = if (paddingEnd != Dp.Unspecified) paddingEnd else padding
        val bottomPadding = if (paddingBottom != Dp.Unspecified) paddingBottom else padding
        val totalHorizontal = startPadding.toIntPx() + endPadding.toIntPx()
        val totalVertical = topPadding.toIntPx() + bottomPadding.toIntPx()

        val childConstraints = constraints
            .offset(-totalHorizontal, -totalVertical)
            .copy(minWidth = 0.ipx, minHeight = 0.ipx)

        val placeables = measurables.map { it.measure(childConstraints) }
        var containerWidth = constraints.minWidth
        var containerHeight = constraints.minHeight
        placeables.forEach {
            containerWidth = max(containerWidth, it.width + totalHorizontal)
            containerHeight = max(containerHeight, it.height + totalVertical)
        }

        layout(containerWidth, containerHeight) {
            placeables.forEach {
                val position = gravity.align(
                    IntPxSize(
                        containerWidth - it.width - totalHorizontal,
                        containerHeight - it.height - totalVertical
                    )
                )
                it.place(
                    startPadding.toIntPx() + position.x,
                    topPadding.toIntPx() + position.y
                )
            }
        }
    }
}

// TODO(popam/148014745): add a Gravity class consistent with cross axis alignment for Row/Column
typealias ContentGravity = Alignment
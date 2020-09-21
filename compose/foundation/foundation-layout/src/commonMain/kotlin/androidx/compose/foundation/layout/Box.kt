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

package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Layout
import androidx.compose.ui.Measurable
import androidx.compose.ui.Modifier
import androidx.compose.ui.ParentDataModifier
import androidx.compose.ui.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import kotlin.math.max

/**
 * A layout composable that positions its children relative to its edges.
 * The component is useful for drawing children that overlap. The children will always be
 * drawn in the order they are specified in the body of the [Box].
 * When children are smaller than the parent, by default they will be positioned inside the [Box]
 * according to the [alignment]. If individual alignment of the children is needed, apply the
 * [BoxScope.align] modifier to a child to specify its alignment.
 *
 * Example usage:
 *
 * @sample androidx.compose.foundation.layout.samples.SimpleBox
 *
 * @param modifier The modifier to be applied to the layout.
 * @param alignment The default alignment inside the Box.
 */
@Composable
fun Box(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopStart,
    children: @Composable BoxScope.() -> Unit
) {
    val boxChildren: @Composable () -> Unit = { BoxScope.children() }

    Layout(boxChildren, modifier = modifier) { measurables, constraints ->
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        // First measure aligned children to get the size of the layout.
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0)
        (0 until measurables.size).filter { i -> !measurables[i].stretch }.forEach { i ->
            placeables[i] = measurables[i].measure(childConstraints)
        }
        val (boxWidth, boxHeight) = with(placeables.filterNotNull()) {
            Pair(
                max(maxByOrNull { it.width }?.width ?: 0, constraints.minWidth),
                max(maxByOrNull { it.height }?.height ?: 0, constraints.minHeight)
            )
        }

        // Now measure stretch children.
        (0 until measurables.size).filter { i -> measurables[i].stretch }.forEach { i ->
            // infinity check is needed for intrinsic measurements
            val minWidth = if (boxWidth != Constraints.Infinity) boxWidth else 0
            val minHeight = if (boxHeight != Constraints.Infinity) boxHeight else 0
            placeables[i] = measurables[i].measure(
                Constraints(minWidth, boxWidth, minHeight, boxHeight)
            )
        }

        // Position the children.
        layout(boxWidth, boxHeight) {
            (0 until measurables.size).forEach { i ->
                val measurable = measurables[i]
                val childAlignment = measurable.boxChildData?.alignment ?: alignment
                val placeable = placeables[i]!!

                val position = childAlignment.align(
                    IntSize(
                        boxWidth - placeable.width,
                        boxHeight - placeable.height
                    ),
                    layoutDirection
                )
                placeable.place(position.x, position.y)
            }
        }
    }
}

/**
 * A convenience box with no content that can participate in layout, drawing, pointer input
 * due to the [modifier] applied to it.
 *
 * Example usage:
 *
 * @sample androidx.compose.foundation.layout.samples.SimpleBox
 *
 * @param modifier The modifier to be applied to the layout.
 */
@Composable
fun Box(modifier: Modifier) {
    Layout({}, modifier = modifier) { _, constraints ->
        layout(constraints.minWidth, constraints.minHeight) {}
    }
}

@Composable
@Deprecated(
    "Stack was renamed to Box.",
    ReplaceWith("Box", "androidx.compose.foundation.layout.Box")
)
fun Stack(
    modifier: Modifier = Modifier,
    alignment: Alignment = Alignment.TopStart,
    children: @Composable BoxScope.() -> Unit
) = Box(modifier, alignment, children)

/**
 * A BoxScope provides a scope for the children of a [Box].
 */
@LayoutScopeMarker
@Immutable
interface BoxScope {
    /**
     * Pull the content element to a specific [Alignment] within the [Box]. This alignment will
     * have priority over the [Box]'s `alignment` parameter.
     */
    @Stable
    fun Modifier.align(alignment: Alignment) = this.then(BoxChildData(alignment, false))

    @Stable
    @Deprecated("gravity has been renamed to align.", ReplaceWith("align(align)"))
    fun Modifier.gravity(align: Alignment) = this.then(BoxChildData(align, false))

    /**
     * Size the element to match the size of the [Box] after all other content elements have
     * been measured.
     *
     * The element using this modifier does not take part in defining the size of the [Box].
     * Instead, it matches the size of the [Box] after all other children (not using
     * matchParentSize() modifier) have been measured to obtain the [Box]'s size.
     * In contrast, a general-purpose [Modifier.fillMaxSize] modifier, which makes an element
     * occupy all available space, will take part in defining the size of the [Box]. Consequently,
     * using it for an element inside a [Box] will make the [Box] itself always fill the
     * available space.
     */
    @Stable
    fun Modifier.matchParentSize() = this.then(StretchAlignModifier)

    companion object : BoxScope
}

@Deprecated(
    "Stack was renamed to Box.",
    ReplaceWith("BoxScope", "androidx.compose.foundation.layout.BoxScope")
)
typealias StackScope = BoxScope

@Stable
private val StretchAlignModifier: ParentDataModifier = BoxChildData(Alignment.Center, true)

private val Measurable.boxChildData: BoxChildData? get() = parentData as? BoxChildData
private val Measurable.stretch: Boolean get() = boxChildData?.stretch ?: false

private data class BoxChildData(
    var alignment: Alignment,
    var stretch: Boolean = false
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?) = this@BoxChildData
}

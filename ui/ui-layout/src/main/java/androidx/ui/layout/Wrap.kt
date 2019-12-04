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

package androidx.ui.layout

import androidx.ui.core.IntPxSize
import androidx.ui.core.Layout
import androidx.ui.core.ipx
import androidx.ui.core.looseMin
import androidx.ui.core.max
import androidx.compose.Composable
import androidx.ui.core.Alignment
import androidx.ui.core.Constraints
import androidx.ui.core.DensityScope
import androidx.ui.core.IntPxPosition
import androidx.ui.core.LayoutModifier

/**
 * A layout that expects a child and places it within itself. The child will be measured
 * with the same max constraints received by the parent, and 0 ipx min constraints.
 * The parent will try to size itself to be as large as the child. If this is not possible
 * (the child is too small and does not satisfy the min constraints of the parent), the parent
 * will size itself to min constraints and the child will be aligned according to the alignment.
 *
 * For a composable that does alignment and tries to be as large as possible, see [Align].
 */
@Composable
fun Wrap(alignment: Alignment = Alignment.TopLeft, children: @Composable() () -> Unit) {
    Layout(children) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
        // The child cannot be larger than our max constraints, but we ignore min constraints.
        val placeable = measurable?.measure(constraints.looseMin())

        // Try to be as small as possible.
        val layoutWidth = max(placeable?.width ?: 0.ipx, constraints.minWidth)
        val layoutHeight = max(placeable?.height ?: 0.ipx, constraints.minHeight)

        layout(layoutWidth, layoutHeight) {
            if (placeable != null) {
                val position = alignment.align(
                    IntPxSize(layoutWidth - placeable.width, layoutHeight - placeable.height)
                )
                placeable.place(position.x, position.y)
            }
        }
    }
}

/**
 * A layout modifier that tries to size itself to the size of the target component.
 * To achieve this, the modifier measures the target layout with the max incoming constraints
 * and zero min constraints, and then sizes itself to the measured size. If the measured size of
 * the target layout is smaller than the incoming min constraints, the modified component will
 * size itself to min incoming constraints and place its content in the center.
 */
val Wrapped: LayoutModifier = object : LayoutModifier {
    override fun DensityScope.modifyConstraints(constraints: Constraints) = constraints.looseMin()

    override fun DensityScope.modifySize(
        constraints: Constraints,
        childSize: IntPxSize
    ): IntPxSize {
        val width = max(childSize.width, constraints.minWidth)
        val height = max(childSize.height, constraints.minHeight)
        return IntPxSize(width, height)
    }

    override fun DensityScope.modifyPosition(
        childSize: IntPxSize,
        containerSize: IntPxSize
    ): IntPxPosition {
        return Alignment.Center.align(
            IntPxSize(
                containerSize.width - childSize.width,
                containerSize.height - childSize.height
            )
        )
    }
}
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

package androidx.ui.core

import androidx.ui.unit.Density
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxPosition
import androidx.ui.unit.IntPxSize

/**
 * A [Modifier.Element] that changes the way a UI component is measured and laid out.
 */
interface LayoutModifier : Modifier.Element {
    /**
     * Modifies [constraints] for performing measurement of the modified layout element.
     */
    fun ModifierScope.modifyConstraints(constraints: Constraints): Constraints = constraints

    /**
     * Returns the container size of a modified layout element given the original container
     * measurement [constraints] and the measured [childSize].
     */
    fun ModifierScope.modifySize(constraints: Constraints, childSize: IntPxSize): IntPxSize =
        childSize

    /**
     * Determines the modified minimum intrinsic width of [measurable].
     * See [Measurable.minIntrinsicWidth].
     */
    fun ModifierScope.minIntrinsicWidthOf(measurable: Measurable, height: IntPx): IntPx {
        val constraints = Constraints(maxHeight = height)
        val layoutWidth = measurable.minIntrinsicWidth(modifyConstraints(constraints).maxHeight)
        return modifySize(constraints, IntPxSize(layoutWidth, height)).width
    }

    /**
     * Determines the modified maximum intrinsic width of [measurable].
     * See [Measurable.maxIntrinsicWidth].
     */
    fun ModifierScope.maxIntrinsicWidthOf(measurable: Measurable, height: IntPx): IntPx {
        val constraints = Constraints(maxHeight = height)
        val layoutWidth = measurable.maxIntrinsicWidth(modifyConstraints(constraints).maxHeight)
        return modifySize(constraints, IntPxSize(layoutWidth, height)).width
    }

    /**
     * Determines the modified minimum intrinsic height of [measurable].
     * See [Measurable.minIntrinsicHeight].
     */
    fun ModifierScope.minIntrinsicHeightOf(measurable: Measurable, width: IntPx): IntPx {
        val constraints = Constraints(maxWidth = width)
        val layoutHeight = measurable.minIntrinsicHeight(modifyConstraints(constraints).maxWidth)
        return modifySize(constraints, IntPxSize(width, layoutHeight)).height
    }

    /**
     * Determines the modified maximum intrinsic height of [measurable].
     * See [Measurable.maxIntrinsicHeight].
     */
    fun ModifierScope.maxIntrinsicHeightOf(measurable: Measurable, width: IntPx): IntPx {
        val constraints = Constraints(maxWidth = width)
        val layoutHeight = measurable.maxIntrinsicHeight(modifyConstraints(constraints).maxWidth)
        return modifySize(constraints, IntPxSize(width, layoutHeight)).height
    }

    /**
     * Returns the position of a modified child of size [childSize] within a container of
     * size [containerSize].
     */
    fun ModifierScope.modifyPosition(
        childSize: IntPxSize,
        containerSize: IntPxSize
    ): IntPxPosition = IntPxPosition.Origin

    /**
     * Returns the modified position of [line] given its unmodified [value].
     */
    fun ModifierScope.modifyAlignmentLine(
        line: AlignmentLine,
        value: IntPx?
    ): IntPx? = value

    /**
     * Modifies the layout direction to be used for measurement and layout by the modified layout
     * node.
     */
    fun ModifierScope.modifyLayoutDirection(): LayoutDirection = layoutDirection
}

/**
 * Receiver scope for a layout modifier.
 */
interface ModifierScope : Density {

    /**
     * Layout direction set by layout modifier to force LTR or RTL direction in layout.
     */
    val layoutDirection: LayoutDirection

    /**
     * Layout direction provided through ambient. Unless modified through ambient, it reflects
     * the locale's direction.
     */
    val ambientLayoutDirection: LayoutDirection
}
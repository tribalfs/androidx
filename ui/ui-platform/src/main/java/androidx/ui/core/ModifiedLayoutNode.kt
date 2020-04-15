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

package androidx.ui.core

import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.Paint
import androidx.ui.graphics.PaintingStyle
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxSize

internal class ModifiedLayoutNode2(
    wrapped: LayoutNodeWrapper,
    val layoutModifier: LayoutModifier2
) : DelegatingLayoutNodeWrapper<LayoutModifier2>(wrapped, layoutModifier) {

    override val measureScope = ModifierMeasureScope()

    override fun performMeasure(
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): Placeable = with(layoutModifier) {
        measureScope.layoutDirection = layoutDirection
        measureResult = measureScope.measure(wrapped, constraints, layoutDirection)
        this@ModifiedLayoutNode2
    }

    // TODO(popam): wire up layout direction to intrinsics as well
    override fun minIntrinsicWidth(height: IntPx): IntPx = with(layoutModifier) {
        measureScope.minIntrinsicWidth(wrapped, height, LayoutDirection.Ltr)
    }

    override fun maxIntrinsicWidth(height: IntPx): IntPx = with(layoutModifier) {
        measureScope.maxIntrinsicWidth(wrapped, height, LayoutDirection.Ltr)
    }

    override fun minIntrinsicHeight(width: IntPx): IntPx = with(layoutModifier) {
        measureScope.minIntrinsicHeight(wrapped, width, LayoutDirection.Ltr)
    }

    override fun maxIntrinsicHeight(width: IntPx): IntPx = with(layoutModifier) {
        measureScope.maxIntrinsicHeight(wrapped, width, LayoutDirection.Ltr)
    }

    override operator fun get(line: AlignmentLine): IntPx? =
        measureResult.alignmentLines.getOrElse(line, { wrapped[line] })

    override fun draw(canvas: Canvas) {
        withPositionTranslation(canvas) {
            wrapped.draw(canvas)
            if (layoutNode.requireOwner().showLayoutBounds) {
                drawBorder(canvas, modifierBoundsPaint)
            }
        }
    }

    internal companion object {
        val modifierBoundsPaint = Paint().also { paint ->
            paint.color = Color.Blue
            paint.strokeWidth = 1f
            paint.style = PaintingStyle.stroke
        }
    }

    inner class ModifierMeasureScope : MeasureScope() {
        override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
        override val density: Float
            get() = layoutNode.measureScope.density
        override val fontScale: Float
            get() = layoutNode.measureScope.fontScale
    }
}

@Suppress("Deprecation")
internal class ModifiedLayoutNode(
    wrapped: LayoutNodeWrapper,
    layoutModifier: LayoutModifier
) : DelegatingLayoutNodeWrapper<LayoutModifier>(wrapped, layoutModifier) {
    override val measureScope = ModifierMeasureScope()

    override fun performMeasure(
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): Placeable = with(modifier) {
        val modifiedLayoutDirection = measureScope.modifyLayoutDirection(layoutDirection)
        measureScope.layoutDirection = modifiedLayoutDirection

        val placeable = wrapped.measure(
            measureScope.modifyConstraints(constraints, layoutDirection),
            measureScope.layoutDirection
        )
        val size = measureScope.modifySize(
            constraints,
            layoutDirection,
            IntPxSize(placeable.width, placeable.height)
        )
        val wrappedPosition = with(modifier) {
            measureScope.modifyPosition(
                IntPxSize(placeable.width, placeable.height),
                size,
                layoutDirection
            )
        }
        measureResult = object : MeasureScope.MeasureResult {
            override val width: IntPx = size.width
            override val height: IntPx = size.height
            override val alignmentLines: Map<AlignmentLine, IntPx> = emptyMap()
            override fun placeChildren(layoutDirection: LayoutDirection) {
                with(InnerPlacementScope) {
                    placeable.placeAbsolute(wrappedPosition)
                }
            }
        }
        this@ModifiedLayoutNode
    }

    override fun minIntrinsicWidth(height: IntPx): IntPx = with(modifier) {
        measureScope.minIntrinsicWidthOf(wrapped, height, LayoutDirection.Ltr)
    }

    override fun maxIntrinsicWidth(height: IntPx): IntPx = with(modifier) {
        measureScope.maxIntrinsicWidthOf(wrapped, height, LayoutDirection.Ltr)
    }

    override fun minIntrinsicHeight(width: IntPx): IntPx = with(modifier) {
        measureScope.minIntrinsicHeightOf(wrapped, width, LayoutDirection.Ltr)
    }

    override fun maxIntrinsicHeight(width: IntPx): IntPx = with(modifier) {
        measureScope.maxIntrinsicHeightOf(wrapped, width, LayoutDirection.Ltr)
    }

    override operator fun get(line: AlignmentLine): IntPx? = with(modifier) {
        return layoutNode.measureScope.modifyAlignmentLine(
            line,
            super.get(line),
            measureScope.layoutDirection
        )
    }

    override fun draw(canvas: Canvas) {
        withPositionTranslation(canvas) {
            wrapped.draw(canvas)
            if (layoutNode.requireOwner().showLayoutBounds) {
                drawBorder(canvas, modifierBoundsPaint)
            }
        }
    }

    internal companion object {
        val modifierBoundsPaint = Paint().also { paint ->
            paint.color = Color.Blue
            paint.strokeWidth = 1f
            paint.style = PaintingStyle.stroke
        }
    }

    inner class ModifierMeasureScope : MeasureScope() {
        override var layoutDirection: LayoutDirection = LayoutDirection.Ltr
        override val density: Float
            get() = layoutNode.measureScope.density
        override val fontScale: Float
            get() = layoutNode.measureScope.fontScale
    }
}
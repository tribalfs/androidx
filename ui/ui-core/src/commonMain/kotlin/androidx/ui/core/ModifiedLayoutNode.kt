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

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle

@OptIn(ExperimentalLayoutNodeApi::class)
internal class ModifiedLayoutNode(
    wrapped: LayoutNodeWrapper,
    modifier: LayoutModifier
) : DelegatingLayoutNodeWrapper<LayoutModifier>(wrapped, modifier) {

    override val measureScope = ModifierMeasureScope()

    override fun performMeasure(
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): Placeable = with(modifier) {
        measureScope.layoutDirection = layoutDirection
        measureResult = measureScope.measure(wrapped, constraints, layoutDirection)
        this@ModifiedLayoutNode
    }

    override fun minIntrinsicWidth(height: Int, layoutDirection: LayoutDirection): Int =
        with(modifier) {
            measureScope.layoutDirection = layoutDirection
            measureScope.minIntrinsicWidth(wrapped, height, layoutDirection)
        }

    override fun maxIntrinsicWidth(height: Int, layoutDirection: LayoutDirection): Int =
        with(modifier) {
            measureScope.layoutDirection = layoutDirection
            measureScope.maxIntrinsicWidth(wrapped, height, layoutDirection)
        }

    override fun minIntrinsicHeight(width: Int, layoutDirection: LayoutDirection): Int =
        with(modifier) {
            measureScope.layoutDirection = layoutDirection
            measureScope.minIntrinsicHeight(wrapped, width, layoutDirection)
        }

    override fun maxIntrinsicHeight(width: Int, layoutDirection: LayoutDirection): Int =
        with(modifier) {
            measureScope.layoutDirection = layoutDirection
            measureScope.maxIntrinsicHeight(wrapped, width, layoutDirection)
        }

    override operator fun get(line: AlignmentLine): Int {
        if (measureResult.alignmentLines.containsKey(line)) {
            return measureResult.alignmentLines[line] ?: AlignmentLine.Unspecified
        }
        val positionInWrapped = wrapped[line]
        if (positionInWrapped == AlignmentLine.Unspecified) {
            return AlignmentLine.Unspecified
        }
        // Place our wrapped to obtain their position inside ourselves.
        isShallowPlacing = true
        place(this.position)
        isShallowPlacing = false
        return if (line is HorizontalAlignmentLine) {
            positionInWrapped + wrapped.position.y
        } else {
            positionInWrapped + wrapped.position.x
        }
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

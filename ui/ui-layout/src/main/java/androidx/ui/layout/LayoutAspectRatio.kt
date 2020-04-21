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

@file:Suppress("DEPRECATION")

package androidx.ui.layout

import androidx.annotation.FloatRange
import androidx.ui.core.Constraints
import androidx.ui.core.LayoutDirection
import androidx.ui.core.LayoutModifier
import androidx.ui.core.Measurable
import androidx.ui.core.Modifier
import androidx.ui.core.satisfiedBy
import androidx.ui.unit.Density
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.ipx
import androidx.ui.unit.isFinite

/**
 * Attempts to size the content to match a specified aspect ratio by trying to match one of the
 * incoming constraints in the following order:
 * [Constraints.maxWidth], [Constraints.maxHeight], [Constraints.minWidth], [Constraints.minHeight].
 * The size in the other dimension is determined by the aspect ratio.
 *
 * @param ratio the desired width/height ratio
 */
fun Modifier.aspectRatio(
    @FloatRange(from = 0.0, fromInclusive = false)
    ratio: Float
) = this + LayoutAspectRatio(ratio)

/**
 * A layout modifier that attempts to size a layout to match a specified aspect ratio. The layout
 * modifier will try to match one of the incoming constraints, in the following order: maxWidth,
 * maxHeight, minWidth, minHeight. The size in the other dimension will then be computed
 * according to the aspect ratio. Note that the provided aspectRatio will always correspond to
 * the width/height ratio.
 *
 * If a valid size that satisfies the constraints is found this way, the modifier will size the
 * target layout to it: the layout will be measured with the tight constraints to match the size.
 * If a child is present, it will be measured with tight constraints to match the size.
 * If no valid size is found, the aspect ratio will not be satisfied, and the target layout will
 * be measured with the original constraints.
 *
 * Example usage:
 * @sample androidx.ui.layout.samples.SimpleAspectRatio
 *
 * @param aspectRatio A positive non-zero value representing the aspect ratio.
 */
data class LayoutAspectRatio
@Deprecated(
    "Use Modifier.aspectRatio",
    replaceWith = ReplaceWith(
        "Modifier.aspectRatio(aspectRatio)",
        "androidx.ui.core.Modifier",
        "androidx.ui.layout.aspectRatio"
    )
)
constructor(
    @FloatRange(from = 0.0, fromInclusive = false)
    val aspectRatio: Float
) : LayoutModifier {
    init {
        require(aspectRatio > 0) { "aspectRatio $aspectRatio must be > 0" }
    }

    override fun Density.modifyConstraints(
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): Constraints {
        val size = constraints.findSizeWith(aspectRatio)
        return if (size != null)
            Constraints.fixed(size.width, size.height)
        else
            constraints
    }

    override fun Density.minIntrinsicWidthOf(
        measurable: Measurable,
        height: IntPx,
        layoutDirection: LayoutDirection
    ): IntPx {
        return if (height == IntPx.Infinity) measurable.minIntrinsicWidth(height, layoutDirection)
        else height * aspectRatio
    }

    override fun Density.maxIntrinsicWidthOf(
        measurable: Measurable,
        height: IntPx,
        layoutDirection: LayoutDirection
    ): IntPx {
        return if (height == IntPx.Infinity) measurable.maxIntrinsicWidth(height, layoutDirection)
        else height * aspectRatio
    }

    override fun Density.minIntrinsicHeightOf(
        measurable: Measurable,
        width: IntPx,
        layoutDirection: LayoutDirection
    ): IntPx {
        return if (width == IntPx.Infinity) measurable.minIntrinsicHeight(width, layoutDirection)
        else width / aspectRatio
    }

    override fun Density.maxIntrinsicHeightOf(
        measurable: Measurable,
        width: IntPx,
        layoutDirection: LayoutDirection
    ): IntPx {
        return if (width == IntPx.Infinity) measurable.maxIntrinsicHeight(width, layoutDirection)
        else width / aspectRatio
    }
}

private fun Constraints.findSizeWith(aspectRatio: Float): IntPxSize? {
    return listOf(
        IntPxSize(this.maxWidth, this.maxWidth / aspectRatio),
        IntPxSize(this.maxHeight * aspectRatio, this.maxHeight),
        IntPxSize(this.minWidth, this.minWidth / aspectRatio),
        IntPxSize(this.minHeight * aspectRatio, this.minHeight)
    ).find {
        this.satisfiedBy(it) &&
                it.width != 0.ipx && it.height != 0.ipx &&
                it.width.isFinite() && it.height.isFinite()
    }
}
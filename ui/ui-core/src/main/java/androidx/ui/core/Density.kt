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

import android.content.Context
import androidx.ui.engine.geometry.Rect
import androidx.ui.graphics.Canvas

/**
 * A density of the screen. Used for convert [Dp] to pixels.
 */
data class Density(val density: Float, val fontScale: Float = 1f)

/**
 * Creates a [Density] from this [Context]
 *
 */
// TODO(Andrey): Move to android specific module
fun Density(context: Context): Density =
    Density(
        context.resources.displayMetrics.density,
        context.resources.configuration.fontScale
    )

/**
 * If you have a [Density] object and you want to perform some conversions use this.
 *
 * @sample androidx.ui.core.samples.WithDensitySample
 */
inline fun <R> withDensity(density: Density, block: DensityScope.() -> R) =
    DensityScope(density).block()

/**
 * Used to add density resolution logic within a receiver scope.
 *
 * @see [withDensity] for a simple usage
 */
interface DensityScope {

    /**
     * A [Density] object. Useful if you need to pass it as a param.
     */
    val density: Density

    /**
     * Convert [Dp] to [Px]. Pixels are used to paint to [Canvas].
     */
    fun Dp.toPx(): Px = Px(value * density.density)

    /**
     * Convert [Dp] to [IntPx] by rounding
     */
    fun Dp.toIntPx(): IntPx = toPx().round()

    /**
     * Convert [Dp] to Sp. Sp is used for font size, etc.
     */
    fun Dp.toSp(): TextUnit = TextUnit.Sp(value / density.fontScale)

    /**
     * Convert Sp to [Px]. Pixels are used to paint to [Canvas].
     * @throws ArithmeticException if TextUnit other than SP unit is specified.
     */
    fun TextUnit.toPx(): Px {
        require(type == TextUnitType.Sp) { "Only Sp can convert to Px" }
        return Px(value * density.fontScale * density.density)
    }

    /**
     * Convert Sp to [IntPx] by rounding
     */
    fun TextUnit.toIntPx(): IntPx = toPx().round()

    /**
     * Convert Sp to [Dp].
     * @throws ArithmeticException if TextUnit other than SP unit is specified.
     */
    fun TextUnit.toDp(): Dp {
        require(type == TextUnitType.Sp) { "Only Sp can convert to Px" }
        return Dp(value * density.fontScale)
    }

    /**
     * Convert [Px] to [Dp].
     */
    fun Px.toDp(): Dp = (value / density.density).dp

    /**
     * Convert [Px] to Sp.
     */
    fun Px.toSp(): TextUnit = (value / (density.fontScale * density.density)).sp

    /**
     * Convert [IntPx] to [Dp].
     */
    fun IntPx.toDp(): Dp = (value / density.density).dp

    /**
     * Convert [IntPx] to Sp.
     */
    fun IntPx.toSp(): TextUnit = (value / (density.fontScale * density.density)).sp

    /** Convert a [Float] pixel value to a Dp */
    fun Float.toDp(): Dp = (this / density.density).dp

    /** Convert a [Float] pixel value to a Sp */
    fun Float.toSp(): TextUnit = (this / (density.fontScale * density.density)).sp

    /** Convert a [Int] pixel value to a Dp */
    fun Int.toDp(): Dp = toFloat().toDp()

    /** Convert a [Int] pixel value to a Sp */
    fun Int.toSp(): TextUnit = toFloat().toSp()

    /**
     * Convert a [Size] to a [PxSize].
     */
    fun Size.toPx(): PxSize =
        PxSize(width.toPx(), height.toPx())

    /**
     * Convert a [Bounds] to a [Rect].
     */
    fun Bounds.toRect(): Rect {
        return Rect(
            left.toPx().value,
            top.toPx().value,
            right.toPx().value,
            bottom.toPx().value
        )
    }
}

/**
 * Returns a [DensityScope] reflecting [density].
 */
fun DensityScope(density: Density): DensityScope = DensityScopeImpl(density)

/**
 * A simple implementation for [DensityScope].
 */
private class DensityScopeImpl(override val density: Density) : DensityScope

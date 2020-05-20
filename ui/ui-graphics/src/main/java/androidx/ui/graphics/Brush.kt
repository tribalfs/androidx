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

package androidx.ui.graphics

import androidx.ui.geometry.Offset

sealed class Brush {
    abstract fun applyTo(p: Paint)
}

data class SolidColor(val value: Color) : Brush() {
    override fun applyTo(p: Paint) {
        if (p.color != value) p.color = value
        if (p.shader != null) p.shader = null
    }
}

typealias ColorStop = Pair<Float, Color>

/**
 * Creates a linear gradient with the provided colors along the given start and end coordinates.
 * The colors are
 *
 * ```
 *  LinearGradient(
 *      listOf(Color.Red, Color.Green, Color.Blue),
 *      startX = 0.0f,
 *      startY = 50.0f,
 *      endY = 0.0f,
 *      endY = 100.0f
 * )
 * ```
 */
fun LinearGradient(
    colors: List<Color>,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    tileMode: TileMode = TileMode.Clamp
) = LinearGradient(
        colors,
        null,
        startX,
        startY,
        endX,
        endY,
        tileMode
    )

/**
 * Creates a linear gradient with the provided colors along the given start and end coordinates.
 * The colors are dispersed at the provided offset defined in the [ColorStop]
 *
 * ```
 *  LinearGradient(
 *      0.0f to Color.Red,
 *      0.3f to Color.Green,
 *      1.0f to Color.Blue,
 *      startX = 0.0f,
 *      startY = 50.0f,
 *      endY = 0.0f,
 *      endY = 100.0f
 * )
 * ```
 */
fun LinearGradient(
    vararg colorStops: ColorStop,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
    tileMode: TileMode = TileMode.Clamp
) = LinearGradient(
        List<Color>(colorStops.size) { i -> colorStops[i].second },
        List<Float>(colorStops.size) { i -> colorStops[i].first },
        startX,
        startY,
        endX,
        endY,
        tileMode
    )

/**
 * Creates a radial gradient with the given colors at the provided offset defined in the [ColorStop]
 * ```
 * RadialGradient(
 *      0.0f to Color.Red,
 *      0.3f to Color.Green,
 *      1.0f to Color.Blue,
 *      centerX = side1 / 2.0f,
 *      centerY = side2 / 2.0f,
 *      radius = side1 / 2.0f,
 *      tileMode = TileMode.Repeated
 * )
 * ```
 */
fun RadialGradient(
    vararg colorStops: ColorStop,
    centerX: Float,
    centerY: Float,
    radius: Float,
    tileMode: TileMode = TileMode.Clamp
) = RadialGradient(
        List<Color>(colorStops.size) { i -> colorStops[i].second },
        List<Float>(colorStops.size) { i -> colorStops[i].first },
        centerX,
        centerY,
        radius,
        tileMode
    )

/**
 * Creates a radial gradient with the given colors evenly dispersed within the gradient
 * ```
 * RadialGradient(
 *      listOf(Color.Red, Color.Green, Color.Blue),
 *      centerX = side1 / 2.0f,
 *      centerY = side2 / 2.0f,
 *      radius = side1 / 2.0f,
 *      tileMode = TileMode.Repeated
 * )
 * ```
 */
fun RadialGradient(
    colors: List<Color>,
    centerX: Float,
    centerY: Float,
    radius: Float,
    tileMode: TileMode = TileMode.Clamp
) = RadialGradient(colors, null, centerX, centerY, radius, tileMode)

/**
 * Creates a vertical gradient with the given colors evenly dispersed within the gradient
 * Ex:
 * ```
 *  VerticalGradient(
 *      listOf(Color.Red, Color.Green, Color.Blue),
 *      startY = 0.0f,
 *      endY = 100.0f
 * )
 *
 * ```
 */
fun VerticalGradient(
    colors: List<Color>,
    startY: Float,
    endY: Float,
    tileMode: TileMode = TileMode.Clamp
) = LinearGradient(
        colors,
        null,
        startX = 0.0f,
        startY = startY,
        endX = 0.0f,
        endY = endY,
        tileMode = tileMode
    )

/**
 * Creates a vertical gradient with the given colors at the provided offset defined in the [ColorStop]
 * Ex:
 * ```
 *  VerticalGradient(
 *      0.1f to Color.Red,
 *      0.3f to Color.Green,
 *      0.5f to Color.Blue,
 *      startY = 0.0f,
 *      endY = 100.0f
 * )
 * ```
 */
fun VerticalGradient(
    vararg colorStops: ColorStop,
    startY: Float,
    endY: Float,
    tileMode: TileMode = TileMode.Clamp
) = LinearGradient(
        List<Color>(colorStops.size) { i -> colorStops[i].second },
        List<Float>(colorStops.size) { i -> colorStops[i].first },
        startX = 0.0f,
        startY = startY,
        endX = 0.0f,
        endY = endY,
        tileMode = tileMode
    )

/**
 * Creates a horizontal gradient with the given colors evenly dispersed within the gradient
 *
 * Ex:
 * ```
 *  HorizontalGradient(
 *      listOf(Color.Red, Color.Green, Color.Blue),
 *      startX = 10.0f,
 *      endX = 20.0f
 * )
 * ```
 */
fun HorizontalGradient(
    colors: List<Color>,
    startX: Float,
    endX: Float,
    tileMode: TileMode = TileMode.Clamp
) = LinearGradient(
        colors,
        null,
        startX = startX,
        startY = 0.0f,
        endX = endX,
        endY = 0.0f,
        tileMode = tileMode
    )

/**
 * Creates a horizontal gradient with the given colors dispersed at the provided offset defined in the [ColorStop]
 *
 * Ex:
 * ```
 *  HorizontalGradient(
 *      0.0f to Color.Red,
 *      0.3f to Color.Green,
 *      1.0f to Color.Blue,
 *      startX = 0.0f,
 *      endX = 100.0f
 * )
 * ```
 */
fun HorizontalGradient(
    vararg colorStops: ColorStop,
    startX: Float,
    endX: Float,
    tileMode: TileMode = TileMode.Clamp
) = LinearGradient(
        List<Color>(colorStops.size) { i -> colorStops[i].second },
        List<Float>(colorStops.size) { i -> colorStops[i].first },
        startX = startX,
        startY = 0.0f,
        endX = endX,
        endY = 0.0f,
        tileMode = tileMode
    )

/**
 * Brush implementation used to apply a linear gradient on a given [Paint]
 */
data class LinearGradient internal constructor(
    private val colors: List<Color>,
    private val stops: List<Float>? = null,
    private val startX: Float,
    private val startY: Float,
    private val endX: Float,
    private val endY: Float,
    private val tileMode: TileMode = TileMode.Clamp
) : ShaderBrush(
    LinearGradientShader(
        Offset(startX, startY),
        Offset(endX, endY),
        colors,
        stops,
        tileMode
    )
)

/**
 * Brush implementation used to apply a radial gradient on a given [Paint]
 */
data class RadialGradient internal constructor(
    private val colors: List<Color>,
    private val stops: List<Float>? = null,
    private val centerX: Float,
    private val centerY: Float,
    private val radius: Float,
    private val tileMode: TileMode = TileMode.Clamp
) : ShaderBrush(
    RadialGradientShader(
        Offset(centerX, centerY),
        radius,
        colors,
        stops,
        tileMode
    )
)

/**
 * Brush implementation that wraps and applies a the provided shader to a [Paint]
 */
open class ShaderBrush(val shader: Shader) : Brush() {
    final override fun applyTo(p: Paint) {
        if (p.color != Color.Black) p.color = Color.Black
        if (p.shader != shader) p.shader = shader
    }
}

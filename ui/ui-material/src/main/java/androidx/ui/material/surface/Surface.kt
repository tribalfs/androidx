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

package androidx.ui.material.surface

import androidx.compose.Composable
import androidx.ui.core.Clip
import androidx.ui.core.CurrentTextStyleProvider
import androidx.ui.core.DrawShadow
import androidx.ui.core.Layout
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.foundation.Border
import androidx.ui.foundation.ProvideContentColor
import androidx.ui.foundation.shape.DrawShape
import androidx.ui.foundation.shape.RectangleShape
import androidx.ui.graphics.Brush
import androidx.ui.graphics.Color
import androidx.ui.graphics.Shape
import androidx.ui.graphics.compositeOver
import androidx.ui.material.ColorPalette
import androidx.ui.material.MaterialTheme
import androidx.ui.material.contentColorFor
import androidx.ui.text.TextStyle
import androidx.ui.unit.Dp
import androidx.ui.unit.dp
import androidx.ui.unit.ipx
import kotlin.math.ln

/**
 * The [Surface] is responsible for:
 *
 * 1) Clipping: Surface clips its children to the shape specified by [shape]
 *
 * 2) Elevation: Surface elevates its children on the Z axis by [elevation] pixels,
 *   and draws the appropriate shadow.
 *
 * 3) Borders: If [shape] has a border, then it will also be drawn.
 *
 * Material surface is the central metaphor in material design. Each surface
 * exists at a given elevation, which influences how that piece of surface
 * visually relates to other surfaces and how that surface casts shadows.
 *
 * [contentColor] is the preferred color for any children inside this surface - any [Text] inside
 * this Surface will use this color by default.
 *
 * If no [contentColor] is set, this surface will try and match its background color to a color
 * defined in the theme [ColorPalette], and return the corresponding `onFoo` color. For example,
 * if the [color] of this surface is [ColorPalette.surface], [contentColor] will be set to
 * [ColorPalette.onSurface]. If [color] is not part of the theme palette, [contentColor] will keep
 * the same value set above this Surface.
 *
 * To modify these default style values used by text, use [CurrentTextStyleProvider] or explicitly
 * pass a new [TextStyle] to your text.
 *
 * To manually retrieve the content color inside a surface, use [contentColor].
 *
 * @param modifier Modifier to be applied to the layout corresponding to the surface
 * @param shape Defines the surface's shape as well its shadow. A shadow is only
 *  displayed if the [elevation] is greater than zero.
 * @param color The background color. Use [Color.Transparent] to have no color.
 * @param contentColor The preferred content color provided by this Surface to its children.
 * Defaults to either the matching `onFoo` color for [color], or if [color] is not a color from
 * the theme, this will keep the same value set above this Surface.
 * @param borderWidth width of the border of the Surface (if it exists)
 * @param borderBrush brush to paint the border with. If null, there will be no border, no matter
 * the borderWidth
 * @param elevation The z-coordinate at which to place this surface. This controls
 * the size of the shadow below the surface.
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier.None,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colors().surface,
    contentColor: Color = contentColorFor(color),
    borderWidth: Dp = 0.dp,
    borderBrush: Brush? = null,
    elevation: Dp = 0.dp,
    children: @Composable() () -> Unit
) {
    val border =
        if (borderBrush != null) Border(shape, borderWidth, borderBrush) else Modifier.None
    SurfaceLayout(modifier + border) {
        if (elevation > 0.dp) {
            DrawShadow(shape = shape, elevation = elevation)
        }
        val backgroundColor = getBackgroundColorForElevation(color, elevation)
        DrawShape(shape = shape, color = backgroundColor)
        Clip(shape = shape) {
            ProvideContentColor(contentColor, children)
        }
    }
}

/**
 * A simple layout which just reserves a space for a [Surface].
 * It positions the only child in the left top corner.
 */
// TODO("Andrey: Should be replaced with some basic layout implementation when we have it")
@Composable
private fun SurfaceLayout(modifier: Modifier = Modifier.None, children: @Composable() () -> Unit) {
    Layout(children, modifier) { measurables, constraints ->
        if (measurables.size > 1) {
            throw IllegalStateException("Surface can have only one direct measurable child!")
        }
        val measurable = measurables.firstOrNull()
        if (measurable == null) {
            layout(constraints.minWidth, constraints.minHeight) {}
        } else {
            val placeable = measurable.measure(constraints)
            layout(placeable.width, placeable.height) {
                placeable.place(0.ipx, 0.ipx)
            }
        }
    }
}

/**
 * If in a light theme, returns [color]. If in dark theme, applies an elevation overlay if [color]
 * is equal to ColorPalette.surface, else returns [color]
 */
@Composable
private fun getBackgroundColorForElevation(color: Color, elevation: Dp): Color {
    val colors = MaterialTheme.colors()
    return if (elevation > 0.dp && color == colors.surface && !colors.isLight) {
        color.withElevation(elevation)
    } else {
        color
    }
}

/**
 * Applies a [Color.White] overlay to this color based on the [elevation]. This increases visibility
 * of elevation for surfaces in a dark theme.
 */
private fun Color.withElevation(elevation: Dp): Color {
    val foreground = calculateForeground(elevation)
    return foreground.compositeOver(this)
}

// TODO: b/145802792 - clarify this algorithm
/**
 * @return the alpha-modified [Color.White] to overlay on top of the surface color to produce
 * the resultant color.
 */
private fun calculateForeground(elevation: Dp): Color {
    val alpha = ((4.5f * ln(elevation.value + 1)) + 2f) / 100f
    return Color.White.copy(alpha = alpha)
}

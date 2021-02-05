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

package androidx.compose.material

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Material surface is the central metaphor in material design. Each surface exists at a given
 * elevation, which influences how that piece of surface visually relates to other surfaces and
 * how that surface casts shadows.
 *
 * The [Surface] is responsible for:
 *
 * 1) Clipping: Surface clips its children to the shape specified by [shape]
 *
 * 2) Elevation: Surface draws a shadow to represent depth, where [elevation] represents the
 * depth of this surface.
 *
 * 3) Borders: If [shape] has a border, then it will also be drawn.
 *
 * 4) Background: Surface fills the shape specified by [shape] with the [color]. If [color] is
 * [Colors.surface], the [ElevationOverlay] from [LocalElevationOverlay] will be used to apply
 * an overlay - by default this will only occur in dark theme. The color of the overlay depends
 * on the [elevation] of this Surface, and the [LocalAbsoluteElevation] set by any parent
 * surfaces. This ensures that a Surface never appears to have a lower elevation overlay than its
 * ancestors, by summing the elevation of all previous Surfaces.
 *
 * 5) Content color: Surface uses [contentColor] to specify a preferred color for the content of
 * this surface - this is used by the [Text] and [Icon] components as a default color.
 *
 * If no [contentColor] is set, this surface will try and match its background color to a color
 * defined in the theme [Colors], and return the corresponding content color. For example,
 * if the [color] of this surface is [Colors.surface], [contentColor] will be set to
 * [Colors.onSurface]. If [color] is not part of the theme palette, [contentColor] will keep
 * the same value set above this Surface.
 *
 * @sample androidx.compose.material.samples.SurfaceSample
 *
 * To modify these default style values used by text, use [ProvideTextStyle] or explicitly
 * pass a new [TextStyle] to your text.
 *
 * To manually retrieve the content color inside a surface, use [LocalContentColor].
 *
 * @param modifier Modifier to be applied to the layout corresponding to the surface
 * @param shape Defines the surface's shape as well its shadow. A shadow is only
 *  displayed if the [elevation] is greater than zero.
 * @param color The background color. Use [Color.Transparent] to have no color.
 * @param contentColor The preferred content color provided by this Surface to its children.
 * Defaults to either the matching content color for [color], or if [color] is not a color from
 * the theme, this will keep the same value set above this Surface.
 * @param border Optional border to draw on top of the surface
 * @param elevation The size of the shadow below the surface. Note that It will not affect z index
 * of the Surface. If you want to change the drawing order you can use `Modifier.zIndex`.
 */
@Composable
fun Surface(
    modifier: Modifier = Modifier,
    shape: Shape = RectangleShape,
    color: Color = MaterialTheme.colors.surface,
    contentColor: Color = contentColorFor(color),
    border: BorderStroke? = null,
    elevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    val elevationPx = with(LocalDensity.current) { elevation.toPx() }
    val elevationOverlay = LocalElevationOverlay.current
    val absoluteElevation = LocalAbsoluteElevation.current + elevation
    val backgroundColor = if (color == MaterialTheme.colors.surface && elevationOverlay != null) {
        elevationOverlay.apply(color, absoluteElevation)
    } else {
        color
    }
    CompositionLocalProvider(
        LocalContentColor provides contentColor,
        LocalAbsoluteElevation provides absoluteElevation
    ) {
        Box(
            modifier.graphicsLayer(shadowElevation = elevationPx, shape = shape)
                .then(if (border != null) Modifier.border(border, shape) else Modifier)
                .background(
                    color = backgroundColor,
                    shape = shape
                )
                .clip(shape),
            propagateMinConstraints = true
        ) {
            content()
        }
    }
}

/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.ui.text

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawStyle
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em

/**
 * A bullet annotation applied to the AnnotatedString that draws a bullet.
 *
 * @param shape a shape of the bullet to draw
 * @param size a size of the bullet
 * @param gapWidth a gap between the end of the bullet and the start of the paragraph
 * @param brush a brush to draw a bullet with
 * @param alpha an alpha to apply when drawing a bullet
 * @param drawStyle defines the draw style of the bullet, e.g. a fill or an outline
 */
internal class Bullet(
    val shape: Shape,
    val size: TextUnit, // Make TextUnitSize or something similar when making public
    val gapWidth: TextUnit,
    val brush: Brush?,
    val alpha: Float,
    val drawStyle: DrawStyle
) : AnnotatedString.Annotation {
    constructor(
        shape: Shape,
        size: TextUnit, // Make TextUnitSize or something similar when making public
        gapWidth: TextUnit,
        color: Color = Color.Unspecified,
        alpha: Float = Float.NaN,
        drawStyle: DrawStyle = Fill
    ) : this(shape, size, gapWidth, SolidColor(color), alpha, drawStyle)
}

internal val DefaultBulletIndentation = 1.em
private val DefaultBulletSize = 0.25.em
private val DefaultBulletGapSize = 0.25.em
internal val DefaultBullet =
    Bullet(CircleShape, DefaultBulletSize, DefaultBulletGapSize, null, 1f, Fill)

private object CircleShape : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val cornerRadius = CornerRadius(size.minDimension / 2f)
        return Outline.Rounded(
            RoundRect(
                rect = size.toRect(),
                topLeft = cornerRadius,
                topRight = cornerRadius,
                bottomRight = cornerRadius,
                bottomLeft = cornerRadius
            )
        )
    }
}

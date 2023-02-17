/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.tv.material3

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * GlowIndication is an [Indication] that displays a diffused shadow behind the component it is
 * applied to. It takes in parameters like [Color], [Shape], blur radius, and Offset to let users
 * customise it to their brand personality.
 */
@ExperimentalTvMaterial3Api
@Stable
class GlowIndication internal constructor(
    private val color: Color,
    private val shape: Shape,
    private val glowBlurRadius: Dp,
    private val offsetX: Dp,
    private val offsetY: Dp
) : Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        val animatedGlowBlurRadius by animateDpAsState(targetValue = glowBlurRadius)
        return GlowIndicationInstance(
            color = color,
            shape = shape,
            glowBlurRadius = animatedGlowBlurRadius,
            offsetX = offsetX,
            offsetY = offsetY,
            density = LocalDensity.current
        )
    }
}

@ExperimentalTvMaterial3Api
private class GlowIndicationInstance(
    color: Color,
    private val shape: Shape,
    private val density: Density,
    private val glowBlurRadius: Dp,
    private val offsetX: Dp,
    private val offsetY: Dp
) : IndicationInstance {
    val shadowColor = color.toArgb()
    val transparentColor = color.copy(alpha = 0f).toArgb()

    val paint = Paint()
    val frameworkPaint = paint.asFrameworkPaint()

    init {
        frameworkPaint.color = transparentColor

        with(density) {
            frameworkPaint.setShadowLayer(
                glowBlurRadius.toPx(),
                offsetX.toPx(),
                offsetY.toPx(),
                shadowColor
            )
        }
    }

    override fun ContentDrawScope.drawIndication() {
        drawIntoCanvas { canvas ->
            when (
                val shapeOutline = shape.createOutline(
                    size = size,
                    layoutDirection = layoutDirection,
                    density = this@GlowIndicationInstance.density
                )
            ) {
                is Outline.Rectangle -> canvas.drawRect(shapeOutline.rect, paint)

                is Outline.Rounded -> {
                    val shapeCornerRadiusX = shapeOutline.roundRect.topLeftCornerRadius.x
                    val shapeCornerRadiusY = shapeOutline.roundRect.topLeftCornerRadius.y

                    canvas.drawRoundRect(
                        0f,
                        0f,
                        size.width,
                        size.height,
                        shapeCornerRadiusX,
                        shapeCornerRadiusY,
                        paint
                    )
                }

                is Outline.Generic -> canvas.drawPath(shapeOutline.path, paint)
            }
        }
        drawContent()
    }
}

/**
 * Creates and remembers an instance of [GlowIndication].
 * @param color describes the color of the background glow.
 * @param shape describes the shape on which the glow will be clipped.
 * @param glowBlurRadius describes how long and blurred would the glow shadow be.
 * @param offsetX describes the horizontal offset of the glow from the composable.
 * @param offsetY describes the vertical offset of the glow from the composable.
 * @return A remembered instance of [GlowIndication].
 */
@ExperimentalTvMaterial3Api
@Composable
fun rememberGlowIndication(
    color: Color = MaterialTheme.colorScheme.primaryContainer,
    shape: Shape = RectangleShape,
    glowBlurRadius: Dp = 0.dp,
    offsetX: Dp = 0.dp,
    offsetY: Dp = 0.dp
) = remember(color, shape, glowBlurRadius, offsetX, offsetY) {
    GlowIndication(
        color = color,
        shape = shape,
        glowBlurRadius = glowBlurRadius,
        offsetX = offsetY,
        offsetY = offsetX
    )
}

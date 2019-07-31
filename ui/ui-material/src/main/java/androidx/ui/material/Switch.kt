/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.ui.material

import androidx.animation.TweenBuilder
import androidx.compose.Composable
import androidx.compose.composer
import androidx.compose.unaryPlus
import androidx.ui.core.DensityReceiver
import androidx.ui.core.Draw
import androidx.ui.core.PxSize
import androidx.ui.core.dp
import androidx.ui.core.px
import androidx.ui.core.withDensity
import androidx.ui.engine.geometry.Offset
import androidx.ui.foundation.gestures.DragDirection
import androidx.ui.foundation.gestures.Draggable
import androidx.ui.foundation.selection.Toggleable
import androidx.ui.foundation.selection.ToggleableState
import androidx.ui.graphics.Color
import androidx.ui.layout.Container
import androidx.ui.layout.Padding
import androidx.ui.layout.Wrap
import androidx.ui.material.internal.anchoredControllerByState
import androidx.ui.material.ripple.Ripple
import androidx.ui.painting.Canvas
import androidx.ui.painting.Paint
import androidx.ui.painting.StrokeCap

/**
 * A Switch is a two state toggleable component that provides on/off like options
 *
 * @param checked whether or not this components is checked
 * @param onCheckedChange callback to be invoked when Switch is being clicked,
 * therefore the change of checked state is requested.
 * if [null], Switch appears in [checked] state and remains disabled
 * @param color optional active color for Switch,
 * by default [MaterialColors.secondaryVariant] will be used
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    color: Color = +themeColor { secondaryVariant }
) {
    val value = if (checked) ToggleableState.Checked else ToggleableState.Unchecked
    Wrap {
        Ripple(bounded = false) {
            Toggleable(value = value, onToggle = onCheckedChange?.let { { it(!checked) } }) {
                Padding(padding = DefaultSwitchPadding) {
                    SwitchImpl(checked, onCheckedChange, color)
                }
            }
        }
    }
}

@Composable
private fun SwitchImpl(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, color: Color) {
    val minBound = 0f
    val maxBound = +withDensity { ThumbPathLength.toPx().value }
    val (controller, callback) =
        +anchoredControllerByState(
            state = checked,
            onStateChange = onCheckedChange ?: {},
            anchorsToState = listOf(minBound to false, maxBound to true),
            animationBuilder = AnimationBuilder
        )
    Draggable(
        dragDirection = DragDirection.Horizontal,
        minValue = minBound,
        maxValue = maxBound,
        valueController = controller,
        callback = callback
    ) { thumbPosition ->
        Container(width = SwitchWidth, height = SwitchHeight, expanded = true) {
            DrawSwitch(
                checked = checked,
                checkedThumbColor = color,
                thumbPosition = thumbPosition
            )
        }
    }
}

@Composable
private fun DrawSwitch(checked: Boolean, checkedThumbColor: Color, thumbPosition: Float) {
    val thumbColor = if (checked) checkedThumbColor else +themeColor { surface }
    val trackColor = if (checked) {
        checkedThumbColor.copy(alpha = CheckedTrackOpacity)
    } else {
        (+themeColor { onSurface }).copy(alpha = UncheckedTrackOpacity)
    }
    Draw { canvas, parentSize ->
        drawTrack(canvas, parentSize, trackColor)
        drawThumb(canvas, parentSize, thumbPosition, thumbColor)
    }
}

private fun DensityReceiver.drawTrack(
    canvas: Canvas,
    parentSize: PxSize,
    trackColor: Color
) {
    val paint = Paint().apply {
        isAntiAlias = true
        color = trackColor
        strokeCap = StrokeCap.round
        strokeWidth = TrackStrokeWidth.toPx().value
    }

    val strokeRadius = TrackStrokeWidth / 2
    val centerHeight = parentSize.height / 2

    canvas.drawLine(
        Offset(strokeRadius.toPx().value, centerHeight.value),
        Offset((TrackWidth - strokeRadius).toPx().value, centerHeight.value),
        paint
    )
}

private fun DensityReceiver.drawThumb(
    canvas: Canvas,
    parentSize: PxSize,
    position: Float,
    thumbColor: Color
) {
    val paint = Paint().apply {
        isAntiAlias = true
        color = thumbColor
    }
    val centerHeight = parentSize.height / 2
    val thumbRadius = (ThumbDiameter / 2).toPx().value
    val x = position.px.value + thumbRadius

    canvas.drawCircle(Offset(x, centerHeight.value), thumbRadius, paint)
}

private val CheckedTrackOpacity = 0.54f
private val UncheckedTrackOpacity = 0.38f

private val TrackWidth = 34.dp
private val TrackStrokeWidth = 14.dp

private val ThumbDiameter = 20.dp

// TODO(malkov): clarify this padding for Switch
private val DefaultSwitchPadding = 2.dp
private val SwitchWidth = TrackWidth
private val SwitchHeight = ThumbDiameter
private val ThumbPathLength = TrackWidth - ThumbDiameter

private val AnimationBuilder = TweenBuilder<Float>().apply { duration = 100 }
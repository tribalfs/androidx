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
import androidx.compose.State
import androidx.compose.state
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Modifier
import androidx.ui.core.semantics.semantics
import androidx.ui.foundation.Box
import androidx.ui.foundation.Canvas
import androidx.ui.foundation.gestures.DragDirection
import androidx.ui.foundation.selection.toggleable
import androidx.ui.geometry.Offset
import androidx.ui.graphics.Color
import androidx.ui.graphics.StrokeCap
import androidx.ui.graphics.drawscope.DrawScope
import androidx.ui.graphics.drawscope.Stroke
import androidx.ui.layout.padding
import androidx.ui.layout.preferredSize
import androidx.ui.material.internal.stateDraggable
import androidx.ui.material.ripple.RippleIndication
import androidx.ui.unit.dp

/**
 * A Switch is a two state toggleable component that provides on/off like options
 *
 * @sample androidx.ui.material.samples.SwitchSample
 *
 * @param checked whether or not this components is checked
 * @param onCheckedChange callback to be invoked when Switch is being clicked,
 * therefore the change of checked state is requested.
 * @param modifier Modifier to be applied to the switch layout
 * @param enabled whether or not components is enabled and can be clicked to request state change
 * @param color active color for Switch
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colors.secondaryVariant
) {
    val minBound = 0f
    val maxBound = with(DensityAmbient.current) { ThumbPathLength.toPx() }
    val thumbPosition = state { if (checked) maxBound else minBound }
    Box(
        modifier
            .semantics(mergeAllDescendants = true)
            .toggleable(
                value = checked,
                onValueChange = onCheckedChange,
                enabled = enabled,
                indication = RippleIndication(bounded = false)
            )
            .stateDraggable(
                state = checked,
                onStateChange = onCheckedChange,
                anchorsToState = listOf(minBound to false, maxBound to true),
                animationBuilder = AnimationBuilder,
                dragDirection = DragDirection.Horizontal,
                minValue = minBound,
                maxValue = maxBound,
                onNewValue = { thumbPosition.value = it }
            )
            .padding(DefaultSwitchPadding)
    ) {
        DrawSwitch(
            checked = checked,
            checkedThumbColor = color,
            thumbValue = thumbPosition
        )
    }
}

@Composable
private fun DrawSwitch(
    checked: Boolean,
    checkedThumbColor: Color,
    thumbValue: State<Float>
) {
    val thumbColor = if (checked) checkedThumbColor else MaterialTheme.colors.surface
    val trackColor = if (checked) {
        checkedThumbColor.copy(alpha = CheckedTrackOpacity)
    } else {
        MaterialTheme.colors.onSurface.copy(alpha = UncheckedTrackOpacity)
    }

    val trackStroke: Stroke
    with(DensityAmbient.current) {
        trackStroke = Stroke(width = TrackStrokeWidth.toPx(), cap = StrokeCap.round)
    }
    Canvas(Modifier.preferredSize(SwitchWidth, SwitchHeight)) {
        drawTrack(trackColor, TrackWidth.toPx(), trackStroke)
        drawThumb(thumbValue.value, ThumbDiameter.toPx(), thumbColor)
    }
}

private fun DrawScope.drawTrack(trackColor: Color, trackWidth: Float, stroke: Stroke) {
    val strokeRadius = stroke.width / 2
    drawLine(
        trackColor,
        Offset(strokeRadius, center.dy),
        Offset(trackWidth - strokeRadius, center.dy),
        stroke
    )
}

private fun DrawScope.drawThumb(position: Float, thumbDiameter: Float, thumbColor: Color) {
    val thumbRadius = thumbDiameter / 2
    val x = position + thumbRadius
    drawCircle(thumbColor, thumbRadius, Offset(x, center.dy))
}

private const val CheckedTrackOpacity = 0.54f
private const val UncheckedTrackOpacity = 0.38f

private val TrackWidth = 34.dp
private val TrackStrokeWidth = 14.dp

private val ThumbDiameter = 20.dp

// TODO(malkov): clarify this padding for Switch
private val DefaultSwitchPadding = 2.dp
private val SwitchWidth = TrackWidth
private val SwitchHeight = ThumbDiameter
private val ThumbPathLength = TrackWidth - ThumbDiameter

private val AnimationBuilder = TweenBuilder<Float>().apply { duration = 100 }

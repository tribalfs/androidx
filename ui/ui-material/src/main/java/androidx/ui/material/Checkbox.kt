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

import androidx.animation.FloatPropKey
import androidx.animation.TransitionSpec
import androidx.animation.transitionDefinition
import androidx.compose.Composable
import androidx.compose.remember
import androidx.ui.animation.ColorPropKey
import androidx.ui.animation.Transition
import androidx.ui.core.Modifier
import androidx.ui.foundation.Box
import androidx.ui.foundation.Canvas
import androidx.ui.foundation.CanvasScope
import androidx.ui.foundation.selection.ToggleableState
import androidx.ui.foundation.selection.TriStateToggleable
import androidx.ui.geometry.Offset
import androidx.ui.geometry.RRect
import androidx.ui.geometry.Radius
import androidx.ui.geometry.outerRect
import androidx.ui.geometry.shrink
import androidx.ui.graphics.ClipOp
import androidx.ui.graphics.Color
import androidx.ui.graphics.Paint
import androidx.ui.graphics.PaintingStyle
import androidx.ui.graphics.StrokeCap
import androidx.ui.layout.Container
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutSize
import androidx.ui.material.ripple.Ripple
import androidx.ui.semantics.Semantics
import androidx.ui.unit.dp

/**
 * A component that represents two states (checked / unchecked).
 *
 * @sample androidx.ui.material.samples.CheckboxSample
 *
 * @see [TriStateCheckbox] if you require support for an indeterminate state.
 *
 * @param checked whether Checkbox is checked or unchecked
 * @param onCheckedChange callback to be invoked when checkbox is being clicked,
 * therefore the change of checked state in requested.
 * If `null`, Checkbox will appears in the [checked] state and remains disabled
 * @param modifier Modifier to be applied to the layout of the checkbox
 * @param color custom color for checkbox
 */
@Composable
fun Checkbox(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier.None,
    color: Color = MaterialTheme.colors().secondary
) {
    TriStateCheckbox(
        value = ToggleableState(checked),
        onClick = onCheckedChange?.let { { it(!checked) } },
        color = color,
        modifier = modifier
    )
}

/**
 * A TriStateCheckbox is a toggleable component that provides
 * checked / unchecked / indeterminate options.
 * <p>
 * A TriStateCheckbox should be used when there are
 * dependent checkboxes associated to this component and those can have different values.
 *
 * @sample androidx.ui.material.samples.TriStateCheckboxSample
 *
 * @see [Checkbox] if you want a simple component that represents Boolean state
 *
 * @param value whether TriStateCheckbox is checked, unchecked or in indeterminate state
 * @param onClick callback to be invoked when checkbox is being clicked,
 * therefore the change of ToggleableState state is requested.
 * If `null`, TriStateCheckbox appears in the [value] state and remains disabled
 * @param modifier Modifier to be applied to the layout of the checkbox
 * @param color custom color for checkbox
 */
@Composable
fun TriStateCheckbox(
    value: ToggleableState,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier.None,
    color: Color = MaterialTheme.colors().secondary
) {
    Semantics(container = true, mergeAllDescendants = true) {
        Container(modifier) {
            Ripple(bounded = false) {
                TriStateToggleable(value = value, onToggle = onClick) {
                    Box(LayoutPadding(CheckboxDefaultPadding)) {
                        DrawCheckbox(value = value, activeColor = color)
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawCheckbox(value: ToggleableState, activeColor: Color) {
    val unselectedColor = MaterialTheme.colors().onSurface.copy(alpha = UncheckedBoxOpacity)
    val definition = remember(activeColor, unselectedColor) {
        generateTransitionDefinition(activeColor, unselectedColor)
    }
    val checkboxPaint = remember { Paint() }
    Transition(definition = definition, toState = value) { state ->
        Canvas(modifier = LayoutSize(CheckboxSize)) {
            drawBox(
                color = state[BoxColorProp],
                innerRadiusFraction = state[InnerRadiusFractionProp],
                paint = checkboxPaint
            )
            drawCheck(
                checkFraction = state[CheckFractionProp],
                crossCenterGravitation = state[CenterGravitationForCheck],
                paint = checkboxPaint
            )
        }
    }
}

private fun CanvasScope.drawBox(
    color: Color,
    innerRadiusFraction: Float,
    paint: Paint
) {
    val strokeWidth = StrokeWidth.toPx().value
    val halfStrokeWidth = strokeWidth / 2.0f
    paint.style = PaintingStyle.stroke
    paint.strokeWidth = strokeWidth
    paint.isAntiAlias = true
    paint.color = color

    val checkboxSize = size.width.value

    val outer = RRect(
        halfStrokeWidth,
        halfStrokeWidth,
        checkboxSize - halfStrokeWidth,
        checkboxSize - halfStrokeWidth,
        Radius.circular(RadiusSize.toPx().value)
    )

    // Determine whether or not we need to offset the inset by a pixel
    // to ensure that there is no gap between the outer stroked round rect
    // and the inner rect.
    val offset = (halfStrokeWidth - halfStrokeWidth.toInt()) + 0.5f

    // TODO(malkov): this radius formula is not in material spec

    val outerRadius = RadiusSize.toPx().value

    // If the inner region is to be filled such that it is larger than the outer stroke size
    // then create a difference clip to draw the stroke outside of the rectangular region
    // to be drawn within the interior rectangle. This is done to ensure that pixels do
    // not overlap which might cause unexpected blending if the target color has some
    // opacity. If the inner region is not to be drawn or will occupy a smaller width than
    // the outer stroke then just draw the outer stroke
    val innerStrokeWidth = innerRadiusFraction * checkboxSize / 2
    if (innerStrokeWidth > strokeWidth) {
        val clipRect = outer.shrink(strokeWidth / 2 - offset).outerRect()
        save()
        clipRect(clipRect, ClipOp.difference)
        drawRoundRect(outer.left, outer.top, outer.right, outer.bottom, outerRadius,
            outerRadius, paint)
        restore()

        save()
        clipRect(clipRect)
        paint.strokeWidth = innerStrokeWidth
        val innerHalfStrokeWidth = paint.strokeWidth / 2
        drawRect(outer.shrink(innerHalfStrokeWidth - offset).outerRect(), paint)
        restore()
    } else {
        drawRoundRect(outer.left, outer.top, outer.right, outer.bottom, outerRadius,
            outerRadius, paint)
    }
}

private fun CanvasScope.drawCheck(
    checkFraction: Float,
    crossCenterGravitation: Float,
    paint: Paint
) {
    paint.isAntiAlias = true
    paint.style = PaintingStyle.stroke
    paint.strokeCap = StrokeCap.square
    paint.strokeWidth = StrokeWidth.toPx().value
    paint.color = CheckStrokeDefaultColor

    val width = size.width.value

    val checkCrossX = 0.4f
    val checkCrossY = 0.7f
    val leftX = 0.2f
    val leftY = 0.5f
    val rightX = 0.8f
    val rightY = 0.3f

    val gravitatedCrossX = calcMiddleValue(checkCrossX, 0.5f, crossCenterGravitation)
    val gravitatedCrossY = calcMiddleValue(checkCrossY, 0.5f, crossCenterGravitation)

    // gravitate only Y for end to achieve center line
    val gravitatedLeftY = calcMiddleValue(leftY, 0.5f, crossCenterGravitation)
    val gravitatedRightY = calcMiddleValue(rightY, 0.5f, crossCenterGravitation)

    val crossPoint = Offset(width * gravitatedCrossX, width * gravitatedCrossY)
    val rightBranch = Offset(
        width * calcMiddleValue(gravitatedCrossX, rightX, checkFraction),
        width * calcMiddleValue(gravitatedCrossY, gravitatedRightY, checkFraction)
    )
    val leftBranch = Offset(
        width * calcMiddleValue(gravitatedCrossX, leftX, checkFraction),
        width * calcMiddleValue(gravitatedCrossY, gravitatedLeftY, checkFraction)
    )
    drawLine(crossPoint, leftBranch, paint)
    drawLine(crossPoint, rightBranch, paint)
}

private fun calcMiddleValue(start: Float, finish: Float, fraction: Float): Float {
    return start * (1 - fraction) + finish * fraction
}

// all float props are fraction now [0f .. 1f] as it seems convenient
private val InnerRadiusFractionProp = FloatPropKey()
private val CheckFractionProp = FloatPropKey()
private val CenterGravitationForCheck = FloatPropKey()
private val BoxColorProp = ColorPropKey()

private val BoxAnimationDuration = 100
private val CheckStrokeAnimationDuration = 100

private fun generateTransitionDefinition(color: Color, unselectedColor: Color) =
    transitionDefinition {
        state(ToggleableState.On) {
            this[CheckFractionProp] = 1f
            this[InnerRadiusFractionProp] = 1f
            this[CenterGravitationForCheck] = 0f
            this[BoxColorProp] = color
        }
        state(ToggleableState.Off) {
            this[CheckFractionProp] = 0f
            this[InnerRadiusFractionProp] = 0f
            this[CenterGravitationForCheck] = 1f
            this[BoxColorProp] = unselectedColor
        }
        state(ToggleableState.Indeterminate) {
            this[CheckFractionProp] = 1f
            this[InnerRadiusFractionProp] = 1f
            this[CenterGravitationForCheck] = 1f
            this[BoxColorProp] = color
        }
        transition(fromState = ToggleableState.Off, toState = ToggleableState.On) {
            boxTransitionFromUnchecked()
            CenterGravitationForCheck using snap()
        }
        transition(fromState = ToggleableState.On, toState = ToggleableState.Off) {
            boxTransitionToUnchecked()
            CenterGravitationForCheck using tween {
                duration = CheckStrokeAnimationDuration
            }
        }
        transition(
            ToggleableState.On to ToggleableState.Indeterminate,
            ToggleableState.Indeterminate to ToggleableState.On
        ) {
            CenterGravitationForCheck using tween {
                duration = CheckStrokeAnimationDuration
            }
        }
        transition(fromState = ToggleableState.Indeterminate, toState = ToggleableState.Off) {
            boxTransitionToUnchecked()
        }
        transition(fromState = ToggleableState.Off, toState = ToggleableState.Indeterminate) {
            boxTransitionFromUnchecked()
        }
    }

private fun TransitionSpec<ToggleableState>.boxTransitionFromUnchecked() {
    BoxColorProp using snap()
    InnerRadiusFractionProp using tween {
        duration = BoxAnimationDuration
    }
    CheckFractionProp using tween {
        duration = CheckStrokeAnimationDuration
        delay = BoxAnimationDuration
    }
}

private fun TransitionSpec<ToggleableState>.boxTransitionToUnchecked() {
    BoxColorProp using snap()
    InnerRadiusFractionProp using tween {
        duration = BoxAnimationDuration
        delay = CheckStrokeAnimationDuration
    }
    CheckFractionProp using tween {
        duration = CheckStrokeAnimationDuration
    }
}

private val CheckboxDefaultPadding = 2.dp
private val CheckboxSize = 20.dp
private val StrokeWidth = 2.dp
private val RadiusSize = 2.dp

private val UncheckedBoxOpacity = 0.6f
private val CheckStrokeDefaultColor = Color.White
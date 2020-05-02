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

import androidx.animation.transitionDefinition
import androidx.compose.Composable
import androidx.compose.Stable
import androidx.compose.remember
import androidx.ui.animation.ColorPropKey
import androidx.ui.animation.DpPropKey
import androidx.ui.animation.Transition
import androidx.ui.core.Modifier
import androidx.ui.core.DrawScope
import androidx.ui.foundation.Box
import androidx.ui.foundation.Canvas
import androidx.ui.foundation.Text
import androidx.ui.foundation.selection.MutuallyExclusiveSetItem
import androidx.ui.geometry.Offset
import androidx.ui.graphics.Color
import androidx.ui.graphics.Paint
import androidx.ui.graphics.PaintingStyle
import androidx.ui.layout.Column
import androidx.ui.layout.Row
import androidx.ui.layout.Stack
import androidx.ui.layout.fillMaxWidth
import androidx.ui.layout.padding
import androidx.ui.layout.preferredSize
import androidx.ui.material.ripple.ripple
import androidx.ui.semantics.Semantics
import androidx.ui.text.TextStyle
import androidx.ui.unit.Dp
import androidx.ui.unit.dp

/**
 * Components for creating mutually exclusive set of [RadioButton]s.
 * Because of the nature of mutually exclusive set, when radio button is selected,
 * it can't be unselected by being pressed again.
 *
 * Typical content for RadioGroup will be [RadioGroupScope.RadioGroupItem] and following usage:
 *
 * @sample androidx.ui.material.samples.CustomRadioGroupSample
 *
 * If you want a simplified version with [Column] of [RadioGroupScope.RadioGroupTextItem],
 * consider using version that accepts list of [String] options and doesn't require any children
 */
@Composable
fun RadioGroup(content: @Composable RadioGroupScope.() -> Unit) {
    val scope = remember { RadioGroupScope() }
    scope.content()
}

/**
 * Components for creating mutually exclusive set of [RadioButton]
 * as well as text label for this RadioButtons.
 * Because of the nature of mutually exclusive set, when radio button is selected,
 * it can't be unselected by being pressed again. This component guarantees
 * that there will be only one or none selected RadioButton at a time.
 *
 * This component is ready to use without children being passed and
 * it places the options into a [Column] of [RadioGroupScope.RadioGroupTextItem].
 *
 * @sample androidx.ui.material.samples.DefaultRadioGroupSample
 *
 * @param options list of [String] to provide RadioButtons label
 * @param selectedOption label which represents selected RadioButton,
 * or `null` if nothing is selected
 * @param onSelectedChange callback to be invoked when RadioButton is clicked,
 * therefore the selection of this item is requested
 * @param radioColor color for RadioButtons when selected.
 * @param textStyle parameters for text customization
 */
@Composable
fun RadioGroup(
    options: List<String>,
    selectedOption: String?,
    onSelectedChange: (String) -> Unit,
    radioColor: Color = MaterialTheme.colors.secondary,
    textStyle: TextStyle? = null
) {
    RadioGroup {
        Column {
            options.forEach { text ->
                RadioGroupTextItem(
                    selected = (text == selectedOption),
                    onSelect = { onSelectedChange(text) },
                    text = text,
                    radioColor = radioColor,
                    textStyle = textStyle
                )
            }
        }
    }
}

/**
 * Scope of [RadioGroup] to grant access to [RadioGroupItem] and others. This scope will be
 * provided automatically to the children of [RadioGroup].
 */
@Stable
class RadioGroupScope internal constructor() {

    /**
     * Basic item to be used inside [RadioGroup].
     *
     * This component provides basic radio item behavior such as
     * clicks and accessibility support.
     *
     * If you need simple item with [RadioButton] and [Text] wrapped in a [Row],
     * consider using [RadioGroupTextItem].
     *
     * @param selected whether or not this item is selected
     * @param onSelect callback to be invoked when your item is being clicked,
     * therefore the selection of this item is requested. Not invoked if item is already selected
     */
    @Composable
    fun RadioGroupItem(
        selected: Boolean,
        onSelect: () -> Unit,
        content: @Composable() () -> Unit
    ) {
        Semantics(container = true, mergeAllDescendants = true) {
            Box {
                MutuallyExclusiveSetItem(
                    selected = selected,
                    onClick = { if (!selected) onSelect() }, children = content,
                    modifier = Modifier.ripple()
                )
            }
        }
    }

    /**
     * Simple component to be used inside [RadioGroup] as a child.
     * Places [RadioButton] and [Text] inside the [Row].
     *
     * @param selected whether or not radio button in this item is selected
     * @param onSelect callback to be invoked when your item is being clicked, therefore the
     * selection of this item is requested. Not invoked if item is already selected
     * @param text to put as a label description of this item
     * @param radioColor color for RadioButtons when selected
     * @param textStyle parameters for text customization
     */
    @Composable
    fun RadioGroupTextItem(
        selected: Boolean,
        onSelect: () -> Unit,
        text: String,
        radioColor: Color = MaterialTheme.colors.secondary,
        textStyle: TextStyle? = null
    ) {
        RadioGroupItem(selected = selected, onSelect = onSelect) {
            // TODO: remove this Box when Ripple becomes a modifier.
            Box {
                Row(Modifier.fillMaxWidth().padding(DefaultRadioItemPadding)) {
                    RadioButton(selected = selected, onSelect = onSelect, color = radioColor)
                    Text(
                        text = text,
                        style = MaterialTheme.typography.body1.merge(textStyle),
                        modifier = Modifier.padding(start = DefaultRadioLabelOffset)
                    )
                }
            }
        }
    }
}

/**
 * Component to represent two states, selected and not selected.
 *
 * RadioButton is usually used as a child of [RadioGroupScope.RadioGroupItem], and these items
 * and coupled together to [RadioGroup] to represent a multiple exclusion set of options
 * the user can choose from.
 *
 * @sample androidx.ui.material.samples.RadioButtonSample
 *
 * @param selected boolean state for this button: either it is selected or not
 * @param onSelect callback to be invoked when RadioButton is being clicked,
 * therefore the selection of this item is requested. Not invoked if item is already selected
 * @param color color of the RadioButton
 */
@Composable
@Suppress("DEPRECATION")
fun RadioButton(
    selected: Boolean,
    onSelect: (() -> Unit)?,
    color: Color = MaterialTheme.colors.secondary
) {
    val radioPaint = remember { Paint() }
    Stack {
        MutuallyExclusiveSetItem(
            selected = selected, onClick = { if (!selected) onSelect?.invoke() },
            modifier = Modifier.ripple(bounded = false)
        ) {
            val unselectedColor =
                MaterialTheme.colors.onSurface.copy(alpha = UnselectedOpacity)
            val definition = remember(color, unselectedColor) {
                generateTransitionDefinition(color, unselectedColor)
            }
            Transition(definition = definition, toState = selected) { state ->
                Canvas(Modifier.padding(RadioButtonPadding).preferredSize(RadioButtonSize)) {
                    drawRadio(
                        state[ColorProp],
                        state[OuterRadiusProp],
                        state[InnerRadiusProp],
                        state[GapProp],
                        radioPaint
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawRadio(
    color: Color,
    outerRadius: Dp,
    innerRadius: Dp,
    gap: Dp,
    paint: Paint
) {
    paint.isAntiAlias = true
    paint.color = color

    // TODO(malkov): currently Radio gravity is always CENTER but we need to be flexible
    val centerW = size.width.value / 2
    val centerH = size.height.value / 2
    val outerPx = outerRadius.toPx().value
    val innerPx = innerRadius.toPx().value

    val center = Offset(centerW, centerH)
    if (gap == 0.dp) {
        val strokeWidth = outerPx - innerPx
        paint.style = PaintingStyle.stroke
        paint.strokeWidth = strokeWidth
        drawCircle(center, outerPx - strokeWidth / 2, paint)
    } else {
        val strokeWidth = RadioStrokeWidth.toPx().value
        paint.style = PaintingStyle.stroke
        paint.strokeWidth = strokeWidth
        drawCircle(center, outerPx - strokeWidth / 2, paint)

        val gapWidth = gap.toPx().value
        val circleRadius = outerPx - strokeWidth - gapWidth
        val innerCircleStrokeWidth = circleRadius - innerPx

        paint.strokeWidth = innerCircleStrokeWidth
        drawCircle(center, circleRadius - innerCircleStrokeWidth / 2, paint)
    }
}

private val OuterRadiusProp = DpPropKey()
private val InnerRadiusProp = DpPropKey()
private val GapProp = DpPropKey()
private val ColorProp = ColorPropKey()

private const val RadiusClosureDuration = 150
private const val PulseDuration = 100
private const val GapDuration = 150
private const val TotalDuration = RadiusClosureDuration + PulseDuration + GapDuration

private fun generateTransitionDefinition(
    selectedColor: Color,
    unselectedColor: Color
) = transitionDefinition {
    state(false) {
        this[OuterRadiusProp] = RadioRadius
        this[InnerRadiusProp] = InitialInner
        this[GapProp] = 0.dp
        this[ColorProp] = unselectedColor
    }
    state(true) {
        this[OuterRadiusProp] = RadioRadius
        this[InnerRadiusProp] = 0.dp
        this[GapProp] = DefaultGap
        this[ColorProp] = selectedColor
    }
    transition(fromState = false, toState = true) {
        ColorProp using snap()
        OuterRadiusProp using keyframes {
            val smallerOuter = RadioRadius - OuterOffsetDuringAnimation + RadioStrokeWidth / 2
            duration = TotalDuration
            RadioRadius at 0
            smallerOuter at RadiusClosureDuration
            smallerOuter + PulseDelta at RadiusClosureDuration + PulseDuration / 2
            smallerOuter at RadiusClosureDuration + PulseDuration
            RadioRadius at TotalDuration
        }
        InnerRadiusProp using tween {
            duration = RadiusClosureDuration
        }
        GapProp using tween {
            delay = RadiusClosureDuration + PulseDuration
            duration = GapDuration
        }
    }
    transition(fromState = true, toState = false) {
        ColorProp using snap()
        OuterRadiusProp using keyframes {
            val smallerOuter = RadioRadius - OuterOffsetDuringAnimation + RadioStrokeWidth / 2
            duration = TotalDuration
            RadioRadius at 0
            smallerOuter at GapDuration
            smallerOuter + PulseDelta at GapDuration + PulseDuration / 2
            smallerOuter at GapDuration + PulseDuration
            RadioRadius at TotalDuration
        }
        GapProp using tween {
            duration = GapDuration
        }
        InnerRadiusProp using tween {
            delay = GapDuration + PulseDuration
            duration = RadiusClosureDuration
        }
    }
}

private val RadioButtonPadding = 2.dp
private val RadioButtonSize = 20.dp
private val RadioRadius = RadioButtonSize / 2
private val RadioStrokeWidth = 2.dp
// TODO(malkov): random numbers for now to produce radio as in material comp.
private val DefaultGap = 3.dp
private const val UnselectedOpacity = 0.6f

// for animations
private val OuterOffsetDuringAnimation = 2.dp
private val PulseDelta = 0.5.dp
private val InitialInner = RadioRadius - RadioStrokeWidth

private val DefaultRadioLabelOffset = 20.dp
private val DefaultRadioItemPadding = 10.dp

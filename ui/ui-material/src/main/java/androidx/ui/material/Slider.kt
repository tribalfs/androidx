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

package androidx.ui.material

import android.annotation.SuppressLint
import androidx.animation.AnimatedFloat
import androidx.animation.AnimationClockObservable
import androidx.animation.AnimationEndReason
import androidx.animation.TargetAnimation
import androidx.animation.TweenBuilder
import androidx.annotation.IntRange
import androidx.compose.Composable
import androidx.compose.remember
import androidx.ui.animation.asDisposableClock
import androidx.ui.core.Alignment
import androidx.ui.core.AnimationClockAmbient
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Modifier
import androidx.ui.core.WithConstraints
import androidx.ui.core.gesture.pressIndicatorGestureFilter
import androidx.ui.core.semantics.semantics
import androidx.ui.foundation.Box
import androidx.ui.foundation.Canvas
import androidx.ui.foundation.Interaction
import androidx.ui.foundation.InteractionState
import androidx.ui.foundation.Strings
import androidx.ui.foundation.animation.FlingConfig
import androidx.ui.foundation.animation.fling
import androidx.ui.foundation.gestures.DragDirection
import androidx.ui.foundation.gestures.draggable
import androidx.ui.foundation.indication
import androidx.ui.foundation.shape.corner.CircleShape
import androidx.ui.geometry.Offset
import androidx.ui.geometry.lerp
import androidx.ui.graphics.Color
import androidx.ui.graphics.PointMode
import androidx.ui.graphics.StrokeCap
import androidx.ui.layout.Spacer
import androidx.ui.layout.Stack
import androidx.ui.layout.fillMaxSize
import androidx.ui.layout.padding
import androidx.ui.layout.preferredHeightIn
import androidx.ui.layout.preferredSize
import androidx.ui.layout.preferredWidthIn
import androidx.ui.material.ripple.RippleIndication
import androidx.ui.semantics.AccessibilityRangeInfo
import androidx.ui.semantics.accessibilityValue
import androidx.ui.semantics.accessibilityValueRange
import androidx.ui.semantics.scrollBackward
import androidx.ui.semantics.scrollForward
import androidx.ui.semantics.setProgress
import androidx.ui.unit.dp
import androidx.ui.util.lerp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sliders allow users to make selections from a range of values.
 *
 * Sliders reflect a range of values along a bar, from which users may select a single value.
 * They are ideal for adjusting settings such as volume, brightness, or applying image filters.
 *
 * Use continuous sliders allow users to make meaningful selections that don’t
 * require a specific value:
 *
 * @sample androidx.ui.material.samples.SliderSample
 *
 * You can allow the user to choose only between predefined set of values by specifying the amount
 * of steps between min and max values:
 *
 * @sample androidx.ui.material.samples.StepsSliderSample
 *
 * @param value current value of the Slider. If outside of [valueRange] provided, value will be
 * coerced to this range.
 * @param onValueChange lambda in which value should be updated
 * @param modifier modifiers for the Slider layout
 * @param valueRange range of values that Slider value can take. Passed [value] will be coerced to
 * this range
 * @param steps if greater than 0, specifies the amounts of discrete values, evenly distributed
 * between across the whole value range. If 0, slider will behave as a continuous slider and allow
 * to choose any value from the range specified
 * @param onValueChangeEnd lambda to be invoked when value change has ended. This callback
 * shouldn't be used to update the slider value (use [onValueChange] for that), but rather to
 * know when the user has completed selecting a new value by ending a drag or a click.
 * @param color color of the Slider
 */
@Composable
fun Slider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0) steps: Int = 0,
    onValueChangeEnd: () -> Unit = {},
    color: Color = MaterialTheme.colors.primary
) {
    val clock = AnimationClockAmbient.current.asDisposableClock()
    val position = remember(valueRange, steps) {
        SliderPosition(value, valueRange, steps, clock, onValueChange)
    }
    position.onValueChange = onValueChange
    position.scaledValue = value
    WithConstraints(modifier.sliderSemantics(value, position, onValueChange, valueRange, steps)) {
        val maxPx = constraints.maxWidth.toFloat()
        val minPx = 0f
        position.setBounds(minPx, maxPx)

        val flingConfig = SliderFlingConfig(position, position.anchorsPx) { endValue ->
            position.holder.snapTo(endValue)
            onValueChangeEnd()
        }
        val gestureEndAction = { velocity: Float ->
            if (flingConfig != null) {
                position.holder.fling(flingConfig, velocity)
            } else {
                onValueChangeEnd()
            }
        }

        val interactionState = remember { InteractionState() }

        val press = Modifier.pressIndicatorGestureFilter(
            onStart = { pos ->
                position.holder.snapTo(pos.x)
                interactionState.addInteraction(Interaction.Pressed, pos)
            },
            onStop = {
                gestureEndAction(0f)
                interactionState.removeInteraction(Interaction.Pressed)
            },
            onCancel = {
                interactionState.removeInteraction(Interaction.Pressed)
            }
        )

        val drag = Modifier.draggable(
            dragDirection = DragDirection.Horizontal,
            interactionState = interactionState,
            onDragDeltaConsumptionRequested = { delta ->
                position.holder.snapTo(position.holder.value + delta)
                // consume all so slider won't participate in nested scrolling
                delta
            },
            onDragStopped = gestureEndAction,
            startDragImmediately = position.holder.isRunning
        )
        val coerced = value.coerceIn(position.startValue, position.endValue)
        val fraction = calcFraction(position.startValue, position.endValue, coerced)
        SliderImpl(
            fraction,
            position.tickFractions,
            color,
            maxPx,
            interactionState,
            modifier = press.plus(drag)
        )
    }
}

@Composable
private fun SliderImpl(
    positionFraction: Float,
    tickFractions: List<Float>,
    color: Color,
    width: Float,
    interactionState: InteractionState,
    modifier: Modifier
) {
    val widthDp = with(DensityAmbient.current) {
        width.toDp()
    }
    Stack(modifier + DefaultSliderConstraints) {
        val thumbSize = ThumbRadius * 2
        val offset = (widthDp - thumbSize) * positionFraction
        val center = Modifier.gravity(Alignment.CenterStart)

        val trackStrokeWidth: Float
        val thumbPx: Float
        with(DensityAmbient.current) {
            trackStrokeWidth = TrackHeight.toPx()
            thumbPx = ThumbRadius.toPx()
        }
        Track(
            center.fillMaxSize(),
            color,
            positionFraction,
            tickFractions,
            thumbPx,
            trackStrokeWidth
        )
        Box(center.padding(start = offset)) {
            val elevation = if (
                Interaction.Pressed in interactionState || Interaction.Dragged in interactionState
            ) {
                ThumbPressedElevation
            } else {
                ThumbDefaultElevation
            }
            Surface(
                shape = CircleShape,
                color = color,
                elevation = elevation,
                modifier = Modifier.indication(
                    interactionState = interactionState,
                    indication = RippleIndication(
                        radius = ThumbRippleRadius,
                        bounded = false
                    )
                )
            ) {
                Spacer(Modifier.preferredSize(thumbSize, thumbSize))
            }
        }
    }
}

@Composable
private fun Track(
    modifier: Modifier,
    color: Color,
    positionFraction: Float,
    tickFractions: List<Float>,
    thumbPx: Float,
    trackStrokeWidth: Float
) {
    val activeTickColor = MaterialTheme.colors.onPrimary.copy(alpha = TickColorAlpha)
    val inactiveTickColor = color.copy(alpha = TickColorAlpha)
    Canvas(modifier) {
        val sliderStart = Offset(thumbPx, center.y)
        val sliderMax = Offset(size.width - thumbPx, center.y)
        drawLine(
            color.copy(alpha = InactiveTrackColorAlpha),
            sliderStart,
            sliderMax,
            trackStrokeWidth,
            StrokeCap.round
        )
        val sliderValue = Offset(
            sliderStart.x + (sliderMax.x - sliderStart.x) * positionFraction,
            center.y
        )

        drawLine(color, sliderStart, sliderValue, trackStrokeWidth)
        tickFractions.groupBy { it > positionFraction }.forEach { (afterFraction, list) ->
            drawPoints(
                list.map {
                    Offset(lerp(sliderStart, sliderMax, it).x, center.y)
                },
                PointMode.points,
                if (afterFraction) inactiveTickColor else activeTickColor,
                trackStrokeWidth
            )
        }
    }
}

// Scale x1 from a1..b1 range to a2..b2 range
private fun scale(a1: Float, b1: Float, x1: Float, a2: Float, b2: Float) =
    lerp(a2, b2, calcFraction(a1, b1, x1))

// Calculate the 0..1 fraction that `pos` value represents between `a` and `b`
private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)

@Composable
private fun SliderFlingConfig(
    value: SliderPosition,
    anchors: List<Float>,
    onSuccessfulEnd: (Float) -> Unit
): FlingConfig? {
    if (anchors.isEmpty()) {
        return null
    } else {
        val adjustTarget: (Float) -> TargetAnimation? = { _ ->
            val now = value.holder.value
            val point = anchors.minBy { abs(it - now) }
            val adjusted = point ?: now
            TargetAnimation(adjusted, SliderToTickAnimation)
        }
        return FlingConfig(
            adjustTarget = adjustTarget,
            onAnimationEnd = { reason, endValue, _ ->
                if (reason != AnimationEndReason.Interrupted) {
                    onSuccessfulEnd(endValue)
                }
            }
        )
    }
}

private fun Modifier.sliderSemantics(
    value: Float,
    position: SliderPosition,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0) steps: Int = 0
): Modifier {
    val coerced = value.coerceIn(position.startValue, position.endValue)
    val fraction = calcFraction(position.startValue, position.endValue, coerced)
    // We only display 0% or 100% when it is exactly 0% or 100%.
    val percent = when (fraction) {
        0f -> 0
        1f -> 100
        else -> (fraction * 100).roundToInt().coerceIn(1, 99)
    }
    return semantics {
        accessibilityValue = Strings.TemplatePercent.format(percent)
        accessibilityValueRange = AccessibilityRangeInfo(coerced, valueRange)
        setProgress(action = { setSliderProgress(it, coerced, position, onValueChange, steps) })

        // TODO(b/157692376) Remove accessibility scroll actions in Slider when
        //  talkback is fixed
        var increment = (position.endValue - position.startValue) / AccessibilityStepsCount
        if (steps > 0) {
            increment = (position.endValue - position.startValue) / (steps + 1)
        }
        if (coerced < position.endValue) {
            @Suppress("DEPRECATION")
            scrollForward(action = {
                setSliderProgress(coerced + increment, coerced, position, onValueChange, steps)
            })
        }
        if (coerced > position.startValue) {
            @Suppress("DEPRECATION")
            scrollBackward(action = {
                setSliderProgress(coerced - increment, coerced, position, onValueChange, steps)
            })
        }
    }
}

private fun setSliderProgress(
    targetValue: Float,
    currentValue: Float,
    position: SliderPosition,
    onValueChange: (Float) -> Unit,
    @IntRange(from = 0) steps: Int = 0
): Boolean {
    var newValue = targetValue.coerceIn(position.startValue, position.endValue)
    if (steps >= 0) {
        val anchorsValue = position.tickFractions.map {
            lerp(position.startValue, position.endValue, it)
        }
        val point = anchorsValue.minBy { abs(it - newValue) }
        newValue = point ?: newValue
    }
    // This is to keep it consistent with AbsSeekbar.java: return false if no
    // change from current.
    if (newValue == currentValue) {
        return false
    }
    onValueChange(newValue)
    return true
}

// 20 is taken from AbsSeekbar.java.
private const val AccessibilityStepsCount = 20

/**
 * Internal state for [Slider] that represents the Slider value, its bounds and optional amount of
 * steps evenly distributed across the Slider range.
 *
 * @param initial initial value for the Slider when created. If outside of range provided,
 * initial position will be coerced to this range
 * @param valueRange range of values that Slider value can take
 * @param steps if greater than 0, specifies the amounts of discrete values, evenly distributed
 * between across the whole value range. If 0, slider will behave as a continuous slider and allow
 * to choose any value from the range specified
 */
private class SliderPosition(
    initial: Float = 0f,
    val valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    @IntRange(from = 0) steps: Int = 0,
    animatedClock: AnimationClockObservable,
    var onValueChange: (Float) -> Unit
) {

    internal val startValue: Float = valueRange.start
    internal val endValue: Float = valueRange.endInclusive

    init {
        require(steps >= 0) {
            "steps should be >= 0"
        }
    }

    internal var scaledValue: Float = initial
        set(value) {
            val scaled = scale(startValue, endValue, value, startPx, endPx)
            // floating point error due to rescaling
            if ((scaled - holder.value) > floatPointMistakeCorrection) {
                holder.snapTo(scaled)
            }
        }

    private val floatPointMistakeCorrection = (valueRange.endInclusive - valueRange.start) / 100

    private var endPx = Float.MAX_VALUE
    private var startPx = Float.MIN_VALUE

    internal fun setBounds(min: Float, max: Float) {
        if (startPx == min && endPx == max) return
        val newValue = scale(startPx, endPx, holder.value, min, max)
        startPx = min
        endPx = max
        holder.setBounds(min, max)
        anchorsPx = tickFractions.map {
            lerp(startPx, endPx, it)
        }
        holder.snapTo(newValue)
    }

    internal val tickFractions: List<Float> =
        if (steps == 0) emptyList() else List(steps + 2) { it.toFloat() / (steps + 1) }

    internal var anchorsPx: List<Float> = emptyList()
        private set

    @SuppressLint("UnnecessaryLambdaCreation")
    internal val holder =
        CallbackBasedAnimatedFloat(
            scale(startValue, endValue, initial, startPx, endPx),
            animatedClock
        ) { onValueChange(scale(startPx, endPx, it, startValue, endValue)) }
}

private class CallbackBasedAnimatedFloat(
    initial: Float,
    clock: AnimationClockObservable,
    var onValue: (Float) -> Unit
) : AnimatedFloat(clock) {

    override var value = initial
        set(value) {
            onValue(value)
            field = value
        }
}

private val ThumbRadius = 10.dp
private val ThumbRippleRadius = 24.dp
private val ThumbDefaultElevation = 1.dp
private val ThumbPressedElevation = 6.dp
private val TrackHeight = 4.dp
private val SliderHeight = 48.dp
private val SliderMinWidth = 144.dp // TODO: clarify min width
private val DefaultSliderConstraints =
    Modifier.preferredWidthIn(minWidth = SliderMinWidth)
        .preferredHeightIn(maxHeight = SliderHeight)
private val InactiveTrackColorAlpha = 0.24f
private val TickColorAlpha = 0.54f
private val SliderToTickAnimation = TweenBuilder<Float>().apply { duration = 100 }

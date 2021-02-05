/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.graphics.vector

import androidx.compose.animation.core.AnimationVector4D
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.KeyframesSpec
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateValue
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.util.fastMaxBy
import androidx.compose.ui.util.lerp

internal sealed class Animator {
    abstract val totalDuration: Int

    @Composable
    fun createVectorOverride(
        transition: Transition<Boolean>,
        overallDuration: Int
    ): VectorOverride {
        return StateVectorOverride().also { override ->
            Configure(transition, override, overallDuration, 0)
        }
    }

    @Composable
    abstract fun Configure(
        transition: Transition<Boolean>,
        override: StateVectorOverride,
        overallDuration: Int,
        parentDelay: Int
    )
}

internal data class ObjectAnimator(
    val duration: Int,
    val startDelay: Int,
    val repeatCount: Int,
    val repeatMode: RepeatMode,
    val holders: List<PropertyValuesHolder<*>>
) : Animator() {

    override val totalDuration = if (repeatCount == Int.MAX_VALUE) {
        Int.MAX_VALUE
    } else {
        startDelay + duration * (repeatCount + 1)
    }

    @Composable
    override fun Configure(
        transition: Transition<Boolean>,
        override: StateVectorOverride,
        overallDuration: Int,
        parentDelay: Int
    ) {
        for (holder in holders) {
            holder.AnimateIn(
                override,
                transition,
                overallDuration,
                duration,
                parentDelay + startDelay
            )
        }
    }
}

internal data class AnimatorSet(
    val animators: List<Animator>,
    val ordering: Ordering
) : Animator() {

    override val totalDuration = when (ordering) {
        Ordering.Together -> animators.fastMaxBy { it.totalDuration }?.totalDuration ?: 0
        Ordering.Sequentially -> animators.sumBy { it.totalDuration }
    }

    @Composable
    override fun Configure(
        transition: Transition<Boolean>,
        override: StateVectorOverride,
        overallDuration: Int,
        parentDelay: Int
    ) {
        when (ordering) {
            Ordering.Together -> {
                for (animator in animators) {
                    animator.Configure(transition, override, overallDuration, parentDelay)
                }
            }
            Ordering.Sequentially -> {
                var accumulatedDelay = parentDelay
                for (animator in animators) {
                    animator.Configure(transition, override, overallDuration, accumulatedDelay)
                    accumulatedDelay += animator.totalDuration
                }
            }
        }
    }
}

internal sealed class PropertyValuesHolder<T> {

    @Composable
    abstract fun AnimateIn(
        override: StateVectorOverride,
        transition: Transition<Boolean>,
        overallDuration: Int,
        duration: Int,
        delay: Int
    )
}

internal data class PropertyValuesHolder2D(
    val xPropertyName: String,
    val yPropertyName: String,
    val pathData: List<PathNode>,
    val interpolator: Easing
) : PropertyValuesHolder<Pair<Float, Float>>() {

    @Composable
    override fun AnimateIn(
        override: StateVectorOverride,
        transition: Transition<Boolean>,
        overallDuration: Int,
        duration: Int,
        delay: Int
    ) {
        // TODO(b/178978971): Implement path animation.
    }
}

internal sealed class PropertyValuesHolder1D<T>(
    val propertyName: String
) : PropertyValuesHolder<T>() {

    abstract val animatorKeyframes: List<Keyframe<T>>

    protected val targetValueByState: @Composable (Boolean) -> T = { atEnd ->
        if (atEnd) {
            animatorKeyframes.last().value
        } else {
            animatorKeyframes.first().value
        }
    }

    protected fun <R> createTransitionSpec(
        overallDuration: Int,
        duration: Int,
        delay: Int,
        addKeyframe: KeyframesSpec.KeyframesSpecConfig<R>.(
            keyframe: Keyframe<T>,
            time: Int,
            easing: Easing
        ) -> Unit
    ): @Composable Transition.Segment<Boolean>.() -> FiniteAnimationSpec<R> {
        return {
            if (targetState) { // at end
                keyframes {
                    durationMillis = duration
                    delayMillis = delay
                    for (keyframe in animatorKeyframes) {
                        val time = (duration * keyframe.fraction).toInt()
                        addKeyframe(keyframe, time, keyframe.interpolator)
                    }
                }
            } else {
                keyframes {
                    durationMillis = duration
                    delayMillis = overallDuration - duration - delay
                    for (keyframe in animatorKeyframes) {
                        val time = (duration * (1 - keyframe.fraction)).toInt()
                        addKeyframe(keyframe, time, keyframe.interpolator.transpose())
                    }
                }
            }
        }
    }
}

internal class PropertyValuesHolderFloat(
    propertyName: String,
    override val animatorKeyframes: List<Keyframe<Float>>
) : PropertyValuesHolder1D<Float>(propertyName) {

    @Composable
    override fun AnimateIn(
        override: StateVectorOverride,
        transition: Transition<Boolean>,
        overallDuration: Int,
        duration: Int,
        delay: Int
    ) {
        val state = transition.animateFloat(
            transitionSpec = createTransitionSpec(
                overallDuration,
                duration,
                delay
            ) { keyframe, time, easing ->
                keyframe.value at time with easing
            },
            label = propertyName,
            targetValueByState = targetValueByState
        )
        when (propertyName) {
            "rotation" -> override.rotationState = state
            "pivotX" -> override.pivotXState = state
            "pivotY" -> override.pivotYState = state
            "scaleX" -> override.scaleXState = state
            "scaleY" -> override.scaleYState = state
            "translateX" -> override.translateXState = state
            "translateY" -> override.translateYState = state
            "fillAlpha" -> override.fillAlphaState = state
            "strokeWidth" -> override.strokeWidthState = state
            "strokeAlpha" -> override.strokeAlphaState = state
            "trimPathStart" -> override.trimPathStartState = state
            "trimPathEnd" -> override.trimPathEndState = state
            "trimPathOffset" -> override.trimPathOffsetState = state
            else -> throw IllegalStateException("Unknown propertyName: $propertyName")
        }
    }
}

internal class PropertyValuesHolderInt(
    propertyName: String,
    override val animatorKeyframes: List<Keyframe<Int>>
) : PropertyValuesHolder1D<Int>(propertyName) {

    @Composable
    override fun AnimateIn(
        override: StateVectorOverride,
        transition: Transition<Boolean>,
        overallDuration: Int,
        duration: Int,
        delay: Int
    ) {
        // AnimatedVectorDrawable does not have an Int property; Ignore.
    }
}

internal class PropertyValuesHolderColor(
    propertyName: String,
    override val animatorKeyframes: List<Keyframe<Color>>
) : PropertyValuesHolder1D<Color>(propertyName) {

    @Composable
    override fun AnimateIn(
        override: StateVectorOverride,
        transition: Transition<Boolean>,
        overallDuration: Int,
        duration: Int,
        delay: Int
    ) {
        val state = transition.animateColor(
            transitionSpec = createTransitionSpec(
                overallDuration,
                duration,
                delay
            ) { keyframe, time, easing ->
                keyframe.value at time with easing
            },
            label = propertyName,
            targetValueByState = targetValueByState
        )
        when (propertyName) {
            "fillColor" -> override.fillColorState = state
            "strokeColor" -> override.strokeColorState = state
            else -> throw IllegalStateException("Unknown propertyName: $propertyName")
        }
    }
}

internal class PropertyValuesHolderPath(
    propertyName: String,
    override val animatorKeyframes: List<Keyframe<List<PathNode>>>
) : PropertyValuesHolder1D<List<PathNode>>(propertyName) {

    @Composable
    override fun AnimateIn(
        override: StateVectorOverride,
        transition: Transition<Boolean>,
        overallDuration: Int,
        duration: Int,
        delay: Int
    ) {
        if (propertyName == "pathData") {
            val state = transition.animateFloat(
                transitionSpec = createTransitionSpec(
                    overallDuration,
                    duration,
                    delay
                ) { keyframe, time, easing ->
                    keyframe.fraction at time with easing
                }
            ) { atEnd ->
                if (atEnd) {
                    animatorKeyframes.last().fraction
                } else {
                    animatorKeyframes.first().fraction
                }
            }
            override.pathDataStatePair = state to this
        } else {
            throw IllegalStateException("Unknown propertyName: $propertyName")
        }
    }

    fun interpolate(fraction: Float): List<PathNode> {
        val index = (animatorKeyframes.indexOfFirst { it.fraction > fraction } - 1).coerceAtLeast(0)
        val easing = animatorKeyframes[index + 1].interpolator
        val innerFraction = easing.transform(
            (
                (fraction - animatorKeyframes[index].fraction) /
                    (animatorKeyframes[index + 1].fraction - animatorKeyframes[index].fraction)
                )
                .coerceIn(0f, 1f)
        )
        return lerp(
            animatorKeyframes[index].value,
            animatorKeyframes[index + 1].value,
            innerFraction
        )
    }
}

internal data class Keyframe<T>(
    val fraction: Float,
    val value: T,
    val interpolator: Easing
)

internal enum class Ordering {
    Together,
    Sequentially
}

internal class StateVectorOverride : VectorOverride {

    var rotationState: State<Float>? = null
    var pivotXState: State<Float>? = null
    var pivotYState: State<Float>? = null
    var pivotState: State<Offset>? = null
    var scaleXState: State<Float>? = null
    var scaleYState: State<Float>? = null
    var scaleState: State<Offset>? = null
    var translateXState: State<Float>? = null
    var translateYState: State<Float>? = null
    var translateState: State<Offset>? = null

    // PathData is special because we have to animate its float fraction and interpolate the path.
    var pathDataStatePair: Pair<State<Float>, PropertyValuesHolderPath>? = null
    var fillColorState: State<Color>? = null
    var strokeColorState: State<Color>? = null
    var strokeWidthState: State<Float>? = null
    var strokeAlphaState: State<Float>? = null
    var fillAlphaState: State<Float>? = null
    var trimPathStartState: State<Float>? = null
    var trimPathEndState: State<Float>? = null
    var trimPathOffsetState: State<Float>? = null

    override fun obtainRotation(rotation: Float): Float {
        return rotationState?.value ?: rotation
    }

    override fun obtainPivotX(pivotX: Float): Float {
        return pivotXState?.value ?: pivotState?.value?.x ?: pivotX
    }

    override fun obtainPivotY(pivotY: Float): Float {
        return pivotYState?.value ?: pivotState?.value?.y ?: pivotY
    }

    override fun obtainScaleX(scaleX: Float): Float {
        return scaleXState?.value ?: scaleState?.value?.x ?: scaleX
    }

    override fun obtainScaleY(scaleY: Float): Float {
        return scaleYState?.value ?: scaleState?.value?.y ?: scaleY
    }

    override fun obtainTranslateX(translateX: Float): Float {
        return translateXState?.value ?: translateState?.value?.x ?: translateX
    }

    override fun obtainTranslateY(translateY: Float): Float {
        return translateYState?.value ?: translateState?.value?.y ?: translateY
    }

    override fun obtainPathData(pathData: List<PathNode>): List<PathNode> {
        return pathDataStatePair.let { pair ->
            if (pair != null) {
                val (state, holder) = pair
                holder.interpolate(state.value)
            } else {
                pathData
            }
        }
    }

    override fun obtainFill(fill: Brush?): Brush? {
        return fillColorState.let { state ->
            if (state != null) {
                SolidColor(state.value)
            } else {
                fill
            }
        }
    }

    override fun obtainFillAlpha(fillAlpha: Float): Float {
        return fillAlphaState?.value ?: fillAlpha
    }

    override fun obtainStroke(stroke: Brush?): Brush? {
        return strokeColorState.let { state ->
            if (state != null) {
                SolidColor(state.value)
            } else {
                stroke
            }
        }
    }

    override fun obtainStrokeWidth(strokeWidth: Float): Float {
        return strokeWidthState?.value ?: strokeWidth
    }

    override fun obtainStrokeAlpha(strokeAlpha: Float): Float {
        return strokeAlphaState?.value ?: strokeAlpha
    }

    override fun obtainTrimPathStart(trimPathStart: Float): Float {
        return trimPathStartState?.value ?: trimPathStart
    }

    override fun obtainTrimPathEnd(trimPathEnd: Float): Float {
        return trimPathEndState?.value ?: trimPathEnd
    }

    override fun obtainTrimPathOffset(trimPathOffset: Float): Float {
        return trimPathOffsetState?.value ?: trimPathOffset
    }
}

@Composable
private inline fun <S> Transition<S>.animateColor(
    noinline transitionSpec: @Composable Transition.Segment<S>.() -> FiniteAnimationSpec<Color>,
    label: String = "ColorAnimation",
    targetValueByState: @Composable (state: S) -> Color
): State<Color> {
    val colorSpace = targetValueByState(targetState).colorSpace
    val typeConverter = remember(colorSpace) {
        ColorToVector(colorSpace)
    }
    return animateValue(typeConverter, transitionSpec, label, targetValueByState)
}

private val ColorToVector: (colorSpace: ColorSpace) -> TwoWayConverter<Color, AnimationVector4D> =
    { colorSpace ->
        TwoWayConverter(
            convertToVector = {
                val linearColor = it.convert(ColorSpaces.LinearExtendedSrgb)
                AnimationVector4D(
                    linearColor.alpha, linearColor.red, linearColor.green, linearColor.blue
                )
            },
            convertFromVector = {
                Color(
                    alpha = it.v1.coerceIn(0.0f, 1.0f),
                    red = it.v2.coerceIn(0.0f, 1.0f),
                    green = it.v3.coerceIn(0.0f, 1.0f),
                    blue = it.v4.coerceIn(0.0f, 1.0f),
                    colorSpace = ColorSpaces.LinearExtendedSrgb
                ).convert(colorSpace)
            }
        )
    }

private fun Easing.transpose(): Easing {
    return Easing { x -> 1 - this.transform(1 - x) }
}

private fun lerp(start: List<PathNode>, stop: List<PathNode>, fraction: Float): List<PathNode> {
    return start.zip(stop) { a, b -> lerp(a, b, fraction) }
}

/**
 * Linearly interpolate between [start] and [stop] with [fraction] fraction between them.
 */
private fun lerp(start: PathNode, stop: PathNode, fraction: Float): PathNode {
    return when (start) {
        is PathNode.RelativeMoveTo -> {
            require(stop is PathNode.RelativeMoveTo)
            PathNode.RelativeMoveTo(
                lerp(start.dx, stop.dx, fraction),
                lerp(start.dy, stop.dy, fraction)
            )
        }
        is PathNode.MoveTo -> {
            require(stop is PathNode.MoveTo)
            PathNode.MoveTo(
                lerp(start.x, stop.x, fraction),
                lerp(start.y, stop.y, fraction)
            )
        }
        is PathNode.RelativeLineTo -> {
            require(stop is PathNode.RelativeLineTo)
            PathNode.RelativeLineTo(
                lerp(start.dx, stop.dx, fraction),
                lerp(start.dy, stop.dy, fraction)
            )
        }
        is PathNode.LineTo -> {
            require(stop is PathNode.LineTo)
            PathNode.LineTo(
                lerp(start.x, stop.x, fraction),
                lerp(start.y, stop.y, fraction)
            )
        }
        is PathNode.RelativeHorizontalTo -> {
            require(stop is PathNode.RelativeHorizontalTo)
            PathNode.RelativeHorizontalTo(
                lerp(start.dx, stop.dx, fraction)
            )
        }
        is PathNode.HorizontalTo -> {
            require(stop is PathNode.HorizontalTo)
            PathNode.HorizontalTo(
                lerp(start.x, stop.x, fraction)
            )
        }
        is PathNode.RelativeVerticalTo -> {
            require(stop is PathNode.RelativeVerticalTo)
            PathNode.RelativeVerticalTo(
                lerp(start.dy, stop.dy, fraction)
            )
        }
        is PathNode.VerticalTo -> {
            require(stop is PathNode.VerticalTo)
            PathNode.VerticalTo(
                lerp(start.y, stop.y, fraction)
            )
        }
        is PathNode.RelativeCurveTo -> {
            require(stop is PathNode.RelativeCurveTo)
            PathNode.RelativeCurveTo(
                lerp(start.dx1, stop.dx1, fraction),
                lerp(start.dy1, stop.dy1, fraction),
                lerp(start.dx2, stop.dx2, fraction),
                lerp(start.dy2, stop.dy2, fraction),
                lerp(start.dx3, stop.dx3, fraction),
                lerp(start.dy3, stop.dy3, fraction)
            )
        }
        is PathNode.CurveTo -> {
            require(stop is PathNode.CurveTo)
            PathNode.CurveTo(
                lerp(start.x1, stop.x1, fraction),
                lerp(start.y1, stop.y1, fraction),
                lerp(start.x2, stop.x2, fraction),
                lerp(start.y2, stop.y2, fraction),
                lerp(start.x3, stop.x3, fraction),
                lerp(start.y3, stop.y3, fraction)
            )
        }
        is PathNode.RelativeReflectiveCurveTo -> {
            require(stop is PathNode.RelativeReflectiveCurveTo)
            PathNode.RelativeReflectiveCurveTo(
                lerp(start.dx1, stop.dx1, fraction),
                lerp(start.dy1, stop.dy1, fraction),
                lerp(start.dx2, stop.dx2, fraction),
                lerp(start.dy2, stop.dy2, fraction)
            )
        }
        is PathNode.ReflectiveCurveTo -> {
            require(stop is PathNode.ReflectiveCurveTo)
            PathNode.ReflectiveCurveTo(
                lerp(start.x1, stop.x1, fraction),
                lerp(start.y1, stop.y1, fraction),
                lerp(start.x2, stop.x2, fraction),
                lerp(start.y2, stop.y2, fraction)
            )
        }
        is PathNode.RelativeQuadTo -> {
            require(stop is PathNode.RelativeQuadTo)
            PathNode.RelativeQuadTo(
                lerp(start.dx1, stop.dx1, fraction),
                lerp(start.dy1, stop.dy1, fraction),
                lerp(start.dx2, stop.dx2, fraction),
                lerp(start.dy2, stop.dy2, fraction)
            )
        }
        is PathNode.QuadTo -> {
            require(stop is PathNode.QuadTo)
            PathNode.QuadTo(
                lerp(start.x1, stop.x1, fraction),
                lerp(start.y1, stop.y1, fraction),
                lerp(start.x2, stop.x2, fraction),
                lerp(start.y2, stop.y2, fraction)
            )
        }
        is PathNode.RelativeReflectiveQuadTo -> {
            require(stop is PathNode.RelativeReflectiveQuadTo)
            PathNode.RelativeReflectiveQuadTo(
                lerp(start.dx, stop.dx, fraction),
                lerp(start.dy, stop.dy, fraction)
            )
        }
        is PathNode.ReflectiveQuadTo -> {
            require(stop is PathNode.ReflectiveQuadTo)
            PathNode.ReflectiveQuadTo(
                lerp(start.x, stop.x, fraction),
                lerp(start.y, stop.y, fraction)
            )
        }
        is PathNode.RelativeArcTo -> {
            require(stop is PathNode.RelativeArcTo)
            PathNode.RelativeArcTo(
                lerp(start.horizontalEllipseRadius, stop.horizontalEllipseRadius, fraction),
                lerp(start.verticalEllipseRadius, stop.verticalEllipseRadius, fraction),
                lerp(start.theta, stop.theta, fraction),
                start.isMoreThanHalf,
                start.isPositiveArc,
                lerp(start.arcStartDx, stop.arcStartDx, fraction),
                lerp(start.arcStartDy, stop.arcStartDy, fraction)
            )
        }
        is PathNode.ArcTo -> {
            require(stop is PathNode.ArcTo)
            PathNode.ArcTo(
                lerp(start.horizontalEllipseRadius, stop.horizontalEllipseRadius, fraction),
                lerp(start.verticalEllipseRadius, stop.verticalEllipseRadius, fraction),
                lerp(start.theta, stop.theta, fraction),
                start.isMoreThanHalf,
                start.isPositiveArc,
                lerp(start.arcStartX, stop.arcStartX, fraction),
                lerp(start.arcStartY, stop.arcStartY, fraction)
            )
        }
        PathNode.Close -> PathNode.Close
    }
}

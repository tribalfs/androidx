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

package androidx.ui.material.ripple

import androidx.animation.FastOutSlowInEasing
import androidx.animation.FloatPropKey
import androidx.animation.InterruptionHandling
import androidx.animation.LinearEasing
import androidx.animation.PxPositionPropKey
import androidx.animation.PxPropKey
import androidx.animation.TransitionAnimation
import androidx.animation.transitionDefinition
import androidx.ui.core.Density
import androidx.ui.core.DensityReceiver
import androidx.ui.core.Dp
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.Px
import androidx.ui.core.PxPosition
import androidx.ui.core.PxSize
import androidx.ui.core.center
import androidx.ui.core.dp
import androidx.ui.core.getDistance
import androidx.ui.core.inMilliseconds
import androidx.ui.core.max
import androidx.ui.core.milliseconds
import androidx.ui.core.toOffset
import androidx.ui.core.toRect
import androidx.ui.core.withDensity
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.Paint
import androidx.ui.vectormath64.Matrix4
import androidx.ui.vectormath64.getAsTranslation

internal fun getRippleStartRadius(size: PxSize) =
    max(size.width, size.height) * 0.3f

internal fun DensityReceiver.getRippleTargetRadius(size: PxSize) =
    PxPosition(size.width, size.height).getDistance() / 2f + 10.dp.toPx()

/**
 * Used to specify this type of [RippleEffect] for [Ripple].
 */
object DefaultRippleEffectFactory : RippleEffectFactory {

    override fun create(
        coordinates: LayoutCoordinates,
        surfaceCoordinates: LayoutCoordinates,
        touchPosition: PxPosition,
        color: Color,
        density: Density,
        radius: Dp?,
        bounded: Boolean,
        requestRedraw: (() -> Unit),
        onAnimationFinished: ((RippleEffect) -> Unit)
    ): RippleEffect {
        return DefaultRippleEffect(
            coordinates,
            surfaceCoordinates,
            touchPosition,
            color,
            density,
            radius,
            bounded,
            requestRedraw,
            onAnimationFinished
        )
    }
}

/**
 * A visual reaction on a piece of [RippleSurface] to user input.
 *
 * Use [Ripple] to add an animation for your component.
 *
 * This is a default implementation based on the Material Design specification.
 *
 * A circular ripple effect whose origin starts at the input touch point and
 * whose radius expands from 60% of the final value. The ripple origin
 * animates to the center of its target layout for the bounded version
 * and stays in the center for the unbounded one.
 *
 * @param coordinates The layout coordinates of the target layout.
 * @param surfaceCoordinates The surface layout coordinates.
 * @param touchPosition The position the animation will start from.
 * @param color The color for this [RippleEffect].
 * @param density The [Density] object to convert the dimensions.
 * @param radius Effects grow up to this size. By default the size is
 *  determined from the size of the layout itself.
 * @param bounded If true, then the ripple will be sized to fit the bounds of the target
 *  layout, then clipped to it when drawn. If false, then the ripple is clipped only
 *  to the edges of the surface.
 * @param requestRedraw Call when the ripple should be redrawn to display the next frame.
 * @param onAnimationFinished Call when the effect animation has been finished.
 */
private class DefaultRippleEffect(
    private val coordinates: LayoutCoordinates,
    surfaceCoordinates: LayoutCoordinates,
    touchPosition: PxPosition,
    color: Color,
    density: Density,
    radius: Dp? = null,
    private val bounded: Boolean = false,
    private val requestRedraw: (() -> Unit),
    private val onAnimationFinished: ((RippleEffect) -> Unit)
) : RippleEffect(coordinates, surfaceCoordinates, color, requestRedraw) {

    private val animation: TransitionAnimation<RippleTransition.State>
    private var transitionState = RippleTransition.State.Initial
    private var finishRequested = false

    init {
        val surfaceSize = coordinates.size
        val startRadius = getRippleStartRadius(surfaceSize)
        val targetRadius = withDensity(density) {
            radius?.toPx() ?: getRippleTargetRadius(surfaceSize)
        }

        val center = coordinates.size.center()
        animation = RippleTransition.definition(
            revealedAlpha = color.alpha,
            startRadius = startRadius,
            endRadius = targetRadius,
            startCenter = if (bounded) touchPosition else center,
            endCenter = center
        ).createAnimation()
        animation.onUpdate = requestRedraw
        animation.onStateChangeFinished = { stage ->
            transitionState = stage
            if (transitionState == RippleTransition.State.Finished) {
                onAnimationFinished(this)
            }
        }
        // currently we are in Initial state, now we start the animation:
        animation.toState(RippleTransition.State.Revealed)
    }

    override fun finish(canceled: Boolean) {
        finishRequested = true
        animation.toState(RippleTransition.State.Finished)
    }

    override fun drawEffect(canvas: Canvas, transform: Matrix4) {
        val alpha = if (transitionState == RippleTransition.State.Initial && finishRequested) {
            // if we still fading-in we should immediately switch to the final alpha.
            color.alpha
        } else {
            animation[RippleTransition.Alpha]
        }
        val radius = animation[RippleTransition.Radius].value
        val centerOffset = animation[RippleTransition.Center].toOffset()
        val paint = Paint()
        paint.color = color.copy(alpha = alpha)
        val originOffset = transform.getAsTranslation()
        val clipRect = if (bounded) coordinates.size.toRect() else null
        if (originOffset == null) {
            // TODO (njawad) replace with save lambda when multi children DrawNodes are supported
            canvas.nativeCanvas.save()
            canvas.concat(transform)
            if (clipRect != null) {
                canvas.clipRect(clipRect)
            }
            canvas.drawCircle(centerOffset, radius, paint)
            // TODO (njawad) replace with save lambda when multi children DrawNodes are supported
            canvas.nativeCanvas.restore()
        } else {
            if (clipRect != null) {
                canvas.save()
                canvas.clipRect(clipRect.shift(originOffset))
            }
            canvas.drawCircle(centerOffset + originOffset, radius, paint)
            if (clipRect != null) {
                // TODO (njawad) replace with save lambda when multi children DrawNodes are supported
                canvas.nativeCanvas.restore()
            }
        }
    }

    override fun dispose() {
        // TODO: Stop animation here. there is no public method for it yet. b/137183289
    }
}

/**
 * The Ripple transition specification.
 */
private object RippleTransition {

    enum class State {
        /** The starting state.  */
        Initial,
        /** User is still touching the surface.  */
        Revealed,
        /** User stopped touching the surface.  */
        Finished
    }

    private val FadeInDuration = 75.milliseconds
    private val RadiusDuration = 225.milliseconds
    private val FadeOutDuration = 150.milliseconds

    val Alpha = FloatPropKey()
    val Radius = PxPropKey()
    val Center = PxPositionPropKey()

    fun definition(
        revealedAlpha: Float,
        startRadius: Px,
        endRadius: Px,
        startCenter: PxPosition,
        endCenter: PxPosition
    ) = transitionDefinition {
        state(State.Initial) {
            this[Alpha] = 0f
            this[Radius] = startRadius
            this[Center] = startCenter
        }
        state(State.Revealed) {
            this[Alpha] = revealedAlpha
            this[Radius] = endRadius
            this[Center] = endCenter
        }
        state(State.Finished) {
            this[Alpha] = 0f
            // the rest are the same as for Revealed
            this[Radius] = endRadius
            this[Center] = endCenter
        }
        transition(State.Initial to State.Revealed) {
            Alpha using tween {
                duration = FadeInDuration.inMilliseconds().toInt()
                easing = LinearEasing
            }
            Radius using tween {
                duration = RadiusDuration.inMilliseconds().toInt()
                easing = FastOutSlowInEasing
            }
            Center using tween {
                duration = RadiusDuration.inMilliseconds().toInt()
                easing = LinearEasing
            }
            // we need to always finish the radius animation before starting fading out
            interruptionHandling = InterruptionHandling.UNINTERRUPTIBLE
        }
        transition(State.Revealed to State.Finished) {
            fun <T> toFinished() = tween<T> {
                duration = FadeOutDuration.inMilliseconds().toInt()
                easing = LinearEasing
            }
            Alpha using toFinished()
            Radius using toFinished()
            Center using toFinished()
        }
    }
}

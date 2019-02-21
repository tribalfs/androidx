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

// TODO("Andrey: Android dependencies are temporary. To be replaced with Crane's animations system)
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.ui.core.Bounds
import androidx.ui.core.Density
import androidx.ui.core.Dimension
import androidx.ui.core.Duration
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.Position
import androidx.ui.core.Size
import androidx.ui.core.center
import androidx.ui.core.div
import androidx.ui.core.dp
import androidx.ui.core.getDistance
import androidx.ui.core.lerp
import androidx.ui.core.max
import androidx.ui.core.plus
import androidx.ui.core.times
import androidx.ui.core.toBounds
import androidx.ui.core.toPx
import androidx.ui.core.toRect
import androidx.ui.core.toSize
import androidx.ui.engine.geometry.Offset
import androidx.ui.engine.geometry.RRect
import androidx.ui.engine.geometry.Rect
import androidx.ui.material.borders.BorderRadius
import androidx.ui.material.borders.BoxShape
import androidx.ui.material.surface.Surface
import androidx.ui.painting.Canvas
import androidx.ui.painting.Color
import androidx.ui.painting.Paint
import androidx.ui.vectormath64.Matrix4
import androidx.ui.vectormath64.getAsTranslation

internal val FadeInDuration = Duration.create(milliseconds = 75)
internal val RadiusDuration = Duration.create(milliseconds = 225)
internal val FadeOutDuration = Duration.create(milliseconds = 150)
internal val FadeOutMinStartDelay = Duration.create(milliseconds = 225)

internal fun getRippleClipCallback(
    containedInkWell: Boolean,
    boundsCallback: ((LayoutCoordinates) -> Bounds)?
): ((LayoutCoordinates) -> Bounds)? {
    if (boundsCallback != null) {
        assert(containedInkWell)
        return boundsCallback
    }
    if (containedInkWell) {
        return { it.size.toBounds() }
    }
    return null
}

internal fun getSurfaceSize(
    coordinates: LayoutCoordinates,
    boundsCallback: ((LayoutCoordinates) -> Bounds)?
) = boundsCallback?.invoke(coordinates)?.toSize() ?: coordinates.size

internal fun getRippleStartRadius(size: Size) =
    max(size.width, size.height) * 0.3f

internal fun getRippleTargetRadius(size: Size) =
    Position(size.width, size.height).getDistance() / 2f + 10.dp

/**
 * Used to specify this type of [RippleEffect] for an [BoundedRipple] and [Ripple].
 */
object DefaultRippleEffectFactory : RippleEffectFactory() {

    override fun create(
        rippleSurface: RippleSurfaceOwner,
        coordinates: LayoutCoordinates,
        touchPosition: Position,
        color: Color,
        shape: BoxShape,
        finalRadius: Dimension?,
        containedInkWell: Boolean,
        boundsCallback: ((LayoutCoordinates) -> Bounds)?,
        clippingBorderRadius: BorderRadius?,
        onRemoved: (() -> Unit)?
    ): RippleEffect {
        return DefaultRippleEffect(
            rippleSurface,
            coordinates,
            touchPosition,
            color,
            finalRadius,
            containedInkWell,
            boundsCallback,
            clippingBorderRadius,
            onRemoved
        )
    }
}

/**
 * A visual reaction on a piece of [RippleSurface] to user input.
 *
 * A circular ripple effect whose origin starts at the input touch point and
 * whose finalRadius expands from 60% of the final finalRadius. The ripple origin
 * animates to the center of its target layout.
 *
 * This object is rarely created directly. Instead of creating a ripple effect,
 * consider using an [Ripple] or [BoundedRipple].
 *
 * See also:
 *
 *  * [Ripple], which draws [RippleEffect]s in the parent [RippleSurface].
 *  * [BoundedRipple], which is a rectangular [Ripple] (the most common type of
 *    ripple).
 *  * [RippleSurface], which is the widget on which the ripple effect is drawn.
 *
 * Begin a ripple, centered at [touchPosition] relative to the target layout.
 *
 * If "bounded" is true, then the ripple will be sized to fit the bounds, then
 * clipped to it when drawn. The bounds are returned by `boundsCallback`, if provided, or
 * otherwise is the bounds of the target layout.
 *
 * If "bounded" is false, then "boundsCallback" should be null.
 * The ripple is clipped only to the edges of the [Surface]. This is the default.
 *
 * When the ripple is removed, [onRemoved] will be called.
 */
internal class DefaultRippleEffect(
    rippleSurface: RippleSurfaceOwner,
    coordinates: LayoutCoordinates,
    private val touchPosition: Position,
    color: Color,
    finalRadius: Dimension? = null,
    containedInkWell: Boolean = false,
    boundsCallback: ((LayoutCoordinates) -> Bounds)? = null,
    clippingBorderRadius: BorderRadius? = null,
    onRemoved: (() -> Unit)? = null
) : RippleEffect(rippleSurface, coordinates, color, onRemoved) {

    private val borderRadius: BorderRadius =
        clippingBorderRadius ?: BorderRadius.Zero
    private val clipCallback: ((LayoutCoordinates) -> Bounds)? =
        getRippleClipCallback(containedInkWell, boundsCallback)
    private val startedTime: Duration =
        Duration.create(milliseconds = System.currentTimeMillis())

    private val radius: ValueAnimator
    private val fadeIn: ValueAnimator
    private val fadeOut: ValueAnimator

    init {
        val surfaceSize = getSurfaceSize(coordinates, boundsCallback)
        val startRadius = getRippleStartRadius(surfaceSize)
        val targetRadius = finalRadius ?: getRippleTargetRadius(surfaceSize)

        val redrawListener = object : ValueAnimator.AnimatorUpdateListener {
            override fun onAnimationUpdate(animation: ValueAnimator?) {
                rippleSurface.markNeedsRedraw()
            }
        }

        // Immediately begin fading-in the initial ripple.
        fadeIn = ValueAnimator.ofInt(0, color.alpha)
        fadeIn.duration = FadeInDuration.inMilliseconds
        fadeIn.addUpdateListener(redrawListener)
        fadeIn.start()

        // Controls the ripple radius and its center.
        radius = ValueAnimator.ofFloat(startRadius.dp, targetRadius.dp)
        radius.duration = RadiusDuration.inMilliseconds
        radius.interpolator = FastOutSlowInInterpolator()
        radius.addUpdateListener(redrawListener)
        radius.start()

        // Controls the fading-out effects. Will be started in finish callback
        fadeOut = ValueAnimator.ofInt(color.alpha, 0)
        fadeOut.duration = FadeOutDuration.inMilliseconds
        fadeOut.addUpdateListener(redrawListener)
        fadeOut.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                dispose()
            }
        })

        rippleSurface.addEffect(this)
    }

    override fun finish(canceled: Boolean) {
        // if we still fading-in we should immediately switch to the final alpha.
        if (fadeIn.isRunning) {
            fadeIn.end()
        }
        val currentTime = Duration.create(milliseconds = System.currentTimeMillis())
        // starting fading-out but not before [FadeOutMinStartDelay] after the ripple start.
        val difference = currentTime - startedTime
        val startDelay = if (difference < FadeOutMinStartDelay)
            FadeOutMinStartDelay - difference else Duration.zero
        fadeOut.startDelay = startDelay.inMilliseconds
        fadeOut.start()
    }

    private fun clipRRectFromRect(rect: Rect): RRect {
        return RRect(
            rect,
            topLeft = borderRadius.topLeft,
            topRight = borderRadius.topRight,
            bottomLeft = borderRadius.bottomLeft,
            bottomRight = borderRadius.bottomRight
        )
    }

    private fun clipCanvasWithRect(canvas: Canvas, rect: Rect, offset: Offset? = null) {
        var clipRect = rect
        if (offset != null) {
            clipRect = clipRect.shift(offset)
        }
        if (borderRadius != BorderRadius.Zero) {
            canvas.clipRRect(clipRRectFromRect(clipRect))
        } else {
            canvas.clipRect(clipRect)
        }
    }

    override fun drawEffect(canvas: Canvas, transform: Matrix4, density: Density) {
        val alpha = (if (fadeOut.isRunning) fadeOut.animatedValue else fadeIn.animatedValue) as Int
        val paint = Paint()
        paint.color = color.withAlpha(alpha)
        // Ripple moves to the center of the parent layout
        val center = lerp(
            touchPosition,
            coordinates.size.center(),
            radius.animatedFraction
        )
        val radius = Dimension(radius.animatedValue as Float)
        val centerOffset = Offset(center.x.toPx(density), center.y.toPx(density))
        val originOffset = transform.getAsTranslation()
        val clipRect = clipCallback?.invoke(coordinates)?.toRect(density)
        if (originOffset == null) {
            canvas.save()
            canvas.transform(transform)
            if (clipRect != null) {
                clipCanvasWithRect(canvas, clipRect)
            }
            canvas.drawCircle(centerOffset, radius.toPx(density), paint)
            canvas.restore()
        } else {
            if (clipRect != null) {
                canvas.save()
                clipCanvasWithRect(canvas, clipRect, offset = originOffset)
            }
            canvas.drawCircle(centerOffset + originOffset, radius.toPx(density), paint)
            if (clipRect != null) {
                canvas.restore()
            }
        }
    }
}

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

package androidx.compose.material.ripple

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.view.View
import androidx.annotation.DoNotInline
import androidx.annotation.RequiresApi
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toAndroidRect
import androidx.compose.ui.graphics.toArgb
import java.lang.reflect.Method

/**
 * Empty [View] that hosts a [RippleDrawable] as its background. This is needed as
 * [RippleDrawable]s cannot currently be drawn directly to a [android.graphics.RenderNode]
 * (b/184760109), so instead we rely on [View]'s internal implementation to draw to the
 * background [android.graphics.RenderNode].
 *
 * A [RippleContainer] is used to manage and assign RippleHostViews when needed - see
 * [RippleContainer.getRippleHostView].
 */
internal class RippleHostView(
    context: Context
) : View(context) {
    /**
     * View related configuration
     */

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(0, 0)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        // noop
    }

    override fun refreshDrawableState() {
        // We don't want the View to manage the drawable state, so avoid updating the ripple's
        // state (via View.mBackground) when we lose window focus, or other events.
    }

    /**
     * A [RippleDrawable] cannot be dynamically changed between bounded / unbounded states - as a
     * result we need to create a new instance when we need to draw a different type.
     * Alternatively we could maintain both a bounded and unbounded instance, but we would still
     * need to reset state for both, and change what the view's background is - so it doesn't
     * help us out that much.
     */
    private var ripple: UnprojectedRipple? = null
    private var bounded: Boolean? = null

    /**
     * Creates a new [UnprojectedRipple] and assigns it to [ripple].
     *
     * @param bounded whether the [UnprojectedRipple] is bounded (fills the bounds of the
     * containing canvas, or unbounded (starts from the center of the canvas and fills outwards
     * in a circle that may go outside the bounds of the canvas).
     */
    private fun createRipple(bounded: Boolean) {
        ripple = UnprojectedRipple(bounded).apply {
            // Set the ripple to be the view's background - this will internally set the ripple's
            // Drawable.Callback callback to equal this view so there is no need to manage this
            // separately.
            background = this
        }
    }

    /**
     * Callback invoked when the underlying [RippleDrawable] requests to be
     * invalidated - this callback should end up triggering a re-draw in the owning ripple instance.
     */
    private var onInvalidateRipple: (() -> Unit)? = null

    /**
     * Pass through any drawable invalidations to the owning ripple instance - the normal
     * [View.invalidate] circuitry won't trigger a re-draw / re-composition inside of Compose out
     * of the box.
     */
    override fun invalidateDrawable(who: Drawable) {
        onInvalidateRipple?.invoke()
    }

    /**
     * Adds and starts drawing a ripple with the provided properties.
     *
     * @param onInvalidateRipple callback invoked when the ripple requests an invalidation
     */
    fun addRipple(
        interaction: PressInteraction.Press,
        bounded: Boolean,
        size: Size,
        radius: Int,
        color: Color,
        alpha: Float,
        onInvalidateRipple: () -> Unit
    ) {
        // Create a new ripple if there is no existing ripple, or bounded has changed.
        // (Since this.bounded is initialized to `null`, technically the first check isn't
        // needed, but it might not survive refactoring).
        if (ripple == null || bounded != this.bounded) {
            createRipple(bounded)
            this.bounded = bounded
        }
        val ripple = ripple!!
        this.onInvalidateRipple = onInvalidateRipple
        updateRippleProperties(size, radius, color, alpha)
        if (bounded) {
            // Bounded ripples should animate from the press position
            ripple.setHotspot(interaction.pressPosition.x, interaction.pressPosition.y)
        } else {
            // Unbounded ripples should animate from the center of the ripple - in the framework
            // this change in spec was never made, so they currently animate from the press
            // position into a circle that starts at the center of the ripple, instead of
            // starting directly at the center.
            ripple.setHotspot(
                ripple.bounds.centerX().toFloat(),
                ripple.bounds.centerY().toFloat()
            )
        }
        ripple.state = PressedState
    }

    /**
     * Removes the most recent ripple, causing it to start the 'end' animation. Note that this is
     * separate from immediately cancelling existing ripples - see [disposeRipple].
     */
    fun removeRipple() {
        ripple?.state = RestingState
    }

    /**
     * Update the underlying [RippleDrawable] with the new properties. Note that changes to
     * [size] or [radius] while a ripple is animating will cause the animation to move to the UI
     * thread, so it is important to also provide the correct values in [addRipple].
     */
    fun updateRippleProperties(
        size: Size,
        radius: Int,
        color: Color,
        alpha: Float
    ) {
        val ripple = ripple ?: return
        // NOTE: if adding new properties here, make sure they are guarded with an equality check
        // (either here or internally in RippleDrawable). Many properties invalidate the ripple when
        // changed, which will lead to a call to updateRippleProperties again, which will cause
        // another invalidation, etc.
        ripple.trySetRadius(radius)
        ripple.setColor(color, alpha)
        val newBounds = size.toRect().toAndroidRect()
        // Drawing the background causes the view to update the bounds of the drawable
        // based on the view's bounds, so we need to adjust the view itself to match the
        // canvas' bounds.
        // These setters will no-op if there is no change, so no need for us to check for equality
        left = newBounds.left
        top = newBounds.top
        right = newBounds.right
        bottom = newBounds.bottom
        ripple.bounds = newBounds
    }

    /**
     * Remove existing callbacks and clear any currently drawing ripples.
     */
    fun disposeRipple() {
        onInvalidateRipple = null
        val ripple = ripple ?: return
        ripple.state = RestingState
        ripple.setVisible(false, false)
        unscheduleDrawable(ripple)
    }

    companion object {
        private val PressedState = intArrayOf(
            android.R.attr.state_pressed,
            android.R.attr.state_enabled
        )
        private val RestingState = intArrayOf()
    }
}

/**
 * [RippleDrawable] that always returns `false` for [isProjected], so that it will always be drawn
 * in the owning [View]'s RenderNode, and not an ancestor node. This is only meaningful if the
 * owning [View]'s drawing is not clipped, which it won't be in Compose, so we can always return
 * `false` and just draw outside of the bounds if we need to.
 */
private class UnprojectedRipple(private val bounded: Boolean) : RippleDrawable(
    // Temporary default color that we will override later
    /* color */ ColorStateList.valueOf(android.graphics.Color.BLACK),
    /* content */null,
    // The color of the mask here doesn't matter - we just need a mask to draw the bounded ripple
    // against
    /* mask */ if (bounded) ColorDrawable(android.graphics.Color.WHITE) else null
) {
    /**
     * Store the ripple color so we can compare it later, as there is no way to get the currently
     * set color on the RippleDrawable itself.
     */
    private var rippleColor: Color? = null

    /**
     * Store the ripple radius so we can compare it later - [getRadius] is only available on M+,
     * and we don't want to use reflection to read this below that.
     */
    private var rippleRadius: Int? = null

    /**
     * Set a new [color] with [alpha] for this [RippleDrawable].
     */
    fun setColor(color: Color, alpha: Float) {
        val newColor = calculateRippleColor(color, alpha)
        if (rippleColor != newColor) {
            rippleColor = newColor
            setColor(ColorStateList.valueOf(newColor.toArgb()))
        }
    }

    private var projected = false

    /**
     * Return false (other than when calculating dirty bounds, see [getDirtyBounds]) to ensure
     * that this [RippleDrawable] will be drawn inside the owning [View]'s RenderNode, and not an
     * ancestor that supports projection.
     */
    override fun isProjected(): Boolean {
        return projected
    }

    /**
     * On older API levels [isProjected] is used to control whether the dirty bounds (and hence
     * how the ripple is clipped) are bounded / unbounded. Since we turn off projection for this
     * ripple, if the ripple is unbounded we temporarily set isProjected to return true, so the
     * super implementation will return us the correct bounds for an unbounded ripple.
     */
    override fun getDirtyBounds(): Rect {
        if (!bounded) {
            projected = true
        }
        val bounds = super.getDirtyBounds()
        projected = false
        return bounds
    }

    /**
     * Compat wrapper for [setRadius] which is only available on [Build.VERSION_CODES.M] and
     * above. This will try to call setMaxRadius below that if possible.
     */
    fun trySetRadius(radius: Int) {
        if (rippleRadius != radius) {
            rippleRadius = radius
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                try {
                    if (!setMaxRadiusFetched) {
                        setMaxRadiusFetched = true
                        setMaxRadiusMethod = RippleDrawable::class.java.getDeclaredMethod(
                            "setMaxRadius",
                            Int::class.javaPrimitiveType
                        )
                    }
                    setMaxRadiusMethod?.invoke(this, radius)
                } catch (e: Exception) {
                    // Fail silently
                }
            } else {
                MRadiusHelper.setRadius(this, radius)
            }
        }
    }

    /**
     * Calculates the resulting [Color] from [color] with [alpha] applied, accounting for
     * differences in [RippleDrawable]'s behavior on different API levels.
     */
    private fun calculateRippleColor(color: Color, alpha: Float): Color {
        // On API 21-27 the ripple animation is split into two sections - an overlay and an
        // animation on top - and 50% of the original alpha is used for both. Since these sections
        // don't always overlap, the actual alpha of the animation in parts can be 50% of the
        // original amount, so to ensure that the contrast is correct, and make the ripple alpha
        // match more closely with the provided value, we double it first.
        // Note that this is also consistent with MDC behavior.
        val transformedAlpha = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            alpha * 2
        } else {
            // Note: above 28 the ripple alpha is clamped to 50%, so this might not be the
            // _actual_ alpha that is used in the ripple.
            alpha
        }
        return color.copy(alpha = transformedAlpha)
    }

    /**
     * Separate class to avoid verification errors for methods introduced in M.
     */
    @RequiresApi(Build.VERSION_CODES.M)
    private object MRadiusHelper {
        /**
         * Sets the [radius] for the given [ripple].
         */
        @DoNotInline
        fun setRadius(ripple: RippleDrawable, radius: Int) {
            ripple.radius = radius
        }
    }

    companion object {
        /**
         * Cache RippleDrawable#setMaxRadius to avoid retrieving it more times than necessary
         */
        private var setMaxRadiusMethod: Method? = null
        private var setMaxRadiusFetched = false
    }
}

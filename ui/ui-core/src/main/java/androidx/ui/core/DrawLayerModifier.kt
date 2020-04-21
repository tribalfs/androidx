/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.ui.core

import androidx.annotation.FloatRange
import androidx.compose.Immutable
import androidx.ui.graphics.Shape
import androidx.ui.util.packFloats
import androidx.ui.util.unpackFloat1
import androidx.ui.util.unpackFloat2

/**
 * Constructs a [TransformOrigin] from the given fractional values from the Layer's
 * width and height
 */
@Suppress("NOTHING_TO_INLINE")
inline fun TransformOrigin(pivotFractionX: Float, pivotFractionY: Float): TransformOrigin =
    TransformOrigin(packFloats(pivotFractionX, pivotFractionY))

/**
 * A two-dimensional position represented as a fraction of the Layer's width and height
 */
@OptIn(ExperimentalUnsignedTypes::class)
@Immutable
inline class TransformOrigin(@PublishedApi internal val value: Long) {

    /**
     * Return the position along the x-axis that should be used as the
     * origin for rotation and scale transformations. This is represented as a fraction
     * of the width of the content. A value of 0.5f represents the midpoint between the left
     * and right bounds of the content
     */
    val pivotFractionX: Float
        get() = unpackFloat1(value)

    /**
     * Return the position along the y-axis that should be used as the
     * origin for rotation and scale transformations. This is represented as a fraction
     * of the height of the content. A value of 0.5f represents the midpoint between the top
     * and bottom bounds of the content
     */
    val pivotFractionY: Float
        get() = unpackFloat2(value)

    companion object {

        /**
         * [TransformOrigin] constant to indicate that the center of the content should
         * be used for rotation and scale transformations
         */
        val Center = TransformOrigin(0.5f, 0.5f)
    }
}

/**
 * A [Modifier.Element] that makes content draw into a layer, allowing easily changing
 * properties of the drawn contents.
 *
 * @sample androidx.ui.core.samples.AnimateFadeIn
 */
interface DrawLayerModifier : Modifier.Element {
    /**
     * The horizontal scale of the drawn area. This would typically default to `1`.
     */
    val scaleX: Float get() = 1f

    /**
     * The vertical scale of the drawn area. This would typically default to `1`.
     */
    val scaleY: Float get() = 1f

    /**
     * The alpha of the drawn area. Setting this to something other than `1`
     * will cause the drawn contents to be translucent and setting it to `0` will
     * cause it to be fully invisible.
     */
    @get:FloatRange(from = 0.0, to = 1.0)
    val alpha: Float get() = 1f

    /**
     * Horizontal pixel offset of the layer relative to its left bound
     */
    val translationX: Float get() = 0f

    /**
     * Vertical pixel offset of the layer relative to its top bound
     */
    val translationY: Float get() = 0f

    /**
     * Sets the elevation for the shadow in pixels. With the [shadowElevation] > 0f and
     * [outlineShape] set, a shadow is produced.
     */
    @get:FloatRange(from = 0.0)
    val shadowElevation: Float get() = 0f

    /**
     * The rotation of the contents around the horizontal axis in degrees.
     */
    @get:FloatRange(from = 0.0, to = 360.0)
    val rotationX: Float get() = 0f

    /**
     * The rotation of the contents around the vertical axis in degrees.
     */
    @get:FloatRange(from = 0.0, to = 360.0)
    val rotationY: Float get() = 0f

    /**
     * The rotation of the contents around the Z axis in degrees.
     */
    @get:FloatRange(from = 0.0, to = 360.0)
    val rotationZ: Float get() = 0f

    /**
     * Offset percentage along the x and y axis for which contents are rotated and scaled.
     * The default value of 0.5f, 0.5f indicates the pivot point will be at the midpoint of the
     * left and right as well as the top and bottom bounds of the layer
     */
    val transformOrigin: TransformOrigin get() = TransformOrigin.Center

    /**
     * The [Shape] of the layer. When [shadowElevation] is non-zero and [outlineShape] is non-null,
     * a shadow is produced. When [clipToOutline] is `true` and [outlineShape] is non-null, the
     * contents will be clipped to the outline.
     */
    val outlineShape: Shape? get() = null

    /**
     * Set to `true` to clip the content to the size of the layer or `false` to allow
     * drawing outside of the layer's bounds. This a convenient way to clip to the bounding
     * rectangle. When [clipToOutline] is `true` the contents are clipped by both the
     * bounding rectangle and the [outlineShape].
     *
     * @see clipToOutline
     */
    val clipToBounds: Boolean get() = false

    /**
     * Clips the content to the [outlineShape]. If [outlineShape] is null, no clipping will occur.
     * When both [clipToBounds] and [clipToOutline] are `true`, the content will be clipped by
     * both the bounding rectangle and the [outlineShape].
     */
    val clipToOutline: Boolean get() = false
}

private data class SimpleDrawLayerModifier(
    override val scaleX: Float,
    override val scaleY: Float,
    override val alpha: Float,
    override val translationX: Float,
    override val translationY: Float,
    override val shadowElevation: Float,
    override val rotationX: Float,
    override val rotationY: Float,
    override val rotationZ: Float,
    override val transformOrigin: TransformOrigin,
    override val outlineShape: Shape?,
    override val clipToBounds: Boolean,
    override val clipToOutline: Boolean
) : DrawLayerModifier

/**
 * Create a [DrawLayerModifier] with fixed properties.
 *
 * @sample androidx.ui.core.samples.ChangeOpacity
 *
 * @param scaleX [DrawLayerModifier.scaleX]
 * @param scaleY [DrawLayerModifier.scaleY]
 * @param alpha [DrawLayerModifier.alpha]
 * @param translationX [DrawLayerModifier.translationX]
 * @param translationY [DrawLayerModifier.translationY]
 * @param elevation [DrawLayerModifier.shadowElevation]
 * @param rotationX [DrawLayerModifier.rotationX]
 * @param rotationY [DrawLayerModifier.rotationY]
 * @param rotationZ [DrawLayerModifier.rotationZ]
 * @param transformOrigin [DrawLayerModifier.transformOrigin]
 * @param outlineShape [DrawLayerModifier.outlineShape]
 * @param clipToBounds [DrawLayerModifier.clipToBounds]
 * @param clipToOutline [DrawLayerModifier.clipToOutline]
 */
@Deprecated(
    "Use Modifier.drawLayer",
    replaceWith = ReplaceWith(
        "Modifier.drawLayer(scaleX, scaleY, alpha, elevation, rotationX, rotationY, rotationZ, " +
                "transformOrigin, outlineShape, clipToBounds, clipToOutline)",
        "androidx.ui.core.Modifier",
        "androidx.ui.core.drawLayer"
    )
)
fun drawLayer(
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    alpha: Float = 1f,
    translationX: Float = 0f,
    translationY: Float = 0f,
    elevation: Float = 0f,
    rotationX: Float = 0f,
    rotationY: Float = 0f,
    rotationZ: Float = 0f,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    outlineShape: Shape? = null,
    clipToBounds: Boolean = false,
    clipToOutline: Boolean = false
): Modifier = SimpleDrawLayerModifier(
    scaleX = scaleX,
    scaleY = scaleY,
    alpha = alpha,
    translationX = translationX,
    translationY = translationY,
    shadowElevation = elevation,
    rotationX = rotationX,
    rotationY = rotationY,
    rotationZ = rotationZ,
    transformOrigin = transformOrigin,
    outlineShape = outlineShape,
    clipToBounds = clipToBounds,
    clipToOutline = clipToOutline
)

/**
 * Draw the content into a layer. This permits applying special effects and transformations:
 *
 * @sample androidx.ui.core.samples.ChangeOpacity
 *
 * @param scaleX [DrawLayerModifier.scaleX]
 * @param scaleY [DrawLayerModifier.scaleY]
 * @param alpha [DrawLayerModifier.alpha]
 * @param shadowElevation [DrawLayerModifier.shadowElevation]
 * @param rotationX [DrawLayerModifier.rotationX]
 * @param rotationY [DrawLayerModifier.rotationY]
 * @param rotationZ [DrawLayerModifier.rotationZ]
 * @param outlineShape [DrawLayerModifier.outlineShape]
 * @param clipToBounds [DrawLayerModifier.clipToBounds]
 * @param clipToOutline [DrawLayerModifier.clipToOutline]
 */
fun Modifier.drawLayer(
    scaleX: Float = 1f,
    scaleY: Float = 1f,
    alpha: Float = 1f,
    translationX: Float = 0f,
    translationY: Float = 0f,
    shadowElevation: Float = 0f,
    rotationX: Float = 0f,
    rotationY: Float = 0f,
    rotationZ: Float = 0f,
    transformOrigin: TransformOrigin = TransformOrigin.Center,
    outlineShape: Shape? = null,
    clipToBounds: Boolean = false,
    clipToOutline: Boolean = false
) = this + SimpleDrawLayerModifier(
    scaleX = scaleX,
    scaleY = scaleY,
    alpha = alpha,
    translationX = translationX,
    translationY = translationY,
    shadowElevation = shadowElevation,
    rotationX = rotationX,
    rotationY = rotationY,
    rotationZ = rotationZ,
    transformOrigin = transformOrigin,
    outlineShape = outlineShape,
    clipToBounds = clipToBounds,
    clipToOutline = clipToOutline
)
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

import android.annotation.TargetApi
import android.graphics.Matrix
import android.graphics.RenderNode
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.RectangleShape
import androidx.ui.unit.IntPxPosition
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.toPxSize

/**
 * RenderNode implementation of OwnedLayer.
 */
@TargetApi(29)
internal class RenderNodeLayer(
    val ownerView: AndroidComposeView,
    val drawLayerModifier: DrawLayerModifier,
    val drawBlock: (Canvas) -> Unit,
    val invalidateParentLayer: () -> Unit
) : OwnedLayer {
    /**
     * True when the RenderNodeLayer has been invalidated and not yet drawn.
     */
    private var isDirty = false
    private val outlineResolver = OutlineResolver(ownerView.density)
    private var isDestroyed = false
    private var cacheMatrix: Matrix? = null
    private var drawnWithZ = false

    /**
     * Local copy of the transform origin as DrawLayerModifier can be implemented
     * as a model object. Update this field within [updateLayerProperties] and use it
     * in [resize] or other methods
     */
    private var transformOrigin: TransformOrigin = TransformOrigin.Center

    private val renderNode = RenderNode(null).apply {
        setHasOverlappingRendering(true)
    }

    override val layerId: Long
        get() = renderNode.uniqueId

    override fun updateLayerProperties() {
        transformOrigin = drawLayerModifier.transformOrigin
        val wasClippingManually = renderNode.clipToOutline && outlineResolver.clipPath != null
        renderNode.scaleX = drawLayerModifier.scaleX
        renderNode.scaleY = drawLayerModifier.scaleY
        renderNode.alpha = drawLayerModifier.alpha
        renderNode.translationX = drawLayerModifier.translationX
        renderNode.translationY = drawLayerModifier.translationY
        renderNode.elevation = drawLayerModifier.shadowElevation
        renderNode.rotationZ = drawLayerModifier.rotationZ
        renderNode.rotationX = drawLayerModifier.rotationX
        renderNode.rotationY = drawLayerModifier.rotationY
        renderNode.pivotX = transformOrigin.pivotFractionX * renderNode.width
        renderNode.pivotY = transformOrigin.pivotFractionY * renderNode.height
        val shape = drawLayerModifier.shape
        val clip = drawLayerModifier.clip
        renderNode.clipToOutline = clip && shape !== RectangleShape
        renderNode.clipToBounds = clip && shape === RectangleShape
        val shapeChanged = outlineResolver.update(
            shape,
            renderNode.alpha,
            renderNode.clipToOutline,
            renderNode.elevation
        )
        renderNode.setOutline(outlineResolver.outline)
        val isClippingManually = renderNode.clipToOutline && outlineResolver.clipPath != null
        if (wasClippingManually != isClippingManually || (isClippingManually && shapeChanged)) {
            invalidate()
        }
        if (!drawnWithZ && renderNode.elevation > 0f) {
            invalidateParentLayer()
        }
    }

    override fun resize(size: IntPxSize) {
        val width = size.width.value
        val height = size.height.value
        renderNode.pivotX = transformOrigin.pivotFractionX * width
        renderNode.pivotY = transformOrigin.pivotFractionY * height
        if (renderNode.setPosition(
            renderNode.left,
            renderNode.top,
            renderNode.left + width,
            renderNode.top + height
        )) {
            outlineResolver.update(size.toPxSize())
            renderNode.setOutline(outlineResolver.outline)
            invalidate()
        }
    }

    override fun move(position: IntPxPosition) {
        renderNode.offsetLeftAndRight(position.x.value - renderNode.left)
        renderNode.offsetTopAndBottom(position.y.value - renderNode.top)
    }

    override fun invalidate() {
        if (!isDirty && !isDestroyed) {
            ownerView.invalidate()
            ownerView.dirtyLayers += this
            isDirty = true
        }
    }

    override fun drawLayer(canvas: Canvas) {
        val androidCanvas = canvas.nativeCanvas
        if (androidCanvas.isHardwareAccelerated) {
            updateDisplayList()
            drawnWithZ = renderNode.elevation > 0f
            if (drawnWithZ) {
                canvas.enableZ()
            }
            androidCanvas.drawRenderNode(renderNode)
            if (drawnWithZ) {
                canvas.disableZ()
            }
        } else {
            drawBlock(canvas)
        }
        isDirty = false
    }

    override fun updateDisplayList() {
        if (isDirty || !renderNode.hasDisplayList()) {
            isDirty = false
            val renderNodeCanvas = renderNode.beginRecording()
            val uiCanvas = Canvas(renderNodeCanvas)

            val clipPath = outlineResolver.clipPath
            val manuallyClip = renderNode.clipToOutline && clipPath != null
            if (manuallyClip) {
                uiCanvas.save()
                uiCanvas.clipPath(clipPath!!)
            }
            ownerView.observeLayerModelReads(this) {
                drawBlock(uiCanvas)
            }
            if (manuallyClip) {
                uiCanvas.restore()
            }
            renderNode.endRecording()
        }
    }

    override fun destroy() {
        isDestroyed = true
        ownerView.dirtyLayers -= this
    }

    override fun getMatrix(): Matrix {
        val matrix = cacheMatrix ?: Matrix().also { cacheMatrix = it }
        renderNode.getMatrix(matrix)
        return matrix
    }
}

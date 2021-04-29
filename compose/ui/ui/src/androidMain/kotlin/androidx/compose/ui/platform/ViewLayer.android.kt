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

package androidx.compose.ui.platform

import android.os.Build
import android.view.View
import android.view.ViewOutlineProvider
import androidx.annotation.RequiresApi
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.CanvasHolder
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.setFrom
import androidx.compose.ui.node.OwnedLayer
import androidx.compose.ui.unit.LayoutDirection
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * View implementation of OwnedLayer.
 */
internal class ViewLayer(
    val ownerView: AndroidComposeView,
    val container: DrawChildContainer,
    val drawBlock: (Canvas) -> Unit,
    val invalidateParentLayer: () -> Unit
) : View(ownerView.context), OwnedLayer {
    private val outlineResolver = OutlineResolver(ownerView.density)
    // Value of the layerModifier's clipToBounds property
    private var clipToBounds = false
    private var clipBoundsCache: android.graphics.Rect? = null
    private val manualClipPath: Path? get() =
        if (!clipToOutline) null else outlineResolver.clipPath
    var isInvalidated = false
        private set
    private var drawnWithZ = false
    private val canvasHolder = CanvasHolder()

    private val matrixCache = ViewLayerMatrixCache()

    /**
     * Local copy of the transform origin as GraphicsLayerModifier can be implemented
     * as a model object. Update this field within [updateLayerProperties] and use it
     * in [resize] or other methods
     */
    private var mTransformOrigin: TransformOrigin = TransformOrigin.Center

    init {
        setWillNotDraw(false) // we WILL draw
        id = generateViewId()
        container.addView(this)
    }

    override val layerId: Long
        get() = id.toLong()

    @ExperimentalComposeUiApi
    override val ownerViewId: Long
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            UniqueDrawingIdApi29.getUniqueDrawingId(ownerView)
        } else {
            -1
        }

    @RequiresApi(29)
    private class UniqueDrawingIdApi29 {
        @RequiresApi(29)
        companion object {
            @JvmStatic
            fun getUniqueDrawingId(view: View) = view.uniqueDrawingId
        }
    }

    /**
     * Configure the camera distance on the View in pixels. View already has a get/setCameraDistance
     * API however, that operates in Dp values.
     */
    var cameraDistancePx: Float
        get() {
            // View internally converts distance to dp so divide by density here to have
            // consistent usage of pixels with RenderNode that is backing the View
            return cameraDistance / resources.displayMetrics.densityDpi
        }
        set(value) {
            // View internally converts distance to dp so multiply by density here to have
            // consistent usage of pixels with RenderNode that is backing the View
            cameraDistance = value * resources.displayMetrics.densityDpi
        }

    override fun updateLayerProperties(
        scaleX: Float,
        scaleY: Float,
        alpha: Float,
        translationX: Float,
        translationY: Float,
        shadowElevation: Float,
        rotationX: Float,
        rotationY: Float,
        rotationZ: Float,
        cameraDistance: Float,
        transformOrigin: TransformOrigin,
        shape: Shape,
        clip: Boolean,
        layoutDirection: LayoutDirection
    ) {
        this.mTransformOrigin = transformOrigin
        this.scaleX = scaleX
        this.scaleY = scaleY
        this.alpha = alpha
        this.translationX = translationX
        this.translationY = translationY
        this.elevation = shadowElevation
        this.rotation = rotationZ
        this.rotationX = rotationX
        this.rotationY = rotationY
        this.pivotX = mTransformOrigin.pivotFractionX * width
        this.pivotY = mTransformOrigin.pivotFractionY * height
        this.cameraDistancePx = cameraDistance
        this.clipToBounds = clip && shape === RectangleShape
        resetClipBounds()
        val wasClippingManually = manualClipPath != null
        this.clipToOutline = clip && shape !== RectangleShape
        val shapeChanged = outlineResolver.update(
            shape,
            this.alpha,
            this.clipToOutline,
            this.elevation,
            layoutDirection
        )
        updateOutlineResolver()
        val isClippingManually = manualClipPath != null
        if (wasClippingManually != isClippingManually || (isClippingManually && shapeChanged)) {
            invalidate() // have to redraw the content
        }
        if (!drawnWithZ && elevation > 0) {
            invalidateParentLayer()
        }
        matrixCache.invalidate()
    }

    private fun updateOutlineResolver() {
        this.outlineProvider = if (outlineResolver.outline != null) {
            OutlineProvider
        } else {
            null
        }
    }

    private fun resetClipBounds() {
        this.clipBounds = if (clipToBounds) {
            if (clipBoundsCache == null) {
                clipBoundsCache = android.graphics.Rect(0, 0, width, height)
            } else {
                clipBoundsCache!!.set(0, 0, width, height)
            }
            clipBoundsCache
        } else {
            null
        }
    }

    override fun resize(size: IntSize) {
        val width = size.width
        val height = size.height
        if (width != this.width || height != this.height) {
            pivotX = mTransformOrigin.pivotFractionX * width
            pivotY = mTransformOrigin.pivotFractionY * height
            outlineResolver.update(Size(width.toFloat(), height.toFloat()))
            updateOutlineResolver()
            layout(left, top, left + width, top + height)
            resetClipBounds()
            matrixCache.invalidate()
        }
    }

    override fun move(position: IntOffset) {
        val left = position.x

        if (left != this.left) {
            offsetLeftAndRight(left - this.left)
            matrixCache.invalidate()
        }
        val top = position.y
        if (top != this.top) {
            offsetTopAndBottom(top - this.top)
            matrixCache.invalidate()
        }
    }

    override fun drawLayer(canvas: Canvas) {
        drawnWithZ = elevation > 0f
        if (drawnWithZ) {
            canvas.enableZ()
        }
        container.drawChild(canvas, this, drawingTime)
        if (drawnWithZ) {
            canvas.disableZ()
        }
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        canvasHolder.drawInto(canvas) {
            val clipPath = manualClipPath
            if (clipPath != null) {
                save()
                clipPath(clipPath)
            }
            drawBlock(this)
            if (clipPath != null) {
                restore()
            }
            isInvalidated = false
        }
    }

    override fun invalidate() {
        if (!isInvalidated) {
            isInvalidated = true
            super.invalidate()
            ownerView.dirtyLayers += this
            ownerView.invalidate()
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
    }

    override fun destroy() {
        container.postOnAnimation {
            container.removeView(this)
        }
        ownerView.dirtyLayers -= this
        ownerView.requestClearInvalidObservations()
    }

    override fun updateDisplayList() {
        if (isInvalidated && !shouldUseDispatchDraw) {
            updateDisplayList(this)
            isInvalidated = false
        }
    }

    override fun forceLayout() {
        // Don't do anything. These Views are treated as RenderNodes, so a forced layout
        // should not do anything. If we keep this, we get more redrawing than is necessary.
    }

    override fun mapOffset(point: Offset, inverse: Boolean): Offset {
        return if (inverse) {
            matrixCache.getInverseMatrix(this).map(point)
        } else {
            matrixCache.getMatrix(this).map(point)
        }
    }

    override fun mapBounds(rect: MutableRect, inverse: Boolean) {
        if (inverse) {
            matrixCache.getInverseMatrix(this).map(rect)
        } else {
            matrixCache.getMatrix(this).map(rect)
        }
    }

    companion object {
        val OutlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                view as ViewLayer
                outline.set(view.outlineResolver.outline!!)
            }
        }
        private var updateDisplayListIfDirtyMethod: Method? = null
        private var recreateDisplayList: Field? = null
        var hasRetrievedMethod = false
            private set

        var shouldUseDispatchDraw = false
            internal set // internal so that tests can use it.

        fun updateDisplayList(view: View) {
            try {
                if (!hasRetrievedMethod) {
                    hasRetrievedMethod = true
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                        updateDisplayListIfDirtyMethod =
                            View::class.java.getDeclaredMethod("updateDisplayListIfDirty")
                        recreateDisplayList =
                            View::class.java.getDeclaredField("mRecreateDisplayList")
                    } else {
                        val getDeclaredMethod = Class::class.java.getDeclaredMethod(
                            "getDeclaredMethod",
                            String::class.java,
                            arrayOf<Class<*>>()::class.java
                        )
                        updateDisplayListIfDirtyMethod = getDeclaredMethod.invoke(
                            View::class.java,
                            "updateDisplayListIfDirty", emptyArray<Class<*>>()
                        ) as Method?
                        val getDeclaredField = Class::class.java.getDeclaredMethod(
                            "getDeclaredField",
                            String::class.java
                        )
                        recreateDisplayList = getDeclaredField.invoke(
                            View::class.java,
                            "mRecreateDisplayList"
                        ) as Field?
                    }
                    updateDisplayListIfDirtyMethod?.isAccessible = true
                    recreateDisplayList?.isAccessible = true
                }
                recreateDisplayList?.setBoolean(view, true)
                updateDisplayListIfDirtyMethod?.invoke(view)
            } catch (_: Throwable) {
                shouldUseDispatchDraw = true
            }
        }
    }
}

/**
 * Helper class to cache a [Matrix] and inverse [Matrix], allowing the instance to be reused until
 * the [RenderNodeLayer]'s properties have changed, causing it to call [invalidate].
 *
 * This allows us to avoid repeated calls to [android.graphics.Matrix.getValues], which calls
 * an expensive native method (nGetValues). If we know the matrix hasn't changed, we can just
 * re-use it without needing to read and update values.
 */
private class ViewLayerMatrixCache {
    private var androidMatrixCache: android.graphics.Matrix? = null
    private var inverseAndroidMatrixCache: android.graphics.Matrix? = null
    private var matrixCache: Matrix? = null
    private var inverseMatrixCache: Matrix? = null

    private var isDirty = true
    private var isInverseDirty = true

    /**
     * Ensures that the internal matrix will be updated next time [getMatrix] or [getInverseMatrix]
     * is called - this should be called when something that will change the matrix calculation
     * has happened.
     */
    fun invalidate() {
        isDirty = true
        isInverseDirty = true
    }

    /**
     * Returns the cached [Matrix], updating it if required (if [invalidate] was previously called).
     */
    fun getMatrix(view: View): Matrix {
        val matrix = matrixCache ?: Matrix().also {
            matrixCache = it
        }
        if (!isDirty) {
            return matrix
        }

        val new = view.matrix

        if (androidMatrixCache != new) {
            // Update the Compose matrix if the underlying Android matrix has changed
            matrix.setFrom(new)
            if (androidMatrixCache == null) {
                androidMatrixCache = android.graphics.Matrix(new)
            } else {
                androidMatrixCache!!.set(new)
            }
        }
        isDirty = false
        return matrix
    }

    /**
     * Returns the cached inverse [Matrix], updating it if required (if [invalidate] was previously
     * called).
     */
    fun getInverseMatrix(view: View): Matrix {
        val matrix = inverseMatrixCache ?: Matrix().also {
            inverseMatrixCache = it
        }
        if (!isInverseDirty) {
            return matrix
        }

        val new = view.matrix

        if (inverseAndroidMatrixCache != new) {
            // Update the Compose matrix if the underlying Android matrix has changed
            matrix.setFrom(new)
            matrix.invert()
            if (inverseAndroidMatrixCache == null) {
                inverseAndroidMatrixCache = android.graphics.Matrix(new)
            } else {
                inverseAndroidMatrixCache!!.set(new)
            }
        }
        isInverseDirty = false
        return matrix
    }
}

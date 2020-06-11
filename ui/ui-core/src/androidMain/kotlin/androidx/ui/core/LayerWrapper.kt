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

import android.graphics.Matrix
import android.graphics.RectF
import androidx.ui.core.pointerinput.PointerInputFilter
import androidx.ui.geometry.Rect
import androidx.ui.geometry.Offset
import androidx.ui.graphics.Canvas
import androidx.ui.unit.IntOffset

internal class LayerWrapper(
    wrapped: LayoutNodeWrapper,
    modifier: DrawLayerModifier
) : DelegatingLayoutNodeWrapper<DrawLayerModifier>(wrapped, modifier) {
    private var _layer: OwnedLayer? = null
    private var layerDestroyed = false

    // Do not invalidate itself on position change.
    override val invalidateLayerOnBoundsChange get() = false

    private val invalidateParentLayer: () -> Unit = {
        wrappedBy?.findLayer()?.invalidate()
    }

    val layer: OwnedLayer
        get() {
            return _layer ?: layoutNode.requireOwner().createLayer(
                modifier,
                wrapped::draw,
                invalidateParentLayer
            ).also {
                _layer = it
                invalidateParentLayer()
            }
        }

    // TODO(mount): This cache isn't thread safe at all.
    private var positionCache: FloatArray? = null
    // TODO (njawad): This cache matrix is not thread safe
    private var inverseMatrixCache: Matrix? = null

    override fun performMeasure(
        constraints: Constraints,
        layoutDirection: LayoutDirection
    ): Placeable {
        val placeable = super.performMeasure(constraints, layoutDirection)
        layer.resize(measuredSize)
        return placeable
    }

    override fun place(position: IntOffset) {
        super.place(position)
        layer.move(position)
    }

    override fun draw(canvas: Canvas) {
        layer.drawLayer(canvas)
    }

    override fun detach() {
        super.detach()
        _layer?.destroy()
    }

    override fun findLayer(): OwnedLayer? {
        return layer
    }

    override fun fromParentPosition(position: Offset): Offset {
        val matrix = layer.getMatrix()
        val targetPosition =
            if (!matrix.isIdentity) {
                val inverse = inverseMatrixCache ?: Matrix().also { inverseMatrixCache = it }
                matrix.invert(inverse)
                mapPointsFromMatrix(inverse, position)
            } else {
                position
            }
        return super.fromParentPosition(targetPosition)
    }

    override fun toParentPosition(position: Offset): Offset {
        val matrix = layer.getMatrix()
        val targetPosition =
            if (!matrix.isIdentity) {
                mapPointsFromMatrix(matrix, position)
            } else {
                position
            }
        return super.toParentPosition(targetPosition)
    }

    /**
     * Return a transformed [Offset] based off of the provided matrix transformation
     * and untransformed position.
     */
    private fun mapPointsFromMatrix(matrix: Matrix, position: Offset): Offset {
        val x = position.x
        val y = position.y
        val cache = positionCache
        val point = if (cache != null) {
            cache[0] = x
            cache[1] = y
            cache
        } else {
            floatArrayOf(x, y).also { positionCache = it }
        }
        matrix.mapPoints(point)
        return Offset(point[0], point[1])
    }

    override fun rectInParent(bounds: RectF) {
        if (modifier.clip &&
            !bounds.intersect(0f, 0f, size.width.toFloat(), size.height.toFloat())
        ) {
            bounds.setEmpty()
        }
        val matrix = layer.getMatrix()
        matrix.mapRect(bounds)
        return super.rectInParent(bounds)
    }

    override fun hitTest(
        pointerPositionRelativeToScreen: Offset,
        hitPointerInputFilters: MutableList<PointerInputFilter>
    ) {
        if (modifier.clip) {
            val l = globalPosition.x
            val t = globalPosition.y
            val r = l + width
            val b = t + height

            val localBoundsRelativeToScreen = Rect(l, t, r, b)
            if (!localBoundsRelativeToScreen.contains(pointerPositionRelativeToScreen)) {
                // If we should clip pointer input hit testing to our bounds, and the pointer is
                // not in our bounds, then return false now.
                return
            }
        }

        // If we are here, either we aren't clipping to bounds or we are and the pointer was in
        // bounds.
        super.hitTest(
            pointerPositionRelativeToScreen,
            hitPointerInputFilters
        )
    }
}

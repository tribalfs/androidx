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

package androidx.ui.graphics.vector

import androidx.ui.core.LayoutDirection
import androidx.ui.geometry.Offset
import androidx.ui.geometry.Size
import androidx.ui.graphics.BlendMode
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.ColorFilter
import androidx.ui.graphics.ImageAsset
import androidx.ui.graphics.drawscope.DrawScope
import androidx.ui.unit.Density
import androidx.ui.unit.IntSize
import androidx.ui.unit.toSize

/**
 * Creates a drawing environment that directs its drawing commands to an [ImageAsset]
 * which can be drawn directly in another [DrawScope] instance. This is useful to cache
 * complicated drawing commands across frames especially if the content has not changed.
 * Additionally some drawing operations such as rendering paths are done purely in
 * software so it is beneficial to cache the result and render the contents
 * directly through a texture as done by [DrawScope.drawImage]
 */
internal class DrawCache {

    @PublishedApi internal lateinit var cachedImage: ImageAsset
    private lateinit var cachedCanvas: Canvas
    private lateinit var scopeDensity: Density
    private lateinit var layoutDirection: LayoutDirection

    private val cacheScope = CacheDrawScope()

    /**
     * Draw the contents of the lambda with receiver scope into an [ImageAsset] with the provided
     * size. If the same size is provided across calls, the same [ImageAsset] instance is
     * re-used and the contents are cleared out before drawing content in it again
     */
    fun drawCachedImage(
        size: IntSize,
        density: Density,
        layoutDirection: LayoutDirection,
        block: DrawScope.() -> Unit
    ) {
        this.scopeDensity = density
        this.layoutDirection = layoutDirection
        val isInitialized = ::cachedImage.isInitialized
        if (!isInitialized ||
            size.width > cachedImage.width ||
            size.height > cachedImage.height
        ) {
            cachedImage = ImageAsset(size.width, size.height)
            cachedCanvas = Canvas(cachedImage)
        }
        cacheScope.drawInto(cachedCanvas, size.toSize()) {
            cacheScope.clear()
            block()
        }
        cachedImage.prepareToDraw()
    }

    /**
     * Draw the cached content into the provided [DrawScope] instance
     */
    fun drawInto(
        target: DrawScope,
        alpha: Float = 1.0f,
        colorFilter: ColorFilter? = null
    ) {
        check(::cachedImage.isInitialized) {
            "drawCachedImage must be invoked first before attempting to draw the result " +
                    "into another destination"
        }
        target.drawImage(cachedImage, Offset.Zero, alpha = alpha, colorFilter = colorFilter)
    }

    /**
     * Inner class to avoid exposing DrawScope drawing commands on the DrawCache directly
     */
    private inner class CacheDrawScope : DrawScope() {

        fun drawInto(
            canvas: Canvas,
            size: Size,
            block: DrawScope.() -> Unit
        ) = draw(canvas, size, block)

        override val layoutDirection: LayoutDirection
            get() = this@DrawCache.layoutDirection

        override val density: Float
            get() = this@DrawCache.scopeDensity.density

        override val fontScale: Float
            get() = this@DrawCache.scopeDensity.fontScale

        /**
         * Helper method to clear contents of the draw environment from the given bounds of the
         * DrawScope
         */
        fun clear() {
            drawRect(color = Color.Black, blendMode = BlendMode.clear)
        }
    }
}
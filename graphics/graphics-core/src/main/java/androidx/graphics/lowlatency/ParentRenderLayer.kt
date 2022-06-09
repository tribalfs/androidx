/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.graphics.lowlatency

import android.view.SurfaceControl
import androidx.graphics.opengl.GLRenderer

/**
 * Interface used to define a parent for rendering dry and wet layers.
 * This provides the following facilities:
 *
 * 1) Specifying a parent [SurfaceControl] for a front buffered wet layer
 * 2) Creating a [GLRenderer.RenderTarget] for rendering double buffered dry layer
 * 3) Providing callbacks for consumers to know when to recreate dependencies based on
 * the size/state of the parent, as well as allowing consumers to provide parameters
 * to implementations of dry/double buffered layers
 */
internal interface ParentRenderLayer {
    /**
     * Modify the provided [SurfaceControl.Transaction] to reparent the provided
     * child [SurfaceControl] to a [SurfaceControl] provided by the parent rendering layer
     */
    fun buildReparentTransaction(
        child: SurfaceControl,
        transaction: SurfaceControl.Transaction,
    )

    /**
     * Create a [GLRenderer.RenderTarget] instance for the parent rendering layer given
     * a [GLRenderer] and corresponding [GLRenderer.RenderCallback]
     */
    fun createRenderTarget(
        renderer: GLRenderer,
        renderLayerCallback: GLWetDryRenderer.Callback
    ): GLRenderer.RenderTarget

    /**
     * Configure the callbacks on this [ParentRenderLayer] instance
     * @param callback [Callback] specified on [ParentRenderLayer]. This can be null to remove
     * the previously set [Callback]
     */
    fun setParentLayerCallbacks(callback: Callback?)

    /**
     * Clear the contents of the parent buffer.
     * This triggers a call to [GLWetDryRenderer.Callback.onDryLayerRenderComplete] to update the
     * buffer shown for the dry layer as well as hides the wet layer.
     */
    fun clear()

    /**
     * Release all resources associated with this [ParentRenderLayer] instance
     */
    fun release(transaction: SurfaceControl.Transaction)

    /**
     * Callbacks to be implemented by the consumer of [ParentRenderLayer] to be alerted
     * of size changes or if the [ParentRenderLayer] is destroyed as well as providing a mechanism
     * to expose parameters for rendering wet/dry layers
     */
    interface Callback {
        /**
         * Callback invoked whenever the size of the [ParentRenderLayer] changes.
         * Consumers can leverage this to initialize appropriate buffer sizes and [SurfaceControl]
         * instances
         */
        fun onSizeChanged(width: Int, height: Int)

        /**
         * Callback invoked when the [ParentRenderLayer] is destroyed. This can be in response
         * to the corresponding View backing the [ParentRenderLayer] is being detached/removed
         * from the View hierarchy
         */
        fun onLayerDestroyed()

        /**
         * Callback invoked by the [ParentRenderLayer] to query the next parameters to be used
         * for rendering wet buffer content
         */
        fun pollWetLayerParams(): Any?

        /**
         * Callback invoked by the [ParentRenderLayer] to query the parameters since the last
         * render to the dry layer. This includes all parameters to each request to render content
         * to the wet layer since the last time the dry layer was re-rendered.
         * This is useful for recreating the entire scene when wet layer contents are to be "dried"
         */
        fun obtainDryLayerParams(): MutableCollection<Any?>

        /**
         * Obtain a handle to the wet layer [SurfaceControl] to be used in transactions to
         * atomically update dry layer content as well as hiding the visibility of the wet layer
         */
        fun getWetLayerSurfaceControl(): SurfaceControl?

        /**
         * Obtain a handle to the [RenderBufferPool] to get [RenderBuffer] instances for
         * rendering to wet and dry layers
         */
        fun getRenderBufferPool(): RenderBufferPool?
    }
}
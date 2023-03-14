/*
 * Copyright 2023 The Android Open Source Project
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

import android.graphics.Canvas
import android.hardware.HardwareBuffer
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.WorkerThread
import androidx.hardware.SyncFenceCompat
import java.util.concurrent.Executor

/**
 * Interface to provide an abstraction around implementations for a low latency hardware
 * accelerated [Canvas] that provides a [HardwareBuffer] with the [Canvas] rendered scene
 */
internal interface SingleBufferedCanvasRenderer<T> {

    interface RenderCallbacks<T> {
        @WorkerThread
        fun render(canvas: Canvas, width: Int, height: Int, param: T)

        @WorkerThread
        fun onBufferReady(hardwareBuffer: HardwareBuffer, syncFenceCompat: SyncFenceCompat?)
    }

    /**
     * Render into the [HardwareBuffer] with the given parameter and bounds
     */
    fun render(param: T)

    /**
     * Flag to indicate whether or not the contents of the [SingleBufferedCanvasRenderer] are visible.
     * This is used to help internal state to determine appropriate synchronization
     */
    var isVisible: Boolean

    /**
     * Releases resources associated with [SingleBufferedCanvasRenderer] instance. Attempts to
     * use this object after it is closed will be ignored
     */
    fun release(cancelPending: Boolean, onReleaseComplete: (() -> Unit)? = null)

    /**
     * Clear the contents of the [HardwareBuffer]
     */
    fun clear()

    /**
     * Cancel all pending render requests
     */
    fun cancelPending()

    companion object {

        @RequiresApi(Build.VERSION_CODES.Q)
        fun <T> create(
            width: Int,
            height: Int,
            bufferTransformer: BufferTransformer,
            executor: Executor,
            bufferReadyListener: RenderCallbacks<T>
        ): SingleBufferedCanvasRenderer<T> {
            // TODO return different instance for corresponding platform version
            return SingleBufferedCanvasRendererV29(
                width,
                height,
                bufferTransformer,
                executor,
                bufferReadyListener
            )
        }
    }
}
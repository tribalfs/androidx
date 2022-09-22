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

package androidx.camera.camera2.pipe.integration.interop

import android.hardware.camera2.CameraCharacteristics
import androidx.annotation.RequiresApi
import androidx.camera.core.CameraInfo

/**
 * An interface for retrieving Camera2-related camera information.
 */
@ExperimentalCamera2Interop
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
class Camera2CameraInfo private constructor() {

    /**
     * Gets a camera characteristic value.
     *
     * The characteristic value is the same as the value in the [CameraCharacteristics]
     * that would be obtained from
     * [android.hardware.camera2.CameraManager.getCameraCharacteristics].
     *
     * @param <T> The type of the characteristic value.
     * @param key The [CameraCharacteristics.Key] of the characteristic.
     * @return the value of the characteristic.
    </T> */
    fun <T> getCameraCharacteristic(
        @Suppress("UNUSED_PARAMETER") key: CameraCharacteristics.Key<T>
    ): T? {
        TODO()
    }

    /**
     * Gets the string camera ID.
     *
     *
     * The camera ID is the same as the camera ID that would be obtained from
     * [android.hardware.camera2.CameraManager.getCameraIdList]. The ID that is retrieved
     * is not static and can change depending on the current internal configuration of the
     * [androidx.camera.core.Camera] from which the CameraInfo was retrieved.
     *
     * The Camera is a logical camera which can be backed by multiple
     * [android.hardware.camera2.CameraDevice]. However, only one CameraDevice is active at
     * one time. When the CameraDevice changes then the camera id will change.
     *
     * @return the camera ID.
     * @throws IllegalStateException if the camera info does not contain the camera 2 camera ID
     * (e.g., if CameraX was not initialized with a
     * [androidx.camera.camera2.Camera2Config]).
     */

    fun getCameraId(): String {
        TODO()
    }

    /**
     * @hide
     */
    companion object {

        /**
         * Gets the [Camera2CameraInfo] from a [CameraInfo].
         *
         * @param cameraInfo The [CameraInfo] to get from.
         * @return The camera information with Camera2 implementation.
         * @throws IllegalArgumentException if the camera info does not contain the camera2 information
         * (e.g., if CameraX was not initialized with a
         * [androidx.camera.camera2.Camera2Config]).
         */
        @JvmStatic
        fun from(@Suppress("UNUSED_PARAMETER") cameraInfo: CameraInfo): Camera2CameraInfo {
            TODO()
        }
    }
}
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
package androidx.camera.camera2.pipe.integration.compat.quirk

import android.annotation.SuppressLint
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.CameraMetadata
import androidx.camera.core.impl.Quirk

/**
 * A quirk to denote the surface can only be used to configure to only one
 * [CameraCaptureSession], the next [CameraCaptureSession] may need to use
 * another one.
 *
 * QuirkSummary
 * - Bug Id: 129520942, 135050586
 * - Description: Reusing a surface to create different [CameraCaptureSession] causes
 *   crash on LEGACY devices.
 * - Device(s): Devices in LEGACY camera hardware level.
 *
 * TODO(b/270421716): enable CameraXQuirksClassDetector lint check when kotlin is supported.
 */
@SuppressLint("CameraXQuirksClassDetector")
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
class ConfigureSurfaceToSecondarySessionFailQuirk : Quirk {

    companion object {
        fun isEnabled(cameraMetadata: CameraMetadata): Boolean {
            val level = cameraMetadata[CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL]
            return level == INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY
        }
    }
}
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

@file:RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java

package androidx.camera.camera2.pipe.integration.compat.quirk

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.camera.core.impl.Quirk

/**
 * Quirk needed on devices where not closing capture session before creating a new capture session
 * can lead to undesirable behaviors:
 * - CameraDevice.close() call might stall indefinitely
 * - Crashes in the camera HAL
 *
 * QuirkSummary
 * - Bug Id:      277675483, 282871038
 * - Description: Instructs CameraPipe to close the capture session before creating a new one to
 *                avoid undesirable behaviors
 *
 * TODO(b/270421716): enable CameraXQuirksClassDetector lint check when kotlin is supported.
 */
@SuppressLint("CameraXQuirksClassDetector")
class CloseCaptureSessionOnDisconnectQuirk : Quirk {

    companion object {

        @JvmStatic
        fun isEnabled(): Boolean {
            if (CameraQuirks.isImmediateSurfaceReleaseAllowed()) {
                // If we can release Surfaces immediately, we'll finalize the session when the
                // camera graph is closed (through FinalizeSessionOnCloseQuirk), and thus we won't
                // need to explicitly close the capture session.
                return false
            }
            return if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.O_MR1) {
                // TODO(b/277675483): Older devices (Android version <= 8.1.0) seem to have a higher
                //  chance of encountering an issue where not closing the capture session would lead
                //  to CameraDevice.close() stalling indefinitely. This version check might need to
                //  be further fine-turned down the line.
                true
            } else {
                // TODO(b/282871038): On some platforms, not closing the capture session before
                //  switching to a new capture session may trigger camera HAL crashes. Add more
                //  hardware platforms here when they're identified.
                Build.HARDWARE == "samsungexynos7870"
            }
        }
    }
}
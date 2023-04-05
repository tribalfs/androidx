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

package androidx.camera.integration.view

import androidx.camera.core.CameraEffect
import androidx.camera.core.impl.utils.executor.CameraXExecutors.mainThreadExecutor

/**
 * A tone mapping effect for Preview/VideoCapture UseCase.
 */
internal class ToneMappingSurfaceEffect : CameraEffect(
    PREVIEW or VIDEO_CAPTURE, mainThreadExecutor(), ToneMappingSurfaceProcessor(), {}
) {

    fun release() {
        (surfaceProcessor as? ToneMappingSurfaceProcessor)?.release()
    }
}
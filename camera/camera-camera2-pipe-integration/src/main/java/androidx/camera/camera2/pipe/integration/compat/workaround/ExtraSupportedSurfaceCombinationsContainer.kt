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

package androidx.camera.camera2.pipe.integration.compat.workaround

import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.integration.compat.quirk.DeviceQuirks
import androidx.camera.camera2.pipe.integration.compat.quirk.ExtraSupportedSurfaceCombinationsQuirk
import androidx.camera.core.impl.SurfaceCombination

@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
class ExtraSupportedSurfaceCombinationsContainer {
    private val quirk: ExtraSupportedSurfaceCombinationsQuirk? =
        DeviceQuirks[ExtraSupportedSurfaceCombinationsQuirk::class.java]

    /**
     * Retrieves the extra surface combinations which can be supported on the device.
     */
    operator fun get(cameraId: String, hardwareLevel: Int): List<SurfaceCombination> {
        return quirk?.getExtraSupportedSurfaceCombinations(cameraId, hardwareLevel) ?: listOf()
    }
}

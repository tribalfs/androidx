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

package androidx.camera.camera2.pipe.integration.compat.workaround

import android.graphics.PointF
import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.integration.compat.quirk.AfRegionFlipHorizontallyQuirk
import androidx.camera.camera2.pipe.integration.compat.quirk.CameraQuirks
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.MeteringPoint
import dagger.Module
import dagger.Provides

interface MeteringRegionCorrection {
    fun getCorrectedPoint(
        meteringPoint: MeteringPoint,
        @FocusMeteringAction.MeteringMode meteringMode: Int,
    ): PointF

    @Module
    abstract class Bindings {
        companion object {
            @Provides
            fun provideMeteringRegionCorrection(
                cameraQuirks: CameraQuirks
            ): MeteringRegionCorrection {
                return if (cameraQuirks.quirks.contains(AfRegionFlipHorizontallyQuirk::class.java))
                    MeteringRegionQuirkCorrection
                else NoOpMeteringRegionCorrection
            }
        }
    }
}

object MeteringRegionQuirkCorrection : MeteringRegionCorrection {
    /**
     * Return corrected normalized point by given MeteringPoint, MeteringMode and Quirks.
     */
    override fun getCorrectedPoint(
        meteringPoint: MeteringPoint,
        @FocusMeteringAction.MeteringMode meteringMode: Int,
    ) = when (meteringMode) {
        FocusMeteringAction.FLAG_AF -> PointF(1f - meteringPoint.x, meteringPoint.y)
        else -> PointF(meteringPoint.x, meteringPoint.y)
    }
}

object NoOpMeteringRegionCorrection : MeteringRegionCorrection {
    override fun getCorrectedPoint(meteringPoint: MeteringPoint, meteringMode: Int) =
        PointF(meteringPoint.x, meteringPoint.y)
}

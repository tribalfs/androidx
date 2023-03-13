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
package androidx.camera.camera2.pipe.integration.adapter

import android.content.Context
import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.CameraId
import androidx.camera.camera2.pipe.core.Debug
import androidx.camera.camera2.pipe.core.Log.debug
import androidx.camera.camera2.pipe.core.SystemTimeSource
import androidx.camera.camera2.pipe.core.Timestamps
import androidx.camera.camera2.pipe.core.Timestamps.formatMs
import androidx.camera.camera2.pipe.core.Timestamps.measureNow
import androidx.camera.camera2.pipe.integration.config.CameraAppComponent
import androidx.camera.camera2.pipe.integration.config.CameraAppConfig
import androidx.camera.camera2.pipe.integration.config.CameraConfig
import androidx.camera.camera2.pipe.integration.config.DaggerCameraAppComponent
import androidx.camera.camera2.pipe.integration.internal.CameraCompatibilityFilter
import androidx.camera.camera2.pipe.integration.internal.CameraSelectionOptimizer
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.concurrent.CameraCoordinator
import androidx.camera.core.concurrent.CameraCoordinator.ConcurrentCameraModeListener
import androidx.camera.core.impl.CameraFactory
import androidx.camera.core.impl.CameraInternal
import androidx.camera.core.impl.CameraThreadConfig

/**
 * The [CameraFactoryAdapter] is responsible for creating the root dagger component that is used
 * to share resources across Camera instances.
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
class CameraFactoryAdapter(
    context: Context,
    threadConfig: CameraThreadConfig,
    availableCamerasSelector: CameraSelector?
) : CameraFactory {
    private val appComponent: CameraAppComponent by lazy {
        Debug.traceStart { "CameraFactoryAdapter#appComponent" }
        val timeSource = SystemTimeSource()
        val start = Timestamps.now(timeSource)
        val result = DaggerCameraAppComponent.builder()
            .config(CameraAppConfig(context, threadConfig))
            .build()
        debug { "Created CameraFactoryAdapter in ${start.measureNow(timeSource).formatMs()}" }
        debug { "availableCamerasSelector: $availableCamerasSelector " }
        Debug.traceStop()
        result
    }

    private var mAvailableCamerasSelector: CameraSelector? = availableCamerasSelector
    private var mAvailableCameraIds: List<String>

    init {
        debug { "Created CameraFactoryAdapter" }

        val optimizedCameraIds = CameraSelectionOptimizer.getSelectedAvailableCameraIds(
            this,
            mAvailableCamerasSelector
        )
        mAvailableCameraIds = CameraCompatibilityFilter.getBackwardCompatibleCameraIds(
            appComponent.getCameraDevices(),
            optimizedCameraIds
        )
    }

    /**
     * The [getCamera] method is responsible for providing CameraInternal object based on cameraID.
     * Use cameraId from set of cameraIds provided by [getAvailableCameraIds] method.
     */
    override fun getCamera(cameraId: String): CameraInternal =
        appComponent.cameraBuilder()
            .config(CameraConfig(CameraId(cameraId)))
            .build()
            .getCameraInternal()

    override fun getAvailableCameraIds(): Set<String> =
        // Use a LinkedHashSet to preserve order
        LinkedHashSet(mAvailableCameraIds)

    override fun getCameraCoordinator(): CameraCoordinator {
        // TODO(b/262772650): camera-pipe support for concurrent camera.
        return object : CameraCoordinator {
            override fun getConcurrentCameraSelectors(): MutableList<MutableList<CameraSelector>> {
                return mutableListOf()
            }

            override fun getActiveConcurrentCameraInfos(): MutableList<CameraInfo> {
                return mutableListOf()
            }

            override fun setActiveConcurrentCameraInfos(cameraInfos: MutableList<CameraInfo>) {
            }

            override fun getPairedConcurrentCameraId(cameraId: String): String? {
                return null
            }

            override fun getCameraOperatingMode(): Int {
                return CameraCoordinator.CAMERA_OPERATING_MODE_UNSPECIFIED
            }

            override fun setCameraOperatingMode(cameraOperatingMode: Int) {
            }

            override fun addListener(listener: ConcurrentCameraModeListener) {
            }

            override fun removeListener(listener: ConcurrentCameraModeListener) {
            }
        }
    }

    override fun getCameraManager(): Any? = appComponent
}
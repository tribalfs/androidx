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

import android.annotation.SuppressLint
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import androidx.camera.camera2.pipe.CameraPipe
import androidx.camera.camera2.pipe.core.Log.warn
import androidx.camera.camera2.pipe.integration.config.CameraScope
import androidx.camera.camera2.pipe.integration.impl.CameraProperties
import androidx.camera.camera2.pipe.integration.impl.EvCompControl
import androidx.camera.camera2.pipe.integration.impl.UseCaseCamera
import androidx.camera.camera2.pipe.integration.impl.UseCaseManager
import androidx.camera.camera2.pipe.integration.impl.ZoomControl
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.FocusMeteringResult
import androidx.camera.core.ImageCapture
import androidx.camera.core.TorchState
import androidx.camera.core.impl.CameraCaptureResult
import androidx.camera.core.impl.CameraControlInternal
import androidx.camera.core.impl.CaptureConfig
import androidx.camera.core.impl.Config
import androidx.camera.core.impl.MutableOptionsBundle
import androidx.camera.core.impl.utils.futures.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Adapt the [CameraControlInternal] interface to [CameraPipe].
 *
 * This controller class maintains state as use-cases are attached / detached from the camera as
 * well as providing access to other utility methods. The primary purpose of this class it to
 * forward these interactions to the currently configured [UseCaseCamera].
 */
@SuppressLint("UnsafeExperimentalUsageError")
@CameraScope
@OptIn(ExperimentalCoroutinesApi::class)
class CameraControlAdapter @Inject constructor(
    private val cameraProperties: CameraProperties,
    private val cameraScope: CoroutineScope,
    private val useCaseManager: UseCaseManager,
    private val cameraStateAdapter: CameraStateAdapter,
    private val zoomControl: ZoomControl,
    private val evCompControl: EvCompControl
) : CameraControlInternal {
    private var interopConfig: Config = MutableOptionsBundle.create()
    private var imageCaptureFlashMode: Int = ImageCapture.FLASH_MODE_OFF

    override fun getSensorRect(): Rect {
        return cameraProperties.metadata[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE]!!
    }

    override fun addInteropConfig(config: Config) {
        interopConfig = Config.mergeConfigs(config, interopConfig)
    }

    override fun clearInteropConfig() {
        interopConfig = MutableOptionsBundle.create()
    }

    override fun getInteropConfig(): Config {
        return interopConfig
    }

    override fun enableTorch(torch: Boolean): ListenableFuture<Void> {
        // Launch UNDISPATCHED to preserve interaction order with the camera.
        return cameraScope.launch(start = CoroutineStart.UNDISPATCHED) {
            useCaseManager.camera?.let {
                // Tell the camera to turn the torch on / off.
                val result = it.setTorchAsync(torch)

                // Update the torch state
                cameraStateAdapter.setTorchState(
                    when (torch) {
                        true -> TorchState.ON
                        false -> TorchState.OFF
                    }
                )

                // Wait until the command is received by the camera.
                result.await()
            }
        }.asListenableFuture()
    }

    override fun startFocusAndMetering(
        action: FocusMeteringAction
    ): ListenableFuture<FocusMeteringResult> {
        warn { "TODO: startFocusAndMetering is not yet supported" }
        return Futures.immediateFuture(FocusMeteringResult.emptyInstance())
    }

    override fun cancelFocusAndMetering(): ListenableFuture<Void> {
        warn { "TODO: cancelFocusAndMetering is not yet supported" }
        return Futures.immediateFuture(null)
    }

    override fun setZoomRatio(ratio: Float): ListenableFuture<Void> {
        return cameraScope.launch(start = CoroutineStart.UNDISPATCHED) {
            useCaseManager.camera?.let {
                zoomControl.zoomRatio = ratio
                val zoomValue = ZoomValue(
                    ratio,
                    zoomControl.minZoom,
                    zoomControl.maxZoom
                )
                cameraStateAdapter.setZoomState(zoomValue)
            }
        }.asListenableFuture()
    }

    override fun setLinearZoom(linearZoom: Float): ListenableFuture<Void> {
        val ratio = zoomControl.toZoomRatio(linearZoom)
        return setZoomRatio(ratio)
    }

    override fun getFlashMode(): Int {
        return imageCaptureFlashMode
    }

    override fun setFlashMode(flashMode: Int) {
        warn { "TODO: setFlashMode is not yet supported" }
        this.imageCaptureFlashMode = flashMode
    }

    override fun triggerAf(): ListenableFuture<CameraCaptureResult> {
        warn { "TODO: triggerAf is not yet supported" }
        return Futures.immediateFuture(CameraCaptureResult.EmptyCameraCaptureResult.create())
    }

    override fun triggerAePrecapture(): ListenableFuture<CameraCaptureResult> {
        warn { "TODO: triggerAePrecapture is not yet supported" }
        return Futures.immediateFuture(CameraCaptureResult.EmptyCameraCaptureResult.create())
    }

    override fun cancelAfAeTrigger(cancelAfTrigger: Boolean, cancelAePrecaptureTrigger: Boolean) {
        warn { "TODO: cancelAfAeTrigger is not yet supported" }
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun setExposureCompensationIndex(exposure: Int): ListenableFuture<Int> {
        return cameraScope.async(start = CoroutineStart.UNDISPATCHED) {
            useCaseManager.camera?.let {
                evCompControl.evCompIndex = exposure
                cameraStateAdapter.setExposureState(
                    EvCompValue(
                        evCompControl.supported,
                        evCompControl.evCompIndex,
                        evCompControl.range,
                        evCompControl.step,
                    )
                )
                return@async exposure
            }
            // TODO: Consider throwing instead? This is only reached if there's no camera.
            evCompControl.evCompIndex
        }.asListenableFuture()
    }

    override fun submitCaptureRequests(captureConfigs: MutableList<CaptureConfig>) {
        warn { "TODO: submitCaptureRequests is not yet supported" }
    }
}
/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.camera.camera2.pipe.integration.impl

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.integration.config.CameraScope
import androidx.camera.core.CameraControl
import androidx.camera.core.TorchState
import androidx.camera.core.impl.CameraControlInternal
import androidx.camera.core.impl.utils.Threads
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import dagger.Binds
import dagger.Module
import dagger.multibindings.IntoSet
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Implementation of Torch control exposed by [CameraControlInternal].
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
@CameraScope
class TorchControl @Inject constructor(
    cameraProperties: CameraProperties,
    private val threads: UseCaseThreads,
) : UseCaseCameraControl {

    private var _useCaseCamera: UseCaseCamera? = null
    override var useCaseCamera: UseCaseCamera?
        get() = _useCaseCamera
        set(value) {
            _useCaseCamera = value
            setTorchAsync(
                when (torchStateLiveData.value) {
                    TorchState.ON -> true
                    else -> false
                }
            )
        }

    override fun reset() {
        _torchState.setLiveDataValue(false)
        threads.sequentialScope.launch {
            stopRunningTaskInternal()
        }
        setTorchAsync(false)
    }

    private val hasFlashUnit: Boolean =
        cameraProperties.metadata[CameraCharacteristics.FLASH_INFO_AVAILABLE].let {
            it != null && it
        }

    private val _torchState = MutableLiveData(TorchState.OFF)
    val torchStateLiveData: LiveData<Int>
        get() = _torchState

    private var _updateSignal: CompletableDeferred<Unit>? = null

    fun setTorchAsync(torch: Boolean): Deferred<Unit> {
        val signal = CompletableDeferred<Unit>()

        if (!hasFlashUnit) {
            return signal.createFailureResult(IllegalStateException("No flash unit"))
        }

        useCaseCamera?.let { useCaseCamera ->

            _torchState.setLiveDataValue(torch)

            threads.sequentialScope.launch {
                stopRunningTaskInternal()
                _updateSignal = signal

                // TODO(b/209757083), handle the failed result of the setTorchAsync().
                useCaseCamera.requestControl.setTorchAsync(torch).join()

                if (torch) {
                    // Hold the internal AE mode to ON while the torch is turned ON.
                    useCaseCamera.requestControl.addParametersAsync(
                        type = UseCaseCameraRequestControl.Type.TORCH,
                        values = mapOf(
                            CaptureRequest.CONTROL_AE_MODE to CaptureRequest.CONTROL_AE_MODE_ON,
                        )
                    )
                } else {
                    // Restore the AE mode after the torch control has been used.
                    useCaseCamera.requestControl.setConfigAsync(
                        type = UseCaseCameraRequestControl.Type.TORCH,
                    )
                }.join()

                signal.complete(Unit)
            }
        } ?: run {
            signal.createFailureResult(
                CameraControl.OperationCanceledException("Camera is not active.")
            )
        }

        return signal
    }

    private fun stopRunningTaskInternal() {
        _updateSignal?.createFailureResult(
            CameraControl.OperationCanceledException(
                "There is a new enableTorch being set"
            )
        )
        _updateSignal = null
    }

    private fun CompletableDeferred<Unit>.createFailureResult(exception: Exception) = apply {
        completeExceptionally(exception)
    }

    private fun MutableLiveData<Int>.setLiveDataValue(enableTorch: Boolean) = when (enableTorch) {
        true -> TorchState.ON
        false -> TorchState.OFF
    }.let { torchState ->
        if (Threads.isMainThread()) {
            this.value = torchState
        } else {
            this.postValue(torchState)
        }
    }

    @Module
    abstract class Bindings {
        @Binds
        @IntoSet
        abstract fun provideControls(torchControl: TorchControl): UseCaseCameraControl
    }
}
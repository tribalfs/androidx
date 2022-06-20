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

package androidx.camera.integration.avsync

import android.content.Context
import androidx.camera.integration.avsync.model.AudioGenerator
import androidx.camera.integration.avsync.model.CameraHelper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.util.Preconditions
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.withContext

private const val ACTIVE_LENGTH_SEC: Double = 0.5
private const val ACTIVE_INTERVAL_SEC: Double = 1.0
private const val ACTIVE_DELAY_SEC: Double = 0.0

enum class ActivationSignal {
    Active, Inactive
}

class SignalGeneratorViewModel : ViewModel() {

    private var signalGenerationJob: Job? = null
    private val audioGenerator = AudioGenerator()
    private val cameraHelper = CameraHelper()

    var isGeneratorReady: Boolean by mutableStateOf(false)
        private set
    var isRecorderReady: Boolean by mutableStateOf(false)
        private set
    var isSignalGenerating: Boolean by mutableStateOf(false)
        private set
    var isActivePeriod: Boolean by mutableStateOf(false)
        private set
    var isRecording: Boolean by mutableStateOf(false)
        private set

    suspend fun initialRecorder(context: Context, lifecycleOwner: LifecycleOwner) {
        withContext(Dispatchers.Main) {
            isRecorderReady = cameraHelper.bindCamera(context, lifecycleOwner)
        }
    }

    suspend fun initialSignalGenerator(beepFrequency: Int) {
        withContext(Dispatchers.Default) {
            audioGenerator.initAudioTrack(
                frequency = beepFrequency,
                beepLengthInSec = ACTIVE_LENGTH_SEC,
            )
            isGeneratorReady = true
        }
    }

    fun startSignalGeneration() {
        Preconditions.checkState(isGeneratorReady)

        signalGenerationJob?.cancel()

        isSignalGenerating = true
        signalGenerationJob = activationSignalFlow().map { activationSignal ->
            when (activationSignal) {
                ActivationSignal.Active -> {
                    isActivePeriod = true
                    playBeepSound()
                }
                ActivationSignal.Inactive -> {
                    isActivePeriod = false
                    stopBeepSound()
                }
            }
        }.onCompletion {
            stopBeepSound()
            isActivePeriod = false
        }.launchIn(viewModelScope)
    }

    fun stopSignalGeneration() {
        Preconditions.checkState(isGeneratorReady)

        isSignalGenerating = false
        signalGenerationJob?.cancel()
        signalGenerationJob = null
    }

    fun startRecording(context: Context) {
        Preconditions.checkState(isRecorderReady)

        cameraHelper.startRecording(context)
        isRecording = true
    }

    fun stopRecording() {
        Preconditions.checkState(isRecorderReady)

        cameraHelper.stopRecording()
        isRecording = false
    }

    private fun activationSignalFlow() = flow {
        delay((ACTIVE_DELAY_SEC * 1000).toLong())
        while (true) {
            emit(ActivationSignal.Active)
            delay((ACTIVE_LENGTH_SEC * 1000).toLong())
            emit(ActivationSignal.Inactive)
            delay((ACTIVE_INTERVAL_SEC * 1000).toLong())
        }
    }

    private fun playBeepSound() {
        audioGenerator.start()
    }

    private fun stopBeepSound() {
        audioGenerator.stop()
    }
}
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
import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.integration.config.CameraScope
import androidx.camera.camera2.pipe.integration.impl.EvCompControl
import androidx.camera.camera2.pipe.integration.impl.TorchControl
import androidx.camera.camera2.pipe.integration.impl.ZoomControl
import androidx.camera.core.ExposureState
import androidx.camera.core.ZoomState
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * [CameraStateAdapter] caches and updates based on callbacks from the active CameraGraph.
 */
@SuppressLint("UnsafeOptInUsageError")
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
@CameraScope
class CameraStateAdapter @Inject constructor(
    private val zoomControl: ZoomControl,
    private val evCompControl: EvCompControl,
    private val torchControl: TorchControl,
) {
    val torchStateLiveData: LiveData<Int>
        get() = torchControl.torchStateLiveData

    private val _zoomState by lazy {
        MutableLiveData<ZoomState>(
            ZoomValue(
                zoomControl.zoomRatio,
                zoomControl.minZoom,
                zoomControl.maxZoom
            )
        )
    }
    val zoomStateLiveData: LiveData<ZoomState>
        get() = _zoomState
    suspend fun setZoomState(value: ZoomState) {
        withContext(Dispatchers.Main) {
            _zoomState.value = value
        }
    }

    val exposureState: ExposureState
        get() = evCompControl.exposureState
}
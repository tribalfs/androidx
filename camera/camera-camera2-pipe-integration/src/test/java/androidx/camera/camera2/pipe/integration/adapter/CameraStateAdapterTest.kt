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

package androidx.camera.camera2.pipe.integration.adapter

import android.os.Build
import androidx.camera.camera2.pipe.GraphState.GraphStateStarted
import androidx.camera.camera2.pipe.GraphState.GraphStateStarting
import androidx.camera.camera2.pipe.GraphState.GraphStateStopped
import androidx.camera.camera2.pipe.GraphState.GraphStateStopping
import androidx.camera.camera2.pipe.integration.testing.FakeCameraGraph
import androidx.camera.core.CameraState
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.internal.DoNotInstrument

@RunWith(RobolectricCameraPipeTestRunner::class)
@DoNotInstrument
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
internal class CameraStateAdapterTest {
    private val cameraStateAdapter = CameraStateAdapter()
    private val cameraGraph1 = FakeCameraGraph()
    private val cameraGraph2 = FakeCameraGraph()

    @Test
    fun testNormalStateTransitions() {
        cameraStateAdapter.onGraphUpdated(cameraGraph1)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.CLOSED)

        cameraStateAdapter.onGraphStateUpdated(cameraGraph1, GraphStateStarting)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.OPENING)

        cameraStateAdapter.onGraphStateUpdated(cameraGraph1, GraphStateStarted)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.OPEN)

        cameraStateAdapter.onGraphStateUpdated(cameraGraph1, GraphStateStopped)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.CLOSED)
    }

    @Test
    fun testStaleStateTransitions() {
        cameraStateAdapter.onGraphUpdated(cameraGraph1)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.CLOSED)

        cameraStateAdapter.onGraphStateUpdated(cameraGraph1, GraphStateStarting)
        cameraStateAdapter.onGraphStateUpdated(cameraGraph1, GraphStateStarted)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.OPEN)

        // Simulate that a new camera graph is created.
        cameraStateAdapter.onGraphUpdated(cameraGraph2)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.CLOSED)
        cameraStateAdapter.onGraphStateUpdated(cameraGraph2, GraphStateStarting)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.OPENING)

        // This came from cameraGraph1 and thereby making the transition stale.
        cameraStateAdapter.onGraphStateUpdated(cameraGraph1, GraphStateStopped)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.OPENING)

        cameraStateAdapter.onGraphStateUpdated(cameraGraph2, GraphStateStarted)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.OPEN)
    }

    @Test
    fun testImpermissibleStateTransitions() {
        cameraStateAdapter.onGraphUpdated(cameraGraph1)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.CLOSED)

        // Impermissible state transition from GraphStateStopped to GraphStateStopping
        cameraStateAdapter.onGraphStateUpdated(cameraGraph1, GraphStateStopping)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.CLOSED)

        cameraStateAdapter.onGraphStateUpdated(cameraGraph1, GraphStateStarting)
        cameraStateAdapter.onGraphStateUpdated(cameraGraph1, GraphStateStarted)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.OPEN)

        // Impermissible state transition from GraphStateStarted to GraphStateStarting
        cameraStateAdapter.onGraphStateUpdated(cameraGraph1, GraphStateStarting)
        assertThat(cameraStateAdapter.cameraState.value?.type).isEqualTo(CameraState.Type.OPEN)
    }
}
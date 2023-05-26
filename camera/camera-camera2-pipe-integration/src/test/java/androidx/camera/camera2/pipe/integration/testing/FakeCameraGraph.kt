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

package androidx.camera.camera2.pipe.integration.testing

import android.view.Surface
import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.CameraGraph
import androidx.camera.camera2.pipe.GraphState
import androidx.camera.camera2.pipe.StreamGraph
import androidx.camera.camera2.pipe.StreamId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.StateFlow

@RequiresApi(21)
class FakeCameraGraph(
    val fakeCameraGraphSession: FakeCameraGraphSession = FakeCameraGraphSession()
) : CameraGraph {

    val setSurfaceResults = mutableMapOf<StreamId, Surface?>()
    private var isClosed = false

    override val streams: StreamGraph
        get() = throw NotImplementedError("Not used in testing")

    override val graphState: StateFlow<GraphState>
        get() = throw NotImplementedError("Not used in testing")
    override var isForeground = false

    override suspend fun acquireSession(): CameraGraph.Session {
        if (isClosed) {
            throw CancellationException()
        }
        return fakeCameraGraphSession
    }

    override fun acquireSessionOrNull() = if (isClosed) null else fakeCameraGraphSession

    override fun close() {
        isClosed = true
    }

    override fun setSurface(stream: StreamId, surface: Surface?) {
        setSurfaceResults[stream] = surface
    }

    override fun start() {
        throw NotImplementedError("Not used in testing")
    }

    override fun stop() {
        throw NotImplementedError("Not used in testing")
    }
}

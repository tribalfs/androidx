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

package androidx.camera.core.streamsharing

import android.os.Build
import android.util.Size
import androidx.camera.core.impl.SessionConfig
import androidx.camera.core.impl.StreamSpec
import androidx.camera.core.impl.UseCaseConfigFactory
import androidx.camera.core.impl.utils.executor.CameraXExecutors.mainThreadExecutor
import androidx.camera.core.internal.TargetConfig.OPTION_TARGET_CLASS
import androidx.camera.core.internal.TargetConfig.OPTION_TARGET_NAME
import androidx.camera.core.processing.DefaultSurfaceProcessor
import androidx.camera.testing.fakes.FakeCamera
import androidx.camera.testing.fakes.FakeSurfaceProcessorInternal
import androidx.camera.testing.fakes.FakeUseCase
import androidx.camera.testing.fakes.FakeUseCaseConfigFactory
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.internal.DoNotInstrument

/**
 * Unit tests for [StreamSharing].
 */
@RunWith(RobolectricTestRunner::class)
@DoNotInstrument
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
class StreamSharingTest {

    private val parentCamera = FakeCamera()
    private val child1 = FakeUseCase()
    private val child2 = FakeUseCase()
    private val useCaseConfigFactory = FakeUseCaseConfigFactory()
    private val camera = FakeCamera()
    private lateinit var streamSharing: StreamSharing
    private val size = Size(800, 600)

    @Before
    fun setUp() {
        DefaultSurfaceProcessor.Factory.setSupplier {
            FakeSurfaceProcessorInternal(
                mainThreadExecutor()
            )
        }
        streamSharing = StreamSharing(parentCamera, setOf(child1, child2), useCaseConfigFactory)
    }

    @Test
    fun updateStreamSpec_propagatesToChildren() {
        // Arrange: bind StreamSharing to the camera.
        streamSharing.bindToCamera(
            camera,
            null,
            streamSharing.getDefaultConfig(true, useCaseConfigFactory)
        )

        // Act: update suggested specs.
        streamSharing.onSuggestedStreamSpecUpdated(StreamSpec.builder(size).build())

        // Assert: StreamSharing pipeline created.
        val node = streamSharing.node!!
        val cameraEdge = streamSharing.cameraEdge!!
        assertThat(streamSharing.cameraEdge).isNotNull()
        assertThat(streamSharing.node).isNotNull()
        assertThat(streamSharing.sessionConfig.repeatingCameraCaptureCallbacks).isNotEmpty()
        // Assert: specs propagated to children.
        assertThat(child1.attachedStreamSpec).isNotNull()
        assertThat(child2.attachedStreamSpec).isNotNull()

        // Act: unbind StreamSharing.
        streamSharing.unbindFromCamera(camera)

        // Assert: pipeline is cleared.
        assertThat(streamSharing.cameraEdge).isNull()
        assertThat(streamSharing.node).isNull()
        assertThat((node.surfaceProcessor as FakeSurfaceProcessorInternal).isReleased).isTrue()
        assertThat(cameraEdge.isClosed).isTrue()
        assertThat(child1.attachedStreamSpec).isNull()
        assertThat(child2.attachedStreamSpec).isNull()
    }

    @Test
    fun onError_restartsPipeline() {
        // Arrange: bind stream sharing and update specs.
        streamSharing.bindToCamera(
            camera,
            null,
            streamSharing.getDefaultConfig(true, useCaseConfigFactory)
        )
        streamSharing.onSuggestedStreamSpecUpdated(StreamSpec.builder(size).build())
        val cameraEdge = streamSharing.cameraEdge
        val node = streamSharing.node

        // Act: send error to StreamSharing
        val sessionConfig = streamSharing.sessionConfig
        sessionConfig.errorListeners.single()
            .onError(sessionConfig, SessionConfig.SessionError.SESSION_ERROR_SURFACE_NEEDS_RESET)

        // Assert: StreamSharing and children pipeline are recreated.
        assertThat(streamSharing.cameraEdge).isNotSameInstanceAs(cameraEdge)
        assertThat(streamSharing.node).isNotSameInstanceAs(node)
        assertThat(child1.pipelineCreationCount).isEqualTo(2)
        assertThat(child2.pipelineCreationCount).isEqualTo(2)
    }

    @Test
    fun bindAndUnbindParent_propagatesToChildren() {
        // Assert: children not bound to camera by default.
        assertThat(child1.camera).isNull()
        assertThat(child2.camera).isNull()
        // Act: bind to camera.
        streamSharing.bindToCamera(camera, null, null)
        // Assert: children bound to the virtual camera.
        assertThat(child1.camera).isInstanceOf(VirtualCamera::class.java)
        assertThat(child1.mergedConfigRetrieved).isTrue()
        assertThat(child2.camera).isInstanceOf(VirtualCamera::class.java)
        assertThat(child2.mergedConfigRetrieved).isTrue()
        // Act: unbind.
        streamSharing.unbindFromCamera(camera)
        // Assert: children not bound.
        assertThat(child1.camera).isNull()
        assertThat(child2.camera).isNull()
    }

    @Test
    fun attachAndDetachParent_propagatesToChildren() {
        // Assert: children not attached by default.
        assertThat(child1.stateAttachedCount).isEqualTo(0)
        assertThat(child2.stateAttachedCount).isEqualTo(0)
        // Act: attach.
        streamSharing.onStateAttached()
        // Assert: children attached.
        assertThat(child1.stateAttachedCount).isEqualTo(1)
        assertThat(child2.stateAttachedCount).isEqualTo(1)
        // Act: detach.
        streamSharing.onStateDetached()
        // Assert: children not attached.
        assertThat(child1.stateAttachedCount).isEqualTo(0)
        assertThat(child2.stateAttachedCount).isEqualTo(0)
    }

    @Test
    fun getDefaultConfig_usesVideoCaptureType() {
        val config = streamSharing.getDefaultConfig(true, useCaseConfigFactory)!!

        assertThat(useCaseConfigFactory.lastRequestedCaptureType)
            .isEqualTo(UseCaseConfigFactory.CaptureType.VIDEO_CAPTURE)
        assertThat(
            config.retrieveOption(
                OPTION_TARGET_CLASS,
                null
            )
        ).isEqualTo(StreamSharing::class.java)
        assertThat(
            config.retrieveOption(
                OPTION_TARGET_NAME,
                null
            )
        ).startsWith("androidx.camera.core.streamsharing.StreamSharing-")
    }
}
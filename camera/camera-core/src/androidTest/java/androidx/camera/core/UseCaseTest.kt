/*
 * Copyright (C) 2019 The Android Open Source Project
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
package androidx.camera.core

import android.graphics.ImageFormat
import android.graphics.Rect
import android.util.LayoutDirection
import android.util.Rational
import android.util.Size
import android.view.Surface
import androidx.camera.core.impl.CameraInternal
import androidx.camera.core.impl.Config
import androidx.camera.core.impl.ImageOutputConfig
import androidx.camera.core.impl.SessionConfig
import androidx.camera.core.impl.StreamSpec
import androidx.camera.core.impl.UseCaseConfigFactory
import androidx.camera.core.internal.CameraUseCaseAdapter
import androidx.camera.testing.fakes.FakeCamera
import androidx.camera.testing.fakes.FakeCameraDeviceSurfaceManager
import androidx.camera.testing.fakes.FakeCameraInfoInternal
import androidx.camera.testing.fakes.FakeUseCase
import androidx.camera.testing.fakes.FakeUseCaseConfig
import androidx.camera.testing.fakes.FakeUseCaseConfigFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito

@SmallTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 21)
class UseCaseTest {
    private var mockCameraInternal: CameraInternal? = null

    @Before
    fun setup() {
        mockCameraInternal = Mockito.mock(
            CameraInternal::class.java
        )
    }

    @Test
    fun noCameraTransform_rotationMirrored() {
        // Arrange.
        val testUseCase = createFakeUseCase(targetRotation = Surface.ROTATION_90)
        val fakeCamera = FakeCamera()
        testUseCase.hasCameraTransform = false
        // Act/Assert:
        assertThat(testUseCase.getRelativeRotation(fakeCamera, true)).isEqualTo(90)
        assertThat(testUseCase.getRelativeRotation(fakeCamera, false)).isEqualTo(270)
    }

    @Test
    fun hasCameraTransform_rotationNotMirrored() {
        // Arrange.
        val testUseCase = createFakeUseCase(targetRotation = Surface.ROTATION_90)
        val fakeCamera = FakeCamera()
        // Act/Assert:
        assertThat(testUseCase.getRelativeRotation(fakeCamera, true)).isEqualTo(270)
        assertThat(testUseCase.getRelativeRotation(fakeCamera, false)).isEqualTo(270)
    }

    @Test
    fun getAttachedSessionConfig() {
        val testUseCase = createFakeUseCase()
        val sessionToAttach = SessionConfig.Builder().build()
        testUseCase.updateSessionConfig(sessionToAttach)
        val attachedSession = testUseCase.sessionConfig
        assertThat(attachedSession).isEqualTo(sessionToAttach)
    }

    @Test
    fun removeListener() {
        val testUseCase = createFakeUseCase()
        testUseCase.bindToCamera(mockCameraInternal!!, null, null)
        testUseCase.unbindFromCamera(mockCameraInternal!!)
        testUseCase.notifyActive()
        Mockito.verify(mockCameraInternal, Mockito.never())!!.onUseCaseActive(
            ArgumentMatchers.any(
                UseCase::class.java
            )
        )
    }

    @Test
    fun notifyActiveState() {
        val testUseCase = createFakeUseCase()
        testUseCase.bindToCamera(mockCameraInternal!!, null, null)
        testUseCase.notifyActive()
        Mockito.verify(mockCameraInternal, Mockito.times(1))!!.onUseCaseActive(testUseCase)
    }

    @Test
    fun notifyInactiveState() {
        val testUseCase = createFakeUseCase()
        testUseCase.bindToCamera(mockCameraInternal!!, null, null)
        testUseCase.notifyInactive()
        Mockito.verify(mockCameraInternal, Mockito.times(1))!!.onUseCaseInactive(testUseCase)
    }

    @Test
    fun notifyUpdatedSettings() {
        val testUseCase = FakeUseCase()
        testUseCase.bindToCamera(mockCameraInternal!!, null, null)
        testUseCase.notifyUpdated()
        Mockito.verify(mockCameraInternal, Mockito.times(1))!!.onUseCaseUpdated(testUseCase)
    }

    @Test
    fun notifyResetUseCase() {
        val testUseCase = FakeUseCase()
        testUseCase.bindToCamera(mockCameraInternal!!, null, null)
        testUseCase.notifyReset()
        Mockito.verify(mockCameraInternal, Mockito.times(1))!!.onUseCaseReset(testUseCase)
    }

    @Test
    fun useCaseConfig_keepOptionPriority() {
        val builder = FakeUseCaseConfig.Builder()
        val opt = Config.Option.create<Int>("OPT1", Int::class.java)
        builder.mutableConfig.insertOption(opt, Config.OptionPriority.ALWAYS_OVERRIDE, 1)
        val fakeUseCase = builder.build()
        val useCaseConfig = fakeUseCase.currentConfig
        assertThat(useCaseConfig.getOptionPriority(opt))
            .isEqualTo(Config.OptionPriority.ALWAYS_OVERRIDE)
    }

    @Test
    fun attachedSurfaceResolutionCanBeReset_whenOnDetach() {
        val testUseCase = FakeUseCase()
        testUseCase.updateSuggestedStreamSpec(TEST_STREAM_SPEC)
        assertThat(testUseCase.attachedSurfaceResolution).isNotNull()
        testUseCase.bindToCamera(mockCameraInternal!!, null, null)
        testUseCase.unbindFromCamera(mockCameraInternal!!)
        assertThat(testUseCase.attachedSurfaceResolution).isNull()
    }

    @Test
    fun attachedStreamSpecCanBeReset_whenOnDetach() {
        val testUseCase = FakeUseCase()
        testUseCase.updateSuggestedStreamSpec(TEST_STREAM_SPEC)
        assertThat(testUseCase.attachedStreamSpec).isNotNull()
        testUseCase.bindToCamera(mockCameraInternal!!, null, null)
        testUseCase.unbindFromCamera(mockCameraInternal!!)
        assertThat(testUseCase.attachedStreamSpec).isNull()
    }

    @Test
    fun viewPortCropRectCanBeReset_whenOnDetach() {
        val testUseCase = FakeUseCase()
        testUseCase.setViewPortCropRect(Rect(0, 0, 640, 480))
        assertThat(testUseCase.viewPortCropRect).isNotNull()
        testUseCase.bindToCamera(mockCameraInternal!!, null, null)
        testUseCase.unbindFromCamera(mockCameraInternal!!)
        assertThat(testUseCase.viewPortCropRect).isNull()
    }

    @Test
    fun mergeConfigs() {
        val cameraDefaultPriority = 4
        val defaultConfig = FakeUseCaseConfig.Builder()
            .setTargetRotation(Surface.ROTATION_0)
            .setBufferFormat(ImageFormat.RAW10)
            .setSurfaceOccupancyPriority(cameraDefaultPriority).useCaseConfig
        val useCaseImageFormat = ImageFormat.YUV_420_888
        val useCaseConfig = FakeUseCaseConfig.Builder()
            .setTargetRotation(Surface.ROTATION_90)
            .setBufferFormat(useCaseImageFormat).useCaseConfig
        val extendedConfig = FakeUseCaseConfig.Builder()
            .setTargetRotation(Surface.ROTATION_180).useCaseConfig
        val testUseCase = FakeUseCase(useCaseConfig)
        val cameraInfo = FakeCameraInfoInternal()
        val mergedConfig = testUseCase.mergeConfigs(
            cameraInfo, extendedConfig,
            defaultConfig
        )
        assertThat(mergedConfig.surfaceOccupancyPriority).isEqualTo(cameraDefaultPriority)
        assertThat(mergedConfig.inputFormat).isEqualTo(useCaseImageFormat)
        val imageOutputConfig = mergedConfig as ImageOutputConfig
        assertThat(imageOutputConfig.targetRotation).isEqualTo(Surface.ROTATION_180)
    }

    @Test
    fun returnNullResolutionInfo_beforeAddingToCameraUseCaseAdapter() {
        val fakeUseCase = FakeUseCase()
        assertThat(fakeUseCase.resolutionInfo).isNull()
    }

    @Test
    @Throws(CameraUseCaseAdapter.CameraException::class)
    fun returnResolutionInfo_afterAddingToCameraUseCaseAdapter() {
        val fakeUseCase = FakeUseCase()
        val cameraUseCaseAdapter = createCameraUseCaseAdapter()
        cameraUseCaseAdapter.addUseCases(listOf<UseCase>(fakeUseCase))
        val resolutionInfo = fakeUseCase.resolutionInfo
        assertThat(resolutionInfo).isNotNull()
        assertThat(resolutionInfo!!.resolution).isEqualTo(SURFACE_RESOLUTION)
        assertThat(resolutionInfo.cropRect).isEqualTo(
            Rect(
                0, 0,
                SURFACE_RESOLUTION.width, SURFACE_RESOLUTION.height
            )
        )
        assertThat(resolutionInfo.rotationDegrees).isEqualTo(0)
    }

    @Test
    @Throws(CameraUseCaseAdapter.CameraException::class)
    fun returnNullResolutionInfo_afterRemovedFromCameraUseCaseAdapter() {
        val fakeUseCase = FakeUseCase()
        val cameraUseCaseAdapter = createCameraUseCaseAdapter()
        cameraUseCaseAdapter.addUseCases(listOf<UseCase>(fakeUseCase))
        cameraUseCaseAdapter.removeUseCases(listOf<UseCase>(fakeUseCase))
        val resolutionInfo = fakeUseCase.resolutionInfo
        assertThat(resolutionInfo).isNull()
    }

    @Test
    @Throws(CameraUseCaseAdapter.CameraException::class)
    fun correctRotationDegreesInResolutionInfo() {
        val fakeUseCase = FakeUseCase()
        fakeUseCase.targetRotationInternal = Surface.ROTATION_90
        val cameraUseCaseAdapter = createCameraUseCaseAdapter()
        cameraUseCaseAdapter.addUseCases(listOf<UseCase>(fakeUseCase))
        val resolutionInfo = fakeUseCase.resolutionInfo
        assertThat(resolutionInfo!!.rotationDegrees).isEqualTo(270)
    }

    @Test
    @Throws(CameraUseCaseAdapter.CameraException::class)
    fun correctViewPortRectInResolutionInfo() {
        val fakeUseCase = FakeUseCase()
        val cameraUseCaseAdapter = createCameraUseCaseAdapter()
        cameraUseCaseAdapter.setViewPort(
            ViewPort(
                ViewPort.FILL_CENTER,
                Rational(16, 9), Surface.ROTATION_0, LayoutDirection.LTR
            )
        )
        cameraUseCaseAdapter.addUseCases(listOf<UseCase>(fakeUseCase))
        val resolutionInfo = fakeUseCase.resolutionInfo
        assertThat(resolutionInfo!!.cropRect).isEqualTo(Rect(0, 60, 640, 420))
    }

    private fun createFakeUseCase(
        targetRotation: Int = Surface.ROTATION_0
    ): FakeUseCase {
        return FakeUseCase(
            FakeUseCaseConfig.Builder()
                .setTargetName("UseCase")
                .setTargetRotation(targetRotation)
                .useCaseConfig
        )
    }

    private fun createCameraUseCaseAdapter(): CameraUseCaseAdapter {
        val cameraId = "fakeCameraId"
        val fakeCamera = FakeCamera(
            cameraId, null,
            FakeCameraInfoInternal(cameraId)
        )
        val fakeCameraDeviceSurfaceManager = FakeCameraDeviceSurfaceManager()
        fakeCameraDeviceSurfaceManager.setSuggestedStreamSpec(
            cameraId,
            FakeUseCaseConfig::class.java,
            TEST_STREAM_SPEC
        )
        val useCaseConfigFactory: UseCaseConfigFactory = FakeUseCaseConfigFactory()
        return CameraUseCaseAdapter(
            LinkedHashSet(setOf(fakeCamera)),
            fakeCameraDeviceSurfaceManager,
            useCaseConfigFactory
        )
    }

    companion object {
        private val SURFACE_RESOLUTION: Size by lazy { Size(640, 480) }
        private val TEST_STREAM_SPEC: StreamSpec by lazy {
            StreamSpec.builder(SURFACE_RESOLUTION).build()
        }
    }
}
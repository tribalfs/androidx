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

package androidx.camera.camera2.internal

import android.content.Context
import android.content.pm.PackageManager.FEATURE_CAMERA_CONCURRENT
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.CamcorderProfile
import android.media.CamcorderProfile.QUALITY_1080P
import android.media.CamcorderProfile.QUALITY_2160P
import android.media.CamcorderProfile.QUALITY_480P
import android.media.CamcorderProfile.QUALITY_720P
import android.media.MediaRecorder
import android.os.Build
import android.util.Range
import android.util.Size
import android.view.WindowManager
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.internal.compat.CameraManagerCompat
import androidx.camera.core.CameraSelector.LensFacing
import androidx.camera.core.CameraX
import androidx.camera.core.CameraXConfig
import androidx.camera.core.UseCase
import androidx.camera.core.impl.AttachedSurfaceInfo
import androidx.camera.core.impl.CameraDeviceSurfaceManager
import androidx.camera.core.impl.ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE
import androidx.camera.core.impl.SurfaceCombination
import androidx.camera.core.impl.SurfaceConfig
import androidx.camera.core.impl.SurfaceConfig.ConfigSize
import androidx.camera.core.impl.SurfaceConfig.ConfigType
import androidx.camera.core.impl.UseCaseConfig
import androidx.camera.core.impl.UseCaseConfigFactory
import androidx.camera.core.impl.UseCaseConfigFactory.CaptureType
import androidx.camera.core.internal.utils.SizeUtil.RESOLUTION_1440P
import androidx.camera.core.internal.utils.SizeUtil.RESOLUTION_720P
import androidx.camera.core.internal.utils.SizeUtil.RESOLUTION_VGA
import androidx.camera.testing.CameraUtil
import androidx.camera.testing.CameraXUtil
import androidx.camera.testing.EncoderProfilesUtil
import androidx.camera.testing.fakes.FakeCamera
import androidx.camera.testing.fakes.FakeCameraFactory
import androidx.camera.testing.fakes.FakeCameraInfoInternal
import androidx.camera.testing.fakes.FakeEncoderProfilesProvider
import androidx.camera.testing.fakes.FakeUseCaseConfig
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import org.codehaus.plexus.util.ReflectionUtils
import org.junit.After
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.internal.DoNotInstrument
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowCameraCharacteristics
import org.robolectric.shadows.ShadowCameraManager

private const val DEFAULT_CAMERA_ID = "0"
private const val EXTERNAL_CAMERA_ID = "0-external"
private const val SENSOR_ORIENTATION_90 = 90
private val LANDSCAPE_PIXEL_ARRAY_SIZE = Size(4032, 3024)
private val DISPLAY_SIZE = Size(720, 1280)
private val PREVIEW_SIZE = Size(1280, 720)
private val RECORD_SIZE = Size(3840, 2160)
private val MAXIMUM_SIZE = Size(4032, 3024)
private val LEGACY_VIDEO_MAXIMUM_SIZE = Size(1920, 1080)
private val DEFAULT_SUPPORTED_SIZES = arrayOf(
    Size(4032, 3024), // 4:3
    Size(3840, 2160), // 16:9
    Size(1920, 1440), // 4:3
    Size(1920, 1080), // 16:9
    Size(1280, 960), // 4:3
    Size(1280, 720), // 16:9
    Size(960, 544), // a mod16 version of resolution with 16:9 aspect ratio.
    Size(800, 450), // 16:9
    Size(640, 480), // 4:3
)

/** Robolectric test for [SupportedSurfaceCombination] class */
@RunWith(RobolectricTestRunner::class)
@DoNotInstrument
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
class SupportedSurfaceCombinationTest {
    private val mockCamcorderProfileHelper = Mockito.mock(
        CamcorderProfileHelper::class.java
    )
    private val mockCamcorderProfile = Mockito.mock(
        CamcorderProfile::class.java
    )
    private var cameraManagerCompat: CameraManagerCompat? = null
    private val profileUhd = EncoderProfilesUtil.createFakeEncoderProfilesProxy(
        RECORD_SIZE.width, RECORD_SIZE.height
    )
    private val profileFhd = EncoderProfilesUtil.createFakeEncoderProfilesProxy(
        1920, 1080
    )
    private val profileHd = EncoderProfilesUtil.createFakeEncoderProfilesProxy(
        PREVIEW_SIZE.width, PREVIEW_SIZE.height
    )
    private val profileSd = EncoderProfilesUtil.createFakeEncoderProfilesProxy(
        RESOLUTION_VGA.width, RESOLUTION_VGA.height
    )
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var cameraFactory: FakeCameraFactory? = null
    private var useCaseConfigFactory: UseCaseConfigFactory? = null
    private lateinit var cameraDeviceSurfaceManager: CameraDeviceSurfaceManager

    @Suppress("DEPRECATION") // defaultDisplay
    @Before
    fun setUp() {
        DisplayInfoManager.releaseInstance()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        shadowOf(windowManager.defaultDisplay).setRealWidth(DISPLAY_SIZE.width)
        shadowOf(windowManager.defaultDisplay).setRealHeight(DISPLAY_SIZE.height)
        Mockito.`when`(
            mockCamcorderProfileHelper.hasProfile(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.anyInt()
            )
        ).thenReturn(true)
        ReflectionUtils.setVariableValueInObject(mockCamcorderProfile, "videoFrameWidth", 3840)
        ReflectionUtils.setVariableValueInObject(mockCamcorderProfile, "videoFrameHeight", 2160)
        Mockito.`when`(
            mockCamcorderProfileHelper[ArgumentMatchers.anyInt(), ArgumentMatchers.anyInt()]
        ).thenReturn(mockCamcorderProfile)
    }

    @After
    fun tearDown() {
        CameraXUtil.shutdown()[10000, TimeUnit.MILLISECONDS]
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Surface combination support tests for guaranteed configurations
    //
    // //////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun checkLegacySurfaceCombinationSupportedInLegacyDevice() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLegacySupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isTrue()
        }
    }

    @Test
    fun checkLegacySurfaceCombinationSubListSupportedInLegacyDevice() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLegacySupportedCombinationList().also {
            assertThat(isAllSubConfigListSupported(false, supportedSurfaceCombination, it)).isTrue()
        }
    }

    @Test
    fun checkLimitedSurfaceCombinationNotSupportedInLegacyDevice() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLimitedSupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isFalse()
        }
    }

    @Test
    fun checkFullSurfaceCombinationNotSupportedInLegacyDevice() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getFullSupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isFalse()
        }
    }

    @Test
    fun checkLevel3SurfaceCombinationNotSupportedInLegacyDevice() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLevel3SupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isFalse()
        }
    }

    @Test
    fun checkLimitedSurfaceCombinationSupportedInLimitedDevice() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLimitedSupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isTrue()
        }
    }

    @Test
    fun checkLimitedSurfaceCombinationSubListSupportedInLimited3Device() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLimitedSupportedCombinationList().also {
            assertThat(isAllSubConfigListSupported(false, supportedSurfaceCombination, it)).isTrue()
        }
    }

    @Test
    fun checkFullSurfaceCombinationNotSupportedInLimitedDevice() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getFullSupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isFalse()
        }
    }

    @Test
    fun checkLevel3SurfaceCombinationNotSupportedInLimitedDevice() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLevel3SupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isFalse()
        }
    }

    @Test
    fun checkFullSurfaceCombinationSupportedInFullDevice() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getFullSupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isTrue()
        }
    }

    @Test
    fun checkFullSurfaceCombinationSubListSupportedInFullDevice() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getFullSupportedCombinationList().also {
            assertThat(isAllSubConfigListSupported(false, supportedSurfaceCombination, it)).isTrue()
        }
    }

    @Test
    fun checkLevel3SurfaceCombinationNotSupportedInFullDevice() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLevel3SupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isFalse()
        }
    }

    @Test
    fun checkLimitedSurfaceCombinationSupportedInRawDevice() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            capabilities = intArrayOf(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLimitedSupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isTrue()
        }
    }

    @Test
    fun checkLegacySurfaceCombinationSupportedInRawDevice() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            capabilities = intArrayOf(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLegacySupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isTrue()
        }
    }

    @Test
    fun checkFullSurfaceCombinationSupportedInRawDevice() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            capabilities = intArrayOf(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getFullSupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isTrue()
        }
    }

    @Test
    fun checkRawSurfaceCombinationSupportedInRawDevice() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            capabilities = intArrayOf(CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getRAWSupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isTrue()
        }
    }

    @Test
    fun checkLevel3SurfaceCombinationSupportedInLevel3Device() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLevel3SupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                false, it.surfaceConfigList)).isTrue()
        }
    }

    @Test
    fun checkLevel3SurfaceCombinationSubListSupportedInLevel3Device() {
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getLevel3SupportedCombinationList().also {
            assertThat(isAllSubConfigListSupported(false, supportedSurfaceCombination, it)).isTrue()
        }
    }

    @Test
    fun checkConcurrentSurfaceCombinationSupportedInConcurrentCameraMode() {
        shadowOf(context.packageManager).setSystemFeature(
            FEATURE_CAMERA_CONCURRENT, true)
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getConcurrentSupportedCombinationList().forEach {
            assertThat(supportedSurfaceCombination.checkSupported(
                true, it.surfaceConfigList)).isTrue()
        }
    }

    @Test
    fun checkConcurrentSurfaceCombinationSubListSupportedInConcurrentCameraMode() {
        shadowOf(context.packageManager).setSystemFeature(
            FEATURE_CAMERA_CONCURRENT, true)
        setupCameraAndInitCameraX(
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        GuaranteedConfigurationsUtil.getConcurrentSupportedCombinationList().also {
            assertThat(isAllSubConfigListSupported(
                true, supportedSurfaceCombination, it)).isTrue()
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Surface config transformation tests
    //
    // //////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun transformSurfaceConfigWithYUVAnalysisSize() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            false,
            ImageFormat.YUV_420_888, RESOLUTION_VGA
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.YUV, ConfigSize.VGA)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithYUVPreviewSize() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            false,
            ImageFormat.YUV_420_888, PREVIEW_SIZE
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.YUV, ConfigSize.PREVIEW)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithYUVRecordSize() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            false,
            ImageFormat.YUV_420_888, RECORD_SIZE
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.YUV, ConfigSize.RECORD)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithYUVMaximumSize() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            false,
            ImageFormat.YUV_420_888, MAXIMUM_SIZE
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.YUV, ConfigSize.MAXIMUM)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithJPEGAnalysisSize() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            false,
            ImageFormat.JPEG, RESOLUTION_VGA
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.JPEG, ConfigSize.VGA)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithJPEGPreviewSize() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            false,
            ImageFormat.JPEG, PREVIEW_SIZE
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.JPEG, ConfigSize.PREVIEW)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithJPEGRecordSize() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            false,
            ImageFormat.JPEG, RECORD_SIZE
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.JPEG, ConfigSize.RECORD)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithJPEGMaximumSize() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            false,
            ImageFormat.JPEG, MAXIMUM_SIZE
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.JPEG, ConfigSize.MAXIMUM)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithPRIVS720PSizeInConcurrentMode() {
        shadowOf(context.packageManager).setSystemFeature(FEATURE_CAMERA_CONCURRENT, true)
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            true,
            ImageFormat.PRIVATE, RESOLUTION_720P
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.PRIV, ConfigSize.s720p)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithYUVS720PSizeInConcurrentMode() {
        shadowOf(context.packageManager).setSystemFeature(FEATURE_CAMERA_CONCURRENT, true)
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            true,
            ImageFormat.YUV_420_888, RESOLUTION_720P
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.YUV, ConfigSize.s720p)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithJPEGS720PSizeInConcurrentMode() {
        shadowOf(context.packageManager).setSystemFeature(FEATURE_CAMERA_CONCURRENT, true)
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            true,
            ImageFormat.JPEG, RESOLUTION_720P
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.JPEG, ConfigSize.s720p)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithPRIVS1440PSizeInConcurrentMode() {
        shadowOf(context.packageManager).setSystemFeature(FEATURE_CAMERA_CONCURRENT, true)
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            true,
            ImageFormat.PRIVATE, RESOLUTION_1440P
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.PRIV, ConfigSize.s1440p)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithYUVS1440PSizeInConcurrentMode() {
        shadowOf(context.packageManager).setSystemFeature(FEATURE_CAMERA_CONCURRENT, true)
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            true,
            ImageFormat.YUV_420_888, RESOLUTION_1440P
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.YUV, ConfigSize.s1440p)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    @Test
    fun transformSurfaceConfigWithJPEGS1440PSizeInConcurrentMode() {
        shadowOf(context.packageManager).setSystemFeature(FEATURE_CAMERA_CONCURRENT, true)
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val surfaceConfig = supportedSurfaceCombination.transformSurfaceConfig(
            true,
            ImageFormat.JPEG, RESOLUTION_1440P
        )
        val expectedSurfaceConfig = SurfaceConfig.create(ConfigType.JPEG, ConfigSize.s1440p)
        assertThat(surfaceConfig).isEqualTo(expectedSurfaceConfig)
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Resolution selection tests for LEGACY-level guaranteed configurations
    //
    // //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * PRIV/MAXIMUM
     */
    @Test
    fun canSelectCorrectSize_singlePrivStream_inLegacyDevice() {
        val privUseCase = createUseCase(CaptureType.VIDEO_CAPTURE)
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(useCaseExpectedResultMap)
    }

    /**
     * JPEG/MAXIMUM
     */
    @Test
    fun canSelectCorrectSize_singleJpegStream_inLegacyDevice() {
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE)
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(jpegUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(useCaseExpectedResultMap)
    }

    /**
     * YUV/MAXIMUM
     */
    @Test
    fun canSelectCorrectSize_singleYuvStream_inLegacyDevice() {
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS)
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(yuvUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(useCaseExpectedResultMap)
    }

    /**
     * PRIV/PREVIEW + JPEG/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusJpeg_inLegacyDevice() {
        val privUseCase = createUseCase(CaptureType.PREVIEW)
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE)
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(jpegUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(useCaseExpectedResultMap)
    }

    /**
     * YUV/PREVIEW + JPEG/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_yuvPlusJpeg_inLegacyDevice() {
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE) // JPEG
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(yuvUseCase, PREVIEW_SIZE)
            put(jpegUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(useCaseExpectedResultMap)
    }

    /**
     * PRIV/PREVIEW + PRIV/PREVIEW
     */
    @Test
    fun canSelectCorrectSizes_privPlusPriv_inLegacyDevice() {
        val privUseCase1 = createUseCase(CaptureType.PREVIEW) // PRIV
        val privUseCase2 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase1, PREVIEW_SIZE)
            put(privUseCase2, PREVIEW_SIZE)
        }
        getSuggestedSpecsAndVerify(useCaseExpectedResultMap)
    }

    /**
     * PRIV/PREVIEW + YUV/PREVIEW
     */
    @Test
    fun canSelectCorrectSizes_privPlusYuv_inLegacyDevice() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(yuvUseCase, PREVIEW_SIZE)
        }
        getSuggestedSpecsAndVerify(useCaseExpectedResultMap)
    }

    /**
     * PRIV/PREVIEW + YUV/PREVIEW + JPEG/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusYuvPlusJpeg_inLegacyDevice() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE) // JPEG
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(yuvUseCase, PREVIEW_SIZE)
            put(jpegUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(useCaseExpectedResultMap)
    }

    /**
     * Unsupported PRIV + JPEG + PRIV for legacy level devices
     */
    @Test
    fun throwsException_unsupportedConfiguration_inLegacyDevice() {
        val privUseCase1 = createUseCase(CaptureType.PREVIEW) // PRIV
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE) // JPEG
        val privUseCas2 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase1, RESOLUTION_VGA)
            put(jpegUseCase, RESOLUTION_VGA)
            put(privUseCas2, RESOLUTION_VGA)
        }
        assertThrows(IllegalArgumentException::class.java) {
            getSuggestedSpecsAndVerify(useCaseExpectedResultMap)
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Resolution selection tests for LIMITED-level guaranteed configurations
    //
    // //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * PRIV/PREVIEW + PRIV/RECORD
     */
    @Test
    fun canSelectCorrectSizes_privPlusPriv_inLimitedDevice() {
        val privUseCase1 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val privUseCas2 = createUseCase(CaptureType.PREVIEW) // PRIV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase1, RECORD_SIZE)
            put(privUseCas2, PREVIEW_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
    }

    /**
     * PRIV/PREVIEW + YUV/RECORD
     */
    @Test
    fun canSelectCorrectSizes_privPlusYuv_inLimitedDevice() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(yuvUseCase, RECORD_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
    }

    /**
     * YUV/PREVIEW + YUV/RECORD
     */
    @Test
    fun canSelectCorrectSizes_yuvPlusYuv_inLimitedDevice() {
        val yuvUseCase1 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val yuvUseCase2 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(yuvUseCase1, RECORD_SIZE)
            put(yuvUseCase2, PREVIEW_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
    }

    /**
     * PRIV/PREVIEW + PRIV/RECORD + JPEG/RECORD
     */
    @Test
    fun canSelectCorrectSizes_privPlusPrivPlusJpeg_inLimitedDevice() {
        val privUseCase1 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val privUseCase2 = createUseCase(CaptureType.PREVIEW) // PRIV
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE) // JPEG
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase1, RECORD_SIZE)
            put(privUseCase2, PREVIEW_SIZE)
            put(jpegUseCase, RECORD_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
    }

    /**
     * PRIV/PREVIEW + YUV/RECORD + JPEG/RECORD
     */
    @Test
    fun canSelectCorrectSizes_privPlusYuvPlusJpeg_inLimitedDevice() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE) // JPEG
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(yuvUseCase, RECORD_SIZE)
            put(jpegUseCase, RECORD_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
    }

    /**
     * YUV/PREVIEW + YUV/PREVIEW + JPEG/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_yuvPlusYuvPlusJpeg_inLimitedDevice() {
        val yuvUseCase1 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val yuvUseCase2 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE) // JPEG
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(yuvUseCase1, PREVIEW_SIZE)
            put(yuvUseCase2, PREVIEW_SIZE)
            put(jpegUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
    }

    /**
     * Unsupported YUV + PRIV + YUV for limited level devices
     */
    @Test
    fun throwsException_unsupportedConfiguration_inLimitedDevice() {
        val yuvUseCase1 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase2 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(yuvUseCase1, RESOLUTION_VGA)
            put(privUseCase, RESOLUTION_VGA)
            put(yuvUseCase2, RESOLUTION_VGA)
        }
        assertThrows(IllegalArgumentException::class.java) {
            getSuggestedSpecsAndVerify(
                useCaseExpectedResultMap,
                hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
            )
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Resolution selection tests for FULL-level guaranteed configurations
    //
    // //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * PRIV/PREVIEW + PRIV/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusPriv_inFullDevice() {
        val privUseCase1 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val privUseCase2 = createUseCase(CaptureType.PREVIEW) // PRIV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase1, MAXIMUM_SIZE)
            put(privUseCase2, PREVIEW_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        )
    }

    /**
     * PRIV/PREVIEW + YUV/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusYuv_inFullDevice() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(yuvUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        )
    }

    /**
     * YUV/PREVIEW + YUV/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_yuvPlusYuv_inFullDevice() {
        val yuvUseCase1 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val yuvUseCase2 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(yuvUseCase1, MAXIMUM_SIZE)
            put(yuvUseCase2, PREVIEW_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        )
    }

    /**
     * PRIV/PREVIEW + PRIV/PREVIEW + JPEG/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusPrivPlusJpeg_inFullDevice() {
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE) // JPEG
        val privUseCase1 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val privUseCase2 = createUseCase(CaptureType.PREVIEW) // PRIV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(jpegUseCase, MAXIMUM_SIZE)
            put(privUseCase1, PREVIEW_SIZE)
            put(privUseCase2, PREVIEW_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        )
    }

    /**
     * YUV/VGA + PRIV/PREVIEW + YUV/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_yuvPlusPrivPlusYuv_inFullDevice() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase1 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val yuvUseCase2 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(yuvUseCase1, MAXIMUM_SIZE)
            put(yuvUseCase2, RESOLUTION_VGA)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        )
    }

    /**
     * YUV/VGA + YUV/PREVIEW + YUV/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_yuvPlusYuvPlusYuv_inFullDevice() {
        val yuvUseCase1 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val yuvUseCase2 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val yuvUseCase3 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(yuvUseCase1, MAXIMUM_SIZE)
            put(yuvUseCase2, PREVIEW_SIZE)
            put(yuvUseCase3, RESOLUTION_VGA)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL
        )
    }

    /**
     * Unsupported PRIV + PRIV + YUV + RAW for full level devices
     */
    @Test
    fun throwsException_unsupportedConfiguration_inFullDevice() {
        val privUseCase1 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val privUseCase2 = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase1, RESOLUTION_VGA)
            put(privUseCase2, RESOLUTION_VGA)
            put(yuvUseCase, RESOLUTION_VGA)
            put(rawUseCase, RESOLUTION_VGA)
        }
        assertThrows(IllegalArgumentException::class.java) {
            getSuggestedSpecsAndVerify(
                useCaseExpectedResultMap,
                hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
            )
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Resolution selection tests for Level-3 guaranteed configurations
    //
    // //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * PRIV/PREVIEW + PRIV/VGA + YUV/MAXIMUM + RAW/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusPrivPlusYuvPlusRaw_inLevel3Device() {
        val privUseCase1 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val privUseCase2 = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase1, PREVIEW_SIZE)
            put(privUseCase2, RESOLUTION_VGA)
            put(yuvUseCase, MAXIMUM_SIZE)
            put(rawUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
        )
    }

    /**
     * PRIV/PREVIEW + PRIV/VGA + JPEG/MAXIMUM + RAW/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusPrivPlusJpegPlusRaw_inLevel3Device() {
        val privUseCase1 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val privUseCase2 = createUseCase(CaptureType.PREVIEW) // PRIV
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE) // JPEG
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase1, PREVIEW_SIZE)
            put(privUseCase2, RESOLUTION_VGA)
            put(jpegUseCase, MAXIMUM_SIZE)
            put(rawUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
        )
    }

    /**
     * Unsupported PRIV + YUV + YUV + RAW for level-3 devices
     */
    @Test
    fun throwsException_unsupportedConfiguration_inLevel3Device() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase1 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val yuvUseCase2 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, RESOLUTION_VGA)
            put(yuvUseCase1, RESOLUTION_VGA)
            put(yuvUseCase2, RESOLUTION_VGA)
            put(rawUseCase, RESOLUTION_VGA)
        }
        assertThrows(IllegalArgumentException::class.java) {
            getSuggestedSpecsAndVerify(
                useCaseExpectedResultMap,
                hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_3
            )
        }
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Resolution selection tests for Burst-capability guaranteed configurations
    //
    // //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * PRIV/PREVIEW + PRIV/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusPriv_inLimitedDevice_withBurstCapability() {
        val privUseCase1 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val privUseCase2 = createUseCase(CaptureType.PREVIEW) // PRIV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase1, MAXIMUM_SIZE)
            put(privUseCase2, PREVIEW_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = intArrayOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE
            )
        )
    }

    /**
     * PRIV/PREVIEW + YUV/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusYuv_inLimitedDevice_withBurstCapability() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(yuvUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = intArrayOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE
            )
        )
    }

    /**
     * YUV/PREVIEW + YUV/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_yuvPlusYuv_inLimitedDevice_withBurstCapability() {
        val yuvUseCase1 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val yuvUseCase2 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(yuvUseCase1, MAXIMUM_SIZE)
            put(yuvUseCase2, PREVIEW_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = intArrayOf(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE
            )
        )
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Resolution selection tests for RAW-capability guaranteed configurations
    //
    // //////////////////////////////////////////////////////////////////////////////////////////

    /**
     * RAW/MAX
     */
    @Test
    fun canSelectCorrectSizes_singleRawStream_inLimitedDevice_withRawCapability() {
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(rawUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
    }

    /**
     * PRIV/PREVIEW + RAW/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusRAW_inLimitedDevice_withRawCapability() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(rawUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
    }

    /**
     * PRIV/PREVIEW + PRIV/PREVIEW + RAW/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusPrivPlusRAW_inLimitedDevice_withRawCapability() {
        val privUseCase1 = createUseCase(CaptureType.VIDEO_CAPTURE) // PRIV
        val privUseCase2 = createUseCase(CaptureType.PREVIEW) // PRIV
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase1, PREVIEW_SIZE)
            put(privUseCase2, PREVIEW_SIZE)
            put(rawUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
    }

    /**
     * PRIV/PREVIEW + YUV/PREVIEW + RAW/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusYuvPlusRAW_inLimitedDevice_withRawCapability() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(yuvUseCase, PREVIEW_SIZE)
            put(rawUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
    }

    /**
     * YUV/PREVIEW + YUV/PREVIEW + RAW/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_yuvPlusYuvPlusRAW_inLimitedDevice_withRawCapability() {
        val yuvUseCase1 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val yuvUseCase2 = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(yuvUseCase1, PREVIEW_SIZE)
            put(yuvUseCase2, PREVIEW_SIZE)
            put(rawUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
    }

    /**
     * PRIV/PREVIEW + JPEG/MAXIMUM + RAW/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_privPlusJpegPlusRAW_inLimitedDevice_withRawCapability() {
        val privUseCase = createUseCase(CaptureType.PREVIEW) // PRIV
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE) // JPEG
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(privUseCase, PREVIEW_SIZE)
            put(jpegUseCase, MAXIMUM_SIZE)
            put(rawUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
    }

    /**
     * YUV/PREVIEW + JPEG/MAXIMUM + RAW/MAXIMUM
     */
    @Test
    fun canSelectCorrectSizes_yuvPlusJpegPlusRAW_inLimitedDevice_withRawCapability() {
        val yuvUseCase = createUseCase(CaptureType.IMAGE_ANALYSIS) // YUV
        val jpegUseCase = createUseCase(CaptureType.IMAGE_CAPTURE) // JPEG
        val rawUseCase = createRawUseCase() // RAW
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(yuvUseCase, PREVIEW_SIZE)
            put(jpegUseCase, MAXIMUM_SIZE)
            put(rawUseCase, MAXIMUM_SIZE)
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            capabilities = intArrayOf(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW)
        )
    }

    private fun getSuggestedSpecsAndVerify(
        useCasesExpectedResultMap: Map<UseCase, Size>,
        attachedSurfaceInfoList: List<AttachedSurfaceInfo> = emptyList(),
        hardwareLevel: Int = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
        capabilities: IntArray? = null,
        compareWithAtMost: Boolean = false
    ) {
        setupCameraAndInitCameraX(
            hardwareLevel = hardwareLevel,
            capabilities = capabilities
        )
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )

        val useCaseConfigMap = getUseCaseToConfigMap(useCasesExpectedResultMap.keys.toList())
        val useCaseConfigToOutputSizesMap =
            getUseCaseConfigToOutputSizesMap(useCaseConfigMap.values.toList())
        val suggestedStreamSpecs = supportedSurfaceCombination.getSuggestedStreamSpecifications(
            false,
            attachedSurfaceInfoList,
            useCaseConfigToOutputSizesMap
        )

        useCasesExpectedResultMap.keys.forEach {
            val resultSize = suggestedStreamSpecs[useCaseConfigMap[it]]!!.resolution
            val expectedSize = useCasesExpectedResultMap[it]!!
            if (!compareWithAtMost) {
                assertThat(resultSize).isEqualTo(expectedSize)
            } else {
                assertThat(sizeIsAtMost(resultSize, expectedSize)).isTrue()
            }
        }
    }

    private fun getUseCaseToConfigMap(useCases: List<UseCase>): Map<UseCase, UseCaseConfig<*>> {
        val useCaseConfigMap = mutableMapOf<UseCase, UseCaseConfig<*>>().apply {
            useCases.forEach {
                put(it, it.currentConfig)
            }
        }
        return useCaseConfigMap
    }

    private fun getUseCaseConfigToOutputSizesMap(
        useCaseConfigs: List<UseCaseConfig<*>>
    ): Map<UseCaseConfig<*>, List<Size>> {
        val resultMap = mutableMapOf<UseCaseConfig<*>, List<Size>>().apply {
            useCaseConfigs.forEach {
                put(it, DEFAULT_SUPPORTED_SIZES.toList())
            }
        }

        return resultMap
    }

    /**
     * Helper function that returns whether size is <= maxSize
     *
     */
    private fun sizeIsAtMost(size: Size, maxSize: Size): Boolean {
        return (size.height * size.width) <= (maxSize.height * maxSize.width)
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Resolution selection tests for FPS settings
    //
    // //////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun getSupportedOutputSizes_single_valid_targetFPS() {
        // a valid target means the device is capable of that fps
        val useCase = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(25, 30))
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(useCase, Size(3840, 2160))
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
    }

    @Test
    fun getSupportedOutputSizes_single_invalid_targetFPS() {
        // an invalid target means the device would neve be able to reach that fps
        val useCase = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(65, 70))
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            put(useCase, Size(800, 450))
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED
        )
    }

    @Test
    fun getSupportedOutputSizes_multiple_targetFPS_first_is_larger() {
        // a valid target means the device is capable of that fps
        val useCase1 = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(30, 35))
        val useCase2 = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(15, 25))
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            // both selected size should be no larger than 1920 x 1445
            put(useCase1, Size(1920, 1445))
            put(useCase2, Size(1920, 1445))
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_FULL,
            compareWithAtMost = true
        )
    }

    @Test
    fun getSupportedOutputSizes_multiple_targetFPS_first_is_smaller() {
        // a valid target means the device is capable of that fps
        val useCase1 = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(30, 35))
        val useCase2 = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(45, 50))
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            // both selected size should be no larger than 1920 x 1440
            put(useCase1, Size(1920, 1440))
            put(useCase2, Size(1920, 1440))
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            compareWithAtMost = true
        )
    }

    @Test
    fun getSupportedOutputSizes_multiple_targetFPS_intersect() {
        // first and second new use cases have target fps that intersect each other
        val useCase1 = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(30, 40))
        val useCase2 = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(35, 45))
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            // effective target fps becomes 35-40
            // both selected size should be no larger than 1920 x 1080
            put(useCase1, Size(1920, 1080))
            put(useCase2, Size(1920, 1080))
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            compareWithAtMost = true
        )
    }

    @Test
    fun getSupportedOutputSizes_multiple_cases_first_has_targetFPS() {
        // first new use case has a target fps, second new use case does not
        val useCase1 = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(30, 35))
        val useCase2 = createUseCase(CaptureType.PREVIEW)
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            // both selected size should be no larger than 1920 x 1440
            put(useCase1, Size(1920, 1440))
            put(useCase2, Size(1920, 1440))
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            compareWithAtMost = true
        )
    }

    @Test
    fun getSupportedOutputSizes_multiple_cases_second_has_targetFPS() {
        // second new use case does not have a target fps, first new use case does not
        val useCase1 = createUseCase(CaptureType.PREVIEW)
        val useCase2 = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(30, 35))
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            // both selected size should be no larger than 1920 x 1440
            put(useCase1, Size(1920, 1440))
            put(useCase2, Size(1920, 1440))
        }
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            compareWithAtMost = true
        )
    }

    @Test
    fun getSupportedOutputSizes_attached_with_targetFPS_no_new_targetFPS() {
        // existing surface with target fps + new use case without a target fps
        val useCase = createUseCase(CaptureType.PREVIEW)
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            // size should be no larger than 1280 x 960
            put(useCase, Size(1280, 960))
        }
        // existing surface w/ target fps
        val attachedSurfaceInfo = AttachedSurfaceInfo.create(
            SurfaceConfig.create(
                ConfigType.JPEG,
                ConfigSize.PREVIEW
            ), ImageFormat.JPEG,
            Size(1280, 720), Range(40, 50)
        )
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            attachedSurfaceInfoList = listOf(attachedSurfaceInfo),
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            compareWithAtMost = true
        )
    }

    @Test
    fun getSupportedOutputSizes_attached_with_targetFPS_and_new_targetFPS_no_intersect() {
        // existing surface with target fps + new use case with target fps that does not intersect
        val useCase = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(30, 35))
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            // size of new surface should be no larger than 1280 x 960
            put(useCase, Size(1280, 960))
        }
        // existing surface w/ target fps
        val attachedSurfaceInfo = AttachedSurfaceInfo.create(
            SurfaceConfig.create(
                ConfigType.JPEG,
                ConfigSize.PREVIEW
            ), ImageFormat.JPEG,
            Size(1280, 720), Range(40, 50)
        )
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            attachedSurfaceInfoList = listOf(attachedSurfaceInfo),
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            compareWithAtMost = true
        )
    }

    @Test
    fun getSupportedOutputSizes_attached_with_targetFPS_and_new_targetFPS_with_intersect() {
        // existing surface with target fps + new use case with target fps that intersect each other
        val useCase = createUseCase(CaptureType.PREVIEW, targetFrameRate = Range<Int>(45, 50))
        val useCaseExpectedResultMap = mutableMapOf<UseCase, Size>().apply {
            // size of new surface should be no larger than 1280 x 720
            put(useCase, Size(1280, 720))
        }
        // existing surface w/ target fps
        val attachedSurfaceInfo = AttachedSurfaceInfo.create(
            SurfaceConfig.create(
                ConfigType.JPEG,
                ConfigSize.PREVIEW
            ), ImageFormat.JPEG,
            Size(1280, 720), Range(40, 50)
        )
        getSuggestedSpecsAndVerify(
            useCaseExpectedResultMap,
            attachedSurfaceInfoList = listOf(attachedSurfaceInfo),
            hardwareLevel = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LIMITED,
            compareWithAtMost = true
        )
    }

    // //////////////////////////////////////////////////////////////////////////////////////////
    //
    // Other tests
    //
    // //////////////////////////////////////////////////////////////////////////////////////////

    @Test
    fun getMaximumSizeForImageFormat() {
        setupCameraAndInitCameraX()
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, DEFAULT_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        val maximumYUVSize =
            supportedSurfaceCombination.getMaxOutputSizeByFormat(ImageFormat.YUV_420_888)
        assertThat(maximumYUVSize).isEqualTo(MAXIMUM_SIZE)
        val maximumJPEGSize = supportedSurfaceCombination.getMaxOutputSizeByFormat(ImageFormat.JPEG)
        assertThat(maximumJPEGSize).isEqualTo(MAXIMUM_SIZE)
    }

    @Test
    fun determineRecordSizeFromStreamConfigurationMap() {
        // Setup camera with non-integer camera Id
        setupCameraAndInitCameraX(cameraId = EXTERNAL_CAMERA_ID)
        val supportedSurfaceCombination = SupportedSurfaceCombination(
            context, EXTERNAL_CAMERA_ID, cameraManagerCompat!!, mockCamcorderProfileHelper
        )
        // Checks the determined RECORD size
        assertThat(
            supportedSurfaceCombination.mSurfaceSizeDefinition.recordSize
        ).isEqualTo(
            LEGACY_VIDEO_MAXIMUM_SIZE
        )
    }

    /**
     * Sets up camera according to the specified settings and initialize [CameraX].
     *
     * @param cameraId the camera id to be set up. Default value is [DEFAULT_CAMERA_ID].
     * @param hardwareLevel the hardware level of the camera. Default value is
     * [CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY].
     * @param sensorOrientation the sensor orientation of the camera. Default value is
     * [SENSOR_ORIENTATION_90].
     * @param pixelArraySize the active pixel array size of the camera. Default value is
     * [LANDSCAPE_PIXEL_ARRAY_SIZE].
     * @param supportedSizes the supported sizes of the camera. Default value is
     * [DEFAULT_SUPPORTED_SIZES].
     * @param capabilities the capabilities of the camera. Default value is null.
     */
    private fun setupCameraAndInitCameraX(
        cameraId: String = DEFAULT_CAMERA_ID,
        hardwareLevel: Int = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
        sensorOrientation: Int = SENSOR_ORIENTATION_90,
        pixelArraySize: Size = LANDSCAPE_PIXEL_ARRAY_SIZE,
        supportedSizes: Array<Size> = DEFAULT_SUPPORTED_SIZES,
        supportedHighResolutionSizes: Array<Size>? = null,
        capabilities: IntArray? = null
    ) {
        setupCamera(
            cameraId,
            hardwareLevel,
            sensorOrientation,
            pixelArraySize,
            supportedSizes,
            supportedHighResolutionSizes,
            capabilities
        )

        @LensFacing val lensFacingEnum = CameraUtil.getLensFacingEnumFromInt(
            CameraCharacteristics.LENS_FACING_BACK
        )
        cameraManagerCompat = CameraManagerCompat.from(context)
        val cameraInfo = FakeCameraInfoInternal(
            cameraId,
            sensorOrientation,
            CameraCharacteristics.LENS_FACING_BACK
        ).apply {
            encoderProfilesProvider = FakeEncoderProfilesProvider.Builder()
                .add(QUALITY_2160P, profileUhd)
                .add(QUALITY_1080P, profileFhd)
                .add(QUALITY_720P, profileHd)
                .add(QUALITY_480P, profileSd)
                .build()
        }

        cameraFactory = FakeCameraFactory().apply {
            insertCamera(lensFacingEnum, cameraId) {
                FakeCamera(cameraId, null, cameraInfo)
            }
        }

        initCameraX()
    }

    /**
     * Sets up camera according to the specified settings.
     *
     * @param cameraId the camera id to be set up. Default value is [DEFAULT_CAMERA_ID].
     * @param hardwareLevel the hardware level of the camera. Default value is
     * [CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY].
     * @param sensorOrientation the sensor orientation of the camera. Default value is
     * [SENSOR_ORIENTATION_90].
     * @param pixelArraySize the active pixel array size of the camera. Default value is
     * [LANDSCAPE_PIXEL_ARRAY_SIZE].
     * @param supportedSizes the supported sizes of the camera. Default value is
     * [DEFAULT_SUPPORTED_SIZES].
     * @param capabilities the capabilities of the camera. Default value is null.
     */
    fun setupCamera(
        cameraId: String = DEFAULT_CAMERA_ID,
        hardwareLevel: Int = CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY,
        sensorOrientation: Int = SENSOR_ORIENTATION_90,
        pixelArraySize: Size = LANDSCAPE_PIXEL_ARRAY_SIZE,
        supportedSizes: Array<Size> = DEFAULT_SUPPORTED_SIZES,
        supportedHighResolutionSizes: Array<Size>? = null,
        capabilities: IntArray? = null
    ) {
        val mockMap = Mockito.mock(StreamConfigurationMap::class.java).also {
            // Sets up the supported sizes
            Mockito.`when`(it.getOutputSizes(ArgumentMatchers.anyInt()))
                .thenReturn(supportedSizes)
            // ImageFormat.PRIVATE was supported since API level 23. Before that, the supported
            // output sizes need to be retrieved via SurfaceTexture.class.
            Mockito.`when`(it.getOutputSizes(SurfaceTexture::class.java))
                .thenReturn(supportedSizes)
            // This is setup for the test to determine RECORD size from StreamConfigurationMap
            Mockito.`when`(it.getOutputSizes(MediaRecorder::class.java))
                .thenReturn(supportedSizes)

            // setup to return different minimum frame durations depending on resolution
            // minimum frame durations were designated only for the purpose of testing
            Mockito.`when`(it.getOutputMinFrameDuration(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.eq(Size(4032, 3024))
            ))
                .thenReturn(50000000L) // 20 fps, size maximum

            Mockito.`when`(it.getOutputMinFrameDuration(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.eq(Size(3840, 2160))
            ))
                .thenReturn(40000000L) // 25, size record

            Mockito.`when`(it.getOutputMinFrameDuration(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.eq(Size(1920, 1440))
            ))
                .thenReturn(30000000L) // 30

            Mockito.`when`(it.getOutputMinFrameDuration(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.eq(Size(1920, 1080))
            ))
                .thenReturn(28000000L) // 35

            Mockito.`when`(it.getOutputMinFrameDuration(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.eq(Size(1280, 960))
            ))
                .thenReturn(25000000L) // 40

            Mockito.`when`(it.getOutputMinFrameDuration(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.eq(Size(1280, 720))
            ))
                .thenReturn(22000000L) // 45, size preview/display

            Mockito.`when`(it.getOutputMinFrameDuration(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.eq(Size(960, 544))
            ))
                .thenReturn(20000000L) // 50

            Mockito.`when`(it.getOutputMinFrameDuration(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.eq(Size(800, 450))
            ))
                .thenReturn(16666000L) // 60fps

            Mockito.`when`(it.getOutputMinFrameDuration(
                ArgumentMatchers.anyInt(),
                ArgumentMatchers.eq(Size(640, 480))
            ))
                .thenReturn(16666000L) // 60fps

            // Sets up the supported high resolution sizes
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Mockito.`when`(it.getHighResolutionOutputSizes(ArgumentMatchers.anyInt()))
                    .thenReturn(supportedHighResolutionSizes)
            }
        }

        val characteristics = ShadowCameraCharacteristics.newCameraCharacteristics()
        Shadow.extract<ShadowCameraCharacteristics>(characteristics).apply {
            set(CameraCharacteristics.LENS_FACING, CameraCharacteristics.LENS_FACING_BACK)
            set(CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL, hardwareLevel)
            set(CameraCharacteristics.SENSOR_ORIENTATION, sensorOrientation)
            set(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE, pixelArraySize)
            set(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP, mockMap)
            capabilities?.let {
                set(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES, it)
            }
        }

        val cameraManager = ApplicationProvider.getApplicationContext<Context>()
            .getSystemService(Context.CAMERA_SERVICE) as CameraManager
        (Shadow.extract<Any>(cameraManager) as ShadowCameraManager)
            .addCamera(cameraId, characteristics)
    }

    /**
     * Initializes the [CameraX].
     */
    private fun initCameraX() {
        val surfaceManagerProvider =
            CameraDeviceSurfaceManager.Provider { context, _, availableCameraIds ->
                cameraDeviceSurfaceManager = Camera2DeviceSurfaceManager(
                    context,
                    mockCamcorderProfileHelper,
                    CameraManagerCompat.from(this@SupportedSurfaceCombinationTest.context),
                    availableCameraIds
                )
                cameraDeviceSurfaceManager
            }
        val cameraXConfig = CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            .setDeviceSurfaceManagerProvider(surfaceManagerProvider)
            .setCameraFactoryProvider { _, _, _ -> cameraFactory!! }
            .build()
        val cameraX: CameraX = try {
            CameraXUtil.getOrCreateInstance(context) { cameraXConfig }.get()
        } catch (e: ExecutionException) {
            throw IllegalStateException("Unable to initialize CameraX for test.")
        } catch (e: InterruptedException) {
            throw IllegalStateException("Unable to initialize CameraX for test.")
        }
        useCaseConfigFactory = cameraX.defaultConfigFactory
    }

    private fun isAllSubConfigListSupported(
        isConcurrentCameraModeOn: Boolean,
        supportedSurfaceCombination: SupportedSurfaceCombination,
        combinationList: List<SurfaceCombination>
    ): Boolean {
        combinationList.forEach { combination ->
            val configList = combination.surfaceConfigList
            val length = configList.size
            if (length <= 1) {
                return@forEach
            }
            for (index in 0 until length) {
                val subConfigurationList = arrayListOf<SurfaceConfig>().apply {
                    addAll(configList)
                    removeAt(index)
                }
                if (!supportedSurfaceCombination.checkSupported(
                        isConcurrentCameraModeOn, subConfigurationList
                    )
                ) {
                    return false
                }
            }
        }
        return true
    }

    private fun createUseCase(
        captureType: CaptureType,
        targetFrameRate: Range<Int>? = null
    ): UseCase {
        val builder = FakeUseCaseConfig.Builder(captureType, when (captureType) {
            CaptureType.IMAGE_CAPTURE -> ImageFormat.JPEG
            CaptureType.IMAGE_ANALYSIS -> ImageFormat.YUV_420_888
            else -> INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE
        })
        targetFrameRate?.let {
            builder.mutableConfig.insertOption(UseCaseConfig.OPTION_TARGET_FRAME_RATE, it)
        }
        return builder.build()
    }

    private fun createRawUseCase(): UseCase {
        val builder = FakeUseCaseConfig.Builder()
        builder.mutableConfig.insertOption(
            UseCaseConfig.OPTION_INPUT_FORMAT,
            ImageFormat.RAW_SENSOR
        )
        return builder.build()
    }
}

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

package androidx.camera.camera2.pipe.integration

import android.hardware.camera2.CameraCharacteristics
import android.media.CamcorderProfile
import android.media.EncoderProfiles.VideoProfile.HDR_NONE
import android.media.EncoderProfiles.VideoProfile.YUV_420
import android.os.Build
import androidx.camera.camera2.pipe.CameraId
import androidx.camera.camera2.pipe.CameraPipe
import androidx.camera.camera2.pipe.integration.adapter.EncoderProfilesProviderAdapter
import androidx.camera.camera2.pipe.integration.compat.StreamConfigurationMapCompat
import androidx.camera.camera2.pipe.integration.compat.quirk.CameraQuirks
import androidx.camera.camera2.pipe.integration.compat.quirk.DeviceQuirks
import androidx.camera.camera2.pipe.integration.compat.quirk.InvalidVideoProfilesQuirk
import androidx.camera.camera2.pipe.integration.compat.workaround.OutputSizesCorrector
import androidx.camera.core.CameraSelector
import androidx.camera.core.impl.EncoderProfilesProxy.VideoProfileProxy.BIT_DEPTH_8
import androidx.camera.testing.impl.CameraUtil
import androidx.camera.testing.impl.LabTestRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@SmallTest
@SdkSuppress(minSdkVersion = 21)
class EncoderProfilesProviderAdapterDeviceTest(
    private val quality: Int,
) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data(): Array<Array<Int>> =
            arrayOf(
                arrayOf(CamcorderProfile.QUALITY_LOW),
                arrayOf(CamcorderProfile.QUALITY_HIGH),
                arrayOf(CamcorderProfile.QUALITY_QCIF),
                arrayOf(CamcorderProfile.QUALITY_CIF),
                arrayOf(CamcorderProfile.QUALITY_480P),
                arrayOf(CamcorderProfile.QUALITY_720P),
                arrayOf(CamcorderProfile.QUALITY_1080P),
                arrayOf(CamcorderProfile.QUALITY_QVGA),
                arrayOf(CamcorderProfile.QUALITY_2160P),
                arrayOf(CamcorderProfile.QUALITY_VGA),
                arrayOf(CamcorderProfile.QUALITY_4KDCI),
                arrayOf(CamcorderProfile.QUALITY_QHD),
                arrayOf(CamcorderProfile.QUALITY_2K)
            )
    }

    private lateinit var encoderProfilesProvider: EncoderProfilesProviderAdapter
    private var cameraId = ""
    private var intCameraId = -1

    @get:Rule val useCamera = CameraUtil.grantCameraPermissionAndPreTestAndPostTest()

    @get:Rule val labTestRule = LabTestRule()

    @Before
    fun setup() {
        Assume.assumeTrue(CameraUtil.hasCameraWithLensFacing(CameraSelector.LENS_FACING_BACK))

        cameraId = CameraUtil.getCameraIdWithLensFacing(CameraSelector.LENS_FACING_BACK)!!
        intCameraId = cameraId.toInt()
        setUptEncoderProfileProvider()
    }

    private fun setUptEncoderProfileProvider() {
        val cameraPipe = CameraPipe(CameraPipe.Config(ApplicationProvider.getApplicationContext()))
        val cameraMetadata = cameraPipe.cameras().awaitCameraMetadata(CameraId(cameraId))!!
        val streamConfigurationMap =
            cameraMetadata[CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP]
        val cameraQuirks =
            CameraQuirks(
                cameraMetadata,
                StreamConfigurationMapCompat(
                    streamConfigurationMap,
                    OutputSizesCorrector(cameraMetadata, streamConfigurationMap)
                )
            )
        encoderProfilesProvider = EncoderProfilesProviderAdapter(cameraId, cameraQuirks.quirks)
    }

    @Test
    fun notHasProfile_getReturnNull() {
        Assume.assumeTrue(!CamcorderProfile.hasProfile(intCameraId, quality))

        assertThat(encoderProfilesProvider.getAll(quality)).isNull()
    }

    @Suppress("DEPRECATION")
    @Test
    fun hasSameContentAsCamcorderProfile() {
        Assume.assumeTrue(encoderProfilesProvider.hasProfile(quality))

        val profile = CamcorderProfile.get(quality)
        val encoderProfiles = encoderProfilesProvider.getAll(quality)
        val videoProfile = encoderProfiles!!.videoProfiles[0]
        val audioProfile = encoderProfiles.audioProfiles[0]

        assertThat(encoderProfiles.defaultDurationSeconds).isEqualTo(profile.duration)
        assertThat(encoderProfiles.recommendedFileFormat).isEqualTo(profile.fileFormat)
        assertThat(videoProfile.codec).isEqualTo(profile.videoCodec)
        assertThat(videoProfile.bitrate).isEqualTo(profile.videoBitRate)
        assertThat(videoProfile.frameRate).isEqualTo(profile.videoFrameRate)
        assertThat(videoProfile.width).isEqualTo(profile.videoFrameWidth)
        assertThat(videoProfile.height).isEqualTo(profile.videoFrameHeight)
        assertThat(audioProfile.codec).isEqualTo(profile.audioCodec)
        assertThat(audioProfile.bitrate).isEqualTo(profile.audioBitRate)
        assertThat(audioProfile.sampleRate).isEqualTo(profile.audioSampleRate)
        assertThat(audioProfile.channels).isEqualTo(profile.audioChannels)
    }

    @SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
    @Test
    fun api31Api32_hasSameContentAsEncoderProfiles() {
        Assume.assumeTrue(encoderProfilesProvider.hasProfile(quality))

        val profiles = CamcorderProfile.getAll(cameraId, quality)
        val video = profiles!!.videoProfiles[0]
        Assume.assumeTrue(video != null)
        val audio = profiles.audioProfiles[0]
        val profilesProxy = encoderProfilesProvider.getAll(quality)
        val videoProxy = profilesProxy!!.videoProfiles[0]
        val audioProxy = profilesProxy.audioProfiles[0]

        assertThat(profilesProxy.defaultDurationSeconds).isEqualTo(profiles.defaultDurationSeconds)
        assertThat(profilesProxy.recommendedFileFormat).isEqualTo(profiles.recommendedFileFormat)
        assertThat(videoProxy.codec).isEqualTo(video.codec)
        assertThat(videoProxy.mediaType).isEqualTo(video.mediaType)
        assertThat(videoProxy.bitrate).isEqualTo(video.bitrate)
        assertThat(videoProxy.frameRate).isEqualTo(video.frameRate)
        assertThat(videoProxy.width).isEqualTo(video.width)
        assertThat(videoProxy.height).isEqualTo(video.height)
        assertThat(videoProxy.profile).isEqualTo(video.profile)
        assertThat(videoProxy.bitDepth).isEqualTo(BIT_DEPTH_8)
        assertThat(videoProxy.chromaSubsampling).isEqualTo(YUV_420)
        assertThat(videoProxy.hdrFormat).isEqualTo(HDR_NONE)
        assertThat(audioProxy.codec).isEqualTo(audio.codec)
        assertThat(audioProxy.mediaType).isEqualTo(audio.mediaType)
        assertThat(audioProxy.bitrate).isEqualTo(audio.bitrate)
        assertThat(audioProxy.sampleRate).isEqualTo(audio.sampleRate)
        assertThat(audioProxy.channels).isEqualTo(audio.channels)
        assertThat(audioProxy.profile).isEqualTo(audio.profile)
    }

    @SdkSuppress(minSdkVersion = 33)
    @Test
    fun afterApi33_hasSameContentAsEncoderProfiles() {
        Assume.assumeTrue(encoderProfilesProvider.hasProfile(quality))

        val profiles = CamcorderProfile.getAll(cameraId, quality)
        val video = profiles!!.videoProfiles[0]
        Assume.assumeTrue(video != null)
        val audio = profiles.audioProfiles[0]
        val profilesProxy = encoderProfilesProvider.getAll(quality)
        val videoProxy = profilesProxy!!.videoProfiles[0]
        val audioProxy = profilesProxy.audioProfiles[0]

        assertThat(profilesProxy.defaultDurationSeconds).isEqualTo(profiles.defaultDurationSeconds)
        assertThat(profilesProxy.recommendedFileFormat).isEqualTo(profiles.recommendedFileFormat)
        assertThat(videoProxy.codec).isEqualTo(video.codec)
        assertThat(videoProxy.mediaType).isEqualTo(video.mediaType)
        assertThat(videoProxy.bitrate).isEqualTo(video.bitrate)
        assertThat(videoProxy.frameRate).isEqualTo(video.frameRate)
        assertThat(videoProxy.width).isEqualTo(video.width)
        assertThat(videoProxy.height).isEqualTo(video.height)
        assertThat(videoProxy.profile).isEqualTo(video.profile)
        assertThat(videoProxy.bitDepth).isEqualTo(video.bitDepth)
        assertThat(videoProxy.chromaSubsampling).isEqualTo(video.chromaSubsampling)
        assertThat(videoProxy.hdrFormat).isEqualTo(video.hdrFormat)
        assertThat(audioProxy.codec).isEqualTo(audio.codec)
        assertThat(audioProxy.mediaType).isEqualTo(audio.mediaType)
        assertThat(audioProxy.bitrate).isEqualTo(audio.bitrate)
        assertThat(audioProxy.sampleRate).isEqualTo(audio.sampleRate)
        assertThat(audioProxy.channels).isEqualTo(audio.channels)
        assertThat(audioProxy.profile).isEqualTo(audio.profile)
    }

    @LabTestRule.LabTestOnly
    @SdkSuppress(minSdkVersion = 31)
    @Test
    fun detectNullVideoProfile() {
        Assume.assumeTrue(CamcorderProfile.hasProfile(intCameraId, quality))
        skipTestOnDevicesWithProblematicBuild()
        val profiles = CamcorderProfile.getAll(cameraId, quality)!!
        assertThat(profiles.videoProfiles[0]).isNotNull()
    }

    private fun skipTestOnDevicesWithProblematicBuild() {
        // Skip test for b/265613005, b/223439995 and b/277174217
        val hasVideoProfilesQuirk = DeviceQuirks[InvalidVideoProfilesQuirk::class.java] != null
        Assume.assumeFalse(
            "Skip test with null VideoProfile issue. Unable to test.",
            hasVideoProfilesQuirk || isProblematicCuttlefishBuild()
        )
    }

    private fun isProblematicCuttlefishBuild(): Boolean {
        return Build.MODEL.contains("Cuttlefish", true) &&
            (Build.ID.startsWith("TP1A", true) || Build.ID.startsWith("TSE4", true))
    }
}

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

package androidx.camera.video

import android.media.CamcorderProfile
import android.os.Build
import android.util.Size
import androidx.camera.core.DynamicRange
import androidx.camera.core.DynamicRange.BIT_DEPTH_10_BIT
import androidx.camera.core.DynamicRange.BIT_DEPTH_8_BIT
import androidx.camera.core.DynamicRange.BIT_DEPTH_UNSPECIFIED
import androidx.camera.core.DynamicRange.ENCODING_DOLBY_VISION
import androidx.camera.core.DynamicRange.ENCODING_HDR_UNSPECIFIED
import androidx.camera.core.DynamicRange.ENCODING_UNSPECIFIED
import androidx.camera.core.DynamicRange.HDR10_10_BIT
import androidx.camera.core.DynamicRange.HDR_UNSPECIFIED_10_BIT
import androidx.camera.core.DynamicRange.HLG_10_BIT
import androidx.camera.core.DynamicRange.SDR
import androidx.camera.core.DynamicRange.UNSPECIFIED
import androidx.camera.core.impl.EncoderProfilesProxy.VideoProfileProxy
import androidx.camera.testing.EncoderProfilesUtil.PROFILES_2160P
import androidx.camera.testing.EncoderProfilesUtil.PROFILES_720P
import androidx.camera.testing.EncoderProfilesUtil.RESOLUTION_2160P
import androidx.camera.testing.EncoderProfilesUtil.RESOLUTION_720P
import androidx.camera.testing.fakes.FakeCameraInfoInternal
import androidx.camera.testing.fakes.FakeEncoderProfilesProvider
import androidx.camera.video.Quality.FHD
import androidx.camera.video.Quality.HD
import androidx.camera.video.Quality.HIGHEST
import androidx.camera.video.Quality.LOWEST
import androidx.camera.video.Quality.SD
import androidx.camera.video.Quality.UHD
import androidx.camera.video.internal.VideoValidatedEncoderProfilesProxy
import androidx.core.util.component1
import androidx.core.util.component2
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.internal.DoNotInstrument

private val UNSPECIFIED_8_BIT = DynamicRange(ENCODING_UNSPECIFIED, BIT_DEPTH_8_BIT)
private val UNSPECIFIED_10_BIT = DynamicRange(ENCODING_UNSPECIFIED, BIT_DEPTH_10_BIT)
private val HDR_UNSPECIFIED = DynamicRange(ENCODING_HDR_UNSPECIFIED, BIT_DEPTH_UNSPECIFIED)
private val DOLBY_VISION_UNSPECIFIED = DynamicRange(ENCODING_DOLBY_VISION, BIT_DEPTH_UNSPECIFIED)

@RunWith(RobolectricTestRunner::class)
@DoNotInstrument
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
class RecorderVideoCapabilitiesTest {

    private val defaultProfilesProvider = FakeEncoderProfilesProvider.Builder()
        .add(CamcorderProfile.QUALITY_HIGH, PROFILES_2160P) // UHD (2160p) per above definition
        .add(CamcorderProfile.QUALITY_2160P, PROFILES_2160P) // UHD (2160p)
        .add(CamcorderProfile.QUALITY_720P, PROFILES_720P) // HD (720p)
        .add(CamcorderProfile.QUALITY_LOW, PROFILES_720P) // HD (720p) per above definition
        .build()
    private val defaultDynamicRanges = setOf(SDR, HLG_10_BIT)
    private val cameraInfo = FakeCameraInfoInternal().apply {
        encoderProfilesProvider = defaultProfilesProvider
        supportedDynamicRanges = defaultDynamicRanges
    }

    private val fakeValidator: (VideoProfileProxy) -> VideoProfileProxy = {
        // Just returns the input video profile.
        it
    }
    private val validatedProfiles2160p = VideoValidatedEncoderProfilesProxy.from(PROFILES_2160P)
    private val validatedProfiles720p = VideoValidatedEncoderProfilesProxy.from(PROFILES_720P)
    private val videoCapabilities = RecorderVideoCapabilities(cameraInfo, fakeValidator)

    @Test
    fun canGetSupportedDynamicRanges() {
        assertThat(videoCapabilities.supportedDynamicRanges).containsExactly(SDR, HLG_10_BIT)
    }

    @Test
    fun hasSupportedQualities_sdr() {
        assertThat(videoCapabilities.getSupportedQualities(SDR)).containsExactly(HD, UHD)
    }

    @Test
    fun hasSupportedQualities_hlg10() {
        assertThat(videoCapabilities.getSupportedQualities(HLG_10_BIT)).containsExactly(HD, UHD)
    }

    @Test
    fun hasSupportedQualities_hdr10() {
        assertThat(videoCapabilities.getSupportedQualities(HDR10_10_BIT)).isEmpty()
    }

    @Test
    fun hasSupportedQualities_unspecified() {
        assertThat(videoCapabilities.getSupportedQualities(UNSPECIFIED)).containsExactly(HD, UHD)
    }

    @Test
    fun hasSupportedQualities_hdrUnspecified() {
        assertThat(videoCapabilities.getSupportedQualities(HDR_UNSPECIFIED))
            .containsExactly(HD, UHD)
    }

    @Test
    fun hasSupportedQualities_hdrUnspecified10Bit() {
        assertThat(videoCapabilities.getSupportedQualities(HDR_UNSPECIFIED_10_BIT))
            .containsExactly(HD, UHD)
    }

    @Test
    fun hasSupportedQualities_unspecified8Bit() {
        assertThat(videoCapabilities.getSupportedQualities(UNSPECIFIED_8_BIT))
            .containsExactly(HD, UHD)
    }

    @Test
    fun hasSupportedQualities_unspecified10Bit() {
        assertThat(videoCapabilities.getSupportedQualities(UNSPECIFIED_10_BIT))
            .containsExactly(HD, UHD)
    }

    @Test
    fun hasSupportedQualities_dolbyVisionUnspecified() {
        assertThat(videoCapabilities.getSupportedQualities(DOLBY_VISION_UNSPECIFIED)).isEmpty()
    }

    @Test
    fun isQualitySupported_sdr() {
        assertThat(videoCapabilities.isQualitySupported(HIGHEST, SDR)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(LOWEST, SDR)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(UHD, SDR)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(FHD, SDR)).isFalse()
        assertThat(videoCapabilities.isQualitySupported(HD, SDR)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(SD, SDR)).isFalse()
    }

    @Test
    fun isQualitySupported_unspecified() {
        assertThat(videoCapabilities.isQualitySupported(HIGHEST, UNSPECIFIED)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(LOWEST, UNSPECIFIED)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(UHD, UNSPECIFIED)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(FHD, UNSPECIFIED)).isFalse()
        assertThat(videoCapabilities.isQualitySupported(HD, UNSPECIFIED)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(SD, UNSPECIFIED)).isFalse()
    }

    @Test
    fun isQualitySupported_hlg10WithBackupProfile() {
        assertThat(videoCapabilities.isQualitySupported(HIGHEST, HLG_10_BIT)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(LOWEST, HLG_10_BIT)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(UHD, HLG_10_BIT)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(FHD, HLG_10_BIT)).isFalse()
        assertThat(videoCapabilities.isQualitySupported(HD, HLG_10_BIT)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(SD, HLG_10_BIT)).isFalse()
    }

    @Test
    fun isQualitySupported_hdrUnspecified10BitWithBackupProfile() {
        assertThat(videoCapabilities.isQualitySupported(HIGHEST, HDR_UNSPECIFIED_10_BIT)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(LOWEST, HDR_UNSPECIFIED_10_BIT)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(UHD, HDR_UNSPECIFIED_10_BIT)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(FHD, HDR_UNSPECIFIED_10_BIT)).isFalse()
        assertThat(videoCapabilities.isQualitySupported(HD, HDR_UNSPECIFIED_10_BIT)).isTrue()
        assertThat(videoCapabilities.isQualitySupported(SD, HDR_UNSPECIFIED_10_BIT)).isFalse()
    }

    @Test
    fun canGetSameSdrProfile() {
        assertThat(videoCapabilities.getProfiles(HIGHEST, SDR)).isEqualTo(validatedProfiles2160p)
        assertThat(videoCapabilities.getProfiles(LOWEST, SDR)).isEqualTo(validatedProfiles720p)
        assertThat(videoCapabilities.getProfiles(UHD, SDR)).isEqualTo(validatedProfiles2160p)
        assertThat(videoCapabilities.getProfiles(FHD, SDR)).isNull()
        assertThat(videoCapabilities.getProfiles(HD, SDR)).isEqualTo(validatedProfiles720p)
        assertThat(videoCapabilities.getProfiles(SD, SDR)).isNull()
    }

    @Test
    fun canGetNonNullHdrUnspecifiedBackupProfile_whenSdrProfileExisted() {
        assertThat(videoCapabilities.getProfiles(HIGHEST, HDR_UNSPECIFIED_10_BIT)).isNotNull()
        assertThat(videoCapabilities.getProfiles(LOWEST, HDR_UNSPECIFIED_10_BIT)).isNotNull()
        assertThat(videoCapabilities.getProfiles(UHD, HDR_UNSPECIFIED_10_BIT)).isNotNull()
        assertThat(videoCapabilities.getProfiles(FHD, HDR_UNSPECIFIED_10_BIT)).isNull()
        assertThat(videoCapabilities.getProfiles(HD, HDR_UNSPECIFIED_10_BIT)).isNotNull()
        assertThat(videoCapabilities.getProfiles(SD, HDR_UNSPECIFIED_10_BIT)).isNull()
    }

    @Test
    fun canGetNonNullHlg10BackupProfile_whenSdrProfileExisted() {
        assertThat(videoCapabilities.getProfiles(HIGHEST, HLG_10_BIT)).isNotNull()
        assertThat(videoCapabilities.getProfiles(LOWEST, HLG_10_BIT)).isNotNull()
        assertThat(videoCapabilities.getProfiles(UHD, HLG_10_BIT)).isNotNull()
        assertThat(videoCapabilities.getProfiles(FHD, HLG_10_BIT)).isNull()
        assertThat(videoCapabilities.getProfiles(HD, HLG_10_BIT)).isNotNull()
        assertThat(videoCapabilities.getProfiles(SD, HLG_10_BIT)).isNull()
    }

    @Test
    fun findHighestSupportedQuality_returnsHigherQuality() {
        // Create a size between 720p and 2160p
        val (width720p, height720p) = RESOLUTION_720P
        val inBetweenSize = Size(width720p + 10, height720p)

        assertThat(videoCapabilities.findHighestSupportedQualityFor(inBetweenSize, SDR))
            .isEqualTo(UHD)
    }

    @Test
    fun findHighestSupportedQuality_returnsHighestQuality_whenAboveHighest() {
        // Create a size between greater than the max quality (UHD)
        val (width2160p, height2160p) = RESOLUTION_2160P
        val aboveHighestSize = Size(width2160p + 10, height2160p)

        assertThat(videoCapabilities.findHighestSupportedQualityFor(aboveHighestSize, SDR))
            .isEqualTo(UHD)
    }

    @Test
    fun findHighestSupportedQuality_returnsLowestQuality_whenBelowLowest() {
        // Create a size below the lowest quality (HD)
        val (width720p, height720p) = RESOLUTION_720P
        val belowLowestSize = Size(width720p - 10, height720p)

        assertThat(videoCapabilities.findHighestSupportedQualityFor(belowLowestSize, SDR))
            .isEqualTo(HD)
    }

    @Test
    fun findHighestSupportedQuality_returnsExactQuality_whenExactSizeGiven() {
        val exactSize720p = RESOLUTION_720P

        assertThat(videoCapabilities.findHighestSupportedQualityFor(exactSize720p, SDR))
            .isEqualTo(HD)
    }

    @Test
    fun findHighestSupportedEncoderProfilesFor_returnsHigherProfile() {
        // Create a size between 720p and 2160p
        val (width720p, height720p) = RESOLUTION_720P
        val inBetweenSize = Size(width720p + 10, height720p)

        assertThat(videoCapabilities.findHighestSupportedEncoderProfilesFor(inBetweenSize, SDR))
            .isEqualTo(validatedProfiles2160p)
    }

    @Test
    fun findHighestSupportedEncoderProfilesFor_returnsHighestProfile_whenAboveHighest() {
        // Create a size between greater than the max quality (UHD)
        val (width2160p, height2160p) = RESOLUTION_2160P
        val aboveHighestSize = Size(width2160p + 10, height2160p)

        assertThat(videoCapabilities.findHighestSupportedEncoderProfilesFor(aboveHighestSize, SDR))
            .isEqualTo(validatedProfiles2160p)
    }

    @Test
    fun findHighestSupportedEncoderProfilesFor_returnsLowestProfile_whenBelowLowest() {
        // Create a size below the lowest quality (HD)
        val (width720p, height720p) = RESOLUTION_720P
        val belowLowestSize = Size(width720p - 10, height720p)

        assertThat(videoCapabilities.findHighestSupportedEncoderProfilesFor(belowLowestSize, SDR))
            .isEqualTo(validatedProfiles720p)
    }

    @Test
    fun findHighestSupportedEncoderProfilesFor_returnsExactProfile_whenExactSizeGiven() {
        val exactSize720p = RESOLUTION_720P

        assertThat(videoCapabilities.findHighestSupportedEncoderProfilesFor(exactSize720p, SDR))
            .isEqualTo(validatedProfiles720p)
    }
}
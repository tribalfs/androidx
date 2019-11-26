/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.camera.camera2.impl;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;

import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CaptureRequest;
import android.os.Build;

import androidx.annotation.experimental.UseExperimental;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.CameraCaptureCallback;
import androidx.camera.core.CaptureConfig;
import androidx.camera.core.ImageCapture;
import androidx.test.filters.SmallTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.internal.DoNotInstrument;

@SmallTest
@RunWith(RobolectricTestRunner.class)
@DoNotInstrument
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
public final class Camera2CaptureOptionUnpackerTest {

    private Camera2CaptureOptionUnpacker mUnpacker;

    @Before
    public void setUp() {
        mUnpacker = Camera2CaptureOptionUnpacker.INSTANCE;
    }

    @Test
    @UseExperimental(markerClass = ExperimentalCamera2Interop.class)
    public void unpackerExtractsCaptureCallbacks() {
        ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder();
        CaptureCallback captureCallback = mock(CaptureCallback.class);

        new Camera2Interop.Extender<>(imageCaptureBuilder)
                .setSessionCaptureCallback(captureCallback);

        CaptureConfig.Builder captureBuilder = new CaptureConfig.Builder();
        mUnpacker.unpack(imageCaptureBuilder.getUseCaseConfig(), captureBuilder);
        CaptureConfig captureConfig = captureBuilder.build();

        CameraCaptureCallback cameraCaptureCallback =
                captureConfig.getCameraCaptureCallbacks().get(0);
        assertThat(((CaptureCallbackContainer) cameraCaptureCallback).getCaptureCallback())
                .isEqualTo(captureCallback);
    }

    @Test
    @UseExperimental(markerClass = ExperimentalCamera2Interop.class)
    public void unpackerExtractsOptions() {
        ImageCapture.Builder imageCaptureConfigBuilder = new ImageCapture.Builder();

        // Add 2 options to ensure that multiple options can be unpacked.
        new Camera2Interop.Extender<>(imageCaptureConfigBuilder)
                .setCaptureRequestOption(
                        CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
                .setCaptureRequestOption(
                        CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH);

        CaptureConfig.Builder captureBuilder = new CaptureConfig.Builder();
        mUnpacker.unpack(imageCaptureConfigBuilder.getUseCaseConfig(), captureBuilder);
        CaptureConfig captureConfig = captureBuilder.build();

        Camera2ImplConfig config = new Camera2ImplConfig(captureConfig.getImplementationOptions());

        assertThat(config.getCaptureRequestOption(
                CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF))
                .isEqualTo(CaptureRequest.CONTROL_AF_MODE_AUTO);
        assertThat(config.getCaptureRequestOption(
                CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF))
                .isEqualTo(CaptureRequest.FLASH_MODE_TORCH);
    }
}

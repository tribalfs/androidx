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

package androidx.camera.camera2;

import android.hardware.camera2.CameraDevice;
import android.util.Log;

import androidx.camera.core.CameraFactory;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.ConfigurationProvider;
import androidx.camera.core.ImageCaptureUseCase;
import androidx.camera.core.ImageCaptureUseCaseConfiguration;
import androidx.camera.core.SessionConfiguration;

/** Provides defaults for {@link ImageCaptureUseCaseConfiguration} in the Camera2 implementation. */
final class DefaultImageCaptureConfigurationProvider
        implements ConfigurationProvider<ImageCaptureUseCaseConfiguration> {
    private static final String TAG = "DefaultImageCaptureConfigurationProvider";

    private final CameraFactory cameraFactory;

    public DefaultImageCaptureConfigurationProvider(CameraFactory cameraFactory) {
        this.cameraFactory = cameraFactory;
    }

    @Override
    public ImageCaptureUseCaseConfiguration getConfiguration() {
        ImageCaptureUseCaseConfiguration.Builder builder =
                ImageCaptureUseCaseConfiguration.Builder.fromConfig(
                        ImageCaptureUseCase.DEFAULT_CONFIG.getConfiguration());

        // SessionConfiguration containing all intrinsic properties needed for ImageCaptureUseCase
        SessionConfiguration.Builder sessionBuilder = new SessionConfiguration.Builder();
        // TODO(b/114762170): Must set to preview here until we allow for multiple template types
        sessionBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);

        // Add options to UseCaseConfiguration
        builder.setDefaultSessionConfiguration(sessionBuilder.build());
        builder.setOptionUnpacker(Camera2OptionUnpacker.INSTANCE);

        // Add default lensFacing if we can
        try {
            String defaultId = cameraFactory.cameraIdForLensFacing(LensFacing.BACK);
            if (defaultId != null) {
                builder.setLensFacing(LensFacing.BACK);
            } else {
                defaultId = cameraFactory.cameraIdForLensFacing(LensFacing.FRONT);
                if (defaultId != null) {
                    builder.setLensFacing(LensFacing.FRONT);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to determine default lens facing for ImageCaptureUseCase.", e);
        }

        return builder.build();
    }
}

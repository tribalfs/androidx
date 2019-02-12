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
import androidx.camera.core.SessionConfiguration;
import androidx.camera.core.ViewFinderUseCase;
import androidx.camera.core.ViewFinderUseCaseConfiguration;

/** Provides defaults for {@link ViewFinderUseCaseConfiguration} in the Camera2 implementation. */
final class DefaultViewFinderConfigurationProvider
        implements ConfigurationProvider<ViewFinderUseCaseConfiguration> {
    private static final String TAG = "DefaultViewFinderConfigurationProvider";

    private final CameraFactory mCameraFactory;

    DefaultViewFinderConfigurationProvider(CameraFactory cameraFactory) {
        mCameraFactory = cameraFactory;
    }

    @Override
    public ViewFinderUseCaseConfiguration getConfiguration() {
        ViewFinderUseCaseConfiguration.Builder builder =
                ViewFinderUseCaseConfiguration.Builder.fromConfig(
                        ViewFinderUseCase.DEFAULT_CONFIG.getConfiguration());

        // SessionConfiguration containing all intrinsic properties needed for ViewFinderUseCase
        SessionConfiguration.Builder sessionBuilder = new SessionConfiguration.Builder();
        sessionBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);

        // Add options to UseCaseConfiguration
        builder.setDefaultSessionConfiguration(sessionBuilder.build());
        builder.setOptionUnpacker(Camera2OptionUnpacker.INSTANCE);

        // Add default lensFacing if we can
        try {
            String defaultId = mCameraFactory.cameraIdForLensFacing(LensFacing.BACK);
            if (defaultId != null) {
                builder.setLensFacing(LensFacing.BACK);
            } else {
                defaultId = mCameraFactory.cameraIdForLensFacing(LensFacing.FRONT);
                if (defaultId != null) {
                    builder.setLensFacing(LensFacing.FRONT);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Unable to determine default lens facing for ViewFinderUseCase.", e);
        }

        return builder.build();
    }
}

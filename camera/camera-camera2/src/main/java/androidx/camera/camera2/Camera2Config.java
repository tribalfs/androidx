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

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.camera.camera2.internal.Camera2CameraFactory;
import androidx.camera.camera2.internal.Camera2DeviceSurfaceManager;
import androidx.camera.camera2.internal.ImageAnalysisConfigProvider;
import androidx.camera.camera2.internal.ImageCaptureConfigProvider;
import androidx.camera.camera2.internal.PreviewConfigProvider;
import androidx.camera.camera2.internal.VideoCaptureConfigProvider;
import androidx.camera.core.CameraFactory;
import androidx.camera.core.CameraXConfig;
import androidx.camera.core.ExtendableUseCaseConfigFactory;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.PreviewConfig;
import androidx.camera.core.UseCaseConfigFactory;
import androidx.camera.core.VideoCaptureConfig;
import androidx.camera.core.impl.CameraDeviceSurfaceManager;
import androidx.core.util.Preconditions;

/**
 * Convenience class for generating a pre-populated Camera2 {@link CameraXConfig}.
 */
public final class Camera2Config {

    private Camera2Config() {
    }

    /**
     * Creates a {@link CameraXConfig} containing the default Camera2 implementation for CameraX.
     */
    @NonNull
    public static CameraXConfig defaultConfig(@NonNull Context c) {
        Preconditions.checkNotNull(c);

        // Create the camera factory for creating Camera2 camera objects
        CameraFactory cameraFactory = new Camera2CameraFactory(c);

        // Create the DeviceSurfaceManager for Camera2
        CameraDeviceSurfaceManager.Provider surfaceManagerProvider =
                Camera2DeviceSurfaceManager::new;

        // Create default configuration factory
        UseCaseConfigFactory.Provider configFactoryProvider = context -> {
            ExtendableUseCaseConfigFactory factory = new ExtendableUseCaseConfigFactory();
            factory.installDefaultProvider(
                    ImageAnalysisConfig.class,
                    new ImageAnalysisConfigProvider(context));
            factory.installDefaultProvider(
                    ImageCaptureConfig.class,
                    new ImageCaptureConfigProvider(context));
            factory.installDefaultProvider(
                    VideoCaptureConfig.class,
                    new VideoCaptureConfigProvider(context));
            factory.installDefaultProvider(
                    PreviewConfig.class, new PreviewConfigProvider(context));
            return factory;
        };

        CameraXConfig.Builder appConfigBuilder =
                new CameraXConfig.Builder()
                        .setCameraFactory(cameraFactory)
                        .setDeviceSurfaceManagerProvider(surfaceManagerProvider)
                        .setUseCaseConfigFactoryProvider(configFactoryProvider);

        return appConfigBuilder.build();
    }
}

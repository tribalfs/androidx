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

package androidx.camera.core;

import android.hardware.camera2.CameraCaptureSession.StateCallback;
import android.os.Handler;

import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;

import com.google.auto.value.AutoValue;

import java.util.List;

/**
 * Surface configuration type and size pair
 *
 * <p>{@link android.hardware.camera2.CameraDevice#createCaptureSession} defines the default
 * guaranteed stream combinations for different hardware level devices. It defines what combination
 * of surface configuration type and size pairs can be supported for different hardware level camera
 * devices.
 *
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
@AutoValue
public abstract class SurfaceConfiguration {
    /** Prevent sublcassing */
    SurfaceConfiguration() {
    }

    public static SurfaceConfiguration create(ConfigurationType type, ConfigurationSize size) {
        return new AutoValue_SurfaceConfiguration(type, size);
    }

    public abstract ConfigurationType getConfigurationType();

    public abstract ConfigurationSize getConfigurationSize();

    /**
     * Check whether the input surface configuration has a smaller size than this object and can be
     * supported
     *
     * @param surfaceConfiguration the surface configuration to be compared
     * @return the check result that whether it could be supported
     */
    public final boolean isSupported(SurfaceConfiguration surfaceConfiguration) {
        boolean isSupported = false;
        ConfigurationType configurationType = surfaceConfiguration.getConfigurationType();
        ConfigurationSize configurationSize = surfaceConfiguration.getConfigurationSize();

        // Check size and type to make sure it could be supported
        if (configurationSize.id <= getConfigurationSize().id
                && configurationType == getConfigurationType()) {
            isSupported = true;
        }
        return isSupported;
    }

    /**
     * The Camera2 configuration type for the surface.
     *
     * <p>These are the enumerations defined in {@link
     * android.hardware.camera2.CameraDevice#createCaptureSession(List, StateCallback, Handler)}.
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public enum ConfigurationType {
        PRIV,
        YUV,
        JPEG,
        RAW
    }

    /**
     * The Camera2 stream sizes for the surface.
     *
     * <p>These are the enumerations defined in {@link
     * android.hardware.camera2.CameraDevice#createCaptureSession(List, StateCallback, Handler)}.
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public enum ConfigurationSize {
        /** Default AYALYSIS size is 640x480. */
        ANALYSIS(0),
        /**
         * PREVIEW refers to the best size match to the device's screen resolution, or to 1080p
         * (1920x1080), whichever is smaller.
         */
        PREVIEW(1),
        /**
         * RECORD refers to the camera device's maximum supported recording resolution, as
         * determined by CamcorderProfile.
         */
        RECORD(2),
        /**
         * MAXIMUM refers to the camera device's maximum output resolution for that format or target
         * from StreamConfigurationMap.getOutputSizes(int)
         */
        MAXIMUM(3),
        /** NOT_SUPPORT is for the size larger than MAXIMUM */
        NOT_SUPPORT(4);

        final int id;

        ConfigurationSize(int id) {
            this.id = id;
        }
    }
}

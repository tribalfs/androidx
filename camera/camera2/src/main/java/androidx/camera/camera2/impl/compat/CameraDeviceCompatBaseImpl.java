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

package androidx.camera.camera2.impl.compat;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.os.Handler;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.camera.camera2.impl.compat.params.OutputConfigurationCompat;
import androidx.camera.camera2.impl.compat.params.SessionConfigurationCompat;
import androidx.camera.core.impl.utils.MainThreadAsyncHandler;
import androidx.core.util.Preconditions;

import java.util.ArrayList;
import java.util.List;

@RequiresApi(21)
class CameraDeviceCompatBaseImpl implements CameraDeviceCompat.CameraDeviceCompatImpl {
    static List<Surface> unpackSurfaces(@NonNull List<OutputConfigurationCompat> outputConfigs) {
        List<Surface> surfaces = new ArrayList<>(outputConfigs.size());
        for (OutputConfigurationCompat outputConfig : outputConfigs) {
            surfaces.add(outputConfig.getSurface());
        }

        return surfaces;
    }

    static void checkPreconditions(CameraDevice device, SessionConfigurationCompat config) {
        Preconditions.checkNotNull(device);
        Preconditions.checkNotNull(config);
        Preconditions.checkNotNull(config.getStateCallback());

        List<OutputConfigurationCompat> outputConfigs = config.getOutputConfigurations();
        if (outputConfigs == null) {
            throw new IllegalArgumentException("Invalid output configurations");
        }
        if (config.getExecutor() == null) {
            throw new IllegalArgumentException("Invalid executor");
        }
    }

    void createBaseCaptureSession(@NonNull CameraDevice device, @NonNull List<Surface> surfaces,
            @NonNull CameraCaptureSession.StateCallback cb, @NonNull Handler handler)
            throws CameraAccessException {
        device.createCaptureSession(surfaces, cb, handler);
    }

    @Override
    public void createCaptureSession(@NonNull CameraDevice device,
            @NonNull SessionConfigurationCompat config) throws CameraAccessException {
        checkPreconditions(device, config);

        if (config.getInputConfiguration() != null) {
            throw new IllegalArgumentException("Reprocessing sessions not supported until API 23");
        }

        if (config.getSessionType() == SessionConfigurationCompat.SESSION_HIGH_SPEED) {
            throw new IllegalArgumentException(
                    "High speed capture sessions not supported until API 23");
        }

        // Wrap the executor in the callback
        CameraCaptureSession.StateCallback cb = new CameraDeviceCompat.StateCallbackExecutorWrapper(
                config.getExecutor(), config.getStateCallback());

        // Convert the OutputConfigurations to surfaces
        List<Surface> surfaces = unpackSurfaces(config.getOutputConfigurations());

        createBaseCaptureSession(device, surfaces, cb, MainThreadAsyncHandler.getInstance());
    }


}

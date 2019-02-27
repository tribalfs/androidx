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

package androidx.camera.extensions;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraX;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.Configuration;
import androidx.camera.core.Configuration.Option;
import androidx.camera.core.ViewFinderUseCaseConfiguration;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Provides interfaces that OEM need to implement to enable extension function in ViewFinder.
 */
public abstract class ViewFinderUseCaseExtender {
    private static final String TAG = "ViewFinderExtender";
    private final ViewFinderUseCaseConfiguration.Builder mBuilder;
    protected ViewFinderUseCaseExtender mImpl;

    public ViewFinderUseCaseExtender(ViewFinderUseCaseConfiguration.Builder builder) {
        mBuilder = builder;
    }

    boolean loadImplementation(String className) {
        try {
            final Class<?> ViewFinderClass = Class.forName(className);
            Constructor<?> ViewFinderConstructor =
                    ViewFinderClass.getDeclaredConstructor(
                            ViewFinderUseCaseConfiguration.Builder.class);
            mImpl = (ViewFinderUseCaseExtender) ViewFinderConstructor.newInstance(mBuilder);
        } catch (ClassNotFoundException
                | NoSuchMethodException
                | InstantiationException
                | InvocationTargetException
                | IllegalAccessException e) {
            Log.e(TAG, "Failed to load view finder extension with exception: " + e);
        }

        if (mImpl == null) {
            mImpl = new DefaultViewFinderUseCaseExtender(mBuilder);
            return false;
        }

        return true;
    }

    /**
     * Indicates whether extension function can support with
     * {@link ViewFinderUseCaseConfiguration.Builder}
     *
     * @return True if the specific extension function is supported for the camera device.
     */
    public boolean isExtensionAvailable() {
        LensFacing lensFacing = mBuilder.build().getLensFacing();
        String cameraId;
        try {
            cameraId = CameraX.getCameraWithLensFacing(lensFacing);
        } catch (CameraInfoUnavailableException e) {
            throw new IllegalArgumentException(
                    "Unable to attach to camera with LensFacing " + lensFacing, e);
        }

        Context context = CameraX.getContext();
        CameraManager cameraManager = (CameraManager) context.getSystemService(
                Context.CAMERA_SERVICE);
        CameraCharacteristics cameraCharacteristics = null;
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            throw new IllegalArgumentException(
                    "Unable to retrieve info for camera with id " + cameraId + ".", e);
        }

        return isExtensionAvailable(cameraId, cameraCharacteristics);
    }

    protected boolean isExtensionAvailable(String cameraId,
            CameraCharacteristics cameraCharacteristics) {
        return mImpl.isExtensionAvailable(cameraId, cameraCharacteristics);
    }

    /** Enable the extension if available. If not available then acts a no-op. */
    public void enableExtension() {
        mImpl.enableExtension();
    }

    protected void setCaptureStage(@NonNull CaptureStage captureStage) {
        Configuration configuration =
                captureStage.getCaptureRequestConfiguration().getImplementationOptions();

        for (Option<?> option : configuration.listOptions()) {
            @SuppressWarnings("unchecked") // Options/values are being copied directly
                    Option<Object> objectOpt = (Option<Object>) option;
            mBuilder.insertOption(objectOpt, configuration.retrieveOption(objectOpt));
        }
    }
}

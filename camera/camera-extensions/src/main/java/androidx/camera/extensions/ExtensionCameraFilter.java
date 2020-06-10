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

package androidx.camera.extensions;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.experimental.UseExperimental;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraFilter;
import androidx.camera.core.ExperimentalCameraFilter;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.extensions.impl.ImageCaptureExtenderImpl;
import androidx.camera.extensions.impl.PreviewExtenderImpl;
import androidx.core.util.Preconditions;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A filter that filters camera based on extender implementation. If the implementation is
 * unavailable, the camera will be considered available.
 */
@UseExperimental(markerClass = ExperimentalCameraFilter.class)
public final class ExtensionCameraFilter implements CameraFilter {
    private PreviewExtenderImpl mPreviewExtenderImpl;
    private ImageCaptureExtenderImpl mImageCaptureExtenderImpl;

    ExtensionCameraFilter(@Nullable PreviewExtenderImpl previewExtenderImpl) {
        mPreviewExtenderImpl = previewExtenderImpl;
        mImageCaptureExtenderImpl = null;
    }

    ExtensionCameraFilter(@Nullable ImageCaptureExtenderImpl imageCaptureExtenderImpl) {
        mPreviewExtenderImpl = null;
        mImageCaptureExtenderImpl = imageCaptureExtenderImpl;
    }

    ExtensionCameraFilter(@Nullable PreviewExtenderImpl previewExtenderImpl,
            @Nullable ImageCaptureExtenderImpl imageCaptureExtenderImpl) {
        mPreviewExtenderImpl = previewExtenderImpl;
        mImageCaptureExtenderImpl = imageCaptureExtenderImpl;
    }

    @Override
    public void filter(@NonNull LinkedHashSet<Camera> cameras) {
        Set<Camera> resultCameras = new LinkedHashSet<>();
        for (Camera camera : cameras) {
            Preconditions.checkState(camera instanceof CameraInternal,
                    "The camera doesn't contain internal implementation.");
            String cameraId = ((CameraInternal) camera).getCameraInfoInternal().getCameraId();

            boolean available = true;

            // If preview extender impl isn't null, check if the camera id is supported.
            if (mPreviewExtenderImpl != null) {
                available =
                        mPreviewExtenderImpl.isExtensionAvailable(cameraId,
                                CameraUtil.getCameraCharacteristics(cameraId));
            }
            // If image capture extender impl isn't null, check if the camera id is supported.
            if (mImageCaptureExtenderImpl != null) {
                available = mImageCaptureExtenderImpl.isExtensionAvailable(cameraId,
                        CameraUtil.getCameraCharacteristics(cameraId));
            }

            if (available) {
                resultCameras.add(camera);
            }
        }
        cameras.retainAll(resultCameras);
    }
}

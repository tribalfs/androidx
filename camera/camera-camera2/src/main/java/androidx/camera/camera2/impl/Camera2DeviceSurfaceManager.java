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

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.CamcorderProfile;
import android.util.Rational;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.VisibleForTesting;
import androidx.camera.core.CameraDeviceConfig;
import androidx.camera.core.CameraDeviceSurfaceManager;
import androidx.camera.core.CameraX;
import androidx.camera.core.SurfaceConfig;
import androidx.camera.core.UseCase;
import androidx.camera.core.UseCaseConfig;
import androidx.core.util.Preconditions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Camera device manager to provide the guaranteed supported stream capabilities related info for
 * all camera devices
 *
 * <p>{@link android.hardware.camera2.CameraDevice#createCaptureSession} defines the default
 * guaranteed stream combinations for different hardware level devices. It defines what combination
 * of surface configuration type and size pairs can be supported for different hardware level camera
 * devices. This structure is used to store the guaranteed supported stream capabilities related
 * info.
 *
 * @hide
 */
@RestrictTo(Scope.LIBRARY)
public final class Camera2DeviceSurfaceManager implements CameraDeviceSurfaceManager {
    private static final String TAG = "Camera2DeviceSurfaceManager";
    private static final Size MAXIMUM_PREVIEW_SIZE = new Size(1920, 1080);
    private final Map<String, SupportedSurfaceCombination> mCameraSupportedSurfaceCombinationMap =
            new HashMap<>();
    private final Context mContext;
    private final CamcorderProfileHelper mCamcorderProfileHelper;
    private boolean mIsInitialized = false;

    public Camera2DeviceSurfaceManager(@NonNull Context context) {
        this(context, new CamcorderProfileHelper() {
            @Override
            public boolean hasProfile(int cameraId, int quality) {
                return CamcorderProfile.hasProfile(cameraId, quality);
            }
        });
    }

    @VisibleForTesting
    Camera2DeviceSurfaceManager(@NonNull Context context,
            @NonNull CamcorderProfileHelper camcorderProfileHelper) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(camcorderProfileHelper);
        mContext = context.getApplicationContext();
        mCamcorderProfileHelper = camcorderProfileHelper;
    }

    /**
     * Prepare necessary resources for the surface manager.
     */
    @Override
    public void init() {
        if (!mIsInitialized) {
            CameraManager cameraManager =
                    (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);

            try {
                for (String cameraId : cameraManager.getCameraIdList()) {
                    mCameraSupportedSurfaceCombinationMap.put(
                            cameraId,
                            new SupportedSurfaceCombination(
                                    mContext, cameraId, mCamcorderProfileHelper));
                }
            } catch (CameraAccessException e) {
                throw new IllegalArgumentException("Fail to get camera id list", e);
            }

            mIsInitialized = true;
        }
    }

    /**
     * Check whether surface manager is initialized.
     *
     * @return true if initialized
     */
    @Override
    public boolean isInitialized() {
        return mIsInitialized;
    }

    /**
     * Check whether the input surface configuration list is under the capability of any combination
     * of this object.
     *
     * @param cameraId          the camera id of the camera device to be compared
     * @param surfaceConfigList the surface configuration list to be compared
     * @return the check result that whether it could be supported
     * @throws IllegalStateException if not initialized
     */
    @Override
    public boolean checkSupported(
            @NonNull String cameraId, @Nullable List<SurfaceConfig> surfaceConfigList) {
        checkInitialized();

        if (surfaceConfigList == null || surfaceConfigList.isEmpty()) {
            return true;
        }

        SupportedSurfaceCombination supportedSurfaceCombination =
                mCameraSupportedSurfaceCombinationMap.get(cameraId);

        boolean isSupported = false;
        if (supportedSurfaceCombination != null) {
            isSupported = supportedSurfaceCombination.checkSupported(surfaceConfigList);
        }

        return isSupported;
    }

    /**
     * Transform to a SurfaceConfig object with cameraId, image format and size info
     *
     * @param cameraId    the camera id of the camera device to transform the object
     * @param imageFormat the image format info for the surface configuration object
     * @param size        the size info for the surface configuration object
     * @return new {@link SurfaceConfig} object
     * @throws IllegalStateException if not initialized
     */
    @Nullable
    @Override
    public SurfaceConfig transformSurfaceConfig(@NonNull String cameraId, int imageFormat,
            @NonNull Size size) {
        checkInitialized();

        SupportedSurfaceCombination supportedSurfaceCombination =
                mCameraSupportedSurfaceCombinationMap.get(cameraId);

        SurfaceConfig surfaceConfig = null;
        if (supportedSurfaceCombination != null) {
            surfaceConfig =
                    supportedSurfaceCombination.transformSurfaceConfig(imageFormat, size);
        }

        return surfaceConfig;
    }

    /**
     * Retrieves a map of suggested resolutions for the given list of use cases.
     *
     * @param cameraId         the camera id of the camera device used by the use cases
     * @param originalUseCases list of use cases with existing surfaces
     * @param newUseCases      list of new use cases
     * @return map of suggested resolutions for given use cases
     * @throws IllegalStateException if not initialized
     */
    @NonNull
    @Override
    public Map<UseCase, Size> getSuggestedResolutions(
            @NonNull String cameraId,
            @Nullable List<UseCase> originalUseCases,
            @NonNull List<UseCase> newUseCases) {
        checkInitialized();
        Preconditions.checkNotNull(newUseCases, "No new use cases to be bound.");
        Preconditions.checkArgument(!newUseCases.isEmpty(), "No new use cases to be bound.");

        UseCaseSurfaceOccupancyManager.checkUseCaseLimitNotExceeded(originalUseCases, newUseCases);

        // Use the small size (640x480) for new use cases to check whether there is any possible
        // supported combination first
        List<SurfaceConfig> surfaceConfigs = new ArrayList<>();

        if (originalUseCases != null) {
            for (UseCase useCase : originalUseCases) {
                String useCaseCameraId = getCameraIdFromConfig(useCase.getUseCaseConfig());
                Size resolution = useCase.getAttachedSurfaceResolution(useCaseCameraId);

                surfaceConfigs.add(
                        transformSurfaceConfig(cameraId, useCase.getImageFormat(), resolution));
            }
        }

        for (UseCase useCase : newUseCases) {
            surfaceConfigs.add(
                    transformSurfaceConfig(cameraId, useCase.getImageFormat(), new Size(640, 480)));
        }

        SupportedSurfaceCombination supportedSurfaceCombination =
                mCameraSupportedSurfaceCombinationMap.get(cameraId);

        if (supportedSurfaceCombination == null
                || !supportedSurfaceCombination.checkSupported(surfaceConfigs)) {
            throw new IllegalArgumentException(
                    "No supported surface combination is found for camera device - Id : "
                            + cameraId + ".  May be attempting to bind too many use cases.");
        }

        return supportedSurfaceCombination.getSuggestedResolutions(originalUseCases, newUseCases);
    }

    /**
     * Get max supported output size for specific camera device and image format
     *
     * @param cameraId    the camera Id
     * @param imageFormat the image format info
     * @return the max supported output size for the image format
     * @throws IllegalStateException if not initialized
     */
    @NonNull
    @Override
    public Size getMaxOutputSize(@NonNull String cameraId, int imageFormat) {
        checkInitialized();

        SupportedSurfaceCombination supportedSurfaceCombination =
                mCameraSupportedSurfaceCombinationMap.get(cameraId);

        if (supportedSurfaceCombination == null) {
            throw new IllegalArgumentException(
                    "Fail to find supported surface info - CameraId:" + cameraId);
        }

        return supportedSurfaceCombination.getMaxOutputSizeByFormat(imageFormat);
    }

    /**
     * Retrieves the preview size, choosing the smaller of the display size and 1080P.
     *
     * @return preview size from {@link androidx.camera.core.SurfaceSizeDefinition}
     * @throws IllegalStateException if not initialized
     */
    @NonNull
    @Override
    public Size getPreviewSize() {
        checkInitialized();

        // 1920x1080 is maximum preview size
        Size previewSize = MAXIMUM_PREVIEW_SIZE;

        if (!mCameraSupportedSurfaceCombinationMap.isEmpty()) {
            // Preview size depends on the display size and 1080P. Therefore, we can get the first
            // camera
            // device's preview size to return it.
            String cameraId = (String) mCameraSupportedSurfaceCombinationMap.keySet().toArray()[0];
            previewSize =
                    mCameraSupportedSurfaceCombinationMap
                            .get(cameraId)
                            .getSurfaceSizeDefinition()
                            .getPreviewSize();
        }

        return previewSize;
    }

    /**
     * Checks whether the use case requires a corrected aspect ratio due to device constraints.
     *
     * @param useCaseConfig to check aspect ratio
     * @return the check result that whether aspect ratio need to be corrected
     * @throws IllegalStateException if not initialized
     */
    @Override
    public boolean requiresCorrectedAspectRatio(@NonNull UseCaseConfig<?> useCaseConfig) {
        checkInitialized();

        String cameraId = getCameraIdFromConfig(useCaseConfig);
        SupportedSurfaceCombination supportedSurfaceCombination =
                mCameraSupportedSurfaceCombinationMap.get(cameraId);

        if (supportedSurfaceCombination == null) {
            throw new IllegalArgumentException(
                    "Fail to find supported surface info - CameraId:" + cameraId);
        }
        return supportedSurfaceCombination.requiresCorrectedAspectRatio();
    }

    /**
     * Returns the corrected aspect ratio for the given use case configuration or {@code null} if
     * no correction is needed.
     *
     * @param useCaseConfig to check aspect ratio
     * @return the corrected aspect ratio for the use case
     * @throws IllegalStateException if not initialized
     */
    @Nullable
    @Override
    public Rational getCorrectedAspectRatio(@NonNull UseCaseConfig<?> useCaseConfig) {
        checkInitialized();

        String cameraId = getCameraIdFromConfig(useCaseConfig);
        SupportedSurfaceCombination supportedSurfaceCombination =
                mCameraSupportedSurfaceCombinationMap.get(cameraId);

        if (supportedSurfaceCombination == null) {
            throw new IllegalArgumentException(
                    "Fail to find supported surface info - CameraId:" + cameraId);
        }
        return supportedSurfaceCombination.getCorrectedAspectRatio(useCaseConfig);
    }

    private String getCameraIdFromConfig(UseCaseConfig<?> useCaseConfig) {
        CameraDeviceConfig config = (CameraDeviceConfig) useCaseConfig;
        String cameraId;
        try {
            CameraX.LensFacing lensFacing = config.getLensFacing(null);
            // Adds default lensFacing if the user doesn't specify the lens facing.
            if (lensFacing == null) {
                lensFacing = CameraX.getDefaultLensFacing();
            }
            cameraId = CameraX.getCameraWithLensFacing(lensFacing);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unable to get camera ID for use case " + useCaseConfig.getTargetName(), e);
        }
        return cameraId;
    }

    private void checkInitialized() {
        Preconditions.checkState(mIsInitialized, "CameraDeviceSurfaceManager is not initialized.");
    }

    enum Operation {
        ADD_CONFIG,
        REMOVE_CONFIG
    }
}

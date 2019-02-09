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
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraManager;
import android.media.CamcorderProfile;
import android.support.annotation.RestrictTo;
import android.support.annotation.RestrictTo.Scope;
import android.support.annotation.VisibleForTesting;
import android.util.Size;
import androidx.camera.core.BaseUseCase;
import androidx.camera.core.CameraDeviceConfiguration;
import androidx.camera.core.CameraDeviceSurfaceManager;
import androidx.camera.core.CameraX;
import androidx.camera.core.SurfaceConfiguration;
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
 * @hide Implementation detail
 */
final class Camera2DeviceSurfaceManager implements CameraDeviceSurfaceManager {
  private static final String TAG = "Camera2DeviceSurfaceManager";
  private static final Size MAXIMUM_PREVIEW_SIZE = new Size(1920, 1080);
  private boolean isInitialized = false;
  private final Map<String, SupportedSurfaceCombination> cameraSupportedSurfaceCombinationMap =
      new HashMap<>();

  enum Operation {
    ADD_CONFIG,
    REMOVE_CONFIG
  }

  public Camera2DeviceSurfaceManager(Context context) {
    init(context, CamcorderProfile::hasProfile);
  }

  @VisibleForTesting
  Camera2DeviceSurfaceManager(Context context, CamcorderProfileHelper camcorderProfileHelper) {
    init(context, camcorderProfileHelper);
  }

  /**
   * Check whether the input surface configuration list is under the capability of any combination
   * of this object.
   *
   * @param cameraId the camera id of the camera device to be compared
   * @param surfaceConfigurationList the surface configuration list to be compared
   * @return the check result that whether it could be supported
   */
  @Override
  public boolean checkSupported(
      String cameraId, List<SurfaceConfiguration> surfaceConfigurationList) {
    boolean isSupported = false;

    if (!isInitialized) {
      throw new IllegalStateException("Camera2DeviceSurfaceManager is not initialized.");
    }

    if (surfaceConfigurationList == null || surfaceConfigurationList.isEmpty()) {
      return true;
    }

    SupportedSurfaceCombination supportedSurfaceCombination =
        cameraSupportedSurfaceCombinationMap.get(cameraId);

    if (supportedSurfaceCombination != null) {
      isSupported = supportedSurfaceCombination.checkSupported(surfaceConfigurationList);
    }

    return isSupported;
  }

  /**
   * Transform to a SurfaceConfiguration object with cameraId, image format and size info
   *
   * @param cameraId the camera id of the camera device to transform the object
   * @param imageFormat the image format info for the surface configuration object
   * @param size the size info for the surface configuration object
   * @return new {@link SurfaceConfiguration} object
   */
  @Override
  public SurfaceConfiguration transformSurfaceConfiguration(
      String cameraId, int imageFormat, Size size) {
    SurfaceConfiguration surfaceConfiguration = null;

    if (!isInitialized) {
      throw new IllegalStateException("Camera2DeviceSurfaceManager is not initialized.");
    }

    SupportedSurfaceCombination supportedSurfaceCombination =
        cameraSupportedSurfaceCombinationMap.get(cameraId);

    if (supportedSurfaceCombination != null) {
      surfaceConfiguration =
          supportedSurfaceCombination.transformSurfaceConfiguration(imageFormat, size);
    }

    return surfaceConfiguration;
  }

  /**
   * Retrieves a map of suggested resolutions for the given list of use cases.
   *
   * @param cameraId the camera id of the camera device used by the use cases
   * @param originalUseCases list of use cases with existing surfaces
   * @param newUseCases list of new use cases
   * @return map of suggested resolutions for given use cases
   */
  @Override
  public Map<BaseUseCase, Size> getSuggestedResolutions(
      String cameraId, List<BaseUseCase> originalUseCases, List<BaseUseCase> newUseCases) {

    if (newUseCases == null || newUseCases.isEmpty()) {
      throw new IllegalArgumentException("No new use cases to be bound.");
    }

    UseCaseSurfaceOccupancyManager.checkUseCaseLimitNotExceeded(originalUseCases, newUseCases);

    // Use the small size (640x480) for new use cases to check whether there is any possible
    // supported combination first
    List<SurfaceConfiguration> surfaceConfigurations = new ArrayList<>();

    if (originalUseCases != null) {
      for (BaseUseCase useCase : originalUseCases) {
        CameraDeviceConfiguration configuration =
            (CameraDeviceConfiguration) useCase.getUseCaseConfiguration();
        String useCaseCameraId;
        try {
          useCaseCameraId = CameraX.getCameraWithLensFacing(configuration.getLensFacing());
        } catch (Exception e) {
          throw new IllegalArgumentException(
              "Unable to get camera ID for use case " + useCase.getName(), e);
        }
        Size resolution = useCase.getAttachedSurfaceResolution(useCaseCameraId);

        surfaceConfigurations.add(
            transformSurfaceConfiguration(cameraId, useCase.getImageFormat(), resolution));
      }
    }

    for (BaseUseCase useCase : newUseCases) {
      surfaceConfigurations.add(
          transformSurfaceConfiguration(
              cameraId, useCase.getImageFormat(), new Size(640, 480)));
    }

    SupportedSurfaceCombination supportedSurfaceCombination =
        cameraSupportedSurfaceCombinationMap.get(cameraId);

    if (supportedSurfaceCombination == null
        || !supportedSurfaceCombination.checkSupported(surfaceConfigurations)) {
      throw new IllegalArgumentException(
          "No supported surface combination is found for camera device - Id : " + cameraId);
    }

    return supportedSurfaceCombination.getSuggestedResolutions(originalUseCases, newUseCases);
  }

  private void init(Context context, CamcorderProfileHelper camcorderProfileHelper) {
    if (!isInitialized) {
      CameraManager cameraManager =
          (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

      try {
        for (String cameraId : cameraManager.getCameraIdList()) {
          cameraSupportedSurfaceCombinationMap.put(
              cameraId, new SupportedSurfaceCombination(context, cameraId, camcorderProfileHelper));
        }
      } catch (CameraAccessException e) {
        throw new IllegalArgumentException("Fail to get camera id list", e);
      }

      isInitialized = true;
    }
  }

  /**
   * Get max supported output size for specific camera device and image format
   *
   * @param cameraId the camera Id
   * @param imageFormat the image format info
   * @return the max supported output size for the image format
   */
  @Override
  public Size getMaxOutputSize(String cameraId, int imageFormat) {
    if (!isInitialized) {
      throw new IllegalStateException("CameraDeviceSurfaceManager is not initialized.");
    }

    SupportedSurfaceCombination supportedSurfaceCombination =
        cameraSupportedSurfaceCombinationMap.get(cameraId);

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
   */
  @Override
  public Size getPreviewSize() {
    if (!isInitialized) {
      throw new IllegalStateException("CameraDeviceSurfaceManager is not initialized.");
    }

    // 1920x1080 is maximum preview size
    Size previewSize = MAXIMUM_PREVIEW_SIZE;

    if (!cameraSupportedSurfaceCombinationMap.isEmpty()) {
      // Preview size depends on the display size and 1080P. Therefore, we can get the first camera
      // device's preview size to return it.
      String cameraId = (String) cameraSupportedSurfaceCombinationMap.keySet().toArray()[0];
      previewSize =
          cameraSupportedSurfaceCombinationMap
              .get(cameraId)
              .getSurfaceSizeDefinition()
              .getPreviewSize();
    }

    return previewSize;
  }
}

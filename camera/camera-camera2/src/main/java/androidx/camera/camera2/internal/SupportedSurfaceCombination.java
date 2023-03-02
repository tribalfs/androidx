/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.camera.camera2.internal;

import static android.content.pm.PackageManager.FEATURE_CAMERA_CONCURRENT;

import static androidx.camera.core.internal.utils.SizeUtil.RESOLUTION_1080P;
import static androidx.camera.core.internal.utils.SizeUtil.RESOLUTION_480P;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.util.Range;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import androidx.camera.camera2.internal.compat.CameraAccessExceptionCompat;
import androidx.camera.camera2.internal.compat.CameraCharacteristicsCompat;
import androidx.camera.camera2.internal.compat.CameraManagerCompat;
import androidx.camera.camera2.internal.compat.StreamConfigurationMapCompat;
import androidx.camera.camera2.internal.compat.workaround.ExtraSupportedSurfaceCombinationsContainer;
import androidx.camera.core.CameraUnavailableException;
import androidx.camera.core.impl.AttachedSurfaceInfo;
import androidx.camera.core.impl.ImageFormatConstants;
import androidx.camera.core.impl.StreamSpec;
import androidx.camera.core.impl.SurfaceCombination;
import androidx.camera.core.impl.SurfaceConfig;
import androidx.camera.core.impl.SurfaceSizeDefinition;
import androidx.camera.core.impl.UseCaseConfig;
import androidx.camera.core.impl.utils.CompareSizesByArea;
import androidx.core.util.Preconditions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Camera device supported surface configuration combinations
 *
 * <p>{@link android.hardware.camera2.CameraDevice#createCaptureSession} defines the default
 * guaranteed stream combinations for different hardware level devices. It defines what combination
 * of surface configuration type and size pairs can be supported for different hardware level camera
 * devices. This structure is used to store a list of surface combinations that are guaranteed to
 * support for this camera device.
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
final class SupportedSurfaceCombination {
    private static final String TAG = "SupportedSurfaceCombination";
    private final List<SurfaceCombination> mSurfaceCombinations = new ArrayList<>();
    private final List<SurfaceCombination> mConcurrentSurfaceCombinations = new ArrayList<>();
    private final String mCameraId;
    private final CamcorderProfileHelper mCamcorderProfileHelper;
    private final CameraCharacteristicsCompat mCharacteristics;
    private final ExtraSupportedSurfaceCombinationsContainer
            mExtraSupportedSurfaceCombinationsContainer;
    private final int mHardwareLevel;
    private boolean mIsRawSupported = false;
    private boolean mIsBurstCaptureSupported = false;
    @VisibleForTesting
    SurfaceSizeDefinition mSurfaceSizeDefinition;
    @NonNull
    private final DisplayInfoManager mDisplayInfoManager;

    SupportedSurfaceCombination(@NonNull Context context, @NonNull String cameraId,
            @NonNull CameraManagerCompat cameraManagerCompat,
            @NonNull CamcorderProfileHelper camcorderProfileHelper)
            throws CameraUnavailableException {
        mCameraId = Preconditions.checkNotNull(cameraId);
        mCamcorderProfileHelper = Preconditions.checkNotNull(camcorderProfileHelper);
        mExtraSupportedSurfaceCombinationsContainer =
                new ExtraSupportedSurfaceCombinationsContainer();
        mDisplayInfoManager = DisplayInfoManager.getInstance(context);

        try {
            mCharacteristics = cameraManagerCompat.getCameraCharacteristicsCompat(mCameraId);
            Integer keyValue = mCharacteristics.get(
                    CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL);
            mHardwareLevel = keyValue != null ? keyValue
                    : CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL_LEGACY;
        } catch (CameraAccessExceptionCompat e) {
            throw CameraUnavailableExceptionHelper.createFrom(e);
        }

        int[] availableCapabilities =
                mCharacteristics.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

        if (availableCapabilities != null) {
            for (int capability : availableCapabilities) {
                if (capability == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_RAW) {
                    mIsRawSupported = true;
                } else if (capability
                        == CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE) {
                    mIsBurstCaptureSupported = true;
                }
            }
        }

        generateSupportedCombinationList();
        if (context.getPackageManager().hasSystemFeature(FEATURE_CAMERA_CONCURRENT)) {
            generateConcurrentSupportedCombinationList();
        }
        generateSurfaceSizeDefinition();
        checkCustomization();
    }

    String getCameraId() {
        return mCameraId;
    }

    boolean isRawSupported() {
        return mIsRawSupported;
    }

    boolean isBurstCaptureSupported() {
        return mIsBurstCaptureSupported;
    }

    /**
     * Check whether the input surface configuration list is under the capability of any combination
     * of this object.
     *
     * @param isConcurrentCameraModeOn true if concurrent camera mode is on, otherwise false.
     * @param surfaceConfigList the surface configuration list to be compared
     * @return the check result that whether it could be supported
     */
    boolean checkSupported(
            boolean isConcurrentCameraModeOn,
            List<SurfaceConfig> surfaceConfigList) {
        boolean isSupported = false;

        List<SurfaceCombination> targetSurfaceCombinations = isConcurrentCameraModeOn
                ? mConcurrentSurfaceCombinations : mSurfaceCombinations;
        for (SurfaceCombination surfaceCombination : targetSurfaceCombinations) {
            isSupported = surfaceCombination.isSupported(surfaceConfigList);

            if (isSupported) {
                break;
            }
        }

        return isSupported;
    }

    /**
     * Transform to a SurfaceConfig object with image format and size info
     *
     * @param isConcurrentCameraModeOn true if concurrent camera mode is on, otherwise false.
     * @param imageFormat the image format info for the surface configuration object
     * @param size        the size info for the surface configuration object
     * @return new {@link SurfaceConfig} object
     */
    SurfaceConfig transformSurfaceConfig(
            boolean isConcurrentCameraModeOn,
            int imageFormat,
            Size size) {
        Size maxOutputSizeForConcurrentMode = isConcurrentCameraModeOn
                ? getMaxOutputSizeByFormat(imageFormat) : null;
        return SurfaceConfig.transformSurfaceConfig(
                isConcurrentCameraModeOn,
                imageFormat,
                size,
                mSurfaceSizeDefinition,
                maxOutputSizeForConcurrentMode);
    }

    static int getMaxFramerate(CameraCharacteristicsCompat characteristics, int imageFormat,
            Size size) {
        int maxFramerate = 0;
        try {
            maxFramerate = (int) (1000000000.0
                    / characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                    .getOutputMinFrameDuration(imageFormat,
                            size));
        } catch (Exception e) {
            //TODO
            //this try catch is in place for the rare that a surface config has a size
            // incompatible for getOutputMinFrameDuration...  put into a Quirk
        }
        return maxFramerate;
    }

    /**
     * @param newTargetFramerate    an incoming framerate range
     * @param storedTargetFramerate a stored framerate range to be modified
     * @return adjusted target frame rate
     *
     * If the two ranges are both nonnull and disjoint of each other, then the range that was
     * already stored will be used
     */
    private Range<Integer> getUpdatedTargetFramerate(Range<Integer> newTargetFramerate,
            Range<Integer> storedTargetFramerate) {
        Range<Integer> updatedTarget = storedTargetFramerate;

        if (storedTargetFramerate == null) {
            // if stored value was null before, set it to the new value
            updatedTarget = newTargetFramerate;
        } else if (newTargetFramerate != null) {
            try {
                // get intersection of existing target fps
                updatedTarget =
                        storedTargetFramerate
                                .intersect(newTargetFramerate);
            } catch (IllegalArgumentException e) {
                // no intersection, keep the previously stored value
                updatedTarget = storedTargetFramerate;
            }
        }
        return updatedTarget;
    }

    /**
     * @param currentMaxFps the previously stored Max FPS
     * @param imageFormat   the image format of the incoming surface
     * @param size          the size of the incoming surface
     */
    private int getUpdatedMaximumFps(int currentMaxFps, int imageFormat, Size size) {
        return Math.min(currentMaxFps, getMaxFramerate(mCharacteristics, imageFormat, size));
    }

    /**
     * Finds the suggested stream specifications of the newly added UseCaseConfig.
     *
     * @param isConcurrentCameraModeOn          true if concurrent camera mode is on, otherwise
     *                                          false.
     * @param attachedSurfaces                  the existing surfaces.
     * @param newUseCaseConfigsSupportedSizeMap newly added UseCaseConfig to supported output
     *                                          sizes map.
     * @return the suggested stream specifications, which is a mapping from UseCaseConfig to the
     * suggested stream specification.
     * @throws IllegalArgumentException if the suggested solution for newUseCaseConfigs cannot be
     *                                  found. This may be due to no available output size or no
     *                                  available surface combination.
     */
    @NonNull
    Map<UseCaseConfig<?>, StreamSpec> getSuggestedStreamSpecifications(
            boolean isConcurrentCameraModeOn,
            @NonNull List<AttachedSurfaceInfo> attachedSurfaces,
            @NonNull Map<UseCaseConfig<?>, List<Size>> newUseCaseConfigsSupportedSizeMap) {
        // Refresh Preview Size based on current display configurations.
        refreshPreviewSize();
        List<SurfaceConfig> surfaceConfigs = new ArrayList<>();
        for (AttachedSurfaceInfo attachedSurface : attachedSurfaces) {
            surfaceConfigs.add(attachedSurface.getSurfaceConfig());
        }

        List<UseCaseConfig<?>> newUseCaseConfigs = new ArrayList<>(
                newUseCaseConfigsSupportedSizeMap.keySet());
        // Use the small size (640x480) for new use cases to check whether there is any possible
        // supported combination first
        for (UseCaseConfig<?> useCaseConfig : newUseCaseConfigs) {
            Size maxOutputSizeForConcurrentMode = isConcurrentCameraModeOn
                    ? getMaxOutputSizeByFormat(useCaseConfig.getInputFormat()) : null;
            surfaceConfigs.add(
                    SurfaceConfig.transformSurfaceConfig(
                            isConcurrentCameraModeOn,
                            useCaseConfig.getInputFormat(),
                            new Size(640, 480),
                            mSurfaceSizeDefinition,
                            maxOutputSizeForConcurrentMode));
        }

        if (!checkSupported(isConcurrentCameraModeOn, surfaceConfigs)) {
            throw new IllegalArgumentException(
                    "No supported surface combination is found for camera device - Id : "
                            + mCameraId + ".  May be attempting to bind too many use cases. "
                            + "Existing surfaces: " + attachedSurfaces + " New configs: "
                            + newUseCaseConfigs);
        }

        Range<Integer> targetFramerateForConfig = null;
        int existingSurfaceFrameRateCeiling = Integer.MAX_VALUE;

        for (AttachedSurfaceInfo attachedSurfaceInfo : attachedSurfaces) {
            // init target fps range for new configs from existing surfaces
            targetFramerateForConfig = getUpdatedTargetFramerate(
                    attachedSurfaceInfo.getTargetFrameRate(),
                    targetFramerateForConfig);
            //get the fps ceiling for existing surfaces
            existingSurfaceFrameRateCeiling = getUpdatedMaximumFps(
                    existingSurfaceFrameRateCeiling,
                    attachedSurfaceInfo.getImageFormat(), attachedSurfaceInfo.getSize());
        }

        // Get the index order list by the use case priority for finding stream configuration
        List<Integer> useCasesPriorityOrder = getUseCasesPriorityOrder(newUseCaseConfigs);
        List<List<Size>> supportedOutputSizesList = new ArrayList<>();

        // Collect supported output sizes for all use cases
        for (Integer index : useCasesPriorityOrder) {
            List<Size> supportedOutputSizes = newUseCaseConfigsSupportedSizeMap.get(
                    newUseCaseConfigs.get(index));
            supportedOutputSizesList.add(supportedOutputSizes);
        }

        // Get all possible size arrangements
        List<List<Size>> allPossibleSizeArrangements =
                getAllPossibleSizeArrangements(
                        supportedOutputSizesList);

        // update target fps for new configs using new use cases' priority order
        for (Integer index : useCasesPriorityOrder) {
            targetFramerateForConfig =
                    getUpdatedTargetFramerate(
                            newUseCaseConfigs.get(index).getTargetFramerate(null),
                            targetFramerateForConfig);
        }

        Map<UseCaseConfig<?>, StreamSpec> suggestedStreamSpecMap;
        List<Size> savedSizes = null;
        int savedConfigMaxFps = Integer.MAX_VALUE;

        // Transform use cases to SurfaceConfig list and find the first (best) workable combination
        for (List<Size> possibleSizeList : allPossibleSizeArrangements) {
            // Attach SurfaceConfig of original use cases since it will impact the new use cases
            List<SurfaceConfig> surfaceConfigList = new ArrayList<>();
            int currentConfigFramerateCeiling = existingSurfaceFrameRateCeiling;
            boolean isConfigFrameRateAcceptable = true;

            for (AttachedSurfaceInfo attachedSurfaceInfo : attachedSurfaces) {
                surfaceConfigList.add(attachedSurfaceInfo.getSurfaceConfig());
            }

            // Attach SurfaceConfig of new use cases
            for (int i = 0; i < possibleSizeList.size(); i++) {
                Size size = possibleSizeList.get(i);
                UseCaseConfig<?> newUseCase =
                        newUseCaseConfigs.get(useCasesPriorityOrder.get(i));
                // add new use case/size config to list of surfaces
                Size maxOutputSizeForConcurrentMode = isConcurrentCameraModeOn
                        ? getMaxOutputSizeByFormat(newUseCase.getInputFormat()) : null;
                surfaceConfigList.add(
                        SurfaceConfig.transformSurfaceConfig(
                                isConcurrentCameraModeOn,
                                newUseCase.getInputFormat(),
                                size,
                                mSurfaceSizeDefinition,
                                maxOutputSizeForConcurrentMode));

                // get the maximum fps of the new surface and update the maximum fps of the
                // proposed configuration
                currentConfigFramerateCeiling = getUpdatedMaximumFps(
                        currentConfigFramerateCeiling,
                        newUseCase.getInputFormat(),
                        size);
            }
            if (targetFramerateForConfig != null) {
                if (existingSurfaceFrameRateCeiling > currentConfigFramerateCeiling
                        && currentConfigFramerateCeiling < targetFramerateForConfig.getLower()) {
                    // if the max fps before adding new use cases supports our target fps range
                    // BUT the max fps of the new configuration is below
                    // our target fps range, we'll want to check the next configuration until we
                    // get one that supports our target FPS
                    isConfigFrameRateAcceptable = false;
                }
            }

            // only change the saved config if you get another that has a better max fps
            if (checkSupported(isConcurrentCameraModeOn, surfaceConfigList)) {
                // if the config is supported by the device but doesn't meet the target framerate,
                // save the config
                if (savedConfigMaxFps == Integer.MAX_VALUE) {
                    savedConfigMaxFps = currentConfigFramerateCeiling;
                    savedSizes = possibleSizeList;
                } else if (savedConfigMaxFps < currentConfigFramerateCeiling) {
                    // only change the saved config if the max fps is better
                    savedConfigMaxFps = currentConfigFramerateCeiling;
                    savedSizes = possibleSizeList;
                }

                // if we have a configuration where the max fps is acceptable for our target, break
                if (isConfigFrameRateAcceptable) {
                    savedConfigMaxFps = currentConfigFramerateCeiling;
                    savedSizes = possibleSizeList;
                    break;
                }
            }
        }

        // Map the saved supported SurfaceConfig combination
        if (savedSizes != null) {
            suggestedStreamSpecMap = new HashMap<>();
            for (UseCaseConfig<?> useCaseConfig : newUseCaseConfigs) {
                suggestedStreamSpecMap.put(
                        useCaseConfig,
                        StreamSpec.builder(savedSizes.get(useCasesPriorityOrder.indexOf(
                                newUseCaseConfigs.indexOf(useCaseConfig)))).build());
            }
        } else {
            throw new IllegalArgumentException(
                    "No supported surface combination is found for camera device - Id : "
                            + mCameraId + " and Hardware level: " + mHardwareLevel
                            + ". May be the specified resolution is too large and not supported."
                            + " Existing surfaces: " + attachedSurfaces
                            + " New configs: " + newUseCaseConfigs);
        }
        return suggestedStreamSpecMap;
    }

    private List<Integer> getUseCasesPriorityOrder(List<UseCaseConfig<?>> newUseCaseConfigs) {
        List<Integer> priorityOrder = new ArrayList<>();

        /*
         * Once the stream resource is occupied by one use case, it will impact the other use cases.
         * Therefore, we need to define the priority for stream resource usage. For the use cases
         * with the higher priority, we will try to find the best one for them in priority as
         * possible.
         */
        List<Integer> priorityValueList = new ArrayList<>();

        for (UseCaseConfig<?> config : newUseCaseConfigs) {
            int priority = config.getSurfaceOccupancyPriority(0);
            if (!priorityValueList.contains(priority)) {
                priorityValueList.add(priority);
            }
        }

        Collections.sort(priorityValueList);
        // Reverse the priority value list in descending order since larger value means higher
        // priority
        Collections.reverse(priorityValueList);

        for (int priorityValue : priorityValueList) {
            for (UseCaseConfig<?> config : newUseCaseConfigs) {
                if (priorityValue == config.getSurfaceOccupancyPriority(0)) {
                    priorityOrder.add(newUseCaseConfigs.indexOf(config));
                }
            }
        }

        return priorityOrder;
    }

    private List<List<Size>> getAllPossibleSizeArrangements(
            List<List<Size>> supportedOutputSizesList) {
        int totalArrangementsCount = 1;

        for (List<Size> supportedOutputSizes : supportedOutputSizesList) {
            totalArrangementsCount *= supportedOutputSizes.size();
        }

        // If totalArrangementsCount is 0 means that there may some problem to get
        // supportedOutputSizes
        // for some use case
        if (totalArrangementsCount == 0) {
            throw new IllegalArgumentException("Failed to find supported resolutions.");
        }

        List<List<Size>> allPossibleSizeArrangements = new ArrayList<>();

        // Initialize allPossibleSizeArrangements for the following operations
        for (int i = 0; i < totalArrangementsCount; i++) {
            List<Size> sizeList = new ArrayList<>();
            allPossibleSizeArrangements.add(sizeList);
        }

        /*
         * Try to list out all possible arrangements by attaching all possible size of each column
         * in sequence. We have generated supportedOutputSizesList by the priority order for
         * different use cases. And the supported outputs sizes for each use case are also arranged
         * from large to small. Therefore, the earlier size arrangement in the result list will be
         * the better one to choose if finally it won't exceed the camera device's stream
         * combination capability.
         */
        int currentRunCount = totalArrangementsCount;
        int nextRunCount = currentRunCount / supportedOutputSizesList.get(0).size();

        for (int currentIndex = 0; currentIndex < supportedOutputSizesList.size(); currentIndex++) {
            List<Size> supportedOutputSizes = supportedOutputSizesList.get(currentIndex);
            for (int i = 0; i < totalArrangementsCount; i++) {
                List<Size> surfaceConfigList = allPossibleSizeArrangements.get(i);

                surfaceConfigList.add(
                        supportedOutputSizes.get((i % currentRunCount) / nextRunCount));
            }

            if (currentIndex < supportedOutputSizesList.size() - 1) {
                currentRunCount = nextRunCount;
                nextRunCount =
                        currentRunCount / supportedOutputSizesList.get(currentIndex + 1).size();
            }
        }

        return allPossibleSizeArrangements;
    }

    /**
     * Get max supported output size for specific image format
     *
     * @param imageFormat the image format info
     * @return the max supported output size for the image format
     */
    Size getMaxOutputSizeByFormat(int imageFormat) {
        // Needs to retrieve the output size from the original stream configuration map without
        // quirks applied.
        StreamConfigurationMap map =
                mCharacteristics.getStreamConfigurationMapCompat().toStreamConfigurationMap();
        Size[] outputSizes;
        if (imageFormat == ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE) {
            // This is a little tricky that 0x22 that is internal defined in
            // StreamConfigurationMap.java to be equal to ImageFormat.PRIVATE that is public
            // after Android level 23 but not public in Android L. Use {@link SurfaceTexture}
            // or {@link MediaCodec} will finally mapped to 0x22 in StreamConfigurationMap to
            // retrieve the output sizes information.
            outputSizes = map.getOutputSizes(SurfaceTexture.class);
        } else {
            outputSizes = map.getOutputSizes(imageFormat);
        }
        return Collections.max(Arrays.asList(outputSizes), new CompareSizesByArea());
    }

    private void generateSupportedCombinationList() {
        mSurfaceCombinations.addAll(
                GuaranteedConfigurationsUtil.generateSupportedCombinationList(mHardwareLevel,
                        mIsRawSupported, mIsBurstCaptureSupported));

        mSurfaceCombinations.addAll(
                mExtraSupportedSurfaceCombinationsContainer.get(mCameraId, mHardwareLevel));
    }

    private void generateConcurrentSupportedCombinationList() {
        mConcurrentSurfaceCombinations.addAll(
                GuaranteedConfigurationsUtil.getConcurrentSupportedCombinationList());
    }

    private void checkCustomization() {
        // TODO(b/119466260): Integrate found feasible stream combinations into supported list
    }

    // Utility classes and methods:
    // *********************************************************************************************

    private void generateSurfaceSizeDefinition() {
        Size analysisSize = new Size(640, 480);
        Size s720p = new Size(1280, 720);
        Size previewSize = mDisplayInfoManager.getPreviewSize();
        Size s1440p = new Size(1920, 1440);
        Size recordSize = getRecordSize();
        mSurfaceSizeDefinition =
                SurfaceSizeDefinition.create(analysisSize, s720p, previewSize, s1440p, recordSize);
    }

    private void refreshPreviewSize() {
        mDisplayInfoManager.refresh();
        if (mSurfaceSizeDefinition == null) {
            generateSurfaceSizeDefinition();
        } else {
            Size previewSize = mDisplayInfoManager.getPreviewSize();
            mSurfaceSizeDefinition = SurfaceSizeDefinition.create(
                    mSurfaceSizeDefinition.getAnalysisSize(),
                    mSurfaceSizeDefinition.getS720pSize(),
                    previewSize,
                    mSurfaceSizeDefinition.getS1440pSize(),
                    mSurfaceSizeDefinition.getRecordSize());
        }
    }

    /**
     * RECORD refers to the camera device's maximum supported recording resolution, as determined by
     * CamcorderProfile.
     */
    @NonNull
    private Size getRecordSize() {
        int cameraId;

        try {
            cameraId = Integer.parseInt(mCameraId);
        } catch (NumberFormatException e) {
            // The camera Id is not an integer because the camera may be a removable device. Use
            // StreamConfigurationMap to determine the RECORD size.
            return getRecordSizeFromStreamConfigurationMap();
        }

        CamcorderProfile profile = null;

        if (mCamcorderProfileHelper.hasProfile(cameraId, CamcorderProfile.QUALITY_HIGH)) {
            profile = mCamcorderProfileHelper.get(cameraId, CamcorderProfile.QUALITY_HIGH);
        }

        if (profile != null) {
            return new Size(profile.videoFrameWidth, profile.videoFrameHeight);
        }

        return getRecordSizeByHasProfile(cameraId);
    }

    /**
     * Return the maximum supported video size for cameras using data from the stream
     * configuration map.
     *
     * @return Maximum supported video size.
     */
    @NonNull
    private Size getRecordSizeFromStreamConfigurationMap() {
        // Determining the record size needs to retrieve the output size from the original stream
        // configuration map without quirks applied.
        StreamConfigurationMapCompat mapCompat = mCharacteristics.getStreamConfigurationMapCompat();
        Size[] videoSizeArr = mapCompat.toStreamConfigurationMap().getOutputSizes(
                MediaRecorder.class);

        if (videoSizeArr == null) {
            return RESOLUTION_480P;
        }

        Arrays.sort(videoSizeArr, new CompareSizesByArea(true));

        for (Size size : videoSizeArr) {
            // Returns the largest supported size under 1080P
            if (size.getWidth() <= RESOLUTION_1080P.getWidth()
                    && size.getHeight() <= RESOLUTION_1080P.getHeight()) {
                return size;
            }
        }

        return RESOLUTION_480P;
    }

    /**
     * Return the maximum supported video size for cameras by
     * {@link CamcorderProfile#hasProfile(int, int)}.
     *
     * @return Maximum supported video size.
     */
    @NonNull
    private Size getRecordSizeByHasProfile(int cameraId) {
        Size recordSize = RESOLUTION_480P;
        CamcorderProfile profile = null;

        // Check whether 4KDCI, 2160P, 2K, 1080P, 720P, 480P (sorted by size) are supported by
        // CamcorderProfile
        if (mCamcorderProfileHelper.hasProfile(cameraId, CamcorderProfile.QUALITY_4KDCI)) {
            profile = mCamcorderProfileHelper.get(cameraId, CamcorderProfile.QUALITY_4KDCI);
        } else if (mCamcorderProfileHelper.hasProfile(cameraId, CamcorderProfile.QUALITY_2160P)) {
            profile = mCamcorderProfileHelper.get(cameraId, CamcorderProfile.QUALITY_2160P);
        } else if (mCamcorderProfileHelper.hasProfile(cameraId, CamcorderProfile.QUALITY_2K)) {
            profile = mCamcorderProfileHelper.get(cameraId, CamcorderProfile.QUALITY_2K);
        } else if (mCamcorderProfileHelper.hasProfile(cameraId, CamcorderProfile.QUALITY_1080P)) {
            profile = mCamcorderProfileHelper.get(cameraId, CamcorderProfile.QUALITY_1080P);
        } else if (mCamcorderProfileHelper.hasProfile(cameraId, CamcorderProfile.QUALITY_720P)) {
            profile = mCamcorderProfileHelper.get(cameraId, CamcorderProfile.QUALITY_720P);
        } else if (mCamcorderProfileHelper.hasProfile(cameraId, CamcorderProfile.QUALITY_480P)) {
            profile = mCamcorderProfileHelper.get(cameraId, CamcorderProfile.QUALITY_480P);
        }

        if (profile != null) {
            recordSize = new Size(profile.videoFrameWidth, profile.videoFrameHeight);
        }

        return recordSize;
    }
}

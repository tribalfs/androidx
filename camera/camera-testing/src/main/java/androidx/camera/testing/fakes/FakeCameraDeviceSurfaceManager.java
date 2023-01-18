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

package androidx.camera.testing.fakes;

import static android.graphics.ImageFormat.JPEG;
import static android.graphics.ImageFormat.YUV_420_888;

import static androidx.camera.core.impl.ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE;

import static com.google.common.primitives.Ints.asList;

import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.camera.core.impl.AttachedSurfaceInfo;
import androidx.camera.core.impl.CameraDeviceSurfaceManager;
import androidx.camera.core.impl.SurfaceConfig;
import androidx.camera.core.impl.UseCaseConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A CameraDeviceSurfaceManager which has no supported SurfaceConfigs. */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
public final class FakeCameraDeviceSurfaceManager implements CameraDeviceSurfaceManager {

    public static final Size MAX_OUTPUT_SIZE = new Size(4032, 3024); // 12.2 MP

    private final Map<String, Map<Class<? extends UseCaseConfig<?>>, Size>> mDefinedResolutions =
            new HashMap<>();

    private final Set<List<Integer>> mValidSurfaceCombos = createDefaultValidSurfaceCombos();

    /**
     * Sets the given suggested resolutions for the specified camera Id and use case type.
     */
    public void setSuggestedResolution(@NonNull String cameraId,
            @NonNull Class<? extends UseCaseConfig<?>> type,
            @NonNull Size size) {
        Map<Class<? extends UseCaseConfig<?>>, Size> useCaseConfigTypeToSizeMap =
                mDefinedResolutions.get(cameraId);
        if (useCaseConfigTypeToSizeMap == null) {
            useCaseConfigTypeToSizeMap = new HashMap<>();
            mDefinedResolutions.put(cameraId, useCaseConfigTypeToSizeMap);
        }

        useCaseConfigTypeToSizeMap.put(type, size);
    }

    @Override
    public boolean checkSupported(@NonNull String cameraId,
            @Nullable List<SurfaceConfig> surfaceConfigList) {
        return false;
    }

    @Override
    @Nullable
    public SurfaceConfig transformSurfaceConfig(@NonNull String cameraId, int imageFormat,
            @NonNull Size size) {

        //returns a placeholder SurfaceConfig
        return SurfaceConfig.create(SurfaceConfig.ConfigType.PRIV,
                SurfaceConfig.ConfigSize.PREVIEW);
    }

    @Override
    @NonNull
    public Map<UseCaseConfig<?>, Size> getSuggestedResolutions(
            @NonNull String cameraId,
            @NonNull List<AttachedSurfaceInfo> existingSurfaces,
            @NonNull List<UseCaseConfig<?>> newUseCaseConfigs) {
        checkSurfaceCombo(existingSurfaces, newUseCaseConfigs);
        Map<UseCaseConfig<?>, Size> suggestedSizes = new HashMap<>();
        for (UseCaseConfig<?> useCaseConfig : newUseCaseConfigs) {
            Size resolution = MAX_OUTPUT_SIZE;
            Map<Class<? extends UseCaseConfig<?>>, Size> definedResolutions =
                    mDefinedResolutions.get(cameraId);
            if (definedResolutions != null) {
                Size definedResolution = definedResolutions.get(useCaseConfig.getClass());
                if (definedResolution != null) {
                    resolution = definedResolution;
                }
            }

            suggestedSizes.put(useCaseConfig, resolution);
        }

        return suggestedSizes;
    }

    /**
     * Checks if the surface combinations is supported.
     *
     * <p> Throws {@link IllegalArgumentException} if not supported.
     */
    private void checkSurfaceCombo(List<AttachedSurfaceInfo> existingSurfaceInfos,
            @NonNull List<UseCaseConfig<?>> newSurfaceConfigs) {
        // Combine existing Surface with new Surface
        List<Integer> currentCombo = new ArrayList<>();
        for (UseCaseConfig<?> useCaseConfig : newSurfaceConfigs) {
            currentCombo.add(useCaseConfig.getInputFormat());
        }
        for (AttachedSurfaceInfo surfaceInfo : existingSurfaceInfos) {
            currentCombo.add(surfaceInfo.getImageFormat());
        }
        // Loop through valid combinations and return early if the combo is supported.
        for (List<Integer> validCombo : mValidSurfaceCombos) {
            if (isComboSupported(currentCombo, validCombo)) {
                return;
            }
        }
        // Throw IAE if none of the valid combos supports the current combo.
        throw new IllegalArgumentException("Surface combo not supported");
    }

    /**
     * Checks if the app combination in covered by the given valid combination.
     */
    private boolean isComboSupported(@NonNull List<Integer> appCombo,
            @NonNull List<Integer> validCombo) {
        List<Integer> combo = new ArrayList<>(validCombo);
        for (Integer format : appCombo) {
            if (!combo.remove(format)) {
                return false;
            }
        }
        return true;
    }

    /**
     * The default combination is similar to LEGACY level devices.
     */
    private static Set<List<Integer>> createDefaultValidSurfaceCombos() {
        Set<List<Integer>> validCombos = new HashSet<>();
        validCombos.add(asList(INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE, YUV_420_888, JPEG));
        validCombos.add(asList(INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE,
                INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE));
        return validCombos;
    }
}

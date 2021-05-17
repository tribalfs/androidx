/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.camera.core.internal;

import android.graphics.Rect;
import android.util.Size;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraInfo;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Logger;
import androidx.camera.core.UseCase;
import androidx.camera.core.ViewPort;
import androidx.camera.core.impl.CameraConfig;
import androidx.camera.core.impl.CameraConfigs;
import androidx.camera.core.impl.CameraControlInternal;
import androidx.camera.core.impl.CameraDeviceSurfaceManager;
import androidx.camera.core.impl.CameraInfoInternal;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.core.impl.Config;
import androidx.camera.core.impl.SurfaceConfig;
import androidx.camera.core.impl.UseCaseConfig;
import androidx.camera.core.impl.UseCaseConfigFactory;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.core.util.Consumer;
import androidx.core.util.Preconditions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * A {@link CameraInternal} adapter which checks that the UseCases to make sure that the resolutions
 * and image formats can be supported.
 *
 * <p> The CameraUseCaseAdapter wraps a set of CameraInternals which it can dynamically switch
 * between based on different configurations that are required by the adapter. This is used by
 * extensions in order to select the correct CameraInternal instance which has the required
 * camera id.
 */
public final class CameraUseCaseAdapter implements Camera {
    @NonNull
    private CameraInternal mCameraInternal;
    private final LinkedHashSet<CameraInternal> mCameraInternals;
    private final CameraDeviceSurfaceManager mCameraDeviceSurfaceManager;
    private final UseCaseConfigFactory mUseCaseConfigFactory;

    private static final String TAG = "CameraUseCaseAdapter";

    private final CameraId mId;

    @GuardedBy("mLock")
    private final List<UseCase> mUseCases = new ArrayList<>();

    @GuardedBy("mLock")
    @Nullable
    private ViewPort mViewPort;

    // Additional configs to apply onto the UseCases when added to this Camera
    @GuardedBy("mLock")
    @NonNull
    private CameraConfig mCameraConfig = CameraConfigs.emptyConfig();

    private final Object mLock = new Object();

    // This indicates whether or not the UseCases that have been added to this adapter has
    // actually been attached to the CameraInternal instance.
    @GuardedBy("mLock")
    private boolean mAttached = true;

    // This holds the cached Interop config from CameraControlInternal.
    @GuardedBy("mLock")
    private Config mInteropConfig = null;

    /**
     * Create a new {@link CameraUseCaseAdapter} instance.
     *
     * @param cameras                    the set of cameras that are wrapped, with them in order
     *                                   of preference. The actual camera used will be dependent
     *                                   on configs set by
     *                                   {@link #setExtendedConfig(CameraConfig)} which can
     *                                   filter out specific camera instances
     * @param cameraDeviceSurfaceManager A class that checks for whether a specific camera
     *                                   can support the set of Surface with set resolutions.
     */
    public CameraUseCaseAdapter(@NonNull LinkedHashSet<CameraInternal> cameras,
            @NonNull CameraDeviceSurfaceManager cameraDeviceSurfaceManager,
            @NonNull UseCaseConfigFactory useCaseConfigFactory) {
        mCameraInternal = cameras.iterator().next();
        mCameraInternals = new LinkedHashSet<>(cameras);
        mId = new CameraId(mCameraInternals);
        mCameraDeviceSurfaceManager = cameraDeviceSurfaceManager;
        mUseCaseConfigFactory = useCaseConfigFactory;
    }

    /**
     * Generate a identifier for the set of {@link CameraInternal}.
     */
    @NonNull
    public static CameraId generateCameraId(@NonNull LinkedHashSet<CameraInternal> cameras) {
        return new CameraId(cameras);
    }

    /**
     * Returns the identifier for this {@link CameraUseCaseAdapter}.
     */
    @NonNull
    public CameraId getCameraId() {
        return mId;
    }

    /**
     * Returns true if the {@link CameraUseCaseAdapter} is an equivalent camera.
     */
    public boolean isEquivalent(@NonNull CameraUseCaseAdapter cameraUseCaseAdapter) {
        return mId.equals(cameraUseCaseAdapter.getCameraId());
    }

    /**
     * Set the viewport that will be used for the {@link UseCase} attached to the camera.
     */
    public void setViewPort(@Nullable ViewPort viewPort) {
        synchronized (mLock) {
            mViewPort = viewPort;
        }
    }

    /**
     * Check to see if the set of {@link UseCase} can be attached to the camera.
     *
     * <p> This does not take into account UseCases which are already attached to the camera.
     */
    public void checkAttachUseCases(@NonNull List<UseCase> useCases) throws CameraException {
        synchronized (mLock) {
            // If the UseCases exceed the resolutions then it will throw an exception
            try {
                Map<UseCase, ConfigPair> configs = getConfigs(useCases,
                        mCameraConfig.getUseCaseConfigFactory(), mUseCaseConfigFactory);
                calculateSuggestedResolutions(mCameraInternal.getCameraInfoInternal(),
                        useCases, Collections.emptyList(), configs);
            } catch (IllegalArgumentException e) {
                throw new CameraException(e.getMessage());
            }
        }
    }

    /**
     * Add the specified collection of {@link UseCase} to the adapter.
     *
     * @throws CameraException Thrown if the combination of newly added UseCases and the
     *                         currently added UseCases exceed the capability of the camera.
     */
    @OptIn(markerClass = androidx.camera.core.ExperimentalUseCaseGroup.class)
    public void addUseCases(@NonNull Collection<UseCase> useCases) throws CameraException {
        synchronized (mLock) {
            List<UseCase> newUseCases = new ArrayList<>();

            for (UseCase useCase : useCases) {
                if (mUseCases.contains(useCase)) {
                    Logger.d(TAG, "Attempting to attach already attached UseCase");
                } else {
                    newUseCases.add(useCase);
                }
            }

            Map<UseCase, ConfigPair> configs = getConfigs(newUseCases,
                    mCameraConfig.getUseCaseConfigFactory(), mUseCaseConfigFactory);

            Map<UseCase, Size> suggestedResolutionsMap;
            try {
                suggestedResolutionsMap =
                        calculateSuggestedResolutions(mCameraInternal.getCameraInfoInternal(),
                                newUseCases, mUseCases, configs);
            } catch (IllegalArgumentException e) {
                throw new CameraException(e.getMessage());
            }
            updateViewPort(suggestedResolutionsMap, useCases);

            // At this point the binding will succeed since all the calculations are done
            // Do all attaching related work
            for (UseCase useCase : newUseCases) {
                ConfigPair configPair = configs.get(useCase);
                useCase.onAttach(mCameraInternal, configPair.mExtendedConfig,
                        configPair.mCameraConfig);
                useCase.updateSuggestedResolution(
                        Preconditions.checkNotNull(suggestedResolutionsMap.get(useCase)));
            }

            mUseCases.addAll(newUseCases);
            if (mAttached) {
                notifyAttachedUseCasesChange(mUseCases);
                mCameraInternal.attachUseCases(newUseCases);
            }

            // Once all use cases are attached, they need to notify the CameraInternal of its state
            for (UseCase useCase : newUseCases) {
                useCase.notifyState();
            }
        }
    }

    /**
     * Remove the specified collection of {@link UseCase} from the adapter.
     */
    public void removeUseCases(@NonNull Collection<UseCase> useCases) {
        synchronized (mLock) {
            mCameraInternal.detachUseCases(useCases);

            for (UseCase useCase : useCases) {
                if (mUseCases.contains(useCase)) {
                    useCase.onDetach(mCameraInternal);
                } else {
                    Logger.e(TAG, "Attempting to detach non-attached UseCase: " + useCase);
                }
            }

            mUseCases.removeAll(useCases);
        }
    }

    /**
     * Returns the UseCases currently associated with the adapter.
     *
     * <p> The UseCases may or may not be actually attached to the underlying
     * {@link CameraInternal} instance.
     */
    @NonNull
    public List<UseCase> getUseCases() {
        synchronized (mLock) {
            return new ArrayList<>(mUseCases);
        }
    }

    /**
     * Attach the UseCases to the {@link CameraInternal} camera so that the UseCases can receive
     * data if they are active.
     *
     * <p> This will start the underlying {@link CameraInternal} instance.
     *
     * <p> This will restore the cached Interop config to the {@link CameraInternal}.
     */
    public void attachUseCases() {
        synchronized (mLock) {
            if (!mAttached) {
                mCameraInternal.attachUseCases(mUseCases);
                notifyAttachedUseCasesChange(mUseCases);
                restoreInteropConfig();

                // Notify to update the use case's active state because it may be cleared if the
                // use case was ever detached from a camera previously.
                for (UseCase useCase : mUseCases) {
                    useCase.notifyState();
                }

                mAttached = true;
            }
        }
    }

    /**
     * Detach the UseCases from the {@link CameraInternal} so that the UseCases stop receiving data.
     *
     * <p> This will stop the underlying {@link CameraInternal} instance.
     *
     * <p> This will cache the Interop config from the {@link CameraInternal}.
     */
    public void detachUseCases() {
        synchronized (mLock) {
            if (mAttached) {
                mCameraInternal.detachUseCases(new ArrayList<>(mUseCases));
                cacheInteropConfig();
                mAttached = false;
            }
        }
    }

    /**
     * Restores the cached InteropConfig to the camera.
     */
    private void restoreInteropConfig() {
        synchronized (mLock) {
            if (mInteropConfig != null) {
                mCameraInternal.getCameraControlInternal().addInteropConfig(mInteropConfig);
            }
        }
    }

    /**
     * Caches and clears the InteropConfig from the camera.
     */
    private void cacheInteropConfig() {
        synchronized (mLock) {
            CameraControlInternal cameraControlInternal =
                    mCameraInternal.getCameraControlInternal();
            mInteropConfig = cameraControlInternal.getInteropConfig();
            cameraControlInternal.clearInteropConfig();
        }
    }

    private Map<UseCase, Size> calculateSuggestedResolutions(
            @NonNull CameraInfoInternal cameraInfoInternal,
            @NonNull List<UseCase> newUseCases,
            @NonNull List<UseCase> currentUseCases,
            @NonNull Map<UseCase, ConfigPair> configPairMap) {
        List<SurfaceConfig> existingSurfaces = new ArrayList<>();
        String cameraId = cameraInfoInternal.getCameraId();
        Map<UseCase, Size> suggestedResolutions = new HashMap<>();

        // Get resolution for current use cases.
        for (UseCase useCase : currentUseCases) {
            SurfaceConfig surfaceConfig =
                    mCameraDeviceSurfaceManager.transformSurfaceConfig(cameraId,
                            useCase.getImageFormat(),
                            useCase.getAttachedSurfaceResolution());
            existingSurfaces.add(surfaceConfig);
            suggestedResolutions.put(useCase, useCase.getAttachedSurfaceResolution());
        }

        // Calculate resolution for new use cases.
        if (!newUseCases.isEmpty()) {
            Map<UseCaseConfig<?>, UseCase> configToUseCaseMap = new HashMap<>();
            for (UseCase useCase : newUseCases) {
                ConfigPair configPair = configPairMap.get(useCase);
                // Combine with default configuration.
                UseCaseConfig<?> combinedUseCaseConfig =
                        useCase.mergeConfigs(cameraInfoInternal, configPair.mExtendedConfig,
                                configPair.mCameraConfig);
                configToUseCaseMap.put(combinedUseCaseConfig, useCase);
            }

            // Get suggested resolutions and update the use case session configuration
            Map<UseCaseConfig<?>, Size> useCaseConfigSizeMap = mCameraDeviceSurfaceManager
                    .getSuggestedResolutions(cameraId, existingSurfaces,
                            new ArrayList<>(configToUseCaseMap.keySet()));

            for (Map.Entry<UseCaseConfig<?>, UseCase> entry : configToUseCaseMap.entrySet()) {
                suggestedResolutions.put(entry.getValue(),
                        useCaseConfigSizeMap.get(entry.getKey()));
            }
        }
        return suggestedResolutions;
    }

    @OptIn(markerClass = androidx.camera.core.ExperimentalUseCaseGroup.class)
    private void updateViewPort(@NonNull Map<UseCase, Size> suggestedResolutionsMap,
            @NonNull Collection<UseCase> useCases) {
        synchronized (mLock) {
            if (mViewPort != null) {
                boolean isFrontCamera = mCameraInternal.getCameraInfoInternal().getLensFacing()
                        == CameraSelector.LENS_FACING_FRONT;
                // Calculate crop rect if view port is provided.
                Map<UseCase, Rect> cropRectMap = ViewPorts.calculateViewPortRects(
                        mCameraInternal.getCameraControlInternal().getSensorRect(),
                        isFrontCamera,
                        mViewPort.getAspectRatio(),
                        mCameraInternal.getCameraInfoInternal().getSensorRotationDegrees(
                                mViewPort.getRotation()),
                        mViewPort.getScaleType(),
                        mViewPort.getLayoutDirection(),
                        suggestedResolutionsMap);
                for (UseCase useCase : useCases) {
                    useCase.setViewPortCropRect(
                            Preconditions.checkNotNull(cropRectMap.get(useCase)));
                }
            }
        }
    }

    // Pair of UseCase configs. One for the extended config applied on top of the use case and
    // the camera default which applied underneath the use case's config.
    private static class ConfigPair {
        ConfigPair(UseCaseConfig<?> extendedConfig, UseCaseConfig<?> cameraConfig) {
            mExtendedConfig = extendedConfig;
            mCameraConfig = cameraConfig;
        }

        UseCaseConfig<?> mExtendedConfig;
        UseCaseConfig<?> mCameraConfig;
    }

    // Get a map of the configs for the use cases from the respective factories
    private Map<UseCase, ConfigPair> getConfigs(List<UseCase> useCases,
            UseCaseConfigFactory extendedFactory, UseCaseConfigFactory cameraFactory) {
        Map<UseCase, ConfigPair> configs = new HashMap<>();
        for (UseCase useCase : useCases) {
            configs.put(useCase, new ConfigPair(useCase.getDefaultConfig(false, extendedFactory),
                    useCase.getDefaultConfig(true, cameraFactory)));
        }
        return configs;
    }

    /**
     * An identifier for a {@link CameraUseCaseAdapter}.
     *
     * <p>This identifies the actual camera instances that are wrapped by the
     * CameraUseCaseAdapter and is used to determine if 2 different instances of
     * CameraUseCaseAdapter are actually equivalent.
     */
    public static final class CameraId {
        private final List<String> mIds;

        CameraId(LinkedHashSet<CameraInternal> cameraInternals) {
            mIds = new ArrayList<>();
            for (CameraInternal cameraInternal : cameraInternals) {
                mIds.add(cameraInternal.getCameraInfoInternal().getCameraId());
            }
        }

        @Override
        public boolean equals(Object cameraId) {
            if (cameraId instanceof CameraId) {
                return mIds.equals(((CameraId) cameraId).mIds);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return 53 * mIds.hashCode();
        }
    }

    /**
     * An exception thrown when the {@link CameraUseCaseAdapter} errors in one of its operations.
     */
    public static final class CameraException extends Exception {
        public CameraException() {
            super();
        }

        public CameraException(@NonNull String message) {
            super(message);
        }

        public CameraException(@NonNull Throwable cause) {
            super(cause);
        }
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // Camera interface
    ////////////////////////////////////////////////////////////////////////////////////////////////
    @NonNull
    @Override
    public CameraControl getCameraControl() {
        return mCameraInternal.getCameraControlInternal();
    }

    @NonNull
    @Override
    public CameraInfo getCameraInfo() {
        return mCameraInternal.getCameraInfoInternal();
    }

    @NonNull
    @Override
    public LinkedHashSet<CameraInternal> getCameraInternals() {
        return mCameraInternals;
    }

    @NonNull
    @Override
    public CameraConfig getExtendedConfig() {
        synchronized (mLock) {
            return mCameraConfig;
        }
    }

    @Override
    public void setExtendedConfig(@Nullable CameraConfig cameraConfig) {
        synchronized (mLock) {
            if (cameraConfig == null) {
                mCameraConfig = CameraConfigs.emptyConfig();
                return;
            }
            mCameraConfig = cameraConfig;
        }
    }

    /**
     * Notify the attached use cases change to the listener
     */
    private void notifyAttachedUseCasesChange(@NonNull List<UseCase> useCases) {
        CameraXExecutors.mainThreadExecutor().execute(() -> {
            for (UseCase useCase : useCases) {
                Consumer<Collection<UseCase>> attachedUseCasesUpdateListener =
                        useCase.getCurrentConfig().getAttachedUseCasesUpdateListener(null);

                if (attachedUseCasesUpdateListener != null) {
                    attachedUseCasesUpdateListener.accept(Collections.unmodifiableList(useCases));
                }
            }
        });
    }
}

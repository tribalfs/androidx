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

import android.hardware.camera2.CameraCharacteristics;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.camera.camera2.internal.Camera2ImplConfig;
import androidx.camera.camera2.internal.CameraEventCallback;
import androidx.camera.camera2.internal.CameraEventCallbacks;
import androidx.camera.core.CameraIdFilter;
import androidx.camera.core.CameraInfoUnavailableException;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraX;
import androidx.camera.core.CaptureBundle;
import androidx.camera.core.CaptureConfig;
import androidx.camera.core.CaptureStage;
import androidx.camera.core.Config;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.UseCase;
import androidx.camera.core.impl.utils.CameraSelectorUtil;
import androidx.camera.extensions.ExtensionsErrorListener.ExtensionsErrorCode;
import androidx.camera.extensions.ExtensionsManager.EffectMode;
import androidx.camera.extensions.impl.CaptureProcessorImpl;
import androidx.camera.extensions.impl.CaptureStageImpl;
import androidx.camera.extensions.impl.ImageCaptureExtenderImpl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class for using an OEM provided extension on image capture.
 */
public abstract class ImageCaptureExtender {
    private static final String TAG = "ImageCaptureExtender";
    static final Config.Option<EffectMode> OPTION_IMAGE_CAPTURE_EXTENDER_MODE =
            Config.Option.create("camerax.extensions.imageCaptureExtender.mode", EffectMode.class);

    private ImageCapture.Builder mBuilder;
    private ImageCaptureExtenderImpl mImpl;
    private EffectMode mEffectMode;

    void init(ImageCapture.Builder builder, ImageCaptureExtenderImpl implementation,
            EffectMode effectMode) {
        mBuilder = builder;
        mImpl = implementation;
        mEffectMode = effectMode;
    }

    /**
     * Indicates whether extension function can support with {@link ImageCapture.Builder}
     *
     * @param cameraSelector The selector that determines a camera that will be checked for the
     *                       availability of extensions.
     * @return True if the specific extension function is supported for the camera device.
     */
    public boolean isExtensionAvailable(@NonNull CameraSelector cameraSelector) {
        Integer lensFacing = cameraSelector.getLensFacing();
        Set<String> availableCameraIds = null;
        try {
            availableCameraIds = CameraUtil.getCameraIdSetWithLensFacing(lensFacing);
        } catch (CameraInfoUnavailableException e) {
            // Returns false if camera info is unavailable.
            return false;
        }
        ExtensionCameraIdFilter extensionCameraIdFilter = new ExtensionCameraIdFilter(mImpl);
        availableCameraIds = extensionCameraIdFilter.filter(availableCameraIds);

        return !availableCameraIds.isEmpty();
    }

    /**
     * Enables the derived image capture extension feature.
     *
     * <p>Image capture extension has dependence on preview extension. A
     * PREVIEW_EXTENSION_REQUIRED error will be thrown if corresponding preview extension is not
     * enabled together.
     *
     * @param cameraSelector The selector used to determine the camera for which to enable
     *                       extensions.
     */
    public void enableExtension(@NonNull CameraSelector cameraSelector) {
        // Add extension camera id filter to config.
        ExtensionCameraIdFilter extensionCameraIdFilter = new ExtensionCameraIdFilter(mImpl);
        CameraIdFilter currentCameraIdFilter = mBuilder.getUseCaseConfig().getCameraIdFilter(null);
        CameraSelector.Builder selectorBuilder =
                CameraSelector.Builder.fromSelector(cameraSelector);
        if (currentCameraIdFilter == null) {
            selectorBuilder.appendFilter(extensionCameraIdFilter);
        } else {
            selectorBuilder.appendFilter(currentCameraIdFilter);
            selectorBuilder.appendFilter(extensionCameraIdFilter);
        }

        CameraSelector selectorWithFilters = selectorBuilder.build();
        String cameraId = CameraUtil.getCameraIdUnchecked(selectorWithFilters);
        if (cameraId == null) {
            // If there's no available camera id for the extender to function, just return here
            // and it will be no-ops.
            return;
        }

        mBuilder.setCameraIdFilter(
                CameraSelectorUtil.toCameraDeviceConfig(selectorWithFilters).getCameraIdFilter());

        CameraCharacteristics cameraCharacteristics = CameraUtil.getCameraCharacteristics(cameraId);
        mImpl.init(cameraId, cameraCharacteristics);

        CaptureProcessorImpl captureProcessor = mImpl.getCaptureProcessor();
        if (captureProcessor != null) {
            mBuilder.setCaptureProcessor(new AdaptingCaptureProcessor(captureProcessor));
        }

        if (mImpl.getMaxCaptureStage() > 0) {
            mBuilder.setMaxCaptureStages(mImpl.getMaxCaptureStage());
        }

        ImageCaptureAdapter imageCaptureAdapter = new ImageCaptureAdapter(mImpl, mEffectMode);
        new Camera2ImplConfig.Extender<>(mBuilder).setCameraEventCallback(
                new CameraEventCallbacks(imageCaptureAdapter));
        mBuilder.setUseCaseEventCallback(imageCaptureAdapter);
        mBuilder.setCaptureBundle(imageCaptureAdapter);
        mBuilder.getMutableConfig().insertOption(OPTION_IMAGE_CAPTURE_EXTENDER_MODE, mEffectMode);
        setSupportedResolutions();
    }

    private void setSupportedResolutions() {
        if (ExtensionVersion.getRuntimeVersion().compareTo(Version.VERSION_1_1) < 0) {
            return;
        }

        List<Pair<Integer, Size[]>> supportedResolutions = null;

        try {
            supportedResolutions = mImpl.getSupportedResolutions();
        } catch (NoSuchMethodError e) {
            Log.e(TAG, "getSupportedResolution interface is not implemented in vendor library.");
        }

        if (supportedResolutions != null) {
            mBuilder.setSupportedResolutions(supportedResolutions);
        }
    }

    static void checkPreviewEnabled(EffectMode effectMode, Collection<UseCase> activeUseCases) {
        boolean isPreviewExtenderEnabled = false;
        boolean isMismatched = false;

        // In case all use cases are unbound when doing the check.
        if (activeUseCases == null || activeUseCases.isEmpty()) {
            return;
        }

        for (UseCase useCase : activeUseCases) {
            EffectMode previewExtenderMode = useCase.getUseCaseConfig().retrieveOption(
                    PreviewExtender.OPTION_PREVIEW_EXTENDER_MODE, null);

            if (effectMode == previewExtenderMode) {
                isPreviewExtenderEnabled = true;
            } else if (previewExtenderMode != null) {
                isMismatched = true;
            }
        }

        if (isMismatched) {
            ExtensionsManager.postExtensionsError(
                    ExtensionsErrorCode.MISMATCHED_EXTENSIONS_ENABLED);
        } else if (!isPreviewExtenderEnabled) {
            ExtensionsManager.postExtensionsError(
                    ExtensionsErrorCode.PREVIEW_EXTENSION_REQUIRED);
        }
    }

    /**
     * An implementation to adapt the OEM provided implementation to core.
     */
    static class ImageCaptureAdapter extends CameraEventCallback implements UseCase.EventCallback,
            CaptureBundle {
        final EffectMode mEffectMode;
        private final ImageCaptureExtenderImpl mImpl;
        private final AtomicBoolean mActive = new AtomicBoolean(true);
        private final Object mLock = new Object();
        @GuardedBy("mLock")
        private volatile int mEnabledSessionCount = 0;
        @GuardedBy("mLock")
        private volatile boolean mUnbind = false;

        ImageCaptureAdapter(ImageCaptureExtenderImpl impl, EffectMode effectMode) {
            mImpl = impl;
            mEffectMode = effectMode;
        }

        @Override
        public void onBind(@NonNull String cameraId) {
            if (mActive.get()) {
                CameraCharacteristics cameraCharacteristics = CameraUtil.getCameraCharacteristics(
                        cameraId);
                mImpl.onInit(cameraId, cameraCharacteristics, CameraX.getContext());
            }
        }

        @Override
        public void onUnbind() {
            synchronized (mLock) {
                mUnbind = true;
                if (mEnabledSessionCount == 0) {
                    callDeInit();
                }
            }
        }

        private void callDeInit() {
            if (mActive.get()) {
                mImpl.onDeInit();
                mActive.set(false);
            }
        }

        @Override
        public CaptureConfig onPresetSession() {
            if (mActive.get()) {
                Handler handler = new Handler(Looper.getMainLooper());
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        checkPreviewEnabled(mEffectMode, CameraX.getActiveUseCases());
                    }
                });
                CaptureStageImpl captureStageImpl = mImpl.onPresetSession();
                if (captureStageImpl != null) {
                    return new AdaptingCaptureStage(captureStageImpl).getCaptureConfig();
                }
            }
            return null;
        }

        @Override
        public CaptureConfig onEnableSession() {
            try {
                if (mActive.get()) {
                    CaptureStageImpl captureStageImpl = mImpl.onEnableSession();
                    if (captureStageImpl != null) {
                        return new AdaptingCaptureStage(captureStageImpl).getCaptureConfig();
                    }
                }

                return null;
            } finally {
                synchronized (mLock) {
                    mEnabledSessionCount++;
                }
            }
        }

        @Override
        public CaptureConfig onDisableSession() {
            try {
                if (mActive.get()) {
                    CaptureStageImpl captureStageImpl = mImpl.onDisableSession();
                    if (captureStageImpl != null) {
                        return new AdaptingCaptureStage(captureStageImpl).getCaptureConfig();
                    }
                }

                return null;
            } finally {
                synchronized (mLock) {
                    mEnabledSessionCount--;
                    if (mEnabledSessionCount == 0 && mUnbind) {
                        callDeInit();
                    }
                }
            }
        }

        @Override
        public List<CaptureStage> getCaptureStages() {
            if (mActive.get()) {
                List<CaptureStageImpl> captureStages = mImpl.getCaptureStages();
                if (captureStages != null && !captureStages.isEmpty()) {
                    ArrayList<CaptureStage> ret = new ArrayList<>();
                    for (CaptureStageImpl s : captureStages) {
                        ret.add(new AdaptingCaptureStage(s));
                    }
                    return ret;
                }
            }

            return null;
        }
    }

}

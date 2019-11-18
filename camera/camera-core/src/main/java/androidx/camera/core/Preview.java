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

import static androidx.camera.core.PreviewConfig.IMAGE_INFO_PROCESSOR;
import static androidx.camera.core.PreviewConfig.OPTION_BACKGROUND_EXECUTOR;
import static androidx.camera.core.PreviewConfig.OPTION_CAMERA_ID_FILTER;
import static androidx.camera.core.PreviewConfig.OPTION_CAPTURE_CONFIG_UNPACKER;
import static androidx.camera.core.PreviewConfig.OPTION_DEFAULT_CAPTURE_CONFIG;
import static androidx.camera.core.PreviewConfig.OPTION_DEFAULT_RESOLUTION;
import static androidx.camera.core.PreviewConfig.OPTION_DEFAULT_SESSION_CONFIG;
import static androidx.camera.core.PreviewConfig.OPTION_LENS_FACING;
import static androidx.camera.core.PreviewConfig.OPTION_MAX_RESOLUTION;
import static androidx.camera.core.PreviewConfig.OPTION_PREVIEW_CAPTURE_PROCESSOR;
import static androidx.camera.core.PreviewConfig.OPTION_SESSION_CONFIG_UNPACKER;
import static androidx.camera.core.PreviewConfig.OPTION_SUPPORTED_RESOLUTIONS;
import static androidx.camera.core.PreviewConfig.OPTION_SURFACE_OCCUPANCY_PRIORITY;
import static androidx.camera.core.PreviewConfig.OPTION_TARGET_ASPECT_RATIO;
import static androidx.camera.core.PreviewConfig.OPTION_TARGET_ASPECT_RATIO_CUSTOM;
import static androidx.camera.core.PreviewConfig.OPTION_TARGET_CLASS;
import static androidx.camera.core.PreviewConfig.OPTION_TARGET_NAME;
import static androidx.camera.core.PreviewConfig.OPTION_TARGET_RESOLUTION;
import static androidx.camera.core.PreviewConfig.OPTION_TARGET_ROTATION;
import static androidx.camera.core.PreviewConfig.OPTION_USE_CASE_EVENT_CALLBACK;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Pair;
import android.util.Rational;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.TextureView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.UiThread;
import androidx.camera.core.impl.utils.Threads;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.util.Preconditions;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * A use case that provides a camera preview stream for displaying on-screen.
 *
 * <p>The preview stream is connected to the {@link Surface} provided via
 * {@link PreviewSurfaceCallback}. The application decides how the {@link Surface} is shown,
 * and is responsible for managing the {@link Surface} lifecycle after providing it.
 *
 * <p> To display the preview with the correct orientation, app needs to take different actions
 * based on the source of the Surface. If the {@link Surface} is backed by a {@link SurfaceView},
 * it will always be in the device's display orientation. If the {@link Surface} is backed by
 * {@link ImageReader}, {@link MediaCodec} or other objects, it's the application's
 * responsibility to calculate the rotation. If the {@link Surface} is backed by a
 * {@link SurfaceTexture}, {@link SurfaceTexture#getTransformMatrix(float[])} can be used to
 * transform the preview to natural orientation. The value is available after a frame is pushed
 * to the {@link SurfaceTexture} and its
 * {@link SurfaceTexture.OnFrameAvailableListener#onFrameAvailable(SurfaceTexture)} has been called.
 * {@link TextureView} handles this automatically and always puts the preview in the
 * natural orientation. To further transform the {@link TextureView} to display orientation,
 * the app needs to apply the current display rotation. Example:
 * <pre>
 *     <code>
 *         switch (getWindowManager().getDefaultDisplay().getRotation()) {
 *             case Surface.ROTATION_0:
 *                 displayRotation = 0;
 *                 break;
 *             case Surface.ROTATION_90:
 *                 displayRotation = 90;
 *                 break;
 *             case Surface.ROTATION_180:
 *                 displayRotation = 180;
 *                 break;
 *             case Surface.ROTATION_270:
 *                 displayRotation = 270;
 *                 break;
 *             default:
 *                 throw new UnsupportedOperationException(
 *                         "Unsupported display rotation: " + displayRotation);
 *         }
 *         matrix.postRotate(-displayRotation, centerX, centerY);
 *         textureView.setTransform(matrix);
 *     </code>
 * </pre>
 */
public class Preview extends UseCase {
    /**
     * Provides a static configuration with implementation-agnostic options.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final Defaults DEFAULT_CONFIG = new Defaults();
    private static final String TAG = "Preview";
    @Nullable
    private HandlerThread mProcessingPreviewThread;
    @Nullable
    private Handler mProcessingPreviewHandler;

    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    @Nullable
    PreviewSurfaceCallback mPreviewSurfaceCallback;
    @SuppressWarnings("WeakerAccess") /* Synthetic Accessor */
    @Nullable
    Executor mPreviewSurfaceCallbackExecutor;
    // Cached latest resolution for creating the pipeline as soon as it's ready.
    @Nullable
    private Size mLatestResolution;

    // Synthetic Accessor
    @SuppressWarnings("WeakerAccess")
    SurfaceHolder mSurfaceHolder;

    /**
     * Creates a new preview use case from the given configuration.
     *
     * @param config for this use case instance
     */
    @SuppressWarnings("WeakerAccess")
    @MainThread
    Preview(@NonNull PreviewConfig config) {
        super(config);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    SessionConfig.Builder createPipeline(@NonNull String cameraId, @NonNull PreviewConfig config,
            @NonNull Size resolution) {
        Threads.checkMainThread();
        Preconditions.checkState(isPreviewSurfaceCallbackSet());
        SessionConfig.Builder sessionConfigBuilder = SessionConfig.Builder.createFrom(config);

        final CaptureProcessor captureProcessor = config.getCaptureProcessor(null);
        final CallbackDeferrableSurface callbackDeferrableSurface = new CallbackDeferrableSurface(
                resolution, mPreviewSurfaceCallbackExecutor,
                mPreviewSurfaceCallback);
        if (captureProcessor != null) {
            CaptureStage captureStage = new CaptureStage.DefaultCaptureStage();
            // TODO: To allow user to use an Executor for the processing.

            if (mProcessingPreviewHandler == null) {
                mProcessingPreviewThread = new HandlerThread("ProcessingSurfaceTexture");
                mProcessingPreviewThread.start();
                mProcessingPreviewHandler = new Handler(mProcessingPreviewThread.getLooper());
            }

            ProcessingSurface processingSurface =
                    new ProcessingSurface(
                            resolution.getWidth(),
                            resolution.getHeight(),
                            ImageFormat.YUV_420_888,
                            mProcessingPreviewHandler,
                            captureStage,
                            captureProcessor,
                            callbackDeferrableSurface);

            sessionConfigBuilder.addCameraCaptureCallback(
                    processingSurface.getCameraCaptureCallback());

            mSurfaceHolder = processingSurface;
            sessionConfigBuilder.addSurface(processingSurface);
            sessionConfigBuilder.setTag(captureStage.getId());
        } else {
            final ImageInfoProcessor processor = config.getImageInfoProcessor(null);

            if (processor != null) {
                sessionConfigBuilder.addCameraCaptureCallback(new CameraCaptureCallback() {
                    @Override
                    public void onCaptureCompleted(
                            @NonNull CameraCaptureResult cameraCaptureResult) {
                        super.onCaptureCompleted(cameraCaptureResult);
                        if (processor.process(
                                new CameraCaptureResultImageInfo(cameraCaptureResult))) {
                            notifyUpdated();
                        }
                    }
                });
            }
            mSurfaceHolder = callbackDeferrableSurface;
            sessionConfigBuilder.addSurface(callbackDeferrableSurface);
        }
        sessionConfigBuilder.addErrorListener(new SessionConfig.ErrorListener() {
            @Override
            public void onError(@NonNull SessionConfig sessionConfig,
                    @NonNull SessionConfig.SessionError error) {
                callbackDeferrableSurface.release();

                // Ensure the bound camera has not changed before resetting.
                // TODO(b/143915543): Ensure this never gets called by a camera that is not bound
                //  to this use case so we don't need to do this check.
                if (isCurrentlyBoundCamera(cameraId)) {
                    // Only reset the pipeline when the bound camera is the same.
                    SessionConfig.Builder sessionConfigBuilder = createPipeline(cameraId, config,
                            resolution);

                    attachToCamera(cameraId, sessionConfigBuilder.build());
                    notifyReset();
                }
            }
        });

        return sessionConfigBuilder;
    }

    /**
     * Gets {@link PreviewSurfaceCallback}
     *
     * <p> Setting the callback will signal to the camera that the use case is ready to receive
     * data.
     *
     * <p> To displaying preview with a {@link TextureView}, consider using
     * {@link PreviewSurfaceProviders#createSurfaceTextureProvider(
     *PreviewSurfaceProviders.SurfaceTextureCallback)}
     * to create the callback.
     *
     * @return the last set callback or {@code null} if no listener is set
     */
    @UiThread
    @Nullable
    public PreviewSurfaceCallback getPreviewSurfaceCallback() {
        Threads.checkMainThread();
        return mPreviewSurfaceCallback;
    }

    /**
     * Sets a {@link PreviewSurfaceCallback} to provide Surface for Preview.
     *
     * <p> Setting the callback will signal to the camera that the use case is ready to receive
     * data.
     *
     * <p> To displaying preview with a {@link TextureView}, consider using
     * {@link PreviewSurfaceProviders#createSurfaceTextureProvider(
     *PreviewSurfaceProviders.SurfaceTextureCallback)}
     * to create the callback.
     *
     * @param previewSurfaceCallback PreviewSurfaceCallback that provides a Preview.
     * @param callbackExecutor       on which the previewSurfaceCallback will be triggered.
     */
    @UiThread
    public void setPreviewSurfaceCallback(@NonNull Executor callbackExecutor,
            @Nullable PreviewSurfaceCallback previewSurfaceCallback) {
        Threads.checkMainThread();
        if (previewSurfaceCallback == null) {
            mPreviewSurfaceCallback = null;
            notifyInactive();
        } else {
            mPreviewSurfaceCallback = previewSurfaceCallback;
            mPreviewSurfaceCallbackExecutor = callbackExecutor;
            notifyActive();
            if (mLatestResolution != null) {
                updateConfigAndOutput(getBoundCameraId(), (PreviewConfig) getUseCaseConfig(),
                        mLatestResolution);
            }
        }
    }

    /**
     * Sets a {@link PreviewSurfaceCallback} to provide Surface for Preview.
     *
     * <p> Setting the callback will signal to the camera that the use case is ready to receive
     * data. The callback will be triggered on main thread.
     *
     * @param previewSurfaceCallback PreviewSurfaceCallback that provides a Preview.
     */
    @UiThread
    public void setPreviewSurfaceCallback(@Nullable PreviewSurfaceCallback previewSurfaceCallback) {
        setPreviewSurfaceCallback(CameraXExecutors.mainThreadExecutor(), previewSurfaceCallback);
    }

    /**
     * Checks if {@link PreviewSurfaceCallback} is set by the user.
     */
    @SuppressWarnings("WeakerAccess")
    boolean isPreviewSurfaceCallbackSet() {
        return mPreviewSurfaceCallback != null && mPreviewSurfaceCallbackExecutor != null;
    }


    private void updateConfigAndOutput(@NonNull String cameraId, @NonNull PreviewConfig config,
            @NonNull Size resolution) {
        Preconditions.checkState(isPreviewSurfaceCallbackSet());
        attachToCamera(cameraId, createPipeline(cameraId, config, resolution).build());
    }

    private CameraControlInternal getCurrentCameraControl() {
        String cameraId = getBoundCameraId();
        return getCameraControl(cameraId);
    }

    /**
     * Sets torch on/off.
     *
     * When the torch is on, the torch will remain on during photo capture regardless of flash
     * setting.  When the torch is off, flash will function as set by {@link ImageCapture}.
     *
     * @param torch True if turn on torch, otherwise false
     */
    // TODO(b/143514107): remove this API when CameraControl#enableTorch implementation is done.
    public void enableTorch(boolean torch) {
        getCurrentCameraControl().enableTorch(torch);
    }

    /** True if the torch is on */
    // TODO(b/143514107): remove this API when CameraInfo#getTorchState implementation is done.
    public boolean isTorchOn() {
        return getCurrentCameraControl().isTorchOn();
    }

    @NonNull
    @Override
    public String toString() {
        return TAG + ":" + getName();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @Nullable
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected UseCaseConfig.Builder<?, ?, ?> getDefaultBuilder(LensFacing lensFacing) {
        PreviewConfig defaults = CameraX.getDefaultUseCaseConfig(PreviewConfig.class, lensFacing);
        if (defaults != null) {
            return Builder.fromConfig(defaults);
        }

        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    protected UseCaseConfig<?> applyDefaults(
            @NonNull UseCaseConfig<?> userConfig,
            @Nullable UseCaseConfig.Builder<?, ?, ?> defaultConfigBuilder) {
        PreviewConfig previewConfig = (PreviewConfig) super.applyDefaults(userConfig,
                defaultConfigBuilder);

        CameraDeviceConfig deviceConfig = getBoundDeviceConfig();
        // Checks the device constraints and get the corrected aspect ratio.
        if (deviceConfig != null && CameraX.getSurfaceManager().requiresCorrectedAspectRatio(
                deviceConfig)) {
            ImageOutputConfig imageConfig = previewConfig;
            Rational resultRatio =
                    CameraX.getSurfaceManager().getCorrectedAspectRatio(deviceConfig,
                            imageConfig.getTargetRotation(Surface.ROTATION_0));
            if (resultRatio != null) {
                Builder configBuilder = Builder.fromConfig(previewConfig);
                configBuilder.setTargetAspectRatioCustom(resultRatio);
                previewConfig = configBuilder.getUseCaseConfig();
            }
        }

        return previewConfig;
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public void clear() {
        notifyInactive();
        if (mSurfaceHolder != null) {
            mSurfaceHolder.release();
        }
        super.clear();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    protected Map<String, Size> onSuggestedResolutionUpdated(
            @NonNull Map<String, Size> suggestedResolutionMap) {
        String cameraId = getBoundCameraId();
        Size resolution = suggestedResolutionMap.get(cameraId);
        if (resolution == null) {
            throw new IllegalArgumentException(
                    "Suggested resolution map missing resolution for camera " + cameraId);
        }
        mLatestResolution = resolution;

        if (isPreviewSurfaceCallbackSet()) {
            updateConfigAndOutput(cameraId, (PreviewConfig) getUseCaseConfig(), resolution);
        }
        return suggestedResolutionMap;
    }


    /**
     * A callback for the application to provide a {@link Surface} to CameraX.
     *
     * <p> This interface is implemented by the application to provide a {@link Surface}, and then
     * called by CameraX when a preview output Surface is needed or is no longer in use by CameraX.
     *
     * @see Preview#setPreviewSurfaceCallback(PreviewSurfaceCallback)
     */
    public interface PreviewSurfaceCallback {

        /**
         * For the application to create an output Surface with the given resolution.
         *
         * <p> This is called when {@link Preview} needs a valid {@link Surface}. e.g. when the
         * {@link Preview} is bound to lifecycle. If the {@link Surface} is backed by a
         * {@link SurfaceTexture}, both the {@link Surface} and the {@link ListenableFuture} need
         * to be recreated each time this is invoked. The application is also responsible to hold
         * a reference to the {@link SurfaceTexture} since the weak reference from
         * {@link Surface} does not prevent it to be garbage collected.
         *
         * <p> It's most common to use it with a {@link SurfaceView} or a {@link TextureView}.
         * For {@link TextureView}, {@link PreviewSurfaceProviders} for creating {@link Surface}
         * backed by a {@link SurfaceTexture}. For {@link SurfaceView}, the creation is in the
         * hands of the {@link SurfaceView}. Use {@link CallbackToFutureAdapter} to wait for the
         * creation of the {@link Surface} in {@link android.view.SurfaceHolder.Callback
         * #surfaceChanged(android.view.SurfaceHolder, int, int, int)}. Example:
         *
         * <pre><code>
         * class SurfaceViewHandler implements SurfaceHolder.Callback, PreviewSurfaceCallback {
         *
         *     Size mResolution;
         *     CallbackToFutureAdapter.Completer<Surface> mCompleter;
         *
         *     &#64;Override
         *     public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
         *         if (mResolution != null && mCompleter != null && mResolution.getHeight()
         *                 == height && mResolution.getWidth() == width) {
         *             mCompleter.set(holder.getSurface());
         *         }
         *     }
         *
         *     &#64;Override
         *     public ListenableFuture<Surface> createSurfaceFuture(@NonNull Size resolution) {
         *         mResolution = resolution;
         *         return CallbackToFutureAdapter.getFuture(completer -> {
         *             mCompleter = completer
         *         });
         *     }
         * }
         * </code></pre>
         *
         * @param resolution the resolution of the {@link Surface} to create. The value is
         *                   based on the coordinate system of the image sensor.
         * @return A ListenableFuture that contains the application created Surface.
         */
        @NonNull
        ListenableFuture<Surface> createSurfaceFuture(@NonNull Size resolution);

        /**
         * Called when the {@link Surface} is safe to be released.
         *
         * <p> This method is called when the {@link Surface} previously returned from
         * {@link #createSurfaceFuture(Size)} is no longer being used by the camera system, and
         * it's safe to be released during or after this is called. The application is
         * responsible to release the {@link Surface} when it's also no longer being used by the
         * app.
         *
         * @param surfaceFuture the {@link Surface} to be released.
         */
        void onSafeToRelease(@NonNull ListenableFuture<Surface> surfaceFuture);
    }

    /**
     * Provides a base static default configuration for the Preview
     *
     * <p>These values may be overridden by the implementation. They only provide a minimum set of
     * defaults that are implementation independent.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final class Defaults implements ConfigProvider<PreviewConfig> {
        private static final Size DEFAULT_MAX_RESOLUTION =
                CameraX.getSurfaceManager().getPreviewSize();
        private static final int DEFAULT_SURFACE_OCCUPANCY_PRIORITY = 2;

        private static final PreviewConfig DEFAULT_CONFIG;

        static {
            Builder builder =
                    new Builder()
                            .setMaxResolution(DEFAULT_MAX_RESOLUTION)
                            .setSurfaceOccupancyPriority(DEFAULT_SURFACE_OCCUPANCY_PRIORITY);
            DEFAULT_CONFIG = builder.getUseCaseConfig();
        }

        @Override
        public PreviewConfig getConfig(LensFacing lensFacing) {
            return DEFAULT_CONFIG;
        }
    }

    /** Builder for a {@link Preview}. */
    public static final class Builder
            implements UseCaseConfig.Builder<Preview, PreviewConfig, Builder>,
            ImageOutputConfig.Builder<Builder>,
            CameraDeviceConfig.Builder<Builder>,
            ThreadConfig.Builder<Builder> {

        private final MutableOptionsBundle mMutableConfig;

        /** Creates a new Builder object. */
        public Builder() {
            this(MutableOptionsBundle.create());
        }

        private Builder(MutableOptionsBundle mutableConfig) {
            mMutableConfig = mutableConfig;

            Class<?> oldConfigClass =
                    mutableConfig.retrieveOption(TargetConfig.OPTION_TARGET_CLASS, null);
            if (oldConfigClass != null && !oldConfigClass.equals(Preview.class)) {
                throw new IllegalArgumentException(
                        "Invalid target class configuration for "
                                + Builder.this
                                + ": "
                                + oldConfigClass);
            }

            setTargetClass(Preview.class);
        }

        /**
         * Generates a Builder from another Config object
         *
         * @param configuration An immutable configuration to pre-populate this builder.
         * @return The new Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public static Builder fromConfig(@NonNull PreviewConfig configuration) {
            return new Builder(MutableOptionsBundle.from(configuration));
        }

        /**
         * {@inheritDoc}
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public MutableConfig getMutableConfig() {
            return mMutableConfig;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public PreviewConfig getUseCaseConfig() {
            return new PreviewConfig(OptionsBundle.from(mMutableConfig));
        }

        /**
         * Builds an immutable {@link Preview} from the current state.
         *
         * @return A {@link Preview} populated with the current state.
         * @throws IllegalArgumentException if attempting to set both target aspect ratio and
         *                                  target resolution.
         */
        @NonNull
        @Override
        public Preview build() {
            // Error at runtime for using both setTargetResolution and setTargetAspectRatio on
            // the same config.
            if (getMutableConfig().retrieveOption(OPTION_TARGET_ASPECT_RATIO, null) != null
                    && getMutableConfig().retrieveOption(OPTION_TARGET_RESOLUTION, null) != null) {
                throw new IllegalArgumentException(
                        "Cannot use both setTargetResolution and setTargetAspectRatio on the same "
                                + "config.");
            }
            return new Preview(getUseCaseConfig());
        }

        // Implementations of TargetConfig.Builder default methods

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setTargetClass(@NonNull Class<Preview> targetClass) {
            getMutableConfig().insertOption(OPTION_TARGET_CLASS, targetClass);

            // If no name is set yet, then generate a unique name
            if (null == getMutableConfig().retrieveOption(OPTION_TARGET_NAME, null)) {
                String targetName = targetClass.getCanonicalName() + "-" + UUID.randomUUID();
                setTargetName(targetName);
            }

            return this;
        }

        /**
         * Sets the name of the target object being configured, used only for debug logging.
         *
         * <p>The name should be a value that can uniquely identify an instance of the object being
         * configured.
         *
         * <p>If not set, the target name will default to an unique name automatically generated
         * with the class canonical name and random UUID.
         *
         * @param targetName A unique string identifier for the instance of the class being
         *                   configured.
         * @return the current Builder.
         */
        @Override
        @NonNull
        public Builder setTargetName(@NonNull String targetName) {
            getMutableConfig().insertOption(OPTION_TARGET_NAME, targetName);
            return this;
        }

        // Implementations of CameraDeviceConfig.Builder default methods

        /**
         * Sets the primary camera to be configured based on the direction the lens is facing.
         *
         * <p>If multiple cameras exist with equivalent lens facing direction, the first ("primary")
         * camera for that direction will be chosen.
         *
         * @param lensFacing The direction of the camera's lens.
         * @return the current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setLensFacing(@NonNull LensFacing lensFacing) {
            getMutableConfig().insertOption(OPTION_LENS_FACING, lensFacing);
            return this;
        }

        /**
         * Sets a {@link CameraIdFilter} that filter out the unavailable camera id.
         *
         * <p>The camera id filter will be used to filter those cameras with lens facing
         * specified in the config.
         *
         * @param cameraIdFilter The {@link CameraIdFilter}.
         * @return the current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setCameraIdFilter(@NonNull CameraIdFilter cameraIdFilter) {
            getMutableConfig().insertOption(OPTION_CAMERA_ID_FILTER, cameraIdFilter);
            return this;
        }

        // Implementations of ImageOutputConfig.Builder default methods

        /**
         * Sets the aspect ratio of the intended target for images from this configuration.
         *
         * <p>This is the ratio of the target's width to the image's height, where the numerator of
         * the provided {@link Rational} corresponds to the width, and the denominator corresponds
         * to the height.
         *
         * <p>The target aspect ratio is used as a hint when determining the resulting output aspect
         * ratio which may differ from the request, possibly due to device constraints.
         * Application code should check the resulting output's resolution.
         *
         * <p>This method can be used to request an aspect ratio that is not from the standard set
         * of aspect ratios defined in the {@link AspectRatio}.
         *
         * <p>This method will remove any value set by setTargetAspectRatio().
         *
         * <p>For Preview, the value will be used to calculate the suggested resolution size in
         * {@link Preview.PreviewSurfaceCallback#createSurfaceFuture(Size)}.
         *
         * @param aspectRatio A {@link Rational} representing the ratio of the target's width and
         *                    height.
         * @return The current Builder.
         * @hide
         */
        @NonNull
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        public Builder setTargetAspectRatioCustom(@NonNull Rational aspectRatio) {
            getMutableConfig().insertOption(OPTION_TARGET_ASPECT_RATIO_CUSTOM, aspectRatio);
            getMutableConfig().removeOption(OPTION_TARGET_ASPECT_RATIO);
            return this;
        }

        /**
         * Sets the aspect ratio of the intended target for images from this configuration.
         *
         * <p>It is not allowed to set both target aspect ratio and target resolution on the same
         * use case.  Attempting so will throw an IllegalArgumentException when building the
         * Config.
         *
         * <p>The target aspect ratio is used as a hint when determining the resulting output aspect
         * ratio which may differ from the request, possibly due to device constraints.
         * Application code should check the resulting output's resolution.
         *
         * <p>For Preview, the value will be used to calculate the suggested resolution size in
         * {@link Preview.PreviewSurfaceCallback#createSurfaceFuture(Size)}.
         *
         * <p>If not set, resolutions with aspect ratio 4:3 will be considered in higher
         * priority.
         *
         * @param aspectRatio A {@link AspectRatio} representing the ratio of the
         *                    target's width and height.
         * @return The current Builder.
         */
        @NonNull
        @Override
        public Builder setTargetAspectRatio(@AspectRatio int aspectRatio) {
            getMutableConfig().insertOption(OPTION_TARGET_ASPECT_RATIO, aspectRatio);
            return this;
        }

        /**
         * Sets the rotation of the intended target for images from this configuration.
         *
         * <p>This is one of four valid values: {@link Surface#ROTATION_0}, {@link
         * Surface#ROTATION_90}, {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}.
         * Rotation values are relative to the "natural" rotation, {@link Surface#ROTATION_0}.
         *
         * <p>If not set, the target rotation will default to the value of
         * {@link Display#getRotation()} of the default display at the time the use case is created.
         *
         * @param rotation The rotation of the intended target.
         * @return The current Builder.
         * @hide Preview always set the rotation to device's nature orientation.
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder setTargetRotation(@ImageOutputConfig.RotationValue int rotation) {
            getMutableConfig().insertOption(OPTION_TARGET_ROTATION, rotation);
            return this;
        }

        /**
         * Sets the resolution of the intended target from this configuration.
         *
         * <p>The target resolution attempts to establish a minimum bound for the preview
         * resolution. The actual preview resolution will be the closest available resolution in
         * size that is not smaller than the target resolution, as determined by the Camera
         * implementation. However, if no resolution exists that is equal to or larger than the
         * target resolution, the nearest available resolution smaller than the target resolution
         * will be chosen.  Resolutions with the same aspect ratio of the provided {@link Size} will
         * be considered in higher priority before resolutions of different aspect ratios.
         *
         * <p>It is not allowed to set both target aspect ratio and target resolution on the same
         * use case.  Attempting so will throw an IllegalArgumentException when building the
         * Config.
         *
         * <p>The resolution {@link Size} should be expressed at the use cases's target rotation.
         * For example, a device with portrait natural orientation in natural target rotation
         * requesting a portrait image may specify 480x640, and the same device, rotated 90 degrees
         * and targeting landscape orientation may specify 640x480.
         *
         * <p>The maximum available resolution that could be selected for a {@link Preview} is
         * limited to be under 1080p. The limitation of 1080p for {@link Preview} has considered
         * both performance and quality factors that users can obtain reasonable quality and smooth
         * output stream under 1080p.
         *
         * <p>If not set, the default selected resolution will be the best size match to the
         * device's screen resolution, or to 1080p (1920x1080), whichever is smaller.
         *
         * @param resolution The target resolution to choose from supported output sizes list.
         * @return The current Builder.
         */
        @NonNull
        @Override
        public Builder setTargetResolution(@NonNull Size resolution) {
            getMutableConfig()
                    .insertOption(ImageOutputConfig.OPTION_TARGET_RESOLUTION, resolution);
            if (resolution != null) {
                getMutableConfig().insertOption(OPTION_TARGET_ASPECT_RATIO_CUSTOM,
                        new Rational(resolution.getWidth(), resolution.getHeight()));
            }
            return this;
        }

        /**
         * Sets the default resolution of the intended target from this configuration.
         *
         * @param resolution The default resolution to choose from supported output sizes list.
         * @return The current Builder.
         * @hide
         */
        @NonNull
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        public Builder setDefaultResolution(@NonNull Size resolution) {
            getMutableConfig().insertOption(OPTION_DEFAULT_RESOLUTION, resolution);
            return this;
        }

        /** @hide */
        @NonNull
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        public Builder setMaxResolution(@NonNull Size resolution) {
            getMutableConfig().insertOption(OPTION_MAX_RESOLUTION, resolution);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setSupportedResolutions(@NonNull List<Pair<Integer, Size[]>> resolutions) {
            getMutableConfig().insertOption(OPTION_SUPPORTED_RESOLUTIONS, resolutions);
            return this;
        }

        // Implementations of ThreadConfig.Builder default methods

        /**
         * Sets the default executor that will be used for background tasks.
         *
         * <p>If not set, the background executor will default to an automatically generated
         * {@link Executor}.
         *
         * @param executor The executor which will be used for background tasks.
         * @return the current Builder.
         * @hide Background executor not used in {@link Preview}.
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setBackgroundExecutor(@NonNull Executor executor) {
            getMutableConfig().insertOption(OPTION_BACKGROUND_EXECUTOR, executor);
            return this;
        }

        // Implementations of UseCaseConfig.Builder default methods

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setDefaultSessionConfig(@NonNull SessionConfig sessionConfig) {
            getMutableConfig().insertOption(OPTION_DEFAULT_SESSION_CONFIG, sessionConfig);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setDefaultCaptureConfig(@NonNull CaptureConfig captureConfig) {
            getMutableConfig().insertOption(OPTION_DEFAULT_CAPTURE_CONFIG, captureConfig);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setSessionOptionUnpacker(
                @NonNull SessionConfig.OptionUnpacker optionUnpacker) {
            getMutableConfig().insertOption(OPTION_SESSION_CONFIG_UNPACKER, optionUnpacker);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setCaptureOptionUnpacker(
                @NonNull CaptureConfig.OptionUnpacker optionUnpacker) {
            getMutableConfig().insertOption(OPTION_CAPTURE_CONFIG_UNPACKER, optionUnpacker);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setSurfaceOccupancyPriority(int priority) {
            getMutableConfig().insertOption(OPTION_SURFACE_OCCUPANCY_PRIORITY, priority);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setUseCaseEventCallback(
                @NonNull UseCase.EventCallback useCaseEventCallback) {
            getMutableConfig().insertOption(OPTION_USE_CASE_EVENT_CALLBACK, useCaseEventCallback);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setImageInfoProcessor(@NonNull ImageInfoProcessor processor) {
            getMutableConfig().insertOption(IMAGE_INFO_PROCESSOR, processor);
            return this;
        }

        /**
         * Sets the {@link CaptureProcessor}.
         *
         * @param captureProcessor The requested capture processor for extension.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setCaptureProcessor(@NonNull CaptureProcessor captureProcessor) {
            getMutableConfig().insertOption(OPTION_PREVIEW_CAPTURE_PROCESSOR, captureProcessor);
            return this;
        }
    }
}

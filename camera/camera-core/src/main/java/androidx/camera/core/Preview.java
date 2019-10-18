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

import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.View;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.UiThread;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.ImageOutputConfig.RotationValue;
import androidx.camera.core.impl.utils.Threads;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.core.util.Preconditions;

import com.google.auto.value.AutoValue;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * A use case that provides a camera preview stream for displaying on-screen.
 *
 * <p>The preview stream is connected to an underlying {@link SurfaceTexture}.  This SurfaceTexture
 * is created by the Preview use case and provided as an output after it is configured and attached
 * to the camera.  The application receives the SurfaceTexture by setting an output listener with
 * {@link Preview#setOnPreviewOutputUpdateListener(OnPreviewOutputUpdateListener)}. When the
 * lifecycle becomes active, the camera will start and images will be streamed to the
 * SurfaceTexture.
 * {@link OnPreviewOutputUpdateListener#onUpdated(PreviewOutput)} is called when a
 * new SurfaceTexture is created.  A SurfaceTexture is created each time the use case becomes
 * active and no previous SurfaceTexture exists.
 *
 * <p>The application can then decide how this texture is shown.  The texture data is as received
 * by the camera system with no rotation applied.  To display the SurfaceTexture with the correct
 * orientation, the rotation parameter sent to {@link Preview.OnPreviewOutputUpdateListener} can
 * be used to create a correct transformation matrix for display. See
 * {@link #setTargetRotation(int)} and {@link PreviewConfig.Builder#setTargetRotation(int)} for
 * details.  See {@link Preview#setOnPreviewOutputUpdateListener(OnPreviewOutputUpdateListener)} for
 * notes if attaching the SurfaceTexture to {@link android.view.TextureView}.
 *
 * <p>The application is responsible for managing SurfaceTexture after receiving it.  See
 * {@link Preview#setOnPreviewOutputUpdateListener(OnPreviewOutputUpdateListener)} for notes on
 * if overriding {@link
 * android.view.TextureView.SurfaceTextureListener#onSurfaceTextureDestroyed(SurfaceTexture)}.
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
    private static final String CONFLICTING_SURFACE_API_ERROR_MESSAGE =
            "PreviewSurfaceCallback cannot be used with OnPreviewOutputUpdateListener.";
    @Nullable
    private HandlerThread mProcessingPreviewThread;
    @Nullable
    private Handler mProcessingPreviewHandler;

    private final PreviewConfig.Builder mUseCaseConfigBuilder;
    @Nullable
    private OnPreviewOutputUpdateListener mSubscribedPreviewOutputListener;
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    @Nullable
    PreviewSurfaceCallback mPreviewSurfaceCallback;
    @SuppressWarnings("WeakerAccess") /* Synthetic Accessor */
    @Nullable
    Executor mPreviewSurfaceCallbackExecutor;
    @Nullable
    private PreviewOutput mLatestPreviewOutput;
    // Cached latest resolution for creating the pipeline as soon as it's ready.
    @Nullable
    private Size mLatestResolution;
    private boolean mSurfaceDispatched = false;
    private SessionConfig.Builder mSessionConfigBuilder;

    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
            SurfaceTextureHolder mSurfaceTextureHolder;

    private Executor mOutputUpdateExecutor;

    /**
     * Creates a new preview use case from the given configuration.
     *
     * @param config for this use case instance
     */
    @MainThread
    public Preview(@NonNull PreviewConfig config) {
        super(config);
        mUseCaseConfigBuilder = PreviewConfig.Builder.fromConfig(config);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    SessionConfig.Builder createPipeline(PreviewConfig config, Size resolution) {
        Threads.checkMainThread();
        SessionConfig.Builder sessionConfigBuilder = SessionConfig.Builder.createFrom(config);

        final CaptureProcessor captureProcessor = config.getCaptureProcessor(null);

        CallbackDeferrableSurface callbackDeferrableSurface =
                isUsingPreviewSurfaceCallback() ? new CallbackDeferrableSurface(
                        resolution, mPreviewSurfaceCallbackExecutor,
                        mPreviewSurfaceCallback) : null;
        if (captureProcessor != null) {
            CaptureStage captureStage = new CaptureStage.DefaultCaptureStage();
            // TODO: To allow user to use an Executor for the processing.

            if (mProcessingPreviewHandler == null) {
                mProcessingPreviewThread = new HandlerThread("ProcessingSurfaceTexture");
                mProcessingPreviewThread.start();
                mProcessingPreviewHandler = new Handler(mProcessingPreviewThread.getLooper());
            }

            ProcessingSurfaceTexture processingSurfaceTexture =
                    new ProcessingSurfaceTexture(
                            resolution.getWidth(),
                            resolution.getHeight(),
                            ImageFormat.YUV_420_888,
                            mProcessingPreviewHandler,
                            captureStage,
                            captureProcessor,
                            callbackDeferrableSurface);

            sessionConfigBuilder.addCameraCaptureCallback(
                    processingSurfaceTexture.getCameraCaptureCallback());

            mSurfaceTextureHolder = processingSurfaceTexture;
            sessionConfigBuilder.addSurface(processingSurfaceTexture);
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

            if (callbackDeferrableSurface != null) {
                mSurfaceTextureHolder = callbackDeferrableSurface;
                sessionConfigBuilder.addSurface(callbackDeferrableSurface);
            } else {
                CheckedSurfaceTexture checkedSurfaceTexture = new CheckedSurfaceTexture(resolution);
                mSurfaceTextureHolder = checkedSurfaceTexture;
                sessionConfigBuilder.addSurface(checkedSurfaceTexture);
            }
        }
        sessionConfigBuilder.addErrorListener(new SessionConfig.ErrorListener() {
            @Override
            public void onError(@NonNull SessionConfig sessionConfig,
                    @NonNull SessionConfig.SessionError error) {
                if (callbackDeferrableSurface == null) {
                    clearPipeline();
                } else {
                    callbackDeferrableSurface.release();
                }
                SessionConfig.Builder sessionConfigBuilder = createPipeline(config, resolution);
                String cameraId = getCameraIdUnchecked(config);
                attachToCamera(cameraId, sessionConfigBuilder.build());
                if (!isUsingPreviewSurfaceCallback()) {
                    updateOutput(mSurfaceTextureHolder.getSurfaceTexture(), resolution);
                }
                notifyReset();
            }
        });

        return sessionConfigBuilder;
    }


    /**
     * Clear the internal pipeline so that the pipeline can be set up again.
     *
     * @deprecated TODO(b / 117519540): remove after {@link OnPreviewOutputUpdateListener} is
     * removed.
     */
    @Deprecated
    void clearPipeline() {
        Threads.checkMainThread();
        SurfaceTextureHolder surfaceTextureHolder = mSurfaceTextureHolder;
        mSurfaceTextureHolder = null;
        if (surfaceTextureHolder != null) {
            surfaceTextureHolder.release();
        }

        // The handler no longer used by the ProcessingSurfaceTexture, we can close the
        // handlerTread after the ProcessingSurfaceTexture was released.
        if (mProcessingPreviewHandler != null) {
            mProcessingPreviewThread.quitSafely();
            mProcessingPreviewThread = null;
            mProcessingPreviewHandler = null;
        }
    }

    /**
     * Un-register a listener previously registered via
     * {@link Preview#setOnPreviewOutputUpdateListener(OnPreviewOutputUpdateListener)}.
     * It will signal to the camera that the camera should no longer stream data to the last
     * {@link PreviewOutput}.
     *
     * @throws IllegalStateException If not called on main thread.
     */
    @SuppressLint("PairedRegistration") // TODO(b/117519540): once bug fixed, this API and
    // SuppressLint should be removed.
    @UiThread
    public void removePreviewOutputListener() {
        Threads.checkMainThread();
        if (mSubscribedPreviewOutputListener != null) {
            mSubscribedPreviewOutputListener = null;
            notifyInactive();
        }
    }

    /**
     * Gets {@link OnPreviewOutputUpdateListener}
     *
     * @return the last set listener or {@code null} if no listener is set
     * @throws IllegalStateException If not called on main thread.
     */
    @UiThread
    @Nullable
    public OnPreviewOutputUpdateListener getOnPreviewOutputUpdateListener() {
        Threads.checkMainThread();
        return mSubscribedPreviewOutputListener;
    }

    /**
     * Sets a listener to get the {@link PreviewOutput} updates.
     *
     * <p>Setting this listener will signal to the camera that the use case is ready to receive
     * data.
     *
     * <p>Once {@link OnPreviewOutputUpdateListener#onUpdated(PreviewOutput)}  is called,
     * ownership of the {@link PreviewOutput} and its contents is transferred to the application. It
     * is the application's responsibility to release the last {@link SurfaceTexture} returned by
     * {@link PreviewOutput#getSurfaceTexture()} when a new SurfaceTexture is provided via an update
     * or when the user is finished with the use case.  A SurfaceTexture is created each time the
     * use case becomes active and no previous SurfaceTexture exists.
     * {@link OnPreviewOutputUpdateListener#onUpdated(PreviewOutput)} is called when a new
     * SurfaceTexture is created.
     *
     * <p>Calling {@link android.view.TextureView#setSurfaceTexture(SurfaceTexture)} when the
     * TextureView's SurfaceTexture is already created, should be preceded by calling
     * {@link android.view.ViewGroup#removeView(View)} and
     * {@link android.view.ViewGroup#addView(View)} on the parent view of the TextureView to ensure
     * the setSurfaceTexture() call succeeds.
     *
     * <p>Since {@link OnPreviewOutputUpdateListener#onUpdated(PreviewOutput)} is called when the
     * underlying SurfaceTexture is created, applications that override and return false from {@link
     * android.view.TextureView.SurfaceTextureListener#onSurfaceTextureDestroyed(SurfaceTexture)}
     * should be sure to call {@link android.view.TextureView#setSurfaceTexture(SurfaceTexture)}
     * with the output from the previous {@link PreviewOutput} to attach it to a new TextureView,
     * such as on resuming the application.
     *
     * <p>The listener will run on the UI thread. See
     * {@link Preview#setOnPreviewOutputUpdateListener(Executor, OnPreviewOutputUpdateListener)}
     * to set the updates run on the given executor.
     *
     * @param newListener The listener which will receive {@link PreviewOutput} updates.
     */
    @UiThread
    public void setOnPreviewOutputUpdateListener(
            @NonNull OnPreviewOutputUpdateListener newListener) {
        setOnPreviewOutputUpdateListener(CameraXExecutors.mainThreadExecutor(), newListener);
    }

    /**
     * Sets a listener and its executor to get the {@link PreviewOutput} updates.
     *
     * <p>See {@link Preview#setOnPreviewOutputUpdateListener(OnPreviewOutputUpdateListener)} for
     * more information.
     *
     * @param executor    The executor on which the listener should be invoked.
     * @param newListener The listener which will receive {@link PreviewOutput} updates.
     * @throws IllegalStateException If not called on main thread.
     */
    @UiThread
    public void setOnPreviewOutputUpdateListener(
            @NonNull /* @CallbackExecutor */ Executor executor,
            @NonNull OnPreviewOutputUpdateListener newListener) {
        Threads.checkMainThread();
        Preconditions.checkState(mPreviewSurfaceCallback == null,
                CONFLICTING_SURFACE_API_ERROR_MESSAGE);
        mOutputUpdateExecutor = executor;
        OnPreviewOutputUpdateListener oldListener = mSubscribedPreviewOutputListener;
        mSubscribedPreviewOutputListener = newListener;
        if (oldListener == null && newListener != null) {
            notifyActive();
            if (mLatestPreviewOutput != null) {
                mSurfaceDispatched = true;
                updateListener(newListener, mLatestPreviewOutput);
            }
        } else if (oldListener != null && oldListener != newListener) {
            if (mLatestPreviewOutput != null) {
                PreviewConfig config = (PreviewConfig) getUseCaseConfig();
                Size resolution = mLatestPreviewOutput.getTextureSize();
                updateConfigAndOutput(config, resolution);
                notifyReset();
            }
        }
    }

    /**
     * Gets {@link PreviewSurfaceCallback}
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
            Preconditions.checkState(mSubscribedPreviewOutputListener == null,
                    CONFLICTING_SURFACE_API_ERROR_MESSAGE);
            mPreviewSurfaceCallback = previewSurfaceCallback;
            mPreviewSurfaceCallbackExecutor = callbackExecutor;
            notifyActive();
            if (mLatestResolution != null) {
                updateConfigAndOutput((PreviewConfig) getUseCaseConfig(), mLatestResolution);
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
     * Checks if we are using the new {@link PreviewSurfaceCallback}.
     *
     * TODO(b/117519540): Once the {@link OnPreviewOutputUpdateListener} is removed, we should
     * remove this method along with all the code it's guarding.
     */
    @SuppressWarnings("WeakerAccess")
    boolean isUsingPreviewSurfaceCallback() {
        Preconditions.checkState(
                mSubscribedPreviewOutputListener == null || mPreviewSurfaceCallback == null,
                CONFLICTING_SURFACE_API_ERROR_MESSAGE);
        return mPreviewSurfaceCallback != null && mPreviewSurfaceCallbackExecutor != null;
    }

    private void updateConfigAndOutput(PreviewConfig config, Size resolution) {
        String cameraId = getCameraIdUnchecked(config);

        mSessionConfigBuilder = createPipeline(config, resolution);
        attachToCamera(cameraId, mSessionConfigBuilder.build());
        if (!isUsingPreviewSurfaceCallback()) {
            updateOutput(mSurfaceTextureHolder.getSurfaceTexture(), resolution);
        }
    }

    private void updateListener(OnPreviewOutputUpdateListener listener, PreviewOutput output) {
        try {
            mOutputUpdateExecutor.execute(() -> listener.onUpdated(output));
        } catch (RejectedExecutionException e) {
            Log.e(TAG, "Unable to post to the supplied executor.", e);
        }
    }

    private CameraControlInternal getCurrentCameraControl() {
        PreviewConfig config = (PreviewConfig) getUseCaseConfig();
        String cameraId = getCameraIdUnchecked(config);
        return getCameraControl(cameraId);
    }

    /**
     * Adjusts the preview to zoom to a local region.
     *
     * <p>Setting the zoom is equivalent to setting a scalar crop region (digital zoom), and zoom
     * occurs about the center of the image.
     *
     * <p>Dimensions of the sensor coordinate frame can be found using Camera2.
     *
     * @param crop rectangle with dimensions in sensor coordinate frame for zooming
     */
    public void zoom(@Nullable Rect crop) {
        getCurrentCameraControl().setCropRegion(crop);
    }

    /**
     * Sets torch on/off.
     *
     * When the torch is on, the torch will remain on during photo capture regardless of flash
     * setting.  When the torch is off, flash will function as set by {@link ImageCapture}.
     *
     * @param torch True if turn on torch, otherwise false
     */
    public void enableTorch(boolean torch) {
        getCurrentCameraControl().enableTorch(torch);
    }

    /** True if the torch is on */
    public boolean isTorchOn() {
        return getCurrentCameraControl().isTorchOn();
    }

    /**
     * Sets the target rotation.
     *
     * <p>This informs the use case so it can adjust the rotation value sent to
     * {@link Preview.OnPreviewOutputUpdateListener}.
     *
     * <p>In most cases this should be set to the current rotation returned by {@link
     * Display#getRotation()}. In that case, the rotation values output by the use case will be
     * the rotation, which if applied to the output image, will make the image match the display
     * orientation.
     *
     * <p>If no target rotation is set by the application, it is set to the value of
     * {@link Display#getRotation()} of the default display at the time the
     * use case is created.
     *
     * @param rotation Rotation of the surface texture consumer expressed as one of
     *                 {@link Surface#ROTATION_0}, {@link Surface#ROTATION_90},
     *                 {@link Surface#ROTATION_180}, or {@link Surface#ROTATION_270}.
     */
    public void setTargetRotation(@RotationValue int rotation) {
        ImageOutputConfig oldConfig = (ImageOutputConfig) getUseCaseConfig();
        int oldRotation = oldConfig.getTargetRotation(ImageOutputConfig.INVALID_ROTATION);
        if (oldRotation == ImageOutputConfig.INVALID_ROTATION || oldRotation != rotation) {
            mUseCaseConfigBuilder.setTargetRotation(rotation);
            updateUseCaseConfig(mUseCaseConfigBuilder.build());

            // TODO(b/122846516): Update session configuration and possibly reconfigure session.
            // For now we'll just attempt to update the rotation metadata.
            invalidateMetadata();
        }
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
            return PreviewConfig.Builder.fromConfig(defaults);
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
    protected void updateUseCaseConfig(UseCaseConfig<?> useCaseConfig) {
        PreviewConfig config = (PreviewConfig) useCaseConfig;
        // Checks the device constraints and get the corrected aspect ratio.
        if (CameraX.getSurfaceManager().requiresCorrectedAspectRatio(config)) {
            Rational resultRatio = CameraX.getSurfaceManager().getCorrectedAspectRatio(config);
            PreviewConfig.Builder configBuilder = PreviewConfig.Builder.fromConfig(config);
            configBuilder.setTargetAspectRatioCustom(resultRatio);
            config = configBuilder.build();
        }
        super.updateUseCaseConfig(config);
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public void clear() {
        removePreviewOutputListener();
        notifyInactive();

        SurfaceTexture oldTexture =
                (mLatestPreviewOutput == null)
                        ? null
                        : mLatestPreviewOutput.getSurfaceTexture();
        if (oldTexture != null && !mSurfaceDispatched) {
            oldTexture.release();
        }

        clearPipeline();
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
        PreviewConfig config = (PreviewConfig) getUseCaseConfig();
        String cameraId = getCameraIdUnchecked(config);
        Size resolution = suggestedResolutionMap.get(cameraId);
        if (resolution == null) {
            throw new IllegalArgumentException(
                    "Suggested resolution map missing resolution for camera " + cameraId);
        }
        mLatestResolution = resolution;
        updateConfigAndOutput(config, resolution);
        return suggestedResolutionMap;
    }

    @UiThread
    private void invalidateMetadata() {
        // TODO(b/117519540): Find a way to pass relative rotation to user.
        if (!isUsingPreviewSurfaceCallback() && mLatestPreviewOutput != null) {
            // Only update the output if we have a SurfaceTexture. Otherwise we'll wait until a
            // SurfaceTexture is ready.
            updateOutput(
                    mLatestPreviewOutput.getSurfaceTexture(),
                    mLatestPreviewOutput.getTextureSize());
        }
    }

    /**
     * @deprecated TODO(b / 117519540) remove after {@link OnPreviewOutputUpdateListener} is
     * removed.
     */
    @Deprecated
    @UiThread
    void updateOutput(SurfaceTexture surfaceTexture, Size resolution) {
        Preconditions.checkState(!isUsingPreviewSurfaceCallback());
        PreviewConfig useCaseConfig = (PreviewConfig) getUseCaseConfig();

        int relativeRotation =
                (mLatestPreviewOutput == null) ? 0
                        : mLatestPreviewOutput.getRotationDegrees();
        try {
            // Attempt to get the camera ID. If this fails, we probably don't have permission, so we
            // will rely on the updated UseCaseConfig to set the correct rotation in
            // onSuggestedResolutionUpdated()
            String cameraId = getCameraIdUnchecked(useCaseConfig);
            CameraInfoInternal cameraInfoInternal = CameraX.getCameraInfo(cameraId);
            relativeRotation =
                    cameraInfoInternal.getSensorRotationDegrees(
                            useCaseConfig.getTargetRotation(Surface.ROTATION_0));
        } catch (CameraInfoUnavailableException e) {
            Log.e(TAG, "Unable to update output metadata: " + e);
        }

        PreviewOutput newOutput =
                PreviewOutput.create(surfaceTexture, resolution, relativeRotation);

        // Only update the output if something has changed
        if (!Objects.equals(mLatestPreviewOutput, newOutput)) {
            SurfaceTexture oldTexture =
                    (mLatestPreviewOutput == null)
                            ? null
                            : mLatestPreviewOutput.getSurfaceTexture();
            OnPreviewOutputUpdateListener outputListener = getOnPreviewOutputUpdateListener();

            mLatestPreviewOutput = newOutput;

            boolean textureChanged = oldTexture != surfaceTexture;
            if (textureChanged) {
                // If the old surface was never dispatched, we can safely release the old
                // SurfaceTexture.
                if (oldTexture != null && !mSurfaceDispatched) {
                    oldTexture.release();
                }

                // Keep track of whether this SurfaceTexture is dispatched
                mSurfaceDispatched = false;
            }

            if (outputListener != null) {
                mSurfaceDispatched = true;
                updateListener(outputListener, newOutput);
            }
        }
    }

    /** Describes the error that occurred during preview operation. */
    public enum UseCaseError {
        /** Unknown error occurred. See message or log for more details. */
        UNKNOWN_ERROR
    }

    /**
     * A listener of {@link PreviewOutput}.
     *
     * TODO(b/117519540): Mark as deprecated once PreviewSurfaceCallback is ready.
     */
    public interface OnPreviewOutputUpdateListener {
        /** Callback when PreviewOutput has been updated. */
        void onUpdated(@NonNull PreviewOutput output);
    }

    /**
     * A callback to access the Preview Surface.
     */
    public interface PreviewSurfaceCallback {

        /**
         * Creates preview output Surface with the given resolution and format.
         *
         * <p> This is called when Preview needs a valid Surface. e.g. when the Preview is bound
         * to lifecycle. If the {@link Surface} is backed by a {@link SurfaceTexture}, both the
         * {@link Surface} and the {@link ListenableFuture} need to be recreated each time this
         * is invoked.
         *
         * @param resolution  the resolution required by CameraX.
         * @param imageFormat the {@link ImageFormat} required by CameraX.
         * @return A ListenableFuture that contains the user created Surface.
         */
        @NonNull
        ListenableFuture<Surface> createSurfaceFuture(@NonNull Size resolution, int imageFormat);

        /**
         * Called when the {@link Surface} is safe to release.
         *
         * <p> This method is called when the {@link Surface} previously
         * returned from {@link #createSurfaceFuture(Size, int)} is no longer in use. If the
         * {@link Surface} is backed by a {@link SurfaceTexture}, it should be released to avoid
         * leak.
         *
         * @param surfaceFuture the {@link Surface} to release.
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
            PreviewConfig.Builder builder =
                    new PreviewConfig.Builder()
                            .setMaxResolution(DEFAULT_MAX_RESOLUTION)
                            .setSurfaceOccupancyPriority(DEFAULT_SURFACE_OCCUPANCY_PRIORITY);
            DEFAULT_CONFIG = builder.build();
        }

        @Override
        public PreviewConfig getConfig(LensFacing lensFacing) {
            if (lensFacing != null) {
                PreviewConfig.Builder configBuilder = PreviewConfig.Builder.fromConfig(
                        DEFAULT_CONFIG);
                configBuilder.setLensFacing(lensFacing);
                return configBuilder.build();
            } else {
                return DEFAULT_CONFIG;
            }
        }
    }

    /**
     * A bundle containing a {@link SurfaceTexture} and properties needed to display a Preview.
     */
    @AutoValue
    public abstract static class PreviewOutput {

        PreviewOutput() {
        }

        static PreviewOutput create(
                SurfaceTexture surfaceTexture, Size textureSize, int rotationDegrees) {
            return new AutoValue_Preview_PreviewOutput(
                    surfaceTexture, textureSize, rotationDegrees);
        }

        /** Returns the PreviewOutput that receives image data. */
        @NonNull
        public abstract SurfaceTexture getSurfaceTexture();

        /** Returns the dimensions of the PreviewOutput. */
        @NonNull
        public abstract Size getTextureSize();

        /**
         * Returns the rotation required, in degrees, to transform the PreviewOutput to match the
         * orientation given by ImageOutputConfig#getTargetRotation(int).
         *
         * <p>This number is independent of any rotation value that can be derived from the
         * PreviewOutput's {@link SurfaceTexture#getTransformMatrix(float[])}.
         */
        public abstract int getRotationDegrees();
    }
}

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

import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.location.Location;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.UiThread;
import androidx.camera.core.CameraCaptureMetaData.AeState;
import androidx.camera.core.CameraCaptureMetaData.AfMode;
import androidx.camera.core.CameraCaptureMetaData.AfState;
import androidx.camera.core.CameraCaptureMetaData.AwbState;
import androidx.camera.core.CameraCaptureResult.EmptyCameraCaptureResult;
import androidx.camera.core.CameraX.LensFacing;
import androidx.camera.core.ImageOutputConfiguration.RotationValue;

import com.google.common.base.Function;
import com.google.common.util.concurrent.AsyncCallable;
import com.google.common.util.concurrent.AsyncFunction;
import com.google.common.util.concurrent.FluentFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A use case for taking a picture.
 *
 * <p>This class is designed for basic picture taking. It provides simple controls on how a picture
 * will be taken. The caller is responsible for deciding how to use the captured picture, such as
 * saving the picture to a file.
 *
 * <p>The captured image is made available through an {@link ImageReader} which is passed to an
 * {@link ImageCaptureUseCase.OnImageCapturedListener}.
 */
public class ImageCaptureUseCase extends BaseUseCase {
    /**
     * Provides a static configuration with implementation-agnostic options.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final Defaults DEFAULT_CONFIG = new Defaults();
    private static final String TAG = "ImageCaptureUseCase";
    private static final long CHECK_3A_TIMEOUT_IN_MS = 1000L;
    private static final int MAX_IMAGES = 2;
    // Empty metadata object used as a placeholder for no user-supplied metadata.
    // Should be initialized to all default values.
    private static final Metadata EMPTY_METADATA = new Metadata();
    final Handler mHandler;
    final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final SessionConfiguration.Builder mSessionConfigBuilder;
    private final ArrayDeque<ImageCaptureRequest> mImageCaptureRequests = new ArrayDeque<>();
    private final ExecutorService mExecutor =
            Executors.newFixedThreadPool(
                    1,
                    new ThreadFactory() {
                        private final AtomicInteger mId = new AtomicInteger(0);

                        @Override
                        public Thread newThread(Runnable r) {
                            return new Thread(
                                    r,
                                    CameraXThreads.TAG + "image_capture_" + mId.getAndIncrement());
                        }
                    });
    private final CaptureCallbackChecker mSessionCallbackChecker = new CaptureCallbackChecker();
    private final CaptureMode mCaptureMode;
    private final ImageCaptureUseCaseConfiguration.Builder mUseCaseConfigBuilder;
    private ImageCaptureUseCaseConfiguration mConfiguration;
    private ImageReaderProxy mImageReader;
    /**
     * A flag to check 3A converged or not.
     *
     * <p>In order to speed up the taking picture process, trigger AF / AE should be skipped when
     * the flag is disabled. Set it to be enabled in the maximum quality mode and disabled in the
     * minimum latency mode.
     */
    private boolean mEnableCheck3AConverged;
    /** Current flash mode. */
    private FlashMode mFlashMode;

    /**
     * Creates a new image capture use case from the given configuration.
     *
     * @param userConfiguration for this use case instance
     */
    public ImageCaptureUseCase(ImageCaptureUseCaseConfiguration userConfiguration) {
        super(userConfiguration);
        mUseCaseConfigBuilder =
                ImageCaptureUseCaseConfiguration.Builder.fromConfig(userConfiguration);
        setImageFormat(ImageReaderFormatRecommender.chooseCombo().imageCaptureFormat());
        // Ensure we're using the combined configuration (user config + defaults)
        mConfiguration = (ImageCaptureUseCaseConfiguration) getUseCaseConfiguration();
        mCaptureMode = mConfiguration.getCaptureMode();
        mFlashMode = mConfiguration.getFlashMode();

        if (mCaptureMode == CaptureMode.MAX_QUALITY) {
            mEnableCheck3AConverged = true; // check 3A convergence in MAX_QUALITY mode
        } else if (mCaptureMode == CaptureMode.MIN_LATENCY) {
            mEnableCheck3AConverged = false; // skip 3A convergence in MIN_LATENCY mode
        }

        mHandler = mConfiguration.getCallbackHandler(null);
        if (mHandler == null) {
            throw new IllegalStateException("No default handler specified.");
        }

        mSessionConfigBuilder = SessionConfiguration.Builder.createFrom(mConfiguration);
        mSessionConfigBuilder.setCameraCaptureCallback(mSessionCallbackChecker);
    }

    private static String getCameraIdUnchecked(LensFacing lensFacing) {
        try {
            return CameraX.getCameraWithLensFacing(lensFacing);
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Unable to get camera id for camera lens facing " + lensFacing, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @Nullable
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected UseCaseConfiguration.Builder<?, ?, ?> getDefaultBuilder() {
        ImageCaptureUseCaseConfiguration defaults =
                CameraX.getDefaultUseCaseConfiguration(ImageCaptureUseCaseConfiguration.class);
        if (defaults != null) {
            return ImageCaptureUseCaseConfiguration.Builder.fromConfig(defaults);
        }

        return null;
    }

    private CameraControl getCurrentCameraControl() {
        String cameraId = getCameraIdUnchecked(mConfiguration.getLensFacing());
        return getCameraControl(cameraId);
    }

    /** Configures flash mode to CameraControl once it is ready. */
    @Override
    protected void onCameraControlReady(String cameraId) {
        getCameraControl(cameraId).setFlashMode(mFlashMode);
    }

    /**
     * Get the flash mode.
     *
     * @return the {@link FlashMode}.
     */
    public FlashMode getFlashMode() {
        return mFlashMode;
    }

    /**
     * Set the flash mode.
     *
     * @param flashMode the {@link FlashMode}.
     */
    public void setFlashMode(FlashMode flashMode) {
        this.mFlashMode = flashMode;
        getCurrentCameraControl().setFlashMode(flashMode);
    }

    /**
     * Sets target aspect ratio.
     *
     * @param aspectRatio New target aspect ratio.
     */
    public void setTargetAspectRatio(Rational aspectRatio) {
        ImageOutputConfiguration oldconfig = (ImageOutputConfiguration) getUseCaseConfiguration();
        Rational oldRatio = oldconfig.getTargetAspectRatio(null);
        if (!aspectRatio.equals(oldRatio)) {
            mUseCaseConfigBuilder.setTargetAspectRatio(aspectRatio);
            updateUseCaseConfiguration(mUseCaseConfigBuilder.build());
            mConfiguration = (ImageCaptureUseCaseConfiguration) getUseCaseConfiguration();

            // TODO(b/122846516): Reconfigure capture session if the ratio is changed drastically.
        }
    }

    /**
     * Sets the desired rotation of the output image.
     *
     * <p>This will affect the rotation of the saved image or the rotation value returned by the
     * {@link OnImageCapturedListener}.
     *
     * <p>In most cases this should be set to the current rotation returned by {@link
     * Display#getRotation()}.
     *
     * @param rotation Desired rotation of the output image.
     */
    public void setTargetRotation(@RotationValue int rotation) {
        ImageOutputConfiguration oldconfig = (ImageOutputConfiguration) getUseCaseConfiguration();
        int oldRotation = oldconfig.getTargetRotation(ImageOutputConfiguration.INVALID_ROTATION);
        if (oldRotation == ImageOutputConfiguration.INVALID_ROTATION || oldRotation != rotation) {
            mUseCaseConfigBuilder.setTargetRotation(rotation);
            updateUseCaseConfiguration(mUseCaseConfigBuilder.build());
            mConfiguration = (ImageCaptureUseCaseConfiguration) getUseCaseConfiguration();

            // TODO(b/122846516): Update session configuration and possibly reconfigure session.
        }
    }

    /**
     * Captures a new still image.
     *
     * <p>The listener's callback will be called only once for every invocation of this method. The
     * listener is responsible for calling {@link Image#close()} on the returned image.
     *
     * @param listener for the newly captured image
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void takePicture(final OnImageCapturedListener listener) {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            mMainHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageCaptureUseCase.this.takePicture(listener);
                        }
                    });
            return;
        }

        sendImageCaptureRequest(listener, mHandler);
    }

    /**
     * Captures a new still image and saves to disk.
     *
     * <p>The listener's callback will be called only once for every invocation of this method.
     *
     * @param saveLocation       Location to store the newly captured image.
     * @param imageSavedListener Listener to be called for the newly captured image.
     */
    public void takePicture(File saveLocation, OnImageSavedListener imageSavedListener) {
        takePicture(saveLocation, imageSavedListener, EMPTY_METADATA);
    }

    /**
     * Captures a new still image and saves to disk.
     *
     * <p>The listener's callback will be called only once for every invocation of this method.
     *
     * @param saveLocation       Location to store the newly captured image.
     * @param imageSavedListener Listener to be called for the newly captured image.
     * @param metadata           Metadata to be stored with the saved image. For JPEG this will
     *                           be included in
     *                           EXIF.
     */
    public void takePicture(
            final File saveLocation, final OnImageSavedListener imageSavedListener,
            final Metadata metadata) {
        if (Looper.getMainLooper() != Looper.myLooper()) {
            mMainHandler.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            ImageCaptureUseCase.this.takePicture(saveLocation, imageSavedListener,
                                    metadata);
                        }
                    });
            return;
        }

        /*
         * We need to chain the following callbacks to save the image to disk:
         *
         * +-----------------------+
         * |                       |
         * |ImageCaptureUseCase.   |
         * |OnImageCapturedListener|
         * |                       |
         * +-----------+-----------+
         *             |
         *             |
         * +-----------v-----------+      +----------------------+
         * |                       |      |                      |
         * | ImageSaver.           |      | ImageCaptureUseCase. |
         * | OnImageSavedListener  +------> OnImageSavedListener |
         * |                       |      |                      |
         * +-----------------------+      +----------------------+
         */

        // Convert the ImageSaver.OnImageSavedListener to ImageCaptureUseCase.OnImageSavedListener
        final ImageSaver.OnImageSavedListener imageSavedListenerWrapper =
                new ImageSaver.OnImageSavedListener() {
                    @Override
                    public void onImageSaved(File file) {
                        imageSavedListener.onImageSaved(file);
                    }

                    @Override
                    public void onError(
                            ImageSaver.SaveError error, String message, @Nullable Throwable cause) {
                        UseCaseError useCaseError = UseCaseError.UNKNOWN_ERROR;
                        switch (error) {
                            case FILE_IO_FAILED:
                                useCaseError = UseCaseError.FILE_IO_ERROR;
                                break;
                            default:
                                // Keep the useCaseError as UNKNOWN_ERROR
                                break;
                        }

                        imageSavedListener.onError(useCaseError, message, cause);
                    }
                };

        // Wrap the ImageCaptureUseCase.OnImageSavedListener with an OnImageCapturedListener so it
        // can
        // be put into the capture request queue
        OnImageCapturedListener imageCaptureCallbackWrapper =
                new OnImageCapturedListener() {
                    @Override
                    public void onCaptureSuccess(ImageProxy image, int rotationDegrees) {
                        Handler completionHandler = (mHandler != null) ? mHandler : mMainHandler;
                        IoExecutor.getInstance()
                                .execute(
                                        new ImageSaver(
                                                image,
                                                saveLocation,
                                                rotationDegrees,
                                                metadata.isReversedHorizontal,
                                                metadata.isReversedVertical,
                                                metadata.location,
                                                imageSavedListenerWrapper,
                                                completionHandler));
                    }

                    @Override
                    public void onError(
                            UseCaseError error, String message, @Nullable Throwable cause) {
                        imageSavedListener.onError(error, message, cause);
                    }
                };

        // Always use the mMainHandler for the initial callback so we don't need to double post to
        // another thread
        sendImageCaptureRequest(imageCaptureCallbackWrapper, mMainHandler);
    }

    @UiThread
    private void sendImageCaptureRequest(
            OnImageCapturedListener listener, @Nullable Handler listenerHandler) {

        String cameraId = getCameraIdUnchecked(mConfiguration.getLensFacing());

        // Get the relative rotation or default to 0 if the camera info is unavailable
        int relativeRotation = 0;
        try {
            CameraInfo cameraInfo = CameraX.getCameraInfo(cameraId);
            relativeRotation =
                    cameraInfo.getSensorRotationDegrees(
                            mConfiguration.getTargetRotation(Surface.ROTATION_0));
        } catch (CameraInfoUnavailableException e) {
            Log.e(TAG, "Unable to retrieve camera sensor orientation.", e);
        }

        mImageCaptureRequests.offer(
                new ImageCaptureRequest(listener, listenerHandler, relativeRotation));
        if (mImageCaptureRequests.size() == 1) {
            issueImageCaptureRequests();
        }
    }

    /** Issues saved ImageCaptureRequest. */
    @UiThread
    private void issueImageCaptureRequests() {
        if (mImageCaptureRequests.isEmpty()) {
            return;
        }
        takePictureInternal();
    }

    /**
     * The take picture flow.
     *
     * <p>There are three steps to take a picture.
     *
     * <p>(1) Pre-take picture, which will trigger af/ae scan or open torch if necessary. Then check
     * 3A converged if necessary.
     *
     * <p>(2) Issue take picture single request.
     *
     * <p>(3) Post-take picture, which will cancel af/ae scan or close torch if necessary.
     */
    private void takePictureInternal() {
        final TakePictureState state = new TakePictureState();

        FluentFuture.from(preTakePicture(state))
                .transformAsync(new AsyncFunction<Void, Void>() {
                    @Override
                    public ListenableFuture<Void> apply(Void v) throws Exception {
                        return ImageCaptureUseCase.this.issueTakePicture();
                    }
                }, mExecutor)
                .transformAsync(new AsyncFunction<Void, Void>() {
                    @Override
                    public ListenableFuture<Void> apply(Void v) throws Exception {
                        return ImageCaptureUseCase.this.postTakePicture(state);
                    }
                }, mExecutor)
                .addCallback(
                        new FutureCallback<Void>() {
                            @Override
                            public void onSuccess(Void result) {
                            }

                            @Override
                            public void onFailure(Throwable t) {
                                Log.e(TAG, "takePictureInternal onFailure", t);
                            }
                        },
                        mExecutor);
    }

    @Override
    public String toString() {
        return TAG + ":" + getName();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public void clear() {
        if (mImageReader != null) {
            mImageReader.close();
            mImageReader = null;
        }
        mExecutor.shutdown();
        super.clear();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    protected Map<String, Size> onSuggestedResolutionUpdated(
            Map<String, Size> suggestedResolutionMap) {
        String cameraId = getCameraIdUnchecked(mConfiguration.getLensFacing());
        Size resolution = suggestedResolutionMap.get(cameraId);
        if (resolution == null) {
            throw new IllegalArgumentException(
                    "Suggested resolution map missing resolution for camera " + cameraId);
        }

        if (mImageReader != null) {
            if (mImageReader.getHeight() == resolution.getHeight()
                    && mImageReader.getWidth() == resolution.getWidth()) {
                // Resolution does not need to be updated. Return early.
                return suggestedResolutionMap;
            }
            mImageReader.close();
        }

        mImageReader =
                ImageReaderProxys.createCompatibleReader(
                        cameraId,
                        resolution.getWidth(),
                        resolution.getHeight(),
                        getImageFormat(),
                        MAX_IMAGES,
                        mHandler);

        mImageReader.setOnImageAvailableListener(
                new ImageReaderProxy.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReaderProxy imageReader) {
                        // Call the listener so that the captured image can be processed.
                        ImageCaptureRequest imageCaptureRequest = mImageCaptureRequests.peek();
                        if (imageCaptureRequest != null) {
                            ImageProxy image = null;
                            try {
                                image = imageReader.acquireLatestImage();
                            } catch (IllegalStateException e) {
                                Log.e(TAG, "Failed to acquire latest image.", e);
                            } finally {
                                if (image != null) {
                                    // Remove the first listener from the queue
                                    mImageCaptureRequests.poll();

                                    // Inform the listener
                                    imageCaptureRequest.dispatchImage(image);

                                    ImageCaptureUseCase.this.issueImageCaptureRequests();
                                }
                            }
                        } else {
                            // Flush the queue if we have no requests
                            ImageProxy image = null;
                            try {
                                image = imageReader.acquireLatestImage();
                            } catch (IllegalStateException e) {
                                Log.e(TAG, "Failed to acquire latest image.", e);
                            } finally {
                                if (image != null) {
                                    image.close();
                                }
                            }
                        }
                    }
                },
                mMainHandler);

        mSessionConfigBuilder.clearSurfaces();
        mSessionConfigBuilder.addNonRepeatingSurface(
                new ImmediateSurface(mImageReader.getSurface()));

        attachToCamera(cameraId, mSessionConfigBuilder.build());

        // In order to speed up the take picture process, notifyActive at an early stage to attach
        // the
        // session capture callback to repeating and get capture result all the time.
        notifyActive();

        return suggestedResolutionMap;
    }

    /**
     * Routine before taking picture.
     *
     * <p>For example, trigger 3A scan, open torch and check 3A converged if necessary.
     */
    private ListenableFuture<Void> preTakePicture(final TakePictureState state) {
        return FluentFuture.from(getPreCaptureStateIfNeeded())
                .transformAsync(
                        new AsyncFunction<CameraCaptureResult, Boolean>() {
                            @Override
                            public ListenableFuture<Boolean> apply(
                                    CameraCaptureResult captureResult) throws Exception {
                                state.mPreCaptureState = captureResult;
                                ImageCaptureUseCase.this.triggerAfIfNeeded(state);

                                if (ImageCaptureUseCase.this.isFlashRequired(state)) {
                                    state.mIsFlashTriggered = true;
                                    ImageCaptureUseCase.this.triggerAePrecapture(state);
                                }
                                return ImageCaptureUseCase.this.check3AConverged(state);
                            }
                        },
                        mExecutor)
                // Ignore the 3A convergence result.
                .transform(new Function<Boolean, Void>() {
                    @Override
                    public Void apply(Boolean is3AConverged) {
                        return null;
                    }
                }, mExecutor);
    }

    /**
     * Routine after picture was taken.
     *
     * <p>For example, cancel 3A scan, close torch if necessary.
     */
    private ListenableFuture<Void> postTakePicture(final TakePictureState state) {
        return Futures.submitAsync(
                new AsyncCallable<Void>() {
                    @Override
                    public ListenableFuture<Void> call() throws Exception {
                        ImageCaptureUseCase.this.cancelAfAeTrigger(state);
                        return Futures.immediateFuture(null);
                    }
                },
                mExecutor);
    }

    /**
     * Gets a capture result or not according to current configuration.
     *
     * <p>Conditions to get a capture result.
     *
     * <p>(1) The enableCheck3AConverged is enabled because it needs to know current AF mode and
     * state.
     *
     * <p>(2) The flashMode is AUTO because it needs to know the current AE state.
     */
    // Currently this method is used to prevent there is no repeating surface to get capture result.
    // If app is in min-latency mode and flash ALWAYS/OFF mode, it can still take picture without
    // checking the capture result. Remove this check once no repeating surface issue is fixed.
    private ListenableFuture<CameraCaptureResult> getPreCaptureStateIfNeeded() {
        if (mEnableCheck3AConverged || getFlashMode() == FlashMode.AUTO) {
            return mSessionCallbackChecker.checkCaptureResult(
                    new CaptureCallbackChecker.CaptureResultChecker<CameraCaptureResult>() {
                        @Override
                        public CameraCaptureResult check(
                                @NonNull CameraCaptureResult captureResult) {
                            return captureResult;
                        }
                    });
        }
        return Futures.immediateFuture(null);
    }

    private boolean isFlashRequired(TakePictureState state) {
        switch (getFlashMode()) {
            case ON:
                return true;
            case AUTO:
                return state.mPreCaptureState.getAeState() == AeState.FLASH_REQUIRED;
            case OFF:
                return false;
        }
        throw new AssertionError(getFlashMode());
    }

    private ListenableFuture<Boolean> check3AConverged(TakePictureState state) {
        // Besides enableCheck3AConverged == true (MAX_QUALITY), if flash is triggered we also need
        // to
        // wait for 3A convergence.
        if (!mEnableCheck3AConverged && !state.mIsFlashTriggered) {
            return Futures.immediateFuture(false);
        }

        return mSessionCallbackChecker.checkCaptureResult(
                new CaptureCallbackChecker.CaptureResultChecker<Boolean>() {
                    @Override
                    public Boolean check(@NonNull CameraCaptureResult captureResult) {
                        // If afMode is CAF, don't check af locked to speed up.
                        if ((captureResult.getAfMode() == AfMode.ON_CONTINUOUS_AUTO
                                || (captureResult.getAfState() == AfState.FOCUSED
                                || captureResult.getAfState() == AfState.LOCKED_FOCUSED
                                || captureResult.getAfState()
                                == AfState.LOCKED_NOT_FOCUSED))
                                && captureResult.getAeState() == AeState.CONVERGED
                                && captureResult.getAwbState() == AwbState.CONVERGED) {
                            return true;
                        }
                        // Return null to continue check.
                        return null;
                    }
                },
                CHECK_3A_TIMEOUT_IN_MS,
                false);
    }

    /**
     * Issues the AF scan if needed.
     *
     * <p>If enableCheck3AConverged is disabled or it is in CAF mode, AF scan should not be
     * triggered. Trigger AF scan only in {@link AfMode#ON_MANUAL_AUTO} and current AF state is
     * {@link AfState#INACTIVE}. If the AF mode is {@link AfMode#ON_MANUAL_AUTO} and AF state is not
     * inactive, it means that a manual or auto focus request may be in progress or completed.
     */
    private void triggerAfIfNeeded(TakePictureState state) {
        if (mEnableCheck3AConverged
                && state.mPreCaptureState.getAfMode() == AfMode.ON_MANUAL_AUTO
                && state.mPreCaptureState.getAfState() == AfState.INACTIVE) {
            triggerAf(state);
        }
    }

    /**
     * Issues a {@link CaptureRequest#CONTROL_AF_TRIGGER_START} request to start auto focus scan.
     */
    private void triggerAf(TakePictureState state) {
        state.mIsAfTriggered = true;
        getCurrentCameraControl().triggerAf();
    }

    /**
     * Issues a {@link CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER_START} request to start auto
     * exposure scan.
     */
    private void triggerAePrecapture(TakePictureState state) {
        state.mIsAePrecaptureTriggered = true;
        getCurrentCameraControl().triggerAePrecapture();
    }

    /**
     * Issues {@link CaptureRequest#CONTROL_AF_TRIGGER_CANCEL} or {@link
     * CaptureRequest#CONTROL_AE_PRECAPTURE_TRIGGER_CANCEL} request to cancel auto focus or auto
     * exposure scan.
     */
    private void cancelAfAeTrigger(TakePictureState state) {
        if (!state.mIsAfTriggered && !state.mIsAePrecaptureTriggered) {
            return;
        }
        getCurrentCameraControl()
                .cancelAfAeTrigger(state.mIsAfTriggered, state.mIsAePrecaptureTriggered);
        state.mIsAfTriggered = false;
        state.mIsAePrecaptureTriggered = false;
    }

    // TODO(b/123897971):  move the device specific code once we complete the device workaround
    // module.
    private void applyPixelHdrPlusChangeForCaptureMode(
            CaptureMode captureMode, CaptureRequestConfiguration.Builder takePhotoRequestBuilder) {
        if (Build.MANUFACTURER.equals("Google")
                && (Build.MODEL.equals("Pixel 2") || Build.MODEL.equals("Pixel 3"))) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                switch (captureMode) {
                    case MAX_QUALITY:
                        // enable ZSL to make sure HDR+ is enabled
                        takePhotoRequestBuilder.addCharacteristic(
                                CaptureRequest.CONTROL_ENABLE_ZSL, true);
                        break;
                    case MIN_LATENCY:
                        // disable ZSL to turn off HDR+
                        takePhotoRequestBuilder.addCharacteristic(
                                CaptureRequest.CONTROL_ENABLE_ZSL, false);
                        break;
                }
            }
        }
    }

    /** Issues a take picture request. */
    private ListenableFuture<Void> issueTakePicture() {
        CaptureRequestConfiguration.Builder builder = new CaptureRequestConfiguration.Builder();
        builder.addSurface(new ImmediateSurface(mImageReader.getSurface()));
        builder.setTemplateType(CameraDevice.TEMPLATE_STILL_CAPTURE);

        applyPixelHdrPlusChangeForCaptureMode(mCaptureMode, builder);

        final SettableFuture<Void> future = SettableFuture.create();
        builder.setCameraCaptureCallback(
                new CameraCaptureCallback() {
                    @Override
                    public void onCaptureCompleted(@NonNull CameraCaptureResult result) {
                        future.set(null);
                    }

                    @Override
                    public void onCaptureFailed(@NonNull CameraCaptureFailure failure) {
                        Log.e(
                                TAG,
                                "capture picture get onCaptureFailed with reason "
                                        + failure.getReason());
                        future.set(null);
                    }
                });
        notifySingleCapture(builder.build());
        return future;
    }

    /**
     * Describes the error that occurred during an image capture operation (such as {@link
     * ImageCaptureUseCase.takePicture()}).
     *
     * <p>This is a parameter sent to the error callback functions set in listeners such as {@link
     * ImageCaptureUseCase.OnImageSavedListener.onError}.
     */
    public enum UseCaseError {
        /**
         * An unknown error occurred.
         *
         * <p>See message parameter in onError callback or log for more details.
         */
        UNKNOWN_ERROR,
        /**
         * An error occurred while attempting to read or write a file, such as when saving an image
         * to a File.
         */
        FILE_IO_ERROR
    }

    /**
     * Capture mode options for ImageCaptureUseCase. A picture will always be taken regardless of
     * mode, and the mode will be used on devices that support it.
     */
    public enum CaptureMode {
        /**
         * Optimizes capture pipeline to prioritize image quality over latency. When the capture
         * mode is set to MAX_QUALITY, images may take longer to capture.
         */
        MAX_QUALITY,
        /**
         * Optimizes capture pipeline to prioritize latency over image quality. When the capture
         * mode is set to MIN_LATENCY, images may capture faster but the image quality may be
         * reduced.
         */
        MIN_LATENCY
    }

    /** Listener containing callbacks for image file I/O events. */
    public interface OnImageSavedListener {
        /** Called when an image has been successfully saved. */
        void onImageSaved(@NonNull File file);

        /** Called when an error occurs while attempting to save an image. */
        void onError(
                @NonNull UseCaseError useCaseError,
                @NonNull String message,
                @Nullable Throwable cause);
    }

    /**
     * Listener called when an image capture has completed.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public abstract static class OnImageCapturedListener {
        /**
         * Callback for when the image has been captured.
         *
         * <p>The listener is responsible for closing the supplied {@link Image}.
         */
        public void onCaptureSuccess(ImageProxy image, int rotationDegrees) {
            image.close();
        }

        /** Callback for when an error occurred during image capture. */
        public void onError(
                UseCaseError useCaseError, String message, @Nullable Throwable cause) {
        }
    }

    /**
     * Provides a base static default configuration for the ImageCaptureUseCase
     *
     * <p>These values may be overridden by the implementation. They only provide a minimum set of
     * defaults that are implementation independent.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final class Defaults
            implements ConfigurationProvider<ImageCaptureUseCaseConfiguration> {
        private static final CaptureMode DEFAULT_CAPTURE_MODE = CaptureMode.MIN_LATENCY;
        private static final FlashMode DEFAULT_FLASH_MODE = FlashMode.OFF;
        private static final Handler DEFAULT_HANDLER = new Handler(Looper.getMainLooper());
        private static final Rational DEFAULT_ASPECT_RATIO = new Rational(4, 3);
        private static final int DEFAULT_SURFACE_OCCUPANCY_PRIORITY = 4;

        private static final ImageCaptureUseCaseConfiguration DEFAULT_CONFIG;

        static {
            ImageCaptureUseCaseConfiguration.Builder builder =
                    new ImageCaptureUseCaseConfiguration.Builder()
                            .setCaptureMode(DEFAULT_CAPTURE_MODE)
                            .setFlashMode(DEFAULT_FLASH_MODE)
                            .setCallbackHandler(DEFAULT_HANDLER)
                            .setTargetAspectRatio(DEFAULT_ASPECT_RATIO)
                            .setSurfaceOccupancyPriority(DEFAULT_SURFACE_OCCUPANCY_PRIORITY);

            DEFAULT_CONFIG = builder.build();
        }

        @Override
        public ImageCaptureUseCaseConfiguration getConfiguration() {
            return DEFAULT_CONFIG;
        }
    }

    /** Holder class for metadata that will be saved with captured images. */
    public static final class Metadata {
        /**
         * Indicates an upside down mirroring, equivalent to a horizontal mirroring (reflection)
         * followed by a 180 degree rotation.
         */
        public boolean isReversedHorizontal;
        /** Indicates a left-right mirroring (reflection). */
        public boolean isReversedVertical;
        /** Data representing a geographic location. */
        @Nullable
        public Location location;
    }

    /**
     * An intermediate action recorder while taking picture. It is used to restore certain states.
     * For example, cancel AF/AE scan, and close flash light.
     */
    static final class TakePictureState {
        CameraCaptureResult mPreCaptureState = EmptyCameraCaptureResult.create();
        boolean mIsAfTriggered = false;
        boolean mIsAePrecaptureTriggered = false;
        boolean mIsFlashTriggered = false;
    }

    /**
     * A helper class to check camera capture result.
     *
     * <p>CaptureCallbackChecker is an implementation of {@link CameraCaptureCallback} that checks a
     * specified list of condition and sets a ListenableFuture when the conditions have been met. It
     * is mainly used to continuously capture callbacks to detect specific conditions. It also
     * handles the timeout condition if the check condition does not satisfy the given timeout, and
     * returns the given default value if the timeout is met.
     */
    static final class CaptureCallbackChecker extends CameraCaptureCallback {
        private static final long NO_TIMEOUT = 0L;

        /** Capture listeners. */
        private final Set<CaptureResultListener> mCaptureResultListeners = new HashSet<>();

        @Override
        public void onCaptureCompleted(@NonNull CameraCaptureResult cameraCaptureResult) {
            deliverCaptureResultToListeners(cameraCaptureResult);
        }

        /**
         * Check the capture results of current session capture callback by giving a {@link
         * CaptureResultChecker}.
         *
         * @param checker a CaptureResult checker that returns an object with type T if the check is
         *                complete, returning null to continue the check process.
         * @param <T>     the type parameter for CaptureResult checker.
         * @return a listenable future for capture result check process.
         */
        public <T> ListenableFuture<T> checkCaptureResult(CaptureResultChecker<T> checker) {
            return checkCaptureResult(checker, NO_TIMEOUT, null);
        }

        /**
         * Check the capture results of current session capture callback with timeout limit by
         * giving a {@link CaptureResultChecker}.
         *
         * @param checker     a CaptureResult checker that returns an object with type T if the
         *                    check is
         *                    complete, returning null to continue the check process.
         * @param timeoutInMs used to force stop checking.
         * @param defValue    the default return value if timeout occur.
         * @param <T>         the type parameter for CaptureResult checker.
         * @return a listenable future for capture result check process.
         */
        public <T> ListenableFuture<T> checkCaptureResult(
                final CaptureResultChecker<T> checker, final long timeoutInMs, final T defValue) {
            if (timeoutInMs < NO_TIMEOUT) {
                throw new IllegalArgumentException("Invalid timeout value: " + timeoutInMs);
            }
            final long startTimeInMs =
                    (timeoutInMs != NO_TIMEOUT) ? SystemClock.elapsedRealtime() : 0L;

            final SettableFuture<T> future = SettableFuture.create();
            addListener(
                    new CaptureResultListener() {
                        @Override
                        public boolean onCaptureResult(@NonNull CameraCaptureResult captureResult) {
                            T result = checker.check(captureResult);
                            if (result != null) {
                                future.set(result);
                                return true;
                            } else if (startTimeInMs > 0
                                    && SystemClock.elapsedRealtime() - startTimeInMs
                                    > timeoutInMs) {
                                future.set(defValue);
                                return true;
                            }
                            // Return false to continue check.
                            return false;
                        }
                    });
            return future;
        }

        /**
         * Delivers camera capture result to {@link CaptureCallbackChecker#mCaptureResultListeners}.
         */
        private void deliverCaptureResultToListeners(@NonNull CameraCaptureResult captureResult) {
            synchronized (mCaptureResultListeners) {
                Set<CaptureResultListener> removeSet = null;
                for (CaptureResultListener listener : new HashSet<>(mCaptureResultListeners)) {
                    // Remove listener if the callback return true
                    if (listener.onCaptureResult(captureResult)) {
                        if (removeSet == null) {
                            removeSet = new HashSet<>();
                        }
                        removeSet.add(listener);
                    }
                }
                if (removeSet != null) {
                    mCaptureResultListeners.removeAll(removeSet);
                }
            }
        }

        /** Add capture result listener. */
        private void addListener(CaptureResultListener listener) {
            synchronized (mCaptureResultListeners) {
                mCaptureResultListeners.add(listener);
            }
        }

        /** An interface to check camera capture result. */
        public interface CaptureResultChecker<T> {

            /**
             * The callback to check camera capture result.
             *
             * @param captureResult the camera capture result.
             * @return the check result, return null to continue checking.
             */
            T check(@NonNull CameraCaptureResult captureResult);
        }

        /** An interface to listen to camera capture results. */
        private interface CaptureResultListener {

            /**
             * Callback to handle camera capture results.
             *
             * @param captureResult camera capture result.
             * @return true to finish listening, false to continue listening.
             */
            boolean onCaptureResult(@NonNull CameraCaptureResult captureResult);
        }
    }

    private final class ImageCaptureRequest {
        OnImageCapturedListener mListener;
        @Nullable
        Handler mHandler;
        @RotationValue
        int mRotationDegrees;

        ImageCaptureRequest(
                OnImageCapturedListener listener,
                @Nullable Handler handler,
                @RotationValue int rotationDegrees) {
            mListener = listener;
            mHandler = handler;
            mRotationDegrees = rotationDegrees;
        }

        void dispatchImage(final ImageProxy image) {
            if (mHandler != null && Looper.myLooper() != mHandler.getLooper()) {
                boolean posted =
                        mHandler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        ImageCaptureRequest.this.dispatchImage(image);
                                    }
                                });
                // Unable to post to the supplied handler, close the image.
                if (!posted) {
                    Log.e(TAG, "Unable to post to the supplied handler.");
                    image.close();
                }
                return;
            }

            Rational targetRatio = mConfiguration.getTargetAspectRatio();
            targetRatio = ImageUtil.rotate(targetRatio, mRotationDegrees);
            Size sourceSize = new Size(image.getWidth(), image.getHeight());
            if (ImageUtil.isAspectRatioValid(sourceSize, targetRatio)) {
                image.setCropRect(
                        ImageUtil.computeCropRectFromAspectRatio(sourceSize, targetRatio));
            }

            mListener.onCaptureSuccess(image, mRotationDegrees);
        }
    }
}

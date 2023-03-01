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

package androidx.camera.video;

import static androidx.camera.core.CameraEffect.VIDEO_CAPTURE;
import static androidx.camera.core.impl.ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_CUSTOM_ORDERED_RESOLUTIONS;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_DEFAULT_RESOLUTION;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_MAX_RESOLUTION;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_RESOLUTION_SELECTOR;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_SUPPORTED_RESOLUTIONS;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_TARGET_ROTATION;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_CAMERA_SELECTOR;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_CAPTURE_CONFIG_UNPACKER;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_DEFAULT_CAPTURE_CONFIG;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_DEFAULT_SESSION_CONFIG;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_HIGH_RESOLUTION_DISABLED;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_SESSION_CONFIG_UNPACKER;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_SURFACE_OCCUPANCY_PRIORITY;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_ZSL_DISABLED;
import static androidx.camera.core.impl.utils.Threads.isMainThread;
import static androidx.camera.core.impl.utils.TransformUtils.rectToString;
import static androidx.camera.core.internal.TargetConfig.OPTION_TARGET_CLASS;
import static androidx.camera.core.internal.TargetConfig.OPTION_TARGET_NAME;
import static androidx.camera.core.internal.ThreadConfig.OPTION_BACKGROUND_EXECUTOR;
import static androidx.camera.core.internal.UseCaseEventConfig.OPTION_USE_CASE_EVENT_CALLBACK;
import static androidx.camera.video.StreamInfo.STREAM_ID_ERROR;
import static androidx.camera.video.impl.VideoCaptureConfig.OPTION_VIDEO_ENCODER_INFO_FINDER;
import static androidx.camera.video.impl.VideoCaptureConfig.OPTION_VIDEO_OUTPUT;
import static androidx.camera.video.internal.config.VideoConfigUtil.resolveVideoEncoderConfig;
import static androidx.camera.video.internal.config.VideoConfigUtil.resolveVideoMimeInfo;
import static androidx.core.util.Preconditions.checkState;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.hardware.camera2.CameraDevice;
import android.media.MediaCodec;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.VisibleForTesting;
import androidx.arch.core.util.Function;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Logger;
import androidx.camera.core.ResolutionSelector;
import androidx.camera.core.SurfaceRequest;
import androidx.camera.core.UseCase;
import androidx.camera.core.ViewPort;
import androidx.camera.core.impl.CameraCaptureCallback;
import androidx.camera.core.impl.CameraCaptureResult;
import androidx.camera.core.impl.CameraInfoInternal;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.core.impl.CaptureConfig;
import androidx.camera.core.impl.Config;
import androidx.camera.core.impl.ConfigProvider;
import androidx.camera.core.impl.DeferrableSurface;
import androidx.camera.core.impl.ImageOutputConfig;
import androidx.camera.core.impl.ImageOutputConfig.RotationValue;
import androidx.camera.core.impl.MutableConfig;
import androidx.camera.core.impl.MutableOptionsBundle;
import androidx.camera.core.impl.Observable;
import androidx.camera.core.impl.Observable.Observer;
import androidx.camera.core.impl.OptionsBundle;
import androidx.camera.core.impl.SessionConfig;
import androidx.camera.core.impl.StreamSpec;
import androidx.camera.core.impl.Timebase;
import androidx.camera.core.impl.UseCaseConfig;
import androidx.camera.core.impl.UseCaseConfigFactory;
import androidx.camera.core.impl.utils.Threads;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.core.impl.utils.futures.FutureCallback;
import androidx.camera.core.impl.utils.futures.Futures;
import androidx.camera.core.internal.ThreadConfig;
import androidx.camera.core.processing.DefaultSurfaceProcessor;
import androidx.camera.core.processing.SurfaceEdge;
import androidx.camera.core.processing.SurfaceProcessorNode;
import androidx.camera.video.StreamInfo.StreamState;
import androidx.camera.video.impl.VideoCaptureConfig;
import androidx.camera.video.internal.VideoValidatedEncoderProfilesProxy;
import androidx.camera.video.internal.compat.quirk.DeviceQuirks;
import androidx.camera.video.internal.compat.quirk.ExtraSupportedResolutionQuirk;
import androidx.camera.video.internal.compat.quirk.ImageCaptureFailedWhenVideoCaptureIsBoundQuirk;
import androidx.camera.video.internal.compat.quirk.PreviewDelayWhenVideoCaptureIsBoundQuirk;
import androidx.camera.video.internal.compat.quirk.PreviewStretchWhenVideoCaptureIsBoundQuirk;
import androidx.camera.video.internal.compat.quirk.VideoQualityQuirk;
import androidx.camera.video.internal.config.MimeInfo;
import androidx.camera.video.internal.encoder.InvalidConfigException;
import androidx.camera.video.internal.encoder.VideoEncoderConfig;
import androidx.camera.video.internal.encoder.VideoEncoderInfo;
import androidx.camera.video.internal.encoder.VideoEncoderInfoImpl;
import androidx.camera.video.internal.workaround.VideoEncoderInfoWrapper;
import androidx.concurrent.futures.CallbackToFutureAdapter;
import androidx.core.util.Preconditions;

import com.google.common.util.concurrent.ListenableFuture;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A use case that provides camera stream suitable for video application.
 *
 * <p>VideoCapture is used to create a camera stream suitable for a video application such as
 * recording a high-quality video to a file. The camera stream is used by the extended classes of
 * {@link VideoOutput}.
 * {@link #withOutput(VideoOutput)} can be used to create a VideoCapture instance associated with
 * the given VideoOutput. Take {@link Recorder} as an example,
 * <pre>{@code
 *         VideoCapture<Recorder> videoCapture
 *                 = VideoCapture.withOutput(new Recorder.Builder().build());
 * }</pre>
 * Then {@link #getOutput()} can retrieve the Recorder instance.
 *
 * @param <T> the type of VideoOutput
 */
@RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
public final class VideoCapture<T extends VideoOutput> extends UseCase {
    private static final String TAG = "VideoCapture";
    private static final String SURFACE_UPDATE_KEY =
            "androidx.camera.video.VideoCapture.streamUpdate";
    private static final Defaults DEFAULT_CONFIG = new Defaults();
    private static final boolean ENABLE_SURFACE_PROCESSING_BY_QUIRK;
    private static final boolean USE_TEMPLATE_PREVIEW_BY_QUIRK;

    static {
        boolean hasPreviewStretchQuirk =
                DeviceQuirks.get(PreviewStretchWhenVideoCaptureIsBoundQuirk.class) != null;
        boolean hasPreviewDelayQuirk =
                DeviceQuirks.get(PreviewDelayWhenVideoCaptureIsBoundQuirk.class) != null;
        boolean hasImageCaptureFailedQuirk =
                DeviceQuirks.get(ImageCaptureFailedWhenVideoCaptureIsBoundQuirk.class) != null;
        boolean hasVideoQualityQuirkAndWorkaroundBySurfaceProcessing =
                hasVideoQualityQuirkAndWorkaroundBySurfaceProcessing();
        boolean hasExtraSupportedResolutionQuirk =
                DeviceQuirks.get(ExtraSupportedResolutionQuirk.class) != null;
        USE_TEMPLATE_PREVIEW_BY_QUIRK =
                hasPreviewStretchQuirk || hasPreviewDelayQuirk || hasImageCaptureFailedQuirk;
        ENABLE_SURFACE_PROCESSING_BY_QUIRK =
                hasPreviewDelayQuirk || hasImageCaptureFailedQuirk
                        || hasVideoQualityQuirkAndWorkaroundBySurfaceProcessing
                        || hasExtraSupportedResolutionQuirk;
    }

    @SuppressWarnings("WeakerAccess") // Synthetic access
    DeferrableSurface mDeferrableSurface;
    @Nullable
    private SurfaceEdge mCameraEdge;
    @SuppressWarnings("WeakerAccess") // Synthetic access
    StreamInfo mStreamInfo = StreamInfo.STREAM_INFO_ANY_INACTIVE;
    @SuppressWarnings("WeakerAccess") // Synthetic access
    @NonNull
    SessionConfig.Builder mSessionConfigBuilder = new SessionConfig.Builder();
    @SuppressWarnings("WeakerAccess") // Synthetic access
    ListenableFuture<Void> mSurfaceUpdateFuture = null;
    private SurfaceRequest mSurfaceRequest;
    @SuppressWarnings("WeakerAccess") // Synthetic access
    VideoOutput.SourceState mSourceState = VideoOutput.SourceState.INACTIVE;
    @Nullable
    private SurfaceProcessorNode mNode;
    @Nullable
    private VideoEncoderInfo mVideoEncoderInfo;
    @Nullable
    private Rect mCropRect;

    /**
     * Create a VideoCapture associated with the given {@link VideoOutput}.
     *
     * @throws NullPointerException if {@code videoOutput} is null.
     */
    @NonNull
    public static <T extends VideoOutput> VideoCapture<T> withOutput(@NonNull T videoOutput) {
        return new VideoCapture.Builder<T>(Preconditions.checkNotNull(videoOutput)).build();
    }

    /**
     * Creates a new video capture use case from the given configuration.
     *
     * @param config for this use case instance
     */
    VideoCapture(@NonNull VideoCaptureConfig<T> config) {
        super(config);
    }

    /**
     * Gets the {@link VideoOutput} associated with this VideoCapture.
     *
     * @return the value provided to {@link #withOutput(VideoOutput)} used to create this
     * VideoCapture.
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public T getOutput() {
        return ((VideoCaptureConfig<T>) getCurrentConfig()).getVideoOutput();
    }

    /**
     * Returns the desired rotation of the output video.
     *
     * <p>The rotation can be set by calling {@link VideoCapture#setTargetRotation(int)}. If not
     * set, the target rotation defaults to the value of {@link Display#getRotation()} of the
     * default display at the time the use case is created. The use case is fully created once it
     * has been attached to a camera.
     *
     * @return The rotation of the intended target.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @RotationValue
    public int getTargetRotation() {
        return getTargetRotationInternal();
    }

    /**
     * Sets the desired rotation of the output video.
     *
     * <p>This is one of four valid values: {@link Surface#ROTATION_0},
     * {@link Surface#ROTATION_90}, {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}.
     * Rotation values are relative to the "natural" rotation, {@link Surface#ROTATION_0}.
     *
     * <p>If not set, the target rotation will default to the value of
     * {@link Display#getRotation()} of the default display at the time the use case is
     * created. The use case is fully created once it has been attached to a camera.
     *
     * @param rotation Desired rotation of the output video.
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void setTargetRotation(@RotationValue int rotation) {
        if (setTargetRotationInternal(rotation)) {
            sendTransformationInfoIfReady();
        }
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @SuppressWarnings("unchecked")
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public void onStateAttached() {
        super.onStateAttached();
        Preconditions.checkNotNull(getAttachedStreamSpec(), "The suggested stream "
                + "specification should be already updated and shouldn't be null.");
        Preconditions.checkState(mSurfaceRequest == null, "The surface request should be null "
                + "when VideoCapture is attached.");
        mStreamInfo = fetchObservableValue(getOutput().getStreamInfo(),
                StreamInfo.STREAM_INFO_ANY_INACTIVE);
        mSessionConfigBuilder = createPipeline(getCameraId(),
                (VideoCaptureConfig<T>) getCurrentConfig(), getAttachedStreamSpec());
        applyStreamInfoToSessionConfigBuilder(mSessionConfigBuilder, mStreamInfo);
        updateSessionConfig(mSessionConfigBuilder.build());
        // VideoCapture has to be active to apply SessionConfig's template type.
        notifyActive();
        getOutput().getStreamInfo().addObserver(CameraXExecutors.mainThreadExecutor(),
                mStreamInfoObserver);
        setSourceState(VideoOutput.SourceState.ACTIVE_NON_STREAMING);
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void setViewPortCropRect(@NonNull Rect viewPortCropRect) {
        super.setViewPortCropRect(viewPortCropRect);
        sendTransformationInfoIfReady();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public void onStateDetached() {
        checkState(isMainThread(), "VideoCapture can only be detached on the main thread.");
        setSourceState(VideoOutput.SourceState.INACTIVE);
        getOutput().getStreamInfo().removeObserver(mStreamInfoObserver);
        if (mSurfaceUpdateFuture != null) {
            if (mSurfaceUpdateFuture.cancel(false)) {
                Logger.d(TAG, "VideoCapture is detached from the camera. Surface update "
                        + "cancelled.");
            }
        }
        // Clear the pipeline to close the surface, which releases the codec so that it's
        // available for other applications.
        clearPipeline();
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
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    @Nullable
    public UseCaseConfig<?> getDefaultConfig(boolean applyDefaultConfig,
            @NonNull UseCaseConfigFactory factory) {
        Config captureConfig = factory.getConfig(
                UseCaseConfigFactory.CaptureType.VIDEO_CAPTURE,
                ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY);

        if (applyDefaultConfig) {
            captureConfig = Config.mergeConfigs(captureConfig, DEFAULT_CONFIG.getConfig());
        }

        return captureConfig == null ? null :
                getUseCaseConfigBuilder(captureConfig).getUseCaseConfig();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    @Override
    protected UseCaseConfig<?> onMergeConfig(@NonNull CameraInfoInternal cameraInfo,
            @NonNull UseCaseConfig.Builder<?, ?, ?> builder) {

        updateSupportedResolutionsByQuality(cameraInfo, builder);

        return builder.getUseCaseConfig();
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @NonNull
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public UseCaseConfig.Builder<?, ?, ?> getUseCaseConfigBuilder(@NonNull Config config) {
        return Builder.fromConfig(config);
    }

    private void sendTransformationInfoIfReady() {
        CameraInternal cameraInternal = getCamera();
        SurfaceRequest surfaceRequest = mSurfaceRequest;
        Rect cropRect = mCropRect;
        if (cameraInternal != null && surfaceRequest != null && cropRect != null) {
            int relativeRotation = getRelativeRotation(cameraInternal);
            int targetRotation = getAppTargetRotation();
            if (mCameraEdge != null) {
                mCameraEdge.setRotationDegrees(relativeRotation);
            } else {
                surfaceRequest.updateTransformationInfo(
                        SurfaceRequest.TransformationInfo.of(cropRect, relativeRotation,
                                targetRotation, cameraInternal.getHasTransform()));
            }
        }
    }

    @VisibleForTesting
    @Nullable
    Rect getCropRect() {
        return mCropRect;
    }

    /**
     * Calculates the crop rect.
     *
     * <p>Fall back to the full {@link Surface} rect if {@link ViewPort} crop rect is not
     * available. The returned crop rect is adjusted if it is not valid to the video encoder.
     */
    @NonNull
    private Rect calculateCropRect(@NonNull Size surfaceResolution,
            @Nullable VideoEncoderInfo videoEncoderInfo) {
        Rect cropRect;
        if (getViewPortCropRect() != null) {
            cropRect = getViewPortCropRect();
        } else {
            cropRect = new Rect(0, 0, surfaceResolution.getWidth(), surfaceResolution.getHeight());
        }
        if (videoEncoderInfo == null || videoEncoderInfo.isSizeSupported(cropRect.width(),
                cropRect.height())) {
            return cropRect;
        }
        return adjustCropRectToValidSize(cropRect, surfaceResolution, videoEncoderInfo);
    }

    @SuppressLint("WrongConstant")
    @MainThread
    @NonNull
    private SessionConfig.Builder createPipeline(@NonNull String cameraId,
            @NonNull VideoCaptureConfig<T> config,
            @NonNull StreamSpec streamSpec) {
        Threads.checkMainThread();
        CameraInternal camera = Preconditions.checkNotNull(getCamera());
        Size resolution = streamSpec.getResolution();

        // Currently, VideoCapture uses StreamInfo to handle requests for surface, so
        // handleInvalidate() is not used. But if a different approach is asked in the future,
        // handleInvalidate() can be used as an alternative.
        Runnable onSurfaceInvalidated = this::notifyReset;

        // TODO(b/229410005): The expected FPS range will need to come from the camera rather
        //  than what is requested in the config. For now we use the default range of (30, 30)
        //  for behavioral consistency.
        Range<Integer> targetFpsRange = requireNonNull(
                config.getTargetFramerate(Defaults.DEFAULT_FPS_RANGE));
        MediaSpec mediaSpec = requireNonNull(getMediaSpec());
        VideoCapabilities videoCapabilities = VideoCapabilities.from(camera.getCameraInfo());
        VideoEncoderInfo videoEncoderInfo = getVideoEncoderInfo(config.getVideoEncoderInfoFinder(),
                videoCapabilities, mediaSpec, resolution, targetFpsRange);
        mCropRect = calculateCropRect(resolution, videoEncoderInfo);
        mNode = createNodeIfNeeded(isCropNeeded(mCropRect, resolution));
        // Choose Timebase based on the whether the buffer is copied.
        Timebase timebase;
        if (mNode != null || !camera.getHasTransform()) {
            timebase = camera.getCameraInfoInternal().getTimebase();
        } else {
            // When camera buffers from a REALTIME device are passed directly to a video encoder
            // from the camera, automatic compensation is done to account for differing timebases
            // of the audio and camera subsystems. See the document of
            // CameraMetadata#SENSOR_INFO_TIMESTAMP_SOURCE_REALTIME. So the timebase is always
            // UPTIME when encoder surface is directly sent to camera.
            timebase = Timebase.UPTIME;
        }
        if (mNode != null) {
            // Make sure the previously created camera edge is cleared before creating a new one.
            checkState(mCameraEdge == null);
            SurfaceEdge cameraEdge = new SurfaceEdge(
                    VIDEO_CAPTURE,
                    INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE,
                    streamSpec,
                    getSensorToBufferTransformMatrix(),
                    camera.getHasTransform(),
                    mCropRect,
                    getRelativeRotation(camera),
                    /*mirroring=*/false);
            cameraEdge.addOnInvalidatedListener(onSurfaceInvalidated);
            mCameraEdge = cameraEdge;
            SurfaceProcessorNode.OutConfig outConfig =
                    SurfaceProcessorNode.OutConfig.of(cameraEdge);
            SurfaceProcessorNode.In nodeInput = SurfaceProcessorNode.In.of(
                    cameraEdge,
                    singletonList(outConfig));
            SurfaceProcessorNode.Out nodeOutput = mNode.transform(nodeInput);
            SurfaceEdge appEdge = requireNonNull(nodeOutput.get(outConfig));
            appEdge.addOnInvalidatedListener(
                    () -> onAppEdgeInvalidated(appEdge, camera, config, timebase));
            mSurfaceRequest = appEdge.createSurfaceRequest(camera, targetFpsRange);
            mDeferrableSurface = cameraEdge.getDeferrableSurface();
            DeferrableSurface latestDeferrableSurface = mDeferrableSurface;
            mDeferrableSurface.getTerminationFuture().addListener(() -> {
                // If camera surface is the latest one, it means this pipeline can be abandoned.
                // Clear the pipeline in order to trigger the surface complete event to appSurface.
                if (latestDeferrableSurface == mDeferrableSurface) {
                    clearPipeline();
                }
            }, CameraXExecutors.mainThreadExecutor());
        } else {
            mSurfaceRequest = new SurfaceRequest(resolution, camera, targetFpsRange,
                    onSurfaceInvalidated);
            mDeferrableSurface = mSurfaceRequest.getDeferrableSurface();
        }

        config.getVideoOutput().onSurfaceRequested(mSurfaceRequest, timebase);
        sendTransformationInfoIfReady();
        // Since VideoCapture is in video module and can't be recognized by core module, use
        // MediaCodec class instead.
        mDeferrableSurface.setContainerClass(MediaCodec.class);

        SessionConfig.Builder sessionConfigBuilder = SessionConfig.Builder.createFrom(config);
        sessionConfigBuilder.setExpectedFrameRateRange(streamSpec.getExpectedFrameRateRange());
        sessionConfigBuilder.addErrorListener(
                (sessionConfig, error) -> resetPipeline(cameraId, config, streamSpec));
        if (USE_TEMPLATE_PREVIEW_BY_QUIRK) {
            sessionConfigBuilder.setTemplateType(CameraDevice.TEMPLATE_PREVIEW);
        }

        return sessionConfigBuilder;
    }

    private void onAppEdgeInvalidated(@NonNull SurfaceEdge appEdge, @NonNull CameraInternal camera,
            @NonNull VideoCaptureConfig<T> config, @NonNull Timebase timebase) {
        if (camera == getCamera()) {
            mSurfaceRequest = appEdge.createSurfaceRequest(camera);
            config.getVideoOutput().onSurfaceRequested(mSurfaceRequest, timebase);
            sendTransformationInfoIfReady();
        }
    }

    /**
     * Clear the internal pipeline so that the pipeline can be set up again.
     */
    @MainThread
    private void clearPipeline() {
        Threads.checkMainThread();

        if (mDeferrableSurface != null) {
            mDeferrableSurface.close();
            mDeferrableSurface = null;
        }
        if (mNode != null) {
            mNode.release();
            mNode = null;
        }
        if (mCameraEdge != null) {
            mCameraEdge.close();
            mCameraEdge = null;
        }
        mVideoEncoderInfo = null;
        mCropRect = null;
        mSurfaceRequest = null;
        mStreamInfo = StreamInfo.STREAM_INFO_ANY_INACTIVE;
    }

    @MainThread
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    void resetPipeline(@NonNull String cameraId,
            @NonNull VideoCaptureConfig<T> config,
            @NonNull StreamSpec streamSpec) {
        clearPipeline();

        // Ensure the attached camera has not changed before resetting.
        // TODO(b/143915543): Ensure this never gets called by a camera that is not attached
        //  to this use case so we don't need to do this check.
        if (isCurrentCamera(cameraId)) {
            // Only reset the pipeline when the bound camera is the same.
            mSessionConfigBuilder = createPipeline(cameraId, config, streamSpec);
            applyStreamInfoToSessionConfigBuilder(mSessionConfigBuilder, mStreamInfo);
            updateSessionConfig(mSessionConfigBuilder.build());
            notifyReset();
        }
    }

    /**
     * @hide
     */
    @Nullable
    @RestrictTo(Scope.TESTS)
    SurfaceEdge getCameraEdge() {
        return mCameraEdge;
    }

    /**
     * Provides a base static default configuration for the VideoCapture
     *
     * <p>These values may be overridden by the implementation. They only provide a minimum set of
     * defaults that are implementation independent.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final class Defaults implements ConfigProvider<VideoCaptureConfig<?>> {
        /** Surface occupancy priority to this use case */
        private static final int DEFAULT_SURFACE_OCCUPANCY_PRIORITY = 5;
        private static final VideoOutput DEFAULT_VIDEO_OUTPUT =
                SurfaceRequest::willNotProvideSurface;
        private static final VideoCaptureConfig<?> DEFAULT_CONFIG;

        private static final Function<VideoEncoderConfig, VideoEncoderInfo>
                DEFAULT_VIDEO_ENCODER_INFO_FINDER = createFinder();

        static final Range<Integer> DEFAULT_FPS_RANGE = new Range<>(30, 30);

        static {
            Builder<?> builder = new Builder<>(DEFAULT_VIDEO_OUTPUT)
                    .setSurfaceOccupancyPriority(DEFAULT_SURFACE_OCCUPANCY_PRIORITY)
                    .setVideoEncoderInfoFinder(DEFAULT_VIDEO_ENCODER_INFO_FINDER);

            DEFAULT_CONFIG = builder.getUseCaseConfig();
        }

        @NonNull
        private static Function<VideoEncoderConfig, VideoEncoderInfo> createFinder() {
            return encoderConfig -> {
                try {
                    return VideoEncoderInfoImpl.from(encoderConfig);
                } catch (InvalidConfigException e) {
                    Logger.w(TAG, "Unable to find VideoEncoderInfo", e);
                    return null;
                }
            };
        }

        @NonNull
        @Override
        public VideoCaptureConfig<?> getConfig() {
            return DEFAULT_CONFIG;
        }
    }

    @Nullable
    private MediaSpec getMediaSpec() {
        return fetchObservableValue(getOutput().getMediaSpec(), null);
    }

    private final Observer<StreamInfo> mStreamInfoObserver = new Observer<StreamInfo>() {
        @SuppressWarnings("unchecked")
        @Override
        public void onNewData(@Nullable StreamInfo streamInfo) {
            if (streamInfo == null) {
                throw new IllegalArgumentException("StreamInfo can't be null");
            }
            if (mSourceState == VideoOutput.SourceState.INACTIVE) {
                // VideoCapture is unbound.
                return;
            }
            Logger.d(TAG, "Stream info update: old: " + mStreamInfo + " new: " + streamInfo);

            StreamInfo currentStreamInfo = mStreamInfo;
            mStreamInfo = streamInfo;

            // Doing resetPipeline() includes notifyReset/notifyUpdated(). Doing NotifyReset()
            // includes notifyUpdated(). So we just take actions on higher order item for
            // optimization.
            if (!StreamInfo.NON_SURFACE_STREAM_ID.contains(currentStreamInfo.getId())
                    && !StreamInfo.NON_SURFACE_STREAM_ID.contains(streamInfo.getId())
                    && currentStreamInfo.getId() != streamInfo.getId()) {
                // Reset pipeline if the stream ids are different, which means there's a new
                // surface ready to be requested.
                resetPipeline(getCameraId(), (VideoCaptureConfig<T>) getCurrentConfig(),
                        Preconditions.checkNotNull(getAttachedStreamSpec()));
            } else if ((currentStreamInfo.getId() != STREAM_ID_ERROR
                    && streamInfo.getId() == STREAM_ID_ERROR)
                    || (currentStreamInfo.getId() == STREAM_ID_ERROR
                    && streamInfo.getId() != STREAM_ID_ERROR)) {
                // If id switch to STREAM_ID_ERROR, it means VideoOutput is failed to setup video
                // stream. The surface should be removed from camera. Vice versa.
                applyStreamInfoToSessionConfigBuilder(mSessionConfigBuilder, streamInfo);
                updateSessionConfig(mSessionConfigBuilder.build());
                notifyReset();
            } else if (currentStreamInfo.getStreamState() != streamInfo.getStreamState()) {
                applyStreamInfoToSessionConfigBuilder(mSessionConfigBuilder, streamInfo);
                updateSessionConfig(mSessionConfigBuilder.build());
                notifyUpdated();
            }
        }

        @Override
        public void onError(@NonNull Throwable t) {
            Logger.w(TAG, "Receive onError from StreamState observer", t);
        }
    };

    @MainThread
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    void applyStreamInfoToSessionConfigBuilder(@NonNull SessionConfig.Builder sessionConfigBuilder,
            @NonNull StreamInfo streamInfo) {
        final boolean isStreamError = streamInfo.getId() == StreamInfo.STREAM_ID_ERROR;
        final boolean isStreamActive = streamInfo.getStreamState() == StreamState.ACTIVE;
        if (isStreamError && isStreamActive) {
            throw new IllegalStateException(
                    "Unexpected stream state, stream is error but active");
        }

        sessionConfigBuilder.clearSurfaces();
        if (!isStreamError) {
            if (isStreamActive) {
                sessionConfigBuilder.addSurface(mDeferrableSurface);
            } else {
                sessionConfigBuilder.addNonRepeatingSurface(mDeferrableSurface);
            }
        } // Don't attach surface when stream is invalid.

        setupSurfaceUpdateNotifier(sessionConfigBuilder, isStreamActive);
    }

    @Nullable
    private SurfaceProcessorNode createNodeIfNeeded(boolean isCropNeeded) {
        if (getEffect() != null || ENABLE_SURFACE_PROCESSING_BY_QUIRK || isCropNeeded) {
            Logger.d(TAG, "Surface processing is enabled.");
            return new SurfaceProcessorNode(requireNonNull(getCamera()),
                    getEffect() != null ? getEffect().createSurfaceProcessorInternal() :
                            DefaultSurfaceProcessor.Factory.newInstance());
        }
        return null;
    }

    @VisibleForTesting
    @Nullable
    SurfaceProcessorNode getNode() {
        return mNode;
    }

    /**
     * This method resizes the crop rectangle to a valid size.
     *
     * <p>The valid size must fulfill
     * <ul>
     * <li>The multiple of VideoEncoderInfo.getWidthAlignment()/getHeightAlignment() alignment</li>
     * <li>In the scope of Surface resolution and VideoEncoderInfo.getSupportedWidths()
     * /getSupportedHeights().</li>
     * </ul>
     *
     * <p>When the size is not a multiple of the alignment, it seeks to shrink or enlarge the size
     * with the smallest amount of change and ensures that the size is within the surface
     * resolution and supported widths and heights. The new cropping rectangle position (left,
     * right, top, and bottom) is then calculated by extending or indenting from the center of
     * the original cropping rectangle.
     */
    @NonNull
    private static Rect adjustCropRectToValidSize(@NonNull Rect cropRect, @NonNull Size resolution,
            @NonNull VideoEncoderInfo videoEncoderInfo) {
        Logger.d(TAG, String.format("Adjust cropRect %s by width/height alignment %d/%d and "
                        + "supported widths %s / supported heights %s",
                rectToString(cropRect),
                videoEncoderInfo.getWidthAlignment(),
                videoEncoderInfo.getHeightAlignment(),
                videoEncoderInfo.getSupportedWidths(),
                videoEncoderInfo.getSupportedHeights()
        ));

        // Construct all up/down alignment combinations.
        int widthAlignment = videoEncoderInfo.getWidthAlignment();
        int heightAlignment = videoEncoderInfo.getHeightAlignment();
        Range<Integer> supportedWidths = videoEncoderInfo.getSupportedWidths();
        Range<Integer> supportedHeights = videoEncoderInfo.getSupportedHeights();
        int widthAlignedDown = alignDown(cropRect.width(), widthAlignment, supportedWidths);
        int widthAlignedUp = alignUp(cropRect.width(), widthAlignment, supportedWidths);
        int heightAlignedDown = alignDown(cropRect.height(), heightAlignment, supportedHeights);
        int heightAlignedUp = alignUp(cropRect.height(), heightAlignment, supportedHeights);

        // Use Set to filter out duplicates.
        Set<Size> candidateSet = new HashSet<>();
        addBySupportedSize(candidateSet, widthAlignedDown, heightAlignedDown, resolution,
                videoEncoderInfo);
        addBySupportedSize(candidateSet, widthAlignedDown, heightAlignedUp, resolution,
                videoEncoderInfo);
        addBySupportedSize(candidateSet, widthAlignedUp, heightAlignedDown, resolution,
                videoEncoderInfo);
        addBySupportedSize(candidateSet, widthAlignedUp, heightAlignedUp, resolution,
                videoEncoderInfo);
        if (candidateSet.isEmpty()) {
            Logger.w(TAG, "Can't find valid cropped size");
            return cropRect;
        }
        List<Size> candidatesList = new ArrayList<>(candidateSet);
        Logger.d(TAG, "candidatesList = " + candidatesList);

        // Find the smallest change in dimensions.
        //noinspection ComparatorCombinators - Suggestion by Comparator.comparingInt is for API24+
        Collections.sort(candidatesList,
                (s1, s2) -> (Math.abs(s1.getWidth() - cropRect.width()) + Math.abs(
                        s1.getHeight() - cropRect.height()))
                        - (Math.abs(s2.getWidth() - cropRect.width()) + Math.abs(
                        s2.getHeight() - cropRect.height())));
        Logger.d(TAG, "sorted candidatesList = " + candidatesList);
        Size newSize = candidatesList.get(0);
        int newWidth = newSize.getWidth();
        int newHeight = newSize.getHeight();

        if (newWidth == cropRect.width() && newHeight == cropRect.height()) {
            Logger.d(TAG, "No need to adjust cropRect because crop size is valid.");
            return cropRect;
        }

        // New width/height should be multiple of 2 since VideoCapabilities.get*Alignment()
        // returns power of 2. This ensures width/2 and height/2 are not rounded off.
        // New width/height smaller than resolution ensures calculated cropRect never exceeds
        // the resolution.
        checkState(newWidth % 2 == 0 && newHeight % 2 == 0
                && newWidth <= resolution.getWidth() && newHeight <= resolution.getHeight());
        Rect newCropRect = new Rect(cropRect);
        if (newWidth != cropRect.width()) {
            // Note: When the width/height of cropRect is odd number, Rect.centerX/Y() will be
            // offset to the left/top by 0.5.
            newCropRect.left = Math.max(0, cropRect.centerX() - newWidth / 2);
            newCropRect.right = newCropRect.left + newWidth;
            if (newCropRect.right > resolution.getWidth()) {
                newCropRect.right = resolution.getWidth();
                newCropRect.left = newCropRect.right - newWidth;
            }
        }
        if (newHeight != cropRect.height()) {
            newCropRect.top = Math.max(0, cropRect.centerY() - newHeight / 2);
            newCropRect.bottom = newCropRect.top + newHeight;
            if (newCropRect.bottom > resolution.getHeight()) {
                newCropRect.bottom = resolution.getHeight();
                newCropRect.top = newCropRect.bottom - newHeight;
            }
        }
        Logger.d(TAG, String.format("Adjust cropRect from %s to %s", rectToString(cropRect),
                rectToString(newCropRect)));
        return newCropRect;
    }

    private static void addBySupportedSize(@NonNull Set<Size> candidates, int width, int height,
            @NonNull Size resolution, @NonNull VideoEncoderInfo videoEncoderInfo) {
        if (width > resolution.getWidth() || height > resolution.getHeight()) {
            return;
        }
        try {
            Range<Integer> supportedHeights = videoEncoderInfo.getSupportedHeightsFor(width);
            candidates.add(new Size(width, supportedHeights.clamp(height)));
        } catch (IllegalArgumentException e) {
            Logger.w(TAG, "No supportedHeights for width: " + width, e);
        }
        try {
            Range<Integer> supportedWidths = videoEncoderInfo.getSupportedWidthsFor(height);
            candidates.add(new Size(supportedWidths.clamp(width), height));
        } catch (IllegalArgumentException e) {
            Logger.w(TAG, "No supportedWidths for height: " + height, e);
        }
    }

    private static boolean isCropNeeded(@NonNull Rect cropRect, @NonNull Size resolution) {
        return resolution.getWidth() != cropRect.width()
                || resolution.getHeight() != cropRect.height();
    }

    private static int alignDown(int length, int alignment,
            @NonNull Range<Integer> supportedLength) {
        return align(true, length, alignment, supportedLength);
    }

    private static int alignUp(int length, int alignment,
            @NonNull Range<Integer> supportedRange) {
        return align(false, length, alignment, supportedRange);
    }

    private static int align(boolean alignDown, int length, int alignment,
            @NonNull Range<Integer> supportedRange) {
        int remainder = length % alignment;
        int newLength;
        if (remainder == 0) {
            newLength = length;
        } else if (alignDown) {
            newLength = length - remainder;
        } else {
            newLength = length + (alignment - remainder);
        }
        // Clamp new length by supportedRange, which is supposed to be valid length.
        return supportedRange.clamp(newLength);
    }

    @MainThread
    @Nullable
    private VideoEncoderInfo getVideoEncoderInfo(
            @NonNull Function<VideoEncoderConfig, VideoEncoderInfo> videoEncoderInfoFinder,
            @NonNull VideoCapabilities videoCapabilities,
            @NonNull MediaSpec mediaSpec,
            @NonNull Size resolution,
            @NonNull Range<Integer> targetFps) {
        if (mVideoEncoderInfo != null) {
            return mVideoEncoderInfo;
        }

        // Find the nearest EncoderProfiles
        VideoValidatedEncoderProfilesProxy encoderProfiles =
                videoCapabilities.findHighestSupportedEncoderProfilesFor(resolution);
        VideoEncoderInfo videoEncoderInfo = resolveVideoEncoderInfo(videoEncoderInfoFinder,
                encoderProfiles, mediaSpec, resolution, targetFps);
        if (videoEncoderInfo == null) {
            // If VideoCapture cannot find videoEncoderInfo, it means that VideoOutput should
            // also not be able to find the encoder. VideoCapture will not handle this situation
            // and leave it to VideoOutput to respond.
            Logger.w(TAG, "Can't find videoEncoderInfo");
            return null;
        }

        Size profileSize = encoderProfiles != null ? new Size(
                encoderProfiles.getDefaultVideoProfile().getWidth(),
                encoderProfiles.getDefaultVideoProfile().getHeight()) : null;
        videoEncoderInfo = VideoEncoderInfoWrapper.from(videoEncoderInfo, profileSize);

        // Cache the VideoEncoderInfo as it should be the same when recreating the pipeline.
        // This avoids recreating the MediaCodec instance to get encoder information.
        // Note: We should clear the cache if the MediaSpec changes at any time, especially when
        // the Encoder-related content in the VideoSpec changes. i.e. when we need to observe the
        // MediaSpec Observable.
        return mVideoEncoderInfo = videoEncoderInfo;
    }

    @Nullable
    private static VideoEncoderInfo resolveVideoEncoderInfo(
            @NonNull Function<VideoEncoderConfig, VideoEncoderInfo> videoEncoderInfoFinder,
            @Nullable VideoValidatedEncoderProfilesProxy encoderProfiles,
            @NonNull MediaSpec mediaSpec,
            @NonNull Size resolution,
            @NonNull Range<Integer> targetFps) {
        // Resolve the VideoEncoderConfig
        MimeInfo videoMimeInfo = resolveVideoMimeInfo(mediaSpec, encoderProfiles);
        VideoEncoderConfig videoEncoderConfig = resolveVideoEncoderConfig(
                videoMimeInfo,
                // Timebase won't affect the found EncoderInfo so give a arbitrary one.
                Timebase.UPTIME,
                mediaSpec.getVideoSpec(),
                resolution,
                targetFps);

        return videoEncoderInfoFinder.apply(videoEncoderConfig);
    }

    @MainThread
    private void setupSurfaceUpdateNotifier(@NonNull SessionConfig.Builder sessionConfigBuilder,
            boolean isStreamActive) {
        if (mSurfaceUpdateFuture != null) {
            // A newer update is issued before the previous update is completed. Cancel the
            // previous future.
            if (mSurfaceUpdateFuture.cancel(false)) {
                Logger.d(TAG,
                        "A newer surface update is requested. Previous surface update cancelled.");
            }
        }

        ListenableFuture<Void> surfaceUpdateFuture = mSurfaceUpdateFuture =
                CallbackToFutureAdapter.getFuture(completer -> {
                    // Use the completer as the tag to identify the update.
                    sessionConfigBuilder.addTag(SURFACE_UPDATE_KEY, completer.hashCode());
                    AtomicBoolean surfaceUpdateComplete = new AtomicBoolean(false);
                    CameraCaptureCallback cameraCaptureCallback =
                            new CameraCaptureCallback() {
                                @Override
                                public void onCaptureCompleted(
                                        @NonNull CameraCaptureResult cameraCaptureResult) {
                                    super.onCaptureCompleted(cameraCaptureResult);
                                    if (!surfaceUpdateComplete.get()) {
                                        Object tag = cameraCaptureResult.getTagBundle().getTag(
                                                SURFACE_UPDATE_KEY);
                                        if (tag != null
                                                && (int) tag == completer.hashCode()
                                                && completer.set(null)
                                                && !surfaceUpdateComplete.getAndSet(true)) {
                                            // Remove from builder so this callback doesn't get
                                            // added to future SessionConfigs
                                            CameraXExecutors.mainThreadExecutor().execute(() ->
                                                    sessionConfigBuilder
                                                            .removeCameraCaptureCallback(this));
                                        }
                                    }
                                }
                            };
                    completer.addCancellationListener(() -> {
                        checkState(isMainThread(), "Surface update "
                                + "cancellation should only occur on main thread.");
                        surfaceUpdateComplete.set(true);
                        sessionConfigBuilder.removeCameraCaptureCallback(cameraCaptureCallback);
                    }, CameraXExecutors.directExecutor());
                    sessionConfigBuilder.addRepeatingCameraCaptureCallback(cameraCaptureCallback);

                    return String.format("%s[0x%x]", SURFACE_UPDATE_KEY, completer.hashCode());
                });

        Futures.addCallback(surfaceUpdateFuture, new FutureCallback<Void>() {
            @Override
            public void onSuccess(@Nullable Void result) {
                // If there is a new surface update request, we will wait to update the video
                // output until that update is complete.
                // Also, if the source state is inactive, then we are detached and should not tell
                // the video output we're active.
                if (surfaceUpdateFuture == mSurfaceUpdateFuture
                        && mSourceState != VideoOutput.SourceState.INACTIVE) {
                    setSourceState(isStreamActive ? VideoOutput.SourceState.ACTIVE_STREAMING
                            : VideoOutput.SourceState.ACTIVE_NON_STREAMING);
                }
            }

            @Override
            public void onFailure(@NonNull Throwable t) {
                if (!(t instanceof CancellationException)) {
                    Logger.e(TAG, "Surface update completed with unexpected exception", t);
                }
            }
        }, CameraXExecutors.mainThreadExecutor());
    }

    /**
     * Set {@link ImageOutputConfig#OPTION_SUPPORTED_RESOLUTIONS} according to the resolution found
     * by the {@link QualitySelector} in VideoOutput.
     *
     * @throws IllegalArgumentException if not able to find a resolution by the QualitySelector
     *                                  in VideoOutput.
     */
    private void updateSupportedResolutionsByQuality(@NonNull CameraInfoInternal cameraInfo,
            @NonNull UseCaseConfig.Builder<?, ?, ?> builder) throws IllegalArgumentException {
        MediaSpec mediaSpec = getMediaSpec();

        Preconditions.checkArgument(mediaSpec != null,
                "Unable to update target resolution by null MediaSpec.");

        List<Quality> supportedQualities = QualitySelector.getSupportedQualities(cameraInfo);
        if (supportedQualities.isEmpty()) {
            // When the device does not have any supported quality, even the most flexible
            // QualitySelector such as QualitySelector.from(Quality.HIGHEST), still cannot
            // find any resolution. This should be a rare case but will cause VideoCapture
            // to always fail to bind. The workaround is not set any resolution and leave it to
            // auto resolution mechanism.
            Logger.w(TAG, "Can't find any supported quality on the device.");
            return;
        }

        QualitySelector qualitySelector = mediaSpec.getVideoSpec().getQualitySelector();

        List<Quality> selectedQualities = qualitySelector.getPrioritizedQualities(cameraInfo);
        Logger.d(TAG,
                "Found selectedQualities " + selectedQualities + " by " + qualitySelector);
        if (selectedQualities.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unable to find supported quality by QualitySelector");
        }

        int aspectRatio = mediaSpec.getVideoSpec().getAspectRatio();
        Map<Quality, Size> qualityToSizeMap = QualitySelector.getQualityToResolutionMap(cameraInfo);
        QualityRatioToResolutionsTable qualityRatioTable = new QualityRatioToResolutionsTable(
                cameraInfo.getSupportedResolutions(getImageFormat()), qualityToSizeMap);
        List<Size> supportedResolutions = new ArrayList<>();
        for (Quality selectedQuality : selectedQualities) {
            supportedResolutions.addAll(
                    qualityRatioTable.getResolutions(selectedQuality, aspectRatio));
        }
        Logger.d(TAG, "Set supported resolutions = " + supportedResolutions);

        builder.getMutableConfig().insertOption(OPTION_CUSTOM_ORDERED_RESOLUTIONS,
                supportedResolutions);
    }

    private static boolean hasVideoQualityQuirkAndWorkaroundBySurfaceProcessing() {
        List<VideoQualityQuirk> quirks = DeviceQuirks.getAll(VideoQualityQuirk.class);
        for (VideoQualityQuirk quirk : quirks) {
            if (quirk.workaroundBySurfaceProcessing()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Gets the snapshot value of the given {@link Observable}.
     *
     * <p>Note: Set {@code valueIfMissing} to a non-{@code null} value doesn't mean the method
     * will never return a {@code null} value. The observable could contain exact {@code null}
     * value.
     *
     * @param observable     the observable
     * @param valueIfMissing if the observable doesn't contain value.
     * @param <T>            the value type
     * @return the snapshot value of the given {@link Observable}.
     */
    @Nullable
    private static <T> T fetchObservableValue(@NonNull Observable<T> observable,
            @Nullable T valueIfMissing) {
        ListenableFuture<T> future = observable.fetchData();
        if (!future.isDone()) {
            return valueIfMissing;
        }
        try {
            return future.get();
        } catch (ExecutionException | InterruptedException e) {
            // Should not happened
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("WeakerAccess") // synthetic accessor
    @MainThread
    void setSourceState(@NonNull VideoOutput.SourceState newState) {
        VideoOutput.SourceState oldState = mSourceState;
        if (newState != oldState) {
            mSourceState = newState;
            getOutput().onSourceStateChanged(newState);
        }
    }

    @VisibleForTesting
    @NonNull
    SurfaceRequest getSurfaceRequest() {
        return requireNonNull(mSurfaceRequest);
    }

    /**
     * @inheritDoc
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    @Override
    public Set<Integer> getSupportedEffectTargets() {
        Set<Integer> targets = new HashSet<>();
        targets.add(VIDEO_CAPTURE);
        return targets;
    }

    /**
     * Builder for a {@link VideoCapture}.
     *
     * @param <T> the type of VideoOutput
     * @hide
     */
    @RequiresApi(21) // TODO(b/200306659): Remove and replace with annotation on package-info.java
    @RestrictTo(Scope.LIBRARY_GROUP)
    @SuppressWarnings("ObjectToString")
    public static final class Builder<T extends VideoOutput> implements
            UseCaseConfig.Builder<VideoCapture<T>, VideoCaptureConfig<T>, Builder<T>>,
            ImageOutputConfig.Builder<Builder<T>>, ThreadConfig.Builder<Builder<T>> {
        private final MutableOptionsBundle mMutableConfig;

        /** Creates a new Builder object. */
        Builder(@NonNull T videoOutput) {
            this(createInitialBundle(videoOutput));
        }

        @SuppressWarnings("unchecked")
        private Builder(@NonNull MutableOptionsBundle mutableConfig) {
            mMutableConfig = mutableConfig;

            if (!mMutableConfig.containsOption(OPTION_VIDEO_OUTPUT)) {
                throw new IllegalArgumentException("VideoOutput is required");
            }

            Class<?> oldConfigClass =
                    mutableConfig.retrieveOption(OPTION_TARGET_CLASS, null);
            if (oldConfigClass != null && !oldConfigClass.equals(VideoCapture.class)) {
                throw new IllegalArgumentException(
                        "Invalid target class configuration for "
                                + Builder.this
                                + ": "
                                + oldConfigClass);
            }

            setTargetClass((Class<VideoCapture<T>>) (Type) VideoCapture.class);
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        static Builder<? extends VideoOutput> fromConfig(@NonNull Config configuration) {
            return new Builder<>(MutableOptionsBundle.from(configuration));
        }

        /**
         * Generates a Builder from another Config object
         *
         * @param configuration An immutable configuration to pre-populate this builder.
         * @return The new Builder.
         */
        @NonNull
        public static <T extends VideoOutput> Builder<T> fromConfig(
                @NonNull VideoCaptureConfig<T> configuration) {
            return new Builder<>(MutableOptionsBundle.from(configuration));
        }

        @NonNull
        private static <T extends VideoOutput> MutableOptionsBundle createInitialBundle(
                @NonNull T videoOutput) {
            MutableOptionsBundle bundle = MutableOptionsBundle.create();
            bundle.insertOption(OPTION_VIDEO_OUTPUT, videoOutput);
            return bundle;
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

        /**
         * {@inheritDoc}
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public VideoCaptureConfig<T> getUseCaseConfig() {
            return new VideoCaptureConfig<>(OptionsBundle.from(mMutableConfig));
        }

        @NonNull
        Builder<T> setVideoEncoderInfoFinder(
                @NonNull Function<VideoEncoderConfig, VideoEncoderInfo> videoEncoderInfoFinder) {
            getMutableConfig().insertOption(OPTION_VIDEO_ENCODER_INFO_FINDER,
                    videoEncoderInfoFinder);
            return this;
        }

        /**
         * Builds an immutable {@link VideoCaptureConfig} from the current state.
         *
         * @return A {@link VideoCaptureConfig} populated with the current state.
         */
        @Override
        @NonNull
        public VideoCapture<T> build() {
            return new VideoCapture<>(getUseCaseConfig());
        }

        // Implementations of TargetConfig.Builder default methods

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setTargetClass(@NonNull Class<VideoCapture<T>> targetClass) {
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
        public Builder<T> setTargetName(@NonNull String targetName) {
            getMutableConfig().insertOption(OPTION_TARGET_NAME, targetName);
            return this;
        }

        // Implementations of ImageOutputConfig.Builder default methods

        /**
         * setTargetAspectRatio is not supported on VideoCapture
         *
         * <p>To set aspect ratio, see {@link Recorder.Builder#setAspectRatio(int)}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setTargetAspectRatio(@AspectRatio.Ratio int aspectRatio) {
            throw new UnsupportedOperationException("setTargetAspectRatio is not supported.");
        }

        /**
         * Sets the rotation of the intended target for images from this configuration.
         *
         * <p>This is one of four valid values: {@link Surface#ROTATION_0}, {@link
         * Surface#ROTATION_90}, {@link Surface#ROTATION_180}, {@link Surface#ROTATION_270}.
         * Rotation values are relative to the "natural" rotation, {@link Surface#ROTATION_0}.
         *
         * <p>If not set, the target rotation will default to the value of
         * {@link Display#getRotation()} of the default display at the time the use case is
         * created. The use case is fully created once it has been attached to a camera.
         *
         * @param rotation The rotation of the intended target.
         * @return The current Builder.
         */
        @NonNull
        @Override
        public Builder<T> setTargetRotation(@RotationValue int rotation) {
            getMutableConfig().insertOption(OPTION_TARGET_ROTATION, rotation);
            return this;
        }

        /**
         * setTargetResolution is not supported on VideoCapture
         *
         * <p>To set resolution, see {@link Recorder.Builder#setQualitySelector(QualitySelector)}.
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setTargetResolution(@NonNull Size resolution) {
            throw new UnsupportedOperationException("setTargetResolution is not supported.");
        }

        /**
         * Sets the default resolution of the intended target from this configuration.
         *
         * @param resolution The default resolution to choose from supported output sizes list.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setDefaultResolution(@NonNull Size resolution) {
            getMutableConfig().insertOption(OPTION_DEFAULT_RESOLUTION, resolution);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setMaxResolution(@NonNull Size resolution) {
            getMutableConfig().insertOption(OPTION_MAX_RESOLUTION, resolution);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setSupportedResolutions(
                @NonNull List<Pair<Integer, Size[]>> resolutions) {
            getMutableConfig().insertOption(OPTION_SUPPORTED_RESOLUTIONS, resolutions);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setCustomOrderedResolutions(@NonNull List<Size> resolutions) {
            getMutableConfig().insertOption(OPTION_CUSTOM_ORDERED_RESOLUTIONS, resolutions);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setResolutionSelector(@NonNull ResolutionSelector resolutionSelector) {
            getMutableConfig().insertOption(OPTION_RESOLUTION_SELECTOR, resolutionSelector);
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
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setBackgroundExecutor(@NonNull Executor executor) {
            getMutableConfig().insertOption(OPTION_BACKGROUND_EXECUTOR, executor);
            return this;
        }

        // Implementations of UseCaseConfig.Builder default methods

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setDefaultSessionConfig(@NonNull SessionConfig sessionConfig) {
            getMutableConfig().insertOption(OPTION_DEFAULT_SESSION_CONFIG, sessionConfig);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setDefaultCaptureConfig(@NonNull CaptureConfig captureConfig) {
            getMutableConfig().insertOption(OPTION_DEFAULT_CAPTURE_CONFIG, captureConfig);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setSessionOptionUnpacker(
                @NonNull SessionConfig.OptionUnpacker optionUnpacker) {
            getMutableConfig().insertOption(OPTION_SESSION_CONFIG_UNPACKER, optionUnpacker);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setCaptureOptionUnpacker(
                @NonNull CaptureConfig.OptionUnpacker optionUnpacker) {
            getMutableConfig().insertOption(OPTION_CAPTURE_CONFIG_UNPACKER, optionUnpacker);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setSurfaceOccupancyPriority(int priority) {
            getMutableConfig().insertOption(OPTION_SURFACE_OCCUPANCY_PRIORITY, priority);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setCameraSelector(@NonNull CameraSelector cameraSelector) {
            getMutableConfig().insertOption(OPTION_CAMERA_SELECTOR, cameraSelector);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder<T> setUseCaseEventCallback(
                @NonNull EventCallback useCaseEventCallback) {
            getMutableConfig().insertOption(OPTION_USE_CASE_EVENT_CALLBACK, useCaseEventCallback);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setZslDisabled(boolean disabled) {
            getMutableConfig().insertOption(OPTION_ZSL_DISABLED, disabled);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder<T> setHighResolutionDisabled(boolean disabled) {
            getMutableConfig().insertOption(OPTION_HIGH_RESOLUTION_DISABLED, disabled);
            return this;
        }
    }
}

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

import static androidx.camera.core.impl.ImageOutputConfig.OPTION_DEFAULT_RESOLUTION;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_MAX_RESOLUTION;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_SUPPORTED_RESOLUTIONS;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_TARGET_ASPECT_RATIO;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_TARGET_RESOLUTION;
import static androidx.camera.core.impl.ImageOutputConfig.OPTION_TARGET_ROTATION;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_CAMERA_SELECTOR;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_CAPTURE_CONFIG_UNPACKER;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_DEFAULT_CAPTURE_CONFIG;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_DEFAULT_SESSION_CONFIG;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_SESSION_CONFIG_UNPACKER;
import static androidx.camera.core.impl.UseCaseConfig.OPTION_SURFACE_OCCUPANCY_PRIORITY;
import static androidx.camera.core.impl.VideoCaptureConfig.OPTION_AUDIO_BIT_RATE;
import static androidx.camera.core.impl.VideoCaptureConfig.OPTION_AUDIO_CHANNEL_COUNT;
import static androidx.camera.core.impl.VideoCaptureConfig.OPTION_AUDIO_MIN_BUFFER_SIZE;
import static androidx.camera.core.impl.VideoCaptureConfig.OPTION_AUDIO_RECORD_SOURCE;
import static androidx.camera.core.impl.VideoCaptureConfig.OPTION_AUDIO_SAMPLE_RATE;
import static androidx.camera.core.impl.VideoCaptureConfig.OPTION_BIT_RATE;
import static androidx.camera.core.impl.VideoCaptureConfig.OPTION_INTRA_FRAME_INTERVAL;
import static androidx.camera.core.impl.VideoCaptureConfig.OPTION_VIDEO_FRAME_RATE;
import static androidx.camera.core.internal.TargetConfig.OPTION_TARGET_CLASS;
import static androidx.camera.core.internal.TargetConfig.OPTION_TARGET_NAME;
import static androidx.camera.core.internal.ThreadConfig.OPTION_BACKGROUND_EXECUTOR;
import static androidx.camera.core.internal.UseCaseEventConfig.OPTION_USE_CASE_EVENT_CALLBACK;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.location.Location;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.CamcorderProfile;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder.AudioSource;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Pair;
import android.util.Size;
import android.view.Display;
import android.view.Surface;

import androidx.annotation.GuardedBy;
import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.annotation.UiThread;
import androidx.camera.core.impl.CameraInternal;
import androidx.camera.core.impl.CaptureConfig;
import androidx.camera.core.impl.ConfigProvider;
import androidx.camera.core.impl.DeferrableSurface;
import androidx.camera.core.impl.ImageOutputConfig;
import androidx.camera.core.impl.ImageOutputConfig.RotationValue;
import androidx.camera.core.impl.ImmediateSurface;
import androidx.camera.core.impl.MutableConfig;
import androidx.camera.core.impl.MutableOptionsBundle;
import androidx.camera.core.impl.OptionsBundle;
import androidx.camera.core.impl.SessionConfig;
import androidx.camera.core.impl.UseCaseConfig;
import androidx.camera.core.impl.VideoCaptureConfig;
import androidx.camera.core.impl.utils.executor.CameraXExecutors;
import androidx.camera.core.internal.ThreadConfig;
import androidx.camera.core.internal.utils.VideoUtil;
import androidx.core.util.Preconditions;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A use case for taking a video.
 *
 * <p>This class is designed for simple video capturing. It gives basic configuration of the
 * recorded video such as resolution and file format.
 *
 * @hide
 */
@RestrictTo(Scope.LIBRARY_GROUP)
public final class VideoCapture extends UseCase {

    ////////////////////////////////////////////////////////////////////////////////////////////
    // [UseCase lifetime constant] - Stays constant for the lifetime of the UseCase. Which means
    // they could be created in the constructor.
    ////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * An unknown error occurred.
     *
     * <p>See message parameter in onError callback or log for more details.
     */
    public static final int ERROR_UNKNOWN = 0;
    /**
     * An error occurred with encoder state, either when trying to change state or when an
     * unexpected state change occurred.
     */
    public static final int ERROR_ENCODER = 1;
    /** An error with muxer state such as during creation or when stopping. */
    public static final int ERROR_MUXER = 2;
    /**
     * An error indicating start recording was called when video recording is still in progress.
     */
    public static final int ERROR_RECORDING_IN_PROGRESS = 3;
    /**
     * An error indicating the file saving operations.
     */
    public static final int ERROR_FILE_IO = 4;

    /**
     * Provides a static configuration with implementation-agnostic options.
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final Defaults DEFAULT_CONFIG = new Defaults();
    private static final String TAG = "VideoCapture";
    /** Amount of time to wait for dequeuing a buffer from the videoEncoder. */
    private static final int DEQUE_TIMEOUT_USEC = 10000;
    /** Android preferred mime type for AVC video. */
    private static final String VIDEO_MIME_TYPE = "video/avc";
    private static final String AUDIO_MIME_TYPE = "audio/mp4a-latm";
    /** Camcorder profiles quality list */
    private static final int[] CamcorderQuality = {
            CamcorderProfile.QUALITY_2160P,
            CamcorderProfile.QUALITY_1080P,
            CamcorderProfile.QUALITY_720P,
            CamcorderProfile.QUALITY_480P
    };
    /**
     * Audio encoding
     *
     * <p>the result of PCM_8BIT and PCM_FLOAT are not good. Set PCM_16BIT as the first option.
     */
    private static final short[] sAudioEncoding = {
            AudioFormat.ENCODING_PCM_16BIT,
            AudioFormat.ENCODING_PCM_8BIT,
            AudioFormat.ENCODING_PCM_FLOAT
    };

    private final BufferInfo mVideoBufferInfo = new BufferInfo();
    private final Object mMuxerLock = new Object();
    private final AtomicBoolean mEndOfVideoStreamSignal = new AtomicBoolean(true);
    private final AtomicBoolean mEndOfAudioStreamSignal = new AtomicBoolean(true);
    private final AtomicBoolean mEndOfAudioVideoSignal = new AtomicBoolean(true);
    private final BufferInfo mAudioBufferInfo = new BufferInfo();
    /** For record the first sample written time. */
    private final AtomicBoolean mIsFirstVideoSampleWrite = new AtomicBoolean(false);
    private final AtomicBoolean mIsFirstAudioSampleWrite = new AtomicBoolean(false);

    ////////////////////////////////////////////////////////////////////////////////////////////
    // [UseCase attached constant] - Is only valid when the UseCase is attached to a camera.
    ////////////////////////////////////////////////////////////////////////////////////////////

    /** Thread on which all encoding occurs. */
    private HandlerThread mVideoHandlerThread;
    private Handler mVideoHandler;
    /** Thread on which audio encoding occurs. */
    private HandlerThread mAudioHandlerThread;
    private Handler mAudioHandler;

    @NonNull
    MediaCodec mVideoEncoder;
    @NonNull
    private MediaCodec mAudioEncoder;

    ////////////////////////////////////////////////////////////////////////////////////////////
    // [UseCase attached dynamic] - Can change but is only available when the UseCase is attached.
    ////////////////////////////////////////////////////////////////////////////////////////////

    /** The muxer that writes the encoding data to file. */
    @GuardedBy("mMuxerLock")
    private MediaMuxer mMuxer;
    private boolean mMuxerStarted = false;
    /** The index of the video track used by the muxer. */
    private int mVideoTrackIndex;
    /** The index of the audio track used by the muxer. */
    private int mAudioTrackIndex;
    /** Surface the camera writes to, which the videoEncoder uses as input. */
    Surface mCameraSurface;

    /** audio raw data */
    @NonNull
    private AudioRecord mAudioRecorder;
    private int mAudioBufferSize;
    private boolean mIsRecording = false;
    private int mAudioChannelCount;
    private int mAudioSampleRate;
    private int mAudioBitRate;
    private DeferrableSurface mDeferrableSurface;
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    Uri mSavedVideoUri;
    private ParcelFileDescriptor mParcelFileDescriptor;

    /**
     * Creates a new video capture use case from the given configuration.
     *
     * @param config for this use case instance
     */
    VideoCapture(@NonNull VideoCaptureConfig config) {
        super(config);
    }

    /** Creates a {@link MediaFormat} using parameters from the configuration */
    private static MediaFormat createMediaFormat(VideoCaptureConfig config, Size resolution) {
        MediaFormat format =
                MediaFormat.createVideoFormat(
                        VIDEO_MIME_TYPE, resolution.getWidth(), resolution.getHeight());
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, config.getBitRate());
        format.setInteger(MediaFormat.KEY_FRAME_RATE, config.getVideoFrameRate());
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, config.getIFrameInterval());

        return format;
    }


    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @Nullable
    @RestrictTo(Scope.LIBRARY_GROUP)
    public UseCaseConfig.Builder<?, ?, ?> getDefaultBuilder(@Nullable CameraInfo cameraInfo) {
        VideoCaptureConfig defaults = CameraX.getDefaultUseCaseConfig(VideoCaptureConfig.class,
                cameraInfo);
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
    @SuppressWarnings("WrongConstant")
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    public void onAttached() {
        mVideoHandlerThread = new HandlerThread(CameraXThreads.TAG + "video encoding thread");
        mAudioHandlerThread = new HandlerThread(CameraXThreads.TAG + "audio encoding thread");

        // video thread start
        mVideoHandlerThread.start();
        mVideoHandler = new Handler(mVideoHandlerThread.getLooper());

        // audio thread start
        mAudioHandlerThread.start();
        mAudioHandler = new Handler(mAudioHandlerThread.getLooper());
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    protected Size onSuggestedResolutionUpdated(@NonNull Size suggestedResolution) {
        if (mCameraSurface != null) {
            mVideoEncoder.stop();
            mVideoEncoder.release();
            mAudioEncoder.stop();
            mAudioEncoder.release();
            releaseCameraSurface(false);
        }

        try {
            mVideoEncoder = MediaCodec.createEncoderByType(VIDEO_MIME_TYPE);
            mAudioEncoder = MediaCodec.createEncoderByType(AUDIO_MIME_TYPE);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create MediaCodec due to: " + e.getCause());
        }

        setupEncoder(getCameraId(), suggestedResolution);
        return suggestedResolution;
    }

    /**
     * Starts recording video, which continues until {@link VideoCapture#stopRecording()} is
     * called.
     *
     * <p>StartRecording() is asynchronous. User needs to check if any error occurs by setting the
     * {@link OnVideoSavedCallback#onError(int, String, Throwable)}.
     *
     * @param outputFileOptions Location to save the video capture
     * @param executor          The executor in which the callback methods will be run.
     * @param callback          Callback for when the recorded video saving completion or failure.
     */
    public void startRecording(
            @NonNull OutputFileOptions outputFileOptions, @NonNull Executor executor,
            @NonNull OnVideoSavedCallback callback) {
        Logger.i(TAG, "startRecording");
        mIsFirstVideoSampleWrite.set(false);
        mIsFirstAudioSampleWrite.set(false);

        OnVideoSavedCallback postListener = new VideoSavedListenerWrapper(executor, callback);
        Metadata metadata = outputFileOptions.getMetadata();

        if (!mEndOfAudioVideoSignal.get()) {
            postListener.onError(
                    ERROR_RECORDING_IN_PROGRESS, "It is still in video recording!",
                    null);
            return;
        }

        try {
            // audioRecord start
            mAudioRecorder.startRecording();
        } catch (IllegalStateException e) {
            postListener.onError(ERROR_ENCODER, "AudioRecorder start fail", e);
            return;
        }

        CameraInternal attachedCamera = getCamera();
        String cameraId = getCameraId();
        Size resolution = getAttachedSurfaceResolution();
        try {
            // video encoder start
            Logger.i(TAG, "videoEncoder start");
            mVideoEncoder.start();
            // audio encoder start
            Logger.i(TAG, "audioEncoder start");
            mAudioEncoder.start();

        } catch (IllegalStateException e) {
            setupEncoder(cameraId, resolution);
            postListener.onError(ERROR_ENCODER, "Audio/Video encoder start fail", e);
            return;
        }

        try {
            synchronized (mMuxerLock) {
                mMuxer = initMediaMuxer(outputFileOptions, postListener);
                Preconditions.checkNotNull(mMuxer);
                mMuxer.setOrientationHint(getRelativeRotation(attachedCamera));

                if (metadata != null && metadata.location != null) {
                    mMuxer.setLocation(
                            (float) metadata.location.getLatitude(),
                            (float) metadata.location.getLongitude());
                }
            }
        } catch (IOException e) {
            setupEncoder(cameraId, resolution);
            postListener.onError(ERROR_MUXER, "MediaMuxer creation failed!", e);
            return;
        }

        mEndOfVideoStreamSignal.set(false);
        mEndOfAudioStreamSignal.set(false);
        mEndOfAudioVideoSignal.set(false);
        mIsRecording = true;

        notifyActive();
        mAudioHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        VideoCapture.this.audioEncode(postListener);
                    }
                });

        mVideoHandler.post(
                new Runnable() {
                    @Override
                    public void run() {
                        boolean errorOccurred = VideoCapture.this.videoEncode(postListener,
                                cameraId, resolution);
                        if (!errorOccurred) {
                            postListener.onVideoSaved(new OutputFileResults(mSavedVideoUri));
                        }
                    }
                });
    }

    /**
     * Stops recording video, this must be called after {@link
     * VideoCapture#startRecording(OutputFileOptions, Executor, OnVideoSavedCallback)} is
     * called.
     *
     * <p>stopRecording() is asynchronous API. User need to check if {@link
     * OnVideoSavedCallback#onVideoSaved(OutputFileResults)} or
     * {@link OnVideoSavedCallback#onError(int, String, Throwable)} be called
     * before startRecording.
     */
    public void stopRecording() {
        Logger.i(TAG, "stopRecording");
        notifyInactive();
        if (!mEndOfAudioVideoSignal.get() && mIsRecording) {
            // stop audio encoder thread, and wait video encoder and muxer stop.
            mEndOfAudioStreamSignal.set(true);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public void onDetached() {
        mVideoHandlerThread.quitSafely();

        // audio encoder release
        mAudioHandlerThread.quitSafely();
        if (mAudioEncoder != null) {
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

        if (mAudioRecorder != null) {
            mAudioRecorder.release();
            mAudioRecorder = null;
        }

        if (mCameraSurface != null) {
            releaseCameraSurface(true);
        }
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @NonNull
    @RestrictTo(Scope.LIBRARY_GROUP)
    @Override
    public UseCaseConfig.Builder<?, ?, ?> getUseCaseConfigBuilder() {
        return Builder.fromConfig((VideoCaptureConfig) getUseCaseConfig());
    }

    /**
     * {@inheritDoc}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @UiThread
    @Override
    public void onStateDetached() {
        stopRecording();
    }

    private void releaseCameraSurface(final boolean releaseVideoEncoder) {
        if (mDeferrableSurface == null) {
            return;
        }

        final MediaCodec videoEncoder = mVideoEncoder;

        // Calling close should allow termination future to complete and close the surface with
        // the listener that was added after constructing the DeferrableSurface.
        mDeferrableSurface.close();
        mDeferrableSurface.getTerminationFuture().addListener(
                () -> {
                    if (releaseVideoEncoder && videoEncoder != null) {
                        videoEncoder.release();
                    }
                }, CameraXExecutors.mainThreadExecutor());

        if (releaseVideoEncoder) {
            mVideoEncoder = null;
        }
        mCameraSurface = null;
        mDeferrableSurface = null;
    }


    /**
     * Sets the desired rotation of the output video.
     *
     * <p>In most cases this should be set to the current rotation returned by {@link
     * Display#getRotation()}.
     *
     * @param rotation Desired rotation of the output video.
     */
    public void setTargetRotation(@RotationValue int rotation) {
        setTargetRotationInternal(rotation);
    }

    /**
     * Setup the {@link MediaCodec} for encoding video from a camera {@link Surface} and encoding
     * audio from selected audio source.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic accessor */
    void setupEncoder(@NonNull String cameraId, @NonNull Size resolution) {
        VideoCaptureConfig config = (VideoCaptureConfig) getUseCaseConfig();

        // video encoder setup
        mVideoEncoder.reset();
        mVideoEncoder.configure(
                createMediaFormat(config, resolution), /*surface*/
                null, /*crypto*/
                null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        if (mCameraSurface != null) {
            releaseCameraSurface(false);
        }
        Surface cameraSurface = mVideoEncoder.createInputSurface();
        mCameraSurface = cameraSurface;

        SessionConfig.Builder sessionConfigBuilder = SessionConfig.Builder.createFrom(config);

        if (mDeferrableSurface != null) {
            mDeferrableSurface.close();
        }
        mDeferrableSurface = new ImmediateSurface(mCameraSurface);
        mDeferrableSurface.getTerminationFuture().addListener(
                cameraSurface::release, CameraXExecutors.mainThreadExecutor()
        );

        sessionConfigBuilder.addSurface(mDeferrableSurface);

        sessionConfigBuilder.addErrorListener(new SessionConfig.ErrorListener() {
            @Override
            public void onError(@NonNull SessionConfig sessionConfig,
                    @NonNull SessionConfig.SessionError error) {
                // Ensure the attached camera has not changed before calling setupEncoder.
                // TODO(b/143915543): Ensure this never gets called by a camera that is not attached
                //  to this use case so we don't need to do this check.
                if (isCurrentCamera(cameraId)) {
                    // Only reset the pipeline when the bound camera is the same.
                    setupEncoder(cameraId, resolution);
                }
            }
        });

        updateSessionConfig(sessionConfigBuilder.build());

        // audio encoder setup
        setAudioParametersByCamcorderProfile(resolution, cameraId);
        mAudioEncoder.reset();
        mAudioEncoder.configure(
                createAudioMediaFormat(), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);

        if (mAudioRecorder != null) {
            mAudioRecorder.release();
        }
        mAudioRecorder = autoConfigAudioRecordSource(config);
        // check mAudioRecorder
        if (mAudioRecorder == null) {
            Logger.e(TAG, "AudioRecord object cannot initialized correctly!");
        }

        mVideoTrackIndex = -1;
        mAudioTrackIndex = -1;
        mIsRecording = false;
    }

    /**
     * Write a buffer that has been encoded to file.
     *
     * @param bufferIndex the index of the buffer in the videoEncoder that has available data
     * @return returns true if this buffer is the end of the stream
     */
    private boolean writeVideoEncodedBuffer(int bufferIndex) {
        if (bufferIndex < 0) {
            Logger.e(TAG, "Output buffer should not have negative index: " + bufferIndex);
            return false;
        }
        // Get data from buffer
        ByteBuffer outputBuffer = mVideoEncoder.getOutputBuffer(bufferIndex);

        // Check if buffer is valid, if not then return
        if (outputBuffer == null) {
            Logger.d(TAG, "OutputBuffer was null.");
            return false;
        }

        // Write data to mMuxer if available
        if (mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0 && mVideoBufferInfo.size > 0) {
            outputBuffer.position(mVideoBufferInfo.offset);
            outputBuffer.limit(mVideoBufferInfo.offset + mVideoBufferInfo.size);
            mVideoBufferInfo.presentationTimeUs = (System.nanoTime() / 1000);

            synchronized (mMuxerLock) {
                if (!mIsFirstVideoSampleWrite.get()) {
                    Logger.i(TAG, "First video sample written.");
                    mIsFirstVideoSampleWrite.set(true);
                }
                mMuxer.writeSampleData(mVideoTrackIndex, outputBuffer, mVideoBufferInfo);
            }
        }

        // Release data
        mVideoEncoder.releaseOutputBuffer(bufferIndex, false);

        // Return true if EOS is set
        return (mVideoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }

    private boolean writeAudioEncodedBuffer(int bufferIndex) {
        ByteBuffer buffer = getOutputBuffer(mAudioEncoder, bufferIndex);
        buffer.position(mAudioBufferInfo.offset);
        if (mAudioTrackIndex >= 0
                && mVideoTrackIndex >= 0
                && mAudioBufferInfo.size > 0
                && mAudioBufferInfo.presentationTimeUs > 0) {
            try {
                synchronized (mMuxerLock) {
                    if (!mIsFirstAudioSampleWrite.get()) {
                        Logger.i(TAG, "First audio sample written.");
                        mIsFirstAudioSampleWrite.set(true);
                    }
                    mMuxer.writeSampleData(mAudioTrackIndex, buffer, mAudioBufferInfo);
                }
            } catch (Exception e) {
                Logger.e(
                        TAG,
                        "audio error:size="
                                + mAudioBufferInfo.size
                                + "/offset="
                                + mAudioBufferInfo.offset
                                + "/timeUs="
                                + mAudioBufferInfo.presentationTimeUs);
                e.printStackTrace();
            }
        }
        mAudioEncoder.releaseOutputBuffer(bufferIndex, false);
        return (mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
    }

    /**
     * Encoding which runs indefinitely until end of stream is signaled. This should not run on the
     * main thread otherwise it will cause the application to block.
     *
     * @return returns {@code true} if an error condition occurred, otherwise returns {@code false}
     */
    boolean videoEncode(@NonNull OnVideoSavedCallback videoSavedCallback, @NonNull String cameraId,
            @NonNull Size resolution) {
        // Main encoding loop. Exits on end of stream.
        boolean errorOccurred = false;
        boolean videoEos = false;
        while (!videoEos && !errorOccurred) {
            // Check for end of stream from main thread
            if (mEndOfVideoStreamSignal.get()) {
                mVideoEncoder.signalEndOfInputStream();
                mEndOfVideoStreamSignal.set(false);
            }

            // Deque buffer to check for processing step
            int outputBufferId =
                    mVideoEncoder.dequeueOutputBuffer(mVideoBufferInfo, DEQUE_TIMEOUT_USEC);
            switch (outputBufferId) {
                case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                    if (mMuxerStarted) {
                        videoSavedCallback.onError(
                                ERROR_ENCODER,
                                "Unexpected change in video encoding format.",
                                null);
                        errorOccurred = true;
                    }

                    synchronized (mMuxerLock) {
                        mVideoTrackIndex = mMuxer.addTrack(mVideoEncoder.getOutputFormat());
                        if (mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0) {
                            mMuxerStarted = true;
                            Logger.i(TAG, "media mMuxer start");
                            mMuxer.start();
                        }
                    }
                    break;
                case MediaCodec.INFO_TRY_AGAIN_LATER:
                    // Timed out. Just wait until next attempt to deque.
                default:
                    videoEos = writeVideoEncodedBuffer(outputBufferId);
            }
        }

        try {
            Logger.i(TAG, "videoEncoder stop");
            mVideoEncoder.stop();
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(ERROR_ENCODER,
                    "Video encoder stop failed!", e);
            errorOccurred = true;
        }

        try {
            // new MediaMuxer instance required for each new file written, and release current one.
            synchronized (mMuxerLock) {
                if (mMuxer != null) {
                    if (mMuxerStarted) {
                        mMuxer.stop();
                    }
                    mMuxer.release();
                    mMuxer = null;
                }
            }
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(ERROR_MUXER, "Muxer stop failed!", e);
            errorOccurred = true;
        }

        if (mParcelFileDescriptor != null) {
            try {
                mParcelFileDescriptor.close();
                mParcelFileDescriptor = null;
            } catch (IOException e) {
                videoSavedCallback.onError(ERROR_MUXER, "File descriptor close failed!", e);
                errorOccurred = true;
            }
        }

        mMuxerStarted = false;
        // Do the setup of the videoEncoder at the end of video recording instead of at the start of
        // recording because it requires attaching a new Surface. This causes a glitch so we don't
        // want that to incur latency at the start of capture.
        setupEncoder(cameraId, resolution);
        notifyReset();

        // notify the UI thread that the video recording has finished
        mEndOfAudioVideoSignal.set(true);

        Logger.i(TAG, "Video encode thread end.");
        return errorOccurred;
    }

    boolean audioEncode(OnVideoSavedCallback videoSavedCallback) {
        // Audio encoding loop. Exits on end of stream.
        boolean audioEos = false;
        int outIndex;
        while (!audioEos && mIsRecording) {
            // Check for end of stream from main thread
            if (mEndOfAudioStreamSignal.get()) {
                mEndOfAudioStreamSignal.set(false);
                mIsRecording = false;
            }

            // get audio deque input buffer
            if (mAudioEncoder != null && mAudioRecorder != null) {
                int index = mAudioEncoder.dequeueInputBuffer(-1);
                if (index >= 0) {
                    final ByteBuffer buffer = getInputBuffer(mAudioEncoder, index);
                    buffer.clear();
                    int length = mAudioRecorder.read(buffer, mAudioBufferSize);
                    if (length > 0) {
                        mAudioEncoder.queueInputBuffer(
                                index,
                                0,
                                length,
                                (System.nanoTime() / 1000),
                                mIsRecording ? 0 : MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    }
                }

                // start to dequeue audio output buffer
                do {
                    outIndex = mAudioEncoder.dequeueOutputBuffer(mAudioBufferInfo, 0);
                    switch (outIndex) {
                        case MediaCodec.INFO_OUTPUT_FORMAT_CHANGED:
                            synchronized (mMuxerLock) {
                                mAudioTrackIndex = mMuxer.addTrack(mAudioEncoder.getOutputFormat());
                                if (mAudioTrackIndex >= 0 && mVideoTrackIndex >= 0) {
                                    mMuxerStarted = true;
                                    mMuxer.start();
                                }
                            }
                            break;
                        case MediaCodec.INFO_TRY_AGAIN_LATER:
                            break;
                        default:
                            audioEos = writeAudioEncodedBuffer(outIndex);
                    }
                } while (outIndex >= 0 && !audioEos); // end of dequeue output buffer
            }
        } // end of while loop

        // Audio Stop
        try {
            Logger.i(TAG, "audioRecorder stop");
            mAudioRecorder.stop();
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(
                    ERROR_ENCODER, "Audio recorder stop failed!", e);
        }

        try {
            mAudioEncoder.stop();
        } catch (IllegalStateException e) {
            videoSavedCallback.onError(ERROR_ENCODER,
                    "Audio encoder stop failed!", e);
        }

        Logger.i(TAG, "Audio encode thread end");
        // Use AtomicBoolean to signal because MediaCodec.signalEndOfInputStream() is not thread
        // safe
        mEndOfVideoStreamSignal.set(true);

        return false;
    }

    private ByteBuffer getInputBuffer(MediaCodec codec, int index) {
        return codec.getInputBuffer(index);
    }

    private ByteBuffer getOutputBuffer(MediaCodec codec, int index) {
        return codec.getOutputBuffer(index);
    }

    /** Creates a {@link MediaFormat} using parameters for audio from the configuration */
    private MediaFormat createAudioMediaFormat() {
        MediaFormat format =
                MediaFormat.createAudioFormat(AUDIO_MIME_TYPE, mAudioSampleRate,
                        mAudioChannelCount);
        format.setInteger(
                MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        format.setInteger(MediaFormat.KEY_BIT_RATE, mAudioBitRate);

        return format;
    }

    /** Create a AudioRecord object to get raw data */
    private AudioRecord autoConfigAudioRecordSource(VideoCaptureConfig config) {
        for (short audioFormat : sAudioEncoding) {

            // Use channel count to determine stereo vs mono
            int channelConfig =
                    mAudioChannelCount == 1
                            ? AudioFormat.CHANNEL_IN_MONO
                            : AudioFormat.CHANNEL_IN_STEREO;
            int source = config.getAudioRecordSource();

            try {
                int bufferSize =
                        AudioRecord.getMinBufferSize(mAudioSampleRate, channelConfig, audioFormat);

                if (bufferSize <= 0) {
                    bufferSize = config.getAudioMinBufferSize();
                }

                AudioRecord recorder =
                        new AudioRecord(
                                source,
                                mAudioSampleRate,
                                channelConfig,
                                audioFormat,
                                bufferSize * 2);

                if (recorder.getState() == AudioRecord.STATE_INITIALIZED) {
                    mAudioBufferSize = bufferSize;
                    Logger.i(
                            TAG,
                            "source: "
                                    + source
                                    + " audioSampleRate: "
                                    + mAudioSampleRate
                                    + " channelConfig: "
                                    + channelConfig
                                    + " audioFormat: "
                                    + audioFormat
                                    + " bufferSize: "
                                    + bufferSize);
                    return recorder;
                }
            } catch (Exception e) {
                Logger.e(TAG, "Exception, keep trying.", e);
            }
        }

        return null;
    }

    /** Set audio record parameters by CamcorderProfile */
    private void setAudioParametersByCamcorderProfile(Size currentResolution, String cameraId) {
        CamcorderProfile profile;
        boolean isCamcorderProfileFound = false;

        for (int quality : CamcorderQuality) {
            if (CamcorderProfile.hasProfile(Integer.parseInt(cameraId), quality)) {
                profile = CamcorderProfile.get(Integer.parseInt(cameraId), quality);
                if (currentResolution.getWidth() == profile.videoFrameWidth
                        && currentResolution.getHeight() == profile.videoFrameHeight) {
                    mAudioChannelCount = profile.audioChannels;
                    mAudioSampleRate = profile.audioSampleRate;
                    mAudioBitRate = profile.audioBitRate;
                    isCamcorderProfileFound = true;
                    break;
                }
            }
        }

        // In case no corresponding camcorder profile can be founded, * get default value from
        // VideoCaptureConfig.
        if (!isCamcorderProfileFound) {
            VideoCaptureConfig config = (VideoCaptureConfig) getUseCaseConfig();
            mAudioChannelCount = config.getAudioChannelCount();
            mAudioSampleRate = config.getAudioSampleRate();
            mAudioBitRate = config.getAudioBitRate();
        }
    }

    @NonNull
    private MediaMuxer initMediaMuxer(@NonNull OutputFileOptions outputFileOptions,
            OnVideoSavedCallback postListener) throws IOException {
        MediaMuxer mediaMuxer;
        File savedVideoFile;

        if (outputFileOptions.isSavingToFile()) {
            savedVideoFile = outputFileOptions.getFile();
            mSavedVideoUri = Uri.fromFile(outputFileOptions.getFile());

            mediaMuxer = new MediaMuxer(savedVideoFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } else if (outputFileOptions.isSavingToMediaStore()) {
            ContentValues values = outputFileOptions.getContentValues() != null
                    ? new ContentValues(outputFileOptions.getContentValues())
                    : new ContentValues();

            mSavedVideoUri = outputFileOptions.getContentResolver().insert(
                    outputFileOptions.getSaveCollection(), values);

            if (mSavedVideoUri == null) {
                postListener.onError(
                        ERROR_FILE_IO, "Invalid Uri!",
                        null);
                return null;
            }

            // Sine API 26, media muxer could be initiated by a FileDescriptor.
            try {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                    String savedLocationPath = VideoUtil.getAbsolutePathFromUri(
                            outputFileOptions.getContentResolver(), mSavedVideoUri);

                    Logger.i(TAG, "Saved Location Path: " + savedLocationPath);
                    mediaMuxer = new MediaMuxer(savedLocationPath,
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                } else {
                    mParcelFileDescriptor =
                            outputFileOptions.getContentResolver().openFileDescriptor(
                                    mSavedVideoUri, "rw");
                    mediaMuxer = new MediaMuxer(mParcelFileDescriptor.getFileDescriptor(),
                            MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
                }
            } catch (IOException e) {
                postListener.onError(
                        ERROR_FILE_IO, "Open file descriptor failed!",
                        null);
                return null;
            }
        } else {
            throw new IllegalArgumentException(
                    "The OutputFileOptions should assign before recording");
        }

        return mediaMuxer;
    }

    /**
     * Describes the error that occurred during video capture operations.
     *
     * <p>This is a parameter sent to the error callback functions set in listeners such as {@link
     * VideoCapture.OnVideoSavedCallback#onError(int, String, Throwable)}.
     *
     * <p>See message parameter in onError callback or log for more details.
     *
     * @hide
     */
    @IntDef({ERROR_UNKNOWN, ERROR_ENCODER, ERROR_MUXER, ERROR_RECORDING_IN_PROGRESS,
            ERROR_FILE_IO})
    @Retention(RetentionPolicy.SOURCE)
    @RestrictTo(Scope.LIBRARY_GROUP)
    public @interface VideoCaptureError {
    }

    /** Listener containing callbacks for video file I/O events. */
    public interface OnVideoSavedCallback {
        /** Called when the video has been successfully saved. */
        void onVideoSaved(@NonNull OutputFileResults outputFileResults);

        /** Called when an error occurs while attempting to save the video. */
        void onError(@VideoCaptureError int videoCaptureError, @NonNull String message,
                @Nullable Throwable cause);
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
    public static final class Defaults
            implements ConfigProvider<VideoCaptureConfig> {
        private static final int DEFAULT_VIDEO_FRAME_RATE = 30;
        /** 8Mb/s the recommend rate for 30fps 1080p */
        private static final int DEFAULT_BIT_RATE = 8 * 1024 * 1024;
        /** Seconds between each key frame */
        private static final int DEFAULT_INTRA_FRAME_INTERVAL = 1;
        /** audio bit rate */
        private static final int DEFAULT_AUDIO_BIT_RATE = 64000;
        /** audio sample rate */
        private static final int DEFAULT_AUDIO_SAMPLE_RATE = 8000;
        /** audio channel count */
        private static final int DEFAULT_AUDIO_CHANNEL_COUNT = 1;
        /** audio record source */
        private static final int DEFAULT_AUDIO_RECORD_SOURCE = AudioSource.MIC;
        /** audio default minimum buffer size */
        private static final int DEFAULT_AUDIO_MIN_BUFFER_SIZE = 1024;
        /** Current max resolution of VideoCapture is set as FHD */
        private static final Size DEFAULT_MAX_RESOLUTION = new Size(1920, 1080);
        /** Surface occupancy prioirty to this use case */
        private static final int DEFAULT_SURFACE_OCCUPANCY_PRIORITY = 3;

        private static final VideoCaptureConfig DEFAULT_CONFIG;

        static {
            Builder builder = new Builder()
                    .setVideoFrameRate(DEFAULT_VIDEO_FRAME_RATE)
                    .setBitRate(DEFAULT_BIT_RATE)
                    .setIFrameInterval(DEFAULT_INTRA_FRAME_INTERVAL)
                    .setAudioBitRate(DEFAULT_AUDIO_BIT_RATE)
                    .setAudioSampleRate(DEFAULT_AUDIO_SAMPLE_RATE)
                    .setAudioChannelCount(DEFAULT_AUDIO_CHANNEL_COUNT)
                    .setAudioRecordSource(DEFAULT_AUDIO_RECORD_SOURCE)
                    .setAudioMinBufferSize(DEFAULT_AUDIO_MIN_BUFFER_SIZE)
                    .setMaxResolution(DEFAULT_MAX_RESOLUTION)
                    .setSurfaceOccupancyPriority(DEFAULT_SURFACE_OCCUPANCY_PRIORITY);

            DEFAULT_CONFIG = builder.getUseCaseConfig();
        }

        @NonNull
        @Override
        public VideoCaptureConfig getConfig(@Nullable CameraInfo cameraInfo) {
            return DEFAULT_CONFIG;
        }
    }

    /** Holder class for metadata that should be saved alongside captured video. */
    public static final class Metadata {
        /** Data representing a geographic location. */
        @Nullable
        public Location location;
    }

    private static final class VideoSavedListenerWrapper implements OnVideoSavedCallback {

        @NonNull
        Executor mExecutor;
        @NonNull
        OnVideoSavedCallback mOnVideoSavedCallback;

        VideoSavedListenerWrapper(@NonNull Executor executor,
                @NonNull OnVideoSavedCallback onVideoSavedCallback) {
            mExecutor = executor;
            mOnVideoSavedCallback = onVideoSavedCallback;
        }

        @Override
        public void onVideoSaved(@NonNull OutputFileResults outputFileResults) {
            try {
                mExecutor.execute(() -> mOnVideoSavedCallback.onVideoSaved(outputFileResults));
            } catch (RejectedExecutionException e) {
                Logger.e(TAG, "Unable to post to the supplied executor.");
            }
        }

        @Override
        public void onError(@VideoCaptureError int videoCaptureError, @NonNull String message,
                @Nullable Throwable cause) {
            try {
                mExecutor.execute(
                        () -> mOnVideoSavedCallback.onError(videoCaptureError, message, cause));
            } catch (RejectedExecutionException e) {
                Logger.e(TAG, "Unable to post to the supplied executor.");
            }
        }

    }

    /** Builder for a {@link VideoCapture}. */
    public static final class Builder
            implements
            UseCaseConfig.Builder<VideoCapture, VideoCaptureConfig, Builder>,
            ImageOutputConfig.Builder<Builder>,
            ThreadConfig.Builder<Builder> {

        private final MutableOptionsBundle mMutableConfig;

        /** Creates a new Builder object. */
        public Builder() {
            this(MutableOptionsBundle.create());
        }

        private Builder(@NonNull MutableOptionsBundle mutableConfig) {
            mMutableConfig = mutableConfig;

            Class<?> oldConfigClass =
                    mutableConfig.retrieveOption(OPTION_TARGET_CLASS, null);
            if (oldConfigClass != null && !oldConfigClass.equals(VideoCapture.class)) {
                throw new IllegalArgumentException(
                        "Invalid target class configuration for "
                                + Builder.this
                                + ": "
                                + oldConfigClass);
            }

            setTargetClass(VideoCapture.class);
        }

        /**
         * Generates a Builder from another Config object
         *
         * @param configuration An immutable configuration to pre-populate this builder.
         * @return The new Builder.
         */
        @NonNull
        public static Builder fromConfig(@NonNull VideoCaptureConfig configuration) {
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

        /**
         * {@inheritDoc}
         *
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public VideoCaptureConfig getUseCaseConfig() {
            return new VideoCaptureConfig(OptionsBundle.from(mMutableConfig));
        }

        /**
         * Builds an immutable {@link VideoCaptureConfig} from the current state.
         *
         * @return A {@link VideoCaptureConfig} populated with the current state.
         */
        @Override
        @NonNull
        public VideoCapture build() {
            // Error at runtime for using both setTargetResolution and setTargetAspectRatio on
            // the same config.
            if (getMutableConfig().retrieveOption(OPTION_TARGET_ASPECT_RATIO, null) != null
                    && getMutableConfig().retrieveOption(OPTION_TARGET_RESOLUTION, null) != null) {
                throw new IllegalArgumentException(
                        "Cannot use both setTargetResolution and setTargetAspectRatio on the same "
                                + "config.");
            }
            return new VideoCapture(getUseCaseConfig());
        }

        /**
         * Sets the recording frames per second.
         *
         * @param videoFrameRate The requested interval in seconds.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setVideoFrameRate(int videoFrameRate) {
            getMutableConfig().insertOption(OPTION_VIDEO_FRAME_RATE, videoFrameRate);
            return this;
        }

        /**
         * Sets the encoding bit rate.
         *
         * @param bitRate The requested bit rate in bits per second.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setBitRate(int bitRate) {
            getMutableConfig().insertOption(OPTION_BIT_RATE, bitRate);
            return this;
        }

        /**
         * Sets number of seconds between each key frame in seconds.
         *
         * @param interval The requested interval in seconds.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setIFrameInterval(int interval) {
            getMutableConfig().insertOption(OPTION_INTRA_FRAME_INTERVAL, interval);
            return this;
        }

        /**
         * Sets the bit rate of the audio stream.
         *
         * @param bitRate The requested bit rate in bits/s.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setAudioBitRate(int bitRate) {
            getMutableConfig().insertOption(OPTION_AUDIO_BIT_RATE, bitRate);
            return this;
        }

        /**
         * Sets the sample rate of the audio stream.
         *
         * @param sampleRate The requested sample rate in bits/s.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setAudioSampleRate(int sampleRate) {
            getMutableConfig().insertOption(OPTION_AUDIO_SAMPLE_RATE, sampleRate);
            return this;
        }

        /**
         * Sets the number of audio channels.
         *
         * @param channelCount The requested number of audio channels.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setAudioChannelCount(int channelCount) {
            getMutableConfig().insertOption(OPTION_AUDIO_CHANNEL_COUNT, channelCount);
            return this;
        }

        /**
         * Sets the audio source.
         *
         * @param source The audio source. Currently only AudioSource.MIC is supported.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setAudioRecordSource(int source) {
            getMutableConfig().insertOption(OPTION_AUDIO_RECORD_SOURCE, source);
            return this;
        }

        /**
         * Sets the audio min buffer size.
         *
         * @param minBufferSize The requested audio minimum buffer size, in bytes.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        public Builder setAudioMinBufferSize(int minBufferSize) {
            getMutableConfig().insertOption(OPTION_AUDIO_MIN_BUFFER_SIZE, minBufferSize);
            return this;
        }

        // Implementations of TargetConfig.Builder default methods

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @Override
        @NonNull
        public Builder setTargetClass(@NonNull Class<VideoCapture> targetClass) {
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

        // Implementations of ImageOutputConfig.Builder default methods

        /**
         * Sets the aspect ratio of the intended target for images from this configuration.
         *
         * <p>It is not allowed to set both target aspect ratio and target resolution on the same
         * use case.
         *
         * <p>The target aspect ratio is used as a hint when determining the resulting output aspect
         * ratio which may differ from the request, possibly due to device constraints.
         * Application code should check the resulting output's resolution.
         *
         * <p>If not set, resolutions with aspect ratio 4:3 will be considered in higher
         * priority.
         *
         * @param aspectRatio A {@link AspectRatio} representing the ratio of the
         *                    target's width and height.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder setTargetAspectRatio(@AspectRatio.Ratio int aspectRatio) {
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
         * {@link Display#getRotation()} of the default display at the time the use case is
         * created. The use case is fully created once it has been attached to a camera.
         *
         * @param rotation The rotation of the intended target.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder setTargetRotation(@RotationValue int rotation) {
            getMutableConfig().insertOption(OPTION_TARGET_ROTATION, rotation);
            return this;
        }

        /**
         * Sets the resolution of the intended target from this configuration.
         *
         * <p>The target resolution attempts to establish a minimum bound for the image resolution.
         * The actual image resolution will be the closest available resolution in size that is not
         * smaller than the target resolution, as determined by the Camera implementation. However,
         * if no resolution exists that is equal to or larger than the target resolution, the
         * nearest available resolution smaller than the target resolution will be chosen.
         *
         * <p>It is not allowed to set both target aspect ratio and target resolution on the same
         * use case.
         *
         * <p>The target aspect ratio will also be set the same as the aspect ratio of the provided
         * {@link Size}. Make sure to set the target resolution with the correct orientation.
         *
         * @param resolution The target resolution to choose from supported output sizes list.
         * @return The current Builder.
         * @hide
         */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
        @Override
        public Builder setTargetResolution(@NonNull Size resolution) {
            getMutableConfig().insertOption(OPTION_TARGET_RESOLUTION, resolution);
            return this;
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
        public Builder setDefaultResolution(@NonNull Size resolution) {
            getMutableConfig().insertOption(OPTION_DEFAULT_RESOLUTION, resolution);
            return this;
        }

        /** @hide */
        @RestrictTo(Scope.LIBRARY_GROUP)
        @NonNull
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
         * @hide
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
        @RestrictTo(Scope.LIBRARY)
        @Override
        @NonNull
        public Builder setCameraSelector(@NonNull CameraSelector cameraSelector) {
            getMutableConfig().insertOption(OPTION_CAMERA_SELECTOR, cameraSelector);
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
    }

    /**
     * Info about the saved video file.
     */
    public static class OutputFileResults {
        @Nullable
        private Uri mSavedUri;

        OutputFileResults(@Nullable Uri savedUri) {
            mSavedUri = savedUri;
        }

        /**
         * Returns the {@link Uri} of the saved video file.
         *
         * <p> This field is only returned if the {@link VideoCapture.OutputFileOptions} is
         * backed by {@link MediaStore} constructed with
         * {@link androidx.camera.core.VideoCapture.OutputFileOptions}.
         */
        @Nullable
        public Uri getSavedUri() {
            return mSavedUri;
        }
    }

    /**
     * Options for saving newly captured video.
     *
     * <p> this class is used to configure save location and metadata. Save location can be
     * either a {@link File}, {@link MediaStore}. The metadata will be
     * stored with the saved video.
     */
    public static final class OutputFileOptions {

        // Empty metadata object used as a placeholder for no user-supplied metadata.
        // Should be initialized to all default values.
        private static final Metadata EMPTY_METADATA = new Metadata();

        @Nullable
        private final File mFile;
        @Nullable
        private final ContentResolver mContentResolver;
        @Nullable
        private final Uri mSaveCollection;
        @Nullable
        private final ContentValues mContentValues;
        @Nullable
        private final Metadata mMetadata;

        OutputFileOptions(@Nullable File file,
                @Nullable ContentResolver contentResolver,
                @Nullable Uri saveCollection,
                @Nullable ContentValues contentValues,
                @Nullable Metadata metadata) {
            mFile = file;
            mContentResolver = contentResolver;
            mSaveCollection = saveCollection;
            mContentValues = contentValues;
            mMetadata = metadata == null ? EMPTY_METADATA : metadata;
        }

        /** Returns the File object which is set by the {@link OutputFileOptions.Builder}. */
        @Nullable
        File getFile() {
            return mFile;
        }

        /** Returns the content resolver which is set by the {@link OutputFileOptions.Builder}. */
        @Nullable
        ContentResolver getContentResolver() {
            return mContentResolver;
        }

        /** Returns the URI which is set by the {@link OutputFileOptions.Builder}. */
        @Nullable
        Uri getSaveCollection() {
            return mSaveCollection;
        }

        /** Returns the content values which is set by the {@link OutputFileOptions.Builder}. */
        @Nullable
        ContentValues getContentValues() {
            return mContentValues;
        }

        /** Return the metadata which is set by the {@link OutputFileOptions.Builder}.. */
        @Nullable
        Metadata getMetadata() {
            return mMetadata;
        }

        /** Checking the caller wants to save video to MediaStore. */
        boolean isSavingToMediaStore() {
            return getSaveCollection() != null && getContentResolver() != null
                    && getContentValues() != null;
        }

        /** Checking the caller wants to save video to a File. */
        boolean isSavingToFile() {
            return getFile() != null;
        }

        /**
         * Builder class for {@link OutputFileOptions}.
         */
        public static final class Builder {
            @Nullable
            private File mFile;
            @Nullable
            private ContentResolver mContentResolver;
            @Nullable
            private Uri mSaveCollection;
            @Nullable
            private ContentValues mContentValues;
            @Nullable
            private Metadata mMetadata;

            /**
             * Creates options to write captured video to a {@link File}.
             *
             * @param file save location of the video.
             */
            public Builder(@NonNull File file) {
                mFile = file;
            }

            /**
             * Creates options to write captured video to {@link MediaStore}.
             *
             * Example:
             *
             * <pre>{@code
             *
             * ContentValues contentValues = new ContentValues();
             * contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, "NEW_VIDEO");
             * contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
             *
             * OutputFileOptions options = new OutputFileOptions.Builder(
             *         getContentResolver(),
             *         MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
             *         contentValues).build();
             *
             * }</pre>
             *
             * @param contentResolver to access {@link MediaStore}
             * @param saveCollection  The URL of the table to insert into.
             * @param contentValues   to be included in the created video file.
             */
            public Builder(@NonNull ContentResolver contentResolver,
                    @NonNull Uri saveCollection,
                    @NonNull ContentValues contentValues) {
                mContentResolver = contentResolver;
                mSaveCollection = saveCollection;
                mContentValues = contentValues;
            }

            /**
             * Sets the metadata to be stored with the saved video.
             *
             * @param metadata Metadata to be stored with the saved video.
             */
            @NonNull
            public Builder setMetadata(@NonNull Metadata metadata) {
                mMetadata = metadata;
                return this;
            }

            /**
             * Builds {@link OutputFileOptions}.
             */
            @NonNull
            public OutputFileOptions build() {
                return new OutputFileOptions(mFile, mContentResolver, mSaveCollection,
                        mContentValues, mMetadata);
            }
        }
    }
}

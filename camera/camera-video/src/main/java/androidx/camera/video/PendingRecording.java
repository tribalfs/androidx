/*
 * Copyright 2021 The Android Open Source Project
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

import android.Manifest;
import android.content.Context;

import androidx.annotation.CheckResult;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresPermission;
import androidx.camera.core.impl.utils.ContextUtil;
import androidx.core.content.PermissionChecker;
import androidx.core.util.Consumer;
import androidx.core.util.Preconditions;

import java.util.concurrent.Executor;

/**
 * A recording that can be started at a future time.
 *
 * <p>A pending recording allows for configuration of a recording before it is started. Once a
 * pending recording is started with {@link #start()}, any changes to the pending recording will
 * not affect the actual recording; any modifications to the recording will need to occur through
 * the controls of the {@link ActiveRecording} class returned by {@link #start()}.
 *
 * <p>A pending recording can be created using one of the {@link Recorder} methods for starting a
 * recording such as {@link Recorder#prepareRecording(Context, MediaStoreOutputOptions)}.

 * <p>There may be more settings that can only be changed per-recorder instead of per-recording,
 * because it requires expensive operations like reconfiguring the camera. For those settings, use
 * the {@link Recorder.Builder} methods to configure before creating the {@link Recorder}
 * instance, then create the pending recording with it.
 */
public final class PendingRecording {

    private final Context mContext;
    private final Recorder mRecorder;
    private final OutputOptions mOutputOptions;
    private Consumer<VideoRecordEvent> mEventListener;
    private Executor mCallbackExecutor;

    private boolean mAudioEnabled = false;

    PendingRecording(@NonNull Context context, @NonNull Recorder recorder,
            @NonNull OutputOptions options) {
        // Application context is sufficient for all our needs, so store that to avoid leaking
        // unused resources
        mContext = ContextUtil.getApplicationContext(context);
        mRecorder = recorder;
        mOutputOptions = options;
    }

    @NonNull
    Recorder getRecorder() {
        return mRecorder;
    }

    @NonNull
    OutputOptions getOutputOptions() {
        return mOutputOptions;
    }

    @Nullable
    Executor getCallbackExecutor() {
        return mCallbackExecutor;
    }

    @Nullable
    Consumer<VideoRecordEvent> getEventListener() {
        return mEventListener;
    }

    boolean isAudioEnabled() {
        return mAudioEnabled;
    }

    /**
     * Sets the event listener that will receive {@link VideoRecordEvent} for this recording.
     *
     * @param callbackExecutor the executor that the event listener will be run on.
     * @param listener the event listener to handle the video record event.
     * @return this pending recording
     */
    @NonNull
    public PendingRecording withEventListener(@NonNull Executor callbackExecutor,
            @NonNull Consumer<VideoRecordEvent> listener) {
        Preconditions.checkNotNull(callbackExecutor, "CallbackExecutor can't be null.");
        Preconditions.checkNotNull(listener, "Event listener can't be null");
        mCallbackExecutor = callbackExecutor;
        mEventListener = listener;
        return this;
    }

    /**
     * Enables audio to be recorded for this recording.
     *
     * <p>This method must be called prior to {@link #start()} to enable audio in the recording. If
     * this method is not called, the {@link ActiveRecording} generated by {@link #start()}
     * will not contain audio, and {@link AudioStats#getAudioState()} will always return
     * {@link AudioStats#AUDIO_STATE_DISABLED} for all {@link RecordingStats} send to the listener
     * set by {@link #withEventListener(Executor, Consumer)}.
     *
     * <p>Recording with audio requires the {@link android.Manifest.permission#RECORD_AUDIO}
     * permission; without it, recording will fail at {@link #start()} with an
     * {@link IllegalStateException}.
     *
     * @return this pending recording
     * @throws IllegalStateException if the {@link Recorder} this recording is associated to
     * doesn't support audio.
     * @throws SecurityException if the current application does not have the
     * {@link Manifest.permission#RECORD_AUDIO} granted.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    @NonNull
    public PendingRecording withAudioEnabled() {
        // Check permissions and throw a security exception if RECORD_AUDIO is not granted.
        if (PermissionChecker.checkSelfPermission(mContext, Manifest.permission.RECORD_AUDIO)
                == PermissionChecker.PERMISSION_DENIED) {
            throw new SecurityException("Attempted to enable audio for recording but application "
                    + "does not have RECORD_AUDIO permission granted.");
        }
        Preconditions.checkState(mRecorder.isAudioSupported(), "The Recorder this recording is "
                + "associated to doesn't support audio.");
        mAudioEnabled = true;
        return this;
    }

    /**
     * Starts the recording, making it an active recording.
     *
     * <p>Only a single recording can be active at a time, so if another recording is active,
     * this will throw an {@link IllegalStateException}.
     *
     * <p>If there are no errors starting the recording, the returned {@link ActiveRecording}
     * can be used to {@link ActiveRecording#pause() pause}, {@link ActiveRecording#resume() resume
     * }, or {@link ActiveRecording#stop() stop} the recording.
     *
     * <p>Upon successfully starting the recording, a {@link VideoRecordEvent.Start} event will
     * be the first event sent to the listener set in
     * {@link #withEventListener(Executor, Consumer)}.
     *
     * <p>If errors occur while starting the recording, a {@link VideoRecordEvent.Finalize} event
     * will be the first event sent to the listener set in
     * {@link #withEventListener(Executor, Consumer)}, and information about the error can be
     * found in that event's {@link VideoRecordEvent.Finalize#getError()} method. The returned
     * {@link ActiveRecording} will be in a finalized state, and all controls will be no-ops.
     *
     * <p>If the returned {@link ActiveRecording} is garbage collected, the recording will be
     * automatically stopped. A reference to the active recording must be maintained as long as
     * the recording needs to be active.
     *
     * @throws IllegalStateException if the associated Recorder currently has an unfinished
     * active recording, or if the recording has {@link #withAudioEnabled()} audio} but
     * {@link android.Manifest.permission#RECORD_AUDIO} is not granted.
     */
    @NonNull
    @CheckResult
    public ActiveRecording start() {
        return mRecorder.start(this);
    }
}

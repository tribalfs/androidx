/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.media2;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.annotation.TargetApi;
import android.graphics.SurfaceTexture;
import android.media.AudioAttributes;
import android.media.DeniedByServerException;
import android.media.MediaDataSource;
import android.media.MediaDrm;
import android.media.MediaPlayer;
import android.media.MediaTimestamp;
import android.media.PlaybackParams;
import android.media.ResourceBusyException;
import android.media.SubtitleData;
import android.media.SyncParams;
import android.media.TimedMetaData;
import android.media.UnsupportedSchemeException;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Parcel;
import android.os.PersistableBundle;
import android.util.Log;
import android.util.Pair;
import android.view.Surface;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.collection.ArrayMap;
import androidx.core.util.ObjectsCompat;
import androidx.core.util.Preconditions;
import androidx.media.AudioAttributesCompat;
import androidx.media2.MediaPlayerConnector.BuffState;
import androidx.media2.MediaPlayerConnector.PlayerEventCallback;
import androidx.media2.MediaPlayerConnector.PlayerState;
import androidx.media2.common.TrackInfoImpl;

import java.io.IOException;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @hide
 */
@TargetApi(Build.VERSION_CODES.P)
@RestrictTo(LIBRARY_GROUP)
public final class MediaPlayer2Impl extends MediaPlayer2 {

    private static final String TAG = "MediaPlayer2Impl";

    private static final int SOURCE_STATE_ERROR = -1;
    private static final int SOURCE_STATE_INIT = 0;
    private static final int SOURCE_STATE_PREPARING = 1;
    private static final int SOURCE_STATE_PREPARED = 2;

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static final PlaybackParams DEFAULT_PLAYBACK_PARAMS =
            new PlaybackParams().allowDefaults();

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static ArrayMap<Integer, Integer> sInfoEventMap;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static ArrayMap<Integer, Integer> sErrorEventMap;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static ArrayMap<Integer, Integer> sStateMap;

    static {
        sInfoEventMap = new ArrayMap<>();
        sInfoEventMap.put(MediaPlayer.MEDIA_INFO_UNKNOWN, MEDIA_INFO_UNKNOWN);
        sInfoEventMap.put(MediaPlayer.MEDIA_INFO_STARTED_AS_NEXT, MEDIA_INFO_UNKNOWN);
        sInfoEventMap.put(
                MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START, MEDIA_INFO_VIDEO_RENDERING_START);
        sInfoEventMap.put(
                MediaPlayer.MEDIA_INFO_VIDEO_TRACK_LAGGING, MEDIA_INFO_VIDEO_TRACK_LAGGING);
        sInfoEventMap.put(MediaPlayer.MEDIA_INFO_BUFFERING_START, MEDIA_INFO_BUFFERING_START);
        sInfoEventMap.put(MediaPlayer.MEDIA_INFO_BUFFERING_END, MEDIA_INFO_BUFFERING_END);
        sInfoEventMap.put(MediaPlayer.MEDIA_INFO_BAD_INTERLEAVING, MEDIA_INFO_BAD_INTERLEAVING);
        sInfoEventMap.put(MediaPlayer.MEDIA_INFO_NOT_SEEKABLE, MEDIA_INFO_NOT_SEEKABLE);
        sInfoEventMap.put(MediaPlayer.MEDIA_INFO_METADATA_UPDATE, MEDIA_INFO_METADATA_UPDATE);
        sInfoEventMap.put(MediaPlayer.MEDIA_INFO_AUDIO_NOT_PLAYING, MEDIA_INFO_AUDIO_NOT_PLAYING);
        sInfoEventMap.put(MediaPlayer.MEDIA_INFO_VIDEO_NOT_PLAYING, MEDIA_INFO_VIDEO_NOT_PLAYING);
        sInfoEventMap.put(
                MediaPlayer.MEDIA_INFO_UNSUPPORTED_SUBTITLE, MEDIA_INFO_UNSUPPORTED_SUBTITLE);
        sInfoEventMap.put(MediaPlayer.MEDIA_INFO_SUBTITLE_TIMED_OUT, MEDIA_INFO_SUBTITLE_TIMED_OUT);

        sErrorEventMap = new ArrayMap<>();
        sErrorEventMap.put(MediaPlayer.MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_UNKNOWN);
        sErrorEventMap.put(
                MediaPlayer.MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK,
                MEDIA_ERROR_NOT_VALID_FOR_PROGRESSIVE_PLAYBACK);
        sErrorEventMap.put(MediaPlayer.MEDIA_ERROR_IO, MEDIA_ERROR_IO);
        sErrorEventMap.put(MediaPlayer.MEDIA_ERROR_MALFORMED, MEDIA_ERROR_MALFORMED);
        sErrorEventMap.put(MediaPlayer.MEDIA_ERROR_UNSUPPORTED, MEDIA_ERROR_UNSUPPORTED);
        sErrorEventMap.put(MediaPlayer.MEDIA_ERROR_TIMED_OUT, MEDIA_ERROR_TIMED_OUT);

        sStateMap = new ArrayMap<>();
        sStateMap.put(PLAYER_STATE_IDLE, MediaPlayerConnector.PLAYER_STATE_IDLE);
        sStateMap.put(PLAYER_STATE_PREPARED, MediaPlayerConnector.PLAYER_STATE_PAUSED);
        sStateMap.put(PLAYER_STATE_PAUSED, MediaPlayerConnector.PLAYER_STATE_PAUSED);
        sStateMap.put(PLAYER_STATE_PLAYING, MediaPlayerConnector.PLAYER_STATE_PLAYING);
        sStateMap.put(PLAYER_STATE_ERROR, MediaPlayerConnector.PLAYER_STATE_ERROR);
    }

    MediaPlayerSourceQueue mPlayer;

    private HandlerThread mHandlerThread;
    private final Handler mEndPositionHandler;
    private final Handler mTaskHandler;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final Object mTaskLock = new Object();
    @GuardedBy("mTaskLock")
    private final ArrayDeque<Task> mPendingTasks = new ArrayDeque<>();
    @GuardedBy("mTaskLock")
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    Task mCurrentTask;

    private final Object mLock = new Object();
    //--- guarded by |mLock| start
    private Pair<Executor, EventCallback> mMp2EventCallbackRecord;
    private ArrayMap<PlayerEventCallback, Executor> mPlayerEventCallbackMap =
            new ArrayMap<>();
    private Pair<Executor, DrmEventCallback> mDrmEventCallbackRecord;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    MediaPlayerConnectorImpl mMediaPlayerConnectorImpl;
    //--- guarded by |mLock| end

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void handleDataSourceError(final DataSourceError err) {
        if (err == null) {
            return;
        }
        notifyMediaPlayer2Event(new Mp2EventNotifier() {
            @Override
            public void notify(EventCallback callback) {
                callback.onError(MediaPlayer2Impl.this, err.mDSD, err.mWhat, err.mExtra);
            }
        });
    }

    /**
     * Default constructor.
     * <p>When done with the MediaPlayer2Impl, you should call  {@link #close()},
     * to free the resources. If not released, too many MediaPlayer2Impl instances may
     * result in an exception.</p>
     */
    public MediaPlayer2Impl() {
        mHandlerThread = new HandlerThread("MediaPlayer2TaskThread");
        mHandlerThread.start();
        Looper looper = mHandlerThread.getLooper();
        mEndPositionHandler = new Handler(looper);
        mTaskHandler = new Handler(looper);

        // TODO: To make sure MediaPlayer1 listeners work, the caller thread should have a looper.
        // Fix the framework or document this behavior.
        mPlayer = new MediaPlayerSourceQueue();
    }

    /**
     * Returns a {@link MediaPlayerConnector} implementation which runs based on
     * this MediaPlayer2 instance.
     */
    @Override
    public MediaPlayerConnector getMediaPlayerConnector() {
        synchronized (mLock) {
            if (mMediaPlayerConnectorImpl == null) {
                mMediaPlayerConnectorImpl = new MediaPlayerConnectorImpl();
            }
            return mMediaPlayerConnectorImpl;
        }
    }

    /**
     * Releases the resources held by this {@code MediaPlayer2} object.
     *
     * It is considered good practice to call this method when you're
     * done using the MediaPlayer2. In particular, whenever an Activity
     * of an application is paused (its onPause() method is called),
     * or stopped (its onStop() method is called), this method should be
     * invoked to release the MediaPlayer2 object, unless the application
     * has a special need to keep the object around. In addition to
     * unnecessary resources (such as memory and instances of codecs)
     * being held, failure to call this method immediately if a
     * MediaPlayer2 object is no longer needed may also lead to
     * continuous battery consumption for mobile devices, and playback
     * failure for other applications if no multiple instances of the
     * same codec are supported on a device. Even if multiple instances
     * of the same codec are supported, some performance degradation
     * may be expected when unnecessary multiple instances are used
     * at the same time.
     *
     * {@code close()} may be safely called after a prior {@code close()}.
     * This class implements the Java {@code AutoCloseable} interface and
     * may be used with try-with-resources.
     */
    @Override
    public void close() {
        mPlayer.release();
        if (mHandlerThread != null) {
            mHandlerThread.quitSafely();
            mHandlerThread = null;
        }
    }

    /**
     * Starts or resumes playback. If playback had previously been paused,
     * playback will continue from where it was paused. If playback had
     * been stopped, or never started before, playback will start at the
     * beginning.
     */
    @Override
    public void play() {
        addTask(new Task(CALL_COMPLETED_PLAY, false) {
            @Override
            void process() {
                mPlayer.play();
            }
        });
    }

    /**
     * Prepares the player for playback, asynchronously.
     *
     * After setting the datasource and the display surface, you need to either
     * call prepare(). For streams, you should call prepare(),
     * which returns immediately, rather than blocking until enough data has been
     * buffered.
     */
    @Override
    public void prepare() {
        addTask(new Task(CALL_COMPLETED_PREPARE, true) {
            @Override
            void process() throws IOException {
                mPlayer.prepareAsync();
            }
        });
    }

    /**
     * Pauses playback. Call play() to resume.
     */
    @Override
    public void pause() {
        addTask(new Task(CALL_COMPLETED_PAUSE, false) {
            @Override
            void process() {
                mPlayer.pause();
            }
        });
    }

    /**
     * Tries to play next data source if applicable.
     */
    @Override
    public void skipToNext() {
        addTask(new Task(CALL_COMPLETED_SKIP_TO_NEXT, false) {
            @Override
            void process() {
                mPlayer.skipToNext();
            }
        });
    }

    /**
     * Gets the current playback position.
     *
     * @return the current position in milliseconds
     */
    @Override
    public long getCurrentPosition() {
        return mPlayer.getCurrentPosition();
    }

    /**
     * Gets the duration of the file.
     *
     * @return the duration in milliseconds, if no duration is available
     * (for example, if streaming live content), -1 is returned.
     */
    @Override
    public long getDuration() {
        return mPlayer.getDuration();
    }

    /**
     * Gets the current buffered media source position received through progressive downloading.
     * The received buffering percentage indicates how much of the content has been buffered
     * or played. For example a buffering update of 80 percent when half the content
     * has already been played indicates that the next 30 percent of the
     * content to play has been buffered.
     *
     * @return the current buffered media source position in milliseconds
     */
    @Override
    public long getBufferedPosition() {
        // Use cached buffered percent for now.
        return mPlayer.getBufferedPosition();
    }

    /**
     * Gets the current MediaPlayer2 state.
     *
     * @return the current MediaPlayer2 state.
     */
    @Override
    public @MediaPlayer2State int getState() {
        return mPlayer.getMediaPlayer2State();
    }

    @MediaPlayerConnector.PlayerState int getPlayerState() {
        return mPlayer.getPlayerState();
    }

    /**
     * Gets the current buffering state of the player.
     * During buffering, see {@link #getBufferedPosition()} for the quantifying the amount already
     * buffered.
     */
    @MediaPlayerConnector.BuffState int getBufferingState() {
        return  mPlayer.getBufferingState();
    }

    /**
     * Sets the audio attributes for this MediaPlayer2.
     * See {@link AudioAttributes} for how to build and configure an instance of this class.
     * You must call this method before {@link #prepare()} in order
     * for the audio attributes to become effective thereafter.
     * @param attributes a non-null set of audio attributes
     * @throws IllegalArgumentException if the attributes are null or invalid.
     */
    @Override
    public void setAudioAttributes(@NonNull final AudioAttributesCompat attributes) {
        addTask(new Task(CALL_COMPLETED_SET_AUDIO_ATTRIBUTES, false) {
            @Override
            void process() {
                mPlayer.setAudioAttributes(attributes);
            }
        });
    }

    @Override
    public @Nullable AudioAttributesCompat getAudioAttributes() {
        return mPlayer.getAudioAttributes();
    }

    /**
     * Sets the data source as described by a DataSourceDesc2.
     *
     * @param dsd the descriptor of data source you want to play
     * @throws IllegalStateException if it is called in an invalid state
     * @throws NullPointerException if dsd is null
     */
    @Override
    public void setDataSource(@NonNull final DataSourceDesc2 dsd) {
        addTask(new Task(CALL_COMPLETED_SET_DATA_SOURCE, false) {
            @Override
            void process() {
                Preconditions.checkArgument(dsd != null, "the DataSourceDesc2 cannot be null");
                // TODO: setDataSource could update exist data source
                try {
                    mPlayer.setFirst(dsd);
                } catch (IOException e) {
                    Log.e(TAG, "process: setDataSource", e);
                }
            }
        });
    }

    /**
     * Sets a single data source as described by a DataSourceDesc2 which will be played
     * after current data source is finished.
     *
     * @param dsd the descriptor of data source you want to play after current one
     * @throws IllegalStateException if it is called in an invalid state
     * @throws NullPointerException if dsd is null
     */
    @Override
    public void setNextDataSource(@NonNull final DataSourceDesc2 dsd) {
        addTask(new Task(CALL_COMPLETED_SET_NEXT_DATA_SOURCE, false) {
            @Override
            void process() {
                Preconditions.checkArgument(dsd != null, "the DataSourceDesc2 cannot be null");
                handleDataSourceError(mPlayer.setNext(dsd));
            }
        });
    }

    /**
     * Sets a list of data sources to be played sequentially after current data source is done.
     *
     * @param dsds the list of data sources you want to play after current one
     * @throws IllegalStateException if it is called in an invalid state
     * @throws IllegalArgumentException if dsds is null or empty, or contains null DataSourceDesc2
     */
    @Override
    public void setNextDataSources(@NonNull final List<DataSourceDesc2> dsds) {
        addTask(new Task(CALL_COMPLETED_SET_NEXT_DATA_SOURCES, false) {
            @Override
            void process() {
                if (dsds == null || dsds.size() == 0) {
                    throw new IllegalArgumentException("data source list cannot be null or empty.");
                }
                for (DataSourceDesc2 dsd : dsds) {
                    if (dsd == null) {
                        throw new IllegalArgumentException(
                                "DataSourceDesc2 in the source list cannot be null.");
                    }
                }
                handleDataSourceError(mPlayer.setNextMultiple(dsds));
            }
        });
    }

    @Override public @NonNull
    DataSourceDesc2 getCurrentDataSource() {
        return mPlayer.getFirst().getDSD();
    }

    /**
     * Configures the player to loop on the current data source.
     * @param loop true if the current data source is meant to loop.
     */
    @Override
    public void loopCurrent(final boolean loop) {
        addTask(new Task(CALL_COMPLETED_LOOP_CURRENT, false) {
            @Override
            void process() {
                mPlayer.setLooping(loop);
            }
        });
    }

    /**
     * Sets the volume of the audio of the media to play, expressed as a linear multiplier
     * on the audio samples.
     * Note that this volume is specific to the player, and is separate from stream volume
     * used across the platform.<br>
     * A value of 0.0f indicates muting, a value of 1.0f is the nominal unattenuated and unamplified
     * gain. See {@link #getMaxPlayerVolume()} for the volume range supported by this player.
     * @param volume a value between 0.0f and {@link #getMaxPlayerVolume()}.
     */
    @Override
    public void setPlayerVolume(final float volume) {
        addTask(new Task(CALL_COMPLETED_SET_PLAYER_VOLUME, false) {
            @Override
            void process() {
                mPlayer.setVolume(volume);
            }
        });
    }

    /**
     * Returns the current volume of this player to this player.
     * Note that it does not take into account the associated stream volume.
     * @return the player volume.
     */
    @Override
    public float getPlayerVolume() {
        return mPlayer.getVolume();
    }

    /**
     * @return the maximum volume that can be used in {@link #setPlayerVolume(float)}.
     */
    @Override
    public float getMaxPlayerVolume() {
        return 1.0f;
    }

    /**
     * Adds a callback to be notified of events for this player.
     * @param e the {@link Executor} to be used for the events.
     * @param cb the callback to receive the events.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void registerPlayerEventCallback(@NonNull Executor e,
            @NonNull PlayerEventCallback cb) {
        if (cb == null) {
            throw new IllegalArgumentException("Illegal null PlayerEventCallback");
        }
        if (e == null) {
            throw new IllegalArgumentException(
                    "Illegal null Executor for the PlayerEventCallback");
        }
        synchronized (mLock) {
            mPlayerEventCallbackMap.put(cb, e);
        }
    }

    /**
     * Removes a previously registered callback for player events
     * @param cb the callback to remove
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void unregisterPlayerEventCallback(@NonNull PlayerEventCallback cb) {
        if (cb == null) {
            throw new IllegalArgumentException("Illegal null PlayerEventCallback");
        }
        synchronized (mLock) {
            mPlayerEventCallbackMap.remove(cb);
        }
    }

    @Override
    public void notifyWhenCommandLabelReached(final Object label) {
        addTask(new Task(CALL_COMPLETED_NOTIFY_WHEN_COMMAND_LABEL_REACHED, false) {
            @Override
            void process() {
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback cb) {
                        cb.onCommandLabelReached(MediaPlayer2Impl.this, label);
                    }
                });
            }
        });
    }

    /**
     * Sets the {@link Surface} to be used as the sink for the video portion of
     * the media. Setting a Surface will un-set any Surface or SurfaceHolder that
     * was previously set. A null surface will result in only the audio track
     * being played.
     *
     * If the Surface sends frames to a {@link SurfaceTexture}, the timestamps
     * returned from {@link SurfaceTexture#getTimestamp()} will have an
     * unspecified zero point.  These timestamps cannot be directly compared
     * between different media sources, different instances of the same media
     * source, or multiple runs of the same program.  The timestamp is normally
     * monotonically increasing and is unaffected by time-of-day adjustments,
     * but it is reset when the position is set.
     *
     * @param surface The {@link Surface} to be used for the video portion of
     * the media.
     * @throws IllegalStateException if the internal player engine has not been
     * initialized or has been released.
     */
    @Override
    public void setSurface(final Surface surface) {
        addTask(new Task(CALL_COMPLETED_SET_SURFACE, false) {
            @Override
            void process() {
                mPlayer.setSurface(surface);
            }
        });
    }

    /**
     * Discards all pending commands.
     */
    @Override
    public void clearPendingCommands() {
        synchronized (mTaskLock) {
            mPendingTasks.clear();
        }
    }

    private void addTask(Task task) {
        synchronized (mTaskLock) {
            if (task.mMediaCallType == MediaPlayer2.CALL_COMPLETED_SEEK_TO) {
                Task previous = mPendingTasks.peekLast();
                if (previous != null && previous.mMediaCallType == task.mMediaCallType) {
                    // Skip the unnecessary previous seek command.
                    previous.mSkip = true;
                }
            }
            mPendingTasks.add(task);
            processPendingTask_l();
        }
    }

    @GuardedBy("mTaskLock")
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void processPendingTask_l() {
        if (mCurrentTask != null) {
            return;
        }
        if (!mPendingTasks.isEmpty()) {
            Task task = mPendingTasks.removeFirst();
            mCurrentTask = task;
            mTaskHandler.post(task);
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static void handleDataSource(MediaPlayerSource src)
            throws IOException {
        final DataSourceDesc2 dsd = src.getDSD();
        Preconditions.checkArgument(dsd != null, "the DataSourceDesc2 cannot be null");

        MediaPlayer player = src.getPlayer();
        if (dsd instanceof CallbackDataSourceDesc2) {
            player.setDataSource(new MediaDataSource() {
                DataSourceCallback2 mDataSource =
                        ((CallbackDataSourceDesc2) dsd).getDataSourceCallback2();

                @Override
                public int readAt(long position, byte[] buffer, int offset, int size)
                        throws IOException {
                    return mDataSource.readAt(position, buffer, offset, size);
                }

                @Override
                public long getSize() throws IOException {
                    return mDataSource.getSize();
                }

                @Override
                public void close() throws IOException {
                    mDataSource.close();
                }
            });
        } else if (dsd instanceof FileDataSourceDesc2) {
            FileDataSourceDesc2 fdsd = (FileDataSourceDesc2) dsd;
            player.setDataSource(
                    fdsd.getFileDescriptor(),
                    fdsd.getFileDescriptorOffset(),
                    fdsd.getFileDescriptorLength());
        } else if (dsd instanceof UriDataSourceDesc2) {
            UriDataSourceDesc2 udsd = (UriDataSourceDesc2) dsd;
            player.setDataSource(
                    udsd.getUriContext(),
                    udsd.getUri(),
                    udsd.getUriHeaders(),
                    udsd.getUriCookies());
        } else {
            throw new IllegalArgumentException(
                    "Unsupported data source description. " + dsd.toString());
        }
    }

    /**
     * Returns the width of the video.
     *
     * @return the width of the video, or 0 if there is no video,
     * no display surface was set, or the width has not been determined
     * yet. The {@code MediaPlayer2EventCallback} can be registered via
     * {@link #setEventCallback(Executor, EventCallback)} to provide a
     * notification {@code MediaPlayer2EventCallback.onVideoSizeChanged} when the width
     * is available.
     */
    @Override
    public int getVideoWidth() {
        return mPlayer.getVideoWidth();
    }

    /**
     * Returns the height of the video.
     *
     * @return the height of the video, or 0 if there is no video,
     * no display surface was set, or the height has not been determined
     * yet. The {@code MediaPlayer2EventCallback} can be registered via
     * {@link #setEventCallback(Executor, EventCallback)} to provide a
     * notification {@code MediaPlayer2EventCallback.onVideoSizeChanged} when the height
     * is available.
     */
    @Override
    public int getVideoHeight() {
        return mPlayer.getVideoHeight();
    }

    @Override
    public PersistableBundle getMetrics() {
        return mPlayer.getMetrics();
    }

    /**
     * Sets playback rate using {@link PlaybackParams2}. The object sets its internal
     * PlaybackParams2 to the input, except that the object remembers previous speed
     * when input speed is zero. This allows the object to resume at previous speed
     * when play() is called. Calling it before the object is prepared does not change
     * the object state. After the object is prepared, calling it with zero speed is
     * equivalent to calling pause(). After the object is prepared, calling it with
     * non-zero speed is equivalent to calling play().
     *
     * @param params the playback params.
     * @throws IllegalStateException if the internal player engine has not been
     * initialized or has been released.
     * @throws IllegalArgumentException if params is not supported.
     */
    @Override
    public void setPlaybackParams(@NonNull final PlaybackParams2 params) {
        addTask(new Task(CALL_COMPLETED_SET_PLAYBACK_PARAMS, false) {
            @Override
            void process() {
                mPlayer.setPlaybackParams(params.getPlaybackParams());
            }
        });
    }

    /**
     * Gets the playback params, containing the current playback rate.
     *
     * @return the playback params.
     * @throws IllegalStateException if the internal player engine has not been
     * initialized.
     */
    @Override
    @NonNull
    public PlaybackParams2 getPlaybackParams() {
        return new PlaybackParams2.Builder(mPlayer.getPlaybackParams()).build();
    }

    /**
     * Moves the media to specified time position by considering the given mode.
     * <p>
     * When seekTo is finished, the user will be notified via OnSeekComplete supplied by the user.
     * There is at most one active seekTo processed at any time. If there is a to-be-completed
     * seekTo, new seekTo requests will be queued in such a way that only the last request
     * is kept. When current seekTo is completed, the queued request will be processed if
     * that request is different from just-finished seekTo operation, i.e., the requested
     * position or mode is different.
     *
     * @param msec the offset in milliseconds from the start to seek to.
     * When seeking to the given time position, there is no guarantee that the data source
     * has a frame located at the position. When this happens, a frame nearby will be rendered.
     * If msec is negative, time position zero will be used.
     * If msec is larger than duration, duration will be used.
     * @param mode the mode indicating where exactly to seek to.
     * Use {@link #SEEK_PREVIOUS_SYNC} if one wants to seek to a sync frame
     * that has a timestamp earlier than or the same as msec. Use
     * {@link #SEEK_NEXT_SYNC} if one wants to seek to a sync frame
     * that has a timestamp later than or the same as msec. Use
     * {@link #SEEK_CLOSEST_SYNC} if one wants to seek to a sync frame
     * that has a timestamp closest to or the same as msec. Use
     * {@link #SEEK_CLOSEST} if one wants to seek to a frame that may
     * or may not be a sync frame but is closest to or the same as msec.
     * {@link #SEEK_CLOSEST} often has larger performance overhead compared
     * to the other options if there is no sync frame located at msec.
     * @throws IllegalStateException if the internal player engine has not been
     * initialized
     * @throws IllegalArgumentException if the mode is invalid.
     */
    @Override
    public void seekTo(final long msec, @SeekMode final int mode) {
        addTask(new Task(CALL_COMPLETED_SEEK_TO, true) {
            @Override
            void process() {
                mPlayer.seekTo(msec, mode);
            }
        });
    }

    /**
     * Get current playback position as a {@link MediaTimestamp2}.
     * <p>
     * The MediaTimestamp2 represents how the media time correlates to the system time in
     * a linear fashion using an anchor and a clock rate. During regular playback, the media
     * time moves fairly constantly (though the anchor frame may be rebased to a current
     * system time, the linear correlation stays steady). Therefore, this method does not
     * need to be called often.
     * <p>
     * To help users get current playback position, this method always anchors the timestamp
     * to the current {@link System#nanoTime system time}, so
     * {@link MediaTimestamp2#getAnchorMediaTimeUs} can be used as current playback position.
     *
     * @return a MediaTimestamp2 object if a timestamp is available, or {@code null} if no timestamp
     * is available, e.g. because the media player has not been initialized.
     * @see MediaTimestamp2
     */
    @Override
    @Nullable
    public MediaTimestamp2 getTimestamp() {
        return mPlayer.getTimestamp();
    }

    /**
     * Resets the MediaPlayer2 to its uninitialized state. After calling
     * this method, you will have to initialize it again by setting the
     * data source and calling prepare().
     */
    @Override
    public void reset() {
        mPlayer.reset();
    }

    /**
     * Sets the audio session ID.
     *
     * @param sessionId the audio session ID.
     * The audio session ID is a system wide unique identifier for the audio stream played by
     * this MediaPlayer2 instance.
     * The primary use of the audio session ID  is to associate audio effects to a particular
     * instance of MediaPlayer2: if an audio session ID is provided when creating an audio effect,
     * this effect will be applied only to the audio content of media players within the same
     * audio session and not to the output mix.
     * When created, a MediaPlayer2 instance automatically generates its own audio session ID.
     * However, it is possible to force this player to be part of an already existing audio session
     * by calling this method.
     * This method must be called before one of the overloaded <code> setDataSource </code> methods.
     * @throws IllegalStateException if it is called in an invalid state
     * @throws IllegalArgumentException if the sessionId is invalid.
     */
    @Override
    public void setAudioSessionId(final int sessionId) {
        addTask(new Task(CALL_COMPLETED_SET_AUDIO_SESSION_ID, false) {
            @Override
            void process() {
                mPlayer.setAudioSessionId(sessionId);
            }
        });
    }

    @Override
    public int getAudioSessionId() {
        return mPlayer.getAudioSessionId();
    }

    /**
     * Attaches an auxiliary effect to the player. A typical auxiliary effect is a reverberation
     * effect which can be applied on any sound source that directs a certain amount of its
     * energy to this effect. This amount is defined by setAuxEffectSendLevel().
     * See {@link #setAuxEffectSendLevel(float)}.
     * <p>After creating an auxiliary effect (e.g.
     * {@link android.media.audiofx.EnvironmentalReverb}), retrieve its ID with
     * {@link android.media.audiofx.AudioEffect#getId()} and use it when calling this method
     * to attach the player to the effect.
     * <p>To detach the effect from the player, call this method with a null effect id.
     * <p>This method must be called after one of the overloaded <code> setDataSource </code>
     * methods.
     * @param effectId system wide unique id of the effect to attach
     */
    @Override
    public void attachAuxEffect(final int effectId) {
        addTask(new Task(CALL_COMPLETED_ATTACH_AUX_EFFECT, false) {
            @Override
            void process() {
                mPlayer.attachAuxEffect(effectId);
            }
        });
    }

    /**
     * Sets the send level of the player to the attached auxiliary effect.
     * See {@link #attachAuxEffect(int)}. The level value range is 0 to 1.0.
     * <p>By default the send level is 0, so even if an effect is attached to the player
     * this method must be called for the effect to be applied.
     * <p>Note that the passed level value is a raw scalar. UI controls should be scaled
     * logarithmically: the gain applied by audio framework ranges from -72dB to 0dB,
     * so an appropriate conversion from linear UI input x to level is:
     * x == 0 -> level = 0
     * 0 < x <= R -> level = 10^(72*(x-R)/20/R)
     * @param level send level scalar
     */
    @Override
    public void setAuxEffectSendLevel(final float level) {
        addTask(new Task(CALL_COMPLETED_SET_AUX_EFFECT_SEND_LEVEL, false) {
            @Override
            void process() {
                mPlayer.setAuxEffectSendLevel(level);
            }
        });
    }

    /**
     * Returns a List of track information.
     *
     * @return List of track info. The total number of tracks is the array length.
     * Must be called again if an external timed text source has been added after
     * addTimedTextSource method is called.
     * @throws IllegalStateException if it is called in an invalid state.
     */
    @Override
    public List<TrackInfo> getTrackInfo() {
        MediaPlayer.TrackInfo[] list = mPlayer.getTrackInfo();
        List<TrackInfo> trackList = new ArrayList<>();
        for (MediaPlayer.TrackInfo info : list) {
            trackList.add(new TrackInfoImpl(info.getTrackType(), info.getFormat()));
        }
        return trackList;
    }

    /**
     * Returns the index of the audio, video, or subtitle track currently selected for playback,
     * The return value is an index into the array returned by {@link #getTrackInfo()}, and can
     * be used in calls to {@link #selectTrack(int)} or {@link #deselectTrack(int)}.
     *
     * @param trackType should be one of {@link TrackInfo#MEDIA_TRACK_TYPE_VIDEO},
     * {@link TrackInfo#MEDIA_TRACK_TYPE_AUDIO}, or
     * {@link TrackInfo#MEDIA_TRACK_TYPE_SUBTITLE}
     * @return index of the audio, video, or subtitle track currently selected for playback;
     * a negative integer is returned when there is no selected track for {@code trackType} or
     * when {@code trackType} is not one of audio, video, or subtitle.
     * @throws IllegalStateException if called after {@link #close()}
     *
     * @see #getTrackInfo()
     * @see #selectTrack(int)
     * @see #deselectTrack(int)
     */
    @Override
    public int getSelectedTrack(int trackType) {
        return mPlayer.getSelectedTrack(trackType);
    }

    /**
     * Selects a track.
     * <p>
     * If a MediaPlayer2 is in invalid state, it throws an IllegalStateException exception.
     * If a MediaPlayer2 is in <em>Started</em> state, the selected track is presented immediately.
     * If a MediaPlayer2 is not in Started state, it just marks the track to be played.
     * </p>
     * <p>
     * In any valid state, if it is called multiple times on the same type of track (ie. Video,
     * Audio, Timed Text), the most recent one will be chosen.
     * </p>
     * <p>
     * The first audio and video tracks are selected by default if available, even though
     * this method is not called. However, no timed text track will be selected until
     * this function is called.
     * </p>
     * <p>
     * Currently, only timed text tracks or audio tracks can be selected via this method.
     * In addition, the support for selecting an audio track at runtime is pretty limited
     * in that an audio track can only be selected in the <em>Prepared</em> state.
     * </p>
     *
     * @param index the index of the track to be selected. The valid range of the index
     * is 0..total number of track - 1. The total number of tracks as well as the type of
     * each individual track can be found by calling {@link #getTrackInfo()} method.
     * @throws IllegalStateException if called in an invalid state.
     * @see MediaPlayer2#getTrackInfo
     */
    @Override
    public void selectTrack(final int index) {
        addTask(new Task(CALL_COMPLETED_SELECT_TRACK, false) {
            @Override
            void process() {
                mPlayer.selectTrack(index);
            }
        });
    }

    /**
     * Deselect a track.
     * <p>
     * Currently, the track must be a timed text track and no audio or video tracks can be
     * deselected. If the timed text track identified by index has not been
     * selected before, it throws an exception.
     * </p>
     *
     * @param index the index of the track to be deselected. The valid range of the index
     * is 0..total number of tracks - 1. The total number of tracks as well as the type of
     * each individual track can be found by calling {@link #getTrackInfo()} method.
     * @throws IllegalStateException if called in an invalid state.
     * @see MediaPlayer2#getTrackInfo
     */
    @Override
    public void deselectTrack(final int index) {
        addTask(new Task(CALL_COMPLETED_DESELECT_TRACK, false) {
            @Override
            void process() {
                mPlayer.deselectTrack(index);
            }
        });
    }

    /**
     * Register a callback to be invoked when the media source is ready
     * for playback.
     *
     * @param eventCallback the callback that will be run
     * @param executor the executor through which the callback should be invoked
     */
    @Override
    public void setEventCallback(@NonNull Executor executor,
            @NonNull EventCallback eventCallback) {
        if (eventCallback == null) {
            throw new IllegalArgumentException("Illegal null EventCallback");
        }
        if (executor == null) {
            throw new IllegalArgumentException(
                    "Illegal null Executor for the EventCallback");
        }
        synchronized (mLock) {
            mMp2EventCallbackRecord = new Pair(executor, eventCallback);
        }
    }

    /**
     * Clears the {@link EventCallback}.
     */
    @Override
    public void clearEventCallback() {
        synchronized (mLock) {
            mMp2EventCallbackRecord = null;
        }
    }

    // Modular DRM begin

    /**
     * Register a callback to be invoked for configuration of the DRM object before
     * the session is created.
     * The callback will be invoked synchronously during the execution
     * of {@link #prepareDrm(UUID uuid)}.
     *
     * @param listener the callback that will be run
     */
    @Override
    public void setOnDrmConfigHelper(final OnDrmConfigHelper listener) {
        mPlayer.setOnDrmConfigHelper(new MediaPlayer.OnDrmConfigHelper() {
            @Override
            public void onDrmConfig(MediaPlayer mp) {
                MediaPlayerSource src = mPlayer.getSourceForPlayer(mp);
                DataSourceDesc2 dsd = src == null ? null : src.getDSD();
                listener.onDrmConfig(MediaPlayer2Impl.this, dsd);
            }
        });
    }

    /**
     * Register a callback to be invoked when the media source is ready
     * for playback.
     *
     * @param eventCallback the callback that will be run
     * @param executor the executor through which the callback should be invoked
     */
    @Override
    public void setDrmEventCallback(@NonNull Executor executor,
                                    @NonNull DrmEventCallback eventCallback) {
        if (eventCallback == null) {
            throw new IllegalArgumentException("Illegal null EventCallback");
        }
        if (executor == null) {
            throw new IllegalArgumentException(
                    "Illegal null Executor for the EventCallback");
        }
        synchronized (mLock) {
            mDrmEventCallbackRecord = new Pair(executor, eventCallback);
        }
    }

    /**
     * Clears the {@link DrmEventCallback}.
     */
    @Override
    public void clearDrmEventCallback() {
        synchronized (mLock) {
            mDrmEventCallbackRecord = null;
        }
    }


    /**
     * Retrieves the DRM Info associated with the current source
     *
     * @throws IllegalStateException if called before prepare()
     */
    @Override
    public DrmInfo getDrmInfo() {
        MediaPlayer.DrmInfo info = mPlayer.getDrmInfo();
        return info == null ? null : new DrmInfoImpl(info.getPssh(), info.getSupportedSchemes());
    }


    /**
     * Prepares the DRM for the current source
     * <p>
     * If {@link OnDrmConfigHelper} is registered, it will be called during
     * preparation to allow configuration of the DRM properties before opening the
     * DRM session. Note that the callback is called synchronously in the thread that called
     * {@link #prepareDrm}. It should be used only for a series of {@code getDrmPropertyString}
     * and {@code setDrmPropertyString} calls and refrain from any lengthy operation.
     * <p>
     * If the device has not been provisioned before, this call also provisions the device
     * which involves accessing the provisioning server and can take a variable time to
     * complete depending on the network connectivity.
     * prepareDrm() runs in non-blocking mode by launching the provisioning in the background and
     * returning. {@link DrmEventCallback#onDrmPrepared} will be called when provisioning and
     * preparation has finished. The application should check the status code returned with
     * {@link DrmEventCallback#onDrmPrepared} to proceed.
     * <p>
     *
     * @param uuid The UUID of the crypto scheme. If not known beforehand, it can be retrieved
     * from the source through {@#link getDrmInfo} or registering
     * {@link DrmEventCallback#onDrmInfo}.
     */
    @Override
    public void prepareDrm(@NonNull final UUID uuid) {
        addTask(new Task(CALL_COMPLETED_PREPARE_DRM, false) {
            @Override
            void process() {
                int status = PREPARE_DRM_STATUS_SUCCESS;
                try {
                    mPlayer.prepareDrm(uuid);
                } catch (ResourceBusyException e) {
                    status = PREPARE_DRM_STATUS_RESOURCE_BUSY;
                } catch (MediaPlayer.ProvisioningServerErrorException e) {
                    status = PREPARE_DRM_STATUS_PROVISIONING_SERVER_ERROR;
                } catch (MediaPlayer.ProvisioningNetworkErrorException e) {
                    status = PREPARE_DRM_STATUS_PROVISIONING_NETWORK_ERROR;
                } catch (UnsupportedSchemeException e) {
                    status = PREPARE_DRM_STATUS_UNSUPPORTED_SCHEME;
                } catch (Exception e) {
                    status = PREPARE_DRM_STATUS_PREPARATION_ERROR;
                }
                final int prepareDrmStatus = status;
                notifyDrmEvent(new DrmEventNotifier() {
                    @Override
                    public void notify(DrmEventCallback cb) {
                        cb.onDrmPrepared(MediaPlayer2Impl.this, mDSD, prepareDrmStatus);
                    }
                });
            }
        });
    }

    /**
     * Releases the DRM session
     * <p>
     * The player has to have an active DRM session and be in stopped, or prepared
     * state before this call is made.
     * A {@code reset()} call will release the DRM session implicitly.
     *
     * @throws NoDrmSchemeException if there is no active DRM session to release
     */
    @Override
    public void releaseDrm() throws NoDrmSchemeException {
        try {
            mPlayer.releaseDrm();
        } catch (MediaPlayer.NoDrmSchemeException e) {
            throw new NoDrmSchemeException(e.getMessage());
        }
    }


    /**
     * A key request/response exchange occurs between the app and a license server
     * to obtain or release keys used to decrypt encrypted content.
     * <p>
     * getDrmKeyRequest() is used to obtain an opaque key request byte array that is
     * delivered to the license server.  The opaque key request byte array is returned
     * in KeyRequest.data.  The recommended URL to deliver the key request to is
     * returned in KeyRequest.defaultUrl.
     * <p>
     * After the app has received the key request response from the server,
     * it should deliver to the response to the DRM engine plugin using the method
     * {@link #provideDrmKeyResponse}.
     *
     * @param keySetId is the key-set identifier of the offline keys being released when keyType is
     * {@link MediaDrm#KEY_TYPE_RELEASE}. It should be set to null for other key requests, when
     * keyType is {@link MediaDrm#KEY_TYPE_STREAMING} or {@link MediaDrm#KEY_TYPE_OFFLINE}.
     *
     * @param initData is the container-specific initialization data when the keyType is
     * {@link MediaDrm#KEY_TYPE_STREAMING} or {@link MediaDrm#KEY_TYPE_OFFLINE}. Its meaning is
     * interpreted based on the mime type provided in the mimeType parameter.  It could
     * contain, for example, the content ID, key ID or other data obtained from the content
     * metadata that is required in generating the key request.
     * When the keyType is {@link MediaDrm#KEY_TYPE_RELEASE}, it should be set to null.
     *
     * @param mimeType identifies the mime type of the content
     *
     * @param keyType specifies the type of the request. The request may be to acquire
     * keys for streaming, {@link MediaDrm#KEY_TYPE_STREAMING}, or for offline content
     * {@link MediaDrm#KEY_TYPE_OFFLINE}, or to release previously acquired
     * keys ({@link MediaDrm#KEY_TYPE_RELEASE}), which are identified by a keySetId.
     *
     * @param optionalParameters are included in the key request message to
     * allow a client application to provide additional message parameters to the server.
     * This may be {@code null} if no additional parameters are to be sent.
     *
     * @throws NoDrmSchemeException if there is no active DRM session
     */
    @Override
    @NonNull
    public MediaDrm.KeyRequest getDrmKeyRequest(@Nullable byte[] keySetId,
            @Nullable byte[] initData, @Nullable String mimeType, int keyType,
            @Nullable Map<String, String> optionalParameters)
            throws NoDrmSchemeException {
        try {
            return mPlayer.getKeyRequest(keySetId, initData, mimeType, keyType, optionalParameters);
        } catch (MediaPlayer.NoDrmSchemeException e) {
            throw new NoDrmSchemeException(e.getMessage());
        }
    }


    /**
     * A key response is received from the license server by the app, then it is
     * provided to the DRM engine plugin using provideDrmKeyResponse. When the
     * response is for an offline key request, a key-set identifier is returned that
     * can be used to later restore the keys to a new session with the method
     * {@link #restoreDrmKeys}.
     * When the response is for a streaming or release request, null is returned.
     *
     * @param keySetId When the response is for a release request, keySetId identifies
     * the saved key associated with the release request (i.e., the same keySetId
     * passed to the earlier {@link #getDrmKeyRequest} call. It MUST be null when the
     * response is for either streaming or offline key requests.
     *
     * @param response the byte array response from the server
     *
     * @throws NoDrmSchemeException if there is no active DRM session
     * @throws DeniedByServerException if the response indicates that the
     * server rejected the request
     */
    @Override
    public byte[] provideDrmKeyResponse(@Nullable byte[] keySetId, @NonNull byte[] response)
            throws NoDrmSchemeException, DeniedByServerException {
        try {
            return mPlayer.provideKeyResponse(keySetId, response);
        } catch (MediaPlayer.NoDrmSchemeException e) {
            throw new NoDrmSchemeException(e.getMessage());
        }
    }


    /**
     * Restore persisted offline keys into a new session.  keySetId identifies the
     * keys to load, obtained from a prior call to {@link #provideDrmKeyResponse}.
     *
     * @param keySetId identifies the saved key set to restore
     */
    @Override
    public void restoreDrmKeys(@NonNull final byte[] keySetId)
            throws NoDrmSchemeException {
        try {
            mPlayer.restoreKeys(keySetId);
        } catch (MediaPlayer.NoDrmSchemeException e) {
            throw new NoDrmSchemeException(e.getMessage());
        }
    }


    /**
     * Read a DRM engine plugin String property value, given the property name string.
     * <p>
     *

     * @param propertyName the property name
     *
     * Standard fields names are:
     * {@link MediaDrm#PROPERTY_VENDOR}, {@link MediaDrm#PROPERTY_VERSION},
     * {@link MediaDrm#PROPERTY_DESCRIPTION}, {@link MediaDrm#PROPERTY_ALGORITHMS}
     */
    @Override
    @NonNull
    public String getDrmPropertyString(@NonNull String propertyName)
            throws NoDrmSchemeException {
        try {
            return mPlayer.getDrmPropertyString(propertyName);
        } catch (MediaPlayer.NoDrmSchemeException e) {
            throw new NoDrmSchemeException(e.getMessage());
        }
    }


    /**
     * Set a DRM engine plugin String property value.
     * <p>
     *
     * @param propertyName the property name
     * @param value the property value
     *
     * Standard fields names are:
     * {@link MediaDrm#PROPERTY_VENDOR}, {@link MediaDrm#PROPERTY_VERSION},
     * {@link MediaDrm#PROPERTY_DESCRIPTION}, {@link MediaDrm#PROPERTY_ALGORITHMS}
     */
    @Override
    public void setDrmPropertyString(@NonNull String propertyName,
                                     @NonNull String value)
            throws NoDrmSchemeException {
        try {
            mPlayer.setDrmPropertyString(propertyName, value);
        } catch (MediaPlayer.NoDrmSchemeException e) {
            throw new NoDrmSchemeException(e.getMessage());
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void notifyMediaPlayer2Event(final Mp2EventNotifier notifier) {
        final Pair<Executor, EventCallback> record;
        synchronized (mLock) {
            record = mMp2EventCallbackRecord;
        }
        if (record != null) {
            record.first.execute(new Runnable() {
                @Override
                public void run() {
                    notifier.notify(record.second);
                }
            });
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void notifyPlayerEvent(final PlayerEventNotifier notifier) {
        ArrayMap<PlayerEventCallback, Executor> map;
        synchronized (mLock) {
            map = new ArrayMap<>(mPlayerEventCallbackMap);
        }
        final int callbackCount = map.size();
        for (int i = 0; i < callbackCount; i++) {
            final Executor executor = map.valueAt(i);
            final PlayerEventCallback cb = map.keyAt(i);
            try {
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        notifier.notify(cb);
                    }
                });
            } catch (RejectedExecutionException e) {
                // The given executor is shutting down.
                Log.w(TAG, "The given executor is shutting down. Ignoring the player event.");
            }
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void notifyDrmEvent(final DrmEventNotifier notifier) {
        final Pair<Executor, DrmEventCallback> record;
        synchronized (mLock) {
            record = mDrmEventCallbackRecord;
        }
        if (record != null) {
            try {
                record.first.execute(new Runnable() {
                @Override
                public void run() {
                    notifier.notify(record.second);
                }
            });
            } catch (RejectedExecutionException e) {
                // The given executor is shutting down.
                Log.w(TAG, "The given executor is shutting down. Ignoring the player event.");
            }
        }
    }

    private interface Mp2EventNotifier {
        void notify(EventCallback callback);
    }

    private interface PlayerEventNotifier {
        void notify(PlayerEventCallback callback);
    }

    private interface DrmEventNotifier {
        void notify(DrmEventCallback callback);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void setEndPositionTimerIfNeeded(
            final MediaPlayer.OnCompletionListener completionListener,
            final MediaPlayerSource src, MediaTimestamp timedsd) {
        if (src == mPlayer.getFirst()) {
            mEndPositionHandler.removeCallbacksAndMessages(null);
            DataSourceDesc2 dsd = src.getDSD();
            if (dsd.getEndPosition() != DataSourceDesc2.POSITION_UNKNOWN) {
                if (timedsd.getMediaClockRate() > 0.0f) {
                    long nowNs = System.nanoTime();
                    long elapsedTimeUs = (nowNs - timedsd.getAnchorSytemNanoTime()) / 1000;
                    long nowMediaMs = (timedsd.getAnchorMediaTimeUs() + elapsedTimeUs) / 1000;
                    long timeLeftMs = (long) ((dsd.getEndPosition() - nowMediaMs)
                            / timedsd.getMediaClockRate());
                    mEndPositionHandler.postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            if (mPlayer.getFirst() != src) {
                                return;
                            }
                            mPlayer.pause();
                            completionListener.onCompletion(src.getPlayer());
                        }
                    }, timeLeftMs < 0 ? 0 : timeLeftMs);
                }
            }
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void setUpListeners(final MediaPlayerSource src) {
        MediaPlayer p = src.getPlayer();
        final MediaPlayer.OnPreparedListener preparedListener =
                new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {
                        handleDataSourceError(mPlayer.onPrepared(mp));
                        notifyMediaPlayer2Event(new Mp2EventNotifier() {
                            @Override
                            public void notify(EventCallback callback) {
                                MediaPlayer2Impl mp2 = MediaPlayer2Impl.this;
                                DataSourceDesc2 dsd = src.getDSD();
                                callback.onInfo(mp2, dsd, MEDIA_INFO_PREPARED, 0);
                            }
                        });
                        notifyPlayerEvent(new PlayerEventNotifier() {
                            @Override
                            public void notify(PlayerEventCallback cb) {
                                cb.onMediaPrepared(mMediaPlayerConnectorImpl, src.getDSD());
                            }
                        });
                        synchronized (mTaskLock) {
                            if (mCurrentTask != null
                                    && mCurrentTask.mMediaCallType == CALL_COMPLETED_PREPARE
                                    && ObjectsCompat.equals(mCurrentTask.mDSD, src.getDSD())
                                    && mCurrentTask.mNeedToWaitForEventToComplete) {
                                mCurrentTask.sendCompleteNotification(CALL_STATUS_NO_ERROR);
                                mCurrentTask = null;
                                processPendingTask_l();
                            }
                        }
                    }
                };
        p.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mp) {
                if (src.getDSD().getStartPosition() != 0) {
                    src.getPlayer().seekTo((int) src.getDSD().getStartPosition(),
                            MediaPlayer.SEEK_CLOSEST);
                    // PREPARED notification will be sent when seek is done.
                } else {
                    preparedListener.onPrepared(mp);
                }
            }
        });
        p.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(MediaPlayer mp, final int width, final int height) {
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback cb) {
                        cb.onVideoSizeChanged(MediaPlayer2Impl.this, src.getDSD(), width, height);
                    }
                });
            }
        });
        p.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(final MediaPlayer mp, final int what, final int extra) {
                switch (what) {
                    case MediaPlayer.MEDIA_INFO_BUFFERING_START:
                        mPlayer.setBufferingState(
                                mp, MediaPlayerConnector.BUFFERING_STATE_BUFFERING_AND_STARVED);
                        break;
                    case MediaPlayer.MEDIA_INFO_BUFFERING_END:
                        mPlayer.setBufferingState(
                                mp, MediaPlayerConnector.BUFFERING_STATE_BUFFERING_AND_PLAYABLE);
                        break;
                }
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback cb) {
                        if (what == MediaPlayer.MEDIA_INFO_STARTED_AS_NEXT) {
                            mPlayer.onStartedAsNext(mp);
                            return;
                        }
                        int w = sInfoEventMap.getOrDefault(what, MEDIA_INFO_UNKNOWN);
                        cb.onInfo(MediaPlayer2Impl.this, src.getDSD(), w, extra);
                    }
                });
                return true;
            }
        });
        final MediaPlayer.OnCompletionListener completionListener =
                new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        handleDataSourceError(mPlayer.onCompletion(mp));
                    }
                };
        p.setOnCompletionListener(completionListener);
        p.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mp, final int what, final int extra) {
                mPlayer.onError(mp);
                synchronized (mTaskLock) {
                    if (mCurrentTask != null
                            && mCurrentTask.mNeedToWaitForEventToComplete) {
                        mCurrentTask.sendCompleteNotification(CALL_STATUS_ERROR_UNKNOWN);
                        mCurrentTask = null;
                        processPendingTask_l();
                    }
                }
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback cb) {
                        int w = sErrorEventMap.getOrDefault(what, MEDIA_ERROR_UNKNOWN);
                        cb.onError(MediaPlayer2Impl.this, src.getDSD(), w, extra);
                    }
                });
                return true;
            }
        });
        p.setOnSeekCompleteListener(new MediaPlayer.OnSeekCompleteListener() {
            @Override
            public void onSeekComplete(MediaPlayer mp) {
                if (src.mMp2State == PLAYER_STATE_IDLE
                        && src.getDSD().getStartPosition() != 0) {
                    // This seek request was for handling start position. Notify client that it's
                    // ready to start playback.
                    preparedListener.onPrepared(mp);
                    return;
                }
                synchronized (mTaskLock) {
                    if (mCurrentTask != null
                            && mCurrentTask.mMediaCallType == CALL_COMPLETED_SEEK_TO
                            && mCurrentTask.mNeedToWaitForEventToComplete) {
                        mCurrentTask.sendCompleteNotification(CALL_STATUS_NO_ERROR);
                        mCurrentTask = null;
                        processPendingTask_l();
                    }
                }
                final long seekPos = getCurrentPosition();
                notifyPlayerEvent(new PlayerEventNotifier() {
                    @Override
                    public void notify(PlayerEventCallback cb) {
                        // TODO: The actual seeked position might be different from the
                        // requested position. Clarify which one is expected here.
                        cb.onSeekCompleted(mMediaPlayerConnectorImpl, seekPos);
                    }
                });
            }
        });
        p.setOnTimedMetaDataAvailableListener(
                new MediaPlayer.OnTimedMetaDataAvailableListener() {
                    @Override
                    public void onTimedMetaDataAvailable(MediaPlayer mp, final TimedMetaData data) {
                        notifyMediaPlayer2Event(new Mp2EventNotifier() {
                            @Override
                            public void notify(EventCallback cb) {
                                cb.onTimedMetaDataAvailable(MediaPlayer2Impl.this, src.getDSD(),
                                        new TimedMetaData2(data));
                            }
                        });
                    }
                });
        p.setOnBufferingUpdateListener(new MediaPlayer.OnBufferingUpdateListener() {
            @Override
            public void onBufferingUpdate(MediaPlayer mp, final int percent) {
                if (percent >= 100) {
                    mPlayer.setBufferingState(
                            mp, MediaPlayerConnector.BUFFERING_STATE_BUFFERING_COMPLETE);
                }
                src.mBufferedPercentage.set(percent);
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback cb) {
                        cb.onInfo(MediaPlayer2Impl.this, src.getDSD(),
                                MEDIA_INFO_BUFFERING_UPDATE, percent);
                    }
                });
            }
        });
        p.setOnMediaTimeDiscontinuityListener(
                new MediaPlayer.OnMediaTimeDiscontinuityListener() {
                    @Override
                    public void onMediaTimeDiscontinuity(
                            MediaPlayer mp, final MediaTimestamp timestamp) {
                        notifyMediaPlayer2Event(new Mp2EventNotifier() {
                            @Override
                            public void notify(EventCallback cb) {
                                cb.onMediaTimeDiscontinuity(
                                        MediaPlayer2Impl.this, src.getDSD(),
                                        new MediaTimestamp2(timestamp));
                            }
                        });
                        setEndPositionTimerIfNeeded(completionListener, src, timestamp);
                    }
                });
        p.setOnSubtitleDataListener(new MediaPlayer.OnSubtitleDataListener() {
            @Override
            public  void onSubtitleData(MediaPlayer mp, final SubtitleData data) {
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback cb) {
                        cb.onSubtitleData(
                                MediaPlayer2Impl.this, src.getDSD(), new SubtitleData2(data));
                    }
                });
            }
        });
        p.setOnDrmInfoListener(new MediaPlayer.OnDrmInfoListener() {
            @Override
            public void onDrmInfo(MediaPlayer mp, final MediaPlayer.DrmInfo drmInfo) {
                notifyDrmEvent(new DrmEventNotifier() {
                    @Override
                    public void notify(DrmEventCallback cb) {
                        cb.onDrmInfo(MediaPlayer2Impl.this, src.getDSD(),
                                new DrmInfoImpl(drmInfo.getPssh(), drmInfo.getSupportedSchemes()));
                    }
                });
            }
        });
    }

    /**
     * Encapsulates the DRM properties of the source.
     */
    public static final class DrmInfoImpl extends DrmInfo {
        private Map<UUID, byte[]> mMapPssh;
        private UUID[] mSupportedSchemes;

        /**
         * Returns the PSSH info of the data source for each supported DRM scheme.
         */
        @Override
        public Map<UUID, byte[]> getPssh() {
            return mMapPssh;
        }

        /**
         * Returns the intersection of the data source and the device DRM schemes.
         * It effectively identifies the subset of the source's DRM schemes which
         * are supported by the device too.
         */
        @Override
        public List<UUID> getSupportedSchemes() {
            return Arrays.asList(mSupportedSchemes);
        }

        DrmInfoImpl(Map<UUID, byte[]> pssh, UUID[] supportedSchemes) {
            mMapPssh = pssh;
            mSupportedSchemes = supportedSchemes;
        }

        private DrmInfoImpl(Parcel parcel) {
            Log.v(TAG, "DrmInfoImpl(" + parcel + ") size " + parcel.dataSize());

            int psshsize = parcel.readInt();
            byte[] pssh = new byte[psshsize];
            parcel.readByteArray(pssh);

            Log.v(TAG, "DrmInfoImpl() PSSH: " + arrToHex(pssh));
            mMapPssh = parsePSSH(pssh, psshsize);
            Log.v(TAG, "DrmInfoImpl() PSSH: " + mMapPssh);

            int supportedDRMsCount = parcel.readInt();
            mSupportedSchemes = new UUID[supportedDRMsCount];
            for (int i = 0; i < supportedDRMsCount; i++) {
                byte[] uuid = new byte[16];
                parcel.readByteArray(uuid);

                mSupportedSchemes[i] = bytesToUUID(uuid);

                Log.v(TAG, "DrmInfoImpl() supportedScheme[" + i + "]: "
                        + mSupportedSchemes[i]);
            }

            Log.v(TAG, "DrmInfoImpl() Parcel psshsize: " + psshsize
                    + " supportedDRMsCount: " + supportedDRMsCount);
        }

        private DrmInfoImpl makeCopy() {
            return new DrmInfoImpl(this.mMapPssh, this.mSupportedSchemes);
        }

        private String arrToHex(byte[] bytes) {
            String out = "0x";
            for (int i = 0; i < bytes.length; i++) {
                out += String.format("%02x", bytes[i]);
            }

            return out;
        }

        private UUID bytesToUUID(byte[] uuid) {
            long msb = 0, lsb = 0;
            for (int i = 0; i < 8; i++) {
                msb |= (((long) uuid[i] & 0xff) << (8 * (7 - i)));
                lsb |= (((long) uuid[i + 8] & 0xff) << (8 * (7 - i)));
            }

            return new UUID(msb, lsb);
        }

        private Map<UUID, byte[]> parsePSSH(byte[] pssh, int psshsize) {
            Map<UUID, byte[]> result = new HashMap<UUID, byte[]>();

            final int uuidSize = 16;
            final int dataLenSize = 4;

            int len = psshsize;
            int numentries = 0;
            int i = 0;

            while (len > 0) {
                if (len < uuidSize) {
                    Log.w(TAG, String.format("parsePSSH: len is too short to parse "
                            + "UUID: (%d < 16) pssh: %d", len, psshsize));
                    return null;
                }

                byte[] subset = Arrays.copyOfRange(pssh, i, i + uuidSize);
                UUID uuid = bytesToUUID(subset);
                i += uuidSize;
                len -= uuidSize;

                // get data length
                if (len < 4) {
                    Log.w(TAG, String.format("parsePSSH: len is too short to parse "
                            + "datalen: (%d < 4) pssh: %d", len, psshsize));
                    return null;
                }

                subset = Arrays.copyOfRange(pssh, i, i + dataLenSize);
                int datalen = (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN)
                        ? ((subset[3] & 0xff) << 24) | ((subset[2] & 0xff) << 16)
                        | ((subset[1] & 0xff) << 8) | (subset[0] & 0xff)
                        : ((subset[0] & 0xff) << 24) | ((subset[1] & 0xff) << 16)
                                | ((subset[2] & 0xff) << 8) | (subset[3] & 0xff);
                i += dataLenSize;
                len -= dataLenSize;

                if (len < datalen) {
                    Log.w(TAG, String.format("parsePSSH: len is too short to parse "
                            + "data: (%d < %d) pssh: %d", len, datalen, psshsize));
                    return null;
                }

                byte[] data = Arrays.copyOfRange(pssh, i, i + datalen);

                // skip the data
                i += datalen;
                len -= datalen;

                Log.v(TAG, String.format("parsePSSH[%d]: <%s, %s> pssh: %d",
                        numentries, uuid, arrToHex(data), psshsize));
                numentries++;
                result.put(uuid, data);
            }

            return result;
        }

    };  // DrmInfoImpl

    private abstract class Task implements Runnable {
        final int mMediaCallType;
        final boolean mNeedToWaitForEventToComplete;
        DataSourceDesc2 mDSD;
        boolean mSkip;

        Task(int mediaCallType, boolean needToWaitForEventToComplete) {
            mMediaCallType = mediaCallType;
            mNeedToWaitForEventToComplete = needToWaitForEventToComplete;
        }

        abstract void process() throws IOException, NoDrmSchemeException;

        @Override
        public void run() {
            int status = CALL_STATUS_NO_ERROR;
            boolean skip;
            synchronized (mTaskLock) {
                skip = mSkip;
            }
            if (!skip) {
                try {
                    if (mMediaCallType != CALL_COMPLETED_NOTIFY_WHEN_COMMAND_LABEL_REACHED
                            && getState() == PLAYER_STATE_ERROR) {
                        status = CALL_STATUS_INVALID_OPERATION;
                    } else {
                        process();
                    }
                } catch (IllegalStateException e) {
                    status = CALL_STATUS_INVALID_OPERATION;
                } catch (IllegalArgumentException e) {
                    status = CALL_STATUS_BAD_VALUE;
                } catch (SecurityException e) {
                    status = CALL_STATUS_PERMISSION_DENIED;
                } catch (IOException e) {
                    status = CALL_STATUS_ERROR_IO;
                } catch (Exception e) {
                    status = CALL_STATUS_ERROR_UNKNOWN;
                }
            } else {
                status = CALL_STATUS_SKIPPED;
            }

            mDSD = getCurrentDataSource();

            if (!mNeedToWaitForEventToComplete || status != CALL_STATUS_NO_ERROR || skip) {

                sendCompleteNotification(status);

                synchronized (mTaskLock) {
                    mCurrentTask = null;
                    processPendingTask_l();
                }
            }
        }

        void sendCompleteNotification(final int status) {
            if (mMediaCallType >= SEPARATE_CALL_COMPLETE_CALLBACK_START) {
                // These methods have a separate call complete callback and it should be already
                // called within {@link #processs()}.
                return;
            }
            notifyMediaPlayer2Event(new Mp2EventNotifier() {
                @Override
                public void notify(EventCallback cb) {
                    cb.onCallCompleted(
                            MediaPlayer2Impl.this, mDSD, mMediaCallType, status);
                }
            });
        }
    };

    private static class DataSourceError {
        final DataSourceDesc2 mDSD;
        final int mWhat;

        final int mExtra;
        DataSourceError(DataSourceDesc2 dsd, int what, int extra) {
            mDSD = dsd;
            mWhat = what;
            mExtra = extra;
        }

    }

    private class MediaPlayerSource {

        volatile DataSourceDesc2 mDSD;
        MediaPlayer mPlayer;
        final AtomicInteger mBufferedPercentage = new AtomicInteger(0);
        int mSourceState = SOURCE_STATE_INIT;
        @MediaPlayer2State int mMp2State = PLAYER_STATE_IDLE;
        @BuffState int mBufferingState = MediaPlayerConnector.BUFFERING_STATE_UNKNOWN;
        @PlayerState int mPlayerState = MediaPlayerConnector.PLAYER_STATE_IDLE;
        boolean mPlayPending;
        boolean mSetAsNextPlayer;

        MediaPlayerSource(final DataSourceDesc2 dsd) {
            mDSD = dsd;
            setUpListeners(this);
        }

        DataSourceDesc2 getDSD() {
            return mDSD;
        }

        synchronized MediaPlayer getPlayer() {
            if (mPlayer == null) {
                mPlayer = new MediaPlayer();
            }
            return mPlayer;
        }
    }

    private class MediaPlayerSourceQueue {
        List<MediaPlayerSource> mQueue = new ArrayList<>();
        Float mVolume = 1.0f;
        Surface mSurface;
        Integer mAuxEffect;
        Float mAuxEffectSendLevel;
        AudioAttributesCompat mAudioAttributes;
        Integer mAudioSessionId;
        SyncParams mSyncParams;
        PlaybackParams mPlaybackParams = DEFAULT_PLAYBACK_PARAMS;
        PlaybackParams mPlaybackParamsToSetWhenStarting;
        boolean mLooping;

        MediaPlayerSourceQueue() {
            mQueue.add(new MediaPlayerSource(null));
        }

        synchronized MediaPlayer getCurrentPlayer() {
            return mQueue.get(0).getPlayer();
        }

        synchronized MediaPlayerSource getFirst() {
            return mQueue.get(0);
        }

        synchronized void setFirst(DataSourceDesc2 dsd) throws IOException {
            if (mQueue.isEmpty()) {
                mQueue.add(0, new MediaPlayerSource(dsd));
            } else {
                mQueue.get(0).mDSD = dsd;
                setUpListeners(mQueue.get(0));
            }
            handleDataSource(mQueue.get(0));
            notifyPlayerEvent(new PlayerEventNotifier() {
                @Override
                public void notify(PlayerEventCallback cb) {
                    cb.onCurrentDataSourceChanged(mMediaPlayerConnectorImpl, mQueue.get(0).mDSD);
                }
            });
        }

        synchronized DataSourceError setNext(DataSourceDesc2 dsd) {
            if (mQueue.isEmpty() || getFirst().getDSD() == null) {
                throw new IllegalStateException();
            }
            // Clear next data sources if any.
            while (mQueue.size() >= 2) {
                MediaPlayerSource src = mQueue.remove(1);
                src.mPlayer.release();
            }
            MediaPlayerSource src = new MediaPlayerSource(dsd);
            mQueue.add(1, src);
            return prepareAt(1);
        }

        synchronized DataSourceError setNextMultiple(List<DataSourceDesc2> descs) {
            if (mQueue.isEmpty() || getFirst().getDSD() == null) {
                throw new IllegalStateException();
            }
            // Clear next data sources if any.
            while (mQueue.size() >= 2) {
                MediaPlayerSource src = mQueue.remove(1);
                src.mPlayer.release();
            }
            List<MediaPlayerSource> sources = new ArrayList<>();
            for (DataSourceDesc2 dsd: descs) {
                sources.add(new MediaPlayerSource(dsd));
            }
            mQueue.addAll(1, sources);
            return prepareAt(1);
        }

        synchronized void play() {
            final MediaPlayerSource src = mQueue.get(0);
            if (src.mSourceState == SOURCE_STATE_PREPARED) {
                if (mPlaybackParamsToSetWhenStarting != null) {
                    src.getPlayer().setPlaybackParams(mPlaybackParamsToSetWhenStarting);
                    mPlaybackParamsToSetWhenStarting = null;
                }
                src.getPlayer().start();
                setMp2State(src.getPlayer(), PLAYER_STATE_PLAYING);
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback callback) {
                        callback.onInfo(MediaPlayer2Impl.this, src.getDSD(),
                                MEDIA_INFO_DATA_SOURCE_START, 0);
                    }
                });
            } else {
                throw new IllegalStateException();
            }
        }

        synchronized void prepare() {
            getCurrentPlayer().prepareAsync();
        }

        synchronized void release() {
            getCurrentPlayer().release();
        }

        synchronized void prepareAsync() {
            MediaPlayer mp = getCurrentPlayer();
            mp.prepareAsync();
            setBufferingState(mp, MediaPlayerConnector.BUFFERING_STATE_BUFFERING_AND_STARVED);
        }

        synchronized void pause() {
            MediaPlayerSource current = getFirst();
            if (current.mMp2State == PLAYER_STATE_PREPARED) {
                // MediaPlayer1 does not allow pause() in the prepared state. To workaround, call
                // start() here right before calling pause().
                current.mPlayer.start();
            }
            current.mPlayer.pause();
            setMp2State(current.mPlayer, PLAYER_STATE_PAUSED);
        }

        synchronized long getCurrentPosition() {
            // Throws an ISE here rather than relying on MediaPlayer1 implementation which returns
            // a garbage value in the IDLE state.
            if (getFirst().mMp2State == PLAYER_STATE_IDLE) {
                throw new IllegalStateException();
            }
            return getCurrentPlayer().getCurrentPosition();
        }

        synchronized long getDuration() {
            // Throws an ISE here rather than relying on MediaPlayer1 implementation which returns
            // a garbage value in the IDLE state.
            if (getFirst().mMp2State == PLAYER_STATE_IDLE) {
                throw new IllegalStateException();
            }
            return getCurrentPlayer().getDuration();
        }

        synchronized long getBufferedPosition() {
            // Throws an ISE here rather than relying on MediaPlayer1 implementation which returns
            // a garbage value in the IDLE state.
            if (getFirst().mMp2State == PLAYER_STATE_IDLE) {
                throw new IllegalStateException();
            }
            MediaPlayerSource src = mQueue.get(0);
            return (long) src.getPlayer().getDuration() * src.mBufferedPercentage.get() / 100;
        }

        synchronized void setAudioAttributes(AudioAttributesCompat attributes) {
            mAudioAttributes = attributes;
            AudioAttributes attr = mAudioAttributes == null
                    ? null : (AudioAttributes) mAudioAttributes.unwrap();
            getCurrentPlayer().setAudioAttributes(attr);
        }

        synchronized AudioAttributesCompat getAudioAttributes() {
            return mAudioAttributes;
        }

        synchronized DataSourceError onPrepared(MediaPlayer mp) {
            for (int i = 0; i < mQueue.size(); i++) {
                MediaPlayerSource src = mQueue.get(i);
                if (mp == src.getPlayer()) {
                    if (i == 0) {
                        if (src.mPlayPending) {
                            src.mPlayPending = false;
                            src.getPlayer().start();
                            setMp2State(src.getPlayer(), PLAYER_STATE_PLAYING);
                        } else {
                            setMp2State(src.getPlayer(), PLAYER_STATE_PREPARED);
                        }
                    }
                    src.mSourceState = SOURCE_STATE_PREPARED;
                    setBufferingState(src.getPlayer(),
                            MediaPlayerConnector.BUFFERING_STATE_BUFFERING_AND_PLAYABLE);
                    if (i == 1) {
                        boolean hasVideo = false;
                        for (MediaPlayer.TrackInfo info : mp.getTrackInfo()) {
                            if (info.getTrackType()
                                    == MediaPlayer.TrackInfo.MEDIA_TRACK_TYPE_VIDEO) {
                                hasVideo = true;
                                break;
                            }
                        }
                        // setNextMediaPlayer() does not pass surface to next player. Use it only
                        // for the audio-only data source.
                        if (!hasVideo) {
                            getCurrentPlayer().setNextMediaPlayer(src.mPlayer);
                            src.mSetAsNextPlayer = true;
                        }
                    }
                    return prepareAt(i + 1);
                }
            }
            return null;
        }

        synchronized DataSourceError onCompletion(MediaPlayer mp) {
            final MediaPlayerSource src = getFirst();
            if (mLooping && mp == src.mPlayer) {
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback cb) {
                        MediaPlayer2Impl mp2 = MediaPlayer2Impl.this;
                        DataSourceDesc2 dsd = src.getDSD();
                        cb.onInfo(mp2, dsd, MEDIA_INFO_DATA_SOURCE_REPEAT, 0);
                    }
                });
                src.mPlayer.seekTo((int) src.getDSD().getStartPosition());
                src.mPlayer.start();
                setMp2State(mp, PLAYER_STATE_PLAYING);
                return null;
            }
            if (mp == src.mPlayer) {
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback cb) {
                        MediaPlayer2Impl mp2 = MediaPlayer2Impl.this;
                        DataSourceDesc2 dsd = src.getDSD();
                        cb.onInfo(mp2, dsd, MEDIA_INFO_DATA_SOURCE_END, 0);
                    }
                });
            } else {
                Log.w(TAG, "Playback complete event from next player. Ignoring.");
            }
            if (!mQueue.isEmpty() && mp == src.mPlayer) {
                if (mQueue.size() == 1) {
                    setMp2State(mp, PLAYER_STATE_PAUSED);

                    final DataSourceDesc2 dsd = mQueue.get(0).getDSD();
                    notifyPlayerEvent(new PlayerEventNotifier() {
                        @Override
                        public void notify(PlayerEventCallback cb) {
                            cb.onCurrentDataSourceChanged(mMediaPlayerConnectorImpl, null);
                        }
                    });
                    notifyMediaPlayer2Event(new Mp2EventNotifier() {
                        @Override
                        public void notify(EventCallback callback) {
                            callback.onInfo(MediaPlayer2Impl.this, dsd,
                                    MEDIA_INFO_DATA_SOURCE_LIST_END, 0);
                        }
                    });
                    return null;
                } else {
                    if (mQueue.get(1).mSetAsNextPlayer) {
                        // Transition to next player will be handled by MEDIA_INFO_STARTED_AS_NEXT
                        // event later.
                        return null;
                    }
                    moveToNext();
                    return playCurrent();
                }
            } else {
                Log.w(TAG, "Invalid playback complete callback from " + mp.toString());
                return null;
            }
        }

        synchronized void onStartedAsNext(MediaPlayer mp) {
            if (mQueue.size() >= 2 && mQueue.get(1).mPlayer == mp) {
                moveToNext();
                final MediaPlayerSource src = getFirst();
                setMp2State(src.getPlayer(), PLAYER_STATE_PLAYING);
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback callback) {
                        callback.onInfo(MediaPlayer2Impl.this, src.getDSD(),
                                MEDIA_INFO_DATA_SOURCE_START, 0);
                    }
                });
                prepareAt(1);
                applyProperties();
            }
        }

        synchronized void moveToNext() {
            final MediaPlayerSource src1 = mQueue.remove(0);
            src1.getPlayer().release();
            if (mQueue.isEmpty()) {
                throw new IllegalStateException("player/source queue emptied");
            }
            final MediaPlayerSource src2 = mQueue.get(0);
            if (src1.mPlayerState != src2.mPlayerState) {
                notifyPlayerEvent(new PlayerEventNotifier() {
                    @Override
                    public void notify(PlayerEventCallback cb) {
                        cb.onPlayerStateChanged(mMediaPlayerConnectorImpl, src2.mPlayerState);
                    }
                });
            }
            notifyPlayerEvent(new PlayerEventNotifier() {
                @Override
                public void notify(PlayerEventCallback cb) {
                    cb.onCurrentDataSourceChanged(mMediaPlayerConnectorImpl, src2.mDSD);
                }
            });
        }

        synchronized DataSourceError playCurrent() {
            DataSourceError err = null;
            applyProperties();

            final MediaPlayerSource src = mQueue.get(0);
            if (src.mSourceState == SOURCE_STATE_PREPARED) {
                // start next source only when it's in prepared state.
                src.getPlayer().start();
                setMp2State(src.getPlayer(), PLAYER_STATE_PLAYING);
                notifyMediaPlayer2Event(new Mp2EventNotifier() {
                    @Override
                    public void notify(EventCallback callback) {
                        callback.onInfo(MediaPlayer2Impl.this, src.getDSD(),
                                MEDIA_INFO_DATA_SOURCE_START, 0);
                    }
                });
                prepareAt(1);

            } else {
                if (src.mSourceState == SOURCE_STATE_INIT) {
                    err = prepareAt(0);
                }
                src.mPlayPending = true;
            }
            return err;
        }

        synchronized void applyProperties() {
            final MediaPlayerSource src = mQueue.get(0);
            if (mSurface != null) {
                src.getPlayer().setSurface(mSurface);
            }
            if (mVolume != null) {
                src.getPlayer().setVolume(mVolume, mVolume);
            }
            if (mAudioAttributes != null) {
                src.getPlayer().setAudioAttributes((AudioAttributes) mAudioAttributes.unwrap());
            }
            if (mAuxEffect != null) {
                src.getPlayer().attachAuxEffect(mAuxEffect);
            }
            if (mAuxEffectSendLevel != null) {
                src.getPlayer().setAuxEffectSendLevel(mAuxEffectSendLevel);
            }
            if (mSyncParams != null) {
                src.getPlayer().setSyncParams(mSyncParams);
            }
            if (mPlaybackParams != DEFAULT_PLAYBACK_PARAMS) {
                src.getPlayer().setPlaybackParams(mPlaybackParams);
            }
        }

        synchronized void onError(MediaPlayer mp) {
            setMp2State(mp, PLAYER_STATE_ERROR);
            setBufferingState(mp, MediaPlayerConnector.BUFFERING_STATE_UNKNOWN);
        }

        synchronized DataSourceError prepareAt(int n) {
            if (n >= Math.min(2, mQueue.size())
                    || mQueue.get(n).mSourceState != SOURCE_STATE_INIT
                    || (n != 0 && getPlayerState() == MediaPlayerConnector.PLAYER_STATE_IDLE)) {
                // There is no next source or it's in preparing or prepared state.
                return null;
            }

            MediaPlayerSource src = mQueue.get(n);
            try {
                // Apply audio session ID before calling setDataSource().
                if (mAudioSessionId != null) {
                    src.getPlayer().setAudioSessionId(mAudioSessionId);
                }
                src.mSourceState = SOURCE_STATE_PREPARING;
                handleDataSource(src);
                src.getPlayer().prepareAsync();
                return null;
            } catch (Exception e) {
                DataSourceDesc2 dsd = src.getDSD();
                setMp2State(src.getPlayer(), PLAYER_STATE_ERROR);
                return new DataSourceError(dsd, MEDIA_ERROR_UNKNOWN, MEDIA_ERROR_UNSUPPORTED);
            }

        }

        synchronized void skipToNext() {
            if (mQueue.size() <= 1) {
                throw new IllegalStateException("No next source available");
            }
            final MediaPlayerSource src = mQueue.get(0);
            moveToNext();
            if (src.mPlayerState == MediaPlayerConnector.PLAYER_STATE_PLAYING || src.mPlayPending) {
                playCurrent();
            }
        }

        synchronized void setLooping(boolean loop) {
            mLooping = loop;
        }

        synchronized void setPlaybackParams(final PlaybackParams params) {
            if (params == null || params.getSpeed() == 0f) {
                throw new IllegalArgumentException();
            }
            PlaybackParams current = getPlaybackParams();
            MediaPlayerSource firstPlayer = mPlayer.getFirst();
            if (firstPlayer.mMp2State != PLAYER_STATE_PLAYING) {
                // MediaPlayer1 may start the playback on setPlaybackParams. Store the value here
                // so that it can be applied later when starting the playback.
                mPlaybackParamsToSetWhenStarting = params;
            } else {
                firstPlayer.mPlayer.setPlaybackParams(params);
                mPlaybackParamsToSetWhenStarting = null;
            }

            if (current != null && current.getSpeed() != params.getSpeed()) {
                notifyPlayerEvent(new PlayerEventNotifier() {
                    @Override
                    public void notify(PlayerEventCallback cb) {
                        cb.onPlaybackSpeedChanged(mMediaPlayerConnectorImpl, params.getSpeed());
                    }
                });
            }
            mPlaybackParams = params;
        }

        synchronized float getVolume() {
            return mVolume;
        }

        synchronized void setVolume(float volume) {
            mVolume = volume;
            getCurrentPlayer().setVolume(volume, volume);
        }

        synchronized void setSurface(Surface surface) {
            mSurface = surface;
            getCurrentPlayer().setSurface(surface);
        }

        synchronized int getVideoWidth() {
            return getCurrentPlayer().getVideoWidth();
        }

        synchronized int getVideoHeight() {
            return getCurrentPlayer().getVideoHeight();
        }

        synchronized PersistableBundle getMetrics() {
            return getCurrentPlayer().getMetrics();
        }

        synchronized PlaybackParams getPlaybackParams() {
            // PlaybackParams is mutable. Make a copy of mPlaybackParams and return.
            Parcel parcel = Parcel.obtain();
            mPlaybackParams.writeToParcel(parcel, 0);
            parcel.setDataPosition(0);
            PlaybackParams ret = PlaybackParams.CREATOR.createFromParcel(parcel);
            parcel.recycle();
            return ret;
        }

        synchronized void setSyncParams(SyncParams params) {
            getCurrentPlayer().setSyncParams(params);
            mSyncParams = params;
        }

        synchronized SyncParams getSyncParams() {
            return getCurrentPlayer().getSyncParams();
        }

        synchronized void seekTo(long msec, int mode) {
            getCurrentPlayer().seekTo(msec, mode);
        }

        synchronized void reset() {
            MediaPlayerSource src = mQueue.get(0);
            src.getPlayer().reset();
            src.mBufferedPercentage.set(0);
            mVolume = 1.0f;
            mSurface = null;
            mAuxEffect = null;
            mAuxEffectSendLevel = null;
            mAudioAttributes = null;
            mAudioSessionId = null;
            mSyncParams = null;
            mPlaybackParams = DEFAULT_PLAYBACK_PARAMS;
            mPlaybackParamsToSetWhenStarting = null;
            mLooping = false;

            setMp2State(src.getPlayer(), PLAYER_STATE_IDLE);
            setBufferingState(src.getPlayer(), MediaPlayerConnector.BUFFERING_STATE_UNKNOWN);
        }

        synchronized MediaTimestamp2 getTimestamp() {
            MediaTimestamp t = getCurrentPlayer().getTimestamp();
            return (t == null) ? null : new MediaTimestamp2(t);
        }

        synchronized void setAudioSessionId(int sessionId) {
            getCurrentPlayer().setAudioSessionId(sessionId);
            mAudioSessionId = Integer.valueOf(sessionId);
        }

        synchronized int getAudioSessionId() {
            return getCurrentPlayer().getAudioSessionId();
        }

        synchronized void attachAuxEffect(int effectId) {
            getCurrentPlayer().attachAuxEffect(effectId);
            mAuxEffect = Integer.valueOf(effectId);
        }

        synchronized void setAuxEffectSendLevel(float level) {
            getCurrentPlayer().setAuxEffectSendLevel(level);
            mAuxEffectSendLevel = Float.valueOf(level);
        }

        synchronized MediaPlayer.TrackInfo[] getTrackInfo() {
            return getCurrentPlayer().getTrackInfo();
        }

        synchronized int getSelectedTrack(int trackType) {
            return getCurrentPlayer().getSelectedTrack(trackType);
        }

        synchronized void selectTrack(int index) {
            getCurrentPlayer().selectTrack(index);
        }

        synchronized void deselectTrack(int index) {
            getCurrentPlayer().deselectTrack(index);
        }

        synchronized MediaPlayer.DrmInfo getDrmInfo() {
            return getCurrentPlayer().getDrmInfo();
        }

        synchronized void prepareDrm(UUID uuid)
                throws ResourceBusyException, MediaPlayer.ProvisioningServerErrorException,
                MediaPlayer.ProvisioningNetworkErrorException, UnsupportedSchemeException {
            getCurrentPlayer().prepareDrm(uuid);
        }

        synchronized void releaseDrm() throws MediaPlayer.NoDrmSchemeException {
            getCurrentPlayer().stop();
            getCurrentPlayer().releaseDrm();
        }

        synchronized byte[] provideKeyResponse(byte[] keySetId, byte[] response)
                throws DeniedByServerException, MediaPlayer.NoDrmSchemeException {
            return getCurrentPlayer().provideKeyResponse(keySetId, response);
        }

        synchronized void restoreKeys(byte[] keySetId) throws MediaPlayer.NoDrmSchemeException {
            getCurrentPlayer().restoreKeys(keySetId);
        }

        synchronized String getDrmPropertyString(String propertyName)
                throws MediaPlayer.NoDrmSchemeException {
            return getCurrentPlayer().getDrmPropertyString(propertyName);
        }

        synchronized void setDrmPropertyString(String propertyName, String value)
                throws MediaPlayer.NoDrmSchemeException {
            getCurrentPlayer().setDrmPropertyString(propertyName, value);
        }

        synchronized void setOnDrmConfigHelper(MediaPlayer.OnDrmConfigHelper onDrmConfigHelper) {
            getCurrentPlayer().setOnDrmConfigHelper(onDrmConfigHelper);
        }

        synchronized MediaDrm.KeyRequest getKeyRequest(byte[] keySetId, byte[] initData,
                String mimeType,
                int keyType, Map<String, String> optionalParameters)
                throws MediaPlayer.NoDrmSchemeException {
            return getCurrentPlayer().getKeyRequest(keySetId, initData, mimeType, keyType,
                    optionalParameters);
        }

        synchronized void setMp2State(MediaPlayer mp, @MediaPlayer2State int mp2State) {
            for (final MediaPlayerSource src: mQueue) {
                if (src.getPlayer() != mp) {
                    continue;
                }
                if (src.mMp2State == mp2State) {
                    return;
                }
                src.mMp2State = mp2State;

                final int playerState = sStateMap.get(mp2State);
                if (src.mPlayerState == playerState) {
                    return;
                }
                src.mPlayerState = playerState;
                notifyPlayerEvent(new PlayerEventNotifier() {
                    @Override
                    public void notify(PlayerEventCallback cb) {
                        cb.onPlayerStateChanged(mMediaPlayerConnectorImpl, playerState);
                    }
                });
                return;
            }
        }

        synchronized void setBufferingState(MediaPlayer mp, @BuffState final int state) {
            for (final MediaPlayerSource src: mQueue) {
                if (src.getPlayer() != mp) {
                    continue;
                }
                if (src.mBufferingState == state) {
                    return;
                }
                src.mBufferingState = state;
                notifyPlayerEvent(new PlayerEventNotifier() {
                    @Override
                    public void notify(PlayerEventCallback cb) {
                        DataSourceDesc2 dsd = src.getDSD();
                        cb.onBufferingStateChanged(mMediaPlayerConnectorImpl, dsd, state);
                    }
                });
                return;
            }
        }

        synchronized @MediaPlayer2State int getMediaPlayer2State() {
            return mQueue.get(0).mMp2State;
        }

        synchronized @BuffState int getBufferingState() {
            return mQueue.get(0).mBufferingState;
        }

        synchronized @PlayerState int getPlayerState() {
            return mQueue.get(0).mPlayerState;
        }

        synchronized MediaPlayerSource getSourceForPlayer(MediaPlayer mp) {
            for (MediaPlayerSource src: mQueue) {
                if (src.getPlayer() == mp) {
                    return src;
                }
            }
            return null;
        }
    }

    private class MediaPlayerConnectorImpl extends MediaPlayerConnector {
        MediaPlayerConnectorImpl() {
        }

        @Override
        public void play() {
            MediaPlayer2Impl.this.play();
        }

        @Override
        public void prepare() {
            MediaPlayer2Impl.this.prepare();
        }

        @Override
        public void pause() {
            MediaPlayer2Impl.this.pause();
        }

        @Override
        public void reset() {
            MediaPlayer2Impl.this.reset();
        }

        @Override
        public void skipToNext() {
            MediaPlayer2Impl.this.skipToNext();
        }

        @Override
        public void seekTo(long pos) {
            MediaPlayer2Impl.this.seekTo(pos);
        }

        @Override
        public long getCurrentPosition() {
            try {
                return MediaPlayer2Impl.this.getCurrentPosition();
            } catch (IllegalStateException e) {
                return MediaPlayerConnector.UNKNOWN_TIME;
            }
        }

        @Override
        public long getDuration() {
            try {
                return MediaPlayer2Impl.this.getDuration();
            } catch (IllegalStateException e) {
                return MediaPlayerConnector.UNKNOWN_TIME;
            }
        }

        @Override
        public long getBufferedPosition() {
            try {
                return MediaPlayer2Impl.this.getBufferedPosition();
            } catch (IllegalStateException e) {
                return MediaPlayerConnector.UNKNOWN_TIME;
            }
        }

        @Override
        public int getPlayerState() {
            return MediaPlayer2Impl.this.getPlayerState();
        }

        @Override
        public int getBufferingState() {
            return MediaPlayer2Impl.this.getBufferingState();
        }

        @Override
        public void setAudioAttributes(AudioAttributesCompat attributes) {
            MediaPlayer2Impl.this.setAudioAttributes(attributes);
        }

        @Override
        public AudioAttributesCompat getAudioAttributes() {
            return MediaPlayer2Impl.this.getAudioAttributes();
        }

        @Override
        public void setDataSource(DataSourceDesc2 dsd) {
            MediaPlayer2Impl.this.setDataSource(dsd);
        }

        @Override
        public void setNextDataSource(DataSourceDesc2 dsd) {
            MediaPlayer2Impl.this.setNextDataSource(dsd);
        }

        @Override
        public void setNextDataSources(List<DataSourceDesc2> dsds) {
            MediaPlayer2Impl.this.setNextDataSources(dsds);
        }

        @Override
        public DataSourceDesc2 getCurrentDataSource() {
            return MediaPlayer2Impl.this.getCurrentDataSource();
        }

        @Override
        public void loopCurrent(boolean loop) {
            MediaPlayer2Impl.this.loopCurrent(loop);
        }

        @Override
        public void setPlaybackSpeed(float speed) {
            MediaPlayer2Impl.this.setPlaybackParams(
                    new PlaybackParams2.Builder(
                            getPlaybackParams().getPlaybackParams()).setSpeed(speed).build());
        }

        @Override
        public float getPlaybackSpeed() {
            try {
                return MediaPlayer2Impl.this.getPlaybackParams().getSpeed();
            } catch (IllegalStateException e) {
                return super.getPlaybackSpeed();
            }
        }

        @Override
        public void setPlayerVolume(float volume) {
            MediaPlayer2Impl.this.setPlayerVolume(volume);
        }

        @Override
        public float getPlayerVolume() {
            return MediaPlayer2Impl.this.getPlayerVolume();
        }

        @Override
        public void registerPlayerEventCallback(Executor e, final PlayerEventCallback cb) {
            MediaPlayer2Impl.this.registerPlayerEventCallback(e, cb);
        }

        @Override
        public void unregisterPlayerEventCallback(PlayerEventCallback cb) {
            MediaPlayer2Impl.this.unregisterPlayerEventCallback(cb);
        }

        @Override
        public void close() throws Exception {
            MediaPlayer2Impl.this.close();
        }
    }
}

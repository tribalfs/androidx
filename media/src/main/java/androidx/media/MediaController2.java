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

package androidx.media;

import static android.support.v4.media.MediaMetadataCompat.METADATA_KEY_DURATION;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static androidx.media.MediaConstants2.ARGUMENT_ALLOWED_COMMANDS;
import static androidx.media.MediaConstants2.ARGUMENT_COMMAND_CODE;
import static androidx.media.MediaConstants2.ARGUMENT_ERROR_CODE;
import static androidx.media.MediaConstants2.ARGUMENT_ERROR_EXTRAS;
import static androidx.media.MediaConstants2.ARGUMENT_ICONTROLLER_CALLBACK;
import static androidx.media.MediaConstants2.ARGUMENT_PACKAGE_NAME;
import static androidx.media.MediaConstants2.ARGUMENT_PID;
import static androidx.media.MediaConstants2.ARGUMENT_PLAYBACK_STATE_COMPAT;
import static androidx.media.MediaConstants2.ARGUMENT_PLAYER_STATE;
import static androidx.media.MediaConstants2.ARGUMENT_PLAYLIST;
import static androidx.media.MediaConstants2.ARGUMENT_PLAYLIST_METADATA;
import static androidx.media.MediaConstants2.ARGUMENT_REPEAT_MODE;
import static androidx.media.MediaConstants2.ARGUMENT_SEEK_POSITION;
import static androidx.media.MediaConstants2.ARGUMENT_SHUFFLE_MODE;
import static androidx.media.MediaConstants2.ARGUMENT_UID;
import static androidx.media.MediaConstants2.CONNECT_RESULT_CONNECTED;
import static androidx.media.MediaConstants2.CONNECT_RESULT_DISCONNECTED;
import static androidx.media.MediaConstants2.CONTROLLER_COMMAND_BY_COMMAND_CODE;
import static androidx.media.MediaConstants2.CONTROLLER_COMMAND_CONNECT;
import static androidx.media.MediaConstants2.SESSION_EVENT_NOTIFY_ERROR;
import static androidx.media.MediaConstants2.SESSION_EVENT_ON_PLAYER_STATE_CHANGED;
import static androidx.media.MediaConstants2.SESSION_EVENT_ON_PLAYLIST_CHANGED;
import static androidx.media.MediaConstants2.SESSION_EVENT_ON_PLAYLIST_METADATA_CHANGED;
import static androidx.media.MediaConstants2.SESSION_EVENT_ON_REPEAT_MODE_CHANGED;
import static androidx.media.MediaConstants2.SESSION_EVENT_ON_SHUFFLE_MODE_CHANGED;
import static androidx.media.MediaPlayerBase.BUFFERING_STATE_UNKNOWN;
import static androidx.media.MediaPlayerBase.UNKNOWN_TIME;
import static androidx.media.SessionCommand2.COMMAND_CODE_PLAYBACK_PAUSE;
import static androidx.media.SessionCommand2.COMMAND_CODE_PLAYBACK_PLAY;
import static androidx.media.SessionCommand2.COMMAND_CODE_PLAYBACK_PREPARE;
import static androidx.media.SessionCommand2.COMMAND_CODE_PLAYBACK_RESET;
import static androidx.media.SessionCommand2.COMMAND_CODE_PLAYBACK_SEEK_TO;
import static androidx.media.SessionCommand2.COMMAND_CODE_PLAYLIST_SET_LIST;
import static androidx.media.SessionCommand2.COMMAND_CODE_PLAYLIST_SET_LIST_METADATA;
import static androidx.media.SessionCommand2.COMMAND_CODE_PLAYLIST_SET_REPEAT_MODE;
import static androidx.media.SessionCommand2.COMMAND_CODE_PLAYLIST_SET_SHUFFLE_MODE;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.content.Context;
import android.media.AudioManager;
import android.media.session.MediaSessionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.media.MediaPlaylistAgent.RepeatMode;
import androidx.media.MediaPlaylistAgent.ShuffleMode;
import androidx.media.MediaSession2.CommandButton;
import androidx.media.MediaSession2.ControllerInfo;
import androidx.media.MediaSession2.ErrorCode;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * @hide
 * Allows an app to interact with an active {@link MediaSession2} or a
 * {@link MediaSessionService2} in any status. Media buttons and other commands can be sent to
 * the session.
 * <p>
 * When you're done, use {@link #close()} to clean up resources. This also helps session service
 * to be destroyed when there's no controller associated with it.
 * <p>
 * When controlling {@link MediaSession2}, the controller will be available immediately after
 * the creation.
 * <p>
 * When controlling {@link MediaSessionService2}, the {@link MediaController2} would be
 * available only if the session service allows this controller by
 * {@link MediaSession2.SessionCallback#onConnect(MediaSession2, ControllerInfo)} for the service.
 * Wait {@link ControllerCallback#onConnected(MediaController2, SessionCommandGroup2)} or
 * {@link ControllerCallback#onDisconnected(MediaController2)} for the result.
 * <p>
 * A controller can be created through token from {@link MediaSessionManager} if you hold the
 * signature|privileged permission "android.permission.MEDIA_CONTENT_CONTROL" permission or are
 * an enabled notification listener or by getting a {@link SessionToken2} directly the
 * the session owner.
 * <p>
 * MediaController2 objects are thread-safe.
 * <p>
 * @see MediaSession2
 * @see MediaSessionService2
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
@RestrictTo(LIBRARY_GROUP)
public class MediaController2 implements AutoCloseable {
    /**
     * Interface for listening to change in activeness of the {@link MediaSession2}.  It's
     * active if and only if it has set a player.
     */
    public abstract static class ControllerCallback {
        /**
         * Called when the controller is successfully connected to the session. The controller
         * becomes available afterwards.
         *
         * @param controller the controller for this event
         * @param allowedCommands commands that's allowed by the session.
         */
        public void onConnected(@NonNull MediaController2 controller,
                @NonNull SessionCommandGroup2 allowedCommands) { }

        /**
         * Called when the session refuses the controller or the controller is disconnected from
         * the session. The controller becomes unavailable afterwards and the callback wouldn't
         * be called.
         * <p>
         * It will be also called after the {@link #close()}, so you can put clean up code here.
         * You don't need to call {@link #close()} after this.
         *
         * @param controller the controller for this event
         */
        public void onDisconnected(@NonNull MediaController2 controller) { }

        /**
         * Called when the session set the custom layout through the
         * {@link MediaSession2#setCustomLayout(ControllerInfo, List)}.
         * <p>
         * Can be called before {@link #onConnected(MediaController2, SessionCommandGroup2)}
         * is called.
         *
         * @param controller the controller for this event
         * @param layout
         */
        public void onCustomLayoutChanged(@NonNull MediaController2 controller,
                @NonNull List<CommandButton> layout) { }

        /**
         * Called when the session has changed anything related with the {@link PlaybackInfo}.
         *
         * @param controller the controller for this event
         * @param info new playback info
         */
        public void onPlaybackInfoChanged(@NonNull MediaController2 controller,
                @NonNull PlaybackInfo info) { }

        /**
         * Called when the allowed commands are changed by session.
         *
         * @param controller the controller for this event
         * @param commands newly allowed commands
         */
        public void onAllowedCommandsChanged(@NonNull MediaController2 controller,
                @NonNull SessionCommandGroup2 commands) { }

        /**
         * Called when the session sent a custom command.
         *
         * @param controller the controller for this event
         * @param command
         * @param args
         * @param receiver
         */
        public void onCustomCommand(@NonNull MediaController2 controller,
                @NonNull SessionCommand2 command, @Nullable Bundle args,
                @Nullable ResultReceiver receiver) { }

        /**
         * Called when the player state is changed.
         *
         * @param controller the controller for this event
         * @param state
         */
        public void onPlayerStateChanged(@NonNull MediaController2 controller, int state) { }

        /**
         * Called when playback speed is changed.
         *
         * @param controller the controller for this event
         * @param speed speed
         */
        public void onPlaybackSpeedChanged(@NonNull MediaController2 controller,
                float speed) { }

        /**
         * Called to report buffering events for a data source.
         * <p>
         * Use {@link #getBufferedPosition()} for current buffering position.
         *
         * @param controller the controller for this event
         * @param item the media item for which buffering is happening.
         * @param state the new buffering state.
         */
        public void onBufferingStateChanged(@NonNull MediaController2 controller,
                @NonNull MediaItem2 item, @MediaPlayerBase.BuffState int state) { }

        /**
         * Called to indicate that seeking is completed.
         *
         * @param controller the controller for this event.
         * @param position the previous seeking request.
         */
        public void onSeekCompleted(@NonNull MediaController2 controller, long position) { }

        /**
         * Called when a error from
         *
         * @param controller the controller for this event
         * @param errorCode error code
         * @param extras extra information
         */
        public void onError(@NonNull MediaController2 controller, @ErrorCode int errorCode,
                @Nullable Bundle extras) { }

        /**
         * Called when the player's currently playing item is changed
         * <p>
         * When it's called, you should invalidate previous playback information and wait for later
         * callbacks.
         *
         * @param controller the controller for this event
         * @param item new item
         * @see #onBufferingStateChanged(MediaController2, MediaItem2, int)
         */
        // TODO(jaewan): Use this (b/74316764)
        public void onCurrentMediaItemChanged(@NonNull MediaController2 controller,
                @NonNull MediaItem2 item) { }

        /**
         * Called when a playlist is changed.
         *
         * @param controller the controller for this event
         * @param list new playlist
         * @param metadata new metadata
         */
        public void onPlaylistChanged(@NonNull MediaController2 controller,
                @NonNull List<MediaItem2> list, @Nullable MediaMetadata2 metadata) { }

        /**
         * Called when a playlist metadata is changed.
         *
         * @param controller the controller for this event
         * @param metadata new metadata
         */
        public void onPlaylistMetadataChanged(@NonNull MediaController2 controller,
                @Nullable MediaMetadata2 metadata) { }

        /**
         * Called when the shuffle mode is changed.
         *
         * @param controller the controller for this event
         * @param shuffleMode repeat mode
         * @see MediaPlaylistAgent#SHUFFLE_MODE_NONE
         * @see MediaPlaylistAgent#SHUFFLE_MODE_ALL
         * @see MediaPlaylistAgent#SHUFFLE_MODE_GROUP
         */
        public void onShuffleModeChanged(@NonNull MediaController2 controller,
                @MediaPlaylistAgent.ShuffleMode int shuffleMode) { }

        /**
         * Called when the repeat mode is changed.
         *
         * @param controller the controller for this event
         * @param repeatMode repeat mode
         * @see MediaPlaylistAgent#REPEAT_MODE_NONE
         * @see MediaPlaylistAgent#REPEAT_MODE_ONE
         * @see MediaPlaylistAgent#REPEAT_MODE_ALL
         * @see MediaPlaylistAgent#REPEAT_MODE_GROUP
         */
        public void onRepeatModeChanged(@NonNull MediaController2 controller,
                @MediaPlaylistAgent.RepeatMode int repeatMode) { }
    }

    /**
     * Holds information about the the way volume is handled for this session.
     */
    // The same as MediaController.PlaybackInfo
    public static final class PlaybackInfo {
        private static final String KEY_PLAYBACK_TYPE = "android.media.audio_info.playback_type";
        private static final String KEY_CONTROL_TYPE = "android.media.audio_info.control_type";
        private static final String KEY_MAX_VOLUME = "android.media.audio_info.max_volume";
        private static final String KEY_CURRENT_VOLUME = "android.media.audio_info.current_volume";
        private static final String KEY_AUDIO_ATTRIBUTES = "android.media.audio_info.audio_attrs";

        private final int mPlaybackType;
        private final int mControlType;
        private final int mMaxVolume;
        private final int mCurrentVolume;
        private final AudioAttributesCompat mAudioAttrsCompat;

        /**
         * The session uses remote playback.
         */
        public static final int PLAYBACK_TYPE_REMOTE = 2;
        /**
         * The session uses local playback.
         */
        public static final int PLAYBACK_TYPE_LOCAL = 1;

        PlaybackInfo(int playbackType, AudioAttributesCompat attrs, int controlType, int max,
                int current) {
            mPlaybackType = playbackType;
            // TODO: Use AudioAttributesCompat instead of AudioAttributes, and set the value
            mAudioAttrsCompat = null;
            mControlType = controlType;
            mMaxVolume = max;
            mCurrentVolume = current;
        }

        /**
         * Get the type of playback which affects volume handling. One of:
         * <ul>
         * <li>{@link #PLAYBACK_TYPE_LOCAL}</li>
         * <li>{@link #PLAYBACK_TYPE_REMOTE}</li>
         * </ul>
         *
         * @return The type of playback this session is using.
         */
        public int getPlaybackType() {
            return mPlaybackType;
        }

        /**
         * Get the audio attributes for this session. The attributes will affect
         * volume handling for the session. When the volume type is
         * {@link #PLAYBACK_TYPE_REMOTE} these may be ignored by the
         * remote volume handler.
         *
         * @return The attributes for this session.
         */
        public AudioAttributesCompat getAudioAttributes() {
            return mAudioAttrsCompat;
        }

        /**
         * Get the type of volume control that can be used. One of:
         * <ul>
         * <li>{@link VolumeProviderCompat#VOLUME_CONTROL_ABSOLUTE}</li>
         * <li>{@link VolumeProviderCompat#VOLUME_CONTROL_RELATIVE}</li>
         * <li>{@link VolumeProviderCompat#VOLUME_CONTROL_FIXED}</li>
         * </ul>
         *
         * @return The type of volume control that may be used with this session.
         */
        public int getControlType() {
            return mControlType;
        }

        /**
         * Get the maximum volume that may be set for this session.
         *
         * @return The maximum allowed volume where this session is playing.
         */
        public int getMaxVolume() {
            return mMaxVolume;
        }

        /**
         * Get the current volume for this session.
         *
         * @return The current volume where this session is playing.
         */
        public int getCurrentVolume() {
            return mCurrentVolume;
        }

        Bundle toBundle() {
            Bundle bundle = new Bundle();
            bundle.putInt(KEY_PLAYBACK_TYPE, mPlaybackType);
            bundle.putInt(KEY_CONTROL_TYPE, mControlType);
            bundle.putInt(KEY_MAX_VOLUME, mMaxVolume);
            bundle.putInt(KEY_CURRENT_VOLUME, mCurrentVolume);
            bundle.putParcelable(KEY_AUDIO_ATTRIBUTES,
                    MediaUtils2.toAudioAttributesBundle(mAudioAttrsCompat));
            return bundle;
        }

        static PlaybackInfo createPlaybackInfo(int playbackType, AudioAttributesCompat attrs,
                int controlType, int max, int current) {
            return new PlaybackInfo(playbackType, attrs, controlType, max, current);
        }

        static PlaybackInfo fromBundle(Bundle bundle) {
            if (bundle == null) {
                return null;
            }
            final int volumeType = bundle.getInt(KEY_PLAYBACK_TYPE);
            final int volumeControl = bundle.getInt(KEY_CONTROL_TYPE);
            final int maxVolume = bundle.getInt(KEY_MAX_VOLUME);
            final int currentVolume = bundle.getInt(KEY_CURRENT_VOLUME);
            final AudioAttributesCompat attrs = MediaUtils2.fromAudioAttributesBundle(
                    bundle.getBundle(KEY_AUDIO_ATTRIBUTES));

            return createPlaybackInfo(volumeType, attrs, volumeControl, maxVolume,
                    currentVolume);
        }
    }

    private final class ControllerCompatCallback extends MediaControllerCompat.Callback {
        @Override
        public void onSessionReady() {
            sendCommand(CONTROLLER_COMMAND_CONNECT, null, new ResultReceiver(mHandler) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    if (!mHandlerThread.isAlive()) {
                        return;
                    }
                    switch (resultCode) {
                        case CONNECT_RESULT_CONNECTED:
                            onConnectedNotLocked(resultData);
                            break;
                        case CONNECT_RESULT_DISCONNECTED:
                            mCallback.onDisconnected(MediaController2.this);
                            close();
                            break;
                    }
                }
            });
        }

        @Override
        public void onSessionDestroyed() {
            close();
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            synchronized (mLock) {
                mPlaybackStateCompat = state;
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            synchronized (mLock) {
                mMediaMetadataCompat = metadata;
            }
        }

        @Override
        public void onSessionEvent(String event, Bundle extras) {
            switch (event) {
                case SESSION_EVENT_ON_PLAYER_STATE_CHANGED: {
                    int playerState = extras.getInt(ARGUMENT_PLAYER_STATE);
                    synchronized (mLock) {
                        mPlayerState = playerState;
                    }
                    mCallback.onPlayerStateChanged(MediaController2.this, playerState);
                    break;
                }
                case SESSION_EVENT_NOTIFY_ERROR: {
                    int errorCode = extras.getInt(ARGUMENT_ERROR_CODE);
                    Bundle errorExtras = extras.getBundle(ARGUMENT_ERROR_EXTRAS);
                    mCallback.onError(MediaController2.this, errorCode, errorExtras);
                    break;
                }
                case SESSION_EVENT_ON_PLAYLIST_CHANGED: {
                    MediaMetadata2 playlistMetadata = MediaMetadata2.fromBundle(
                            extras.getBundle(ARGUMENT_PLAYLIST_METADATA));
                    List<MediaItem2> playlist = MediaUtils2.fromMediaItem2BundleArray(
                            (Bundle[]) extras.getParcelableArray(ARGUMENT_PLAYLIST));
                    synchronized (mLock) {
                        mPlaylist = playlist;
                        mPlaylistMetadata = playlistMetadata;
                    }
                    mCallback.onPlaylistChanged(MediaController2.this, playlist, playlistMetadata);
                    break;
                }
                case SESSION_EVENT_ON_PLAYLIST_METADATA_CHANGED: {
                    MediaMetadata2 playlistMetadata = MediaMetadata2.fromBundle(
                            extras.getBundle(ARGUMENT_PLAYLIST_METADATA));
                    synchronized (mLock) {
                        mPlaylistMetadata = playlistMetadata;
                    }
                    mCallback.onPlaylistMetadataChanged(MediaController2.this, playlistMetadata);
                    break;
                }
                case SESSION_EVENT_ON_REPEAT_MODE_CHANGED: {
                    int repeatMode = extras.getInt(ARGUMENT_REPEAT_MODE);
                    synchronized (mLock) {
                        mRepeatMode = repeatMode;
                    }
                    mCallback.onRepeatModeChanged(MediaController2.this, repeatMode);
                    break;
                }
                case SESSION_EVENT_ON_SHUFFLE_MODE_CHANGED: {
                    int shuffleMode = extras.getInt(ARGUMENT_SHUFFLE_MODE);
                    synchronized (mLock) {
                        mShuffleMode = shuffleMode;
                    }
                    mCallback.onShuffleModeChanged(MediaController2.this, shuffleMode);
                    break;
                }
            }
        }
    }

    private static final String TAG = "MediaController2";
    private static final boolean DEBUG = true; // TODO(jaewan): Change

    // TODO: Is null root suitable here?
    static final Bundle sDefaultRootHints = null;

    private final Context mContext;
    private final Object mLock = new Object();

    private final SessionToken2 mToken;
    private final ControllerCallback mCallback;
    private final Executor mCallbackExecutor;
    private final IBinder.DeathRecipient mDeathRecipient;

    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    @GuardedBy("mLock")
    private MediaBrowserCompat mBrowserCompat;
    @GuardedBy("mLock")
    private boolean mIsReleased;
    @GuardedBy("mLock")
    private List<MediaItem2> mPlaylist;
    @GuardedBy("mLock")
    private MediaMetadata2 mPlaylistMetadata;
    @GuardedBy("mLock")
    private @RepeatMode int mRepeatMode;
    @GuardedBy("mLock")
    private @ShuffleMode int mShuffleMode;
    @GuardedBy("mLock")
    private int mPlayerState;
    @GuardedBy("mLock")
    private PlaybackInfo mPlaybackInfo;
    @GuardedBy("mLock")
    private SessionCommandGroup2 mAllowedCommands;

    // Media 1.0 variables
    @GuardedBy("mLock")
    private MediaControllerCompat mControllerCompat;
    @GuardedBy("mLock")
    private ControllerCompatCallback mControllerCompatCallback;
    @GuardedBy("mLock")
    private PlaybackStateCompat mPlaybackStateCompat;
    @GuardedBy("mLock")
    private MediaMetadataCompat mMediaMetadataCompat;

    // Assignment should be used with the lock hold, but should be used without a lock to prevent
    // potential deadlock.
    @GuardedBy("mLock")
    private volatile boolean mConnected;

    /**
     * Create a {@link MediaController2} from the {@link SessionToken2}.
     * This connects to the session and may wake up the service if it's not available.
     *
     * @param context Context
     * @param token token to connect to
     * @param executor executor to run callbacks on.
     * @param callback controller callback to receive changes in
     */
    public MediaController2(@NonNull Context context, @NonNull SessionToken2 token,
            @NonNull Executor executor, @NonNull ControllerCallback callback) {
        super();
        if (context == null) {
            throw new IllegalArgumentException("context shouldn't be null");
        }
        if (token == null) {
            throw new IllegalArgumentException("token shouldn't be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback shouldn't be null");
        }
        if (executor == null) {
            throw new IllegalArgumentException("executor shouldn't be null");
        }
        mContext = context;
        mHandlerThread = new HandlerThread("MediaController2_Thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());
        mToken = token;
        mCallback = callback;
        mCallbackExecutor = executor;
        mDeathRecipient = new IBinder.DeathRecipient() {
            @Override
            public void binderDied() {
                MediaController2.this.close();
            }
        };

        initialize();
    }

    /**
     * Release this object, and disconnect from the session. After this, callbacks wouldn't be
     * received.
     */
    @Override
    public void close() {
        if (DEBUG) {
            Log.d(TAG, "release from " + mToken, new IllegalStateException());
        }
        synchronized (mLock) {
            if (mIsReleased) {
                // Prevent re-enterance from the ControllerCallback.onDisconnected()
                return;
            }
            mHandler.removeCallbacksAndMessages(null);
            mHandlerThread.quitSafely();

            mIsReleased = true;
            if (mBrowserCompat != null) {
                mBrowserCompat.disconnect();
                mBrowserCompat = null;
            }
            if (mControllerCompat != null) {
                mControllerCompat.unregisterCallback(mControllerCompatCallback);
                mControllerCompat = null;
            }
            mConnected = false;
        }
        mCallbackExecutor.execute(new Runnable() {
            @Override
            public void run() {
                mCallback.onDisconnected(MediaController2.this);
            }
        });
    }

    /**
     * @return token
     */
    public @NonNull SessionToken2 getSessionToken() {
        return mToken;
    }

    /**
     * Returns whether this class is connected to active {@link MediaSession2} or not.
     */
    public boolean isConnected() {
        synchronized (mLock) {
            return mConnected;
        }
    }

    /**
     * Requests that the player starts or resumes playback.
     */
    public void play() {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            sendCommand(COMMAND_CODE_PLAYBACK_PLAY);
        }
    }

    /**
     * Requests that the player pauses playback.
     */
    public void pause() {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            sendCommand(COMMAND_CODE_PLAYBACK_PAUSE);
        }
    }

    /**
     * Requests that the player be reset to its uninitialized state.
     */
    public void reset() {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            sendCommand(COMMAND_CODE_PLAYBACK_RESET);
        }
    }

    /**
     * Request that the player prepare its playback. In other words, other sessions can continue
     * to play during the preparation of this session. This method can be used to speed up the
     * start of the playback. Once the preparation is done, the session will change its playback
     * state to {@link MediaPlayerBase#PLAYER_STATE_PAUSED}. Afterwards, {@link #play} can be called
     * to start playback.
     */
    public void prepare() {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            sendCommand(COMMAND_CODE_PLAYBACK_PREPARE);
        }
    }

    /**
     * Start fast forwarding. If playback is already fast forwarding this
     * may increase the rate.
     */
    public void fastForward() {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            MediaControllerCompat.TransportControls control =
                    mControllerCompat.getTransportControls();
            if (control != null) {
                control.fastForward();
            }
        }
    }

    /**
     * Start rewinding. If playback is already rewinding this may increase
     * the rate.
     */
    public void rewind() {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            MediaControllerCompat.TransportControls control =
                    mControllerCompat.getTransportControls();
            if (control != null) {
                control.rewind();
            }
        }
    }

    /**
     * Move to a new location in the media stream.
     *
     * @param pos Position to move to, in milliseconds.
     */
    public void seekTo(long pos) {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            Bundle args = new Bundle();
            args.putLong(ARGUMENT_SEEK_POSITION, pos);
            sendCommand(COMMAND_CODE_PLAYBACK_SEEK_TO, args);
        }
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void skipForward() {
        // To match with KEYCODE_MEDIA_SKIP_FORWARD
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void skipBackward() {
        // To match with KEYCODE_MEDIA_SKIP_BACKWARD
    }

    /**
     * Request that the player start playback for a specific media id.
     *
     * @param mediaId The id of the requested media.
     * @param extras Optional extras that can include extra information about the media item
     *               to be played.
     */
    public void playFromMediaId(@NonNull String mediaId, @Nullable Bundle extras) {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            MediaControllerCompat.TransportControls control =
                    mControllerCompat.getTransportControls();
            if (control != null) {
                control.playFromMediaId(mediaId, extras);
            }
        }
    }

    /**
     * Request that the player start playback for a specific search query.
     *
     * @param query The search query. Should not be an empty string.
     * @param extras Optional extras that can include extra information about the query.
     */
    public void playFromSearch(@NonNull String query, @Nullable Bundle extras) {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            MediaControllerCompat.TransportControls control =
                    mControllerCompat.getTransportControls();
            if (control != null) {
                control.playFromSearch(query, extras);
            }
        }
    }

    /**
     * Request that the player start playback for a specific {@link Uri}.
     *
     * @param uri The URI of the requested media.
     * @param extras Optional extras that can include extra information about the media item
     *               to be played.
     */
    public void playFromUri(@NonNull Uri uri, @Nullable Bundle extras) {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            MediaControllerCompat.TransportControls control =
                    mControllerCompat.getTransportControls();
            if (control != null) {
                control.playFromUri(uri, extras);
            }
        }
    }

    /**
     * Request that the player prepare playback for a specific media id. In other words, other
     * sessions can continue to play during the preparation of this session. This method can be
     * used to speed up the start of the playback. Once the preparation is done, the session
     * will change its playback state to {@link MediaPlayerBase#PLAYER_STATE_PAUSED}. Afterwards,
     * {@link #play} can be called to start playback. If the preparation is not needed,
     * {@link #playFromMediaId} can be directly called without this method.
     *
     * @param mediaId The id of the requested media.
     * @param extras Optional extras that can include extra information about the media item
     *               to be prepared.
     */
    public void prepareFromMediaId(@NonNull String mediaId, @Nullable Bundle extras) {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            MediaControllerCompat.TransportControls control =
                    mControllerCompat.getTransportControls();
            if (control != null) {
                control.prepareFromMediaId(mediaId, extras);
            }
        }
    }

    /**
     * Request that the player prepare playback for a specific search query.
     * In other words, other sessions can continue to play during the preparation of this session.
     * This method can be used to speed up the start of the playback.
     * Once the preparation is done, the session will change its playback state to
     * {@link MediaPlayerBase#PLAYER_STATE_PAUSED}. Afterwards,
     * {@link #play} can be called to start playback. If the preparation is not needed,
     * {@link #playFromSearch} can be directly called without this method.
     *
     * @param query The search query. Should not be an empty string.
     * @param extras Optional extras that can include extra information about the query.
     */
    public void prepareFromSearch(@NonNull String query, @Nullable Bundle extras) {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            MediaControllerCompat.TransportControls control =
                    mControllerCompat.getTransportControls();
            if (control != null) {
                control.prepareFromSearch(query, extras);
            }
        }
    }

    /**
     * Request that the player prepare playback for a specific {@link Uri}. In other words,
     * other sessions can continue to play during the preparation of this session. This method
     * can be used to speed up the start of the playback. Once the preparation is done, the
     * session will change its playback state to {@link MediaPlayerBase#PLAYER_STATE_PAUSED}.
     * Afterwards, {@link #play} can be called to start playback. If the preparation is not needed,
     * {@link #playFromUri} can be directly called without this method.
     *
     * @param uri The URI of the requested media.
     * @param extras Optional extras that can include extra information about the media item
     *               to be prepared.
     */
    public void prepareFromUri(@NonNull Uri uri, @Nullable Bundle extras) {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            MediaControllerCompat.TransportControls control =
                    mControllerCompat.getTransportControls();
            if (control != null) {
                control.prepareFromUri(uri, extras);
            }
        }
    }

    /**
     * Set the volume of the output this session is playing on. The command will be ignored if it
     * does not support {@link VolumeProviderCompat#VOLUME_CONTROL_ABSOLUTE}.
     * <p>
     * If the session is local playback, this changes the device's volume with the stream that
     * session's player is using. Flags will be specified for the {@link AudioManager}.
     * <p>
     * If the session is remote player (i.e. session has set volume provider), its volume provider
     * will receive this request instead.
     *
     * @see #getPlaybackInfo()
     * @param value The value to set it to, between 0 and the reported max.
     * @param flags flags from {@link AudioManager} to include with the volume request for local
     *              playback
     */
    public void setVolumeTo(int value, int flags) {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            mControllerCompat.setVolumeTo(value, flags);
        }
    }

    /**
     * Adjust the volume of the output this session is playing on. The direction
     * must be one of {@link AudioManager#ADJUST_LOWER},
     * {@link AudioManager#ADJUST_RAISE}, or {@link AudioManager#ADJUST_SAME}.
     * The command will be ignored if the session does not support
     * {@link VolumeProviderCompat#VOLUME_CONTROL_RELATIVE} or
     * {@link VolumeProviderCompat#VOLUME_CONTROL_ABSOLUTE}.
     * <p>
     * If the session is local playback, this changes the device's volume with the stream that
     * session's player is using. Flags will be specified for the {@link AudioManager}.
     * <p>
     * If the session is remote player (i.e. session has set volume provider), its volume provider
     * will receive this request instead.
     *
     * @see #getPlaybackInfo()
     * @param direction The direction to adjust the volume in.
     * @param flags flags from {@link AudioManager} to include with the volume request for local
     *              playback
     */
    public void adjustVolume(int direction, int flags) {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return;
            }
            mControllerCompat.adjustVolume(direction, flags);
        }
    }

    /**
     * Get an intent for launching UI associated with this session if one exists.
     *
     * @return A {@link PendingIntent} to launch UI or null.
     */
    public @Nullable PendingIntent getSessionActivity() {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return null;
            }
            return mControllerCompat.getSessionActivity();
        }
    }

    /**
     * Get the lastly cached player state from
     * {@link ControllerCallback#onPlayerStateChanged(MediaController2, int)}.
     *
     * @return player state
     */
    public int getPlayerState() {
        synchronized (mLock) {
            return mPlayerState;
        }
    }

    /**
     * Gets the duration of the current media item, or {@link MediaPlayerBase#UNKNOWN_TIME} if
     * unknown.
     * @return the duration in ms, or {@link MediaPlayerBase#UNKNOWN_TIME}.
     */
    public long getDuration() {
        synchronized (mLock) {
            if (mMediaMetadataCompat != null
                    && mMediaMetadataCompat.containsKey(METADATA_KEY_DURATION)) {
                return mMediaMetadataCompat.getLong(METADATA_KEY_DURATION);
            }
        }
        return MediaPlayerBase.UNKNOWN_TIME;
    }

    /**
     * Gets the current playback position.
     * <p>
     * This returns the calculated value of the position, based on the difference between the
     * update time and current time.
     *
     * @return position
     */
    public long getCurrentPosition() {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return UNKNOWN_TIME;
            }
            if (mPlaybackStateCompat != null) {
                long timeDiff = System.currentTimeMillis()
                        - mPlaybackStateCompat.getLastPositionUpdateTime();
                long expectedPosition = mPlaybackStateCompat.getPosition()
                        + (long) (mPlaybackStateCompat.getPlaybackSpeed() * timeDiff);
                return Math.max(0, expectedPosition);
            }
            return UNKNOWN_TIME;
        }
    }

    /**
     * Get the lastly cached playback speed from
     * {@link ControllerCallback#onPlaybackSpeedChanged(MediaController2, float)}.
     *
     * @return speed the lastly cached playback speed, or 0.0f if unknown.
     */
    public float getPlaybackSpeed() {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return 0f;
            }
            return (mPlaybackStateCompat == null) ? 0f : mPlaybackStateCompat.getPlaybackSpeed();
        }
    }

    /**
     * Set the playback speed.
     */
    public void setPlaybackSpeed(float speed) {
        // TODO(jaewan): implement this (b/74093080)
    }

    /**
     * Gets the current buffering state of the player.
     * During buffering, see {@link #getBufferedPosition()} for the quantifying the amount already
     * buffered.
     * @return the buffering state.
     */
    public @MediaPlayerBase.BuffState int getBufferingState() {
        // TODO(jaewan): Implement.
        return BUFFERING_STATE_UNKNOWN;
    }

    /**
     * Gets the lastly cached buffered position from the session when
     * {@link ControllerCallback#onBufferingStateChanged(MediaController2, MediaItem2, int)} is
     * called.
     *
     * @return buffering position in millis, or {@link MediaPlayerBase#UNKNOWN_TIME} if unknown.
     */
    public long getBufferedPosition() {
        synchronized (mLock) {
            if (!mConnected) {
                Log.w(TAG, "Session isn't active", new IllegalStateException());
                return UNKNOWN_TIME;
            }
            return (mPlaybackStateCompat == null) ? UNKNOWN_TIME
                    : mPlaybackStateCompat.getBufferedPosition();
        }
    }

    /**
     * Get the current playback info for this session.
     *
     * @return The current playback info or null.
     */
    public @Nullable PlaybackInfo getPlaybackInfo() {
        synchronized (mLock) {
            // TODO: update mPlaybackInfo via MediaControllerCompat.Callback.onAudioInfoChanged().
            return mPlaybackInfo;
        }
    }

    /**
     * Rate the media. This will cause the rating to be set for the current user.
     * The rating style must follow the user rating style from the session.
     * You can get the rating style from the session through the
     * {@link MediaMetadata2#getRating(String)} with the key
     * {@link MediaMetadata2#METADATA_KEY_USER_RATING}.
     * <p>
     * If the user rating was {@code null}, the media item does not accept setting user rating.
     *
     * @param mediaId The id of the media
     * @param rating The rating to set
     */
    public void setRating(@NonNull String mediaId, @NonNull Rating2 rating) {
        //mProvider.setRating_impl(mediaId, rating);
    }

    /**
     * Send custom command to the session
     *
     * @param command custom command
     * @param args optional argument
     * @param cb optional result receiver
     */
    public void sendCustomCommand(@NonNull SessionCommand2 command, @Nullable Bundle args,
            @Nullable ResultReceiver cb) {
        //mProvider.sendCustomCommand_impl(command, args, cb);
    }

    /**
     * Returns the cached playlist from {@link ControllerCallback#onPlaylistChanged}.
     * <p>
     * This list may differ with the list that was specified with
     * {@link #setPlaylist(List, MediaMetadata2)} depending on the {@link MediaPlaylistAgent}
     * implementation. Use media items returned here for other playlist agent APIs such as
     * {@link MediaPlaylistAgent#skipToPlaylistItem(MediaItem2)}.
     *
     * @return playlist. Can be {@code null} if the controller doesn't have enough permission.
     */
    public @Nullable List<MediaItem2> getPlaylist() {
        synchronized (mLock) {
            return mPlaylist;
        }
    }

    /**
     * Sets the playlist.
     * <p>
     * Even when the playlist is successfully set, use the playlist returned from
     * {@link #getPlaylist()} for playlist APIs such as {@link #skipToPlaylistItem(MediaItem2)}.
     * Otherwise the session in the remote process can't distinguish between media items.
     *
     * @param list playlist
     * @param metadata metadata of the playlist
     * @see #getPlaylist()
     * @see ControllerCallback#onPlaylistChanged
     */
    public void setPlaylist(@NonNull List<MediaItem2> list, @Nullable MediaMetadata2 metadata) {
        if (list == null) {
            throw new IllegalArgumentException("list shouldn't be null");
        }
        Bundle args = new Bundle();
        args.putParcelableArray(ARGUMENT_PLAYLIST, MediaUtils2.toMediaItem2BundleArray(list));
        args.putBundle(ARGUMENT_PLAYLIST_METADATA, metadata == null ? null : metadata.toBundle());
        sendCommand(COMMAND_CODE_PLAYLIST_SET_LIST, args);
    }

    /**
     * Updates the playlist metadata
     *
     * @param metadata metadata of the playlist
     */
    public void updatePlaylistMetadata(@Nullable MediaMetadata2 metadata) {
        Bundle args = new Bundle();
        args.putBundle(ARGUMENT_PLAYLIST_METADATA, metadata == null ? null : metadata.toBundle());
        sendCommand(COMMAND_CODE_PLAYLIST_SET_LIST_METADATA, args);
    }

    /**
     * Gets the lastly cached playlist playlist metadata either from
     * {@link ControllerCallback#onPlaylistMetadataChanged or
     * {@link ControllerCallback#onPlaylistChanged}.
     *
     * @return metadata metadata of the playlist, or null if none is set
     */
    public @Nullable MediaMetadata2 getPlaylistMetadata() {
        synchronized (mLock) {
            return mPlaylistMetadata;
        }
    }

    /**
     * Adds the media item to the playlist at position index. Index equals or greater than
     * the current playlist size will add the item at the end of the playlist.
     * <p>
     * This will not change the currently playing media item.
     * If index is less than or equal to the current index of the playlist,
     * the current index of the playlist will be incremented correspondingly.
     *
     * @param index the index you want to add
     * @param item the media item you want to add
     */
    public void addPlaylistItem(int index, @NonNull MediaItem2 item) {
        //mProvider.addPlaylistItem_impl(index, item);
    }

    /**
     * Removes the media item at index in the playlist.
     *<p>
     * If the item is the currently playing item of the playlist, current playback
     * will be stopped and playback moves to next source in the list.
     *
     * @param item the media item you want to add
     */
    public void removePlaylistItem(@NonNull MediaItem2 item) {
        //mProvider.removePlaylistItem_impl(item);
    }

    /**
     * Replace the media item at index in the playlist. This can be also used to update metadata of
     * an item.
     *
     * @param index the index of the item to replace
     * @param item the new item
     */
    public void replacePlaylistItem(int index, @NonNull MediaItem2 item) {
        //mProvider.replacePlaylistItem_impl(index, item);
    }

    /**
     * Get the lastly cached current item from
     * {@link ControllerCallback#onCurrentMediaItemChanged(MediaController2, MediaItem2)}.
     *
     * @return index of the current item
     */
    public MediaItem2 getCurrentMediaItem() {
        //return mProvider.getCurrentMediaItem_impl();
        return null;
    }

    /**
     * Skips to the previous item in the playlist.
     * <p>
     * This calls {@link MediaPlaylistAgent#skipToPreviousItem()}.
     */
    public void skipToPreviousItem() {
        //mProvider.skipToPreviousItem_impl();
    }

    /**
     * Skips to the next item in the playlist.
     * <p>
     * This calls {@link MediaPlaylistAgent#skipToNextItem()}.
     */
    public void skipToNextItem() {
        //mProvider.skipToNextItem_impl();
    }

    /**
     * Skips to the item in the playlist.
     * <p>
     * This calls {@link MediaPlaylistAgent#skipToPlaylistItem(MediaItem2)}.
     *
     * @param item The item in the playlist you want to play
     */
    public void skipToPlaylistItem(@NonNull MediaItem2 item) {
        //mProvider.skipToPlaylistItem_impl(item);
    }

    /**
     * Gets the cached repeat mode from the {@link ControllerCallback#onRepeatModeChanged}.
     *
     * @return repeat mode
     * @see MediaPlaylistAgent#REPEAT_MODE_NONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ALL
     * @see MediaPlaylistAgent#REPEAT_MODE_GROUP
     */
    public @RepeatMode int getRepeatMode() {
        synchronized (mLock) {
            return mRepeatMode;
        }
    }

    /**
     * Sets the repeat mode.
     *
     * @param repeatMode repeat mode
     * @see MediaPlaylistAgent#REPEAT_MODE_NONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ALL
     * @see MediaPlaylistAgent#REPEAT_MODE_GROUP
     */
    public void setRepeatMode(@RepeatMode int repeatMode) {
        // TODO: check permission
        Bundle args = new Bundle();
        args.putInt(ARGUMENT_REPEAT_MODE, repeatMode);
        sendCommand(COMMAND_CODE_PLAYLIST_SET_REPEAT_MODE, args);
    }

    /**
     * Gets the cached shuffle mode from the {@link ControllerCallback#onShuffleModeChanged}.
     *
     * @return The shuffle mode
     * @see MediaPlaylistAgent#SHUFFLE_MODE_NONE
     * @see MediaPlaylistAgent#SHUFFLE_MODE_ALL
     * @see MediaPlaylistAgent#SHUFFLE_MODE_GROUP
     */
    public @ShuffleMode int getShuffleMode() {
        synchronized (mLock) {
            return mShuffleMode;
        }
    }

    /**
     * Sets the shuffle mode.
     *
     * @param shuffleMode The shuffle mode
     * @see MediaPlaylistAgent#SHUFFLE_MODE_NONE
     * @see MediaPlaylistAgent#SHUFFLE_MODE_ALL
     * @see MediaPlaylistAgent#SHUFFLE_MODE_GROUP
     */
    public void setShuffleMode(@ShuffleMode int shuffleMode) {
        // TODO: check permission
        Bundle args = new Bundle();
        args.putInt(ARGUMENT_SHUFFLE_MODE, shuffleMode);
        sendCommand(COMMAND_CODE_PLAYLIST_SET_SHUFFLE_MODE, args);
    }

    // Should be used without a lock to prevent potential deadlock.
    void onConnectedNotLocked(Bundle data) {
        // TODO: Getting mPlaybackInfo via MediaControllerCompat.Callback.onAudioInfoChanged()
        // is enough or should we pass it while connecting?
        final SessionCommandGroup2 allowedCommands = SessionCommandGroup2.fromBundle(
                data.getBundle(ARGUMENT_ALLOWED_COMMANDS));
        final int playerState = data.getInt(ARGUMENT_PLAYER_STATE);
        final PlaybackStateCompat playbackStateCompat = data.getParcelable(
                ARGUMENT_PLAYBACK_STATE_COMPAT);
        final int repeatMode = data.getInt(ARGUMENT_REPEAT_MODE);
        final int shuffleMode = data.getInt(ARGUMENT_SHUFFLE_MODE);
        // TODO: Set mMediaMetadataCompat from the data.
        final List<MediaItem2> playlist = MediaUtils2.fromMediaItem2BundleArray(
                (Bundle[]) data.getParcelableArray(ARGUMENT_PLAYLIST));
        if (DEBUG) {
            Log.d(TAG, "onConnectedNotLocked sessionCompatToken=" + mToken.getSessionCompatToken()
                    + ", allowedCommands=" + allowedCommands);
        }
        boolean close = false;
        try {
            synchronized (mLock) {
                if (mIsReleased) {
                    return;
                }
                if (mConnected) {
                    Log.e(TAG, "Cannot be notified about the connection result many times."
                            + " Probably a bug or malicious app.");
                    close = true;
                    return;
                }
                mAllowedCommands = allowedCommands;
                mPlayerState = playerState;
                mPlaybackStateCompat = playbackStateCompat;
                mRepeatMode = repeatMode;
                mShuffleMode = shuffleMode;
                mPlaylist = playlist;
                mConnected = true;
            }
            // TODO(jaewan): Keep commands to prevents illegal API calls.
            mCallbackExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    // Note: We may trigger ControllerCallbacks with the initial values
                    // But it's hard to define the order of the controller callbacks
                    // Only notify about the
                    mCallback.onConnected(MediaController2.this, allowedCommands);
                }
            });
        } finally {
            if (close) {
                // Trick to call release() without holding the lock, to prevent potential deadlock
                // with the developer's custom lock within the ControllerCallback.onDisconnected().
                close();
            }
        }
    }

    private void initialize() {
        // TODO(jaewan): More sanity checks.
        // TODO: Check the connection between 1.0 and 2.0 APIs
        if (mToken.getType() == SessionToken2.TYPE_SESSION) {
            synchronized (mLock) {
                mBrowserCompat = null;
            }
            connectToSession(mToken.getSessionCompatToken());
        } else {
            connectToService();
        }
    }

    private void connectToSession(MediaSessionCompat.Token sessionCompatToken) {
        MediaControllerCompat controllerCompat = null;
        try {
            controllerCompat = new MediaControllerCompat(mContext, sessionCompatToken);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
        synchronized (mLock) {
            mControllerCompat = controllerCompat;
            mControllerCompatCallback = new ControllerCompatCallback();
            mControllerCompat.registerCallback(mControllerCompatCallback, mHandler);
        }

        if (controllerCompat.isSessionReady()) {
            sendCommand(CONTROLLER_COMMAND_CONNECT, null, new ResultReceiver(mHandler) {
                @Override
                protected void onReceiveResult(int resultCode, Bundle resultData) {
                    if (!mHandlerThread.isAlive()) {
                        return;
                    }
                    switch (resultCode) {
                        case CONNECT_RESULT_CONNECTED:
                            onConnectedNotLocked(resultData);
                            break;
                        case CONNECT_RESULT_DISCONNECTED:
                            mCallback.onDisconnected(MediaController2.this);
                            close();
                            break;
                    }
                }
            });
        }
    }

    private void connectToService() {
        synchronized (mLock) {
            mBrowserCompat = new MediaBrowserCompat(mContext, mToken.getComponentName(),
                    new ConnectionCallback(), sDefaultRootHints);
            mBrowserCompat.connect();
        }
    }

    private void sendCommand(int commandCode) {
        sendCommand(commandCode, null);
    }

    private void sendCommand(int commandCode, Bundle args) {
        if (args == null) {
            args = new Bundle();
        }
        args.putInt(ARGUMENT_COMMAND_CODE, commandCode);
        sendCommand(CONTROLLER_COMMAND_BY_COMMAND_CODE, args, null);
    }

    private void sendCommand(String command, Bundle args, ResultReceiver receiver) {
        if (args == null) {
            args = new Bundle();
        }
        MediaControllerCompat controller;
        ControllerCompatCallback callback;
        synchronized (mLock) {
            controller = mControllerCompat;
            callback = mControllerCompatCallback;
        }
        args.putBinder(ARGUMENT_ICONTROLLER_CALLBACK, callback.getIControllerCallback().asBinder());
        args.putString(ARGUMENT_PACKAGE_NAME, mContext.getPackageName());
        args.putInt(ARGUMENT_UID, Process.myUid());
        args.putInt(ARGUMENT_PID, Process.myPid());
        controller.sendCommand(command, args, receiver);
    }

    @NonNull Context getContext() {
        return mContext;
    }

    @NonNull ControllerCallback getCallback() {
        return mCallback;
    }

    @NonNull Executor getCallbackExecutor() {
        return mCallbackExecutor;
    }

    @Nullable MediaBrowserCompat getBrowserCompat() {
        synchronized (mLock) {
            return mBrowserCompat;
        }
    }

    private class ConnectionCallback extends MediaBrowserCompat.ConnectionCallback {
        @Override
        public void onConnected() {
            MediaBrowserCompat browser = getBrowserCompat();
            if (browser != null) {
                connectToSession(browser.getSessionToken());
            } else if (DEBUG) {
                Log.d(TAG, "Controller is closed prematually", new IllegalStateException());
            }
        }

        @Override
        public void onConnectionSuspended() {
            close();
        }

        @Override
        public void onConnectionFailed() {
            close();
        }
    }
}

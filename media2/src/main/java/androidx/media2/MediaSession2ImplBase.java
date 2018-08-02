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

import static androidx.media2.MediaPlayerConnector.BUFFERING_STATE_UNKNOWN;
import static androidx.media2.MediaPlayerConnector.PLAYER_STATE_IDLE;
import static androidx.media2.MediaSession2.ControllerCb;
import static androidx.media2.MediaSession2.ControllerInfo;
import static androidx.media2.MediaSession2.OnDataSourceMissingHelper;
import static androidx.media2.MediaSession2.SessionCallback;
import static androidx.media2.SessionToken2.TYPE_LIBRARY_SERVICE;
import static androidx.media2.SessionToken2.TYPE_SESSION;
import static androidx.media2.SessionToken2.TYPE_SESSION_SERVICE;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.os.SystemClock;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.Token;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.ObjectsCompat;
import androidx.media.AudioAttributesCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.VolumeProviderCompat;
import androidx.media2.MediaController2.PlaybackInfo;
import androidx.media2.MediaPlayerConnector.PlayerEventCallback;
import androidx.media2.MediaPlaylistAgent.PlaylistEventCallback;
import androidx.media2.MediaSession2.ErrorCode;
import androidx.media2.MediaSession2.MediaSession2Impl;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Executor;

class MediaSession2ImplBase implements MediaSession2Impl {
    private static final String DEFAULT_MEDIA_SESSION_TAG_PREFIX = "android.media.session2.id";
    private static final String DEFAULT_MEDIA_SESSION_TAG_DELIM = ".";

    static final String TAG = "MS2ImplBase";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private final Context mContext;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    private final MediaSessionCompat mSessionCompat;
    private final MediaSession2Stub mSession2Stub;
    private final MediaSessionLegacyStub mSessionLegacyStub;
    private final Executor mCallbackExecutor;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final SessionCallback mCallback;
    private final SessionToken2 mSessionToken;
    private final AudioManager mAudioManager;
    private final MediaPlayerConnector.PlayerEventCallback mPlayerEventCallback;
    private final MediaPlaylistAgent.PlaylistEventCallback mPlaylistEventCallback;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final AudioFocusHandler mAudioFocusHandler;
    private final MediaSession2 mInstance;
    private final PendingIntent mSessionActivity;
    private final MediaBrowserServiceCompat mBrowserServiceLegacyStub;

    final Object mLock = new Object();

    @GuardedBy("mLock")
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    PlaybackInfo mPlaybackInfo;

    @GuardedBy("mLock")
    private MediaPlayerConnector mPlayer;
    @GuardedBy("mLock")
    private MediaPlaylistAgent mPlaylistAgent;
    @GuardedBy("mLock")
    private SessionPlaylistAgentImplBase mSessionPlaylistAgent;
    @GuardedBy("mLock")
    private OnDataSourceMissingHelper mDsmHelper;

    MediaSession2ImplBase(MediaSession2 instance, Context context, String id,
            MediaPlayerConnector player, MediaPlaylistAgent playlistAgent,
            PendingIntent sessionActivity, Executor callbackExecutor, SessionCallback callback) {
        mContext = context;
        mInstance = instance;
        mHandlerThread = new HandlerThread("MediaController2_Thread");
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mSession2Stub = new MediaSession2Stub(this);
        mSessionActivity = sessionActivity;

        mCallback = callback;
        mCallbackExecutor = callbackExecutor;
        mAudioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        mPlayerEventCallback = new MyPlayerEventCallback(this);
        mPlaylistEventCallback = new MyPlaylistEventCallback(this);
        mAudioFocusHandler = new AudioFocusHandler(context, instance);

        // Infer type from the id and package name.
        String libraryService = getServiceName(context, MediaLibraryService2.SERVICE_INTERFACE, id);
        String sessionService = getServiceName(context, MediaSessionService2.SERVICE_INTERFACE, id);
        if (sessionService != null && libraryService != null) {
            throw new IllegalArgumentException("Ambiguous session type. Multiple"
                    + " session services define the same id=" + id);
        } else if (libraryService != null) {
            mSessionToken = new SessionToken2(new SessionToken2ImplBase(Process.myUid(),
                    TYPE_LIBRARY_SERVICE, context.getPackageName(), libraryService, id,
                    mSession2Stub));
        } else if (sessionService != null) {
            mSessionToken = new SessionToken2(new SessionToken2ImplBase(Process.myUid(),
                    TYPE_SESSION_SERVICE, context.getPackageName(), sessionService, id,
                    mSession2Stub));
        } else {
            mSessionToken = new SessionToken2(new SessionToken2ImplBase(Process.myUid(),
                    TYPE_SESSION, context.getPackageName(), null, id, mSession2Stub));
        }

        String sessionCompatId = TextUtils.join(DEFAULT_MEDIA_SESSION_TAG_DELIM,
                new String[] {DEFAULT_MEDIA_SESSION_TAG_PREFIX, id});

        mSessionCompat = new MediaSessionCompat(context, sessionCompatId, mSessionToken.toBundle());
        // NOTE: mSessionLegacyStub should be created after mSessionCompat created.
        mSessionLegacyStub = new MediaSessionLegacyStub(this);

        mSessionCompat.setSessionActivity(sessionActivity);
        mSessionCompat.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS
                | MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mSessionCompat.setActive(true);
        if (mSessionToken.getType() == TYPE_SESSION) {
            mBrowserServiceLegacyStub = null;
        } else {
            mBrowserServiceLegacyStub = createLegacyBrowserService(context, mSessionToken,
                    mSessionCompat.getSessionToken());
        }

        updatePlayer(player, playlistAgent);
        // Do this at the last moment. Otherwise commands through framework would be sent to this
        // session while initializing, and end up with unexpected situation.
        mSessionCompat.setCallback(mSessionLegacyStub, mHandler);
    }

    @Override
    public void updatePlayer(@NonNull MediaPlayerConnector player,
            @Nullable MediaPlaylistAgent playlistAgent) {
        if (player == null) {
            throw new IllegalArgumentException("player shouldn't be null");
        }

        synchronized (mLock) {
            if (player == mPlayer && playlistAgent == mPlaylistAgent) {
                return;
            }
        }

        final boolean isPlaybackInfoChanged;

        final MediaPlayerConnector oldPlayer;
        final MediaPlaylistAgent oldPlaylistAgent;
        final PlaybackInfo info = createPlaybackInfo(player);

        synchronized (mLock) {
            isPlaybackInfoChanged = !info.equals(mPlaybackInfo);

            oldPlayer = mPlayer;
            oldPlaylistAgent = mPlaylistAgent;
            mPlayer = player;

            if (playlistAgent == null) {
                mSessionPlaylistAgent = new SessionPlaylistAgentImplBase(this, mPlayer);
                if (mDsmHelper != null) {
                    mSessionPlaylistAgent.setOnDataSourceMissingHelper(mDsmHelper);
                }
                playlistAgent = mSessionPlaylistAgent;
            } else if (mSessionPlaylistAgent != null) {
                mSessionPlaylistAgent.setPlayer(mPlayer);
            }
            mPlaylistAgent = playlistAgent;
            mPlaybackInfo = info;

            if (oldPlayer != mPlayer) {
                if (oldPlayer != null) {
                    // Warning: Poorly implement player may ignore this
                    oldPlayer.unregisterPlayerEventCallback(mPlayerEventCallback);
                }
                // Registering callbacks is critical in case the player is being reused after reset.
                mPlayer.registerPlayerEventCallback(mCallbackExecutor, mPlayerEventCallback);
            }

            if (oldPlaylistAgent != mPlaylistAgent) {
                if (oldPlaylistAgent != null) {
                    // Warning: Poorly implement agent may ignore this
                    oldPlaylistAgent.unregisterPlaylistEventCallback(mPlaylistEventCallback);
                }
                mPlaylistAgent.registerPlaylistEventCallback(
                        mCallbackExecutor, mPlaylistEventCallback);
            }
        }

        if (oldPlayer == null) {
            // updatePlayerConnector() is called inside of the constructor.
            // There's no connected controllers at this moment, so just initialize session compat's
            // playback state. Otherwise, framework doesn't know whether this is ready to receive
            // media key event.
            mSessionCompat.setPlaybackState(createPlaybackStateCompat());
        } else {
            // updatePlayerConnector() is called by {@link MediaSession2#updatePlayer} to replace
            // player connector. Tell connected controllers about the changes in the player
            // connector, agent, and playback info.
            if (playlistAgent != oldPlaylistAgent) {
                // Update agent first. Otherwise current position may be changed off the current
                // media item's duration, and controller may consider it as a bug.
                notifyAgentUpdatedNotLocked(oldPlaylistAgent);
            }
            if (player != oldPlayer) {
                notifyPlayerUpdatedNotLocked(oldPlayer);
            }
            if (isPlaybackInfoChanged) {
                // Currently hasPlaybackInfo is always true, but check this in case that we're
                // adding PlaybackInfo#equals().
                notifyPlaybackInfoChangedNotLocked(info);
            }
        }

        if (player instanceof BaseRemoteMediaPlayerConnector) {
            final BaseRemoteMediaPlayerConnector remotePlayer =
                    (BaseRemoteMediaPlayerConnector) player;
            VolumeProviderCompat volumeProvider =
                    new VolumeProviderCompat(remotePlayer.getVolumeControlType(),
                            (int) remotePlayer.getMaxPlayerVolume(),
                            (int) remotePlayer.getPlayerVolume()) {
                        @Override
                        public void onSetVolumeTo(int volume) {
                            remotePlayer.setPlayerVolume(volume);
                        }

                        @Override
                        public void onAdjustVolume(int direction) {
                            remotePlayer.adjustPlayerVolume(direction);
                        }
                    };
            mSessionCompat.setPlaybackToRemote(volumeProvider);
        } else {
            int stream = getLegacyStreamType(player.getAudioAttributes());
            mSessionCompat.setPlaybackToLocal(stream);
        }
    }

    @NonNull PlaybackInfo createPlaybackInfo(@NonNull MediaPlayerConnector player) {
        final AudioAttributesCompat attrs = player.getAudioAttributes();

        if (!(player instanceof BaseRemoteMediaPlayerConnector)) {
            int stream = getLegacyStreamType(attrs);
            int controlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
            if (Build.VERSION.SDK_INT >= 21 && mAudioManager.isVolumeFixed()) {
                controlType = VolumeProviderCompat.VOLUME_CONTROL_FIXED;
            }
            return PlaybackInfo.createPlaybackInfo(
                    PlaybackInfo.PLAYBACK_TYPE_LOCAL,
                    attrs,
                    controlType,
                    mAudioManager.getStreamMaxVolume(stream),
                    mAudioManager.getStreamVolume(stream));
        } else {
            BaseRemoteMediaPlayerConnector remotePlayer = (BaseRemoteMediaPlayerConnector) player;
            return PlaybackInfo.createPlaybackInfo(
                    PlaybackInfo.PLAYBACK_TYPE_REMOTE,
                    attrs,
                    remotePlayer.getVolumeControlType(),
                    (int) remotePlayer.getMaxPlayerVolume(),
                    (int) remotePlayer.getPlayerVolume());
        }
    }

    private int getLegacyStreamType(@Nullable AudioAttributesCompat attrs) {
        int stream;
        if (attrs == null) {
            stream = AudioManager.STREAM_MUSIC;
        } else {
            stream = attrs.getLegacyStreamType();
            if (stream == AudioManager.USE_DEFAULT_STREAM_TYPE) {
                // Usually, AudioAttributesCompat#getLegacyStreamType() does not return
                // USE_DEFAULT_STREAM_TYPE unless the developer sets it with
                // AudioAttributesCompat.Builder#setLegacyStreamType().
                // But for safety, let's convert USE_DEFAULT_STREAM_TYPE to STREAM_MUSIC here.
                stream = AudioManager.STREAM_MUSIC;
            }
        }
        return stream;
    }

    @Override
    public void close() {
        synchronized (mLock) {
            if (mPlayer == null) {
                return;
            }
            mAudioFocusHandler.close();
            mPlayer.unregisterPlayerEventCallback(mPlayerEventCallback);
            mPlayer = null;
            mSessionCompat.release();
            notifyToAllControllers(new NotifyRunnable() {
                @Override
                public void run(ControllerCb callback) throws RemoteException {
                    callback.onDisconnected();
                }
            });
            mHandler.removeCallbacksAndMessages(null);
            if (mHandlerThread.isAlive()) {
                if (Build.VERSION.SDK_INT >= 18) {
                    mHandlerThread.quitSafely();
                } else {
                    mHandlerThread.quit();
                }
            }
        }
    }

    @Override
    public @Nullable MediaPlayerConnector getPlayer() {
        synchronized (mLock) {
            return mPlayer;
        }
    }

    @Override
    public @NonNull MediaPlaylistAgent getPlaylistAgent() {
        synchronized (mLock) {
            return mPlaylistAgent;
        }
    }

    @Override
    public @NonNull SessionToken2 getToken() {
        return mSessionToken;
    }

    @Override
    public @NonNull List<ControllerInfo> getConnectedControllers() {
        List<ControllerInfo> controllers = new ArrayList<>();
        controllers.addAll(mSession2Stub.getConnectedControllersManager()
                .getConnectedControllers());
        controllers.addAll(mSessionLegacyStub.getConnectedControllersManager()
                .getConnectedControllers());
        return controllers;
    }

    @Override
    public boolean isConnected(ControllerInfo controller) {
        if (controller.equals(mSessionLegacyStub.getControllersForAll())) {
            return true;
        }
        return mSession2Stub.getConnectedControllersManager().isConnected(controller)
                || mSessionLegacyStub.getConnectedControllersManager().isConnected(controller);
    }

    @Override
    public void setCustomLayout(@NonNull ControllerInfo controller,
            @NonNull final List<MediaSession2.CommandButton> layout) {
        if (controller == null) {
            throw new IllegalArgumentException("controller shouldn't be null");
        }
        if (layout == null) {
            throw new IllegalArgumentException("layout shouldn't be null");
        }
        notifyToController(controller, new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onCustomLayoutChanged(layout);
            }
        });
    }

    @Override
    public void setAllowedCommands(@NonNull ControllerInfo controller,
            @NonNull final SessionCommandGroup2 commands) {
        if (controller == null) {
            throw new IllegalArgumentException("controller shouldn't be null");
        }
        if (commands == null) {
            throw new IllegalArgumentException("commands shouldn't be null");
        }

        if (mSession2Stub.getConnectedControllersManager().isConnected(controller)) {
            mSession2Stub.getConnectedControllersManager()
                    .updateAllowedCommands(controller, commands);
            notifyToController(controller, new NotifyRunnable() {
                @Override
                public void run(ControllerCb callback) throws RemoteException {
                    callback.onAllowedCommandsChanged(commands);
                }
            });
        } else {
            mSessionLegacyStub.getConnectedControllersManager()
                    .updateAllowedCommands(controller, commands);
        }
    }

    @Override
    public void sendCustomCommand(@NonNull final SessionCommand2 command,
            @Nullable final Bundle args) {
        if (command == null) {
            throw new IllegalArgumentException("command shouldn't be null");
        }
        notifyToAllControllers(new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onCustomCommand(command, args, null);
            }
        });
    }

    @Override
    public void sendCustomCommand(@NonNull ControllerInfo controller,
            @NonNull final SessionCommand2 command, @Nullable final Bundle args,
            @Nullable final ResultReceiver receiver) {
        if (controller == null) {
            throw new IllegalArgumentException("controller shouldn't be null");
        }
        if (command == null) {
            throw new IllegalArgumentException("command shouldn't be null");
        }
        notifyToController(controller, new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onCustomCommand(command, args, receiver);
            }
        });
    }

    @Override
    public void play() {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (player != null) {
            if (mAudioFocusHandler.onPlayRequested()) {
                if (player.getPlayerState() == PLAYER_STATE_IDLE) {
                    // Note: Ideally audio focus should be requested only when play() is called,
                    // but it would be fine calling prepare() after the audio focus here because
                    // play() will be triggered immediately after.
                    player.prepare();
                }
                player.play();
            } else {
                Log.w(TAG, "play() wouldn't be called because of the failure in audio focus");
            }
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void pause() {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (player != null) {
            mAudioFocusHandler.onPauseRequested();
            player.pause();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void reset() {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (player != null) {
            player.reset();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void prepare() {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (player != null) {
            player.prepare();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void seekTo(long pos) {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (player != null) {
            player.seekTo(pos);
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void skipForward() {
        // To match with KEYCODE_MEDIA_SKIP_FORWARD
    }

    @Override
    public void skipBackward() {
        // To match with KEYCODE_MEDIA_SKIP_BACKWARD
    }

    @Override
    public void notifyError(@ErrorCode final int errorCode, @Nullable final Bundle extras) {
        notifyToAllControllers(new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onError(errorCode, extras);
            }
        });
    }

    @Override
    public void notifyRoutesInfoChanged(@NonNull ControllerInfo controller,
            @Nullable final List<Bundle> routes) {
        notifyToController(controller, new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onRoutesInfoChanged(routes);
            }
        });
    }

    @Override
    public @MediaPlayerConnector.PlayerState int getPlayerState() {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (player != null) {
            return player.getPlayerState();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return MediaPlayerConnector.PLAYER_STATE_ERROR;
    }

    @Override
    public long getCurrentPosition() {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (isInPlaybackState(player)) {
            return player.getCurrentPosition();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return MediaPlayerConnector.UNKNOWN_TIME;
    }

    @Override
    public long getDuration() {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (isInPlaybackState(player)) {
            // Note: This should be the same as
            // getCurrentMediaItem().getMetadata().getLong(METADATA_KEY_DURATION)
            return player.getDuration();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return MediaPlayerConnector.UNKNOWN_TIME;
    }

    @Override
    public long getBufferedPosition() {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (isInPlaybackState(player)) {
            return player.getBufferedPosition();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return MediaPlayerConnector.UNKNOWN_TIME;
    }

    @Override
    public @MediaPlayerConnector.BuffState int getBufferingState() {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (player != null) {
            return player.getBufferingState();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return BUFFERING_STATE_UNKNOWN;
    }

    @Override
    public float getPlaybackSpeed() {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (isInPlaybackState(player)) {
            return player.getPlaybackSpeed();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return 1.0f;
    }

    @Override
    public void setPlaybackSpeed(float speed) {
        MediaPlayerConnector player;
        synchronized (mLock) {
            player = mPlayer;
        }
        if (player != null) {
            player.setPlaybackSpeed(speed);
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void setOnDataSourceMissingHelper(
            @NonNull OnDataSourceMissingHelper helper) {
        if (helper == null) {
            throw new IllegalArgumentException("helper shouldn't be null");
        }
        synchronized (mLock) {
            mDsmHelper = helper;
            if (mSessionPlaylistAgent != null) {
                mSessionPlaylistAgent.setOnDataSourceMissingHelper(helper);
            }
        }
    }

    @Override
    public void clearOnDataSourceMissingHelper() {
        synchronized (mLock) {
            mDsmHelper = null;
            if (mSessionPlaylistAgent != null) {
                mSessionPlaylistAgent.clearOnDataSourceMissingHelper();
            }
        }
    }

    @Override
    public List<MediaItem2> getPlaylist() {
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            return agent.getPlaylist();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return null;
    }

    @Override
    public void setPlaylist(@NonNull List<MediaItem2> list, @Nullable MediaMetadata2 metadata) {
        if (list == null) {
            throw new IllegalArgumentException("list shouldn't be null");
        }
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            agent.setPlaylist(list, metadata);
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void skipToPlaylistItem(@NonNull MediaItem2 item) {
        if (item == null) {
            throw new IllegalArgumentException("item shouldn't be null");
        }
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            agent.skipToPlaylistItem(item);
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void skipToPreviousItem() {
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            agent.skipToPreviousItem();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void skipToNextItem() {
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            agent.skipToNextItem();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public MediaMetadata2 getPlaylistMetadata() {
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            return agent.getPlaylistMetadata();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return null;
    }

    @Override
    public void addPlaylistItem(int index, @NonNull MediaItem2 item) {
        if (index < 0) {
            throw new IllegalArgumentException("index shouldn't be negative");
        }
        if (item == null) {
            throw new IllegalArgumentException("item shouldn't be null");
        }
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            agent.addPlaylistItem(index, item);
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void removePlaylistItem(@NonNull MediaItem2 item) {
        if (item == null) {
            throw new IllegalArgumentException("item shouldn't be null");
        }
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            agent.removePlaylistItem(item);
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public void replacePlaylistItem(int index, @NonNull MediaItem2 item) {
        if (index < 0) {
            throw new IllegalArgumentException("index shouldn't be negative");
        }
        if (item == null) {
            throw new IllegalArgumentException("item shouldn't be null");
        }
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            agent.replacePlaylistItem(index, item);
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public MediaItem2 getCurrentMediaItem() {
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            return agent.getCurrentMediaItem();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return null;
    }

    @Override
    public void updatePlaylistMetadata(@Nullable MediaMetadata2 metadata) {
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            agent.updatePlaylistMetadata(metadata);
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public @MediaPlaylistAgent.RepeatMode int getRepeatMode() {
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            return agent.getRepeatMode();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return MediaPlaylistAgent.REPEAT_MODE_NONE;
    }

    @Override
    public void setRepeatMode(@MediaPlaylistAgent.RepeatMode int repeatMode) {
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            agent.setRepeatMode(repeatMode);
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    @Override
    public @MediaPlaylistAgent.ShuffleMode int getShuffleMode() {
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            return agent.getShuffleMode();
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
        return MediaPlaylistAgent.SHUFFLE_MODE_NONE;
    }

    @Override
    public void setShuffleMode(int shuffleMode) {
        MediaPlaylistAgent agent;
        synchronized (mLock) {
            agent = mPlaylistAgent;
        }
        if (agent != null) {
            agent.setShuffleMode(shuffleMode);
        } else if (DEBUG) {
            Log.d(TAG, "API calls after the close()", new IllegalStateException());
        }
    }

    ///////////////////////////////////////////////////
    // package private and private methods
    ///////////////////////////////////////////////////
    @Override
    public @NonNull MediaSession2 getInstance() {
        return mInstance;
    }

    @Override
    public @NonNull IBinder getSessionBinder() {
        return mSession2Stub.asBinder();
    }

    @Override
    public Context getContext() {
        return mContext;
    }

    @Override
    public Executor getCallbackExecutor() {
        return mCallbackExecutor;
    }

    @Override
    public SessionCallback getCallback() {
        return mCallback;
    }

    @Override
    public MediaSessionCompat getSessionCompat() {
        return mSessionCompat;
    }

    @Override
    public AudioFocusHandler getAudioFocusHandler() {
        return mAudioFocusHandler;
    }

    @Override
    public boolean isClosed() {
        return !mHandlerThread.isAlive();
    }

    @Override
    public PlaybackStateCompat createPlaybackStateCompat() {
        synchronized (mLock) {
            int state = MediaUtils2.convertToPlaybackStateCompatState(getPlayerState(),
                    getBufferingState());
            long allActions = PlaybackStateCompat.ACTION_STOP | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_REWIND
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                    | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_FAST_FORWARD
                    | PlaybackStateCompat.ACTION_SET_RATING
                    | PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_PLAY_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PLAY_FROM_SEARCH
                    | PlaybackStateCompat.ACTION_SKIP_TO_QUEUE_ITEM
                    | PlaybackStateCompat.ACTION_PLAY_FROM_URI | PlaybackStateCompat.ACTION_PREPARE
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_MEDIA_ID
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_SEARCH
                    | PlaybackStateCompat.ACTION_PREPARE_FROM_URI
                    | PlaybackStateCompat.ACTION_SET_REPEAT_MODE
                    | PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE
                    | PlaybackStateCompat.ACTION_SET_CAPTIONING_ENABLED;
            return new PlaybackStateCompat.Builder()
                    .setState(state, getCurrentPosition(), getPlaybackSpeed(),
                            SystemClock.elapsedRealtime())
                    .setActions(allActions)
                    .setBufferedPosition(getBufferedPosition())
                    .build();
        }
    }

    @Override
    public PlaybackInfo getPlaybackInfo() {
        synchronized (mLock) {
            return mPlaybackInfo;
        }
    }

    @Override
    public PendingIntent getSessionActivity() {
        return mSessionActivity;
    }

    MediaBrowserServiceCompat createLegacyBrowserService(Context context, SessionToken2 token,
            Token sessionToken) {
        switch (token.getType()) {
            case TYPE_SESSION:
                // Shouldn't be happen.
                return null;
            case TYPE_SESSION_SERVICE:
                return new MediaSessionService2LegacyStub(context, this, sessionToken);
        }
        return null;
    }

    @Override
    public IBinder getLegacyBrowserServiceBinder() {
        if (mBrowserServiceLegacyStub != null) {
            Intent intent = new Intent(MediaBrowserServiceCompat.SERVICE_INTERFACE);
            return mBrowserServiceLegacyStub.onBind(intent);
        }
        return null;
    }

    MediaBrowserServiceCompat getLegacyBrowserService() {
        return mBrowserServiceLegacyStub;
    }

    private static String getServiceName(Context context, String serviceAction, String id) {
        PackageManager manager = context.getPackageManager();
        Intent serviceIntent = new Intent(serviceAction);
        serviceIntent.setPackage(context.getPackageName());
        List<ResolveInfo> services = manager.queryIntentServices(serviceIntent,
                PackageManager.GET_META_DATA);
        String serviceName = null;
        if (services != null) {
            for (int i = 0; i < services.size(); i++) {
                String serviceId = SessionToken2.getSessionId(services.get(i));
                if (serviceId != null && TextUtils.equals(id, serviceId)) {
                    if (services.get(i).serviceInfo == null) {
                        continue;
                    }
                    if (serviceName != null) {
                        throw new IllegalArgumentException("Ambiguous session type. Multiple"
                                + " session services define the same id=" + id);
                    }
                    serviceName = services.get(i).serviceInfo.name;
                }
            }
        }
        return serviceName;
    }

    private boolean isInPlaybackState(@Nullable MediaPlayerConnector player) {
        return player != null
                && player.getPlayerState() != MediaPlayerConnector.PLAYER_STATE_IDLE
                && player.getPlayerState() != MediaPlayerConnector.PLAYER_STATE_ERROR;
    }

    private void notifyAgentUpdatedNotLocked(MediaPlaylistAgent oldAgent) {
        // Tells the playlist change first, to current item can change be notified with an item
        // within the playlist.
        List<MediaItem2> oldPlaylist = oldAgent.getPlaylist();
        final List<MediaItem2> newPlaylist = getPlaylist();
        if (!ObjectsCompat.equals(oldPlaylist, newPlaylist)) {
            notifyToAllControllers(new NotifyRunnable() {
                @Override
                public void run(ControllerCb callback) throws RemoteException {
                    callback.onPlaylistChanged(
                            newPlaylist, getPlaylistMetadata());
                }
            });
        } else {
            MediaMetadata2 oldMetadata = oldAgent.getPlaylistMetadata();
            final MediaMetadata2 newMetadata = getPlaylistMetadata();
            if (!ObjectsCompat.equals(oldMetadata, newMetadata)) {
                notifyToAllControllers(new NotifyRunnable() {
                    @Override
                    public void run(ControllerCb callback) throws RemoteException {
                        callback.onPlaylistMetadataChanged(newMetadata);
                    }
                });
            }
        }
        MediaItem2 oldCurrentItem = oldAgent.getCurrentMediaItem();
        final MediaItem2 newCurrentItem = getCurrentMediaItem();
        if (!ObjectsCompat.equals(oldCurrentItem, newCurrentItem)) {
            notifyToAllControllers(new NotifyRunnable() {
                @Override
                public void run(ControllerCb callback) throws RemoteException {
                    callback.onCurrentMediaItemChanged(newCurrentItem);
                }
            });
        }
        final int repeatMode = getRepeatMode();
        if (oldAgent.getRepeatMode() != repeatMode) {
            notifyToAllControllers(new NotifyRunnable() {
                @Override
                public void run(ControllerCb callback) throws RemoteException {
                    callback.onRepeatModeChanged(repeatMode);
                }
            });
        }
        final int shuffleMode = getShuffleMode();
        if (oldAgent.getShuffleMode() != shuffleMode) {
            notifyToAllControllers(new NotifyRunnable() {
                @Override
                public void run(ControllerCb callback) throws RemoteException {
                    callback.onShuffleModeChanged(shuffleMode);
                }
            });
        }
    }

    private void notifyPlayerUpdatedNotLocked(MediaPlayerConnector oldPlayer) {
        // Always forcefully send the player state and buffered state to send the current position
        // and buffered position.
        final long currentTimeMs = SystemClock.elapsedRealtime();
        final long positionMs = getCurrentPosition();
        final int playerState = getPlayerState();
        notifyToAllControllers(new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onPlayerStateChanged(currentTimeMs, positionMs, playerState);
            }
        });
        final MediaItem2 item = getCurrentMediaItem();
        if (item != null) {
            final int bufferingState = getBufferingState();
            final long bufferedPositionMs = getBufferedPosition();
            notifyToAllControllers(new NotifyRunnable() {
                @Override
                public void run(ControllerCb callback) throws RemoteException {
                    callback.onBufferingStateChanged(item, bufferingState, bufferedPositionMs);
                }
            });
        }
        final float speed = getPlaybackSpeed();
        if (speed != oldPlayer.getPlaybackSpeed()) {
            notifyToAllControllers(new NotifyRunnable() {
                @Override
                public void run(ControllerCb callback) throws RemoteException {
                    callback.onPlaybackSpeedChanged(currentTimeMs, positionMs, speed);
                }
            });
        }
        // Note: AudioInfo is updated outside of this API.
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void notifyPlaylistChangedOnExecutor(MediaPlaylistAgent playlistAgent,
            final List<MediaItem2> list, final MediaMetadata2 metadata) {
        synchronized (mLock) {
            if (playlistAgent != mPlaylistAgent) {
                // Ignore calls from the old agent.
                return;
            }
        }
        mCallback.onPlaylistChanged(mInstance, playlistAgent, list, metadata);
        notifyToAllControllers(new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onPlaylistChanged(list, metadata);
            }
        });
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void notifyPlaylistMetadataChangedOnExecutor(MediaPlaylistAgent playlistAgent,
            final MediaMetadata2 metadata) {
        synchronized (mLock) {
            if (playlistAgent != mPlaylistAgent) {
                // Ignore calls from the old agent.
                return;
            }
        }
        mCallback.onPlaylistMetadataChanged(mInstance, playlistAgent, metadata);
        notifyToAllControllers(new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onPlaylistMetadataChanged(metadata);
            }
        });
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void notifyRepeatModeChangedOnExecutor(MediaPlaylistAgent playlistAgent,
            final int repeatMode) {
        synchronized (mLock) {
            if (playlistAgent != mPlaylistAgent) {
                // Ignore calls from the old agent.
                return;
            }
        }
        mCallback.onRepeatModeChanged(mInstance, playlistAgent, repeatMode);
        notifyToAllControllers(new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onRepeatModeChanged(repeatMode);
            }
        });
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void notifyShuffleModeChangedOnExecutor(MediaPlaylistAgent playlistAgent,
            final int shuffleMode) {
        synchronized (mLock) {
            if (playlistAgent != mPlaylistAgent) {
                // Ignore calls from the old agent.
                return;
            }
        }
        mCallback.onShuffleModeChanged(mInstance, playlistAgent, shuffleMode);
        notifyToAllControllers(new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onShuffleModeChanged(shuffleMode);
            }
        });
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void notifyPlaybackInfoChangedNotLocked(final PlaybackInfo info) {
        notifyToAllControllers(new NotifyRunnable() {
            @Override
            public void run(ControllerCb callback) throws RemoteException {
                callback.onPlaybackInfoChanged(info);
            }
        });
    }

    void notifyToController(@NonNull final ControllerInfo controller,
            @NonNull NotifyRunnable runnable) {
        if (controller == null) {
            return;
        }
        if (!isConnected(controller)) {
            // Do not send command to an unconnected controller.
            return;
        }

        try {
            runnable.run(controller.getControllerCb());
        } catch (DeadObjectException e) {
            if (DEBUG) {
                Log.d(TAG, controller.toString() + " is gone", e);
            }
            // Note: Only removing from MediaSession2Stub would be fine for now, because other
            //       (legacy) stubs wouldn't throw DeadObjectException.
            mSession2Stub.getConnectedControllersManager().removeController(controller);
        } catch (RemoteException e) {
            // Currently it's TransactionTooLargeException or DeadSystemException.
            // We'd better to leave log for those cases because
            //   - TransactionTooLargeException means that we may need to fix our code.
            //     (e.g. add pagination or special way to deliver Bitmap)
            //   - DeadSystemException means that errors around it can be ignored.
            Log.w(TAG, "Exception in " + controller.toString(), e);
        }
    }

    void notifyToAllControllers(@NonNull NotifyRunnable runnable) {
        List<ControllerInfo> controllers =
                mSession2Stub.getConnectedControllersManager().getConnectedControllers();
        for (int i = 0; i < controllers.size(); i++) {
            notifyToController(controllers.get(i), runnable);
        }
        ControllerInfo controller = mSessionLegacyStub.getControllersForAll();
        notifyToController(controller, runnable);
    }

    ///////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////
    @FunctionalInterface
    interface NotifyRunnable {
        void run(ControllerCb callback) throws RemoteException;
    }

    private static class MyPlayerEventCallback extends PlayerEventCallback {
        private final WeakReference<MediaSession2ImplBase> mSession;

        MyPlayerEventCallback(MediaSession2ImplBase session) {
            mSession = new WeakReference<>(session);
        }

        @Override
        public void onCurrentDataSourceChanged(final MediaPlayerConnector player,
                final DataSourceDesc2 dsd) {
            final MediaSession2ImplBase session = getSession();
            if (session == null) {
                return;
            }
            session.getCallbackExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    final MediaItem2 item;
                    if (dsd == null) {
                        // This is OK because onCurrentDataSourceChanged() can be called with the
                        // null dsd, so onCurrentMediaItemChanged() can be as well.
                        item = null;
                    } else {
                        item = MyPlayerEventCallback.this.getMediaItem(session, dsd);
                        if (item == null) {
                            Log.w(TAG, "Cannot obtain media item from the dsd=" + dsd);
                            return;
                        }
                    }
                    session.getCallback().onCurrentMediaItemChanged(session.getInstance(), player,
                            item);
                    session.notifyToAllControllers(new NotifyRunnable() {
                        @Override
                        public void run(ControllerCb callback) throws RemoteException {
                            callback.onCurrentMediaItemChanged(item);
                        }
                    });
                }
            });
        }

        @Override
        public void onMediaPrepared(final MediaPlayerConnector mpb, final DataSourceDesc2 dsd) {
            final MediaSession2ImplBase session = getSession();
            if (session == null || dsd == null) {
                return;
            }
            session.getCallbackExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    MediaItem2 item = MyPlayerEventCallback.this.getMediaItem(session, dsd);
                    if (item == null) {
                        return;
                    }
                    if (item.equals(session.getCurrentMediaItem())) {
                        long duration = session.getDuration();
                        if (duration < 0) {
                            return;
                        }
                        MediaMetadata2 metadata = item.getMetadata();
                        if (metadata != null) {
                            if (!metadata.containsKey(MediaMetadata2.METADATA_KEY_DURATION)) {
                                metadata = new MediaMetadata2.Builder(metadata).putLong(
                                        MediaMetadata2.METADATA_KEY_DURATION, duration).build();
                            } else {
                                long durationFromMetadata =
                                        metadata.getLong(MediaMetadata2.METADATA_KEY_DURATION);
                                if (duration != durationFromMetadata) {
                                    // Warns developers about the mismatch. Don't log media item
                                    // here to keep metadata secure.
                                    Log.w(TAG, "duration mismatch for an item."
                                            + " duration from player=" + duration
                                            + " duration from metadata=" + durationFromMetadata
                                            + ". May be a timing issue?");
                                }
                                // Trust duration in the metadata set by developer.
                                // In theory, duration may differ if the current item has been
                                // changed before the getDuration(). So it's better not touch
                                // duration set by developer.
                                metadata = null;
                            }
                        } else {
                            metadata = new MediaMetadata2.Builder()
                                    .putLong(MediaMetadata2.METADATA_KEY_DURATION, duration)
                                    .putString(MediaMetadata2.METADATA_KEY_MEDIA_ID,
                                            item.getMediaId())
                                    .build();
                        }
                        if (metadata != null) {
                            item.setMetadata(metadata);
                            session.notifyToAllControllers(new NotifyRunnable() {
                                @Override
                                public void run(ControllerCb callback) throws RemoteException {
                                    callback.onPlaylistChanged(
                                            session.getPlaylist(), session.getPlaylistMetadata());
                                }
                            });
                        }
                    }
                    session.getCallback().onMediaPrepared(session.getInstance(), mpb, item);
                }
            });
        }

        @Override
        public void onPlayerStateChanged(final MediaPlayerConnector player, final int state) {
            final MediaSession2ImplBase session = getSession();
            if (session == null) {
                return;
            }
            session.getCallbackExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    // Order is important here. AudioFocusHandler should be called at the first
                    // for testing purpose.
                    session.mAudioFocusHandler.onPlayerStateChanged(state);
                    session.getCallback().onPlayerStateChanged(
                            session.getInstance(), player, state);
                    session.notifyToAllControllers(new NotifyRunnable() {
                        @Override
                        public void run(ControllerCb callback) throws RemoteException {
                            callback.onPlayerStateChanged(SystemClock.elapsedRealtime(),
                                    player.getCurrentPosition(), state);
                        }
                    });
                    // Currently we don't have a listener to player's AudioAttributes changes.
                    // Therefore, MediaController2 can get wrong AudioAttributes from
                    // MediaController2.getPlaybackInfo(). As a remedy, we update the
                    // AudioAttributes whenever player's state is changed.
                    PlaybackInfo newInfo = session.createPlaybackInfo(player);
                    PlaybackInfo oldInfo;
                    synchronized (session.mLock) {
                        oldInfo = session.mPlaybackInfo;
                        session.mPlaybackInfo = newInfo;
                    }
                    if (!ObjectsCompat.equals(
                            newInfo.getAudioAttributes(), oldInfo.getAudioAttributes())) {
                        session.notifyPlaybackInfoChangedNotLocked(newInfo);
                    }
                }
            });
        }

        @Override
        public void onBufferingStateChanged(final MediaPlayerConnector mpb,
                final DataSourceDesc2 dsd, final int state) {
            final MediaSession2ImplBase session = getSession();
            if (session == null || dsd == null) {
                return;
            }
            session.getCallbackExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    final MediaItem2 item = MyPlayerEventCallback.this.getMediaItem(session, dsd);
                    if (item == null) {
                        return;
                    }
                    session.getCallback().onBufferingStateChanged(
                            session.getInstance(), mpb, item, state);
                    session.notifyToAllControllers(new NotifyRunnable() {
                        @Override
                        public void run(ControllerCb callback) throws RemoteException {
                            callback.onBufferingStateChanged(item, state,
                                    mpb.getBufferedPosition());
                        }
                    });
                }
            });
        }

        @Override
        public void onPlaybackSpeedChanged(final MediaPlayerConnector mpb, final float speed) {
            final MediaSession2ImplBase session = getSession();
            if (session == null) {
                return;
            }
            session.getCallbackExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    session.getCallback().onPlaybackSpeedChanged(session.getInstance(), mpb, speed);
                    session.notifyToAllControllers(new NotifyRunnable() {
                        @Override
                        public void run(ControllerCb callback) throws RemoteException {
                            callback.onPlaybackSpeedChanged(SystemClock.elapsedRealtime(),
                                    session.getCurrentPosition(), speed);
                        }
                    });
                }
            });
        }

        @Override
        public void onSeekCompleted(final MediaPlayerConnector mpb, final long position) {
            final MediaSession2ImplBase session = getSession();
            if (session == null) {
                return;
            }
            session.getCallbackExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    session.getCallback().onSeekCompleted(session.getInstance(), mpb, position);
                    session.notifyToAllControllers(new NotifyRunnable() {
                        @Override
                        public void run(ControllerCb callback) throws RemoteException {
                            callback.onSeekCompleted(SystemClock.elapsedRealtime(),
                                    session.getCurrentPosition(), position);
                        }
                    });
                }
            });
        }

        private MediaSession2ImplBase getSession() {
            final MediaSession2ImplBase session = mSession.get();
            if (session == null && DEBUG) {
                Log.d(TAG, "Session is closed", new IllegalStateException());
            }
            return session;
        }

        MediaItem2 getMediaItem(MediaSession2ImplBase session, DataSourceDesc2 dsd) {
            MediaPlaylistAgent agent = session.getPlaylistAgent();
            if (agent == null) {
                if (DEBUG) {
                    Log.d(TAG, "Session is closed", new IllegalStateException());
                }
                return null;
            }
            MediaItem2 item = agent.getMediaItem(dsd);
            if (item == null) {
                if (DEBUG) {
                    Log.d(TAG, "Could not find matching item for dsd=" + dsd,
                            new NoSuchElementException());
                }
            }
            return item;
        }
    }

    private static class MyPlaylistEventCallback extends PlaylistEventCallback {
        private final WeakReference<MediaSession2ImplBase> mSession;

        MyPlaylistEventCallback(MediaSession2ImplBase session) {
            mSession = new WeakReference<>(session);
        }

        @Override
        public void onPlaylistChanged(MediaPlaylistAgent playlistAgent, List<MediaItem2> list,
                MediaMetadata2 metadata) {
            final MediaSession2ImplBase session = mSession.get();
            if (session == null) {
                return;
            }
            session.notifyPlaylistChangedOnExecutor(playlistAgent, list, metadata);
        }

        @Override
        public void onPlaylistMetadataChanged(MediaPlaylistAgent playlistAgent,
                MediaMetadata2 metadata) {
            final MediaSession2ImplBase session = mSession.get();
            if (session == null) {
                return;
            }
            session.notifyPlaylistMetadataChangedOnExecutor(playlistAgent, metadata);
        }

        @Override
        public void onRepeatModeChanged(MediaPlaylistAgent playlistAgent, int repeatMode) {
            final MediaSession2ImplBase session = mSession.get();
            if (session == null) {
                return;
            }
            session.notifyRepeatModeChangedOnExecutor(playlistAgent, repeatMode);
        }

        @Override
        public void onShuffleModeChanged(MediaPlaylistAgent playlistAgent, int shuffleMode) {
            final MediaSession2ImplBase session = mSession.get();
            if (session == null) {
                return;
            }
            session.notifyShuffleModeChangedOnExecutor(playlistAgent, shuffleMode);
        }
    }
}

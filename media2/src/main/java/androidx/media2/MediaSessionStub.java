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

import static androidx.media2.BaseResult.RESULT_CODE_UNKNOWN_ERROR;
import static androidx.media2.MediaSession.SessionResult.RESULT_CODE_BAD_VALUE;
import static androidx.media2.MediaSession.SessionResult.RESULT_CODE_INVALID_STATE;
import static androidx.media2.MediaSession.SessionResult.RESULT_CODE_SUCCESS;
import static androidx.media2.MediaUtils.DIRECT_EXECUTOR;
import static androidx.media2.SessionCommand.COMMAND_CODE_CUSTOM;
import static androidx.media2.SessionCommand.COMMAND_VERSION_CURRENT;

import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.support.v4.media.session.MediaSessionCompat;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.MediaSessionManager;
import androidx.media2.MediaController.PlaybackInfo;
import androidx.media2.MediaLibraryService.LibraryParams;
import androidx.media2.MediaLibraryService.LibraryResult;
import androidx.media2.MediaLibraryService.MediaLibrarySession;
import androidx.media2.MediaLibraryService.MediaLibrarySession.MediaLibrarySessionImpl;
import androidx.media2.MediaSession.CommandButton;
import androidx.media2.MediaSession.ControllerCb;
import androidx.media2.MediaSession.ControllerInfo;
import androidx.media2.MediaSession.MediaSessionImpl;
import androidx.media2.MediaSession.SessionResult;
import androidx.media2.MediaSessionImplBase.PlayerTask;
import androidx.media2.SessionCommand.CommandCode;
import androidx.media2.SessionPlayer.PlayerResult;
import androidx.versionedparcelable.ParcelImpl;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Handles incoming commands from {@link MediaController} and {@link MediaBrowser}
 * to both {@link MediaSession} and {@link MediaLibrarySession}.
 * <p>
 * We cannot create a subclass for library service specific function because AIDL doesn't support
 * subclassing and it's generated stub class is an abstract class.
 */
class MediaSessionStub extends IMediaSession.Stub {
    private static final String TAG = "MediaSessionStub";
    private static final boolean DEBUG = true; //Log.isLoggable(TAG, Log.DEBUG);
    private static final boolean RETHROW_EXCEPTION = true;

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static final SparseArray<SessionCommand> sCommandsForOnCommandRequest =
            new SparseArray<>();

    static {
        SessionCommandGroup group = new SessionCommandGroup.Builder()
                .addAllPlayerCommands(COMMAND_VERSION_CURRENT)
                .addAllVolumeCommands(COMMAND_VERSION_CURRENT)
                .build();
        Set<SessionCommand> commands = group.getCommands();
        for (SessionCommand command : commands) {
            sCommandsForOnCommandRequest.append(command.getCommandCode(), command);
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final ConnectedControllersManager<IBinder> mConnectedControllersManager;

    final Object mLock = new Object();

    final MediaSessionImpl mSessionImpl;
    final Context mContext;
    final MediaSessionManager mSessionManager;

    @GuardedBy("mLock")
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final Set<IBinder> mConnectingControllers = new HashSet<>();

    MediaSessionStub(MediaSessionImpl sessionImpl) {
        mSessionImpl = sessionImpl;
        mContext = mSessionImpl.getContext();
        mSessionManager = MediaSessionManager.getSessionManager(mContext);
        mConnectedControllersManager = new ConnectedControllersManager<>(sessionImpl);
    }

    ConnectedControllersManager<IBinder> getConnectedControllersManager() {
        return mConnectedControllersManager;
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static void sendSessionResult(@NonNull ControllerInfo controller, int seq,
            int resultCode) {
        sendSessionResult(controller, seq, new SessionResult(resultCode));
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static void sendSessionResult(@NonNull ControllerInfo controller, int seq,
            @NonNull SessionResult result) {
        try {
            controller.getControllerCb().onSessionResult(seq, result);
        } catch (RemoteException e) {
            Log.w(TAG, "Exception in " + controller.toString(), e);
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static void sendPlayerResult(@NonNull ControllerInfo controller, int seq,
            @NonNull PlayerResult result) {
        try {
            controller.getControllerCb().onPlayerResult(seq, result);
        } catch (RemoteException e) {
            Log.w(TAG, "Exception in " + controller.toString(), e);
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static void sendLibraryResult(@NonNull ControllerInfo controller, int seq,
            int resultCode) {
        sendLibraryResult(controller, seq, new LibraryResult(resultCode));
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    static void sendLibraryResult(@NonNull ControllerInfo controller, int seq,
            @NonNull LibraryResult result) {
        try {
            controller.getControllerCb().onLibraryResult(seq, result);
        } catch (RemoteException e) {
            Log.w(TAG, "Exception in " + controller.toString(), e);
        }
    }

    private void dispatchSessionTask(@NonNull IMediaController caller, int seq,
            final @CommandCode int commandCode,
            final @NonNull SessionTask task) {
        dispatchSessionTaskInternal(caller, seq, null, commandCode, task);
    }

    private void dispatchSessionTask(@NonNull IMediaController caller, int seq,
            @NonNull final SessionCommand sessionCommand,
            @NonNull final SessionTask task) {
        dispatchSessionTaskInternal(caller, seq, sessionCommand, COMMAND_CODE_CUSTOM, task);
    }

    private void dispatchSessionTaskInternal(@NonNull IMediaController caller, final int seq,
            final @Nullable SessionCommand sessionCommand,
            final @CommandCode int commandCode,
            final @NonNull SessionTask task) {
        final long token = Binder.clearCallingIdentity();
        try {
            final ControllerInfo controller = mConnectedControllersManager.getController(
                    caller.asBinder());
            if (mSessionImpl.isClosed() || controller == null) {
                return;
            }
            mSessionImpl.getCallbackExecutor().execute(new Runnable() {
                @Override
                public void run() {
                    if (!mConnectedControllersManager.isConnected(controller)) {
                        return;
                    }
                    SessionCommand commandForOnCommandRequest;
                    if (sessionCommand != null) {
                        if (!mConnectedControllersManager.isAllowedCommand(
                                controller, sessionCommand)) {
                            if (DEBUG) {
                                Log.d(TAG, "Command (" + sessionCommand + ") from "
                                        + controller + " isn't allowed.");
                            }
                            return;
                        }
                        commandForOnCommandRequest = sCommandsForOnCommandRequest.get(
                                sessionCommand.getCommandCode());
                    } else {
                        if (!mConnectedControllersManager.isAllowedCommand(controller,
                                commandCode)) {
                            if (DEBUG) {
                                Log.d(TAG, "Command (" + commandCode + ") from "
                                        + controller + " isn't allowed.");
                            }
                            return;
                        }
                        commandForOnCommandRequest = sCommandsForOnCommandRequest.get(
                                commandCode);
                    }
                    try {
                        if (commandForOnCommandRequest != null) {
                            int resultCode = mSessionImpl.getCallback().onCommandRequest(
                                    mSessionImpl.getInstance(), controller,
                                    commandForOnCommandRequest);
                            if (resultCode != RESULT_CODE_SUCCESS) {
                                // Don't run rejected command.
                                if (DEBUG) {
                                    Log.d(TAG, "Command (" + commandForOnCommandRequest
                                            + ") from " + controller + " was rejected by "
                                            + mSessionImpl + ", code=" + resultCode);
                                }
                                sendSessionResult(controller, seq, resultCode);
                                return;
                            }
                        }
                        if (task instanceof SessionPlayerTask) {
                            final ListenableFuture<PlayerResult> future =
                                    ((SessionPlayerTask) task).run(controller);
                            if (future == null) {
                                throw new RuntimeException("SessionPlayer has returned null,"
                                        + " commandCode=" + commandCode);
                            } else {
                                future.addListener(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            sendPlayerResult(controller, seq,
                                                    future.get(0, TimeUnit.MILLISECONDS));
                                        } catch (Exception e) {
                                            Log.w(TAG, "Cannot obtain PlayerResult after the"
                                                    + " command is finished", e);
                                            sendSessionResult(controller, seq,
                                                    RESULT_CODE_INVALID_STATE);
                                        }
                                    }
                                }, DIRECT_EXECUTOR);
                            }
                        } else if (task instanceof SessionCallbackTask) {
                            final Object result = ((SessionCallbackTask) task).run(controller);
                            if (result == null) {
                                throw new RuntimeException("SessionCallback has returned null,"
                                        + " commandCode=" + commandCode);
                            } else if (result instanceof Integer) {
                                sendSessionResult(controller, seq, (Integer) result);
                            } else if (result instanceof SessionResult) {
                                sendSessionResult(controller, seq, (SessionResult) result);
                            } else if (DEBUG) {
                                throw new RuntimeException("Unexpected return type " + result
                                        + ". Fix bug");
                            }
                        } else if (task instanceof LibrarySessionCallbackTask) {
                            final Object result = ((LibrarySessionCallbackTask) task).run(
                                    controller);
                            if (result == null) {
                                throw new RuntimeException("LibrarySessionCallback has returned"
                                        + " null, commandCode=" + commandCode);
                            } else if (result instanceof Integer) {
                                sendLibraryResult(controller, seq, (Integer) result);
                            } else if (result instanceof LibraryResult) {
                                sendLibraryResult(controller, seq, (LibraryResult) result);
                            } else if (DEBUG) {
                                throw new RuntimeException("Unexpected return type " + result
                                        + ". Fix bug");
                            }
                        } else if (DEBUG) {
                            throw new RuntimeException("Unknown task " + task + ". Fix bug");
                        }
                    } catch (RemoteException e) {
                        // Currently it's TransactionTooLargeException or DeadSystemException.
                        // We'd better to leave log for those cases because
                        //   - TransactionTooLargeException means that we may need to fix our code.
                        //     (e.g. add pagination or special way to deliver Bitmap)
                        //   - DeadSystemException means that errors around it can be ignored.
                        Log.w(TAG, "Exception in " + controller.toString(), e);
                    } catch (Exception e) {
                        // Any random exception may be happen inside
                        // of the session player / callback.

                        if (RETHROW_EXCEPTION) {
                            throw e;
                        }
                        if (task instanceof PlayerTask) {
                            sendPlayerResult(controller, seq,
                                    new PlayerResult(PlayerResult.RESULT_CODE_UNKNOWN_ERROR, null));
                        } else if (task instanceof SessionCallbackTask) {
                            sendSessionResult(controller, seq,
                                    SessionResult.RESULT_CODE_UNKNOWN_ERROR);
                        } else if (task instanceof LibrarySessionCallbackTask) {
                            sendLibraryResult(controller, seq,
                                    LibraryResult.RESULT_CODE_UNKNOWN_ERROR);
                        }
                    }
                }
            });
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void dispatchLibrarySessionTask(@NonNull IMediaController caller, int seq,
            @CommandCode final int commandCode, final @NonNull LibrarySessionCallbackTask task) {
        if (!(mSessionImpl instanceof MediaLibrarySessionImpl)) {
            throw new RuntimeException("MediaSession cannot handle MediaLibrarySession command");
        }
        dispatchSessionTaskInternal(caller, seq, null, commandCode, task);
    }

    void connect(final IMediaController caller, final String callingPackage, final int pid,
            final int uid) {
        MediaSessionManager.RemoteUserInfo
                remoteUserInfo = new MediaSessionManager.RemoteUserInfo(callingPackage, pid, uid);
        final ControllerInfo controllerInfo = new ControllerInfo(remoteUserInfo,
                mSessionManager.isTrustedForMediaControl(remoteUserInfo),
                new Controller2Cb(caller));
        mSessionImpl.getCallbackExecutor().execute(new Runnable() {
            @Override
            public void run() {
                if (mSessionImpl.isClosed()) {
                    return;
                }
                final IBinder callbackBinder = ((Controller2Cb) controllerInfo.getControllerCb())
                        .getCallbackBinder();
                synchronized (mLock) {
                    // Keep connecting controllers.
                    // This helps sessions to call APIs in the onConnect()
                    // (e.g. setCustomLayout()) instead of pending them.
                    mConnectingControllers.add(callbackBinder);
                }
                SessionCommandGroup allowedCommands = mSessionImpl.getCallback().onConnect(
                        mSessionImpl.getInstance(), controllerInfo);
                // Don't reject connection for the request from trusted app.
                // Otherwise server will fail to retrieve session's information to dispatch
                // media keys to.
                boolean accept = allowedCommands != null || controllerInfo.isTrusted();
                if (accept) {
                    if (DEBUG) {
                        Log.d(TAG, "Accepting connection, controllerInfo=" + controllerInfo
                                + " allowedCommands=" + allowedCommands);
                    }
                    if (allowedCommands == null) {
                        // For trusted apps, send non-null allowed commands to keep
                        // connection.
                        allowedCommands = new SessionCommandGroup();
                    }
                    synchronized (mLock) {
                        mConnectingControllers.remove(callbackBinder);
                        mConnectedControllersManager.addController(
                                callbackBinder, controllerInfo, allowedCommands);
                    }
                    // If connection is accepted, notify the current state to the controller.
                    // It's needed because we cannot call synchronous calls between
                    // session/controller.
                    // Note: We're doing this after the onConnectionChanged(), but there's no
                    //       guarantee that events here are notified after the onConnected()
                    //       because IMediaController is oneway (i.e. async call) and Stub will
                    //       use thread poll for incoming calls.
                    final int playerState = mSessionImpl.getPlayerState();
                    final ParcelImpl currentItem = MediaUtils.toParcelable(
                            mSessionImpl.getCurrentMediaItem());
                    final long positionEventTimeMs = SystemClock.elapsedRealtime();
                    final long positionMs = mSessionImpl.getCurrentPosition();
                    final float playbackSpeed = mSessionImpl.getPlaybackSpeed();
                    final long bufferedPositionMs = mSessionImpl.getBufferedPosition();
                    final ParcelImpl playbackInfo =
                            MediaUtils.toParcelable(mSessionImpl.getPlaybackInfo());
                    final int repeatMode = mSessionImpl.getRepeatMode();
                    final int shuffleMode = mSessionImpl.getShuffleMode();
                    final PendingIntent sessionActivity = mSessionImpl.getSessionActivity();
                    final List<MediaItem> playlist =
                            allowedCommands.hasCommand(
                                    SessionCommand.COMMAND_CODE_PLAYER_GET_PLAYLIST)
                                    ? mSessionImpl.getPlaylist() : null;
                    final ParcelImplListSlice playlistSlice =
                            MediaUtils.convertMediaItemListToParcelImplListSlice(playlist);

                    // Double check if session is still there, because close() can be called in
                    // another thread.
                    if (mSessionImpl.isClosed()) {
                        return;
                    }
                    try {
                        caller.onConnected(MediaSessionStub.this,
                                MediaUtils.toParcelable(allowedCommands),
                                playerState, currentItem, positionEventTimeMs, positionMs,
                                playbackSpeed, bufferedPositionMs, playbackInfo, repeatMode,
                                shuffleMode, playlistSlice, sessionActivity);
                    } catch (RemoteException e) {
                        // Controller may be died prematurely.
                    }
                } else {
                    synchronized (mLock) {
                        mConnectingControllers.remove(callbackBinder);
                    }
                    if (DEBUG) {
                        Log.d(TAG, "Rejecting connection, controllerInfo=" + controllerInfo);
                    }
                    try {
                        caller.onDisconnected();
                    } catch (RemoteException e) {
                        // Controller may be died prematurely.
                        // Not an issue because we'll ignore it anyway.
                    }
                }
            }
        });
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    @Nullable
    MediaItem convertMediaItemOnExecutor(ControllerInfo controller,
            @NonNull String mediaId) {
        if (TextUtils.isEmpty(mediaId)) {
            Log.w(TAG, "Media ID shouldn't be null");
            return null;
        }
        MediaItem newItem = mSessionImpl.getCallback().onCreateMediaItem(
                mSessionImpl.getInstance(), controller, mediaId);
        if (newItem == null) {
            Log.w(TAG, "onCreateMediaItem(mediaId=" + mediaId + ") returned null. Ignoring");
        } else if (newItem.getMetadata() == null
                || !TextUtils.equals(mediaId,
                        newItem.getMetadata().getString(MediaMetadata.METADATA_KEY_MEDIA_ID))) {
            throw new RuntimeException("onCreateMediaItem(mediaId=" + mediaId + "): media ID in the"
                    + " returned media item should match");
        }
        return newItem;
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    // AIDL methods for session overrides
    //////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void connect(final IMediaController caller, int seq, final String callingPackage)
            throws RuntimeException {
        if (caller == null || TextUtils.isEmpty(callingPackage)) {
            return;
        }
        final int pid = Binder.getCallingPid();
        final int uid = Binder.getCallingUid();
        final long token = Binder.clearCallingIdentity();
        try {
            connect(caller, callingPackage, pid, uid);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void release(final IMediaController caller, int seq) throws RemoteException {
        if (caller == null) {
            return;
        }
        final long token = Binder.clearCallingIdentity();
        try {
            mConnectedControllersManager.removeController(caller.asBinder());
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void onControllerResult(final IMediaController caller, int seq,
            final ParcelImpl controllerResult) {
        if (caller == null || controllerResult == null) {
            return;
        }
        final long token = Binder.clearCallingIdentity();
        try {
            SequencedFutureManager manager = mConnectedControllersManager.getSequencedFutureManager(
                    caller.asBinder());
            if (manager == null) {
                return;
            }
            MediaController.ControllerResult result = MediaUtils.fromParcelable(controllerResult);
            manager.setFutureResult(seq, SessionResult.from(result));
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void setVolumeTo(final IMediaController caller, int seq, final int value,
            final int flags) throws RuntimeException {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_VOLUME_SET_VOLUME,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        MediaSessionCompat sessionCompat = mSessionImpl.getSessionCompat();
                        if (sessionCompat != null) {
                            sessionCompat.getController().setVolumeTo(value, flags);
                        }
                        // TODO: Handle remote player case
                        return RESULT_CODE_SUCCESS;
                    }
                });
    }

    @Override
    public void adjustVolume(IMediaController caller, int seq, final int direction,
            final int flags) throws RuntimeException {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_VOLUME_ADJUST_VOLUME,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        MediaSessionCompat sessionCompat = mSessionImpl.getSessionCompat();
                        if (sessionCompat != null) {
                            sessionCompat.getController().adjustVolume(direction, flags);
                        }
                        // TODO: Handle remote player case
                        return RESULT_CODE_SUCCESS;
                    }
                });
    }

    @Override
    public void play(IMediaController caller, int seq) throws RuntimeException {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_PLAY,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.play();
                    }
                });
    }

    @Override
    public void pause(IMediaController caller, int seq) throws RuntimeException {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_PAUSE,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.pause();
                    }
                });
    }

    @Override
    public void prepare(IMediaController caller, int seq) throws RuntimeException {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_PREPARE,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.prepare();
                    }
                });
    }

    @Override
    public void fastForward(IMediaController caller, int seq) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        return mSessionImpl.getCallback().onFastForward(
                                mSessionImpl.getInstance(), controller);
                    }
                });
    }

    @Override
    public void rewind(IMediaController caller, int seq) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_SESSION_REWIND,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        return mSessionImpl.getCallback().onRewind(
                                mSessionImpl.getInstance(), controller);
                    }
                });
    }

    @Override
    public void skipForward(IMediaController caller, int seq) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_SESSION_SKIP_FORWARD,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        return mSessionImpl.getCallback().onSkipForward(
                                mSessionImpl.getInstance(), controller);
                    }
                });
    }

    @Override
    public void skipBackward(IMediaController caller, int seq) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_SESSION_SKIP_BACKWARD,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        return mSessionImpl.getCallback().onSkipBackward(
                                mSessionImpl.getInstance(), controller);
                    }
                });
    }

    @Override
    public void seekTo(IMediaController caller, int seq, final long pos) throws RuntimeException {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_SEEK_TO,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.seekTo(pos);
                    }
                });
    }

    @Override
    public void onCustomCommand(final IMediaController caller, final int seq,
            final ParcelImpl command, final Bundle args) {
        if (caller == null || command == null) {
            return;
        }
        final SessionCommand sessionCommand = MediaUtils.fromParcelable(command);
        dispatchSessionTask(caller, seq, sessionCommand, new SessionCallbackTask<SessionResult>() {
            @Override
            public SessionResult run(final ControllerInfo controller) {
                SessionResult result = mSessionImpl.getCallback().onCustomCommand(
                        mSessionImpl.getInstance(), controller, sessionCommand, args);
                if (result == null) {
                    if (RETHROW_EXCEPTION) {
                        throw new RuntimeException("SessionCallback#onCustomCommand has returned"
                                + " null, command=" + sessionCommand);
                    } else {
                        result = new SessionResult(RESULT_CODE_UNKNOWN_ERROR);
                    }
                }
                return result;
            }
        });
    }

    @Override
    public void prepareFromUri(final IMediaController caller, int seq, final Uri uri,
            final Bundle extras) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_SESSION_PREPARE_FROM_URI,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        if (uri == null) {
                            Log.w(TAG, "prepareFromUri(): Ignoring null uri from " + controller);
                            return RESULT_CODE_BAD_VALUE;
                        }
                        return mSessionImpl.getCallback().onPrepareFromUri(
                                mSessionImpl.getInstance(), controller, uri, extras);
                    }
                });
    }

    @Override
    public void prepareFromSearch(final IMediaController caller, int seq, final String query,
            final Bundle extras) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_SESSION_PREPARE_FROM_SEARCH,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(query)) {
                            Log.w(TAG, "prepareFromSearch(): Ignoring empty query from "
                                    + controller);
                            return RESULT_CODE_BAD_VALUE;
                        }
                        return mSessionImpl.getCallback().onPrepareFromSearch(
                                mSessionImpl.getInstance(), controller, query, extras);
                    }
                });
    }

    @Override
    public void prepareFromMediaId(final IMediaController caller, int seq, final String mediaId,
            final Bundle extras) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq,
                SessionCommand.COMMAND_CODE_SESSION_PREPARE_FROM_MEDIA_ID,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(mediaId)) {
                            Log.w(TAG, "prepareFromMediaId(): Ignoring empty mediaId from "
                                    + controller);
                            return RESULT_CODE_BAD_VALUE;
                        }
                        return mSessionImpl.getCallback().onPrepareFromMediaId(
                                mSessionImpl.getInstance(), controller, mediaId, extras);
                    }
                });
    }

    @Override
    public void playFromUri(final IMediaController caller, int seq, final Uri uri,
            final Bundle extras) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_SESSION_PLAY_FROM_URI,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        if (uri == null) {
                            Log.w(TAG, "playFromUri(): Ignoring null uri from " + controller);
                            return RESULT_CODE_BAD_VALUE;
                        }
                        return mSessionImpl.getCallback().onPlayFromUri(
                                mSessionImpl.getInstance(), controller, uri, extras);
                    }
                });
    }

    @Override
    public void playFromSearch(final IMediaController caller, int seq, final String query,
            final Bundle extras) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_SESSION_PLAY_FROM_SEARCH,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(query)) {
                            Log.w(TAG, "playFromSearch(): Ignoring empty query from " + controller);
                            return RESULT_CODE_BAD_VALUE;
                        }
                        return mSessionImpl.getCallback().onPlayFromSearch(
                                mSessionImpl.getInstance(), controller, query, extras);
                    }
                });
    }

    @Override
    public void playFromMediaId(final IMediaController caller, int seq, final String mediaId,
            final Bundle extras) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_SESSION_PLAY_FROM_MEDIA_ID,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(mediaId)) {
                            Log.w(TAG, "playFromMediaId(): Ignoring empty mediaId from "
                                    + controller);
                            return RESULT_CODE_BAD_VALUE;
                        }
                        return mSessionImpl.getCallback().onPlayFromMediaId(
                                mSessionImpl.getInstance(), controller, mediaId, extras);
                    }
                });
    }

    @Override
    public void setRating(final IMediaController caller, int seq, final String mediaId,
            final ParcelImpl ratingParcelable) {
        if (caller == null || ratingParcelable == null) {
            return;
        }
        final Rating rating = MediaUtils.fromParcelable(ratingParcelable);
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_SESSION_SET_RATING,
                new SessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(mediaId)) {
                            Log.w(TAG, "setRating(): Ignoring empty mediaId from " + controller);
                            return RESULT_CODE_BAD_VALUE;
                        }
                        if (rating == null) {
                            Log.w(TAG, "setRating(): Ignoring null rating from " + controller);
                            return RESULT_CODE_BAD_VALUE;
                        }
                        return mSessionImpl.getCallback().onSetRating(
                                mSessionImpl.getInstance(), controller, mediaId, rating);
                    }
                });
    }

    @Override
    public void setPlaybackSpeed(final IMediaController caller, int seq, final float speed) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_SET_SPEED,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.setPlaybackSpeed(speed);
                    }
                });
    }

    @Override
    public void setPlaylist(final IMediaController caller, int seq, final List<String> playlist,
            final ParcelImpl metadata) {
        if (caller == null || metadata == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_SET_PLAYLIST,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        if (playlist == null) {
                            Log.w(TAG, "setPlaylist(): Ignoring null playlist from " + controller);
                            return PlayerResult.createFuture(RESULT_CODE_BAD_VALUE);
                        }
                        List<MediaItem> list = new ArrayList<>();
                        for (int i = 0; i < playlist.size(); i++) {
                            MediaItem item = convertMediaItemOnExecutor(controller,
                                    playlist.get(i));
                            if (item != null) {
                                list.add(item);
                            }
                        }
                        return mSessionImpl.setPlaylist(list,
                                (MediaMetadata) MediaUtils.fromParcelable(metadata));
                    }
                });
    }

    @Override
    public void setMediaItem(final IMediaController caller, int seq, final String mediaId) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_SET_MEDIA_ITEM,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(mediaId)) {
                            Log.w(TAG, "setMediaItem(): Ignoring empty mediaId from " + controller);
                            return PlayerResult.createFuture(RESULT_CODE_BAD_VALUE);
                        }
                        MediaItem item = convertMediaItemOnExecutor(controller, mediaId);
                        if (item == null) {
                            return PlayerResult.createFuture(RESULT_CODE_BAD_VALUE);
                        }
                        return mSessionImpl.setMediaItem(item);
                    }
                });
    }

    @Override
    public void updatePlaylistMetadata(final IMediaController caller, int seq,
            final ParcelImpl metadata) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.updatePlaylistMetadata(
                                (MediaMetadata) MediaUtils.fromParcelable(metadata));
                    }
                });
    }

    @Override
    public void addPlaylistItem(IMediaController caller, int seq, final int index,
            final String mediaId) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(mediaId)) {
                            Log.w(TAG, "addPlaylistItem(): Ignoring empty mediaId from "
                                    + controller);
                            return PlayerResult.createFuture(RESULT_CODE_BAD_VALUE);
                        }
                        MediaItem item = convertMediaItemOnExecutor(controller, mediaId);
                        if (item == null) {
                            return PlayerResult.createFuture(RESULT_CODE_BAD_VALUE);
                        }
                        return mSessionImpl.addPlaylistItem(index, item);
                    }
                });
    }

    @Override
    public void removePlaylistItem(IMediaController caller, int seq, final int index) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.removePlaylistItem(index);
                    }
                });
    }

    @Override
    public void replacePlaylistItem(IMediaController caller, int seq, final int index,
            final String mediaId) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(mediaId)) {
                            Log.w(TAG, "replacePlaylistItem(): Ignoring empty mediaId from "
                                    + controller);
                            return PlayerResult.createFuture(RESULT_CODE_BAD_VALUE);
                        }
                        MediaItem item = convertMediaItemOnExecutor(controller, mediaId);
                        if (item == null) {
                            return PlayerResult.createFuture(RESULT_CODE_BAD_VALUE);
                        }
                        return mSessionImpl.replacePlaylistItem(index, item);
                    }
                });
    }

    @Override
    public void skipToPlaylistItem(IMediaController caller, int seq, final int index) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        if (index < 0) {
                            Log.w(TAG, "skipToPlaylistItem(): Ignoring negative index from "
                                    + controller);
                            return PlayerResult.createFuture(RESULT_CODE_BAD_VALUE);
                        }
                        return mSessionImpl.skipToPlaylistItem(index);
                    }
                });
    }

    @Override
    public void skipToPreviousItem(IMediaController caller, int seq) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq,
                SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.skipToPreviousItem();
                    }
                });
    }

    @Override
    public void skipToNextItem(IMediaController caller, int seq) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq,
                SessionCommand.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.skipToNextItem();
                    }
                });
    }

    @Override
    public void setRepeatMode(IMediaController caller, int seq, final int repeatMode) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_SET_REPEAT_MODE,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.setRepeatMode(repeatMode);
                    }
                });
    }

    @Override
    public void setShuffleMode(IMediaController caller, int seq, final int shuffleMode) {
        if (caller == null) {
            return;
        }
        dispatchSessionTask(caller, seq, SessionCommand.COMMAND_CODE_PLAYER_SET_SHUFFLE_MODE,
                new SessionPlayerTask() {
                    @Override
                    public ListenableFuture<PlayerResult> run(ControllerInfo controller) {
                        return mSessionImpl.setShuffleMode(shuffleMode);
                    }
                });
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    // AIDL methods for LibrarySession overrides
    //////////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    MediaLibrarySessionImpl getLibrarySession() {
        if (!(mSessionImpl instanceof MediaLibrarySessionImpl)) {
            throw new RuntimeException("Session cannot be casted to library session");
        }
        return (MediaLibrarySessionImpl) mSessionImpl;
    }

    @Override
    public void getLibraryRoot(final IMediaController caller, int seq,
            final ParcelImpl libraryParams) throws RuntimeException {
        if (caller == null || libraryParams == null) {
            return;
        }
        dispatchLibrarySessionTask(caller, seq,
                SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT,
                new LibrarySessionCallbackTask<LibraryResult>() {
                    @Override
                    public LibraryResult run(ControllerInfo controller) {
                        return getLibrarySession().onGetLibraryRootOnExecutor(controller,
                                (LibraryParams) MediaUtils.fromParcelable(libraryParams));
                    }
                });
    }

    @Override
    public void getItem(final IMediaController caller, int seq, final String mediaId)
            throws RuntimeException {
        dispatchLibrarySessionTask(caller, seq, SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM,
                new LibrarySessionCallbackTask<LibraryResult>() {
                    @Override
                    public LibraryResult run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(mediaId)) {
                            Log.w(TAG, "getItem(): Ignoring empty mediaId from " + controller);
                            return new LibraryResult(RESULT_CODE_BAD_VALUE);
                        }
                        return getLibrarySession().onGetItemOnExecutor(controller, mediaId);
                    }
                });
    }

    @Override
    public void getChildren(final IMediaController caller, int seq, final String parentId,
            final int page, final int pageSize, final ParcelImpl libraryParams)
            throws RuntimeException {
        if (caller == null || libraryParams == null) {
            return;
        }
        dispatchLibrarySessionTask(caller, seq, SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN,
                new LibrarySessionCallbackTask<LibraryResult>() {
                    @Override
                    public LibraryResult run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(parentId)) {
                            Log.w(TAG, "getChildren(): Ignoring empty parentId from " + controller);
                            return new LibraryResult(LibraryResult.RESULT_CODE_BAD_VALUE);
                        }
                        if (page < 0) {
                            Log.w(TAG, "getChildren(): Ignoring negative page from " + controller);
                            return new LibraryResult(LibraryResult.RESULT_CODE_BAD_VALUE);
                        }
                        if (pageSize < 1) {
                            Log.w(TAG, "getChildren(): Ignoring pageSize less than 1 from "
                                    + controller);
                            return new LibraryResult(LibraryResult.RESULT_CODE_BAD_VALUE);
                        }
                        return getLibrarySession().onGetChildrenOnExecutor(controller, parentId,
                                page, pageSize,
                                (LibraryParams) MediaUtils.fromParcelable(libraryParams));
                    }
                });
    }

    @Override
    public void search(IMediaController caller, int seq, final String query,
            final ParcelImpl libraryParams) {
        if (caller == null || libraryParams == null) {
            return;
        }
        dispatchLibrarySessionTask(caller, seq, SessionCommand.COMMAND_CODE_LIBRARY_SEARCH,
                new LibrarySessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(query)) {
                            Log.w(TAG, "search(): Ignoring empty query from " + controller);
                            return LibraryResult.RESULT_CODE_BAD_VALUE;
                        }
                        return getLibrarySession().onSearchOnExecutor(controller, query,
                                (LibraryParams) MediaUtils.fromParcelable(libraryParams));
                    }
                });
    }

    @Override
    public void getSearchResult(final IMediaController caller, int seq, final String query,
            final int page, final int pageSize, final ParcelImpl libraryParams) {
        if (caller == null || libraryParams == null) {
            return;
        }
        dispatchLibrarySessionTask(caller, seq,
                SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT,
                new LibrarySessionCallbackTask<LibraryResult>() {
                    @Override
                    public LibraryResult run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(query)) {
                            Log.w(TAG, "getSearchResult(): Ignoring empty query from "
                                    + controller);
                            return new LibraryResult(LibraryResult.RESULT_CODE_BAD_VALUE);
                        }
                        if (page < 0) {
                            Log.w(TAG, "getSearchResult(): Ignoring negative page from "
                                    + controller);
                            return new LibraryResult(LibraryResult.RESULT_CODE_BAD_VALUE);
                        }
                        if (pageSize < 1) {
                            Log.w(TAG, "getSearchResult(): Ignoring pageSize less than 1 from "
                                    + controller);
                            return new LibraryResult(LibraryResult.RESULT_CODE_BAD_VALUE);
                        }
                        return getLibrarySession().onGetSearchResultOnExecutor(controller,
                                query, page, pageSize,
                                (LibraryParams) MediaUtils.fromParcelable(libraryParams));
                    }
                });
    }

    @Override
    public void subscribe(final IMediaController caller, int seq, final String parentId,
            final ParcelImpl libraryParams) {
        if (caller == null || libraryParams == null) {
            return;
        }
        dispatchLibrarySessionTask(caller, seq, SessionCommand.COMMAND_CODE_LIBRARY_SUBSCRIBE,
                new LibrarySessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(parentId)) {
                            Log.w(TAG, "subscribe(): Ignoring empty parentId from " + controller);
                            return LibraryResult.RESULT_CODE_BAD_VALUE;
                        }
                        return getLibrarySession().onSubscribeOnExecutor(
                                controller, parentId,
                                (LibraryParams) MediaUtils.fromParcelable(libraryParams));
                    }
                });
    }

    @Override
    public void unsubscribe(final IMediaController caller, int seq, final String parentId) {
        if (caller == null) {
            return;
        }
        dispatchLibrarySessionTask(caller, seq, SessionCommand.COMMAND_CODE_LIBRARY_UNSUBSCRIBE,
                new LibrarySessionCallbackTask<Integer>() {
                    @Override
                    public Integer run(ControllerInfo controller) {
                        if (TextUtils.isEmpty(parentId)) {
                            Log.w(TAG, "unsubscribe(): Ignoring empty parentId from " + controller);
                            return LibraryResult.RESULT_CODE_BAD_VALUE;
                        }
                        return getLibrarySession().onUnsubscribeOnExecutor(controller, parentId);
                    }
                });
    }

    /**
     * Common interface for code snippets to handle all incoming commands from the controller.
     *
     * @see #dispatchSessionTask
     */
    private interface SessionTask<T> {
        // empty interface
    }

    private interface SessionPlayerTask extends SessionTask {
        ListenableFuture<PlayerResult> run(ControllerInfo controller) throws RemoteException;
    }

    private interface SessionCallbackTask<T> extends SessionTask {
        T run(ControllerInfo controller) throws RemoteException;
    }

    private interface LibrarySessionCallbackTask<T> extends SessionTask {
        T run(ControllerInfo controller) throws RemoteException;
    }

    final class Controller2Cb extends ControllerCb {
        // TODO: Drop 'Callback' from the name.
        private final IMediaController mIControllerCallback;

        Controller2Cb(@NonNull IMediaController callback) {
            mIControllerCallback = callback;
        }

        @NonNull
        IBinder getCallbackBinder() {
            return mIControllerCallback.asBinder();
        }

        @Override
        void onPlayerResult(int seq, @Nullable PlayerResult result) throws RemoteException {
            onSessionResult(seq, SessionResult.from(result));
        }

        @Override
        void onSessionResult(int seq, @Nullable SessionResult result) throws RemoteException {
            if (result == null) {
                result = new SessionResult(RESULT_CODE_UNKNOWN_ERROR, null);
            }
            mIControllerCallback.onSessionResult(seq, MediaUtils.toParcelable(result));
        }

        @Override
        void onLibraryResult(int seq, LibraryResult result) throws RemoteException {
            if (result == null) {
                result = new LibraryResult(LibraryResult.RESULT_CODE_UNKNOWN_ERROR);
            }
            mIControllerCallback.onLibraryResult(seq, MediaUtils.toParcelable(result));
        }

        @Override
        void setCustomLayout(int seq, List<CommandButton> layout) throws RemoteException {
            mIControllerCallback.onSetCustomLayout(seq,
                    MediaUtils.convertCommandButtonListToParcelImplList(layout));
        }

        @Override
        void onPlaybackInfoChanged(PlaybackInfo info) throws RemoteException {
            mIControllerCallback.onPlaybackInfoChanged(MediaUtils.toParcelable(info));
        }

        @Override
        void onAllowedCommandsChanged(SessionCommandGroup commands) throws RemoteException {
            mIControllerCallback.onAllowedCommandsChanged(MediaUtils.toParcelable(commands));
        }

        @Override
        void sendCustomCommand(int seq, SessionCommand command, Bundle args)
                throws RemoteException {
            mIControllerCallback.onCustomCommand(seq, MediaUtils.toParcelable(command), args);
        }

        @Override
        void onPlayerStateChanged(long eventTimeMs, long positionMs, int playerState)
                throws RemoteException {
            mIControllerCallback.onPlayerStateChanged(eventTimeMs, positionMs, playerState);
        }

        @Override
        void onPlaybackSpeedChanged(long eventTimeMs, long positionMs, float speed)
                throws RemoteException {
            mIControllerCallback.onPlaybackSpeedChanged(eventTimeMs, positionMs, speed);
        }

        @Override
        void onBufferingStateChanged(MediaItem item, int bufferingState, long bufferedPositionMs)
                throws RemoteException {
            mIControllerCallback.onBufferingStateChanged(MediaUtils.toParcelable(item),
                    bufferingState, bufferedPositionMs);
        }

        @Override
        void onSeekCompleted(long eventTimeMs, long positionMs, long seekPositionMs)
                throws RemoteException {
            mIControllerCallback.onSeekCompleted(eventTimeMs, positionMs, seekPositionMs);
        }

        @Override
        void onCurrentMediaItemChanged(MediaItem item) throws RemoteException {
            mIControllerCallback.onCurrentMediaItemChanged(MediaUtils.toParcelable(item));
        }

        @Override
        void onPlaylistChanged(List<MediaItem> playlist, MediaMetadata metadata)
                throws RemoteException {
            ControllerInfo controller = mConnectedControllersManager.getController(
                    getCallbackBinder());
            if (mConnectedControllersManager.isAllowedCommand(controller,
                    SessionCommand.COMMAND_CODE_PLAYER_GET_PLAYLIST)) {
                mIControllerCallback.onPlaylistChanged(
                        MediaUtils.convertMediaItemListToParcelImplListSlice(playlist),
                        MediaUtils.toParcelable(metadata));
            } else if (mConnectedControllersManager.isAllowedCommand(controller,
                    SessionCommand.COMMAND_CODE_PLAYER_GET_PLAYLIST_METADATA)) {
                mIControllerCallback.onPlaylistMetadataChanged(MediaUtils.toParcelable(metadata));
            }
        }

        @Override
        void onPlaylistMetadataChanged(MediaMetadata metadata) throws RemoteException {
            ControllerInfo controller = mConnectedControllersManager.getController(
                    getCallbackBinder());
            if (mConnectedControllersManager.isAllowedCommand(controller,
                    SessionCommand.COMMAND_CODE_PLAYER_GET_PLAYLIST_METADATA)) {
                mIControllerCallback.onPlaylistMetadataChanged(MediaUtils.toParcelable(metadata));
            }
        }

        @Override
        void onShuffleModeChanged(int shuffleMode) throws RemoteException {
            mIControllerCallback.onShuffleModeChanged(shuffleMode);
        }

        @Override
        void onRepeatModeChanged(int repeatMode) throws RemoteException {
            mIControllerCallback.onRepeatModeChanged(repeatMode);
        }

        @Override
        void onPlaybackCompleted() throws RemoteException {
            mIControllerCallback.onPlaybackCompleted();
        }

        @Override
        void onChildrenChanged(String parentId, int itemCount, LibraryParams params)
                throws RemoteException {
            mIControllerCallback.onChildrenChanged(parentId, itemCount,
                    MediaUtils.toParcelable(params));
        }

        @Override
        void onSearchResultChanged(String query, int itemCount, LibraryParams params)
                throws RemoteException {
            mIControllerCallback.onSearchResultChanged(query, itemCount,
                    MediaUtils.toParcelable(params));
        }

        @Override
        void onDisconnected() throws RemoteException {
            mIControllerCallback.onDisconnected();
        }
    }
}

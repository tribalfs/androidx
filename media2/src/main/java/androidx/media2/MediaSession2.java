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
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ResultReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.view.KeyEvent;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.core.content.ContextCompat;
import androidx.core.util.ObjectsCompat;
import androidx.media.AudioAttributesCompat;
import androidx.media.MediaSessionManager.RemoteUserInfo;
import androidx.media2.MediaController2.PlaybackInfo;
import androidx.media2.MediaPlayerConnector.BuffState;
import androidx.media2.MediaPlayerConnector.PlayerState;
import androidx.media2.MediaPlaylistAgent.PlaylistEventCallback;
import androidx.media2.MediaPlaylistAgent.RepeatMode;
import androidx.media2.MediaPlaylistAgent.ShuffleMode;
import androidx.versionedparcelable.ParcelField;
import androidx.versionedparcelable.VersionedParcelable;
import androidx.versionedparcelable.VersionedParcelize;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Allows a media app to expose its transport controls and playback information in a process to
 * other processes including the Android framework and other apps. Common use cases are as follows.
 * <ul>
 *     <li>Bluetooth/wired headset key events support</li>
 *     <li>Android Auto/Wearable support</li>
 *     <li>Separating UI process and playback process</li>
 * </ul>
 * <p>
 * A MediaSession2 should be created when an app wants to publish media playback information or
 * handle media keys. In general an app only needs one session for all playback, though multiple
 * sessions can be created to provide finer grain controls of media.
 * <p>
 * If you want to support background playback, {@link MediaSessionService2} is preferred
 * instead. With it, your playback can be revived even after playback is finished. See
 * {@link MediaSessionService2} for details.
 * <p>
 * Topic covered here:
 * <ol>
 * <li><a href="#SessionLifecycle">Session Lifecycle</a>
 * <li><a href="#AudioFocusAndNoisyIntent">Audio focus and noisy intent</a>
 * <li><a href="#Thread">Thread</a>
 * <li><a href="#KeyEvents">Media key events mapping</a>
 * </ol>
 * <a name="SessionLifecycle"></a>
 * <h3>Session Lifecycle</h3>
 * <p>
 * A session can be obtained by {@link Builder}. The owner of the session may pass its session token
 * to other processes to allow them to create a {@link MediaController2} to interact with the
 * session.
 * <p>
 * When a session receive transport control commands, the session sends the commands directly to
 * the the underlying media player set by {@link Builder} or {@link #updatePlayerConnector}.
 * <p>
 * When an app is finished performing playback it must call {@link #close()} to clean up the session
 * and notify any controllers.
 * <p>
 * <a name="AudioFocusAndNoisyIntent"></a>
 * <h3>Audio focus and noisy intent</h3>
 * <p>
 * MediaSession2 handles audio focus and noisy intent with {@link AudioAttributesCompat} set to the
 * underlying {@link MediaPlayerConnector} by default. You need to set the audio attribute before
 * the session is created, and playback started with the session.
 * <p>
 * Here's the table of automatic audio focus behavior with audio attributes.
 * <table>
 * <tr><th>Audio Attributes</th><th>Audio Focus Gain Type</th><th>Misc</th></tr>
 * <tr><td>{@link AudioAttributesCompat#USAGE_VOICE_COMMUNICATION_SIGNALLING}</td>
 *     <td>{@link android.media.AudioManager#AUDIOFOCUS_NONE}</td>
 *     <td /></tr>
 * <tr><td><ul><li>{@link AudioAttributesCompat#USAGE_GAME}</li>
 *             <li>{@link AudioAttributesCompat#USAGE_MEDIA}</li>
 *             <li>{@link AudioAttributesCompat#USAGE_UNKNOWN}</li></ul></td>
 *     <td>{@link android.media.AudioManager#AUDIOFOCUS_GAIN}</td>
 *     <td>Developers should specific a proper usage instead of
 *         {@link AudioAttributesCompat#USAGE_UNKNOWN}</td></tr>
 * <tr><td><ul><li>{@link AudioAttributesCompat#USAGE_ALARM}</li>
 *             <li>{@link AudioAttributesCompat#USAGE_VOICE_COMMUNICATION}</li></ul></td>
 *     <td>{@link android.media.AudioManager#AUDIOFOCUS_GAIN_TRANSIENT}</td>
 *     <td /></tr>
 * <tr><td><ul><li>{@link AudioAttributesCompat#USAGE_ASSISTANCE_NAVIGATION_GUIDANCE}</li>
 *             <li>{@link AudioAttributesCompat#USAGE_ASSISTANCE_SONIFICATION}</li>
 *             <li>{@link AudioAttributesCompat#USAGE_NOTIFICATION}</li>
 *             <li>{@link AudioAttributesCompat#USAGE_NOTIFICATION_COMMUNICATION_DELAYED}</li>
 *             <li>{@link AudioAttributesCompat#USAGE_NOTIFICATION_COMMUNICATION_INSTANT}</li>
 *             <li>{@link AudioAttributesCompat#USAGE_NOTIFICATION_COMMUNICATION_REQUEST}</li>
 *             <li>{@link AudioAttributesCompat#USAGE_NOTIFICATION_EVENT}</li>
 *             <li>{@link AudioAttributesCompat#USAGE_NOTIFICATION_RINGTONE}</li></ul></td>
 *     <td>{@link android.media.AudioManager#AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK}</td>
 *     <td /></tr>
 * <tr><td><ul><li>{@link AudioAttributesCompat#USAGE_ASSISTANT}</li></ul></td>
 *     <td>{@link android.media.AudioManager#AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE}</td>
 *     <td /></tr>
 * <tr><td>{@link AudioAttributesCompat#USAGE_ASSISTANCE_ACCESSIBILITY}</td>
 *     <td>{@link android.media.AudioManager#AUDIOFOCUS_GAIN_TRANSIENT} if
 *         {@link AudioAttributesCompat#CONTENT_TYPE_SPEECH},
 *         {@link android.media.AudioManager#AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK} otherwise</td>
 *     <td /></tr>
 * <tr><td>{@code null}</td>
 *     <td>No audio focus handling, and sets the player volume to {@code 0}</td>
 *     <td>Only valid if your media contents don't have audio</td></tr>
 * <tr><td>Any other AudioAttributes</td>
 *     <td>No audio focus handling, and sets the player volume to {@code 0}</td>
 *     <td>This is to handle error</td></tr>
 * </table>
 * <p>
 * For more information about the audio focus, take a look at
 * <a href="{@docRoot}guide/topics/media-apps/audio-focus.html">Managing audio focus</a>
 * <p>
 * <a name="Thread"></a>
 * <h3>Thread</h3>
 * <p>
 * {@link MediaSession2} objects are thread safe, but should be used on the thread on the looper.
 * <a name="KeyEvents"></a>
 * <h3>Media key events mapping</h3>
 * <p>
 * Here's the table of per key event.
 * <table>
 * <tr><th>Key code</th><th>{@link MediaSession2} API</th></tr>
 * <tr><td>{@link KeyEvent#KEYCODE_MEDIA_PLAY}</td>
 *     <td>{@link MediaSession2#play()}</td></tr>
 * <tr><td>{@link KeyEvent#KEYCODE_MEDIA_PAUSE}</td>
 *     <td>{@link MediaSession2#pause()}</td></tr>
 * <tr><td>{@link KeyEvent#KEYCODE_MEDIA_NEXT}</td>
 *     <td>{@link MediaSession2#skipToNextItem()}</td></tr>
 * <tr><td>{@link KeyEvent#KEYCODE_MEDIA_PREVIOUS}</td>
 *     <td>{@link MediaSession2#skipToPreviousItem()}</td></tr>
 * <tr><td>{@link KeyEvent#KEYCODE_MEDIA_STOP}</td>
 *     <td>{@link MediaSession2#pause()} and then
 *         {@link MediaSession2#seekTo(long)} with 0</td></tr>
 * <tr><td>{@link KeyEvent#KEYCODE_MEDIA_FAST_FORWARD}</td>
 *     <td>{@link SessionCallback#onFastForward}</td></tr>
 * <tr><td>{@link KeyEvent#KEYCODE_MEDIA_REWIND}</td>
 *     <td>{@link SessionCallback#onRewind}</td></tr>
 * <tr><td><ul><li>{@link KeyEvent#KEYCODE_MEDIA_PLAY_PAUSE}</li>
 *             <li>{@link KeyEvent#KEYCODE_HEADSETHOOK}</li></ul></td>
 *     <td><ul><li>For a single tap
 *             <ul><li>{@link MediaSession2#pause()} if
 *             {@link MediaPlayerConnector#PLAYER_STATE_PLAYING}</li>
 *             <li>{@link MediaSession2#play()} otherwise</li></ul>
 *             <li>For a double tap, {@link MediaSession2#skipToNextItem()}</li></ul></td>
 *     </td>
 * </table>
 * @see MediaSessionService2
 */
@TargetApi(Build.VERSION_CODES.P)
public class MediaSession2 implements MediaInterface2.SessionPlayer, AutoCloseable {
    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef({ERROR_CODE_UNKNOWN_ERROR, ERROR_CODE_APP_ERROR, ERROR_CODE_NOT_SUPPORTED,
            ERROR_CODE_AUTHENTICATION_EXPIRED, ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED,
            ERROR_CODE_CONCURRENT_STREAM_LIMIT, ERROR_CODE_PARENTAL_CONTROL_RESTRICTED,
            ERROR_CODE_NOT_AVAILABLE_IN_REGION, ERROR_CODE_CONTENT_ALREADY_PLAYING,
            ERROR_CODE_SKIP_LIMIT_REACHED, ERROR_CODE_ACTION_ABORTED, ERROR_CODE_END_OF_QUEUE,
            ERROR_CODE_SETUP_REQUIRED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ErrorCode {}

    /**
     * This is the default error code and indicates that none of the other error codes applies.
     */
    public static final int ERROR_CODE_UNKNOWN_ERROR = 0;

    /**
     * Error code when the application state is invalid to fulfill the request.
     */
    public static final int ERROR_CODE_APP_ERROR = 1;

    /**
     * Error code when the request is not supported by the application.
     */
    public static final int ERROR_CODE_NOT_SUPPORTED = 2;

    /**
     * Error code when the request cannot be performed because authentication has expired.
     */
    public static final int ERROR_CODE_AUTHENTICATION_EXPIRED = 3;

    /**
     * Error code when a premium account is required for the request to succeed.
     */
    public static final int ERROR_CODE_PREMIUM_ACCOUNT_REQUIRED = 4;

    /**
     * Error code when too many concurrent streams are detected.
     */
    public static final int ERROR_CODE_CONCURRENT_STREAM_LIMIT = 5;

    /**
     * Error code when the content is blocked due to parental controls.
     */
    public static final int ERROR_CODE_PARENTAL_CONTROL_RESTRICTED = 6;

    /**
     * Error code when the content is blocked due to being regionally unavailable.
     */
    public static final int ERROR_CODE_NOT_AVAILABLE_IN_REGION = 7;

    /**
     * Error code when the requested content is already playing.
     */
    public static final int ERROR_CODE_CONTENT_ALREADY_PLAYING = 8;

    /**
     * Error code when the application cannot skip any more songs because skip limit is reached.
     */
    public static final int ERROR_CODE_SKIP_LIMIT_REACHED = 9;

    /**
     * Error code when the action is interrupted due to some external event.
     */
    public static final int ERROR_CODE_ACTION_ABORTED = 10;

    /**
     * Error code when the playback navigation (previous, next) is not possible because the queue
     * was exhausted.
     */
    public static final int ERROR_CODE_END_OF_QUEUE = 11;

    /**
     * Error code when the session needs user's manual intervention.
     */
    public static final int ERROR_CODE_SETUP_REQUIRED = 12;

    static final String TAG = "MediaSession2";

    private final MediaSession2Impl mImpl;

    MediaSession2(Context context, String id, SessionPlayer2 player,
            PendingIntent sessionActivity, Executor callbackExecutor, SessionCallback callback) {
        mImpl = createImpl(context, id, player, sessionActivity, callbackExecutor,
                callback);
    }

    MediaSession2Impl createImpl(Context context, String id, SessionPlayer2 player,
            PendingIntent sessionActivity, Executor callbackExecutor, SessionCallback callback) {
        return new MediaSession2ImplBase(this, context, id, player, sessionActivity,
                callbackExecutor, callback);
    }

    /**
     * Should be only used by subclass.
     */
    MediaSession2Impl getImpl() {
        return mImpl;
    }

    /**
     * Sets the underlying {@link MediaPlayerConnector} and {@link MediaPlaylistAgent} for this
     * session to dispatch incoming event to.
     * <p>
     * When a {@link MediaPlaylistAgent} is specified here, the playlist agent should manage
     * {@link MediaPlayerConnector} for calling
     * {@link MediaPlayerConnector#setNextMediaItems(List)}.
     * <p>
     * If the {@link MediaPlaylistAgent} isn't set, session will recreate the default playlist
     * agent.
     *
     * @param player a {@link MediaPlayerConnector} that handles actual media playback in your app
     * @param playlistAgent a {@link MediaPlaylistAgent} that manages playlist of the {@code player}
     */
    public void updatePlayerConnector(@NonNull MediaPlayerConnector player,
            @Nullable MediaPlaylistAgent playlistAgent) {
        mImpl.updatePlayer(player, playlistAgent);
    }

    /**
     * Sets the underlying {@link SessionPlayer2} for this session to dispatch incoming event to.
     *
     * @param player a player that handles actual media playback in your app
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void updatePlayer(@NonNull SessionPlayer2 player) {
        mImpl.updatePlayer(player);
    }

    @Override
    public void close() {
        try {
            mImpl.close();
        } catch (Exception e) {
            // Should not be here.
        }
    }

    /**
     * @return player. Can be {@code null} if and only if the session is released.
     */
    public @Nullable MediaPlayerConnector getPlayerConnector() {
        return mImpl.getPlayerConnector();
    }

    /**
     * @return playlist agent
     */
    public @NonNull MediaPlaylistAgent getPlaylistAgent() {
        return mImpl.getPlaylistAgent();
    }

    /**
     * @return player. Can be {@code null} if and only if the session is released.
     * @hide
     */
    // TODO(jaewan): Unhide
    @RestrictTo(LIBRARY_GROUP)
    public @Nullable SessionPlayer2 getPlayer() {
        return mImpl.getPlayer();
    }

    /**
     * Gets the session ID
     *
     * @return
     */
    public @NonNull String getId() {
        return mImpl.getId();
    }

    /**
     * Returns the {@link SessionToken2} for creating {@link MediaController2}.
     */
    public @NonNull SessionToken2 getToken() {
        return mImpl.getToken();
    }

    @NonNull Context getContext() {
        return mImpl.getContext();
    }

    @NonNull Executor getCallbackExecutor() {
        return mImpl.getCallbackExecutor();
    }

    @NonNull SessionCallback getCallback() {
        return mImpl.getCallback();
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
    public @NonNull AudioFocusHandler getAudioFocusHandler() {
        return mImpl.getAudioFocusHandler();
    }

    /**
     * Returns the list of connected controller.
     *
     * @return list of {@link ControllerInfo}
     */
    public @NonNull List<ControllerInfo> getConnectedControllers() {
        return mImpl.getConnectedControllers();
    }

    /**
     * Sets ordered list of {@link CommandButton} for controllers to build UI with it.
     * <p>
     * It's up to controller's decision how to represent the layout in its own UI.
     * Here's the same way
     * (layout[i] means a CommandButton at index i in the given list)
     * For 5 icons row
     *      layout[3] layout[1] layout[0] layout[2] layout[4]
     * For 3 icons row
     *      layout[1] layout[0] layout[2]
     * For 5 icons row with overflow icon (can show +5 extra buttons with overflow button)
     *      expanded row:   layout[5] layout[6] layout[7] layout[8] layout[9]
     *      main row:       layout[3] layout[1] layout[0] layout[2] layout[4]
     * <p>
     * This API can be called in the
     * {@link SessionCallback#onConnect(MediaSession2, ControllerInfo)}.
     *
     * @param controller controller to specify layout.
     * @param layout ordered list of layout.
     */
    public void setCustomLayout(@NonNull ControllerInfo controller,
            @NonNull List<CommandButton> layout) {
        mImpl.setCustomLayout(controller, layout);
    }

    /**
     * Set the new allowed command group for the controller
     *
     * @param controller controller to change allowed commands
     * @param commands new allowed commands
     */
    public void setAllowedCommands(@NonNull ControllerInfo controller,
            @NonNull SessionCommandGroup2 commands) {
        mImpl.setAllowedCommands(controller, commands);
    }

    /**
     * Send custom command to all connected controllers.
     *
     * @param command a command
     * @param args optional argument
     */
    public void sendCustomCommand(@NonNull SessionCommand2 command, @Nullable Bundle args) {
        mImpl.sendCustomCommand(command, args);
    }

    /**
     * Send custom command to a specific controller.
     *
     * @param command a command
     * @param args optional argument
     * @param receiver result receiver for the session
     */
    public void sendCustomCommand(@NonNull ControllerInfo controller,
            @NonNull SessionCommand2 command, @Nullable Bundle args,
            @Nullable ResultReceiver receiver) {
        mImpl.sendCustomCommand(controller, command, args, receiver);
    }

    /**
     * Play playback.
     * <p>
     * This calls {@link MediaPlayerConnector#play()}.
     */
    @Override
    public void play() {
        mImpl.play();
    }

    /**
     * Pause playback.
     * <p>
     * This calls {@link MediaPlayerConnector#pause()}.
     */
    @Override
    public void pause() {
        mImpl.pause();
    }

    /**
     * Resets the player connector to the idle state.
     * <p>
     * This calls {@link MediaPlayerConnector#reset()} which resets the player connector to the
     * idle state, and detailed behaviors may differ depending on the player implementation. Use
     * this with caution.
     */
    @Override
    public void reset() {
        mImpl.reset();
    }

    /**
     * Request that the player prepare its playback. In other words, other sessions can continue
     * to play during the preparation of this session. This method can be used to speed up the
     * start of the playback. Once the preparation is done, the session will change its playback
     * state to {@link MediaPlayerConnector#PLAYER_STATE_PAUSED}. Afterwards, {@link #play} can be
     * called to start playback.
     * <p>
     * This calls {@link MediaPlayerConnector#reset()}.
     */
    @Override
    public void prepare() {
        mImpl.prepare();
    }

    /**
     * Move to a new location in the media stream.
     *
     * @param pos Position to move to, in milliseconds.
     */
    @Override
    public void seekTo(long pos) {
        mImpl.seekTo(pos);
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @Override
    public void skipForward() {
        mImpl.skipForward();
    }

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @Override
    public void skipBackward() {
        mImpl.skipBackward();
    }

    /**
     * Notify errors to the connected controllers
     *
     * @param errorCode error code
     * @param extras extras
     */
    @Override
    public void notifyError(@ErrorCode int errorCode, @Nullable Bundle extras) {
        mImpl.notifyError(errorCode, extras);
    }

    /**
     * Notify routes information to a connected controller
     *
     * @param controller controller information
     * @param routes The routes information. Each bundle should be from {@link
     *               androidx.mediarouter.media.MediaRouter.RouteInfo#getUniqueRouteDescriptorBundle
     *               RouteInfo}.
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    public void notifyRoutesInfoChanged(@NonNull ControllerInfo controller,
            @Nullable List<Bundle> routes) {
        mImpl.notifyRoutesInfoChanged(controller, routes);
    }

    /**
     * Gets the current player state.
     *
     * @return the current player state
     */
    @Override
    public @PlayerState int getPlayerState() {
        return mImpl.getPlayerState();
    }

    /**
     * Gets the current position.
     *
     * @return the current playback position in ms, or {@link MediaPlayerConnector#UNKNOWN_TIME} if
     *         unknown.
     */
    @Override
    public long getCurrentPosition() {
        return mImpl.getCurrentPosition();
    }

    /**
     * Gets the duration of the currently playing media item.
     *
     * @return the duration of the current item from {@link MediaPlayerConnector#getDuration()}.
     */
    @Override
    public long getDuration() {
        return mImpl.getDuration();
    }

    /**
     * Gets the buffered position, or {@link MediaPlayerConnector#UNKNOWN_TIME} if unknown.
     *
     * @return the buffered position in ms, or {@link MediaPlayerConnector#UNKNOWN_TIME}.
     */
    @Override
    public long getBufferedPosition() {
        return mImpl.getBufferedPosition();
    }

    /**
     * Gets the current buffering state of the player.
     * During buffering, see {@link #getBufferedPosition()} for the quantifying the amount already
     * buffered.
     *
     * @return the buffering state.
     */
    @Override
    public @BuffState int getBufferingState() {
        return mImpl.getBufferingState();
    }

    /**
     * Get the playback speed.
     *
     * @return speed
     */
    @Override
    public float getPlaybackSpeed() {
        return mImpl.getPlaybackSpeed();
    }

    /**
     * Set the playback speed.
     */
    @Override
    public void setPlaybackSpeed(float speed) {
        mImpl.setPlaybackSpeed(speed);
    }

    /**
     * Sets the media item missing helper. Helper will be used to provide default implementation of
     * {@link MediaPlaylistAgent} when it isn't set by developer.
     * <p>
     * Default implementation of the {@link MediaPlaylistAgent} will call helper when a
     * {@link MediaItem2} in the playlist doesn't have a {@link MediaItem2}. This may happen
     * when
     * <ul>
     *      <li>{@link MediaItem2} specified by {@link #setPlaylist(List, MediaMetadata2)} doesn't
     *          have {@link MediaItem2}</li>
     *      <li>{@link MediaController2#addPlaylistItem(int, MediaItem2)} is called and accepted
     *          by {@link SessionCallback#onCommandRequest(
     *          MediaSession2, ControllerInfo, SessionCommand2)}.
     *          In that case, an item would be added automatically without the media item.</li>
     * </ul>
     * <p>
     * If it's not set, playback wouldn't happen for the item without media item descriptor.
     * <p>
     * The helper will be run on the executor that was specified by
     * {@link Builder#setSessionCallback(Executor, SessionCallback)}.
     *
     * @param helper a media item missing helper.
     * @throws IllegalStateException when the helper is set when the playlist agent is set
     * @see #setPlaylist(List, MediaMetadata2)
     * @see SessionCallback#onCommandRequest(MediaSession2, ControllerInfo, SessionCommand2)
     * @see SessionCommand2#COMMAND_CODE_PLAYLIST_ADD_ITEM
     * @see SessionCommand2#COMMAND_CODE_PLAYLIST_REPLACE_ITEM
     */
    @Override
    public void setOnDataSourceMissingHelper(@NonNull OnDataSourceMissingHelper helper) {
        mImpl.setOnDataSourceMissingHelper(helper);
    }

    /**
     * Clears the media item missing helper.
     *
     * @see #setOnDataSourceMissingHelper(OnDataSourceMissingHelper)
     */
    @Override
    public void clearOnDataSourceMissingHelper() {
        mImpl.clearOnDataSourceMissingHelper();
    }

    /**
     * Returns the playlist from the {@link MediaPlaylistAgent}.
     * <p>
     * This list may differ with the list that was specified with
     * {@link #setPlaylist(List, MediaMetadata2)} depending on the {@link MediaPlaylistAgent}
     * implementation. Use media items returned here for other playlist agent APIs such as
     * {@link MediaPlaylistAgent#skipToPlaylistItem(MediaItem2)}.
     *
     * @return playlist
     * @see MediaPlaylistAgent#getPlaylist()
     * @see SessionCallback#onPlaylistChanged(
     *          MediaSession2, MediaPlaylistAgent, List, MediaMetadata2)
     */
    @Override
    public List<MediaItem2> getPlaylist() {
        return mImpl.getPlaylist();
    }

    /**
     * Sets a list of {@link MediaItem2} to the {@link MediaPlaylistAgent}. Ensure uniqueness of
     * each {@link MediaItem2} in the playlist so the session can uniquely identity individual
     * items.
     * <p>
     * This may be an asynchronous call, and {@link MediaPlaylistAgent} may keep the copy of the
     * list. Wait for {@link SessionCallback#onPlaylistChanged(MediaSession2, MediaPlaylistAgent,
     * List, MediaMetadata2)} to know the operation finishes.
     * <p>
     * You may specify a {@link MediaItem2} without {@link MediaItem2}. In that case,
     * {@link MediaPlaylistAgent} has responsibility to dynamically query {link MediaItem2}
     * when such media item is ready for preparation or play. Default implementation needs
     * {@link OnDataSourceMissingHelper} for such case.
     * <p>
     * It's recommended to fill {@link MediaMetadata2} in each {@link MediaItem2} especially for the
     * duration information with the key {@link MediaMetadata2#METADATA_KEY_DURATION}. Without the
     * duration information in the metadata, session will do extra work to get the duration and send
     * it to the controller.
     *
     * @param list A list of {@link MediaItem2} objects to set as a play list.
     * @throws IllegalArgumentException if given list is {@code null}, or has duplicated media
     * items.
     * @see MediaPlaylistAgent#setPlaylist(List, MediaMetadata2)
     * @see SessionCallback#onPlaylistChanged(
     *          MediaSession2, MediaPlaylistAgent, List, MediaMetadata2)
     * @see #setOnDataSourceMissingHelper
     */
    @Override
    public void setPlaylist(@NonNull List<MediaItem2> list, @Nullable MediaMetadata2 metadata) {
        mImpl.setPlaylist(list, metadata);
    }

    /**
     * Skips to the item in the playlist.
     * <p>
     * This calls {@link MediaPlaylistAgent#skipToPlaylistItem(MediaItem2)} and the behavior depends
     * on the playlist agent implementation, especially with the shuffle/repeat mode.
     *
     * @param item The item in the playlist you want to play
     * @see #getShuffleMode()
     * @see #getRepeatMode()
     */
    @Override
    public void skipToPlaylistItem(@NonNull MediaItem2 item) {
        mImpl.skipToPlaylistItem(item);
    }

    /**
     * Skips to the previous item.
     * <p>
     * This calls {@link MediaPlaylistAgent#skipToPreviousItem()} and the behavior depends on the
     * playlist agent implementation, especially with the shuffle/repeat mode.
     *
     * @see #getShuffleMode()
     * @see #getRepeatMode()
     **/
    @Override
    public void skipToPreviousItem() {
        mImpl.skipToPreviousItem();
    }

    /**
     * Skips to the next item.
     * <p>
     * This calls {@link MediaPlaylistAgent#skipToNextItem()} and the behavior depends on the
     * playlist agent implementation, especially with the shuffle/repeat mode.
     *
     * @see #getShuffleMode()
     * @see #getRepeatMode()
     */
    @Override
    public void skipToNextItem() {
        mImpl.skipToNextItem();
    }

    /**
     * Gets the playlist metadata from the {@link MediaPlaylistAgent}.
     *
     * @return the playlist metadata
     */
    @Override
    public MediaMetadata2 getPlaylistMetadata() {
        return mImpl.getPlaylistMetadata();
    }

    /**
     * Adds the media item to the playlist at position index. Index equals or greater than
     * the current playlist size (e.g. {@link Integer#MAX_VALUE}) will add the item at the end of
     * the playlist.
     * <p>
     * This will not change the currently playing media item.
     * If index is less than or equal to the current index of the play list,
     * the current index of the play list will be incremented correspondingly.
     *
     * @param index the index you want to add
     * @param item the media item you want to add
     */
    @Override
    public void addPlaylistItem(int index, @NonNull MediaItem2 item) {
        mImpl.addPlaylistItem(index, item);
    }

    /**
     * Removes the media item in the playlist.
     * <p>
     * If the item is the currently playing item of the playlist, current playback
     * will be stopped and playback moves to next source in the list.
     *
     * @param item the media item you want to add
     */
    @Override
    public void removePlaylistItem(@NonNull MediaItem2 item) {
        mImpl.removePlaylistItem(item);
    }

    /**
     * Replaces the media item at index in the playlist. This can be also used to update metadata of
     * an item.
     *
     * @param index the index of the item to replace
     * @param item the new item
     */
    @Override
    public void replacePlaylistItem(int index, @NonNull MediaItem2 item) {
        mImpl.replacePlaylistItem(index, item);
    }

    /**
     * Return currently playing media item.
     *
     * @return currently playing media item
     */
    @Override
    public MediaItem2 getCurrentMediaItem() {
        return mImpl.getCurrentMediaItem();
    }

    /**
     * Updates the playlist metadata to the {@link MediaPlaylistAgent}.
     *
     * @param metadata metadata of the playlist
     */
    @Override
    public void updatePlaylistMetadata(@Nullable MediaMetadata2 metadata) {
        mImpl.updatePlaylistMetadata(metadata);
    }

    /**
     * Gets the repeat mode from the {@link MediaPlaylistAgent}.
     *
     * @return repeat mode
     * @see MediaPlaylistAgent#REPEAT_MODE_NONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ALL
     * @see MediaPlaylistAgent#REPEAT_MODE_GROUP
     */
    @Override
    public @RepeatMode int getRepeatMode() {
        return mImpl.getRepeatMode();
    }

    /**
     * Sets the repeat mode to the {@link MediaPlaylistAgent}.
     *
     * @param repeatMode repeat mode
     * @see MediaPlaylistAgent#REPEAT_MODE_NONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ONE
     * @see MediaPlaylistAgent#REPEAT_MODE_ALL
     * @see MediaPlaylistAgent#REPEAT_MODE_GROUP
     */
    @Override
    public void setRepeatMode(@RepeatMode int repeatMode) {
        mImpl.setRepeatMode(repeatMode);
    }

    /**
     * Gets the shuffle mode from the {@link MediaPlaylistAgent}.
     *
     * @return The shuffle mode
     * @see MediaPlaylistAgent#SHUFFLE_MODE_NONE
     * @see MediaPlaylistAgent#SHUFFLE_MODE_ALL
     * @see MediaPlaylistAgent#SHUFFLE_MODE_GROUP
     */
    @Override
    public @ShuffleMode int getShuffleMode() {
        return mImpl.getShuffleMode();
    }

    /**
     * Sets the shuffle mode to the {@link MediaPlaylistAgent}.
     *
     * @param shuffleMode The shuffle mode
     * @see MediaPlaylistAgent#SHUFFLE_MODE_NONE
     * @see MediaPlaylistAgent#SHUFFLE_MODE_ALL
     * @see MediaPlaylistAgent#SHUFFLE_MODE_GROUP
     */
    @Override
    public void setShuffleMode(@ShuffleMode int shuffleMode) {
        mImpl.setShuffleMode(shuffleMode);
    }

    /**
     * @hide
     * @return Bundle
     */
    @RestrictTo(LIBRARY_GROUP)
    public MediaSessionCompat getSessionCompat() {
        return mImpl.getSessionCompat();
    }

    /**
     * Handles the controller's connection request from {@link MediaSessionService2}.
     *
     * @param controller controller aidl
     * @param packageName controller package name
     * @param pid controller pid
     * @param uid controller uid
     */
    void handleControllerConnectionFromService(IMediaController2 controller, String packageName,
            int pid, int uid) {
        mImpl.connectFromService(controller, packageName, pid, uid);
    }

    IBinder getLegacyBrowerServiceBinder() {
        return mImpl.getLegacyBrowserServiceBinder();
    }

    /**
     * Interface definition of a callback to be invoked when a {@link MediaItem2} in the playlist
     * didn't have a {@link MediaItem2} but it's needed now for preparing or playing it.
     *
     * #see #setOnDataSourceMissingHelper
     */
    public interface OnDataSourceMissingHelper {
        /**
         * Called when a {@link MediaItem2} in the playlist didn't have a {@link MediaItem2}
         * but it's needed now for preparing or playing it. Returned media item descriptor will be
         * sent to the player directly to prepare or play the contents.
         * <p>
         * An exception may be thrown if the returned {@link MediaItem2} is duplicated in the
         * playlist, so items cannot be differentiated.
         *
         * @param session the session for this event
         * @param item media item from the controller
         * @return a media item descriptor if the media item. Can be {@code null} if the content
         *        isn't available.
         */
        @Nullable
        MediaItem2 onDataSourceMissing(@NonNull MediaSession2 session,
                @NonNull MediaItem2 item);
    }

    /**
     * Callback to be called for all incoming commands from {@link MediaController2}s.
     * <p>
     * If it's not set, the session will accept all controllers and all incoming commands by
     * default.
     */
    public abstract static class SessionCallback {
        ForegroundServiceEventCallback mForegroundServiceEventCallback;

        /**
         * Called when a controller is created for this session. Return allowed commands for
         * controller. By default it allows all connection requests and commands.
         * <p>
         * You can reject the connection by return {@code null}. In that case, controller receives
         * {@link MediaController2.ControllerCallback#onDisconnected(MediaController2)} and cannot
         * be usable.
         *
         * @param session the session for this event
         * @param controller controller information.
         * @return allowed commands. Can be {@code null} to reject connection.
         */
        public @Nullable SessionCommandGroup2 onConnect(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller) {
            SessionCommandGroup2 commands = new SessionCommandGroup2.Builder()
                    .addAllPredefinedCommands(SessionCommand2.COMMAND_VERSION_1)
                    .build();
            return commands;
        }

        /**
         * Called when a controller is disconnected
         *
         * @param session the session for this event
         * @param controller controller information
         */
        public void onDisconnected(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller) { }

        /**
         * Called when a controller sent a command which will be sent directly to one of the
         * following:
         * <ul>
         *  <li> {@link MediaPlayerConnector} </li>
         *  <li> {@link MediaPlaylistAgent} </li>
         *  <li> {@link android.media.AudioManager}</li>
         * </ul>
         * Return {@code false} here to reject the request and stop sending command.
         *
         * @param session the session for this event
         * @param controller controller information.
         * @param command a command. This method will be called for every single command.
         * @return {@code true} if you want to accept incoming command. {@code false} otherwise.
         * @see SessionCommand2#COMMAND_CODE_PLAYBACK_PLAY
         * @see SessionCommand2#COMMAND_CODE_PLAYBACK_PAUSE
         * @see SessionCommand2#COMMAND_CODE_PLAYBACK_RESET
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_SKIP_TO_NEXT_ITEM
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_SKIP_TO_PREV_ITEM
         * @see SessionCommand2#COMMAND_CODE_PLAYBACK_PREPARE
         * @see SessionCommand2#COMMAND_CODE_PLAYBACK_SEEK_TO
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_SKIP_TO_PLAYLIST_ITEM
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_SET_SHUFFLE_MODE
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_SET_REPEAT_MODE
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_ADD_ITEM
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_REMOVE_ITEM
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_REPLACE_ITEM
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_GET_LIST
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_SET_LIST
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_GET_LIST_METADATA
         * @see SessionCommand2#COMMAND_CODE_PLAYLIST_UPDATE_LIST_METADATA
         * @see SessionCommand2#COMMAND_CODE_VOLUME_SET_VOLUME
         * @see SessionCommand2#COMMAND_CODE_VOLUME_ADJUST_VOLUME
         */
        public boolean onCommandRequest(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull SessionCommand2 command) {
            return true;
        }

        /**
         * Called when a controller set rating of a media item through
         * {@link MediaController2#setRating(String, Rating2)}.
         * <p>
         * To allow setting user rating for a {@link MediaItem2}, the media item's metadata
         * should have {@link Rating2} with the key {@link MediaMetadata2#METADATA_KEY_USER_RATING},
         * in order to provide possible rating style for controller. Controller will follow the
         * rating style.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param mediaId media id from the controller
         * @param rating new rating from the controller
         * @see SessionCommand2#COMMAND_CODE_SESSION_SET_RATING
         */
        public void onSetRating(@NonNull MediaSession2 session, @NonNull ControllerInfo controller,
                @NonNull String mediaId, @NonNull Rating2 rating) { }

        /**
         * Called when a controller sent a custom command through
         * {@link MediaController2#sendCustomCommand(SessionCommand2, Bundle, ResultReceiver)}.
         * <p>
         * Interoperability: This would be also called by {@link
         * android.support.v4.media.MediaBrowserCompat
         * #sendCustomAction(String, Bundle, CustomActionCallback)}. If so, extra from
         * sendCustomAction will be considered as args and customCommand would have null extra.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param customCommand custom command.
         * @param args optional arguments
         * @param cb optional result receiver
         * @see SessionCommand2#COMMAND_CODE_CUSTOM
         */
        public void onCustomCommand(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull SessionCommand2 customCommand,
                @Nullable Bundle args, @Nullable ResultReceiver cb) { }

        /**
         * Called when a controller requested to play a specific mediaId through
         * {@link MediaController2#playFromMediaId(String, Bundle)}.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param mediaId media id
         * @param extras optional extra bundle
         * @see SessionCommand2#COMMAND_CODE_SESSION_PLAY_FROM_MEDIA_ID
         */
        public void onPlayFromMediaId(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull String mediaId,
                @Nullable Bundle extras) { }

        /**
         * Called when a controller requested to begin playback from a search query through
         * {@link MediaController2#playFromSearch(String, Bundle)}
         * <p>
         * An empty query indicates that the app may play any music. The implementation should
         * attempt to make a smart choice about what to play.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param query query string. Can be empty to indicate any suggested media
         * @param extras optional extra bundle
         * @see SessionCommand2#COMMAND_CODE_SESSION_PLAY_FROM_SEARCH
         */
        public void onPlayFromSearch(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull String query,
                @Nullable Bundle extras) { }

        /**
         * Called when a controller requested to play a specific media item represented by a URI
         * through {@link MediaController2#playFromUri(Uri, Bundle)}
         *
         * @param session the session for this event
         * @param controller controller information
         * @param uri uri
         * @param extras optional extra bundle
         * @see SessionCommand2#COMMAND_CODE_SESSION_PLAY_FROM_URI
         */
        public void onPlayFromUri(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull Uri uri,
                @Nullable Bundle extras) { }

        /**
         * Called when a controller requested to prepare for playing a specific mediaId through
         * {@link MediaController2#prepareFromMediaId(String, Bundle)}.
         * <p>
         * During the preparation, a session should not hold audio focus in order to allow other
         * sessions play seamlessly. The state of playback should be updated to
         * {@link MediaPlayerConnector#PLAYER_STATE_PAUSED} after the preparation is done.
         * <p>
         * The playback of the prepared content should start in the later calls of
         * {@link MediaSession2#play()}.
         * <p>
         * Override {@link #onPlayFromMediaId} to handle requests for starting
         * playback without preparation.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param mediaId media id to prepare
         * @param extras optional extra bundle
         * @see SessionCommand2#COMMAND_CODE_SESSION_PREPARE_FROM_MEDIA_ID
         */
        public void onPrepareFromMediaId(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull String mediaId,
                @Nullable Bundle extras) { }

        /**
         * Called when a controller requested to prepare playback from a search query through
         * {@link MediaController2#prepareFromSearch(String, Bundle)}.
         * <p>
         * An empty query indicates that the app may prepare any music. The implementation should
         * attempt to make a smart choice about what to play.
         * <p>
         * The state of playback should be updated to
         * {@link MediaPlayerConnector#PLAYER_STATE_PAUSED} after the preparation is done.
         * The playback of the prepared content should start in the
         * later calls of {@link MediaSession2#play()}.
         * <p>
         * Override {@link #onPlayFromSearch} to handle requests for starting playback without
         * preparation.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param query query string. Can be empty to indicate any suggested media
         * @param extras optional extra bundle
         * @see SessionCommand2#COMMAND_CODE_SESSION_PREPARE_FROM_SEARCH
         */
        public void onPrepareFromSearch(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull String query,
                @Nullable Bundle extras) { }

        /**
         * Called when a controller requested to prepare a specific media item represented by a URI
         * through {@link MediaController2#prepareFromUri(Uri, Bundle)}.
         * <p>
         * During the preparation, a session should not hold audio focus in order to allow
         * other sessions play seamlessly. The state of playback should be updated to
         * {@link MediaPlayerConnector#PLAYER_STATE_PAUSED} after the preparation is done.
         * <p>
         * The playback of the prepared content should start in the later calls of
         * {@link MediaSession2#play()}.
         * <p>
         * Override {@link #onPlayFromUri} to handle requests for starting playback without
         * preparation.
         *
         * @param session the session for this event
         * @param controller controller information
         * @param uri uri
         * @param extras optional extra bundle
         * @see SessionCommand2#COMMAND_CODE_SESSION_PREPARE_FROM_URI
         */
        public void onPrepareFromUri(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull Uri uri, @Nullable Bundle extras) { }

        /**
         * Called when a controller called {@link MediaController2#fastForward()}
         *
         * @param session the session for this event
         * @param controller controller information
         * @see SessionCommand2#COMMAND_CODE_SESSION_FAST_FORWARD
         */
        public void onFastForward(@NonNull MediaSession2 session, ControllerInfo controller) { }

        /**
         * Called when a controller called {@link MediaController2#rewind()}
         *
         * @param session the session for this event
         * @param controller controller information
         * @see SessionCommand2#COMMAND_CODE_SESSION_REWIND
         */
        public void onRewind(@NonNull MediaSession2 session, ControllerInfo controller) { }

        /**
         * Called when a controller called {@link MediaController2#subscribeRoutesInfo()}
         * Session app should notify the routes information by calling
         * {@link MediaSession2#notifyRoutesInfoChanged(ControllerInfo, List)}.
         *
         * @param session the session for this event
         * @param controller controller information
         * @see SessionCommand2#COMMAND_CODE_SESSION_SUBSCRIBE_ROUTES_INFO
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public void onSubscribeRoutesInfo(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller) { }

        /**
         * Called when a controller called {@link MediaController2#unsubscribeRoutesInfo()}
         *
         * @param session the session for this event
         * @param controller controller information
         * @see SessionCommand2#COMMAND_CODE_SESSION_UNSUBSCRIBE_ROUTES_INFO
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public void onUnsubscribeRoutesInfo(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller) { }

        /**
         * Called when a controller called {@link MediaController2#selectRoute(Bundle)}.
         * @param session the session for this event
         * @param controller controller information
         * @param route The route bundle from {@link
         *              androidx.mediarouter.media.MediaRouter.RouteInfo
         *              #getUniqueRouteDescriptorBundle RouteInfo}
         * @see SessionCommand2#COMMAND_CODE_SESSION_SELECT_ROUTE
         * @see androidx.mediarouter.media.MediaRouter.RouteInfo#getUniqueRouteDescriptorBundle
         * @see androidx.mediarouter.media.MediaRouter#getRoute
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public void onSelectRoute(@NonNull MediaSession2 session,
                @NonNull ControllerInfo controller, @NonNull Bundle route) { }
        /**
         * Called when the player's current playing item is changed
         * <p>
         * When it's called, you should invalidate previous playback information and wait for later
         * callbacks.
         *
         * @param session the controller for this event
         * @param player the player for this event
         * @param item new item
         */
        public void onCurrentMediaItemChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlayerConnector player, @Nullable MediaItem2 item) { }

        /**
         * Called when the player is <i>prepared</i>, i.e. it is ready to play the content
         * referenced by the given media item.
         * @param session the session for this event
         * @param player the player for this event
         * @param item the media item for which buffering is happening
         */
        public void onMediaPrepared(@NonNull MediaSession2 session,
                @NonNull MediaPlayerConnector player, @NonNull MediaItem2 item) { }

        /**
         * Called to indicate that the state of the player has changed.
         * See {@link MediaPlayerConnector#getPlayerState()} for polling the player state.
         * @param session the session for this event
         * @param player the player for this event
         * @param state the new state of the player.
         */
        public void onPlayerStateChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlayerConnector player, @PlayerState int state) { }

        /**
         * Called to report buffering events for a media item.
         *
         * @param session the session for this event
         * @param player the player for this event
         * @param item the media item for which buffering is happening.
         * @param state the new buffering state.
         */
        public void onBufferingStateChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlayerConnector player, @NonNull MediaItem2 item,
                @BuffState int state) { }

        /**
         * Called to indicate that the playback speed has changed.
         * @param session the session for this event
         * @param player the player for this event
         * @param speed the new playback speed.
         */
        public void onPlaybackSpeedChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlayerConnector player, float speed) { }

        /**
         * Called to indicate that {@link #seekTo(long)} is completed.
         *
         * @param session the session for this event.
         * @param player the player that has completed seeking.
         * @param position the previous seeking request.
         * @see #seekTo(long)
         */
        public void onSeekCompleted(@NonNull MediaSession2 session,
                @NonNull MediaPlayerConnector player, long position) { }

        /**
         * Called when a playlist is changed from the {@link MediaPlaylistAgent}.
         * <p>
         * This is called when the underlying agent has called
         * {@link PlaylistEventCallback#onPlaylistChanged(MediaPlaylistAgent,
         * List, MediaMetadata2)}.
         *
         * @param session the session for this event
         * @param playlistAgent playlist agent for this event
         * @param list new playlist
         * @param metadata new metadata
         */
        public void onPlaylistChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlaylistAgent playlistAgent, @NonNull List<MediaItem2> list,
                @Nullable MediaMetadata2 metadata) { }

        /**
         * Called when a playlist metadata is changed.
         *
         * @param session the session for this event
         * @param playlistAgent playlist agent for this event
         * @param metadata new metadata
         */
        public void onPlaylistMetadataChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlaylistAgent playlistAgent, @Nullable MediaMetadata2 metadata) { }

        /**
         * Called when the shuffle mode is changed.
         *
         * @param session the session for this event
         * @param playlistAgent playlist agent for this event
         * @param shuffleMode repeat mode
         * @see MediaPlaylistAgent#SHUFFLE_MODE_NONE
         * @see MediaPlaylistAgent#SHUFFLE_MODE_ALL
         * @see MediaPlaylistAgent#SHUFFLE_MODE_GROUP
         */
        public void onShuffleModeChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlaylistAgent playlistAgent,
                @MediaPlaylistAgent.ShuffleMode int shuffleMode) { }

        /**
         * Called when the repeat mode is changed.
         *
         * @param session the session for this event
         * @param playlistAgent playlist agent for this event
         * @param repeatMode repeat mode
         * @see MediaPlaylistAgent#REPEAT_MODE_NONE
         * @see MediaPlaylistAgent#REPEAT_MODE_ONE
         * @see MediaPlaylistAgent#REPEAT_MODE_ALL
         * @see MediaPlaylistAgent#REPEAT_MODE_GROUP
         */
        public void onRepeatModeChanged(@NonNull MediaSession2 session,
                @NonNull MediaPlaylistAgent playlistAgent,
                @MediaPlaylistAgent.RepeatMode int repeatMode) { }

        /**
         * Called when the player state is changed. Used internally for setting the
         * {@link MediaSessionService2} as foreground/background.
         */
        final void onPlayerStateChanged(MediaSession2 session, @PlayerState int state) {
            if (mForegroundServiceEventCallback != null) {
                mForegroundServiceEventCallback.onPlayerStateChanged(session, state);
            }
        }

        final void onSessionClosed(MediaSession2 session) {
            if (mForegroundServiceEventCallback != null) {
                mForegroundServiceEventCallback.onSessionClosed(session);
            }
        }

        void setForegroundServiceEventCallback(ForegroundServiceEventCallback callback) {
            mForegroundServiceEventCallback = callback;
        }

        abstract static class ForegroundServiceEventCallback {
            public void onPlayerStateChanged(MediaSession2 session, @PlayerState int state) { }
            public void onSessionClosed(MediaSession2 session) { }
        }
    }

    /**
     * Builder for {@link MediaSession2}.
     * <p>
     * Any incoming event from the {@link MediaController2} will be handled on the thread
     * that created session with the {@link Builder#build()}.
     */
    public static final class Builder extends BuilderBase<MediaSession2, Builder, SessionCallback> {
        public Builder(Context context) {
            super(context);
        }

        @Override
        public @NonNull Builder setPlayer(@NonNull MediaPlayerConnector player) {
            return super.setPlayer(player);
        }

        @Override
        public @NonNull Builder setPlaylistAgent(@NonNull MediaPlaylistAgent playlistAgent) {
            return super.setPlaylistAgent(playlistAgent);
        }

        /**
         * @param player a {@link SessionPlayer2} that handles actual media playback in your app.
         * @return
         * @hide
         */
        // TODO(jaewan): Unhide
        @RestrictTo(LIBRARY_GROUP)
        @Override
        public @NonNull Builder setPlayer(@NonNull SessionPlayer2 player) {
            return super.setPlayer(player);
        }

        @Override
        public @NonNull Builder setSessionActivity(@Nullable PendingIntent pi) {
            return super.setSessionActivity(pi);
        }

        @Override
        public @NonNull Builder setId(@NonNull String id) {
            return super.setId(id);
        }

        @Override
        public @NonNull Builder setSessionCallback(@NonNull Executor executor,
                @NonNull SessionCallback callback) {
            return super.setSessionCallback(executor, callback);
        }

        @Override
        public @NonNull MediaSession2 build() {
            if (mCallbackExecutor == null) {
                mCallbackExecutor = ContextCompat.getMainExecutor(mContext);
            }
            if (mCallback == null) {
                mCallback = new SessionCallback() {};
            }
            return new MediaSession2(mContext, mId, mSessionPlayer, mSessionActivity,
                    mCallbackExecutor, mCallback);
        }
    }

    /**
     * Information of a controller.
     */
    public static final class ControllerInfo {
        private final RemoteUserInfo mRemoteUserInfo;
        private final boolean mIsTrusted;
        private final ControllerCb mControllerCb;

        /**
         * @param remoteUserInfo remote user info
         * @param trusted {@code true} if trusted, {@code false} otherwise
         * @param cb ControllerCb. Can be {@code null} only when a MediaBrowserCompat connects to
         *           MediaSessionService2 and ControllerInfo is needed for
         *           SessionCallback#onConnected().
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        ControllerInfo(@NonNull RemoteUserInfo remoteUserInfo, boolean trusted,
                @Nullable ControllerCb cb) {
            mRemoteUserInfo = remoteUserInfo;
            mIsTrusted = trusted;
            mControllerCb = cb;
        }

        /**
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public @NonNull RemoteUserInfo getRemoteUserInfo() {
            return mRemoteUserInfo;
        }

        /**
         * @return package name of the controller. Can be
         *         {@link androidx.media.MediaSessionManager.RemoteUserInfo#LEGACY_CONTROLLER} if
         *         the package name cannot be obtained.
         */
        public @NonNull String getPackageName() {
            return mRemoteUserInfo.getPackageName();
        }

        /**
         * @return uid of the controller. Can be a negative value if the uid cannot be obtained.
         */
        public int getUid() {
            return mRemoteUserInfo.getUid();
        }

        /**
         * Return if the controller has granted {@code android.permission.MEDIA_CONTENT_CONTROL} or
         * has a enabled notification listener so can be trusted to accept connection and incoming
         * command request.
         *
         * @return {@code true} if the controller is trusted.
         * @hide
         */
        @RestrictTo(LIBRARY_GROUP)
        public boolean isTrusted() {
            return mIsTrusted;
        }

        @Override
        public int hashCode() {
            return mControllerCb != null ? mControllerCb.hashCode() : 0;
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof ControllerInfo)) {
                return false;
            }
            if (this == obj) {
                return true;
            }
            ControllerInfo other = (ControllerInfo) obj;
            if (mControllerCb != null || other.mControllerCb != null) {
                return ObjectsCompat.equals(mControllerCb, other.mControllerCb);
            }
            return mRemoteUserInfo.equals(other.mRemoteUserInfo);
        }

        @Override
        public String toString() {
            return "ControllerInfo {pkg=" + mRemoteUserInfo.getPackageName() + ", uid="
                    + mRemoteUserInfo.getUid() + "})";
        }

        @Nullable ControllerCb getControllerCb() {
            return mControllerCb;
        }
    }

    /**
     * Button for a {@link SessionCommand2} that will be shown by the controller.
     * <p>
     * It's up to the controller's decision to respect or ignore this customization request.
     */
    @VersionedParcelize
    public static final class CommandButton implements VersionedParcelable {
        private static final String KEY_COMMAND = "android.media.session2.command_button.command";
        private static final String KEY_ICON_RES_ID =
                "android.media.session2.command_button.icon_res_id";
        private static final String KEY_DISPLAY_NAME =
                "android.media.session2.command_button.display_name";
        private static final String KEY_EXTRAS = "android.media.session2.command_button.extras";
        private static final String KEY_ENABLED = "android.media.session2.command_button.enabled";

        @ParcelField(1)
        SessionCommand2 mCommand;
        @ParcelField(2)
        int mIconResId;
        @ParcelField(3)
        String mDisplayName;
        @ParcelField(4)
        Bundle mExtras;
        @ParcelField(5)
        boolean mEnabled;

        /**
         * Used for VersionedParcelable
         */
        CommandButton() {
        }

        CommandButton(@Nullable SessionCommand2 command, int iconResId,
                @Nullable String displayName, Bundle extras, boolean enabled) {
            mCommand = command;
            mIconResId = iconResId;
            mDisplayName = displayName;
            mExtras = extras;
            mEnabled = enabled;
        }

        /**
         * Get command associated with this button. Can be {@code null} if the button isn't enabled
         * and only providing placeholder.
         *
         * @return command or {@code null}
         */
        public @Nullable SessionCommand2 getCommand() {
            return mCommand;
        }

        /**
         * Resource id of the button in this package. Can be {@code 0} if the command is predefined
         * and custom icon isn't needed.
         *
         * @return resource id of the icon. Can be {@code 0}.
         */
        public int getIconResId() {
            return mIconResId;
        }

        /**
         * Display name of the button. Can be {@code null} or empty if the command is predefined
         * and custom name isn't needed.
         *
         * @return custom display name. Can be {@code null} or empty.
         */
        public @Nullable String getDisplayName() {
            return mDisplayName;
        }

        /**
         * Extra information of the button. It's private information between session and controller.
         *
         * @return
         */
        public @Nullable Bundle getExtras() {
            return mExtras;
        }

        /**
         * Return whether it's enabled.
         *
         * @return {@code true} if enabled. {@code false} otherwise.
         */
        public boolean isEnabled() {
            return mEnabled;
        }

        /**
         * @hide
         * @return Bundle
         */
        @RestrictTo(LIBRARY_GROUP)
        public @NonNull Bundle toBundle() {
            Bundle bundle = new Bundle();
            bundle.putBundle(KEY_COMMAND, mCommand.toBundle());
            bundle.putInt(KEY_ICON_RES_ID, mIconResId);
            bundle.putString(KEY_DISPLAY_NAME, mDisplayName);
            bundle.putBundle(KEY_EXTRAS, mExtras);
            bundle.putBoolean(KEY_ENABLED, mEnabled);
            return bundle;
        }

        /**
         * @hide
         * @return CommandButton
         */
        @RestrictTo(LIBRARY_GROUP)
        public static @Nullable CommandButton fromBundle(Bundle bundle) {
            if (bundle == null) {
                return null;
            }
            CommandButton.Builder builder = new CommandButton.Builder();
            builder.setCommand(SessionCommand2.fromBundle(bundle.getBundle(KEY_COMMAND)));
            builder.setIconResId(bundle.getInt(KEY_ICON_RES_ID, 0));
            builder.setDisplayName(bundle.getString(KEY_DISPLAY_NAME));
            builder.setExtras(bundle.getBundle(KEY_EXTRAS));
            builder.setEnabled(bundle.getBoolean(KEY_ENABLED));
            try {
                return builder.build();
            } catch (IllegalStateException e) {
                // Malformed or version mismatch. Return null for now.
                return null;
            }
        }

        /**
         * Builder for {@link CommandButton}.
         */
        public static final class Builder {
            private SessionCommand2 mCommand;
            private int mIconResId;
            private String mDisplayName;
            private Bundle mExtras;
            private boolean mEnabled;

            /**
             * Sets the {@link SessionCommand2} that would be sent to the session when the button
             * is clicked.
             *
             * @param command session command
             */
            public @NonNull Builder setCommand(@Nullable SessionCommand2 command) {
                mCommand = command;
                return this;
            }

            /**
             * Sets the bitmap-type (e.g. PNG) icon resource id of the button.
             * <p>
             * None bitmap type (e.g. VectorDrawabale) may cause unexpected behavior when it's sent
             * to {@link MediaController2} app, so please avoid using it especially for the older
             * platform (API < 21).
             *
             * @param resId resource id of the button
             */
            public @NonNull Builder setIconResId(int resId) {
                mIconResId = resId;
                return this;
            }

            /**
             * Sets the display name of the button.
             *
             * @param displayName display name of the button
             */
            public @NonNull Builder setDisplayName(@Nullable String displayName) {
                mDisplayName = displayName;
                return this;
            }

            /**
             * Sets whether the button is enabled. Can be {@code false} to indicate that the button
             * should be shown but isn't clickable.
             *
             * @param enabled {@code true} if the button is enabled and ready.
             *          {@code false} otherwise.
             */
            public @NonNull Builder setEnabled(boolean enabled) {
                mEnabled = enabled;
                return this;
            }

            /**
             * Sets the extras of the button.
             *
             * @param extras extras information of the button
             */
            public @NonNull Builder setExtras(@Nullable Bundle extras) {
                mExtras = extras;
                return this;
            }

            /**
             * Builds the {@link CommandButton}.
             *
             * @return a new {@link CommandButton}
             */
            public @NonNull CommandButton build() {
                return new CommandButton(mCommand, mIconResId, mDisplayName, mExtras, mEnabled);
            }
        }
    }

    abstract static class ControllerCb {
        // Mostly matched with the methods in MediaController2.ControllerCallback
        abstract void onCustomLayoutChanged(@NonNull List<CommandButton> layout)
                throws RemoteException;
        abstract void onPlaybackInfoChanged(@NonNull PlaybackInfo info) throws RemoteException;
        abstract void onAllowedCommandsChanged(@NonNull SessionCommandGroup2 commands)
                throws RemoteException;
        abstract void onCustomCommand(@NonNull SessionCommand2 command, @Nullable Bundle args,
                @Nullable ResultReceiver receiver) throws RemoteException;
        abstract void onPlayerStateChanged(long eventTimeMs, long positionMs, int playerState)
                throws RemoteException;
        abstract void onPlaybackSpeedChanged(long eventTimeMs, long positionMs, float speed)
                throws RemoteException;
        abstract void onBufferingStateChanged(@NonNull MediaItem2 item,
                @BuffState int bufferingState, long bufferedPositionMs) throws RemoteException;
        abstract void onSeekCompleted(long eventTimeMs, long positionMs, long position)
                throws RemoteException;
        abstract void onError(@ErrorCode int errorCode, @Nullable Bundle extras)
                throws RemoteException;
        abstract void onCurrentMediaItemChanged(@Nullable MediaItem2 item) throws RemoteException;
        abstract void onPlaylistChanged(@NonNull List<MediaItem2> playlist,
                @Nullable MediaMetadata2 metadata) throws RemoteException;
        abstract void onPlaylistMetadataChanged(@Nullable MediaMetadata2 metadata)
                throws RemoteException;
        abstract void onShuffleModeChanged(@SessionPlayer2.ShuffleMode int shuffleMode)
                throws RemoteException;
        abstract void onRepeatModeChanged(@SessionPlayer2.RepeatMode int repeatMode)
                throws RemoteException;
        abstract void onRoutesInfoChanged(@Nullable List<Bundle> routes) throws RemoteException;
        abstract void onDisconnected() throws RemoteException;

        // Mostly matched with the methods in MediaBrowser2.BrowserCallback.
        abstract void onGetLibraryRootDone(@Nullable Bundle rootHints, @Nullable String rootMediaId,
                @Nullable Bundle rootExtra) throws RemoteException;
        abstract void onChildrenChanged(@NonNull String parentId, int itemCount,
                @Nullable Bundle extras) throws RemoteException;
        abstract void onGetChildrenDone(@NonNull String parentId, int page, int pageSize,
                @Nullable List<MediaItem2> result, @Nullable Bundle extras) throws RemoteException;
        abstract void onGetItemDone(@NonNull String mediaId, @Nullable MediaItem2 result)
                throws RemoteException;
        abstract void onSearchResultChanged(@NonNull String query, int itemCount,
                @Nullable Bundle extras) throws RemoteException;
        abstract void onGetSearchResultDone(@NonNull String query, int page, int pageSize,
                @Nullable List<MediaItem2> result, @Nullable Bundle extras) throws RemoteException;
    }

    interface MediaSession2Impl extends MediaInterface2.SessionPlayer, AutoCloseable {
        void updatePlayer(@NonNull MediaPlayerConnector player,
                @Nullable MediaPlaylistAgent playlistAgent);
        void updatePlayer(@NonNull SessionPlayer2 player);
        @NonNull MediaPlayerConnector getPlayerConnector();
        @NonNull MediaPlaylistAgent getPlaylistAgent();
        @NonNull SessionPlayer2 getPlayer();
        @NonNull String getId();
        @NonNull SessionToken2 getToken();
        @NonNull List<ControllerInfo> getConnectedControllers();
        boolean isConnected(@NonNull ControllerInfo controller);

        void setCustomLayout(@NonNull ControllerInfo controller,
                @NonNull List<CommandButton> layout);
        void setAllowedCommands(@NonNull ControllerInfo controller,
                @NonNull SessionCommandGroup2 commands);
        void sendCustomCommand(@NonNull SessionCommand2 command, @Nullable Bundle args);
        void sendCustomCommand(@NonNull ControllerInfo controller,
                @NonNull SessionCommand2 command, @Nullable Bundle args,
                @Nullable ResultReceiver receiver);
        void notifyRoutesInfoChanged(@NonNull ControllerInfo controller,
                @Nullable List<Bundle> routes);

        // Internally used methods
        MediaSession2 getInstance();
        MediaSessionCompat getSessionCompat();
        Context getContext();
        Executor getCallbackExecutor();
        SessionCallback getCallback();
        boolean isClosed();
        PlaybackStateCompat createPlaybackStateCompat();
        PlaybackInfo getPlaybackInfo();
        AudioFocusHandler getAudioFocusHandler();
        PendingIntent getSessionActivity();
        IBinder getLegacyBrowserServiceBinder();
        void connectFromService(IMediaController2 caller, String packageName, int pid, int uid);
    }

    /**
     * Base builder class for MediaSession2 and its subclass. Any change in this class should be
     * also applied to the subclasses {@link MediaSession2.Builder} and
     * {@link MediaLibraryService2.MediaLibrarySession.Builder}.
     * <p>
     * APIs here should be package private, but should have documentations for developers.
     * Otherwise, javadoc will generate documentation with the generic types such as follows.
     * <pre>U extends BuilderBase<T, U, C> setSessionCallback(Executor executor, C callback)</pre>
     * <p>
     * This class is hidden to prevent from generating test stub, which fails with
     * 'unexpected bound' because it tries to auto generate stub class as follows.
     * <pre>abstract static class BuilderBase<
     *      T extends android.media.MediaSession2,
     *      U extends android.media.MediaSession2.BuilderBase<
     *              T, U, C extends android.media.MediaSession2.SessionCallback>, C></pre>
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    abstract static class BuilderBase
            <T extends MediaSession2, U extends BuilderBase<T, U, C>, C extends SessionCallback> {
        final Context mContext;
        MediaPlayerConnector mPlayer;
        String mId;
        Executor mCallbackExecutor;
        C mCallback;
        MediaPlaylistAgent mPlaylistAgent;
        PendingIntent mSessionActivity;
        SessionPlayer2 mSessionPlayer;

        BuilderBase(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("context shouldn't be null");
            }
            mContext = context;
            // Ensure non-null id.
            mId = "";
        }

        /**
         * Sets the underlying {@link MediaPlayerConnector} for this session to dispatch incoming
         * event to.
         *
         * @param player a {@link MediaPlayerConnector} that handles actual media playback in your
         *               app.
         */
        @NonNull U setPlayer(@NonNull MediaPlayerConnector player) {
            if (player == null) {
                throw new IllegalArgumentException("player shouldn't be null");
            }
            mPlayer = player;
            return (U) this;
        }

        /**
         * Sets the {@link MediaPlaylistAgent} for this session to manages playlist of the
         * underlying {@link MediaPlayerConnector}. The playlist agent should manage
         * {@link MediaPlayerConnector} for calling
         * {@link MediaPlayerConnector#setNextMediaItems(List)}.
         * <p>
         * If the {@link MediaPlaylistAgent} isn't set, session will create the default playlist
         * agent.
         *
         * @param playlistAgent a {@link MediaPlaylistAgent} that manages playlist of the
         *                      {@code player}
         */
        U setPlaylistAgent(@NonNull MediaPlaylistAgent playlistAgent) {
            if (playlistAgent == null) {
                throw new IllegalArgumentException("playlistAgent shouldn't be null");
            }
            mPlaylistAgent = playlistAgent;
            return (U) this;
        }

        /**
         * Sets the underlying {@link SessionPlayer2} for this session to dispatch incoming
         * event to.
         *
         * @param player a {@link SessionPlayer2} that handles actual media playback in your app.
         */
        @NonNull U setPlayer(@NonNull SessionPlayer2 player) {
            if (player == null) {
                throw new IllegalArgumentException("player shouldn't be null");
            }
            mSessionPlayer = player;
            return (U) this;
        }

        /**
         * Set an intent for launching UI for this Session. This can be used as a
         * quick link to an ongoing media screen. The intent should be for an
         * activity that may be started using {@link Context#startActivity(Intent)}.
         *
         * @param pi The intent to launch to show UI for this session.
         */
        @NonNull U setSessionActivity(@Nullable PendingIntent pi) {
            mSessionActivity = pi;
            return (U) this;
        }

        /**
         * Set ID of the session. If it's not set, an empty string with used to create a session.
         * <p>
         * Use this if and only if your app supports multiple playback at the same time and also
         * wants to provide external apps to have finer controls of them.
         *
         * @param id id of the session. Must be unique per package.
         * @throws IllegalArgumentException if id is {@code null}
         * @return
         */
        @NonNull U setId(@NonNull String id) {
            if (id == null) {
                throw new IllegalArgumentException("id shouldn't be null");
            }
            mId = id;
            return (U) this;
        }

        /**
         * Set callback for the session.
         *
         * @param executor callback executor
         * @param callback session callback.
         * @return
         */
        @NonNull U setSessionCallback(@NonNull Executor executor, @NonNull C callback) {
            if (executor == null) {
                throw new IllegalArgumentException("executor shouldn't be null");
            }
            if (callback == null) {
                throw new IllegalArgumentException("callback shouldn't be null");
            }
            mCallbackExecutor = executor;
            mCallback = callback;
            return (U) this;
        }

        /**
         * Build {@link MediaSession2}.
         *
         * @return a new session
         * @throws IllegalStateException if the session with the same id is already exists for the
         *      package.
         */
        @NonNull abstract T build();
    }
}

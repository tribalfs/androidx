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

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;

import android.content.Context;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * @hide
 * * TODO: Fix {link DataSourceDesc}
 * MediaPlaylistAgent is the abstract class an application needs to derive from to pass an object
 * to a MediaSession2 that will override default playlist handling behaviors. It contains a set of
 * notify methods to signal MediaSession2 that playlist-related state has changed.
 * <p>
 * Playlists are composed of one or multiple {@link MediaItem2} instances, which combine metadata
 * and data sources (as {link DataSourceDesc})
 * Used by {@link MediaSession2} and {@link MediaController2}.
 */
// This class only includes methods that contain {@link MediaItem2}.
@RestrictTo(LIBRARY_GROUP)
public abstract class MediaPlaylistAgent {
    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef({REPEAT_MODE_NONE, REPEAT_MODE_ONE, REPEAT_MODE_ALL,
            REPEAT_MODE_GROUP})
    @Retention(RetentionPolicy.SOURCE)
    public @interface RepeatMode {}

    /**
     * Playback will be stopped at the end of the playing media list.
     */
    public static final int REPEAT_MODE_NONE = 0;

    /**
     * Playback of the current playing media item will be repeated.
     */
    public static final int REPEAT_MODE_ONE = 1;

    /**
     * Playing media list will be repeated.
     */
    public static final int REPEAT_MODE_ALL = 2;

    /**
     * Playback of the playing media group will be repeated.
     * A group is a logical block of media items which is specified in the section 5.7 of the
     * Bluetooth AVRCP 1.6. An example of a group is the playlist.
     */
    public static final int REPEAT_MODE_GROUP = 3;

    /**
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    @IntDef({SHUFFLE_MODE_NONE, SHUFFLE_MODE_ALL, SHUFFLE_MODE_GROUP})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ShuffleMode {}

    /**
     * Media list will be played in order.
     */
    public static final int SHUFFLE_MODE_NONE = 0;

    /**
     * Media list will be played in shuffled order.
     */
    public static final int SHUFFLE_MODE_ALL = 1;

    /**
     * Media group will be played in shuffled order.
     * A group is a logical block of media items which is specified in the section 5.7 of the
     * Bluetooth AVRCP 1.6. An example of a group is the playlist.
     */
    public static final int SHUFFLE_MODE_GROUP = 2;

    //private final MediaPlaylistAgentProvider mProvider;

    /**
     * A callback class to receive notifications for events on the media player. See
     * {@link MediaPlaylistAgent#registerPlaylistEventCallback(Executor, PlaylistEventCallback)}
     * to register this callback.
     */
    public abstract static class PlaylistEventCallback {
        /**
         * Called when a playlist is changed.
         *
         * @param playlistAgent playlist agent for this event
         * @param list new playlist
         * @param metadata new metadata
         */
        public void onPlaylistChanged(@NonNull MediaPlaylistAgent playlistAgent,
                @NonNull List<MediaItem2> list, @Nullable MediaMetadata2 metadata) { }

        /**
         * Called when a playlist metadata is changed.
         *
         * @param playlistAgent playlist agent for this event
         * @param metadata new metadata
         */
        public void onPlaylistMetadataChanged(@NonNull MediaPlaylistAgent playlistAgent,
                @Nullable MediaMetadata2 metadata) { }

        /**
         * Called when the shuffle mode is changed.
         *
         * @param playlistAgent playlist agent for this event
         * @param shuffleMode repeat mode
         * @see #SHUFFLE_MODE_NONE
         * @see #SHUFFLE_MODE_ALL
         * @see #SHUFFLE_MODE_GROUP
         */
        public void onShuffleModeChanged(@NonNull MediaPlaylistAgent playlistAgent,
                @ShuffleMode int shuffleMode) { }

        /**
         * Called when the repeat mode is changed.
         *
         * @param playlistAgent playlist agent for this event
         * @param repeatMode repeat mode
         * @see #REPEAT_MODE_NONE
         * @see #REPEAT_MODE_ONE
         * @see #REPEAT_MODE_ALL
         * @see #REPEAT_MODE_GROUP
         */
        public void onRepeatModeChanged(@NonNull MediaPlaylistAgent playlistAgent,
                @RepeatMode int repeatMode) { }
    }

    /**
     * TODO: add javadoc
     */
    public MediaPlaylistAgent(@NonNull Context context) {
        //mProvider = ApiLoader.getProvider().createMediaPlaylistAgent(context, this);
    }

    /**
     * Register {@link PlaylistEventCallback} to listen changes in the underlying
     * {@link MediaPlaylistAgent}.
     *
     * @param executor a callback Executor
     * @param callback a PlaylistEventCallback
     * @throws IllegalArgumentException if executor or callback is {@code null}.
     */
    public final void registerPlaylistEventCallback(
            @NonNull /*@CallbackExecutor*/ Executor executor,
            @NonNull PlaylistEventCallback callback) {
        //mProvider.registerPlaylistEventCallback_impl(executor, callback);
    }

    /**
     * Unregister the previously registered {@link PlaylistEventCallback}.
     *
     * @param callback the callback to be removed
     * @throws IllegalArgumentException if the callback is {@code null}.
     */
    public final void unregisterPlaylistEventCallback(@NonNull PlaylistEventCallback callback) {
        //mProvider.unregisterPlaylistEventCallback_impl(callback);
    }

    /**
     * TODO: add javadoc
     */
    public final void notifyPlaylistChanged() {
        //mProvider.notifyPlaylistChanged_impl();
    }

    /**
     * TODO: add javadoc
     */
    public final void notifyPlaylistMetadataChanged() {
        //mProvider.notifyPlaylistMetadataChanged_impl();
    }

    /**
     * TODO: add javadoc
     */
    public final void notifyShuffleModeChanged() {
        //mProvider.notifyShuffleModeChanged_impl();
    }

    /**
     * TODO: add javadoc
     */
    public final void notifyRepeatModeChanged() {
        //mProvider.notifyRepeatModeChanged_impl();
    }

    /**
     * Returns the playlist
     *
     * @return playlist, or null if none is set.
     */
    public @Nullable List<MediaItem2> getPlaylist() {
        //return mProvider.getPlaylist_impl();
        return null;
    }

    /**
     * Sets the playlist.
     *
     * @param list playlist
     * @param metadata metadata of the playlist
     */
    public void setPlaylist(@NonNull List<MediaItem2> list, @Nullable MediaMetadata2 metadata) {
        //mProvider.setPlaylist_impl(list, metadata);
    }

    /**
     * Returns the playlist metadata
     *
     * @return metadata metadata of the playlist, or null if none is set
     */
    public @Nullable MediaMetadata2 getPlaylistMetadata() {
        //return mProvider.getPlaylistMetadata_impl();
        return null;
    }

    /**
     * Updates the playlist metadata
     *
     * @param metadata metadata of the playlist
     */
    public void updatePlaylistMetadata(@Nullable MediaMetadata2 metadata) {
        //mProvider.updatePlaylistMetadata_impl(metadata);
    }

    /**
     * Adds the media item to the playlist at the index
     *
     * @param index index
     * @param item media item to add
     */
    public void addPlaylistItem(int index, @NonNull MediaItem2 item) {
        //mProvider.addPlaylistItem_impl(index, item);
    }

    /**
     * Removes the media item from the playlist
     *
     * @param item media item to remove
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
     * Skips to the the media item, and plays from it.
     *
     * @param item media item to start playing from
     */
    public void skipToPlaylistItem(@NonNull MediaItem2 item) {
        //mProvider.skipToPlaylistItem_impl(item);
    }

    /**
     * Skips to the previous item in the playlist.
     */
    public void skipToPreviousItem() {
        //mProvider.skipToPreviousItem_impl();
    }

    /**
     * Skips to the next item in the playlist.
     */
    public void skipToNextItem() {
        //mProvider.skipToNextItem_impl();
    }

    /**
     * Gets the repeat mode
     *
     * @return repeat mode
     * @see #REPEAT_MODE_NONE
     * @see #REPEAT_MODE_ONE
     * @see #REPEAT_MODE_ALL
     * @see #REPEAT_MODE_GROUP
     */
    public @RepeatMode int getRepeatMode() {
        //return mProvider.getRepeatMode_impl();
        return REPEAT_MODE_NONE;
    }

    /**
     * Sets the repeat mode
     *
     * @param repeatMode repeat mode
     * @see #REPEAT_MODE_NONE
     * @see #REPEAT_MODE_ONE
     * @see #REPEAT_MODE_ALL
     * @see #REPEAT_MODE_GROUP
     */
    public void setRepeatMode(@RepeatMode int repeatMode) {
        //mProvider.setRepeatMode_impl(repeatMode);
    }

    /**
     * Gets the shuffle mode
     *
     * @return The shuffle mode
     * @see #SHUFFLE_MODE_NONE
     * @see #SHUFFLE_MODE_ALL
     * @see #SHUFFLE_MODE_GROUP
     */
    public @ShuffleMode int getShuffleMode() {
        //return mProvider.getShuffleMode_impl();
        return SHUFFLE_MODE_NONE;
    }

    /**
     * Sets the shuffle mode
     *
     * @param shuffleMode The shuffle mode
     * @see #SHUFFLE_MODE_NONE
     * @see #SHUFFLE_MODE_ALL
     * @see #SHUFFLE_MODE_GROUP
     */
    public void setShuffleMode(@ShuffleMode int shuffleMode) {
        //mProvider.setShuffleMode_impl(shuffleMode);
    }

    /**
     * TODO: Fix {link DataSourceDesc}
     * Gets a {@link MediaItem2} in the playlist that matches given {@code dsd}.
     * You can override this method to have more finer control of updating {link DataSourceDesc}
     * on items in the playlist.
     *
     * @return A {@link MediaItem2} object in the playlist that matches given {@code dsd}.
     *         {@code null} if playlist is not set, or if the playlist has no matching item.
     * @throws IllegalArgumentException if {@code dsd} is null
     * @hide
     */
    @RestrictTo(LIBRARY_GROUP)
    // TODO(jaewan): Unhide
    public @Nullable MediaItem2 getMediaItem(@NonNull Object /*DataSourceDesc*/ dsd) {
        //return mProvider.getMediaItem_impl(dsd);
        return null;
    }
}

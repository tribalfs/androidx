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

import static androidx.media2.MediaItem2.FLAG_PLAYABLE;
import static androidx.media2.MediaMetadata2.METADATA_KEY_DISPLAY_DESCRIPTION;
import static androidx.media2.MediaMetadata2.METADATA_KEY_DISPLAY_ICON;
import static androidx.media2.MediaMetadata2.METADATA_KEY_DISPLAY_ICON_URI;
import static androidx.media2.MediaMetadata2.METADATA_KEY_DISPLAY_SUBTITLE;
import static androidx.media2.MediaMetadata2.METADATA_KEY_DISPLAY_TITLE;
import static androidx.media2.MediaMetadata2.METADATA_KEY_MEDIA_ID;
import static androidx.media2.MediaMetadata2.METADATA_KEY_MEDIA_URI;
import static androidx.media2.MediaMetadata2.METADATA_KEY_TITLE;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.media.MediaBrowserCompat.MediaItem;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.media.AudioAttributesCompat;
import androidx.media.MediaBrowserServiceCompat.BrowserRoot;
import androidx.media2.MediaSession2.CommandButton;
import androidx.versionedparcelable.ParcelImpl;
import androidx.versionedparcelable.ParcelUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
public class MediaUtils2 {
    public static final String TAG = "MediaUtils2";
    public static final int TRANSACTION_SIZE_LIMIT_IN_BYTES = 256 * 1024; // 256KB

    // Stub BrowserRoot for accepting any connection here.
    public static final BrowserRoot sDefaultBrowserRoot =
            new BrowserRoot(MediaLibraryService2.SERVICE_INTERFACE, null);

    public static final Executor DIRECT_EXECUTOR = new Executor() {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    };

    private MediaUtils2() {
    }

    /**
     * Creates a {@link MediaItem} from the {@link MediaItem2}.
     *
     * @param item2 an item.
     * @return The newly created media item.
     */
    public static MediaItem convertToMediaItem(MediaItem2 item2) {
        if (item2 == null) {
            return null;
        }
        MediaDescriptionCompat descCompat;

        MediaMetadata2 metadata = item2.getMetadata();
        if (metadata == null) {
            descCompat = new MediaDescriptionCompat.Builder()
                    .setMediaId(item2.getMediaId())
                    .build();
        } else {
            MediaDescriptionCompat.Builder builder = new MediaDescriptionCompat.Builder()
                    .setMediaId(item2.getMediaId())
                    .setSubtitle(metadata.getText(METADATA_KEY_DISPLAY_SUBTITLE))
                    .setDescription(metadata.getText(METADATA_KEY_DISPLAY_DESCRIPTION))
                    .setIconBitmap(metadata.getBitmap(METADATA_KEY_DISPLAY_ICON))
                    .setExtras(metadata.getExtras());

            String title = metadata.getString(METADATA_KEY_TITLE);
            if (title != null) {
                builder.setTitle(title);
            } else {
                builder.setTitle(metadata.getString(METADATA_KEY_DISPLAY_TITLE));
            }

            String displayIconUri = metadata.getString(METADATA_KEY_DISPLAY_ICON_URI);
            if (displayIconUri != null) {
                builder.setIconUri(Uri.parse(displayIconUri));
            }

            String mediaUri = metadata.getString(METADATA_KEY_MEDIA_URI);
            if (mediaUri != null) {
                builder.setMediaUri(Uri.parse(mediaUri));
            }

            descCompat = builder.build();
        }
        return new MediaItem(descCompat, item2.getFlags());
    }

    /**
     * Convert a list of {@link MediaItem2} to a list of {@link MediaItem}.
     */
    public static List<MediaItem> convertToMediaItemList(List<MediaItem2> items) {
        if (items == null) {
            return null;
        }
        List<MediaItem> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            result.add(convertToMediaItem(items.get(i)));
        }
        return result;
    }

    /**
     * Creates a {@link MediaItem2} from the {@link MediaItem}.
     *
     * @param item an item.
     * @return The newly created media item.
     */
    public static MediaItem2 convertToMediaItem2(MediaItem item) {
        if (item == null) {
            return null;
        }
        MediaMetadata2 metadata2 = convertToMediaMetadata2(item.getDescription());
        return new MediaItem2.Builder(item.getFlags())
                .setMediaId(item.getMediaId())
                .setMetadata(metadata2)
                .build();
    }

    /**
     * Convert a {@link QueueItem} to a {@link MediaItem2}.
     */
    public static MediaItem2 convertToMediaItem2(QueueItem item) {
        if (item == null) {
            return null;
        }
        // descriptionCompat cannot be null
        MediaDescriptionCompat descriptionCompat = item.getDescription();
        MediaMetadata2 metadata2 = convertToMediaMetadata2(descriptionCompat);
        return new MediaItem2.Builder(FLAG_PLAYABLE)
                .setMetadata(metadata2)
                .setUuid(createUuidByQueueIdAndMediaId(item.getQueueId(),
                        descriptionCompat.getMediaId()))
                .build();
    }

    /**
     * Create a {@link UUID} with queue id and media id.
     */
    public static UUID createUuidByQueueIdAndMediaId(long queueId, String mediaId) {
        return new UUID(queueId, (mediaId == null) ? 0 : mediaId.hashCode());
    }

    /**
     * Convert a {@link MediaMetadataCompat} to a {@link MediaItem2}.
     */
    public static MediaItem2 convertToMediaItem2(MediaMetadataCompat metadataCompat) {
        MediaMetadata2 metadata2 = convertToMediaMetadata2(metadataCompat);
        if (metadata2 == null) {
            return null;
        }
        return new MediaItem2.Builder(FLAG_PLAYABLE).setMetadata(metadata2).build();
    }

    /**
     * Convert a {@link MediaDescriptionCompat} to a {@link MediaItem2}.
     */
    public static MediaItem2 convertToMediaItem2(MediaDescriptionCompat descriptionCompat) {
        MediaMetadata2 metadata2 = convertToMediaMetadata2(descriptionCompat);
        if (metadata2 == null) {
            return null;
        }
        return new MediaItem2.Builder(FLAG_PLAYABLE).setMetadata(metadata2).build();
    }

    /**
     * Convert a list of {@link MediaItem} to a list of {@link MediaItem2}.
     */
    public static List<MediaItem2> convertMediaItemListToMediaItem2List(List<MediaItem> items) {
        if (items == null) {
            return null;
        }
        List<MediaItem2> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            result.add(convertToMediaItem2(items.get(i)));
        }
        return result;
    }

    /**
     * Convert a list of {@link QueueItem} to a list of {@link MediaItem2}.
     */
    public static List<MediaItem2> convertQueueItemListToMediaItem2List(List<QueueItem> items) {
        if (items == null) {
            return null;
        }
        List<MediaItem2> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            MediaItem2 item = convertToMediaItem2(items.get(i));
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    /**
     * Convert a {@link MediaItem2} to a {@link QueueItem}.
     */
    public static QueueItem convertToQueueItem(MediaItem2 item) {
        if (item == null) {
            return null;
        }
        MediaDescriptionCompat description = (item.getMetadata() == null)
                ? new MediaDescriptionCompat.Builder().setMediaId(item.getMediaId()).build()
                : convertToMediaMetadataCompat(item.getMetadata()).getDescription();
        return new QueueItem(description, item.getUuid().getMostSignificantBits());
    }

    /**
     * Convert a list of {@link MediaItem2} to a list of {@link QueueItem}.
     */
    public static List<QueueItem> convertToQueueItemList(List<MediaItem2> items) {
        if (items == null) {
            return null;
        }
        List<QueueItem> result = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            QueueItem queueItem = convertToQueueItem(items.get(i));
            if (queueItem != null) {
                result.add(queueItem);
            }
        }
        return result;
    }

    /**
     * Convert a {@link ParcelImplListSlice} to a list of {@link MediaItem2}.
     */
    public static List<MediaItem2> convertParcelImplListSliceToMediaItem2List(
            ParcelImplListSlice listSlice) {
        if (listSlice == null) {
            return null;
        }
        List<ParcelImpl> parcelImplList = listSlice.getList();
        List<MediaItem2> mediaItem2List = new ArrayList<>();
        for (int i = 0; i < parcelImplList.size(); i++) {
            final ParcelImpl itemParcelImpl = parcelImplList.get(i);
            if (itemParcelImpl != null) {
                mediaItem2List.add((MediaItem2) ParcelUtils.fromParcelable(itemParcelImpl));
            }
        }
        return mediaItem2List;
    }

    /**
     * Return a list which consists of first {@code N} items of the given list with the same order.
     * {@code N} is determined as the maximum number of items whose total parcelled size is less
     * than {@param sizeLimitInBytes}.
     */
    public static <T extends Parcelable> List<T> truncateListBySize(final List<T> list,
            final int sizeLimitInBytes) {
        if (list == null) {
            return null;
        }
        List<T> result = new ArrayList<>();
        Parcel parcel = Parcel.obtain();
        for (int i = 0; i < list.size(); i++) {
            // Calculate the size.
            T item = list.get(i);
            parcel.writeParcelable(item, 0);
            if (parcel.dataSize() < sizeLimitInBytes) {
                result.add(item);
            } else {
                break;
            }
        }
        parcel.recycle();
        return result;
    }

    /**
     * Creates a {@link MediaMetadata2} from the {@link MediaDescriptionCompat}.
     *
     * @param descCompat A {@link MediaDescriptionCompat} object.
     * @return The newly created {@link MediaMetadata2} object.
     */
    public static MediaMetadata2 convertToMediaMetadata2(MediaDescriptionCompat descCompat) {
        if (descCompat == null) {
            return null;
        }

        MediaMetadata2.Builder metadata2Builder = new MediaMetadata2.Builder();
        metadata2Builder.putString(METADATA_KEY_MEDIA_ID, descCompat.getMediaId());

        CharSequence title = descCompat.getTitle();
        if (title != null) {
            metadata2Builder.putText(METADATA_KEY_DISPLAY_TITLE, title);
        }

        CharSequence description = descCompat.getDescription();
        if (description != null) {
            metadata2Builder.putText(METADATA_KEY_DISPLAY_DESCRIPTION, descCompat.getDescription());
        }

        CharSequence subtitle = descCompat.getSubtitle();
        if (subtitle != null) {
            metadata2Builder.putText(METADATA_KEY_DISPLAY_SUBTITLE, subtitle);
        }

        Bitmap icon = descCompat.getIconBitmap();
        if (icon != null) {
            metadata2Builder.putBitmap(METADATA_KEY_DISPLAY_ICON, icon);
        }

        Uri iconUri = descCompat.getIconUri();
        if (iconUri != null) {
            metadata2Builder.putText(METADATA_KEY_DISPLAY_ICON_URI, iconUri.toString());
        }

        Bundle bundle = descCompat.getExtras();
        if (bundle != null) {
            metadata2Builder.setExtras(descCompat.getExtras());
        }

        Uri mediaUri = descCompat.getMediaUri();
        if (mediaUri != null) {
            metadata2Builder.putText(METADATA_KEY_MEDIA_URI, mediaUri.toString());
        }

        return metadata2Builder.build();
    }

    /**
     * Creates a {@link MediaMetadata2} from the {@link MediaMetadataCompat}.
     *
     * @param metadataCompat A {@link MediaMetadataCompat} object.
     * @return The newly created {@link MediaMetadata2} object.
     */
    public static MediaMetadata2 convertToMediaMetadata2(MediaMetadataCompat metadataCompat) {
        if (metadataCompat == null) {
            return null;
        }
        return new MediaMetadata2(metadataCompat.getBundle());
    }

    /**
     * Creates a {@link MediaMetadata2} from the {@link CharSequence}.
     */
    public static MediaMetadata2 convertToMediaMetadata2(CharSequence queueTitle) {
        if (queueTitle == null) {
            return null;
        }
        return new MediaMetadata2.Builder()
                .putString(METADATA_KEY_TITLE, queueTitle.toString()).build();
    }

    /**
     * Creates a {@link MediaMetadataCompat} from the {@link MediaMetadata2}.
     *
     * @param metadata2 A {@link MediaMetadata2} object.
     * @return The newly created {@link MediaMetadataCompat} object.
     */
    public static MediaMetadataCompat convertToMediaMetadataCompat(MediaMetadata2 metadata2) {
        if (metadata2 == null) {
            return null;
        }

        MediaMetadataCompat.Builder builder = new MediaMetadataCompat.Builder();
        Bundle bundle = metadata2.toBundle();
        for (String key : bundle.keySet()) {
            Object value = bundle.get(key);
            if (value instanceof CharSequence) {
                builder.putText(key, (CharSequence) value);
            } else if (value instanceof Rating2) {
                builder.putRating(key, convertToRatingCompat((Rating2) value));
            } else if (value instanceof Bitmap) {
                builder.putBitmap(key, (Bitmap) value);
            } else if (value instanceof Long) {
                builder.putLong(key, (Long) value);
            }
        }
        return builder.build();
    }

    /**
     * Creates a {@link MediaMetadataCompat} from the {@link MediaDescriptionCompat}.
     */
    public static MediaMetadataCompat convertToMediaMetadataCompat(
            MediaDescriptionCompat description) {
        return convertToMediaMetadataCompat(convertToMediaMetadata2(description));
    }

    /**
     * Creates a {@link Rating2} from the {@link RatingCompat}.
     *
     * @param ratingCompat A {@link RatingCompat} object.
     * @return The newly created {@link Rating2} object.
     */
    public static Rating2 convertToRating2(RatingCompat ratingCompat) {
        if (ratingCompat == null) {
            return null;
        }
        switch (ratingCompat.getRatingStyle()) {
            case RatingCompat.RATING_3_STARS:
                return ratingCompat.isRated()
                        ? new StarRating2(3, ratingCompat.getStarRating()) : new StarRating2(3);
            case RatingCompat.RATING_4_STARS:
                return ratingCompat.isRated()
                        ? new StarRating2(4, ratingCompat.getStarRating()) : new StarRating2(4);
            case RatingCompat.RATING_5_STARS:
                return ratingCompat.isRated()
                        ? new StarRating2(5, ratingCompat.getStarRating()) : new StarRating2(5);
            case RatingCompat.RATING_HEART:
                return ratingCompat.isRated()
                        ? new HeartRating2(ratingCompat.hasHeart()) : new HeartRating2();
            case RatingCompat.RATING_THUMB_UP_DOWN:
                return ratingCompat.isRated()
                        ? new ThumbRating2(ratingCompat.isThumbUp()) : new ThumbRating2();
            case RatingCompat.RATING_PERCENTAGE:
                return ratingCompat.isRated()
                        ? new PercentageRating2(ratingCompat.getPercentRating())
                        : new PercentageRating2();
            default:
                return null;
        }
    }

    /**
     * Creates a {@link RatingCompat} from the {@link Rating2}.
     *
     * @param rating2 A {@link Rating2} object.
     * @return The newly created {@link RatingCompat} object.
     */
    @SuppressLint("WrongConstant") // for @StarStyle
    public static RatingCompat convertToRatingCompat(Rating2 rating2) {
        if (rating2 == null) {
            return null;
        }
        int ratingCompatStyle = getRatingCompatStyle(rating2);
        if (!rating2.isRated()) {
            return RatingCompat.newUnratedRating(ratingCompatStyle);
        }

        switch (ratingCompatStyle) {
            case RatingCompat.RATING_3_STARS:
            case RatingCompat.RATING_4_STARS:
            case RatingCompat.RATING_5_STARS:
                return RatingCompat.newStarRating(
                        ratingCompatStyle, ((StarRating2) rating2).getStarRating());
            case RatingCompat.RATING_HEART:
                return RatingCompat.newHeartRating(((HeartRating2) rating2).hasHeart());
            case RatingCompat.RATING_THUMB_UP_DOWN:
                return RatingCompat.newThumbRating(((ThumbRating2) rating2).isThumbUp());
            case RatingCompat.RATING_PERCENTAGE:
                return RatingCompat.newPercentageRating(
                        ((PercentageRating2) rating2).getPercentRating());
            default:
                return null;
        }
    }

    /**
     * Convert a list of {@link CommandButton} to a list of {@link ParcelImpl}.
     */
    public static List<ParcelImpl> convertCommandButtonListToParcelImplList(
            List<CommandButton> commandButtonList) {
        if (commandButtonList == null) {
            return null;
        }
        List<ParcelImpl> parcelImplList = new ArrayList<>();
        for (int i = 0; i < commandButtonList.size(); i++) {
            final CommandButton commandButton = commandButtonList.get(i);
            parcelImplList.add((ParcelImpl) ParcelUtils.toParcelable(commandButton));
        }
        return parcelImplList;
    }

    /**
     * Convert a list of {@link MediaItem2} to a list of {@link ParcelImplListSlice}.
     */
    public static ParcelImplListSlice convertMediaItem2ListToParcelImplListSlice(
            List<MediaItem2> mediaItem2List) {
        if (mediaItem2List == null) {
            return null;
        }
        List<ParcelImpl> itemParcelableList = new ArrayList<>();
        for (int i = 0; i < mediaItem2List.size(); i++) {
            final MediaItem2 item = mediaItem2List.get(i);
            if (item != null) {
                final ParcelImpl itemParcelImpl = (ParcelImpl) ParcelUtils.toParcelable(item);
                itemParcelableList.add(itemParcelImpl);
            }
        }
        return new ParcelImplListSlice(itemParcelableList);
    }

    /**
     * Convert a {@link SessionPlayer2.PlayerState} and
     * {@link SessionPlayer2.BuffState} into {@link PlaybackStateCompat.State}.
     */
    public static int convertToPlaybackStateCompatState(int playerState, int bufferingState) {
        switch (playerState) {
            case SessionPlayer2.PLAYER_STATE_PLAYING:
                switch (bufferingState) {
                    case SessionPlayer2.BUFFERING_STATE_BUFFERING_AND_STARVED:
                        return PlaybackStateCompat.STATE_BUFFERING;
                }
                return PlaybackStateCompat.STATE_PLAYING;
            case SessionPlayer2.PLAYER_STATE_PAUSED:
                return PlaybackStateCompat.STATE_PAUSED;
            case SessionPlayer2.PLAYER_STATE_IDLE:
                return PlaybackStateCompat.STATE_NONE;
            case SessionPlayer2.PLAYER_STATE_ERROR:
                return PlaybackStateCompat.STATE_ERROR;
        }
        // For unknown value
        return PlaybackStateCompat.STATE_ERROR;
    }

    /**
     * Convert a {@link PlaybackStateCompat} into {@link SessionPlayer2.PlayerState}.
     */
    public static int convertToPlayerState(PlaybackStateCompat state) {
        if (state == null) {
            return SessionPlayer2.PLAYER_STATE_IDLE;
        }
        switch (state.getState()) {
            case PlaybackStateCompat.STATE_ERROR:
                return SessionPlayer2.PLAYER_STATE_ERROR;
            case PlaybackStateCompat.STATE_NONE:
                return SessionPlayer2.PLAYER_STATE_IDLE;
            case PlaybackStateCompat.STATE_PAUSED:
            case PlaybackStateCompat.STATE_STOPPED:
            case PlaybackStateCompat.STATE_BUFFERING: // means paused for buffering.
                return SessionPlayer2.PLAYER_STATE_PAUSED;
            case PlaybackStateCompat.STATE_FAST_FORWARDING:
            case PlaybackStateCompat.STATE_PLAYING:
            case PlaybackStateCompat.STATE_REWINDING:
            case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
            case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
            case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
            case PlaybackStateCompat.STATE_CONNECTING: // Note: there's no perfect match for this.
                return SessionPlayer2.PLAYER_STATE_PLAYING;
        }
        return SessionPlayer2.PLAYER_STATE_ERROR;
    }

    /**
     * Convert a {@link PlaybackStateCompat.State} into {@link SessionPlayer2.BuffState}.
     */
    // Note: there's no perfect match for this.
    public static int toBufferingState(int playbackStateCompatState) {
        switch (playbackStateCompatState) {
            case PlaybackStateCompat.STATE_BUFFERING:
                return SessionPlayer2.BUFFERING_STATE_BUFFERING_AND_STARVED;
            case PlaybackStateCompat.STATE_PLAYING:
                return SessionPlayer2.BUFFERING_STATE_BUFFERING_COMPLETE;
            default:
                return SessionPlayer2.BUFFERING_STATE_UNKNOWN;
        }
    }

    /**
     * Convert a {@link MediaControllerCompat.PlaybackInfo} into
     * {@link MediaController2.PlaybackInfo}.
     */
    public static MediaController2.PlaybackInfo toPlaybackInfo2(
            MediaControllerCompat.PlaybackInfo info) {
        return MediaController2.PlaybackInfo.createPlaybackInfo(info.getPlaybackType(),
                new AudioAttributesCompat.Builder()
                        .setLegacyStreamType(info.getAudioStream()).build(),
                info.getVolumeControl(), info.getMaxVolume(), info.getCurrentVolume());
    }

    /**
     * Returns whether the bundle is not parcelable.
     */
    public static boolean isUnparcelableBundle(Bundle bundle) {
        if (bundle == null) {
            return false;
        }
        bundle.setClassLoader(MediaUtils2.class.getClassLoader());
        try {
            bundle.size();
        } catch (Exception e) {
            return true;
        }
        return false;
    }

    /**
     * Removes unparcelable bundles in the given list.
     */
    public static void keepUnparcelableBundlesOnly(final List<Bundle> bundles) {
        if (bundles == null) {
            return;
        }
        for (int i = bundles.size() - 1; i >= 0; --i) {
            Bundle bundle = bundles.get(i);
            if (isUnparcelableBundle(bundle)) {
                bundles.remove(i);
            }
        }
    }

    private static @RatingCompat.Style int getRatingCompatStyle(Rating2 rating) {
        if (rating instanceof HeartRating2) {
            return RatingCompat.RATING_HEART;
        } else if (rating instanceof ThumbRating2) {
            return RatingCompat.RATING_THUMB_UP_DOWN;
        } else if (rating instanceof StarRating2) {
            switch (((StarRating2) rating).getMaxStars()) {
                case 3:
                    return RatingCompat.RATING_3_STARS;
                case 4:
                    return RatingCompat.RATING_4_STARS;
                case 5:
                    return RatingCompat.RATING_5_STARS;
            }
        } else if (rating instanceof PercentageRating2) {
            return RatingCompat.RATING_PERCENTAGE;
        }
        return RatingCompat.RATING_NONE;
    }
}

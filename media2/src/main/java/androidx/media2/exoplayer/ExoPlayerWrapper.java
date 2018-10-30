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

package androidx.media2.exoplayer;

import static androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP;
import static androidx.media2.MediaPlayer2.MEDIA_ERROR_UNKNOWN;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.core.util.Preconditions;
import androidx.media.AudioAttributesCompat;
import androidx.media2.CallbackMediaItem2;
import androidx.media2.FileMediaItem2;
import androidx.media2.MediaItem2;
import androidx.media2.MediaPlayer2;
import androidx.media2.MediaTimestamp2;
import androidx.media2.PlaybackParams2;
import androidx.media2.exoplayer.external.C;
import androidx.media2.exoplayer.external.DefaultLoadControl;
import androidx.media2.exoplayer.external.DefaultRenderersFactory;
import androidx.media2.exoplayer.external.ExoPlaybackException;
import androidx.media2.exoplayer.external.ExoPlayerFactory;
import androidx.media2.exoplayer.external.Player;
import androidx.media2.exoplayer.external.Renderer;
import androidx.media2.exoplayer.external.SimpleExoPlayer;
import androidx.media2.exoplayer.external.audio.AudioAttributes;
import androidx.media2.exoplayer.external.audio.AudioCapabilities;
import androidx.media2.exoplayer.external.audio.AudioListener;
import androidx.media2.exoplayer.external.audio.AudioProcessor;
import androidx.media2.exoplayer.external.audio.AudioRendererEventListener;
import androidx.media2.exoplayer.external.audio.AuxEffectInfo;
import androidx.media2.exoplayer.external.audio.DefaultAudioSink;
import androidx.media2.exoplayer.external.audio.MediaCodecAudioRenderer;
import androidx.media2.exoplayer.external.drm.DrmSessionManager;
import androidx.media2.exoplayer.external.drm.FrameworkMediaCrypto;
import androidx.media2.exoplayer.external.mediacodec.MediaCodecSelector;
import androidx.media2.exoplayer.external.source.ClippingMediaSource;
import androidx.media2.exoplayer.external.source.ConcatenatingMediaSource;
import androidx.media2.exoplayer.external.source.MediaSource;
import androidx.media2.exoplayer.external.source.TrackGroup;
import androidx.media2.exoplayer.external.source.TrackGroupArray;
import androidx.media2.exoplayer.external.trackselection.DefaultTrackSelector;
import androidx.media2.exoplayer.external.trackselection.TrackSelectionArray;
import androidx.media2.exoplayer.external.upstream.DataSource;
import androidx.media2.exoplayer.external.upstream.DefaultDataSourceFactory;
import androidx.media2.exoplayer.external.util.MimeTypes;
import androidx.media2.exoplayer.external.util.Util;
import androidx.media2.exoplayer.external.video.VideoListener;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Wraps an ExoPlayer instance and provides methods and notifies events like those in the
 * {@link MediaPlayer2} API. {@link #getLooper()} returns the looper on which all other method calls
 * must be made.
 *
 * @hide
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
@RestrictTo(LIBRARY_GROUP)
@SuppressLint("RestrictedApi") // TODO(b/68398926): Remove once RestrictedApi checks are fixed.
/* package */ final class ExoPlayerWrapper {

    private static final String TAG = "ExoPlayerWrapper";

    /** Listener for player wrapper events. */
    public interface Listener {

        /** Called when the player is prepared. */
        void onPrepared(MediaItem2 mediaItem2);

        /** Called when metadata (e.g., the set of available tracks) changes. */
        void onMetadataChanged(MediaItem2 mediaItem2);

        /** Called when a seek request has completed. */
        void onSeekCompleted(long positionMs);

        /** Called when the player rebuffers. */
        void onBufferingStarted(MediaItem2 mediaItem2);

        /** Called when the player becomes ready again after rebuffering. */
        void onBufferingEnded(MediaItem2 mediaItem2);

        /** Called when video rendering of the specified media item has started. */
        void onVideoRenderingStart(MediaItem2 mediaItem2);

        /** Called when the video size of the specified media item has changed. */
        void onVideoSizeChanged(MediaItem2 mediaItem2, int width, int height);

        /** Called when playback transitions to the next media item. */
        void onMediaItem2StartedAsNext(MediaItem2 mediaItem2);

        /** Called when playback of a media item ends. */
        void onMediaItem2Ended(MediaItem2 mediaItem2);

        /** Called when playback of the specified item loops back to its start. */
        void onLoop(MediaItem2 mediaItem2);

        /** Called when a change in the progression of media time is detected. */
        void onMediaTimeDiscontinuity(MediaItem2 mediaItem2, MediaTimestamp2 mediaTimestamp2);

        /** Called when playback of the item list has ended. */
        void onPlaybackEnded(MediaItem2 mediaItem2);

        /** Called when the player encounters an error. */
        void onError(MediaItem2 mediaItem2, int what);

    }

    private static final String USER_AGENT_NAME = "MediaPlayer2";

    private final Context mContext;
    private final Listener mListener;
    private final Looper mLooper;

    private SimpleExoPlayer mPlayer;
    private DefaultAudioSink mAudioSink;
    private MediaItemQueue mMediaItemQueue;

    private boolean mHasAudioAttributes;
    private int mAudioSessionId;
    private int mAuxEffectId;
    private float mAuxEffectSendLevel;
    private boolean mPrepared;
    private boolean mNewlyPrepared;
    private boolean mRebuffering;
    private boolean mPendingSeek;
    private int mVideoWidth;
    private int mVideoHeight;
    private PlaybackParams2 mPlaybackParams2;

    /**
     * Creates a new ExoPlayer wrapper.
     *
     * @param context The context for accessing system components.
     * @param listener A listener for player wrapper events.
     * @param looper The looper that will be used for player events.
     */
    ExoPlayerWrapper(Context context, Listener listener, Looper looper) {
        mContext = context.getApplicationContext();
        mListener = listener;
        mLooper = looper;
    }

    public Looper getLooper() {
        return mLooper;
    }

    public void setMediaItem(MediaItem2 mediaItem2) {
        mMediaItemQueue.setMediaItem2(Preconditions.checkNotNull(mediaItem2));
    }

    public MediaItem2 getCurrentMediaItem() {
        return mMediaItemQueue.getCurrentMediaItem();
    }

    public void prepare() {
        Preconditions.checkState(!mPrepared);
        mMediaItemQueue.preparePlayer();
    }

    public void play() {
        mNewlyPrepared = false;
        if (mPlayer.getPlaybackState() == Player.STATE_ENDED) {
            mPlayer.seekTo(0);
        }
        mPlayer.setPlayWhenReady(true);
    }

    public void pause() {
        mNewlyPrepared = false;
        mPlayer.setPlayWhenReady(false);
    }

    public void seekTo(long position, @MediaPlayer2.SeekMode int mode) {
        mPlayer.setSeekParameters(ExoPlayerUtils.getSeekParameters(mode));
        MediaItem2 mediaItem2 = mMediaItemQueue.getCurrentMediaItem();
        if (mediaItem2 != null) {
            Preconditions.checkArgument(
                    mediaItem2.getStartPosition() <= position
                            && mediaItem2.getEndPosition() >= position,
                    "Requested seek position is out of range : " + position);
            position -= mediaItem2.getStartPosition();
        }
        mPlayer.seekTo(position);
    }

    public long getCurrentPosition() {
        long position = mPlayer.getCurrentPosition();
        MediaItem2 mediaItem2 = mMediaItemQueue.getCurrentMediaItem();
        if (mediaItem2 != null) {
            position += mediaItem2.getStartPosition();
        }
        return position;
    }

    public long getDuration() {
        long duration = mMediaItemQueue.getCurrentMediaItemDuration();
        return duration == C.TIME_UNSET ? -1 : duration;
    }

    public long getBufferedPosition() {
        long position = mPlayer.getBufferedPosition();
        MediaItem2 mediaItem2 = mMediaItemQueue.getCurrentMediaItem();
        if (mediaItem2 != null) {
            position += mediaItem2.getStartPosition();
        }
        return position;
    }

    public @MediaPlayer2.MediaPlayer2State int getState() {
        if (hasError()) {
            return MediaPlayer2.PLAYER_STATE_ERROR;
        }
        if (mNewlyPrepared) {
            return MediaPlayer2.PLAYER_STATE_PREPARED;
        }
        int state = mPlayer.getPlaybackState();
        boolean playWhenReady = mPlayer.getPlayWhenReady();
        // TODO(b/80232248): Return PLAYER_STATE_PREPARED before playback when we have track
        // groups.
        switch (state) {
            case Player.STATE_IDLE:
            case Player.STATE_ENDED:
                return MediaPlayer2.PLAYER_STATE_IDLE;
            case Player.STATE_BUFFERING:
                return MediaPlayer2.PLAYER_STATE_PAUSED;
            case Player.STATE_READY:
                return playWhenReady ? MediaPlayer2.PLAYER_STATE_PLAYING
                        : MediaPlayer2.PLAYER_STATE_PAUSED;
            default:
                throw new IllegalStateException();
        }
    }

    public void loopCurrent(boolean loop) {
        mPlayer.setRepeatMode(loop ? Player.REPEAT_MODE_ONE : Player.REPEAT_MODE_OFF);
    }

    public void skipToNext() {
        mMediaItemQueue.skipToNext();
    }

    public void setNextMediaItem(MediaItem2 mediaItem2) {
        Preconditions.checkState(!mMediaItemQueue.isEmpty());
        mMediaItemQueue.setNextMediaItem2s(Collections.singletonList(mediaItem2));
    }

    public void setNextMediaItems(List<MediaItem2> mediaItem2s) {
        Preconditions.checkState(!mMediaItemQueue.isEmpty());
        mMediaItemQueue.setNextMediaItem2s(Preconditions.checkNotNull(mediaItem2s));
    }

    public void setAudioAttributes(AudioAttributesCompat audioAttributes) {
        mHasAudioAttributes = true;
        mPlayer.setAudioAttributes(ExoPlayerUtils.getAudioAttributes(audioAttributes));
        // Reset the audio session ID, as it gets cleared by setting audio attributes.
        if (mAudioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            mAudioSink.setAudioSessionId(mAudioSessionId);
        }
    }

    public AudioAttributesCompat getAudioAttributes() {
        return mHasAudioAttributes
                ? ExoPlayerUtils.getAudioAttributesCompat(mPlayer.getAudioAttributes()) : null;
    }

    public void setAudioSessionId(int audioSessionId) {
        mAudioSessionId = audioSessionId;
        mAudioSink.setAudioSessionId(mAudioSessionId);
    }

    public int getAudioSessionId() {
        if (Build.VERSION.SDK_INT >= 21 && mAudioSessionId == C.AUDIO_SESSION_ID_UNSET) {
            setAudioSessionId(C.generateAudioSessionIdV21(mContext));
        }
        return mAudioSessionId == C.AUDIO_SESSION_ID_UNSET ? 0 : mAudioSessionId;
    }

    public void attachAuxEffect(int auxEffectId) {
        mAuxEffectId = auxEffectId;
        mPlayer.setAuxEffectInfo(new AuxEffectInfo(auxEffectId, mAuxEffectSendLevel));
    }

    public void setAuxEffectSendLevel(float auxEffectSendLevel) {
        mAuxEffectSendLevel = auxEffectSendLevel;
        mPlayer.setAuxEffectInfo(new AuxEffectInfo(mAuxEffectId, auxEffectSendLevel));
    }

    public void setPlaybackParams(PlaybackParams2 playbackParams2) {
        // TODO(b/80232248): Decide how to handle fallback modes, which ExoPlayer doesn't support.
        mPlaybackParams2 = playbackParams2;
        mPlayer.setPlaybackParameters(ExoPlayerUtils.getPlaybackParameters(mPlaybackParams2));
        mListener.onMediaTimeDiscontinuity(getCurrentMediaItem(), getTimestamp());
    }

    public PlaybackParams2 getPlaybackParams() {
        return mPlaybackParams2;
    }

    public int getVideoWidth() {
        return mVideoWidth;
    }

    public int getVideoHeight() {
        return mVideoHeight;
    }

    public void setSurface(Surface surface) {
        mPlayer.setVideoSurface(surface);
    }

    public void setVolume(float volume) {
        mPlayer.setVolume(volume);
    }

    public float getVolume() {
        return mPlayer.getVolume();
    }

    public List<MediaPlayer2.TrackInfo> getTrackInfo() {
        return ExoPlayerUtils.getTrackInfo(mPlayer.getCurrentTrackGroups());
    }

    @TargetApi(21)
    public PersistableBundle getMetricsV21() {
        TrackGroupArray trackGroupArray = mPlayer.getCurrentTrackGroups();
        long durationMs = mPlayer.getDuration();
        long playingTimeMs = mMediaItemQueue.getCurrentMediaItemPlayingTimeMs();
        @Nullable String primaryAudioMimeType = null;
        @Nullable String primaryVideoMimeType = null;
        for (int i = 0; i < trackGroupArray.length; i++) {
            TrackGroup trackGroup = trackGroupArray.get(i);
            String mimeType = trackGroup.getFormat(0).sampleMimeType;
            if (primaryVideoMimeType == null && MimeTypes.isVideo(mimeType)) {
                primaryVideoMimeType = mimeType;
            } else if (primaryAudioMimeType == null && MimeTypes.isAudio(mimeType)) {
                primaryAudioMimeType = mimeType;
            }
        }
        PersistableBundle bundle = new PersistableBundle();
        if (primaryVideoMimeType != null) {
            bundle.putString(MediaPlayer2.MetricsConstants.MIME_TYPE_VIDEO, primaryVideoMimeType);
        }
        if (primaryAudioMimeType != null) {
            bundle.putString(MediaPlayer2.MetricsConstants.MIME_TYPE_AUDIO, primaryAudioMimeType);
        }
        bundle.putLong(MediaPlayer2.MetricsConstants.DURATION,
                durationMs == C.TIME_UNSET ? -1 : durationMs);
        bundle.putLong(MediaPlayer2.MetricsConstants.PLAYING, playingTimeMs);
        return bundle;
    }

    public MediaTimestamp2 getTimestamp() {
        boolean isPlaying =
                mPlayer.getPlaybackState() == Player.STATE_READY && mPlayer.getPlayWhenReady();
        float speed = isPlaying ? mPlaybackParams2.getSpeed() : 0f;
        return new MediaTimestamp2(C.msToUs(getCurrentPosition()), System.nanoTime(), speed);
    }

    public void reset() {
        if (mPlayer != null) {
            mPlayer.setPlayWhenReady(false);
            mListener.onMediaTimeDiscontinuity(getCurrentMediaItem(), getTimestamp());
            mPlayer.release();
            mMediaItemQueue.clear();
        }
        mAudioSink = new DefaultAudioSink(
                AudioCapabilities.getCapabilities(mContext), new AudioProcessor[0]);
        mPlayer = ExoPlayerFactory.newSimpleInstance(
                mContext,
                new AudioSinkRenderersFactory(mContext, mAudioSink),
                new DefaultTrackSelector(),
                new DefaultLoadControl(),
                /* drmSessionManager= */ null,
                mLooper);
        mMediaItemQueue = new MediaItemQueue(mContext, mPlayer, mListener);
        ComponentListener listener = new ComponentListener();
        mPlayer.addListener(listener);
        mPlayer.addVideoListener(listener);
        mVideoWidth = 0;
        mVideoHeight = 0;
        mPrepared = false;
        mNewlyPrepared = false;
        mRebuffering = false;
        mPendingSeek = false;
        mHasAudioAttributes = false;
        mAudioSessionId = C.AUDIO_SESSION_ID_UNSET;
        mAuxEffectId = AuxEffectInfo.NO_AUX_EFFECT_ID;
        mAuxEffectSendLevel = 0f;
        mPlaybackParams2 = new PlaybackParams2.Builder()
                .setSpeed(1f)
                .setPitch(1f)
                .setAudioFallbackMode(PlaybackParams2.AUDIO_FALLBACK_MODE_DEFAULT)
                .build();
    }

    public void close() {
        if (mPlayer != null) {
            mPlayer.release();
            mPlayer = null;
            mMediaItemQueue.clear();
        }
    }

    public boolean hasError() {
        return mPlayer.getPlaybackError() != null;
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void handleVideoSizeChanged(int width, int height) {
        mVideoWidth = width;
        mVideoHeight = height;
        mListener.onVideoSizeChanged(mMediaItemQueue.getCurrentMediaItem(), width, height);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void handleRenderedFirstFrame() {
        mListener.onVideoRenderingStart(mMediaItemQueue.getCurrentMediaItem());
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void handlePlayerStateChanged(boolean playWhenReady, int state) {
        mListener.onMediaTimeDiscontinuity(getCurrentMediaItem(), getTimestamp());

        if (state == Player.STATE_READY && playWhenReady) {
            maybeUpdateTimerForPlaying();
        } else {
            maybeUpdateTimerForStopped();
        }

        switch (state) {
            case Player.STATE_BUFFERING:
                maybeNotifyBufferingEvents();
                break;
            case Player.STATE_READY:
                maybeNotifyReadyEvents();
                break;
            case Player.STATE_ENDED:
                mMediaItemQueue.onPlayerEnded();
                break;
            case Player.STATE_IDLE:
            default:
                // Do nothing.
                break;
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void handleTracksChanged() {
        mListener.onMetadataChanged(getCurrentMediaItem());
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void handleSeekProcessed() {
        mPendingSeek = true;
        if (mPlayer.getPlaybackState() == Player.STATE_READY) {
            // The player doesn't need to buffer to seek, so handle being ready now.
            maybeNotifyReadyEvents();
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void handlePositionDiscontinuity(@Player.DiscontinuityReason int reason) {
        mListener.onMediaTimeDiscontinuity(getCurrentMediaItem(), getTimestamp());
        mMediaItemQueue.onPositionDiscontinuity(
                reason == Player.DISCONTINUITY_REASON_PERIOD_TRANSITION);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void handlePlayerError(ExoPlaybackException exception) {
        mListener.onError(getCurrentMediaItem(), ExoPlayerUtils.getError(exception));
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void handleAudioSessionId(int audioSessionId) {
        mAudioSessionId = audioSessionId;
    }

    private void maybeUpdateTimerForPlaying() {
        mMediaItemQueue.onPlaying();
    }

    private void maybeUpdateTimerForStopped() {
        mMediaItemQueue.onStopped();
    }

    private void maybeNotifyBufferingEvents() {
        if (mPrepared && !mRebuffering) {
            mRebuffering = true;
            mListener.onBufferingStarted(getCurrentMediaItem());
        }
    }

    private void maybeNotifyReadyEvents() {
        MediaItem2 mediaItem2 = mMediaItemQueue.getCurrentMediaItem();
        boolean prepareComplete = !mPrepared;
        boolean seekComplete = mPendingSeek;
        if (prepareComplete) {
            mPrepared = true;
            mNewlyPrepared = true;
            mMediaItemQueue.onPositionDiscontinuity(/* isPeriodTransition= */ false);
            // TODO(b/80232248): Trigger onInfo with MEDIA_INFO_PREPARED for any item in the data
            // source queue for which the duration is now known, even if this is not the initial
            // preparation.
            mListener.onPrepared(mediaItem2);
        } else if (seekComplete) {
            // TODO(b/80232248): Suppress notification if this is an initial seek for a non-zero
            // start position.
            mPendingSeek = false;
            mListener.onSeekCompleted(getCurrentPosition());
        } else if (mRebuffering) {
            mRebuffering = false;
            mListener.onBufferingEnded(getCurrentMediaItem());
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final class ComponentListener extends Player.DefaultEventListener
            implements VideoListener, AudioListener {

        // DefaultEventListener implementation.

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int state) {
            handlePlayerStateChanged(playWhenReady, state);
        }

        @Override
        public void onTracksChanged(
                TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {
            handleTracksChanged();
        }

        @Override
        public void onSeekProcessed() {
            handleSeekProcessed();
        }

        @Override
        public void onPositionDiscontinuity(@Player.DiscontinuityReason int reason) {
            handlePositionDiscontinuity(reason);
        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            handlePlayerError(error);
        }

        // VideoListener implementation.

        @Override
        public void onVideoSizeChanged(
                final int width,
                final int height,
                int unappliedRotationDegrees,
                float pixelWidthHeightRatio) {
            handleVideoSizeChanged(width, height);
        }

        @Override
        public void onRenderedFirstFrame() {
            handleRenderedFirstFrame();
        }

        @Override
        public void onSurfaceSizeChanged(int width, int height) {}

        // AudioListener implementation.

        @Override
        public void onAudioSessionId(int audioSessionId) {
            handleAudioSessionId(audioSessionId);
        }

        @Override
        public void onAudioAttributesChanged(AudioAttributes audioAttributes) {}

        @Override
        public void onVolumeChanged(float volume) {}

    }

    // TODO(b/80232248): Upstream a setter for the audio session ID then remove this.
    private static final class AudioSinkRenderersFactory extends DefaultRenderersFactory {

        private final DefaultAudioSink mAudioSink;

        AudioSinkRenderersFactory(Context context, DefaultAudioSink audioSink) {
            super(context);
            mAudioSink = audioSink;
        }

        @Override
        protected void buildAudioRenderers(Context context,
                @Nullable DrmSessionManager<FrameworkMediaCrypto> drmSessionManager,
                AudioProcessor[] audioProcessors, Handler eventHandler,
                AudioRendererEventListener eventListener, int extensionRendererMode,
                ArrayList<Renderer> out) {
            out.add(new MediaCodecAudioRenderer(
                    context,
                    MediaCodecSelector.DEFAULT,
                    drmSessionManager,
                    /* playClearSamplesWithoutKeys= */ false,
                    eventHandler,
                    eventListener,
                    mAudioSink));
        }

    }

    private static final class MediaItemInfo {

        final MediaItem2 mMediaItem;
        @Nullable
        final DurationProvidingMediaSource mDurationProvidingMediaSource;
        @Nullable
        final FileDescriptor mFileDescriptor;

        MediaItemInfo(
                MediaItem2 mediaItem,
                @Nullable DurationProvidingMediaSource durationProvidingMediaSource,
                @Nullable FileDescriptor fileDescriptor) {
            mMediaItem = mediaItem;
            mDurationProvidingMediaSource = durationProvidingMediaSource;
            mFileDescriptor = fileDescriptor;
        }

        public void close() {
            try {
                if (mFileDescriptor != null) {
                    FileDescriptorUtil.close(mFileDescriptor);
                } else if (mMediaItem instanceof CallbackMediaItem2) {
                    ((CallbackMediaItem2) mMediaItem).getDataSourceCallback2().close();
                }
            } catch (IOException e) {
                Log.w(TAG, "Error closing media item " + mMediaItem, e);
            }
        }
    }

    private static final class MediaItemQueue {

        private final Listener mListener;
        private final SimpleExoPlayer mPlayer;
        private final DataSource.Factory mDataSourceFactory;
        private final ConcatenatingMediaSource mConcatenatingMediaSource;
        private final ArrayDeque<MediaItemInfo> mMediaItemInfos;

        private long mStartPlayingTimeNs;
        private long mCurrentMediaItemPlayingTimeUs;

        MediaItemQueue(Context context, SimpleExoPlayer player, Listener listener) {
            mPlayer = player;
            mListener = listener;
            String userAgent = Util.getUserAgent(context, USER_AGENT_NAME);
            mDataSourceFactory = new DefaultDataSourceFactory(context, userAgent);
            mConcatenatingMediaSource = new ConcatenatingMediaSource();
            mMediaItemInfos = new ArrayDeque<>();
            mStartPlayingTimeNs = -1;
        }

        public void clear() {
            while (!mMediaItemInfos.isEmpty()) {
                mMediaItemInfos.remove().close();
            }
        }

        public boolean isEmpty() {
            return mConcatenatingMediaSource.getSize() == 0;
        }

        public void setMediaItem2(MediaItem2 mediaItem2) {
            clear();
            mConcatenatingMediaSource.clear();
            setNextMediaItem2s(Collections.singletonList(mediaItem2));
        }

        public void setNextMediaItem2s(List<MediaItem2> mediaItem2s) {
            int size = mConcatenatingMediaSource.getSize();
            if (size > 1) {
                mConcatenatingMediaSource.removeMediaSourceRange(
                        /* fromIndex= */ 1, /* toIndex= */ size);
                while (mMediaItemInfos.size() > 1) {
                    mMediaItemInfos.removeLast().close();
                }
            }

            List<MediaSource> mediaSources = new ArrayList<>(mediaItem2s.size());
            for (MediaItem2 mediaItem2 : mediaItem2s) {
                if (mediaItem2 == null) {
                    mListener.onError(/* mediaItem2= */ null, MEDIA_ERROR_UNKNOWN);
                    return;
                }
                try {
                    appendMediaItem(mediaItem2, mDataSourceFactory, mMediaItemInfos, mediaSources);
                } catch (IOException e) {
                    mListener.onError(mediaItem2, MEDIA_ERROR_UNKNOWN);
                }
            }
            mConcatenatingMediaSource.addMediaSources(mediaSources);
        }

        public void preparePlayer() {
            mPlayer.prepare(mConcatenatingMediaSource);
        }

        @Nullable
        public MediaItem2 getCurrentMediaItem() {
            return mMediaItemInfos.isEmpty() ? null : mMediaItemInfos.peekFirst().mMediaItem;
        }

        public long getCurrentMediaItemDuration() {
            DurationProvidingMediaSource durationProvidingMediaSource =
                    mMediaItemInfos.peekFirst().mDurationProvidingMediaSource;
            if (durationProvidingMediaSource != null) {
                return durationProvidingMediaSource.getDurationMs();
            } else {
                return mPlayer.getDuration();
            }
        }

        public long getCurrentMediaItemPlayingTimeMs() {
            return C.usToMs(mCurrentMediaItemPlayingTimeUs);
        }

        public void skipToNext() {
            // TODO(b/68398926): Play the start position of the next media item.
            mMediaItemInfos.removeFirst().close();
            mConcatenatingMediaSource.removeMediaSource(0);
        }

        public void onPlaying() {
            if (mStartPlayingTimeNs != -1) {
                return;
            }
            mStartPlayingTimeNs = System.nanoTime();
        }

        public void onStopped() {
            if (mStartPlayingTimeNs == -1) {
                return;
            }
            long nowNs = System.nanoTime();
            mCurrentMediaItemPlayingTimeUs += (nowNs - mStartPlayingTimeNs + 500) / 1000;
            mStartPlayingTimeNs = -1;
        }

        public void onPlayerEnded() {
            MediaItem2 mediaItem = getCurrentMediaItem();
            mListener.onMediaItem2Ended(mediaItem);
            mListener.onPlaybackEnded(mediaItem);
        }

        public void onPositionDiscontinuity(boolean isPeriodTransition) {
            MediaItem2 currentMediaItem = getCurrentMediaItem();
            if (isPeriodTransition && mPlayer.getRepeatMode() != Player.REPEAT_MODE_OFF) {
                mListener.onLoop(currentMediaItem);
            }
            int windowIndex = mPlayer.getCurrentWindowIndex();
            if (windowIndex > 0) {
                // We're no longer playing the first item in the queue.
                if (isPeriodTransition) {
                    mListener.onMediaItem2Ended(getCurrentMediaItem());
                }
                for (int i = 0; i < windowIndex; i++) {
                    mMediaItemInfos.removeFirst().close();
                }
                if (isPeriodTransition) {
                    mListener.onMediaItem2StartedAsNext(getCurrentMediaItem());
                }
                mConcatenatingMediaSource.removeMediaSourceRange(0, windowIndex);
                mCurrentMediaItemPlayingTimeUs = 0;
                mStartPlayingTimeNs = -1;
                if (mPlayer.getPlaybackState() == Player.STATE_READY) {
                    onPlaying();
                }
            }
        }

        /**
         * Appends a media source and associated information for the given media item to the
         * collections provided.
         */
        private static void appendMediaItem(
                MediaItem2 mediaItem2,
                DataSource.Factory dataSourceFactory,
                Collection<MediaItemInfo> mediaItemInfos,
                Collection<MediaSource> mediaSources) throws IOException {
            // Create a data source for reading from the file descriptor, if needed.
            FileDescriptor fileDescriptor = null;
            if (mediaItem2 instanceof FileMediaItem2) {
                FileMediaItem2 fileMediaItem2 = (FileMediaItem2) mediaItem2;
                fileDescriptor = FileDescriptorUtil.dup(fileMediaItem2.getFileDescriptor());
                long offset = fileMediaItem2.getFileDescriptorOffset();
                long length = fileMediaItem2.getFileDescriptorLength();
                dataSourceFactory =
                        FileDescriptorDataSource.getFactory(fileDescriptor, offset, length);
            }

            // Create a source for the item.
            MediaSource mediaSource =
                    ExoPlayerUtils.createUnclippedMediaSource(dataSourceFactory, mediaItem2);

            // Apply clipping if needed. Because ExoPlayer doesn't expose the unclipped duration, we
            // wrap the child source in an intermediate source that lets us access its duration.
            DurationProvidingMediaSource durationProvidingMediaSource = null;
            long startPosition = mediaItem2.getStartPosition();
            long endPosition = mediaItem2.getEndPosition();
            if (startPosition != 0L || endPosition != MediaItem2.POSITION_UNKNOWN) {
                durationProvidingMediaSource = new DurationProvidingMediaSource(mediaSource);
                mediaSource = new ClippingMediaSource(
                        durationProvidingMediaSource,
                        C.msToUs(startPosition),
                        C.msToUs(endPosition));
            }

            mediaSources.add(mediaSource);
            mediaItemInfos.add(
                    new MediaItemInfo(mediaItem2, durationProvidingMediaSource, fileDescriptor));
        }

    }

}

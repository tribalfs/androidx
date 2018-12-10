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

package androidx.media2.widget;

import static androidx.media2.MediaSession.SessionResult.RESULT_CODE_INVALID_STATE;
import static androidx.media2.MediaSession.SessionResult.RESULT_CODE_SUCCESS;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Point;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;
import androidx.media.AudioAttributesCompat;
import androidx.media2.FileMediaItem;
import androidx.media2.MediaItem;
import androidx.media2.MediaMetadata;
import androidx.media2.MediaPlayer;
import androidx.media2.MediaPlayer2;
import androidx.media2.MediaSession;
import androidx.media2.RemoteSessionPlayer;
import androidx.media2.SessionCommand;
import androidx.media2.SessionCommandGroup;
import androidx.media2.SessionPlayer;
import androidx.media2.SessionToken;
import androidx.media2.SubtitleData;
import androidx.media2.UriMediaItem;
import androidx.media2.VideoSize;
import androidx.media2.subtitle.Cea708CaptionRenderer;
import androidx.media2.subtitle.ClosedCaptionRenderer;
import androidx.media2.subtitle.SubtitleController;
import androidx.media2.subtitle.SubtitleTrack;
import androidx.mediarouter.media.MediaControlIntent;
import androidx.mediarouter.media.MediaRouteSelector;
import androidx.mediarouter.media.MediaRouter;
import androidx.palette.graphics.Palette;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Base implementation of VideoView.
 */
@RequiresApi(19)
class VideoViewImplBase implements VideoViewImpl, VideoViewInterface.SurfaceListener {
    private static final String TAG = "VideoViewImplBase";
    static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    private static final int STATE_ERROR = -1;
    private static final int STATE_IDLE = 0;
    private static final int STATE_PREPARING = 1;
    private static final int STATE_PREPARED = 2;
    private static final int STATE_PLAYING = 3;
    private static final int STATE_PAUSED = 4;
    private static final int STATE_PLAYBACK_COMPLETED = 5;

    private static final int INVALID_TRACK_INDEX = -1;
    private static final int SIZE_TYPE_EMBEDDED = 0;
    private static final int SIZE_TYPE_FULL = 1;

    private static final String SUBTITLE_TRACK_LANG_UNDEFINED = "und";

    private AudioAttributesCompat mAudioAttributes;

    private VideoView.OnViewTypeChangedListener mViewTypeChangedListener;

    VideoViewInterface mCurrentView;
    VideoViewInterface mTargetView;
    VideoTextureView mTextureView;
    VideoSurfaceView mSurfaceView;

    VideoViewPlayer mMediaPlayer;
    MediaItem mMediaItem;
    MediaControlView mMediaControlView;
    MediaSession mMediaSession;
    private String mTitle;
    Executor mCallbackExecutor;

    View mCurrentMusicView;
    View mMusicFullLandscapeView;
    View mMusicFullPortraitView;
    View mMusicEmbeddedView;
    private Drawable mMusicAlbumDrawable;
    private String mMusicArtistText;

    final Object mLock = new Object();
    @GuardedBy("mLock")
    boolean mCurrentItemIsMusic;

    private int mPrevWidth;
    int mDominantColor;
    private int mSizeType;

    int mTargetState = STATE_IDLE;
    int mCurrentState = STATE_IDLE;
    long mSeekWhenPrepared;  // recording the seek position while preparing

    private ArrayList<Integer> mVideoTrackIndices;
    ArrayList<Integer> mAudioTrackIndices;
    SparseArray<SubtitleTrack> mSubtitleTracks;
    private SubtitleController mSubtitleController;

    // selected audio/subtitle track index as MediaPlayer returns
    int mSelectedAudioTrackIndex;
    int mSelectedSubtitleTrackIndex;

    private SubtitleAnchorView mSubtitleAnchorView;

    VideoView mInstance;

    private MediaRouter mMediaRouter;
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    MediaRouteSelector mRouteSelector;
    MediaRouter.RouteInfo mRoute;
    RoutePlayer mRoutePlayer;

    private final MediaRouter.Callback mRouterCallback = new MediaRouter.Callback() {
        @Override
        public void onRouteSelected(MediaRouter router, MediaRouter.RouteInfo route) {
            if (route.supportsControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK)) {
                // Save local playback state and position
                int localPlaybackState = mCurrentState;
                long localPlaybackPosition = (mMediaSession == null)
                        ? 0 : mMediaSession.getPlayer().getCurrentPosition();

                // Update player
                resetPlayer();
                mRoute = route;
                mRoutePlayer = new RoutePlayer(mInstance.getContext(), mRouteSelector, route);
                // TODO: Replace with MediaSession#setPlaylist once b/110811730 is fixed.
                mRoutePlayer.setMediaItem(mMediaItem);
                mRoutePlayer.setCurrentPosition(localPlaybackPosition);
                ensureSessionWithPlayer(mRoutePlayer);
                if (localPlaybackState == STATE_PLAYING) {
                    mMediaSession.getPlayer().play();
                }
            }
        }

        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route, int reason) {
            long currentPosition = 0;
            int currentState = 0;
            if (mRoute != null && mRoutePlayer != null) {
                currentPosition = mRoutePlayer.getCurrentPosition();
                currentState = mRoutePlayer.getPlayerState();
                mRoutePlayer.close();
                mRoutePlayer = null;
            }
            if (mRoute == route) {
                mRoute = null;
            }
            if (reason != MediaRouter.UNSELECT_REASON_ROUTE_CHANGED) {
                openVideo();
                mMediaSession.getPlayer().seekTo(currentPosition);
                if (currentState == SessionPlayer.PLAYER_STATE_PLAYING) {
                    mMediaSession.getPlayer().play();
                }
            }
        }
    };

    @Override
    public void initialize(
            VideoView instance, Context context,
            @Nullable AttributeSet attrs, int defStyleAttr) {
        mInstance = instance;

        mSelectedSubtitleTrackIndex = INVALID_TRACK_INDEX;

        mAudioAttributes = new AudioAttributesCompat.Builder()
                .setUsage(AudioAttributesCompat.USAGE_MEDIA)
                .setContentType(AudioAttributesCompat.CONTENT_TYPE_MOVIE).build();

        mCallbackExecutor = ContextCompat.getMainExecutor(context);

        mInstance.setFocusable(true);
        mInstance.setFocusableInTouchMode(true);
        mInstance.requestFocus();

        mTextureView = new VideoTextureView(context);
        mSurfaceView = new VideoSurfaceView(context);
        LayoutParams params = new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT);
        mTextureView.setLayoutParams(params);
        mSurfaceView.setLayoutParams(params);
        mTextureView.setSurfaceListener(this);
        mSurfaceView.setSurfaceListener(this);

        mInstance.addView(mTextureView);
        mInstance.addView(mSurfaceView);

        mSubtitleAnchorView = new SubtitleAnchorView(context);
        mSubtitleAnchorView.setLayoutParams(params);
        mSubtitleAnchorView.setBackgroundColor(0);
        mInstance.addView(mSubtitleAnchorView);

        LayoutInflater inflater = (LayoutInflater) mInstance.getContext()
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mMusicFullLandscapeView = inflater.inflate(R.layout.full_landscape_music, null);
        mMusicFullPortraitView = inflater.inflate(R.layout.full_portrait_music, null);
        mMusicEmbeddedView = inflater.inflate(R.layout.embedded_music, null);

        boolean enableControlView = (attrs == null) || attrs.getAttributeBooleanValue(
                "http://schemas.android.com/apk/res-auto",
                "enableControlView", true);
        if (enableControlView) {
            mMediaControlView = new MediaControlView(context);
        }

        // Choose surface view by default
        int viewType = (attrs == null) ? VideoView.VIEW_TYPE_SURFACEVIEW
                : attrs.getAttributeIntValue(
                "http://schemas.android.com/apk/res-auto",
                "viewType", VideoView.VIEW_TYPE_SURFACEVIEW);
        if (viewType == VideoView.VIEW_TYPE_SURFACEVIEW) {
            if (DEBUG) {
                Log.d(TAG, "viewType attribute is surfaceView.");
            }
            mTextureView.setVisibility(View.GONE);
            mSurfaceView.setVisibility(View.VISIBLE);
            mCurrentView = mSurfaceView;
        } else if (viewType == VideoView.VIEW_TYPE_TEXTUREVIEW) {
            if (DEBUG) {
                Log.d(TAG, "viewType attribute is textureView.");
            }
            mTextureView.setVisibility(View.VISIBLE);
            mSurfaceView.setVisibility(View.GONE);
            mCurrentView = mTextureView;
        }
        mTargetView = mCurrentView;

        MediaRouteSelector.Builder builder = new MediaRouteSelector.Builder();
        builder.addControlCategory(MediaControlIntent.CATEGORY_REMOTE_PLAYBACK);
        builder.addControlCategory(MediaControlIntent.CATEGORY_LIVE_AUDIO);
        builder.addControlCategory(MediaControlIntent.CATEGORY_LIVE_VIDEO);
        mRouteSelector = builder.build();
    }

    /**
     * Sets MediaControlView instance. It will replace the previously assigned MediaControlView
     * instance if any.
     *
     * @param mediaControlView a media control view2 instance.
     * @param intervalMs a time interval in milliseconds until VideoView hides MediaControlView.
     */
    @Override
    public void setMediaControlView(@NonNull MediaControlView mediaControlView, long intervalMs) {
        mMediaControlView = mediaControlView;
        mMediaControlView.setShowControllerInterval(intervalMs);

        if (mInstance.isAttachedToWindow()) {
            attachMediaControlView();
        }
    }

    /**
     * Returns MediaControlView instance which is currently attached to VideoView by default or by
     * {@link #setMediaControlView} method.
     */
    @Override
    public MediaControlView getMediaControlView() {
        return mMediaControlView;
    }

    /**
     * Returns {@link SessionToken} so that developers create their own
     * {@link androidx.media2.MediaController} instance. This method should be called when
     * VideoView is attached to window or after {@link #setMediaItem} is called.
     *
     * @throws IllegalStateException if internal MediaSession is not created yet.
     */
    @Override
    @NonNull
    public SessionToken getSessionToken() {
        if (mMediaSession == null) {
            throw new IllegalStateException("MediaSession instance is not available.");
        }
        return mMediaSession.getToken();
    }

    /**
     * Sets the {@link AudioAttributesCompat} to be used during the playback of the video.
     *
     * @param attributes non-null <code>AudioAttributesCompat</code>.
     */
    @Override
    public void setAudioAttributes(@NonNull AudioAttributesCompat attributes) {
        if (attributes == null) {
            throw new IllegalArgumentException("Illegal null AudioAttributes");
        }
        mAudioAttributes = attributes;
    }

    /**
     * Sets {@link MediaItem} object to render using VideoView.
     * @param mediaItem the MediaItem to play
     */
    @Override
    public void setMediaItem(@NonNull MediaItem mediaItem) {
        mSeekWhenPrepared = 0;
        mMediaItem = mediaItem;
        openVideo();
    }

    /**
     * Selects which view will be used to render video between SurfaceView and TextureView.
     *
     * @param viewType the view type to render video
     * <ul>
     * <li>{@link VideoView#VIEW_TYPE_SURFACEVIEW}
     * <li>{@link VideoView#VIEW_TYPE_TEXTUREVIEW}
     * </ul>
     */
    @Override
    public void setViewType(@VideoView.ViewType int viewType) {
        if (viewType == mTargetView.getViewType()) {
            Log.d(TAG, "setViewType with the same type (" + viewType + ") is ignored.");
            return;
        }
        VideoViewInterface targetView;
        if (viewType == VideoView.VIEW_TYPE_TEXTUREVIEW) {
            Log.d(TAG, "switching to TextureView");
            targetView = mTextureView;
        } else if (viewType == VideoView.VIEW_TYPE_SURFACEVIEW) {
            Log.d(TAG, "switching to SurfaceView");
            targetView = mSurfaceView;
        } else {
            throw new IllegalArgumentException("Unknown view type: " + viewType);
        }

        mTargetView = targetView;
        ((View) targetView).setVisibility(View.VISIBLE);
        targetView.takeOver();
        mInstance.requestLayout();
    }

    /**
     * Returns view type.
     *
     * @return view type. See {@see setViewType}.
     */
    @VideoView.ViewType
    @Override
    public int getViewType() {
        return mCurrentView.getViewType();
    }

    /**
     * Registers a callback to be invoked when a view type change is done.
     * {@see #setViewType(int)}
     * @param l The callback that will be run
     */
    @Override
    public void setOnViewTypeChangedListener(VideoView.OnViewTypeChangedListener l) {
        mViewTypeChangedListener = l;
    }

    @Override
    public void onAttachedToWindowImpl() {
        // Note: MediaPlayer2 and MediaSession instances are created in onAttachedToWindow()
        // and closed in onDetachedFromWindow().
        if (mMediaPlayer == null) {
            mMediaPlayer = new VideoViewPlayer(mInstance.getContext());

            mSurfaceView.setMediaPlayer(mMediaPlayer);
            mTextureView.setMediaPlayer(mMediaPlayer);
            mCurrentView.assignSurfaceToMediaPlayer(mMediaPlayer);

            if (mMediaSession != null) {
                mMediaSession.updatePlayer(mMediaPlayer);
            }
        } else {
            if (!mCurrentView.assignSurfaceToMediaPlayer(mMediaPlayer)) {
                Log.w(TAG, "failed to assign surface");
            }
        }

        ensureSessionWithPlayer(mMediaPlayer);

        attachMediaControlView();
        mMediaRouter = MediaRouter.getInstance(mInstance.getContext());
        // TODO: Revisit once ag/4207152 is merged.
        mMediaRouter.setMediaSessionCompat(mMediaSession.getSessionCompat());
        mMediaRouter.addCallback(mRouteSelector, mRouterCallback,
                MediaRouter.CALLBACK_FLAG_PERFORM_ACTIVE_SCAN);
    }

    @Override
    public void onDetachedFromWindowImpl() {
        try {
            mMediaPlayer.close();
        } catch (Exception e) {
        }
        mMediaSession.close();
        mMediaPlayer = null;
        mMediaSession = null;
    }

    @Override
    public void onVisibilityAggregatedImpl(boolean isVisible) {
        if (isMediaPrepared()) {
            if (!isVisible && mCurrentState == STATE_PLAYING) {
                mMediaSession.getPlayer().pause();
            } else if (isVisible && mTargetState == STATE_PLAYING) {
                mMediaSession.getPlayer().play();
            }
        }
    }

    @Override
    public void onTouchEventImpl(MotionEvent ev) {
        if (DEBUG) {
            Log.d(TAG, "onTouchEvent(). mCurrentState=" + mCurrentState
                    + ", mTargetState=" + mTargetState);
        }
    }

    @Override
    public void onTrackballEventImpl(MotionEvent ev) {
        if (DEBUG) {
            Log.d(TAG, "onTrackBallEvent(). mCurrentState=" + mCurrentState
                    + ", mTargetState=" + mTargetState);
        }
    }

    @Override
    public void onMeasureImpl(int widthMeasureSpec, int heightMeasureSpec) {
        synchronized (mLock) {
            if (mCurrentItemIsMusic) {
                int currWidth = mInstance.getMeasuredWidth();
                if (mPrevWidth != currWidth) {
                    Point screenSize = new Point();
                    WindowManager winManager = (WindowManager) mInstance.getContext()
                            .getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
                    winManager.getDefaultDisplay().getSize(screenSize);
                    int screenWidth = screenSize.x;
                    if (currWidth == screenWidth) {
                        int orientation = retrieveOrientation();
                        if (orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
                            updateCurrentMusicView(mMusicFullLandscapeView);
                        } else {
                            updateCurrentMusicView(mMusicFullPortraitView);
                        }

                        if (mSizeType != SIZE_TYPE_FULL) {
                            mSizeType = SIZE_TYPE_FULL;
                        }
                    } else {
                        if (mSizeType != SIZE_TYPE_EMBEDDED) {
                            mSizeType = SIZE_TYPE_EMBEDDED;
                            updateCurrentMusicView(mMusicEmbeddedView);
                        }
                    }
                    mPrevWidth = currWidth;
                }
            }
        }
    }

    ///////////////////////////////////////////////////
    // Implements VideoViewInterface.SurfaceListener
    ///////////////////////////////////////////////////

    @Override
    public void onSurfaceCreated(View view, int width, int height) {
        if (DEBUG) {
            Log.d(TAG, "onSurfaceCreated(). mCurrentState=" + mCurrentState
                    + ", mTargetState=" + mTargetState + ", width/height: " + width + "/" + height
                    + ", " + view.toString());
        }
        if (view == mTargetView) {
            ((VideoViewInterface) view).takeOver();
        }
        if (needToStart()) {
            mMediaSession.getPlayer().play();
        }
    }

    @Override
    public void onSurfaceDestroyed(View view) {
        if (DEBUG) {
            Log.d(TAG, "onSurfaceDestroyed(). mCurrentState=" + mCurrentState
                    + ", mTargetState=" + mTargetState + ", " + view.toString());
        }
    }

    @Override
    public void onSurfaceChanged(View view, int width, int height) {
        if (DEBUG) {
            Log.d(TAG, "onSurfaceChanged(). width/height: " + width + "/" + height
                    + ", " + view.toString());
        }
    }

    @Override
    public void onSurfaceTakeOverDone(VideoViewInterface view) {
        if (view != mTargetView) {
            if (DEBUG) {
                Log.d(TAG, "onSurfaceTakeOverDone(). view is not targetView. ignore.: " + view);
            }
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "onSurfaceTakeOverDone(). Now current view is: " + view);
        }
        if (mCurrentState != STATE_PLAYING) {
            mMediaSession.getPlayer().seekTo(mMediaSession.getPlayer().getCurrentPosition());
        }
        if (view != mCurrentView) {
            ((View) mCurrentView).setVisibility(View.GONE);
            mCurrentView = view;
            if (mViewTypeChangedListener != null) {
                mViewTypeChangedListener.onViewTypeChanged(mInstance, view.getViewType());
            }
        }

        if (needToStart()) {
            mMediaSession.getPlayer().play();
        }
    }

    ///////////////////////////////////////////////////
    // Protected or private methods
    ///////////////////////////////////////////////////
    private void attachMediaControlView() {
        // Get MediaController from MediaSession and set it inside MediaControlView
        mMediaControlView.setSessionToken(mMediaSession.getToken());

        LayoutParams params =
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        mInstance.addView(mMediaControlView, params);
    }

    void ensureSessionWithPlayer(SessionPlayer player) {
        if (mMediaSession != null) {
            SessionPlayer oldPlayer = mMediaSession.getPlayer();
            if (oldPlayer == player) {
                return;
            }
            oldPlayer.unregisterPlayerCallback(mMediaPlayerCallback);
            mMediaSession.updatePlayer(player);
        } else {
            final Context context = mInstance.getContext();
            mMediaSession = new MediaSession.Builder(context, player)
                    .setId("VideoView_" + mInstance.toString())
                    .setSessionCallback(mCallbackExecutor, new MediaSessionCallback())
                    .build();
        }
        player.registerPlayerCallback(mCallbackExecutor, mMediaPlayerCallback);
    }

    private boolean isMediaPrepared() {
        return mMediaSession != null
                && mMediaSession.getPlayer().getPlayerState() != SessionPlayer.PLAYER_STATE_ERROR
                && mMediaSession.getPlayer().getPlayerState() != SessionPlayer.PLAYER_STATE_IDLE;
    }

    boolean needToStart() {
        return (mMediaPlayer != null || mRoutePlayer != null) && isWaitingPlayback();
    }

    private boolean isWaitingPlayback() {
        return mCurrentState != STATE_PLAYING && mTargetState == STATE_PLAYING;
    }

    // Creates a MediaPlayer instance and prepare media item.
    void openVideo() {
        if (DEBUG) {
            Log.d(TAG, "openVideo()");
        }
        if (mMediaItem != null) {
            resetPlayer();
            if (isRemotePlayback()) {
                mRoutePlayer.setMediaItem(mMediaItem);
                return;
            }
        }

        try {
            if (mMediaPlayer == null) {
                mMediaPlayer = new VideoViewPlayer(mInstance.getContext());
            }
            mSurfaceView.setMediaPlayer(mMediaPlayer);
            mTextureView.setMediaPlayer(mMediaPlayer);
            if (!mCurrentView.assignSurfaceToMediaPlayer(mMediaPlayer)) {
                Log.w(TAG, "failed to assign surface");
            }
            mMediaPlayer.setAudioAttributes(mAudioAttributes);

            ensureSessionWithPlayer(mMediaPlayer);
            mMediaPlayer.setMediaItem(mMediaItem);

            final Context context = mInstance.getContext();
            mSubtitleController = new SubtitleController(context);
            mSubtitleController.registerRenderer(new ClosedCaptionRenderer(context));
            mSubtitleController.registerRenderer(new Cea708CaptionRenderer(context));
            mSubtitleController.setAnchor(mSubtitleAnchorView);

            // we don't set the target state here either, but preserve the
            // target state that was there before.
            mCurrentState = STATE_PREPARING;
            mMediaSession.getPlayer().prepare();
        } catch (IllegalArgumentException ex) {
            Log.w(TAG, "Unable to open content: " + mMediaItem, ex);
            mCurrentState = STATE_ERROR;
            mTargetState = STATE_ERROR;
        }
    }

    /*
     * Reset the media player in any state
     */
    void resetPlayer() {
        if (mMediaPlayer != null) {
            mMediaPlayer.reset();
            mTextureView.setMediaPlayer(null);
            mSurfaceView.setMediaPlayer(null);
            mCurrentState = STATE_IDLE;
            mTargetState = STATE_IDLE;
            mSelectedSubtitleTrackIndex = INVALID_TRACK_INDEX;
            mSelectedAudioTrackIndex = INVALID_TRACK_INDEX;
        }
    }

    boolean isRemotePlayback() {
        return mRoutePlayer != null
                && mMediaSession != null
                && (mMediaSession.getPlayer() instanceof RemoteSessionPlayer);
    }

    void selectSubtitleTrack(int trackIndex) {
        if (!isMediaPrepared()) {
            return;
        }
        SubtitleTrack track = mSubtitleTracks.get(trackIndex);
        if (track != null) {
            mMediaPlayer.selectTrack(trackIndex);
            mSubtitleController.selectTrack(track);
            mSelectedSubtitleTrackIndex = trackIndex;
            mSubtitleAnchorView.setVisibility(View.VISIBLE);

            Bundle data = new Bundle();
            data.putInt(MediaControlView.KEY_SELECTED_SUBTITLE_INDEX,
                    mSubtitleTracks.indexOfKey(trackIndex));
            mMediaSession.broadcastCustomCommand(
                    new SessionCommand(MediaControlView.EVENT_UPDATE_SUBTITLE_SELECTED, null),
                    data);
        }
    }

    void deselectSubtitleTrack() {
        if (!isMediaPrepared() || mSelectedSubtitleTrackIndex == INVALID_TRACK_INDEX) {
            return;
        }
        mMediaPlayer.deselectTrack(mSelectedSubtitleTrackIndex);
        mSelectedSubtitleTrackIndex = INVALID_TRACK_INDEX;
        mSubtitleAnchorView.setVisibility(View.GONE);

        mMediaSession.broadcastCustomCommand(
                new SessionCommand(MediaControlView.EVENT_UPDATE_SUBTITLE_DESELECTED, null),
                null);
    }

    // TODO: move this method inside callback to make sure it runs inside the callback thread.
    Bundle extractTrackInfoData() {
        List<MediaPlayer.TrackInfo> trackInfos = mMediaPlayer.getTrackInfo();
        mVideoTrackIndices = new ArrayList<>();
        mAudioTrackIndices = new ArrayList<>();
        mSubtitleTracks = new SparseArray<>();
        ArrayList<String> subtitleTracksLanguageList = new ArrayList<>();
        mSubtitleController.reset();
        for (int i = 0; i < trackInfos.size(); ++i) {
            int trackType = trackInfos.get(i).getTrackType();
            if (trackType == MediaPlayer2.TrackInfo.MEDIA_TRACK_TYPE_VIDEO) {
                mVideoTrackIndices.add(i);
            } else if (trackType == MediaPlayer2.TrackInfo.MEDIA_TRACK_TYPE_AUDIO) {
                mAudioTrackIndices.add(i);
            } else if (trackType == MediaPlayer2.TrackInfo.MEDIA_TRACK_TYPE_SUBTITLE) {
                SubtitleTrack track = mSubtitleController.addTrack(trackInfos.get(i).getFormat());
                if (track != null) {
                    mSubtitleTracks.put(i, track);
                    String language =
                            (trackInfos.get(i).getLanguage().equals(SUBTITLE_TRACK_LANG_UNDEFINED))
                                    ? "" : trackInfos.get(i).getLanguage();
                    subtitleTracksLanguageList.add(language);
                }
            }
        }
        // Select first tracks as default
        if (mAudioTrackIndices.size() > 0) {
            mSelectedAudioTrackIndex = 0;
        }

        synchronized (mLock) {
            mCurrentItemIsMusic = mVideoTrackIndices.size() == 0 && mAudioTrackIndices.size() > 0;
        }

        Bundle data = new Bundle();
        data.putInt(MediaControlView.KEY_VIDEO_TRACK_COUNT, mVideoTrackIndices.size());
        data.putInt(MediaControlView.KEY_AUDIO_TRACK_COUNT, mAudioTrackIndices.size());
        data.putInt(MediaControlView.KEY_SUBTITLE_TRACK_COUNT, mSubtitleTracks.size());
        data.putStringArrayList(MediaControlView.KEY_SUBTITLE_TRACK_LANGUAGE_LIST,
                subtitleTracksLanguageList);
        return data;
    }

    // TODO: move this method inside callback to make sure it runs inside the callback thread.
    MediaMetadata extractMetadata() {
        MediaMetadataRetriever retriever = null;
        String path = "";
        try {
            if (mMediaItem == null) {
                return null;
            } else if (mMediaItem instanceof UriMediaItem) {
                Uri uri = ((UriMediaItem) mMediaItem).getUri();

                // Save file name as title since the file may not have a title Metadata.
                if (UriUtil.isFromNetwork(uri)) {
                    path = uri.getPath();
                } else if ("file".equals(uri.getScheme())) {
                    path = uri.getLastPathSegment();
                } else {
                    // TODO: needs default title. b/120515913
                }
                retriever = new MediaMetadataRetriever();
                retriever.setDataSource(mInstance.getContext(), uri);
            } else if (mMediaItem instanceof FileMediaItem) {
                retriever = new MediaMetadataRetriever();
                retriever.setDataSource(
                        ((FileMediaItem) mMediaItem).getFileDescriptor(),
                        ((FileMediaItem) mMediaItem).getFileDescriptorOffset(),
                        ((FileMediaItem) mMediaItem).getFileDescriptorLength());
            }
        } catch (IllegalArgumentException e) {
            Log.v(TAG, "Cannot retrieve metadata for this media file.");
            retriever = null;
        }

        MediaMetadata metadata = mMediaItem.getMetadata();

        synchronized (mLock) {
            if (!mCurrentItemIsMusic) {
                mTitle = extractString(metadata,
                        MediaMetadata.METADATA_KEY_TITLE, retriever,
                        MediaMetadataRetriever.METADATA_KEY_TITLE, path);
            } else {
                Resources resources = mInstance.getResources();
                mTitle = extractString(metadata,
                        MediaMetadata.METADATA_KEY_TITLE, retriever,
                        MediaMetadataRetriever.METADATA_KEY_TITLE,
                        resources.getString(R.string.mcv2_music_title_unknown_text));
                mMusicArtistText = extractString(metadata,
                        MediaMetadata.METADATA_KEY_ARTIST,
                        retriever,
                        MediaMetadataRetriever.METADATA_KEY_ARTIST,
                        resources.getString(R.string.mcv2_music_artist_unknown_text));
                mMusicAlbumDrawable = extractAlbumArt(metadata, retriever,
                        resources.getDrawable(R.drawable.ic_default_album_image));
            }

            if (retriever != null) {
                retriever.release();
            }

            // Set duration and title values as MediaMetadata for MediaControlView
            MediaMetadata.Builder builder = new MediaMetadata.Builder();

            if (mCurrentItemIsMusic) {
                builder.putString(MediaMetadata.METADATA_KEY_ARTIST, mMusicArtistText);
            }
            builder.putString(MediaMetadata.METADATA_KEY_TITLE, mTitle);
            builder.putLong(
                    MediaMetadata.METADATA_KEY_DURATION, mMediaSession.getPlayer().getDuration());
            builder.putString(
                    MediaMetadata.METADATA_KEY_MEDIA_ID, mMediaItem.getMediaId());
            builder.putLong(MediaMetadata.METADATA_KEY_PLAYABLE, 1);
            return builder.build();
        }
    }

    // TODO: move this method inside callback to make sure it runs inside the callback thread.
    private String extractString(MediaMetadata metadata, String stringKey,
            MediaMetadataRetriever retriever, int intKey, String defaultValue) {
        String value = null;

        if (metadata != null) {
            value = metadata.getString(stringKey);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        }
        if (retriever != null) {
            value = retriever.extractMetadata(intKey);
        }
        return value == null ? defaultValue : value;
    }

    // TODO: move this method inside callback to make sure it runs inside the callback thread.
    private Drawable extractAlbumArt(MediaMetadata metadata, MediaMetadataRetriever retriever,
            Drawable defaultDrawable) {
        Bitmap bitmap = null;

        if (metadata != null && metadata.containsKey(MediaMetadata.METADATA_KEY_ALBUM_ART)) {
            bitmap = metadata.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        } else if (retriever != null) {
            byte[] album = retriever.getEmbeddedPicture();
            if (album != null) {
                bitmap = BitmapFactory.decodeByteArray(album, 0, album.length);
            }
        }
        if (bitmap != null) {
            Palette.Builder builder = Palette.from(bitmap);
            builder.generate(new Palette.PaletteAsyncListener() {
                @Override
                public void onGenerated(Palette palette) {
                    mDominantColor = palette.getDominantColor(0);
                    if (mCurrentMusicView != null) {
                        mCurrentMusicView.setBackgroundColor(mDominantColor);
                    }
                }
            });
            return new BitmapDrawable(bitmap);
        }
        return defaultDrawable;
    }

    private int retrieveOrientation() {
        DisplayMetrics dm = Resources.getSystem().getDisplayMetrics();
        int width = dm.widthPixels;
        int height = dm.heightPixels;

        return (height > width)
                ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }

    void updateCurrentMusicView(View newMusicView) {
        newMusicView.setBackgroundColor(mDominantColor);

        ImageView albumView = newMusicView.findViewById(R.id.album);
        if (albumView != null) {
            albumView.setImageDrawable(mMusicAlbumDrawable);
        }

        TextView titleView = newMusicView.findViewById(R.id.title);
        if (titleView != null) {
            titleView.setText(mTitle);
        }

        TextView artistView = newMusicView.findViewById(R.id.artist);
        if (artistView != null) {
            artistView.setText(mMusicArtistText);
        }

        mInstance.removeView(mCurrentMusicView);
        mInstance.addView(newMusicView, 0);
        mCurrentMusicView = newMusicView;
    }

    @SuppressLint("SyntheticAccessor")
    MediaPlayer.PlayerCallback mMediaPlayerCallback =
            new MediaPlayer.PlayerCallback() {
                @Override
                public void onVideoSizeChanged(
                        MediaPlayer mp, MediaItem dsd, VideoSize size) {
                    if (DEBUG) {
                        Log.d(TAG, "onVideoSizeChanged(): size: " + size.getWidth() + "/"
                                + size.getHeight());
                    }
                    if (mp != mMediaPlayer) {
                        if (DEBUG) {
                            Log.w(TAG, "onVideoSizeChanged() is ignored. mp is already gone.");
                        }
                        return;
                    }
                    mTextureView.forceLayout();
                    mSurfaceView.forceLayout();
                    mInstance.requestLayout();
                }

                @Override
                public void onInfo(
                        MediaPlayer mp, MediaItem dsd, int what, int extra) {
                    if (DEBUG) {
                        Log.d(TAG, "onInfo()");
                    }
                    if (mp != mMediaPlayer) {
                        if (DEBUG) {
                            Log.w(TAG, "onInfo() is ignored. mp is already gone.");
                        }
                        return;
                    }
                    if (what == MediaPlayer2.MEDIA_INFO_METADATA_UPDATE) {
                        Bundle data = extractTrackInfoData();
                        if (data != null) {
                            mMediaSession.broadcastCustomCommand(
                                    new SessionCommand(MediaControlView.EVENT_UPDATE_TRACK_STATUS,
                                            null), data);
                        }
                    }
                }

                @Override
                public void onError(
                        MediaPlayer mp, MediaItem dsd, int frameworkErr, int implErr) {
                    if (DEBUG) {
                        Log.d(TAG, "Error: " + frameworkErr + "," + implErr);
                    }
                    if (mp != mMediaPlayer) {
                        if (DEBUG) {
                            Log.w(TAG, "onError() is ignored. mp is already gone.");
                        }
                        return;
                    }
                    if (mCurrentState != STATE_ERROR) {
                        mCurrentState = STATE_ERROR;
                        mTargetState = STATE_ERROR;
                    }
                }

                @Override
                public void onSubtitleData(
                        MediaPlayer mp, MediaItem dsd, SubtitleData data) {
                    if (DEBUG) {
                        Log.d(TAG, "onSubtitleData(): getTrackIndex: " + data.getTrackIndex()
                                + ", getCurrentPosition: " + mp.getCurrentPosition()
                                + ", getStartTimeUs(): " + data.getStartTimeUs()
                                + ", diff: "
                                + (data.getStartTimeUs() / 1000 - mp.getCurrentPosition())
                                + "ms, getDurationUs(): " + data.getDurationUs());
                    }
                    if (mp != mMediaPlayer) {
                        if (DEBUG) {
                            Log.w(TAG, "onSubtitleData() is ignored. mp is already gone.");
                        }
                        return;
                    }
                    final int index = data.getTrackIndex();
                    if (index != mSelectedSubtitleTrackIndex) {
                        return;
                    }
                    SubtitleTrack track = mSubtitleTracks.get(index);
                    if (track != null) {
                        track.onData(data);
                    }
                }

                @Override
                public void onPlayerStateChanged(@NonNull SessionPlayer player,
                        @SessionPlayer.PlayerState int state) {
                    switch (state) {
                        case SessionPlayer.PLAYER_STATE_IDLE:
                            mCurrentState = STATE_IDLE;
                            break;
                        case SessionPlayer.PLAYER_STATE_PLAYING:
                            mCurrentState = STATE_PLAYING;
                            break;
                        case SessionPlayer.PLAYER_STATE_PAUSED:
                            if (mCurrentState == STATE_PREPARING) {
                                onPrepared(player);
                            }
                            mCurrentState = STATE_PAUSED;
                            break;
                        case SessionPlayer.PLAYER_STATE_ERROR:
                            mCurrentState = STATE_ERROR;
                            break;
                    }
                }

                private void onPrepared(SessionPlayer player) {
                    if (DEBUG) {
                        Log.d(TAG, "OnPreparedListener(): "
                                + ", mCurrentState=" + mCurrentState
                                + ", mTargetState=" + mTargetState);
                    }
                    mCurrentState = STATE_PREPARED;

                    if (mMediaSession != null) {
                        Bundle data = extractTrackInfoData();
                        if (data != null) {
                            mMediaSession.broadcastCustomCommand(
                                    new SessionCommand(MediaControlView.EVENT_UPDATE_TRACK_STATUS,
                                            null), data);
                        }

                        // Run extractMetadata() in another thread to prevent StrictMode violation.
                        // extractMetadata() contains file IO indirectly,
                        // via MediaMetadataRetriever.
                        MetadataExtractTask task = new MetadataExtractTask();
                        task.execute();
                    }

                    if (mMediaControlView != null) {
                        mMediaControlView.setEnabled(true);

                        Uri uri = (mMediaItem instanceof UriMediaItem)
                                ? ((UriMediaItem) mMediaItem).getUri() : null;
                        if (uri != null && UriUtil.isFromNetwork(uri)) {
                            mMediaControlView.setRouteSelector(mRouteSelector);
                        } else {
                            mMediaControlView.setRouteSelector(null);
                        }
                    }

                    // mSeekWhenPrepared may be changed after seekTo() call
                    long seekToPosition = mSeekWhenPrepared;
                    if (seekToPosition != 0) {
                        mMediaSession.getPlayer().seekTo(seekToPosition);
                    }

                    if (player instanceof VideoViewPlayer) {
                        if (needToStart()) {
                            mMediaSession.getPlayer().play();
                        }
                    }
                }

                private void onCompletion(MediaPlayer mp, MediaItem dsd) {
                    mCurrentState = STATE_PLAYBACK_COMPLETED;
                    mTargetState = STATE_PLAYBACK_COMPLETED;
                }
            };

    class MediaSessionCallback extends MediaSession.SessionCallback {
        @Override
        public SessionCommandGroup onConnect(
                @NonNull MediaSession session,
                @NonNull MediaSession.ControllerInfo controller) {
            if (session != mMediaSession) {
                if (DEBUG) {
                    Log.w(TAG, "onConnect() is ignored. session is already gone.");
                }
            }
            SessionCommandGroup.Builder commandsBuilder = new SessionCommandGroup.Builder()
                    .addCommand(SessionCommand.COMMAND_CODE_PLAYER_PAUSE)
                    .addCommand(SessionCommand.COMMAND_CODE_PLAYER_PLAY)
                    .addCommand(SessionCommand.COMMAND_CODE_PLAYER_PREPARE)
                    .addCommand(SessionCommand.COMMAND_CODE_PLAYER_SET_SPEED)
                    .addCommand(SessionCommand.COMMAND_CODE_SESSION_FAST_FORWARD)
                    .addCommand(SessionCommand.COMMAND_CODE_SESSION_REWIND)
                    .addCommand(SessionCommand.COMMAND_CODE_PLAYER_SEEK_TO)
                    .addCommand(SessionCommand.COMMAND_CODE_VOLUME_SET_VOLUME)
                    .addCommand(SessionCommand.COMMAND_CODE_VOLUME_ADJUST_VOLUME)
                    .addCommand(SessionCommand.COMMAND_CODE_SESSION_PLAY_FROM_URI)
                    .addCommand(SessionCommand.COMMAND_CODE_SESSION_PREPARE_FROM_URI)
                    .addCommand(SessionCommand.COMMAND_CODE_PLAYER_GET_PLAYLIST)
                    .addCommand(SessionCommand.COMMAND_CODE_PLAYER_GET_PLAYLIST_METADATA)
                    .addCommand(new SessionCommand(
                            MediaControlView.COMMAND_SELECT_AUDIO_TRACK, null))
                    .addCommand(new SessionCommand(
                            MediaControlView.COMMAND_SHOW_SUBTITLE, null))
                    .addCommand(new SessionCommand(
                            MediaControlView.COMMAND_HIDE_SUBTITLE, null));
            return commandsBuilder.build();
        }

        @Override
        public MediaSession.SessionResult onCustomCommand(@NonNull MediaSession session,
                @NonNull MediaSession.ControllerInfo controller,
                @NonNull SessionCommand customCommand, @Nullable Bundle args) {
            if (session != mMediaSession) {
                if (DEBUG) {
                    Log.w(TAG, "onCustomCommand() is ignored. session is already gone.");
                }
            }
            if (isRemotePlayback()) {
                // TODO: call mRoutePlayer.onCommand()
                return new MediaSession.SessionResult(RESULT_CODE_SUCCESS, null);
            }
            switch (customCommand.getCustomCommand()) {
                case MediaControlView.COMMAND_SHOW_SUBTITLE:
                    int subtitleIndex = args != null ? args.getInt(
                            MediaControlView.KEY_SELECTED_SUBTITLE_INDEX,
                            INVALID_TRACK_INDEX) : INVALID_TRACK_INDEX;
                    if (subtitleIndex != INVALID_TRACK_INDEX) {
                        int subtitleTrackIndex = mSubtitleTracks.keyAt(subtitleIndex);
                        if (subtitleTrackIndex != mSelectedSubtitleTrackIndex) {
                            selectSubtitleTrack(subtitleTrackIndex);
                        }
                    }
                    break;
                case MediaControlView.COMMAND_HIDE_SUBTITLE:
                    deselectSubtitleTrack();
                    break;
                case MediaControlView.COMMAND_SELECT_AUDIO_TRACK:
                    int audioIndex = (args != null)
                            ? args.getInt(MediaControlView.KEY_SELECTED_AUDIO_INDEX,
                            INVALID_TRACK_INDEX) : INVALID_TRACK_INDEX;
                    if (audioIndex != INVALID_TRACK_INDEX) {
                        int audioTrackIndex = mAudioTrackIndices.get(audioIndex);
                        if (audioTrackIndex != mSelectedAudioTrackIndex) {
                            mSelectedAudioTrackIndex = audioTrackIndex;
                            mMediaPlayer.selectTrack(mSelectedAudioTrackIndex);
                        }
                    }
                    break;
            }
            return new MediaSession.SessionResult(RESULT_CODE_SUCCESS, null);
        }

        @Override
        public int onCommandRequest(@NonNull MediaSession session,
                @NonNull MediaSession.ControllerInfo controller,
                @NonNull SessionCommand command) {
            if (session != mMediaSession) {
                if (DEBUG) {
                    Log.w(TAG, "onCommandRequest() is ignored. session is already gone.");
                }
            }
            switch (command.getCommandCode()) {
                case SessionCommand.COMMAND_CODE_PLAYER_PLAY:
                    mTargetState = STATE_PLAYING;
                    synchronized (mLock) {
                        if (!mCurrentView.hasAvailableSurface() && !mCurrentItemIsMusic) {
                            Log.d(TAG, "surface is not available");
                            return RESULT_CODE_INVALID_STATE;
                        }
                    }
                    break;
                case SessionCommand.COMMAND_CODE_PLAYER_PAUSE:
                    mTargetState = STATE_PAUSED;
                    break;
                case SessionCommand.COMMAND_CODE_PLAYER_SEEK_TO:
                    mSeekWhenPrepared = 0;
                    break;
            }
            return RESULT_CODE_SUCCESS;
        }
    }

    private class MetadataExtractTask extends AsyncTask<Void, Void, MediaMetadata> {
        MetadataExtractTask() {
        }

        @Override
        protected MediaMetadata doInBackground(Void... params) {
            return extractMetadata();
        }

        @Override
        @SuppressLint("SyntheticAccessor")
        protected void onPostExecute(MediaMetadata metadata) {
            if (metadata != null) {
                mMediaItem.setMetadata(metadata);
            }

            synchronized (mLock) {
                if (mCurrentItemIsMusic) {
                    // Update Music View to reflect the new metadata
                    mInstance.removeView(mSurfaceView);
                    mInstance.removeView(mTextureView);
                    updateCurrentMusicView(mMusicEmbeddedView);
                }
            }
        }
    }
}

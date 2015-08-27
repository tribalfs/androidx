/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.support.v7.app;

import static android.widget.SeekBar.OnSeekBarChangeListener;

import android.content.ContentResolver;
import android.content.Context;
import android.content.IntentSender;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.graphics.Palette;
import android.support.v7.media.MediaRouteSelector;
import android.support.v7.media.MediaRouter;
import android.support.v7.mediarouter.R;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.util.List;

/**
 * This class implements the route controller dialog for {@link MediaRouter}.
 * <p>
 * This dialog allows the user to control or disconnect from the currently selected route.
 * </p>
 *
 * @see MediaRouteButton
 * @see MediaRouteActionProvider
 */
public class MediaRouteControllerDialog extends AlertDialog {
    private static final String TAG = "MediaRouteControllerDialog";

    // STOPSHIP: Remove the flag when the group volume control implementation completes.
    private static final boolean USE_GROUP = false;

    // Time to wait before updating the volume when the user lets go of the seek bar
    // to allow the route provider time to propagate the change and publish a new
    // route descriptor.
    private static final int VOLUME_UPDATE_DELAY_MILLIS = 250;

    private final MediaRouter mRouter;
    private final MediaRouterCallback mCallback;
    private final MediaRouter.RouteInfo mRoute;

    private boolean mCreated;
    private boolean mAttachedToWindow;

    private View mControlView;

    private Button mDisconnectButton;
    private Button mStopCastingButton;
    private ImageButton mPlayPauseButton;
    private ImageButton mCloseButton;
    private ImageButton mGroupExpandCollapseButton;

    private ImageView mArtView;
    private TextView mTitleView;
    private TextView mSubtitleView;
    private TextView mRouteNameView;

    private boolean mVolumeControlEnabled = true;
    private LinearLayout mVolumeLayout;
    private ListView mVolumeGroupList;
    private SeekBar mVolumeSlider;
    private boolean mVolumeSliderTouched;

    private MediaControllerCompat mMediaController;
    private MediaControllerCallback mControllerCallback;
    private PlaybackStateCompat mState;
    private MediaDescriptionCompat mDescription;

    private FetchArtTask mFetchArtTask;

    public MediaRouteControllerDialog(Context context) {
        this(context, 0);
    }

    public MediaRouteControllerDialog(Context context, int theme) {
        super(MediaRouterThemeHelper.createThemedContext(context), theme);
        context = getContext();

        mControllerCallback = new MediaControllerCallback();
        mRouter = MediaRouter.getInstance(context);
        mCallback = new MediaRouterCallback();
        mRoute = mRouter.getSelectedRoute();
        setMediaSession(mRouter.getMediaSessionToken());
    }

    /**
     * Gets the route that this dialog is controlling.
     */
    public MediaRouter.RouteInfo getRoute() {
        return mRoute;
    }

    private MediaRouter.RouteGroup getGroup() {
        if (mRoute instanceof MediaRouter.RouteGroup) {
            return (MediaRouter.RouteGroup) mRoute;
        }
        return null;
    }

    /**
     * Provides the subclass an opportunity to create a view that will
     * be included within the body of the dialog to offer additional media controls
     * for the currently playing content.
     *
     * @param savedInstanceState The dialog's saved instance state.
     * @return The media control view, or null if none.
     */
    public View onCreateMediaControlView(Bundle savedInstanceState) {
        return null;
    }

    /**
     * Gets the media control view that was created by {@link #onCreateMediaControlView(Bundle)}.
     *
     * @return The media control view, or null if none.
     */
    public View getMediaControlView() {
        return mControlView;
    }

    /**
     * Sets whether to enable the volume slider and volume control using the volume keys
     * when the route supports it.
     * <p>
     * The default value is true.
     * </p>
     */
    public void setVolumeControlEnabled(boolean enable) {
        if (mVolumeControlEnabled != enable) {
            mVolumeControlEnabled = enable;
            if (mCreated) {
                updateVolume();
            }
        }
    }

    /**
     * Returns whether to enable the volume slider and volume control using the volume keys
     * when the route supports it.
     */
    public boolean isVolumeControlEnabled() {
        return mVolumeControlEnabled;
    }

    /**
     * Set the session to use for metadata and transport controls. The dialog
     * will listen to changes on this session and update the UI automatically in
     * response to changes.
     *
     * @param sessionToken The token for the session to use.
     */
    private void setMediaSession(MediaSessionCompat.Token sessionToken) {
        if (mMediaController != null) {
            mMediaController.unregisterCallback(mControllerCallback);
            mMediaController = null;
        }
        if (sessionToken == null) {
            return;
        }
        if (!mAttachedToWindow) {
            return;
        }
        try {
            mMediaController = new MediaControllerCompat(getContext(), sessionToken);
        } catch (RemoteException e) {
            Log.e(TAG, "Error creating media controller in setMediaSession.", e);
        }
        if (mMediaController != null) {
            mMediaController.registerCallback(mControllerCallback);
        }
        MediaMetadataCompat metadata = mMediaController == null ? null
                : mMediaController.getMetadata();
        mDescription = metadata == null ? null : metadata.getDescription();
        mState = mMediaController == null ? null : mMediaController.getPlaybackState();
        update();
    }

    /**
     * Gets the description being used by the default UI.
     *
     * @return The current description.
     */
    public MediaSessionCompat.Token getMediaSession() {
        return mMediaController == null ? null : mMediaController.getSessionToken();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.mr_media_route_controller_material_dialog_b);

        ClickListener listener = new ClickListener();

        mDisconnectButton = (Button) findViewById(R.id.disconnect);
        mDisconnectButton.setOnClickListener(listener);

        mStopCastingButton = (Button) findViewById(R.id.stop);
        mStopCastingButton.setOnClickListener(listener);

        mCloseButton = (ImageButton) findViewById(R.id.close);
        mCloseButton.setOnClickListener(listener);

        mArtView = (ImageView) findViewById(R.id.art);
        mTitleView = (TextView) findViewById(R.id.title);
        mSubtitleView = (TextView) findViewById(R.id.subtitle);
        mPlayPauseButton = (ImageButton) findViewById(R.id.play_pause);
        mPlayPauseButton.setOnClickListener(listener);
        mRouteNameView = (TextView) findViewById(R.id.route_name);
        mVolumeLayout = (LinearLayout)findViewById(R.id.media_route_volume_layout);
        mVolumeGroupList = (ListView)findViewById(R.id.media_route_volume_group_list);

        TypedArray styledAttributes = getContext().obtainStyledAttributes(new int[] {
                R.attr.mediaRouteExpandGroupDrawable,
                R.attr.mediaRouteCollapseGroupDrawable });
        final Drawable expandGroupDrawable = styledAttributes.getDrawable(0);
        final Drawable collapseGroupDrawable = styledAttributes.getDrawable(1);
        styledAttributes.recycle();

        mGroupExpandCollapseButton = (ImageButton)findViewById(
                R.id.media_route_group_expand_collapse);
        mGroupExpandCollapseButton.setOnClickListener(new View.OnClickListener() {
            private boolean mIsExpanded;

            @Override
            public void onClick(View v) {
                mIsExpanded = !mIsExpanded;
                if (mIsExpanded) {
                    mGroupExpandCollapseButton.setImageDrawable(collapseGroupDrawable);
                    mVolumeGroupList.setVisibility(View.VISIBLE);
                    mVolumeGroupList.setAdapter(
                            new VolumeGroupAdapter(getContext(), getGroup().getRoutes()));
                } else {
                    mGroupExpandCollapseButton.setImageDrawable(expandGroupDrawable);
                    mVolumeGroupList.setVisibility(View.GONE);
                }
            }
        });
        mVolumeSlider = (SeekBar)findViewById(R.id.media_route_volume_slider);
        mVolumeSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private final Runnable mStopTrackingTouch = new Runnable() {
                @Override
                public void run() {
                    if (mVolumeSliderTouched) {
                        mVolumeSliderTouched = false;
                        updateVolume();
                    }
                }
            };

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (mVolumeSliderTouched) {
                    mVolumeSlider.removeCallbacks(mStopTrackingTouch);
                } else {
                    mVolumeSliderTouched = true;
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Defer resetting mVolumeSliderTouched to allow the media route provider
                // a little time to settle into its new state and publish the final
                // volume update.
                mVolumeSlider.postDelayed(mStopTrackingTouch, VOLUME_UPDATE_DELAY_MILLIS);
            }

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mRoute.requestSetVolume(progress);
                }
            }
        });

        mCreated = true;
        if (update()) {
            mControlView = onCreateMediaControlView(savedInstanceState);
            FrameLayout controlFrame =
                    (FrameLayout)findViewById(R.id.media_route_control_frame);
            if (mControlView != null) {
                controlFrame.findViewById(R.id.default_control_frame).setVisibility(View.GONE);
                controlFrame.addView(mControlView);
            }
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        mAttachedToWindow = true;

        mRouter.addCallback(MediaRouteSelector.EMPTY, mCallback,
                MediaRouter.CALLBACK_FLAG_UNFILTERED_EVENTS);
        setMediaSession(mRouter.getMediaSessionToken());
    }

    @Override
    public void onDetachedFromWindow() {
        mRouter.removeCallback(mCallback);
        setMediaSession(null);
        mAttachedToWindow = false;
        super.onDetachedFromWindow();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mRoute.requestUpdateVolume(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ? -1 : 1);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
                || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private boolean update() {
        if (!mRoute.isSelected() || mRoute.isDefault()) {
            dismiss();
            return false;
        }
        if (!mCreated) {
            return false;
        }

        updateVolume();

        mRouteNameView.setText(mRoute.getName());

        if (mRoute.canDisconnect()) {
            mDisconnectButton.setVisibility(View.VISIBLE);
        } else {
            mDisconnectButton.setVisibility(View.GONE);
        }

        if (mRoute.getSettingsIntent() != null) {
            mCloseButton.setVisibility(View.VISIBLE);
        } else {
            mCloseButton.setVisibility(View.GONE);
        }

        if (mControlView == null) {
            if (mFetchArtTask != null) {
                mFetchArtTask.cancel(true);
            }
            mArtView.setVisibility(View.GONE);
            mFetchArtTask = new FetchArtTask();
            mFetchArtTask.execute();

            CharSequence title = mDescription == null ? null : mDescription.getTitle();
            boolean hasTitle = !TextUtils.isEmpty(title);

            CharSequence subtitle = mDescription == null ? null : mDescription.getSubtitle();
            boolean hasSubtitle = !TextUtils.isEmpty(subtitle);

            if (!hasTitle && !hasSubtitle) {
                mTitleView.setText(R.string.mr_media_route_controller_no_info_available);
                mTitleView.setEnabled(false);
                mTitleView.setVisibility(View.VISIBLE);
                mSubtitleView.setVisibility(View.GONE);
            } else {
                mTitleView.setText(title);
                mTitleView.setEnabled(hasTitle);
                mTitleView.setVisibility(hasTitle ? View.VISIBLE : View.GONE);
                mSubtitleView.setText(subtitle);
                mSubtitleView.setVisibility(hasSubtitle ? View.VISIBLE : View.GONE);
            }
            if (mState != null) {
                boolean isPlaying = mState.getState() == PlaybackStateCompat.STATE_BUFFERING
                        || mState.getState() == PlaybackStateCompat.STATE_PLAYING;
                boolean supportsPlay = (mState.getActions() & (PlaybackStateCompat.ACTION_PLAY
                        | PlaybackStateCompat.ACTION_PLAY_PAUSE)) != 0;
                boolean supportsPause = (mState.getActions() & (PlaybackStateCompat.ACTION_PAUSE
                                | PlaybackStateCompat.ACTION_PLAY_PAUSE)) != 0;
                if (isPlaying && supportsPause) {
                    mPlayPauseButton.setVisibility(View.VISIBLE);
                    mPlayPauseButton.setImageResource(MediaRouterThemeHelper.getThemeResource(
                            getContext(), R.attr.mediaRoutePauseDrawable));
                    mPlayPauseButton.setContentDescription(getContext().getResources()
                            .getText(R.string.mr_media_route_controller_pause));
                } else if (!isPlaying && supportsPlay) {
                    mPlayPauseButton.setVisibility(View.VISIBLE);
                    mPlayPauseButton.setImageResource(MediaRouterThemeHelper.getThemeResource(
                            getContext(), R.attr.mediaRoutePlayDrawable));
                    mPlayPauseButton.setContentDescription(getContext().getResources()
                            .getText(R.string.mr_media_route_controller_play));
                } else {
                    mPlayPauseButton.setVisibility(View.GONE);
                }
            } else {
                mPlayPauseButton.setVisibility(View.GONE);
            }
        }
        return true;
    }

    private void updateVolume() {
        if (!mVolumeSliderTouched) {
            if (isVolumeControlAvailable()) {
                mVolumeLayout.setVisibility(View.VISIBLE);
                mVolumeSlider.setMax(mRoute.getVolumeMax());
                mVolumeSlider.setProgress(mRoute.getVolume());
                if (USE_GROUP) {
                    mGroupExpandCollapseButton.setVisibility(
                            getGroup() != null ? View.VISIBLE : View.GONE);
                }
            } else {
                mVolumeLayout.setVisibility(View.GONE);
            }
        }
    }

    private boolean isVolumeControlAvailable() {
        return mVolumeControlEnabled && mRoute.getVolumeHandling() ==
                MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE;
    }

    private final class MediaRouterCallback extends MediaRouter.Callback {
        @Override
        public void onRouteUnselected(MediaRouter router, MediaRouter.RouteInfo route) {
            update();
        }

        @Override
        public void onRouteChanged(MediaRouter router, MediaRouter.RouteInfo route) {
            update();
        }

        @Override
        public void onRouteVolumeChanged(MediaRouter router, MediaRouter.RouteInfo route) {
            if (route == mRoute) {
                updateVolume();
            }
        }
    }

    private final class MediaControllerCallback extends MediaControllerCompat.Callback {
        @Override
        public void onSessionDestroyed() {
            if (mMediaController != null) {
                mMediaController.unregisterCallback(mControllerCallback);
                mMediaController = null;
            }
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            mState = state;
            update();
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            mDescription = metadata == null ? null : metadata.getDescription();
            update();
        }
    }

    private final class ClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            int id = v.getId();
            if (id == R.id.stop || id == R.id.disconnect) {
                if (mRoute.isSelected()) {
                    mRouter.unselect(id == R.id.stop ?
                            MediaRouter.UNSELECT_REASON_STOPPED :
                            MediaRouter.UNSELECT_REASON_DISCONNECTED);
                }
                dismiss();
            } else if (id == R.id.play_pause) {
                if (mMediaController != null && mState != null) {
                    if (mState.getState() == PlaybackStateCompat.STATE_PLAYING) {
                        mMediaController.getTransportControls().pause();
                    } else {
                        mMediaController.getTransportControls().play();
                    }
                }
            } else if (id == R.id.close) {
                dismiss();
            }
        }
    }

    private class VolumeGroupAdapter extends ArrayAdapter<MediaRouter.RouteInfo> {
        final OnSeekBarChangeListener mOnSeekBarChangeListener = new OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    int position = (int) seekBar.getTag();
                    // TODO: Verify
                    getGroup().getRouteAt(position).requestSetVolume(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // TODO: Implement
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // TODO: Implement
            }
        };

        public VolumeGroupAdapter(Context context, List<MediaRouter.RouteInfo> objects) {
            super(context, 0, objects);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View v = convertView;
            if (v == null) {
                v = LayoutInflater.from(getContext()).inflate(
                        R.layout.mr_media_route_controller_volume_item, null);
            }
            MediaRouter.RouteInfo route = getItem(position);
            if (route != null) {
                TextView textView = (TextView) v.findViewById(R.id.media_route_name);
                textView.setText(route.getName());

                SeekBar volumeSlider = (SeekBar) v.findViewById(R.id.media_route_volume_slider);
                if (route.getVolumeHandling() == MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE) {
                    volumeSlider.setMax(route.getVolumeMax());
                    volumeSlider.setProgress(route.getVolume());
                    volumeSlider.setOnSeekBarChangeListener(mOnSeekBarChangeListener);
                } else {
                    volumeSlider.setMax(100);
                    volumeSlider.setProgress(100);
                    volumeSlider.setEnabled(false);
                }
                volumeSlider.setTag(position);
            }
            return v;
        }
    }

    private class FetchArtTask extends AsyncTask<Void, Void, Bitmap> {
        private int mBackgroundColor;

        @Override
        protected Bitmap doInBackground(Void... arg) {
            Bitmap bitmap = null;
            if (mDescription == null) {
                return null;
            }
            if (mDescription.getIconBitmap() != null) {
                bitmap = mDescription.getIconBitmap();
            } else if (mDescription.getIconUri() != null) {
                Uri iconUri = mDescription.getIconUri();
                String scheme = iconUri.getScheme();
                if (!(ContentResolver.SCHEME_ANDROID_RESOURCE.equals(scheme)
                        || ContentResolver.SCHEME_CONTENT.equals(scheme)
                        || ContentResolver.SCHEME_FILE.equals(scheme))) {
                    Log.w(TAG, "Icon Uri should point to local resources.");
                    return null;
                }
                BufferedInputStream stream = null;
                try {
                    stream = new BufferedInputStream(
                            getContext().getContentResolver().openInputStream(iconUri));

                    // Query bitmap size.
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(stream, null, options);

                    // Rewind the stream in order to restart bitmap decoding.
                    try {
                        stream.reset();
                    } catch (IOException e) {
                        // Failed to rewind the stream, try to reopen it.
                        stream.close();
                        stream = new BufferedInputStream(getContext().getContentResolver()
                                .openInputStream(iconUri));
                    }

                    // Caculate required size to decode the bitmap and possibly resize it.
                    options.inJustDecodeBounds = false;
                    int reqWidth;
                    int reqHeight;
                    if (options.outWidth >= options.outHeight) {
                        // For landscape image, fit width to dialog width.
                        reqWidth = getWindow().getDecorView().getWidth();
                        reqHeight = reqWidth * (options.outHeight / options.outWidth);
                    } else {
                        // For portrait image, fit height to 16:9 ratio case's height.
                        reqHeight = getWindow().getDecorView().getWidth() * 9 / 16;
                        reqWidth = reqHeight * (options.outWidth / options.outHeight);
                    }
                    int ratio = Math.max(
                            options.outWidth / reqWidth, options.outHeight / reqHeight);
                    options.inSampleSize = Math.max(1, Integer.highestOneBit(ratio));
                    if (isCancelled()) {
                        return null;
                    }
                    bitmap = BitmapFactory.decodeStream(stream, null, options);
                } catch (IOException e){
                    Log.w(TAG, "Unable to open content: " + iconUri, e);
                } finally {
                    if (stream != null) {
                        try {
                            stream.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
            if (bitmap != null) {
                if (bitmap.getWidth() < bitmap.getHeight()) {
                    // Portrait image requires background color.
                    mBackgroundColor =
                            new Palette.Builder(bitmap).generate().getDarkVibrantColor(0);
                }
            }
            return bitmap;
        }

        @Override
        protected void onCancelled() {
            mFetchArtTask = null;
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            mFetchArtTask = null;
            mArtView.setImageBitmap(bitmap);
            if (bitmap != null) {
                mArtView.setVisibility(View.VISIBLE);
                if (bitmap.getWidth() < bitmap.getHeight()) {
                    mArtView.setMaxHeight(getWindow().getDecorView().getWidth() * 9 / 16);
                    mArtView.setBackgroundColor(mBackgroundColor);
                } else {
                    mArtView.setMaxHeight(Integer.MAX_VALUE);
                }
            } else {
                mArtView.setVisibility(View.GONE);
            }
        }
    }
}

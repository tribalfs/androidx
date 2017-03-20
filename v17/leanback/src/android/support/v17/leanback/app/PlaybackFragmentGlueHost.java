/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package android.support.v17.leanback.app;

import android.support.v17.leanback.media.PlaybackGlueHost;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.OnItemViewClickedListener;
import android.support.v17.leanback.widget.PlaybackRowPresenter;
import android.support.v17.leanback.widget.PlaybackSeekUi;
import android.support.v17.leanback.widget.Presenter;
import android.support.v17.leanback.widget.Row;
import android.support.v17.leanback.widget.RowPresenter;
import android.view.View;

/**
 * {@link PlaybackGlueHost} implementation
 * the interaction between this class and {@link PlaybackFragment}.
 */
public class PlaybackFragmentGlueHost extends PlaybackGlueHost implements PlaybackSeekUi {
    private final PlaybackFragment mFragment;

    public PlaybackFragmentGlueHost(PlaybackFragment fragment) {
        this.mFragment = fragment;
    }

    @Override
    public void setControlsOverlayAutoHideEnabled(boolean enabled) {
        mFragment.setControlsOverlayAutoHideEnabled(enabled);
    }

    @Override
    public boolean isControlsOverlayAutoHideEnabled() {
        return mFragment.isControlsOverlayAutoHideEnabled();
    }

    @Override
    public void setOnKeyInterceptListener(View.OnKeyListener onKeyListener) {
        mFragment.setOnKeyInterceptListener(onKeyListener);
    }

    @Override
    public void setOnActionClickedListener(final OnActionClickedListener listener) {
        if (listener == null) {
            mFragment.setOnPlaybackItemViewClickedListener(null);
        } else {
            mFragment.setOnPlaybackItemViewClickedListener(new OnItemViewClickedListener() {
                @Override
                public void onItemClicked(Presenter.ViewHolder itemViewHolder, Object item,
                                          RowPresenter.ViewHolder rowViewHolder, Row row) {
                    if (item instanceof Action) {
                        listener.onActionClicked((Action) item);
                    }
                }
            });
        }
    }

    @Override
    public void setHostCallback(HostCallback callback) {
        mFragment.setHostCallback(callback);
    }

    @Override
    public void notifyPlaybackRowChanged() {
        mFragment.notifyPlaybackRowChanged();
    }

    @Override
    public void setPlaybackRowPresenter(PlaybackRowPresenter presenter) {
        mFragment.setPlaybackRowPresenter(presenter);
    }

    @Override
    public void setPlaybackRow(Row row) {
        mFragment.setPlaybackRow(row);
    }

    @Override
    public void fadeOut() {
        mFragment.fadeOut();
    }

    @Override
    public boolean isControlsOverlayVisible() {
        return mFragment.isControlsOverlayVisible();
    }

    @Override
    public void hideControlsOverlay(boolean runAnimation) {
        mFragment.hideControlsOverlay(runAnimation);
    }

    @Override
    public void showControlsOverlay(boolean runAnimation) {
        mFragment.showControlsOverlay(runAnimation);
    }

    @Override
    public void setPlaybackSeekUiClient(Client client) {
        mFragment.setPlaybackSeekUiClient(client);
    }

    final PlayerCallback mPlayerCallback =
            new PlayerCallback() {
                @Override
                public void onBufferingStateChanged(boolean start) {
                    mFragment.onBufferingStateChanged(start);
                }

                @Override
                public void onError(int errorCode, CharSequence errorMessage) {
                    mFragment.onError(errorCode, errorMessage);
                }

                @Override
                public void onVideoSizeChanged(int videoWidth, int videoHeight) {
                    mFragment.onVideoSizeChanged(videoWidth, videoHeight);
                }
            };

    @Override
    public PlayerCallback getPlayerCallback() {
        return mPlayerCallback;
    }
}

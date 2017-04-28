// CHECKSTYLE:OFF Generated code
/* This file is auto-generated from OnboardingDemoFragment.java.  DO NOT MODIFY. */

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
package com.example.android.leanback;

import android.net.Uri;
import android.os.Bundle;
import android.support.v17.leanback.app.VideoSupportFragmentGlueHost;
import android.support.v17.leanback.media.MediaPlayerAdapter;
import android.support.v17.leanback.media.PlaybackGlue;
import android.support.v17.leanback.media.PlaybackTransportControlGlue;
import android.support.v17.leanback.widget.PlaybackControlsRow;

/**
 * Fragment demonstrating the use of {@link android.support.v17.leanback.app.VideoSupportFragment} to
 * render video with playback controls. And demonstrates video seeking with thumbnails.
 *
 * Generate 1 frame per second thumbnail bitmaps and put on sdcard:
 * <pre>
 * sudo apt-get install libav-tools
 * avconv -i input.mp4 -s 240x135 -vsync 1 -r 1 -an -y -qscale 8 frame_%04d.jpg
 * adb shell mkdir /sdcard/seek
 * adb push frame_*.jpg /sdcard/seek/
 * </pre>
 * Change to 1 frame per minute: use "-r 1/60".
 * For more options, see https://wiki.libav.org/Snippets/avconv
 *
 * <p>
 * Showcase:
 * </p>
 * <li>Auto play when ready</li>
 * <li>Set seek provider</li>
 * <li>switch MediaSource</li>
 * <li>switch PlaybackGlue</li>
 */
public class SampleVideoSupportFragment extends android.support.v17.leanback.app.VideoSupportFragment {
    private PlaybackTransportControlGlueSample<MediaPlayerAdapter> mMediaPlayerGlue;

    final VideoSupportFragmentGlueHost mHost = new VideoSupportFragmentGlueHost(SampleVideoSupportFragment.this);

    static void playWhenReady(PlaybackGlue glue) {
        if (glue.isPrepared()) {
            glue.play();
        } else {
            glue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                @Override
                public void onPreparedStateChanged(PlaybackGlue glue) {
                    if (glue.isPrepared()) {
                        glue.removePlayerCallback(this);
                        glue.play();
                    }
                }
            });
        }
    }

    static void loadSeekData(final PlaybackTransportControlGlue glue) {
        if (glue.isPrepared()) {
            glue.setSeekProvider(new PlaybackSeekDiskDataProvider(
                    glue.getDuration(),
                    1000,
                    "/sdcard/seek/frame_%04d.jpg"));
        } else {
            glue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
                @Override
                public void onPreparedStateChanged(PlaybackGlue glue) {
                    if (glue.isPrepared()) {
                        glue.removePlayerCallback(this);
                        PlaybackTransportControlGlue transportControlGlue =
                                (PlaybackTransportControlGlue) glue;
                        transportControlGlue.setSeekProvider(new PlaybackSeekDiskDataProvider(
                                transportControlGlue.getDuration(),
                                1000,
                                "/sdcard/seek/frame_%04d.jpg"));
                    }
                }
            });
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mMediaPlayerGlue = new PlaybackTransportControlGlueSample(getActivity(),
                new MediaPlayerAdapter(getActivity()));
        mMediaPlayerGlue.setHost(mHost);
        mMediaPlayerGlue.setMode(PlaybackControlsRow.RepeatAction.NONE);
        mMediaPlayerGlue.addPlayerCallback(new PlaybackGlue.PlayerCallback() {
            boolean mSecondCompleted = false;
            @Override
            public void onPlayCompleted(PlaybackGlue glue) {
                if (!mSecondCompleted) {
                    mSecondCompleted = true;
                    mMediaPlayerGlue.setSubtitle("Leanback artist Changed!");
                    mMediaPlayerGlue.setTitle("Leanback team at work");
                    String uriPath = "https://storage.googleapis.com/android-tv/Sample videos/"
                            + "April Fool's 2013/Explore Treasure Mode with Google Maps.mp4";
                    mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(uriPath));
                    loadSeekData(mMediaPlayerGlue);
                    playWhenReady(mMediaPlayerGlue);
                } else {
                    mMediaPlayerGlue.removePlayerCallback(this);
                    switchAnotherGlue();
                }
            }
        });
        mMediaPlayerGlue.setSubtitle("Leanback artist");
        mMediaPlayerGlue.setTitle("Leanback team at work");
        String uriPath = "https://storage.googleapis.com/android-tv/Sample videos/"
                + "April Fool's 2013/Explore Treasure Mode with Google Maps.mp4";
        mMediaPlayerGlue.getPlayerAdapter().setDataSource(Uri.parse(uriPath));
        loadSeekData(mMediaPlayerGlue);
        playWhenReady(mMediaPlayerGlue);
    }

    @Override
    public void onPause() {
        if (mMediaPlayerGlue != null) {
            mMediaPlayerGlue.pause();
        }
        super.onPause();
    }

    void switchAnotherGlue() {
        mMediaPlayerGlue = new PlaybackTransportControlGlueSample(getActivity(),
                new MediaPlayerAdapter(getActivity()));
        mMediaPlayerGlue.setMode(PlaybackControlsRow.RepeatAction.ONE);
        mMediaPlayerGlue.setSubtitle("A Googler");
        mMediaPlayerGlue.setTitle("Swimming with the fishes");
        mMediaPlayerGlue.getPlayerAdapter().setDataSource(
                Uri.parse("http://techslides.com/demos/sample-videos/small.mp4"));
        mMediaPlayerGlue.setHost(mHost);
        loadSeekData(mMediaPlayerGlue);
        playWhenReady(mMediaPlayerGlue);
    }
}

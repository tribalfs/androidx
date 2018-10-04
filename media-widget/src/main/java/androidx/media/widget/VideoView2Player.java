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

package androidx.media.widget;

import android.content.Context;

import androidx.media2.MediaItem2;
import androidx.media2.MediaMetadata2;
import androidx.media2.XMediaPlayer;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.List;

class VideoView2Player extends XMediaPlayer {
    VideoView2Player(Context context) {
        super(context);
    }

    private MediaItem2 mMediaItem;

    @Override
    public ListenableFuture<PlayerResult> seekTo(long position) {
        return super.seekTo(position, SEEK_CLOSEST);
    }

    ///////////////////////////////////////////////////////////////////////////////////////
    // Workarounds for not throwing UnsupportedOperationException.
    // TODO: Remove overrides below.
    ///////////////////////////////////////////////////////////////////////////////////////
    @Override
    public List<MediaItem2> getPlaylist() {
        try {
            return super.getPlaylist();
        } finally {
            ArrayList<MediaItem2> list = new ArrayList<>();
            list.add(getCurrentMediaItem());
            return list;
        }
    }

    @Override
    public MediaMetadata2 getPlaylistMetadata() {
        try {
            return super.getPlaylistMetadata();
        } finally {
            return null;
        }
    }

    @Override
    public int getRepeatMode() {
        try {
            return super.getRepeatMode();
        } finally {
            return REPEAT_MODE_NONE;
        }
    }

    @Override
    public int getShuffleMode() {
        try {
            return super.getShuffleMode();
        } finally {
            return SHUFFLE_MODE_NONE;
        }
    }

    @Override
    public ListenableFuture<PlayerResult> setMediaItem(MediaItem2 item) {
        mMediaItem = item;
        return super.setMediaItem(item);
    }

    @Override
    public MediaItem2 getCurrentMediaItem() {
        try {
            return super.getCurrentMediaItem();
        } finally {
            return mMediaItem;
        }
    }
}

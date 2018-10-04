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

package androidx.media.test.client.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.media.AudioAttributesCompat;
import androidx.media.VolumeProviderCompat;
import androidx.media.test.client.MediaTestUtils;
import androidx.media.test.client.RemoteMediaSession2;
import androidx.media.test.lib.TestUtils;
import androidx.media2.FileMediaItem2;
import androidx.media2.MediaItem2;
import androidx.media2.MediaMetadata2;
import androidx.media2.MediaSession2;
import androidx.media2.MediaUtils2;
import androidx.media2.SessionPlayer2;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileDescriptor;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link MediaSession2}.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.JELLY_BEAN)
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaControllerCompatCallbackTestWithMediaSession2 extends MediaSession2TestBase {
    private static final String TAG = "MCCCallbackTestWithMS2";

    private static final long WAIT_TIME_MS = 1000L;

    private RemoteMediaSession2 mSession;
    private MediaControllerCompat mControllerCompat;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mSession = new RemoteMediaSession2(TAG, mContext);
        mControllerCompat = new MediaControllerCompat(mContext, mSession.getCompatToken());
    }

    @After
    @Override
    public void cleanUp() throws Exception {
        super.cleanUp();
        mSession.close();
    }

    @Test
    public void testRepeatModeChange() throws Exception {
        prepareLooper();
        final int testRepeatMode = SessionPlayer2.REPEAT_MODE_GROUP;

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(1);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.getMockPlayer().setRepeatMode(testRepeatMode);
        mSession.getMockPlayer().notifyRepeatModeChanged();
        assertTrue(controllerCallback.await(WAIT_TIME_MS));
        assertTrue(controllerCallback.mOnRepeatModeChangedCalled);
        assertEquals(testRepeatMode, mControllerCompat.getRepeatMode());
    }

    @Test
    public void testShuffleModeChange() throws Exception {
        prepareLooper();
        final int testShuffleMode = SessionPlayer2.SHUFFLE_MODE_GROUP;

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(1);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.getMockPlayer().setShuffleMode(testShuffleMode);
        mSession.getMockPlayer().notifyShuffleModeChanged();
        assertTrue(controllerCallback.await(WAIT_TIME_MS));
        assertTrue(controllerCallback.mOnShuffleModeChangedCalled);
        assertEquals(testShuffleMode, mControllerCompat.getShuffleMode());
    }

    @Test
    public void testClose() throws Exception {
        prepareLooper();
        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(1);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.close();
        assertTrue(controllerCallback.await(WAIT_TIME_MS));
        assertTrue(controllerCallback.mOnSessionDestroyedCalled);
    }

    @Test
    public void testUpdatePlayer() throws Exception {
        prepareLooper();
        final int testState = SessionPlayer2.PLAYER_STATE_PLAYING;
        final int testBufferingPosition = 1500;
        final float testSpeed = 1.5f;
        final List<MediaItem2> testPlaylist = MediaTestUtils.createPlaylist(3);
        final String testPlaylistTitle = "testPlaylistTitle";
        final MediaMetadata2 testPlaylistMetadata = new MediaMetadata2.Builder()
                .putText(MediaMetadata2.METADATA_KEY_DISPLAY_TITLE, testPlaylistTitle).build();

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        // TODO: Make each callback method use their own CountDownLatch.
        if (Build.VERSION.SDK_INT < 21) {
            controllerCallback.reset(7);
        } else {
            // On API 21+, MediaControllerCompat.Callback.onAudioInfoChanged() is called
            // only when the playback type is changed. Since this test method does not change
            // the playback type (local -> local), onAudioInfoChanged will not be called.
            controllerCallback.reset(6);
        }
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        Bundle config = RemoteMediaSession2.createMockPlayerConnectorConfig(
                testState, 0 /* buffState */, 0 /* pos */, testBufferingPosition,
                testSpeed, null /* audioAttrs */, testPlaylist, testPlaylist.get(0),
                testPlaylistMetadata);
        mSession.updatePlayer(config);

        assertTrue(controllerCallback.await(WAIT_TIME_MS));
        assertTrue(controllerCallback.mOnPlaybackStateChangedCalled);
        assertEquals(testState,
                MediaUtils2.convertToPlayerState(controllerCallback.mPlaybackState.getState()));
        assertEquals(testBufferingPosition,
                controllerCallback.mPlaybackState.getBufferedPosition());
        assertEquals(testSpeed, controllerCallback.mPlaybackState.getPlaybackSpeed(), 0.0f);

        assertTrue(controllerCallback.mOnMetadataChangedCalled);
        assertTrue(controllerCallback.mOnQueueChangedCalled);
        assertTrue(controllerCallback.mOnQueueTitleChangedCalled);
        List<QueueItem> queue = mControllerCompat.getQueue();
        assertNotNull(queue);
        assertEquals(testPlaylist.size(), queue.size());
        for (int i = 0; i < testPlaylist.size(); i++) {
            assertEquals(testPlaylist.get(i).getMediaId(),
                    queue.get(i).getDescription().getMediaId());
        }
        assertEquals(testPlaylistTitle, controllerCallback.mTitle);
    }

    @Test
    public void testUpdatePlayer_playbackTypeChangedToRemote() throws Exception {
        prepareLooper();
        final int controlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
        final int maxVolume = 25;
        final int currentVolume = 10;

        final CountDownLatch latch = new CountDownLatch(1);
        final MediaControllerCompat.Callback controllerCallback =
                new MediaControllerCompat.Callback() {
                    @Override
                    public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
                        assertEquals(MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE,
                                info.getPlaybackType());
                        assertEquals(controlType, info.getVolumeControl());
                        assertEquals(maxVolume, info.getMaxVolume(), 0.0f);
                        latch.countDown();
                    }
                };
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        Bundle playerConfig = RemoteMediaSession2.createMockPlayerConnectorConfig(
                controlType, maxVolume, currentVolume, null);
        mSession.updatePlayer(playerConfig);
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));

        MediaControllerCompat.PlaybackInfo info = mControllerCompat.getPlaybackInfo();
        assertEquals(MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE,
                info.getPlaybackType());
        assertEquals(controlType, info.getVolumeControl());
        assertEquals(maxVolume, info.getMaxVolume());
        assertEquals(currentVolume, info.getCurrentVolume());
    }

    @Test
    public void testUpdatePlayer_playbackTypeChangedToLocal() throws Exception {
        prepareLooper();
        Bundle prevPlayerConfig = RemoteMediaSession2.createMockPlayerConnectorConfig(
                VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE, 10 /* maxVolume */,
                1 /* currentVolume */, null /* audioAttrs */);
        mSession.updatePlayer(prevPlayerConfig);

        final int legacyStream = AudioManager.STREAM_RING;
        final AudioAttributesCompat attrs = new AudioAttributesCompat.Builder()
                .setLegacyStreamType(legacyStream).build();

        final CountDownLatch latch = new CountDownLatch(1);
        final MediaControllerCompat.Callback controllerCallback =
                new MediaControllerCompat.Callback() {
                    @Override
                    public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
                        if (MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL
                                == info.getPlaybackType()
                                && legacyStream == info.getAudioStream()) {
                            latch.countDown();
                        }
                    }
                };
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        Bundle playerConfig = RemoteMediaSession2.createMockPlayerConnectorConfig(
                0 /* state */, 0 /* buffState */, 0 /* pos */, 0 /* bufferingPosition */,
                1.0f /* speed */, attrs);
        mSession.updatePlayer(playerConfig);

        // In API 21 and 22, onAudioInfoChanged is not called when playback is changed to local.
        if (Build.VERSION.SDK_INT == 21 || Build.VERSION.SDK_INT == 22) {
            Thread.sleep(WAIT_TIME_MS);
        } else {
            assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        }

        MediaControllerCompat.PlaybackInfo info = mControllerCompat.getPlaybackInfo();
        assertEquals(MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL,
                info.getPlaybackType());
        assertEquals(legacyStream, info.getAudioStream());
    }

    @Test
    public void testUpdatePlayer_playbackTypeNotChanged_local() throws Exception {
        final int legacyStream = AudioManager.STREAM_RING;
        final AudioAttributesCompat attrs = new AudioAttributesCompat.Builder()
                .setLegacyStreamType(legacyStream).build();

        final CountDownLatch latch = new CountDownLatch(1);
        final MediaControllerCompat.Callback controllerCallback =
                new MediaControllerCompat.Callback() {
                    @Override
                    public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
                        assertEquals(MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL,
                                info.getPlaybackType());
                        assertEquals(legacyStream, info.getAudioStream());
                        latch.countDown();
                    }
                };
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        Bundle playerConfig = RemoteMediaSession2.createMockPlayerConnectorConfig(
                0 /* state */, 0 /* buffState */, 0 /* pos */, 0 /* bufferingPosition */,
                1.0f /* speed */, attrs);
        mSession.updatePlayer(playerConfig);

        // In API 21+, onAudioInfoChanged() is not called when playbackType is not changed.
        if (Build.VERSION.SDK_INT >= 21) {
            Thread.sleep(WAIT_TIME_MS);
        } else {
            assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        }

        MediaControllerCompat.PlaybackInfo info = mControllerCompat.getPlaybackInfo();
        assertEquals(MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL,
                info.getPlaybackType());
        assertEquals(legacyStream, info.getAudioStream());
    }

    @Test
    public void testUpdatePlayer_playbackTypeNotChanged_remote() throws Exception {
        prepareLooper();
        Bundle prevPlayerConfig = RemoteMediaSession2.createMockPlayerConnectorConfig(
                VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE, 10 /* maxVolume */,
                1 /* currentVolume */, null /* audioAttrs */);
        mSession.updatePlayer(prevPlayerConfig);

        final int controlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
        final int maxVolume = 25;
        final int currentVolume = 10;

        final CountDownLatch latch = new CountDownLatch(1);
        final MediaControllerCompat.Callback controllerCallback =
                new MediaControllerCompat.Callback() {
                    @Override
                    public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
                        if (MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE
                                == info.getPlaybackType()
                                && controlType == info.getVolumeControl()
                                && maxVolume == info.getMaxVolume()
                                && currentVolume == info.getCurrentVolume()) {
                            latch.countDown();
                        }
                    }
                };
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        Bundle playerConfig = RemoteMediaSession2.createMockPlayerConnectorConfig(
                controlType, maxVolume, currentVolume, null);
        mSession.updatePlayer(playerConfig);

        // In API 21+, onAudioInfoChanged() is not called when playbackType is not changed.
        if (Build.VERSION.SDK_INT >= 21) {
            Thread.sleep(WAIT_TIME_MS);
        } else {
            assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        }

        MediaControllerCompat.PlaybackInfo info = mControllerCompat.getPlaybackInfo();
        assertEquals(MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE,
                info.getPlaybackType());
        assertEquals(controlType, info.getVolumeControl());
        assertEquals(maxVolume, info.getMaxVolume(), 0.0f);
        assertEquals(currentVolume, info.getCurrentVolume(), 0.0f);
    }

    @Test
    public void testPlayerStateChange() throws Exception {
        prepareLooper();
        final int targetState = SessionPlayer2.PLAYER_STATE_PLAYING;

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(1);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.getMockPlayer().notifyPlayerStateChanged(targetState);
        assertTrue(controllerCallback.await(WAIT_TIME_MS));
        assertTrue(controllerCallback.mOnSessionReadyCalled);
        assertTrue(controllerCallback.mOnPlaybackStateChangedCalled);
        assertEquals(targetState,
                MediaUtils2.convertToPlayerState(controllerCallback.mPlaybackState.getState()));
    }

    @Test
    public void testPlaybackSpeedChange() throws Exception {
        prepareLooper();
        final float speed = 1.5f;

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(1);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.getMockPlayer().setPlaybackSpeed(speed);
        mSession.getMockPlayer().notifyPlaybackSpeedChanged(speed);
        assertTrue(controllerCallback.await(WAIT_TIME_MS));
        assertTrue(controllerCallback.mOnPlaybackStateChangedCalled);
        assertEquals(speed, controllerCallback.mPlaybackState.getPlaybackSpeed(), 0.0f);
    }

    @Test
    public void testBufferingStateChange() throws Exception {
        prepareLooper();
        final List<MediaItem2> testPlaylist = MediaTestUtils.createPlaylist(3);
        final int testItemIndex = 0;
        final int testBufferingState = SessionPlayer2.BUFFERING_STATE_BUFFERING_AND_PLAYABLE;
        final long testBufferingPosition = 500;
        mSession.getMockPlayer().setPlaylistWithDummyItem(testPlaylist);

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(1);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.getMockPlayer().setBufferedPosition(testBufferingPosition);
        mSession.getMockPlayer().notifyBufferingStateChanged(testItemIndex, testBufferingState);
        assertTrue(controllerCallback.await(WAIT_TIME_MS));
        assertTrue(controllerCallback.mOnPlaybackStateChangedCalled);
        assertEquals(testBufferingPosition,
                controllerCallback.mPlaybackState.getBufferedPosition(), 0.0f);
    }

    @Test
    public void testSeekComplete() throws Exception {
        prepareLooper();
        final long testSeekPosition = 1300;

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(1);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.getMockPlayer().setCurrentPosition(testSeekPosition);
        mSession.getMockPlayer().setPlayerState(SessionPlayer2.PLAYER_STATE_PAUSED);
        mSession.getMockPlayer().notifySeekCompleted(testSeekPosition);
        assertTrue(controllerCallback.await(TIMEOUT_MS));
        assertTrue(controllerCallback.mOnPlaybackStateChangedCalled);
        assertEquals(testSeekPosition, controllerCallback.mPlaybackState.getPosition());
    }

    @Test
    public void testNotifyError() throws Exception {
        prepareLooper();
        final int errorCode = MediaSession2.ERROR_CODE_NOT_AVAILABLE_IN_REGION;
        final Bundle extras = new Bundle();
        extras.putString("args", "testNotifyError");

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(1);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.notifyError(errorCode, extras);
        assertTrue(controllerCallback.await(TIMEOUT_MS));
        assertTrue(controllerCallback.mOnPlaybackStateChangedCalled);
        assertEquals(errorCode, controllerCallback.mPlaybackState.getErrorCode());
        assertTrue(TestUtils.equals(extras, controllerCallback.mPlaybackState.getExtras()));
    }

    @Test
    public void testCurrentMediaItemChange() throws Exception {
        prepareLooper();

        String displayTitle = "displayTitle";
        MediaMetadata2 metadata = new MediaMetadata2.Builder()
                .putText(MediaMetadata2.METADATA_KEY_DISPLAY_TITLE, displayTitle).build();
        MediaItem2 currentMediaItem = new FileMediaItem2.Builder(new FileDescriptor())
                .setMetadata(metadata)
                .build();

        List<MediaItem2> playlist = MediaTestUtils.createPlaylist(5);
        final int testItemIndex = 3;
        playlist.set(testItemIndex, currentMediaItem);
        mSession.getMockPlayer().setPlaylistWithDummyItem(playlist);

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(1);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.getMockPlayer().setCurrentMediaItem(testItemIndex);
        mSession.getMockPlayer().notifyCurrentMediaItemChanged(testItemIndex);

        assertTrue(controllerCallback.await(TIMEOUT_MS));
        assertTrue(controllerCallback.mOnMetadataChangedCalled);
        assertEquals(displayTitle, controllerCallback.mMediaMetadata
                .getString(MediaMetadata2.METADATA_KEY_DISPLAY_TITLE));
    }

    @Test
    public void testPlaylistAndPlaylistMetadataChange() throws Exception {
        prepareLooper();
        final List<MediaItem2> playlist = MediaTestUtils.createPlaylist(5);
        final String playlistTitle = "playlistTitle";
        MediaMetadata2 playlistMetadata = new MediaMetadata2.Builder()
                .putText(MediaMetadata2.METADATA_KEY_DISPLAY_TITLE, playlistTitle).build();

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(2);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.getMockPlayer().setPlaylist(playlist);
        mSession.getMockPlayer().setPlaylistMetadata(playlistMetadata);
        mSession.getMockPlayer().notifyPlaylistChanged();

        assertTrue(controllerCallback.await(TIMEOUT_MS));
        assertTrue(controllerCallback.mOnQueueChangedCalled);
        assertTrue(controllerCallback.mOnQueueTitleChangedCalled);

        List<QueueItem> queue = mControllerCompat.getQueue();
        assertNotNull(queue);
        assertEquals(playlist.size(), queue.size());
        for (int i = 0; i < playlist.size(); i++) {
            assertEquals(playlist.get(i).getMediaId(), queue.get(i).getDescription().getMediaId());
        }
        assertEquals(playlistTitle, controllerCallback.mTitle);
    }

    @Test
    public void testPlaylistMetadataChange() throws Exception {
        prepareLooper();
        final String playlistTitle = "playlistTitle";
        MediaMetadata2 playlistMetadata = new MediaMetadata2.Builder()
                .putText(MediaMetadata2.METADATA_KEY_DISPLAY_TITLE, playlistTitle).build();

        final MediaControllerCallback controllerCallback = new MediaControllerCallback();
        controllerCallback.reset(1);
        mControllerCompat.registerCallback(controllerCallback, sHandler);

        mSession.getMockPlayer().setPlaylistMetadata(playlistMetadata);
        mSession.getMockPlayer().notifyPlaylistMetadataChanged();

        assertTrue(controllerCallback.await(TIMEOUT_MS));
        assertTrue(controllerCallback.mOnQueueTitleChangedCalled);
        assertEquals(playlistTitle, controllerCallback.mTitle);
    }

    private class MediaControllerCallback extends MediaControllerCompat.Callback {
        private CountDownLatch mLatch;

        private boolean mOnPlaybackStateChangedCalled;
        private boolean mOnMetadataChangedCalled;
        private boolean mOnQueueChangedCalled;
        private boolean mOnQueueTitleChangedCalled;
        private boolean mOnExtraChangedCalled;
        private boolean mOnAudioInfoChangedCalled;
        private boolean mOnSessionDestroyedCalled;
        private boolean mOnSessionEventCalled;
        private boolean mOnCaptioningEnabledChangedCalled;
        private boolean mOnRepeatModeChangedCalled;
        private boolean mOnShuffleModeChangedCalled;
        private boolean mOnSessionReadyCalled;

        private PlaybackStateCompat mPlaybackState;
        private MediaMetadataCompat mMediaMetadata;
        private List<QueueItem> mQueue;
        private CharSequence mTitle;
        private String mEvent;
        private Bundle mExtras;
        private MediaControllerCompat.PlaybackInfo mPlaybackInfo;
        private boolean mCaptioningEnabled;
        private int mRepeatMode;
        private int mShuffleMode;

        public void reset(int count) {
            mLatch = new CountDownLatch(count);
            mOnPlaybackStateChangedCalled = false;
            mOnMetadataChangedCalled = false;
            mOnQueueChangedCalled = false;
            mOnQueueTitleChangedCalled = false;
            mOnExtraChangedCalled = false;
            mOnAudioInfoChangedCalled = false;
            mOnSessionDestroyedCalled = false;
            mOnSessionEventCalled = false;
            mOnRepeatModeChangedCalled = false;
            mOnShuffleModeChangedCalled = false;

            mPlaybackState = null;
            mMediaMetadata = null;
            mQueue = null;
            mTitle = null;
            mExtras = null;
            mPlaybackInfo = null;
            mCaptioningEnabled = false;
            mRepeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
            mShuffleMode = PlaybackStateCompat.SHUFFLE_MODE_NONE;
        }

        public boolean await(long timeoutMs) {
            try {
                return mLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            mOnPlaybackStateChangedCalled = true;
            mPlaybackState = state;
            mLatch.countDown();
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            mOnMetadataChangedCalled = true;
            mMediaMetadata = metadata;
            mLatch.countDown();
        }

        @Override
        public void onQueueChanged(List<QueueItem> queue) {
            mOnQueueChangedCalled = true;
            mQueue = queue;
            mLatch.countDown();
        }

        @Override
        public void onQueueTitleChanged(CharSequence title) {
            mOnQueueTitleChangedCalled = true;
            mTitle = title;
            mLatch.countDown();
        }

        @Override
        public void onExtrasChanged(Bundle extras) {
            mOnExtraChangedCalled = true;
            mExtras = extras;
            mLatch.countDown();
        }

        @Override
        public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            mOnAudioInfoChangedCalled = true;
            mPlaybackInfo = info;
            mLatch.countDown();
        }

        @Override
        public void onSessionDestroyed() {
            mOnSessionDestroyedCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onSessionEvent(String event, Bundle extras) {
            mOnSessionEventCalled = true;
            mEvent = event;
            mExtras = (Bundle) extras.clone();
            mLatch.countDown();
        }

        @Override
        public void onCaptioningEnabledChanged(boolean enabled) {
            mOnCaptioningEnabledChangedCalled = true;
            mCaptioningEnabled = enabled;
            mLatch.countDown();
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            mOnRepeatModeChangedCalled = true;
            mRepeatMode = repeatMode;
            mLatch.countDown();
        }

        @Override
        public void onShuffleModeChanged(int shuffleMode) {
            mOnShuffleModeChangedCalled = true;
            mShuffleMode = shuffleMode;
            mLatch.countDown();
        }

        @Override
        public void onSessionReady() {
            mOnSessionReadyCalled = true;
        }
    }
}

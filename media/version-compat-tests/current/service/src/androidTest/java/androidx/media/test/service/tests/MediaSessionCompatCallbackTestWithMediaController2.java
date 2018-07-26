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

package androidx.media.test.service.tests;

import static android.support.v4.media.session.MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.mediacompat.testlib.util.PollingCheck;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.MediaSessionCompat.QueueItem;
import android.support.v4.media.session.PlaybackStateCompat;

import androidx.media.VolumeProviderCompat;
import androidx.media.test.lib.MockActivity;
import androidx.media.test.lib.TestUtils;
import androidx.media.test.service.MediaTestUtils;
import androidx.media.test.service.RemoteMediaController2;
import androidx.media2.MediaController2;
import androidx.media2.MediaItem2;
import androidx.media2.MediaPlaylistAgent;
import androidx.media2.MediaUtils2;
import androidx.media2.Rating2;
import androidx.media2.SessionCommand2;
import androidx.media2.SessionToken2;
import androidx.media2.StarRating2;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link MediaController2}.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.JELLY_BEAN)
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaSessionCompatCallbackTestWithMediaController2 extends MediaSession2TestBase {
    private static final String TAG = "MediaController2Test";

    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 3000L;

    PendingIntent mIntent;
    MediaSessionCompat mSession;
    MediaSessionCallback mSessionCallback;
    AudioManager mAudioManager;
    RemoteMediaController2 mController;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        final Intent sessionActivity = new Intent(mContext, MockActivity.class);
        // Create this test specific MediaSession2 to use our own Handler.
        mIntent = PendingIntent.getActivity(mContext, 0, sessionActivity, 0);

        mSessionCallback = new MediaSessionCallback();
        mSession = new MediaSessionCompat(mContext, TAG + "Compat");
        mSession.setCallback(mSessionCallback, sHandler);
        mSession.setSessionActivity(mIntent);
        mSession.setActive(true);

        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
    }

    @After
    @Override
    public void cleanUp() throws Exception {
        super.cleanUp();
        if (mSession != null) {
            mSession.release();
            mSession = null;
        }

        if (mController != null) {
            mController.close();
            mController = null;
        }
    }

    private void createControllerAndWaitConnection() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        SessionToken2.createSessionToken2(mContext, mSession.getSessionToken(),
                sHandlerExecutor, new SessionToken2.OnSessionToken2CreatedListener() {
                    @Override
                    public void onSessionToken2Created(
                            MediaSessionCompat.Token token, SessionToken2 token2) {
                        assertTrue(token2.isLegacySession());
                        mController = new RemoteMediaController2(mContext, token2, true);
                        latch.countDown();
                    }
                });
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testPlay() throws Exception {
        prepareLooper();
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.play();
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(1, mSessionCallback.mOnPlayCalledCount);
    }

    @Test
    public void testPause() throws Exception {
        prepareLooper();
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.pause();
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnPauseCalled);
    }

    @Test
    public void testReset() throws Exception {
        prepareLooper();
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.reset();
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnStopCalled);
    }

    @Test
    public void testPrepare() throws Exception {
        prepareLooper();
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.prepare();
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnPrepareCalled);
    }

    @Test
    public void testSeekTo() throws Exception {
        prepareLooper();
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        final long seekPosition = 12125L;
        mController.seekTo(seekPosition);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertTrue(mSessionCallback.mOnSeekToCalled);
        assertEquals(seekPosition, mSessionCallback.mSeekPosition);
    }

    @Test
    public void testAddPlaylistItem() throws Exception {
        prepareLooper();
        final List<MediaItem2> testList = MediaTestUtils.createPlaylist(2);
        final List<QueueItem> testQueue = MediaUtils2.convertToQueueItemList(testList);
        final MediaItem2 testMediaItem2ToAdd = MediaTestUtils.createMediaItemWithMetadata();

        mSession.setQueue(testQueue);
        mSession.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
        createControllerAndWaitConnection();

        final int testIndex = 1;
        mController.addPlaylistItem(testIndex, testMediaItem2ToAdd);
        assertTrue(mSessionCallback.await(TIMEOUT_MS));
        assertTrue(mSessionCallback.mOnAddQueueItemAtCalled);

        assertEquals(testIndex, mSessionCallback.mQueueIndex);
        assertNotNull(mSessionCallback.mQueueDescriptionForAdd);
        assertEquals(testMediaItem2ToAdd.getMediaId(),
                mSessionCallback.mQueueDescriptionForAdd.getMediaId());
    }

    @Test
    public void testRemovePlaylistItem() throws Exception {
        prepareLooper();
        final List<MediaItem2> testList = MediaTestUtils.createPlaylist(2);
        final List<QueueItem> testQueue = MediaUtils2.convertToQueueItemList(testList);

        mSession.setQueue(testQueue);
        mSession.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
        createControllerAndWaitConnection();

        final MediaItem2 itemToRemove = testList.get(1);
        mController.removePlaylistItem(itemToRemove);
        assertTrue(mSessionCallback.await(TIMEOUT_MS));
        assertTrue(mSessionCallback.mOnRemoveQueueItemCalled);

        assertNotNull(mSessionCallback.mQueueDescriptionForRemove);
        assertEquals(itemToRemove.getMediaId(),
                mSessionCallback.mQueueDescriptionForRemove.getMediaId());
    }

    @Test
    public void testReplacePlaylistItem() throws Exception {
        prepareLooper();
        final int testReplaceIndex = 1;
        // replace = remove + add
        final List<MediaItem2> testList = MediaTestUtils.createPlaylist(2);
        final List<QueueItem> testQueue = MediaUtils2.convertToQueueItemList(testList);
        final MediaItem2 testMediaItem2ToReplace = MediaTestUtils.createMediaItemWithMetadata();

        mSession.setQueue(testQueue);
        mSession.setFlags(FLAG_HANDLES_QUEUE_COMMANDS);
        createControllerAndWaitConnection();

        mSessionCallback.reset(2);
        mController.replacePlaylistItem(1, testMediaItem2ToReplace);
        assertTrue(mSessionCallback.await(TIMEOUT_MS));
        assertTrue(mSessionCallback.mOnRemoveQueueItemCalled);
        assertTrue(mSessionCallback.mOnAddQueueItemAtCalled);

        assertNotNull(mSessionCallback.mQueueDescriptionForRemove);
        assertEquals(testList.get(testReplaceIndex).getMediaId(),
                mSessionCallback.mQueueDescriptionForRemove.getMediaId());

        assertNotNull(mSessionCallback.mQueueDescriptionForAdd);
        assertEquals(testMediaItem2ToReplace.getMediaId(),
                mSessionCallback.mQueueDescriptionForAdd.getMediaId());
    }

    @Test
    public void testSkipToPreviousItem() throws Exception {
        prepareLooper();
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.skipToPreviousItem();
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertTrue(mSessionCallback.mOnSkipToPreviousCalled);
    }

    @Test
    public void testSkipToNextItem() throws Exception {
        prepareLooper();
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.skipToNextItem();
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertTrue(mSessionCallback.mOnSkipToNextCalled);
    }

    //@Test see: b/110738672
    public void testSkipToPlaylistItem() throws Exception {
        prepareLooper();
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        final long queueItemId = 1;
        final QueueItem queueItem = new QueueItem(
                MediaUtils2.convertToMediaMetadataCompat(MediaTestUtils.createMetadata())
                        .getDescription(),
                queueItemId);
        final MediaItem2 mediaItem2 = MediaUtils2.convertToMediaItem2(queueItem);

        mController.skipToPlaylistItem(mediaItem2);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertTrue(mSessionCallback.mOnSkipToQueueItemCalled);
        assertEquals(queueItemId, mSessionCallback.mQueueItemId);
    }

    @Test
    public void testSetShuffleMode() throws Exception {
        prepareLooper();
        final int testShuffleMode = MediaPlaylistAgent.SHUFFLE_MODE_GROUP;

        mSession.setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_NONE);
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.setShuffleMode(testShuffleMode);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertTrue(mSessionCallback.mOnSetShuffleModeCalled);
        assertEquals(testShuffleMode, mSessionCallback.mShuffleMode);
    }

    @Test
    public void testSetRepeatMode() throws Exception {
        prepareLooper();
        final int testRepeatMode = MediaPlaylistAgent.REPEAT_MODE_ALL;

        mSession.setRepeatMode(PlaybackStateCompat.REPEAT_MODE_NONE);
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.setRepeatMode(testRepeatMode);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertTrue(mSessionCallback.mOnSetRepeatModeCalled);
        assertEquals(testRepeatMode, mSessionCallback.mRepeatMode);
    }

    @Test
    public void testSetVolumeTo() throws Exception {
        prepareLooper();
        final int maxVolume = 100;
        final int currentVolume = 23;
        final int volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
        TestVolumeProvider volumeProvider =
                new TestVolumeProvider(volumeControlType, maxVolume, currentVolume);
        mSession.setPlaybackToRemote(volumeProvider);
        createControllerAndWaitConnection();

        final int targetVolume = 50;
        mController.setVolumeTo(targetVolume, 0 /* flags */);
        assertTrue(volumeProvider.mLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertTrue(volumeProvider.mSetVolumeToCalled);
        assertEquals(targetVolume, volumeProvider.mVolume);
    }

    @Test
    public void testAdjustVolume() throws Exception {
        prepareLooper();
        final int maxVolume = 100;
        final int currentVolume = 23;
        final int volumeControlType = VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
        TestVolumeProvider volumeProvider =
                new TestVolumeProvider(volumeControlType, maxVolume, currentVolume);
        mSession.setPlaybackToRemote(volumeProvider);
        createControllerAndWaitConnection();

        final int direction = AudioManager.ADJUST_RAISE;
        mController.adjustVolume(direction, 0 /* flags */);
        assertTrue(volumeProvider.mLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertTrue(volumeProvider.mAdjustVolumeCalled);
        assertEquals(direction, volumeProvider.mDirection);
    }

    @Test
    public void testSetVolumeWithLocalVolume() throws Exception {
        prepareLooper();
        if (Build.VERSION.SDK_INT >= 21 && mAudioManager.isVolumeFixed()) {
            // This test is not eligible for this device.
            return;
        }

        // Here, we intentionally choose STREAM_ALARM in order not to consider
        // 'Do Not Disturb' or 'Volume limit'.
        final int stream = AudioManager.STREAM_ALARM;
        final int maxVolume = mAudioManager.getStreamMaxVolume(stream);
        final int minVolume = 0;
        if (maxVolume <= minVolume) {
            return;
        }
        // Set stream of the session.
        mSession.setPlaybackToLocal(stream);
        createControllerAndWaitConnection();
        final int originalVolume = mAudioManager.getStreamVolume(stream);
        final int targetVolume = originalVolume == minVolume
                ? originalVolume + 1 : originalVolume - 1;

        mController.setVolumeTo(targetVolume, AudioManager.FLAG_SHOW_UI);
        new PollingCheck(WAIT_TIME_MS) {
            @Override
            protected boolean check() {
                return targetVolume == mAudioManager.getStreamVolume(stream);
            }
        }.run();

        // Set back to original volume.
        mAudioManager.setStreamVolume(stream, originalVolume, 0 /* flags */);
    }

    @Test
    public void testAdjustVolumeWithLocalVolume() throws Exception {
        prepareLooper();
        if (Build.VERSION.SDK_INT >= 21 && mAudioManager.isVolumeFixed()) {
            // This test is not eligible for this device.
            return;
        }

        // Here, we intentionally choose STREAM_ALARM in order not to consider
        // 'Do Not Disturb' or 'Volume limit'.
        final int stream = AudioManager.STREAM_ALARM;
        final int maxVolume = mAudioManager.getStreamMaxVolume(stream);
        final int minVolume = 0;
        if (maxVolume <= minVolume) {
            return;
        }
        // Set stream of the session.
        mSession.setPlaybackToLocal(stream);
        createControllerAndWaitConnection();

        final int originalVolume = mAudioManager.getStreamVolume(stream);
        final int direction = originalVolume == minVolume
                ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER;
        final int targetVolume = originalVolume + direction;

        mController.adjustVolume(direction, AudioManager.FLAG_SHOW_UI);
        new PollingCheck(WAIT_TIME_MS) {
            @Override
            protected boolean check() {
                return targetVolume == mAudioManager.getStreamVolume(stream);
            }
        }.run();

        // Set back to original volume.
        mAudioManager.setStreamVolume(stream, originalVolume, 0 /* flags */);
    }

    @Test
    public void testSendCustomCommand() throws Exception {
        prepareLooper();
        final String command = "test_custom_command";
        final Bundle testArgs = new Bundle();
        testArgs.putString("args", "test_args");
        final SessionCommand2 testCommand = new SessionCommand2(command, null);
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.sendCustomCommand(testCommand, testArgs, null);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnCommandCalled);
        assertEquals(command, mSessionCallback.mCommand);
        assertTrue(TestUtils.equals(testArgs, mSessionCallback.mExtras));
    }

    @Test
    public void testFastForward() throws Exception {
        prepareLooper();
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.fastForward();
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnFastForwardCalled);
    }

    @Test
    public void testRewind() throws Exception {
        prepareLooper();
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.rewind();
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnRewindCalled);
    }

    @Test
    public void testPlayFromSearch() throws Exception {
        prepareLooper();
        final String request = "random query";
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.playFromSearch(request, bundle);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnPlayFromSearchCalled);
        assertEquals(request, mSessionCallback.mQuery);
        assertTrue(TestUtils.equals(bundle, mSessionCallback.mExtras));
    }

    @Test
    public void testPlayFromUri() throws Exception {
        prepareLooper();
        final Uri request = Uri.parse("foo://boo");
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.playFromUri(request, bundle);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnPlayFromUriCalled);
        assertEquals(request, mSessionCallback.mUri);
        assertTrue(TestUtils.equals(bundle, mSessionCallback.mExtras));
    }

    @Test
    public void testPlayFromMediaId() throws Exception {
        prepareLooper();
        final String request = "media_id";
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.playFromMediaId(request, bundle);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnPlayFromMediaIdCalled);
        assertEquals(request, mSessionCallback.mMediaId);
        assertTrue(TestUtils.equals(bundle, mSessionCallback.mExtras));
    }

    @Test
    @Ignore("b/110738672")
    public void testPrepareFromSearch() throws Exception {
        prepareLooper();
        final String request = "random query";
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.prepareFromSearch(request, bundle);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnPrepareFromSearchCalled);
        assertEquals(request, mSessionCallback.mQuery);
        assertTrue(TestUtils.equals(bundle, mSessionCallback.mExtras));
    }

    @Test
    public void testPrepareFromUri() throws Exception {
        prepareLooper();
        final Uri request = Uri.parse("foo://boo");
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.prepareFromUri(request, bundle);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnPrepareFromUriCalled);
        assertEquals(request, mSessionCallback.mUri);
        assertTrue(TestUtils.equals(bundle, mSessionCallback.mExtras));
    }

    @Test
    public void testPrepareFromMediaId() throws Exception {
        prepareLooper();
        final String request = "media_id";
        final Bundle bundle = new Bundle();
        bundle.putString("key", "value");
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.prepareFromMediaId(request, bundle);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertEquals(true, mSessionCallback.mOnPrepareFromMediaIdCalled);
        assertEquals(request, mSessionCallback.mMediaId);
        assertTrue(TestUtils.equals(bundle, mSessionCallback.mExtras));
    }

    @Test
    public void testSetRating() throws Exception {
        prepareLooper();
        final float ratingValue = 3.5f;
        final Rating2 rating2 = new StarRating2(5, ratingValue);
        final String mediaId = "media_id";
        final MediaMetadataCompat metadata = new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, mediaId).build();
        mSession.setMetadata(metadata);
        createControllerAndWaitConnection();
        mSessionCallback.reset(1);

        mController.setRating(mediaId, rating2);
        assertTrue(mSessionCallback.await(TIME_OUT_MS));
        assertTrue(mSessionCallback.mOnSetRatingCalled);
        assertEquals(rating2, MediaUtils2.convertToRating2(mSessionCallback.mRating));
    }

    private void setPlaybackState(int state) {
        final long allActions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_REWIND;
        PlaybackStateCompat playbackState = new PlaybackStateCompat.Builder().setActions(allActions)
                .setState(state, 0L, 0.0f).build();
        mSession.setPlaybackState(playbackState);
    }

    class TestVolumeProvider extends VolumeProviderCompat {
        final CountDownLatch mLatch = new CountDownLatch(1);
        boolean mSetVolumeToCalled;
        boolean mAdjustVolumeCalled;
        int mVolume;
        int mDirection;

        TestVolumeProvider(int controlType, int maxVolume, int currentVolume) {
            super(controlType, maxVolume, currentVolume);
        }

        @Override
        public void onSetVolumeTo(int volume) {
            mSetVolumeToCalled = true;
            mVolume = volume;
            mLatch.countDown();
        }

        @Override
        public void onAdjustVolume(int direction) {
            mAdjustVolumeCalled = true;
            mDirection = direction;
            mLatch.countDown();
        }
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        private CountDownLatch mLatch = new CountDownLatch(1);
        private long mSeekPosition;
        private long mQueueItemId;
        private RatingCompat mRating;
        private String mMediaId;
        private String mQuery;
        private Uri mUri;
        private String mAction;
        private String mCommand;
        private Bundle mExtras;
        private ResultReceiver mCommandCallback;
        private boolean mCaptioningEnabled;
        private int mRepeatMode;
        private int mShuffleMode;
        private int mQueueIndex;
        private MediaDescriptionCompat mQueueDescriptionForAdd;
        private MediaDescriptionCompat mQueueDescriptionForRemove;

        private int mOnPlayCalledCount;
        private boolean mOnPauseCalled;
        private boolean mOnStopCalled;
        private boolean mOnFastForwardCalled;
        private boolean mOnRewindCalled;
        private boolean mOnSkipToPreviousCalled;
        private boolean mOnSkipToNextCalled;
        private boolean mOnSeekToCalled;
        private boolean mOnSkipToQueueItemCalled;
        private boolean mOnSetRatingCalled;
        private boolean mOnPlayFromMediaIdCalled;
        private boolean mOnPlayFromSearchCalled;
        private boolean mOnPlayFromUriCalled;
        private boolean mOnCustomActionCalled;
        private boolean mOnCommandCalled;
        private boolean mOnPrepareCalled;
        private boolean mOnPrepareFromMediaIdCalled;
        private boolean mOnPrepareFromSearchCalled;
        private boolean mOnPrepareFromUriCalled;
        private boolean mOnSetCaptioningEnabledCalled;
        private boolean mOnSetRepeatModeCalled;
        private boolean mOnSetShuffleModeCalled;
        private boolean mOnAddQueueItemCalled;
        private boolean mOnAddQueueItemAtCalled;
        private boolean mOnRemoveQueueItemCalled;

        public void reset(int count) {
            mLatch = new CountDownLatch(count);
            mSeekPosition = -1;
            mQueueItemId = -1;
            mRating = null;
            mMediaId = null;
            mQuery = null;
            mUri = null;
            mAction = null;
            mExtras = null;
            mCommand = null;
            mCommandCallback = null;
            mCaptioningEnabled = false;
            mRepeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
            mShuffleMode = PlaybackStateCompat.SHUFFLE_MODE_NONE;
            mQueueIndex = -1;
            mQueueDescriptionForAdd = null;
            mQueueDescriptionForRemove = null;

            mOnPlayCalledCount = 0;
            mOnPauseCalled = false;
            mOnStopCalled = false;
            mOnFastForwardCalled = false;
            mOnRewindCalled = false;
            mOnSkipToPreviousCalled = false;
            mOnSkipToNextCalled = false;
            mOnSkipToQueueItemCalled = false;
            mOnSeekToCalled = false;
            mOnSetRatingCalled = false;
            mOnPlayFromMediaIdCalled = false;
            mOnPlayFromSearchCalled = false;
            mOnPlayFromUriCalled = false;
            mOnCustomActionCalled = false;
            mOnCommandCalled = false;
            mOnPrepareCalled = false;
            mOnPrepareFromMediaIdCalled = false;
            mOnPrepareFromSearchCalled = false;
            mOnPrepareFromUriCalled = false;
            mOnSetCaptioningEnabledCalled = false;
            mOnSetRepeatModeCalled = false;
            mOnSetShuffleModeCalled = false;
            mOnAddQueueItemCalled = false;
            mOnAddQueueItemAtCalled = false;
            mOnRemoveQueueItemCalled = false;
        }

        public boolean await(long timeoutMs) {
            try {
                return mLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }

        @Override
        public void onPlay() {
            mOnPlayCalledCount++;
            setPlaybackState(PlaybackStateCompat.STATE_PLAYING);
            mLatch.countDown();
        }

        @Override
        public void onPause() {
            mOnPauseCalled = true;
            setPlaybackState(PlaybackStateCompat.STATE_PAUSED);
            mLatch.countDown();
        }

        @Override
        public void onStop() {
            mOnStopCalled = true;
            setPlaybackState(PlaybackStateCompat.STATE_STOPPED);
            mLatch.countDown();
        }

        @Override
        public void onFastForward() {
            mOnFastForwardCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onRewind() {
            mOnRewindCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onSkipToPrevious() {
            mOnSkipToPreviousCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onSkipToNext() {
            mOnSkipToNextCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onSeekTo(long pos) {
            mOnSeekToCalled = true;
            mSeekPosition = pos;
            mLatch.countDown();
        }

        @Override
        public void onSetRating(RatingCompat rating) {
            mOnSetRatingCalled = true;
            mRating = rating;
            mLatch.countDown();
        }

        @Override
        public void onPlayFromMediaId(String mediaId, Bundle extras) {
            mOnPlayFromMediaIdCalled = true;
            mMediaId = mediaId;
            mExtras = extras;
            mLatch.countDown();
        }

        @Override
        public void onPlayFromSearch(String query, Bundle extras) {
            mOnPlayFromSearchCalled = true;
            mQuery = query;
            mExtras = extras;
            mLatch.countDown();
        }

        @Override
        public void onPlayFromUri(Uri uri, Bundle extras) {
            mOnPlayFromUriCalled = true;
            mUri = uri;
            mExtras = extras;
            mLatch.countDown();
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            mOnCustomActionCalled = true;
            mAction = action;
            mExtras = extras;
            mLatch.countDown();
        }

        @Override
        public void onSkipToQueueItem(long id) {
            mOnSkipToQueueItemCalled = true;
            mQueueItemId = id;
            mLatch.countDown();
        }

        @Override
        public void onCommand(String command, Bundle extras, ResultReceiver cb) {
            mOnCommandCalled = true;
            mCommand = command;
            mExtras = extras;
            mCommandCallback = cb;
            mLatch.countDown();
        }

        @Override
        public void onPrepare() {
            mOnPrepareCalled = true;
            mLatch.countDown();
        }

        @Override
        public void onPrepareFromMediaId(String mediaId, Bundle extras) {
            mOnPrepareFromMediaIdCalled = true;
            mMediaId = mediaId;
            mExtras = extras;
            mLatch.countDown();
        }

        @Override
        public void onPrepareFromSearch(String query, Bundle extras) {
            mOnPrepareFromSearchCalled = true;
            mQuery = query;
            mExtras = extras;
            mLatch.countDown();
        }

        @Override
        public void onPrepareFromUri(Uri uri, Bundle extras) {
            mOnPrepareFromUriCalled = true;
            mUri = uri;
            mExtras = extras;
            mLatch.countDown();
        }

        @Override
        public void onSetRepeatMode(int repeatMode) {
            mOnSetRepeatModeCalled = true;
            mRepeatMode = repeatMode;
            mSession.setRepeatMode(repeatMode);
            mLatch.countDown();
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            mOnAddQueueItemCalled = true;
            mQueueDescriptionForAdd = description;
            mLatch.countDown();
        }

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description, int index) {
            mOnAddQueueItemAtCalled = true;
            mQueueIndex = index;
            mQueueDescriptionForAdd = description;
            mLatch.countDown();
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            mOnRemoveQueueItemCalled = true;
            mQueueDescriptionForRemove = description;
            mLatch.countDown();
        }

        @Override
        public void onSetCaptioningEnabled(boolean enabled) {
            mOnSetCaptioningEnabledCalled = true;
            mCaptioningEnabled = enabled;
            mLatch.countDown();
        }

        @Override
        public void onSetShuffleMode(int shuffleMode) {
            mOnSetShuffleModeCalled = true;
            mShuffleMode = shuffleMode;
            mSession.setShuffleMode(shuffleMode);
            mLatch.countDown();
        }
    }
}

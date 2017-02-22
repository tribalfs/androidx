/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.support.v4.media.session;

import static android.support.test.InstrumentationRegistry.getContext;
import static android.support.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.support.v4.media.PollingCheck;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.VolumeProviderCompat;
import android.view.KeyEvent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Test {@link MediaSessionCompat}.
 */
@RunWith(AndroidJUnit4.class)
public class MediaSessionCompatTest {
    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 3000L;
    private static final int MAX_AUDIO_INFO_CHANGED_CALLBACK_COUNT = 10;
    private static final String TEST_SESSION_TAG = "test-session-tag";
    private static final String TEST_KEY = "test-key";
    private static final String TEST_VALUE = "test-val";
    private static final Bundle TEST_BUNDLE = createTestBundle();
    private static final String TEST_SESSION_EVENT = "test-session-event";
    private static final int TEST_CURRENT_VOLUME = 10;
    private static final int TEST_MAX_VOLUME = 11;
    private static final long TEST_QUEUE_ID = 12L;
    private static final long TEST_ACTION = 55L;
    private static final int TEST_ERROR_CODE =
            PlaybackStateCompat.ERROR_CODE_AUTHENTICATION_EXPIRED;
    private static final String TEST_ERROR_MSG = "test-error-msg";

    private static Bundle createTestBundle() {
        Bundle bundle = new Bundle();
        bundle.putString(TEST_KEY, TEST_VALUE);
        return bundle;
    }

    private AudioManager mAudioManager;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Object mWaitLock = new Object();
    private MediaControllerCallback mCallback = new MediaControllerCallback();
    private MediaSessionCompat mSession;

    @Before
    public void setUp() throws Exception {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
                mSession = new MediaSessionCompat(getContext(), TEST_SESSION_TAG);
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        // It is OK to call release() twice.
        mSession.release();
        mSession = null;
    }

    /**
     * Tests that a session can be created and that all the fields are
     * initialized correctly.
     */
    @Test
    @SmallTest
    public void testCreateSession() throws Exception {
        assertNotNull(mSession.getSessionToken());
        assertFalse("New session should not be active", mSession.isActive());

        // Verify by getting the controller and checking all its fields
        MediaControllerCompat controller = mSession.getController();
        assertNotNull(controller);
        verifyNewSession(controller, TEST_SESSION_TAG);
    }

    /**
     * Tests MediaSessionCompat.Token created in the constructor of MediaSessionCompat.
     */
    @Test
    @SmallTest
    public void testSessionToken() throws Exception {
        MediaSessionCompat.Token sessionToken = mSession.getSessionToken();

        assertNotNull(sessionToken);
        assertEquals(0, sessionToken.describeContents());

        // Test writeToParcel
        Parcel p = Parcel.obtain();
        sessionToken.writeToParcel(p, 0);
        p.setDataPosition(0);
        MediaSessionCompat.Token token = MediaSessionCompat.Token.CREATOR.createFromParcel(p);
        assertEquals(token, sessionToken);
        p.recycle();
    }

    /**
     * Tests {@link MediaSessionCompat#setExtras}.
     */
    @Test
    @SmallTest
    public void testSetExtras() throws Exception {
        final Bundle extras = new Bundle();
        MediaControllerCompat controller = mSession.getController();
        controller.registerCallback(mCallback, mHandler);
        synchronized (mWaitLock) {
            mCallback.resetLocked();
            mSession.setExtras(TEST_BUNDLE);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnExtraChangedCalled);

            Bundle extrasOut = mCallback.mExtras;
            assertNotNull(extrasOut);
            assertEquals(TEST_VALUE, extrasOut.get(TEST_KEY));

            extrasOut = controller.getExtras();
            assertNotNull(extrasOut);
            assertEquals(TEST_VALUE, extrasOut.get(TEST_KEY));
        }
    }

    /**
     * Tests {@link MediaSessionCompat#setFlags}.
     */
    @Test
    @SmallTest
    public void testSetFlags() throws Exception {
        MediaControllerCompat controller = mSession.getController();
        controller.registerCallback(mCallback, mHandler);
        synchronized (mWaitLock) {
            mCallback.resetLocked();
            mSession.setFlags(5);
            assertEquals(5, controller.getFlags());
        }
    }

    /**
     * Tests {@link MediaSessionCompat#setMetadata}.
     */
    @Test
    @SmallTest
    public void testSetMetadata() throws Exception {
        MediaControllerCompat controller = mSession.getController();
        controller.registerCallback(mCallback, mHandler);
        synchronized (mWaitLock) {
            mCallback.resetLocked();
            MediaMetadataCompat metadata =
                    new MediaMetadataCompat.Builder().putString(TEST_KEY, TEST_VALUE).build();
            mSession.setMetadata(metadata);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnMetadataChangedCalled);

            MediaMetadataCompat metadataOut = mCallback.mMediaMetadata;
            assertNotNull(metadataOut);
            assertEquals(TEST_VALUE, metadataOut.getString(TEST_KEY));

            metadataOut = controller.getMetadata();
            assertNotNull(metadataOut);
            assertEquals(TEST_VALUE, metadataOut.getString(TEST_KEY));
        }
    }

    /**
     * Tests {@link MediaSessionCompat#setPlaybackState}.
     */
    @Test
    @SmallTest
    public void testSetPlaybackState() throws Exception {
        MediaControllerCompat controller = mSession.getController();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            waitUntilExtraBinderReady(controller);
        }
        controller.registerCallback(mCallback, mHandler);
        synchronized (mWaitLock) {
            mCallback.resetLocked();
            PlaybackStateCompat state =
                    new PlaybackStateCompat.Builder()
                            .setActions(TEST_ACTION)
                            .setErrorMessage(TEST_ERROR_CODE, TEST_ERROR_MSG)
                            .build();
            mSession.setPlaybackState(state);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnPlaybackStateChangedCalled);

            PlaybackStateCompat stateOut = mCallback.mPlaybackState;
            assertNotNull(stateOut);
            assertEquals(TEST_ACTION, stateOut.getActions());
            assertEquals(TEST_ERROR_CODE, stateOut.getErrorCode());
            assertEquals(TEST_ERROR_MSG, stateOut.getErrorMessage().toString());

            stateOut = controller.getPlaybackState();
            assertNotNull(stateOut);
            assertEquals(TEST_ACTION, stateOut.getActions());
            assertEquals(TEST_ERROR_CODE, stateOut.getErrorCode());
            assertEquals(TEST_ERROR_MSG, stateOut.getErrorMessage().toString());
        }
    }

    /**
     * Tests {@link MediaSessionCompat#setQueue} and {@link MediaSessionCompat#setQueueTitle}.
     */
    @Test
    @SmallTest
    public void testSetQueueAndSetQueueTitle() throws Exception {
        MediaControllerCompat controller = mSession.getController();
        controller.registerCallback(mCallback, mHandler);
        synchronized (mWaitLock) {
            mCallback.resetLocked();
            List<MediaSessionCompat.QueueItem> queue = new ArrayList<>();
            MediaSessionCompat.QueueItem item = new MediaSessionCompat.QueueItem(
                    new MediaDescriptionCompat.Builder()
                            .setMediaId(TEST_VALUE)
                            .setTitle("title")
                            .build(),
                    TEST_QUEUE_ID);
            queue.add(item);
            mSession.setQueue(queue);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnQueueChangedCalled);

            mSession.setQueueTitle(TEST_VALUE);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnQueueTitleChangedCalled);

            assertEquals(TEST_VALUE, mCallback.mTitle);
            assertEquals(queue.size(), mCallback.mQueue.size());
            assertEquals(TEST_QUEUE_ID, mCallback.mQueue.get(0).getQueueId());
            assertEquals(TEST_VALUE, mCallback.mQueue.get(0).getDescription().getMediaId());

            assertEquals(TEST_VALUE, controller.getQueueTitle());
            assertEquals(queue.size(), controller.getQueue().size());
            assertEquals(TEST_QUEUE_ID, controller.getQueue().get(0).getQueueId());
            assertEquals(TEST_VALUE, controller.getQueue().get(0).getDescription().getMediaId());

            mCallback.resetLocked();
            mSession.setQueue(null);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnQueueChangedCalled);

            mSession.setQueueTitle(null);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnQueueTitleChangedCalled);

            assertNull(mCallback.mTitle);
            assertNull(mCallback.mQueue);
            assertNull(controller.getQueueTitle());
            assertNull(controller.getQueue());
        }
    }

    /**
     * Tests {@link MediaSessionCompat#setSessionActivity}.
     */
    @Test
    @SmallTest
    public void testSessionActivity() throws Exception {
        MediaControllerCompat controller = mSession.getController();
        synchronized (mWaitLock) {
            Intent intent = new Intent("cts.MEDIA_SESSION_ACTION");
            PendingIntent pi = PendingIntent.getActivity(getContext(), 555, intent, 0);
            mSession.setSessionActivity(pi);
            assertEquals(pi, controller.getSessionActivity());
        }
    }

    /**
     * Tests {@link MediaSessionCompat#setRepeatMode}.
     */
    @Test
    @SmallTest
    public void testSetRepeatMode() throws Exception {
        MediaControllerCompat controller = mSession.getController();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            waitUntilExtraBinderReady(controller);
        }
        controller.registerCallback(mCallback, mHandler);
        synchronized (mWaitLock) {
            mCallback.resetLocked();
            final int repeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;
            mSession.setRepeatMode(repeatMode);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnRepeatModeChangedCalled);
            assertEquals(repeatMode, mCallback.mRepeatMode);
            assertEquals(repeatMode, controller.getRepeatMode());
        }
    }

    /**
     * Tests {@link MediaSessionCompat#setShuffleModeEnabled}.
     */
    @Test
    @SmallTest
    public void testSetShuffleModeEnabled() throws Exception {
        final boolean shuffleModeEnabled = true;
        MediaControllerCompat controller = mSession.getController();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            waitUntilExtraBinderReady(controller);
        }
        controller.registerCallback(mCallback, mHandler);
        synchronized (mWaitLock) {
            mCallback.resetLocked();
            final int repeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;
            mSession.setShuffleModeEnabled(shuffleModeEnabled);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnShuffleModeChangedCalled);
            assertEquals(shuffleModeEnabled, mCallback.mShuffleModeEnabled);
            assertEquals(shuffleModeEnabled, controller.isShuffleModeEnabled());
        }
    }

    /**
     * Tests {@link MediaSessionCompat#sendSessionEvent}.
     */
    @Test
    @SmallTest
    public void testSendSessionEvent() throws Exception {
        MediaControllerCompat controller = mSession.getController();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP &&
                Build.VERSION.SDK_INT <= Build.VERSION_CODES.LOLLIPOP_MR1) {
            waitUntilExtraBinderReady(controller);
        }
        controller.registerCallback(mCallback, mHandler);
        synchronized (mWaitLock) {
            mCallback.resetLocked();
            mSession.sendSessionEvent(TEST_SESSION_EVENT, TEST_BUNDLE);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnSessionEventCalled);
            assertEquals(TEST_SESSION_EVENT, mCallback.mEvent);
            assertEquals(TEST_VALUE, mCallback.mExtras.getString(TEST_KEY));
        }
    }

    /**
     * Tests {@link MediaSessionCompat#setActive} and {@link MediaSessionCompat#release}.
     */
    @Test
    @SmallTest
    public void testSetActiveAndRelease() throws Exception {
        MediaControllerCompat controller = mSession.getController();
        controller.registerCallback(mCallback, mHandler);
        synchronized (mWaitLock) {
            mSession.setActive(true);
            assertTrue(mSession.isActive());

            mCallback.resetLocked();
            mSession.release();
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(mCallback.mOnSessionDestroyedCalled);
        }
    }

    /**
     * Tests {@link MediaSessionCompat#setPlaybackToLocal} and
     * {@link MediaSessionCompat#setPlaybackToRemote}.
     */
    @Test
    @SmallTest
    public void testPlaybackToLocalAndRemote() throws Exception {
        MediaControllerCompat controller = mSession.getController();
        controller.registerCallback(mCallback, mHandler);
        synchronized (mWaitLock) {
            // test setPlaybackToRemote, do this before testing setPlaybackToLocal
            // to ensure it switches correctly.
            mCallback.resetLocked();
            try {
                mSession.setPlaybackToRemote(null);
                fail("Expected IAE for setPlaybackToRemote(null)");
            } catch (IllegalArgumentException e) {
                // expected
            }
            VolumeProviderCompat vp = new VolumeProviderCompat(
                    VolumeProviderCompat.VOLUME_CONTROL_FIXED,
                    TEST_MAX_VOLUME,
                    TEST_CURRENT_VOLUME) {};
            mSession.setPlaybackToRemote(vp);

            MediaControllerCompat.PlaybackInfo info = null;
            for (int i = 0; i < MAX_AUDIO_INFO_CHANGED_CALLBACK_COUNT; ++i) {
                mCallback.mOnAudioInfoChangedCalled = false;
                mWaitLock.wait(TIME_OUT_MS);
                assertTrue(mCallback.mOnAudioInfoChangedCalled);
                info = mCallback.mPlaybackInfo;
                if (info != null && info.getCurrentVolume() == TEST_CURRENT_VOLUME
                        && info.getMaxVolume() == TEST_MAX_VOLUME
                        && info.getVolumeControl() == VolumeProviderCompat.VOLUME_CONTROL_FIXED
                        && info.getPlaybackType()
                        == MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE) {
                    break;
                }
            }
            assertNotNull(info);
            assertEquals(MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE,
                    info.getPlaybackType());
            assertEquals(TEST_MAX_VOLUME, info.getMaxVolume());
            assertEquals(TEST_CURRENT_VOLUME, info.getCurrentVolume());
            assertEquals(VolumeProviderCompat.VOLUME_CONTROL_FIXED,
                    info.getVolumeControl());

            info = controller.getPlaybackInfo();
            assertNotNull(info);
            assertEquals(MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_REMOTE,
                    info.getPlaybackType());
            assertEquals(TEST_MAX_VOLUME, info.getMaxVolume());
            assertEquals(TEST_CURRENT_VOLUME, info.getCurrentVolume());
            assertEquals(VolumeProviderCompat.VOLUME_CONTROL_FIXED, info.getVolumeControl());

            // test setPlaybackToLocal
            mSession.setPlaybackToLocal(AudioManager.STREAM_RING);
            info = controller.getPlaybackInfo();
            assertNotNull(info);
            assertEquals(MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL,
                    info.getPlaybackType());
        }
    }

    /**
     * Tests {@link MediaSessionCompat.Callback#onMediaButtonEvent}.
     */
    @Test
    @SmallTest
    public void testCallbackOnMediaButtonEvent() throws Exception {
        MediaSessionCallback sessionCallback = new MediaSessionCallback();
        mSession.setCallback(sessionCallback, new Handler(Looper.getMainLooper()));
        mSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS);
        mSession.setActive(true);

        Intent mediaButtonIntent = new Intent(Intent.ACTION_MEDIA_BUTTON).setComponent(
                new ComponentName(getContext(), getContext().getClass()));
        PendingIntent pi = PendingIntent.getBroadcast(getContext(), 0, mediaButtonIntent, 0);
        mSession.setMediaButtonReceiver(pi);

        long supportedActions = PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_STOP
                | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                | PlaybackStateCompat.ACTION_FAST_FORWARD | PlaybackStateCompat.ACTION_REWIND;

        // Set state to STATE_PLAYING to get higher priority.
        PlaybackStateCompat defaultState = new PlaybackStateCompat.Builder()
                .setActions(supportedActions)
                .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 0.0f)
                .build();
        mSession.setPlaybackState(defaultState);

        synchronized (mWaitLock) {
            sessionCallback.reset();
            sendMediaKeyInputToController(KeyEvent.KEYCODE_MEDIA_PLAY);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(sessionCallback.mOnPlayCalled);

            sessionCallback.reset();
            sendMediaKeyInputToController(KeyEvent.KEYCODE_MEDIA_PAUSE);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(sessionCallback.mOnPauseCalled);

            sessionCallback.reset();
            sendMediaKeyInputToController(KeyEvent.KEYCODE_MEDIA_NEXT);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(sessionCallback.mOnSkipToNextCalled);

            sessionCallback.reset();
            sendMediaKeyInputToController(KeyEvent.KEYCODE_MEDIA_PREVIOUS);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(sessionCallback.mOnSkipToPreviousCalled);

            sessionCallback.reset();
            sendMediaKeyInputToController(KeyEvent.KEYCODE_MEDIA_STOP);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(sessionCallback.mOnStopCalled);

            sessionCallback.reset();
            sendMediaKeyInputToController(KeyEvent.KEYCODE_MEDIA_FAST_FORWARD);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(sessionCallback.mOnFastForwardCalled);

            sessionCallback.reset();
            sendMediaKeyInputToController(KeyEvent.KEYCODE_MEDIA_REWIND);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(sessionCallback.mOnRewindCalled);

            // Test PLAY_PAUSE button twice.
            // First, send PLAY_PAUSE button event while in STATE_PAUSED.
            sessionCallback.reset();
            mSession.setPlaybackState(new PlaybackStateCompat.Builder().setActions(supportedActions)
                    .setState(PlaybackStateCompat.STATE_PAUSED, 0L, 0.0f).build());
            sendMediaKeyInputToController(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(sessionCallback.mOnPlayCalled);

            // Next, send PLAY_PAUSE button event while in STATE_PLAYING.
            sessionCallback.reset();
            mSession.setPlaybackState(new PlaybackStateCompat.Builder().setActions(supportedActions)
                    .setState(PlaybackStateCompat.STATE_PLAYING, 0L, 0.0f).build());
            sendMediaKeyInputToController(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE);
            mWaitLock.wait(TIME_OUT_MS);
            assertTrue(sessionCallback.mOnPauseCalled);
        }
    }

    @Test
    @SmallTest
    public void testSetNullCallback() throws Throwable {
        getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                try {
                    MediaSessionCompat session = new MediaSessionCompat(getContext(), "TEST");
                    session.setCallback(null);
                } catch (Exception e) {
                    fail("Fail with an exception: " + e);
                }
            }
        });
    }

    /**
     * Tests {@link MediaSessionCompat.QueueItem}.
     */
    @Test
    @SmallTest
    public void testQueueItem() {
        MediaSessionCompat.QueueItem item = new MediaSessionCompat.QueueItem(
                new MediaDescriptionCompat.Builder()
                        .setMediaId("media-id")
                        .setTitle("title")
                        .build(),
                TEST_QUEUE_ID);
        assertEquals(TEST_QUEUE_ID, item.getQueueId());
        assertEquals("media-id", item.getDescription().getMediaId());
        assertEquals("title", item.getDescription().getTitle());
        assertEquals(0, item.describeContents());

        Parcel p = Parcel.obtain();
        item.writeToParcel(p, 0);
        p.setDataPosition(0);
        MediaSessionCompat.QueueItem other =
                MediaSessionCompat.QueueItem.CREATOR.createFromParcel(p);
        assertEquals(item.toString(), other.toString());
        p.recycle();
    }

    /**
     * Verifies that a new session hasn't had any configuration bits set yet.
     *
     * @param controller The controller for the session
     */
    private void verifyNewSession(MediaControllerCompat controller, String tag) {
        assertEquals("New session has unexpected configuration", 0L, controller.getFlags());
        assertNull("New session has unexpected configuration", controller.getExtras());
        assertNull("New session has unexpected configuration", controller.getMetadata());
        assertEquals("New session has unexpected configuration",
                getContext().getPackageName(), controller.getPackageName());
        assertNull("New session has unexpected configuration", controller.getPlaybackState());
        assertNull("New session has unexpected configuration", controller.getQueue());
        assertNull("New session has unexpected configuration", controller.getQueueTitle());
        assertEquals("New session has unexpected configuration", RatingCompat.RATING_NONE,
                controller.getRatingType());
        assertNull("New session has unexpected configuration", controller.getSessionActivity());

        assertNotNull(controller.getSessionToken());
        assertNotNull(controller.getTransportControls());

        MediaControllerCompat.PlaybackInfo info = controller.getPlaybackInfo();
        assertNotNull(info);
        assertEquals(MediaControllerCompat.PlaybackInfo.PLAYBACK_TYPE_LOCAL,
                info.getPlaybackType());
        assertEquals(mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC),
                info.getCurrentVolume());
    }

    private void sendMediaKeyInputToController(int keyCode) {
        MediaControllerCompat controller = mSession.getController();
        controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keyCode));
        controller.dispatchMediaButtonEvent(new KeyEvent(KeyEvent.ACTION_UP, keyCode));
    }

    private void waitUntilExtraBinderReady(final MediaControllerCompat controller) {
        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                return controller.isExtraBinderReady();
            }
        }.run();
    }

    private class MediaControllerCallback extends MediaControllerCompat.Callback {
        private volatile boolean mOnPlaybackStateChangedCalled;
        private volatile boolean mOnMetadataChangedCalled;
        private volatile boolean mOnQueueChangedCalled;
        private volatile boolean mOnQueueTitleChangedCalled;
        private volatile boolean mOnExtraChangedCalled;
        private volatile boolean mOnAudioInfoChangedCalled;
        private volatile boolean mOnSessionDestroyedCalled;
        private volatile boolean mOnSessionEventCalled;
        private volatile boolean mOnRepeatModeChangedCalled;
        private volatile boolean mOnShuffleModeChangedCalled;

        private volatile PlaybackStateCompat mPlaybackState;
        private volatile MediaMetadataCompat mMediaMetadata;
        private volatile List<MediaSessionCompat.QueueItem> mQueue;
        private volatile CharSequence mTitle;
        private volatile String mEvent;
        private volatile Bundle mExtras;
        private volatile MediaControllerCompat.PlaybackInfo mPlaybackInfo;
        private volatile int mRepeatMode;
        private volatile boolean mShuffleModeEnabled;

        public void resetLocked() {
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
            mRepeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
            mShuffleModeEnabled = false;
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat state) {
            synchronized (mWaitLock) {
                mOnPlaybackStateChangedCalled = true;
                mPlaybackState = state;
                mWaitLock.notify();
            }
        }

        @Override
        public void onMetadataChanged(MediaMetadataCompat metadata) {
            synchronized (mWaitLock) {
                mOnMetadataChangedCalled = true;
                mMediaMetadata = metadata;
                mWaitLock.notify();
            }
        }

        @Override
        public void onQueueChanged(List<MediaSessionCompat.QueueItem> queue) {
            synchronized (mWaitLock) {
                mOnQueueChangedCalled = true;
                mQueue = queue;
                mWaitLock.notify();
            }
        }

        @Override
        public void onQueueTitleChanged(CharSequence title) {
            synchronized (mWaitLock) {
                mOnQueueTitleChangedCalled = true;
                mTitle = title;
                mWaitLock.notify();
            }
        }

        @Override
        public void onExtrasChanged(Bundle extras) {
            synchronized (mWaitLock) {
                mOnExtraChangedCalled = true;
                mExtras = extras;
                mWaitLock.notify();
            }
        }

        @Override
        public void onAudioInfoChanged(MediaControllerCompat.PlaybackInfo info) {
            synchronized (mWaitLock) {
                mOnAudioInfoChangedCalled = true;
                mPlaybackInfo = info;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSessionDestroyed() {
            synchronized (mWaitLock) {
                mOnSessionDestroyedCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSessionEvent(String event, Bundle extras) {
            synchronized (mWaitLock) {
                mOnSessionEventCalled = true;
                mEvent = event;
                mExtras = (Bundle) extras.clone();
                mWaitLock.notify();
            }
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {
            synchronized (mWaitLock) {
                mOnRepeatModeChangedCalled = true;
                mRepeatMode = repeatMode;
                mWaitLock.notify();
            }
        }

        @Override
        public void onShuffleModeChanged(boolean enabled) {
            synchronized (mWaitLock) {
                mOnShuffleModeChangedCalled = true;
                mShuffleModeEnabled = enabled;
                mWaitLock.notify();
            }
        }
    }

    private class MediaSessionCallback extends MediaSessionCompat.Callback {
        private boolean mOnPlayCalled;
        private boolean mOnPauseCalled;
        private boolean mOnStopCalled;
        private boolean mOnFastForwardCalled;
        private boolean mOnRewindCalled;
        private boolean mOnSkipToPreviousCalled;
        private boolean mOnSkipToNextCalled;

        public void reset() {
            mOnPlayCalled = false;
            mOnPauseCalled = false;
            mOnStopCalled = false;
            mOnFastForwardCalled = false;
            mOnRewindCalled = false;
            mOnSkipToPreviousCalled = false;
            mOnSkipToNextCalled = false;
        }

        @Override
        public void onPlay() {
            synchronized (mWaitLock) {
                mOnPlayCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onPause() {
            synchronized (mWaitLock) {
                mOnPauseCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onStop() {
            synchronized (mWaitLock) {
                mOnStopCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onFastForward() {
            synchronized (mWaitLock) {
                mOnFastForwardCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onRewind() {
            synchronized (mWaitLock) {
                mOnRewindCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSkipToPrevious() {
            synchronized (mWaitLock) {
                mOnSkipToPreviousCalled = true;
                mWaitLock.notify();
            }
        }

        @Override
        public void onSkipToNext() {
            synchronized (mWaitLock) {
                mOnSkipToNextCalled = true;
                mWaitLock.notify();
            }
        }
    }
}

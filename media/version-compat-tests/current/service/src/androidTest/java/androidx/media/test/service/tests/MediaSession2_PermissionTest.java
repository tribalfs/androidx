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

import static android.support.mediacompat.testlib.util.IntentUtil.CLIENT_PACKAGE_NAME;

import static androidx.media2.MediaSession2.SessionResult.RESULT_CODE_SUCCESS;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_PAUSE;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_PLAY;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_SEEK_TO;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_SET_MEDIA_ITEM;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_SET_PLAYLIST;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM;
import static androidx.media2.SessionCommand2.COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA;
import static androidx.media2.SessionCommand2.COMMAND_CODE_SESSION_FAST_FORWARD;
import static androidx.media2.SessionCommand2.COMMAND_CODE_SESSION_PLAY_FROM_MEDIA_ID;
import static androidx.media2.SessionCommand2.COMMAND_CODE_SESSION_PLAY_FROM_SEARCH;
import static androidx.media2.SessionCommand2.COMMAND_CODE_SESSION_PLAY_FROM_URI;
import static androidx.media2.SessionCommand2.COMMAND_CODE_SESSION_PREFETCH_FROM_MEDIA_ID;
import static androidx.media2.SessionCommand2.COMMAND_CODE_SESSION_PREFETCH_FROM_SEARCH;
import static androidx.media2.SessionCommand2.COMMAND_CODE_SESSION_PREFETCH_FROM_URI;
import static androidx.media2.SessionCommand2.COMMAND_CODE_SESSION_REWIND;
import static androidx.media2.SessionCommand2.COMMAND_CODE_SESSION_SET_RATING;
import static androidx.media2.SessionCommand2.COMMAND_CODE_VOLUME_ADJUST_VOLUME;
import static androidx.media2.SessionCommand2.COMMAND_CODE_VOLUME_SET_VOLUME;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.media.test.service.MediaTestUtils;
import androidx.media.test.service.MockPlayer;
import androidx.media.test.service.RemoteMediaController2;
import androidx.media2.MediaItem2;
import androidx.media2.MediaSession2;
import androidx.media2.MediaSession2.ControllerInfo;
import androidx.media2.Rating2;
import androidx.media2.SessionCommand2;
import androidx.media2.SessionCommandGroup2;
import androidx.media2.StarRating2;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests whether {@link MediaSession2} receives commands that hasn't allowed.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.JELLY_BEAN)
@RunWith(AndroidJUnit4.class)
@MediumTest
public class MediaSession2_PermissionTest extends MediaSession2TestBase {
    private static final String SESSION_ID = "MediaSession2Test_permission";

    private MockPlayer mPlayer;
    private MediaSession2 mSession;
    private MySessionCallback mCallback;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
    }

    @After
    @Override
    public void cleanUp() throws Exception {
        super.cleanUp();
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
        mPlayer = null;
        mCallback = null;
    }

    private MediaSession2 createSessionWithAllowedActions(final SessionCommandGroup2 commands) {
        mPlayer = new MockPlayer(1);
        mCallback = new MySessionCallback() {
            @Override
            public SessionCommandGroup2 onConnect(MediaSession2 session,
                    ControllerInfo controller) {
                if (!TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName())) {
                    return null;
                }
                return commands == null ? new SessionCommandGroup2() : commands;
            }
        };
        if (mSession != null) {
            mSession.close();
        }
        mSession = new MediaSession2.Builder(mContext, mPlayer).setId(SESSION_ID)
                .setSessionCallback(sHandlerExecutor, mCallback).build();
        return mSession;
    }

    private SessionCommandGroup2 createCommandGroupWith(int commandCode) {
        SessionCommandGroup2 commands = new SessionCommandGroup2.Builder()
                .addCommand(new SessionCommand2(commandCode))
                .build();
        return commands;
    }

    private SessionCommandGroup2 createCommandGroupWithout(int commandCode) {
        SessionCommandGroup2 commands = new SessionCommandGroup2.Builder()
                .addAllPredefinedCommands(SessionCommand2.COMMAND_VERSION_1)
                .removeCommand(new SessionCommand2(commandCode))
                .build();
        return commands;
    }

    private void testOnCommandRequest(int commandCode, PermissionTestRunnable runnable)
            throws InterruptedException {
        createSessionWithAllowedActions(createCommandGroupWith(commandCode));
        runnable.run(createRemoteController2(mSession.getToken()));

        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnCommandRequestCalled);
        assertEquals(commandCode, mCallback.mCommand.getCommandCode());

        createSessionWithAllowedActions(createCommandGroupWithout(commandCode));
        runnable.run(createRemoteController2(mSession.getToken()));

        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mCallback.mOnCommandRequestCalled);
    }

    @Test
    public void testPlay() throws InterruptedException {
        prepareLooper();
        testOnCommandRequest(COMMAND_CODE_PLAYER_PLAY, new PermissionTestRunnable() {
            @Override
            public void run(RemoteMediaController2 controller) {
                controller.play();
            }
        });
    }

    @Test
    public void testPause() throws InterruptedException {
        prepareLooper();
        testOnCommandRequest(COMMAND_CODE_PLAYER_PAUSE, new PermissionTestRunnable() {
            @Override
            public void run(RemoteMediaController2 controller) {
                controller.pause();
            }
        });
    }

    @Test
    public void testSeekTo() throws InterruptedException {
        prepareLooper();
        final long position = 10;
        testOnCommandRequest(COMMAND_CODE_PLAYER_SEEK_TO, new PermissionTestRunnable() {
            @Override
            public void run(RemoteMediaController2 controller) {
                controller.seekTo(position);
            }
        });
    }

    @Test
    public void testSkipToNext() throws InterruptedException {
        prepareLooper();
        testOnCommandRequest(COMMAND_CODE_PLAYER_SKIP_TO_NEXT_PLAYLIST_ITEM,
                new PermissionTestRunnable() {
                    @Override
                    public void run(RemoteMediaController2 controller) {
                        controller.skipToNextItem();
                    }
                });
    }

    @Test
    public void testSkipToPrevious() throws InterruptedException {
        prepareLooper();
        testOnCommandRequest(COMMAND_CODE_PLAYER_SKIP_TO_PREVIOUS_PLAYLIST_ITEM,
                new PermissionTestRunnable() {
                    @Override
                    public void run(RemoteMediaController2 controller) {
                        controller.skipToPreviousItem();
                    }
                });
    }

    @Test
    public void testSkipToPlaylistItem() throws InterruptedException {
        prepareLooper();
        final MediaItem2 testItem = MediaTestUtils.createMediaItemWithMetadata();
        testOnCommandRequest(
                COMMAND_CODE_PLAYER_SKIP_TO_PLAYLIST_ITEM,
                new PermissionTestRunnable() {
                    @Override
                    public void run(RemoteMediaController2 controller) {
                        controller.skipToPlaylistItem(testItem);
                    }
                });
    }

    @Test
    public void testSetPlaylist() throws InterruptedException {
        prepareLooper();
        final List<MediaItem2> list = MediaTestUtils.createPlaylist(2);
        testOnCommandRequest(COMMAND_CODE_PLAYER_SET_PLAYLIST, new PermissionTestRunnable() {
            @Override
            public void run(RemoteMediaController2 controller) {
                controller.setPlaylist(list, null);
            }
        });
    }

    @Test
    public void testSetMediaItem() throws InterruptedException {
        prepareLooper();
        final MediaItem2 item = MediaTestUtils.createMediaItemWithMetadata();
        testOnCommandRequest(COMMAND_CODE_PLAYER_SET_MEDIA_ITEM, new PermissionTestRunnable() {
            @Override
            public void run(RemoteMediaController2 controller) {
                controller.setMediaItem(item);
            }
        });
    }

    @Test
    public void testUpdatePlaylistMetadata() throws InterruptedException {
        prepareLooper();
        testOnCommandRequest(COMMAND_CODE_PLAYER_UPDATE_LIST_METADATA,
                new PermissionTestRunnable() {
                    @Override
                    public void run(RemoteMediaController2 controller) {
                        controller.updatePlaylistMetadata(null);
                    }
                });
    }

    @Test
    public void testAddPlaylistItem() throws InterruptedException {
        prepareLooper();
        final MediaItem2 testItem = MediaTestUtils.createMediaItemWithMetadata();
        testOnCommandRequest(COMMAND_CODE_PLAYER_ADD_PLAYLIST_ITEM, new PermissionTestRunnable() {
            @Override
            public void run(RemoteMediaController2 controller) {
                controller.addPlaylistItem(0, testItem);
            }
        });
    }

    @Test
    public void testRemovePlaylistItem() throws InterruptedException {
        prepareLooper();
        final MediaItem2 testItem = MediaTestUtils.createMediaItemWithMetadata();
        testOnCommandRequest(COMMAND_CODE_PLAYER_REMOVE_PLAYLIST_ITEM,
                new PermissionTestRunnable() {
                    @Override
                    public void run(RemoteMediaController2 controller) {
                        controller.removePlaylistItem(testItem);
                    }
                });
    }

    @Test
    public void testReplacePlaylistItem() throws InterruptedException {
        prepareLooper();
        final MediaItem2 testItem = MediaTestUtils.createMediaItemWithMetadata();
        testOnCommandRequest(COMMAND_CODE_PLAYER_REPLACE_PLAYLIST_ITEM,
                new PermissionTestRunnable() {
                    @Override
                    public void run(RemoteMediaController2 controller) {
                        controller.replacePlaylistItem(0, testItem);
                    }
                });
    }

    @Test
    public void testSetVolume() throws InterruptedException {
        prepareLooper();
        testOnCommandRequest(COMMAND_CODE_VOLUME_SET_VOLUME, new PermissionTestRunnable() {
            @Override
            public void run(RemoteMediaController2 controller) {
                controller.setVolumeTo(0, 0);
            }
        });
    }

    @Test
    public void testAdjustVolume() throws InterruptedException {
        prepareLooper();
        testOnCommandRequest(COMMAND_CODE_VOLUME_ADJUST_VOLUME, new PermissionTestRunnable() {
            @Override
            public void run(RemoteMediaController2 controller) {
                controller.adjustVolume(0, 0);
            }
        });
    }

    @Test
    public void testFastForward() throws InterruptedException {
        prepareLooper();
        createSessionWithAllowedActions(
                createCommandGroupWith(COMMAND_CODE_SESSION_FAST_FORWARD));
        createRemoteController2(mSession.getToken()).fastForward();

        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnFastForwardCalled);

        createSessionWithAllowedActions(
                createCommandGroupWithout(COMMAND_CODE_SESSION_FAST_FORWARD));
        createRemoteController2(mSession.getToken()).fastForward();
        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mCallback.mOnFastForwardCalled);
    }

    @Test
    public void testRewind() throws InterruptedException {
        prepareLooper();
        createSessionWithAllowedActions(
                createCommandGroupWith(COMMAND_CODE_SESSION_REWIND));
        createRemoteController2(mSession.getToken()).rewind();

        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnRewindCalled);

        createSessionWithAllowedActions(
                createCommandGroupWithout(COMMAND_CODE_SESSION_REWIND));
        createRemoteController2(mSession.getToken()).rewind();
        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mCallback.mOnRewindCalled);
    }

    @Test
    public void testPlayFromMediaId() throws InterruptedException {
        prepareLooper();
        final String mediaId = "testPlayFromMediaId";
        createSessionWithAllowedActions(
                createCommandGroupWith(COMMAND_CODE_SESSION_PLAY_FROM_MEDIA_ID));
        createRemoteController2(mSession.getToken()).playFromMediaId(mediaId, null);

        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnPlayFromMediaIdCalled);
        assertEquals(mediaId, mCallback.mMediaId);
        assertNull(mCallback.mExtras);

        createSessionWithAllowedActions(
                createCommandGroupWithout(COMMAND_CODE_SESSION_PLAY_FROM_MEDIA_ID));
        createRemoteController2(mSession.getToken()).playFromMediaId(mediaId, null);
        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mCallback.mOnPlayFromMediaIdCalled);
    }

    @Test
    public void testPlayFromUri() throws InterruptedException {
        prepareLooper();
        final Uri uri = Uri.parse("play://from.uri");
        createSessionWithAllowedActions(
                createCommandGroupWith(COMMAND_CODE_SESSION_PLAY_FROM_URI));
        createRemoteController2(mSession.getToken()).playFromUri(uri, null);

        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnPlayFromUriCalled);
        assertEquals(uri, mCallback.mUri);
        assertNull(mCallback.mExtras);

        createSessionWithAllowedActions(
                createCommandGroupWithout(COMMAND_CODE_SESSION_PLAY_FROM_URI));
        createRemoteController2(mSession.getToken()).playFromUri(uri, null);
        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mCallback.mOnPlayFromUriCalled);
    }

    @Test
    public void testPlayFromSearch() throws InterruptedException {
        prepareLooper();
        final String query = "testPlayFromSearch";
        createSessionWithAllowedActions(
                createCommandGroupWith(COMMAND_CODE_SESSION_PLAY_FROM_SEARCH));
        createRemoteController2(mSession.getToken()).playFromSearch(query, null);

        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnPlayFromSearchCalled);
        assertEquals(query, mCallback.mQuery);
        assertNull(mCallback.mExtras);

        createSessionWithAllowedActions(
                createCommandGroupWithout(COMMAND_CODE_SESSION_PLAY_FROM_SEARCH));
        createRemoteController2(mSession.getToken()).playFromSearch(query, null);
        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mCallback.mOnPlayFromSearchCalled);
    }

    @Test
    public void testPrepareFromMediaId() throws InterruptedException {
        prepareLooper();
        final String mediaId = "testPrepareFromMediaId";
        createSessionWithAllowedActions(
                createCommandGroupWith(COMMAND_CODE_SESSION_PREFETCH_FROM_MEDIA_ID));
        createRemoteController2(mSession.getToken()).prefetchFromMediaId(mediaId, null);

        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnPrepareFromMediaIdCalled);
        assertEquals(mediaId, mCallback.mMediaId);
        assertNull(mCallback.mExtras);

        createSessionWithAllowedActions(
                createCommandGroupWithout(COMMAND_CODE_SESSION_PREFETCH_FROM_MEDIA_ID));
        createRemoteController2(mSession.getToken()).prefetchFromMediaId(mediaId, null);
        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mCallback.mOnPrepareFromMediaIdCalled);
    }

    @Test
    public void testPrepareFromUri() throws InterruptedException {
        prepareLooper();
        final Uri uri = Uri.parse("prefetch://from.uri");
        createSessionWithAllowedActions(
                createCommandGroupWith(COMMAND_CODE_SESSION_PREFETCH_FROM_URI));
        createRemoteController2(mSession.getToken()).prefetchFromUri(uri, null);

        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnPrepareFromUriCalled);
        assertEquals(uri, mCallback.mUri);
        assertNull(mCallback.mExtras);

        createSessionWithAllowedActions(
                createCommandGroupWithout(COMMAND_CODE_SESSION_PREFETCH_FROM_URI));
        createRemoteController2(mSession.getToken()).prefetchFromUri(uri, null);
        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mCallback.mOnPrepareFromUriCalled);
    }

    @Test
    public void testPrepareFromSearch() throws InterruptedException {
        prepareLooper();
        final String query = "testPrepareFromSearch";
        createSessionWithAllowedActions(
                createCommandGroupWith(COMMAND_CODE_SESSION_PREFETCH_FROM_SEARCH));
        createRemoteController2(mSession.getToken()).prefetchFromSearch(query, null);

        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnPrepareFromSearchCalled);
        assertEquals(query, mCallback.mQuery);
        assertNull(mCallback.mExtras);

        createSessionWithAllowedActions(
                createCommandGroupWithout(COMMAND_CODE_SESSION_PREFETCH_FROM_SEARCH));
        createRemoteController2(mSession.getToken()).prefetchFromSearch(query, null);
        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mCallback.mOnPrepareFromSearchCalled);
    }

    @Test
    public void testSetRating() throws InterruptedException {
        prepareLooper();
        final String mediaId = "testSetRating";
        final Rating2 rating = new StarRating2(5, 3.5f);
        createSessionWithAllowedActions(
                createCommandGroupWith(COMMAND_CODE_SESSION_SET_RATING));
        createRemoteController2(mSession.getToken()).setRating(mediaId, rating);

        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnSetRatingCalled);
        assertEquals(mediaId, mCallback.mMediaId);
        assertEquals(rating, mCallback.mRating);

        createSessionWithAllowedActions(
                createCommandGroupWithout(COMMAND_CODE_SESSION_SET_RATING));
        createRemoteController2(mSession.getToken()).setRating(mediaId, rating);
        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mCallback.mOnSetRatingCalled);
    }

    @Test
    public void testChangingPermissionWithSetAllowedCommands() throws InterruptedException {
        prepareLooper();
        final String query = "testChangingPermissionWithSetAllowedCommands";
        createSessionWithAllowedActions(
                createCommandGroupWith(COMMAND_CODE_SESSION_PREFETCH_FROM_SEARCH));
        RemoteMediaController2 controller = createRemoteController2(mSession.getToken());

        controller.prefetchFromSearch(query, null);
        assertTrue(mCallback.mCountDownLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
        assertTrue(mCallback.mOnPrepareFromSearchCalled);
        assertEquals(query, mCallback.mQuery);
        assertNull(mCallback.mExtras);
        mCallback.reset();

        // Change allowed commands.
        mSession.setAllowedCommands(getTestControllerInfo(),
                createCommandGroupWithout(COMMAND_CODE_SESSION_PREFETCH_FROM_SEARCH));
        Thread.sleep(WAIT_TIME_MS);

        controller.prefetchFromSearch(query, null);
        assertFalse(mCallback.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
    }

    private ControllerInfo getTestControllerInfo() {
        List<ControllerInfo> controllers = mSession.getConnectedControllers();
        assertNotNull(controllers);
        for (int i = 0; i < controllers.size(); i++) {
            if (TextUtils.equals(CLIENT_PACKAGE_NAME, controllers.get(i).getPackageName())) {
                return controllers.get(i);
            }
        }
        fail("Failed to get test controller info");
        return null;
    }

    @FunctionalInterface
    private interface PermissionTestRunnable {
        void run(@NonNull RemoteMediaController2 controller);
    }

    public class MySessionCallback extends MediaSession2.SessionCallback {
        public CountDownLatch mCountDownLatch;

        public SessionCommand2 mCommand;
        public String mMediaId;
        public String mQuery;
        public Uri mUri;
        public Bundle mExtras;
        public Rating2 mRating;

        public boolean mOnCommandRequestCalled;
        public boolean mOnPlayFromMediaIdCalled;
        public boolean mOnPlayFromSearchCalled;
        public boolean mOnPlayFromUriCalled;
        public boolean mOnPrepareFromMediaIdCalled;
        public boolean mOnPrepareFromSearchCalled;
        public boolean mOnPrepareFromUriCalled;
        public boolean mOnFastForwardCalled;
        public boolean mOnRewindCalled;
        public boolean mOnSetRatingCalled;


        public MySessionCallback() {
            mCountDownLatch = new CountDownLatch(1);
        }

        public void reset() {
            mCountDownLatch = new CountDownLatch(1);

            mCommand = null;
            mMediaId = null;
            mQuery = null;
            mUri = null;
            mExtras = null;

            mOnCommandRequestCalled = false;
            mOnPlayFromMediaIdCalled = false;
            mOnPlayFromSearchCalled = false;
            mOnPlayFromUriCalled = false;
            mOnPrepareFromMediaIdCalled = false;
            mOnPrepareFromSearchCalled = false;
            mOnPrepareFromUriCalled = false;
            mOnFastForwardCalled = false;
            mOnRewindCalled = false;
            mOnSetRatingCalled = false;
        }

        @Override
        public int onCommandRequest(MediaSession2 session, ControllerInfo controller,
                SessionCommand2 command) {
            assertTrue(TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName()));
            mOnCommandRequestCalled = true;
            mCommand = command;
            mCountDownLatch.countDown();
            return super.onCommandRequest(session, controller, command);
        }

        @Override
        public int onFastForward(MediaSession2 session, ControllerInfo controller) {
            assertTrue(TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName()));
            mOnFastForwardCalled = true;
            mCountDownLatch.countDown();
            return RESULT_CODE_SUCCESS;
        }

        @Override
        public int onRewind(MediaSession2 session, ControllerInfo controller) {
            assertTrue(TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName()));
            mOnRewindCalled = true;
            mCountDownLatch.countDown();
            return RESULT_CODE_SUCCESS;
        }

        @Override
        public int onPlayFromMediaId(MediaSession2 session, ControllerInfo controller,
                String mediaId, Bundle extras) {
            assertTrue(TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName()));
            mOnPlayFromMediaIdCalled = true;
            mMediaId = mediaId;
            mExtras = extras;
            mCountDownLatch.countDown();
            return RESULT_CODE_SUCCESS;
        }

        @Override
        public int onPlayFromSearch(MediaSession2 session, ControllerInfo controller,
                String query, Bundle extras) {
            assertTrue(TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName()));
            mOnPlayFromSearchCalled = true;
            mQuery = query;
            mExtras = extras;
            mCountDownLatch.countDown();
            return RESULT_CODE_SUCCESS;
        }

        @Override
        public int onPlayFromUri(MediaSession2 session, ControllerInfo controller,
                Uri uri, Bundle extras) {
            assertTrue(TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName()));
            mOnPlayFromUriCalled = true;
            mUri = uri;
            mExtras = extras;
            mCountDownLatch.countDown();
            return RESULT_CODE_SUCCESS;
        }

        @Override
        public int onPrefetchFromMediaId(MediaSession2 session, ControllerInfo controller,
                String mediaId, Bundle extras) {
            assertTrue(TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName()));
            mOnPrepareFromMediaIdCalled = true;
            mMediaId = mediaId;
            mExtras = extras;
            mCountDownLatch.countDown();
            return RESULT_CODE_SUCCESS;
        }

        @Override
        public int onPrefetchFromSearch(MediaSession2 session, ControllerInfo controller,
                String query, Bundle extras) {
            assertTrue(TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName()));
            mOnPrepareFromSearchCalled = true;
            mQuery = query;
            mExtras = extras;
            mCountDownLatch.countDown();
            return RESULT_CODE_SUCCESS;
        }

        @Override
        public int onPrefetchFromUri(MediaSession2 session, ControllerInfo controller,
                Uri uri, Bundle extras) {
            assertTrue(TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName()));
            mOnPrepareFromUriCalled = true;
            mUri = uri;
            mExtras = extras;
            mCountDownLatch.countDown();
            return RESULT_CODE_SUCCESS;
        }

        @Override
        public int onSetRating(MediaSession2 session, ControllerInfo controller,
                String mediaId, Rating2 rating) {
            assertTrue(TextUtils.equals(CLIENT_PACKAGE_NAME, controller.getPackageName()));
            mOnSetRatingCalled = true;
            mMediaId = mediaId;
            mRating = rating;
            mCountDownLatch.countDown();
            return RESULT_CODE_SUCCESS;
        }
    }
}

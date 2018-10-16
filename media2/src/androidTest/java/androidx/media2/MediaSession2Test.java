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

package androidx.media2;

import static android.media.AudioAttributes.CONTENT_TYPE_MUSIC;

import static androidx.media.VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE;
import static androidx.media.VolumeProviderCompat.VOLUME_CONTROL_FIXED;
import static androidx.media2.MediaSession2.SessionResult.RESULT_CODE_INVALID_STATE;
import static androidx.media2.MediaSession2.SessionResult.RESULT_CODE_SUCCESS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.ResultReceiver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.AudioAttributesCompat;
import androidx.media2.MediaController2.ControllerCallback;
import androidx.media2.MediaController2.PlaybackInfo;
import androidx.media2.MediaSession2.CommandButton;
import androidx.media2.MediaSession2.ControllerInfo;
import androidx.media2.MediaSession2.SessionCallback;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests {@link MediaSession2}.
 */
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.JELLY_BEAN)
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaSession2Test extends MediaSession2TestBase {
    private static final String TAG = "MediaSession2Test";

    private MediaSession2 mSession;
    private MockPlayer mPlayer;
    private ControllerInfo mTestControllerInfo;

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        mPlayer = new MockPlayer(1);

        mSession = new MediaSession2.Builder(mContext, mPlayer)
                .setId(TAG)
                .setSessionCallback(sHandlerExecutor, new SessionCallback() {
                    @Override
                    public SessionCommandGroup2 onConnect(MediaSession2 session,
                            ControllerInfo controller) {
                        if (Process.myUid() == controller.getUid()) {
                            return super.onConnect(session, controller);
                        }
                        return null;
                    }
                }).build();
    }

    @After
    @Override
    public void cleanUp() throws Exception {
        super.cleanUp();
        if (mSession != null) {
            mSession.close();
            mSession = null;
        }
    }

    @Test
    public void testBuilder() {
        prepareLooper();
        MediaSession2.Builder builder;
        try {
            builder = new MediaSession2.Builder(mContext, null);
            fail("null player shouldn't be allowed");
        } catch (IllegalArgumentException e) {
            // expected. pass-through
        }
        try {
            builder = new MediaSession2.Builder(mContext, mPlayer);
            builder.setId(null);
            fail("null id shouldn't be allowed");
        } catch (IllegalArgumentException e) {
            // expected. pass-through
        }
    }

    @Test
    public void testBuilder_emptyStringAsId() throws Exception {
        prepareLooper();
        try (MediaSession2 session = new MediaSession2.Builder(mContext, mPlayer)
                .setId("").build()) {
            // Using empty string as Id shouldn't crash.
        }
    }

    @Test
    public void testUpdatePlayer() throws Exception {
        prepareLooper();
        MockPlayer anotherPlayer = new MockPlayer(0);

        // Test if setPlayer doesn't crash with various situations.
        mSession.updatePlayer(mPlayer);
        assertEquals(mPlayer, mSession.getPlayer());

        mSession.updatePlayer(anotherPlayer);
        assertEquals(anotherPlayer, mSession.getPlayer());
    }

    @Test
    public void testUpdatePlayer_playbackInfo() throws Exception {
        prepareLooper();
        MockPlayer player = new MockPlayer(0);
        final AudioAttributesCompat attrs = new AudioAttributesCompat.Builder()
                .setContentType(CONTENT_TYPE_MUSIC)
                .build();
        player.setAudioAttributes(attrs);

        final int maxVolume = 100;
        final int currentVolume = 23;
        final int volumeControlType = VOLUME_CONTROL_ABSOLUTE;

        final CountDownLatch latch = new CountDownLatch(1);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onPlaybackInfoChanged(MediaController2 controller, PlaybackInfo info) {
                Assert.assertEquals(PlaybackInfo.PLAYBACK_TYPE_REMOTE, info.getPlaybackType());
                assertEquals(attrs, info.getAudioAttributes());
                assertEquals(volumeControlType, info.getPlaybackType());
                assertEquals(maxVolume, info.getMaxVolume());
                assertEquals(currentVolume, info.getCurrentVolume());
                latch.countDown();
            }
        };

        mSession.updatePlayer(player);

        final MediaController2 controller = createController(mSession.getToken(), true, callback);
        PlaybackInfo info = controller.getPlaybackInfo();
        assertNotNull(info);
        assertEquals(PlaybackInfo.PLAYBACK_TYPE_LOCAL, info.getPlaybackType());
        assertEquals(attrs, info.getAudioAttributes());
        AudioManager manager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);

        int localVolumeControlType = VOLUME_CONTROL_ABSOLUTE;
        if (Build.VERSION.SDK_INT >= 21 && manager.isVolumeFixed()) {
            localVolumeControlType = VOLUME_CONTROL_FIXED;
        }
        assertEquals(localVolumeControlType, info.getControlType());
        assertEquals(manager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), info.getMaxVolume());
        assertEquals(manager.getStreamVolume(AudioManager.STREAM_MUSIC), info.getCurrentVolume());

        MockRemotePlayer remotePlayer = new MockRemotePlayer(
                volumeControlType, maxVolume, currentVolume) {
            @Override
            public AudioAttributesCompat getAudioAttributes() {
                return attrs;
            }
        };
        mSession.updatePlayer(remotePlayer);
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));

        info = controller.getPlaybackInfo();
        assertNotNull(info);
        assertEquals(PlaybackInfo.PLAYBACK_TYPE_REMOTE, info.getPlaybackType());
        assertEquals(attrs, info.getAudioAttributes());
        assertEquals(volumeControlType, info.getControlType());
        assertEquals(maxVolume, info.getMaxVolume());
        assertEquals(currentVolume, info.getCurrentVolume());
    }

    @Test
    public void testPlay() throws Exception {
        prepareLooper();
        mSession.getPlayer().play();
        assertTrue(mPlayer.mPlayCalled);
    }

    @Test
    public void testPause() throws Exception {
        prepareLooper();
        mSession.getPlayer().pause();
        assertTrue(mPlayer.mPauseCalled);
    }

    @Test
    public void testPrepare() throws Exception {
        prepareLooper();
        mSession.getPlayer().prefetch();
        assertTrue(mPlayer.mPrepareCalled);
    }

    @Test
    public void testSeekTo() throws Exception {
        prepareLooper();
        final long pos = 1004L;
        mSession.getPlayer().seekTo(pos);
        assertTrue(mPlayer.mSeekToCalled);
        assertEquals(pos, mPlayer.mSeekPosition);
    }

    @Test
    public void testGetDuration() throws Exception {
        prepareLooper();
        final long testDuration = 9999;
        mPlayer.mDuration = testDuration;
        mPlayer.mLastPlayerState = SessionPlayer2.PLAYER_STATE_PLAYING;
        assertEquals(testDuration, mSession.getPlayer().getDuration());
    }

    @Test
    public void testSetPlaybackSpeed() throws Exception {
        prepareLooper();
        final float speed = 1.5f;
        mSession.getPlayer().setPlaybackSpeed(speed);
        assertTrue(mPlayer.mSetPlaybackSpeedCalled);
        assertEquals(speed, mPlayer.mPlaybackSpeed, 0.0f);
    }

    @Test
    public void testGetPlaybackSpeed() throws Exception {
        prepareLooper();
        final float speed = 1.5f;
        mPlayer.setPlaybackSpeed(speed);
        mPlayer.mLastPlayerState = SessionPlayer2.PLAYER_STATE_PLAYING;
        assertEquals(speed, mSession.getPlayer().getPlaybackSpeed(), 0.0f);
    }

    @Test
    public void testGetCurrentMediaItem() {
        prepareLooper();
        MediaItem2 item = TestUtils.createMediaItemWithMetadata();
        mPlayer.mCurrentMediaItem = item;
        assertEquals(item, mSession.getPlayer().getCurrentMediaItem());
    }

    @Test
    public void testSkipToPreviousItem() {
        prepareLooper();
        mSession.getPlayer().skipToPreviousPlaylistItem();
        assertTrue(mPlayer.mSkipToPreviousItemCalled);
    }

    @Test
    public void testSkipToNextItem() throws Exception {
        prepareLooper();
        mSession.getPlayer().skipToNextPlaylistItem();
        assertTrue(mPlayer.mSkipToNextItemCalled);
    }

    @Test
    public void testSkipToPlaylistItem() throws Exception {
        prepareLooper();
        final MediaItem2 testMediaItem = TestUtils.createMediaItemWithMetadata();
        mSession.getPlayer().skipToPlaylistItem(testMediaItem);
        assertTrue(mPlayer.mSkipToPlaylistItemCalled);
        assertSame(testMediaItem, mPlayer.mItem);
    }

    @Test
    public void testGetPlayerState() {
        prepareLooper();
        final int state = SessionPlayer2.PLAYER_STATE_PLAYING;
        mPlayer.mLastPlayerState = state;
        assertEquals(state, mSession.getPlayer().getPlayerState());
    }

    @Test
    public void testGetBufferingState() {
        prepareLooper();
        final int bufferingState = SessionPlayer2.BUFFERING_STATE_BUFFERING_AND_PLAYABLE;
        mPlayer.mLastBufferingState = bufferingState;
        assertEquals(bufferingState, mSession.getPlayer().getBufferingState());
    }

    @Test
    public void testGetPosition() {
        prepareLooper();
        final long position = 150000;
        mPlayer.mCurrentPosition = position;
        mPlayer.mLastPlayerState = SessionPlayer2.PLAYER_STATE_PLAYING;
        assertEquals(position, mSession.getPlayer().getCurrentPosition());
    }

    @Test
    public void testGetBufferedPosition() {
        prepareLooper();
        final long bufferedPosition = 900000;
        mPlayer.mBufferedPosition = bufferedPosition;
        mPlayer.mLastPlayerState = SessionPlayer2.PLAYER_STATE_PLAYING;
        assertEquals(bufferedPosition, mSession.getPlayer().getBufferedPosition());
    }

    @Test
    public void testSetPlaylist() {
        prepareLooper();
        final List<MediaItem2> list = TestUtils.createPlaylist(2);
        mSession.getPlayer().setPlaylist(list, null);
        assertTrue(mPlayer.mSetPlaylistCalled);
        assertSame(list, mPlayer.mPlaylist);
        assertNull(mPlayer.mMetadata);
    }

    @Test
    public void testGetPlaylist() {
        prepareLooper();
        final List<MediaItem2> list = TestUtils.createPlaylist(2);
        mPlayer.mPlaylist = list;
        assertEquals(list, mSession.getPlayer().getPlaylist());
    }

    @Test
    public void testUpdatePlaylistMetadata() {
        prepareLooper();
        final MediaMetadata2 testMetadata = TestUtils.createMetadata();
        mSession.getPlayer().updatePlaylistMetadata(testMetadata);
        assertTrue(mPlayer.mUpdatePlaylistMetadataCalled);
        assertSame(testMetadata, mPlayer.mMetadata);
    }

    @Test
    public void testGetPlaylistMetadata() {
        prepareLooper();
        final MediaMetadata2 testMetadata = TestUtils.createMetadata();
        mPlayer.mMetadata = testMetadata;
        assertEquals(testMetadata, mSession.getPlayer().getPlaylistMetadata());
    }

    @Test
    public void testAddPlaylistItem() {
        prepareLooper();
        final int testIndex = 12;
        final MediaItem2 testMediaItem = TestUtils.createMediaItemWithMetadata();
        mSession.getPlayer().addPlaylistItem(testIndex, testMediaItem);
        assertTrue(mPlayer.mAddPlaylistItemCalled);
        assertEquals(testIndex, mPlayer.mIndex);
        assertSame(testMediaItem, mPlayer.mItem);
    }

    @Test
    public void testRemovePlaylistItem() {
        prepareLooper();
        final MediaItem2 testMediaItem = TestUtils.createMediaItemWithMetadata();
        mSession.getPlayer().removePlaylistItem(testMediaItem);
        assertTrue(mPlayer.mRemovePlaylistItemCalled);
        assertSame(testMediaItem, mPlayer.mItem);
    }

    @Test
    public void testReplacePlaylistItem() throws InterruptedException {
        prepareLooper();
        final int testIndex = 12;
        final MediaItem2 testMediaItem = TestUtils.createMediaItemWithMetadata();
        mSession.getPlayer().replacePlaylistItem(testIndex, testMediaItem);
        assertTrue(mPlayer.mReplacePlaylistItemCalled);
        assertEquals(testIndex, mPlayer.mIndex);
        assertSame(testMediaItem, mPlayer.mItem);
    }

    @Test
    public void testSetShuffleMode() {
        prepareLooper();
        final int testShuffleMode = SessionPlayer2.SHUFFLE_MODE_GROUP;
        mSession.getPlayer().setShuffleMode(testShuffleMode);
        assertTrue(mPlayer.mSetShuffleModeCalled);
        assertEquals(testShuffleMode, mPlayer.mShuffleMode);
    }

    @Test
    public void testSetRepeatMode() {
        prepareLooper();
        final int testRepeatMode = SessionPlayer2.REPEAT_MODE_GROUP;
        mSession.getPlayer().setRepeatMode(testRepeatMode);
        assertTrue(mPlayer.mSetRepeatModeCalled);
        assertEquals(testRepeatMode, mPlayer.mRepeatMode);
    }

    @Test
    public void testOnCommandCallback() throws InterruptedException {
        prepareLooper();
        final MockOnCommandCallback callback = new MockOnCommandCallback();
        sHandler.postAndSync(new Runnable() {
            @Override
            public void run() {
                mSession.close();
                mPlayer = new MockPlayer(1);
                mSession = new MediaSession2.Builder(mContext, mPlayer)
                        .setSessionCallback(sHandlerExecutor, callback).build();
            }
        });
        MediaController2 controller = createController(mSession.getToken());
        controller.pause();
        assertFalse(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertFalse(mPlayer.mPauseCalled);
        assertEquals(1, callback.commands.size());
        assertEquals(SessionCommand2.COMMAND_CODE_PLAYER_PAUSE,
                (long) callback.commands.get(0).getCommandCode());

        controller.play();
        assertTrue(mPlayer.mCountDownLatch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        assertTrue(mPlayer.mPlayCalled);
        assertFalse(mPlayer.mPauseCalled);
        assertEquals(2, callback.commands.size());
        assertEquals(SessionCommand2.COMMAND_CODE_PLAYER_PLAY,
                (long) callback.commands.get(1).getCommandCode());
    }

    @Test
    public void testOnConnectCallback() throws InterruptedException {
        prepareLooper();
        final MockOnConnectCallback sessionCallback = new MockOnConnectCallback();
        sHandler.postAndSync(new Runnable() {
            @Override
            public void run() {
                mSession.close();
                mSession = new MediaSession2.Builder(mContext, mPlayer)
                        .setSessionCallback(sHandlerExecutor, sessionCallback).build();
            }
        });
        MediaController2 controller = createController(mSession.getToken(), false, null);
        assertNotNull(controller);
        waitForConnect(controller, false);
        waitForDisconnect(controller, true);
    }

    @Test
    public void testOnDisconnectCallback() throws InterruptedException {
        prepareLooper();
        final CountDownLatch latch = new CountDownLatch(1);
        try (MediaSession2 session = new MediaSession2.Builder(mContext, mPlayer)
                .setId("testOnDisconnectCallback")
                .setSessionCallback(sHandlerExecutor, new SessionCallback() {
                    @Override
                    public void onDisconnected(MediaSession2 session,
                            ControllerInfo controller) {
                        assertEquals(Process.myUid(), controller.getUid());
                        latch.countDown();
                    }
                }).build()) {
            MediaController2 controller = createController(session.getToken());
            controller.close();
            assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testSetCustomLayout() throws InterruptedException {
        prepareLooper();
        final List<CommandButton> customLayout = new ArrayList<>();
        customLayout.add(new CommandButton.Builder()
                .setCommand(new SessionCommand2(SessionCommand2.COMMAND_CODE_PLAYER_PLAY))
                .setDisplayName("button")
                .build());
        final CountDownLatch latch = new CountDownLatch(1);

        try (MediaSession2 session = new MediaSession2.Builder(mContext, mPlayer)
                .setId("testSetCustomLayout")
                .setSessionCallback(sHandlerExecutor, new SessionCallback() {
                    @Override
                    public SessionCommandGroup2 onConnect(MediaSession2 session,
                            ControllerInfo controller) {
                        if (mContext.getPackageName().equals(controller.getPackageName())) {
                            mTestControllerInfo = controller;
                            return super.onConnect(session, controller);
                        }
                        return null;
                    }
                })
                .build()) {

            final ControllerCallback callback = new ControllerCallback() {
                @Override
                public void onCustomLayoutChanged(MediaController2 controller2,
                        List<CommandButton> layout) {
                    assertEquals(customLayout.size(), layout.size());
                    for (int i = 0; i < layout.size(); i++) {
                        assertEquals(customLayout.get(i).getCommand(), layout.get(i).getCommand());
                        assertEquals(customLayout.get(i).getDisplayName(),
                                layout.get(i).getDisplayName());
                    }
                    latch.countDown();
                }
            };
            MediaController2 controller = createController(session.getToken(), true, callback);
            session.setCustomLayout(mTestControllerInfo, customLayout);
            assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Test
    public void testSetAllowedCommands() throws InterruptedException {
        prepareLooper();
        final SessionCommandGroup2 commands = new SessionCommandGroup2.Builder()
                .addCommand(new SessionCommand2(SessionCommand2.COMMAND_CODE_PLAYER_PLAY))
                .addCommand(new SessionCommand2(SessionCommand2.COMMAND_CODE_PLAYER_PAUSE))
                .build();

        final CountDownLatch latch = new CountDownLatch(1);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onAllowedCommandsChanged(MediaController2 controller,
                    SessionCommandGroup2 commandsOut) {
                assertNotNull(commandsOut);
                Set<SessionCommand2> expected = commands.getCommands();
                Set<SessionCommand2> actual = commandsOut.getCommands();

                assertNotNull(actual);
                assertEquals(expected.size(), actual.size());
                for (SessionCommand2 command : expected) {
                    assertTrue(actual.contains(command));
                }
                latch.countDown();
            }
        };

        final MediaController2 controller = createController(mSession.getToken(), true, callback);
        ControllerInfo controllerInfo = getTestControllerInfo();
        assertNotNull(controllerInfo);

        mSession.setAllowedCommands(controllerInfo, commands);
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testSendCustomCommand() throws InterruptedException {
        prepareLooper();
        final SessionCommand2 testCommand = new SessionCommand2(
                SessionCommand2.COMMAND_CODE_PLAYER_PREFETCH);
        final Bundle testArgs = new Bundle();
        testArgs.putString("args", "testSendCustomAction");

        final CountDownLatch latch = new CountDownLatch(2);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onCustomCommand(MediaController2 controller, SessionCommand2 command,
                    Bundle args, ResultReceiver receiver) {
                assertEquals(testCommand, command);
                assertTrue(TestUtils.equals(testArgs, args));
                assertNull(receiver);
                latch.countDown();
            }
        };
        final MediaController2 controller =
                createController(mSession.getToken(), true, callback);
        // TODO(jaewan): Test with multiple controllers
        mSession.sendCustomCommand(testCommand, testArgs);

        ControllerInfo controllerInfo = getTestControllerInfo();
        assertNotNull(controllerInfo);
        // TODO(jaewan): Test receivers as well.
        mSession.sendCustomCommand(controllerInfo, testCommand, testArgs, null);
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testNotifyRoutesInfoChanged() throws InterruptedException {
        prepareLooper();
        final CountDownLatch latch = new CountDownLatch(1);
        final ControllerCallback callback = new ControllerCallback() {
            @Override
            public void onRoutesInfoChanged(@NonNull MediaController2 controller,
                    @Nullable List<Bundle> routes) {
                assertNull(routes);
                latch.countDown();
            }
        };
        final MediaController2 controller = createController(mSession.getToken(), true, callback);
        ControllerInfo controllerInfo = getTestControllerInfo();
        mSession.notifyRoutesInfoChanged(controllerInfo, null);
        assertTrue(latch.await(WAIT_TIME_MS, TimeUnit.MILLISECONDS));
    }

    private ControllerInfo getTestControllerInfo() {
        List<ControllerInfo> controllers = mSession.getConnectedControllers();
        assertNotNull(controllers);
        for (int i = 0; i < controllers.size(); i++) {
            if (Process.myUid() == controllers.get(i).getUid()) {
                return controllers.get(i);
            }
        }
        fail("Failed to get test controller info");
        return null;
    }

    public class MockOnConnectCallback extends SessionCallback {
        @Override
        public SessionCommandGroup2 onConnect(MediaSession2 session,
                ControllerInfo controllerInfo) {
            if (Process.myUid() != controllerInfo.getUid()) {
                return null;
            }
            assertEquals(mContext.getPackageName(), controllerInfo.getPackageName());
            assertEquals(Process.myUid(), controllerInfo.getUid());
            assertFalse(controllerInfo.isTrusted());
            // Reject all
            return null;
        }
    }

    public class MockOnCommandCallback extends SessionCallback {
        public final ArrayList<SessionCommand2> commands = new ArrayList<>();

        @Override
        public int onCommandRequest(MediaSession2 session, ControllerInfo controllerInfo,
                SessionCommand2 command) {
            assertEquals(mContext.getPackageName(), controllerInfo.getPackageName());
            assertEquals(Process.myUid(), controllerInfo.getUid());
            assertFalse(controllerInfo.isTrusted());
            commands.add(command);
            if (command.getCommandCode() == SessionCommand2.COMMAND_CODE_PLAYER_PAUSE) {
                return RESULT_CODE_INVALID_STATE;
            }
            return RESULT_CODE_SUCCESS;
        }
    }
}

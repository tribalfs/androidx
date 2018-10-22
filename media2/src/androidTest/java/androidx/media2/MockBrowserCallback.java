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

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertTrue;

import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.GuardedBy;
import androidx.media2.MediaBrowser2.BrowserCallback;
import androidx.media2.MediaController2.ControllerCallback;
import androidx.media2.MediaSession2.CommandButton;
import androidx.media2.MediaSession2TestBase.TestControllerCallbackInterface;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Mock {@link MediaBrowser2.BrowserCallback} that implements
 * {@link TestControllerCallbackInterface}
 */
public class MockBrowserCallback extends BrowserCallback
        implements TestControllerCallbackInterface {

    private final ControllerCallback mCallbackProxy;
    public final CountDownLatch connectLatch = new CountDownLatch(1);
    public final CountDownLatch disconnectLatch = new CountDownLatch(1);
    @GuardedBy("this")
    private Runnable mOnCustomCommandRunnable;

    MockBrowserCallback(ControllerCallback callbackProxy) {
        if (callbackProxy == null) {
            callbackProxy = new BrowserCallback() {
            };
        }
        mCallbackProxy = callbackProxy;
    }

    @CallSuper
    @Override
    public void onConnected(MediaController2 controller, SessionCommandGroup2 commands) {
        connectLatch.countDown();
        mCallbackProxy.onConnected(controller, commands);
    }

    @CallSuper
    @Override
    public void onDisconnected(MediaController2 controller) {
        disconnectLatch.countDown();
        mCallbackProxy.onDisconnected(controller);
    }

    @Override
    public void waitForConnect(boolean expect) throws InterruptedException {
        if (expect) {
            assertTrue(connectLatch.await(
                    MediaSession2TestBase.TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } else {
            assertFalse(connectLatch.await(
                    MediaSession2TestBase.TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Override
    public void waitForDisconnect(boolean expect) throws InterruptedException {
        if (expect) {
            assertTrue(disconnectLatch.await(
                    MediaSession2TestBase.TIMEOUT_MS, TimeUnit.MILLISECONDS));
        } else {
            assertFalse(disconnectLatch.await(
                    MediaSession2TestBase.TIMEOUT_MS, TimeUnit.MILLISECONDS));
        }
    }

    @Override
    public void onPlaybackInfoChanged(MediaController2 controller,
            MediaController2.PlaybackInfo info) {
        mCallbackProxy.onPlaybackInfoChanged(controller, info);
    }

    @Override
    public MediaController2.ControllerResult onCustomCommand(
            MediaController2 controller, SessionCommand2 command, Bundle args) {
        synchronized (this) {
            if (mOnCustomCommandRunnable != null) {
                mOnCustomCommandRunnable.run();
            }
        }
        return mCallbackProxy.onCustomCommand(controller, command, args);
    }

    @Override
    public int onSetCustomLayout(MediaController2 controller, List<CommandButton> layout) {
        return mCallbackProxy.onSetCustomLayout(controller, layout);
    }

    @Override
    public void onAllowedCommandsChanged(MediaController2 controller,
            SessionCommandGroup2 commands) {
        mCallbackProxy.onAllowedCommandsChanged(controller, commands);
    }

    @Override
    public void onPlayerStateChanged(MediaController2 controller, int state) {
        mCallbackProxy.onPlayerStateChanged(controller, state);
    }

    @Override
    public void onSeekCompleted(MediaController2 controller, long position) {
        mCallbackProxy.onSeekCompleted(controller, position);
    }

    @Override
    public void onPlaybackSpeedChanged(MediaController2 controller, float speed) {
        mCallbackProxy.onPlaybackSpeedChanged(controller, speed);
    }

    @Override
    public void onBufferingStateChanged(MediaController2 controller, MediaItem2 item,
            int state) {
        mCallbackProxy.onBufferingStateChanged(controller, item, state);
    }

    @Override
    public void onCurrentMediaItemChanged(MediaController2 controller, MediaItem2 item) {
        mCallbackProxy.onCurrentMediaItemChanged(controller, item);
    }

    @Override
    public void onPlaylistChanged(MediaController2 controller,
            List<MediaItem2> list, MediaMetadata2 metadata) {
        mCallbackProxy.onPlaylistChanged(controller, list, metadata);
    }

    @Override
    public void onPlaylistMetadataChanged(MediaController2 controller,
            MediaMetadata2 metadata) {
        mCallbackProxy.onPlaylistMetadataChanged(controller, metadata);
    }

    @Override
    public void onShuffleModeChanged(MediaController2 controller, int shuffleMode) {
        mCallbackProxy.onShuffleModeChanged(controller, shuffleMode);
    }

    @Override
    public void onRepeatModeChanged(MediaController2 controller, int repeatMode) {
        mCallbackProxy.onRepeatModeChanged(controller, repeatMode);
    }

    @Override
    public void onGetLibraryRootDone(MediaBrowser2 browser, Bundle rootHints,
            String rootMediaId, Bundle rootExtra) {
        super.onGetLibraryRootDone(browser, rootHints, rootMediaId, rootExtra);
        if (mCallbackProxy instanceof BrowserCallback) {
            ((BrowserCallback) mCallbackProxy)
                    .onGetLibraryRootDone(browser, rootHints, rootMediaId, rootExtra);
        }
    }

    @Override
    public void onGetItemDone(MediaBrowser2 browser, String mediaId, MediaItem2 result) {
        super.onGetItemDone(browser, mediaId, result);
        if (mCallbackProxy instanceof BrowserCallback) {
            ((BrowserCallback) mCallbackProxy).onGetItemDone(browser, mediaId, result);
        }
    }

    @Override
    public void onGetChildrenDone(MediaBrowser2 browser, String parentId, int page,
            int pageSize, List<MediaItem2> result, Bundle extras) {
        super.onGetChildrenDone(browser, parentId, page, pageSize, result, extras);
        if (mCallbackProxy instanceof BrowserCallback) {
            ((BrowserCallback) mCallbackProxy)
                    .onGetChildrenDone(browser, parentId, page, pageSize, result, extras);
        }
    }

    @Override
    public void onSearchResultChanged(MediaBrowser2 browser, String query, int itemCount,
            Bundle extras) {
        super.onSearchResultChanged(browser, query, itemCount, extras);
        if (mCallbackProxy instanceof BrowserCallback) {
            ((BrowserCallback) mCallbackProxy)
                    .onSearchResultChanged(browser, query, itemCount, extras);
        }
    }

    @Override
    public void onGetSearchResultDone(MediaBrowser2 browser, String query, int page,
            int pageSize, List<MediaItem2> result, Bundle extras) {
        super.onGetSearchResultDone(browser, query, page, pageSize, result, extras);
        if (mCallbackProxy instanceof BrowserCallback) {
            ((BrowserCallback) mCallbackProxy)
                    .onGetSearchResultDone(browser, query, page, pageSize, result, extras);
        }
    }

    @Override
    public void onChildrenChanged(MediaBrowser2 browser, String parentId, int itemCount,
            Bundle extras) {
        super.onChildrenChanged(browser, parentId, itemCount, extras);
        if (mCallbackProxy instanceof BrowserCallback) {
            ((BrowserCallback) mCallbackProxy)
                    .onChildrenChanged(browser, parentId, itemCount, extras);
        }
    }

    @Override
    public void setRunnableForOnCustomCommand(Runnable runnable) {
        synchronized (this) {
            mOnCustomCommandRunnable = runnable;
        }
    }
}

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

import static androidx.media.test.lib.CommonConstants.MOCK_MEDIA_LIBRARY_SERVICE;
import static androidx.media.test.lib.MediaBrowserConstants.CUSTOM_ACTION_ASSERT_PARAMS;
import static androidx.media.test.lib.MediaBrowserConstants.LONG_LIST_COUNT;
import static androidx.media.test.lib.MediaBrowserConstants.NOTIFY_CHILDREN_CHANGED_EXTRAS;
import static androidx.media.test.lib.MediaBrowserConstants.NOTIFY_CHILDREN_CHANGED_ITEM_COUNT;
import static androidx.media.test.lib.MediaBrowserConstants.ROOT_EXTRAS;
import static androidx.media.test.lib.MediaBrowserConstants.ROOT_ID;
import static androidx.media.test.lib.MediaBrowserConstants
        .SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL;
import static androidx.media.test.lib.MediaBrowserConstants
        .SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL_WITH_NON_SUBSCRIBED_ID;
import static androidx.media.test.lib.MediaBrowserConstants
        .SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE;
import static androidx.media.test.lib.MediaBrowserConstants
        .SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE_WITH_NON_SUBSCRIBED_ID;
import static androidx.media2.MediaBrowser.BrowserResult.RESULT_CODE_BAD_VALUE;
import static androidx.media2.MediaBrowser.BrowserResult.RESULT_CODE_SUCCESS;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

import static org.junit.Assert.assertNotEquals;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.CallSuper;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media.test.client.MediaTestUtils;
import androidx.media.test.lib.MediaBrowserConstants;
import androidx.media.test.lib.TestUtils;
import androidx.media2.MediaBrowser;
import androidx.media2.MediaBrowser.BrowserCallback;
import androidx.media2.MediaBrowser.BrowserResult;
import androidx.media2.MediaController;
import androidx.media2.MediaController.ControllerCallback;
import androidx.media2.MediaController.ControllerResult;
import androidx.media2.MediaItem;
import androidx.media2.MediaLibraryService.LibraryParams;
import androidx.media2.MediaMetadata;
import androidx.media2.MediaSession.CommandButton;
import androidx.media2.SessionCommand;
import androidx.media2.SessionCommandGroup;
import androidx.media2.SessionToken;
import androidx.test.filters.FlakyTest;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import androidx.versionedparcelable.ParcelUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests {@link MediaBrowser.BrowserCallback}.
 * <p>
 * This test inherits {@link MediaControllerCallbackTest} to ensure that inherited APIs from
 * {@link MediaController} works cleanly.
 */
// TODO: (internal cleanup) Move tests that aren't related with callbacks.
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.JELLY_BEAN)
@RunWith(AndroidJUnit4.class)
@SmallTest
public class MediaBrowserCallbackTest extends MediaControllerCallbackTest {
    private static final String TAG = "MediaBrowserCallbackTest";

    @Override
    TestControllerInterface onCreateController(final @NonNull SessionToken token,
            final @Nullable ControllerCallback callback) throws InterruptedException {
        assertNotNull("Test bug", token);
        final AtomicReference<TestControllerInterface> controller = new AtomicReference<>();
        sHandler.postAndSync(new Runnable() {
            @Override
            public void run() {
                // Create controller on the test handler, for changing MediaBrowserCompat's Handler
                // Looper. Otherwise, MediaBrowserCompat will post all the commands to the handler
                // and commands wouldn't be run if tests codes waits on the test handler.
                controller.set(new TestMediaBrowser(
                        mContext, token, new TestBrowserCallback(callback)));
            }
        });
        return controller.get();
    }

    final MediaBrowser createBrowser() throws InterruptedException {
        return createBrowser(null);
    }

    final MediaBrowser createBrowser(@Nullable BrowserCallback callback)
            throws InterruptedException {
        final SessionToken token = new SessionToken(mContext, MOCK_MEDIA_LIBRARY_SERVICE);
        return (MediaBrowser) createController(token, true, callback);
    }

    /**
     * Test if the {@link TestBrowserCallback} wraps the callback proxy without missing any method.
     */
    @Test
    public void testTestBrowserCallback() {
        prepareLooper();
        Method[] methods = TestBrowserCallback.class.getMethods();
        assertNotNull(methods);
        for (int i = 0; i < methods.length; i++) {
            // For any methods in the controller callback, TestControllerCallback should have
            // overriden the method and call matching API in the callback proxy.
            assertNotEquals("TestBrowserCallback should override " + methods[i]
                            + " and call callback proxy",
                    BrowserCallback.class, methods[i].getDeclaringClass());
            assertNotEquals("TestBrowserCallback should override " + methods[i]
                            + " and call callback proxy",
                    ControllerCallback.class, methods[i].getDeclaringClass());
        }
    }

    @Test
    public void testGetLibraryRoot() throws Exception {
        prepareLooper();
        final LibraryParams params = new LibraryParams.Builder()
                .setOffline(true).setRecent(true).setExtras(new Bundle()).build();

        MediaBrowser browser = createBrowser();
        setExpectedLibraryParam(browser, params);
        BrowserResult result = browser.getLibraryRoot(params)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());
        MediaMetadata metadata = result.getMediaItem().getMetadata();
        assertEquals(ROOT_ID, metadata.getString(MediaMetadata.METADATA_KEY_MEDIA_ID));
        assertTrue(TestUtils.equals(ROOT_EXTRAS, result.getLibraryParams().getExtras()));
    }

    @Test
    public void testGetItem() throws Exception {
        prepareLooper();
        final String mediaId = MediaBrowserConstants.MEDIA_ID_GET_ITEM;

        BrowserResult result = createBrowser().getItem(mediaId)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());
        MediaTestUtils.assertMediaItemHasId(result.getMediaItem(), mediaId);
    }

    @Test
    public void testGetItem_unknownId() throws Exception {
        prepareLooper();
        final String mediaId = "random_media_id";

        BrowserResult result = createBrowser().getItem(mediaId)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_BAD_VALUE, result.getResultCode());
        assertNull(result.getMediaItem());
    }

    @Test
    public void testGetItem_nullResult() throws Exception {
        prepareLooper();
        final String mediaId = MediaBrowserConstants.MEDIA_ID_GET_NULL_ITEM;

        // Exception will be thrown in the service side, and the process will be crashed.
        // In that case one of following will happen
        //   Case 1) Process is crashed. Pending ListenableFuture will get error
        //   Case 2) Due to the frequent crashes with other tests, process may not crash immediately
        //           because the Android shows dialog 'xxx keeps stopping' and defer sending
        //           SIG_KILL until the user's explicit action.
        try {
            BrowserResult result = createBrowser().getItem(mediaId)
                    .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            // Case 1.
            assertNotEquals(RESULT_CODE_SUCCESS, result.getResultCode());
        } catch (TimeoutException e) {
            // Case 2.
        }
    }

    @Test
    public void testGetItem_invalidResult() throws Exception {
        prepareLooper();
        final String mediaId = MediaBrowserConstants.MEDIA_ID_GET_INVALID_ITEM;

        // Exception will be thrown in the service side, and the process will be crashed.
        // In that case one of following will happen
        //   Case 1) Process is crashed. Pending ListenableFuture will get error
        //   Case 2) Due to the frequent crashes with other tests, process may not crash immediately
        //           because the Android shows dialog 'xxx keeps stopping' and defer sending
        //           SIG_KILL until the user's explicit action.
        try {
            BrowserResult result = createBrowser().getItem(mediaId)
                    .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
            // Case 1.
            assertNotEquals(RESULT_CODE_SUCCESS, result.getResultCode());
        } catch (TimeoutException e) {
            // Case 2.
        }
    }

    @Test
    public void testGetChildren() throws Exception {
        prepareLooper();
        final String parentId = MediaBrowserConstants.PARENT_ID;
        final int page = 4;
        final int pageSize = 10;
        final LibraryParams params = MediaTestUtils.createLibraryParams();

        MediaBrowser browser = createBrowser();
        setExpectedLibraryParam(browser, params);

        BrowserResult result = browser.getChildren(parentId, page, pageSize, params)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());
        assertNull(result.getLibraryParams());

        MediaTestUtils.assertPaginatedListHasIds(
                result.getMediaItems(), MediaBrowserConstants.GET_CHILDREN_RESULT,
                page, pageSize);
    }

    @Test
    @LargeTest
    public void testGetChildren_withLongList() throws Exception {
        prepareLooper();
        final String parentId = MediaBrowserConstants.PARENT_ID_LONG_LIST;
        final int page = 0;
        final int pageSize = Integer.MAX_VALUE;
        final LibraryParams params = MediaTestUtils.createLibraryParams();

        MediaBrowser browser = createBrowser();
        setExpectedLibraryParam(browser, params);

        BrowserResult result = browser.getChildren(parentId, page, pageSize, params)
                .get(10, TimeUnit.SECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());
        assertNull(result.getLibraryParams());

        List<MediaItem> list = result.getMediaItems();
        assertEquals(LONG_LIST_COUNT, list.size());
        for (int i = 0; i < result.getMediaItems().size(); i++) {
            assertEquals(TestUtils.getMediaIdInDummyList(i), list.get(i).getMediaId());
        }
    }

    @Test
    public void testGetChildren_emptyResult() throws Exception {
        prepareLooper();
        final String parentId = MediaBrowserConstants.PARENT_ID_NO_CHILDREN;

        MediaBrowser browser = createBrowser();
        BrowserResult result = browser.getChildren(parentId, 1, 1, null)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());
        assertEquals(0, result.getMediaItems().size());
    }

    @Test
    public void testGetChildren_nullResult() throws Exception {
        prepareLooper();
        final String parentId = MediaBrowserConstants.PARENT_ID_ERROR;

        MediaBrowser browser = createBrowser();
        BrowserResult result = browser.getChildren(parentId, 1, 1, null)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertNotEquals(RESULT_CODE_SUCCESS, result.getResultCode());
        assertNull(result.getMediaItems());
    }

    @Test
    public void testSearchCallbacks() throws Exception {
        prepareLooper();
        final String query = MediaBrowserConstants.SEARCH_QUERY;
        final int page = 4;
        final int pageSize = 10;
        final LibraryParams testParams = MediaTestUtils.createLibraryParams();

        final CountDownLatch latchForSearch = new CountDownLatch(1);
        final BrowserCallback callback = new BrowserCallback() {
            @Override
            public void onSearchResultChanged(MediaBrowser browser,
                    String queryOut, int itemCount, LibraryParams params) {
                assertEquals(query, queryOut);
                MediaTestUtils.assertLibraryParamsEquals(testParams, params);
                assertEquals(MediaBrowserConstants.SEARCH_RESULT_COUNT, itemCount);
                latchForSearch.countDown();
            }
        };

        // Request the search.
        MediaBrowser browser = createBrowser(callback);
        setExpectedLibraryParam(browser, testParams);
        BrowserResult result = browser.search(query, testParams)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());

        // Get the search result.
        result = browser.getSearchResult(query, page, pageSize, testParams)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());
        MediaTestUtils.assertPaginatedListHasIds(result.getMediaItems(),
                MediaBrowserConstants.SEARCH_RESULT, page, pageSize);
    }

    @Test
    @LargeTest
    public void testSearchCallbacks_withLongList() throws Exception {
        prepareLooper();
        final String query = MediaBrowserConstants.SEARCH_QUERY_LONG_LIST;
        final int page = 0;
        final int pageSize = Integer.MAX_VALUE;
        final LibraryParams testParams = MediaTestUtils.createLibraryParams();

        final CountDownLatch latch = new CountDownLatch(1);
        final BrowserCallback callback = new BrowserCallback() {
            @Override
            public void onSearchResultChanged(
                    MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
                assertEquals(query, queryOut);
                MediaTestUtils.assertLibraryParamsEquals(testParams, params);
                assertEquals(MediaBrowserConstants.LONG_LIST_COUNT, itemCount);
                latch.countDown();
            }
        };

        MediaBrowser browser = createBrowser(callback);
        setExpectedLibraryParam(browser, testParams);
        BrowserResult result = browser.search(query, testParams)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());

        result = browser.getSearchResult(query, page, pageSize, testParams)
                .get(10, TimeUnit.SECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());
        List<MediaItem> list = result.getMediaItems();
        for (int i = 0; i < list.size(); i++) {
            assertEquals(TestUtils.getMediaIdInDummyList(i), list.get(i).getMediaId());
        }
    }

    @Test
    @LargeTest
    public void testOnSearchResultChanged_searchTakesTime() throws Exception {
        prepareLooper();
        final String query = MediaBrowserConstants.SEARCH_QUERY_TAKES_TIME;
        final LibraryParams testParams = MediaTestUtils.createLibraryParams();

        final CountDownLatch latch = new CountDownLatch(1);
        final BrowserCallback callback = new BrowserCallback() {
            @Override
            public void onSearchResultChanged(
                    MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
                assertEquals(query, queryOut);
                MediaTestUtils.assertLibraryParamsEquals(testParams, params);
                assertEquals(MediaBrowserConstants.SEARCH_RESULT_COUNT, itemCount);
                latch.countDown();
            }
        };

        MediaBrowser browser = createBrowser(callback);
        setExpectedLibraryParam(browser, testParams);
        BrowserResult result = browser.search(query, testParams)
                .get(MediaBrowserConstants.SEARCH_TIME_IN_MS + TIMEOUT_MS,
                        TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());
    }

    @Test
    public void testOnSearchResultChanged_emptyResult() throws Exception {
        prepareLooper();
        final String query = MediaBrowserConstants.SEARCH_QUERY_EMPTY_RESULT;
        final LibraryParams testParams = MediaTestUtils.createLibraryParams();

        final CountDownLatch latch = new CountDownLatch(1);
        final BrowserCallback callback = new BrowserCallback() {
            @Override
            public void onSearchResultChanged(
                    MediaBrowser browser, String queryOut, int itemCount, LibraryParams params) {
                assertEquals(query, queryOut);
                MediaTestUtils.assertLibraryParamsEquals(testParams, params);
                assertEquals(0, itemCount);
                latch.countDown();
            }
        };

        MediaBrowser browser = createBrowser(callback);
        setExpectedLibraryParam(browser, testParams);
        BrowserResult result = browser.search(query, testParams)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());
    }

    @Test
    public void testOnChildrenChanged_calledWhenSubscribed() throws Exception {
        // This test uses MediaLibrarySession.notifyChildrenChanged().
        prepareLooper();
        final String expectedParentId = SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL;

        final CountDownLatch latch = new CountDownLatch(1);
        final BrowserCallback controllerCallbackProxy = new BrowserCallback() {
            @Override
            public void onChildrenChanged(MediaBrowser browser, String parentId,
                    int itemCount, LibraryParams params) {
                assertEquals(expectedParentId, parentId);
                assertEquals(NOTIFY_CHILDREN_CHANGED_ITEM_COUNT, itemCount);
                MediaTestUtils.assertLibraryParamsEquals(params, NOTIFY_CHILDREN_CHANGED_EXTRAS);
                latch.countDown();
            }
        };

        BrowserResult result = createBrowser(controllerCallbackProxy)
                .subscribe(expectedParentId, null)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());

        // The MediaLibrarySession in MockMediaLibraryService is supposed to call
        // notifyChildrenChanged() in its callback onSubscribe().
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testOnChildrenChanged_calledWhenSubscribed2() throws Exception {
        // This test uses MediaLibrarySession.notifyChildrenChanged(ControllerInfo).
        prepareLooper();
        final String expectedParentId = SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE;

        final CountDownLatch latch = new CountDownLatch(1);
        final BrowserCallback controllerCallbackProxy = new BrowserCallback() {
            @Override
            public void onChildrenChanged(MediaBrowser browser, String parentId,
                    int itemCount, LibraryParams params) {
                assertEquals(expectedParentId, parentId);
                assertEquals(NOTIFY_CHILDREN_CHANGED_ITEM_COUNT, itemCount);
                MediaTestUtils.assertLibraryParamsEquals(params, NOTIFY_CHILDREN_CHANGED_EXTRAS);
                latch.countDown();
            }
        };

        BrowserResult result = createBrowser(controllerCallbackProxy)
                .subscribe(expectedParentId, null)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());

        // The MediaLibrarySession in MockMediaLibraryService is supposed to call
        // notifyChildrenChanged(ControllerInfo) in its callback onSubscribe().
        assertTrue(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    @FlakyTest(bugId = 118671770)
    public void testOnChildrenChanged_notCalledWhenNotSubscribed() throws Exception {
        // This test uses MediaLibrarySession.notifyChildrenChanged().
        prepareLooper();
        final String subscribedMediaId =
                SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ALL_WITH_NON_SUBSCRIBED_ID;
        final CountDownLatch latch = new CountDownLatch(1);

        final BrowserCallback controllerCallbackProxy = new BrowserCallback() {
            @Override
            public void onChildrenChanged(MediaBrowser browser, String parentId,
                    int itemCount, LibraryParams params) {
                // Unexpected call.
                fail();
                latch.countDown();
            }
        };

        BrowserResult result = createBrowser(controllerCallbackProxy)
                .subscribe(subscribedMediaId, null)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());

        // The MediaLibrarySession in MockMediaLibraryService is supposed to call
        // notifyChildrenChanged() in its callback onSubscribe(), but with a different media ID.
        // Therefore, onChildrenChanged() should not be called.
        assertFalse(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testOnChildrenChanged_notCalledWhenNotSubscribed2() throws Exception {
        // This test uses MediaLibrarySession.notifyChildrenChanged(ControllerInfo).
        prepareLooper();
        final String subscribedMediaId =
                SUBSCRIBE_ID_NOTIFY_CHILDREN_CHANGED_TO_ONE_WITH_NON_SUBSCRIBED_ID;
        final CountDownLatch latch = new CountDownLatch(1);

        final BrowserCallback controllerCallbackProxy = new BrowserCallback() {
            @Override
            public void onChildrenChanged(MediaBrowser browser, String parentId,
                    int itemCount, LibraryParams params) {
                // Unexpected call.
                fail();
                latch.countDown();
            }
        };

        BrowserResult result = createBrowser(controllerCallbackProxy)
                .subscribe(subscribedMediaId, null)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(RESULT_CODE_SUCCESS, result.getResultCode());

        // The MediaLibrarySession in MockMediaLibraryService is supposed to call
        // notifyChildrenChanged(ControllerInfo) in its callback onSubscribe(),
        // but with a different media ID.
        // Therefore, onChildrenChanged() should not be called.
        assertFalse(latch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
    }

    private void setExpectedLibraryParam(MediaBrowser browser, LibraryParams params)
            throws Exception {
        SessionCommand command = new SessionCommand(CUSTOM_ACTION_ASSERT_PARAMS, null);
        Bundle args = new Bundle();
        ParcelUtils.putVersionedParcelable(args, CUSTOM_ACTION_ASSERT_PARAMS, params);
        ControllerResult result = browser.sendCustomCommand(command, args)
                .get(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        assertEquals(ControllerResult.RESULT_CODE_SUCCESS, result.getResultCode());
    }

    public static class TestBrowserCallback extends BrowserCallback
            implements TestControllerCallbackInterface {
        private final ControllerCallback mCallbackProxy;
        public final CountDownLatch connectLatch = new CountDownLatch(1);
        public final CountDownLatch disconnectLatch = new CountDownLatch(1);
        @GuardedBy("this")
        private Runnable mOnCustomCommandRunnable;

        TestBrowserCallback(ControllerCallback callbackProxy) {
            if (callbackProxy == null) {
                callbackProxy = new BrowserCallback() {};
            }
            mCallbackProxy = callbackProxy;
        }

        @CallSuper
        @Override
        public void onConnected(MediaController controller, SessionCommandGroup commands) {
            connectLatch.countDown();
        }

        @CallSuper
        @Override
        public void onDisconnected(MediaController controller) {
            disconnectLatch.countDown();
        }

        @Override
        public void waitForConnect(boolean expect) throws InterruptedException {
            if (expect) {
                assertTrue(connectLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } else {
                assertFalse(connectLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            }
        }

        @Override
        public void waitForDisconnect(boolean expect) throws InterruptedException {
            if (expect) {
                assertTrue(disconnectLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            } else {
                assertFalse(disconnectLatch.await(TIMEOUT_MS, TimeUnit.MILLISECONDS));
            }
        }

        @Override
        public void onPlaybackInfoChanged(MediaController controller,
                MediaController.PlaybackInfo info) {
            mCallbackProxy.onPlaybackInfoChanged(controller, info);
        }

        @Override
        public MediaController.ControllerResult onCustomCommand(MediaController controller,
                SessionCommand command, Bundle args) {
            synchronized (this) {
                if (mOnCustomCommandRunnable != null) {
                    mOnCustomCommandRunnable.run();
                }
            }
            return mCallbackProxy.onCustomCommand(controller, command, args);
        }

        @Override
        public int onSetCustomLayout(MediaController controller, List<CommandButton> layout) {
            return mCallbackProxy.onSetCustomLayout(controller, layout);
        }

        @Override
        public void onAllowedCommandsChanged(MediaController controller,
                SessionCommandGroup commands) {
            mCallbackProxy.onAllowedCommandsChanged(controller, commands);
        }

        @Override
        public void onPlayerStateChanged(MediaController controller, int state) {
            mCallbackProxy.onPlayerStateChanged(controller, state);
        }

        @Override
        public void onSeekCompleted(MediaController controller, long position) {
            mCallbackProxy.onSeekCompleted(controller, position);
        }

        @Override
        public void onPlaybackSpeedChanged(MediaController controller, float speed) {
            mCallbackProxy.onPlaybackSpeedChanged(controller, speed);
        }

        @Override
        public void onBufferingStateChanged(MediaController controller, MediaItem item,
                int state) {
            mCallbackProxy.onBufferingStateChanged(controller, item, state);
        }

        @Override
        public void onCurrentMediaItemChanged(MediaController controller, MediaItem item) {
            mCallbackProxy.onCurrentMediaItemChanged(controller, item);
        }

        @Override
        public void onPlaylistChanged(MediaController controller,
                List<MediaItem> list, MediaMetadata metadata) {
            mCallbackProxy.onPlaylistChanged(controller, list, metadata);
        }

        @Override
        public void onPlaylistMetadataChanged(MediaController controller,
                MediaMetadata metadata) {
            mCallbackProxy.onPlaylistMetadataChanged(controller, metadata);
        }

        @Override
        public void onShuffleModeChanged(MediaController controller, int shuffleMode) {
            mCallbackProxy.onShuffleModeChanged(controller, shuffleMode);
        }

        @Override
        public void onRepeatModeChanged(MediaController controller, int repeatMode) {
            mCallbackProxy.onRepeatModeChanged(controller, repeatMode);
        }

        @Override
        public void onPlaybackCompleted(MediaController controller) {
            mCallbackProxy.onPlaybackCompleted(controller);
        }

        @Override
        public void onSearchResultChanged(MediaBrowser browser, String query, int itemCount,
                LibraryParams params) {
            super.onSearchResultChanged(browser, query, itemCount, params);
            if (mCallbackProxy instanceof BrowserCallback) {
                ((BrowserCallback) mCallbackProxy)
                        .onSearchResultChanged(browser, query, itemCount, params);
            }
        }

        @Override
        public void onChildrenChanged(MediaBrowser browser, String parentId, int itemCount,
                LibraryParams params) {
            super.onChildrenChanged(browser, parentId, itemCount, params);
            if (mCallbackProxy instanceof BrowserCallback) {
                ((BrowserCallback) mCallbackProxy)
                        .onChildrenChanged(browser, parentId, itemCount, params);
            }
        }

        @Override
        public void setRunnableForOnCustomCommand(Runnable runnable) {
            synchronized (this) {
                mOnCustomCommandRunnable = runnable;
            }
        }
    }

    public class TestMediaBrowser extends MediaBrowser implements TestControllerInterface {
        private final BrowserCallback mCallback;

        public TestMediaBrowser(@NonNull Context context, @NonNull SessionToken token,
                @NonNull BrowserCallback callback) {
            super(context, token, sHandlerExecutor, callback);
            mCallback = callback;
        }

        @Override
        public BrowserCallback getCallback() {
            return mCallback;
        }
    }
}

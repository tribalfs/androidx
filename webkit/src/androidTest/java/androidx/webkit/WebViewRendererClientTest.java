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

package androidx.webkit;

import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import androidx.concurrent.futures.ResolvableFuture;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebViewRendererClientTest {
    WebViewOnUiThread mWebViewOnUiThread;

    @Before
    public void setUp() {
        mWebViewOnUiThread = new androidx.webkit.WebViewOnUiThread();
    }

    @After
    public void tearDown() {
        if (mWebViewOnUiThread != null) {
            mWebViewOnUiThread.cleanUp();
        }
    }

    private static class JSBlocker {
        // A CoundDownLatch is used here, instead of a Future, because that makes it
        // easier to support requiring variable numbers of releaseBlock() calls
        // to unblock.
        private CountDownLatch mLatch;
        private ResolvableFuture<Void> mBecameBlocked;
        JSBlocker(int requiredReleaseCount) {
            mLatch = new CountDownLatch(requiredReleaseCount);
            mBecameBlocked = ResolvableFuture.create();
        }

        JSBlocker() {
            this(1);
        }

        public void releaseBlock() {
            mLatch.countDown();
        }

        @JavascriptInterface
        public void block() throws Exception {
            // This blocks indefinitely (until signalled) on a background thread.
            // The actual test timeout is not determined by this wait, but by other
            // code waiting for the onRendererUnresponsive() call.
            mBecameBlocked.set(null);
            mLatch.await();
        }

        public void waitForBlocked() {
            WebkitUtils.waitForFuture(mBecameBlocked);
        }
    }

    private void blockRenderer(final JSBlocker blocker) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(new Runnable() {
            @Override
            public void run() {
                WebView webView = mWebViewOnUiThread.getWebViewOnCurrentThread();
                webView.getSettings().setJavaScriptEnabled(true);
                webView.addJavascriptInterface(blocker, "blocker");
                webView.evaluateJavascript("blocker.block();", null);
                blocker.waitForBlocked();
                // Sending an input event that does not get acknowledged will cause
                // the unresponsive renderer event to fire.
                webView.dispatchKeyEvent(
                        new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER));
            }
        });
    }

    private void testWebViewRendererClientOnExecutor(Executor executor) throws Throwable {
        WebkitUtils.checkFeature(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE);
        final JSBlocker blocker = new JSBlocker();
        final ResolvableFuture<Void> rendererUnblocked = ResolvableFuture.create();

        WebViewRendererClient client = new WebViewRendererClient() {
            @Override
            public void onRendererUnresponsive(WebView view, WebViewRenderer renderer) {
                // Let the renderer unblock.
                blocker.releaseBlock();
            }

            @Override
            public void onRendererResponsive(WebView view, WebViewRenderer renderer) {
                // Notify that the renderer has been unblocked.
                rendererUnblocked.set(null);
            }
        };
        if (executor == null) {
            mWebViewOnUiThread.setWebViewRendererClient(client);
        } else {
            mWebViewOnUiThread.setWebViewRendererClient(executor, client);
        }

        mWebViewOnUiThread.loadUrlAndWaitForCompletion("about:blank");
        blockRenderer(blocker);
        WebkitUtils.waitForFuture(rendererUnblocked);
    }

    @Test
    public void testWebViewRendererClientWithoutExecutor() throws Throwable {
        testWebViewRendererClientOnExecutor(null);
    }

    @Test
    public void testWebViewRendererClientWithExecutor() throws Throwable {
        final AtomicInteger executorCount = new AtomicInteger();
        testWebViewRendererClientOnExecutor(new Executor() {
            @Override
            public void execute(Runnable r) {
                executorCount.incrementAndGet();
                r.run();
            }
        });
        Assert.assertEquals(2, executorCount.get());
    }

    @Test
    public void testSetWebViewRendererClient() throws Throwable {
        WebkitUtils.checkFeature(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE);

        Assert.assertNull("Initially the renderer client should be null",
                mWebViewOnUiThread.getWebViewRendererClient());

        final WebViewRendererClient webViewRendererClient = new WebViewRendererClient() {
            @Override
            public void onRendererUnresponsive(WebView view, WebViewRenderer renderer) {}

            @Override
            public void onRendererResponsive(WebView view, WebViewRenderer renderer) {}
        };
        mWebViewOnUiThread.setWebViewRendererClient(webViewRendererClient);

        Assert.assertSame(
                "After the renderer client is set, getting it should return the same object",
                webViewRendererClient, mWebViewOnUiThread.getWebViewRendererClient());
    }
}

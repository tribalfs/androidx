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

import android.graphics.Bitmap;
import android.net.Uri;
import android.webkit.ValueCallback;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class WebViewClientCompatTest {
    private WebViewOnUiThread mWebViewOnUiThread;

    private static final long TEST_TIMEOUT = 20000L;
    private static final String TEST_URL = "http://www.example.com/";
    private static final String TEST_SAFE_BROWSING_URL =
            "chrome://safe-browsing/match?type=malware";

    @Before
    public void setUp() {
        mWebViewOnUiThread = new WebViewOnUiThread();
    }

    /**
     * This should remain functionally equivalent to
     * android.webkit.cts.WebViewClientTest#testShouldOverrideUrlLoadingDefault. Modifications to
     * this test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    @SdkSuppress(minSdkVersion = 21) // to instantiate WebResourceRequest
    public void testShouldOverrideUrlLoadingDefault() {
        // This never calls into chromium, so we don't need to do any feature checks.

        final MockWebViewClient webViewClient = new MockWebViewClient();

        // Create any valid WebResourceRequest, the return values don't matter much.
        final WebResourceRequest resourceRequest = new WebResourceRequest() {
            @Override
            public Uri getUrl() {
                return Uri.parse(TEST_URL);
            }

            @Override
            public boolean isForMainFrame() {
                return false;
            }

            @Override
            public boolean isRedirect() {
                return false;
            }

            @Override
            public boolean hasGesture() {
                return false;
            }

            @Override
            public String getMethod() {
                return "GET";
            }

            @Override
            public Map<String, String> getRequestHeaders() {
                return new HashMap<>();
            }
        };

        Assert.assertFalse(webViewClient.shouldOverrideUrlLoading(
                mWebViewOnUiThread.getWebViewOnCurrentThread(), resourceRequest));
    }

    /**
     * This should remain functionally equivalent to
     * android.webkit.cts.WebViewClientTest#testShouldOverrideUrlLoading. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testShouldOverrideUrlLoading() throws InterruptedException {
        AssumptionUtils.checkFeature(WebViewFeature.SHOULD_OVERRIDE_WITH_REDIRECTS);
        AssumptionUtils.checkFeature(WebViewFeature.WEB_RESOURCE_REQUEST_IS_REDIRECT);

        String data = "<html><body>"
                + "<a href=\"" + TEST_URL + "\" id=\"link\">new page</a>"
                + "</body></html>";
        mWebViewOnUiThread.loadDataAndWaitForCompletion(data, "text/html", null);
        final CountDownLatch pageFinishedLatch = new CountDownLatch(1);
        final MockWebViewClient webViewClient = new MockWebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                pageFinishedLatch.countDown();
            }
        };
        mWebViewOnUiThread.setWebViewClient(webViewClient);
        mWebViewOnUiThread.getSettings().setJavaScriptEnabled(true);
        clickOnLinkUsingJs("link", mWebViewOnUiThread);
        pageFinishedLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
        Assert.assertEquals(TEST_URL,
                webViewClient.getLastShouldOverrideResourceRequest().getUrl().toString());

        WebResourceRequest request = webViewClient.getLastShouldOverrideResourceRequest();
        Assert.assertNotNull(request);
        Assert.assertTrue(request.isForMainFrame());
        Assert.assertFalse(WebResourceRequestCompat.isRedirect(request));
        Assert.assertFalse(request.hasGesture());
    }

    private void clickOnLinkUsingJs(final String linkId, WebViewOnUiThread webViewOnUiThread)
            throws InterruptedException {
        final CountDownLatch callbackLatch = new CountDownLatch(1);
        ValueCallback<String> callback = new ValueCallback<String>() {
            @Override
            public void onReceiveValue(String value) {
                callbackLatch.countDown();
            }
        };
        webViewOnUiThread.evaluateJavascript(
                "document.getElementById('" + linkId + "').click();"
                        + "console.log('element with id [" + linkId + "] clicked');", callback);
        Assert.assertTrue(callbackLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    /**
     * This should remain functionally equivalent to
     * android.webkit.cts.WebViewClientTest#testOnReceivedError. Modifications to this test should
     * be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testOnReceivedError() throws Exception {
        AssumptionUtils.checkFeature(WebViewFeature.RECEIVE_WEB_RESOURCE_ERROR);
        AssumptionUtils.checkFeature(WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE);

        final MockWebViewClient webViewClient = new MockWebViewClient();
        mWebViewOnUiThread.setWebViewClient(webViewClient);

        String wrongUri = "invalidscheme://some/resource";
        Assert.assertNull(webViewClient.getOnReceivedResourceError());
        mWebViewOnUiThread.loadUrlAndWaitForCompletion(wrongUri);
        Assert.assertNotNull(webViewClient.getOnReceivedResourceError());
        Assert.assertEquals(WebViewClient.ERROR_UNSUPPORTED_SCHEME,
                webViewClient.getOnReceivedResourceError().getErrorCode());
    }

    /**
     * This should remain functionally equivalent to
     * android.webkit.cts.WebViewClientTest#testOnReceivedErrorForSubresource. Modifications to
     * this test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testOnReceivedErrorForSubresource() throws Exception {
        AssumptionUtils.checkFeature(WebViewFeature.RECEIVE_WEB_RESOURCE_ERROR);

        final MockWebViewClient webViewClient = new MockWebViewClient();
        mWebViewOnUiThread.setWebViewClient(webViewClient);

        Assert.assertNull(webViewClient.getOnReceivedResourceError());
        String data = "<html>"
                + "  <body>"
                + "    <img src=\"invalidscheme://some/resource\" />"
                + "  </body>"
                + "</html>";

        mWebViewOnUiThread.loadDataAndWaitForCompletion(data, "text/html", null);
        Assert.assertNotNull(webViewClient.getOnReceivedResourceError());
        Assert.assertEquals(WebViewClient.ERROR_UNSUPPORTED_SCHEME,
                webViewClient.getOnReceivedResourceError().getErrorCode());
    }

    /**
     * This should remain functionally equivalent to
     * android.webkit.cts.WebViewClientTest#testOnSafeBrowsingHitBackToSafety. Modifications to
     * this test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testOnSafeBrowsingHitBackToSafety() throws Throwable {
        AssumptionUtils.checkFeature(WebViewFeature.SAFE_BROWSING_HIT);
        AssumptionUtils.checkFeature(WebViewFeature.SAFE_BROWSING_ENABLE);
        AssumptionUtils.checkFeature(WebViewFeature.SAFE_BROWSING_RESPONSE_BACK_TO_SAFETY);
        AssumptionUtils.checkFeature(WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE);

        final SafeBrowsingBackToSafetyClient backToSafetyWebViewClient =
                new SafeBrowsingBackToSafetyClient();
        mWebViewOnUiThread.setWebViewClient(backToSafetyWebViewClient);
        WebSettingsCompat.setSafeBrowsingEnabled(mWebViewOnUiThread.getSettings(), true);

        // Load any page
        String data = "<html><body>some safe page</body></html>";
        mWebViewOnUiThread.loadDataAndWaitForCompletion(data, "text/html", null);
        final String originalUrl = mWebViewOnUiThread.getUrl();

        enableSafeBrowsingAndLoadUnsafePage(backToSafetyWebViewClient);

        // Back to safety should produce a network error
        Assert.assertNotNull(backToSafetyWebViewClient.getOnReceivedResourceError());
        Assert.assertEquals(WebViewClient.ERROR_UNSAFE_RESOURCE,
                backToSafetyWebViewClient.getOnReceivedResourceError().getErrorCode());

        // Check that we actually navigated backward
        Assert.assertEquals(originalUrl, mWebViewOnUiThread.getUrl());
    }

    /**
     * This should remain functionally equivalent to
     * android.webkit.cts.WebViewClientTest#testOnSafeBrowsingHitProceed. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testOnSafeBrowsingHitProceed() throws Throwable {
        AssumptionUtils.checkFeature(WebViewFeature.SAFE_BROWSING_HIT);
        AssumptionUtils.checkFeature(WebViewFeature.SAFE_BROWSING_ENABLE);
        AssumptionUtils.checkFeature(WebViewFeature.SAFE_BROWSING_RESPONSE_PROCEED);

        final SafeBrowsingProceedClient proceedWebViewClient = new SafeBrowsingProceedClient();
        mWebViewOnUiThread.setWebViewClient(proceedWebViewClient);
        WebSettingsCompat.setSafeBrowsingEnabled(mWebViewOnUiThread.getSettings(), true);

        // Load any page
        String data = "<html><body>some safe page</body></html>";
        mWebViewOnUiThread.loadDataAndWaitForCompletion(data, "text/html", null);

        enableSafeBrowsingAndLoadUnsafePage(proceedWebViewClient);

        // Check that we actually proceeded
        Assert.assertEquals(TEST_SAFE_BROWSING_URL, mWebViewOnUiThread.getUrl());
    }

    private void enableSafeBrowsingAndLoadUnsafePage(SafeBrowsingClient client) throws Throwable {
        // Note: Safe Browsing depends on user opt-in as well, so we can't assume it's actually
        // enabled. #getSafeBrowsingEnabled will tell us the true state of whether Safe Browsing is
        // enabled.
        boolean deviceSupportsSafeBrowsing =
                WebSettingsCompat.getSafeBrowsingEnabled(mWebViewOnUiThread.getSettings());
        final String msg = "The device should support Safe Browsing";
        Assume.assumeTrue(msg, deviceSupportsSafeBrowsing);

        Assert.assertNull(client.getOnReceivedResourceError());
        mWebViewOnUiThread.loadUrlAndWaitForCompletion(TEST_SAFE_BROWSING_URL);

        Assert.assertEquals(TEST_SAFE_BROWSING_URL,
                client.getOnSafeBrowsingHitRequest().getUrl().toString());
        Assert.assertTrue(client.getOnSafeBrowsingHitRequest().isForMainFrame());
    }

    /**
     * This should remain functionally equivalent to
     * android.webkit.cts.WebViewClientTest#testOnPageCommitVisibleCalled. Modifications to this
     * test should be reflected in that test as necessary. See http://go/modifying-webview-cts.
     */
    @Test
    public void testOnPageCommitVisibleCalled() throws Exception {
        AssumptionUtils.checkFeature(WebViewFeature.VISUAL_STATE_CALLBACK);

        final CountDownLatch callbackLatch = new CountDownLatch(1);

        mWebViewOnUiThread.setWebViewClient(new WebViewClientCompat() {
            @Override
            public void onPageCommitVisible(@NonNull WebView view, @NonNull String url) {
                Assert.assertEquals(url, "about:blank");
                callbackLatch.countDown();
            }
        });

        mWebViewOnUiThread.loadUrl("about:blank");
        Assert.assertTrue(callbackLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testResetClientToCompat() throws Exception {
        AssumptionUtils.checkFeature(WebViewFeature.VISUAL_STATE_CALLBACK);

        WebViewClient nonCompatClient = new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap bitmap) {
                Assert.fail("This client should not have callbacks invoked");
            }
        };
        mWebViewOnUiThread.setWebViewClient(nonCompatClient);

        final CountDownLatch callbackLatch = new CountDownLatch(1);
        WebViewClientCompat compatClient = new WebViewClientCompat() {
            @Override
            public void onPageCommitVisible(@NonNull WebView view, @NonNull String url) {
                Assert.assertEquals(url, "about:blank");
                callbackLatch.countDown();
            }
        };
        mWebViewOnUiThread.setWebViewClient(compatClient); // reset to the new client
        mWebViewOnUiThread.loadUrl("about:blank");
        Assert.assertTrue(callbackLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testResetClientToRegular() throws Exception {
        WebViewClientCompat compatClient = new WebViewClientCompat() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap bitmap) {
                Assert.fail("This client should not have callbacks invoked");
            }
        };
        mWebViewOnUiThread.setWebViewClient(compatClient);

        final CountDownLatch callbackLatch = new CountDownLatch(1);
        WebViewClient nonCompatClient = new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                Assert.assertEquals(url, "about:blank");
                callbackLatch.countDown();
            }
        };
        mWebViewOnUiThread.setWebViewClient(nonCompatClient); // reset to the new client
        mWebViewOnUiThread.loadUrl("about:blank");
        Assert.assertTrue(callbackLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS));
    }

    private class MockWebViewClient extends WebViewOnUiThread.WaitForLoadedClient {
        private boolean mOnPageStartedCalled;
        private boolean mOnPageFinishedCalled;
        private boolean mOnLoadResourceCalled;
        private WebResourceErrorCompat mOnReceivedResourceError;
        private WebResourceResponse mOnReceivedHttpError;
        private WebResourceRequest mLastShouldOverrideResourceRequest;

        MockWebViewClient() {
            super(mWebViewOnUiThread);
        }

        public WebResourceErrorCompat getOnReceivedResourceError() {
            return mOnReceivedResourceError;
        }

        public WebResourceResponse getOnReceivedHttpError() {
            return mOnReceivedHttpError;
        }

        public WebResourceRequest getLastShouldOverrideResourceRequest() {
            return mLastShouldOverrideResourceRequest;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            mOnPageStartedCalled = true;
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            Assert.assertTrue(mOnPageStartedCalled);
            Assert.assertTrue(mOnLoadResourceCalled);
            mOnPageFinishedCalled = true;
        }

        @Override
        public void onLoadResource(WebView view, String url) {
            super.onLoadResource(view, url);
            Assert.assertTrue(mOnPageStartedCalled);
            mOnLoadResourceCalled = true;
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onReceivedError(WebView view, int errorCode,
                String description, String failingUrl) {
            // This can be called if a test runs for a WebView which does not support the {@link
            // WebViewFeature#RECEIVE_WEB_RESOURCE_ERROR} feature.
        }

        @Override
        public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request,
                @NonNull WebResourceErrorCompat error) {
            mOnReceivedResourceError = error;
        }

        @Override
        public void onReceivedHttpError(@NonNull WebView view, @NonNull WebResourceRequest request,
                @NonNull WebResourceResponse errorResponse) {
            super.onReceivedHttpError(view, request, errorResponse);
            mOnReceivedHttpError = errorResponse;
        }

        @Override
        @SuppressWarnings("deprecation")
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            // This can be called if a test runs for a WebView which does not support the {@link
            // WebViewFeature#SHOULD_OVERRIDE_WITH_REDIRECTS} feature.
            return false;
        }

        @Override
        public boolean shouldOverrideUrlLoading(@NonNull WebView view,
                @NonNull WebResourceRequest request) {
            mLastShouldOverrideResourceRequest = request;
            return false;
        }
    }

    private class SafeBrowsingClient extends MockWebViewClient {
        private WebResourceRequest mOnSafeBrowsingHitRequest;
        private int mOnSafeBrowsingHitThreatType;

        public WebResourceRequest getOnSafeBrowsingHitRequest() {
            return mOnSafeBrowsingHitRequest;
        }

        public void setOnSafeBrowsingHitRequest(WebResourceRequest request) {
            mOnSafeBrowsingHitRequest = request;
        }

        public int getOnSafeBrowsingHitThreatType() {
            return mOnSafeBrowsingHitThreatType;
        }

        public void setOnSafeBrowsingHitThreatType(int type) {
            mOnSafeBrowsingHitThreatType = type;
        }
    }

    private class SafeBrowsingBackToSafetyClient extends SafeBrowsingClient {
        @Override
        public void onSafeBrowsingHit(@NonNull WebView view, @NonNull WebResourceRequest request,
                int threatType, @NonNull SafeBrowsingResponseCompat response) {
            // Immediately go back to safety to return the network error code
            setOnSafeBrowsingHitRequest(request);
            setOnSafeBrowsingHitThreatType(threatType);
            response.backToSafety(/* report */ true);
        }
    }

    private class SafeBrowsingProceedClient extends SafeBrowsingClient {
        @Override
        public void onSafeBrowsingHit(@NonNull WebView view, @NonNull WebResourceRequest request,
                int threatType, @NonNull SafeBrowsingResponseCompat response) {
            // Proceed through Safe Browsing warnings
            setOnSafeBrowsingHitRequest(request);
            setOnSafeBrowsingHitThreatType(threatType);
            response.proceed(/* report */ true);
        }
    }
}

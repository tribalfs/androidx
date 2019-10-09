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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresFeature;
import androidx.annotation.RestrictTo;
import androidx.annotation.UiThread;
import androidx.webkit.internal.WebMessagePortImpl;
import androidx.webkit.internal.WebViewFeatureInternal;
import androidx.webkit.internal.WebViewGlueCommunicator;
import androidx.webkit.internal.WebViewProviderAdapter;
import androidx.webkit.internal.WebViewProviderFactory;
import androidx.webkit.internal.WebViewRenderProcessClientFrameworkAdapter;
import androidx.webkit.internal.WebViewRenderProcessImpl;

import org.chromium.support_lib_boundary.WebViewProviderBoundaryInterface;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Compatibility version of {@link android.webkit.WebView}
 */
public class WebViewCompat {
    private static final Uri WILDCARD_URI = Uri.parse("*");
    private static final Uri EMPTY_URI = Uri.parse("");

    private WebViewCompat() {} // Don't allow instances of this class to be constructed.

    /**
     * Callback interface supplied to {@link #postVisualStateCallback} for receiving
     * notifications about the visual state.
     */
    public interface VisualStateCallback {
        /**
         * Invoked when the visual state is ready to be drawn in the next {@link WebView#onDraw}.
         *
         * @param requestId The identifier passed to {@link #postVisualStateCallback} when this
         *                  callback was posted.
         */
        @UiThread
        void onComplete(long requestId);
    }

    /**
     * WebMessageListener callback interface. This is used to listen messages from the injected
     * JavaScript object. See also {@link #addWebMessageListener()}.
     *
     * TODO(ctzsm): unhide.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public interface WebMessageListener {
        /**
         * Receives JavaScript {@code postMessage()} call information from the JavaScript object.
         *
         * @param view The {@link WebView} issued this message.
         * @param message The {@link WebMessageCompat} message from JavaScript.
         * @param sourceOrigin The origin of the frame where the message is from.
         * @param isMainFrame If the message is from a main frame.
         * @param replyProxy Used for reply message to the injected JavaScript object.
         */
        @UiThread
        void onPostMessage(@NonNull WebView view, @NonNull WebMessageCompat message,
                @NonNull Uri sourceOrigin, boolean isMainFrame, @NonNull JsReplyProxy replyProxy);
    }

    /**
     * Posts a {@link VisualStateCallback}, which will be called when
     * the current state of the WebView is ready to be drawn.
     *
     * <p>Because updates to the DOM are processed asynchronously, updates to the DOM may not
     * immediately be reflected visually by subsequent {@link WebView#onDraw} invocations. The
     * {@link VisualStateCallback} provides a mechanism to notify the caller when the contents
     * of the DOM at the current time are ready to be drawn the next time the {@link WebView} draws.
     *
     * <p>The next draw after the callback completes is guaranteed to reflect all the updates to the
     * DOM up to the point at which the {@link VisualStateCallback} was posted, but it may
     * also contain updates applied after the callback was posted.
     *
     * <p>The state of the DOM covered by this API includes the following:
     * <ul>
     * <li>primitive HTML elements (div, img, span, etc..)</li>
     * <li>images</li>
     * <li>CSS animations</li>
     * <li>WebGL</li>
     * <li>canvas</li>
     * </ul>
     * It does not include the state of:
     * <ul>
     * <li>the video tag</li>
     * </ul>
     *
     * <p>To guarantee that the {@link WebView} will successfully render the first frame
     * after the {@link VisualStateCallback#onComplete} method has been called a set of
     * conditions must be met:
     * <ul>
     * <li>If the {@link WebView}'s visibility is set to {@link android.view.View#VISIBLE VISIBLE}
     * then * the {@link WebView} must be attached to the view hierarchy.</li>
     * <li>If the {@link WebView}'s visibility is set to
     * {@link android.view.View#INVISIBLE INVISIBLE} then the {@link WebView} must be attached to
     * the view hierarchy and must be made {@link android.view.View#VISIBLE VISIBLE} from the
     * {@link VisualStateCallback#onComplete} method.</li>
     * <li>If the {@link WebView}'s visibility is set to {@link android.view.View#GONE GONE} then
     * the {@link WebView} must be attached to the view hierarchy and its
     * {@link android.widget.AbsoluteLayout.LayoutParams LayoutParams}'s width and height need to be
     * set to fixed values and must be made {@link android.view.View#VISIBLE VISIBLE} from the
     * {@link VisualStateCallback#onComplete} method.</li>
     * </ul>
     *
     * <p>When using this API it is also recommended to enable pre-rasterization if the {@link
     * WebView} is off screen to avoid flickering. See
     * {@link android.webkit.WebSettings#setOffscreenPreRaster} for more details and do consider its
     * caveats.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#VISUAL_STATE_CALLBACK}.
     *
     * @param requestId An id that will be returned in the callback to allow callers to match
     *                  requests with callbacks.
     * @param callback  The callback to be invoked.
     */
    @SuppressWarnings("NewApi")
    @RequiresFeature(name = WebViewFeature.VISUAL_STATE_CALLBACK,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void postVisualStateCallback(@NonNull WebView webview, long requestId,
            @NonNull final VisualStateCallback callback) {
        WebViewFeatureInternal webViewFeature =
                WebViewFeatureInternal.getFeature(WebViewFeature.VISUAL_STATE_CALLBACK);
        if (webViewFeature.isSupportedByFramework()) {
            webview.postVisualStateCallback(requestId,
                    new android.webkit.WebView.VisualStateCallback() {
                        @Override
                        public void onComplete(long l) {
                            callback.onComplete(l);
                        }
                    });
        } else if (webViewFeature.isSupportedByWebView()) {
            checkThread(webview);
            getProvider(webview).insertVisualStateCallback(requestId, callback);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Starts Safe Browsing initialization.
     * <p>
     * URL loads are not guaranteed to be protected by Safe Browsing until after {@code callback} is
     * invoked with {@code true}. Safe Browsing is not fully supported on all devices. For those
     * devices {@code callback} will receive {@code false}.
     * <p>
     * This should not be called if Safe Browsing has been disabled by manifest tag or {@link
     * android.webkit.WebSettings#setSafeBrowsingEnabled}. This prepares resources used for Safe
     * Browsing.
     * <p>
     * This should be called with the Application Context (and will always use the Application
     * context to do its work regardless).
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#START_SAFE_BROWSING}.
     *
     * @param context Application Context.
     * @param callback will be called on the UI thread with {@code true} if initialization is
     * successful, {@code false} otherwise.
     */
    @SuppressLint("NewApi")
    @RequiresFeature(name = WebViewFeature.START_SAFE_BROWSING,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void startSafeBrowsing(@NonNull Context context,
            @Nullable ValueCallback<Boolean> callback) {
        WebViewFeatureInternal webviewFeature =
                WebViewFeatureInternal.getFeature(WebViewFeature.START_SAFE_BROWSING);
        if (webviewFeature.isSupportedByFramework()) {
            WebView.startSafeBrowsing(context, callback);
        } else if (webviewFeature.isSupportedByWebView()) {
            getFactory().getStatics().initSafeBrowsing(context, callback);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Sets the list of hosts (domain names/IP addresses) that are exempt from SafeBrowsing checks.
     * The list is global for all the WebViews.
     * <p>
     * Each rule should take one of these:
     * <table>
     * <tr><th> Rule </th> <th> Example </th> <th> Matches Subdomain</th> </tr>
     * <tr><td> HOSTNAME </td> <td> example.com </td> <td> Yes </td> </tr>
     * <tr><td> .HOSTNAME </td> <td> .example.com </td> <td> No </td> </tr>
     * <tr><td> IPV4_LITERAL </td> <td> 192.168.1.1 </td> <td> No </td></tr>
     * <tr><td> IPV6_LITERAL_WITH_BRACKETS </td><td>[10:20:30:40:50:60:70:80]</td><td>No</td></tr>
     * </table>
     * <p>
     * All other rules, including wildcards, are invalid.
     * <p>
     * The correct syntax for hosts is defined by <a
     * href="https://tools.ietf.org/html/rfc3986#section-3.2.2">RFC 3986</a>.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#SAFE_BROWSING_WHITELIST}.
     *
     * @param hosts the list of hosts
     * @param callback will be called with {@code true} if hosts are successfully added to the
     * whitelist. It will be called with {@code false} if any hosts are malformed. The callback
     * will be run on the UI thread
     */
    @SuppressLint("NewApi")
    @RequiresFeature(name = WebViewFeature.SAFE_BROWSING_WHITELIST,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void setSafeBrowsingWhitelist(@NonNull List<String> hosts,
            @Nullable ValueCallback<Boolean> callback) {
        WebViewFeatureInternal webviewFeature =
                WebViewFeatureInternal.getFeature(WebViewFeature.SAFE_BROWSING_WHITELIST);
        if (webviewFeature.isSupportedByFramework()) {
            WebView.setSafeBrowsingWhitelist(hosts, callback);
        } else if (webviewFeature.isSupportedByWebView()) {
            getFactory().getStatics().setSafeBrowsingWhitelist(hosts, callback);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Returns a URL pointing to the privacy policy for Safe Browsing reporting.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#SAFE_BROWSING_PRIVACY_POLICY_URL}.
     *
     * @return the url pointing to a privacy policy document which can be displayed to users.
     */
    @SuppressLint("NewApi")
    @NonNull
    @RequiresFeature(name = WebViewFeature.SAFE_BROWSING_PRIVACY_POLICY_URL,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static Uri getSafeBrowsingPrivacyPolicyUrl() {
        WebViewFeatureInternal webviewFeature =
                WebViewFeatureInternal.getFeature(WebViewFeature.SAFE_BROWSING_PRIVACY_POLICY_URL);
        if (webviewFeature.isSupportedByFramework()) {
            return WebView.getSafeBrowsingPrivacyPolicyUrl();
        } else if (webviewFeature.isSupportedByWebView()) {
            return getFactory().getStatics().getSafeBrowsingPrivacyPolicyUrl();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * If WebView has already been loaded into the current process this method will return the
     * package that was used to load it. Otherwise, the package that would be used if the WebView
     * was loaded right now will be returned; this does not cause WebView to be loaded, so this
     * information may become outdated at any time.
     * The WebView package changes either when the current WebView package is updated, disabled, or
     * uninstalled. It can also be changed through a Developer Setting.
     * If the WebView package changes, any app process that has loaded WebView will be killed. The
     * next time the app starts and loads WebView it will use the new WebView package instead.
     * @return the current WebView package, or {@code null} if there is none.
     */
    // Note that this API is not protected by a {@link androidx.webkit.WebViewFeature} since
    // this feature is not dependent on the WebView APK.
    @Nullable
    public static PackageInfo getCurrentWebViewPackage(@NonNull Context context) {
        // There was no WebView Package before Lollipop, the WebView code was part of the framework
        // back then.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return WebView.getCurrentWebViewPackage();
        } else { // L-N
            try {
                PackageInfo loadedWebViewPackageInfo = getLoadedWebViewPackageInfo();
                if (loadedWebViewPackageInfo != null) return loadedWebViewPackageInfo;
            } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException
                | NoSuchMethodException  e) {
                return null;
            }

            // If WebViewFactory.getLoadedPackageInfo() returns null then WebView hasn't been loaded
            // yet, in that case we need to fetch the name of the WebView package, and fetch the
            // corresponding PackageInfo through the PackageManager
            return getNotYetLoadedWebViewPackageInfo(context);
        }
    }

    /**
     * Return the PackageInfo of the currently loaded WebView APK. This method uses reflection and
     * propagates any exceptions thrown, to the caller.
     */
    @SuppressLint("PrivateApi")
    private static PackageInfo getLoadedWebViewPackageInfo()
            throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException,
            IllegalAccessException {
        Class<?> webViewFactoryClass = Class.forName("android.webkit.WebViewFactory");
        return (PackageInfo) webViewFactoryClass.getMethod(
                "getLoadedPackageInfo").invoke(null);
    }

    /**
     * Return the PackageInfo of the WebView APK that would have been used as WebView implementation
     * if WebView was to be loaded right now.
     */
    @SuppressLint("PrivateApi")
    private static PackageInfo getNotYetLoadedWebViewPackageInfo(Context context) {
        String webviewPackageName;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                    && Build.VERSION.SDK_INT <= Build.VERSION_CODES.M) {
                Class<?> webViewFactoryClass = Class.forName("android.webkit.WebViewFactory");

                webviewPackageName = (String) webViewFactoryClass.getMethod(
                        "getWebViewPackageName").invoke(null);
            } else {
                Class<?> webviewUpdateServiceClass =
                        Class.forName("android.webkit.WebViewUpdateService");
                webviewPackageName = (String) webviewUpdateServiceClass.getMethod(
                        "getCurrentWebViewPackageName").invoke(null);
            }
        } catch (ClassNotFoundException e) {
            return null;
        } catch (IllegalAccessException e) {
            return null;
        } catch (InvocationTargetException e) {
            return null;
        } catch (NoSuchMethodException e) {
            return null;
        }
        if (webviewPackageName == null) return null;
        PackageManager pm = context.getPackageManager();
        try {
            return pm.getPackageInfo(webviewPackageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    private static WebViewProviderAdapter getProvider(WebView webview) {
        return new WebViewProviderAdapter(createProvider(webview));
    }

    /**
     * Creates a message channel to communicate with JS and returns the message
     * ports that represent the endpoints of this message channel. The HTML5 message
     * channel functionality is described
     * <a href="https://html.spec.whatwg.org/multipage/comms.html#messagechannel">here
     * </a>
     *
     * <p>The returned message channels are entangled and already in started state.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#CREATE_WEB_MESSAGE_CHANNEL}.
     *
     * @return an array of size two, containing the two message ports that form the message channel.
     */
    @SuppressLint("NewApi")
    @RequiresFeature(name = WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static @NonNull WebMessagePortCompat[] createWebMessageChannel(
            @NonNull WebView webview) {
        final WebViewFeatureInternal feature =
                WebViewFeatureInternal.getFeature(WebViewFeature.CREATE_WEB_MESSAGE_CHANNEL);
        if (feature.isSupportedByFramework()) {
            return WebMessagePortImpl.portsToCompat(webview.createWebMessageChannel());
        } else if (feature.isSupportedByWebView()) {
            return getProvider(webview).createWebMessageChannel();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Post a message to main frame. The embedded application can restrict the
     * messages to a certain target origin. See
     * <a href="https://html.spec.whatwg.org/multipage/comms.html#posting-messages">
     * HTML5 spec</a> for how target origin can be used.
     * <p>
     * A target origin can be set as a wildcard ("*"). However this is not recommended.
     * See the page above for security issues.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#POST_WEB_MESSAGE}.
     *
     * @param message the WebMessage
     * @param targetOrigin the target origin.
     */
    @SuppressLint("NewApi")
    @RequiresFeature(name = WebViewFeature.POST_WEB_MESSAGE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void postWebMessage(@NonNull WebView webview, @NonNull WebMessageCompat message,
            @NonNull Uri targetOrigin) {
        // The wildcard ("*") Uri was first supported in WebView 60, see
        // crrev/5ec5b67cbab33cea51b0ee11a286c885c2de4d5d, so on some Android versions using "*"
        // won't work. WebView has always supported using an empty Uri "" as a wildcard - so convert
        // "*" into "" here.
        if (WILDCARD_URI.equals(targetOrigin)) {
            targetOrigin = EMPTY_URI;
        }

        final WebViewFeatureInternal feature =
                WebViewFeatureInternal.getFeature(WebViewFeature.POST_WEB_MESSAGE);
        if (feature.isSupportedByFramework()) {
            webview.postWebMessage(
                    WebMessagePortImpl.compatToFrameworkMessage(message),
                    targetOrigin);
        } else if (feature.isSupportedByWebView()) {
            getProvider(webview).postWebMessage(message, targetOrigin);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Adds a {@link WebMessageListener} to the {@android.webkit.WebView} and inject a JavaScript
     * object that the {@link WebMessageListener} will listener on.
     *
     * <p>
     * The injected JavaScript will be named as {@code jsObjectName}. We will inject the JavaScript
     * object if the frame's origin matches any of the {@code allowedOriginRules} for every
     * navigation after this call, the JavaScript object will be available immediately after the
     * page loads.
     *
     * <p>
     * This method could be called multiple times so multiple JavaScript objects will be injected.
     *
     * <p>
     * Note that this is a powerful API, the JavaScript object will be injected when the frame's
     * origin matches any one of the allowed origins. If a wildcard {@code "*"} is provided, it will
     * inject JavaScript object to all frames. App should try to void to use the wildcard as much as
     * possible, instead, passing rules that only matches trusted URLs is highly recommended.
     *
     * @param jsObjectName The name for the injected JavaScript object for this {@link
     *         WebMessageListener}.
     * @param allowedOriginRules A list of matching rules for the allowed origins.
     * @param listener The {@link WebMessageListener} to be called when received onPostMessage().
     *
     * @throws IllegalArgumentException If one of the {@code allowedOriginRules} is invalid.
     *
     * //TODO(ctzsm): unhide
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @RequiresFeature(name = WebViewFeature.WEB_MESSAGE_LISTENER,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void addWebMessageListener(@NonNull WebView webview, @NonNull String jsObjectName,
            @NonNull List<String> allowedOriginRules, @NonNull WebMessageListener listener) {
        final WebViewFeatureInternal feature =
                WebViewFeatureInternal.getFeature(WebViewFeature.WEB_MESSAGE_LISTENER);
        if (feature.isSupportedByWebView()) {
            getProvider(webview).addWebMessageListener(
                    jsObjectName, allowedOriginRules.toArray(new String[0]), listener);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Removes the {@link WebMessageListener} associated with the {@code jsObjectName}.
     *
     * <p>
     * Note that after this call, the injected JavaScript object is still in the JavaScript context,
     * however any message send after this call won't reach to the {@link WebMessageListener}.
     *
     * @param jsObjectName The JavaScript object's name that passed to {@link
     *         #addWebMessageListener()} previously.
     *
     * //TODO(ctzsm): unhide
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @RequiresFeature(name = WebViewFeature.WEB_MESSAGE_LISTENER,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void removeWebMessageListener(
            @NonNull WebView webview, @NonNull String jsObjectName) {
        final WebViewFeatureInternal feature =
                WebViewFeatureInternal.getFeature(WebViewFeature.WEB_MESSAGE_LISTENER);
        if (feature.isSupportedByWebView()) {
            getProvider(webview).removeWebMessageListener(jsObjectName);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Gets the WebViewClient for the WebView argument.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#GET_WEB_VIEW_CLIENT}.
     *
     * @return the WebViewClient, or a default client if not yet set
     */
    @SuppressLint("NewApi")
    @RequiresFeature(name = WebViewFeature.GET_WEB_VIEW_CLIENT,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static @NonNull WebViewClient getWebViewClient(@NonNull WebView webview) {
        final WebViewFeatureInternal feature =
                WebViewFeatureInternal.getFeature(WebViewFeature.GET_WEB_VIEW_CLIENT);
        if (feature.isSupportedByFramework()) {
            return webview.getWebViewClient();
        } else if (feature.isSupportedByWebView()) {
            return getProvider(webview).getWebViewClient();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Gets the WebChromeClient.
     *
     * <p>
     * This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#GET_WEB_CHROME_CLIENT}.
     *
     * @return the WebChromeClient, or {@code null} if not yet set
     */
    @SuppressLint("NewApi")
    @RequiresFeature(name = WebViewFeature.GET_WEB_CHROME_CLIENT,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static @Nullable WebChromeClient getWebChromeClient(@NonNull WebView webview) {
        final WebViewFeatureInternal feature =
                WebViewFeatureInternal.getFeature(WebViewFeature.GET_WEB_CHROME_CLIENT);
        if (feature.isSupportedByFramework()) {
            return webview.getWebChromeClient();
        } else if (feature.isSupportedByWebView()) {
            return getProvider(webview).getWebChromeClient();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Gets the WebView renderer associated with this WebView.
     *
     * <p>In Android O and above, WebView may run in "multiprocess"
     * mode. In multiprocess mode, rendering of web content is performed by
     * a sandboxed renderer process separate to the application process.
     * This renderer process may be shared with other WebViews in the
     * application, but is not shared with other application processes.
     *
     * <p>If WebView is running in multiprocess mode, this method returns a
     * handle to the renderer process associated with the WebView, which can
     * be used to control the renderer process.
     *
     * <p>This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#GET_WEB_VIEW_RENDERER}.
     *
     * @return the {@link WebViewRenderProcess} renderer handle associated
     *         with this {@link android.webkit.WebView}, or {@code null} if
     *         WebView is not runing in multiprocess mode.
     */
    @SuppressLint("NewApi")
    @RequiresFeature(name = WebViewFeature.GET_WEB_VIEW_RENDERER,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static @Nullable WebViewRenderProcess getWebViewRenderProcess(@NonNull WebView webview) {
        final WebViewFeatureInternal feature =
                WebViewFeatureInternal.getFeature(WebViewFeature.GET_WEB_VIEW_RENDERER);
        if (feature.isSupportedByFramework()) {
            android.webkit.WebViewRenderProcess renderer = webview.getWebViewRenderProcess();
            return renderer != null ? WebViewRenderProcessImpl.forFrameworkObject(renderer) : null;
        } else if (feature.isSupportedByWebView()) {
            return getProvider(webview).getWebViewRenderProcess();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Sets the renderer client object associated with this WebView.
     *
     * <p>The renderer client encapsulates callbacks relevant to WebView renderer
     * state. See {@link WebViewRenderProcessClient} for details.
     *
     * <p>Although many WebView instances may share a single underlying renderer, and renderers may
     * live either in the application process, or in a sandboxed process that is isolated from
     * the application process, instances of {@link WebViewRenderProcessClient} are set per-WebView.
     * Callbacks represent renderer events from the perspective of this WebView, and may or may
     * not be correlated with renderer events affecting other WebViews.
     *
     * <p>The renderer client encapsulates callbacks relevant to WebView renderer
     * state. See {@link WebViewRenderProcessClient} for details.
     *
     * <p>Although many WebView instances may share a single underlying renderer, and renderers may
     * live either in the application process, or in a sandboxed process that is isolated from
     * the application process, instances of {@link WebViewRenderProcessClient} are set per-WebView.
     * Callbacks represent renderer events from the perspective of this WebView, and may or may
     * not be correlated with renderer events affecting other WebViews.
     *
     * <p>This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE}.
     *
     * @param webview the {@link WebView} on which to monitor responsiveness.
     * @param executor the {@link Executor} that will be used to execute callbacks.
     * @param webViewRenderProcessClient the {@link WebViewRenderProcessClient} to set for
     *                                   callbacks.
     */
    // WebViewRenderProcessClient is a callback class, so it should be last. See
    // https://issuetracker.google.com/issues/139770271.
    @SuppressLint("LambdaLast")
    @RequiresFeature(name = WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void setWebViewRenderProcessClient(
            @NonNull WebView webview,
            @NonNull /* @CallbackExecutor */ Executor executor,
            @NonNull WebViewRenderProcessClient webViewRenderProcessClient) {
        final WebViewFeatureInternal feature = WebViewFeatureInternal.getFeature(
                WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE);
        if (feature.isSupportedByFramework()) {
            webview.setWebViewRenderProcessClient(executor, webViewRenderProcessClient != null
                    ? new WebViewRenderProcessClientFrameworkAdapter(webViewRenderProcessClient)
                    : null);
        } else if (feature.isSupportedByWebView()) {
            getProvider(webview).setWebViewRenderProcessClient(
                    executor, webViewRenderProcessClient);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Sets the renderer client object associated with this WebView.
     *
     * <p>See {@link WebViewCompat#setWebViewRenderProcessClient(WebView,Executor,WebViewRenderProcessClient)} for
     * details, with the following differences:
     *
     * <p>Callbacks will execute directly on the thread on which this WebView was instantiated.
     *
     * <p>Passing {@code null} for {@code webViewRenderProcessClient} will clear the renderer client
     * object for this WebView.
     *
     * <p>This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE}.
     *
     * @param webview the {@link WebView} on which to monitor responsiveness.
     * @param webViewRenderProcessClient the {@link WebViewRenderProcessClient} to set for
     *                                   callbacks.
     */
    @RequiresFeature(name = WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static void setWebViewRenderProcessClient(
            @NonNull WebView webview,
            @Nullable WebViewRenderProcessClient webViewRenderProcessClient) {
        final WebViewFeatureInternal feature = WebViewFeatureInternal.getFeature(
                WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE);
        if (feature.isSupportedByFramework()) {
            webview.setWebViewRenderProcessClient(webViewRenderProcessClient != null
                    ? new WebViewRenderProcessClientFrameworkAdapter(webViewRenderProcessClient)
                    : null);
        } else if (feature.isSupportedByWebView()) {
            getProvider(webview).setWebViewRenderProcessClient(null, webViewRenderProcessClient);
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Gets the renderer client object associated with this WebView.
     *
     * <p>This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE}.
     *
     * @return the {@link WebViewRenderProcessClient} object associated with this WebView, if
     * one has been set via
     * {@link #setWebViewRenderProcessClient(WebView,WebViewRenderProcessClient)} or {@code null}
     * otherwise.
     */
    @RequiresFeature(name = WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static @Nullable WebViewRenderProcessClient getWebViewRenderProcessClient(
            @NonNull WebView webview) {
        final WebViewFeatureInternal feature = WebViewFeatureInternal.getFeature(
                WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE);
        if (feature.isSupportedByFramework()) {
            android.webkit.WebViewRenderProcessClient renderer =
                    webview.getWebViewRenderProcessClient();
            if (renderer == null
                    || !(renderer instanceof WebViewRenderProcessClientFrameworkAdapter)) {
                return null;
            }
            return ((WebViewRenderProcessClientFrameworkAdapter) renderer)
                .getFrameworkRenderProcessClient();
        } else if (feature.isSupportedByWebView()) {
            return getProvider(webview).getWebViewRenderProcessClient();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    /**
     * Returns true if {@link WebView} is running in multi process mode.
     *
     * //TODO(laisminchillo): unhide
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    @RequiresFeature(name = WebViewFeature.MULTI_PROCESS_QUERY,
            enforcement = "androidx.webkit.WebViewFeature#isFeatureSupported")
    public static boolean isMultiProcessEnabled() {
        final WebViewFeatureInternal feature = WebViewFeatureInternal.getFeature(
                WebViewFeature.MULTI_PROCESS_QUERY);
        if (feature.isSupportedByWebView()) {
            return getFactory().getStatics().isMultiProcessEnabled();
        } else {
            throw WebViewFeatureInternal.getUnsupportedOperationException();
        }
    }

    private static WebViewProviderFactory getFactory() {
        return WebViewGlueCommunicator.getFactory();
    }

    private static WebViewProviderBoundaryInterface createProvider(WebView webview) {
        return getFactory().createWebView(webview);
    }

    @SuppressWarnings({"JavaReflectionMemberAccess", "PrivateApi"})
    private static void checkThread(WebView webview) {
        if (Build.VERSION.SDK_INT >= 28) {
            if (webview.getWebViewLooper() != Looper.myLooper()) {
                throw new RuntimeException("A WebView method was called on thread '"
                        + Thread.currentThread().getName() + "'. "
                        + "All WebView methods must be called on the same thread. "
                        + "(Expected Looper " + webview.getWebViewLooper() + " called on "
                        + Looper.myLooper() + ", FYI main Looper is " + Looper.getMainLooper()
                        + ")");
            }
        } else {
            try {
                Method checkThreadMethod = WebView.class.getDeclaredMethod("checkThread");
                checkThreadMethod.setAccessible(true);
                // WebView.checkThread() performs some logging and potentially throws an exception
                // if WebView is used on the wrong thread.
                checkThreadMethod.invoke(webview);
            } catch (NoSuchMethodException e) {
                throw new RuntimeException(e);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            } catch (InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }
}

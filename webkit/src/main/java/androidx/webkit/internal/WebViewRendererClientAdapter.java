/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.webkit.internal;

import android.webkit.WebView;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.webkit.WebViewRenderer;
import androidx.webkit.WebViewRendererClient;

import org.chromium.support_lib_boundary.WebViewRendererClientBoundaryInterface;
import org.chromium.support_lib_boundary.util.Features;

import java.lang.reflect.InvocationHandler;
import java.util.concurrent.Executor;

class WebViewRendererClientAdapter implements WebViewRendererClientBoundaryInterface {
    private static final String[] sSupportedFeatures = new String[] {
            Features.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE,
    };

    private final Executor mExecutor;
    private final WebViewRendererClient mWebViewRendererClient;

    WebViewRendererClientAdapter(Executor executor, WebViewRendererClient webViewRendererClient) {
        mExecutor = executor;
        mWebViewRendererClient = webViewRendererClient;
    }

    WebViewRendererClient getWebViewRendererClient() {
        return mWebViewRendererClient;
    }

    /**
     * Returns the list of features this client supports. This feature list should always be a
     * subset of the Features declared in WebViewFeature.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @Override
    public final String[] getSupportedFeatures() {
        return sSupportedFeatures;
    }

    /**
     * Invoked by chromium with arguments that need to be wrapped by support library adapter
     * objects.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @Override
    public final void onRendererUnresponsive(
            @NonNull final WebView view,
            @NonNull /* WebViewRenderer */ final InvocationHandler renderer) {
        final WebViewRenderer rendererObject = WebViewRendererImpl.forInvocationHandler(renderer);
        final WebViewRendererClient client = mWebViewRendererClient;
        if (mExecutor == null) {
            client.onRendererUnresponsive(view, rendererObject);
        } else {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    client.onRendererUnresponsive(view, rendererObject);
                }
            });
        }
    }

    /**
     * Invoked by chromium with arguments that need to be wrapped by support library adapter
     * objects.
     *
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
    @Override
    public final void onRendererResponsive(
            @NonNull final WebView view,
            @NonNull /* WebViewRenderer */ InvocationHandler renderer) {
        final WebViewRenderer rendererObject = WebViewRendererImpl.forInvocationHandler(renderer);
        final WebViewRendererClient client = mWebViewRendererClient;
        if (mExecutor == null) {
            client.onRendererResponsive(view, rendererObject);
        } else {
            mExecutor.execute(new Runnable() {
                @Override
                public void run() {
                    client.onRendererResponsive(view, rendererObject);
                }
            });
        }
    }
}

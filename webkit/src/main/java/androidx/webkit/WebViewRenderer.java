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

import androidx.annotation.RestrictTo;

/**
 * WebViewRenderer provides an opaque handle to a WebView renderer.
 */
public abstract class WebViewRenderer {
    /**
     * Cause this renderer to terminate.
     *
     * <p>Calling this on a not yet started, or an already terminated renderer will have no effect.
     *
     * <p>Terminating a renderer process may have an effect on multiple
     * {@link android.webkit.WebView} instances.
     *
     * <p>Renderer termination must be handled by properly overriding
     * {@link android.webkit.WebViewClient#onRenderProcessGone} for every WebView that shares this
     * renderer. If termination is not handled by all associated WebViews, then the application
     * process will also be terminated.
     *
     * <p>This method should only be called if
     * {@link WebViewFeature#isFeatureSupported(String)}
     * returns true for {@link WebViewFeature#WEB_VIEW_RENDERER_TERMINATE}.
     *
     * @return {@code true} if it was possible to terminate this renderer, {@code false} otherwise.
     */
    public abstract boolean terminate();

    /**
     * This class cannot be created by applications.
     * @hide
     */
    @RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    public WebViewRenderer() {
    }
}

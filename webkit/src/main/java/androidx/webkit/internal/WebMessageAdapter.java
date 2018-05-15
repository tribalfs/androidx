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

package androidx.webkit.internal;

import androidx.webkit.WebMessageCompat;
import androidx.webkit.WebMessagePortCompat;

import org.chromium.support_lib_boundary.WebMessageBoundaryInterface;

import java.lang.reflect.InvocationHandler;

/**
 * Adapter between {@link WebMessageCompat} and
 * {@link org.chromium.support_lib_boundary.WebMessageBoundaryInterface}.
 * This class is used to pass a PostMessage from the app to Chromium.
 */
public class WebMessageAdapter implements WebMessageBoundaryInterface {
    private WebMessageCompat mWebMessageCompat;

    WebMessageAdapter(WebMessageCompat webMessage) {
        this.mWebMessageCompat = webMessage;
    }

    @Override
    public String getData() {
        return mWebMessageCompat.getData();
    }

    @Override
    public InvocationHandler[] getPorts() {
        WebMessagePortCompat[] ports = mWebMessageCompat.getPorts();
        if (ports == null) return null;

        InvocationHandler[] invocationHandlers = new InvocationHandler[ports.length];
        for (int n = 0; n < ports.length; n++) {
            invocationHandlers[n] = ports[n].getInvocationHandler();
        }
        return invocationHandlers;
    }

    @Override
    public String[] getSupportedFeatures() {
        // getData() and getPorts() are not covered by feature flags.
        return new String[0];
    }

    // ====================================================================================
    // Methods related to converting a WebMessageBoundaryInterface into a WebMessageCompat.
    // ====================================================================================

    /**
     * Utility method used to convert PostMessages from the Chromium side to
     * {@link WebMessageCompat} objects - a class apps recognize.
     */
    public static WebMessageCompat webMessageCompatFromBoundaryInterface(
            WebMessageBoundaryInterface boundaryInterface) {
        return new WebMessageCompat(boundaryInterface.getData(),
                toWebMessagePortCompats(boundaryInterface.getPorts()));
    }

    private static WebMessagePortCompat[] toWebMessagePortCompats(InvocationHandler[] ports) {
        WebMessagePortCompat[] compatPorts = new WebMessagePortCompat[ports.length];
        for (int n = 0; n < ports.length; n++) {
            compatPorts[n] = new WebMessagePortImpl(ports[n]);
        }
        return compatPorts;
    }
}

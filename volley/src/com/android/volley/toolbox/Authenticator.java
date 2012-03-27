/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.volley.toolbox;

import com.android.volley.AuthFailureError;

/**
 * An interface for interacting with auth tokens.
 *
 * Authenticators can support multiple auth types by implementing {@link #getAuthToken(String)}.
 */
public interface Authenticator {
    /**
     * Synchronously retrieves an auth token with default type.
     *
     * @throws AuthFailureError If authentication did not succeed
     */
    public String getAuthToken() throws AuthFailureError;

    /**
     * Synchronously retrieves an auth token of the given type.
     *
     * If not supported, {@link UnsupportedOperationException} should be thrown.
     * @throws AuthFailureError If authentication did not succeed
     *
     * @deprecated
     */
    public String getAuthToken(String authTokenType) throws AuthFailureError;

    /**
     * Invalidates the provided auth token.
     */
    public void invalidateAuthToken(String authToken);
}

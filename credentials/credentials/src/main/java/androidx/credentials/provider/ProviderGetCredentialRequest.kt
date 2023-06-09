/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.credentials.provider

import android.app.PendingIntent
import androidx.credentials.CredentialOption

/**
 * Request received by the provider after the query phase of the get flow is complete i.e. the user
 * was presented with a list of credentials, and the user has now made a selection from the list of
 * [CredentialEntry] presented on the selector UI.
 *
 * This request will be added to the intent extras of the activity invoked by the [PendingIntent]
 * set on the [CredentialEntry] that the user selected. The request
 * must be extracted using the [PendingIntentHandler.retrieveProviderGetCredentialRequest] helper
 * API.
 *
 * @constructor constructs an instance of [ProviderGetCredentialRequest]
 *
 * @param credentialOptions the list of credential retrieval options containing the
 * required parameters, expected  to contain a single [CredentialOption] when this
 * request is retrieved from the [android.app.Activity] invoked by the [android.app.PendingIntent]
 * set on a [PasswordCredentialEntry] or a [PublicKeyCredentialEntry], or expected to contain
 * multiple [CredentialOption] when this request is retrieved
 * from the [android.app.Activity] invoked by the [android.app.PendingIntent]
 * set on a [RemoteEntry]
 * @param callingAppInfo information pertaining to the calling application
 *
 * Note : Credential providers are not expected to utilize the constructor in this class for any
 * production flow. This constructor must only be used for testing purposes.
 */
class ProviderGetCredentialRequest constructor(
    val credentialOptions: List<CredentialOption>,
    val callingAppInfo: CallingAppInfo
) {
    internal companion object {
        @JvmStatic
        internal fun createFrom(
            options: List<CredentialOption>,
            callingAppInfo: CallingAppInfo
        ): ProviderGetCredentialRequest {
            return ProviderGetCredentialRequest(
                options,
                callingAppInfo)
        }
    }
}

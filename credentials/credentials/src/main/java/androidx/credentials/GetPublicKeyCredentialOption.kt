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

package androidx.credentials

import android.content.ComponentName
import android.os.Bundle
import androidx.credentials.internal.FrameworkClassParsingException

/**
 * A request to get passkeys from the user's public key credential provider.
 *
 * @property requestJson the request in JSON format in the standard webauthn web json
 * shown [here](https://w3c.github.io/webauthn/#dictdef-publickeycredentialrequestoptionsjson).
 * @property clientDataHash a clientDataHash value to sign over in place of assembling and hashing
 * clientDataJSON during the signature request; meaningful only if you have set the
 * [GetCredentialRequest.origin]
 * @param requestJson the request in JSON format in the standard webauthn web json
 * shown [here](https://w3c.github.io/webauthn/#dictdef-publickeycredentialrequestoptionsjson).
 * @param clientDataHash a clientDataHash value to sign over in place of assembling and hashing
 * clientDataJSON during the signature request; set only if you have set the
 * [GetCredentialRequest.origin]
 * @param allowedProviders a set of provider service [ComponentName] allowed to receive this
 * option (Note: a [SecurityException] will be thrown if it is set as non-empty but your app does
 * not have android.permission.CREDENTIAL_MANAGER_SET_ALLOWED_PROVIDERS; for API level < 34,
 * this property will not take effect and you should control the allowed provider via
 * [library dependencies](https://developer.android.com/training/sign-in/passkeys#add-dependencies))
 * @throws NullPointerException If [requestJson] is null
 * @throws IllegalArgumentException If [requestJson] is empty
 */
class GetPublicKeyCredentialOption @JvmOverloads constructor(
    val requestJson: String,
    val clientDataHash: ByteArray? = null,
    allowedProviders: Set<ComponentName> = emptySet(),
) : CredentialOption(
    type = PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL,
    requestData = toRequestDataBundle(requestJson, clientDataHash),
    candidateQueryData = toRequestDataBundle(requestJson, clientDataHash),
    isSystemProviderRequired = false,
    isAutoSelectAllowed = true,
    allowedProviders,
) {
    init {
        require(requestJson.isNotEmpty()) { "requestJson must not be empty" }
    }

    internal companion object {
        internal const val BUNDLE_KEY_CLIENT_DATA_HASH =
            "androidx.credentials.BUNDLE_KEY_CLIENT_DATA_HASH"
        internal const val BUNDLE_KEY_REQUEST_JSON = "androidx.credentials.BUNDLE_KEY_REQUEST_JSON"
        internal const val BUNDLE_VALUE_SUBTYPE_GET_PUBLIC_KEY_CREDENTIAL_OPTION =
            "androidx.credentials.BUNDLE_VALUE_SUBTYPE_GET_PUBLIC_KEY_CREDENTIAL_OPTION"

        @JvmStatic
        internal fun toRequestDataBundle(
            requestJson: String,
            clientDataHash: ByteArray?,
        ): Bundle {
            val bundle = Bundle()
            bundle.putString(
                PublicKeyCredential.BUNDLE_KEY_SUBTYPE,
                BUNDLE_VALUE_SUBTYPE_GET_PUBLIC_KEY_CREDENTIAL_OPTION
            )
            bundle.putString(BUNDLE_KEY_REQUEST_JSON, requestJson)
            bundle.putByteArray(BUNDLE_KEY_CLIENT_DATA_HASH, clientDataHash)
            return bundle
        }

        @Suppress("deprecation") // bundle.get() used for boolean value to prevent default
                                         // boolean value from being returned.
        @JvmStatic
        internal fun createFrom(
            data: Bundle,
            allowedProviders: Set<ComponentName>,
        ): GetPublicKeyCredentialOption {
            try {
                val requestJson = data.getString(BUNDLE_KEY_REQUEST_JSON)
                val clientDataHash = data.getByteArray(BUNDLE_KEY_CLIENT_DATA_HASH)
                return GetPublicKeyCredentialOption(
                    requestJson!!,
                    clientDataHash,
                    allowedProviders
                )
            } catch (e: Exception) {
                throw FrameworkClassParsingException()
            }
        }
    }
}

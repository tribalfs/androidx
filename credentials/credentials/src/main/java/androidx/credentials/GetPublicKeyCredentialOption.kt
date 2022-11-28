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

import android.os.Bundle
import androidx.annotation.VisibleForTesting
import androidx.credentials.internal.FrameworkClassParsingException

/**
 * A request to get passkeys from the user's public key credential provider.
 *
 * @property requestJson the privileged request in JSON format in the standard webauthn web json
 * shown [here](https://w3c.github.io/webauthn/#dictdef-publickeycredentialrequestoptionsjson).
 * @property allowHybrid defines whether hybrid credentials are allowed to fulfill this request,
 * true by default, with hybrid credentials defined
 * [here](https://w3c.github.io/webauthn/#dom-authenticatortransport-hybrid)
 * @throws NullPointerException If [requestJson] is null
 * @throws IllegalArgumentException If [requestJson] is empty
 */
class GetPublicKeyCredentialOption @JvmOverloads constructor(
    val requestJson: String,
    @get:JvmName("allowHybrid")
    val allowHybrid: Boolean = true,
) : GetCredentialOption(
    PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL,
    toBundle(requestJson, allowHybrid),
    false
) {
    init {
        require(requestJson.isNotEmpty()) { "requestJson must not be empty" }
    }

    /** @hide */
    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val BUNDLE_KEY_ALLOW_HYBRID = "androidx.credentials.BUNDLE_KEY_ALLOW_HYBRID"
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val BUNDLE_KEY_REQUEST_JSON = "androidx.credentials.BUNDLE_KEY_REQUEST_JSON"
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        const val BUNDLE_VALUE_SUBTYPE_GET_PUBLIC_KEY_CREDENTIAL_OPTION =
            "androidx.credentials.BUNDLE_VALUE_SUBTYPE_GET_PUBLIC_KEY_CREDENTIAL_OPTION"

        @JvmStatic
        internal fun toBundle(requestJson: String, allowHybrid: Boolean): Bundle {
            val bundle = Bundle()
            bundle.putString(
                PublicKeyCredential.BUNDLE_KEY_SUBTYPE,
                BUNDLE_VALUE_SUBTYPE_GET_PUBLIC_KEY_CREDENTIAL_OPTION
            )
            bundle.putString(BUNDLE_KEY_REQUEST_JSON, requestJson)
            bundle.putBoolean(BUNDLE_KEY_ALLOW_HYBRID, allowHybrid)
            return bundle
        }

        @Suppress("deprecation") // bundle.get() used for boolean value to prevent default
                                         // boolean value from being returned.
        @JvmStatic
        internal fun createFrom(data: Bundle): GetPublicKeyCredentialOption {
            try {
                val requestJson = data.getString(BUNDLE_KEY_REQUEST_JSON)
                val allowHybrid = data.get(BUNDLE_KEY_ALLOW_HYBRID)
                return GetPublicKeyCredentialOption(requestJson!!, (allowHybrid!!) as Boolean)
            } catch (e: Exception) {
                throw FrameworkClassParsingException()
            }
        }
    }
}
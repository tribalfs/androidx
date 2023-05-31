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
import androidx.annotation.RequiresApi
import androidx.credentials.PublicKeyCredential.Companion.BUNDLE_KEY_SUBTYPE
import androidx.credentials.internal.FrameworkClassParsingException
import org.json.JSONObject

/**
 * A request to register a passkey from the user's public key credential provider.
 *
 * @param requestJson the request in JSON format in the [standard webauthn web json](https://w3c.github.io/webauthn/#dictdef-publickeycredentialcreationoptionsjson).
 * @param clientDataHash a hash that is used to verify the origin
 * @param preferImmediatelyAvailableCredentials true if you prefer the operation to return
 * immediately when there is no available passkey registration offering instead of falling back to
 * discovering remote options, and false (default) otherwise
 * @param origin the origin of a different application if the request is being made on behalf of
 * that application. For API level >=34, setting a non-null value for this parameter, will throw
 * a SecurityException if android.permission.CREDENTIAL_MANAGER_SET_ORIGIN is not present.
 */
class CreatePublicKeyCredentialRequest private constructor(
    val requestJson: String,
    val clientDataHash: String?,
    @get:JvmName("preferImmediatelyAvailableCredentials")
    val preferImmediatelyAvailableCredentials: Boolean,
    displayInfo: DisplayInfo,
    origin: String? = null,
) : CreateCredentialRequest(
    type = PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL,
    credentialData = toCredentialDataBundle(requestJson, clientDataHash,
        preferImmediatelyAvailableCredentials),
    // The whole request data should be passed during the query phase.
    candidateQueryData = toCredentialDataBundle(requestJson, clientDataHash,
        preferImmediatelyAvailableCredentials),
    isSystemProviderRequired = false,
    isAutoSelectAllowed = false,
    displayInfo,
    origin
) {

    /**
     * Constructs a [CreatePublicKeyCredentialRequest] to register a passkey from the user's public key credential provider.
     *
     * @param requestJson the privileged request in JSON format in the [standard webauthn web json](https://w3c.github.io/webauthn/#dictdef-publickeycredentialcreationoptionsjson).
     * @param clientDataHash a hash that is used to verify the relying party identity
     * @param preferImmediatelyAvailableCredentials true if you prefer the operation to return
     * immediately when there is no available passkey registration offering instead of falling back to
     * discovering remote options, and false (default) otherwise
     * @param origin the origin of a different application if the request is being made on behalf of
     * that application. For API level >=34, setting a non-null value for this parameter, will throw
     * a SecurityException if android.permission.CREDENTIAL_MANAGER_SET_ORIGIN is not present.
     * @throws NullPointerException If [requestJson] is null
     * @throws IllegalArgumentException If [requestJson] is empty, or if it doesn't have a valid
     * `user.name` defined according to the [webauthn spec](https://w3c.github.io/webauthn/#dictdef-publickeycredentialcreationoptionsjson)
     */
    @JvmOverloads constructor(
        requestJson: String,
        clientDataHash: String? = null,
        preferImmediatelyAvailableCredentials: Boolean = false,
        origin: String? = null
    ) : this(requestJson, clientDataHash, preferImmediatelyAvailableCredentials,
        getRequestDisplayInfo(requestJson), origin)

    init {
        require(requestJson.isNotEmpty()) { "requestJson must not be empty" }
    }

    internal companion object {
        internal const val BUNDLE_KEY_PREFER_IMMEDIATELY_AVAILABLE_CREDENTIALS =
            "androidx.credentials.BUNDLE_KEY_PREFER_IMMEDIATELY_AVAILABLE_CREDENTIALS"
        internal const val BUNDLE_KEY_CLIENT_DATA_HASH =
            "androidx.credentials.BUNDLE_KEY_CLIENT_DATA_HASH"
        internal const val BUNDLE_KEY_REQUEST_JSON = "androidx.credentials.BUNDLE_KEY_REQUEST_JSON"
        internal const val BUNDLE_VALUE_SUBTYPE_CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST =
            "androidx.credentials.BUNDLE_VALUE_SUBTYPE_CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST"

        @JvmStatic
        internal fun getRequestDisplayInfo(requestJson: String): DisplayInfo {
            return try {
                val json = JSONObject(requestJson)
                val user = json.getJSONObject("user")
                val userName = user.getString("name")
                val displayName: String? =
                    if (user.isNull("displayName")) null else user.getString("displayName")
                DisplayInfo(userName, displayName)
            } catch (e: Exception) {
                throw IllegalArgumentException("user.name must be defined in requestJson")
            }
        }

        @JvmStatic
        internal fun toCredentialDataBundle(
            requestJson: String,
            clientDataHash: String? = null,
            preferImmediatelyAvailableCredentials: Boolean
        ): Bundle {
            val bundle = Bundle()
            bundle.putString(
                BUNDLE_KEY_SUBTYPE,
                BUNDLE_VALUE_SUBTYPE_CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST
            )
            bundle.putString(BUNDLE_KEY_REQUEST_JSON, requestJson)
            bundle.putString(BUNDLE_KEY_CLIENT_DATA_HASH, clientDataHash)
            bundle.putBoolean(
                BUNDLE_KEY_PREFER_IMMEDIATELY_AVAILABLE_CREDENTIALS,
                preferImmediatelyAvailableCredentials
            )
            return bundle
        }

        @JvmStatic
        internal fun toCandidateDataBundle(
            requestJson: String,
            clientDataHash: String?,
            preferImmediatelyAvailableCredentials: Boolean
        ): Bundle {
            val bundle = Bundle()
            bundle.putString(
                BUNDLE_KEY_SUBTYPE,
                BUNDLE_VALUE_SUBTYPE_CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST
            )
            bundle.putString(BUNDLE_KEY_REQUEST_JSON, requestJson)
            bundle.putString(BUNDLE_KEY_CLIENT_DATA_HASH, clientDataHash)
            bundle.putBoolean(
                BUNDLE_KEY_PREFER_IMMEDIATELY_AVAILABLE_CREDENTIALS,
                preferImmediatelyAvailableCredentials
            )
            return bundle
        }

        @Suppress("deprecation") // bundle.get() used for boolean value
        // to prevent default boolean value from being returned.
        @JvmStatic
        @RequiresApi(23)
        internal fun createFrom(data: Bundle, origin: String? = null):
            CreatePublicKeyCredentialRequest {
            try {
                val requestJson = data.getString(BUNDLE_KEY_REQUEST_JSON)
                val clientDataHash = data.getString(BUNDLE_KEY_CLIENT_DATA_HASH)
                val preferImmediatelyAvailableCredentials =
                    data.get(BUNDLE_KEY_PREFER_IMMEDIATELY_AVAILABLE_CREDENTIALS)
                val displayInfo = DisplayInfo.parseFromCredentialDataBundle(data)
                return if (displayInfo == null) CreatePublicKeyCredentialRequest(
                    requestJson!!,
                    clientDataHash,
                    (preferImmediatelyAvailableCredentials!!) as Boolean,
                    origin
                ) else CreatePublicKeyCredentialRequest(
                    requestJson!!,
                    clientDataHash,
                    (preferImmediatelyAvailableCredentials!!) as Boolean,
                    displayInfo,
                    origin
                )
            } catch (e: Exception) {
                throw FrameworkClassParsingException()
            }
        }
    }
}

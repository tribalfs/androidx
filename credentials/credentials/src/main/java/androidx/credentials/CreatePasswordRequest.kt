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
import androidx.annotation.VisibleForTesting
import androidx.credentials.internal.FrameworkClassParsingException

/**
 * A request to save the user password credential with their password provider.
 *
 * @property id the user id associated with the password
 * @property password the password
 * @param id the user id associated with the password
 * @param password the password
 * @param origin the origin of a different application if the request is being made on behalf of
 * that application (Note: for API level >=34, setting a non-null value for this parameter will
 * throw a SecurityException if android.permission.CREDENTIAL_MANAGER_SET_ORIGIN is not present)
 * @param preferImmediatelyAvailableCredentials true if you prefer the operation to return
 * immediately when there is no available credential creation offering instead of falling back to
 * discovering remote options, and false (default) otherwise
 */
class CreatePasswordRequest private constructor(
    val id: String,
    val password: String,
    displayInfo: DisplayInfo,
    origin: String? = null,
    preferImmediatelyAvailableCredentials: Boolean,
) : CreateCredentialRequest(
    type = PasswordCredential.TYPE_PASSWORD_CREDENTIAL,
    credentialData = toCredentialDataBundle(id, password),
    candidateQueryData = toCandidateDataBundle(),
    isSystemProviderRequired = false,
    isAutoSelectAllowed = false,
    displayInfo,
    origin,
    preferImmediatelyAvailableCredentials,
) {

    /**
     * Constructs a [CreatePasswordRequest] to save the user password credential with their
     * password provider.
     *
     * @param id the user id associated with the password
     * @param password the password
     * @param origin the origin of a different application if the request is being made on behalf of
     * that application (Note: for API level >=34, setting a non-null value for this parameter will
     * throw a SecurityException if android.permission.CREDENTIAL_MANAGER_SET_ORIGIN is not present)
     * @param preferImmediatelyAvailableCredentials true if you prefer the operation to return
     * immediately when there is no available password saving option instead of falling back
     * to discovering remote options, and false (default) otherwise
     * @throws NullPointerException If [id] is null
     * @throws NullPointerException If [password] is null
     * @throws IllegalArgumentException If [password] is empty
     * @throws SecurityException if [origin] is set but
     * android.permission.CREDENTIAL_MANAGER_SET_ORIGIN is not present
     */
    @JvmOverloads constructor(
        id: String,
        password: String,
        origin: String? = null,
        preferImmediatelyAvailableCredentials: Boolean = false,
    ) : this(id, password, DisplayInfo(id, null), origin, preferImmediatelyAvailableCredentials)

    /**
     * Constructs a [CreatePasswordRequest] to save the user password credential with their
     * password provider.
     *
     * @param id the user id associated with the password
     * @param password the password
     * @param origin the origin of a different application if the request is being made on behalf of
     * that application (Note: for API level >=34, setting a non-null value for this parameter will
     * throw a SecurityException if android.permission.CREDENTIAL_MANAGER_SET_ORIGIN is not present)
     * @param preferDefaultProvider the preferred default provider component name to prioritize in
     * the selection UI flows (Note: your app must have the permission
     * android.permission.CREDENTIAL_MANAGER_SET_ALLOWED_PROVIDERS to specify this, or it
     * would not take effect; also this bit may not take effect for Android API level 33 and below,
     * depending on the pre-34 provider(s) you have chosen)
     * @param preferImmediatelyAvailableCredentials true if you prefer the operation to return
     * immediately when there is no available passkey registration offering instead of falling back
     * to discovering remote options, and false (preferably) otherwise
     * @throws NullPointerException If [id] is null
     * @throws NullPointerException If [password] is null
     * @throws IllegalArgumentException If [password] is empty
     * @throws SecurityException if [origin] is set but
     * android.permission.CREDENTIAL_MANAGER_SET_ORIGIN is not present
     */
    constructor(
        id: String,
        password: String,
        origin: String?,
        preferDefaultProvider: String?,
        preferImmediatelyAvailableCredentials: Boolean,
    ) : this(
        id, password, DisplayInfo(
            userId = id,
            userDisplayName = null,
            preferDefaultProvider = preferDefaultProvider,
        ), origin, preferImmediatelyAvailableCredentials,
    )

    init {
        require(password.isNotEmpty()) { "password should not be empty" }
    }

    /** @hide */
    companion object {
        internal const val BUNDLE_KEY_ID = "androidx.credentials.BUNDLE_KEY_ID"
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        const val BUNDLE_KEY_PASSWORD = "androidx.credentials.BUNDLE_KEY_PASSWORD"

        @JvmStatic
        internal fun toCredentialDataBundle(id: String, password: String): Bundle {
            val bundle = Bundle()
            bundle.putString(BUNDLE_KEY_ID, id)
            bundle.putString(BUNDLE_KEY_PASSWORD, password)
            return bundle
        }

        // No credential data should be sent during the query phase.
        @JvmStatic
        internal fun toCandidateDataBundle(): Bundle {
            return Bundle()
        }

        @JvmStatic
        @RequiresApi(23)
        internal fun createFrom(data: Bundle, origin: String? = null): CreatePasswordRequest {
            try {
                val id = data.getString(BUNDLE_KEY_ID)
                val password = data.getString(BUNDLE_KEY_PASSWORD)
                val displayInfo = DisplayInfo.parseFromCredentialDataBundle(data)
                val preferImmediatelyAvailableCredentials =
                    data.getBoolean(BUNDLE_KEY_PREFER_IMMEDIATELY_AVAILABLE_CREDENTIALS, false)
                return if (displayInfo == null) CreatePasswordRequest(
                    id = id!!,
                    password = password!!,
                    origin = origin,
                    preferImmediatelyAvailableCredentials = preferImmediatelyAvailableCredentials,
                ) else CreatePasswordRequest(
                    id = id!!,
                    password = password!!,
                    displayInfo = displayInfo,
                    origin = origin,
                    preferImmediatelyAvailableCredentials = preferImmediatelyAvailableCredentials,
                )
            } catch (e: Exception) {
                throw FrameworkClassParsingException()
            }
        }
    }
}
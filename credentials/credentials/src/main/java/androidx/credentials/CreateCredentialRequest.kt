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

import android.graphics.drawable.Icon
import android.os.Bundle
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.annotation.VisibleForTesting
import androidx.credentials.CreatePublicKeyCredentialRequestPrivileged.Companion.BUNDLE_VALUE_SUBTYPE_CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST_PRIV
import androidx.credentials.PublicKeyCredential.Companion.BUNDLE_KEY_SUBTYPE
import androidx.credentials.internal.FrameworkClassParsingException

/**
 * Base request class for registering a credential.
 *
 * An application can construct a subtype request and call [CredentialManager.createCredential] to
 * launch framework UI flows to collect consent and any other metadata needed from the user to
 * register a new user credential.
 */
abstract class CreateCredentialRequest internal constructor(
    /** @hide */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    open val type: String,
    /** @hide */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    open val credentialData: Bundle,
    /** @hide */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    open val candidateQueryData: Bundle,
    /** @hide */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    open val isSystemProviderRequired: Boolean,
    /** @hide */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    open val isAutoSelectAllowed: Boolean,
    /** @hide */
    val displayInfo: DisplayInfo,
) {

    init {
        @Suppress("UNNECESSARY_SAFE_CALL")
        credentialData?.let {
            credentialData.putBoolean(BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED, isAutoSelectAllowed)
        }
    }

    /**
     * Information that may be used for display purposes when rendering UIs to collect the user
     * consent and choice.
     *
     * @property userId the user identifier of the created credential
     * @property userDisplayName an optional display name in addition to the [userId] that may be
     * displayed next to the `userId` during the user consent to help your user better understand
     * the credential being created.
     */
    class DisplayInfo internal /** @hide */ constructor(
        val userId: CharSequence,
        val userDisplayName: CharSequence?,
        /** @hide */
        val credentialTypeIcon: Icon?
    ) {

        /**
         * Constructs a [DisplayInfo].
         *
         * @param userId the user id of the created credential
         * @param userDisplayName an optional display name in addition to the [userId] that may be
         * displayed next to the `userId` during the user consent to help your user better
         * understand the credential being created.
         * @throws IllegalArgumentException If [userId] is empty
         */
        @JvmOverloads constructor(
            userId: CharSequence,
            userDisplayName: CharSequence? = null,
        ) : this(
            userId,
            userDisplayName,
            null
        )

        init {
            require(userId.isNotEmpty()) { "userId should not be empty" }
        }

        /** @hide */
        @RequiresApi(23)
        fun toBundle(): Bundle {
            val bundle = Bundle()
            bundle.putCharSequence(BUNDLE_KEY_USER_ID, userId)
            if (!TextUtils.isEmpty(userDisplayName)) {
                bundle.putCharSequence(BUNDLE_KEY_USER_DISPLAY_NAME, userDisplayName)
            }
            // Today the type icon is determined solely within this library right before the
            // request is passed into the framework. Later if needed a new API can be added for
            // custom SDKs to supply their own credential type icons.
            return bundle
        }

        /** @hide */
        companion object {
            /** @hide */
            const val BUNDLE_KEY_REQUEST_DISPLAY_INFO =
                "androidx.credentials.BUNDLE_KEY_REQUEST_DISPLAY_INFO"

            @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
            /** @hide */
            const val BUNDLE_KEY_USER_ID =
                "androidx.credentials.BUNDLE_KEY_USER_ID"

            @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
            /** @hide */
            const val BUNDLE_KEY_USER_DISPLAY_NAME =
                "androidx.credentials.BUNDLE_KEY_USER_DISPLAY_NAME"

            /** @hide */
            const val BUNDLE_KEY_CREDENTIAL_TYPE_ICON =
                "androidx.credentials.BUNDLE_KEY_CREDENTIAL_TYPE_ICON"

            /**
             * Returns a RequestDisplayInfo from a `credentialData` Bundle, or otherwise `null` if
             * parsing fails.
             *
             * @hide
             */
            @JvmStatic
            @RequiresApi(23)
            @Suppress("DEPRECATION") // bundle.getParcelable(key)
            fun parseFromCredentialDataBundle(from: Bundle): DisplayInfo? {
                return try {
                    val displayInfoBundle = from.getBundle(BUNDLE_KEY_REQUEST_DISPLAY_INFO)!!
                    val userId = displayInfoBundle.getCharSequence(BUNDLE_KEY_USER_ID)
                    val displayName =
                        displayInfoBundle.getCharSequence(BUNDLE_KEY_USER_DISPLAY_NAME)
                    val icon: Icon? =
                        displayInfoBundle.getParcelable(BUNDLE_KEY_CREDENTIAL_TYPE_ICON)
                    DisplayInfo(userId!!, displayName, icon)
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    /** @hide */
    companion object {
        /** @hide */
        const val BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED =
            "androidx.credentials.BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED"

        /**
         * Attempts to parse the raw data into one of [CreatePasswordRequest],
         * [CreatePublicKeyCredentialRequest], [CreatePublicKeyCredentialRequestPrivileged], and
         * [CreateCustomCredentialRequest]. Otherwise returns null.
         *
         * @hide
         */
        @JvmStatic
        @RequiresApi(23)
        fun createFrom(
            type: String,
            credentialData: Bundle,
            candidateQueryData: Bundle,
            requireSystemProvider: Boolean
        ): CreateCredentialRequest? {
            return try {
                when (type) {
                    PasswordCredential.TYPE_PASSWORD_CREDENTIAL ->
                        CreatePasswordRequest.createFrom(credentialData)

                    PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL ->
                        when (credentialData.getString(BUNDLE_KEY_SUBTYPE)) {
                            CreatePublicKeyCredentialRequest
                                .BUNDLE_VALUE_SUBTYPE_CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST ->
                                CreatePublicKeyCredentialRequest.createFrom(credentialData)

                            BUNDLE_VALUE_SUBTYPE_CREATE_PUBLIC_KEY_CREDENTIAL_REQUEST_PRIV ->
                                CreatePublicKeyCredentialRequestPrivileged
                                    .createFrom(credentialData)

                            else -> throw FrameworkClassParsingException()
                        }

                    else -> throw FrameworkClassParsingException()
                }
            } catch (e: FrameworkClassParsingException) {
                // Parsing failed but don't crash the process. Instead just output a request with
                // the raw framework values.
                CreateCustomCredentialRequest(
                    type,
                    credentialData,
                    candidateQueryData,
                    requireSystemProvider,
                    DisplayInfo.parseFromCredentialDataBundle(
                        credentialData
                    ) ?: return null,
                    credentialData.getBoolean(BUNDLE_KEY_IS_AUTO_SELECT_ALLOWED, false)
                )
            }
        }
    }
}

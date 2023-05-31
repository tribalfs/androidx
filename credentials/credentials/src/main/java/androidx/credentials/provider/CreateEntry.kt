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

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.slice.Slice
import android.app.slice.SliceSpec
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.credentials.CredentialManager
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import java.time.Instant
import java.util.Collections

/**
 * An entry to be shown on the selector during a create flow initiated when an app calls
 * [CredentialManager.createCredential]
 *
 * A [CreateEntry] points to a location such as an account, or a group where the credential can be
 * registered. When user selects this entry, the corresponding [PendingIntent] is fired, and the
 * credential creation can be completed.
 *
 * @throws IllegalArgumentException If [accountName] is empty
 */
@RequiresApi(28)
class CreateEntry internal constructor(
    val accountName: CharSequence,
    val pendingIntent: PendingIntent,
    val icon: Icon?,
    val description: CharSequence?,
    val lastUsedTime: Instant?,
    private val credentialCountInformationMap: MutableMap<String, Int?>,
    val isAutoSelectAllowed: Boolean
) {

    /**
     * Creates an entry to be displayed on the selector during create flows.
     *
     * @param accountName the name of the account where the credential will be saved
     * @param pendingIntent the [PendingIntent] that will get invoked when user selects this entry
     * @param description the localized description shown on UI about where the credential is stored
     * @param icon the icon to be displayed with this entry on the UI
     * @param lastUsedTime the last time the account underlying this entry was used by the user.
     * Note that this value will only be distinguishable up to the milli second mark. If two
     * entries have the same millisecond precision, they will be considered to have been
     * used at the same time
     * @param passwordCredentialCount the no. of password credentials saved by the provider
     * @param publicKeyCredentialCount the no. of public key credentials saved by the provider
     * @param totalCredentialCount the total no. of credentials saved by the provider
     * @param isAutoSelectAllowed whether the entry should be auto selected if it is the only
     * entry on the selector
     *
     * @throws IllegalArgumentException If [accountName] is empty, or if [description] is longer
     * than 300 characters (important: make sure your descriptions across all locales are within
     * this limit)
     * @throws NullPointerException If [accountName] or [pendingIntent] is null
     */
    constructor(
        accountName: CharSequence,
        pendingIntent: PendingIntent,
        description: CharSequence? = null,
        lastUsedTime: Instant? = null,
        icon: Icon? = null,
        @Suppress("AutoBoxing")
        passwordCredentialCount: Int? = null,
        @Suppress("AutoBoxing")
        publicKeyCredentialCount: Int? = null,
        @Suppress("AutoBoxing")
        totalCredentialCount: Int? = null,
        isAutoSelectAllowed: Boolean = false
    ) : this(
        accountName,
        pendingIntent,
        icon,
        description,
        lastUsedTime,
        mutableMapOf(
            PasswordCredential.TYPE_PASSWORD_CREDENTIAL to passwordCredentialCount,
            PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL to publicKeyCredentialCount,
            TYPE_TOTAL_CREDENTIAL to totalCredentialCount
        ),
        isAutoSelectAllowed
    )

    init {
        require(accountName.isNotEmpty()) { "accountName must not be empty" }
        if (description != null) {
            require(description.length <= DESCRIPTION_MAX_CHAR_LIMIT) {
                "Description must follow a limit of 300 characters."
            }
        }
    }

    /** Returns the no. of password type credentials that the provider with this entry has. */
    @Suppress("AutoBoxing")
    fun getPasswordCredentialCount(): Int? {
        return credentialCountInformationMap[PasswordCredential.TYPE_PASSWORD_CREDENTIAL]
    }

    /** Returns the no. of public key type credentials that the provider with this entry has. */
    @Suppress("AutoBoxing")
    fun getPublicKeyCredentialCount(): Int? {
        return credentialCountInformationMap[PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL]
    }

    /** Returns the no. of total credentials that the provider with this entry has.
     *
     * This total count is not necessarily equal to the sum of [getPasswordCredentialCount]
     * and [getPublicKeyCredentialCount].
     *
     */
    @Suppress("AutoBoxing")
    fun getTotalCredentialCount(): Int? {
        return credentialCountInformationMap[TYPE_TOTAL_CREDENTIAL]
    }

    /**
     * A builder for [CreateEntry]
     *
     * @param accountName the name of the account where the credential will be registered
     * @param pendingIntent the [PendingIntent] that will be fired when the user selects
     * this entry
     */
    class Builder constructor(
        private val accountName: CharSequence,
        private val pendingIntent: PendingIntent
    ) {

        private var credentialCountInformationMap: MutableMap<String, Int?> =
            mutableMapOf()
        private var icon: Icon? = null
        private var description: CharSequence? = null
        private var lastUsedTime: Instant? = null
        private var passwordCredentialCount: Int? = null
        private var publicKeyCredentialCount: Int? = null
        private var totalCredentialCount: Int? = null
        private var autoSelectAllowed: Boolean = false

        /**
         * Sets whether the entry should be auto-selected.
         * The value is false by default.
         */
        @Suppress("MissingGetterMatchingBuilder")
        fun setAutoSelectAllowed(autoSelectAllowed: Boolean): Builder {
            this.autoSelectAllowed = autoSelectAllowed
            return this
        }

        /** Sets the password credential count, denoting how many credentials of type
         * [PasswordCredential.TYPE_PASSWORD_CREDENTIAL] does the provider have stored.
         *
         * This information will be displayed on the [CreateEntry] to help the user
         * make a choice.
         */
        fun setPasswordCredentialCount(count: Int): Builder {
            passwordCredentialCount = count
            credentialCountInformationMap[PasswordCredential.TYPE_PASSWORD_CREDENTIAL] = count
            return this
        }

        /** Sets the password credential count, denoting how many credentials of type
         * [PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL] does the provider have stored.
         *
         * This information will be displayed on the [CreateEntry] to help the user
         * make a choice.
         */
        fun setPublicKeyCredentialCount(count: Int): Builder {
            publicKeyCredentialCount = count
            credentialCountInformationMap[PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL] = count
            return this
        }

        /** Sets the total credential count, denoting how many credentials in total
         * does the provider have stored.
         *
         * This total count no. does not need to be a total of the counts set through
         * [setPasswordCredentialCount] and [setPublicKeyCredentialCount].
         *
         * This information will be displayed on the [CreateEntry] to help the user
         * make a choice.
         */
        fun setTotalCredentialCount(count: Int): Builder {
            totalCredentialCount = count
            credentialCountInformationMap[TYPE_TOTAL_CREDENTIAL] = count
            return this
        }

        /** Sets an icon to be displayed with the entry on the UI */
        fun setIcon(icon: Icon?): Builder {
            this.icon = icon
            return this
        }

        /**
         * Sets a localized description to be displayed on the UI at the time of credential
         * creation.
         *
         * Typically this description should contain information informing the user of the
         * credential being created, and where it is being stored. Providers are free
         * to phrase this however they see fit.
         *
         * @throws IllegalArgumentException if [description] is longer than 300 characters (
         * important: make sure your descriptions across all locales are within this limit).
         */
        fun setDescription(description: CharSequence?): Builder {
            if (description?.length != null && description.length > DESCRIPTION_MAX_CHAR_LIMIT) {
                throw IllegalArgumentException("Description must follow a limit of 300 characters.")
            }
            this.description = description
            return this
        }

        /** Sets the last time this account was used */
        fun setLastUsedTime(lastUsedTime: Instant?): Builder {
            this.lastUsedTime = lastUsedTime
            return this
        }

        /**
         * Builds an instance of [CreateEntry]
         *
         * @throws IllegalArgumentException If [accountName] is empty
         */
        fun build(): CreateEntry {
            return CreateEntry(
                accountName, pendingIntent, icon, description, lastUsedTime,
                credentialCountInformationMap, autoSelectAllowed
            )
        }
    }

    /** @hide **/
    @Suppress("AcronymName")
    companion object {
        private const val TAG = "CreateEntry"
        private const val DESCRIPTION_MAX_CHAR_LIMIT = 300

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val TYPE_TOTAL_CREDENTIAL = "TOTAL_CREDENTIAL_COUNT_TYPE"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val SLICE_HINT_ACCOUNT_NAME =
            "androidx.credentials.provider.createEntry.SLICE_HINT_USER_PROVIDER_ACCOUNT_NAME"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val SLICE_HINT_NOTE =
            "androidx.credentials.provider.createEntry.SLICE_HINT_NOTE"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val SLICE_HINT_ICON =
            "androidx.credentials.provider.createEntry.SLICE_HINT_PROFILE_ICON"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val SLICE_HINT_CREDENTIAL_COUNT_INFORMATION =
            "androidx.credentials.provider.createEntry.SLICE_HINT_CREDENTIAL_COUNT_INFORMATION"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val SLICE_HINT_LAST_USED_TIME_MILLIS =
            "androidx.credentials.provider.createEntry.SLICE_HINT_LAST_USED_TIME_MILLIS"

        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val SLICE_HINT_PENDING_INTENT =
            "androidx.credentials.provider.createEntry.SLICE_HINT_PENDING_INTENT"

        private const val SLICE_HINT_AUTO_SELECT_ALLOWED =
            "androidx.credentials.provider.createEntry.SLICE_HINT_AUTO_SELECT_ALLOWED"

        private const val AUTO_SELECT_TRUE_STRING = "true"

        private const val AUTO_SELECT_FALSE_STRING = "false"

        /** @hide **/
        @JvmStatic
        fun toSlice(
            createEntry: CreateEntry
        ): Slice {
            val accountName = createEntry.accountName
            val icon = createEntry.icon
            val description = createEntry.description
            val lastUsedTime = createEntry.lastUsedTime
            val credentialCountInformationMap = createEntry.credentialCountInformationMap
            val pendingIntent = createEntry.pendingIntent

            // TODO("Use the right type and revision")
            val sliceBuilder = Slice.Builder(Uri.EMPTY, SliceSpec("type", 1))

            val autoSelectAllowed = if (createEntry.isAutoSelectAllowed) {
                AUTO_SELECT_TRUE_STRING
            } else {
                AUTO_SELECT_FALSE_STRING
            }

            sliceBuilder.addText(
                accountName, /*subType=*/null,
                listOf(SLICE_HINT_ACCOUNT_NAME)
            )
            if (lastUsedTime != null) {
                sliceBuilder.addLong(
                    lastUsedTime.toEpochMilli(), /*subType=*/null, listOf(
                        SLICE_HINT_LAST_USED_TIME_MILLIS
                    )
                )
            }
            if (description != null) {
                sliceBuilder.addText(
                    description, null,
                    listOf(SLICE_HINT_NOTE)
                )
            }
            if (icon != null) {
                sliceBuilder.addIcon(
                    icon, /*subType=*/null,
                    listOf(SLICE_HINT_ICON)
                )
            }
            val credentialCountBundle = convertCredentialCountInfoToBundle(
                credentialCountInformationMap
            )
            if (credentialCountBundle != null) {
                sliceBuilder.addBundle(
                    convertCredentialCountInfoToBundle(
                        credentialCountInformationMap
                    ), null, listOf(
                        SLICE_HINT_CREDENTIAL_COUNT_INFORMATION
                    )
                )
            }
            sliceBuilder.addAction(
                pendingIntent,
                Slice.Builder(sliceBuilder)
                    .addHints(Collections.singletonList(SLICE_HINT_PENDING_INTENT))
                    .build(),
                /*subType=*/null
            ).addText(
                autoSelectAllowed, /*subType=*/null,
                listOf(SLICE_HINT_AUTO_SELECT_ALLOWED)
            )
            return sliceBuilder.build()
        }

        /**
         * Returns an instance of [CreateEntry] derived from a [Slice] object.
         *
         * @param slice the [Slice] object constructed through [toSlice]
         *
         * @hide
         */
        @RequiresApi(28)
        @SuppressLint("WrongConstant") // custom conversion between jetpack and framework
        @JvmStatic
        fun fromSlice(slice: Slice): CreateEntry? {
            // TODO("Put the right spec and version value")
            var accountName: CharSequence? = null
            var icon: Icon? = null
            var pendingIntent: PendingIntent? = null
            var credentialCountInfo: MutableMap<String, Int?> = mutableMapOf()
            var description: CharSequence? = null
            var lastUsedTime: Instant? = null
            var autoSelectAllowed = false
            slice.items.forEach {
                if (it.hasHint(SLICE_HINT_ACCOUNT_NAME)) {
                    accountName = it.text
                } else if (it.hasHint(SLICE_HINT_ICON)) {
                    icon = it.icon
                } else if (it.hasHint(SLICE_HINT_PENDING_INTENT)) {
                    pendingIntent = it.action
                } else if (it.hasHint(SLICE_HINT_CREDENTIAL_COUNT_INFORMATION)) {
                    credentialCountInfo = convertBundleToCredentialCountInfo(it.bundle)
                        as MutableMap<String, Int?>
                } else if (it.hasHint(SLICE_HINT_LAST_USED_TIME_MILLIS)) {
                    lastUsedTime = Instant.ofEpochMilli(it.long)
                } else if (it.hasHint(SLICE_HINT_NOTE)) {
                    description = it.text
                } else if (it.hasHint(SLICE_HINT_LAST_USED_TIME_MILLIS)) {
                    lastUsedTime = Instant.ofEpochMilli(it.long)
                } else if (it.hasHint(SLICE_HINT_AUTO_SELECT_ALLOWED)) {
                    val autoSelectValue = it.text
                    if (autoSelectValue == AUTO_SELECT_TRUE_STRING) {
                        autoSelectAllowed = true
                    }
                }
            }
            return try {
                CreateEntry(
                    accountName!!, pendingIntent!!, icon, description,
                    lastUsedTime, credentialCountInfo, autoSelectAllowed
                )
            } catch (e: Exception) {
                Log.i(TAG, "fromSlice failed with: " + e.message)
                null
            }
        }

        /** @hide **/
        @JvmStatic
        internal fun convertBundleToCredentialCountInfo(bundle: Bundle?):
            Map<String, Int?> {
            val credentialCountMap = HashMap<String, Int?>()
            if (bundle == null) {
                return credentialCountMap
            }
            bundle.keySet().forEach {
                try {
                    credentialCountMap[it] = bundle.getInt(it)
                } catch (e: Exception) {
                    Log.i(TAG, "Issue unpacking credential count info bundle: " + e.message)
                }
            }
            return credentialCountMap
        }

        /** @hide **/
        @JvmStatic
        internal fun convertCredentialCountInfoToBundle(
            credentialCountInformationMap: Map<String, Int?>
        ): Bundle? {
            var foundCredentialCount = false
            val bundle = Bundle()
            credentialCountInformationMap.forEach {
                if (it.value != null) {
                    bundle.putInt(it.key, it.value!!)
                    foundCredentialCount = true
                }
            }
            if (!foundCredentialCount) {
                return null
            }
            return bundle
        }
    }
}

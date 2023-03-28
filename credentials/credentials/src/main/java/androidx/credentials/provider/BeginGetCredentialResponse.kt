/*
 * Copyright 2023 The Android Open Source Project
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

/**
 * Response from a credential provider to [BeginGetCredentialRequest], containing credential
 * entries and other associated data to be shown on the account selector UI.
 *
 * Credential providers can set multiple [CredentialEntry] per [BeginGetCredentialOption]
 * retrieved from the top level request [BeginGetCredentialRequest]. These entries will appear
 * to the user on the selector.
 *
 * Additionally credential providers can add a list of [AuthenticationAction] if all
 * credentials for the credential provider are locked. Providers can also set a list of
 * [Action] that can navigate the user straight to a provider activity where the rest of
 * the request can be processed.
 */
class BeginGetCredentialResponse constructor(
    val credentialEntries: List<CredentialEntry> = listOf(),
    val actions: List<Action> = listOf(),
    val authenticationActions: List<AuthenticationAction> = listOf(),
    val remoteEntry: RemoteEntry? = null
) {
    /** Builder for [BeginGetCredentialResponse]. **/
    class Builder {
        private var credentialEntries: MutableList<CredentialEntry> = mutableListOf()
        private var actions: MutableList<Action> = mutableListOf()
        private var authenticationActions: MutableList<AuthenticationAction> = mutableListOf()
        private var remoteEntry: RemoteEntry? = null

        /**
         * Sets a remote credential entry to be shown on the UI. Provider must set this if they
         * wish to get the credential from a different device.
         *
         * When constructing the [CredentialEntry] object, the pending intent
         * must be set such that it leads to an activity that can provide UI to fulfill the request
         * on a remote device. When user selects this [remoteEntry], the system will
         * invoke the pending intent set on the [CredentialEntry].
         *
         * <p> Once the remote credential flow is complete, the [android.app.Activity]
         * result should be set to [android.app.Activity#RESULT_OK] and an extra with the
         * [CredentialProviderService#EXTRA_GET_CREDENTIAL_RESPONSE] key should be populated
         * with a [android.credentials.Credential] object.
         *
         * <p> Note that as a provider service you will only be able to set a remote entry if :
         * - Provider service possesses the
         * [android.Manifest.permission.PROVIDE_REMOTE_CREDENTIALS] permission.
         * - Provider service is configured as the provider that can provide remote entries.
         *
         * If the above conditions are not met, setting back [BeginGetCredentialResponse]
         * on the callback from [CredentialProviderService#onBeginGetCredential] will
         * throw a [SecurityException].
         */
        fun setRemoteEntry(remoteEntry: RemoteEntry?): Builder {
            this.remoteEntry = remoteEntry
            return this
        }

        /**
         * Adds a [CredentialEntry] to the list of entries to be displayed on the UI.
         */
        fun addCredentialEntry(entry: CredentialEntry): Builder {
            credentialEntries.add(entry)
            return this
        }

        /**
         * Sets the list of credential entries to be displayed on the account selector UI.
         */
        fun setCredentialEntries(entries: List<CredentialEntry>): Builder {
            credentialEntries = entries.toMutableList()
            return this
        }

        /**
         * Adds an [Action] to the list of actions to be displayed on
         * the UI.
         *
         * <p> An [Action] must be used for independent user actions,
         * such as opening the app, intenting directly into a certain app activity etc. The
         * pending intent set with the [action] must invoke the corresponding activity.
         */
        fun addAction(action: Action): Builder {
            this.actions.add(action)
            return this
        }

        /**
         * Sets the list of actions to be displayed on the UI.
         */
        fun setActions(actions: List<Action>): Builder {
            this.actions = actions.toMutableList()
            return this
        }

        /**
         * Add an authentication entry to be shown on the UI. Providers must set this entry if
         * the corresponding account is locked and no underlying credentials can be returned.
         *
         * <p> When the user selects this [authenticationAction], the system invokes the
         * corresponding pending intent.
         * Once the authentication action activity is launched, and the user is authenticated,
         * providers should create another response with [BeginGetCredentialResponse] using
         * this time adding the unlocked credentials in the form of [CredentialEntry]'s.
         *
         * <p>The new response object must be set on the authentication activity's
         * result. The result code should be set to [android.app.Activity#RESULT_OK] and
         * the [CredentialProviderService#EXTRA_BEGIN_GET_CREDENTIAL_RESPONSE] extra
         * should be set with the new fully populated [BeginGetCredentialResponse] object.
         */
        fun addAuthenticationAction(authenticationAction: AuthenticationAction): Builder {
            this.authenticationActions.add(authenticationAction)
            return this
        }

        /**
         * Sets the list of authentication entries to be displayed on the account selector UI.
         */
        fun setAuthenticationActions(authenticationEntries: List<AuthenticationAction>): Builder {
            this.authenticationActions = authenticationEntries.toMutableList()
            return this
        }

        /**
         * Builds a [BeginGetCredentialResponse] instance.
         */
        fun build(): BeginGetCredentialResponse {
            return BeginGetCredentialResponse(
                credentialEntries.toList(),
                actions.toList(),
                authenticationActions.toList(),
                remoteEntry
            )
        }
    }
}
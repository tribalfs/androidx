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

package androidx.credentials.exceptions

/**
 * During the create credential flow, this is thrown when a user intentionally cancels an operation.
 * When this happens, the application should handle logic accordingly, typically under indication
 * the user does not want to see Credential Manager anymore.
 *
 * @see CreateCredentialException
 */
class CreateCredentialCancellationException @JvmOverloads constructor(
    errorMessage: CharSequence? = null
) : CreateCredentialException(TYPE_CREATE_CREDENTIAL_CANCELLATION_EXCEPTION, errorMessage) {
    internal companion object {
        internal const val TYPE_CREATE_CREDENTIAL_CANCELLATION_EXCEPTION =
            "android.credentials.CreateCredentialException.TYPE_USER_CANCELED"
    }
}

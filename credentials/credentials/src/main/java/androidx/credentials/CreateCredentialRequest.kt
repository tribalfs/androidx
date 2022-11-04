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

/**
 * Base request class for registering a credential.
 *
 * An application can construct a subtype request and call [CredentialManager.executeCreateCredential] to
 * launch framework UI flows to collect consent and any other metadata needed from the user to
 * register a new user credential.
 *
 * @property type the credential type determined by the credential-type-specific subclass
 * @property data the request data in the [Bundle] format
 * @property requireSystemProvider true if must only be fulfilled by a system provider and false
 *                              otherwise
 */
open class CreateCredentialRequest(
    val type: String,
    val data: Bundle,
    val requireSystemProvider: Boolean,
)
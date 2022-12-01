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

import androidx.annotation.VisibleForTesting
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialException

/**
 * During the create public key credential flow, this is returned when an authenticator response
 * exception contains a namespace_err from the fido spec, indicating the operation is not allowed
 * by namespaces in XML. The fido spec can be found
 * [here](https://webidl.spec.whatwg.org/#idl-DOMException-error-names).
 *
 * @see CreatePublicKeyCredentialException
 * @hide
 */
class CreatePublicKeyCredentialNamespaceException @JvmOverloads constructor(
    errorMessage: CharSequence? = null
) : CreatePublicKeyCredentialException(
    TYPE_CREATE_PUBLIC_KEY_CREDENTIAL_NAMESPACE_EXCEPTION,
    errorMessage) {
    /** @hide */
    companion object {
        @VisibleForTesting(otherwise = VisibleForTesting.PACKAGE_PRIVATE)
        const val TYPE_CREATE_PUBLIC_KEY_CREDENTIAL_NAMESPACE_EXCEPTION: String =
            "androidx.credentials.TYPE_CREATE_PUBLIC_KEY_CREDENTIAL_NAMESPACE_EXCEPTION"
    }
}

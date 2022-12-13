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

import android.os.Bundle
import android.util.ArraySet
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.internal.FrameworkClassParsingException

/**
 * Base request class for registering a credential.
 *
 * Providers will receive a subtype of this request with the call.
 *
 * @hide
 */
@RequiresApi(34)
abstract class BeginCreateCredentialProviderRequest internal constructor(
    /** @hide */
    @get:RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
    open val type: String,
    val callingAppInfo: CallingAppInfo
    ) {
    companion object {
        internal fun createFrom(
            type: String,
            data: Bundle,
            // TODO("Change to framework ApplicationInfo")
            packageName: String
        ): BeginCreateCredentialProviderRequest {
            return try {
                when (type) {
                    PasswordCredential.TYPE_PASSWORD_CREDENTIAL ->
                        BeginCreatePasswordCredentialRequest.createFrom(
                            data,
                            CallingAppInfo(packageName, ArraySet())
                        )
                    PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL ->
                        BeginCreatePublicKeyCredentialRequest.createFrom(
                            data,
                            CallingAppInfo(packageName, ArraySet())
                        )
                    else -> throw FrameworkClassParsingException()
                }
            } catch (e: FrameworkClassParsingException) {
                BeginCreateCustomCredentialRequest(
                    type,
                    data,
                    CallingAppInfo(packageName, ArraySet())
                )
            }
        }
    }
}

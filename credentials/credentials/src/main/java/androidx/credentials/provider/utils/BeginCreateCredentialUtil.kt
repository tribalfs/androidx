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

package androidx.credentials.provider.utils

import android.service.credentials.BeginCreateCredentialRequest
import androidx.annotation.RequiresApi
import androidx.credentials.PasswordCredential
import androidx.credentials.PublicKeyCredential
import androidx.credentials.internal.FrameworkClassParsingException
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginCreatePublicKeyCredentialRequest

/**
 * @hide
 */
@RequiresApi(34)
class BeginCreateCredentialUtil {
    companion object {
        @JvmStatic
        internal fun convertToStructuredRequest(request: BeginCreateCredentialRequest):
            BeginCreateCredentialRequest {
            return try {
                when (request.type) {
                    PasswordCredential.TYPE_PASSWORD_CREDENTIAL -> {
                        BeginCreatePasswordCredentialRequest.createFrom(
                            request.data, request.callingAppInfo)
                    }
                    PublicKeyCredential.TYPE_PUBLIC_KEY_CREDENTIAL -> {
                        BeginCreatePublicKeyCredentialRequest.createFrom(
                            request.data, request.callingAppInfo)
                    }
                    else -> {
                        BeginCreateCredentialRequest(request.type, request.data,
                            request.callingAppInfo)
                    }
                }
            } catch (e: FrameworkClassParsingException) {
                BeginCreateCredentialRequest(request.type, request.data, request.callingAppInfo)
            }
        }
    }
}
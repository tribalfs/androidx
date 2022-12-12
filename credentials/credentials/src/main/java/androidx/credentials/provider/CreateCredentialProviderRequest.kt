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

import androidx.annotation.RequiresApi
import androidx.credentials.CreateCredentialRequest

/**
 * Request class for registering a credential.
 *
 * This request contains the actual request coming from the calling app,
 * and the application information associated with the calling app
 *
 * @hide
 */
@RequiresApi(34)
class CreateCredentialProviderRequest internal constructor(
    val callingRequest: CreateCredentialRequest,
    val callingAppInfo: CallingAppInfo
)
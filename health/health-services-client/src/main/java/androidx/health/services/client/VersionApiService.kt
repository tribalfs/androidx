/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.health.services.client

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.health.services.client.impl.IVersionApiService
import androidx.health.services.client.impl.IpcConstants

/** Service that allows querying the canonical SDK version used to compile this app. */
public class VersionApiService : Service() {

    private val stub: VersionApiServiceStub = VersionApiServiceStub()

    override fun onBind(intent: Intent?): IBinder? {
        if (intent?.action != IpcConstants.VERSION_API_BIND_ACTION) {
            Log.w(TAG, "Bind request with invalid action [${intent?.action}]")
            return null
        }

        return stub
    }

    private class VersionApiServiceStub : IVersionApiService.Stub() {
        override fun getVersionApiServiceVersion(): Int = VERSION_API_SERVICE_VERSION
        override fun getSdkVersion(): Int = CANONICAL_SDK_VERSION
    }

    private companion object {
        private const val TAG = "VersionApiService"
    }
}

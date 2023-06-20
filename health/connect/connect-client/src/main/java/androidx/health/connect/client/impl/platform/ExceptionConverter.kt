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

@file:RequiresApi(api = 34)

package androidx.health.connect.client.impl.platform

import android.health.connect.HealthConnectException
import android.os.RemoteException
import androidx.annotation.RequiresApi
import java.io.IOException
import java.lang.IllegalArgumentException
import java.lang.IllegalStateException

/** Converts exception returned by the platform to one of standard exception class hierarchy. */
internal fun HealthConnectException.toKtException(): Exception {
    return when (errorCode) {
        HealthConnectException.ERROR_IO -> IOException(this)
        HealthConnectException.ERROR_REMOTE -> RemoteException(message)
        HealthConnectException.ERROR_SECURITY -> SecurityException(this)
        HealthConnectException.ERROR_INVALID_ARGUMENT -> IllegalArgumentException(this)
        else -> IllegalStateException(this)
    }
}

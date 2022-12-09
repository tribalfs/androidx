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
import android.net.Uri
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.VisibleForTesting
import androidx.credentials.provider.Action.Companion.toSlice
import java.util.Collections
/**
 * An entry on the selector, denoting that authentication is needed to proceed.
 *
 * Providers should set this entry when the provider app is locked, and no credentials can
 * be returned. Providers must set the [PendingIntent] that leads to their authentication flow.
 *
 * @property pendingIntent the [PendingIntent] to be invoked when the user selects
 * this authentication entry
 *
 * See [CredentialsResponseContent] for usage details.
 *
 * @hide
 */
@RequiresApi(34)
class AuthenticationAction constructor(
    val pendingIntent: PendingIntent,
) {
    companion object {
        private const val TAG = "AuthenticationAction"
        @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
        internal const val SLICE_HINT_PENDING_INTENT =
            "androidx.credentials.provider.authenticationAction.SLICE_HINT_PENDING_INTENT"
        @JvmStatic
        fun toSlice(authenticationAction: AuthenticationAction): Slice {
            // TODO("Put the right spec and version value")
            val sliceBuilder = Slice.Builder(Uri.EMPTY, SliceSpec("type", 1))
            sliceBuilder.addAction(authenticationAction.pendingIntent,
                Slice.Builder(sliceBuilder)
                    .addHints(Collections.singletonList(SLICE_HINT_PENDING_INTENT))
                    .build(),
                /*subType=*/null)
            return sliceBuilder.build()
        }
        @JvmStatic
        internal fun toFrameworkClass(authenticationAction: AuthenticationAction):
            android.service.credentials.Action {
            return android.service.credentials.Action(toSlice(authenticationAction),
                authenticationAction.pendingIntent)
        }
        /**
         * Returns an instance of [AuthenticationAction] derived from a [Slice] object.
         *
         * @param slice the [Slice] object constructed through [toSlice]
         */
        @SuppressLint("WrongConstant") // custom conversion between jetpack and framework
        @JvmStatic
        fun fromSlice(slice: Slice): AuthenticationAction? {
            slice.items.forEach {
                if (it.hasHint(SLICE_HINT_PENDING_INTENT)) {
                    return try {
                        AuthenticationAction(it.action)
                    } catch (e: Exception) {
                        Log.i(TAG, "fromSlice failed with: " + e.message)
                        null
                    }
                }
            }
            return null
        }
    }
}

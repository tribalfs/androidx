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

import android.app.PendingIntent
import android.content.Intent
import android.credentials.CreateCredentialResponse
import android.service.credentials.BeginGetCredentialRequest
import android.service.credentials.CreateCredentialRequest
import android.service.credentials.CredentialProviderService
import android.util.Log
import android.service.credentials.CredentialEntry
import android.service.credentials.BeginGetCredentialResponse
import android.service.credentials.BeginCreateCredentialResponse
import androidx.annotation.RequiresApi
import androidx.credentials.GetCredentialResponse
import android.app.Activity
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.provider.utils.BeginGetCredentialUtil

/**
 * PendingIntentHandler to be used by credential providers to extract requests from
 * [PendingIntent] invoked when a given [CreateEntry], or a [CustomCredentialEntry]
 * is selected by the user.
 *
 * This handler can also be used to set [android.credentials.CreateCredentialResponse] and
 * [android.credentials.GetCredentialResponse] on the result of the activity
 * invoked by the [PendingIntent]
 */
@RequiresApi(34)
class PendingIntentHandler {
    companion object {
        private const val TAG = "PendingIntentHandler"

        /**
         * Extracts the [ProviderCreateCredentialRequest] from the provider's
         * [PendingIntent] invoked by the Android system.
         *
         * @param intent the intent associated with the [Activity] invoked through the
         * [PendingIntent]
         *
         * @throws NullPointerException If [intent] is null
         */
        @JvmStatic
        fun retrieveProviderCreateCredentialRequest(intent: Intent):
            ProviderCreateCredentialRequest? {
            val frameworkReq: CreateCredentialRequest? =
                intent.getParcelableExtra(
                CredentialProviderService
                    .EXTRA_CREATE_CREDENTIAL_REQUEST, CreateCredentialRequest::class.java
                )
            if (frameworkReq == null) {
                Log.i(TAG, "Request not found in pendingIntent")
                return frameworkReq
            }
            return ProviderCreateCredentialRequest(
                androidx.credentials.CreateCredentialRequest
                    .createFrom(
                        frameworkReq.type,
                        frameworkReq.data,
                        frameworkReq.data,
                        requireSystemProvider = false) ?: return null,
                frameworkReq.callingAppInfo)
        }

        /**
         * Extracts the [BeginGetCredentialRequest] from the provider's
         * [PendingIntent] invoked by the Android system when the user
         * selects an [AuthenticationAction].
         *
         * @param intent the intent associated with the [Activity] invoked through the
         * [PendingIntent]
         *
         * @throws NullPointerException If [intent] is null
         */
        @JvmStatic
        fun retrieveBeginGetCredentialRequest(intent: Intent): BeginGetCredentialRequest? {
            val request = intent.getParcelableExtra(
                "android.service.credentials.extra.BEGIN_GET_CREDENTIAL_REQUEST",
                BeginGetCredentialRequest::class.java
            )
            return request?.let { BeginGetCredentialUtil.convertToStructuredRequest(it) }
        }

        /**
         * Sets the [CreateCredentialResponse] on the result of the
         * activity invoked by the [PendingIntent] set on a
         * [CreateEntry].
         *
         * @param intent the intent to be set on the result of the [Activity] invoked through the
         * [PendingIntent]
         * @param response the response to be set as an extra on the [intent]
         *
         * @throws NullPointerException If [intent], or [response] is null
         */
        @JvmStatic
        fun setCreateCredentialResponse(
            intent: Intent,
            response: androidx.credentials.CreateCredentialResponse
        ) {
            intent.putExtra(
                CredentialProviderService.EXTRA_CREATE_CREDENTIAL_RESPONSE,
                CreateCredentialResponse(response.data))
        }

        /**
         * Extracts the [ProviderGetCredentialRequest] from the provider's
         * [PendingIntent] invoked by the Android system, when the user selects a
         * [CredentialEntry].
         *
         * @param intent the intent associated with the [Activity] invoked through the
         * [PendingIntent]
         *
         * @throws NullPointerException If [intent] is null
         */
        @JvmStatic
        fun retrieveProviderGetCredentialRequest(intent: Intent):
            ProviderGetCredentialRequest? {
            val frameworkReq = intent.getParcelableExtra(
                CredentialProviderService.EXTRA_GET_CREDENTIAL_REQUEST,
                android.service.credentials.GetCredentialRequest::class.java
            )
            if (frameworkReq == null) {
                Log.i(TAG, "Get request from framework is null")
                return null
            }
            return ProviderGetCredentialRequest.createFrom(frameworkReq)
        }

        /**
         * Sets the [android.credentials.GetCredentialResponse] on the result of the
         * activity invoked by the [PendingIntent], set on a [CreateEntry].
         *
         * @param intent the intent to be set on the result of the [Activity] invoked through the
         * [PendingIntent]
         * @param response the response to be set as an extra on the [intent]
         *
         * @throws NullPointerException If [intent], or [response] is null
         */
        @JvmStatic
        fun setGetCredentialResponse(
            intent: Intent,
            response: GetCredentialResponse
        ) {
            intent.putExtra(
                CredentialProviderService.EXTRA_GET_CREDENTIAL_RESPONSE,
                android.credentials.GetCredentialResponse(
                    android.credentials.Credential(response.credential.type,
                        response.credential.data))
            )
        }

        /**
         * Sets the [android.service.credentials.BeginGetCredentialResponse] on the result of the
         * activity invoked by the [PendingIntent], set on an [AuthenticationAction].
         *
         * @param intent the intent to be set on the result of the [Activity] invoked through the
         * [PendingIntent]
         * @param response the response to be set as an extra on the [intent]
         *
         * @throws NullPointerException If [intent], or [response] is null
         */
        @JvmStatic
        fun setBeginGetCredentialResponse(
            intent: Intent,
            response: BeginGetCredentialResponse
        ) {
            intent.putExtra(
                CredentialProviderService.EXTRA_BEGIN_GET_CREDENTIAL_RESPONSE,
                response
            )
        }

        /**
         * Sets the [androidx.credentials.exceptions.GetCredentialException] if an error is
         * encountered during the final phase of the get credential flow.
         *
         * A credential provider service returns a list of [CredentialEntry] as part of
         * the [BeginGetCredentialResponse] to the query phase of the get-credential flow.
         * If the user selects one of these entries, the corresponding [PendingIntent]
         * is fired and the provider's activity is invoked.
         * If there is an error encountered during the lifetime of that activity, the provider
         * must use this API to set an exception before finishing this activity.
         *
         * @param intent the intent to be set on the result of the [Activity] invoked through the
         * [PendingIntent]
         * @param exception the exception to be set as an extra to the [intent]
         *
         * @throws NullPointerException If [intent], or [exception] is null
         */
        @JvmStatic
        fun setGetCredentialException(
            intent: Intent,
            exception: GetCredentialException
        ) {
            intent.putExtra(
                CredentialProviderService.EXTRA_GET_CREDENTIAL_EXCEPTION,
                android.credentials.GetCredentialException(exception.type, exception.message)
            )
        }

        /**
         * Sets the [androidx.credentials.exceptions.CreateCredentialException] if an error is
         * encountered during the final phase of the create credential flow.
         *
         * A credential provider service returns a list of [CreateEntry] as part of
         * the [BeginCreateCredentialResponse] to the query phase of the get-credential flow.
         *
         * If the user selects one of these entries, the corresponding [PendingIntent]
         * is fired and the provider's activity is invoked. If there is an error encountered
         * during the lifetime of that activity, the provider must use this API to set
         * an exception before finishing the activity.
         *
         * @param intent the intent to be set on the result of the [Activity] invoked through the
         * [PendingIntent]
         * @param exception the exception to be set as an extra to the [intent]
         *
         * @throws NullPointerException If [intent], or [exception] is null
         */
        @JvmStatic
        fun setCreateCredentialException(
            intent: Intent,
            exception: CreateCredentialException
        ) {
            intent.putExtra(
                CredentialProviderService.EXTRA_CREATE_CREDENTIAL_EXCEPTION,
                android.credentials.CreateCredentialException(exception.type, exception.message)
            )
        }
    }
}

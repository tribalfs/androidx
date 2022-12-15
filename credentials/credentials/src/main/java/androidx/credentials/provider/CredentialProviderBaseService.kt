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

import android.os.CancellationSignal
import android.os.OutcomeReceiver
import android.service.credentials.BeginCreateCredentialResponse
import android.service.credentials.CredentialProviderService
import android.service.credentials.BeginGetCredentialsRequest
import android.service.credentials.BeginGetCredentialsResponse
import android.util.ArraySet
import android.util.Log
import androidx.annotation.RequiresApi

/**
 * Credential Provider base service to be extended by provider services.
 *
 * This class extends from the framework [CredentialProviderService], and is
 * called by the framework on credential get and create requests. The framework
 * requests are converted to structured jetpack requests, and sent to
 * provider services that extend from this service.
 *
 * @hide
 */
@RequiresApi(34)
abstract class CredentialProviderBaseService : CredentialProviderService() {
    final override fun onBeginGetCredentials(
        request: BeginGetCredentialsRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialsResponse,
            android.service.credentials.CredentialProviderException>
    ) {
        val beginGetCredentialOptions: MutableList<BeginGetCredentialOption> = mutableListOf()
        request.beginGetCredentialOptions.forEach {
            beginGetCredentialOptions.add(
                BeginGetCredentialOption
                    .createFrom(
                        it.type,
                        it.candidateQueryData))
        }
        val structuredRequest =
            BeginGetCredentialsProviderRequest(
                beginGetCredentialOptions,
                CallingAppInfo(request.callingPackage,
                    ArraySet()
                ))
        val outcome = object : OutcomeReceiver<BeginGetCredentialsProviderResponse,
            CredentialProviderException> {
            override fun onResult(response: BeginGetCredentialsProviderResponse?) {
                Log.i(TAG, "onGetCredentials response returned from provider " +
                    "to jetpack library")
                callback.onResult(response?.let {
                    BeginGetCredentialsProviderResponse.toFrameworkClass(it) })
            }

            override fun onError(error: CredentialProviderException) {
                super.onError(error)
                Log.i(TAG, "onGetCredentials error returned from provider " +
                    "to jetpack library")
                // TODO("Change error code to provider error when ready on framework")
                error.message?.let {
                    callback.onError(android.service.credentials.CredentialProviderException(
                        android.service.credentials.CredentialProviderException.ERROR_UNKNOWN, it)
                    )
                    return
                }
                callback.onError(android.service.credentials.CredentialProviderException(
                    android.service.credentials.CredentialProviderException.ERROR_UNKNOWN))
            }
        }
        onBeginGetCredentialsRequest(structuredRequest, cancellationSignal, outcome)
    }

    final override fun onBeginCreateCredential(
        request: android.service.credentials.BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse,
            android.service.credentials.CredentialProviderException>
    ) {
        val outcome = object : OutcomeReceiver<
            BeginCreateCredentialProviderResponse, CredentialProviderException> {
            override fun onResult(response: BeginCreateCredentialProviderResponse?) {
                Log.i(
                    TAG, "onCreateCredential result returned from provider to jetpack " +
                        "library with credential entries size: " + response?.createEntries?.size)
                callback.onResult(response?.let {
                    BeginCreateCredentialProviderResponse.toFrameworkClass(it) })
            }
            override fun onError(error: CredentialProviderException) {
                Log.i(
                    TAG, "onCreateCredential result returned from provider to jetpack")
                super.onError(error)
                // TODO("Change error code to provider error when ready on framework")
                error.message?.let {
                    callback.onError(android.service.credentials.CredentialProviderException(
                        android.service.credentials.CredentialProviderException.ERROR_UNKNOWN, it)
                    )
                    return
                }
                callback.onError(android.service.credentials.CredentialProviderException(
                    android.service.credentials.CredentialProviderException.ERROR_UNKNOWN))
            }
        }
        onBeginCreateCredentialRequest(
            BeginCreateCredentialProviderRequest.createFrom(
                request.type,
                request.data,
                request.callingPackage),
            cancellationSignal, outcome)
    }

    /**
     * Called by the Credential Manager Jetpack library to get credentials stored with a provider
     * service. Provider services must extend this in order to handle a
     * [GetCredentialProviderRequest] request.
     *
     * Provider service must call one of the [callback] methods to notify the result of the
     * request.
     *
     * @param [request] the [GetCredentialProviderRequest] to handle
     * See [BeginGetCredentialsProviderResponse] for the response to be returned
     * @param cancellationSignal signal for observing cancellation requests. The system will
     * use this to notify you that the result is no longer needed and you should stop
     * handling it in order to save your resources
     * @param callback the callback object to be used to notify the response or error
     *
     * @hide
     */
    abstract fun onBeginGetCredentialsRequest(
        request: BeginGetCredentialsProviderRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialsProviderResponse, CredentialProviderException>
    )

    /**
     * Called by the Credential Manager Jetpack library to begin a credential registration flow
     * with a credential provider service. Provider services must extend this in order to handle a
     * [BeginCreateCredentialProviderRequest] request.
     *
     * Provider service must call one of the [callback] methods to notify the result of the
     * request.
     *
     * @param [request] the [BeginCreateCredentialProviderRequest] to handle
     * See [BeginCreateCredentialProviderResponse] for the response to be returned
     * @param cancellationSignal signal for observing cancellation requests. The system will
     * use this to notify you that the result is no longer needed and you should stop
     * handling it in order to save your resources
     * @param callback the callback object to be used to notify the response or error
     *
     * @hide
     */
    abstract fun onBeginCreateCredentialRequest(
        request: BeginCreateCredentialProviderRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialProviderResponse,
            CredentialProviderException>
    )

    companion object {
        private const val TAG = "BaseService"
    }
}

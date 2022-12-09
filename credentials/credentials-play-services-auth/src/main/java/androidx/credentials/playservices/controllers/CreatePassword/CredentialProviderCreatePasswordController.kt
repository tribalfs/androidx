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

package androidx.credentials.playservices.controllers.CreatePassword

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ResultReceiver
import android.util.Log
import androidx.annotation.VisibleForTesting
import androidx.credentials.CreateCredentialResponse
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.CredentialManagerCallback
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.playservices.HiddenActivity
import androidx.credentials.playservices.controllers.CredentialProviderController
import com.google.android.gms.auth.api.identity.SavePasswordRequest
import com.google.android.gms.auth.api.identity.SignInPassword
import java.util.concurrent.Executor

/**
 * A controller to handle the CreatePassword flow with play services.
 *
 * @hide
 */
@Suppress("deprecation")
class CredentialProviderCreatePasswordController(private val activity: Activity) :
    CredentialProviderController<
        CreatePasswordRequest,
        SavePasswordRequest,
        Unit,
        CreateCredentialResponse,
        CreateCredentialException>(activity) {

    /**
     * The callback object state, used in the protected handleResponse method.
     */
    private lateinit var callback: CredentialManagerCallback<CreateCredentialResponse,
        CreateCredentialException>

    /**
     * The callback requires an executor to invoke it.
     */
    private lateinit var executor: Executor

    private val resultReceiver = object : ResultReceiver(
        Handler(Looper.getMainLooper())
    ) {
        public override fun onReceiveResult(
            resultCode: Int,
            resultData: Bundle
        ) {
            Log.i(
                TAG,
                "onReceiveResult - CredentialProviderCreatePasswordController"
            )
            if (maybeReportErrorFromResultReceiver(resultData,
                    { errType, errMsg ->
                        createCredentialExceptionTypeToException(errType, errMsg) },
                    executor = executor, callback = callback)) return
            handleResponse(resultData.getInt(ACTIVITY_REQUEST_CODE_TAG), resultCode,
                resultData.getParcelable(RESULT_DATA_TAG))
        }
    }

    override fun invokePlayServices(
        request: CreatePasswordRequest,
        callback: CredentialManagerCallback<CreateCredentialResponse, CreateCredentialException>,
        executor: Executor
    ) {
        this.callback = callback
        this.executor = executor
        val convertedRequest: SavePasswordRequest = this.convertRequestToPlayServices(request)
        val hiddenIntent = Intent(activity, HiddenActivity::class.java)
        hiddenIntent.putExtra(REQUEST_TAG, convertedRequest)
        generateHiddenActivityIntent(resultReceiver, hiddenIntent, CREATE_PASSWORD_TAG)
        activity.startActivity(hiddenIntent)
    }

    internal fun handleResponse(uniqueRequestCode: Int, resultCode: Int, data: Intent?) {
        Log.i(TAG, "$data - the intent back - is un-used.")
        if (uniqueRequestCode != CONTROLLER_REQUEST_CODE) {
            return
        }
        if (maybeReportErrorResultCodeCreate(resultCode, TAG) { e -> this.executor.execute {
            this.callback.onError(e) } }) return
        Log.i(TAG, "Saving password succeeded")
        val response: CreateCredentialResponse = convertResponseToCredentialManager(Unit)
        this.executor.execute { -> this.callback.onResult(response) }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun convertRequestToPlayServices(request: CreatePasswordRequest):
        SavePasswordRequest {
        return SavePasswordRequest.builder().setSignInPassword(
            SignInPassword(request.id, request.password)
        ).build()
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    public override fun convertResponseToCredentialManager(response: Unit):
        CreateCredentialResponse {
        return CreatePasswordResponse()
    }

    companion object {
        private val TAG = CredentialProviderCreatePasswordController::class.java.name
        private var controller: CredentialProviderCreatePasswordController? = null
        // TODO("Ensure this is tested for multiple calls")
        /**
         * This finds a past version of the
         * [CredentialProviderCreatePasswordController] if it exists, otherwise
         * it generates a new instance.
         *
         * @param activity the calling activity for this controller
         * @return a credential provider controller for CreatePasswordController
         */
        @JvmStatic
        fun getInstance(activity: Activity):
            CredentialProviderCreatePasswordController {
            if (controller == null) {
                controller = CredentialProviderCreatePasswordController(activity)
            }
            return controller!!
        }
    }
}

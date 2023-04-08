/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.appactions.interaction.capabilities.core.impl

import androidx.annotation.RestrictTo
import androidx.appactions.interaction.capabilities.core.ActionExecutorAsync
import androidx.appactions.interaction.capabilities.core.ExecutionResult
import androidx.appactions.interaction.capabilities.core.impl.spec.ActionSpec
import androidx.appactions.interaction.proto.AppActionsContext.AppDialogState
import androidx.appactions.interaction.proto.FulfillmentResponse
import androidx.appactions.interaction.proto.ParamValue
import androidx.concurrent.futures.await
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/**
 * CapabilitySession implementation for executing single-turn fulfillment requests.
 *
 * @suppress
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class SingleTurnCapabilitySession<
    ArgumentsT,
    OutputT,
>(
    override val sessionId: String,
    private val actionSpec: ActionSpec<*, ArgumentsT, OutputT>,
    private val actionExecutorAsync: ActionExecutorAsync<ArgumentsT, OutputT>,
    private val mutex: Mutex,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default),
) : CapabilitySession {
    override val state: AppDialogState
        get() {
            throw UnsupportedOperationException()
        }
    override val status: CapabilitySession.Status
        get() {
            throw UnsupportedOperationException()
        }

    override val uiHandle: Any = actionExecutorAsync.uiHandle

    override fun destroy() {}

    // single-turn capability does not have touch events
    override fun setTouchEventCallback(callback: TouchEventCallback) {
        throw UnsupportedOperationException()
    }

    override fun execute(
        argumentsWrapper: ArgumentsWrapper,
        callback: CallbackInternal,
    ) {
        val paramValuesMap: Map<String, List<ParamValue>> =
            argumentsWrapper.paramValues.mapValues { entry -> entry.value.mapNotNull { it.value } }
        val arguments = actionSpec.buildArguments(paramValuesMap)
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                mutex.lock(owner = this@SingleTurnCapabilitySession)
                UiHandleRegistry.registerUiHandle(uiHandle, sessionId)
                val output = actionExecutorAsync.execute(arguments).await()
                callback.onSuccess(convertToFulfillmentResponse(output))
            } catch (t: Throwable) {
                callback.onError(ErrorStatusInternal.CANCELLED)
            } finally {
                UiHandleRegistry.unregisterUiHandle(uiHandle)
                mutex.unlock(owner = this@SingleTurnCapabilitySession)
            }
        }
    }

    /** Converts typed {@link ExecutionResult} to {@link FulfillmentResponse} proto. */
    private fun convertToFulfillmentResponse(
        executionResult: ExecutionResult<OutputT>,
    ): FulfillmentResponse {
        val fulfillmentResponseBuilder =
            FulfillmentResponse.newBuilder().setStartDictation(executionResult.shouldStartDictation)
        executionResult.output?.let {
            fulfillmentResponseBuilder.setExecutionOutput(
                actionSpec.convertOutputToProto(it),
            )
        }
        return fulfillmentResponseBuilder.build()
    }
}

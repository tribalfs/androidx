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

package androidx.appactions.interaction.capabilities.core

import androidx.appactions.interaction.capabilities.core.impl.concurrent.ListenableFutureHelper
import com.google.common.util.concurrent.ListenableFuture
/**
 * Base interface for Session of all verticals.
 */
interface BaseSession<ArgumentT, OutputT> {
    /**
     * Implement any initialization logic.
     *
     * This method is called once, before any other listeners are invoked.
     */
    fun onInit(initArg: InitArg) {}

    /**
     * Called when all arguments are finalized.
     * @param argument the Argument instance containing data for fulfillment.
     * @return an ExecutionResult instance.
     */
    suspend fun onFinish(argument: ArgumentT): ExecutionResult<OutputT> {
        throw NotImplementedError()
    }

    /**
     * Called when all arguments are finalized.
     * @param argument the Argument instance containing data for fulfillment.
     * @return a ListenableFuture containing an ExecutionResult instance.
     */
    fun onFinishAsync(argument: ArgumentT): ListenableFuture<ExecutionResult<OutputT>> {
        return ListenableFutureHelper.convertToListenableFuture("onFinish") { onFinish(argument) }
    }

    /**
     * Implement any cleanup logic.
     * This method is called some time after the session finishes.
     */
    fun onDestroy() {}
}

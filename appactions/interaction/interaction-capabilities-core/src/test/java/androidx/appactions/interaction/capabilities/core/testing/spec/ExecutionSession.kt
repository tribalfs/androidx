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

package androidx.appactions.interaction.capabilities.core.testing.spec

import androidx.appactions.interaction.capabilities.core.AppEntityListener
import androidx.appactions.interaction.capabilities.core.BaseExecutionSession
import androidx.appactions.interaction.capabilities.core.ExecutionResult
import androidx.appactions.interaction.capabilities.core.impl.concurrent.Futures

interface ExecutionSession : BaseExecutionSession<Arguments, Output> {

    val requiredStringListener: AppEntityListener<String>? get() = null

    companion object {
        @JvmStatic
        val DEFAULT = object : ExecutionSession {
            override fun onExecuteAsync(arguments: Arguments) =
                Futures.immediateFuture(
                    ExecutionResult.Builder<Output>().build(),
                )
        }
    }
}

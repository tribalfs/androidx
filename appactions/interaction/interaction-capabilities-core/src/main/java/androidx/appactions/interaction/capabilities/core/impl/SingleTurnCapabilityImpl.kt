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
import androidx.appactions.interaction.capabilities.core.ActionCapability
import androidx.appactions.interaction.capabilities.core.ActionExecutor
import androidx.appactions.interaction.capabilities.core.ActionExecutorAsync
import androidx.appactions.interaction.capabilities.core.HostProperties
import androidx.appactions.interaction.capabilities.core.impl.concurrent.ListenableFutureHelper
import androidx.appactions.interaction.capabilities.core.impl.spec.ActionSpec
import androidx.appactions.interaction.proto.AppActionsContext.AppAction
import androidx.appactions.interaction.proto.TaskInfo

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY)
internal class SingleTurnCapabilityImpl<
    PropertyT,
    ArgumentT,
    OutputT,
    > private constructor(
    override val id: String,
    val actionSpec: ActionSpec<PropertyT, ArgumentT, OutputT>,
    val property: PropertyT,
    val actionExecutorAsync: ActionExecutorAsync<ArgumentT, OutputT>,
    val uiHandle: Any,
) : ActionCapability {
    /** constructor using ActionExecutor. */
    constructor(
        id: String,
        actionSpec: ActionSpec<PropertyT, ArgumentT, OutputT>,
        property: PropertyT,
        actionExecutor: ActionExecutor<ArgumentT, OutputT>,
    ) : this(
        id,
        actionSpec,
        property,
        ActionExecutorAsync<ArgumentT, OutputT> {
                argument ->
            ListenableFutureHelper.convertToListenableFuture {
                actionExecutor.execute(argument)
            }
        },
        actionExecutor,
    )

    /** constructor using ActionExecutorAsync.  */
    constructor(
        id: String,
        actionSpec: ActionSpec<PropertyT, ArgumentT, OutputT>,
        property: PropertyT,
        actionExecutorAsync: ActionExecutorAsync<ArgumentT, OutputT>,
    ) : this(
        id,
        actionSpec,
        property,
        actionExecutorAsync,
        actionExecutorAsync,
    )
    override val supportsMultiTurnTask = false

    override fun getAppAction(): AppAction {
        return actionSpec.convertPropertyToProto(property).toBuilder()
            .setTaskInfo(TaskInfo.newBuilder().setSupportsPartialFulfillment(false))
            .setIdentifier(id)
            .build()
    }

    override fun createSession(hostProperties: HostProperties): ActionCapabilitySession {
        return SingleTurnCapabilitySession(
            actionSpec,
            actionExecutorAsync,
            uiHandle,
        )
    }
}

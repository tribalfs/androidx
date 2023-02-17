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

package androidx.appactions.interaction.capabilities.core.task.impl
import androidx.appactions.interaction.capabilities.core.ActionCapability
import androidx.appactions.interaction.capabilities.core.BaseSession
import androidx.appactions.interaction.capabilities.core.HostProperties
import androidx.appactions.interaction.capabilities.core.SessionBuilder
import androidx.appactions.interaction.capabilities.core.impl.ActionCapabilitySession
import androidx.appactions.interaction.capabilities.core.impl.spec.ActionSpec
import androidx.appactions.interaction.proto.AppActionsContext.AppAction
import androidx.appactions.interaction.proto.TaskInfo
import java.util.function.Supplier

/**
 * @param id a unique id for this capability, can be null
 * @param supportsMultiTurnTask whether this is a single-turn capability or a multi-turn capability
 * @param actionSpec the ActionSpec for this capability
 * @param sessionBuilder the SessionBuilder provided by the library user
 * @param sessionBridge a SessionBridge object that converts SessionT into TaskHandler instance
 * @param sessionUpdaterSupplier a Supplier of SessionUpdaterT instances
 */
internal class TaskCapabilityImpl<
    PropertyT,
    ArgumentT,
    OutputT,
    SessionT : BaseSession<ArgumentT, OutputT>,
    ConfirmationT,
    SessionUpdaterT,
    > constructor(
    override val id: String?,
    val actionSpec: ActionSpec<PropertyT, ArgumentT, OutputT>,
    val property: PropertyT,
    val sessionBuilder: SessionBuilder<SessionT>,
    val sessionBridge: SessionBridge<SessionT, ConfirmationT>,
    val sessionUpdaterSupplier: Supplier<SessionUpdaterT>,
) : ActionCapability {

    override val supportsMultiTurnTask = true

    override fun getAppAction(): AppAction {
        val appActionBuilder = actionSpec.convertPropertyToProto(property).toBuilder()
            .setTaskInfo(TaskInfo.newBuilder().setSupportsPartialFulfillment(true))
        id?.let(appActionBuilder::setIdentifier)
        return appActionBuilder.build()
    }

    override fun createSession(hostProperties: HostProperties): ActionCapabilitySession {
        val externalSession = sessionBuilder.createSession(
            hostProperties,
        )
        return TaskCapabilitySession(
            actionSpec,
            getAppAction(),
            sessionBridge.createTaskHandler(externalSession),
            externalSession,
        )
    }
}

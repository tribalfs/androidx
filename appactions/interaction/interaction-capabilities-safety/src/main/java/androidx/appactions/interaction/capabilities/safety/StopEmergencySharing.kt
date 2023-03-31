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

package androidx.appactions.interaction.capabilities.safety

import androidx.appactions.interaction.capabilities.core.CapabilityBuilderBase
import androidx.appactions.interaction.capabilities.core.Capability
import androidx.appactions.interaction.capabilities.core.BaseSession
import androidx.appactions.interaction.capabilities.core.impl.BuilderOf
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters
import androidx.appactions.interaction.capabilities.core.impl.spec.ActionSpecBuilder
import androidx.appactions.interaction.capabilities.core.task.impl.AbstractTaskUpdater
import androidx.appactions.interaction.capabilities.core.values.GenericErrorStatus
import androidx.appactions.interaction.capabilities.core.values.SuccessStatus
import androidx.appactions.interaction.capabilities.core.values.executionstatus.ActionNotInProgress
import androidx.appactions.interaction.capabilities.core.values.executionstatus.NoInternetConnection
import androidx.appactions.interaction.capabilities.safety.executionstatus.SafetyAccountNotLoggedIn
import androidx.appactions.interaction.capabilities.safety.executionstatus.SafetyFeatureNotOnboarded
import androidx.appactions.interaction.proto.ParamValue
import androidx.appactions.interaction.protobuf.Struct
import androidx.appactions.interaction.protobuf.Value
import java.util.Optional

/** StopEmergencySharing.kt in interaction-capabilities-safety */
private const val CAPABILITY_NAME = "actions.intent.STOP_EMERGENCY_SHARING"

private val ACTION_SPEC =
    ActionSpecBuilder.ofCapabilityNamed(CAPABILITY_NAME)
        .setDescriptor(StopEmergencySharing.Property::class.java)
        .setArgument(
            StopEmergencySharing.Argument::class.java,
            StopEmergencySharing.Argument::Builder
        )
        .setOutput(StopEmergencySharing.Output::class.java)
        .bindOptionalOutput(
            "executionStatus",
            { output -> Optional.ofNullable(output.executionStatus) },
            StopEmergencySharing.ExecutionStatus::toParamValue,
        )
        .build()

// TODO(b/267806701): Add capability factory annotation once the testing library is fully migrated.
class StopEmergencySharing private constructor() {
    // TODO(b/267805819): Update to include the SessionFactory once Session API is ready.
    class CapabilityBuilder :
        CapabilityBuilderBase<
            CapabilityBuilder, Property, Argument, Output, Confirmation, TaskUpdater, Session,
            >(ACTION_SPEC) {
        override fun build(): Capability {
            super.setProperty(Property())
            return super.build()
        }
    }

    // TODO(b/268369632): Remove Property from public capability APIs.
    class Property internal constructor()

    class Argument internal constructor() {
        class Builder : BuilderOf<Argument> {
            override fun build(): Argument = Argument()
        }
    }

    class Output internal constructor(val executionStatus: ExecutionStatus?) {
        override fun toString(): String {
            return "Output(executionStatus=$executionStatus)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Output

            if (executionStatus != other.executionStatus) return false

            return true
        }

        override fun hashCode(): Int {
            return executionStatus.hashCode()
        }

        class Builder {
            private var executionStatus: ExecutionStatus? = null

            fun setExecutionStatus(executionStatus: ExecutionStatus): Builder =
                apply { this.executionStatus = executionStatus }

            fun build(): Output = Output(executionStatus)
        }
    }

    class ExecutionStatus {
        private var successStatus: SuccessStatus? = null
        private var genericErrorStatus: GenericErrorStatus? = null
        private var actionNotInProgress: ActionNotInProgress? = null
        private var safetyAccountNotLoggedIn: SafetyAccountNotLoggedIn? = null
        private var safetyFeatureNotOnboarded: SafetyFeatureNotOnboarded? = null
        private var noInternetConnection: NoInternetConnection? = null

        constructor(successStatus: SuccessStatus) {
            this.successStatus = successStatus
        }

        constructor(genericErrorStatus: GenericErrorStatus) {
            this.genericErrorStatus = genericErrorStatus
        }

        constructor(actionNotInProgress: ActionNotInProgress) {
            this.actionNotInProgress = actionNotInProgress
        }

        constructor(safetyAccountNotLoggedIn: SafetyAccountNotLoggedIn) {
            this.safetyAccountNotLoggedIn = safetyAccountNotLoggedIn
        }

        constructor(safetyFeatureNotOnboarded: SafetyFeatureNotOnboarded) {
            this.safetyFeatureNotOnboarded = safetyFeatureNotOnboarded
        }

        constructor(noInternetConnection: NoInternetConnection) {
            this.noInternetConnection = noInternetConnection
        }

        internal fun toParamValue(): ParamValue {
            var status: String = ""
            if (successStatus != null) {
                status = successStatus.toString()
            }
            if (genericErrorStatus != null) {
                status = genericErrorStatus.toString()
            }
            if (actionNotInProgress != null) {
                status = actionNotInProgress.toString()
            }
            if (safetyAccountNotLoggedIn != null) {
                status = safetyAccountNotLoggedIn.toString()
            }
            if (safetyFeatureNotOnboarded != null) {
                status = safetyFeatureNotOnboarded.toString()
            }
            if (noInternetConnection != null) {
                status = noInternetConnection.toString()
            }
            val value: Value = Value.newBuilder().setStringValue(status).build()
            return ParamValue.newBuilder()
                .setStructValue(
                    Struct.newBuilder()
                        .putFields(TypeConverters.FIELD_NAME_TYPE, value)
                        .build(),
                )
                .build()
        }
    }

    class Confirmation internal constructor()

    class TaskUpdater internal constructor() : AbstractTaskUpdater()

    sealed interface Session : BaseSession<Argument, Output>
}

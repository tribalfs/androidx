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

package androidx.appactions.interaction.capabilities.communication

import androidx.appactions.builtintypes.experimental.properties.Participant
import androidx.appactions.builtintypes.experimental.types.Call
import androidx.appactions.builtintypes.experimental.types.GenericErrorStatus
import androidx.appactions.builtintypes.experimental.types.SuccessStatus
import androidx.appactions.interaction.capabilities.core.BaseExecutionSession
import androidx.appactions.interaction.capabilities.core.Capability
import androidx.appactions.interaction.capabilities.core.CapabilityFactory
import androidx.appactions.interaction.capabilities.core.impl.converters.EntityConverter
import androidx.appactions.interaction.capabilities.core.impl.converters.ParamValueConverter
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.CALL_TYPE_SPEC
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters.PARTICIPANT_TYPE_SPEC
import androidx.appactions.interaction.capabilities.core.impl.spec.ActionSpecBuilder
import androidx.appactions.interaction.capabilities.core.impl.spec.ActionSpecRegistry
import androidx.appactions.interaction.capabilities.core.properties.Property
import androidx.appactions.interaction.proto.ParamValue
import androidx.appactions.interaction.protobuf.Struct
import androidx.appactions.interaction.protobuf.Value

/** A capability corresponding to actions.intent.CREATE_CALL */
@CapabilityFactory(name = CreateCall.CAPABILITY_NAME)
class CreateCall private constructor() {
    internal enum class SlotMetadata(val path: String) {
        CALL_FORMAT("call.callFormat"),
        PARTICIPANT("call.participant")
    }

    class CapabilityBuilder :
        Capability.Builder<
            CapabilityBuilder, Arguments, Output, Confirmation, ExecutionSession
            >(ACTION_SPEC) {

        fun setCallFormatProperty(
            callFormat: Property<Call.CanonicalValue.CallFormat>
        ): CapabilityBuilder =
            setProperty(
                SlotMetadata.CALL_FORMAT.path,
                callFormat,
                TypeConverters.CALL_FORMAT_ENTITY_CONVERTER)

        fun setParticipantProperty(participant: Property<Participant>): CapabilityBuilder =
            setProperty(
            SlotMetadata.PARTICIPANT.path,
            participant,
            EntityConverter.of(PARTICIPANT_TYPE_SPEC))
    }

    class Arguments
    internal constructor(
        val callFormat: Call.CanonicalValue.CallFormat?,
        val participantList: List<ParticipantReference>
    ) {
        override fun toString(): String {
            return "Arguments(callFormat=$callFormat, participantList=$participantList)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Arguments

            if (callFormat != other.callFormat) return false
            if (participantList != other.participantList) return false

            return true
        }

        override fun hashCode(): Int {
            var result = callFormat.hashCode()
            result = 31 * result + participantList.hashCode()
            return result
        }

        class Builder {
            private var callFormat: Call.CanonicalValue.CallFormat? = null
            private var participantList: List<ParticipantReference> = mutableListOf()

            fun setCallFormat(callFormat: Call.CanonicalValue.CallFormat): Builder = apply {
                this.callFormat = callFormat
            }

            fun setParticipantList(participantList: List<ParticipantReference>): Builder = apply {
                this.participantList = participantList
            }

            fun build(): Arguments = Arguments(callFormat, participantList)
        }
    }

    class Output internal constructor(val call: Call?, val executionStatus: ExecutionStatus?) {
        override fun toString(): String {
            return "Output(call=$call, executionStatus=$executionStatus)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Output

            if (call != other.call) return false
            if (executionStatus != other.executionStatus) return false

            return true
        }

        override fun hashCode(): Int {
            var result = call.hashCode()
            result = 31 * result + executionStatus.hashCode()
            return result
        }

        class Builder {
            private var call: Call? = null
            private var executionStatus: ExecutionStatus? = null

            fun setCall(call: Call): Builder = apply { this.call = call }

            fun setExecutionStatus(executionStatus: ExecutionStatus): Builder = apply {
                this.executionStatus = executionStatus
            }

            fun build(): Output = Output(call, executionStatus)
        }
    }

    class ExecutionStatus {
        private var successStatus: SuccessStatus? = null
        private var genericErrorStatus: GenericErrorStatus? = null

        constructor(successStatus: SuccessStatus) {
            this.successStatus = successStatus
        }

        constructor(genericErrorStatus: GenericErrorStatus) {
            this.genericErrorStatus = genericErrorStatus
        }

        internal fun toParamValue(): ParamValue {
            var status = ""
            if (successStatus != null) {
                status = successStatus.toString()
            }
            if (genericErrorStatus != null) {
                status = genericErrorStatus.toString()
            }
            val value: Value = Value.newBuilder().setStringValue(status).build()
            return ParamValue.newBuilder()
                .setStructValue(
                    Struct.newBuilder().putFields(TypeConverters.FIELD_NAME_TYPE, value).build()
                )
                .build()
        }
    }

    class Confirmation internal constructor()

    sealed interface ExecutionSession : BaseExecutionSession<Arguments, Output>

    companion object {
        /** Canonical name for [CreateCall] capability. */
        const val CAPABILITY_NAME: String = "actions.intent.CREATE_CALL"
        private val ACTION_SPEC =
            ActionSpecBuilder.ofCapabilityNamed(CAPABILITY_NAME)
                .setArguments(Arguments::class.java, Arguments::Builder, Arguments.Builder::build)
                .setOutput(Output::class.java)
                .bindParameter(
                    SlotMetadata.CALL_FORMAT.path,
                    Arguments::callFormat,
                    Arguments.Builder::setCallFormat,
                    TypeConverters.CALL_FORMAT_PARAM_VALUE_CONVERTER
                )
                .bindRepeatedParameter(
                    SlotMetadata.PARTICIPANT.path,
                    Arguments::participantList,
                    Arguments.Builder::setParticipantList,
                    ParticipantReference.PARAM_VALUE_CONVERTER,
                )
                .bindOutput(
                    "call",
                    Output::call,
                    ParamValueConverter.of(CALL_TYPE_SPEC)::toParamValue
                )
                .bindOutput(
                    "executionStatus",
                    Output::executionStatus,
                    ExecutionStatus::toParamValue
                )
                .build()
        init {
            ActionSpecRegistry.registerActionSpec(Arguments::class, Output::class, ACTION_SPEC)
        }
    }
}

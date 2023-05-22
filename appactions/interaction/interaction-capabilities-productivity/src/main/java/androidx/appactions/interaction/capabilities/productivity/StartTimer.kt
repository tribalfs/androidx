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

package androidx.appactions.interaction.capabilities.productivity

import androidx.appactions.builtintypes.experimental.types.GenericErrorStatus
import androidx.appactions.builtintypes.experimental.types.SuccessStatus
import androidx.appactions.interaction.capabilities.core.BaseExecutionSession
import androidx.appactions.interaction.capabilities.core.Capability
import androidx.appactions.interaction.capabilities.core.CapabilityFactory
import androidx.appactions.interaction.capabilities.core.impl.BuilderOf
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters
import androidx.appactions.interaction.capabilities.core.impl.spec.ActionSpecBuilder
import androidx.appactions.interaction.capabilities.core.properties.Property
import androidx.appactions.interaction.capabilities.core.properties.StringValue
import androidx.appactions.interaction.proto.ParamValue
import androidx.appactions.interaction.protobuf.Struct
import androidx.appactions.interaction.protobuf.Value
import java.time.Duration

private const val CAPABILITY_NAME = "actions.intent.START_TIMER"

/** A capability corresponding to actions.intent.START_TIMER */
@CapabilityFactory(name = CAPABILITY_NAME)
class StartTimer private constructor() {
    internal enum class SlotMetadata(val path: String) {
        IDENTIFIER("timer.identifier"),
        NAME("timer.name"),
        DURATION("timer.duration")
    }

    class CapabilityBuilder :
        Capability.Builder<
            CapabilityBuilder,
            Arguments,
            Output,
            Confirmation,
            ExecutionSession
            >(ACTION_SPEC) {
        fun setIdentifierProperty(
            identifier: Property<StringValue>
        ): CapabilityBuilder = setProperty(
            SlotMetadata.IDENTIFIER.path,
            identifier,
            TypeConverters.STRING_VALUE_ENTITY_CONVERTER
        )

        fun setNameProperty(name: Property<StringValue>): CapabilityBuilder = setProperty(
            SlotMetadata.NAME.path,
            name,
            TypeConverters.STRING_VALUE_ENTITY_CONVERTER
        )

        fun setDurationProperty(duration: Property<Duration>): CapabilityBuilder = setProperty(
            SlotMetadata.DURATION.path,
            duration,
            TypeConverters.DURATION_ENTITY_CONVERTER
        )
    }

    class Arguments internal constructor(
        val identifier: String?,
        val name: String?,
        val duration: Duration?
    ) {
        override fun toString(): String {
            return "Arguments(identifier=$identifier,name=$name,duration=$duration)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Arguments

            if (identifier != other.identifier) return false
            if (name != other.name) return false
            if (duration != other.duration) return false

            return true
        }

        override fun hashCode(): Int {
            var result = identifier.hashCode()
            result += 31 * name.hashCode()
            result += 31 * duration.hashCode()
            return result
        }

        class Builder : BuilderOf<Arguments> {
            private var identifier: String? = null
            private var name: String? = null
            private var duration: Duration? = null

            fun setIdentifier(identifier: String): Builder = apply { this.identifier = identifier }

            fun setName(name: String): Builder = apply { this.name = name }

            fun setDuration(duration: Duration): Builder = apply { this.duration = duration }

            override fun build(): Arguments = Arguments(identifier, name, duration)
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

            fun setExecutionStatus(executionStatus: ExecutionStatus): Builder = apply {
                this.executionStatus = executionStatus
            }

            fun build(): Output = Output(executionStatus)
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
            var status: String = ""
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

    sealed interface ExecutionSession : BaseExecutionSession<Arguments, Output>
    class Confirmation internal constructor()

    companion object {
        private val ACTION_SPEC =
            ActionSpecBuilder.ofCapabilityNamed(CAPABILITY_NAME)
                .setArguments(Arguments::class.java, Arguments::Builder)
                .setOutput(Output::class.java)
                .bindParameter(
                    SlotMetadata.IDENTIFIER.path,
                    Arguments.Builder::setIdentifier,
                    TypeConverters.STRING_PARAM_VALUE_CONVERTER
                )
                .bindParameter(
                    SlotMetadata.NAME.path,
                    Arguments.Builder::setName,
                    TypeConverters.STRING_PARAM_VALUE_CONVERTER
                )
                .bindParameter(
                    SlotMetadata.DURATION.path,
                    Arguments.Builder::setDuration,
                    TypeConverters.DURATION_PARAM_VALUE_CONVERTER
                )
                .bindOutput(
                    "executionStatus",
                    Output::executionStatus,
                    ExecutionStatus::toParamValue
                )
                .build()
    }
}

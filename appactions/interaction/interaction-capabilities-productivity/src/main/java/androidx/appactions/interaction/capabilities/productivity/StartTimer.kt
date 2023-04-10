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

import androidx.appactions.interaction.capabilities.core.Capability
import androidx.appactions.interaction.capabilities.core.BaseSession
import androidx.appactions.interaction.capabilities.core.SessionFactory
import androidx.appactions.interaction.capabilities.core.ValueListener
import androidx.appactions.interaction.capabilities.core.impl.BuilderOf
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters
import androidx.appactions.interaction.capabilities.core.impl.spec.ActionSpecBuilder
import androidx.appactions.interaction.capabilities.core.properties.StringValue
import androidx.appactions.interaction.capabilities.core.properties.Property
import androidx.appactions.interaction.capabilities.core.impl.task.SessionBridge
import androidx.appactions.interaction.capabilities.core.impl.task.TaskHandler
import androidx.appactions.interaction.capabilities.core.values.GenericErrorStatus
import androidx.appactions.interaction.capabilities.core.values.SuccessStatus
import androidx.appactions.interaction.proto.ParamValue
import androidx.appactions.interaction.protobuf.Struct
import androidx.appactions.interaction.protobuf.Value
import java.time.Duration
import java.util.Optional

/** StartTimer.kt in interaction-capabilities-productivity */
private const val CAPABILITY_NAME = "actions.intent.START_TIMER"

private val ACTION_SPEC =
    ActionSpecBuilder.ofCapabilityNamed(CAPABILITY_NAME)
        .setDescriptor(StartTimer.Properties::class.java)
        .setArguments(StartTimer.Arguments::class.java, StartTimer.Arguments::Builder)
        .setOutput(StartTimer.Output::class.java)
        .bindOptionalParameter(
            "timer.identifier",
            { property -> Optional.ofNullable(property.identifier) },
            StartTimer.Arguments.Builder::setIdentifier,
            TypeConverters.STRING_PARAM_VALUE_CONVERTER,
            TypeConverters.STRING_VALUE_ENTITY_CONVERTER,
        )
        .bindOptionalParameter(
            "timer.name",
            { property -> Optional.ofNullable(property.name) },
            StartTimer.Arguments.Builder::setName,
            TypeConverters.STRING_PARAM_VALUE_CONVERTER,
            TypeConverters.STRING_VALUE_ENTITY_CONVERTER,
        )
        .bindOptionalParameter(
            "timer.duration",
            { property -> Optional.ofNullable(property.duration) },
            StartTimer.Arguments.Builder::setDuration,
            TypeConverters.DURATION_PARAM_VALUE_CONVERTER,
            TypeConverters.DURATION_ENTITY_CONVERTER,
        )
        .bindOptionalOutput(
            "executionStatus",
            { output -> Optional.ofNullable(output.executionStatus) },
            StartTimer.ExecutionStatus::toParamValue,
        )
        .build()

private val SESSION_BRIDGE = SessionBridge<StartTimer.Session, StartTimer.Confirmation> {
        session ->
    val taskHandlerBuilder = TaskHandler.Builder<StartTimer.Confirmation>()
    session.nameListener?.let {
        taskHandlerBuilder.registerValueTaskParam(
            "timer.name",
            it,
            TypeConverters.STRING_PARAM_VALUE_CONVERTER,
        )
    }
    session.durationListener?.let {
        taskHandlerBuilder.registerValueTaskParam(
            "timer.duration",
            it,
            TypeConverters.DURATION_PARAM_VALUE_CONVERTER,
        )
    }
    taskHandlerBuilder.build()
}

// TODO(b/267806701): Add capability factory annotation once the testing library is fully migrated.
class StartTimer private constructor() {

    class CapabilityBuilder :
        Capability.Builder<
            CapabilityBuilder, Properties, Arguments, Output, Confirmation, Session,
            >(ACTION_SPEC) {

        override val sessionBridge: SessionBridge<Session, Confirmation> = SESSION_BRIDGE

        public override fun setSessionFactory(
            sessionFactory: SessionFactory<Session>,
        ): CapabilityBuilder = super.setSessionFactory(sessionFactory)

        override fun build(): Capability {
            super.setProperty(Properties.Builder().build())
            return super.build()
        }
    }

    interface Session : BaseSession<Arguments, Output> {
        val nameListener: ValueListener<String>?
            get() = null
        val durationListener: ValueListener<Duration>?
            get() = null
    }

    // TODO(b/268369632): Remove Property from public capability APIs.
    class Properties
    internal constructor(
        val identifier: Property<StringValue>?,
        val name: Property<StringValue>?,
        val duration: Property<Duration>?,
    ) {
        override fun toString(): String {
            return "Property(identifier=$identifier,name=$name,duration=$duration}"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Properties

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

        class Builder {
            private var identifier: Property<StringValue>? = null
            private var name: Property<StringValue>? = null
            private var duration: Property<Duration>? = null

            fun setIdentifier(identifier: Property<StringValue>): Builder = apply {
                this.identifier = identifier
            }

            fun setName(name: Property<StringValue>): Builder = apply { this.name = name }

            fun setDuration(duration: Property<Duration>): Builder = apply {
                this.duration = duration
            }

            fun build(): Properties = Properties(identifier, name, duration)
        }
    }

    class Arguments internal constructor(
        val identifier: String?,
        val name: String?,
        val duration: Duration?,
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
                    Struct.newBuilder().putFields(TypeConverters.FIELD_NAME_TYPE, value).build(),
                )
                .build()
        }
    }

    class Confirmation internal constructor()
    }

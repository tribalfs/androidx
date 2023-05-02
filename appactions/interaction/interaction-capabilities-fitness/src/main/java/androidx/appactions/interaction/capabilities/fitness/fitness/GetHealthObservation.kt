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

package androidx.appactions.interaction.capabilities.fitness.fitness

import androidx.appactions.interaction.capabilities.core.BaseExecutionSession
import androidx.appactions.interaction.capabilities.core.Capability
import androidx.appactions.interaction.capabilities.core.CapabilityFactory
import androidx.appactions.interaction.capabilities.core.impl.BuilderOf
import androidx.appactions.interaction.capabilities.core.impl.converters.TypeConverters
import androidx.appactions.interaction.capabilities.core.impl.spec.ActionSpecBuilder
import androidx.appactions.interaction.capabilities.core.properties.Property
import java.time.LocalTime
import java.util.Optional

/** GetHealthObservation.kt in interaction-capabilities-fitness */
private const val CAPABILITY_NAME = "actions.intent.START_EXERCISE"

// TODO(b/273602015): Update to use Name property from builtintype library.
@Suppress("UNCHECKED_CAST")
private val ACTION_SPEC =
    ActionSpecBuilder.ofCapabilityNamed(CAPABILITY_NAME)
        .setArguments(
            GetHealthObservation.Arguments::class.java,
            GetHealthObservation.Arguments::Builder
        )
        .setOutput(GetHealthObservation.Output::class.java)
        .bindOptionalParameter(
            "healthObservation.startTime",
            { properties ->
                Optional.ofNullable(
                    properties[GetHealthObservation.PropertyMapStrings.START_TIME.key]
                        as Property<LocalTime>
                )
            },
            GetHealthObservation.Arguments.Builder::setStartTime,
            TypeConverters.LOCAL_TIME_PARAM_VALUE_CONVERTER,
            TypeConverters.LOCAL_TIME_ENTITY_CONVERTER
        )
        .bindOptionalParameter(
            "healthObservation.endTime",
            { properties ->
                Optional.ofNullable(
                    properties[GetHealthObservation.PropertyMapStrings.END_TIME.key]
                        as Property<LocalTime>
                )
            },
            GetHealthObservation.Arguments.Builder::setEndTime,
            TypeConverters.LOCAL_TIME_PARAM_VALUE_CONVERTER,
            TypeConverters.LOCAL_TIME_ENTITY_CONVERTER
        )
        .build()

@CapabilityFactory(name = CAPABILITY_NAME)
class GetHealthObservation private constructor() {
    internal enum class PropertyMapStrings(val key: String) {
        START_TIME("healthObservation.startTime"),
        END_TIME("healthObservation.endTime"),
    }

    class CapabilityBuilder :
        Capability.Builder<
            CapabilityBuilder,
            Arguments,
            Output,
            Confirmation,
            ExecutionSession
            >(ACTION_SPEC) {
        private var properties = mutableMapOf<String, Property<*>>()

        fun setStartTime(startTime: Property<LocalTime>): CapabilityBuilder =
            apply { properties[PropertyMapStrings.START_TIME.key] = startTime }

        fun setEndTime(endTime: Property<LocalTime>): CapabilityBuilder =
            apply { properties[PropertyMapStrings.END_TIME.key] = endTime }

        override fun build(): Capability {
            super.setProperty(properties)
            return super.build()
        }
    }

    class Arguments internal constructor(
        val startTime: LocalTime?,
        val endTime: LocalTime?
    ) {
        override fun toString(): String {
            return "Arguments(startTime=$startTime, endTime=$endTime)"
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass !== other?.javaClass) return false

            other as Arguments

            if (startTime != other.startTime) return false
            if (endTime != other.endTime) return false

            return true
        }

        override fun hashCode(): Int {
            var result = startTime.hashCode()
            result += 31 * endTime.hashCode()
            return result
        }

        class Builder : BuilderOf<Arguments> {
            private var startTime: LocalTime? = null
            private var endTime: LocalTime? = null

            fun setStartTime(startTime: LocalTime): Builder =
                apply { this.startTime = startTime }

            fun setEndTime(endTime: LocalTime): Builder =
                apply { this.endTime = endTime }

            override fun build(): Arguments = Arguments(startTime, endTime)
        }
    }

    class Output internal constructor()

    class Confirmation internal constructor()

    sealed interface ExecutionSession : BaseExecutionSession<Arguments, Output>
}

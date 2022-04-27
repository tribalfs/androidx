/*
 * Copyright (C) 2022 The Android Open Source Project
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

package androidx.health.services.client.data

import androidx.health.services.client.proto.DataProto
import java.time.Instant

/** Represents a user's health event. */
public class HealthEvent(
    /** Gets the type of event. */
    public val type: Type,

    /** Returns the time of the health event. */
    public val eventTime: Instant,

    /** Gets metrics associated to the event. */
    public val metrics: Map<DataType, List<DataPoint>>,
) {

    /** Health event types. */
    public class Type private constructor(public val id: Int, public val name: String) {

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Type) return false
            if (id != other.id) return false

            return true
        }

        override fun hashCode(): Int = id

        override fun toString(): String = name

        internal fun toProto(): DataProto.HealthEvent.HealthEventType =
            DataProto.HealthEvent.HealthEventType.forNumber(id)
                ?: DataProto.HealthEvent.HealthEventType.HEALTH_EVENT_TYPE_UNKNOWN

        public companion object {
            /**
             * The Health Event is unknown, or is represented by a value too new for this library
             * version to parse.
             */
            @JvmField
            public val UNKNOWN: Type = Type(0, "UNKNOWN")

            /** Health Event signifying the device detected that the user fell. */
            @JvmField
            public val FALL_DETECTED: Type = Type(3, "FALL_DETECTED")

            @JvmField
            internal val VALUES: List<Type> = listOf(UNKNOWN, FALL_DETECTED)

            internal fun fromProto(proto: DataProto.HealthEvent.HealthEventType): Type =
                VALUES.firstOrNull { it.id == proto.number } ?: UNKNOWN
        }
    }

    internal constructor(
        proto: DataProto.HealthEvent
    ) : this(
        Type.fromProto(proto.type),
        Instant.ofEpochMilli(proto.eventTimeEpochMs),
        proto
            .metricsList
            .map { entry -> DataType(entry.dataType) to entry.dataPointsList.map { DataPoint(it) } }
            .toMap()
    )

    internal val proto: DataProto.HealthEvent by lazy {
        DataProto.HealthEvent.newBuilder()
            .setType(type.toProto())
            .setEventTimeEpochMs(eventTime.toEpochMilli())
            .addAllMetrics(
                metrics
                    .map { entry ->
                        DataProto.HealthEvent.MetricsEntry.newBuilder()
                            .setDataType(entry.key.proto)
                            .addAllDataPoints(entry.value.map { it.proto })
                            .build()
                    }
                    .sortedBy { it.dataType.name } // Required to ensure equals() works
            )
            .build()
    }
}
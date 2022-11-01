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
package androidx.health.connect.client.records

import androidx.annotation.IntDef
import androidx.annotation.RestrictTo
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset

/**
 * Captures pause or rest events within an exercise. Each record contains the start / stop time of
 * the event.
 *
 * For pause events, resume state can be assumed from the end time of the pause or rest event.
 */
public class ExerciseEventRecord(
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    /**
     * Type of event. Required field. Allowed values: [EventType].
     *
     * @see EventType
     */
    @property:EventTypes public val eventType: Int,
    override val metadata: Metadata = Metadata.EMPTY,
) : IntervalRecord {

    init {
        require(startTime.isBefore(endTime)) { "startTime must be before endTime." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ExerciseEventRecord) return false

        if (eventType != other.eventType) return false
        if (startTime != other.startTime) return false
        if (startZoneOffset != other.startZoneOffset) return false
        if (endTime != other.endTime) return false
        if (endZoneOffset != other.endZoneOffset) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = eventType.hashCode()
        result = 31 * result + (startZoneOffset?.hashCode() ?: 0)
        result = 31 * result + endTime.hashCode()
        result = 31 * result + (endZoneOffset?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }

    /**
     * Types of exercise event. They can be either explicitly requested by a user or auto-detected
     * by a tracking app.
     */
    internal object EventType {
        /**
         * Explicit pause during a workout, requested by the user (by clicking a pause button in
         * the session UI). Movement happening during pause should not contribute to session
         * metrics.
         */
        const val PAUSE = "pause"
        /**
         * Auto-detected periods of rest during a workout. There should be no user movement
         * detected during rest and any movement detected should finish rest event.
         */
        const val REST = "rest"
    }
    /**
     * Types of exercise event. They can be either explicitly requested by a user or auto-detected
     * by a tracking app.
     *
     * @suppress
     */
    @Retention(AnnotationRetention.SOURCE)
    @IntDef(
        value =
            [
                EVENT_TYPE_UNKNOWN,
                EVENT_TYPE_PAUSE,
                EVENT_TYPE_REST,
            ]
    )
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    annotation class EventTypes

    companion object {
        const val EVENT_TYPE_UNKNOWN = 0
        const val EVENT_TYPE_PAUSE = 1
        const val EVENT_TYPE_REST = 2

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @JvmField
        val EVENT_TYPE_STRING_TO_INT_MAP: Map<String, Int> =
            mapOf(
                EventType.PAUSE to EVENT_TYPE_PAUSE,
                EventType.REST to EVENT_TYPE_REST,
            )

        @RestrictTo(RestrictTo.Scope.LIBRARY)
        @JvmField
        val EVENT_TYPE_INT_TO_STRING_MAP = EVENT_TYPE_STRING_TO_INT_MAP.reverse()
    }
}

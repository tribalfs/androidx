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

import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

/**
 * Captures the user's length and type of sleep. Each record represents a time interval for a stage
 * of sleep.
 *
 * The start time of the record represents the start of the sleep stage and always needs to be
 * included. The timestamp represents the end of the sleep stage. Time intervals don't need to be
 * continuous but shouldn't overlap.
 *
 * Example code demonstrate how to read sleep session with stages:
 * @sample androidx.health.connect.client.samples.ReadSleepSessions
 *
 * When deleting a session, associated sleep stage records need to be deleted separately:
 * @sample androidx.health.connect.client.samples.DeleteSleepSession
 *
 * @see SleepStageRecord
 */
public class SleepSessionRecord(
    /** Title of the session. Optional field. */
    public val title: String? = null,
    /** Additional notes for the session. Optional field. */
    public val notes: String? = null,
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    override val metadata: Metadata = Metadata.EMPTY,
) : IntervalRecord {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SleepSessionRecord) return false

        if (title != other.title) return false
        if (notes != other.notes) return false
        if (startTime != other.startTime) return false
        if (startZoneOffset != other.startZoneOffset) return false
        if (endTime != other.endTime) return false
        if (endZoneOffset != other.endZoneOffset) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 0
        result = 31 * result + title.hashCode()
        result = 31 * result + notes.hashCode()
        result = 31 * result + (startZoneOffset?.hashCode() ?: 0)
        result = 31 * result + endTime.hashCode()
        result = 31 * result + (endZoneOffset?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }

    companion object {
        /**
         * Metric identifier to retrieve the total sleep session duration from
         * [androidx.health.connect.client.aggregate.AggregationResult].
         */
        @JvmField
        val SLEEP_DURATION_TOTAL: AggregateMetric<Duration> =
            AggregateMetric.durationMetric("SleepSession")
    }
}

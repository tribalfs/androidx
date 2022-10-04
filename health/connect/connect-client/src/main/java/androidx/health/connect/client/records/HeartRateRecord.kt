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
import androidx.health.connect.client.aggregate.AggregationResult
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset

/** Captures the user's heart rate. Each record represents a series of measurements. */
public class HeartRateRecord(
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    override val samples: List<Sample>,
    override val metadata: Metadata = Metadata.EMPTY,
) : SeriesRecord<HeartRateRecord.Sample> {

    init {
        require(!startTime.isAfter(endTime)) { "startTime must not be after endTime." }
    }

    /*
     * Generated by the IDE: Code -> Generate -> "equals() and hashCode()".
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeartRateRecord) return false

        if (startTime != other.startTime) return false
        if (startZoneOffset != other.startZoneOffset) return false
        if (endTime != other.endTime) return false
        if (endZoneOffset != other.endZoneOffset) return false
        if (samples != other.samples) return false
        if (metadata != other.metadata) return false

        return true
    }

    /*
     * Generated by the IDE: Code -> Generate -> "equals() and hashCode()".
     */
    override fun hashCode(): Int {
        var result = startTime.hashCode()
        result = 31 * result + (startZoneOffset?.hashCode() ?: 0)
        result = 31 * result + endTime.hashCode()
        result = 31 * result + (endZoneOffset?.hashCode() ?: 0)
        result = 31 * result + samples.hashCode()
        result = 31 * result + metadata.hashCode()
        return result
    }

    companion object {
        private const val HEART_RATE_TYPE_NAME = "HeartRateSeries"
        private const val BPM_FIELD_NAME = "bpm"

        /** Metric identifier to retrieve the average heart rate from [AggregationResult]. */
        @JvmField
        val BPM_AVG: AggregateMetric<Long> =
            AggregateMetric.longMetric(
                HEART_RATE_TYPE_NAME,
                AggregateMetric.AggregationType.AVERAGE,
                BPM_FIELD_NAME
            )

        /** Metric identifier to retrieve the minimum heart rate from [AggregationResult]. */
        @JvmField
        val BPM_MIN: AggregateMetric<Long> =
            AggregateMetric.longMetric(
                HEART_RATE_TYPE_NAME,
                AggregateMetric.AggregationType.MINIMUM,
                BPM_FIELD_NAME
            )

        /** Metric identifier to retrieve the maximum heart rate from [AggregationResult]. */
        @JvmField
        val BPM_MAX: AggregateMetric<Long> =
            AggregateMetric.longMetric(
                HEART_RATE_TYPE_NAME,
                AggregateMetric.AggregationType.MAXIMUM,
                BPM_FIELD_NAME
            )

        /**
         * Metric identifier to retrieve the number of heart rate measurements from
         * [AggregationResult].
         */
        @JvmField
        val MEASUREMENTS_COUNT: AggregateMetric<Long> =
            AggregateMetric.countMetric(HEART_RATE_TYPE_NAME)
    }

    /**
     * Represents a single measurement of the heart rate.
     *
     * @param time The point in time when the measurement was taken.
     * @param beatsPerMinute Heart beats per minute. Validation range: 1-300.
     *
     * @see HeartRateRecord
     */
    public class Sample(
        val time: Instant,
        @androidx.annotation.IntRange(from = 1, to = 300) val beatsPerMinute: Long,
    ) {

        init {
            requireNonNegative(value = beatsPerMinute, name = "beatsPerMinute")
        }

        /*
         * Generated by the IDE: Code -> Generate -> "equals() and hashCode()".
         */
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Sample) return false

            if (time != other.time) return false
            if (beatsPerMinute != other.beatsPerMinute) return false

            return true
        }

        /*
         * Generated by the IDE: Code -> Generate -> "equals() and hashCode()".
         */
        override fun hashCode(): Int {
            var result = time.hashCode()
            result = 31 * result + beatsPerMinute.hashCode()
            return result
        }
    }
}

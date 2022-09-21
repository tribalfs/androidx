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
import androidx.health.connect.client.units.Mass
import java.time.Instant
import java.time.ZoneOffset

/**
 * Captures the user's weight.
 *
 * See [Mass] for supported units.
 */
public class WeightRecord(
    /** User's weight in kilograms. Required field. Valid range: 0-1000 kilograms. */
    public val weight: Mass,
    override val time: Instant,
    override val zoneOffset: ZoneOffset?,
    override val metadata: Metadata = Metadata.EMPTY,
) : InstantaneousRecord {

    init {
        weight.requireNotLess(other = weight.zero(), name = "weight")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WeightRecord) return false

        if (weight != other.weight) return false
        if (time != other.time) return false
        if (zoneOffset != other.zoneOffset) return false
        if (metadata != other.metadata) return false

        return true
    }

    /*
     * Generated by the IDE: Code -> Generate -> "equals() and hashCode()".
     */
    override fun hashCode(): Int {
        var result = weight.hashCode()
        result = 31 * result + time.hashCode()
        result = 31 * result + (zoneOffset?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }

    /*
     * Generated by the IDE: Code -> Generate -> "equals() and hashCode()".
     */
    companion object {
        private const val WEIGHT_NAME = "Weight"
        private const val WEIGHT_FIELD = "weight"

        /**
         * Metric identifier to retrieve the average weight from
         * [androidx.health.connect.client.aggregate.AggregationResult].
         */
        @JvmField
        val WEIGHT_AVG: AggregateMetric<Mass> =
            AggregateMetric.doubleMetric(
                dataTypeName = WEIGHT_NAME,
                aggregationType = AggregateMetric.AggregationType.AVERAGE,
                fieldName = WEIGHT_FIELD,
                mapper = Mass::kilograms,
            )

        /**
         * Metric identifier to retrieve the minimum weight from
         * [androidx.health.connect.client.aggregate.AggregationResult].
         */
        @JvmField
        val WEIGHT_MIN: AggregateMetric<Mass> =
            AggregateMetric.doubleMetric(
                dataTypeName = WEIGHT_NAME,
                aggregationType = AggregateMetric.AggregationType.MINIMUM,
                fieldName = WEIGHT_FIELD,
                mapper = Mass::kilograms,
            )

        /**
         * Metric identifier to retrieve the maximum weight from
         * [androidx.health.connect.client.aggregate.AggregationResult].
         */
        @JvmField
        val WEIGHT_MAX: AggregateMetric<Mass> =
            AggregateMetric.doubleMetric(
                dataTypeName = WEIGHT_NAME,
                aggregationType = AggregateMetric.AggregationType.MAXIMUM,
                fieldName = WEIGHT_FIELD,
                mapper = Mass::kilograms,
            )
    }
}

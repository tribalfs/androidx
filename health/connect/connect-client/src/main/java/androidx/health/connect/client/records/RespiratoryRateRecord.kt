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

import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset

/**
 * Captures the user's respiratory rate. Each record represents a single instantaneous measurement.
 */
public class RespiratoryRateRecord(
    /** Respiratory rate in breaths per minute. Required field. Valid range: 0-1000. */
    public val rate: Double,
    override val time: Instant,
    override val zoneOffset: ZoneOffset?,
    override val metadata: Metadata = Metadata.EMPTY,
) : InstantaneousRecord {
    init {
        requireNonNegative(value = rate, name = "rate")
        rate.requireNotMore(other = 100.0, name = "rate")
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RespiratoryRateRecord) return false

        if (rate != other.rate) return false
        if (time != other.time) return false
        if (zoneOffset != other.zoneOffset) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 0
        result = 31 * result + rate.hashCode()
        result = 31 * result + time.hashCode()
        result = 31 * result + (zoneOffset?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }
}

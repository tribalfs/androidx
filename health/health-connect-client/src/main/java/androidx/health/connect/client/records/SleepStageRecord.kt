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

import androidx.annotation.RestrictTo
import androidx.annotation.StringDef
import androidx.health.connect.client.records.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset

/**
 * Captures the sleep stage the user entered during a sleep session.
 *
 * @see SleepSessionRecord
 */
public class SleepStageRecord(
    /**
     * Type of sleep stage. Required field. Allowed values: [StageType].
     *
     * @see StageType
     */
    @property:StageTypes public val stage: String,
    override val startTime: Instant,
    override val startZoneOffset: ZoneOffset?,
    override val endTime: Instant,
    override val endZoneOffset: ZoneOffset?,
    override val metadata: Metadata = Metadata.EMPTY,
) : IntervalRecord {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SleepStageRecord) return false

        if (stage != other.stage) return false
        if (startTime != other.startTime) return false
        if (startZoneOffset != other.startZoneOffset) return false
        if (endTime != other.endTime) return false
        if (endZoneOffset != other.endZoneOffset) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 0
        result = 31 * result + stage.hashCode()
        result = 31 * result + (startZoneOffset?.hashCode() ?: 0)
        result = 31 * result + endTime.hashCode()
        result = 31 * result + (endZoneOffset?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }

    /** Type of sleep stage. */
    public object StageType {
        const val UNKNOWN = "unknown"
        const val AWAKE = "awake"
        const val SLEEPING = "sleeping"
        const val OUT_OF_BED = "out_of_bed"
        const val LIGHT = "light"
        const val DEEP = "deep"
        const val REM = "rem"
    }

    /**
     * Type of sleep stage.
     * @suppress
     */
    @Retention(AnnotationRetention.SOURCE)
    @StringDef(
        value =
            [
                StageType.UNKNOWN,
                StageType.AWAKE,
                StageType.SLEEPING,
                StageType.OUT_OF_BED,
                StageType.LIGHT,
                StageType.DEEP,
                StageType.REM,
            ]
    )
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    annotation class StageTypes
}

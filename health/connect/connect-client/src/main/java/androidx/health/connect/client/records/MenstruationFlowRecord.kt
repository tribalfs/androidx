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
 * Captures a description of how heavy a user's menstrual flow was (spotting, light, medium, or
 * heavy). Each record represents a description of how heavy the user's menstrual bleeding was.
 */
public class MenstruationFlowRecord(
    /**
     * How heavy the user's menstrual flow was. Optional field. Allowed values: [Flow].
     *
     * @see Flow
     */
    @property:Flows public val flow: String? = null,
    override val time: Instant,
    override val zoneOffset: ZoneOffset?,
    override val metadata: Metadata = Metadata.EMPTY,
) : InstantaneousRecord {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MenstruationFlowRecord) return false

        if (flow != other.flow) return false
        if (time != other.time) return false
        if (zoneOffset != other.zoneOffset) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 0
        result = 31 * result + flow.hashCode()
        result = 31 * result + time.hashCode()
        result = 31 * result + (zoneOffset?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }

    /** How heavy the user's menstruation flow was. */
    public object Flow {
        const val SPOTTING = "spotting"
        const val LIGHT = "light"
        const val MEDIUM = "medium"
        const val HEAVY = "heavy"
    }

    /**
     * How heavy the user's menstruation flow was.
     * @suppress
     */
    @Retention(AnnotationRetention.SOURCE)
    @StringDef(
        value =
            [
                MenstruationFlowRecord.Flow.SPOTTING,
                MenstruationFlowRecord.Flow.LIGHT,
                MenstruationFlowRecord.Flow.MEDIUM,
                MenstruationFlowRecord.Flow.HEAVY,
            ]
    )
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    annotation class Flows
}

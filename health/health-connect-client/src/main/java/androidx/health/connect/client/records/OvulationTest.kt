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
import androidx.health.connect.client.metadata.Metadata
import java.time.Instant
import java.time.ZoneOffset

/** Each record represents the binary result of an ovulation test (positive or negative). */
public class OvulationTest(
    /**
     * The result of a user's ovulation test, which shows if they're ovulating or not. Required
     * field. Allowed values: [Results].
     */
    @property:Results public val result: String,
    override val time: Instant,
    override val zoneOffset: ZoneOffset?,
    override val metadata: Metadata = Metadata.EMPTY,
) : InstantaneousRecord {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is OvulationTest) return false

        if (result != other.result) return false
        if (time != other.time) return false
        if (zoneOffset != other.zoneOffset) return false
        if (metadata != other.metadata) return false

        return true
    }

    override fun hashCode(): Int {
        var result = 0
        result = 31 * result + result.hashCode()
        result = 31 * result + time.hashCode()
        result = 31 * result + (zoneOffset?.hashCode() ?: 0)
        result = 31 * result + metadata.hashCode()
        return result
    }

    /** The result of a user's ovulation test, which shows if they're ovulating or not. */
    object Result {
        const val NEGATIVE = "negative"
        const val POSITIVE = "positive"
    }

    /**
     * The result of a user's ovulation test, which shows if they're ovulating or not.
     * @suppress
     */
    @Retention(AnnotationRetention.SOURCE)
    @StringDef(
        value =
            [
                Result.NEGATIVE,
                Result.POSITIVE,
            ]
    )
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    annotation class Results
}

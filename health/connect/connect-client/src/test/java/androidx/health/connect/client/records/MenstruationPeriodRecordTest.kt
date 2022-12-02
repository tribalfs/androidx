/*
 * Copyright 2022 The Android Open Source Project
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import kotlin.test.assertFailsWith
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MenstruationPeriodRecordTest {

    @Test
    fun validRecord_equals() {
        assertThat(
                MenstruationPeriodRecord(
                    startTime = Instant.ofEpochMilli(1234L),
                    startZoneOffset = null,
                    endTime = Instant.ofEpochMilli(1236L),
                    endZoneOffset = null,
                )
            )
            .isEqualTo(
                MenstruationPeriodRecord(
                    startTime = Instant.ofEpochMilli(1234L),
                    startZoneOffset = null,
                    endTime = Instant.ofEpochMilli(1236L),
                    endZoneOffset = null,
                )
            )
    }

    @Test
    fun endTimeEqualsStartTime_throws() {
        assertFailsWith<IllegalArgumentException> {
            MenstruationPeriodRecord(
                startTime = Instant.ofEpochMilli(1234L),
                startZoneOffset = null,
                endTime = Instant.ofEpochMilli(1234L),
                endZoneOffset = null,
            )
        }
    }

    @Test
    fun endTimeBeforeStartTime_throws() {
        assertFailsWith<IllegalArgumentException> {
            MenstruationPeriodRecord(
                startTime = Instant.ofEpochMilli(1234L),
                startZoneOffset = null,
                endTime = Instant.ofEpochMilli(1233L),
                endZoneOffset = null,
            )
        }
    }

    @Test
    fun periodExceeds31Days_throws() {
        assertFailsWith<IllegalArgumentException> {
            MenstruationPeriodRecord(
                startTime = Instant.parse("2022-01-01T00:00:00.00Z"),
                startZoneOffset = null,
                endTime = Instant.parse("2022-02-01T00:00:00.001Z"),
                endZoneOffset = null,
            )
        }
    }

    @Test
    fun period31Days_doesNotThrow() {
        MenstruationPeriodRecord(
            startTime = Instant.parse("2022-01-01T00:00:00.00Z"),
            startZoneOffset = null,
            endTime = Instant.parse("2022-02-01T00:00:00.000Z"),
            endZoneOffset = null,
        )
    }
}

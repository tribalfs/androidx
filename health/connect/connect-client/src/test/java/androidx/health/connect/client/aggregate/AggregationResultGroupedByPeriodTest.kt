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
package androidx.health.connect.client.aggregate

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.IllegalArgumentException
import java.time.LocalDateTime
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AggregationResultGroupedByPeriodTest {
    @Test
    fun constructor_endTimeNotAfterStartTime_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            AggregationResultGroupedByPeriod(
                result = AggregationResult(mapOf(), mapOf(), setOf()),
                startTime = LocalDateTime.parse("2022-02-22T20:22:02"),
                endTime = LocalDateTime.parse("2022-02-11T20:22:02"),
            )
        }

        assertThrows(IllegalArgumentException::class.java) {
            AggregationResultGroupedByPeriod(
                result = AggregationResult(mapOf(), mapOf(), setOf()),
                startTime = LocalDateTime.parse("2022-02-11T20:22:02"),
                endTime = LocalDateTime.parse("2022-02-11T20:22:02"),
            )
        }

        AggregationResultGroupedByPeriod(
            result = AggregationResult(mapOf(), mapOf(), setOf()),
            startTime = LocalDateTime.parse("2022-02-11T20:22:02"),
            endTime = LocalDateTime.parse("2022-02-22T20:22:02"),
        )
    }
}

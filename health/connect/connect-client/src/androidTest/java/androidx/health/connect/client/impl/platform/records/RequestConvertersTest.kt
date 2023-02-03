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

package androidx.health.connect.client.impl.platform.records

import android.annotation.TargetApi
import android.health.connect.datatypes.DataOrigin as PlatformDataOrigin
import android.health.connect.datatypes.HeartRateRecord as PlatformHeartRateRecord
import android.health.connect.datatypes.NutritionRecord as PlatformNutritionRecord
import android.health.connect.datatypes.StepsRecord as PlatformStepsRecord
import android.health.connect.datatypes.WheelchairPushesRecord as PlatformWheelchairPushesRecord
import android.health.connect.TimeInstantRangeFilter
import android.os.Build
import androidx.health.connect.client.impl.platform.time.FakeTimeSource
import androidx.health.connect.client.impl.platform.time.SystemDefaultTimeSource
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.WheelchairPushesRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.request.AggregateGroupByDurationRequest
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.Period
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@SmallTest
@TargetApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
// Comment the SDK suppress to run on emulators lower than U.
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.UPSIDE_DOWN_CAKE, codeName = "UpsideDownCake")
class RequestConvertersTest {

    @Test
    fun readRecordsRequest_fromSdkToPlatform() {
        val sdkRequest =
            ReadRecordsRequest(
                StepsRecord::class,
                TimeRangeFilter.between(Instant.ofEpochMilli(123L), Instant.ofEpochMilli(456L)),
                setOf(DataOrigin("package1"), DataOrigin("package2")))

        with(sdkRequest.toPlatformRequest(SystemDefaultTimeSource)) {
            assertThat(recordType).isAssignableTo(PlatformStepsRecord::class.java)
            assertThat(dataOrigins)
                .containsExactly(
                    PlatformDataOrigin.Builder().setPackageName("package1").build(),
                    PlatformDataOrigin.Builder().setPackageName("package2").build())
        }
    }

    @Test
    fun timeRangeFilter_fromSdkToPlatform() {
        val sdkFilter =
            TimeRangeFilter.between(Instant.ofEpochMilli(123L), Instant.ofEpochMilli(456L))

        with(sdkFilter.toPlatformTimeRangeFilter(SystemDefaultTimeSource)) {
            assertThat(startTime).isEqualTo(Instant.ofEpochMilli(123L))
            assertThat(endTime).isEqualTo(Instant.ofEpochMilli(456L))
        }
    }

    @Test
    fun timeRangeFilter_fromSdkToPlatform_none() {
        val timeSource = FakeTimeSource()
        timeSource.now = Instant.ofEpochMilli(123L)

        val sdkFilter = TimeRangeFilter.none()

        with(sdkFilter.toPlatformTimeRangeFilter(timeSource)) {
            assertThat(startTime).isEqualTo(Instant.EPOCH)
            assertThat(endTime).isEqualTo(Instant.ofEpochMilli(123L))
        }
    }

    @Test
    fun changesTokenRequest_fromSdkToPlatform() {
        val sdkRequest =
            ChangesTokenRequest(
                setOf(StepsRecord::class, HeartRateRecord::class),
                setOf(DataOrigin("package1"), DataOrigin("package2")))

        with(sdkRequest.toPlatformRequest()) {
            assertThat(recordTypes)
                .containsExactly(
                    PlatformStepsRecord::class.java, PlatformHeartRateRecord::class.java)
            assertThat(dataOriginFilters)
                .containsExactly(
                    PlatformDataOrigin.Builder().setPackageName("package1").build(),
                    PlatformDataOrigin.Builder().setPackageName("package2").build())
        }
    }

    @Test
    fun aggregateRequest_fromSdkToPlatform() {
        val sdkRequest =
            AggregateRequest(
                setOf(StepsRecord.COUNT_TOTAL, NutritionRecord.CAFFEINE_TOTAL),
                TimeRangeFilter.between(Instant.ofEpochMilli(123L), Instant.ofEpochMilli(456L)),
                setOf(DataOrigin("package1")))

        with(sdkRequest.toPlatformRequest(SystemDefaultTimeSource)) {
            with(timeRangeFilter as TimeInstantRangeFilter) {
                assertThat(startTime).isEqualTo(Instant.ofEpochMilli(123L))
                assertThat(endTime).isEqualTo(Instant.ofEpochMilli(456L))
            }
            assertThat(aggregationTypes)
                .containsExactly(
                    PlatformStepsRecord.STEPS_COUNT_TOTAL, PlatformNutritionRecord.CAFFEINE_TOTAL)
            assertThat(dataOriginsFilters)
                .containsExactly(PlatformDataOrigin.Builder().setPackageName("package1").build())
        }
    }

    @Test
    fun aggregateGroupByDurationRequest_fromSdkToPlatform() {
        val sdkRequest =
            AggregateGroupByDurationRequest(
                setOf(NutritionRecord.ENERGY_TOTAL),
                TimeRangeFilter.between(Instant.ofEpochMilli(123L), Instant.ofEpochMilli(456L)),
                Duration.ofDays(1),
                setOf(DataOrigin("package1"), DataOrigin("package2")))

        with(sdkRequest.toPlatformRequest(SystemDefaultTimeSource)) {
            with(timeRangeFilter as TimeInstantRangeFilter) {
                assertThat(startTime).isEqualTo(Instant.ofEpochMilli(123L))
                assertThat(endTime).isEqualTo(Instant.ofEpochMilli(456L))
            }
            assertThat(aggregationTypes).containsExactly(PlatformNutritionRecord.ENERGY_TOTAL)
            assertThat(dataOriginsFilters)
                .containsExactly(
                    PlatformDataOrigin.Builder().setPackageName("package1").build(),
                    PlatformDataOrigin.Builder().setPackageName("package2").build())
        }
    }

    @Test
    fun aggregateGroupByPeriodRequest_fromSdkToPlatform() {
        val sdkRequest =
            AggregateGroupByPeriodRequest(
                setOf(HeartRateRecord.BPM_MAX, HeartRateRecord.BPM_MIN, HeartRateRecord.BPM_AVG),
                TimeRangeFilter.between(Instant.ofEpochMilli(123L), Instant.ofEpochMilli(456L)),
                Period.ofDays(1),
                setOf(DataOrigin("package1"), DataOrigin("package2"), DataOrigin("package3")))

        with(sdkRequest.toPlatformRequest(SystemDefaultTimeSource)) {
            with(timeRangeFilter as TimeInstantRangeFilter) {
                assertThat(startTime).isEqualTo(Instant.ofEpochMilli(123L))
                assertThat(endTime).isEqualTo(Instant.ofEpochMilli(456L))
            }
            assertThat(aggregationTypes)
                .containsExactly(
                    PlatformHeartRateRecord.BPM_MAX,
                    PlatformHeartRateRecord.BPM_MIN,
                    PlatformHeartRateRecord.BPM_AVG)
            assertThat(dataOriginsFilters)
                .containsExactly(
                    PlatformDataOrigin.Builder().setPackageName("package1").build(),
                    PlatformDataOrigin.Builder().setPackageName("package2").build(),
                    PlatformDataOrigin.Builder().setPackageName("package3").build())
        }
    }

    @Test
    fun aggregateMetric_fromSdkToPlatformAggregationType() {
        assertThat(WheelchairPushesRecord.COUNT_TOTAL.toAggregationType())
            .isEqualTo(PlatformWheelchairPushesRecord.WHEEL_CHAIR_PUSHES_COUNT_TOTAL)
        assertThat(NutritionRecord.ENERGY_TOTAL.toAggregationType())
            .isEqualTo(PlatformNutritionRecord.ENERGY_TOTAL)
    }

    @Test
    fun aggregateMetric_isLongAggregationType() {
        assertThat(WheelchairPushesRecord.COUNT_TOTAL.isLongAggregationType()).isTrue()
        assertThat(NutritionRecord.ENERGY_TOTAL.isLongAggregationType()).isFalse()
    }

    @Test
    fun aggregateMetric_isDoubleAggregationType() {
        assertThat(NutritionRecord.ENERGY_TOTAL.isDoubleAggregationType()).isTrue()
        assertThat(WheelchairPushesRecord.COUNT_TOTAL.isDoubleAggregationType()).isFalse()
    }
}

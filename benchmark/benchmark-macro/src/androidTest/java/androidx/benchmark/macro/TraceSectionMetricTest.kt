/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.benchmark.macro

import androidx.benchmark.perfetto.PerfettoHelper
import androidx.benchmark.perfetto.PerfettoTraceProcessor
import androidx.test.filters.MediumTest
import org.junit.Assume.assumeTrue
import org.junit.Test

@MediumTest
@OptIn(ExperimentalMetricApi::class)
class TraceSectionMetricTest {
    private val api24ColdStart = createTempFileFromAsset(
        prefix = "api24_startup_cold",
        suffix = ".perfetto-trace"
    ).absolutePath

    private val commasInSliceNames = createTempFileFromAsset(
        prefix = "api24_commas_in_slice_names",
        suffix = ".perfetto-trace"
    ).absolutePath

    @Test
    fun activityThreadMain() = verifyFirstSum(
        tracePath = api24ColdStart,
        packageName = Packages.TEST,
        sectionName = "ActivityThreadMain",
        expectedFirstMs = 12.639
    )

    @Test
    fun activityStart() = verifyFirstSum(
        tracePath = api24ColdStart,
        packageName = Packages.TEST,
        sectionName = "activityStart",
        expectedFirstMs = 81.979
    )

    @Test
    fun startActivityAndWait() = verifyFirstSum(
        tracePath = api24ColdStart,
        packageName = Packages.TEST,
        sectionName = "startActivityAndWait",
        expectedFirstMs = 1_110.689
    )

    @Test
    fun launching() = verifyFirstSum(
        tracePath = api24ColdStart,
        packageName = Packages.TEST,
        sectionName = "launching: androidx.benchmark.integration.macrobenchmark.target",
        expectedFirstMs = 269.947
    )

    @Test
    fun section1_2() = verifyFirstSum(
        tracePath = commasInSliceNames,
        packageName = Packages.TARGET,
        sectionName = "section1,2",
        expectedFirstMs = 0.006615
    )

    @Test
    fun multiSection() = verifyFirstSum(
        tracePath = api24ColdStart,
        packageName = Packages.TARGET,
        sectionName = "inflate",
        expectedFirstMs = 13.318, // first inflation
        expectedSumMs = 43.128, // total inflation
        expectedSumCount = 8,
    )

    companion object {
        private val captureInfo = Metric.CaptureInfo(
            targetPackageName = Packages.TEST,
            testPackageName = Packages.TEST,
            startupMode = StartupMode.COLD,
            apiLevel = 24
        )

        private fun verifyMetric(
            tracePath: String,
            packageName: String,
            sectionName: String,
            mode: TraceSectionMetric.Mode,
            expectedMs: Double,
            expectedCount: Int,
        ) {
            assumeTrue(PerfettoHelper.isAbiSupported())

            val metric = TraceSectionMetric(sectionName, mode)
            metric.configure(packageName = packageName)

            val result = PerfettoTraceProcessor.runSingleSessionServer(tracePath) {
                metric.getResult(
                    captureInfo = captureInfo,
                    traceSession = this
                )
            }

            var measurements = listOf(Metric.Measurement(sectionName + "Ms", expectedMs))

            if (mode == TraceSectionMetric.Mode.Sum) {
                measurements = measurements + listOf(
                    Metric.Measurement(sectionName + "Count", expectedCount.toDouble())
                )
            }

            assertEqualMeasurements(
                expected = measurements,
                observed = result,
                threshold = 0.001
            )
        }

        private fun verifyFirstSum(
            tracePath: String,
            packageName: String,
            sectionName: String,
            expectedFirstMs: Double,
            expectedSumMs: Double = expectedFirstMs, // default implies only one matching section
            expectedSumCount: Int = 1
        ) {
            verifyMetric(
                tracePath = tracePath,
                packageName = packageName,
                sectionName = sectionName,
                mode = TraceSectionMetric.Mode.First,
                expectedMs = expectedFirstMs,
                expectedCount = 1
            )
            verifyMetric(
                tracePath = tracePath,
                packageName = packageName,
                sectionName = sectionName,
                mode = TraceSectionMetric.Mode.Sum,
                expectedMs = expectedSumMs,
                expectedCount = expectedSumCount,
            )
        }
    }
}

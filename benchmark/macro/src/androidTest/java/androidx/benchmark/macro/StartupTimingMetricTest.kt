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

import android.content.Intent
import androidx.benchmark.macro.perfetto.PerfettoCaptureWrapper
import androidx.benchmark.macro.perfetto.PerfettoTraceProcessor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 29)
@RunWith(AndroidJUnit4::class)
class StartupTimingMetricTest {
    @LargeTest
    @Test
    fun noResults() {
        assumeTrue(PerfettoTraceProcessor.isAbiSupported())
        val packageName = "fake.package.fiction.nostartups"
        val metrics = measureStartup(packageName) {
            // Do nothing
        }
        assertEquals(metrics.isEmpty(), true)
    }

    @LargeTest
    @Test
    fun validateStartup() {
        assumeTrue(PerfettoTraceProcessor.isAbiSupported())
        val packageName = "androidx.benchmark.integration.macrobenchmark.target"
        val scope = MacrobenchmarkScope(packageName = packageName, launchWithClearTask = true)
        val metrics = measureStartup(packageName) {
            // Simulate a cold start
            scope.killProcess()
            scope.dropKernelPageCache()
            scope.pressHome()
            scope.startActivityAndWait {
                it.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                it.action =
                    "androidx.benchmark.integration.macrobenchmark.target.TRIVIAL_STARTUP_ACTIVITY"
            }
        }
        val hasStartupMetrics = "startupMs" in metrics
        assertEquals(hasStartupMetrics, true)
    }
}

fun measureStartup(packageName: String, measureBlock: () -> Unit): Map<String, Long> {
    val wrapper = PerfettoCaptureWrapper()
    val metric = StartupTimingMetric()
    metric.configure(packageName)
    wrapper.start()
    measureBlock()
    val tracePath = wrapper.stop(packageName, 1)!!
    return metric.getMetrics(packageName, tracePath)
}

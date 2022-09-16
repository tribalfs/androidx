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

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.benchmark.Outputs
import androidx.benchmark.macro.perfetto.PerfettoTraceProcessor
import androidx.benchmark.perfetto.PerfettoCaptureWrapper
import androidx.benchmark.perfetto.PerfettoHelper.Companion.isAbiSupported
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@SdkSuppress(minSdkVersion = 23)
@RunWith(AndroidJUnit4::class)
class StartupTimingMetricTest {
    @MediumTest
    @Test
    fun noResults() {
        assumeTrue(isAbiSupported())
        val packageName = "fake.package.fiction.nostartups"
        val iterationResult = measureStartup(packageName, StartupMode.COLD) {
            // Do nothing
        }
        assertEquals(true, iterationResult.singleMetrics.isEmpty())
    }

    @LargeTest
    @Test
    // Disabled pre-29, as other process may not have tracing for reportFullyDrawn pre-29, due to
    // lack of profileable. Within our test process (other startup tests in this class), we use
    // reflection to force reportFullyDrawn() to be traced. See b/182386956
    @SdkSuppress(minSdkVersion = 29)
    fun startup() {
        assumeTrue(isAbiSupported())
        val packageName = "androidx.benchmark.integration.macrobenchmark.target"
        val scope = MacrobenchmarkScope(packageName = packageName, launchWithClearTask = true)
        val iterationResult = measureStartup(packageName, StartupMode.COLD) {
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

        assertEquals(
            setOf("timeToInitialDisplayMs"),
            iterationResult.singleMetrics.keys
        )
        assertNotNull(iterationResult.timelineRangeNs)
    }

    /**
     * Validate that reasonable startup and fully drawn metrics are extracted, either from
     * startActivityAndWait, or from in-app Activity based navigation
     */
    private fun validateStartup_fullyDrawn(
        delayMs: Long,
        useInAppNav: Boolean = false
    ) {
        val awaitActivityText: (String) -> UiObject2 = { expectedText ->
            UiDevice
                .getInstance(InstrumentationRegistry.getInstrumentation())
                .wait(Until.findObject(By.text(expectedText)), 3000)!!
        }
        assumeTrue(isAbiSupported())

        val scope = MacrobenchmarkScope(packageName = Packages.TEST, launchWithClearTask = true)
        val launchIntent = ConfigurableActivity.createIntent(
            text = "ORIGINAL TEXT",
            reportFullyDrawnDelayMs = delayMs
        )
        // setup initial Activity if needed
        if (useInAppNav) {
            scope.startActivityAndWait(launchIntent)
            if (delayMs > 0) {
                awaitActivityText(ConfigurableActivity.FULLY_DRAWN_TEXT)
            }
        }

        // measure the activity launch
        val iterationResult = measureStartup(Packages.TEST, StartupMode.WARM) {
            // Simulate a warm start, since it's our own process
            if (useInAppNav) {
                // click the textview, which triggers an activity launch
                awaitActivityText(
                    if (delayMs > 0) {
                        ConfigurableActivity.FULLY_DRAWN_TEXT
                    } else {
                        "ORIGINAL TEXT"
                    }
                ).click()
            } else {
                scope.pressHome()
                scope.startActivityAndWait(launchIntent)
            }

            if (delayMs > 0) {
                awaitActivityText(ConfigurableActivity.FULLY_DRAWN_TEXT)
            } else if (useInAppNav) {
                awaitActivityText(ConfigurableActivity.INNER_ACTIVITY_TEXT)
            }
        }

        // validate
        assertEquals(
            setOf("timeToInitialDisplayMs", "timeToFullDisplayMs"),
            iterationResult.singleMetrics.keys
        )
        assertNotNull(iterationResult.timelineRangeNs)

        val timeToInitialDisplayMs = iterationResult.singleMetrics["timeToInitialDisplayMs"]!!
        val timeToFullDisplayMs = iterationResult.singleMetrics["timeToFullDisplayMs"]!!

        if (delayMs == 0L) {
            // since reportFullyDrawn is dispatched before startup is complete,
            // timeToInitialDisplay and timeToFullDisplay should match
            assertEquals(timeToFullDisplayMs, timeToInitialDisplayMs, 0.0001)
        } else {
            // expect to see at a gap of around delayMs or more between two metrics
            assertTrue(
                timeToFullDisplayMs > timeToInitialDisplayMs,
                "Didn't see full draw delayed after initial display: " +
                    "ttid $timeToInitialDisplayMs, ttfd $timeToFullDisplayMs"
            )
        }
        assertNotNull(iterationResult.timelineRangeNs)
    }

    @LargeTest
    @Test
    fun startup_fullyDrawn_immediate() {
        validateStartup_fullyDrawn(delayMs = 0)
    }

    @LargeTest
    @Test
    fun startup_fullyDrawn_delayed() {
        validateStartup_fullyDrawn(delayMs = 100)
    }

    @LargeTest
    @Test
    fun startupInAppNav_immediate() {
        validateStartup_fullyDrawn(delayMs = 0, useInAppNav = true)
    }

    @LargeTest
    @Test
    fun startupInAppNav_fullyDrawn() {
        validateStartup_fullyDrawn(delayMs = 100, useInAppNav = true)
    }

    private fun getApi32WarmMetrics(metric: Metric): IterationResult {
        assumeTrue(isAbiSupported())
        val traceFile = createTempFileFromAsset("api32_startup_warm", ".perfetto-trace")
        val packageName = "androidx.benchmark.integration.macrobenchmark.target"

        metric.configure(packageName)
        return PerfettoTraceProcessor.runServer(traceFile.absolutePath) {
            metric.getMetrics(
                captureInfo = Metric.CaptureInfo(
                    targetPackageName = "androidx.benchmark.integration.macrobenchmark.target",
                    testPackageName = "androidx.benchmark.integration.macrobenchmark.test",
                    startupMode = StartupMode.WARM,
                    apiLevel = 32
                ),
                perfettoTraceProcessor = this
            )
        }
    }

    @MediumTest
    @Test
    fun fixedStartupTraceMetricsReport_fullyDrawnBeforeFirstFrame() {
        assumeTrue(isAbiSupported())
        val traceFile = createTempFileFromAsset(
            prefix = "api24_startup_sameproc_immediatefullydrawn",
            suffix = ".perfetto-trace"
        )
        val metric = StartupTimingMetric()
        metric.configure(Packages.TEST)

        val metrics = PerfettoTraceProcessor.runServer(traceFile.absolutePath) {
            metric.getMetrics(
                captureInfo = Metric.CaptureInfo(
                    targetPackageName = Packages.TEST,
                    testPackageName = Packages.TEST,
                    startupMode = StartupMode.WARM,
                    apiLevel = 24
                ),
                perfettoTraceProcessor = this
            )
        }

        // check known values
        assertEquals(
            setOf("timeToInitialDisplayMs", "timeToFullDisplayMs"),
            metrics.singleMetrics.keys
        )
        assertEquals(178.58525, metrics.singleMetrics["timeToInitialDisplayMs"]!!, 0.0001)
        assertEquals(178.58525, metrics.singleMetrics["timeToFullDisplayMs"]!!, 0.0001)
        assertEquals(1680207215350..1680385800600, metrics.timelineRangeNs)
    }

    @MediumTest
    @Test
    fun fixedStartupTraceMetrics() {
        val metrics = getApi32WarmMetrics(StartupTimingMetric())

        // check known values
        assertEquals(
            setOf("timeToInitialDisplayMs", "timeToFullDisplayMs"),
            metrics.singleMetrics.keys
        )
        assertEquals(154.629883, metrics.singleMetrics["timeToInitialDisplayMs"]!!, 0.0001)
        assertEquals(659.641358, metrics.singleMetrics["timeToFullDisplayMs"]!!, 0.0001)
        assertEquals(157479786572825..157480446214183, metrics.timelineRangeNs)
    }

    @SuppressLint("NewApi") // suppressed for StartupTimingLegacyMetric, since data is fixed
    @MediumTest
    @Test
    fun fixedStartupTraceMetrics_legacy() {
        val metrics = getApi32WarmMetrics(StartupTimingLegacyMetric())

        // check known values
        assertEquals(setOf("startupMs", "fullyDrawnMs"), metrics.singleMetrics.keys)
        assertEquals(156.515747, metrics.singleMetrics["startupMs"]!!, 0.0001)
        assertEquals(644.613729, metrics.singleMetrics["fullyDrawnMs"]!!, 0.0001)
        assertEquals(157479786566030..157479943081777, metrics.timelineRangeNs)
    }
}

@RequiresApi(23)
internal fun measureStartup(
    packageName: String,
    startupMode: StartupMode,
    measureBlock: () -> Unit
): IterationResult {
    val metric = StartupTimingMetric()
    metric.configure(packageName)
    val tracePath = PerfettoCaptureWrapper().record(
        benchmarkName = packageName,
        // note - packageName may be this package, so we convert to set then list to make unique
        // and on API 23 and below, we use reflection to trace instead within this process
        appTagPackages = if (Build.VERSION.SDK_INT >= 24 && packageName != Packages.TEST) {
            listOf(packageName, Packages.TEST)
        } else {
            listOf(packageName)
        },
        userspaceTracingPackage = packageName,
        block = measureBlock
    )!!

    return PerfettoTraceProcessor.runServer(tracePath) {
        metric.getMetrics(
            captureInfo = Metric.CaptureInfo(
                targetPackageName = packageName,
                testPackageName = Packages.TEST,
                startupMode = startupMode,
                apiLevel = Build.VERSION.SDK_INT
            ),
            perfettoTraceProcessor = this
        )
    }
}

@Suppress("SameParameterValue")
internal fun createTempFileFromAsset(prefix: String, suffix: String): File {
    val file = File.createTempFile(prefix, suffix, Outputs.dirUsableByAppAndShell)
    InstrumentationRegistry
        .getInstrumentation()
        .context
        .assets
        .open(prefix + suffix)
        .copyTo(file.outputStream())
    return file
}

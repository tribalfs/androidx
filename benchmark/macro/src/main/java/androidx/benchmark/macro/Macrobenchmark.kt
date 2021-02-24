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
import android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE
import android.content.pm.PackageManager
import android.os.Build
import androidx.benchmark.Arguments
import androidx.benchmark.BenchmarkResult
import androidx.benchmark.InstrumentationResults
import androidx.benchmark.ResultWriter
import androidx.benchmark.macro.perfetto.PerfettoCaptureWrapper
import androidx.test.platform.app.InstrumentationRegistry

internal fun checkErrors(packageName: String): ConfigurationError.SuppressionState? {
    val pm = InstrumentationRegistry.getInstrumentation().context.packageManager

    val applicationInfo = try {
        pm.getApplicationInfo(packageName, 0)
    } catch (notFoundException: PackageManager.NameNotFoundException) {
        throw AssertionError(
            "Unable to find target package $packageName, is it installed?",
            notFoundException
        )
    }

    @SuppressLint("UnsafeNewApiCall")
    val errorNotProfileable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        !applicationInfo.isProfileableByShell
    } else {
        false
    }

    val errors = DeviceInfo.errors +
        // TODO: Merge this debuggable check / definition with Errors.kt in benchmark-common
        listOfNotNull(
            conditionalError(
                hasError = applicationInfo.flags.and(FLAG_DEBUGGABLE) != 0,
                id = "DEBUGGABLE",
                summary = "Benchmark Target is Debuggable",
                message = """
                    Target package $packageName
                    is running with debuggable=true, which drastically reduces
                    runtime performance in order to support debugging features. Run
                    benchmarks with debuggable=false. Debuggable affects execution speed
                    in ways that mean benchmark improvements might not carry over to a
                    real user's experience (or even regress release performance).
                """.trimIndent()
            ),
            conditionalError(
                hasError = errorNotProfileable,
                id = "NOT-PROFILEABLE",
                summary = "Benchmark Target is NOT profileable",
                message = """
                    Target package $packageName
                    is running without profileable. Profileable is required to enable
                    macrobenchmark to capture detailed trace information from the target process,
                    such as System tracing sections defined in the app, or libraries.

                    To make the target profileable, add the following in your target app's
                    main AndroidManifest.xml, within the application tag:

                    <!--suppress AndroidElementNotAllowed -->
                    <profileable android:shell="true"/>
                """.trimIndent()
            )
        ).sortedBy { it.id }

    return errors.checkAndGetSuppressionState(Arguments.suppressedErrors)
}

/**
 * macrobenchmark test entrypoint, which doesn't depend on JUnit.
 *
 * This function is a building block for public testing APIs
 */
internal fun macrobenchmark(
    uniqueName: String,
    className: String,
    testName: String,
    packageName: String,
    metrics: List<Metric>,
    compilationMode: CompilationMode = CompilationMode.SpeedProfile(),
    iterations: Int,
    launchWithClearTask: Boolean,
    setupBlock: MacrobenchmarkScope.(Boolean) -> Unit,
    measureBlock: MacrobenchmarkScope.() -> Unit
) {
    require(iterations > 0) {
        "Require iterations > 0 (iterations = $iterations)"
    }
    require(metrics.isNotEmpty()) {
        "Empty list of metrics passed to metrics param, must pass at least one Metric"
    }
    require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        "Macrobenchmark currently requires Android 10 (API 29) or greater."
    }

    val suppressionState = checkErrors(packageName)
    var warningMessage = suppressionState?.warningMessage ?: ""

    val startTime = System.nanoTime()
    val scope = MacrobenchmarkScope(packageName, launchWithClearTask)

    // always kill the process at beginning of test
    scope.killProcess()

    compilationMode.compile(packageName) {
        setupBlock(scope, false)
        measureBlock(scope)
    }

    // Perfetto collector is separate from metrics, so we can control file
    // output, and give it different (test-wide) lifecycle
    val perfettoCollector = PerfettoCaptureWrapper()
    try {
        metrics.forEach {
            it.configure(packageName)
        }
        var isFirstRun = true
        val metricResults = List(iterations) { iteration ->
            setupBlock(scope, isFirstRun)
            isFirstRun = false
            perfettoCollector.start()

            try {
                metrics.forEach {
                    it.start()
                }
                measureBlock(scope)
            } finally {
                metrics.forEach {
                    it.stop()
                }
            }
            val tracePath = perfettoCollector.stop(uniqueName, iteration)
            metrics
                // capture list of Map<String,Long> per metric
                .map { it.getMetrics(packageName, tracePath!!) }
                // merge into one map
                .reduce { sum, element -> sum + element }
        }.mergeToMetricResults()

        require(metricResults.isNotEmpty()) {
            """
                Unable to read any metrics during benchmark (metric list: $metrics).
                Check that you're performing the operations to be measured. For example, if
                using StartupTimingMetric, are you starting an activity for the specified package
                in the measure block?
            """.trimIndent()
        }
        InstrumentationResults.instrumentationReport {
            val statsList = metricResults.map { it.stats }
            ideSummaryRecord(ideSummaryString(warningMessage, uniqueName, statsList))
            warningMessage = "" // warning only printed once
            statsList.forEach { it.putInBundle(bundle, suppressionState?.prefix ?: "") }
        }

        val warmupIterations = if (compilationMode is CompilationMode.SpeedProfile) {
            compilationMode.warmupIterations
        } else {
            0
        }

        ResultWriter.appendReport(
            BenchmarkResult(
                className = className,
                testName = testName,
                totalRunTimeNs = System.nanoTime() - startTime,
                metrics = metricResults,
                repeatIterations = iterations,
                thermalThrottleSleepSeconds = 0,
                warmupIterations = warmupIterations
            )
        )
    } finally {
        scope.killProcess()
    }
}

fun macrobenchmarkWithStartupMode(
    uniqueName: String,
    className: String,
    testName: String,
    packageName: String,
    metrics: List<Metric>,
    compilationMode: CompilationMode = CompilationMode.SpeedProfile(),
    iterations: Int,
    startupMode: StartupMode?,
    setupBlock: MacrobenchmarkScope.() -> Unit,
    measureBlock: MacrobenchmarkScope.() -> Unit
) {
    macrobenchmark(
        uniqueName = uniqueName,
        className = className,
        testName = testName,
        packageName = packageName,
        metrics = metrics,
        compilationMode = compilationMode,
        iterations = iterations,
        setupBlock = { firstIterationAfterCompile ->
            if (startupMode == StartupMode.COLD) {
                killProcess()
                // drop app pages from page cache to ensure it is loaded from disk, from scratch
                dropKernelPageCache()
            } else if (startupMode != null && firstIterationAfterCompile) {
                // warmup process by running the measure block once unmeasured
                measureBlock()
            }
            setupBlock(this)
        },
        // Don't reuse activities by default in COLD / WARM
        launchWithClearTask = startupMode == StartupMode.COLD || startupMode == StartupMode.WARM,
        measureBlock = measureBlock
    )
}

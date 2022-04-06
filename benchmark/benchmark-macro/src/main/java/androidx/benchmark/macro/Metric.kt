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
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.benchmark.Shell
import androidx.benchmark.macro.PowerRail.hasMetrics
import androidx.benchmark.macro.perfetto.AudioUnderrunQuery
import androidx.benchmark.macro.perfetto.EnergyQuery
import androidx.benchmark.macro.perfetto.FrameTimingQuery
import androidx.benchmark.macro.perfetto.FrameTimingQuery.SubMetric
import androidx.benchmark.macro.perfetto.PerfettoResultsParser.parseStartupResult
import androidx.benchmark.macro.perfetto.PerfettoTraceProcessor
import androidx.benchmark.macro.perfetto.PowerQuery
import androidx.benchmark.macro.perfetto.StartupTimingQuery
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Metric interface.
 */
public sealed class Metric {
    internal abstract fun configure(packageName: String)

    internal abstract fun start()

    internal abstract fun stop()
    /**
     * After stopping, collect metrics
     *
     * TODO: takes package for package level filtering, but probably want a
     *  general config object coming into [start].
     */
    internal abstract fun getMetrics(captureInfo: CaptureInfo, tracePath: String): IterationResult

    internal data class CaptureInfo(
        val apiLevel: Int,
        val targetPackageName: String,
        val testPackageName: String,
        val startupMode: StartupMode?
    )
}

private fun Long.nsToDoubleMs(): Double = this / 1_000_000.0

/**
 * Metric which captures information about underruns while playing audio.
 *
 * Each time an instance of [android.media.AudioTrack] is started, the systems repeatedly
 * logs the number of audio frames available for output. This doesn't work when audio offload is
 * enabled. No logs are generated while there is no active track. See
 * [android.media.AudioTrack.Builder.setOffloadedPlayback] for more details.
 *
 * Test fails in case of multiple active tracks during a single iteration.
 *
 * This outputs the following measurements:
 *
 * * `audioTotalMs` - Total duration of played audio captured during the iteration.
 * The test fails if no counters are detected.
 *
 * * `audioUnderrunMs` - Duration of played audio when zero audio frames were available for output.
 * Each single log of zero frames available for output indicates a gap in audio playing.
 */
@ExperimentalMetricApi
@Suppress("CanSealedSubClassBeObject")
public class AudioUnderrunMetric : Metric() {
    internal override fun configure(packageName: String) {
    }

    internal override fun start() {
    }

    internal override fun stop() {
    }

    internal override fun getMetrics(captureInfo: CaptureInfo, tracePath: String): IterationResult {
        val subMetrics = AudioUnderrunQuery.getSubMetrics(tracePath)

        return IterationResult(
            singleMetrics = mapOf(
                "audioTotalMs" to subMetrics.totalMs.toDouble(),
                "audioUnderrunMs" to subMetrics.zeroMs.toDouble()
            ),
            sampledMetrics = emptyMap(),
            timelineRangeNs = null
        )
    }
}

/**
 * Legacy version of FrameTimingMetric, based on 'dumpsys gfxinfo' instead of trace data.
 *
 * Temporary - to be removed after transition to FrameTimingMetric
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class FrameTimingGfxInfoMetric : Metric() {
    private lateinit var packageName: String
    private val helper = JankCollectionHelper()

    internal override fun configure(packageName: String) {
        this.packageName = packageName
        helper.addTrackedPackages(packageName)
    }

    internal override fun start() {
        try {
            helper.startCollecting()
        } catch (exception: RuntimeException) {
            // Ignore the exception that might result from trying to clear GfxInfo
            // The current implementation of JankCollectionHelper throws a RuntimeException
            // when that happens. This is safe to ignore because the app being benchmarked
            // is not showing any UI when this happens typically.

            // Once the MacroBenchmarkRule has the ability to setup the app in the right state via
            // a designated setup block, we can get rid of this.
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            if (instrumentation != null) {
                if (!Shell.isPackageAlive(packageName)) {
                    error(exception.message ?: "Assertion error, $packageName not running")
                }
            }
        }
    }

    internal override fun stop() {
        helper.stopCollecting()
    }

    /**
     * Used to convert keys from platform to JSON format.
     *
     * This both converts `snake_case_format` to `camelCaseFormat`, and renames for clarity.
     *
     * Note that these will still output to inst results in snake_case, with `MetricNameUtils`
     * via [androidx.benchmark.MetricResult.putInBundle].
     */
    private val keyRenameMap = mapOf(
        "frame_render_time_percentile_50" to "frameTime50thPercentileMs",
        "frame_render_time_percentile_90" to "frameTime90thPercentileMs",
        "frame_render_time_percentile_95" to "frameTime95thPercentileMs",
        "frame_render_time_percentile_99" to "frameTime99thPercentileMs",
        "gpu_frame_render_time_percentile_50" to "gpuFrameTime50thPercentileMs",
        "gpu_frame_render_time_percentile_90" to "gpuFrameTime90thPercentileMs",
        "gpu_frame_render_time_percentile_95" to "gpuFrameTime95thPercentileMs",
        "gpu_frame_render_time_percentile_99" to "gpuFrameTime99thPercentileMs",
        "missed_vsync" to "vsyncMissedFrameCount",
        "deadline_missed" to "deadlineMissedFrameCount",
        "deadline_missed_legacy" to "deadlineMissedFrameCountLegacy",
        "janky_frames_count" to "jankyFrameCount",
        "janky_frames_legacy_count" to "jankyFrameCountLegacy",
        "high_input_latency" to "highInputLatencyFrameCount",
        "slow_ui_thread" to "slowUiThreadFrameCount",
        "slow_bmp_upload" to "slowBitmapUploadFrameCount",
        "slow_issue_draw_cmds" to "slowIssueDrawCommandsFrameCount",
        "total_frames" to "totalFrameCount",
        "janky_frames_percent" to "jankyFramePercent",
        "janky_frames_legacy_percent" to "jankyFramePercentLegacy"
    )

    /**
     * Filters output to only frameTimeXXthPercentileMs and totalFrameCount
     */
    private val keyAllowList = setOf(
        "frameTime50thPercentileMs",
        "frameTime90thPercentileMs",
        "frameTime95thPercentileMs",
        "frameTime99thPercentileMs",
        "totalFrameCount"
    )

    internal override fun getMetrics(captureInfo: CaptureInfo, tracePath: String) = IterationResult(
        singleMetrics = helper.metrics
            .map {
                val prefix = "gfxinfo_${packageName}_"
                val keyWithoutPrefix = it.key.removePrefix(prefix)

                if (keyWithoutPrefix != it.key && keyRenameMap.containsKey(keyWithoutPrefix)) {
                    keyRenameMap[keyWithoutPrefix]!! to it.value
                } else {
                    throw IllegalStateException("Unexpected key ${it.key}")
                }
            }
            .toMap()
            .filterKeys { keyAllowList.contains(it) },
        sampledMetrics = emptyMap(),
        timelineRangeNs = null
    )
}

/**
 * Metric which captures timing information from frames produced by a benchmark, such as
 * a scrolling or animation benchmark.
 *
 * This outputs the following measurements:
 *
 * * `frameOverrunMs` (Requires API 29) - How much time a given frame missed its deadline by.
 * Positive numbers indicate a dropped frame and visible jank / stutter, negative numbers indicate
 * how much faster than the deadline a frame was.
 *
 * * `frameCpuTimeMs` - How much time the frame took to be produced on the CPU - on both the UI
 * Thread, and RenderThread.
 */
@Suppress("CanSealedSubClassBeObject")
public class FrameTimingMetric : Metric() {
    internal override fun configure(packageName: String) {}
    internal override fun start() {}
    internal override fun stop() {}

    @SuppressLint("SyntheticAccessor")
    internal override fun getMetrics(captureInfo: CaptureInfo, tracePath: String): IterationResult {
        val subMetricsMsMap = FrameTimingQuery.getFrameSubMetrics(
            absoluteTracePath = tracePath,
            captureApiLevel = Build.VERSION.SDK_INT,
            packageName = captureInfo.targetPackageName
        )
            .filterKeys { it == SubMetric.FrameDurationCpuNs || it == SubMetric.FrameOverrunNs }
            .mapKeys {
                if (it.key == SubMetric.FrameDurationCpuNs) {
                    "frameDurationCpuMs"
                } else {
                    "frameOverrunMs"
                }
            }
            .mapValues { entry ->
                entry.value.map { timeNs -> timeNs.nsToDoubleMs() }
            }
        return IterationResult(
            singleMetrics = emptyMap(),
            sampledMetrics = subMetricsMsMap,
            timelineRangeNs = null
        )
    }
}

/**
 * Captures app startup timing metrics.
 *
 * This outputs the following measurements:
 *
 * * `timeToInitialDisplayMs` - Time from the system receiving a launch intent to rendering the
 * first frame of the destination Activity.
 *
 * * `timeToFullDisplayMs` - Time from the system receiving a launch intent until the application
 * reports fully drawn via [android.app.Activity.reportFullyDrawn]. The measurement stops at the
 * completion of rendering the first frame after (or containing) the `reportFullyDrawn()` call. This
 * measurement may not be available prior to API 29.
 */
@Suppress("CanSealedSubClassBeObject")
public class StartupTimingMetric : Metric() {
    internal override fun configure(packageName: String) {
    }

    internal override fun start() {
    }

    internal override fun stop() {
    }

    @SuppressLint("SyntheticAccessor")
    internal override fun getMetrics(captureInfo: CaptureInfo, tracePath: String): IterationResult {
        return StartupTimingQuery.getFrameSubMetrics(
            absoluteTracePath = tracePath,
            captureApiLevel = captureInfo.apiLevel,
            targetPackageName = captureInfo.targetPackageName,
            testPackageName = captureInfo.testPackageName,

            // Pick an arbitrary startup mode if unspecified. In the future, consider throwing an
            // error if startup mode not defined
            startupMode = captureInfo.startupMode ?: StartupMode.COLD
        )?.run {
            @Suppress("UNCHECKED_CAST")
            IterationResult(
                singleMetrics = mapOf(
                    "timeToInitialDisplayMs" to timeToInitialDisplayNs.nsToDoubleMs(),
                    "timeToFullDisplayMs" to timeToFullDisplayNs?.nsToDoubleMs()
                ).filterValues { it != null } as Map<String, Double>,
                sampledMetrics = emptyMap(),
                timelineRangeNs = timelineRangeNs
            )
        } ?: IterationResult.EMPTY
    }
}

/**
 * Captures app startup timing metrics.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
@Suppress("CanSealedSubClassBeObject")
@RequiresApi(29)
public class StartupTimingLegacyMetric : Metric() {
    internal override fun configure(packageName: String) {
    }

    internal override fun start() {
    }

    internal override fun stop() {
    }

    internal override fun getMetrics(captureInfo: CaptureInfo, tracePath: String): IterationResult {
        val json = PerfettoTraceProcessor.getJsonMetrics(tracePath, "android_startup")
        return parseStartupResult(json, captureInfo.targetPackageName)
    }
}

/**
 * Captures the time taken by a trace section - a named begin / end pair matching the provided name.
 *
 * Always selects the first instance of a trace section captured during a measurement.
 *
 * @see androidx.tracing.Trace.beginSection
 * @see androidx.tracing.Trace.endSection
 * @see androidx.tracing.trace
 */
@RequiresApi(29) // Remove once b/182386956 fixed, as app tag may be needed for this to work.
@ExperimentalMetricApi
public class TraceSectionMetric(
    private val sectionName: String
) : Metric() {
    internal override fun configure(packageName: String) {
    }

    internal override fun start() {
    }

    internal override fun stop() {
    }

    @SuppressLint("SyntheticAccessor")
    internal override fun getMetrics(captureInfo: CaptureInfo, tracePath: String): IterationResult {
        val slice = PerfettoTraceProcessor.querySlices(tracePath, sectionName).firstOrNull()
        return if (slice == null) {
            IterationResult.EMPTY
        } else IterationResult(
            singleMetrics = mapOf(
                sectionName + "Ms" to slice.dur / 1_000_000.0
            ),
            sampledMetrics = emptyMap(),
            timelineRangeNs = slice.ts..slice.endTs
        )
    }
}
/**
 * Captures the change of power rails metrics over time for specified duration.
 *
 * This outputs measurements like the following:
 *
 * ```
 * odpmEnergyRailsCpuBigUws     min        99,545.0,    median       110,339.0,  max      316,444.0
 * odpmEnergyRailsAocLogicUws   min        81,548.0,    median       86,211.0,   max      87,650.0
 * ```
 *
 * * `name` - The name of the subsystem associated with the energy usage in camel case.
 *
 * * `energy` - The change in swpower usage over the course of the power test, measured in uWs.
 *
 * The outputs are stored in the format `odpmEnergy<name>Uws`.
 *
 * This measurement is not available prior to API 29.
 */
@RequiresApi(29)
@Suppress("CanSealedSubClassBeObject")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class EnergyMetric : Metric() {

    internal companion object {
        internal const val MEASURE_BLOCK_SECTION_NAME = "measureBlock"
    }

    internal override fun configure(packageName: String) {
         hasMetrics(throwOnMissingMetrics = true)
    }

    internal override fun start() {}

    internal override fun stop() {}

    internal override fun getMetrics(captureInfo: CaptureInfo, tracePath: String): IterationResult {
        // collect metrics between trace point flags
        val slice = PerfettoTraceProcessor.querySlices(tracePath, MEASURE_BLOCK_SECTION_NAME)
            .firstOrNull()
            ?: return IterationResult.EMPTY

        val metrics = EnergyQuery.getEnergyMetrics(tracePath, slice)
        val metricMap = mutableMapOf<String, Double>()
        for (metric in metrics) {
            metricMap["odpmEnergy${metric.name}Uws"] = metric.energyUws
        }
        return IterationResult(
            singleMetrics = metricMap,
            sampledMetrics = emptyMap())
    }
}
/**
 * Captures the change of power rails metrics over time for specified duration.  All rails under
 * the same subsystem are added together for the total energy consumed in each subsystem.
 *
 * This outputs measurements like the following:
 *
 * ```
 * odpmTotalEnergyDdrUws      min        107,087.0,   median        133,942.0,  max       135,084.0
 * odpmTotalEnergyAocUws      min        81,548.0,    median        86,211.0,   max       87,650.0
 * ```
 *
 * * `name` - The name of the subsystem associated with the energy usage in camel case.
 *
 * * `energy` - The change in swpower usage over the course of the power test, measured in uWs.
 *
 * The outputs are stored in the format `odpmTotalEnergy<name>Uws`.
 *
 * This measurement is not available prior to API 29.
 */
@RequiresApi(29)
@Suppress("CanSealedSubClassBeObject")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class TotalEnergyMetric : Metric() {

    internal companion object {
        internal const val MEASURE_BLOCK_SECTION_NAME = "measureBlock"
    }

    internal override fun configure(packageName: String) {
        hasMetrics(throwOnMissingMetrics = true)
    }

    internal override fun start() {}

    internal override fun stop() {}

    internal override fun getMetrics(captureInfo: CaptureInfo, tracePath: String): IterationResult {
        // collect metrics between trace point flags
        val slice = PerfettoTraceProcessor.querySlices(tracePath, MEASURE_BLOCK_SECTION_NAME)
            .firstOrNull()
            ?: return IterationResult.EMPTY

        val metrics = EnergyQuery.getTotalEnergyMetrics(tracePath, slice)
        val metricMap = mutableMapOf<String, Double>()
        for (metric in metrics) {
            metricMap["odpmTotalEnergy${metric.name}Uws"] = metric.energyUws
        }
        return IterationResult(
            singleMetrics = metricMap,
            sampledMetrics = emptyMap())
    }
}

/**
 * Captures the change of power rails metrics over time for specified duration.
 *
 * This outputs measurements like the following:
 *
 * ```
 * odpmPowerRailsCpuBigUw       min        22.1,        median        22.6,       max       67.6
 * odpmPowerRailsAocLogicUw     min        17.9,        median        18.1,       max       18.3
 * ```
 *
 * * `name` - The name of the subsystem associated with the power usage in camel case.
 *
 * * `power` - The energy used divided by the elapsed time, measured in mW.
 *
 * The outputs are stored in the format `odpmPower<name>Uw`.
 *
 * This measurement is not available prior to API 29.
 */
@RequiresApi(29)
@Suppress("CanSealedSubClassBeObject")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class PowerMetric : Metric() {

    internal companion object {
        internal const val MEASURE_BLOCK_SECTION_NAME = "measureBlock"
    }

    internal override fun configure(packageName: String) {
        hasMetrics(throwOnMissingMetrics = true)
    }

    internal override fun start() {}

    internal override fun stop() {}

    internal override fun getMetrics(captureInfo: CaptureInfo, tracePath: String): IterationResult {
        // collect metrics between trace point flags
        val slice = PerfettoTraceProcessor.querySlices(tracePath, MEASURE_BLOCK_SECTION_NAME)
            .firstOrNull()
            ?: return IterationResult.EMPTY

        val metrics = PowerQuery.getPowerMetrics(tracePath, slice)
        val metricMap = mutableMapOf<String, Double>()
        for (metric in metrics) {
            metricMap["odpmPower${metric.name}Uw"] = metric.powerUs
        }
        return IterationResult(
            singleMetrics = metricMap,
            sampledMetrics = emptyMap())
    }
}

/**
 * Captures the change of power rails metrics over time for specified duration. All rails under
 * the same subsystem are added together for the total power consumed in each subsystem.
 *
 * This outputs measurements like the following:
 *
 * ```
 * odpmTotalPowerDisplayUw    min        138.5,       median        140.0,      max       140.6
 * odpmTotalPowerAocUw        min        17.9,        median        18.1,       max       18.3
 * ```
 *
 * * `name` - The name of the subsystem associated with the power usage in camel case.
 *
 * * `power` - The energy used divided by the elapsed time, measured in mW.
 *
 * The outputs are stored in the format `odpmTotalPower<name>Uw`.
 *
 * This measurement is not available prior to API 29.
 */
@RequiresApi(29)
@Suppress("CanSealedSubClassBeObject")
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP_PREFIX)
public class TotalPowerMetric : Metric() {

    internal companion object {
        internal const val MEASURE_BLOCK_SECTION_NAME = "measureBlock"
    }

    internal override fun configure(packageName: String) {
        hasMetrics(throwOnMissingMetrics = true)
    }

    internal override fun start() {}

    internal override fun stop() {}

    internal override fun getMetrics(captureInfo: CaptureInfo, tracePath: String): IterationResult {
        // collect metrics between trace point flags
        val slice = PerfettoTraceProcessor.querySlices(tracePath, MEASURE_BLOCK_SECTION_NAME)
            .firstOrNull()
            ?: return IterationResult.EMPTY

        val metrics = PowerQuery.getTotalPowerMetrics(tracePath, slice)
        val metricMap = mutableMapOf<String, Double>()
        for (metric in metrics) {
            metricMap["odpmTotalPower${metric.name}Uw"] = metric.powerUs
        }
        return IterationResult(
            singleMetrics = metricMap,
            sampledMetrics = emptyMap())
    }
}

/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.benchmark

import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.benchmark.simpleperf.ProfileSession
import androidx.benchmark.simpleperf.RecordOptions

/**
 * Profiler abstraction used for the timing stage.
 *
 * Controlled externally by `androidx.benchmark.profiling.mode`
 * Subclasses are objects, as these generally refer to device or process global state. For
 * example, things like whether the simpleperf process is running, or whether the runtime is
 * capturing method trace.
 *
 * Note: flags on this class would be simpler if we either had a 'Default'/'Noop' profiler, or a
 * wrapper extension function (e.g. `fun Profiler? .requiresSingleMeasurementIteration`). We
 * avoid these however, in order to avoid the runtime visiting a new class in the hot path, when
 * switching from warmup -> timing phase, when [start] would be called.
 */
internal sealed class Profiler {
    abstract fun start(traceUniqueName: String)
    abstract fun stop()

    /**
     * Measure exactly one loop (one repeat, one iteration).
     *
     * Generally only set for tracing profilers.
     */
    open val requiresSingleMeasurementIteration = false

    /**
     * Generally only set for sampling profilers.
     */
    open val requiresExtraRuntime = false

    /**
     * Currently, debuggable is required to support studio-connected profiling.
     *
     * Remove this once stable Studio supports profileable.
     */
    open val requiresDebuggable = false

    /**
     * Connected modes don't need dir, since library isn't doing the capture.
     */
    open val requiresLibraryOutputDir = true

    companion object {
        const val CONNECTED_PROFILING_SLEEP_MS = 20_000L

        fun getByName(name: String): Profiler? = mapOf(
            "MethodSampling" to MethodSampling,
            "MethodTracing" to MethodTracing,

            "ConnectedAllocation" to ConnectedAllocation,
            "ConnectedSampling" to ConnectedSampling,

            "MethodSamplingSimpleperf" to MethodSamplingSimpleperf,

            // Below are compat codepaths for old names. Remove before 1.1 stable.
            "Method" to MethodTracing,
            "Sampled" to MethodSampling,
            "ConnectedSampled" to ConnectedSampling
        )
            .mapKeys { it.key.toLowerCase() }[name.toLowerCase()]
    }
}

internal fun startRuntimeMethodTracing(traceFileName: String, sampled: Boolean) {
    val path = Arguments.testOutputFile(traceFileName).absolutePath

    Log.d(BenchmarkState.TAG, "Profiling output file: $path")
    InstrumentationResults.reportAdditionalFileToCopy("profiling_trace", path)

    val bufferSize = 16 * 1024 * 1024
    if (sampled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
    ) {
        Debug.startMethodTracingSampling(path, bufferSize, Arguments.profilerSampleFrequency)
    } else {
        Debug.startMethodTracing(path, bufferSize, 0)
    }
}

internal fun stopRuntimeMethodTracing() {
    Debug.stopMethodTracing()
}

internal object MethodSampling : Profiler() {
    override fun start(traceUniqueName: String) {
        startRuntimeMethodTracing(
            traceFileName = "$traceUniqueName-methodSampling.trace",
            sampled = true
        )
    }

    override fun stop() {
        stopRuntimeMethodTracing()
    }

    override val requiresExtraRuntime: Boolean = true
}

internal object MethodTracing : Profiler() {
    override fun start(traceUniqueName: String) {
        startRuntimeMethodTracing(
            traceFileName = "$traceUniqueName-methodTracing.trace",
            sampled = false
        )
    }

    override fun stop() {
        stopRuntimeMethodTracing()
    }

    override val requiresSingleMeasurementIteration: Boolean = true
}

internal object ConnectedAllocation : Profiler() {
    override fun start(traceUniqueName: String) {
        Thread.sleep(CONNECTED_PROFILING_SLEEP_MS)
    }

    override fun stop() {
        Thread.sleep(CONNECTED_PROFILING_SLEEP_MS)
    }

    override val requiresSingleMeasurementIteration: Boolean = true
    override val requiresDebuggable: Boolean = true
    override val requiresLibraryOutputDir: Boolean = false
}

internal object ConnectedSampling : Profiler() {
    override fun start(traceUniqueName: String) {
        Thread.sleep(CONNECTED_PROFILING_SLEEP_MS)
    }

    override fun stop() {
        Thread.sleep(CONNECTED_PROFILING_SLEEP_MS)
    }

    override val requiresDebuggable: Boolean = true
    override val requiresLibraryOutputDir: Boolean = false
}

internal object MethodSamplingSimpleperf : Profiler() {
    @RequiresApi(28)
    private var session: ProfileSession? = null

    @RequiresApi(28)
    override fun start(traceUniqueName: String) {
        session?.stopRecording() // stop previous
        session = ProfileSession().also {
            it.startRecording(
                RecordOptions()
                    .setSampleFrequency(Arguments.profilerSampleFrequency)
                    .recordDwarfCallGraph() // enable Java/Kotlin callstacks
                    .traceOffCpu() // track time sleeping
                    .setOutputFilename("$traceUniqueName.data")
            )
        }
    }

    @RequiresApi(28)
    override fun stop() {
        session!!.stopRecording()
        session = null
    }

    override val requiresLibraryOutputDir: Boolean = false
}
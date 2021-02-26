/*
 * Copyright 2019 The Android Open Source Project
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

import android.os.Bundle
import android.util.Log
import androidx.annotation.RestrictTo
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * This allows tests to override arguments from code
 *
 * @suppress
 */
@RestrictTo(RestrictTo.Scope.TESTS)
public var argumentSource: Bundle? = null

@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
public object Arguments {
    // public properties are shared by micro + macro benchmarks
    public val suppressedErrors: Set<String>
    public val outputDirectoryPath: String

    // internal properties are microbenchmark only
    internal val outputEnable: Boolean
    internal val startupMode: Boolean
    internal val dryRunMode: Boolean
    internal val profiler: Profiler?
    internal val profilerSampleFrequency: Int
    internal val profilerSampleDurationSeconds: Long

    internal var error: String? = null

    private val testOutputDir: File

    private const val prefix = "androidx.benchmark."

    private fun Bundle.getBenchmarkArgument(key: String, defaultValue: String? = null) =
        getString(prefix + key, defaultValue)

    private fun Bundle.getProfiler(outputIsEnabled: Boolean): Profiler? {
        val argumentName = "profiling.mode"
        val argumentValue = getBenchmarkArgument(argumentName, "")
        val profiler = Profiler.getByName(argumentValue)
        if (profiler == null && argumentValue.isNotEmpty()) {
            error = "Could not parse $prefix$argumentName=$argumentValue"
            return null
        }
        if (profiler?.requiresLibraryOutputDir == true && !outputIsEnabled) {
            error = "Output is not enabled, so cannot profile with mode $argumentValue"
            return null
        }
        return profiler
    }

    // note: initialization may happen at any time
    init {
        val arguments = argumentSource ?: InstrumentationRegistry.getArguments()

        dryRunMode = arguments.getBenchmarkArgument("dryRunMode.enable")?.toBoolean() ?: false

        startupMode = !dryRunMode &&
            (arguments.getBenchmarkArgument("startupMode.enable")?.toBoolean() ?: false)

        outputEnable = !dryRunMode &&
            (arguments.getBenchmarkArgument("output.enable")?.toBoolean() ?: true)

        // Transform comma-delimited list into set of suppressed errors
        // E.g. "DEBUGGABLE, UNLOCKED" -> setOf("DEBUGGABLE", "UNLOCKED")
        suppressedErrors = arguments.getBenchmarkArgument("suppressErrors", "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        profiler = arguments.getProfiler(outputEnable)
        profilerSampleFrequency =
            arguments.getBenchmarkArgument("profiling.sampleFrequency")?.ifBlank { null }
            ?.toInt()
            ?: 10000
        profilerSampleDurationSeconds =
            arguments.getBenchmarkArgument("profiling.sampleDurationSeconds")?.ifBlank { null }
            ?.toLong()
            ?: 5

        if (profiler != null) {
            Log.d(
                BenchmarkState.TAG,
                "Profiler ${profiler.javaClass.simpleName}, freq " +
                    "$profilerSampleFrequency, duration $profilerSampleDurationSeconds"
            )
        }

        val additionalTestOutputDir = arguments.getString("additionalTestOutputDir")
        testOutputDir = additionalTestOutputDir?.let { File(it) }
            ?: InstrumentationRegistry.getInstrumentation().context.externalCacheDir
            ?: throw IllegalStateException(
                "Unable to read externalCacheDir for writing files, " +
                    "additionalTestOutputDir argument required to declare output dir."
            )

        outputDirectoryPath = testOutputDir.path
    }

    public fun testOutputFile(filename: String): File {
        return File(testOutputDir, filename)
    }
}
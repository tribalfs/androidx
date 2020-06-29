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
import android.os.Environment
import androidx.annotation.RestrictTo
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * This allows tests to override arguments from code
 *
 * @suppress
 */
@RestrictTo(RestrictTo.Scope.TESTS)
var argumentSource: Bundle? = null

internal object Arguments {
    val testOutputDir: File
    val outputEnable: Boolean
    val startupMode: Boolean
    val dryRunMode: Boolean
    val suppressedErrors: Set<String>
    val profiler: Profiler?

    var error: String? = null

    val prefix = "androidx.benchmark."

    private fun Bundle.getArgument(key: String, defaultValue: String = "") =
        getString(prefix + key, defaultValue)

    private fun Bundle.getProfiler(outputIsEnabled: Boolean): Profiler? {
        val argumentName = "profiling.mode"
        val argumentValue = getArgument(argumentName)
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

        dryRunMode = arguments.getArgument("dryRunMode.enable")?.toBoolean() ?: false

        startupMode = !dryRunMode &&
                (arguments.getArgument("startupMode.enable")?.toBoolean() ?: false)

        outputEnable = !dryRunMode &&
                (arguments.getArgument("output.enable")?.toBoolean() ?: false)

        // Transform comma-delimited list into set of suppressed errors
        // E.g. "DEBUGGABLE, UNLOCKED" -> setOf("DEBUGGABLE", "UNLOCKED")
        suppressedErrors = arguments.getArgument("suppressErrors", "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        profiler = arguments.getProfiler(outputEnable)

        val additionalTestOutputDir = arguments.getString("additionalTestOutputDir")
        @Suppress("DEPRECATION") // Legacy code path for versions of agp older than 3.6
        testOutputDir = additionalTestOutputDir?.let { File(it) }
            ?: Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }
}
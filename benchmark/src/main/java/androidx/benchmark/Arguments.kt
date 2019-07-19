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
import androidx.test.platform.app.InstrumentationRegistry

// This allows tests to override arguments from code
internal var ArgumentSource: Bundle? = null

internal object Arguments {
    val outputEnable: Boolean
    val suppressedErrors: Set<String>

    init {
        val prefix = "androidx.benchmark"
        val arguments = ArgumentSource ?: InstrumentationRegistry.getArguments()
        outputEnable = arguments.getString("$prefix.output.enable")?.toBoolean() ?: false

        // Transform comma-delimited list into set of suppressed errors
        // E.g. "DEBUGGABLE, UNLOCKED" -> setOf("DEBUGGABLE", "UNLOCKED")
        suppressedErrors = arguments.getString("androidx.benchmark.suppressErrors", "")
            .split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }
}
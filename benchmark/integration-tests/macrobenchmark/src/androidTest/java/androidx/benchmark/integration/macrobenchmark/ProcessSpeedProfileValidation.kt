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

package androidx.benchmark.integration.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.MacrobenchmarkConfig
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.macrobenchmark
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@SdkSuppress(minSdkVersion = 29)
@RunWith(Parameterized::class)
class ProcessSpeedProfileValidation(
    private val compilationMode: CompilationMode,
    private val killProcess: Boolean
) {
    @Test
    fun start() {
        val benchmarkName = "speed_profile_process_validation"
        val config = MacrobenchmarkConfig(
            packageName = PACKAGE_NAME,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            killProcessEachIteration = killProcess,
            iterations = 10
        )
        macrobenchmark(
            benchmarkName = benchmarkName,
            config = config
        ) {
            pressHome()
            launchPackageAndWait { launchIntent ->
                // Clear out any previous instances
                launchIntent.flags =
                    Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }
    }

    companion object {
        private const val PACKAGE_NAME = "androidx.benchmark.integration.macrobenchmark.target"

        @Parameterized.Parameters(name = "compilation_mode={0}, kill_process={1}")
        @JvmStatic
        fun kilProcessParameters(): List<Array<Any>> {
            val compilationModes = listOf(
                CompilationMode.None,
                CompilationMode.SpeedProfile(warmupIterations = 3)
            )
            val processKillOptions = listOf(true, false)
            return compilationModes.zip(processKillOptions).map {
                arrayOf(it.first, it.second)
            }
        }
    }
}

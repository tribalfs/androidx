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

package androidx.benchmark.macro.junit4

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.benchmark.Arguments
import androidx.benchmark.InstrumentationResults
import androidx.benchmark.macro.perfetto.PerfettoCapture
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Add this rule to record a Perfetto trace for each test on Q+ devices.
 *
 * Relies on either AGP's additionalTestOutputDir copying, or (in Jetpack CI),
 * `additionalTestOutputFile_***` copying.
 *
 * When invoked locally with Gradle, file will be copied to host path like the following:
 *
 * ```
 * out/androidx/benchmark/benchmark-macro/build/outputs/connected_android_test_additional_output/debugAndroidTest/connected/<deviceName>/androidx.mypackage.TestClass_testMethod.perfetto-trace
 * ```
 *
 * Note: if run from Studio, the file must be `adb pull`-ed manually, e.g.:
 * ```
 * > adb pull /storage/emulated/0/Android/data/androidx.mypackage.test/files/test_data/androidx.mypackage.TestClass_testMethod.trace
 * ```
 *
 * You can check logcat for messages tagged "PerfettoRule:" for the path of each perfetto trace.
 * ```
 * > adb pull /storage/emulated/0/Android/data/mypackage.test/files/PerfettoCaptureTest.trace
 * ```
 */
class PerfettoRule : TestRule {
    override fun apply(base: Statement, description: Description) = object : Statement() {
        override fun evaluate() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val traceName = "${description.className}_${description.methodName}.perfetto-trace"
                PerfettoCapture().recordAndReportFile(traceName) {
                    base.evaluate()
                }
            } else {
                Log.d(TAG, "Perfetto trace skipped due to API level (${Build.VERSION.SDK_INT})")
                base.evaluate()
            }
        }
    }

    companion object {
        internal const val TAG = "PerfettoRule"
    }
}

@RequiresApi(Build.VERSION_CODES.Q)
internal fun PerfettoCapture.recordAndReportFile(traceName: String, block: () -> Unit) {
    try {
        Log.d(PerfettoRule.TAG, "Recording perfetto trace $traceName")
        start()
        block()
        val destinationPath = Arguments.testOutputFile(traceName).absolutePath
        stop(destinationPath)
        Log.d(PerfettoRule.TAG, "Finished recording to $destinationPath")
        InstrumentationResults.reportAdditionalFileToCopy("perfetto_trace", destinationPath)
    } finally {
        cancel()
    }
}

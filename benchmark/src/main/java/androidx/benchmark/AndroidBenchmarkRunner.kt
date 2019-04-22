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

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import androidx.test.runner.AndroidJUnitRunner

/**
 * Instrumentation runner for benchmarks, used to increase stability of measurements and minimize
 * interference.
 *
 * To use this runner, put the following in your module level `build.gradle`:
 *
 * ```
 * android {
 *     defaultConfig {
 *         testInstrumentationRunner "androidx.benchmark.AndroidBenchmarkRunner"
 *     }
 * }
 * ```
 *
 * ## Minimizing Interference
 *
 * This runner launches a simple opaque activity used to reduce benchmark interference from other
 * windows. Launching other activities is supported e.g. via ActivityTestRule and ActivityScenario -
 * the opaque activity will be relaunched if not actively running before each test, and after each
 * test's cleanup is complete.
 *
 * For example, sources of potential interference:
 * - live wallpaper rendering
 * - homescreen widget updates
 * - hotword detection
 * - status bar repaints
 * - running in background (some cores may be foreground-app only)
 *
 * ## Clock Stability
 *
 * While it is better for performance stability to lock clocks with the `./gradlew lockClocks` task
 * provided by the gradle plugin, this is not possible on most devices. The runner provides a
 * fallback mode for preventing thermal throttling.
 *
 * On devices that support [android.view.Window.setSustainedPerformanceMode], the runner will set
 * this mode on the window of every Activity launched (including the opaque Activity mentioned
 * above). The runner will also launch a continuously spinning Thread. Together, these ensure that
 * the app runs in the multithreaded stable performance mode, which locks the maximum clock
 * frequency to prevent thermal throttling. This ensures stable clock levels across all benchmarks,
 * even if a continuous suite of benchmarks runs for many minutes on end.
 */
@Suppress("unused") // Note: not referenced by code
class AndroidBenchmarkRunner : AndroidJUnitRunner() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)

        if (Clocks.lockState == Clocks.LockState.SUSTAINED_PERFORMANCE_MODE) {
            // Keep at least one core busy. Together with a single threaded benchmark, this makes
            // the process get multi-threaded setSustainedPerformanceMode.
            //
            // We want to keep to the relatively lower clocks of the multi-threaded benchmark mode
            // to avoid any benchmarks running at higher clocks than any others.
            //
            // Note, thread names have 15 char max in Systrace
            object : Thread("BenchSpinThread") {
                override fun run() {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_LOWEST)
                    while (true) {}
                }
            }.start()
        }
    }

    override fun callActivityOnStart(activity: Activity) {
        super.callActivityOnStart(activity)

        @SuppressLint("NewApi") // window API guarded by [Clocks.lockState]
        if (Clocks.lockState == Clocks.LockState.SUSTAINED_PERFORMANCE_MODE) {
            activity.window.setSustainedPerformanceMode(true)
        }
    }

    override fun waitForActivitiesToComplete() {
        // We don't call the super method here, since we have
        // an activity we intend to persist between tests
        // TODO: somehow wait for every activity but IsolationActivity

        // Before/After each test, from the test thread, synchronously launch
        // our IsolationActivity if it's not already resumed
        var isResumed = false
        runOnMainSync {
            val activity = IsolationActivity.singleton.get()
            if (activity != null) {
                isResumed = activity.resumed
            }
        }
        if (!isResumed) {
            IsolationActivity.launchSingleton()
        }
    }

    override fun onDestroy() {
        IsolationActivity.finishSingleton()
        super.waitForActivitiesToComplete()
        super.onDestroy()
    }
}
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

package androidx.benchmark.macro

import android.os.Build
import android.util.Log
import androidx.benchmark.perfetto.PerfettoCapture
import androidx.benchmark.perfetto.destinationPath
import androidx.benchmark.perfetto.reportAdditionalFileToCopy

/**
 * Helps capture Perfetto traces.
 */
class PerfettoCollector(private val traceName: String) : Collector<Unit> {
    private var capture: PerfettoCapture? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            capture = PerfettoCapture()
        }
    }

    override fun start(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(TAG, "Recording perfetto trace $traceName")
            capture?.start()
        }
        return true
    }

    override fun stop(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val destination = destinationPath(traceName).absolutePath
            capture?.stop(destination)
            reportAdditionalFileToCopy("perfetto_trace", destination)
        }
        return true
    }

    override fun metrics(): Map<String, Unit> {
        return emptyMap()
    }

    companion object {
        private const val TAG = "PerfettoCollector"
    }
}

/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.wear.compose.material3.macrobenchmark.metric

import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.Metric
import androidx.benchmark.perfetto.PerfettoTraceProcessor

/** A copy from aosp/3328563 */
@ExperimentalMetricApi
@Suppress(
    "SEALED_INHERITOR_IN_DIFFERENT_MODULE",
    "SEALED_INHERITOR_IN_DIFFERENT_PACKAGE",
    "CANNOT_OVERRIDE_INVISIBLE_MEMBER",
    "OPT_IN_USAGE_ERROR"
)
class FrameCostMetric : Metric() {
    override fun getMeasurements(
        captureInfo: CaptureInfo,
        traceSession: PerfettoTraceProcessor.Session
    ): List<Measurement> {
        val costs =
            FrameCostQuery.getFrameCost(
                session = traceSession,
                packageName = captureInfo.targetPackageName
            )
        return listOf(Measurement("frameCost", costs))
    }
}

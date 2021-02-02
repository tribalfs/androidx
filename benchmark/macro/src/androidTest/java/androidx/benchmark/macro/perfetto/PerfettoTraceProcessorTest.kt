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

package androidx.benchmark.macro.perfetto

import androidx.benchmark.macro.device
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFailsWith

@SmallTest
@SdkSuppress(minSdkVersion = 29)
@RunWith(AndroidJUnit4::class)
class PerfettoTraceProcessorTest {
    @Test
    fun shellFile() {
        assumeTrue(PerfettoTraceProcessor.isAbiSupported())
        val shellFile = PerfettoTraceProcessor.shellFile.absolutePath
        val device = InstrumentationRegistry.getInstrumentation().device()
        val out = device.executeShellCommand("$shellFile --version")
        assertTrue(
            "expect to get Perfetto version string, saw: $out",
            out.contains("Perfetto v")
        )
    }

    @Test
    fun getJsonMetrics_tracePathWithSpaces() {
        assumeTrue(PerfettoTraceProcessor.isAbiSupported())
        assertFailsWith<IllegalArgumentException> {
            PerfettoTraceProcessor.getJsonMetrics("/a b", "ignored")
        }
    }

    @Test
    fun getJsonMetrics_metricWithSpaces() {
        assumeTrue(PerfettoTraceProcessor.isAbiSupported())
        assertFailsWith<IllegalArgumentException> {
            PerfettoTraceProcessor.getJsonMetrics("/ignored", "a b")
        }
    }

    @Test
    fun validateAbiNotSupportedBehavior() {
        assumeFalse(PerfettoTraceProcessor.isAbiSupported())
        assertFailsWith<IllegalStateException> {
            PerfettoTraceProcessor.shellFile
        }

        assertFailsWith<IllegalStateException> {
            PerfettoTraceProcessor.getJsonMetrics("ignored_path", "ignored_metric")
        }
    }
}
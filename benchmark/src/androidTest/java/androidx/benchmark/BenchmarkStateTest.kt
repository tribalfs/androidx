/*
 * Copyright 2018 The Android Open Source Project
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

import androidx.test.filters.LargeTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit

@LargeTest
@RunWith(JUnit4::class)
class BenchmarkStateTest {
    private fun ms2ns(ms: Long): Long = TimeUnit.MILLISECONDS.toNanos(ms)

    @Test
    fun simple() {
        // would be better to mock the clock, but going with minimal changes for now
        val state = BenchmarkState()
        while (state.keepRunning()) {
            Thread.sleep(3)
            state.pauseTiming()
            Thread.sleep(5)
            state.resumeTiming()
        }
        val median = state.stats.median
        assertTrue("median $median should be between 2ms and 4ms",
                ms2ns(2) < median && median < ms2ns(4))
    }

    @Test
    fun ideSummary() {
        val summary1 = BenchmarkState().apply {
            while (keepRunning()) {
                Thread.sleep(1)
            }
        }.ideSummaryLine("foo")
        val summary2 = BenchmarkState().apply {
            while (keepRunning()) {
                // nothing
            }
        }.ideSummaryLine("fooBarLongerKey")

        assertEquals(summary1.indexOf("foo"),
            summary2.indexOf("foo"))
    }

    @Test
    fun bundle() {
        val bundle = BenchmarkState().apply {
            while (keepRunning()) {
                // nothing, we're ignoring numbers
            }
        }.getFullStatusReport("foo")
        val expectedLabel = WarningState.WARNING_PREFIX + "foo"

        assertTrue(
            (bundle.get("android.studio.display.benchmark") as String).contains(expectedLabel))

        // check attribute presence and naming
        assertNotNull(bundle.get(expectedLabel + "_min"))
        assertNotNull(bundle.get(expectedLabel + "_mean"))
        assertNotNull(bundle.get(expectedLabel + "_count"))
    }
}

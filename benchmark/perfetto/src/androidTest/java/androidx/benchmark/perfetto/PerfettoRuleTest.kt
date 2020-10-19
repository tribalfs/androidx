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

package androidx.benchmark.perfetto

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.FlakyTest
import androidx.test.filters.LargeTest
import androidx.test.filters.RequiresDevice
import androidx.test.filters.SmallTest
import androidx.tracing.Trace
import androidx.tracing.trace
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@FlakyTest // Workaround for cuttlefish ignoring @RequiresDevice (b/170960583)
@RequiresDevice // TODO: sort out emulator support
@LargeTest // recording is expensive
@RunWith(AndroidJUnit4::class)
class PerfettoRuleTest {
    @get:Rule
    val perfettoRule = PerfettoRule()

    @Test
    fun tracingEnabled() {
        Thread.sleep(100)
        trace("PerfettoCaptureTest") {
            val traceShouldBeEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            assertEquals(traceShouldBeEnabled, Trace.isEnabled())

            // Tracing non-trivial duration for manual debugging/verification
            Thread.sleep(20)
        }

        // NOTE: ideally, we'd validate the output file, but it's difficult to assert the
        // behavior of the rule, since we can't really assert the result of a rule, which
        // occurs after both @Test and @After
    }
}

@SmallTest // not recording is cheap
@RunWith(AndroidJUnit4::class)
class PerfettoRuleControlTest {
    @Test
    fun tracingNotEnabled() {
        Thread.sleep(10)
        trace("PerfettoCaptureTest") {
            assertFalse(Trace.isEnabled())
        }
    }
}
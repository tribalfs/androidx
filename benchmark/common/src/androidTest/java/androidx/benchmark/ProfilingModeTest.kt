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

package androidx.benchmark

import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class ProfilingModeTest {
    @Test
    fun getFromString() {
        assertEquals(ProfilingMode.None, ProfilingMode.getFromString("none"))
        assertEquals(ProfilingMode.None, ProfilingMode.getFromString("None"))
        assertEquals(ProfilingMode.None, ProfilingMode.getFromString("nOne"))

        for (mode in ProfilingMode.values()) {
            assertEquals(mode, ProfilingMode.getFromString(mode.toString()))
        }

        assertEquals(null, ProfilingMode.getFromString("methob"))
        assertEquals(null, ProfilingMode.getFromString("secretmode"))
    }
}

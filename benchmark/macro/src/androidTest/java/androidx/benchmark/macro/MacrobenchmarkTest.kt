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

package androidx.benchmark.macro

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.IllegalArgumentException
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class MacrobenchmarkTest {
    @Test
    fun macrobenchmarkWithStartupMode_emptyMetricList() {
        assertFailsWith<IllegalArgumentException> {
            macrobenchmarkWithStartupMode(
                uniqueName = "uniqueName", // ignored, uniqueness not important
                className = "className",
                testName = "testName",
                packageName = "com.ignored",
                metrics = emptyList(), // invalid
                compilationMode = CompilationMode.None,
                iterations = 1,
                startupMode = null,
                setupBlock = {},
                measureBlock = {}
            )
        }
    }
}
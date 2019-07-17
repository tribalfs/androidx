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

import androidx.test.filters.SmallTest
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class BenchmarkRuleAnnotationTest {
    @Suppress("MemberVisibilityCanBePrivate") // intentionally public
    // NOTE: not annotated, so will throw when state is accessed
    val unannotatedRule = BenchmarkRule(enableReport = false)

    @Test(expected = IllegalStateException::class)
    fun throwsIfNotAnnotated() {
        unannotatedRule.getState()
    }

    @Test(expected = IllegalStateException::class)
    fun throwsIfNotAnnotatedMeasure() {
        unannotatedRule.measureRepeated { }
    }
}

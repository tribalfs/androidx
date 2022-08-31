/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.window.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit test for [ActivityComponentInfo] to check [Object.equals] and [Object.hashCode].
 */
class ActivityComponentInfoTest {

    @Test
    fun equalsImpliesSameHashCode() {
        val first = ActivityComponentInfo(PACKAGE_NAME, CLASS_NAME)
        val second = ActivityComponentInfo(PACKAGE_NAME, CLASS_NAME)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    companion object {
        const val PACKAGE_NAME = "package"
        const val CLASS_NAME = "ClassName"
    }
}
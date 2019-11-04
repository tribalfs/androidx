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

package androidx.paging

import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals

@Suppress("SameParameterValue")
@RunWith(JUnit4::class)
class TransformedPageTest {
    @Test
    fun loadHintNoLookup() {
        val page = TransformedPage(
            sourcePageIndex = 0,
            data = listOf('a', 'b'),
            sourcePageSize = 2,
            originalIndices = null
        )

        // negative - index pass-through
        assertEquals(LoadHint(0, -1), page.getLoadHint(-1))

        // verify non-lookup behavior (index pass-through)
        assertEquals(LoadHint(0, 0), page.getLoadHint(0))
        assertEquals(LoadHint(0, 1), page.getLoadHint(1))

        // oob - index passthrough (because data size == source size)
        assertEquals(LoadHint(0, 2), page.getLoadHint(2))
    }

    @Test
    fun loadHintLookup() {
        val page = TransformedPage(
            data = listOf('a', 'b'),
            sourcePageIndex = -4,
            sourcePageSize = 30,
            originalIndices = listOf(10, 20)
        )
        // negative - index pass-through
        assertEquals(LoadHint(-4, -1), page.getLoadHint(-1))

        // verify lookup behavior
        assertEquals(LoadHint(-4, 10), page.getLoadHint(0))
        assertEquals(LoadHint(-4, 20), page.getLoadHint(1))

        // if we access placeholder just after a page with lookup, we offset according to
        // sourcePageSize, since the list may have been filtered, and we want to clearly signal
        // that we're at the end
        assertEquals(LoadHint(-4, 30), page.getLoadHint(2))
    }
}
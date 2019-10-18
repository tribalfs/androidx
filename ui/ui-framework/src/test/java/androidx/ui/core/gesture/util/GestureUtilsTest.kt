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

package androidx.ui.core.gesture.util

import androidx.ui.core.IntPxSize
import androidx.ui.core.ipx
import androidx.ui.core.millisecondsToTimestamp
import androidx.ui.testutils.down
import androidx.ui.testutils.up
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class GestureUtilsTest {

    @Test
    fun anyPointersInBounds_1Up_returnsFalse() {
        assertThat(
            listOf(
                down(x = 0f, y = 0f).up(100L.millisecondsToTimestamp())
            )
                .anyPointersInBounds(IntPxSize(1.ipx, 1.ipx))
        ).isFalse()
    }

    @Test
    fun anyPointersInBounds_4OutOfBounds_returnsFalse() {
        assertThat(
            listOf(
                down(x = -1f, y = 0f),
                down(x = 1f, y = 0f),
                down(x = 0f, y = -1f),
                down(x = 0f, y = 1f)
            )
                .anyPointersInBounds(IntPxSize(1.ipx, 1.ipx))
        ).isFalse()
    }

    @Test
    fun anyPointersInBounds_1InBounds_returnsTrue() {
        assertThat(
            listOf(down(x = 0f, y = 0f))
                .anyPointersInBounds(IntPxSize(1.ipx, 1.ipx))
        ).isTrue()
    }

    @Test
    fun anyPointersInBounds_5OneInBounds_returnsTrue() {
        assertThat(
            listOf(
                down(x = 0f, y = 0f),
                down(x = -1f, y = 0f),
                down(x = 1f, y = 0f),
                down(x = 0f, y = -1f),
                down(x = 0f, y = 1f)
            )
                .anyPointersInBounds(IntPxSize(1.ipx, 1.ipx))
        ).isTrue()
    }
}
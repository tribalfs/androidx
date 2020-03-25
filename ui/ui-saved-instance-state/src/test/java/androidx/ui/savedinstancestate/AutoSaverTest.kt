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

package androidx.ui.savedinstancestate

import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class AutoSaverTest {

    @Test
    fun simpleSave() {
        val saver = autoSaver<Int>()

        val allowingScope = object : SaverScope {
            override fun canBeSaved(value: Any) = true
        }
        with(saver) {
            assertThat(allowingScope.save(2))
                .isEqualTo(2)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun exceptionWhenCantBeSaved() {
        val saver = autoSaver<Int>()

        val disallowingScope = object : SaverScope {
            override fun canBeSaved(value: Any) = false
        }
        with(saver) {
            disallowingScope.save(2)
        }
    }
}

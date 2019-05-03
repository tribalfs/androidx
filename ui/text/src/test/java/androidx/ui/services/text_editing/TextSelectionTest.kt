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

package androidx.ui.services.text_editing

import androidx.ui.engine.text.TextAffinity
import androidx.ui.engine.text.TextPosition
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TextSelectionTest {
    @Test
    fun collapsed() {
        val offset = 10
        val textAffinity = TextAffinity.upstream
        val textSelection = TextSelection.collapsed(offset, textAffinity)

        assertThat(offset, equalTo(textSelection.baseOffset))
        assertThat(offset, equalTo(textSelection.extentOffset))
        assertThat(textAffinity, equalTo(textSelection.affinity))
        assertThat(false, equalTo(textSelection.isDirectional))
    }

    @Test
    fun fromPosition() {
        val offset = 20
        val textAffinity = TextAffinity.downstream
        val pos = TextPosition(offset, textAffinity)
        val textSelection = TextSelection.fromPosition(pos)

        assertThat(offset, equalTo(textSelection.baseOffset))
        assertThat(offset, equalTo(textSelection.extentOffset))
        assertThat(textAffinity, equalTo(textSelection.affinity))
        assertThat(false, equalTo(textSelection.isDirectional))
    }
}
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
package androidx.ui.text.font

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class FontFamilyTest {

    private val dummyResourceId1 = 1
    private val dummyResourceId2 = 2

    @Test(expected = AssertionError::class)
    fun `cannot be instantiated with empty font list`() {
        fontFamily(listOf())
    }

    @Test
    fun `two equal family declarations are equal`() {
        val fontFamily = fontFamily(
            font(
                resId = dummyResourceId1,
                weight = FontWeight.W900,
                style = FontStyle.Italic
            )
        )

        val otherFontFamily = fontFamily(
            font(
                resId = dummyResourceId1,
                weight = FontWeight.W900,
                style = FontStyle.Italic
            )
        )

        assertThat(fontFamily).isEqualTo(otherFontFamily)
    }

    @Test
    fun `two non equal family declarations are not equal`() {
        val fontFamily = fontFamily(
            font(
                resId = dummyResourceId1,
                weight = FontWeight.W900,
                style = FontStyle.Italic
            )
        )

        val otherFontFamily = fontFamily(
            font(
                resId = dummyResourceId1,
                weight = FontWeight.W800,
                style = FontStyle.Italic
            )
        )

        assertThat(fontFamily).isNotEqualTo(otherFontFamily)
    }

    @Test(expected = AssertionError::class)
    fun `cannot add two fonts that have the same FontWeight and FontStyle`() {
        fontFamily(
            font(
                resId = dummyResourceId1,
                weight = FontWeight.W900,
                style = FontStyle.Italic
            ),
            font(
                resId = dummyResourceId2,
                weight = FontWeight.W900,
                style = FontStyle.Italic
            )
        )
    }
}
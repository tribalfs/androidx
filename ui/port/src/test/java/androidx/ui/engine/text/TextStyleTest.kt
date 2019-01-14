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
package androidx.ui.engine.text

import androidx.ui.engine.text.font.FontFamily
import androidx.ui.engine.window.Locale
import androidx.ui.painting.Color
import androidx.ui.painting.Paint
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TextStyleTest {

    @Test(expected = AssertionError::class)
    fun `constructor with both color and foreground defined throws AssertionError`() {
        TextStyle(
            color = Color.fromARGB(1, 1, 1, 1),
            foreground = Paint()
        )
    }

    @Test
    fun `toString with null values`() {
        val textStyle = TextStyle()
        assertThat(
            textStyle.toString(),
            `is`(
                equalTo(
                    "TextStyle(" +
                        "color: unspecified, " +
                        "decoration: unspecified, " +
                        "fontWeight: unspecified, " +
                        "fontStyle: unspecified, " +
                        "textBaseline: unspecified, " +
                        "fontFamily: unspecified, " +
                        "fontSize: unspecified, " +
                        "letterSpacing: unspecified, " +
                        "wordSpacing: unspecified, " +
                        "height: unspecified, " +
                        "locale: unspecified, " +
                        "background: unspecified, " +
                        "foreground: unspecified, " +
                        "fontSynthesis: unspecified" +
                        ")"
                )
            )
        )
    }

    @Test
    fun `toString with values`() {
        val color = Color.fromARGB(1, 2, 3, 4)
        val decoration = TextDecoration.underline
        val fontWeight = FontWeight.bold
        val fontStyle = FontStyle.italic
        val textBaseline = TextBaseline.alphabetic
        val fontFamily = FontFamily("sans-serif")
        val fontSize = 1.0f
        val letterSpacing = 2.0f
        val wordSpacing = 3.0f
        val height = 4.0f
        val locale = Locale("en")
        val background = Color(0xFF000000.toInt())
        val fontSynthesis = FontSynthesis.style

        val textStyle = TextStyle(
            color = color,
            decoration = decoration,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            textBaseline = textBaseline,
            fontFamily = fontFamily,
            fontSize = fontSize,
            letterSpacing = letterSpacing,
            wordSpacing = wordSpacing,
            height = height,
            locale = locale,
            background = background,
            foreground = null,
            fontSynthesis = fontSynthesis
        )

        assertThat(
            textStyle.toString(),
            `is`(
                equalTo(
                    "TextStyle(" +
                        "color: $color, " +
                        "decoration: $decoration, " +
                        "fontWeight: $fontWeight, " +
                        "fontStyle: $fontStyle, " +
                        "textBaseline: $textBaseline, " +
                        "fontFamily: $fontFamily, " +
                        "fontSize: $fontSize, " +
                        "letterSpacing: ${letterSpacing}x, " +
                        "wordSpacing: ${wordSpacing}x, " +
                        "height: ${height}x, " +
                        "locale: $locale, " +
                        "background: $background, " +
                        "foreground: unspecified, " +
                        "fontSynthesis: $fontSynthesis" +
                        ")"
                )
            )
        )
    }
}
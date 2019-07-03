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

package androidx.ui.text

import androidx.ui.core.Constraints
import androidx.ui.core.Density
import androidx.ui.core.sp
import androidx.ui.engine.geometry.Offset
import androidx.ui.text.style.TextAlign
import androidx.ui.text.style.TextDirection
import androidx.ui.painting.Canvas
import androidx.ui.text.style.TextOverflow
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TextPainterTest() {
    private val density = Density(density = 1f)

    @Test
    fun `constructor with default values`() {
        val textPainter = TextPainter(density = density)

        assertThat(textPainter.text).isNull()
        assertThat(textPainter.textAlign).isEqualTo(TextAlign.Start)
        assertThat(textPainter.textDirection).isEqualTo(TextDirection.Ltr)
        assertThat(textPainter.maxLines).isNull()
        assertThat(textPainter.overflow).isEqualTo(TextOverflow.Clip)
        assertThat(textPainter.locale).isNull()
    }

    @Test
    fun `constructor with customized text(TextSpan)`() {
        val text = AnnotatedString("Hello")
        val textPainter = TextPainter(text = text, density = density)

        assertThat(textPainter.text).isEqualTo(text)
    }

    @Test
    fun `constructor with customized textAlign`() {
        val textPainter = TextPainter(
            paragraphStyle = ParagraphStyle(textAlign = TextAlign.Left),
            density = density
        )

        assertThat(textPainter.textAlign).isEqualTo(TextAlign.Left)
    }

    @Test
    fun `constructor with customized textDirection`() {
        val textPainter = TextPainter(
            paragraphStyle = ParagraphStyle(textDirection = TextDirection.Rtl),
            density = density
        )

        assertThat(textPainter.textDirection).isEqualTo(TextDirection.Rtl)
    }

    @Test
    fun `constructor with customized maxLines`() {
        val maxLines = 8

        val textPainter = TextPainter(maxLines = maxLines, density = density)

        assertThat(textPainter.maxLines).isEqualTo(maxLines)
    }

    @Test
    fun `constructor with customized overflow`() {
        val overflow = TextOverflow.Ellipsis

        val textPainter = TextPainter(overflow = overflow, density = density)

        assertThat(textPainter.overflow).isEqualTo(overflow)
    }

    @Test
    fun `constructor with customized locale`() {
        val locale = Locale("en", "US")

        val textPainter = TextPainter(locale = locale, density = density)

        assertThat(textPainter.locale).isEqualTo(locale)
    }

    @Test
    fun `text setter`() {
        val textPainter = TextPainter(density = density)
        val text = AnnotatedString(text = "Hello")

        textPainter.text = text

        assertThat(textPainter.text).isEqualTo(text)
        assertThat(textPainter.paragraph).isNull()
        assertThat(textPainter.needsLayout).isTrue()
    }

    @Test
    fun `createParagraphStyle without TextStyle in AnnotatedText`() {
        val maxLines = 5
        val overflow = TextOverflow.Ellipsis
        val locale = Locale("en", "US")
        val text = AnnotatedString(text = "Hello")
        val textPainter = TextPainter(
            text = text,
            paragraphStyle = ParagraphStyle(
                textAlign = TextAlign.Center,
                textDirection = TextDirection.Rtl
            ),
            maxLines = maxLines,
            overflow = overflow,
            locale = locale,
            density = Density(density = 1f)
        )

        val paragraphStyle = textPainter.createParagraphStyle()

        assertThat(paragraphStyle.textAlign).isEqualTo(TextAlign.Center)
        assertThat(paragraphStyle.textDirection).isEqualTo(TextDirection.Rtl)
        assertThat(paragraphStyle.maxLines).isEqualTo(maxLines)
        assertThat(paragraphStyle.ellipsis).isEqualTo(true)
    }

    @Test
    fun `createParagraphStyle with defaultTextDirection`() {
        val fontSize = 15.sp
        val maxLines = 5
        val overflow = TextOverflow.Ellipsis
        val locale = Locale("en", "US")
        val textStyle = TextStyle(fontSize = fontSize)
        val text = AnnotatedString(text = "Hello")
        val textPainter = TextPainter(
            text = text,
            style = textStyle,
            paragraphStyle = ParagraphStyle(
                textAlign = TextAlign.Center,
                textDirection = TextDirection.Rtl
            ),
            maxLines = maxLines,
            overflow = overflow,
            locale = locale,
            density = density
        )

        val paragraphStyle = textPainter.createParagraphStyle()

        assertThat(paragraphStyle.textAlign).isEqualTo(TextAlign.Center)
        assertThat(paragraphStyle.textDirection).isEqualTo(TextDirection.Rtl)
        assertThat(paragraphStyle.maxLines).isEqualTo(maxLines)
        assertThat(paragraphStyle.ellipsis).isEqualTo(true)
    }

    @Test
    fun `applyFloatingPointHack with value is integer toDouble`() {
        assertThat(applyFloatingPointHack(2f)).isEqualTo(2.0f)
    }

    @Test
    fun `applyFloatingPointHack with value smaller than half`() {
        assertThat(applyFloatingPointHack(2.2f)).isEqualTo(3.0f)
    }

    @Test
    fun `applyFloatingPointHack with value larger than half`() {
        assertThat(applyFloatingPointHack(2.8f)).isEqualTo(3.0f)
    }

    @Test(expected = AssertionError::class)
    fun `minIntrinsicWidth without layout assertion should fail`() {
        val textPainter = TextPainter(density = density)

        textPainter.minIntrinsicWidth
    }

    @Test(expected = AssertionError::class)
    fun `maxIntrinsicWidth without layout assertion should fail`() {
        val textPainter = TextPainter(density = density)

        textPainter.maxIntrinsicWidth
    }

    @Test(expected = AssertionError::class)
    fun `width without layout assertion should fail`() {
        val textPainter = TextPainter(density = density)

        textPainter.width
    }

    @Test(expected = AssertionError::class)
    fun `height without layout assertion should fail`() {
        val textPainter = TextPainter(density = density)

        textPainter.height
    }

    @Test(expected = AssertionError::class)
    fun `size without layout assertion should fail`() {
        val textPainter = TextPainter(density = density)

        textPainter.size
    }

    @Test(expected = AssertionError::class)
    fun `layout without text assertion should fail`() {
        val textPainter = TextPainter(
            paragraphStyle = ParagraphStyle(textDirection = TextDirection.Ltr),
            density = density
        )

        textPainter.layout(Constraints())
    }

    @Test(expected = AssertionError::class)
    fun `paint without layout assertion should fail`() {
        val textPainter = TextPainter(density = density)
        val canvas = mock<Canvas>()

        textPainter.paint(canvas, Offset(0.0f, 0.0f))
    }
}

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

package androidx.ui.painting

import androidx.ui.engine.geometry.Offset
import androidx.ui.engine.text.TextAlign
import androidx.ui.engine.text.TextDirection
import androidx.ui.engine.window.Locale
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TextPainterTest() {
    @Test
    fun `constructor with default values`() {
        val textPainter = TextPainter()

        assertThat(textPainter.text).isNull()
        assertThat(textPainter.textAlign).isEqualTo(TextAlign.START)
        assertThat(textPainter.textDirection).isNull()
        assertThat(textPainter.textScaleFactor).isEqualTo(1.0f)
        assertThat(textPainter.maxLines).isNull()
        assertThat(textPainter.ellipsis).isNull()
        assertThat(textPainter.locale).isNull()
    }

    @Test
    fun `constructor with customized text(TextSpan)`() {
        val textSpan = TextSpan(text = "Hello")

        val textPainter = TextPainter(text = textSpan)

        assertThat(textPainter.text).isEqualTo(textSpan)
    }

    @Test
    fun `constructor with customized textAlign`() {
        val textPainter = TextPainter(textAlign = TextAlign.LEFT)

        assertThat(textPainter.textAlign).isEqualTo(TextAlign.LEFT)
    }

    @Test
    fun `constructor with customized textDirection`() {
        val textPainter = TextPainter(textDirection = TextDirection.RTL)

        assertThat(textPainter.textDirection).isEqualTo(TextDirection.RTL)
    }

    @Test
    fun `constructor with customized textScaleFactor`() {
        val scaleFactor = 2.0f

        val textPainter = TextPainter(textScaleFactor = scaleFactor)

        assertThat(textPainter.textScaleFactor).isEqualTo(scaleFactor)
    }

    @Test
    fun `constructor with customized maxLines`() {
        val maxLines = 8

        val textPainter = TextPainter(maxLines = maxLines)

        assertThat(textPainter.maxLines).isEqualTo(maxLines)
    }

    @Test
    fun `constructor with customized ellipsis`() {
        val ellipsis = true

        val textPainter = TextPainter(ellipsis = ellipsis)

        assertThat(textPainter.ellipsis).isEqualTo(ellipsis)
    }

    @Test
    fun `constructor with customized locale`() {
        val locale = Locale("en", "US")

        val textPainter = TextPainter(locale = locale)

        assertThat(textPainter.locale).isEqualTo(locale)
    }

    @Test
    fun `text setter`() {
        val textPainter = TextPainter()
        val textSpan = TextSpan(text = "Hello")

        textPainter.text = textSpan

        assertThat(textPainter.text).isEqualTo(textSpan)
        assertThat(textPainter.paragraph).isNull()
        assertThat(textPainter.needsLayout).isTrue()
    }

    @Test
    fun `textAlign setter`() {
        val textPainter = TextPainter()

        textPainter.textAlign = TextAlign.LEFT

        assertThat(textPainter.textAlign).isEqualTo(TextAlign.LEFT)
        assertThat(textPainter.paragraph).isNull()
        assertThat(textPainter.needsLayout).isTrue()
    }

    @Test
    fun `textDirection setter`() {
        val textPainter = TextPainter()

        textPainter.textDirection = TextDirection.RTL

        assertThat(textPainter.textDirection).isEqualTo(TextDirection.RTL)
        assertThat(textPainter.paragraph).isNull()
        assertThat(textPainter.layoutTemplate).isNull()
        assertThat(textPainter.needsLayout).isTrue()
    }

    @Test
    fun `textScaleFactor setter`() {
        val textPainter = TextPainter()
        val scaleFactor = 3.0f

        textPainter.textScaleFactor = scaleFactor

        assertThat(textPainter.textScaleFactor).isEqualTo(scaleFactor)
        assertThat(textPainter.paragraph).isNull()
        assertThat(textPainter.layoutTemplate).isNull()
        assertThat(textPainter.needsLayout).isTrue()
    }

    @Test
    fun `maxLines setter`() {
        val textPainter = TextPainter()
        val maxLines = 5

        textPainter.maxLines = maxLines

        assertThat(textPainter.maxLines).isEqualTo(maxLines)
        assertThat(textPainter.paragraph).isNull()
        assertThat(textPainter.needsLayout).isTrue()
    }

    @Test
    fun `ellipsis setter`() {
        val textPainter = TextPainter()
        val ellipsis = true

        textPainter.ellipsis = ellipsis

        assertThat(textPainter.ellipsis).isEqualTo(ellipsis)
        assertThat(textPainter.paragraph).isNull()
        assertThat(textPainter.needsLayout).isTrue()
    }

    @Test
    fun `locale setter`() {
        val textPainter = TextPainter()
        val locale = Locale("en", "US")

        textPainter.locale = locale

        assertThat(textPainter.locale).isEqualTo(locale)
        assertThat(textPainter.paragraph).isNull()
        assertThat(textPainter.needsLayout).isTrue()
    }

    @Test
    fun `createParagraphStyle with TextStyle in TextSpan`() {
        val fontSize = 15.0f
        val scaleFactor = 3.0f
        val maxLines = 5
        val ellipsis = true
        val locale = Locale("en", "US")
        val textStyle = TextStyle(fontSize = fontSize)
        val textSpan = TextSpan(text = "Hello", style = textStyle)
        val textPainter = TextPainter(
            text = textSpan,
            textAlign = TextAlign.CENTER,
            textDirection = TextDirection.RTL,
            textScaleFactor = scaleFactor,
            maxLines = maxLines,
            ellipsis = ellipsis,
            locale = locale
        )

        val paragraphStyle = textPainter.createParagraphStyle()

        assertThat(paragraphStyle.getTextStyle().fontSize).isEqualTo(fontSize * scaleFactor)
        assertThat(paragraphStyle.textAlign).isEqualTo(TextAlign.CENTER)
        assertThat(paragraphStyle.textDirection).isEqualTo(TextDirection.RTL)
        assertThat(paragraphStyle.maxLines).isEqualTo(maxLines)
        assertThat(paragraphStyle.ellipsis).isEqualTo(ellipsis)
        assertThat(paragraphStyle.locale).isEqualTo(locale)
    }

    @Test
    fun `createParagraphStyle without TextStyle in TextSpan`() {
        val scaleFactor = 3.0f
        val maxLines = 5
        val ellipsis = true
        val locale = Locale("en", "US")
        val textSpan = TextSpan(text = "Hello")
        val textPainter = TextPainter(
            text = textSpan,
            textAlign = TextAlign.CENTER,
            textDirection = TextDirection.RTL,
            textScaleFactor = scaleFactor,
            maxLines = maxLines,
            ellipsis = ellipsis,
            locale = locale
        )

        val paragraphStyle = textPainter.createParagraphStyle()

        assertThat(paragraphStyle.getTextStyle().fontSize).isNull()
        assertThat(paragraphStyle.textAlign).isEqualTo(TextAlign.CENTER)
        assertThat(paragraphStyle.textDirection).isEqualTo(TextDirection.RTL)
        assertThat(paragraphStyle.maxLines).isEqualTo(maxLines)
        assertThat(paragraphStyle.ellipsis).isEqualTo(ellipsis)
        assertThat(paragraphStyle.locale).isEqualTo(locale)
    }

    @Test
    fun `createParagraphStyle with defaultTextDirection`() {
        val fontSize = 15.0f
        val scaleFactor = 3.0f
        val maxLines = 5
        val ellipsis = true
        val locale = Locale("en", "US")
        val textStyle = TextStyle(fontSize = fontSize)
        val textSpan = TextSpan(text = "Hello", style = textStyle)
        val textPainter = TextPainter(
            text = textSpan,
            textAlign = TextAlign.CENTER,
            textScaleFactor = scaleFactor,
            maxLines = maxLines,
            ellipsis = ellipsis,
            locale = locale
        )

        val paragraphStyle = textPainter.createParagraphStyle(TextDirection.RTL)

        assertThat(paragraphStyle.getTextStyle().fontSize).isEqualTo(fontSize * scaleFactor)
        assertThat(paragraphStyle.textAlign).isEqualTo(TextAlign.CENTER)
        assertThat(paragraphStyle.textDirection).isEqualTo(TextDirection.RTL)
        assertThat(paragraphStyle.maxLines).isEqualTo(maxLines)
        assertThat(paragraphStyle.ellipsis).isEqualTo(ellipsis)
        assertThat(paragraphStyle.locale).isEqualTo(locale)
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
        val textPainter = TextPainter()

        textPainter.minIntrinsicWidth
    }

    @Test(expected = AssertionError::class)
    fun `maxIntrinsicWidth without layout assertion should fail`() {
        val textPainter = TextPainter()

        textPainter.maxIntrinsicWidth
    }

    @Test(expected = AssertionError::class)
    fun `width without layout assertion should fail`() {
        val textPainter = TextPainter()

        textPainter.width
    }

    @Test(expected = AssertionError::class)
    fun `height without layout assertion should fail`() {
        val textPainter = TextPainter()

        textPainter.height
    }

    @Test(expected = AssertionError::class)
    fun `size without layout assertion should fail`() {
        val textPainter = TextPainter()

        textPainter.size
    }

    @Test(expected = AssertionError::class)
    fun `layout without text assertion should fail`() {
        val textPainter = TextPainter(textDirection = TextDirection.LTR)

        textPainter.layout()
    }

    @Test(expected = AssertionError::class)
    fun `layout without textDirection assertion should fail`() {
        val textPainter = TextPainter(text = TextSpan())

        textPainter.layout()
    }

    @Test
    fun `layout with !needsLayout && minWidth == lastMinWidth && maxWidth == lastMaxWidth`() {
        val textPainter =
            TextPainter(text = TextSpan(text = "Hello"), textDirection = TextDirection.LTR)
        textPainter.needsLayout = false

        textPainter.layout(0.0f, 0.0f)

        assertThat(textPainter.paragraph).isNull()
    }

    @Test(expected = AssertionError::class)
    fun `paint without layout assertion should fail`() {
        val textPainter = TextPainter()
        val canvas = mock<Canvas>()

        textPainter.paint(canvas, Offset(0.0f, 0.0f))
    }
}

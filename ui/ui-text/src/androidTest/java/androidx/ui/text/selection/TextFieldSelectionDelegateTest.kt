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

package androidx.ui.text.selection

import androidx.activity.ComponentActivity
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.ui.core.Constraints
import androidx.ui.core.LayoutDirection
import androidx.ui.test.android.AndroidComposeTestRule
import androidx.ui.text.AnnotatedString
import androidx.ui.text.InternalTextApi
import androidx.ui.text.SpanStyle
import androidx.ui.text.TextDelegate
import androidx.ui.text.TextLayoutResult
import androidx.ui.text.TextRange
import androidx.ui.text.TextStyle
import androidx.ui.text.font.asFontFamily
import androidx.ui.unit.Density
import androidx.ui.unit.TextUnit
import androidx.ui.unit.sp
import com.google.common.truth.Truth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(InternalTextApi::class)
@RunWith(JUnit4::class)
@SmallTest
class TextFieldSelectionDelegateTest {
    @get:Rule
    val composeTestRule = AndroidComposeTestRule<ComponentActivity>()

    private val fontFamily = BASIC_MEASURE_FONT.asFontFamily()
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val defaultDensity = Density(density = 1f)
    private val resourceLoader = TestFontResourceLoader(context)

    @Test
    fun getTextFieldSelection_long_press_select_word_ltr() {
        val text = "hello world\n"
        val fontSize = 20.sp

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        // Act.
        val range = getTextFieldSelection(
            textLayoutResult = textLayoutResult,
            rawStartOffset = 2,
            rawEndOffset = 2,
            previousSelection = null,
            previousHandlesCrossed = false,
            isStartHandle = true,
            wordBasedSelection = true
        )

        // Assert.
        Truth.assertThat(range.start).isEqualTo(0)
        Truth.assertThat(range.end).isEqualTo("hello".length)
    }

    @Test
    fun getTextFieldSelection_long_press_select_word_rtl() {
        val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
        val fontSize = 20.sp

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        // Act.
        val range = getTextFieldSelection(
            textLayoutResult = textLayoutResult,
            rawStartOffset = 5,
            rawEndOffset = 5,
            previousSelection = null,
            previousHandlesCrossed = false,
            isStartHandle = true,
            wordBasedSelection = true
        )

        // Assert.
        Truth.assertThat(range.start).isEqualTo(text.indexOf("\u05D3"))
        Truth.assertThat(range.end).isEqualTo(text.indexOf("\u05D5") + 1)
    }

    @Test
    fun getTextFieldSelection_long_press_drag_handle_not_cross_select_word() {
        val text = "hello world"
        val fontSize = 20.sp

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        val rawStartOffset = text.indexOf('e')
        val rawEndOffset = text.indexOf('r')

        // Act.
        val range = getTextFieldSelection(
            textLayoutResult = textLayoutResult,
            rawStartOffset = rawStartOffset,
            rawEndOffset = rawEndOffset,
            previousSelection = null,
            previousHandlesCrossed = false,
            isStartHandle = true,
            wordBasedSelection = true
        )

        // Assert.
        Truth.assertThat(range.start).isEqualTo(0)
        Truth.assertThat(range.end).isEqualTo(text.length)
    }

    @Test
    fun getTextFieldSelection_long_press_drag_handle_cross_select_word() {
        val text = "hello world"
        val fontSize = 20.sp

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        val rawStartOffset = text.indexOf('r')
        val rawEndOffset = text.indexOf('e')

        // Act.
        val range = getTextFieldSelection(
            textLayoutResult = textLayoutResult,
            rawStartOffset = rawStartOffset,
            rawEndOffset = rawEndOffset,
            previousSelection = null,
            previousHandlesCrossed = false,
            isStartHandle = true,
            wordBasedSelection = true
        )

        // Assert.
        Truth.assertThat(range.start).isEqualTo(text.length)
        Truth.assertThat(range.end).isEqualTo(0)
    }

    @Test
    fun getTextFieldSelection_drag_select_range_ltr() {
        val text = "hello world\n"
        val fontSize = 20.sp

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        // "llo wor" is selected.
        val startOffset = text.indexOf("l")
        val endOffset = text.indexOf("r") + 1

        // Act.
        val range = getTextFieldSelection(
            textLayoutResult = textLayoutResult,
            rawStartOffset = startOffset,
            rawEndOffset = endOffset,
            previousSelection = TextRange(0, 0),
            previousHandlesCrossed = false,
            isStartHandle = true,
            wordBasedSelection = false
        )

        // Assert.
        Truth.assertThat(range.start).isEqualTo(startOffset)
        Truth.assertThat(range.end).isEqualTo(endOffset)
    }

    @Test
    fun getTextFieldSelection_drag_select_range_rtl() {
        val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
        val fontSize = 20.sp

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        // "\u05D1\u05D2 \u05D3" is selected.
        val startOffset = text.indexOf("\u05D1")
        val endOffset = text.indexOf("\u05D3") + 1

        // Act.
        val range = getTextFieldSelection(
            textLayoutResult = textLayoutResult,
            rawStartOffset = startOffset,
            rawEndOffset = endOffset,
            previousSelection = TextRange(0, 0),
            previousHandlesCrossed = false,
            isStartHandle = true,
            wordBasedSelection = false
        )

        // Assert.
        Truth.assertThat(range.start).isEqualTo(startOffset)
        Truth.assertThat(range.end).isEqualTo(endOffset)
    }

    @Test
    fun getTextFieldSelection_drag_select_range_bidi() {
        val textLtr = "Hello"
        val textRtl = "\u05D0\u05D1\u05D2\u05D3\u05D4"
        val text = textLtr + textRtl
        val fontSize = 20.sp

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        // "llo"+"\u05D0\u05D1\u05D2" is selected
        val startOffset = text.indexOf("l")
        val endOffset = text.indexOf("\u05D2") + 1

        // Act.
        val range = getTextFieldSelection(
            textLayoutResult = textLayoutResult,
            rawStartOffset = startOffset,
            rawEndOffset = endOffset,
            previousSelection = TextRange(0, 0),
            previousHandlesCrossed = false,
            isStartHandle = true,
            wordBasedSelection = false
        )

        // Assert.
        Truth.assertThat(range.start).isEqualTo(startOffset)
        Truth.assertThat(range.end).isEqualTo(endOffset)
    }

    @Test
    fun getTextFieldSelection_drag_handles_crossed_ltr() {
        val text = "hello world\n"
        val fontSize = 20.sp

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        // "llo wor" is selected.
        val startOffset = text.indexOf("r") + 1
        val endOffset = text.indexOf("l")

        // Act.
        val range = getTextFieldSelection(
            textLayoutResult = textLayoutResult,
            rawStartOffset = startOffset,
            rawEndOffset = endOffset,
            previousSelection = TextRange(0, 0),
            previousHandlesCrossed = false,
            isStartHandle = true,
            wordBasedSelection = false
        )

        // Assert.
        Truth.assertThat(range.start).isEqualTo(startOffset)
        Truth.assertThat(range.end).isEqualTo(endOffset)
    }

    @Test
    fun getTextFieldSelection_drag_handles_crossed_rtl() {
        val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
        val fontSize = 20.sp

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        // "\u05D1\u05D2 \u05D3" is selected.
        val startOffset = text.indexOf("\u05D3") + 1
        val endOffset = text.indexOf("\u05D1")

        // Act.
        val range = getTextFieldSelection(
            textLayoutResult = textLayoutResult,
            rawStartOffset = startOffset,
            rawEndOffset = endOffset,
            previousSelection = TextRange(0, 0),
            previousHandlesCrossed = false,
            isStartHandle = true,
            wordBasedSelection = false
        )

        // Assert.
        Truth.assertThat(range.start).isEqualTo(startOffset)
        Truth.assertThat(range.end).isEqualTo(endOffset)
    }

    @Test
    fun getTextFieldSelection_drag_handles_crossed_bidi() {
        val textLtr = "Hello"
        val textRtl = "\u05D0\u05D1\u05D2\u05D3\u05D4"
        val text = textLtr + textRtl
        val fontSize = 20.sp

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        // "llo"+"\u05D0\u05D1\u05D2" is selected
        val startOffset = text.indexOf("\u05D2") + 1
        val endOffset = text.indexOf("l")

        // Act.
        val range = getTextFieldSelection(
            textLayoutResult = textLayoutResult,
            rawStartOffset = startOffset,
            rawEndOffset = endOffset,
            previousSelection = TextRange(0, 0),
            previousHandlesCrossed = false,
            isStartHandle = true,
            wordBasedSelection = false
        )

        // Assert.
        Truth.assertThat(range.start).isEqualTo(startOffset)
        Truth.assertThat(range.end).isEqualTo(endOffset)
    }

    private fun simpleTextLayout(
        text: String = "",
        fontSize: TextUnit = TextUnit.Inherit,
        density: Density
    ): TextLayoutResult {
        val spanStyle = SpanStyle(fontSize = fontSize, fontFamily = fontFamily)
        val annotatedString = AnnotatedString(text, spanStyle)
        return TextDelegate(
            text = annotatedString,
            style = TextStyle(),
            density = density,
            resourceLoader = resourceLoader
        ).layout(Constraints(), LayoutDirection.Ltr)
    }
}

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

import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.test.filters.Suppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.ui.core.Density
import androidx.ui.core.LayoutDirection
import androidx.ui.core.PxPosition
import androidx.ui.core.Sp
import androidx.ui.core.px
import androidx.ui.core.sp
import androidx.ui.core.withDensity
import androidx.ui.engine.geometry.Offset
import androidx.ui.engine.geometry.Rect
import androidx.ui.graphics.Color
import androidx.ui.painting.Canvas
import androidx.ui.painting.Image
import androidx.ui.painting.ImageConfig
import androidx.ui.painting.Path
import androidx.ui.painting.PathOperation
import androidx.ui.painting.Shadow
import androidx.ui.text.FontTestData.Companion.BASIC_KERN_FONT
import androidx.ui.text.FontTestData.Companion.BASIC_MEASURE_FONT
import androidx.ui.text.FontTestData.Companion.FONT_100_REGULAR
import androidx.ui.text.FontTestData.Companion.FONT_200_REGULAR
import androidx.ui.text.font.FontFamily
import androidx.ui.text.font.asFontFamily
import androidx.ui.text.matchers.equalToBitmap
import androidx.ui.text.style.TextAlign
import androidx.ui.text.style.TextDirection
import androidx.ui.text.style.TextDirectionAlgorithm
import androidx.ui.text.style.TextIndent
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.not
import org.junit.Assert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.Mockito.mock
import java.util.Locale
import kotlin.math.roundToInt

@RunWith(JUnit4::class)
@SmallTest
class ParagraphIntegrationTest {
    private val fontFamilyMeasureFont = BASIC_MEASURE_FONT.asFontFamily()
    private val fontFamilyKernFont = BASIC_KERN_FONT.asFontFamily()
    private val fontFamilyCustom100 = FONT_100_REGULAR.asFontFamily()
    private val fontFamilyCustom200 = FONT_200_REGULAR.asFontFamily()

    private val context = InstrumentationRegistry.getInstrumentation().context
    private val defaultDensity = Density(density = 1f)

    private val resourceLoader = TestFontResourceLoader(context)

    private val cursorWidth = 4f

    @Test
    fun empty_string() {
        withDensity(defaultDensity) {
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val text = ""
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = 100.0f))

            assertThat(paragraph.width, equalTo(100.0f))

            assertThat(paragraph.height, equalTo(fontSizeInPx))
            // defined in sample_font
            assertThat(paragraph.baseline, equalTo(fontSizeInPx * 0.8f))
            assertThat(paragraph.maxIntrinsicWidth, equalTo(0.0f))
            assertThat(paragraph.minIntrinsicWidth, equalTo(0.0f))
            // TODO(Migration/siyamed): no baseline query per line?
            // TODO(Migration/siyamed): no line count?
        }
    }

    @Test
    fun single_line_default_values() {
        withDensity(defaultDensity) {
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value

            for (text in arrayOf("xyz", "\u05D0\u05D1\u05D2")) {
                val paragraph = simpleParagraph(text = text, fontSize = fontSize)

                // width greater than text width - 150
                paragraph.layout(ParagraphConstraints(width = 200.0f))

                assertThat(text, paragraph.width, equalTo(200.0f))
                assertThat(text, paragraph.height, equalTo(fontSizeInPx))
                // defined in sample_font
                assertThat(text, paragraph.baseline, equalTo(fontSizeInPx * 0.8f))
                assertThat(
                    text,
                    paragraph.maxIntrinsicWidth,
                    equalTo(fontSizeInPx * text.length)
                )
                assertThat(text, paragraph.minIntrinsicWidth, equalTo(text.length * fontSizeInPx))
            }
        }
    }

    @Test
    fun line_break_default_values() {
        withDensity(defaultDensity) {
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value

            for (text in arrayOf("abcdef", "\u05D0\u05D1\u05D2\u05D3\u05D4\u05D5")) {
                val paragraph = simpleParagraph(text = text, fontSize = fontSize)

                // 3 chars width
                paragraph.layout(ParagraphConstraints(width = 3 * fontSizeInPx))

                // 3 chars
                assertThat(text, paragraph.width, equalTo(3 * fontSizeInPx))
                // 2 lines, 1 line gap
                assertThat(
                    text,
                    paragraph.height,
                    equalTo(2 * fontSizeInPx + fontSizeInPx / 5.0f)
                )
                // defined in sample_font
                assertThat(text, paragraph.baseline, equalTo(fontSizeInPx * 0.8f))
                assertThat(
                    text,
                    paragraph.maxIntrinsicWidth,
                    equalTo(fontSizeInPx * text.length)
                )
                assertThat(text, paragraph.minIntrinsicWidth, equalTo(text.length * fontSizeInPx))
            }
        }
    }

    @Test
    fun newline_default_values() {
        withDensity(defaultDensity) {
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value

            for (text in arrayOf("abc\ndef", "\u05D0\u05D1\u05D2\n\u05D3\u05D4\u05D5")) {
                val paragraph = simpleParagraph(text = text, fontSize = fontSize)

                // 3 chars width
                paragraph.layout(ParagraphConstraints(width = 3 * fontSizeInPx))

                // 3 chars

                assertThat(text, paragraph.width, equalTo(3 * fontSizeInPx))
                // 2 lines, 1 line gap
                assertThat(
                    text,
                    paragraph.height,
                    equalTo(2 * fontSizeInPx + fontSizeInPx / 5.0f)
                )
                // defined in sample_font
                assertThat(text, paragraph.baseline, equalTo(fontSizeInPx * 0.8f))
                assertThat(
                    text,
                    paragraph.maxIntrinsicWidth,
                    equalTo(fontSizeInPx * text.indexOf("\n"))
                )
                assertThat(text, paragraph.minIntrinsicWidth,
                    equalTo(fontSizeInPx * text.indexOf("\n"))
                )
            }
        }
    }

    @Test
    fun newline_and_line_break_default_values() {
        withDensity(defaultDensity) {
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value

            for (text in arrayOf("abc\ndef", "\u05D0\u05D1\u05D2\n\u05D3\u05D4\u05D5")) {
                val paragraph = simpleParagraph(text = text, fontSize = fontSize)

                // 2 chars width

                paragraph.layout(ParagraphConstraints(width = 2 * fontSizeInPx))

                // 2 chars
                assertThat(text, paragraph.width, equalTo(2 * fontSizeInPx))
                // 4 lines, 3 line gaps
                assertThat(
                    text,
                    paragraph.height,
                    equalTo(4 * fontSizeInPx + 3 * fontSizeInPx / 5.0f)
                )
                // defined in sample_font
                assertThat(text, paragraph.baseline, equalTo(fontSizeInPx * 0.8f))
                assertThat(
                    text,
                    paragraph.maxIntrinsicWidth,
                    equalTo(fontSizeInPx * text.indexOf("\n"))
                )
                assertThat(text, paragraph.minIntrinsicWidth,
                    equalTo(fontSizeInPx * text.indexOf("\n"))
                )
            }
        }
    }

    @Test
    fun getOffsetForPosition_ltr() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))
            // test positions that are 1, fontSize+1, 2fontSize+1 which maps to chars 0, 1, 2 ...
            for (i in 0..text.length) {
                val position = PxPosition((i * fontSizeInPx + 1).px, (fontSizeInPx / 2).px)
                val offset = paragraph.getOffsetForPosition(position)
                assertThat(
                    "offset at index $i, position $position does not match",
                    offset,
                    equalTo(i)
                )
            }
        }
    }

    @Test
    fun getOffsetForPosition_rtl() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            // test positions that are 1, fontSize+1, 2fontSize+1 which maps to chars .., 2, 1, 0
            for (i in 0..text.length) {
                val position = PxPosition((i * fontSizeInPx + 1).px, (fontSizeInPx / 2).px)
                val offset = paragraph.getOffsetForPosition(position)
                assertThat(
                    "offset at index $i, position $position does not match",
                    offset,
                    equalTo(text.length - i)
                )
            }
        }
    }

    @Test
    fun getOffsetForPosition_ltr_multiline() {
        withDensity(defaultDensity) {
            val firstLine = "abc"
            val secondLine = "def"
            val text = firstLine + secondLine
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = firstLine.length * fontSizeInPx))

            // test positions are 1, fontSize+1, 2fontSize+1 and always on the second line
            // which maps to chars 3, 4, 5
            for (i in 0..secondLine.length) {
                val position = PxPosition((i * fontSizeInPx + 1).px, (fontSizeInPx * 1.5f).px)
                val offset = paragraph.getOffsetForPosition(position)
                assertThat(
                    "offset at index $i, position $position, second line does not match",
                    offset,
                    equalTo(i + firstLine.length)
                )
            }
        }
    }

    @Test
    fun getOffsetForPosition_rtl_multiline() {
        withDensity(defaultDensity) {
            val firstLine = "\u05D0\u05D1\u05D2"
            val secondLine = "\u05D3\u05D4\u05D5"
            val text = firstLine + secondLine
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = firstLine.length * fontSizeInPx))

            // test positions are 1, fontSize+1, 2fontSize+1 and always on the second line
            // which maps to chars 5, 4, 3
            for (i in 0..secondLine.length) {
                val position = PxPosition((i * fontSizeInPx + 1).px, (fontSizeInPx * 1.5f).px)
                val offset = paragraph.getOffsetForPosition(position)
                assertThat(
                    "offset at index $i, position $position, second line does not match",
                    offset,
                    equalTo(text.length - i)
                )
            }
        }
    }

    @Test
    fun getOffsetForPosition_ltr_width_outOfBounds() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            // greater than width
            var position = PxPosition((fontSizeInPx * text.length * 2).px, (fontSizeInPx / 2).px)
            var offset = paragraph.getOffsetForPosition(position)
            assertThat(offset, equalTo(text.length))

            // negative
            position = PxPosition((-1 * fontSizeInPx).px, (fontSizeInPx / 2).px)
            offset = paragraph.getOffsetForPosition(position)
            assertThat(offset, equalTo(0))
        }
    }

    @Test
    fun getOffsetForPosition_ltr_height_outOfBounds() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            // greater than height
            var position = PxPosition((fontSizeInPx / 2).px, (fontSizeInPx * text.length * 2).px)
            var offset = paragraph.getOffsetForPosition(position)
            assertThat(offset, equalTo(0))

            // negative
            position = PxPosition((fontSizeInPx / 2).px, (-1 * fontSizeInPx).px)
            offset = paragraph.getOffsetForPosition(position)
            assertThat(offset, equalTo(0))
        }
    }

    @Test
    fun getBoundingBox_ltr_singleLine() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))
            // test positions that are 0, 1, 2 ... which maps to chars 0, 1, 2 ...
            for (i in 0..text.length - 1) {
                val box = paragraph.getBoundingBox(i)
                assertThat(box.left, equalTo(i * fontSizeInPx))
                assertThat(box.right, equalTo((i + 1) * fontSizeInPx))
                assertThat(box.top, equalTo(0f))
                assertThat(box.bottom, equalTo(fontSizeInPx))
            }
        }
    }

    @Test
    fun getBoundingBox_ltr_multiLines() {
        withDensity(defaultDensity) {
            val firstLine = "abc"
            val secondLine = "def"
            val text = firstLine + secondLine
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = firstLine.length * fontSizeInPx))

            // test positions are 3, 4, 5 and always on the second line
            // which maps to chars 3, 4, 5
            for (i in 0..secondLine.length - 1) {
                val textPosition = i + firstLine.length
                val box = paragraph.getBoundingBox(textPosition)
                assertThat(box.left, equalTo(i * fontSizeInPx))
                assertThat(box.right, equalTo((i + 1) * fontSizeInPx))
                assertThat(box.top, equalTo(fontSizeInPx))
                assertThat(box.bottom, equalTo((2f + 1 / 5f) * fontSizeInPx))
            }
        }
    }

    @Test
    fun getBoundingBox_ltr_textPosition_negative() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            val textPosition = -1
            val box = paragraph.getBoundingBox(textPosition)
            assertThat(box.left, equalTo(0f))
            assertThat(box.right, equalTo(0f))
            assertThat(box.top, equalTo(0f))
            assertThat(box.bottom, equalTo(fontSizeInPx))
        }
    }

    // TODO(qqd) on API 23 this test causes test to be blocked and wait indefinitely. Please fix.
    @Suppress
    @Test(expected = java.lang.IndexOutOfBoundsException::class)
    fun getBoundingBox_ltr_textPosition_larger_than_length_throw_exception() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            val textPosition = text.length + 1
            paragraph.getBoundingBox(textPosition)
        }
    }

    @Test(expected = java.lang.AssertionError::class)
    fun getCursorRect_larger_than_length_throw_exception() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            paragraph.getCursorRect(text.length + 1)
        }
    }

    @Test(expected = java.lang.AssertionError::class)
    fun getCursorRect_negative_throw_exception() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            paragraph.getCursorRect(-1)
        }
    }

    @Test
    fun getCursorRect_ltr_singleLine() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            for (i in 0 until text.length) {
                val cursorRect = paragraph.getCursorRect(i)
                val cursorXOffset = i * fontSizeInPx
                assertThat(
                    cursorRect,
                    equalTo(Rect(
                        left = cursorXOffset - cursorWidth / 2,
                        top = 0f,
                        right = cursorXOffset + cursorWidth / 2,
                        bottom = fontSizeInPx
                    ))
                )
            }
        }
    }

    @Test
    fun getCursorRect_ltr_multiLines() {
        withDensity(defaultDensity) {
            val text = "abcdef"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)
            val charsPerLine = 3

            paragraph.layout(ParagraphConstraints(width = charsPerLine * fontSizeInPx))

            for (i in 0 until charsPerLine) {
                val cursorXOffset = i * fontSizeInPx
                assertThat(
                    paragraph.getCursorRect(i),
                    equalTo(Rect(
                        left = cursorXOffset - cursorWidth / 2,
                        top = 0f,
                        right = cursorXOffset + cursorWidth / 2,
                        bottom = fontSizeInPx
                    ))
                )
            }

            for (i in charsPerLine until text.length) {
                val cursorXOffset = (i % charsPerLine) * fontSizeInPx
                assertThat(
                    paragraph.getCursorRect(i),
                    equalTo(Rect(
                        left = cursorXOffset - cursorWidth / 2,
                        top = fontSizeInPx,
                        right = cursorXOffset + cursorWidth / 2,
                        bottom = fontSizeInPx * 2.2f
                    ))
                )
            }
        }
    }

    @Test
    fun getCursorRect_ltr_newLine() {
        withDensity(defaultDensity) {
            val text = "abc\ndef"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            // Cursor before '\n'
            assertThat(
                paragraph.getCursorRect(3),
                equalTo(Rect(
                    left = 3 * fontSizeInPx - cursorWidth / 2,
                    top = 0f,
                    right = 3 * fontSizeInPx + cursorWidth / 2,
                    bottom = fontSizeInPx
                ))
            )

            // Cursor after '\n'
            assertThat(
                paragraph.getCursorRect(4),
                equalTo(Rect(
                    left = -cursorWidth / 2,
                    top = fontSizeInPx,
                    right = cursorWidth / 2,
                    bottom = fontSizeInPx * 2.2f
                ))
            )
        }
    }

    @Test
    fun getCursorRect_ltr_newLine_last_char() {
        withDensity(defaultDensity) {
            val text = "abc\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            // Cursor before '\n'
            assertThat(
                paragraph.getCursorRect(3),
                equalTo(Rect(
                    left = 3 * fontSizeInPx - cursorWidth / 2,
                    top = 0f,
                    right = 3 * fontSizeInPx + cursorWidth / 2,
                    bottom = fontSizeInPx
                ))
            )

            // Cursor after '\n'
            assertThat(
                paragraph.getCursorRect(4),
                equalTo(Rect(
                    left = -cursorWidth / 2,
                    top = fontSizeInPx,
                    right = cursorWidth / 2,
                    bottom = fontSizeInPx * 2.2f
                ))
            )
        }
    }

    @Test
    fun getCursorRect_rtl_singleLine() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            for (i in 0 until text.length) {
                val cursorXOffset = (text.length - i) * fontSizeInPx
                assertThat(
                    paragraph.getCursorRect(i),
                    equalTo(Rect(
                        left = cursorXOffset - cursorWidth / 2,
                        top = 0f,
                        right = cursorXOffset + cursorWidth / 2,
                        bottom = fontSizeInPx
                    ))
                )
            }
        }
    }

    @Test
    fun getCursorRect_rtl_multiLines() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\u05D0\u05D1\u05D2"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)
            val charsPerLine = 3

            paragraph.layout(ParagraphConstraints(width = charsPerLine * fontSizeInPx))

            for (i in 0 until charsPerLine) {
                val cursorXOffset = (charsPerLine - i) * fontSizeInPx
                assertThat(
                    paragraph.getCursorRect(i),
                    equalTo(Rect(
                        left = cursorXOffset - cursorWidth / 2,
                        top = 0f,
                        right = cursorXOffset + cursorWidth / 2,
                        bottom = fontSizeInPx
                    ))
                )
            }

            for (i in charsPerLine until text.length) {
                val cursorXOffset = (charsPerLine - i % charsPerLine) * fontSizeInPx
                assertThat(
                    paragraph.getCursorRect(i),
                    equalTo(Rect(
                        left = cursorXOffset - cursorWidth / 2,
                        top = fontSizeInPx,
                        right = cursorXOffset + cursorWidth / 2,
                        bottom = fontSizeInPx * 2.2f
                    ))
                )
            }
        }
    }

    @Test
    fun getCursorRect_rtl_newLine() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\n\u05D0\u05D1\u05D2"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = 3 * fontSizeInPx))

            // Cursor before '\n'
            assertThat(
                paragraph.getCursorRect(3),
                equalTo(Rect(
                    left = 0 - cursorWidth / 2,
                    top = 0f,
                    right = 0 + cursorWidth / 2,
                    bottom = fontSizeInPx
                ))
            )

            // Cursor after '\n'
            assertThat(
                paragraph.getCursorRect(4),
                equalTo(Rect(
                    left = 3 * fontSizeInPx - cursorWidth / 2,
                    top = fontSizeInPx,
                    right = 3 * fontSizeInPx + cursorWidth / 2,
                    bottom = fontSizeInPx * 2.2f
                ))
            )
        }
    }

    @Test
    fun getCursorRect_rtl_newLine_last_char() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = 3 * fontSizeInPx))

            // Cursor before '\n'
            assertThat(
                paragraph.getCursorRect(3),
                equalTo(Rect(
                    left = 0 - cursorWidth / 2,
                    top = 0f,
                    right = 0 + cursorWidth / 2,
                    bottom = fontSizeInPx
                ))
            )

            // Cursor after '\n'
            assertThat(
                paragraph.getCursorRect(4),
                equalTo(Rect(
                    left = -cursorWidth / 2,
                    top = fontSizeInPx,
                    right = +cursorWidth / 2,
                    bottom = fontSizeInPx * 2.2f
                ))
            )
        }
    }

    @Test
    fun getPrimaryHorizontal_ltr_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            for (i in 0..text.length) {
                assertThat(
                    paragraph.getPrimaryHorizontal(i),
                    equalTo(fontSizeInPx * i)
                )
            }
        }
    }

    @Test
    fun getPrimaryHorizontal_rtl_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..text.length) {
                assertThat(
                    paragraph.getPrimaryHorizontal(i),
                    equalTo(width - fontSizeInPx * i)
                )
            }
        }
    }

    @Test
    fun getPrimaryHorizontal_Bidi_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..ltrText.length) {
                assertThat(
                    paragraph.getPrimaryHorizontal(i),
                    equalTo(fontSizeInPx * i)
                )
            }

            for (i in 1 until rtlText.length) {
                assertThat(
                    paragraph.getPrimaryHorizontal(i + ltrText.length),
                    equalTo(width - fontSizeInPx * i)
                )
            }

            assertThat(
                paragraph.getPrimaryHorizontal(text.length),
                equalTo(width)
            )
        }
    }

    @Test
    fun getPrimaryHorizontal_ltr_singleLine_textDirectionRtl() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl
            )
            val width = text.length * fontSizeInPx
            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getPrimaryHorizontal(0), equalTo(width))

            for (i in 1 until text.length) {
                assertThat(
                    paragraph.getPrimaryHorizontal(i),
                    equalTo(fontSizeInPx * i)
                )
            }

            assertThat(paragraph.getPrimaryHorizontal(text.length), equalTo(0f))
        }
    }

    @Test
    fun getPrimaryHorizontal_rtl_singleLine_textDirectionLtr() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr
            )
            val width = text.length * fontSizeInPx
            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getPrimaryHorizontal(0), equalTo(0f))

            for (i in 1 until text.length) {
                assertThat(
                    paragraph.getPrimaryHorizontal(i),
                    equalTo(width - fontSizeInPx * i)
                )
            }

            assertThat(paragraph.getPrimaryHorizontal(text.length), equalTo(width))
        }
    }

    @Test
    fun getPrimaryHorizontal_Bidi_singleLine_textDirectionLtr() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..ltrText.length) {
                assertThat(
                    paragraph.getPrimaryHorizontal(i),
                    equalTo(fontSizeInPx * i)
                )
            }

            for (i in 1 until rtlText.length) {
                assertThat(
                    paragraph.getPrimaryHorizontal(i + ltrText.length),
                    equalTo(width - fontSizeInPx * i)
                )
            }

            assertThat(
                paragraph.getPrimaryHorizontal(text.length),
                equalTo(width)
            )
        }
    }

    @Test
    fun getPrimaryHorizontal_Bidi_singleLine_textDirectionRtl() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getPrimaryHorizontal(0), equalTo(width))
            for (i in 1 until ltrText.length) {
                assertThat(
                    paragraph.getPrimaryHorizontal(i),
                    equalTo(rtlText.length * fontSizeInPx + i * fontSizeInPx)
                )
            }

            for (i in 0..rtlText.length) {
                assertThat(
                    paragraph.getPrimaryHorizontal(i + ltrText.length),
                    equalTo(rtlText.length * fontSizeInPx - i * fontSizeInPx)
                )
            }
        }
    }

    @Test
    fun getPrimaryHorizontal_ltr_newLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "abc\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getPrimaryHorizontal(text.length), equalTo(0f))
        }
    }

    @Test
    fun getPrimaryHorizontal_rtl_newLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getPrimaryHorizontal(text.length), equalTo(0f))
        }
    }

    @Test
    fun getPrimaryHorizontal_ltr_newLine_textDirectionRtl() {
        withDensity(defaultDensity) {
            val text = "abc\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getPrimaryHorizontal(text.length), equalTo(width))
        }
    }

    @Test
    fun getPrimaryHorizontal_rtl_newLine_textDirectionLtr() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getPrimaryHorizontal(text.length), equalTo(0f))
        }
    }

    @Test
    fun getSecondaryHorizontal_ltr_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)

            paragraph.layout(ParagraphConstraints(width = text.length * fontSizeInPx))

            for (i in 0..text.length) {
                assertThat(
                    paragraph.getSecondaryHorizontal(i),
                    equalTo(fontSizeInPx * i)
                )
            }
        }
    }

    @Test
    fun getSecondaryHorizontal_rtl_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..text.length) {
                assertThat(
                    paragraph.getSecondaryHorizontal(i),
                    equalTo(width - fontSizeInPx * i)
                )
            }
        }
    }

    @Test
    fun getSecondaryHorizontal_Bidi_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0 until ltrText.length) {
                assertThat(
                    paragraph.getSecondaryHorizontal(i),
                    equalTo(fontSizeInPx * i)
                )
            }

            for (i in 0..rtlText.length) {
                assertThat(
                    paragraph.getSecondaryHorizontal(i + ltrText.length),
                    equalTo(width - fontSizeInPx * i)
                )
            }
        }
    }

    @Test
    fun getSecondaryHorizontal_ltr_singleLine_textDirectionRtl() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl
            )
            val width = text.length * fontSizeInPx
            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getSecondaryHorizontal(0), equalTo(0f))

            for (i in 1 until text.length) {
                assertThat(
                    paragraph.getSecondaryHorizontal(i),
                    equalTo(fontSizeInPx * i)
                )
            }

            assertThat(paragraph.getSecondaryHorizontal(text.length), equalTo(width))
        }
    }

    @Test
    fun getSecondaryHorizontal_rtl_singleLine_textDirectionLtr() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr
            )
            val width = text.length * fontSizeInPx
            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getSecondaryHorizontal(0), equalTo(width))

            for (i in 1 until text.length) {
                assertThat(
                    paragraph.getSecondaryHorizontal(i),
                    equalTo(width - fontSizeInPx * i)
                )
            }

            assertThat(paragraph.getSecondaryHorizontal(text.length), equalTo(0f))
        }
    }

    @Test
    fun getSecondaryHorizontal_Bidi_singleLine_textDirectionLtr() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0 until ltrText.length) {
                assertThat(
                    paragraph.getSecondaryHorizontal(i),
                    equalTo(fontSizeInPx * i)
                )
            }

            for (i in 0 until rtlText.length) {
                assertThat(
                    paragraph.getSecondaryHorizontal(i + ltrText.length),
                    equalTo(width - fontSizeInPx * i)
                )
            }

            assertThat(
                paragraph.getSecondaryHorizontal(text.length),
                equalTo(width - rtlText.length * fontSizeInPx)
            )
        }
    }

    @Test
    fun getSecondaryHorizontal_Bidi_singleLine_textDirectionRtl() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            assertThat(
                paragraph.getSecondaryHorizontal(0),
                equalTo(width - ltrText.length * fontSizeInPx)
            )
            for (i in 1..ltrText.length) {
                assertThat(
                    paragraph.getSecondaryHorizontal(i),
                    equalTo(rtlText.length * fontSizeInPx + i * fontSizeInPx)
                )
            }

            for (i in 1..rtlText.length) {
                assertThat(
                    paragraph.getSecondaryHorizontal(i + ltrText.length),
                    equalTo(rtlText.length * fontSizeInPx - i * fontSizeInPx)
                )
            }
        }
    }

    @Test
    fun getSecondaryHorizontal_ltr_newLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "abc\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getSecondaryHorizontal(text.length), equalTo(0f))
        }
    }

    @Test
    fun getSecondaryHorizontal_rtl_newLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize)
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getSecondaryHorizontal(text.length), equalTo(0f))
        }
    }

    @Test
    fun getSecondaryHorizontal_ltr_newLine_textDirectionRtl() {
        withDensity(defaultDensity) {
            val text = "abc\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getSecondaryHorizontal(text.length), equalTo(width))
        }
    }

    @Test
    fun getSecondaryHorizontal_rtl_newLine_textDirectionLtr() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            assertThat(paragraph.getSecondaryHorizontal(text.length), equalTo(0f))
        }
    }

    @Test
    fun getParagraphDirection_ltr_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..text.length) {
                assertThat(paragraph.getParagraphDirection(i), equalTo(TextDirection.Ltr))
            }
        }
    }

    @Test
    fun getParagraphDirection_ltr_singleLine_textDirectionRtl() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..text.length) {
                assertThat(paragraph.getParagraphDirection(i), equalTo(TextDirection.Rtl))
            }
        }
    }

    @Test
    fun getParagraphDirection_rtl_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0 until text.length) {
                assertThat(paragraph.getParagraphDirection(i), equalTo(TextDirection.Rtl))
            }
        }
    }

    @Test
    fun getParagraphDirection_rtl_singleLine_textDirectionLtr() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..text.length) {
                assertThat(paragraph.getParagraphDirection(i), equalTo(TextDirection.Ltr))
            }
        }
    }

    @Test
    fun getParagraphDirection_Bidi_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..text.length) {
                assertThat(paragraph.getParagraphDirection(i), equalTo(TextDirection.Ltr))
            }
        }
    }

    @Test
    fun getParagraphDirection_Bidi_singleLine_textDirectionLtr() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..text.length) {
                assertThat(paragraph.getParagraphDirection(i), equalTo(TextDirection.Ltr))
            }
        }
    }

    @Test
    fun getParagraphDirection_Bidi_singleLine_textDirectionRtl() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..text.length) {
                assertThat(paragraph.getParagraphDirection(i), equalTo(TextDirection.Rtl))
            }
        }
    }

    @Test
    fun getBidiRunDirection_ltr_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..text.length) {
                assertThat(paragraph.getBidiRunDirection(i), equalTo(TextDirection.Ltr))
            }
        }
    }

    @Test
    fun getBidiRunDirection_ltr_singleLine_textDirectionRtl() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0..text.length) {
                assertThat(paragraph.getBidiRunDirection(i), equalTo(TextDirection.Ltr))
            }
        }
    }

    @Test
    fun getBidiRunDirection_rtl_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0 until text.length) {
                assertThat(paragraph.getBidiRunDirection(i), equalTo(TextDirection.Rtl))
            }
        }
    }

    @Test
    fun getBidiRunDirection_rtl_singleLine_textDirectionLtr() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2\n"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0 until text.length - 1) {
                assertThat(paragraph.getBidiRunDirection(i), equalTo(TextDirection.Rtl))
            }
            assertThat(paragraph.getBidiRunDirection(text.length - 1), equalTo(TextDirection.Ltr))
        }
    }

    @Test
    fun getBidiRunDirection_Bidi_singleLine_textDirectionDefault() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0 until ltrText.length) {
                assertThat(paragraph.getBidiRunDirection(i), equalTo(TextDirection.Ltr))
            }

            for (i in ltrText.length until text.length) {
                assertThat(paragraph.getBidiRunDirection(i), equalTo(TextDirection.Rtl))
            }
        }
    }

    @Test
    fun getBidiRunDirection_Bidi_singleLine_textDirectionLtr() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0 until ltrText.length) {
                assertThat(paragraph.getBidiRunDirection(i), equalTo(TextDirection.Ltr))
            }

            for (i in ltrText.length until text.length) {
                assertThat(paragraph.getBidiRunDirection(i), equalTo(TextDirection.Rtl))
            }
        }
    }

    @Test
    fun getBidiRunDirection_Bidi_singleLine_textDirectionRtl() {
        withDensity(defaultDensity) {
            val ltrText = "abc"
            val rtlText = "\u05D0\u05D1\u05D2"
            val text = ltrText + rtlText
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl
            )
            val width = text.length * fontSizeInPx

            paragraph.layout(ParagraphConstraints(width))

            for (i in 0 until ltrText.length) {
                assertThat(paragraph.getBidiRunDirection(i), equalTo(TextDirection.Ltr))
            }

            for (i in ltrText.length until text.length) {
                assertThat(paragraph.getBidiRunDirection(i), equalTo(TextDirection.Rtl))
            }
        }
    }

    @Test
    fun locale_withCJK_shouldNotDrawSame() {
        withDensity(defaultDensity) {
            val text = "\u82B1"
            val fontSize = 10.sp
            val fontSizeInPx = fontSize.toPx().value
            val locales = arrayOf(
                // duplicate ja is on purpose
                Locale("ja"),
                Locale("ja"),
                Locale("zh", "CN"),
                Locale("zh", "TW")
            )

            val bitmaps = locales.map { locale ->
                val paragraph = Paragraph(
                    text = text,
                    textStyles = listOf(),
                    style = TextStyle(
                        fontSize = fontSize,
                        locale = locale
                    ),
                    paragraphStyle = ParagraphStyle(),
                    density = defaultDensity,
                    resourceLoader = resourceLoader,
                    layoutDirection = LayoutDirection.Ltr
                )

                // just have 10x font size to have a bitmap
                paragraph.layout(ParagraphConstraints(width = fontSizeInPx * 10))

                paragraph.bitmap()
            }

            assertThat(bitmaps[0], equalToBitmap(bitmaps[1]))
            assertThat(bitmaps[1], not(equalToBitmap(bitmaps[2])))
            assertThat(bitmaps[1], not(equalToBitmap(bitmaps[3])))
            // this does not work on API 21
            // assertThat(bitmaps[2], not(equalToBitmap(bitmaps[3])))
        }
    }

    @Test
    fun maxLines_withMaxLineEqualsZero() {
        val text = "a\na\na"
        val maxLines = 0
        val paragraph = simpleParagraph(
            text = text,
            fontSize = 100.sp,
            maxLines = maxLines
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))
        assertThat(paragraph.height, equalTo(0f))
    }

    @Test(expected = java.lang.IllegalArgumentException::class)
    fun maxLines_withMaxLineNegative_throwsException() {
        val text = "a\na\na"
        val maxLines = -1
        val paragraph = simpleParagraph(
            text = text,
            fontSize = 100.sp,
            maxLines = maxLines
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))
    }

    @Test
    fun maxLines_withMaxLineSmallerThanTextLines_clipHeight() {
        withDensity(defaultDensity) {
            val text = "a\na\na"
            val fontSize = 100.sp
            val fontSizeInPx = fontSize.toPx().value
            val lineCount = text.lines().size
            val maxLines = lineCount
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                maxLines = maxLines
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))
            val expectHeight = (lineCount + (lineCount - 1) * 0.2f) * fontSizeInPx
            assertThat(paragraph.height, equalTo(expectHeight))
        }
    }

    @Test
    fun maxLines_withMaxLineEqualsTextLine() {
        withDensity(defaultDensity) {
            val text = "a\na\na"
            val fontSize = 100.sp
            val fontSizeInPx = fontSize.toPx().value
            val maxLines = text.lines().size
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                maxLines = maxLines
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))
            val expectHeight = (maxLines + (maxLines - 1) * 0.2f) * fontSizeInPx
            assertThat(paragraph.height, equalTo(expectHeight))
        }
    }

    @Test
    fun maxLines_withMaxLineGreaterThanTextLines() {
        withDensity(defaultDensity) {
            val text = "a\na\na"
            val fontSize = 100.sp
            val fontSizeInPx = fontSize.toPx().value
            val lineCount = text.lines().size
            val maxLines = lineCount + 1
            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                maxLines = maxLines
            )
            paragraph.layout(ParagraphConstraints(width = 200f))
            val expectHeight = (lineCount + (lineCount - 1) * 0.2f) * fontSizeInPx
            assertThat(paragraph.height, equalTo(expectHeight))
        }
    }

    @Test
    fun maxLines_paintDifferently() {
        withDensity(defaultDensity) {
            val text = "a\na\na"
            val fontSize = 100.sp
            val fontSizeInPx = fontSize.toPx().value
            val maxLines = 1

            val paragraphWithMaxLine = simpleParagraph(
                text = text,
                fontSize = fontSize,
                maxLines = maxLines
            )
            paragraphWithMaxLine.layout(ParagraphConstraints(width = fontSizeInPx))

            val paragraphNoMaxLine = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            paragraphNoMaxLine.layout(ParagraphConstraints(width = fontSizeInPx))

            // Make sure the maxLine is applied correctly
            assertThat(paragraphNoMaxLine.height, greaterThan(paragraphWithMaxLine.height))

            val imageNoMaxLine = Image(
                paragraphNoMaxLine.width.roundToInt(),
                paragraphNoMaxLine.height.roundToInt(),
                ImageConfig.Argb8888
            )
            // Same size with imageNoMaxLine for comparison
            val imageWithMaxLine = Image(
                paragraphNoMaxLine.width.roundToInt(),
                paragraphNoMaxLine.height.roundToInt(),
                ImageConfig.Argb8888
            )

            paragraphNoMaxLine.paint(Canvas(imageNoMaxLine))
            paragraphWithMaxLine.paint(Canvas(imageWithMaxLine))
            assertThat(
                imageNoMaxLine.nativeImage,
                not(equalToBitmap(imageWithMaxLine.nativeImage))
            )
        }
    }

    @Test
    fun didExceedMaxLines_withMaxLinesSmallerThanTextLines_returnsTrue() {
        val text = "aaa\naa"
        val maxLines = text.lines().size - 1
        val paragraph = simpleParagraph(text = text, maxLines = maxLines)

        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))
        assertThat(paragraph.didExceedMaxLines, equalTo(true))
    }

    @Test
    fun didExceedMaxLines_withMaxLinesEqualToTextLines_returnsFalse() {
        val text = "aaa\naa"
        val maxLines = text.lines().size
        val paragraph = simpleParagraph(text = text, maxLines = maxLines)

        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))
        assertThat(paragraph.didExceedMaxLines, equalTo(false))
    }

    @Test
    fun didExceedMaxLines_withMaxLinesGreaterThanTextLines_returnsFalse() {
        val text = "aaa\naa"
        val maxLines = text.lines().size + 1
        val paragraph = simpleParagraph(text = text, maxLines = maxLines)

        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))
        assertThat(paragraph.didExceedMaxLines, equalTo(false))
    }

    @Test
    fun didExceedMaxLines_withMaxLinesSmallerThanTextLines_withLineWrap_returnsTrue() {
        withDensity(defaultDensity) {
            val text = "aa"
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val maxLines = 1
            val paragraph = simpleParagraph(text = text, fontSize = fontSize, maxLines = maxLines)

            // One line can only contain 1 character
            paragraph.layout(ParagraphConstraints(width = fontSizeInPx))
            assertThat(paragraph.didExceedMaxLines, equalTo(true))
        }
    }

    @Test
    fun didExceedMaxLines_withMaxLinesEqualToTextLines_withLineWrap_returnsFalse() {
        val text = "a"
        val maxLines = text.lines().size
        val paragraph = simpleParagraph(text = text, fontSize = 50.sp, maxLines = maxLines)

        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))
        assertThat(paragraph.didExceedMaxLines, equalTo(false))
    }

    @Test
    fun didExceedMaxLines_withMaxLinesGreaterThanTextLines_withLineWrap_returnsFalse() {
        withDensity(defaultDensity) {
            val text = "aa"
            val maxLines = 3
            val fontSize = 50.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(text = text, fontSize = fontSize, maxLines = maxLines)

            // One line can only contain 1 character
            paragraph.layout(ParagraphConstraints(width = fontSizeInPx))
            assertThat(paragraph.didExceedMaxLines, equalTo(false))
        }
    }

    @Test
    fun textAlign_defaultValue_alignsStart() {
        withDensity(defaultDensity) {
            val textLTR = "aa"
            val textRTL = "\u05D0\u05D0"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value

            val paragraphLTR = simpleParagraph(
                text = textLTR,
                fontSize = fontSize
            )
            val layoutLTRWidth = (textLTR.length + 2) * fontSizeInPx
            paragraphLTR.layout(ParagraphConstraints(width = layoutLTRWidth))

            val paragraphRTL = simpleParagraph(
                text = textRTL,
                fontSize = fontSize
            )
            val layoutRTLWidth = (textRTL.length + 2) * fontSizeInPx
            paragraphRTL.layout(ParagraphConstraints(width = layoutRTLWidth))

            // When textAlign is TextAlign.start, LTR aligns to left, RTL aligns to right.
            assertThat(paragraphLTR.getLineLeft(0), equalTo(0.0f))
            assertThat(paragraphRTL.getLineRight(0), equalTo(layoutRTLWidth))
        }
    }

    @Test
    fun textAlign_whenAlignLeft_returnsZeroForGetLineLeft() {
        withDensity(defaultDensity) {
            val texts = listOf("aa", "\u05D0\u05D0")
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value

            texts.map { text ->
                val paragraph = simpleParagraph(
                    text = text,
                    textAlign = TextAlign.Left,
                    fontSize = fontSize
                )
                val layoutWidth = (text.length + 2) * fontSizeInPx
                paragraph.layout(ParagraphConstraints(width = layoutWidth))

                assertThat(paragraph.getLineLeft(0), equalTo(0.0f))
            }
        }
    }

    @Test
    fun textAlign_whenAlignRight_returnsLayoutWidthForGetLineRight() {
        withDensity(defaultDensity) {
            val texts = listOf("aa", "\u05D0\u05D0")
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value

            texts.map { text ->
                val paragraph = simpleParagraph(
                    text = text,
                    textAlign = TextAlign.Right,
                    fontSize = fontSize
                )
                val layoutWidth = (text.length + 2) * fontSizeInPx
                paragraph.layout(ParagraphConstraints(width = layoutWidth))

                assertThat(paragraph.getLineRight(0), equalTo(layoutWidth))
            }
        }
    }

    @Test
    fun textAlign_whenAlignCenter_textIsCentered() {
        withDensity(defaultDensity) {
            val texts = listOf("aa", "\u05D0\u05D0")
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value

            texts.map { text ->
                val paragraph = simpleParagraph(
                    text = text,
                    textAlign = TextAlign.Center,
                    fontSize = fontSize
                )
                val layoutWidth = (text.length + 2) * fontSizeInPx
                paragraph.layout(ParagraphConstraints(width = layoutWidth))
                val textWidth = text.length * fontSizeInPx

                assertThat(
                    paragraph.getLineLeft(0),
                    equalTo(layoutWidth / 2 - textWidth / 2)
                )
                assertThat(
                    paragraph.getLineRight(0),
                    equalTo(layoutWidth / 2 + textWidth / 2)
                )
            }
        }
    }

    @Test
    fun textAlign_whenAlignStart_withLTR_returnsZeroForGetLineLeft() {
        withDensity(defaultDensity) {
            val text = "aa"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = (text.length + 2) * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                textAlign = TextAlign.Start,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))

            assertThat(paragraph.getLineLeft(0), equalTo(0.0f))
        }
    }

    @Test
    fun textAlign_whenAlignEnd_withLTR_returnsLayoutWidthForGetLineRight() {
        withDensity(defaultDensity) {
            val text = "aa"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = (text.length + 2) * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                textAlign = TextAlign.End,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))

            assertThat(paragraph.getLineRight(0), equalTo(layoutWidth))
        }
    }

    @Test
    fun textAlign_whenAlignStart_withRTL_returnsLayoutWidthForGetLineRight() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D0"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = (text.length + 2) * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                textAlign = TextAlign.Start,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))

            assertThat(paragraph.getLineRight(0), equalTo(layoutWidth))
        }
    }

    @Test
    fun textAlign_whenAlignEnd_withRTL_returnsZeroForGetLineLeft() {
        withDensity(defaultDensity) {
            val text = "\u05D0\u05D0"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = (text.length + 2) * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                textAlign = TextAlign.End,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))

            assertThat(paragraph.getLineLeft(0), equalTo(0.0f))
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 28)
    // We have to test justification above API 28 because of this bug b/68009059, where devices
    // before API 28 may have an extra space at the end of line.
    fun textAlign_whenAlignJustify_justifies() {
        withDensity(defaultDensity) {
            val text = "a a a"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = ("a a".length + 1) * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                textAlign = TextAlign.Justify,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))

            assertThat(paragraph.getLineLeft(0), equalTo(0.0f))
            assertThat(paragraph.getLineRight(0), equalTo(layoutWidth))
            // Last line should align start
            assertThat(paragraph.getLineLeft(1), equalTo(0.0f))
        }
    }

    @Test
    fun textDirection_whenLTR_dotIsOnRight() {
        withDensity(defaultDensity) {
            val text = "a.."
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = text.length * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceLtr,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))
            // The position of the last character in display order.
            val position = PxPosition(("a.".length * fontSizeInPx + 1).px, (fontSizeInPx / 2).px)
            val charIndex = paragraph.getOffsetForPosition(position)
            assertThat(charIndex, equalTo(2))
        }
    }

    @Test
    fun textDirection_whenRTL_dotIsOnLeft() {
        withDensity(defaultDensity) {
            val text = "a.."
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = text.length * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                textDirectionAlgorithm = TextDirectionAlgorithm.ForceRtl,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))
            // The position of the first character in display order.
            val position = PxPosition((fontSizeInPx / 2 + 1).px, (fontSizeInPx / 2).px)
            val charIndex = paragraph.getOffsetForPosition(position)
            assertThat(charIndex, equalTo(2))
        }
    }

    @Test
    fun textDirection_whenDefault_withoutStrongChar_directionIsLTR() {
        withDensity(defaultDensity) {
            val text = "..."
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = text.length * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))
            for (i in 0..text.length) {
                // The position of the i-th character in display order.
                val position = PxPosition((i * fontSizeInPx + 1).px, (fontSizeInPx / 2).px)
                val charIndex = paragraph.getOffsetForPosition(position)
                assertThat(charIndex, equalTo(i))
            }
        }
    }

    @Test
    fun textDirection_whenDefault_withFirstStrongCharLTR_directionIsLTR() {
        withDensity(defaultDensity) {
            val text = "a\u05D0."
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = text.length * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))
            for (i in 0 until text.length) {
                // The position of the i-th character in display order.
                val position = PxPosition((i * fontSizeInPx + 1).px, (fontSizeInPx / 2).px)
                val charIndex = paragraph.getOffsetForPosition(position)
                assertThat(charIndex, equalTo(i))
            }
        }
    }

    @Test
    fun textDirection_whenDefault_withFirstStrongCharRTL_directionIsRTL() {
        withDensity(defaultDensity) {
            val text = "\u05D0a."
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = text.length * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))
            // The first character in display order should be '.'
            val position = PxPosition((fontSizeInPx / 2 + 1).px, (fontSizeInPx / 2).px)
            val index = paragraph.getOffsetForPosition(position)
            assertThat(index, equalTo(2))
        }
    }

    @Test
    fun lineHeight_returnsSameAsGiven() {
        withDensity(defaultDensity) {
            val text = "abcdefgh"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            // Make the layout 4 lines
            val layoutWidth = text.length * fontSizeInPx / 4
            val lineHeight = 1.5f

            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                lineHeight = lineHeight
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))

            assertThat(paragraph.lineCount, equalTo(4))
            // TODO(Migration/haoyuchang): Due to bug b/120530738, the height of the first line is
            // wrong in the framework. Will fix it when the lineHeight in TextSpan is implemented.
            for (i in 1 until paragraph.lineCount - 1) {
                val actualHeight = paragraph.getLineHeight(i)
                // In the sample_font.ttf, the height of the line should be
                // fontSize + 0.2f * fontSize(line gap)
                assertThat(
                    "line number $i",
                    actualHeight,
                    equalTo(1.2f * fontSizeInPx * lineHeight)
                )
            }
        }
    }

    @Test
    fun lineHeight_hasNoEffectOnLastLine() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val layoutWidth = (text.length - 1) * fontSizeInPx
            val lineHeight = 1.5f

            val paragraph = simpleParagraph(
                text = text,
                fontSize = fontSize,
                lineHeight = lineHeight
            )
            paragraph.layout(ParagraphConstraints(width = layoutWidth))

            val lastLine = paragraph.lineCount - 1
            // In the sample_font.ttf, the height of the line should be
            // fontSize + 0.2 * fontSize(line gap)
            assertThat(paragraph.getLineHeight(lastLine), equalTo(1.2f * fontSizeInPx))
        }
    }

    @Test
    fun testAnnotatedString_setFontSizeOnWholeText() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textStyle = TextStyle(fontSize = fontSize)
            val paragraphWidth = fontSizeInPx * text.length

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            // Make sure there is only one line, so that we can use getLineRight to test fontSize.
            assertThat(paragraph.lineCount, equalTo(1))
            // Notice that in this test font, the width of character equals to fontSize.
            assertThat(paragraph.getLineWidth(0), equalTo(fontSizeInPx * text.length))
        }
    }

    @Test
    fun testAnnotatedString_setFontSizeOnPartOfText() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textStyleFontSize = 30.sp
            val textStyleFontSizeInPx = textStyleFontSize.toPx().value
            val textStyle = TextStyle(fontSize = textStyleFontSize)
            val paragraphWidth = textStyleFontSizeInPx * text.length

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length)),
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            // Make sure there is only one line, so that we can use getLineRight to test fontSize.
            assertThat(paragraph.lineCount, equalTo(1))
            // Notice that in this test font, the width of character equals to fontSize.
            val expectedLineRight =
                "abc".length * textStyleFontSizeInPx + "de".length * fontSizeInPx
            assertThat(paragraph.getLineWidth(0), equalTo(expectedLineRight))
        }
    }

    @Test
    fun testAnnotatedString_seFontSizeTwice_lastOneOverwrite() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textStyle = TextStyle(fontSize = fontSize)

            val fontSizeOverwrite = 30.sp
            val fontSizeOverwriteInPx = fontSizeOverwrite.toPx().value
            val textStyleOverwrite = TextStyle(fontSize = fontSizeOverwrite)
            val paragraphWidth = fontSizeOverwriteInPx * text.length

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(
                    AnnotatedString.Item(textStyle, 0, text.length),
                    AnnotatedString.Item(textStyleOverwrite, 0, "abc".length)
                )
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            // Make sure there is only one line, so that we can use getLineRight to test fontSize.
            assertThat(paragraph.lineCount, equalTo(1))
            // Notice that in this test font, the width of character equals to fontSize.
            val expectedWidth = "abc".length * fontSizeOverwriteInPx + "de".length * fontSizeInPx
            assertThat(paragraph.getLineWidth(0), equalTo(expectedWidth))
        }
    }

    @Test
    fun testAnnotatedString_fontSizeScale() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val fontSizeScale = 0.5f
            val textStyle = TextStyle(fontSizeScale = fontSizeScale)

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length)),
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            assertThat(
                paragraph.getLineRight(0),
                equalTo(text.length * fontSizeInPx * fontSizeScale)
            )
        }
    }

    @Test
    fun testAnnotatedString_fontSizeScaleNested() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val fontSizeScale = 0.5f
            val textStyle = TextStyle(fontSizeScale = fontSizeScale)

            val fontSizeScaleNested = 2f
            val textStyleNested = TextStyle(fontSizeScale = fontSizeScaleNested)

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(
                    AnnotatedString.Item(textStyle, 0, text.length),
                    AnnotatedString.Item(textStyleNested, 0, text.length)
                ),
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            assertThat(
                paragraph.getLineRight(0),
                equalTo(text.length * fontSizeInPx * fontSizeScale * fontSizeScaleNested)
            )
        }
    }

    @Test
    fun testAnnotatedString_fontSizeScaleWithFontSizeFirst() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val paragraphFontSize = 20.sp

            val fontSize = 30.sp
            val fontSizeInPx = fontSize.toPx().value
            val fontSizeStyle = TextStyle(fontSize = fontSize)

            val fontSizeScale = 0.5f
            val fontSizeScaleStyle = TextStyle(fontSizeScale = fontSizeScale)

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(
                    AnnotatedString.Item(fontSizeStyle, 0, text.length),
                    AnnotatedString.Item(fontSizeScaleStyle, 0, text.length)
                ),
                fontSize = paragraphFontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            assertThat(
                paragraph.getLineRight(0),
                equalTo(text.length * fontSizeInPx * fontSizeScale)
            )
        }
    }

    @Test
    fun testAnnotatedString_fontSizeScaleWithFontSizeSecond() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val paragraphFontSize = 20.sp

            val fontSize = 30.sp
            val fontSizeInPx = fontSize.toPx().value
            val fontSizeStyle = TextStyle(fontSize = fontSize)

            val fontSizeScale = 0.5f
            val fontSizeScaleStyle = TextStyle(fontSizeScale = fontSizeScale)

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(
                    AnnotatedString.Item(fontSizeScaleStyle, 0, text.length),
                    AnnotatedString.Item(fontSizeStyle, 0, text.length)
                ),
                fontSize = paragraphFontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            assertThat(
                paragraph.getLineRight(0),
                equalTo(text.length * fontSizeInPx)
            )
        }
    }

    @Test
    fun testAnnotatedString_fontSizeScaleWithFontSizeNested() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val paragraphFontSize = 20.sp

            val fontSize = 30.sp
            val fontSizeInPx = fontSize.toPx().value
            val fontSizeStyle = TextStyle(fontSize = fontSize)

            val fontSizeScale1 = 0.5f
            val fontSizeScaleStyle1 = TextStyle(fontSizeScale = fontSizeScale1)

            val fontSizeScale2 = 2f
            val fontSizeScaleStyle2 = TextStyle(fontSizeScale = fontSizeScale2)

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(
                    AnnotatedString.Item(fontSizeScaleStyle1, 0, text.length),
                    AnnotatedString.Item(fontSizeStyle, 0, text.length),
                    AnnotatedString.Item(fontSizeScaleStyle2, 0, text.length)
                ),
                fontSize = paragraphFontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            assertThat(
                paragraph.getLineRight(0),
                equalTo(text.length * fontSizeInPx * fontSizeScale2)
            )
        }
    }

    @Test
    fun testAnnotatedString_setLetterSpacingOnWholeText() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val letterSpacing = 5.0f
            val textStyle = TextStyle(letterSpacing = letterSpacing)
            val paragraphWidth = fontSizeInPx * (1 + letterSpacing) * text.length

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length)),
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            // Make sure there is only one line, so that we can use getLineRight to test fontSize.
            assertThat(paragraph.lineCount, equalTo(1))
            // Notice that in this test font, the width of character equals to fontSize.
            assertThat(
                paragraph.getLineWidth(0),
                equalTo(fontSizeInPx * text.length * (1 + letterSpacing))
            )
        }
    }

    @Test
    fun testAnnotatedString_setLetterSpacingOnPartText() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val letterSpacing = 5.0f
            val textStyle = TextStyle(letterSpacing = letterSpacing)
            val paragraphWidth = fontSizeInPx * (1 + letterSpacing) * text.length

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length)),
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            // Make sure there is only one line, so that we can use getLineRight to test fontSize.
            assertThat(paragraph.lineCount, equalTo(1))
            // Notice that in this test font, the width of character equals to fontSize.
            val expectedWidth = ("abc".length * letterSpacing + text.length) * fontSizeInPx
            assertThat(paragraph.getLineWidth(0), equalTo(expectedWidth))
        }
    }

    @Test
    fun testAnnotatedString_setLetterSpacingTwice_lastOneOverwrite() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val letterSpacing = 5.0f
            val textStyle = TextStyle(letterSpacing = letterSpacing)

            val letterSpacingOverwrite = 10.0f
            val textStyleOverwrite =
                TextStyle(letterSpacing = letterSpacingOverwrite)
            val paragraphWidth = fontSizeInPx * (1 + letterSpacingOverwrite) * text.length

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(
                    AnnotatedString.Item(textStyle, 0, text.length),
                    AnnotatedString.Item(textStyleOverwrite, 0, "abc".length)
                ),
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            // Make sure there is only one line, so that we can use getLineRight to test fontSize.
            assertThat(paragraph.lineCount, equalTo(1))
            // Notice that in this test font, the width of character equals to fontSize.
            val expectedWidth = "abc".length * (1 + letterSpacingOverwrite) * fontSizeInPx +
                    "de".length * (1 + letterSpacing) * fontSizeInPx
            assertThat(paragraph.getLineWidth(0), equalTo(expectedWidth))
        }
    }

    @Test
    fun textIndent_onSingleLine() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val indent = 20.0f

            val paragraph = simpleParagraph(
                text = text,
                textIndent = TextIndent(firstLine = indent.px),
                fontSize = fontSize,
                fontFamily = fontFamilyMeasureFont
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            // This position should point to the first character 'a' if indent is applied.
            // Otherwise this position will point to the second character 'b'.
            val position = PxPosition((indent + 1).px, (fontSizeInPx / 2).px)
            // The offset corresponding to the position should be the first char 'a'.
            assertThat(paragraph.getOffsetForPosition(position), equalTo(0))
        }
    }

    @Test
    fun textIndent_onFirstLine() {
        withDensity(defaultDensity) {
            val text = "abcdef"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val indent = 20.0f
            val paragraphWidth = "abcd".length * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                textIndent = TextIndent(firstLine = indent.px),
                fontSize = fontSize,
                fontFamily = fontFamilyMeasureFont
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            assertThat(paragraph.lineCount, equalTo(2))
            // This position should point to the first character of the first line if indent is
            // applied. Otherwise this position will point to the second character of the second line.
            val position = PxPosition((indent + 1).px, (fontSizeInPx / 2).px)
            // The offset corresponding to the position should be the first char 'a'.
            assertThat(paragraph.getOffsetForPosition(position), equalTo(0))
        }
    }

    @Test
    fun textIndent_onRestLine() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val indent = 20.0f
            val paragraphWidth = "abc".length * fontSizeInPx

            val paragraph = simpleParagraph(
                text = text,
                textIndent = TextIndent(
                    firstLine = 0.px,
                    restLine = indent.px
                ),
                fontSize = fontSize,
                fontFamily = fontFamilyMeasureFont
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            // This position should point to the first character of the second line if indent is
            // applied. Otherwise this position will point to the second character of the second line.
            val position = PxPosition((indent + 1).px, (fontSizeInPx / 2 + fontSizeInPx).px)
            // The offset corresponding to the position should be the 'd' in the second line.
            assertThat(
                paragraph.getOffsetForPosition(position),
                equalTo("abcd".length - 1)
            )
        }
    }

    @Test
    fun testAnnotatedString_fontFamily_changesMeasurement() {
        withDensity(defaultDensity) {
            val text = "ad"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            // custom 100 regular font has b as the wide glyph
            // custom 200 regular font has d as the wide glyph
            val textStyle = TextStyle(fontFamily = fontFamilyCustom200)
            // a is rendered in paragraphStyle font (custom 100), it will not have wide glyph
            // d is rendered in defaultTextStyle font (custom 200), and it will be wide glyph
            val expectedWidth = fontSizeInPx + fontSizeInPx * 3

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(
                    AnnotatedString.Item(textStyle, "a".length, text.length)
                ),
                fontSize = fontSize,
                fontFamily = fontFamilyCustom100
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            assertThat(paragraph.lineCount, equalTo(1))
            assertThat(paragraph.getLineWidth(0), equalTo(expectedWidth))
        }
    }

    @Test
    fun testAnnotatedString_fontFeature_turnOffKern() {
        withDensity(defaultDensity) {
            val text = "AaAa"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            // This fontFeatureSetting turns off the kerning
            val textStyle = TextStyle(fontFeatureSettings = "\"kern\" 0")

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(
                    AnnotatedString.Item(textStyle, 0, "aA".length)
                ),
                fontSize = fontSize,
                fontFamily = fontFamilyKernFont
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            // Two characters are kerning, so minus 0.4 * fontSize
            val expectedWidth = text.length * fontSizeInPx - 0.4f * fontSizeInPx
            assertThat(paragraph.lineCount, equalTo(1))
            assertThat(paragraph.getLineWidth(0), equalTo(expectedWidth))
        }
    }

    @Test
    fun testAnnotatedString_shadow() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraphWidth = fontSizeInPx * text.length

            val textStyle =
                TextStyle(
                    shadow = Shadow(
                        Color(0xFF00FF00),
                        Offset(1f, 2f),
                        3.px
                    )
                )
            val paragraphShadow = simpleParagraph(
                text = text,
                textStyles = listOf(
                    AnnotatedString.Item(textStyle, 0, text.length)
                )
            )
            paragraphShadow.layout(ParagraphConstraints(width = paragraphWidth))

            val paragraph = simpleParagraph(text = text)
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            assertThat(paragraphShadow.bitmap(), not(equalToBitmap(paragraph.bitmap())))
        }
    }

    @Test
    fun testDefaultTextStyle_setColor() {
        withDensity(defaultDensity) {
            val text = "abc"
            // FontSize doesn't matter here, but it should be big enough for bitmap comparison.
            val fontSize = 100.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraphWidth = fontSizeInPx * text.length
            val textStyle = TextStyle(color = Color.Red)

            val paragraphWithoutColor = simpleParagraph(
                text = text,
                fontSize = fontSize
            )
            paragraphWithoutColor.layout(ParagraphConstraints(paragraphWidth))

            val paragraphWithColor = simpleParagraph(
                text = text,
                textStyle = textStyle,
                fontSize = fontSize
            )
            paragraphWithColor.layout(ParagraphConstraints(paragraphWidth))

            assertThat(
                paragraphWithColor.bitmap(),
                not(equalToBitmap(paragraphWithoutColor.bitmap()))
            )
        }
    }

    @Test
    fun testDefaultTextStyle_setLetterSpacing() {
        withDensity(defaultDensity) {
            val text = "abc"
            // FontSize doesn't matter here, but it should be big enough for bitmap comparison.
            val fontSize = 100.sp
            val fontSizeInPx = fontSize.toPx().value
            val letterSpacing = 1f
            val textStyle = TextStyle(letterSpacing = letterSpacing)

            val paragraph = simpleParagraph(
                text = text,
                textStyle = textStyle,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(Float.MAX_VALUE))

            assertThat(
                paragraph.getLineRight(0),
                equalTo(fontSizeInPx * (1 + letterSpacing) * text.length)
            )
        }
    }

    @Test
    fun testGetPathForRange_singleLine() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontFamily = fontFamilyMeasureFont,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            val expectedPath = Path()
            val lineLeft = paragraph.getLineLeft(0)
            val lineRight = paragraph.getLineRight(0)
            expectedPath.addRect(
                Rect(
                    lineLeft,
                    0f,
                    lineRight - fontSizeInPx,
                    fontSizeInPx
                )
            )

            // Select "ab"
            val actualPath = paragraph.getPathForRange(0, 2)

            val diff = Path.combine(PathOperation.difference, expectedPath, actualPath).getBounds()
            assertThat(diff, equalTo(Rect.zero))
        }
    }

    @Test
    fun testGetPathForRange_multiLines() {
        withDensity(defaultDensity) {
            val text = "abc\nabc"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontFamily = fontFamilyMeasureFont,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            val expectedPath = Path()
            val firstLineLeft = paragraph.getLineLeft(0)
            val secondLineLeft = paragraph.getLineLeft(1)
            val firstLineRight = paragraph.getLineRight(0)
            val secondLineRight = paragraph.getLineRight(1)
            expectedPath.addRect(
                Rect(
                    firstLineLeft + fontSizeInPx,
                    0f,
                    firstLineRight,
                    fontSizeInPx
                )
            )
            expectedPath.addRect(
                Rect(
                    secondLineLeft,
                    fontSizeInPx,
                    secondLineRight - fontSizeInPx,
                    paragraph.height
                )
            )

            // Select "bc\nab"
            val actualPath = paragraph.getPathForRange(1, 6)

            val diff = Path.combine(PathOperation.difference, expectedPath, actualPath).getBounds()
            assertThat(diff, equalTo(Rect.zero))
        }
    }

    @Test
    fun testGetPathForRange_Bidi() {
        withDensity(defaultDensity) {
            val textLTR = "Hello"
            val textRTL = "שלום"
            val text = textLTR + textRTL
            val selectionLTRStart = 2
            val selectionRTLEnd = 2
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontFamily = fontFamilyMeasureFont,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            val expectedPath = Path()
            val lineLeft = paragraph.getLineLeft(0)
            val lineRight = paragraph.getLineRight(0)
            expectedPath.addRect(
                Rect(
                    lineLeft + selectionLTRStart * fontSizeInPx,
                    0f,
                    lineLeft + textLTR.length * fontSizeInPx,
                    fontSizeInPx
                )
            )
            expectedPath.addRect(
                Rect(
                    lineRight - selectionRTLEnd * fontSizeInPx,
                    0f,
                    lineRight,
                    fontSizeInPx
                )
            )

            // Select "llo..של"
            val actualPath =
                paragraph.getPathForRange(selectionLTRStart, textLTR.length + selectionRTLEnd)

            val diff = Path.combine(PathOperation.difference, expectedPath, actualPath).getBounds()
            assertThat(diff, equalTo(Rect.zero))
        }
    }

    @Test
    fun testGetPathForRange_Start_Equals_End_Returns_Empty_Path() {
        val text = "abc"
        val paragraph = simpleParagraph(
            text = text,
            fontFamily = fontFamilyMeasureFont,
            fontSize = 20.sp
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        val actualPath = paragraph.getPathForRange(1, 1)

        assertThat(actualPath.getBounds(), equalTo(Rect.zero))
    }

    @Test
    fun testGetPathForRange_Empty_Text() {
        val text = ""
        val paragraph = simpleParagraph(
            text = text,
            fontFamily = fontFamilyMeasureFont,
            fontSize = 20.sp
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        val actualPath = paragraph.getPathForRange(0, 0)

        assertThat(actualPath.getBounds(), equalTo(Rect.zero))
    }

    @Test
    fun testGetPathForRange_Surrogate_Pair_Start_Middle_Second_Character_Selected() {
        withDensity(defaultDensity) {
            val text = "\uD834\uDD1E\uD834\uDD1F"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontFamily = fontFamilyMeasureFont,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            val expectedPath = Path()
            val lineRight = paragraph.getLineRight(0)
            expectedPath.addRect(Rect(lineRight / 2, 0f, lineRight, fontSizeInPx))

            // Try to select "\uDD1E\uD834\uDD1F", only "\uD834\uDD1F" is selected.
            val actualPath = paragraph.getPathForRange(1, text.length)

            val diff = Path.combine(PathOperation.difference, expectedPath, actualPath).getBounds()
            assertThat(diff, equalTo(Rect.zero))
        }
    }

    @Test
    fun testGetPathForRange_Surrogate_Pair_End_Middle_Second_Character_Selected() {
        withDensity(defaultDensity) {
            val text = "\uD834\uDD1E\uD834\uDD1F"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontFamily = fontFamilyMeasureFont,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            val expectedPath = Path()
            val lineRight = paragraph.getLineRight(0)
            expectedPath.addRect(Rect(lineRight / 2, 0f, lineRight, fontSizeInPx))

            // Try to select "\uDD1E\uD834", actually "\uD834\uDD1F" is selected.
            val actualPath = paragraph.getPathForRange(1, text.length - 1)

            val diff = Path.combine(PathOperation.difference, expectedPath, actualPath).getBounds()
            assertThat(diff, equalTo(Rect.zero))
        }
    }

    @Test
    fun testGetPathForRange_Surrogate_Pair_Start_Middle_End_Same_Character_Returns_Line_Segment() {
        withDensity(defaultDensity) {
            val text = "\uD834\uDD1E\uD834\uDD1F"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontFamily = fontFamilyMeasureFont,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            val expectedPath = Path()
            val lineRight = paragraph.getLineRight(0)
            expectedPath.addRect(Rect(lineRight / 2, 0f, lineRight / 2, fontSizeInPx))

            // Try to select "\uDD1E", get vertical line segment after this character.
            val actualPath = paragraph.getPathForRange(1, 2)

            val diff = Path.combine(PathOperation.difference, expectedPath, actualPath).getBounds()
            assertThat(diff, equalTo(Rect.zero))
        }
    }

    @Test
    fun testGetPathForRange_Emoji_Sequence() {
        withDensity(defaultDensity) {
            val text = "\u1F600\u1F603\u1F604\u1F606"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontFamily = fontFamilyMeasureFont,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            val expectedPath = Path()
            val lineLeft = paragraph.getLineLeft(0)
            val lineRight = paragraph.getLineRight(0)
            expectedPath.addRect(
                Rect(
                    lineLeft + fontSizeInPx,
                    0f,
                    lineRight - fontSizeInPx,
                    fontSizeInPx
                )
            )

            // Select "\u1F603\u1F604"
            val actualPath = paragraph.getPathForRange(1, text.length - 1)

            val diff = Path.combine(PathOperation.difference, expectedPath, actualPath).getBounds()
            assertThat(diff, equalTo(Rect.zero))
        }
    }

    @Test
    fun testGetPathForRange_Unicode_200D_Return_Line_Segment() {
        withDensity(defaultDensity) {
            val text = "\u200D"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontFamily = fontFamilyMeasureFont,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            val expectedPath = Path()
            val lineLeft = paragraph.getLineLeft(0)
            val lineRight = paragraph.getLineRight(0)
            expectedPath.addRect(Rect(lineLeft, 0f, lineRight, fontSizeInPx))

            val actualPath = paragraph.getPathForRange(0, 1)

            assertThat(lineLeft, equalTo(lineRight))
            val diff = Path.combine(PathOperation.difference, expectedPath, actualPath).getBounds()
            assertThat(diff, equalTo(Rect.zero))
        }
    }

    @Test
    fun testGetPathForRange_Unicode_2066_Return_Line_Segment() {
        withDensity(defaultDensity) {
            val text = "\u2066"
            val fontSize = 20f.sp
            val fontSizeInPx = fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                fontFamily = fontFamilyMeasureFont,
                fontSize = fontSize
            )
            paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

            val expectedPath = Path()
            val lineLeft = paragraph.getLineLeft(0)
            val lineRight = paragraph.getLineRight(0)
            expectedPath.addRect(Rect(lineLeft, 0f, lineRight, fontSizeInPx))

            val actualPath = paragraph.getPathForRange(0, 1)

            assertThat(lineLeft, equalTo(lineRight))
            val diff = Path.combine(PathOperation.difference, expectedPath, actualPath).getBounds()
            assertThat(diff, equalTo(Rect.zero))
        }
    }

    @Test
    fun testGetWordBoundary() {
        val text = "abc def"
        val paragraph = simpleParagraph(
            text = text,
            fontFamily = fontFamilyMeasureFont,
            fontSize = 20.sp
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        val result = paragraph.getWordBoundary(text.indexOf('a'))

        assertThat(result.start, equalTo(text.indexOf('a')))
        assertThat(result.end, equalTo(text.indexOf(' ')))
    }

    @Test
    fun testGetWordBoundary_Bidi() {
        val text = "abc \u05d0\u05d1\u05d2 def"
        val paragraph = simpleParagraph(
            text = text,
            fontFamily = fontFamilyMeasureFont,
            fontSize = 20.sp
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        val resultEnglish = paragraph.getWordBoundary(text.indexOf('a'))
        val resultHebrew = paragraph.getWordBoundary(text.indexOf('\u05d1'))

        assertThat(resultEnglish.start, equalTo(text.indexOf('a')))
        assertThat(resultEnglish.end, equalTo(text.indexOf(' ')))
        assertThat(resultHebrew.start, equalTo(text.indexOf('\u05d0')))
        assertThat(resultHebrew.end, equalTo(text.indexOf('\u05d2') + 1))
    }

    @Test
    fun test_finalFontSizeChangesWithDensity() {
        val text = "a"
        val fontSize = 20.sp
        val densityMultiplier = 2f

        val paragraph = simpleParagraph(
            text = text,
            textStyle = TextStyle(fontSize = fontSize),
            density = Density(density = 1f, fontScale = 1f)
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        val doubleFontSizeParagraph = simpleParagraph(
            text = text,
            textStyle = TextStyle(fontSize = fontSize),
            density = Density(density = 1f, fontScale = densityMultiplier)
        )
        doubleFontSizeParagraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        assertThat(
            doubleFontSizeParagraph.maxIntrinsicWidth,
            equalTo(paragraph.maxIntrinsicWidth * densityMultiplier)
        )
        assertThat(doubleFontSizeParagraph.height, equalTo(paragraph.height * densityMultiplier))
    }

    @Test
    fun width_default_value() {
        val paragraph = simpleParagraph()

        assertThat(paragraph.width, equalTo(0.0f))
    }

    @Test
    fun height_default_value() {
        val paragraph = simpleParagraph()

        assertThat(paragraph.height, equalTo(0.0f))
    }

    @Test
    fun minIntrinsicWidth_default_value() {
        val paragraph = simpleParagraph()

        assertThat(paragraph.minIntrinsicWidth, equalTo(0.0f))
    }

    @Test
    fun minInstrinsicWidth_includes_white_space() {
        withDensity(defaultDensity) {
            val fontSize = 12.sp
            val text = "b "
            val paragraph = simpleParagraph(text = text, textStyle = TextStyle(fontSize = fontSize))

            val expectedWidth = text.length * fontSize.toPx().value
            assertThat(paragraph.minIntrinsicWidth, equalTo(expectedWidth))
        }
    }

    @Test
    fun minInstrinsicWidth_returns_longest_word_width() {
        withDensity(defaultDensity) {
            // create words with length 1, 2, 3... 50; and append all with space.
            val maxWordLength = 50
            val text = (1..maxWordLength).fold("") { string, next ->
                string + "a".repeat(next) + " "
            }
            val fontSize = 12.sp
            val paragraph = simpleParagraph(text = text, textStyle = TextStyle(fontSize = fontSize))

            // +1 is for the white space
            val expectedWidth = (maxWordLength + 1) * fontSize.toPx().value
            assertThat(paragraph.minIntrinsicWidth, equalTo(expectedWidth))
        }
    }

    @Test
    fun minInstrinsicWidth_withStyledText() {
        withDensity(defaultDensity) {
            val text = "a bb ccc"
            val fontSize = 12.sp
            val styledFontSize = fontSize * 2
            val paragraph = simpleParagraph(
                text = text,
                textStyle = TextStyle(fontSize = fontSize),
                textStyles = listOf(
                    AnnotatedString.Item(
                        TextStyle(fontSize = styledFontSize), "a".length, "a bb ".length
                    )
                )
            )

            val expectedWidth = "bb ".length * styledFontSize.toPx().value
            assertThat(paragraph.minIntrinsicWidth, equalTo(expectedWidth))
        }
    }

    @Test
    fun maxIntrinsicWidth_default_value() {
        val paragraph = simpleParagraph()

        assertThat(paragraph.maxIntrinsicWidth, equalTo(0.0f))
    }

    @Test
    fun alphabeticBaseline_default_value() {
        val paragraph = simpleParagraph()

        assertThat(paragraph.baseline, equalTo(0.0f))
    }

    @Test
    fun didExceedMaxLines_default_value() {
        val paragraph = simpleParagraph()

        assertThat(paragraph.didExceedMaxLines, equalTo(false))
    }

    @Test(expected = IllegalStateException::class)
    fun paint_throws_exception_if_layout_is_not_called() {
        val paragraph = simpleParagraph()

        paragraph.paint(mock(Canvas::class.java))
    }

    @Test(expected = IllegalStateException::class)
    fun getOffsetForPosition_throws_exception_if_layout_is_not_called() {
        val paragraph = simpleParagraph()

        paragraph.getOffsetForPosition(PxPosition.Origin)
    }

    @Test(expected = AssertionError::class)
    fun getPathForRange_throws_exception_if_start_larger_than_end() {
        val text = "ab"
        val textStart = 0
        val textEnd = text.length
        val paragraph = simpleParagraph(text = text)

        paragraph.getPathForRange(textEnd, textStart)
    }

    @Test(expected = AssertionError::class)
    fun getPathForRange_throws_exception_if_start_is_smaller_than_zero() {
        val text = "ab"
        val textStart = 0
        val textEnd = text.length
        val paragraph = simpleParagraph(text = text)

        paragraph.getPathForRange(textStart - 2, textEnd - 1)
    }

    @Test(expected = AssertionError::class)
    fun getPathForRange_throws_exception_if_end_is_larger_than_text_length() {
        val text = "ab"
        val textStart = 0
        val textEnd = text.length
        val paragraph = simpleParagraph(text = text)

        paragraph.getPathForRange(textStart, textEnd + 1)
    }

    private fun simpleParagraph(
        text: String = "",
        textIndent: TextIndent? = null,
        textAlign: TextAlign? = null,
        fontSize: Sp? = null,
        maxLines: Int? = null,
        lineHeight: Float? = null,
        textStyles: List<AnnotatedString.Item<TextStyle>> = listOf(),
        fontFamily: FontFamily = fontFamilyMeasureFont,
        locale: Locale? = null,
        textStyle: TextStyle? = null,
        density: Density? = null,
        textDirectionAlgorithm: TextDirectionAlgorithm? = null,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr
    ): Paragraph {
        return Paragraph(
            text = text,
            textStyles = textStyles,
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = fontSize,
                locale = locale
            ).merge(textStyle),
            paragraphStyle = ParagraphStyle(
                textIndent = textIndent,
                textAlign = textAlign,
                textDirectionAlgorithm = textDirectionAlgorithm,
                lineHeight = lineHeight
            ),
            maxLines = maxLines,
            density = density ?: defaultDensity,
            layoutDirection = layoutDirection,
            resourceLoader = TestFontResourceLoader(context)
        )
    }
}

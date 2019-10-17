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

import androidx.ui.core.sp
import androidx.ui.graphics.Color
import androidx.ui.text.AnnotatedString.Item
import androidx.ui.text.style.TextAlign
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class AnnotatedStringTest {
    @Test
    fun normalizedParagraphStyles() {
        val text = "Hello World"
        val paragraphStyle = ParagraphStyle(textAlign = TextAlign.Center)
        val paragraphStyles = listOf(Item(paragraphStyle, 0, 5))
        val annotatedString = AnnotatedString(text = text, paragraphStyles = paragraphStyles)
        val defaultParagraphStyle = ParagraphStyle(lineHeight = 20.sp)

        val paragraphs = annotatedString.normalizedParagraphStyles(defaultParagraphStyle)

        assertThat(paragraphs).isEqualTo(
            listOf(
                Item(defaultParagraphStyle.merge(paragraphStyle), 0, 5),
                Item(defaultParagraphStyle, 5, text.length)
            )
        )
    }

    @Test
    fun normalizedParagraphStyles_only_string() {
        val text = "Hello World"
        val annotatedString = AnnotatedString(text = text)
        val defaultParagraphStyle = ParagraphStyle(lineHeight = 20.sp)

        val paragraphs = annotatedString.normalizedParagraphStyles(defaultParagraphStyle)

        assertThat(paragraphs).isEqualTo(listOf(Item(defaultParagraphStyle, 0, text.length)))
    }

    @Test
    fun normalizedParagraphStyles_empty_string() {
        val text = ""
        val annotatedString = AnnotatedString(text = text)
        val defaultParagraphStyle = ParagraphStyle(lineHeight = 20.sp)

        val paragraphs = annotatedString.normalizedParagraphStyles(defaultParagraphStyle)

        assertThat(paragraphs).isEqualTo(listOf(Item(defaultParagraphStyle, 0, text.length)))
    }

    @Test
    fun normalizedParagraphStyles_with_newLine() {
        val text = "Hello\nWorld"
        val annotatedString = AnnotatedString(text = text)
        val defaultParagraphStyle = ParagraphStyle(lineHeight = 20.sp)

        val paragraphs = annotatedString.normalizedParagraphStyles(defaultParagraphStyle)

        assertThat(paragraphs).isEqualTo(listOf(Item(defaultParagraphStyle, 0, text.length)))
    }

    @Test
    fun normalizedParagraphStyles_with_only_lineFeed() {
        val text = "\n"
        val annotatedString = AnnotatedString(text = text)
        val defaultParagraphStyle = ParagraphStyle(lineHeight = 20.sp)

        val paragraphs = annotatedString.normalizedParagraphStyles(defaultParagraphStyle)

        assertThat(paragraphs).isEqualTo(listOf(Item(defaultParagraphStyle, 0, 1)))
    }

    @Test
    fun length_returns_text_length() {
        val text = "abc"
        val annotatedString = AnnotatedString(text)
        assertThat(annotatedString.length).isEqualTo(text.length)
    }

    @Test
    fun plus_operator_creates_a_new_annotated_string() {
        val text1 = "Hello"
        val textStyles1 = listOf(
            Item(TextStyle(color = Color.Red), 0, 3),
            Item(TextStyle(color = Color.Blue), 2, 4)
        )
        val paragraphStyles1 = listOf(
            Item(ParagraphStyle(lineHeight = 20.sp), 0, 1),
            Item(ParagraphStyle(lineHeight = 30.sp), 1, 5)
        )
        val annotatedString1 = AnnotatedString(
            text = text1,
            textStyles = textStyles1,
            paragraphStyles = paragraphStyles1
        )

        val text2 = "World"
        val textStyle = TextStyle(color = Color.Cyan)
        val paragraphStyle = ParagraphStyle(lineHeight = 10.sp)
        val annotatedString2 = AnnotatedString(
            text = text2,
            textStyles = listOf(Item(textStyle, 0, text2.length)),
            paragraphStyles = listOf(Item(paragraphStyle, 0, text2.length))
        )

        assertThat(annotatedString1 + annotatedString2).isEqualTo(
            AnnotatedString(
                "$text1$text2",
                textStyles1 + listOf(
                    Item(textStyle, text1.length, text1.length + text2.length)
                ),
                paragraphStyles1 + listOf(
                    Item(paragraphStyle, text1.length, text1.length + text2.length)
                )
            )
        )
    }

    @Test
    fun subSequence_returns_the_correct_string() {
        val annotatedString = AnnotatedString.Builder("abcd").toAnnotatedString()

        assertThat(annotatedString.subSequence(1, 3).text).isEqualTo("bc")
    }

    @Test
    fun subSequence_returns_empty_text_for_start_equals_end() {
        val annotatedString = with(AnnotatedString.Builder()) {
            withStyle(TextStyle(fontSize = 12.sp)) {
                append("a")
            }
            withStyle(TextStyle(fontSize = 12.sp)) {
                append("b")
            }
            withStyle(ParagraphStyle(lineHeight = 14.sp)) {
                append("c")
            }
            toAnnotatedString()
        }.subSequence(1, 1)

        assertThat(annotatedString).isEqualTo(
            AnnotatedString("", listOf(Item(TextStyle(fontSize = 12.sp), 0, 0)))
        )
    }

    @Test
    fun subSequence_doesNot_include_styles_before_the_start() {
        val annotatedString = with(AnnotatedString.Builder()) {
            withStyle(TextStyle(fontSize = 12.sp)) {
                append("a")
            }
            withStyle(ParagraphStyle(lineHeight = 14.sp)) {
                append("b")
            }
            append("c")
            toAnnotatedString()
        }

        assertThat(annotatedString.subSequence("ab".length, annotatedString.length)).isEqualTo(
            AnnotatedString("c")
        )
    }

    @Test
    fun subSequence_doesNot_include_styles_after_the_end() {
        val annotatedString = with(AnnotatedString.Builder()) {
            append("a")
            withStyle(TextStyle(fontSize = 12.sp)) {
                append("b")
            }
            withStyle(ParagraphStyle(lineHeight = 14.sp)) {
                append("c")
            }
            toAnnotatedString()
        }

        assertThat(annotatedString.subSequence(0, "a".length)).isEqualTo(
            AnnotatedString("a")
        )
    }

    @Test
    fun subSequence_collapsed_item_with_itemStart_equalTo_rangeStart() {
        val style = TextStyle(fontSize = 12.sp)
        val annotatedString = with(AnnotatedString.Builder()) {
            append("abc")
            // add collapsed item at the beginning of b
            addStyle(style, 1, 1)
            toAnnotatedString()
        }

        assertThat(annotatedString.subSequence(1, 2)).isEqualTo(
            AnnotatedString("b", listOf(Item(style, 0, 0)))
        )
    }

    @Test
    fun subSequence_collapses_included_item() {
        val style = TextStyle(fontSize = 12.sp)
        val annotatedString = with(AnnotatedString.Builder()) {
            append("a")
            // will collapse this style in subsequence
            withStyle(style) {
                append("b")
            }
            append("c")
            toAnnotatedString()
        }

        // subsequence with 1,1 will remove text, but include the style
        assertThat(annotatedString.subSequence(1, 1)).isEqualTo(
            AnnotatedString("", listOf(Item(style, 0, 0)))
        )
    }

    @Test
    fun subSequence_collapses_covering_item() {
        val style = TextStyle(fontSize = 12.sp)
        val annotatedString = with(AnnotatedString.Builder()) {
            withStyle(style) {
                append("abc")
            }
            toAnnotatedString()
        }

        assertThat(annotatedString.subSequence(1, 1)).isEqualTo(
            AnnotatedString("", listOf(Item(style, 0, 0)))
        )
    }

    @Test
    fun subSequence_with_collapsed_range_with_collapsed_item() {
        val style = TextStyle(fontSize = 12.sp)
        val annotatedString = with(AnnotatedString.Builder()) {
            append("abc")
            // add collapsed item at the beginning of b
            addStyle(style, 1, 1)
            toAnnotatedString()
        }

        assertThat(annotatedString.subSequence(1, 1)).isEqualTo(
            AnnotatedString("", listOf(Item(style, 0, 0)))
        )
    }

    @Test
    fun subSequence_includes_partial_matches() {
        val annotatedString = with(AnnotatedString.Builder()) {
            withStyle(TextStyle(fontSize = 12.sp)) {
                append("ab")
            }
            withStyle(TextStyle(fontSize = 12.sp)) {
                append("c")
            }
            withStyle(ParagraphStyle(lineHeight = 14.sp)) {
                append("de")
            }
            toAnnotatedString()
        }

        val expectedString = with(AnnotatedString.Builder()) {
            withStyle(TextStyle(fontSize = 12.sp)) {
                append("b")
            }
            withStyle(TextStyle(fontSize = 12.sp)) {
                append("c")
            }
            withStyle(ParagraphStyle(lineHeight = 14.sp)) {
                append("d")
            }
            toAnnotatedString()
        }

        val subSequence = annotatedString.subSequence("a".length, "abcd".length)

        assertThat(subSequence).isEqualTo(expectedString)
    }

    @Test(expected = IllegalArgumentException::class)
    fun subSequence_throws_exception_for_start_greater_than_end() {
        AnnotatedString("ab").subSequence(1, 0)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun subSequence_throws_exception_for_negative_values() {
        AnnotatedString("abc").subSequence(-1, 2)
    }

    @Test(expected = IndexOutOfBoundsException::class)
    fun subSequence_throws_exception_when_start_is_out_of_bounds_values() {
        val text = "abc"
        AnnotatedString(text).subSequence(text.length, text.length + 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun creating_item_with_start_greater_than_end_throws_exception() {
        Item(TextStyle(color = Color.Red), 1, 0)
    }

    @Test
    fun creating_item_with_start_equal_to_end_does_not_throw_exception() {
        Item(TextStyle(color = Color.Red), 1, 1)
    }

    @Test
    fun constructor_function_with_single_textStyle() {
        val text = "a"
        val textStyle = TextStyle(color = Color.Red)
        assertThat(
            AnnotatedString(text, textStyle)
        ).isEqualTo(
            AnnotatedString(text, listOf(Item(textStyle, 0, text.length)))
        )
    }

    @Test
    fun constructor_function_with_single_paragraphStyle() {
        val text = "a"
        val paragraphStyle = ParagraphStyle(lineHeight = 12.sp)
        assertThat(
            AnnotatedString(text, paragraphStyle)
        ).isEqualTo(
            AnnotatedString(
                text,
                listOf(),
                listOf(Item(paragraphStyle, 0, text.length))
            )
        )
    }

    @Test
    fun constructor_function_with_single_textStyle_and_paragraphStyle() {
        val text = "a"
        val textStyle = TextStyle(color = Color.Red)
        val paragraphStyle = ParagraphStyle(lineHeight = 12.sp)
        assertThat(
            AnnotatedString(text, textStyle, paragraphStyle)
        ).isEqualTo(
            AnnotatedString(
                text,
                listOf(Item(textStyle, 0, text.length)),
                listOf(Item(paragraphStyle, 0, text.length))
            )
        )
    }
}
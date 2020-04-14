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

package androidx.ui.text.demos

import androidx.compose.Composable
import androidx.ui.foundation.Text
import androidx.ui.foundation.VerticalScroller
import androidx.ui.layout.Column
import androidx.ui.text.AnnotatedString
import androidx.ui.text.ParagraphStyle
import androidx.ui.text.TextStyle
import androidx.ui.text.style.TextAlign
import androidx.ui.text.style.TextIndent
import androidx.ui.text.withStyle
import androidx.ui.unit.sp

val lorem = "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Maecenas fermentum non" +
        " diam sed pretium."

@Composable
fun MultiParagraphDemo() {
    VerticalScroller {
        Column {
            TagLine(tag = "multiple paragraphs basic")
            TextDemoParagraph()
            TagLine(tag = "multiple paragraphs TextAlign")
            TextDemoParagraphTextAlign()
            TagLine(tag = "multiple paragraphs line height")
            TextDemoParagraphLineHeight()
            TagLine(tag = "multiple paragraphs TextIndent")
            TextDemoParagraphIndent()
            TagLine(tag = "multiple paragraphs TextDirection")
            TextDemoParagraphTextDirection()
        }
    }
}

@Composable
fun TextDemoParagraph() {
    val text1 = "paragraph1 paragraph1 paragraph1 paragraph1 paragraph1"
    val text2 = "paragraph2 paragraph2 paragraph2 paragraph2 paragraph2"
    Text(
        text = AnnotatedString {
            append(text1)
            withStyle(ParagraphStyle()) {
                append(text2)
            }
        },
        style = TextStyle(fontSize = fontSize6)
    )
}

@Composable
fun TextDemoParagraphTextAlign() {
    val annotatedString = AnnotatedString {
        TextAlign.values().forEach { textAlign ->
            val str = List(4) { "TextAlign.$textAlign" }.joinToString(" ")
            withStyle(ParagraphStyle(textAlign = textAlign)) {
                append(str)
            }
        }
    }

    Text(text = annotatedString, style = TextStyle(fontSize = fontSize6))
}

@Composable
fun TextDemoParagraphLineHeight() {
    val text1 = "LineHeight=30sp: $lorem"
    val text2 = "LineHeight=40sp: $lorem"
    val text3 = "LineHeight=50sp: $lorem"

    Text(
        text = AnnotatedString(
            text = text1 + text2 + text3,
            spanStyles = listOf(),
            paragraphStyles = listOf(
                AnnotatedString.Item(
                    ParagraphStyle(lineHeight = 30.sp),
                    0,
                    text1.length
                ),
                AnnotatedString.Item(
                    ParagraphStyle(lineHeight = 40.sp),
                    text1.length,
                    text1.length + text2.length
                ),
                AnnotatedString.Item(
                    ParagraphStyle(lineHeight = 50.sp),
                    text1.length + text2.length,
                    text1.length + text2.length + text3.length
                )
            )
        ),
        style = TextStyle(fontSize = fontSize6)
    )
}

@Composable
fun TextDemoParagraphIndent() {
    val text1 = "TextIndent firstLine TextIndent firstLine TextIndent firstLine"
    val text2 = "TextIndent restLine TextIndent restLine TextIndent restLine"

    Text(
        text = AnnotatedString {
            withStyle(ParagraphStyle(textIndent = TextIndent(firstLine = 20.sp))) {
                append(text1)
            }
            withStyle(ParagraphStyle(textIndent = TextIndent(restLine = 20.sp))) {
                append(text2)
            }
        },
        style = TextStyle(fontSize = fontSize6)
    )
}

@Composable
fun TextDemoParagraphTextDirection() {
    val ltrText = "Hello World! Hello World! Hello World! Hello World! Hello World!"
    val rtlText = "مرحبا بالعالم مرحبا بالعالم مرحبا بالعالم مرحبا بالعالم مرحبا بالعالم"
    Text(
        text = AnnotatedString {
            withStyle(ParagraphStyle()) {
                append(ltrText)
            }
            withStyle(ParagraphStyle()) {
                append(rtlText)
            }
        },
        style = TextStyle(fontSize = fontSize6)
    )
}

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
import androidx.compose.state
import androidx.compose.unaryPlus
import androidx.ui.core.Dp
import androidx.ui.core.Layout
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.core.dp
import androidx.ui.core.max
import androidx.ui.core.selection.Selection
import androidx.ui.core.selection.SelectionContainer
import androidx.ui.core.sp
import androidx.ui.foundation.VerticalScroller
import androidx.ui.foundation.shape.DrawShape
import androidx.ui.foundation.shape.RectangleShape
import androidx.ui.graphics.Color
import androidx.ui.layout.Column
import androidx.ui.layout.FlexRow
import androidx.ui.layout.Padding
import androidx.ui.text.AnnotatedString
import androidx.ui.text.ParagraphStyle
import androidx.ui.text.TextStyle
import androidx.ui.text.withStyle

val commonStyle = TextStyle(fontSize = 16.sp, color = Color(0xFF9e9e9e))
val commonParagraph = ParagraphStyle(lineHeight = 22.sp)

val header = TextStyle(fontSize = 22.sp, color = Color(0xFF707070))
val headerParagraph = ParagraphStyle(lineHeight = 36.sp)
val header2 = TextStyle(fontSize = 18.sp, color = Color(0xFF707070))
val header2Paragraph = ParagraphStyle(lineHeight = 30.sp)

val link = TextStyle(color = Color(0xFF03a9f4))
val highlight = TextStyle(background = Color(0xFFefefef))

val rectColor = Color(0xFFffb74d)

val langContent = arrayOf(
    arrayOf(
        "Jetpack يؤلف أساسيات",
        "Jetpack Compose عبارة عن مجموعة أدوات حديثة لبناء واجهة مستخدم " +
                "Android الأصلية. يعمل Jetpack Compose على تبسيط وتسريع " +
                "تطوير واجهة المستخدم على نظام Android باستخدام " +
                "رموز أقل وأدوات قوية وواجهات برمجة تطبيقات Kotlin البديهية."
    ),
    arrayOf(
        "添加文本元素",
        "Jetpack Compose是用于构建本机Android UI的现代工具包。 Jetpack " +
                "Compose使用更少的代码，强大的工具和直观的Kotlin API简化并加速了Android上的UI开发。"
    ),
    arrayOf(
        "एक पाठ तत्व जोड़ें",
        "रचना योग्य कार्यों को केवल अन्य रचना कार्यों के " +
                "दायरे में से बुलाया जा सकता है। किसी फंक्शन को " +
                "कंपोजिटेबल बनाने के लिए, @ कम्\u200Dपोजिट " +
                "एनोटेशन जोड़ें।"
    ),
    arrayOf(
        "ข้อมูลพื้นฐานเกี่ยวกับการเขียน Jetpack",
        "ฟังก์ชั่น Composable สามารถเรียกใช้ได้จากภายในขอบเขตของฟังก์ชั่นอื่น ๆ เท่านั้น " +
                "ในการสร้างฟังก์ชั่นคอมโพสิตให้เพิ่มคำอธิบายประกอบ @Composable"
    )
)

@Composable
fun TextSelectionSample() {
    val selection = +state<Selection?> { null }
    VerticalScroller {
        SelectionContainer(
            selection = selection.value,
            onSelectionChange = { selection.value = it }
        ) {
            Padding(12.dp) {
                Column {
                    Basics()
                    AddTextElement()
                    langContent.forEach {
                        MultiLanguage(it[0], it[1])
                    }
                    Basics()
                    MultiParagraph()
                    AddTextElement()
                }
            }
        }
    }
}

@Composable
fun Basics() {
    Text(
        text = "Jetpack Compose Basics",
        style = commonStyle.merge(header),
        paragraphStyle = headerParagraph
    )
    FlexRow {
        inflexible {
            Padding(8.dp) {
                SizedRectangle(color = rectColor, width = 48.dp, height = 48.dp)
            }
        }
        flexible(1f) {
            Text(
                text = "Jetpack Compose is a modern toolkit for building native Android UI." +
                        " Jetpack Compose simplifies and accelerates UI development on Android " +
                        "with less code, powerful tools, and intuitive Kotlin APIs.",
                style = commonStyle,
                paragraphStyle = commonParagraph
            )
        }
    }
}

@Composable
fun AddTextElement() {
    Text(
        text = "Add a text element",
        style = commonStyle.merge(header2),
        paragraphStyle = header2Paragraph
    )
    FlexRow {
        flexible(1f) {
            Column {
                Text(
                    text = AnnotatedString {
                        append("To begin, follow the")
                        withStyle(link) {
                            append(" Jetpack Compose setup instructions ")
                        }
                        append(
                            ", and create an app using the Empty Compose Activity template. Then " +
                                    "add a text element to your blank activity. You do this by " +
                                    "defining a content block, and calling the Text() function."
                        )
                    },
                    style = commonStyle,
                    paragraphStyle = commonParagraph
                )
            }
        }
        inflexible {
            Padding(8.dp) {
                SizedRectangle(color = rectColor, width = 48.dp, height = 48.dp)
            }
        }
    }
    EmptyRect()
    Text(
        text = AnnotatedString {
            withStyle(commonStyle) {
                append(
                    "The setContent block defines the activity's layout. Instead of " +
                            "defining the layout contents with an XML file, we call " +
                            "composable functions. Jetpack Compose uses a custom " +
                            "Kotlin compiler plugin to transform these composable " +
                            "functions into the app's UI elements. For example, the"
                )
                withStyle(highlight) {
                    append(" Text() ")
                }
                append(
                    " function is defined by the Compose UI library; you call that " +
                            "function to declare a text element in your app."
                )
            }
        }
    )
}

@Composable
fun MultiParagraph() {
    Text(
        text = "Define a composable function (Multi Paragraph)",
        style = commonStyle.merge(header2),
        paragraphStyle = header2Paragraph
    )
    Text(
        text = AnnotatedString {
            withStyle(commonStyle) {
                withStyle(commonParagraph) {
                    append(
                        "Composable functions can only be called from within the scope of " +
                                "other composable functions. To make a function composable, add " +
                                "the @Composable annotation. "
                    )
                }
                withStyle(commonParagraph) {
                    append(
                        "To try this out, define a Greeting() function which is passed a " +
                                "name, and uses that name to configure the text element."
                    )
                }
                withStyle(highlight) {
                    append(" Text() ")
                }
                append(
                    " function is defined by the Compose UI library; you call that " +
                            "function to declare a text element in your app."
                )
            }
        }
    )
}

@Composable
fun MultiLanguage(title: String, content: String) {
    Text(
        text = title,
        style = commonStyle.merge(header),
        paragraphStyle = headerParagraph
    )
    FlexRow {
        inflexible {
            Padding(8.dp) {
                SizedRectangle(color = rectColor, width = 48.dp, height = 48.dp)
            }
        }
        flexible(1f) {
            Text(
                text = content,
                style = commonStyle,
                paragraphStyle = commonParagraph
            )
        }
    }
}

@Composable
fun EmptyRect() {
    Padding(top = 20.dp, bottom = 20.dp) {
        SizedRectangle(color = rectColor, width = 200.dp, height = 60.dp)
    }
}

@Composable
fun SizedRectangle(
    modifier: Modifier = Modifier.None,
    color: Color,
    width: Dp? = null,
    height: Dp? = null
) {
    Layout(children = { DrawRectangle(color = color) }, modifier = modifier) { _, constraints ->
        val widthPx = max(width?.toIntPx() ?: constraints.maxWidth, constraints.minWidth)
        val heightPx = max(height?.toIntPx() ?: constraints.maxHeight, constraints.minHeight)
        layout(widthPx, heightPx) {}
    }
}

/**
 * Draws a rectangle of a given color in the space of the parent layout.
 */
@Composable
fun DrawRectangle(color: Color) {
    DrawShape(shape = RectangleShape, color = color)
}

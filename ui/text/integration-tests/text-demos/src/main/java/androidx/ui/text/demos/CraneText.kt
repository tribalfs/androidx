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

package androidx.ui.text.demos

import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.ui.core.CraneWrapper
import androidx.ui.core.EditableText
import androidx.ui.core.EditorStyle
import androidx.ui.core.Selection
import androidx.ui.core.SelectionContainer
import androidx.ui.core.Text
import androidx.ui.core.px
import androidx.ui.engine.geometry.Offset
import androidx.ui.engine.text.BaselineShift
import androidx.ui.engine.text.FontStyle
import androidx.ui.engine.text.FontWeight
import androidx.ui.engine.text.TextAlign
import androidx.ui.engine.text.TextDecoration
import androidx.ui.engine.text.TextDirection
import androidx.ui.engine.text.font.FontFamily
import androidx.ui.engine.window.Locale
import androidx.ui.input.EditorState
import androidx.ui.graphics.Color
import androidx.ui.graphics.lerp
import androidx.ui.painting.Shadow
import androidx.ui.painting.TextStyle
import androidx.ui.rendering.paragraph.TextOverflow
import androidx.compose.Composable
import androidx.compose.composer
import androidx.compose.state
import androidx.compose.unaryPlus
import androidx.ui.core.Span

val displayText = "Text Demo"
val displayTextChinese = "文本演示"
val displayTextArabic = "عرض النص"
val displayTextHindi = "पाठ डेमो"
val fontSize4: Float = 40.0.toFloat()
val fontSize6: Float = 60.0.toFloat()
val fontSize7: Float = 70.0.toFloat()
val fontSize8: Float = 80.0.toFloat()
val fontSize10: Float = 100.0.toFloat()

@Composable
fun TextDemo() {
    LinearLayout(
        orientation = LinearLayout.VERTICAL,
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.MATCH_PARENT
        )
    ) {
        ScrollView {
            LinearLayout(orientation = LinearLayout.VERTICAL) {
                TagLine(tag = "color, fontSize, fontWeight and fontStyle")
                TextDemoBasic()
                TagLine(tag = "Chinese, Arabic, and Hindi")
                TextDemoLanguage()
                TagLine(tag = "FontFamily: sans-serif, serif, and monospace")
                TextDemoFontFamily()
                TagLine(tag = "decoration, decorationColor and decorationStyle")
                TextDemoTextDecoration()
                TagLine(tag = "letterSpacing")
                TextDemoLetterSpacing()
                TagLine(tag = "wordSpacing")
                TextDemoWordSpacing()
                TagLine(tag = "baselineShift")
                TextDemoBaselineShift()
                TagLine(tag = "height")
                TextDemoHeight()
                TagLine(tag = "background")
                TextDemoBackground()
                TagLine(tag = "Locale: Japanese, Simplified and Traditional Chinese")
                TextDemoLocale()
                TagLine(tag = "textAlign and textDirection")
                TextDemoTextAlign()
                TagLine(tag = "softWrap: on and off")
                TextDemoSoftWrap()
                TagLine(tag = "textScaleFactor: default and 2.0")
                TextDemoTextScaleFactor()
                TagLine(tag = "TextOverFlow: FADE")
                TexDemoTextOverflowFade()
                TagLine(tag = "shadow")
                TextDemoShadowEffect()
                TagLine(tag = "editing")
                EditLine()
                TagLine(tag = "selection")
                TextDemoSelection()
                TagLine(tag = "composable textspan")
                TextDemoComposableTextSpan()
            }
        }
    }
}

@Composable
fun TagLine(tag: String) {
    CraneWrapper {
        Text {
            Span(text = "\n", style = TextStyle(fontSize = fontSize8))
            Span(
                text = tag,
                style = TextStyle(color = Color(0xFFAAAAAA.toInt()), fontSize = fontSize6)
            )
        }
    }
}

@Composable
fun SecondTagLine(tag: String) {
    CraneWrapper {
        Text {
            Span(
                text = tag,
                style = TextStyle(color = Color(0xFFAAAAAA.toInt()), fontSize = fontSize4)
            )
        }
    }
}

@Composable
fun TextDemoBasic() {
    // This group of text widgets show different color, fontSize, fontWeight and fontStyle in
    // English.
    CraneWrapper {
        Text {
            Span(
                text = "$displayText   ",
                style = TextStyle(
                    color = Color(0xFFFF0000.toInt()),
                    fontSize = fontSize6,
                    fontWeight = FontWeight.w200,
                    fontStyle = FontStyle.italic
                )
            )

            Span(
                text = "$displayText   ",
                style = TextStyle(
                    color = Color(0xFF00FF00.toInt()),
                    fontSize = fontSize8,
                    fontWeight = FontWeight.w500,
                    fontStyle = FontStyle.normal
                )
            )

            Span(
                text = displayText,
                style = TextStyle(
                    color = Color(0xFF0000FF.toInt()),
                    fontSize = fontSize10,
                    fontWeight = FontWeight.w800,
                    fontStyle = FontStyle.normal
                )
            )
        }
    }
}

@Composable
fun TextDemoLanguage() {
    // This group of text widgets show different color, fontSize, fontWeight and fontStyle in
    // Chinese, Arabic, and Hindi.
    CraneWrapper {
        Text {
            Span(
                text = "$displayTextChinese   ",
                style = TextStyle(
                    color = Color(0xFFFF0000.toInt()),
                    fontSize = fontSize6,
                    fontWeight = FontWeight.w200,
                    fontStyle = FontStyle.italic
                )
            )

            Span(
                text = "$displayTextArabic   ",
                style = TextStyle(
                    color = Color(0xFF00FF00.toInt()),
                    fontSize = fontSize8,
                    fontWeight = FontWeight.w500,
                    fontStyle = FontStyle.normal
                )
            )

            Span(
                text = displayTextHindi,
                style = TextStyle(
                    color = Color(0xFF0000FF.toInt()),
                    fontSize = fontSize10,
                    fontWeight = FontWeight.w800,
                    fontStyle = FontStyle.normal
                )
            )
        }
    }
}

@Composable
fun TextDemoFontFamily() {
    // This group of text widgets show different fontFamilies in English.
    CraneWrapper {
        Text {
            Span(text = "$displayText   ", style = TextStyle(
                    fontSize = fontSize8,
                    fontFamily = FontFamily("sans-serif")
                )
            )

            Span(text = "$displayText   ", style = TextStyle(
                    fontSize = fontSize8,
                    fontFamily = FontFamily("serif")
                )
            )

            Span(text = displayText, style = TextStyle(
                    fontSize = fontSize8,
                    fontFamily = FontFamily("monospace")
                )
            )
        }
    }
}

@Composable
fun TextDemoTextDecoration() {
    // This group of text widgets show different decoration, decorationColor and decorationStyle.
    CraneWrapper {
        Text {
            Span(text = displayText, style = TextStyle(
                    fontSize = fontSize8,
                    decoration = TextDecoration.LineThrough
                )
            )

            Span(text = "$displayText\n", style = TextStyle(
                    fontSize = fontSize8,
                    decoration = TextDecoration.Underline
                )
            )

            Span(text = displayText, style = TextStyle(
                    fontSize = fontSize8,
                    decoration = TextDecoration.combine(
                        listOf(
                            TextDecoration.Underline,
                            TextDecoration.LineThrough
                        )
                    )
                )
            )
        }
    }
}

@Composable
fun TextDemoLetterSpacing() {
    // This group of text widgets show different letterSpacing.
    CraneWrapper {
        Text {
            Span(text = "$displayText   ", style = TextStyle(fontSize = fontSize8))
            Span(text = displayText, style = TextStyle(fontSize = fontSize8, letterSpacing = 0.5f))
        }
    }
}

@Composable
fun TextDemoWordSpacing() {
    // This group of text widgets show different wordSpacing.
    CraneWrapper {
        Text {
            Span(text = "$displayText   ", style = TextStyle(fontSize = fontSize8))

            Span(
                text = displayText,
                style = TextStyle(
                    fontSize = fontSize8,
                    wordSpacing = 100.0f
                )
            )
        }
    }
}

@Composable
fun TextDemoBaselineShift() {
    CraneWrapper {
        Text {
            Span(text = displayText, style = TextStyle(fontSize = fontSize8)) {
                Span(
                    text = "superscript",
                    style = TextStyle(
                        baselineShift = BaselineShift.SUPERSCRIPT,
                        fontSize = fontSize4
                    )
                ) {
                    Span(
                        text = "subscript",
                        style = TextStyle(
                            baselineShift = BaselineShift.SUBSCRIPT,
                            fontSize = fontSize4
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun TextDemoHeight() {
    // This group of text widgets show different height.
    LinearLayout(orientation = LinearLayout.HORIZONTAL) {
        CraneWrapper {
            Text {
                Span(
                    text = "$displayText\n$displayText   ",
                    style = TextStyle(fontSize = fontSize8)
                )
            }
        }
        CraneWrapper {
            Text {
                Span(
                    text = "$displayText\n$displayText   ",
                    style = TextStyle(
                        fontSize = fontSize8,
                        height = 2.0f
                    )
                )
            }
        }
    }
}

@Composable
fun TextDemoBackground() {
    // This group of text widgets show different background.
    CraneWrapper {
        Text {
            Span(
                text = "$displayText   ",
                style = TextStyle(
                    fontSize = fontSize8,
                    background = Color(0xFFFF0000.toInt())
                )
            )

            Span(
                text = "$displayText   ",
                style = TextStyle(
                    fontSize = fontSize8,
                    background = Color(0xFF00FF00.toInt())
                )
            )

            Span(
                text = displayText,
                style = TextStyle(
                    fontSize = fontSize8,
                    background = Color(0xFF0000FF.toInt())
                )
            )
        }
    }
}

@Composable
fun TextDemoLocale() {
    // This group of text widgets show different Locales of the same Unicode codepoint.
    val text = "\u82B1"
    CraneWrapper {
        Text {
            Span(
                text = "$text   ",
                style = TextStyle(
                        fontSize = fontSize8,
                        locale = Locale(_languageCode = "ja", _countryCode = "JP")
                )
            )

            Span(
                text = "$text   ",
                style = TextStyle(
                    fontSize = fontSize8,
                    locale = Locale(_languageCode = "zh", _countryCode = "CN")
                )
            )

            Span(
                text = text,
                style = TextStyle(
                    fontSize = fontSize8,
                    locale = Locale(_languageCode = "zh", _countryCode = "TW")
                )
            )
        }
    }
}

@Composable
fun TextDemoTextAlign() {
    // This group of text widgets show different TextAligns: LEFT, RIGHT, CENTER, JUSTIFY, START for
    // LTR and RTL, END for LTR and RTL.
    var text: String = ""
    for (i in 1..10) {
        text = "$text$displayText "
    }
    LinearLayout(orientation = LinearLayout.VERTICAL) {
        SecondTagLine(tag = "textAlgin = TextAlign.LEFT")
        CraneWrapper {
            Text(textAlign = TextAlign.LEFT) {
                Span(text = displayText, style = TextStyle(fontSize = fontSize8))
            }
        }
        SecondTagLine(tag = "textAlgin = TextAlign.RIGHT")
        CraneWrapper {
            Text(textAlign = TextAlign.RIGHT) {
                Span(text = displayText, style = TextStyle(fontSize = fontSize8))
            }
        }
        SecondTagLine(tag = "textAlgin = TextAlign.CENTER")
        CraneWrapper {
            Text(textAlign = TextAlign.CENTER) {
                Span(text = displayText, style = TextStyle(fontSize = fontSize8))
            }
        }
        SecondTagLine(tag = "textAlgin = default and TextAlign.JUSTIFY")
        CraneWrapper {
            Text {
                Span(
                    text = text,
                    style = TextStyle(
                        fontSize = fontSize8,
                        color = Color(0xFFFF0000.toInt())
                    )
                )
            }
        }
        CraneWrapper {
            Text(textAlign = TextAlign.JUSTIFY) {
                Span(
                    text = text,
                    style = TextStyle(
                        fontSize = fontSize8,
                        color = Color(0xFF0000FF.toInt())
                    )
                )
            }
        }
        SecondTagLine(tag = "textAlgin = TextAlign.START for LTR")
        CraneWrapper {
            Text(textAlign = TextAlign.START) {
                Span(text = displayText, style = TextStyle(fontSize = fontSize8))
            }
        }
        SecondTagLine(tag = "textAlgin = TextAlign.START for RTL")
        CraneWrapper {
            Text(textDirection = TextDirection.RTL, textAlign = TextAlign.START) {
                Span(text = displayText, style = TextStyle(fontSize = fontSize8))
            }
        }
        SecondTagLine(tag = "textAlgin = TextAlign.END for LTR")
        CraneWrapper {
            Text(textAlign = TextAlign.END) {
                Span(text = displayText, style = TextStyle(fontSize = fontSize8))
            }
        }
        SecondTagLine(tag = "textAlgin = TextAlign.END for RTL")
        CraneWrapper {
            Text(textDirection = TextDirection.RTL, textAlign = TextAlign.END) {
                Span(text = displayText, style = TextStyle(fontSize = fontSize8))
            }
        }
    }
}

@Composable
fun TextDemoSoftWrap() {
    // This group of text widgets show difference between softWrap is true and false.
    var text: String = ""
    for (i in 1..10) {
        text = "$text$displayText"
    }
    val textStyle = TextStyle(fontSize = fontSize8, color = Color(0xFFFF0000.toInt()))

    LinearLayout(orientation = LinearLayout.VERTICAL) {
        CraneWrapper {
            Text {
                Span(text = text, style = textStyle)
            }
        }
        CraneWrapper {
            Text(softWrap = false) {
                Span(text = text, style = textStyle)
            }
        }
    }
}

// TODO(Migration/qqd): Impelement text demo for overflow and maxLines.
@Composable
fun TextDemoOverflow() {
}

@Composable
fun TextDemoMaxLines() {
}

@Composable
fun TextDemoTextScaleFactor() {
    // This group of text widgets show the different textScaleFactor.
    LinearLayout(orientation = LinearLayout.VERTICAL) {
        CraneWrapper {
            Text {
                Span(text = displayText, style = TextStyle(fontSize = fontSize8))
            }
        }
        CraneWrapper {
            Text(textScaleFactor = 2.0f) {
                Span(text = displayText, style = TextStyle(fontSize = fontSize8))
            }
        }
    }
}

@Composable
fun TexDemoTextOverflowFade() {
    var text = ""
    for (i in 1..15) {
        text = text + displayText
    }
    val textSytle = TextStyle(fontSize = fontSize8, color = Color(0xFFFF0000.toInt()))
    SecondTagLine(tag = "horizontally fading edge")
    CraneWrapper {
        Text(
            maxLines = 1,
            overflow = TextOverflow.FADE,
            softWrap = false
        ) {
            Span(text = text, style = textSytle)
        }
    }
    SecondTagLine(tag = "vertically fading edge")
    CraneWrapper {
        Text(
            maxLines = 3,
            overflow = TextOverflow.FADE
        ) {
            Span(text = text, style = textSytle)
        }
    }
}

@Composable
fun TextDemoShadowEffect() {
    val shadow = Shadow(
        Color(0xFFE0A0A0.toInt()),
        Offset(5f, 5f),
        blurRadius = 5.px
    )
    CraneWrapper {
        Text {
            Span(text = "text with ", style = TextStyle(fontSize = fontSize8)) {
                Span(text = "shadow!", style = TextStyle(shadow = shadow))
            }
        }
    }
}

@Composable
fun EditLine() {
    val state = +state { EditorState() }
    CraneWrapper {
        EditableText(
            value = state.value,
            onValueChange = { state.value = it },
            editorStyle = EditorStyle(textStyle = TextStyle(fontSize = fontSize8))
        )
    }
}

@Composable
fun TextDemoSelection() {
    val selection = +state<Selection?> { null }
    CraneWrapper {
        SelectionContainer(
                selection = selection.value,
                onSelectionChange = { selection.value = it }) {
            Text {
                Span(style = TextStyle(
                    color = Color(0xFFFF0000.toInt()),
                    fontSize = fontSize6,
                    fontWeight = FontWeight.w200,
                    fontStyle = FontStyle.italic)
                ) {
                    Span(text = "$displayText   ")
                    Span(text = "$displayTextChinese   ")
                    Span(
                        text = displayTextHindi,
                        style = TextStyle(
                            color = Color(0xFF0000FF.toInt()),
                            fontSize = fontSize10,
                            fontWeight = FontWeight.w800,
                            fontStyle = FontStyle.normal
                        )
                    )
                    Span(
                        text = "\n先帝创业未半而中道崩殂，今天下三分，益州疲弊，此诚危急存亡之秋也。",
                        style = TextStyle(locale = Locale("zh", "CN"))
                    )
                    Span(
                        text = "\nまず、現在天下が魏・呉・蜀に分れており、そのうち蜀は疲弊していることを指摘する。",
                        style = TextStyle(locale = Locale("ja", "JP"))
                    )
                }
            }
        }
    }
}

@Composable
fun TextDemoComposableTextSpan() {
    CraneWrapper {
        Text {
            Span(text = "This is a ", style = TextStyle(fontSize = fontSize8)) {
                Span(text = "composable ", style = TextStyle(fontStyle = FontStyle.italic))
                val color1 = Color(0xFFEF50AD.toInt())
                val color2 = Color(0xFF10AF52.toInt())
                val text = "TextSpan"
                text.forEachIndexed { index, ch ->
                    val color = lerp(color1, color2, index.toFloat() / text.lastIndex)
                    Span(text = "$ch", style = TextStyle(color = color))
                }
            }
        }
    }
}

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

package androidx.compose.foundation.demos.text

import androidx.compose.foundation.demos.text2.BasicSecureTextFieldDemos
import androidx.compose.foundation.demos.text2.BasicTextField2CustomPinFieldDemo
import androidx.compose.foundation.demos.text2.BasicTextField2Demos
import androidx.compose.foundation.demos.text2.BasicTextField2FilterDemos
import androidx.compose.foundation.demos.text2.DecorationBoxDemos
import androidx.compose.foundation.demos.text2.KeyboardOptionsDemos
import androidx.compose.foundation.demos.text2.ScrollableDemos
import androidx.compose.integration.demos.common.ComposableDemo
import androidx.compose.integration.demos.common.DemoCategory

val TextDemos = DemoCategory(
    "Text",
    listOf(
        ComposableDemo("Text Accessibility") { TextAccessibilityDemo() },
        DemoCategory(
            "Text Canvas",
            listOf(
                ComposableDemo("Brush") { TextBrushDemo() },
                ComposableDemo("drawText") { DrawTextDemo() },
                ComposableDemo("Stroke") { TextStrokeDemo() }
            )
        ),
        DemoCategory(
            "Animation",
            listOf(
                ComposableDemo("color = { animatedColor.value }") { TextColorAnimation() },
                ComposableDemo("GraphicsLayer (skew, scale, etc)") { TextAnimationDemo() },
            )
        ),
        DemoCategory(
            "Text Layout",
            listOf(
                ComposableDemo("Static text") { TextDemo() },
                DemoCategory(
                    "Line breaking",
                    listOf(
                        ComposableDemo("Line Break") { TextLineBreakDemo() },
                        ComposableDemo("Hyphens") { TextDemoHyphens() },
                        ComposableDemo("Ellipsize") { EllipsizeDemo() },
                        ComposableDemo("Ellipsize and letterspacing") {
                            EllipsizeWithLetterSpacing()
                        },
                        ComposableDemo("Letterspacing") {
                            LetterSpacingDemo()
                        }
                    )
                ),
                DemoCategory(
                    "Text Overflow",
                    listOf(
                        ComposableDemo("TextOverflow demo") { TextOverflowDemo() },
                        ComposableDemo("Visible overflow in drawText") {
                            TextOverflowVisibleInDrawText()
                        },
                        ComposableDemo("Visible overflow in Popup") {
                            TextOverflowVisibleInPopupDemo()
                        },
                        ComposableDemo("Min/max lines") { BasicTextMinMaxLinesDemo() },
                    )
                ),
                ComposableDemo("IncludeFontPadding & Clip") { TextFontPaddingDemo() },
                ComposableDemo("Line Height Behavior") { TextLineHeightDemo() },
                ComposableDemo("Layout Reuse") { TextReuseLayoutDemo() },
                ComposableDemo("Multi paragraph") { MultiParagraphDemo() },
                ComposableDemo("Interactive text") { InteractiveTextDemo() },
            )
        ),
        DemoCategory(
            "Fonts",
            listOf(
                ComposableDemo("Typeface") { TypefaceDemo() },
                ComposableDemo("Variable Fonts") { VariableFontsDemo() },
                ComposableDemo("FontFamily fallback") { FontFamilyDemo() },
                ComposableDemo("All system font families") { SystemFontFamilyDemo() },
                ComposableDemo("Emoji Compat") { EmojiCompatDemo() },
            )
        ),
        DemoCategory(
            "Text Input",
            listOf(
                ComposableDemo("Basic input fields") { InputFieldDemo() },
                ComposableDemo("Capitalization/AutoCorrect") {
                    CapitalizationAutoCorrectDemo()
                },
                ComposableDemo("Cursor configuration") { TextFieldCursorBlinkingDemo() },
                DemoCategory(
                    "Focus",
                    listOf(
                        ComposableDemo("Focus transition") { TextFieldFocusTransition() },
                        ComposableDemo("Focus keyboard interaction") {
                            TextFieldFocusKeyboardInteraction()
                        },
                    )
                ),
                ComposableDemo("Full-screen field") { FullScreenTextFieldDemo() },
                ComposableDemo("Ime Action") { ImeActionDemo() },
                ComposableDemo("Ime SingleLine") { ImeSingleLineDemo() },
                ComposableDemo("Inside Dialog") { TextFieldsInDialogDemo() },
                ComposableDemo("Inside scrollable") { TextFieldsInScrollableDemo() },
                ComposableDemo("Keyboard Types") { KeyboardTypeDemo() },
                ComposableDemo("Min/Max Lines") { BasicTextFieldMinMaxDemo() },
                ComposableDemo("Reject Text Change") { RejectTextChangeDemo() },
                ComposableDemo("Scrollable text fields") { ScrollableTextFieldDemo() },
                ComposableDemo("Visual Transformation") { VisualTransformationDemo() },
                ComposableDemo("TextFieldValue") { TextFieldValueDemo() },
                ComposableDemo("Tail Following Text Field") { TailFollowingTextFieldDemo() },
                ComposableDemo("Focus immediately") { FocusTextFieldImmediatelyDemo() },
                ComposableDemo("Secondary input system") { PlatformTextInputAdapterDemo() },
                ComposableDemo("TextField focus") { TextFieldFocusDemo() },
            )
        ),
        DemoCategory(
            "BasicTextField2",
            listOf(
                ComposableDemo("Basic text input") { BasicTextField2Demos() },
                ComposableDemo("Keyboard Options") { KeyboardOptionsDemos() },
                ComposableDemo("Decoration Box") { DecorationBoxDemos() },
                ComposableDemo("Scroll") { ScrollableDemos() },
                ComposableDemo("Filters") { BasicTextField2FilterDemos() },
                ComposableDemo("Secure Field") { BasicSecureTextFieldDemos() },
                ComposableDemo("Custom PIN field") { BasicTextField2CustomPinFieldDemo() },
            )
        ),
        DemoCategory(
            "Selection",
            listOf(
                ComposableDemo("Text selection") { TextSelectionDemo() },
                ComposableDemo("Text selection sample") { TextSelectionSample() },
                ComposableDemo("Overflowed Selection") { TextOverflowedSelectionDemo() },
            )
        ),
        DemoCategory(
            "\uD83D\uDD75️️️ Memory allocs",
            listOf(
                ComposableDemo("\uD83D\uDD75️ SetText") { MemoryAllocsSetText() },
                ComposableDemo("\uD83D\uDD75️ IfNotEmptyText") { MemoryAllocsIfNotEmptyText() },
                ComposableDemo("\uD83E\uDDA5 LazyList reuse") { MemoryAllocsLazyList() }
            )
        )
    )
)

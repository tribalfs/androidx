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

package androidx.ui.text.selection

import android.content.Context
import android.graphics.Typeface
import androidx.activity.ComponentActivity
import androidx.core.content.res.ResourcesCompat
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.ui.core.Constraints
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.LayoutDirection
import androidx.ui.core.selection.Selectable
import androidx.ui.core.selection.Selection
import androidx.ui.test.android.AndroidComposeTestRule
import androidx.ui.text.AnnotatedString
import androidx.ui.text.SpanStyle
import androidx.ui.text.TextDelegate
import androidx.ui.text.TextLayoutResult
import androidx.ui.text.TextStyle
import androidx.ui.text.font.Font
import androidx.ui.text.font.FontStyle
import androidx.ui.text.font.FontWeight
import androidx.ui.text.font.ResourceFont
import androidx.ui.text.font.asFontFamily
import androidx.ui.text.font.font
import androidx.ui.text.font.test.R
import androidx.ui.text.style.TextDirection
import androidx.ui.unit.Density
import androidx.ui.unit.PxPosition
import androidx.ui.unit.TextUnit
import androidx.ui.unit.px
import androidx.ui.unit.sp
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

val BASIC_MEASURE_FONT = font(
    resId = R.font.sample_font,
    weight = FontWeight.Normal,
    style = FontStyle.Normal
)

@RunWith(JUnit4::class)
@SmallTest
class TextSelectionDelegateTest {
    @get:Rule
    val composeTestRule = AndroidComposeTestRule<ComponentActivity>()

    private val fontFamily = BASIC_MEASURE_FONT.asFontFamily()
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val defaultDensity = Density(density = 1f)
    private val resourceLoader = TestFontResourceLoader(context)

    @Test
    fun getHandlePosition_StartHandle_invalid() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "hello world\n"
                val fontSize = 20.sp

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val selectableInvalid = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { null },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('h')
                val endOffset = text.indexOf('o')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectableInvalid
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectableInvalid
                    ),
                    handlesCrossed = false
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = true
                )

                // Assert.
                assertThat(coordinates).isEqualTo(PxPosition.Origin)
            }
        }
    }

    @Test
    fun getHandlePosition_EndHandle_invalid() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "hello world\n"
                val fontSize = 20.sp

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val selectableInvalid = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { null },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('h')
                val endOffset = text.indexOf('o')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectableInvalid
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectableInvalid
                    ),
                    handlesCrossed = false
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = false
                )

                // Assert.
                assertThat(coordinates).isEqualTo(PxPosition.Origin)
            }
        }
    }

    @Test
    fun getHandlePosition_StartHandle_not_cross_ltr() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "hello world\n"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('h')
                val endOffset = text.indexOf('o')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = false
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = true
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * startOffset).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_StartHandle_cross_ltr() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "hello world\n"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('o')
                val endOffset = text.indexOf('h')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = true
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = true
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * startOffset).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_StartHandle_not_cross_rtl() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('\u05D1')
                val endOffset = text.indexOf('\u05D5')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = false
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = true
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * (text.length - 1 - startOffset)).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_StartHandle_cross_rtl() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('\u05D5')
                val endOffset = text.indexOf('\u05D1')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = true
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = true
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * (text.length - 1 - startOffset)).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_StartHandle_not_cross_bidi() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val textLtr = "Hello"
                val textRtl = "\u05D0\u05D1\u05D2"
                val text = textLtr + textRtl
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('\u05D0')
                val endOffset = text.indexOf('\u05D2')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = false
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = true
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * (text.length)).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_StartHandle_cross_bidi() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val textLtr = "Hello"
                val textRtl = "\u05D0\u05D1\u05D2"
                val text = textLtr + textRtl
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('\u05D0')
                val endOffset = text.indexOf('H')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = true
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = true
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * (textLtr.length)).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_EndHandle_not_cross_ltr() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "hello world\n"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('h')
                val endOffset = text.indexOf('o')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = false
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = false
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * endOffset).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_EndHandle_cross_ltr() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "hello world\n"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('o')
                val endOffset = text.indexOf('h')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = true
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = false
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * endOffset).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_EndHandle_not_cross_rtl() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('\u05D1')
                val endOffset = text.indexOf('\u05D5')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = false
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = false
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * (text.length - 1 - endOffset)).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_EndHandle_cross_rtl() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('\u05D5')
                val endOffset = text.indexOf('\u05D1')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = true
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = false
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * (text.length - 1 - endOffset)).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_EndHandle_not_cross_bidi() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val textLtr = "Hello"
                val textRtl = "\u05D0\u05D1\u05D2"
                val text = textLtr + textRtl
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('e')
                val endOffset = text.indexOf('\u05D0')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = false
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = false
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * (textLtr.length)).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getHandlePosition_EndHandle_cross_bidi() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val textLtr = "Hello"
                val textRtl = "\u05D0\u05D1\u05D2"
                val text = textLtr + textRtl
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val startOffset = text.indexOf('\u05D2')
                val endOffset = text.indexOf('\u05D0')

                val selection = Selection(
                    start = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = startOffset,
                        selectable = selectable
                    ),
                    end = Selection.AnchorInfo(
                        direction = TextDirection.Ltr,
                        offset = endOffset,
                        selectable = selectable
                    ),
                    handlesCrossed = true
                )

                // Act.
                val coordinates = selectable.getHandlePosition(
                    selection = selection,
                    isStartHandle = false
                )

                // Assert.
                assertThat(coordinates).isEqualTo(
                    PxPosition((fontSizeInPx * (text.length)).px, fontSizeInPx.px)
                )
            }
        }
    }

    @Test
    fun getText_textLayoutResult_Null_Return_Empty_AnnotatedString() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val layoutResult = null

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                assertThat(selectable.getText()).isEqualTo(AnnotatedString(""))
            }
        }
    }

    @Test
    fun getText_textLayoutResult_NotNull_Return_AnnotatedString() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val textLtr = "Hello"
                val textRtl = "\u05D0\u05D1\u05D2"
                val text = textLtr + textRtl
                val fontSize = 20.sp
                val spanStyle = SpanStyle(fontSize = fontSize, fontFamily = fontFamily)

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = {},
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                assertThat(selectable.getText()).isEqualTo(AnnotatedString(text, spanStyle))
            }
        }
    }

    @Test
    fun getBoundingBox_valid() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "hello\nworld\n"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = mock(),
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                val textOffset = text.indexOf('w')

                // Act.
                val box = selectable.getBoundingBox(textOffset)

                // Assert.
                assertThat(box.left).isZero()
                assertThat(box.right).isEqualTo(fontSizeInPx)
                assertThat(box.top).isEqualTo(fontSizeInPx)
                assertThat(box.bottom).isEqualTo((2f + 1 / 5f) * fontSizeInPx)
            }
        }
    }

    @Test
    fun getBoundingBox_negative_offset_should_return_zero_rect() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "hello\nworld\n"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = mock(),
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                // Act.
                val box = selectable.getBoundingBox(-2)

                // Assert.
                assertThat(box.left).isZero()
                assertThat(box.right).isEqualTo(fontSizeInPx)
                assertThat(box.top).isZero()
                assertThat(box.bottom).isEqualTo(fontSizeInPx)
            }
        }
    }

    @Test
    fun getBoundingBox_offset_larger_than_range_should_return_largest() {
        with(defaultDensity) {
            composeTestRule.setContent {
                val text = "hello\nworld"
                val fontSize = 20.sp
                val fontSizeInPx = fontSize.toPx().value

                val layoutResult = simpleTextLayout(
                    text = text,
                    fontSize = fontSize,
                    density = defaultDensity
                )

                val layoutCoordinates = mock<LayoutCoordinates>()
                whenever(layoutCoordinates.isAttached).thenReturn(true)

                val selectable = TextSelectionDelegate(
                    selectionRangeUpdate = mock(),
                    coordinatesCallback = { layoutCoordinates },
                    layoutResultCallback = { layoutResult }
                )

                // Act.
                val box = selectable.getBoundingBox(text.indexOf('d') + 5)

                // Assert.
                assertThat(box.left).isEqualTo(4 * fontSizeInPx)
                assertThat(box.right).isEqualTo(5 * fontSizeInPx)
                assertThat(box.top).isEqualTo(fontSizeInPx)
                assertThat(box.bottom).isEqualTo((2f + 1 / 5f) * fontSizeInPx)
            }
        }
    }

    @Test
    fun getTextSelectionInfo_long_press_select_word_ltr() {
        val text = "hello world\n"
        val fontSize = 20.sp
        val fontSizeInPx = with(defaultDensity) { fontSize.toPx().value }

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        val start = PxPosition((fontSizeInPx * 2).px, (fontSizeInPx / 2).px)
        val end = start

        // Act.
        val textSelectionInfo = getTextSelectionInfo(
            textLayoutResult = textLayoutResult,
            selectionCoordinates = Pair(start, end),
            selectable = mock(),
            wordBasedSelection = true
        )

        // Assert.
        assertThat(textSelectionInfo).isNotNull()

        assertThat(textSelectionInfo?.start).isNotNull()
        textSelectionInfo?.start?.let {
            assertThat(it.direction).isEqualTo(TextDirection.Ltr)
            assertThat(it.offset).isEqualTo(0)
        }

        assertThat(textSelectionInfo?.end).isNotNull()
        textSelectionInfo?.end?.let {
            assertThat(it.direction).isEqualTo(TextDirection.Ltr)
            assertThat(it.offset).isEqualTo("hello".length)
        }
    }

    @Test
    fun getTextSelectionInfo_long_press_select_word_rtl() {
        val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
        val fontSize = 20.sp
        val fontSizeInPx = with(defaultDensity) { fontSize.toPx().value }

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        val start = PxPosition((fontSizeInPx * 2).px, (fontSizeInPx / 2).px)
        val end = start

        // Act.
        val textSelectionInfo = getTextSelectionInfo(
            textLayoutResult = textLayoutResult,
            selectionCoordinates = Pair(start, end),
            selectable = mock(),
            wordBasedSelection = true
        )

        // Assert.
        assertThat(textSelectionInfo).isNotNull()

        assertThat(textSelectionInfo?.start).isNotNull()
        textSelectionInfo?.start?.let {
            assertThat(it.direction).isEqualTo(TextDirection.Rtl)
            assertThat(it.offset).isEqualTo(text.indexOf("\u05D3"))
        }

        assertThat(textSelectionInfo?.end).isNotNull()
        textSelectionInfo?.end?.let {
            assertThat(it.direction).isEqualTo(TextDirection.Rtl)
            assertThat(it.offset).isEqualTo(text.indexOf("\u05D5") + 1)
        }
    }

    @Test
    fun getTextSelectionInfo_long_press_drag_handle_not_cross_select_word() {
        val text = "hello world"
        val fontSize = 20.sp
        val fontSizeInPx = with(defaultDensity) { fontSize.toPx().value }

        val textLayoutResult = simpleTextLayout(
            text = text,
            fontSize = fontSize,
            density = defaultDensity
        )

        val rawStartOffset = text.indexOf('e')
        val rawEndOffset = text.indexOf('r')
        val start = PxPosition((fontSizeInPx * rawStartOffset).px, (fontSizeInPx / 2).px)
        val end = PxPosition((fontSizeInPx * rawEndOffset).px, (fontSizeInPx / 2).px)

        // Act.
        val textSelectionInfo = getTextSelectionInfo(
            textLayoutResult = textLayoutResult,
            selectionCoordinates = Pair(start, end),
            selectable = mock(),
            wordBasedSelection = true
        )

        // Assert.
        assertThat(textSelectionInfo).isNotNull()

        assertThat(textSelectionInfo?.start).isNotNull()
        textSelectionInfo?.start?.let {
            assertThat(it.direction).isEqualTo(TextDirection.Ltr)
            assertThat(it.offset).isEqualTo(0)
        }

        assertThat(textSelectionInfo?.end).isNotNull()
        textSelectionInfo?.end?.let {
            assertThat(it.direction).isEqualTo(TextDirection.Ltr)
            assertThat(it.offset).isEqualTo(text.length)
        }
        assertThat(textSelectionInfo?.handlesCrossed).isFalse()
    }

    @Test
    fun getTextSelectionInfo_long_press_drag_handle_cross_select_word() {
        with(defaultDensity) {
            val text = "hello world"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value

            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )

            val rawStartOffset = text.indexOf('r')
            val rawEndOffset = text.indexOf('e')
            val start = PxPosition((fontSizeInPx * rawStartOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * rawEndOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                textLayoutResult = textLayoutResult,
                selectionCoordinates = Pair(start, end),
                selectable = mock(),
                wordBasedSelection = true
            )

            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(text.length)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(0)
            }
            assertThat(textSelectionInfo?.handlesCrossed).isTrue()
        }
    }

    @Test
    fun getTextSelectionInfo_drag_select_range_ltr() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value

            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )

            // "llo wor" is selected.
            val startOffset = text.indexOf("l")
            val endOffset = text.indexOf("r") + 1
            val start = PxPosition((fontSizeInPx * startOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * endOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                textLayoutResult = textLayoutResult,
                selectionCoordinates = Pair(start, end),
                selectable = mock(),
                wordBasedSelection = false
            )

            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(startOffset)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(endOffset)
            }
        }
    }

    @Test
    fun getTextSelectionInfo_drag_select_range_rtl() {
        with(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value

            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )

            // "\u05D1\u05D2 \u05D3" is selected.
            val startOffset = text.indexOf("\u05D1")
            val endOffset = text.indexOf("\u05D3") + 1
            val start = PxPosition(
                (fontSizeInPx * (text.length - 1 - startOffset)).px,
                (fontSizeInPx / 2).px
            )
            val end = PxPosition(
                (fontSizeInPx * (text.length - 1 - endOffset)).px,
                (fontSizeInPx / 2).px
            )

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                textLayoutResult = textLayoutResult,
                selectionCoordinates = Pair(start, end),
                selectable = mock(),
                wordBasedSelection = false
            )

            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Rtl)
                assertThat(it.offset).isEqualTo(startOffset)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Rtl)
                assertThat(it.offset).isEqualTo(endOffset)
            }
        }
    }

    @Test
    fun getTextSelectionInfo_drag_select_range_bidi() {
        with(defaultDensity) {
            val textLtr = "Hello"
            val textRtl = "\u05D0\u05D1\u05D2\u05D3\u05D4"
            val text = textLtr + textRtl
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value

            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )

            // "llo"+"\u05D0\u05D1\u05D2" is selected
            val startOffset = text.indexOf("l")
            val endOffset = text.indexOf("\u05D2") + 1
            val start = PxPosition(
                (fontSizeInPx * startOffset).px,
                (fontSizeInPx / 2).px
            )
            val end = PxPosition(
                (fontSizeInPx * (textLtr.length + text.length - endOffset)).px,
                (fontSizeInPx / 2).px
            )

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                textLayoutResult = textLayoutResult,
                selectionCoordinates = Pair(start, end),
                selectable = mock(),
                wordBasedSelection = false
            )

            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(startOffset)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Rtl)
                assertThat(it.offset).isEqualTo(endOffset)
            }
        }
    }

    @Test
    fun testTextSelectionProcessor_single_widget_handles_crossed_ltr() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "llo wor" is selected.
            val startOffset = text.indexOf("r") + 1
            val endOffset = text.indexOf("l")
            val start = PxPosition((fontSizeInPx * startOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * endOffset).px, (fontSizeInPx / 2).px)
            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = mock(),
                wordBasedSelection = false
            )
            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(startOffset)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(endOffset)
            }
            assertThat(textSelectionInfo?.handlesCrossed).isTrue()
        }
    }

    @Test
    fun testTextSelectionProcessor_single_widget_handles_crossed_rtl() {
        with(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "\u05D1\u05D2 \u05D3" is selected.
            val startOffset = text.indexOf("\u05D3") + 1
            val endOffset = text.indexOf("\u05D1")
            val start = PxPosition(
                (fontSizeInPx * (text.length - 1 - startOffset)).px,
                (fontSizeInPx / 2).px
            )
            val end = PxPosition(
                (fontSizeInPx * (text.length - 1 - endOffset)).px,
                (fontSizeInPx / 2).px
            )

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = mock(),
                wordBasedSelection = false
            )
            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Rtl)
                assertThat(it.offset).isEqualTo(startOffset)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Rtl)
                assertThat(it.offset).isEqualTo(endOffset)
            }
            assertThat(textSelectionInfo?.handlesCrossed).isTrue()
        }
    }

    @Test
    fun testTextSelectionProcessor_single_widget_handles_crossed_bidi() {
        with(defaultDensity) {
            val textLtr = "Hello"
            val textRtl = "\u05D0\u05D1\u05D2\u05D3\u05D4"
            val text = textLtr + textRtl
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "llo"+"\u05D0\u05D1\u05D2" is selected
            val startOffset = text.indexOf("\u05D2") + 1
            val endOffset = text.indexOf("l")
            val start = PxPosition(
                (fontSizeInPx * (textLtr.length + text.length - startOffset)).px,
                (fontSizeInPx / 2).px
            )
            val end = PxPosition(
                (fontSizeInPx * endOffset).px,
                (fontSizeInPx / 2).px
            )

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = mock(),
                wordBasedSelection = false
            )
            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Rtl)
                assertThat(it.offset).isEqualTo(startOffset)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(endOffset)
            }
            assertThat(textSelectionInfo?.handlesCrossed).isTrue()
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_ltr_drag_endHandle() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "llo" is selected.
            val oldStartOffset = text.indexOf("l")
            val oldEndOffset = text.indexOf("o") + 1
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = false
            )
            // "l" is selected.
            val start = PxPosition((fontSizeInPx * oldStartOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldStartOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = false
            )
            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isEqualTo(previousSelection.start)

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(oldStartOffset - 1)
            }

            assertThat(textSelectionInfo?.handlesCrossed).isTrue()
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_rtl_drag_endHandle() {
        with(defaultDensity) {
            val text = "\u05D0\u05D1\u05D2 \u05D3\u05D4\u05D5\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "\u05D0\u05D1" is selected.
            val oldStartOffset = text.indexOf("\u05D1")
            val oldEndOffset = text.length
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Rtl,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Rtl,
                    selectable = selectable
                ),
                handlesCrossed = false
            )
            // "\u05D1" is selected.
            val start = PxPosition(
                (fontSizeInPx * (text.length - 1 - oldStartOffset)).px,
                (fontSizeInPx / 2).px
            )
            val end = PxPosition(
                (fontSizeInPx * (text.length - 1 - oldStartOffset)).px,
                (fontSizeInPx / 2).px
            )

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = false
            )
            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isEqualTo(previousSelection.start)

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Rtl)
                assertThat(it.offset).isEqualTo(oldStartOffset - 1)
            }

            assertThat(textSelectionInfo?.handlesCrossed).isTrue()
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_startHandle_not_crossed() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "llo" is selected.
            val oldStartOffset = text.indexOf("l")
            val oldEndOffset = text.indexOf("o") + 1
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = false
            )
            // The Space after "o" is selected.
            val start = PxPosition((fontSizeInPx * oldEndOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldEndOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = true
            )

            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo((oldEndOffset + 1))
            }

            assertThat(textSelectionInfo?.end).isEqualTo(previousSelection.end)

            assertThat(textSelectionInfo?.handlesCrossed).isTrue()
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_startHandle_crossed() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "llo" is selected.
            val oldStartOffset = text.indexOf("o") + 1
            val oldEndOffset = text.indexOf("l")
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = true
            )
            // "l" is selected.
            val start = PxPosition((fontSizeInPx * oldEndOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldEndOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = true
            )

            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo((oldEndOffset - 1))
            }

            assertThat(textSelectionInfo?.end).isEqualTo(previousSelection.end)

            assertThat(textSelectionInfo?.handlesCrossed).isFalse()
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_startHandle_not_crossed_bounded() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "e" is selected.
            val oldStartOffset = text.indexOf("e")
            val oldEndOffset = text.indexOf("l")
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = false
            )
            // "e" should be selected.
            val start = PxPosition((fontSizeInPx * oldEndOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldEndOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = true
            )

            // Assert.
            assertThat(textSelectionInfo).isEqualTo(previousSelection)
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_startHandle_crossed_bounded() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "e" is selected.
            val oldStartOffset = text.indexOf("l")
            val oldEndOffset = text.indexOf("e")
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = true
            )
            // "e" should be selected.
            val start = PxPosition((fontSizeInPx * oldEndOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldEndOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = true
            )

            // Assert.
            assertThat(textSelectionInfo).isEqualTo(previousSelection)
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_startHandle_not_crossed_boundary() {
        with(defaultDensity) {
            val text = "hello world"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "d" is selected.
            val oldStartOffset = text.length - 1
            val oldEndOffset = text.length
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = false
            )
            // "d" should be selected.
            val start = PxPosition((fontSizeInPx * oldEndOffset).px - (fontSizeInPx / 2).px,
                (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldEndOffset).px - 1.px,
                (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = true
            )

            // Assert.
            assertThat(textSelectionInfo).isEqualTo(previousSelection)
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_startHandle_crossed_boundary() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "h" is selected.
            val oldStartOffset = text.indexOf("e")
            val oldEndOffset = 0
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = true
            )
            // "e" should be selected.
            val start = PxPosition((fontSizeInPx * oldEndOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldEndOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = true
            )

            // Assert.
            assertThat(textSelectionInfo).isEqualTo(previousSelection)
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_endHandle_crossed() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "llo" is selected.
            val oldStartOffset = text.indexOf("o") + 1
            val oldEndOffset = text.indexOf("l")
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = true
            )
            // The space after "o" is selected.
            val start = PxPosition((fontSizeInPx * oldStartOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldStartOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = false
            )

            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isEqualTo(previousSelection.start)

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo((oldStartOffset + 1))
            }

            assertThat(textSelectionInfo?.handlesCrossed).isFalse()
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_endHandle_not_crossed_bounded() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "e" is selected.
            val oldStartOffset = text.indexOf("e")
            val oldEndOffset = text.indexOf("l")
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = false
            )
            // "e" should be selected.
            val start = PxPosition((fontSizeInPx * oldStartOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldStartOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = false
            )

            // Assert.
            assertThat(textSelectionInfo).isEqualTo(previousSelection)
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_endHandle_crossed_bounded() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "e" is selected.
            val oldStartOffset = text.indexOf("l")
            val oldEndOffset = text.indexOf("e")
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = true
            )
            // "e" should be selected.
            val start = PxPosition((fontSizeInPx * oldStartOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldStartOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = false
            )

            // Assert.
            assertThat(textSelectionInfo).isEqualTo(previousSelection)
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_endHandle_not_crossed_boundary() {
        with(defaultDensity) {
            val text = "hello world"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "h" is selected.
            val oldStartOffset = 0
            val oldEndOffset = text.indexOf('e')
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = false
            )
            // "h" should be selected.
            val start = PxPosition((fontSizeInPx * oldStartOffset).px,
                (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldStartOffset).px,
                (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = false
            )

            // Assert.
            assertThat(textSelectionInfo).isEqualTo(previousSelection)
        }
    }

    @Test
    fun testTextSelectionProcessor_bound_to_one_character_drag_endHandle_crossed_boundary() {
        with(defaultDensity) {
            val text = "hello world"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "d" is selected.
            val oldStartOffset = text.length
            val oldEndOffset = text.length - 1
            val selectable: Selectable = mock()
            val previousSelection = Selection(
                start = Selection.AnchorInfo(
                    offset = oldStartOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                end = Selection.AnchorInfo(
                    offset = oldEndOffset,
                    direction = TextDirection.Ltr,
                    selectable = selectable
                ),
                handlesCrossed = true
            )
            // "d" should be selected.
            val start = PxPosition((fontSizeInPx * oldStartOffset).px - 1.px,
                (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * oldStartOffset).px - 1.px,
                (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = selectable,
                wordBasedSelection = false,
                previousSelection = previousSelection,
                isStartHandle = false
            )

            // Assert.
            assertThat(textSelectionInfo).isEqualTo(previousSelection)
        }
    }

    @Test
    fun testTextSelectionProcessor_cross_widget_not_contain_start() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "hello w" is selected.
            val endOffset = text.indexOf("w") + 1
            val start = PxPosition(-50.px, -50.px)
            val end = PxPosition((fontSizeInPx * endOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = mock(),
                wordBasedSelection = false
            )
            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(0)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(endOffset)
            }
        }
    }

    @Test
    fun testTextSelectionProcessor_cross_widget_not_contain_end() {
        with(defaultDensity) {
            val text = "hello world"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "o world" is selected.
            val startOffset = text.indexOf("o")
            val start = PxPosition((fontSizeInPx * startOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition((fontSizeInPx * text.length * 2).px, (fontSizeInPx * 2)
                .px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = mock(),
                wordBasedSelection = false
            )
            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(startOffset)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(text.length)
            }
        }
    }

    @Test
    fun testTextSelectionProcessor_cross_widget_not_contain_start_handles_crossed() {
        with(defaultDensity) {
            val text = "hello world"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "world" is selected.
            val endOffset = text.indexOf("w")
            val start =
                PxPosition((fontSizeInPx * text.length * 2).px, (fontSizeInPx * 2).px)
            val end = PxPosition((fontSizeInPx * endOffset).px, (fontSizeInPx / 2).px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = mock(),
                wordBasedSelection = false
            )
            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(text.length)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(endOffset)
            }
            assertThat(textSelectionInfo?.handlesCrossed).isTrue()
        }
    }

    @Test
    fun testTextSelectionProcessor_cross_widget_not_contain_end_handles_crossed() {
        with(defaultDensity) {
            val text = "hello world"
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            // "hell" is selected.
            val startOffset = text.indexOf("o")
            val start =
                PxPosition((fontSizeInPx * startOffset).px, (fontSizeInPx / 2).px)
            val end = PxPosition(-50.px, -50.px)

            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = mock(),
                wordBasedSelection = false
            )
            // Assert.
            assertThat(textSelectionInfo).isNotNull()

            assertThat(textSelectionInfo?.start).isNotNull()
            textSelectionInfo?.start?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(startOffset)
            }

            assertThat(textSelectionInfo?.end).isNotNull()
            textSelectionInfo?.end?.let {
                assertThat(it.direction).isEqualTo(TextDirection.Ltr)
                assertThat(it.offset).isEqualTo(0)
            }
            assertThat(textSelectionInfo?.handlesCrossed).isTrue()
        }
    }

    @Test
    fun testTextSelectionProcessor_not_selected() {
        with(defaultDensity) {
            val text = "hello world\n"
            val fontSize = 20.sp
            val textLayoutResult = simpleTextLayout(
                text = text,
                fontSize = fontSize,
                density = defaultDensity
            )
            val start = PxPosition(-50.px, -50.px)
            val end = PxPosition(-20.px, -20.px)
            // Act.
            val textSelectionInfo = getTextSelectionInfo(
                selectionCoordinates = Pair(start, end),
                textLayoutResult = textLayoutResult,
                selectable = mock(),
                wordBasedSelection = true
            )
            assertThat(textSelectionInfo).isNull()
        }
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

class TestFontResourceLoader(val context: Context) : Font.ResourceLoader {
    override fun load(font: Font): Typeface {
        return when (font) {
            is ResourceFont -> ResourcesCompat.getFont(context, font.resId)!!
            else -> throw IllegalArgumentException("Unknown font type: $font")
        }
    }
}

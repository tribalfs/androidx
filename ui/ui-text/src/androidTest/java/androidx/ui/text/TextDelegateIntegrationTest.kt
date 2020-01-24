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

import android.graphics.Bitmap
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.ui.core.Constraints
import androidx.ui.core.LayoutDirection
import androidx.ui.geometry.Rect
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.Paint
import androidx.ui.text.FontTestData.Companion.BASIC_MEASURE_FONT
import androidx.ui.text.font.asFontFamily
import androidx.ui.text.matchers.assertThat
import androidx.ui.unit.Density
import androidx.ui.unit.ipx
import androidx.ui.unit.sp
import androidx.ui.unit.withDensity
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@SmallTest
class TextDelegateIntegrationTest {

    private val fontFamily = BASIC_MEASURE_FONT.asFontFamily()
    private val density = Density(density = 1f)
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val resourceLoader = TestFontResourceLoader(context)

    @Test
    fun minIntrinsicWidth_getter() {
        withDensity(density) {
            val fontSize = 20.sp
            val text = "Hello"
            val spanStyle = SpanStyle(fontSize = fontSize, fontFamily = fontFamily)
            val annotatedString = AnnotatedString(text, spanStyle)
            val textDelegate = TextDelegate(
                text = annotatedString,
                density = density,
                resourceLoader = resourceLoader,
                layoutDirection = LayoutDirection.Ltr
            )
            textDelegate.layoutIntrinsics()

            assertThat(textDelegate.minIntrinsicWidth)
                .isEqualTo((fontSize.toPx().value * text.length).toIntPx())
        }
    }

    @Test
    fun maxIntrinsicWidth_getter() {
        withDensity(density) {
            val fontSize = 20.sp
            val text = "Hello"
            val spanStyle = SpanStyle(fontSize = fontSize, fontFamily = fontFamily)
            val annotatedString = AnnotatedString(text, spanStyle)
            val textDelegate = TextDelegate(
                text = annotatedString,
                density = density,
                resourceLoader = resourceLoader,
                layoutDirection = LayoutDirection.Ltr
            )

            textDelegate.layoutIntrinsics()

            assertThat(textDelegate.maxIntrinsicWidth)
                .isEqualTo((fontSize.toPx().value * text.length).toIntPx())
        }
    }

    @Test
    fun testBackgroundPaint_paint_wrap_multiLines() {
        withDensity(density) {
            // Setup test.
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val text = "HelloHello"
            val spanStyle = SpanStyle(fontSize = fontSize, fontFamily = fontFamily)
            val annotatedString = AnnotatedString(text, spanStyle)
            val textDelegate = TextDelegate(
                text = annotatedString,
                density = density,
                resourceLoader = resourceLoader,
                layoutDirection = LayoutDirection.Ltr
            )
            val layoutResult = textDelegate.layout(Constraints(maxWidth = 120.ipx))

            val expectedBitmap = layoutResult.toBitmap()
            val expectedCanvas = Canvas(android.graphics.Canvas(expectedBitmap))
            val expectedPaint = Paint()
            val defaultSelectionColor = Color(0x6633B5E5)
            expectedPaint.color = defaultSelectionColor

            val firstLineLeft = layoutResult.multiParagraph.getLineLeft(0)
            val secondLineLeft = layoutResult.multiParagraph.getLineLeft(1)
            val firstLineRight = layoutResult.multiParagraph.getLineRight(0)
            val secondLineRight = layoutResult.multiParagraph.getLineRight(1)
            expectedCanvas.drawRect(
                Rect(firstLineLeft, 0f, firstLineRight, fontSizeInPx),
                expectedPaint
            )
            expectedCanvas.drawRect(
                Rect(
                    secondLineLeft,
                    fontSizeInPx,
                    secondLineRight,
                    layoutResult.multiParagraph.height
                ),
                expectedPaint
            )

            val actualBitmap = layoutResult.toBitmap()
            val actualCanvas = Canvas(android.graphics.Canvas(actualBitmap))

            // Run.
            // Select all.
            textDelegate.paintBackground(
                start = 0,
                end = text.length,
                color = defaultSelectionColor,
                canvas = actualCanvas,
                textLayoutResult = layoutResult
            )

            // Assert.
            assertThat(actualBitmap).isEqualToBitmap(expectedBitmap)
        }
    }

    @Test
    fun testBackgroundPaint_paint_with_default_color() {
        withDensity(density) {
            // Setup test.
            val selectionStart = 0
            val selectionEnd = 3
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val text = "Hello"
            val spanStyle = SpanStyle(fontSize = fontSize, fontFamily = fontFamily)
            val annotatedString = AnnotatedString(text, spanStyle)
            val textDelegate = TextDelegate(
                text = annotatedString,
                density = density,
                resourceLoader = resourceLoader,
                layoutDirection = LayoutDirection.Ltr
            )
            val layoutResult = textDelegate.layout(Constraints())

            val expectedBitmap = layoutResult.toBitmap()
            val expectedCanvas = Canvas(android.graphics.Canvas(expectedBitmap))
            val expectedPaint = Paint()
            val defaultSelectionColor = Color(0x6633B5E5)
            expectedPaint.color = defaultSelectionColor
            expectedCanvas.drawRect(
                Rect(
                    left = 0f,
                    top = 0f,
                    right = fontSizeInPx * (selectionEnd - selectionStart),
                    bottom = fontSizeInPx
                ),
                expectedPaint
            )

            val actualBitmap = layoutResult.toBitmap()
            val actualCanvas = Canvas(android.graphics.Canvas(actualBitmap))

            // Run.
            textDelegate.paintBackground(
                start = selectionStart,
                end = selectionEnd,
                color = defaultSelectionColor,
                canvas = actualCanvas,
                textLayoutResult = layoutResult
            )

            // Assert
            assertThat(actualBitmap).isEqualToBitmap(expectedBitmap)
        }
    }

    @Test
    fun testBackgroundPaint_paint_with_default_color_bidi() {
        withDensity(density) {
            // Setup test.
            val textLTR = "Hello"
            // From right to left: שלום
            val textRTL = "\u05e9\u05dc\u05d5\u05dd"
            val text = textLTR + textRTL
            val selectionLTRStart = 2
            val selectionRTLEnd = 2
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val spanStyle = SpanStyle(fontSize = fontSize, fontFamily = fontFamily)
            val annotatedString = AnnotatedString(text, spanStyle)
            val textDelegate = TextDelegate(
                text = annotatedString,
                density = density,
                resourceLoader = resourceLoader,
                layoutDirection = LayoutDirection.Ltr
            )
            val layoutResult = textDelegate.layout(Constraints())

            val expectedBitmap = layoutResult.toBitmap()
            val expectedCanvas = Canvas(android.graphics.Canvas(expectedBitmap))
            val expectedPaint = Paint()
            val defaultSelectionColor = Color(0x6633B5E5)
            expectedPaint.color = defaultSelectionColor
            // Select "llo".
            expectedCanvas.drawRect(
                Rect(
                    left = fontSizeInPx * selectionLTRStart,
                    top = 0f,
                    right = textLTR.length * fontSizeInPx,
                    bottom = fontSizeInPx
                ),
                expectedPaint
            )

            // Select "של"
            expectedCanvas.drawRect(
                Rect(
                    left = (textLTR.length + textRTL.length - selectionRTLEnd) * fontSizeInPx,
                    top = 0f,
                    right = (textLTR.length + textRTL.length) * fontSizeInPx,
                    bottom = fontSizeInPx
                ),
                expectedPaint
            )

            val actualBitmap = layoutResult.toBitmap()
            val actualCanvas = Canvas(android.graphics.Canvas(actualBitmap))

            // Run.
            textDelegate.paintBackground(
                start = selectionLTRStart,
                end = textLTR.length + selectionRTLEnd,
                color = defaultSelectionColor,
                canvas = actualCanvas,
                textLayoutResult = layoutResult
            )

            // Assert
            assertThat(actualBitmap).isEqualToBitmap(expectedBitmap)
        }
    }

    @Test
    fun testBackgroundPaint_paint_with_customized_color() {
        withDensity(density) {
            // Setup test.
            val selectionStart = 0
            val selectionEnd = 3
            val fontSize = 20.sp
            val fontSizeInPx = fontSize.toPx().value
            val text = "Hello"
            val spanStyle = SpanStyle(fontSize = fontSize, fontFamily = fontFamily)
            val annotatedString = AnnotatedString(text, spanStyle)
            val selectionColor = Color(0x66AABB33)
            val textDelegate = TextDelegate(
                text = annotatedString,
                density = density,
                resourceLoader = resourceLoader,
                layoutDirection = LayoutDirection.Ltr
            )
            val layoutResult = textDelegate.layout(Constraints())

            val expectedBitmap = layoutResult.toBitmap()
            val expectedCanvas = Canvas(android.graphics.Canvas(expectedBitmap))
            val expectedPaint = Paint()
            expectedPaint.color = selectionColor
            expectedCanvas.drawRect(
                Rect(
                    left = 0f,
                    top = 0f,
                    right = fontSizeInPx * (selectionEnd - selectionStart),
                    bottom = fontSizeInPx
                ),
                expectedPaint
            )

            val actualBitmap = layoutResult.toBitmap()
            val actualCanvas = Canvas(android.graphics.Canvas(actualBitmap))

            // Run.
            textDelegate.paintBackground(
                start = selectionStart,
                end = selectionEnd,
                color = selectionColor,
                canvas = actualCanvas,
                textLayoutResult = layoutResult
            )

            // Assert
            assertThat(actualBitmap).isEqualToBitmap(expectedBitmap)
        }
    }

    @Test
    fun multiParagraphIntrinsics_isReused() {
        val textDelegate = TextDelegate(
            text = AnnotatedString(text = "abc"),
            density = density,
            resourceLoader = resourceLoader,
            layoutDirection = LayoutDirection.Ltr
        )

        // create the intrinsics object
        textDelegate.layoutIntrinsics()
        val multiParagraphIntrinsics = textDelegate.paragraphIntrinsics

        // layout should create the MultiParagraph. The final MultiParagraph is expected to use
        // the previously calculated intrinsics
        val layoutResult = textDelegate.layout(Constraints())
        val layoutIntrinsics = layoutResult.multiParagraph.intrinsics

        // primary assertions to make sure that the objects are not null
        assertThat(layoutIntrinsics.infoList.get(0)).isNotNull()
        assertThat(multiParagraphIntrinsics?.infoList?.get(0)).isNotNull()

        // the intrinsics passed to multi paragraph should be the same instance
        assertThat(layoutIntrinsics).isSameInstanceAs(multiParagraphIntrinsics)
        // the ParagraphIntrinsic in the MultiParagraphIntrinsic should be the same instance
        assertThat(layoutIntrinsics.infoList.get(0))
            .isSameInstanceAs(multiParagraphIntrinsics?.infoList?.get(0))
    }

    @Test
    fun TextLayoutInput_reLayout_withDifferentHeight() {
        val textDelegate = TextDelegate(
            text = AnnotatedString(text = "Hello World!"),
            density = density,
            resourceLoader = resourceLoader,
            layoutDirection = LayoutDirection.Ltr
        )
        val width = 200.ipx
        val heightFirstLayout = 100.ipx
        val heightSecondLayout = 200.ipx

        val constraintsFirstLayout = Constraints.fixed(width, heightFirstLayout)
        val resultFirstLayout = textDelegate.layout(constraintsFirstLayout)
        assertThat(resultFirstLayout.layoutInput.constraints).isEqualTo(constraintsFirstLayout)

        val constraintsSecondLayout = Constraints.fixed(width, heightSecondLayout)
        val resultSecondLayout = textDelegate.layout(constraintsSecondLayout, resultFirstLayout)
        assertThat(resultSecondLayout.layoutInput.constraints).isEqualTo(constraintsSecondLayout)
    }

    @Test
    fun TextLayoutResult_reLayout_withDifferentHeight() {
        val textDelegate = TextDelegate(
            text = AnnotatedString(text = "Hello World!"),
            density = density,
            resourceLoader = resourceLoader,
            layoutDirection = LayoutDirection.Ltr
        )
        val width = 200.ipx
        val heightFirstLayout = 100.ipx
        val heightSecondLayout = 200.ipx

        val constraintsFirstLayout = Constraints.fixed(width, heightFirstLayout)
        val resultFirstLayout = textDelegate.layout(constraintsFirstLayout)
        assertThat(resultFirstLayout.size.height).isEqualTo(heightFirstLayout)

        val constraintsSecondLayout = Constraints.fixed(width, heightSecondLayout)
        val resultSecondLayout = textDelegate.layout(constraintsSecondLayout, resultFirstLayout)
        assertThat(resultSecondLayout.size.height).isEqualTo(heightSecondLayout)
    }
}

private fun TextLayoutResult.toBitmap() = Bitmap.createBitmap(
    size.width.value,
    size.height.value,
    Bitmap.Config.ARGB_8888
)

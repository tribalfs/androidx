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

import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.ui.core.Constraints
import androidx.ui.core.Density
import androidx.ui.core.LayoutDirection
import androidx.ui.core.PxPosition
import androidx.ui.core.ipx
import androidx.ui.core.px
import androidx.ui.core.sp
import androidx.ui.core.withDensity
import androidx.ui.graphics.Canvas
import androidx.ui.text.FontTestData.Companion.BASIC_MEASURE_FONT
import androidx.ui.text.font.asFontFamily
import androidx.ui.text.matchers.isZero
import androidx.ui.text.style.TextOverflow
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
@SmallTest
class TextLayoutResultIntegrationTest {

    private val fontFamily = BASIC_MEASURE_FONT.asFontFamily()
    private val density = Density(density = 1f)
    private val context = InstrumentationRegistry.getInstrumentation().context
    private val resourceLoader = TestFontResourceLoader(context)

    @Test
    fun width_getter() {
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

            val layoutResult = textDelegate.layout(Constraints(0.ipx, 200.ipx))

            assertThat(layoutResult.size.width).isEqualTo(
                (fontSize.toPx().value * text.length).toIntPx()
            )
        }
    }

    @Test
    fun width_getter_with_small_width() {
        val text = "Hello"
        val width = 80.ipx
        val spanStyle = SpanStyle(fontSize = 20.sp, fontFamily = fontFamily)
        val annotatedString = AnnotatedString(text, spanStyle)
        val textDelegate = TextDelegate(
            text = annotatedString,
            density = density,
            resourceLoader = resourceLoader,
            layoutDirection = LayoutDirection.Ltr
        )

        val layoutResult = textDelegate.layout(Constraints(maxWidth = width))

        assertThat(layoutResult.size.width).isEqualTo(width)
    }

    @Test
    fun height_getter() {
        withDensity(density) {
            val fontSize = 20.sp
            val spanStyle = SpanStyle(fontSize = fontSize, fontFamily = fontFamily)
            val text = "hello"
            val annotatedString = AnnotatedString(text, spanStyle)
            val textDelegate = TextDelegate(
                text = annotatedString,
                density = density,
                resourceLoader = resourceLoader,
                layoutDirection = LayoutDirection.Ltr
            )

            val layoutResult = textDelegate.layout(Constraints())

            assertThat(layoutResult.size.height).isEqualTo((fontSize.toPx().value).toIntPx())
        }
    }

    @Test
    fun layout_build_layoutResult() {
        val textDelegate = TextDelegate(
            text = AnnotatedString(text = "Hello"),
            density = density,
            resourceLoader = resourceLoader,
            layoutDirection = LayoutDirection.Ltr
        )

        val layoutResult = textDelegate.layout(Constraints(0.ipx, 20.ipx))

        assertThat(layoutResult).isNotNull()
    }

    @Test
    fun getPositionForOffset_First_Character() {
        val text = "Hello"
        val annotatedString = AnnotatedString(
            text,
            SpanStyle(fontSize = 20.sp, fontFamily = fontFamily)
        )

        val textDelegate = TextDelegate(
            text = annotatedString,
            density = density,
            resourceLoader = resourceLoader,
            layoutDirection = LayoutDirection.Ltr
        )
        val layoutResult = textDelegate.layout(Constraints())

        val selection = layoutResult.getOffsetForPosition(PxPosition.Origin)

        assertThat(selection).isZero()
    }

    @Test
    fun getPositionForOffset_other_Character() {
        withDensity(density) {
            val fontSize = 20.sp
            val characterIndex = 2 // Start from 0.
            val text = "Hello"

            val annotatedString = AnnotatedString(
                text,
                SpanStyle(fontSize = fontSize, fontFamily = fontFamily)
            )

            val textDelegate = TextDelegate(
                text = annotatedString,
                density = density,
                resourceLoader = resourceLoader,
                layoutDirection = LayoutDirection.Ltr
            )
            val layoutResult = textDelegate.layout(Constraints())

            val selection = layoutResult.getOffsetForPosition(
                position = PxPosition((fontSize.toPx().value * characterIndex + 1).px, 0.px)
            )

            assertThat(selection).isEqualTo(characterIndex)
        }
    }

    @Test
    fun hasOverflowShaderFalse() {
        val text = "Hello"
        val spanStyle = SpanStyle(fontSize = 20.sp, fontFamily = fontFamily)
        val annotatedString = AnnotatedString(text, spanStyle)
        val textDelegate = TextDelegate(
            text = annotatedString,
            density = density,
            resourceLoader = resourceLoader,
            layoutDirection = LayoutDirection.Ltr
        )

        val layoutResult = textDelegate.layout(Constraints())

        assertThat(layoutResult.hasVisualOverflow).isFalse()

        // paint should not throw exception
        textDelegate.paint(Canvas(android.graphics.Canvas()), layoutResult)
    }

    @Test
    fun hasOverflowShaderFadeHorizontallyTrue() {
        val text = "Hello World".repeat(15)
        val spanStyle = SpanStyle(fontSize = 20.sp, fontFamily = fontFamily)
        val annotatedString = AnnotatedString(text, spanStyle)

        val textDelegate = TextDelegate(
            text = annotatedString,
            overflow = TextOverflow.Fade,
            softWrap = false,
            maxLines = 1,
            density = density,
            resourceLoader = resourceLoader,
            layoutDirection = LayoutDirection.Ltr
        )

        val layoutResult = textDelegate.layout(Constraints(maxWidth = 100.ipx))

        assertThat(layoutResult.hasVisualOverflow).isTrue()

        // paint should not throw exception
        textDelegate.paint(Canvas(android.graphics.Canvas()), layoutResult)
    }
}

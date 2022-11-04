/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.ui.text.platform

import android.graphics.Typeface
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.extensions.applySpanStyle
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SmallTest
class ApplySpanStyleTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext!!
    private val density = Density(context)
    private val resolveTypeface: (FontFamily?, FontWeight, FontStyle, FontSynthesis) -> Typeface =
        { _, _, _, _ ->
            Typeface.DEFAULT
        }

    @Test
    fun fontSizeSp_shouldBeAppliedTo_textSize() {
        val fontSize = 24.sp
        val spanStyle = SpanStyle(fontSize = fontSize)
        val tp = AndroidTextPaint(0, density.density)

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.textSize).isEqualTo(with(density) { fontSize.toPx() })
        assertThat(notApplied.fontSize).isEqualTo(TextUnit.Unspecified)
    }

    @Test
    fun fontSizeEm_shouldBeAppliedTo_textSize() {
        val fontSize = 2.em
        val spanStyle = SpanStyle(fontSize = fontSize)
        val tp = AndroidTextPaint(0, density.density)
        tp.textSize = 30f

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.textSize).isEqualTo(60f)
        assertThat(notApplied.fontSize).isEqualTo(TextUnit.Unspecified)
    }

    @Test
    fun textGeometricTransform_shouldBeAppliedTo_scaleSkew() {
        val textGeometricTransform = TextGeometricTransform(
            scaleX = 1.5f,
            skewX = 1f
        )
        val spanStyle = SpanStyle(textGeometricTransform = textGeometricTransform)
        val tp = AndroidTextPaint(0, density.density)
        val originalSkew = tp.textSkewX
        val originalScale = tp.textScaleX

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.textSkewX).isEqualTo(originalSkew + textGeometricTransform.skewX)
        assertThat(tp.textScaleX).isEqualTo(originalScale * textGeometricTransform.scaleX)
        assertThat(notApplied.textGeometricTransform).isNull()
    }

    @Test
    fun letterSpacingSp_shouldBeLeftAsSpan() {
        val letterSpacing = 10.sp
        val spanStyle = SpanStyle(letterSpacing = letterSpacing)
        val tp = AndroidTextPaint(0, density.density)
        tp.letterSpacing = 4f

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.letterSpacing).isEqualTo(4f)
        assertThat(notApplied.letterSpacing).isEqualTo(letterSpacing)
    }

    @Test
    fun letterSpacingEm_shouldBeAppliedTo_letterSpacing() {
        val letterSpacing = 1.5.em
        val spanStyle = SpanStyle(letterSpacing = letterSpacing)
        val tp = AndroidTextPaint(0, density.density)
        tp.letterSpacing = 4f

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.letterSpacing).isEqualTo(1.5f)
        assertThat(notApplied.letterSpacing).isEqualTo(TextUnit.Unspecified)
    }

    @Test
    fun letterSpacingUnspecified_shouldBeNoOp() {
        val letterSpacing = TextUnit.Unspecified
        val spanStyle = SpanStyle(letterSpacing = letterSpacing)
        val tp = AndroidTextPaint(0, density.density)
        tp.letterSpacing = 4f

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.letterSpacing).isEqualTo(4f)
        assertThat(notApplied.letterSpacing).isEqualTo(TextUnit.Unspecified)
    }

    @Test
    fun nonEmptyFontFeatureSettings_shouldBeAppliedTo_fontFeatureSettings() {
        val fontFeatureSettings = "\"kern\" 0"
        val spanStyle = SpanStyle(fontFeatureSettings = fontFeatureSettings)
        val tp = AndroidTextPaint(0, density.density)
        tp.fontFeatureSettings = ""

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.fontFeatureSettings).isEqualTo(fontFeatureSettings)
        assertThat(notApplied.fontFeatureSettings).isNull()
    }

    @Test
    fun emptyFontFeatureSettings_shouldBeNotAppliedTo_fontFeatureSettings() {
        val fontFeatureSettings = ""
        val spanStyle = SpanStyle(fontFeatureSettings = fontFeatureSettings)
        val tp = AndroidTextPaint(0, density.density)
        tp.fontFeatureSettings = "\"kern\" 0"

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.fontFeatureSettings).isEqualTo("\"kern\" 0")
        assertThat(notApplied.fontFeatureSettings).isNull()
    }

    @Test
    fun fontSettings_shouldBeAppliedTo_typeface() {
        val fontFamily = FontFamily.Cursive
        val fontWeight = FontWeight.W800
        val fontStyle = FontStyle.Italic
        val fontSynthesis = FontSynthesis.Style

        val spanStyle = SpanStyle(
            fontFamily = fontFamily,
            fontWeight = fontWeight,
            fontStyle = fontStyle,
            fontSynthesis = fontSynthesis
        )

        val tp = AndroidTextPaint(0, density.density)
        tp.typeface = Typeface.DEFAULT

        var calledFontFamily: FontFamily? = null
        var calledFontWeight: FontWeight? = null
        var calledFontStyle: FontStyle? = null
        var calledFontSynthesis: FontSynthesis? = null

        val notApplied = tp.applySpanStyle(
            spanStyle,
            { family, weight, style, synthesis ->
                calledFontFamily = family
                calledFontWeight = weight
                calledFontStyle = style
                calledFontSynthesis = synthesis
                Typeface.MONOSPACE
            },
            density
        )

        assertThat(tp.typeface).isEqualTo(Typeface.MONOSPACE)
        assertThat(calledFontFamily).isEqualTo(fontFamily)
        assertThat(calledFontWeight).isEqualTo(fontWeight)
        assertThat(calledFontStyle).isEqualTo(fontStyle)
        assertThat(calledFontSynthesis).isEqualTo(fontSynthesis)

        assertThat(notApplied.fontFamily).isNull()
        assertThat(notApplied.fontWeight).isNull()
        assertThat(notApplied.fontStyle).isNull()
        assertThat(notApplied.fontSynthesis).isNull()
    }

    @Test
    fun baselineShift_shouldBeLeftAsSpan() {
        val baselineShift = BaselineShift(0.8f)
        val spanStyle = SpanStyle(baselineShift = baselineShift)
        val tp = AndroidTextPaint(0, density.density)
        tp.baselineShift = 0

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.baselineShift).isEqualTo(0)
        assertThat(notApplied.baselineShift).isEqualTo(baselineShift)
    }

    @Test
    fun baselineShiftNone_shouldNotBeLeftAsSpan() {
        val baselineShift = BaselineShift.None
        val spanStyle = SpanStyle(baselineShift = baselineShift)
        val tp = AndroidTextPaint(0, density.density)
        tp.baselineShift = 0

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.baselineShift).isEqualTo(0)
        assertThat(notApplied.baselineShift).isNull()
    }

    @Test
    fun background_shouldBeLeftAsSpan() {
        val background = Color.Red
        val spanStyle = SpanStyle(background = background)
        val tp = AndroidTextPaint(0, density.density)
        tp.color = Color.Black.toArgb()

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.color).isEqualTo(Color.Black.toArgb())
        assertThat(notApplied.background).isEqualTo(background)
    }

    @Test
    fun backgroundTransparent_shouldNotBeLeftAsSpan() {
        val background = Color.Transparent
        val spanStyle = SpanStyle(background = background)
        val tp = AndroidTextPaint(0, density.density)
        tp.color = Color.Black.toArgb()

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.color).isEqualTo(Color.Black.toArgb())
        assertThat(notApplied.background).isEqualTo(Color.Unspecified)
    }

    /*@Test
    fun textDecorationUnderline_shouldBeLeftAsSpan() {
        val textDecoration = TextDecoration.Underline
        val spanStyle = SpanStyle(textDecoration = textDecoration)
        val tp = AndroidTextPaint(0, density.density)
        tp.isUnderlineText = false

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.isUnderlineText).isEqualTo(false)
        assertThat(notApplied.textDecoration).isEqualTo(textDecoration)
    }

    @Test
    fun textDecorationLineThrough_shouldBeLeftAsSpan() {
        val textDecoration = TextDecoration.LineThrough
        val spanStyle = SpanStyle(textDecoration = textDecoration)
        val tp = AndroidTextPaint(0, density.density)
        tp.isStrikeThruText = false

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.isStrikeThruText).isEqualTo(false)
        assertThat(notApplied.textDecoration).isEqualTo(textDecoration)
    }

    @Test
    fun textDecorationNone_shouldNotBeLeftAsSpan() {
        val textDecoration = TextDecoration.None
        val spanStyle = SpanStyle(textDecoration = textDecoration)
        val tp = AndroidTextPaint(0, density.density)
        tp.isUnderlineText = false
        tp.isStrikeThruText = false

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.isUnderlineText).isEqualTo(false)
        assertThat(tp.isStrikeThruText).isEqualTo(false)
        assertThat(notApplied.textDecoration).isNull()
    }*/

    @Test
    fun shadow_shouldBeAppliedTo_shadowLayer() {
        val shadow = Shadow(Color.Red, Offset(4f, 4f), blurRadius = 8f)
        val spanStyle = SpanStyle(shadow = shadow)
        val tp = AndroidTextPaint(0, density.density)
        tp.clearShadowLayer()

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.shadow).isEqualTo(shadow)
        assertThat(notApplied.shadow).isNull()
    }

    @Test
    fun color_shouldBeAppliedTo_color() {
        val color = Color.Red
        val spanStyle = SpanStyle(color = color)
        val tp = AndroidTextPaint(0, density.density)
        tp.color = Color.Black.toArgb()

        val notApplied = tp.applySpanStyle(spanStyle, resolveTypeface, density)

        assertThat(tp.color).isEqualTo(Color.Red.toArgb())
        assertThat(notApplied.color).isEqualTo(Color.Unspecified)
    }
}
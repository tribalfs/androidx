package androidx.ui.text.platform

import android.graphics.Paint
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LocaleSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ScaleXSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import androidx.test.filters.SmallTest
import androidx.test.platform.app.InstrumentationRegistry
import androidx.text.LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_LTR
import androidx.text.LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_RTL
import androidx.text.LayoutCompat.TEXT_DIRECTION_LTR
import androidx.text.LayoutCompat.TEXT_DIRECTION_RTL
import androidx.text.StaticLayoutCompat
import androidx.text.style.BaselineShiftSpan
import androidx.text.style.FontFeatureSpan
import androidx.text.style.LetterSpacingSpan
import androidx.text.style.ShadowSpan
import androidx.text.style.SkewXSpan
import androidx.text.style.TypefaceSpan
import androidx.ui.core.Density
import androidx.ui.core.LayoutDirection
import androidx.ui.core.px
import androidx.ui.core.sp
import androidx.ui.core.withDensity
import androidx.ui.engine.geometry.Offset
import androidx.ui.text.style.BaselineShift
import androidx.ui.text.font.FontStyle
import androidx.ui.text.font.FontSynthesis
import androidx.ui.text.FontTestData.Companion.BASIC_MEASURE_FONT
import androidx.ui.text.font.FontWeight
import androidx.ui.text.ParagraphConstraints
import androidx.ui.text.style.TextAlign
import androidx.ui.text.style.TextDecoration
import androidx.ui.text.style.TextGeometricTransform
import androidx.ui.text.style.TextIndent
import androidx.ui.text.font.FontFamily
import androidx.ui.text.font.asFontFamily
import androidx.ui.graphics.Color
import androidx.ui.graphics.toArgb
import androidx.ui.text.matchers.equalToBitmap
import androidx.ui.text.matchers.hasSpan
import androidx.ui.text.matchers.hasSpanOnTop
import androidx.ui.text.AnnotatedString
import androidx.ui.painting.Shadow
import androidx.ui.text.ParagraphStyle
import androidx.ui.text.TestFontResourceLoader
import androidx.ui.text.TextStyle
import androidx.ui.text.style.TextDirectionAlgorithm
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.spy
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.Locale
import kotlin.math.ceil

@RunWith(JUnit4::class)
@SmallTest
class AndroidParagraphTest {
    // This sample font provides the following features:
    // 1. The width of most of visible characters equals to font size.
    // 2. The LTR/RTL characters are rendered as ▶/◀.
    // 3. The fontMetrics passed to TextPaint has descend - ascend equal to 1.2 * fontSize.
    private val fontFamily = BASIC_MEASURE_FONT.asFontFamily()
    private val defaultDensity = Density(density = 1f)
    private val context = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun draw_with_newline_and_line_break_default_values() {
        withDensity(defaultDensity) {
            val fontSize = 50.sp
            for (text in arrayOf("abc\ndef", "\u05D0\u05D1\u05D2\n\u05D3\u05D4\u05D5")) {
                val paragraphAndroid = simpleParagraph(
                    text = text,
                    textStyle = TextStyle(
                        fontSize = fontSize,
                        fontFamily = fontFamily
                    )
                )

                // 2 chars width
                paragraphAndroid.layout(ParagraphConstraints(width = 2 * fontSize.toPx().value))

                val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG)
                textPaint.textSize = fontSize.toPx().value
                textPaint.typeface = TypefaceAdapter().create(fontFamily)

                val staticLayout = StaticLayoutCompat.Builder(
                    text,
                    textPaint,
                    ceil(paragraphAndroid.width).toInt()
                )
                    .setEllipsizedWidth(ceil(paragraphAndroid.width).toInt())
                    .build()
                assertThat(paragraphAndroid.bitmap(), equalToBitmap(staticLayout.bitmap()))
            }
        }
    }

    @Test
    fun testAnnotatedString_setColorOnWholeText() {
        val text = "abcde"
        val textStyle = TextStyle(color = Color(0xFF0000FF))

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText, hasSpan(ForegroundColorSpan::class, 0, text.length))
    }

    @Test
    fun testAnnotatedString_setColorOnPartOfText() {
        val text = "abcde"
        val textStyle = TextStyle(color = Color(0xFF0000FF))

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText, hasSpan(ForegroundColorSpan::class, 0, "abc".length))
    }

    @Test
    fun testAnnotatedString_setColorTwice_lastOneOverwrite() {
        val text = "abcde"
        val textStyle = TextStyle(color = Color(0xFF0000FF))
        val textStyleOverwrite = TextStyle(color = Color(0xFF00FF00))

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(
                AnnotatedString.Item(textStyle, 0, text.length),
                AnnotatedString.Item(textStyleOverwrite, 0, "abc".length)
            )
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText, hasSpan(ForegroundColorSpan::class, 0, text.length))
        assertThat(paragraph.underlyingText, hasSpan(ForegroundColorSpan::class, 0, "abc".length))
        assertThat(
            paragraph.underlyingText,
            hasSpanOnTop(ForegroundColorSpan::class, 0, "abc".length)
        )
    }

    @Test
    fun testStyle_setTextDecorationOnWholeText_withLineThrough() {
        val text = "abcde"
        val textStyle = TextStyle(decoration = TextDecoration.LineThrough)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText, hasSpan(StrikethroughSpan::class, 0, text.length))
    }

    @Test
    fun testStyle_setTextDecorationOnWholeText_withUnderline() {
        val text = "abcde"
        val textStyle = TextStyle(decoration = TextDecoration.Underline)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText, hasSpan(UnderlineSpan::class, 0, text.length))
    }

    @Test
    fun testStyle_setTextDecorationOnPartText_withLineThrough() {
        val text = "abcde"
        val textStyle = TextStyle(decoration = TextDecoration.LineThrough)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText, hasSpan(StrikethroughSpan::class, 0, "abc".length))
    }

    @Test
    fun testStyle_setTextDecorationOnPartText_withUnderline() {
        val text = "abcde"
        val textStyle = TextStyle(decoration = TextDecoration.Underline)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText, hasSpan(UnderlineSpan::class, 0, "abc".length))
    }

    @Test
    fun testStyle_setTextDecoration_withLineThroughAndUnderline() {
        val text = "abcde"
        val textStyle = TextStyle(
            decoration = TextDecoration.combine(
                listOf(TextDecoration.LineThrough, TextDecoration.Underline)
            )
        )

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText, hasSpan(UnderlineSpan::class, 0, "abc".length))
        assertThat(paragraph.underlyingText, hasSpan(StrikethroughSpan::class, 0, "abc".length))
    }

    @Test
    fun testAnnotatedString_setFontSizeOnWholeText() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val paragraphWidth = text.length * fontSize.toPx().value
            val textStyle = TextStyle(fontSize = fontSize)

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            assertThat(paragraph.underlyingText, hasSpan(AbsoluteSizeSpan::class, 0, text.length))
        }
    }

    @Test
    fun testAnnotatedString_setFontSizeOnPartText() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val paragraphWidth = text.length * fontSize.toPx().value
            val textStyle = TextStyle(fontSize = fontSize)

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            assertThat(paragraph.underlyingText, hasSpan(AbsoluteSizeSpan::class, 0, "abc".length))
        }
    }

    @Test
    fun testAnnotatedString_setFontSizeTwice_lastOneOverwrite() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 20.sp
            val fontSizeOverwrite = 30.sp
            val paragraphWidth = text.length * fontSizeOverwrite.toPx().value
            val textStyle = TextStyle(fontSize = fontSize)
            val textStyleOverwrite = TextStyle(fontSize = fontSizeOverwrite)

            val paragraph = simpleParagraph(
                text = text,
                textStyles = listOf(
                    AnnotatedString.Item(textStyle, 0, text.length),
                    AnnotatedString.Item(textStyleOverwrite, 0, "abc".length)
                )
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            assertThat(paragraph.underlyingText, hasSpan(AbsoluteSizeSpan::class, 0, text.length))
            assertThat(paragraph.underlyingText, hasSpan(AbsoluteSizeSpan::class, 0, "abc".length))
            assertThat(
                paragraph.underlyingText,
                hasSpanOnTop(AbsoluteSizeSpan::class, 0, "abc".length)
            )
        }
    }

    @Test
    fun testAnnotatedString_setFontSizeScaleOnWholeText() {
        val text = "abcde"
        val fontSizeScale = 2.0f
        val textStyle = TextStyle(fontSizeScale = fontSizeScale)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(
            paragraph.underlyingText,
            hasSpan(RelativeSizeSpan::class, 0, text.length) {
                it.sizeChange == fontSizeScale
            }
        )
    }

    @Test
    fun testAnnotatedString_setFontSizeScaleOnPartText() {
        val text = "abcde"
        val fontSizeScale = 2.0f
        val textStyle = TextStyle(fontSizeScale = fontSizeScale)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(
            paragraph.underlyingText,
            hasSpan(RelativeSizeSpan::class, 0, "abc".length) {
                it.sizeChange == fontSizeScale
            }
        )
    }

    @Test
    fun testAnnotatedString_setLetterSpacingOnWholeText() {
        val text = "abcde"
        val letterSpacing = 2.0f
        val textStyle = TextStyle(letterSpacing = letterSpacing)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))
        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText, hasSpan(LetterSpacingSpan::class, 0, text.length))
    }

    @Test
    fun testAnnotatedString_setLetterSpacingOnPartText() {
        val text = "abcde"
        val textStyle = TextStyle(letterSpacing = 2.0f)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))
        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText, hasSpan(LetterSpacingSpan::class, 0, "abc".length))
    }

    @Test
    fun testAnnotatedString_setLetterSpacingTwice_lastOneOverwrite() {
        val text = "abcde"
        val textStyle = TextStyle(letterSpacing = 2.0f)
        val textStyleOverwrite = TextStyle(letterSpacing = 3.0f)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(
                AnnotatedString.Item(textStyle, 0, text.length),
                AnnotatedString.Item(textStyleOverwrite, 0, "abc".length)
            )
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))
        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText, hasSpan(LetterSpacingSpan::class, 0, text.length))
        assertThat(paragraph.underlyingText, hasSpan(LetterSpacingSpan::class, 0, "abc".length))
        assertThat(
            paragraph.underlyingText,
            hasSpanOnTop(LetterSpacingSpan::class, 0, "abc".length)
        )
    }

    @Test
    fun testAnnotatedString_setBackgroundOnWholeText() {
        val text = "abcde"
        val color = Color(0xFF0000FF)
        val textStyle = TextStyle(background = color)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText,
            hasSpan(BackgroundColorSpan::class, 0, text.length) { span ->
                span.backgroundColor == color.toArgb()
            }
        )
    }

    @Test
    fun testAnnotatedString_setBackgroundOnPartText() {
        val text = "abcde"
        val color = Color(0xFF0000FF)
        val textStyle = TextStyle(background = color)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText,
            hasSpan(BackgroundColorSpan::class, 0, "abc".length) { span ->
                span.backgroundColor == color.toArgb()
            }
        )
    }

    @Test
    fun testAnnotatedString_setBackgroundTwice_lastOneOverwrite() {
        val text = "abcde"
        val color = Color(0xFF0000FF)
        val textStyle = TextStyle(background = color)
        val colorOverwrite = Color(0xFF00FF00)
        val textStyleOverwrite = TextStyle(background = colorOverwrite)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(
                AnnotatedString.Item(textStyle, 0, text.length),
                AnnotatedString.Item(textStyleOverwrite, 0, "abc".length)
            )
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(paragraph.underlyingText,
            hasSpan(BackgroundColorSpan::class, 0, text.length) { span ->
                span.backgroundColor == color.toArgb()
            }
        )
        assertThat(paragraph.underlyingText,
            hasSpan(BackgroundColorSpan::class, 0, "abc".length) { span ->
                span.backgroundColor == colorOverwrite.toArgb()
            }
        )
        assertThat(
            paragraph.underlyingText,
            hasSpanOnTop(BackgroundColorSpan::class, 0, "abc".length) { span ->
                span.backgroundColor == colorOverwrite.toArgb()
            }
        )
    }

    @Test
    fun testAnnotatedString_setLocaleOnWholeText() {
        val text = "abcde"
        val locale = Locale("en", "US")
        val textStyle = TextStyle(locale = locale)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText, hasSpan(LocaleSpan::class, 0, text.length))
    }

    @Test
    fun testAnnotatedString_setLocaleOnPartText() {
        val text = "abcde"
        val locale = Locale("en", "US")
        val textStyle = TextStyle(locale = locale)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText, hasSpan(LocaleSpan::class, 0, "abc".length))
    }

    @Test
    fun testAnnotatedString_setLocaleTwice_lastOneOverwrite() {
        val text = "abcde"
        val textStyle = TextStyle(locale = Locale("en", "US"))
        val textStyleOverwrite = TextStyle(locale = Locale("ja", "JP"))

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(
                AnnotatedString.Item(textStyle, 0, text.length),
                AnnotatedString.Item(textStyleOverwrite, 0, "abc".length)
            )
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText, hasSpan(LocaleSpan::class, 0, text.length))
        assertThat(paragraph.underlyingText, hasSpan(LocaleSpan::class, 0, "abc".length))
        assertThat(
            paragraph.underlyingText,
            hasSpanOnTop(LocaleSpan::class, 0, "abc".length)
        )
    }

    @Test
    fun testAnnotatedString_setBaselineShiftOnWholeText() {
        val text = "abcde"
        val textStyle = TextStyle(baselineShift = BaselineShift.Subscript)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        // width is not important
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText, hasSpan(BaselineShiftSpan::class, 0, text.length))
    }

    @Test
    fun testAnnotatedString_setBaselineShiftOnPartText() {
        val text = "abcde"
        val textStyle = TextStyle(baselineShift = BaselineShift.Superscript)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
        )
        // width is not important
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText, hasSpan(BaselineShiftSpan::class, 0, "abc".length))
    }

    @Test
    fun testAnnotatedString_setBaselineShiftTwice_LastOneOnTop() {
        val text = "abcde"
        val textStyle = TextStyle(baselineShift = BaselineShift.Subscript)
        val textStyleOverwrite =
            TextStyle(baselineShift = BaselineShift.Superscript)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(
                AnnotatedString.Item(textStyle, 0, text.length),
                AnnotatedString.Item(textStyleOverwrite, 0, "abc".length)
            )
        )
        // width is not important
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText, hasSpan(BaselineShiftSpan::class, 0, text.length))
        assertThat(paragraph.underlyingText, hasSpan(BaselineShiftSpan::class, 0, "abc".length))
        assertThat(
            paragraph.underlyingText,
            hasSpanOnTop(BaselineShiftSpan::class, 0, "abc".length)
        )
    }

    @Test
    fun testAnnotatedString_setTextGeometricTransformWithNull_noSpanSet() {
        val text = "abcde"
        val textStyle =
            TextStyle(textGeometricTransform = TextGeometricTransform(
                null,
                null
            )
            )

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        // width is not important
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText, not(hasSpan(ScaleXSpan::class, 0, text.length)))
        assertThat(paragraph.underlyingText, not(hasSpan(SkewXSpan::class, 0, text.length)))
    }

    @Test
    fun testAnnotatedString_setTextGeometricTransformWithScaleX() {
        val text = "abcde"
        val scaleX = 0.5f
        val textStyle = TextStyle(
            textGeometricTransform = TextGeometricTransform(
                scaleX,
                null
            )
        )

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        // width is not important
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(
            paragraph.underlyingText,
            hasSpan(ScaleXSpan::class, 0, text.length) { it.scaleX == scaleX }
        )
        assertThat(paragraph.underlyingText, not(hasSpan(SkewXSpan::class, 0, text.length)))
    }

    @Test
    fun testAnnotatedString_setTextGeometricTransformWithSkewX() {
        val text = "aa"
        val skewX = 1f
        val textStyle =
            TextStyle(textGeometricTransform = TextGeometricTransform(
                null,
                skewX
            )
            )

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, text.length))
        )
        // width is not important
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(
            paragraph.underlyingText,
            hasSpan(SkewXSpan::class, 0, text.length) { it.skewX == skewX }
        )
        assertThat(paragraph.underlyingText, not(hasSpan(ScaleXSpan::class, 0, text.length)))
    }

    @Test
    fun textIndent_onWholeParagraph() {
        val text = "abc\ndef"
        val firstLine = 40
        val restLine = 20

        val paragraph = simpleParagraph(
            text = text,
            textIndent = TextIndent(firstLine.px, restLine.px)
        )
        // width is not important
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(
            paragraph.underlyingText,
            hasSpan(LeadingMarginSpan.Standard::class, 0, text.length) {
                it.getLeadingMargin(true) == firstLine && it.getLeadingMargin(false) == restLine
            }
        )
    }

    @Test
    fun testAnnotatedString_setShadow() {
        val text = "abcde"
        val color = Color(0xFF00FF00)
        val offset = Offset(1f, 2f)
        val radius = 3.px
        val textStyle = TextStyle(shadow = Shadow(color, offset, radius))

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(
                AnnotatedString.Item(textStyle, start = 0, end = text.length)
            )
        )
        // width is not important
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(
            paragraph.underlyingText,
            hasSpan(ShadowSpan::class, start = 0, end = text.length) {
                return@hasSpan it.color == color.toArgb() &&
                        it.offsetX == offset.dx &&
                        it.offsetY == offset.dy &&
                        it.radius == radius.value
            }
        )
    }

    @Test
    fun testAnnotatedString_setShadowTwice_lastOnTop() {
        val text = "abcde"
        val color = Color(0xFF00FF00)
        val offset = Offset(1f, 2f)
        val radius = 3.px
        val textStyle = TextStyle(shadow = Shadow(color, offset, radius))

        val colorOverwrite = Color(0xFF0000FF)
        val offsetOverwrite = Offset(3f, 2f)
        val radiusOverwrite = 1.px
        val textStyleOverwrite = TextStyle(
            shadow = Shadow(colorOverwrite, offsetOverwrite, radiusOverwrite)
        )

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(
                AnnotatedString.Item(textStyle, start = 0, end = text.length),
                AnnotatedString.Item(textStyleOverwrite, start = 0, end = "abc".length)
            )
        )
        // width is not important
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(
            paragraph.underlyingText,
            hasSpan(ShadowSpan::class, start = 0, end = text.length) {
                return@hasSpan it.color == color.toArgb() &&
                        it.offsetX == offset.dx &&
                        it.offsetY == offset.dy &&
                        it.radius == radius.value
            }
        )
        assertThat(
            paragraph.underlyingText,
            hasSpanOnTop(ShadowSpan::class, start = 0, end = "abc".length) {
                return@hasSpanOnTop it.color == colorOverwrite.toArgb() &&
                        it.offsetX == offsetOverwrite.dx &&
                        it.offsetY == offsetOverwrite.dy &&
                        it.radius == radiusOverwrite.value
            }
        )
    }

    @Test
    fun testAnnotatedString_fontFamily_addsTypefaceSpanWithCorrectTypeface() {
        val text = "abcde"
        val textStyle = TextStyle(
            fontFamily = fontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.bold
        )
        val expectedTypeface = TypefaceAdapter().create(
            fontFamily = fontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.bold
        )
        val expectedStart = 0
        val expectedEnd = "abc".length

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(
                AnnotatedString.Item(
                    textStyle,
                    expectedStart,
                    expectedEnd
                )
            )
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(
            paragraph.underlyingText,
            hasSpan(TypefaceSpan::class, expectedStart, expectedEnd) { span ->
                span.typeface == expectedTypeface
            })
    }

    @Test
    fun testAnnotatedString_fontFamily_whenFontSynthesizeTurnedOff() {
        val text = "abcde"
        val textStyle = TextStyle(
            fontFamily = fontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.bold,
            fontSynthesis = FontSynthesis.None
        )
        val expectedTypeface = TypefaceAdapter().create(
            fontFamily = fontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.bold,
            fontSynthesis = FontSynthesis.None
        )
        val expectedStart = 0
        val expectedEnd = "abc".length

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(
                AnnotatedString.Item(
                    textStyle,
                    expectedStart,
                    expectedEnd
                )
            )
        )
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(paragraph.underlyingText.toString(), equalTo(text))
        assertThat(
            paragraph.underlyingText,
            hasSpan(TypefaceSpan::class, expectedStart, expectedEnd) { span ->
                span.typeface == expectedTypeface
            })
    }

    @Test
    fun testAnnotatedString_fontFeatureSetting_setSpanOnText() {
        val text = "abc"
        val fontFeatureSettings = "\"kern\" 0"
        val textStyle = TextStyle(fontFeatureSettings = fontFeatureSettings)

        val paragraph = simpleParagraph(
            text = text,
            textStyles = listOf(AnnotatedString.Item(textStyle, 0, "abc".length))
        )
        // width is not important
        paragraph.layout(ParagraphConstraints(width = 100.0f))

        assertThat(
            paragraph.underlyingText,
            hasSpan(FontFeatureSpan::class, 0, "abc".length) {
                it.fontFeatureSettings == fontFeatureSettings
            })
    }

    @Test
    fun testEmptyFontFamily() {
        val typefaceAdapter = mock<TypefaceAdapter>()
        val paragraph = simpleParagraph(
            text = "abc",
            typefaceAdapter = typefaceAdapter
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        verify(typefaceAdapter, never()).create(
            fontFamily = any(),
            fontWeight = any(),
            fontStyle = any(),
            fontSynthesis = any()
        )
        assertThat(paragraph.textPaint.typeface, nullValue())
    }

    @Test
    fun testEmptyFontFamily_withBoldFontWeightSelection() {
        val typefaceAdapter = spy(TypefaceAdapter())

        val paragraph = simpleParagraph(
            text = "abc",
            textStyle = TextStyle(
                fontFamily = null,
                fontWeight = FontWeight.bold
            ),
            typefaceAdapter = typefaceAdapter
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        verify(typefaceAdapter, times(1)).create(
            fontFamily = eq(null),
            fontWeight = eq(FontWeight.bold),
            fontStyle = eq(FontStyle.Normal),
            fontSynthesis = eq(FontSynthesis.All)
        )

        val typeface = paragraph.textPaint.typeface
        assertThat(typeface, not(nullValue()))
        assertThat(typeface.isBold, equalTo(true))
        assertThat(typeface.isItalic, equalTo(false))
    }

    @Test
    fun testEmptyFontFamily_withFontStyleSelection() {
        val typefaceAdapter = spy(TypefaceAdapter())
        val paragraph = simpleParagraph(
            text = "abc",
            textStyle = TextStyle(
                fontFamily = null,
                fontStyle = FontStyle.Italic
            ),
            typefaceAdapter = typefaceAdapter
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        verify(typefaceAdapter, times(1)).create(
            fontFamily = eq(null),
            fontWeight = eq(FontWeight.normal),
            fontStyle = eq(FontStyle.Italic),
            fontSynthesis = eq(FontSynthesis.All)
        )

        val typeface = paragraph.textPaint.typeface
        assertThat(typeface, not(nullValue()))
        assertThat(typeface.isBold, equalTo(false))
        assertThat(typeface.isItalic, equalTo(true))
    }

    @Test
    fun testFontFamily_withGenericFamilyName() {
        val typefaceAdapter = spy(TypefaceAdapter())
        val fontFamily = FontFamily("sans-serif")

        val paragraph = simpleParagraph(
            text = "abc",
            textStyle = TextStyle(
                fontFamily = fontFamily
            ),
            typefaceAdapter = typefaceAdapter
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        verify(typefaceAdapter, times(1)).create(
            fontFamily = eq(fontFamily),
            fontWeight = eq(FontWeight.normal),
            fontStyle = eq(FontStyle.Normal),
            fontSynthesis = eq(FontSynthesis.All)
        )

        val typeface = paragraph.textPaint.typeface
        assertThat(typeface, not(nullValue()))
        assertThat(typeface.isBold, equalTo(false))
        assertThat(typeface.isItalic, equalTo(false))
    }

    @Test
    fun testFontFamily_withCustomFont() {
        val typefaceAdapter = spy(TypefaceAdapter())
        val paragraph = simpleParagraph(
            text = "abc",
            textStyle = TextStyle(
                fontFamily = fontFamily
            ),
            typefaceAdapter = typefaceAdapter
        )
        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        verify(typefaceAdapter, times(1)).create(
            fontFamily = eq(fontFamily),
            fontWeight = eq(FontWeight.normal),
            fontStyle = eq(FontStyle.Normal),
            fontSynthesis = eq(FontSynthesis.All)
        )
        val typeface = paragraph.textPaint.typeface
        assertThat(typeface.isBold, equalTo(false))
        assertThat(typeface.isItalic, equalTo(false))
    }

    @Test
    fun testEllipsis_withMaxLineEqualsNull_doesNotEllipsis() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 20.sp
            val paragraphWidth = (text.length - 1) * fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                textStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSize
                ),
                ellipsis = true
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))
            for (i in 0 until paragraph.lineCount) {
                assertFalse(paragraph.isEllipsisApplied(i))
            }
        }
    }

    @Test
    fun testEllipsis_withMaxLinesLessThanTextLines_doesEllipsis() {
        withDensity(defaultDensity) {
            val text = "abcde"
            val fontSize = 100.sp
            // Note that on API 21, if the next line only contains 1 character, ellipsis won't work
            val paragraphWidth = (text.length - 1.5f) * fontSize.toPx().value
            val paragraph = simpleParagraph(
                text = text,
                ellipsis = true,
                maxLines = 1,
                textStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSize
                )
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            assertTrue(paragraph.isEllipsisApplied(0))
        }
    }

    @Test
    fun testEllipsis_withMaxLinesMoreThanTextLines_doesNotEllipsis() {
        withDensity(defaultDensity) {
            val text = "abc"
            val fontSize = 100.sp
            val paragraphWidth = (text.length - 1) * fontSize.toPx().value
            val maxLines = ceil(text.length * fontSize.toPx().value / paragraphWidth).toInt()
            val paragraph = simpleParagraph(
                text = text,
                ellipsis = true,
                maxLines = maxLines,
                textStyle = TextStyle(
                    fontFamily = fontFamily,
                    fontSize = fontSize
                )
            )
            paragraph.layout(ParagraphConstraints(width = paragraphWidth))

            for (i in 0 until paragraph.lineCount) {
                assertFalse(paragraph.isEllipsisApplied(i))
            }
        }
    }

    @Test
    fun testTextStyle_fontSize_appliedOnTextPaint() {
        withDensity(defaultDensity) {
            val fontSize = 100.sp
            val paragraph = simpleParagraph(
                text = "",
                textStyle = TextStyle(fontSize = fontSize)
            )
            paragraph.layout(ParagraphConstraints(width = 0.0f))

            assertThat(paragraph.textPaint.textSize, equalTo(fontSize.toPx().value))
        }
    }

    @Test
    fun testTextStyle_fontSizeScale_appliedOnTextPaint() {
        withDensity(defaultDensity) {
            val fontSize = 100.sp
            val fontSizeScale = 2f
            val paragraph = simpleParagraph(
                text = "",
                textStyle = TextStyle(
                    fontSize = fontSize,
                    fontSizeScale = fontSizeScale
                )
            )
            paragraph.layout(ParagraphConstraints(width = 0.0f))

            assertThat(paragraph.textPaint.textSize, equalTo(fontSize.toPx().value * fontSizeScale))
        }
    }

    @Test
    fun testTextStyle_locale_appliedOnTextPaint() {
        val systemLocale = java.util.Locale.JAPANESE
        val locale = Locale(systemLocale.language, systemLocale.country)

        val paragraph = simpleParagraph(
            text = "",
            textStyle = TextStyle(locale = locale)
        )
        paragraph.layout(ParagraphConstraints(width = 0.0f))

        assertThat(paragraph.textPaint.textLocale, equalTo(systemLocale))
    }

    @Test
    fun testTextStyle_color_appliedOnTextPaint() {
        val color = Color(0x12345678)
        val paragraph = simpleParagraph(
            text = "",
            textStyle = TextStyle(color = color)
        )
        paragraph.layout(ParagraphConstraints(width = 0.0f))

        assertThat(paragraph.textPaint.color, equalTo(color.toArgb()))
    }

    @Test
    fun testTextStyle_letterSpacing_appliedOnTextPaint() {
        val letterSpacing = 2.0f
        val paragraph = simpleParagraph(
            text = "",
            textStyle = TextStyle(letterSpacing = letterSpacing)
        )
        paragraph.layout(ParagraphConstraints(width = 0.0f))

        assertThat(paragraph.textPaint.letterSpacing, equalTo(letterSpacing))
    }

    @Test
    fun testTextStyle_fontFeatureSettings_appliedOnTextPaint() {
        val fontFeatureSettings = "\"kern\" 0"
        val paragraph = simpleParagraph(
            text = "",
            textStyle = TextStyle(fontFeatureSettings = fontFeatureSettings)
        )
        paragraph.layout(ParagraphConstraints(width = 0.0f))

        assertThat(paragraph.textPaint.fontFeatureSettings, equalTo(fontFeatureSettings))
    }

    @Test
    fun testTextStyle_scaleX_appliedOnTextPaint() {
        val scaleX = 0.5f
        val paragraph = simpleParagraph(
            text = "",
            textStyle = TextStyle(
                textGeometricTransform = TextGeometricTransform(
                    scaleX = scaleX
                )
            )
        )
        paragraph.layout(ParagraphConstraints(width = 0.0f))

        assertThat(paragraph.textPaint.textScaleX, equalTo(scaleX))
    }

    @Test
    fun testTextStyle_skewX_appliedOnTextPaint() {
        val skewX = 0.5f
        val paragraph = simpleParagraph(
            text = "",
            textStyle = TextStyle(
                textGeometricTransform = TextGeometricTransform(
                    skewX = skewX
                )
            )
        )
        paragraph.layout(ParagraphConstraints(width = 0.0f))

        assertThat(paragraph.textPaint.textSkewX, equalTo(skewX))
    }

    @Test
    fun testTextStyle_decoration_underline_appliedOnTextPaint() {
        val paragraph = simpleParagraph(
            text = "",
            textStyle = TextStyle(decoration = TextDecoration.Underline)
        )
        paragraph.layout(ParagraphConstraints(width = 0.0f))

        assertThat(paragraph.textPaint.isUnderlineText, equalTo(true))
    }

    @Test
    fun testTextStyle_decoration_lineThrough_appliedOnTextPaint() {
        val paragraph = simpleParagraph(
            text = "",
            textStyle = TextStyle(decoration = TextDecoration.LineThrough)
        )
        paragraph.layout(ParagraphConstraints(width = 0.0f))

        assertThat(paragraph.textPaint.isStrikeThruText, equalTo(true))
    }

    @Test
    fun testTextStyle_background_appliedAsSpan() {
        // bgColor is reset in the Android Layout constructor.
        // therefore we cannot apply them on paint, have to use spans.
        val text = "abc"
        val color = Color(0x12345678)
        val paragraph = simpleParagraph(
            text = text,
            textStyle = TextStyle(background = color)
        )
        paragraph.layout(ParagraphConstraints(width = 0.0f))

        assertThat(paragraph.underlyingText,
            hasSpan(BackgroundColorSpan::class, 0, text.length) { span ->
                span.backgroundColor == color.toArgb()
            }
        )
    }

    @Test
    fun testTextStyle_baselineShift_appliedAsSpan() {
        // baselineShift is reset in the Android Layout constructor.
        // therefore we cannot apply them on paint, have to use spans.
        val text = "abc"
        val baselineShift = BaselineShift.Subscript
        val paragraph = simpleParagraph(
            text = text,
            textStyle = TextStyle(baselineShift = baselineShift)
        )
        paragraph.layout(ParagraphConstraints(width = 0.0f))

        assertThat(
            paragraph.underlyingText,
            hasSpan(BaselineShiftSpan::class, 0, text.length) { span ->
                span.multiplier == BaselineShift.Subscript.multiplier
            }
        )
    }

    @Test
    fun locale_isDefaultLocaleIfNotProvided() {
        val text = "abc"
        val paragraph = simpleParagraph(text = text)

        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        assertThat(
            paragraph.textLocale.toLanguageTag(),
            equalTo(java.util.Locale.getDefault().toLanguageTag())
        )
    }

    @Test
    fun locale_isSetOnParagraphImpl_enUS() {
        val locale = Locale("en", "US")
        val text = "abc"
        val paragraph = simpleParagraph(
            text = text,
            textStyle = TextStyle(locale = locale)
        )

        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        assertThat(paragraph.textLocale.toLanguageTag(), equalTo("en-US"))
    }

    @Test
    fun locale_isSetOnParagraphImpl_jpJP() {
        val locale = Locale("ja", "JP")
        val text = "abc"
        val paragraph = simpleParagraph(
            text = text,
            textStyle = TextStyle(locale = locale)
        )

        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        assertThat(paragraph.textLocale.toLanguageTag(), equalTo("ja-JP"))
    }

    @Test
    fun locale_noCountryCode_isSetOnParagraphImpl() {
        val locale = Locale("ja")
        val text = "abc"
        val paragraph = simpleParagraph(
            text = text,
            textStyle = TextStyle(locale = locale)
        )

        paragraph.layout(ParagraphConstraints(width = Float.MAX_VALUE))

        assertThat(paragraph.textLocale.toLanguageTag(), equalTo("ja"))
    }

    @Test
    fun resolveTextDirectionHeuristics_null() {
        assertEquals(
            TEXT_DIRECTION_FIRST_STRONG_LTR,
            simpleParagraph(
                text = "a",
                layoutDirection = LayoutDirection.Ltr
            ).resolveTextDirectionHeuristics(null)
        )

        assertEquals(
            TEXT_DIRECTION_FIRST_STRONG_RTL,
            simpleParagraph(
                text = "a",
                layoutDirection = LayoutDirection.Rtl
            ).resolveTextDirectionHeuristics(null)
        )
    }

    @Test
    fun resolveTextDirectionHeuristics_DefaultLtr() {
        assertEquals(
            TEXT_DIRECTION_FIRST_STRONG_LTR,
            simpleParagraph(
                text = "a",
                layoutDirection = LayoutDirection.Ltr
            ).resolveTextDirectionHeuristics(TextDirectionAlgorithm.ContentOrLtr)
        )

        assertEquals(
            TEXT_DIRECTION_FIRST_STRONG_LTR,
            simpleParagraph(
                text = "a",
                layoutDirection = LayoutDirection.Rtl
                ).resolveTextDirectionHeuristics(TextDirectionAlgorithm.ContentOrLtr)
        )
    }

    @Test
    fun resolveTextDirectionHeuristics_DefaultRtl() {
        assertEquals(
            TEXT_DIRECTION_FIRST_STRONG_RTL,
            simpleParagraph(
                text = "a",
                layoutDirection = LayoutDirection.Ltr
            ).resolveTextDirectionHeuristics(TextDirectionAlgorithm.ContentOrRtl)
        )

        assertEquals(
            TEXT_DIRECTION_FIRST_STRONG_RTL,
            simpleParagraph(
                text = "a",
                layoutDirection = LayoutDirection.Rtl
                ).resolveTextDirectionHeuristics(TextDirectionAlgorithm.ContentOrRtl)
        )
    }

    @Test
    fun resolveTextDirectionHeuristics_Ltr() {
        assertEquals(
            TEXT_DIRECTION_LTR,
            simpleParagraph(
                text = "a",
                layoutDirection = LayoutDirection.Ltr
            ).resolveTextDirectionHeuristics(TextDirectionAlgorithm.ForceLtr)
        )

        assertEquals(
            TEXT_DIRECTION_LTR,
            simpleParagraph(
                text = "a",
                layoutDirection = LayoutDirection.Rtl
                ).resolveTextDirectionHeuristics(TextDirectionAlgorithm.ForceLtr)
        )
    }

    @Test
    fun resolveTextDirectionHeuristics_Rtl() {
        assertEquals(
            TEXT_DIRECTION_RTL,
            simpleParagraph(
                text = "a",
                layoutDirection = LayoutDirection.Ltr
            ).resolveTextDirectionHeuristics(TextDirectionAlgorithm.ForceRtl)
        )

        assertEquals(
            TEXT_DIRECTION_RTL,
            simpleParagraph(
                text = "a",
                layoutDirection = LayoutDirection.Rtl
                ).resolveTextDirectionHeuristics(TextDirectionAlgorithm.ForceRtl)
        )
    }

    private fun simpleParagraph(
        text: String = "",
        textStyles: List<AnnotatedString.Item<TextStyle>> = listOf(),
        textIndent: TextIndent? = null,
        textAlign: TextAlign? = null,
        ellipsis: Boolean? = null,
        maxLines: Int? = null,
        textStyle: TextStyle? = null,
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        typefaceAdapter: TypefaceAdapter = TypefaceAdapter()
    ): AndroidParagraph {
        return AndroidParagraph(
            text = text,
            textStyles = textStyles,
            typefaceAdapter = typefaceAdapter,
            style = TextStyle().merge(textStyle),
            paragraphStyle = ParagraphStyle(
                textAlign = textAlign,
                textIndent = textIndent
            ),
            maxLines = maxLines,
            ellipsis = ellipsis,
            density = Density(density = 1f),
            layoutDirection = layoutDirection
        )
    }

    private fun TypefaceAdapter() = TypefaceAdapter(
        resourceLoader = TestFontResourceLoader(context)
    )
}
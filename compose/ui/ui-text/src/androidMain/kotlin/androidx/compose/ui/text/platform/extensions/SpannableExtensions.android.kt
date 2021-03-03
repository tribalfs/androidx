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

package androidx.compose.ui.text.platform.extensions

import android.graphics.Typeface
import android.os.Build
import android.text.Spannable
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.LocaleSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ScaleXSpan
import android.text.style.StrikethroughSpan
import android.text.style.UnderlineSpan
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.android.InternalPlatformTextApi
import androidx.compose.ui.text.android.style.BaselineShiftSpan
import androidx.compose.ui.text.android.style.FontFeatureSpan
import androidx.compose.ui.text.android.style.LetterSpacingSpanEm
import androidx.compose.ui.text.android.style.LetterSpacingSpanPx
import androidx.compose.ui.text.android.style.LineHeightSpan
import androidx.compose.ui.text.android.style.ShadowSpan
import androidx.compose.ui.text.android.style.SkewXSpan
import androidx.compose.ui.text.android.style.TypefaceSpan
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intersect
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.platform.TypefaceAdapter
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextGeometricTransform
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.util.fastForEachIndexed
import kotlin.math.ceil
import kotlin.math.roundToInt

private data class SpanRange(
    val span: Any,
    val start: Int,
    val end: Int
)

internal fun Spannable.setSpan(span: Any, start: Int, end: Int) {
    setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
}

@Suppress("DEPRECATION")
internal fun Spannable.setTextIndent(
    textIndent: TextIndent?,
    contextFontSize: Float,
    density: Density
) {
    textIndent?.let { indent ->
        if (indent.firstLine == 0.sp && indent.restLine == 0.sp) return@let
        if (indent.firstLine.isUnspecified || indent.restLine.isUnspecified) return@let
        with(density) {
            val firstLine = when (indent.firstLine.type) {
                TextUnitType.Sp -> indent.firstLine.toPx()
                TextUnitType.Em -> indent.firstLine.value * contextFontSize
                TextUnitType.Unspecified -> 0f
            }
            val restLine = when (indent.restLine.type) {
                TextUnitType.Sp -> indent.restLine.toPx()
                TextUnitType.Em -> indent.restLine.value * contextFontSize
                TextUnitType.Unspecified -> 0f
            }
            setSpan(
                LeadingMarginSpan.Standard(
                    ceil(firstLine).toInt(),
                    ceil(restLine).toInt()
                ),
                0,
                length
            )
        }
    }
}

@OptIn(InternalPlatformTextApi::class)
@Suppress("DEPRECATION")
internal fun Spannable.setLineHeight(
    lineHeight: TextUnit,
    contextFontSize: Float,
    density: Density
) {
    when (lineHeight.type) {
        TextUnitType.Sp -> with(density) {
            setSpan(
                LineHeightSpan(ceil(lineHeight.toPx()).toInt()),
                0,
                length
            )
        }
        TextUnitType.Em -> {
            setSpan(
                LineHeightSpan(ceil(lineHeight.value * contextFontSize).toInt()),
                0,
                length
            )
        }
        TextUnitType.Unspecified -> {
        } // Do nothing
    }
}

internal fun Spannable.setSpanStyles(
    spanStyles: List<AnnotatedString.Range<SpanStyle>>,
    density: Density,
    typefaceAdapter: TypefaceAdapter
) {

    setFontAttributes(spanStyles, typefaceAdapter)

    // LetterSpacingSpanPx/LetterSpacingSpanSP has lower priority than normal spans. Because
    // letterSpacing relies on the fontSize on [Paint] to compute Px/Sp from Em. So it must be
    // applied after all spans that changes the fontSize.
    val lowPrioritySpans = ArrayList<SpanRange>()

    for (spanStyleRange in spanStyles) {
        val start = spanStyleRange.start
        val end = spanStyleRange.end

        if (start < 0 || start >= length || end <= start || end > length) continue

        setSpanStyle(
            spanStyleRange,
            density,
            lowPrioritySpans
        )
    }

    lowPrioritySpans.fastForEach { (span, start, end) ->
        setSpan(span, start, end)
    }
}

private fun Spannable.setSpanStyle(
    spanStyleRange: AnnotatedString.Range<SpanStyle>,
    density: Density,
    lowPrioritySpans: ArrayList<SpanRange>
) {
    val start = spanStyleRange.start
    val end = spanStyleRange.end
    val style = spanStyleRange.item

    // Be aware that SuperscriptSpan needs to be applied before all other spans which
    // affect FontMetrics
    setBaselineShift(style.baselineShift, start, end)

    setColor(style.color, start, end)

    setTextDecoration(style.textDecoration, start, end)

    setFontSize(style.fontSize, density, start, end)

    setFontFeatureSettings(style.fontFeatureSettings, start, end)

    setGeometricTransform(style.textGeometricTransform, start, end)

    setLocaleList(style.localeList, start, end)

    setBackground(style.background, start, end)

    setShadow(style.shadow, start, end)

    createLetterSpacingSpan(style.letterSpacing, density)?.let {
        lowPrioritySpans.add(
            SpanRange(it, start, end)
        )
    }
}

@OptIn(InternalPlatformTextApi::class)
private fun Spannable.setFontAttributes(
    spanStyles: List<AnnotatedString.Range<SpanStyle>>,
    typefaceAdapter: TypefaceAdapter
) {
    val fontRelatedSpanStyles = spanStyles.filter {
        it.item.hasFontAttributes() || it.item.fontSynthesis != null
    }
    flattenStylesAndApply(fontRelatedSpanStyles) { spanStyle, start, end ->
        setSpan(
            TypefaceSpan(
                typefaceAdapter.create(
                    fontFamily = spanStyle.fontFamily,
                    fontWeight = spanStyle.fontWeight ?: FontWeight.Normal,
                    fontStyle = spanStyle.fontStyle ?: FontStyle.Normal,
                    fontSynthesis = spanStyle.fontSynthesis ?: FontSynthesis.All
                )
            ),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
}

/**
 * Flatten styles in the [spanStyles], so that overlapping styles are merged, and then apply the
 * [block] on the merged [SpanStyle].
 *
 * @param spanStyles the input [SpanStyle] ranges to be flattened.
 * @param block the function to be applied on the merged [SpanStyle].
 */
internal fun flattenStylesAndApply(
    spanStyles: List<AnnotatedString.Range<SpanStyle>>,
    block: (SpanStyle, Int, Int) -> Unit
) {
    if (spanStyles.size <= 1) {
        if (spanStyles.isNotEmpty()) {
            block(spanStyles[0].item, spanStyles[0].start, spanStyles[0].end)
        }
        return
    }

    val spanCount = spanStyles.size
    val transitionOffsets = Array(spanCount * 2) { 0 }
    spanStyles.fastForEachIndexed { idx, spanStyle ->
        transitionOffsets[idx] = spanStyle.start
        transitionOffsets[idx + spanCount] = spanStyle.end
    }
    transitionOffsets.sort()

    var lastTransitionOffsets = transitionOffsets.first()
    for (transitionOffset in transitionOffsets) {
        // There might be duplicated transition offsets, we skip them here.
        if (transitionOffset == lastTransitionOffsets) {
            continue
        }

        // Check all spans that intersects with this transition range.
        var mergedSpanStyle: SpanStyle? = null
        for (spanStyle in spanStyles) {
            if (
                intersect(lastTransitionOffsets, transitionOffset, spanStyle.start, spanStyle.end)
            ) {
                if (mergedSpanStyle == null) {
                    mergedSpanStyle = SpanStyle()
                }
                mergedSpanStyle = mergedSpanStyle.merge(spanStyle.item)
            }
        }

        if (mergedSpanStyle != null) {
            block(mergedSpanStyle, lastTransitionOffsets, transitionOffset)
        }

        lastTransitionOffsets = transitionOffset
    }
}

@OptIn(InternalPlatformTextApi::class)
@Suppress("DEPRECATION")
private fun createLetterSpacingSpan(
    letterSpacing: TextUnit,
    density: Density
): MetricAffectingSpan? {
    return when (letterSpacing.type) {
        TextUnitType.Sp -> with(density) {
            LetterSpacingSpanPx(letterSpacing.toPx())
        }
        TextUnitType.Em -> {
            LetterSpacingSpanEm(letterSpacing.value)
        }
        TextUnitType.Unspecified -> {
            null
        }
    }
}

@OptIn(InternalPlatformTextApi::class)
private fun Spannable.setShadow(shadow: Shadow?, start: Int, end: Int) {
    shadow?.let {
        setSpan(
            ShadowSpan(it.color.toArgb(), it.offset.x, it.offset.y, it.blurRadius),
            start,
            end
        )
    }
}

internal fun Spannable.setBackground(color: Color, start: Int, end: Int) {
    if (color.isSpecified) {
        setSpan(
            BackgroundColorSpan(color.toArgb()),
            start,
            end
        )
    }
}

internal fun Spannable.setLocaleList(localeList: LocaleList?, start: Int, end: Int) {
    localeList?.let {
        setSpan(
            if (Build.VERSION.SDK_INT >= 24) {
                LocaleListHelperMethods.localeSpan(it)
            } else {
                val locale = if (it.isEmpty()) Locale.current else it[0]
                LocaleSpan(locale.toJavaLocale())
            },
            start,
            end
        )
    }
}

@OptIn(InternalPlatformTextApi::class)
private fun Spannable.setGeometricTransform(
    textGeometricTransform: TextGeometricTransform?,
    start: Int,
    end: Int
) {
    textGeometricTransform?.let {
        if (it.scaleX != 1.0f) {
            setSpan(ScaleXSpan(it.scaleX), start, end)
        }
        if (it.skewX != 0f) {
            setSpan(SkewXSpan(it.skewX), start, end)
        }
    }
}

@OptIn(InternalPlatformTextApi::class)
private fun Spannable.setFontFeatureSettings(fontFeatureSettings: String?, start: Int, end: Int) {
    fontFeatureSettings?.let {
        setSpan(FontFeatureSpan(it), start, end)
    }
}

@Suppress("DEPRECATION")
internal fun Spannable.setFontSize(fontSize: TextUnit, density: Density, start: Int, end: Int) {
    when (fontSize.type) {
        TextUnitType.Sp -> with(density) {
            setSpan(
                AbsoluteSizeSpan(fontSize.toPx().roundToInt(), true),
                start,
                end
            )
        }
        TextUnitType.Em -> {
            setSpan(RelativeSizeSpan(fontSize.value), start, end)
        }
        TextUnitType.Unspecified -> {
        } // Do nothing
    }
}

internal fun Spannable.setTextDecoration(textDecoration: TextDecoration?, start: Int, end: Int) {
    textDecoration?.let {
        if (TextDecoration.Underline in it) {
            setSpan(UnderlineSpan(), start, end)
        }
        if (TextDecoration.LineThrough in it) {
            setSpan(StrikethroughSpan(), start, end)
        }
    }
}

internal fun Spannable.setColor(color: Color, start: Int, end: Int) {
    if (color.isSpecified) {
        setSpan(ForegroundColorSpan(color.toArgb()), start, end)
    }
}

@OptIn(InternalPlatformTextApi::class)
private fun Spannable.setBaselineShift(baselineShift: BaselineShift?, start: Int, end: Int) {
    baselineShift?.let {
        setSpan(BaselineShiftSpan(it.multiplier), start, end)
    }
}

private fun createTypeface(
    fontFamily: FontFamily?,
    weight: Int,
    isItalic: Boolean,
    fontSynthesis: FontSynthesis?,
    typefaceAdapter: TypefaceAdapter
): Typeface {
    val fontWeight = FontWeight(weight)
    val fontStyle = if (isItalic) FontStyle.Italic else FontStyle.Normal

    return typefaceAdapter.create(
        fontFamily = fontFamily,
        fontWeight = fontWeight,
        fontStyle = fontStyle,
        fontSynthesis = fontSynthesis ?: FontSynthesis.All
    )
}

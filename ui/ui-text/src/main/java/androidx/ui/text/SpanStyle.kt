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

import androidx.compose.Immutable
import androidx.ui.core.TextUnit
import androidx.ui.core.lerpTextUnit
import androidx.ui.graphics.Color
import androidx.ui.graphics.Shadow
import androidx.ui.graphics.lerp
import androidx.ui.text.font.FontFamily
import androidx.ui.text.font.FontStyle
import androidx.ui.text.font.FontSynthesis
import androidx.ui.text.font.FontWeight
import androidx.ui.text.font.lerp
import androidx.ui.text.style.BaselineShift
import androidx.ui.text.style.TextDecoration
import androidx.ui.text.style.TextGeometricTransform
import androidx.ui.text.style.lerp

/**
 * Styling configuration for a text span. This configuration only allows character level styling,
 * in order to set paragraph level styling such as line height, or text alignment please see
 * [ParagraphStyle].
 *
 * @sample androidx.ui.text.samples.SpanStyleSample
 *
 * @sample androidx.ui.text.samples.AnnotatedStringBuilderSample
 *
 * @param color The text color.
 * @param fontSize The size of glyphs (in logical pixels) to use when painting the text. This
 * may be [TextUnit.Inherit] for inheriting from another [SpanStyle].
 * @param fontWeight The typeface thickness to use when painting the text (e.g., bold).
 * @param fontStyle The typeface variant to use when drawing the letters (e.g., italic).
 * @param fontSynthesis Whether to synthesize font weight and/or style when the requested weight or
 *  style cannot be found in the provided custom font family.
 * @param fontFamily The font family to be used when rendering the text.
 * @param fontFeatureSettings The advanced typography settings provided by font. The format is the
 *  same as the CSS font-feature-settings attribute:
 *  https://www.w3.org/TR/css-fonts-3/#font-feature-settings-prop
 * @param letterSpacing The amount of space (in em) to add between each letter.
 * @param baselineShift The amount by which the text is shifted up from the current baseline.
 * @param textGeometricTransform The geometric transformation applied the text.
 * @param localeList The locale list used to select region-specific glyphs.
 * @param background The background color for the text.
 * @param decoration The decorations to paint near the text (e.g., an underline).
 * @param shadow The shadow effect applied on the text.
 *
 * @see [AnnotatedString]
 * @see [TextStyle]
 * @see [ParagraphStyle]
 */
@Immutable
data class SpanStyle(
    val color: Color? = null,
    val fontSize: TextUnit = TextUnit.Inherit,
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val fontSynthesis: FontSynthesis? = null,
    val fontFamily: FontFamily? = null,
    val fontFeatureSettings: String? = null,
    val letterSpacing: TextUnit = TextUnit.Inherit,
    val baselineShift: BaselineShift? = null,
    val textGeometricTransform: TextGeometricTransform? = null,
    val localeList: LocaleList? = null,
    val background: Color? = null,
    val decoration: TextDecoration? = null,
    val shadow: Shadow? = null
) {
    /**
     * Returns a new span style that is a combination of this style and the given [other] style.
     *
     * [other] span style's null or inherit properties are replaced with the non-null properties of
     * this span style. Another way to think of it is that the "missing" properties of the [other]
     * style are _filled_ by the properties of this style.
     *
     * If the given span style is null, returns this span style.
     */
    fun merge(other: SpanStyle? = null): SpanStyle {
        if (other == null) return this

        return SpanStyle(
            color = other.color ?: this.color,
            fontFamily = other.fontFamily ?: this.fontFamily,
            fontSize = if (!other.fontSize.isInherit) other.fontSize else this.fontSize,
            fontWeight = other.fontWeight ?: this.fontWeight,
            fontStyle = other.fontStyle ?: this.fontStyle,
            fontSynthesis = other.fontSynthesis ?: this.fontSynthesis,
            fontFeatureSettings = other.fontFeatureSettings ?: this.fontFeatureSettings,
            letterSpacing = if (!other.letterSpacing.isInherit) {
                other.letterSpacing
            } else {
                this.letterSpacing
            },
            baselineShift = other.baselineShift ?: this.baselineShift,
            textGeometricTransform = other.textGeometricTransform ?: this.textGeometricTransform,
            localeList = other.localeList ?: this.localeList,
            background = other.background ?: this.background,
            decoration = other.decoration ?: this.decoration,
            shadow = other.shadow ?: this.shadow
        )
    }
}
/**
 * @param a An sp value. Maybe [TextUnit.Inherit]
 * @param b An sp value. Maybe [TextUnit.Inherit]
 */
private fun lerpTextUnitInheritable(a: TextUnit, b: TextUnit, t: Float): TextUnit {
    if (a.isInherit && b.isInherit) return a
    return lerpTextUnit(a, b, t)
}

private fun <T> lerpDiscrete(a: T, b: T, t: Float): T = if (t < 0.5) a else b

/**
 * Interpolate between two span styles.
 *
 * This will not work well if the styles don't set the same fields.
 *
 * The [fraction] argument represents position on the timeline, with 0.0 meaning
 * that the interpolation has not started, returning [start] (or something
 * equivalent to [start]), 1.0 meaning that the interpolation has finished,
 * returning [stop] (or something equivalent to [stop]), and values in between
 * meaning that the interpolation is at the relevant point on the timeline
 * between [start] and [stop]. The interpolation can be extrapolated beyond 0.0 and
 * 1.0, so negative values and values greater than 1.0 are valid.
 */
fun lerp(start: SpanStyle, stop: SpanStyle, fraction: Float): SpanStyle {
    return SpanStyle(
        color = lerp(start.color ?: Color.Black, stop.color ?: Color.Black, fraction),
        fontFamily = lerpDiscrete(
            start.fontFamily,
            stop.fontFamily,
            fraction
        ),
        fontSize = lerpTextUnitInheritable(start.fontSize, stop.fontSize, fraction),
        fontWeight = lerp(
            start.fontWeight ?: FontWeight.Normal,
            stop.fontWeight ?: FontWeight.Normal,
            fraction
        ),
        fontStyle = lerpDiscrete(
            start.fontStyle,
            stop.fontStyle,
            fraction
        ),
        fontSynthesis = lerpDiscrete(
            start.fontSynthesis,
            stop.fontSynthesis,
            fraction
        ),
        fontFeatureSettings = lerpDiscrete(
            start.fontFeatureSettings,
            stop.fontFeatureSettings,
            fraction
        ),
        letterSpacing = lerpTextUnitInheritable(
            start.letterSpacing,
            stop.letterSpacing,
            fraction
        ),
        baselineShift = lerp(
            start.baselineShift ?: BaselineShift(0f),
            stop.baselineShift ?: BaselineShift(0f),
            fraction
        ),
        textGeometricTransform = lerp(
            start.textGeometricTransform ?: TextGeometricTransform.None,
            stop.textGeometricTransform ?: TextGeometricTransform.None,
            fraction
        ),
        localeList = lerpDiscrete(start.localeList, stop.localeList, fraction),
        background = lerp(
            start.background ?: Color.Transparent,
            stop.background ?: Color.Transparent,
            fraction
        ),
        decoration = lerpDiscrete(
            start.decoration,
            stop.decoration,
            fraction
        ),
        shadow = lerp(
            start.shadow ?: Shadow(),
            stop.shadow ?: Shadow(),
            fraction
        )
    )
}
/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.wear.compose.foundation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp

/** The default values to use if they are not specified. */
internal val DefaultCurvedTextStyles = CurvedTextStyle(
    color = Color.Black,
    fontSize = 14.sp,
    background = Color.Transparent
).also {
    it.fontWeight = FontWeight.Normal
}

/**
 * Styling configuration for a curved text.
 *
 * @sample androidx.wear.compose.foundation.samples.CurvedAndNormalText
 *
 * @param background The background color for the text.
 * @param color The text color.
 * @param fontSize The size of glyphs (in logical pixels) to use when painting the text. This
 * may be [TextUnit.Unspecified] for inheriting from another [CurvedTextStyle].
 *
 */
class CurvedTextStyle(
    val background: Color = Color.Unspecified,
    val color: Color = Color.Unspecified,
    val fontSize: TextUnit = TextUnit.Unspecified,
) {
    // Temporary added as internal field until we can add to the public API in 1.1
    internal var fontWeight: FontWeight? = null

    /**
     * Create a curved text style from the given text style.
     *
     * Note that not all parameters in the text style will be used, only [TextStyle.color],
     * [TextStyle.fontSize], [TextStyle.background] and [TextStyle.fontWeight]
     */
    constructor(style: TextStyle) : this(style.background, style.color, style.fontSize) {
        fontWeight = style.fontWeight
    }

    /**
     * Returns a new curved text style that is a combination of this style and the given
     * [other] style.
     *
     * [other] curved text style's null or inherit properties are replaced with the non-null
     * properties of this curved text style. Another way to think of it is that the "missing"
     * properties of the [other] style are _filled_ by the properties of this style.
     *
     * If the given curved text style is null, returns this curved text style.
     */
    fun merge(other: CurvedTextStyle? = null): CurvedTextStyle {
        if (other == null) return this

        return CurvedTextStyle(
            color = other.color.takeOrElse { this.color },
            fontSize = if (!other.fontSize.isUnspecified) other.fontSize else this.fontSize,
            background = other.background.takeOrElse { this.background },
        ).also {
            it.fontWeight = other.fontWeight ?: this.fontWeight
        }
    }

    /**
     * Plus operator overload that applies a [merge].
     */
    operator fun plus(other: CurvedTextStyle): CurvedTextStyle = this.merge(other)

    fun copy(
        background: Color = this.background,
        color: Color = this.color,
        fontSize: TextUnit = this.fontSize,
    ): CurvedTextStyle {
        return CurvedTextStyle(
            background = background,
            color = color,
            fontSize = fontSize,
        ).also {
            it.fontWeight = this.fontWeight
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true

        return other is CurvedTextStyle &&
            color == other.color &&
            fontSize == other.fontSize &&
            background == other.background &&
            fontWeight == other.fontWeight
    }

    override fun hashCode(): Int {
        var result = color.hashCode()
        result = 31 * result + fontSize.hashCode()
        result = 31 * result + background.hashCode()
        result = 31 * result + fontWeight.hashCode()
        return result
    }

    override fun toString(): String {
        return "CurvedTextStyle(" +
            "background=$background" +
            "color=$color, " +
            "fontSize=$fontSize, " +
            "fontWeight=$fontWeight, " +
            ")"
    }
}

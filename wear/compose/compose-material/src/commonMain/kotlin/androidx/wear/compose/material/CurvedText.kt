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

package androidx.wear.compose.material

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedScope
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.basicCurvedText
import androidx.wear.compose.foundation.curvedRow

/**
 * CurvedText is a component allowing developers to easily write curved text following
 * the curvature a circle (usually at the edge of a circular screen).
 * CurvedText can be only created within the CurvedLayout to ensure the best experience, like being
 * able to specify to positioning.
 *
 * The default [style] uses the [LocalTextStyle] provided by the [MaterialTheme] / components,
 * converting it to a [CurvedTextStyle]. Note that not all parameters are used by [curvedText].
 *
 * If you are setting your own style, you may want to consider first retrieving [LocalTextStyle],
 * and using [TextStyle.copy] to keep any theme defined attributes, only modifying the specific
 * attributes you want to override, then convert to [CurvedTextStyle]
 *
 * For ease of use, commonly used parameters from [CurvedTextStyle] are also present here. The
 * order of precedence is as follows:
 * - If a parameter is explicitly set here (i.e, it is _not_ `null` or [TextUnit.Unspecified]),
 * then this parameter will always be used.
 * - If a parameter is _not_ set, (`null` or [TextUnit.Unspecified]), then the corresponding value
 * from [style] will be used instead.
 *
 * Additionally, for [color], if [color] is not set, and [style] does not have a color, then
 * [LocalContentColor] will be used with an alpha of [LocalContentAlpha]- this allows this
 * [curvedText] or element containing this [curvedText] to adapt to different background colors and
 * still maintain contrast and accessibility.
 *
 * @sample androidx.wear.compose.material.samples.CurvedTextDemo
 *
 * For more information, see the
 * [Curved Text](https://developer.android.com/training/wearables/compose/curved-text)
 * guide.
 *
 * @param text The text to display
 * @param modifier The [CurvedModifier] to apply to this curved text.
 * @param background The background color for the text.
 * @param color [Color] to apply to the text. If [Color.Unspecified], and [style] has no color set,
 * this will be [LocalContentColor].
 * @param fontSize The size of glyphs to use when painting the text. See [TextStyle.fontSize].
 * @param style Specified the style to use.
 * @param angularDirection Specify if the text is laid out clockwise or anti-clockwise, and if
 * those needs to be reversed in a Rtl layout.
 * If not specified, it will be inherited from the enclosing [curvedRow] or [CurvedLayout]
 * See [CurvedDirection.Angular].
 * @param overflow How visual overflow should be handled.
 */
public fun CurvedScope.curvedText(
    text: String,
    modifier: CurvedModifier = CurvedModifier,
    background: Color = Color.Unspecified,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    style: CurvedTextStyle? = null,
    angularDirection: CurvedDirection.Angular? = null,
    overflow: TextOverflow = TextOverflow.Clip,
) = basicCurvedText(text, modifier, angularDirection, overflow) {
    val baseStyle = style ?: CurvedTextStyle(LocalTextStyle.current)
    val textColor = color.takeOrElse {
        baseStyle.color.takeOrElse {
            LocalContentColor.current.copy(alpha = LocalContentAlpha.current)
        }
    }
    baseStyle.merge(
        CurvedTextStyle(
            color = textColor,
            fontSize = fontSize,
            background = background
        )
    )
}
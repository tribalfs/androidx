/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.ui.engine.text

import androidx.ui.engine.text.font.FontFamily
import androidx.ui.engine.window.Locale
import androidx.ui.painting.Color
import androidx.ui.painting.Paint

/**
 * An opaque object that determines the size, position, and rendering of text.
 *
 * Creates a new TextStyle object.
 *
 * * `color`: The color to use when painting the text. If this is specified, `foreground` must be
 *             null.
 * * `decoration`: The decorations to paint near the text (e.g., an underline).
 * * `decorationColor`: The color in which to paint the text decorations.
 * * `fontWeight`: The typeface thickness to use when painting the text (e.g., bold).
 * * `fontStyle`: The typeface variant to use when drawing the letters (e.g., italics).
 * * `fontFamily`: The name of the font to use when painting the text (e.g., Roboto).
 * * `fontSize`: The size of glyphs (in logical pixels) to use when painting the text.
 * * `letterSpacing`: The amount of space (in EM) to add between each letter.
 * * `wordSpacing`: The amount of space (in logical pixels) to add at each sequence of white-space
 *                  (i.e. between each word).
 * * `textBaseline`: The common baseline that should be aligned between this text span and its
 *                   parent text span, or, for the root text spans, with the line box.
 * * `height`: The height of this text span, as a multiple of the font size.
 * * `locale`: The locale used to select region-specific glyphs.
 * * `background`: The background color for the text.
 * * `foreground`: The paint used to draw the text. If this is specified, `color` must be null.
 * * `fontSynthesis`: Whether to synthesize font weight and/or style when the requested weight or
 *                    style cannot be found in the provided custom font family.
 */
data class TextStyle constructor(
    val color: Color? = null,
    val decoration: TextDecoration? = null,
    val decorationColor: Color? = null,
    val fontWeight: FontWeight? = null,
    val fontStyle: FontStyle? = null,
    val textBaseline: TextBaseline? = null,
    val fontFamily: FontFamily? = null,
    val fontSize: Float? = null,
    val letterSpacing: Float? = null,
    val wordSpacing: Float? = null,
    val height: Float? = null,
    val locale: Locale? = null,
    // TODO(Migration/haoyuchang): background is changed to color from paint.
    val background: Color? = null,
    val foreground: Paint? = null,
    val fontSynthesis: FontSynthesis? = null
) {
    init {
        assert(color == null || foreground == null) {
            "Cannot provide both a color and a foreground\n" +
                "The color argument is just a shorthand for " +
                "'foreground: new Paint()..color = color'."
        }
    }

    override fun toString(): String {
        return "TextStyle(" +
        "color: ${color ?: "unspecified"}, " +
        "decoration: ${decoration ?: "unspecified"}, " +
        "decorationColor: ${decorationColor ?: "unspecified"}, " +
        "fontWeight: ${fontWeight ?: "unspecified"}, " +
        "fontStyle: ${fontStyle ?: "unspecified"}, " +
        "textBaseline: ${textBaseline ?: "unspecified"}, " +
        "fontFamily: ${fontFamily ?: "unspecified"}, " +
        "fontSize: ${fontSize ?: "unspecified"}, " +
        "letterSpacing: ${if (letterSpacing != null) "${letterSpacing}x" else "unspecified"}, " +
        "wordSpacing: ${if (wordSpacing != null) "${wordSpacing}x" else "unspecified"}, " +
        "height: ${if (height != null) "${height}x" else "unspecified"}, " +
        "locale: ${locale ?: "unspecified"}, " +
        "background: ${background ?: "unspecified"}, " +
        "foreground: ${foreground ?: "unspecified"}, " +
        "fontSynthesis: ${fontSynthesis ?: "unspecified"}" +
        ")"
    }
}

// TODO(Migration/siyamed) Remove, Native defaults
// class TextStyle {
//     public:
//     SkColor color = SK_ColorWHITE;
//     int decoration = TextDecoration::kNone;
//     // Does not make sense to draw a transparent object, so we use it as a default
//     // value to indicate no decoration color was set.
//     SkColor decoration_color = SK_ColorTRANSPARENT;
//     TextDecorationStyle decoration_style = TextDecorationStyle::kSolid;
//     // Thickness is applied as a multiplier to the default thickness of the font.
//     double decoration_thickness_multiplier = 1.0;
//     FontWeight font_weight = FontWeight::w400;
//     FontStyle font_style = FontStyle::normal;
//     TextBaseline text_baseline = TextBaseline::kAlphabetic;
//     std::string font_family;
//     double font_size = 14.0;
//     double letter_spacing = 0.0;
//     double word_spacing = 0.0;
//     double height = 1.0;
//     std::string locale;
//     bool has_background = false;
//     SkPaint background;
//     bool has_foreground = false;
//     SkPaint foreground;
//
//     TextStyle();
//
//     bool equals(const TextStyle& other) const;
// };

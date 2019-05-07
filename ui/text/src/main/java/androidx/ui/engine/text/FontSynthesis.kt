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
package androidx.ui.engine.text

/**
 *  Whether to synthesize custom fonts. Can be used with custom font families. Work the same way as
 *  [CSS font-synthesis](https://www.w3.org/TR/css-fonts-4/#font-synthesis) property. For example,
 *  When weight synthesis is turned on using [FontSynthesis.weight], and if custom font family does
 *  not include a requested weight, the system will try to fake bold the given font. It is possible
 *  to make a regular font fake italic, but not vice versa. Similarly it is possible to fake bold
 *  a font during rendering, but not fake thinner.
 **/
enum class FontSynthesis {
    /**
     * Turns off font synthesis.
     */
    none,

    /**
     * Synthesize weight
     */
    weight,

    /**
     * Synthesize style
     */
    style,

    /**
     * Synthesize weight and style
     */
    all;

    internal val isWeightOn: Boolean
        get() = this == all || this == weight

    internal val isStyleOn: Boolean
        get() = this == all || this == style
}
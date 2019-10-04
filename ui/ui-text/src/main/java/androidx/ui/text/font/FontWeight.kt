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
package androidx.ui.text.font

import androidx.ui.lerp

/**
 * The thickness of the glyphs used to draw the text.
 */
/* inline */ data class FontWeight private constructor(
    /**
     * Can be in the range of [1,1000]
     */
    internal val weight: Int
) : Comparable<FontWeight> {

    // TODO(Migration/siyamed): This is weird since it should actually be possible to create a font
    // weight that is not one of the items here. This decision changes the lerp behavior.
    companion object {
        // Thin, the least thick
        val w100 = FontWeight(100)
        // Extra-light
        val w200 = FontWeight(200)
        // Light
        val w300 = FontWeight(300)
        // Normal / regular / plain
        val w400 = FontWeight(400)
        // Medium
        val w500 = FontWeight(500)
        // Semi-bold
        val w600 = FontWeight(600)
        // Bold
        val w700 = FontWeight(700)
        // Extra-bold
        val w800 = FontWeight(800)
        // Black, the most thick
        val w900 = FontWeight(900)
        // The default font weight.
        val normal = w400
        // A commonly used font weight that is heavier than normal.
        val bold = w700

        // A list of all the font weights.
        val values: List<FontWeight> = listOf(
            w100,
            w200,
            w300,
            w400,
            w500,
            w600,
            w700,
            w800,
            w900
        )

        /**
         * Linearly interpolate between two font weights
         *
         * Rather than using fractional weights, the interpolation rounds to the
         * nearest weight.
         *
         * The [fraction] argument represents position on the timeline, with 0.0 meaning
         * that the interpolation has not started, returning [start] (or something
         * equivalent to [start]), 1.0 meaning that the interpolation has finished,
         * returning [stop] (or something equivalent to [stop]), and values in between
         * meaning that the interpolation is at the relevant point on the timeline
         * between [start] and [stop]. The interpolation can be extrapolated beyond 0.0 and
         * 1.0, so negative values and values greater than 1.0 are valid (and can
         * easily be generated by curves).
         *
         * Values for [fraction] are usually obtained from an [Animation<Float>], such as
         * an `AnimationController`.
         */
        // TODO(siyamed): These should not accept nullable arguments
        // TODO(siyamed): This should be in the file, not a Companion function
        fun lerp(start: FontWeight?, stop: FontWeight?, fraction: Float): FontWeight {
            return values[lerp(
                start?.index ?: normal.index,
                stop?.index ?: normal.index,
                fraction
            ).coerceIn(0, 8)]
        }
    }

    private val index: Int get() = weight / 100 - 1

    override fun compareTo(other: FontWeight): Int {
        return weight.compareTo(other.weight)
    }

    override fun toString(): String {
        return when (index) {
            0 -> "FontWeight.w100"
            1 -> "FontWeight.w200"
            2 -> "FontWeight.w300"
            3 -> "FontWeight.w400"
            4 -> "FontWeight.w500"
            5 -> "FontWeight.w600"
            6 -> "FontWeight.w700"
            7 -> "FontWeight.w800"
            8 -> "FontWeight.w900"
            else -> "FontWeight.unknown"
        }
    }
}

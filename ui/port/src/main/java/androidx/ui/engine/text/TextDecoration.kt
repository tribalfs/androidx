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

/** A linear decoration to draw near the text. */
data class TextDecoration internal constructor(val mask: Int) {

    companion object {
        val none: TextDecoration = TextDecoration(0x0)

        /** Draw a line underneath each line of text */
        val underline: TextDecoration = TextDecoration(0x1)

        /** Draw a line through each line of text */
        val lineThrough: TextDecoration = TextDecoration(0x2)

        /** Creates a decoration that paints the union of all the given decorations. */
        fun combine(decorations: List<TextDecoration>): TextDecoration {
            val mask = decorations.fold(0) { acc, decoration ->
                acc or decoration.mask
            }
            return TextDecoration(mask)
        }
    }

    /** Whether this decoration will paint at least as much decoration as the given decoration. */
    fun contains(other: TextDecoration): Boolean {
        return (mask or other.mask) == mask
    }

    override fun toString(): String {
        if (mask == 0) {
            return "TextDecoration.none"
        }

        var values: MutableList<String> = mutableListOf()
        if (!((mask and TextDecoration.underline.mask) == 0)) {
            values.add("underline")
        }
        if (!((mask and TextDecoration.lineThrough.mask) == 0)) {
            values.add("lineThrough")
        }
        if ((values.size == 1)) {
            return "TextDecoration.${values.get(0)}"
        }
        return "TextDecoration.combine([${values.joinToString(separator = ", ")}])"
    }
}

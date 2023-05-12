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

package androidx.glance.layout

/**
 * A class used to specify the position of a sized box inside an available space. This is often used
 * to specify how a parent layout should place its children.
 */

class Alignment(val horizontal: Horizontal, val vertical: Vertical) {
    /**
     * Specifies how a parent should lay its children out horizontally, if the child has a width
     * smaller than the parent.
     */
    @JvmInline
    value class Horizontal private constructor(private val value: Int) {
        companion object {
            val Start: Horizontal = Horizontal(0)
            val CenterHorizontally: Horizontal = Horizontal(1)
            val End: Horizontal = Horizontal(2)
        }
    }

    /**
     * Specifies how a parent should lay its children out vertically, if the child has a height
     * smaller than the parent.
     */
    @JvmInline
    value class Vertical private constructor(private val value: Int) {
        companion object {
            val Top: Vertical = Vertical(0)
            val CenterVertically: Vertical = Vertical(1)
            val Bottom: Vertical = Vertical(2)
        }
    }

    /** Common [Alignment] options used in layouts. */
    companion object {
        val TopStart: Alignment = Alignment(Horizontal.Start, Vertical.Top)
        val TopCenter: Alignment = Alignment(Horizontal.CenterHorizontally, Vertical.Top)
        val TopEnd: Alignment = Alignment(Horizontal.End, Vertical.Top)

        val CenterStart: Alignment = Alignment(Horizontal.Start, Vertical.CenterVertically)
        val Center: Alignment =
            Alignment(Horizontal.CenterHorizontally, Vertical.CenterVertically)
        val CenterEnd: Alignment = Alignment(Horizontal.End, Vertical.CenterVertically)

        val BottomStart: Alignment = Alignment(Horizontal.Start, Vertical.Bottom)
        val BottomCenter: Alignment =
            Alignment(Horizontal.CenterHorizontally, Vertical.Bottom)
        val BottomEnd: Alignment = Alignment(Horizontal.End, Vertical.Bottom)

        val Top: Vertical = Vertical.Top
        val CenterVertically: Vertical = Vertical.CenterVertically
        val Bottom: Vertical = Vertical.Bottom

        val Start: Horizontal = Horizontal.Start
        val CenterHorizontally: Horizontal = Horizontal.CenterHorizontally
        val End: Horizontal = Horizontal.End
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Alignment

        if (horizontal != other.horizontal) return false
        if (vertical != other.vertical) return false

        return true
    }

    override fun hashCode(): Int {
        var result = horizontal.hashCode()
        result = 31 * result + vertical.hashCode()
        return result
    }

    override fun toString(): String {
        return "Alignment(horizontal=$horizontal, vertical=$vertical)"
    }
}

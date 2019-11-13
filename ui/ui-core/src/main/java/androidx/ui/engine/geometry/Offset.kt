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

package androidx.ui.engine.geometry

import androidx.ui.core.PxPosition
import androidx.ui.core.px
import androidx.ui.lerp
import androidx.ui.toStringAsFixed
import kotlin.math.atan2
import kotlin.math.sqrt
import kotlin.math.truncate

/**
 * An immutable 2D floating-point offset.
 *
 * Generally speaking, Offsets can be interpreted in two ways:
 *
 * 1. As representing a point in Cartesian space a specified distance from a
 *    separately-maintained origin. For example, the top-left position of
 *    children in the [RenderBox] protocol is typically represented as an
 *    [Offset] from the top left of the parent box.
 *
 * 2. As a vector that can be applied to coordinates. For example, when
 *    painting a [RenderObject], the parent is passed an [Offset] from the
 *    screen's origin which it can add to the offsets of its children to find
 *    the [Offset] from the screen's origin to each of the children.
 *
 * Because a particular [Offset] can be interpreted as one sense at one time
 * then as the other sense at a later time, the same class is used for both
 * senses.
 *
 * See also:
 *
 *  * [Size], which represents a vector describing the size of a rectangle.
 *
 * Creates an offset. The first argument sets [dx], the horizontal component,
 * and the second sets [dy], the vertical component.
 */
data class Offset(override val dx: Float, override val dy: Float) : OffsetBase {

    companion object {
        /**
         * An offset with zero magnitude.
         *
         * This can be used to represent the origin of a coordinate space.
         */
        val zero = Offset(0.0f, 0.0f)

        /**
         * An offset with infinite x and y components.
         *
         * See also:
         *
         *  * [isInfinite], which checks whether either component is infinite.
         *  * [isFinite], which checks whether both components are finite.
         */
        // This is included for completeness, because [Size.infinite] exists.
        val infinite = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)

        /**
         * Linearly interpolate between two offsets.
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
        fun lerp(start: Offset, stop: Offset, fraction: Float): Offset {
            return Offset(lerp(start.dx, stop.dx, fraction), lerp(start.dy, stop.dy, fraction))
        }

        fun isValid(offset: Offset): Boolean {
            assert(!offset.dx.isNaN() && !offset.dy.isNaN()) {
                "Offset argument contained a NaN value."
            }
            return true
        }
    }

    /**
     * The magnitude of the offset.
     *
     * If you need this value to compare it to another [Offset]'s distance,
     * consider using [getDistanceSquared] instead, since it is cheaper to compute.
     */
    fun getDistance() = sqrt(dx * dx + dy * dy)

    /**
     * The square of the magnitude of the offset.
     *
     * This is cheaper than computing the [getDistance] itself.
     */
    fun getDistanceSquared() = dx * dx + dy * dy

    /**
     * The angle of this offset as radians clockwise from the positive x-axis, in
     * the range -[pi] to [pi], assuming positive values of the x-axis go to the
     * left and positive values of the y-axis go down.
     *
     * Zero means that [dy] is zero and [dx] is zero or positive.
     *
     * Values from zero to [pi]/2 indicate positive values of [dx] and [dy], the
     * bottom-right quadrant.
     *
     * Values from [pi]/2 to [pi] indicate negative values of [dx] and positive
     * values of [dy], the bottom-left quadrant.
     *
     * Values from zero to -[pi]/2 indicate positive values of [dx] and negative
     * values of [dy], the top-right quadrant.
     *
     * Values from -[pi]/2 to -[pi] indicate negative values of [dx] and [dy],
     * the top-left quadrant.
     *
     * When [dy] is zero and [dx] is negative, the [direction] is [pi].
     *
     * When [dx] is zero, [direction] is [pi]/2 if [dy] is positive and -[pi]/2
     * if [dy] is negative.
     *
     * See also:
     *
     *  * [distance], to compute the magnitude of the vector.
     *  * [Canvas.rotate], which uses the same convention for its angle.
     */
    fun getDirection() = atan2(dy, dx)

    /**
     * Returns a new offset with the x component scaled by `scaleX` and the y
     * component scaled by `scaleY`.
     *
     * If the two scale arguments are the same, consider using the `*` operator
     * instead:
     *
     * ```dart
     * Offset a = const Offset(10.0, 10.0);
     * Offset b = a * 2.0; // same as: a.scale(2.0, 2.0)
     * ```
     *
     * If the two arguments are -1, consider using the unary `-` operator
     * instead:
     *
     * ```dart
     * Offset a = const Offset(10.0, 10.0);
     * Offset b = -a; // same as: a.scale(-1.0, -1.0)
     * ```
     */
    fun scale(scaleX: Float, scaleY: Float): Offset = Offset(dx * scaleX, dy * scaleY)

    /**
     * Returns a new offset with translateX added to the x component and
     * translateY added to the y component.
     *
     * If the arguments come from another [Offset], consider using the `+` or `-`
     * operators instead:
     *
     * ```dart
     * Offset a = const Offset(10.0, 10.0);
     * Offset b = const Offset(10.0, 10.0);
     * Offset c = a + b; // same as: a.translate(b.dx, b.dy)
     * Offset d = a - b; // same as: a.translate(-b.dx, -b.dy)
     * ```
     */
    fun translate(translateX: Float, translateY: Float): Offset =
        Offset(dx + translateX, dy + translateY)

    /**
     * Unary negation operator.
     *
     * Returns an offset with the coordinates negated.
     *
     * If the [Offset] represents an arrow on a plane, this operator returns the
     * same arrow but pointing in the reverse direction.
     */
    operator fun unaryMinus(): Offset = Offset(-dx, -dy)

    /**
     * Binary subtraction operator.
     *
     * Returns an offset whose [dx] value is the left-hand-side operand's [dx]
     * minus the right-hand-side operand's [dx] and whose [dy] value is the
     * left-hand-side operand's [dy] minus the right-hand-side operand's [dy].
     *
     * See also [translate].
     */
    operator fun minus(other: Offset): Offset = Offset(dx - other.dx, dy - other.dy)

    /**
     * Binary addition operator.
     *
     * Returns an offset whose [dx] value is the sum of the [dx] values of the
     * two operands, and whose [dy] value is the sum of the [dy] values of the
     * two operands.
     *
     * See also [translate].
     */
    operator fun plus(other: Offset): Offset = Offset(dx + other.dx, dy + other.dy)

    /**
     * Multiplication operator.
     *
     * Returns an offset whose coordinates are the coordinates of the
     * left-hand-side operand (an Offset) multiplied by the scalar
     * right-hand-side operand (a Float).
     *
     * See also [scale].
     */
    operator fun times(operand: Float): Offset = Offset(dx * operand, dy * operand)

    /**
     * Division operator.
     *
     * Returns an offset whose coordinates are the coordinates of the
     * left-hand-side operand (an Offset) divided by the scalar right-hand-side
     * operand (a Float).
     *
     * See also [scale].
     */
    operator fun div(operand: Float): Offset = Offset(dx / operand, dy / operand)

    /**
     * Integer (truncating) division operator.
     *
     * Returns an offset whose coordinates are the coordinates of the
     * left-hand-side operand (an Offset) divided by the scalar right-hand-side
     * operand (a Float), rounded towards zero.
     */
    // TODO(Filip): Original operator ~/ could not be overriden in Kotlin
    fun truncDiv(operand: Float) =
        Offset(truncate(dx / operand), truncate(dy / operand))

    /**
     * Modulo (remainder) operator.
     *
     * Returns an offset whose coordinates are the remainder of dividing the
     * coordinates of the left-hand-side operand (an Offset) by the scalar
     * right-hand-side operand (a Float).
     */
    operator fun rem(operand: Float) = Offset(dx % operand, dy % operand)

    /**
     * Rectangle constructor operator.
     *
     * Combines an [Offset] and a [Size] to form a [Rect] whose top-left
     * coordinate is the point given by adding this offset, the left-hand-side
     * operand, to the origin, and whose size is the right-hand-side operand.
     *
     * ```dart
     * Rect myRect = Offset.zero & const Size(100.0, 100.0);
     * // same as: new Rect.fromLTWH(0.0, 0.0, 100.0, 100.0)
     * ```
     */
    // TODO(Filip): Original operator & could not be overriden in Kotlin
    infix fun and(other: Size): Rect = Rect.fromLTWH(dx, dy, other.width, other.height)

    override fun toString() = "Offset(${dx.toStringAsFixed(1)}, ${dy.toStringAsFixed(1)})"

    // We need to manually override equals (and thus also hashCode) because the auto generated
    // equals was treating Offset(0.0, 0.0) != Offset(0.0, -0.0).
    // Filed as https://youtrack.jetbrains.com/issue/KT-27343

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Offset) return false

        if (dx != other.dx) return false
        if (dy != other.dy) return false

        return true
    }

    override fun hashCode(): Int {
        var result = dx.hashCode()
        result = 31 * result + dy.hashCode()
        return result
    }
}

fun Offset.toPxPosition() = PxPosition(dx.px, dy.px)

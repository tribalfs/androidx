package androidx.ui.engine.geometry

import androidx.ui.lerpFloat
import androidx.ui.toStringAsFixed
import androidx.ui.truncDiv
import kotlin.math.absoluteValue

/**
 * Holds a 2D floating-point size.
 *
 * You can think of this as an [Offset] from the origin.
 */
open class Size(val width: Float, val height: Float) : OffsetBase {

    override val dx: Float = width
    override val dy: Float = height

    companion object {
        /**
         * Creates an instance of [Size] that has the same values as another.
         */
        // Used by the rendering library's _DebugSize hack.
        fun copy(source: Size): Size {
            return Size(source.width, source.height)
        }

        /**
         * Creates a square [Size] whose [width] and [height] are the given dimension.
         *
         * See also:
         *
         *  * [new Size.fromRadius], which is more convenient when the available size
         *    is the radius of a circle.
         */
        fun square(dimension: Float): Size {
            return Size(dimension, dimension)
        }

        /**
         * Creates a [Size] with the given [width] and an infinite [height].
         */
        fun fromWidth(width: Float): Size {
            return Size(width, Float.POSITIVE_INFINITY)
        }

        /**
         * Creates a [Size] with the given [height] and an infinite [width].
         */
        fun fromHeight(height: Float): Size {
            return Size(Float.POSITIVE_INFINITY, height)
        }

        /**
         * Creates a square [Size] whose [width] and [height] are twice the given
         * dimension.
         *
         * This is a square that contains a circle with the given radius.
         *
         * See also:
         *
         *  * [new Size.square], which creates a square with the given dimension.
         */
        fun fromRadius(radius: Float): Size {
            return Size(radius * 2.0f, radius * 2.0f)
        }

        /**
         * An empty size, one with a zero width and a zero height.
         */
        val zero = Size(0.0f, 0.0f)

        /**
         * A size whose [width] and [height] are infinite.
         *
         * See also:
         *
         *  * [isInfinite], which checks whether either dimension is infinite.
         *  * [isFinite], which checks whether both dimensions are finite.
         */
        val infinite = Size(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)

        /**
         * Linearly interpolate between two sizes
         *
         * If either size is null, this function interpolates from [Size.zero].
         *
         * The `t` argument represents position on the timeline, with 0.0 meaning
         * that the interpolation has not started, returning `a` (or something
         * equivalent to `a`), 1.0 meaning that the interpolation has finished,
         * returning `b` (or something equivalent to `b`), and values in between
         * meaning that the interpolation is at the relevant point on the timeline
         * between `a` and `b`. The interpolation can be extrapolated beyond 0.0 and
         * 1.0, so negative values and values greater than 1.0 are valid (and can
         * easily be generated by curves such as [Curves.elasticInOut]).
         *
         * Values for `t` are usually obtained from an [Animation<Float>], such as
         * an [AnimationController].
         */
        fun lerp(a: Size, b: Size, t: Float): Size? {
//            if (a == null && b == null)
//                return null
//            if (a == null)
//                return b * t
//            if (b == null)
//                return a * (1.0 - t)
            return Size(lerpFloat(a.width, b.width, t), lerpFloat(a.height, b.height, t))
        }
    }

    /**
     * Whether this size encloses a non-zero area.
     *
     * Negative areas are considered empty.
     */
    fun isEmpty() = width <= 0.0f || height <= 0.0f

    /**
     * Binary subtraction operator for [Size].
     *
     * Subtracting a [Size] from a [Size] returns the [Offset] that describes how
     * much bigger the left-hand-side operand is than the right-hand-side
     * operand. Adding that resulting [Offset] to the [Size] that was the
     * right-hand-side operand would return a [Size] equal to the [Size] that was
     * the left-hand-side operand. (i.e. if `sizeA - sizeB -> offsetA`, then
     * `offsetA + sizeB -> sizeA`)
     *
     * Subtracting an [Offset] from a [Size] returns the [Size] that is smaller than
     * the [Size] operand by the difference given by the [Offset] operand. In other
     * words, the returned [Size] has a [width] consisting of the [width] of the
     * left-hand-side operand minus the [Offset.dx] dimension of the
     * right-hand-side operand, and a [height] consisting of the [height] of the
     * left-hand-side operand minus the [Offset.dy] dimension of the
     * right-hand-side operand.
     */
    operator fun minus(other: Offset): Size {
        return Size(width - other.dx, height - other.dy)
    }

    operator fun minus(other: Size): Offset {
        return Offset(width - other.width, height - other.height)
    }

    /**
     * Binary addition operator for adding an [Offset] to a [Size].
     *
     * Returns a [Size] whose [width] is the sum of the [width] of the
     * left-hand-side operand, a [Size], and the [Offset.dx] dimension of the
     * right-hand-side operand, an [Offset], and whose [height] is the sum of the
     * [height] of the left-hand-side operand and the [Offset.dy] dimension of
     * the right-hand-side operand.
     */
    operator fun plus(other: Offset) = Size(width + other.dx, height + other.dy)

    /**
     * Multiplication operator.
     *
     * Returns a [Size] whose dimensions are the dimensions of the left-hand-side
     * operand (a [Size]) multiplied by the scalar right-hand-side operand (a
     * [Float]).
     */
    operator fun times(operand: Float) = Size(width * operand, height * operand)

    /**
     * Division operator.
     *
     * Returns a [Size] whose dimensions are the dimensions of the left-hand-side
     * operand (a [Size]) divided by the scalar right-hand-side operand (a
     * [Float]).
     */
    operator fun div(operand: Float) = Size(width / operand, height / operand)

    /**
     * Integer (truncating) division operator.
     *
     * Returns a [Size] whose dimensions are the dimensions of the left-hand-side
     * operand (a [Size]) divided by the scalar right-hand-side operand (a
     * [Float]), rounded towards zero.
     */
    fun truncDiv(operand: Float) =
            Size((width.truncDiv(operand)).toFloat(), (height.truncDiv(operand)).toFloat())

    /**
     * Modulo (remainder) operator.
     *
     * Returns a [Size] whose dimensions are the remainder of dividing the
     * left-hand-side operand (a [Size]) by the scalar right-hand-side operand (a
     * [Float]).
     */
    operator fun rem(operand: Float) = Size(width % operand, height % operand)

    /**
     * The lesser of the magnitudes of the [width] and the [height].
     */
    fun getShortestSide(): Float = Math.min(width.absoluteValue, height.absoluteValue)

    /**
     * The greater of the magnitudes of the [width] and the [height].
     */
    fun getLongestSide(): Float = Math.max(width.absoluteValue, height.absoluteValue)

    // Convenience methods that do the equivalent of calling the similarly named
    // methods on a Rect constructed from the given origin and this size.

    /**
     * The offset to the intersection of the top and left edges of the rectangle
     * described by the given [Offset] (which is interpreted as the top-left corner)
     * and this [Size].
     *
     * See also [Rect.topLeft].
     */
    fun topLeft(origin: Offset): Offset = origin

    /**
     * The offset to the center of the top edge of the rectangle described by the
     * given offset (which is interpreted as the top-left corner) and this size.
     *
     * See also [Rect.topCenter].
     */
    fun topCenter(origin: Offset): Offset = Offset(origin.dx + width / 2.0f, origin.dy)

    /**
     * The offset to the intersection of the top and right edges of the rectangle
     * described by the given offset (which is interpreted as the top-left corner)
     * and this size.
     *
     * See also [Rect.topRight].
     */
    fun topRight(origin: Offset): Offset = Offset(origin.dx + width, origin.dy)

    /**
     * The offset to the center of the left edge of the rectangle described by the
     * given offset (which is interpreted as the top-left corner) and this size.
     *
     * See also [Rect.centerLeft].
     */
    fun centerLeft(origin: Offset): Offset = Offset(origin.dx, origin.dy + height / 2.0f)

    /**
     * The offset to the point halfway between the left and right and the top and
     * bottom edges of the rectangle described by the given offset (which is
     * interpreted as the top-left corner) and this size.
     *
     * See also [Rect.center].
     */
    fun center(origin: Offset): Offset = Offset(origin.dx + width / 2.0f, origin.dy + height / 2.0f)

    /**
     * The offset to the center of the right edge of the rectangle described by the
     * given offset (which is interpreted as the top-left corner) and this size.
     *
     * See also [Rect.centerLeft].
     */
    fun centerRight(origin: Offset): Offset = Offset(origin.dx + width, origin.dy + height / 2.0f)

    /**
     * The offset to the intersection of the bottom and left edges of the
     * rectangle described by the given offset (which is interpreted as the
     * top-left corner) and this size.
     *
     * See also [Rect.bottomLeft].
     */
    fun bottomLeft(origin: Offset): Offset = Offset(origin.dx, origin.dy + height)

    /**
     * The offset to the center of the bottom edge of the rectangle described by
     * the given offset (which is interpreted as the top-left corner) and this
     * size.
     *
     * See also [Rect.bottomLeft].
     */
    fun bottomCenter(origin: Offset): Offset = Offset(origin.dx + width / 2.0f, origin.dy + height)

    /**
     * The offset to the intersection of the bottom and right edges of the
     * rectangle described by the given offset (which is interpreted as the
     * top-left corner) and this size.
     *
     * See also [Rect.bottomRight].
     */
    fun bottomRight(origin: Offset): Offset = Offset(origin.dx + width, origin.dy + height)

    /**
     * Whether the point specified by the given offset (which is assumed to be
     * relative to the top left of the size) lies between the left and right and
     * the top and bottom edges of a rectangle of this size.
     *
     * Rectangles include their top and left edges but exclude their bottom and
     * right edges.
     */
    fun contains(offset: Offset): Boolean {
        return offset.dx >= 0.0f && offset.dx < width && offset.dy >= 0.0f && offset.dy < height
    }

    /**
     * A [Size] with the [width] and [height] swapped.
     */
    fun getFlipped() = Size(height, width)

    override fun toString() = "Size(${width.toStringAsFixed(1)}, ${height.toStringAsFixed(1)})"

    // TODO(Migration/Andrey): Can't use data class because of _DebugSize class extending this one.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Size) return false

        return dx == other.dx && dy == other.dy
    }

    override fun hashCode(): Int {
        var result = dx.hashCode()
        result = 31 * result + dy.hashCode()
        return result
    }
}

/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.window.embedding

import android.annotation.SuppressLint
import androidx.annotation.ColorInt
import androidx.annotation.FloatRange
import androidx.annotation.IntRange
import androidx.window.core.SpecificationComputer.Companion.startSpecification
import androidx.window.core.VerificationMode
import androidx.window.embedding.SplitAttributes.LayoutDirection
import androidx.window.embedding.SplitAttributes.LayoutDirection.Companion.LOCALE
import androidx.window.embedding.SplitAttributes.SplitType
import androidx.window.embedding.SplitAttributes.SplitType.Companion.splitEqually

/**
 * Attributes that describe how the parent window (typically the activity task
 * window) is split between the primary and secondary activity containers,
 * including:
 *   - Split type &mdash; Categorizes the split and specifies the sizes of the
 *     primary and secondary activity containers relative to the parent bounds
 *   - Layout direction &mdash; Specifies whether the parent window is split
 *     vertically or horizontally and in which direction the primary and
 *     secondary containers are respectively positioned (left to right, right to
 *     left, top to bottom, and so forth)
 *   - Animation background color &mdash; The color of the background during
 *     animation of the split involving this `SplitAttributes` object if the
 *     animation requires a background
 *
 * Attributes can be configured by:
 *   - Setting the default `SplitAttributes` using
 *     [SplitPairRule.Builder.setDefaultSplitAttributes] or
 *     [SplitPlaceholderRule.Builder.setDefaultSplitAttributes].
 *   - Setting `splitRatio`, `splitLayoutDirection`, and
 *     `animationBackgroundColor` attributes in `<SplitPairRule>` or
 *     `<SplitPlaceholderRule>` tags in an XML configuration file. The
 *     attributes are parsed as [SplitType], [LayoutDirection], and [ColorInt],
 *     respectively. Note that [SplitType.HingeSplitType] is not supported XML
 *     format.
 *   - Using
 *     [SplitAttributesCalculator.computeSplitAttributesForParams] to customize
 *     the `SplitAttributes` for a given device and window state.
 *
 * @see SplitAttributes.SplitType
 * @see SplitAttributes.LayoutDirection
 */
class SplitAttributes internal constructor(

    /**
     * The split type attribute. Defaults to an equal split of the parent window
     * for the primary and secondary containers.
     */
    val splitType: SplitType = splitEqually(),

    /**
     * The layout direction attribute for the parent window split. The default
     * is based on locale.
     */
    val layoutDirection: LayoutDirection = LOCALE,

    /**
     * The [ColorInt] to use for the background color during the animation of
     * the split involving this `SplitAttributes` object if the animation
     * requires a background.
     *
     * The default is 0, which specifies the theme window background color.
     */
    @ColorInt
    val animationBackgroundColor: Int = 0
) {

    /**
     * The type of parent window split, which defines the proportion of the
     * parent window occupied by the primary and secondary activity containers.
     */
    open class SplitType internal constructor(

        /**
         * The description of this `SplitType`.
         */
        internal val description: String,

        /**
         * An identifier for the split type.
         *
         * Used in the evaluation in the `equals()` method.
         */
        internal val value: Float,

    ) {

        /**
         * A string representation of this split type.
         *
         * @return The string representation of the object.
         */
        override fun toString(): String = description

        /**
         * Determines whether this object is the same type of split as the
         * compared object.
         *
         * @param other The object to compare to this object.
         * @return True if the objects are the same split type, false otherwise.
         */
        override fun equals(other: Any?): Boolean {
            if (other === this) return true
            if (other !is SplitType) return false
            return value == other.value &&
                description == other.description
        }

        /**
         * Returns a hash code for this split type.
         *
         * @return The hash code for this object.
         */
        override fun hashCode(): Int = description.hashCode() + 31 * value.hashCode()

        /**
         * A window split that's based on the ratio of the size of the primary
         * container to the size of the parent window.
         *
         * @see SplitAttributes.SplitType.ratio
         */
        class RatioSplitType internal constructor(

            /**
             * The proportion of the parent window occupied by the primary
             * container of the split.
             */
            @FloatRange(from = 0.0, to = 1.0, fromInclusive = false, toInclusive = false)
            val ratio: Float

        ) : SplitType("ratio:$ratio", ratio)

        /**
         * A window split in which the primary and secondary activity containers
         * each occupy the entire parent window.
         *
         * The secondary container overlays the primary container.
         *
         * @see SplitAttributes.SplitType.ExpandContainersSplitType
         */
        class ExpandContainersSplitType internal constructor() : SplitType("expandContainer", 0.0f)

        /**
         * A parent window split that conforms to a hinge or separating fold in
         * the device display.
         *
         * @see SplitAttributes.SplitType.splitByHinge
         */
        class HingeSplitType internal constructor(

            /**
             * The split type to use if a split based on the device hinge or
             * separating fold cannot be determined.
             */
            val fallbackSplitType: SplitType

        ) : SplitType("hinge, fallback=$fallbackSplitType", -1.0f)

        /**
         * Methods that create various split types.
         */
        companion object {

            /**
             * Creates a split type based on the proportion of the parent window
             * occupied by the primary container of the split.
             *
             * Values in the non-inclusive range (0.0, 1.0) define the size of
             * the primary container relative to the size of the parent window:
             * - 0.5 &mdash; Primary container occupies half of the parent
             *   window; secondary container, the other half
             * - &gt; 0.5 &mdash; Primary container occupies a larger proportion
             *   of the parent window than the secondary container
             * - &lt; 0.5 &mdash; Primary container occupies a smaller
             *   proportion of the parent window than the secondary container
             *
             * @param ratio The proportion of the parent window occupied by the
             *     primary container of the split.
             * @return An instance of [RatioSplitType] with the specified ratio.
             */
            @JvmStatic
            fun ratio(
                @FloatRange(from = 0.0, to = 1.0, fromInclusive = false, toInclusive = false)
                ratio: Float
            ): RatioSplitType {
                val checkedRatio = ratio.startSpecification(
                    TAG,
                    VerificationMode.STRICT
                ).require("Ratio must be in range (0.0, 1.0). " +
                    "Use SplitType.expandContainers() instead of 0 or 1.") {
                    ratio in 0.0..1.0 && ratio !in arrayOf(0.0f, 1.0f)
                }.compute()!!
                return RatioSplitType(checkedRatio)
            }

            private val EXPAND_CONTAINERS = ExpandContainersSplitType()

            /**
             * Creates a split type in which the primary and secondary activity
             * containers each expand to fill the parent window; the secondary
             * container overlays the primary container.
             *
             * Use this method with [SplitAttributesCalculator] to expand the
             * activity containers in some device states. The following sample
             * shows how to always fill the parent bounds if the device is in
             * portrait orientation:
             *
             * @sample androidx.window.samples.embedding.expandContainersInPortrait
             *
             * @return An instance of [ExpandContainersSplitType].
             */
            @JvmStatic
            fun expandContainers(): ExpandContainersSplitType = EXPAND_CONTAINERS

            /**
             * Creates a split type in which the primary and secondary
             * containers occupy equal portions of the parent window.
             *
             * Serves as the default [SplitType].
             *
             * @return A `RatioSplitType` in which the activity containers
             *     occupy equal portions of the parent window.
             */
            @JvmStatic
            fun splitEqually(): RatioSplitType = ratio(0.5f)

            /**
             * Creates a split type in which the split ratio conforms to the
             * position of a hinge or separating fold in the device display.
             *
             * The split type is created only if:
             * <ul>
             *     <li>The host task is not in multi-window mode (e.g.,
             *         split-screen mode or picture-in-picture mode)</li>
             *     <li>The device has a hinge or separating fold reported by
             *         [androidx.window.layout.FoldingFeature.isSeparating]</li>
             *     <li>The hinge or separating fold orientation matches how the
             *         parent bounds are split:
             *         <ul style="list-style-type: circle;">
             *             <li>The hinge or fold orientation is vertical, and
             *                 the parent bounds are also split vertically
             *                 (containers are side by side)</li>
             *             <li>The hinge or fold orientation is horizontal, and
             *                 the parent bounds are also split horizontally
             *                 (containers are top and bottom)</li>
             *         </ul>
             *     </li>
             * </ul>
             *
             * Otherwise, the method falls back to `fallbackSplitType`.
             *
             * @param fallbackSplitType The split type to use if a split based
             *     on the device hinge or separating fold cannot be determined.
             *     Can be a [RatioSplitType] or [ExpandContainersSplitType].
             *     Defaults to [SplitType.splitEqually].
             * @return An instance of [HingeSplitType] with a fallback split
             *     type.
             */
            @JvmStatic
            fun splitByHinge(
                fallbackSplitType: SplitType = splitEqually()
            ): HingeSplitType {
                val checkedType = fallbackSplitType.startSpecification(
                    TAG,
                    VerificationMode.STRICT
                ).require(
                    "FallbackSplitType must be a RatioSplitType or ExpandContainerSplitType"
                ) {
                    fallbackSplitType is RatioSplitType ||
                        fallbackSplitType is ExpandContainersSplitType
                }.compute()!!
                return HingeSplitType(checkedType)
            }

            /**
             * Returns a `SplitType` with the given `value`.
             */
            @SuppressLint("Range") // value = 0.0 is covered.
            @JvmStatic
            internal fun buildSplitTypeFromValue(
                @FloatRange(from = 0.0, to = 1.0, toInclusive = false) value: Float
            ) = if (value == EXPAND_CONTAINERS.value) {
                    expandContainers()
                } else {
                    ratio(value)
                }
        }
    }

    /**
     * The layout direction of the primary and secondary activity containers.
     */
    class LayoutDirection private constructor(

        /**
         * The description of this `LayoutDirection`.
         */
        private val description: String,

        /**
         * The enum value defined in `splitLayoutDirection` attributes in
         * `attrs.xml`.
         */
        internal val value: Int,

    ) {

        /**
         * A string representation of this `LayoutDirection`.
         *
         * @return The string representation of the object.
         */
        override fun toString(): String = description

        /**
         * Non-public properties and methods.
         */
        companion object {
            /**
             * Specifies that the parent bounds are split vertically (side to
             * side).
             *
             * The direction of the primary and secondary containers is deduced
             * from the locale as either `LEFT_TO_RIGHT` or `RIGHT_TO_LEFT`.
             *
             * See also [layoutDirection].
             */
            @JvmField
            val LOCALE = LayoutDirection("LOCALE", 0)
            /**
             * Specifies that the parent bounds are split vertically (side to
             * side).
             *
             * Places the primary container in the left portion of the parent
             * window, and the secondary container in the right portion.
             *
             * <img width="70%" height="70%" src="/images/guide/topics/large-screens/activity-embedding/reference-docs/a_to_a_b_ltr.png" alt="Activity A starts activity B to the right."/>
             *
             * See also [layoutDirection].
             */
            @JvmField
            val LEFT_TO_RIGHT = LayoutDirection("LEFT_TO_RIGHT", 1)
            /**
             * Specifies that the parent bounds are split vertically (side to
             * side).
             *
             * Places the primary container in the right portion of the parent
             * window, and the secondary container in the left portion.
             *
             * <img width="70%" height="70%" src="/images/guide/topics/large-screens/activity-embedding/reference-docs/a_to_a_b_rtl.png" alt="Activity A starts activity B to the left."/>
             *
             * See also [layoutDirection].
             */
            @JvmField
            val RIGHT_TO_LEFT = LayoutDirection("RIGHT_TO_LEFT", 2)
            /**
             * Specifies that the parent bounds are split horizontally (top and
             * bottom).
             *
             * Places the primary container in the top portion of the parent
             * window, and the secondary container in the bottom portion.
             *
             * <img width="70%" height="70%" src="/images/guide/topics/large-screens/activity-embedding/reference-docs/a_to_a_b_ttb.png" alt="Activity A starts activity B to the bottom."/>
             *
             * If the horizontal layout direction is not supported on the
             * device, layout direction falls back to `LOCALE`.
             *
             * See also [layoutDirection].
             */
            @JvmField
            val TOP_TO_BOTTOM = LayoutDirection("TOP_TO_BOTTOM", 3)
            /**
             * Specifies that the parent bounds are split horizontally (top and
             * bottom).
             *
             * Places the primary container in the bottom portion of the parent
             * window, and the secondary container in the top portion.
             *
             * <img width="70%" height="70%" src="/images/guide/topics/large-screens/activity-embedding/reference-docs/a_to_a_b_btt.png" alt="Activity A starts activity B to the top."/>
             *
             * If the horizontal layout direction is not supported on the
             * device, layout direction falls back to `LOCALE`.
             *
             * See also [layoutDirection].
             */
            @JvmField
            val BOTTOM_TO_TOP = LayoutDirection("BOTTOM_TO_TOP", 4)

            /**
             * Returns `LayoutDirection` with the given `value`.
             */
            @JvmStatic
            internal fun getLayoutDirectionFromValue(
                @IntRange(from = 0, to = 4) value: Int
            ) = when (value) {
                LEFT_TO_RIGHT.value -> LEFT_TO_RIGHT
                RIGHT_TO_LEFT.value -> RIGHT_TO_LEFT
                LOCALE.value -> LOCALE
                TOP_TO_BOTTOM.value -> TOP_TO_BOTTOM
                BOTTOM_TO_TOP.value -> BOTTOM_TO_TOP
                else -> throw IllegalArgumentException("Undefined value:$value")
            }
        }
    }

    /**
     * Non-public properties and methods.
     */
    companion object {
        private val TAG = SplitAttributes::class.java.simpleName
    }

    /**
     * Returns a hash code for this `SplitAttributes` object.
     *
     * @return The hash code for this object.
     */
    override fun hashCode(): Int {
        var result = splitType.hashCode()
        result = result * 31 + layoutDirection.hashCode()
        result = result * 31 + animationBackgroundColor.hashCode()
        return result
    }

    /**
     * Determines whether this object has the same split attributes as the
     * compared object.
     *
     * @param other The object to compare to this object.
     * @return True if the objects have the same split attributes, false
     * otherwise.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SplitAttributes) return false
        return splitType == other.splitType &&
            layoutDirection == other.layoutDirection &&
            animationBackgroundColor == other.animationBackgroundColor
    }

    /**
     * A string representation of this `SplitAttributes` object.
     *
     * @return The string representation of the object.
     */
    override fun toString(): String =
        "${SplitAttributes::class.java.simpleName}:" +
            "{splitType=$splitType, layoutDir=$layoutDirection," +
            " animationBackgroundColor=${Integer.toHexString(animationBackgroundColor)}"

    /**
     * Builder for creating an instance of [SplitAttributes].
     *
     * The default split type is an equal split between primary and secondary
     * containers. The default layout direction is based on locale. The default
     * animation background color is 0, which specifies the theme window
     * background color.
     */
    class Builder {
        private var splitType: SplitType = splitEqually()
        private var layoutDirection = LOCALE
        @ColorInt
        private var animationBackgroundColor = 0

        /**
         * Sets the split type attribute.
         *
         * The default is an equal split between primary and secondary
         * containers.
         *
         * @param type The split type attribute.
         * @return This `Builder`.
         *
         * @see SplitAttributes.SplitType
         */
        fun setSplitType(type: SplitType): Builder = apply { splitType = type }

        /**
         * Sets the split layout direction attribute.
         *
         * The default is based on locale.
         *
         * @param layoutDirection The layout direction attribute.
         * @return This `Builder`.
         *
         * @see SplitAttributes.LayoutDirection
         */
        fun setLayoutDirection(layoutDirection: LayoutDirection): Builder =
            apply { this.layoutDirection = layoutDirection }

        /**
         * Sets the [ColorInt] to use for the background color during animation
         * of the split involving this `SplitAttributes` object if the animation
         * requires a background.
         *
         * The default is 0, which specifies the theme window background color.
         *
         * @param color A packed color int of the form `AARRGGBB`, for the
         * animation background color.
         * @return This `Builder`.
         *
         * @see SplitAttributes.animationBackgroundColor
         */
        fun setAnimationBackgroundColor(@ColorInt color: Int): Builder =
            apply { this.animationBackgroundColor = color }

        /**
         * Builds a `SplitAttributes` instance with the attributes specified by
         * [setSplitType], [setLayoutDirection], and
         * [setAnimationBackgroundColor].
         *
         * @return The new `SplitAttributes` instance.
         */
        fun build(): SplitAttributes = SplitAttributes(splitType, layoutDirection,
            animationBackgroundColor)
    }
}
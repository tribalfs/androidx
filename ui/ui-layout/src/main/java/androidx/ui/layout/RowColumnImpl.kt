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

package androidx.ui.layout

import androidx.compose.Composable
import androidx.compose.Immutable
import androidx.compose.Stable
import androidx.ui.core.Alignment
import androidx.ui.core.AlignmentLine
import androidx.ui.core.Constraints
import androidx.ui.core.IntrinsicMeasurable
import androidx.ui.core.IntrinsicMeasureBlock
import androidx.ui.core.Layout
import androidx.ui.core.LayoutDirection
import androidx.ui.core.Measured
import androidx.ui.core.Modifier
import androidx.ui.core.ParentDataModifier
import androidx.ui.core.Placeable
import androidx.ui.unit.Density
import androidx.ui.unit.IntPx
import androidx.ui.unit.ipx
import androidx.ui.unit.isFinite
import androidx.ui.unit.max
import androidx.ui.util.fastForEach
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Shared implementation for [Row] and [Column].
 */
@Composable
internal fun RowColumnImpl(
    orientation: LayoutOrientation,
    modifier: Modifier = Modifier,
    arrangement: Arrangement,
    crossAxisSize: SizeMode,
    crossAxisAlignment: CrossAxisAlignment,
    children: @Composable () -> Unit
) {
    fun Placeable.mainAxisSize() =
        if (orientation == LayoutOrientation.Horizontal) width else height
    fun Placeable.crossAxisSize() =
        if (orientation == LayoutOrientation.Horizontal) height else width

    Layout(
        children,
        modifier = modifier,
        minIntrinsicWidthMeasureBlock = MinIntrinsicWidthMeasureBlock(orientation),
        minIntrinsicHeightMeasureBlock = MinIntrinsicHeightMeasureBlock(orientation),
        maxIntrinsicWidthMeasureBlock = MaxIntrinsicWidthMeasureBlock(orientation),
        maxIntrinsicHeightMeasureBlock = MaxIntrinsicHeightMeasureBlock(orientation)
    ) { ltrMeasurables, outerConstraints, layoutDirection ->
        // rtl support
        val measurables = if (orientation == LayoutOrientation.Horizontal &&
            layoutDirection == LayoutDirection.Rtl) {
            ltrMeasurables.asReversed()
        } else {
            ltrMeasurables
        }

        val constraints = OrientationIndependentConstraints(outerConstraints, orientation)

        var totalWeight = 0f
        var fixedSpace = IntPx.Zero
        var crossAxisSpace = IntPx.Zero

        var anyAlignWithSiblings = false
        val placeables = arrayOfNulls<Placeable>(measurables.size)
        val rowColumnParentData = Array(measurables.size) { measurables[it].data }

        // First measure children with zero weight.
        for (i in measurables.indices) {
            val child = measurables[i]
            val parentData = rowColumnParentData[i]
            val weight = parentData.weight

            if (weight > 0f) {
                totalWeight += weight
            } else {
                val placeable = child.measure(
                    // Ask for preferred main axis size.
                    constraints.copy(
                        mainAxisMin = IntPx.Zero,
                        mainAxisMax = constraints.mainAxisMax - fixedSpace,
                        crossAxisMin = IntPx.Zero
                    ).toBoxConstraints(orientation)
                )
                fixedSpace += placeable.mainAxisSize()
                crossAxisSpace = max(crossAxisSpace, placeable.crossAxisSize())
                anyAlignWithSiblings = anyAlignWithSiblings || parentData.isRelative
                placeables[i] = placeable
            }
        }

        // Then measure the rest according to their weights in the remaining main axis space.
        val targetSpace = if (totalWeight > 0f && constraints.mainAxisMax.isFinite()) {
            constraints.mainAxisMax
        } else {
            constraints.mainAxisMin
        }

        val weightUnitSpace = if (totalWeight > 0) {
            (targetSpace.value.toFloat() - fixedSpace.value.toFloat()) / totalWeight
        } else {
            0f
        }

        var remainder = targetSpace - fixedSpace - rowColumnParentData.sumBy {
            (weightUnitSpace * it.weight).roundToInt()
        }.ipx

        var weightedSpace = IntPx.Zero

        for (i in measurables.indices) {
            if (placeables[i] == null) {
                val child = measurables[i]
                val parentData = rowColumnParentData[i]
                val weight = parentData.weight
                check(weight > 0) { "All weights <= 0 should have placeables" }
                val remainderUnit = remainder.value.sign.ipx
                remainder -= remainderUnit
                val childMainAxisSize = max(
                    IntPx.Zero,
                    (weightUnitSpace * weight).roundToInt().ipx + remainderUnit
                )
                val placeable = child.measure(
                    OrientationIndependentConstraints(
                        if (parentData.fill && childMainAxisSize.isFinite()) {
                            childMainAxisSize
                        } else {
                            IntPx.Zero
                        },
                        childMainAxisSize,
                        IntPx.Zero,
                        constraints.crossAxisMax
                    ).toBoxConstraints(orientation)
                )
                weightedSpace += placeable.mainAxisSize()
                crossAxisSpace = max(crossAxisSpace, placeable.crossAxisSize())
                anyAlignWithSiblings = anyAlignWithSiblings || parentData.isRelative
                placeables[i] = placeable
            }
        }

        var beforeCrossAxisAlignmentLine = IntPx.Zero
        var afterCrossAxisAlignmentLine = IntPx.Zero
        if (anyAlignWithSiblings) {
            for (i in placeables.indices) {
                val placeable = placeables[i]!!
                val parentData = rowColumnParentData[i]
                val alignmentLinePosition = parentData.crossAxisAlignment
                    ?.calculateAlignmentLinePosition(placeable)
                if (alignmentLinePosition != null) {
                    beforeCrossAxisAlignmentLine = max(
                        beforeCrossAxisAlignmentLine,
                        alignmentLinePosition
                    )
                    afterCrossAxisAlignmentLine = max(
                        afterCrossAxisAlignmentLine,
                        placeable.crossAxisSize() - alignmentLinePosition
                    )
                }
            }
        }

        // Compute the Row or Column size and position the children.
        val mainAxisLayoutSize = if (totalWeight > 0f && constraints.mainAxisMax.isFinite()) {
            constraints.mainAxisMax
        } else {
            max(fixedSpace + weightedSpace, constraints.mainAxisMin)
        }
        val crossAxisLayoutSize = if (constraints.crossAxisMax.isFinite() &&
            crossAxisSize == SizeMode.Expand
        ) {
            constraints.crossAxisMax
        } else {
            max(
                crossAxisSpace,
                max(
                    constraints.crossAxisMin,
                    beforeCrossAxisAlignmentLine + afterCrossAxisAlignmentLine
                )
            )
        }
        val layoutWidth = if (orientation == LayoutOrientation.Horizontal) {
            mainAxisLayoutSize
        } else {
            crossAxisLayoutSize
        }
        val layoutHeight = if (orientation == LayoutOrientation.Horizontal) {
            crossAxisLayoutSize
        } else {
            mainAxisLayoutSize
        }
        layout(layoutWidth, layoutHeight) {
            val childrenMainAxisSize = placeables.map { it!!.mainAxisSize() }
            val mainAxisPositions = arrangement.arrange(
                mainAxisLayoutSize,
                childrenMainAxisSize,
                layoutDirection
            )
            placeables.forEachIndexed { index, placeable ->
                placeable!!
                val parentData = rowColumnParentData[index]
                val childCrossAlignment = parentData.crossAxisAlignment ?: crossAxisAlignment

                val crossAxis = childCrossAlignment.align(
                    size = crossAxisLayoutSize - placeable.crossAxisSize(),
                    layoutDirection = if (orientation == LayoutOrientation.Horizontal) {
                        LayoutDirection.Ltr
                    } else {
                        layoutDirection
                    },
                    placeable = placeable,
                    beforeCrossAxisAlignmentLine = beforeCrossAxisAlignmentLine
                )

                if (orientation == LayoutOrientation.Horizontal) {
                    placeable.placeAbsolute(mainAxisPositions[index], crossAxis)
                } else {
                    placeable.placeAbsolute(crossAxis, mainAxisPositions[index])
                }
            }
        }
    }
}

/**
 * Used to specify the arrangement of the layout's children in [Row] or [Column] in the main axis
 * direction (horizontal and vertical, respectively).
 * @constructor Creates an arrangement using the [arrange] function. Use it to provide your own
 * arrangement of the layout's children.
 */
@Immutable
interface Arrangement {
    /**
     * Places the layout children inside the parent layout along the main axis.
     *
     * @param totalSize Available space that can be occupied by the children.
     * @param size A list of sizes of all children.
     * @param layoutDirection A layout direction, left-to-right or right-to-left, of the parent
     * layout that should be taken into account when determining positions of the children in
     * horizontal direction.
     */
    fun arrange(totalSize: IntPx, size: List<IntPx>, layoutDirection: LayoutDirection): List<IntPx>

    /**
     * Used to specify the vertical arrangement of the layout's children in a [Column].
     */
    interface Vertical : Arrangement
    /**
     * Used to specify the horizontal arrangement of the layout's children in a [Row].
     */
    interface Horizontal : Arrangement

    /**
     * Place children horizontally such that they are as close as possible to the beginning of the
     * main axis.
     */
    object Start : Horizontal {
        override fun arrange(
            totalSize: IntPx,
            size: List<IntPx>,
            layoutDirection: LayoutDirection
        ) = if (layoutDirection == LayoutDirection.Ltr) {
            placeLeftOrTop(size)
        } else {
            placeRightOrBottom(totalSize, size)
        }
    }

    /**
     * Place children horizontally such that they are as close as possible to the end of the main
     * axis.
     */
    object End : Horizontal {
        override fun arrange(
            totalSize: IntPx,
            size: List<IntPx>,
            layoutDirection: LayoutDirection
        ) = if (layoutDirection == LayoutDirection.Ltr) {
            placeRightOrBottom(totalSize, size)
        } else {
            placeLeftOrTop(size)
        }
    }

    /**
     * Place children vertically such that they are as close as possible to the top of the main
     * axis.
     */
    object Top : Vertical {
        override fun arrange(
            totalSize: IntPx,
            size: List<IntPx>,
            layoutDirection: LayoutDirection
        ) = placeLeftOrTop(size)
    }

    /**
     * Place children vertically such that they are as close as possible to the bottom of the main
     * axis.
     */
    object Bottom : Vertical {
        override fun arrange(
            totalSize: IntPx,
            size: List<IntPx>,
            layoutDirection: LayoutDirection
        ) = placeRightOrBottom(totalSize, size)
    }

    /**
     * Place children such that they are as close as possible to the middle of the main axis.
     */
    object Center : Vertical, Horizontal {
        override fun arrange(
            totalSize: IntPx,
            size: List<IntPx>,
            layoutDirection: LayoutDirection
        ): List<IntPx> {
            val consumedSize = size.fold(0.ipx) { a, b -> a + b }
            val positions = mutableListOf<IntPx>()
            var current = (totalSize - consumedSize).value.toFloat() / 2
            size.fastForEach {
                positions.add(current.roundToInt().ipx)
                current += it.value.toFloat()
            }
            return positions
        }
    }

    /**
     * Place children such that they are spaced evenly across the main axis, including free
     * space before the first child and after the last child.
     */
    object SpaceEvenly : Vertical, Horizontal {
        override fun arrange(
            totalSize: IntPx,
            size: List<IntPx>,
            layoutDirection: LayoutDirection
        ): List<IntPx> {
            val consumedSize = size.fold(0.ipx) { a, b -> a + b }
            val gapSize = (totalSize - consumedSize).value.toFloat() / (size.size + 1)
            val positions = mutableListOf<IntPx>()
            var current = gapSize
            size.fastForEach {
                positions.add(current.roundToInt().ipx)
                current += it.value.toFloat() + gapSize
            }
            return positions
        }
    }

    /**
     * Place children such that they are spaced evenly across the main axis, without free
     * space before the first child or after the last child.
     */
    object SpaceBetween : Vertical, Horizontal {
        override fun arrange(
            totalSize: IntPx,
            size: List<IntPx>,
            layoutDirection: LayoutDirection
        ): List<IntPx> {
            val consumedSize = size.fold(0.ipx) { a, b -> a + b }
            val gapSize = if (size.size > 1) {
                (totalSize - consumedSize).value.toFloat() / (size.size - 1)
            } else {
                0f
            }
            val positions = mutableListOf<IntPx>()
            var current = 0f
            size.fastForEach {
                positions.add(current.roundToInt().ipx)
                current += it.value.toFloat() + gapSize
            }
            return positions
        }
    }

    /**
     * Place children such that they are spaced evenly across the main axis, including free
     * space before the first child and after the last child, but half the amount of space
     * existing otherwise between two consecutive children.
     */
    object SpaceAround : Vertical, Horizontal {
        override fun arrange(
            totalSize: IntPx,
            size: List<IntPx>,
            layoutDirection: LayoutDirection
        ): List<IntPx> {
            val consumedSize = size.fold(0.ipx) { a, b -> a + b }
            val gapSize = if (size.isNotEmpty()) {
                (totalSize - consumedSize).value.toFloat() / size.size
            } else {
                0f
            }
            val positions = mutableListOf<IntPx>()
            var current = gapSize / 2
            size.fastForEach {
                positions.add(current.roundToInt().ipx)
                current += it.value.toFloat() + gapSize
            }
            return positions
        }
    }

    private companion object {
        private fun placeRightOrBottom(totalSize: IntPx, size: List<IntPx>): List<IntPx> {
            val consumedSize = size.fold(0.ipx) { a, b -> a + b }
            val positions = mutableListOf<IntPx>()
            var current = totalSize - consumedSize
            size.fastForEach {
                positions.add(current)
                current += it
            }
            return positions
        }
        private fun placeLeftOrTop(size: List<IntPx>): List<IntPx> {
            val positions = mutableListOf<IntPx>()
            var current = 0.ipx
            size.fastForEach {
                positions.add(current)
                current += it
            }
            return positions
        }
    }
}

/**
 * [Row] will be [Horizontal], [Column] is [Vertical].
 */
internal enum class LayoutOrientation {
    Horizontal,
    Vertical
}

/**
 * Used to specify how a layout chooses its own size when multiple behaviors are possible.
 */
// TODO(popam): remove this when Flow is reworked
enum class SizeMode {
    /**
     * Minimize the amount of free space by wrapping the children,
     * subject to the incoming layout constraints.
     */
    Wrap,
    /**
     * Maximize the amount of free space by expanding to fill the available space,
     * subject to the incoming layout constraints.
     */
    Expand
}

/**
 * Used to specify the alignment of a layout's children, in main axis direction.
 */
enum class MainAxisAlignment(internal val arrangement: Arrangement) {
    // TODO(soboleva) support RTl in Flow
    // workaround for now - use Arrangement that equals to previous Arrangement
    /**
     * Place children such that they are as close as possible to the middle of the main axis.
     */
    Center(Arrangement.Center),
    /**
     * Place children such that they are as close as possible to the start of the main axis.
     */
    Start(Arrangement.Top),
    /**
     * Place children such that they are as close as possible to the end of the main axis.
     */
    End(Arrangement.Bottom),
    /**
     * Place children such that they are spaced evenly across the main axis, including free
     * space before the first child and after the last child.
     */
    SpaceEvenly(Arrangement.SpaceEvenly),
    /**
     * Place children such that they are spaced evenly across the main axis, without free
     * space before the first child or after the last child.
     */
    SpaceBetween(Arrangement.SpaceBetween),
    /**
     * Place children such that they are spaced evenly across the main axis, including free
     * space before the first child and after the last child, but half the amount of space
     * existing otherwise between two consecutive children.
     */
    SpaceAround(Arrangement.SpaceAround);
}

/**
 * Used to specify the alignment of a layout's children, in cross axis direction.
 */
// TODO(popam): refine this API surface with modifiers - add type safety for alignment orientation.
@Immutable
sealed class CrossAxisAlignment {
    /**
     * Aligns to [size]. If this is a vertical alignment, [layoutDirection] should be
     * [LayoutDirection.Ltr].
     *
     * @param size The remaining space (total size - content size) in the container.
     * @param layoutDirection The layout direction of the content if horizontal or
     * [LayoutDirection.Ltr] if vertical.
     * @param placeable The item being aligned.
     * @param beforeCrossAxisAlignmentLine The space before the cross-axis alignment line if
     * an alignment line is being used or 0.ipx if no alignment line is being used.
     */
    internal abstract fun align(
        size: IntPx,
        layoutDirection: LayoutDirection,
        placeable: Placeable,
        beforeCrossAxisAlignmentLine: IntPx
    ): IntPx

    /**
     * Returns `true` if this is [Relative].
     */
    internal open val isRelative: Boolean
        get() = false

    /**
     * Returns the alignment line position relative to the left/top of the space or `null` if
     * this alignment doesn't rely on alignment lines.
     */
    internal open fun calculateAlignmentLinePosition(placeable: Placeable): IntPx? = null

    companion object {
        /**
         * Place children such that their center is in the middle of the cross axis.
         */
        @Stable
        val Center: CrossAxisAlignment = CenterCrossAxisAlignment
        /**
         * Place children such that their start edge is aligned to the start edge of the cross
         * axis. TODO(popam): Consider rtl directionality.
         */
        @Stable
        val Start: CrossAxisAlignment = StartCrossAxisAlignment
        /**
         * Place children such that their end edge is aligned to the end edge of the cross
         * axis. TODO(popam): Consider rtl directionality.
         */
        @Stable
        val End: CrossAxisAlignment = EndCrossAxisAlignment
        /**
         * Align children by their baseline.
         */
        fun AlignmentLine(alignmentLine: AlignmentLine): CrossAxisAlignment =
            AlignmentLineCrossAxisAlignment(AlignmentLineProvider.Value(alignmentLine))
        /**
         * Align children relative to their siblings using the alignment line provided as a
         * parameter using [AlignmentLineProvider].
         */
        internal fun Relative(alignmentLineProvider: AlignmentLineProvider): CrossAxisAlignment =
            AlignmentLineCrossAxisAlignment(alignmentLineProvider)

        /**
         * Align children with vertical alignment.
         */
        internal fun vertical(vertical: Alignment.Vertical): CrossAxisAlignment =
            VerticalCrossAxisAlignment(vertical)

        /**
         * Align children with horizontal alignment.
         */
        internal fun horizontal(horizontal: Alignment.Horizontal): CrossAxisAlignment =
            HorizontalCrossAxisAlignment(horizontal)
    }

    private object CenterCrossAxisAlignment : CrossAxisAlignment() {
        override fun align(
            size: IntPx,
            layoutDirection: LayoutDirection,
            placeable: Placeable,
            beforeCrossAxisAlignmentLine: IntPx
        ): IntPx {
            return size / 2
        }
    }

    private object StartCrossAxisAlignment : CrossAxisAlignment() {
        override fun align(
            size: IntPx,
            layoutDirection: LayoutDirection,
            placeable: Placeable,
            beforeCrossAxisAlignmentLine: IntPx
        ): IntPx {
            return if (layoutDirection == LayoutDirection.Ltr) 0.ipx else size
        }
    }

    private object EndCrossAxisAlignment : CrossAxisAlignment() {
        override fun align(
            size: IntPx,
            layoutDirection: LayoutDirection,
            placeable: Placeable,
            beforeCrossAxisAlignmentLine: IntPx
        ): IntPx {
            return if (layoutDirection == LayoutDirection.Ltr) size else 0.ipx
        }
    }

    private class AlignmentLineCrossAxisAlignment(
        val alignmentLineProvider: AlignmentLineProvider
    ) : CrossAxisAlignment() {
        override val isRelative: Boolean
            get() = true

        override fun calculateAlignmentLinePosition(placeable: Placeable): IntPx? {
            return alignmentLineProvider.calculateAlignmentLinePosition(placeable)
        }

        override fun align(
            size: IntPx,
            layoutDirection: LayoutDirection,
            placeable: Placeable,
            beforeCrossAxisAlignmentLine: IntPx
        ): IntPx {
            val alignmentLinePosition =
                alignmentLineProvider.calculateAlignmentLinePosition(placeable)
            return if (alignmentLinePosition != null) {
                val line = beforeCrossAxisAlignmentLine - alignmentLinePosition
                if (layoutDirection == LayoutDirection.Rtl) {
                    size - line
                } else {
                    line
                }
            } else {
                IntPx.Zero
            }
        }
    }

    private class VerticalCrossAxisAlignment(
        val vertical: Alignment.Vertical
    ) : CrossAxisAlignment() {
        override fun align(
            size: IntPx,
            layoutDirection: LayoutDirection,
            placeable: Placeable,
            beforeCrossAxisAlignmentLine: IntPx
        ): IntPx {
            return vertical.align(size)
        }
    }

    private class HorizontalCrossAxisAlignment(
        val horizontal: Alignment.Horizontal
    ) : CrossAxisAlignment() {
        override fun align(
            size: IntPx,
            layoutDirection: LayoutDirection,
            placeable: Placeable,
            beforeCrossAxisAlignmentLine: IntPx
        ): IntPx {
            return horizontal.align(size, layoutDirection)
        }
    }
}

/**
 * Box [Constraints], but which abstract away width and height in favor of main axis and cross axis.
 */
internal data class OrientationIndependentConstraints(
    val mainAxisMin: IntPx,
    val mainAxisMax: IntPx,
    val crossAxisMin: IntPx,
    val crossAxisMax: IntPx
) {
    constructor(c: Constraints, orientation: LayoutOrientation) : this(
        if (orientation === LayoutOrientation.Horizontal) c.minWidth else c.minHeight,
        if (orientation === LayoutOrientation.Horizontal) c.maxWidth else c.maxHeight,
        if (orientation === LayoutOrientation.Horizontal) c.minHeight else c.minWidth,
        if (orientation === LayoutOrientation.Horizontal) c.maxHeight else c.maxWidth
    )

    // Creates a new instance with the same main axis constraints and maximum tight cross axis.
    fun stretchCrossAxis() = OrientationIndependentConstraints(
        mainAxisMin,
        mainAxisMax,
        if (crossAxisMax.isFinite()) crossAxisMax else crossAxisMin,
        crossAxisMax
    )

    // Given an orientation, resolves the current instance to traditional constraints.
    fun toBoxConstraints(orientation: LayoutOrientation) =
        if (orientation === LayoutOrientation.Horizontal) {
            Constraints(mainAxisMin, mainAxisMax, crossAxisMin, crossAxisMax)
        } else {
            Constraints(crossAxisMin, crossAxisMax, mainAxisMin, mainAxisMax)
        }

    // Given an orientation, resolves the max width constraint this instance represents.
    fun maxWidth(orientation: LayoutOrientation) =
        if (orientation === LayoutOrientation.Horizontal) {
            mainAxisMax
        } else {
            crossAxisMax
        }

    // Given an orientation, resolves the max height constraint this instance represents.
    fun maxHeight(orientation: LayoutOrientation) =
        if (orientation === LayoutOrientation.Horizontal) {
            crossAxisMax
        } else {
            mainAxisMax
        }
}

private val IntrinsicMeasurable.data: RowColumnParentData?
    get() = parentData as? RowColumnParentData

private val RowColumnParentData?.weight: Float
    get() = this?.weight ?: 0f

private val RowColumnParentData?.fill: Boolean
    get() = this?.fill ?: true

private val RowColumnParentData?.crossAxisAlignment: CrossAxisAlignment?
    get() = this?.crossAxisAlignment

private val RowColumnParentData?.isRelative: Boolean
    get() = this.crossAxisAlignment?.isRelative ?: false

private /*inline*/ fun MinIntrinsicWidthMeasureBlock(orientation: LayoutOrientation) =
    if (orientation == LayoutOrientation.Horizontal) {
        IntrinsicMeasureBlocks.HorizontalMinWidth
    } else {
        IntrinsicMeasureBlocks.VerticalMinWidth
    }

private /*inline*/ fun MinIntrinsicHeightMeasureBlock(orientation: LayoutOrientation) =
    if (orientation == LayoutOrientation.Horizontal) {
        IntrinsicMeasureBlocks.HorizontalMinHeight
    } else {
        IntrinsicMeasureBlocks.VerticalMinHeight
    }

private /*inline*/ fun MaxIntrinsicWidthMeasureBlock(orientation: LayoutOrientation) =
    if (orientation == LayoutOrientation.Horizontal) {
        IntrinsicMeasureBlocks.HorizontalMaxWidth
    } else {
        IntrinsicMeasureBlocks.VerticalMaxWidth
    }

private /*inline*/ fun MaxIntrinsicHeightMeasureBlock(orientation: LayoutOrientation) =
    if (orientation == LayoutOrientation.Horizontal) {
        IntrinsicMeasureBlocks.HorizontalMaxHeight
    } else {
        IntrinsicMeasureBlocks.VerticalMaxHeight
    }

private object IntrinsicMeasureBlocks {
    val HorizontalMinWidth: IntrinsicMeasureBlock = { measurables, availableHeight, _ ->
        intrinsicSize(
            measurables,
            { h -> minIntrinsicWidth(h) },
            { w -> maxIntrinsicHeight(w) },
            availableHeight,
            LayoutOrientation.Horizontal,
            LayoutOrientation.Horizontal
        )
    }
    val VerticalMinWidth: IntrinsicMeasureBlock = { measurables, availableHeight, _ ->
        intrinsicSize(
            measurables,
            { h -> minIntrinsicWidth(h) },
            { w -> maxIntrinsicHeight(w) },
            availableHeight,
            LayoutOrientation.Vertical,
            LayoutOrientation.Horizontal
        )
    }
    val HorizontalMinHeight: IntrinsicMeasureBlock = { measurables, availableWidth, _ ->
        intrinsicSize(
            measurables,
            { w -> minIntrinsicHeight(w) },
            { h -> maxIntrinsicWidth(h) },
            availableWidth,
            LayoutOrientation.Horizontal,
            LayoutOrientation.Vertical
        )
    }
    val VerticalMinHeight: IntrinsicMeasureBlock = { measurables, availableWidth, _ ->
        intrinsicSize(
            measurables,
            { w -> minIntrinsicHeight(w) },
            { h -> maxIntrinsicWidth(h) },
            availableWidth,
            LayoutOrientation.Vertical,
            LayoutOrientation.Vertical
        )
    }
    val HorizontalMaxWidth: IntrinsicMeasureBlock = { measurables, availableHeight, _ ->
        intrinsicSize(
            measurables,
            { h -> maxIntrinsicWidth(h) },
            { w -> maxIntrinsicHeight(w) },
            availableHeight,
            LayoutOrientation.Horizontal,
            LayoutOrientation.Horizontal
        )
    }
    val VerticalMaxWidth: IntrinsicMeasureBlock = { measurables, availableHeight, _ ->
        intrinsicSize(
            measurables,
            { h -> maxIntrinsicWidth(h) },
            { w -> maxIntrinsicHeight(w) },
            availableHeight,
            LayoutOrientation.Vertical,
            LayoutOrientation.Horizontal
        )
    }
    val HorizontalMaxHeight: IntrinsicMeasureBlock = { measurables, availableWidth, _ ->
        intrinsicSize(
            measurables,
            { w -> maxIntrinsicHeight(w) },
            { h -> maxIntrinsicWidth(h) },
            availableWidth,
            LayoutOrientation.Horizontal,
            LayoutOrientation.Vertical
        )
    }
    val VerticalMaxHeight: IntrinsicMeasureBlock = { measurables, availableWidth, _ ->
        intrinsicSize(
            measurables,
            { w -> maxIntrinsicHeight(w) },
            { h -> maxIntrinsicWidth(h) },
            availableWidth,
            LayoutOrientation.Vertical,
            LayoutOrientation.Vertical
        )
    }
}

private fun intrinsicSize(
    children: List<IntrinsicMeasurable>,
    intrinsicMainSize: IntrinsicMeasurable.(IntPx) -> IntPx,
    intrinsicCrossSize: IntrinsicMeasurable.(IntPx) -> IntPx,
    crossAxisAvailable: IntPx,
    layoutOrientation: LayoutOrientation,
    intrinsicOrientation: LayoutOrientation
) = if (layoutOrientation == intrinsicOrientation) {
    intrinsicMainAxisSize(children, intrinsicMainSize, crossAxisAvailable)
} else {
    intrinsicCrossAxisSize(children, intrinsicCrossSize, intrinsicMainSize, crossAxisAvailable)
}

private fun intrinsicMainAxisSize(
    children: List<IntrinsicMeasurable>,
    mainAxisSize: IntrinsicMeasurable.(IntPx) -> IntPx,
    crossAxisAvailable: IntPx
): IntPx {
    var weightUnitSpace = 0.ipx
    var fixedSpace = 0.ipx
    var totalWeight = 0f
    children.fastForEach { child ->
        val weight = child.data.weight
        val size = child.mainAxisSize(crossAxisAvailable)
        if (weight == 0f) {
            fixedSpace += size
        } else if (weight > 0f) {
            totalWeight += weight
            weightUnitSpace = max(weightUnitSpace, size / weight)
        }
    }
    return weightUnitSpace * totalWeight + fixedSpace
}

private fun intrinsicCrossAxisSize(
    children: List<IntrinsicMeasurable>,
    mainAxisSize: IntrinsicMeasurable.(IntPx) -> IntPx,
    crossAxisSize: IntrinsicMeasurable.(IntPx) -> IntPx,
    mainAxisAvailable: IntPx
): IntPx {
    var fixedSpace = 0.ipx
    var crossAxisMax = 0.ipx
    var totalWeight = 0f
    children.fastForEach { child ->
        val weight = child.data.weight
        if (weight == 0f) {
            val mainAxisSpace = child.mainAxisSize(IntPx.Infinity)
            fixedSpace += mainAxisSpace
            crossAxisMax = max(crossAxisMax, child.crossAxisSize(mainAxisSpace))
        } else if (weight > 0f) {
            totalWeight += weight
        }
    }

    val weightUnitSpace = if (totalWeight == 0f) {
        IntPx.Zero
    } else {
        max(mainAxisAvailable - fixedSpace, IntPx.Zero) / totalWeight
    }

    children.fastForEach { child ->
        val weight = child.data.weight
        if (weight > 0f) {
            crossAxisMax = max(crossAxisMax, child.crossAxisSize(weightUnitSpace * weight))
        }
    }
    return crossAxisMax
}

internal data class LayoutWeightImpl(val weight: Float, val fill: Boolean) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?) =
        ((parentData as? RowColumnParentData) ?: RowColumnParentData()).also {
            it.weight = weight
            it.fill = fill
        }
}

internal sealed class SiblingsAlignedModifier : ParentDataModifier {
    abstract override fun Density.modifyParentData(parentData: Any?): Any?

    internal data class WithAlignmentLineBlock(val block: (Measured) -> IntPx) :
        SiblingsAlignedModifier() {
        override fun Density.modifyParentData(parentData: Any?): Any? {
            return ((parentData as? RowColumnParentData) ?: RowColumnParentData()).also {
                it.crossAxisAlignment =
                    CrossAxisAlignment.Relative(AlignmentLineProvider.Block(block))
            }
        }
    }

    internal data class WithAlignmentLine(val line: AlignmentLine) :
        SiblingsAlignedModifier() {
        override fun Density.modifyParentData(parentData: Any?): Any? {
            return ((parentData as? RowColumnParentData) ?: RowColumnParentData()).also {
                it.crossAxisAlignment =
                    CrossAxisAlignment.Relative(AlignmentLineProvider.Value(line))
            }
        }
    }
}

internal data class HorizontalGravityModifier(
    val horizontal: Alignment.Horizontal
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): RowColumnParentData {
        return ((parentData as? RowColumnParentData) ?: RowColumnParentData()).also {
            it.crossAxisAlignment = CrossAxisAlignment.horizontal(horizontal)
        }
    }
}

internal data class VerticalGravityModifier(
    val vertical: Alignment.Vertical
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): RowColumnParentData {
        return ((parentData as? RowColumnParentData) ?: RowColumnParentData()).also {
            it.crossAxisAlignment = CrossAxisAlignment.vertical(vertical)
        }
    }
}

/**
 * Parent data associated with children.
 */
internal data class RowColumnParentData(
    var weight: Float = 0f,
    var fill: Boolean = true,
    var crossAxisAlignment: CrossAxisAlignment? = null
)

/**
 * Provides the alignment line.
 */
internal sealed class AlignmentLineProvider {
    abstract fun calculateAlignmentLinePosition(placeable: Placeable): IntPx?
    data class Block(val lineProviderBlock: (Measured) -> IntPx) : AlignmentLineProvider() {
        override fun calculateAlignmentLinePosition(
            placeable: Placeable
        ): IntPx? {
            return lineProviderBlock(Measured(placeable))
        }
    }

    data class Value(val line: AlignmentLine) : AlignmentLineProvider() {
        override fun calculateAlignmentLinePosition(placeable: Placeable): IntPx? {
            return placeable[line]
        }
    }
}

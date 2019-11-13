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

import androidx.annotation.FloatRange
import androidx.compose.Composable
import androidx.ui.core.Alignment
import androidx.ui.core.AlignmentLine
import androidx.ui.core.Constraints
import androidx.ui.core.DensityScope
import androidx.ui.core.HorizontalAlignmentLine
import androidx.ui.core.IntPx
import androidx.ui.core.IntPxPosition
import androidx.ui.core.IntPxSize
import androidx.ui.core.Placeable
import androidx.ui.core.ipx
import androidx.ui.core.max
import androidx.ui.core.IntrinsicMeasurable
import androidx.ui.core.IntrinsicMeasureBlock
import androidx.ui.core.Layout
import androidx.ui.core.LayoutModifier
import androidx.ui.core.Measurable
import androidx.ui.core.Modifier
import androidx.ui.core.ParentData
import androidx.ui.core.VerticalAlignmentLine
import androidx.ui.core.isFinite
import androidx.ui.core.px
import androidx.ui.core.round
import androidx.ui.core.toPx

/**
 * Collects information about the children of a [FlexColumn] or [FlexColumn]
 * when its body is executed with a [FlexChildren] instance as argument.
 */
class FlexChildren internal constructor() {
    internal val childrenList = mutableListOf<@Composable() () -> Unit>()
    fun expanded(@FloatRange(from = 0.0) flex: Float, children: @Composable() () -> Unit) {
        if (flex < 0) {
            throw IllegalArgumentException("flex must be >= 0")
        }
        childrenList += {
            ParentData(
                data = FlexChildProperties(flex = flex, fit = FlexFit.Tight),
                children = children
            )
        }
    }

    fun flexible(@FloatRange(from = 0.0) flex: Float, children: @Composable() () -> Unit) {
        if (flex < 0) {
            throw IllegalArgumentException("flex must be >= 0")
        }
        childrenList += {
            ParentData(
                data = FlexChildProperties(flex = flex, fit = FlexFit.Loose),
                children = children
            )
        }
    }

    fun inflexible(children: @Composable() () -> Unit) {
        childrenList += @Composable {
            ParentData(
                data = FlexChildProperties(flex = 0f, fit = FlexFit.Loose),
                children = children
            )
        }
    }
}

/**
 * A composable that places its children in a horizontal sequence, assigning children widths
 * according to their flex weights.
 *
 * [FlexRow] children can be:
 * - [inflexible] meaning that the child is not flex, and it should be measured first
 * to determine its size, before the expanded and flexible children are measured
 * - [expanded] meaning that the child is flexible, and it should be assigned a width according
 * to its flex weight relative to its flexible children. The child is forced to occupy the
 * entire width assigned by the parent
 * - [flexible] similar to [expanded], but the child can leave unoccupied width.
 *
 * Example usage:
 *
 * @sample androidx.ui.layout.samples.SimpleFlexRow
 *
 * @param mainAxisAlignment The alignment of the layout's children in main axis direction.
 * Default is [MainAxisAlignment.Start].
 * @param crossAxisAlignment The alignment of the layout's children in cross axis direction.
 * Default is [CrossAxisAlignment.Start].
 * @param crossAxisSize The size of the layout in the cross axis dimension.
 * Default is [LayoutSize.Wrap].
 */
@Composable
fun FlexRow(
    modifier: Modifier = Modifier.None,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.Start,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.Start,
    crossAxisSize: LayoutSize = LayoutSize.Wrap,
    block: FlexChildren.() -> Unit
) {
    Flex(
        orientation = LayoutOrientation.Horizontal,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisAlignment = crossAxisAlignment,
        crossAxisSize = crossAxisSize,
        modifier = modifier,
        block = block
    )
}

/**
 * A composable that places its children in a vertical sequence, assigning children heights
 * according to their flex weights.
 *
 * [FlexColumn] children can be:
 * - [inflexible] meaning that the child is not flex, and it should be measured first
 * to determine its size, before the expanded and flexible children are measured
 * - [expanded] meaning that the child is flexible, and it should be assigned a
 * height according to its flex weight relative to its flexible children. The child is forced
 * to occupy the entire height assigned by the parent
 * - [flexible] similar to [expanded], but the child can leave unoccupied height.
 *
 * Example usage:
 *
 * @sample androidx.ui.layout.samples.SimpleFlexColumn
 *
 * @param mainAxisAlignment The alignment of the layout's children in main axis direction.
 * Default is [MainAxisAlignment.Start].
 * @param crossAxisAlignment The alignment of the layout's children in cross axis direction.
 * Default is [CrossAxisAlignment.Start].
 * @param crossAxisSize The size of the layout in the cross axis dimension.
 * Default is [LayoutSize.Wrap].
 */
@Composable
fun FlexColumn(
    modifier: Modifier = Modifier.None,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.Start,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.Start,
    crossAxisSize: LayoutSize = LayoutSize.Wrap,
    block: FlexChildren.() -> Unit
) {
    Flex(
        orientation = LayoutOrientation.Vertical,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisAlignment = crossAxisAlignment,
        crossAxisSize = crossAxisSize,
        modifier = modifier,
        block = block
    )
}

/**
 * A FlexScope provides a scope for Inflexible/Flexible functions.
 */
@LayoutScopeMarker
sealed class FlexScope {
    /**
     * A layout modifier within a [Column] or [Row] that makes the target component flexible.
     * It will be assigned a space according to its flex weight relative to the flexible siblings.
     * When [tight] is set to true, the target component is forced to occupy the entire space
     * assigned to it by the parent. [Flexible] children will be measured after all the
     * [Inflexible] ones have been measured, in order to divide the unclaimed space between
     * them.
     */
    fun Flexible(flex: Float, tight: Boolean = true): LayoutModifier =
        if (tight) {
            FlexModifier(FlexChildProperties(flex, FlexFit.Tight))
        } else {
            FlexModifier(FlexChildProperties(flex, FlexFit.Loose))
        }

    /**
     * A layout modifier within a [Column] or [Row] that makes the target component inflexible.
     * All [Inflexible] children will be measured before the [Flexible] ones. They will be
     * measured in the order they appear, without min constraints and with max constraints in
     * the main direction of the layout (maxHeight for Column and maxWidth for Row) such that
     * the sum of the space occupied by inflexible children will not exceed the incoming constraint
     * of the [Column] or [Row]: for example the first child of a [Column] will be measured with
     * maxHeight = column's maxHeight; the second child will be measured with maxHeight = column's
     * maxHeight - first child's height, and so on.
     */
    val Inflexible: LayoutModifier = inflexibleModifier

    internal companion object {
        val inflexibleModifier: LayoutModifier = FlexModifier(
            FlexChildProperties(0f, FlexFit.Loose)
        )
    }

    /**
     * A layout modifier within a [Column] or [Row] that positions target component in a
     * perpendicular direction according to the [AlignmentLine] which is provided through the
     * [alignmentLineBlock].
     * If target component is the only component with the specified RelativeToSiblings modifier
     * within a Column or Row, then the component will be positioned using
     * [ColumnScope.Gravity.Start] in Column or [RowScope.Gravity.Top] in Row respectively.
     *
     * Example usage:
     * @sample androidx.ui.layout.samples.SimpleRelativeToSiblings
     */
    @Suppress("unused")
    fun Gravity.RelativeToSiblings(alignmentLineBlock: (Placeable) -> IntPx): LayoutModifier =
        SiblingsAlignedModifier.WithAlignmentLineBlock(alignmentLineBlock)
}

/**
 * A ColumnScope provides a scope for the children of a [Column].
 */
@Suppress("unused") // Note: Gravity object provides a scope only but is never used itself
class ColumnScope internal constructor() : FlexScope() {
    /**
     * A layout modifier within a Column that positions target component in a horizontal direction
     * so that its start edge is aligned to the start edge of the horizontal axis.
     */
    // TODO: Consider ltr/rtl.
    val Gravity.Start: LayoutModifier get() = StartGravityModifier
    /**
     * A layout modifier within a Column that positions target component in a horizontal direction
     * so that its center is in the middle of the horizontal axis.
     */
    val Gravity.Center: LayoutModifier get() = CenterGravityModifier
    /**
     * A layout modifier within a Column that positions target component in a horizontal direction
     * so that its end edge is aligned to the end edge of the horizontal axis.
     */
    val Gravity.End: LayoutModifier get() = EndGravityModifier
    /**
     * A layout modifier within a [Column] that positions target component in a perpendicular
     * direction according to the [AlignmentLine].
     * If target component is the only component within a Column with the specified
     * RelativeToSiblings modifier, or if the provided alignment line is not defined for the
     * component, the component will be positioned using [Gravity.Start].
     *
     * Example usage:
     *
     * @sample androidx.ui.layout.samples.SimpleRelativeToSiblingsInColumn
     */
    fun Gravity.RelativeToSiblings(alignmentLine: VerticalAlignmentLine): LayoutModifier =
        SiblingsAlignedModifier.WithAlignmentLine(alignmentLine)

    internal companion object {
        val StartGravityModifier: LayoutModifier = GravityModifier(CrossAxisAlignment.Start)
        val CenterGravityModifier: LayoutModifier = GravityModifier(CrossAxisAlignment.Center)
        val EndGravityModifier: LayoutModifier = GravityModifier(CrossAxisAlignment.End)
    }
}

/**
 * A RowScope provides a scope for the children of a [Row].
 */
@Suppress("unused") // Note: Gravity object provides a scope only but is never used itself
class RowScope internal constructor() : FlexScope() {
    /**
     * A layout modifier within a Row that positions target component in a vertical direction
     * so that its top edge is aligned to the top edge of the vertical axis.
     */
    val Gravity.Top: LayoutModifier get() = TopGravityModifier
    /**
     * A layout modifier within a Row that positions target component in a vertical direction
     * so that its center is in the middle of the vertical axis.
     */
    val Gravity.Center: LayoutModifier get() = CenterGravityModifier
    /**
     * A layout modifier within a Row that positions target component in a vertical direction
     * so that its bottom edge is aligned to the bottom edge of the vertical axis.
     */
    val Gravity.Bottom: LayoutModifier get() = BottomGravityModifier
    /**
     * A layout modifier within a [Row] that positions target component in a perpendicular
     * direction according to the [AlignmentLine].
     * If target component is the only component within a Row with the specified
     * RelativeToSiblings modifier, or if the provided alignment line is not defined for the
     * component, the component will be positioned using [Gravity.Top].
     *
     * Example usage:
     * @sample androidx.ui.layout.samples.SimpleRelativeToSiblingsInRow
     */
    fun Gravity.RelativeToSiblings(alignmentLine: HorizontalAlignmentLine): LayoutModifier =
        SiblingsAlignedModifier.WithAlignmentLine(alignmentLine)

    internal companion object {
        val TopGravityModifier: LayoutModifier = GravityModifier(CrossAxisAlignment.Start)
        val CenterGravityModifier: LayoutModifier = GravityModifier(CrossAxisAlignment.Center)
        val BottomGravityModifier: LayoutModifier = GravityModifier(CrossAxisAlignment.End)
    }
}

/**
 * A composable that places its children in a horizontal sequence and is able to assign them widths
 * according to their flex weights provided through [androidx.ui.layout.FlexScope.Flexible]
 * modifier.
 * If [androidx.ui.layout.FlexScope.Inflexible] or no modifier is provided, the child will be
 * treated as inflexible, and will be sized to its preferred width.
 *
 * Example usage:
 *
 * @sample androidx.ui.layout.samples.SimpleRow
 *
 * @param mainAxisAlignment The alignment of the layout's children in main axis direction.
 * Default is [MainAxisAlignment.Start].
 * @param crossAxisAlignment The alignment of the layout's children in cross axis direction.
 * Default is [CrossAxisAlignment.Start].
 */
@Composable
fun Row(
    modifier: Modifier = Modifier.None,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.Start,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.Start,
    block: @Composable() RowScope.() -> Unit
) {
    FlexLayout(
        orientation = LayoutOrientation.Horizontal,
        modifier = modifier,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisAlignment = crossAxisAlignment,
        crossAxisSize = LayoutSize.Wrap,
        layoutChildren = { RowScope().block() }
    )
}

/**
 * A composable that places its children in a vertical sequence and is able to assign them heights
 * according to their flex weights provided through [androidx.ui.layout.FlexScope.Flexible]
 * modifiers.
 * If [androidx.ui.layout.FlexScope.Inflexible] or no modifier is provided, the child will be
 * treated as inflexible, and will be sized to its preferred height.
 *
 * Example usage:
 *
 * @sample androidx.ui.layout.samples.SimpleColumn
 *
 * @param mainAxisAlignment The alignment of the layout's children in main axis direction.
 * Default is [MainAxisAlignment.Start].
 * @param crossAxisAlignment The alignment of the layout's children in cross axis direction.
 * Default is [CrossAxisAlignment.Start].
 */
@Composable
fun Column(
    modifier: Modifier = Modifier.None,
    mainAxisAlignment: MainAxisAlignment = MainAxisAlignment.Start,
    crossAxisAlignment: CrossAxisAlignment = CrossAxisAlignment.Start,
    block: @Composable() ColumnScope.() -> Unit
) {
    FlexLayout(
        orientation = LayoutOrientation.Vertical,
        modifier = modifier,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisAlignment = crossAxisAlignment,
        crossAxisSize = LayoutSize.Wrap,
        layoutChildren = { ColumnScope().block() }
    )
}

internal enum class FlexFit {
    Tight,
    Loose
}

internal enum class LayoutOrientation {
    Horizontal,
    Vertical
}

/**
 * Used to specify how a layout chooses its own size when multiple behaviors are possible.
 */
enum class LayoutSize {
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
enum class MainAxisAlignment(internal val aligner: Aligner) {
    /**
     * Place children such that they are as close as possible to the middle of the main axis.
     */
    Center(MainAxisCenterAligner()),
    /**
     * Place children such that they are as close as possible to the start of the main axis.
     */
    // TODO(popam): Consider rtl directionality.
    Start(MainAxisStartAligner()),
    /**
     * Place children such that they are as close as possible to the end of the main axis.
     */
    End(MainAxisEndAligner()),
    /**
     * Place children such that they are spaced evenly across the main axis, including free
     * space before the first child and after the last child.
     */
    SpaceEvenly(MainAxisSpaceEvenlyAligner()),
    /**
     * Place children such that they are spaced evenly across the main axis, without free
     * space before the first child or after the last child.
     */
    SpaceBetween(MainAxisSpaceBetweenAligner()),
    /**
     * Place children such that they are spaced evenly across the main axis, including free
     * space before the first child and after the last child, but half the amount of space
     * existing otherwise between two consecutive children.
     */
    SpaceAround(MainAxisSpaceAroundAligner());

    internal interface Aligner {
        fun align(totalSize: IntPx, size: List<IntPx>): List<IntPx>
    }

    private class MainAxisCenterAligner : Aligner {
        override fun align(totalSize: IntPx, size: List<IntPx>): List<IntPx> {
            val consumedSize = size.fold(0.ipx) { a, b -> a + b }
            val positions = mutableListOf<IntPx>()
            var current = (totalSize - consumedSize).toPx() / 2
            size.forEach {
                positions.add(current.round())
                current += it
            }
            return positions
        }
    }

    private class MainAxisStartAligner : Aligner {
        override fun align(totalSize: IntPx, size: List<IntPx>): List<IntPx> {
            val positions = mutableListOf<IntPx>()
            var current = 0.ipx
            size.forEach {
                positions.add(current)
                current += it
            }
            return positions
        }
    }

    private class MainAxisEndAligner : Aligner {
        override fun align(totalSize: IntPx, size: List<IntPx>): List<IntPx> {
            val consumedSize = size.fold(0.ipx) { a, b -> a + b }
            val positions = mutableListOf<IntPx>()
            var current = totalSize - consumedSize
            size.forEach {
                positions.add(current)
                current += it
            }
            return positions
        }
    }

    private class MainAxisSpaceEvenlyAligner : Aligner {
        override fun align(totalSize: IntPx, size: List<IntPx>): List<IntPx> {
            val consumedSize = size.fold(0.ipx) { a, b -> a + b }
            val gapSize = (totalSize - consumedSize).toPx() / (size.size + 1)
            val positions = mutableListOf<IntPx>()
            var current = gapSize
            size.forEach {
                positions.add(current.round())
                current += it.toPx() + gapSize
            }
            return positions
        }
    }

    private class MainAxisSpaceBetweenAligner : Aligner {
        override fun align(totalSize: IntPx, size: List<IntPx>): List<IntPx> {
            val consumedSize = size.fold(0.ipx) { a, b -> a + b }
            val gapSize = if (size.size > 1) {
                (totalSize - consumedSize).toPx() / (size.size - 1)
            } else {
                0.px
            }
            val positions = mutableListOf<IntPx>()
            var current = 0.px
            size.forEach {
                positions.add(current.round())
                current += it.toPx() + gapSize
            }
            return positions
        }
    }

    private class MainAxisSpaceAroundAligner : Aligner {
        override fun align(totalSize: IntPx, size: List<IntPx>): List<IntPx> {
            val consumedSize = size.fold(0.ipx) { a, b -> a + b }
            val gapSize = if (size.isNotEmpty()) {
                (totalSize - consumedSize).toPx() / size.size
            } else {
                0.px
            }
            val positions = mutableListOf<IntPx>()
            var current = gapSize / 2
            size.forEach {
                positions.add(current.round())
                current += it.toPx() + gapSize
            }
            return positions
        }
    }
}

/**
 * Used to specify the alignment of a layout's children, in cross axis direction.
 */
// TODO(popam): refine this API surface with modifiers - add type safety for alignment orientation.
class CrossAxisAlignment private constructor(
    internal val alignmentLineProvider: AlignmentLineProvider? = null
) {
    companion object {
        /**
         * Place children such that their center is in the middle of the cross axis.
         */
        val Center = CrossAxisAlignment(null)
        /**
         * Place children such that their start edge is aligned to the start edge of the cross
         * axis. TODO(popam): Consider rtl directionality.
         */
        val Start = CrossAxisAlignment(null)
        /**
         * Place children such that their end edge is aligned to the end edge of the cross
         * axis. TODO(popam): Consider rtl directionality.
         */
        val End = CrossAxisAlignment(null)
        /**
         * Force children to occupy the entire cross axis space.
         */
        val Stretch = CrossAxisAlignment(null)
        /**
         * Align children by their baseline.
         */
        fun AlignmentLine(alignmentLine: AlignmentLine) =
            CrossAxisAlignment(AlignmentLineProvider.Value(alignmentLine))
        /**
         * Align children relative to their siblings using the alignment line provided as a
         * parameter using [AlignmentLineProvider].
         */
        internal fun Relative(alignmentLineProvider: AlignmentLineProvider) =
            CrossAxisAlignment(alignmentLineProvider)
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

private val IntrinsicMeasurable.flex: Float
    get() = (parentData as? FlexChildProperties)?.flex ?: 0f

private val IntrinsicMeasurable.fit: FlexFit
    get() = (parentData as? FlexChildProperties)?.fit ?: FlexFit.Loose

private val IntrinsicMeasurable.crossAxisAlignment: CrossAxisAlignment?
    get() = (parentData as? FlexChildProperties)?.crossAxisAlignment

@Composable
private fun Flex(
    orientation: LayoutOrientation,
    modifier: Modifier = Modifier.None,
    mainAxisAlignment: MainAxisAlignment,
    crossAxisSize: LayoutSize,
    crossAxisAlignment: CrossAxisAlignment,
    block: FlexChildren.() -> Unit
) {
    val flexChildren: @Composable() () -> Unit = with(FlexChildren()) {
        block()
        val composable = @Composable {
            childrenList.forEach { it() }
        }
        composable
    }
    FlexLayout(
        orientation = orientation,
        modifier = modifier,
        mainAxisAlignment = mainAxisAlignment,
        crossAxisSize = crossAxisSize,
        crossAxisAlignment = crossAxisAlignment,
        layoutChildren = flexChildren
    )
}

/**
 * Layout model that places its children in a horizontal or vertical sequence, according to the
 * specified orientation, while also looking at the flex weights of the children.
 */
@Composable
private fun FlexLayout(
    orientation: LayoutOrientation,
    modifier: Modifier = Modifier.None,
    mainAxisAlignment: MainAxisAlignment,
    crossAxisSize: LayoutSize,
    crossAxisAlignment: CrossAxisAlignment,
    layoutChildren: @Composable() () -> Unit
) {
    fun Placeable.mainAxisSize() =
        if (orientation == LayoutOrientation.Horizontal) width else height
    fun Placeable.crossAxisSize() =
        if (orientation == LayoutOrientation.Horizontal) height else width

    Layout(
        layoutChildren,
        modifier = modifier,
        minIntrinsicWidthMeasureBlock = MinIntrinsicWidthMeasureBlock(orientation),
        minIntrinsicHeightMeasureBlock = MinIntrinsicHeightMeasureBlock(orientation),
        maxIntrinsicWidthMeasureBlock = MaxIntrinsicWidthMeasureBlock(orientation),
        maxIntrinsicHeightMeasureBlock = MaxIntrinsicHeightMeasureBlock(orientation)
    ) { children, outerConstraints ->
        val constraints = OrientationIndependentConstraints(outerConstraints, orientation)

        var totalFlex = 0f
        var inflexibleSpace = IntPx.Zero
        var crossAxisSpace = IntPx.Zero
        var beforeCrossAxisAlignmentLine = IntPx.Zero
        var afterCrossAxisAlignmentLine = IntPx.Zero

        val placeables = arrayOfNulls<Placeable>(children.size)
        // First measure children with zero flex.
        for (i in 0 until children.size) {
            val child = children[i]
            val flex = child.flex

            if (flex > 0f) {
                totalFlex += child.flex
            } else {
                val placeable = child.measure(
                    // Ask for preferred main axis size.
                    constraints.copy(
                        mainAxisMin = 0.ipx,
                        mainAxisMax = constraints.mainAxisMax - inflexibleSpace
                    ).let {
                        if (child.crossAxisAlignment == CrossAxisAlignment.Stretch ||
                            crossAxisAlignment == CrossAxisAlignment.Stretch
                        ) {
                            it.stretchCrossAxis()
                        } else {
                            it.copy(crossAxisMin = IntPx.Zero)
                        }
                    }.toBoxConstraints(orientation)
                )
                inflexibleSpace += placeable.mainAxisSize()
                crossAxisSpace = max(crossAxisSpace, placeable.crossAxisSize())

                val lineProvider = children[i].crossAxisAlignment?.alignmentLineProvider
                    ?: crossAxisAlignment.alignmentLineProvider
                if (lineProvider != null) {
                    val alignmentLinePosition = when (lineProvider) {
                        is AlignmentLineProvider.Block -> lineProvider.lineProviderBlock(placeable)
                        is AlignmentLineProvider.Value -> placeable[lineProvider.line]
                    }
                    beforeCrossAxisAlignmentLine = max(
                        beforeCrossAxisAlignmentLine,
                        alignmentLinePosition ?: 0.ipx
                    )
                    afterCrossAxisAlignmentLine = max(
                        afterCrossAxisAlignmentLine,
                        placeable.crossAxisSize() -
                                (alignmentLinePosition ?: placeable.crossAxisSize())
                    )
                }
                placeables[i] = placeable
            }
        }

        // Then measure the rest according to their flexes in the remaining main axis space.
        val targetSpace = if (totalFlex > 0f && constraints.mainAxisMax.isFinite()) {
            constraints.mainAxisMax
        } else {
            constraints.mainAxisMin
        }

        var flexibleSpace = IntPx.Zero

        for (i in 0 until children.size) {
            val child = children[i]
            val flex = child.flex
            if (flex > 0f) {
                val childMainAxisSize = max(
                    IntPx.Zero,
                    (targetSpace - inflexibleSpace) * child.flex / totalFlex
                )
                val placeable = child.measure(
                    OrientationIndependentConstraints(
                        if (child.fit == FlexFit.Tight && childMainAxisSize.isFinite()) {
                            childMainAxisSize
                        } else {
                            IntPx.Zero
                        },
                        childMainAxisSize,
                        if (child.crossAxisAlignment == CrossAxisAlignment.Stretch ||
                            crossAxisAlignment == CrossAxisAlignment.Stretch
                        ) {
                            constraints.crossAxisMax
                        } else {
                            IntPx.Zero
                        },
                        constraints.crossAxisMax
                    ).toBoxConstraints(orientation)
                )
                flexibleSpace += placeable.mainAxisSize()
                crossAxisSpace = max(crossAxisSpace, placeable.crossAxisSize())
                placeables[i] = placeable
            }
        }

        // Compute the Flex size and position the children.
        val mainAxisLayoutSize = if (totalFlex > 0f && constraints.mainAxisMax.isFinite()) {
            constraints.mainAxisMax
        } else {
            max(inflexibleSpace + flexibleSpace, constraints.mainAxisMin)
        }
        val crossAxisLayoutSize = if (constraints.crossAxisMax.isFinite() &&
            crossAxisSize == LayoutSize.Expand
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
            val mainAxisPositions = mainAxisAlignment.aligner
                .align(mainAxisLayoutSize, childrenMainAxisSize)
            placeables.forEachIndexed { index, placeable ->
                placeable!!
                val childCrossAlignment = children[index].crossAxisAlignment ?: crossAxisAlignment
                val crossAxis = when (childCrossAlignment) {
                    CrossAxisAlignment.Start -> IntPx.Zero
                    CrossAxisAlignment.Stretch -> IntPx.Zero
                    CrossAxisAlignment.End -> {
                        crossAxisLayoutSize - placeable.crossAxisSize()
                    }
                    CrossAxisAlignment.Center -> {
                        Alignment.Center.align(
                            IntPxSize(
                                mainAxisLayoutSize - placeable.mainAxisSize(),
                                crossAxisLayoutSize - placeable.crossAxisSize()
                            )
                        ).y
                    }
                    else -> {
                        val provider = children[index].crossAxisAlignment?.alignmentLineProvider
                            ?: crossAxisAlignment.alignmentLineProvider
                        val alignmentLinePosition = when (provider) {
                            is AlignmentLineProvider.Block -> provider.lineProviderBlock(placeable)
                            is AlignmentLineProvider.Value -> placeable[provider.line]
                            else -> null
                        }
                        if (alignmentLinePosition != null) {
                            beforeCrossAxisAlignmentLine - alignmentLinePosition
                        } else {
                            IntPx.Zero
                        }
                    }
                }
                if (orientation == LayoutOrientation.Horizontal) {
                    placeable.place(mainAxisPositions[index], crossAxis)
                } else {
                    placeable.place(crossAxis, mainAxisPositions[index])
                }
            }
        }
    }
}

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
    val HorizontalMinWidth: IntrinsicMeasureBlock = { measurables, availableHeight ->
        intrinsicSize(
            measurables,
            { h -> minIntrinsicWidth(h) },
            { w -> maxIntrinsicHeight(w) },
            availableHeight,
            LayoutOrientation.Horizontal,
            LayoutOrientation.Horizontal
        )
    }
    val VerticalMinWidth: IntrinsicMeasureBlock = { measurables, availableHeight ->
        intrinsicSize(
            measurables,
            { h -> minIntrinsicWidth(h) },
            { w -> maxIntrinsicHeight(w) },
            availableHeight,
            LayoutOrientation.Vertical,
            LayoutOrientation.Horizontal
        )
    }
    val HorizontalMinHeight: IntrinsicMeasureBlock = { measurables, availableWidth ->
        intrinsicSize(
            measurables,
            { w -> minIntrinsicHeight(w) },
            { h -> maxIntrinsicWidth(h) },
            availableWidth,
            LayoutOrientation.Horizontal,
            LayoutOrientation.Vertical
        )
    }
    val VerticalMinHeight: IntrinsicMeasureBlock = { measurables, availableWidth ->
        intrinsicSize(
            measurables,
            { w -> minIntrinsicHeight(w) },
            { h -> maxIntrinsicWidth(h) },
            availableWidth,
            LayoutOrientation.Vertical,
            LayoutOrientation.Vertical
        )
    }
    val HorizontalMaxWidth: IntrinsicMeasureBlock = { measurables, availableHeight ->
        intrinsicSize(
            measurables,
            { h -> maxIntrinsicWidth(h) },
            { w -> maxIntrinsicHeight(w) },
            availableHeight,
            LayoutOrientation.Horizontal,
            LayoutOrientation.Horizontal
        )
    }
    val VerticalMaxWidth: IntrinsicMeasureBlock = { measurables, availableHeight ->
        intrinsicSize(
            measurables,
            { h -> maxIntrinsicWidth(h) },
            { w -> maxIntrinsicHeight(w) },
            availableHeight,
            LayoutOrientation.Vertical,
            LayoutOrientation.Horizontal
        )
    }
    val HorizontalMaxHeight: IntrinsicMeasureBlock = { measurables, availableWidth ->
        intrinsicSize(
            measurables,
            { w -> maxIntrinsicHeight(w) },
            { h -> maxIntrinsicWidth(h) },
            availableWidth,
            LayoutOrientation.Horizontal,
            LayoutOrientation.Vertical
        )
    }
    val VerticalMaxHeight: IntrinsicMeasureBlock = { measurables, availableWidth ->
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
    flexOrientation: LayoutOrientation,
    intrinsicOrientation: LayoutOrientation
) = if (flexOrientation == intrinsicOrientation) {
    intrinsicMainAxisSize(children, intrinsicMainSize, crossAxisAvailable)
} else {
    intrinsicCrossAxisSize(children, intrinsicCrossSize, intrinsicMainSize, crossAxisAvailable)
}

private fun intrinsicMainAxisSize(
    children: List<IntrinsicMeasurable>,
    mainAxisSize: IntrinsicMeasurable.(IntPx) -> IntPx,
    crossAxisAvailable: IntPx
): IntPx {
    var maxFlexibleSpace = 0.ipx
    var inflexibleSpace = 0.ipx
    var totalFlex = 0f
    children.forEach { child ->
        val flex = child.flex
        val size = child.mainAxisSize(crossAxisAvailable)
        if (flex == 0f) {
            inflexibleSpace += size
        } else if (flex > 0f) {
            totalFlex += flex
            maxFlexibleSpace = max(maxFlexibleSpace, size / flex)
        }
    }
    return maxFlexibleSpace * totalFlex + inflexibleSpace
}

private fun intrinsicCrossAxisSize(
    children: List<IntrinsicMeasurable>,
    mainAxisSize: IntrinsicMeasurable.(IntPx) -> IntPx,
    crossAxisSize: IntrinsicMeasurable.(IntPx) -> IntPx,
    mainAxisAvailable: IntPx
): IntPx {
    var inflexibleSpace = 0.ipx
    var crossAxisMax = 0.ipx
    var totalFlex = 0f
    children.forEach { child ->
        val flex = child.flex
        if (flex == 0f) {
            val mainAxisSpace = child.mainAxisSize(IntPx.Infinity)
            inflexibleSpace += mainAxisSpace
            crossAxisMax = max(crossAxisMax, child.crossAxisSize(mainAxisSpace))
        } else if (flex > 0f) {
            totalFlex += flex
        }
    }

    val flexSection = if (totalFlex == 0f) {
        IntPx.Zero
    } else {
        max(mainAxisAvailable - inflexibleSpace, IntPx.Zero) / totalFlex
    }

    children.forEach { child ->
        if (child.flex > 0f) {
            crossAxisMax = max(crossAxisMax, child.crossAxisSize(flexSection * child.flex))
        }
    }
    return crossAxisMax
}

private data class FlexModifier(val flexProperties: FlexChildProperties) : LayoutModifier {
    override fun DensityScope.modifyConstraints(constraints: Constraints): Constraints {
        return constraints
    }

    override fun DensityScope.modifySize(
        constraints: Constraints,
        childSize: IntPxSize
    ): IntPxSize {
        return childSize
    }

    override fun DensityScope.minIntrinsicWidthOf(measurable: Measurable, height: IntPx): IntPx {
        return measurable.minIntrinsicWidth(height)
    }

    override fun DensityScope.maxIntrinsicWidthOf(measurable: Measurable, height: IntPx): IntPx {
        return measurable.maxIntrinsicWidth(height)
    }

    override fun DensityScope.minIntrinsicHeightOf(measurable: Measurable, width: IntPx): IntPx {
        return measurable.minIntrinsicHeight(width)
    }

    override fun DensityScope.maxIntrinsicHeightOf(measurable: Measurable, width: IntPx): IntPx {
        return measurable.maxIntrinsicHeight(width)
    }

    override fun DensityScope.modifyPosition(
        childPosition: IntPxPosition,
        childSize: IntPxSize,
        containerSize: IntPxSize
    ): IntPxPosition {
        return childPosition
    }

    override fun DensityScope.modifyAlignmentLine(line: AlignmentLine, value: IntPx?): IntPx? {
        return value
    }

    override fun DensityScope.modifyParentData(parentData: Any?): FlexChildProperties {
        return if (parentData is FlexChildProperties) {
            if (parentData.flex == null || parentData.fit == null) {
                parentData.flex = flexProperties.flex
                parentData.fit = flexProperties.fit
            }
            parentData
        } else {
            FlexChildProperties(flex = flexProperties.flex, fit = flexProperties.fit)
        }
    }
}

private sealed class SiblingsAlignedModifier : LayoutModifier {
    override fun DensityScope.modifyConstraints(constraints: Constraints) = constraints

    override fun DensityScope.modifySize(
        constraints: Constraints,
        childSize: IntPxSize
    ) = childSize

    override fun DensityScope.minIntrinsicWidthOf(measurable: Measurable, height: IntPx) =
        measurable.minIntrinsicWidth(height)
    override fun DensityScope.maxIntrinsicWidthOf(measurable: Measurable, height: IntPx) =
        measurable.maxIntrinsicWidth(height)

    override fun DensityScope.minIntrinsicHeightOf(measurable: Measurable, width: IntPx) =
        measurable.minIntrinsicHeight(width)

    override fun DensityScope.maxIntrinsicHeightOf(measurable: Measurable, width: IntPx) =
        measurable.maxIntrinsicHeight(width)

    override fun DensityScope.modifyPosition(
        childPosition: IntPxPosition,
        childSize: IntPxSize,
        containerSize: IntPxSize
    ) = childPosition

    override fun DensityScope.modifyAlignmentLine(line: AlignmentLine, value: IntPx?) = value

    abstract override fun DensityScope.modifyParentData(parentData: Any?): Any?

    internal data class WithAlignmentLineBlock(val block: (Placeable) -> IntPx) :
        SiblingsAlignedModifier() {
        override fun DensityScope.modifyParentData(parentData: Any?): Any? {
            return ((parentData as? FlexChildProperties) ?: FlexChildProperties()).also {
                if (it.crossAxisAlignment == null) {
                    it.crossAxisAlignment =
                        CrossAxisAlignment.Relative(AlignmentLineProvider.Block(block))
                }
            }
        }
    }

    internal data class WithAlignmentLine(val line: AlignmentLine) :
        SiblingsAlignedModifier() {
        override fun DensityScope.modifyParentData(parentData: Any?): Any? {
            return ((parentData as? FlexChildProperties) ?: FlexChildProperties()).also {
                if (it.crossAxisAlignment == null) {
                    it.crossAxisAlignment =
                        CrossAxisAlignment.Relative(AlignmentLineProvider.Value(line))
                }
            }
        }
    }
}

private data class GravityModifier(val alignment: CrossAxisAlignment) : LayoutModifier {
    override fun DensityScope.modifyConstraints(constraints: Constraints) = constraints

    override fun DensityScope.modifySize(
        constraints: Constraints,
        childSize: IntPxSize
    ) = childSize

    override fun DensityScope.minIntrinsicWidthOf(measurable: Measurable, height: IntPx) =
        measurable.minIntrinsicWidth(height)

    override fun DensityScope.maxIntrinsicWidthOf(measurable: Measurable, height: IntPx) =
        measurable.maxIntrinsicWidth(height)

    override fun DensityScope.minIntrinsicHeightOf(measurable: Measurable, width: IntPx) =
        measurable.minIntrinsicHeight(width)

    override fun DensityScope.maxIntrinsicHeightOf(measurable: Measurable, width: IntPx) =
        measurable.maxIntrinsicHeight(width)

    override fun DensityScope.modifyPosition(
        childPosition: IntPxPosition,
        childSize: IntPxSize,
        containerSize: IntPxSize
    ) = childPosition

    override fun DensityScope.modifyAlignmentLine(line: AlignmentLine, value: IntPx?) = value

    override fun DensityScope.modifyParentData(parentData: Any?): FlexChildProperties {
        return if (parentData is FlexChildProperties) {
            if (parentData.crossAxisAlignment == null) {
                parentData.crossAxisAlignment = alignment
            }
            parentData
        } else {
            FlexChildProperties(crossAxisAlignment = alignment)
        }
    }
}

/**
 * Parent data associated with children.
 */
private data class FlexChildProperties(
    var flex: Float? = null,
    var fit: FlexFit? = null,
    var crossAxisAlignment: CrossAxisAlignment? = null
)

/**
 * Provides the alignment line.
 */
internal sealed class AlignmentLineProvider {
    data class Block(val lineProviderBlock: (Placeable) -> IntPx) : AlignmentLineProvider()
    data class Value(val line: AlignmentLine) : AlignmentLineProvider()
}
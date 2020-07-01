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

package androidx.ui.core

import androidx.compose.Applier
import androidx.compose.Composable
import androidx.compose.ComposableContract
import androidx.compose.Composition
import androidx.compose.CompositionReference
import androidx.compose.ExperimentalComposeApi
import androidx.compose.Recomposer
import androidx.compose.Stable
import androidx.compose.compositionReference
import androidx.compose.currentComposer
import androidx.compose.emit
import androidx.compose.onDispose
import androidx.compose.remember
import androidx.compose.snapshots.Snapshot
import androidx.ui.core.LayoutNode.LayoutState
import androidx.ui.unit.Density
import androidx.ui.unit.Dp
import androidx.ui.unit.IntOffset
import androidx.ui.unit.IntSize
import androidx.ui.util.fastForEach
import kotlin.math.max

/**
 * [Layout] is the main core component for layout. It can be used to measure and position
 * zero or more children.
 *
 * Intrinsic measurement blocks define the intrinsic sizes of the current layout. These
 * can be queried by the parent in order to understand, in specific cases, what constraints
 * should the layout be measured with:
 * - [minIntrinsicWidthMeasureBlock] defines the minimum width this layout can take, given
 *   a specific height, such that the content of the layout will be painted correctly
 * - [minIntrinsicHeightMeasureBlock] defines the minimum height this layout can take, given
 *   a specific width, such that the content of the layout will be painted correctly
 * - [maxIntrinsicWidthMeasureBlock] defines the minimum width such that increasing it further
 *   will not decrease the minimum intrinsic height
 * - [maxIntrinsicHeightMeasureBlock] defines the minimum height such that increasing it further
 *   will not decrease the minimum intrinsic width
 *
 * For a composable able to define its content according to the incoming constraints,
 * see [WithConstraints].
 *
 * Example usage:
 * @sample androidx.ui.core.samples.LayoutWithProvidedIntrinsicsUsage
 *
 * @param children The children composable to be laid out.
 * @param modifier Modifiers to be applied to the layout.
 * @param minIntrinsicWidthMeasureBlock The minimum intrinsic width of the layout.
 * @param minIntrinsicHeightMeasureBlock The minimum intrinsic height of the layout.
 * @param maxIntrinsicWidthMeasureBlock The maximum intrinsic width of the layout.
 * @param maxIntrinsicHeightMeasureBlock The maximum intrinsic height of the layout.
 * @param measureBlock The block defining the measurement and positioning of the layout.
 *
 * @see Layout
 * @see WithConstraints
 */
@Composable
@OptIn(ExperimentalLayoutNodeApi::class)
/*inline*/ fun Layout(
    /*crossinline*/
    children: @Composable () -> Unit,
    /*crossinline*/
    minIntrinsicWidthMeasureBlock: IntrinsicMeasureBlock2,
    /*crossinline*/
    minIntrinsicHeightMeasureBlock: IntrinsicMeasureBlock2,
    /*crossinline*/
    maxIntrinsicWidthMeasureBlock: IntrinsicMeasureBlock2,
    /*crossinline*/
    maxIntrinsicHeightMeasureBlock: IntrinsicMeasureBlock2,
    modifier: Modifier = Modifier,
    /*crossinline*/
    measureBlock: MeasureBlock2
) {
    val measureBlocks = object : LayoutNode.MeasureBlocks {
        override fun measure(
            measureScope: MeasureScope,
            measurables: List<Measurable>,
            constraints: Constraints,
            layoutDirection: LayoutDirection
        ) = measureScope.measureBlock(measurables, constraints)
        override fun minIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ): Int {
            val receiver = IntrinsicsMeasureScope(intrinsicMeasureScope, layoutDirection)
            return receiver.minIntrinsicWidthMeasureBlock(measurables, h)
        }
        override fun minIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ): Int {
            val receiver = IntrinsicsMeasureScope(intrinsicMeasureScope, layoutDirection)
            return receiver.minIntrinsicHeightMeasureBlock(measurables, w)
        }
        override fun maxIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ): Int {
            val receiver = IntrinsicsMeasureScope(intrinsicMeasureScope, layoutDirection)
            return receiver.maxIntrinsicWidthMeasureBlock(measurables, h)
        }
        override fun maxIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ): Int {
            val receiver = IntrinsicsMeasureScope(intrinsicMeasureScope, layoutDirection)
            return receiver.maxIntrinsicHeightMeasureBlock(measurables, w)
        }
    }
    Layout(children, measureBlocks, modifier)
}

@Deprecated(
    "Use Layout() function with 'MeasureScope.(List<Measurable>, Constraints) -> MeasureScope" +
            ".MeasureResult' measure block and 'IntrinsicMeasureScope.(List<IntrinsicMeasurable>," +
            " Int) -> Int' intrinsic measure blocks instead.",
    ReplaceWith("Layout(children, {measurable, height -> minIntrinsicWidthMeasureBlock}, " +
            "{measurable, width -> minIntrinsicHeightMeasureBlock}, " +
            "{measurable, height -> maxIntrinsicWidthMeasureBlock}, " +
            "{measurable, width -> maxIntrinsicHeightMeasureBlock}," +
            " modifier, " +
            "{measurables, constraints -> measureBlock})"
    )
)
@Suppress("DEPRECATION")
@Composable
@OptIn(ExperimentalLayoutNodeApi::class)
/*inline*/ fun Layout(
    /*crossinline*/
    children: @Composable () -> Unit,
    /*crossinline*/
    minIntrinsicWidthMeasureBlock: IntrinsicMeasureBlock,
    /*crossinline*/
    minIntrinsicHeightMeasureBlock: IntrinsicMeasureBlock,
    /*crossinline*/
    maxIntrinsicWidthMeasureBlock: IntrinsicMeasureBlock,
    /*crossinline*/
    maxIntrinsicHeightMeasureBlock: IntrinsicMeasureBlock,
    modifier: Modifier = Modifier,
    /*crossinline*/
    measureBlock: MeasureBlock
) {
    val measureBlocks = object : LayoutNode.MeasureBlocks {
        override fun measure(
            measureScope: MeasureScope,
            measurables: List<Measurable>,
            constraints: Constraints,
            layoutDirection: LayoutDirection
        ) = measureScope.measureBlock(measurables, constraints, layoutDirection)
        override fun minIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.minIntrinsicWidthMeasureBlock(measurables, h, layoutDirection)
        override fun minIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.minIntrinsicHeightMeasureBlock(measurables, w, layoutDirection)
        override fun maxIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.maxIntrinsicWidthMeasureBlock(measurables, h, layoutDirection)
        override fun maxIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.maxIntrinsicHeightMeasureBlock(measurables, w, layoutDirection)
    }
    Layout(children, measureBlocks, modifier)
}

/**
 * [Layout] is the main core component for layout. It can be used to measure and position
 * zero or more children.
 *
 * The intrinsic measurements of this layout will be calculated by running the measureBlock,
 * while swapping measure calls with appropriate intrinsic measurements. Note that these
 * provided implementations will not be accurate in all cases - when this happens, the other
 * overload of [Layout] should be used to provide correct measurements.
 *
 * For a composable able to define its content according to the incoming constraints,
 * see [WithConstraints].
 *
 * Example usage:
 * @sample androidx.ui.core.samples.LayoutUsage
 *
 * @param children The children composable to be laid out.
 * @param modifier Modifiers to be applied to the layout.
 * @param measureBlock The block defining the measurement and positioning of the layout.
 *
 * @see Layout
 * @see WithConstraints
 */
@Composable
@OptIn(ExperimentalLayoutNodeApi::class)
/*inline*/ fun Layout(
    /*crossinline*/
    children: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /*noinline*/
    measureBlock: MeasureBlock2
) {

    val measureBlocks = remember(measureBlock) { MeasuringIntrinsicsMeasureBlocks(measureBlock) }
    Layout(children, measureBlocks, modifier)
}

@Deprecated(
    """Use Layout() function with
        'MeasureScope.(List<Measurable>, Constraints) -> MeasureScope.MeasureResult'
        measure block instead.""",
    ReplaceWith("Layout(children, modifier, {measurables, constraints -> measureBlock})")
)
@Suppress("DEPRECATION")
@Composable
@OptIn(ExperimentalLayoutNodeApi::class)
/*inline*/ fun Layout(
    /*crossinline*/
    children: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    /*noinline*/
    measureBlock: MeasureBlock
) {

    val measureBlocks = remember(measureBlock) { MeasuringIntrinsicsMeasureBlocks(measureBlock) }
    Layout(children, measureBlocks, modifier)
}

@ExperimentalLayoutNodeApi
/*@PublishedApi*/ @Composable internal /*inline*/ fun Layout(
    /*crossinline*/
    children: @Composable () -> Unit,
    measureBlocks: LayoutNode.MeasureBlocks,
    modifier: Modifier
) {
    val materialized = currentComposer.materialize(modifier)

    @OptIn(ExperimentalComposeApi::class)
    emit<LayoutNode, Applier<Any>>(
        ctor = LayoutEmitHelper.constructor,
        update = {
            set(materialized, LayoutEmitHelper.setModifier)
            set(measureBlocks, LayoutEmitHelper.setMeasureBlocks)
        },
        children = children
    )
}

@Composable
@Deprecated("This composable is temporary to enable quicker prototyping in ConstraintLayout. " +
        "It should not be used in app code directly.")
@OptIn(ExperimentalLayoutNodeApi::class)
fun MultiMeasureLayout(
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit,
    measureBlock: MeasureBlock2
) {
    val measureBlocks = remember(measureBlock) { MeasuringIntrinsicsMeasureBlocks(measureBlock) }
    val materialized = currentComposer.materialize(modifier)
    @OptIn(ExperimentalComposeApi::class)
    emit<LayoutNode, Applier<Any>>(
        ctor = LayoutEmitHelper.constructor,
        update = {
            set(materialized, LayoutEmitHelper.setModifier)
            set(measureBlocks, LayoutEmitHelper.setMeasureBlocks)
            @Suppress("DEPRECATION")
            set(Unit) { this.canMultiMeasure = true }
        },
        children = children
    )
}

/**
 * Used to return a fixed sized item for intrinsics measurements in [Layout]
 */
private class DummyPlaceable(width: Int, height: Int) : Placeable() {
    init {
        measuredSize = IntSize(width, height)
    }
    override fun get(line: AlignmentLine): Int = AlignmentLine.Unspecified
    override fun place(position: IntOffset) { }
}

/**
 * Identifies an [IntrinsicMeasurable] as a min or max intrinsic measurement.
 */
@PublishedApi
internal enum class IntrinsicMinMax {
    Min, Max
}

/**
 * Identifies an [IntrinsicMeasurable] as a width or height intrinsic measurement.
 */
@PublishedApi
internal enum class IntrinsicWidthHeight {
    Width, Height
}

/**
 * A wrapper around a [Measurable] for intrinsic measurments in [Layout]. Consumers of
 * [Layout] don't identify intrinsic methods, but we can give a reasonable implementation
 * by using their [measure], substituting the intrinsics gathering method
 * for the [Measurable.measure] call.
 */
@PublishedApi
internal class DefaultIntrinsicMeasurable(
    val measurable: IntrinsicMeasurable,
    val minMax: IntrinsicMinMax,
    val widthHeight: IntrinsicWidthHeight
) : Measurable {
    override val parentData: Any?
        get() = measurable.parentData

    override fun measure(constraints: Constraints, layoutDirection: LayoutDirection): Placeable {
        if (widthHeight == IntrinsicWidthHeight.Width) {
            val width = if (minMax == IntrinsicMinMax.Max) {
                measurable.maxIntrinsicWidth(constraints.maxHeight, layoutDirection)
            } else {
                measurable.minIntrinsicWidth(constraints.maxHeight, layoutDirection)
            }
            return DummyPlaceable(width, constraints.maxHeight)
        }
        val height = if (minMax == IntrinsicMinMax.Max) {
            measurable.maxIntrinsicHeight(constraints.maxWidth, layoutDirection)
        } else {
            measurable.minIntrinsicHeight(constraints.maxWidth, layoutDirection)
        }
        return DummyPlaceable(constraints.maxWidth, height)
    }

    override fun minIntrinsicWidth(height: Int, layoutDirection: LayoutDirection): Int {
        return measurable.minIntrinsicWidth(height, layoutDirection)
    }

    override fun maxIntrinsicWidth(height: Int, layoutDirection: LayoutDirection): Int {
        return measurable.maxIntrinsicWidth(height, layoutDirection)
    }

    override fun minIntrinsicHeight(width: Int, layoutDirection: LayoutDirection): Int {
        return measurable.minIntrinsicHeight(width, layoutDirection)
    }

    override fun maxIntrinsicHeight(width: Int, layoutDirection: LayoutDirection): Int {
        return measurable.maxIntrinsicHeight(width, layoutDirection)
    }
}

/**
 * Receiver scope for [Layout]'s layout lambda when used in an intrinsics call.
 */
@PublishedApi
@OptIn(ExperimentalLayoutNodeApi::class)
internal class IntrinsicsMeasureScope(
    density: Density,
    override val layoutDirection: LayoutDirection
) : MeasureScope(), Density by density
/**
 * Default [LayoutNode.MeasureBlocks] object implementation, providing intrinsic measurements
 * that use the measure block replacing the measure calls with intrinsic measurement calls.
 */
@OptIn(ExperimentalLayoutNodeApi::class)
fun MeasuringIntrinsicsMeasureBlocks(measureBlock: MeasureBlock2) =
    object : LayoutNode.MeasureBlocks {
        override fun measure(
            measureScope: MeasureScope,
            measurables: List<Measurable>,
            constraints: Constraints,
            layoutDirection: LayoutDirection
        ) = measureScope.measureBlock(measurables, constraints)
        override fun minIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.MeasuringMinIntrinsicWidth(
            measureBlock,
            measurables,
            h,
            layoutDirection
        )
        override fun minIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.MeasuringMinIntrinsicHeight(
            measureBlock,
            measurables,
            w,
            layoutDirection
        )
        override fun maxIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.MeasuringMaxIntrinsicWidth(
            measureBlock,
            measurables,
            h,
            layoutDirection
        )
        override fun maxIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.MeasuringMaxIntrinsicHeight(
            measureBlock,
            measurables,
            w,
            layoutDirection
        )

        override fun toString(): String {
            // this calls simpleIdentityToString on measureBlock because it is typically a lambda,
            // which has a useless toString that doesn't hint at the source location
            return simpleIdentityToString(
                this,
                "MeasuringIntrinsicsMeasureBlocks"
            ) + "{ measureBlock=${simpleIdentityToString(measureBlock, null)} }"
        }
    }

@OptIn(ExperimentalLayoutNodeApi::class)
@Deprecated("Use MeasuringIntrinsicsMeasureBlocks with MeasureBlock2 instead")
@Suppress("DEPRECATION")
fun MeasuringIntrinsicsMeasureBlocks(measureBlock: MeasureBlock) =
    object : LayoutNode.MeasureBlocks {
        override fun measure(
            measureScope: MeasureScope,
            measurables: List<Measurable>,
            constraints: Constraints,
            layoutDirection: LayoutDirection
        ) = measureScope.measureBlock(measurables, constraints, layoutDirection)
        override fun minIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.MeasuringMinIntrinsicWidth(
            measureBlock,
            measurables,
            h,
            layoutDirection
        )
        override fun minIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.MeasuringMinIntrinsicHeight(
            measureBlock,
            measurables,
            w,
            layoutDirection
        )
        override fun maxIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.MeasuringMaxIntrinsicWidth(
            measureBlock,
            measurables,
            h,
            layoutDirection
        )
        override fun maxIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ) = intrinsicMeasureScope.MeasuringMaxIntrinsicHeight(
            measureBlock,
            measurables,
            w,
            layoutDirection
        )

        override fun toString(): String {
            // this calls simpleIdentityToString on measureBlock because it is typically a lambda,
            // which has a useless toString that doesn't hint at the source location
            return simpleIdentityToString(
                this,
                "MeasuringIntrinsicsMeasureBlocks"
            ) + "{ measureBlock=${simpleIdentityToString(measureBlock, null)} }"
        }
    }

/**
 * Default implementation for the min intrinsic width of a layout. This works by running the
 * measure block with measure calls replaced with intrinsic measurement calls.
 */
private inline fun Density.MeasuringMinIntrinsicWidth(
    measureBlock: MeasureBlock2 /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    h: Int,
    layoutDirection: LayoutDirection
): Int {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Min, IntrinsicWidthHeight.Width)
    }
    val constraints = Constraints(maxHeight = h)
    val layoutReceiver = IntrinsicsMeasureScope(this, layoutDirection)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints)
    return layoutResult.width
}

@Suppress("DEPRECATION")
private inline fun Density.MeasuringMinIntrinsicWidth(
    measureBlock: MeasureBlock /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    h: Int,
    layoutDirection: LayoutDirection
): Int {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Min, IntrinsicWidthHeight.Width)
    }
    val constraints = Constraints(maxHeight = h)
    val layoutReceiver = IntrinsicsMeasureScope(this, layoutDirection)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints, layoutDirection)
    return layoutResult.width
}

/**
 * Default implementation for the min intrinsic width of a layout. This works by running the
 * measure block with measure calls replaced with intrinsic measurement calls.
 */
private inline fun Density.MeasuringMinIntrinsicHeight(
    measureBlock: MeasureBlock2 /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    w: Int,
    layoutDirection: LayoutDirection
): Int {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Min, IntrinsicWidthHeight.Height)
    }
    val constraints = Constraints(maxWidth = w)
    val layoutReceiver = IntrinsicsMeasureScope(this, layoutDirection)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints)
    return layoutResult.height
}

@Suppress("DEPRECATION")
private inline fun Density.MeasuringMinIntrinsicHeight(
    measureBlock: MeasureBlock /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    w: Int,
    layoutDirection: LayoutDirection
): Int {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Min, IntrinsicWidthHeight.Height)
    }
    val constraints = Constraints(maxWidth = w)
    val layoutReceiver = IntrinsicsMeasureScope(this, layoutDirection)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints, layoutDirection)
    return layoutResult.height
}

/**
 * Default implementation for the max intrinsic width of a layout. This works by running the
 * measure block with measure calls replaced with intrinsic measurement calls.
 */
private inline fun Density.MeasuringMaxIntrinsicWidth(
    measureBlock: MeasureBlock2 /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    h: Int,
    layoutDirection: LayoutDirection
): Int {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Max, IntrinsicWidthHeight.Width)
    }
    val constraints = Constraints(maxHeight = h)
    val layoutReceiver = IntrinsicsMeasureScope(this, layoutDirection)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints)
    return layoutResult.width
}

@Suppress("DEPRECATION")
private inline fun Density.MeasuringMaxIntrinsicWidth(
    measureBlock: MeasureBlock /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    h: Int,
    layoutDirection: LayoutDirection
): Int {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Max, IntrinsicWidthHeight.Width)
    }
    val constraints = Constraints(maxHeight = h)
    val layoutReceiver = IntrinsicsMeasureScope(this, layoutDirection)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints, layoutDirection)
    return layoutResult.width
}

/**
 * Default implementation for the max intrinsic height of a layout. This works by running the
 * measure block with measure calls replaced with intrinsic measurement calls.
 */
private inline fun Density.MeasuringMaxIntrinsicHeight(
    measureBlock: MeasureBlock2 /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    w: Int,
    layoutDirection: LayoutDirection
): Int {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Max, IntrinsicWidthHeight.Height)
    }
    val constraints = Constraints(maxWidth = w)
    val layoutReceiver = IntrinsicsMeasureScope(this, layoutDirection)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints)
    return layoutResult.height
}

@Suppress("DEPRECATION")
private inline fun Density.MeasuringMaxIntrinsicHeight(
    measureBlock: MeasureBlock /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    w: Int,
    layoutDirection: LayoutDirection
): Int {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Max, IntrinsicWidthHeight.Height)
    }
    val constraints = Constraints(maxWidth = w)
    val layoutReceiver = IntrinsicsMeasureScope(this, layoutDirection)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints, layoutDirection)
    return layoutResult.height
}

/**
 * A composable that defines its own content according to the available space, based on the incoming
 * constraints or the current [LayoutDirection]. Example usage:
 * @sample androidx.ui.core.samples.WithConstraintsSample
 *
 * The composable will compose the given children, and will position the resulting layout composables
 * in a parent [Layout]. This layout will be as small as possible such that it can fit its
 * children. If the composition yields multiple layout children, these will be all placed at the
 * top left of the WithConstraints, so consider wrapping them in an additional common
 * parent if different positioning is preferred.
 *
 * @param modifier Modifier to be applied to the introduced layout.
 */
@Composable
@OptIn(ExperimentalLayoutNodeApi::class)
fun WithConstraints(
    modifier: Modifier = Modifier,
    children: @Composable WithConstraintsScope.() -> Unit
) {
    val state = remember { WithConstrainsState() }
    state.children = children
    // TODO(lmr): refactor these APIs so that recomposer isn't necessary
    @OptIn(ExperimentalComposeApi::class)
    state.recomposer = currentComposer.recomposer
    state.compositionRef = compositionReference()
    // if this code was executed subcomposition must be triggered as well
    state.forceRecompose = true

    val materialized = currentComposer.materialize(modifier)
    @OptIn(ExperimentalComposeApi::class)
    emit<LayoutNode, Applier<Any>>(
        ctor = LayoutEmitHelper.constructor,
        update = {
            set(materialized, LayoutEmitHelper.setModifier)
            set(state.measureBlocks, LayoutEmitHelper.setMeasureBlocks)
            set(state.nodeRef, LayoutEmitHelper.setRef)
        }
    )

    // if LayoutNode scheduled the remeasuring no further steps are needed - subcomposition
    // will happen later on the measuring stage. otherwise we can assume the LayoutNode
    // already holds the final Constraints and we should subcompose straight away.
    // if owner is null this means we are not yet attached. once attached the remeasuring
    // will be scheduled which would cause subcomposition
    val layoutNode = state.nodeRef.value!!
    if (layoutNode.layoutState == LayoutState.Ready && layoutNode.owner != null) {
        state.subcompose()
    }
    onDispose {
        state.composition?.dispose()
    }
}

/**
 * Receiver scope being used by the children parameter of [WithConstraints]
 */
@Stable
interface WithConstraintsScope {
    /**
     * The constraints given by the parent layout in pixels.
     *
     * Use [minWidth], [maxWidth], [minHeight] or [maxHeight] if you need value in [Dp].
     */
    val constraints: Constraints
    /**
     * The current [LayoutDirection] to be used by this layout.
     */
    val layoutDirection: LayoutDirection
    /**
     * The minimum width in [Dp].
     *
     * @see constraints for the values in pixels.
     */
    val minWidth: Dp
    /**
     * The maximum width in [Dp].
     *
     * @see constraints for the values in pixels.
     */
    val maxWidth: Dp
    /**
     * The minimum height in [Dp].
     *
     * @see constraints for the values in pixels.
     */
    val minHeight: Dp
    /**
     * The minimum height in [Dp].
     *
     * @see constraints for the values in pixels.
     */
    val maxHeight: Dp
}

@OptIn(ExperimentalLayoutNodeApi::class)
private class WithConstrainsState {
    lateinit var recomposer: Recomposer
    var compositionRef: CompositionReference? = null
    val nodeRef = Ref<LayoutNode>()
    var children: @Composable WithConstraintsScope.() -> Unit = { }
    var forceRecompose = false
    var composition: Composition? = null

    private var scope: WithConstraintsScope = WithConstraintsScopeImpl(
        Density(1f),
        Constraints.fixed(0, 0),
        LayoutDirection.Ltr
    )

    val measureBlocks = object : LayoutNode.NoIntrinsicsMeasureBlocks(
        error = "Intrinsic measurements are not supported by WithConstraints"
    ) {
        override fun measure(
            measureScope: MeasureScope,
            measurables: List<Measurable>,
            constraints: Constraints,
            layoutDirection: LayoutDirection
        ): MeasureScope.MeasureResult {
            val root = nodeRef.value!!
            if (scope.constraints != constraints ||
                scope.layoutDirection != measureScope.layoutDirection ||
                forceRecompose
            ) {
                scope = WithConstraintsScopeImpl(measureScope, constraints, layoutDirection)
                root.ignoreModelReads { subcompose() }
                // if there were models created and read inside this subcomposition
                // and we are going to modify this models within the same frame
                // the composables which read this model will not be recomposed.
                // to make this possible we should apply global changes to ensure
                // these are observed as changes.
                @OptIn(ExperimentalComposeApi::class)
                Snapshot.notifyObjectsInitialized()
            }

            // Measure the obtained children and compute our size.
            val layoutChildren = root.children
            var maxWidth: Int = constraints.minWidth
            var maxHeight: Int = constraints.minHeight
            layoutChildren.fastForEach {
                it.measure(constraints, layoutDirection)
                maxWidth = max(maxWidth, it.width)
                maxHeight = max(maxHeight, it.height)
            }
            maxWidth = maxWidth.coerceAtMost(constraints.maxWidth)
            maxHeight = maxHeight.coerceAtMost(constraints.maxHeight)

            return measureScope.layout(maxWidth, maxHeight) {
                layoutChildren.fastForEach { it.place(0, 0) }
            }
        }
    }

    @OptIn(ExperimentalComposeApi::class)
    fun subcompose() {
        // TODO(b/150390669): Review use of @ComposableContract(tracked = false)
        composition =
            subcomposeInto(
                nodeRef.value!!,
                recomposer,
                compositionRef
            ) @ComposableContract(tracked = false) {
                scope.children()
            }
        forceRecompose = false
    }

    private data class WithConstraintsScopeImpl(
        private val density: Density,
        override val constraints: Constraints,
        override val layoutDirection: LayoutDirection
    ) : WithConstraintsScope {
        override val minWidth: Dp
            get() = with(density) { constraints.minWidth.toDp() }
        override val maxWidth: Dp
            get() = with(density) { constraints.maxWidth.toDp() }
        override val minHeight: Dp
            get() = with(density) { constraints.minHeight.toDp() }
        override val maxHeight: Dp
            get() = with(density) { constraints.maxHeight.toDp() }
    }
}
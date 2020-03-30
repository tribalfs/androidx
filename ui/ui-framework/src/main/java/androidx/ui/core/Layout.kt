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

import android.content.Context
import androidx.compose.Composable
import androidx.compose.CompositionReference
import androidx.compose.FrameManager
import androidx.compose.Untracked
import androidx.compose.compositionReference
import androidx.compose.remember
import androidx.ui.unit.Density
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxPosition
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.max
import androidx.ui.unit.min

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
 * @sample androidx.ui.framework.samples.LayoutWithProvidedIntrinsicsUsage
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
/*inline*/ fun Layout(
    /*crossinline*/
    children: @Composable() () -> Unit,
    /*crossinline*/
    minIntrinsicWidthMeasureBlock: IntrinsicMeasureBlock,
    /*crossinline*/
    minIntrinsicHeightMeasureBlock: IntrinsicMeasureBlock,
    /*crossinline*/
    maxIntrinsicWidthMeasureBlock: IntrinsicMeasureBlock,
    /*crossinline*/
    maxIntrinsicHeightMeasureBlock: IntrinsicMeasureBlock,
    modifier: Modifier = Modifier.None,
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
            density: Density,
            measurables: List<IntrinsicMeasurable>,
            h: IntPx,
            layoutDirection: LayoutDirection
        ) = density.minIntrinsicWidthMeasureBlock(measurables, h, layoutDirection)
        override fun minIntrinsicHeight(
            density: Density,
            measurables: List<IntrinsicMeasurable>,
            w: IntPx,
            layoutDirection: LayoutDirection
        ) = density.minIntrinsicHeightMeasureBlock(measurables, w, layoutDirection)
        override fun maxIntrinsicWidth(
            density: Density,
            measurables: List<IntrinsicMeasurable>,
            h: IntPx,
            layoutDirection: LayoutDirection
        ) = density.maxIntrinsicWidthMeasureBlock(measurables, h, layoutDirection)
        override fun maxIntrinsicHeight(
            density: Density,
            measurables: List<IntrinsicMeasurable>,
            w: IntPx,
            layoutDirection: LayoutDirection
        ) = density.maxIntrinsicHeightMeasureBlock(measurables, w, layoutDirection)
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
 * @sample androidx.ui.framework.samples.LayoutUsage
 *
 * @param children The children composable to be laid out.
 * @param modifier Modifiers to be applied to the layout.
 * @param measureBlock The block defining the measurement and positioning of the layout.
 *
 * @see Layout
 * @see WithConstraints
 */
@Composable
/*inline*/ fun Layout(
    /*crossinline*/
    children: @Composable() () -> Unit,
    modifier: Modifier = Modifier.None,
    /*noinline*/
    measureBlock: MeasureBlock
) {

    val measureBlocks = remember(measureBlock) { MeasuringIntrinsicsMeasureBlocks(measureBlock) }
    Layout(children, measureBlocks, modifier)
}

/*@PublishedApi*/ @Composable internal /*inline*/ fun Layout(
    /*crossinline*/
    children: @Composable() () -> Unit,
    measureBlocks: LayoutNode.MeasureBlocks,
    modifier: Modifier
) {
    LayoutNode(modifier = modifier, measureBlocks = measureBlocks) {
        children()
    }
}

@Composable
@Deprecated("This composable is temporary to enable quicker prototyping in ConstraintLayout. " +
        "It should not be used in app code directly.")
fun MultiMeasureLayout(
    modifier: Modifier = Modifier.None,
    children: @Composable() () -> Unit,
    measureBlock: MeasureBlock
) {
    val measureBlocks = remember(measureBlock) { MeasuringIntrinsicsMeasureBlocks(measureBlock) }
    LayoutNode(modifier = modifier, measureBlocks = measureBlocks, canMultiMeasure = true) {
        children()
    }
}

@Composable
@Deprecated("This composable supports our transition from single child composables to modifiers. " +
        "It should not be used in app code directly.")
fun PassThroughLayout(
    modifier: Modifier = Modifier.None,
    children: @Composable() () -> Unit
) {
    val measureBlocks = remember {
        val measureBlock: MeasureBlock = { measurables, constraints, _ ->
            val placeables = measurables.map { it.measure(constraints) }
            val width = placeables.maxBy { it.width }?.width ?: constraints.minWidth
            val height = placeables.maxBy { it.height }?.height ?: constraints.minHeight
            layout(width, height) {
                placeables.forEach { it.place(IntPx.Zero, IntPx.Zero) }
            }
        }
        MeasuringIntrinsicsMeasureBlocks(measureBlock)
    }
    LayoutNode(modifier = modifier, measureBlocks = measureBlocks, handlesParentData = false) {
        children()
    }
}

/**
 * Used to return a fixed sized item for intrinsics measurements in [Layout]
 */
private class DummyPlaceable(width: IntPx, height: IntPx) : Placeable() {
    override fun get(line: AlignmentLine): IntPx? = null
    override val size = IntPxSize(width, height)
    override fun performPlace(position: IntPxPosition) { }
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

    override fun measure(constraints: Constraints): Placeable {
        if (widthHeight == IntrinsicWidthHeight.Width) {
            val width = if (minMax == IntrinsicMinMax.Max) {
                measurable.maxIntrinsicWidth(constraints.maxHeight)
            } else {
                measurable.minIntrinsicWidth(constraints.maxHeight)
            }
            return DummyPlaceable(width, constraints.maxHeight)
        }
        val height = if (minMax == IntrinsicMinMax.Max) {
            measurable.maxIntrinsicHeight(constraints.maxWidth)
        } else {
            measurable.minIntrinsicHeight(constraints.maxWidth)
        }
        return DummyPlaceable(constraints.maxWidth, height)
    }

    override fun minIntrinsicWidth(height: IntPx): IntPx {
        return measurable.minIntrinsicWidth(height)
    }

    override fun maxIntrinsicWidth(height: IntPx): IntPx {
        return measurable.maxIntrinsicWidth(height)
    }

    override fun minIntrinsicHeight(width: IntPx): IntPx {
        return measurable.minIntrinsicHeight(width)
    }

    override fun maxIntrinsicHeight(width: IntPx): IntPx {
        return measurable.maxIntrinsicHeight(width)
    }
}

/**
 * Receiver scope for [Layout]'s layout lambda when used in an intrinsics call.
 */
@PublishedApi
internal class IntrinsicsMeasureScope(
    density: Density
) : MeasureScope(), Density by density

/**
 * Default [LayoutNode.MeasureBlocks] object implementation, providing intrinsic measurements
 * that use the measure block replacing the measure calls with intrinsic measurement calls.
 */
fun MeasuringIntrinsicsMeasureBlocks(measureBlock: MeasureBlock) =
    object : LayoutNode.MeasureBlocks {
        override fun measure(
            measureScope: MeasureScope,
            measurables: List<Measurable>,
            constraints: Constraints,
            layoutDirection: LayoutDirection
        ) = measureScope.measureBlock(measurables, constraints, layoutDirection)
        override fun minIntrinsicWidth(
            density: Density,
            measurables: List<IntrinsicMeasurable>,
            h: IntPx,
            layoutDirection: LayoutDirection
        ) = density.MeasuringMinIntrinsicWidth(measureBlock, measurables, h, layoutDirection)
        override fun minIntrinsicHeight(
            density: Density,
            measurables: List<IntrinsicMeasurable>,
            w: IntPx,
            layoutDirection: LayoutDirection
        ) = density.MeasuringMinIntrinsicHeight(measureBlock, measurables, w, layoutDirection)
        override fun maxIntrinsicWidth(
            density: Density,
            measurables: List<IntrinsicMeasurable>,
            h: IntPx,
            layoutDirection: LayoutDirection
        ) = density.MeasuringMaxIntrinsicWidth(measureBlock, measurables, h, layoutDirection)
        override fun maxIntrinsicHeight(
            density: Density,
            measurables: List<IntrinsicMeasurable>,
            w: IntPx,
            layoutDirection: LayoutDirection
        ) = density.MeasuringMaxIntrinsicHeight(measureBlock, measurables, w, layoutDirection)

        override fun toString(): String {
            // this calls simpleIdentityToString on measureBlock because it is typically a lambda,
            // which has a useless toString that doesn't hint at the source location
            return simpleIdentityToString(
                this,
                "MeasuringIntrinsicsMeasureBlocks"
            ) + "{ measureBlock=${simpleIdentityToString(measureBlock)} }"
        }
    }

/**
 * Default implementation for the min intrinsic width of a layout. This works by running the
 * measure block with measure calls replaced with intrinsic measurement calls.
 */
private inline fun Density.MeasuringMinIntrinsicWidth(
    measureBlock: MeasureBlock /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    h: IntPx,
    layoutDirection: LayoutDirection
): IntPx {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Min, IntrinsicWidthHeight.Width)
    }
    val constraints = Constraints(maxHeight = h)
    val layoutReceiver = IntrinsicsMeasureScope(this)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints, layoutDirection)
    return layoutResult.width
}

/**
 * Default implementation for the min intrinsic width of a layout. This works by running the
 * measure block with measure calls replaced with intrinsic measurement calls.
 */
private inline fun Density.MeasuringMinIntrinsicHeight(
    measureBlock: MeasureBlock /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    w: IntPx,
    layoutDirection: LayoutDirection
): IntPx {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Min, IntrinsicWidthHeight.Height)
    }
    val constraints = Constraints(maxWidth = w)
    val layoutReceiver = IntrinsicsMeasureScope(this)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints, layoutDirection)
    return layoutResult.height
}

/**
 * Default implementation for the max intrinsic width of a layout. This works by running the
 * measure block with measure calls replaced with intrinsic measurement calls.
 */
private inline fun Density.MeasuringMaxIntrinsicWidth(
    measureBlock: MeasureBlock /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    h: IntPx,
    layoutDirection: LayoutDirection
): IntPx {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Max, IntrinsicWidthHeight.Width)
    }
    val constraints = Constraints(maxHeight = h)
    val layoutReceiver = IntrinsicsMeasureScope(this)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints, layoutDirection)
    return layoutResult.width
}

/**
 * Default implementation for the max intrinsic height of a layout. This works by running the
 * measure block with measure calls replaced with intrinsic measurement calls.
 */
private inline fun Density.MeasuringMaxIntrinsicHeight(
    measureBlock: MeasureBlock /*TODO: crossinline*/,
    measurables: List<IntrinsicMeasurable>,
    w: IntPx,
    layoutDirection: LayoutDirection
): IntPx {
    val mapped = measurables.map {
        DefaultIntrinsicMeasurable(it, IntrinsicMinMax.Max, IntrinsicWidthHeight.Height)
    }
    val constraints = Constraints(maxWidth = w)
    val layoutReceiver = IntrinsicsMeasureScope(this)
    val layoutResult = layoutReceiver.measureBlock(mapped, constraints, layoutDirection)
    return layoutResult.height
}

/**
 * A composable that defines its own content according to the available space, based on the incoming
 * constraints. Example usage:
 * @sample androidx.ui.framework.samples.WithConstraintsSample
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
fun WithConstraints(
    modifier: Modifier = Modifier.None,
    children: @Composable() (Constraints, LayoutDirection) -> Unit
) {
    val state = remember { WithConstrainsState() }
    state.children = children
    state.context = ContextAmbient.current
    state.compositionRef = compositionReference()
    // if this code was executed subcomposition must be triggered as well
    state.forceRecompose = true

    LayoutNode(modifier = modifier, ref = state.nodeRef, measureBlocks = state.measureBlocks)

    // if LayoutNode scheduled the remeasuring no further steps are needed - subcomposition
    // will happen later on the measuring stage. otherwise we can assume the LayoutNode
    // already holds the final Constraints and we should subcompose straight away.
    // if owner is null this means we are not yet attached. once attached the remeasuring
    // will be scheduled which would cause subcomposition
    val layoutNode = state.nodeRef.value!!
    if (!layoutNode.needsRemeasure && layoutNode.owner != null) {
        state.subcompose()
    }
}

private class WithConstrainsState {
    var compositionRef: CompositionReference? = null
    var context: Context? = null
    val nodeRef = Ref<LayoutNode>()
    var lastConstraints: Constraints? = null
    var children: @Composable() (Constraints, LayoutDirection) -> Unit = { _, _ -> }
    var forceRecompose = false
    val measureBlocks = object : LayoutNode.NoIntrinsicsMeasureBlocks(
        error = "Intrinsic measurements are not supported by WithConstraints"
    ) {
        override fun measure(
            measureScope: MeasureScope,
            measurables: List<Measurable>,
            constraints: Constraints,
            layoutDirection: LayoutDirection
        ): MeasureScope.LayoutResult {
            val root = nodeRef.value!!
            if (lastConstraints != constraints || forceRecompose) {
                lastConstraints = constraints
                root.ignoreModelReads { subcompose() }
                // if there were models created and read inside this subcomposition
                // and we are going to modify this models within the same frame
                // the composables which read this model will not be recomposed.
                // to make this possible we should switch to the next frame.
                FrameManager.nextFrame()
            }

            // Measure the obtained children and compute our size.
            val layoutChildren = root.layoutChildren
            var maxWidth: IntPx = constraints.minWidth
            var maxHeight: IntPx = constraints.minHeight
            layoutChildren.forEach {
                it.measure(constraints)
                maxWidth = max(maxWidth, it.width)
                maxHeight = max(maxHeight, it.height)
            }
            maxWidth = min(maxWidth, constraints.maxWidth)
            maxHeight = min(maxHeight, constraints.maxHeight)

            return measureScope.layout(maxWidth, maxHeight) {
                layoutChildren.forEach { it.place(IntPx.Zero, IntPx.Zero) }
            }
        }
    }

    fun subcompose() {
        val node = nodeRef.value!!
        val constraints = lastConstraints!!
        // TODO(b/150390669): Review use of @Untracked
        subcomposeInto(node, context!!, compositionRef) @Untracked {
            children(constraints, node.layoutDirection!!)
        }
        forceRecompose = false
    }
}
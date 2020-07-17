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

@file:Suppress("Deprecation")

package androidx.ui.layout

import android.annotation.SuppressLint
import android.util.Log
import androidx.annotation.FloatRange
import androidx.compose.Composable
import androidx.compose.remember
import androidx.constraintlayout.core.state.ConstraintReference
import androidx.constraintlayout.core.state.Dimension.SPREAD_DIMENSION
import androidx.constraintlayout.core.state.Dimension.WRAP_DIMENSION
import androidx.constraintlayout.core.state.helpers.BarrierReference
import androidx.constraintlayout.core.widgets.ConstraintWidget
import androidx.constraintlayout.core.widgets.ConstraintWidget.DimensionBehaviour.MATCH_CONSTRAINT
import androidx.constraintlayout.core.widgets.ConstraintWidget.DimensionBehaviour.WRAP_CONTENT
import androidx.constraintlayout.core.widgets.ConstraintWidget.DimensionBehaviour.FIXED
import androidx.constraintlayout.core.widgets.ConstraintWidget.MATCH_CONSTRAINT_SPREAD
import androidx.constraintlayout.core.widgets.ConstraintWidget.MATCH_CONSTRAINT_WRAP
import androidx.constraintlayout.core.widgets.ConstraintWidgetContainer
import androidx.constraintlayout.core.widgets.Optimizer
import androidx.constraintlayout.core.widgets.analyzer.BasicMeasure
import androidx.compose.Immutable
import androidx.ui.core.Constraints
import androidx.ui.core.LayoutDirection
import androidx.ui.text.FirstBaseline
import androidx.ui.core.Measurable
import androidx.ui.core.MeasureScope
import androidx.ui.core.Modifier
import androidx.ui.core.MultiMeasureLayout
import androidx.ui.core.ParentDataModifier
import androidx.ui.core.Placeable
import androidx.ui.core.hasFixedHeight
import androidx.ui.core.hasFixedWidth
import androidx.ui.core.id
import androidx.ui.layout.ConstraintSetBuilderScope.Companion.baselineAnchorFunction
import androidx.ui.layout.ConstraintSetBuilderScope.Companion.horizontalAnchorFunctions
import androidx.ui.layout.ConstraintSetBuilderScope.Companion.verticalAnchorFunctions
import androidx.ui.layout.ConstraintSetBuilderScope.Companion.verticalAnchorIndexToFunctionIndex
import androidx.ui.unit.Density
import androidx.ui.unit.Dp
import androidx.ui.unit.IntSize
import androidx.ui.unit.dp
import androidx.ui.util.fastForEach

/**
 * Layout that positions its children according to the constraints between them.
 *
 * Example usage:
 * @sample androidx.ui.layout.samples.DemoInlineDSL
 */
@Composable
fun ConstraintLayout(
    modifier: Modifier = Modifier,
    children: @Composable ConstraintLayoutScope.() -> Unit
) {
    val measurer = remember { Measurer() }
    val scope = remember { ConstraintLayoutScope() }

    @Suppress("Deprecation")
    MultiMeasureLayout(modifier = modifier, children = {
        scope.reset()
        scope.children()
    }) { measurables, constraints ->
        val constraintSet = object : ConstraintSet {
            override fun applyTo(state: State, measurables: List<Measurable>) {
                scope.applyTo(state)
                measurables.fastForEach { measurable ->
                    val parentData = measurable.parentData as? ConstraintLayoutParentData
                    // Map the id and the measurable, to be retrieved later during measurement.
                    val givenTag = parentData?.ref?.id
                    state.map(givenTag ?: createId(), measurable)
                    // Run the constrainAs block of the child, to obtain its constraints.
                    if (parentData != null) {
                        val constrainScope = ConstrainScope(parentData.ref.id)
                        parentData.constrain(constrainScope)
                        constrainScope.applyTo(state)
                    }
                }
            }
        }

        val layoutSize = measurer.performMeasure(
            constraints,
            layoutDirection,
            constraintSet,
            measurables,
            this
        )
        layout(layoutSize.width, layoutSize.height) {
            with(measurer) { performLayout() }
        }
    }
}

/**
 * Layout that positions its children according to the constraints between them.
 *
 * Example usage:
 * @sample androidx.ui.layout.samples.DemoConstraintSet
 */
@Composable
fun ConstraintLayout(
    constraintSet: ConstraintSet,
    modifier: Modifier = Modifier,
    children: @Composable () -> Unit
) {
    val measurer = remember { Measurer() }
    @Suppress("Deprecation")
    MultiMeasureLayout(modifier, children) { measurables, constraints ->
        val layoutSize = measurer.performMeasure(
            constraints,
            layoutDirection,
            constraintSet,
            measurables,
            this
        )
        layout(layoutSize.width, layoutSize.height) {
            with(measurer) { performLayout() }
        }
    }
}

/**
 * Represents a layout within a [ConstraintLayout].
 */
class ConstrainedLayoutReference(val id: Any) {
    /**
     * The start anchor of this layout. Represents left in LTR layout direction, or right in RTL.
     */
    val start = ConstraintLayoutBaseScope.VerticalAnchor(id, -2)

    /**
     * The left anchor of this layout.
     */
    val absoluteLeft = ConstraintLayoutBaseScope.VerticalAnchor(id, 0)

    /**
     * The top anchor of this layout.
     */
    val top = ConstraintLayoutBaseScope.HorizontalAnchor(id, 0)

    /**
     * The end anchor of this layout. Represents right in LTR layout direction, or left in RTL.
     */
    val end = ConstraintLayoutBaseScope.VerticalAnchor(id, -1)

    /**
     * The right anchor of this layout.
     */
    val absoluteRight = ConstraintLayoutBaseScope.VerticalAnchor(id, 1)

    /**
     * The bottom anchor of this layout.
     */
    val bottom = ConstraintLayoutBaseScope.HorizontalAnchor(id, 1)

    /**
     * The baseline anchor of this layout.
     */
    val baseline = ConstraintLayoutBaseScope.BaselineAnchor(id)
}

/**
 * Common scope for [ConstraintLayoutScope] and [ConstraintSetScope], the content being shared
 * between the inline DSL API and the ConstraintSet-based API.
 */
abstract class ConstraintLayoutBaseScope {
    protected val tasks = mutableListOf<(State) -> Unit>()

    fun applyTo(state: State) = tasks.forEach { it(state) }

    fun reset() = tasks.clear()

    /**
     * Represents a vertical anchor (e.g. start/end of a layout, guideline) that layouts
     * can link to in their `Modifier.constrainAs` or `constrain` blocks.
     */
    data class VerticalAnchor internal constructor(internal val id: Any, internal val index: Int)

    /**
     * Represents a horizontal anchor (e.g. top/bottom of a layout, guideline) that layouts
     * can link to in their `Modifier.constrainAs` or `constrain` blocks.
     */
    data class HorizontalAnchor internal constructor(internal val id: Any, internal val index: Int)

    /**
     * Represents a horizontal anchor corresponding to the [FirstBaseline] of a layout that other
     * layouts can link to in their `Modifier.constrainAs` or `constrain` blocks.
     */
    // TODO(popam): investigate if this can be just a HorizontalAnchor
    data class BaselineAnchor internal constructor(internal val id: Any)

    /**
     * Creates a guideline at a specific offset from the start of the [ConstraintLayout].
     */
    fun createGuidelineFromStart(offset: Dp): VerticalAnchor {
        val id = createId()
        tasks.add { state ->
            state.verticalGuideline(id).apply {
                if (state.layoutDirection == LayoutDirection.Ltr) start(offset) else end(offset)
            }
        }
        return VerticalAnchor(id, 0)
    }

    /**
     * Creates a guideline at a specific offset from the left of the [ConstraintLayout].
     */
    fun createGuidelineFromAbsoluteLeft(offset: Dp): VerticalAnchor {
        val id = createId()
        tasks.add { state -> state.verticalGuideline(id).apply { start(offset) } }
        return VerticalAnchor(id, 0)
    }

    /**
     * Creates a guideline at a specific offset from the start of the [ConstraintLayout].
     * A [fraction] of 0f will correspond to the start of the [ConstraintLayout], while 1f will
     * correspond to the end.
     */
    fun createGuidelineFromStart(fraction: Float): VerticalAnchor {
        val id = createId()
        tasks.add { state ->
            state.verticalGuideline(id).apply {
                if (state.layoutDirection == LayoutDirection.Ltr) {
                    percent(fraction)
                } else {
                    percent(1f - fraction)
                }
            }
        }
        return VerticalAnchor(id, 0)
    }

    /**
     * Creates a guideline at a width fraction from the left of the [ConstraintLayout].
     * A [fraction] of 0f will correspond to the left of the [ConstraintLayout], while 1f will
     * correspond to the right.
     */
    // TODO(popam, b/157781990): this is not really percenide
    fun createGuidelineFromAbsoluteLeft(fraction: Float): VerticalAnchor {
        val id = createId()
        tasks.add { state -> state.verticalGuideline(id).apply { percent(fraction) } }
        return VerticalAnchor(id, 0)
    }

    /**
     * Creates a guideline at a specific offset from the end of the [ConstraintLayout].
     */
    fun createGuidelineFromEnd(offset: Dp): VerticalAnchor {
        val id = createId()
        tasks.add { state ->
            state.verticalGuideline(id).apply {
                if (state.layoutDirection == LayoutDirection.Ltr) end(offset) else start(offset)
            }
        }
        return VerticalAnchor(id, 0)
    }

    /**
     * Creates a guideline at a specific offset from the right of the [ConstraintLayout].
     */
    fun createGuidelineFromAbsoluteRight(offset: Dp): VerticalAnchor {
        val id = createId()
        tasks.add { state -> state.verticalGuideline(id).apply { end(offset) } }
        return VerticalAnchor(id, 0)
    }

    /**
     * Creates a guideline at a width fraction from the end of the [ConstraintLayout].
     * A [fraction] of 0f will correspond to the end of the [ConstraintLayout], while 1f will
     * correspond to the start.
     */
    fun createGuidelineFromEnd(fraction: Float): VerticalAnchor {
        return createGuidelineFromStart(1f - fraction)
    }

    /**
     * Creates a guideline at a width fraction from the right of the [ConstraintLayout].
     * A [fraction] of 0f will correspond to the right of the [ConstraintLayout], while 1f will
     * correspond to the left.
     */
    fun createGuidelineFromAbsoluteRight(fraction: Float): VerticalAnchor {
        return createGuidelineFromAbsoluteLeft(1f - fraction)
    }

    /**
     * Creates a guideline at a specific offset from the top of the [ConstraintLayout].
     */
    fun createGuidelineFromTop(offset: Dp): HorizontalAnchor {
        val id = createId()
        tasks.add { state -> state.horizontalGuideline(id).apply { start(offset) } }
        return HorizontalAnchor(id, 0)
    }

    /**
     * Creates a guideline at a height percenide from the top of the [ConstraintLayout].
     * A [fraction] of 0f will correspond to the top of the [ConstraintLayout], while 1f will
     * correspond to the bottom.
     */
    fun createGuidelineFromTop(fraction: Float): HorizontalAnchor {
        val id = createId()
        tasks.add { state -> state.horizontalGuideline(id).apply { percent(fraction) } }
        return HorizontalAnchor(id, 0)
    }

    /**
     * Creates a guideline at a specific offset from the bottom of the [ConstraintLayout].
     */
    fun createGuidelineFromBottom(offset: Dp): HorizontalAnchor {
        val id = createId()
        tasks.add { state -> state.horizontalGuideline(id).apply { end(offset) } }
        return HorizontalAnchor(id, 0)
    }

    /**
     * Creates a guideline at a height percenide from the bottom of the [ConstraintLayout].
     * A [fraction] of 0f will correspond to the bottom of the [ConstraintLayout], while 1f will
     * correspond to the top.
     */
    fun createGuidelineFromBottom(fraction: Float): HorizontalAnchor {
        return createGuidelineFromTop(1f - fraction)
    }

    /**
     * Creates and returns a start barrier, containing the specified elements.
     */
    fun createStartBarrier(
        vararg elements: ConstrainedLayoutReference,
        margin: Dp = 0.dp
    ): VerticalAnchor {
        val id = createId()
        tasks.add { state ->
            val direction = if (state.layoutDirection == LayoutDirection.Ltr) {
                SolverDirection.LEFT
            } else {
                SolverDirection.RIGHT
            }
            state.barrier(id, direction).apply {
                add(*(elements.map { it.id }.toTypedArray()))
            }.margin(state.convertDimension(margin))
        }
        return VerticalAnchor(id, 0)
    }

    /**
     * Creates and returns a left barrier, containing the specified elements.
     */
    fun createAbsoluteLeftBarrier(
        vararg elements: ConstrainedLayoutReference,
        margin: Dp = 0.dp
    ): VerticalAnchor {
        val id = createId()
        tasks.add { state ->
            state.barrier(id, SolverDirection.LEFT).apply {
                add(*(elements.map { it.id }.toTypedArray()))
            }.margin(state.convertDimension(margin))
        }
        return VerticalAnchor(id, 0)
    }

    /**
     * Creates and returns a top barrier, containing the specified elements.
     */
    fun createTopBarrier(
        vararg elements: ConstrainedLayoutReference,
        margin: Dp = 0.dp
    ): HorizontalAnchor {
        val id = createId()
        tasks.add { state ->
            state.barrier(id, SolverDirection.TOP).apply {
                add(*(elements.map { it.id }.toTypedArray()))
            }.margin(state.convertDimension(margin))
        }
        return HorizontalAnchor(id, 0)
    }

    /**
     * Creates and returns an end barrier, containing the specified elements.
     */
    fun createEndBarrier(
        vararg elements: ConstrainedLayoutReference,
        margin: Dp = 0.dp
    ): VerticalAnchor {
        val id = createId()
        tasks.add { state ->
            val direction = if (state.layoutDirection == LayoutDirection.Ltr) {
                SolverDirection.RIGHT
            } else {
                SolverDirection.LEFT
            }
            state.barrier(id, direction).apply {
                add(*(elements.map { it.id }.toTypedArray()))
            }.margin(state.convertDimension(margin))
        }
        return VerticalAnchor(id, 0)
    }

    /**
     * Creates and returns a right barrier, containing the specified elements.
     */
    fun createAbsoluteRightBarrier(
        vararg elements: ConstrainedLayoutReference,
        margin: Dp = 0.dp
    ): VerticalAnchor {
        val id = createId()
        tasks.add { state ->
            state.barrier(id, SolverDirection.RIGHT).apply {
                add(*(elements.map { it.id }.toTypedArray()))
            }.margin(state.convertDimension(margin))
        }
        return VerticalAnchor(id, 0)
    }

    /**
     * Creates and returns a bottom barrier, containing the specified elements.
     */
    fun createBottomBarrier(
        vararg elements: ConstrainedLayoutReference,
        margin: Dp = 0.dp
    ): HorizontalAnchor {
        val id = createId()
        tasks.add { state ->
            state.barrier(id, SolverDirection.BOTTOM).apply {
                add(*(elements.map { it.id }.toTypedArray()))
            }.margin(state.convertDimension(margin))
        }
        return HorizontalAnchor(id, 0)
    }

    /**
     * Creates a horizontal chain including the referenced layouts.
     */
    // TODO(popam, b/157783937): this API should be improved
    fun createHorizontalChain(
        vararg elements: ConstrainedLayoutReference,
        chainStyle: ChainStyle = ChainStyle.Spread
    ) {
        tasks.add { state ->
            state.horizontalChain(*(elements.map { it.id }.toTypedArray()))
                .also { it.style(chainStyle.style) }
                .apply()
            if (chainStyle.bias != null) {
                state.constraints(elements[0].id).horizontalBias(chainStyle.bias)
            }
        }
    }

    /**
     * Creates a vertical chain including the referenced layouts.
     */
    // TODO(popam, b/157783937): this API should be improved
    fun createVerticalChain(
        vararg elements: ConstrainedLayoutReference,
        chainStyle: ChainStyle = ChainStyle.Spread
    ) {
        tasks.add { state ->
            state.verticalChain(*(elements.map { it.id }.toTypedArray()))
                .also { it.style(chainStyle.style) }
                .apply()
            if (chainStyle.bias != null) {
                state.constraints(elements[0].id).verticalBias(chainStyle.bias)
            }
        }
    }
}

/**
 * Scope used by the inline DSL of [ConstraintLayout].
 */
@LayoutScopeMarker
class ConstraintLayoutScope internal constructor() : ConstraintLayoutBaseScope() {
    /**
     * Creates one [ConstrainedLayoutReference], which needs to be assigned to a layout within the
     * [ConstraintLayout] as part of [Modifier.constrainAs]. To create more references at the
     * same time, see [createRefs].
     */
    fun createRef() = ConstrainedLayoutReference(createId())

    /**
     * Convenient way to create multiple [ConstrainedLayoutReference]s, which need to be assigned
     * to layouts within the [ConstraintLayout] as part of [Modifier.constrainAs]. To create just
     * one reference, see [createRef].
     */
    fun createRefs() = ConstrainedLayoutReferences()

    /**
     * Convenience API for creating multiple [ConstrainedLayoutReference] via [createRefs].
     */
    inner class ConstrainedLayoutReferences internal constructor() {
        operator fun component1() = createRef()
        operator fun component2() = createRef()
        operator fun component3() = createRef()
        operator fun component4() = createRef()
        operator fun component5() = createRef()
        operator fun component6() = createRef()
        operator fun component7() = createRef()
        operator fun component8() = createRef()
        operator fun component9() = createRef()
        operator fun component10() = createRef()
        operator fun component11() = createRef()
        operator fun component12() = createRef()
        operator fun component13() = createRef()
        operator fun component14() = createRef()
        operator fun component15() = createRef()
        operator fun component16() = createRef()
    }

    /**
     * [Modifier] that defines the constraints, as part of a [ConstraintLayout], of the layout
     * element.
     */
    fun Modifier.constrainAs(
        ref: ConstrainedLayoutReference,
        constrainBlock: ConstrainScope.() -> Unit
    ): Modifier {
        // TODO(popam, b/157782492): make equals comparable modifiers here.
        return this + object : ParentDataModifier {
            override fun Density.modifyParentData(parentData: Any?) =
                ConstraintLayoutParentData(ref, constrainBlock)
        }
    }
}

/**
 * Scope used by the [ConstraintSet] DSL.
 */
@LayoutScopeMarker
class ConstraintSetScope internal constructor() : ConstraintLayoutBaseScope() {
    /**
     * Creates one [ConstrainedLayoutReference] corresponding to the [ConstraintLayout] element
     * with [id].
     */
    fun createRefFor(id: Any) = ConstrainedLayoutReference(id)

    /**
     * Specifies the constraints associated to the layout identified with [ref].
     */
    fun constrain(
        ref: ConstrainedLayoutReference,
        constrainBlock: ConstrainScope.() -> Unit
    ) = ConstrainScope(ref.id).apply {
        constrainBlock()
        this@ConstraintSetScope.tasks.addAll(this.tasks)
    }
}

/**
 * The style of a horizontal or vertical chain.
 */
class ChainStyle internal constructor(
    internal val style: SolverChain,
    internal val bias: Float? = null
) {
    companion object {
        /**
         * A chain style that evenly distributes the contained layouts.
         */
        val Spread = ChainStyle(SolverChain.SPREAD)

        /**
         * A chain style where the first and last layouts are affixed to the constraints
         * on each end of the chain and the rest are evenly distributed.
         */
        val SpreadInside = ChainStyle(SolverChain.SPREAD_INSIDE)

        /**
         * A chain style where the contained layouts are packed together and placed to the
         * center of the available space.
         */
        val Packed = Packed(0.5f)

        /**
         * A chain style where the contained layouts are packed together and placed in
         * the available space according to a given [bias].
         */
        fun Packed(bias: Float) = ChainStyle(SolverChain.PACKED, bias)
    }
}

/**
 * Parent data provided by `Modifier.constrainAs`.
 */
private class ConstraintLayoutParentData(
    val ref: ConstrainedLayoutReference,
    val constrain: ConstrainScope.() -> Unit
)

/**
 * Scope used by `Modifier.constrainAs`.
 */
@LayoutScopeMarker
class ConstrainScope internal constructor(internal val id: Any) {
    internal val tasks = mutableListOf<(State) -> Unit>()
    internal fun applyTo(state: State) = tasks.forEach { it(state) }

    /**
     * Reference to the [ConstraintLayout] itself, which can be used to specify constraints
     * between itself and its children.
     */
    val parent = ConstrainedLayoutReference(SolverState.PARENT)

    /**
     * The start anchor of the layout - can be constrained using [VerticalAnchorable.linkTo].
     */
    val start = VerticalAnchorable(id, -2)

    /**
     * The left anchor of the layout - can be constrained using [VerticalAnchorable.linkTo].
     */
    val absoluteLeft = VerticalAnchorable(id, 0)

    /**
     * The top anchor of the layout - can be constrained using [HorizontalAnchorable.linkTo].
     */
    val top = HorizontalAnchorable(id, 0)

    /**
     * The end anchor of the layout - can be constrained using [VerticalAnchorable.linkTo].
     */
    val end = VerticalAnchorable(id, -1)

    /**
     * The right anchor of the layout - can be constrained using [VerticalAnchorable.linkTo].
     */
    val absoluteRight = VerticalAnchorable(id, 1)

    /**
     * The bottom anchor of the layout - can be constrained using [HorizontalAnchorable.linkTo].
     */
    val bottom = HorizontalAnchorable(id, 1)

    /**
     * The [FirstBaseline] of the layout - can be constrained using [BaselineAnchorable.linkTo].
     */
    val baseline = BaselineAnchorable(id)

    /**
     * The width of the [ConstraintLayout] child.
     */
    var width: Dimension = Dimension.wrapContent
        set(value) {
            field = value
            tasks.add { state ->
                state.constraints(id).width(
                    (value as DimensionDescription).toSolverDimension(state)
                )
            }
        }

    /**
     * The height of the [ConstraintLayout] child.
     */
    var height: Dimension = Dimension.wrapContent
        set(value) {
            field = value
            tasks.add { state ->
                state.constraints(id).height(
                    (value as DimensionDescription).toSolverDimension(state)
                )
            }
        }

    /**
     * Represents a vertical side of a layout (i.e start and end) that can be anchored using
     * [linkTo] in their `Modifier.constrainAs` blocks.
     */
    inner class VerticalAnchorable internal constructor(
        internal val id: Any,
        internal val index: Int
    ) {
        /**
         * Adds a link towards a [ConstraintLayoutBaseScope.VerticalAnchor].
         */
        // TODO(popam, b/158069248): add parameter for gone margin
        fun linkTo(anchor: ConstraintLayoutBaseScope.VerticalAnchor, margin: Dp = 0.dp) {
            tasks.add { state ->
                with(state.constraints(id)) {
                    val layoutDirection = state.layoutDirection
                    val index1 = verticalAnchorIndexToFunctionIndex(index, layoutDirection)
                    val index2 = verticalAnchorIndexToFunctionIndex(anchor.index, layoutDirection)
                    verticalAnchorFunctions[index1][index2]
                        .invoke(this, anchor.id)
                        .margin(margin)
                }
            }
        }
    }

    /**
     * Represents a horizontal side of a layout (i.e top and bottom) that can be anchored using
     * [linkTo] in their `Modifier.constrainAs` blocks.
     */
    inner class HorizontalAnchorable internal constructor(
        internal val tag: Any,
        internal val index: Int
    ) {
        /**
         * Adds a link towards a [ConstraintLayoutBaseScope.HorizontalAnchor].
         */
        // TODO(popam, b/158069248): add parameter for gone margin
        fun linkTo(anchor: ConstraintLayoutBaseScope.HorizontalAnchor, margin: Dp = 0.dp) {
            tasks.add { state ->
                with(state.constraints(id)) {
                    horizontalAnchorFunctions[index][anchor.index]
                        .invoke(this, anchor.id)
                        .margin(margin)
                }
            }
        }
    }

    /**
     * Represents the [FirstBaseline] of a layout that can be anchored
     * using [linkTo] in their `Modifier.constrainAs` blocks.
     */
    inner class BaselineAnchorable internal constructor(internal val id: Any) {
        /**
         * Adds a link towards a [ConstraintLayoutBaseScope.BaselineAnchor].
         */
        // TODO(popam, b/158069248): add parameter for gone margin
        fun linkTo(anchor: ConstraintLayoutBaseScope.BaselineAnchor, margin: Dp = 0.dp) {
            tasks.add { state ->
                with(state.constraints(id)) {
                    baselineAnchorFunction.invoke(this, anchor.id).margin(margin)
                }
            }
        }
    }

    /**
     * Adds both start and end links towards other [ConstraintLayoutBaseScope.HorizontalAnchor]s.
     */
    // TODO(popam, b/158069248): add parameter for gone margin
    fun linkTo(
        start: ConstraintLayoutBaseScope.VerticalAnchor,
        end: ConstraintLayoutBaseScope.VerticalAnchor,
        startMargin: Dp = 0.dp,
        endMargin: Dp = 0.dp,
        @FloatRange(from = 0.0, to = 1.0) bias: Float = 0.5f
    ) {
        this@ConstrainScope.start.linkTo(start, startMargin)
        this@ConstrainScope.end.linkTo(end, endMargin)
        tasks.add { state ->
            state.constraints(id).horizontalBias(bias)
        }
    }

    /**
     * Adds both top and bottom links towards other [ConstraintLayoutBaseScope.HorizontalAnchor]s.
     */
    // TODO(popam, b/158069248): add parameter for gone margin
    fun linkTo(
        top: ConstraintLayoutBaseScope.HorizontalAnchor,
        bottom: ConstraintLayoutBaseScope.HorizontalAnchor,
        topMargin: Dp = 0.dp,
        bottomMargin: Dp = 0.dp,
        @FloatRange(from = 0.0, to = 1.0) bias: Float = 0.5f
    ) {
        this@ConstrainScope.top.linkTo(top, topMargin)
        this@ConstrainScope.bottom.linkTo(bottom, bottomMargin)
        tasks.add { state ->
            state.constraints(id).verticalBias(bias)
        }
    }

    /**
     * Adds all start, top, end, bottom links towards
     * other [ConstraintLayoutBaseScope.HorizontalAnchor]s.
     */
    // TODO(popam, b/158069248): add parameter for gone margin
    fun linkTo(
        start: ConstraintLayoutBaseScope.VerticalAnchor,
        top: ConstraintLayoutBaseScope.HorizontalAnchor,
        end: ConstraintLayoutBaseScope.VerticalAnchor,
        bottom: ConstraintLayoutBaseScope.HorizontalAnchor,
        startMargin: Dp = 0.dp,
        topMargin: Dp = 0.dp,
        endMargin: Dp = 0.dp,
        bottomMargin: Dp = 0.dp,
        @FloatRange(from = 0.0, to = 1.0) horizontalBias: Float = 0.5f,
        @FloatRange(from = 0.0, to = 1.0) verticalBias: Float = 0.5f
    ) {
        linkTo(start, end, startMargin, endMargin, horizontalBias)
        linkTo(top, bottom, topMargin, bottomMargin, verticalBias)
    }

    /**
     * Adds all start, top, end, bottom links towards the corresponding anchors of [other].
     * This will center the current layout inside or around (depending on size) [other].
     */
    fun centerTo(other: ConstrainedLayoutReference) {
        linkTo(other.start, other.top, other.end, other.bottom)
    }

    /**
     * Adds start and end links towards the corresponding anchors of [other].
     * This will center horizontally the current layout inside or around (depending on size)
     * [other].
     */
    fun centerHorizontallyTo(other: ConstrainedLayoutReference) {
        linkTo(other.start, other.end)
    }

    /**
     * Adds top and bottom links towards the corresponding anchors of [other].
     * This will center vertically the current layout inside or around (depending on size)
     * [other].
     */
    fun centerVerticallyTo(other: ConstrainedLayoutReference) {
        linkTo(other.top, other.bottom)
    }

    /**
     * Adds start and end links towards a vertical [anchor].
     * This will center the current layout around the vertical [anchor].
     */
    fun centerAround(anchor: ConstraintLayoutBaseScope.VerticalAnchor) {
        linkTo(anchor, anchor)
    }

    /**
     * Adds top and bottom links towards a horizontal [anchor].
     * This will center the current layout around the horizontal [anchor].
     */
    fun centerAround(anchor: ConstraintLayoutBaseScope.HorizontalAnchor) {
        linkTo(anchor, anchor)
    }
}

/**
 * Convenience for creating ids corresponding to layout references that cannot be referred
 * to from the outside of the scope (e.g. barriers, layout references in the modifier-based API,
 * etc.).
 */
private fun createId() = object : Any() {}

/**
 * Represents a dimension that can be assigned to the width or height of a [ConstraintLayout]
 * [child][ConstrainedLayoutReference].
 */
// TODO(popam, b/157781841): It is unfortunate that this interface is top level in
// `foundation-layout`. This will be ok if we move constraint layout to its own module or at
// least subpackage.
interface Dimension {
    /**
     * A [Dimension] that can be assigned both min and max bounds.
     */
    interface Coercible : Dimension

    /**
     * A [Dimension] that can be assigned a min bound.
     */
    interface MinCoercible : Dimension

    /**
     * A [Dimension] that can be assigned a max bound.
     */
    interface MaxCoercible : Dimension

    companion object {
        /**
         * Creates a [Dimension] representing a suggested dp size. The requested size will
         * be respected unless the constraints in the [ConstraintSet] do not allow it. The min
         * and max bounds will be respected regardless of the constraints in the [ConstraintSet].
         * To make the value fixed (respected regardless the [ConstraintSet]), [value] should
         * be used instead.
         */
        fun preferredValue(dp: Dp): Dimension.Coercible =
            DimensionDescription { state -> SolverDimension.Suggested(state.convertDimension(dp)) }

        /**
         * Creates a [Dimension] representing a fixed dp size. The size will not change
         * according to the constraints in the [ConstraintSet].
         */
        fun value(dp: Dp): Dimension =
            DimensionDescription { state -> SolverDimension.Fixed(state.convertDimension(dp)) }

        /**
         * A [Dimension] with suggested wrap content behavior. The wrap content size
         * will be respected unless the constraints in the [ConstraintSet] do not allow it.
         * To make the value fixed (respected regardless the [ConstraintSet]), [wrapContent]
         * should be used instead.
         */
        val preferredWrapContent: Dimension.Coercible
            get() = DimensionDescription { SolverDimension.Suggested(WRAP_DIMENSION) }

        /**
         * A [Dimension] with fixed wrap content behavior. The size will not change
         * according to the constraints in the [ConstraintSet].
         */
        val wrapContent: Dimension
            get() = DimensionDescription { SolverDimension.Fixed(WRAP_DIMENSION) }

        /**
         * A [Dimension] that spreads to match constraints. Links should be specified from both
         * sides corresponding to this dimension, in order for this to work.
         */
        val fillToConstraints: Dimension
            get() = DimensionDescription { SolverDimension.Suggested(SPREAD_DIMENSION) }

        /**
         * A [Dimension] that is a percent of the parent in the corresponding direction.
         */
        fun percent(percent: Float): Dimension =
            // TODO(popam, b/157880732): make this nicer when possible in future solver releases
            DimensionDescription { SolverDimension.Percent(0, percent).suggested(0) }
    }
}

/**
 * Sets the lower bound of the current [Dimension] to be the wrap content size of the child.
 */
val Dimension.Coercible.atLeastWrapContent: Dimension.MaxCoercible
    get() = (this as DimensionDescription).also { it.minSymbol = WRAP_DIMENSION }

/**
 * Sets the lower bound of the current [Dimension] to a fixed [dp] value.
 */
fun Dimension.Coercible.atLeast(dp: Dp): Dimension.MaxCoercible =
    (this as DimensionDescription).also { it.min = dp }

/**
 * Sets the upper bound of the current [Dimension] to a fixed [dp] value.
 */
fun Dimension.Coercible.atMost(dp: Dp): Dimension.MinCoercible =
    (this as DimensionDescription).also { it.max = dp }

/**
 * Sets the upper bound of the current [Dimension] to be the wrap content size of the child.
 */
val Dimension.Coercible.atMostWrapContent: Dimension.MinCoercible
    get() = (this as DimensionDescription).also { it.maxSymbol = WRAP_DIMENSION }

/**
 * Sets the lower bound of the current [Dimension] to a fixed [dp] value.
 */
fun Dimension.MinCoercible.atLeastWrapContent(dp: Dp): Dimension =
    (this as DimensionDescription).also { it.min = dp }

/**
 * Sets the lower bound of the current [Dimension] to be the wrap content size of the child.
 */
val Dimension.MinCoercible.atLeastWrapContent: Dimension
    get() = (this as DimensionDescription).also { it.minSymbol = WRAP_DIMENSION }

/**
 * Sets the upper bound of the current [Dimension] to a fixed [dp] value.
 */
fun Dimension.MaxCoercible.atMost(dp: Dp): Dimension =
    (this as DimensionDescription).also { it.max = dp }

/**
 * Sets the upper bound of the current [Dimension] to be the [Wrap] size of the child.
 */
val Dimension.MaxCoercible.atMostWrapContent: Dimension
    get() = (this as DimensionDescription).also { it.maxSymbol = WRAP_DIMENSION }

/**
 * Describes a sizing behavior that can be applied to the width or height of a
 * [ConstraintLayout] child. The content of this class should not be instantiated
 * directly; helpers available in the [Dimension]'s companion object should be used.
 */
internal class DimensionDescription internal constructor(
    private val baseDimension: (State) -> SolverDimension
) : Dimension.Coercible, Dimension.MinCoercible, Dimension.MaxCoercible, Dimension {
    var min: Dp? = null
    var minSymbol: Any? = null
    var max: Dp? = null
    var maxSymbol: Any? = null
    internal fun toSolverDimension(state: State) = baseDimension(state).also {
        if (minSymbol != null) {
            it.min(minSymbol)
        } else if (min != null) {
            it.min(state.convertDimension(min!!))
        }
        if (maxSymbol != null) {
            it.max(maxSymbol)
        } else if (max != null) {
            it.max(state.convertDimension(max!!))
        }
    }
}

/**
 * Immutable description of the constraints used to layout the children of a [ConstraintLayout].
 */
@Immutable
interface ConstraintSet {
    /**
     * Applies the [ConstraintSet] to a state.
     */
    fun applyTo(state: State, measurables: List<Measurable>)
}

@Deprecated("This API for building ConstraintSets has been deprecated. " +
        "Consider using the inline DSL API - see ConstraintLayoutDemo for an example," +
        "or use ConstraintSet2 for building constraint sets.")
fun ConstraintSet(description: ConstraintSetBuilderScope.() -> Unit) = object : ConstraintSet {
    override fun applyTo(state: State, measurables: List<Measurable>) {
        measurables.forEach { measurable ->
            state.map((measurable.id ?: createId()), measurable)
        }
        description(ConstraintSetBuilderScope(state))
    }
}

/**
 * Creates a [ConstraintSet].
 */
fun ConstraintSet2(description: ConstraintSetScope.() -> Unit) = object : ConstraintSet {
    override fun applyTo(state: State, measurables: List<Measurable>) {
        measurables.forEach { measurable ->
            state.map((measurable.id ?: createId()), measurable)
        }
        val scope = ConstraintSetScope()
        scope.description()
        scope.applyTo(state)
    }
}

/**
 * Builder scope for a [ConstraintSet]. The scope should not be created directly - the
 * [ConstraintSet] class constructor should be used instead.
 */
// TODO(popam): support RTL
@Deprecated("This way of building ConstraintSets has been deprecated. " +
        "Use ConstraintSet2 (and its implicit ConstraintSetScope) instead.")
class ConstraintSetBuilderScope internal constructor(internal val state: State) {
    /**
     * Creates a reference corresponding to the constraint layout children with a specific tag,
     * which can be used to define the constraints to be imposed to those children.
     */
    fun tag(tag: Any) = tags.getOrPut(tag, { ConstrainedLayoutReference(tag) })
    private val tags = mutableMapOf<Any, ConstrainedLayoutReference>()

    /**
     * Reference to the [ConstraintLayout] itself, which can be used to specify constraints
     * between itself and its children.
     */
    val parent = ConstrainedLayoutReference(SolverState.PARENT)

    /**
     * Represents a dimension that can be assigned to the width or height of a [ConstraintLayout]
     * [child][ConstrainedLayoutReference].
     */
    interface Dimension {
        /**
         * A [Dimension] that can be assigned both min and max bounds.
         */
        interface Coercible : Dimension

        /**
         * A [Dimension] that can be assigned a min bound.
         */
        interface MinCoercible : Dimension

        /**
         * A [Dimension] that can be assigned a max bound.
         */
        interface MaxCoercible : Dimension
    }

    /**
     * Sets the lower bound of the current [Dimension] to be the [Wrap] size of the child.
     */
    val Dimension.Coercible.minWrap: Dimension.MaxCoercible
        get() = (this as DimensionDescription).also { it.minSymbol = WRAP_DIMENSION }

    /**
     * Sets the lower bound of the current [Dimension] to a fixed [dp] value.
     */
    fun Dimension.Coercible.min(dp: Dp): Dimension.MaxCoercible =
        (this as DimensionDescription).also { it.min = state.convertDimension(dp) }

    /**
     * Sets the upper bound of the current [Dimension] to a fixed [dp] value.
     */
    fun Dimension.Coercible.max(dp: Dp): Dimension.MinCoercible =
        (this as DimensionDescription).also { it.max = state.convertDimension(dp) }

    /**
     * Sets the upper bound of the current [Dimension] to be the [Wrap] size of the child.
     */
    val Dimension.Coercible.maxWrap: Dimension.MinCoercible
        get() = (this as DimensionDescription).also { it.maxSymbol = WRAP_DIMENSION }

    /**
     * Sets the lower bound of the current [Dimension] to a fixed [dp] value.
     */
    fun Dimension.MinCoercible.min(dp: Dp): Dimension =
        (this as DimensionDescription).also { it.min = state.convertDimension(dp) }

    /**
     * Sets the lower bound of the current [Dimension] to be the [Wrap] size of the child.
     */
    val Dimension.MinCoercible.minWrap: Dimension
        get() = (this as DimensionDescription).also { it.minSymbol = WRAP_DIMENSION }

    /**
     * Sets the upper bound of the current [Dimension] to a fixed [dp] value.
     */
    fun Dimension.MaxCoercible.max(dp: Dp): Dimension =
        (this as DimensionDescription).also { it.max = state.convertDimension(dp) }

    /**
     * Sets the upper bound of the current [Dimension] to be the [Wrap] size of the child.
     */
    val Dimension.MaxCoercible.maxWrap: Dimension
        get() = (this as DimensionDescription).also { it.maxSymbol = WRAP_DIMENSION }

    /**
     * Describes a sizing behavior that can be applied to the width or height of a
     * [ConstraintLayout] child. The content of this class should not be instantiated
     * directly; helpers available in the scope such as [wrap], [value]
     * or [wrapFixed] should be used instead to create a [Dimension].
     */
    internal class DimensionDescription internal constructor(
        private val baseDimension: SolverDimension
    ) : Dimension.Coercible, Dimension.MinCoercible, Dimension.MaxCoercible, Dimension {
        var min: Int? = null
        var minSymbol: Any? = null
        var max: Int? = null
        var maxSymbol: Any? = null
        internal fun toSolverDimension() = baseDimension.also {
            if (minSymbol != null) {
                it.min(minSymbol)
            } else if (min != null) {
                it.min(min!!)
            }
            if (maxSymbol != null) {
                it.max(maxSymbol)
            } else if (max != null) {
                it.max(max!!)
            }
        }
    }

    /**
     * Creates a [Dimension] representing a suggested dp size. The requested size will
     * be respected unless the constraints in the [ConstraintSet] do not allow it. The min
     * and max bounds will be respected regardless of the constraints in the [ConstraintSet].
     * To make the value fixed (respected regardless the [ConstraintSet]), [valueFixed] should
     * be used instead.
     */
    fun value(dp: Dp): Dimension.Coercible =
        DimensionDescription(SolverDimension.Suggested(state.convertDimension(dp)))

    /**
     * Creates a [Dimension] representing a fixed dp size. The size will not change
     * according to the constraints in the [ConstraintSet].
     */
    fun valueFixed(dp: Dp): Dimension =
        DimensionDescription(SolverDimension.Fixed(state.convertDimension(dp)))

    /**
     * A [Dimension] with suggested wrap content behavior. The wrap content size
     * will be respected unless the constraints in the [ConstraintSet] do not allow it.
     * To make the value fixed (respected regardless the [ConstraintSet]), [wrapFixed]
     * should be used instead.
     */
    val wrap: Dimension.Coercible
        get() = DimensionDescription(SolverDimension.Suggested(WRAP_DIMENSION))

    /**
     * A [Dimension] with fixed wrap content behavior. The size will not change
     * according to the constraints in the [ConstraintSet].
     */
    val wrapFixed: Dimension
        get() = DimensionDescription(SolverDimension.Fixed(WRAP_DIMENSION))

    /**
     * A [Dimension] that spreads to match constraints.
     */
    val spread: Dimension
        get() = DimensionDescription(SolverDimension.Suggested(SPREAD_DIMENSION))

    /**
     * A [Dimension] that is a percent of the parent in the corresponding direction.
     */
    fun percent(percent: Float): Dimension =
        // TODO(popam): make this nicer when possible in future solver releases
        DimensionDescription(SolverDimension.Percent(0, percent).suggested(0))

    inner class ConstrainedLayoutReference internal constructor(val tag: Any) {
        val left = VerticalAnchor.ConstrainedLayoutAnchor(state, this, 0)
        val top = HorizontalAnchor.ConstrainedLayoutAnchor(state, this, 0)
        var right = VerticalAnchor.ConstrainedLayoutAnchor(state, this, 1)
        var bottom = HorizontalAnchor.ConstrainedLayoutAnchor(state, this, 1)
        var baseline = ConstrainedLayoutBaselineAnchor(state, this)

        /**
         * The width of the [ConstraintLayout] child.
         */
        var width: Dimension = wrap
            set(value) {
                field = value
                state.constraints(tag).width(
                    (value as DimensionDescription).toSolverDimension()
                ).apply()
            }

        /**
         * The height of the [ConstraintLayout] child.
         */
        var height: Dimension = wrap
            set(value) {
                field = value
                state.constraints(tag).height(
                    (value as DimensionDescription).toSolverDimension()
                ).apply()
            }

        /**
         * Adds constraints between left, top, right and bottom corresponding anchors of
         * two layout references.
         */
        infix fun constrainTo(other: ConstrainedLayoutReference) {
            left constrainTo other.left
            top constrainTo other.top
            right constrainTo other.right
            bottom constrainTo other.bottom
        }

        /**
         * Adds constraints between left and right corresponding anchors of two layout references.
         */
        infix fun constrainHorizontallyTo(other: ConstrainedLayoutReference) {
            left constrainTo other.left
            right constrainTo other.right
        }

        /**
         * Adds constraints between top and bottom corresponding anchors of two layout references.
         */
        infix fun constrainVerticallyTo(other: ConstrainedLayoutReference) {
            top constrainTo other.top
            bottom constrainTo other.bottom
        }

        /**
         * The horizontal bias of the current layout reference.
         */
        // TODO(popam): keep the source of truth in ConstraintReference or make this write only
        var horizontalBias: Float = 0.5f
            set(value) {
                field = value
                state.constraints(tag).horizontalBias(value)
            }

        /**
         * The vertical bias of the current layout reference.
         */
        // TODO(popam): keep the source of truth in ConstraintReference or make this write only
        var verticalBias: Float = 0.5f
            set(value) {
                field = value
                state.constraints(tag).verticalBias(value)
            }

        /**
         * Centers the layout horizontally in its [parent].
         */
        fun centerHorizontally() = state.centerHorizontally(tag).also { it.bias(0.5f) }.apply()

        /**
         * Centers the layout vertically in its [parent].
         */
        fun centerVertically() = state.centerVertically(tag).also { it.bias(0.5f) }.apply()

        /**
         * Centers the layout in its [parent].
         */
        fun center() {
            centerHorizontally()
            centerVertically()
        }
    }

    /**
     * Represents a horizontal chain.
     */
    class HorizontalChain internal constructor(
        internal val first: ConstrainedLayoutReference,
        internal val last: ConstrainedLayoutReference
    ) {
        val left: VerticalAnchor.ConstrainedLayoutAnchor get() = first.left
        val right: VerticalAnchor.ConstrainedLayoutAnchor get() = last.right
        infix fun constrainTo(other: ConstrainedLayoutReference) {
            left constrainTo other.left
            right constrainTo other.right
        }
    }

    /**
     * Represents a vertical chain.
     */
    class VerticalChain internal constructor(
        internal val first: ConstrainedLayoutReference,
        internal val last: ConstrainedLayoutReference
    ) {
        val top: HorizontalAnchor.ConstrainedLayoutAnchor get() = first.top
        val bottom: HorizontalAnchor.ConstrainedLayoutAnchor get() = last.bottom
        infix fun constrainTo(other: ConstrainedLayoutReference) {
            first.top constrainTo other.top
            last.bottom constrainTo other.bottom
        }
    }

    /**
     * Defines a vertical anchor which can be used for defining constraints. It can correspond:
     * - to a side or baseline of a child of the [ConstraintLayout]
     * - to a left barrier or right barrier
     * - to a vertical guideline
     */
    sealed class VerticalAnchor {
        internal abstract val state: State
        internal abstract val tag: Any
        internal abstract val index: Int
        /**
         * Anchor corresponding to the left or right of a child of the [ConstraintLayout].
         */
        class ConstrainedLayoutAnchor internal constructor(
            override val state: State,
            internal val constrainedLayoutReference: ConstrainedLayoutReference,
            override val index: Int
        ) : VerticalAnchor() {
            override val tag: Any get() = constrainedLayoutReference.tag
            // TODO(popam): keep the source of truth in ConstraintReference
            /**
             * The margin to be applied to the current [ConstrainedLayoutAnchor].
             */
            var margin: Dp = 0.dp
                set(value) {
                    field = value
                    state.constraints(tag)
                        .let { if (index == 0) it.start() else it.end() }
                        .margin(value)
                }

            /**
             * Adds a constraint between a [ConstrainedLayoutAnchor] and a [VerticalAnchor].
             */
            infix fun constrainTo(other: VerticalAnchor) {
                with(state.constraints(this.constrainedLayoutReference.tag)) {
                    val thisIndex = this@ConstrainedLayoutAnchor.index
                    val otherIndex = other.index
                    verticalAnchorFunctions[thisIndex][otherIndex].invoke(this, other.tag)
                }
            }
        }

        /**
         * Anchor corresponding to a vertical guideline.
         */
        class GuidelineAnchor internal constructor(
            override val state: State,
            override val tag: Any,
            override val index: Int = 0
        ) : VerticalAnchor()

        /**
         * Anchor corresponding to a left or right barrier.
         */
        class BarrierAnchor internal constructor(
            override val state: State,
            override val tag: Any,
            private val barrierReference: BarrierReference,
            override val index: Int = 0
        ) : VerticalAnchor() {
            // TODO(popam): keep the source of truth in ConstraintReference
            /**
             * The margin to be applied to the current [BarrierAnchor], in the
             * direction of the barrier.
             */
            var margin: Dp = 0.dp
                set(value) {
                    field = value
                    barrierReference.margin(value)
                }
        }
    }

    /**
     * Defines an horizontal anchor which can be used for defining constraints. It can correspond:
     * - to a side or baseline of a child of the [ConstraintLayout]
     * - to a top or bottom barrier
     * - to a horizontal guideline
     */
    sealed class HorizontalAnchor {
        internal abstract val state: State
        internal abstract val tag: Any
        internal abstract val index: Int
        /**
         * Anchor corresponding to the top or bottom of a child of the [ConstraintLayout].
         */
        class ConstrainedLayoutAnchor internal constructor(
            override val state: State,
            internal val constrainedLayoutReference: ConstrainedLayoutReference,
            override val index: Int
        ) : HorizontalAnchor() {
            override val tag: Any get() = constrainedLayoutReference.tag
            // TODO(popam): keep the source of truth in ConstraintReference
            /**
             * The margin to be applied to the current [ConstrainedLayoutAnchor].
             */
            var margin: Dp = 0.dp
                set(value) {
                    field = value
                    state.constraints(tag)
                        .let { if (index == 0) it.top() else it.bottom() }
                        .margin(value)
                }
            /**
             * Adds a constraint between a [ConstrainedLayoutAnchor] and a [HorizontalAnchor].
             */
            infix fun constrainTo(other: HorizontalAnchor) {
                with(state.constraints(this.constrainedLayoutReference.tag)) {
                    val thisIndex = this@ConstrainedLayoutAnchor.index
                    val otherIndex = other.index
                    horizontalAnchorFunctions[thisIndex][otherIndex].invoke(this, other.tag)
                }
            }
        }

        /**
         * Anchor corresponding to a horizontal guideline.
         */
        class GuidelineAnchor internal constructor(
            override val state: State,
            override val tag: Any,
            override val index: Int = 0
        ) : HorizontalAnchor()

        /**
         * Anchor corresponding to a top or bottom barrier.
         */
        class BarrierAnchor internal constructor(
            override val state: State,
            override val tag: Any,
            private val barrierReference: BarrierReference,
            override val index: Int = 0
        ) : HorizontalAnchor() {
            // TODO(popam): keep the source of truth in ConstraintReference
            var margin: Dp = 0.dp
                set(value) {
                    field = value
                    barrierReference.margin(value)
                }
        }
    }

    /**
     * Anchor corresponding to the baseline of a [ConstraintLayout] child.
     */
    class ConstrainedLayoutBaselineAnchor internal constructor(
        val state: State,
        val tag: Any
    ) {
        /**
         * Adds a constraint between two [ConstrainedLayoutBaselineAnchor] anchors.
         */
        infix fun constrainTo(other: ConstrainedLayoutBaselineAnchor) {
            with(state.constraints(tag)) {
                baselineAnchorFunction.invoke(this, other.tag)
            }
        }
    }

    /**
     * Creates a horizontal chain including the referenced layouts.
     */
    @SuppressLint
    fun createHorizontalChain(
        // Suppress lint here to allow vararg parameter for elements. API likely to change.
        @SuppressLint("ArrayReturn")
        vararg elements: ConstrainedLayoutReference,
        chainStyle: ChainStyle = ChainStyle.Spread
    ): HorizontalChain {
        state.horizontalChain(*(elements.map { it.tag }.toTypedArray()))
            .also { it.style(chainStyle.style) }
            .apply()
        if (chainStyle.bias != null) elements[0].horizontalBias = chainStyle.bias
        return HorizontalChain(elements.first(), elements.last())
    }

    /**
     * Creates a vertical chain including the referenced layouts.
     */
    fun createVerticalChain(
        // Suppress lint here to allow vararg parameter for elements. API likely to change.
        @SuppressLint("ArrayReturn")
        vararg elements: ConstrainedLayoutReference,
        chainStyle: ChainStyle = ChainStyle.Spread
    ): VerticalChain {
        state.verticalChain(*(elements.map { it.tag }.toTypedArray()))
            .also { it.style(chainStyle.style) }
            .apply()
        if (chainStyle.bias != null) elements[0].verticalBias = chainStyle.bias
        return VerticalChain(elements.first(), elements.last())
    }

    /**
     * The style of a horizontal or vertical chain.
     */
    class ChainStyle internal constructor(
        internal val style: SolverChain,
        internal val bias: Float? = null
    ) {
        companion object {
            /**
             * A chain style that evenly distributes the contained layouts.
             */
            val Spread = ChainStyle(SolverChain.SPREAD)

            /**
             * A chain style where the first and last layouts are affixed to the constraints
             * on each end of the chain and the rest are evenly distributed.
             */
            val SpreadInside = ChainStyle(SolverChain.SPREAD_INSIDE)

            /**
             * A chain style where the contained layouts are packed together and placed to the
             * center of the available space.
             */
            val Packed = Packed(0.5f)

            /**
             * A chain style where the contained layouts are packed together and placed in
             * the available space according to a given [bias].
             */
            fun Packed(bias: Float) = ChainStyle(SolverChain.PACKED, bias)
        }
    }

    /**
     * Creates a guideline at a specific offset from the left of the [ConstraintLayout].
     */
    fun createGuidelineFromLeft(offset: Dp): VerticalAnchor.GuidelineAnchor {
        val tag = createId()
        state.verticalGuideline(tag).apply { start(offset) }
        return VerticalAnchor.GuidelineAnchor(state, tag)
    }

    /**
     * Creates a guideline at a width percentage from the left of the [ConstraintLayout].
     */
    fun createGuidelineFromLeft(percent: Float): VerticalAnchor.GuidelineAnchor {
        val tag = createId()
        state.verticalGuideline(tag).apply { percent(percent) }
        return VerticalAnchor.GuidelineAnchor(state, tag)
    }

    /**
     * Creates a guideline at a specific offset from the right of the [ConstraintLayout].
     */
    fun createGuidelineFromRight(offset: Dp): VerticalAnchor.GuidelineAnchor {
        val tag = createId()
        state.verticalGuideline(tag).apply { end(offset) }
        return VerticalAnchor.GuidelineAnchor(state, tag)
    }

    /**
     * Creates a guideline at a width percentage from the right of the [ConstraintLayout].
     */
    fun createGuidelineFromRight(percent: Float): VerticalAnchor.GuidelineAnchor {
        return createGuidelineFromLeft(1f - percent)
    }

    /**
     * Creates a guideline at a specific offset from the top of the [ConstraintLayout].
     */
    fun createGuidelineFromTop(offset: Dp): HorizontalAnchor.GuidelineAnchor {
        val tag = createId()
        state.horizontalGuideline(tag).apply { start(offset) }
        return HorizontalAnchor.GuidelineAnchor(state, tag)
    }

    /**
     * Creates a guideline at a height percentage from the top of the [ConstraintLayout].
     */
    fun createGuidelineFromTop(percent: Float): HorizontalAnchor.GuidelineAnchor {
        val tag = createId()
        state.horizontalGuideline(tag).apply { percent(percent) }
        return HorizontalAnchor.GuidelineAnchor(state, tag)
    }

    /**
     * Creates a guideline at a specific offset from the bottom of the [ConstraintLayout].
     */
    fun createGuidelineFromBottom(offset: Dp): HorizontalAnchor.GuidelineAnchor {
        val tag = createId()
        state.horizontalGuideline(tag).apply { end(offset) }
        return HorizontalAnchor.GuidelineAnchor(state, tag)
    }

    /**
     * Creates a guideline at a height percentage from the bottom of the [ConstraintLayout].
     */
    fun createGuidelineFromBottom(percent: Float): HorizontalAnchor.GuidelineAnchor {
        return createGuidelineFromTop(1f - percent)
    }

    /**
     * Creates and returns a top barrier, containing the specified elements.
     */
    fun createTopBarrier(
        vararg elements: ConstrainedLayoutReference
    ): HorizontalAnchor.BarrierAnchor {
        val tag = createId()
        val barrier = state.barrier(tag, SolverDirection.TOP).apply {
            add(*(elements.map { it.tag }.toTypedArray()))
        }
        return HorizontalAnchor.BarrierAnchor(state, tag, barrier)
    }

    /**
     * Creates and returns a bottom barrier, containing the specified elements.
     */
    fun createBottomBarrier(
        vararg elements: ConstrainedLayoutReference
    ): HorizontalAnchor.BarrierAnchor {
        val tag = createId()
        val barrier = state.barrier(tag, SolverDirection.BOTTOM).apply {
            add(*(elements.map { it.tag }.toTypedArray()))
        }
        return HorizontalAnchor.BarrierAnchor(state, tag, barrier)
    }

    /**
     * Creates and returns a left barrier, containing the specified elements.
     */
    fun createLeftBarrier(
        vararg elements: ConstrainedLayoutReference
    ): VerticalAnchor.BarrierAnchor {
        val tag = createId()
        val barrier = state.barrier(tag, SolverDirection.START).apply {
            add(*(elements.map { it.tag }.toTypedArray()))
        }
        return VerticalAnchor.BarrierAnchor(state, tag, barrier)
    }

    /**
     * Creates and returns a right barrier, containing the specified elements.
     */
    fun createRightBarrier(
        vararg elements: ConstrainedLayoutReference
    ): VerticalAnchor.BarrierAnchor {
        val tag = createId()
        val barrier = state.barrier(tag, SolverDirection.END).apply {
            add(*(elements.map { it.tag }.toTypedArray()))
        }
        return VerticalAnchor.BarrierAnchor(state, tag, barrier)
    }

    internal companion object {
        val verticalAnchorFunctions:
                Array<Array<ConstraintReference.(Any) -> ConstraintReference>> = arrayOf(
            arrayOf(
                { other -> leftToLeft(other) },
                { other -> leftToRight(other) }
            ),
            arrayOf(
                { other -> rightToLeft(other) },
                { other -> rightToRight(other) }
            )
        )

        /**
         * Converts the index (-2 -> start, -1 -> end, 0 -> left, 1 -> right) to an index in
         * the arrays above (0 -> left, 1 -> right).
         */
        // TODO(popam, b/157886946): this is temporary until we can use CL's own RTL handling
        fun verticalAnchorIndexToFunctionIndex(index: Int, layoutDirection: LayoutDirection) =
            when {
                index >= 0 -> index // already left or right
                layoutDirection == LayoutDirection.Ltr -> 2 + index // start -> left, end -> right
                else -> -index - 1 // start -> right, end -> left
            }

        val horizontalAnchorFunctions:
                Array<Array<ConstraintReference.(Any) -> ConstraintReference>> = arrayOf(
            arrayOf(
                { other -> topToTop(other) },
                { other -> topToBottom(other) }
            ),
            arrayOf(
                { other -> bottomToTop(other) },
                { other -> bottomToBottom(other) }
            )
        )
        val baselineAnchorFunction: ConstraintReference.(Any) -> ConstraintReference =
            { other -> baselineToBaseline(other) }
    }
}

/**
 * The state of the [ConstraintLayout] solver.
 */
class State(val density: Density) : SolverState() {
    lateinit var rootIncomingConstraints: Constraints
    lateinit var layoutDirection: LayoutDirection

    override fun convertDimension(value: Any?): Int {
        return if (value is Dp) {
            with(density) { value.toIntPx() }
        } else {
            super.convertDimension(value)
        }
    }

    override fun reset() {
        // TODO(b/158197001): this should likely be done by the solver
        mReferences.clear()
        mReferences[PARENT] = mParent
        super.reset()
    }
}

private class Measurer internal constructor() : BasicMeasure.Measurer {
    private val root = ConstraintWidgetContainer(0, 0).also { it.measurer = this }
    private val placeables = mutableMapOf<Measurable, Placeable>()
    private val lastMeasures = mutableMapOf<Measurable, Array<Int>>()
    private val lastMeasureDefaultsHolder = arrayOf(0, 0, 0)
    private lateinit var density: Density
    private lateinit var measureScope: MeasureScope
    private val state by lazy(LazyThreadSafetyMode.NONE) { State(density) }

    private val widthConstraintsHolder = IntArray(2)
    private val heightConstraintsHolder = IntArray(2)

    fun reset() {
        placeables.clear()
        lastMeasures.clear()
        state.reset()
    }

    override fun measure(constraintWidget: ConstraintWidget, measure: BasicMeasure.Measure) {
        val measurable = constraintWidget.companionWidget
        if (measurable !is Measurable) return

        if (DEBUG) {
            Log.d(
                "CCL", "Measuring ${measurable.id} with: " +
                        constraintWidget.toDebugString() + "\n" + measure.toDebugString()
            )
        }

        val (initialWidth, initialHeight, initialBaseline) =
            lastMeasures[measurable] ?: lastMeasureDefaultsHolder.apply { copyFrom(measure) }

        var wrappingWidth: Boolean
        var wrappingHeight: Boolean
        var constraints: Constraints
        run {
            wrappingWidth = obtainConstraints(
                constraintWidget.horizontalDimensionBehaviour,
                constraintWidget.width,
                constraintWidget.mMatchConstraintDefaultWidth,
                measure.useDeprecated,
                constraintWidget.wrapMeasure[0],
                state.rootIncomingConstraints.maxWidth,
                widthConstraintsHolder
            )
            wrappingHeight = obtainConstraints(
                constraintWidget.verticalDimensionBehaviour,
                constraintWidget.height,
                constraintWidget.mMatchConstraintDefaultHeight,
                measure.useDeprecated,
                constraintWidget.wrapMeasure[1],
                state.rootIncomingConstraints.maxHeight,
                heightConstraintsHolder
            )

            constraints = Constraints(
                widthConstraintsHolder[0],
                widthConstraintsHolder[1],
                heightConstraintsHolder[0],
                heightConstraintsHolder[1]
            )
        }

        if (measure.useDeprecated ||
            constraintWidget.horizontalDimensionBehaviour != MATCH_CONSTRAINT ||
            constraintWidget.mMatchConstraintDefaultWidth != MATCH_CONSTRAINT_SPREAD ||
            constraintWidget.verticalDimensionBehaviour != MATCH_CONSTRAINT ||
            constraintWidget.mMatchConstraintDefaultHeight != MATCH_CONSTRAINT_SPREAD
        ) {
            if (DEBUG) {
                Log.d("CCL", "Measuring ${measurable.id} with $constraints")
            }
            val placeable = with(measureScope) {
                measurable.measure(constraints).also { placeables[measurable] = it }
            }
            if (DEBUG) {
                Log.d("CCL", "${measurable.id} is size ${placeable.width} ${placeable.height}")
            }
            if (wrappingWidth) {
                constraintWidget.wrapMeasure[0] = placeable.width
            }
            if (wrappingHeight) {
                constraintWidget.wrapMeasure[1] = placeable.height
            }

            val coercedWidth = placeable.width.coerceIn(
                constraintWidget.minWidth.takeIf { it > 0 },
                constraintWidget.maxWidth.takeIf { it > 0 }
            )
            val coercedHeight = placeable.height.coerceIn(
                constraintWidget.minHeight.takeIf { it > 0 },
                constraintWidget.maxHeight.takeIf { it > 0 }
            )

            var remeasure = false
            if (coercedWidth != placeable.width) {
                constraints = constraints.copy(
                    minWidth = coercedWidth,
                    maxWidth = coercedWidth
                )
                remeasure = true
            }
            if (coercedHeight != placeable.height) {
                constraints = constraints.copy(
                    minHeight = coercedHeight,
                    maxHeight = coercedHeight
                )
                remeasure = true
            }
            if (remeasure) {
                if (DEBUG) {
                    Log.d("CCL", "Remeasuring coerced ${measurable.id} with $constraints")
                }
                with(measureScope) {
                    measurable.measure(constraints).also { placeables[measurable] = it }
                }
            }
        }

        val currentPlaceable = placeables[measurable]
        measure.measuredWidth = currentPlaceable?.width ?: constraintWidget.width
        measure.measuredHeight = currentPlaceable?.height ?: constraintWidget.height
        val baseline = currentPlaceable?.get(FirstBaseline)
        measure.measuredHasBaseline = baseline != null
        if (baseline != null) measure.measuredBaseline = baseline
        lastMeasures.getOrPut(measurable, { arrayOf(0, 0, 0) }).copyFrom(measure)

        measure.measuredNeedsSolverPass = measure.measuredWidth != initialWidth ||
                measure.measuredHeight != initialHeight ||
                measure.measuredBaseline != initialBaseline
    }

    /**
     * Calculates the [Constraints] in one direction that should be used to measure a child,
     * based on the solver measure request. Returns `true` if the constraints correspond to a
     * wrap content measurement.
     */
    private fun obtainConstraints(
        dimensionBehaviour: ConstraintWidget.DimensionBehaviour,
        dimension: Int,
        matchConstraintDefaultDimension: Int,
        useDeprecated: Boolean,
        knownWrapContentSize: Int,
        rootMaxConstraint: Int,
        outConstraints: IntArray
    ): Boolean = when (dimensionBehaviour) {
        FIXED -> {
            outConstraints[0] = dimension
            outConstraints[1] = dimension
            false
        }
        WRAP_CONTENT -> {
            outConstraints[0] = 0
            outConstraints[1] = rootMaxConstraint
            true
        }
        MATCH_CONSTRAINT -> {
            val useDimension = useDeprecated &&
                    (matchConstraintDefaultDimension != MATCH_CONSTRAINT_WRAP ||
                            dimension != knownWrapContentSize)
            outConstraints[0] = if (useDimension) dimension else 0
            outConstraints[1] = if (useDimension) dimension else rootMaxConstraint
            !useDimension
        }
        else -> {
            error("MATCH_PARENT is not supported")
        }
    }

    fun Array<Int>.copyFrom(measure: BasicMeasure.Measure) {
        this[0] = measure.measuredWidth
        this[1] = measure.measuredHeight
        this[2] = measure.measuredBaseline
    }

    fun performMeasure(
        constraints: Constraints,
        layoutDirection: LayoutDirection,
        constraintSet: ConstraintSet,
        measurables: List<Measurable>,
        measureScope: MeasureScope
    ): IntSize {
        this.density = measureScope
        this.measureScope = measureScope
        reset()
        // Define the size of the ConstraintLayout.
        state.width(
            if (constraints.hasFixedWidth) {
                SolverDimension.Fixed(constraints.maxWidth)
            } else {
                SolverDimension.Wrap().min(constraints.minWidth)
            }
        )
        state.height(
            if (constraints.hasFixedHeight) {
                SolverDimension.Fixed(constraints.maxHeight)
            } else {
                SolverDimension.Wrap().min(constraints.minHeight)
            }
        )
        // Build constraint set and apply it to the state.
        state.rootIncomingConstraints = constraints
        state.layoutDirection = layoutDirection
        constraintSet.applyTo(state, measurables)
        state.apply(root)
        root.width = constraints.maxWidth
        root.height = constraints.maxHeight
        root.updateHierarchy()

        if (DEBUG) {
            root.debugName = "ConstraintLayout"
            root.children.forEach { child ->
                child.debugName = (child.companionWidget as? Measurable)?.id?.toString() ?: "NOTAG"
            }
            Log.d("CCL", "ConstraintLayout is asked to measure with $constraints")
            Log.d("CCL", root.toDebugString())
            for (child in root.children) {
                Log.d("CCL", child.toDebugString())
            }
        }

        // No need to set sizes and size modes as we passed them to the state above.
        root.measure(Optimizer.OPTIMIZATION_NONE, 0, 0, 0, 0, 0, 0, 0, 0)

        for (child in root.children) {
            val measurable = child.companionWidget
            if (measurable !is Measurable) continue
            val placeable = placeables[measurable]
            val currentWidth = placeable?.width
            val currentHeight = placeable?.height
            if (child.width != currentWidth || child.height != currentHeight) {
                if (DEBUG) {
                    Log.d(
                        "CCL",
                        "Final measurement for ${measurable.id} " +
                                "to confirm size ${child.width} ${child.height}"
                    )
                }
                with(measureScope) {
                    measurable.measure(
                        Constraints.fixed(child.width, child.height)
                    ).also { placeables[measurable] = it }
                }
            }
        }
        if (DEBUG) {
            Log.d("CCL", "ConstraintLayout is at the end ${root.width} ${root.height}")
        }

        return IntSize(root.width, root.height)
    }

    fun Placeable.PlacementScope.performLayout() {
        for (child in root.children) {
            val measurable = child.companionWidget
            if (measurable !is Measurable) continue
            // TODO(popam, b/157886946): check if measurer's rtl support should be used instead
            placeables[measurable]?.placeAbsolute(child.x, child.y)
        }
    }

    override fun didMeasures() { }
}

private typealias SolverDimension = androidx.constraintlayout.core.state.Dimension
private typealias SolverState = androidx.constraintlayout.core.state.State
private typealias SolverDirection = androidx.constraintlayout.core.state.State.Direction
private typealias SolverChain = androidx.constraintlayout.core.state.State.Chain
private val DEBUG = false
private fun ConstraintWidget.toDebugString() =
    "$debugName " +
            "width $width minWidth $minWidth maxWidth $maxWidth " +
            "height $height minHeight $minHeight maxHeight $maxHeight " +
            "HDB $horizontalDimensionBehaviour VDB $verticalDimensionBehaviour " +
            "MCW $mMatchConstraintDefaultWidth MCH $mMatchConstraintDefaultHeight " +
            "percentW $mMatchConstraintPercentWidth percentH $mMatchConstraintPercentHeight"
private fun BasicMeasure.Measure.toDebugString() =
    "use deprecated is $useDeprecated "

/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.ui.layout

import androidx.compose.runtime.Applier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ComposeNode
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.materialize
import androidx.compose.ui.node.ComposeUiNode
import androidx.compose.ui.node.LayoutNode
import androidx.compose.ui.node.LayoutNode.LayoutState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.createSubcomposition
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection

/**
 * Analogue of [Layout] which allows to subcompose the actual content during the measuring stage
 * for example to use the values calculated during the measurement as params for the composition
 * of the children.
 *
 * Possible use cases:
 * * You need to know the constraints passed by the parent during the composition and can't solve
 * your use case with just custom [Layout] or [LayoutModifier].
 * See [androidx.compose.foundation.layout.BoxWithConstraints].
 * * You want to use the size of one child during the composition of the second child.
 * * You want to compose your items lazily based on the available size. For example you have a
 * list of 100 items and instead of composing all of them you only compose the ones which are
 * currently visible(say 5 of them) and compose next items when the component is scrolled.
 *
 * @sample androidx.compose.ui.samples.SubcomposeLayoutSample
 *
 * @param modifier [Modifier] to apply for the layout.
 * @param measurePolicy Measure policy which provides ability to subcompose during the measuring.
 */
@Composable
fun SubcomposeLayout(
    modifier: Modifier = Modifier,
    measurePolicy: SubcomposeMeasureScope.(Constraints) -> MeasureResult
) {
    SubcomposeLayout(
        state = remember { SubcomposeLayoutState() },
        modifier = modifier,
        measurePolicy = measurePolicy
    )
}

/**
 * Analogue of [Layout] which allows to subcompose the actual content during the measuring stage
 * for example to use the values calculated during the measurement as params for the composition
 * of the children.
 *
 * Possible use cases:
 * * You need to know the constraints passed by the parent during the composition and can't solve
 * your use case with just custom [Layout] or [LayoutModifier].
 * See [androidx.compose.foundation.layout.BoxWithConstraints].
 * * You want to use the size of one child during the composition of the second child.
 * * You want to compose your items lazily based on the available size. For example you have a
 * list of 100 items and instead of composing all of them you only compose the ones which are
 * currently visible(say 5 of them) and compose next items when the component is scrolled.
 *
 * @sample androidx.compose.ui.samples.SubcomposeLayoutSample
 *
 * @param state the state object to be used by the layout.
 * @param modifier [Modifier] to apply for the layout.
 * @param measurePolicy Measure policy which provides ability to subcompose during the measuring.
 */
@Composable
fun SubcomposeLayout(
    state: SubcomposeLayoutState,
    modifier: Modifier = Modifier,
    measurePolicy: SubcomposeMeasureScope.(Constraints) -> MeasureResult
) {
    state.compositionContext = rememberCompositionContext()
    DisposableEffect(state) {
        onDispose {
            state.disposeCurrentNodes()
        }
    }

    val materialized = currentComposer.materialize(modifier)
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    ComposeNode<LayoutNode, Applier<Any>>(
        factory = LayoutNode.Constructor,
        update = {
            init(state.setRoot)
            set(materialized, ComposeUiNode.SetModifier)
            set(measurePolicy, state.setMeasurePolicy)
            set(density, ComposeUiNode.SetDensity)
            set(layoutDirection, ComposeUiNode.SetLayoutDirection)
        }
    )
}

/**
 * The receiver scope of a [SubcomposeLayout]'s measure lambda which adds ability to dynamically
 * subcompose a content during the measuring on top of the features provided by [MeasureScope].
 */
interface SubcomposeMeasureScope : MeasureScope {
    /**
     * Performs subcomposition of the provided [content] with given [slotId].
     *
     * @param slotId unique id which represents the slot we are composing into. If you have fixed
     * amount or slots you can use enums as slot ids, or if you have a list of items maybe an
     * index in the list or some other unique key can work. To be able to correctly match the
     * content between remeasures you should provide the object which is equals to the one you
     * used during the previous measuring.
     * @param content the composable content which defines the slot. It could emit multiple
     * layouts, in this case the returned list of [Measurable]s will have multiple elements.
     */
    fun subcompose(slotId: Any?, content: @Composable () -> Unit): List<Measurable>
}

/**
 * Contains the state used by [SubcomposeLayout].
 */
class SubcomposeLayoutState {
    internal var compositionContext: CompositionContext? = null

    // Pre-allocated lambdas to update LayoutNode
    internal val setRoot: LayoutNode.() -> Unit = { _root = this }
    internal val setMeasurePolicy:
        LayoutNode.(SubcomposeMeasureScope.(Constraints) -> MeasureResult) -> Unit =
            { measurePolicy = createMeasurePolicy(it) }

    // inner state
    private var _root: LayoutNode? = null
    private val root: LayoutNode get() = requireNotNull(_root)
    private var currentIndex = 0
    private val nodeToNodeState = mutableMapOf<LayoutNode, NodeState>()
    private val slodIdToNode = mutableMapOf<Any?, LayoutNode>()
    private val scope = Scope()
    private val precomposeMap = mutableMapOf<Any?, LayoutNode>()

    /**
     * `root.foldedChildren` list contains all the active children (used during the last measure
     * pass) plus n = `precomposedCount` nodes in the end of the list which were precomposed and
     * are waiting to be used during the next measure passes.
     */
    private var precomposedCount = 0

    internal fun subcompose(slotId: Any?, content: @Composable () -> Unit): List<Measurable> {
        val layoutState = root.layoutState
        check(layoutState == LayoutState.Measuring || layoutState == LayoutState.LayingOut) {
            "subcompose can only be used inside the measure or layout blocks"
        }

        val node = slodIdToNode.getOrPut(slotId) {
            val precomposed = precomposeMap.remove(slotId)
            if (precomposed != null) {
                check(precomposedCount > 0)
                precomposedCount--
                precomposed
            } else {
                createNodeAt(currentIndex)
            }
        }

        val itemIndex = root.foldedChildren.indexOf(node)
        if (itemIndex < currentIndex) {
            throw IllegalArgumentException(
                "$slotId was already used with subcompose during this measuring pass"
            )
        }
        if (currentIndex != itemIndex) {
            move(itemIndex, currentIndex)
        }
        currentIndex++

        subcompose(node, slotId, content)
        return node.children
    }

    private fun subcompose(node: LayoutNode, slotId: Any?, content: @Composable () -> Unit) {
        val nodeState = nodeToNodeState.getOrPut(node) {
            NodeState(slotId, {})
        }
        val hasPendingChanges = nodeState.composition?.hasInvalidations ?: true
        if (nodeState.content !== content || hasPendingChanges) {
            nodeState.content = content
            subcompose(node, nodeState)
        }
    }

    private fun subcompose(node: LayoutNode, nodeState: NodeState) {
        node.withNoSnapshotReadObservation {
            ignoreRemeasureRequests {
                val content = nodeState.content
                nodeState.composition = subcomposeInto(
                    existing = nodeState.composition,
                    container = node,
                    parent = compositionContext ?: error("parent composition reference not set"),
                    // Do not optimize this by passing nodeState.content directly; the additional
                    // composable function call from the lambda expression affects the scope of
                    // recomposition and recomposition of siblings.
                    composable = { content() }
                )
            }
        }
    }

    private fun subcomposeInto(
        existing: Composition?,
        container: LayoutNode,
        parent: CompositionContext,
        composable: @Composable () -> Unit
    ): Composition {
        return if (existing == null || existing.isDisposed) {
            createSubcomposition(container, parent)
        } else {
            existing
        }
            .apply {
                setContent(composable)
            }
    }

    private fun disposeAfterIndex(currentIndex: Int) {
        // this size is not including precomposed items in the end of the list
        val activeChildrenSize = root.foldedChildren.size - precomposedCount
        ignoreRemeasureRequests {
            for (i in currentIndex until activeChildrenSize) {
                disposeNode(root.foldedChildren[i])
            }
            root.removeAt(currentIndex, activeChildrenSize - currentIndex)
        }
    }

    private fun disposeNode(node: LayoutNode) {
        val nodeState = nodeToNodeState.remove(node)!!
        nodeState.composition!!.dispose()
        slodIdToNode.remove(nodeState.slotId)
    }

    private fun createMeasurePolicy(
        block: SubcomposeMeasureScope.(Constraints) -> MeasureResult
    ): MeasurePolicy = object : LayoutNode.NoIntrinsicsMeasurePolicy(
        error = "Intrinsic measurements are not currently supported by SubcomposeLayout"
    ) {
        override fun MeasureScope.measure(
            measurables: List<Measurable>,
            constraints: Constraints
        ): MeasureResult {
            scope.layoutDirection = layoutDirection
            scope.density = density
            scope.fontScale = fontScale
            currentIndex = 0
            val result = scope.block(constraints)
            val indexAfterMeasure = currentIndex
            return object : MeasureResult {
                override val width: Int
                    get() = result.width
                override val height: Int
                    get() = result.height
                override val alignmentLines: Map<AlignmentLine, Int>
                    get() = result.alignmentLines

                override fun placeChildren() {
                    currentIndex = indexAfterMeasure
                    result.placeChildren()
                    disposeAfterIndex(currentIndex)
                }
            }
        }
    }

    internal fun disposeCurrentNodes() {
        nodeToNodeState.values.forEach {
            it.composition!!.dispose()
        }
        nodeToNodeState.clear()
        slodIdToNode.clear()
    }

    /**
     * Composes the content for the given [slotId]. This makes the next scope.subcompose(slotId)
     * call during the measure pass faster as the content is already composed.
     *
     * If the [slotId] was precomposed already but after the future calculations ended up to not be
     * needed anymore (meaning this slotId is not going to be used during the measure pass
     * anytime soon) you can use [PrecomposedSlotHandle.dispose] on a returned object to dispose the
     * content.
     *
     * @param slotId unique id which represents the slot we are composing into.
     * @param content the composable content which defines the slot.
     * @return [PrecomposedSlotHandle] instance which allows you to dispose the content.
     */
    fun precompose(slotId: Any?, content: @Composable () -> Unit): PrecomposedSlotHandle {
        if (!slodIdToNode.containsKey(slotId)) {
            val node = precomposeMap.getOrPut(slotId) {
                createNodeAt(root.foldedChildren.size).also {
                    precomposedCount++
                }
            }
            subcompose(node, slotId, content)
        }
        return object : PrecomposedSlotHandle {
            override fun dispose() {
                val node = precomposeMap.remove(slotId)
                if (node != null) {
                    ignoreRemeasureRequests {
                        disposeNode(node)
                        val itemIndex = root.foldedChildren.indexOf(node)
                        check(itemIndex != -1)
                        root.removeAt(itemIndex, 1)
                        check(precomposedCount > 0)
                        precomposedCount--
                    }
                }
            }
        }
    }

    private fun createNodeAt(index: Int) = LayoutNode(isVirtual = true).also {
        ignoreRemeasureRequests {
            root.insertAt(index, it)
        }
    }

    private fun move(from: Int, to: Int, count: Int = 1) {
        ignoreRemeasureRequests {
            root.move(from, to, count)
        }
    }

    private inline fun ignoreRemeasureRequests(block: () -> Unit) =
        root.ignoreRemeasureRequests(block)

    private class NodeState(
        val slotId: Any?,
        var content: @Composable () -> Unit,
        var composition: Composition? = null
    )

    private inner class Scope : SubcomposeMeasureScope {
        // MeasureScope delegation
        override var layoutDirection: LayoutDirection = LayoutDirection.Rtl
        override var density: Float = 0f
        override var fontScale: Float = 0f

        override fun subcompose(slotId: Any?, content: @Composable () -> Unit) =
            this@SubcomposeLayoutState.subcompose(slotId, content)
    }

    /**
     * Instance of this interface is returned by [precompose] function.
     */
    interface PrecomposedSlotHandle {

        /**
         * This function allows to dispose the content for the slot which was precomposed
         * previously via [precompose].
         *
         * If this slot was already used during the regular measure pass via
         * [SubcomposeMeasureScope.subcompose] this function will do nothing.
         *
         * This could be useful if after the future calculations this item is not anymore expected to
         * be used during the measure pass anytime soon.
         */
        fun dispose()
    }
}

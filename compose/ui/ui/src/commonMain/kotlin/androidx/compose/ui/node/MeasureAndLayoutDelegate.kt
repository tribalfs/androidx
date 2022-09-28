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

package androidx.compose.ui.node

import androidx.compose.runtime.collection.mutableVectorOf
import androidx.compose.ui.layout.OnGloballyPositionedModifier
import androidx.compose.ui.node.LayoutNode.LayoutState.Idle
import androidx.compose.ui.node.LayoutNode.LayoutState.LayingOut
import androidx.compose.ui.node.LayoutNode.LayoutState.Measuring
import androidx.compose.ui.node.LayoutNode.LayoutState.LookaheadLayingOut
import androidx.compose.ui.node.LayoutNode.LayoutState.LookaheadMeasuring
import androidx.compose.ui.node.LayoutNode.UsageByParent.InLayoutBlock
import androidx.compose.ui.node.LayoutNode.UsageByParent.InMeasureBlock
import androidx.compose.ui.unit.Constraints

/**
 * Keeps track of [LayoutNode]s which needs to be remeasured or relaid out.
 *
 * Use [requestRemeasure] to schedule remeasuring or [requestRelayout] to schedule relayout.
 *
 * Use [measureAndLayout] to perform scheduled actions and [dispatchOnPositionedCallbacks] to
 * dispatch [OnGloballyPositionedModifier] callbacks for the nodes affected by the previous
 * [measureAndLayout] execution.
 */
internal class MeasureAndLayoutDelegate(private val root: LayoutNode) {
    /**
     * LayoutNodes that need measure or layout.
     */
    private val relayoutNodes = DepthSortedSet(Owner.enableExtraAssertions)

    /**
     * Whether any LayoutNode needs measure or layout.
     */
    val hasPendingMeasureOrLayout get() = relayoutNodes.isNotEmpty()

    /**
     * Flag to indicate that we're currently measuring.
     */
    private var duringMeasureLayout = false

    /**
     * Dispatches on positioned callbacks.
     */
    private val onPositionedDispatcher = OnPositionedDispatcher()

    /**
     * List of listeners that must be called after layout has completed.
     */
    private val onLayoutCompletedListeners = mutableVectorOf<Owner.OnLayoutCompletedListener>()

    /**
     * The current measure iteration. The value is incremented during the [measureAndLayout]
     * execution. Some [measureAndLayout] executions will increment it more than once.
     */
    var measureIteration: Long = 1L
        get() {
            require(duringMeasureLayout) {
                "measureIteration should be only used during the measure/layout pass"
            }
            return field
        }
        private set

    /**
     * Stores the list of [LayoutNode]s scheduled to be remeasured in the next measure/layout pass.
     * We were unable to mark them as needsRemeasure=true previously as this request happened
     * during the previous measure/layout pass and they were already measured as part of it.
     * See [requestRemeasure] for more details.
     */
    private val postponedMeasureRequests = mutableVectorOf<PostponedRequest>()

    private var rootConstraints: Constraints? = null

    /**
     * @param constraints The constraints to measure the root [LayoutNode] with
     */
    fun updateRootConstraints(constraints: Constraints) {
        if (rootConstraints != constraints) {
            require(!duringMeasureLayout)
            rootConstraints = constraints
            root.markMeasurePending()
            relayoutNodes.add(root)
        }
    }

    private val consistencyChecker: LayoutTreeConsistencyChecker? =
        if (Owner.enableExtraAssertions) {
            LayoutTreeConsistencyChecker(
                root,
                relayoutNodes,
                postponedMeasureRequests.asMutableList(),
            )
        } else {
            null
        }

    /**
     * Requests lookahead remeasure for this [layoutNode] and nodes affected by its measure result
     *
     * Note: This should only be called on a [LayoutNode] in the subtree defined in a
     * LookaheadLayout. The caller is responsible for checking with [LayoutNode.mLookaheadScope]
     * is valid (i.e. non-null) before calling this method.
     *
     * @return true if the [measureAndLayout] execution should be scheduled as a result
     * of the request.
     */
    fun requestLookaheadRemeasure(layoutNode: LayoutNode, forced: Boolean = false): Boolean {
        check(layoutNode.mLookaheadScope != null) {
            "Error: requestLookaheadRemeasure cannot be called on a node outside" +
                " LookaheadLayout"
        }
        return when (layoutNode.layoutState) {
            LookaheadMeasuring -> {
                // requestLookaheadRemeasure has already been called for this node or
                // we're currently measuring it, let's swallow.
                false
            }
            Measuring, LookaheadLayingOut, LayingOut -> {
                // requestLookaheadRemeasure is currently laying out and it is incorrect to
                // request lookahead remeasure now, let's postpone it.
                postponedMeasureRequests.add(
                    PostponedRequest(node = layoutNode, isLookahead = true, isForced = forced)
                )
                consistencyChecker?.assertConsistent()
                false
            }
            Idle -> {
                if (layoutNode.lookaheadMeasurePending && !forced) {
                    false
                } else {
                    layoutNode.markLookaheadMeasurePending()
                    layoutNode.markMeasurePending()
                    if (layoutNode.isPlacedInLookahead == true ||
                        layoutNode.canAffectParentInLookahead
                    ) {
                        if (layoutNode.parent?.lookaheadMeasurePending != true) {
                            relayoutNodes.add(layoutNode)
                        }
                    }
                    !duringMeasureLayout
                }
            }
        }
    }

    /**
     * Requests remeasure for this [layoutNode] and nodes affected by its measure result.
     *
     * @return true if the [measureAndLayout] execution should be scheduled as a result
     * of the request.
     */
    fun requestRemeasure(layoutNode: LayoutNode, forced: Boolean = false): Boolean =
        when (layoutNode.layoutState) {
            Measuring, LookaheadMeasuring -> {
                // requestMeasure has already been called for this node or
                // we're currently measuring it, let's swallow. example when it happens: we compose
                // DataNode inside BoxWithConstraints, this calls onRequestMeasure on DataNode's
                // parent, but this parent is BoxWithConstraints which is currently measuring.
                false
            }
            LookaheadLayingOut, LayingOut -> {
                // requestMeasure is currently laying out and it is incorrect to request remeasure
                // now, let's postpone it.
                postponedMeasureRequests.add(
                    PostponedRequest(node = layoutNode, isLookahead = false, isForced = forced)
                )
                consistencyChecker?.assertConsistent()
                false
            }
            Idle -> {
                if (layoutNode.measurePending && !forced) {
                    false
                } else {
                    layoutNode.markMeasurePending()
                    if (layoutNode.isPlaced || layoutNode.canAffectParent) {
                        if (layoutNode.parent?.measurePending != true) {
                            relayoutNodes.add(layoutNode)
                        }
                    }
                    !duringMeasureLayout
                }
            }
        }

    /**
     * Requests lookahead relayout for this [layoutNode] and nodes affected by its position.
     *
     * @return true if the [measureAndLayout] execution should be scheduled as a result
     * of the request.
     */
    fun requestLookaheadRelayout(layoutNode: LayoutNode, forced: Boolean = false): Boolean =
        when (layoutNode.layoutState) {
            LookaheadMeasuring, LookaheadLayingOut -> {
                // Don't need to do anything else since the parent is already scheduled
                // for a lookahead relayout (lookahead measure will trigger lookahead
                // relayout), or lookahead layout is in process right now
                consistencyChecker?.assertConsistent()
                false
            }
            Measuring, LayingOut, Idle -> {
                if ((layoutNode.lookaheadMeasurePending || layoutNode.lookaheadLayoutPending) &&
                    !forced
                ) {
                    // Don't need to do anything else since the parent is already scheduled
                    // for a lookahead relayout (lookahead measure will trigger lookahead
                    // relayout)
                    consistencyChecker?.assertConsistent()
                    false
                } else {
                    // Mark both lookahead layout and layout as pending, as layout has a
                    // dependency on lookahead layout.
                    layoutNode.markLookaheadLayoutPending()
                    layoutNode.markLayoutPending()
                    if (layoutNode.isPlacedInLookahead == true) {
                        val parent = layoutNode.parent
                        if (parent?.lookaheadMeasurePending != true &&
                            parent?.lookaheadLayoutPending != true
                        ) {
                            relayoutNodes.add(layoutNode)
                        }
                    }
                    !duringMeasureLayout
                }
            }
        }

    /**
     * Requests relayout for this [layoutNode] and nodes affected by its position.
     *
     * @return true if the [measureAndLayout] execution should be scheduled as a result
     * of the request.
     */
    fun requestRelayout(layoutNode: LayoutNode, forced: Boolean = false): Boolean =
        when (layoutNode.layoutState) {
            Measuring, LookaheadMeasuring, LookaheadLayingOut, LayingOut -> {
                // don't need to do anything else since the parent is already scheduled
                // for a relayout (measure will trigger relayout), or is laying out right now
                consistencyChecker?.assertConsistent()
                false
            }
            Idle -> {
                if (!forced && (layoutNode.measurePending || layoutNode.layoutPending)) {
                    // don't need to do anything else since the parent is already scheduled
                    // for a relayout (measure will trigger relayout), or is laying out right now
                    consistencyChecker?.assertConsistent()
                    false
                } else {
                    layoutNode.markLayoutPending()
                    if (layoutNode.isPlaced) {
                        val parent = layoutNode.parent
                        if (parent?.layoutPending != true && parent?.measurePending != true) {
                            relayoutNodes.add(layoutNode)
                        }
                    }
                    !duringMeasureLayout
                }
            }
        }

    /**
     * Request that [layoutNode] and children should call their position change callbacks.
     */
    fun requestOnPositionedCallback(layoutNode: LayoutNode) {
        onPositionedDispatcher.onNodePositioned(layoutNode)
    }

    /**
     * @return true if the [LayoutNode] size has been changed.
     */
    private fun doLookaheadRemeasure(layoutNode: LayoutNode, constraints: Constraints?): Boolean {
        if (layoutNode.mLookaheadScope == null) return false
        val lookaheadSizeChanged = if (constraints != null) {
            layoutNode.lookaheadRemeasure(constraints)
        } else {
            layoutNode.lookaheadRemeasure()
        }

        val parent = layoutNode.parent
        if (lookaheadSizeChanged && parent != null) {
            if (parent.mLookaheadScope == null) {
                requestRemeasure(parent)
            } else if (layoutNode.measuredByParentInLookahead == InMeasureBlock) {
                requestLookaheadRemeasure(parent)
            } else if (layoutNode.measuredByParentInLookahead == InLayoutBlock) {
                requestLookaheadRelayout(parent)
            }
        }
        return lookaheadSizeChanged
    }

    private fun doRemeasure(layoutNode: LayoutNode, constraints: Constraints?): Boolean {
        val sizeChanged = if (constraints != null) {
            layoutNode.remeasure(constraints)
        } else {
            layoutNode.remeasure()
        }
        val parent = layoutNode.parent
        if (sizeChanged && parent != null) {
            if (layoutNode.measuredByParent == InMeasureBlock) {
                requestRemeasure(parent)
            } else if (layoutNode.measuredByParent == InLayoutBlock) {
                requestRelayout(parent)
            }
        }
        return sizeChanged
    }

    /**
     * Iterates through all LayoutNodes that have requested layout and measures and lays them out
     */
    fun measureAndLayout(onLayout: (() -> Unit)? = null): Boolean {
        var rootNodeResized = false
        performMeasureAndLayout {
            if (relayoutNodes.isNotEmpty()) {
                relayoutNodes.popEach { layoutNode ->
                    val sizeChanged = remeasureAndRelayoutIfNeeded(layoutNode)
                    if (layoutNode === root && sizeChanged) {
                        rootNodeResized = true
                    }
                }
                onLayout?.invoke()
            }
        }
        callOnLayoutCompletedListeners()
        return rootNodeResized
    }

    /**
     * Only does measurement from the root without doing any placement. This is intended
     * to be called to determine only how large the root is with minimal effort.
     */
    fun measureOnly() {
        performMeasureAndLayout {
            recurseRemeasure(root)
        }
    }

    /**
     * Walks the hierarchy from [layoutNode] and remeasures [layoutNode] and any
     * descendants that affect its size.
     */
    private fun recurseRemeasure(layoutNode: LayoutNode) {
        remeasureOnly(layoutNode)

        layoutNode._children.forEach { child ->
            if (child.measureAffectsParent) {
                recurseRemeasure(child)
            }
        }
        // The child measurement may have invalidated layoutNode's measurement
        remeasureOnly(layoutNode)
    }

    fun measureAndLayout(layoutNode: LayoutNode, constraints: Constraints) {
        require(layoutNode != root)
        performMeasureAndLayout {
            relayoutNodes.remove(layoutNode)
            // we don't check for the layoutState as even if the node doesn't need remeasure
            // it could be remeasured because the constraints changed.
            val lookaheadSizeChanged = doLookaheadRemeasure(layoutNode, constraints)
            doRemeasure(layoutNode, constraints)
            if ((lookaheadSizeChanged || layoutNode.lookaheadLayoutPending) &&
                layoutNode.isPlacedInLookahead == true
            ) {
                layoutNode.lookaheadReplace()
            }
            if (layoutNode.layoutPending && layoutNode.isPlaced) {
                layoutNode.replace()
                onPositionedDispatcher.onNodePositioned(layoutNode)
            }
        }
        callOnLayoutCompletedListeners()
    }

    private inline fun performMeasureAndLayout(block: () -> Unit) {
        require(root.isAttached)
        require(root.isPlaced)
        require(!duringMeasureLayout)
        // we don't need to measure any children unless we have the correct root constraints
        if (rootConstraints != null) {
            duringMeasureLayout = true
            try {
                block()
            } finally {
                duringMeasureLayout = false
            }
            consistencyChecker?.assertConsistent()
        }
    }

    fun registerOnLayoutCompletedListener(listener: Owner.OnLayoutCompletedListener) {
        onLayoutCompletedListeners += listener
    }

    private fun callOnLayoutCompletedListeners() {
        onLayoutCompletedListeners.forEach { it.onLayoutComplete() }
        onLayoutCompletedListeners.clear()
    }

    /**
     * Does actual remeasure and relayout on the node if it is required.
     * The [layoutNode] should be already removed from [relayoutNodes] before running it.
     *
     * @return true if the [LayoutNode] size has been changed.
     */
    private fun remeasureAndRelayoutIfNeeded(layoutNode: LayoutNode): Boolean {
        var sizeChanged = false
        if (layoutNode.isPlaced ||
            layoutNode.canAffectParent ||
            layoutNode.isPlacedInLookahead == true ||
            layoutNode.canAffectParentInLookahead ||
            layoutNode.alignmentLinesRequired
        ) {
            var lookaheadSizeChanged = false
            if (layoutNode.lookaheadMeasurePending || layoutNode.measurePending) {
                val constraints = if (layoutNode === root) rootConstraints!! else null
                if (layoutNode.lookaheadMeasurePending) {
                    lookaheadSizeChanged = doLookaheadRemeasure(layoutNode, constraints)
                }
                sizeChanged = doRemeasure(layoutNode, constraints)
            }
            if ((lookaheadSizeChanged || layoutNode.lookaheadLayoutPending) &&
                layoutNode.isPlacedInLookahead == true
            ) {
                layoutNode.lookaheadReplace()
            }
            if (layoutNode.layoutPending && layoutNode.isPlaced) {
                if (layoutNode === root) {
                    layoutNode.place(0, 0)
                } else {
                    layoutNode.replace()
                }
                onPositionedDispatcher.onNodePositioned(layoutNode)
                consistencyChecker?.assertConsistent()
            }
            // execute postponed `onRequestMeasure`
            if (postponedMeasureRequests.isNotEmpty()) {
                postponedMeasureRequests.forEach { request ->
                    if (request.node.isAttached) {
                        if (!request.isLookahead) {
                            requestRemeasure(request.node, request.isForced)
                        } else {
                            requestLookaheadRemeasure(request.node, request.isForced)
                        }
                    }
                }
                postponedMeasureRequests.clear()
            }
        }
        return sizeChanged
    }

    /**
     * Remeasures [layoutNode] if it has [LayoutNode.measurePending] or
     * [LayoutNode.lookaheadMeasurePending].
     */
    private fun remeasureOnly(layoutNode: LayoutNode) {
        if (!layoutNode.measurePending && !layoutNode.lookaheadMeasurePending) {
            return // nothing needs to be remeasured
        }
        val constraints = if (layoutNode === root) rootConstraints!! else null
        if (layoutNode.lookaheadMeasurePending) {
            doLookaheadRemeasure(layoutNode, constraints)
        }
        doRemeasure(layoutNode, constraints)
    }

    /**
     * Makes sure the passed [layoutNode] and its subtree is remeasured and has the final sizes.
     *
     * The node or some of the nodes in its subtree can still be kept unmeasured if they are
     * not placed and don't affect the parent size. See [requestRemeasure] for details.
     */
    fun forceMeasureTheSubtree(layoutNode: LayoutNode) {
        // if there is nothing in `relayoutNodes` everything is remeasured.
        if (relayoutNodes.isEmpty()) {
            return
        }

        // assert that it is executed during the `measureAndLayout` pass.
        check(duringMeasureLayout)
        // if this node is not yet measured this invocation shouldn't be needed.
        require(!layoutNode.measurePending)

        layoutNode.forEachChild { child ->
            if (child.measurePending && relayoutNodes.remove(child)) {
                remeasureAndRelayoutIfNeeded(child)
            }

            // if the child is still in NeedsRemeasure state then this child remeasure wasn't
            // needed. it can happen for example when this child is not placed and can't affect
            // the parent size. we can skip the whole subtree.
            if (!child.measurePending) {
                // run recursively for the subtree.
                forceMeasureTheSubtree(child)
            }
        }

        // if the child was resized during the remeasurement it could request a remeasure on
        // the parent. we need to remeasure now as this function assumes the whole subtree is
        // fully measured as a result of the invocation.
        if (layoutNode.measurePending && relayoutNodes.remove(layoutNode)) {
            remeasureAndRelayoutIfNeeded(layoutNode)
        }
    }

    /**
     * Dispatch [OnPositionedModifier] callbacks for the nodes affected by the previous
     * [measureAndLayout] execution.
     *
     * @param forceDispatch true means the whole tree should dispatch the callback (for example
     * when the global position of the Owner has been changed)
     */
    fun dispatchOnPositionedCallbacks(forceDispatch: Boolean = false) {
        if (forceDispatch) {
            onPositionedDispatcher.onRootNodePositioned(root)
        }
        onPositionedDispatcher.dispatch()
    }

    /**
     * Removes [node] from the list of LayoutNodes being scheduled for the remeasure/relayout as
     * it was detached.
     */
    fun onNodeDetached(node: LayoutNode) {
        relayoutNodes.remove(node)
    }

    private val LayoutNode.measureAffectsParent
        get() = (measuredByParent == InMeasureBlock ||
            layoutDelegate.alignmentLinesOwner.alignmentLines.required)

    private val LayoutNode.canAffectParent
        get() = measurePending && measureAffectsParent

    private val LayoutNode.canAffectParentInLookahead
        get() = lookaheadLayoutPending &&
            (measuredByParentInLookahead == InMeasureBlock ||
                layoutDelegate.lookaheadAlignmentLinesOwner?.alignmentLines?.required == true)

    class PostponedRequest(val node: LayoutNode, val isLookahead: Boolean, val isForced: Boolean)
}

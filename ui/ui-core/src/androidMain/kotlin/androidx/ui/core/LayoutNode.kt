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

import androidx.ui.core.LayoutNode.LayoutState.Measuring
import androidx.ui.core.LayoutNode.LayoutState.NeedsRelayout
import androidx.ui.core.LayoutNode.LayoutState.NeedsRemeasure
import androidx.ui.core.LayoutNode.LayoutState.Ready
import androidx.ui.core.focus.FocusModifierImpl
import androidx.ui.core.focus.ModifiedFocusNode
import androidx.ui.core.keyinput.KeyInputModifier
import androidx.ui.core.keyinput.ModifiedKeyInputNode
import androidx.ui.core.pointerinput.PointerInputFilter
import androidx.ui.core.pointerinput.PointerInputModifier
import androidx.ui.core.semantics.SemanticsModifier
import androidx.ui.core.semantics.SemanticsWrapper
import androidx.ui.core.semantics.outerSemantics
import androidx.ui.core.focus.FocusModifier
import androidx.ui.geometry.Offset
import androidx.ui.geometry.Size
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.drawscope.DrawScope
import androidx.ui.graphics.drawscope.drawCanvas
import androidx.ui.unit.Density
import androidx.ui.util.fastForEach
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Enable to log changes to the LayoutNode tree.  This logging is quite chatty.
 */
private const val DebugChanges = false

// Top level DrawScope instance shared across the LayoutNode hierarchy to re-use internal
// drawing objects
internal val sharedDrawScope = LayoutNodeDrawScope()

/**
 * The base type for all nodes from the tree generated from a component hierarchy.
 *
 * Specific components are backed by a tree of nodes: Draw, Layout, GestureDetector.
 * All other components are not represented in the backing hierarchy.
 */
class LayoutNode : Measurable {
    private val _children = mutableListOf<LayoutNode>()

    /**
     * The children of this LayoutNode, controlled by [insertAt], [move], and [removeAt].
     */
    val children: List<LayoutNode> get() = _children

    /**
     * The parent node in the LayoutNode hierarchy. This is `null` when the `LayoutNode`
     * is attached (has an [owner]) and is the root of the tree or has not had [add] called for it.
     */
    var parent: LayoutNode? = null
        private set

    /**
     * The view system [Owner]. This `null` until [attach] is called
     */
    var owner: Owner? = null
        private set

    /**
     * The tree depth of the LayoutNode. This is valid only when [owner] is not `null`.
     */
    var depth: Int = 0

    /**
     * The layout state the node is currently in.
     */
    var layoutState = Ready
        internal set

    internal val wasMeasuredDuringThisIteration: Boolean
        get() = requireOwner().measureIteration == outerMeasurablePlaceable.measureIteration

    /**
     * Inserts a child [LayoutNode] at a particular index. If this LayoutNode [owner] is not `null`
     * then [instance] will become [attach]ed also. [instance] must have a `null` [parent].
     */
    fun insertAt(index: Int, instance: LayoutNode) {
        ErrorMessages.ComponentNodeHasParent.validateState(instance.parent == null)
        ErrorMessages.OwnerAlreadyAttached.validateState(instance.owner == null)

        if (DebugChanges) {
            println("$instance added to $this at index $index")
        }

        instance.parent = this
        _children.add(index, instance)

        instance.outerLayoutNodeWrapper.wrappedBy = innerLayoutNodeWrapper

        val owner = this.owner
        if (owner != null) {
            instance.attach(owner)
        }
    }

    /**
     * Removes one or more children, starting at [index].
     */
    fun removeAt(index: Int, count: Int) {
        ErrorMessages.CountOutOfRange.validateArg(count >= 0, count)
        val attached = owner != null
        for (i in index + count - 1 downTo index) {
            val child = _children.removeAt(i)
            if (DebugChanges) {
                println("$child removed from $this at index $i")
            }

            if (attached) {
                child.detach()
            }
            child.parent = null
        }
    }

    /**
     * Moves [count] elements starting at index [from] to index [to]. The [to] index is related to
     * the position before the change, so, for example, to move an element at position 1 to after
     * the element at position 2, [from] should be `1` and [to] should be `3`. If the elements
     * were LayoutNodes A B C D E, calling `move(1, 3, 1)` would result in the LayoutNodes
     * being reordered to A C B D E.
     */
    fun move(from: Int, to: Int, count: Int) {
        if (from == to) {
            return // nothing to do
        }

        for (i in 0 until count) {
            // if "from" is after "to," the from index moves because we're inserting before it
            val fromIndex = if (from > to) from + i else from
            val toIndex = if (from > to) to + i else to + count - 2
            val child = _children.removeAt(fromIndex)

            if (DebugChanges) {
                println("$child moved in $this from index $fromIndex to $toIndex")
            }

            _children.add(toIndex, child)
        }

        requestRemeasure()
    }

    /**
     * Set the [Owner] of this LayoutNode. This LayoutNode must not already be attached.
     * [owner] must match its [parent].[owner].
     */
    fun attach(owner: Owner) {
        ErrorMessages.OwnerAlreadyAttached.validateState(this.owner == null)
        val parent = parent
        ErrorMessages.ParentOwnerMustMatchChild.validateState(
            parent == null ||
                    parent.owner == owner
        )
        this.owner = owner
        this.depth = (parent?.depth ?: -1) + 1
        owner.onAttach(this)
        _children.fastForEach { child ->
            child.attach(owner)
        }

        requestRemeasure()
        parent?.requestRemeasure()
        outerLayoutNodeWrapper.attach()
        onAttach?.invoke(owner)
    }

    /**
     * Remove the LayoutNode from the [Owner]. The [owner] must not be `null` before this call
     * and its [parent]'s [owner] must be `null` before calling this. This will also [detach] all
     * children. After executing, the [owner] will be `null`.
     */
    fun detach() {
        val owner = owner ?: ErrorMessages.OwnerAlreadyDetached.state()
        val parentLayoutNode = parent
        if (parentLayoutNode != null) {
            parentLayoutNode.onInvalidate()
            parentLayoutNode.requestRemeasure()
        }
        alignmentLinesQueryOwner = null
        alignmentUsageByParent = UsageByParent.NotUsed
        onDetach?.invoke(owner)
        outerLayoutNodeWrapper.detach()

        if (outerSemantics != null) {
            owner.onSemanticsChange()
        }
        owner.onDetach(this)
        this.owner = null
        depth = 0
        _children.fastForEach { child ->
            child.detach()
        }
    }

    private val _zIndexSortedChildren = mutableListOf<LayoutNode>()

    /**
     * Returns the children list sorted by their [LayoutNode.zIndex].
     * Note that the object is reused so you shouldn't save it for later.
     */
    @PublishedApi
    internal val zIndexSortedChildren: List<LayoutNode>
        get() {
            _zIndexSortedChildren.clear()
            _zIndexSortedChildren.addAll(_children)
            _zIndexSortedChildren.sortWith(ZIndexComparator)
            return _zIndexSortedChildren
        }

    override fun toString(): String {
        return "${simpleIdentityToString(this)} children: ${_children.size} " +
                "measureBlocks: $measureBlocks"
    }

    /**
     * Call this method from the debugger to see a dump of the LayoutNode tree structure
     */
    private fun debugTreeToString(depth: Int = 0): String {
        val tree = StringBuilder()
        for (i in 0 until depth) {
            tree.append("  ")
        }
        tree.append("|-")
        tree.append(toString())
        tree.append('\n')

        children.fastForEach { child ->
            tree.append(child.debugTreeToString(depth + 1))
        }

        if (depth == 0) {
            // Delete trailing newline
            tree.deleteCharAt(tree.length - 1)
        }
        return tree.toString()
    }

    interface MeasureBlocks {
        /**
         * The function used to measure the child. It must call [MeasureScope.layout] before
         * completing.
         */
        fun measure(
            measureScope: MeasureScope,
            measurables: List<Measurable>,
            constraints: Constraints,
            layoutDirection: LayoutDirection
        ): MeasureScope.MeasureResult

        /**
         * The function used to calculate [IntrinsicMeasurable.minIntrinsicWidth].
         */
        fun minIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ): Int

        /**
         * The lambda used to calculate [IntrinsicMeasurable.minIntrinsicHeight].
         */
        fun minIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ): Int

        /**
         * The function used to calculate [IntrinsicMeasurable.maxIntrinsicWidth].
         */
        fun maxIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ): Int

        /**
         * The lambda used to calculate [IntrinsicMeasurable.maxIntrinsicHeight].
         */
        fun maxIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ): Int
    }

    abstract class NoIntrinsicsMeasureBlocks(private val error: String) : MeasureBlocks {
        override fun minIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ) = error(error)

        override fun minIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ) = error(error)

        override fun maxIntrinsicWidth(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            h: Int,
            layoutDirection: LayoutDirection
        ) = error(error)

        override fun maxIntrinsicHeight(
            intrinsicMeasureScope: IntrinsicMeasureScope,
            measurables: List<IntrinsicMeasurable>,
            w: Int,
            layoutDirection: LayoutDirection
        ) = error(error)
    }

    /**
     * Blocks that define the measurement and intrinsic measurement of the layout.
     */
    var measureBlocks: MeasureBlocks = ErrorMeasureBlocks
        set(value) {
            if (field != value) {
                field = value
                requestRemeasure()
            }
        }

    /**
     * The scope used to run the [MeasureBlocks.measure] [MeasureBlock].
     */
    val measureScope: MeasureScope = object : MeasureScope(), Density {
        private val ownerDensity: Density
            get() = owner?.density ?: Density(1f)
        override val density: Float
            get() = ownerDensity.density
        override val fontScale: Float
            get() = ownerDensity.fontScale
        override val layoutDirection: LayoutDirection get() = this@LayoutNode.layoutDirection
    }

    /**
     * The layout direction of the layout node.
     */
    internal var layoutDirection: LayoutDirection = LayoutDirection.Ltr

    /**
     * Implementation oddity around composition; used to capture a reference to this
     * [LayoutNode] when composed. This is a reverse property that mutates its right-hand side.
     */
    var ref: Ref<LayoutNode>?
        get() = null
        set(value) {
            value?.value = this
        }

    /**
     * The measured width of this layout and all of its [modifier]s. Shortcut for `size.width`.
     */
    val width: Int get() = outerMeasurablePlaceable.width

    /**
     * The measured height of this layout and all of its [modifier]s. Shortcut for `size.height`.
     */
    val height: Int get() = outerMeasurablePlaceable.height

    /**
     * The alignment lines of this layout, inherited + intrinsic
     */
    internal val alignmentLines: MutableMap<AlignmentLine, Int> = hashMapOf()

    /**
     * The alignment lines provided by this layout at the last measurement
     */
    internal val providedAlignmentLines: MutableMap<AlignmentLine, Int> = hashMapOf()

    internal val mDrawScope: LayoutNodeDrawScope = sharedDrawScope

    /**
     * Whether or not this has been placed in the hierarchy.
     */
    var isPlaced = false
        internal set

    /**
     * Remembers how the node was measured by the parent.
     */
    internal var measuredByParent: UsageByParent = UsageByParent.NotUsed

    /**
     * `true` while doing [calculateAlignmentLines]
     */
    private var isCalculatingAlignmentLines = false

    /**
     * `true` when the current node is positioned during the measure pass,
     * since it needs to compute alignment lines.
     */
    private var positionedDuringMeasurePass: Boolean = false

    /**
     * `true` when the parent reads our alignment lines
     */
    private var alignmentLinesRead = false

    private var alignmentLinesCalculatedDuringLastLayout = false

    /**
     * `true` when an ancestor relies on our alignment lines
     */
    internal val alignmentLinesRequired
        get() = alignmentLinesQueryOwner != null && alignmentLinesQueryOwner!!.alignmentLinesRead

    /**
     * Used by the parent to identify if the child has been queried for alignment lines since
     * last measurement.
     */
    private var alignmentLinesQueriedSinceLastLayout = false

    /**
     * The closest layout node above in the hierarchy which asked for alignment lines.
     */
    internal var alignmentLinesQueryOwner: LayoutNode? = null
        private set

    internal var alignmentUsageByParent = UsageByParent.NotUsed

    private val previousAlignmentLines = mutableMapOf<AlignmentLine, Int>()

    @Deprecated("Temporary API to support ConstraintLayout prototyping.")
    var canMultiMeasure: Boolean = false

    internal val innerLayoutNodeWrapper: LayoutNodeWrapper = InnerPlaceable(this)
    private val outerMeasurablePlaceable = OuterMeasurablePlaceable(this, innerLayoutNodeWrapper)
    internal val outerLayoutNodeWrapper: LayoutNodeWrapper
        get() = outerMeasurablePlaceable.outerWrapper

    /**
     * zIndex defines the drawing order of the LayoutNode. Children with larger zIndex are drawn
     * after others (the original order is used for the nodes with the same zIndex).
     * Default zIndex is 0. Current implementation is using the first(front) DrawLayerModifier's
     * elevation as a zIndex. We will have a separate zIndex modifier later instead to decouple
     * this features.
     */
    internal val zIndex: Float
        get() {
            @Suppress("DEPRECATION")
            return if (useChildZIndex) {
                // While some temporary components for adding semantics have to add
                // PassThroughLayout it breaks zIndex calculation via adding extra layout layer.
                // To workaround it we use the zIndex of the first child of PassThroughLayout
                children.firstOrNull()?.zIndex ?: 0f
            } else {
                outerZIndexModifier?.zIndex ?: 0f
            }
        }

    @Deprecated("To be removed when we remove PassThroughLayout")
    internal var useChildZIndex = false

    /**
     * The outermost ZIndexModifier in the modifier chain or `null` if there are no
     * ZIndexModifier in the modifier chain.
     */
    private var outerZIndexModifier: ZIndexModifier? = null

    /**
     * The inner-most layer wrapper. Used for performance for LayoutNodeWrapper.findLayer().
     */
    internal var innerLayerWrapper: LayerWrapper? = null

    /**
     * Returns the inner-most layer as part of this LayoutNode or from the containing LayoutNode.
     * This is added for performance so that LayoutNodeWrapper.findLayer() can be faster.
     */
    internal fun findLayer(): OwnedLayer? {
        return innerLayerWrapper?.layer ?: parent?.findLayer()
    }

    /**
     * The [Modifier] currently applied to this node.
     */
    var modifier: Modifier = Modifier
        set(value) {
            if (value == field) return
            field = value

            // Rebuild layoutNodeWrapper
            val oldOuterWrapper = outerMeasurablePlaceable.outerWrapper
            if (outerSemantics != null && isAttached()) {
                owner!!.onSemanticsChange()
            }
            val addedCallback = hasNewPositioningCallback()
            onPositionedCallbacks.clear()
            onChildPositionedCallbacks.clear()
            outerZIndexModifier = null
            innerLayerWrapper = null
            val outerWrapper = modifier.foldOut(innerLayoutNodeWrapper) { mod, toWrap ->
                var wrapper = toWrap
                // The order in which the following blocks occur matters.  For example, the
                // DrawModifier block should be before the LayoutModifier block so that a Modifier
                // that implements both DrawModifier and LayoutModifier will have it's draw bounds
                // reflect the dimensions defined by the LayoutModifier.
                if (mod is OnPositionedModifier) {
                    onPositionedCallbacks += mod
                }
                if (mod is OnChildPositionedModifier) {
                    onChildPositionedCallbacks += mod
                }
                if (mod is DrawModifier) {
                    wrapper = ModifiedDrawNode(wrapper, mod)
                }
                if (mod is DrawLayerModifier) {
                    val layerWrapper = LayerWrapper(wrapper, mod)
                    wrapper = layerWrapper
                    if (innerLayerWrapper == null) {
                        innerLayerWrapper = layerWrapper
                    }
                }
                if (mod is FocusModifier) {
                    require(mod is FocusModifierImpl)
                    wrapper = ModifiedFocusNode(wrapper, mod).also { mod.focusNode = it }
                }
                if (mod is KeyInputModifier) {
                    wrapper = ModifiedKeyInputNode(wrapper, mod).also { mod.keyInputNode = it }
                }
                if (mod is PointerInputModifier) {
                    wrapper = PointerInputDelegatingWrapper(wrapper, mod)
                }
                if (mod is LayoutModifier) {
                    wrapper = ModifiedLayoutNode(wrapper, mod)
                }
                if (mod is ParentDataModifier) {
                    wrapper = ModifiedParentDataNode(wrapper, mod)
                }
                if (mod is SemanticsModifier) {
                    wrapper = SemanticsWrapper(wrapper, mod)
                    if (isAttached()) {
                        owner!!.onSemanticsChange()
                    }
                }
                if (mod is ZIndexModifier) {
                    outerZIndexModifier = mod
                }
                wrapper
            }
            outerWrapper.wrappedBy = parent?.innerLayoutNodeWrapper
            outerMeasurablePlaceable.outerWrapper = outerWrapper
            // Optimize the case where the layout itself is not modified. A common reason for
            // this is if no wrapping actually occurs above because no LayoutModifiers are
            // present in the modifier chain.
            if (oldOuterWrapper != outerWrapper) {
                oldOuterWrapper.detach()
                requestRemeasure()
                parent?.requestRelayout()
                outerWrapper.attach()
            } else if (layoutState == Ready && addedCallback) {
                // We need to notify the callbacks of a change in position since there's
                // a new one.
                requestRemeasure()
            }
            owner?.onInvalidate(this)
        }

    @Deprecated(
        "Temporary API to support our transition from single child composables to modifiers."
    )
    // TODO(popam): remove this
    var handlesParentData: Boolean = true

    /**
     * Coordinates of just the contents of the LayoutNode, after being affected by all modifiers.
     */
    // TODO(mount): remove this
    val coordinates: LayoutCoordinates
        get() = innerLayoutNodeWrapper

    /**
     * Callback to be executed whenever the [LayoutNode] is attached to a new [Owner].
     */
    var onAttach: ((Owner) -> Unit)? = null

    /**
     * Callback to be executed whenever the [LayoutNode] is detached from an [Owner].
     */
    var onDetach: ((Owner) -> Unit)? = null

    /**
     * List of all OnPositioned callbacks in the modifier chain.
     */
    private val onPositionedCallbacks = mutableListOf<OnPositionedModifier>()

    /**
     * List of all OnChildPositioned callbacks in the modifier chain.
     */
    private val onChildPositionedCallbacks = mutableListOf<OnChildPositionedModifier>()

    fun place(x: Int, y: Int) {
        with(InnerPlacementScope) {
            this.parentLayoutDirection = layoutDirection
            val previousParentWidth = parentWidth
            this.parentWidth = outerMeasurablePlaceable.measuredWidth
            outerMeasurablePlaceable.place(x, y)
            this.parentWidth = previousParentWidth
        }
    }

    /**
     * Place this layout node again on the same position it was placed last time
     */
    internal fun replace() {
        outerMeasurablePlaceable.replace()
    }

    fun draw(canvas: Canvas) = outerLayoutNodeWrapper.draw(canvas)

    /**
     * Carries out a hit test on the [PointerInputModifier]s associated with this [LayoutNode] and
     * all [PointerInputModifier]s on all descendant [LayoutNode]s.
     *
     * If [pointerPositionRelativeToScreen] is within the bounds of any tested
     * [PointerInputModifier]s, the [PointerInputModifier] is added to [hitPointerInputFilters]
     * and true is returned.
     *
     * @param pointerPositionRelativeToScreen The tested pointer position, which is relative to
     * the device screen.
     * @param hitPointerInputFilters The collection that the hit [PointerInputFilter]s will be
     * added to if hit.
     */
    fun hitTest(
        pointerPositionRelativeToScreen: Offset,
        hitPointerInputFilters: MutableList<PointerInputFilter>
    ) {
        outerLayoutNodeWrapper.hitTest(pointerPositionRelativeToScreen, hitPointerInputFilters)
    }

    /**
     * Returns the alignment line value for a given alignment line without affecting whether
     * the flag for whether the alignment line was read.
     */
    fun getAlignmentLine(line: AlignmentLine): Int? {
        val linePos = alignmentLines[line] ?: return null
        var pos = Offset(linePos.toFloat(), linePos.toFloat())
        var wrapper = innerLayoutNodeWrapper
        while (wrapper != outerLayoutNodeWrapper) {
            pos = wrapper.toParentPosition(pos)
            wrapper = wrapper.wrappedBy!!
        }
        pos = wrapper.toParentPosition(pos)
        return if (line is HorizontalAlignmentLine) {
            pos.y.roundToInt()
        } else {
            pos.x.roundToInt()
        }
    }

    /**
     * Return true if there is a new [OnPositionedModifier] or [OnChildPositionedModifier]
     * assigned to this Layout.
     */
    private fun hasNewPositioningCallback(): Boolean {
        return modifier.foldOut(false) { mod, hasNewCallback ->
            hasNewCallback || when (mod) {
                is OnPositionedModifier -> mod !in onPositionedCallbacks
                is OnChildPositionedModifier -> mod !in onChildPositionedCallbacks
                else -> false
            }
        }
    }

    internal fun layoutChildren() {
        if (layoutState == NeedsRelayout) {
            onBeforeLayoutChildren()
        }
        // as a result of the previous operation we can figure out a child has been resized
        // and we need to be remeasured, not relaid out
        if (layoutState == NeedsRelayout) {
            layoutState = LayoutState.LayingOut
            val owner = requireOwner()
            val parent = parent
            owner.observeLayoutModelReads(this) {
                children.fastForEach { child ->
                    child.isPlaced = false
                    if (alignmentLinesRequired && child.layoutState == Ready &&
                        !child.alignmentLinesCalculatedDuringLastLayout) {
                        child.layoutState = NeedsRelayout
                    }
                    if (!child.alignmentLinesRequired) {
                        child.alignmentLinesQueryOwner = alignmentLinesQueryOwner
                    }
                    child.alignmentLinesQueriedSinceLastLayout = false
                }
                positionedDuringMeasurePass = parent != null &&
                        (parent.layoutState == Measuring ||
                                parent.positionedDuringMeasurePass)
                innerLayoutNodeWrapper.measureResult.placeChildren(layoutDirection)
                children.fastForEach { child ->
                    child.alignmentLinesRead = child.alignmentLinesQueriedSinceLastLayout
                }
            }

            alignmentLinesCalculatedDuringLastLayout = false
            if (alignmentLinesRequired) {
                alignmentLinesCalculatedDuringLastLayout = true
                previousAlignmentLines.clear()
                previousAlignmentLines.putAll(alignmentLines)
                alignmentLines.clear()
                children.fastForEach { child ->
                    if (!child.isPlaced) return@fastForEach
                    child.alignmentLines.keys.forEach { childLine ->
                        val linePositionInContainer = child.getAlignmentLine(childLine)!!
                        // If the line was already provided by a previous child, merge the values.
                        alignmentLines[childLine] = if (childLine in alignmentLines) {
                            childLine.merge(
                                alignmentLines.getValue(childLine),
                                linePositionInContainer
                            )
                        } else {
                            linePositionInContainer
                        }
                    }
                }
                alignmentLines += providedAlignmentLines
                if (previousAlignmentLines != alignmentLines) {
                    onAlignmentsChanged()
                }
            }
            layoutState = Ready
        }
    }

    /**
     * The callback to be executed before running layoutChildren.
     *
     * There are possible cases when we run layoutChildren() on the parent node, but some of its
     * children are not yet measured even if they are supposed to be measured in the measure
     * block of our parent.
     *
     * Example:
     * val child = Layout(...)
     * Layout(child) { measuruables, constraints ->
     *    val placeable = measurables.first().measure(constraints)
     *    layout(placeable.width, placeable.height) {
     *       placeable.place(0, 0)
     *    }
     * }
     * And now some set of changes scheduled remeasure for child and relayout for parent.
     *
     * During the [MeasureAndLayoutDelegate.measureAndLayout] we will start with the parent as it
     * has lower depth. Inside the layout block we will call placeable.width which is currently
     * dirty as the child was scheduled to remeasure. This callback will ensure it never happens
     * and pre-remeasure everything required for this layoutChildren().
     */
    private fun onBeforeLayoutChildren() {
        children.fastForEach {
            if (it.layoutState == NeedsRemeasure &&
                it.measuredByParent == UsageByParent.InMeasureBlock
            ) {
                if (it.remeasure()) {
                    requestRemeasure()
                }
            }
        }
    }

    internal fun onAlignmentsChanged() {
        val parent = parent
        if (parent != null) {
            if (alignmentUsageByParent == UsageByParent.InMeasureBlock &&
                parent.layoutState != LayoutState.LayingOut
            ) {
                parent.requestRemeasure()
            } else if (alignmentUsageByParent == UsageByParent.InLayoutBlock) {
                parent.requestRelayout()
            }
        }
    }

    internal fun calculateAlignmentLines(): Map<AlignmentLine, Int> {
        isCalculatingAlignmentLines = true
        alignmentLinesRead = true
        alignmentLinesQueryOwner = this
        alignmentLinesQueriedSinceLastLayout = true
        val newUsageByParent = when (parent?.layoutState) {
            Measuring -> UsageByParent.InMeasureBlock
            LayoutState.LayingOut -> UsageByParent.InLayoutBlock
            else -> UsageByParent.NotUsed
        }
        val newUsageHasLowerPriority = newUsageByParent == UsageByParent.InLayoutBlock &&
                alignmentUsageByParent == UsageByParent.InMeasureBlock
        if (!newUsageHasLowerPriority) {
            alignmentUsageByParent = newUsageByParent
        }
        if (layoutState == NeedsRelayout || !alignmentLinesCalculatedDuringLastLayout) {
            // layoutChildren() is a state transformation from NeedsRelayout to Ready.
            // when we are already in NeedsRelayout we need to end up with Ready, but if we are
            // currently measuring or need remeasure this extra layoutChildren is just a side effect
            // and we will need to restore the current state.
            val endState = if (layoutState == Measuring || layoutState == NeedsRemeasure) {
                layoutState
            } else {
                Ready
            }
            if (!alignmentLinesCalculatedDuringLastLayout) {
                layoutState = NeedsRelayout
            }
            layoutChildren()
            layoutState = endState
        }
        isCalculatingAlignmentLines = false
        return alignmentLines
    }

    internal fun handleMeasureResult(measureResult: MeasureScope.MeasureResult) {
        innerLayoutNodeWrapper.measureResult = measureResult
        this.providedAlignmentLines.clear()
        this.providedAlignmentLines += measureResult.alignmentLines
    }

    /**
     * Used to request a new measurement + layout pass from the owner.
     */
    fun requestRemeasure() {
        owner?.onRequestMeasure(this)
    }

    /**
     * Used to request a new layout pass from the owner.
     */
    fun requestRelayout() {
        owner?.onRequestRelayout(this)
    }

    /**
     * Used to request a new draw pass from the owner.
     */
    fun onInvalidate() {
        owner?.onInvalidate(this)
    }

    /**
     * Execute your code within the [block] if you want some code to not be observed for the
     * model reads even if you are currently inside some observed scope like measuring.
     */
    fun ignoreModelReads(block: () -> Unit) {
        requireOwner().pauseModelReadObserveration(block)
    }

    internal fun dispatchOnPositionedCallbacks() {
        if (layoutState != Ready) {
            return // it hasn't yet been properly positioned, so don't make a call
        }
        if (!isPlaced) {
            return // it hasn't been placed, so don't make a call
        }
        // There are two types of callbacks:
        // a) when the Layout is positioned - `onPositioned`
        // b) when the child of the Layout is positioned - `onChildPositioned`
        onPositionedCallbacks.fastForEach { it.onPositioned(coordinates) }
        parent?.onChildPositionedCallbacks?.fastForEach {
            it.onChildPositioned(coordinates)
        }
        // iterate through the subtree
        children.fastForEach { it.dispatchOnPositionedCallbacks() }
    }

    /**
     * This returns a new List of Modifiers and the coordinates and any extra information
     * that may be useful. This is used for tooling to retrieve layout modifier and layer
     * information.
     */
    fun getModifierInfo(): List<ModifierInfo> {
        val infoList = mutableListOf<ModifierInfo>()
        var wrapper = outerLayoutNodeWrapper

        while (wrapper != innerLayoutNodeWrapper) {
            val info = if (wrapper is LayerWrapper) {
                ModifierInfo(wrapper.modifier, wrapper, wrapper.layer)
            } else {
                wrapper as DelegatingLayoutNodeWrapper<*>
                ModifierInfo(wrapper.modifier, wrapper)
            }
            infoList += info
            wrapper = wrapper.wrapped!!
        }
        return infoList
    }

    // Delegation from Measurable to measurableAndPlaceable
    override fun measure(constraints: Constraints, layoutDirection: LayoutDirection) =
        outerMeasurablePlaceable.measure(constraints, layoutDirection)

    /**
     * Return true if the measured size has been changed
     */
    internal fun remeasure(
        constraints: Constraints = outerMeasurablePlaceable.lastConstraints!!,
        layoutDirection: LayoutDirection = outerMeasurablePlaceable.lastLayoutDirection!!
    ) = outerMeasurablePlaceable.remeasure(constraints, layoutDirection)

    override val parentData: Any? get() = outerMeasurablePlaceable.parentData

    override fun minIntrinsicWidth(height: Int, layoutDirection: LayoutDirection): Int =
        outerMeasurablePlaceable.minIntrinsicWidth(height, layoutDirection)

    override fun maxIntrinsicWidth(height: Int, layoutDirection: LayoutDirection): Int =
        outerMeasurablePlaceable.maxIntrinsicWidth(height, layoutDirection)

    override fun minIntrinsicHeight(width: Int, layoutDirection: LayoutDirection): Int =
        outerMeasurablePlaceable.minIntrinsicHeight(width, layoutDirection)

    override fun maxIntrinsicHeight(width: Int, layoutDirection: LayoutDirection): Int =
        outerMeasurablePlaceable.maxIntrinsicHeight(width, layoutDirection)

    internal companion object {
        private val ErrorMeasureBlocks = object : NoIntrinsicsMeasureBlocks(
            error = "Undefined intrinsics block and it is required"
        ) {
            override fun measure(
                measureScope: MeasureScope,
                measurables: List<Measurable>,
                constraints: Constraints,
                layoutDirection: LayoutDirection
            ) = error("Undefined measure and it is required")
        }
    }

    /**
     * Describes the current state the [LayoutNode] is in.
     */
    enum class LayoutState {
        /**
         * Request remeasure was called on the node.
         */
        NeedsRemeasure,
        /**
         * Node is currently being measured.
         */
        Measuring,
        /**
         * Request relayout was called on the node or the node was just measured and is going to
         * layout soon (measure stage is always being followed by the layout stage).
         */
        NeedsRelayout,
        /**
         * Node is currently being laid out.
         */
        LayingOut,
        /**
         * Node is measured and laid out or not yet attached to the [Owner] (see [LayoutNode.owner]).
         */
        Ready
    }

    internal enum class UsageByParent {
        InMeasureBlock,
        InLayoutBlock,
        NotUsed,
    }
}

/**
 * Object of pre-allocated lambdas used to make emits to LayoutNodes allocation-less.
 */
internal object LayoutEmitHelper {
    val constructor: () -> LayoutNode = { LayoutNode() }
    val setModifier: LayoutNode.(Modifier) -> Unit = { this.modifier = it }
    val setMeasureBlocks: LayoutNode.(LayoutNode.MeasureBlocks) -> Unit =
        { this.measureBlocks = it }
    val setRef: LayoutNode.(Ref<LayoutNode>) -> Unit = { this.ref = it }
}

/**
 * Comparator allowing to sort nodes by zIndex
 */
private val ZIndexComparator = Comparator<LayoutNode> { node1, node2 ->
    sign(node1.zIndex - node2.zIndex).toInt()
}

/**
 * Returns true if this [LayoutNode] currently has an [LayoutNode.owner].  Semantically,
 * this means that the LayoutNode is currently a part of a component tree.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun LayoutNode.isAttached() = owner != null

/**
 * Used by tooling to examine the modifiers on a [LayoutNode].
 */
class ModifierInfo(
    val modifier: Modifier,
    val coordinates: LayoutCoordinates,
    val extra: Any? = null
)

/**
 * Returns [LayoutNode.owner] or throws if it is null.
 */
internal fun LayoutNode.requireOwner(): Owner = owner ?: ErrorMessages.NodeShouldBeAttached.state()

/**
 * Inserts a child [LayoutNode] at a last index. If this LayoutNode [isAttached]
 * then [child] will become [isAttached]ed also. [child] must have a `null` [LayoutNode.parent].
 */
internal fun LayoutNode.add(child: LayoutNode) {
    insertAt(children.size, child)
}

/**
 * Executes [selector] on every parent of this [LayoutNode] and returns the closest
 * [LayoutNode] to return `true` from [selector] or null if [selector] returns false
 * for all ancestors.
 */
fun LayoutNode.findClosestParentNode(selector: (LayoutNode) -> Boolean): LayoutNode? {
    var currentParent = parent
    while (currentParent != null) {
        if (selector(currentParent)) {
            return currentParent
        } else {
            currentParent = currentParent.parent
        }
    }

    return null
}

/**
 * [ContentDrawScope] implementation that extracts density and layout direction information
 * from the given LayoutNodeWrapper
 */
internal class LayoutNodeDrawScope : ContentDrawScope() {

    // NOTE, currently a single ComponentDrawScope is shared across composables
    // which done to allocate a single set of Paint objects and re-use them across
    // draw calls for all composables.
    // As a result there could be thread safety concerns here for multi-threaded drawing
    // scenarios, generally a single ComponentDrawScope should be shared for a particular thread
    private var wrapped: LayoutNodeWrapper? = null

    override fun drawContent() {
        drawCanvas { canvas, _ ->
            wrapped?.draw(canvas)
        }
    }

    internal fun draw(
        canvas: Canvas,
        size: Size,
        layoutNodeWrapper: LayoutNodeWrapper,
        block: DrawScope.() -> Unit
    ) {
        val previousWrapper = wrapped
        wrapped = layoutNodeWrapper
        draw(canvas, size, block)
        wrapped = previousWrapper
    }

    override val density: Float
        get() = wrapped!!.measureScope.density

    override val fontScale: Float
        get() = wrapped!!.measureScope.fontScale

    override val layoutDirection: LayoutDirection
        get() = wrapped!!.measureScope.layoutDirection
}

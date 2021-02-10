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

@file:Suppress("NOTHING_TO_INLINE")

package androidx.compose.ui.node

import androidx.compose.ui.focus.FocusOrder
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.geometry.MutableRect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.toRect
import androidx.compose.ui.input.nestedscroll.NestedScrollDelegatingWrapper
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ReusableGraphicsLayerScope
import androidx.compose.ui.input.pointer.PointerInputFilter
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.findRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.minus
import androidx.compose.ui.unit.plus

/**
 * Measurable and Placeable type that has a position.
 */
internal abstract class LayoutNodeWrapper(
    internal val layoutNode: LayoutNode
) : Placeable(), Measurable, LayoutCoordinates, OwnerScope, (Canvas) -> Unit {
    internal open val wrapped: LayoutNodeWrapper? = null
    internal var wrappedBy: LayoutNodeWrapper? = null

    /**
     * The scope used to measure the wrapped. InnerPlaceables are using the MeasureScope
     * of the LayoutNode. For fewer allocations, everything else is reusing the measure scope of
     * their wrapped.
     */
    abstract val measureScope: MeasureScope

    // Size exposed to LayoutCoordinates.
    final override val size: IntSize get() = measuredSize

    private var isClipping: Boolean = false

    protected var layerBlock: (GraphicsLayerScope.() -> Unit)? = null
        private set

    private var _isAttached = false
    override val isAttached: Boolean
        get() {
            if (_isAttached) {
                require(layoutNode.isAttached)
            }
            return _isAttached
        }

    private var _measureResult: MeasureResult? = null
    open var measureResult: MeasureResult
        get() = _measureResult ?: error(UnmeasuredError)
        internal set(value) {
            if (value.width != _measureResult?.width || value.height != _measureResult?.height) {
                val layer = layer
                if (layer != null) {
                    layer.resize(IntSize(value.width, value.height))
                } else {
                    wrappedBy?.invalidateLayer()
                }
                layoutNode.owner?.onLayoutChange(layoutNode)
            }
            _measureResult = value
            measuredSize = IntSize(measureResult.width, measureResult.height)
        }

    var position: IntOffset = IntOffset.Zero
        private set

    var zIndex: Float = 0f
        protected set

    override val parentLayoutCoordinates: LayoutCoordinates?
        get() {
            check(isAttached) { ExpectAttachedLayoutCoordinates }
            return layoutNode.outerLayoutNodeWrapper.wrappedBy
        }

    override val parentCoordinates: LayoutCoordinates?
        get() {
            check(isAttached) { ExpectAttachedLayoutCoordinates }
            return wrappedBy?.getWrappedByCoordinates()
        }

    protected open fun getWrappedByCoordinates(): LayoutCoordinates? {
        return wrappedBy?.getWrappedByCoordinates()
    }

    // True when the wrapper is running its own placing block to obtain the position of the
    // wrapped, but is not interested in the position of the wrapped of the wrapped.
    var isShallowPlacing = false

    private var _rectCache: MutableRect? = null
    private val rectCache: MutableRect
        get() = _rectCache ?: MutableRect(0f, 0f, 0f, 0f).also {
            _rectCache = it
        }

    private val snapshotObserver get() = layoutNode.requireOwner().snapshotObserver

    // TODO (njawad): This cache matrix is not thread safe
    private var _matrixCache: Matrix? = null
    private val matrixCache: Matrix
        get() = _matrixCache ?: Matrix().also { _matrixCache = it }

    /**
     * Whether a pointer that is relative to the [LayoutNodeWrapper] is in the bounds of this
     * LayoutNodeWrapper.
     */
    fun isPointerInBounds(pointerPosition: Offset): Boolean {
        val x = pointerPosition.x
        val y = pointerPosition.y
        return x >= 0f && y >= 0f && x < measuredWidth && y < measuredHeight
    }

    /**
     * Measures the modified child.
     */
    abstract fun performMeasure(constraints: Constraints): Placeable

    /**
     * Measures the modified child.
     */
    final override fun measure(constraints: Constraints): Placeable {
        measurementConstraints = constraints
        val result = performMeasure(constraints)
        layer?.resize(measuredSize)
        return result
    }

    /**
     * Places the modified child.
     */
    /*@CallSuper*/
    override fun placeAt(
        position: IntOffset,
        zIndex: Float,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
        onLayerBlockUpdated(layerBlock)
        if (this.position != position) {
            this.position = position
            val layer = layer
            if (layer != null) {
                layer.move(position)
            } else {
                wrappedBy?.invalidateLayer()
            }
            layoutNode.owner?.onLayoutChange(layoutNode)
        }
        this.zIndex = zIndex
    }

    /**
     * Draws the content of the LayoutNode
     */
    fun draw(canvas: Canvas) {
        val layer = layer
        if (layer != null) {
            layer.drawLayer(canvas)
        } else {
            val x = position.x.toFloat()
            val y = position.y.toFloat()
            canvas.translate(x, y)
            performDraw(canvas)
            canvas.translate(-x, -y)
        }
    }

    protected abstract fun performDraw(canvas: Canvas)

    // implementation of draw block passed to the OwnedLayer
    override fun invoke(canvas: Canvas) {
        if (layoutNode.isPlaced) {
            require(layoutNode.layoutState == LayoutNode.LayoutState.Ready) {
                "Layer is redrawn for LayoutNode in state ${layoutNode.layoutState} [$layoutNode]"
            }
            snapshotObserver.observeReads(this, onCommitAffectingLayer) {
                performDraw(canvas)
            }
            lastLayerDrawingWasSkipped = false
        } else {
            // The invalidation is requested even for nodes which are not placed. As we are not
            // going to display them we skip the drawing. It is safe to just draw nothing as the
            // layer will be invalidated again when the node will be finally placed.
            lastLayerDrawingWasSkipped = true
        }
    }

    fun onLayerBlockUpdated(layerBlock: (GraphicsLayerScope.() -> Unit)?) {
        val blockHasBeenChanged = this.layerBlock !== layerBlock
        this.layerBlock = layerBlock
        if (isAttached && layerBlock != null) {
            if (layer == null) {
                layer = layoutNode.requireOwner().createLayer(
                    this,
                    invalidateParentLayer
                ).apply {
                    resize(measuredSize)
                    move(position)
                }
                updateLayerParameters()
                layoutNode.innerLayerWrapperIsDirty = true
                invalidateParentLayer()
            } else if (blockHasBeenChanged) {
                updateLayerParameters()
            }
        } else {
            layer?.let {
                it.destroy()
                layoutNode.innerLayerWrapperIsDirty = true
                invalidateParentLayer()
            }
            layer = null
        }
    }

    private fun updateLayerParameters() {
        val layer = layer
        if (layer != null) {
            val layerBlock = requireNotNull(layerBlock)
            graphicsLayerScope.reset()
            graphicsLayerScope.graphicsDensity = layoutNode.density
            snapshotObserver.observeReads(this, onCommitAffectingLayerParams) {
                layerBlock.invoke(graphicsLayerScope)
            }
            layer.updateLayerProperties(
                scaleX = graphicsLayerScope.scaleX,
                scaleY = graphicsLayerScope.scaleY,
                alpha = graphicsLayerScope.alpha,
                translationX = graphicsLayerScope.translationX,
                translationY = graphicsLayerScope.translationY,
                shadowElevation = graphicsLayerScope.shadowElevation,
                rotationX = graphicsLayerScope.rotationX,
                rotationY = graphicsLayerScope.rotationY,
                rotationZ = graphicsLayerScope.rotationZ,
                cameraDistance = graphicsLayerScope.cameraDistance,
                transformOrigin = graphicsLayerScope.transformOrigin,
                shape = graphicsLayerScope.shape,
                clip = graphicsLayerScope.clip,
                layoutDirection = layoutNode.layoutDirection
            )
            isClipping = graphicsLayerScope.clip
        } else {
            require(layerBlock == null)
        }
    }

    private val invalidateParentLayer: () -> Unit = {
        wrappedBy?.invalidateLayer()
    }

    /**
     * True when the last drawing of this layer didn't draw the real content as the LayoutNode
     * containing this layer was not placed by the parent.
     */
    internal var lastLayerDrawingWasSkipped = false
        private set

    var layer: OwnedLayer? = null
        private set

    override val isValid: Boolean
        get() = layer != null

    /**
     * Executes a hit test on any appropriate type associated with this [LayoutNodeWrapper].
     *
     * Override appropriately to either add a [PointerInputFilter] to [hitPointerInputFilters] or
     * to pass the execution on.
     *
     * @param pointerPosition The tested pointer position, which is relative to
     * the [LayoutNodeWrapper].
     * @param hitPointerInputFilters The collection that the hit [PointerInputFilter]s will be
     * added to if hit.
     */
    abstract fun hitTest(
        pointerPosition: Offset,
        hitPointerInputFilters: MutableList<PointerInputFilter>
    )

    override fun windowToLocal(relativeToWindow: Offset): Offset {
        check(isAttached) { ExpectAttachedLayoutCoordinates }
        val root = findRoot()
        val positionInRoot = layoutNode.requireOwner()
            .calculateLocalPosition(relativeToWindow) - root.positionInRoot()
        return localPositionOf(root, positionInRoot)
    }

    override fun localToWindow(relativeToLocal: Offset): Offset {
        val positionInRoot = localToRoot(relativeToLocal)
        val owner = layoutNode.requireOwner()
        return owner.calculatePositionInWindow(positionInRoot)
    }

    override fun localPositionOf(
        sourceCoordinates: LayoutCoordinates,
        relativeToSource: Offset
    ): Offset {
        val layoutNodeWrapper = sourceCoordinates as LayoutNodeWrapper
        val commonAncestor = findCommonAncestor(sourceCoordinates)

        var position = relativeToSource
        var wrapper = layoutNodeWrapper
        while (wrapper !== commonAncestor) {
            position = wrapper.toParentPosition(position)
            wrapper = wrapper.wrappedBy!!
        }

        return ancestorToLocal(commonAncestor, position)
    }

    override fun localBoundingBoxOf(
        sourceCoordinates: LayoutCoordinates,
        clipBounds: Boolean
    ): Rect {
        check(isAttached) { ExpectAttachedLayoutCoordinates }
        check(sourceCoordinates.isAttached) {
            "LayoutCoordinates $sourceCoordinates is not attached!"
        }
        val layoutNodeWrapper = sourceCoordinates as LayoutNodeWrapper
        val commonAncestor = findCommonAncestor(sourceCoordinates)

        val bounds = rectCache
        bounds.left = 0f
        bounds.top = 0f
        bounds.right = sourceCoordinates.size.width.toFloat()
        bounds.bottom = sourceCoordinates.size.height.toFloat()

        var wrapper = layoutNodeWrapper
        while (wrapper !== commonAncestor) {
            wrapper.rectInParent(bounds, clipBounds)
            if (bounds.isEmpty) {
                return Rect.Zero
            }

            wrapper = wrapper.wrappedBy!!
        }

        ancestorToLocal(commonAncestor, bounds, clipBounds)
        return bounds.toRect()
    }

    private fun ancestorToLocal(ancestor: LayoutNodeWrapper, offset: Offset): Offset {
        if (ancestor === this) {
            return offset
        }
        val wrappedBy = wrappedBy
        if (wrappedBy == null || ancestor == wrappedBy) {
            return fromParentPosition(offset)
        }
        return fromParentPosition(wrappedBy.ancestorToLocal(ancestor, offset))
    }

    private fun ancestorToLocal(
        ancestor: LayoutNodeWrapper,
        rect: MutableRect,
        clipBounds: Boolean
    ) {
        if (ancestor === this) {
            return
        }
        wrappedBy?.ancestorToLocal(ancestor, rect, clipBounds)
        return fromParentRect(rect, clipBounds)
    }

    override fun localToRoot(relativeToLocal: Offset): Offset {
        check(isAttached) { ExpectAttachedLayoutCoordinates }
        var wrapper: LayoutNodeWrapper? = this
        var position = relativeToLocal
        while (wrapper != null) {
            position = wrapper.toParentPosition(position)
            wrapper = wrapper.wrappedBy
        }
        return position
    }

    protected inline fun withPositionTranslation(canvas: Canvas, block: (Canvas) -> Unit) {
        val x = position.x.toFloat()
        val y = position.y.toFloat()
        canvas.translate(x, y)
        block(canvas)
        canvas.translate(-x, -y)
    }

    /**
     * Converts [position] in the local coordinate system to a [Offset] in the
     * [parentLayoutCoordinates] coordinate system.
     */
    open fun toParentPosition(position: Offset): Offset {
        val layer = layer
        val targetPosition = if (layer == null) {
            position
        } else {
            val matrix = matrixCache
            layer.getMatrix(matrix)
            matrix.map(position)
        }
        return targetPosition + this.position
    }

    /**
     * Converts [position] in the [parentLayoutCoordinates] coordinate system to a [Offset] in the
     * local coordinate system.
     */
    open fun fromParentPosition(position: Offset): Offset {
        val layer = layer
        val targetPosition = if (layer == null) {
            position
        } else {
            val inverse = matrixCache
            layer.getMatrix(inverse)
            inverse.invert()
            inverse.map(position)
        }
        return targetPosition - this.position
    }

    protected fun drawBorder(canvas: Canvas, paint: Paint) {
        val rect = Rect(
            left = 0.5f,
            top = 0.5f,
            right = measuredSize.width.toFloat() - 0.5f,
            bottom = measuredSize.height.toFloat() - 0.5f
        )
        canvas.drawRect(rect, paint)
    }

    /**
     * Attaches the [LayoutNodeWrapper] and its wrapped [LayoutNodeWrapper] to an active
     * LayoutNode.
     *
     * This will be called when the [LayoutNode] associated with this [LayoutNodeWrapper] is
     * attached to the [Owner].
     *
     * It is also called whenever the modifier chain is replaced and the [LayoutNodeWrapper]s are
     * recreated.
     */
    open fun attach() {
        _isAttached = true
        onLayerBlockUpdated(layerBlock)
    }

    /**
     * Detaches the [LayoutNodeWrapper] and its wrapped [LayoutNodeWrapper] from an active
     * LayoutNode.
     *
     * This will be called when the [LayoutNode] associated with this [LayoutNodeWrapper] is
     * detached from the [Owner].
     *
     * It is also called whenever the modifier chain is replaced and the [LayoutNodeWrapper]s are
     * recreated.
     */
    open fun detach() {
        _isAttached = false
        onLayerBlockUpdated(layerBlock)
        // The layer has been removed and we need to invalidate the containing layer. We've lost
        // which layer contained this one, but all layers in this modifier chain will be invalidated
        // in onModifierChanged(). Therefore the only possible layer that won't automatically be
        // invalidated is the parent's layer. We'll invalidate it here:
        layoutNode.parent?.invalidateLayer()
    }

    /**
     * Modifies bounds to be in the parent LayoutNodeWrapper's coordinates, including clipping,
     * if [clipBounds] is true.
     */
    private fun rectInParent(bounds: MutableRect, clipBounds: Boolean) {
        val layer = layer
        if (layer != null) {
            if (isClipping && clipBounds) {
                bounds.intersect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                if (bounds.isEmpty) {
                    return
                }
            }
            val matrix = matrixCache
            layer.getMatrix(matrix)
            matrix.map(bounds)
        }

        val x = position.x
        bounds.left += x
        bounds.right += x

        val y = position.y
        bounds.top += y
        bounds.bottom += y
    }

    /**
     * Modifies bounds in the parent's coordinates to be in this LayoutNodeWrapper's
     * coordinates, including clipping, if [clipBounds] is true.
     */
    private fun fromParentRect(bounds: MutableRect, clipBounds: Boolean) {
        val x = position.x
        bounds.left -= x
        bounds.right -= x

        val y = position.y
        bounds.top -= y
        bounds.bottom -= y

        val layer = layer
        if (layer != null) {
            val matrix = matrixCache
            layer.getMatrix(matrix)
            matrix.invert()
            matrix.map(bounds)
            if (isClipping && clipBounds) {
                bounds.intersect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                if (bounds.isEmpty) {
                    return
                }
            }
        }
    }

    protected fun withinLayerBounds(pointerPosition: Offset): Boolean {
        if (layer != null && isClipping) {
            return isPointerInBounds(pointerPosition)
        }

        // If we are here, either we aren't clipping to bounds or we are and the pointer was in
        // bounds.
        return true
    }

    /**
     * Invalidates the layer that this wrapper will draw into.
     */
    open fun invalidateLayer() {
        val layer = layer
        if (layer != null) {
            layer.invalidate()
        } else {
            wrappedBy?.invalidateLayer()
        }
    }

    /**
     * Returns the first [NestedScrollDelegatingWrapper] in the wrapper list that wraps this
     * [LayoutNodeWrapper].
     *
     * Note: This method tried to find [NestedScrollDelegatingWrapper] in the
     * modifiers before the one wrapped with this [LayoutNodeWrapper] and goes up the hierarchy of
     * [LayoutNode]s if needed.
     */
    abstract fun findPreviousNestedScrollWrapper(): NestedScrollDelegatingWrapper?

    /**
     * Returns the first [NestedScrollDelegatingWrapper] in the wrapper list that is wrapped by this
     * [LayoutNodeWrapper].
     *
     * Note: This method only goes to the modifiers that follow the one wrapped by
     * this [LayoutNodeWrapper], it doesn't to the children [LayoutNode]s.
     */
    abstract fun findNextNestedScrollWrapper(): NestedScrollDelegatingWrapper?

    /**
     * Returns the first [focus node][ModifiedFocusNode] in the wrapper list that wraps this
     * [LayoutNodeWrapper].
     *
     * Note: This method tried to find [NestedScrollDelegatingWrapper] in the
     * modifiers before the one wrapped with this [LayoutNodeWrapper] and goes up the hierarchy of
     * [LayoutNode]s if needed.
     */
    abstract fun findPreviousFocusWrapper(): ModifiedFocusNode?

    /**
     * Returns the next [focus node][ModifiedFocusNode] in the wrapper list that is wrapped by
     * this [LayoutNodeWrapper].
     *
     * Note: This method only goes to the modifiers that follow the one wrapped by
     * this [LayoutNodeWrapper], it doesn't to the children [LayoutNode]s.
     */
    abstract fun findNextFocusWrapper(): ModifiedFocusNode?

    /**
     * Returns the last [focus node][ModifiedFocusNode] found following this [LayoutNodeWrapper].
     * It searches the wrapper list associated with this [LayoutNodeWrapper].
     */
    abstract fun findLastFocusWrapper(): ModifiedFocusNode?

    /**
     * When the focus state changes, a [LayoutNodeWrapper] calls this function on the wrapper
     * that wraps it. The focus state change must be propagated to the parents until we reach
     * another [focus node][ModifiedFocusNode].
     */
    open fun propagateFocusEvent(focusState: FocusState) {
        wrappedBy?.propagateFocusEvent(focusState)
    }

    /**
     * Search up the component tree for any parent/parents that have specified a custom focus order.
     * Allowing parents higher up the hierarchy to overwrite the focus order specified by their
     * children.
     */
    open fun populateFocusOrder(focusOrder: FocusOrder) {
        wrappedBy?.populateFocusOrder(focusOrder)
    }

    /**
     * Find the first ancestor that is a [ModifiedFocusNode].
     */
    internal fun findParentFocusNode(): ModifiedFocusNode? {
        // TODO(b/152066829): We shouldn't need to search through the parentLayoutNode, as the
        // wrappedBy property should automatically point to the last layoutWrapper of the parent.
        // Find out why this doesn't work.
        var focusParent = wrappedBy?.findPreviousFocusWrapper()
        if (focusParent != null) {
            return focusParent
        }

        var parentLayoutNode = layoutNode.parent
        while (parentLayoutNode != null) {
            focusParent = parentLayoutNode.outerLayoutNodeWrapper.findLastFocusWrapper()
            if (focusParent != null) {
                return focusParent
            }
            parentLayoutNode = parentLayoutNode.parent
        }
        return null
    }

    /**
     *  Find the first ancestor that is a [ModifiedKeyInputNode].
     */
    internal fun findParentKeyInputNode(): ModifiedKeyInputNode? {
        // TODO(b/152066829): We shouldn't need to search through the parentLayoutNode, as the
        // wrappedBy property should automatically point to the last layoutWrapper of the parent.
        // Find out why this doesn't work.
        var keyInputParent = wrappedBy?.findPreviousKeyInputWrapper()
        if (keyInputParent != null) {
            return keyInputParent
        }

        var parentLayoutNode = layoutNode.parent
        while (parentLayoutNode != null) {
            keyInputParent = parentLayoutNode.outerLayoutNodeWrapper.findLastKeyInputWrapper()
            if (keyInputParent != null) {
                return keyInputParent
            }
            parentLayoutNode = parentLayoutNode.parent
        }
        return null
    }

    /**
     * Returns the first [ModifiedKeyInputNode] in the wrapper list that wraps this
     * [LayoutNodeWrapper].
     *
     * Note: This method tried to find [NestedScrollDelegatingWrapper] in the
     * modifiers before the one wrapped with this [LayoutNodeWrapper] and goes up the hierarchy of
     * [LayoutNode]s if needed.
     */
    abstract fun findPreviousKeyInputWrapper(): ModifiedKeyInputNode?

    /**
     * Returns the next [ModifiedKeyInputNode] in the wrapper list that is wrapped by this
     * [LayoutNodeWrapper].
     *
     * Note: This method only goes to the modifiers that follow the one wrapped by
     * this [LayoutNodeWrapper], it doesn't to the children [LayoutNode]s.
     */
    abstract fun findNextKeyInputWrapper(): ModifiedKeyInputNode?

    /**
     * Returns the last [focus node][ModifiedFocusNode] found following this [LayoutNodeWrapper].
     * It searches the wrapper list associated with this [LayoutNodeWrapper]
     */
    abstract fun findLastKeyInputWrapper(): ModifiedKeyInputNode?

    /**
     * Called when [LayoutNode.modifier] has changed and all the LayoutNodeWrappers have been
     * configured.
     */
    open fun onModifierChanged() {
        layer?.invalidate()
    }

    internal fun findCommonAncestor(other: LayoutNodeWrapper): LayoutNodeWrapper {
        var ancestor1 = other.layoutNode
        var ancestor2 = layoutNode
        if (ancestor1 === ancestor2) {
            // They are on the same node, but we don't know which is the deeper of the two
            val tooFar = layoutNode.outerLayoutNodeWrapper
            var tryMe = this
            while (tryMe !== tooFar && tryMe !== other) {
                tryMe = tryMe.wrappedBy!!
            }
            if (tryMe === other) {
                return other
            }
            return this
        }

        while (ancestor1.depth > ancestor2.depth) {
            ancestor1 = ancestor1.parent!!
        }

        while (ancestor2.depth > ancestor1.depth) {
            ancestor2 = ancestor2.parent!!
        }

        while (ancestor1 !== ancestor2) {
            val parent1 = ancestor1.parent
            val parent2 = ancestor2.parent
            if (parent1 == null || parent2 == null) {
                throw IllegalArgumentException("layouts are not part of the same hierarchy")
            }
            ancestor1 = parent1
            ancestor2 = parent2
        }

        return when {
            ancestor2 === layoutNode -> this
            ancestor1 === other.layoutNode -> other
            else -> ancestor1.innerLayoutNodeWrapper
        }
    }

    internal companion object {
        const val ExpectAttachedLayoutCoordinates = "LayoutCoordinate operations are only valid " +
            "when isAttached is true"
        const val UnmeasuredError = "Asking for measurement result of unmeasured layout modifier"
        private val onCommitAffectingLayerParams: (LayoutNodeWrapper) -> Unit = { wrapper ->
            if (wrapper.isValid) {
                wrapper.updateLayerParameters()
            }
        }
        private val onCommitAffectingLayer: (LayoutNodeWrapper) -> Unit = { wrapper ->
            wrapper.layer?.invalidate()
        }
        private val graphicsLayerScope = ReusableGraphicsLayerScope()
    }
}

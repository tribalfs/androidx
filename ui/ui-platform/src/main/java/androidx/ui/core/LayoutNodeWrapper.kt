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

package androidx.ui.core

import android.graphics.RectF
import androidx.ui.core.focus.ModifiedFocusNode
import androidx.ui.core.pointerinput.PointerInputFilter
import androidx.ui.core.pointerinput.PointerInputModifier
import androidx.ui.geometry.Rect
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.Paint
import androidx.ui.graphics.PaintingStyle
import androidx.ui.unit.Density
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxPosition
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.PxBounds
import androidx.ui.unit.PxPosition
import androidx.ui.unit.px
import androidx.ui.unit.round
import androidx.ui.unit.toPx
import androidx.ui.unit.toPxPosition
import androidx.ui.unit.toPxSize
import androidx.ui.util.fastAny
import androidx.ui.util.fastForEach

/**
 * Measurable and Placeable type that has a position.
 */
internal sealed class LayoutNodeWrapper(
    internal val layoutNode: LayoutNode
) : Placeable(), Measurable, LayoutCoordinates {
    internal open val wrapped: LayoutNodeWrapper? = null
    internal var wrappedBy: LayoutNodeWrapper? = null
    open var position = IntPxPosition.Origin

    private var dirtySize: Boolean = false
    fun hasDirtySize(): Boolean = dirtySize || (wrapped?.hasDirtySize() ?: false)
    // TODO(popam): avoid allocation here
    final override val size: IntPxSize get() = IntPxSize(measureResult.width, measureResult.height)

    private var _measureResult: MeasureScope.MeasureResult? = null
    var measureResult: MeasureScope.MeasureResult
        get() = _measureResult ?: error(UnmeasuredError)
        internal set(value) {
            if (value.width != _measureResult?.width || value.height != _measureResult?.height) {
                dirtySize = true
            }
            _measureResult = value
        }

    override val parentCoordinates: LayoutCoordinates?
        get() {
            check(isAttached) { ExpectAttachedLayoutCoordinates }
            return layoutNode.layoutNodeWrapper.wrappedBy
        }

    // TODO(mount): This is not thread safe.
    private var rectCache: RectF? = null

    /**
     * Whether a pointer that is relative to the device screen is in the bounds of this
     * LayoutNodeWrapper.
     */
    fun isGlobalPointerInBounds(globalPointerPosition: PxPosition): Boolean {
        // TODO(shepshapard): Right now globalToLocal has to traverse the tree all the way back up
        //  so calling this is expensive.  Would be nice to cache data such that this is cheap.
        val localPointerPosition = globalToLocal(globalPointerPosition)
        return localPointerPosition.x.value >= 0 &&
                localPointerPosition.x < size.width &&
                localPointerPosition.y.value >= 0 &&
                localPointerPosition.y < size.height
    }

    /**
     * Places the modified child.
     */
    abstract fun place(position: IntPxPosition)

    /**
     * Places the modified child.
     */
    final override fun performPlace(position: IntPxPosition) {
        place(position)
        dirtySize = false
    }

    /**
     * Draws the content of the LayoutNode
     */
    abstract fun draw(canvas: Canvas, density: Density)

    /**
     * Executes a hit test on any appropriate type associated with this [LayoutNodeWrapper].
     *
     * Override appropriately to either add a [PointerInputFilter] to [hitPointerInputFilters] or
     * to pass the execution on.
     *
     * @param pointerPositionRelativeToScreen The tested pointer position, which is relative to
     * the device screen.
     * @param hitPointerInputFilters The collection that the hit [PointerInputFilter]s will be
     * added to if hit.
     *
     * @return True if any [PointerInputFilter]s were hit and thus added to
     * [hitPointerInputFilters].
     */
    abstract fun hitTest(
        pointerPositionRelativeToScreen: PxPosition,
        hitPointerInputFilters: MutableList<PointerInputFilter>
    ): Boolean

    override fun childToLocal(child: LayoutCoordinates, childLocal: PxPosition): PxPosition {
        check(isAttached) { ExpectAttachedLayoutCoordinates }
        check(child.isAttached) { "Child $child is not attached!" }
        var wrapper = child as LayoutNodeWrapper
        var position = childLocal
        while (wrapper !== this) {
            position = wrapper.toParentPosition(position)

            val parent = wrapper.wrappedBy
            check(parent != null) {
                "childToLocal: child parameter is not a child of the LayoutCoordinates"
            }
            wrapper = parent
        }
        return position
    }

    override fun globalToLocal(global: PxPosition): PxPosition {
        check(isAttached) { ExpectAttachedLayoutCoordinates }
        val wrapper = wrappedBy ?: return fromParentPosition(
            global - layoutNode.requireOwner().calculatePosition().toPxPosition()
        )
        return fromParentPosition(wrapper.globalToLocal(global))
    }

    override fun localToGlobal(local: PxPosition): PxPosition {
        return localToRoot(local) + layoutNode.requireOwner().calculatePosition()
    }

    override fun localToRoot(local: PxPosition): PxPosition {
        check(isAttached) { ExpectAttachedLayoutCoordinates }
        var wrapper: LayoutNodeWrapper? = this
        var position = local
        while (wrapper != null) {
            position = wrapper.toParentPosition(position)
            wrapper = wrapper.wrappedBy
        }
        return position
    }

    protected inline fun withPositionTranslation(canvas: Canvas, block: (Canvas) -> Unit) {
        val x = position.x.value.toFloat()
        val y = position.y.value.toFloat()
        canvas.translate(x, y)
        block(canvas)
        canvas.translate(-x, -y)
    }

    /**
     * Converts [position] in the local coordinate system to a [PxPosition] in the
     * [parentCoordinates] coordinate system.
     */
    open fun toParentPosition(position: PxPosition): PxPosition = position + this.position

    /**
     * Converts [position] in the [parentCoordinates] coordinate system to a [PxPosition] in the
     * local coordinate system.
     */
    open fun fromParentPosition(position: PxPosition): PxPosition = position - this.position

    protected fun drawBorder(canvas: Canvas, paint: Paint) {
        val rect = Rect(
            left = 0.5f,
            top = 0.5f,
            right = size.width.value.toFloat() - 0.5f,
            bottom = size.height.value.toFloat() - 0.5f
        )
        canvas.drawRect(rect, paint)
    }

    /**
     * Detaches the LayoutNodeWrapper and its wrapped LayoutNodeWrapper from an active LayoutNode.
     * This will be called whenever the modifier chain is replaced and the LayoutNodeWrappers
     * are recreated.
     */
    abstract fun detach()

    /**
     * Modifies bounds to be in the parent LayoutNodeWrapper's coordinates, including clipping,
     * scaling, etc.
     */
    protected open fun rectInParent(bounds: RectF) {
        val x = position.x.value
        bounds.left += x
        bounds.right += x

        val y = position.y.value
        bounds.top += y
        bounds.bottom += y
    }

    override fun childBoundingBox(child: LayoutCoordinates): PxBounds {
        check(isAttached) { ExpectAttachedLayoutCoordinates }
        check(child.isAttached) { "Child $child is not attached!" }
        val bounds = rectCache ?: RectF().also { rectCache = it }
        bounds.set(
            0f,
            0f,
            child.size.width.value.toFloat(),
            child.size.height.value.toFloat()
        )
        var wrapper = child as LayoutNodeWrapper
        while (wrapper !== this) {
            wrapper.rectInParent(bounds)

            val parent = wrapper.wrappedBy
            check(parent != null) {
                "childToLocal: child parameter is not a child of the LayoutCoordinates"
            }
            wrapper = parent
        }
        return PxBounds(
            left = bounds.left.px,
            top = bounds.top.px,
            right = bounds.right.px,
            bottom = bounds.bottom.px
        )
    }

    /**
     * Returns the layer that this wrapper will draw into.
     */
    abstract fun findLayer(): OwnedLayer?

    /**
     * Returns the first [ModifiedFocusNode] in the wrapper list that wraps this
     * [LayoutNodeWrapper].
     */
    abstract fun findFocusWrapperWrappingThisWrapper(): ModifiedFocusNode?

    /**
     * Returns the next [ModifiedFocusNode] in the wrapper list that is wrapped by this
     * [LayoutNodeWrapper].
     */
    abstract fun findFocusWrapperWrappedByThisWrapper(): ModifiedFocusNode?

    /**
     * Returns the last [ModifiedFocusNode] found following this [LayoutNodeWrapper]. It searches
     * the wrapper list associated with this [LayoutNodeWrapper]
     */
    abstract fun findLastFocusWrapper(): ModifiedFocusNode?

    /**
     * Find the first ancestor that is a [ModifiedFocusNode].
     */
    internal fun findParentFocusNode(): ModifiedFocusNode? {
        // TODO(b/152066829): We shouldn't need to search through the parentLayoutNode, as the
        // wrappedBy property should automatically point to the last layoutWrapper of the parent.
        // Find out why this doesn't work.
        var focusParent = wrappedBy?.findFocusWrapperWrappingThisWrapper()
        if (focusParent != null) {
            return focusParent
        }

        var parentLayoutNode = layoutNode.parentLayoutNode
        while (parentLayoutNode != null) {
            focusParent = parentLayoutNode.layoutNodeWrapper.findLastFocusWrapper()
            if (focusParent != null) {
                return focusParent
            }
            parentLayoutNode = parentLayoutNode.parentLayoutNode
        }
        return null
    }

    internal companion object {
        const val ExpectAttachedLayoutCoordinates = "LayoutCoordinate operations are only valid " +
                "when isAttached is true"
        const val UnmeasuredError = "Asking for measurement result of unmeasured layout modifier"
    }
}

/**
 * [LayoutNodeWrapper] with default implementations for methods.
 */
internal open class DelegatingLayoutNodeWrapper(
    override val wrapped: LayoutNodeWrapper
) : LayoutNodeWrapper(wrapped.layoutNode) {
    override val providedAlignmentLines: Set<AlignmentLine>
        get() = wrapped.providedAlignmentLines

    private var _isAttached = true
    override val isAttached: Boolean
        get() = _isAttached && layoutNode.isAttached()

    init {
        wrapped.wrappedBy = this
    }

    override fun draw(canvas: Canvas, density: Density) {
        withPositionTranslation(canvas) {
            wrapped.draw(canvas, density)
        }
    }

    override fun hitTest(
        pointerPositionRelativeToScreen: PxPosition,
        hitPointerInputFilters: MutableList<PointerInputFilter>
    ): Boolean {
        if (isGlobalPointerInBounds(pointerPositionRelativeToScreen)) {
            return wrapped.hitTest(pointerPositionRelativeToScreen, hitPointerInputFilters)
        } else {
            // Anything out of bounds of ourselves can't be hit.
            return false
        }
    }

    override fun get(line: AlignmentLine): IntPx? {
        val value = wrapped[line] ?: return null
        val px = value.toPx()
        val pos = wrapped.toParentPosition(PxPosition(px, px))
        return if (line is HorizontalAlignmentLine) pos.y.round() else pos.y.round()
    }

    override fun place(position: IntPxPosition) {
        this.position = position
        measureResult.placeChildren(layoutNode.layoutDirection!!)
    }

    override fun measure(constraints: Constraints): Placeable {
        val placeable = wrapped.measure(constraints)
        measureResult = object : MeasureScope.MeasureResult {
            override val width: IntPx = wrapped.measureResult.width
            override val height: IntPx = wrapped.measureResult.height
            override val alignmentLines: Map<AlignmentLine, IntPx> = emptyMap()
            override fun placeChildren(layoutDirection: LayoutDirection) {
                placeable.placeAbsolute(IntPxPosition.Origin)
            }
        }
        return this
    }

    override fun findLayer(): OwnedLayer? {
        return wrappedBy?.findLayer()
    }

    override fun findFocusWrapperWrappingThisWrapper() =
        wrappedBy?.findFocusWrapperWrappingThisWrapper()

    override fun findFocusWrapperWrappedByThisWrapper() =
        wrapped.findFocusWrapperWrappedByThisWrapper()

    override fun findLastFocusWrapper(): ModifiedFocusNode? {
        var lastFocusWrapper: ModifiedFocusNode? = null

        // Find last focus wrapper for the current layout node.
        var next: ModifiedFocusNode? = findFocusWrapperWrappedByThisWrapper()
        while (next != null) {
            lastFocusWrapper = next
            next = next.wrapped.findFocusWrapperWrappedByThisWrapper()
        }
        return lastFocusWrapper
    }

    override fun minIntrinsicWidth(height: IntPx) = wrapped.minIntrinsicWidth(height)
    override fun maxIntrinsicWidth(height: IntPx) = wrapped.maxIntrinsicWidth(height)
    override fun minIntrinsicHeight(width: IntPx) = wrapped.minIntrinsicHeight(width)
    override fun maxIntrinsicHeight(width: IntPx) = wrapped.maxIntrinsicHeight(width)
    override val parentData: Any? get() = wrapped.parentData

    override fun detach() {
        _isAttached = false
        wrapped.detach()
    }
}

internal class InnerPlaceable(
    layoutNode: LayoutNode
) : LayoutNodeWrapper(layoutNode), Density by layoutNode.measureScope {

    override val providedAlignmentLines: Set<AlignmentLine>
        get() = layoutNode.providedAlignmentLines.keys
    override val isAttached: Boolean
        get() = layoutNode.isAttached()

    override fun measure(constraints: Constraints): Placeable {
        val measureResult = layoutNode.measureBlocks.measure(
            layoutNode.measureScope,
            layoutNode.layoutChildren,
            constraints,
            layoutNode.layoutDirection!!
        )
        layoutNode.handleMeasureResult(measureResult)
        return this
    }

    override val parentData: Any?
        @Suppress("DEPRECATION")
        get() = if (layoutNode.handlesParentData) {
            layoutNode.parentDataNode?.value
        } else {
            layoutNode.parentDataNode?.value
                ?: layoutNode.layoutChildren
                    .firstOrNull { it.layoutNodeWrapper.parentData != null }?.parentData
        }

    override fun findLayer(): OwnedLayer? {
        return wrappedBy?.findLayer()
    }

    override fun findFocusWrapperWrappingThisWrapper() =
        wrappedBy?.findFocusWrapperWrappingThisWrapper()

    override fun findFocusWrapperWrappedByThisWrapper() = null

    override fun findLastFocusWrapper(): ModifiedFocusNode? = findFocusWrapperWrappingThisWrapper()

    override fun minIntrinsicWidth(height: IntPx): IntPx {
        return layoutNode.measureBlocks.minIntrinsicWidth(
            layoutNode.measureScope,
            layoutNode.layoutChildren,
            height,
            layoutNode.layoutDirection!!
        )
    }

    override fun minIntrinsicHeight(width: IntPx): IntPx {
        return layoutNode.measureBlocks.minIntrinsicHeight(
            layoutNode.measureScope,
            layoutNode.layoutChildren,
            width,
            layoutNode.layoutDirection!!
        )
    }

    override fun maxIntrinsicWidth(height: IntPx): IntPx {
        return layoutNode.measureBlocks.maxIntrinsicWidth(
            layoutNode.measureScope,
            layoutNode.layoutChildren,
            height,
            layoutNode.layoutDirection!!
        )
    }

    override fun maxIntrinsicHeight(width: IntPx): IntPx {
        return layoutNode.measureBlocks.maxIntrinsicHeight(
            layoutNode.measureScope,
            layoutNode.layoutChildren,
            width,
            layoutNode.layoutDirection!!
        )
    }

    override fun place(position: IntPxPosition) {
        layoutNode.isPlaced = true
        val wasMoved = position != this.position
        this.position = position
        if (wasMoved) {
            layoutNode.owner?.onPositionChange(layoutNode)
        }
        layoutNode.layout()
    }

    override operator fun get(line: AlignmentLine): IntPx? {
        return layoutNode.calculateAlignmentLines()[line]
    }

    override fun draw(canvas: Canvas, density: Density) {
        withPositionTranslation(canvas) {
            val owner = layoutNode.requireOwner()
            val sizePx = size.toPxSize()
            layoutNode.zIndexSortedChildren.fastForEach { child ->
                owner.callDraw(canvas, child, sizePx)
            }
            if (owner.showLayoutBounds) {
                drawBorder(canvas, innerBoundsPaint)
            }
        }
    }

    override fun hitTest(
        pointerPositionRelativeToScreen: PxPosition,
        hitPointerInputFilters: MutableList<PointerInputFilter>
    ): Boolean {
        if (isGlobalPointerInBounds(pointerPositionRelativeToScreen)) {
            // Any because as soon as true is returned, we know we have found a hit path and we must
            //  not add PointerInputFilters on different paths so we should not even go looking.
            return layoutNode.children.reversed().fastAny { child ->
                callHitTest(child, pointerPositionRelativeToScreen, hitPointerInputFilters)
            }
        } else {
            // Anything out of bounds of ourselves can't be hit.
            return false
        }
    }

    override fun detach() {
        // Do nothing. InnerPlaceable only is detached when the LayoutNode is detached.
    }

    internal companion object {
        val innerBoundsPaint = Paint().also { paint ->
            paint.color = Color.Red
            paint.strokeWidth = 1f
            paint.style = PaintingStyle.stroke
        }
    }
}

private fun callHitTest(
    node: ComponentNode,
    globalPoint: PxPosition,
    hitPointerInputFilters: MutableList<PointerInputFilter>
): Boolean {
    if (node is LayoutNode) {
        return node.hitTest(globalPoint, hitPointerInputFilters)
    } else {
        // Any because as soon as true is returned, we know we have found a hit path and we must
        // not add PointerInputFilters on different paths so we should not even go looking.
        return node.children.reversed().fastAny { child ->
            callHitTest(child, globalPoint, hitPointerInputFilters)
        }
    }
}

internal class ModifiedParentDataNode(
    wrapped: LayoutNodeWrapper,
    val parentDataModifier: ParentDataModifier
) : DelegatingLayoutNodeWrapper(wrapped) {
    override val parentData: Any?
        get() = with(parentDataModifier) {
            /**
             * ParentData provided through the parentData node will override the data provided
             * through a modifier
             */
            layoutNode.parentDataNode?.value
                ?: layoutNode.measureScope.modifyParentData(wrapped.parentData)
        }
}

internal class ModifiedLayoutNode2(
    wrapped: LayoutNodeWrapper,
    val layoutModifier: LayoutModifier2
) : DelegatingLayoutNodeWrapper(wrapped) {

    override fun measure(constraints: Constraints): Placeable = with(layoutModifier) {
        updateLayoutDirection()
        measureResult =
            layoutNode.measureScope.measure(wrapped, constraints, layoutNode.layoutDirection!!)
        this@ModifiedLayoutNode2
    }

    override fun minIntrinsicWidth(height: IntPx): IntPx = with(layoutModifier) {
        updateLayoutDirection()
        layoutNode.measureScope.minIntrinsicWidth(wrapped, height, layoutNode.layoutDirection!!)
    }

    override fun maxIntrinsicWidth(height: IntPx): IntPx = with(layoutModifier) {
        updateLayoutDirection()
        layoutNode.measureScope.maxIntrinsicWidth(wrapped, height, layoutNode.layoutDirection!!)
    }

    override fun minIntrinsicHeight(width: IntPx): IntPx = with(layoutModifier) {
        updateLayoutDirection()
        layoutNode.measureScope.minIntrinsicHeight(wrapped, width, layoutNode.layoutDirection!!)
    }

    override fun maxIntrinsicHeight(width: IntPx): IntPx = with(layoutModifier) {
        updateLayoutDirection()
        layoutNode.measureScope.maxIntrinsicHeight(wrapped, width, layoutNode.layoutDirection!!)
    }

    override operator fun get(line: AlignmentLine): IntPx? =
        measureResult.alignmentLines.getOrElse(line, { wrapped[line] })

    override fun draw(canvas: Canvas, density: Density) {
        withPositionTranslation(canvas) {
            wrapped.draw(canvas, density)
            if (layoutNode.requireOwner().showLayoutBounds) {
                drawBorder(canvas, modifierBoundsPaint)
            }
        }
    }

    internal companion object {
        val modifierBoundsPaint = Paint().also { paint ->
            paint.color = Color.Blue
            paint.strokeWidth = 1f
            paint.style = PaintingStyle.stroke
        }
    }

    private fun updateLayoutDirection() {
        // TODO(popam): add support to change layout direction in the layout DSL
    }
}

@Suppress("Deprecation")
internal class ModifiedLayoutNode(
    wrapped: LayoutNodeWrapper,
    val layoutModifier: LayoutModifier
) : DelegatingLayoutNodeWrapper(wrapped) {
    override fun measure(constraints: Constraints): Placeable = with(layoutModifier) {
        updateLayoutDirection()
        val placeable = wrapped.measure(
            layoutNode.measureScope.modifyConstraints(constraints, layoutNode.layoutDirection!!)
        )
        val size = layoutNode.measureScope.modifySize(
            constraints,
            layoutNode.layoutDirection!!,
            placeable.size
        )
        val wrappedPosition = with(layoutModifier) {
            layoutNode.measureScope.modifyPosition(
                placeable.size,
                size,
                layoutNode.layoutDirection!!
            )
        }
        measureResult = object : MeasureScope.MeasureResult {
            override val width: IntPx = size.width
            override val height: IntPx = size.height
            override val alignmentLines: Map<AlignmentLine, IntPx> = emptyMap()
            override fun placeChildren(layoutDirection: LayoutDirection) {
                placeable.placeAbsolute(wrappedPosition)
            }
        }
        this@ModifiedLayoutNode
    }

    override fun minIntrinsicWidth(height: IntPx): IntPx = with(layoutModifier) {
        updateLayoutDirection()
        layoutNode.measureScope.minIntrinsicWidthOf(wrapped, height, layoutNode.layoutDirection!!)
    }

    override fun maxIntrinsicWidth(height: IntPx): IntPx = with(layoutModifier) {
        updateLayoutDirection()
        layoutNode.measureScope.maxIntrinsicWidthOf(wrapped, height, layoutNode.layoutDirection!!)
    }

    override fun minIntrinsicHeight(width: IntPx): IntPx = with(layoutModifier) {
        updateLayoutDirection()
        layoutNode.measureScope.minIntrinsicHeightOf(wrapped, width, layoutNode.layoutDirection!!)
    }

    override fun maxIntrinsicHeight(width: IntPx): IntPx = with(layoutModifier) {
        updateLayoutDirection()
        layoutNode.measureScope.maxIntrinsicHeightOf(wrapped, width, layoutNode.layoutDirection!!)
    }

    override operator fun get(line: AlignmentLine): IntPx? = with(layoutModifier) {
        return layoutNode.measureScope.modifyAlignmentLine(
            line,
            super.get(line),
            layoutNode.layoutDirection!!
        )
    }

    override fun draw(canvas: Canvas, density: Density) {
        withPositionTranslation(canvas) {
            wrapped.draw(canvas, density)
            if (layoutNode.requireOwner().showLayoutBounds) {
                drawBorder(canvas, modifierBoundsPaint)
            }
        }
    }

    internal companion object {
        val modifierBoundsPaint = Paint().also { paint ->
            paint.color = Color.Blue
            paint.strokeWidth = 1f
            paint.style = PaintingStyle.stroke
        }
    }

    private fun updateLayoutDirection() = with(layoutModifier) {
        val modifiedLayoutDirection =
            layoutNode.measureScope.modifyLayoutDirection(layoutNode.layoutDirection!!)
        layoutNode.layoutDirection = modifiedLayoutDirection
    }
}

internal class ModifiedDrawNode(
    wrapped: LayoutNodeWrapper,
    val drawModifier: DrawModifier
) : DelegatingLayoutNodeWrapper(wrapped), () -> Unit {
    private var density: Density? = null
    private var canvas: Canvas? = null

    override fun draw(canvas: Canvas, density: Density) {
        withPositionTranslation(canvas) {
            this.density = density
            this.canvas = canvas
            val pxSize = size.toPxSize()
            drawModifier.draw(density, this, canvas, pxSize)
            this.density = null
            this.canvas = null
        }
    }

    // This is the implementation of drawContent()
    override fun invoke() {
        wrapped.draw(canvas!!, density!!)
    }
}

internal class PointerInputDelegatingWrapper(
    wrapped: LayoutNodeWrapper,
    private val pointerInputModifier: PointerInputModifier
) : DelegatingLayoutNodeWrapper(wrapped) {

    init {
        pointerInputModifier.pointerInputFilter.layoutCoordinates = this
    }

    override fun hitTest(
        pointerPositionRelativeToScreen: PxPosition,
        hitPointerInputFilters: MutableList<PointerInputFilter>
    ): Boolean {
        if (isGlobalPointerInBounds(pointerPositionRelativeToScreen)) {
            // If we were hit, add the pointerInputFilter and keep looking to see if anything
            // further down the tree is also hit and return true.
            hitPointerInputFilters.add(pointerInputModifier.pointerInputFilter)
            super.hitTest(pointerPositionRelativeToScreen, hitPointerInputFilters)
            return true
        } else {
            // Anything out of bounds of ourselves can't be hit.
            return false
        }
    }
}

internal class LayerWrapper(
    wrapped: LayoutNodeWrapper,
    val drawLayerModifier: DrawLayerModifier
) : DelegatingLayoutNodeWrapper(wrapped) {
    private var _layer: OwnedLayer? = null
    private var layerDestroyed = false

    private val invalidateParentLayer: () -> Unit = {
        wrappedBy?.findLayer()?.invalidate()
    }

    val layer: OwnedLayer
        get() {
            return _layer ?: layoutNode.requireOwner().createLayer(
                drawLayerModifier,
                wrapped::draw,
                invalidateParentLayer
            ).also {
                _layer = it
                invalidateParentLayer()
            }
        }

    // TODO(mount): This cache isn't thread safe at all.
    private var positionCache: FloatArray? = null

    override fun measure(constraints: Constraints): Placeable {
        val placeable = super.measure(constraints)
        layer.resize(size)
        return placeable
    }

    override fun place(position: IntPxPosition) {
        super.place(position)
        layer.move(position)
    }

    override fun draw(canvas: Canvas, density: Density) {
        layer.drawLayer(canvas)
    }

    override fun detach() {
        super.detach()
        _layer?.destroy()
    }

    override fun findLayer(): OwnedLayer? {
        return layer
    }

    override fun toParentPosition(position: PxPosition): PxPosition {
        val matrix = layer.getMatrix()
        if (!matrix.isIdentity) {
            val x = position.x.value
            val y = position.y.value
            val cache = positionCache
            val point = if (cache != null) {
                cache[0] = x
                cache[1] = y
                cache
            } else {
                floatArrayOf(x, y).also { positionCache = it }
            }
            matrix.mapPoints(point)
            return super.toParentPosition(PxPosition(point[0].px, point[1].px))
        } else {
            return super.toParentPosition(position)
        }
    }

    override fun rectInParent(bounds: RectF) {
        if ((drawLayerModifier.clipToBounds ||
                    (drawLayerModifier.clipToOutline && drawLayerModifier.outlineShape != null)) &&
            !bounds.intersect(0f, 0f, size.width.value.toFloat(), size.height.value.toFloat())
        ) {
            bounds.setEmpty()
        }
        val matrix = layer.getMatrix()
        matrix.mapRect(bounds)
        return super.rectInParent(bounds)
    }
}

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

package androidx.ui.core.pointerinput

import androidx.ui.core.IntPxPosition
import androidx.ui.core.IntPxSize
import androidx.ui.core.PointerEventPass
import androidx.ui.core.PointerInputChange
import androidx.ui.core.PointerInputNode
import androidx.ui.core.hasNoLayoutDescendants
import androidx.ui.core.ipx
import androidx.ui.core.isAttached
import androidx.ui.core.positionRelativeToRoot
import androidx.ui.core.visitLayoutChildren
import kotlin.math.max
import kotlin.math.min

/**
 * Organizes pointers and the [PointerInputNode]s that they hit into a hierarchy such that
 * [PointerInputChange]s can be dispatched to the [PointerInputNode]s in a hierarchical fashion.
 */
internal class HitPathTracker {

    // TODO(shepshapard): Consider not making the root an instance of Node, but instead some other
    //  "root" class.  It may simplify the implementation of Node.
    internal val root: Node = Node()

    /**
     * Associates [pointerId] to [pointerInputNodes] and tracks them.
     *
     * @param pointerId The id of the pointer that was hit tested against [PointerInputNode]s
     * @param pointerInputNodes The [PointerInputNode]s that were hit by [pointerId].  Must be
     * ordered from ancestor to descendant.
     */
    fun addHitPath(pointerId: Int, pointerInputNodes: List<PointerInputNode>) {
        var parent = root
        var merging = true
        eachPin@ for (pointerInputNode in pointerInputNodes) {
            if (merging) {
                val node = parent.children.find { it.pointerInputNode == pointerInputNode }
                if (node != null) {
                    node.pointerIds.add(pointerId)
                    parent = node
                    continue@eachPin
                } else {
                    merging = false
                }
            }
            val node = Node(pointerInputNode).apply {
                pointerIds.add(pointerId)
            }
            parent.children.add(node)
            parent = node
        }
    }

    /**
     * Dispatches [pointerInputChanges] through the hierarchy; first down the hierarchy, passing
     * [downPass] to each [PointerInputNode], and then up the hierarchy with [upPass] if [upPass]
     * is not null.
     */
    fun dispatchChanges(
        pointerInputChanges: List<PointerInputChange>,
        downPass: PointerEventPass,
        upPass: PointerEventPass? = null
    ): List<PointerInputChange> {

        // TODO(b/124523868): PointerInputNodes should opt into passes prevent having to visit
        // each one for every PointerInputChange.

        val idToChangesMap = pointerInputChanges.associateTo(mutableMapOf()) {
            it.id to it
        }
        root.dispatchChanges(idToChangesMap, downPass, upPass)
        return idToChangesMap.values.toList()
    }

    /**
     * Dispatches cancel events to all tracked [PointerInputNode]s to notify them that
     * [PointerInputNode.pointerInputHandler] will not be called again until all pointers have been
     * removed from the application and then at least one is added again.
     */
    fun dispatchCancel() {
        root.dispatchCancel()
    }

    /**
     * Removes all paths tracked by [addHitPath].
     */
    fun clear() {
        root.clear()
    }

    /**
     * Removes the [pointerId] and any [PointerInputNode]s that are no longer associated with any
     * remaining [pointerId].
     */
    fun removePointerId(pointerId: Int) {
        root.removePointerId(pointerId)
    }

    /**
     * Removes [PointerInputNode]s that have been removed from the component tree.
     */
    fun removeDetachedPointerInputNodes() {
        root.removeDetachedPointerInputNodes()
    }

    /**
     * Removes [PointerInputNode]s that do not have any descendant LayoutNodes.
     */
    fun removePointerInputNodesWithNoLayoutNodeDescendants() {
        root.removePointerInputNodesWithNoLayoutNodeDescendants()
    }

    // TODO(shepshapard): Bind removeDetachedPointerInputNodes,
    //  removePointerInputNodesWithNoLayoutNodeDescendants, and refreshOffsets together given the
    //  constraint that right now, one must be called before the other.
    /**
     * Updates this [HitPathTracker]'s cached knowledge of the bounds of the [PointerInputNode]s
     * it is tracking.  This is is necessary to call before calls to [dispatchChanges] so that
     * the positions of [PointerInputChange]s are offset to be relative to the [PointerInputNode]s
     * that are going to receive them.
     *
     * Must only be called after guaranteeing that each Node has a PointerInputNode that has at
     * least one descendant LayoutNode.
     *
     * @param additionalPointerOffset The additional offset that will be added to all
     * [PointerInputChange]s when [dispatchChanges] is called.
     */
    fun refreshOffsets(additionalPointerOffset: IntPxPosition) {
        root.refreshPositionInformation(additionalPointerOffset)
    }

    // TODO(b/145305910): removeDetachedPointerInputNodes and
    //  removePointerInputNodesWithNoLayoutNodeDescendants should not wait to be called during
    //  dispatching of pointer input events.  Changing PointerInputNodes to be PointerInputModifiers
    //  will fix this for us.
    /**
     * Convenience method that removes PointerInputNodes that are no longer valid and refreshes the
     * offset information for those that are.
     *
     * @param additionalPointerOffset The additional offset that will be added to all
     * [PointerInputChange]s when [dispatchChanges] is called.
     */
    fun refreshPathInformation(additionalPointerOffset: IntPxPosition) {
        removeDetachedPointerInputNodes()
        removePointerInputNodesWithNoLayoutNodeDescendants()
        refreshOffsets(additionalPointerOffset)
    }
}

// TODO(shepshapard): This really should be private. Currently some tests inspect the node's
// directly which is unnecessary and bad practice.
internal class Node(
    val pointerInputNode: PointerInputNode? = null
) {
    val pointerIds: MutableSet<Int> = mutableSetOf()
    val children: MutableSet<Node> = mutableSetOf()

    // Stores the associated PointerInputNode's virtual position relative to it's parent
    // PointerInputNode, or relative to the compose root if it has no parent PointerInputNode.
    var offset: IntPxPosition = IntPxPosition.Origin

    // Stores the associated PointerInputNode's virtual size.
    var size = IntPxSize(0.ipx, 0.ipx)

    fun dispatchChanges(
        pointerInputChanges: MutableMap<Int, PointerInputChange>,
        downPass: PointerEventPass,
        upPass: PointerEventPass?
    ) {
        // Filter for changes that are associated with pointer ids that are relevant to this node.
        val relevantChanges = if (pointerInputNode == null) {
            pointerInputChanges
        } else {
            pointerInputChanges.filterTo(mutableMapOf()) { entry ->
                pointerIds.contains(entry.key)
            }
        }

        if (relevantChanges.isEmpty()) {
            throw IllegalStateException(
                "Currently, HitPathTracker is operating under the assumption that there should " +
                        "never be a circumstance in which it is tracking a PointerInputNode " +
                        "where when it receives pointerInputChanges, none are relevant to that " +
                        "PointerInputNode.  This assumption may not hold true in the future, but " +
                        "currently it assumes it can abide by this contract."
            )
        }

        // For each relevant change:
        //  1. subtract the offset
        //  2. dispatch the change on the down pass,
        //  3. update it in relevantChanges.
        if (pointerInputNode != null) {
            relevantChanges.let {
                // TODO(shepshapard): would be nice if we didn't have to subtract and then add
                // offsets.  This is currently done because the calculated offsets are currently
                // global, not relative to eachother.
                it.subtractOffset(offset)
                it.dispatchToPointerInputNode(pointerInputNode, downPass, size)
                it.addOffset(offset)
            }
        }

        // Call children recursively with the relevant changes.
        children.forEach { it.dispatchChanges(relevantChanges, downPass, upPass) }

        // For each relevant change:
        //  1. dispatch the change on the up pass,
        //  2. add the offset,
        //  3. update it in  relevant changes.
        if (pointerInputNode != null && upPass != null) {
            relevantChanges.let {
                it.subtractOffset(offset)
                it.dispatchToPointerInputNode(pointerInputNode, upPass, size)
                it.addOffset(offset)
            }
        }

        // Mutate the pointerInputChanges with the ones we modified.
        pointerInputChanges.putAll(relevantChanges)
    }

    // TODO(shepshapard): Should some order of cancel dispatch be guaranteed? I think the answer is
    //  essentially "no", but given that an order can be consistent... maybe we might as well
    //  set an arbitrary standard and stick to it so user expectations are maintained.
    /**
     * Does a depth first traversal and invokes [PointerInputNode.cancelHandler] during
     * backtracking.
     */
    fun dispatchCancel() {
        children.forEach { it.dispatchCancel() }
        pointerInputNode?.cancelHandler?.invoke()
    }

    /**
     * Removes all children from this Node.
     */
    fun clear() {
        children.clear()
    }

    fun removeDetachedPointerInputNodes() {
        children.removeAndProcess(
            removeIf = {
                it.pointerInputNode != null && !it.pointerInputNode.isAttached()
            },
            ifRemoved = {
                it.dispatchCancel()
            },
            ifKept = {
                it.removeDetachedPointerInputNodes()
            })
    }

    fun removePointerInputNodesWithNoLayoutNodeDescendants() {
        children.removeAndProcess(
            removeIf = {
                it.pointerInputNode != null && it.pointerInputNode.hasNoLayoutDescendants()
            },
            ifRemoved = {
                it.dispatchCancel()
            },
            ifKept = {
                it.removePointerInputNodesWithNoLayoutNodeDescendants()
            })
    }

    fun removePointerId(pointerId: Int) {
        children.forEach {
            it.pointerIds.remove(pointerId)
        }
        children.removeAll {
            it.pointerInputNode != null && it.pointerIds.isEmpty()
        }
        children.forEach {
            it.removePointerId(pointerId)
        }
    }

    // TODO(b/124960509): Make this much more efficient.  Right now, even though the data structure
    //  is a tree, each LayoutNode requests it's position relative to the root, even though it's
    //  parent would already have it's position relative to root.
    /**
     * Updates all position and size information for all nodes.
     *
     * @param additionalPointerOffset The additional offset that will be added to all
     * [PointerInputChange]s when [dispatchChanges] is called.
     */
    fun refreshPositionInformation(additionalPointerOffset: IntPxPosition) {
        children.forEach { child ->
            var minX = Int.MAX_VALUE
            var minY = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE
            var maxY = Int.MIN_VALUE
            child.pointerInputNode?.visitLayoutChildren { layoutChild ->
                val globalPosition = layoutChild.positionRelativeToRoot()
                val x = globalPosition.x.value + additionalPointerOffset.x.value
                val y = globalPosition.y.value + additionalPointerOffset.y.value
                minX = min(minX, x)
                minY = min(minY, y)
                maxX = max(maxX, x + layoutChild.width.value)
                maxY = max(maxY, y + layoutChild.height.value)
            }
            child.offset = IntPxPosition(minX.ipx, minY.ipx)
            child.size = IntPxSize((maxX - minX).ipx, (maxY - minY).ipx)
            child.refreshPositionInformation(additionalPointerOffset)
        }
    }

    override fun toString(): String {
        return "Node(pointerInputNode=$pointerInputNode, children=$children, " +
                "pointerIds=$pointerIds)"
    }

    private fun MutableMap<Int, PointerInputChange>.dispatchToPointerInputNode(
        node: PointerInputNode,
        pass: PointerEventPass,
        size: IntPxSize
    ) {
        node.pointerInputHandler(values.toList(), pass, size).forEach {
            this[it.id] = it
        }
    }

    private fun MutableMap<Int, PointerInputChange>.addOffset(position: IntPxPosition) {
        if (position != IntPxPosition.Origin) {
            replaceEverything {
                it.copy(
                    current = it.current.copy(position = it.current.position?.plus(position)),
                    previous = it.previous.copy(position = it.previous.position?.plus(position))
                )
            }
        }
    }

    private fun MutableMap<Int, PointerInputChange>.subtractOffset(position: IntPxPosition) {
        addOffset(-position)
    }

    private inline fun <K, V> MutableMap<K, V>.replaceEverything(f: (V) -> V) {
        for (entry in this) {
            entry.setValue(f(entry.value))
        }
    }
}

private fun <T> MutableIterable<T>.removeAndProcess(
    removeIf: (T) -> Boolean,
    ifRemoved: (T) -> Unit,
    ifKept: (T) -> Unit
) {
    with(iterator()) {
        while (hasNext()) {
            val next = next()
            if (removeIf(next)) {
                remove()
                ifRemoved(next)
            } else {
                ifKept(next)
            }
        }
    }
}

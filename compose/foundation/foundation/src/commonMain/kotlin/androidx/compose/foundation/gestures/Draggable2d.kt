/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.foundation.gestures

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.DragEvent.DragDelta
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

/**
 * State of Draggable2d. Allows for granular control of how deltas are consumed by the user as well
 * as to write custom drag methods using [drag] suspend function.
 */
internal interface Draggable2dState {
    /**
     * Call this function to take control of drag logic.
     *
     * All actions that change the logical drag position must be performed within a [drag]
     * block (even if they don't call any other methods on this object) in order to guarantee
     * that mutual exclusion is enforced.
     *
     * If [drag] is called from elsewhere with the [dragPriority] higher or equal to ongoing
     * drag, ongoing drag will be canceled.
     *
     * @param dragPriority of the drag operation
     * @param block to perform drag in
     */
    suspend fun drag(
        dragPriority: MutatePriority = MutatePriority.Default,
        block: suspend Drag2dScope.() -> Unit
    )

    /**
     * Dispatch drag delta in pixels avoiding all drag related priority mechanisms.
     *
     * **Note:** unlike [drag], dispatching any delta with this method will bypass scrolling of
     * any priority. This method will also ignore `reverseDirection` and other parameters set in
     * draggable2d.
     *
     * This method is used internally for low level operations, allowing implementers of
     * [Draggable2dState] influence the consumption as suits them.
     * Manually dispatching delta via this method will likely result in a bad user experience,
     * you must prefer [drag] method over this one.
     *
     * @param delta amount of scroll dispatched in the nested drag process
     */
    fun dispatchRawDelta(delta: Offset)
}

/**
 * Scope used for suspending drag blocks
 */
internal interface Drag2dScope {
    /**
     * Attempts to drag by [pixels] px.
     */
    fun dragBy(pixels: Offset)
}

/**
 * Default implementation of [Draggable2dState] interface that allows to pass a simple action that
 * will be invoked when the drag occurs.
 *
 * This is the simplest way to set up a draggable2d modifier. When constructing this
 * [Draggable2dState], you must provide a [onDelta] lambda, which will be invoked whenever
 * drag happens (by gesture input or a custom [Draggable2dState.drag] call) with the delta in
 * pixels.
 *
 * If you are creating [Draggable2dState] in composition, consider using [rememberDraggable2dState].
 *
 * @param onDelta callback invoked when drag occurs. The callback receives the delta in pixels.
 */
@Suppress("PrimitiveInLambda")
internal fun Draggable2dState(onDelta: (Offset) -> Unit): Draggable2dState =
    DefaultDraggable2dState(onDelta)

/**
 * Create and remember default implementation of [Draggable2dState] interface that allows to pass a
 * simple action that will be invoked when the drag occurs.
 *
 * This is the simplest way to set up a [draggable] modifier. When constructing this
 * [Draggable2dState], you must provide a [onDelta] lambda, which will be invoked whenever
 * drag happens (by gesture input or a custom [Draggable2dState.drag] call) with the delta in
 * pixels.
 *
 * @param onDelta callback invoked when drag occurs. The callback receives the delta in pixels.
 */
@Suppress("PrimitiveInLambda")
@Composable
internal fun rememberDraggable2dState(onDelta: (Offset) -> Unit): Draggable2dState {
    val onDeltaState = rememberUpdatedState(onDelta)
    return remember { Draggable2dState { onDeltaState.value.invoke(it) } }
}

/**
 * Configure touch dragging for the UI element in both orientations. The drag distance
 * reported to [Draggable2dState], allowing users to react to the drag delta and update their state.
 *
 * The common common usecase for this component is when you need to be able to drag something
 * inside the component on the screen and represent this state via one float value
 *
 * If you are implementing dragging in a single orientation, consider using [draggable].
 *
 * @param state [Draggable2dState] state of the draggable2d. Defines how drag events will be
 * interpreted by the user land logic.
 * @param enabled whether or not drag is enabled
 * @param interactionSource [MutableInteractionSource] that will be used to emit
 * [DragInteraction.Start] when this draggable is being dragged.
 * @param startDragImmediately when set to true, draggable2d will start dragging immediately and
 * prevent other gesture detectors from reacting to "down" events (in order to block composed
 * press-based gestures). This is intended to allow end users to "catch" an animating widget by
 * pressing on it. It's useful to set it when value you're dragging is settling / animating.
 * @param onDragStarted callback that will be invoked when drag is about to start at the starting
 * position, allowing user to suspend and perform preparation for drag, if desired.This suspend
 * function is invoked with the draggable2d scope, allowing for async processing, if desired. Note
 * that the scope used here is the onw provided by the draggable2d node, for long running work that
 * needs to outlast the modifier being in the composition you should use a scope that fits the
 * lifecycle needed.
 * @param onDragStopped callback that will be invoked when drag is finished, allowing the
 * user to react on velocity and process it. This suspend function is invoked with the draggable2d
 * scope, allowing for async processing, if desired. Note that the scope used here is the onw
 * provided by the draggable2d scope, for long running work that needs to outlast the modifier being
 * in the composition you should use a scope that fits the lifecycle needed.
 * @param reverseDirection reverse the direction of the scroll, so top to bottom scroll will
 * behave like bottom to top and left to right will behave like right to left.
 */
@Suppress("PrimitiveInLambda")
internal fun Modifier.draggable2d(
    state: Draggable2dState,
    enabled: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    startDragImmediately: Boolean = false,
    onDragStarted: suspend CoroutineScope.(startedPosition: Offset) -> Unit = {},
    onDragStopped: suspend CoroutineScope.(velocity: Velocity) -> Unit = {},
    reverseDirection: Boolean = false
): Modifier = this then Draggable2dElement(
    state = state,
    enabled = enabled,
    interactionSource = interactionSource,
    startDragImmediately = { startDragImmediately },
    onDragStarted = onDragStarted,
    onDragStopped = onDragStopped,
    reverseDirection = reverseDirection,
    canDrag = { true }
)

@Suppress("PrimitiveInLambda")
internal class Draggable2dElement(
    private val state: Draggable2dState,
    private val canDrag: (PointerInputChange) -> Boolean,
    private val enabled: Boolean,
    private val interactionSource: MutableInteractionSource?,
    private val startDragImmediately: () -> Boolean,
    private val onDragStarted: suspend CoroutineScope.(startedPosition: Offset) -> Unit,
    private val onDragStopped: suspend CoroutineScope.(velocity: Velocity) -> Unit,
    private val reverseDirection: Boolean,

    ) : ModifierNodeElement<Draggable2dNode>() {
    override fun create(): Draggable2dNode = Draggable2dNode(
        state,
        canDrag,
        enabled,
        interactionSource,
        startDragImmediately,
        onDragStarted,
        onDragStopped,
        reverseDirection
    )

    override fun update(node: Draggable2dNode) {
        node.update(
            state,
            canDrag,
            enabled,
            interactionSource,
            startDragImmediately,
            onDragStarted,
            onDragStopped,
            reverseDirection
        )
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other === null) return false
        if (this::class != other::class) return false

        other as Draggable2dElement

        if (state != other.state) return false
        if (canDrag != other.canDrag) return false
        if (enabled != other.enabled) return false
        if (interactionSource != other.interactionSource) return false
        if (startDragImmediately != other.startDragImmediately) return false
        if (onDragStarted != other.onDragStarted) return false
        if (onDragStopped != other.onDragStopped) return false
        if (reverseDirection != other.reverseDirection) return false

        return true
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + canDrag.hashCode()
        result = 31 * result + enabled.hashCode()
        result = 31 * result + (interactionSource?.hashCode() ?: 0)
        result = 31 * result + startDragImmediately.hashCode()
        result = 31 * result + onDragStarted.hashCode()
        result = 31 * result + onDragStopped.hashCode()
        result = 31 * result + reverseDirection.hashCode()
        return result
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "draggable2d"
        properties["canDrag"] = canDrag
        properties["enabled"] = enabled
        properties["interactionSource"] = interactionSource
        properties["startDragImmediately"] = startDragImmediately
        properties["onDragStarted"] = onDragStarted
        properties["onDragStopped"] = onDragStopped
        properties["reverseDirection"] = reverseDirection
        properties["state"] = state
    }
}

@Suppress("PrimitiveInLambda")
internal class Draggable2dNode(
    private var state: Draggable2dState,
    canDrag: (PointerInputChange) -> Boolean,
    enabled: Boolean,
    interactionSource: MutableInteractionSource?,
    startDragImmediately: () -> Boolean,
    onDragStarted: suspend CoroutineScope.(startedPosition: Offset) -> Unit,
    onDragStopped: suspend CoroutineScope.(velocity: Velocity) -> Unit,
    reverseDirection: Boolean
) : AbstractDraggableNode(
    canDrag,
    enabled,
    interactionSource,
    startDragImmediately,
    onDragStarted,
    onDragStopped,
    reverseDirection
) {
    var drag2dScope: Drag2dScope = NoOpDrag2dScope

    private val abstractDragScope = object : AbstractDragScope {
        override fun dragBy(pixels: Offset) {
            drag2dScope.dragBy(pixels)
        }
    }

    override suspend fun drag(block: suspend AbstractDragScope.() -> Unit) {
        state.drag(MutatePriority.UserInput) {
            drag2dScope = this
            block.invoke(abstractDragScope)
        }
    }

    override suspend fun AbstractDragScope.draggingBy(dragDelta: DragDelta) {
        dragBy(dragDelta.delta)
    }

    override val pointerDirectionConfig = BidirectionalPointerDirectionConfig

    @Suppress("PrimitiveInLambda")
    fun update(
        state: Draggable2dState,
        canDrag: (PointerInputChange) -> Boolean,
        enabled: Boolean,
        interactionSource: MutableInteractionSource?,
        startDragImmediately: () -> Boolean,
        onDragStarted: suspend CoroutineScope.(startedPosition: Offset) -> Unit,
        onDragStopped: suspend CoroutineScope.(velocity: Velocity) -> Unit,
        reverseDirection: Boolean
    ) {
        var resetPointerInputHandling = false
        if (this.state != state) {
            this.state = state
            resetPointerInputHandling = true
        }
        this.canDrag = canDrag
        if (this.enabled != enabled) {
            this.enabled = enabled
            if (!enabled) {
                disposeInteractionSource()
            }
            resetPointerInputHandling = true
        }
        if (this.interactionSource != interactionSource) {
            disposeInteractionSource()
            this.interactionSource = interactionSource
        }
        this.startDragImmediately = startDragImmediately
        this.onDragStarted = onDragStarted
        this.onDragStopped = onDragStopped
        if (this.reverseDirection != reverseDirection) {
            this.reverseDirection = reverseDirection
            resetPointerInputHandling = true
        }
        if (resetPointerInputHandling) {
            pointerInputNode.resetPointerInputHandler()
        }
    }
}

private val NoOpDrag2dScope: Drag2dScope = object : Drag2dScope {
    override fun dragBy(pixels: Offset) {}
}

@Suppress("PrimitiveInLambda")
private class DefaultDraggable2dState(val onDelta: (Offset) -> Unit) : Draggable2dState {
    private val drag2dScope: Drag2dScope = object : Drag2dScope {
        override fun dragBy(pixels: Offset) = onDelta(pixels)
    }

    private val drag2dMutex = MutatorMutex()

    override suspend fun drag(
        dragPriority: MutatePriority,
        block: suspend Drag2dScope.() -> Unit
    ): Unit = coroutineScope {
        drag2dMutex.mutateWith(drag2dScope, dragPriority, block)
    }

    override fun dispatchRawDelta(delta: Offset) {
        return onDelta(delta)
    }
}

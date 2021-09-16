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

package androidx.compose.ui.test

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * The receiver scope of the multi-modal input injection lambda from [performMultiModalInput].
 *
 * The input uses the [SemanticsNode] identified by the corresponding [SemanticsNodeInteraction]
 * as the frame of reference for the event's positions. How the event is injected exactly is
 * platform dependent.
 *
 * The functions in [MultiModalInjectionScope] are divided by modality: currently, we have a
 * [Touch] scope and a [Mouse] scope. See their respective docs for more information.
 *
 * Note that all events generated by the gesture methods are batched together and sent as a whole
 * after [performMultiModalInput] has executed its code block.
 *
 * Example usage:
 * ```
 * onNodeWithTag("myWidget")
 *    .performMultiModalInput {
 *        Touch.click(center)
 *    }
 *
 * onNodeWithTag("myWidget")
 *    // TODO(fresen): add multi modal example
 *    .performMultiModalInput {
 *        Touch.down(topLeft)
 *        Touch.move(topLeft + percentOffset(0f, .1f))
 *        Keyboard.press(Keyboard.Ctrl)
 *        Touch.move(center)
 *        Touch.up()
 *        Keyboard.release(Keyboard.Ctrl)
 *    }
 * ```
 *
 * @see InjectionScope
 * @see TouchInjectionScope
 */
class MultiModalInjectionScope(node: SemanticsNode, testContext: TestContext) : InjectionScope {
    // TODO(b/133217292): Better error: explain which gesture couldn't be performed
    private var _semanticsNode: SemanticsNode? = node
    internal val semanticsNode
        get() = checkNotNull(_semanticsNode) {
            "Can't query SemanticsNode, InjectionScope has already been disposed"
        }

    // TODO(b/133217292): Better error: explain which gesture couldn't be performed
    private var _inputDispatcher: InputDispatcher? =
        createInputDispatcher(testContext, checkNotNull(semanticsNode.root))
    internal val inputDispatcher
        get() = checkNotNull(_inputDispatcher) {
            "Can't send gesture, InjectionScope has already been disposed"
        }

    /**
     * Returns and stores the visible bounds of the [semanticsNode] we're interacting with. This
     * applies clipping, which is almost always the correct thing to do when injecting gestures,
     * as gestures operate on visible UI.
     */
    internal val boundsInRoot: Rect by lazy { semanticsNode.boundsInRoot }

    /**
     * Returns the size of the visible part of the node we're interacting with. This is contrary
     * to [SemanticsNode.size], which returns the unclipped size of the node.
     */
    override val visibleSize: IntSize by lazy {
        IntSize(boundsInRoot.width.roundToInt(), boundsInRoot.height.roundToInt())
    }

    /**
     * Transforms the [position] to root coordinates.
     *
     * @param position A position in local coordinates
     * @return [position] transformed to coordinates relative to the containing root.
     */
    private fun localToRoot(position: Offset): Offset {
        return position + boundsInRoot.topLeft
    }

    private fun rootToLocal(position: Offset): Offset {
        return position - boundsInRoot.topLeft
    }

    /**
     * Adds the given [durationMillis] to the current event time, delaying the next event by that
     * time. Only valid when a gesture has already been started, or when a finished gesture is
     * resumed.
     */
    override fun advanceEventTime(durationMillis: Long) {
        inputDispatcher.advanceEventTime(durationMillis)
    }

    internal fun dispose() {
        inputDispatcher.dispose()
        _semanticsNode = null
        _inputDispatcher = null
    }

    val Touch: TouchInjectionScope = object : TouchInjectionScope, InjectionScope by this {
        override fun currentPosition(pointerId: Int): Offset? {
            val positionInRoot = inputDispatcher.getCurrentTouchPosition(pointerId) ?: return null
            return rootToLocal(positionInRoot)
        }

        override fun down(pointerId: Int, position: Offset) {
            val positionInRoot = localToRoot(position)
            inputDispatcher.enqueueTouchDown(pointerId, positionInRoot)
        }

        override fun updatePointerTo(pointerId: Int, position: Offset) {
            val positionInRoot = localToRoot(position)
            inputDispatcher.updateTouchPointer(pointerId, positionInRoot)
        }

        override fun move(delayMillis: Long) {
            advanceEventTime(delayMillis)
            inputDispatcher.enqueueTouchMove()
        }

        override fun up(pointerId: Int) {
            inputDispatcher.enqueueTouchUp(pointerId)
        }

        override fun cancel(delayMillis: Long) {
            advanceEventTime(delayMillis)
            inputDispatcher.enqueueTouchCancel()
        }
    }

    @Suppress("EXPERIMENTAL_ANNOTATION_ON_WRONG_TARGET")
    @get:ExperimentalTestApi // Required to annotate Java-facing APIs
    @ExperimentalTestApi
    val Mouse: MouseInjectionScope = object : MouseInjectionScope, InjectionScope by this {
        override val currentPosition: Offset
            get() = rootToLocal(inputDispatcher.currentMousePosition)

        override fun moveTo(position: Offset, delayMillis: Long) {
            advanceEventTime(delayMillis)
            val positionInRoot = localToRoot(position)
            inputDispatcher.enqueueMouseMove(positionInRoot)
        }

        override fun updatePointerTo(position: Offset) {
            val positionInRoot = localToRoot(position)
            inputDispatcher.updateMousePosition(positionInRoot)
        }

        override fun press(button: MouseButton) {
            inputDispatcher.enqueueMousePress(button.buttonId)
        }

        override fun release(button: MouseButton) {
            inputDispatcher.enqueueMouseRelease(button.buttonId)
        }

        override fun enter(position: Offset, delayMillis: Long) {
            advanceEventTime(delayMillis)
            val positionInRoot = localToRoot(position)
            inputDispatcher.enqueueMouseEnter(positionInRoot)
        }

        override fun exit(position: Offset, delayMillis: Long) {
            advanceEventTime(delayMillis)
            val positionInRoot = localToRoot(position)
            inputDispatcher.enqueueMouseExit(positionInRoot)
        }

        override fun cancel(delayMillis: Long) {
            advanceEventTime(delayMillis)
            inputDispatcher.enqueueMouseCancel()
        }

        override fun scroll(delta: Float, scrollWheel: ScrollWheel) {
            inputDispatcher.enqueueMouseScroll(delta, scrollWheel)
        }
    }
}

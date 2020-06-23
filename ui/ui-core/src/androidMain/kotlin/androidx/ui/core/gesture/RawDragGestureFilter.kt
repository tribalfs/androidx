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

@file:OptIn(ExperimentalPointerInput::class)

package androidx.ui.core.gesture

import androidx.compose.remember
import androidx.ui.core.CustomEvent
import androidx.ui.core.CustomEventDispatcher
import androidx.ui.core.Modifier
import androidx.ui.core.PointerEventPass
import androidx.ui.core.PointerId
import androidx.ui.core.PointerInputChange
import androidx.ui.core.changedToDown
import androidx.ui.core.changedToDownIgnoreConsumed
import androidx.ui.core.changedToUp
import androidx.ui.core.changedToUpIgnoreConsumed
import androidx.ui.core.composed
import androidx.ui.core.consumeDownChange
import androidx.ui.core.consumePositionChange
import androidx.ui.core.gesture.scrollorientationlocking.Orientation
import androidx.ui.core.gesture.scrollorientationlocking.ScrollOrientationLocker
import androidx.ui.core.gesture.util.VelocityTracker
import androidx.ui.core.pointerinput.PointerInputFilter
import androidx.ui.core.positionChange
import androidx.ui.unit.IntSize
import androidx.ui.geometry.Offset
import androidx.ui.util.fastAny
import androidx.ui.util.fastForEach

/**
 * Defines the callbacks associated with dragging.
 */
interface DragObserver {

    /**
     * Override to be notified when a drag has started.
     *
     * This will be called as soon as the DragGestureDetector is allowed to start (canStartDragging
     * is null or returns true) and the average distance the pointers have moved are not 0 on
     * both the x and y axes.
     *
     * Only called if the last called if the most recent call among [onStart], [onStop], and
     * [onCancel] was [onStop] or [onCancel].
     *
     * @param downPosition The average position of all pointer positions when they first touched
     * down.
     */
    fun onStart(downPosition: Offset) {}

    /**
     * Override to be notified when a distance has been dragged.
     *
     * When overridden, return the amount of the [dragDistance] that has been consumed.
     *
     * Called immediately after [onStart] and for every subsequent pointer movement, as long as the
     * movement was enough to constitute a drag (the average movement on the x or y axis is not
     * equal to 0).
     *
     * Note: This may be called multiple times in a single pass and the values should be accumulated
     * for each call.
     *
     * @param dragDistance The distance that has been dragged.  Reflects the average drag distance
     * of all pointers.
     */
    fun onDrag(dragDistance: Offset) = Offset.Zero

    /**
     * Override to be notified when a drag has stopped.
     *
     * This is called once all pointers have stopped interacting with this DragGestureDetector.
     *
     * Only called if the last called if the most recent call among [onStart], [onStop], and
     * [onCancel] was [onStart].
     *
     * @param velocity The velocity of the drag in both orientations at the point in time when all
     * pointers have released the relevant PointerInputFilter. In pixels per second.
     */
    fun onStop(velocity: Offset) {}

    /**
     * Override to be notified when the drag has been cancelled.
     *
     * This is called in response to a cancellation event such as the associated
     * PointerInputFilter having been removed from the hierarchy.
     *
     * Only called if the last called if the most recent call among [onStart], [onStop], and
     * [onCancel] was [onStart].
     */
    fun onCancel() {}
}

// TODO(shepshapard): Convert to functional component with effects once effects are ready.
// TODO(shepshapard): Should this calculate the drag distance as the average of all fingers
//  (Shep thinks this is better), or should it only track the most recent finger to have
//  touched the screen over the detector (this is how Android currently does it)?
// TODO(b/139020678): Probably has shared functionality with other movement based detectors.
/**
 * This gesture detector detects dragging in any direction.
 *
 * Note: By default, this gesture detector only waits for a single pointer to have moved to start
 * dragging.  It is extremely likely that you don't want to use this gesture detector directly, but
 * instead use a drag gesture detector that does wait for some other condition to have occurred
 * (such as [dragGestureFilter] which waits for a single pointer to have passed touch
 * slop before dragging starts).
 *
 * Dragging begins when the a single pointer has moved and either [canStartDragging] is null or
 * returns true.  When dragging begins, [DragObserver.onStart] is called.  [DragObserver.onDrag] is
 * then continuously called whenever the average movement of all pointers has movement along the x
 * or y axis.  The gesture ends with either a call to [DragObserver.onStop] or
 * [DragObserver.onCancel], only after [DragObserver.onStart] is called. [DragObserver.onStop] is
 * called when the dragging ends due to all of the pointers no longer interacting with the
 * DragGestureDetector (for example, the last pointer has been lifted off of the
 * DragGestureDetector). [DragObserver.onCancel] is called when the dragging ends due to a system
 * cancellation event.
 *
 * When multiple pointers are touching the detector, the drag distance is taken as the average of
 * all of the pointers.
 *
 * Note: Changing the value of [orientation] will reset the gesture filter such that it will not
 * respond to input until new pointers are detected.
 *
 * @param dragObserver The callback interface to report all events related to dragging.
 * @param canStartDragging If set, Before dragging is started ([DragObserver.onStart] is called),
 *                         canStartDragging is called to check to see if it is allowed to start.
 * @param orientation Limits the directions under which dragging can occur to those that are
 *                    within the provided orientation, locks pointers that are used to drag in
 *                    the given orientation to that orientation, and ignores pointers that are
 *                    locked to other orientations.  If no orientation is provided, does none of
 *                    the above.
 */

// TODO(b/129784010): Consider also allowing onStart, onDrag, and onStop to be set individually
//  (instead of all being set via DragObserver).
fun Modifier.rawDragGestureFilter(
    dragObserver: DragObserver,
    canStartDragging: (() -> Boolean)? = null,
    orientation: Orientation? = null
): Modifier = composed {
    val filter = remember { RawDragGestureFilter() }
    filter.dragObserver = dragObserver
    filter.canStartDragging = canStartDragging
    filter.orientation = orientation
    PointerInputModifierImpl(filter)
}

internal class RawDragGestureFilter : PointerInputFilter() {
    private val velocityTrackers: MutableMap<PointerId, VelocityTracker> = mutableMapOf()
    private val downPositions: MutableMap<PointerId, Offset> = mutableMapOf()

    internal lateinit var dragObserver: DragObserver
    internal var canStartDragging: (() -> Boolean)? = null
    internal var orientation: Orientation? = null

    private var started = false
    internal lateinit var scrollOrientationLocker: ScrollOrientationLocker

    override fun onInit(customEventDispatcher: CustomEventDispatcher) {
        scrollOrientationLocker = ScrollOrientationLocker(customEventDispatcher)
    }

    override fun onPointerInput(
        changes: List<PointerInputChange>,
        pass: PointerEventPass,
        bounds: IntSize
    ): List<PointerInputChange> {

        scrollOrientationLocker.onPointerInputSetup(changes, pass)

        var changesToReturn = changes

        if (pass == PointerEventPass.InitialDown) {

            if (started) {
                // If we are have started we want to prevent any descendants from reacting to
                // any down change.
                changesToReturn = changesToReturn.map {
                    if (it.changedToDown()) {
                        it.consumeDownChange()
                    } else {
                        it
                    }
                }
            }
        }

        if (pass == PointerEventPass.PostUp) {

            val applicableChanges =
                with(orientation) {
                    if (this != null) {
                        scrollOrientationLocker.getPointersFor(changes, this)
                    } else {
                        changes
                    }
                }

            // Handle up changes, which includes removing individual pointer VelocityTrackers
            // and potentially calling onStop().
            if (changesToReturn.fastAny { it.changedToUpIgnoreConsumed() }) {

                var velocityTracker: VelocityTracker? = null

                changesToReturn.fastForEach {
                    // This pointer is up (consumed or not), so we should stop tracking
                    // information about it.  If the pointer is not locked out of our
                    // orientation, get the velocity tracker because this might be a fling.
                    if (it.changedToUp() && applicableChanges.contains(it)) {
                        velocityTracker = velocityTrackers.remove(it.id)
                    } else if (it.changedToUpIgnoreConsumed()) {
                        velocityTrackers.remove(it.id)
                    }
                    // removing stored down position for the pointer.
                    if (it.changedToUp()) {
                        downPositions.remove(it.id)
                    }
                }

                if (changesToReturn.all { it.changedToUpIgnoreConsumed() }) {
                    // All of the pointers are up, so reset and call onStop.  If we have a
                    // velocityTracker at this point, that means at least one of the up events
                    // was not consumed so we should send velocity for flinging.
                    if (started) {
                        val velocity: Offset? =
                            if (velocityTracker != null) {
                                changesToReturn = changesToReturn.map {
                                    it.consumeDownChange()
                                }
                                velocityTracker!!.calculateVelocity().pixelsPerSecond
                            } else {
                                null
                            }
                        started = false
                        dragObserver.onStop(velocity ?: Offset.Zero)
                        reset()
                    }
                }
            }

            // For each new pointer that has been added, start tracking information about it.
            if (changesToReturn.fastAny { it.changedToDownIgnoreConsumed() }) {
                changesToReturn.fastForEach {
                    // If a pointer has changed to down, we should start tracking information
                    // about it.
                    if (it.changedToDownIgnoreConsumed()) {
                        velocityTrackers[it.id] = VelocityTracker()
                            .apply {
                                addPosition(
                                    it.current.uptime!!,
                                    it.current.position!!
                                )
                            }
                        downPositions[it.id] = it.current.position!!
                    }
                }
            }
        }

        // This if block is run for both PostUp and PostDown to allow for the detector to
        // respond to modified changes after ancestors may have modified them.  (This allows
        // for things like dragging an ancestor scrolling container, while keeping a pointer on
        // a descendant scrolling container, and the descendant scrolling container keeping the
        // descendant still.)
        if (pass == PointerEventPass.PostUp || pass == PointerEventPass.PostDown) {

            var (movedChanges, otherChanges) = changesToReturn.partition {
                it.current.down && !it.changedToDownIgnoreConsumed()
            }

            movedChanges.fastForEach {
                // TODO(shepshapard): handle the case that the pointerTrackingData is null,
                // either with an exception or a logged error, or something else.
                val velocityTracker = velocityTrackers[it.id]

                if (velocityTracker != null) {

                    // Add information to the velocity tracker only during one pass.
                    // TODO(shepshapard): VelocityTracker needs to be updated to not accept
                    // position information, but rather vector information about movement.
                    if (pass == PointerEventPass.PostUp) {
                        velocityTracker.addPosition(
                            it.current.uptime!!,
                            it.current.position!!
                        )
                    }
                }
            }

            // Check to see if we are already started so we don't have to call canStartDragging again.
            val canStart = !started && canStartDragging?.invoke() ?: true

            // At this point, check to see if we have started, and if we have, we may
            // be calling onDrag and updating change information on the PointerInputChanges.
            if (started || canStart) {

                var totalDx = 0f
                var totalDy = 0f

                val verticalPointers =
                    scrollOrientationLocker.getPointersFor(
                        movedChanges,
                        Orientation.Vertical
                    )
                val horizontalPointers =
                    scrollOrientationLocker.getPointersFor(
                        movedChanges,
                        Orientation.Horizontal
                    )

                movedChanges.fastForEach {
                    if (horizontalPointers.contains(it) && orientation !=
                        Orientation.Vertical
                    ) {
                        totalDx += it.positionChange().x
                    }
                    if (verticalPointers.contains(it) && orientation !=
                        Orientation.Horizontal
                    ) {
                        totalDy += it.positionChange().y
                    }
                }

                if (totalDx != 0f || totalDy != 0f) {

                    // At this point, if we have not started, check to see if we should start
                    // and if we should, update our state and call onStart().
                    if (!started) {
                        started = true
                        dragObserver.onStart(downPositions.values.averagePosition())
                        downPositions.clear()
                    }

                    // Only need to do this during the first pass that we care about (PostUp).
                    if (pass == PointerEventPass.PostUp) {
                        orientation?.let {
                            scrollOrientationLocker.attemptToLockPointers(
                                movedChanges,
                                it
                            )
                        }
                    }

                    val consumed = dragObserver.onDrag(
                        Offset(
                            totalDx / changesToReturn.size,
                            totalDy / changesToReturn.size
                        )
                    )

                    movedChanges = movedChanges.map {
                        it.consumePositionChange(consumed.x, consumed.y)
                    }
                }
            }

            changesToReturn = movedChanges + otherChanges
        }

        scrollOrientationLocker.onPointerInputTearDown(changes, pass)

        return changesToReturn
    }

    override fun onCancel() {
        downPositions.clear()
        velocityTrackers.clear()
        if (started) {
            started = false
            dragObserver.onCancel()
        }
        scrollOrientationLocker.onCancel()
        reset()
    }

    override fun onCustomEvent(customEvent: CustomEvent, pass: PointerEventPass) {
        scrollOrientationLocker.onCustomEvent(customEvent, pass)
    }

    private fun reset() {
        downPositions.clear()
        velocityTrackers.clear()
    }
}

private fun Iterable<Offset>.averagePosition(): Offset {
    var x = 0f
    var y = 0f
    this.forEach {
        x += it.x
        y += it.y
    }
    return Offset(x / count(), y / count())
}

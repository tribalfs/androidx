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
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.gesture.DoubleTapTimeout
import androidx.compose.ui.gesture.LongPressTimeout
import androidx.compose.ui.layout.globalBounds
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.unit.Duration
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.inMilliseconds
import androidx.compose.ui.unit.milliseconds
import androidx.compose.ui.util.annotation.FloatRange
import androidx.compose.ui.util.lerp
import androidx.compose.ui.test.InputDispatcher.Companion.eventPeriod
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * The distance of a swipe's start position from the node's edge, in terms of the node's length.
 * We do not start the swipe exactly on the node's edge, but somewhat more inward, since swiping
 * from the exact edge may behave in an unexpected way (e.g. may open a navigation drawer).
 */
private const val edgeFuzzFactor = 0.083f

/**
 * The time between the last event of the first click and the first event of the second click in
 * a double click gesture. 145 milliseconds: both median and average of empirical data (33 samples)
 */
private val doubleClickDelay = 145.milliseconds

/**
 * The receiver scope for injecting gestures on the [semanticsNode] identified by the
 * corresponding [SemanticsNodeInteraction]. Gestures can be injected by calling methods defined
 * on [GestureScope], such as [click] or [swipe]. The [SemanticsNodeInteraction] can be found by
 * one of the finder methods such as [ComposeTestRule.onNode].
 *
 * The functions in [GestureScope] can roughly be divided into two groups: full gestures and
 * partial gestures. Partial gestures are the ones that send individual touch events: [down],
 * [move], [up] and [cancel]. Full gestures are all the other functions, like [click],
 * [doubleClick], [swipe], etc. See the documentation of [down] for more information about
 * partial gestures. Normally, if you accidentally try to execute a full gesture while in the
 * middle of a partial gesture, an [IllegalStateException] or [IllegalArgumentException] will be
 * thrown. However, you might want to do this on purpose, for testing multi-touch gestures, where
 * one finger might tap the screen while another is making a gesture. In that case, make sure the
 * partial gesture uses a non-default pointer id.
 *
 * Note that all events generated by the gesture methods are batched together and sent as a whole
 * after [performGesture] has executed its code block.
 *
 * Next to the functions, [GestureScope] also exposes several properties that allow you to get
 * [coordinates][Offset] within a node, like the [top left corner][topLeft], its [center], or
 * 20% to the left of the right edge and 10% below the top edge ([percentOffset]).
 *
 * Example usage:
 * ```
 * onNodeWithTag("myWidget")
 *    .performGesture {
 *        click(center)
 *    }
 *
 * onNodeWithTag("myWidget")
 *    // Perform an L-shaped gesture
 *    .performGesture {
 *        down(topLeft)
 *        move(topLeft + percentOffset(0f, .1f))
 *        move(topLeft + percentOffset(0f, .2f))
 *        move(topLeft + percentOffset(0f, .3f))
 *        move(topLeft + percentOffset(0f, .4f))
 *        move(centerLeft)
 *        move(centerLeft + percentOffset(.1f, 0f))
 *        move(centerLeft + percentOffset(.2f, 0f))
 *        move(centerLeft + percentOffset(.3f, 0f))
 *        move(centerLeft + percentOffset(.4f, 0f))
 *        move(center)
 *        up()
 *    }
 * ```
 */
class GestureScope(node: SemanticsNode, testContext: TestContext) {
    // TODO(b/133217292): Better error: explain which gesture couldn't be performed
    private var _semanticsNode: SemanticsNode? = node
    internal val semanticsNode
        get() = checkNotNull(_semanticsNode) {
            "Can't query SemanticsNode, (Partial)GestureScope has already been disposed"
        }

    // Convenience property
    private val owner get() = semanticsNode.owner

    // TODO(b/133217292): Better error: explain which gesture couldn't be performed
    private var _inputDispatcher: InputDispatcher? =
        createInputDispatcher(testContext, checkNotNull(owner))
    internal val inputDispatcher
        get() = checkNotNull(_inputDispatcher) {
            "Can't send gesture, (Partial)GestureScope has already been disposed"
        }

    /**
     * Returns the size of the visible part of the node we're interacting with. This is contrary
     * to [SemanticsNode.size], which returns the unclipped size of the node.
     */
    val visibleSize: IntSize by lazy {
        val bounds = semanticsNode.boundsInRoot
        IntSize(bounds.width.roundToInt(), bounds.height.roundToInt())
    }

    internal fun dispose() {
        inputDispatcher.dispose()
        _semanticsNode = null
        _inputDispatcher = null
    }
}

/**
 * Shorthand for `size.width`
 */
inline val GestureScope.width: Int
    get() = visibleSize.width

/**
 * Shorthand for `size.height`
 */
inline val GestureScope.height: Int
    get() = visibleSize.height

/**
 * Returns the x-coordinate for the left edge of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 */
@Suppress("unused")
inline val GestureScope.left: Float
    get() = 0f

/**
 * Returns the y-coordinate for the bottom of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 */
@Suppress("unused")
inline val GestureScope.top: Float
    get() = 0f

/**
 * Returns the x-coordinate for the center of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 */
inline val GestureScope.centerX: Float
    get() = width / 2f

/**
 * Returns the y-coordinate for the center of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 */
inline val GestureScope.centerY: Float
    get() = height / 2f

/**
 * Returns the x-coordinate for the right edge of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 * Note that, unless `width == 0`, `right != width`. In particular, `right == width - 1f`, because
 * pixels are 0-based. If `width == 0`, `right == 0` too.
 */
inline val GestureScope.right: Float
    get() = width.let { if (it == 0) 0f else it - 1f }

/**
 * Returns the y-coordinate for the bottom of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 * Note that, unless `height == 0`, `bottom != height`. In particular, `bottom == height - 1f`,
 * because pixels are 0-based. If `height == 0`, `bottom == 0` too.
 */
inline val GestureScope.bottom: Float
    get() = height.let { if (it == 0) 0f else it - 1f }

/**
 * Returns the top left corner of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node.
 */
@Suppress("unused")
val GestureScope.topLeft: Offset
    get() = Offset(left, top)

/**
 * Returns the center of the top edge of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node.
 */
val GestureScope.topCenter: Offset
    get() = Offset(centerX, top)

/**
 * Returns the top right corner of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node. Note that
 * `topRight.x != width`, see [right].
 */
val GestureScope.topRight: Offset
    get() = Offset(right, top)

/**
 * Returns the center of the left edge of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 */
val GestureScope.centerLeft: Offset
    get() = Offset(left, centerY)

/**
 * Returns the center of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node.
 */
val GestureScope.center: Offset
    get() = Offset(centerX, centerY)

/**
 * Returns the center of the right edge of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 * Note that `centerRight.x != width`, see [right].
 */
val GestureScope.centerRight: Offset
    get() = Offset(right, centerY)

/**
 * Returns the bottom left corner of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node. Note that
 * `bottomLeft.y != height`, see [bottom].
 */
val GestureScope.bottomLeft: Offset
    get() = Offset(left, bottom)

/**
 * Returns the center of the bottom edge of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node. Note that
 * `bottomCenter.y != height`, see [bottom].
 */
val GestureScope.bottomCenter: Offset
    get() = Offset(centerX, bottom)

/**
 * Returns the bottom right corner of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node. Note that
 * `bottomRight.x != width` and `bottomRight.y != height`, see [right] and [bottom].
 */
val GestureScope.bottomRight: Offset
    get() = Offset(right, bottom)

/**
 * Creates an [Offset] relative to the size of the node we're interacting with. [x] and [y]
 * are fractions of the [width] and [height]. Note that `percentOffset(1f, 1f) != bottomRight`,
 * see [right] and [bottom].
 *
 * For example: `percentOffset(.5f, .5f)` is the same as the [center]; `centerLeft +
 * percentOffset(.1f, 0f)` is a point 10% inward from the middle of the left edge; and
 * `bottomRight - percentOffset(.2f, .1f)` is a point 20% to the left and 10% to the top of the
 * bottom right corner.
 */
fun GestureScope.percentOffset(
    @FloatRange(from = -1.0, to = 1.0) x: Float = 0f,
    @FloatRange(from = -1.0, to = 1.0) y: Float = 0f
): Offset {
    return Offset(x * width, y * height)
}

/**
 * Transforms the [position] to global coordinates, as defined by
 * [LayoutCoordinates.localToGlobal][androidx.compose.ui.layout.LayoutCoordinates.localToGlobal]
 *
 * @param position A position in local coordinates
 */
private fun GestureScope.localToGlobal(position: Offset): Offset {
    return position + semanticsNode.layoutInfo.coordinates.globalBounds.topLeft
}

/**
 * Performs a click gesture at the given [position] on the associated node, or in the center
 * if the [position] is omitted. The [position] is in the node's local coordinate system,
 * where (0, 0) is the top left corner of the node. The default [position] is the
 * center of the node.
 *
 * @param position The position where to click, in the node's local coordinate system. If
 * omitted, the center position will be used.
 */
fun GestureScope.click(position: Offset = center) {
    inputDispatcher.enqueueClick(localToGlobal(position))
}

/**
 * Performs a long click gesture at the given [position] on the associated node, or in the
 * center if the [position] is omitted. By default, the [duration] of the press is
 * [LongPressTimeout] + 100 milliseconds. The [position] is in the node's local coordinate
 * system, where (0, 0) is the top left corner of the node.
 *
 * @param position The position of the long click, in the node's local coordinate system. If
 * omitted, the center position will be used.
 * @param duration The time between the down and the up event
 */
fun GestureScope.longClick(
    position: Offset = center,
    duration: Duration = LongPressTimeout + 100.milliseconds
) {
    require(duration >= LongPressTimeout) {
        "Long click must have a duration of at least ${LongPressTimeout.inMilliseconds()}ms"
    }
    swipe(position, position, duration)
}

/**
 * Performs a double click gesture at the given [position] on the associated node, or in the
 * center if the [position] is omitted. By default, the [delay] between the first and the second
 * click is 145 milliseconds (empirically established). The [position] is in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node.
 *
 * @param position The position of the double click, in the node's local coordinate system.
 * If omitted, the center position will be used.
 * @param delay The time between the up event of the first click and the down event of the second
 * click
 */
fun GestureScope.doubleClick(
    position: Offset = center,
    delay: Duration = doubleClickDelay
) {
    require(delay <= DoubleTapTimeout - 10.milliseconds) {
        "Time between clicks in double click can be at most ${DoubleTapTimeout - 10.milliseconds}ms"
    }
    val globalPosition = localToGlobal(position)
    inputDispatcher.enqueueClick(globalPosition)
    inputDispatcher.enqueueDelay(delay)
    inputDispatcher.enqueueClick(globalPosition)
}

/**
 * Performs the swipe gesture on the associated node. The motion events are linearly
 * interpolated between [start] and [end]. The coordinates are in the node's local
 * coordinate system, where (0, 0) is the top left corner of the node. The default
 * duration is 200 milliseconds.
 *
 * @param start The start position of the gesture, in the node's local coordinate system
 * @param end The end position of the gesture, in the node's local coordinate system
 * @param duration The duration of the gesture
 */
fun GestureScope.swipe(
    start: Offset,
    end: Offset,
    duration: Duration = 200.milliseconds
) {
    val globalStart = localToGlobal(start)
    val globalEnd = localToGlobal(end)
    inputDispatcher.enqueueSwipe(globalStart, globalEnd, duration)
}

/**
 * Performs a pinch gesture on the associated node.
 *
 * For each pair of start and end [Offset]s, the motion events are linearly interpolated. The
 * coordinates are in the node's local coordinate system where (0, 0) is the top left
 * corner of the node. The default duration is 400 milliseconds.
 *
 * @param start0 The start position of the first gesture in the node's local coordinate system
 * @param end0 The end position of the first gesture in the node's local coordinate system
 * @param start1 The start position of the second gesture in the node's local coordinate system
 * @param end1 The end position of the second gesture in the node's local coordinate system
 * @param duration the duration of the gesture
 */
fun GestureScope.pinch(
    start0: Offset,
    end0: Offset,
    start1: Offset,
    end1: Offset,
    duration: Duration = 400.milliseconds
) {
    val globalStart0 = localToGlobal(start0)
    val globalEnd0 = localToGlobal(end0)
    val globalStart1 = localToGlobal(start1)
    val globalEnd1 = localToGlobal(end1)
    val durationFloat = duration.inMilliseconds().toFloat()

    inputDispatcher.enqueueSwipes(
        listOf<(Long) -> Offset>(
            { lerp(globalStart0, globalEnd0, it / durationFloat) },
            { lerp(globalStart1, globalEnd1, it / durationFloat) }
        ),
        duration
    )
}

/**
 * Performs the swipe gesture on the associated node, such that the velocity when the
 * gesture is finished is roughly equal to [endVelocity]. The MotionEvents are linearly
 * interpolated between [start] and [end]. The coordinates are in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node. The
 * default duration is 200 milliseconds.
 *
 * Note that due to imprecisions, no guarantees can be made on the precision of the actual
 * velocity at the end of the gesture, but generally it is within 0.1% of the desired velocity.
 *
 * @param start The start position of the gesture, in the node's local coordinate system
 * @param end The end position of the gesture, in the node's local coordinate system
 * @param endVelocity The velocity of the gesture at the moment it ends. Must be positive.
 * @param duration The duration of the gesture. Must be long enough that at least 3 input events
 * are generated, which happens with a duration of 25ms or more.
 */
fun GestureScope.swipeWithVelocity(
    start: Offset,
    end: Offset,
    @FloatRange(from = 0.0, to = 3.4e38 /* POSITIVE_INFINITY */) endVelocity: Float,
    duration: Duration = 200.milliseconds
) {
    require(endVelocity >= 0f) {
        "Velocity cannot be $endVelocity, it must be positive"
    }
    require(eventPeriod < 40.milliseconds.inMilliseconds()) {
        "InputDispatcher.eventPeriod must be smaller than 40ms in order to generate velocities"
    }
    val minimumDuration = ceil(2.5f * eventPeriod).roundToInt()
    require(duration >= minimumDuration.milliseconds) {
        "Duration must be at least ${minimumDuration}ms because " +
            "velocity requires at least 3 input events"
    }
    val globalStart = localToGlobal(start)
    val globalEnd = localToGlobal(end)

    // Decompose v into it's x and y components
    val delta = end - start
    val theta = atan2(delta.y, delta.x)
    // VelocityTracker internally calculates px/s, not px/ms
    val vx = cos(theta) * endVelocity / 1000
    val vy = sin(theta) * endVelocity / 1000

    // Note: it would be more precise to do `theta = atan2(-y, x)`, because atan2 expects a
    // coordinate system where positive y goes up and in our coordinate system positive y goes
    // down. However, in that case we would also have to inverse `vy` to convert the velocity
    // back to our own coordinate system. But then it's just a double negation, so we can skip
    // both conversions entirely.

    // To get the desired velocity, generate fx and fy such that VelocityTracker calculates
    // the right velocity. VelocityTracker makes a polynomial fit through the points
    // (-age, x) and (-age, y) for vx and vy respectively, which is accounted for in
    // f(Long, Long, Float, Float, Float).
    val durationMs = duration.inMilliseconds()
    val fx = createFunctionForVelocity(durationMs, globalStart.x, globalEnd.x, vx)
    val fy = createFunctionForVelocity(durationMs, globalStart.y, globalEnd.y, vy)

    inputDispatcher.enqueueSwipe({ t -> Offset(fx(t), fy(t)) }, duration)
}

/**
 * Performs a swipe up gesture on the associated node. The gesture starts slightly above the
 * bottom of the node and ends at the top.
 */
fun GestureScope.swipeUp() {
    val x = center.x
    val y0 = (visibleSize.height * (1 - edgeFuzzFactor)).roundToInt().toFloat()
    val y1 = 0.0f
    val start = Offset(x, y0)
    val end = Offset(x, y1)
    swipe(start, end, 200.milliseconds)
}

/**
 * Performs a swipe down gesture on the associated node. The gesture starts slightly below the
 * top of the node and ends at the bottom.
 */
fun GestureScope.swipeDown() {
    val x = center.x
    val y0 = (visibleSize.height * edgeFuzzFactor).roundToInt().toFloat()
    val y1 = visibleSize.height.toFloat()
    val start = Offset(x, y0)
    val end = Offset(x, y1)
    swipe(start, end, 200.milliseconds)
}

/**
 * Performs a swipe left gesture on the associated node. The gesture starts slightly left of
 * the right side of the node and ends at the left side.
 */
fun GestureScope.swipeLeft() {
    val x0 = (visibleSize.width * (1 - edgeFuzzFactor)).roundToInt().toFloat()
    val x1 = 0.0f
    val y = center.y
    val start = Offset(x0, y)
    val end = Offset(x1, y)
    swipe(start, end, 200.milliseconds)
}

/**
 * Performs a swipe right gesture on the associated node. The gesture starts slightly right of
 * the left side of the node and ends at the right side.
 */
fun GestureScope.swipeRight() {
    val x0 = (visibleSize.width * edgeFuzzFactor).roundToInt().toFloat()
    val x1 = visibleSize.width.toFloat()
    val y = center.y
    val start = Offset(x0, y)
    val end = Offset(x1, y)
    swipe(start, end, 200.milliseconds)
}

/**
 * Generate a function of the form `f(t) = a*(t-T)^2 + b*(t-T) + c` that satisfies
 * `f(0) = [start]`, `f([duration]) = [end]`, `T = [duration]` and `b = [velocity]`.
 *
 * Filling in `f([duration]) = [end]`, `T = [duration]` and `b = [velocity]` gives:
 * * `a * (duration - duration)^2 + velocity * (duration - duration) + c = end`
 * * `c = end`
 *
 * Filling in `f(0) = [start]`, `T = [duration]` and `b = [velocity]` gives:
 * * `a * (0 - duration)^2 + velocity * (0 - duration) + c = start`
 * * `a * duration^2 - velocity * duration + end = start`
 * * `a * duration^2 = start - end + velocity * duration`
 * * `a = (start - end + velocity * duration) / duration^2`
 *
 * @param duration The duration of the fling
 * @param start The start x or y position
 * @param end The end x or y position
 * @param velocity The desired velocity in the x or y direction at the [end] position
 */
private fun createFunctionForVelocity(
    duration: Long,
    start: Float,
    end: Float,
    velocity: Float
): (Long) -> Float {
    val a = (start - end + velocity * duration) / (duration * duration)
    val function = { t: Long ->
        val tMinusDuration = t - duration
        // `f(t) = a*(t-T)^2 + b*(t-T) + c`
        a * tMinusDuration * tMinusDuration + velocity * tMinusDuration + end
    }

    // High velocities often result in curves that start off in the wrong direction, like a bow
    // being strung to reach a high velocity at the end coordinate. For a gesture, that is not
    // desirable, and can be mitigated by using the fact that VelocityTracker only uses the last
    // 100 ms of the gesture. Anything before that doesn't need to follow the curve.

    // Does the function go in the correct direction at the start?
    if (sign(function(1) - start) == sign(end - start)) {
        return function
    } else {
        // If not, lerp between 0 and `duration - 100` in an attempt to prevent the function from
        // going in the wrong direction. This does not affect the velocity at f(duration), as
        // VelocityTracker only uses the last 100ms. This only works if f(duration - 100) is
        // between from and to, log a warning if this is not the case.
        val cutOffTime = duration - 100
        val cutOffValue = function(cutOffTime)
        require(sign(cutOffValue - start) == sign(end - start)) {
            "Creating a gesture between $start and $end with a duration of $duration and a " +
                "resulting velocity of $velocity results in a movement that goes outside " +
                "of the range [$start..$end]"
        }
        return { t ->
            if (t < cutOffTime) {
                lerp(start, cutOffValue, t / cutOffTime.toFloat())
            } else {
                function(t)
            }
        }
    }
}

/**
 * Sends a down event for the pointer with the given [pointerId] at [position] on the associated
 * node. The [position] is in the node's local coordinate system, where (0, 0) is
 * the top left corner of the node.
 *
 * If no pointers are down yet, this will start a new partial gesture. If a partial gesture is
 * already in progress, this event is sent with at the same timestamp as the last event. If the
 * given pointer is already down, an [IllegalArgumentException] will be thrown.
 *
 * This gesture is considered _partial_, because the entire gesture can be spread over several
 * invocations of [performGesture]. An entire gesture starts with a [down][down] event,
 * followed by several down, move or up events, and ends with an [up][up] or a
 * [cancel][cancel] event. Movement can be expressed with [moveTo] and [moveBy] to
 * move a single pointer at a time, or [movePointerTo] and [movePointerBy] to move multiple
 * pointers at a time. The `movePointer[To|By]` methods do not send the move event directly, use
 * [move] to send the move event. Some other methods can send a move event as well. All
 * events, regardless the method used, will always contain the current position of _all_ pointers.
 *
 * Down and up events are sent at the same time as the previous event, but will send an extra
 * move event just before the down or up event if [movePointerTo] or [movePointerBy] has been
 * called and no move event has been sent yet. This does not happen for cancel events, but the
 * cancel event will contain the up to date position of all pointers. Move and cancel events will
 * advance the event time by 10 milliseconds.
 *
 * Because partial gestures don't have to be defined all in the same [performGesture] block,
 * keep in mind that while the gesture is not complete, all code you execute in between
 * blocks that progress the gesture, will be executed while imaginary fingers are actively
 * touching the screen.
 *
 * In the context of testing, it is not necessary to complete a gesture with an up or cancel
 * event, if the test ends before it expects the finger to be lifted from the screen.
 *
 * @param pointerId The id of the pointer, can be any number not yet in use by another pointer
 * @param position The position of the down event, in the node's local coordinate system
 */
fun GestureScope.down(pointerId: Int, position: Offset) {
    val globalPosition = localToGlobal(position)
    inputDispatcher.enqueueDown(pointerId, globalPosition)
}

/**
 * Sends a down event for the default pointer at [position] on the associated node. The
 * [position] is in the node's local coordinate system, where (0, 0) is the top left
 * corner of the node. The default pointer has `pointerId = 0`.
 *
 * If no pointers are down yet, this will start a new partial gesture. If a partial gesture is
 * already in progress, this event is sent with at the same timestamp as the last event. If the
 * default pointer is already down, an [IllegalArgumentException] will be thrown.
 *
 * @param position The position of the down event, in the node's local coordinate system
 */
fun GestureScope.down(position: Offset) {
    down(0, position)
}

/**
 * Sends a move event on the associated node, with the position of the pointer with the
 * given [pointerId] updated to [position]. The [position] is in the node's local coordinate
 * system, where (0, 0) is the top left corner of the node.
 *
 * If the pointer is not yet down, an [IllegalArgumentException] will be thrown.
 *
 * @param pointerId The id of the pointer to move, as supplied in [down]
 * @param position The new position of the pointer, in the node's local coordinate system
 */
fun GestureScope.moveTo(pointerId: Int, position: Offset) {
    movePointerTo(pointerId, position)
    move()
}

/**
 * Sends a move event on the associated node, with the position of the default pointer
 * updated to [position]. The [position] is in the node's local coordinate system, where
 * (0, 0) is the top left corner of the node. The default pointer has `pointerId = 0`.
 *
 * If the default pointer is not yet down, an [IllegalArgumentException] will be thrown.
 *
 * @param position The new position of the pointer, in the node's local coordinate system
 */
fun GestureScope.moveTo(position: Offset) {
    moveTo(0, position)
}

/**
 * Updates the position of the pointer with the given [pointerId] to the given [position], but
 * does not send a move event. The move event can be sent with [move]. The [position] is in
 * the node's local coordinate system, where (0.px, 0.px) is the top left corner of the
 * node.
 *
 * If the pointer is not yet down, an [IllegalArgumentException] will be thrown.
 *
 * @param pointerId The id of the pointer to move, as supplied in [down]
 * @param position The new position of the pointer, in the node's local coordinate system
 */
fun GestureScope.movePointerTo(pointerId: Int, position: Offset) {
    val globalPosition = localToGlobal(position)
    inputDispatcher.movePointer(pointerId, globalPosition)
}

/**
 * Sends a move event on the associated node, with the position of the pointer with the
 * given [pointerId] moved by the given [delta].
 *
 * If the pointer is not yet down, an [IllegalArgumentException] will be thrown.
 *
 * @param pointerId The id of the pointer to move, as supplied in [down]
 * @param delta The position for this move event, relative to the last sent position of the
 * pointer. For example, `delta = Offset(10.px, -10.px) will add 10.px to the pointer's last
 * x-position, and subtract 10.px from the pointer's last y-position.
 */
fun GestureScope.moveBy(pointerId: Int, delta: Offset) {
    movePointerBy(pointerId, delta)
    move()
}

/**
 * Sends a move event on the associated node, with the position of the default pointer
 * moved by the given [delta]. The default pointer has `pointerId = 0`.
 *
 * If the pointer is not yet down, an [IllegalArgumentException] will be thrown.
 *
 * @param delta The position for this move event, relative to the last sent position of the
 * pointer. For example, `delta = Offset(10.px, -10.px) will add 10.px to the pointer's last
 * x-position, and subtract 10.px from the pointer's last y-position.
 */
fun GestureScope.moveBy(delta: Offset) {
    moveBy(0, delta)
}

/**
 * Moves the position of the pointer with the given [pointerId] by the given [delta], but does
 * not send a move event. The move event can be sent with [move].
 *
 * If the pointer is not yet down, an [IllegalArgumentException] will be thrown.
 *
 * @param pointerId The id of the pointer to move, as supplied in [down]
 * @param delta The position for this move event, relative to the last sent position of the
 * pointer. For example, `delta = Offset(10.px, -10.px) will add 10.px to the pointer's last
 * x-position, and subtract 10.px from the pointer's last y-position.
 */
fun GestureScope.movePointerBy(pointerId: Int, delta: Offset) {
    // Ignore currentPosition of null here, let movePointer generate the error
    val globalPosition =
        (inputDispatcher.getCurrentPosition(pointerId) ?: Offset.Zero) + delta
    inputDispatcher.movePointer(pointerId, globalPosition)
}

/**
 * Sends a move event without updating any of the pointer positions. This can be useful when
 * batching movement of multiple pointers together, which can be done with [movePointerTo] and
 * [movePointerBy].
 */
fun GestureScope.move() {
    inputDispatcher.enqueueMove()
}

/**
 * Sends an up event for the pointer with the given [pointerId], or the default pointer if
 * [pointerId] is omitted, on the associated node. If any pointers have been moved with
 * [movePointerTo] or [movePointerBy] and no move event has been sent yet, a move event will be
 * sent right before the up event.
 *
 * @param pointerId The id of the pointer to lift up, as supplied in [down]
 */
fun GestureScope.up(pointerId: Int = 0) {
    inputDispatcher.enqueueUp(pointerId)
}

/**
 * Sends a cancel event to cancel the current partial gesture. The cancel event contains the
 * current position of all active pointers.
 */
fun GestureScope.cancel() {
    inputDispatcher.enqueueCancel()
}

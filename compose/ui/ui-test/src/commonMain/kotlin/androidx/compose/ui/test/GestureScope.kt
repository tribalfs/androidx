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
 * The distance of a swipe's start position from the node's edge, in terms of the node's length.
 * We do not start the swipe exactly on the node's edge, but somewhat more inward, since swiping
 * from the exact edge may behave in an unexpected way (e.g. may open a navigation drawer).
 */
private const val edgeFuzzFactor = 0.083f

/**
 * The time between the last event of the first click and the first event of the second click in
 * a double click gesture. 145 milliseconds: both median and average of empirical data (33 samples)
 */
private const val doubleClickDelayMillis = 145L

/** The time before a long press gesture attempts to win. */
// remove after b/179281066
private const val LongPressTimeoutMillis: Long = 500L

/**
 * The receiver scope for injecting gestures on the [semanticsNode] identified by the
 * corresponding [SemanticsNodeInteraction]. Gestures can be injected by calling methods defined
 * on [GestureScope], such as [click] or [swipe]. The [SemanticsNodeInteraction] can be found by
 * one of the finder methods such as
 * [ComposeTestRule.onNode][androidx.compose.ui.test.junit4.ComposeTestRule.onNode].
 *
 * The functions in [GestureScope] can roughly be divided into two groups: full gestures and
 * individual touch events. The individual touch events are: [down], [move] and friends, [up],
 * [cancel] and [advanceEventTime]. Full gestures are all the other functions, like [click],
 * [doubleClick], [swipe], etc. See the documentation of [down] for more information about
 * individual events. If you execute a full gesture while in the middle of another gesture, an
 * [IllegalStateException] or [IllegalArgumentException] can be thrown when the pointerId is
 * unintentionally used for both gestures. If you want to perform e.g. a click during a partially
 * performed gesture, make sure they use different pointer ids.
 *
 * Note that all events generated by the gesture methods are batched together and sent as a whole
 * after [performGesture] has executed its code block.
 *
 * Next to the functions, [GestureScope] also exposes several properties that allow you to get
 * [coordinates][Offset] within a node, like the [top left corner][topLeft], its [center], or
 * some percentage of the size ([percentOffset]).
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
    @PublishedApi
    internal val delegateScope = MultiModalInjectionScope(node, testContext)

    internal val semanticsNode get() = delegateScope.semanticsNode
    internal val inputDispatcher get() = delegateScope.inputDispatcher

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
    val visibleSize: IntSize = delegateScope.visibleSize

    internal fun dispose() = delegateScope.dispose()
}

/**
 * Shorthand for `size.width`
 */
inline val GestureScope.width: Int
    get() = delegateScope.width

/**
 * Shorthand for `size.height`
 */
inline val GestureScope.height: Int
    get() = delegateScope.height

/**
 * Returns the x-coordinate for the left edge of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 */
@Suppress("unused")
inline val GestureScope.left: Float
    get() = delegateScope.left

/**
 * Returns the y-coordinate for the bottom of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 */
@Suppress("unused")
inline val GestureScope.top: Float
    get() = delegateScope.top

/**
 * Returns the x-coordinate for the center of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 */
inline val GestureScope.centerX: Float
    get() = delegateScope.centerX

/**
 * Returns the y-coordinate for the center of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 */
inline val GestureScope.centerY: Float
    get() = delegateScope.centerY

/**
 * Returns the x-coordinate for the right edge of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 * Note that, unless `width == 0`, `right != width`. In particular, `right == width - 1f`, because
 * pixels are 0-based. If `width == 0`, `right == 0` too.
 */
inline val GestureScope.right: Float
    get() = delegateScope.right

/**
 * Returns the y-coordinate for the bottom of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 * Note that, unless `height == 0`, `bottom != height`. In particular, `bottom == height - 1f`,
 * because pixels are 0-based. If `height == 0`, `bottom == 0` too.
 */
inline val GestureScope.bottom: Float
    get() = delegateScope.bottom

/**
 * Returns the top left corner of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node.
 */
@Suppress("unused")
val GestureScope.topLeft: Offset
    get() = delegateScope.topLeft

/**
 * Returns the center of the top edge of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node.
 */
val GestureScope.topCenter: Offset
    get() = delegateScope.topCenter

/**
 * Returns the top right corner of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node. Note that
 * `topRight.x != width`, see [right].
 */
val GestureScope.topRight: Offset
    get() = delegateScope.topRight

/**
 * Returns the center of the left edge of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 */
val GestureScope.centerLeft: Offset
    get() = delegateScope.centerLeft

/**
 * Returns the center of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node.
 */
val GestureScope.center: Offset
    get() = delegateScope.center

/**
 * Returns the center of the right edge of the node we're interacting with, in the
 * node's local coordinate system, where (0, 0) is the top left corner of the node.
 * Note that `centerRight.x != width`, see [right].
 */
val GestureScope.centerRight: Offset
    get() = delegateScope.centerRight

/**
 * Returns the bottom left corner of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node. Note that
 * `bottomLeft.y != height`, see [bottom].
 */
val GestureScope.bottomLeft: Offset
    get() = delegateScope.bottomLeft

/**
 * Returns the center of the bottom edge of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node. Note that
 * `bottomCenter.y != height`, see [bottom].
 */
val GestureScope.bottomCenter: Offset
    get() = delegateScope.bottomCenter

/**
 * Returns the bottom right corner of the node we're interacting with, in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node. Note that
 * `bottomRight.x != width` and `bottomRight.y != height`, see [right] and [bottom].
 */
val GestureScope.bottomRight: Offset
    get() = delegateScope.bottomRight

/**
 * Creates an [Offset] relative to the size of the node we're interacting with. [x] and [y]
 * are fractions of the [width] and [height], between `-1` and `1`.
 * Note that `percentOffset(1f, 1f) != bottomRight`, see [right] and [bottom].
 *
 * For example: `percentOffset(.5f, .5f)` is the same as the [center]; `centerLeft +
 * percentOffset(.1f, 0f)` is a point 10% inward from the middle of the left edge; and
 * `bottomRight - percentOffset(.2f, .1f)` is a point 20% to the left and 10% to the top of the
 * bottom right corner.
 */
fun GestureScope.percentOffset(
    /*@FloatRange(from = -1.0, to = 1.0)*/
    x: Float = 0f,
    /*@FloatRange(from = -1.0, to = 1.0)*/
    y: Float = 0f
): Offset = delegateScope.percentOffset(x, y)

/**
 * Performs a click gesture at the given [position] on the associated node, or in the center
 * if the [position] is omitted. The [position] is in the node's local coordinate system,
 * where (0, 0) is the top left corner of the node. The default [position] is the
 * center of the node.
 *
 * @param position The position where to click, in the node's local coordinate system. If
 * omitted, the center position will be used.
 */
fun GestureScope.click(position: Offset = center) = delegateScope.Touch.click(position)

/**
 * Performs a long click gesture at the given [position] on the associated node, or in the
 * center if the [position] is omitted. By default, the [durationMillis] of the press is
 * [LongPressTimeoutMillis] + 100 milliseconds. The [position] is in the node's local coordinate
 * system, where (0, 0) is the top left corner of the node.
 *
 * @param position The position of the long click, in the node's local coordinate system. If
 * omitted, the center position will be used.
 * @param durationMillis The time between the down and the up event
 */
fun GestureScope.longClick(
    position: Offset = center,
    durationMillis: Long = LongPressTimeoutMillis + 100
) = delegateScope.Touch.longClick(position, durationMillis)

/**
 * Performs a double click gesture at the given [position] on the associated node, or in the
 * center if the [position] is omitted. By default, the [delayMillis] between the first and the
 * second click is 145 milliseconds (empirically established). The [position] is in the node's
 * local coordinate system, where (0, 0) is the top left corner of the node.
 *
 * @param position The position of the double click, in the node's local coordinate system.
 * If omitted, the center position will be used.
 * @param delayMillis The time between the up event of the first click and the down event of the
 * second click
 */
fun GestureScope.doubleClick(
    position: Offset = center,
    delayMillis: Long = doubleClickDelayMillis
) = delegateScope.Touch.doubleClick(position, delayMillis)

/**
 * Performs the swipe gesture on the associated node. The motion events are linearly
 * interpolated between [start] and [end]. The coordinates are in the node's local
 * coordinate system, where (0, 0) is the top left corner of the node. The default
 * duration is 200 milliseconds.
 *
 * @param start The start position of the gesture, in the node's local coordinate system
 * @param end The end position of the gesture, in the node's local coordinate system
 * @param durationMillis The duration of the gesture
 */
fun GestureScope.swipe(
    start: Offset,
    end: Offset,
    durationMillis: Long = 200
) = delegateScope.Touch.swipe(start, end, durationMillis)

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
 * @param durationMillis the duration of the gesture
 */
fun GestureScope.pinch(
    start0: Offset,
    end0: Offset,
    start1: Offset,
    end1: Offset,
    durationMillis: Long = 400
) = delegateScope.Touch.pinch(start0, end0, start1, end1, durationMillis)

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
 * @param durationMillis The duration of the gesture in milliseconds. Must be long enough that at
 * least 3 input events are generated, which happens with a duration of 25ms or more.
 */
fun GestureScope.swipeWithVelocity(
    start: Offset,
    end: Offset,
    /*@FloatRange(from = 0.0)*/
    endVelocity: Float,
    durationMillis: Long = 200
) = delegateScope.Touch.swipeWithVelocity(start, end, endVelocity, durationMillis)

/**
 * Performs a swipe up gesture along the [centerX] of the associated node. The gesture starts
 * slightly above the [bottom] of the node and ends at the [top].
 */
fun GestureScope.swipeUp() = delegateScope.Touch.swipeUp()

/**
 * Performs a swipe up gesture along the [centerX] of the associated node, from [startY] till
 * [endY], taking [durationMillis] milliseconds.
 *
 * @param startY The y-coordinate of the start of the swipe. Must be greater than or equal to the
 * [endY]. By default slightly above the [bottom] of the node.
 * @param endY The y-coordinate of the end of the swipe. Must be less than or equal to the
 * [startY]. By default the [top] of the node.
 * @param durationMillis The duration of the swipe. By default 200 milliseconds.
 */
@ExperimentalTestApi
fun GestureScope.swipeUp(
    startY: Float = bottomFuzzed,
    endY: Float = top,
    durationMillis: Long = 200
) = delegateScope.Touch.swipeUp(startY, endY, durationMillis)

/**
 * Performs a swipe down gesture along the [centerX] of the associated node. The gesture starts
 * slightly below the [top] of the node and ends at the [bottom].
 */
fun GestureScope.swipeDown() = delegateScope.Touch.swipeDown()

/**
 * Performs a swipe down gesture along the [centerX] of the associated node, from [startY] till
 * [endY], taking [durationMillis] milliseconds.
 *
 * @param startY The y-coordinate of the start of the swipe. Must be less than or equal to the
 * [endY]. By default slightly below the [top] of the node.
 * @param endY The y-coordinate of the end of the swipe. Must be greater than or equal to the
 * [startY]. By default the [bottom] of the node.
 * @param durationMillis The duration of the swipe. By default 200 milliseconds.
 */
@ExperimentalTestApi
fun GestureScope.swipeDown(
    startY: Float = topFuzzed,
    endY: Float = bottom,
    durationMillis: Long = 200
) = delegateScope.Touch.swipeDown(startY, endY, durationMillis)

/**
 * Performs a swipe left gesture along the [centerY] of the associated node. The gesture starts
 * slightly left of the [right] side of the node and ends at the [left] side.
 */
fun GestureScope.swipeLeft() = delegateScope.Touch.swipeLeft()

/**
 * Performs a swipe left gesture along the [centerY] of the associated node, from [startX] till
 * [endX], taking [durationMillis] milliseconds.
 *
 * @param startX The x-coordinate of the start of the swipe. Must be greater than or equal to the
 * [endX]. By default slightly left of the [right] of the node.
 * @param endX The x-coordinate of the end of the swipe. Must be less than or equal to the
 * [startX]. By default the [left] of the node.
 * @param durationMillis The duration of the swipe. By default 200 milliseconds.
 */
@ExperimentalTestApi
fun GestureScope.swipeLeft(
    startX: Float = rightFuzzed,
    endX: Float = left,
    durationMillis: Long = 200
) = delegateScope.Touch.swipeLeft(startX, endX, durationMillis)

/**
 * Performs a swipe right gesture along the [centerY] of the associated node. The gesture starts
 * slightly right of the [left] side of the node and ends at the [right] side.
 */
fun GestureScope.swipeRight() = delegateScope.Touch.swipeRight()

/**
 * Performs a swipe right gesture along the [centerY] of the associated node, from [startX] till
 * [endX], taking [durationMillis] milliseconds.
 *
 * @param startX The x-coordinate of the start of the swipe. Must be less than or equal to the
 * [endX]. By default slightly right of the [left] of the node.
 * @param endX The x-coordinate of the end of the swipe. Must be greater than or equal to the
 * [startX]. By default the [right] of the node.
 * @param durationMillis The duration of the swipe. By default 200 milliseconds.
 */
@ExperimentalTestApi
fun GestureScope.swipeRight(
    startX: Float = leftFuzzed,
    endX: Float = right,
    durationMillis: Long = 200
) = delegateScope.Touch.swipeRight(startX, endX, durationMillis)

private val Int.startFuzzed: Float get() = (this * edgeFuzzFactor).roundToInt().toFloat()
private val Int.endFuzzed: Float get() = (this * (1 - edgeFuzzFactor)).roundToInt().toFloat()

private val GestureScope.leftFuzzed: Float get() = width.startFuzzed
private val GestureScope.topFuzzed: Float get() = height.startFuzzed
private val GestureScope.rightFuzzed: Float get() = width.endFuzzed
private val GestureScope.bottomFuzzed: Float get() = height.endFuzzed

/**
 * Sends a down event for the pointer with the given [pointerId] at [position] on the associated
 * node. The [position] is in the node's local coordinate system, where (0, 0) is
 * the top left corner of the node.
 *
 * If no pointers are down yet, this will start a new gesture. If a gesture is
 * already in progress, this event is sent with at the same timestamp as the last event. If the
 * given pointer is already down, an [IllegalArgumentException] will be thrown.
 *
 * Subsequent events for this or other gestures can be spread out over both this and future
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
 * Because gestures don't have to be defined all in the same [performGesture] block,
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
fun GestureScope.down(pointerId: Int, position: Offset) =
    delegateScope.Touch.down(pointerId, position)

/**
 * Sends a down event for the default pointer at [position] on the associated node. The
 * [position] is in the node's local coordinate system, where (0, 0) is the top left
 * corner of the node. The default pointer has `pointerId = 0`.
 *
 * If no pointers are down yet, this will start a new gesture. If a gesture is
 * already in progress, this event is sent with at the same timestamp as the last event. If the
 * default pointer is already down, an [IllegalArgumentException] will be thrown.
 *
 * @param position The position of the down event, in the node's local coordinate system
 */
fun GestureScope.down(position: Offset) = delegateScope.Touch.down(position)

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
fun GestureScope.moveTo(pointerId: Int, position: Offset) =
    delegateScope.Touch.moveTo(pointerId, position)

/**
 * Sends a move event on the associated node, with the position of the default pointer
 * updated to [position]. The [position] is in the node's local coordinate system, where
 * (0, 0) is the top left corner of the node. The default pointer has `pointerId = 0`.
 *
 * If the default pointer is not yet down, an [IllegalArgumentException] will be thrown.
 *
 * @param position The new position of the pointer, in the node's local coordinate system
 */
fun GestureScope.moveTo(position: Offset) = delegateScope.Touch.moveTo(position)

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
fun GestureScope.movePointerTo(pointerId: Int, position: Offset) =
    delegateScope.Touch.updatePointerTo(pointerId, position)

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
fun GestureScope.moveBy(pointerId: Int, delta: Offset) =
    delegateScope.Touch.moveBy(pointerId, delta)

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
fun GestureScope.moveBy(delta: Offset) = delegateScope.Touch.moveBy(delta)

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
fun GestureScope.movePointerBy(pointerId: Int, delta: Offset) =
    delegateScope.Touch.updatePointerBy(pointerId, delta)

/**
 * Sends a move event without updating any of the pointer positions. This can be useful when
 * batching movement of multiple pointers together, which can be done with [movePointerTo] and
 * [movePointerBy].
 */
fun GestureScope.move() = delegateScope.Touch.move()

/**
 * Sends an up event for the pointer with the given [pointerId], or the default pointer if
 * [pointerId] is omitted, on the associated node. If any pointers have been moved with
 * [movePointerTo] or [movePointerBy] and no move event has been sent yet, a move event will be
 * sent right before the up event.
 *
 * @param pointerId The id of the pointer to lift up, as supplied in [down]
 */
fun GestureScope.up(pointerId: Int = 0) = delegateScope.Touch.up(pointerId)

/**
 * Sends a cancel event to cancel the current gesture. The cancel event contains the
 * current position of all active pointers.
 */
fun GestureScope.cancel() = delegateScope.Touch.cancel()

/**
 * Adds the given [durationMillis] to the current event time, delaying the next event by that
 * time. Only valid when a gesture has already been started, or when a finished gesture is resumed.
 */
@ExperimentalTestApi
fun GestureScope.advanceEventTime(durationMillis: Long) =
    delegateScope.Touch.advanceEventTime(durationMillis)

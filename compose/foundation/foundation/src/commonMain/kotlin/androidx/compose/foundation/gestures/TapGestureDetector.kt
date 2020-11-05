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

package androidx.compose.foundation.gestures

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.gesture.ExperimentalPointerInput
import androidx.compose.ui.input.pointer.HandlePointerInputScope
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.anyPositionChangeConsumed
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.consumeDownChange
import androidx.compose.ui.input.pointer.isOutOfBounds
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Uptime
import androidx.compose.ui.unit.inMilliseconds
import androidx.compose.ui.unit.seconds
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Receiver scope for [tapGestureDetector]'s `onPress` lambda. This offers
 * two methods to allow waiting for the press to be released.
 */
interface PressGestureScope : Density {
    /**
     * Waits for the press to be released before returning. If the gesture was canceled by
     * motion being consumed by another gesture, [GestureCancellationException] will be
     * thrown.
     */
    suspend fun awaitRelease()

    /**
     * Waits for the press to be released before returning. If the press was released,
     * `false` is returned, or if the gesture was canceled by motion being consumed by
     * another gesture, `false` is returned .
     */
    suspend fun tryAwaitRelease(): Boolean
}

private val NoPressGesture: suspend PressGestureScope.(Offset) -> Unit = { }

/**
 * Detects tap, double-tap, and long press gestures and calls [onTap], [onDoubleTap], and
 * [onLongPress], respectively, when detected. [onPress] is called when the press is detected
 * and the [PressGestureScope.tryAwaitRelease] and [PressGestureScope.awaitRelease] can be
 * used to detect when pointers have released or the gesture was canceled.
 * The first pointer down and final pointer up are consumed, and in the
 * case of long press, all changes after the long press is detected are consumed.
 *
 * When [onDoubleTap] is provided, the tap gesture is detected only after
 * the [ViewConfiguration.doubleTapMinTime] has passed and [onDoubleTap] is called if the second
 * tap is started before [ViewConfiguration.doubleTapTimeout]. If [onDoubleTap] is not provided,
 * then [onTap] is called when the pointer up has been received.
 *
 * If the first down event was consumed, the entire gesture will be skipped, including
 * [onPress]. If the first down event was not consumed, if any other gesture consumes the down or
 * up events, the pointer moves out of the input area, or the position change is consumed,
 * the gestures are considered canceled. [onDoubleTap], [onLongPress], and [onTap] will not be
 * called after a gesture has been canceled.
 */
@ExperimentalPointerInput
suspend fun PointerInputScope.tapGestureDetector(
    onDoubleTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onPress: suspend PressGestureScope.(Offset) -> Unit = NoPressGesture,
    onTap: () -> Unit
) {
    val pressScope = PressGestureScopeImpl(this)
    forEachGesture {
        coroutineScope {
            pressScope.reset()
            val down = handlePointerInput {
                waitForFirstDown().also {
                    it.consumeDownChange()
                }
            }
            if (onPress !== NoPressGesture) {
                launch { pressScope.onPress(down.current.position!!) }
            }

            val longPressTimeout =
                if (onLongPress == null) {
                    Int.MAX_VALUE.seconds
                } else {
                    viewConfiguration.longPressTimeout
                }

            var up: PointerInputChange? = null
            try {
                // wait for first tap up or long press
                up = withTimeout(longPressTimeout.inMilliseconds()) {
                    handlePointerInput {
                        waitForUpOrCancel()?.also { it.consumeDownChange() }
                    }
                }
                if (up == null) {
                    pressScope.cancel() // tap-up was canceled
                } else {
                    pressScope.release()
                }
            } catch (_: TimeoutCancellationException) {
                onLongPress?.invoke()
                consumeAllEventsUntilUp()
                pressScope.release()
            }

            if (up != null) {
                // tap was successful.
                if (onDoubleTap == null) {
                    onTap() // no need to check for double-tap.
                } else {
                    // check for second tap
                    val secondDown = detectSecondTapDown(up.current.uptime!!)

                    if (secondDown == null) {
                        onTap() // no valid second tap started
                    } else {
                        // Second tap down detected
                        secondDown.consumeDownChange()
                        pressScope.reset()
                        if (onPress !== NoPressGesture) {
                            launch { pressScope.onPress(secondDown.current.position!!) }
                        }

                        try {
                            // Might have a long second press as the second tap
                            withTimeout(longPressTimeout.inMilliseconds()) {
                                handlePointerInput {
                                    val secondUp = waitForUpOrCancel()
                                    if (secondUp == null) {
                                        pressScope.cancel()
                                        onTap()
                                    } else {
                                        secondUp.consumeDownChange()
                                        pressScope.release()
                                        onDoubleTap()
                                    }
                                }
                            }
                        } catch (e: TimeoutCancellationException) {
                            // The first tap was valid, but the second tap is a long press.
                            // notify for the first tap
                            onTap()

                            // notify for the long press
                            onLongPress?.invoke()
                            consumeAllEventsUntilUp()
                            pressScope.release()
                        }
                    }
                }
            }
        }
    }
}

/**
 * Reads events until the first down is received and it isn't consumed in the
 * [PointerEventPass.Main] pass.
 */
@ExperimentalPointerInput
suspend fun HandlePointerInputScope.waitForFirstDown(): PointerInputChange {
    var event: PointerEvent
    do {
        event = awaitPointerEvent()
    } while (!event.changes.fastAll { it.changedToDown() })
    return event.changes[0]
}

/**
 * Reads events until all pointers are up or the gesture was canceled. The gesture
 * is considered canceled when a pointer leaves the event region, a position change
 * has been consumed or a pointer down change event was consumed in the [PointerEventPass.Main]
 * pass. If the gesture was not canceled, the final up change is returned or `null` if the
 * event was canceled.
 */
@ExperimentalPointerInput
suspend fun HandlePointerInputScope.waitForUpOrCancel(): PointerInputChange? {
    while (true) {
        val event = awaitPointerEvent(PointerEventPass.Main)
        if (event.changes.fastAll { it.changedToUp() }) {
            // All pointers are up
            return event.changes[0]
        }

        if (event.changes.fastAny { it.consumed.downChange || it.isOutOfBounds(size) }) {
            return null // Canceled
        }

        // Check for cancel by position consumption. We can look on the Final pass of the
        // existing pointer event because it comes after the Main pass we checked above.
        val consumeCheck = awaitPointerEvent(PointerEventPass.Final)
        if (consumeCheck.changes.fastAny { it.anyPositionChangeConsumed() }) {
            return null
        }
    }
}

/**
 * Consumes all event changes in the [PointerEventPass.Initial] until all pointers are up.
 */
@ExperimentalPointerInput
private suspend fun PointerInputScope.consumeAllEventsUntilUp() {
    handlePointerInput {
        if (!allPointersUp()) {
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.fastForEach { it.consumeAllChanges() }
            } while (event.changes.fastAny { it.current.down })
        }
    }
}

/**
 * Reads input for second tap down event. If the second tap is within
 * [ViewConfiguration.doubleTapMinTime] of [upTime], the event is discarded. If the second down is
 * not detected within [ViewConfiguration.doubleTapTimeout] of [upTime], `null` is returned.
 * Otherwise, the down event is returned.
 */
@ExperimentalPointerInput
private suspend fun PointerInputScope.detectSecondTapDown(
    upTime: Uptime
): PointerInputChange? {
    return withTimeoutOrNull(viewConfiguration.doubleTapTimeout.inMilliseconds()) {
        handlePointerInput {
            val minUptime = upTime + viewConfiguration.doubleTapMinTime
            var change: PointerInputChange
            // The second tap doesn't count if it happens before DoubleTapMinTime of the first tap
            do {
                change = waitForFirstDown()
            } while (change.current.uptime!! < minUptime)
            change
        }
    }
}

/**
 * [tapGestureDetector]'s implementation of [PressGestureScope].
 */
private class PressGestureScopeImpl(
    density: Density
) : PressGestureScope, Density by density {
    private var isReleased = false
    private var isCanceled = false
    private val mutex = Mutex(locked = false)

    /**
     * Called when a gesture has been canceled.
     */
    fun cancel() {
        isCanceled = true
        mutex.unlock()
    }

    /**
     * Called when all pointers are up.
     */
    fun release() {
        isReleased = true
        mutex.unlock()
    }

    /**
     * Called when a new gesture has started.
     */
    fun reset() {
        mutex.tryLock() // If tryAwaitRelease wasn't called, this will be unlocked.
        isReleased = false
        isCanceled = false
    }

    override suspend fun awaitRelease() {
        if (!tryAwaitRelease()) {
            throw GestureCancellationException("The press gesture was canceled.")
        }
    }

    override suspend fun tryAwaitRelease(): Boolean {
        if (!isReleased && !isCanceled) {
            mutex.lock()
        }
        return isReleased
    }
}
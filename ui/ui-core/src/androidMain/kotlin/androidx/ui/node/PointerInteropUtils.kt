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

package androidx.ui.node

import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.MotionEvent.ACTION_CANCEL
import androidx.ui.core.PointerEvent
import androidx.compose.ui.geometry.Offset
import androidx.ui.unit.Duration
import androidx.ui.unit.NanosecondsPerMillisecond
import androidx.ui.unit.milliseconds

/**
 * Converts to a [MotionEvent] and runs [block] with it.
 *
 * @param offset The offset to be applied to the resulting [MotionEvent].
 * @param block The block to be executed with the resulting [MotionEvent].
 */
internal fun PointerEvent.toMotionEventScope(
    offset: Offset,
    block: (MotionEvent) -> Unit
) {
    toMotionEventScope(offset, block, false)
}

/**
 * Converts to an [MotionEvent.ACTION_CANCEL] [MotionEvent] and runs [block] with it.
 *
 * @param offset The offset to be applied to the resulting [MotionEvent].
 * @param block The block to be executed with the resulting [MotionEvent].
 */
internal fun PointerEvent.toCancelMotionEventScope(
    offset: Offset,
    block: (MotionEvent) -> Unit
) {
    toMotionEventScope(offset, block, true)
}

internal fun emptyCancelMotionEventScope(
    now: Duration = SystemClock.uptimeMillis().milliseconds,
    block: (MotionEvent) -> Unit
) {
    // Does what ViewGroup does when it needs to send a minimal ACTION_CANCEL event.
    val nowMillis = now.nanoseconds / NanosecondsPerMillisecond
    val motionEvent =
        MotionEvent.obtain(nowMillis, nowMillis, ACTION_CANCEL, 0.0f, 0.0f, 0)
    motionEvent.source = InputDevice.SOURCE_UNKNOWN
    block(motionEvent)
    motionEvent.recycle()
}

private fun PointerEvent.toMotionEventScope(
    offset: Offset,
    block: (MotionEvent) -> Unit,
    cancel: Boolean
) {
    requireNotNull(motionEvent) {
        "The PointerEvent receiver cannot have a null MotionEvent."
    }

    motionEvent.apply {
        val oldAction = action
        if (cancel) {
            action = ACTION_CANCEL
        }

        offsetLocation(-offset.x, -offset.y)

        block(this)

        offsetLocation(offset.x, offset.y)

        action = oldAction
    }
}
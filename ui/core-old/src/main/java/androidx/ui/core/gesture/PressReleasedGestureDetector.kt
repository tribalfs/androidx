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

package androidx.ui.core.gesture

import androidx.ui.core.PointerInput
import androidx.ui.core.pointerinput.PointerEventPass
import androidx.ui.core.pointerinput.PointerInputChange
import androidx.ui.core.pointerinput.anyPositionChangeConsumed
import androidx.ui.core.pointerinput.changedToDown
import androidx.ui.core.pointerinput.changedToDownIgnoreConsumed
import androidx.ui.core.pointerinput.changedToUp
import androidx.ui.core.pointerinput.changedToUpIgnoreConsumed
import androidx.ui.core.pointerinput.consumeDownChange
import com.google.r4a.Children
import com.google.r4a.Component

// TODO(shepshapard): Convert to functional component with effects once effects are ready.
/**
 * This gesture detector has a callback for when a press gesture being released for the purposes of
 * firing an event in response to something like a button being pressed.
 *
 * More specifically, it will call [onRelease] if:
 * - The first [PointerInputChange] it receives during the [PointerEventPass.PostUp] pass has
 *   an unconsumed down change.
 * - The last [PointerInputChange]  it receives during the [PointerEventPass.PostUp] pass has
 *   an unconsumed up change.
 * - And while it has at least one pointer touching it, no [PointerInputChange] has had any
 *   movement consumed (as that would indicate that something in the heirarchy moved and this a
 *   press should be cancelled.
 *
 * By default, this gesture detector also consumes the down change during the
 * [PointerEventPass.PostUp] pass if it has not already been consumed. That behavior can be changed
 * via [consumeDownOnStart].
 */
class PressReleasedGestureDetector(
    @Children var children: () -> Unit
) : Component() {
    private val recognizer = PressReleaseGestureRecognizer()
    var onRelease: (() -> Unit)?
        get() = recognizer.onRelease
        set(value) {
            recognizer.onRelease = value
        }
    var consumeDownOnStart
        get() = recognizer.consumeDownOnStart
        set(value) {
            recognizer.consumeDownOnStart = value
        }

    override fun compose() {
        <PointerInput pointerInputHandler=recognizer.pointerInputHandler>
            <children />
        </PointerInput>
    }
}

internal class PressReleaseGestureRecognizer {
    /**
     * Called to indicate that a press gesture has successfully completed.
     *
     * This should be used to fire a state changing event as if a button was pressed.
     */
    var onRelease: (() -> Unit)? = null
    /**
     * True if down change should be consumed when start is called.  The default is true.
     */
    var consumeDownOnStart = true

    private var pointerCount = 0
    private var shouldRespondToUp = false

    val pointerInputHandler = { event: PointerInputChange, pass: PointerEventPass ->
        var pointerEvent: PointerInputChange = event

        if (pass == PointerEventPass.InitialDown && pointerEvent.changedToDownIgnoreConsumed()) {
            pointerCount++
        }

        if (pass == PointerEventPass.PostUp && pointerCount == 1) {
            if (pointerEvent.changedToDown()) {
                shouldRespondToUp = true
                if (consumeDownOnStart) {
                    pointerEvent = pointerEvent.consumeDownChange()
                }
            }
            if (shouldRespondToUp && pointerEvent.changedToUp()) {
                onRelease?.invoke()
                pointerEvent = pointerEvent.consumeDownChange()
            }
        }

        if (pass == PointerEventPass.PostDown && event.anyPositionChangeConsumed()) {
            shouldRespondToUp = false
        }

        if (pass == PointerEventPass.PostDown && pointerEvent.changedToUpIgnoreConsumed()) {
            pointerCount--
            if (pointerCount == 0) {
                shouldRespondToUp = false
            }
        }

        pointerEvent
    }
}
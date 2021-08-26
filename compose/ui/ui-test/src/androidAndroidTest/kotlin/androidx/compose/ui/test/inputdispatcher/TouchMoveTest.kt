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

package androidx.compose.ui.test.inputdispatcher

import android.view.MotionEvent.ACTION_CANCEL
import android.view.MotionEvent.ACTION_DOWN
import android.view.MotionEvent.ACTION_MOVE
import android.view.MotionEvent.ACTION_POINTER_DOWN
import android.view.MotionEvent.ACTION_POINTER_UP
import androidx.compose.testutils.expectError
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.AndroidInputDispatcher
import androidx.compose.ui.test.InputDispatcher.Companion.eventPeriodMillis
import androidx.compose.ui.test.util.Finger
import androidx.compose.ui.test.util.Touchscreen
import androidx.compose.ui.test.util.assertHasValidEventTimes
import androidx.compose.ui.test.util.verifyEvent
import androidx.compose.ui.test.util.verifyPointer
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Tests if [AndroidInputDispatcher.updateTouchPointer] and
 * [AndroidInputDispatcher.enqueueTouchMove] work
 */
@SmallTest
class TouchMoveTest : InputDispatcherTest() {
    companion object {
        // pointerIds
        private const val pointer1 = 11
        private const val pointer2 = 22
        private const val pointer3 = 33

        // positions (used with corresponding pointerId: pointerX with positionX_Y)
        private val position1_1 = Offset(11f, 11f)
        private val position2_1 = Offset(21f, 21f)
        private val position3_1 = Offset(31f, 31f)

        private val position1_2 = Offset(12f, 12f)
        private val position2_2 = Offset(22f, 22f)

        private val position1_3 = Offset(13f, 13f)
    }

    private fun AndroidInputDispatcher.generateCancelAndCheckPointers() {
        generateTouchCancelAndCheck()
        assertThat(getCurrentTouchPosition(pointer1)).isNull()
        assertThat(getCurrentTouchPosition(pointer2)).isNull()
        assertThat(getCurrentTouchPosition(pointer3)).isNull()
    }

    @Test
    fun onePointer() {
        subject.generateTouchDownAndCheck(pointer1, position1_1)
        subject.updateTouchPointerAndCheck(pointer1, position1_2)
        subject.advanceEventTime()
        subject.enqueueTouchMove()
        subject.sendAllSynchronous()

        var t = 0L
        recorder.assertHasValidEventTimes()
        assertThat(recorder.events).hasSize(2)
        recorder.events[0].verifyEvent(1, ACTION_DOWN, 0, t, Touchscreen) // pointer1
        recorder.events[0].verifyPointer(pointer1, position1_1, Finger)

        t += eventPeriodMillis
        recorder.events[1].verifyEvent(1, ACTION_MOVE, 0, t, Touchscreen) // pointer1
        recorder.events[1].verifyPointer(pointer1, position1_2, Finger)
    }

    @Test
    fun twoPointers_downDownMoveMove() {
        // 2 fingers, both go down before they move
        subject.generateTouchDownAndCheck(pointer1, position1_1)
        subject.generateTouchDownAndCheck(pointer2, position2_1)
        subject.updateTouchPointerAndCheck(pointer1, position1_2)
        subject.advanceEventTime()
        subject.enqueueTouchMove()
        subject.updateTouchPointerAndCheck(pointer2, position2_2)
        subject.advanceEventTime()
        subject.enqueueTouchMove()
        subject.sendAllSynchronous()

        recorder.assertHasValidEventTimes()
        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(4)

            this[0].verifyEvent(1, ACTION_DOWN, 0, t, Touchscreen) // pointer1
            this[0].verifyPointer(pointer1, position1_1, Finger)

            this[1].verifyEvent(2, ACTION_POINTER_DOWN, 1, t, Touchscreen) // pointer2
            this[1].verifyPointer(pointer1, position1_1, Finger)
            this[1].verifyPointer(pointer2, position2_1, Finger)

            t += eventPeriodMillis
            this[2].verifyEvent(2, ACTION_MOVE, 0, t, Touchscreen)
            this[2].verifyPointer(pointer1, position1_2, Finger)
            this[2].verifyPointer(pointer2, position2_1, Finger)

            t += eventPeriodMillis
            this[3].verifyEvent(2, ACTION_MOVE, 0, t, Touchscreen)
            this[3].verifyPointer(pointer1, position1_2, Finger)
            this[3].verifyPointer(pointer2, position2_2, Finger)
        }
    }

    @Test
    fun twoPointers_downMoveDownMove() {
        // 2 fingers, 1st finger moves before 2nd finger goes down and moves
        subject.generateTouchDownAndCheck(pointer1, position1_1)
        subject.updateTouchPointerAndCheck(pointer1, position1_2)
        subject.advanceEventTime()
        subject.enqueueTouchMove()
        subject.generateTouchDownAndCheck(pointer2, position2_1)
        subject.updateTouchPointerAndCheck(pointer2, position2_2)
        subject.advanceEventTime()
        subject.enqueueTouchMove()
        subject.sendAllSynchronous()

        recorder.assertHasValidEventTimes()
        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(4)

            this[0].verifyEvent(1, ACTION_DOWN, 0, t, Touchscreen) // pointer1
            this[0].verifyPointer(pointer1, position1_1, Finger)

            t += eventPeriodMillis
            this[1].verifyEvent(1, ACTION_MOVE, 0, t, Touchscreen)
            this[1].verifyPointer(pointer1, position1_2, Finger)

            this[2].verifyEvent(2, ACTION_POINTER_DOWN, 1, t, Touchscreen) // pointer2
            this[2].verifyPointer(pointer1, position1_2, Finger)
            this[2].verifyPointer(pointer2, position2_1, Finger)

            t += eventPeriodMillis
            this[3].verifyEvent(2, ACTION_MOVE, 0, t, Touchscreen)
            this[3].verifyPointer(pointer1, position1_2, Finger)
            this[3].verifyPointer(pointer2, position2_2, Finger)
        }
    }

    @Test
    fun updateTouchPointer_oneMovePerPointer() {
        // 2 fingers, use [updateTouchPointer] and [enqueueTouchMove]
        subject.generateTouchDownAndCheck(pointer1, position1_1)
        subject.generateTouchDownAndCheck(pointer2, position2_1)
        subject.updateTouchPointerAndCheck(pointer1, position1_2)
        subject.updateTouchPointerAndCheck(pointer2, position2_2)
        subject.advanceEventTime()
        subject.enqueueTouchMove()
        subject.sendAllSynchronous()

        recorder.assertHasValidEventTimes()
        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(3)

            this[0].verifyEvent(1, ACTION_DOWN, 0, t, Touchscreen) // pointer1
            this[0].verifyPointer(pointer1, position1_1, Finger)

            this[1].verifyEvent(2, ACTION_POINTER_DOWN, 1, t, Touchscreen) // pointer2
            this[1].verifyPointer(pointer1, position1_1, Finger)
            this[1].verifyPointer(pointer2, position2_1, Finger)

            t += eventPeriodMillis
            this[2].verifyEvent(2, ACTION_MOVE, 0, t, Touchscreen)
            this[2].verifyPointer(pointer1, position1_2, Finger)
            this[2].verifyPointer(pointer2, position2_2, Finger)
        }
    }

    @Test
    fun updateTouchPointer_multipleMovesPerPointer() {
        // 2 fingers, do several [updateTouchPointer]s and then [enqueueTouchMove]
        subject.generateTouchDownAndCheck(pointer1, position1_1)
        subject.generateTouchDownAndCheck(pointer2, position2_1)
        subject.updateTouchPointerAndCheck(pointer1, position1_2)
        subject.updateTouchPointerAndCheck(pointer1, position1_3)
        subject.advanceEventTime()
        subject.enqueueTouchMove()
        subject.sendAllSynchronous()

        recorder.assertHasValidEventTimes()
        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(3)

            this[0].verifyEvent(1, ACTION_DOWN, 0, t, Touchscreen) // pointer1
            this[0].verifyPointer(pointer1, position1_1, Finger)

            this[1].verifyEvent(2, ACTION_POINTER_DOWN, 1, t, Touchscreen) // pointer2
            this[1].verifyPointer(pointer1, position1_1, Finger)
            this[1].verifyPointer(pointer2, position2_1, Finger)

            t += eventPeriodMillis
            this[2].verifyEvent(2, ACTION_MOVE, 0, t, Touchscreen)
            this[2].verifyPointer(pointer1, position1_3, Finger)
            this[2].verifyPointer(pointer2, position2_1, Finger)
        }
    }

    @Test
    fun enqueueTouchMove_withoutUpdateTouchPointer() {
        // 2 fingers, do [enqueueTouchMove] without [updateTouchPointer]
        subject.generateTouchDownAndCheck(pointer1, position1_1)
        subject.generateTouchDownAndCheck(pointer2, position2_1)
        subject.advanceEventTime()
        subject.enqueueTouchMove()
        subject.sendAllSynchronous()

        recorder.assertHasValidEventTimes()
        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(3)

            this[0].verifyEvent(1, ACTION_DOWN, 0, t, Touchscreen) // pointer1
            this[0].verifyPointer(pointer1, position1_1, Finger)

            this[1].verifyEvent(2, ACTION_POINTER_DOWN, 1, t, Touchscreen) // pointer2
            this[1].verifyPointer(pointer1, position1_1, Finger)
            this[1].verifyPointer(pointer2, position2_1, Finger)

            t += eventPeriodMillis
            this[2].verifyEvent(2, ACTION_MOVE, 0, t, Touchscreen)
            this[2].verifyPointer(pointer1, position1_1, Finger)
            this[2].verifyPointer(pointer2, position2_1, Finger)
        }
    }

    @Test
    fun enqueueTouchDown_flushesPointerMovement() {
        // Movement from [updateTouchPointer] that hasn't been sent will be sent when sending DOWN
        subject.generateTouchDownAndCheck(pointer1, position1_1)
        subject.generateTouchDownAndCheck(pointer2, position2_1)
        subject.updateTouchPointerAndCheck(pointer1, position1_2)
        subject.updateTouchPointerAndCheck(pointer1, position1_3)
        subject.advanceEventTime()
        subject.generateTouchDownAndCheck(pointer3, position3_1)
        subject.sendAllSynchronous()

        recorder.assertHasValidEventTimes()
        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(4)

            this[0].verifyEvent(1, ACTION_DOWN, 0, t, Touchscreen) // pointer1
            this[0].verifyPointer(pointer1, position1_1, Finger)

            this[1].verifyEvent(2, ACTION_POINTER_DOWN, 1, t, Touchscreen) // pointer2
            this[1].verifyPointer(pointer1, position1_1, Finger)
            this[1].verifyPointer(pointer2, position2_1, Finger)

            t += eventPeriodMillis
            this[2].verifyEvent(2, ACTION_MOVE, 0, t, Touchscreen)
            this[2].verifyPointer(pointer1, position1_3, Finger)
            this[2].verifyPointer(pointer2, position2_1, Finger)

            this[3].verifyEvent(3, ACTION_POINTER_DOWN, 2, t, Touchscreen) // pointer2
            this[3].verifyPointer(pointer1, position1_3, Finger)
            this[3].verifyPointer(pointer2, position2_1, Finger)
            this[3].verifyPointer(pointer3, position3_1, Finger)
        }
    }

    @Test
    fun enqueueTouchUp_flushesPointerMovement() {
        // Movement from [updateTouchPointer] that hasn't been sent will be sent when sending UP
        subject.generateTouchDownAndCheck(pointer1, position1_1)
        subject.generateTouchDownAndCheck(pointer2, position2_1)
        subject.updateTouchPointerAndCheck(pointer1, position1_2)
        subject.updateTouchPointerAndCheck(pointer1, position1_3)
        subject.advanceEventTime()
        subject.generateTouchUpAndCheck(pointer1)
        subject.sendAllSynchronous()

        recorder.assertHasValidEventTimes()
        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(3)

            this[0].verifyEvent(1, ACTION_DOWN, 0, t, Touchscreen) // pointer1
            this[0].verifyPointer(pointer1, position1_1, Finger)

            this[1].verifyEvent(2, ACTION_POINTER_DOWN, 1, t, Touchscreen) // pointer2
            this[1].verifyPointer(pointer1, position1_1, Finger)
            this[1].verifyPointer(pointer2, position2_1, Finger)

            t += eventPeriodMillis
            this[2].verifyEvent(2, ACTION_POINTER_UP, 0, t, Touchscreen) // pointer1
            this[2].verifyPointer(pointer1, position1_3, Finger)
            this[2].verifyPointer(pointer2, position2_1, Finger)
        }
    }

    @Test
    fun enqueueTouchCancel_doesNotFlushPointerMovement() {
        // 2 fingers, both with pending movement.
        // CANCEL doesn't force a MOVE, but _does_ reflect the latest positions
        subject.generateTouchDownAndCheck(pointer1, position1_1)
        subject.generateTouchDownAndCheck(pointer2, position2_1)
        subject.updateTouchPointerAndCheck(pointer1, position1_2)
        subject.updateTouchPointerAndCheck(pointer2, position2_2)
        subject.advanceEventTime()
        subject.generateCancelAndCheckPointers()
        subject.sendAllSynchronous()

        recorder.assertHasValidEventTimes()
        recorder.events.apply {
            var t = 0L
            assertThat(this).hasSize(3)
            this[0].verifyEvent(1, ACTION_DOWN, 0, t, Touchscreen) // pointer1
            this[0].verifyPointer(pointer1, position1_1, Finger)

            this[1].verifyEvent(2, ACTION_POINTER_DOWN, 1, t, Touchscreen) // pointer2
            this[1].verifyPointer(pointer1, position1_1, Finger)
            this[1].verifyPointer(pointer2, position2_1, Finger)

            t += eventPeriodMillis
            this[2].verifyEvent(2, ACTION_CANCEL, 0, t, Touchscreen)
            this[2].verifyPointer(pointer1, position1_2, Finger)
            this[2].verifyPointer(pointer2, position2_2, Finger)
        }
    }

    @Test
    fun updateTouchPointer_withoutDown() {
        expectError<IllegalStateException> {
            subject.updateTouchPointer(pointer1, position1_1)
        }
    }

    @Test
    fun updateTouchPointer_wrongPointerId() {
        subject.enqueueTouchDown(pointer1, position1_1)
        expectError<IllegalArgumentException> {
            subject.updateTouchPointer(pointer2, position1_2)
        }
    }

    @Test
    fun updateTouchPointer_afterUp() {
        subject.enqueueTouchDown(pointer1, position1_1)
        subject.enqueueTouchUp(pointer1)
        expectError<IllegalStateException> {
            subject.updateTouchPointer(pointer1, position1_2)
        }
    }

    @Test
    fun updateTouchPointer_afterCancel() {
        subject.enqueueTouchDown(pointer1, position1_1)
        subject.enqueueTouchCancel()
        expectError<IllegalStateException> {
            subject.updateTouchPointer(pointer1, position1_2)
        }
    }

    @Test
    fun enqueueTouchMove_withoutDown() {
        expectError<IllegalStateException> {
            subject.enqueueTouchMove()
        }
    }

    @Test
    fun enqueueTouchMove_afterUp() {
        subject.enqueueTouchDown(pointer1, position1_1)
        subject.enqueueTouchUp(pointer1)
        expectError<IllegalStateException> {
            subject.enqueueTouchMove()
        }
    }

    @Test
    fun enqueueTouchMove_afterCancel() {
        subject.enqueueTouchDown(pointer1, position1_1)
        subject.enqueueTouchCancel()
        expectError<IllegalStateException> {
            subject.enqueueTouchMove()
        }
    }
}

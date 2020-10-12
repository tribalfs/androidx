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

package androidx.compose.ui.gesture

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.gesture.customevents.DelayUpEvent
import androidx.compose.ui.gesture.customevents.DelayUpMessage
import androidx.compose.ui.input.pointer.CustomEventDispatcher
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.consumeDownChange
import androidx.compose.ui.input.pointer.down
import androidx.compose.ui.input.pointer.invokeOverAllPasses
import androidx.compose.ui.input.pointer.moveTo
import androidx.compose.ui.input.pointer.up
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.milliseconds
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyNoMoreInteractions
import kotlinx.coroutines.CoroutineScope
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.TimeUnit

// TODO(shepshapard): Add more tests for:
//  1. More complex multi-pointer scenarios testing how consumption affects firing events
//  2. More complex multi-pointer scenarios testing how pointers effect consumption

@kotlinx.coroutines.ObsoleteCoroutinesApi
@RunWith(JUnit4::class)
class DoubleTapGestureFilterTest {

    private val DoubleTapTimeoutMillis = 100.milliseconds

    @Suppress("DEPRECATION")
    private val testContext = kotlinx.coroutines.test.TestCoroutineContext()
    private val onDoubleTap: (Offset) -> Unit = mock()
    private val customEventDispatcher: CustomEventDispatcher = mock()
    private lateinit var filter: DoubleTapGestureFilter

    @Before
    fun setup() {
        filter = DoubleTapGestureFilter(CoroutineScope(testContext))
        filter.onDoubleTap = onDoubleTap
        filter.doubleTapTimeout = DoubleTapTimeoutMillis
        filter.onInit(customEventDispatcher)
    }

    // Tests that verify conditions under which onDoubleTap will not be called.

    @Test
    fun onPointerEvent_down_onDoubleTapNotCalled() {
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down(0, 0.milliseconds)))
        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downUp_onDoubleTapNotCalled() {
        val down = down(0, 0.milliseconds)
        val up = down.up(duration = 1.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downUpDownWithinTimeout_onDoubleTapNotCalled() {
        val down1 = down(1, 0.milliseconds)
        val up = down1.up(duration = 1.milliseconds)
        val down2 = down(2, 100.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downUpDownOutsideTimeout_onDoubleTapNotCalled() {
        val down1 = down(1, 0.milliseconds)
        val up = down1.up(duration = 1.milliseconds)
        val down2 = down(2, 101.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        testContext.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downUpDownOutsideTimeoutUp_onDoubleTapNotCalled() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2 = down(2, 101.milliseconds)
        val up2 = down2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downMoveConsumedUpDownInsideTimeoutUp_onDoubleTapNotCalled() {
        val down1 = down(1, 0.milliseconds)
        val moveConsumed = down1.moveTo(1.milliseconds, x = 1f).consume(dx = 1f)
        val up1 = moveConsumed.up(duration = 2.milliseconds)
        val down2 = down(2, 101.milliseconds)
        val up2 = down2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(moveConsumed))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downUpDownInsideTimeoutMoveConsumedUp_onDoubleTapNotCalled() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2 = down(2, 100.milliseconds)
        val moveConsumed = down2.moveTo(101.milliseconds, x = 1f).consume(dx = 1f)
        val up2 = moveConsumed.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(moveConsumed))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_2Down1MoveConsumedUpDownInsideTimeoutUp_onDoubleTapNotCalled() {
        val down1A = down(0, 0.milliseconds)
        val down1B = down(1, 0.milliseconds)
        val moveConsumed1A = down1A.moveTo(1.milliseconds, x = 1f).consume(dx = 1f)
        val move1B = down1B.moveTo(1.milliseconds)
        val up1A = moveConsumed1A.up(duration = 2.milliseconds)
        val up1B = move1B.up(duration = 2.milliseconds)
        val down2 = down(2, 101.milliseconds)
        val up2 = down2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1A, down1B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(moveConsumed1A, move1B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1A, up1B))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downUp2DownInsideTimeout1MoveConsumedUp_onDoubleTapNotCalled() {
        val down1 = down(1, 0.milliseconds)
        val up2 = down1.up(duration = 1.milliseconds)
        val down2A = down(0, 100.milliseconds)
        val down2B = down(1, 100.milliseconds)
        val moveConsumed2A = down2A.moveTo(101.milliseconds, x = 1f).consume(dx = 1f)
        val move2B = down2B.moveTo(101.milliseconds)
        val up2A = moveConsumed2A.up(duration = 102.milliseconds)
        val up2B = move2B.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2A, down2B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(moveConsumed2A, move2B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2A, up2B))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downConsumedUpDownWithinTimeoutUp_onDoubleTapNotCalled() {
        val down1 = down(1, 0.milliseconds).apply { consumeDownChange() }
        val up1 = down1.up(duration = 1.milliseconds)
        val down2 = down(0, 100.milliseconds)
        val up2 = down2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downUpConsumedDownWithinTimeoutUp_onDoubleTapNotCalled() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds).apply { consumeDownChange() }
        val down2 = down(0, 100.milliseconds)
        val up2 = down2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downUpDownConsumedWithinTimeoutUp_onDoubleTapNotCalled() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2 = down(0, 100.milliseconds).apply { consumeDownChange() }
        val up2 = down2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downUpDownWithinTimeoutUpConsumed_onDoubleTapNotCalled() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2 = down(0, 100.milliseconds)
        val up2 = down2.up(duration = 102.milliseconds).apply { consumeDownChange() }

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_2down1Up1DownWithinTimeout1Up_onDoubleTapNotCalled() {
        val down1A = down(0, 0.milliseconds)
        val down1B = down(1, 0.milliseconds)
        val move1A1 = down1A.moveTo(2.milliseconds)
        val up2B = down1B.up(duration = 2.milliseconds)
        val move1A2 = move1A1.moveTo(101.milliseconds)
        val down2 = down(1, 101.milliseconds)
        val move1A3 = move1A2.moveTo(102.milliseconds)
        val up2 = down2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1A, down1B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move1A1, up2B))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move1A2, down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move1A3, up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_1down1Up2DownWithinTimeout1Up_onDoubleTapNotCalled() {
        val down1 = down(0, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2A = down(0, 100.milliseconds)
        val down2B = down(1, 100.milliseconds)
        val move2A = down2A.moveTo(101.milliseconds)
        val up2B = down2B.up(duration = 101.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2A, down2B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move2A, up2B))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downMoveOutOfBoundsUpDownUp_onDoubleTapNotCalled() {
        val down = down(0, 0.milliseconds, 0f, 0f)
        val move = down.moveTo(1.milliseconds, 1f, 1f)
        val up = move.up(duration = 12.milliseconds)
        val down2 = down(0, 13.milliseconds, 0f, 0f)
        val up2 = down2.up(duration = 14.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2), IntSize(1, 1))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onPointerEvent_downUpDownMoveOutOfBoundsUp_onDoubleTapNotCalled() {
        val down = down(0, 0.milliseconds, 0f, 0f)
        val up = down.up(duration = 1.milliseconds)
        val down2 = down(0, 2.milliseconds, 0f, 0f)
        val move2 = down2.moveTo(3.milliseconds, 1f, 1f)
        val up2 = down2.up(duration = 4.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move2), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2), IntSize(1, 1))

        verify(onDoubleTap, never()).invoke(any())
    }

    // Tests that verify conditions under which onDoubleTap will be called.

    @Test
    fun onPointerEvent_downUpDownInsideTimeoutUp_onDoubleTapCalled() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2 = down(2, 100.milliseconds)
        val up2 = down2.up(duration = 101.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(any())
    }

    @Test
    fun onPointerEvent_downMoveUpDownInsideTimeoutUp_onDoubleTapCalled() {
        val down1 = down(1, 0.milliseconds)
        val move = down1.moveTo(1.milliseconds, x = 1f)
        val up1 = move.up(duration = 2.milliseconds)
        val down2 = down(2, 101.milliseconds)
        val up2 = down2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(any())
    }

    @Test
    fun onPointerEvent_downUpDownInsideTimeoutMoveUp_onDoubleTapCalled() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2 = down(2, 10.milliseconds)
        val move = down2.moveTo(101.milliseconds, x = 1f)
        val up2 = move.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(any())
    }

    @Test
    fun onPointerEvent_2Down1MoveUpDownInsideTimeoutUp_onDoubleTapCalled() {
        val down1A = down(0, 0.milliseconds)
        val down1B = down(1, 0.milliseconds)
        val move1A = down1A.moveTo(1.milliseconds, x = 1f)
        val move1B = down1B.moveTo(1.milliseconds)
        val up1A = move1A.up(duration = 2.milliseconds)
        val up1B = move1B.up(duration = 2.milliseconds)
        val down2 = down(2, 101.milliseconds)
        val up2 = down2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1A, down1B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move1A, move1B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1A, up1B))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(any())
    }

    @Test
    fun onPointerEvent_downUp2DownInsideTimeout1MoveUp_onDoubleTapCalled() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2A = down(0, 100.milliseconds)
        val down2B = down(1, 100.milliseconds)
        val move2A = down2A.moveTo(101.milliseconds, x = 1f)
        val move2B = down2B.moveTo(101.milliseconds)
        val up2A = move2A.up(duration = 102.milliseconds)
        val up2B = move2B.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2A, down2B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move2A, move2B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2A, up2B))

        verify(onDoubleTap).invoke(any())
    }

    @Test
    fun onPointerEvent_downMoveOutOfBoundsUpDownUpDownUp_onDoubleTapCalledOnce() {
        val down = down(0, 0.milliseconds, 0f, 0f)
        val move = down.moveTo(1.milliseconds, 1f, 1f)
        val up = move.up(duration = 2.milliseconds)
        val down2 = down(0, 3.milliseconds, 0f, 0f)
        val up2 = down2.up(duration = 4.milliseconds)
        val down3 = down(0, 5.milliseconds, 0f, 0f)
        val up3 = down3.up(6.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up), IntSize(1, 1))

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down3), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up3), IntSize(1, 1))

        verify(onDoubleTap).invoke(any())
    }

    @Test
    fun onPointerEvent_downUpDownMoveOutOfBoundsUpDownUpDownUp_onDoubleTapCalledOnce() {
        val down = down(0, 0.milliseconds, 0f, 0f)
        val up = down.up(duration = 2.milliseconds)
        val down2 = down(0, 3.milliseconds, 0f, 0f)
        val move2 = down2.moveTo(1.milliseconds, 1f, 1f)
        val up2 = move2.up(duration = 4.milliseconds)
        val down3 = down(0, 5.milliseconds, 0f, 0f)
        val up3 = down3.up(6.milliseconds)
        val down4 = down(0, 7.milliseconds, 0f, 0f)
        val up4 = down4.up(8.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move2), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2), IntSize(1, 1))

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down3), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up3), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down4), IntSize(1, 1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up4), IntSize(1, 1))

        verify(onDoubleTap).invoke(any())
    }

    // This test verifies that the 2nd down causes the double tap time out timer to stop such that
    // the second wait doesn't cause the gesture detector to reset to an idle state.
    @Test
    fun onPointerEvent_downUpWaitHalfDownWaitHalfUp_onDoubleTapCalled() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val wait1 = 50L
        val down2 = down(2, 51.milliseconds)
        val wait2 = 50L
        val up2 = down2.up(duration = 101.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(wait1, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        testContext.advanceTimeBy(wait2, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(any())
    }

    // Tests that verify correctness of PxPosition value passed to onDoubleTap

    @Test
    fun onPointerEvent_downUpDownUpAllAtOrigin_onDoubleTapCalledWithOrigin() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2 = down(2, 100.milliseconds)
        val up2 = down2.up(duration = 101.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(Offset.Zero)
    }

    @Test
    fun onPointerEvent_downUpDownMoveUp_onDoubleTapCalledWithFinalMovePosition() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2 = down(2, 100.milliseconds)
        val move2 = down2.moveTo(101.milliseconds, 3f, 5f)
        val up2 = move2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(Offset(3f, 5f))
    }

    @Test
    fun onPointerEvent_downUp2Down2Move1UpThen1Up_onDoubleTapCalledWithFinalFingerPosition() {
        val down1 = down(1, 0.milliseconds)
        val up1 = down1.up(duration = 1.milliseconds)
        val down2A = down(0, 100.milliseconds)
        val down2B = down(1, 100.milliseconds)
        val move2A = down2A.moveTo(101.milliseconds, 3f, 5f)
        val move2B1 = down2B.moveTo(101.milliseconds, 7f, 11f)
        val up2A = move2A.up(duration = 102.milliseconds)
        val move2B2 = move2B1.moveTo(102.milliseconds, x = 7f, y = 11f)
        val up2B = move2B2.up(duration = 103.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2A, down2B))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(move2A, move2B1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2A, move2B2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2B))

        verify(onDoubleTap).invoke(Offset(7f, 11f))
    }

    // Tests that verify correct consumption behavior

    @Test
    fun onPointerEvent_down_downNotConsumed() {
        val down = down(0, 0.milliseconds)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down))
        assertThat(down.consumed.downChange).isFalse()
    }

    @Test
    fun onPointerEvent_downUp_upNotConsumed() {
        val down = down(0, 0.milliseconds)
        val up = down.up(1.milliseconds)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        assertThat(up.consumed.downChange).isFalse()
    }

    @Test
    fun onPointerEvent_downUpDownInsideTimeout_lastDownNotConsumed() {
        val down = down(0, 0.milliseconds)
        val up = down.up(1.milliseconds)
        val down2 = down(2, 100.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))

        assertThat(down2.consumed.downChange).isFalse()
    }

    @Test
    fun onPointerEvent_downUpDownOutsideTimeoutUp_lastUpNotConsumed() {
        val down = down(0, 0.milliseconds)
        val up = down.up(1.milliseconds)
        val down2 = down(2, 101.milliseconds)
        val up2 = down2.up(duration = 102.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        testContext.advanceTimeBy(100, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        assertThat(up2.consumed.downChange).isFalse()
    }

    @Test
    fun onPointerEvent_downUpDownInsideTimeoutUp_lastUpConsumed() {
        val down = down(0, 0.milliseconds)
        val up = down.up(1.milliseconds)
        val down2 = down(2, 100.milliseconds)
        val up2 = down2.up(duration = 101.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        assertThat(up2.consumed.downChange).isTrue()
    }

    // Tests that verify correct cancellation behavior

    @Test
    fun onCancel_downUpCancelWaitDownUp_onDoubleTapNotCalled() {
        val down1 = down(0, duration = 100.milliseconds)
        val up1 = down1.up(duration = 101.milliseconds)
        val down2 = down(1, duration = 200.milliseconds)
        val up2 = down2.up(duration = 201.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        filter.onCancel()
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onCancel_downUpWaitCancelDownUp_onDoubleTapNotCalled() {
        val down1 = down(1, 100.milliseconds)
        val up1 = down1.up(duration = 101.milliseconds)
        val down2 = down(2, 200.milliseconds)
        val up2 = down2.up(duration = 201.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter.onCancel()
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap, never()).invoke(any())
    }

    @Test
    fun onCancel_cancelDownUpDownUp_onDoubleTapCalledOnce() {
        val down1 = down(0, duration = 100.milliseconds)
        val up1 = down1.up(duration = 101.milliseconds)
        val down2 = down(1, duration = 200.milliseconds)
        val up2 = down2.up(duration = 201.milliseconds)

        filter.onCancel()
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(any())
    }

    @Test
    fun onCancel_downCancelDownUpDownUp_onDoubleTapCalledOnce() {
        val down0 = down(0, duration = 99.milliseconds)
        val down1 = down(1, duration = 100.milliseconds)
        val up1 = down1.up(duration = 101.milliseconds)
        val down2 = down(2, duration = 200.milliseconds)
        val up2 = down2.up(duration = 201.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down0))
        filter.onCancel()
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(any())
    }

    @Test
    fun onCancel_downUpCancelDownUpDownUp_onDoubleTapCalledOnce() {
        val down0 = down(0, duration = 98.milliseconds)
        val up0 = down0.up(duration = 99.milliseconds)
        val down1 = down(1, duration = 100.milliseconds)
        val up1 = down1.up(duration = 101.milliseconds)
        val down2 = down(2, duration = 200.milliseconds)
        val up2 = down2.up(duration = 201.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down0))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up0))
        filter.onCancel()
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(any())
    }

    @Test
    fun onCancel_downUpDownCancelDownUpDownUp_onDoubleTapCalledOnce() {
        val down0 = down(0, duration = 97.milliseconds)
        val up0 = down0.up(duration = 98.milliseconds)
        val down1 = down(1, 99.milliseconds)
        val down2 = down(2, 100.milliseconds)
        val up2 = down2.up(duration = 101.milliseconds)
        val down3 = down(3, 200.milliseconds)
        val up3 = down3.up(duration = 201.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down0))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up0))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter.onCancel()
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down3))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up3))

        verify(onDoubleTap).invoke(any())
    }

    @Test
    fun onCancel_downUpDownUpCancelDownUpDownUp_onDoubleTapCalledTwice() {
        val down0 = down(0, 0.milliseconds)
        val up0 = down0.up(duration = 1.milliseconds)
        val down1 = down(1, 100.milliseconds)
        val up1 = down1.up(duration = 101.milliseconds)

        val down2 = down(2, 200.milliseconds)
        val up2 = down2.up(duration = 201.milliseconds)
        val down3 = down(3, 300.milliseconds)
        val up3 = down3.up(duration = 301.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down0))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up0))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        filter.onCancel()
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))
        testContext.advanceTimeBy(99, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down3))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up3))

        verify(onDoubleTap, times(2)).invoke(any())
    }

    // This test verifies that the cancel event causes the double tap timer to be reset.  If it does
    // not cause it to be reset, then when delay1 is dispatched, the DoubleTapGestureDetector will
    // be forced back into the IDLE state, preventing the double tap that follows cancel from
    // firing.
    @Test
    fun onCancel_downUpWaitCancelDownWaitUpDownUp_onDoubleTapCalledOnce() {
        val down0 = down(0, 0.milliseconds)
        val up0 = down0.up(duration = 1.milliseconds)
        val delay0 = 50L
        // Cancel happens here
        val down1 = down(1, 51.milliseconds)
        val delay1 = 50L
        val up1 = down1.up(duration = 101.milliseconds)
        val down2 = down(2, 102.milliseconds)
        val up2 = down2.up(duration = 103.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down0))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up0))
        testContext.advanceTimeBy(delay0, TimeUnit.MILLISECONDS)
        filter.onCancel()
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        testContext.advanceTimeBy(delay1, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(onDoubleTap).invoke(any())
    }

    // Verifies correct behavior around dispatching custom messages with simple cases

    @Test
    fun onPointerEvent_downUp_delayUpToCorrectPointersAndRetained() {
        val down1 = down(123, 0.milliseconds)
        val up = down1.up(duration = 1.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))

        verify(customEventDispatcher)
            .dispatchCustomEvent(DelayUpEvent(DelayUpMessage.DelayUp, setOf(PointerId(123))))
        verify(customEventDispatcher)
            .retainHitPaths(setOf(PointerId(123)))
        verifyNoMoreInteractions(customEventDispatcher)
    }

    @Test
    fun onPointerEvent_downUpDownBeforeTimeOut_delayUpConsumedToCorrectPointersAndReleased() {
        val down1 = down(123, 0.milliseconds)
        val up = down1.up(duration = 1.milliseconds)
        val delay1 = 1L
        val down2 = down(456, 2.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        reset(customEventDispatcher)
        testContext.advanceTimeBy(delay1, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))

        verify(customEventDispatcher).dispatchCustomEvent(
            DelayUpEvent(DelayUpMessage.DelayedUpConsumed, setOf(PointerId(123)))
        )
        verify(customEventDispatcher).releaseHitPaths(
            setOf(PointerId(123))
        )
        verifyNoMoreInteractions(customEventDispatcher)
    }

    @Test
    fun onPointerEvent_downUpTimeOut_delayUpNotConsumedToCorrectPointersAndReleased() {
        val down1 = down(123, 0.milliseconds)
        val up = down1.up(duration = 1.milliseconds)
        val delay1 = 1000L

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        reset(customEventDispatcher)
        testContext.advanceTimeBy(delay1, TimeUnit.MILLISECONDS)

        verify(customEventDispatcher).dispatchCustomEvent(
            DelayUpEvent(DelayUpMessage.DelayedUpNotConsumed, setOf(PointerId(123)))
        )
        verify(customEventDispatcher).releaseHitPaths(
            setOf(PointerId(123))
        )
        verifyNoMoreInteractions(customEventDispatcher)
    }

    // Verifies correct behavior around dispatching custom messages in relation to other factors

    @Test
    fun onPointerEvent_downUpConsumed_noCustomMessageDispatched() {
        val down1 = down(123, 0.milliseconds)
        val upConsumed = down1.up(duration = 1.milliseconds).apply { consumeDownChange() }

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(upConsumed))

        verifyNoMoreInteractions(customEventDispatcher)
    }

    @Test
    fun onPointerEvent_downUpConsumedDownBeforeTimeout_noCustomMessageDispatched() {
        val down1 = down(123, 0.milliseconds)
        val upConsumed = down1.up(duration = 1.milliseconds).apply { consumeDownChange() }
        val delay1 = 1L
        val down2 = down(456, 2.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(upConsumed))
        testContext.advanceTimeBy(delay1, TimeUnit.MILLISECONDS)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))

        verifyNoMoreInteractions(customEventDispatcher)
    }

    @Test
    fun onPointerEvent_downUpConsumedTimeout_noCustomMessageDispatched() {
        val down1 = down(123, 0.milliseconds)
        val upConsumed = down1.up(duration = 1.milliseconds).apply { consumeDownChange() }
        val delay1 = 1000L

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(upConsumed))
        testContext.advanceTimeBy(delay1, TimeUnit.MILLISECONDS)

        verifyNoMoreInteractions(customEventDispatcher)
    }

    @Test
    fun onCancel_downUpCancelTimeOut_delayUpConsumedToCorrectPointersAndReleased() {
        val down1 = down(123, 0.milliseconds)
        val up = down1.up(duration = 1.milliseconds)
        val delay1 = 1000L

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        reset(customEventDispatcher)
        filter.onCancel()
        testContext.advanceTimeBy(delay1, TimeUnit.MILLISECONDS)

        verify(customEventDispatcher).dispatchCustomEvent(
            DelayUpEvent(DelayUpMessage.DelayedUpConsumed, setOf(PointerId(123)))
        )
        verify(customEventDispatcher).releaseHitPaths(
            setOf(PointerId(123))
        )
        verifyNoMoreInteractions(customEventDispatcher)
    }

    @Test
    fun onPointerEvent_downUpCancelDownBeforeTimeOut_noMessageDispatched() {
        val down1 = down(123, 0.milliseconds)
        val up = down1.up(duration = 1.milliseconds)
        val delay1 = 1L
        val down2 = down(456, 2.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        testContext.advanceTimeBy(delay1, TimeUnit.MILLISECONDS)
        filter.onCancel()
        reset(customEventDispatcher)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))

        verifyNoMoreInteractions(customEventDispatcher)
    }

    @Test
    fun onPointerEvent_downUpCancelDownUp_delayUpToCorrectPointersAndRetained() {
        val down1 = down(123, 0.milliseconds)
        val up = down1.up(duration = 1.milliseconds)
        val delay1 = 1L
        val down2 = down(456, 2.milliseconds)
        val up2 = down2.up(3.milliseconds)

        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down1))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up))
        testContext.advanceTimeBy(delay1, TimeUnit.MILLISECONDS)
        filter.onCancel()
        reset(customEventDispatcher)
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(down2))
        filter::onPointerEvent.invokeOverAllPasses(pointerEventOf(up2))

        verify(customEventDispatcher)
            .dispatchCustomEvent(DelayUpEvent(DelayUpMessage.DelayUp, setOf(PointerId(456))))
        verify(customEventDispatcher)
            .retainHitPaths(setOf(PointerId(456)))
        verifyNoMoreInteractions(customEventDispatcher)
    }
}

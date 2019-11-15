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

import androidx.ui.core.IntPxSize
import androidx.ui.core.PointerEventPass
import androidx.ui.core.consumeDownChange
import androidx.ui.core.ipx
import androidx.ui.core.milliseconds
import androidx.ui.testutils.consume
import androidx.ui.testutils.down
import androidx.ui.testutils.invokeOverAllPasses
import androidx.ui.testutils.invokeOverPasses
import androidx.ui.testutils.moveTo
import androidx.ui.testutils.up
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.verify
import org.hamcrest.CoreMatchers.`is`
import org.junit.Assert.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PressReleasedGestureDetectorTest {

    private lateinit var recognizer: PressReleaseGestureRecognizer

    @Before
    fun setup() {
        recognizer = PressReleaseGestureRecognizer()
        recognizer.onRelease = mock()
    }

    @Test
    fun pointerInputHandler_down_onReleaseNotCalled() {
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(down()))
        verify(recognizer.onRelease!!, never()).invoke()
    }

    @Test
    fun pointerInputHandler_downConsumedUp_onReleaseNotCalled() {
        var pointer = down().consumeDownChange()
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))
        pointer = pointer.up(100.milliseconds)
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))

        verify(recognizer.onRelease!!, never()).invoke()
    }

    @Test
    fun pointerInputHandler_downMoveConsumedUp_onReleaseNotCalled() {
        var pointer = down()
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))
        pointer = pointer.moveTo(100.milliseconds, 5f).consume(5f)
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))
        pointer = pointer.up(200.milliseconds)
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))

        verify(recognizer.onRelease!!, never()).invoke()
    }

    @Test
    fun pointerInputHandler_downUpConsumed_onReleaseNotCalled() {
        var pointer = down().consumeDownChange()
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))
        pointer = pointer.up(100.milliseconds).consumeDownChange()
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))

        verify(recognizer.onRelease!!, never()).invoke()
    }

    @Test
    fun pointerInputHandler_downMoveOutsideBoundsNegativeXUp_onReleaseNotCalled() {
        var pointer = down(x = 0f, y = 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.moveTo(50.milliseconds, -1f, 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.up(100.milliseconds)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))

        verify(recognizer.onRelease!!, never()).invoke()
    }

    @Test
    fun pointerInputHandler_downMoveOutsideBoundsPositiveXUp_onReleaseNotCalled() {
        var pointer = down(x = 0f, y = 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.moveTo(50.milliseconds, 1f, 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.up(100.milliseconds)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))

        verify(recognizer.onRelease!!, never()).invoke()
    }

    @Test
    fun pointerInputHandler_downMoveOutsideBoundsNegativeYUp_onReleaseNotCalled() {
        var pointer = down(x = 0f, y = 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.moveTo(50.milliseconds, 0f, -1f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.up(100.milliseconds)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))

        verify(recognizer.onRelease!!, never()).invoke()
    }

    @Test
    fun pointerInputHandler_downMoveOutsideBoundsPositiveYUp_onReleaseNotCalled() {
        var pointer = down(x = 0f, y = 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.moveTo(50.milliseconds, 0f, 1f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.up(100.milliseconds)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))

        verify(recognizer.onRelease!!, never()).invoke()
    }

    @Test
    fun pointerInputHandler_downUp_onReleaseCalledOnce() {
        var pointer = down()
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))
        pointer = pointer.up(100.milliseconds)
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))

        verify(recognizer.onRelease!!).invoke()
    }

    @Test
    fun pointerInputHandler_downMoveUp_onReleaseCalledOnce() {
        var pointer = down()
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))
        pointer = pointer.moveTo(100.milliseconds, 5f)
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))
        pointer = pointer.up(200.milliseconds)
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))

        verify(recognizer.onRelease!!).invoke()
    }

    @Test
    fun pointerInputHandler_downMoveOutsideBoundsUpDownUp_onReleaseCalledOnce() {
        var pointer = down(x = 0f, y = 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.moveTo(50.milliseconds, 0f, 1f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.up(100.milliseconds)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = down(duration = 150.milliseconds, x = 0f, y = 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.up(200.milliseconds)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))

        verify(recognizer.onRelease!!).invoke()
    }

    @Test
    fun pointerInputHandler_consumeDownOnStartIsDefault_downChangeConsumed() {
        val pointerEventChange = recognizer.pointerInputHandler.invokeOverAllPasses(listOf(down()))
        assertThat(pointerEventChange.first().consumed.downChange, `is`(true))
    }

    @Test
    fun pointerInputHandler_consumeDownOnStartIsFalse_downChangeNotConsumed() {
        recognizer.consumeDownOnStart = false
        val pointerEventChange = recognizer.pointerInputHandler.invokeOverAllPasses(listOf(down()))
        assertThat(pointerEventChange.first().consumed.downChange, `is`(false))
    }

    @Test
    fun pointerInputHandler_upChangeConsumed() {
        var pointer = down()
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))
        pointer = pointer.up(100.milliseconds)
        val pointerEventChange = recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))
        assertThat(pointerEventChange.first().consumed.downChange, `is`(true))
    }

    @Test
    fun pointerInputHandler_downMoveOutsideBoundsNegativeXUp_upChangeNotConsumed() {
        var pointer = down(x = 0f, y = 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.moveTo(50.milliseconds, -1f, 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.up(100.milliseconds)
        val result =
            recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))

        assertThat(result.first().consumed.downChange, `is`(false))
    }

    @Test
    fun pointerInputHandler_downMoveOutsideBoundsPositiveXUp_upChangeNotConsumed() {
        var pointer = down(x = 0f, y = 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.moveTo(50.milliseconds, 1f, 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.up(100.milliseconds)
        val result =
            recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))

        assertThat(result.first().consumed.downChange, `is`(false))
    }

    @Test
    fun pointerInputHandler_downMoveOutsideBoundsNegativeYUp_upChangeNotConsumed() {
        var pointer = down(x = 0f, y = 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.moveTo(50.milliseconds, 0f, -1f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.up(100.milliseconds)
        val result =
            recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))

        assertThat(result.first().consumed.downChange, `is`(false))
    }

    @Test
    fun pointerInputHandler_downMoveOutsideBoundsPositiveYUp_upChangeNotConsumed() {
        var pointer = down(x = 0f, y = 0f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.moveTo(50.milliseconds, 0f, 1f)
        recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))
        pointer = pointer.up(100.milliseconds)

        val result =
            recognizer.pointerInputHandler.invokeOverAllPasses(pointer, IntPxSize(1.ipx, 1.ipx))

        assertThat(result.first().consumed.downChange, `is`(false))
    }

    @Test
    fun pointerInputHandler_downChangeConsumedDuringPostUp() {
        var pointerEventChange = listOf(down())
        pointerEventChange = recognizer.pointerInputHandler.invokeOverPasses(
            pointerEventChange,
            PointerEventPass.InitialDown,
            PointerEventPass.PreUp,
            PointerEventPass.PreDown
        )
        assertThat(pointerEventChange.first().consumed.downChange, `is`(false))

        pointerEventChange = recognizer.pointerInputHandler.invoke(
            pointerEventChange,
            PointerEventPass.PostUp,
            IntPxSize(0.ipx, 0.ipx)
        )
        assertThat(pointerEventChange.first().consumed.downChange, `is`(true))
    }

    @Test
    fun pointerInputHandler_upChangeConsumedDuringPostUp() {
        val pointer = down()
        recognizer.pointerInputHandler.invokeOverAllPasses(listOf(pointer))
        var pointerEventChange = listOf(pointer.up(100.milliseconds))
        pointerEventChange = recognizer.pointerInputHandler.invokeOverPasses(
            pointerEventChange,
            PointerEventPass.InitialDown,
            PointerEventPass.PreUp,
            PointerEventPass.PreDown
        )
        assertThat(pointerEventChange.first().consumed.downChange, `is`(false))

        pointerEventChange = recognizer.pointerInputHandler.invoke(
            pointerEventChange,
            PointerEventPass.PostUp,
            IntPxSize(0.ipx, 0.ipx)
        )
        assertThat(pointerEventChange.first().consumed.downChange, `is`(true))
    }
}
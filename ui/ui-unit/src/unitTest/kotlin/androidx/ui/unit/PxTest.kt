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

package androidx.ui.unit

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PxTest {

    @Test
    fun compareDimension2() {
        assertTrue(PxSquared(0f) < PxSquared(Float.MIN_VALUE))
        assertTrue(PxSquared(1f) < PxSquared(3f))
        assertTrue(PxSquared(1f) == PxSquared(1f))
        assertTrue(PxSquared(1f) > PxSquared(0f))
    }

    @Test
    fun compareDimension3() {
        assertTrue(PxCubed(0f) < PxCubed(Float.MIN_VALUE))
        assertTrue(PxCubed(1f) < PxCubed(3f))
        assertTrue(PxCubed(1f) == PxCubed(1f))
        assertTrue(PxCubed(1f) > PxCubed(0f))
    }

    @Test
    fun compareDimensionInverse() {
        assertTrue(PxInverse(0f) < PxInverse(Float.MIN_VALUE))
        assertTrue(PxInverse(1f) < PxInverse(3f))
        assertTrue(PxInverse(1f) == PxInverse(1f))
        assertTrue(PxInverse(1f) > PxInverse(0f))
    }

    @Test
    fun sizeCenter() {
        val size = PxSize(width = 10f, height = 20f)
        assertEquals(PxPosition(5f, 10f), size.center())
    }

    @Test
    fun positionDistance() {
        val position = PxPosition(3f, 4f)
        assertEquals(5f, position.getDistance())
    }

    @Test
    fun lerpPosition() {
        val a = PxPosition(3f, 10f)
        val b = PxPosition(5f, 8f)
        assertEquals(PxPosition(4f, 9f), lerp(a, b, 0.5f))
        assertEquals(PxPosition(3f, 10f), lerp(a, b, 0f))
        assertEquals(PxPosition(5f, 8f), lerp(a, b, 1f))
    }

    @Test
    fun positionMinus() {
        val a = PxPosition(3f, 10f)
        val b = PxPosition(5f, 8f)
        assertEquals(PxPosition(-2f, 2f), a - b)
        assertEquals(PxPosition(2f, -2f), b - a)
    }

    @Test
    fun positionPlus() {
        val a = PxPosition(3f, 10f)
        val b = PxPosition(5f, 8f)
        assertEquals(PxPosition(8f, 18f), a + b)
        assertEquals(PxPosition(8f, 18f), b + a)
    }

    @Test
    fun pxPositionMinusIntPxPosition() {
        val a = PxPosition(3f, 10f)
        val b = IntPxPosition(5.ipx, 8.ipx)
        assertEquals(PxPosition(-2f, 2f), a - b)
    }

    @Test
    fun pxPositionPlusIntPxPosition() {
        val a = PxPosition(3f, 10f)
        val b = IntPxPosition(5.ipx, 8.ipx)
        assertEquals(PxPosition(8f, 18f), a + b)
    }

    @Test
    fun boundsWidth() {
        val bounds = PxBounds(10f, 5f, 25f, 15f)
        assertEquals(15f, bounds.width)
    }

    @Test
    fun boundsHeight() {
        val bounds = PxBounds(10f, 5f, 25f, 15f)
        assertEquals(10f, bounds.height)
    }

    @Test
    fun toSize() {
        val size = PxSize(15f, 10f)
        val bounds = PxBounds(10f, 5f, 25f, 15f)
        assertEquals(size, bounds.toSize())
    }

    @Test
    fun toBounds() {
        val size = PxSize(15f, 10f)
        val bounds = PxBounds(0f, 0f, 15f, 10f)
        assertEquals(bounds, size.toBounds())
    }

    @Test
    fun sizeTimesInt() {
        assertEquals(PxSize(10f, 10f), PxSize(2.5f, 2.5f) * 4)
        assertEquals(PxSize(10f, 10f), 4 * PxSize(2.5f, 2.5f))
    }

    @Test
    fun sizeDivInt() {
        assertEquals(PxSize(10f, 10f), PxSize(40f, 40f) / 4)
    }

    @Test
    fun sizeTimesFloat() {
        assertEquals(PxSize(10f, 10f), PxSize(4f, 4f) * 2.5f)
        assertEquals(PxSize(10f, 10f), 2.5f * PxSize(4f, 4f))
    }

    @Test
    fun sizeDivFloat() {
        assertEquals(PxSize(10f, 10f), PxSize(40f, 40f) / 4f)
    }

    @Test
    fun sizeTimesDouble() {
        assertEquals(PxSize(10f, 10f), PxSize(4f, 4f) * 2.5)
        assertEquals(PxSize(10f, 10f), 2.5 * PxSize(4f, 4f))
    }

    @Test
    fun sizeDivDouble() {
        assertEquals(PxSize(10f, 10f), PxSize(40f, 40f) / 4.0)
    }
}
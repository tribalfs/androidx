/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.ui.painting

import androidx.ui.engine.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class BoxFitTest {

    @Test
    fun `applyBoxFit`() {
        var result: FittedSizes

        result = applyBoxFit(BoxFit.scaleDown, Size(100.0f, 1000.0f), Size(200.0f, 2000.0f))
        assertEquals(result.source, Size(100.0f, 1000.0f))
        assertEquals(result.destination, Size(100.0f, 1000.0f))

        result = applyBoxFit(BoxFit.scaleDown, Size(300.0f, 3000.0f), Size(200.0f, 2000.0f))
        assertEquals(result.source, Size(300.0f, 3000.0f))
        assertEquals(result.destination, Size(200.0f, 2000.0f))

        result = applyBoxFit(BoxFit.fitWidth, Size(2000.0f, 400.0f), Size(1000.0f, 100.0f))
        assertEquals(result.source, Size(2000.0f, 200.0f))
        assertEquals(result.destination, Size(1000.0f, 100.0f))

        result = applyBoxFit(BoxFit.fitHeight, Size(400.0f, 2000.0f), Size(100.0f, 1000.0f))
        assertEquals(result.source, Size(200.0f, 2000.0f))
        assertEquals(result.destination, Size(100.0f, 1000.0f))

        _testZeroAndNegativeSizes(BoxFit.fill)
        _testZeroAndNegativeSizes(BoxFit.contain)
        _testZeroAndNegativeSizes(BoxFit.cover)
        _testZeroAndNegativeSizes(BoxFit.fitWidth)
        _testZeroAndNegativeSizes(BoxFit.fitHeight)
        _testZeroAndNegativeSizes(BoxFit.none)
        _testZeroAndNegativeSizes(BoxFit.scaleDown)
    }

    private fun _testZeroAndNegativeSizes(fit: BoxFit) {
        var result: FittedSizes

        result = applyBoxFit(fit, Size(-400.0f, 2000.0f), Size(100.0f, 1000.0f))
        assertEquals(Size.zero, result.source)
        assertEquals(Size.zero, result.destination)

        result = applyBoxFit(fit, Size(400.0f, -2000.0f), Size(100.0f, 1000.0f))
        assertEquals(Size.zero, result.source)
        assertEquals(Size.zero, result.destination)

        result = applyBoxFit(fit, Size(400.0f, 2000.0f), Size(-100.0f, 1000.0f))
        assertEquals(Size.zero, result.source)
        assertEquals(Size.zero, result.destination)

        result = applyBoxFit(fit, Size(400.0f, 2000.0f), Size(100.0f, -1000.0f))
        assertEquals(Size.zero, result.source)
        assertEquals(Size.zero, result.destination)

        result = applyBoxFit(fit, Size(0.0f, 2000.0f), Size(100.0f, 1000.0f))
        assertEquals(Size.zero, result.source)
        assertEquals(Size.zero, result.destination)

        result = applyBoxFit(fit, Size(400.0f, 0.0f), Size(100.0f, 1000.0f))
        assertEquals(Size.zero, result.source)
        assertEquals(Size.zero, result.destination)

        result = applyBoxFit(fit, Size(400.0f, 2000.0f), Size(0.0f, 1000.0f))
        assertEquals(Size.zero, result.source)
        assertEquals(Size.zero, result.destination)

        result = applyBoxFit(fit, Size(400.0f, 2000.0f), Size(100.0f, 0.0f))
        assertEquals(Size.zero, result.source)
        assertEquals(Size.zero, result.destination)
    }
}
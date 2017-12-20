/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.graphics

import android.graphics.Point
import android.graphics.PointF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [26])
class PointTest {
    @Test fun destructuringInt() {
        val (x, y) = Point(2, 3)
        assertEquals(2, x)
        assertEquals(3, y)
    }

    @Test fun destructuringFloat() {
        val (x, y) = PointF(2.0f, 3.0f)
        assertEquals(2.0f, x)
        assertEquals(3.0f, y)
    }

    @Test fun offsetInt() {
        val (x, y) = Point(2, 3) + 2
        assertEquals(4, x)
        assertEquals(5, y)
    }

    @Test fun offsetFloat() {
        val (x, y) = PointF(2.0f, 3.0f) + 2.0f
        assertEquals(4.0f, x)
        assertEquals(5.0f, y)
    }

    @Test fun offsetPoint() {
        val (x, y) = Point(2, 3) + Point(1, 2)
        assertEquals(3, x)
        assertEquals(5, y)
    }

    @Test fun offsetPointF() {
        val (x, y) = PointF(2.0f, 3.0f) + PointF(1.0f, 2.0f)
        assertEquals(3.0f, x)
        assertEquals(5.0f, y)
    }

    @Test fun negateInt() {
        val (x, y) = -Point(2, 3)
        assertEquals(-2, x)
        assertEquals(-3, y)
    }

    @Test fun negateFloat() {
        val (x, y) = -PointF(2.0f, 3.0f)
        assertEquals(-2.0f, x)
        assertEquals(-3.0f, y)
    }
}

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

package androidx.ui.graphics.painter

import androidx.test.filters.SmallTest
import androidx.ui.core.LayoutDirection
import androidx.ui.geometry.Rect
import androidx.ui.graphics.BlendMode
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.ColorFilter
import androidx.ui.graphics.ImageAsset
import androidx.ui.graphics.Paint
import androidx.ui.graphics.compositeOver
import androidx.ui.graphics.drawscope.DrawScope
import androidx.ui.graphics.drawscope.drawPainter
import androidx.ui.graphics.toPixelMap
import androidx.ui.unit.Px
import androidx.ui.unit.PxSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class PainterTest {

    val size = PxSize(Px(100.0f), Px(100.0f))

    @Test
    fun testPainterDidDraw() {
        val p = object : Painter() {

            var didDraw: Boolean = false

            override val intrinsicSize: PxSize
                get() = size

            override fun DrawScope.onDraw() {
                didDraw = true
            }
        }

        assertEquals(size, p.intrinsicSize)
        assertFalse(p.didDraw)

        drawPainter(
            p,
            Canvas(ImageAsset(100, 100)),
            PxSize(Px(100f), Px(100f))
        )
        assertTrue(p.didDraw)
    }

    @Test
    fun testPainterRtl() {
        val p = object : Painter() {

            var color = Color.Black

            override val intrinsicSize: PxSize
                get() = size

            override fun applyLayoutDirection(layoutDirection: LayoutDirection): Boolean {
                color = if (layoutDirection == LayoutDirection.Rtl) Color.Red else Color.Cyan
                return true
            }

            override fun DrawScope.onDraw() {
                drawRect(color = color)
            }
        }

        val image = ImageAsset(100, 100)

        drawPainter(
            p,
            Canvas(image),
            PxSize(Px(100f), Px(100f)),
            layoutDirection = LayoutDirection.Rtl
        )

        assertEquals(Color.Red, image.toPixelMap()[50, 50])
    }

    @Test
    fun testPainterAlpha() {
        val p = object : Painter() {

            override val intrinsicSize: PxSize
                get() = size

            override fun DrawScope.onDraw() {
                drawRect(color = Color.Red)
            }
        }

        val image = ImageAsset(100, 100)
        val canvas = Canvas(image)

        val paint = Paint().apply { this.color = Color.White }
        canvas.drawRect(Rect.fromLTWH(0.0f, 0.0f, 100.0f, 100.0f), paint)

        drawPainter(
            p,
            canvas,
            size,
            alpha = 0.5f
        )

        val expected = Color(
            alpha = 0.5f,
            red = Color.Red.red,
            green = Color.Red.green,
            blue = Color.Red.blue
        ).compositeOver(Color.White)

        val result = image.toPixelMap()[50, 50]
        assertEquals(expected.red, result.red, 0.01f)
        assertEquals(expected.green, result.green, 0.01f)
        assertEquals(expected.blue, result.blue, 0.01f)
        assertEquals(expected.alpha, result.alpha, 0.01f)
    }

    @Test
    fun testPainterCustomAlpha() {
        val p = object : Painter() {

            var color = Color.Red

            override fun applyAlpha(alpha: Float): Boolean {
                color =
                    Color(
                        alpha = alpha,
                        red = Color.Red.red,
                        blue = Color.Red.blue,
                        green = Color.Red.green
                    )
                return true
            }

            override val intrinsicSize: PxSize
                get() = size

            override fun DrawScope.onDraw() {
                drawRect(color = color)
            }
        }

        assertEquals(Color.Red, p.color)
        val image = ImageAsset(100, 100)
        val canvas = Canvas(image)

        val paint = Paint().apply { this.color = Color.White }
        canvas.drawRect(Rect.fromLTWH(0.0f, 0.0f, 100.0f, 100.0f), paint)

        drawPainter(
            p,
            canvas,
            size,
            alpha = 0.5f
        )

        val expected = Color(
            alpha = 0.5f,
            red = Color.Red.red,
            green = Color.Red.green,
            blue = Color.Red.blue
        ).compositeOver(Color.White)

        val result = image.toPixelMap()[50, 50]
        assertEquals(expected.red, result.red, 0.01f)
        assertEquals(expected.green, result.green, 0.01f)
        assertEquals(expected.blue, result.blue, 0.01f)
        assertEquals(expected.alpha, result.alpha, 0.01f)
    }

    @Test
    fun testColorFilter() {
        val p = object : Painter() {

            var colorFilter: ColorFilter? = ColorFilter(Color.Red, BlendMode.srcIn)

            override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
                this.colorFilter = colorFilter
                return true
            }

            override val intrinsicSize: PxSize
                get() = size

            override fun DrawScope.onDraw() {
                drawRect(color = Color.Black, colorFilter = colorFilter)
            }
        }

        val image = ImageAsset(100, 100)

        drawPainter(
            p,
            Canvas(image),
            size,
            colorFilter = ColorFilter(Color.Blue, BlendMode.srcIn)
        )
        assertEquals(Color.Blue, image.toPixelMap()[50, 50])
    }
}
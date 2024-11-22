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

package androidx.compose.ui.graphics

import android.os.Build
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import kotlin.math.roundToInt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

val RedDisplayP3 = Color(red = 1f, green = 0f, blue = 0f, colorSpace = ColorSpaces.DisplayP3)
val BlueDisplayP3 = Color(red = 0f, green = 0f, blue = 1f, colorSpace = ColorSpaces.DisplayP3)

@SmallTest
@RunWith(AndroidJUnit4::class)
class ShaderTest {

    @Test
    fun testLinearGradient() {
        val imageBitmap = ImageBitmap(100, 100)
        imageBitmap.drawInto {
            drawRect(
                brush =
                    Brush.linearGradient(
                        0.0f to Color.Red,
                        0.5f to Color.Red,
                        0.5f to Color.Blue,
                        1.0f to Color.Blue,
                        start = Offset.Zero,
                        end = Offset(0.0f, 100f),
                        tileMode = TileMode.Clamp
                    )
            )
        }

        val pixelMap = imageBitmap.toPixelMap()
        val centerX = imageBitmap.width / 2
        val centerY = imageBitmap.height / 2
        assertEquals(Color.Red, pixelMap[centerX, centerY - 5])
        assertEquals(Color.Blue, pixelMap[centerX, centerY + 5])
        assertEquals(Color.Red, pixelMap[5, centerY - 5])
        assertEquals(Color.Blue, pixelMap[5, centerY + 5])
        assertEquals(Color.Red, pixelMap[imageBitmap.width - 5, centerY - 5])
        assertEquals(Color.Blue, pixelMap[imageBitmap.width - 5, centerY + 5])
    }

    @Test
    fun testLinearGradientColorLong() {
        val imageBitmap = ImageBitmap(100, 100, ImageBitmapConfig.F16)
        imageBitmap.drawInto {
            drawRect(
                brush =
                    Brush.linearGradient(
                        0.0f to RedDisplayP3,
                        0.5f to RedDisplayP3,
                        0.5f to BlueDisplayP3,
                        1.0f to BlueDisplayP3,
                        start = Offset.Zero,
                        end = Offset(0.0f, 100f),
                        tileMode = TileMode.Clamp
                    )
            )
        }

        val centerX = imageBitmap.width / 2
        val centerY = imageBitmap.height / 2
        val bitmapColorSpace = imageBitmap.colorSpace
        val convertedRed = RedDisplayP3.convertColorCompat(bitmapColorSpace)
        val convertedBlue = BlueDisplayP3.convertColorCompat(bitmapColorSpace)
        verifyColor(convertedRed, imageBitmap.getComposeColor(centerX, centerY - 5))
        verifyColor(convertedBlue, imageBitmap.getComposeColor(centerX, centerY + 5))
        verifyColor(convertedRed, imageBitmap.getComposeColor(5, centerY - 5))
        verifyColor(convertedBlue, imageBitmap.getComposeColor(5, centerY + 5))
        verifyColor(convertedRed, imageBitmap.getComposeColor(imageBitmap.width - 5, centerY - 5))
        verifyColor(convertedBlue, imageBitmap.getComposeColor(imageBitmap.width - 5, centerY + 5))
    }

    @Test
    fun testRadialGradient() {
        val imageBitmap = ImageBitmap(100, 100)

        imageBitmap.drawInto {
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0.0f to Color.Red,
                        0.5f to Color.Red,
                        0.5f to Color.Blue,
                        1.0f to Color.Blue,
                        center = Offset(50f, 50f),
                        radius = 50f,
                        tileMode = TileMode.Clamp
                    )
            )
        }

        val pixelMap = imageBitmap.toPixelMap()

        assertEquals(Color.Red, pixelMap[50, 50])
        assertEquals(Color.Red, pixelMap[50, 30])
        assertEquals(Color.Red, pixelMap[70, 50])
        assertEquals(Color.Red, pixelMap[50, 70])
        assertEquals(Color.Red, pixelMap[30, 50])

        assertEquals(Color.Blue, pixelMap[50, 20])
        assertEquals(Color.Blue, pixelMap[80, 50])
        assertEquals(Color.Blue, pixelMap[50, 80])
        assertEquals(Color.Blue, pixelMap[20, 50])
    }

    @Test
    fun testRadialGradientColorLong() {
        val imageBitmap = ImageBitmap(100, 100, config = ImageBitmapConfig.F16)

        imageBitmap.drawInto {
            drawCircle(
                brush =
                    Brush.radialGradient(
                        0.0f to RedDisplayP3,
                        0.5f to RedDisplayP3,
                        0.5f to BlueDisplayP3,
                        1.0f to BlueDisplayP3,
                        center = Offset(50f, 50f),
                        radius = 50f,
                        tileMode = TileMode.Clamp
                    )
            )
        }

        val bitmapColorSpace = imageBitmap.colorSpace
        val convertedRed = RedDisplayP3.convertColorCompat(bitmapColorSpace)
        val convertedBlue = BlueDisplayP3.convertColorCompat(bitmapColorSpace)
        verifyColor(convertedRed, imageBitmap.getComposeColor(50, 50))
        verifyColor(convertedRed, imageBitmap.getComposeColor(50, 30))
        verifyColor(convertedRed, imageBitmap.getComposeColor(70, 50))
        verifyColor(convertedRed, imageBitmap.getComposeColor(50, 70))
        verifyColor(convertedRed, imageBitmap.getComposeColor(30, 50))

        verifyColor(convertedBlue, imageBitmap.getComposeColor(50, 20))
        verifyColor(convertedBlue, imageBitmap.getComposeColor(80, 50))
        verifyColor(convertedBlue, imageBitmap.getComposeColor(50, 80))
        verifyColor(convertedBlue, imageBitmap.getComposeColor(20, 50))
    }

    @Test
    fun testSweepGradient() {
        val imageBitmap = ImageBitmap(100, 100)
        val center = Offset(50f, 50f)
        imageBitmap.drawInto {
            drawRect(
                brush =
                    Brush.sweepGradient(
                        0.0f to Color.Red,
                        0.5f to Color.Red,
                        0.5f to Color.Blue,
                        1.0f to Color.Blue,
                        center = center
                    )
            )
        }

        val pixelMap = imageBitmap.toPixelMap()
        val centerX = center.x.roundToInt()
        val centerY = center.y.roundToInt()
        assertEquals(Color.Red, pixelMap[centerX, centerY + 5])
        assertEquals(Color.Blue, pixelMap[centerX, centerY - 5])
        assertEquals(Color.Red, pixelMap[centerX * 2 - 5, centerY + 5])
        assertEquals(Color.Blue, pixelMap[centerX * 2 - 5, centerY - 5])
        assertEquals(Color.Red, pixelMap[5, centerY + 5])
        assertEquals(Color.Blue, pixelMap[5, centerY - 5])
    }

    @Test
    fun testSweepGradientColorLong() {
        val imageBitmap = ImageBitmap(100, 100, config = ImageBitmapConfig.F16)
        val center = Offset(50f, 50f)
        imageBitmap.drawInto {
            drawRect(
                brush =
                    Brush.sweepGradient(
                        0.0f to RedDisplayP3,
                        0.5f to RedDisplayP3,
                        0.5f to BlueDisplayP3,
                        1.0f to BlueDisplayP3,
                        center = center
                    )
            )
        }

        val bitmapColorSpace = imageBitmap.colorSpace
        val convertedRed = RedDisplayP3.convertColorCompat(bitmapColorSpace)
        val convertedBlue = BlueDisplayP3.convertColorCompat(bitmapColorSpace)
        val centerX = center.x.roundToInt()
        val centerY = center.y.roundToInt()

        verifyColor(convertedRed, imageBitmap.getComposeColor(centerX, centerY + 5))
        verifyColor(convertedBlue, imageBitmap.getComposeColor(centerX, centerY - 5))
        verifyColor(convertedRed, imageBitmap.getComposeColor(centerX * 2 - 5, centerY + 5))
        verifyColor(convertedBlue, imageBitmap.getComposeColor(centerX * 2 - 5, centerY - 5))
        verifyColor(convertedRed, imageBitmap.getComposeColor(5, centerY + 5))
        verifyColor(convertedBlue, imageBitmap.getComposeColor(5, centerY - 5))
    }

    private fun Color.convertColorCompat(dst: ColorSpace): Color {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            convert(dst)
        } else {
            // Bitmap#getColor was not introduced until Android Q, so convert to argb for these
            // platform versions ad Bitmap#getPixel is the only fallback
            Color(this.toArgb())
        }
    }

    private fun ImageBitmap.getComposeColor(x: Int, y: Int): Color {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val androidColor = asAndroidBitmap().getColor(x, y)
            Color(
                red = androidColor.red(),
                green = androidColor.green(),
                blue = androidColor.blue(),
                alpha = androidColor.alpha(),
                colorSpace = androidColor.colorSpace.toComposeColorSpace()
            )
        } else {
            Color(this.asAndroidBitmap().getPixel(x, y))
        }
    }

    private fun verifyColor(expected: Color, actual: Color) {
        assertEquals(expected.red, actual.red, 0.01f)
        assertEquals(expected.green, actual.green, 0.01f)
        assertEquals(expected.blue, actual.blue, 0.01f)
        assertEquals(expected.colorSpace, actual.colorSpace)
    }

    @Test
    fun testLinearGradientIntrinsicSize() {
        assertEquals(
            Size(100f, 200f),
            Brush.linearGradient(
                    listOf(Color.Red, Color.Blue),
                    start = Offset(200f, 100f),
                    end = Offset(300f, 300f)
                )
                .intrinsicSize
        )
    }

    @Test
    fun testLinearGradientNegativePosition() {
        assertEquals(
            Size(100f, 200f),
            Brush.linearGradient(
                    listOf(Color.Red, Color.Blue),
                    start = Offset(200f, 100f),
                    end = Offset(100f, -100f)
                )
                .intrinsicSize
        )
    }

    @Test
    fun testLinearGradientInfiniteWidth() {
        assertEquals(
            Size(Float.NaN, 200f),
            Brush.linearGradient(
                    listOf(Color.Red, Color.Blue),
                    start = Offset(Float.POSITIVE_INFINITY, 100f),
                    end = Offset(Float.POSITIVE_INFINITY, 300f)
                )
                .intrinsicSize
        )
    }

    @Test
    fun testLinearGradientInfiniteHeight() {
        assertEquals(
            Size(100f, Float.NaN),
            Brush.linearGradient(
                    listOf(Color.Red, Color.Blue),
                    start = Offset(100f, 0f),
                    end = Offset(200f, Float.POSITIVE_INFINITY)
                )
                .intrinsicSize
        )
    }

    @Test
    fun testSweepGradientIntrinsicSize() {
        // Sweep gradients do not have an intrinsic size as they sweep/fill the geometry they are
        // drawn with
        assertEquals(
            Size.Unspecified,
            Brush.sweepGradient(listOf(Color.Red, Color.Blue)).intrinsicSize
        )
    }

    @Test
    fun testRadialGradientIntrinsicSize() {
        assertEquals(
            Size(100f, 100f),
            Brush.radialGradient(listOf(Color.Red, Color.Blue), radius = 50f).intrinsicSize
        )
    }

    @Test
    fun testRadialGradientInfiniteSize() {
        assertEquals(
            Size.Unspecified,
            Brush.radialGradient(listOf(Color.Red, Color.Blue)).intrinsicSize
        )
    }

    @Test
    fun testInvalidWidthBrush() {
        // Verify that attempts to create a RadialGradient with a width of 0 do not throw
        // IllegalArgumentExceptions for an invalid radius
        val brush = Brush.radialGradient(listOf(Color.Red, Color.Blue))
        val paint = Paint()
        brush.applyTo(Size(0f, 10f), paint, 1.0f)
    }

    @Test
    fun testInvalidHeightBrush() {
        val brush = Brush.radialGradient(listOf(Color.Red, Color.Blue))
        val paint = Paint()
        // Verify that attempts to create a RadialGradient with a height of 0 do not throw
        // IllegalArgumentExceptions for an invalid radius
        brush.applyTo(Size(10f, 0f), paint, 1.0f)
    }

    @Test
    fun testValidToInvalidWidthBrush() {
        // Verify that attempts to create a RadialGradient with a non-zero width/height that
        // is later attempted to be recreated with a zero width remove the shader from the Paint
        val brush = Brush.radialGradient(listOf(Color.Red, Color.Blue))
        val paint = Paint()
        brush.applyTo(Size(10f, 10f), paint, 1.0f)

        assertNotNull(paint.shader)

        brush.applyTo(Size(0f, 10f), paint, 1.0f)
        assertNull(paint.shader)
    }

    @Test
    fun testValidToInvalidHeightBrush() {
        // Verify that attempts to create a RadialGradient with a non-zero width/height that
        // is later attempted to be recreated with a zero height remove the shader from the Paint
        val brush = Brush.radialGradient(listOf(Color.Red, Color.Blue))
        val paint = Paint()

        brush.applyTo(Size(10f, 10f), paint, 1.0f)

        assertNotNull(paint.shader)

        brush.applyTo(Size(10f, 0f), paint, 1.0f)
        assertNull(paint.shader)
    }

    private fun ImageBitmap.drawInto(block: DrawScope.() -> Unit) =
        CanvasDrawScope()
            .draw(
                Density(1.0f),
                LayoutDirection.Ltr,
                Canvas(this),
                Size(width.toFloat(), height.toFloat()),
                block
            )
}

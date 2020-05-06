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
import androidx.ui.geometry.Offset
import androidx.ui.geometry.Rect
import androidx.ui.geometry.Size
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.ImageAsset
import androidx.ui.graphics.Paint
import androidx.ui.graphics.SolidColor
import androidx.ui.graphics.compositeOver
import androidx.ui.graphics.toPixelMap
import androidx.ui.unit.Px
import androidx.ui.unit.PxSize
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@SmallTest
@RunWith(JUnit4::class)
class CanvasScopeTest {

    private val width: Int = 100
    private val height: Int = 100
    private val dstSize = PxSize(Px(width.toFloat()), Px(height.toFloat()))

    private fun createTestDstImage(): ImageAsset {
        val dst = ImageAsset(width, height)
        val dstCanvas = Canvas(dst)
        val dstPaint = Paint().apply {
            this.color = Color.White
        }
        dstCanvas.drawRect(
            Rect.fromLTWH(0.0f, 0.0f, 200.0f, 200.0f),
            dstPaint
        )
        return dst
    }

    @Test
    fun testDrawRectColor() {
        val img = createTestDstImage()
        CanvasScope().draw(Canvas(img), dstSize) {
            // Verify that the overload that consumes a color parameter
            // fills the canvas with red color
            drawRect(color = Color.Red)
        }

        val pixelMap = img.toPixelMap()
        for (i in 0 until pixelMap.width) {
            for (j in 0 until pixelMap.height) {
                assertEquals(Color.Red, pixelMap[i, j])
            }
        }
    }

    @Test
    fun testDrawRectBrushColor() {
        val img = createTestDstImage()
        CanvasScope().draw(Canvas(img), dstSize) {
            // Verify that the overload that consumes a brush parameter
            // fills the canvas with red color
            drawRect(brush = SolidColor(Color.Red))
        }

        val pixelMap = img.toPixelMap()
        for (i in 0 until pixelMap.width) {
            for (j in 0 until pixelMap.height) {
                assertEquals(Color.Red, pixelMap[i, j])
            }
        }
    }

    @Test
    fun testDrawRectColorAlpha() {
        val img = createTestDstImage()
        CanvasScope().draw(Canvas(img), dstSize) {
            // Verify that the overload that consumes a color parameter
            // fills the canvas with red color
            drawRect(color = Color.Red, alpha = 0.5f)
        }

        val expected = Color(
            alpha = 0.5f,
            red = Color.Red.red,
            green = Color.Red.green,
            blue = Color.Red.blue
        ).compositeOver(Color.White)

        val pixelMap = img.toPixelMap()
        for (i in 0 until pixelMap.width) {
            for (j in 0 until pixelMap.height) {
                val result = pixelMap[i, j]
                assertEquals(expected.red, result.red, 0.01f)
                assertEquals(expected.green, result.green, 0.01f)
                assertEquals(expected.blue, result.blue, 0.01f)
                assertEquals(expected.alpha, result.alpha, 0.01f)
            }
        }
    }

    @Test
    fun testDrawRectBrushColorAlpha() {
        val img = createTestDstImage()
        CanvasScope().draw(Canvas(img), dstSize) {
            // Verify that the overload that consumes a brush parameter
            // fills the canvas with red color
            drawRect(brush = SolidColor(Color.Red), alpha = 0.5f)
        }

        val expected = Color(
            alpha = 0.5f,
            red = Color.Red.red,
            green = Color.Red.green,
            blue = Color.Red.blue
        ).compositeOver(Color.White)

        val pixelMap = img.toPixelMap()
        for (i in 0 until pixelMap.width) {
            for (j in 0 until pixelMap.height) {
                val result = pixelMap[i, j]
                assertEquals(expected.red, result.red, 0.01f)
                assertEquals(expected.green, result.green, 0.01f)
                assertEquals(expected.blue, result.blue, 0.01f)
                assertEquals(expected.alpha, result.alpha, 0.01f)
            }
        }
    }

    @Test
    fun testDrawTranslatedRect() {
        val img = createTestDstImage()
        val insetLeft = 10.0f
        val insetTop = 12.0f
        CanvasScope().draw(Canvas(img), dstSize) {
            translate(insetLeft, insetTop) {
                drawRect(color = Color.Red)
            }
        }

        val pixelMap = img.toPixelMap()
        for (i in 0 until pixelMap.width) {
            for (j in 0 until pixelMap.height) {
                val expectedColor =
                    if (i >= insetLeft && j >= insetTop) {
                        Color.Red
                    } else {
                        Color.White
                    }
                assertEquals("Coordinate: " + i + ", " + j, expectedColor, pixelMap[i, j])
            }
        }
    }

    @Test
    fun testDrawInsetRect() {
        val img = createTestDstImage()
        val insetLeft = 10.0f
        val insetTop = 12.0f
        val insetRight = 11.0f
        val insetBottom = 13.0f
        CanvasScope().draw(Canvas(img), dstSize) {
            inset(insetLeft, insetTop, insetRight, insetBottom) {
                drawRect(color = Color.Red)
            }
        }

        val pixelMap = img.toPixelMap()
        for (i in 0 until pixelMap.width) {
            for (j in 0 until pixelMap.height) {
                val expectedColor =
                    if (i >= insetLeft && i < pixelMap.width - insetRight &&
                        j >= insetTop && j < pixelMap.height - insetBottom) {
                        Color.Red
                    } else {
                        Color.White
                    }
                assertEquals("Coordinate: " + i + ", " + j, expectedColor, pixelMap[i, j])
            }
        }
    }

    @Test
    fun testInsetRestoredAfterScopedInsetDraw() {
        val img = createTestDstImage()
        CanvasScope().draw(Canvas(img), dstSize) {
            // Verify that the overload that consumes a color parameter
            // fills the canvas with red color
            val left = 10.0f
            val top = 30.0f
            val right = 20.0f
            val bottom = 12.0f
            inset(left, top, right, bottom) {
                drawRect(color = Color.Red)
                assertEquals(dstSize.width.value - (left + right), size.width)
                assertEquals(dstSize.height.value - (top + bottom), size.height)
            }

            assertEquals(dstSize.width.value, size.width)
            assertEquals(dstSize.height.value, size.height)
        }
    }

    @Test
    fun testFillOverwritesOldAlpha() {
        val img = createTestDstImage()
        CanvasScope().draw(Canvas(img), dstSize) {
            // Verify that the alpha parameter used in the first draw call is overridden
            // in the subsequent call that does not specify an alpha value
            drawRect(color = Color.Blue, alpha = 0.5f)
            drawRect(color = Color.Red)
        }

        val pixelMap = img.toPixelMap()
        for (i in 0 until pixelMap.width) {
            for (j in 0 until pixelMap.height) {
                assertEquals(Color.Red, pixelMap[i, j])
            }
        }
    }

    @Test
    fun testFillOverwritesOldPaintBrushAlpha() {
        val img = createTestDstImage()
        CanvasScope().draw(Canvas(img), dstSize) {
            // Verify that the alpha parameter used in the first draw call is overridden
            // in the subsequent call that does not specify an alpha value that goes through
            // a different code path for configuration of the underlying paint
            drawRect(color = Color.Blue, alpha = 0.5f)
            drawRect(brush = SolidColor(Color.Red))
        }

        val pixelMap = img.toPixelMap()
        for (i in 0 until pixelMap.width) {
            for (j in 0 until pixelMap.height) {
                assertEquals(Color.Red, pixelMap[i, j])
            }
        }
    }

    @Test
    fun testScaleTopLeftPivot() {
        val canvasScope = CanvasScope()

        val width = 200
        val height = 200
        val size = PxSize(Px(width.toFloat()), Px(height.toFloat()))
        val imageAsset = ImageAsset(width, height)

        canvasScope.draw(Canvas(imageAsset), size) {
            drawRect(color = Color.Red)
            scale(0.5f, 0.5f, pivotX = 0.0f, pivotY = 0.0f) {
                drawRect(color = Color.Blue)
            }
        }

        val pixelMap = imageAsset.toPixelMap()
        assertEquals(Color.Blue, pixelMap[0, 0])
        assertEquals(Color.Blue, pixelMap[99, 0])
        assertEquals(Color.Blue, pixelMap[0, 99])
        assertEquals(Color.Blue, pixelMap[99, 99])

        assertEquals(Color.Red, pixelMap[0, 100])
        assertEquals(Color.Red, pixelMap[100, 0])
        assertEquals(Color.Red, pixelMap[100, 100])
        assertEquals(Color.Red, pixelMap[100, 99])
        assertEquals(Color.Red, pixelMap[99, 100])
    }

    @Test
    fun testScaleCenterDefaultPivot() {
        val canvasScope = CanvasScope()

        val width = 200
        val height = 200
        val size = PxSize(Px(width.toFloat()), Px(height.toFloat()))
        val imageAsset = ImageAsset(width, height)

        canvasScope.draw(Canvas(imageAsset), size) {
            drawRect(color = Color.Red)
            scale(0.5f, 0.5f) {
                drawRect(color = Color.Blue)
            }
        }

        val pixelMap = imageAsset.toPixelMap()
        val left = width / 2 - 50
        val top = height / 2 - 50
        val right = width / 2 + 50 - 1
        val bottom = height / 2 + 50 - 1
        assertEquals(Color.Blue, pixelMap[left, top])
        assertEquals(Color.Blue, pixelMap[right, top])
        assertEquals(Color.Blue, pixelMap[left, bottom])
        assertEquals(Color.Blue, pixelMap[right, bottom])

        assertEquals(Color.Red, pixelMap[left - 1, top - 1])
        assertEquals(Color.Red, pixelMap[left - 1, top])
        assertEquals(Color.Red, pixelMap[left, top - 1])

        assertEquals(Color.Red, pixelMap[right + 1, top - 1])
        assertEquals(Color.Red, pixelMap[right + 1, top])
        assertEquals(Color.Red, pixelMap[right, top - 1])

        assertEquals(Color.Red, pixelMap[left - 1, bottom + 1])
        assertEquals(Color.Red, pixelMap[left - 1, bottom])
        assertEquals(Color.Red, pixelMap[left, bottom + 1])

        assertEquals(Color.Red, pixelMap[right + 1, bottom + 1])
        assertEquals(Color.Red, pixelMap[right + 1, bottom])
        assertEquals(Color.Red, pixelMap[right, bottom + 1])
    }

    @Test
    fun testInsetNegativeWidthThrows() {
        val canvasScope = CanvasScope()

        val width = 200
        val height = 200
        val size = PxSize(Px(width.toFloat()), Px(height.toFloat()))
        val imageAsset = ImageAsset(width, height)

        try {
            canvasScope.draw(Canvas(imageAsset), size) {
                inset(100.0f, 0.0f, 100.0f, 0.0f) {
                    drawRect(color = Color.Red)
                }
            }
            fail("Width must be greater than zero after applying inset")
        } catch (e: IllegalArgumentException) {
            // no-op
        }
    }

    @Test
    fun testInsetNegativeHeightThrows() {
        val canvasScope = CanvasScope()

        val width = 200
        val height = 200
        val size = PxSize(Px(width.toFloat()), Px(height.toFloat()))
        val imageAsset = ImageAsset(width, height)

        try {
            canvasScope.draw(Canvas(imageAsset), size) {
                inset(0.0f, 100.0f, 0.0f, 100.0f) {
                    drawRect(color = Color.Red)
                }
            }
            fail("Height must be greater than zero after applying inset")
        } catch (e: IllegalArgumentException) {
            // no-op
        }
    }

    @Test
    fun testScaleBottomRightPivot() {
        val canvasScope = CanvasScope()

        val width = 200
        val height = 200
        val size = PxSize(Px(width.toFloat()), Px(height.toFloat()))
        val imageAsset = ImageAsset(width, height)

        canvasScope.draw(Canvas(imageAsset), size) {
            drawRect(color = Color.Red)
            scale(0.5f, 0.5f, width.toFloat(), height.toFloat()) {
                drawRect(color = Color.Blue)
            }
        }

        val pixelMap = imageAsset.toPixelMap()

        val left = width - 100
        val top = height - 100
        val right = width - 1
        val bottom = height - 1
        assertEquals(Color.Blue, pixelMap[left, top])
        assertEquals(Color.Blue, pixelMap[right, top])
        assertEquals(Color.Blue, pixelMap[left, bottom])
        assertEquals(Color.Blue, pixelMap[left, right])

        assertEquals(Color.Red, pixelMap[left, top - 1])
        assertEquals(Color.Red, pixelMap[left - 1, top])
        assertEquals(Color.Red, pixelMap[left - 1, top - 1])

        assertEquals(Color.Red, pixelMap[right, top - 1])
        assertEquals(Color.Red, pixelMap[left - 1, bottom])
    }

    @Test
    fun testRotationCenterPivot() {
        val width = 200
        val height = 200
        val size = PxSize(Px(width.toFloat()), Px(height.toFloat()))
        val imageAsset = ImageAsset(width, height)
        CanvasScope().draw(Canvas(imageAsset), size) {
            drawRect(color = Color.Red)
            rotate(180.0f) {
                drawRect(
                    topLeft = Offset(100.0f, 100.0f),
                    size = Size(100.0f, 100.0f),
                    color = Color.Blue
                )
            }
        }

        val pixelMap = imageAsset.toPixelMap()
        assertEquals(Color.Blue, pixelMap[0, 0])
        assertEquals(Color.Blue, pixelMap[99, 0])
        assertEquals(Color.Blue, pixelMap[0, 99])
        assertEquals(Color.Blue, pixelMap[99, 99])

        assertEquals(Color.Red, pixelMap[0, 100])
        assertEquals(Color.Red, pixelMap[100, 0])
        assertEquals(Color.Red, pixelMap[100, 100])
        assertEquals(Color.Red, pixelMap[100, 99])
        assertEquals(Color.Red, pixelMap[99, 100])
    }

    @Test
    fun testRotationTopLeftPivot() {
        val width = 200
        val height = 200
        val size = PxSize(Px(width.toFloat()), Px(height.toFloat()))
        val imageAsset = ImageAsset(width, height)
        CanvasScope().draw(Canvas(imageAsset), size) {
            drawRect(color = Color.Red)
            rotate(-45.0f, 0.0f, 0.0f) {
                drawRect(
                    size = Size(100.0f, 100.0f),
                    color = Color.Blue
                )
            }
        }

        val pixelMap = imageAsset.toPixelMap()
        assertEquals(Color.Blue, pixelMap[2, 0])
        assertEquals(Color.Blue, pixelMap[50, 49])
        assertEquals(Color.Blue, pixelMap[70, 0])
        assertEquals(Color.Blue, pixelMap[70, 68])

        assertEquals(Color.Red, pixelMap[50, 51])
        assertEquals(Color.Red, pixelMap[75, 76])
    }

    @Test
    fun testBatchTransformEquivalent() {
        val width = 200
        val height = 200
        val size = PxSize(Px(width.toFloat()), Px(height.toFloat()))
        val imageAsset1 = ImageAsset(width, height)
        CanvasScope().draw(Canvas(imageAsset1), size) {
            drawRect(color = Color.Red)
            inset(20.0f, 12.0f, 10.0f, 8.0f) {
                scale(2.0f, 0.5f) {
                    rotate(-45.0f, 0.0f, 0.0f) {
                        translate(7.0f, 9.0f) {
                            drawRect(
                                size = Size(100.0f, 100.0f),
                                color = Color.Blue
                            )
                        }
                    }
                }
            }
        }

        val imageAsset2 = ImageAsset(width, height)
        val saveCountCanvas = SaveCountCanvas(Canvas(imageAsset2))
        CanvasScope().draw(saveCountCanvas, size) {
            drawRect(color = Color.Red)
            withTransform({
                inset(20.0f, 12.0f, 10.0f, 8.0f)
                scale(2.0f, 0.5f)
                rotate(-45.0f, 0.0f, 0.0f)
                translate(7.0f, 9.0f)
            }) {
                // 2 saves at this point, the initial draw call does a save
                // as well as the withTransform call
                assertEquals(2, saveCountCanvas.saveCount)
                drawRect(
                    size = Size(100.0f, 100.0f),
                    color = Color.Blue
                )
            }

            // Restore to the save count of the initial CanvasScope.draw call
            assertEquals(1, saveCountCanvas.saveCount)
        }

        val pixelMap1 = imageAsset1.toPixelMap()
        val pixelMap2 = imageAsset2.toPixelMap()
        assertEquals(pixelMap1.width, pixelMap2.width)
        assertEquals(pixelMap1.height, pixelMap2.height)
        assertEquals(pixelMap1.stride, pixelMap2.stride)
        assertEquals(pixelMap1.bufferOffset, pixelMap2.bufferOffset)
        for (x in 0 until pixelMap1.width) {
            for (y in 0 until pixelMap1.height) {
                assertEquals("coordinate: " + x + ", " + y + " expected: " +
                        pixelMap1[x, y] + " actual: " + pixelMap2[x, y],
                    pixelMap1[x, y],
                    pixelMap2[x, y]
                )
            }
        }
    }

    class SaveCountCanvas(val canvas: Canvas) : Canvas by canvas {

        var saveCount: Int = 0

        override fun save() {
            saveCount++
        }

        override fun restore() {
            saveCount--
        }
    }
}
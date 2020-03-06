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

package androidx.ui.core

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.Composable
import androidx.test.filters.SdkSuppress
import androidx.test.filters.SmallTest
import androidx.test.rule.ActivityTestRule
import androidx.ui.core.test.AtLeastSize
import androidx.ui.core.test.Padding
import androidx.ui.core.test.background

import androidx.ui.core.test.runOnUiThreadIR
import androidx.ui.core.test.waitAndScreenShot
import androidx.ui.framework.test.TestActivity
import androidx.ui.geometry.Rect
import androidx.ui.graphics.BlendMode
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.ColorFilter
import androidx.ui.graphics.DefaultAlpha
import androidx.ui.graphics.Paint
import androidx.ui.graphics.ScaleFit
import androidx.ui.graphics.compositeOver
import androidx.ui.graphics.painter.Painter
import androidx.ui.graphics.toArgb
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.Px
import androidx.ui.unit.ipx
import androidx.ui.unit.PxSize
import androidx.ui.unit.max
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@SmallTest
@RunWith(JUnit4::class)
class PainterModifierTest {

    val containerWidth = 100.0f
    val containerHeight = 100.0f

    @get:Rule
    val rule = ActivityTestRule<TestActivity>(TestActivity::class.java)
    private lateinit var activity: TestActivity
    private lateinit var drawLatch: CountDownLatch

    @Before
    fun setup() {
        activity = rule.activity
        activity.hasFocusLatch
        drawLatch = CountDownLatch(1)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testPainterModifierColorFilter() {
        val paintLatch = CountDownLatch(1)
        rule.runOnUiThreadIR {
            activity.setContent {
                testPainter(
                    colorFilter = ColorFilter(Color.Cyan, BlendMode.srcIn),
                    latch = paintLatch
                )
            }
        }

        paintLatch.await(5, TimeUnit.SECONDS)

        obtainScreenshotBitmap(
            containerWidth.roundToInt(),
            containerHeight.roundToInt()
        ).apply {
            assertEquals(Color.Cyan.toArgb(), getPixel(50, 50))
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testPainterModifierAlpha() {
        val paintLatch = CountDownLatch(1)
        rule.runOnUiThreadIR {
            activity.setContent {
                testPainter(
                    alpha = 0.5f,
                    latch = paintLatch
                )
            }
        }

        paintLatch.await(5, TimeUnit.SECONDS)

        obtainScreenshotBitmap(
            containerWidth.roundToInt(),
            containerHeight.roundToInt()
        ).apply {
            val expected = Color(
                alpha = 0.5f,
                red = Color.Red.red,
                green = Color.Red.green,
                blue = Color.Red.blue
            ).compositeOver(Color.White)

            val result = Color(getPixel(50, 50))
            assertEquals(expected.red, result.red, 0.01f)
            assertEquals(expected.green, result.green, 0.01f)
            assertEquals(expected.blue, result.blue, 0.01f)
            assertEquals(expected.alpha, result.alpha, 0.01f)
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testPainterModifierRtl() {
        val paintLatch = CountDownLatch(1)
        rule.runOnUiThreadIR {
            activity.setContent {
                testPainter(
                    rtl = true,
                    latch = paintLatch
                )
            }
        }

        paintLatch.await(5, TimeUnit.SECONDS)

        obtainScreenshotBitmap(
            containerWidth.roundToInt(),
            containerHeight.roundToInt()
        ).apply {
            assertEquals(Color.Blue.toArgb(), getPixel(50, 50))
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testPainterAspectRatioMaintainedInSmallerParent() {
        val paintLatch = CountDownLatch(1)
        val containerSizePx = containerWidth.roundToInt().ipx * 3
        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(size = containerSizePx, modifier = background
                    (Color.White)) {
                    // Verify that the contents are scaled down appropriately even though
                    // the Painter's intrinsic width and height is twice that of the component
                    // it is to be drawn into
                    Padding(containerWidth.roundToInt().ipx) {
                        AtLeastSize(size = containerWidth.roundToInt().ipx,
                            modifier = LatchPainter(
                                containerWidth * 2,
                                containerHeight * 2,
                                paintLatch
                            ).toModifier(alignment = Alignment.Center, scaleFit = ScaleFit.Fit)) {
                        }
                    }
                }
            }
        }

        paintLatch.await(5, TimeUnit.SECONDS)

        obtainScreenshotBitmap(
            containerSizePx.value,
            containerSizePx.value
        ).apply {
            assertEquals(Color.White.toArgb(), getPixel(containerWidth.roundToInt() - 1,
                containerHeight.roundToInt() - 1))
            assertEquals(Color.Red.toArgb(), getPixel(containerWidth.roundToInt() + 1,
                containerWidth.roundToInt() + 1))
            assertEquals(Color.Red.toArgb(), getPixel(containerWidth.roundToInt() * 2 - 1,
                containerWidth.roundToInt() * 2 - 1))
            assertEquals(Color.White.toArgb(), getPixel(containerWidth.roundToInt() * 2 + 1,
                containerHeight.roundToInt() * 2 + 1))
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testPainterAlignedBottomRightIfSmallerThanParent() {
        val paintLatch = CountDownLatch(1)
        val containerSizePx = containerWidth.roundToInt().ipx * 2
        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(size = containerWidth.roundToInt().ipx * 2,
                    modifier = background(Color.White) + LatchPainter(
                        containerWidth,
                        containerHeight,
                        paintLatch
                    ).toModifier(
                        alignment = Alignment.BottomEnd,
                        scaleFit = ScaleFit.Fit)
                    ) {
                    // Intentionally empty
                }
            }
        }

        paintLatch.await(5, TimeUnit.SECONDS)

        val bottom = containerSizePx.value - 1
        val right = containerSizePx.value - 1
        val innerBoxTop = containerSizePx.value - containerWidth.roundToInt()
        val innerBoxLeft = containerSizePx.value - containerWidth.roundToInt()
        obtainScreenshotBitmap(
            containerSizePx.value,
            containerSizePx.value
        ).apply {
            assertEquals(Color.Red.toArgb(), getPixel(right, bottom))
            assertEquals(Color.Red.toArgb(), getPixel(innerBoxLeft, bottom))
            assertEquals(Color.Red.toArgb(), getPixel(innerBoxLeft, innerBoxTop))
            assertEquals(Color.Red.toArgb(), getPixel(right, innerBoxTop))

            assertEquals(Color.White.toArgb(), getPixel(innerBoxLeft - 1, bottom))
            assertEquals(Color.White.toArgb(), getPixel(innerBoxLeft - 1, innerBoxTop - 1))
            assertEquals(Color.White.toArgb(), getPixel(right, innerBoxTop - 1))
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testPainterModifierIntrinsicSize() {
        val paintLatch = CountDownLatch(1)
        rule.runOnUiThreadIR {
            activity.setContent {
                NoMinSizeContainer {
                    NoIntrinsicSizeContainer(
                        LatchPainter(containerWidth, containerHeight, paintLatch).toModifier()
                    ) {
                        // Intentionally empty
                    }
                }
            }
        }

        paintLatch.await()
        obtainScreenshotBitmap(
            containerWidth.roundToInt(),
            containerHeight.roundToInt()
        ).apply {
            assertEquals(Color.Red.toArgb(), getPixel(0, 0))
            assertEquals(Color.Red.toArgb(), getPixel(containerWidth.roundToInt() - 1, 0))
            assertEquals(
                Color.Red.toArgb(), getPixel(
                    containerWidth.roundToInt() - 1,
                    containerHeight.roundToInt() - 1
                )
            )
            assertEquals(Color.Red.toArgb(), getPixel(0, containerHeight.roundToInt() - 1))
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testPainterIntrinsicSizeDoesNotExceedMax() {
        val paintLatch = CountDownLatch(1)
        val containerSize = containerWidth.roundToInt() / 2
        rule.runOnUiThreadIR {
            activity.setContent {
                NoIntrinsicSizeContainer(
                    background(Color.White) +
                            FixedSizeModifier(containerWidth.roundToInt().ipx)
                ) {
                    NoIntrinsicSizeContainer(
                    FixedSizeModifier(containerSize.ipx) +
                            LatchPainter(
                                containerWidth,
                                containerHeight,
                                paintLatch
                            ).toModifier(alignment = Alignment.TopStart)
                    ) {
                        // Intentionally empty
                    }
                }
            }
        }

        paintLatch.await()
        obtainScreenshotBitmap(
            containerWidth.roundToInt(),
            containerHeight.roundToInt()
        ).apply {
            assertEquals(Color.Red.toArgb(), getPixel(0, 0))
            assertEquals(Color.Red.toArgb(), getPixel(containerWidth.roundToInt() / 2 - 1, 0))
            assertEquals(
                Color.White.toArgb(), getPixel(
                    containerWidth.roundToInt() - 1,
                    containerHeight.roundToInt() - 1
                )
            )
            assertEquals(Color.Red.toArgb(), getPixel(0, containerHeight.roundToInt() / 2 - 1))

            assertEquals(Color.White.toArgb(), getPixel(containerWidth.roundToInt() / 2 + 1, 0))
            assertEquals(Color.White.toArgb(), getPixel(containerWidth.roundToInt() / 2 + 1,
                containerHeight.roundToInt() / 2 + 1))
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun testPainterNotSizedToIntrinsics() {
        val paintLatch = CountDownLatch(1)
        val containerSize = containerWidth.roundToInt() / 2
        rule.runOnUiThreadIR {
            activity.setContent {
                NoIntrinsicSizeContainer(
                    background(Color.White) +
                            FixedSizeModifier(containerSize.ipx)
                ) {
                    NoIntrinsicSizeContainer(
                        FixedSizeModifier(containerSize.ipx) +
                        LatchPainter
                            (
                            containerWidth,
                            containerHeight,
                            paintLatch
                        ).toModifier(sizeToIntrinsics = false, alignment = Alignment.TopStart)
                    ) {
                        // Intentionally empty
                    }
                }
            }
        }

        paintLatch.await()
        obtainScreenshotBitmap(
            containerSize,
            containerSize
        ).apply {
            assertEquals(Color.Red.toArgb(), getPixel(0, 0))
            assertEquals(Color.Red.toArgb(), getPixel(containerSize - 1, 0))
            assertEquals(
                Color.Red.toArgb(), getPixel(
                    containerSize - 1,
                    containerSize - 1
                )
            )
            assertEquals(Color.Red.toArgb(), getPixel(0, containerSize - 1))
        }
    }

    @Composable
    private fun testPainter(
        alpha: Float = DefaultAlpha,
        colorFilter: ColorFilter? = null,
        rtl: Boolean = false,
        latch: CountDownLatch
    ) {
        with(DensityAmbient.current) {
            val p = LatchPainter(containerWidth, containerHeight, latch, rtl)
            AtLeastSize(
                modifier = background(Color.White) +
                        p.toModifier(alpha = alpha, colorFilter =
                colorFilter, rtl = rtl),
                size = containerWidth.roundToInt().ipx
            ) {
                // Intentionally empty
            }
        }
    }

    private fun obtainScreenshotBitmap(width: Int, height: Int = width): Bitmap {
        val bitmap = rule.waitAndScreenShot()
        Assert.assertEquals(width, bitmap.width)
        Assert.assertEquals(height, bitmap.height)
        return bitmap
    }

    private class LatchPainter(
        val width: Float,
        val height: Float,
        val latch: CountDownLatch,
        val rtl: Boolean = false
    ) : Painter() {
        override val intrinsicSize: PxSize
            get() = PxSize(
                Px(width),
                Px(height)
            )

        override fun onDraw(canvas: Canvas, bounds: PxSize) {
            val paint = Paint().apply {
                this.color = if (rtl) Color.Blue else Color.Red
            }
            canvas.drawRect(
                Rect.fromLTWH(0.0f, 0.0f, bounds.width.value, bounds.height.value),
                paint
            )
            latch.countDown()
        }
    }

    /**
     * Container composable that relaxes the minimum width and height constraints
     * before giving them to their child
     */
    @Composable
    fun NoMinSizeContainer(children: @Composable() () -> Unit) {
        Layout(children) { measurables, constraints ->
            val loosenedConstraints = constraints.copy(minWidth = 0.ipx, minHeight = 0.ipx)
            val placeables = measurables.map { it.measure(loosenedConstraints) }
            val maxPlaceableWidth = placeables.maxBy { it.width.value }?.width ?: 0.ipx
            val maxPlaceableHeight = placeables.maxBy { it.height.value }?.width ?: 0.ipx
            val width = max(maxPlaceableWidth, loosenedConstraints.minWidth)
            val height = max(maxPlaceableHeight, loosenedConstraints.minHeight)
            layout(width, height) {
                placeables.forEach { it.place(0.ipx, 0.ipx) }
            }
        }
    }

    /**
     * Composable that is sized purely by the constraints given by its modifiers
     */
    @Composable
    fun NoIntrinsicSizeContainer(
        modifier: Modifier = Modifier.None,
        children: @Composable() () -> Unit
    ) {
        Layout(children, modifier) { measurables, constraints ->
            val placeables = measurables.map { it.measure(constraints) }
            val width = max(
                placeables.maxBy { it.width.value }?.width ?: 0.ipx, constraints
                    .minWidth
            )
            val height = max(
                placeables.maxBy { it.height.value }?.height ?: 0.ipx, constraints
                    .minHeight
            )
            layout(width, height) {
                placeables.forEach { it.place(0.ipx, 0.ipx) }
            }
        }
    }

    class FixedSizeModifier(val width: IntPx, val height: IntPx = width) : LayoutModifier {
        override fun ModifierScope.modifySize(
            constraints: Constraints,
            childSize: IntPxSize
        ): IntPxSize = IntPxSize(width, height)

        override fun ModifierScope.modifyConstraints(constraints: Constraints): Constraints =
            Constraints(
                minWidth = width,
                minHeight = height,
                maxWidth = width,
                maxHeight = height
            )
    }
}
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
package androidx.ui.material

import android.os.Build
import androidx.animation.AnimationClockObservable
import androidx.compose.Composable
import androidx.compose.Model
import androidx.compose.Providers
import androidx.compose.mutableStateOf
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.ui.core.TestTag
import androidx.ui.foundation.Box
import androidx.ui.foundation.Clickable
import androidx.ui.foundation.ContentGravity
import androidx.ui.foundation.DrawBackground
import androidx.ui.foundation.shape.RectangleShape
import androidx.ui.geometry.Rect
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.Paint
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutSize
import androidx.ui.layout.Row
import androidx.ui.material.ripple.RippleThemeAmbient
import androidx.ui.material.ripple.RippleEffect
import androidx.ui.material.ripple.RippleEffectFactory
import androidx.ui.material.ripple.RippleTheme
import androidx.ui.material.ripple.ripple
import androidx.ui.test.assertShape
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.doClick
import androidx.ui.test.findByTag
import androidx.ui.unit.Density
import androidx.ui.unit.Dp
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.PxPosition
import androidx.ui.unit.dp
import androidx.ui.unit.px
import androidx.ui.unit.toPxSize
import androidx.ui.unit.toRect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@MediumTest
@RunWith(JUnit4::class)
class RippleTest {

    private val contentTag = "ripple"

    @get:Rule
    val composeTestRule = createComposeRule()

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun rippleDrawsCorrectly() {
        composeTestRule.setMaterialContent {
            DrawRectRippleCallback {
                TestTag(contentTag) {
                    Box(
                        modifier = DrawBackground(Color.Blue),
                        gravity = ContentGravity.Center
                    ) {
                        Box(LayoutSize(10.dp) + ripple())
                    }
                }
            }
        }

        findByTag(contentTag)
            .doClick()

        val bitmap = findByTag(contentTag).captureToBitmap()
        with(composeTestRule.density) {
            bitmap.assertShape(
                density = composeTestRule.density,
                backgroundColor = Color.Blue,
                shape = RectangleShape,
                shapeSizeX = 10.dp.toPx(),
                shapeSizeY = 10.dp.toPx(),
                shapeColor = Color.Red
            )
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun rippleUsesCorrectSize() {
        composeTestRule.setMaterialContent {
            DrawRectRippleCallback {
                TestTag(contentTag) {
                    Box(
                        modifier = DrawBackground(Color.Blue),
                        gravity = ContentGravity.Center
                    ) {
                        Box(
                            LayoutSize(30.dp) +
                                    LayoutPadding(5.dp) +
                                    ripple() +
                                    // this padding should not affect the size of the ripple
                                    LayoutPadding(5.dp)
                        )
                    }
                }
            }
        }

        findByTag(contentTag)
            .doClick()

        val bitmap = findByTag(contentTag).captureToBitmap()
        with(composeTestRule.density) {
            bitmap.assertShape(
                density = composeTestRule.density,
                backgroundColor = Color.Blue,
                shape = RectangleShape,
                shapeSizeX = 20.dp.toPx(),
                shapeSizeY = 20.dp.toPx(),
                shapeColor = Color.Red
            )
        }
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun unboundedIsNotClipped() {
        composeTestRule.setMaterialContent {
            DrawRectRippleCallback {
                TestTag(contentTag) {
                    Box(
                        modifier = DrawBackground(Color.Blue),
                        gravity = ContentGravity.Center
                    ) {
                        Box(LayoutSize(10.dp) + ripple(bounded = false))
                    }
                }
            }
        }

        findByTag(contentTag)
            .doClick()

        val bitmap = findByTag(contentTag).captureToBitmap()
        bitmap.assertShape(
            density = composeTestRule.density,
            backgroundColor = Color.Red,
            shape = RectangleShape,
            shapeSizeX = 0.px,
            shapeSizeY = 0.px,
            shapeColor = Color.Red
        )
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun rippleDrawnAfterContent() {
        composeTestRule.setMaterialContent {
            DrawRectRippleCallback {
                TestTag(contentTag) {
                    Box(
                        modifier = DrawBackground(Color.Blue),
                        gravity = ContentGravity.Center
                    ) {
                        Box(LayoutSize(10.dp) + ripple() + DrawBackground(Color.Blue))
                    }
                }
            }
        }

        findByTag(contentTag)
            .doClick()

        val bitmap = findByTag(contentTag).captureToBitmap()
        with(composeTestRule.density) {
            bitmap.assertShape(
                density = composeTestRule.density,
                backgroundColor = Color.Blue,
                shape = RectangleShape,
                shapeSizeX = 10.dp.toPx(),
                shapeSizeY = 10.dp.toPx(),
                shapeColor = Color.Red
            )
        }
    }

    @Test
    fun twoEffectsDrawnAndDisposedCorrectly() {
        val drawLatch = CountDownLatch(2)
        val disposeLatch = CountDownLatch(2)
        val emit = DoEmit(true)

        composeTestRule.setMaterialContent {
            RippleCallback(
                onDraw = { _, _ -> drawLatch.countDown() },
                onDispose = { disposeLatch.countDown() }
            ) {
                Card {
                    if (emit.emit) {
                        Row {
                            TestTag(tag = contentTag) {
                                RippleButton()
                            }
                        }
                    }
                }
            }
        }

        // create two effects
        findByTag(contentTag)
            .doClick()
        findByTag(contentTag)
            .doClick()

        // wait for drawEffect to be called
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        composeTestRule.runOnUiThread { emit.emit = false }

        // wait for dispose to be called
        assertTrue(disposeLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun rippleColorAndOpacityAreTakenFromTheme() {
        val drawLatch = CountDownLatch(1)
        val color = Color.Yellow
        val opacity = 0.2f
        composeTestRule.setMaterialContent {
            RippleCallback(
                defaultColor = { color },
                opacityCallback = { opacity },
                onDraw = { _, actualColor ->
                    assertEquals(color.copy(alpha = opacity), actualColor)
                    drawLatch.countDown()
                }
            ) {
                TestTag(tag = contentTag) {
                    RippleButton()
                }
            }
        }

        findByTag(contentTag)
            .doClick()

        // wait for drawEffect to be called
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun rippleOpacityIsTakenFromTheme() {
        val drawLatch = CountDownLatch(1)
        val color = Color.Green
        val opacity = 0.2f
        composeTestRule.setMaterialContent {
            RippleCallback(
                opacityCallback = { opacity },
                onDraw = { _, actualColor ->
                    assertEquals(color.copy(alpha = opacity), actualColor)
                    drawLatch.countDown()
                }
            ) {
                TestTag(tag = contentTag) {
                    RippleButton(color = color)
                }
            }
        }

        findByTag(contentTag)
            .doClick()

        // wait for drawEffect to be called
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun disabledRippleDoesntCreateEffects() {
        val createdLatch = CountDownLatch(1)

        composeTestRule.setMaterialContent {
            RippleCallback(
                onEffectCreated = { createdLatch.countDown() }
            ) {
                Card {
                    TestTag(tag = contentTag) {
                        RippleButton(enabled = false)
                    }
                }
            }
        }

        // create two effects
        findByTag(contentTag)
            .doClick()

        // assert no effects has been created
        assertFalse(createdLatch.await(500, TimeUnit.MILLISECONDS))
    }

    @Test
    fun rippleColorChangeWhileAnimatingAppliedCorrectly() {
        var drawLatch = CountDownLatch(1)
        var actualColor = Color.Transparent
        var colorState by mutableStateOf(Color.Yellow)
        composeTestRule.setMaterialContent {
            RippleCallback(
                defaultColor = { colorState },
                onDraw = { _, color ->
                    actualColor = color
                    drawLatch.countDown()
                }
            ) {
                TestTag(tag = contentTag) {
                    RippleButton()
                }
            }
        }

        findByTag(contentTag)
            .doClick()

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertEquals(Color.Yellow, actualColor)

        drawLatch = CountDownLatch(1)
        composeTestRule.runOnUiThread {
            colorState = Color.Green
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertEquals(Color.Green, actualColor)
    }

    @Composable
    private fun RippleButton(size: Dp = 10.dp, color: Color? = null, enabled: Boolean = true) {
        Clickable(
            onClick = {},
            modifier = ripple(bounded = false, color = color, enabled = enabled)
        ) {
            Box(LayoutSize(size))
        }
    }

    @Composable
    fun DrawRectRippleCallback(children: @Composable() () -> Unit) {
        RippleCallback(
            onDraw = { canvas, _ ->
                canvas.drawRect(
                    Rect(-100000f, -100000f, 100000f, 100000f),
                    Paint().apply { this.color = Color.Red })
            },
            children = children
        )
    }

    @Composable
    private fun RippleCallback(
        onDraw: (Canvas, Color) -> Unit = { _, _ -> },
        onDispose: () -> Unit = {},
        onEffectCreated: () -> Unit = {},
        defaultColor: @Composable() () -> Color = { Color(0) },
        opacityCallback: @Composable() () -> Float = { 1f },
        children: @Composable() () -> Unit
    ) {
        val theme = RippleTheme(
            testRippleEffect(onDraw, onDispose, onEffectCreated),
            defaultColor,
            opacityCallback
        )
        Providers(RippleThemeAmbient provides theme, children = children)
    }

    private fun testRippleEffect(
        onDraw: (Canvas, Color) -> Unit,
        onDispose: () -> Unit,
        onEffectCreated: () -> Unit
    ): RippleEffectFactory =
        object : RippleEffectFactory {
            override fun create(
                size: IntPxSize,
                startPosition: PxPosition,
                density: Density,
                radius: Dp?,
                clipped: Boolean,
                clock: AnimationClockObservable,
                requestRedraw: () -> Unit,
                onAnimationFinished: (RippleEffect) -> Unit
            ): RippleEffect {
                onEffectCreated()
                return object : RippleEffect {

                    override fun draw(canvas: Canvas, size: IntPxSize, color: Color) {
                        if (clipped) {
                            canvas.save()
                            canvas.clipRect(size.toPxSize().toRect())
                        }
                        onDraw(canvas, color)
                        if (clipped) {
                            canvas.restore()
                        }
                    }

                    override fun finish(canceled: Boolean) {
                    }

                    override fun dispose() {
                        onDispose()
                    }
                }
            }
        }
}

@Model
private data class DoEmit(var emit: Boolean)
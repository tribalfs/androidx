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

package androidx.ui.core.test

import android.os.Build
import androidx.compose.Composable
import androidx.compose.emptyContent
import androidx.compose.state
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Layout
import androidx.ui.core.Modifier
import androidx.ui.core.TestTag
import androidx.ui.core.TransformOrigin
import androidx.ui.core.drawBehind
import androidx.ui.core.drawLayer
import androidx.ui.core.gesture.pressIndicatorGestureFilter
import androidx.ui.geometry.Rect
import androidx.ui.graphics.Color
import androidx.ui.graphics.Paint
import androidx.ui.graphics.toArgb
import androidx.ui.layout.fillMaxSize
import androidx.ui.layout.offset
import androidx.ui.layout.preferredSize
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.doGesture
import androidx.ui.test.findByTag
import androidx.ui.test.sendTouchDown
import androidx.ui.unit.Px
import androidx.ui.unit.PxPosition
import androidx.ui.unit.dp
import androidx.ui.unit.ipx
import androidx.ui.unit.max
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@MediumTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
@RunWith(JUnit4::class)
class LayerTouchTransformTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun testTransformTouchEventConsumed() {
        val testTag = "transformedComposable"
        var latch: CountDownLatch? = null
        rule.setContent {
            val pressed = state { false }
            val onStart: (PxPosition) -> Unit = {
                pressed.value = true
            }

            val onStop = {
                pressed.value = false
            }

            val color = if (pressed.value) {
                Color.Red
            } else {
                Color.Blue
            }

            val background = Modifier.drawBehind {
                drawRect(
                    Rect.fromLTWH(
                        0.0f,
                        0.0f,
                        size.width.value,
                        size.height.value
                    ),
                    Paint().apply { this.color = Color.Gray }
                )
            }

            val latchDrawModifier = Modifier.drawBehind { latch?.countDown() }

            val containerDp = (200.0f / DensityAmbient.current.density).dp
            val boxDp = (50.0f / DensityAmbient.current.density).dp

            val offsetX = (270.0f / DensityAmbient.current.density).dp
            val offsetY = (120.0f / DensityAmbient.current.density).dp
            TestTag(testTag) {
                SimpleLayout(
                    modifier = Modifier.fillMaxSize().offset(offsetX, offsetY)
                ) {
                    SimpleLayout(modifier = background + Modifier.preferredSize(containerDp)) {
                        SimpleLayout(modifier =
                            Modifier.drawLayer(
                                translationX = 50.0f,
                                translationY = 30.0f,
                                rotationZ = 45.0f,
                                scaleX = 2.0f,
                                scaleY = 0.5f,
                                transformOrigin =
                                    TransformOrigin(1.0f, 1.0f)
                            ).drawBehind {
                                val paint = Paint().apply { this.color = color }
                                drawRect(
                                    Rect.fromLTWH(
                                        0.0f,
                                        0.0f,
                                        size.width.value,
                                        size.height.value
                                    ),
                                    paint
                                )
                            }
                                .plus(latchDrawModifier)
                                .preferredSize(boxDp)
                                .pressIndicatorGestureFilter(onStart, onStop, onStop)
                            )
                    }
                }
            }
        }

        // Touch position outside the bounds of the target composable
        // however, after transformations, this point will be within
        // its bounds

        val mappedPosition = PxPosition(Px(342.0f), Px(168.0f))
        val node = findByTag(testTag).doGesture { sendTouchDown(mappedPosition) }

        latch = CountDownLatch(1).apply {
            await(5, TimeUnit.SECONDS)
        }

        node.captureToBitmap().apply {
            Assert.assertEquals(
                Color.Red.toArgb(),
                getPixel(
                    mappedPosition.x.value.toInt(),
                    mappedPosition.y.value.toInt()
                )
            )
        }
    }
}

@Composable
fun SimpleLayout(modifier: Modifier, children: @Composable() () -> Unit = emptyContent()) {
    Layout(
        children,
        modifier
    ) { measurables, constraints, _ ->
        val childConstraints = constraints.copy(minWidth = 0.ipx, minHeight = 0.ipx)
        val placeables = measurables.map { it.measure(childConstraints) }
        var containerWidth = constraints.minWidth
        var containerHeight = constraints.minHeight
        placeables.forEach {
            containerWidth = max(containerWidth, it.width)
            containerHeight = max(containerHeight, it.height)
        }
        layout(containerWidth, containerHeight) {
            placeables.forEach {
                it.place(0.ipx, 0.ipx)
            }
        }
    }
}
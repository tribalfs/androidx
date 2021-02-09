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

package androidx.compose.foundation.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.Ref
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalLayout::class)
class IntrinsicTest : LayoutTest() {
    @Test
    fun testMinIntrinsicWidth() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val minIntrinsicWidthSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                FixedIntrinsicsBox(
                    Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                        minIntrinsicWidthSize.value = coordinates.size
                        positionedLatch.countDown()
                    }
                        .width(IntrinsicSize.Min).saveLayoutInfo(
                            size = childSize,
                            position = childPosition,
                            positionedLatch = positionedLatch
                        ),
                    10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                )
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(10.dp.roundToPx(), 50.dp.roundToPx()), minIntrinsicWidthSize.value)
        assertEquals(IntSize(10.dp.roundToPx(), 50.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMinIntrinsicHeight() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val minIntrinsicHeightSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                FixedIntrinsicsBox(
                    Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                        minIntrinsicHeightSize.value = coordinates.size
                        positionedLatch.countDown()
                    }.height(IntrinsicSize.Min).saveLayoutInfo(
                        size = childSize,
                        position = childPosition,
                        positionedLatch = positionedLatch
                    ),
                    10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                )
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(20.dp.roundToPx(), 40.dp.roundToPx()), minIntrinsicHeightSize.value)
        assertEquals(IntSize(20.dp.roundToPx(), 40.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMaxIntrinsicWidth() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val maxIntrinsicWidthSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                FixedIntrinsicsBox(
                    Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                        maxIntrinsicWidthSize.value = coordinates.size
                        positionedLatch.countDown()
                    }.width(IntrinsicSize.Max).saveLayoutInfo(
                        size = childSize,
                        position = childPosition,
                        positionedLatch = positionedLatch
                    ),
                    10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                )
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(30.dp.roundToPx(), 50.dp.roundToPx()), maxIntrinsicWidthSize.value)
        assertEquals(IntSize(30.dp.roundToPx(), 50.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMaxIntrinsicHeight() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val maxIntrinsicHeightSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                FixedIntrinsicsBox(
                    Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                        maxIntrinsicHeightSize.value = coordinates.size
                        positionedLatch.countDown()
                    }.height(IntrinsicSize.Max).saveLayoutInfo(
                        size = childSize,
                        position = childPosition,
                        positionedLatch = positionedLatch
                    ),
                    10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                )
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(20.dp.roundToPx(), 60.dp.roundToPx()), maxIntrinsicHeightSize.value)
        assertEquals(IntSize(20.dp.roundToPx(), 60.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMinIntrinsicWidth_respectsIncomingMaxConstraints() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val minIntrinsicWidthSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                ConstrainedBox(DpConstraints(maxWidth = 5.dp)) {
                    FixedIntrinsicsBox(
                        Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                            minIntrinsicWidthSize.value = coordinates.size
                            positionedLatch.countDown()
                        }.width(IntrinsicSize.Min).saveLayoutInfo(
                            size = childSize,
                            position = childPosition,
                            positionedLatch = positionedLatch
                        ),
                        10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                    )
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(5.dp.roundToPx(), 50.dp.roundToPx()), minIntrinsicWidthSize.value)
        assertEquals(IntSize(5.dp.roundToPx(), 50.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMinIntrinsicWidth_respectsIncomingMinConstraints() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val minIntrinsicWidthSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                ConstrainedBox(DpConstraints(minWidth = 15.dp)) {
                    FixedIntrinsicsBox(
                        Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                            minIntrinsicWidthSize.value = coordinates.size
                            positionedLatch.countDown()
                        }.width(IntrinsicSize.Min).saveLayoutInfo(
                            size = childSize,
                            position = childPosition,
                            positionedLatch = positionedLatch
                        ),
                        10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                    )
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(15.dp.roundToPx(), 50.dp.roundToPx()), minIntrinsicWidthSize.value)
        assertEquals(IntSize(15.dp.roundToPx(), 50.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMinIntrinsicHeight_respectsMaxIncomingConstraints() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val minIntrinsicHeightSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                ConstrainedBox(
                    DpConstraints(maxHeight = 35.dp),
                    modifier = Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                        minIntrinsicHeightSize.value = coordinates.size
                        positionedLatch.countDown()
                    }
                ) {
                    FixedIntrinsicsBox(
                        Modifier.height(IntrinsicSize.Min).saveLayoutInfo(
                            size = childSize,
                            position = childPosition,
                            positionedLatch = positionedLatch
                        ),
                        10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                    )
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(20.dp.roundToPx(), 35.dp.roundToPx()), minIntrinsicHeightSize.value)
        assertEquals(IntSize(20.dp.roundToPx(), 35.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMinIntrinsicHeight_respectsMinIncomingConstraints() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val minIntrinsicHeightSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                ConstrainedBox(
                    DpConstraints(minHeight = 45.dp),
                    modifier = Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                        minIntrinsicHeightSize.value = coordinates.size
                        positionedLatch.countDown()
                    }
                ) {
                    FixedIntrinsicsBox(
                        Modifier.height(IntrinsicSize.Min).saveLayoutInfo(
                            size = childSize,
                            position = childPosition,
                            positionedLatch = positionedLatch
                        ),
                        10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                    )
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(20.dp.roundToPx(), 45.dp.roundToPx()), minIntrinsicHeightSize.value)
        assertEquals(IntSize(20.dp.roundToPx(), 45.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMaxIntrinsicWidth_respectsMaxIncomingConstraints() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val maxIntrinsicWidthSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                ConstrainedBox(
                    DpConstraints(maxWidth = 25.dp),
                    modifier = Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                        maxIntrinsicWidthSize.value = coordinates.size
                        positionedLatch.countDown()
                    }
                ) {
                    FixedIntrinsicsBox(
                        Modifier.width(IntrinsicSize.Max).saveLayoutInfo(
                            size = childSize,
                            position = childPosition,
                            positionedLatch = positionedLatch
                        ),
                        10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                    )
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(25.dp.roundToPx(), 50.dp.roundToPx()), maxIntrinsicWidthSize.value)
        assertEquals(IntSize(25.dp.roundToPx(), 50.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMaxIntrinsicWidth_respectsMinIncomingConstraints() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val maxIntrinsicWidthSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                ConstrainedBox(
                    DpConstraints(minWidth = 35.dp),
                    modifier = Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                        maxIntrinsicWidthSize.value = coordinates.size
                        positionedLatch.countDown()
                    }
                ) {
                    FixedIntrinsicsBox(
                        Modifier.width(IntrinsicSize.Max).saveLayoutInfo(
                            size = childSize,
                            position = childPosition,
                            positionedLatch = positionedLatch
                        ),
                        10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                    )
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(35.dp.roundToPx(), 50.dp.roundToPx()), maxIntrinsicWidthSize.value)
        assertEquals(IntSize(35.dp.roundToPx(), 50.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMaxIntrinsicHeight_respectsMaxIncomingConstraints() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val maxIntrinsicHeightSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                ConstrainedBox(
                    DpConstraints(maxHeight = 55.dp),
                    modifier = Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                        maxIntrinsicHeightSize.value = coordinates.size
                        positionedLatch.countDown()
                    }
                ) {
                    FixedIntrinsicsBox(
                        Modifier.height(IntrinsicSize.Max).saveLayoutInfo(
                            size = childSize,
                            position = childPosition,
                            positionedLatch = positionedLatch
                        ),
                        10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                    )
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(20.dp.roundToPx(), 55.dp.roundToPx()), maxIntrinsicHeightSize.value)
        assertEquals(IntSize(20.dp.roundToPx(), 55.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMaxIntrinsicHeight_respectsMinIncomingConstraints() = with(density) {
        val positionedLatch = CountDownLatch(2)
        val maxIntrinsicHeightSize = Ref<IntSize>()
        val childSize = Ref<IntSize>()
        val childPosition = Ref<Offset>()
        show {
            Box {
                ConstrainedBox(
                    DpConstraints(minHeight = 65.dp),
                    modifier = Modifier.onGloballyPositioned { coordinates: LayoutCoordinates ->
                        maxIntrinsicHeightSize.value = coordinates.size
                        positionedLatch.countDown()
                    }
                ) {
                    FixedIntrinsicsBox(
                        Modifier.height(IntrinsicSize.Max).saveLayoutInfo(
                            size = childSize,
                            position = childPosition,
                            positionedLatch = positionedLatch
                        ),
                        10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
                    )
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntSize(20.dp.roundToPx(), 65.dp.roundToPx()), maxIntrinsicHeightSize.value)
        assertEquals(IntSize(20.dp.roundToPx(), 65.dp.roundToPx()), childSize.value)
        assertEquals(Offset(0f, 0f), childPosition.value)
    }

    @Test
    fun testMinIntrinsicWidth_intrinsicMeasurements() = with(density) {
        testIntrinsics({
            FixedIntrinsicsBox(
                Modifier.width(IntrinsicSize.Min), 10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
            )
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            assertEquals(10.dp.roundToPx(), minIntrinsicWidth(0))
            assertEquals(40.dp.roundToPx(), minIntrinsicHeight(0))
            assertEquals(10.dp.roundToPx(), maxIntrinsicWidth(0))
            assertEquals(60.dp.roundToPx(), maxIntrinsicHeight(0))
        }
    }

    @Test
    fun testMinIntrinsicHeight_intrinsicMeasurements() = with(density) {
        testIntrinsics({
            FixedIntrinsicsBox(
                Modifier.height(IntrinsicSize.Min),
                10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
            )
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            assertEquals(10.dp.roundToPx(), minIntrinsicWidth(0))
            assertEquals(40.dp.roundToPx(), minIntrinsicHeight(0))
            assertEquals(30.dp.roundToPx(), maxIntrinsicWidth(0))
            assertEquals(40.dp.roundToPx(), maxIntrinsicHeight(0))
        }
    }

    @Test
    fun testMaxIntrinsicWidth_intrinsicMeasurements() = with(density) {
        testIntrinsics({
            FixedIntrinsicsBox(
                Modifier.width(IntrinsicSize.Max), 10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
            )
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            assertEquals(30.dp.roundToPx(), minIntrinsicWidth(0))
            assertEquals(40.dp.roundToPx(), minIntrinsicHeight(0))
            assertEquals(30.dp.roundToPx(), maxIntrinsicWidth(0))
            assertEquals(60.dp.roundToPx(), maxIntrinsicHeight(0))
        }
    }

    @Test
    fun testMaxIntrinsicHeight_intrinsicMeasurements() = with(density) {
        testIntrinsics({
            FixedIntrinsicsBox(
                Modifier.height(IntrinsicSize.Max),
                10.dp, 20.dp, 30.dp, 40.dp, 50.dp, 60.dp
            )
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            assertEquals(10.dp.roundToPx(), minIntrinsicWidth(0))
            assertEquals(60.dp.roundToPx(), minIntrinsicHeight(0))
            assertEquals(30.dp.roundToPx(), maxIntrinsicWidth(0))
            assertEquals(60.dp.roundToPx(), maxIntrinsicHeight(0))
        }
    }
}

@Composable
private fun FixedIntrinsicsBox(
    modifier: Modifier = Modifier,
    minIntrinsicWidth: Dp,
    width: Dp,
    maxIntrinsicWidth: Dp,
    minIntrinsicHeight: Dp,
    height: Dp,
    maxIntrinsicHeight: Dp
) {
    Layout(
        {},
        minIntrinsicWidthMeasureBlock = { _, _ -> minIntrinsicWidth.roundToPx() },
        minIntrinsicHeightMeasureBlock = { _, _ -> minIntrinsicHeight.roundToPx() },
        maxIntrinsicWidthMeasureBlock = { _, _ -> maxIntrinsicWidth.roundToPx() },
        maxIntrinsicHeightMeasureBlock = { _, _ -> maxIntrinsicHeight.roundToPx() },
        modifier = modifier
    ) { _, constraints ->
        layout(
            constraints.constrainWidth(width.roundToPx()),
            constraints.constrainHeight(height.roundToPx())
        ) {}
    }
}

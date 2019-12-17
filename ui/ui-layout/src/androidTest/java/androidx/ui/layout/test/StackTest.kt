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

package androidx.ui.layout.test

import androidx.compose.Composable
import androidx.test.filters.SmallTest
import androidx.ui.core.Alignment
import androidx.ui.core.IntPx
import androidx.ui.core.OnChildPositioned
import androidx.ui.core.PxPosition
import androidx.ui.core.PxSize
import androidx.ui.core.Ref
import androidx.ui.core.dp
import androidx.ui.core.ipx
import androidx.ui.core.px
import androidx.ui.core.withDensity
import androidx.ui.layout.Align
import androidx.ui.layout.Container
import androidx.ui.layout.DpConstraints
import androidx.ui.layout.LayoutAspectRatio
import androidx.ui.layout.LayoutExpanded
import androidx.ui.layout.LayoutGravity
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutSize
import androidx.ui.layout.Stack
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
@RunWith(JUnit4::class)
class StackTest : LayoutTest() {
    @Test
    fun testStack() = withDensity(density) {
        val sizeDp = 50.dp
        val size = sizeDp.toIntPx()

        val positionedLatch = CountDownLatch(3)
        val stackSize = Ref<PxSize>()
        val alignedChildSize = Ref<PxSize>()
        val alignedChildPosition = Ref<PxPosition>()
        val positionedChildSize = Ref<PxSize>()
        val positionedChildPosition = Ref<PxPosition>()
        show {
            Align(alignment = Alignment.TopLeft) {
                OnChildPositioned(onPositioned = { coordinates ->
                    stackSize.value = coordinates.size
                    positionedLatch.countDown()
                }) {
                    Stack {
                        Container(LayoutGravity.BottomRight, width = sizeDp, height = sizeDp) {
                            SaveLayoutInfo(
                                size = alignedChildSize,
                                position = alignedChildPosition,
                                positionedLatch = positionedLatch
                            )
                        }

                        Container(LayoutGravity.Stretch + LayoutPadding(10.dp)) {
                            SaveLayoutInfo(
                                size = positionedChildSize,
                                position = positionedChildPosition,
                                positionedLatch = positionedLatch
                            )
                        }
                    }
                }
            }
        }
        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(PxSize(size, size), stackSize.value)
        assertEquals(PxSize(size, size), alignedChildSize.value)
        assertEquals(PxPosition(0.px, 0.px), alignedChildPosition.value)
        assertEquals(PxSize(30.dp.toIntPx(), 30.dp.toIntPx()), positionedChildSize.value)
        assertEquals(PxPosition(10.dp.toIntPx(), 10.dp.toIntPx()), positionedChildPosition.value)
    }

    @Test
    fun testStack_withMultipleAlignedChildren() = withDensity(density) {
        val size = 250.ipx
        val sizeDp = size.toDp()
        val doubleSizeDp = sizeDp * 2
        val doubleSize = (sizeDp * 2).toIntPx()

        val positionedLatch = CountDownLatch(3)
        val stackSize = Ref<PxSize>()
        val childSize = arrayOf(Ref<PxSize>(), Ref<PxSize>())
        val childPosition = arrayOf(Ref<PxPosition>(), Ref<PxPosition>())
        show {
            Align(alignment = Alignment.TopLeft) {
                OnChildPositioned(onPositioned = { coordinates ->
                    stackSize.value = coordinates.size
                    positionedLatch.countDown()
                }) {
                    Stack {
                        Container(
                            modifier = LayoutGravity.BottomRight, width = sizeDp, height = sizeDp
                        ) {
                            SaveLayoutInfo(
                                size = childSize[0],
                                position = childPosition[0],
                                positionedLatch = positionedLatch
                            )
                        }
                        Container(
                            modifier = LayoutGravity.BottomRight,
                            width = doubleSizeDp,
                            height = doubleSizeDp
                        ) {
                            SaveLayoutInfo(
                                size = childSize[1],
                                position = childPosition[1],
                                positionedLatch = positionedLatch
                            )
                        }
                    }
                }
            }
        }
        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(PxSize(doubleSize, doubleSize), stackSize.value)
        assertEquals(PxSize(size, size), childSize[0].value)
        assertEquals(PxPosition(size, size), childPosition[0].value)
        assertEquals(PxSize(doubleSize, doubleSize), childSize[1].value)
        assertEquals(PxPosition(0.px, 0.px), childPosition[1].value)
    }

    @Test
    fun testStack_withStretchChildren() = withDensity(density) {
        val size = 250.ipx
        val sizeDp = size.toDp()
        val halfSizeDp = sizeDp / 2
        val inset = 50.ipx
        val insetDp = inset.toDp()

        val positionedLatch = CountDownLatch(6)
        val stackSize = Ref<PxSize>()
        val childSize = Array(5) { Ref<PxSize>() }
        val childPosition = Array(5) { Ref<PxPosition>() }
        show {
            Align(alignment = Alignment.TopLeft) {
                OnChildPositioned(onPositioned = { coordinates ->
                    stackSize.value = coordinates.size
                    positionedLatch.countDown()
                }) {
                    Stack {
                        Container(LayoutGravity.Center, width = sizeDp, height = sizeDp) {
                            SaveLayoutInfo(
                                size = childSize[0],
                                position = childPosition[0],
                                positionedLatch = positionedLatch
                            )
                        }
                        Container(
                            LayoutGravity.Stretch + LayoutPadding(
                                left = insetDp,
                                top = insetDp
                            ),
                            width = halfSizeDp,
                            height = halfSizeDp
                        ) {
                            SaveLayoutInfo(
                                size = childSize[1],
                                position = childPosition[1],
                                positionedLatch = positionedLatch
                            )
                        }
                        Container(
                            LayoutGravity.Stretch + LayoutPadding(
                                right = insetDp,
                                bottom = insetDp
                            ),
                            width = halfSizeDp,
                            height = halfSizeDp
                        ) {
                            SaveLayoutInfo(
                                size = childSize[2],
                                position = childPosition[2],
                                positionedLatch = positionedLatch
                            )
                        }
                        Container(
                            LayoutGravity.Stretch + LayoutPadding(
                                left = insetDp,
                                right = insetDp
                            ),
                            width = halfSizeDp,
                            height = halfSizeDp) {
                            SaveLayoutInfo(
                                size = childSize[3],
                                position = childPosition[3],
                                positionedLatch = positionedLatch
                            )
                        }
                        Container(
                            LayoutGravity.Stretch + LayoutPadding(
                                top = insetDp,
                                bottom = insetDp
                            ),
                            width = halfSizeDp,
                            height = halfSizeDp
                        ) {
                            SaveLayoutInfo(
                                size = childSize[4],
                                position = childPosition[4],
                                positionedLatch = positionedLatch
                            )
                        }
                    }
                }
            }
        }
        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(PxSize(size, size), stackSize.value)
        assertEquals(PxSize(size, size), childSize[0].value)
        assertEquals(PxPosition(0.px, 0.px), childPosition[0].value)
        assertEquals(PxSize(size - inset, size - inset), childSize[1].value)
        assertEquals(PxPosition(inset, inset), childPosition[1].value)
        assertEquals(PxSize(size - inset, size - inset), childSize[2].value)
        assertEquals(PxPosition(0.px, 0.px), childPosition[2].value)
        assertEquals(PxSize(size - inset * 2, size), childSize[3].value)
        assertEquals(PxPosition(inset, 0.ipx), childPosition[3].value)
        assertEquals(PxSize(size, size - inset * 2), childSize[4].value)
        assertEquals(PxPosition(0.ipx, inset), childPosition[4].value)
    }

    @Test
    fun testStack_expanded() = withDensity(density) {
        val size = 250.ipx
        val sizeDp = size.toDp()
        val halfSize = 125.ipx
        val halfSizeDp = halfSize.toDp()

        val positionedLatch = CountDownLatch(3)
        val stackSize = Ref<PxSize>()
        val childSize = Array(2) { Ref<PxSize>() }
        val childPosition = Array(2) { Ref<PxPosition>() }
        show {
            Align(alignment = Alignment.TopLeft) {
                OnChildPositioned(onPositioned = { coordinates ->
                    stackSize.value = coordinates.size
                    positionedLatch.countDown()
                }) {
                    Container(LayoutSize(sizeDp, sizeDp)) {
                        Stack {
                            Container(LayoutExpanded) {
                                SaveLayoutInfo(
                                    size = childSize[0],
                                    position = childPosition[0],
                                    positionedLatch = positionedLatch
                                )
                            }
                            Container(
                                LayoutGravity.BottomRight,
                                width = halfSizeDp,
                                height = halfSizeDp
                            ) {
                                SaveLayoutInfo(
                                    size = childSize[1],
                                    position = childPosition[1],
                                    positionedLatch = positionedLatch
                                )
                            }
                        }
                    }
                }
            }
        }
        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(PxSize(size, size), stackSize.value)
        assertEquals(PxSize(size, size), childSize[0].value)
        assertEquals(PxPosition(0.px, 0.px), childPosition[0].value)
        assertEquals(PxSize(halfSize, halfSize), childSize[1].value)
        assertEquals(PxPosition(size - halfSize, size - halfSize), childPosition[1].value)
    }

    @Test
    fun testStack_hasCorrectIntrinsicMeasurements() = withDensity(density) {
        val testWidth = 90.ipx.toDp()
        val testHeight = 80.ipx.toDp()

        val testDimension = 200.ipx
        // When measuring the height with testDimension, width should be double
        val expectedWidth = testDimension * 2
        // When measuring the width with testDimension, height should be half
        val expectedHeight = testDimension / 2

        testIntrinsics(@Composable {
            Stack {
                Container(LayoutGravity.TopLeft + LayoutAspectRatio(2f)) { }
                ConstrainedBox(
                    DpConstraints.tightConstraints(testWidth, testHeight),
                    LayoutGravity.BottomCenter
                ) { }
                ConstrainedBox(
                    DpConstraints.tightConstraints(200.dp, 200.dp),
                    LayoutGravity.Stretch + LayoutPadding(10.dp)
                ) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(testWidth.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(expectedWidth, minIntrinsicWidth(testDimension))
            assertEquals(testWidth.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(testHeight.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(expectedHeight, minIntrinsicHeight(testDimension))
            assertEquals(testHeight.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(testWidth.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(expectedWidth, maxIntrinsicWidth(testDimension))
            assertEquals(testWidth.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(testHeight.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(expectedHeight, maxIntrinsicHeight(testDimension))
            assertEquals(testHeight.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testStack_hasCorrectIntrinsicMeasurements_withNoAlignedChildren() = withDensity(density) {
        testIntrinsics(@Composable {
            Stack {
                ConstrainedBox(
                    modifier = LayoutGravity.Stretch + LayoutPadding(10.dp),
                    constraints = DpConstraints.tightConstraints(200.dp, 200.dp)
                ) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }
}

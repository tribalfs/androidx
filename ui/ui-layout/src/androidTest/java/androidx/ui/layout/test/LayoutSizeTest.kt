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

import android.content.res.Resources
import androidx.compose.Composable
import androidx.test.filters.SmallTest
import androidx.ui.core.Layout
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.Modifier
import androidx.ui.core.Ref
import androidx.ui.core.onPositioned
import androidx.ui.layout.Column
import androidx.ui.layout.Row
import androidx.ui.layout.Stack
import androidx.ui.layout.aspectRatio
import androidx.ui.layout.fillMaxHeight
import androidx.ui.layout.fillMaxSize
import androidx.ui.layout.fillMaxWidth
import androidx.ui.layout.preferredHeight
import androidx.ui.layout.preferredHeightIn
import androidx.ui.layout.preferredSize
import androidx.ui.layout.preferredSizeIn
import androidx.ui.layout.preferredWidth
import androidx.ui.layout.preferredWidthIn
import androidx.ui.unit.Dp
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.PxPosition
import androidx.ui.unit.dp
import androidx.ui.unit.ipx
import androidx.ui.unit.min
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
@RunWith(JUnit4::class)
class LayoutSizeTest : LayoutTest() {

    @Test
    fun testSize_withWidthSizeModifiers() = with(density) {
        val sizeDp = 50.dp
        val sizeIpx = sizeDp.toIntPx()

        val positionedLatch = CountDownLatch(6)
        val size = MutableList(6) { Ref<IntPxSize>() }
        val position = MutableList(6) { Ref<PxPosition>() }
        show {
            Stack {
                Column {
                    Container(
                        Modifier.preferredWidthIn(minWidth = sizeDp, maxWidth = sizeDp * 2)
                            .preferredHeight(sizeDp)
                            .saveLayoutInfo(size[0], position[0], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredWidthIn(maxWidth = sizeDp * 2)
                            .preferredHeight(sizeDp)
                            .saveLayoutInfo(size[1], position[1], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredWidthIn(minWidth = sizeDp)
                            .preferredHeight(sizeDp)
                            .saveLayoutInfo(size[2], position[2], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredWidthIn(maxWidth = sizeDp)
                            .preferredWidthIn(minWidth = sizeDp * 2)
                            .preferredHeight(sizeDp)
                            .saveLayoutInfo(size[3], position[3], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredWidthIn(minWidth = sizeDp * 2)
                            .preferredWidthIn(maxWidth = sizeDp)
                            .preferredHeight(sizeDp)
                            .saveLayoutInfo(size[4], position[4], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredSize(sizeDp)
                            .saveLayoutInfo(size[5], position[5], positionedLatch)
                    ) {
                    }
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[0].value)
        assertEquals(PxPosition.Origin, position[0].value)

        assertEquals(IntPxSize(0.ipx, sizeIpx), size[1].value)
        assertEquals(PxPosition(0.ipx, sizeIpx), position[1].value)

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[2].value)
        assertEquals(PxPosition(0.ipx, sizeIpx * 2), position[2].value)

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[3].value)
        assertEquals(PxPosition(0.ipx, sizeIpx * 3), position[3].value)

        assertEquals(IntPxSize((sizeDp * 2).toIntPx(), sizeIpx), size[4].value)
        assertEquals(PxPosition(0.ipx, sizeIpx * 4), position[4].value)

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[5].value)
        assertEquals(PxPosition(0.ipx, sizeIpx * 5), position[5].value)
    }

    @Test
    fun testSize_withHeightSizeModifiers() = with(density) {
        val sizeDp = 10.dp
        val sizeIpx = sizeDp.toIntPx()

        val positionedLatch = CountDownLatch(6)
        val size = MutableList(6) { Ref<IntPxSize>() }
        val position = MutableList(6) { Ref<PxPosition>() }
        show {
            Stack {
                Row {
                    Container(
                        Modifier.preferredHeightIn(minHeight = sizeDp, maxHeight = sizeDp * 2)
                            .preferredWidth(sizeDp)
                            .saveLayoutInfo(size[0], position[0], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredHeightIn(maxHeight = sizeDp * 2)
                            .preferredWidth(sizeDp)
                            .saveLayoutInfo(size[1], position[1], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredHeightIn(minHeight = sizeDp)
                            .preferredWidth(sizeDp)
                            .saveLayoutInfo(size[2], position[2], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredHeightIn(maxHeight = sizeDp)
                            .preferredHeightIn(minHeight = sizeDp * 2)
                            .preferredWidth(sizeDp)
                            .saveLayoutInfo(size[3], position[3], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredHeightIn(minHeight = sizeDp * 2)
                            .preferredHeightIn(maxHeight = sizeDp)
                            .preferredWidth(sizeDp)
                            .saveLayoutInfo(size[4], position[4], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredHeight(sizeDp) + Modifier.preferredWidth(sizeDp) +
                                Modifier.saveLayoutInfo(size[5], position[5], positionedLatch)
                    ) {
                    }
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[0].value)
        assertEquals(PxPosition.Origin, position[0].value)

        assertEquals(IntPxSize(sizeIpx, 0.ipx), size[1].value)
        assertEquals(PxPosition(sizeIpx, 0.ipx), position[1].value)

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[2].value)
        assertEquals(PxPosition(sizeIpx * 2, 0.ipx), position[2].value)

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[3].value)
        assertEquals(PxPosition(sizeIpx * 3, 0.ipx), position[3].value)

        assertEquals(IntPxSize(sizeIpx, (sizeDp * 2).toIntPx()), size[4].value)
        assertEquals(PxPosition(sizeIpx * 4, 0.ipx), position[4].value)

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[5].value)
        assertEquals(PxPosition(sizeIpx * 5, 0.ipx), position[5].value)
    }

    @Test
    fun testSize_withSizeModifiers() = with(density) {
        val sizeDp = 50.dp
        val sizeIpx = sizeDp.toIntPx()

        val positionedLatch = CountDownLatch(5)
        val size = MutableList(5) { Ref<IntPxSize>() }
        val position = MutableList(5) { Ref<PxPosition>() }
        show {
            Stack {
                Row {
                    val maxSize = sizeDp * 2
                    Container(
                        Modifier.preferredSizeIn(maxWidth = maxSize, maxHeight = maxSize)
                            .preferredSizeIn(minWidth = sizeDp, minHeight = sizeDp)
                            .saveLayoutInfo(size[0], position[0], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredSizeIn(maxWidth = sizeDp, maxHeight = sizeDp)
                            .preferredSizeIn(minWidth = sizeDp * 2, minHeight = sizeDp)
                            .saveLayoutInfo(size[1], position[1], positionedLatch)
                    ) {
                    }
                    val maxSize1 = sizeDp * 2
                    Container(
                        Modifier.preferredSizeIn(minWidth = sizeDp, minHeight = sizeDp)
                            .preferredSizeIn(maxWidth = maxSize1, maxHeight = maxSize1)
                            .saveLayoutInfo(size[2], position[2], positionedLatch)
                    ) {
                    }
                    val minSize = sizeDp * 2
                    Container(
                        Modifier.preferredSizeIn(minWidth = minSize, minHeight = minSize)
                            .preferredSizeIn(maxWidth = sizeDp, maxHeight = sizeDp)
                            .saveLayoutInfo(size[3], position[3], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredSize(sizeDp)
                            .saveLayoutInfo(size[4], position[4], positionedLatch)
                    ) {
                    }
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[0].value)
        assertEquals(PxPosition.Origin, position[0].value)

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[1].value)
        assertEquals(PxPosition(sizeIpx, 0.ipx), position[1].value)

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[2].value)
        assertEquals(PxPosition(sizeIpx * 2, 0.ipx), position[2].value)

        assertEquals(IntPxSize((sizeDp * 2).toIntPx(), (sizeDp * 2).toIntPx()), size[3].value)
        assertEquals(PxPosition(sizeIpx * 3, 0.ipx), position[3].value)

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[4].value)
        assertEquals(PxPosition((sizeDp * 5).toIntPx(), 0.ipx), position[4].value)
    }

    @Test
    fun testSizeModifiers_respectMaxConstraint() = with(density) {
        val sizeDp = 100.dp
        val size = sizeDp.toIntPx()

        val positionedLatch = CountDownLatch(2)
        val constrainedBoxSize = Ref<IntPxSize>()
        val childSize = Ref<IntPxSize>()
        val childPosition = Ref<PxPosition>()
        show {
            Stack {
                Container(width = sizeDp, height = sizeDp) {
                    Container(
                        Modifier.preferredWidth(sizeDp * 2)
                            .preferredHeight(sizeDp * 3)
                            .onPositioned { coordinates: LayoutCoordinates ->
                                constrainedBoxSize.value = coordinates.size
                                positionedLatch.countDown()
                            }
                    ) {
                        Container(expanded = true,
                            modifier = Modifier.saveLayoutInfo(
                                size = childSize,
                                position = childPosition,
                                positionedLatch = positionedLatch
                            )
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntPxSize(size, size), constrainedBoxSize.value)
        assertEquals(IntPxSize(size, size), childSize.value)
        assertEquals(PxPosition.Origin, childPosition.value)
    }

    @Test
    fun testMaxModifiers_withInfiniteValue() = with(density) {
        val sizeDp = 20.dp
        val sizeIpx = sizeDp.toIntPx()

        val positionedLatch = CountDownLatch(4)
        val size = MutableList(4) { Ref<IntPxSize>() }
        val position = MutableList(4) { Ref<PxPosition>() }
        show {
            Stack {
                Row {
                    Container(Modifier.preferredWidthIn(maxWidth = Dp.Infinity)) {
                        Container(width = sizeDp, height = sizeDp,
                            modifier = Modifier.saveLayoutInfo(size[0], position[0],
                                positionedLatch)
                        ) {
                        }
                    }
                    Container(Modifier.preferredHeightIn(maxHeight = Dp.Infinity)) {
                        Container(width = sizeDp, height = sizeDp,
                            modifier = Modifier.saveLayoutInfo(
                                size[1],
                                position[1],
                                positionedLatch
                            )
                        ) {
                        }
                    }
                    Container(
                        Modifier.preferredWidth(sizeDp)
                            .preferredHeight(sizeDp)
                            .preferredWidthIn(maxWidth = Dp.Infinity)
                            .preferredHeightIn(maxHeight = Dp.Infinity)
                            .saveLayoutInfo(size[2], position[2], positionedLatch)
                    ) {
                    }
                    Container(
                        Modifier.preferredSizeIn(
                            maxWidth = Dp.Infinity,
                            maxHeight = Dp.Infinity
                        )
                    ) {
                        Container(
                            width = sizeDp,
                            height = sizeDp,
                            modifier = Modifier.saveLayoutInfo(
                                size[3],
                                position[3],
                                positionedLatch
                            )
                        ) {
                        }
                    }
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[0].value)
        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[1].value)
        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[2].value)
        assertEquals(IntPxSize(sizeIpx, sizeIpx), size[3].value)
    }

    @Test
    fun testMinWidthModifier_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Container(Modifier.preferredWidthIn(minWidth = 10.dp)) {
                Container(Modifier.aspectRatio(1f)) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(10.dp.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), minIntrinsicWidth(5.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(35.dp.toIntPx(), minIntrinsicHeight(35.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(10.dp.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), maxIntrinsicWidth(5.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(35.dp.toIntPx(), maxIntrinsicHeight(35.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testMaxWidthModifier_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Container(Modifier.preferredWidthIn(maxWidth = 20.dp)) {
                Container(Modifier.aspectRatio(1f)) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), minIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(20.dp.toIntPx(), minIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), minIntrinsicHeight(15.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), maxIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(20.dp.toIntPx(), maxIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), maxIntrinsicHeight(15.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testMinHeightModifier_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Container(Modifier.preferredHeightIn(minHeight = 30.dp)) {
                Container(Modifier.aspectRatio(1f)) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), minIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(30.dp.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(30.dp.toIntPx(), minIntrinsicHeight(15.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(30.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), maxIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(30.dp.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(30.dp.toIntPx(), maxIntrinsicHeight(15.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(30.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testMaxHeightModifier_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Container(Modifier.preferredHeightIn(maxHeight = 40.dp)) {
                Container(Modifier.aspectRatio(1f)) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), minIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), minIntrinsicHeight(15.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), minIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), maxIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), maxIntrinsicHeight(15.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), maxIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testWidthModifier_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Container(Modifier.preferredWidth(10.dp)) {
                Container(Modifier.aspectRatio(1f)) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(10.dp.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), minIntrinsicWidth(10.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), minIntrinsicWidth(75.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(35.dp.toIntPx(), minIntrinsicHeight(35.dp.toIntPx()))
            assertEquals(70.dp.toIntPx(), minIntrinsicHeight(70.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(10.dp.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), maxIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), maxIntrinsicWidth(75.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(35.dp.toIntPx(), maxIntrinsicHeight(35.dp.toIntPx()))
            assertEquals(70.dp.toIntPx(), maxIntrinsicHeight(70.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testHeightModifier_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Container(Modifier.preferredHeight(10.dp)) {
                Container(Modifier.aspectRatio(1f)) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), minIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(75.dp.toIntPx(), minIntrinsicWidth(75.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(10.dp.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), minIntrinsicHeight(35.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), minIntrinsicHeight(70.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), maxIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(75.dp.toIntPx(), maxIntrinsicWidth(75.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(10.dp.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), maxIntrinsicHeight(35.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), maxIntrinsicHeight(70.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testWidthHeightModifiers_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Container(
                Modifier.preferredSizeIn(
                    minWidth = 10.dp,
                    maxWidth = 20.dp,
                    minHeight = 30.dp,
                    maxHeight = 40.dp
                )
            ) {
                Container(Modifier.aspectRatio(1f)) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(10.dp.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), minIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(20.dp.toIntPx(), minIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(30.dp.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(35.dp.toIntPx(), minIntrinsicHeight(35.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), minIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(30.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(10.dp.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), maxIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(20.dp.toIntPx(), maxIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(10.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(30.dp.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(35.dp.toIntPx(), maxIntrinsicHeight(35.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), maxIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(30.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testMinSizeModifier_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Container(Modifier.preferredSizeIn(minWidth = 20.dp, minHeight = 30.dp)) {
                Container(Modifier.aspectRatio(1f)) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(20.dp.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(20.dp.toIntPx(), minIntrinsicWidth(10.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(20.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(30.dp.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(30.dp.toIntPx(), minIntrinsicHeight(10.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(30.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(20.dp.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(20.dp.toIntPx(), maxIntrinsicWidth(10.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(20.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(30.dp.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(30.dp.toIntPx(), maxIntrinsicHeight(10.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicHeight(50.dp.toIntPx()))
            assertEquals(30.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testMaxSizeModifier_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Container(Modifier.preferredSizeIn(maxWidth = 40.dp, maxHeight = 50.dp)) {
                Container(Modifier.aspectRatio(1f)) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), minIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), minIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), minIntrinsicHeight(15.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicHeight(75.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), maxIntrinsicWidth(15.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), maxIntrinsicWidth(50.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(15.dp.toIntPx(), maxIntrinsicHeight(15.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicHeight(75.dp.toIntPx()))
            assertEquals(0.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testSizeModifier_hasCorrectIntrinsicMeasurements() = with(density) {
        testIntrinsics(@Composable {
            Container(Modifier.preferredSize(40.dp, 50.dp)) {
                Container(Modifier.aspectRatio(1f)) { }
            }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Min width.
            assertEquals(40.dp.toIntPx(), minIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), minIntrinsicWidth(35.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), minIntrinsicWidth(75.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))
            // Min height.
            assertEquals(50.dp.toIntPx(), minIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicHeight(35.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicHeight(70.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))
            // Max width.
            assertEquals(40.dp.toIntPx(), maxIntrinsicWidth(0.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), maxIntrinsicWidth(35.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), maxIntrinsicWidth(75.dp.toIntPx()))
            assertEquals(40.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))
            // Max height.
            assertEquals(50.dp.toIntPx(), maxIntrinsicHeight(0.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicHeight(35.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicHeight(70.dp.toIntPx()))
            assertEquals(50.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }

    @Test
    fun testFillModifier_correctSize() = with(density) {
        val width = 100.dp
        val height = 80.dp

        val displayMetrics = Resources.getSystem().displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        assertEquals(IntPxSize(width.toIntPx(), height.toIntPx()), getSize())
        assertEquals(IntPxSize(screenWidth.ipx, height.toIntPx()), getSize(Modifier.fillMaxWidth()))
        assertEquals(IntPxSize(width.toIntPx(), screenHeight.ipx),
            getSize(Modifier.fillMaxHeight()))
        assertEquals(IntPxSize(screenWidth.ipx, screenHeight.ipx), getSize(Modifier.fillMaxSize()))
    }

    @Test
    fun testFillModifier_noChangeIntrinsicMeasurements() = with(density) {
        verifyIntrinsicMeasurements(Modifier.fillMaxWidth())
        verifyIntrinsicMeasurements(Modifier.fillMaxHeight())
        verifyIntrinsicMeasurements(Modifier.fillMaxSize())
    }

    private fun getSize(modifier: Modifier = Modifier): IntPxSize {
        val width = 100.dp
        val height = 80.dp

        val positionedLatch = CountDownLatch(1)
        val size = Ref<IntPxSize>()
        val position = Ref<PxPosition>()
        show {
            Layout(@Composable {
                Stack {
                    Container(modifier + Modifier.saveLayoutInfo(size, position, positionedLatch)) {
                        Container(width = width, height = height) { }
                    }
                }
            }) { measurables, incomingConstraints, _ ->
                require(measurables.isNotEmpty())
                val placeable = measurables.first().measure(incomingConstraints)
                layout(
                    min(placeable.width, incomingConstraints.maxWidth),
                    min(placeable.height, incomingConstraints.maxHeight)
                ) {
                    placeable.place(IntPx.Zero, IntPx.Zero)
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))
        return size.value!!
    }

    private fun verifyIntrinsicMeasurements(expandedModifier: Modifier) = with(density) {
        // intrinsic measurements do not change with the ExpandedModifier
        testIntrinsics(@Composable {
            Container(
                expandedModifier + Modifier.aspectRatio(2f),
                width = 30.dp, height = 40.dp) { }
        }) { minIntrinsicWidth, minIntrinsicHeight, maxIntrinsicWidth, maxIntrinsicHeight ->
            // Width
            assertEquals(40.ipx, minIntrinsicWidth(20.ipx))
            assertEquals(30.dp.toIntPx(), minIntrinsicWidth(IntPx.Infinity))

            assertEquals(40.ipx, maxIntrinsicWidth(20.ipx))
            assertEquals(30.dp.toIntPx(), maxIntrinsicWidth(IntPx.Infinity))

            // Height
            assertEquals(20.ipx, minIntrinsicHeight(40.ipx))
            assertEquals(40.dp.toIntPx(), minIntrinsicHeight(IntPx.Infinity))

            assertEquals(20.ipx, maxIntrinsicHeight(40.ipx))
            assertEquals(40.dp.toIntPx(), maxIntrinsicHeight(IntPx.Infinity))
        }
    }
}
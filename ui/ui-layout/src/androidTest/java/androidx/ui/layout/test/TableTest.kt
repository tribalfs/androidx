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

import androidx.compose.composer
import androidx.test.filters.SmallTest
import androidx.ui.core.OnChildPositioned
import androidx.ui.core.PxPosition
import androidx.ui.core.PxSize
import androidx.ui.core.Ref
import androidx.ui.core.ipx
import androidx.ui.core.withDensity
import androidx.ui.layout.Align
import androidx.ui.layout.Alignment
import androidx.ui.layout.ConstrainedBox
import androidx.ui.layout.Container
import androidx.ui.layout.DpConstraints
import androidx.ui.layout.Table
import androidx.ui.layout.TableColumnWidth
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.pow

@SmallTest
@RunWith(JUnit4::class)
class TableTest : LayoutTest() {
    @Test
    fun testTable() = withDensity(density) {
        val rows = 8
        val columns = 8

        val size = 64.ipx
        val sizeDp = size.toDp()
        val maxWidth = 256.ipx
        val maxWidthDp = maxWidth.toDp()

        val tableSize = Ref<PxSize>()
        val childSize = Array(rows) { Array(columns) { Ref<PxSize>() } }
        val childPosition = Array(rows) { Array(columns) { Ref<PxPosition>() } }
        val positionedLatch = CountDownLatch(rows * columns + 1)

        show {
            Align(Alignment.TopLeft) {
                ConstrainedBox(constraints = DpConstraints(maxWidth = maxWidthDp)) {
                    OnChildPositioned(onPositioned = { coordinates ->
                        tableSize.value = coordinates.size
                        positionedLatch.countDown()
                    }) {
                        Table {
                            for (i in 0 until rows) {
                                tableRow {
                                    for (j in 0 until columns) {
                                        Container(height = sizeDp, expanded = true) {
                                            SaveLayoutInfo(
                                                size = childSize[i][j],
                                                position = childPosition[i][j],
                                                positionedLatch = positionedLatch
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(
            PxSize(maxWidth, size * rows),
            tableSize.value
        )
        for (i in 0 until rows) {
            for (j in 0 until columns) {
                assertEquals(
                    PxSize(maxWidth / columns, size),
                    childSize[i][j].value
                )
                assertEquals(
                    PxPosition(maxWidth * j / columns, size * i),
                    childPosition[i][j].value
                )
            }
        }
    }

    @Test
    fun testTable_rowHeights() = withDensity(density) {
        val rows = 8
        val columns = 8

        val size = 64.ipx
        val sizeDp = size.toDp()
        val halfSize = 32.ipx
        val halfSizeDp = halfSize.toDp()
        val maxWidth = 256.ipx
        val maxWidthDp = maxWidth.toDp()

        val tableSize = Ref<PxSize>()
        val childSize = Array(rows) { Array(columns) { Ref<PxSize>() } }
        val childPosition = Array(rows) { Array(columns) { Ref<PxPosition>() } }
        val positionedLatch = CountDownLatch(rows * columns + 1)

        show {
            Align(Alignment.TopLeft) {
                ConstrainedBox(constraints = DpConstraints(maxWidth = maxWidthDp)) {
                    OnChildPositioned(onPositioned = { coordinates ->
                        tableSize.value = coordinates.size
                        positionedLatch.countDown()
                    }) {
                        Table {
                            for (i in 0 until rows) {
                                tableRow {
                                    for (j in 0 until columns) {
                                        Container(
                                            height = if (j == 0) sizeDp else halfSizeDp,
                                            expanded = true
                                        ) {
                                            SaveLayoutInfo(
                                                size = childSize[i][j],
                                                position = childPosition[i][j],
                                                positionedLatch = positionedLatch
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(
            PxSize(maxWidth, size * rows),
            tableSize.value
        )
        for (i in 0 until rows) {
            for (j in 0 until columns) {
                assertEquals(
                    PxSize(maxWidth / columns, if (j == 0) size else halfSize),
                    childSize[i][j].value
                )
                assertEquals(
                    PxPosition(maxWidth * j / columns, size * i),
                    childPosition[i][j].value
                )
            }
        }
    }

    @Test
    fun testTable_withColumnWidths_wrap() = withDensity(density) {
        val rows = 8
        val columns = 8

        val size = 64.ipx
        val sizeDp = size.toDp()

        val tableSize = Ref<PxSize>()
        val childSize = Array(rows) { Array(columns) { Ref<PxSize>() } }
        val childPosition = Array(rows) { Array(columns) { Ref<PxPosition>() } }
        val positionedLatch = CountDownLatch(rows * columns + 1)

        show {
            Align(Alignment.TopLeft) {
                OnChildPositioned(onPositioned = { coordinates ->
                    tableSize.value = coordinates.size
                    positionedLatch.countDown()
                }) {
                    Table(columnWidth = { TableColumnWidth.Wrap }) {
                        for (i in 0 until rows) {
                            tableRow {
                                for (j in 0 until columns) {
                                    Container(width = sizeDp, height = sizeDp) {
                                        SaveLayoutInfo(
                                            size = childSize[i][j],
                                            position = childPosition[i][j],
                                            positionedLatch = positionedLatch
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(
            PxSize(size * columns, size * rows),
            tableSize.value
        )
        for (i in 0 until rows) {
            for (j in 0 until columns) {
                assertEquals(
                    PxSize(size, size),
                    childSize[i][j].value
                )
                assertEquals(
                    PxPosition(size * j, size * i),
                    childPosition[i][j].value
                )
            }
        }
    }

    @Test
    fun testTable_withColumnWidths_flex() = withDensity(density) {
        val rows = 8
        val columns = 8

        val size = 64.ipx
        val sizeDp = size.toDp()
        val maxWidth = 256.ipx
        val maxWidthDp = maxWidth.toDp()

        val tableSize = Ref<PxSize>()
        val childSize = Array(rows) { Array(columns) { Ref<PxSize>() } }
        val childPosition = Array(rows) { Array(columns) { Ref<PxPosition>() } }
        val positionedLatch = CountDownLatch(rows * columns + 1)

        val flexes = Array(columns) { j -> 2f.pow(max(j - 1, 0)) }
        val totalFlex = flexes.sum()

        show {
            Align(Alignment.TopLeft) {
                ConstrainedBox(constraints = DpConstraints(maxWidth = maxWidthDp)) {
                    OnChildPositioned(onPositioned = { coordinates ->
                        tableSize.value = coordinates.size
                        positionedLatch.countDown()
                    }) {
                        Table(columnWidth = { j ->
                            TableColumnWidth.Flex(flex = flexes[j])
                        }) {
                            for (i in 0 until rows) {
                                tableRow {
                                    for (j in 0 until columns) {
                                        Container(height = sizeDp, expanded = true) {
                                            SaveLayoutInfo(
                                                size = childSize[i][j],
                                                position = childPosition[i][j],
                                                positionedLatch = positionedLatch
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(
            PxSize(maxWidth, size * rows),
            tableSize.value
        )
        for (i in 0 until rows) {
            for (j in 0 until columns) {
                assertEquals(
                    PxSize(maxWidth * flexes[j] / totalFlex, size),
                    childSize[i][j].value
                )
                assertEquals(
                    PxPosition(maxWidth * flexes.take(j).sum() / totalFlex, size * i),
                    childPosition[i][j].value
                )
            }
        }
    }

    @Test
    fun testTable_withColumnWidths_fixed() = withDensity(density) {
        val rows = 8
        val columns = 8

        val size = 64.ipx
        val sizeDp = size.toDp()

        val tableSize = Ref<PxSize>()
        val childSize = Array(rows) { Array(columns) { Ref<PxSize>() } }
        val childPosition = Array(rows) { Array(columns) { Ref<PxPosition>() } }
        val positionedLatch = CountDownLatch(rows * columns + 1)

        show {
            Align(Alignment.TopLeft) {
                OnChildPositioned(onPositioned = { coordinates ->
                    tableSize.value = coordinates.size
                    positionedLatch.countDown()
                }) {
                    Table(columnWidth = { TableColumnWidth.Fixed(width = sizeDp) }) {
                        for (i in 0 until rows) {
                            tableRow {
                                for (j in 0 until columns) {
                                    Container(height = sizeDp, expanded = true) {
                                        SaveLayoutInfo(
                                            size = childSize[i][j],
                                            position = childPosition[i][j],
                                            positionedLatch = positionedLatch
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(
            PxSize(size * columns, size * rows),
            tableSize.value
        )
        for (i in 0 until rows) {
            for (j in 0 until columns) {
                assertEquals(
                    PxSize(size, size),
                    childSize[i][j].value
                )
                assertEquals(
                    PxPosition(size * j, size * i),
                    childPosition[i][j].value
                )
            }
        }
    }

    @Test
    fun testTable_withColumnWidths_fraction() = withDensity(density) {
        val rows = 8
        val columns = 8

        val size = 64.ipx
        val sizeDp = size.toDp()
        val maxWidth = 256.ipx
        val maxWidthDp = maxWidth.toDp()

        val tableSize = Ref<PxSize>()
        val childSize = Array(rows) { Array(columns) { Ref<PxSize>() } }
        val childPosition = Array(rows) { Array(columns) { Ref<PxPosition>() } }
        val positionedLatch = CountDownLatch(rows * columns + 1)

        val fractions = Array(columns) { j -> 1 / 2f.pow(j + 1) }

        show {
            Align(Alignment.TopLeft) {
                ConstrainedBox(constraints = DpConstraints(maxWidth = maxWidthDp)) {
                    OnChildPositioned(onPositioned = { coordinates ->
                        tableSize.value = coordinates.size
                        positionedLatch.countDown()
                    }) {
                        Table(columnWidth = { j ->
                            TableColumnWidth.Fraction(fraction = fractions[j])
                        }) {
                            for (i in 0 until rows) {
                                tableRow {
                                    for (j in 0 until columns) {
                                        Container(height = sizeDp, expanded = true) {
                                            SaveLayoutInfo(
                                                size = childSize[i][j],
                                                position = childPosition[i][j],
                                                positionedLatch = positionedLatch
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(
            PxSize(maxWidth * fractions.sum(), size * rows),
            tableSize.value
        )
        for (i in 0 until rows) {
            for (j in 0 until columns) {
                assertEquals(
                    PxSize(maxWidth * fractions[j], size),
                    childSize[i][j].value
                )
                assertEquals(
                    PxPosition(maxWidth * fractions.take(j).sum(), size * i),
                    childPosition[i][j].value
                )
            }
        }
    }

    @Test
    fun testTable_withColumnWidths_mixed() = withDensity(density) {
        val rows = 8
        val columns = 5

        val size = 64.ipx
        val sizeDp = size.toDp()
        val halfSize = 32.ipx
        val halfSizeDp = halfSize.toDp()
        val maxWidth = 256.ipx
        val maxWidthDp = maxWidth.toDp()

        val tableSize = Ref<PxSize>()
        val childSize = Array(rows) { Array(columns) { Ref<PxSize>() } }
        val childPosition = Array(rows) { Array(columns) { Ref<PxPosition>() } }
        val positionedLatch = CountDownLatch(rows * columns + 1)

        show {
            Align(Alignment.TopLeft) {
                ConstrainedBox(constraints = DpConstraints(maxWidth = maxWidthDp)) {
                    OnChildPositioned(onPositioned = { coordinates ->
                        tableSize.value = coordinates.size
                        positionedLatch.countDown()
                    }) {
                        Table(columnWidth = { j ->
                            when (j) {
                                0 -> TableColumnWidth.Wrap
                                1 -> TableColumnWidth.Flex(flex = 1f)
                                2 -> TableColumnWidth.Flex(flex = 3f)
                                3 -> TableColumnWidth.Fixed(width = sizeDp)
                                else -> TableColumnWidth.Fraction(fraction = 0.5f)
                            }
                        }) {
                            for (i in 0 until rows) {
                                tableRow {
                                    Container(height = sizeDp, width = halfSizeDp) {
                                        SaveLayoutInfo(
                                            size = childSize[i][0],
                                            position = childPosition[i][0],
                                            positionedLatch = positionedLatch
                                        )
                                    }
                                    Container(height = sizeDp, expanded = true) {
                                        SaveLayoutInfo(
                                            size = childSize[i][1],
                                            position = childPosition[i][1],
                                            positionedLatch = positionedLatch
                                        )
                                    }
                                    Container(height = sizeDp, expanded = true) {
                                        SaveLayoutInfo(
                                            size = childSize[i][2],
                                            position = childPosition[i][2],
                                            positionedLatch = positionedLatch
                                        )
                                    }
                                    Container(height = sizeDp, expanded = true) {
                                        SaveLayoutInfo(
                                            size = childSize[i][3],
                                            position = childPosition[i][3],
                                            positionedLatch = positionedLatch
                                        )
                                    }
                                    Container(height = sizeDp, expanded = true) {
                                        SaveLayoutInfo(
                                            size = childSize[i][4],
                                            position = childPosition[i][4],
                                            positionedLatch = positionedLatch
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        positionedLatch.await(1, TimeUnit.SECONDS)

        assertEquals(
            PxSize(maxWidth, size * rows),
            tableSize.value
        )

        for (i in 0 until rows) {
            // Wrap column 0
            assertEquals(
                PxSize(halfSize, size),
                childSize[i][0].value
            )
            assertEquals(
                PxPosition(0.ipx, size * i),
                childPosition[i][0].value
            )
            // Flex column 1
            assertEquals(
                PxSize((maxWidth / 2 - size - halfSize) / 4, size),
                childSize[i][1].value
            )
            assertEquals(
                PxPosition(halfSize, size * i),
                childPosition[i][1].value
            )
            // Flex column 2
            assertEquals(
                PxSize((maxWidth / 2 - size - halfSize) * 3 / 4, size),
                childSize[i][2].value
            )
            assertEquals(
                PxPosition(halfSize + (maxWidth / 2 - size - halfSize) / 4, size * i),
                childPosition[i][2].value
            )
            // Fixed column 3
            assertEquals(
                PxSize(size, size),
                childSize[i][3].value
            )
            assertEquals(
                PxPosition(maxWidth / 2 - size, size * i),
                childPosition[i][3].value
            )
            // Fraction column 4
            assertEquals(
                PxSize(maxWidth / 2, size),
                childSize[i][4].value
            )
            assertEquals(
                PxPosition(maxWidth / 2, size * i),
                childPosition[i][4].value
            )
        }
    }
}
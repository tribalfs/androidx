/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.glance.wear

import androidx.compose.runtime.Composable
import androidx.glance.GlanceInternalApi
import androidx.glance.Modifier
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.FontStyle
import androidx.glance.layout.FontWeight
import androidx.glance.layout.Row
import androidx.glance.layout.Text
import androidx.glance.layout.TextDecoration
import androidx.glance.layout.TextStyle
import androidx.glance.layout.expandHeight
import androidx.glance.layout.expandWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.unit.Color
import androidx.glance.unit.dp
import androidx.glance.unit.sp
import androidx.glance.wear.layout.background
import androidx.wear.tiles.DimensionBuilders
import androidx.wear.tiles.LayoutElementBuilders
import androidx.wear.tiles.LayoutElementBuilders.FONT_WEIGHT_BOLD
import androidx.wear.tiles.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER
import androidx.wear.tiles.LayoutElementBuilders.HORIZONTAL_ALIGN_END
import androidx.wear.tiles.LayoutElementBuilders.VERTICAL_ALIGN_BOTTOM
import androidx.wear.tiles.LayoutElementBuilders.VERTICAL_ALIGN_CENTER
import androidx.wear.tiles.LayoutElementBuilders.VERTICAL_ALIGN_TOP
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScope
import kotlinx.coroutines.test.runBlockingTest
import org.junit.Before
import org.junit.Test

@OptIn(GlanceInternalApi::class, ExperimentalCoroutinesApi::class)
class WearCompositionTranslatorTest {
    private lateinit var fakeCoroutineScope: TestCoroutineScope

    @Before
    fun setUp() {
        fakeCoroutineScope = TestCoroutineScope()
    }

    @Test
    fun canTranslateBox() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            Box {}
        }

        // runAndTranslate wraps the result in a Box...ensure that the layout generated two Boxes
        val outerBox = content as LayoutElementBuilders.Box
        assertThat(outerBox.contents).hasSize(1)

        assertThat(outerBox.contents[0]).isInstanceOf(LayoutElementBuilders.Box::class.java)
    }

    @Test
    fun canTranslateBoxWithAlignment() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            Box(contentAlignment = Alignment.Center) {}
        }

        val innerBox =
            (content as LayoutElementBuilders.Box).contents[0] as LayoutElementBuilders.Box

        assertThat(innerBox.verticalAlignment!!.value).isEqualTo(VERTICAL_ALIGN_CENTER)
        assertThat(innerBox.horizontalAlignment!!.value).isEqualTo(HORIZONTAL_ALIGN_CENTER)
    }

    @Test
    fun canTranslateBoxWithChildren() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            Box {
                Box(contentAlignment = Alignment.TopCenter) {}
                Box(contentAlignment = Alignment.BottomEnd) {}
            }
        }

        val innerBox =
            (content as LayoutElementBuilders.Box).contents[0] as LayoutElementBuilders.Box
        val leaf0 = innerBox.contents[0] as LayoutElementBuilders.Box
        val leaf1 = innerBox.contents[1] as LayoutElementBuilders.Box

        assertThat(leaf0.verticalAlignment!!.value).isEqualTo(VERTICAL_ALIGN_TOP)
        assertThat(leaf0.horizontalAlignment!!.value).isEqualTo(HORIZONTAL_ALIGN_CENTER)

        assertThat(leaf1.verticalAlignment!!.value).isEqualTo(VERTICAL_ALIGN_BOTTOM)
        assertThat(leaf1.horizontalAlignment!!.value).isEqualTo(HORIZONTAL_ALIGN_END)
    }

    @Test
    fun canTranslatePaddingModifier() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            Box(modifier = Modifier.padding(start = 1.dp, top = 2.dp, end = 3.dp, bottom = 4.dp)) {}
        }

        val innerBox =
            (content as LayoutElementBuilders.Box).contents[0] as LayoutElementBuilders.Box
        val padding = requireNotNull(innerBox.modifiers!!.padding)

        assertThat(padding.start!!.value).isEqualTo(1f)
        assertThat(padding.top!!.value).isEqualTo(2f)
        assertThat(padding.end!!.value).isEqualTo(3f)
        assertThat(padding.bottom!!.value).isEqualTo(4f)
    }

    @Test
    fun canTranslateBackgroundModifier() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            Box(modifier = Modifier.background(Color(0x11223344))) {}
        }

        val innerBox =
            (content as LayoutElementBuilders.Box).contents[0] as LayoutElementBuilders.Box
        val background = requireNotNull(innerBox.modifiers!!.background)

        assertThat(background.color!!.argb).isEqualTo(0x11223344)
    }

    @Test
    fun canTranslateRow() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
                Box(contentAlignment = Alignment.TopCenter) {}
                Box(contentAlignment = Alignment.BottomEnd) {}
            }
        }

        val innerRow =
            (content as LayoutElementBuilders.Box).contents[0] as LayoutElementBuilders.Row

        assertThat(innerRow.verticalAlignment!!.value).isEqualTo(VERTICAL_ALIGN_CENTER)

        val leaf0 = innerRow.contents[0] as LayoutElementBuilders.Box
        val leaf1 = innerRow.contents[1] as LayoutElementBuilders.Box

        assertThat(leaf0.verticalAlignment!!.value).isEqualTo(VERTICAL_ALIGN_TOP)
        assertThat(leaf0.horizontalAlignment!!.value).isEqualTo(HORIZONTAL_ALIGN_CENTER)

        assertThat(leaf1.verticalAlignment!!.value).isEqualTo(VERTICAL_ALIGN_BOTTOM)
        assertThat(leaf1.horizontalAlignment!!.value).isEqualTo(HORIZONTAL_ALIGN_END)
    }

    @Test
    fun rowWithHorizontalAlignmentInflatesInColumn() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            Row(
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                modifier = Modifier.expandWidth().height(100.dp).background(Color(0x11223344))
            ) {}
        }

        val innerColumn =
            (content as LayoutElementBuilders.Box).contents[0] as LayoutElementBuilders.Column
        val innerRow = innerColumn.contents[0] as LayoutElementBuilders.Row

        assertThat(innerColumn.horizontalAlignment!!.value).isEqualTo(HORIZONTAL_ALIGN_CENTER)

        // Column should inherit the size of the inner Row
        assertThat(innerColumn.width).isInstanceOf(
            DimensionBuilders.ExpandedDimensionProp::class.java
        )
        assertThat((innerColumn.height as DimensionBuilders.DpProp).value).isEqualTo(100f)

        // Column should also inherit the modifiers
        assertThat(innerColumn.modifiers!!.background!!.color!!.argb).isEqualTo(0x11223344)

        // The row should have a wrapped width, but still use the height
        assertThat(innerRow.width).isInstanceOf(
            DimensionBuilders.WrappedDimensionProp::class.java
        )
        assertThat((innerRow.height as DimensionBuilders.DpProp).value).isEqualTo(100f)

        // And no modifiers.
        assertThat(innerRow.modifiers).isNull()

        // Should have the vertical alignment set still though
        assertThat(innerRow.verticalAlignment!!.value).isEqualTo(VERTICAL_ALIGN_CENTER)
    }

    @Test
    fun canTranslateColumn() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            Column(horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
                Box(contentAlignment = Alignment.TopCenter) {}
                Box(contentAlignment = Alignment.BottomEnd) {}
            }
        }

        val innerColumn =
            (content as LayoutElementBuilders.Box).contents[0] as LayoutElementBuilders.Column

        assertThat(innerColumn.horizontalAlignment!!.value).isEqualTo(HORIZONTAL_ALIGN_CENTER)

        val leaf0 = innerColumn.contents[0] as LayoutElementBuilders.Box
        val leaf1 = innerColumn.contents[1] as LayoutElementBuilders.Box

        assertThat(leaf0.verticalAlignment!!.value).isEqualTo(VERTICAL_ALIGN_TOP)
        assertThat(leaf0.horizontalAlignment!!.value).isEqualTo(HORIZONTAL_ALIGN_CENTER)

        assertThat(leaf1.verticalAlignment!!.value).isEqualTo(VERTICAL_ALIGN_BOTTOM)
        assertThat(leaf1.horizontalAlignment!!.value).isEqualTo(HORIZONTAL_ALIGN_END)
    }

    @Test
    fun columnWithVerticalAlignmentInflatesInRow() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            Column(
                verticalAlignment = Alignment.Vertical.CenterVertically,
                horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
                modifier = Modifier.expandHeight().width(100.dp).background(Color(0x11223344))
            ) {}
        }

        val innerRow =
            (content as LayoutElementBuilders.Box).contents[0] as LayoutElementBuilders.Row
        val innerColumn = innerRow.contents[0] as LayoutElementBuilders.Column

        assertThat(innerRow.verticalAlignment!!.value).isEqualTo(VERTICAL_ALIGN_CENTER)

        // Row should inherit the size of the inner Row
        assertThat((innerRow.width as DimensionBuilders.DpProp).value).isEqualTo(100f)
        assertThat(innerRow.height).isInstanceOf(
            DimensionBuilders.ExpandedDimensionProp::class.java
        )

        // Row should also inherit the modifiers
        assertThat(innerRow.modifiers!!.background!!.color!!.argb).isEqualTo(0x11223344)

        // The Column should have a wrapped width, but still use the height
        assertThat((innerColumn.width as DimensionBuilders.DpProp).value).isEqualTo(100f)
        assertThat(innerColumn.height).isInstanceOf(
            DimensionBuilders.WrappedDimensionProp::class.java
        )

        // And no modifiers.
        assertThat(innerColumn.modifiers).isNull()

        // Should have the horizontal alignment set still though
        assertThat(innerColumn.horizontalAlignment!!.value).isEqualTo(HORIZONTAL_ALIGN_CENTER)
    }

    @Test
    fun canInflateText() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            val style = TextStyle(
                size = 16.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                textDecoration = TextDecoration.Underline
            )
            Text("Hello World", modifier = Modifier.padding(1.dp), style = style)
        }

        val innerText = (content as LayoutElementBuilders.Box).contents[0]
            as LayoutElementBuilders.Text

        assertThat(innerText.text!!.value).isEqualTo("Hello World")
        assertThat(innerText.fontStyle!!.size!!.value).isEqualTo(16f)
        assertThat(innerText.fontStyle!!.italic!!.value).isTrue()
        assertThat(innerText.fontStyle!!.weight!!.value).isEqualTo(FONT_WEIGHT_BOLD)
        assertThat(innerText.fontStyle!!.underline!!.value).isTrue()
    }

    @Test
    fun textWithSizeInflatesInBox() = fakeCoroutineScope.runBlockingTest {
        val content = runAndTranslate {
            Text("Hello World", modifier = Modifier.size(100.dp).padding(10.dp))
        }

        val innerBox = (content as LayoutElementBuilders.Box).contents[0] as
            LayoutElementBuilders.Box
        val innerText = innerBox.contents[0] as LayoutElementBuilders.Text

        assertThat(innerBox.width is DimensionBuilders.DpProp)
        assertThat((innerBox.width as DimensionBuilders.DpProp).value).isEqualTo(100f)
        assertThat(innerBox.height is DimensionBuilders.DpProp)
        assertThat((innerBox.height as DimensionBuilders.DpProp).value).isEqualTo(100f)

        // Modifiers should apply to the Box
        assertThat(innerBox.modifiers!!.padding).isNotNull()

        // ... and not to the Text
        assertThat(innerText.modifiers?.padding).isNull()
    }

    private suspend fun runAndTranslate(
        content: @Composable () -> Unit
    ): LayoutElementBuilders.LayoutElement {
        val root = runTestingComposition(content)

        return translateComposition(root)
    }
}

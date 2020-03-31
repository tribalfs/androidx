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

package androidx.ui.core

import androidx.compose.Composable
import androidx.compose.remember
import androidx.test.filters.SmallTest
import androidx.test.rule.ActivityTestRule
import androidx.ui.framework.test.R
import androidx.ui.framework.test.TestActivity
import androidx.ui.layout.rtl
import androidx.ui.text.AnnotatedString
import androidx.ui.text.TextLayoutResult
import androidx.ui.text.TextStyle
import androidx.ui.text.font.FontStyle
import androidx.ui.text.font.FontWeight
import androidx.ui.text.font.asFontFamily
import androidx.ui.text.font.font
import androidx.ui.text.style.TextOverflow
import androidx.ui.unit.Density
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.ipx
import androidx.ui.unit.px
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(JUnit4::class)
@SmallTest
class TextLayoutTest {
    @get:Rule
    internal val activityTestRule = ActivityTestRule(TestActivity::class.java)
    private lateinit var activity: TestActivity
    private lateinit var density: Density

    @Before
    fun setup() {
        activity = activityTestRule.activity
        density = Density(activity)
    }

    @Test
    fun testTextLayout() = with(density) {
        val layoutLatch = CountDownLatch(2)
        val textSize = Ref<IntPxSize>()
        val doubleTextSize = Ref<IntPxSize>()
        show {
            TestingText(
                "aa",
                modifier = Modifier.onPositioned { coordinates ->
                    textSize.value = coordinates.size
                    layoutLatch.countDown()
                }
            )
            TestingText("aaaa",
                modifier = Modifier.onPositioned { coordinates ->
                    doubleTextSize.value = coordinates.size
                    layoutLatch.countDown()
                }
            )
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        assertNotNull(textSize.value)
        assertNotNull(doubleTextSize.value)
        assertTrue(textSize.value!!.width > 0.px)
        assertTrue(textSize.value!!.height > 0.px)
        assertEquals(textSize.value!!.width * 2, doubleTextSize.value!!.width)
        assertEquals(textSize.value!!.height, doubleTextSize.value!!.height)
    }

    @Test
    fun testTextLayout_intrinsicMeasurements() = with(density) {
        val layoutLatch = CountDownLatch(2)
        val textSize = Ref<IntPxSize>()
        val doubleTextSize = Ref<IntPxSize>()
        show {
            TestingText("aa ",
                modifier = Modifier.onPositioned { coordinates ->
                    textSize.value = coordinates.size
                    layoutLatch.countDown()
                }
            )
            TestingText("aa aa ",
                modifier = Modifier.onPositioned { coordinates ->
                    doubleTextSize.value = coordinates.size
                    layoutLatch.countDown()
                }
            )
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        val textWidth = textSize.value!!.width
        val textHeight = textSize.value!!.height
        val doubleTextWidth = doubleTextSize.value!!.width

        val intrinsicsLatch = CountDownLatch(1)
        show {
            val text = @Composable {
                TestingText("aa aa ")
            }
            Layout(
                text,
                minIntrinsicWidthMeasureBlock = { _, _, _ -> 0.ipx },
                minIntrinsicHeightMeasureBlock = { _, _, _ -> 0.ipx },
                maxIntrinsicWidthMeasureBlock = { _, _, _ -> 0.ipx },
                maxIntrinsicHeightMeasureBlock = { _, _, _ -> 0.ipx }
            ) { measurables, _, _ ->
                val textMeasurable = measurables.first()
                // Min width.
                assertEquals(textWidth, textMeasurable.minIntrinsicWidth(0.ipx))
                // Min height.
                assertTrue(textMeasurable.minIntrinsicHeight(textWidth) > textHeight)
                assertEquals(textHeight, textMeasurable.minIntrinsicHeight(doubleTextWidth))
                assertEquals(textHeight, textMeasurable.minIntrinsicHeight(IntPx.Infinity))
                // Max width.
                assertEquals(doubleTextWidth, textMeasurable.maxIntrinsicWidth(0.ipx))
                // Max height.
                assertTrue(textMeasurable.maxIntrinsicHeight(textWidth) > textHeight)
                assertEquals(textHeight, textMeasurable.maxIntrinsicHeight(doubleTextWidth))
                assertEquals(textHeight, textMeasurable.maxIntrinsicHeight(IntPx.Infinity))

                intrinsicsLatch.countDown()

                layout(0.ipx, 0.ipx) {}
            }
        }
        assertTrue(intrinsicsLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testTextLayout_providesBaselines() = with(density) {
        val layoutLatch = CountDownLatch(2)
        show {
            val text = @Composable {
                TestingText("aa")
            }
            Layout(text) { measurables, _, _ ->
                val placeable = measurables.first().measure(Constraints())
                assertNotNull(placeable[FirstBaseline])
                assertNotNull(placeable[LastBaseline])
                assertEquals(placeable[FirstBaseline], placeable[LastBaseline])
                layoutLatch.countDown()
                layout(0.ipx, 0.ipx) {}
            }
            Layout(text) { measurables, _, _ ->
                val placeable = measurables.first().measure(Constraints(maxWidth = 0.ipx))
                assertNotNull(placeable[FirstBaseline])
                assertNotNull(placeable[LastBaseline])
                assertTrue(placeable[FirstBaseline]!!.value < placeable[LastBaseline]!!.value)
                layoutLatch.countDown()
                layout(0.ipx, 0.ipx) {}
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun testOnTextLayout() = with(density) {
        val layoutLatch = CountDownLatch(1)
        val callback = mock<(TextLayoutResult) -> Unit>()
        show {
            val text = @Composable {
                TestingText("aa", onTextLayout = callback)
            }
            Layout(text) { measurables, _, _ ->
                measurables.first().measure(Constraints())
                layoutLatch.countDown()
                layout(0.ipx, 0.ipx) {}
            }
        }
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
        verify(callback, times(1)).invoke(any())
    }

    @Test
    fun testCorrectLayoutDirection() {
        val latch = CountDownLatch(1)
        var layoutDirection: LayoutDirection? = null
        show {
            CoreText(
                text = AnnotatedString("..."),
                modifier = Modifier.rtl,
                style = TextStyle.Default,
                softWrap = true,
                overflow = TextOverflow.Clip,
                maxLines = 1
            ) { result ->
                layoutDirection = result.layoutInput.layoutDirection
                latch.countDown()
            }
        }

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue()
        assertThat(layoutDirection).isNotNull()
        assertThat(layoutDirection!!).isEqualTo(LayoutDirection.Rtl)
    }

    private fun show(composable: @Composable() () -> Unit) {
        val runnable: Runnable = object : Runnable {
            override fun run() {
                activity.setContent {
                    Layout(composable) { measurables, constraints, _ ->
                        val placeables = measurables.map {
                            it.measure(constraints.copy(minWidth = 0.ipx, minHeight = 0.ipx))
                        }
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            var top = 0.px
                            placeables.forEach {
                                it.place(0.px, top)
                                top += it.height
                            }
                        }
                    }
                }
            }
        }
        activityTestRule.runOnUiThread(runnable)
    }
}

@Composable
private fun TestingText(
    text: String,
    modifier: Modifier = Modifier.None,
    onTextLayout: (TextLayoutResult) -> Unit = {}
) {
    val textStyle = remember {
        TextStyle(
            fontFamily = font(
                resId = R.font.sample_font,
                weight = FontWeight.Normal,
                style = FontStyle.Normal
            ).asFontFamily()
        )
    }
    CoreText(
        AnnotatedString(text),
        style = textStyle,
        modifier = modifier,
        softWrap = true,
        maxLines = Int.MAX_VALUE,
        overflow = TextOverflow.Clip,
        onTextLayout = onTextLayout
    )
}
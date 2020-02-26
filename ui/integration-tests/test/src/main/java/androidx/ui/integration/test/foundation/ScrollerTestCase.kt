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

package androidx.ui.integration.test.foundation

import androidx.compose.Composable
import androidx.compose.remember
import androidx.ui.foundation.Canvas
import androidx.ui.foundation.ScrollerPosition
import androidx.ui.foundation.VerticalScroller
import androidx.ui.graphics.Color
import androidx.ui.graphics.Paint
import androidx.ui.graphics.PaintingStyle
import androidx.ui.integration.test.ToggleableTestCase
import androidx.ui.layout.Column
import androidx.ui.layout.LayoutHeight
import androidx.ui.layout.LayoutSize
import androidx.ui.test.ComposeTestCase
import androidx.ui.unit.dp
import androidx.ui.unit.toRect

/**
 * Test case that puts a large number of boxes in a column in a vertical scroller to force scrolling.
 */
class ScrollerTestCase() : ComposeTestCase, ToggleableTestCase {
    private lateinit var scrollerPosition: ScrollerPosition

    @Composable
    override fun emitContent() {
        scrollerPosition = ScrollerPosition()
        VerticalScroller(
            scrollerPosition = scrollerPosition
        ) {
            Column(LayoutHeight.Fill) {
                for (green in 0..0xFF) {
                    ColorStripe(0xFF, green, 0)
                }
                for (red in 0xFF downTo 0) {
                    ColorStripe(red, 0xFF, 0)
                }
                for (blue in 0..0xFF) {
                    ColorStripe(0, 0xFF, blue)
                }
                for (green in 0xFF downTo 0) {
                    ColorStripe(0, green, 0xFF)
                }
                for (red in 0..0xFF) {
                    ColorStripe(red, 0, 0xFF)
                }
                for (blue in 0xFF downTo 0) {
                    ColorStripe(0xFF, 0, blue)
                }
            }
        }
    }

    override fun toggleState() {
        scrollerPosition.scrollTo(if (scrollerPosition.value == 0f) 10f else 0f)
    }

    @Composable
    fun ColorStripe(red: Int, green: Int, blue: Int) {
        val paint = remember { Paint() }
        Canvas(LayoutSize(45.dp, 5.dp)) {
            paint.color = Color(red = red, green = green, blue = blue)
            paint.style = PaintingStyle.fill
            drawRect(size.toRect(), paint)
        }
    }
}

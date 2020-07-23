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

package androidx.compose.animation.demos

import android.util.Log
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.fling
import androidx.compose.Composable
import androidx.compose.state
import androidx.compose.animation.animatedFloat
import androidx.ui.core.Modifier
import androidx.ui.core.gesture.DragObserver
import androidx.ui.core.gesture.rawDragGestureFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Text
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.ui.graphics.Color
import androidx.ui.graphics.drawscope.DrawScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.preferredHeight
import androidx.ui.unit.dp
import androidx.ui.unit.sp
import kotlin.math.roundToInt

@Composable
fun SpringBackScrollingDemo() {
    Column(Modifier.fillMaxHeight()) {
        Text(
            "<== Scroll horizontally ==>",
            fontSize = 20.sp,
            modifier = Modifier.padding(40.dp)
        )
        val animScroll = animatedFloat(0f)
        val itemWidth = state { 0f }
        val isFlinging = state { false }
        val gesture = Modifier.rawDragGestureFilter(dragObserver = object : DragObserver {
            override fun onDrag(dragDistance: Offset): Offset {
                animScroll.snapTo(animScroll.targetValue + dragDistance.x)
                return dragDistance
            }

            override fun onStop(velocity: Offset) {
                isFlinging.value = true
                animScroll.fling(velocity.x, onEnd = { _, _, _ ->
                    isFlinging.value = false
                })
            }
        })
        Canvas(gesture.fillMaxWidth().preferredHeight(400.dp)) {
            itemWidth.value = size.width / 2f
            if (isFlinging.value) {
                // Figure out what position to spring back to
                val target = animScroll.targetValue
                var rem = target % itemWidth.value
                if (animScroll.velocity < 0) {
                    if (rem > 0) {
                        rem -= itemWidth.value
                    }
                } else {
                    if (rem < 0) {
                        rem += itemWidth.value
                    }
                }
                val springBackTarget = target - rem

                // Spring back as soon as the target position is crossed.
                if ((animScroll.velocity > 0 && animScroll.value > springBackTarget) ||
                    (animScroll.velocity < 0 && animScroll.value < springBackTarget)
                ) {
                    animScroll.animateTo(
                        springBackTarget,
                        SpringSpec(dampingRatio = 0.8f, stiffness = 200f)
                    )
                }
            }
            if (DEBUG) {
                Log.w(
                    "Anim", "Spring back scrolling, redrawing with new" +
                            " scroll value: ${animScroll.value}"
                )
            }
            drawRects(animScroll.value)
        }
    }
}

private fun DrawScope.drawRects(animScroll: Float) {
    val width = size.width / 2f
    val scroll = animScroll + width / 2
    var startingPos = scroll % width
    if (startingPos > 0) {
        startingPos -= width
    }
    var startingColorIndex = ((scroll - startingPos) / width).roundToInt().rem(colors.size)
    if (startingColorIndex < 0) {
        startingColorIndex += colors.size
    }

    val rectSize = Size(width - 20.0f, size.height)

    drawRect(
        colors[startingColorIndex],
        topLeft = Offset(startingPos + 10, 0f),
        size = rectSize
    )

    drawRect(
        colors[(startingColorIndex + colors.size - 1) % colors.size],
        topLeft = Offset(startingPos + width + 10, 0f),
        size = rectSize
    )

    drawRect(
        colors[(startingColorIndex + colors.size - 2) % colors.size],
        topLeft = Offset(startingPos + width * 2 + 10, 0.0f),
        size = rectSize
    )
}

private val colors = listOf(
    Color(0xFFdaf8e3),
    Color(0xFF97ebdb),
    Color(0xFF00c2c7),
    Color(0xFF0086ad),
    Color(0xFF005582),
    Color(0xFF0086ad),
    Color(0xFF00c2c7),
    Color(0xFF97ebdb)
)

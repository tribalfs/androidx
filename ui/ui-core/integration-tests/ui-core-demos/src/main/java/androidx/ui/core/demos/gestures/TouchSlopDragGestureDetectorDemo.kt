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

package androidx.ui.core.demos.gestures

import androidx.compose.runtime.Composable
import androidx.compose.runtime.state
import androidx.ui.core.Alignment
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Direction
import androidx.ui.core.Modifier
import androidx.ui.core.gesture.DragObserver
import androidx.ui.core.gesture.dragGestureFilter
import androidx.compose.foundation.Box
import androidx.compose.foundation.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp

/**
 * Simple [dragGestureFilter] demo.
 */
@Composable
fun DragGestureFilterDemo() {

    val verticalColor = Color(0xfff44336)
    val horizontalColor = Color(0xff2196f3)

    val offset = state { Offset.Zero }
    val canStartVertically = state { true }

    val dragObserver =
        if (canStartVertically.value) {
            object : DragObserver {
                override fun onDrag(dragDistance: Offset): Offset {
                    offset.value =
                        Offset(x = offset.value.x, y = offset.value.y + dragDistance.y)
                    return dragDistance
                }

                override fun onStop(velocity: Offset) {
                    canStartVertically.value = !canStartVertically.value
                    super.onStop(velocity)
                }
            }
        } else {
            object : DragObserver {
                override fun onDrag(dragDistance: Offset): Offset {
                    offset.value =
                        Offset(x = offset.value.x + dragDistance.x, y = offset.value.y)
                    return dragDistance
                }

                override fun onStop(velocity: Offset) {
                    canStartVertically.value = !canStartVertically.value
                    super.onStop(velocity)
                }
            }
        }

    val canDrag =
        if (canStartVertically.value) {
            { direction: Direction ->
                when (direction) {
                    Direction.DOWN -> true
                    Direction.UP -> true
                    else -> false
                }
            }
        } else {
            { direction: Direction ->
                when (direction) {
                    Direction.LEFT -> true
                    Direction.RIGHT -> true
                    else -> false
                }
            }
        }

    val color =
        if (canStartVertically.value) {
            verticalColor
        } else {
            horizontalColor
        }

    val (offsetX, offsetY) =
        with(DensityAmbient.current) { offset.value.x.toDp() to offset.value.y.toDp() }

    Column {
        Text(
            "Demonstrates standard dragging (when a slop has to be exceeded before dragging can " +
                    "start) and customization of the direction in which dragging can occur."
        )
        Text(
            "When the box is blue, it can only be dragged horizontally.  When the box is red, it" +
                    " can only be dragged vertically."
        )
        Box(
            Modifier.offset(offsetX, offsetY)
                .fillMaxSize()
                .wrapContentSize(Alignment.Center)
                .preferredSize(192.dp)
                .dragGestureFilter(dragObserver, canDrag),
            backgroundColor = color
        )
    }
}

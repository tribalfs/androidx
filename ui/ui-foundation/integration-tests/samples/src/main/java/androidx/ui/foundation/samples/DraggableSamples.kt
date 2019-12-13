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

package androidx.ui.foundation.samples

import androidx.annotation.Sampled
import androidx.compose.Composable
import androidx.ui.core.Alignment
import androidx.ui.core.ambientDensity
import androidx.ui.core.dp
import androidx.ui.core.withDensity
import androidx.ui.foundation.ColoredRect
import androidx.ui.foundation.animation.AnchorsFlingConfig
import androidx.ui.foundation.animation.animatedDragValue
import androidx.ui.foundation.gestures.DragDirection
import androidx.ui.foundation.gestures.Draggable
import androidx.ui.foundation.shape.DrawShape
import androidx.ui.foundation.shape.RectangleShape
import androidx.ui.graphics.Color
import androidx.ui.layout.Container
import androidx.ui.layout.Padding

@Sampled
@Composable
fun DraggableSample() {
    val max = 300.dp
    val min = 0.dp
    val (minPx, maxPx) = withDensity(ambientDensity()) {
        min.toPx().value to max.toPx().value
    }
    val position = animatedDragValue(0f, minPx, maxPx)
    Draggable(
        dragValue = position,
        onDragValueChangeRequested = { position.animatedFloat.snapTo(it) },
        dragDirection = DragDirection.Horizontal
    ) {
        // dragValue is the current value in progress of dragging
        val draggedDp = withDensity(ambientDensity()) {
            position.value.toDp()
        }
        val squareSize = 50.dp

        // Draw a seekbar-like composable that has a black background
        // with a red square that moves along the drag
        Container(width = max + squareSize, alignment = Alignment.CenterLeft) {
            DrawShape(RectangleShape, Color.Black)
            Padding(left = draggedDp) {
                ColoredRect(Color.Red, width = squareSize, height = squareSize)
            }
        }
    }
}

@Sampled
@Composable
fun AnchoredDraggableSample() {
    val max = 300.dp
    val min = 0.dp
    val (minPx, maxPx) = withDensity(ambientDensity()) {
        min.toPx().value to max.toPx().value
    }
    // define anchors and related animation controller
    val anchors = listOf(minPx, maxPx, maxPx / 2)
    val flingConfig = AnchorsFlingConfig(anchors)
    val position = animatedDragValue(0f, minPx, maxPx)

    Draggable(
        dragValue = position,
        onDragValueChangeRequested = { position.animatedFloat.snapTo(it) },
        dragDirection = DragDirection.Horizontal,
        onDragStopped = { position.fling(flingConfig, it) }
    ) {
        val draggedDp = withDensity(ambientDensity()) {
            position.value.toDp()
        }
        val squareSize = 50.dp

        // Draw a seekbar-like widget that has a black background
        // with a red square that moves along the drag
        Container(width = max + squareSize, alignment = Alignment.CenterLeft) {
            DrawShape(RectangleShape, Color.Black)
            Padding(left = draggedDp) {
                ColoredRect(Color.Red, width = squareSize, height = squareSize)
            }
        }
    }
}

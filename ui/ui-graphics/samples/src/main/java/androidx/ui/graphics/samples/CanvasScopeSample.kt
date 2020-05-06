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

package androidx.ui.graphics.samples

import androidx.annotation.Sampled
import androidx.ui.geometry.Offset
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.ImageAsset
import androidx.ui.graphics.painter.CanvasScope
import androidx.ui.graphics.painter.inset
import androidx.ui.graphics.painter.rotate
import androidx.ui.graphics.painter.withTransform
import androidx.ui.unit.Px
import androidx.ui.unit.PxSize

/**
 * Sample showing how to use CanvasScope to issue drawing commands into
 * a given canvas as well as providing transformations to the drawing environment
 */
@Sampled
fun canvasScopeSample() {
    val pxSize = PxSize(Px(300.0f), Px(300.0f))
    val imageAsset = ImageAsset(
        pxSize.width.value.toInt(),
        pxSize.height.value.toInt()
    )

    // create a Canvas to draw contents into the ImageAsset
    val targetCanvas = Canvas(imageAsset)

    // Create a CanvasScope from this canvas to issue drawing commands into
    CanvasScope().draw(targetCanvas, pxSize) {
        drawRect(color = Color.Gray) // Draw grey background
        // Inset content by 10 pixels on the left/right sides and 12 by the
        // top/bottom
        inset(10.0f, 12.0f) {
            val quadrantSize = size / 2.0f

            // Draw a rectangle within the inset bounds
            drawRect(
                size = quadrantSize,
                color = Color.Red
            )

            rotate(45.0f) {
                drawRect(size = quadrantSize, color = Color.Blue)
            }
        }
    }
}

@Sampled
fun canvasScopeBatchedTransformSample() {
    val pxSize = PxSize(Px(300.0f), Px(300.0f))
    val imageAsset = ImageAsset(
        pxSize.width.value.toInt(),
        pxSize.height.value.toInt()
    )

    // create a Canvas to draw contents into the ImageAsset
    val targetCanvas = Canvas(imageAsset)

    CanvasScope().draw(targetCanvas, pxSize) { // CanvasScope
        inset(20.0f) {
            // Use withTransform to batch multiple transformations for 1 or more drawing calls
            // that are to be drawn.
            // This is more efficient than issuing nested translation, rotation and scaling
            // calls as the internal state is saved once before and after instead of multiple
            // times between each transformation if done individually
            withTransform({
                translate(10.0f, 12.0f)
                rotate(45.0f)
                scale(2.0f, 0.5f)
            }) {
                drawRect(Color.Cyan)
                drawCircle(Color.Blue)
            }
            drawRect(Color.Red, alpha = 0.25f)
        }
    }
}
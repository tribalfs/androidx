/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.ui.material

import androidx.ui.baseui.selection.Toggleable
import androidx.ui.core.Draw
import androidx.ui.core.MeasureBox
import androidx.ui.core.PixelSize
import androidx.ui.core.dp
import androidx.ui.core.toPx
import androidx.ui.engine.geometry.Rect
import androidx.ui.painting.Canvas
import androidx.ui.painting.Color
import androidx.ui.painting.Paint
import com.google.r4a.Component
import com.google.r4a.Composable
import com.google.r4a.composer

// TODO(clara): This should not be a class once R4A bug is fixed
class Checkbox : Component() {

    override fun compose() {
        <Toggleable>
            <MeasureBox bust=Math.random()> constraints, measureOperations ->
                // TODO(clara): Use constraints
                val calculatedWidth = 42.dp
                // TODO(clara): get the context from somewhere else
                val width = calculatedWidth.toPx(composer.composer.context)
                measureOperations.collect {
                    <DrawCheckbox width/>
                }
                measureOperations.layout(calculatedWidth, calculatedWidth) {
                    // No children to place
                }
            </MeasureBox>
        </Toggleable>
    }
}

@Composable
internal fun DrawCheckbox(width: Float) {
    val paint = Paint()
    paint.color = Color(0xFFFF0000.toInt())
    val paintLambda : (Canvas, PixelSize) -> Unit = { canvas, parentSize ->
        canvas.drawRect(Rect(0f, 0f, 42f, 42f), paint)
        // TODO(clara): Actually draw a checkbox
    }
    <Draw onPaint=paintLambda />
}
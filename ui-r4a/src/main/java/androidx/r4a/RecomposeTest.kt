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

package androidx.r4a

import android.os.Handler
import android.os.Looper
import androidx.ui.core.Constraints
import androidx.ui.core.CraneWrapper
import androidx.ui.core.Dp
import androidx.ui.core.adapter.Draw
import androidx.ui.core.adapter.MeasureBox
import androidx.ui.core.div
import androidx.ui.core.dp
import androidx.ui.core.minus
import androidx.ui.core.plus
import androidx.ui.core.times
import androidx.ui.core.toRect
import androidx.ui.core.toRoundedPixels
import androidx.ui.engine.geometry.Rect
import androidx.ui.painting.Color
import androidx.ui.painting.Paint
import com.google.r4a.Children
import com.google.r4a.Component
import com.google.r4a.Composable
import com.google.r4a.composer
import com.google.r4a.Recompose


@Composable
fun GrayRect() {
    <MeasureBox> constraints ->
        collect {
            val paint = Paint()
            paint.color = Color(android.graphics.Color.GRAY)
            <Draw> canvas, parentSize ->
                canvas.drawRect(parentSize.toRect(), paint)
            </Draw>
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
        }
    </MeasureBox>
}

@Composable
fun ListWithOffset(
    itemsCount: Int,
    offset: Dp,
    @Children item: () -> Unit
) {
    <MeasureBox> constraints ->
        val measurables = collect {
            repeat(itemsCount) {
                <item />
            }
        }
        val itemHeight = (constraints.maxHeight - offset.toPx() * (itemsCount - 1)) / itemsCount
        val itemConstraint = Constraints.tightConstraints(constraints.maxWidth, itemHeight)
        layout(constraints.maxWidth, constraints.maxHeight) {
            var top = 0
            measurables.map { it.measure(itemConstraint) }.forEach {
                it.place(0, top)
                top += itemHeight.toRoundedPixels() + offset.toRoundedPixels()
            }
        }
    </MeasureBox>
}

class RecomposeTest : Component() {

    var needsScheduling = true
    var offset = 10.dp
    var itemsCount = 1
    var step = 0

    override fun compose() {
        <CraneWrapper>
            <Recompose> recompose ->
                if (needsScheduling) {
                    needsScheduling = false
                    val handler = Handler(Looper.getMainLooper())
                    val r = object : Runnable {
                        override fun run() {
                            if (step.rem(3) == 0) {
                                itemsCount++
                            } else if (step.rem(3) == 1) {
                                offset += 10.dp
                            } else {
                                itemsCount--
                            }
                            step++
                            needsScheduling = true
                            recompose()
                        }
                    }
                    handler.postDelayed(r, 500)
                }
                <ListWithOffset itemsCount offset>
                    <GrayRect />
                </ListWithOffset>
            </Recompose>
        </CraneWrapper>
    }
}
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

package androidx.ui.animation.demos

import android.app.Activity
import android.os.Bundle
import androidx.animation.FastOutSlowInEasing
import androidx.animation.FloatPropKey
import androidx.animation.ManualAnimationClock
import androidx.animation.PhysicsBuilder
import androidx.animation.transitionDefinition
import androidx.compose.Composable
import androidx.compose.remember
import androidx.ui.animation.Transition
import androidx.ui.animation.animatedFloat
import androidx.ui.core.AnimationClockAmbient
import androidx.ui.core.Draw
import androidx.ui.core.PxPosition
import androidx.ui.core.Text
import androidx.ui.core.dp
import androidx.ui.core.gesture.RawDragGestureDetector
import androidx.ui.core.gesture.DragObserver
import androidx.ui.core.gesture.PressGestureDetector
import androidx.ui.core.setContent
import androidx.ui.core.sp
import androidx.ui.engine.geometry.Offset
import androidx.ui.engine.geometry.Rect
import androidx.ui.graphics.Color
import androidx.ui.layout.Column
import androidx.ui.layout.Container
import androidx.ui.layout.Padding
import androidx.ui.graphics.Paint
import androidx.ui.text.TextStyle

class AnimatableSeekBar : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val clock = remember { ManualAnimationClock(0L) }
            AnimationClockAmbient.Provider(clock) {
                Column {
                    Padding(40.dp) {
                        Text("Drag or tap on the seek bar", style = TextStyle(fontSize = 20.sp))
                    }

                    Padding(left = 10.dp, right = 10.dp, bottom = 30.dp) {
                        MovingTargetExample(clock)
                    }

                    Transition(definition = transDef, initState = "start", toState = "end") {
                            state ->
                        Container(width = 600.dp, height = 400.dp) {
                            val paint = remember { Paint() }
                            Draw { canvas, parentSize ->
                                val rect = Rect(0f, 0f, parentSize.width.value * 0.2f,
                                        parentSize.width.value * 0.2f)
                                canvas.drawRect(rect, paint.apply {
                                    color = Color(1.0f, 0f, 0f, state[alphaKey])
                                })

                                canvas.drawRect(rect.translate(state[offset1] * parentSize.width
                                    .value, 0f),
                                    paint.apply {
                                        color = Color(0f, 0f, 1f, state[alphaKey])
                                    })

                                canvas.drawRect(rect.translate(state[offset2] * parentSize.width
                                    .value, 0f),
                                    paint.apply {
                                        color = Color(0f, 1f, 1f, state[alphaKey])
                                    })

                                canvas.drawRect(rect.translate(state[offset3] * parentSize.width
                                    .value, 0f),
                                    paint.apply {
                                        color = Color(0f, 1f, 0f, state[alphaKey])
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MovingTargetExample(clock: ManualAnimationClock) {
        val animValue = animatedFloat(0f)
            RawDragGestureDetector(dragObserver = object : DragObserver {
                override fun onDrag(dragDistance: PxPosition): PxPosition {
                    animValue.snapTo(animValue.targetValue + dragDistance.x.value)
                    return dragDistance
                }
            }) {
                PressGestureDetector(
                    onPress = { position ->
                        animValue.animateTo(position.x.value,
                            PhysicsBuilder(dampingRatio = 1.0f, stiffness = 1500f))
                    }) {

                    Container(height = 60.dp, expanded = true) {
                        DrawSeekBar(animValue.value, clock)
                    }
                }
            }
        }

    @Composable
    fun DrawSeekBar(x: Float, clock: ManualAnimationClock) {
        val paint = remember { Paint() }
        Draw { canvas, parentSize ->
            val centerY = parentSize.height.value / 2
            val xConstraint = x.coerceIn(0f, parentSize.width.value)
            clock.clockTimeMillis = (400 * (x / parentSize.width.value)).toLong().coerceIn(0, 399)
            // draw bar
            paint.color = Color.Gray
            canvas.drawRect(
                Rect(0f, centerY - 5, parentSize.width.value, centerY + 5),
                paint
            )
            paint.color = Color.Magenta
            canvas.drawRect(
                Rect(0f, centerY - 5, xConstraint, centerY + 5),
                paint
            )

            // draw ticker
            canvas.drawCircle(
                Offset(xConstraint, centerY), 40f, paint
            )
        }
    }
}

private val alphaKey = FloatPropKey()
private val offset1 = FloatPropKey()
private val offset2 = FloatPropKey()
private val offset3 = FloatPropKey()

private val transDef = transitionDefinition {

    state("start") {
        this[alphaKey] = 1f
        this[offset1] = 0f
        this[offset2] = 0f
        this[offset3] = 0f
    }

    state("end") {
        this[alphaKey] = 0.2f
        this[offset1] = 0.26f
        this[offset2] = 0.53f
        this[offset3] = 0.8f
    }

    transition {
        alphaKey using tween {
            easing = FastOutSlowInEasing
            duration = 400
        }
        offset1 using tween {
            easing = FastOutSlowInEasing
            duration = 400
        }
        offset2 using tween {
            easing = FastOutSlowInEasing
            duration = 400
        }
        offset3 using tween {
            easing = FastOutSlowInEasing
            duration = 400
        }
    }
}

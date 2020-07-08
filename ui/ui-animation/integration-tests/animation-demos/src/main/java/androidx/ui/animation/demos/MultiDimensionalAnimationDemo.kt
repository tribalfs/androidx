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

package androidx.ui.animation.demos

import androidx.animation.spring
import androidx.animation.transitionDefinition
import androidx.animation.tween
import androidx.compose.Composable
import androidx.compose.remember
import androidx.compose.state
import androidx.compose.structuralEqualityPolicy
import androidx.ui.animation.ColorPropKey
import androidx.ui.animation.RectPropKey
import androidx.ui.animation.transition
import androidx.ui.core.Modifier
import androidx.ui.foundation.Canvas
import androidx.ui.foundation.clickable
import androidx.ui.geometry.Offset
import androidx.ui.geometry.Rect
import androidx.ui.geometry.Size
import androidx.ui.graphics.Color
import androidx.ui.layout.fillMaxSize

@Composable
fun MultiDimensionalAnimationDemo() {
    val currentState = state { AnimState.Collapsed }
    val onClick = {
        // Cycle through states when clicked.
        currentState.value = when (currentState.value) {
            AnimState.Collapsed -> AnimState.Expanded
            AnimState.Expanded -> AnimState.PutAway
            AnimState.PutAway -> AnimState.Collapsed
        }
    }

    val width = state(policy = structuralEqualityPolicy()) { 0f }
    val height = state(policy = structuralEqualityPolicy()) { 0f }
    val state = transition(
        definition = remember(width.value, height.value) {
            createTransDef(width.value, height.value)
        },
        toState = currentState.value
    )
    Canvas(modifier = Modifier.fillMaxSize().clickable(onClick = onClick, indication = null)) {
        width.value = size.width
        height.value = size.height

        val bounds = state[bounds]
        drawRect(
            state[background],
            topLeft = Offset(bounds.left, bounds.top),
            size = Size(bounds.width, bounds.height)
        )
    }
}

private enum class AnimState {
    Collapsed,
    Expanded,
    PutAway
}

// Both PropKeys below are multi-dimensional property keys. That means each dimension's
// value and velocity will be tracked independently. In the case of a color, each color
// channel is a separate dimension. For rectangles, the dimensions are: top, left,
// right and bottom.
private val background = ColorPropKey()
private val bounds = RectPropKey()

private fun createTransDef(width: Float, height: Float) =
    transitionDefinition {
        state(AnimState.Collapsed) {
            this[background] = Color.LightGray
            this[bounds] = Rect(600f, 600f, 900f, 900f)
        }
        state(AnimState.Expanded) {
            this[background] = Color(0xFFd0fff8)
            this[bounds] = Rect(0f, 400f, width, height - 400f)
        }
        state(AnimState.PutAway) {
            this[background] = Color(0xFFe3ffd9)
            this[bounds] = Rect(width - 300f, height - 300f, width, height)
        }

        transition {
            bounds using spring(
                stiffness = 100f
            )
            background using tween(
                durationMillis = 500
            )
        }
    }
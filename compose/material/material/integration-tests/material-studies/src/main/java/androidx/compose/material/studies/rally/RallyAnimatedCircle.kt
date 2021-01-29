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

package androidx.compose.material.studies.rally

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val DividerLengthInDegrees = 1.8f

/** when calculating a proportion of N elements, the sum of elements has to be (1 - N * 0.005)
 * because there will be N dividers of size 1.8 degrees */
@Composable
fun AnimatedCircle(
    modifier: Modifier = Modifier,
    proportions: List<Float>,
    colors: List<Color>
) {
    val stroke = Stroke(5.dp.value * LocalDensity.current.density)
    // Start animating when added to the tree
    val states = remember { MutableTransitionState(0).apply { targetState = 1 } }
    val transition = updateTransition(states)
    val angleOffset by transition.animateFloat(
        transitionSpec = {
            if (0 isTransitioningTo 1) {
                tween(
                    delayMillis = 500,
                    durationMillis = 900,
                    easing = CubicBezierEasing(0f, 0.75f, 0.35f, 0.85f)
                )
            } else {
                spring()
            }
        }
    ) { if (it == 1) 360f else 0f }
    val shift by transition.animateFloat(
        transitionSpec = {
            if (0 isTransitioningTo 1) {
                tween(
                    delayMillis = 500,
                    durationMillis = 900,
                    easing = LinearOutSlowInEasing
                )
            } else {
                spring()
            }
        }
    ) {
        if (it == 1) 30f else 0f
    }
    Canvas(modifier) {
        val innerRadius = (size.minDimension - stroke.width) / 2
        val halfSize = size / 2.0f
        val topLeft = Offset(
            halfSize.width - innerRadius,
            halfSize.height - innerRadius
        )
        val size = Size(innerRadius * 2, innerRadius * 2)
        var startAngle = shift - 90f
        proportions.forEachIndexed { index, proportion ->
            val sweep = proportion * angleOffset
            drawArc(
                color = colors[index],
                startAngle = startAngle + DividerLengthInDegrees / 2,
                sweepAngle = sweep - DividerLengthInDegrees,
                topLeft = topLeft,
                size = size,
                useCenter = false,
                style = stroke
            )
            startAngle += sweep
        }
    }
}
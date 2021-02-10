/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.foundation.gestures

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatePriority
import androidx.compose.runtime.withFrameNanos

/**
 * Scroll by [value] pixels with animation.
 *
 * Cancels the currently running scroll, if any, and suspends until the cancellation is
 * complete.
 *
 * @param value number of pixels to scroll by
 * @param animationSpec [AnimationSpec] to be used for this scrolling
 *
 * @return the amount of scroll consumed
 */
suspend fun ScrollableState.animateScrollBy(
    value: Float,
    animationSpec: AnimationSpec<Float> = spring()
): Float {
    val animSpec = animationSpec.vectorize(Float.VectorConverter)
    val conv = Float.VectorConverter
    val zeroVector = conv.convertToVector(0f)
    val targetVector = conv.convertToVector(value)
    var previousValue = 0f

    scroll {
        val startTimeNanos = withFrameNanos { it }
        do {
            val finished = withFrameNanos { frameTimeNanos ->
                val newValue = conv.convertFromVector(
                    animSpec.getValueFromNanos(
                        playTimeNanos = frameTimeNanos - startTimeNanos,
                        initialValue = zeroVector,
                        targetValue = targetVector,
                        // TODO: figure out if/how we should incorporate existing velocity
                        initialVelocity = zeroVector
                    )
                )
                val delta = newValue - previousValue
                val consumed = scrollBy(delta)

                if (consumed != delta) {
                    previousValue += consumed
                    true
                } else {
                    previousValue = newValue
                    previousValue == value
                }
            }
        } while (!finished)
    }
    return previousValue
}

/**
 * Jump instantly by [value] pixels.
 *
 * Cancels the currently running scroll, if any, and suspends until the cancellation is
 * complete.
 *
 * @see animateScrollBy for an animated version
 *
 * @param value number of pixels to scroll by
 * @return the amount of scroll consumed
 */
suspend fun ScrollableState.scrollBy(value: Float): Float {
    var consumed = 0f
    scroll {
        consumed = scrollBy(value)
    }
    return consumed
}

/**
 * Stop and suspend until any ongoing animation, smooth scrolling, fling, or any other scroll
 * occurring via [ScrollableState.scroll] is terminated.
 *
 * @param scrollPriority scrolls that run with this priority or lower will be stopped
 */
suspend fun ScrollableState.stopScroll(scrollPriority: MutatePriority = MutatePriority.Default) {
    scroll(scrollPriority) {
        // do nothing, just lock the mutex so other scroll actors are cancelled
    }
}
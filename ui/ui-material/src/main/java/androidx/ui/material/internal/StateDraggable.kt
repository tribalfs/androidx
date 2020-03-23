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

package androidx.ui.material.internal

import androidx.animation.AnimatedFloat
import androidx.animation.AnimationBuilder
import androidx.animation.AnimationEndReason
import androidx.compose.Composable
import androidx.compose.onCommit
import androidx.compose.remember
import androidx.compose.state
import androidx.ui.animation.animatedFloat
import androidx.ui.core.PassThroughLayout
import androidx.ui.foundation.animation.AnchorsFlingConfig
import androidx.ui.foundation.animation.fling
import androidx.ui.foundation.gestures.DragDirection
import androidx.ui.foundation.gestures.draggable

/**
 * Higher-level component that allows dragging around anchored positions binded to different states
 *
 * Example might be a Switch which you can drag between two states (true or false).
 *
 * Additional features compared to regular [draggable] modifier:
 * 1. The AnimatedFloat hosted inside and its value will be in sync with call site state
 * 2. When the anchor is reached, [onStateChange] will be called with state mapped to this anchor
 * 3. When the anchor is reached and [onStateChange] with corresponding state is called, but
 * call site didn't update state to the reached one for some reason,
 * this component performs rollback to the previous (correct) state.
 * 4. When new [state] is provided, component will be animated to state's anchor
 *
 * children of this composable will receive [AnimatedFloat] class from which
 * they can read current value when they need or manually animate.
 *
 * @param T type with which state is represented
 * @param state current state to represent Float value with
 * @param onStateChange callback to update call site's state
 * @param anchorsToState pairs of anchors to states to map anchors to state and vise versa
 * @param animationBuilder animation which will be used for animations
 * @param dragDirection direction in which drag should be happening.
 * Either [DragDirection.Vertical] or [DragDirection.Horizontal]
 * @param minValue lower bound for draggable value in this component
 * @param maxValue upper bound for draggable value in this component
 * @param enabled whether or not this Draggable is enabled and should consume events
 */
// TODO(malkov/tianliu) (figure our how to make it better and make public)
@Composable
internal fun <T> StateDraggable(
    state: T,
    onStateChange: (T) -> Unit,
    anchorsToState: List<Pair<Float, T>>,
    animationBuilder: AnimationBuilder<Float>,
    dragDirection: DragDirection,
    enabled: Boolean = true,
    minValue: Float = Float.MIN_VALUE,
    maxValue: Float = Float.MAX_VALUE,
    children: @Composable() (AnimatedFloat) -> Unit
) {
    val forceAnimationCheck = state { true }

    val anchors = remember(anchorsToState) { anchorsToState.map { it.first } }
    val currentValue = anchorsToState.firstOrNull { it.second == state }!!.first
    val flingConfig =
        AnchorsFlingConfig(anchors, animationBuilder, onAnimationEnd = { reason, finalValue, _ ->
            if (reason != AnimationEndReason.Interrupted) {
                val newState = anchorsToState.firstOrNull { it.first == finalValue }?.second
                if (newState != null && newState != state) {
                    onStateChange(newState)
                    forceAnimationCheck.value = !forceAnimationCheck.value
                }
            }
        })
    val position = animatedFloat(currentValue)
    position.setBounds(minValue, maxValue)

    // This state is to force this component to be recomposed and trigger onCommit below
    // This is needed to stay in sync with drag state that caller side holds
    onCommit(currentValue, forceAnimationCheck.value) {
        position.animateTo(currentValue, animationBuilder)
    }
    val draggable = draggable(
        dragDirection = dragDirection,
        onDragDeltaConsumptionRequested = { delta ->
            val old = position.value
            position.snapTo(position.value + delta)
            position.value - old
        },
        onDragStopped = { position.fling(flingConfig, it) },
        enabled = enabled,
        startDragImmediately = position.isRunning
    )
    // TODO(b/150706555): This layout is temporary and should be removed once Semantics
    //  is implemented with modifiers.
    @Suppress("DEPRECATION")
    PassThroughLayout(draggable) {
        children(position)
    }
}
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

package androidx.ui.foundation

import androidx.compose.Composable
import androidx.compose.onCommit
import androidx.compose.remember
import androidx.ui.core.Modifier
import androidx.ui.core.composed
import androidx.ui.core.gesture.doubleTapGestureFilter
import androidx.ui.core.gesture.longPressGestureFilter
import androidx.ui.core.gesture.pressIndicatorGestureFilter
import androidx.ui.core.gesture.tapGestureFilter
import androidx.ui.core.semantics.semantics
import androidx.ui.semantics.enabled
import androidx.ui.semantics.onClick

/**
 * Configure component to receive clicks via input or accessibility "click" event.
 *
 * Add this modifier to the element to make it clickable within its bounds.
 *
 * @sample androidx.ui.foundation.samples.ClickableSample
 *
 * @param enabled Controls the enabled state. When `false`, [onClick], [onLongClick] or
 * [onDoubleClick] won't be invoked
 * @param onClickLabel semantic / accessibility label for the [onClick] action
 * @param interactionState [InteractionState] that will be updated when this Clickable is
 * pressed, using [Interaction.Pressed]. Only initial (first) press will be recorded and added to
 * [InteractionState]
 * @param indication indication to be shown when modified element is pressed. Be default,
 * indication from [IndicationAmbient] will be used. Pass `null` to show no indication
 * @param onLongClick will be called when user long presses on the element
 * @param onDoubleClick will be called when user double clicks on the element
 * @param onClick will be called when user clicks on the element
 */
@Composable
fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    interactionState: InteractionState = remember { InteractionState() },
    indication: Indication? = IndicationAmbient.current(),
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
) = composed {
    val semanticModifier = Modifier.semantics(
        mergeAllDescendants = true,
        properties = {
            this.enabled = enabled
            if (enabled) {
                // b/156468846:  add long click semantics and double click if needed
                onClick(action = { onClick(); return@onClick true }, label = onClickLabel)
            }
        }
    )
    val interactionUpdate =
        if (enabled) {
            Modifier.pressIndicatorGestureFilter(
                onStart = { interactionState.addInteraction(Interaction.Pressed, it) },
                onStop = { interactionState.removeInteraction(Interaction.Pressed) },
                onCancel = { interactionState.removeInteraction(Interaction.Pressed) }
            )
        } else {
            Modifier
        }
    val tap = if (enabled) tapGestureFilter(onTap = { onClick() }) else Modifier
    val longTap = if (enabled && onLongClick != null) {
        longPressGestureFilter(onLongPress = { onLongClick() })
    } else {
        Modifier
    }
    val doubleTap =
        if (enabled && onDoubleClick != null) {
            doubleTapGestureFilter(onDoubleTap = { onDoubleClick() })
        } else {
            Modifier
        }
    onCommit(interactionState) {
        onDispose {
            interactionState.removeInteraction(Interaction.Pressed)
        }
    }
    semanticModifier
        .plus(interactionUpdate)
        .indication(interactionState, indication)
        .plus(tap)
        .plus(longTap)
        .plus(doubleTap)
}

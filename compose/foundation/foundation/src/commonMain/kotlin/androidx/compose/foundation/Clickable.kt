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

package androidx.compose.foundation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.foundation.legacygestures.pressIndicatorGestureFilter
import androidx.compose.foundation.legacygestures.tapGestureFilter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics

/**
 * Configure component to receive clicks via input or accessibility "click" event.
 *
 * Add this modifier to the element to make it clickable within its bounds and show a default
 * indication when it's pressed.
 *
 * This version has no [InteractionState] or [Indication] parameters, default indication from
 * [LocalIndication] will be used. To specify [InteractionState] or [Indication], use another
 * overload.
 *
 * If you need to support double click or long click alongside the single click, consider
 * using [combinedClickable].
 *
 * @sample androidx.compose.foundation.samples.ClickableSample
 *
 * @param enabled Controls the enabled state. When `false`, [onClick], and this modifier will
 * appear disabled for accessibility services
 * @param onClickLabel semantic / accessibility label for the [onClick] action
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param onClick will be called when user clicks on the element
 */
fun Modifier.clickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "clickable"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["onClick"] = onClick
    }
) {
    Modifier.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        onClick = onClick,
        role = role,
        indication = LocalIndication.current,
        interactionState = remember { InteractionState() }
    )
}

/**
 * Configure component to receive clicks via input or accessibility "click" event.
 *
 * Add this modifier to the element to make it clickable within its bounds and show an indication
 * as specified in [indication] parameter.
 *
 * If you need to support double click or long click alongside the single click, consider
 * using [combinedClickable].
 *
 * @sample androidx.compose.foundation.samples.ClickableSample
 *
 * @param interactionState [InteractionState] that will be updated when this Clickable is
 * pressed, using [Interaction.Pressed]. Only initial (first) press will be recorded and added to
 * [InteractionState]
 * @param indication indication to be shown when modified element is pressed. Be default,
 * indication from [LocalIndication] will be used. Pass `null` to show no indication, or
 * current value from [LocalIndication] to show theme default
 * @param enabled Controls the enabled state. When `false`, [onClick], and this modifier will
 * appear disabled for accessibility services
 * @param onClickLabel semantic / accessibility label for the [onClick] action
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param onClick will be called when user clicks on the element
 */
@Suppress("DEPRECATION")
fun Modifier.clickable(
    interactionState: InteractionState,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
) = composed(
    factory = {
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
        Modifier
            .genericClickableWithoutGesture(
                gestureModifiers = Modifier.then(interactionUpdate).then(tap),
                interactionState = interactionState,
                indication = indication,
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = role,
                onLongClickLabel = null,
                onLongClick = null,
                onClick = onClick
            )
    },
    inspectorInfo = debugInspectorInfo {
        name = "clickable"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["onClick"] = onClick
        properties["indication"] = indication
        properties["interactionState"] = interactionState
    }
)

/**
 * Configure component to receive clicks, double clicks and long clicks via input or accessibility
 * "click" event.
 *
 * Add this modifier to the element to make it clickable within its bounds.
 *
 * If you need only click handling, and no double or long clicks, consider using [clickable]
 *
 * This version has no [InteractionState] or [Indication] parameters, default indication from
 * [LocalIndication] will be used. To specify [InteractionState] or [Indication], use another
 * overload.
 *
 * @sample androidx.compose.foundation.samples.ClickableSample
 *
 * @param enabled Controls the enabled state. When `false`, [onClick], [onLongClick] or
 * [onDoubleClick] won't be invoked
 * @param onClickLabel semantic / accessibility label for the [onClick] action
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param onLongClickLabel semantic / accessibility label for the [onLongClick] action
 * @param onLongClick will be called when user long presses on the element
 * @param onDoubleClick will be called when user double clicks on the element
 * @param onClick will be called when user clicks on the element
 */
@ExperimentalFoundationApi
fun Modifier.combinedClickable(
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
) = composed(
    inspectorInfo = debugInspectorInfo {
        name = "combinedClickable"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["onClick"] = onClick
        properties["onDoubleClick"] = onDoubleClick
        properties["onLongClick"] = onLongClick
        properties["onLongClickLabel"] = onLongClickLabel
    }
) {
    Modifier.combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        onClick = onClick,
        role = role,
        indication = LocalIndication.current,
        interactionState = remember { InteractionState() }
    )
}

/**
 * Configure component to receive clicks, double clicks and long clicks via input or accessibility
 * "click" event.
 *
 * Add this modifier to the element to make it clickable within its bounds.
 *
 * If you need only click handling, and no double or long clicks, consider using [clickable].
 *
 * Add this modifier to the element to make it clickable within its bounds.
 *
 * @sample androidx.compose.foundation.samples.ClickableSample
 *
 * @param interactionState [InteractionState] that will be updated when this Clickable is
 * pressed, using [Interaction.Pressed]. Only initial (first) press will be recorded and added to
 * [InteractionState]
 * @param indication indication to be shown when modified element is pressed. Be default,
 * indication from [LocalIndication] will be used. Pass `null` to show no indication, or
 * current value from [LocalIndication] to show theme default
 * @param enabled Controls the enabled state. When `false`, [onClick], [onLongClick] or
 * [onDoubleClick] won't be invoked
 * @param onClickLabel semantic / accessibility label for the [onClick] action
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param onLongClickLabel semantic / accessibility label for the [onLongClick] action
 * @param onLongClick will be called when user long presses on the element
 * @param onDoubleClick will be called when user double clicks on the element
 * @param onClick will be called when user clicks on the element
 */
@ExperimentalFoundationApi
fun Modifier.combinedClickable(
    interactionState: InteractionState,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
) = composed(
    factory = {
        val onClickState = rememberUpdatedState(onClick)
        val interactionStateState = rememberUpdatedState(interactionState)
        val gesture = if (enabled) {
            Modifier.pointerInput(onDoubleClick, onLongClick) {
                detectTapGestures(
                    onDoubleTap = if (onDoubleClick != null) {
                        { onDoubleClick() }
                    } else {
                        null
                    },
                    onLongPress = if (onLongClick != null) {
                        { onLongClick() }
                    } else {
                        null
                    },
                    onPress = {
                        interactionStateState.value.addInteraction(Interaction.Pressed, it)
                        tryAwaitRelease()
                        interactionStateState.value.removeInteraction(Interaction.Pressed)
                    },
                    onTap = { onClickState.value.invoke() }
                )
            }
        } else {
            Modifier
        }
        Modifier
            .genericClickableWithoutGesture(
                gestureModifiers = gesture,
                interactionState = interactionState,
                indication = indication,
                enabled = enabled,
                onClickLabel = onClickLabel,
                role = role,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick,
                onClick = onClick
            )
    },
    inspectorInfo = debugInspectorInfo {
        name = "combinedClickable"
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["onClick"] = onClick
        properties["onDoubleClick"] = onDoubleClick
        properties["onLongClick"] = onLongClick
        properties["onLongClickLabel"] = onLongClickLabel
        properties["indication"] = indication
        properties["interactionState"] = interactionState
    }
)

@Composable
@Suppress("ComposableModifierFactory")
internal fun Modifier.genericClickableWithoutGesture(
    gestureModifiers: Modifier,
    interactionState: InteractionState,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    val semanticModifier = Modifier.semantics(mergeDescendants = true) {
        if (role != null) {
            this.role = role
        }
        // b/156468846:  add long click semantics and double click if needed
        onClick(action = { onClick(); true }, label = onClickLabel)
        if (onLongClick != null) {
            onLongClick(action = { onLongClick(); true }, label = onLongClickLabel)
        }
        if (!enabled) {
            disabled()
        }
    }
    DisposableEffect(interactionState) {
        onDispose {
            interactionState.removeInteraction(Interaction.Pressed)
        }
    }
    return this
        .then(semanticModifier)
        .indication(interactionState, indication)
        .then(gestureModifiers)
}
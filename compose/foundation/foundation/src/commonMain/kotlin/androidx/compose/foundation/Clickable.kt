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

import androidx.compose.foundation.gestures.ModifierLocalScrollableContainer
import androidx.compose.foundation.gestures.PressGestureScope
import androidx.compose.foundation.gestures.detectTapAndPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusTarget
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.modifier.ModifierLocalModifierNode
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.platform.inspectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.center
import androidx.compose.ui.unit.toOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Configure component to receive clicks via input or accessibility "click" event.
 *
 * Add this modifier to the element to make it clickable within its bounds and show a default
 * indication when it's pressed.
 *
 * This version has no [MutableInteractionSource] or [Indication] parameters, the default indication
 * from [LocalIndication] will be used. To specify [MutableInteractionSource] or [Indication], use
 * the other overload.
 *
 * If you are only creating this clickable modifier inside composition, consider using the other
 * overload and explicitly passing `LocalIndication.current` for improved performance. For more
 * information see the documentation on the other overload.
 *
 * If you need to support double click or long click alongside the single click, consider
 * using [combinedClickable].
 *
 * ***Note*** Any removal operations on Android Views from `clickable` should wrap `onClick` in a
 * `post { }` block to guarantee the event dispatch completes before executing the removal. (You
 * do not need to do this when removing a composable because Compose guarantees it completes via the
 * snapshot state system.)
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
    val localIndication = LocalIndication.current
    val interactionSource = if (localIndication is IndicationNodeFactory) {
        // We can fast path here as it will be created inside clickable lazily
        null
    } else {
        // We need an interaction source to pass between the indication modifier and clickable, so
        // by creating here we avoid another composed down the line
        remember { MutableInteractionSource() }
    }
    Modifier.clickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        onClick = onClick,
        role = role,
        indication = localIndication,
        interactionSource = interactionSource
    )
}

/**
 * Configure component to receive clicks via input or accessibility "click" event.
 *
 * Add this modifier to the element to make it clickable within its bounds and show an indication
 * as specified in [indication] parameter.
 *
 * By default, if [interactionSource] is `null`, and [indication] is an [IndicationNodeFactory], an
 * internal [MutableInteractionSource] will be lazily created along with the [indication] only when
 * needed. This reduces the performance cost of clickable during composition, as creating the
 * [indication] can be delayed until there is an incoming
 * [androidx.compose.foundation.interaction.Interaction]. If you are only passing a remembered
 * [MutableInteractionSource] and you are never using it outside of clickable, it is recommended to
 * instead provide `null` to enable lazy creation.
 * If you are providing a [MutableInteractionSource], but you are only observing the
 * [MutableInteractionSource] and never emitting interactions, you can explicitly enable lazy
 * creation using [lazilyCreateIndication].
 * If you are emitting interactions or you need the [indication] to be created immediately, you can
 * pass `false` to [lazilyCreateIndication]. Note that [lazilyCreateIndication] only applies for
 * [IndicationNodeFactory] [indication]s. [Indication] instances using the deprecated
 * [Indication.rememberUpdatedInstance] API can not be lazily created.
 *
 * If [indication] is _not_ an [IndicationNodeFactory], and instead implements the deprecated
 * [Indication.rememberUpdatedInstance] method, you should explicitly pass a remembered
 * [MutableInteractionSource] as a parameter for [interactionSource] instead of `null`, as this
 * cannot be lazily created inside clickable.
 *
 * If you need to support double click or long click alongside the single click, consider
 * using [combinedClickable].
 *
 * ***Note*** Any removal operations on Android Views from `clickable` should wrap `onClick` in a
 * `post { }` block to guarantee the event dispatch completes before executing the removal. (You
 * do not need to do this when removing a composable because Compose guarantees it completes via the
 * snapshot state system.)
 *
 * @sample androidx.compose.foundation.samples.ClickableSample
 *
 * @param interactionSource [MutableInteractionSource] that will be used to dispatch
 * [PressInteraction.Press] when this clickable is pressed. If `null`, an internal
 * [MutableInteractionSource] will be created if needed.
 * @param indication indication to be shown when modified element is pressed. By default,
 * indication from [LocalIndication] will be used. Pass `null` to show no indication, or
 * current value from [LocalIndication] to show theme default
 * @param enabled Controls the enabled state. When `false`, [onClick], and this modifier will
 * appear disabled for accessibility services
 * @param onClickLabel semantic / accessibility label for the [onClick] action
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param lazilyCreateIndication if `true` (recommended for most cases), and [indication] is an
 * [IndicationNodeFactory], [indication] will only be created when this clickable emits an
 * [androidx.compose.foundation.interaction.Interaction]. If [interactionSource] is `null`, or
 * you are only reading from the [interactionSource] and never emitting an interaction, you should
 * typically provide true. If you are emitting an interaction, or you need the indication to be
 * eagerly created, provide false. Note that this parameter has no effect if [indication] is not
 * an [IndicationNodeFactory].
 * @param onClick will be called when user clicks on the element
 */
fun Modifier.clickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    lazilyCreateIndication: Boolean = (interactionSource == null) &&
        (indication is IndicationNodeFactory),
    onClick: () -> Unit
) = inspectable(
    inspectorInfo = debugInspectorInfo {
        name = "clickable"
        properties["interactionSource"] = interactionSource
        properties["indication"] = indication
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["lazilyCreateIndication"] = lazilyCreateIndication
        properties["onClick"] = onClick
    }
) {
    val clickableModifier = when {
        // Fast path - indication is managed internally
        indication is IndicationNodeFactory -> ClickableElement(
            interactionSource = interactionSource,
            indicationNodeFactory = indication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            lazilyCreateIndication = lazilyCreateIndication,
            onClick = onClick
        )
        // Fast path - no need for indication
        indication == null -> ClickableElement(
            interactionSource = interactionSource,
            indicationNodeFactory = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            lazilyCreateIndication = lazilyCreateIndication,
            onClick = onClick
        )
        // Non-null Indication (not IndicationNodeFactory) with a non-null InteractionSource
        interactionSource != null -> Modifier
            .indication(interactionSource, indication)
            .then(
                ClickableElement(
                    interactionSource = interactionSource,
                    indicationNodeFactory = null,
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = role,
                    lazilyCreateIndication = lazilyCreateIndication,
                    onClick = onClick
                )
            )
        // Non-null Indication (not IndicationNodeFactory) with a null InteractionSource, so we need
        // to use composed to create an InteractionSource that can be shared. This should be a rare
        // code path and can only be hit from new callers.
        else ->
            Modifier
                .composed {
                    val newInteractionSource = remember { MutableInteractionSource() }
                    Modifier
                        .indication(newInteractionSource, indication)
                        .then(
                            ClickableElement(
                                interactionSource = newInteractionSource,
                                indicationNodeFactory = null,
                                enabled = enabled,
                                onClickLabel = onClickLabel,
                                role = role,
                                lazilyCreateIndication = lazilyCreateIndication,
                                onClick = onClick
                            )
                        )
                }
    }
    clickableModifier.then(if (enabled) Modifier.focusTarget() else Modifier)
}

@Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
fun Modifier.clickable(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onClick: () -> Unit
): Modifier = clickable(
    interactionSource = interactionSource,
    indication = indication,
    enabled = enabled,
    onClickLabel = onClickLabel,
    role = role,
    lazilyCreateIndication = false,
    onClick = onClick
)

/**
 * Configure component to receive clicks, double clicks and long clicks via input or accessibility
 * "click" event.
 *
 * Add this modifier to the element to make it clickable within its bounds.
 *
 * If you need only click handling, and no double or long clicks, consider using [clickable]
 *
 * This version has no [MutableInteractionSource] or [Indication] parameters, the default indication
 * from [LocalIndication] will be used. To specify [MutableInteractionSource] or [Indication], use
 * the other overload.
 *
 * If you are only creating this combinedClickable modifier inside composition, consider using the
 * other overload and explicitly passing `LocalIndication.current` for improved performance. For
 * more information see the documentation on the other overload.
 *
 * ***Note*** Any removal operations on Android Views from `clickable` should wrap `onClick` in a
 * `post { }` block to guarantee the event dispatch completes before executing the removal. (You
 * do not need to do this when removing a composable because Compose guarantees it completes via the
 * snapshot state system.)
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
 *
 * Note: This API is experimental and is awaiting a rework. combinedClickable handles touch based
 * input quite well but provides subpar functionality for other input types.
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
    val localIndication = LocalIndication.current
    val interactionSource = if (localIndication is IndicationNodeFactory) {
        // We can fast path here as it will be created inside clickable lazily
        null
    } else {
        // We need an interaction source to pass between the indication modifier and clickable, so
        // by creating here we avoid another composed down the line
        remember { MutableInteractionSource() }
    }
    Modifier.combinedClickable(
        enabled = enabled,
        onClickLabel = onClickLabel,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onDoubleClick = onDoubleClick,
        onClick = onClick,
        role = role,
        indication = localIndication,
        interactionSource = interactionSource
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
 * By default, if [interactionSource] is `null`, and [indication] is an [IndicationNodeFactory], an
 * internal [MutableInteractionSource] will be lazily created along with the [indication] only when
 * needed. This reduces the performance cost of clickable during composition, as creating the
 * [indication] can be delayed until there is an incoming
 * [androidx.compose.foundation.interaction.Interaction]. If you are only passing a remembered
 * [MutableInteractionSource] and you are never using it outside of clickable, it is recommended to
 * instead provide `null` to enable lazy creation.
 * If you are providing a [MutableInteractionSource], but you are only observing the
 * [MutableInteractionSource] and never emitting interactions, you can explicitly enable lazy
 * creation using [lazilyCreateIndication].
 * If you are emitting interactions or you need the [indication] to be created immediately, you can
 * pass `false` to [lazilyCreateIndication]. Note that [lazilyCreateIndication] only applies for
 * [IndicationNodeFactory] [indication]s. [Indication] instances using the deprecated
 * [Indication.rememberUpdatedInstance] API can not be lazily created.
 *
 * If [indication] is _not_ an [IndicationNodeFactory], and instead implements the deprecated
 * [Indication.rememberUpdatedInstance] method, you should explicitly pass a remembered
 * [MutableInteractionSource] as a parameter for [interactionSource] instead of `null`, as this
 * cannot be lazily created inside clickable.
 *
 * ***Note*** Any removal operations on Android Views from `clickable` should wrap `onClick` in a
 * `post { }` block to guarantee the event dispatch completes before executing the removal. (You
 * do not need to do this when removing a composable because Compose guarantees it completes via the
 * snapshot state system.)
 *
 * @sample androidx.compose.foundation.samples.ClickableSample
 *
 * @param interactionSource [MutableInteractionSource] that will be used to emit
 * [PressInteraction.Press] when this clickable is pressed. If `null`, an internal
 * [MutableInteractionSource] will be created if needed.
 * @param indication indication to be shown when modified element is pressed. By default,
 * indication from [LocalIndication] will be used. Pass `null` to show no indication, or
 * current value from [LocalIndication] to show theme default
 * @param enabled Controls the enabled state. When `false`, [onClick], [onLongClick] or
 * [onDoubleClick] won't be invoked
 * @param onClickLabel semantic / accessibility label for the [onClick] action
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param lazilyCreateIndication if `true` (recommended for most cases), and [indication] is an
 * [IndicationNodeFactory], [indication] will only be created when this clickable emits an
 * [androidx.compose.foundation.interaction.Interaction]. If [interactionSource] is `null`, or
 * you are only reading from the [interactionSource] and never emitting an interaction, you should
 * typically provide true. If you are emitting an interaction, or you need the indication to be
 * eagerly created, provide false. Note that this parameter has no effect if [indication] is not
 *  * an [IndicationNodeFactory].
 * @param onLongClickLabel semantic / accessibility label for the [onLongClick] action
 * @param onLongClick will be called when user long presses on the element
 * @param onDoubleClick will be called when user double clicks on the element
 * @param onClick will be called when user clicks on the element
 *
 * Note: This API is experimental and is awaiting a rework. combinedClickable handles touch based
 * input quite well but provides subpar functionality for other input types.
 */
@ExperimentalFoundationApi
fun Modifier.combinedClickable(
    interactionSource: MutableInteractionSource?,
    indication: Indication?,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    lazilyCreateIndication: Boolean = (interactionSource == null) &&
        (indication is IndicationNodeFactory),
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onDoubleClick: (() -> Unit)? = null,
    onClick: () -> Unit
) = inspectable(
    inspectorInfo = debugInspectorInfo {
        name = "combinedClickable"
        properties["indication"] = indication
        properties["interactionSource"] = interactionSource
        properties["enabled"] = enabled
        properties["onClickLabel"] = onClickLabel
        properties["role"] = role
        properties["lazilyCreateIndication"] = lazilyCreateIndication
        properties["onClick"] = onClick
        properties["onDoubleClick"] = onDoubleClick
        properties["onLongClick"] = onLongClick
        properties["onLongClickLabel"] = onLongClickLabel
    }
) {
    val combinedClickableModifier = when {
        // Fast path - indication is managed internally
        indication is IndicationNodeFactory -> CombinedClickableElement(
            interactionSource = interactionSource,
            indicationNodeFactory = indication,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            lazilyCreateIndication = lazilyCreateIndication,
            onClick = onClick,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick
        )
        // Fast path - no need for indication
        indication == null -> CombinedClickableElement(
            interactionSource = interactionSource,
            indicationNodeFactory = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
            lazilyCreateIndication = lazilyCreateIndication,
            onClick = onClick,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick
        )
        // Non-null Indication (not IndicationNodeFactory) with a non-null InteractionSource
        interactionSource != null -> Modifier
            .indication(interactionSource, indication)
            .then(
                CombinedClickableElement(
                    interactionSource = interactionSource,
                    indicationNodeFactory = null,
                    enabled = enabled,
                    onClickLabel = onClickLabel,
                    role = role,
                    lazilyCreateIndication = lazilyCreateIndication,
                    onClick = onClick,
                    onLongClickLabel = onLongClickLabel,
                    onLongClick = onLongClick,
                    onDoubleClick = onDoubleClick
                )
            )
        // Non-null Indication (not IndicationNodeFactory) with a null InteractionSource, so we need
        // to use composed to create an InteractionSource that can be shared. This should be a rare
        // code path and can only be hit from new callers.
        else ->
            Modifier
                .composed {
                    val newInteractionSource = remember { MutableInteractionSource() }
                    Modifier
                        .indication(newInteractionSource, indication)
                        .then(
                            CombinedClickableElement(
                                interactionSource = newInteractionSource,
                                indicationNodeFactory = null,
                                enabled = enabled,
                                onClickLabel = onClickLabel,
                                role = role,
                                lazilyCreateIndication = lazilyCreateIndication,
                                onClick = onClick,
                                onLongClickLabel = onLongClickLabel,
                                onLongClick = onLongClick,
                                onDoubleClick = onDoubleClick
                            )
                        )
                }
    }
    combinedClickableModifier.then(if (enabled) Modifier.focusTarget() else Modifier)
}

private suspend fun PressGestureScope.handlePressInteraction(
    pressPoint: Offset,
    interactionSource: MutableInteractionSource,
    interactionData: AbstractClickableNode.InteractionData,
    delayPressInteraction: () -> Boolean
) {
    coroutineScope {
        val delayJob = launch {
            if (delayPressInteraction()) {
                delay(TapIndicationDelay)
            }
            val press = PressInteraction.Press(pressPoint)
            interactionSource.emit(press)
            interactionData.pressInteraction = press
        }
        val success = tryAwaitRelease()
        if (delayJob.isActive) {
            delayJob.cancelAndJoin()
            // The press released successfully, before the timeout duration - emit the press
            // interaction instantly. No else branch - if the press was cancelled before the
            // timeout, we don't want to emit a press interaction.
            if (success) {
                val press = PressInteraction.Press(pressPoint)
                val release = PressInteraction.Release(press)
                interactionSource.emit(press)
                interactionSource.emit(release)
            }
        } else {
            interactionData.pressInteraction?.let { pressInteraction ->
                val endInteraction = if (success) {
                    PressInteraction.Release(pressInteraction)
                } else {
                    PressInteraction.Cancel(pressInteraction)
                }
                interactionSource.emit(endInteraction)
            }
        }
        interactionData.pressInteraction = null
    }
}

/**
 * How long to wait before appearing 'pressed' (emitting [PressInteraction.Press]) - if a touch
 * down will quickly become a drag / scroll, this timeout means that we don't show a press effect.
 */
internal expect val TapIndicationDelay: Long

/**
 * Returns whether the root Compose layout node is hosted in a scrollable container outside of
 * Compose. On Android this will be whether the root View is in a scrollable ViewGroup, as even if
 * nothing in the Compose part of the hierarchy is scrollable, if the View itself is in a scrollable
 * container, we still want to delay presses in case presses in Compose convert to a scroll outside
 * of Compose.
 *
 * Combine this with [ModifierLocalScrollableContainer], which returns whether a [Modifier] is
 * within a scrollable Compose layout, to calculate whether this modifier is within some form of
 * scrollable container, and hence should delay presses.
 */
internal expect fun CompositionLocalConsumerModifierNode
    .isComposeRootInScrollableContainer(): Boolean

/**
 * Whether the specified [KeyEvent] should trigger a press for a clickable component.
 */
internal expect val KeyEvent.isPress: Boolean

/**
 * Whether the specified [KeyEvent] should trigger a click for a clickable component.
 */
internal expect val KeyEvent.isClick: Boolean

internal fun Modifier.genericClickableWithoutGesture(
    interactionSource: MutableInteractionSource,
    indication: Indication?,
    indicationScope: CoroutineScope,
    currentKeyPressInteractions: MutableMap<Key, PressInteraction.Press>,
    keyClickOffset: State<Offset>,
    enabled: Boolean = true,
    onClickLabel: String? = null,
    role: Role? = null,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier {
    fun Modifier.detectPressAndClickFromKey() = this.onKeyEvent { keyEvent ->
        when {
            enabled && keyEvent.isPress -> {
                // If the key already exists in the map, keyEvent is a repeat event.
                // We ignore it as we only want to emit an interaction for the initial key press.
                if (!currentKeyPressInteractions.containsKey(keyEvent.key)) {
                    val press = PressInteraction.Press(keyClickOffset.value)
                    currentKeyPressInteractions[keyEvent.key] = press
                    indicationScope.launch { interactionSource.emit(press) }
                    true
                } else {
                    false
                }
            }
            enabled && keyEvent.isClick -> {
                currentKeyPressInteractions.remove(keyEvent.key)?.let {
                    indicationScope.launch {
                        interactionSource.emit(PressInteraction.Release(it))
                    }
                }
                onClick()
                true
            }
            else -> false
        }
    }
    return this then
        ClickableSemanticsElement(
            enabled = enabled,
            role = role,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick,
            onClickLabel = onClickLabel,
            onClick = onClick
        )
            .detectPressAndClickFromKey()
            .indication(interactionSource, indication)
            .hoverable(enabled = enabled, interactionSource = interactionSource)
            .focusableInNonTouchMode(enabled = enabled, interactionSource = interactionSource)
}

private class ClickableElement(
    private val interactionSource: MutableInteractionSource?,
    private val indicationNodeFactory: IndicationNodeFactory?,
    private val enabled: Boolean,
    private val onClickLabel: String?,
    private val role: Role?,
    private val lazilyCreateIndication: Boolean,
    private val onClick: () -> Unit
) : ModifierNodeElement<ClickableNode>() {
    override fun create() = ClickableNode(
        interactionSource,
        indicationNodeFactory,
        enabled,
        onClickLabel,
        role,
        lazilyCreateIndication,
        onClick
    )

    override fun update(node: ClickableNode) {
        node.update(
            interactionSource,
            indicationNodeFactory,
            enabled,
            onClickLabel,
            role,
            lazilyCreateIndication,
            onClick
        )
    }

    // Defined in the factory functions with inspectable
    override fun InspectorInfo.inspectableProperties() = Unit

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other === null) return false
        if (this::class != other::class) return false

        other as ClickableElement

        if (interactionSource != other.interactionSource) return false
        if (indicationNodeFactory != other.indicationNodeFactory) return false
        if (enabled != other.enabled) return false
        if (onClickLabel != other.onClickLabel) return false
        if (role != other.role) return false
        if (lazilyCreateIndication != other.lazilyCreateIndication) return false
        if (onClick != other.onClick) return false

        return true
    }

    override fun hashCode(): Int {
        var result = (interactionSource?.hashCode() ?: 0)
        result = 31 * result + (indicationNodeFactory?.hashCode() ?: 0)
        result = 31 * result + enabled.hashCode()
        result = 31 * result + (onClickLabel?.hashCode() ?: 0)
        result = 31 * result + (role?.hashCode() ?: 0)
        result = 31 * result + lazilyCreateIndication.hashCode()
        result = 31 * result + onClick.hashCode()
        return result
    }
}

private class CombinedClickableElement(
    private val interactionSource: MutableInteractionSource?,
    private val indicationNodeFactory: IndicationNodeFactory?,
    private val enabled: Boolean,
    private val onClickLabel: String?,
    private val role: Role?,
    private val lazilyCreateIndication: Boolean,
    private val onClick: () -> Unit,
    private val onLongClickLabel: String?,
    private val onLongClick: (() -> Unit)?,
    private val onDoubleClick: (() -> Unit)?
) : ModifierNodeElement<CombinedClickableNodeImpl>() {
    override fun create() = CombinedClickableNodeImpl(
        onClick,
        onLongClickLabel,
        onLongClick,
        onDoubleClick,
        interactionSource,
        indicationNodeFactory,
        enabled,
        onClickLabel,
        role,
        lazilyCreateIndication
    )

    override fun update(node: CombinedClickableNodeImpl) {
        node.update(
            onClick,
            onLongClickLabel,
            onLongClick,
            onDoubleClick,
            interactionSource,
            indicationNodeFactory,
            enabled,
            onClickLabel,
            role,
            lazilyCreateIndication
        )
    }

    // Defined in the factory functions with inspectable
    override fun InspectorInfo.inspectableProperties() = Unit

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other === null) return false
        if (this::class != other::class) return false

        other as CombinedClickableElement

        if (interactionSource != other.interactionSource) return false
        if (indicationNodeFactory != other.indicationNodeFactory) return false
        if (enabled != other.enabled) return false
        if (onClickLabel != other.onClickLabel) return false
        if (role != other.role) return false
        if (lazilyCreateIndication != other.lazilyCreateIndication) return false
        if (onClick != other.onClick) return false
        if (onLongClickLabel != other.onLongClickLabel) return false
        if (onLongClick != other.onLongClick) return false
        if (onDoubleClick != other.onDoubleClick) return false

        return true
    }

    override fun hashCode(): Int {
        var result = (interactionSource?.hashCode() ?: 0)
        result = 31 * result + (indicationNodeFactory?.hashCode() ?: 0)
        result = 31 * result + enabled.hashCode()
        result = 31 * result + (onClickLabel?.hashCode() ?: 0)
        result = 31 * result + (role?.hashCode() ?: 0)
        result = 31 * result + lazilyCreateIndication.hashCode()
        result = 31 * result + onClick.hashCode()
        result = 31 * result + (onLongClickLabel?.hashCode() ?: 0)
        result = 31 * result + (onLongClick?.hashCode() ?: 0)
        result = 31 * result + (onDoubleClick?.hashCode() ?: 0)
        return result
    }
}

private class ClickableNode(
    interactionSource: MutableInteractionSource?,
    indicationNodeFactory: IndicationNodeFactory?,
    enabled: Boolean,
    onClickLabel: String?,
    role: Role?,
    lazilyCreateIndication: Boolean,
    onClick: () -> Unit
) : AbstractClickableNode(
    interactionSource,
    indicationNodeFactory,
    enabled,
    onClickLabel,
    role,
    lazilyCreateIndication,
    onClick
) {
    override val clickableSemanticsNode = delegate(
        ClickableSemanticsNode(
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
            onLongClick = null,
            onLongClickLabel = null
        )
    )

    override val clickablePointerInputNode = delegate(
        ClickablePointerInputNode(
            enabled = enabled,
            interactionSourceProvider = interactionSourceProvider,
            onClick = onClick,
            interactionData = interactionData
        )
    )

    fun update(
        interactionSource: MutableInteractionSource?,
        indicationNodeFactory: IndicationNodeFactory?,
        enabled: Boolean,
        onClickLabel: String?,
        role: Role?,
        lazilyCreateIndication: Boolean,
        onClick: () -> Unit
    ) {
        updateCommon(
            interactionSource,
            indicationNodeFactory,
            enabled,
            onClickLabel,
            role,
            lazilyCreateIndication,
            onClick
        )
        clickableSemanticsNode.update(
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
            onLongClickLabel = null,
            onLongClick = null
        )
        clickablePointerInputNode.update(
            enabled = enabled,
            onClick = onClick
        )
    }
}

/**
 * Create a [CombinedClickableNode] that can be delegated to inside custom modifier nodes.
 *
 * This API is experimental and is temporarily being exposed to enable performance analysis, you
 * should use [combinedClickable] instead for the majority of use cases.
 *
 * @param onClick will be called when user clicks on the element
 * @param onLongClickLabel semantic / accessibility label for the [onLongClick] action
 * @param onLongClick will be called when user long presses on the element
 * @param onDoubleClick will be called when user double clicks on the element
 * @param interactionSource [MutableInteractionSource] that will be used to emit
 * [PressInteraction.Press] when this clickable is pressed. Only the initial (first) press will be
 * recorded and emitted with [MutableInteractionSource]. If `null`, and there is an
 * [indicationNodeFactory] provided, an internal [MutableInteractionSource] will be created when
 * required. See [lazilyCreateIndication] for more information.
 * @param indicationNodeFactory the [IndicationNodeFactory] used to optionally render
 * [Indication] inside this node, instead of using a separate [Modifier.indication]. This should
 * be preferred for performance reasons over using [Modifier.indication] separately.
 * @param enabled Controls the enabled state. When false, [onClick], [onLongClick] or
 * [onDoubleClick] won't be invoked
 * @param onClickLabel semantic / accessibility label for the [onClick] action
 * @param role the type of user interface element. Accessibility services might use this
 * to describe the element or do customizations
 * @param lazilyCreateIndication if `true` (recommended for most cases) the [indicationNodeFactory]
 * will only be created when this clickable emits
 * an [androidx.compose.foundation.interaction.Interaction]. If [interactionSource] is `null`, or
 * you are only reading from the [interactionSource] and never emitting an interaction, you should
 * typically provide true. If you are emitting an interaction, or you need the indication to be
 * eagerly created, provide false.
 *
 * Note: This API is experimental and is awaiting a rework. combinedClickable handles touch based
 * input quite well but provides subpar functionality for other input types.
 */
@ExperimentalFoundationApi
fun CombinedClickableNode(
    onClick: () -> Unit,
    onLongClickLabel: String?,
    onLongClick: (() -> Unit)?,
    onDoubleClick: (() -> Unit)?,
    interactionSource: MutableInteractionSource?,
    indicationNodeFactory: IndicationNodeFactory?,
    enabled: Boolean,
    onClickLabel: String?,
    role: Role?,
    lazilyCreateIndication: Boolean
): CombinedClickableNode = CombinedClickableNodeImpl(
    onClick,
    onLongClickLabel,
    onLongClick,
    onDoubleClick,
    interactionSource,
    indicationNodeFactory,
    enabled,
    onClickLabel,
    role,
    lazilyCreateIndication
)

/**
 * Public interface for the internal node used inside [combinedClickable], to allow for custom
 * modifier nodes to delegate to it.
 *
 * Note: This API is experimental and is temporarily being exposed to enable performance analysis,
 * you should use [combinedClickable] instead for the majority of use cases.
 */
@ExperimentalFoundationApi
sealed interface CombinedClickableNode : PointerInputModifierNode {
    /**
     * Updates this node with new values, and resets any invalidated state accordingly.
     *
     * @param onClick will be called when user clicks on the element
     * @param onLongClickLabel semantic / accessibility label for the [onLongClick] action
     * @param onLongClick will be called when user long presses on the element
     * @param onDoubleClick will be called when user double clicks on the element
     * @param interactionSource [MutableInteractionSource] that will be used to emit
     * [PressInteraction.Press] when this clickable is pressed. Only the initial (first) press will
     * be recorded and emitted with [MutableInteractionSource]. If `null`, and there is an
     * [indicationNodeFactory] provided, an internal [MutableInteractionSource] will be created
     * when required. See [lazilyCreateIndication] for more information.
     * @param indicationNodeFactory the [IndicationNodeFactory] used to optionally render
     * [Indication] inside this node, instead of using a separate [Modifier.indication]. This should
     * be preferred for performance reasons over using [Modifier.indication] separately.
     * @param enabled Controls the enabled state. When false, [onClick], [onLongClick] or
     * [onDoubleClick] won't be invoked
     * @param onClickLabel semantic / accessibility label for the [onClick] action
     * @param role the type of user interface element. Accessibility services might use this
     * to describe the element or do customizations
     * @param lazilyCreateIndication if `true` (recommended for most cases) the
     * [indicationNodeFactory] will only be created when this clickable emits an
     * [androidx.compose.foundation.interaction.Interaction]. If [interactionSource] is `null`,
     * or you are only reading from the [interactionSource] and never emitting an interaction,
     * you should typically provide true. If you are emitting an interaction, or you need the
     * indication to be eagerly created, provide false.
     */
    fun update(
        onClick: () -> Unit,
        onLongClickLabel: String?,
        onLongClick: (() -> Unit)?,
        onDoubleClick: (() -> Unit)?,
        interactionSource: MutableInteractionSource?,
        indicationNodeFactory: IndicationNodeFactory?,
        enabled: Boolean,
        onClickLabel: String?,
        role: Role?,
        lazilyCreateIndication: Boolean
    )
}

@OptIn(ExperimentalFoundationApi::class)
private class CombinedClickableNodeImpl(
    onClick: () -> Unit,
    onLongClickLabel: String?,
    private var onLongClick: (() -> Unit)?,
    onDoubleClick: (() -> Unit)?,
    interactionSource: MutableInteractionSource?,
    indicationNodeFactory: IndicationNodeFactory?,
    enabled: Boolean,
    onClickLabel: String?,
    role: Role?,
    lazilyCreateIndication: Boolean
) : CombinedClickableNode,
    AbstractClickableNode(
        interactionSource,
        indicationNodeFactory,
        enabled,
        onClickLabel,
        role,
        lazilyCreateIndication,
        onClick
    ) {
    override val clickableSemanticsNode = delegate(
        ClickableSemanticsNode(
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick
        )
    )

    override val clickablePointerInputNode = delegate(
        CombinedClickablePointerInputNode(
            enabled = enabled,
            interactionSourceProvider = interactionSourceProvider,
            onClick = onClick,
            interactionData = interactionData,
            onLongClick,
            onDoubleClick
        )
    )

    override fun update(
        onClick: () -> Unit,
        onLongClickLabel: String?,
        onLongClick: (() -> Unit)?,
        onDoubleClick: (() -> Unit)?,
        interactionSource: MutableInteractionSource?,
        indicationNodeFactory: IndicationNodeFactory?,
        enabled: Boolean,
        onClickLabel: String?,
        role: Role?,
        lazilyCreateIndication: Boolean
    ) {
        // If we have gone from no long click to having a long click or vice versa,
        // cancel any existing press interactions.
        if ((this.onLongClick == null) != (onLongClick == null)) {
            disposeInteractionSource()
        }
        this.onLongClick = onLongClick
        updateCommon(
            interactionSource,
            indicationNodeFactory,
            enabled,
            onClickLabel,
            role,
            lazilyCreateIndication,
            onClick
        )
        clickableSemanticsNode.update(
            enabled = enabled,
            role = role,
            onClickLabel = onClickLabel,
            onClick = onClick,
            onLongClickLabel = onLongClickLabel,
            onLongClick = onLongClick
        )
        clickablePointerInputNode.update(
            enabled = enabled,
            onClick = onClick,
            onLongClick = onLongClick,
            onDoubleClick = onDoubleClick
        )
    }
}

private sealed class AbstractClickableNode(
    private var interactionSource: MutableInteractionSource?,
    private var indicationNodeFactory: IndicationNodeFactory?,
    private var enabled: Boolean,
    private var onClickLabel: String?,
    private var role: Role?,
    private var lazilyCreateIndication: Boolean,
    private var onClick: () -> Unit
) : DelegatingNode(), PointerInputModifierNode, KeyInputModifierNode, FocusEventModifierNode {
    abstract val clickablePointerInputNode: AbstractClickablePointerInputNode
    abstract val clickableSemanticsNode: ClickableSemanticsNode
    private val hoverableNode: HoverableNode = HoverableNode(interactionSource)
    private val focusableInNonTouchMode: FocusableInNonTouchMode = FocusableInNonTouchMode()
    private val focusableNode: FocusableNode = FocusableNode(interactionSource)

    private var indicationNode: DelegatableNode? = null
    // Track separately from interactionSource, as we will create our own internal
    // InteractionSource if needed
    private var userProvidedInteractionSource: MutableInteractionSource? = interactionSource

    protected val interactionSourceProvider = { interactionSource }

    class InteractionData {
        val currentKeyPressInteractions = mutableMapOf<Key, PressInteraction.Press>()
        var pressInteraction: PressInteraction.Press? = null
        var centreOffset: Offset = Offset.Zero
    }

    protected val interactionData = InteractionData()

    protected fun updateCommon(
        interactionSource: MutableInteractionSource?,
        indicationNodeFactory: IndicationNodeFactory?,
        enabled: Boolean,
        onClickLabel: String?,
        role: Role?,
        lazilyCreateIndication: Boolean,
        onClick: () -> Unit
    ) {
        var isIndicationNodeDirty = false
        // Compare against userProvidedInteractionSource, as we will create a new InteractionSource
        // lazily if the userProvidedInteractionSource is null, and assign it to interactionSource
        if (userProvidedInteractionSource != interactionSource) {
            disposeInteractionSource()
            userProvidedInteractionSource = interactionSource
            this.interactionSource = interactionSource
            isIndicationNodeDirty = true
        }
        if (this.indicationNodeFactory != indicationNodeFactory) {
            this.indicationNodeFactory = indicationNodeFactory
            isIndicationNodeDirty = true
        }
        if (this.enabled != enabled) {
            if (enabled) {
                delegate(hoverableNode)
                delegate(focusableInNonTouchMode)
                delegate(focusableNode)
            } else {
                // TODO: Should we remove indicationNode? Previously we always emitted indication
                undelegate(hoverableNode)
                undelegate(focusableInNonTouchMode)
                undelegate(focusableNode)
                disposeInteractionSource()
            }
            this.enabled = enabled
        }
        this.onClickLabel = onClickLabel
        this.role = role
        this.onClick = onClick
        if (this.lazilyCreateIndication != lazilyCreateIndication) {
            this.lazilyCreateIndication = lazilyCreateIndication
            // If we are no longer lazily creating the node, and we haven't created the node yet,
            // create it
            if (!lazilyCreateIndication && indicationNode == null) isIndicationNodeDirty = true
        }
        // Create / recreate indication node
        if (isIndicationNodeDirty) {
            // If we already created a node lazily, or we are not lazily creating the node, create
            if (indicationNode != null || !lazilyCreateIndication) {
                indicationNode?.let { undelegate(it) }
                indicationNode = null
                initializeIndicationAndInteractionSourceIfNeeded()
            }
        }
        hoverableNode.updateInteractionSource(interactionSource)
        focusableNode.update(interactionSource)
    }

    override fun onAttach() {
        if (!lazilyCreateIndication) {
            initializeIndicationAndInteractionSourceIfNeeded()
        }
        if (enabled) {
            delegate(hoverableNode)
            delegate(focusableInNonTouchMode)
            delegate(focusableNode)
        }
    }

    override fun onDetach() {
        disposeInteractionSource()
    }

    protected fun disposeInteractionSource() {
        interactionSource?.let { interactionSource ->
            interactionData.pressInteraction?.let { oldValue ->
                val interaction = PressInteraction.Cancel(oldValue)
                interactionSource.tryEmit(interaction)
            }
            interactionData.currentKeyPressInteractions.values.forEach {
                interactionSource.tryEmit(PressInteraction.Cancel(it))
            }
        }
        interactionData.pressInteraction = null
        interactionData.currentKeyPressInteractions.clear()
    }

    private fun initializeIndicationAndInteractionSourceIfNeeded() {
        // We have already created the node, no need to do any work
        if (indicationNode != null) return
        indicationNodeFactory?.let { indicationNodeFactory ->
            if (interactionSource == null) {
                interactionSource = MutableInteractionSource()
            }
            hoverableNode.updateInteractionSource(interactionSource)
            focusableNode.update(interactionSource)
            val node = indicationNodeFactory.create(interactionSource!!)
            delegate(node)
            indicationNode = node
        }
    }

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        initializeIndicationAndInteractionSourceIfNeeded()
        if (hoverableNode.isAttached) {
            hoverableNode.onPointerEvent(pointerEvent, pass, bounds)
        }
        clickablePointerInputNode.onPointerEvent(pointerEvent, pass, bounds)
    }

    override fun onCancelPointerInput() {
        if (hoverableNode.isAttached) {
            hoverableNode.onCancelPointerInput()
        }
        clickablePointerInputNode.onCancelPointerInput()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        // Key events usually require focus, but if a focused child does not handle the KeyEvent,
        // the event can bubble up without this clickable ever being focused, and hence without
        // this being initialized through the focus path
        initializeIndicationAndInteractionSourceIfNeeded()
        return when {
            enabled && event.isPress -> {
                // If the key already exists in the map, keyEvent is a repeat event.
                // We ignore it as we only want to emit an interaction for the initial key press.
                if (!interactionData.currentKeyPressInteractions.containsKey(event.key)) {
                    val press = PressInteraction.Press(interactionData.centreOffset)
                    interactionData.currentKeyPressInteractions[event.key] = press
                    // Even if the interactionSource is null, we still want to intercept the presses
                    // so we always track them above, and return true
                    if (interactionSource != null) {
                        coroutineScope.launch { interactionSource?.emit(press) }
                    }
                    true
                } else {
                    false
                }
            }
            enabled && event.isClick -> {
                interactionData.currentKeyPressInteractions.remove(event.key)?.let {
                    if (interactionSource != null) {
                        coroutineScope.launch {
                            interactionSource?.emit(PressInteraction.Release(it))
                        }
                    }
                }
                onClick()
                true
            }
            else -> false
        }
    }

    override fun onPreKeyEvent(event: KeyEvent) = false

    override fun onFocusEvent(focusState: FocusState) {
        if (focusState.isFocused) {
            initializeIndicationAndInteractionSourceIfNeeded()
        }
        focusableNode.onFocusEvent(focusState)
    }
}

private class ClickableSemanticsElement(
    private val enabled: Boolean,
    private val role: Role?,
    private val onLongClickLabel: String?,
    private val onLongClick: (() -> Unit)?,
    private val onClickLabel: String?,
    private val onClick: () -> Unit
) : ModifierNodeElement<ClickableSemanticsNode>() {
    override fun create() = ClickableSemanticsNode(
        enabled = enabled,
        role = role,
        onLongClickLabel = onLongClickLabel,
        onLongClick = onLongClick,
        onClickLabel = onClickLabel,
        onClick = onClick
    )

    override fun update(node: ClickableSemanticsNode) {
        node.update(enabled, onClickLabel, role, onClick, onLongClickLabel, onLongClick)
    }

    override fun InspectorInfo.inspectableProperties() = Unit

    override fun hashCode(): Int {
        var result = enabled.hashCode()
        result = 31 * result + role.hashCode()
        result = 31 * result + onLongClickLabel.hashCode()
        result = 31 * result + onLongClick.hashCode()
        result = 31 * result + onClickLabel.hashCode()
        result = 31 * result + onClick.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ClickableSemanticsElement) return false

        if (enabled != other.enabled) return false
        if (role != other.role) return false
        if (onLongClickLabel != other.onLongClickLabel) return false
        if (onLongClick != other.onLongClick) return false
        if (onClickLabel != other.onClickLabel) return false
        if (onClick != other.onClick) return false

        return true
    }
}

private class ClickableSemanticsNode(
    private var enabled: Boolean,
    private var onClickLabel: String?,
    private var role: Role?,
    private var onClick: () -> Unit,
    private var onLongClickLabel: String?,
    private var onLongClick: (() -> Unit)?,
) : SemanticsModifierNode, Modifier.Node() {
    fun update(
        enabled: Boolean,
        onClickLabel: String?,
        role: Role?,
        onClick: () -> Unit,
        onLongClickLabel: String?,
        onLongClick: (() -> Unit)?,
    ) {
        this.enabled = enabled
        this.onClickLabel = onClickLabel
        this.role = role
        this.onClick = onClick
        this.onLongClickLabel = onLongClickLabel
        this.onLongClick = onLongClick
    }

    override val shouldMergeDescendantSemantics: Boolean
        get() = true
    override fun SemanticsPropertyReceiver.applySemantics() {
        if (this@ClickableSemanticsNode.role != null) {
            role = this@ClickableSemanticsNode.role!!
        }
        onClick(
            action = { onClick(); true },
            label = onClickLabel
        )
        if (onLongClick != null) {
            onLongClick(
                action = { onLongClick?.invoke(); true },
                label = onLongClickLabel
            )
        }
        if (!enabled) {
            disabled()
        }
    }
}

private sealed class AbstractClickablePointerInputNode(
    protected var enabled: Boolean,
    private val interactionSourceProvider: () -> MutableInteractionSource?,
    protected var onClick: () -> Unit,
    protected val interactionData: AbstractClickableNode.InteractionData
) : DelegatingNode(), ModifierLocalModifierNode, CompositionLocalConsumerModifierNode,
    PointerInputModifierNode {

    private val delayPressInteraction = {
        ModifierLocalScrollableContainer.current || isComposeRootInScrollableContainer()
    }

    private val pointerInputNode = delegate(SuspendingPointerInputModifierNode { pointerInput() })

    protected abstract suspend fun PointerInputScope.pointerInput()

    override fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    ) {
        pointerInputNode.onPointerEvent(pointerEvent, pass, bounds)
    }

    override fun onCancelPointerInput() {
        pointerInputNode.onCancelPointerInput()
    }

    protected suspend fun PressGestureScope.handlePressInteraction(offset: Offset) {
        interactionSourceProvider()?.let { interactionSource ->
            handlePressInteraction(
                offset,
                interactionSource,
                interactionData,
                delayPressInteraction
            )
        }
    }

    protected fun resetPointerInputHandler() = pointerInputNode.resetPointerInputHandler()
}

private class ClickablePointerInputNode(
    enabled: Boolean,
    interactionSourceProvider: () -> MutableInteractionSource?,
    onClick: () -> Unit,
    interactionData: AbstractClickableNode.InteractionData
) : AbstractClickablePointerInputNode(
    enabled,
    interactionSourceProvider,
    onClick,
    interactionData
) {
    override suspend fun PointerInputScope.pointerInput() {
        interactionData.centreOffset = size.center.toOffset()
        detectTapAndPress(
            onPress = { offset ->
                if (enabled) {
                    handlePressInteraction(offset)
                }
            },
            onTap = { if (enabled) onClick() }
        )
    }

    fun update(
        enabled: Boolean,
        onClick: () -> Unit,
    ) {
        // These are captured inside callbacks, not as an input to detectTapGestures,
        // so no need need to reset pointer input handling
        this.enabled = enabled
        this.onClick = onClick
    }
}

private class CombinedClickablePointerInputNode(
    enabled: Boolean,
    interactionSourceProvider: () -> MutableInteractionSource?,
    onClick: () -> Unit,
    interactionData: AbstractClickableNode.InteractionData,
    private var onLongClick: (() -> Unit)?,
    private var onDoubleClick: (() -> Unit)?
) : AbstractClickablePointerInputNode(
    enabled,
    interactionSourceProvider,
    onClick,
    interactionData
) {
    override suspend fun PointerInputScope.pointerInput() {
        interactionData.centreOffset = size.center.toOffset()
        detectTapGestures(
            onDoubleTap = if (enabled && onDoubleClick != null) {
                { onDoubleClick?.invoke() }
            } else null,
            onLongPress = if (enabled && onLongClick != null) {
                { onLongClick?.invoke() }
            } else null,
            onPress = { offset ->
                if (enabled) {
                    handlePressInteraction(offset)
                }
            },
            onTap = { if (enabled) onClick() }
        )
    }

    fun update(
        enabled: Boolean,
        onClick: () -> Unit,
        onLongClick: (() -> Unit)?,
        onDoubleClick: (() -> Unit)?
    ) {
        // This is captured inside a callback, not as an input to detectTapGestures,
        // so no need need to reset pointer input handling
        this.onClick = onClick

        var changed = false

        // This is captured as a parameter to detectTapGestures, so we need to restart detecting
        // gestures if it changes.
        if (this.enabled != enabled) {
            this.enabled = enabled
            changed = true
        }

        // We capture these inside the callback, so if the lambda changes value we don't want to
        // reset input handling - only reset if they go from not-defined to defined, and vice-versa,
        // as that is what is captured in the parameter to detectTapGestures.
        if ((this.onLongClick == null) != (onLongClick == null)) {
            changed = true
        }
        this.onLongClick = onLongClick
        if ((this.onDoubleClick == null) != (onDoubleClick == null)) {
            changed = true
        }
        this.onDoubleClick = onDoubleClick
        if (changed) resetPointerInputHandler()
    }
}

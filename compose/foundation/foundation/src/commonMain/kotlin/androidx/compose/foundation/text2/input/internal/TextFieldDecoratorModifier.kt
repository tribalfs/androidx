/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.foundation.text2.input.internal

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text2.BasicTextField2
import androidx.compose.foundation.text2.input.TextEditFilter
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.deselect
import androidx.compose.foundation.text2.selection.TextFieldSelectionState
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusEventModifierNode
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequesterModifierNode
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.requestFocus
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyInputModifierNode
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.SuspendingPointerInputModifierNode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.PointerInputModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateSemantics
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.PlatformTextInputModifierNode
import androidx.compose.ui.platform.PlatformTextInputSession
import androidx.compose.ui.platform.SoftwareKeyboardController
import androidx.compose.ui.platform.textInputSession
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.copyText
import androidx.compose.ui.semantics.cutText
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.editableText
import androidx.compose.ui.semantics.getTextLayoutResult
import androidx.compose.ui.semantics.insertTextAtCursor
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onImeAction
import androidx.compose.ui.semantics.pasteText
import androidx.compose.ui.semantics.setSelection
import androidx.compose.ui.semantics.setText
import androidx.compose.ui.semantics.textSelectionRange
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Modifier element for most of the functionality of [BasicTextField2] that is attached to the
 * decoration box. This is only half the actual modifiers for the field, the other half are only
 * attached to the internal text field.
 *
 * This modifier handles input events (both key and pointer), semantics, and focus.
 */
@OptIn(ExperimentalFoundationApi::class)
internal data class TextFieldDecoratorModifier(
    private val textFieldState: TextFieldState,
    private val textLayoutState: TextLayoutState,
    private val textFieldSelectionState: TextFieldSelectionState,
    private val filter: TextEditFilter?,
    private val enabled: Boolean,
    private val readOnly: Boolean,
    private val keyboardOptions: KeyboardOptions,
    private val keyboardActions: KeyboardActions,
    private val singleLine: Boolean,
) : ModifierNodeElement<TextFieldDecoratorModifierNode>() {
    override fun create(): TextFieldDecoratorModifierNode = TextFieldDecoratorModifierNode(
        textFieldState = textFieldState,
        textLayoutState = textLayoutState,
        textFieldSelectionState = textFieldSelectionState,
        filter = filter,
        enabled = enabled,
        readOnly = readOnly,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        singleLine = singleLine,
    )

    override fun update(node: TextFieldDecoratorModifierNode) {
        node.updateNode(
            textFieldState = textFieldState,
            textLayoutState = textLayoutState,
            textFieldSelectionState = textFieldSelectionState,
            filter = filter,
            enabled = enabled,
            readOnly = readOnly,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = singleLine,
        )
    }

    override fun InspectorInfo.inspectableProperties() {
        // Show nothing in the inspector.
    }
}

/** Modifier node for [TextFieldDecoratorModifier]. */
@OptIn(ExperimentalFoundationApi::class)
internal class TextFieldDecoratorModifierNode(
    var textFieldState: TextFieldState,
    var textLayoutState: TextLayoutState,
    var textFieldSelectionState: TextFieldSelectionState,
    var filter: TextEditFilter?,
    var enabled: Boolean,
    var readOnly: Boolean,
    keyboardOptions: KeyboardOptions,
    var keyboardActions: KeyboardActions,
    var singleLine: Boolean,
) : DelegatingNode(),
    PlatformTextInputModifierNode,
    SemanticsModifierNode,
    FocusRequesterModifierNode,
    FocusEventModifierNode,
    GlobalPositionAwareModifierNode,
    PointerInputModifierNode,
    KeyInputModifierNode,
    CompositionLocalConsumerModifierNode {

    private val pointerInputNode = delegate(SuspendingPointerInputModifierNode {
        with(textFieldSelectionState) {
            textFieldGestures(
                requestFocus = {
                    if (!isFocused) requestFocus()
                },
                showKeyboard = {
                    requireKeyboardController().show()
                }
            )
        }
    })

    var keyboardOptions: KeyboardOptions = keyboardOptions.withDefaultsFrom(filter?.keyboardOptions)
        private set

    private var isFocused: Boolean = false

    /**
     * Manages key events. These events often are sourced by a hardware keyboard but it's also
     * possible that IME or some other platform system simulates a KeyEvent.
     */
    private val textFieldKeyEventHandler = TextFieldKeyEventHandler().also {
        it.setFilter(filter)
    }

    private val keyboardActionScope = object : KeyboardActionScope {
        private val focusManager: FocusManager
            get() = currentValueOf(LocalFocusManager)

        override fun defaultKeyboardAction(imeAction: ImeAction) {
            when (imeAction) {
                ImeAction.Next -> {
                    focusManager.moveFocus(FocusDirection.Next)
                }
                ImeAction.Previous -> {
                    focusManager.moveFocus(FocusDirection.Previous)
                }
                ImeAction.Done -> {
                    requireKeyboardController().hide()
                }
                ImeAction.Go, ImeAction.Search, ImeAction.Send,
                ImeAction.Default, ImeAction.None -> Unit
            }
        }
    }

    private val onImeActionPerformed: (ImeAction) -> Unit = { imeAction ->
        val keyboardAction = when (imeAction) {
            ImeAction.Done -> keyboardActions.onDone
            ImeAction.Go -> keyboardActions.onGo
            ImeAction.Next -> keyboardActions.onNext
            ImeAction.Previous -> keyboardActions.onPrevious
            ImeAction.Search -> keyboardActions.onSearch
            ImeAction.Send -> keyboardActions.onSend
            ImeAction.Default, ImeAction.None -> null
            else -> error("invalid ImeAction")
        }
        keyboardAction?.invoke(keyboardActionScope)
            ?: keyboardActionScope.defaultKeyboardAction(imeAction)
    }

    /**
     * A coroutine job that observes text and layout changes in selection state to react to those
     * changes.
     */
    private var inputSessionJob: Job? = null

    /**
     * Updates all the related properties and invalidates internal state based on the changes.
     */
    fun updateNode(
        textFieldState: TextFieldState,
        textLayoutState: TextLayoutState,
        textFieldSelectionState: TextFieldSelectionState,
        filter: TextEditFilter?,
        enabled: Boolean,
        readOnly: Boolean,
        keyboardOptions: KeyboardOptions,
        keyboardActions: KeyboardActions,
        singleLine: Boolean,
    ) {
        // Find the diff: current previous and new values before updating current.
        val previousWriteable = this.enabled && !this.readOnly
        val writeable = enabled && !readOnly

        val previousEnabled = this.enabled
        val previousTextFieldState = this.textFieldState
        val previousKeyboardOptions = this.keyboardOptions
        val previousTextFieldSelectionState = this.textFieldSelectionState
        val previousFilter = this.filter

        // Apply the diff.
        this.textFieldState = textFieldState
        this.textLayoutState = textLayoutState
        this.textFieldSelectionState = textFieldSelectionState
        this.filter = filter
        this.enabled = enabled
        this.readOnly = readOnly
        this.keyboardOptions = keyboardOptions.withDefaultsFrom(filter?.keyboardOptions)
        this.keyboardActions = keyboardActions
        this.singleLine = singleLine

        // React to diff.
        // Something about the session changed, restart the session.
        if (writeable != previousWriteable ||
            textFieldState != previousTextFieldState ||
            keyboardOptions != previousKeyboardOptions ||
            filter != previousFilter
        ) {
            if (writeable && isFocused) {
                // The old session will be implicitly disposed.
                startInputSession()
            } else if (!writeable) {
                // We were made read-only or disabled, hide the keyboard.
                disposeInputSession()
            }
        }

        if (previousEnabled != enabled) {
            invalidateSemantics()
        }

        textFieldKeyEventHandler.setFilter(filter)

        if (textFieldSelectionState != previousTextFieldSelectionState) {
            pointerInputNode.resetPointerInputHandler()
        }
    }

    override val shouldMergeDescendantSemantics: Boolean
        get() = true

    // This function is called inside a snapshot observer.
    override fun SemanticsPropertyReceiver.applySemantics() {
        val text = textFieldState.text
        val selection = text.selectionInChars

        getTextLayoutResult {
            textLayoutState.layoutResult?.let { result -> it.add(result) } ?: false
        }
        editableText = AnnotatedString(text.toString())
        textSelectionRange = selection
        if (!enabled) disabled()

        setText { newText ->
            if (readOnly || !enabled) return@setText false

            textFieldState.editProcessor.update(
                listOf(
                    DeleteAllCommand,
                    CommitTextCommand(newText, 1)
                ),
                filter
            )
            true
        }
        setSelection { start, end, _ ->
            // BasicTextField2 doesn't have VisualTransformation for the time being and
            // probably won't have something that uses offsetMapping design. We can safely
            // skip relativeToOriginalText flag. Assume it's always true.

            if (!enabled) {
                false
            } else if (start == selection.start && end == selection.end) {
                false
            } else if (start.coerceAtMost(end) >= 0 &&
                start.coerceAtLeast(end) <= text.length
            ) {
                textFieldState.editProcessor.update(
                    listOf(SetSelectionCommand(start, end)),
                    filter
                )
                true
            } else {
                false
            }
        }
        insertTextAtCursor { newText ->
            if (readOnly || !enabled) return@insertTextAtCursor false

            textFieldState.editProcessor.update(
                listOf(
                    // Finish composing text first because when the field is focused the IME
                    // might set composition.
                    FinishComposingTextCommand,
                    CommitTextCommand(newText, 1)
                ),
                filter
            )
            true
        }
        onImeAction(keyboardOptions.imeAction) {
            onImeActionPerformed(keyboardOptions.imeAction)
            true
        }
        onClick {
            // according to the documentation, we still need to provide proper semantics actions
            // even if the state is 'disabled'
            if (!isFocused) {
                requestFocus()
            } else if (!readOnly) {
                requireKeyboardController().show()
            }
            true
        }
        if (!selection.collapsed) {
            copyText {
                textFieldSelectionState.copy()
                true
            }
            if (enabled && !readOnly) {
                cutText {
                    textFieldSelectionState.cut()
                    true
                }
            }
        }
        if (enabled && !readOnly) {
            pasteText {
                textFieldSelectionState.paste()
                true
            }
        }
    }

    override fun onFocusEvent(focusState: FocusState) {
        if (isFocused == focusState.isFocused) {
            return
        }
        isFocused = focusState.isFocused
        textFieldSelectionState.isFocused = focusState.isFocused

        if (focusState.isFocused) {
            // Deselect when losing focus even if readonly.
            if (enabled && !readOnly) {
                startInputSession()
                // TODO(halilibo): bringIntoView
            }
        } else {
            disposeInputSession()
            textFieldState.deselect()
        }
    }

    override fun onDetach() {
        disposeInputSession()
    }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        textLayoutState.decorationBoxCoordinates = coordinates
    }

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

    override fun onPreKeyEvent(event: KeyEvent): Boolean {
        // TextField does not handle pre key events.
        return false
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        return textFieldKeyEventHandler.onKeyEvent(
            event = event,
            state = textFieldState,
            textLayoutState = textLayoutState,
            editable = enabled && !readOnly,
            singleLine = singleLine,
            onSubmit = { onImeActionPerformed(keyboardOptions.imeAction) }
        )
    }

    private fun startInputSession() {
        inputSessionJob = coroutineScope.launch {
            // This will automatically cancel the previous session, if any, so we don't need to
            // cancel the inputSessionJob ourselves.
            textInputSession {
                // Re-start observing changes in case our TextFieldState instance changed.
                launch(start = CoroutineStart.UNDISPATCHED) {
                    textFieldSelectionState.observeChanges()
                }

                platformSpecificTextInputSession(
                    textFieldState,
                    keyboardOptions.toImeOptions(singleLine),
                    filter = filter,
                    onImeAction = onImeActionPerformed
                )
            }
        }
    }

    private fun disposeInputSession() {
        inputSessionJob?.cancel()
        inputSessionJob = null
    }

    private fun requireKeyboardController(): SoftwareKeyboardController =
        currentValueOf(LocalSoftwareKeyboardController)
            ?: error("No software keyboard controller")
}

/**
 * Runs platform-specific text input logic.
 */
@OptIn(ExperimentalFoundationApi::class)
internal expect suspend fun PlatformTextInputSession.platformSpecificTextInputSession(
    state: TextFieldState,
    imeOptions: ImeOptions,
    filter: TextEditFilter?,
    onImeAction: ((ImeAction) -> Unit)?
): Nothing

/**
 * Returns a [KeyboardOptions] that is merged with [defaults], with this object's values taking
 * precedence.
 */
// TODO KeyboardOptions can't actually be merged correctly in all cases, because its properties
//  don't all have proper "unspecified" values. I think we can fix that in a backwards-compatible
//  way, but it will require adding new API outside of the text2 package so we should hold off on
//  making them until after the study.
internal fun KeyboardOptions.withDefaultsFrom(defaults: KeyboardOptions?): KeyboardOptions {
    if (defaults == null) return this
    return KeyboardOptions(
        capitalization = if (this.capitalization != KeyboardCapitalization.None) {
            this.capitalization
        } else {
            defaults.capitalization
        },
        autoCorrect = this.autoCorrect && defaults.autoCorrect,
        keyboardType = if (this.keyboardType != KeyboardType.Text) {
            this.keyboardType
        } else {
            defaults.keyboardType
        },
        imeAction = if (this.imeAction != ImeAction.Default) {
            this.imeAction
        } else {
            defaults.imeAction
        }
    )
}

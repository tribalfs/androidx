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

package androidx.compose.foundation.text2

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableDefaults
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.CursorHandle
import androidx.compose.foundation.text.Handle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.heightInLines
import androidx.compose.foundation.text.isInTouchMode
import androidx.compose.foundation.text.selection.SelectionHandleInfo
import androidx.compose.foundation.text.selection.SelectionHandleInfoKey
import androidx.compose.foundation.text.textFieldMinSize
import androidx.compose.foundation.text2.input.CodepointTransformation
import androidx.compose.foundation.text2.input.TextEditFilter
import androidx.compose.foundation.text2.input.TextFieldLineLimits
import androidx.compose.foundation.text2.input.TextFieldLineLimits.MultiLine
import androidx.compose.foundation.text2.input.TextFieldLineLimits.SingleLine
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.internal.AndroidTextInputPlugin
import androidx.compose.foundation.text2.input.internal.TextFieldCoreModifier
import androidx.compose.foundation.text2.input.internal.TextFieldDecoratorModifier
import androidx.compose.foundation.text2.input.internal.TextFieldTextLayoutModifier
import androidx.compose.foundation.text2.input.internal.TextLayoutState
import androidx.compose.foundation.text2.selection.TextFieldSelectionState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalPlatformTextInputPluginRegistry
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Density

/**
 * BasicTextField2 is a new text input Composable under heavy development. Please refrain from
 * using it in production since it has a very unstable API and implementation for the time being.
 * Many core features like selection, cursor, gestures, etc. may fail or simply not exist.
 *
 * Basic text composable that provides an interactive box that accepts text input through software
 * or hardware keyboard.
 *
 * All the editing state of this composable is hoisted through [state]. Whenever the contents of
 * this composable change via user input or semantics, [TextFieldState.text] gets updated.
 * Similarly, all the programmatic updates made to [state] also reflect on this composable.
 *
 * @param state [TextFieldState] object that holds the internal editing state of [BasicTextField2].
 * @param modifier optional [Modifier] for this text field.
 * @param enabled controls the enabled state of the [BasicTextField2]. When `false`, the text
 * field will be neither editable nor focusable, the input of the text field will not be selectable.
 * @param readOnly controls the editable state of the [BasicTextField2]. When `true`, the text
 * field can not be modified, however, a user can focus it and copy text from it. Read-only text
 * fields are usually used to display pre-filled forms that user can not edit.
 * @param filter Optional [TextEditFilter] that will be used to filter changes to the
 * [TextFieldState] made by the user. The filter will be applied to changes made by hardware and
 * software keyboard events, pasting or dropping text, accessibility services, and tests. The filter
 * will _not_ be applied when changing the [state] programmatically, or when the filter is changed.
 * If the filter is changed on an existing text field, it will be applied to the next user edit.
 * the filter will not immediately affect the current [state].
 * @param textStyle Typographic and graphic style configuration for text content that's displayed
 * in the editor.
 * @param keyboardOptions Software keyboard options that contain configurations such as
 * [KeyboardType] and [ImeAction].
 * @param keyboardActions When the input service emits an IME action, the corresponding callback
 * is called. Note that this IME action may be different from what you specified in
 * [KeyboardOptions.imeAction].
 * @param lineLimits Whether the text field should be [SingleLine], scroll horizontally, and
 * ignore newlines; or [MultiLine] and grow and scroll vertically. If [SingleLine] is passed without
 * specifying the [codepointTransformation] parameter, a [CodepointTransformation] is automatically
 * applied. This transformation replaces any newline characters ('\n') within the text with regular
 * whitespace (' '), ensuring that the contents of the text field are presented in a single line.
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A
 * [TextLayoutResult] object contains paragraph information, size of the text, baselines and other
 * details. The callback can be used to add additional decoration or functionality to the text.
 * For example, to draw a cursor or selection around the text. [Density] scope is the one that was
 * used while creating the given text layout.
 * @param interactionSource the [MutableInteractionSource] representing the stream of [Interaction]s
 * for this TextField. You can create and pass in your own remembered [MutableInteractionSource]
 * if you want to observe [Interaction]s and customize the appearance / behavior of this TextField
 * for different [Interaction]s.
 * @param cursorBrush [Brush] to paint cursor with. If [SolidColor] with [Color.Unspecified]
 * provided, then no cursor will be drawn.
 * @param scrollState Scroll state that manages either horizontal or vertical scroll of TextField.
 * If [lineLimits] is [SingleLine], this text field is treated as single line with horizontal
 * scroll behavior. In other cases the text field becomes vertically scrollable.
 * @param codepointTransformation Visual transformation interface that provides a 1-to-1 mapping of
 * codepoints.
 * @param decorationBox Composable lambda that allows to add decorations around text field, such
 * as icon, placeholder, helper messages or similar, and automatically increase the hit target area
 * of the text field. To allow you to control the placement of the inner text field relative to your
 * decorations, the text field implementation will pass in a framework-controlled composable
 * parameter "innerTextField" to the decorationBox lambda you provide. You must call
 * innerTextField exactly once.
 */
@ExperimentalFoundationApi
@Composable
fun BasicTextField2(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    filter: TextEditFilter? = null,
    textStyle: TextStyle = TextStyle.Default,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    lineLimits: TextFieldLineLimits = TextFieldLineLimits.Default,
    onTextLayout: Density.(TextLayoutResult) -> Unit = {},
    interactionSource: MutableInteractionSource? = null,
    cursorBrush: Brush = SolidColor(Color.Black),
    scrollState: ScrollState = rememberScrollState(),
    codepointTransformation: CodepointTransformation? = null,
    decorationBox: @Composable (innerTextField: @Composable () -> Unit) -> Unit =
        @Composable { innerTextField -> innerTextField() }
) {
    // only read from local and create an adapter if this text field is enabled and editable
    val textInputAdapter = LocalPlatformTextInputPluginRegistry.takeIf { enabled && !readOnly }
        ?.current?.rememberAdapter(AndroidTextInputPlugin)

    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val singleLine = lineLimits == SingleLine
    // We're using this to communicate focus state to cursor for now.
    @Suppress("NAME_SHADOWING")
    val interactionSource = interactionSource ?: remember { MutableInteractionSource() }

    val orientation = if (singleLine) Orientation.Horizontal else Orientation.Vertical

    val textLayoutState = remember { TextLayoutState() }

    val textFieldSelectionState = remember(state, textLayoutState, density) {
        TextFieldSelectionState(state, textLayoutState, density)
    }
    textFieldSelectionState.hapticFeedBack = LocalHapticFeedback.current

    val decorationModifiers = modifier
        .then(
            // semantics + some focus + input session + touch to focus
            TextFieldDecoratorModifier(
                textFieldState = state,
                textLayoutState = textLayoutState,
                textFieldSelectionState = textFieldSelectionState,
                textInputAdapter = textInputAdapter,
                filter = filter,
                enabled = enabled,
                readOnly = readOnly,
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                singleLine = singleLine,
            )
        )
        .focusable(interactionSource = interactionSource, enabled = enabled)
        .scrollable(
            orientation = orientation,
            reverseDirection = ScrollableDefaults.reverseDirection(
                layoutDirection = layoutDirection,
                orientation = orientation,
                reverseScrolling = false
            ),
            state = scrollState,
            interactionSource = interactionSource,
            enabled = enabled && scrollState.maxValue > 0
        )

    val isFocused = interactionSource.collectIsFocusedAsState().value

    Box(decorationModifiers, propagateMinConstraints = true) {
        decorationBox(innerTextField = {
            val minLines: Int
            val maxLines: Int
            if (lineLimits is MultiLine) {
                minLines = lineLimits.minHeightInLines
                maxLines = lineLimits.maxHeightInLines
            } else {
                minLines = 1
                maxLines = 1
            }

            Box(
                propagateMinConstraints = true,
                modifier = Modifier
                    .heightInLines(
                        textStyle = textStyle,
                        minLines = minLines,
                        maxLines = maxLines
                    )
                    .textFieldMinSize(textStyle)
                    .clipToBounds()
                    .then(
                        TextFieldCoreModifier(
                            isFocused = isFocused,
                            textLayoutState = textLayoutState,
                            textFieldState = state,
                            textFieldSelectionState = textFieldSelectionState,
                            cursorBrush = cursorBrush,
                            writeable = enabled && !readOnly,
                            scrollState = scrollState,
                            orientation = orientation
                        )
                    )
            ) {
                Box(
                    modifier = TextFieldTextLayoutModifier(
                        textLayoutState = textLayoutState,
                        textFieldState = state,
                        codepointTransformation = codepointTransformation,
                        textStyle = textStyle,
                        singleLine = singleLine,
                        onTextLayout = onTextLayout
                    )
                )

                if (enabled && isFocused && !readOnly && textFieldSelectionState.isInTouchMode) {
                    TextFieldCursorHandle(
                        selectionState = textFieldSelectionState
                    )
                }
            }
        })
    }
}

@Composable
internal fun TextFieldCursorHandle(selectionState: TextFieldSelectionState) {
    if (selectionState.cursorHandleVisible) {
        CursorHandle(
            handlePosition = selectionState.cursorRect.bottomCenter,
            modifier = Modifier
                .semantics {
                    this[SelectionHandleInfoKey] = SelectionHandleInfo(
                        handle = Handle.Cursor,
                        position = selectionState.cursorRect.bottomCenter
                    )
                }
                .pointerInput(selectionState) {
                    with(selectionState) { detectCursorHandleDragGestures() }
                },
            content = null
        )
    }
}
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

package androidx.compose.foundation.text.input.internal

import android.graphics.Rect as AndroidRect
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import androidx.annotation.VisibleForTesting
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.EditCommand
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.ImeOptions
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.emoji2.text.EmojiCompat
import java.lang.ref.WeakReference
import kotlin.math.roundToInt
import kotlinx.coroutines.Job

private const val DEBUG_CLASS = "AndroidLegacyPlatformTextInputServiceAdapter"

/**
 * Configures how the [AndroidLegacyPlatformTextInputServiceAdapter] creates its
 * [InputMethodManager] for text input sessions, for tests.
 */
@VisibleForTesting
internal var inputMethodManagerFactory: (View) -> InputMethodManager = ::InputMethodManagerImpl

internal actual fun createLegacyPlatformTextInputServiceAdapter():
    LegacyPlatformTextInputServiceAdapter = AndroidLegacyPlatformTextInputServiceAdapter()

internal class AndroidLegacyPlatformTextInputServiceAdapter :
    LegacyPlatformTextInputServiceAdapter() {

    private var job: Job? = null
    private var currentRequest: LegacyTextInputMethodRequest? = null

    override fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        startInput {
            it.startInput(value, imeOptions, onEditCommand, onImeActionPerformed)
        }
    }

    override fun startInput() {
        if (DEBUG) {
            Log.d(TAG, "$DEBUG_CLASS.startInput")
        }
        startInput(initializeRequest = null)
    }

    private fun startInput(initializeRequest: ((LegacyTextInputMethodRequest) -> Unit)?) {
        if (DEBUG) {
            Log.d(TAG, "$DEBUG_CLASS.startInput")
        }

        val node = textInputModifierNode ?: return

        fun localToScreen(matrix: Matrix) {
            val coordinates = node.layoutCoordinates?.takeIf { it.isAttached } ?: return
            coordinates.transformToScreen(matrix)
        }

        // No need to cancel any previous job, the text input system ensures the previous session
        // will be cancelled.
        job = node.launchTextInputSession {
            val request = LegacyTextInputMethodRequest(
                view = view,
                localToScreen = ::localToScreen,
                inputMethodManager = inputMethodManagerFactory(view)
            )
            initializeRequest?.invoke(request)
            currentRequest = request
            try {
                startInputMethod(request)
            } finally {
                currentRequest = null
            }
        }
    }

    override fun stopInput() {
        if (DEBUG) {
            Log.d(TAG, "$DEBUG_CLASS.stopInput")
        }
        job?.cancel()
        job = null
    }

    override fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        currentRequest?.updateState(oldValue, newValue)
    }

    override fun notifyFocusedRect(rect: Rect) {
        currentRequest?.notifyFocusedRect(rect)
    }

    override fun updateTextLayoutResult(
        textFieldValue: TextFieldValue,
        offsetMapping: OffsetMapping,
        textLayoutResult: TextLayoutResult,
        textFieldToRootTransform: (Matrix) -> Unit,
        innerTextFieldBounds: Rect,
        decorationBoxBounds: Rect
    ) {
        currentRequest?.updateTextLayoutResult(
            textFieldValue,
            offsetMapping,
            textLayoutResult,
            textFieldToRootTransform,
            innerTextFieldBounds,
            decorationBoxBounds
        )
    }
}

/**
 * Provide Android specific input service with the Operating System.
 */
internal class LegacyTextInputMethodRequest(
    val view: View,
    localToScreen: (Matrix) -> Unit,
    private val inputMethodManager: InputMethodManager,
) : PlatformTextInputMethodRequest {

    /**
     * The following three observers are set when the editable composable has initiated the input
     * session
     */
    private var onEditCommand: (List<EditCommand>) -> Unit = {}
    private var onImeActionPerformed: (ImeAction) -> Unit = {}

    // Visible for testing
    var state = TextFieldValue(text = "", selection = TextRange.Zero)
        private set
    private var imeOptions = ImeOptions.Default

    /**
     * RecordingInputConnection has strong reference to the View through TextInputServiceAndroid and
     * event callback. The connection should be closed when IME has changed and removed from this
     * list in onConnectionClosed callback, but not clear it is guaranteed the close connection is
     * called any time. So, keep it in WeakReference just in case.
     */
    private var ics = mutableListOf<WeakReference<RecordingInputConnection>>()

    /** Used for sendKeyEvent delegation */
    private val baseInputConnection by lazy(LazyThreadSafetyMode.NONE) {
        BaseInputConnection(view, false)
    }

    // Visible for testing.
    internal var focusedRect: AndroidRect? = null

    private val cursorAnchorInfoController =
        CursorAnchorInfoController(localToScreen, inputMethodManager)

    init {
        if (DEBUG) {
            Log.d(TAG, "$DEBUG_CLASS.create")
        }
    }

    fun startInput(
        value: TextFieldValue,
        imeOptions: ImeOptions,
        onEditCommand: (List<EditCommand>) -> Unit,
        onImeActionPerformed: (ImeAction) -> Unit
    ) {
        state = value
        this.imeOptions = imeOptions
        this.onEditCommand = onEditCommand
        this.onImeActionPerformed = onImeActionPerformed
    }

    override fun createInputConnection(outAttributes: EditorInfo): RecordingInputConnection {
        outAttributes.update(state.text, state.selection, imeOptions)
        outAttributes.updateWithEmojiCompat()

        return RecordingInputConnection(
            initState = state,
            autoCorrect = imeOptions.autoCorrect,
            eventCallback = object : InputEventCallback2 {
                override fun onEditCommands(editCommands: List<EditCommand>) {
                    onEditCommand(editCommands)
                }

                override fun onImeAction(imeAction: ImeAction) {
                    onImeActionPerformed(imeAction)
                }

                override fun onKeyEvent(event: KeyEvent) {
                    baseInputConnection.sendKeyEvent(event)
                }

                override fun onRequestCursorAnchorInfo(
                    immediate: Boolean,
                    monitor: Boolean,
                    includeInsertionMarker: Boolean,
                    includeCharacterBounds: Boolean,
                    includeEditorBounds: Boolean,
                    includeLineBounds: Boolean
                ) {
                    cursorAnchorInfoController.requestUpdate(
                        immediate,
                        monitor,
                        includeInsertionMarker,
                        includeCharacterBounds,
                        includeEditorBounds,
                        includeLineBounds
                    )
                }

                override fun onConnectionClosed(inputConnection: RecordingInputConnection) {
                    for (i in 0 until ics.size) {
                        if (ics[i].get() == inputConnection) {
                            ics.removeAt(i)
                            return // No duplicated instances should be in the list.
                        }
                    }
                }
            }
        ).also {
            ics.add(WeakReference(it))
            if (DEBUG) {
                Log.d(TAG, "$DEBUG_CLASS.createInputConnection: $ics")
            }
        }
    }

    fun updateState(oldValue: TextFieldValue?, newValue: TextFieldValue) {
        if (DEBUG) {
            Log.d(TAG, "$DEBUG_CLASS.updateState called: $oldValue -> $newValue")
        }

        // If the selection has changed from the last time, we need to update selection even though
        // the oldValue in EditBuffer is already in sync with the newValue.
        // Same holds for composition b/207800945
        val needUpdateSelection = (this.state.selection != newValue.selection) ||
            this.state.composition != newValue.composition
        this.state = newValue
        // update the latest TextFieldValue in InputConnection
        for (i in 0 until ics.size) {
            ics[i].get()?.textFieldValue = newValue
        }
        cursorAnchorInfoController.invalidate()

        if (oldValue == newValue) {
            if (DEBUG) {
                Log.d(TAG, "$DEBUG_CLASS.updateState early return")
            }
            if (needUpdateSelection) {
                // updateSelection API requires -1 if there is no composition
                inputMethodManager.updateSelection(
                    selectionStart = newValue.selection.min,
                    selectionEnd = newValue.selection.max,
                    compositionStart = state.composition?.min ?: -1,
                    compositionEnd = state.composition?.max ?: -1
                )
            }
            return
        }

        val restartInput = oldValue?.let {
            it.text != newValue.text ||
                // when selection is the same but composition has changed, need to reset the input.
                (it.selection == newValue.selection && it.composition != newValue.composition)
        } ?: false

        if (DEBUG) {
            Log.d(TAG, "$DEBUG_CLASS.updateState: restart($restartInput), state: $state")
        }

        if (restartInput) {
            restartInputImmediately()
        } else {
            for (i in 0 until ics.size) {
                ics[i].get()?.updateInputState(this.state, inputMethodManager)
            }
        }
    }

    fun notifyFocusedRect(rect: Rect) {
        focusedRect = AndroidRect(
            rect.left.roundToInt(),
            rect.top.roundToInt(),
            rect.right.roundToInt(),
            rect.bottom.roundToInt()
        )

        // Requesting rectangle too early after obtaining focus may bring view into wrong place
        // probably due to transient IME inset change. We don't know the correct timing of calling
        // requestRectangleOnScreen API, so try to call this API only after the IME is ready to
        // use, i.e. InputConnection has created.
        // Even if we miss all the timing of requesting rectangle during initial text field focus,
        // focused rectangle will be requested when software keyboard has shown.
        if (ics.isEmpty()) {
            focusedRect?.let {
                // Notice that view.requestRectangleOnScreen may modify the input Rect, we have to
                // create another Rect and then pass it.
                view.requestRectangleOnScreen(AndroidRect(it))
            }
        }
    }

    fun updateTextLayoutResult(
        textFieldValue: TextFieldValue,
        offsetMapping: OffsetMapping,
        textLayoutResult: TextLayoutResult,
        textFieldToRootTransform: (Matrix) -> Unit,
        innerTextFieldBounds: Rect,
        decorationBoxBounds: Rect
    ) {
        cursorAnchorInfoController.updateTextLayoutResult(
            textFieldValue,
            offsetMapping,
            textLayoutResult,
            textFieldToRootTransform,
            innerTextFieldBounds,
            decorationBoxBounds
        )
    }

    /** Immediately restart the IME connection. */
    private fun restartInputImmediately() {
        if (DEBUG) Log.d(TAG, "$DEBUG_CLASS.restartInputImmediately")
        inputMethodManager.restartInput()
    }
}

/**
 * Call to update EditorInfo correctly when EmojiCompat is configured.
 */
private fun EditorInfo.updateWithEmojiCompat() {
    if (!EmojiCompat.isConfigured()) {
        return
    }

    EmojiCompat.get().updateEditorInfo(this)
}

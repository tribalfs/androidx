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

package androidx.ui.input

import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY
import java.util.Objects

/**
 * A base class of all EditOperations
 *
 * An EditOperation is a representation of platform IME API call. For example, in Android,
 * InputConnection#commitText API call is translated to CommitTextEditOp object.
 *
 * @hide
 */
@RestrictTo(LIBRARY)
interface EditOperation {

    /**
     * Processes editing buffer with this edit operation.
     */
    fun process(buffer: EditingBuffer)
}

/**
 * An edit operation represent commitText callback from InputMethod.
 *
 * @see https://developer.android.com/reference/android/view/inputmethod/InputConnection.html#commitText(java.lang.CharSequence,%20int)
 *
 * @hide
 */
@RestrictTo(LIBRARY)
data class CommitTextEditOp(
    /**
     * The text to commit. We ignore any styles in the original API.
     */
    val text: String,

    /**
     * The cursor position after inserted text.
     * See original commitText API docs for more details.
     */
    val newCursorPosition: Int
) : EditOperation {

    override fun process(buffer: EditingBuffer) {
        // API description says replace ongoing composition text if there. Then, if there is no
        // composition text, insert text into cursor position or replace selection.
        if (buffer.hasComposition()) {
            buffer.replace(buffer.compositionStart, buffer.compositionEnd, text)
        } else {
            // In this editing buffer, insert into cursor or replace selection are equivalent.
            buffer.replace(buffer.selectionStart, buffer.selectionEnd, text)
        }

        // After replace function is called, the editing buffer places the cursor at the end of the
        // modified range.
        val newCursor = buffer.cursor

        // See above API description for the meaning of newCursorPosition.
        val newCursorInBuffer = if (newCursorPosition > 0) {
            newCursor + newCursorPosition - 1
        } else {
            newCursor + newCursorPosition - text.length
        }

        buffer.cursor = newCursorInBuffer.coerceIn(0, buffer.length)
    }
}

/**
 * An edit operation represents setComposingRegion callback from InputMethod.
 *
 * @see https://developer.android.com/reference/android/view/inputmethod/InputConnection.html#setComposingRegion(int,%2520int)

 * @hide
 */
@RestrictTo(LIBRARY)
data class SetComposingRegionEditOp(
    /**
     * The inclusive start offset of the composing region.
     */
    val start: Int,

    /**
     * The exclusive end offset of the composing region
     */
    val end: Int
) : EditOperation {

    override fun process(buffer: EditingBuffer) {
        // The API description says, different from SetComposingText, SetComposingRegion must
        // preserve the ongoing composition text and set new composition.
        if (buffer.hasComposition()) {
            buffer.commitComposition()
        }

        // Sanitize the input: reverse if reversed, clamped into valid range, ignore empty range.
        val clampedStart = start.coerceIn(0, buffer.length)
        val clampedEnd = end.coerceIn(0, buffer.length)
        if (clampedStart == clampedEnd) {
            // do nothing. empty composition range is not allowed.
        } else if (clampedStart < clampedEnd) {
            buffer.setComposition(clampedStart, clampedEnd)
        } else {
            buffer.setComposition(clampedEnd, clampedStart)
        }
    }
}
/**
 * An edit operation represents setComposingText callback from InputMethod
 *
 * @see https://developer.android.com/reference/android/view/inputmethod/InputConnection.html#setComposingText(java.lang.CharSequence,%2520int)
 *
 * @hide
 */
@RestrictTo(LIBRARY)
data class SetComposingTextEditOp(
    /**
     * The composing text.
     */
    val text: String,
    /**
     * The cursor position after setting composing text.
     * See original setComposingText API docs for more details.
     */
    val newCursorPosition: Int
) : EditOperation {

    override fun process(buffer: EditingBuffer) {
        if (buffer.hasComposition()) {
            // API doc says, if there is ongoing composing text, replace it with new text.
            val compositionStart = buffer.compositionStart
            buffer.replace(buffer.compositionStart, buffer.compositionEnd, text)
            if (text.isNotEmpty()) {
                buffer.setComposition(compositionStart, compositionStart + text.length)
            }
        } else {
            // If there is no composing text, insert composing text into cursor position with
            // removing selected text if any.
            val selectionStart = buffer.selectionStart
            buffer.replace(buffer.selectionStart, buffer.selectionEnd, text)
            if (text.isNotEmpty()) {
                buffer.setComposition(selectionStart, selectionStart + text.length)
            }
        }

        // After replace function is called, the editing buffer places the cursor at the end of the
        // modified range.
        val newCursor = buffer.cursor

        // See above API description for the meaning of newCursorPosition.
        val newCursorInBuffer = if (newCursorPosition > 0) {
            newCursor + newCursorPosition - 1
        } else {
            newCursor + newCursorPosition - text.length
        }

        buffer.cursor = newCursorInBuffer.coerceIn(0, buffer.length)
    }
}
/**
 * An edit operation represents deleteSurroundingText callback from InputMethod
 *
 * @see https://developer.android.com/reference/android/view/inputmethod/InputConnection.html#deleteSurroundingText(int,%2520int)
 *
 * @hide
 */
@RestrictTo(LIBRARY)
data class DeleteSurroundingTextEditOp(
    /**
     * The number of characters in UTF-16 before the cursor to be deleted.
     */
    val beforeLength: Int,
    /**
     * The number of characters in UTF-16 after the cursor to be deleted.
     */
    val afterLength: Int
) : EditOperation {
    override fun process(buffer: EditingBuffer) {
        buffer.delete(
            buffer.selectionEnd,
            Math.min(buffer.selectionEnd + afterLength, buffer.length)
        )

        buffer.delete(
            Math.max(0, buffer.selectionStart - beforeLength),
            buffer.selectionStart
        )
    }
}
/**
 * An edit operation represents deleteSurroundingTextInCodePoitns callback from InputMethod
 *
 * @see https://developer.android.com/reference/android/view/inputmethod/InputConnection.html#deleteSurroundingTextInCodePoints(int,%2520int)
 *
 * @hide
 */
@RestrictTo(LIBRARY)
data class DeleteSurroundingTextInCodePointsEditOp(
    /**
     * The number of characters in Unicode code points before the cursor to be deleted.
     */
    val beforeLength: Int,
    /**
     * The number of characters in Unicode code points after the cursor to be deleted.
     */
    val afterLength: Int
) : EditOperation {
    override fun process(buffer: EditingBuffer) {
        TODO("Not implemented yet")
    }
}
/**
 * An edit operation represents setSelection callback from InputMethod
 *
 * @see https://developer.android.com/reference/android/view/inputmethod/InputConnection.html#setSelection(int,%2520int)
 *
 * @hide
 */
@RestrictTo(LIBRARY)
data class SetSelectionEditOp(
    /**
     * The inclusive start offset of the selection region.
     */
    val start: Int,
    /**
     * The exclusive end offset of the selection region.
     */
    val end: Int
) : EditOperation {

    override fun process(buffer: EditingBuffer) {
        // Sanitize the input: reverse if reversed, clamped into valid range.
        val clampedStart = start.coerceIn(0, buffer.length)
        val clampedEnd = end.coerceIn(0, buffer.length)
        if (clampedStart < clampedEnd) {
            buffer.setSelection(clampedStart, clampedEnd)
        } else {
            buffer.setSelection(clampedEnd, clampedStart)
        }
    }
}
/**
 * An edit operation represents finishComposingText callback from InputMEthod
 *
 * @see https://developer.android.com/reference/android/view/inputmethod/InputConnection.html#finishComposingText()
 *
 * @hide
 */
@RestrictTo(LIBRARY)
class FinishComposingTextEditOp : EditOperation {

    override fun process(buffer: EditingBuffer) {
        buffer.commitComposition()
    }

    // Class with empty arguments default ctor cannot be data class.
    // Treating all FinishComposingTextEditOp are equal object.
    override fun equals(other: Any?): Boolean = other is FinishComposingTextEditOp
    override fun hashCode(): Int = Objects.hashCode(this.javaClass)
}
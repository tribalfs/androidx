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
import androidx.compose.foundation.text2.input.TextEditFilter
import androidx.compose.foundation.text2.input.TextFieldCharSequence
import androidx.compose.ui.text.TextRange
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@OptIn(ExperimentalFoundationApi::class)
@RunWith(JUnit4::class)
class EditProcessorTest {

    @Test
    fun initializeValue() {
        val firstValue = TextFieldCharSequence("ABCDE", TextRange.Zero)
        val processor = EditProcessor(firstValue)

        assertThat(processor.value).isEqualTo(firstValue)
    }

    @Test
    fun apply_commitTextCommand_changesValue() {
        val firstValue = TextFieldCharSequence("ABCDE", TextRange.Zero)
        val processor = EditProcessor(firstValue)

        var resetCalled = 0
        processor.addResetListener { _, _ -> resetCalled++ }

        processor.update(CommitTextCommand("X", 1))
        val newState = processor.value

        assertThat(newState.toString()).isEqualTo("XABCDE")
        assertThat(newState.selectionInChars.min).isEqualTo(1)
        assertThat(newState.selectionInChars.max).isEqualTo(1)
        // edit command updates should not trigger reset listeners.
        assertThat(resetCalled).isEqualTo(0)
    }

    @Test
    fun apply_setSelectionCommand_changesValue() {
        val firstValue = TextFieldCharSequence("ABCDE", TextRange.Zero)
        val processor = EditProcessor(firstValue)

        var resetCalled = 0
        processor.addResetListener { _, _ -> resetCalled++ }

        processor.update(SetSelectionCommand(0, 2))
        val newState = processor.value

        assertThat(newState.toString()).isEqualTo("ABCDE")
        assertThat(newState.selectionInChars.min).isEqualTo(0)
        assertThat(newState.selectionInChars.max).isEqualTo(2)
        // edit command updates should not trigger reset listeners.
        assertThat(resetCalled).isEqualTo(0)
    }

    @Test
    fun testNewState_bufferNotUpdated_ifSameModelStructurally() {
        val processor = EditProcessor()
        var resetCalled = 0
        processor.addResetListener { _, _ -> resetCalled++ }

        val initialBuffer = processor.mBuffer
        processor.reset(TextFieldCharSequence("qwerty", TextRange.Zero, TextRange.Zero))
        assertThat(processor.mBuffer).isNotSameInstanceAs(initialBuffer)

        val updatedBuffer = processor.mBuffer
        processor.reset(TextFieldCharSequence("qwerty", TextRange.Zero, TextRange.Zero))
        assertThat(processor.mBuffer).isSameInstanceAs(updatedBuffer)

        assertThat(resetCalled).isEqualTo(2)
    }

    @Test
    fun testNewState_new_buffer_created_if_text_is_different() {
        val processor = EditProcessor()
        var resetCalled = 0
        processor.addResetListener { _, _ -> resetCalled++ }

        val textFieldValue = TextFieldCharSequence("qwerty", TextRange.Zero, TextRange.Zero)
        processor.reset(textFieldValue)
        val initialBuffer = processor.mBuffer

        val newTextFieldValue = TextFieldCharSequence("abc")
        processor.reset(newTextFieldValue)

        assertThat(processor.mBuffer).isNotSameInstanceAs(initialBuffer)
        assertThat(resetCalled).isEqualTo(2)
    }

    @Test
    fun testNewState_buffer_not_recreated_if_selection_is_different() {
        val processor = EditProcessor()
        var resetCalled = 0
        processor.addResetListener { _, _ -> resetCalled++ }

        val textFieldValue = TextFieldCharSequence("qwerty", TextRange.Zero, TextRange.Zero)
        processor.reset(textFieldValue)
        val initialBuffer = processor.mBuffer

        val newTextFieldValue = TextFieldCharSequence(textFieldValue, selection = TextRange(1))
        processor.reset(newTextFieldValue)

        assertThat(processor.mBuffer).isSameInstanceAs(initialBuffer)
        assertThat(newTextFieldValue.selectionInChars.start)
            .isEqualTo(processor.mBuffer.selectionStart)
        assertThat(newTextFieldValue.selectionInChars.end).isEqualTo(processor.mBuffer.selectionEnd)
        assertThat(resetCalled).isEqualTo(2)
    }

    @Test
    fun testNewState_buffer_not_recreated_if_composition_is_different() {
        val processor = EditProcessor()
        var resetCalled = 0
        processor.addResetListener { _, _ -> resetCalled++ }

        val textFieldValue = TextFieldCharSequence("qwerty", TextRange.Zero, TextRange(1))
        processor.reset(textFieldValue)
        val initialBuffer = processor.mBuffer

        // composition can not be set from app, IME owns it.
        assertThat(EditingBuffer.NOWHERE).isEqualTo(initialBuffer.compositionStart)
        assertThat(EditingBuffer.NOWHERE).isEqualTo(initialBuffer.compositionEnd)

        val newTextFieldValue = TextFieldCharSequence(
            textFieldValue,
            textFieldValue.selectionInChars,
            composition = null
        )
        processor.reset(newTextFieldValue)

        assertThat(processor.mBuffer).isSameInstanceAs(initialBuffer)
        assertThat(EditingBuffer.NOWHERE).isEqualTo(processor.mBuffer.compositionStart)
        assertThat(EditingBuffer.NOWHERE).isEqualTo(processor.mBuffer.compositionEnd)
        assertThat(resetCalled).isEqualTo(2)
    }

    @Test
    fun testNewState_reversedSelection_setsTheSelection() {
        val initialSelection = TextRange(2, 1)
        val textFieldValue = TextFieldCharSequence("qwerty", initialSelection, TextRange(1))
        val processor = EditProcessor(textFieldValue)
        var resetCalled = 0
        processor.addResetListener { _, _ -> resetCalled++ }

        val initialBuffer = processor.mBuffer

        assertThat(initialSelection.min).isEqualTo(initialBuffer.selectionStart)
        assertThat(initialSelection.max).isEqualTo(initialBuffer.selectionEnd)

        val updatedSelection = TextRange(3, 0)
        val newTextFieldValue = TextFieldCharSequence(textFieldValue, selection = updatedSelection)
        // set the new selection
        processor.reset(newTextFieldValue)

        assertThat(processor.mBuffer).isSameInstanceAs(initialBuffer)
        assertThat(updatedSelection.min).isEqualTo(initialBuffer.selectionStart)
        assertThat(updatedSelection.max).isEqualTo(initialBuffer.selectionEnd)
        assertThat(resetCalled).isEqualTo(1)
    }

    @Test
    fun compositionIsCleared_when_textChanged() {
        val processor = EditProcessor()
        var resetCalled = 0
        processor.addResetListener { _, _ -> resetCalled++ }

        // set the initial value
        processor.update(
            CommitTextCommand("ab", 0),
            SetComposingRegionCommand(0, 2)
        )

        // change the text
        val newValue =
            TextFieldCharSequence(
                "cd",
                processor.value.selectionInChars,
                processor.value.compositionInChars
            )
        processor.reset(newValue)

        assertThat(processor.value.toString()).isEqualTo(newValue.toString())
        assertThat(processor.value.compositionInChars).isNull()
    }

    @Test
    fun compositionIsNotCleared_when_textIsSame() {
        val processor = EditProcessor()
        val composition = TextRange(0, 2)

        // set the initial value
        processor.update(
            CommitTextCommand("ab", 0),
            SetComposingRegionCommand(composition.start, composition.end)
        )

        // use the same TextFieldValue
        val newValue =
            TextFieldCharSequence(
                processor.value,
                processor.value.selectionInChars,
                processor.value.compositionInChars
            )
        processor.reset(newValue)

        assertThat(processor.value.toString()).isEqualTo(newValue.toString())
        assertThat(processor.value.compositionInChars).isEqualTo(composition)
    }

    @Test
    fun compositionIsCleared_when_compositionReset() {
        val processor = EditProcessor()

        // set the initial value
        processor.update(
            CommitTextCommand("ab", 0),
            SetComposingRegionCommand(-1, -1)
        )

        // change the composition
        val newValue =
            TextFieldCharSequence(
                processor.value,
                processor.value.selectionInChars,
                composition = TextRange(0, 2)
            )
        processor.reset(newValue)

        assertThat(processor.value.toString()).isEqualTo(newValue.toString())
        assertThat(processor.value.compositionInChars).isNull()
    }

    @Test
    fun compositionIsCleared_when_compositionChanged() {
        val processor = EditProcessor()

        // set the initial value
        processor.update(
            CommitTextCommand("ab", 0),
            SetComposingRegionCommand(0, 2)
        )

        // change the composition
        val newValue = TextFieldCharSequence(
            processor.value,
            processor.value.selectionInChars,
            composition = TextRange(0, 1)
        )
        processor.reset(newValue)

        assertThat(processor.value.toString()).isEqualTo(newValue.toString())
        assertThat(processor.value.compositionInChars).isNull()
    }

    @Test
    fun compositionIsNotCleared_when_onlySelectionChanged() {
        val processor = EditProcessor()

        val composition = TextRange(0, 2)
        val selection = TextRange(0, 2)

        // set the initial value
        processor.update(
            CommitTextCommand("ab", 0),
            SetComposingRegionCommand(composition.start, composition.end),
            SetSelectionCommand(selection.start, selection.end)
        )

        // change selection
        val newSelection = TextRange(1)
        val newValue = TextFieldCharSequence(
            processor.value,
            selection = newSelection,
            composition = processor.value.compositionInChars
        )
        processor.reset(newValue)

        assertThat(processor.value.toString()).isEqualTo(newValue.toString())
        assertThat(processor.value.compositionInChars).isEqualTo(composition)
        assertThat(processor.value.selectionInChars).isEqualTo(newSelection)
    }

    @Test
    fun filterThatDoesNothing_doesNotResetBuffer() {
        val processor = EditProcessor(
            TextFieldCharSequence(
                "abc",
                selection = TextRange(3),
                composition = TextRange(0, 3)
            )
        )

        val initialBuffer = processor.mBuffer

        processor.update(CommitTextCommand("d", 4)) { _, _ -> }

        val value = processor.value

        assertThat(value.toString()).isEqualTo("abcd")
        assertThat(processor.mBuffer).isSameInstanceAs(initialBuffer)
    }

    @Test
    fun returningTheEquivalentValueFromFilter_doesNotResetBuffer() {
        val processor = EditProcessor(
            TextFieldCharSequence(
                "abc",
                selection = TextRange(3),
                composition = TextRange(0, 3)
            )
        )

        val initialBuffer = processor.mBuffer

        processor.update(CommitTextCommand("d", 4)) { _, _ -> /* Noop */ }

        val value = processor.value

        assertThat(value.toString()).isEqualTo("abcd")
        assertThat(processor.mBuffer).isSameInstanceAs(initialBuffer)
    }

    @Test
    fun returningOldValueFromFilter_resetsTheBuffer() {
        val processor = EditProcessor(
            TextFieldCharSequence(
                "abc",
                selection = TextRange(3),
                composition = TextRange(0, 3)
            )
        )

        var resetCalledOld: TextFieldCharSequence? = null
        var resetCalledNew: TextFieldCharSequence? = null
        processor.addResetListener { old, new ->
            resetCalledOld = old
            resetCalledNew = new
        }

        val initialBuffer = processor.mBuffer

        processor.update(CommitTextCommand("d", 4)) { _, new -> new.revertAllChanges() }

        val value = processor.value

        assertThat(value.toString()).isEqualTo("abc")
        assertThat(processor.mBuffer).isNotSameInstanceAs(initialBuffer)
        assertThat(resetCalledOld?.toString()).isEqualTo("abcd") // what IME applied
        assertThat(resetCalledNew?.toString()).isEqualTo("abc") // what is decided by filter
    }

    private fun EditProcessor.update(
        vararg editCommand: EditCommand,
        filter: TextEditFilter? = null
    ) {
        update(editCommand.toList(), filter)
    }
}
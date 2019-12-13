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

package androidx.ui.core

import androidx.ui.engine.geometry.Rect
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.input.CommitTextEditOp
import androidx.ui.input.EditOperation
import androidx.ui.input.EditProcessor
import androidx.ui.input.FinishComposingTextEditOp
import androidx.ui.input.ImeAction
import androidx.ui.input.InputState
import androidx.ui.input.KeyboardType
import androidx.ui.input.OffsetMap
import androidx.ui.input.SetSelectionEditOp
import androidx.ui.input.TextInputService
import androidx.ui.input.TransformedText
import androidx.ui.text.AnnotatedString
import androidx.ui.text.MultiParagraphIntrinsics
import androidx.ui.text.SpanStyle
import androidx.ui.text.TextDelegate
import androidx.ui.text.TextLayoutResult
import androidx.ui.text.TextRange
import androidx.ui.text.TextStyle
import androidx.ui.text.style.TextDecoration
import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class TextFieldDelegateTest {

    private lateinit var canvas: Canvas
    private lateinit var mDelegate: TextDelegate
    private lateinit var processor: EditProcessor
    private lateinit var onValueChange: (InputState) -> Unit
    private lateinit var onEditorActionPerformed: (Any) -> Unit
    private lateinit var textInputService: TextInputService
    private lateinit var layoutCoordinates: LayoutCoordinates
    private lateinit var multiParagraphIntrinsics: MultiParagraphIntrinsics
    private lateinit var textLayoutResult: TextLayoutResult

    /**
     * Test implementation of offset map which doubles the offset in transformed text.
     */
    private val skippingOffsetMap = object : OffsetMap {
        override fun originalToTransformed(offset: Int): Int = offset * 2
        override fun transformedToOriginal(offset: Int): Int = offset / 2
    }

    @Before
    fun setup() {
        mDelegate = mock()
        canvas = mock()
        processor = mock()
        onValueChange = mock()
        onEditorActionPerformed = mock()
        textInputService = mock()
        layoutCoordinates = mock()
        multiParagraphIntrinsics = mock()
        textLayoutResult = mock()
    }

    @Test
    fun draw_selection_test() {
        val selection = TextRange(0, 1)
        val selectionColor = Color.Blue

        TextFieldDelegate.draw(
            canvas = canvas,
            textDelegate = mDelegate,
            value = InputState(text = "Hello, World", selection = selection),
            selectionColor = selectionColor,
            hasFocus = true,
            offsetMap = OffsetMap.identityOffsetMap,
            textLayoutResult = textLayoutResult
        )

        verify(mDelegate, times(1)).paintBackground(
            eq(selection.min),
            eq(selection.max),
            eq(selectionColor),
            eq(canvas),
            eq(textLayoutResult))
        verify(mDelegate, times(1)).paint(eq(canvas), eq(textLayoutResult))

        verify(mDelegate, never()).paintCursor(any(), any(), eq(textLayoutResult))
    }

    @Test
    fun draw_cursor_test() {
        val cursor = TextRange(1, 1)

        TextFieldDelegate.draw(
            canvas = canvas,
            textDelegate = mDelegate,
            value = InputState(text = "Hello, World", selection = cursor),
            hasFocus = true,
            offsetMap = OffsetMap.identityOffsetMap,
            selectionColor = Color.Blue,
            textLayoutResult = textLayoutResult
        )

        verify(mDelegate, times(1)).paintCursor(eq(cursor.min), eq(canvas), eq(textLayoutResult))
        verify(mDelegate, times(1)).paint(eq(canvas), eq(textLayoutResult))
        verify(mDelegate, never()).paintBackground(any(), any(), any(), any(), eq(textLayoutResult))
    }

    @Test
    fun dont_draw_cursor_test() {
        val cursor = TextRange(1, 1)

        TextFieldDelegate.draw(
            canvas = canvas,
            textDelegate = mDelegate,
            value = InputState(text = "Hello, World", selection = cursor),
            hasFocus = false,
            offsetMap = OffsetMap.identityOffsetMap,
            selectionColor = Color.Blue,
            textLayoutResult = textLayoutResult
        )

        verify(mDelegate, never()).paintCursor(any(), any(), eq(textLayoutResult))
        verify(mDelegate, times(1)).paint(eq(canvas), eq(textLayoutResult))
        verify(mDelegate, never()).paintBackground(any(), any(), any(), any(), eq(textLayoutResult))
    }

    @Test
    fun test_on_edit_command() {
        val ops = listOf(CommitTextEditOp("Hello, World", 1))
        val dummyEditorState = InputState(text = "Hello, World", selection = TextRange(1, 1))

        whenever(processor.onEditCommands(ops)).thenReturn(dummyEditorState)

        TextFieldDelegate.onEditCommand(ops, processor, onValueChange)

        verify(onValueChange, times(1)).invoke(eq(
            InputState(
            text = dummyEditorState.text,
            selection = dummyEditorState.selection
        )
        ))
    }

    @Test
    fun test_on_release() {
        val position = PxPosition(100.px, 200.px)
        val offset = 10
        val dummyEditorState = InputState(text = "Hello, World", selection = TextRange(1, 1))
        val dummyInputSessionToken = 10 // We are not using this value in this test. Just dummy.

        whenever(textLayoutResult.getOffsetForPosition(position)).thenReturn(offset)

        val captor = argumentCaptor<List<EditOperation>>()

        whenever(processor.onEditCommands(captor.capture())).thenReturn(dummyEditorState)

        TextFieldDelegate.onRelease(
            position,
            textLayoutResult,
            processor,
            OffsetMap.identityOffsetMap,
            onValueChange,
            textInputService,
            dummyInputSessionToken,
            true)

        assertEquals(1, captor.allValues.size)
        assertEquals(1, captor.firstValue.size)
        assertTrue(captor.firstValue[0] is SetSelectionEditOp)
        verify(onValueChange, times(1)).invoke(eq(
            InputState(
                text = dummyEditorState.text,
                selection = dummyEditorState.selection
            )
        ))
        verify(textInputService).showSoftwareKeyboard(eq(dummyInputSessionToken))
    }

    @Test
    fun test_on_release_do_not_place_cursor_if_focus_is_out() {
        val position = PxPosition(100.px, 200.px)
        val offset = 10
        val dummyInputSessionToken = 10 // We are not using this value in this test. Just dummy.

        whenever(textLayoutResult.getOffsetForPosition(position)).thenReturn(offset)
        TextFieldDelegate.onRelease(
            position,
            textLayoutResult,
            processor,
            OffsetMap.identityOffsetMap,
            onValueChange,
            textInputService,
            dummyInputSessionToken,
            false)

        verify(onValueChange, never()).invoke(any())
        verify(textInputService).showSoftwareKeyboard(eq(dummyInputSessionToken))
    }

    @Test
    fun on_focus() {
        val dummyEditorState = InputState(text = "Hello, World", selection = TextRange(1, 1))
        TextFieldDelegate.onFocus(textInputService, dummyEditorState, processor,
            KeyboardType.Text, ImeAction.Unspecified, onValueChange, onEditorActionPerformed)
        verify(textInputService).startInput(
            eq(
                InputState(
                text = dummyEditorState.text,
                selection = dummyEditorState.selection
            )
            ),
            eq(KeyboardType.Text),
            eq(ImeAction.Unspecified),
            any(),
            eq(onEditorActionPerformed)
        )
    }

    @Test
    fun on_blur() {
        val captor = argumentCaptor<List<EditOperation>>()
        val dummyInputSessionToken = 10 // We are not using this value in this test. Just dummy.

        whenever(processor.onEditCommands(captor.capture())).thenReturn(InputState())

        TextFieldDelegate.onBlur(textInputService, dummyInputSessionToken, processor, onValueChange)

        assertEquals(1, captor.allValues.size)
        assertEquals(1, captor.firstValue.size)
        assertTrue(captor.firstValue[0] is FinishComposingTextEditOp)
        verify(textInputService).stopInput(eq(dummyInputSessionToken))
    }

    @Test
    fun notify_focused_rect() {
        val dummyRect = Rect(0f, 1f, 2f, 3f)
        whenever(textLayoutResult.getBoundingBox(any())).thenReturn(dummyRect)
        val dummyPoint = PxPosition(5.px, 6.px)
        whenever(layoutCoordinates.localToRoot(any())).thenReturn(dummyPoint)
        val dummyEditorState = InputState(text = "Hello, World", selection = TextRange(1, 1))
        val dummyInputSessionToken = 10 // We are not using this value in this test. Just dummy.
        TextFieldDelegate.notifyFocusedRect(
            dummyEditorState,
            mDelegate,
            textLayoutResult,
            layoutCoordinates,
            textInputService,
            dummyInputSessionToken,
            true /* hasFocus */,
            OffsetMap.identityOffsetMap
        )
        verify(textInputService).notifyFocusedRect(eq(dummyInputSessionToken), any())
    }

    @Test
    fun notify_focused_rect_without_focus() {
        val dummyEditorState = InputState(text = "Hello, World", selection = TextRange(1, 1))
        val dummyInputSessionToken = 10 // We are not using this value in this test. Just dummy.
        TextFieldDelegate.notifyFocusedRect(
            dummyEditorState,
            mDelegate,
            textLayoutResult,
            layoutCoordinates,
            textInputService,
            dummyInputSessionToken,
            false /* hasFocus */,
            OffsetMap.identityOffsetMap
        )
        verify(textInputService, never()).notifyFocusedRect(any(), any())
    }

    @Test
    fun notify_rect_tail() {
        val dummyRect = Rect(0f, 1f, 2f, 3f)
        whenever(textLayoutResult.getBoundingBox(any())).thenReturn(dummyRect)
        val dummyPoint = PxPosition(5.px, 6.px)
        whenever(layoutCoordinates.localToRoot(any())).thenReturn(dummyPoint)
        val dummyEditorState = InputState(text = "Hello, World", selection = TextRange(12, 12))
        val dummyInputSessionToken = 10 // We are not using this value in this test. Just dummy.
        TextFieldDelegate.notifyFocusedRect(
            dummyEditorState,
            mDelegate,
            textLayoutResult,
            layoutCoordinates,
            textInputService,
            dummyInputSessionToken,
            true /* hasFocus */,
            OffsetMap.identityOffsetMap
        )
        verify(textInputService).notifyFocusedRect(eq(dummyInputSessionToken), any())
    }

    @Test
    fun layout() {
        val constraints = Constraints(
            minWidth = 0.px.round(),
            maxWidth = 1024.px.round(),
            minHeight = 0.px.round(),
            maxHeight = 2048.px.round()
        )

        val dummyText = AnnotatedString(text = "Hello, World")
        whenever(mDelegate.text).thenReturn(dummyText)
        whenever(mDelegate.style).thenReturn(TextStyle())
        whenever(mDelegate.density).thenReturn(Density(1.0f))
        whenever(mDelegate.resourceLoader).thenReturn(mock())
        whenever(mDelegate.layout(any(), eq(null))).thenReturn(textLayoutResult)
        whenever(textLayoutResult.size).thenReturn(IntPxSize(1024.ipx, 512.ipx))

        val (width, height, layoutResult) = TextFieldDelegate.layout(mDelegate, constraints)
        assertEquals(1024.px.round(), width)
        assertEquals(512.px.round(), height)
        assertEquals(layoutResult, textLayoutResult)
    }

    @Test
    fun check_draw_uses_offset_map() {
        val selection = TextRange(1, 3)
        val selectionColor = Color.Blue

        TextFieldDelegate.draw(
            canvas = canvas,
            textDelegate = mDelegate,
            value = InputState(text = "Hello, World", selection = selection),
            selectionColor = selectionColor,
            hasFocus = true,
            offsetMap = skippingOffsetMap,
            textLayoutResult = textLayoutResult
        )

        val selectionStartInTransformedText = selection.min * 2
        val selectionEmdInTransformedText = selection.max * 2

        verify(mDelegate, times(1)).paintBackground(
            eq(selectionStartInTransformedText),
            eq(selectionEmdInTransformedText),
            eq(selectionColor),
            eq(canvas),
            eq(textLayoutResult))
    }

    @Test
    fun check_notify_rect_uses_offset_map() {
        val dummyRect = Rect(0f, 1f, 2f, 3f)
        val dummyPoint = PxPosition(5.px, 6.px)
        val dummyEditorState = InputState(text = "Hello, World", selection = TextRange(1, 3))
        val dummyInputSessionToken = 10 // We are not using this value in this test. Just dummy.
        whenever(textLayoutResult.getBoundingBox(any())).thenReturn(dummyRect)
        whenever(layoutCoordinates.localToRoot(any())).thenReturn(dummyPoint)

        TextFieldDelegate.notifyFocusedRect(
            dummyEditorState,
            mDelegate,
            textLayoutResult,
            layoutCoordinates,
            textInputService,
            dummyInputSessionToken,
            true /* hasFocus */,
            skippingOffsetMap
        )
        verify(textLayoutResult).getBoundingBox(6)
        verify(textInputService).notifyFocusedRect(eq(dummyInputSessionToken), any())
    }

    @Test
    fun check_on_release_uses_offset_map() {
        val position = PxPosition(100.px, 200.px)
        val offset = 10
        val dummyEditorState = InputState(text = "Hello, World", selection = TextRange(1, 1))
        val dummyInputSessionToken = 10 // We are not using this value in this test. Just dummy.

        whenever(textLayoutResult.getOffsetForPosition(position)).thenReturn(offset)

        val captor = argumentCaptor<List<EditOperation>>()

        whenever(processor.onEditCommands(captor.capture())).thenReturn(dummyEditorState)

        TextFieldDelegate.onRelease(
            position,
            textLayoutResult,
            processor,
            skippingOffsetMap,
            onValueChange,
            textInputService,
            dummyInputSessionToken,
            true)

        val cursorOffsetInTransformedText = offset / 2
        assertEquals(1, captor.allValues.size)
        assertEquals(1, captor.firstValue.size)
        assertTrue(captor.firstValue[0] is SetSelectionEditOp)
        val setSelectionEditOp = captor.firstValue[0] as SetSelectionEditOp
        assertEquals(cursorOffsetInTransformedText, setSelectionEditOp.start)
        assertEquals(cursorOffsetInTransformedText, setSelectionEditOp.end)
        verify(onValueChange, times(1)).invoke(eq(
            InputState(
                text = dummyEditorState.text,
                selection = dummyEditorState.selection
            )
        ))
    }

    @Test
    fun use_identity_mapping_if_visual_transformation_is_null() {
        val (visualText, offsetMap) = TextFieldDelegate.applyVisualFilter(
            InputState(text = "Hello, World"),
            null)

        assertEquals("Hello, World", visualText.text)
        for (i in 0..visualText.text.length) {
            // Identity mapping returns if no visual filter is provided.
            assertEquals(i, offsetMap.originalToTransformed(i))
            assertEquals(i, offsetMap.transformedToOriginal(i))
        }
    }

    @Test
    fun apply_composition_decoration() {
        val identityOffsetMap = object : OffsetMap {
            override fun originalToTransformed(offset: Int): Int = offset
            override fun transformedToOriginal(offset: Int): Int = offset
        }

        val input = TransformedText(
            transformedText = AnnotatedString.Builder().apply {
                pushStyle(SpanStyle(color = Color.Red))
                append("Hello, World")
            }.toAnnotatedString(),
            offsetMap = identityOffsetMap
        )

        val result = TextFieldDelegate.applyCompositionDecoration(
            compositionRange = TextRange(3, 6),
            transformed = input
        )

        assertThat(result.transformedText.text).isEqualTo(input.transformedText.text)
        assertThat(result.transformedText.spanStyles.size).isEqualTo(2)
        assertThat(result.transformedText.spanStyles).contains(
            AnnotatedString.Item(SpanStyle(textDecoration = TextDecoration.Underline), 3, 6)
        )
    }

    @Test
    fun infinte_constraints() {
        val constraints = Constraints(
            minWidth = 0.px.round(),
            maxWidth = IntPx.Infinity,
            minHeight = 0.px.round(),
            maxHeight = 2048.px.round()
        )

        val dummyText = AnnotatedString(text = "Hello, World")
        whenever(mDelegate.text).thenReturn(dummyText)
        whenever(mDelegate.style).thenReturn(TextStyle())
        whenever(mDelegate.density).thenReturn(Density(1.0f))
        whenever(mDelegate.resourceLoader).thenReturn(mock())
        whenever(mDelegate.layout(any(), eq(null))).thenReturn(textLayoutResult)
        whenever(textLayoutResult.size).thenReturn(IntPxSize(123.ipx, 512.ipx))
        whenever(mDelegate.maxIntrinsicWidth).thenReturn(123.ipx)

        val res = TextFieldDelegate.layout(mDelegate, constraints)
        assertThat(res.first).isEqualTo(123.ipx)
        assertEquals(512.ipx, res.second)

        verify(mDelegate, times(1)).layout(Constraints.tightConstraintsForWidth(123.ipx))
    }
}

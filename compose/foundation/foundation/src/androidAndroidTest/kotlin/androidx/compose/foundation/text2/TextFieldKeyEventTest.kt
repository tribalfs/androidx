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

import android.view.KeyEvent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.text.TEST_FONT_FAMILY
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalTextInputService
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performKeyPress
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TextInputService
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth
import org.mockito.kotlin.mock
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
class TextFieldKeyEventTest {
    @get:Rule
    val rule = createComposeRule()

    private var defaultDensity = Density(1f)

    @Test
    fun textField_typedEvents() {
        keysSequenceTest {
            Key.H.downAndUp()
            Key.I.downAndUp(KeyEvent.META_SHIFT_ON)
            expectedText("hI")
        }
    }

    @Ignore // re-enable after copy-cut-paste is supported
    @Test
    fun textField_copyPaste() {
        keysSequenceTest(initText = "hello") {
            Key.A.downAndUp(KeyEvent.META_CTRL_ON)
            Key.C.downAndUp(KeyEvent.META_CTRL_ON)
            Key.DirectionRight.downAndUp()
            Key.Spacebar.downAndUp()
            Key.V.downAndUp(KeyEvent.META_CTRL_ON)
            expectedText("hello hello")
        }
    }

    @Ignore // re-enable after copy-cut-paste is supported
    @Test
    fun textField_directCopyPaste() {
        keysSequenceTest(initText = "hello") {
            Key.A.downAndUp(KeyEvent.META_CTRL_ON)
            Key.Copy.downAndUp()
            expectedText("hello")
            Key.DirectionRight.downAndUp()
            Key.Spacebar.downAndUp()
            Key.Paste.downAndUp()
            expectedText("hello hello")
        }
    }

    @Ignore // re-enable after copy-cut-paste is supported
    @Test
    fun textField_directCutPaste() {
        keysSequenceTest(initText = "hello") {
            Key.A.downAndUp(KeyEvent.META_CTRL_ON)
            Key.Cut.downAndUp()
            expectedText("")
            Key.Paste.downAndUp()
            expectedText("hello")
        }
    }

    @Test
    fun textField_linesNavigation() {
        keysSequenceTest(initText = "hello\nworld") {
            Key.DirectionDown.downAndUp()
            Key.A.downAndUp()
            Key.DirectionUp.downAndUp()
            Key.A.downAndUp()
            expectedText("haello\naworld")
            Key.DirectionUp.downAndUp()
            Key.A.downAndUp()
            expectedText("ahaello\naworld")
        }
    }

    @Test
    fun textField_linesNavigation_cache() {
        keysSequenceTest(initText = "hello\n\nworld") {
            Key.DirectionRight.downAndUp()
            Key.DirectionDown.downAndUp()
            Key.DirectionDown.downAndUp()
            Key.Zero.downAndUp()
            expectedText("hello\n\nw0orld")
        }
    }

    @Test
    fun textField_newLine() {
        keysSequenceTest(initText = "hello") {
            Key.Enter.downAndUp()
            expectedText("\nhello")
        }
    }

    @Test
    fun textField_backspace() {
        keysSequenceTest(initText = "hello") {
            Key.DirectionRight.downAndUp()
            Key.DirectionRight.downAndUp()
            Key.Backspace.downAndUp()
            expectedText("hllo")
        }
    }

    @Test
    fun textField_delete() {
        keysSequenceTest(initText = "hello") {
            Key.Delete.downAndUp()
            expectedText("ello")
        }
    }

    @Test
    fun textField_delete_atEnd() {
        val text = "hello"
        val state = TextFieldState(
            TextFieldValue(
                text,
                // Place cursor at end.
                selection = TextRange(text.length)
            )
        )
        keysSequenceTest(state = state) {
            Key.Delete.downAndUp()
            expectedText("hello")
        }
    }

    @Test
    fun textField_delete_whenEmpty() {
        keysSequenceTest(initText = "") {
            Key.Delete.downAndUp()
            expectedText("")
        }
    }

    @Test
    fun textField_nextWord() {
        keysSequenceTest(initText = "hello world") {
            Key.DirectionRight.downAndUp(KeyEvent.META_CTRL_ON)
            Key.Zero.downAndUp()
            expectedText("hello0 world")
            Key.DirectionRight.downAndUp(KeyEvent.META_CTRL_ON)
            Key.Zero.downAndUp()
            expectedText("hello0 world0")
        }
    }

    @Test
    fun textField_nextWord_doubleSpace() {
        keysSequenceTest(initText = "hello  world") {
            Key.DirectionRight.downAndUp(KeyEvent.META_CTRL_ON)
            Key.DirectionRight.downAndUp()
            Key.DirectionRight.downAndUp(KeyEvent.META_CTRL_ON)
            Key.Zero.downAndUp()
            expectedText("hello  world0")
        }
    }

    @Test
    fun textField_prevWord() {
        keysSequenceTest(initText = "hello world") {
            Key.DirectionRight.downAndUp(KeyEvent.META_CTRL_ON)
            Key.DirectionRight.downAndUp(KeyEvent.META_CTRL_ON)
            Key.DirectionLeft.downAndUp(KeyEvent.META_CTRL_ON)
            Key.Zero.downAndUp()
            expectedText("hello 0world")
        }
    }

    @Test
    fun textField_HomeAndEnd() {
        keysSequenceTest(initText = "hello world") {
            Key.MoveEnd.downAndUp()
            Key.Zero.downAndUp()
            Key.MoveHome.downAndUp()
            Key.Zero.downAndUp()
            expectedText("0hello world0")
        }
    }

    @Test
    fun textField_byWordSelection() {
        keysSequenceTest(initText = "hello  world\nhi") {
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON)
            expectedSelection(TextRange(0, 5))
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON)
            expectedSelection(TextRange(0, 12))
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON)
            expectedSelection(TextRange(0, 15))
            Key.DirectionLeft.downAndUp(KeyEvent.META_SHIFT_ON or KeyEvent.META_CTRL_ON)
            expectedSelection(TextRange(0, 13))
        }
    }

    @Test
    fun textField_lineEndStart() {
        keysSequenceTest(initText = "hello world\nhi") {
            Key.MoveEnd.downAndUp()
            Key.Zero.downAndUp()
            expectedText("hello world0\nhi")
            Key.MoveEnd.downAndUp()
            Key.MoveHome.downAndUp()
            Key.Zero.downAndUp()
            expectedText("0hello world0\nhi")
            Key.MoveEnd.downAndUp(KeyEvent.META_SHIFT_ON)
            expectedSelection(TextRange(1, 16))
        }
    }

    @Test
    fun textField_deleteWords() {
        keysSequenceTest(initText = "hello world\nhi world") {
            Key.MoveEnd.downAndUp()
            Key.Backspace.downAndUp(KeyEvent.META_CTRL_ON)
            expectedText("hello \nhi world")
            Key.Delete.downAndUp(KeyEvent.META_CTRL_ON)
            expectedText("hello  world")
        }
    }

    @Test
    fun textField_deleteToBeginningOfLine() {
        keysSequenceTest(initText = "hello world\nhi world") {
            Key.DirectionRight.downAndUp(KeyEvent.META_CTRL_ON)
            Key.Backspace.downAndUp(KeyEvent.META_ALT_ON)
            expectedText(" world\nhi world")
            Key.Backspace.downAndUp(KeyEvent.META_ALT_ON)
            expectedText(" world\nhi world")
            repeat(3) { Key.DirectionRight.downAndUp() }
            Key.Backspace.downAndUp(KeyEvent.META_ALT_ON)
            expectedText("rld\nhi world")
            Key.DirectionDown.downAndUp()
            Key.MoveEnd.downAndUp()
            Key.Backspace.downAndUp(KeyEvent.META_ALT_ON)
            expectedText("rld\n")
            Key.Backspace.downAndUp(KeyEvent.META_ALT_ON)
            expectedText("rld\n")
        }
    }

    @Test
    fun textField_deleteToEndOfLine() {
        keysSequenceTest(initText = "hello world\nhi world") {
            Key.DirectionRight.downAndUp(KeyEvent.META_CTRL_ON)
            Key.Delete.downAndUp(KeyEvent.META_ALT_ON)
            expectedText("hello\nhi world")
            Key.Delete.downAndUp(KeyEvent.META_ALT_ON)
            expectedText("hello\nhi world")
            repeat(3) { Key.DirectionRight.downAndUp() }
            Key.Delete.downAndUp(KeyEvent.META_ALT_ON)
            expectedText("hello\nhi")
            Key.MoveHome.downAndUp()
            Key.Delete.downAndUp(KeyEvent.META_ALT_ON)
            expectedText("hello\n")
            Key.Delete.downAndUp(KeyEvent.META_ALT_ON)
            expectedText("hello\n")
        }
    }

    @Test
    fun textField_paragraphNavigation() {
        keysSequenceTest(initText = "hello world\nhi") {
            Key.DirectionDown.downAndUp(KeyEvent.META_CTRL_ON)
            Key.Zero.downAndUp()
            expectedText("hello world0\nhi")
            Key.DirectionDown.downAndUp(KeyEvent.META_CTRL_ON)
            Key.DirectionUp.downAndUp(KeyEvent.META_CTRL_ON)
            Key.Zero.downAndUp()
            expectedText("hello world0\n0hi")
        }
    }

    @Ignore // TODO(halilibo): Remove ignore when backing buffer supports reversed selection
    @Test
    fun textField_selectionCaret() {
        keysSequenceTest(initText = "hello world") {
            Key.DirectionRight.downAndUp(KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            expectedSelection(TextRange(0, 5))
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            expectedSelection(TextRange(0, 6))
            Key.Backslash.downAndUp(KeyEvent.META_CTRL_ON)
            expectedSelection(TextRange(6, 6))
            Key.DirectionLeft.downAndUp(KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON)
            expectedSelection(TextRange(6, 0))
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            expectedSelection(TextRange(1, 6))
        }
    }

    @Ignore // (b/276789499) Ignore for now
    @Test
    fun textField_pageNavigation() {
        keysSequenceTest(
            initText = "1\n2\n3\n4\n5",
            modifier = Modifier.requiredSize(77.dp)
        ) {
            // By page down, the cursor should be at the visible top line. In this case the height
            // constraint is 77dp which covers from 1(30), 2(30) and middle of 3(17). Thus,
            // by page down, the first line should be 3, and cursor should be the before letter 3,
            // i.e. index = 4.
            Key.PageDown.downAndUp()
            expectedSelection(TextRange(4))
        }
    }

    @Test
    fun textField_tabSingleLine() {
        keysSequenceTest(initText = "text", singleLine = true) {
            Key.Tab.downAndUp()
            expectedText("text") // no change, should try focus change instead
        }
    }

    @Test
    fun textField_tabMultiLine() {
        keysSequenceTest(initText = "text") {
            Key.Tab.downAndUp()
            expectedText("\ttext")
        }
    }

    @Test
    fun textField_shiftTabSingleLine() {
        keysSequenceTest(initText = "text", singleLine = true) {
            Key.Tab.downAndUp(metaState = KeyEvent.META_SHIFT_ON)
            expectedText("text") // no change, should try focus change instead
        }
    }

    @Test
    fun textField_enterSingleLine() {
        keysSequenceTest(initText = "text", singleLine = true) {
            Key.Enter.downAndUp()
            expectedText("text") // no change, should do ime action instead
        }
    }

    @Test
    fun textField_enterMultiLine() {
        keysSequenceTest(initText = "text") {
            Key.Enter.downAndUp()
            expectedText("\ntext")
        }
    }

    @Test
    fun textField_withActiveSelection_tabSingleLine() {
        keysSequenceTest(initText = "text", singleLine = true) {
            Key.DirectionRight.downAndUp()
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.Tab.downAndUp()
            expectedText("text") // no change, should try focus change instead
        }
    }

    @Test
    fun textField_withActiveSelection_tabMultiLine() {
        keysSequenceTest(initText = "text") {
            Key.DirectionRight.downAndUp()
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.Tab.downAndUp()
            expectedText("t\tt")
        }
    }

    @Ignore // TODO(halilibo): Remove ignore when backing buffer supports reversed selection
    @Test
    fun textField_selectToLeft() {
        keysSequenceTest(initText = "hello world hello") {
            Key.MoveEnd.downAndUp()
            expectedSelection(TextRange(17))
            Key.DirectionLeft.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.DirectionLeft.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.DirectionLeft.downAndUp(KeyEvent.META_SHIFT_ON)
            expectedSelection(TextRange(17, 14))
        }
    }

    @Test
    fun textField_withActiveSelection_shiftTabSingleLine() {
        keysSequenceTest(initText = "text", singleLine = true) {
            Key.DirectionRight.downAndUp()
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.Tab.downAndUp(metaState = KeyEvent.META_SHIFT_ON)
            expectedText("text") // no change, should try focus change instead
        }
    }

    @Test
    fun textField_withActiveSelection_enterSingleLine() {
        keysSequenceTest(initText = "text", singleLine = true) {
            Key.DirectionRight.downAndUp()
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.Enter.downAndUp()
            expectedText("text") // no change, should do ime action instead
        }
    }

    @Test
    fun textField_withActiveSelection_enterMultiLine() {
        keysSequenceTest(initText = "text") {
            Key.DirectionRight.downAndUp()
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.DirectionRight.downAndUp(KeyEvent.META_SHIFT_ON)
            Key.Enter.downAndUp()
            expectedText("t\nt")
        }
    }

    private inner class SequenceScope(
        val state: TextFieldState,
        val nodeGetter: () -> SemanticsNodeInteraction
    ) {
        fun Key.downAndUp(metaState: Int = 0) {
            this.down(metaState)
            this.up(metaState)
        }

        fun Key.down(metaState: Int = 0) {
            nodeGetter().performKeyPress(downEvent(this, metaState))
        }

        fun Key.up(metaState: Int = 0) {
            nodeGetter().performKeyPress(upEvent(this, metaState))
        }

        fun expectedText(text: String) {
            rule.runOnIdle {
                Truth.assertThat(state.value.text).isEqualTo(text)
            }
        }

        fun expectedSelection(selection: TextRange) {
            rule.runOnIdle {
                Truth.assertThat(state.value.selection).isEqualTo(selection)
            }
        }
    }

    private fun keysSequenceTest(
        initText: String = "",
        modifier: Modifier = Modifier.fillMaxSize(),
        singleLine: Boolean = false,
        sequence: SequenceScope.() -> Unit,
    ) {
        val state = TextFieldState(TextFieldValue(initText))
        keysSequenceTest(
            state = state,
            modifier = modifier,
            singleLine = singleLine,
            sequence = sequence
        )
    }

    private fun keysSequenceTest(
        state: TextFieldState,
        modifier: Modifier = Modifier.fillMaxSize(),
        singleLine: Boolean = false,
        sequence: SequenceScope.() -> Unit,
    ) {
        val inputService = TextInputService(mock())
        val focusRequester = FocusRequester()
        rule.setContent {
            LocalClipboardManager.current.setText(AnnotatedString("InitialTestText"))
            CompositionLocalProvider(
                LocalTextInputService provides inputService,
                LocalDensity provides defaultDensity
            ) {
                BasicTextField2(
                    state = state,
                    textStyle = TextStyle(
                        fontFamily = TEST_FONT_FAMILY,
                        fontSize = 30.sp
                    ),
                    modifier = modifier.focusRequester(focusRequester),
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                )
            }
        }

        rule.runOnIdle { focusRequester.requestFocus() }

        sequence(SequenceScope(state) { rule.onNode(hasSetTextAction()) })
    }
}

private fun downEvent(key: Key, metaState: Int = 0): androidx.compose.ui.input.key.KeyEvent {
    return androidx.compose.ui.input.key.KeyEvent(
        KeyEvent(0L, 0L, KeyEvent.ACTION_DOWN, key.nativeKeyCode, 0, metaState)
    )
}

private fun upEvent(key: Key, metaState: Int = 0): androidx.compose.ui.input.key.KeyEvent {
    return androidx.compose.ui.input.key.KeyEvent(
        KeyEvent(0L, 0L, KeyEvent.ACTION_UP, key.nativeKeyCode, 0, metaState)
    )
}
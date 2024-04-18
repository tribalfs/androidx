/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.compose.foundation.textfield

import androidx.compose.foundation.contextmenu.ContextMenuItemLabels
import androidx.compose.foundation.contextmenu.ContextMenuItemState
import androidx.compose.foundation.contextmenu.assertContextMenuItems
import androidx.compose.foundation.contextmenu.clickOffPopup
import androidx.compose.foundation.contextmenu.contextMenuItemInteraction
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.FocusedWindowTest
import androidx.compose.foundation.text.input.internal.selection.FakeClipboardManager
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.click
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.lerp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@MediumTest
@RunWith(AndroidJUnit4::class)
class TextFieldContextMenuTest : FocusedWindowTest {
    @get:Rule
    val rule = createComposeRule()

    private val textFieldTag = "BTF"
    private val defaultFullWidthText = "M".repeat(20)

    //region BasicTextField Context Menu Gesture Tests
    @Test
    fun contextMenu_rightClick_appears() {
        var value by mutableStateOf(TextFieldValue(defaultFullWidthText))
        rule.setTextFieldTestContent {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.testTag(textFieldTag)
            )
        }

        val contextMenuInteraction = rule.onNode(isPopup())
        contextMenuInteraction.assertDoesNotExist()
        rule.onNodeWithTag(textFieldTag).performMouseInput { rightClick(center) }
        contextMenuInteraction.assertExists()
    }

    @Test
    fun contextMenu_leftClick_doesNotAppear() {
        var value by mutableStateOf(TextFieldValue(defaultFullWidthText))
        rule.setTextFieldTestContent {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.testTag(textFieldTag)
            )
        }

        val contextMenuInteraction = rule.onNode(isPopup())
        contextMenuInteraction.assertDoesNotExist()
        rule.onNodeWithTag(textFieldTag).performMouseInput { click(center) }
        contextMenuInteraction.assertDoesNotExist()
    }

    @Test
    fun contextMenu_disabled_rightClick_doesNotAppear() {
        var value by mutableStateOf(TextFieldValue(defaultFullWidthText))
        rule.setTextFieldTestContent {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                enabled = false,
                modifier = Modifier.testTag(textFieldTag)
            )
        }

        val contextMenuInteraction = rule.onNode(isPopup())
        contextMenuInteraction.assertDoesNotExist()
        rule.onNodeWithTag(textFieldTag).performMouseInput { rightClick(center) }
        contextMenuInteraction.assertDoesNotExist()
    }

    @Test
    fun contextMenu_disappearsOnClickOffOfPopup() {
        var value by mutableStateOf(TextFieldValue(defaultFullWidthText))
        rule.setTextFieldTestContent {
            BasicTextField(
                value = value,
                onValueChange = { value = it },
                modifier = Modifier.testTag(textFieldTag)
            )
        }

        val contextMenuInteraction = rule.onNode(isPopup())
        contextMenuInteraction.assertDoesNotExist()
        rule.onNodeWithTag(textFieldTag).performMouseInput { rightClick(center) }
        contextMenuInteraction.assertExists()
        rule.clickOffPopup { rootRect -> lerp(rootRect.topLeft, rootRect.center, 0.5f) }
        contextMenuInteraction.assertDoesNotExist()
    }
    //endregion BasicTextField Context Menu Gesture Tests

    //region Context Menu Item Click Tests
    @Test
    fun contextMenu_onClickCut() = runClickContextMenuItemTest(
        labelToClick = ContextMenuItemLabels.CUT,
        expectedText = "Text  Text",
        expectedSelection = TextRange(5),
        expectedClipboardContent = "Text",
    )

    @Test
    fun contextMenu_onClickCopy() = runClickContextMenuItemTest(
        labelToClick = ContextMenuItemLabels.COPY,
        expectedText = "Text Text Text",
        expectedSelection = TextRange(5, 9),
        expectedClipboardContent = "Text",
    )

    @Test
    fun contextMenu_onClickPaste() = runClickContextMenuItemTest(
        labelToClick = ContextMenuItemLabels.PASTE,
        expectedText = "Text clip Text",
        expectedSelection = TextRange(9),
        expectedClipboardContent = "clip",
    )

    @Test
    fun contextMenu_onClickSelectAll() = runClickContextMenuItemTest(
        labelToClick = ContextMenuItemLabels.SELECT_ALL,
        expectedText = "Text Text Text",
        expectedSelection = TextRange(0, 14),
        expectedClipboardContent = "clip",
    )

    private fun runClickContextMenuItemTest(
        labelToClick: String,
        expectedText: String,
        expectedSelection: TextRange,
        expectedClipboardContent: String,
    ) {
        val text = "Text Text Text"
        val initialClipboardText = "clip"

        var value by mutableStateOf(
            TextFieldValue(
                text = text,
                selection = TextRange(5, 9),
            )
        )

        val clipboardManager = FakeClipboardManager(
            initialText = initialClipboardText,
            supportsClipEntry = true,
        )

        rule.setTextFieldTestContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboardManager) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    modifier = Modifier.testTag(textFieldTag)
                )
            }
        }

        rule.onNodeWithTag(textFieldTag).performMouseInput { rightClick(center) }
        val itemInteraction = rule.contextMenuItemInteraction(labelToClick)
        itemInteraction.assertHasClickAction()
        itemInteraction.assertIsEnabled()
        itemInteraction.performClick()
        rule.waitForIdle()

        // Context menu disappears
        rule.onNode(isPopup()).assertDoesNotExist()
        itemInteraction.assertDoesNotExist()

        // Operation was applied
        assertThat(value.text).isEqualTo(expectedText)
        assertThat(value.selection).isEqualTo(expectedSelection)
        val clipboardContent = clipboardManager.getText()
        assertThat(clipboardContent).isNotNull()
        assertThat(clipboardContent!!.text).isEqualTo(expectedClipboardContent)
    }
    //endregion Context Menu Item Click Tests

    //region Context Menu Correct Item Tests
    @Test
    fun contextMenu_emptyClipboard_noSelection_itemsMatch() = runCorrectItemsTest(
        isEmptyClipboard = true,
        selectionAmount = SelectionAmount.NONE,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.DISABLED,
            copyState = ContextMenuItemState.DISABLED,
            pasteState = ContextMenuItemState.DISABLED,
            selectAllState = ContextMenuItemState.ENABLED,
        )
    }

    @Test
    fun contextMenu_emptyClipboard_partialSelection_itemsMatch() = runCorrectItemsTest(
        isEmptyClipboard = true,
        selectionAmount = SelectionAmount.PARTIAL,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.ENABLED,
            copyState = ContextMenuItemState.ENABLED,
            pasteState = ContextMenuItemState.DISABLED,
            selectAllState = ContextMenuItemState.ENABLED,
        )
    }

    @Test
    fun contextMenu_emptyClipboard_fullSelection_itemsMatch() = runCorrectItemsTest(
        isEmptyClipboard = true,
        selectionAmount = SelectionAmount.ALL,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.ENABLED,
            copyState = ContextMenuItemState.ENABLED,
            pasteState = ContextMenuItemState.DISABLED,
            selectAllState = ContextMenuItemState.DISABLED,
        )
    }

    @Test
    fun contextMenu_nonEmptyClipboard_noSelection_itemsMatch() = runCorrectItemsTest(
        isEmptyClipboard = false,
        selectionAmount = SelectionAmount.NONE,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.DISABLED,
            copyState = ContextMenuItemState.DISABLED,
            pasteState = ContextMenuItemState.ENABLED,
            selectAllState = ContextMenuItemState.ENABLED,
        )
    }

    @Test
    fun contextMenu_nonEmptyClipboard_partialSelection_itemsMatch() = runCorrectItemsTest(
        isPassword = false,
        isReadOnly = false,
        isEmptyClipboard = false,
        selectionAmount = SelectionAmount.PARTIAL,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.ENABLED,
            copyState = ContextMenuItemState.ENABLED,
            pasteState = ContextMenuItemState.ENABLED,
            selectAllState = ContextMenuItemState.ENABLED,
        )
    }

    @Test
    fun contextMenu_nonEmptyClipboard_fullSelection_itemsMatch() = runCorrectItemsTest(
        isEmptyClipboard = false,
        selectionAmount = SelectionAmount.ALL,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.ENABLED,
            copyState = ContextMenuItemState.ENABLED,
            pasteState = ContextMenuItemState.ENABLED,
            selectAllState = ContextMenuItemState.DISABLED,
        )
    }

    @Test
    fun contextMenu_password_noSelection_itemsMatch() = runCorrectItemsTest(
        isPassword = true,
        selectionAmount = SelectionAmount.NONE,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.DISABLED,
            copyState = ContextMenuItemState.DISABLED,
            pasteState = ContextMenuItemState.ENABLED,
            selectAllState = ContextMenuItemState.ENABLED,
        )
    }

    @Test
    fun contextMenu_password_partialSelection_itemsMatch() = runCorrectItemsTest(
        isPassword = true,
        selectionAmount = SelectionAmount.PARTIAL,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.DISABLED,
            copyState = ContextMenuItemState.DISABLED,
            pasteState = ContextMenuItemState.ENABLED,
            selectAllState = ContextMenuItemState.ENABLED,
        )
    }

    @Test
    fun contextMenu_password_fullSelection_itemsMatch() = runCorrectItemsTest(
        isPassword = true,
        selectionAmount = SelectionAmount.ALL,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.DISABLED,
            copyState = ContextMenuItemState.DISABLED,
            pasteState = ContextMenuItemState.ENABLED,
            selectAllState = ContextMenuItemState.DISABLED,
        )
    }

    @Test
    fun contextMenu_readOnly_noSelection_itemsMatch() = runCorrectItemsTest(
        isReadOnly = true,
        isEmptyClipboard = true,
        selectionAmount = SelectionAmount.NONE,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.DISABLED,
            copyState = ContextMenuItemState.DISABLED,
            pasteState = ContextMenuItemState.DISABLED,
            selectAllState = ContextMenuItemState.ENABLED,
        )
    }

    @Test
    fun contextMenu_readOnly_partialSelection_itemsMatch() = runCorrectItemsTest(
        isReadOnly = true,
        isEmptyClipboard = true,
        selectionAmount = SelectionAmount.PARTIAL,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.DISABLED,
            copyState = ContextMenuItemState.ENABLED,
            pasteState = ContextMenuItemState.DISABLED,
            selectAllState = ContextMenuItemState.ENABLED,
        )
    }

    @Test
    fun contextMenu_readOnly_fullSelection_itemsMatch() = runCorrectItemsTest(
        isReadOnly = true,
        isEmptyClipboard = true,
        selectionAmount = SelectionAmount.ALL,
    ) {
        rule.assertContextMenuItems(
            cutState = ContextMenuItemState.DISABLED,
            copyState = ContextMenuItemState.ENABLED,
            pasteState = ContextMenuItemState.DISABLED,
            selectAllState = ContextMenuItemState.DISABLED,
        )
    }

    private enum class SelectionAmount { NONE, PARTIAL, ALL }

    private fun runCorrectItemsTest(
        isPassword: Boolean = false,
        isReadOnly: Boolean = false,
        isEmptyClipboard: Boolean = false,
        selectionAmount: SelectionAmount = SelectionAmount.PARTIAL,
        assertBlock: () -> Unit,
    ) {
        val text = "Text Text Text"
        var value by mutableStateOf(
            TextFieldValue(
                text = text,
                selection = when (selectionAmount) {
                    SelectionAmount.NONE -> TextRange.Zero
                    SelectionAmount.PARTIAL -> TextRange(5, 9)
                    SelectionAmount.ALL -> TextRange(0, 14)
                }
            )
        )

        val visualTransformation =
            if (isPassword) PasswordVisualTransformation() else VisualTransformation.None

        val clipboardManager = FakeClipboardManager(supportsClipEntry = true).apply {
            if (isEmptyClipboard) {
                setClip(null)
            } else {
                setText(AnnotatedString("Clipboard Text"))
            }
        }

        rule.setTextFieldTestContent {
            CompositionLocalProvider(LocalClipboardManager provides clipboardManager) {
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    visualTransformation = visualTransformation,
                    readOnly = isReadOnly,
                    modifier = Modifier.testTag(textFieldTag)
                )
            }
        }

        rule.onNodeWithTag(textFieldTag).performMouseInput { rightClick(center) }
        assertBlock()
    }
    //endregion Context Menu Correct Item Tests
}

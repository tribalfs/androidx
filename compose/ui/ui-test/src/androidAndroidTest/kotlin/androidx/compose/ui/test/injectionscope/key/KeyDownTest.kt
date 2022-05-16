/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.ui.test.injectionscope.key

import androidx.compose.testutils.expectError
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.KeyInjectionScope
import androidx.compose.ui.test.injectionscope.key.Common.assertTyped
import androidx.compose.ui.test.injectionscope.key.Common.performKeyInput
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.util.TestTextField
import androidx.test.filters.MediumTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Tests if [KeyInjectionScope.keyDown] works
 */
@MediumTest
@OptIn(ExperimentalComposeUiApi::class, ExperimentalTestApi::class)
class KeyDownTest {
    companion object {
        private val enterKey = Key.Enter
        private val aKey = Key.A
    }

    @get:Rule
    val rule = createComposeRule()

    @Before
    fun setUp() {
        // Set content to a simple text field.
        rule.setContent {
            TestTextField()
        }
        // Bring text field into focus by clicking on it.
        rule.onNodeWithTag(TestTextField.Tag).performClick()
    }

    @Test
    fun doubleDown_throwsIllegalStateException() {
        rule.performKeyInput { keyDown(enterKey) }
        expectError<IllegalStateException>(
            expectedMessage =
            "Cannot send key down event, Key\\($enterKey\\) is already pressed down."
        ) {
            rule.performKeyInput { keyDown(enterKey) }
        }
    }

    @Test
    fun unDownedKey_isNotDown() {
        rule.performKeyInput { assertFalse(isKeyDown(aKey)) }
    }

    @Test
    fun downedKey_isDown() {
        rule.performKeyInput {
            keyDown(aKey)
            assertTrue(isKeyDown(aKey))
        }
    }

    @Test
    fun enterDown_typesNewLine() {
        rule.performKeyInput { keyDown(enterKey) }
        rule.assertTyped("\n")
    }

    @Test
    fun letterDown_typesLetter() {
        rule.performKeyInput { keyDown(aKey) }
        rule.assertTyped("a")
    }

    @Test
    fun letterDownWithShiftDown_typesCapitalLetter() {
        rule.performKeyInput {
            keyDown(Key.ShiftLeft)
            keyDown(aKey)
        }
        rule.assertTyped("A")
    }

    @Test
    fun semicolonWithShiftDown_typesColon() {
        rule.performKeyInput {
            keyDown(Key.ShiftLeft)
            keyDown(Key.Semicolon)
        }
        rule.assertTyped(":")
    }
}

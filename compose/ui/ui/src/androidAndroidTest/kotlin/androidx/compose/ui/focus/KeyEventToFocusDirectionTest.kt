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

package androidx.compose.ui.focus

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.node.Owner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SmallTest
import com.google.common.truth.Truth
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.view.KeyEvent as AndroidKeyEvent
import android.view.KeyEvent.ACTION_DOWN as KeyDown
import android.view.KeyEvent.META_SHIFT_ON as Shift

@SmallTest
@RunWith(AndroidJUnit4::class)
class KeyEventToFocusDirectionTest {
    @get:Rule
    val rule = createComposeRule()

    private lateinit var owner: Owner

    @Before
    fun setup() {
        rule.setContent {
            owner = LocalView.current as Owner
        }
    }

    @Test
    fun left() {
        // Arrange.
        val keyEvent = KeyEvent(AndroidKeyEvent(KeyDown, Key.DirectionLeft.nativeKeyCode))

        // Act.
        val focusDirection = owner.getFocusDirection(keyEvent)

        // Assert.
        Truth.assertThat(focusDirection).isEqualTo(FocusDirection.Left)
    }

    @Test
    fun right() {
        // Arrange.
        val keyEvent = KeyEvent(AndroidKeyEvent(KeyDown, Key.DirectionRight.nativeKeyCode))

        // Act.
        val focusDirection = owner.getFocusDirection(keyEvent)

        // Assert.
        Truth.assertThat(focusDirection).isEqualTo(FocusDirection.Right)
    }

    @Test
    fun up() {
        // Arrange.
        val keyEvent = KeyEvent(AndroidKeyEvent(KeyDown, Key.DirectionUp.nativeKeyCode))

        // Act.
        val focusDirection = owner.getFocusDirection(keyEvent)

        // Assert.
        Truth.assertThat(focusDirection).isEqualTo(FocusDirection.Up)
    }

    @Test
    fun down() {
        // Arrange.
        val keyEvent = KeyEvent(AndroidKeyEvent(KeyDown, Key.DirectionDown.nativeKeyCode))

        // Act.
        val focusDirection = owner.getFocusDirection(keyEvent)

        // Assert.
        Truth.assertThat(focusDirection).isEqualTo(FocusDirection.Down)
    }

    @Test
    fun tab_next() {
        // Arrange.
        val keyEvent = KeyEvent(AndroidKeyEvent(KeyDown, Key.Tab.nativeKeyCode))

        // Act.
        val focusDirection = owner.getFocusDirection(keyEvent)

        // Assert.
        Truth.assertThat(focusDirection).isEqualTo(FocusDirection.Next)
    }

    @Test
    fun shiftTab_previous() {
        // Arrange.
        val keyEvent = KeyEvent(AndroidKeyEvent(0L, 0L, KeyDown, Key.Tab.nativeKeyCode, 0, Shift))

        // Act.
        val focusDirection = owner.getFocusDirection(keyEvent)

        // Assert.
        Truth.assertThat(focusDirection).isEqualTo(FocusDirection.Previous)
    }
}

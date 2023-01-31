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

package androidx.compose.material3

import android.provider.Settings.System.TIME_12_24
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsProperties.SelectableGroup
import androidx.compose.ui.test.SemanticsMatcher.Companion.expectValue
import androidx.compose.ui.test.SemanticsMatcher.Companion.keyIsDefined
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertAll
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelectable
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.isFocusable
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onSiblings
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.platform.app.InstrumentationRegistry.getInstrumentation
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMaterial3Api::class)
@MediumTest
@RunWith(AndroidJUnit4::class)
class TimePickerTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun timePicker_initialState() {
        val state = TimePickerState(initialHour = 14, initialMinute = 23, is24Hour = false)
        rule.setMaterialContent(lightColorScheme()) {
            TimePicker(state)
        }

        rule.onAllNodesWithText("23").assertCountEquals(1)

        rule.onNodeWithText("02").assertIsSelected()

        rule.onNodeWithText("AM").assertExists()

        rule.onNodeWithText("PM").assertExists().assertIsSelected()
    }

    @Test
    fun timePicker_switchToMinutes() {
        val state = TimePickerState(initialHour = 14, initialMinute = 23, is24Hour = false)
        rule.setMaterialContent(lightColorScheme()) {
            TimePicker(state)
        }

        rule.onNodeWithText("23").performClick()

        rule.onNodeWithText("55").assertExists()
    }

    @Test
    fun timePicker_selectHour() {
        val state = TimePickerState(initialHour = 14, initialMinute = 23, is24Hour = false)
        rule.setMaterialContent(lightColorScheme()) {
            TimePicker(state)
        }

        rule.onNodeWithText("6").performClick()

        // shows 06 in display
        rule.onNodeWithText("06").assertExists()

        // switches to minutes
        rule.onNodeWithText("23").assertIsSelected()

        // state updated
        assertThat(state.hour).isEqualTo(18)
    }

    @Test
    fun timePicker_switchToAM() {
        val state = TimePickerState(initialHour = 14, initialMinute = 23, is24Hour = false)
        rule.setMaterialContent(lightColorScheme()) {
            TimePicker(state)
        }

        assertThat(state.hour).isEqualTo(14)

        rule.onNodeWithText("AM").performClick()

        assertThat(state.hour).isEqualTo(2)
    }

    @Test
    fun timePicker_dragging() {
        val state = TimePickerState(initialHour = 0, initialMinute = 23, is24Hour = false)
        rule.setMaterialContent(lightColorScheme()) {
            TimePicker(state)
        }

        rule.onAllNodes(keyIsDefined(SelectableGroup), useUnmergedTree = true)
            .onLast()
            .performTouchInput {
                down(topCenter)
                // 3 O'Clock
                moveTo(centerRight)
                up()
            }

        rule.runOnIdle {
            assertThat(state.hour).isEqualTo(3)
        }
    }

    @Test
    fun timePickerState_format_12h() {
        lateinit var state: TimePickerState
        getInstrumentation().uiAutomation.executeShellCommand(
            "settings put system $TIME_12_24 12"
        )
        rule.setContent {
            state = rememberTimePickerState()
        }

        assertThat(state.is24hour).isFalse()
    }

    @Test
    fun timePickerState_format_24h() {
        lateinit var state: TimePickerState
        getInstrumentation().uiAutomation.executeShellCommand(
            "settings put system $TIME_12_24 24"
        )
        rule.setContent {
            state = rememberTimePickerState()
        }

        assertThat(state.is24hour).isTrue()
    }

    @Test
    fun timePicker_toggle_semantics() {
        val state = TimePickerState(initialHour = 14, initialMinute = 23, is24Hour = false)
        lateinit var contentDescription: String
        rule.setMaterialContent(lightColorScheme()) {
            contentDescription = getString(Strings.TimePickerPeriodToggle)
            TimePicker(state)
        }

        rule.onNodeWithContentDescription(contentDescription)
            .onChildren()
            .assertAll(isSelectable())
    }

    @Test
    fun timePicker_display_semantics() {
        val state = TimePickerState(initialHour = 14, initialMinute = 23, is24Hour = false)
        lateinit var minuteDescription: String
        lateinit var hourDescription: String
        rule.setMaterialContent(lightColorScheme()) {
            minuteDescription = getString(Strings.TimePickerMinuteSelection)
            hourDescription = getString(Strings.TimePickerHourSelection)
            TimePicker(state)
        }

        rule.onNodeWithContentDescription(minuteDescription)
            .assertIsSelectable()
            .assertIsNotSelected()
            .assert(expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertHasClickAction()

        rule.onNodeWithContentDescription(hourDescription)
            .assertIsSelectable()
            .assertIsSelected()
            .assert(expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertHasClickAction()
    }

    @Test
    fun timePicker_clockFace_hour_semantics() {
        val state = TimePickerState(initialHour = 14, initialMinute = 23, is24Hour = false)
        lateinit var hourDescription: String

        rule.setMaterialContent(lightColorScheme()) {
            hourDescription = getString(Strings.TimePickerHourSuffix, 2)
            TimePicker(state)
        }

        rule.onAllNodesWithContentDescription(hourDescription)
            .onLast()
            .onSiblings()
            .filter(isFocusable())
            .assertCountEquals(11)
            .assertAll(
                hasContentDescription(
                    value = "o'clock",
                    substring = true,
                    ignoreCase = true
                )
            )
    }

    @Test
    fun timePicker_clockFace_selected_semantics() {
        val state = TimePickerState(initialHour = 14, initialMinute = 23, is24Hour = true)

        rule.setMaterialContent(lightColorScheme()) {
            TimePicker(state)
        }

        rule.onAllNodesWithText("14")
            .assertAll(isSelected())
    }

    @Test
    fun timePicker_clockFace_minutes_semantics() {
        val state = TimePickerState(initialHour = 14, initialMinute = 23, is24Hour = false)
        lateinit var minuteDescription: String

        rule.setMaterialContent(lightColorScheme()) {
            minuteDescription = getString(Strings.TimePickerMinuteSuffix, 55)
            TimePicker(state)
        }

        // Switch to minutes
        rule.onNodeWithText("23").performClick()

        rule.waitForIdle()

        rule.onNodeWithContentDescription(minuteDescription)
            .assertExists()
            .onSiblings()
            .assertCountEquals(11)
            .assertAll(
                hasContentDescription(
                    value = "minutes",
                    substring = true,
                    ignoreCase = true
                )
            )
    }
}
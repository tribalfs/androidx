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
package androidx.compose.material

import androidx.compose.foundation.Strings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.ToggleableState
import androidx.compose.foundation.selection.ToggleableState.Indeterminate
import androidx.compose.foundation.selection.ToggleableState.Off
import androidx.compose.foundation.selection.ToggleableState.On
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertValueEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class CheckboxUiTest {

    @get:Rule
    val rule = createComposeRule()

    private val defaultTag = "myCheckbox"

    @Test
    fun checkBoxTest_defaultSemantics() {
        rule.setMaterialContent {
            Column {
                Checkbox(false, {}, modifier = Modifier.testTag(tag = "checkboxUnchecked"))
                Checkbox(true, {}, modifier = Modifier.testTag("checkboxChecked"))
            }
        }

        rule.onNodeWithTag("checkboxUnchecked")
            .assertIsEnabled()
            .assertIsOff()
            .assertValueEquals(Strings.Unchecked)

        rule.onNodeWithTag("checkboxChecked")
            .assertIsEnabled()
            .assertIsOn()
            .assertValueEquals(Strings.Checked)
    }

    @Test
    fun checkBoxTest_toggle() {
        rule.setMaterialContent {
            val (checked, onCheckedChange) = remember { mutableStateOf(false) }
            Checkbox(checked, onCheckedChange, modifier = Modifier.testTag(defaultTag))
        }

        rule.onNodeWithTag(defaultTag)
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    @Test
    fun checkBoxTest_toggle_twice() {
        rule.setMaterialContent {
            val (checked, onCheckedChange) = remember { mutableStateOf(false) }
            Checkbox(checked, onCheckedChange, modifier = Modifier.testTag(defaultTag))
        }

        rule.onNodeWithTag(defaultTag)
            .assertIsOff()
            .performClick()
            .assertIsOn()
            .performClick()
            .assertIsOff()
    }

    @Test
    fun checkBoxTest_untoggleable_whenNoLambda() {

        rule.setMaterialContent {
            val (checked, _) = remember { mutableStateOf(false) }
            Checkbox(checked, {}, enabled = false, modifier = Modifier.testTag(defaultTag))
        }

        rule.onNodeWithTag(defaultTag)
            .assertHasNoClickAction()
    }

    @Test
    fun checkBoxTest_MaterialSize_WhenChecked() {
        materialSizeTestForValue(On)
    }

    @Test
    fun checkBoxTest_MaterialSize_WhenUnchecked() {
        materialSizeTestForValue(Off)
    }

    @Test
    fun checkBoxTest_MaterialSize_WhenIndeterminate() {
        materialSizeTestForValue(Indeterminate)
    }

    private fun materialSizeTestForValue(checkboxValue: ToggleableState) {
        rule
            .setMaterialContentForSizeAssertions {
                TriStateCheckbox(state = checkboxValue, onClick = {}, enabled = false)
            }
            .assertIsSquareWithSize(2.dp * 2 + 20.dp)
    }
}
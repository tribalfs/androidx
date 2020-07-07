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
package androidx.ui.material

import android.os.Build
import androidx.compose.state
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.test.screenshot.assertAgainstGolden
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.ui.core.semantics.semantics
import androidx.ui.core.testTag
import androidx.ui.foundation.Box
import androidx.ui.foundation.selection.ToggleableState
import androidx.ui.layout.wrapContentSize
import androidx.ui.test.captureToBitmap
import androidx.ui.test.center
import androidx.ui.test.createComposeRule
import androidx.ui.test.doClick
import androidx.ui.test.doPartialGesture
import androidx.ui.test.find
import androidx.ui.test.findByTag
import androidx.ui.test.isToggleable
import androidx.ui.test.sendDown
import androidx.ui.test.waitForIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class CheckboxScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val screenshotRule = AndroidXScreenshotTestRule(GOLDEN_MATERIAL)

    val wrap = Modifier.wrapContentSize(Alignment.TopStart)

    // TODO: this test tag as well as Boxes inside testa are temporarty, remove then b/157687898
    //  is fixed
    private val wrapperTestTag = "checkboxWrapper"

    @Test
    fun checkBoxTest_checked() {
        composeTestRule.setMaterialContent {
            Box(wrap.semantics().testTag(wrapperTestTag)) {
                Checkbox(checked = true, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("checkbox_checked")
    }

    @Test
    fun checkBoxTest_unchecked() {
        composeTestRule.setMaterialContent {
            Box(wrap.semantics().testTag(wrapperTestTag)) {
                Checkbox(modifier = wrap, checked = false, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("checkbox_unchecked")
    }

    @Test
    fun checkBoxTest_pressed() {
        composeTestRule.setMaterialContent {
            Box(wrap.semantics().testTag(wrapperTestTag)) {
                Checkbox(modifier = wrap, checked = false, onCheckedChange = { })
            }
        }
        findByTag(wrapperTestTag).doPartialGesture {
            sendDown(center)
        }
        assertToggeableAgainstGolden("checkbox_pressed")
    }

    @Test
    fun checkBoxTest_indeterminate() {
        composeTestRule.setMaterialContent {
            Box(wrap.semantics().testTag(wrapperTestTag)) {
                TriStateCheckbox(
                    state = ToggleableState.Indeterminate,
                    modifier = wrap,
                    onClick = {})
            }
        }
        assertToggeableAgainstGolden("checkbox_indeterminate")
    }

    @Test
    fun checkBoxTest_disabled_checked() {
        composeTestRule.setMaterialContent {
            Box(wrap.semantics().testTag(wrapperTestTag)) {
                Checkbox(modifier = wrap, checked = true, enabled = false, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("checkbox_disabled_checked")
    }

    @Test
    fun checkBoxTest_disabled_unchecked() {
        composeTestRule.setMaterialContent {
            Box(wrap.semantics().testTag(wrapperTestTag)) {
                Checkbox(modifier = wrap, checked = false, enabled = false, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("checkbox_disabled_unchecked")
    }

    @Test
    fun checkBoxTest_disabled_indeterminate() {
        composeTestRule.setMaterialContent {
            Box(wrap.semantics().testTag(wrapperTestTag)) {
                TriStateCheckbox(
                    state = ToggleableState.Indeterminate,
                    enabled = false,
                    modifier = wrap,
                    onClick = {})
            }
        }
        assertToggeableAgainstGolden("checkbox_disabled_indeterminate")
    }

    @Test
    fun checkBoxTest_unchecked_animateToChecked() {
        composeTestRule.setMaterialContent {
            val isChecked = state { false }
            Box(wrap.semantics().testTag(wrapperTestTag)) {
                Checkbox(
                    modifier = wrap,
                    checked = isChecked.value,
                    onCheckedChange = { isChecked.value = it }
                )
            }
        }

        composeTestRule.clockTestRule.pauseClock()

        find(isToggleable())
            .doClick()

        waitForIdle()

        composeTestRule.clockTestRule.advanceClock(60)

        assertToggeableAgainstGolden("checkbox_animateToChecked")
    }

    @Test
    fun checkBoxTest_checked_animateToUnchecked() {
        composeTestRule.setMaterialContent {
            val isChecked = state { true }
            Box(wrap.semantics().testTag(wrapperTestTag)) {
                Checkbox(
                    modifier = wrap,
                    checked = isChecked.value,
                    onCheckedChange = { isChecked.value = it }
                )
            }
        }

        composeTestRule.clockTestRule.pauseClock()

        find(isToggleable())
            .doClick()

        waitForIdle()

        composeTestRule.clockTestRule.advanceClock(60)

        assertToggeableAgainstGolden("checkbox_animateToUnchecked")
    }

    private fun assertToggeableAgainstGolden(goldenName: String) {
        // TODO: replace with find(isToggeable()) after b/157687898 is fixed
        findByTag(wrapperTestTag)
            .captureToBitmap()
            .assertAgainstGolden(screenshotRule, goldenName)
    }
}
/*
 * Copyright 2020 The Android Open Source Project
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
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.test.screenshot.assertAgainstGolden
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.ui.core.testTag
import androidx.ui.foundation.Box
import androidx.ui.graphics.Color
import androidx.ui.layout.wrapContentSize
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.doClick
import androidx.ui.test.find
import androidx.ui.test.findByTag
import androidx.ui.test.isToggleable
import androidx.ui.test.waitForIdle
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class SwitchScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val screenshotRule = AndroidXScreenshotTestRule(GOLDEN_MATERIAL)

    // TODO: this test tag as well as Boxes inside testa are temporarty, remove then b/157687898
    //  is fixed
    private val wrapperTestTag = "switchWrapper"

    private val wrapperModifier = Modifier
        .wrapContentSize(Alignment.TopStart)
        .testTag(wrapperTestTag)

    @Test
    fun switchTest_checked() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = true, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("switch_checked")
    }

    @Test
    fun switchTest_checked_customColor() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = true, onCheckedChange = { }, color = Color.Red)
            }
        }
        assertToggeableAgainstGolden("switch_checked_customColor")
    }

    @Test
    fun switchTest_unchecked() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = false, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("switch_unchecked")
    }

    @Test
    fun switchTest_disabled_checked() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = true, enabled = false, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("switch_disabled_checked")
    }

    @Test
    fun switchTest_disabled_unchecked() {
        composeTestRule.setMaterialContent {
            Box(wrapperModifier) {
                Switch(checked = false, enabled = false, onCheckedChange = { })
            }
        }
        assertToggeableAgainstGolden("switch_disabled_unchecked")
    }

    @Test
    fun switchTest_unchecked_animateToChecked() {
        composeTestRule.setMaterialContent {
            val isChecked = state { false }
            Box(wrapperModifier) {
                Switch(
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

        assertToggeableAgainstGolden("switch_animateToChecked")
    }

    @Test
    fun switchTest_checked_animateToUnchecked() {
        composeTestRule.setMaterialContent {
            val isChecked = state { true }
            Box(wrapperModifier) {
                Switch(
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

        assertToggeableAgainstGolden("switch_animateToUnchecked")
    }

    private fun assertToggeableAgainstGolden(goldenName: String) {
        // TODO: replace with find(isToggeable()) after b/157687898 is fixed
        findByTag(wrapperTestTag)
            .captureToBitmap()
            .assertAgainstGolden(screenshotRule, goldenName)
    }
}
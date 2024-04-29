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

package androidx.wear.compose.material3

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.testutils.assertAgainstGolden
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.screenshot.AndroidXScreenshotTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class EdgeButtonScreenshotTest {
    @get:Rule val rule = createComposeRule()

    @get:Rule val screenshotRule = AndroidXScreenshotTestRule(SCREENSHOT_GOLDEN_PATH)

    @get:Rule val testName = TestName()

    @Test
    fun edge_button_default() =
        verifyScreenshot() {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                EdgeButton(
                    onClick = { /* Do something */ },
                    modifier = Modifier.testTag(TEST_TAG)
                ) {
                    BasicText("Text")
                }
            }
        }

    @Test
    fun edge_button_xsmall() =
        verifyScreenshot() { BasicEdgeButton(height = ButtonDefaults.EdgeButtonHeightExtraSmall) }

    @Test
    fun edge_button_small() =
        verifyScreenshot() { BasicEdgeButton(height = ButtonDefaults.EdgeButtonHeightSmall) }

    @Test
    fun edge_button_medium() =
        verifyScreenshot() { BasicEdgeButton(height = ButtonDefaults.EdgeButtonHeightMedium) }

    @Test
    fun edge_button_large() =
        verifyScreenshot() { BasicEdgeButton(height = ButtonDefaults.EdgeButtonHeightLarge) }

    @Test
    fun edge_button_disabled() =
        verifyScreenshot() {
            BasicEdgeButton(height = ButtonDefaults.EdgeButtonHeightMedium, enabled = false)
        }

    private val LONG_TEXT =
        "Lorem ipsum dolor sit amet, consectetur adipiscing elit, " +
            "sed do eiusmod tempor incididunt ut labore et dolore."

    @Test
    fun edge_button_xsmall_long_text() =
        verifyScreenshot() {
            BasicEdgeButton(height = ButtonDefaults.EdgeButtonHeightExtraSmall, text = LONG_TEXT)
        }

    @Test
    fun edge_button_large_long_text() =
        verifyScreenshot() {
            BasicEdgeButton(height = ButtonDefaults.EdgeButtonHeightLarge, text = LONG_TEXT)
        }

    @Composable
    private fun BasicEdgeButton(height: Dp, enabled: Boolean = true, text: String = "Text") {
        Box(Modifier.fillMaxSize()) {
            EdgeButton(
                onClick = { /* Do something */ },
                enabled = enabled,
                buttonHeight = height,
                modifier = Modifier.align(Alignment.BottomEnd).testTag(TEST_TAG)
            ) {
                BasicText(text)
            }
        }
    }

    private fun verifyScreenshot(
        layoutDirection: LayoutDirection = LayoutDirection.Ltr,
        content: @Composable () -> Unit
    ) {
        rule.setContentWithTheme {
            CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
                Box(
                    modifier =
                        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                ) {
                    content()
                }
            }
        }

        rule
            .onNodeWithTag(TEST_TAG)
            .captureToImage()
            .assertAgainstGolden(screenshotRule, testName.methodName)
    }
}

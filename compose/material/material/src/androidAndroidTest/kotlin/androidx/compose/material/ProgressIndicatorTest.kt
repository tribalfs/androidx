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

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertRangeInfoEquals
import androidx.compose.ui.test.assertValueEquals
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ProgressIndicatorTest {

    private val ExpectedLinearWidth = 240.dp
    private val ExpectedLinearHeight = 4.dp

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun determinateLinearProgressIndicator_Progress() {
        val tag = "linear"
        val progress = mutableStateOf(0f)

        rule.setMaterialContent {
            LinearProgressIndicator(modifier = Modifier.testTag(tag), progress = progress.value)
        }

        rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertValueEquals("0 percent")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))

        rule.runOnUiThread {
            progress.value = 0.5f
        }

        rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertValueEquals("50 percent")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.5f, 0f..1f))
    }

    @Test
    fun determinateLinearProgressIndicator_Size() {
        rule
            .setMaterialContentForSizeAssertions {
                LinearProgressIndicator(progress = 0f)
            }
            .assertWidthIsEqualTo(ExpectedLinearWidth)
            .assertHeightIsEqualTo(ExpectedLinearHeight)
    }

    @Test
    fun indeterminateLinearProgressIndicator_progress() {
        val tag = "linear"

        rule.mainClock.autoAdvance = false
        rule.setMaterialContent {
            LinearProgressIndicator(modifier = Modifier.testTag(tag))
        }

        rule.mainClock.advanceTimeByFrame() // Kick off the animation

        rule.onNodeWithTag(tag)
            .assertValueEquals("In progress")
    }

    @Test
    fun indeterminateLinearProgressIndicator_Size() {
        rule.mainClock.autoAdvance = false
        val contentToTest = rule
            .setMaterialContentForSizeAssertions {
                LinearProgressIndicator()
            }

        rule.mainClock.advanceTimeByFrame() // Kick off the animation

        contentToTest
            .assertWidthIsEqualTo(ExpectedLinearWidth)
            .assertHeightIsEqualTo(ExpectedLinearHeight)
    }

    @Test
    fun determinateCircularProgressIndicator_Progress() {
        val tag = "circular"
        val progress = mutableStateOf(0f)

        rule.setMaterialContent {
            CircularProgressIndicator(
                modifier = Modifier.testTag(tag),
                progress = progress.value
            )
        }

        rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertValueEquals("0 percent")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0f, 0f..1f))

        rule.runOnUiThread {
            progress.value = 0.5f
        }

        rule.onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertValueEquals("50 percent")
            .assertRangeInfoEquals(ProgressBarRangeInfo(0.5f, 0f..1f))
    }

    @Test
    fun determinateCircularProgressIndicator_Size() {
        rule
            .setMaterialContentForSizeAssertions {
                CircularProgressIndicator(progress = 0f)
            }
            .assertIsSquareWithSize(40.dp)
    }

    @Test
    fun indeterminateCircularProgressIndicator_progress() {
        val tag = "circular"

        rule.mainClock.autoAdvance = false
        rule.setMaterialContent {
            CircularProgressIndicator(modifier = Modifier.testTag(tag))
        }

        rule.mainClock.advanceTimeByFrame() // Kick off the animation

        rule.onNodeWithTag(tag)
            .assertValueEquals("In progress")
    }

    @Test
    fun indeterminateCircularProgressIndicator_Size() {
        rule.mainClock.autoAdvance = false
        val contentToTest = rule
            .setMaterialContentForSizeAssertions {
                CircularProgressIndicator()
            }

        rule.mainClock.advanceTimeByFrame() // Kick off the animation

        contentToTest
            .assertIsSquareWithSize(40.dp)
    }
}

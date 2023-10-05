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

package androidx.compose.material3.adaptive

import android.os.Build
import androidx.compose.testutils.assertAgainstGolden
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.test.screenshot.AndroidXScreenshotTestRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
class ThreePaneScaffoldScreenshotTest {
    @get:Rule
    val rule = createComposeRule()

    @get:Rule
    val screenshotRule = AndroidXScreenshotTestRule(GOLDEN_MATERIAL3_ADAPTIVE)

    @Test
    fun threePaneScaffold_listDetailArrangement_standard() {
        rule.setContent {
            val scaffoldDirective = calculateStandardPaneScaffoldDirective(
                calculateWindowAdaptiveInfo()
            )
            val scaffoldValue = calculateThreePaneScaffoldValue(
                scaffoldDirective.maxHorizontalPartitions
            )
            SampleThreePaneScaffold(
                scaffoldDirective,
                scaffoldValue,
                ThreePaneScaffoldDefaults.ListDetailLayoutArrangement
            )
        }

        rule.onNodeWithTag(ThreePaneScaffoldTestTag)
            .captureToImage()
            .assertAgainstGolden(screenshotRule, "threePaneScaffold_listDetail_standard")
    }

    @Test
    fun threePaneScaffold_listDetailArrangement_dense() {
        rule.setContent {
            val scaffoldDirective = calculateDensePaneScaffoldDirective(
                calculateWindowAdaptiveInfo()
            )
            val scaffoldValue = calculateThreePaneScaffoldValue(
                scaffoldDirective.maxHorizontalPartitions
            )
            SampleThreePaneScaffold(
                scaffoldDirective,
                scaffoldValue,
                ThreePaneScaffoldDefaults.ListDetailLayoutArrangement
            )
        }

        rule.onNodeWithTag(ThreePaneScaffoldTestTag)
            .captureToImage()
            .assertAgainstGolden(screenshotRule, "threePaneScaffold_listDetail_dense")
    }
}

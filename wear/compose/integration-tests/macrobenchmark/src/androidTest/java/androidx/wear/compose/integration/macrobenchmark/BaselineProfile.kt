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

package androidx.wear.compose.integration.macrobenchmark.test

import android.content.Intent
import androidx.benchmark.macro.ExperimentalBaselineProfilesApi
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.testutils.createCompilationParams
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runners.Parameterized

// This test generates a baseline profile rules file that can be parsed to produce the
// baseline-prof.txt files for the Wear Compose libraries.
// 1) Build and run debug build of androidx.wear.compose.integration.macrobenchmark-target
//    (not minified, because we need non-obsfuscated method/class names)
// 2) Run this BaselineProfile test then click 'Baseline profile results' link
// 3) Build profileparser:
//    If necessary, include it in settings.gradle:
//      includeProject(":wear:compose:integration-tests:profileparser",
//                     "wear/compose/integration-tests/profileparser",
//                     [BuildType.MAIN])
//    ./gradlew :wear:compose:integration-tests:profileparser:assemble
// 4) Run profileparser for each of wear.compose.material, wear.compose.foundation and
//    wear.compose.navigation. From <workspace>/frameworks/support:
//    java -jar
//      ../../out/androidx/wear/compose/integration-tests/profileparser/build/libs/profileparser-all.jar
//      <input-generated-file eg ./wear/compose/BaselineProfile_profile-baseline-prof.txt>
//      <library-name e.g. androidx/wear/compose/material>
//      <output-file eg ./wear/compose/compose-material/src/androidMain/baseline-prof.txt>
@LargeTest
@SdkSuppress(minSdkVersion = 29)
@OptIn(ExperimentalBaselineProfilesApi::class)
class BaselineProfile {

    @get:Rule
    val baselineRule = BaselineProfileRule()

    private lateinit var device: UiDevice
    private val ALERT_DIALOG = "alert-dialog"
    private val CONFIRMATION_DIALOG = "confirmation-dialog"
    private val BUTTONS = "buttons"
    private val CARDS = "cards"
    private val CHIPS = "chips"
    private val DIALOGS = "dialogs"
    private val PICKER = "picker"
    private val PROGRESSINDICATORS = "progressindicators"
    private val SLIDER = "slider"
    private val STEPPER = "stepper"
    private val PROGRESS_INDICATOR = "progress-indicator"
    private val PROGRESS_INDICATOR_INDETERMINATE = "progress-indicator-indeterminate"

    @Before
    fun setUp() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
    }

    @Test
    fun profile() {
        baselineRule.collectBaselineProfile(
            packageName = PACKAGE_NAME,
            profileBlock = {
                val intent = Intent()
                intent.action = ACTION
                startActivityAndWait(intent)
                testDestination(description = BUTTONS)
                testDestination(description = CARDS)
                testDestination(description = CHIPS)
                testDialogs()
                testDestination(description = PICKER)
                testProgressIndicators()
                testDestination(description = SLIDER)
                testDestination(description = STEPPER)
            }
        )
    }

    private fun testDialogs() {
        findAndClick(By.desc(DIALOGS))
        device.waitForIdle()
        testDestination(description = ALERT_DIALOG)
        testDestination(description = CONFIRMATION_DIALOG)
        device.pressBack()
        device.waitForIdle()
    }

    private fun testProgressIndicators() {
        findAndClick(By.desc(PROGRESSINDICATORS))
        device.waitForIdle()
        testDestination(description = PROGRESS_INDICATOR)
        testDestination(description = PROGRESS_INDICATOR_INDETERMINATE)
        device.pressBack()
        device.waitForIdle()
    }

    private fun testDestination(description: String) {
        findAndClick(By.desc(description))
        device.waitForIdle()
        device.pressBack()
        device.waitForIdle()
    }

    private fun findAndClick(selector: BySelector) {
        device.wait(Until.findObject(selector), 3000)
        device.findObject(selector).click()
    }

    companion object {
        private const val PACKAGE_NAME = "androidx.wear.compose.integration.macrobenchmark.target"
        private const val ACTION =
            "androidx.wear.compose.integration.macrobenchmark.target.BASELINE_ACTIVITY"

        @Parameterized.Parameters(name = "compilation={0}")
        @JvmStatic
        fun parameters() = createCompilationParams()
    }
}
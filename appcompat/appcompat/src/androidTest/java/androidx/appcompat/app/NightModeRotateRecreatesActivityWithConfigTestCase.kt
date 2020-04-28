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

package androidx.appcompat.app

import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
import androidx.appcompat.testutils.NightModeActivityTestRule
import androidx.appcompat.testutils.NightModeUtils.NightSetMode
import androidx.appcompat.testutils.NightModeUtils.assertConfigurationNightModeEquals
import androidx.appcompat.testutils.NightModeUtils.resetRotateAndWaitForRecreate
import androidx.appcompat.testutils.NightModeUtils.rotateAndWaitForRecreate
import androidx.appcompat.testutils.NightModeUtils.setNightModeAndWaitForRecreate
import androidx.test.filters.LargeTest
import org.junit.Assert.assertNotSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@RunWith(Parameterized::class)
class NightModeRotateRecreatesActivityWithConfigTestCase(private val setMode: NightSetMode) {
    @get:Rule
    val activityRule = NightModeActivityTestRule(NightModeActivity::class.java)

    @Test
    fun testRotateRecreatesActivityWithConfig() {
        // Don't run this test on SDK 26 because it has issues with setRequestedOrientation.
        if (Build.VERSION.SDK_INT == 26) {
            return
        }

        // Set local night mode to YES
        setNightModeAndWaitForRecreate(activityRule, MODE_NIGHT_YES, setMode)

        val activity = activityRule.activity
        val config = activity.resources.configuration

        // On API level 26 and below, the configuration object is going to be identical
        // across configuration changes, so we need to pull the orientation value now.
        val orientation = config.orientation

        // Assert that the current Activity is 'dark'
        assertConfigurationNightModeEquals(Configuration.UI_MODE_NIGHT_YES, config)

        rotateAndWaitForRecreate(activityRule.activity)

        // Assert that we got a new activity.
        val activity2 = activityRule.activity
        val config2 = activity2.resources.configuration

        // And assert that we have a different 'dark' Activity in a new orientation
        assertNotSame(activity, activity2)
        assertNotSame(orientation, config2.orientation)
        assertConfigurationNightModeEquals(Configuration.UI_MODE_NIGHT_YES, config2)

        // Reset the requested orientation and wait for it to apply.
        resetRotateAndWaitForRecreate(activityRule.activity)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters
        fun data() = if (Build.VERSION.SDK_INT >= 17) {
            listOf(NightSetMode.DEFAULT, NightSetMode.LOCAL)
        } else {
            listOf(NightSetMode.DEFAULT)
        }
    }
}

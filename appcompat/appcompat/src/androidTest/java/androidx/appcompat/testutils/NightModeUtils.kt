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

package androidx.appcompat.testutils

import org.junit.Assert.assertEquals

import android.app.UiModeManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.app.AppCompatDelegate.NightMode
import androidx.lifecycle.Lifecycle
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.testutils.LifecycleOwnerUtils

object NightModeUtils {
    private const val LOG_TAG = "NightModeUtils"

    enum class NightSetMode {
        /**
         * Set the night mode using [AppCompatDelegate.setDefaultNightMode]
         */
        DEFAULT,

        /**
         * Set the night mode using [AppCompatDelegate.setLocalNightMode]
         */
        LOCAL
    }

    fun assertConfigurationNightModeEquals(
        expectedNightMode: Int,
        context: Context
    ) {
        assertConfigurationNightModeEquals(
            expectedNightMode,
            context.resources.configuration
        )
    }

    fun assertConfigurationNightModeEquals(
        expectedNightMode: Int,
        configuration: Configuration
    ) {
        assertEquals(
            expectedNightMode.toLong(),
            (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK).toLong()
        )
    }

    fun <T : AppCompatActivity> setNightModeAndWait(
        activityRule: ActivityTestRule<T>,
        @NightMode nightMode: Int,
        setMode: NightSetMode
    ) {
        setNightModeAndWait(activityRule.activity, activityRule, nightMode, setMode)
    }

    fun <T : AppCompatActivity> setNightModeAndWait(
        activity: AppCompatActivity?,
        activityRule: ActivityTestRule<T>,
        @NightMode nightMode: Int,
        setMode: NightSetMode
    ) {
        Log.d(
            LOG_TAG, "setNightModeAndWait on Activity: " + activity +
                    " to mode: " + nightMode +
                    " using set mode: " + setMode
        )

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        activityRule.runOnUiThread { setNightMode(nightMode, activity, setMode) }
        instrumentation.waitForIdleSync()
    }

    fun <T : AppCompatActivity> setNightModeAndWaitForRecreate(
        activityRule: ActivityTestRule<T>,
        @NightMode nightMode: Int,
        setMode: NightSetMode
    ) {
        val activity = activityRule.activity

        Log.d(
            LOG_TAG, "setNightModeAndWaitForRecreate on Activity: " + activity +
                    " to mode: " + nightMode +
                    " using set mode: " + setMode
        )

        // Wait for the Activity to be resumed and visible
        LifecycleOwnerUtils.waitUntilState(activity, activityRule, Lifecycle.State.RESUMED)

        // Now perform night mode change wait for the Activity to be recreated
        LifecycleOwnerUtils.waitForRecreation(activity, activityRule) {
            setNightMode(nightMode, activity, setMode)
        }
    }

    fun <T : AppCompatActivity> rotateAndWaitForRecreate(
        activityRule: ActivityTestRule<T>
    ) {
        Log.e(LOG_TAG, "request rotate")
        val activity = activityRule.activity
        LifecycleOwnerUtils.waitUntilState(activity, activityRule, Lifecycle.State.RESUMED)

        // Now perform rotation and wait for the Activity to be recreated
        LifecycleOwnerUtils.waitForRecreation(activity, activityRule) {
            Log.e(LOG_TAG, "request rotate on ui thread")
            if (activity.resources.configuration.orientation ==
                Configuration.ORIENTATION_LANDSCAPE) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            }
            Log.e(LOG_TAG, "request rotate to " + activity.requestedOrientation)
        }
    }

    fun <T : AppCompatActivity> resetRotateAndWaitForRecreate(
        activityRule: ActivityTestRule<T>
    ) {
        val activity = activityRule.activity
        LifecycleOwnerUtils.waitUntilState(activity, activityRule, Lifecycle.State.RESUMED)

        LifecycleOwnerUtils.waitForRecreation(activity, activityRule) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    fun isSystemNightThemeEnabled(context: Context): Boolean {
        val manager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return manager.nightMode == UiModeManager.MODE_NIGHT_YES
    }

    fun setNightMode(
        @NightMode nightMode: Int,
        activity: AppCompatActivity?,
        setMode: NightSetMode
    ) {
        if (setMode == NightSetMode.DEFAULT) {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        } else {
            activity!!.delegate.localNightMode = nightMode
        }
    }
}

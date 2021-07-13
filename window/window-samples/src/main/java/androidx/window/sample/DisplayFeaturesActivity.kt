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

package androidx.window.sample

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.window.WindowInfoRepo
import androidx.window.WindowLayoutInfo
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Demo activity that shows all display features and current device state on the screen. */
class DisplayFeaturesActivity : AppCompatActivity() {

    private val stateLog: StringBuilder = StringBuilder()

    private val displayFeatureViews = ArrayList<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_display_features)

        val windowInfoRepo = WindowInfoRepo.create(this)

        lifecycleScope.launch(Dispatchers.Main) {
            // The block passed to repeatOnLifecycle is executed when the lifecycle
            // is at least STARTED and is cancelled when the lifecycle is STOPPED.
            // It automatically restarts the block when the lifecycle is STARTED again.
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Safely collect from windowInfoRepo when the lifecycle is STARTED
                // and stops collection when the lifecycle is STOPPED
                windowInfoRepo.windowLayoutInfo
                    .collect { newLayoutInfo ->
                        // New posture information
                        updateStateLog(newLayoutInfo)
                        updateCurrentState(newLayoutInfo)
                    }
            }
        }
        stateLog.clear()
        stateLog.append(getString(R.string.stateUpdateLog)).append("\n")
    }

    /** Updates the device state and display feature positions. */
    internal fun updateCurrentState(windowLayoutInfo: WindowLayoutInfo) {
        // Cleanup previously added feature views
        val rootLayout = findViewById<FrameLayout>(R.id.featureContainerLayout)
        for (featureView in displayFeatureViews) {
            rootLayout.removeView(featureView)
        }
        displayFeatureViews.clear()

        // Update the UI with the current state
        val stateStringBuilder = StringBuilder()

        stateStringBuilder.append(getString(R.string.windowLayout))
            .append(": ")

        // Add views that represent display features
        for (displayFeature in windowLayoutInfo.displayFeatures) {
            val lp = getLayoutParamsForFeatureInFrameLayout(displayFeature, rootLayout)
                ?: continue

            // Make sure that zero-wide and zero-high features are still shown
            if (lp.width == 0) {
                lp.width = 1
            }
            if (lp.height == 0) {
                lp.height = 1
            }

            val featureView = View(this)
            val color = getColor(R.color.colorFeatureFold)
            featureView.foreground = ColorDrawable(color)

            rootLayout.addView(featureView, lp)
            featureView.id = View.generateViewId()

            displayFeatureViews.add(featureView)
        }

        findViewById<TextView>(R.id.currentState).text = stateStringBuilder.toString()
    }

    /** Adds the current state to the text log of changes on screen. */
    internal fun updateStateLog(info: Any) {
        stateLog.append(getCurrentTimeString())
            .append(" ")
            .append(info)
            .append("\n")
        findViewById<TextView>(R.id.stateUpdateLog).text = stateLog
    }

    private fun getCurrentTimeString(): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val currentDate = sdf.format(Date())
        return currentDate.toString()
    }
}

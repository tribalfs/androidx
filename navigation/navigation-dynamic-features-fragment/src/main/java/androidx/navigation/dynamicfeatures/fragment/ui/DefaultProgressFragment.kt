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

package androidx.navigation.dynamicfeatures.fragment.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.navigation.dynamicfeatures.fragment.R
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode

/**
 * The default [androidx.fragment.app.Fragment] to display during installation progress.
 *
 * This `Fragment` provides a default UI and handles split install state changes so you don't
 * have to deal with this.
 *
 * To create a custom progress fragment, extend [AbstractProgressFragment].
 */
class DefaultProgressFragment :
    AbstractProgressFragment(R.layout.dynamic_feature_install_fragment) {

    internal companion object {
        private const val PROGRESS_MAX = 100
    }

    private lateinit var title: TextView
    private lateinit var moduleName: TextView
    private lateinit var progressBar: ProgressBar

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        with(view) {
            title = findViewById(R.id.progress_title)
            moduleName = findViewById(R.id.module_name)
            progressBar = findViewById<ProgressBar>(R.id.installation_progress)
            setActivityIcon(findViewById(R.id.progress_icon))
        }
    }

    private fun setActivityIcon(activityIcon: ImageView) {
        with(requireContext().packageManager) {
            val icon = try {
                getActivityIcon(ComponentName(requireContext(), requireActivity().javaClass))
            } catch (e: PackageManager.NameNotFoundException) {
                defaultActivityIcon
            }
            activityIcon.setImageDrawable(icon)
        }
    }

    override fun onProgress(status: Int, bytesDownloaded: Long, bytesTotal: Long) {
        with(progressBar) {
            visibility = View.VISIBLE
            if (bytesTotal == 0L) {
                isIndeterminate = true
            } else {
                progress = (PROGRESS_MAX * bytesDownloaded / bytesTotal).toInt()
                isIndeterminate = false
            }
        }
    }

    override fun onCancelled() {
        displayErrorState(R.string.installation_cancelled)
    }

    override fun onFailed(@SplitInstallErrorCode errorCode: Int) {
        displayErrorState(R.string.installation_failed)
    }

    private fun displayErrorState(@StringRes text: Int) {
        title.setText(text)
        progressBar.visibility = View.INVISIBLE
    }
}

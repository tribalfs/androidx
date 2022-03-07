/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.integration.macrobenchmark.target

import android.app.Activity
import android.os.Bundle
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.doOnPreDraw

class FullyDrawnStartupActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TextBlock("Compose Macrobenchmark Target")
        }
    }

    @Composable
    fun ReportFullyDrawn(
        text: String
    ) {
        val localView: View = LocalView.current
        LaunchedEffect(text) {
            val activity = localView.context as? Activity
            if (activity != null) {
                localView.doOnPreDraw {
                    activity.reportFullyDrawn()
                }
            }
        }
    }

    @Composable
    fun TextBlock(
        text: String,
    ) {
        Text(text)
        ReportFullyDrawn(text)
    }
}

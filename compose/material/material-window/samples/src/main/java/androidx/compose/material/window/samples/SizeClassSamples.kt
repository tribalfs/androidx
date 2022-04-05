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

package androidx.compose.material.window.samples

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.Sampled
import androidx.compose.material.window.ExperimentalMaterialWindowApi
import androidx.compose.material.window.WidthSizeClass
import androidx.compose.material.window.calculateSizeClass
import androidx.compose.runtime.Composable

@OptIn(ExperimentalMaterialWindowApi::class)
@Sampled
fun AndroidSizeClassSample() {
    class MyActivity : ComponentActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            setContent {
                // Calculate the size class for the activity's current window. If the window size
                // changes, for example when the device is rotated, the value returned by
                // calculateSizeClass will also change.
                val sizeClass = calculateSizeClass()
                // Perform logic on the size class to decide whether to show the top app bar.
                val showTopAppBar = sizeClass.width != WidthSizeClass.Compact

                // MyScreen knows nothing about window sizes, and performs logic based on a Boolean
                // flag.
                MyScreen(showTopAppBar = showTopAppBar)
            }
        }
    }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun MyScreen(showTopAppBar: Boolean) {}

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

package androidx.compose.ui.samples

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.Sampled
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView

@Suppress("SetTextI18n")
@Sampled
@Composable
fun AndroidViewSample() {
    // Compose a TextView.
    AndroidView({ context -> TextView(context).apply { text = "This is a TextView" } })
    // Compose a View and update its size based on state. Note the modifiers.
    var size by remember { mutableStateOf(20) }
    AndroidView(::View, Modifier.clickable { size += 20 }.background(Color.Blue)) { view ->
        view.layoutParams = ViewGroup.LayoutParams(size, size)
    }
}

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

package androidx.ui.core.samples

import android.view.View
import androidx.annotation.Sampled
import androidx.compose.Composable
import android.widget.Button
import android.widget.LinearLayout
import androidx.ui.viewinterop.emitView

@Sampled
@Composable
fun EmitViewButtonSample() {
    @Composable
    fun Button(
        text: String,
        onClick: (() -> Unit)? = null
    ) {
        emitView<Button>(::Button) {
            it.text = text
            it.setOnClickListener(onClick?.let { View.OnClickListener { it() } })
        }
    }
}

@Sampled
@Composable
fun EmitViewLinearLayoutSample() {
    @Composable
    fun LinearLayout(
        orientation: Int = android.widget.LinearLayout.VERTICAL,
        children: @Composable () -> Unit
    ) {
        emitView<LinearLayout>(::LinearLayout, { it.orientation = orientation }) {
            children()
        }
    }
}
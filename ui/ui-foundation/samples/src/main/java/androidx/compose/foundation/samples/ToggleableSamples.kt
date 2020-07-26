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

package androidx.compose.foundation.samples

import androidx.annotation.Sampled
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.state
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Text
import androidx.compose.foundation.selection.ToggleableState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.triStateToggleable

@Sampled
@Composable
fun ToggleableSample() {
    var checked by state { false }
    // content that you want to make toggleable
    Text(
        modifier = Modifier.toggleable(value = checked, onValueChange = { checked = it }),
        text = checked.toString()
    )
}

@Sampled
@Composable
fun TriStateToggleableSample() {
    var checked by state { ToggleableState.Indeterminate }
    // content that you want to make toggleable
    Text(
        modifier = Modifier.triStateToggleable(
            state = checked,
            onClick = {
                checked =
                    if (checked == ToggleableState.On) ToggleableState.Off else ToggleableState.On
            }
        ),
        text = checked.toString())
}
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

package androidx.compose.material3.samples

import androidx.annotation.Sampled
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Sampled
@Composable
fun IconButtonSample() {
    IconButton(onClick = { /* doSomething() */ }) {
        Icon(Icons.Outlined.Lock, contentDescription = "Localized description")
    }
}

@Sampled
@Composable
fun IconToggleButtonSample() {
    var checked by remember { mutableStateOf(false) }
    IconToggleButton(checked = checked, onCheckedChange = { checked = it }) {
        if (checked) {
            Icon(Icons.Filled.Lock, contentDescription = "Localized description")
        } else {
            Icon(Icons.Outlined.Lock, contentDescription = "Localized description")
        }
    }
}

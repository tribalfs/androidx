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

package androidx.compose.material.demos

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.samples.MaterialThemeSample
import androidx.compose.material.samples.ThemeColorSample
import androidx.compose.material.samples.ThemeTextStyleSample
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun MaterialThemeDemo() {
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceAround) {
        MaterialThemeSample()
        ThemeTextStyleSample()
        ThemeColorSample()
    }
}

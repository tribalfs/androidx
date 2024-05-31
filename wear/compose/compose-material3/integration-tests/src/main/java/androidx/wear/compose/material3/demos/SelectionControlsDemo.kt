/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.wear.compose.material3.demos

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.SelectableButton
import androidx.wear.compose.material3.Text

@Composable
fun RadioButtonDemo() {
    var selectedIndex by remember { mutableIntStateOf(0) }
    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item { ListHeader { Text(text = "Radio Button") } }
        item {
            RadioButtonDemo(
                selected = selectedIndex == 0,
                onSelected = { selectedIndex = 0 },
                enabled = true
            )
        }
        item {
            RadioButtonDemo(
                selected = selectedIndex == 1,
                onSelected = { selectedIndex = 1 },
                enabled = true
            )
        }
        item { ListHeader { Text(text = "Disabled Radio Button", textAlign = TextAlign.Center) } }
        item { RadioButtonDemo(selected = false, enabled = false) }
        item { RadioButtonDemo(selected = true, enabled = false) }
    }
}

@Composable
private fun RadioButtonDemo(selected: Boolean, enabled: Boolean, onSelected: () -> Unit = {}) {
    SelectableButton(
        label = { Text("Primary label", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        selected = selected,
        onSelect = onSelected,
        enabled = enabled,
    )
}

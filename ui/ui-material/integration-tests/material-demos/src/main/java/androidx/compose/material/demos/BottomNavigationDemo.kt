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

package androidx.compose.material.demos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.state
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.compose.foundation.Text
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.preferredHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.RadioButton
import androidx.compose.material.samples.BottomNavigationSample
import androidx.compose.material.samples.BottomNavigationWithOnlySelectedLabelsSample
import androidx.compose.ui.unit.dp

@Composable
fun BottomNavigationDemo() {
    var alwaysShowLabels by state { false }
    Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.Bottom) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .preferredHeight(56.dp)
                .selectable(
                    selected = !alwaysShowLabels,
                    onClick = { alwaysShowLabels = false }
                ),
            verticalGravity = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = !alwaysShowLabels,
                onClick = { alwaysShowLabels = false }
            )
            Spacer(Modifier.width(16.dp))
            Text("Only show labels when selected")
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .preferredHeight(56.dp)
                .selectable(
                    selected = alwaysShowLabels,
                    onClick = { alwaysShowLabels = true }
                ),
            verticalGravity = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = alwaysShowLabels,
                onClick = { alwaysShowLabels = true }
            )
            Spacer(Modifier.width(16.dp))
            Text("Always show labels")
        }

        Spacer(Modifier.preferredHeight(50.dp))

        if (alwaysShowLabels) {
            BottomNavigationSample()
        } else {
            BottomNavigationWithOnlySelectedLabelsSample()
        }
    }
}

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

package androidx.ui.material.demos

import androidx.compose.Composable
import androidx.compose.state
import androidx.ui.core.Alignment
import androidx.ui.core.Modifier
import androidx.ui.foundation.Text
import androidx.ui.foundation.VerticalScroller
import androidx.ui.graphics.Color
import androidx.ui.layout.Spacer
import androidx.ui.layout.height
import androidx.ui.layout.preferredHeight
import androidx.ui.material.Button
import androidx.ui.material.samples.FancyIndicatorContainerTabs
import androidx.ui.material.samples.FancyIndicatorTabs
import androidx.ui.material.samples.FancyTabs
import androidx.ui.material.samples.IconTabs
import androidx.ui.material.samples.ScrollingFancyIndicatorContainerTabs
import androidx.ui.material.samples.ScrollingTextTabs
import androidx.ui.material.samples.TextAndIconTabs
import androidx.ui.material.samples.TextTabs
import androidx.ui.unit.dp

@Composable
fun TabDemo() {
    VerticalScroller {
        val showingSimple = state { true }
        val buttonText = "Show ${if (showingSimple.value) "custom" else "simple"} tabs"

        Spacer(Modifier.height(24.dp))
        if (showingSimple.value) {
            TextTabs()
            Spacer(Modifier.height(24.dp))
            IconTabs()
            Spacer(Modifier.height(24.dp))
            TextAndIconTabs()
            Spacer(Modifier.height(24.dp))
            ScrollingTextTabs()
        } else {
            FancyTabs()
            Spacer(Modifier.height(24.dp))
            FancyIndicatorTabs()
            Spacer(Modifier.height(24.dp))
            FancyIndicatorContainerTabs()
            Spacer(Modifier.height(24.dp))
            ScrollingFancyIndicatorContainerTabs()
        }
        Spacer(Modifier.height(24.dp))
        Button(
            modifier = Modifier.gravity(Alignment.CenterHorizontally),
            onClick = {
                showingSimple.value = !showingSimple.value
            },
            backgroundColor = Color.Cyan
        ) {
            Text(buttonText)
        }
        Spacer(Modifier.preferredHeight(50.dp))
    }
}

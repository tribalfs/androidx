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

package androidx.compose.foundation.layout.demos

import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ltr
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.foundation.layout.rtl
import androidx.compose.ui.unit.dp

@Composable
fun ComplexLayoutDemo() {
    Stack(
        Modifier.rtl
            .background(color = Color.Magenta)
        .padding(start = 10.dp)
        .ltr
        .padding(start = 10.dp)
        .preferredSize(150.dp)
        .rtl
        .background(Color.Gray)
        .padding(start = 10.dp)
        .ltr
        .padding(start = 10.dp)
        .background(Color.Blue)
        .rtl
    ) {
        Stack(Modifier
            .padding(start = 10.dp)
            .ltr
            .padding(start = 10.dp)
            .fillMaxSize().background(Color.Green)) {}
    }
}

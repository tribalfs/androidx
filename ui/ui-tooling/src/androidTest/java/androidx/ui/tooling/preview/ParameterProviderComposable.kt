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

package androidx.ui.tooling.preview

import androidx.compose.Composable
import androidx.compose.foundation.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.material.Button
import androidx.compose.material.ColorPalette
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface

@Preview
@Composable
fun OneStringParameter(parameter: String) {
    Surface(color = Color.Red) {
        Text(parameter)
    }
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
@Preview
@Composable
fun OneIntParameter(parameter: Integer) {
    Surface(color = Color.Red) {
        Text("$parameter")
    }
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
@Preview
@Composable
fun ColorPaletteParameter(parameter: ColorPalette) {
    MaterialTheme(colors = parameter) {
        Button(onClick = {}) {
            Text("Hello colors")
        }
    }
}
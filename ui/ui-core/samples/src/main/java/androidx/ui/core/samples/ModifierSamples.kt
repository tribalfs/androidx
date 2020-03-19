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

@file:Suppress("unused")

package androidx.ui.core.samples

import androidx.annotation.Sampled
import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.foundation.Text
import androidx.ui.layout.Column
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.Row
import androidx.ui.material.Button
import androidx.ui.unit.dp

@Sampled
@Composable
fun ModifierParameterSample() {
    @Composable
    fun PaddedColumn(modifier: Modifier = Modifier.None) {
        Column(modifier + LayoutPadding(10.dp)) {
            // ...
        }
    }
}

@Sampled
@Composable
fun SubcomponentModifierSample() {
    @Composable
    fun ButtonBar(
        onOk: () -> Unit,
        onCancel: () -> Unit,
        modifier: Modifier = Modifier.None,
        buttonModifier: Modifier = Modifier.None
    ) {
        Row(modifier) {
            Button(onCancel, buttonModifier) {
                Text("Cancel")
            }
            Button(onOk, buttonModifier) {
                Text("Ok")
            }
        }
    }
}

private val defaultFooModifier = Modifier.None

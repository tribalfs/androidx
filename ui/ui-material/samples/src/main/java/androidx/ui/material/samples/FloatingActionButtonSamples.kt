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

package androidx.ui.material.samples

import androidx.annotation.Sampled
import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.foundation.Icon
import androidx.ui.foundation.Text
import androidx.ui.layout.fillMaxWidth
import androidx.ui.material.ExtendedFloatingActionButton
import androidx.ui.material.FloatingActionButton
import androidx.ui.material.icons.Icons
import androidx.ui.material.icons.filled.Favorite

@Sampled
@Composable
fun SimpleFab() {
    FloatingActionButton(onClick = { /*do something*/ }) {
        Icon(Icons.Filled.Favorite)
    }
}

@Composable
fun SimpleExtendedFabNoIcon() {
    ExtendedFloatingActionButton(
        text = { Text("EXTENDED") },
        onClick = {}
    )
}

@Sampled
@Composable
fun SimpleExtendedFabWithIcon() {
    ExtendedFloatingActionButton(
        icon = { Icon(Icons.Filled.Favorite) },
        text = { Text("ADD TO BASKET") },
        onClick = { /*do something*/ }
    )
}

@Sampled
@Composable
fun FluidExtendedFab() {
    ExtendedFloatingActionButton(
        icon = { Icon(Icons.Filled.Favorite) },
        text = { Text("FLUID FAB") },
        onClick = { /*do something*/ },
        modifier = Modifier.fillMaxWidth()
    )
}
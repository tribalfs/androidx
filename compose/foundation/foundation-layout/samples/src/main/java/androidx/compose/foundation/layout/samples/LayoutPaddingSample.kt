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

package androidx.compose.foundation.layout.samples

import androidx.annotation.Sampled
import androidx.compose.foundation.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Stack
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Sampled
@Composable
fun PaddingModifier() {
    Stack(Modifier.background(color = Color.Gray)) {
        Box(
            Modifier.padding(start = 20.dp, top = 30.dp, end = 20.dp, bottom = 30.dp)
                .preferredSize(50.dp),
            backgroundColor = Color.Blue
        )
    }
}

@Sampled
@Composable
fun SymmetricPaddingModifier() {
    Stack(Modifier.background(color = Color.Gray)) {
        Box(
            Modifier.padding(horizontal = 20.dp, vertical = 30.dp).preferredSize(50.dp),
            backgroundColor = Color.Blue
        )
    }
}

@Sampled
@Composable
fun PaddingAllModifier() {
    Stack(Modifier.background(color = Color.Gray)) {
        Box(Modifier.padding(all = 20.dp).preferredSize(50.dp), backgroundColor = Color.Blue)
    }
}

@Sampled
@Composable
fun PaddingValuesModifier() {
    val innerPadding = PaddingValues(top = 10.dp, start = 15.dp)
    Stack(Modifier.background(color = Color.Gray)) {
        Box(Modifier.padding(innerPadding).preferredSize(50.dp), backgroundColor = Color.Blue)
    }
}

@Sampled
@Composable
fun AbsolutePaddingModifier() {
    Stack(Modifier.background(color = Color.Gray)) {
        Box(
            Modifier.absolutePadding(left = 20.dp, top = 30.dp, right = 20.dp, bottom = 30.dp)
                .preferredSize(50.dp),
            backgroundColor = Color.Blue
        )
    }
}
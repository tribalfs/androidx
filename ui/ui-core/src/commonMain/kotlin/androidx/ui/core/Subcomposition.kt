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
package androidx.ui.core

import androidx.compose.Composable
import androidx.compose.Composition
import androidx.compose.CompositionReference
import androidx.compose.ExperimentalComposeApi
import androidx.compose.Recomposer
import androidx.ui.util.annotation.MainThread

@OptIn(ExperimentalLayoutNodeApi::class)
internal expect fun actualSubcomposeInto(
    container: LayoutNode,
    recomposer: Recomposer,
    parent: CompositionReference?,
    composable: @Composable () -> Unit
): Composition

@OptIn(ExperimentalComposeApi::class, ExperimentalLayoutNodeApi::class)
@MainThread
fun subcomposeInto(
    container: LayoutNode,
    recomposer: Recomposer,
    parent: CompositionReference? = null,
    composable: @Composable () -> Unit
): Composition = actualSubcomposeInto(container, recomposer, parent, composable)
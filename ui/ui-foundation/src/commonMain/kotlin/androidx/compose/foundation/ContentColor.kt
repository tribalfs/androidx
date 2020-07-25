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

package androidx.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ambientOf
import androidx.compose.ui.graphics.Color

/**
 * Returns the preferred content color at the call site's position in the hierarchy.
 *
 * This color should be used for any typography / iconography, to ensure that the color of these
 * adjusts when the background color changes. For example, on a dark background, text should be
 * light, and on a light background, text should be dark.
 *
 * @return the preferred content color specified by a parent, defaulting to [Color.Black] if
 * unspecified.
 */
@Composable
fun contentColor() = ContentColorAmbient.current

/**
 * Ambient containing the preferred content color for a given position in the hierarchy.
 *
 * See [contentColor] for a more descriptive alias to consume the value from this ambient.
 */
val ContentColorAmbient = ambientOf { Color.Black }

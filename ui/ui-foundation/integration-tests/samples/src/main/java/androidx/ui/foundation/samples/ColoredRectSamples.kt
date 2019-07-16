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

package androidx.ui.foundation.samples

import androidx.annotation.Sampled
import androidx.compose.Composable
import androidx.compose.composer
import androidx.ui.core.dp
import androidx.ui.core.vectorgraphics.SolidColor
import androidx.ui.foundation.ColoredRect
import androidx.ui.graphics.Color

@Sampled
@Composable
fun ColoredRectColorSample() {
    ColoredRect(Color.Cyan, width = 20.dp, height = 20.dp)
}

@Sampled
@Composable
fun ColoredRectBrushSample() {
    ColoredRect(
        brush = SolidColor(Color.Fuchsia),
        width = 20.dp,
        height = 20.dp
    )
}
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

package androidx.ui.material

import androidx.ui.baseui.ColoredRect
import androidx.ui.core.Dp
import androidx.ui.core.dp
import androidx.ui.layout.Padding
import androidx.ui.painting.Color
import androidx.compose.Composable
import androidx.compose.composer
import androidx.compose.unaryPlus

/**
 * A divider is a thin line that groups content in lists and layouts
 *
 * @param color color of the divider line, [MaterialColors.onSurface] will be used by default
 * @param height height of the divider line, 1 dp is used by default
 * @param indent left offset of this line, no offset by default
 */
@Composable
fun Divider(
    color: Color? = null,
    height: Dp = 1.dp,
    indent: Dp = 0.dp
) {
    val dividerColor = +color.orFromTheme { onSurface }
    Padding(left = indent) {
        ColoredRect(height = height, color = dividerColor)
    }
}
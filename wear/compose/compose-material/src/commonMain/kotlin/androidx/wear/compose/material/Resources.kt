/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.wear.compose.material

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

internal enum class ImageResources {
    CircularVignetteBottom,
    CircularVignetteTop,
    RectangularVignetteBottom,
    RectangularVignetteTop,
}

@Composable
internal expect fun imageResource(image: ImageResources): Painter

@Composable
internal expect fun isRoundDevice(): Boolean

@Composable
internal expect fun is24HourFormat(): Boolean

internal expect fun currentTimeMillis(): Long

@Composable
internal expect fun isLeftyModeEnabled(): Boolean

@Composable
internal expect fun screenHeightDp(): Int

@Composable
internal expect fun screenWidthDp(): Int
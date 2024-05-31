/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.wear.compose.ui.tooling.preview

import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.tooling.preview.devices.WearDevices

/**
 * [WearPreviewSquare] is a custom preview annotation for displaying Wear composables on a square
 * Wear screen ([WearDevices.SQUARE]).
 *
 * @sample androidx.wear.compose.material.samples.ButtonWithIconPreview
 *
 * @see [WearDevices.SQUARE]
 */
@Preview(
    device = WearDevices.SQUARE,
    backgroundColor = 0xff000000,
    showBackground = true,
    group = "Devices - Small Square",
    showSystemUi = true
)
public annotation class WearPreviewSquare()

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

package androidx.ui.baseui

import androidx.ui.core.Draw
import androidx.ui.engine.geometry.Offset
import androidx.ui.layout.Container
import androidx.ui.painting.Image
import androidx.ui.painting.Paint
import androidx.compose.Composable
import androidx.compose.composer
import androidx.ui.core.WithDensity

// TODO(Andrey) Temporary. Should be replaced with our proper Image component when it available
@Composable
fun SimpleImage(
    image: Image
) {
    // TODO b132071873: WithDensity should be able to use the DSL syntax
    WithDensity(block = {
        Container(width = image.width.toDp(), height = image.height.toDp()) {
            Draw { canvas, _ ->
                canvas.drawImage(image, Offset.zero, Paint())
            }
        }
    })
}
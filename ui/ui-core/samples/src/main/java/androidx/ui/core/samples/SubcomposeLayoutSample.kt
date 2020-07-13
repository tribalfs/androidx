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

package androidx.ui.core.samples

import androidx.annotation.Sampled
import androidx.compose.Composable
import androidx.ui.core.ExperimentalSubcomposeLayoutApi
import androidx.ui.core.SubcomposeLayout
import androidx.ui.unit.IntSize

@Sampled
@Composable
@OptIn(ExperimentalSubcomposeLayoutApi::class)
fun SubcomposeLayoutSample(
    mainContent: @Composable () -> Unit,
    dependentContent: @Composable (IntSize) -> Unit
) {
    SubcomposeLayout<SlotsEnum> { constraints ->
        val mainPlaceables = subcompose(SlotsEnum.Main, mainContent).map {
            it.measure(constraints)
        }
        val maxSize = mainPlaceables.fold(IntSize.Zero) { currentMax, placeable ->
            IntSize(
                width = maxOf(currentMax.width, placeable.width),
                height = maxOf(currentMax.height, placeable.height)
            )
        }
        layout(maxSize.width, maxSize.height) {
            mainPlaceables.forEach { it.place(0, 0) }
            subcompose(SlotsEnum.Dependent) {
                dependentContent(maxSize)
            }.forEach {
                it.measure(constraints).place(0, 0)
            }
        }
    }
}

enum class SlotsEnum {
    Main,
    Dependent
}

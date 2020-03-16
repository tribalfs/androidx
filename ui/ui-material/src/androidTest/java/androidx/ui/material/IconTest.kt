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
package androidx.ui.material

import androidx.test.filters.LargeTest
import androidx.ui.core.DensityAmbient
import androidx.ui.foundation.Icon
import androidx.ui.graphics.Color
import androidx.ui.graphics.ImageAsset
import androidx.ui.graphics.painter.ColorPainter
import androidx.ui.graphics.painter.ImagePainter
import androidx.ui.graphics.vector.VectorAssetBuilder
import androidx.ui.material.icons.Icons
import androidx.ui.material.icons.filled.Menu
import androidx.ui.test.createComposeRule
import androidx.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
class IconTest {
    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun vector_materialIconSize_dimensions() {
        val width = 24.dp
        val height = 24.dp
        val vector = Icons.Filled.Menu
        composeTestRule
            .setMaterialContentAndCollectSizes {
                Icon(vector)
            }
            .assertWidthEqualsTo(width)
            .assertHeightEqualsTo(height)
    }

    @Test
    fun vector_customIconSize_dimensions() {
        val width = 35.dp
        val height = 83.dp
        val vector = VectorAssetBuilder(defaultWidth = width, defaultHeight = height,
            viewportWidth = width.value, viewportHeight = height.value).build()
        composeTestRule
            .setMaterialContentAndCollectSizes {
                Icon(vector)
            }
            .assertWidthEqualsTo(width)
            .assertHeightEqualsTo(height)
    }

    @Test
    fun image_noIntrinsicSize_dimensions() {
        val width = 24.dp
        val height = 24.dp
        composeTestRule
            .setMaterialContentAndCollectSizes {
                val dummyImage = with(DensityAmbient.current) {
                    ImageAsset(width.toIntPx().value, height.toIntPx().value)
                }

                Icon(dummyImage)
            }
            .assertWidthEqualsTo(width)
            .assertHeightEqualsTo(height)
    }

    @Test
    fun image_withIntrinsicSize_dimensions() {
        val width = 35.dp
        val height = 83.dp

        composeTestRule
            .setMaterialContentAndCollectSizes {
                val dummyImage = with(DensityAmbient.current) {
                    ImageAsset(width.toIntPx().value, height.toIntPx().value)
                }

                Icon(dummyImage)
            }
            .assertWidthEqualsTo(width)
            .assertHeightEqualsTo(height)
    }

    @Test
    fun painter_noIntrinsicSize_dimensions() {
        val width = 24.dp
        val height = 24.dp
        val painter = ColorPainter(Color.Red)
        composeTestRule
            .setMaterialContentAndCollectSizes {
                Icon(painter)
            }
            .assertWidthEqualsTo(width)
            .assertHeightEqualsTo(height)
    }

    @Test
    fun painter_withIntrinsicSize_dimensions() {
        val width = 35.dp
        val height = 83.dp

        composeTestRule
            .setMaterialContentAndCollectSizes {
                val dummyImage = with(DensityAmbient.current) {
                    ImageAsset(width.toIntPx().value, height.toIntPx().value)
                }

                val imagePainter = ImagePainter(dummyImage)
                Icon(imagePainter)
            }
            .assertWidthEqualsTo(width)
            .assertHeightEqualsTo(height)
    }
}

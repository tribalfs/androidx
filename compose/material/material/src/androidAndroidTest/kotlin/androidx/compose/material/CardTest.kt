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

package androidx.compose.material

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.testutils.assertShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class CardTest {

    @get:Rule
    val rule = createComposeRule()

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    @LargeTest
    fun shapeAndColorFromThemeIsUsed() {
        val shape = CutCornerShape(8.dp)
        val background = Color.Yellow
        var cardColor = Color.Transparent
        rule.setMaterialContent {
            Surface(color = background) {
                Box {
                    cardColor = MaterialTheme.colors.surface
                    CompositionLocalProvider(LocalShapes provides Shapes(medium = shape)) {
                        Card(
                            modifier = Modifier
                                .semantics(mergeDescendants = true) {}
                                .testTag("card"),
                            elevation = 0.dp
                        ) {
                            Box(Modifier.size(50.dp, 50.dp))
                        }
                    }
                }
            }
        }

        rule.onNodeWithTag("card")
            .captureToImage()
            .assertShape(
                density = rule.density,
                shape = shape,
                shapeColor = cardColor,
                backgroundColor = background,
                shapeOverlapPixelCount = with(rule.density) { 1.dp.toPx() }
            )
    }
}

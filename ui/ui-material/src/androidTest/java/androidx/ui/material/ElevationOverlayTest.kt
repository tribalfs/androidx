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

import android.os.Build
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.ui.core.Modifier
import androidx.ui.core.semantics.semantics
import androidx.ui.core.testTag
import androidx.ui.foundation.Box
import androidx.ui.graphics.Color
import androidx.ui.layout.preferredSize
import androidx.ui.test.assertPixels
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.findByTag
import androidx.ui.unit.Dp
import androidx.ui.unit.IntPxSize
import androidx.ui.unit.dp
import androidx.ui.unit.ipx
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@LargeTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
@RunWith(Parameterized::class)
class ElevationOverlayTest(private val elevation: Dp?, overlayColor: Color?) {

    private val Tag = "Surface"
    private val SurfaceSize = IntPxSize(10.ipx, 10.ipx)
    private val expectedOverlayColor = overlayColor!!

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        // Mappings for elevation -> expected overlay color in dark theme
        fun initElevation(): Array<Any> = arrayOf(
            arrayOf(0.dp, Color(0xFF121212), null),
            arrayOf(1.dp, Color(0xFF1E1E1E), null),
            arrayOf(2.dp, Color(0xFF232323), null),
            arrayOf(3.dp, Color(0xFF262626), null),
            arrayOf(4.dp, Color(0xFF282828), null),
            arrayOf(6.dp, Color(0xFF2B2B2B), null),
            arrayOf(8.dp, Color(0xFF2E2E2E), null),
            arrayOf(12.dp, Color(0xFF333333), null),
            arrayOf(16.dp, Color(0xFF353535), null),
            arrayOf(24.dp, Color(0xFF393939), null)
        )
    }

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun correctElevationOverlayInDarkTheme() {
        setupSurfaceForTesting(elevation!!, darkColorPalette())

        findByTag(Tag)
            .captureToBitmap()
            .assertPixels(SurfaceSize) {
                expectedOverlayColor
            }
    }

    @Test
    fun noChangesInLightTheme() {
        setupSurfaceForTesting(elevation!!, lightColorPalette())

        // No overlay should be applied in light theme
        val expectedSurfaceColor = Color.White

        findByTag(Tag)
            .captureToBitmap()
            .assertPixels(SurfaceSize) {
                expectedSurfaceColor
            }
    }

    private fun setupSurfaceForTesting(elevation: Dp, colorPalette: ColorPalette) {
        with(composeTestRule.density) {
            composeTestRule.setContent {
                MaterialTheme(colorPalette) {
                    Box {
                        Surface(elevation = elevation) {
                            // Make the surface size small so we compare less pixels
                            Box(
                                Modifier.preferredSize(
                                    SurfaceSize.width.toDp(),
                                    SurfaceSize.height.toDp()
                                ).semantics(container = true).testTag(Tag)
                            )
                        }
                    }
                }
            }
        }
    }
}

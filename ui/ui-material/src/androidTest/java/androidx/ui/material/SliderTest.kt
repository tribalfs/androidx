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
import androidx.compose.mutableStateOf
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Modifier
import androidx.ui.core.testTag
import androidx.ui.foundation.drawBackground
import androidx.ui.graphics.Color
import androidx.ui.graphics.compositeOver
import androidx.ui.graphics.toArgb
import androidx.ui.layout.DpConstraints
import androidx.ui.test.assertValueEquals
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.findByTag
import androidx.ui.test.runOnIdleCompose
import androidx.ui.test.runOnUiThread
import androidx.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sqrt

@MediumTest
@RunWith(JUnit4::class)
class SliderTest {
    private val tag = "slider"

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun sliderPosition_valueCoercion() {
        val state = mutableStateOf(0f)
        composeTestRule.setContent {
            Slider(
                modifier = Modifier.testTag(tag),
                value = state.value,
                onValueChange = { state.value = it },
                valueRange = 0f..1f
            )
        }
        runOnIdleCompose {
            state.value = 2f
        }
        findByTag(tag).assertValueEquals("100 percent")
        runOnIdleCompose {
            state.value = -123145f
        }
        findByTag(tag).assertValueEquals("0 percent")
    }

    @Test(expected = IllegalArgumentException::class)
    fun sliderPosition_stepsThrowWhenLessThanZero() {
        composeTestRule.setContent {
            Slider(value = 0f, onValueChange = {}, steps = -1)
        }
    }

    @Test
    fun slider_semantics() {
        val state = mutableStateOf(0f)

        composeTestRule
            .setMaterialContent {
                Slider(modifier = Modifier.testTag(tag), value = state.value,
                    onValueChange = { state.value = it })
            }

        findByTag(tag)
            .assertValueEquals("0 percent")

        runOnUiThread {
            state.value = 0.5f
        }

        findByTag(tag)
            .assertValueEquals("50 percent")
    }

    @Test
    fun slider_sizes() {
        val state = mutableStateOf(0f)
        composeTestRule
            .setMaterialContentAndCollectSizes(
                parentConstraints = DpConstraints(maxWidth = 100.dp, maxHeight = 100.dp)
            ) { Slider(value = state.value, onValueChange = { state.value = it }) }
            .assertHeightEqualsTo(48.dp)
            .assertWidthEqualsTo(100.dp)
    }

    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    @Test
    fun slider_endsAreRounded() {
        val sliderTag = "slider"
        var thumbStrokeWidth = 0
        var thumbPx = 0
        composeTestRule.setMaterialContent {
            with(DensityAmbient.current) {
                thumbStrokeWidth = TrackHeight.toIntPx()
                thumbPx = ThumbRadius.toIntPx()
            }
            Slider(modifier = Modifier.testTag(sliderTag).drawBackground(Color.Gray),
                color = Color.Green,
                value = 0.5f,
                onValueChange = {}
            )
        }

        findByTag(sliderTag).captureToBitmap().apply {
            assertNotEquals(0, thumbStrokeWidth)
            assertNotEquals(0, thumbPx)

            val compositedColor =
                Color.Green.copy(alpha = InactiveTrackColorAlpha).compositeOver(Color.Gray)

            val hyp = sqrt(2.0) / 2
            val radius = thumbStrokeWidth / 2

            val left = floor(thumbPx - radius * hyp).toInt()
            val upper = floor(height / 2 - radius * hyp).toInt()
            // top left outside the rounded area has the background color
            assertEquals(getPixel(left - 1, upper - 1), Color.Gray.toArgb())
            // top left inside the rounded area by a few pixels has the track color
            assertEquals(getPixel(left + 3, upper + 3), Color.Green.toArgb())

            val lower = ceil(height / 2 + radius * hyp).toInt()
            // bottom left outside the rounded area has the background color
            assertEquals(getPixel(left - 1, lower + 1), Color.Gray.toArgb())
            // bottom left inside the rounded area by a few pixels has the track color
            assertEquals(getPixel(left + 3, lower - 3), Color.Green.toArgb())

            // top right outside the rounded area has the background color
            val right = ceil(width - thumbPx + radius * hyp).toInt()
            assertEquals(getPixel(right + 1, upper - 1), Color.Gray.toArgb())

            // top right inside the rounded area has the track color with the
            // inactive opacity composited over the background
            val upperRightInsideColor = Color(getPixel(right - 3, upper + 3))
            assertEquals(upperRightInsideColor.alpha, compositedColor.alpha, 0.01f)
            assertEquals(upperRightInsideColor.red, compositedColor.red, 0.01f)
            assertEquals(upperRightInsideColor.blue, compositedColor.blue, 0.01f)
            assertEquals(upperRightInsideColor.green, compositedColor.green, 0.01f)

            // lower right outside the rounded area has the background color
            assertEquals(getPixel(right + 1, lower + 1), Color.Gray.toArgb())

            // lower right inside the rounded area has the track color with the
            // inactive opacity composited over the background
            val lowerRightInsideColor = Color(getPixel(right - 3, lower - 3))
            assertEquals(lowerRightInsideColor.alpha, compositedColor.alpha, 0.01f)
            assertEquals(lowerRightInsideColor.red, compositedColor.red, 0.01f)
            assertEquals(lowerRightInsideColor.blue, compositedColor.blue, 0.01f)
            assertEquals(lowerRightInsideColor.green, compositedColor.green, 0.01f)

            // left along the center has the track color
            assertEquals(getPixel(thumbPx, height / 2), Color.Green.toArgb())

            // right along the center has the modulated color composited over the background
            val actualColor = Color(getPixel(width - thumbPx, height / 2))
            assertEquals(actualColor.alpha, compositedColor.alpha, 0.01f)
            assertEquals(actualColor.red, compositedColor.red, 0.01f)
            assertEquals(actualColor.blue, compositedColor.blue, 0.01f)
            assertEquals(actualColor.green, compositedColor.green, 0.01f)
        }
    }
}
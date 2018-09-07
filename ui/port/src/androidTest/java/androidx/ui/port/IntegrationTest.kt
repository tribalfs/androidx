/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.ui.port

import android.app.Activity
import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import android.view.View.MeasureSpec.AT_MOST
import androidx.test.InstrumentationRegistry
import androidx.test.filters.SmallTest
import androidx.test.rule.ActivityTestRule
import androidx.ui.CraneView
import androidx.ui.foundation.Key
import androidx.ui.painting.Image
import androidx.ui.widgets.basic.RawImage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import androidx.testutils.PollingCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import kotlin.math.abs

@RunWith(JUnit4::class)
@SmallTest
class IntegrationTest {
    @get:Rule
    public val activityTestRule = ActivityTestRule<TestActivity>(TestActivity::class.java)

    private lateinit var activity: Activity
    private lateinit var instrumentation: Instrumentation

    @Before
    fun setup() {
        activity = activityTestRule.activity
        PollingCheck.waitFor { activity.hasWindowFocus() }
        instrumentation = InstrumentationRegistry.getInstrumentation()
    }

    private val image by lazy {
        BitmapFactory.decodeResource(
                activity.resources,
                R.drawable.four_quadrants,
                BitmapFactory.Options().apply { inScaled = false }
        )
    }

    /**
     * The test draws a bitmap representing a square that consists of 4 equal quadrants of
     * different colors. The test then looks at the resulting drawing, and checks with a
     * tolerance that it still contains four quadrants of different colours, and that each
     * colour is approximately 25% of the drawing. The tolerance is required as a consequence
     * of the noise introduced in the drawing when the original bitmap is scaled and maybe
     * filtered.
     */
    @Test
    fun test() {
        // Craneview containing a jetpack image.
        lateinit var craneView: CraneView
        // A canvas where we will draw the craneView.
        lateinit var canvas: Canvas
        // A bitmap backing the above canvas.
        lateinit var bitmap: Bitmap

        // Initialize craneView & measure.
        instrumentation.runOnMainSync {
            val widget = RawImage(
                    image = Image(image),
                    key = Key.createKey("four quadrants image")
            )

            // Measure.
            craneView = CraneView(activity, widget)
            craneView.measure(
                    View.MeasureSpec.makeMeasureSpec(activity.window.decorView.width, AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(activity.window.decorView.height, AT_MOST)
            )

            // Initialize the canvas and its backing bitmap.
            bitmap = Bitmap.createBitmap(
                    craneView.measuredWidth,
                    craneView.measuredHeight,
                    image.config
            )
            canvas = Canvas(bitmap)
        }

        // Layout. Doing this in a separate runOnMainSync, as during measurement we schedule
        // a frame in a post'd job on the main thread, and we want that to be run before.
        instrumentation.runOnMainSync {
            craneView.layout(0, 0, craneView.measuredWidth, craneView.measuredHeight)
        }

        // Draw.
        instrumentation.runOnMainSync {
            craneView.draw(canvas)
        }

        // With some tolerance, make sure the drawing still has four quadrants.
        // (we need some error acceptance as the bitmap is scaled, filtered, etc. before being
        // drawn).
        with(bitmap.pixelCount().toList()
                .filter { it.first != 0 } // Filter out the count of not drawn pixels.
                .sortedByDescending(Pair<Int, Int>::second) // Sort by frequency.
        ) {
            // The first four colours by frequency should be > 95% of the total number of pixels.
            val frequentPixelsSum = take(4).map { it.second }.sum()
            assertTrue(frequentPixelsSum > 0.95f * map { it.second }.sum())
            // Each of the 4 most frequent colors should be 25% roughly.
            val quarterPixels = frequentPixelsSum / 4f
            val epsilon = frequentPixelsSum * 0.01f // 1% of the solid color area
            take(4).forEach { assertEquals(quarterPixels, it.second.toFloat(), epsilon) }
        }
    }

    private fun Bitmap.pixelCount(): MutableMap<Int, Int> {
        val count = mutableMapOf<Int, Int>()
        for (i in 0 until width) {
            for (j in 0 until height) {
                val pixel = getPixel(i, j)
                count[pixel] = (count[pixel] ?: 0) + 1
            }
        }
        return count
    }

    companion object {
        class TestActivity : Activity()
    }
}
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

package androidx.ui.foundation

import android.os.Build
import androidx.test.filters.MediumTest
import androidx.test.filters.SdkSuppress
import androidx.ui.core.Alignment
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Modifier
import androidx.ui.core.TestTag
import androidx.ui.foundation.test.R
import androidx.ui.geometry.Rect
import androidx.ui.graphics.Canvas
import androidx.ui.graphics.Color
import androidx.ui.graphics.ImageAsset
import androidx.ui.graphics.Paint
import androidx.ui.graphics.Path
import androidx.ui.core.ContentScale
import androidx.ui.graphics.toArgb
import androidx.ui.layout.preferredSize
import androidx.ui.layout.preferredSizeIn
import androidx.ui.layout.wrapContentSize
import androidx.ui.res.loadVectorResource
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.findByTag
import androidx.ui.unit.dp
import org.junit.Assert
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
@RunWith(JUnit4::class)
class ImageTest {

    val contentTag = "ImageTest"

    val imageWidth = 100
    val imageHeight = 100
    val containerSize = imageWidth

    val bgColor = Color.Blue
    val pathColor = Color.Red

    @get:Rule
    val rule = createComposeRule()

    private fun createImageAsset(): ImageAsset {
        val image = ImageAsset(imageWidth, imageHeight)
        val path = Path().apply {
            lineTo(imageWidth.toFloat(), imageHeight.toFloat())
            lineTo(0.0f, imageHeight.toFloat())
            close()
        }
        val paint = Paint()
        Canvas(image).apply {
            paint.color = bgColor
            drawRect(
                Rect.fromLTWH(
                    0.0f,
                    0.0f,
                    imageWidth.toFloat(),
                    imageHeight.toFloat()
                ),
                paint
            )

            paint.color = pathColor
            drawPath(path, paint)
        }
        return image
    }

    @Test
    fun testImage() {
        rule.setContent {
            val size = (containerSize / DensityAmbient.current.density).dp
            Box(
                Modifier.preferredSize(size)
                    .drawBackground(Color.White)
                    .wrapContentSize(Alignment.Center)
            ) {
                TestTag(contentTag) {
                    Image(asset = createImageAsset())
                }
            }
        }

        val bgColorArgb = bgColor.toArgb()
        val pathArgb = pathColor.toArgb()

        findByTag(contentTag).captureToBitmap().apply {
            val imageStartX = width / 2 - imageWidth / 2
            val imageStartY = height / 2 - imageHeight / 2
            Assert.assertEquals(bgColorArgb, getPixel(imageStartX + 2, imageStartY))
            Assert.assertEquals(pathArgb, getPixel(imageStartX, imageStartY + 1))
            Assert.assertEquals(pathArgb, getPixel(imageStartX + (imageWidth / 2) - 1,
                imageStartY + (imageHeight / 2) + 1))
            Assert.assertEquals(bgColorArgb, getPixel(imageStartX + (imageWidth / 2) - 2,
                imageStartY + (imageHeight / 2) - 5))
            Assert.assertEquals(pathArgb, getPixel(imageStartX, imageStartY + imageHeight - 1))
        }
    }

    @Test
    fun testImageFixedSizeIsStretched() {
        val imageComposableWidth = imageWidth * 2
        val imageComposableHeight = imageHeight * 2
        rule.setContent {
            val density = DensityAmbient.current.density
            val size = (containerSize * 2 / density).dp
            Box(
                Modifier.preferredSize(size)
                    .drawBackground(Color.White)
                    .wrapContentSize(Alignment.Center)
            ) {
                TestTag(contentTag) {
                    // The resultant Image composable should be twice the size of the underlying
                    // ImageAsset that is to be drawn and will stretch the content to fit
                    // the bounds
                    Image(asset = createImageAsset(),
                        modifier = Modifier.preferredSize(
                            (imageComposableWidth / density).dp,
                            (imageComposableHeight / density).dp
                        ),
                        contentScale = ContentScale.Fit
                    )
                }
            }
        }

        val bgColorArgb = bgColor.toArgb()
        val pathArgb = pathColor.toArgb()
        findByTag(contentTag).captureToBitmap().apply {
            val imageStartX = width / 2 - imageComposableWidth / 2
            val imageStartY = height / 2 - imageComposableHeight / 2
            Assert.assertEquals(bgColorArgb, getPixel(imageStartX + 5, imageStartY))
            Assert.assertEquals(pathArgb, getPixel(imageStartX, imageStartY + 5))
            Assert.assertEquals(pathArgb, getPixel(imageStartX + (imageComposableWidth / 2) - 5,
                imageStartY + (imageComposableHeight / 2) + 5))
            Assert.assertEquals(bgColorArgb, getPixel(imageStartX + (imageComposableWidth / 2),
                imageStartY + (imageComposableHeight / 2) - 10))
            Assert.assertEquals(pathArgb, getPixel(imageStartX, imageStartY +
                    imageComposableHeight - 1))
        }
    }

    @Test
    fun testImageFixedSizeAlignedBottomEnd() {
        val imageComposableWidth = imageWidth * 2
        val imageComposableHeight = imageHeight * 2
        rule.setContent {
            val density = DensityAmbient.current.density
            val size = (containerSize * 2 / density).dp
            Box(
                Modifier.preferredSize(size)
                    .drawBackground(Color.White)
                    .wrapContentSize(Alignment.Center)
            ) {
                TestTag(contentTag) {
                    // The resultant Image composable should be twice the size of the underlying
                    // ImageAsset that is to be drawn in the bottom end section of the composable
                    Image(asset = createImageAsset(),
                        modifier = Modifier.preferredSize(
                            (imageComposableWidth / density).dp,
                            (imageComposableHeight / density).dp
                        ),
                        alignment = Alignment.BottomEnd
                    )
                }
            }
        }

        val bgColorArgb = bgColor.toArgb()
        val pathArgb = pathColor.toArgb()
        findByTag(contentTag).captureToBitmap().apply {
            val composableEndX = width / 2 + imageComposableWidth / 2
            val composableEndY = height / 2 + imageComposableHeight / 2
            val imageStartX = composableEndX - imageWidth
            val imageStartY = composableEndY - imageHeight
            Assert.assertEquals(bgColorArgb, getPixel(imageStartX + 2, imageStartY))
            Assert.assertEquals(pathArgb, getPixel(imageStartX, imageStartY + 1))
            Assert.assertEquals(pathArgb, getPixel(imageStartX + (imageWidth / 2) - 1,
                imageStartY + (imageHeight / 2) + 1))
            Assert.assertEquals(bgColorArgb, getPixel(imageStartX + (imageWidth / 2) - 2,
                imageStartY + (imageHeight / 2) - 5))
            Assert.assertEquals(pathArgb, getPixel(imageStartX, imageStartY + imageHeight - 1))
        }
    }

    @Ignore("Flaky. b/151701537 for info")
    @Test
    fun testVectorScaledCentered() {
        val boxWidth = 240
        val boxHeight = 240
        rule.setContent {
            val density = DensityAmbient.current.density
            val size = (boxWidth * 2 / density).dp
            val minWidth = (boxWidth / density).dp
            val minHeight = (boxHeight / density).dp
            Box(
                Modifier.preferredSize(size)
                    .drawBackground(Color.White)
                    .wrapContentSize(Alignment.Center)
            ) {
                TestTag(contentTag) {
                    loadVectorResource(R.drawable.ic_vector_asset_test).resource.resource?.let {
                        Image(
                            it,
                            modifier = Modifier.preferredSizeIn(
                                minWidth = minWidth,
                                minHeight = minHeight
                            ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        val imageColor = Color.Red.toArgb()
        val containerBgColor = Color.White.toArgb()
        findByTag(contentTag).captureToBitmap().apply {
            val imageStartX = width / 2 - boxWidth / 2
            val imageStartY = height / 2 - boxHeight / 2
            Assert.assertEquals(containerBgColor, getPixel(imageStartX - 1, imageStartY - 1))
            Assert.assertEquals(containerBgColor, getPixel(imageStartX + boxWidth + 1,
                imageStartY - 1))
            Assert.assertEquals(containerBgColor, getPixel(imageStartX + boxWidth + 1,
                imageStartY + boxHeight + 1))
            Assert.assertEquals(containerBgColor, getPixel(imageStartX - 1, imageStartY +
                    boxHeight + 1))

            Assert.assertEquals(imageColor, getPixel(imageStartX, imageStartY + 15))
            Assert.assertEquals(containerBgColor, getPixel(imageStartX + boxWidth - 2,
                imageStartY - 1))
            Assert.assertEquals(imageColor, getPixel(imageStartX + boxWidth - 10,
                imageStartY + boxHeight - 2))
            Assert.assertEquals(imageColor, getPixel(imageStartX, imageStartY +
                    boxHeight - 2))
        }
    }
}
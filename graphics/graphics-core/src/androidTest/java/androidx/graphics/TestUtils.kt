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

package androidx.graphics

import android.graphics.Bitmap
import org.junit.Assert

fun Bitmap.verifyQuadrants(
    topLeft: Int,
    topRight: Int,
    bottomLeft: Int,
    bottomRight: Int
) {
    Assert.assertEquals(topLeft, getPixel(1, 1))
    Assert.assertEquals(topLeft, getPixel(width / 2 - 2, 1))
    Assert.assertEquals(topLeft, getPixel(width / 2 - 2, height / 2 - 2))
    Assert.assertEquals(topLeft, getPixel(1, height / 2 - 2))

    Assert.assertEquals(topRight, getPixel(width / 2 + 2, 1))
    Assert.assertEquals(topRight, getPixel(width - 2, 1))
    Assert.assertEquals(topRight, getPixel(width - 2, height / 2 - 2))
    Assert.assertEquals(topRight, getPixel(width / 2 + 2, height / 2 - 2))

    Assert.assertEquals(bottomLeft, getPixel(1, height / 2 + 2))
    Assert.assertEquals(bottomLeft, getPixel(width / 2 - 2, height / 2 + 2))
    Assert.assertEquals(bottomLeft, getPixel(width / 2 - 2, height - 2))
    Assert.assertEquals(bottomLeft, getPixel(1, height - 2))

    Assert.assertEquals(bottomRight, getPixel(width / 2 + 2, height / 2 + 2))
    Assert.assertEquals(bottomRight, getPixel(width - 2, height / 2 + 2))
    Assert.assertEquals(bottomRight, getPixel(width - 2, height - 2))
    Assert.assertEquals(bottomRight, getPixel(width / 2 + 2, height - 2))
}
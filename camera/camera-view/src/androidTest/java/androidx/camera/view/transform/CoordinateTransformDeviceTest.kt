/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.camera.view.transform

import android.graphics.Matrix
import android.graphics.Rect
import androidx.camera.view.transform.TransformTestUtils.createFakeImageProxy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrument tests for [CoordinateTransform].
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
public class CoordinateTransformDeviceTest {

    @Test(expected = IllegalArgumentException::class)
    public fun mismatchViewPort_throwsException() {
        // Arrange: create 2 imageProxy with mismatched viewport aspect ratio.
        val source = ImageProxyTransform.Builder(
            createFakeImageProxy(300, 400, Rect(0, 0, 300, 400))
        ).build()
        val target = ImageProxyTransform.Builder(
            createFakeImageProxy(300, 400, Rect(0, 0, 200, 400))
        ).build()

        // Act: creating CoordinateTransform throws exception.
        CoordinateTransform(source, target)
    }

    @Test
    public fun sameSourceAndTarget_getsIdentityMatrix() {
        // Arrange.
        val imageProxy = ImageProxyTransform.Builder(
            createFakeImageProxy(3, 4, Rect(0, 0, 3, 4))
        ).build()

        // Act: create a transform with the same source and target.
        val transform = CoordinateTransform(imageProxy, imageProxy)

        // Assert: the result transform is a no-op.
        val matrix = Matrix()
        // Set an arbitrary transform so it's no longer identity matrix.
        matrix.setRotate(90f)
        assertThat(matrix.isIdentity).isFalse()
        transform.getTransform(matrix)
        assertThat(matrix.isIdentity).isTrue()
    }

    @Test
    public fun scaleImageProxy() {
        // Arrange: create 2 ImageProxy with the only difference being 10x scale.
        val source = ImageProxyTransform.Builder(
            createFakeImageProxy(3, 4, Rect(0, 0, 3, 4))
        ).build()
        val target = ImageProxyTransform.Builder(
            createFakeImageProxy(30, 40, Rect(0, 0, 30, 40))
        ).build()

        // Act.
        val coordinateTransform = CoordinateTransform(source, target)

        // Assert: the mapping is scaled.
        val point = floatArrayOf(3f, 4f)
        coordinateTransform.mapPoints(point)
        assertThat(point).isEqualTo(floatArrayOf(30f, 40f))
    }

    @Test
    public fun scaleAndRotateImageProxy() {
        // Arrange: create 2 ImageProxy with different scale and rotation.
        val source = ImageProxyTransform.Builder(
            createFakeImageProxy(3, 4, Rect(0, 0, 3, 4))
        ).setRotationDegrees(270).build()
        val target = ImageProxyTransform.Builder(
            createFakeImageProxy(30, 40, Rect(0, 0, 30, 40))
        ).setRotationDegrees(90).build()

        // Act.
        val coordinateTransform = CoordinateTransform(source, target)

        // Assert.
        val point = floatArrayOf(0f, 0f)
        coordinateTransform.mapPoints(point)
        assertThat(point).isEqualTo(floatArrayOf(40f, 30f))
    }

    @Test
    public fun withViewPortWithoutCropRect() {
        // Arrange: create 2 ImageProxy that have crop rect, but the coordinates do not respect the
        // crop rect. (MLKit scenario).
        val source = ImageProxyTransform.Builder(
            createFakeImageProxy(16, 12, Rect(2, 2, 10, 8))
        ).build()
        val target = ImageProxyTransform.Builder(
            createFakeImageProxy(16, 12, Rect(8, 6, 16, 12))
        ).build()

        // Act.
        val coordinateTransform = CoordinateTransform(source, target)

        // Assert.
        val point = floatArrayOf(10f, 8f)
        coordinateTransform.mapPoints(point)
        assertThat(point).isEqualTo(floatArrayOf(16f, 12f))
    }

    @Test
    public fun withViewPortAndCropRect() {
        // Arrange: create 2 ImageProxy that have crop rect, and the coordinates respect the crop
        // rect.
        val sourceCropRect = Rect(2, 2, 10, 8)
        val source = ImageProxyTransform.Builder(createFakeImageProxy(16, 12, sourceCropRect))
            .setCropRect(sourceCropRect).build()
        val targetCropRect = Rect(8, 6, 16, 12)
        val target = ImageProxyTransform
            .Builder(createFakeImageProxy(16, 12, targetCropRect))
            .setCropRect(targetCropRect)
            .build()

        // Act.
        val coordinateTransform = CoordinateTransform(source, target)

        // Assert.
        val point = floatArrayOf(8f, 6f)
        coordinateTransform.mapPoints(point)
        assertThat(point).isEqualTo(floatArrayOf(8f, 6f))
    }
}
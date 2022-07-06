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
package androidx.camera.integration.core

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraX
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.testing.CameraUtil
import androidx.camera.testing.SurfaceTextureProvider
import androidx.camera.testing.fakes.FakeLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assume
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

private val DEFAULT_SELECTOR = CameraSelector.DEFAULT_BACK_CAMERA

/** Contains tests for [CameraX] which varies use case combinations to run. */
@LargeTest
@RunWith(Parameterized::class)
class UseCaseCombinationTest(
    private val implName: String,
    private val cameraConfig: CameraXConfig
) {

    @get:Rule
    val cameraRule = CameraUtil.grantCameraPermissionAndPreTest(
        CameraUtil.PreTestCameraIdList(cameraConfig)
    )

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
            arrayOf(Camera2Config::class.simpleName, Camera2Config.defaultConfig()),
            arrayOf(CameraPipeConfig::class.simpleName, CameraPipeConfig.defaultConfig())
        )
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var cameraProvider: ProcessCameraProvider
    private lateinit var fakeLifecycleOwner: FakeLifecycleOwner

    @Before
    fun initializeCameraX(): Unit = runBlocking {
        Assume.assumeTrue(CameraUtil.hasCameraWithLensFacing(CameraSelector.LENS_FACING_BACK))
        ProcessCameraProvider.configureInstance(cameraConfig)
        cameraProvider = ProcessCameraProvider.getInstance(context)[10, TimeUnit.SECONDS]

        withContext(Dispatchers.Main) {
            fakeLifecycleOwner = FakeLifecycleOwner()
            fakeLifecycleOwner.startAndResume()
        }
    }

    @After
    fun shutdownCameraX(): Unit = runBlocking {
        if (::cameraProvider.isInitialized) {
            withContext(Dispatchers.Main) {
                cameraProvider.unbindAll()
                cameraProvider.shutdown()[10, TimeUnit.SECONDS]
            }
        }
    }

    /** Test Combination: Preview + ImageCapture */
    @Test
    fun previewCombinesImageCapture(): Unit = runBlocking {
        skipTestOnCameraPipeConfig()

        // Arrange.
        val previewMonitor = PreviewMonitor()
        val preview = initPreview(previewMonitor)
        val imageCapture = initImageCapture()

        // TODO(b/160249108) move off of main thread once UseCases can be attached on any thread
        // Act.
        withContext(Dispatchers.Main) {
            preview.setSurfaceProvider(SurfaceTextureProvider.createSurfaceTextureProvider())
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageCapture
            )
        }

        // Assert.
        imageCapture.waitForCapturing()
        previewMonitor.waitForStream()
    }

    /** Test Combination: Preview + ImageAnalysis */
    @Test
    fun previewCombinesImageAnalysis(): Unit = runBlocking {
        skipTestOnCameraPipeConfig()

        // Arrange.
        val previewMonitor = PreviewMonitor()
        val preview = initPreview(previewMonitor)
        val imageAnalysisMonitor = AnalysisMonitor()
        val imageAnalysis = initImageAnalysis(imageAnalysisMonitor)

        // TODO(b/160249108) move off of main thread once UseCases can be attached on any thread
        // Act.
        withContext(Dispatchers.Main) {
            preview.setSurfaceProvider(SurfaceTextureProvider.createSurfaceTextureProvider())
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageAnalysis
            )
        }

        // Assert.
        previewMonitor.waitForStream()
        imageAnalysisMonitor.waitForImageAnalysis()
    }

    /** Test Combination: Preview + ImageAnalysis + ImageCapture  */
    @Test
    fun previewCombinesImageAnalysisAndImageCapture(): Unit = runBlocking {
        skipTestOnCameraPipeConfig()

        // Arrange.
        val previewMonitor = PreviewMonitor()
        val preview = initPreview(previewMonitor)
        val imageCapture = initImageCapture()
        val imageAnalysisMonitor = AnalysisMonitor()
        val imageAnalysis = initImageAnalysis(imageAnalysisMonitor)

        // TODO(b/160249108) move off of main thread once UseCases can be attached on any thread
        // Act.
        withContext(Dispatchers.Main) {
            preview.setSurfaceProvider(SurfaceTextureProvider.createSurfaceTextureProvider())
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageAnalysis,
                imageCapture
            )
        }

        // Assert.
        imageCapture.waitForCapturing()
        previewMonitor.waitForStream()
        imageAnalysisMonitor.waitForImageAnalysis()
    }

    @Test
    fun sequentialBindTwoUseCases(): Unit = runBlocking {
        skipTestOnCameraPipeConfig()

        // Arrange.
        val previewMonitor = PreviewMonitor()
        val preview = initPreview(previewMonitor)
        val imageCapture = initImageCapture()

        withContext(Dispatchers.Main) {
            preview.setSurfaceProvider(SurfaceTextureProvider.createSurfaceTextureProvider())
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
            )
        }
        previewMonitor.waitForStream()

        // Act.
        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageCapture
            )
        }

        // Assert.
        imageCapture.waitForCapturing()
        previewMonitor.waitForStream()
    }

    @Test
    fun sequentialBindThreeUseCases(): Unit = runBlocking {
        skipTestOnCameraPipeConfig()

        // Arrange.
        val previewMonitor = PreviewMonitor()
        val preview = initPreview(previewMonitor)
        val imageCapture = initImageCapture()
        val imageAnalysisMonitor = AnalysisMonitor()
        val imageAnalysis = initImageAnalysis(imageAnalysisMonitor)
        withContext(Dispatchers.Main) {
            preview.setSurfaceProvider(SurfaceTextureProvider.createSurfaceTextureProvider())
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                imageCapture,
            )
        }
        imageCapture.waitForCapturing()
        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageCapture
            )
        }
        imageCapture.waitForCapturing()
        previewMonitor.waitForStream()

        // Act.
        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageCapture,
                imageAnalysis
            )
        }

        // Assert.
        imageCapture.waitForCapturing()
        previewMonitor.waitForStream()
        imageAnalysisMonitor.waitForImageAnalysis()
    }

    @Test
    fun unbindImageAnalysis_captureAndPreviewStillWorking(): Unit = runBlocking {
        skipTestOnCameraPipeConfig()

        // Arrange.
        val previewMonitor = PreviewMonitor()
        val preview = initPreview(previewMonitor)
        val imageCapture = initImageCapture()
        val imageAnalysisMonitor = AnalysisMonitor()
        val imageAnalysis = initImageAnalysis(imageAnalysisMonitor)
        withContext(Dispatchers.Main) {
            preview.setSurfaceProvider(SurfaceTextureProvider.createSurfaceTextureProvider())
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageCapture,
                imageAnalysis
            )
        }
        previewMonitor.waitForStream()
        imageAnalysisMonitor.waitForImageAnalysis()
        imageCapture.waitForCapturing()

        // Act.
        withContext(Dispatchers.Main) {
            cameraProvider.unbind(imageAnalysis)
        }

        // Assert
        imageCapture.waitForCapturing()
        previewMonitor.waitForStream()
    }

    @Test
    fun unbindPreview_captureAndAnalysisStillWorking(): Unit = runBlocking {
        skipTestOnCameraPipeConfig()

        // Arrange.
        val previewMonitor = PreviewMonitor()
        val preview = initPreview(previewMonitor)
        val imageCapture = initImageCapture()
        val imageAnalysisMonitor = AnalysisMonitor()
        val imageAnalysis = initImageAnalysis(imageAnalysisMonitor)
        withContext(Dispatchers.Main) {
            preview.setSurfaceProvider(SurfaceTextureProvider.createSurfaceTextureProvider())
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageCapture,
                imageAnalysis
            )
        }
        previewMonitor.waitForStream()
        imageAnalysisMonitor.waitForImageAnalysis()
        imageCapture.waitForCapturing()

        // Act.
        withContext(Dispatchers.Main) {
            cameraProvider.unbind(preview)
        }
        previewMonitor.waitForStreamIdle()

        // Assert
        imageCapture.waitForCapturing()
        imageAnalysisMonitor.waitForImageAnalysis()
    }

    @Test
    fun unbindImageCapture_previewAndAnalysisStillWorking(): Unit = runBlocking {
        skipTestOnCameraPipeConfig()

        // Arrange.
        val previewMonitor = PreviewMonitor()
        val preview = initPreview(previewMonitor)
        val imageCapture = initImageCapture()
        val imageAnalysisMonitor = AnalysisMonitor()
        val imageAnalysis = initImageAnalysis(imageAnalysisMonitor)
        withContext(Dispatchers.Main) {
            preview.setSurfaceProvider(SurfaceTextureProvider.createSurfaceTextureProvider())
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageCapture,
                imageAnalysis
            )
        }
        previewMonitor.waitForStream()
        imageAnalysisMonitor.waitForImageAnalysis()
        imageCapture.waitForCapturing()

        // Act.
        withContext(Dispatchers.Main) {
            cameraProvider.unbind(imageCapture)
        }

        // Assert
        imageAnalysisMonitor.waitForImageAnalysis()
        previewMonitor.waitForStream()
    }

    private fun initPreview(monitor: PreviewMonitor?): Preview {
        return Preview.Builder()
            .setTargetName("Preview").also {
                monitor?.let { monitor ->
                    Camera2Interop.Extender(it).setSessionCaptureCallback(monitor)
                }
            }.build()
    }

    private fun initImageAnalysis(analyzer: ImageAnalysis.Analyzer?): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setTargetName("ImageAnalysis")
            .build().apply {
                analyzer?.let { analyzer ->
                    setAnalyzer(Dispatchers.IO.asExecutor(), analyzer)
                }
            }
    }

    private fun initImageCapture(): ImageCapture {
        return ImageCapture.Builder().build()
    }

    // TODO(b/187015621): Remove when DeferrableSurface reference count support is added to
    //  Camera-pipe-integration
    private fun skipTestOnCameraPipeConfig() {
        Assume.assumeFalse(
            "DeferrableSurface ref count isn't supported on Camera-pipe-integration (b/187015621)",
            implName == CameraPipeConfig::class.simpleName
        )
    }

    private fun ImageCapture.waitForCapturing(timeMillis: Long = 5000) {
        val callback = object : ImageCapture.OnImageCapturedCallback() {
            val latch = CountDownLatch(1)
            val errors = mutableListOf<ImageCaptureException>()

            override fun onCaptureSuccess(image: ImageProxy) {
                image.close()
                latch.countDown()
            }

            override fun onError(exception: ImageCaptureException) {
                errors.add(exception)
                latch.countDown()
            }
        }

        takePicture(Dispatchers.Main.asExecutor(), callback)

        assertThat(
            callback.latch.await(
                timeMillis, TimeUnit.MILLISECONDS
            ) && callback.errors.isEmpty()
        ).isTrue()
    }

    class PreviewMonitor : CameraCaptureSession.CaptureCallback() {
        private var countDown: CountDownLatch? = null

        fun waitForStream(count: Int = 10, timeMillis: Long = TimeUnit.SECONDS.toMillis(5)) {
            Truth.assertWithMessage("Preview doesn't start").that(synchronized(this) {
                countDown = CountDownLatch(count)
                countDown
            }!!.await(timeMillis, TimeUnit.MILLISECONDS)).isTrue()
        }

        fun waitForStreamIdle(count: Int = 10, timeMillis: Long = TimeUnit.SECONDS.toMillis(5)) {
            Truth.assertWithMessage("Preview doesn't become idle").that(synchronized(this) {
                countDown = CountDownLatch(count)
                countDown
            }!!.await(timeMillis, TimeUnit.MILLISECONDS)).isFalse()
        }

        override fun onCaptureCompleted(
            session: CameraCaptureSession,
            request: CaptureRequest,
            result: TotalCaptureResult
        ) {
            synchronized(this) {
                countDown?.countDown()
            }
        }
    }

    class AnalysisMonitor : ImageAnalysis.Analyzer {
        private var countDown: CountDownLatch? = null

        fun waitForImageAnalysis(count: Int = 10, timeMillis: Long = TimeUnit.SECONDS.toMillis(5)) {
            Truth.assertWithMessage("Preview doesn't start").that(synchronized(this) {
                countDown = CountDownLatch(count)
                countDown
            }!!.await(timeMillis, TimeUnit.MILLISECONDS)).isTrue()
        }

        override fun analyze(image: ImageProxy) {
            image.close()
            synchronized(this) {
                countDown?.countDown()
            }
        }
    }
}

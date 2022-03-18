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
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraX
import androidx.camera.core.CameraXConfig
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.testing.CameraUtil
import androidx.camera.testing.fakes.FakeLifecycleOwner
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
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

        val preview = initPreview()
        val imageCapture = initImageCapture()

        // TODO(b/160249108) move off of main thread once UseCases can be attached on any thread
        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageCapture
            )
        }
    }

    /** Test Combination: Preview + ImageAnalysis */
    @Test
    fun previewCombinesImageAnalysis(): Unit = runBlocking {
        val preview = initPreview()
        val imageAnalysis = initImageAnalysis()

        // TODO(b/160249108) move off of main thread once UseCases can be attached on any thread
        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageAnalysis
            )
        }
    }

    /** Test Combination: Preview + ImageAnalysis + ImageCapture  */
    @Test
    fun previewCombinesImageAnalysisAndImageCapture(): Unit = runBlocking {
        skipTestOnCameraPipeConfig()

        val preview = initPreview()
        val imageAnalysis = initImageAnalysis()
        val imageCapture = initImageCapture()

        // TODO(b/160249108) move off of main thread once UseCases can be attached on any
        //  thread
        withContext(Dispatchers.Main) {
            cameraProvider.bindToLifecycle(
                fakeLifecycleOwner,
                DEFAULT_SELECTOR,
                preview,
                imageAnalysis,
                imageCapture
            )
        }
    }

    private fun initPreview(): Preview {
        return Preview.Builder()
            .setTargetName("Preview")
            .build()
    }

    private fun initImageAnalysis(): ImageAnalysis {
        return ImageAnalysis.Builder()
            .setTargetName("ImageAnalysis")
            .build()
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
}

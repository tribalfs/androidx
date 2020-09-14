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

package androidx.camera.camera2

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraX
import androidx.camera.core.UseCase
import androidx.camera.core.VideoCapture
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.core.internal.CameraUseCaseAdapter
import androidx.camera.testing.CameraUtil
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.testutils.assertThrows
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout
import org.mockito.Mockito.verify
import java.io.File
import java.util.Collections
import java.util.concurrent.TimeUnit

@LargeTest
@RunWith(AndroidJUnit4::class)
class VideoCaptureTest {

    @get:Rule
    val mUseCamera: TestRule = CameraUtil.grantCameraPermissionAndPreTest()

    @get:Rule
    val mPermissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )

    private val mInstrumentation = InstrumentationRegistry.getInstrumentation()

    private val mContext = ApplicationProvider.getApplicationContext<Context>()

    private lateinit var mCameraSelector: CameraSelector

    private var mCamera: CameraUseCaseAdapter? = null

    @Before
    fun setUp() {
        // TODO(b/168175357): Fix VideoCaptureTest problems on CuttleFish API 29
        assumeFalse(
            "Cuttlefish has MediaCodec dequeueInput/Output buffer fails issue. Unable to test.",
            Build.MODEL.contains("Cuttlefish") && Build.VERSION.SDK_INT == 29
        )

        // TODO(b/168187087): Video: Unable to record Video on Pixel 1 API 26,27 when only
        //  VideoCapture is bound
        assumeFalse(
            "Pixel running API 26 has CameraDevice.onError when set repeating request",
            Build.DEVICE.equals(
                "sailfish",
                true
            ) && (Build.VERSION.SDK_INT == 26 || Build.VERSION.SDK_INT == 27)
        )

        assumeTrue(CameraUtil.deviceHasCamera())

        if (CameraUtil.hasCameraWithLensFacing(CameraSelector.LENS_FACING_BACK)) {
            mCameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
        } else {
            mCameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
        }

        CameraX.initialize(mContext, Camera2Config.defaultConfig()).get()
        mCamera = CameraUtil.createCameraUseCaseAdapter(mContext, mCameraSelector)
    }

    @After
    fun tearDown() {
        mCamera?.apply {
            mInstrumentation.runOnMainSync {
                removeUseCases(useCases)
            }
        }

        CameraX.shutdown().get(10000, TimeUnit.MILLISECONDS)
    }

    @Test
    @SdkSuppress(maxSdkVersion = 25)
    fun buildFileOutputOptionsWithFileDescriptor_throwExceptionWhenAPILevelSmallerThan26() {
        val file = File.createTempFile("CameraX", ".tmp").apply {
            deleteOnExit()
        }

        val fileDescriptor =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).fileDescriptor

        assertThrows<IllegalArgumentException> {
            VideoCapture.OutputFileOptions.Builder(fileDescriptor).build()
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun startRecordingWithFileDescriptor_whenAPILevelLargerThan26() {
        val file = File.createTempFile("CameraX", ".tmp").apply {
            deleteOnExit()
        }

        val fileDescriptor =
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE).fileDescriptor
        val useCase = VideoCapture.Builder().build()

        mInstrumentation.runOnMainSync {
            mCamera!!.addUseCases(Collections.singleton<UseCase>(useCase))
        }

        val outputFileOptions = VideoCapture.OutputFileOptions.Builder(fileDescriptor).build()

        val callback = mock(VideoCapture.OnVideoSavedCallback::class.java)

        // Start recording with FileDescriptor
        useCase.startRecording(outputFileOptions, CameraXExecutors.mainThreadExecutor(), callback)

        // Recording for seconds
        Thread.sleep(3000)

        // Stop recording
        useCase.stopRecording()

        verify(callback, timeout(10000)).onVideoSaved(any())
    }
}
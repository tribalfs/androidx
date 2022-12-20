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

package androidx.camera.video

import androidx.camera.testing.mocks.helpers.ArgumentCaptor as ArgumentCaptorCameraX
import android.Manifest
import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.app.AsyncNotedAppOp
import android.app.SyncNotedAppOp
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.SurfaceTexture
import android.location.Location
import android.media.MediaMetadataRetriever
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Size
import androidx.camera.camera2.Camera2Config
import androidx.camera.camera2.pipe.integration.CameraPipeConfig
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.ImageFormatConstants
import androidx.camera.core.impl.Observable
import androidx.camera.core.impl.utils.executor.CameraXExecutors
import androidx.camera.core.internal.CameraUseCaseAdapter
import androidx.camera.testing.AudioUtil
import androidx.camera.testing.CameraPipeConfigTestRule
import androidx.camera.testing.CameraUtil
import androidx.camera.testing.CameraXUtil
import androidx.camera.testing.GarbageCollectionUtil
import androidx.camera.testing.LabTestRule
import androidx.camera.testing.SurfaceTextureProvider
import androidx.camera.testing.asFlow
import androidx.camera.testing.mocks.MockConsumer
import androidx.camera.testing.mocks.helpers.CallTimes
import androidx.camera.testing.mocks.helpers.CallTimesAtLeast
import androidx.camera.video.VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED
import androidx.camera.video.VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED
import androidx.camera.video.VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS
import androidx.camera.video.VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA
import androidx.camera.video.VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR
import androidx.camera.video.VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE
import androidx.camera.video.internal.compat.quirk.DeactivateEncoderSurfaceBeforeStopEncoderQuirk
import androidx.camera.video.internal.compat.quirk.DeviceQuirks
import androidx.camera.video.internal.compat.quirk.MediaStoreVideoCannotWrite
import androidx.camera.video.internal.encoder.InvalidConfigException
import androidx.test.core.app.ApplicationProvider
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.testutils.assertThrows
import androidx.testutils.fail
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import org.mockito.ArgumentMatchers.argThat
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.mock
import org.mockito.Mockito.timeout

private const val GENERAL_TIMEOUT = 5000L
private const val STATUS_TIMEOUT = 15000L
private const val TEST_ATTRIBUTION_TAG = "testAttribution"
private const val BITRATE_AUTO = 0

@LargeTest
@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = 21)
class RecorderTest(
    private val implName: String,
    private val cameraConfig: CameraXConfig,
) {

    @get:Rule
    val cameraPipeConfigTestRule = CameraPipeConfigTestRule(
        active = implName == CameraPipeConfig::class.simpleName,
    )

    @get:Rule
    val cameraRule = CameraUtil.grantCameraPermissionAndPreTest(
        CameraUtil.PreTestCameraIdList(cameraConfig)
    )

    @get:Rule
    var testName: TestName = TestName()

    @get:Rule
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )

    @get:Rule
    val labTest: LabTestRule = LabTestRule()

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun data() = listOf(
            arrayOf(Camera2Config::class.simpleName, Camera2Config.defaultConfig()),
            arrayOf(CameraPipeConfig::class.simpleName, CameraPipeConfig.defaultConfig())
        )
    }

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

    private lateinit var cameraUseCaseAdapter: CameraUseCaseAdapter
    private lateinit var recorder: Recorder
    private lateinit var preview: Preview
    private lateinit var surfaceTexturePreview: Preview
    private lateinit var mockVideoRecordEventConsumer: MockConsumer<VideoRecordEvent>

    @Before
    fun setUp() {
        assumeTrue(CameraUtil.hasCameraWithLensFacing(CameraSelector.LENS_FACING_BACK))
        // Skip for b/168175357, b/233661493
        assumeFalse(
            "Skip tests for Cuttlefish MediaCodec issues",
            Build.MODEL.contains("Cuttlefish") &&
                (Build.VERSION.SDK_INT == 29 || Build.VERSION.SDK_INT == 33)
        )
        assumeTrue(AudioUtil.canStartAudioRecord(MediaRecorder.AudioSource.CAMCORDER))

        CameraXUtil.initialize(
            context,
            cameraConfig
        ).get()
        cameraUseCaseAdapter = CameraUtil.createCameraUseCaseAdapter(context, cameraSelector)

        // Using Preview so that the surface provider could be set to control when to issue the
        // surface request.
        val cameraInfo = cameraUseCaseAdapter.cameraInfo
        val candidates = mutableSetOf<Size>().apply {
            if (testName.methodName == "setFileSizeLimit") {
                QualitySelector.getResolution(cameraInfo, Quality.FHD)
                    ?.let { add(it) }
                QualitySelector.getResolution(cameraInfo, Quality.HD)
                    ?.let { add(it) }
                QualitySelector.getResolution(cameraInfo, Quality.SD)
                    ?.let { add(it) }
            }
            QualitySelector.getResolution(cameraInfo, Quality.LOWEST)
                ?.let { add(it) }
        }
        assumeTrue(candidates.isNotEmpty())

        val resolutions: List<android.util.Pair<Int, Array<Size>>> =
            listOf<android.util.Pair<Int, Array<Size>>>(
                android.util.Pair.create(
                    ImageFormatConstants.INTERNAL_DEFINED_IMAGE_FORMAT_PRIVATE,
                    candidates.toTypedArray()
                )
            )
        preview = Preview.Builder().setSupportedResolutions(resolutions).build()

        // Add another Preview to provide an additional surface for b/168187087.
        surfaceTexturePreview = Preview.Builder().build()
        instrumentation.runOnMainSync {
            surfaceTexturePreview.setSurfaceProvider(
                SurfaceTextureProvider.createSurfaceTextureProvider(
                    object : SurfaceTextureProvider.SurfaceTextureCallback {
                        override fun onSurfaceTextureReady(
                            surfaceTexture: SurfaceTexture,
                            resolution: Size
                        ) {
                            // No-op
                        }

                        override fun onSafeToRelease(surfaceTexture: SurfaceTexture) {
                            surfaceTexture.release()
                        }
                    }
                )
            )
        }

        assumeTrue(
            "This combination (preview, surfaceTexturePreview) is not supported.",
            cameraUseCaseAdapter.isUseCasesCombinationSupported(
                preview,
                surfaceTexturePreview
            )
        )

        cameraUseCaseAdapter = CameraUtil.createCameraAndAttachUseCase(
            context,
            cameraSelector,
            // Must put surfaceTexturePreview before preview while addUseCases, otherwise
            // an issue on Samsung device will occur. See b/196755459.
            surfaceTexturePreview,
            preview
        )

        mockVideoRecordEventConsumer = MockConsumer<VideoRecordEvent>()
    }

    @After
    fun tearDown() {
        if (this::cameraUseCaseAdapter.isInitialized) {
            instrumentation.runOnMainSync {
                cameraUseCaseAdapter.removeUseCases(cameraUseCaseAdapter.useCases)
            }
            if (this::recorder.isInitialized) {
                recorder.onSourceStateChanged(VideoOutput.SourceState.INACTIVE)
            }
        }

        CameraXUtil.shutdown().get(10, TimeUnit.SECONDS)
    }

    @Test
    fun canRecordToFile() {
        testRecorderIsConfiguredBasedOnTargetVideoEncodingBitrate(BITRATE_AUTO, enableAudio = true)
    }

    @Test
    fun recordingWithSetTargetVideoEncodingBitRate() {
        testRecorderIsConfiguredBasedOnTargetVideoEncodingBitrate(6_000_000)
        verifyConfiguredVideoBitrate()
    }

    @Test
    fun recordingWithSetTargetVideoEncodingBitRateOutOfRange() {
        testRecorderIsConfiguredBasedOnTargetVideoEncodingBitrate(1000_000_000)
        verifyConfiguredVideoBitrate()
    }

    @Test
    fun recordingWithNegativeBitRate() {
        initializeRecorder()
        assertThrows(IllegalArgumentException::class.java) {
            Recorder.Builder().setTargetVideoEncodingBitRate(-5).build()
        }
    }

    @Test
    fun canRecordToMediaStore() {
        initializeRecorder()
        assumeTrue(
            "Ignore the test since the MediaStore.Video has compatibility issues.",
            DeviceQuirks.get(MediaStoreVideoCannotWrite::class.java) == null
        )
        invokeSurfaceRequest()
        val statusSemaphore = Semaphore(0)
        val finalizeSemaphore = Semaphore(0)
        val context: Context = ApplicationProvider.getApplicationContext()
        val contentResolver: ContentResolver = context.contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        }

        val outputOptions = MediaStoreOutputOptions.Builder(
            contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(contentValues).build()

        var uri: Uri = Uri.EMPTY
        val recording = recorder.prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor()) {
                if (it is VideoRecordEvent.Status) {
                    statusSemaphore.release()
                }
                if (it is VideoRecordEvent.Finalize) {
                    uri = it.outputResults.outputUri
                    finalizeSemaphore.release()
                }
            }

        assertThat(statusSemaphore.tryAcquire(5, 15000L, TimeUnit.MILLISECONDS)).isTrue()

        recording.stopSafely()

        // Wait for the recording to complete.
        assertThat(finalizeSemaphore.tryAcquire(GENERAL_TIMEOUT, TimeUnit.MILLISECONDS)).isTrue()

        assertThat(uri).isNotEqualTo(Uri.EMPTY)

        checkFileHasAudioAndVideo(uri)

        contentResolver.delete(uri, null, null)
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun canRecordToFileDescriptor() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }
        val pfd = ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_WRITE
        )
        val recording = recorder
            .prepareRecording(context, FileDescriptorOutputOptions.Builder(pfd).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        // ParcelFileDescriptor should be safe to close after PendingRecording#start.
        pfd.close()

        mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

        recording.stopSafely()

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true,
            GENERAL_TIMEOUT
        )

        checkFileHasAudioAndVideo(Uri.fromFile(file))

        file.delete()
    }

    @Test
    @SdkSuppress(minSdkVersion = 26)
    fun recordToFileDescriptor_withClosedFileDescriptor_receiveError() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)

        pfd.close()

        recorder.prepareRecording(context, FileDescriptorOutputOptions.Builder(pfd).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        // Check the output Uri from the finalize event match the Uri from the given file.
        val captor = ArgumentCaptorCameraX<VideoRecordEvent> { argument ->
            VideoRecordEvent::class.java.isInstance(
                argument
            )
        }
        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent::class.java,
            false,
            CallTimesAtLeast(1),
            captor
        )

        val finalize = captor.value as VideoRecordEvent.Finalize
        assertThat(finalize.error).isEqualTo(ERROR_INVALID_OUTPUT_OPTIONS)

        file.delete()
    }

    @Test
    @SdkSuppress(minSdkVersion = 21, maxSdkVersion = 25)
    @SuppressLint("NewApi") // Intentionally testing behavior of calling from invalid API level
    fun prepareRecordingWithFileDescriptor_throwsExceptionBeforeApi26() {
        initializeRecorder()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }
        ParcelFileDescriptor.open(
            file,
            ParcelFileDescriptor.MODE_READ_WRITE
        ).use { pfd ->
            assertThrows(UnsupportedOperationException::class.java) {
                recorder.prepareRecording(context, FileDescriptorOutputOptions.Builder(pfd).build())
            }
        }

        file.delete()
    }

    @Test
    fun canPauseResume() {
        initializeRecorder()
        invokeSurfaceRequest()

        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer).apply {
                pause()

                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Pause::class.java,
                    true, GENERAL_TIMEOUT
                )

                resume()

                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Resume::class.java,
                    true, GENERAL_TIMEOUT
                )
                // Check there are data being encoded after resuming.
                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Status::class.java,
                    true, STATUS_TIMEOUT, CallTimesAtLeast(5)
                )

                stopSafely()
            }

        // Wait for the recording to be finalized.
        mockVideoRecordEventConsumer.verifyAcceptCall(VideoRecordEvent.Finalize::class.java,
            true, GENERAL_TIMEOUT)

        checkFileHasAudioAndVideo(Uri.fromFile(file))

        file.delete()
    }

    @Test
    fun canStartRecordingPaused_whenRecorderInitializing() {
        initializeRecorder()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer).apply {
                pause()

                // Only invoke surface request after pause() has been called
                invokeSurfaceRequest()

                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Start::class.java,
                    true, GENERAL_TIMEOUT
                )
                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Pause::class.java,
                    true, GENERAL_TIMEOUT
                )

                stopSafely()
            }

        file.delete()
    }

    @Test
    fun canReceiveRecordingStats() {
        initializeRecorder()
        invokeSurfaceRequest()

        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        // Start
        recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer).apply {
                mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

                pause()

                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Pause::class.java,
                    true, GENERAL_TIMEOUT
                )

                resume()

                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Resume::class.java,
                    true, GENERAL_TIMEOUT
                )
                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Status::class.java,
                    true, STATUS_TIMEOUT, CallTimesAtLeast(5)
                )

                stopSafely()

                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Finalize::class.java,
                    true, GENERAL_TIMEOUT
                )
            }

        val captor = ArgumentCaptorCameraX<VideoRecordEvent> { argument ->
            VideoRecordEvent::class.java.isInstance(
                argument
            )
        }
        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent::class.java,
            false,
            CallTimesAtLeast(1),
            captor
        )

        captor.allValues.run {
            assertThat(size).isAtLeast(
                (
                    1 /* Start */ +
                        5 /* Status */ +
                        1 /* Pause */ +
                        1 /* Resume */ +
                        5 /* Status */ +
                        1 /* Stop */
                    )
            )

            // Ensure duration and bytes are increasing
            take(size - 1).mapIndexed { index, _ ->
                Pair(get(index).recordingStats, get(index + 1).recordingStats)
            }.forEach { (former: RecordingStats, latter: RecordingStats) ->
                assertThat(former.numBytesRecorded).isAtMost(latter.numBytesRecorded)
                assertThat(former.recordedDurationNanos).isAtMost((latter.recordedDurationNanos))
            }

            // Ensure they are not all zero by checking last stats
            last().recordingStats.also {
                assertThat(it.numBytesRecorded).isGreaterThan(0L)
                assertThat(it.recordedDurationNanos).isGreaterThan(0L)
            }
        }

        file.delete()
    }

    @Test
    fun setFileSizeLimit() {
        initializeRecorder()
        val fileSizeLimit = 500L * 1024L // 500 KB
        runFileSizeLimitTest(fileSizeLimit)
    }

    // Sets the file size limit to 1 byte, which will be lower than the initial data sent from
    // the encoder. This will ensure that the recording will be finalized even if it has no data
    // written to it.
    @Test
    fun setFileSizeLimitLowerThanInitialDataSize() {
        initializeRecorder()
        val fileSizeLimit = 1L // 1 byte
        runFileSizeLimitTest(fileSizeLimit)
    }

    @Test
    fun setLocation() {
        initializeRecorder()
        runLocationTest(createLocation(25.033267462243586, 121.56454121737946))
    }

    @Test
    fun setNegativeLocation() {
        initializeRecorder()
        runLocationTest(createLocation(-27.14394722411734, -109.33053675296067))
    }

    @Test
    fun stop_withErrorWhenDurationLimitReached() {
        initializeRecorder()
        val videoRecordEventListener = MockConsumer<VideoRecordEvent>()
        invokeSurfaceRequest()
        val durationLimitMs = 3000L
        val durationTolerance = 50L
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }
        val outputOptions = FileOutputOptions.Builder(file)
            .setDurationLimitMillis(durationLimitMs)
            .build()

        val recording = recorder
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), videoRecordEventListener)

        // The recording should be finalized after the specified duration limit plus some time
        // for processing it.
        videoRecordEventListener.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            false,
            durationLimitMs + 2000L
        )

        val captor = ArgumentCaptorCameraX<VideoRecordEvent> {
                argument -> VideoRecordEvent::class.java.isInstance(argument)
        }
        videoRecordEventListener.verifyAcceptCall(VideoRecordEvent::class.java,
            false, CallTimesAtLeast(1), captor)

        val finalize = captor.value as VideoRecordEvent.Finalize
        assertThat(finalize.error).isEqualTo(ERROR_DURATION_LIMIT_REACHED)
        assertThat(finalize.recordingStats.recordedDurationNanos)
            .isAtMost(TimeUnit.MILLISECONDS.toNanos(durationLimitMs + durationTolerance))
        checkDurationAtMost(Uri.fromFile(file), durationLimitMs)

        recording.stopSafely()
        file.delete()
    }

    @Test
    fun checkStreamState() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        @Suppress("UNCHECKED_CAST")
        val streamInfoObserver =
            mock(Observable.Observer::class.java) as Observable.Observer<StreamInfo>
        val inOrder = inOrder(streamInfoObserver)
        recorder.streamInfo.addObserver(CameraXExecutors.directExecutor(), streamInfoObserver)

        // Recorder should start in INACTIVE stream state before any recordings
        inOrder.verify(streamInfoObserver, timeout(5000L)).onNewData(
            argThat {
                it!!.streamState == StreamInfo.StreamState.INACTIVE
            }
        )

        // Start
        val recording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)
        // Starting recording should move Recorder to ACTIVE stream state
        inOrder.verify(streamInfoObserver, timeout(5000L)).onNewData(
            argThat {
                it!!.streamState == StreamInfo.StreamState.ACTIVE
            }
        )

        recording.stopSafely()

        // Stopping recording should eventually move to INACTIVE stream state
        inOrder.verify(streamInfoObserver, timeout(GENERAL_TIMEOUT)).onNewData(
            argThat {
                it!!.streamState == StreamInfo.StreamState.INACTIVE
            }
        )

        file.delete()
    }

    @Test
    fun start_throwsExceptionWhenActive() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }
        val outputOptions = FileOutputOptions.Builder(file).build()

        val recording = recorder.prepareRecording(context, outputOptions).start(
            CameraXExecutors.directExecutor()
        ) {}

        val pendingRecording = recorder.prepareRecording(context, outputOptions)
        assertThrows(java.lang.IllegalStateException::class.java) {
            pendingRecording.start(CameraXExecutors.directExecutor()) {}
        }

        recording.close()
        file.delete()
    }

    @Test
    fun start_whenSourceActiveNonStreaming() {
        initializeRecorder()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        recorder.onSourceStateChanged(VideoOutput.SourceState.ACTIVE_NON_STREAMING)

        val recording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        invokeSurfaceRequest()

        mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

        recording.stopSafely()
        // Wait for the recording to be finalized.
        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true, GENERAL_TIMEOUT
        )

        file.delete()
    }

    @Test
    fun start_finalizeImmediatelyWhenSourceInactive() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        recorder.onSourceStateChanged(VideoOutput.SourceState.INACTIVE)

        val recording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            false, GENERAL_TIMEOUT
        )
        mockVideoRecordEventConsumer.verifyNoMoreAcceptCalls(false)

        val captor = ArgumentCaptorCameraX<VideoRecordEvent> { argument ->
            VideoRecordEvent::class.java.isInstance(
                argument
            )
        }
        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent::class.java,
            false,
            CallTimesAtLeast(1),
            captor
        )

        val finalize = captor.value as VideoRecordEvent.Finalize
        assertThat(finalize.error).isEqualTo(ERROR_SOURCE_INACTIVE)

        recording.stopSafely()

        file.delete()
    }

    @Test
    fun pause_whenSourceActiveNonStreaming() {
        initializeRecorder()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        recorder.onSourceStateChanged(VideoOutput.SourceState.ACTIVE_NON_STREAMING)

        recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer).apply {
                pause()

                invokeSurfaceRequest()

                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Start::class.java,
                    true, GENERAL_TIMEOUT
                )
                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Pause::class.java,
                    true, GENERAL_TIMEOUT
                )

                stopSafely()
            }

        // Wait for the recording to be finalized.
        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true, GENERAL_TIMEOUT
        )

        // If the recording is paused immediately after being started, the recording should be
        // finalized with ERROR_NO_VALID_DATA.
        val captor = ArgumentCaptorCameraX<VideoRecordEvent> { argument ->
            VideoRecordEvent::class.java.isInstance(
                argument
            )
        }
        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent::class.java,
            false,
            CallTimesAtLeast(1),
            captor
        )

        val finalize = captor.value as VideoRecordEvent.Finalize
        assertThat(finalize.error).isEqualTo(ERROR_NO_VALID_DATA)

        file.delete()
    }

    @Test
    fun pause_noOpWhenAlreadyPaused() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer).apply {
                mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

                pause()

                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Pause::class.java,
                    true, GENERAL_TIMEOUT
                )

                pause()

                stopSafely()
            }

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true, GENERAL_TIMEOUT
        )

        // As described in b/197416199, there might be encoded data in flight which will trigger
        // Status event after pausing. So here it checks there's only one Pause event.
        val captor = ArgumentCaptorCameraX<VideoRecordEvent> { argument ->
            VideoRecordEvent::class.java.isInstance(
                argument
            )
        }
        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent::class.java,
            false,
            CallTimesAtLeast(1),
            captor
        )

        assertThat(captor.allValues.count { it is VideoRecordEvent.Pause }).isAtMost(1)

        file.delete()
    }

    @Test
    fun pause_throwsExceptionWhenStopping() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        val recording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

        recording.stopSafely()

        assertThrows(IllegalStateException::class.java) {
            recording.pause()
        }

        file.delete()
    }

    @Test
    fun resume_noOpWhenNotPaused() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        val recording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

        // Calling resume shouldn't affect the stream of status events finally followed
        // by a finalize event. There shouldn't be another resume event generated.
        recording.resume()

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Status::class.java,
            true,
            STATUS_TIMEOUT,
            CallTimesAtLeast(5)
        )

        recording.stopSafely()

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true, GENERAL_TIMEOUT
        )

        // Ensure no resume events were ever sent.
        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Resume::class.java,
            false,
            GENERAL_TIMEOUT,
            CallTimes(0)
        )

        file.delete()
    }

    @Test
    fun resume_throwsExceptionWhenStopping() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        val recording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

        recording.stopSafely()

        assertThrows(IllegalStateException::class.java) {
            recording.resume()
        }

        file.delete()
    }

    @Test
    fun stop_beforeSurfaceRequested() {
        initializeRecorder()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        val recording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        recording.pause()

        recording.stopSafely()

        invokeSurfaceRequest()

        val captor = ArgumentCaptorCameraX<VideoRecordEvent> { argument ->
            VideoRecordEvent::class.java.isInstance(
                argument
            )
        }
        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent::class.java,
            false,
            CallTimesAtLeast(1),
            captor
        )

        val finalize = captor.value as VideoRecordEvent.Finalize
        assertThat(finalize.error).isEqualTo(ERROR_NO_VALID_DATA)

        file.delete()
    }

    @Test
    fun stop_fromAutoCloseable() {
        initializeRecorder()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        // Recording will be stopped by AutoCloseable.close() upon exiting use{} block
        val pendingRecording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
        pendingRecording.start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)
            .use {
                invokeSurfaceRequest()
                mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()
            }

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true, GENERAL_TIMEOUT
        )

        file.delete()
    }

    @Test
    fun stop_WhenUseCaseDetached() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        val recording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .withAudioEnabled()
                .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

        instrumentation.runOnMainSync {
            cameraUseCaseAdapter.removeUseCases(listOf(preview))
        }

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true, GENERAL_TIMEOUT
        )

        recording.stopSafely()
        file.delete()
    }

    @Suppress("UNUSED_VALUE", "ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
    @Test
    fun stop_whenRecordingIsGarbageCollected() {
        initializeRecorder()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        var recording: Recording? = recorder
            .prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        // First ensure the recording gets some status events
        invokeSurfaceRequest()

        mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

        // Remove reference to recording and run GC. The recording should be stopped once
        // the Recording's finalizer runs.
        recording = null
        GarbageCollectionUtil.runFinalization()

        // Ensure the event listener gets a finalize event. Note: the word "finalize" is very
        // overloaded here. This event means the recording has finished, but does not relate to the
        // finalizer that runs during garbage collection. However, that is what causes the
        // recording to finish.
        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true, GENERAL_TIMEOUT
        )

        file.delete()
    }

    @Test
    fun stop_noOpWhenStopping() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer).apply {
                mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

                stopSafely()
                stopSafely()
            }

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true, GENERAL_TIMEOUT
        )
        mockVideoRecordEventConsumer.verifyNoMoreAcceptCalls(true)

        file.delete()
    }

    @Test
    fun optionsOverridesDefaults() {
        initializeRecorder()
        val qualitySelector = QualitySelector.from(Quality.HIGHEST)
        val recorder = Recorder.Builder()
            .setQualitySelector(qualitySelector)
            .build()

        assertThat(recorder.qualitySelector).isEqualTo(qualitySelector)
    }

    @Test
    fun canRetrieveProvidedExecutorFromRecorder() {
        initializeRecorder()
        val myExecutor = Executor { command -> command?.run() }
        val recorder = Recorder.Builder()
            .setExecutor(myExecutor)
            .build()

        assertThat(recorder.executor).isSameInstanceAs(myExecutor)
    }

    @Test
    fun cannotRetrieveExecutorWhenExecutorNotProvided() {
        initializeRecorder()
        val recorder = Recorder.Builder().build()

        assertThat(recorder.executor).isNull()
    }

    @Test
    fun canRecordWithoutAudio() {
        initializeRecorder()
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        val recording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

        // Check the audio information reports state as disabled.
        val captor = ArgumentCaptorCameraX<VideoRecordEvent> { argument ->
            VideoRecordEvent::class.java.isInstance(
                argument
            )
        }

        mockVideoRecordEventConsumer.verifyAcceptCall(VideoRecordEvent::class.java,
            false, CallTimesAtLeast(1), captor)

        assertThat(captor.value).isInstanceOf(VideoRecordEvent.Status::class.java)
        val status = captor.value as VideoRecordEvent.Status
        assertThat(status.recordingStats.audioStats.audioState)
            .isEqualTo(AudioStats.AUDIO_STATE_DISABLED)

        recording.stopSafely()

        mockVideoRecordEventConsumer.verifyAcceptCall(VideoRecordEvent.Finalize::class.java,
            false, GENERAL_TIMEOUT)

        checkFileAudio(Uri.fromFile(file), false)
        checkFileVideo(Uri.fromFile(file), true)

        file.delete()
    }

    @Test
    fun cannotStartMultiplePendingRecordingsWhileInitializing() {
        initializeRecorder()
        val file1 = File.createTempFile("CameraX1", ".tmp").apply { deleteOnExit() }
        val file2 = File.createTempFile("CameraX2", ".tmp").apply { deleteOnExit() }
        try {
            // We explicitly do not invoke the surface request so the recorder is initializing.
            recorder.prepareRecording(context, FileOutputOptions.Builder(file1).build())
                .start(CameraXExecutors.directExecutor()) {}
                .apply {
                    assertThrows<IllegalStateException> {
                        recorder.prepareRecording(context, FileOutputOptions.Builder(file2).build())
                            .start(CameraXExecutors.directExecutor()) {}
                    }
                    stopSafely()
                }
        } finally {
            file1.delete()
            file2.delete()
        }
    }

    @Test
    fun canRecoverFromErrorState(): Unit = runBlocking {
        initializeRecorder()
        // Create a video encoder factory that will fail on first 2 create encoder requests.
        // Recorder initialization should fail by 1st encoder creation fail.
        // 1st recording request should fail by 2nd encoder creation fail.
        // 2nd recording request should be successful.
        var createEncoderRequestCount = 0
        val recorder = Recorder.Builder()
            .setVideoEncoderFactory { executor, config ->
                if (createEncoderRequestCount < 2) {
                    createEncoderRequestCount++
                    throw InvalidConfigException("Create video encoder fail on purpose.")
                } else {
                    Recorder.DEFAULT_ENCODER_FACTORY.createEncoder(executor, config)
                }
            }.build().apply { onSourceStateChanged(VideoOutput.SourceState.INACTIVE) }

        invokeSurfaceRequest(recorder)
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        // Wait STREAM_ID_ERROR which indicates Recorder enter the error state.
        withTimeoutOrNull(3000) {
            recorder.streamInfo.asFlow().dropWhile { it!!.id != StreamInfo.STREAM_ID_ERROR }.first()
        } ?: fail("Do not observe STREAM_ID_ERROR from StreamInfo observer.")

        // 1st recording request
        recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer).let {
                val captor = ArgumentCaptorCameraX<VideoRecordEvent> { argument ->
                    VideoRecordEvent::class.java.isInstance(
                        argument
                    )
                }

                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent::class.java,
                    false,
                    3000L,
                    CallTimesAtLeast(1),
                    captor
                )

                val finalize = captor.value as VideoRecordEvent.Finalize
                assertThat(finalize.error).isEqualTo(ERROR_RECORDER_ERROR)
            }

        // 2nd recording request
        mockVideoRecordEventConsumer.clearAcceptCalls()
        recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer).let {
                mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

                it.stopSafely()

                mockVideoRecordEventConsumer.verifyAcceptCall(
                    VideoRecordEvent.Finalize::class.java,
                    true, GENERAL_TIMEOUT
                )
            }

        file.delete()
    }

    @Test
    @SdkSuppress(minSdkVersion = 31)
    fun audioRecordIsAttributed() = runBlocking {
        initializeRecorder()
        val notedTag = CompletableDeferred<String>()
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        appOps.setOnOpNotedCallback(
            Dispatchers.Main.asExecutor(),
            object : AppOpsManager.OnOpNotedCallback() {
                override fun onNoted(p0: SyncNotedAppOp) {
                    // no-op. record_audio should be async.
                }

                override fun onSelfNoted(p0: SyncNotedAppOp) {
                    // no-op. record_audio should be async.
                }

                override fun onAsyncNoted(noted: AsyncNotedAppOp) {
                    if (AppOpsManager.OPSTR_RECORD_AUDIO == noted.op &&
                        TEST_ATTRIBUTION_TAG == noted.attributionTag
                    ) {
                        notedTag.complete(noted.attributionTag!!)
                    }
                }
            })

        var recording: Recording? = null
        try {
            val attributionContext = context.createAttributionContext(TEST_ATTRIBUTION_TAG)
            invokeSurfaceRequest()
            val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

            recording =
                recorder.prepareRecording(
                    attributionContext, FileOutputOptions.Builder(file).build()
                )
                    .withAudioEnabled()
                    .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

            val timeoutDuration = 5.seconds
            withTimeoutOrNull(timeoutDuration) {
                assertThat(notedTag.await()).isEqualTo(TEST_ATTRIBUTION_TAG)
            } ?: fail("Timed out waiting for attribution tag. Waited $timeoutDuration.")
        } finally {
            appOps.setOnOpNotedCallback(null, null)
            recording?.stopSafely()
        }
    }

    private fun invokeSurfaceRequest() {
        invokeSurfaceRequest(recorder)
    }

    private fun invokeSurfaceRequest(recorder: Recorder) {
        instrumentation.runOnMainSync {
            preview.setSurfaceProvider { request: SurfaceRequest ->
                recorder.onSurfaceRequested(request)
            }
            recorder.onSourceStateChanged(VideoOutput.SourceState.ACTIVE_STREAMING)
        }
    }

    private fun initializeRecorder(bitrate: Int = BITRATE_AUTO) {
        recorder = Recorder.Builder().apply {
            if (bitrate != BITRATE_AUTO) {
                setTargetVideoEncodingBitRate(bitrate)
            }
        }.build()
        recorder.onSourceStateChanged(VideoOutput.SourceState.ACTIVE_NON_STREAMING)
    }

    private fun testRecorderIsConfiguredBasedOnTargetVideoEncodingBitrate(
        bitrate: Int,
        enableAudio: Boolean = false
    ) {
        initializeRecorder(bitrate)
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }

        val recording =
            recorder.prepareRecording(context, FileOutputOptions.Builder(file).build())
                .apply { if (enableAudio) withAudioEnabled() }
                .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

        recording.stopSafely()

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true,
            GENERAL_TIMEOUT
        )

        val uri = Uri.fromFile(file)
        if (enableAudio) {
            checkFileHasAudioAndVideo(uri)
        } else {
            checkFileVideo(uri, true)
        }

        // Check the output Uri from the finalize event match the Uri from the given file.
        val captor = ArgumentCaptorCameraX<VideoRecordEvent> { argument ->
            VideoRecordEvent::class.java.isInstance(
                argument
            )
        }

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent::class.java,
            false,
            CallTimesAtLeast(1),
            captor
        )

        val finalize = captor.value as VideoRecordEvent.Finalize
        assertThat(finalize.outputResults.outputUri).isEqualTo(uri)

        file.delete()
    }

    private fun verifyConfiguredVideoBitrate() {
        assertThat(recorder.mFirstRecordingVideoBitrate).isIn(
            com.google.common.collect.Range.closed(
                recorder.mVideoEncoderBitrateRange.lower,
                recorder.mVideoEncoderBitrateRange.upper
            )
        )
    }

    private fun checkFileHasAudioAndVideo(uri: Uri) {
        checkFileAudio(uri, true)
        checkFileVideo(uri, true)
    }

    private fun checkFileAudio(uri: Uri, hasAudio: Boolean) {
        MediaMetadataRetriever().apply {
            try {
                setDataSource(context, uri)
                val value = extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)

                assertThat(value).isEqualTo(
                    if (hasAudio) {
                        "yes"
                    } else {
                        null
                    }
                )
            } finally {
                release()
            }
        }
    }

    private fun checkFileVideo(uri: Uri, hasVideo: Boolean) {
        MediaMetadataRetriever().apply {
            try {
                setDataSource(context, uri)
                val value = extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)

                assertThat(value).isEqualTo(
                    if (hasVideo) {
                        "yes"
                    } else {
                        null
                    }
                )
            } finally {
                release()
            }
        }
    }

    private fun checkLocation(uri: Uri, location: Location) {
        MediaMetadataRetriever().apply {
            try {
                setDataSource(context, uri)
                // Only test on mp4 output format, others will be ignored.
                val mime = extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                assumeTrue("Unsupported mime = $mime",
                    "video/mp4".equals(mime, ignoreCase = true))
                val value = extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
                assertThat(value).isNotNull()
                // ex: (90, 180) => "+90.0000+180.0000/" (ISO-6709 standard)
                val matchGroup =
                    "([\\+-]?[0-9]+(\\.[0-9]+)?)([\\+-]?[0-9]+(\\.[0-9]+)?)".toRegex()
                        .find(value!!) ?: fail("Fail on checking location metadata: $value")
                val lat = matchGroup.groupValues[1].toDouble()
                val lon = matchGroup.groupValues[3].toDouble()

                // MediaMuxer.setLocation rounds the value to 4 decimal places
                val tolerance = 0.0001
                assertWithMessage("Fail on latitude. $lat($value) vs ${location.latitude}")
                    .that(lat).isWithin(tolerance).of(location.latitude)
                assertWithMessage("Fail on longitude. $lon($value) vs ${location.longitude}")
                    .that(lon).isWithin(tolerance).of(location.longitude)
            } finally {
                release()
            }
        }
    }

    private fun checkDurationAtMost(uri: Uri, duration: Long) {
        MediaMetadataRetriever().apply {
            try {
                setDataSource(context, uri)
                val durationFromFile = extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)

                assertThat(durationFromFile).isNotNull()
                assertThat(durationFromFile!!.toLong()).isAtMost(duration)
            } finally {
                release()
            }
        }
    }

    // It fails on devices with certain chipset if the codec is stopped when the camera is still
    // producing frames to the provided surface. This method first stop the camera from
    // producing frames then stops the recording safely on the problematic devices.
    private fun Recording.stopSafely() {
        val deactivateSurfaceBeforeStop =
            DeviceQuirks.get(DeactivateEncoderSurfaceBeforeStopEncoderQuirk::class.java) != null
        if (deactivateSurfaceBeforeStop) {
            instrumentation.runOnMainSync {
                preview.setSurfaceProvider(null)
            }
        }
        stop()
        if (deactivateSurfaceBeforeStop && Build.VERSION.SDK_INT >= 23) {
            invokeSurfaceRequest()
        }
    }

    private fun runFileSizeLimitTest(fileSizeLimit: Long) {
        // For the file size is small, the final file length possibly exceeds the file size limit
        // after adding the file header. We still add the buffer for the tolerance of comparing the
        // file length and file size limit.
        val sizeLimitBuffer = 50 * 1024 // 50k threshold buffer
        invokeSurfaceRequest()
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }
        val outputOptions = FileOutputOptions.Builder(file)
            .setFileSizeLimit(fileSizeLimit)
            .build()

        val recording = recorder
            .prepareRecording(context, outputOptions)
            .withAudioEnabled()
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            false, 60000L
        )

        val captor = ArgumentCaptorCameraX<VideoRecordEvent> {
                argument -> VideoRecordEvent::class.java.isInstance(argument)
        }
        mockVideoRecordEventConsumer.verifyAcceptCall(VideoRecordEvent::class.java,
            false, CallTimesAtLeast(1), captor)

        assertThat(captor.value).isInstanceOf(VideoRecordEvent.Finalize::class.java)
        val finalize = captor.value as VideoRecordEvent.Finalize
        assertThat(finalize.error).isEqualTo(ERROR_FILE_SIZE_LIMIT_REACHED)
        assertThat(file.length()).isLessThan(fileSizeLimit + sizeLimitBuffer)

        recording.stopSafely()

        file.delete()
    }

    private fun runLocationTest(location: Location) {
        invokeSurfaceRequest(recorder)
        val file = File.createTempFile("CameraX", ".tmp").apply { deleteOnExit() }
        val outputOptions = FileOutputOptions.Builder(file)
            .setLocation(location)
            .build()

        val recording = recorder
            .prepareRecording(context, outputOptions)
            .start(CameraXExecutors.directExecutor(), mockVideoRecordEventConsumer)

        mockVideoRecordEventConsumer.verifyRecordingStartSuccessfully()

        recording.stopSafely()

        mockVideoRecordEventConsumer.verifyAcceptCall(
            VideoRecordEvent.Finalize::class.java,
            true, GENERAL_TIMEOUT
        )

        checkLocation(Uri.fromFile(file), location)

        file.delete()
    }

    private fun createLocation(
        latitude: Double,
        longitude: Double,
        provider: String = "FakeProvider"
    ): Location =
        Location(provider).apply {
            this.latitude = latitude
            this.longitude = longitude
        }

    private fun MockConsumer<VideoRecordEvent>.verifyRecordingStartSuccessfully() {
        verifyAcceptCall(
            VideoRecordEvent.Start::class.java,
            true,
            GENERAL_TIMEOUT
        )
        verifyAcceptCall(
            VideoRecordEvent.Status::class.java,
            true,
            STATUS_TIMEOUT,
            CallTimesAtLeast(5)
        )
    }
}

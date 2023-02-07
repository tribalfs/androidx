/*
 * Copyright 2022 The Android Open Source Project
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
package androidx.wear.watchface.complications.datasource

import android.support.wearable.complications.ComplicationData as WireComplicationData
import android.content.Intent
import android.content.res.Resources
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.RemoteException
import android.support.wearable.complications.IComplicationManager
import android.support.wearable.complications.IComplicationProvider
import android.util.Log
import androidx.wear.protolayout.expression.DynamicBuilders
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationTextExpression
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import com.google.common.truth.Truth.assertThat
import java.time.Instant
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.argThat
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.verify
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.robolectric.annotation.Config
import org.robolectric.annotation.internal.DoNotInstrument
import org.robolectric.shadows.ShadowLog
import org.robolectric.shadows.ShadowLooper.runUiThreadTasks

/** Tests for [ComplicationDataSourceService]. */
@RunWith(ComplicationsTestRunner::class)
@DoNotInstrument
class ComplicationDataSourceServiceTest {
    private var mPretendMainThread = HandlerThread("testThread")
    private lateinit var mPretendMainThreadHandler: Handler

    private val mRemoteManager = mock<IComplicationManager>()
    private val mUpdateComplicationDataLatch = CountDownLatch(1)
    private val mLocalManager: IComplicationManager.Stub = object : IComplicationManager.Stub() {
        override fun updateComplicationData(complicationSlotId: Int, data: WireComplicationData?) {
            mRemoteManager.updateComplicationData(complicationSlotId, data)
            mUpdateComplicationDataLatch.countDown()
        }
    }
    private lateinit var mProvider: IComplicationProvider.Stub

    /**
     * Mock implementation of ComplicationDataSourceService.
     *
     * Can't use Mockito because it doesn't like partially implemented classes.
     */
    private inner class MockComplicationDataSourceService : ComplicationDataSourceService() {
        var respondWithTimeline = false

        /**
         * Will be used to invoke [.ComplicationRequestListener.onComplicationData] on
         * [onComplicationRequest].
         */
        var responseData: ComplicationData? = null

        /**
         * Will be used to invoke [.ComplicationRequestListener.onComplicationDataTimeline] on
         * [onComplicationRequest], if [respondWithTimeline] is true.
         */
        var responseDataTimeline: ComplicationDataTimeline? = null

        /** Last request provided to [onComplicationRequest]. */
        var lastRequest: ComplicationRequest? = null

        /** Will be returned from [previewData]. */
        var previewData: ComplicationData? = null

        /** Last type provided to [previewData]. */
        var lastPreviewType: ComplicationType? = null

        override fun createMainThreadHandler(): Handler = mPretendMainThreadHandler

        override fun onComplicationRequest(
            request: ComplicationRequest,
            listener: ComplicationRequestListener
        ) {
            lastRequest = request
            try {
                if (respondWithTimeline) {
                    listener.onComplicationDataTimeline(responseDataTimeline)
                } else {
                    listener.onComplicationData(responseData)
                }
            } catch (e: RemoteException) {
                Log.e(TAG, "onComplicationRequest failed with error: ", e)
            }
        }

        override fun getPreviewData(type: ComplicationType): ComplicationData? {
            lastPreviewType = type
            return previewData
        }
    }

    private val mService = MockComplicationDataSourceService()

    @Before
    fun setUp() {
        ShadowLog.setLoggable("ComplicationData", Log.DEBUG)
        mProvider = mService.onBind(
            Intent(ComplicationDataSourceService.ACTION_COMPLICATION_UPDATE_REQUEST)
        ) as IComplicationProvider.Stub

        mPretendMainThread.start()
        mPretendMainThreadHandler = Handler(mPretendMainThread.looper)
    }

    @After
    fun tearDown() {
        mPretendMainThread.quitSafely()
    }

    @Test
    fun testOnComplicationRequest() {
        mService.responseData = LongTextComplicationData.Builder(
            PlainComplicationText.Builder("hello").build(),
            ComplicationText.EMPTY
        ).build()
        val id = 123
        mProvider.onUpdate(id, ComplicationType.LONG_TEXT.toWireComplicationType(), mLocalManager)
        assertThat(mUpdateComplicationDataLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue()

        val data = argumentCaptor<WireComplicationData>()
        verify(mRemoteManager).updateComplicationData(eq(id), data.capture())
        assertThat(data.firstValue.longText!!.getTextAt(Resources.getSystem(), 0))
            .isEqualTo("hello")
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun testOnComplicationRequestWithExpression_doesNotEvaluateExpression() {
        mService.responseData = LongTextComplicationData.Builder(
            ComplicationTextExpression(
                DynamicBuilders.DynamicString.constant("hello").concat(
                    DynamicBuilders.DynamicString.constant(" world")
                )
            ),
            ComplicationText.EMPTY
        ).build()
        mProvider.onUpdate(
            /* complicationInstanceId = */ 123,
            ComplicationType.LONG_TEXT.toWireComplicationType(),
            mLocalManager
        )

        assertThat(mUpdateComplicationDataLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue()
        verify(mRemoteManager).updateComplicationData(
            eq(123),
            eq(
                LongTextComplicationData.Builder(
                    ComplicationTextExpression(
                        DynamicBuilders.DynamicString.constant("hello").concat(
                            DynamicBuilders.DynamicString.constant(" world")
                        )
                    ),
                    ComplicationText.EMPTY
                ).build().asWireComplicationData()
            )
        )
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.S])
    fun testOnComplicationRequestWithExpressionPreT_evaluatesExpression() {
        mService.responseData = LongTextComplicationData.Builder(
            ComplicationTextExpression(
                DynamicBuilders.DynamicString.constant("hello").concat(
                    DynamicBuilders.DynamicString.constant(" world")
                )
            ),
            ComplicationText.EMPTY
        ).build()

        mProvider.onUpdate(
            /* complicationInstanceId = */ 123,
            ComplicationType.LONG_TEXT.toWireComplicationType(),
            mLocalManager
        )

        runUiThreadTasksWhileAwaitingDataLatch(1000)
        verify(mRemoteManager).updateComplicationData(
            eq(123),
            argThat { data ->
                data.longText!!.getTextAt(Resources.getSystem(), 0) == "hello world"
            })
    }

    @Test
    fun testOnComplicationRequestWrongType() {
        mService.responseData = LongTextComplicationData.Builder(
            PlainComplicationText.Builder("hello").build(),
            ComplicationText.EMPTY
        ).build()
        val id = 123
        val exception = AtomicReference<Throwable>()
        val exceptionLatch = CountDownLatch(1)

        mPretendMainThread.uncaughtExceptionHandler =
            Thread.UncaughtExceptionHandler { _, throwable ->
                exception.set(throwable)
                exceptionLatch.countDown()
            }
        mProvider.onUpdate(id, ComplicationType.SHORT_TEXT.toWireComplicationType(), mLocalManager)

        assertThat(exceptionLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue()
        assertThat(exception.get()).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun testOnComplicationRequestNoUpdateRequired() {
        mService.responseData = null

        val id = 123
        mProvider.onUpdate(id, ComplicationType.LONG_TEXT.toWireComplicationType(), mLocalManager)
        assertThat(mUpdateComplicationDataLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue()

        val data = argumentCaptor<WireComplicationData>()
        verify(mRemoteManager).updateComplicationData(eq(id), data.capture())
        assertThat(data.allValues).containsExactly(null)
    }

    @Test
    fun testGetComplicationPreviewData() {
        mService.previewData = LongTextComplicationData.Builder(
            PlainComplicationText.Builder("hello preview").build(),
            ComplicationText.EMPTY
        ).build()

        assertThat(
            mProvider.getComplicationPreviewData(
                ComplicationType.LONG_TEXT.toWireComplicationType()
            ).longText!!.getTextAt(Resources.getSystem(), 0)
        ).isEqualTo("hello preview")
    }

    @Test
    fun testTimelineTestService() {
        mService.respondWithTimeline = true
        val timeline = ArrayList<TimelineEntry>()
        timeline.add(
            TimelineEntry(
                TimeInterval(Instant.ofEpochSecond(1000), Instant.ofEpochSecond(4000)),
                LongTextComplicationData.Builder(
                    PlainComplicationText.Builder("A").build(),
                    ComplicationText.EMPTY
                ).build()
            )
        )
        timeline.add(
            TimelineEntry(
                TimeInterval(Instant.ofEpochSecond(6000), Instant.ofEpochSecond(8000)),
                LongTextComplicationData.Builder(
                    PlainComplicationText.Builder("B").build(),
                    ComplicationText.EMPTY
                ).build()
            )
        )
        mService.responseDataTimeline = ComplicationDataTimeline(
            LongTextComplicationData.Builder(
                PlainComplicationText.Builder("default").build(),
                ComplicationText.EMPTY
            ).build(),
            timeline
        )

        val id = 123
        mProvider.onUpdate(id, ComplicationType.LONG_TEXT.toWireComplicationType(), mLocalManager)
        assertThat(mUpdateComplicationDataLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue()
        val data = argumentCaptor<WireComplicationData>()
        verify(mRemoteManager)
            .updateComplicationData(eq(id), data.capture())
        assertThat(data.firstValue.longText!!.getTextAt(Resources.getSystem(), 0))
            .isEqualTo("default")
        val timeLineEntries: List<WireComplicationData?> = data.firstValue.timelineEntries!!
        assertThat(timeLineEntries.size).isEqualTo(2)
        assertThat(timeLineEntries[0]!!.timelineStartEpochSecond).isEqualTo(1000)
        assertThat(timeLineEntries[0]!!.timelineEndEpochSecond).isEqualTo(4000)
        assertThat(timeLineEntries[0]!!.longText!!.getTextAt(Resources.getSystem(), 0))
            .isEqualTo("A")

        assertThat(timeLineEntries[1]!!.timelineStartEpochSecond).isEqualTo(6000)
        assertThat(timeLineEntries[1]!!.timelineEndEpochSecond).isEqualTo(8000)
        assertThat(timeLineEntries[1]!!.longText!!.getTextAt(Resources.getSystem(), 0))
            .isEqualTo("B")
    }

    @Test
    fun testImmediateRequest() {
        mService.responseData = LongTextComplicationData.Builder(
            PlainComplicationText.Builder("hello").build(),
            ComplicationText.EMPTY
        ).build()
        val thread = HandlerThread("testThread")

        try {
            thread.start()
            val threadHandler = Handler(thread.looper)
            val response = AtomicReference<WireComplicationData>()
            val doneLatch = CountDownLatch(1)

            threadHandler.post {
                try {
                    response.set(
                        mProvider.onSynchronousComplicationRequest(
                            123,
                            ComplicationType.LONG_TEXT.toWireComplicationType()
                        )
                    )
                    doneLatch.countDown()
                } catch (e: RemoteException) {
                    // Should not happen
                }
            }

            assertThat(doneLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue()
            assertThat(response.get().longText!!.getTextAt(Resources.getSystem(), 0))
                .isEqualTo("hello")
        } finally {
            thread.quitSafely()
        }
    }

    private fun runUiThreadTasksWhileAwaitingDataLatch(timeout: Long) {
        // Allowing UI thread to execute while we wait for the data latch.
        var attempts: Long = 0
        while (!mUpdateComplicationDataLatch.await(1, TimeUnit.MILLISECONDS)) {
            runUiThreadTasks()
            assertThat(attempts++).isLessThan(timeout) // In total waiting ~timeout.
        }
    }

    companion object {
        private const val TAG = "ComplicationDataSourceServiceTest"
    }
}
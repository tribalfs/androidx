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

package androidx.camera.camera2.pipe.impl

import android.hardware.camera2.CaptureResult
import android.os.Build
import androidx.camera.camera2.pipe.FrameNumber
import androidx.camera.camera2.pipe.RequestNumber
import androidx.camera.camera2.pipe.Status3A
import androidx.camera.camera2.pipe.testing.CameraPipeRobolectricTestRunner
import androidx.camera.camera2.pipe.testing.FakeFrameMetadata
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(CameraPipeRobolectricTestRunner::class)
@Config(minSdk = Build.VERSION_CODES.LOLLIPOP)
@OptIn(ExperimentalCoroutinesApi::class)
class Result3AStateListenerTest {
    @Test
    fun testWithNoUpdate() {
        val listenerForKeys = Result3AStateListener(
            mapOf(
                CaptureResult.CONTROL_AF_STATE to
                    listOf(
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                    )
            )
        )
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()
    }

    @Test
    fun testKeyWithUndesirableValueInFrameMetadata() {
        val listenerForKeys = Result3AStateListener(
            mapOf(
                CaptureResult.CONTROL_AF_STATE to
                    listOf(
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                    )
            )
        )
        listenerForKeys.onRequestSequenceCreated(RequestNumber(1))

        val frameMetadata = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_ACTIVE_SCAN
            )
        )

        listenerForKeys.update(RequestNumber(1), frameMetadata)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()
    }

    @Test
    fun testKeyWithDesirableValueInFrameMetadata() {
        val listenerForKeys = Result3AStateListener(
            mapOf(
                CaptureResult.CONTROL_AF_STATE to
                    listOf(
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                    )
            )
        )
        listenerForKeys.onRequestSequenceCreated(RequestNumber(1))

        val frameMetadata = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
            )
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isTrue()
    }

    @Test
    fun testKeyNotPresentInFrameMetadata() {
        val listenerForKeys = Result3AStateListener(
            mapOf(
                CaptureResult.CONTROL_AF_STATE to
                    listOf(
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                    )
            )
        )
        listenerForKeys.onRequestSequenceCreated(RequestNumber(1))

        val frameMetadata = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AE_STATE to CaptureResult.CONTROL_AE_STATE_CONVERGED
            )
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()
    }

    @Test
    fun testMultipleKeysWithDesiredValuesInFrameMetadata() {
        val listenerForKeys = Result3AStateListener(
            mapOf(
                CaptureResult.CONTROL_AF_STATE to
                    listOf(
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                    ),
                CaptureResult.CONTROL_AE_STATE to listOf(CaptureResult.CONTROL_AE_STATE_LOCKED)
            )
        )
        listenerForKeys.onRequestSequenceCreated(RequestNumber(1))

        val frameMetadata = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                CaptureResult.CONTROL_AE_STATE to CaptureResult.CONTROL_AE_STATE_LOCKED
            )
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isTrue()
    }

    @Test
    fun testMultipleKeysWithDesiredValuesInFrameMetadataForASubset() {
        val listenerForKeys = Result3AStateListener(
            mapOf(
                CaptureResult.CONTROL_AF_STATE to
                    listOf(
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                    ),
                CaptureResult.CONTROL_AE_STATE to listOf(CaptureResult.CONTROL_AE_STATE_LOCKED)
            )
        )
        listenerForKeys.onRequestSequenceCreated(RequestNumber(1))

        val frameMetadata = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
            )
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()
    }

    @Test
    fun testMultipleUpdates() {
        val listenerForKeys = Result3AStateListener(
            mapOf(
                CaptureResult.CONTROL_AF_STATE to
                    listOf(
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                        CaptureResult.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED
                    ),
                CaptureResult.CONTROL_AE_STATE to listOf(CaptureResult.CONTROL_AE_STATE_LOCKED)
            )
        )
        listenerForKeys.onRequestSequenceCreated(RequestNumber(1))

        val frameMetadata = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
            )
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()

        val frameMetadata1 = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED,
                CaptureResult.CONTROL_AE_STATE to CaptureResult.CONTROL_AE_STATE_LOCKED
            )
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata1)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isTrue()
    }

    @Test
    fun testTimeLimit() {
        val listenerForKeys = Result3AStateListener(
            exitConditionForKeys = mapOf(
                CaptureResult.CONTROL_AF_STATE to
                    listOf(CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED)
            ),
            timeLimitNs = 1000000000L
        )
        listenerForKeys.onRequestSequenceCreated(RequestNumber(1))

        val frameMetadata1 = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
                CaptureResult.SENSOR_TIMESTAMP to 400000000L
            )
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata1)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()

        val frameMetadata2 = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
                CaptureResult.SENSOR_TIMESTAMP to 900000000L
            )
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata2)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()

        val frameMetadata3 = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
                CaptureResult.SENSOR_TIMESTAMP to 1500000000L
            )
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata3)
        val completedDeferred = listenerForKeys.getDeferredResult()
        assertThat(completedDeferred.isCompleted).isTrue()
        assertThat(completedDeferred.getCompleted().status).isEqualTo(Status3A.TIME_LIMIT_REACHED)
    }

    @Test
    fun testFrameLimit() {
        val listenerForKeys = Result3AStateListener(
            exitConditionForKeys = mapOf(
                CaptureResult.CONTROL_AF_STATE to
                    listOf(CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED)
            ),
            frameLimit = 10
        )
        listenerForKeys.onRequestSequenceCreated(RequestNumber(1))

        listenerForKeys.onRequestSequenceCreated(RequestNumber(1))

        val frameMetadata1 = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
                CaptureResult.SENSOR_TIMESTAMP to 400000000L
            ),
            frameNumber = FrameNumber(1)
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata1)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()

        val frameMetadata2 = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
                CaptureResult.SENSOR_TIMESTAMP to 900000000L
            ),
            frameNumber = FrameNumber(3)
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata2)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()

        val frameMetadata3 = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
                CaptureResult.SENSOR_TIMESTAMP to 1500000000L
            ),
            frameNumber = FrameNumber(10)
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata3)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()

        val frameMetadata4 = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_PASSIVE_SCAN,
                CaptureResult.SENSOR_TIMESTAMP to 1700000000L
            ),
            frameNumber = FrameNumber(12)
        )
        listenerForKeys.update(RequestNumber(1), frameMetadata4)
        val completedDeferred = listenerForKeys.getDeferredResult()

        assertThat(completedDeferred.isCompleted).isTrue()
        assertThat(completedDeferred.getCompleted().status).isEqualTo(Status3A.FRAME_LIMIT_REACHED)
    }

    @Test
    fun testIgnoreUpdatesFromEarlierRequests() {
        val listenerForKeys = Result3AStateListener(
            mapOf(
                CaptureResult.CONTROL_AF_STATE to
                    listOf(
                        CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
                    )
            )
        )

        val frameMetadata = FakeFrameMetadata(
            resultMetadata = mapOf(
                CaptureResult.CONTROL_AF_STATE to CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED
            )
        )
        // The reference request number of not yet set on the listener, so the update will be
        // ignored.
        listenerForKeys.update(RequestNumber(1), frameMetadata)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()

        // Update the reference request number for this listener.
        listenerForKeys.onRequestSequenceCreated(RequestNumber(3))

        // The update is coming from an earlier request so it will be ignored.
        listenerForKeys.update(RequestNumber(1), frameMetadata)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()

        // The update is coming from an earlier request so it will be ignored.
        listenerForKeys.update(RequestNumber(2), frameMetadata)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isFalse()

        // The update is from the same or later request number so it will be accepted.
        listenerForKeys.update(RequestNumber(3), frameMetadata)
        assertThat(listenerForKeys.getDeferredResult().isCompleted).isTrue()
    }
}
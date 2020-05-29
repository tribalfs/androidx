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

package androidx.ui.test.inputdispatcher

import android.view.MotionEvent
import androidx.test.filters.SmallTest
import androidx.ui.test.android.AndroidInputDispatcher
import androidx.ui.test.util.MotionEventRecorder
import androidx.ui.test.util.assertHasValidEventTimes
import androidx.ui.test.util.expectError
import androidx.ui.test.util.verify
import androidx.ui.unit.PxPosition
import androidx.ui.unit.px
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests if the [AndroidInputDispatcher.sendCancel] gesture works.
 */
@SmallTest
@RunWith(Parameterized::class)
class SendCancelTest(config: TestConfig) {
    data class TestConfig(
        val x: Float,
        val y: Float
    )

    companion object {
        private val downPosition = PxPosition(5.px, 5.px)

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun createTestSet(): List<TestConfig> {
            return listOf(0f, 10f).flatMap { x ->
                listOf(0f, -10f).map { y ->
                    TestConfig(x, y)
                }
            }.plus(TestConfig(downPosition.x.value, downPosition.y.value))
        }
    }

    private val dispatcherRule = AndroidInputDispatcher.TestRule(disableDispatchInRealTime = true)
    private val eventPeriod get() = dispatcherRule.eventPeriod

    @get:Rule
    val inputDispatcherRule: TestRule = dispatcherRule

    private val position = PxPosition(config.x.px, config.y.px)

    private val recorder = MotionEventRecorder()
    private val subject = AndroidInputDispatcher(recorder::recordEvent)

    @After
    fun tearDown() {
        recorder.disposeEvents()
    }

    @Test
    fun testSendCancel() {
        subject.sendDown(downPosition)
        subject.sendCancel(position)
        assertThat(subject.currentPosition).isNull()

        recorder.assertHasValidEventTimes()
        recorder.events.apply {
            assertThat(size).isEqualTo(2)
            first().verify(downPosition, MotionEvent.ACTION_DOWN, 0)
            last().verify(position, MotionEvent.ACTION_CANCEL, eventPeriod)
        }
    }
}

/**
 * Tests if the [AndroidInputDispatcher.sendCancel] gesture throws after
 * [AndroidInputDispatcher.sendUp] or [AndroidInputDispatcher.sendCancel] has been called.
 */
@SmallTest
class SendCancelAfterFinishedTest {
    private val downPosition = PxPosition(5.px, 5.px)
    private val position = PxPosition(1.px, 1.px)

    @get:Rule
    val inputDispatcherRule: TestRule = AndroidInputDispatcher.TestRule(
        disableDispatchInRealTime = true
    )

    private val subject = AndroidInputDispatcher {}

    @Test
    fun testCancelWithoutDown() {
        expectError<IllegalStateException> {
            subject.sendCancel(position)
        }
    }

    @Test
    fun testCancelAfterUp() {
        subject.sendDown(downPosition)
        subject.sendUp(downPosition)
        expectError<IllegalStateException> {
            subject.sendCancel(position)
        }
    }

    @Test
    fun testCancelAfterCancel() {
        subject.sendDown(downPosition)
        subject.sendCancel(downPosition)
        expectError<IllegalStateException> {
            subject.sendCancel(position)
        }
    }
}

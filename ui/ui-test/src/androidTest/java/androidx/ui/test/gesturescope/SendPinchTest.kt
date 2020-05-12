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

package androidx.ui.test.gesturescope

import androidx.test.filters.MediumTest
import androidx.ui.core.Modifier
import androidx.ui.layout.Stack
import androidx.ui.layout.fillMaxSize
import androidx.ui.test.createComposeRule
import androidx.ui.test.doGesture
import androidx.ui.test.findByTag
import androidx.ui.test.runOnIdleCompose
import androidx.ui.test.sendPinch
import androidx.ui.test.util.ClickableTestBox
import androidx.ui.test.util.PointerInputRecorder
import androidx.ui.test.util.assertTimestampsAreIncreasing
import androidx.ui.test.util.isMonotonicBetween
import androidx.ui.unit.PxPosition
import androidx.ui.unit.inMilliseconds
import androidx.ui.unit.milliseconds
import androidx.ui.unit.px
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.math.max

private const val TAG = "PINCH"

// TODO(b/146551983): Access from AndroidInputDispatcher
private val eventPeriod = 10.milliseconds.inMilliseconds()

@MediumTest
@RunWith(JUnit4::class)
class SendPinchTest {
    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    private val recorder = PointerInputRecorder()

    @Test
    fun pinch() {
        composeTestRule.setContent {
            Stack(Modifier.fillMaxSize()) {
                ClickableTestBox(modifier = recorder, tag = TAG)
            }
        }

        val start0 = PxPosition(40.px, 50.px)
        val end0 = PxPosition(8.px, 50.px)
        val start1 = PxPosition(60.px, 50.px)
        val end1 = PxPosition(92.px, 50.px)
        val duration = 400.milliseconds

        findByTag(TAG).doGesture {
            sendPinch(start0, end0, start1, end1, duration)
        }

        runOnIdleCompose {
            recorder.run {
                assertTimestampsAreIncreasing()

                val expectedMoveEvents = 2 * max(1, duration.inMilliseconds() / eventPeriod).toInt()
                // expect up and down events for each pointer as well as the move events
                assertThat(events.size).isAtLeast(4 + expectedMoveEvents)

                val pointerIds = events.map { it.id }.toSet()
                val pointerUpEvents = events.filter { !it.down }

                assertThat(pointerIds).hasSize(2)

                // Assert each pointer went back up
                assertThat(pointerUpEvents.map { it.id }).containsExactlyElementsIn(pointerIds)

                // Assert the up events are at the end
                assertThat(events.takeLastWhile { !it.down }).hasSize(2)

                events.filter { it.id.value == 0L }.isMonotonicBetween(start0, end0)
                events.filter { it.id.value == 1L }.isMonotonicBetween(start1, end1)
            }
        }
    }
}
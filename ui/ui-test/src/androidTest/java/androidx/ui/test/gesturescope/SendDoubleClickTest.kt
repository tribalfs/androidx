/*
 * Copyright 2019 The Android Open Source Project
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
import androidx.ui.core.gesture.doubleTapGestureFilter
import androidx.ui.geometry.Offset
import androidx.ui.test.android.AndroidInputDispatcher
import androidx.ui.test.createComposeRule
import androidx.ui.test.doGesture
import androidx.ui.test.findByTag
import androidx.ui.test.sendDoubleClick
import androidx.ui.test.util.ClickableTestBox
import androidx.ui.test.util.ClickableTestBox.defaultSize
import androidx.ui.test.util.ClickableTestBox.defaultTag
import androidx.ui.test.util.SinglePointerInputRecorder
import androidx.ui.test.util.recordedDuration
import androidx.ui.test.waitForIdle
import androidx.ui.unit.Duration
import androidx.ui.unit.milliseconds
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
class SendDoubleClickTest(private val config: TestConfig) {
    data class TestConfig(val position: Offset?, val delay: Duration?)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun createTestSet(): List<TestConfig> {
            return mutableListOf<TestConfig>().apply {
                for (delay in listOf(null, 50.milliseconds)) {
                    for (x in listOf(1.0f, 33.0f, 99.0f)) {
                        for (y in listOf(1.0f, 33.0f, 99.0f)) {
                            add(TestConfig(Offset(x, y), delay))
                        }
                    }
                    add(TestConfig(null, delay))
                }
            }
        }
    }

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    private val dispatcherRule = AndroidInputDispatcher.TestRule(disableDispatchInRealTime = true)
    private val eventPeriod get() = dispatcherRule.eventPeriod

    @get:Rule
    val inputDispatcherRule: TestRule = dispatcherRule

    private val recordedDoubleClicks = mutableListOf<Offset>()
    private val expectedClickPosition =
        config.position ?: Offset((defaultSize - 1) / 2, (defaultSize - 1) / 2)
    // The delay plus 2 clicks
    private val expectedDuration =
        (config.delay ?: 145.milliseconds) + (2 * eventPeriod).milliseconds

    private fun recordDoubleClick(position: Offset) {
        recordedDoubleClicks.add(position)
    }

    @Test
    fun testDoubleClick() {
        // Given some content
        val recorder = SinglePointerInputRecorder()
        composeTestRule.setContent {
            ClickableTestBox(Modifier.doubleTapGestureFilter(this::recordDoubleClick) + recorder)
        }

        // When we inject a double click
        findByTag(defaultTag).doGesture {
            if (config.position != null && config.delay != null) {
                sendDoubleClick(config.position, config.delay)
            } else if (config.position != null) {
                sendDoubleClick(config.position)
            } else if (config.delay != null) {
                sendDoubleClick(delay = config.delay)
            } else {
                sendDoubleClick()
            }
        }

        waitForIdle()

        // Then we record 1 double click at the expected position
        assertThat(recordedDoubleClicks).isEqualTo(listOf(expectedClickPosition))

        // And that the duration was as expected
        assertThat(recorder.recordedDuration).isEqualTo(expectedDuration)
    }
}

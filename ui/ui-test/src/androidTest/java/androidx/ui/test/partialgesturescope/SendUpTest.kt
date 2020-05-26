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

package androidx.ui.test.partialgesturescope

import androidx.test.filters.MediumTest
import androidx.ui.test.android.AndroidInputDispatcher
import androidx.ui.test.createComposeRule
import androidx.ui.test.doPartialGesture
import androidx.ui.test.findByTag
import androidx.ui.test.runOnIdleCompose
import androidx.ui.test.sendDown
import androidx.ui.test.sendUp
import androidx.ui.test.util.ClickableTestBox
import androidx.ui.test.util.PointerInputRecorder
import androidx.ui.test.util.assertTimestampsAreIncreasing
import androidx.ui.unit.PxPosition
import androidx.ui.unit.px
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@MediumTest
@RunWith(Parameterized::class)
class SendUpTest(private val config: TestConfig) {
    data class TestConfig(val upPosition: PxPosition?) {
        val downPosition = PxPosition(1.px, 1.px)
    }

    companion object {
        private const val tag = "widget"

        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun createTestSet(): List<TestConfig> {
            return mutableListOf<TestConfig>().apply {
                for (x in listOf(2.px, 99.px)) {
                    for (y in listOf(3.px, 53.px)) {
                        add(TestConfig(PxPosition(x, y)))
                    }
                }
                add(TestConfig(null))
            }
        }
    }

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val inputDispatcherRule: TestRule =
        AndroidInputDispatcher.TestRule(disableDispatchInRealTime = true)

    private val recorder = PointerInputRecorder()
    private val expectedEndPosition = config.upPosition ?: config.downPosition

    @Test
    fun testSendUp() {
        // Given some content
        composeTestRule.setContent {
            ClickableTestBox(recorder, tag = tag)
        }

        // When we inject a down event followed by an up event
        findByTag(tag).doPartialGesture { sendDown(config.downPosition) }
        findByTag(tag).doPartialGesture { sendUp(config.upPosition) }

        runOnIdleCompose {
            recorder.run {
                // Then we have recorded 1 down event and 1 up event
                assertTimestampsAreIncreasing()
                assertThat(events).hasSize(2)
                assertThat(events[1].down).isFalse()
                assertThat(events[1].position).isEqualTo(expectedEndPosition)
            }
        }
    }
}

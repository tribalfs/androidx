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

package androidx.compose.ui.test.injectionscope.touch

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.util.ClickableTestBox
import androidx.compose.ui.test.util.ClickableTestBox.defaultSize
import androidx.compose.ui.test.util.ClickableTestBox.defaultTag
import androidx.compose.ui.test.util.SinglePointerInputRecorder
import androidx.compose.ui.test.util.isAlmostEqualTo
import androidx.compose.ui.test.util.verify
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Tests [TouchInjectionScope.longClick] with arguments. Verifies that the click is in the middle
 * of the component, that the gesture has a duration of 600 milliseconds and that all input
 * events were on the same location.
 */
@MediumTest
@RunWith(Parameterized::class)
class LongClickTest(private val config: TestConfig) {
    data class TestConfig(val position: Offset?, val durationMillis: Long?)

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun createTestSet(): List<TestConfig> {
            return mutableListOf<TestConfig>().apply {
                for (duration in listOf(null, 700L)) {
                    add(TestConfig(Offset(10f, 10f), duration))
                    add(TestConfig(null, duration))
                }
            }
        }
    }

    @get:Rule
    val rule = createComposeRule()

    private val recordedLongClicks = mutableListOf<Offset>()
    private val expectedClickPosition =
        config.position ?: Offset(defaultSize / 2, defaultSize / 2)
    private val expectedDuration = config.durationMillis ?: 600L

    private fun recordLongPress(position: Offset) {
        recordedLongClicks.add(position)
    }

    @Before
    fun checkDuration() {
        assertWithMessage(
            "Test must either use a duration that is a multiple of 10, or the assertion needs to " +
                "be rewritten to support arbitrary durations"
        ).that(expectedDuration % 10).isEqualTo(0)
    }

    @Test
    fun longClick() {
        // Given some content
        val recorder = SinglePointerInputRecorder()
        rule.setContent {
            Box(Modifier.fillMaxSize().wrapContentSize(Alignment.BottomEnd)) {
                ClickableTestBox(
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(onLongPress = ::recordLongPress)
                        }
                        .then(recorder)
                )
            }
        }

        // When we inject a long click
        rule.onNodeWithTag(defaultTag).performTouchInput {
            if (config.position != null && config.durationMillis != null) {
                longClick(config.position, config.durationMillis)
            } else if (config.position != null) {
                longClick(config.position)
            } else if (config.durationMillis != null) {
                longClick(durationMillis = config.durationMillis)
            } else {
                longClick()
            }
        }

        rule.waitForIdle()

        // Then we record 1 long click at the expected position
        assertThat(recordedLongClicks).hasSize(1)
        recordedLongClicks[0].isAlmostEqualTo(expectedClickPosition)

        // And that the duration was as expected
        recorder.assertIsLongClick(expectedClickPosition)
    }

    private fun SinglePointerInputRecorder.assertIsLongClick(position: Offset) {
        assertThat(events.size).isAtLeast(2)
        val t0 = events[0].timestamp
        val id = events[0].id

        events.dropLast(1).forEachIndexed { i, event ->
            event.verify(t0 + i * 10, id, true, position)
        }
        events.last().verify(t0 + expectedDuration, id, false, position)
    }
}

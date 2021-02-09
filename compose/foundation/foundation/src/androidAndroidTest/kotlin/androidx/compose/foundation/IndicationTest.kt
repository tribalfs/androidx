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

package androidx.compose.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.platform.isDebugInspectorInfoEnabled
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.center
import androidx.compose.ui.test.down
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performGesture
import androidx.compose.ui.test.up
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@MediumTest
@RunWith(AndroidJUnit4::class)
class IndicationTest {

    @get:Rule
    val rule = createComposeRule()

    val testTag = "indication"

    @Before
    fun before() {
        isDebugInspectorInfoEnabled = true
    }

    @After
    fun after() {
        isDebugInspectorInfoEnabled = false
    }

    @Test
    fun indication_receivesInitialState() {
        val state = InteractionState()
        val countDownLatch = CountDownLatch(1)
        val indication = makeIndication {
            // just wait for initial draw with empty interaction
            if (it.value.isEmpty()) {
                countDownLatch.countDown()
            }
        }
        rule.setContent {
            Box(Modifier.testTag(testTag).size(100.dp).indication(state, indication))
        }
        assertThat(countDownLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue()
    }

    @Test
    fun indication_click_receivesStateUpdates() {
        // indicaiton should be called 3 times: 0 indication, press, and after click 0 again
        val countDownLatch = CountDownLatch(3)
        val indication = makeIndication {
            it.value // value read
            countDownLatch.countDown()
        }
        rule.setContent {
            Box(
                Modifier
                    .testTag(testTag)
                    .size(100.dp)
                    .clickable(
                        interactionState = remember { InteractionState() },
                        indication = indication,
                    ) {}
            )
        }
        assertThat(countDownLatch.count).isEqualTo(2)
        rule.onNodeWithTag(testTag)
            .assertExists()
            .performGesture {
                down(center)
            }
        rule.runOnIdle {
            assertThat(countDownLatch.count).isEqualTo(1)
        }
        rule.onNodeWithTag(testTag)
            .assertExists()
            .performGesture {
                up()
            }
        assertThat(countDownLatch.await(1000, TimeUnit.MILLISECONDS)).isTrue()
    }

    @Test
    @Ignore("b/155466122: multitouch is not supported yet")
    fun indication_multiplyPress_firstWins() {
        var lastPosition: Offset? = null
        val indication = makeIndication {
            it.value // value read
            lastPosition = it.interactionPositionFor(Interaction.Pressed)
        }
        rule.setContent {
            Box(
                Modifier
                    .testTag(testTag)
                    .size(100.dp)
                    .clickable(
                        interactionState = remember { InteractionState() },
                        indication = indication
                    ) { }
            )
        }
        assertThat(lastPosition).isNull()
        var position1: Offset? = null
        rule.onNodeWithTag(testTag)
            .assertExists()
            .performGesture {
                position1 = Offset(center.x, center.y + 20f)
                // pointer 1, when we have multitouch
                down(position1!!)
            }
        rule.runOnIdle {
            assertThat(lastPosition).isEqualTo(position1!!)
        }
        rule.onNodeWithTag(testTag)
            .assertExists()
            .performGesture {
                val position2 = Offset(center.x + 20f, center.y)
                // pointer 2, when we have multitouch
                down(position2)
            }
        // should be still position1
        rule.runOnIdle {
            assertThat(lastPosition).isEqualTo(position1!!)
        }
        rule.onNodeWithTag(testTag)
            .assertExists()
            .performGesture {
                // pointer 1, when we have multitouch
                up()
            }
        rule.runOnIdle {
            assertThat(lastPosition).isNull()
        }
        rule.onNodeWithTag(testTag)
            .assertExists()
            .performGesture {
                // pointer 2, when we have multitouch
                up()
            }
    }

    private fun makeIndication(onDraw: (InteractionState) -> Unit): Indication {
        return object : Indication {
            @Composable
            override fun rememberUpdatedInstance(
                interactionState: InteractionState
            ): IndicationInstance {
                return remember(interactionState) {
                    object : IndicationInstance {
                        override fun ContentDrawScope.drawIndication() {
                            onDraw(interactionState)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun testInspectorValue() {
        val state = InteractionState()
        val indication = makeIndication({})
        rule.setContent {
            val modifier = Modifier.indication(state, indication) as InspectableValue
            assertThat(modifier.nameFallback).isEqualTo("indication")
            assertThat(modifier.valueOverride).isNull()
            assertThat(modifier.inspectableElements.map { it.name }.asIterable()).containsExactly(
                "indication",
                "interactionState"
            )
        }
    }
}
/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.wear.compose.foundation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.assertLeftPositionInRootIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.launch
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalWearFoundationApi::class)
class SwipeToRevealTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun supports_testTag() {
        rule.setContent {
            swipeToRevealWithDefaults(
                modifier = Modifier.testTag(TEST_TAG)
            )
        }

        rule.onNodeWithTag(TEST_TAG).assertExists()
    }

    @Test
    fun onStartWithDefaultState_keepsContentToRight() {
        rule.setContent {
            swipeToRevealWithDefaults(
                content = { getBoxContent(modifier = Modifier.testTag(TEST_TAG)) }
            )
        }

        rule.onNodeWithTag(TEST_TAG).assertLeftPositionInRootIsEqualTo(0.dp)
    }

    @Test
    fun onZeroOffset_doesNotDrawActions() {
        rule.setContent {
            swipeToRevealWithDefaults(
                primaryAction = { actionContent(modifier = Modifier.testTag(TEST_TAG)) }
            )
        }

        rule.onNodeWithTag(TEST_TAG).assertDoesNotExist()
    }

    @Test
    fun onRevealing_drawsAction() {
        rule.setContent {
            swipeToRevealWithDefaults(
                state = rememberRevealState(initialValue = RevealValue.Revealing),
                primaryAction = { actionContent(modifier = Modifier.testTag(TEST_TAG)) }
            )
        }

        rule.onNodeWithTag(TEST_TAG).assertExists()
    }

    @Test
    fun onSwipe_drawsAction() {
        val s2rTag = "S2RTag"
        rule.setContent {
            swipeToRevealWithDefaults(
                modifier = Modifier.testTag(s2rTag),
                primaryAction = { actionContent(modifier = Modifier.testTag(TEST_TAG)) }
            )
        }

        rule.onNodeWithTag(s2rTag).performTouchInput {
            down(center)
            // Move the pointer by quarter of the screen width, don't move up the pointer
            moveBy(delta = Offset(x = -(centerX / 4), y = 0f))
        }

        rule.waitForIdle()
        rule.onNodeWithTag(TEST_TAG).assertExists()
    }

    @Test
    fun noSwipe_onFullSwipeRight() {
        verifyGesture(
            revealValue = RevealValue.Covered,
            gesture = { swipeRight() }
        )
    }

    @Test
    fun stateToSwiped_onFullSwipeLeft() {
        verifyGesture(
            revealValue = RevealValue.Revealed,
            gesture = { swipeLeft() }
        )
    }

    @Test
    fun stateToIconsVisible_onPartialSwipeLeft() {
        verifyGesture(
            revealValue = RevealValue.Revealing,
            gesture = {
                swipeLeft(
                    startX = width / 2f,
                    endX = 0f
                )
            }
        )
    }

    @Test
    fun onSecondaryActionClick_setsLastClickAction() = verifyLastClickAction(
        expectedClickType = RevealActionType.SecondaryAction,
        initialRevealValue = RevealValue.Revealing,
        secondaryActionModifier = Modifier.testTag(TEST_TAG)
    )

    @Test
    fun onPrimaryActionClick_setsLastClickAction() = verifyLastClickAction(
        expectedClickType = RevealActionType.PrimaryAction,
        initialRevealValue = RevealValue.Revealing,
        primaryActionModifier = Modifier.testTag(TEST_TAG)
    )

    @Test
    fun onUndoActionClick_setsLastClickAction() = verifyLastClickAction(
        expectedClickType = RevealActionType.UndoAction,
        initialRevealValue = RevealValue.Revealed,
        undoActionModifier = Modifier.testTag(TEST_TAG)
    )

    private fun verifyLastClickAction(
        expectedClickType: RevealActionType,
        initialRevealValue: RevealValue,
        primaryActionModifier: Modifier = Modifier,
        secondaryActionModifier: Modifier = Modifier,
        undoActionModifier: Modifier = Modifier,
    ) {
        lateinit var revealState: RevealState
        rule.setContent {
            revealState = rememberRevealState(initialRevealValue)
            val coroutineScope = rememberCoroutineScope()
            swipeToRevealWithDefaults(
                state = revealState,
                primaryAction = {
                    actionContent(
                        modifier = primaryActionModifier.clickable {
                            coroutineScope.launch {
                                revealState.snapTo(RevealValue.Covered)
                                revealState.lastActionType = RevealActionType.PrimaryAction
                            }
                        }
                    )
                },
                secondaryAction = {
                    actionContent(
                        modifier = secondaryActionModifier.clickable {
                            coroutineScope.launch {
                                revealState.snapTo(RevealValue.Covered)
                                revealState.lastActionType = RevealActionType.SecondaryAction
                            }
                        }
                    )
                },
                undoAction = {
                    actionContent(
                        modifier = undoActionModifier.clickable {
                            coroutineScope.launch {
                                revealState.animateTo(RevealValue.Covered)
                                revealState.lastActionType = RevealActionType.UndoAction
                            }
                        }
                    )
                }
            )
        }
        rule.onNodeWithTag(TEST_TAG).performClick()
        rule.runOnIdle {
            assertEquals(expectedClickType, revealState.lastActionType)
        }
    }

    private fun verifyGesture(
        revealValue: RevealValue,
        gesture: TouchInjectionScope.() -> Unit
    ) {
        lateinit var revealState: RevealState
        rule.setContent {
            revealState = rememberRevealState()
            swipeToRevealWithDefaults(
                state = revealState,
                modifier = Modifier.testTag(TEST_TAG)
            )
        }

        rule.onNodeWithTag(TEST_TAG).performTouchInput(gesture)

        rule.runOnIdle {
            assertEquals(revealValue, revealState.currentValue)
        }
    }

    @Composable
    private fun swipeToRevealWithDefaults(
        primaryAction: @Composable RevealScope.() -> Unit = { getAction() },
        state: RevealState = rememberRevealState(),
        modifier: Modifier = Modifier,
        secondaryAction: (@Composable RevealScope.() -> Unit)? = null,
        undoAction: (@Composable RevealScope.() -> Unit)? = null,
        content: @Composable () -> Unit = { getBoxContent() }
    ) {
        SwipeToReveal(
            primaryAction = primaryAction,
            state = state,
            modifier = modifier,
            secondaryAction = secondaryAction,
            undoAction = undoAction,
            content = content
        )
    }

    @Composable
    private fun getBoxContent(
        onClick: () -> Unit = {},
        modifier: Modifier = Modifier
    ) {
        Box(modifier = modifier
            .size(width = 200.dp, height = 50.dp)
            .clickable { onClick() }) {}
    }

    @Composable
    private fun actionContent(
        modifier: Modifier = Modifier
    ) {
       Box(modifier = modifier.size(50.dp)) {}
    }

    @Composable
    private fun getAction(
        onClick: () -> Unit = {},
        modifier: Modifier = Modifier,
        content: @Composable () -> Unit = { actionContent(modifier) }
    ) {
        Box(
            modifier = modifier.clickable { onClick() }
        ) {
            content()
        }
    }
}

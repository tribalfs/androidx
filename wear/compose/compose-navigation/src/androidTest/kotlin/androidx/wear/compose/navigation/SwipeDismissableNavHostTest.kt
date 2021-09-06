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
package androidx.wear.compose.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ExperimentalWearMaterialApi
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleButton
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test

@ExperimentalWearMaterialApi
class SwipeDismissableNavHostTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun supports_testtag() {
        rule.setContentWithTheme {
            SwipeDismissWithNavigation()
        }

        rule.onNodeWithTag(TEST_TAG).assertExists()
    }

    @Test
    fun navigates_to_next_level() {
        rule.setContentWithTheme {
            SwipeDismissWithNavigation()
        }

        // Click to move to next destination.
        rule.onNodeWithText(START).performClick()

        // Should now display "next".
        rule.onNodeWithText(NEXT).assertExists()
    }

    @Test
    fun navigates_back_to_previous_level_after_swipe() {
        rule.setContentWithTheme {
            SwipeDismissWithNavigation()
        }

        // Click to move to next destination then swipe to dismiss.
        rule.onNodeWithText(START).performClick()
        rule.onNodeWithTag(TEST_TAG).performTouchInput({ swipeRight() })

        // Should now display "start".
        rule.onNodeWithText(START).assertExists()
    }

    @Test
    fun hides_previous_level_when_not_swiping() {
        rule.setContentWithTheme {
            SwipeDismissWithNavigation()
        }

        // Click to move to next destination then swipe to dismiss.
        rule.onNodeWithText(START).performClick()

        // Should not display "start".
        rule.onNodeWithText(START).assertDoesNotExist()
    }

    @ExperimentalTestApi
    @Test
    fun displays_previous_screen_during_swipe_gesture() {
        rule.setContentWithTheme {
            SwipeDismissWithNavigation()
        }

        // Click to move to next destination then pause during a long swipe to dismiss.
        rule.onNodeWithText(START).performClick()
        rule.mainClock.autoAdvance = false
        rule.onNodeWithTag(TEST_TAG).performTouchInput({ swipeRight() })
        rule.mainClock.advanceTimeBy(milliseconds = LONG_SWIPE / 2)

        rule.onNodeWithText(START).assertExists()
    }

    @Test
    @Ignore // TODO(stevebower): reinstate test once aosp/1818154 has been submitted.
    fun destinations_keep_saved_state() {
        val screenId = mutableStateOf(START)
        rule.setContentWithTheme {
            val holder = rememberSaveableStateHolder()
            holder.SaveableStateProvider(screenId) {
                val navController = rememberSwipeDismissableNavController()
                SwipeDismissableNavHost(
                    navController = navController,
                    startDestination = START,
                    modifier = Modifier.testTag(TEST_TAG),
                ) {
                    composable(START) {
                        screenId.value = START
                        var toggle by rememberSaveable { mutableStateOf(false) }
                        Column {
                            ToggleButton(
                                checked = toggle,
                                onCheckedChange = {
                                    toggle = !toggle
                                },
                                content = { Text(text = if (toggle) "On" else "Off") },
                                modifier = Modifier.testTag("ToggleButton"),
                            )
                            Button(
                                onClick = { navController.navigate(NEXT) },
                            ) {
                                Text("Go")
                            }
                        }
                    }
                    composable("next") {
                        screenId.value = NEXT
                        CompactChip(
                            onClick = {},
                            label = { Text(text = NEXT) }
                        )
                    }
                }
            }
        }

        rule.onNodeWithText("Off").performClick()
        rule.onNodeWithText("Go").performClick()
        rule.waitForIdle()
        rule.onNodeWithTag(TEST_TAG).performTouchInput({ swipeRight() })
        rule.waitForIdle()
        rule.onNodeWithText("On").assertExists()
    }

    @Composable
    fun SwipeDismissWithNavigation() {
        val navController = rememberSwipeDismissableNavController()
        SwipeDismissableNavHost(
            navController = navController,
            startDestination = START,
            modifier = Modifier.testTag(TEST_TAG),
        ) {
            composable(START) {
                CompactChip(
                    onClick = { navController.navigate(NEXT) },
                    label = { Text(text = START) }
                )
            }
            composable("next") {
                Text(NEXT)
            }
        }
    }
}

fun ComposeContentTestRule.setContentWithTheme(
    composable: @Composable () -> Unit
) {
    setContent {
        MaterialTheme {
            composable()
        }
    }
}

private const val LONG_SWIPE = 1000L
private const val NEXT = "next"
private const val START = "start"
private const val TEST_TAG = "test-item"

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

package androidx.compose.material

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.Interaction
import androidx.compose.foundation.InteractionState
import androidx.compose.foundation.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.test.filters.LargeTest
import androidx.test.filters.SdkSuppress
import androidx.test.screenshot.AndroidXScreenshotTestRule
import androidx.test.screenshot.assertAgainstGolden
import androidx.ui.test.captureToBitmap
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithTag
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterialApi::class)
class TabScreenshotTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val screenshotRule = AndroidXScreenshotTestRule(GOLDEN_MATERIAL)

    @Test
    fun lightTheme_defaultColors() {
        val interactionState = InteractionState().apply {
            addInteraction(Interaction.Pressed)
        }

        composeTestRule.setContent {
            MaterialTheme(lightColors()) {
                DefaultTabs(interactionState)
            }
        }

        assertTabsMatch(
            interactionState = interactionState,
            interaction = null,
            goldenIdentifier = "tabs_lightTheme_defaultColors"
        )
    }

    @Test
    fun lightTheme_defaultColors_pressed() {
        val interactionState = InteractionState()

        composeTestRule.setContent {
            MaterialTheme(lightColors()) {
                DefaultTabs(interactionState)
            }
        }

        assertTabsMatch(
            interactionState = interactionState,
            interaction = Interaction.Pressed,
            goldenIdentifier = "tabs_lightTheme_defaultColors_pressed"
        )
    }

    @Test
    fun lightTheme_surfaceColors() {
        val interactionState = InteractionState().apply {
            addInteraction(Interaction.Pressed)
        }

        composeTestRule.setContent {
            MaterialTheme(lightColors()) {
                CustomTabs(
                    interactionState,
                    backgroundColor = MaterialTheme.colors.surface,
                    selectedContentColor = MaterialTheme.colors.primary,
                    unselectedContentColor = MaterialTheme.colors.onSurface
                )
            }
        }

        assertTabsMatch(
            interactionState = interactionState,
            interaction = null,
            goldenIdentifier = "tabs_lightTheme_surfaceColors"
        )
    }

    @Test
    fun lightTheme_surfaceColors_pressed() {
        val interactionState = InteractionState()

        composeTestRule.setContent {
            MaterialTheme(lightColors()) {
                CustomTabs(
                    interactionState,
                    backgroundColor = MaterialTheme.colors.surface,
                    selectedContentColor = MaterialTheme.colors.primary,
                    unselectedContentColor = MaterialTheme.colors.onSurface
                )
            }
        }

        assertTabsMatch(
            interactionState = interactionState,
            interaction = Interaction.Pressed,
            goldenIdentifier = "tabs_lightTheme_surfaceColors_pressed"
        )
    }

    @Test
    fun darkTheme_defaultColors() {
        val interactionState = InteractionState().apply {
            addInteraction(Interaction.Pressed)
        }

        composeTestRule.setContent {
            MaterialTheme(darkColors()) {
                DefaultTabs(interactionState)
            }
        }

        assertTabsMatch(
            interactionState = interactionState,
            interaction = null,
            goldenIdentifier = "tabs_darkTheme_defaultColors"
        )
    }

    @Test
    fun darkTheme_defaultColors_pressed() {
        val interactionState = InteractionState()

        composeTestRule.setContent {
            MaterialTheme(darkColors()) {
                DefaultTabs(interactionState)
            }
        }

        assertTabsMatch(
            interactionState = interactionState,
            interaction = Interaction.Pressed,
            goldenIdentifier = "tabs_darkTheme_defaultColors_pressed"
        )
    }

    // Dark theme by default uses `surface` as the background color, but the selectedContentColor
    // defaults to `onSurface`, whereas a typical use case is for it to be `primary`. This test
    // matches that use case.
    @Test
    fun darkTheme_surfaceColors() {
        val interactionState = InteractionState().apply {
            addInteraction(Interaction.Pressed)
        }

        composeTestRule.setContent {
            MaterialTheme(darkColors()) {
                CustomTabs(
                    interactionState,
                    backgroundColor = MaterialTheme.colors.surface,
                    selectedContentColor = MaterialTheme.colors.primary,
                    unselectedContentColor = MaterialTheme.colors.onSurface
                )
            }
        }

        assertTabsMatch(
            interactionState = interactionState,
            interaction = null,
            goldenIdentifier = "tabs_darkTheme_surfaceColors"
        )
    }

    @Test
    fun darkTheme_surfaceColors_pressed() {
        val interactionState = InteractionState()

        composeTestRule.setContent {
            MaterialTheme(darkColors()) {
                CustomTabs(
                    interactionState,
                    backgroundColor = MaterialTheme.colors.surface,
                    selectedContentColor = MaterialTheme.colors.primary,
                    unselectedContentColor = MaterialTheme.colors.onSurface
                )
            }
        }

        assertTabsMatch(
            interactionState = interactionState,
            interaction = Interaction.Pressed,
            goldenIdentifier = "tabs_darkTheme_surfaceColors_pressed"
        )
    }

    @Test
    fun darkTheme_primaryColors() {
        val interactionState = InteractionState().apply {
            addInteraction(Interaction.Pressed)
        }

        composeTestRule.setContent {
            MaterialTheme(darkColors()) {
                CustomTabs(
                    interactionState,
                    backgroundColor = MaterialTheme.colors.primary,
                    selectedContentColor = MaterialTheme.colors.onPrimary,
                    unselectedContentColor = MaterialTheme.colors.onPrimary
                )
            }
        }

        assertTabsMatch(
            interactionState = interactionState,
            interaction = null,
            goldenIdentifier = "tabs_darkTheme_primaryColors"
        )
    }

    @Test
    fun darkTheme_primaryColors_pressed() {
        val interactionState = InteractionState()

        composeTestRule.setContent {
            MaterialTheme(darkColors()) {
                CustomTabs(
                    interactionState,
                    backgroundColor = MaterialTheme.colors.primary,
                    selectedContentColor = MaterialTheme.colors.onPrimary,
                    unselectedContentColor = MaterialTheme.colors.onPrimary
                )
            }
        }

        assertTabsMatch(
            interactionState = interactionState,
            interaction = Interaction.Pressed,
            goldenIdentifier = "tabs_darkTheme_primaryColors_pressed"
        )
    }

    /**
     * Asserts that the tabs match the screenshot with identifier [goldenIdentifier].
     *
     * @param interactionState the [InteractionState] used for the first Tab
     * @param interaction the [Interaction] to assert for, or `null` if no [Interaction].
     * @param goldenIdentifier the identifier for the corresponding screenshot
     */
    private fun assertTabsMatch(
        interactionState: InteractionState,
        interaction: Interaction? = null,
        goldenIdentifier: String
    ) {
        composeTestRule.clockTestRule.pauseClock()

        if (interaction != null) {
            // Start ripple
            composeTestRule.runOnUiThread {
                if (interaction is Interaction.Pressed) {
                    interactionState.addInteraction(interaction, Offset(10f, 10f))
                } else {
                    interactionState.addInteraction(interaction)
                }
            }

            // Advance to somewhere in the middle of the animation for the ripple
            composeTestRule.waitForIdle()
            composeTestRule.clockTestRule.advanceClock(50)
        }

        // Capture and compare screenshots
        composeTestRule.onNodeWithTag(Tag)
            .captureToBitmap()
            .assertAgainstGolden(screenshotRule, goldenIdentifier)
    }
}

/**
 * Default colored [TabRow] with three [Tab]s. The first [Tab] is selected, and the rest are not.
 *
 * @param interactionState the [InteractionState] for the first [Tab], to control its visual state.
 */
@Composable
private fun DefaultTabs(
    interactionState: InteractionState
) {
    Box(Modifier.semantics(mergeAllDescendants = true) {}.testTag(Tag)) {
        TabRow(selectedTabIndex = 0) {
            Tab(
                text = { Text("TAB") },
                selected = true,
                interactionState = interactionState,
                onClick = {}
            )
            Tab(
                text = { Text("TAB") },
                selected = false,
                onClick = {}
            )
            Tab(
                text = { Text("TAB") },
                selected = false,
                onClick = {}
            )
        }
    }
}

/**
 * Custom colored [TabRow] with three [Tab]s. The first [Tab] is selected, and the rest are not.
 *
 * @param interactionState the [InteractionState] for the first [Tab], to control its visual state.
 * @param backgroundColor the backgroundColor of the [TabRow]
 * @param selectedContentColor the content color for a selected [Tab] (first tab)
 * @param unselectedContentColor the content color for an unselected [Tab] (second and third tabs)
 */
@Composable
private fun CustomTabs(
    interactionState: InteractionState,
    backgroundColor: Color,
    selectedContentColor: Color,
    unselectedContentColor: Color
) {
    // Apply default emphasis
    @Suppress("NAME_SHADOWING")
    val unselectedContentColor = AmbientEmphasisLevels.current.medium
        .applyEmphasis(unselectedContentColor)
    Box(Modifier.semantics(mergeAllDescendants = true) {}.testTag(Tag)) {
        TabRow(selectedTabIndex = 0, backgroundColor = backgroundColor) {
            Tab(
                text = { Text("TAB") },
                selected = true,
                interactionState = interactionState,
                selectedContentColor = selectedContentColor,
                unselectedContentColor = unselectedContentColor,
                onClick = {}
            )
            Tab(
                text = { Text("TAB") },
                selected = false,
                selectedContentColor = selectedContentColor,
                unselectedContentColor = unselectedContentColor,
                onClick = {}
            )
            Tab(
                text = { Text("TAB") },
                selected = false,
                selectedContentColor = selectedContentColor,
                unselectedContentColor = unselectedContentColor,
                onClick = {}
            )
        }
    }
}

private const val Tag = "Tab"

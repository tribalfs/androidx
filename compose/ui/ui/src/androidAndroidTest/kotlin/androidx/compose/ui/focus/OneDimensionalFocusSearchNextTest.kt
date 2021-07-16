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

package androidx.compose.ui.focus

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.FocusDirection.Companion.Next
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.MediumTest
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@MediumTest
@RunWith(AndroidJUnit4::class)
class OneDimensionalFocusSearchNextTest {

    @get:Rule
    val rule = createComposeRule()

    private lateinit var focusManager: FocusManager
    private val initialFocus: FocusRequester = FocusRequester()

    @Test
    fun initialFocus_oneItem() {
        // Arrange.
        val isItemFocused = mutableStateOf(false)
        rule.setContentWithInitialRootFocus {
            FocusableBox(isItemFocused, 0, 0, 10, 10)
        }

        // Act.
        val movedFocusSuccessfully = focusManager.moveFocus(Next)

        // Assert.
        rule.runOnIdle {
            assertThat(movedFocusSuccessfully).isTrue()
            assertThat(isItemFocused.value).isTrue()
        }
    }

    @Test
    fun initialFocus_firstItemInCompositionOrderGetsFocus() {
        // Arrange.
        val (firstItem, secondItem) = List(2) { mutableStateOf(false) }
        rule.setContentWithInitialRootFocus {
            FocusableBox(firstItem, 10, 10, 10, 10)
            FocusableBox(secondItem, 0, 0, 10, 10)
        }

        // Act.
        val movedFocusSuccessfully = focusManager.moveFocus(Next)

        // Assert.
        rule.runOnIdle {
            assertThat(movedFocusSuccessfully).isTrue()
            assertThat(firstItem.value).isTrue()
        }
    }

    @Test
    fun initialFocus_firstParentInCompositionOrderGetsFocus() {
        // Arrange.
        val (parent1, parent2, child1, child2) = List(4) { mutableStateOf(false) }
        rule.setContentWithInitialRootFocus {
            FocusableBox(parent1, 10, 10, 10, 10) {
                FocusableBox(child1, 10, 10, 10, 10)
            }
            FocusableBox(parent2, 0, 0, 10, 10) {
                FocusableBox(child2, 10, 10, 10, 10)
            }
        }

        // Act.
        val movedFocusSuccessfully = focusManager.moveFocus(Next)

        // Assert.
        rule.runOnIdle {
            assertThat(movedFocusSuccessfully).isTrue()
            assertThat(parent1.value).isTrue()
        }
    }

    @Test
    fun initialFocus_firstItemInCompositionOrderGetsFocus_evenIfAnotherNonParentIsPresent() {
        // Arrange.
        val (parent1, child1, item2) = List(3) { mutableStateOf(false) }
        rule.setContentWithInitialRootFocus {
            FocusableBox(parent1, 10, 10, 10, 10) {
                FocusableBox(child1, 10, 10, 10, 10)
            }
            FocusableBox(item2, 0, 0, 10, 10)
        }

        // Act.
        val movedFocusSuccessfully = focusManager.moveFocus(Next)

        // Assert.
        rule.runOnIdle {
            assertThat(movedFocusSuccessfully).isTrue()
            assertThat(parent1.value).isTrue()
        }
    }

    @Test
    fun initialFocus_firstItemInCompositionOrderGetsFocus_evenIfThereIsAParentAtTheRoot() {
        // Arrange.
        val (parent1, child1, item1) = List(3) { mutableStateOf(false) }
        rule.setContentWithInitialRootFocus {
            FocusableBox(item1, 0, 0, 10, 10)
            FocusableBox(parent1, 10, 10, 10, 10) {
                FocusableBox(child1, 10, 10, 10, 10)
            }
        }

        // Act.
        val movedFocusSuccessfully = focusManager.moveFocus(Next)

        // Assert.
        rule.runOnIdle {
            assertThat(movedFocusSuccessfully).isTrue()
            assertThat(item1.value).isTrue()
        }
    }

    @Test
    fun focusMovesToSecondItem() {
        // Arrange.
        val (item1, item2, item3) = List(3) { mutableStateOf(false) }
        rule.setContentForTest {
            FocusableBox(item1, 0, 0, 10, 10, initialFocus)
            FocusableBox(item2, 10, 0, 10, 10)
            FocusableBox(item3, 20, 0, 10, 10)
        }

        // Act.
        val movedFocusSuccessfully = focusManager.moveFocus(Next)

        // Assert.
        rule.runOnIdle {
            assertThat(movedFocusSuccessfully).isTrue()
            assertThat(item2.value).isTrue()
        }
    }

    @Test
    fun focusMovesToThirdItem() {
        // Arrange.
        val (item1, item2, item3) = List(3) { mutableStateOf(false) }
        rule.setContentForTest {
            FocusableBox(item1, 0, 0, 10, 10)
            FocusableBox(item2, 10, 0, 10, 10, initialFocus)
            FocusableBox(item3, 20, 0, 10, 10)
        }

        // Act.
        val movedFocusSuccessfully = focusManager.moveFocus(Next)

        // Assert.
        rule.runOnIdle {
            assertThat(movedFocusSuccessfully).isTrue()
            assertThat(item3.value).isTrue()
        }
    }

    @Test
    fun focusWrapsAroundToFirstItem() {
        // Arrange.
        val (item1, item2, item3) = List(3) { mutableStateOf(false) }
        rule.setContentForTest {
            FocusableBox(item1, 0, 0, 10, 10)
            FocusableBox(item2, 10, 0, 10, 10)
            FocusableBox(item3, 20, 0, 10, 10, initialFocus)
        }

        // Act.
        val movedFocusSuccessfully = focusManager.moveFocus(Next)

        // Assert.
        rule.runOnIdle {
            assertThat(movedFocusSuccessfully).isTrue()
            assertThat(item1.value).isTrue()
        }
    }

    @Test
    fun focusNextOrdering() {
        // Arrange.
        val (parent1, child1, child2, child3) = List(4) { mutableStateOf(false) }
        val (parent2, child4, child5) = List(3) { mutableStateOf(false) }
        val (parent3, child6) = List(2) { mutableStateOf(false) }
        rule.setContentWithInitialRootFocus {
            FocusableBox(parent1, 0, 0, 10, 10) {
                FocusableBox(child1, 0, 0, 10, 10)
                FocusableBox(child2, 20, 0, 10, 10)
                FocusableBox(child3, 10, 0, 10, 10)
            }
            FocusableBox(parent2, 0, 10, 10, 10) {
                FocusableBox(child4, 0, 10, 10, 10)
                FocusableBox(parent3, 10, 10, 10, 10) {
                    FocusableBox(child6, 0, 0, 10, 10)
                }
                FocusableBox(child5, 20, 0, 10, 10)
            }
        }

        // Act & Assert.
        focusManager.moveFocus(Next)
        rule.runOnIdle { assertThat(parent1.value).isTrue() }

        focusManager.moveFocus(Next)
        rule.runOnIdle { assertThat(child1.value).isTrue() }

        focusManager.moveFocus(Next)
        rule.runOnIdle { assertThat(child2.value).isTrue() }

        focusManager.moveFocus(Next)
        rule.runOnIdle { assertThat(child3.value).isTrue() }

        focusManager.moveFocus(Next)
        rule.runOnIdle { assertThat(parent2.value).isTrue() }

        focusManager.moveFocus(Next)
        rule.runOnIdle { assertThat(child4.value).isTrue() }

        focusManager.moveFocus(Next)
        rule.runOnIdle { assertThat(parent3.value).isTrue() }

        focusManager.moveFocus(Next)
        rule.runOnIdle { assertThat(child6.value).isTrue() }

        focusManager.moveFocus(Next)
        rule.runOnIdle { assertThat(child5.value).isTrue() }

        focusManager.moveFocus(Next)
        rule.runOnIdle { assertThat(parent1.value).isTrue() }
    }

    private fun ComposeContentTestRule.setContentForTest(composable: @Composable () -> Unit) {
        setContent {
            focusManager = LocalFocusManager.current
            composable()
        }
        rule.runOnIdle { initialFocus.requestFocus() }
    }

    private fun ComposeContentTestRule.setContentWithInitialRootFocus(
        composable: @Composable () -> Unit
    ) {
        setContent {
            focusManager = LocalFocusManager.current
            composable()
        }
        rule.runOnIdle { (focusManager as FocusManagerImpl).takeFocus() }
    }
}

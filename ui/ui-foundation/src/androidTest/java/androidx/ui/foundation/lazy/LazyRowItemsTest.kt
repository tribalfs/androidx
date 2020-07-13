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

package androidx.ui.foundation.lazy

import androidx.test.filters.MediumTest
import androidx.ui.core.Modifier
import androidx.ui.core.testTag
import androidx.ui.layout.Spacer
import androidx.ui.layout.Stack
import androidx.ui.layout.fillMaxHeight
import androidx.ui.layout.preferredWidth
import androidx.ui.test.assertIsDisplayed
import androidx.ui.test.createComposeRule
import androidx.ui.test.onNodeWithTag
import androidx.ui.unit.dp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class LazyRowItemsTest {

    private val LazyRowItemsTag = "LazyRowItemsTag"

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun lazyRowOnlyVisibleItemsAdded() {
        val items = (1..4).map { it.toString() }

        composeTestRule.setContent {
            Stack(Modifier.preferredWidth(200.dp)) {
                LazyRowItems(items) {
                    Spacer(Modifier.preferredWidth(101.dp).fillMaxHeight().testTag(it))
                }
            }
        }

        onNodeWithTag("1")
            .assertIsDisplayed()

        onNodeWithTag("2")
            .assertIsDisplayed()

        onNodeWithTag("3")
            .assertDoesNotExist()

        onNodeWithTag("4")
            .assertDoesNotExist()
    }

    @Test
    fun lazyRowScrollToShowItems123() {
        val items = (1..4).map { it.toString() }

        composeTestRule.setContent {
            Stack(Modifier.preferredWidth(200.dp)) {
                LazyRowItems(items, Modifier.testTag(LazyRowItemsTag)) {
                    Spacer(Modifier.preferredWidth(101.dp).fillMaxHeight().testTag(it))
                }
            }
        }

        onNodeWithTag(LazyRowItemsTag)
            .scrollBy(x = 50.dp, density = composeTestRule.density)

        onNodeWithTag("1")
            .assertIsDisplayed()

        onNodeWithTag("2")
            .assertIsDisplayed()

        onNodeWithTag("3")
            .assertIsDisplayed()

        onNodeWithTag("4")
            .assertDoesNotExist()
    }

    @Test
    fun lazyRowScrollToHideFirstItem() {
        val items = (1..4).map { it.toString() }

        composeTestRule.setContent {
            Stack(Modifier.preferredWidth(200.dp)) {
                LazyRowItems(items, Modifier.testTag(LazyRowItemsTag)) {
                    Spacer(Modifier.preferredWidth(101.dp).fillMaxHeight().testTag(it))
                }
            }
        }

        onNodeWithTag(LazyRowItemsTag)
            .scrollBy(x = 102.dp, density = composeTestRule.density)

        onNodeWithTag("1")
            .assertDoesNotExist()

        onNodeWithTag("2")
            .assertIsDisplayed()

        onNodeWithTag("3")
            .assertIsDisplayed()
    }

    @Test
    fun lazyRowScrollToShowItems234() {
        val items = (1..4).map { it.toString() }

        composeTestRule.setContent {
            Stack(Modifier.preferredWidth(200.dp)) {
                LazyRowItems(items, Modifier.testTag(LazyRowItemsTag)) {
                    Spacer(Modifier.preferredWidth(101.dp).fillMaxHeight().testTag(it))
                }
            }
        }

        onNodeWithTag(LazyRowItemsTag)
            .scrollBy(x = 150.dp, density = composeTestRule.density)

        onNodeWithTag("1")
            .assertDoesNotExist()

        onNodeWithTag("2")
            .assertIsDisplayed()

        onNodeWithTag("3")
            .assertIsDisplayed()

        onNodeWithTag("4")
            .assertIsDisplayed()
    }
}

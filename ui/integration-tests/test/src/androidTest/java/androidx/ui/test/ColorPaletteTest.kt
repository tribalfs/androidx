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

package androidx.ui.test

import androidx.test.filters.MediumTest
import androidx.ui.test.cases.ImmutableColorPaletteTestCase
import androidx.ui.test.cases.ObservableColorPaletteTestCase
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

/**
 * Test to ensure correctness of [ObservableColorPaletteTestCase] and
 * [ImmutableColorPaletteTestCase].
 */
@MediumTest
@RunWith(JUnit4::class)
class ColorPaletteTest {
    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun testObservablePalette() {
        val testCase = ObservableColorPaletteTestCase()
        composeTestRule
            .forGivenTestCase(testCase)
            .performTestWithEventsControl {
                doFrame()
                assertNoPendingChanges()

                Assert.assertEquals(2, testCase.primaryCompositions)
                Assert.assertEquals(1, testCase.secondaryCompositions)

                doFrame()
                assertNoPendingChanges()

                testCase.toggleState()

                // TODO: b/143769776
                // Currently changing a @Model value that causes another @Model value to change
                // results in two recompositions - so we need to pump two frames here.
                doFrame()
                assertLastRecomposeHadChanges()
                doFrame()
                assertLastRecomposeHadChanges()

                Assert.assertEquals(4, testCase.primaryCompositions)
                Assert.assertEquals(1, testCase.secondaryCompositions)
            }
    }

    @Test
    fun testImmutablePalette() {
        val testCase = ImmutableColorPaletteTestCase()
        composeTestRule
            .forGivenTestCase(testCase)
            .performTestWithEventsControl {
                doFrame()
                assertNoPendingChanges()

                Assert.assertEquals(2, testCase.primaryCompositions)
                Assert.assertEquals(1, testCase.secondaryCompositions)

                doFrame()
                assertNoPendingChanges()

                testCase.toggleState()

                doFrame()
                assertLastRecomposeHadChanges()

                Assert.assertEquals(4, testCase.primaryCompositions)
                Assert.assertEquals(2, testCase.secondaryCompositions)
            }
    }
}

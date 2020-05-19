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

package androidx.ui.test

import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.core.TestTag
import androidx.ui.foundation.Box
import androidx.ui.foundation.Canvas
import androidx.ui.graphics.Color
import androidx.ui.layout.Column
import androidx.ui.layout.preferredSize
import androidx.ui.semantics.ScrollTo
import androidx.ui.semantics.Semantics
import androidx.ui.unit.dp
import com.google.common.truth.Truth
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

class ScrollToTest {
    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun checkSemanticsAction_scrollTo_isCalled() {
        var wasScrollToCalled = false
        val tag = "myTag"

        composeTestRule.setContent {
            Semantics(container = true, properties = {
                ScrollTo(action = { _, _ ->
                    wasScrollToCalled = true
                    return@ScrollTo true
                })
            }) {
                Box {
                    TestTag(tag) {
                        Semantics(container = true) {
                            Box()
                        }
                    }
                }
            }
        }

        runOnIdleCompose {
            Assert.assertTrue(!wasScrollToCalled)
        }

        findByTag(tag)
            .doScrollTo()

        runOnIdleCompose {
            Assert.assertTrue(wasScrollToCalled)
        }
    }

    @Test
    fun checkSemanticsAction_scrollTo_coordAreCorrect() {
        var currentScrollPositionY = 0.0f
        var currentScrollPositionX = 0.0f
        var elementHeight = 0.0f
        val tag = "myTag"

        val drawRect = @Composable { color: Color ->
            Semantics(container = true) {
                Canvas(Modifier.preferredSize(100.dp)) {
                    drawRect(color)

                    elementHeight = size.height
                }
            }
        }

        composeTestRule.setContent {
            // Need to make the "scrolling" container the semantics boundary so that it
            // doesn't try to include the padding
            Semantics(container = true, properties = {
                ScrollTo(action = { x, y ->
                    currentScrollPositionY = y.value
                    currentScrollPositionX = x.value
                    return@ScrollTo true
                })
            }) {
                val red = Color(alpha = 0xFF, red = 0xFF, green = 0, blue = 0)
                val blue = Color(alpha = 0xFF, red = 0, green = 0, blue = 0xFF)
                val green = Color(alpha = 0xFF, red = 0, green = 0xFF, blue = 0)

                Column {
                    drawRect(red)
                    drawRect(blue)
                    TestTag(tag) {
                        drawRect(green)
                    }
                }
            }
        }

        runOnIdleCompose {
            Truth.assertThat(currentScrollPositionY).isEqualTo(0.0f)
            Truth.assertThat(currentScrollPositionX).isEqualTo(0.0f)
        }

        findByTag(tag)
            .doScrollTo() // scroll to third element

        runOnIdleCompose {
            val expected = elementHeight * 2
            Truth.assertThat(currentScrollPositionY).isEqualTo(expected)
            Truth.assertThat(currentScrollPositionX).isEqualTo(0.0f)
        }
    }
}

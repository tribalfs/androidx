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

import androidx.compose.Composable
import androidx.compose.Model
import androidx.test.filters.MediumTest
import androidx.ui.core.Alignment
import androidx.ui.core.Layout
import androidx.ui.core.Modifier
import androidx.ui.foundation.Text
import androidx.ui.foundation.VerticalScroller
import androidx.ui.layout.Column
import androidx.ui.layout.Row
import androidx.ui.layout.Stack
import androidx.ui.layout.padding
import androidx.ui.semantics.Semantics
import androidx.ui.text.TextStyle
import androidx.ui.unit.IntPx
import androidx.ui.unit.dp
import androidx.ui.unit.ipx
import androidx.ui.unit.sp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@MediumTest
@RunWith(JUnit4::class)
class IsDisplayedTests {

    @get:Rule
    val composeTestRule = createComposeRule(disableTransitions = true)

    @Test
    fun componentInScrollable_isDisplayed() {
        createScrollableContent()

        findByText("2")
            .assertIsDisplayed()
    }

    @Test
    fun componentInScrollable_isNotDisplayed() {
        createScrollableContent()

        findByText("50")
            .assertIsNotDisplayed()
    }

    private fun createScrollableContent() {
        composeTestRule.setContent {
            val style = TextStyle(fontSize = 30.sp)
            VerticalScroller(modifier = Modifier.padding(10.dp)) {
                Column {
                    for (i in 1..100) {
                        Semantics(container = true) {
                            Text(text = i.toString(), style = style)
                        }
                    }
                }
            }
        }
    }

    @Test
    fun toggleParentVisibility() {
        /*
        - topNode
        -- secondNode
        --- thirdNode
        ---- Text
         */

        val model = AssertsUiTestsModel(true)

        composeTestRule.setContent {
            val lastNode = @Composable {
                Stack {
                    Semantics(container = true) {
                        Text("Foo")
                    }
                }
            }

            val thirdNode = @Composable {
                Stack {
                    lastNode()
                }
            }

            val secondNode = @Composable {
                Layout({
                    thirdNode()
                }) { measurables, constraints, _ ->
                    if (model.value) {
                        val placeable = measurables[0].measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(0.ipx, 0.ipx)
                        }
                    } else {
                        layout(0.ipx, 0.ipx) {}
                    }
                }
            }

            val topNode = @Composable {
                Layout({
                    secondNode()
                }) { measurables, constraints, _ ->
                    if (model.value) {
                        val placeable = measurables[0].measure(constraints)
                        layout(placeable.width, placeable.height) {
                            placeable.place(0.ipx, 0.ipx)
                        }
                    } else {
                        layout(0.ipx, 0.ipx) {
                        }
                    }
                }
            }

            topNode()
        }

        findByText("Foo")
            .assertIsDisplayed()

        runOnUiThread {
            model.value = false
        }

        findByText("Foo")
            .assertIsNotDisplayed()
    }

    @Test
    fun rowTooSmall() {
        composeTestRule.setContent {
            val style = TextStyle(fontSize = 30.sp)
            Stack {
                // TODO(popam): remove this when a modifier can be used instead
                Layout(
                    children = {
                        Row {
                            for (i in 1..100) {
                                Semantics(container = true) {
                                    Text(text = i.toString(), style = style)
                                }
                            }
                        }
                    },
                    modifier = Modifier.gravity(Alignment.Center)
                ) { measurables, constraints, _ ->
                    val placeable =
                        measurables[0].measure(constraints.copy(maxWidth = IntPx.Infinity))
                    layout(placeable.width, placeable.height) {
                        placeable.place(0.ipx, 0.ipx)
                    }
                }
            }
        }

        findByText("90")
            .assertIsNotDisplayed()
    }
}

@Model
class AssertsUiTestsModel(var value: Boolean)
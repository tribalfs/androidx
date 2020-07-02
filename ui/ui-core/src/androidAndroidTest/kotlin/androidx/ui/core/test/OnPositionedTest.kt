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

package androidx.ui.core.test

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.compose.Recomposer
import androidx.compose.getValue
import androidx.compose.mutableStateOf
import androidx.compose.setValue
import androidx.test.filters.SmallTest
import androidx.ui.core.Layout
import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.Modifier
import androidx.ui.core.boundsInParent
import androidx.ui.core.globalPosition
import androidx.ui.core.onChildPositioned
import androidx.ui.core.onPositioned
import androidx.ui.core.positionInParent
import androidx.ui.core.setContent
import androidx.ui.framework.test.TestActivity
import androidx.ui.unit.PxBounds
import androidx.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
@RunWith(JUnit4::class)
class OnPositionedTest {

    @Suppress("DEPRECATION")
    @get:Rule
    val rule = androidx.test.rule.ActivityTestRule<TestActivity>(TestActivity::class.java)
    private lateinit var activity: TestActivity

    @Before
    fun setup() {
        activity = rule.activity
        activity.hasFocusLatch.await(5, TimeUnit.SECONDS)
    }

    @Test
    fun handlesChildrenNodeMoveCorrectly() {
        val size = 50
        var index by mutableStateOf(0)
        var latch = CountDownLatch(2)
        var wrap1Position = 0f
        var wrap2Position = 0f
        rule.runOnUiThread {
            activity.setContent {
                SimpleRow {
                    for (i in 0 until 2) {
                        if (index == i) {
                            Wrap(
                                minWidth = size,
                                minHeight = size,
                                modifier = Modifier.onPositioned { coordinates ->
                                    wrap1Position = coordinates.globalPosition.x
                                    latch.countDown()
                                }
                            )
                        } else {
                            Wrap(
                                minWidth = size,
                                minHeight = size,
                                modifier = Modifier.onPositioned { coordinates ->
                                    wrap2Position = coordinates.globalPosition.x
                                    latch.countDown()
                                }
                            )
                        }
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(0f, wrap1Position)
        assertEquals(size.toFloat(), wrap2Position)
        latch = CountDownLatch(2)
        rule.runOnUiThread {
            index = 1
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(size.toFloat(), wrap1Position)
        assertEquals(0f, wrap2Position)
    }

    @Test
    fun callbacksAreCalledWhenChildResized() {
        var size by mutableStateOf(10)
        var realSize = 0
        var realChildSize = 0
        var latch = CountDownLatch(1)
        var childLatch = CountDownLatch(1)
        rule.runOnUiThreadIR {
            activity.setContent {
                AtLeastSize(size = 20, modifier = Modifier.onChildPositioned {
                    realSize = it.size.width
                    latch.countDown()
                }) {
                    Wrap(minWidth = size, minHeight = size, modifier = Modifier.onPositioned {
                        realChildSize = it.size.width
                        childLatch.countDown()
                    })
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertTrue(childLatch.await(1, TimeUnit.SECONDS))
        assertEquals(10, realSize)
        assertEquals(10, realChildSize)

        latch = CountDownLatch(1)
        childLatch = CountDownLatch(1)
        rule.runOnUiThread {
            size = 15
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertTrue(childLatch.await(1, TimeUnit.SECONDS))
        assertEquals(15, realSize)
        assertEquals(15, realChildSize)
    }

    @Test
    fun callbackCalledForChildWhenParentMoved() {
        var position by mutableStateOf(0)
        var childGlobalPosition = Offset(0f, 0f)
        var latch = CountDownLatch(1)
        rule.runOnUiThreadIR {
            activity.setContent {
                Layout(
                    measureBlock = { measurables, constraints ->
                        layout(10, 10) {
                            measurables[0].measure(constraints).place(position, 0)
                        }
                    },
                    children = {
                        Wrap(
                            minWidth = 10,
                            minHeight = 10
                        ) {
                            Wrap(
                                minWidth = 10,
                                minHeight = 10,
                                modifier = Modifier.onPositioned { coordinates ->
                                    childGlobalPosition = coordinates.globalPosition
                                    latch.countDown()
                                }
                            )
                        }
                    }
                )
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))

        latch = CountDownLatch(1)
        rule.runOnUiThread {
            position = 10
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertEquals(Offset(10f, 0f), childGlobalPosition)
    }

    @Test
    fun callbacksAreCalledOnlyForPositionedChildren() {
        val latch = CountDownLatch(1)
        var wrap1OnPositionedCalled = false
        var wrap2OnPositionedCalled = false
        var onChildPositionedCalledTimes = 0
        rule.runOnUiThread {
            activity.setContent {
                Layout(
                    modifier = Modifier.onChildPositioned {
                        onChildPositionedCalledTimes++
                    },
                    measureBlock = { measurables, constraints ->
                        layout(10, 10) {
                            measurables[1].measure(constraints).place(0, 0)
                        }
                    },
                    children = {
                        Wrap(
                            minWidth = 10,
                            minHeight = 10,
                            modifier = Modifier.onPositioned {
                                wrap1OnPositionedCalled = true
                            }
                        )
                        Wrap(
                            minWidth = 10,
                            minHeight = 10,
                            modifier = Modifier.onPositioned {
                                wrap2OnPositionedCalled = true
                            }
                        ) {
                            Wrap(
                                minWidth = 10,
                                minHeight = 10,
                                modifier = Modifier.onPositioned {
                                    latch.countDown()
                                }
                            )
                        }
                    }
                )
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
        assertFalse(wrap1OnPositionedCalled)
        assertTrue(wrap2OnPositionedCalled)
        assertEquals(1, onChildPositionedCalledTimes)
    }

    @Test
    fun testPositionInParent() {
        val positionedLatch = CountDownLatch(1)
        var coordinates: LayoutCoordinates? = null

        rule.runOnUiThread {
            activity.setContent {
                FixedSize(10,
                    PaddingModifier(5) +
                            Modifier.onPositioned {
                                coordinates = it
                                positionedLatch.countDown()
                            }
                ) {
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        rule.runOnUiThread {
            assertEquals(Offset(5f, 5f), coordinates!!.positionInParent)

            var root = coordinates!!
            while (root.parentCoordinates != null) {
                root = root.parentCoordinates!!
            }

            assertEquals(Offset.Zero, root.positionInParent)
        }
    }

    @Test
    fun testBoundsInParent() {
        val positionedLatch = CountDownLatch(1)
        var coordinates: LayoutCoordinates? = null

        rule.runOnUiThread {
            activity.setContent {
                FixedSize(10,
                    PaddingModifier(5) +
                            Modifier.onPositioned {
                                coordinates = it
                                positionedLatch.countDown()
                            }
                ) {
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))

        rule.runOnUiThread {
            assertEquals(PxBounds(5f, 5f, 15f, 15f), coordinates!!.boundsInParent)

            var root = coordinates!!
            while (root.parentCoordinates != null) {
                root = root.parentCoordinates!!
            }

            assertEquals(PxBounds(0f, 0f, 20f, 20f), root.boundsInParent)
        }
    }

    @Test
    fun onPositionedIsCalledWhenComposeContainerIsScrolled() {
        var positionedLatch = CountDownLatch(1)
        var coordinates: LayoutCoordinates? = null
        var scrollView: ScrollView? = null

        rule.runOnUiThread {
            scrollView = ScrollView(rule.activity)
            activity.setContentView(scrollView, ViewGroup.LayoutParams(100, 100))
            val frameLayout = FrameLayout(rule.activity)
            scrollView!!.addView(frameLayout)
            frameLayout.setContent(Recomposer.current()) {
                Layout({}, modifier = Modifier.onPositioned {
                    coordinates = it
                    positionedLatch.countDown()
                }) { _, _ ->
                    layout(100, 200) {}
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))
        val startY = coordinates!!.globalPosition.y
        positionedLatch = CountDownLatch(1)

        rule.runOnUiThread {
            scrollView!!.scrollBy(0, 50)
        }

        assertTrue(
            "OnPositioned is not called when the container scrolled",
            positionedLatch.await(1, TimeUnit.SECONDS)
        )
        assertEquals(startY - 50f, coordinates!!.globalPosition.y)
    }

    @Test
    fun onPositionedIsCalledWhenComposeContainerPositionChanged() {
        var positionedLatch = CountDownLatch(1)
        var coordinates: LayoutCoordinates? = null
        var topView: View? = null

        rule.runOnUiThread {
            val linearLayout = LinearLayout(rule.activity)
            linearLayout.orientation = LinearLayout.VERTICAL
            activity.setContentView(linearLayout, ViewGroup.LayoutParams(100, 200))
            topView = View(rule.activity)
            linearLayout.addView(topView!!, ViewGroup.LayoutParams(100, 100))
            val frameLayout = FrameLayout(rule.activity)
            linearLayout.addView(frameLayout, ViewGroup.LayoutParams(100, 100))
            frameLayout.setContent(Recomposer.current()) {
                Layout({}, modifier = Modifier.onPositioned {
                    coordinates = it
                    positionedLatch.countDown()
                }) { _, constraints ->
                    layout(constraints.maxWidth, constraints.maxHeight) {}
                }
            }
        }
        assertTrue(positionedLatch.await(1, TimeUnit.SECONDS))
        val startY = coordinates!!.globalPosition.y
        positionedLatch = CountDownLatch(1)

        rule.runOnUiThread {
            topView!!.visibility = View.GONE
        }

        assertTrue("OnPositioned is not called when the container moved",
            positionedLatch.await(1, TimeUnit.SECONDS))
        assertEquals(startY - 100f, coordinates!!.globalPosition.y)
    }
}
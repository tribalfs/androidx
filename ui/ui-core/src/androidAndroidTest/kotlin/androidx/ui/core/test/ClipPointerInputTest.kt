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

package androidx.ui.core.test

import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.compose.Composable
import androidx.compose.emptyContent
import androidx.test.filters.SmallTest
import androidx.ui.core.DensityAmbient
import androidx.ui.core.Layout
import androidx.ui.core.Modifier
import androidx.ui.core.PointerEventPass
import androidx.ui.core.PointerInputChange
import androidx.ui.core.clipToBounds
import androidx.ui.core.gesture.MotionEvent
import androidx.ui.core.gesture.PointerCoords
import androidx.ui.core.gesture.PointerProperties
import androidx.ui.core.pointerinput.PointerInputFilter
import androidx.ui.core.pointerinput.PointerInputModifier
import androidx.ui.core.setContent
import androidx.ui.framework.test.TestActivity
import androidx.ui.geometry.Offset
import androidx.ui.layout.offset
import androidx.ui.unit.IntSize
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
@RunWith(JUnit4::class)
class ClipPointerInputTest {

    @Suppress("DEPRECATION")
    @get:Rule
    val rule = androidx.test.rule.ActivityTestRule<TestActivity>(TestActivity::class.java)
    private lateinit var activity: TestActivity
    private lateinit var view: View

    @Before
    fun setup() {
        activity = rule.activity
        activity.hasFocusLatch.await(5, TimeUnit.SECONDS)
    }

    /**
     * This test creates a layout of this shape.
     *
     *     0   1   2   3   4
     *   .........   .........
     * 0 .     t .   . t     .
     *   .   |---|---|---|   .
     * 1 . t | t |   | t | t .
     *   ....|---|   |---|....
     * 2     |           |
     *   ....|---|   |---|....
     * 3 . t | t |   | t | t .
     *   .   |---|---|---|   .
     * 4 .     t .   . t     .
     *   .........   .........
     *
     * 4 LayoutNodes with PointerInputModifiers that are positioned by offset modifiers and where
     * pointer input is clipped by a modifier on the parent. 4 touches touch just inside the
     * parent LayoutNode and inside the child LayoutNodes. 8 touches touch just outside the
     * parent LayoutNode but inside the child LayoutNodes.
     *
     * Because clipToBounds is being used on the parent LayoutNode, only the 4 touches inside the
     * parent LayoutNode should hit.
     */
    @Test
    fun clipToBounds_childrenOffsetViaLayout_onlyCorrectPointersHit() {

        val setupLatch = CountDownLatch(2)

        val loggingPim1 = LoggingPim()
        val loggingPim2 = LoggingPim()
        val loggingPim3 = LoggingPim()
        val loggingPim4 = LoggingPim()

        rule.runOnUiThreadIR {
            activity.setContent {

                val children = @Composable {
                    child(loggingPim1)
                    child(loggingPim2)
                    child(loggingPim3)
                    child(loggingPim4)
                }

                val middle = @Composable {
                    Layout(
                        children = children,
                        modifier = Modifier.clipToBounds()
                    ) { measurables, constraints ->
                        val placeables = measurables.map { m ->
                            m.measure(constraints)
                        }
                        layout(3, 3) {
                            placeables[0].place((-1), (-1))
                            placeables[1].place(2, (-1))
                            placeables[2].place((-1), 2)
                            placeables[3].place(2, 2)
                        }
                    }
                }

                Layout(children = middle) { measurables, constraints ->
                    val placeables = measurables.map { m ->
                        m.measure(constraints)
                    }
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        placeables[0].place(1, 1)
                        setupLatch.countDown()
                    }
                }
            }

            view = activity.findViewById<ViewGroup>(android.R.id.content)
            setupLatch.countDown()
        }

        assertThat(setupLatch.await(2, TimeUnit.SECONDS)).isTrue()

        val offsetsThatHit =
            listOf(
                Offset(1f, 1f),
                Offset(3f, 1f),
                Offset(1f, 3f),
                Offset(3f, 3f)
            )
        val offsetsThatMiss =
            listOf(
                Offset(1f, 0f),
                Offset(3f, 0f),
                Offset(0f, 1f),
                Offset(4f, 1f),
                Offset(0f, 3f),
                Offset(4f, 3f),
                Offset(1f, 4f),
                Offset(3f, 4f)
            )

        val downEvents = mutableListOf<MotionEvent>()
        (offsetsThatHit + offsetsThatMiss).forEachIndexed { index, value ->
            downEvents.add(
                MotionEvent(
                    index,
                    MotionEvent.ACTION_DOWN,
                    1,
                    0,
                    arrayOf(PointerProperties(0)),
                    arrayOf(PointerCoords(value.x, value.y)),
                    view
                )
            )
        }

        // Act
        rule.runOnUiThreadIR {
            downEvents.forEach {
                view.dispatchTouchEvent(it)
            }
        }

        // Assert

        assertThat(loggingPim1.log).isEqualTo(listOf(Offset(1f, 1f)))
        assertThat(loggingPim2.log).isEqualTo(listOf(Offset(0f, 1f)))
        assertThat(loggingPim3.log).isEqualTo(listOf(Offset(1f, 0f)))
        assertThat(loggingPim4.log).isEqualTo(listOf(Offset(0f, 0f)))
    }

    /**
     * This test creates a layout of this shape.
     *
     *     0   1   2   3   4
     *   .........   .........
     * 0 .     t .   . t     .
     *   .   |---|---|---|   .
     * 1 . t | t |   | t | t .
     *   ....|---|   |---|....
     * 2     |           |
     *   ....|---|   |---|....
     * 3 . t | t |   | t | t .
     *   .   |---|---|---|   .
     * 4 .     t .   . t     .
     *   .........   .........
     *
     * 4 LayoutNodes with PointerInputModifiers that are positioned by offset modifiers and where
     * pointer input is clipped by a modifier on the parent. 4 touches touch just inside the
     * parent LayoutNode and inside the child LayoutNodes. 8 touches touch just outside the
     * parent LayoutNode but inside the child LayoutNodes.
     *
     * Because clipToBounds is being used on the parent LayoutNode, only the 4 touches inside the
     * parent LayoutNode should hit.
     */
    @Test
    fun clipToBounds_childrenOffsetViaModifier_onlyCorrectPointersHit() {

        val setupLatch = CountDownLatch(2)

        val loggingPim1 = LoggingPim()
        val loggingPim2 = LoggingPim()
        val loggingPim3 = LoggingPim()
        val loggingPim4 = LoggingPim()

        rule.runOnUiThreadIR {
            activity.setContent {

                with(DensityAmbient.current) {

                    val children = @Composable {
                        child(Modifier.offset((-1f).toDp(), (-1f).toDp()).plus(loggingPim1))
                        child(Modifier.offset(2f.toDp(), (-1f).toDp()).plus(loggingPim2))
                        child(Modifier.offset((-1f).toDp(), 2f.toDp()).plus(loggingPim3))
                        child(Modifier.offset(2f.toDp(), 2f.toDp()).plus(loggingPim4))
                    }

                    val middle = @Composable {
                        Layout(
                            children = children,
                            modifier = Modifier.clipToBounds()
                        ) { measurables, constraints ->
                            val placeables = measurables.map { m ->
                                m.measure(constraints)
                            }
                            layout(3, 3) {
                                placeables.forEach { it.place(0, 0) }
                            }
                        }
                    }

                    Layout(children = middle) { measurables, constraints ->
                        val placeables = measurables.map { m ->
                            m.measure(constraints)
                        }
                        layout(constraints.maxWidth, constraints.maxHeight) {
                            placeables[0].place(1, 1)
                            setupLatch.countDown()
                        }
                    }
                }
            }

            view = activity.findViewById<ViewGroup>(android.R.id.content)
            setupLatch.countDown()
        }

        assertThat(setupLatch.await(2, TimeUnit.SECONDS)).isTrue()

        val offsetsThatHit =
            listOf(
                Offset(1f, 1f),
                Offset(3f, 1f),
                Offset(1f, 3f),
                Offset(3f, 3f)
            )
        val offsetsThatMiss =
            listOf(
                Offset(1f, 0f),
                Offset(3f, 0f),
                Offset(0f, 1f),
                Offset(4f, 1f),
                Offset(0f, 3f),
                Offset(4f, 3f),
                Offset(1f, 4f),
                Offset(3f, 4f)
            )

        val downEvents = mutableListOf<MotionEvent>()
        (offsetsThatHit + offsetsThatMiss).forEachIndexed { index, value ->
            downEvents.add(
                MotionEvent(
                    index,
                    MotionEvent.ACTION_DOWN,
                    1,
                    0,
                    arrayOf(PointerProperties(0)),
                    arrayOf(PointerCoords(value.x, value.y)),
                    view
                )
            )
        }

        // Act
        rule.runOnUiThreadIR {
            downEvents.forEach {
                view.dispatchTouchEvent(it)
            }
        }

        // Assert

        assertThat(loggingPim1.log).isEqualTo(listOf(Offset(1f, 1f)))
        assertThat(loggingPim2.log).isEqualTo(listOf(Offset(0f, 1f)))
        assertThat(loggingPim3.log).isEqualTo(listOf(Offset(1f, 0f)))
        assertThat(loggingPim4.log).isEqualTo(listOf(Offset(0f, 0f)))
    }

    @Composable
    fun child(modifier: Modifier) {
        Layout(children = emptyContent(), modifier = modifier) { _, _ ->
            layout(2, 2) {}
        }
    }

    class LoggingPim : PointerInputModifier {
        val log = mutableListOf<Offset>()

        override val pointerInputFilter = object : PointerInputFilter() {
            override fun onPointerInput(
                changes: List<PointerInputChange>,
                pass: PointerEventPass,
                bounds: IntSize
            ): List<PointerInputChange> {
                if (pass == PointerEventPass.InitialDown) {
                    changes.forEach {
                        println("testtest, bounds: $bounds")
                        log.add(it.current.position!!)
                    }
                }
                return changes
            }

            override fun onCancel() {
                // Nothing
            }
        }
    }
}

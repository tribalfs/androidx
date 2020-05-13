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

import androidx.compose.FrameManager
import androidx.compose.mutableStateOf
import androidx.test.filters.SmallTest
import androidx.test.rule.ActivityTestRule
import androidx.ui.core.Layout
import androidx.ui.core.Modifier
import androidx.ui.core.drawBehind
import androidx.ui.core.setContent
import androidx.ui.framework.test.TestActivity
import androidx.ui.unit.ipx
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
class ModelReadsTest {

    @get:Rule
    val rule = ActivityTestRule<TestActivity>(TestActivity::class.java)
    @get:Rule
    val excessiveAssertions = AndroidOwnerExtraAssertionsRule()
    private lateinit var activity: TestActivity
    private lateinit var latch: CountDownLatch

    @Before
    fun setup() {
        activity = rule.activity
        activity.hasFocusLatch.await(5, TimeUnit.SECONDS)
        latch = CountDownLatch(1)
    }

    @Test
    fun useTheSameModelInDrawAndPosition() {
        val model = OffsetModel(5.ipx)
        var drawLatch = CountDownLatch(1)
        var positionLatch = CountDownLatch(1)
        rule.runOnUiThread {
            activity.setContent {
                Layout({}, modifier = Modifier.drawBehind {
                    // read from the model
                    model.offset
                    drawLatch.countDown()
                }) { _, _, _ ->
                    layout(10.ipx, 10.ipx) {
                        // read from the model
                        model.offset
                        positionLatch.countDown()
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertTrue(positionLatch.await(1, TimeUnit.SECONDS))

        drawLatch = CountDownLatch(1)
        positionLatch = CountDownLatch(1)
        rule.runOnUiThread {
            model.offset = 7.ipx
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertTrue(positionLatch.await(1, TimeUnit.SECONDS))

        drawLatch = CountDownLatch(1)
        positionLatch = CountDownLatch(1)
        rule.runOnUiThread {
            model.offset = 10.ipx
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertTrue(positionLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun useDifferentModelsInDrawAndPosition() {
        val drawModel = OffsetModel(5.ipx)
        val positionModel = OffsetModel(5.ipx)
        var drawLatch = CountDownLatch(1)
        var positionLatch = CountDownLatch(1)
        rule.runOnUiThread {
            activity.setContent {
                Layout({}, modifier = Modifier.drawBehind {
                    // read from the model
                    drawModel.offset
                    drawLatch.countDown()
                }) { _, _, _ ->
                    layout(10.ipx, 10.ipx) {
                        // read from the model
                        positionModel.offset
                        positionLatch.countDown()
                    }
                }
            }
        }
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertTrue(positionLatch.await(1, TimeUnit.SECONDS))

        drawLatch = CountDownLatch(1)
        positionLatch = CountDownLatch(1)
        rule.runOnUiThread {
            drawModel.offset = 7.ipx
        }

        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertFalse(positionLatch.await(200, TimeUnit.MILLISECONDS))

        drawLatch = CountDownLatch(1)
        positionLatch = CountDownLatch(1)
        rule.runOnUiThread {
            positionModel.offset = 10.ipx
        }

        assertTrue(positionLatch.await(1, TimeUnit.SECONDS))
        assertFalse(drawLatch.await(200, TimeUnit.MILLISECONDS))
    }

    @Test
    fun useTheSameModelInMeasureAndPosition() {
        val model = OffsetModel(5.ipx)
        var measureLatch = CountDownLatch(1)
        var drawLatch = CountDownLatch(1)
        rule.runOnUiThread {
            activity.setContent {
                Layout({}, modifier = Modifier.drawBehind {
                    // read from the model
                    model.offset
                    drawLatch.countDown()
                }) { _, _, _ ->
                    measureLatch.countDown()
                    // read from the model
                    layout(model.offset, 10.ipx) {}
                }
            }
        }
        assertTrue(measureLatch.await(1, TimeUnit.SECONDS))
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        measureLatch = CountDownLatch(1)
        drawLatch = CountDownLatch(1)
        rule.runOnUiThread {
            model.offset = 10.ipx
        }

        assertTrue(measureLatch.await(1, TimeUnit.SECONDS))
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))

        measureLatch = CountDownLatch(1)
        drawLatch = CountDownLatch(1)
        rule.runOnUiThread {
            model.offset = 15.ipx
        }

        assertTrue(measureLatch.await(1, TimeUnit.SECONDS))
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun useDifferentModelsInMeasureAndPosition() {
        val measureModel = OffsetModel(5.ipx)
        val positionModel = OffsetModel(5.ipx)
        var measureLatch = CountDownLatch(1)
        var positionLatch = CountDownLatch(1)
        rule.runOnUiThread {
            activity.setContent {
                Layout({}) { _, _, _ ->
                    measureLatch.countDown()
                    // read from the model
                    layout(measureModel.offset, 10.ipx) {
                        // read from the model
                        positionModel.offset
                        positionLatch.countDown()
                    }
                }
            }
        }
        assertTrue(measureLatch.await(1, TimeUnit.SECONDS))
        assertTrue(positionLatch.await(1, TimeUnit.SECONDS))

        measureLatch = CountDownLatch(1)
        positionLatch = CountDownLatch(1)
        rule.runOnUiThread {
            measureModel.offset = 10.ipx
        }

        assertTrue(measureLatch.await(1, TimeUnit.SECONDS))
        // remeasuring automatically triggers relayout
        assertTrue(positionLatch.await(1, TimeUnit.SECONDS))

        measureLatch = CountDownLatch(1)
        positionLatch = CountDownLatch(1)
        rule.runOnUiThread {
            positionModel.offset = 15.ipx
        }

        assertFalse(measureLatch.await(200, TimeUnit.MILLISECONDS))
        assertTrue(positionLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun drawReactsOnCorrectModelsChanges() {
        val enabled = ValueModel(true)
        val model = ValueModel(0)
        rule.runOnUiThread {
            activity.setContent {
                AtLeastSize(10.ipx, modifier = Modifier.drawBehind {
                    if (enabled.value) {
                        // read the model
                        model.value
                    }
                    latch.countDown()
                }) {
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun measureReactsOnCorrectModelsChanges() {
        val enabled = ValueModel(true)
        val model = ValueModel(0)
        rule.runOnUiThread {
            activity.setContent {
                Layout({}) { _, _, _ ->
                    if (enabled.value) {
                        // read the model
                        model.value
                    }
                    latch.countDown()
                    layout(10.ipx, 10.ipx) {}
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertCountDownOnlyWhileEnabled(enabled, model)
    }

    @Test
    fun layoutReactsOnCorrectModelsChanges() {
        val enabled = ValueModel(true)
        val model = ValueModel(0)
        rule.runOnUiThread {
            activity.setContent {
                Layout({}) { _, _, _ ->
                    layout(10.ipx, 10.ipx) {
                        if (enabled.value) {
                            // read the model
                            model.value
                        }
                        latch.countDown()
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertCountDownOnlyWhileEnabled(enabled, model)
    }

    @Test
    fun drawStopsReactingOnModelsAfterDetaching() {
        val enabled = ValueModel(true)
        val model = ValueModel(0)
        rule.runOnUiThread {
            activity.setContent {
                val modifier = if (enabled.value) {
                    Modifier.drawBehind {
                        // read the model
                        model.value
                        latch.countDown()
                    }
                } else Modifier
                AtLeastSize(10.ipx, modifier = modifier) {}
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertCountDownOnlyWhileEnabled(enabled, model, false)
    }

    @Test
    fun measureStopsReactingOnModelsAfterDetaching() {
        val enabled = ValueModel(true)
        val model = ValueModel(0)
        rule.runOnUiThread {
            activity.setContent {
                if (enabled.value) {
                    Layout({}) { _, _, _ ->
                        // read the model
                        model.value
                        latch.countDown()
                        layout(10.ipx, 10.ipx) {}
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertCountDownOnlyWhileEnabled(enabled, model, false)
    }

    @Test
    fun layoutStopsReactingOnModelsAfterDetaching() {
        val enabled = ValueModel(true)
        val model = ValueModel(0)
        rule.runOnUiThread {
            activity.setContent {
                if (enabled.value) {
                    Layout({}) { _, _, _ ->
                        layout(10.ipx, 10.ipx) {
                            // read the model
                            model.value
                            latch.countDown()
                        }
                    }
                }
            }
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))

        assertCountDownOnlyWhileEnabled(enabled, model, false)
    }

    @Test
    fun remeasureRequestForTheNodeBeingMeasured() {
        var latch = CountDownLatch(1)
        val model = mutableStateOf(0)
        rule.runOnUiThread {
            activity.setContent {
                Layout({}) { _, _, _ ->
                    if (model.value == 1) {
                        // this will trigger remeasure request for this node we currently measure
                        model.value = 2
                        FrameManager.nextFrame()
                    }
                    latch.countDown()
                    layout(100.ipx, 100.ipx) {}
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))

        latch = CountDownLatch(1)

        rule.runOnUiThread {
            model.value = 1
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun remeasureRequestForTheNodeBeingLaidOut() {
        var remeasureLatch = CountDownLatch(1)
        var relayoutLatch = CountDownLatch(1)
        val remeasureModel = mutableStateOf(0)
        val relayoutModel = mutableStateOf(0)
        var valueReadDuringMeasure = -1
        var modelAlreadyChanged = false
        rule.runOnUiThread {
            activity.setContent {
                Layout({}) { _, _, _ ->
                    valueReadDuringMeasure = remeasureModel.value
                    remeasureLatch.countDown()
                    layout(100.ipx, 100.ipx) {
                        if (relayoutModel.value != 0) {
                            if (!modelAlreadyChanged) {
                                // this will trigger remeasure request for this node we layout
                                remeasureModel.value = 1
                                FrameManager.nextFrame()
                                // the remeasure will also include another relayout and we don't
                                // want to loop and request remeasure again
                                modelAlreadyChanged = true
                            }
                        }
                        relayoutLatch.countDown()
                    }
                }
            }
        }

        assertTrue(remeasureLatch.await(1, TimeUnit.SECONDS))
        assertTrue(relayoutLatch.await(1, TimeUnit.SECONDS))

        remeasureLatch = CountDownLatch(1)
        relayoutLatch = CountDownLatch(1)

        rule.runOnUiThread {
            relayoutModel.value = 1
        }

        assertTrue(remeasureLatch.await(1, TimeUnit.HOURS))
        assertTrue(relayoutLatch.await(1, TimeUnit.HOURS))
        assertEquals(1, valueReadDuringMeasure)
    }

    @Test
    fun relayoutRequestForTheNodeBeingMeasured() {
        var remeasureLatch = CountDownLatch(1)
        var relayoutLatch = CountDownLatch(1)
        val remeasureModel = mutableStateOf(0)
        val relayoutModel = mutableStateOf(0)
        rule.runOnUiThread {
            activity.setContent {
                Layout({}) { _, _, _ ->
                    if (remeasureModel.value != 0) {
                        // this will trigger relayout request for this node we currently measure
                        relayoutModel.value = 1
                        FrameManager.nextFrame()
                    }
                    remeasureLatch.countDown()
                    layout(100.ipx, 100.ipx) {
                        relayoutModel.value // just register the read
                        relayoutLatch.countDown()
                    }
                }
            }
        }

        assertTrue(remeasureLatch.await(1, TimeUnit.SECONDS))
        assertTrue(relayoutLatch.await(1, TimeUnit.SECONDS))

        remeasureLatch = CountDownLatch(1)
        relayoutLatch = CountDownLatch(1)

        rule.runOnUiThread {
            remeasureModel.value = 1
        }

        assertTrue(remeasureLatch.await(1, TimeUnit.SECONDS))
        assertTrue(relayoutLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun relayoutRequestForTheNodeBeingLaidOut() {
        var latch = CountDownLatch(1)
        val model = mutableStateOf(0)
        rule.runOnUiThread {
            activity.setContent {
                Layout({}) { _, _, _ ->
                    layout(100.ipx, 100.ipx) {
                        if (model.value == 1) {
                            // this will trigger relayout request for this node we currently layout
                            model.value = 2
                            FrameManager.nextFrame()
                        }
                        latch.countDown()
                    }
                }
            }
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))

        latch = CountDownLatch(1)

        rule.runOnUiThread {
            model.value = 1
        }

        assertTrue(latch.await(1, TimeUnit.SECONDS))
    }

    fun assertCountDownOnlyWhileEnabled(
        enableModel: ValueModel<Boolean>,
        valueModel: ValueModel<Int>,
        triggeredByEnableSwitch: Boolean = true
    ) {
        latch = CountDownLatch(1)
        rule.runOnUiThread {
            valueModel.value++
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))

        latch = CountDownLatch(1)
        rule.runOnUiThread {
            enableModel.value = false
        }
        if (triggeredByEnableSwitch) {
            assertTrue(latch.await(1, TimeUnit.SECONDS))
        } else {
            assertFalse(latch.await(200, TimeUnit.MILLISECONDS))
        }

        latch = CountDownLatch(1)
        rule.runOnUiThread {
            valueModel.value++
        }
        assertFalse(latch.await(200, TimeUnit.MILLISECONDS))
    }
}

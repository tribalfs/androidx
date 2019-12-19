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

package androidx.ui.core

import androidx.compose.FrameManager
import androidx.compose.annotations.Hide
import androidx.compose.frames.AbstractRecord
import androidx.compose.frames.Framed
import androidx.compose.frames.Record
import androidx.compose.frames._created
import androidx.compose.frames.commit
import androidx.compose.frames.open
import androidx.compose.frames.readable
import androidx.compose.frames.writable
import androidx.test.filters.SmallTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SmallTest
@RunWith(JUnit4::class)
class ModelObserverTest {

    @Test
    fun modelChangeTriggersCallback() {
        val node = DrawNode()
        val countDownLatch = CountDownLatch(1)

        val model = State(0)
        val modelObserver = ModelObserver { it() }
        modelObserver.enableModelUpdatesObserving(true)

        open() // open the frame

        val onCommitListener: (DrawNode) -> Unit = { affectedNode ->
            assertEquals(node, affectedNode)
            assertEquals(1, countDownLatch.count)
            countDownLatch.countDown()
        }

        modelObserver.observeReads(node, onCommitListener) {
            // read the value
            model.value
        }

        model.value++
        commit() // close the frame

        modelObserver.enableModelUpdatesObserving(false)
        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun allThreeStagesWorksTogether() {
        val drawNode = DrawNode()
        val measureNode = LayoutNode()
        val layoutNode = LayoutNode()
        val drawLatch = CountDownLatch(1)
        val measureLatch = CountDownLatch(1)
        val layoutLatch = CountDownLatch(1)
        val drawModel = State(0)
        val measureModel = State(0)
        val layoutModel = State(0)

        val onCommitDrawListener: (DrawNode) -> Unit = { affectedNode ->
            assertEquals(drawNode, affectedNode)
            assertEquals(1, drawLatch.count)
            drawLatch.countDown()
        }
        val onCommitMeasureListener: (LayoutNode) -> Unit = { affectedNode ->
            assertEquals(measureNode, affectedNode)
            assertEquals(1, measureLatch.count)
            measureLatch.countDown()
        }
        val onCommitLayoutListener: (LayoutNode) -> Unit = { affectedNode ->
            assertEquals(layoutNode, affectedNode)
            assertEquals(1, layoutLatch.count)
            layoutLatch.countDown()
        }
        val modelObserver = ModelObserver { it() }

        modelObserver.enableModelUpdatesObserving(true)

        open() // open the frame

        modelObserver.observeReads(layoutNode, onCommitLayoutListener) {
            layoutModel.value
        }

        modelObserver.observeReads(measureNode, onCommitMeasureListener) {
            measureModel.value
        }

        modelObserver.observeReads(drawNode, onCommitDrawListener) {
            drawModel.value
        }

        drawModel.value++
        measureModel.value++
        layoutModel.value++
        commit() // close the frame

        modelObserver.enableModelUpdatesObserving(false)
        assertTrue(drawLatch.await(1, TimeUnit.SECONDS))
        assertTrue(measureLatch.await(1, TimeUnit.SECONDS))
        assertTrue(layoutLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun enclosedStagesCorrectlyObserveChanges() {
        val layoutNode1 = LayoutNode()
        val layoutNode2 = LayoutNode()
        val measureNode = LayoutNode()
        val layoutLatch1 = CountDownLatch(1)
        val layoutLatch2 = CountDownLatch(1)
        val measureLatch = CountDownLatch(1)
        val layoutModel1 = State(0)
        val layoutModel2 = State(0)
        val measureModel = State(0)

        val onCommitMeasureListener: (LayoutNode) -> Unit = { affectedNode ->
            assertEquals(affectedNode, measureNode)
            assertEquals(measureLatch.count, 1)
            measureLatch.countDown()
        }
        val onCommitLayoutListener: (LayoutNode) -> Unit = { affectedNode ->
            when (affectedNode) {
                layoutNode1 -> {
                    assertEquals(1, layoutLatch1.count)
                    layoutLatch1.countDown()
                }
                layoutNode2 -> {
                    assertEquals(1, layoutLatch2.count)
                    layoutLatch2.countDown()
                }
                measureNode -> {
                    throw IllegalStateException("measureNode called with Stage.Layout")
                }
            }
        }
        val modelObserver = ModelObserver { it() }

        modelObserver.enableModelUpdatesObserving(true)

        open() // open the frame

        modelObserver.observeReads(layoutNode1, onCommitLayoutListener) {
            layoutModel1.value
            modelObserver.observeReads(layoutNode2, onCommitLayoutListener) {
                layoutModel2.value
                modelObserver.observeReads(measureNode, onCommitMeasureListener) {
                    measureModel.value
                }
            }
        }

        layoutModel1.value++
        layoutModel2.value++
        measureModel.value++
        commit() // close the frame

        modelObserver.enableModelUpdatesObserving(false)
        assertTrue(layoutLatch1.await(1, TimeUnit.SECONDS))
        assertTrue(layoutLatch2.await(1, TimeUnit.SECONDS))
        assertTrue(measureLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun modelReadTriggersCallbackAfterSwitchingFrameWithinObserveReads() {
        val node = DrawNode()
        val countDownLatch = CountDownLatch(1)

        val model = State(0)
        val onCommitListener: (DrawNode) -> Unit = { _ ->
            assertEquals(1, countDownLatch.count)
            countDownLatch.countDown()
        }
        val modelObserver = ModelObserver { it() }

        modelObserver.enableModelUpdatesObserving(true)

        open() // open the frame

        modelObserver.observeReads(node, onCommitListener) {
            // switch to the next frame.
            // this will be done by subcomposition, for example.
            FrameManager.nextFrame()
            // read the value
            model.value
        }

        model.value++
        commit() // close the frame

        modelObserver.enableModelUpdatesObserving(false)
        assertTrue(countDownLatch.await(1, TimeUnit.SECONDS))
    }

    @Test
    fun pauseStopsObserving() {
        val node = LayoutNode()
        var commits = 0

        runSimpleTest { modelObserver, model ->
            modelObserver.observeReads(node, { _ -> commits++ }) {
                modelObserver.pauseObservingReads {
                    model.value
                }
            }
        }

        assertEquals(0, commits)
    }

    @Test
    fun nestedPauseStopsObserving() {
        val node = LayoutNode()
        var commits = 0

        runSimpleTest { modelObserver, model ->
            modelObserver.observeReads(node, { _ -> commits++ }) {
                modelObserver.pauseObservingReads {
                    modelObserver.pauseObservingReads {
                        model.value
                    }
                    model.value
                }
            }
        }

        assertEquals(0, commits)
    }

    @Test
    fun simpleObserving() {
        val node = LayoutNode()
        var commits = 0

        runSimpleTest { modelObserver, model ->
            modelObserver.observeReads(node, { _ -> commits++ }) {
                model.value
            }
        }

        assertEquals(1, commits)
    }

    @Test
    fun observeWithinPause() {
        val node = LayoutNode()
        var commits = 0
        var commits2 = 0

        runSimpleTest { modelObserver, model ->
            modelObserver.observeReads(node, { _ -> commits++ }) {
                modelObserver.pauseObservingReads {
                    modelObserver.observeReads(node, { _ -> commits2++ }) {
                        model.value
                    }
                }
            }
        }
        assertEquals(0, commits)
        assertEquals(1, commits2)
    }

    private fun runSimpleTest(block: (modelObserver: ModelObserver, model: State<Int>) -> Unit) {
        val modelObserver = ModelObserver { it() }
        val model = State(0)

        modelObserver.enableModelUpdatesObserving(true)
        try {
            open() // open the frame
            block(modelObserver, model)
            model.value++
            commit() // close the frame
        } finally {
            modelObserver.enableModelUpdatesObserving(false)
        }
    }
}

// @Model generation is not enabled for this module and androidx.compose.State is internal
// TODO make State's constructor public and remove the copied code. b/142883125
private class State<T> constructor(value: T) : Framed {

    @Suppress("UNCHECKED_CAST")
    var value: T
        get() = next.readable(this).value
        set(value) {
            next.writable(this).value = value
        }

    private var next: StateRecord<T> =
        StateRecord(value)

    init {
        _created(this)
    }

    // NOTE(lmr): ideally we can compile `State` with our own compiler so that this is not visible
    @Hide
    override val firstFrameRecord: Record
        get() = next

    // NOTE(lmr): ideally we can compile `State` with our own compiler so that this is not visible
    @Hide
    override fun prependFrameRecord(value: Record) {
        value.next = next
        @Suppress("UNCHECKED_CAST")
        next = value as StateRecord<T>
    }

    private class StateRecord<T>(myValue: T) : AbstractRecord() {
        override fun assign(value: Record) {
            @Suppress("UNCHECKED_CAST")
            this.value = (value as StateRecord<T>).value
        }

        override fun create(): Record =
            StateRecord(value)

        var value: T = myValue
    }
}
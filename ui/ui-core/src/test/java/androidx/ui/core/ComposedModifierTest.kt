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

package androidx.ui.core

import androidx.compose.Applier
import androidx.compose.ApplyAdapter
import androidx.compose.Composable
import androidx.compose.Composer
import androidx.compose.CompositionFrameClock
import androidx.compose.ExperimentalComposeApi
import androidx.compose.InternalComposeApi
import androidx.compose.Recomposer
import androidx.compose.SlotTable
import androidx.compose.currentComposer
import androidx.compose.invalidate
import androidx.compose.remember
import androidx.compose.withRunningRecomposer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class TestTagModifier<T>(val name: String, val value: T) : Modifier.Element

fun <T> Modifier.testTag(name: String, value: T) = this + TestTagModifier(name, value)

fun <T> Modifier.getTestTag(name: String, default: T): T = foldIn(default) { acc, element ->
    @Suppress("UNCHECKED_CAST")
    if (element is TestTagModifier<*> && element.name == name) element.value as T else acc
}

@OptIn(InternalComposeApi::class)
class ComposedModifierTest {

    private val composer: Composer<*> get() = error("should not be called")

    /**
     * Confirm that a [composed] modifier correctly constructs separate instances when materialized
     */
    @Test
    fun materializeComposedModifier() = runBlocking {
        // Note: assumes single-threaded composition
        var counter = 0
        val sourceMod = Modifier.testTag("static", 0)
            .composed { testTag("dynamic", ++counter) }

        withRunningRecomposer(TestFrameClock()) { recomposer ->
            lateinit var firstMaterialized: Modifier
            lateinit var secondMaterialized: Modifier
            compose(recomposer) {
                firstMaterialized = currentComposer.materialize(sourceMod)
                secondMaterialized = currentComposer.materialize(sourceMod)
            }

            assertNotEquals("I recomposed some modifiers", 0, counter)

            assertEquals(
                "first static value equal to source",
                sourceMod.getTestTag("static", Int.MIN_VALUE),
                firstMaterialized.getTestTag("static", Int.MAX_VALUE)
            )
            assertEquals(
                "second static value equal to source",
                sourceMod.getTestTag("static", Int.MIN_VALUE),
                secondMaterialized.getTestTag("static", Int.MAX_VALUE)
            )
            assertEquals(
                "dynamic value not present in source",
                Int.MIN_VALUE,
                sourceMod.getTestTag("dynamic", Int.MIN_VALUE)
            )
            assertNotEquals(
                "dynamic value present in first materialized",
                Int.MIN_VALUE,
                firstMaterialized.getTestTag("dynamic", Int.MIN_VALUE)
            )
            assertNotEquals(
                "dynamic value present in second materialized",
                Int.MIN_VALUE,
                firstMaterialized.getTestTag("dynamic", Int.MIN_VALUE)
            )
            assertNotEquals(
                "first and second dynamic values must be unequal",
                firstMaterialized.getTestTag("dynamic", Int.MIN_VALUE),
                secondMaterialized.getTestTag("dynamic", Int.MIN_VALUE)
            )
        }
    }

    /**
     * Confirm that recomposition occurs on invalidation
     */
    @Test
    fun recomposeComposedModifier() = runBlocking {
        // Manually invalidate the composition of the modifier instead of using mutableStateOf
        // Frame-based recomposition requires the FrameManager be up and running.
        var value = 0
        lateinit var invalidator: () -> Unit

        val sourceMod = Modifier.composed {
            invalidator = invalidate
            testTag("changing", value)
        }

        val frameClock = TestFrameClock()
        withRunningRecomposer(frameClock) { recomposer ->
            lateinit var materialized: Modifier
            compose(recomposer) {
                materialized = currentComposer.materialize(sourceMod)
            }

            assertEquals(
                "initial composition value",
                0,
                materialized.getTestTag("changing", Int.MIN_VALUE)
            )

            value = 5
            invalidator()
            frameClock.frame(0L)

            assertEquals(
                "recomposed composition value",
                5,
                materialized.getTestTag("changing", Int.MIN_VALUE)
            )
        }
    }

    @Test
    fun rememberComposedModifier() = runBlocking {
        lateinit var invalidator: () -> Unit
        val sourceMod = Modifier.composed {
            invalidator = invalidate
            val state = remember { Any() }
            testTag("remembered", state)
        }

        val frameClock = TestFrameClock()

        withRunningRecomposer(frameClock) { recomposer ->
            val results = mutableListOf<Any?>()
            val notFound = Any()
            compose(recomposer) {
                results.add(
                    currentComposer.materialize(sourceMod).getTestTag("remembered", notFound)
                )
            }

            assertTrue("one item added for initial composition", results.size == 1)
            assertNotNull("remembered object not null", results[0])

            invalidator()
            frameClock.frame(0)

            assertEquals("two items added after recomposition", 2, results.size)
            assertTrue("no null items", results.none { it === notFound })
            assertEquals("remembered references are equal", results[0], results[1])
        }
    }

    @Test
    fun nestedComposedModifiers() = runBlocking {
        val mod = Modifier.composed {
            composed {
                testTag("nested", 10)
            }
        }

        val frameClock = TestFrameClock()

        withRunningRecomposer(frameClock) { recomposer ->
            lateinit var materialized: Modifier
            compose(recomposer) {
                materialized = currentComposer.materialize(mod)
            }

            assertEquals(
                "fully unwrapped composed modifier value",
                10,
                materialized.getTestTag("nested", 0)
            )
        }
    }
}

@OptIn(InternalComposeApi::class)
private fun compose(
    recomposer: Recomposer,
    block: @Composable () -> Unit
): Composer<Unit> = UnitComposer(recomposer).apply {
    compose(block)
    applyChanges()
    slotTable.verifyWellFormed()
}

/**
 * This ApplyAdapter does nothing. These tests only confirm modifier materialization.
 */
@OptIn(ExperimentalComposeApi::class)
private object UnitApplierAdapter : ApplyAdapter<Unit> {
    override fun Unit.start(instance: Unit) {}
    override fun Unit.insertAt(index: Int, instance: Unit) {}
    override fun Unit.removeAt(index: Int, count: Int) {}
    override fun Unit.move(from: Int, to: Int, count: Int) {}
    override fun Unit.end(instance: Unit, parent: Unit) {}
}

private class TestFrameClock : CompositionFrameClock {

    private val frameCh = Channel<Long>()

    suspend fun frame(frameTimeNanos: Long) {
        frameCh.send(frameTimeNanos)
    }

    override suspend fun <R> awaitFrameNanos(onFrame: (Long) -> R): R = onFrame(frameCh.receive())
}

@OptIn(ExperimentalComposeApi::class)
private class UnitComposer(recomposer: Recomposer) : Composer<Unit>(
    SlotTable(),
    Applier(Unit, UnitApplierAdapter),
    recomposer
) {
    fun compose(composable: @Composable () -> Unit) {
        composeRoot {
            @Suppress("UNCHECKED_CAST")
            val fn = composable as (Composer<*>, Int, Int) -> Unit
            fn(this@UnitComposer, 0, 0)
        }
    }
}
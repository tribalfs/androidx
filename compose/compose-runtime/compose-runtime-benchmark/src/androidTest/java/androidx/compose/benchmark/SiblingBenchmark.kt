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

package androidx.compose.benchmark

import androidx.compose.Composable
import androidx.compose.Composer
import androidx.compose.FrameManager
import androidx.compose.Model
import androidx.compose.Observe
import androidx.compose.State
import androidx.compose.benchmark.deeptree.DeepTree
import androidx.compose.benchmark.realworld4.RealWorld4_FancyWidget_000
import androidx.compose.benchmark.siblings.IdentityType
import androidx.compose.benchmark.siblings.Item
import androidx.compose.benchmark.siblings.ReorderType
import androidx.compose.benchmark.siblings.SiblingManagement
import androidx.compose.benchmark.siblings.update
import androidx.compose.composer
import androidx.compose.disposeComposition
import androidx.compose.runWithCurrent
import androidx.compose.state
import androidx.compose.unaryPlus
import androidx.test.annotation.UiThreadTest
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import androidx.test.rule.ActivityTestRule
import androidx.ui.core.dp
import androidx.ui.core.setContent
import androidx.ui.foundation.ColoredRect
import androidx.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.FixMethodOrder
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import org.junit.runners.Parameterized
import kotlin.random.Random

/**
 * Managing “lists” of components that are siblings in Compose and other declarative reactive frameworks ends up
 * being an important performance characteristic to monitor. The algorithm we use here can depend greatly on how
 * we update lists of items and we might choose to bias towards what happens more commonly in the real world,
 * but understanding our performance characteristics for various types of list updates will be useful.
 *
 * @param count - number of items in the list. Really long lists probably should use a Recycler or something
 * similar in the real world, but testing this will at least let us understand our asymptotic complexity
 * characteristics.
 *
 * @param reorder - This determines what kinds of changes we will be making to the list each frame. Different list
 * management algorithms that Compose uses will yield different trade offs depending on where items in the list
 * are moved/added/removed/etc. For instance, we might be optimized for "append" but not "prepend", so we
 * should benchmark these types of changes individually. Note that some like "AddMiddle" insert at a random
 * index, so benchmarks should run this many times in order to average the randomness into something reasonable
 * to compare with a different run of the same benchmark.
 *
 * @param identity - this will toggle how Compose identifies a row. These three options are slightly different and
 * we might want to test all three.
 */
@LargeTest
@RunWith(Parameterized::class)
class SiblingBenchmark(
    val count: Int,
    val reorder: ReorderType,
    val identity: IdentityType
) : ComposeBenchmarkBase() {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}_{1}_{2}")
        fun data(): Collection<Array<Any>> {
            val counts = listOf(100)
            val reorders = ReorderType.values()
            val identities = IdentityType.values()

            val results = mutableListOf<Array<Any>>()

            for (count in counts) {
                for (reorder in reorders) {
                    for (identity in identities) {
                        results.add(arrayOf(count, reorder, identity))
                    }
                }
            }

            return results
        }
    }

    @UiThreadTest
    @Test
    fun runBenchmark() {
        activityRule.runUiRunnable {
            val items = ValueHolder((0..count).map { Item(it) })
            val random = Random(0)
            measureRecompose {
                compose {
                    SiblingManagement(identity = identity, items = items.value)
                }
                update {
                    items.value = items.value.update(reorder, random) { Item(it + 1) }
                }
            }
        }
    }
}

// NOTE: remove when SAM conversion works in IR
fun ActivityTestRule<ComposeActivity>.runUiRunnable(block: () -> Unit) {
    runOnUiThread(object : Runnable {
        override fun run() {
            block()
        }
    })
}

@Model private class ValueHolder<T>(var value: T)
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

package androidx.ui.benchmark.test

import androidx.compose.Composable
import androidx.compose.State
import androidx.compose.state
import androidx.compose.unaryPlus
import androidx.test.filters.LargeTest
import androidx.ui.benchmark.ComposeBenchmarkRule
import androidx.ui.benchmark.benchmarkDrawPerf
import androidx.ui.benchmark.benchmarkFirstCompose
import androidx.ui.benchmark.benchmarkFirstDraw
import androidx.ui.benchmark.benchmarkFirstLayout
import androidx.ui.benchmark.benchmarkFirstMeasure
import androidx.ui.benchmark.benchmarkLayoutPerf
import androidx.ui.benchmark.toggleStateBenchmarkDraw
import androidx.ui.benchmark.toggleStateBenchmarkLayout
import androidx.ui.benchmark.toggleStateBenchmarkMeasure
import androidx.ui.benchmark.toggleStateBenchmarkRecompose
import androidx.ui.core.Dp
import androidx.ui.core.dp
import androidx.ui.layout.Container
import androidx.ui.layout.Padding
import androidx.ui.layout.Spacing
import androidx.ui.test.ComposeTestCase
import androidx.ui.test.ToggleableTestCase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@LargeTest
@RunWith(JUnit4::class)
class PaddingBenchmark {
    @get:Rule
    val benchmarkRule = ComposeBenchmarkRule()

    @Test
    fun noModifier_first_compose() {
        benchmarkRule.benchmarkFirstCompose(NoModifierTestCase())
    }

    @Test
    fun noModifier_first_measure() {
        benchmarkRule.benchmarkFirstMeasure(NoModifierTestCase())
    }

    @Test
    fun noModifier_first_layout() {
        benchmarkRule.benchmarkFirstLayout(NoModifierTestCase())
    }

    @Test
    fun noModifier_first_draw() {
        benchmarkRule.benchmarkFirstDraw(NoModifierTestCase())
    }

    @Test
    fun noModifier_togglePadding_recompose() {
        benchmarkRule.toggleStateBenchmarkRecompose(NoModifierTestCase())
    }

    @Test
    fun noModifier_togglePadding_measure() {
        benchmarkRule.toggleStateBenchmarkMeasure(NoModifierTestCase())
    }

    @Test
    fun noModifier_togglePadding_layout() {
        benchmarkRule.toggleStateBenchmarkLayout(NoModifierTestCase())
    }

    @Test
    fun noModifier_togglePadding_draw() {
        benchmarkRule.toggleStateBenchmarkDraw(NoModifierTestCase())
    }

    @Test
    fun noModifier_layout() {
        benchmarkRule.benchmarkLayoutPerf(NoModifierTestCase())
    }

    @Test
    fun noModifier_draw() {
        benchmarkRule.benchmarkDrawPerf(NoModifierTestCase())
    }

    @Test
    fun modifier_first_compose() {
        benchmarkRule.benchmarkFirstCompose(ModifierTestCase())
    }

    @Test
    fun modifier_first_measure() {
        benchmarkRule.benchmarkFirstMeasure(ModifierTestCase())
    }

    @Test
    fun modifier_first_layout() {
        benchmarkRule.benchmarkFirstLayout(ModifierTestCase())
    }

    @Test
    fun modifier_first_draw() {
        benchmarkRule.benchmarkFirstDraw(ModifierTestCase())
    }

    @Test
    fun modifier_togglePadding_recompose() {
        benchmarkRule.toggleStateBenchmarkRecompose(ModifierTestCase())
    }

    @Test
    fun modifier_togglePadding_measure() {
        benchmarkRule.toggleStateBenchmarkMeasure(ModifierTestCase())
    }

    @Test
    fun modifier_togglePadding_layout() {
        benchmarkRule.toggleStateBenchmarkLayout(ModifierTestCase())
    }

    @Test
    fun modifier_togglePadding_draw() {
        benchmarkRule.toggleStateBenchmarkDraw(ModifierTestCase())
    }

    @Test
    fun modifier_layout() {
        benchmarkRule.benchmarkLayoutPerf(ModifierTestCase())
    }

    @Test
    fun modifier_draw() {
        benchmarkRule.benchmarkDrawPerf(ModifierTestCase())
    }
}

private sealed class PaddingTestCase : ComposeTestCase,
    ToggleableTestCase {

    var paddingState: State<Dp>? = null

    override fun toggleState() {
        with(paddingState!!) {
            value = if (value == 5.dp) 10.dp else 5.dp
        }
    }

    @Composable
    override fun emitContent() {
        val padding = +state { 5.dp }
        paddingState = padding

        Container(expanded = true) {
            emitPaddedContainer(padding.value) {
                emitPaddedContainer(padding.value) {
                    emitPaddedContainer(padding.value) {
                        emitPaddedContainer(padding.value) {
                            emitPaddedContainer(padding.value) {}
                        }
                    }
                }
            }
        }
    }

    @Composable
    abstract fun emitPaddedContainer(padding: Dp, child: @Composable() () -> Unit)
}

private class ModifierTestCase : PaddingTestCase() {

    @Composable
    override fun emitPaddedContainer(padding: Dp, child: @Composable() () -> Unit) {
        Container(expanded = true, modifier = Spacing(padding), children = child)
    }
}

private class NoModifierTestCase : PaddingTestCase() {

    @Composable
    override fun emitPaddedContainer(padding: Dp, child: @Composable() () -> Unit) {
        Container(expanded = true) {
            Padding(padding = padding, children = child)
        }
    }
}

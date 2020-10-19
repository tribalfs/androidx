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

package androidx.compose.ui

import androidx.compose.testutils.benchmark.ComposeBenchmarkRule
import androidx.compose.testutils.benchmark.benchmarkDrawPerf
import androidx.compose.testutils.benchmark.benchmarkFirstComposeFast
import androidx.compose.testutils.benchmark.benchmarkFirstDrawFast
import androidx.compose.testutils.benchmark.benchmarkFirstLayoutFast
import androidx.compose.testutils.benchmark.benchmarkFirstMeasureFast
import androidx.compose.testutils.benchmark.benchmarkLayoutPerf
import androidx.compose.testutils.benchmark.toggleStateBenchmarkDraw
import androidx.compose.testutils.benchmark.toggleStateBenchmarkLayout
import androidx.compose.testutils.benchmark.toggleStateBenchmarkMeasure
import androidx.compose.testutils.benchmark.toggleStateBenchmarkRecompose
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.filters.SmallTest
import androidx.ui.integration.test.TextBenchmarkTestRule
import androidx.ui.integration.test.core.text.TextFieldToggleTextTestCase
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@SmallTest
@RunWith(Parameterized::class)
class TextFieldToggleTextBenchmark(
    private val textLength: Int
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "length={0}")
        fun initParameters(): Array<Any> = arrayOf(32, 512)
    }

    private val textBenchmarkRule = TextBenchmarkTestRule()
    private val benchmarkRule = ComposeBenchmarkRule()

    @get:Rule
    val testRule = RuleChain
        .outerRule(textBenchmarkRule)
        .around(benchmarkRule)

    private val width = textBenchmarkRule.widthDp.dp
    private val fontSize = textBenchmarkRule.fontSizeSp.sp

    private val caseFactory = {
        textBenchmarkRule.generator { generator ->
            TextFieldToggleTextTestCase(
                textGenerator = generator,
                textLength = textLength,
                textNumber = textBenchmarkRule.repeatTimes,
                width = width,
                fontSize = fontSize
            )
        }
    }

    /**
     * Measure the time taken to compose a [BaseTextField] composable from scratch with the
     * given input. This is the time taken to call the [BaseTextField] composable function.
     */
    @Test
    @Ignore
    fun first_compose() {
        benchmarkRule.benchmarkFirstComposeFast(caseFactory)
    }

    /**
     * Measure the time taken by the first time measure the [BaseTextField] composable with the
     * given input. This is mainly the time used to measure all the [Measurable]s in the
     * [BaseTextField] composable.
     */
    @Test
    @Ignore
    fun first_measure() {
        benchmarkRule.benchmarkFirstMeasureFast(caseFactory)
    }

    /**
     * Measure the time taken by the first time layout the [BaseTextField] composable with the
     * given input.
     */
    @Test
    @Ignore
    fun first_layout() {
        benchmarkRule.benchmarkFirstLayoutFast(caseFactory)
    }

    /**
     * Measure the time taken by first time draw the [BaseTextField] composable with the given
     * input.
     */
    @Test
    @Ignore
    fun first_draw() {
        benchmarkRule.benchmarkFirstDrawFast(caseFactory)
    }

    /**
     * Measure the time taken by layout the [BaseTextField] composable after the layout
     * constrains changed. This is mainly the time used to re-measure and re-layout the composable.
     */
    @Test
    @Ignore
    fun layout() {
        benchmarkRule.benchmarkLayoutPerf(caseFactory)
    }

    /**
     * Measure the time taken by redrawing the [BaseTextField] composable.
     */
    @Test
    @Ignore
    fun draw() {
        benchmarkRule.benchmarkDrawPerf(caseFactory)
    }

    /**
     * Measure the time taken to recompose the [BaseTextField] composable when text gets toggled.
     */
    @Test
    fun toggleText_recompose() {
        benchmarkRule.toggleStateBenchmarkRecompose(caseFactory)
    }

    /**
     * Measure the time taken to measure the [BaseTextField] composable when text gets toggled.
     */
    @Test
    fun toggleText_measure() {
        benchmarkRule.toggleStateBenchmarkMeasure(caseFactory)
    }

    /**
     * Measure the time taken to layout the [BaseTextField] composable when text gets toggled.
     */
    @Test
    fun toggleText_layout() {
        benchmarkRule.toggleStateBenchmarkLayout(caseFactory)
    }

    /**
     * Measure the time taken to draw the [BaseTextField] composable when text gets toggled.
     */
    @Test
    fun toggleText_draw() {
        benchmarkRule.toggleStateBenchmarkDraw(caseFactory)
    }
}
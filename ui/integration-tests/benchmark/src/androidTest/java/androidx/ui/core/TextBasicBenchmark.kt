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
import androidx.ui.integration.test.core.text.TextBasicTestCase
import androidx.ui.integration.test.TextBenchmarkTestRule
import androidx.ui.unit.dp
import androidx.ui.unit.sp
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The benchmark for [Text] composable with the input being a plain string.
 */
@LargeTest
@RunWith(Parameterized::class)
class TextBasicBenchmark(
    private val textLength: Int
) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "length={0}")
        fun initParameters(): Array<Any> = arrayOf(32, 512)
    }

    @get:Rule
    val textBenchmarkRule = TextBenchmarkTestRule()

    @get:Rule
    val benchmarkRule = ComposeBenchmarkRule()

    private val width = textBenchmarkRule.widthDp.dp
    private val fontSize = textBenchmarkRule.fontSizeSp.sp

    private val textCaseFactory = {
        textBenchmarkRule.generator { textGenerator ->
            /**
             * Text render has a word cache in the underlying system. To get a proper metric of its
             * performance, the cache needs to be disabled, which unfortunately is not doable via
             * public API. Here is a workaround which generates a new string when a new test case
             * is created.
             */
            val text = textGenerator.nextParagraph(textLength)
            TextBasicTestCase(
                text = text,
                width = width,
                fontSize = fontSize
            )
        }
    }

    /**
     * Measure the time taken to compose a [Text] composable from scratch with the given input.
     * This is the time taken to call the [Text] composable function.
     */
    @Test
    fun first_compose() {
        benchmarkRule.benchmarkFirstCompose(textCaseFactory)
    }

    /**
     * Measure the time taken by the first time measure the [Text] composable with the given input.
     * This is mainly the time used to measure all the [Measurable]s in the [Text] composable.
     */
    @Test
    fun first_measure() {
        textBenchmarkRule.generator { textGenerator ->
            benchmarkRule.benchmarkFirstMeasure {
                TextBasicTestCase(textGenerator.nextParagraph(textLength), width, fontSize)
            }
        }
    }

    /**
     * Measure the time taken by the first time layout the [Text] composable with the given input.
     * This is mainly the time used to place [Placeable]s in [Text] composable.
     */
    @Test
    fun first_layout() {
        benchmarkRule.benchmarkFirstLayout(textCaseFactory)
    }

    /**
     * Measure the time taken by first time draw the [Text] composable with the given input.
     */
    @Test
    fun first_draw() {
        benchmarkRule.benchmarkFirstDraw(textCaseFactory)
    }

    /**
     * Measure the time taken by layout the [Text] composable after the layout constrains changed.
     * This is mainly the time used to re-measure and re-layout the composable.
     */
    @Test
    fun layout() {
        benchmarkRule.benchmarkLayoutPerf(textCaseFactory)
    }

    /**
     * Measure the time taken by redrawing the [Text] composable.
     */
    @Test
    fun draw() {
        benchmarkRule.benchmarkDrawPerf(textCaseFactory)
    }

    /**
     * Measure the time taken to recompose the [Text] composable when color gets toggled.
     */
    @Test
    fun toggleColor_recompose() {
        benchmarkRule.toggleStateBenchmarkRecompose(textCaseFactory)
    }

    /**
     * Measure the time taken to measure the [Text] composable when color gets toggled.
     */
    @Test
    fun toggleColor_measure() {
        benchmarkRule.toggleStateBenchmarkMeasure(textCaseFactory)
    }

    /**
     * Measure the time taken to layout the [Text] composable when color gets toggled.
     */
    @Test
    fun toggleColor_layout() {
        benchmarkRule.toggleStateBenchmarkLayout(textCaseFactory)
    }

    /**
     * Measure the time taken to draw the [Text] composable when color gets toggled.
     */
    @Test
    fun toggleColor_draw() {
        benchmarkRule.toggleStateBenchmarkDraw(textCaseFactory)
    }
}
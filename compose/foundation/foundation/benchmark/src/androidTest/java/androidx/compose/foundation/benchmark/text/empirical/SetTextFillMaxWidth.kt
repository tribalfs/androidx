/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.foundation.benchmark.text.empirical

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.testutils.LayeredComposeTestCase
import androidx.compose.testutils.ToggleableTestCase
import androidx.compose.testutils.benchmark.ComposeBenchmarkRule
import androidx.compose.testutils.benchmark.toggleStateBenchmarkComposeMeasureLayout
import androidx.compose.testutils.benchmark.toggleStateBenchmarkRecompose
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.test.filters.LargeTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Toggle between "" and "aaaa..." to simulate backend text loading.
 *
 * This intentionally hits as many text caches as possible, to isolate compose setText behavior.
 *
 * This benchmark removes dynamic layout sizing cost by using fillMaxWidth.
 */
class SetTextFillMaxWidth(private val text: String) : LayeredComposeTestCase(), ToggleableTestCase {
    private var toggleText = mutableStateOf("")

    @Composable
    override fun MeasuredContent() {
        Text(
            toggleText.value,
            modifier = Modifier.fillMaxWidth(),
            fontFamily = FontFamily.Monospace
        )
    }

    override fun toggleState() {
        if (toggleText.value == "") {
            toggleText.value = text
        } else {
            toggleText.value = ""
        }
    }
}

@LargeTest
@RunWith(Parameterized::class)
open class SetTextFillMaxWidthParent(private val size: Int) {

    @get:Rule
    val benchmarkRule = ComposeBenchmarkRule()

    private val caseFactory = {
        val text = generateCacheableStringOf(size)
        SetTextFillMaxWidth(text)
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "size={0}")
        fun initParameters(): Array<Any> = arrayOf()
    }

    @Test
    fun recomposeOnly() {
        benchmarkRule.toggleStateBenchmarkRecompose(caseFactory)
    }

    @Test
    fun recomposeMeasureLayout() {
        benchmarkRule.toggleStateBenchmarkComposeMeasureLayout(caseFactory)
    }
}

/**
 * Metrics determined from all apps
 */
@LargeTest
@RunWith(Parameterized::class)
class AllAppsSetTextFillMaxWidth(size: Int) : SetTextFillMaxWidthParent(size) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "size={0}")
        fun initParameters(): Array<Any> = AllApps.TextLengths
    }
}

/**
 * Metrics for Chat-like apps.
 *
 * These apps typically have more longer strings, due to user generated content.
 */
@LargeTest
@RunWith(Parameterized::class)
class ChatAppSetTextFillMaxWidth(size: Int) : SetTextFillMaxWidthParent(size) {
    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "size={0}")
        fun initParameters(): Array<Any> = ChatApps.TextLengths
    }
}

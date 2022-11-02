/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.compiler.plugins.kotlin

abstract class AbstractMetricsTransformTest : AbstractIrTransformTest() {
    private fun verifyMetrics(
        source: String,
        verify: ModuleMetrics.() -> Unit
    ) {
        val files = listOf(
            sourceFile("Test.kt", source.replace('%', '$')),
        )

        val extension = createComposeIrGenerationExtension()
        val metrics = ModuleMetricsImpl(TEST_MODULE_NAME)
        extension.metrics = metrics
        compileToIrWithExtension(files, extension)
        metrics.verify()
    }

    fun assertClasses(
        source: String,
        expected: String,
    ) = verifyMetrics(source) {
        val actual = buildString { appendClassesTxt() }
        assertEquals(
            expected
                .trimIndent()
                .trimTrailingWhitespacesAndAddNewlineAtEOF(),
            actual
                .trimIndent()
                .trimTrailingWhitespacesAndAddNewlineAtEOF(),
        )
    }

    fun assertComposables(
        source: String,
        expected: String,
    ) = verifyMetrics(source) {
        val actual = buildString { appendComposablesTxt() }
        assertEquals(
            expected
                .trimIndent()
                .trimTrailingWhitespacesAndAddNewlineAtEOF(),
            actual
                .trimIndent()
                .trimTrailingWhitespacesAndAddNewlineAtEOF(),
        )
    }

    fun assertModuleJson(
        source: String,
        expected: String,
    ) = verifyMetrics(source) {
        val actual = buildString { appendModuleJson() }
        assertEquals(
            expected
                .trimIndent()
                .trimTrailingWhitespacesAndAddNewlineAtEOF(),
            actual
                .trimIndent()
                .trimTrailingWhitespacesAndAddNewlineAtEOF(),
        )
    }

    fun assertComposablesCsv(
        source: String,
        expected: String,
    ) = verifyMetrics(source) {
        val actual = buildString { appendComposablesCsv() }
        assertEquals(
            expected
                .trimIndent()
                .trimTrailingWhitespacesAndAddNewlineAtEOF(),
            actual
                .trimIndent()
                .trimTrailingWhitespacesAndAddNewlineAtEOF(),
        )
    }
}
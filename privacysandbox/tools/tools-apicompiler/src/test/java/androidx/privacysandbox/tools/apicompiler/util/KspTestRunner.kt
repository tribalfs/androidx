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

package androidx.privacysandbox.tools.apicompiler.util

import androidx.privacysandbox.tools.apicompiler.model.ParsedApi
import androidx.privacysandbox.tools.apicompiler.parser.ApiParser
import androidx.room.compiler.processing.util.DiagnosticMessage
import androidx.room.compiler.processing.util.Source
import androidx.room.compiler.processing.util.compiler.TestCompilationArguments
import androidx.room.compiler.processing.util.compiler.TestCompilationResult
import androidx.room.compiler.processing.util.compiler.compile
import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.google.devtools.ksp.symbol.KSAnnotated
import java.nio.file.Files
import javax.tools.Diagnostic

/**
 * Helper to run KSP processing functionality in tests.
 */
fun parseSource(source: Source): ParsedApi {
    val provider = CapturingSymbolProcessor.Provider()
    compile(
        Files.createTempDirectory("test").toFile(),
        TestCompilationArguments(
            sources = listOf(source),
            symbolProcessorProviders = listOf(provider),
        )
    )
    assert(provider.processor.capture != null) { "KSP run didn't produce any output." }
    return provider.processor.capture!!
}

fun checkSourceFails(source: Source): CompilationResultSubject {
    val provider = CapturingSymbolProcessor.Provider()
    val result = compile(
        Files.createTempDirectory("test").toFile(),
        TestCompilationArguments(
            sources = listOf(source),
            symbolProcessorProviders = listOf(provider)
        )
    )
    assertThat(result.success).isFalse()
    return CompilationResultSubject(result)
}

class CompilationResultSubject(private val result: TestCompilationResult) {
    fun containsError(error: String) {
        assertThat(result.diagnostics[Diagnostic.Kind.ERROR]?.map(DiagnosticMessage::msg))
            .contains(error)
    }
}

private class CapturingSymbolProcessor(private val logger: KSPLogger) : SymbolProcessor {
    var capture: ParsedApi? = null

    override fun process(resolver: Resolver): List<KSAnnotated> {
        capture = ApiParser(resolver, logger).parseApi()
        return emptyList()
    }

    class Provider : SymbolProcessorProvider {
        lateinit var processor: CapturingSymbolProcessor

        override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
            assert(!::processor.isInitialized)
            processor = CapturingSymbolProcessor(environment.logger)
            return processor
        }
    }
}

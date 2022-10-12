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

package androidx.privacysandbox.tools.apicompiler

import androidx.privacysandbox.tools.testing.CompilationTestHelper.assertThat
import androidx.privacysandbox.tools.testing.CompilationTestHelper.compileAll
import androidx.privacysandbox.tools.testing.loadSourcesFromDirectory
import androidx.room.compiler.processing.util.compiler.TestCompilationArguments
import androidx.room.compiler.processing.util.compiler.compile
import androidx.room.compiler.processing.util.Source
import java.io.File
import java.nio.file.Files
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class PrivacySandboxKspCompilerTest {
    @Test
    fun compileServiceInterface_ok() {
        val inputTestDataDir = File("src/test/test-data/testinterface/input")
        val outputTestDataDir = File("src/test/test-data/testinterface/output")
        val sources = loadSourcesFromDirectory(inputTestDataDir)
        val expectedOutput = loadSourcesFromDirectory(outputTestDataDir)
        val provider = PrivacySandboxKspCompiler.Provider()
        // Check that compilation is successful
        assertThat(
            compileAll(
                sources,
                symbolProcessorProviders = listOf(provider),
                processorOptions = getProcessorOptions(),
            )
        ).also {
            it.generatesExactlySources(
                "com/mysdk/IMySdk.java",
                "com/mysdk/ICancellationSignal.java",
                "com/mysdk/IStringTransactionCallback.java",
                "com/mysdk/AbstractSandboxedSdkProvider.kt",
                "com/mysdk/MySdkStubDelegate.kt",
                "com/mysdk/TransportCancellationCallback.kt",
                "com/mysdk/ResponseConverter.kt",
                "com/mysdk/RequestConverter.kt",
                "com/mysdk/ParcelableRequest.java",
                "com/mysdk/ParcelableResponse.java",
                "com/mysdk/IResponseTransactionCallback.java",
            )
        }.also {
            it.generatesSourcesWithContents(expectedOutput)
        }
    }

    @Test
    fun compileEmpty_ok() {
        val provider = PrivacySandboxKspCompiler.Provider()
        // Check that compilation is successful
        assertThat(
            compile(
                Files.createTempDirectory("test").toFile(),
                TestCompilationArguments(
                    sources = emptyList(),
                    symbolProcessorProviders = listOf(provider),
                    processorOptions = getProcessorOptions(),
                )
            )
        ).generatesExactlySources()
    }

    @Test
    fun compileInvalidServiceInterface_fails() {
        val source =
            Source.kotlin(
                "com/mysdk/MySdk.kt",
                """
                    package com.mysdk
                    import androidx.privacysandbox.tools.PrivacySandboxService
                    @PrivacySandboxService
                    abstract class MySdk {
                        fun doStuff(x: Int, y: Int): String
                        fun doMoreStuff()
                    }
                """
            )
        val provider = PrivacySandboxKspCompiler.Provider()
        // Check that compilation fails
        assertThat(
            compileAll(
                listOf(source),
                symbolProcessorProviders = listOf(provider),
                processorOptions = getProcessorOptions(),
            )
        ).fails()
    }

    private fun getProcessorOptions() =
        mapOf(
            "aidl_compiler_path" to (System.getProperty("aidl_compiler_path")
                ?: throw IllegalArgumentException("aidl_compiler_path flag not set."))
        )
}
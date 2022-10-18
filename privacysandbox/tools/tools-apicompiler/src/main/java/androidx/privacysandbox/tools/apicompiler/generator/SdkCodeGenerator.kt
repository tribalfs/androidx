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

package androidx.privacysandbox.tools.apicompiler.generator

import androidx.privacysandbox.tools.core.model.ParsedApi
import androidx.privacysandbox.tools.core.generator.AidlCompiler
import androidx.privacysandbox.tools.core.generator.AidlGenerator
import androidx.privacysandbox.tools.core.generator.ClientProxyTypeGenerator
import androidx.privacysandbox.tools.core.generator.StubDelegatesGenerator
import androidx.privacysandbox.tools.core.generator.ValueConverterFileGenerator
import com.google.devtools.ksp.processing.CodeGenerator
import com.google.devtools.ksp.processing.Dependencies
import com.squareup.kotlinpoet.FileSpec
import java.nio.file.Files.createTempDirectory
import java.nio.file.Path

class SdkCodeGenerator(
    private val codeGenerator: CodeGenerator,
    private val api: ParsedApi,
    private val aidlCompilerPath: Path,
) {
    fun generate() {
        generateAidlSources()
        generateValueConverters()
        generateCallbackProxies()
        generateAbstractSdkProvider()
        generateStubDelegates()
    }

    private fun generateValueConverters() {
        api.values.forEach { value ->
            write(ValueConverterFileGenerator(api, value).generate())
        }
    }

    private fun generateAidlSources() {
        val workingDir = createTempDirectory("aidl")
        try {
            AidlGenerator.generate(AidlCompiler(aidlCompilerPath), api, workingDir)
                .forEach { source ->
                    // Sources created by the AIDL compiler have to be copied to files created
                    // through the KSP APIs, so that they are included in downstream compilation.
                    val kspGeneratedFile = codeGenerator.createNewFile(
                        Dependencies(false),
                        source.packageName,
                        source.interfaceName,
                        extensionName = "java"
                    )
                    source.file.inputStream().copyTo(kspGeneratedFile)
                }
        } finally {
            workingDir.toFile().deleteRecursively()
        }
    }

    private fun generateCallbackProxies() {
        for (callback in api.callbacks) {
            val classSpec = ClientProxyTypeGenerator(api, callback).generate()
            write(
                FileSpec.builder(callback.type.packageName, classSpec.name!!).addType(classSpec)
                    .build()
            )
        }
    }

    private fun generateAbstractSdkProvider() {
        AbstractSdkProviderGenerator(api).generate()?.also(::write)
    }

    private fun generateStubDelegates() {
        StubDelegatesGenerator(api).generate().forEach(::write)
    }

    private fun write(spec: FileSpec) {
        codeGenerator.createNewFile(Dependencies(false), spec.packageName, spec.name)
            .write(spec)
    }
}
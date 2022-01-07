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

package androidx.room.compiler.processing.util

import androidx.room.compiler.processing.ExperimentalProcessingApi
import androidx.room.compiler.processing.SyntheticJavacProcessor
import androidx.room.compiler.processing.SyntheticKspProcessor
import androidx.room.compiler.processing.XProcessingEnvConfig
import androidx.room.compiler.processing.util.compiler.TestCompilationArguments
import androidx.room.compiler.processing.util.compiler.compile
import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.processing.SymbolProcessor
import com.google.devtools.ksp.processing.SymbolProcessorEnvironment
import com.google.devtools.ksp.processing.SymbolProcessorProvider
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.TypeSpec
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import org.junit.Test
import java.net.URLClassLoader
import java.nio.file.Files
import javax.lang.model.element.Modifier
import javax.tools.Diagnostic

@OptIn(ExperimentalProcessingApi::class)
class TestRunnerTest {
    @Test
    fun compileFilesForClasspath() {
        val kotlinSource = Source.kotlin(
            "Foo.kt",
            """
            class KotlinClass1
            class KotlinClass2
            """.trimIndent()
        )
        val javaSource = Source.java(
            "foo.bar.JavaClass1",
            """
            package foo.bar;
            public class JavaClass1 {}
            """.trimIndent()
        )

        val kspProcessorProvider = object : SymbolProcessorProvider {
            override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
                return SyntheticKspProcessor(
                    environment,
                    XProcessingEnvConfig.DEFAULT,
                    listOf { invocation ->
                        if (
                            invocation.processingEnv.findTypeElement("gen.GeneratedKotlin")
                            == null
                        ) {
                            invocation.processingEnv.filer.write(
                                FileSpec.builder("gen", "KotlinGen")
                                    .addType(
                                        com.squareup.kotlinpoet.TypeSpec.classBuilder
                                        ("GeneratedKotlin").build()
                                    )
                                    .build()
                            )
                        }
                    }
                )
            }
        }

        val javaProcessor = SyntheticJavacProcessor(
            XProcessingEnvConfig.DEFAULT,
            listOf { invocation ->
                if (
                    invocation.processingEnv.findTypeElement("gen.GeneratedJava")
                    == null
                ) {
                    invocation.processingEnv.filer.write(
                        JavaFile.builder(
                            "gen",
                            TypeSpec.classBuilder
                            ("GeneratedJava").build()
                        ).build()
                    )
                }
            }
        )
        val classpaths = compile(
            workingDir = Files.createTempDirectory("test-runner").toFile(),
            arguments = TestCompilationArguments(
                sources = listOf(kotlinSource, javaSource),
                symbolProcessorProviders = listOf(
                    kspProcessorProvider
                ),
                kaptProcessors = listOf(
                    javaProcessor
                )
            )
        ).outputClasspath
        val classLoader = URLClassLoader.newInstance(
            classpaths.map {
                it.toURI().toURL()
            }.toTypedArray()
        )

        // try loading generated classes. If any of them fails, it will throw and fail the test
        classLoader.loadClass("KotlinClass1")
        classLoader.loadClass("KotlinClass2")
        classLoader.loadClass("foo.bar.JavaClass1")
        classLoader.loadClass("gen.GeneratedKotlin")
        classLoader.loadClass("gen.GeneratedJava")
    }

    @Test
    fun generatedBadCode_expected() = generatedBadCode(assertFailure = true)

    @Test(expected = AssertionError::class)
    fun generatedBadCode_unexpected() = generatedBadCode(assertFailure = false)

    @Test
    fun options() {
        val testOptions = mapOf(
            "a" to "b",
            "c" to "d"
        )
        val handler: (XTestInvocation) -> Unit = {
            assertThat(it.processingEnv.options).containsAtLeastEntriesIn(testOptions)
        }
        runJavaProcessorTest(
            sources = emptyList(),
            options = testOptions,
            handler = handler
        )
        runKaptTest(
            sources = emptyList(),
            options = testOptions,
            handler = handler
        )
        runKspTest(
            sources = emptyList(),
            options = testOptions,
            handler = handler
        )
    }

    private fun generatedBadCode(assertFailure: Boolean) {
        val badCode = TypeSpec.classBuilder("Foo").apply {
            addStaticBlock(
                CodeBlock.of("bad code")
            )
        }.build()
        val badGeneratedFile = JavaFile.builder("foo", badCode).build()
        runProcessorTest {
            if (it.processingEnv.findTypeElement("foo.Foo") == null) {
                it.processingEnv.filer.write(
                    badGeneratedFile
                )
            }
            if (assertFailure) {
                it.assertCompilationResult {
                    compilationDidFail()
                    hasErrorContaining("';' expected")
                        .onSource(Source.java("foo.Foo", badGeneratedFile.toString()))
                }
            }
        }
    }

    @Test
    fun reportedError_expected() = reportedError(assertFailure = true)

    @Test(expected = AssertionError::class)
    fun reportedError_unexpected() = reportedError(assertFailure = false)

    private fun reportedError(assertFailure: Boolean) {
        runProcessorTest {
            it.processingEnv.messager.printMessage(
                kind = Diagnostic.Kind.ERROR,
                msg = "reported error"
            )
            if (assertFailure) {
                it.assertCompilationResult {
                    hasError("reported error")
                }
            }
        }
    }

    @Test
    fun accessGeneratedCode() {
        val kotlinSource = Source.kotlin(
            "KotlinSubject.kt",
            """
                val x: ToBeGeneratedKotlin? = null
                val y: ToBeGeneratedJava? = null
            """.trimIndent()
        )
        val javaSource = Source.java(
            "JavaSubject",
            """
                public class JavaSubject {
                    public static ToBeGeneratedKotlin x;
                    public static ToBeGeneratedJava y;
                }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(kotlinSource, javaSource)
        ) { invocation ->
            invocation.processingEnv.findTypeElement("ToBeGeneratedJava").let {
                if (it == null) {
                    invocation.processingEnv.filer.write(
                        JavaFile.builder(
                            "",
                            TypeSpec.classBuilder("ToBeGeneratedJava").apply {
                                addModifiers(Modifier.PUBLIC)
                            }.build()
                        ).build()
                    )
                }
            }
            invocation.processingEnv.findTypeElement("ToBeGeneratedKotlin").let {
                if (it == null) {
                    invocation.processingEnv.filer.write(
                        FileSpec.builder("", "Foo")
                            .addType(
                                com.squareup.kotlinpoet.TypeSpec.classBuilder(
                                    "ToBeGeneratedKotlin"
                                ).apply {
                                    addModifiers(KModifier.PUBLIC)
                                }.build()
                            ).build()
                    )
                }
            }
        }
    }

    @Test
    fun syntacticErrorsAreVisibleInTheErrorMessage_java() {
        val src = Source.java(
            "test.Foo",
            """
            package test;
            // static here is invalid, causes a Java syntax error
            public static class Foo {}
            """.trimIndent()
        )
        val errorMessage = "modifier static not allowed here"
        val javapResult = runCatching {
            runJavaProcessorTest(
                sources = listOf(src),
                classpath = emptyList()
            ) {}
        }
        assertThat(javapResult.exceptionOrNull()).hasMessageThat()
            .contains(errorMessage)

        val kaptResult = runCatching {
            runKaptTest(
                sources = listOf(src)
            ) {}
        }
        assertThat(kaptResult.exceptionOrNull()).hasMessageThat()
            .contains(errorMessage)

        if (CompilationTestCapabilities.canTestWithKsp) {
            val kspResult = runCatching {
                runKspTest(
                    sources = listOf(src)
                ) {}
            }
            assertThat(kspResult.exceptionOrNull()).hasMessageThat()
                .contains(errorMessage)
        }
    }

    @Test
    fun syntacticErrorsAreVisibleInTheErrorMessage_kotlin() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            package foo;
            bad code
            """.trimIndent()
        )
        val errorMessage = "Expecting a top level declaration"
        val kaptResult = runCatching {
            runKaptTest(
                sources = listOf(src)
            ) {}
        }
        assertThat(kaptResult.exceptionOrNull()).hasMessageThat()
            .contains(errorMessage)

        if (CompilationTestCapabilities.canTestWithKsp) {
            val kspResult = runCatching {
                runKspTest(
                    sources = listOf(src)
                ) {}
            }
            assertThat(kspResult.exceptionOrNull()).hasMessageThat()
                .contains(errorMessage)
        }
    }

    @Test
    fun javacArguments() {
        val src = Source.java(
            "Foo",
            """
            public class Foo {
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(src),
            javacArguments = listOf("-Werror"),
        ) { invocation ->
            invocation.processingEnv.messager.printMessage(
                Diagnostic.Kind.WARNING,
                "some warning"
            )
            invocation.assertCompilationResult {
                if (invocation.isKsp) {
                    // warning happens during ksp but Werror is only passed into javac so this
                    // shouldn't fail
                } else {
                    compilationDidFail()
                }
            }
        }
    }

    @Test
    fun kotlincArguments() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            class Foo
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(src),
            kotlincArguments = listOf("-Werror"),
            javacArguments = listOf("-Werror") // needed for kapt as it uses javac,
        ) { invocation ->
            invocation.processingEnv.messager.printMessage(
                Diagnostic.Kind.WARNING,
                "some warning"
            )
            invocation.assertCompilationResult {
                // either kapt or ksp, compilation should still fail due to the warning printed
                // by the processor
                compilationDidFail()
            }
        }
    }

    @Test
    fun generatedSourceSubject() {
        runProcessorTest { invocation ->
            if (invocation.processingEnv.findTypeElement("Subject") == null) {
                invocation.processingEnv.filer.write(
                    JavaFile.builder(
                        "",
                        TypeSpec.classBuilder("Subject").build()
                    ).build()
                )
            }
            invocation.assertCompilationResult {
                generatedSourceFileWithPath("Subject.java").contains(
                    "class Subject"
                )
            }
        }
    }
}
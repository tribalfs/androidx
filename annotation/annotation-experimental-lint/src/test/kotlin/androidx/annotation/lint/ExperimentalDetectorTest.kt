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

package androidx.annotation.lint

import com.android.tools.lint.checks.infrastructure.TestFile
import com.android.tools.lint.checks.infrastructure.TestFiles.java
import com.android.tools.lint.checks.infrastructure.TestFiles.kotlin
import com.android.tools.lint.checks.infrastructure.TestLintResult
import com.android.tools.lint.checks.infrastructure.TestLintTask.lint
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class ExperimentalDetectorTest {

    private fun checkJava(vararg testFiles: TestFile): TestLintResult {
        return lint()
            .files(
                javaSample("androidx.annotation.Experimental"),
                javaSample("androidx.annotation.UseExperimental"),
                *testFiles
            )
            .allowMissingSdk(true)
            .issues(*ExperimentalDetector.ISSUES.toTypedArray())
            .run()
    }

    @Test
    fun useJavaExperimentalFromJava() {
        val input = arrayOf(
            javaSample("sample.DateProvider"),
            javaSample("sample.ExperimentalDateTime"),
            javaSample("sample.UseJavaExperimentalFromJava")
        )

        /* ktlint-disable max-line-length */
        val expected = """
src/sample/UseJavaExperimentalFromJava.java:24: Error: This declaration is experimental and its usage should be marked with
'@sample.ExperimentalDateTime' or '@UseExperimental(sample.ExperimentalDateTime.class)' [UnsafeExperimentalUsageError]
        DateProvider dateProvider = new DateProvider();
                                    ~~~~~~~~~~~~~~~~~~
src/sample/UseJavaExperimentalFromJava.java:25: Error: This declaration is experimental and its usage should be marked with
'@sample.ExperimentalDateTime' or '@UseExperimental(sample.ExperimentalDateTime.class)' [UnsafeExperimentalUsageError]
        return dateProvider.getDate();
                            ~~~~~~~
2 errors, 0 warnings
    """.trimIndent()
        /* ktlint-enable max-line-length */

        checkJava(*input).expect(expected)
    }

    @Test
    fun useJavaExperimentalFromKt() {
        val input = arrayOf(
            javaSample("sample.DateProvider"),
            javaSample("sample.ExperimentalDateTime"),
            ktSample("sample.UseJavaExperimentalFromKt")
        )

        /* ktlint-disable max-line-length */
        val expected = """
src/sample/UseJavaExperimentalFromKt.kt:24: Error: This declaration is experimental and its usage should be marked with
'@sample.ExperimentalDateTime' or '@UseExperimental(sample.ExperimentalDateTime.class)' [UnsafeExperimentalUsageError]
        val dateProvider = DateProvider()
                           ~~~~~~~~~~~~
src/sample/UseJavaExperimentalFromKt.kt:25: Error: This declaration is experimental and its usage should be marked with
'@sample.ExperimentalDateTime' or '@UseExperimental(sample.ExperimentalDateTime.class)' [UnsafeExperimentalUsageError]
        return dateProvider.date
                            ~~~~
src/sample/UseJavaExperimentalFromKt.kt:36: Error: This declaration is experimental and its usage should be marked with
'@sample.ExperimentalDateTime' or '@UseExperimental(sample.ExperimentalDateTime.class)' [UnsafeExperimentalUsageError]
        val dateProvider = DateProvider()
                           ~~~~~~~~~~~~
src/sample/UseJavaExperimentalFromKt.kt:37: Error: This declaration is experimental and its usage should be marked with
'@sample.ExperimentalDateTime' or '@UseExperimental(sample.ExperimentalDateTime.class)' [UnsafeExperimentalUsageError]
        return dateProvider.date
                            ~~~~
4 errors, 0 warnings
        """.trimIndent()
        /* ktlint-enable max-line-length */

        checkJava(*input).expect(expected)
    }

    @Test
    fun useKtExperimentalFromJava() {
        val input = arrayOf(
            EXPERIMENTAL_KT,
            ktSample("sample.DateProviderKt"),
            ktSample("sample.ExperimentalDateTimeKt"),
            javaSample("sample.UseKtExperimentalFromJava")
        )

        /* ktlint-disable max-line-length */
        val expected = """
src/sample/UseKtExperimentalFromJava.java:24: Error: This declaration is experimental and its usage should be marked with
'@sample.ExperimentalDateTimeKt' or '@UseExperimental(sample.ExperimentalDateTimeKt.class)' [UnsafeExperimentalUsageError]
        DateProviderKt dateProvider = new DateProviderKt();
                                      ~~~~~~~~~~~~~~~~~~~~
src/sample/UseKtExperimentalFromJava.java:25: Error: This declaration is experimental and its usage should be marked with
'@sample.ExperimentalDateTimeKt' or '@UseExperimental(sample.ExperimentalDateTimeKt.class)' [UnsafeExperimentalUsageError]
        return dateProvider.getDate();
                            ~~~~~~~
2 errors, 0 warnings
        """.trimIndent()
        /* ktlint-enable max-line-length */

        checkJava(*input).expect(expected)
    }

    /**
     * Loads a [TestFile] from Java source code included in the JAR resources.
     */
    private fun javaSample(className: String): TestFile {
        return java(javaClass.getResource("/java/${className.replace('.','/')}.java").readText())
    }

    /**
     * Loads a [TestFile] from Kotlin source code included in the JAR resources.
     */
    private fun ktSample(className: String): TestFile {
        return kotlin(javaClass.getResource("/java/${className.replace('.','/')}.kt").readText())
    }

    companion object {
        /* ktlint-disable max-line-length */
        // The contents of Experimental.kt from the Kotlin standard library.
        val EXPERIMENTAL_KT: TestFile = kotlin("""
            package kotlin

            import kotlin.annotation.AnnotationRetention.BINARY
            import kotlin.annotation.AnnotationRetention.SOURCE
            import kotlin.annotation.AnnotationTarget.*
            import kotlin.internal.RequireKotlin
            import kotlin.internal.RequireKotlinVersionKind
            import kotlin.reflect.KClass

            @Target(ANNOTATION_CLASS)
            @Retention(BINARY)
            @SinceKotlin("1.2")
            @RequireKotlin("1.2.50", versionKind = RequireKotlinVersionKind.COMPILER_VERSION)
            @Suppress("ANNOTATION_CLASS_MEMBER")
            public annotation class Experimental(val level: Level = Level.ERROR) {
                public enum class Level {
                    WARNING,
                    ERROR,
                }
            }

            @Target(
                CLASS, PROPERTY, LOCAL_VARIABLE, VALUE_PARAMETER, CONSTRUCTOR, FUNCTION, PROPERTY_GETTER, PROPERTY_SETTER, EXPRESSION, FILE, TYPEALIAS
            )
            @Retention(SOURCE)
            @SinceKotlin("1.2")
            @RequireKotlin("1.2.50", versionKind = RequireKotlinVersionKind.COMPILER_VERSION)
            public annotation class UseExperimental(
                vararg val markerClass: KClass<out Annotation>
            )

            @Target(CLASS, PROPERTY, CONSTRUCTOR, FUNCTION, TYPEALIAS)
            @Retention(BINARY)
            internal annotation class WasExperimental(
                vararg val markerClass: KClass<out Annotation>
            )
        """.trimIndent())
        /* ktlint-enable max-line-length */
    }
}

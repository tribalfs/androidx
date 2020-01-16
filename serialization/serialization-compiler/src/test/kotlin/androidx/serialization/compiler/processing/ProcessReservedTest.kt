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

package androidx.serialization.compiler.processing

import androidx.serialization.compiler.processing.steps.AbstractProcessingStep
import androidx.serialization.schema.Reserved
import com.google.auto.common.BasicAnnotationProcessor
import com.google.common.truth.Truth.assertThat
import com.google.testing.compile.CompilationSubject.assertThat
import com.google.testing.compile.Compiler.javac
import com.google.testing.compile.JavaFileObjects
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import org.junit.Test
import javax.lang.model.SourceVersion
import javax.lang.model.element.Element
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass

/** Unit tests for [processReserved]. */
class ProcessReservedTest {
    @Test
    fun testEmpty() {
        val typeElement = mock<TypeElement> {
            on { annotationMirrors } doReturn emptyList()
        }

        assertThat(processReserved(typeElement)).isSameInstanceAs(Reserved.empty())
    }

    @Test
    fun testIds() {
        assertThat(compile("@Reserved(ids = {1, 2, 3})").ids).containsExactly(1, 2, 3)
    }

    @Test
    fun testNames() {
        assertThat(compile("""@Reserved(names = {"foo", "bar"})""").names)
            .containsExactly("foo", "bar")
    }

    @Test
    fun testIdRanges() {
        val reserved = """
            @Reserved(idRanges = {
                @Reserved.IdRange(from = 1, to = 2),
                @Reserved.IdRange(from = 4, to = 3) // Reversed for testing
            })
        """.trimIndent()
        assertThat(compile(reserved).idRanges).containsExactly(1..2, 3..4)
    }

    private fun compile(reserved: String): Reserved {
        val processor = ReservedProcessor()
        val source = JavaFileObjects.forSourceString("TestClass", """
            import androidx.serialization.Reserved;
            
            $reserved
            public final class TestClass {}
        """.trimIndent())
        assertThat(javac().withProcessors(processor).compile(source)).succeededWithoutWarnings()
        return processor.reserved
    }

    private class ReservedProcessingStep(
        private val onReserved: (Reserved) -> Unit
    ) : AbstractProcessingStep(androidx.serialization.Reserved::class) {
        override fun process(elementsByAnnotation: Map<KClass<out Annotation>, Set<Element>>) {
            elementsByAnnotation[androidx.serialization.Reserved::class]?.forEach {
                onReserved(processReserved(it.asTypeElement()))
            }
        }
    }

    private class ReservedProcessor : BasicAnnotationProcessor() {
        lateinit var reserved: Reserved

        override fun initSteps(): List<ProcessingStep> = listOf(
            ReservedProcessingStep { reserved = it }
        )

        override fun getSupportedSourceVersion(): SourceVersion = SourceVersion.latest()
    }
}

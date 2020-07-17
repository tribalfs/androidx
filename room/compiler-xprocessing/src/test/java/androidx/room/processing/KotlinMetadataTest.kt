/*
 * Copyright (C) 2020 The Android Open Source Project
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

package androidx.room.processing

import androidx.room.processing.testcode.KotlinTestClass
import androidx.room.processing.util.Source
import androidx.room.processing.util.getMethod
import androidx.room.processing.util.getParameter
import androidx.room.processing.util.runProcessorTest
import com.google.common.truth.Truth.assertThat
import com.squareup.javapoet.TypeName
import org.junit.Test

class KotlinMetadataTest {
    @Test
    fun readWithMetadata() {
        val source = Source.kotlin(
            "Dummy.kt", """
            class Dummy
        """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(source)
        ) {
            val element = it.processingEnv.requireTypeElement(KotlinTestClass::class)
            element.getMethod("mySuspendMethod").apply {
                assertThat(parameters).hasSize(2)
                assertThat(getParameter("param1").type.typeName)
                    .isEqualTo(TypeName.get(String::class.java))
                assertThat(isSuspendFunction()).isTrue()
            }
        }
    }
}

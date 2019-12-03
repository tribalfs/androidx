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

package androidx.ui.text

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

@RunWith(JUnit4::class)
class TextStyleAndSpanStyleTest {

    @Test
    fun spanStyle_is_covered_by_textStyle() {
        val spanStyleParameters = constructorParams(SpanStyle::class)
        val textStyleParameters = constructorParams(TextStyle::class)

        // assert that textStyleParameters contains all of spanStyleParameters
        assertThat(textStyleParameters).containsAtLeastElementsIn(spanStyleParameters)
    }

    private fun <T : Any> constructorParams(clazz: KClass<T>): List<Parameter> {
        return clazz.primaryConstructor?.parameters?.map { Parameter(it) } ?: listOf()
    }

    private data class Parameter(
        val name: String?,
        val type: KType,
        val optional: Boolean,
        val isVarArg: Boolean,
        val kind: KParameter.Kind
    ) {
        constructor(parameter: KParameter) : this(
            parameter.name,
            parameter.type,
            parameter.isOptional,
            parameter.isVararg,
            parameter.kind
        )
    }
}

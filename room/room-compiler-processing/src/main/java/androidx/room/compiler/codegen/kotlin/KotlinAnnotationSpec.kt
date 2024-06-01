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

package androidx.room.compiler.codegen.kotlin

import androidx.room.compiler.codegen.KAnnotationSpecBuilder
import androidx.room.compiler.codegen.XAnnotationSpec
import androidx.room.compiler.codegen.XCodeBlock
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.javapoet.KAnnotationSpec

internal class KotlinAnnotationSpec(internal val actual: KAnnotationSpec) :
    KotlinLang(), XAnnotationSpec {

    internal class Builder(internal val actual: KAnnotationSpecBuilder) :
        KotlinLang(), XAnnotationSpec.Builder {
        override fun addMember(name: String, code: XCodeBlock) = apply {
            require(code is KotlinCodeBlock)
            actual.addMember(CodeBlock.of("$name = %L", code.actual))
        }

        override fun build(): XAnnotationSpec {
            return KotlinAnnotationSpec(actual.build())
        }
    }
}

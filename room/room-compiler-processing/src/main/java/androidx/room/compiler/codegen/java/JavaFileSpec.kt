/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.room.compiler.codegen.java

import androidx.room.compiler.codegen.CodeLanguage
import androidx.room.compiler.codegen.JFileSpec
import androidx.room.compiler.codegen.JFileSpecBuilder
import androidx.room.compiler.codegen.XCodeBlock
import androidx.room.compiler.codegen.XFileSpec
import androidx.room.compiler.codegen.impl.XCodeBlockImpl
import androidx.room.compiler.processing.XFiler

internal class JavaFileSpec(internal val actual: JFileSpec) : XFileSpec {

    override fun writeTo(language: CodeLanguage, generator: XFiler, mode: XFiler.Mode) {
        generator.write(actual, mode)
    }

    internal class Builder(internal val actual: JFileSpecBuilder) : XFileSpec.Builder {

        override fun addFileComment(code: XCodeBlock) = apply {
            require(code is XCodeBlockImpl)
            actual.addFileComment("\$L", code.java.actual)
        }

        override fun build() = JavaFileSpec(actual.build())
    }
}

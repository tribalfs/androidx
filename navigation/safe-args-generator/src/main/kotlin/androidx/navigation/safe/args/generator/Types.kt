/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.navigation.safe.args.generator

import androidx.navigation.safe.args.generator.ext.S
import androidx.navigation.safe.args.generator.models.ResReference
import androidx.navigation.safe.args.generator.models.accessor
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.TypeName

enum class NavType {

    INT {
        override fun typeName(): TypeName = TypeName.INT
        override fun bundlePutMethod() = "putInt"
        override fun bundleGetMethod() = "getInt"
    },

    STRING {
        override fun typeName(): TypeName = ClassName.get(String::class.java)
        override fun bundlePutMethod() = "putString"
        override fun bundleGetMethod() = "getString"
    },

    REFERENCE {
        // it is internally the same as INT, but we don't want to allow to
        // assignment between int and reference args
        override fun typeName(): TypeName = TypeName.INT

        override fun bundlePutMethod() = "putInt"
        override fun bundleGetMethod() = "getInt"
    };

    abstract fun typeName(): TypeName
    abstract fun bundlePutMethod(): String
    abstract fun bundleGetMethod(): String
}

sealed class WriteableValue {
    abstract fun write(): CodeBlock
}

data class ReferenceValue(private val resReference: ResReference?) : WriteableValue() {
    override fun write(): CodeBlock = CodeBlock.of(resReference.accessor())
}

data class StringValue(private val value: String) : WriteableValue() {
    override fun write(): CodeBlock = CodeBlock.of(S, value)
}

// keeping value as String, it will help to preserve client format of it: hex, dec
data class IntValue(private val value: String) : WriteableValue() {
    override fun write(): CodeBlock = CodeBlock.of(value)
}
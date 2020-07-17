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

package androidx.room.processing.javac

import androidx.room.processing.XExecutableType
import androidx.room.processing.XType
import com.google.auto.common.MoreTypes
import com.squareup.javapoet.TypeVariableName
import javax.lang.model.type.ExecutableType

internal class JavacExecutableType(
    val env: JavacProcessingEnv,
    val executableType: ExecutableType
) : XExecutableType {
    override val returnType: JavacType by lazy {
        env.wrap<JavacType>(executableType.returnType)
    }

    override val typeVariableNames by lazy {
        executableType.typeVariables.map {
            TypeVariableName.get(it)
        }
    }

    override val parameterTypes: List<JavacType> by lazy {
        executableType.parameterTypes.map {
            env.wrap<JavacType>(it)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (other !is JavacExecutableType) return false
        return executableType == other.executableType
    }

    override fun hashCode(): Int {
        return executableType.hashCode()
    }

    override fun getSuspendFunctionReturnType(): XType {
        // the continuation parameter is always the last parameter of a suspend function and it only
        // has one type parameter, e.g Continuation<? super T>
        val typeParam =
            MoreTypes.asDeclared(executableType.parameterTypes.last()).typeArguments.first()
        return env.wrap<JavacType>(typeParam).extendsBoundOrSelf() // reduce the type param
    }

    override fun toString(): String {
        return executableType.toString()
    }
}

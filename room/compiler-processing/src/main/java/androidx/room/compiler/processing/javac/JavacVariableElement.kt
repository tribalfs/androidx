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

package androidx.room.compiler.processing.javac

import androidx.room.compiler.processing.XDeclaredType
import androidx.room.compiler.processing.XType
import androidx.room.compiler.processing.XVariableElement
import com.google.auto.common.MoreTypes
import javax.lang.model.element.VariableElement

internal open class JavacVariableElement(
    env: JavacProcessingEnv,
    val containing: JavacTypeElement,
    override val element: VariableElement
) : JavacElement(env, element), XVariableElement {
    override val type: JavacType by lazy {
        MoreTypes.asMemberOf(env.typeUtils, containing.type.typeMirror, element).let {
            env.wrap<JavacType>(
                typeMirror = it,
                nullability = element.nullability
            )
        }
    }

    override fun asMemberOf(other: XDeclaredType): XType {
        return if (containing.type.isSameType(other)) {
            type
        } else {
            check(other is JavacDeclaredType)
            val asMember = MoreTypes.asMemberOf(env.typeUtils, other.typeMirror, element)
            env.wrap<JavacType>(
                typeMirror = asMember,
                nullability = element.nullability
            )
        }
    }

    override val equalityItems: Array<out Any?> by lazy {
        arrayOf(element, containing)
    }
}

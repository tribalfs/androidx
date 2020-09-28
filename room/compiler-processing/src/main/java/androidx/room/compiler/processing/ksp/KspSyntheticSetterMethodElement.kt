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

package androidx.room.compiler.processing.ksp

import androidx.room.compiler.processing.XDeclaredType
import androidx.room.compiler.processing.XExecutableParameterElement
import androidx.room.compiler.processing.XHasModifiers
import androidx.room.compiler.processing.XMethodElement
import androidx.room.compiler.processing.XMethodType
import androidx.room.compiler.processing.XType
import androidx.room.compiler.processing.XTypeElement
import org.jetbrains.kotlin.ksp.symbol.KSPropertySetter
import java.util.Locale

/**
 * In KSP, setters of properties do not show up in function list but we need to see them as
 * functions in Room's abstraction, hence we create this synthetic method elements for them.
 */
internal class KspSyntheticSetterMethodElement(
    env: KspProcessingEnv,
    val containing: KspTypeElement,
    val setter: KSPropertySetter
) : KspElement(
    env = env,
    declaration = setter.receiver
), XMethodElement, XHasModifiers by KspHasModifiers(setter.receiver) {
    override val equalityItems: Array<out Any?> by lazy {
        arrayOf(containing, setter)
    }

    override val name: String by lazy {
        // see https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html#properties
        val propName = setter.receiver.simpleName.asString()
        if (propName.startsWith("is")) {
            "set${propName.substring(2)}"
        } else {
            "set${propName.capitalize(Locale.US)}"
        }
    }

    override val returnType: XType
        get() = TODO("Not yet implemented")

    override val executableType: XMethodType
        get() = TODO("Not yet implemented")

    override fun isJavaDefault(): Boolean {
        return false
    }

    override fun asMemberOf(other: XDeclaredType): XMethodType {
        TODO("Not yet implemented")
    }

    override fun hasKotlinDefaultImpl(): Boolean {
        return false
    }

    override fun isSuspendFunction(): Boolean {
        return false
    }

    override val enclosingTypeElement: XTypeElement by lazy {
        setter.receiver.requireEnclosingTypeElement(env)
    }

    override val parameters: List<XExecutableParameterElement>
        get() = emptyList()

    override fun isVarArgs(): Boolean {
        return false
    }

    override fun overrides(other: XMethodElement, owner: XTypeElement): Boolean {
        return other is KspSyntheticGetterMethodElement &&
                setter.receiver.overrides(other.getter.receiver)
    }

    override fun copyTo(newContainer: XTypeElement): XMethodElement {
        check(newContainer is KspTypeElement)
        return KspSyntheticSetterMethodElement(
            env = env,
            containing = newContainer,
            setter = setter
        )
    }
}

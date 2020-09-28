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

import androidx.room.compiler.processing.XExecutableElement
import androidx.room.compiler.processing.XExecutableParameterElement
import androidx.room.compiler.processing.XHasModifiers
import androidx.room.compiler.processing.XTypeElement
import org.jetbrains.kotlin.ksp.symbol.KSFunctionDeclaration

internal open class KspExecutableElement(
    env: KspProcessingEnv,
    val containing: KspTypeElement,
    override val declaration: KSFunctionDeclaration
) : KspElement(
    env = env,
    declaration = declaration
), XExecutableElement, XHasModifiers by KspHasModifiers(declaration) {

    override val equalityItems: Array<out Any?> by lazy {
        arrayOf(containing, declaration)
    }

    override val enclosingTypeElement: XTypeElement by lazy {
        declaration.requireEnclosingTypeElement(env)
    }

    override val parameters: List<XExecutableParameterElement>
        get() = TODO(
            """
            implement parameters. need to be careful w/ suspend functions as they need to fake
            an additional parameter to look like java
        """.trimIndent()
        )

    override fun isVarArgs(): Boolean {
        return declaration.parameters.any {
            it.isVararg
        }
    }
}

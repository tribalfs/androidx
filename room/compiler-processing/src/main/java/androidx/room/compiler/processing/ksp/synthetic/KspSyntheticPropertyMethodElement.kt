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

package androidx.room.compiler.processing.ksp.synthetic

import androidx.room.compiler.processing.XAnnotated
import androidx.room.compiler.processing.XEquality
import androidx.room.compiler.processing.XExecutableParameterElement
import androidx.room.compiler.processing.XHasModifiers
import androidx.room.compiler.processing.XMethodElement
import androidx.room.compiler.processing.XMethodType
import androidx.room.compiler.processing.XType
import androidx.room.compiler.processing.XTypeElement
import androidx.room.compiler.processing.ksp.KspAnnotated
import androidx.room.compiler.processing.ksp.KspAnnotated.UseSiteFilter.Companion.NO_USE_SITE
import androidx.room.compiler.processing.ksp.KspAnnotated.UseSiteFilter.Companion.PROPERTY_GETTER
import androidx.room.compiler.processing.ksp.KspAnnotated.UseSiteFilter.Companion.PROPERTY_SETTER
import androidx.room.compiler.processing.ksp.KspAnnotated.UseSiteFilter.Companion.PROPERTY_SETTER_PARAMETER
import androidx.room.compiler.processing.ksp.KspFieldElement
import androidx.room.compiler.processing.ksp.KspHasModifiers
import androidx.room.compiler.processing.ksp.KspProcessingEnv
import androidx.room.compiler.processing.ksp.KspTypeElement
import androidx.room.compiler.processing.ksp.overrides
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import java.util.Locale

/**
 * Kotlin properties don't have getters/setters in KSP. As Room expects Java code, we synthesize
 * them.
 *
 * @see KspSyntheticPropertyMethodElement.Getter
 * @see KspSyntheticPropertyMethodElement.Setter
 * @see KspSyntheticPropertyMethodType
 */
internal sealed class KspSyntheticPropertyMethodElement(
    val env: KspProcessingEnv,
    val field: KspFieldElement,
    accessor: KSPropertyAccessor?
) : XMethodElement,
    XEquality,
    XHasModifiers by KspHasModifiers.createForSyntheticAccessor(
        field.declaration,
        accessor
    ) {
    // NOTE: modifiers of the property are not necessarily my modifiers.
    //  that being said, it only matters if it is private in which case KAPT does not generate the
    //  synthetic hence we don't either.
    final override fun isJavaDefault() = false

    final override fun hasKotlinDefaultImpl() = false

    final override fun isSuspendFunction() = false

    final override val enclosingTypeElement: XTypeElement
        get() = this.field.enclosingTypeElement

    final override fun isVarArgs() = false

    final override val executableType: XMethodType by lazy {
        KspSyntheticPropertyMethodType.create(
            element = this,
            container = field.containing.type
        )
    }

    final override fun asMemberOf(other: XType): XMethodType {
        return KspSyntheticPropertyMethodType.create(
            element = this,
            container = other
        )
    }

    override fun equals(other: Any?): Boolean {
        return XEquality.equals(this, other)
    }

    override fun hashCode(): Int {
        return XEquality.hashCode(equalityItems)
    }

    final override fun overrides(other: XMethodElement, owner: XTypeElement): Boolean {
        return env.resolver.overrides(this, other)
    }

    internal class Getter(
        env: KspProcessingEnv,
        field: KspFieldElement
    ) : KspSyntheticPropertyMethodElement(
        env = env,
        field = field,
        accessor = field.declaration.getter
    ),
        XAnnotated by KspAnnotated.create(
            env = env,
            delegate = field.declaration.getter,
            filter = NO_USE_SITE
        ) + KspAnnotated.create(
            env = env,
            delegate = field.declaration,
            filter = PROPERTY_GETTER
        ) {
        override val equalityItems: Array<out Any?> by lazy {
            arrayOf(field, "getter")
        }

        @OptIn(KspExperimental::class)
        override val name: String by lazy {
            field.declaration.getter?.let {
                return@lazy env.resolver.getJvmName(it)
            }
            computeGetterName(field.name)
        }

        override val returnType: XType by lazy {
            field.type
        }

        override val parameters: List<XExecutableParameterElement>
            get() = emptyList()

        override fun kindName(): String {
            return "synthetic property getter"
        }

        override fun copyTo(newContainer: XTypeElement): XMethodElement {
            check(newContainer is KspTypeElement)
            return Getter(
                env = env,
                field = field.copyTo(newContainer)
            )
        }

        companion object {
            private fun computeGetterName(propName: String): String {
                // see https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html#properties
                return if (propName.startsWith("is")) {
                    propName
                } else {
                    "get${propName.capitalize(Locale.US)}"
                }
            }
        }
    }

    internal class Setter(
        env: KspProcessingEnv,
        field: KspFieldElement
    ) : KspSyntheticPropertyMethodElement(
        env = env,
        field = field,
        accessor = field.declaration.setter
    ),
        XAnnotated by KspAnnotated.create(
            env = env,
            delegate = field.declaration.setter,
            filter = NO_USE_SITE
        ) + KspAnnotated.create(
            env = env,
            delegate = field.declaration,
            filter = PROPERTY_SETTER
        ) {
        override val equalityItems: Array<out Any?> by lazy {
            arrayOf(field, "setter")
        }

        @OptIn(KspExperimental::class)
        override val name: String by lazy {
            field.declaration.setter?.let {
                return@lazy env.resolver.getJvmName(it)
            }
            computeSetterName(field.name)
        }

        override val returnType: XType by lazy {
            env.voidType
        }

        override val parameters: List<XExecutableParameterElement> by lazy {
            listOf(
                SyntheticExecutableParameterElement(
                    env = env,
                    origin = this
                )
            )
        }

        override fun kindName(): String {
            return "synthetic property getter"
        }

        override fun copyTo(newContainer: XTypeElement): XMethodElement {
            check(newContainer is KspTypeElement)
            return Setter(
                env = env,
                field = field.copyTo(newContainer)
            )
        }

        private class SyntheticExecutableParameterElement(
            env: KspProcessingEnv,
            private val origin: Setter
        ) : XExecutableParameterElement,
            XAnnotated by KspAnnotated.create(
                env = env,
                delegate = origin.field.declaration,
                filter = PROPERTY_SETTER_PARAMETER
            ) + KspAnnotated.create(
                env = env,
                delegate = origin.field.declaration.setter?.parameter,
                filter = NO_USE_SITE
            ) {

            override val name: String by lazy {
                origin.field.declaration.setter?.parameter?.name?.asString() ?: "value"
            }
            override val type: XType
                get() = origin.field.type

            override val fallbackLocationText: String
                get() = "$name in ${origin.fallbackLocationText}"

            override fun asMemberOf(other: XType): XType {
                return origin.field.asMemberOf(other)
            }

            override fun kindName(): String {
                return "method parameter"
            }
        }

        companion object {
            private fun computeSetterName(propName: String): String {
                // see https://kotlinlang.org/docs/reference/java-to-kotlin-interop.html#properties
                return if (propName.startsWith("is")) {
                    "set${propName.substring(2)}"
                } else {
                    "set${propName.capitalize(Locale.US)}"
                }
            }
        }
    }
}
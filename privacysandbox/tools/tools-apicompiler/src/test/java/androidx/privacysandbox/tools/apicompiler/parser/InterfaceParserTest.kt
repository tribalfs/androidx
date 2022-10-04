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

package androidx.privacysandbox.tools.apicompiler.parser

import androidx.privacysandbox.tools.apicompiler.util.checkSourceFails
import androidx.privacysandbox.tools.apicompiler.util.parseSources
import androidx.privacysandbox.tools.core.model.AnnotatedInterface
import androidx.privacysandbox.tools.core.model.Method
import androidx.privacysandbox.tools.core.model.Parameter
import androidx.privacysandbox.tools.core.model.ParsedApi
import androidx.privacysandbox.tools.core.model.Types
import androidx.privacysandbox.tools.core.model.Type
import androidx.room.compiler.processing.util.Source
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class InterfaceParserTest {

    @Test
    fun parseServiceInterface_ok() {
        val source = Source.kotlin(
            "com/mysdk/MySdk.kt", """
                    package com.mysdk
                    import androidx.privacysandbox.tools.PrivacySandboxService
                    @PrivacySandboxService
                    interface MySdk {
                        suspend fun doStuff(x: Int, y: Int): String
                        fun doMoreStuff()
                    }
                """
        )
        assertThat(parseSources(source)).isEqualTo(
            ParsedApi(
                services = mutableSetOf(
                    AnnotatedInterface(
                        type = Type(packageName = "com.mysdk", simpleName = "MySdk"),
                        methods = listOf(
                            Method(
                                name = "doStuff",
                                parameters = listOf(
                                    Parameter(
                                        name = "x",
                                        type = Types.int,
                                    ), Parameter(
                                        name = "y",
                                        type = Types.int,
                                    )
                                ),
                                returnType = Types.string,
                                isSuspend = true,
                            ), Method(
                                name = "doMoreStuff",
                                parameters = listOf(),
                                returnType = Types.unit,
                                isSuspend = false,
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun serviceAnnotatedClass_fails() {
        val source = Source.kotlin(
            "com/mysdk/MySdk.kt", """
                    package com.mysdk
                    import androidx.privacysandbox.tools.PrivacySandboxService
                    @PrivacySandboxService
                    abstract class MySdk {  // Fails because it's a class, not an interface.
                        abstract fun doStuff(x: Int, y: Int): String
                    }
                """
        )

        checkSourceFails(source)
            .containsError("Only interfaces can be annotated with @PrivacySandboxService.")
    }

    @Test
    fun multipleServices_fails() {
        val source = Source.kotlin(
            "com/mysdk/MySdk.kt", """
                    package com.mysdk
                    import androidx.privacysandbox.tools.PrivacySandboxService
                    @PrivacySandboxService
                    interface MySdk
                """
        )
        val source2 = Source.kotlin(
            "com/mysdk/MySdk2.kt", """
                    package com.mysdk
                    import androidx.privacysandbox.tools.PrivacySandboxService
                    @PrivacySandboxService
                    interface MySdk2
                """
        )

        checkSourceFails(source, source2)
            .containsError(
                "Multiple interfaces annotated with @PrivacySandboxService are not " +
                    "supported (MySdk, MySdk2)."
            )
    }

    @Test
    fun privateInterface_fails() {
        checkSourceFails(
            serviceInterface("private interface MySdk")
        ).containsError("Error in com.mysdk.MySdk: annotated interfaces should be public.")
    }

    @Test
    fun interfaceWithProperties_fails() {
        checkSourceFails(
            serviceInterface(
                """public interface MySdk {
                    |   val x: Int
                    |}
                """.trimMargin()
            )
        ).containsExactlyErrors(
            "Error in com.mysdk.MySdk: annotated interfaces cannot declare properties."
        )
    }

    @Test
    fun interfaceWithCompanionObject_fails() {
        checkSourceFails(
            serviceInterface(
                """interface MySdk {
                    |   companion object {
                    |       fun foo() {}
                    |   }
                    |}
                """.trimMargin()
            )
        ).containsExactlyErrors(
            "Error in com.mysdk.MySdk: annotated interfaces cannot declare companion objects."
        )
    }

    @Test
    fun interfaceWithInvalidModifier_fails() {
        checkSourceFails(
            serviceInterface(
                """external fun interface MySdk {
                    |   suspend fun apply()
                    |}
                """.trimMargin()
            )
        ).containsExactlyErrors(
            "Error in com.mysdk.MySdk: annotated interface contains invalid modifiers (external, " +
                "fun)."
        )
    }

    @Test
    fun interfaceWithGenerics_fails() {
        checkSourceFails(
            serviceInterface(
                """interface MySdk<T, U> {
                    |   suspend fun getT()
                    |}
                """.trimMargin()
            )
        ).containsExactlyErrors(
            "Error in com.mysdk.MySdk: annotated interfaces cannot declare type parameters (T, U)."
        )
    }

    @Test
    fun methodWithImplementation_fails() {
        checkSourceFails(serviceMethod("suspend fun foo(): Int = 1")).containsExactlyErrors(
            "Error in com.mysdk.MySdk.foo: method cannot have default implementation."
        )
    }

    @Test
    fun methodWithGenerics_fails() {
        checkSourceFails(serviceMethod("suspend fun <T> foo()")).containsExactlyErrors(
            "Error in com.mysdk.MySdk.foo: method cannot declare type parameters (<T>)."
        )
    }

    @Test
    fun methodWithInvalidModifiers_fails() {
        checkSourceFails(serviceMethod("suspend inline fun foo()")).containsExactlyErrors(
            "Error in com.mysdk.MySdk.foo: method contains invalid modifiers (inline)."
        )
    }

    @Test
    fun parameterWitDefaultValue_fails() {
        checkSourceFails(serviceMethod("suspend fun foo(x: Int = 5)")).containsExactlyErrors(
            "Error in com.mysdk.MySdk.foo: parameters cannot have default values."
        )
    }

    @Test
    fun parameterWithGenerics_fails() {
        checkSourceFails(serviceMethod("suspend fun foo(x: MutableList<Int>)"))
            .containsExactlyErrors(
                "Error in com.mysdk.MySdk.foo: only primitive types are supported."
            )
    }

    @Test
    fun parameterLambda_fails() {
        checkSourceFails(serviceMethod("suspend fun foo(x: (Int) -> Int)"))
            .containsExactlyErrors(
                "Error in com.mysdk.MySdk.foo: only primitive types are supported."
            )
    }

    @Test
    fun returnTypeCustomClass_fails() {
        val source = Source.kotlin(
            "com/mysdk/MySdk.kt", """
                    package com.mysdk
                    import androidx.privacysandbox.tools.PrivacySandboxService
                    @PrivacySandboxService
                    interface MySdk {
                        suspend fun foo(): CustomClass
                    }

                    class CustomClass
                """
        )
        checkSourceFails(source).containsExactlyErrors(
            "Error in com.mysdk.MySdk.foo: only primitive types are supported."
        )
    }

    @Test
    fun nullableParameter_fails() {
        checkSourceFails(serviceMethod("suspend fun foo(x: Int?)"))
            .containsError(
                "Error in com.mysdk.MySdk.foo: nullable types are not supported."
            )
    }

    @Test
    fun parseCallbackInterface_ok() {
        val serviceSource =
            Source.kotlin(
                "com/mysdk/MySdk.kt",
                """
                    package com.mysdk
                    import androidx.privacysandbox.tools.PrivacySandboxService
                    @PrivacySandboxService
                    interface MySdk
                """
            )
        val callbackSource =
            Source.kotlin(
                "com/mysdk/MyCallback.kt",
                """
                    package com.mysdk
                    import androidx.privacysandbox.tools.PrivacySandboxCallback
                    @PrivacySandboxCallback
                    interface MyCallback {
                        fun onComplete(x: Int, y: Int)
                    }
                """
            )
        assertThat(parseSources(serviceSource, callbackSource)).isEqualTo(
            ParsedApi(
                services = setOf(
                    AnnotatedInterface(
                        type = Type(packageName = "com.mysdk", simpleName = "MySdk"),
                    )
                ),
                callbacks = setOf(
                    AnnotatedInterface(
                        type = Type(packageName = "com.mysdk", simpleName = "MyCallback"),
                        methods = listOf(
                            Method(
                                name = "onComplete",
                                parameters = listOf(
                                    Parameter(
                                        name = "x",
                                        type = Types.int,
                                    ),
                                    Parameter(
                                        name = "y",
                                        type = Types.int,
                                    )
                                ),
                                returnType = Types.unit,
                                isSuspend = false,
                            ),
                        )
                    )
                )
            )
        )
    }

    private fun serviceInterface(declaration: String) = Source.kotlin(
        "com/mysdk/MySdk.kt", """
            package com.mysdk
            import androidx.privacysandbox.tools.PrivacySandboxService
            @PrivacySandboxService
            $declaration
        """
    )

    private fun serviceMethod(declaration: String) = serviceInterface(
        """
            interface MySdk {
                $declaration
            }
        """
    )
}
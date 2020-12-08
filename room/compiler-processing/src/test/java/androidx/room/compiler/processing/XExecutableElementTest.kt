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

package androidx.room.compiler.processing

import androidx.room.compiler.processing.util.CONTINUATION_CLASS_NAME
import androidx.room.compiler.processing.util.Source
import androidx.room.compiler.processing.util.UNIT_CLASS_NAME
import androidx.room.compiler.processing.util.className
import androidx.room.compiler.processing.util.getDeclaredMethod
import androidx.room.compiler.processing.util.getMethod
import androidx.room.compiler.processing.util.getParameter
import androidx.room.compiler.processing.util.runProcessorTest
import androidx.room.compiler.processing.util.typeName
import com.google.common.truth.Truth
import com.google.common.truth.Truth.assertThat
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.WildcardTypeName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class XExecutableElementTest {
    @Test
    fun basic() {
        runProcessorTest(
            sources = listOf(
                Source.java(
                    "foo.bar.Baz",
                    """
                package foo.bar;
                public class Baz {
                    private void foo() {}
                    public String bar(String[] param1) {
                        return "";
                    }
                }
                    """.trimIndent()
                )
            )
        ) {
            val element = it.processingEnv.requireTypeElement("foo.bar.Baz")
            element.getDeclaredMethod("foo").let { method ->
                assertThat(method.isJavaDefault()).isFalse()
                assertThat(method.isVarArgs()).isFalse()
                assertThat(method.isOverrideableIgnoringContainer()).isFalse()
                assertThat(method.parameters).isEmpty()
                val returnType = method.returnType
                // check both as in KSP, it will show up as Unit
                assertThat(returnType.isVoid() || returnType.isKotlinUnit()).isTrue()
                assertThat(returnType.defaultValue()).isEqualTo("null")
            }
            element.getDeclaredMethod("bar").let { method ->
                assertThat(method.isOverrideableIgnoringContainer()).isTrue()
                assertThat(method.parameters).hasSize(1)
                method.getParameter("param1").let { param ->
                    assertThat(param.type.isArray()).isTrue()
                    assertThat(param.type.asArray().componentType.typeName)
                        .isEqualTo(String::class.typeName())
                }
                assertThat(method.returnType.typeName).isEqualTo(String::class.typeName())
            }
        }
    }

    @Test
    fun isVarArgs() {
        val subject = Source.java(
            "foo.bar.Baz",
            """
            package foo.bar;
            interface Baz {
                void method(String... inputs);
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(subject)
        ) {
            val element = it.processingEnv.requireTypeElement("foo.bar.Baz")
            assertThat(element.getMethod("method").isVarArgs()).isTrue()
        }
    }

    @Test
    fun isVarArgs_kotlin() {
        val subject = Source.kotlin(
            "Subject.kt",
            """
            interface Subject {
                fun method(vararg inputs: String)
                suspend fun suspendMethod(vararg inputs: String);
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(subject)
        ) {
            val element = it.processingEnv.requireTypeElement("Subject")
            assertThat(element.getMethod("method").isVarArgs()).isTrue()
            assertThat(element.getMethod("suspendMethod").isVarArgs()).isFalse()
        }
    }

    @Test
    fun kotlinDefaultImpl() {
        val subject = Source.kotlin(
            "Baz.kt",
            """
            package foo.bar;
            import java.util.List;
            interface Baz {
                fun noDefault()
                fun withDefault(): Int {
                    return 3;
                }
                fun nameMatch()
                fun nameMatch(param:Int) {}
                fun withDefaultWithParams(param1:Int, param2:String) {}
                fun withDefaultWithTypeArgs(param1: List<String>): String {
                    return param1.first();
                }
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(subject)
        ) {
            val element = it.processingEnv.requireTypeElement("foo.bar.Baz")
            element.getDeclaredMethod("noDefault").let { method ->
                assertThat(method.hasKotlinDefaultImpl()).isFalse()
            }
            element.getDeclaredMethod("withDefault").let { method ->
                assertThat(method.hasKotlinDefaultImpl()).isTrue()
            }
            element.getDeclaredMethods().first {
                it.name == "nameMatch" && it.parameters.isEmpty()
            }.let { nameMatchWithoutDefault ->
                assertThat(nameMatchWithoutDefault.hasKotlinDefaultImpl()).isFalse()
            }

            element.getDeclaredMethods().first {
                it.name == "nameMatch" && it.parameters.size == 1
            }.let { nameMatchWithoutDefault ->
                assertThat(nameMatchWithoutDefault.hasKotlinDefaultImpl()).isTrue()
            }

            element.getDeclaredMethod("withDefaultWithParams").let { method ->
                assertThat(method.hasKotlinDefaultImpl()).isTrue()
            }

            element.getDeclaredMethod("withDefaultWithTypeArgs").let { method ->
                assertThat(method.hasKotlinDefaultImpl()).isTrue()
            }
        }
    }

    @Test
    fun suspendMethod() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            class Subject {
                suspend fun noArg():Unit = TODO()
                suspend fun intReturn(): Int = TODO()
                suspend fun twoParams(param1:String, param2:Int): Pair<String, Int> = TODO()
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(src)
        ) { invocation ->
            val subject = invocation.processingEnv.requireTypeElement("Subject")
            subject.getMethod("noArg").let { method ->
                assertThat(method.parameters).hasSize(1)
                assertThat(method.isSuspendFunction()).isTrue()
                assertThat(method.returnType.typeName).isEqualTo(TypeName.OBJECT)
                assertThat(method.returnType.nullability).isEqualTo(XNullability.NULLABLE)
                method.executableType.parameterTypes.last().let { cont ->
                    assertThat(cont.typeName).isEqualTo(
                        ParameterizedTypeName.get(
                            CONTINUATION_CLASS_NAME,
                            WildcardTypeName.supertypeOf(UNIT_CLASS_NAME)
                        )
                    )
                    assertThat(cont.nullability).isEqualTo(XNullability.NONNULL)
                }
            }
            subject.getMethod("intReturn").let { method ->
                assertThat(method.parameters).hasSize(1)
                assertThat(method.parameters.last().type.typeName).isEqualTo(
                    ParameterizedTypeName.get(
                        CONTINUATION_CLASS_NAME,
                        WildcardTypeName.supertypeOf(Integer::class.java)
                    )
                )
                assertThat(method.isSuspendFunction()).isTrue()
                assertThat(method.returnType.typeName).isEqualTo(TypeName.OBJECT)
                method.executableType.parameterTypes.last().let { cont ->
                    assertThat(cont.typeName).isEqualTo(
                        ParameterizedTypeName.get(
                            CONTINUATION_CLASS_NAME,
                            WildcardTypeName.supertypeOf(Integer::class.java)
                        )
                    )
                }
            }
            subject.getMethod("twoParams").let { method ->
                assertThat(method.parameters).hasSize(3)
                assertThat(method.parameters[0].type.typeName).isEqualTo(
                    String::class.typeName()
                )
                assertThat(method.parameters[1].type.typeName).isEqualTo(
                    TypeName.INT
                )
                assertThat(method.isSuspendFunction()).isTrue()
                assertThat(method.returnType.typeName).isEqualTo(TypeName.OBJECT)
                method.executableType.parameterTypes.last().let { cont ->
                    assertThat(cont.typeName).isEqualTo(
                        ParameterizedTypeName.get(
                            CONTINUATION_CLASS_NAME,
                            WildcardTypeName.supertypeOf(
                                ParameterizedTypeName.get(
                                    Pair::class.className(),
                                    String::class.typeName(),
                                    Integer::class.typeName()
                                )
                            )
                        )
                    )
                }
            }
        }
    }

    @Test
    fun kotlinProperties() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            data class MyDataClass(val x:String, var y:String, private val z:String) {
                val prop1: String = ""
                var prop2: String = ""
                var prop3: String = TODO()
                    private set
                    get() = TODO()
                private val prop4:String = ""
                protected var prop5:String = ""
                var prop6: String
                    get // this cannot be protected, https://youtrack.jetbrains.com/issue/KT-3110
                    protected set
                protected var prop7: String
                    private set
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val klass = invocation.processingEnv.requireTypeElement("MyDataClass")
            val methodNames = klass.getAllMethods().map {
                it.name
            }
            assertThat(methodNames).containsNoneIn(
                listOf(
                    "setX", "setProp1", "setProp3", "setZ", "setProp4", "getProp4", "setProp7"
                )
            )
            listOf("getX", "getProp1", "getProp2", "getProp3", "getProp5", "getProp6").forEach {
                klass.getMethod(it).let { method ->
                    assertThat(method.returnType.typeName).isEqualTo(String::class.typeName())
                    assertThat(method.parameters).isEmpty()
                }
            }
            listOf("setY", "setProp2").forEach {
                klass.getMethod(it).let { method ->
                    assertThat(method.returnType.typeName).isEqualTo(TypeName.VOID)
                    assertThat(method.parameters.first().type.typeName).isEqualTo(
                        String::class.typeName()
                    )
                    assertThat(method.isPublic()).isTrue()
                }
            }
            listOf("getProp5", "getProp7").forEach {
                klass.getMethod(it).let { method ->
                    assertThat(method.isProtected()).isTrue()
                    assertThat(method.isPublic()).isFalse()
                    assertThat(method.isPrivate()).isFalse()
                }
            }
            listOf("setProp5", "setProp6").forEach {
                klass.getMethod(it).let { method ->
                    assertThat(method.isProtected()).isTrue()
                    assertThat(method.isPublic()).isFalse()
                    assertThat(method.isPrivate()).isFalse()
                }
            }
        }
    }

    @Test
    fun parametersAsMemberOf() {
        val source = Source.kotlin(
            "Foo.kt",
            """
            open class Base<T> {
                fun foo(t:T, nullableT:T?): List<T?> = TODO()
            }
            class Subject : Base<String>()
            class NullableSubject: Base<String?>()
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(source)) { invocation ->
            val base = invocation.processingEnv.requireTypeElement("Base")
            val subject = invocation.processingEnv.requireType("Subject")
                .asDeclaredType()
            val nullableSubject = invocation.processingEnv.requireType("NullableSubject")
                .asDeclaredType()
            val method = base.getMethod("foo")
            method.getParameter("t").let { param ->
                param.asMemberOf(subject).let {
                    assertThat(it.typeName).isEqualTo(String::class.typeName())
                    assertThat(it.nullability).isEqualTo(XNullability.NONNULL)
                }
                param.asMemberOf(nullableSubject).let {
                    assertThat(it.typeName).isEqualTo(String::class.typeName())
                    if (invocation.isKsp) {
                        // kapt implementation is unable to read this properly
                        assertThat(it.nullability).isEqualTo(XNullability.NULLABLE)
                    }
                }
            }
            method.getParameter("nullableT").let { param ->
                param.asMemberOf(subject).let {
                    assertThat(it.typeName).isEqualTo(String::class.typeName())
                    assertThat(it.nullability).isEqualTo(XNullability.NULLABLE)
                }
                param.asMemberOf(nullableSubject).let {
                    assertThat(it.typeName).isEqualTo(String::class.typeName())
                    assertThat(it.nullability).isEqualTo(XNullability.NULLABLE)
                }
            }
        }
    }

    @Test
    fun kotlinPropertyOverrides() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            interface MyInterface {
                val x:Int
                var y:Int
            }
            class MyImpl : MyInterface {
                override var x: Int = 1
                override var y: Int = 1
            }
            """.trimIndent()
        )
        val javaSrc = Source.java(
            "JavaImpl",
            """
            class JavaImpl implements MyInterface {
                public int getX() {
                    return 1;
                }
                public int getY() {
                    return 1;
                }
                public void setY(int value) {
                }
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src, javaSrc)) { invocation ->
            val base = invocation.processingEnv.requireTypeElement("MyInterface")
            val impl = invocation.processingEnv.requireTypeElement("MyImpl")
            val javaImpl = invocation.processingEnv.requireTypeElement("JavaImpl")

            fun overrides(
                owner: XTypeElement,
                ownerMethodName: String,
                base: XTypeElement,
                baseMethodName: String = ownerMethodName
            ): Boolean {
                val overrider = owner.getMethod(ownerMethodName)
                val overridden = base.getMethod(baseMethodName)
                return overrider.overrides(
                    overridden, owner
                )
            }
            listOf(impl, javaImpl).forEach { subject ->
                listOf("getY", "getX", "setY").forEach { methodName ->
                    Truth.assertWithMessage("${subject.className}:$methodName").that(
                        overrides(
                            owner = subject,
                            ownerMethodName = methodName,
                            base = base
                        )
                    ).isTrue()
                }

                Truth.assertWithMessage(subject.className.canonicalName()).that(
                    overrides(
                        owner = subject,
                        ownerMethodName = "getY",
                        base = base,
                        baseMethodName = "getX"
                    )
                ).isFalse()

                Truth.assertWithMessage(subject.className.canonicalName()).that(
                    overrides(
                        owner = subject,
                        ownerMethodName = "getY",
                        base = subject,
                        baseMethodName = "getX"
                    )
                ).isFalse()

                Truth.assertWithMessage(subject.className.canonicalName()).that(
                    overrides(
                        owner = base,
                        ownerMethodName = "getX",
                        base = subject,
                        baseMethodName = "getX"
                    )
                ).isFalse()

                Truth.assertWithMessage(subject.className.canonicalName()).that(
                    overrides(
                        owner = subject,
                        ownerMethodName = "setY",
                        base = base,
                        baseMethodName = "getY"
                    )
                ).isFalse()

                Truth.assertWithMessage(subject.className.canonicalName()).that(
                    overrides(
                        owner = subject,
                        ownerMethodName = "setY",
                        base = subject,
                        baseMethodName = "setY"
                    )
                ).isFalse()
            }
        }
    }
}

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

import androidx.room.compiler.processing.javac.JavacMethodElement
import androidx.room.compiler.processing.javac.JavacTypeElement
import androidx.room.compiler.processing.util.Source
import androidx.room.compiler.processing.util.XTestInvocation
import androidx.room.compiler.processing.util.javaTypeUtils
import androidx.room.compiler.processing.util.runKaptTest
import androidx.room.compiler.processing.util.runProcessorTest
import com.google.auto.common.MoreTypes
import com.google.common.truth.Truth.assertThat
import com.squareup.javapoet.MethodSpec
import org.junit.Test
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.type.DeclaredType
import javax.lang.model.util.Types

class MethodSpecHelperTest {
    @Test
    fun javaOverrides() {
        // check our override impl matches javapoet
        val source = Source.java(
            "foo.bar.Baz",
            """
            package foo.bar;
            import androidx.room.compiler.processing.testcode.OtherAnnotation;

            public class Baz {
                public void method1() {
                }

                public void method2(int x) {
                }

                public int parameterAnnotation(@OtherAnnotation("x") int y) {
                    return 3;
                }

                @OtherAnnotation("x")
                public int methodAnnotation(int y) {
                    return 3;
                }

                public int varargMethod(int... y) {
                    return 3;
                }

                protected <R> R typeArgs(R r) {
                    return r;
                }

                protected void throwsException() throws Exception {
                }
            }
            """.trimIndent()
        )
        overridesCheck(source)
    }

    @Test
    fun kotlinOverrides() {
        // check our override impl matches javapoet
        val source = Source.kotlin(
            "Foo.kt",
            """
            package foo.bar;
            import androidx.room.compiler.processing.testcode.OtherAnnotation;

            open class Baz {
                open fun method1() {
                }

                open fun method2(x:Int) {
                }

                open fun parameterAnnotation(@OtherAnnotation("x") y:Int): Int {
                    return 3;
                }

                @OtherAnnotation("x")
                open fun methodAnnotation(y: Int): Int {
                    return 3;
                }

                open fun varargMethod(vararg y:Int): Int {
                    return 3;
                }

                open fun boxedLongArrayReturn(): Array<Long> {
                    TODO();
                }

                open fun boxedIntArrayReturn(): Array<Int> {
                    TODO();
                }

                protected open fun listArg(r:List<String>) {
                }

                open suspend fun suspendUnitFun() {
                }

                protected open suspend fun suspendBasic(p0:Int):String {
                    TODO()
                }

                protected open suspend fun suspendVarArg(p0:Int, vararg p1:String):Long {
                    TODO()
                }

                protected open fun <R> typeArgs(r:R): R {
                    return r;
                }

                internal open fun internalFun() {
                }

                @Throws(Exception::class)
                protected open fun throwsException() {
                }
            }
            """.trimIndent()
        )
        overridesCheck(source)
    }

    @Test
    fun variance() {
        // check our override impl matches javapoet
        val source = Source.kotlin(
            "Foo.kt",
            """
            package foo.bar;
            interface MyInterface<T> {
                suspend fun suspendReturnList(arg1:Int, arg2:String):List<T>
            }
            interface I1<in T>
            interface I2<out T>
            interface I3<T>
            enum class Lang {
               ES,
               EN;
            }
            class Box<out T>(val value: T)

            interface Base
            class Derived : Base

            interface Baz : MyInterface<String> {
                fun boxDerived(value: Derived): Box<Derived> = Box(value)
                fun unboxBase(box: Box<Base>): Base = box.value
                fun unboxString(box: Box<String>): String = box.value
                fun findByLanguages(langs: Set<Lang>): List<String>
                fun f1(args : I1<String>): I1<String>
                fun f2(args : I2<String>): I2<String>
                fun f3(args : I3<String>): I3<String>
                suspend fun s1(args : I1<String>): I1<String>
                suspend fun s2(args : I2<String>): I2<String>
                suspend fun s3(args : I3<String>): I3<String>
                suspend fun s4(args : I1<String>): String
            }
            """.trimIndent()
        )
        overridesCheck(source)
    }

    @Test
    fun inheritedVariance_openType() {
        val source = Source.kotlin(
            "Foo.kt",
            """
            package foo.bar;
            interface MyInterface<T> {
                fun receiveList(argsInParent : List<T>):Unit
                suspend fun suspendReturnList(arg1:Int, arg2:String):List<T>
            }
            open class Book(val id:Int)
            interface Baz : MyInterface<Book> {
                fun myList(args: List<Book>):Unit
                override fun receiveList(argsInParent : List<Book>):Unit
            }
            """.trimIndent()
        )
        overridesCheck(source)
    }

    @Test
    fun inheritedVariance_finalType() {
        val source = Source.kotlin(
            "Foo.kt",
            """
            package foo.bar;
            interface MyInterface<T> {
                fun receiveList(argsInParent : List<T>):Unit
                suspend fun suspendReturnList(arg1:Int, arg2:String):List<T>
            }
            interface Baz : MyInterface<String> {
                fun myList(args: List<String>):Unit
                override fun receiveList(argsInParent : List<String>):Unit
            }
            """.trimIndent()
        )
        overridesCheck(source, ignoreInheritedMethods = true)
    }

    @Test
    fun inheritedVariance_enumType() {
        val source = Source.kotlin(
            "Foo.kt",
            """
            package foo.bar;
            enum class EnumType {
                FOO,
                BAR;
            }
            interface MyInterface<T> {
                fun receiveList(argsInParent : List<T>):Unit
                suspend fun suspendReturnList(arg1:Int, arg2:String):List<T>
            }
            interface Baz : MyInterface<EnumType> {
                fun myList(args: List<EnumType>):Unit
                override fun receiveList(argsInParent : List<EnumType>):Unit
            }
            """.trimIndent()
        )
        overridesCheck(source)
    }

    @Test
    fun inheritedVariance_multiLevel() {
        val source = Source.kotlin(
            "Foo.kt",
            """
            package foo.bar;
            interface GrandParent<T> {
                fun receiveList(list : List<T>): Unit
                suspend fun suspendReceiveList(list : List<T>): Unit
                suspend fun suspendReturnList(): List<T>
            }
            interface Parent: GrandParent<Number> {
            }
            interface Baz : Parent {
            }
            """.trimIndent()
        )
        overridesCheck(source)
    }

    @Test
    fun primitiveOverrides() {
        val source = Source.kotlin(
            "Foo.kt",
            """
            package foo.bar
            data class LongFoo(val id: Long, val description: String)
            /* Interface with generics only */
            interface MyInterface<Key, Value> {
                fun getItem(id: Key): Value?
                //fun delete(id: Key)
                //fun getFirstItemId(): Key
            }
            /* Interface with non-generics and generics */
            interface Baz : MyInterface<Long, LongFoo> {
                override fun getItem(id: Long): LongFoo?
                //override fun delete(id: Long)
                //fun insert(item: LongFoo)
                //override fun getFirstItemId(): Long
            }
            """.trimIndent()
        )
        overridesCheck(source)
    }

    private fun overridesCheck(source: Source, ignoreInheritedMethods: Boolean = false) {
        // first build golden image with Java processor so we can use JavaPoet's API
        val golden = buildMethodsViaJavaPoet(source, ignoreInheritedMethods)
        runProcessorTest(
            sources = listOf(source)
        ) { invocation ->
            val (target, methods) = invocation.getOverrideTestTargets(ignoreInheritedMethods)
            methods.forEachIndexed { index, method ->

                if (invocation.isKsp && method.name == "throwsException") {
                    // TODO b/171572318
                } else {
                    val subject = MethodSpecHelper.overridingWithFinalParams(
                        method,
                        target.type
                    ).build().toString()
                    assertThat(subject).isEqualTo(golden[index])
                }
            }
        }
    }

    private fun buildMethodsViaJavaPoet(
        source: Source,
        ignoreInheritedMethods: Boolean
    ): List<String> {
        lateinit var result: List<String>
        runKaptTest(
            sources = listOf(source)
        ) { invocation ->
            val (target, methods) = invocation.getOverrideTestTargets(
                ignoreInheritedMethods
            )
            val element = (target as JavacTypeElement).element
            result = methods
                .map {
                    (it as JavacMethodElement).element
                }.map {
                    generateFromJavapoet(
                        it,
                        MoreTypes.asDeclared(element.asType()),
                        invocation.javaTypeUtils
                    ).build().toString()
                }
        }
        return result
    }

    /**
     * Get test targets. There is an edge case where it is not possible to implement an interface
     * in java, b/174313780. [ignoreInheritedMethods] helps avoid that case.
     */
    private fun XTestInvocation.getOverrideTestTargets(
        ignoreInheritedMethods: Boolean
    ): Pair<XTypeElement, List<XMethodElement>> {
        val objectMethodNames = processingEnv
            .requireTypeElement("java.lang.Object")
            .getAllNonPrivateInstanceMethods()
            .map {
                it.name
            }
        val target = processingEnv.requireTypeElement("foo.bar.Baz")
        val methods = if (ignoreInheritedMethods) {
            target.getDeclaredMethods().filter { !it.isStatic() }
        } else {
            target.getAllNonPrivateInstanceMethods()
        }
        val selectedMethods = methods.filter {
            it.isOverrideableIgnoringContainer()
        }.filterNot {
            it.name in objectMethodNames
        }
        return target to selectedMethods
    }

    private fun generateFromJavapoet(
        method: ExecutableElement,
        owner: DeclaredType,
        typeUtils: Types
    ): MethodSpec.Builder {
        return overrideWithoutAnnotations(
            elm = method,
            owner = owner,
            typeUtils = typeUtils
        )
    }

    /**
     * Copied from DaoWriter for backwards compatibility
     */
    private fun overrideWithoutAnnotations(
        elm: ExecutableElement,
        owner: DeclaredType,
        typeUtils: Types
    ): MethodSpec.Builder {
        val baseSpec = MethodSpec.overriding(elm, owner, typeUtils)
            .build()

        // make all the params final
        val params = baseSpec.parameters.map { it.toBuilder().addModifiers(Modifier.FINAL).build() }

        return MethodSpec.methodBuilder(baseSpec.name).apply {
            addAnnotation(Override::class.java)
            addModifiers(baseSpec.modifiers)
            addParameters(params)
            addTypeVariables(baseSpec.typeVariables)
            addExceptions(baseSpec.exceptions)
            varargs(baseSpec.varargs)
            returns(baseSpec.returnType)
        }
    }
}

/*
 * Copyright 2021 The Android Open Source Project
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
import androidx.room.compiler.processing.XMethodElement
import androidx.room.compiler.processing.util.CompilationTestCapabilities
import androidx.room.compiler.processing.util.Source
import androidx.room.compiler.processing.util.XTestInvocation
import androidx.room.compiler.processing.util.compileFiles
import androidx.room.compiler.processing.util.runKaptTest
import androidx.room.compiler.processing.util.runKspTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.squareup.javapoet.TypeName
import org.junit.Test

/**
 * This test generates a brute force of method signatures and ensures they match what we generate
 * in KAPT.
 */
class KspTypeNamesGoldenTest {

    @Test
    fun kaptGoldenTest() {
        CompilationTestCapabilities.assumeKspIsEnabled()

        val (sources, sourceSubjects) = createSubjects(pkg = "main")
        val (classpathSources, classpathSubjects) = createSubjects(pkg = "lib")
        val classpath = compileFiles(classpathSources)
        val subjects = sourceSubjects + classpathSubjects
        fun collectSignaturesInto(
            invocation: XTestInvocation,
            output: MutableMap<String, List<TypeName>>
        ) {
            // collect all methods in the Object class to exclude them from matching
            val objectMethodNames = invocation.processingEnv.requireTypeElement(Any::class)
                .getAllMethods().map { it.name }.toSet()

            fun XExecutableElement.createNewUniqueKey(
                owner: String
            ): String {
                val prefix = this.enclosingElement.className.canonicalName()
                val name = if (this is XMethodElement) {
                    name
                } else {
                    "<init>"
                }
                val key = "$owner.$prefix.$name"
                check(!output.containsKey(key)) {
                    "test expects unique method names. $key is here: ${output[key]}"
                }
                return key
            }
            subjects.map {
                val klass = invocation.processingEnv.requireTypeElement(it)

                klass.getConstructors().singleOrNull()?.let { constructor ->
                    val testKey = constructor.createNewUniqueKey(klass.qualifiedName)
                    output[testKey] = constructor.parameters.map {
                        it.type.typeName
                    }
                }
                // KAPT might duplicate overridden methods. ignore them for test purposes
                // see b/205911014
                val declaredMethodNames = klass.getDeclaredMethods().map {
                    it.name
                }.toSet()
                val methods = klass.getDeclaredMethods() + klass.getAllMethods().filterNot {
                    it.name in declaredMethodNames
                }

                methods
                    .filterNot {
                        // remote these synthetics generated by kapt for property annotations
                        it.name.contains("\$annotations") || objectMethodNames.contains(it.name)
                    }
                    .forEach { method ->
                        val testKey = method.createNewUniqueKey(klass.qualifiedName)
                        val types = listOf(
                            method.returnType
                        ) + method.parameters.map { it.type }
                        output[testKey] = types.map {
                            it.typeName
                        }
                    }
            }
        }
        // classQName.methodName -> returnType, ...paramTypes
        val golden = mutableMapOf<String, List<TypeName>>()
        runKaptTest(
            sources = sources, classpath = classpath
        ) { invocation ->
            collectSignaturesInto(invocation, golden)
        }
        val ksp = mutableMapOf<String, List<TypeName>>()
        runKspTest(
            sources = sources, classpath = classpath
        ) { invocation ->
            collectSignaturesInto(invocation, ksp)
        }

        // make sure we grabbed some values to ensure test is working
        assertThat(golden).isNotEmpty()
        subjects.forEach { subject ->
            assertWithMessage("there are no methods from $subject").that(
                golden.keys.any {
                    it.startsWith(subject)
                }
            ).isTrue()
            assertWithMessage("there are no methods from $subject").that(
                golden.keys.any {
                    it.startsWith(subject)
                }
            ).isTrue()
        }
        // turn them into string, provides better failure reports
        fun Map<String, List<TypeName>>.signatures(): List<Pair<String, String>> {
            return this.entries.map {
                it.key to it.value.joinToString(" ")
            }.sortedBy {
                it.first
            }
        }
        assertThat(ksp.signatures())
            .containsExactlyElementsIn(golden.signatures())
            .inOrder()
    }

    private fun createSubjects(
        pkg: String
    ): TestInput {
        val declarations = Source.kotlin(
            "declarations.kt",
            """
                package $pkg
                class MyType
                class MyGeneric<T>
                enum class MyEnum {
                    VAL1,
                    VAL2;
                }
                """.trimIndent()
        )
        return listOf(
            createBasicSubject(pkg),
            createVarianceSubject(
                pkg = pkg,
                className = "VarianceSubject",
                suppressWildcards = false
            ),
            createVarianceSubject(
                pkg,
                className = "VarianceSubjectSuppressed",
                suppressWildcards = true
            ),
            createOverrideSubjects(pkg),
            createOverridesWithGenericArguments(pkg),
            createKotlinOverridesJavaSubjects(pkg),
            createKotlinOverridesJavaSubjectsWithGenerics(pkg),
            createOverrideSubjectsWithDifferentSuppressLevels(pkg),
            createMultiLevelInheritanceSubjects(pkg),
            createJavaOverridesKotlinSubjects(pkg),
            createOverrideWithBoundsSubjects(pkg),
            createOverrideWithMultipleBoundsSubjects(pkg)
        ).fold(TestInput(declarations, emptyList())) { acc, next ->
            acc + next.copy(
                subjects = next.subjects.map { "$pkg.$it" } // add package
            )
        }
    }

    /**
     * Simple class, no overrides etc
     */
    private fun createBasicSubject(pkg: String): TestInput {
        return TestInput(
            Source.kotlin(
                "Foo.kt",
                """
                    package $pkg
                    class Subject {
                        fun method1():MyGeneric<MyType> = TODO()
                        fun method2(items: MyGeneric<in MyType>): MyType = TODO()
                        fun method3(items: MyGeneric<out MyType>): MyType = TODO()
                        fun method4(items: MyGeneric<MyType>): MyType = TODO()
                        fun method5(): MyGeneric<out MyType> = TODO()
                        fun method6(): MyGeneric<in MyType> = TODO()
                        fun method7(): MyGeneric<MyType> = TODO()
                        fun method8(): MyGeneric<*> = TODO()
                        fun method9(args : Array<Int>):Array<Array<String>> = TODO()
                        fun method10(args : Array<Array<Int>>):Array<String> = TODO()
                        fun method11(iter: Iterable<String>): Iterable<String> = TODO()
                        fun method12(iter: Iterable<Number>): Iterable<Number> = TODO()
                        fun method13(iter: Iterable<MyType>): Iterable<MyType> = TODO()
                    }
                """.trimIndent()
            ), listOf("Subject")
        )
    }

    /**
     * Subjects with variance and star projections.
     */
    private fun createVarianceSubject(
        pkg: String,
        className: String,
        suppressWildcards: Boolean
    ): TestInput {
        val annotation = if (suppressWildcards) {
            "@JvmSuppressWildcards"
        } else {
            ""
        }
        return TestInput(
            Source.kotlin(
                "$className.kt",
                """
                package $pkg
                $annotation
                class $className<R>(
                    starList: List<*>,
                    typeArgList: List<R>,
                    numberList: List<Number>,
                    stringList: List<String>,
                    enumList: List<MyEnum>,
                    jvmWildcard: List<@JvmWildcard String>,
                    suppressJvmWildcard: List<@JvmSuppressWildcards Number>
                ) {
                    var propWithFinalType: String = ""
                    var propWithOpenType: Number = 3
                    var propWithFinalGeneric: List<String> = TODO()
                    var propWithOpenGeneric: List<Number> = TODO()
                    var propWithTypeArg: R = TODO()
                    var propWithTypeArgGeneric: List<R> = TODO()
                    @JvmSuppressWildcards
                    var propWithOpenTypeButSuppressAnnotation: Number = 3
                    fun list(list: List<*>): List<*> { TODO() }
                    fun listTypeArg(list: List<R>): List<R> { TODO() }
                    fun listTypeArgNumber(list: List<Number>): List<Number> { TODO() }
                    fun listTypeArgString(list: List<String>): List<String> { TODO() }
                    fun listTypeArgEnum(list: List<MyEnum>): List<MyEnum> { TODO() }
                    fun explicitJvmWildcard(
                        list: List<@JvmWildcard String>
                    ): List<@JvmWildcard String> { TODO() }

                    fun explicitJvmSuppressWildcard_OnType(
                        list: List<@JvmSuppressWildcards Number>
                    ): List<@JvmSuppressWildcards Number> { TODO() }

                    fun explicitJvmSuppressWildcard_OnType2(
                        list: @JvmSuppressWildcards List<Number>
                    ): @JvmSuppressWildcards List<Number> { TODO() }
                    fun suspendList(list: List<*>): List<*> { TODO() }
                    fun suspendListTypeArg(list: List<R>): List<R> { TODO() }
                    fun suspendListTypeArgNumber(list: List<Number>): List<Number> { TODO() }
                    fun suspendListTypeArgString(list: List<String>): List<String> { TODO() }
                    fun suspendListTypeArgEnum(list: List<MyEnum>): List<MyEnum> { TODO() }
                    fun suspendExplicitJvmWildcard(
                        list: List<@JvmWildcard String>
                    ): List<@JvmWildcard String> { TODO() }

                    fun suspendExplicitJvmSuppressWildcard_OnType(
                        list: List<@JvmSuppressWildcards Number>
                    ): List<@JvmSuppressWildcards Number> { TODO() }

                    fun suspendExplicitJvmSuppressWildcard_OnType2(
                        list: @JvmSuppressWildcards List<Number>
                    ): @JvmSuppressWildcards List<Number> { TODO() }
                }
                """.trimIndent()
            ), listOf(className)
        )
    }

    /**
     * Create test cases where inheritance and wildcards annotations are involved
     */
    private fun createOverrideSubjectsWithDifferentSuppressLevels(pkg: String): TestInput {
        val source = Source.kotlin(
            "OverridesWithSuppress.kt",
            """
            package $pkg
            interface NormalBase<T> {
                fun receiveReturnListT(t : List<T>): List<T>
                fun receiveReturnListTWithWildcard(t : @JvmWildcard List<T>): List<@JvmWildcard T>
            }
            @JvmSuppressWildcards
            interface SuppressedBase<T> {
                fun receiveReturnListT(t : List<T>): List<T>
            }
            @JvmSuppressWildcards
            interface SuppressedInMethodBase<T> {
                @JvmSuppressWildcards
                fun receiveReturnListT(t : List<T>): List<T>
            }
            interface NoOverrideNormal: NormalBase<Number>
            interface NoOverrideSuppressedBase: SuppressedBase<Number>
            interface NoOverrideSuppressedInMethodBase: SuppressedInMethodBase<Number>

            @JvmSuppressWildcards
            interface SuppressInInterface: NormalBase<Number>

            interface OverrideWithSuppress: NormalBase<Number> {
                @JvmSuppressWildcards
                override fun receiveReturnListT(t : List<Number>): List<Number>
            }

            interface OverrideWildcardMethod: NormalBase<Number> {
                override fun receiveReturnListTWithWildcard(t : List<Number>): List<Number>
            }

            @JvmSuppressWildcards
            interface OverrideWildcardMethodSuppressed: NormalBase<Number> {
                override fun receiveReturnListTWithWildcard(t : List<Number>): List<Number>
            }
            """.trimIndent()
        )
        return TestInput(
            source, listOf(
                "NoOverrideNormal",
                "NoOverrideSuppressedBase",
                "NoOverrideSuppressedInMethodBase",
                "SuppressInInterface",
                "OverrideWithSuppress",
                "OverrideWildcardMethod",
                "OverrideWildcardMethodSuppressed"
            )
        )
    }

    /**
     * Create test cases where interfaces are overridden by other interfaces
     */
    private fun createOverrideSubjects(pkg: String): TestInput {
        val base = """
            interface BaseInterface<T> {
                var tProp: T
                var tListProp: List<T>
                var definedProp: Number
                var definedPropList: List<Number>
                fun receiveReturnT(t: T): T
                suspend fun suspendReceiveReturnT(t:T):T
                fun receiveReturnTList(t:List<T>): List<T>
                suspend fun suspendReceiveReturnTList(t:List<T>): List<T>
                fun receiveReturnTOverridden(t: T): T
                suspend fun suspendReceiveReturnTOverridden(t:T):T
                fun receiveReturnTListOverridden(t:List<T>): List<T>
                suspend fun suspendReceiveReturnTListOverridden(t:List<T>): List<T>
            }
        """.trimIndent()

        fun buildInterface(
            name: String,
            typeArg: String
        ) = """
            interface $name: BaseInterface<$typeArg> {
                override fun receiveReturnTOverridden(t: $typeArg): $typeArg
                override suspend fun suspendReceiveReturnTOverridden(t:$typeArg):$typeArg
                override fun receiveReturnTListOverridden(t:List<$typeArg>): List<$typeArg>
                override suspend fun suspendReceiveReturnTListOverridden(t:List<$typeArg>): List<$typeArg>
            }
        """.trimIndent()

        val code = listOf(
            "package $pkg",
            base,
            buildInterface("SubInterface<R>", "R"),
            buildInterface("SubInterfaceOpen", "Number"),
            buildInterface("SubInterfaceEnum", "MyEnum"),
            buildInterface("SubInterfaceFinal", "String"),
            buildInterface("SubInterfacePrimitive", "Int"),
            buildInterface("SubInterfacePrimitiveNullable", "Int?"),
        ).joinToString("\n")
        val classNames = listOf(
            "BaseInterface",
            "SubInterface",
            "SubInterfaceOpen",
            "SubInterfaceEnum",
            "SubInterfaceFinal",
            "SubInterfacePrimitive",
            "SubInterfacePrimitiveNullable",
        )
        return TestInput(Source.kotlin("Overrides.kt", code), classNames)
    }

    /**
     * Create test cases where interfaces are overridden by other interfaces and the type argument
     * has a generic type
     */
    private fun createOverridesWithGenericArguments(
        pkg: String
    ): TestInput {
        // this is not included in buildOverrides case because we don't yet properly handle
        // return type getting variance. see:
        // see: https://issuetracker.google.com/issues/204415667#comment10
        val base = """
            interface GenericBaseInterface<T>  {
                fun receiveT(t:T): Unit
                suspend fun suspendReceiveT(t:T): Unit
                fun receiveTList(t:List<T>): Unit
                suspend fun suspendReceiveTList(t:List<T>): Unit
                fun receiveTArray(t: Array<T>): Unit
                suspend fun suspendReceiveTArray(t: Array<T>): Unit
                fun receiveTOverridden(t:T): Unit
                suspend fun suspendTOverridden(t:T): Unit
                fun receiveTListOverridden(t:List<T>): Unit
                suspend fun suspendReceiveTListOverridden(t:List<T>): Unit
                fun receiveTArrayOverridden(t: Array<T>): Unit
                suspend fun suspendReceiveTArrayOverridden(t: Array<T>): Unit
            }
        """.trimIndent()

        fun buildInterface(
            name: String,
            typeArg: String
        ) = """
            interface $name: GenericBaseInterface<$typeArg> {
                override fun receiveTOverridden(t:$typeArg): Unit
                override suspend fun suspendTOverridden(t:$typeArg): Unit
                override fun receiveTListOverridden(t:List<$typeArg>): Unit
                override suspend fun suspendReceiveTListOverridden(t:List<$typeArg>): Unit
                override fun receiveTArrayOverridden(t: Array<$typeArg>): Unit
                override suspend fun suspendReceiveTArrayOverridden(t: Array<$typeArg>): Unit
            }
        """.trimIndent()

        val code = listOf(
            "package $pkg",
            base,
            buildInterface("SubInterfaceWithGenericR<R>", "List<R>"),
            buildInterface("SubInterfaceWithGenericOpen", "List<Number>"),
            buildInterface("SubInterfaceWithGenericEnum", "List<MyEnum>"),
            buildInterface("SubInterfaceWithGenericFinal", "List<String>"),
            buildInterface("SubInterfaceWithGenericPrimitive", "List<Int>"),
            buildInterface("SubInterfaceWithGenericPrimitiveNullable", "List<Int?>"),
            buildInterface("SubInterfaceWithGenericArray", "Array<Number>"),
            buildInterface("SubInterfaceWithGenericArrayPrimitive", "Array<IntArray>"),
        ).joinToString("\n")
        val classNames = listOf(
            "GenericBaseInterface",
            "SubInterfaceWithGenericR",
            "SubInterfaceWithGenericOpen",
            "SubInterfaceWithGenericEnum",
            "SubInterfaceWithGenericFinal",
            "SubInterfaceWithGenericPrimitive",
            "SubInterfaceWithGenericPrimitiveNullable",
            "SubInterfaceWithGenericArray",
            "SubInterfaceWithGenericArrayPrimitive"
        )
        return TestInput(Source.kotlin("OverridesWithGenerics.kt", code), classNames)
    }

    /**
     * Create test cases where kotlin overrides a java interface
     */
    private fun createKotlinOverridesJavaSubjects(
        pkg: String
    ): TestInput {
        val javaBase = Source.java(
            "$pkg.BaseJavaInterface",
            """
            package $pkg;
            import java.util.List;
            public interface BaseJavaInterface<T> {
                T receiveReturnT(T t);
                List<T> receiveReturnTList(List<T> t);
                T receiveReturnTOverridden(T t);
                List<T> receiveReturnTListOverridden(List<T> t);
            }
            """
        )

        fun buildInterface(
            name: String,
            typeArg: String
        ) = """
            public interface $name: BaseJavaInterface<$typeArg> {
                override fun receiveReturnTOverridden(t: $typeArg): $typeArg
                override fun receiveReturnTListOverridden(t:List<$typeArg>): List<$typeArg>
            }
        """.trimIndent()

        val subInterfaces = listOf(
            "package $pkg",
            buildInterface("SubInterfaceOverridingJava<R>", "R"),
            buildInterface("SubInterfaceOverridingJavaOpen", "Number"),
            buildInterface("SubInterfaceOverridingJavaEnum", "MyEnum"),
            buildInterface("SubInterfaceOverridingJavaFinal", "String"),
            buildInterface("SubInterfaceOverridingJavaPrimitive", "Int"),
            buildInterface("SubInterfaceOverridingJavaPrimitiveNullable", "Int?"),
        ).joinToString("\n")
        val sources = listOf(
            javaBase,
            Source.kotlin("kotlinOverridingJava.kt", subInterfaces)
        )
        return TestInput(
            sources, listOf(
                "BaseJavaInterface",
                "SubInterfaceOverridingJavaOpen",
                "SubInterfaceOverridingJavaEnum",
                "SubInterfaceOverridingJavaFinal",
                "SubInterfaceOverridingJavaPrimitive",
                "SubInterfaceOverridingJavaPrimitiveNullable",
            )
        )
    }

    /**
     * Create test cases where Kotlin overrides a java interface and the type argument includes
     * another generic.
     */
    private fun createKotlinOverridesJavaSubjectsWithGenerics(
        pkg: String
    ): TestInput {
        val javaBase = Source.java(
            "$pkg.BaseGenericJavaInterface",
            """
            package $pkg;
            import java.util.List;
            public interface BaseGenericJavaInterface<T> {
                void receiveT(T t);
                void receiveTList(List<T> t);
                void receiveTOverridden(T t);
                void receiveTListOverridden(List<T> t);
            }
            """
        )

        fun buildInterface(
            name: String,
            typeArg: String
        ) = """
            interface $name: BaseGenericJavaInterface<$typeArg> {
                override fun receiveTOverridden(t:$typeArg): Unit
                override fun receiveTListOverridden(t:List<$typeArg>): Unit
            }
        """.trimIndent()

        val subInterfaces = listOf(
            "package $pkg",
            buildInterface("SubInterfaceOverridingJavaWithGenericR<R>", "List<R>"),
            buildInterface("SubInterfaceOverridingJavaWithGenericOpen", "List<Number>"),
            buildInterface("SubInterfaceOverridingJavaWithGenericEnum", "List<MyEnum>"),
            buildInterface("SubInterfaceOverridingJavaWithGenericFinal", "List<String>"),
            buildInterface("SubInterfaceOverridingJavaWithGenericPrimitive", "List<Number>"),
            buildInterface(
                "SubInterfaceOverridingJavaWithGenericPrimitiveNullable",
                "List<Number?>"
            )
        ).joinToString("\n")
        val sources = listOf(
            javaBase,
            Source.kotlin("kotlinOverridingJavaWithGenerics.kt", subInterfaces)
        )
        return TestInput(
            sources, listOf(
                "SubInterfaceOverridingJavaWithGenericR",
                "SubInterfaceOverridingJavaWithGenericOpen",
                "SubInterfaceOverridingJavaWithGenericEnum",
                "SubInterfaceOverridingJavaWithGenericFinal",
                "SubInterfaceOverridingJavaWithGenericPrimitive",
                "SubInterfaceOverridingJavaWithGenericPrimitiveNullable",
            )
        )
    }

    private fun createMultiLevelInheritanceSubjects(pkg: String): TestInput {
        val base = """
            interface MultiLevelBaseInterface<T> {
                fun receiveReturnT(t : T): T
                suspend fun suspendReceiveReturnT(t : T): T
                fun receiveReturnTList(t : List<T>): List<T>
                suspend fun suspendReceiveReturnTList(t : List<T>): List<T>
                fun receiveReturnTOverriddenInSub(t : T): T
                suspend fun suspendReceiveReturnTOverriddenInSub(t : T): T
            }
            interface MultiLevelSubInterface<R>: MultiLevelBaseInterface<String> {
                fun subReceiveReturnT(r : R): R
                suspend fun subSuspendReceiveReturnT(r : R): R
                override fun receiveReturnTOverriddenInSub(t : String): String
                override suspend fun suspendReceiveReturnTOverriddenInSub(t : String): String
                fun subReceiveReturnROverridden(r : R): R
                suspend fun subSuspendReceiveReturnROverridden(r : R): R
                fun subReceiveReturnRListOverridden(r : List<R>): List<R>
                suspend fun subSuspendReceiveReturnRListOverridden(r : List<R>): List<R>
            }
        """.trimIndent()

        fun buildInterface(
            name: String,
            typeArg: String
        ) = """
            interface $name: MultiLevelSubInterface<$typeArg> {
                override fun subReceiveReturnROverridden(r : $typeArg): $typeArg
                override suspend fun subSuspendReceiveReturnROverridden(r : $typeArg): $typeArg
                override fun subReceiveReturnRListOverridden(r : List<$typeArg>): List<$typeArg>
                override suspend fun subSuspendReceiveReturnRListOverridden(r : List<$typeArg>): List<$typeArg>
            }
        """.trimIndent()

        val subInterfaces = listOf(
            "package $pkg",
            base,
            buildInterface("MultiSubInterfaceOpen", "Number"),
            buildInterface("MultiSubInterfaceEnum", "MyEnum"),
            buildInterface("MultiSubInterfaceFinal", "String"),
            buildInterface("MultiSubInterfacePrimitive", "Int"),
            buildInterface("MultiSubInterfacePrimitiveNullable", "Int?"),
        ).joinToString("\n")

        return TestInput(
            Source.kotlin("MultiLevel.kt", subInterfaces), listOf(
                "MultiSubInterfaceOpen",
                "MultiSubInterfaceEnum",
                "MultiSubInterfaceFinal",
                "MultiSubInterfacePrimitive",
                "MultiSubInterfacePrimitiveNullable",
            )
        )
    }

    private fun createJavaOverridesKotlinSubjects(pkg: String): TestInput {
        val base = Source.kotlin(
            "javaOverridesKotlin.kt",
            """
            package $pkg
            interface BaseKotlinOverriddenByJava<T> {
                fun receiveReturnT(t: T): T
                fun receiveReturnTList(t: List<T>): List<T>
            }
        """.trimIndent()
        )

        fun buildInterface(
            name: String,
            typeArg: String
        ) = Source.java(
            "$pkg.$name",
            """
            package $pkg;
            import java.util.List;
            public interface $name extends BaseKotlinOverriddenByJava<$typeArg> {
            }
        """.trimIndent()
        )
        return TestInput(
            sources = listOf(
                base,
                buildInterface("JavaOverridesKotlinOpen", "Number"),
                buildInterface("JavaOverridesKotlinFinal", "String"),
            ),
            subjects = listOf("JavaOverridesKotlinOpen", "JavaOverridesKotlinFinal")
        )
    }

    /**
     * Create test cases where bounded generics are overridden
     */
    private fun createOverrideWithBoundsSubjects(pkg: String): TestInput {
        val base = """
            interface BaseInterfaceWithCloseableBounds<T : Closeable> {
                var tProp: T
                var tListProp: List<T>
                var definedProp: Number
                var definedPropList: List<Number>
                fun receiveReturnT(t: T): T
                suspend fun suspendReceiveReturnT(t:T):T
                fun receiveReturnTList(t:List<T>): List<T>
                suspend fun suspendReceiveReturnTList(t:List<T>): List<T>
                fun receiveReturnTOverridden(t: T): T
                suspend fun suspendReceiveReturnTOverridden(t:T):T
                fun receiveReturnTListOverridden(t:List<T>): List<T>
                suspend fun suspendReceiveReturnTListOverridden(t:List<T>): List<T>
            }
            interface MyCloseable : Closeable
            open class OpenCloseable(): Closeable {
                override open fun close() { TODO() }
            }
            class FinalCloseable(): Closeable {
                override open fun close() { TODO() }
            }
        """.trimIndent()

        fun buildInterface(
            name: String,
            typeArg: String
        ) = """
            interface $name: BaseInterfaceWithCloseableBounds<$typeArg> {
                override fun receiveReturnTOverridden(t: $typeArg): $typeArg
                override suspend fun suspendReceiveReturnTOverridden(t:$typeArg):$typeArg
                override fun receiveReturnTListOverridden(t:List<$typeArg>): List<$typeArg>
                override suspend fun suspendReceiveReturnTListOverridden(t:List<$typeArg>): List<$typeArg>
            }
        """.trimIndent()

        val code = listOf(
            "package $pkg",
            "import java.io.*",
            base,
            buildInterface("SubInterfaceCloseable<R : Closeable>", "R"),
            buildInterface("SubInterfaceMyCloseable<R : MyCloseable>", "R"),
            buildInterface("SubInterfaceOpenCloseable", "OpenCloseable"),
            buildInterface("SubInterfaceFinalCloseable", "Closeable"),
            buildInterface("SubInterfaceByteArrayInputStream", "ByteArrayInputStream"),
            buildInterface("SubInterfaceFileInputStream", "FileInputStream"),
        ).joinToString("\n")
        val classNames = listOf(
            "BaseInterfaceWithCloseableBounds",
            "SubInterfaceCloseable",
            "SubInterfaceMyCloseable",
            "SubInterfaceOpenCloseable",
            "SubInterfaceFinalCloseable",
            "SubInterfaceByteArrayInputStream",
            "SubInterfaceFileInputStream",
        )
        return TestInput(Source.kotlin("BoundedOverrides.kt", code), classNames)
    }

    /**
     * Create test cases where generics with multiple upper bounds are overridden
     */
    private fun createOverrideWithMultipleBoundsSubjects(pkg: String): TestInput {
        val base = """
            interface BaseInterfaceWithMultipleBounds<T> where T:FirstBound, T:SecondBound {
                var tProp: T
                var tListProp: List<T>
                var definedProp: Number
                var definedPropList: List<Number>
                fun receiveReturnT(t: T): T
                suspend fun suspendReceiveReturnT(t:T):T
                fun receiveReturnTList(t:List<T>): List<T>
                suspend fun suspendReceiveReturnTList(t:List<T>): List<T>
                fun receiveReturnTOverridden(t: T): T
                suspend fun suspendReceiveReturnTOverridden(t:T):T
                fun receiveReturnTListOverridden(t:List<T>): List<T>
                suspend fun suspendReceiveReturnTListOverridden(t:List<T>): List<T>
            }
            interface FirstBound {
                fun foo()
            }
            interface SecondBound {
                fun bar()
            }
            interface MergedBounds : FirstBound, SecondBound
            open class OpenImpl : FirstBound, SecondBound {
                override fun foo() { TODO() }
                override fun bar() { TODO() }
            }
            class FinalImpl : FirstBound, SecondBound {
                override fun foo() { TODO() }
                override fun bar() { TODO() }
            }
        """.trimIndent()

        fun buildInterface(
            name: String,
            typeArg: String
        ) = """
            interface $name: BaseInterfaceWithMultipleBounds<$typeArg> {
                override fun receiveReturnTOverridden(t: $typeArg): $typeArg
                override suspend fun suspendReceiveReturnTOverridden(t:$typeArg):$typeArg
                override fun receiveReturnTListOverridden(t:List<$typeArg>): List<$typeArg>
                override suspend fun suspendReceiveReturnTListOverridden(t:List<$typeArg>): List<$typeArg>
            }
        """.trimIndent()

        val code = listOf(
            "package $pkg",
            "import java.io.*",
            base,
            buildInterface("SubInterfaceMergedBounds<R : MergedBounds>", "R"),
            buildInterface("SubInterfaceOpenImpl", "OpenImpl"),
            buildInterface("SubInterfaceFinalImpl", "FinalImpl"),
        ).joinToString("\n")
        val classNames = listOf(
            "BaseInterfaceWithMultipleBounds",
            "SubInterfaceMergedBounds",
            "SubInterfaceOpenImpl",
            "SubInterfaceFinalImpl"
        )
        return TestInput(Source.kotlin("MultipleBoundsOverrides.kt", code), classNames)
    }

    private data class TestInput(
        val sources: List<Source>,
        val subjects: List<String>
    ) {
        constructor(source: Source, subjects: List<String>) : this(listOf(source), subjects)

        operator fun plus(other: TestInput) = TestInput(
            sources = sources + other.sources,
            subjects = subjects + other.subjects
        )
    }
}
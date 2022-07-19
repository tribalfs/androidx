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

import androidx.room.compiler.processing.util.Source
import androidx.room.compiler.processing.util.XTestInvocation
import androidx.room.compiler.processing.util.compileFiles
import androidx.room.compiler.processing.util.getAllFieldNames
import androidx.room.compiler.processing.util.getField
import androidx.room.compiler.processing.util.getMethodByJvmName
import androidx.room.compiler.processing.util.runProcessorTest
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeVariableName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4

@RunWith(JUnit4::class)
class XTypeElementTest {
    @Test
    fun qualifiedNames() {
        val src1 = Source.kotlin(
            "Foo.kt",
            """
            class TopLevel
            """.trimIndent()
        )
        val src2 = Source.kotlin(
            "Bar.kt",
            """
            package foo.bar
            class InFooBar
            """.trimIndent()
        )
        val src3 = Source.java(
            "foo.bar.Outer",
            """
            package foo.bar;
            public class Outer {
                public static class Nested {
                }
            }
            """
        )
        runProcessorTest(
            sources = listOf(src1, src2, src3)
        ) { invocation ->
            invocation.processingEnv.requireTypeElement("TopLevel").let {
                assertThat(it.packageName).isEqualTo("")
                assertThat(it.name).isEqualTo("TopLevel")
                assertThat(it.qualifiedName).isEqualTo("TopLevel")
                assertThat(it.className).isEqualTo(ClassName.get("", "TopLevel"))
            }
            invocation.processingEnv.requireTypeElement("foo.bar.InFooBar").let {
                assertThat(it.packageName).isEqualTo("foo.bar")
                assertThat(it.name).isEqualTo("InFooBar")
                assertThat(it.qualifiedName).isEqualTo("foo.bar.InFooBar")
                assertThat(it.className).isEqualTo(ClassName.get("foo.bar", "InFooBar"))
            }
            invocation.processingEnv.requireTypeElement("foo.bar.Outer").let {
                assertThat(it.packageName).isEqualTo("foo.bar")
                assertThat(it.name).isEqualTo("Outer")
                assertThat(it.qualifiedName).isEqualTo("foo.bar.Outer")
                assertThat(it.className).isEqualTo(
                    ClassName.get("foo.bar", "Outer")
                )
            }
            invocation.processingEnv.requireTypeElement("foo.bar.Outer.Nested").let {
                assertThat(it.packageName).isEqualTo("foo.bar")
                assertThat(it.name).isEqualTo("Nested")
                assertThat(it.qualifiedName).isEqualTo("foo.bar.Outer.Nested")
                assertThat(it.className).isEqualTo(
                    ClassName.get("foo.bar", "Outer", "Nested")
                )
            }
            if (invocation.isKsp) {
                // these are KSP specific tests, typenames are tested elsewhere
                invocation.processingEnv.requireTypeElement("java.lang.Integer").let {
                    // always return kotlin types, this is what compiler does
                    assertThat(it.packageName).isEqualTo("kotlin")
                    assertThat(it.name).isEqualTo("Int")
                    assertThat(it.qualifiedName).isEqualTo("kotlin.Int")
                }
                invocation.processingEnv.requireTypeElement("kotlin.Int").let {
                    assertThat(it.packageName).isEqualTo("kotlin")
                    assertThat(it.name).isEqualTo("Int")
                    assertThat(it.qualifiedName).isEqualTo("kotlin.Int")
                }
            }
        }
    }

    @Test
    fun typeAndSuperType() {
        val src = Source.kotlin(
            "foo.kt",
            """
            package foo.bar;
            class Baz : MyInterface, AbstractClass() {
            }
            abstract class AbstractClass {}
            interface MyInterface {}
            interface AnotherInterface : MyInterface {}
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            invocation.processingEnv.requireTypeElement("foo.bar.Baz").let {
                assertThat(it.superClass).isEqualTo(
                    invocation.processingEnv.requireType("foo.bar.AbstractClass")
                )
                assertThat(it.superTypes).containsExactly(
                    invocation.processingEnv.requireType("foo.bar.AbstractClass"),
                    invocation.processingEnv.requireType("foo.bar.MyInterface")
                )
                assertThat(it.type).isEqualTo(
                    invocation.processingEnv.requireType("foo.bar.Baz")
                )
                assertThat(it.isInterface()).isFalse()
                assertThat(it.isKotlinObject()).isFalse()
                assertThat(it.isAbstract()).isFalse()
            }
            invocation.processingEnv.requireTypeElement("foo.bar.AbstractClass").let {
                assertThat(it.superClass).isEqualTo(
                    invocation.processingEnv.requireType(TypeName.OBJECT)
                )
                assertThat(it.superTypes).containsExactly(
                    invocation.processingEnv.requireType(TypeName.OBJECT)
                )
                assertThat(it.isAbstract()).isTrue()
                assertThat(it.isInterface()).isFalse()
                assertThat(it.type).isEqualTo(
                    invocation.processingEnv.requireType("foo.bar.AbstractClass")
                )
            }
            invocation.processingEnv.requireTypeElement("foo.bar.MyInterface").let {
                assertThat(it.superClass).isNull()
                assertThat(it.superTypes).containsExactly(
                    invocation.processingEnv.requireType(TypeName.OBJECT)
                )
                assertThat(it.isInterface()).isTrue()
                assertThat(it.type).isEqualTo(
                    invocation.processingEnv.requireType("foo.bar.MyInterface")
                )
            }
            invocation.processingEnv.requireTypeElement("foo.bar.AnotherInterface").let {
                assertThat(it.superClass).isNull()
                assertThat(it.superTypes).containsExactly(
                    invocation.processingEnv.requireType("foo.bar.MyInterface")
                )
                assertThat(it.isInterface()).isTrue()
                assertThat(it.type).isEqualTo(
                    invocation.processingEnv.requireType("foo.bar.AnotherInterface")
                )
            }
        }
    }

    @Test
    fun superInterfaces() {
        val src = Source.kotlin(
            "foo.kt",
            """
            package foo.bar;
            class Baz : MyInterface<String>, AbstractClass() {
            }
            abstract class AbstractClass {}
            interface MyInterface<E> {}
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            invocation.processingEnv.requireTypeElement("foo.bar.Baz").let {
                assertThat(it.superInterfaces).hasSize(1)
                val superInterface = it.superInterfaces.first { type ->
                    type.rawType.toString() == "foo.bar.MyInterface"
                }
                assertThat(superInterface.typeArguments).hasSize(1)
                assertThat(superInterface.typeArguments[0].typeName)
                    .isEqualTo(ClassName.get("java.lang", "String"))
            }
        }
    }

    @Test
    fun nestedClassName() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            package foo.bar;
            class Outer {
                class Inner
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            invocation.processingEnv.requireTypeElement("foo.bar.Outer").let {
                assertThat(it.className).isEqualTo(ClassName.get("foo.bar", "Outer"))
                assertThat(it.isNested()).isFalse()
                assertThat(it.enclosingTypeElement).isNull()
            }
            invocation.processingEnv.requireTypeElement("foo.bar.Outer.Inner").let {
                assertThat(it.className).isEqualTo(ClassName.get("foo.bar", "Outer", "Inner"))
                assertThat(it.packageName).isEqualTo("foo.bar")
                assertThat(it.name).isEqualTo("Inner")
                assertThat(it.isNested()).isTrue()
                assertThat(it.enclosingTypeElement).isEqualTo(
                    invocation.processingEnv.requireTypeElement("foo.bar.Outer")
                )
            }
        }
    }

    @Test
    fun modifiers() {
        val kotlinSrc = Source.kotlin(
            "Foo.kt",
            """
            open class OpenClass
            abstract class AbstractClass
            object MyObject
            interface MyInterface
            class Final
            private class PrivateClass
            class OuterKotlinClass {
                inner class InnerKotlinClass
                class NestedKotlinClass
            }
            annotation class KotlinAnnotation
            data class DataClass(val foo: Int)
            inline class InlineClass(val foo: Int)
            fun interface FunInterface {
               fun foo()
            }
            """.trimIndent()
        )
        val javaSrc = Source.java(
            "OuterJavaClass",
            """
            public class OuterJavaClass {
                public class InnerJavaClass {}
                public static class NestedJavaClass {}
            }
            """.trimIndent()
        )
        val javaAnnotationSrc = Source.java(
            "JavaAnnotation",
            """
            public @interface JavaAnnotation {
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(kotlinSrc, javaSrc, javaAnnotationSrc)
        ) { invocation ->
            fun getModifiers(element: XTypeElement): Set<String> {
                val result = mutableSetOf<String>()
                if (element.isAbstract()) result.add("abstract")
                if (element.isFinal()) result.add("final")
                if (element.isPrivate()) result.add("private")
                if (element.isProtected()) result.add("protected")
                if (element.isPublic()) result.add("public")
                if (element.isKotlinObject()) result.add("object")
                if (element.isCompanionObject()) result.add("companion")
                if (element.isFunctionalInterface()) result.add("fun")
                if (element.isClass()) result.add("class")
                if (element.isDataClass()) result.add("data")
                if (element.isValueClass()) result.add("value")
                if (element.isExpect()) result.add("expect")
                if (element.isInterface()) result.add("interface")
                if (element.isStatic()) result.add("static")
                if (element.isAnnotationClass()) result.add("annotation")
                return result
            }

            fun getModifiers(qName: String): Set<String> = getModifiers(
                invocation.processingEnv
                    .requireTypeElement(qName)
            )

            assertThat(getModifiers("OpenClass"))
                .containsExactly("public", "class")
            assertThat(getModifiers("AbstractClass"))
                .containsExactly("abstract", "public", "class")
            assertThat(getModifiers("MyObject"))
                .containsExactly("final", "public", "object")
            assertThat(getModifiers("MyInterface"))
                .containsExactly("abstract", "interface", "public")
            assertThat(getModifiers("Final"))
                .containsExactly("final", "public", "class")
            assertThat(getModifiers("PrivateClass"))
                .containsExactlyElementsIn(
                    if (invocation.isKsp) {
                        listOf("private", "final", "class")
                    } else {
                        // java does not support top level private classes.
                        listOf("final", "class")
                    }
                )
            assertThat(getModifiers("OuterKotlinClass.InnerKotlinClass"))
                .containsExactly("final", "public", "class")
            assertThat(getModifiers("OuterKotlinClass.NestedKotlinClass"))
                .containsExactly("final", "public", "static", "class")
            assertThat(getModifiers("OuterJavaClass.InnerJavaClass"))
                .containsExactly("public", "class")
            assertThat(getModifiers("OuterJavaClass.NestedJavaClass"))
                .containsExactly("public", "static", "class")
            assertThat(getModifiers("JavaAnnotation"))
                .containsExactly("abstract", "public", "annotation")
            assertThat(getModifiers("KotlinAnnotation")).apply {
                // KSP vs KAPT metadata have a difference in final vs abstract modifiers
                // for annotation types.
                if (invocation.isKsp) {
                    containsExactly("final", "public", "annotation")
                } else {
                    containsExactly("abstract", "public", "annotation")
                }
            }
            assertThat(getModifiers("DataClass"))
                .containsExactly("public", "final", "class", "data")
            assertThat(getModifiers("InlineClass"))
                .containsExactly("public", "final", "class", "value")
            assertThat(getModifiers("FunInterface"))
                .containsExactly("public", "abstract", "interface", "fun")
        }
    }

    @Test
    fun kindName() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            class MyClass
            interface MyInterface
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            invocation.processingEnv.requireTypeElement("MyClass").let {
                assertThat(it.kindName()).isEqualTo("class")
            }
            invocation.processingEnv.requireTypeElement("MyInterface").let {
                assertThat(it.kindName()).isEqualTo("interface")
            }
        }
    }

    @Test
    fun fieldBasic() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            open class BaseClass<T>(val genericProp : T) {
                fun baseMethod(input: T) {}
            }
            class SubClass(x : Int) : BaseClass<Int>(x) {
                val subClassProp : String = "abc"
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val baseClass = invocation.processingEnv.requireTypeElement("BaseClass")
            assertThat(baseClass.getAllFieldNames()).containsExactly("genericProp")
            assertThat(baseClass.getDeclaredFields().map { it.name })
                .containsExactly("genericProp")
            val subClass = invocation.processingEnv.requireTypeElement("SubClass")
            assertThat(subClass.getAllFieldNames()).containsExactly("genericProp", "subClassProp")
            assertThat(subClass.getDeclaredFields().map { it.name })
                .containsExactly("subClassProp")

            val baseMethod = baseClass.getMethodByJvmName("baseMethod")
            baseMethod.asMemberOf(subClass.type).let { methodType ->
                val genericArg = methodType.parameterTypes.first()
                assertThat(genericArg.typeName).isEqualTo(TypeName.INT.box())
            }

            baseClass.getField("genericProp").let { field ->
                assertThat(field.type.typeName).isEqualTo(TypeVariableName.get("T"))
            }

            subClass.getField("genericProp").let { field ->
                // this is tricky because even though it is non-null it, it should still be boxed
                assertThat(field.asMemberOf(subClass.type).typeName).isEqualTo(TypeName.INT.box())
            }
        }
    }

    @Test
    fun fieldsOverride() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            open class BaseClass(
                open val value : List<Int>
            )
            class SubClass(
                override val value : MutableList<Int>
            ) : BaseClass(value)
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val baseClass = invocation.processingEnv.requireTypeElement("BaseClass")
            assertThat(baseClass.getAllFieldNames()).containsExactly("value")
            val subClass = invocation.processingEnv.requireTypeElement("SubClass")
            assertThat(subClass.getAllFieldNames()).containsExactly("value")
            assertThat(
                baseClass.getField("value").type.typeName
            ).isEqualTo(
                ParameterizedTypeName.get(List::class.java, Integer::class.java)
            )
            assertThat(
                subClass.getField("value").type.typeName
            ).isEqualTo(
                ParameterizedTypeName.get(List::class.java, Integer::class.java)
            )
        }
    }

    @Test
    fun fieldsMethodsWithoutBacking() {
        fun buildSrc(pkg: String) = Source.kotlin(
            "Foo.kt",
            """
            package $pkg;
            class Subject {
                val realField: String = ""
                    get() = field
                val noBackingVal: String
                    get() = ""
                var noBackingVar: String
                    get() = ""
                    set(value) {}

                companion object {
                    @JvmStatic
                    val staticRealField: String = ""
                    get() = field
                    @JvmStatic
                    val staticNoBackingVal: String
                        get() = ""
                    @JvmStatic
                    var staticNoBackingVar: String
                        get() = ""
                        set(value) {}
                }
            }
            """.trimIndent()
        )
        val lib = compileFiles(listOf(buildSrc("lib")))
        runProcessorTest(
            sources = listOf(buildSrc("main")),
            classpath = lib
        ) { invocation ->
            listOf("lib", "main").forEach { pkg ->
                val subject = invocation.processingEnv.requireTypeElement("$pkg.Subject")
                val declaredFields = subject.getDeclaredFields().map { it.name } -
                    listOf("Companion") // skip Companion, KAPT generates it
                val expectedFields = listOf("realField", "staticRealField")
                assertWithMessage(subject.qualifiedName)
                    .that(declaredFields)
                    .containsExactlyElementsIn(expectedFields)
                val allFields = subject.getAllFieldsIncludingPrivateSupers().map { it.name } -
                    listOf("Companion") // skip Companion, KAPT generates it
                assertWithMessage(subject.qualifiedName)
                    .that(allFields.toList())
                    .containsExactlyElementsIn(expectedFields)
                val methodNames = subject.getDeclaredMethods().map { it.jvmName }
                assertWithMessage(subject.qualifiedName)
                    .that(methodNames)
                    .containsAtLeast("getNoBackingVal", "getNoBackingVar", "setNoBackingVar")
                assertWithMessage(subject.qualifiedName)
                    .that(methodNames)
                    .doesNotContain("setNoBackingVal")
            }
        }
    }

    @Test
    fun abstractFields() {
        fun buildSource(pkg: String) = Source.kotlin(
            "Foo.kt",
            """
            package $pkg;
            abstract class Subject {
                val value: String = ""
                abstract val abstractValue: String
                companion object {
                    var realCompanion: String = ""
                    @JvmStatic
                    var jvmStatic: String = ""
                }
            }
            """.trimIndent()
        )

        val lib = compileFiles(listOf(buildSource("lib")))
        runProcessorTest(
            sources = listOf(buildSource("main")),
            classpath = lib
        ) { invocation ->
            listOf("lib", "main").forEach { pkg ->
                val subject = invocation.processingEnv.requireTypeElement("$pkg.Subject")
                val declaredFields = subject.getDeclaredFields().map { it.name } -
                    listOf("Companion")
                val expectedFields = listOf("value", "realCompanion", "jvmStatic")
                assertWithMessage(subject.qualifiedName)
                    .that(declaredFields)
                    .containsExactlyElementsIn(expectedFields)
            }
        }
    }

    @Test
    fun lateinitFields() {
        fun buildSource(pkg: String) = Source.kotlin(
            "Foo.kt",
            """
            package $pkg
            class Subject {
                lateinit var x:String
                var y:String = "abc"
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(buildSource("app")),
            classpath = compileFiles(listOf(buildSource("lib")))
        ) { invocation ->
            listOf("app", "lib").forEach { pkg ->
                val subject = invocation.processingEnv.requireTypeElement("$pkg.Subject")
                assertWithMessage(subject.fallbackLocationText)
                    .that(subject.getDeclaredFields().map { it.name })
                    .containsExactly(
                        "x", "y"
                    )
                assertWithMessage(subject.fallbackLocationText)
                    .that(subject.getDeclaredMethods().map { it.jvmName })
                    .containsExactly(
                        "getX", "setX", "getY", "setY"
                    )
            }
        }
    }

    @Test
    fun fieldsInInterfaces() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            interface MyInterface {
                var x:Int
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val element = invocation.processingEnv.requireTypeElement("MyInterface")
            assertThat(element.getAllFieldsIncludingPrivateSupers().toList()).isEmpty()
            element.getMethodByJvmName("getX").let {
                assertThat(it.isAbstract()).isTrue()
            }
            element.getMethodByJvmName("setX").let {
                assertThat(it.isAbstract()).isTrue()
            }
        }
    }

    @Test
    fun fieldsInAbstractClass() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            abstract class MyAbstractClass {
                @JvmField
                var jvmVar: Int = 0
                abstract var abstractVar: Int
                var nonAbstractVar: Int = 0
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val element = invocation.processingEnv.requireTypeElement("MyAbstractClass")
            assertThat(
                element.getAllFieldNames()
            ).containsExactly(
                "nonAbstractVar", "jvmVar"
            )
            assertThat(
                element.getDeclaredMethods().map { it.jvmName }
            ).containsExactly(
                "getAbstractVar", "setAbstractVar",
                "getNonAbstractVar", "setNonAbstractVar"
            )
            element.getMethodByJvmName("getAbstractVar").let {
                assertThat(it.isAbstract()).isTrue()
            }
            element.getMethodByJvmName("setAbstractVar").let {
                assertThat(it.isAbstract()).isTrue()
            }

            element.getMethodByJvmName("getNonAbstractVar").let {
                assertThat(it.isAbstract()).isFalse()
            }
            element.getMethodByJvmName("setNonAbstractVar").let {
                assertThat(it.isAbstract()).isFalse()
            }
        }
    }

    @Test
    fun propertyGettersSetters() {
        val dependencyJavaSource = Source.java(
            "DependencyJavaSubject.java",
            """
            class DependencyJavaSubject {
                int myField;
                private int mutable;
                int immutable;
                int getMutable() {return 3;}
                void setMutable(int x) {}
                int getImmutable() {return 3;}
            }
            """.trimIndent()
        )
        val dependencyKotlinSource = Source.kotlin(
            "DependencyKotlinSubject.kt",
            """
            class DependencyKotlinSubject {
                private val myField = 0
                var mutable: Int = 0
                val immutable:Int = 0
            }
            """.trimIndent()
        )
        val dependency = compileFiles(listOf(dependencyJavaSource, dependencyKotlinSource))
        val javaSource = Source.java(
            "JavaSubject.java",
            """
            class JavaSubject {
                int myField;
                private int mutable;
                int immutable;
                int getMutable() {return 3;}
                void setMutable(int x) {}
                int getImmutable() {return 3;}
            }
            """.trimIndent()
        )
        val kotlinSource = Source.kotlin(
            "KotlinSubject.kt",
            """
            class KotlinSubject {
                private val myField = 0
                var mutable: Int = 0
                val immutable:Int = 0
            }
            """.trimIndent()
        )
        runProcessorTest(
            listOf(javaSource, kotlinSource),
            classpath = dependency
        ) { invocation ->
            listOf(
                "JavaSubject", "DependencyJavaSubject",
                "KotlinSubject", "DependencyKotlinSubject"
            ).map {
                invocation.processingEnv.requireTypeElement(it)
            }.forEach { subject ->
                assertWithMessage(subject.qualifiedName)
                    .that(
                        subject.getDeclaredMethods().map {
                            it.jvmName
                        }
                    ).containsExactly(
                        "getMutable", "setMutable", "getImmutable"
                    )
            }
        }
    }

    @Test
    fun declaredAndInstanceMethods() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            open class Base(x:Int) {
                open fun baseFun(): Int = TODO()
                suspend fun suspendFun(): Int = TODO()
                private fun privateBaseFun(): Int = TODO()
                companion object {
                    @JvmStatic
                    fun staticBaseFun(): Int = TODO()
                    fun companionMethod(): Int = TODO()
                }
            }
            open class SubClass : Base {
                constructor(y:Int): super(y) {
                }
                constructor(x:Int, y:Int): super(y) {
                }
                override fun baseFun(): Int = TODO()
                fun subFun(): Int = TODO()
                private fun privateSubFun(): Int = TODO()
                companion object {
                    @JvmStatic
                    fun staticFun(): Int = TODO()
                }
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val base = invocation.processingEnv.requireTypeElement("Base")
            val objectMethodNames = invocation.objectMethodNames()
            assertThat(base.getDeclaredMethods().jvmNames()).containsExactly(
                "baseFun", "suspendFun", "privateBaseFun", "staticBaseFun"
            )

            val sub = invocation.processingEnv.requireTypeElement("SubClass")
            assertThat(sub.getDeclaredMethods().jvmNames()).containsExactly(
                "baseFun", "subFun", "privateSubFun", "staticFun"
            )
            assertThat(
                sub.getAllNonPrivateInstanceMethods().jvmNames() - objectMethodNames
            ).containsExactly(
                "baseFun", "suspendFun", "subFun"
            )
        }
    }

    @Test
    fun diamondOverride() {
        fun buildSrc(pkg: String) = Source.kotlin(
            "Foo.kt",
            """
            package $pkg;
            interface Parent<T> {
                fun parent(t: T)
            }

            interface Child1<T> : Parent<T> {
                fun child1(t: T)
            }

            interface Child2<T> : Parent<T> {
                fun child2(t: T)
            }

            abstract class Subject1 : Child1<String>, Child2<String>, Parent<String>
            abstract class Subject2 : Child1<String>, Parent<String>
            abstract class Subject3 : Child1<String>, Parent<String> {
                abstract override fun parent(t: String)
            }
            """.trimIndent()
        )

        runProcessorTest(
            sources = listOf(buildSrc("app")),
            classpath = compileFiles(listOf(buildSrc("lib")))
        ) { invocation ->
            listOf("lib", "app").forEach { pkg ->
                invocation.processingEnv.requireTypeElement("$pkg.Subject1").let { subject ->
                    assertWithMessage(subject.qualifiedName).that(
                        invocation.nonObjectMethodSignatures(subject)
                    ).containsExactly(
                        "child1(java.lang.String):void",
                        "child2(java.lang.String):void",
                        "parent(java.lang.String):void",
                    )
                }
                invocation.processingEnv.requireTypeElement("$pkg.Subject2").let { subject ->
                    assertWithMessage(subject.qualifiedName).that(
                        invocation.nonObjectMethodSignatures(subject)
                    ).containsExactly(
                        "child1(java.lang.String):void",
                        "parent(java.lang.String):void",
                    )
                }
                invocation.processingEnv.requireTypeElement("$pkg.Subject3").let { subject ->
                    assertWithMessage(subject.qualifiedName).that(
                        invocation.nonObjectMethodSignatures(subject)
                    ).containsExactly(
                        "child1(java.lang.String):void",
                        "parent(java.lang.String):void",
                    )
                }
            }
        }
    }

    @Test
    fun suspendOverride() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            interface Base<T> {
                suspend fun get(): T
                suspend fun getAll(): List<T>
                suspend fun putAll(input: List<T>)
                suspend fun getAllWithDefault(): List<T>
            }

            interface DerivedInterface : Base<String> {
                override suspend fun get(): String
                override suspend fun getAll(): List<String>
                override suspend fun putAll(input: List<String>)
                override suspend fun getAllWithDefault(): List<String> {
                    return emptyList()
                }
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val base = invocation.processingEnv.requireTypeElement("DerivedInterface")
            val methodNames = base.getAllMethods().toList().jvmNames()
            assertThat(methodNames).containsExactly(
                "get", "getAll", "putAll", "getAllWithDefault"
            )
        }
    }

    @Test
    fun suspendOverride_abstractClass() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            abstract class Base<T> {
                abstract suspend fun get(): T
                abstract suspend fun getAll(): List<T>
                abstract suspend fun putAll(input: List<T>)
            }

            abstract class DerivedClass : Base<Int>() {
                abstract override suspend fun get(): Int
                abstract override suspend fun getAll(): List<Int>
                override suspend fun putAll(input: List<Int>) {
                }
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val base = invocation.processingEnv.requireTypeElement("DerivedClass")
            val methodNamesCount =
                base.getAllMethods().toList().jvmNames().groupingBy { it }.eachCount()
            assertThat(methodNamesCount["get"]).isEqualTo(1)
            assertThat(methodNamesCount["getAll"]).isEqualTo(1)
            assertThat(methodNamesCount["putAll"]).isEqualTo(1)
        }
    }

    @Test
    fun overrideMethodWithCovariantReturnType() {
        val src = Source.kotlin(
            "ParentWithExplicitOverride.kt",
            """
            interface ParentWithExplicitOverride: ChildInterface, Child {
                override fun child(): Child
            }

            interface ParentWithoutExplicitOverride: ChildInterface, Child

            interface Child: ChildInterface {
                override fun child(): Child
            }

            interface ChildInterface {
                fun child(): ChildInterface
            }
            """.trimIndent()
        )

        runProcessorTest(sources = listOf(src)) { invocation ->
            invocation.processingEnv.requireTypeElement(
                "ParentWithExplicitOverride"
            ).let { parent ->
                assertWithMessage(parent.qualifiedName).that(
                    invocation.nonObjectMethodSignatures(parent)
                ).containsExactly(
                    "child():Child"
                )
            }

            invocation.processingEnv.requireTypeElement(
                "ParentWithoutExplicitOverride"
            ).let { parent ->
                assertWithMessage(parent.qualifiedName).that(
                    invocation.nonObjectMethodSignatures(parent)
                ).containsExactly(
                    "child():Child"
                )
            }
        }
    }

    @Test
    fun allMethodsFiltersInAccessibleMethods() {
        val srcs = listOf(
            Source.java(
        "foo.Foo",
                """
                package foo;
                public interface Foo {
                    void foo_Public();
                }
                """.trimIndent()
            ),
            Source.java(
                "foo.parent.FooParent",
                """
                package foo.parent;
                public abstract class FooParent implements foo.Foo {
                    public void fooParent_Public() {}
                    protected void fooParent_Protected() {}
                    private void fooParent_Private() {}
                    void fooParent_PackagePrivate() {}
                }
                """.trimIndent()
            ),
            Source.java(
                "foo.child.FooChild",
                """
                package foo.child;
                public abstract class FooChild extends foo.parent.FooParent {
                    public void fooChild_Public() {}
                    protected void fooChild_Protected() {}
                    private void fooChild_Private() {}
                    void fooChild_PackagePrivate() {}
                }
                """.trimIndent()
            ),
        )
        runProcessorTest(sources = srcs) { invocation ->
            invocation.processingEnv.requireTypeElement("foo.child.FooChild")
                .let { fooChild ->
                    assertWithMessage(fooChild.qualifiedName).that(
                        invocation.nonObjectMethodSignatures(fooChild)
                    ).containsExactly(
                        "foo_Public():void",
                        "fooParent_Public():void",
                        "fooParent_Protected():void",
                        "fooChild_Public():void",
                        "fooChild_Protected():void",
                        "fooChild_Private():void",
                        "fooChild_PackagePrivate():void"
                    )
                }
        }
    }

    @Test
    fun allMethods() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            open class Base(x:Int) {
                constructor(x:Int, y:Int): this(x) {
                }
                fun baseMethod(): Int = TODO()
                open fun overriddenMethod(): Int = TODO()
                private fun privateBaseMethod(): Int = TODO()
                companion object {
                    @JvmStatic
                    private fun privateBaseCompanionMethod(): Int = TODO()
                    @JvmStatic
                    fun baseCompanionMethod(): Int = TODO()
                }
            }
            interface MyInterface {
                fun interfaceMethod(): Int = TODO()
            }
            class SubClass : Base, MyInterface {
                constructor(x:Int): super(x) {
                }
                constructor(x:Int, y:Int): super(y) {
                }
                fun subMethod(): Int = TODO()
                fun privateSubMethod(): Int = TODO()
                override fun overriddenMethod(): Int = TODO()
                override fun interfaceMethod(): Int = TODO()
                companion object {
                    fun dontSeeThisOne(): Int = TODO()
                    @JvmStatic
                    fun subCompanionMethod(): Int = TODO()
                }
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val objectMethodNames = invocation.objectMethodNames()
            val klass = invocation.processingEnv.requireTypeElement("SubClass")
            assertThat(
                klass.getAllMethods().jvmNames() - objectMethodNames
            ).containsExactly(
                "baseMethod", "overriddenMethod", "baseCompanionMethod",
                "interfaceMethod", "subMethod", "privateSubMethod", "subCompanionMethod"
            )
        }
    }

    /**
     * When JvmNames is used along with a suppression over the error, the behavior becomes
     * complicated. Normally, JvmName annotation is not allowed in overrides and open methods, yet
     * developers can still use it by putting a suppression over it. The compiler will generate a
     * delegating method in these cases in the .class file, yet in KSP, we don't really see that
     * method (also shouldn't ideally).
     *
     * This test is here to acknowledge that the behavior is inconsistent yet working as intended
     * from XProcessing's perspective.
     *
     * Also see: https://youtrack.jetbrains.com/issue/KT-50782 as a sign why this suppression is
     * not worth supporting :).
     */
    @Test
    fun allMethods_withJvmNames() {
        fun buildSource(pkg: String) = listOf(
            Source.kotlin(
                "Foo.kt",
                """
                package $pkg
                interface Interface {
                    fun f1()
                    @JvmName("notF2")
                    @Suppress("INAPPLICABLE_JVM_NAME")
                    fun f2()
                }
                abstract class Subject : Interface {
                    @JvmName("notF1")
                    @Suppress("INAPPLICABLE_JVM_NAME")
                    override fun f1() {
                    }
                }
            """.trimIndent()
            )
        )

        runProcessorTest(
            sources = buildSource("app"),
            classpath = compileFiles(buildSource("lib"))
        ) { invocation ->
            listOf("app", "lib").forEach {
                val appSubject = invocation.processingEnv.requireTypeElement("$it.Subject")
                val methodNames = appSubject.getAllMethods().map { it.name }.toList()
                val methodJvmNames = appSubject.getAllMethods().map { it.jvmName }.toList()
                val objectMethodNames = invocation.objectMethodNames()
                if (invocation.isKsp) {
                    assertThat(methodNames - objectMethodNames).containsExactly(
                        "f1", "f2"
                    )
                    assertThat(methodJvmNames - objectMethodNames).containsExactly(
                        "notF1", "notF2"
                    )
                } else {
                    assertThat(methodNames - objectMethodNames).containsExactly(
                        "f1", "f1", "f2"
                    )
                    assertThat(methodJvmNames - objectMethodNames).containsExactly(
                        "f1", "notF1", "notF2"
                    )
                }
            }
        }
    }

    @Test
    fun gettersSetters() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            open class JustGetter(val x:Int) {
                private val invisible:Int = TODO()
                private var invisibleMutable:Int = TODO()
            }
            class GetterSetter(var y:Int) : JustGetter(y) {
                private val subInvisible:Int = TODO()
                private var subInvisibleMutable:Int = TODO()
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val objectMethodNames = invocation.objectMethodNames()
            invocation.processingEnv.requireTypeElement("JustGetter").let { base ->
                assertThat(base.getDeclaredMethods().jvmNames()).containsExactly(
                    "getX"
                )
                assertThat(base.getAllMethods().jvmNames() - objectMethodNames).containsExactly(
                    "getX"
                )
                assertThat(
                    base.getAllNonPrivateInstanceMethods().jvmNames() - objectMethodNames
                ).containsExactly(
                    "getX"
                )
            }
            invocation.processingEnv.requireTypeElement("GetterSetter").let { sub ->
                assertThat(sub.getDeclaredMethods().jvmNames()).containsExactly(
                    "getY", "setY"
                )
                assertThat(sub.getAllMethods().jvmNames() - objectMethodNames).containsExactly(
                    "getX", "getY", "setY"
                )
                assertThat(
                    sub.getAllNonPrivateInstanceMethods().jvmNames() - objectMethodNames
                ).containsExactly(
                    "getX", "getY", "setY"
                )
            }
        }
    }

    @Test
    fun companion() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            open class CompanionSubject {
                companion object {
                    @JvmStatic
                    var mutableStatic: String = "a"
                    @JvmStatic
                    val immutableStatic: String = "bar"
                    val companionProp: Int = 3
                    @get:JvmStatic
                    var companionProp_getterJvmStatic:Int =3
                    @set:JvmStatic
                    var companionProp_setterJvmStatic:Int =3

                    fun companionMethod() {
                    }

                    @JvmStatic
                    fun companionMethodWithJvmStatic() {}
                }
            }
            class SubClass : CompanionSubject()
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val objectMethodNames = invocation.processingEnv.requireTypeElement(
                Any::class
            ).getAllMethods().jvmNames()
            val subject = invocation.processingEnv.requireTypeElement("CompanionSubject")
            assertThat(subject.getAllFieldNames() - "Companion").containsExactly(
                "mutableStatic", "immutableStatic", "companionProp",
                "companionProp_getterJvmStatic", "companionProp_setterJvmStatic"
            )
            val expectedMethodNames = listOf(
                "getMutableStatic", "setMutableStatic", "getImmutableStatic",
                "getCompanionProp_getterJvmStatic", "setCompanionProp_setterJvmStatic",
                "companionMethodWithJvmStatic"
            )
            assertThat(
                subject.getDeclaredMethods().jvmNames()
            ).containsExactlyElementsIn(
                expectedMethodNames
            )
            assertThat(
                subject.getAllMethods().jvmNames() - objectMethodNames
            ).containsExactlyElementsIn(
                expectedMethodNames
            )
            assertThat(
                subject.getAllNonPrivateInstanceMethods().jvmNames() - objectMethodNames
            ).isEmpty()
            val subClass = invocation.processingEnv.requireTypeElement("SubClass")
            assertThat(subClass.getDeclaredMethods()).isEmpty()
            assertThat(
                subClass.getAllMethods().jvmNames() - objectMethodNames
            ).containsExactlyElementsIn(
                expectedMethodNames
            )

            // make sure everything coming from companion is marked as static
            subject.getDeclaredFields().forEach {
                assertWithMessage(it.name).that(it.isStatic()).isTrue()
            }
            subject.getDeclaredMethods().forEach {
                assertWithMessage(it.jvmName).that(it.isStatic()).isTrue()
            }

            // make sure asMemberOf works fine for statics
            val subClassType = subClass.type
            subject.getDeclaredFields().forEach {
                try {
                    it.asMemberOf(subClassType)
                } catch (th: Throwable) {
                    throw AssertionError("Couldn't run asMemberOf for ${it.name}")
                }
            }
            subject.getDeclaredMethods().forEach {
                try {
                    it.asMemberOf(subClassType)
                } catch (th: Throwable) {
                    throw AssertionError("Couldn't run asMemberOf for ${it.jvmName}")
                }
            }
        }
    }

    @Test
    fun gettersSetters_interface() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            interface JustGetter {
                val x:Int
            }
            interface GetterSetter : JustGetter {
                var y:Int
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            invocation.processingEnv.requireTypeElement("JustGetter").let { base ->
                assertThat(base.getDeclaredMethods().jvmNames()).containsExactly(
                    "getX"
                )
                assertThat(base.getAllMethods().jvmNames()).containsExactly(
                    "getX"
                )
                assertThat(base.getAllNonPrivateInstanceMethods().jvmNames()).containsExactly(
                    "getX"
                )
            }
            invocation.processingEnv.requireTypeElement("GetterSetter").let { sub ->
                assertThat(sub.getDeclaredMethods().jvmNames()).containsExactly(
                    "getY", "setY"
                )
                assertThat(sub.getAllMethods().jvmNames()).containsExactly(
                    "getX", "getY", "setY"
                )
                assertThat(sub.getAllNonPrivateInstanceMethods().jvmNames()).containsExactly(
                    "getX", "getY", "setY"
                )
            }
        }
    }

    @Test
    fun constructors() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            interface MyInterface
            class NoExplicitConstructor
            open class Base(x:Int)
            open class ExplicitConstructor {
                constructor(x:Int)
            }
            open class BaseWithSecondary(x:Int) {
                constructor(y:String):this(3)
            }
            class Sub(x:Int) : Base(x)
            class SubWith3Constructors() : BaseWithSecondary("abc") {
                constructor(list:List<String>): this()
                constructor(list:List<String>, x:Int): this()
            }
            abstract class AbstractNoExplicit
            abstract class AbstractExplicit(x:Int)
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val subjects = listOf(
                "MyInterface", "NoExplicitConstructor", "Base", "ExplicitConstructor",
                "BaseWithSecondary", "Sub", "SubWith3Constructors",
                "AbstractNoExplicit", "AbstractExplicit"
            )
            val constructorCounts = subjects.map {
                it to invocation.processingEnv.requireTypeElement(it).getConstructors().size
            }
            assertThat(constructorCounts)
                .containsExactly(
                    "MyInterface" to 0,
                    "NoExplicitConstructor" to 1,
                    "Base" to 1,
                    "ExplicitConstructor" to 1,
                    "BaseWithSecondary" to 2,
                    "Sub" to 1,
                    "SubWith3Constructors" to 3,
                    "AbstractNoExplicit" to 1,
                    "AbstractExplicit" to 1
                )

            val primaryConstructorParameterNames = subjects.map {
                it to invocation.processingEnv.requireTypeElement(it)
                    .findPrimaryConstructor()
                    ?.parameters?.map {
                        it.name
                    }
            }
            assertThat(primaryConstructorParameterNames)
                .containsExactly(
                    "MyInterface" to null,
                    "NoExplicitConstructor" to emptyList<String>(),
                    "Base" to listOf("x"),
                    "ExplicitConstructor" to null,
                    "BaseWithSecondary" to listOf("x"),
                    "Sub" to listOf("x"),
                    "SubWith3Constructors" to emptyList<String>(),
                    "AbstractNoExplicit" to emptyList<String>(),
                    "AbstractExplicit" to listOf("x")
                )
        }
    }

    @Test
    fun jvmDefault() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            interface MyInterface {
                fun notJvmDefault()
                @JvmDefault
                fun jvmDefault() {}
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val subject = invocation.processingEnv.requireTypeElement("MyInterface")
            assertThat(subject.getMethodByJvmName("notJvmDefault").isJavaDefault()).isFalse()
            assertThat(subject.getMethodByJvmName("jvmDefault").isJavaDefault()).isTrue()
        }
    }

    @Test
    fun constructors_java() {
        val src = Source.java(
            "Source",
            """
            import java.util.List;
            interface MyInterface {}
            class NoExplicitConstructor{}
            class Base {
                Base(int x){}
            }
            class ExplicitConstructor {
                ExplicitConstructor(int x){}
            }
            class BaseWithSecondary {
                BaseWithSecondary(int x){}
                BaseWithSecondary(String y){}
            }
            class Sub extends Base {
                Sub(int x) {
                    super(x);
                }
            }
            class SubWith3Constructors extends BaseWithSecondary {
                SubWith3Constructors() {
                    super(3);
                }
                SubWith3Constructors(List<String> list) {
                    super(3);
                }
                SubWith3Constructors(List<String> list, int x) {
                    super(3);
                }
            }
            abstract class AbstractNoExplicit {}
            abstract class AbstractExplicit {
                AbstractExplicit(int x) {}
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val subjects = listOf(
                "MyInterface", "NoExplicitConstructor", "Base", "ExplicitConstructor",
                "BaseWithSecondary", "Sub", "SubWith3Constructors",
                "AbstractNoExplicit", "AbstractExplicit"
            )
            val constructorCounts = subjects.map {
                it to invocation.processingEnv.requireTypeElement(it).getConstructors().size
            }
            assertThat(constructorCounts)
                .containsExactly(
                    "MyInterface" to 0,
                    "NoExplicitConstructor" to 1,
                    "Base" to 1,
                    "ExplicitConstructor" to 1,
                    "BaseWithSecondary" to 2,
                    "Sub" to 1,
                    "SubWith3Constructors" to 3,
                    "AbstractNoExplicit" to 1,
                    "AbstractExplicit" to 1
                )

            subjects.forEach {
                assertWithMessage(it)
                    .that(invocation.processingEnv.requireTypeElement(it).findPrimaryConstructor())
                    .isNull()
            }
        }
    }

    @Test
    fun enumTypeElement() {
        fun createSources(packageName: String) = listOf(
            Source.kotlin(
                "$packageName/KotlinEnum.kt",
                """
                package $packageName
                enum class KotlinEnum(private val x:Int) {
                    VAL1(1),
                    VAL2(2);

                    fun enumMethod():Unit {}
                }
                """.trimIndent()
            ),
            Source.java(
                "$packageName.JavaEnum",
                """
                package $packageName;
                public enum JavaEnum {
                    VAL1(1),
                    VAL2(2);

                    private int x;

                    JavaEnum(int x) {
                        this.x = x;
                    }
                    void enumMethod() {}
                }
                """.trimIndent()
            )
        )

        val classpath = compileFiles(
            createSources("lib")
        )
        runProcessorTest(
            sources = createSources("app"),
            classpath = classpath
        ) { invocation ->
            listOf(
                "lib.KotlinEnum", "lib.JavaEnum",
                "app.KotlinEnum", "app.JavaEnum"
            ).forEach { qName ->
                val typeElement = invocation.processingEnv.requireTypeElement(qName)
                assertWithMessage("$qName is enum")
                    .that(typeElement.isEnum())
                    .isTrue()
                assertWithMessage("$qName does not report enum constants in methods")
                    .that(typeElement.getDeclaredMethods().map { it.jvmName })
                    .run {
                        contains("enumMethod")
                        containsNoneOf("VAL1", "VAL2")
                    }
                assertWithMessage("$qName can return enum constants")
                    .that((typeElement as XEnumTypeElement).entries.map { it.name })
                    .containsExactly("VAL1", "VAL2")
                assertWithMessage("$qName  does not report enum constants in fields")
                    .that(typeElement.getAllFieldNames())
                    .run {
                        contains("x")
                        containsNoneOf("VAL1", "VAL2")
                    }
                assertWithMessage("$qName  does not report enum constants in declared fields")
                    .that(typeElement.getDeclaredFields().map { it.name })
                    .containsExactly("x")
            }
        }
    }

    @Test
    fun enclosedTypes() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            class TopLevelClass {
                class NestedClass
                object NestedObject
                interface NestedInterface
                enum class NestedEnum {
                    A, B
                }
                companion object {
                    val foo = 1
                }
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val topLevelClass = invocation.processingEnv.requireTypeElement("TopLevelClass")
            val enclosedTypeElements = topLevelClass.getEnclosedTypeElements()

            assertThat(enclosedTypeElements)
                .containsExactly(
                    invocation.processingEnv.requireTypeElement("TopLevelClass.NestedClass"),
                    invocation.processingEnv.requireTypeElement("TopLevelClass.NestedObject"),
                    invocation.processingEnv.requireTypeElement("TopLevelClass.NestedInterface"),
                    invocation.processingEnv.requireTypeElement("TopLevelClass.NestedEnum"),
                    invocation.processingEnv.requireTypeElement("TopLevelClass.Companion"),
                )
        }
    }

    @Test
    fun enclosedTypes_java() {
        val src = Source.java(
            "Source",
            """
            class TopLevelClass {
                class InnerClass { }
                static class NestedClass { }
                interface NestedInterface { }
                enum NestedEnum {
                    A, B
                }
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val topLevelClass = invocation.processingEnv.requireTypeElement("TopLevelClass")
            val enclosedTypeElements = topLevelClass.getEnclosedTypeElements()

            assertThat(enclosedTypeElements)
                .containsExactly(
                    invocation.processingEnv.requireTypeElement("TopLevelClass.InnerClass"),
                    invocation.processingEnv.requireTypeElement("TopLevelClass.NestedClass"),
                    invocation.processingEnv.requireTypeElement("TopLevelClass.NestedInterface"),
                    invocation.processingEnv.requireTypeElement("TopLevelClass.NestedEnum"),
                )
        }
    }

    @Test
    fun kotlinObjects() {
        val kotlinSrc = Source.kotlin(
            "Test.kt",
            """
            package foo.bar
            class KotlinClass {
                companion object
                object NestedObject
            }
            """.trimIndent()
        )
        runProcessorTest(listOf(kotlinSrc)) { invocation ->
            val kotlinClass = invocation.processingEnv.requireTypeElement(
                "foo.bar.KotlinClass")
            val companionObjects = kotlinClass.getEnclosedTypeElements().filter {
                it.isCompanionObject()
            }
            assertThat(companionObjects.size).isEqualTo(1)
            val companionObj = companionObjects.first()
            assertThat(companionObj.isKotlinObject()).isTrue()
        }
    }

    @Test
    fun inheritedGenericMethod() {
        runProcessorTest(
            sources = listOf(
                Source.kotlin(
                    "test.ConcreteClass.kt",
                    """
                    package test;
                    class ConcreteClass: AbstractClass<Foo, Bar>() {}
                    abstract class AbstractClass<T1, T2> {
                        fun method(t1: T1, t2: T2): T2 {
                          return t2
                        }
                    }
                    class Foo
                    class Bar
                    """.trimIndent()
                )
            )
        ) { invocation ->
            val concreteClass = invocation.processingEnv.requireTypeElement("test.ConcreteClass")
            val abstractClass = invocation.processingEnv.requireTypeElement("test.AbstractClass")
            val concreteClassMethod = concreteClass.getMethodByJvmName("method")
            val abstractClassMethod = abstractClass.getMethodByJvmName("method")
            val fooType = invocation.processingEnv.requireType("test.Foo")
            val barType = invocation.processingEnv.requireType("test.Bar")

            fun checkMethodElement(method: XMethodElement) {
                checkMethodElement(
                    method = method,
                    name = "method",
                    enclosingElement = abstractClass,
                    returnType = TypeVariableName.get("T2"),
                    parameterTypes = arrayOf(TypeVariableName.get("T1"), TypeVariableName.get("T2"))
                )
                checkMethodType(
                    methodType = method.executableType,
                    returnType = TypeVariableName.get("T2"),
                    parameterTypes = arrayOf(TypeVariableName.get("T1"), TypeVariableName.get("T2"))
                )
                checkMethodType(
                    methodType = method.asMemberOf(abstractClass.type),
                    returnType = TypeVariableName.get("T2"),
                    parameterTypes = arrayOf(TypeVariableName.get("T1"), TypeVariableName.get("T2"))
                )
                checkMethodType(
                    methodType = method.asMemberOf(concreteClass.type),
                    returnType = barType.typeName,
                    parameterTypes = arrayOf(fooType.typeName, barType.typeName)
                )
            }

            assertThat(concreteClassMethod).isEqualTo(abstractClassMethod)
            checkMethodElement(concreteClassMethod)
            checkMethodElement(abstractClassMethod)
        }
    }

    @Test
    fun overriddenGenericMethod() {
        runProcessorTest(
            sources = listOf(
                Source.kotlin(
                    "test.ConcreteClass.kt",
                    """
                    package test;
                    class ConcreteClass: AbstractClass<Foo, Bar>() {
                        override fun method(t1: Foo, t2: Bar): Bar {
                          return t2
                        }
                    }
                    abstract class AbstractClass<T1, T2> {
                        abstract fun method(t1: T1, t2: T2): T2
                    }
                    class Foo
                    class Bar
                    """.trimIndent()
                )
            )
        ) { invocation ->
            val concreteClass = invocation.processingEnv.requireTypeElement("test.ConcreteClass")
            val abstractClass = invocation.processingEnv.requireTypeElement("test.AbstractClass")
            val concreteClassMethod = concreteClass.getMethodByJvmName("method")
            val abstractClassMethod = abstractClass.getMethodByJvmName("method")
            val fooType = invocation.processingEnv.requireType("test.Foo")
            val barType = invocation.processingEnv.requireType("test.Bar")

            assertThat(concreteClassMethod).isNotEqualTo(abstractClassMethod)
            assertThat(concreteClassMethod.overrides(abstractClassMethod, concreteClass)).isTrue()

            // Check the abstract method and method type
            checkMethodElement(
                method = abstractClassMethod,
                name = "method",
                enclosingElement = abstractClass,
                returnType = TypeVariableName.get("T2"),
                parameterTypes = arrayOf(TypeVariableName.get("T1"), TypeVariableName.get("T2"))
            )
            checkMethodType(
                methodType = abstractClassMethod.executableType,
                returnType = TypeVariableName.get("T2"),
                parameterTypes = arrayOf(TypeVariableName.get("T1"), TypeVariableName.get("T2"))
            )
            checkMethodType(
                methodType = abstractClassMethod.asMemberOf(abstractClass.type),
                returnType = TypeVariableName.get("T2"),
                parameterTypes = arrayOf(TypeVariableName.get("T1"), TypeVariableName.get("T2"))
            )
            checkMethodType(
                methodType = abstractClassMethod.asMemberOf(concreteClass.type),
                returnType = barType.typeName,
                parameterTypes = arrayOf(fooType.typeName, barType.typeName)
            )

            // Check the concrete method and method type
            checkMethodElement(
                method = concreteClassMethod,
                name = "method",
                enclosingElement = concreteClass,
                returnType = barType.typeName,
                parameterTypes = arrayOf(fooType.typeName, barType.typeName)
            )
            checkMethodType(
                methodType = concreteClassMethod.executableType,
                returnType = barType.typeName,
                parameterTypes = arrayOf(fooType.typeName, barType.typeName)
            )
            checkMethodType(
                methodType = concreteClassMethod.asMemberOf(concreteClass.type),
                returnType = barType.typeName,
                parameterTypes = arrayOf(fooType.typeName, barType.typeName)
            )
        }
    }

    private fun checkMethodElement(
        method: XMethodElement,
        name: String,
        enclosingElement: XTypeElement,
        returnType: TypeName,
        parameterTypes: Array<TypeName>
    ) {
        assertThat(method.name).isEqualTo(name)
        assertThat(method.enclosingElement).isEqualTo(enclosingElement)
        assertThat(method.returnType.typeName).isEqualTo(returnType)
        assertThat(method.parameters.map { it.type.typeName })
            .containsExactly(*parameterTypes)
            .inOrder()
    }
    private fun checkMethodType(
        methodType: XMethodType,
        returnType: TypeName,
        parameterTypes: Array<TypeName>
    ) {
        assertThat(methodType.returnType.typeName).isEqualTo(returnType)
        assertThat(methodType.parameterTypes.map { it.typeName })
            .containsExactly(*parameterTypes)
            .inOrder()
    }

    @Test
    fun overriddenGenericConstructor() {
        runProcessorTest(
            sources = listOf(
                Source.kotlin(
                    "test.ConcreteClass.kt",
                    """
                    package test;
                    class ConcreteClass(foo: Foo): AbstractClass<Foo>(foo) {}
                    abstract class AbstractClass<T>(t: T)
                    class Foo
                    """.trimIndent()
                )
            )
        ) { invocation ->
            val concreteClass = invocation.processingEnv.requireTypeElement("test.ConcreteClass")
            val abstractClass = invocation.processingEnv.requireTypeElement("test.AbstractClass")
            val fooType = invocation.processingEnv.requireType("test.Foo")

            fun checkConstructorParameters(
                typeElement: XTypeElement,
                expectedParameters: Array<TypeName>
            ) {
                assertThat(typeElement.getConstructors()).hasSize(1)
                val constructor = typeElement.getConstructors()[0]
                assertThat(constructor.parameters.map { it.type.typeName })
                    .containsExactly(*expectedParameters)
                    .inOrder()
            }

            checkConstructorParameters(abstractClass, arrayOf(TypeVariableName.get("T")))
            checkConstructorParameters(concreteClass, arrayOf(fooType.typeName))
        }
    }

    @Test
    fun inheritedGenericField() {
        runProcessorTest(
            sources = listOf(
                Source.kotlin(
                    "test.ConcreteClass.kt",
                    """
                    package test;
                    class ConcreteClass: AbstractClass<Foo>()
                    abstract class AbstractClass<T> {
                        val field: T = TODO()
                    }
                    class Foo
                    """.trimIndent()
                )
            )
        ) { invocation ->
            val concreteClass = invocation.processingEnv.requireTypeElement("test.ConcreteClass")
            val abstractClass = invocation.processingEnv.requireTypeElement("test.AbstractClass")
            val concreteClassField = concreteClass.getField("field")
            val abstractClassField = abstractClass.getField("field")
            val fooType = invocation.processingEnv.requireType("test.Foo")

            fun checkFieldElement(field: XFieldElement) {
                assertThat(field.name).isEqualTo("field")
                assertThat(field.type.typeName).isEqualTo(TypeVariableName.get("T"))
                assertThat(field.asMemberOf(abstractClass.type).typeName)
                    .isEqualTo(TypeVariableName.get("T"))
                assertThat(field.asMemberOf(concreteClass.type).typeName)
                    .isEqualTo(fooType.typeName)
            }

            assertThat(concreteClassField).isEqualTo(abstractClassField)
            checkFieldElement(abstractClassField)
            checkFieldElement(concreteClassField)
        }
    }

    /**
     * it is good to exclude methods coming from Object when testing as they differ between KSP
     * and KAPT but irrelevant for Room.
     */
    private fun XTestInvocation.objectMethodNames() = processingEnv
        .requireTypeElement("java.lang.Object")
        .getAllMethods()
        .jvmNames()

    private fun Sequence<XMethodElement>.jvmNames() = map {
        it.jvmName
    }.toList()

    private fun List<XMethodElement>.jvmNames() = map {
        it.jvmName
    }.toList()

    private fun XMethodElement.signature(owner: XType): String {
        val methodType = this.asMemberOf(owner)
        val params = methodType.parameterTypes.joinToString(",") {
            it.typeName.toString()
        }
        return "$jvmName($params):${returnType.typeName}"
    }

    private fun XTestInvocation.nonObjectMethodSignatures(typeElement: XTypeElement): List<String> =
        typeElement.getAllMethods()
            .filterNot { it.jvmName in objectMethodNames() }
            .map { it.signature(typeElement.type) }.toList()
}

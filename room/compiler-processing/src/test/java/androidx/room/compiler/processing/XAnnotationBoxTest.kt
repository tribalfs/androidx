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

import androidx.room.compiler.processing.testcode.JavaAnnotationWithDefaults
import androidx.room.compiler.processing.testcode.JavaAnnotationWithPrimitiveArray
import androidx.room.compiler.processing.testcode.JavaAnnotationWithTypeReferences
import androidx.room.compiler.processing.testcode.JavaEnum
import androidx.room.compiler.processing.testcode.MainAnnotation
import androidx.room.compiler.processing.testcode.OtherAnnotation
import androidx.room.compiler.processing.util.Source
import androidx.room.compiler.processing.util.getField
import androidx.room.compiler.processing.util.getMethod
import androidx.room.compiler.processing.util.getParameter
import androidx.room.compiler.processing.util.runProcessorTest
import androidx.room.compiler.processing.util.runProcessorTestWithoutKsp
import androidx.room.compiler.processing.util.typeName
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import com.squareup.javapoet.ClassName
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import java.util.LinkedHashMap

@RunWith(JUnit4::class)
class XAnnotationBoxTest {
    @Test
    fun readSimpleAnnotationValue() {
        val source = Source.java(
            "foo.bar.Baz",
            """
            package foo.bar;
            @SuppressWarnings({"warning1", "warning 2"})
            public class Baz {
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(source)
        ) {
            val element = it.processingEnv.requireTypeElement("foo.bar.Baz")
            val annotationBox = element.toAnnotationBox(SuppressWarnings::class)
            assertThat(annotationBox).isNotNull()
            assertThat(
                annotationBox!!.value.value
            ).isEqualTo(
                arrayOf("warning1", "warning 2")
            )
        }
    }

    @Test
    fun typeReference() {
        val mySource = Source.java(
            "foo.bar.Baz",
            """
            package foo.bar;
            import androidx.room.compiler.processing.testcode.MainAnnotation;
            import androidx.room.compiler.processing.testcode.OtherAnnotation;
            @MainAnnotation(
                typeList = {String.class, Integer.class},
                singleType = Long.class,
                intMethod = 3,
                otherAnnotationArray = {
                    @OtherAnnotation(
                        value = "other list 1"
                    ),
                    @OtherAnnotation("other list 2"),
                },
                singleOtherAnnotation = @OtherAnnotation("other single")
            )
            public class Baz {
            }
            """.trimIndent()
        )
        // re-enable after fixing b/175144186
        runProcessorTestWithoutKsp(
            listOf(mySource)
        ) {
            val element = it.processingEnv.requireTypeElement("foo.bar.Baz")
            element.toAnnotationBox(MainAnnotation::class)!!.let { annotation ->
                assertThat(
                    annotation.getAsTypeList("typeList")
                ).containsExactly(
                    it.processingEnv.requireType(java.lang.String::class),
                    it.processingEnv.requireType(java.lang.Integer::class)
                )
                assertThat(
                    annotation.getAsType("singleType")
                ).isEqualTo(
                    it.processingEnv.requireType(java.lang.Long::class)
                )

                assertThat(annotation.value.intMethod).isEqualTo(3)
                annotation.getAsAnnotationBox<OtherAnnotation>("singleOtherAnnotation")
                    .let { other ->
                        assertThat(other.value.value).isEqualTo("other single")
                    }
                annotation.getAsAnnotationBoxArray<OtherAnnotation>("otherAnnotationArray")
                    .let { boxArray ->
                        assertThat(boxArray).hasLength(2)
                        assertThat(boxArray[0].value.value).isEqualTo("other list 1")
                        assertThat(boxArray[1].value.value).isEqualTo("other list 2")
                    }
            }
        }
    }

    @Test
    fun readSimpleAnnotationValue_kotlin() {
        val source = Source.kotlin(
            "Foo.kt",
            """
            @SuppressWarnings("warning1", "warning 2")
            class Subject {
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(source)
        ) {
            val element = it.processingEnv.requireTypeElement("Subject")
            val annotationBox = element.toAnnotationBox(SuppressWarnings::class)
            assertThat(annotationBox).isNotNull()
            assertThat(
                annotationBox!!.value.value
            ).isEqualTo(
                arrayOf("warning1", "warning 2")
            )
        }
    }

    @Test
    fun typeReference_kotlin() {
        val mySource = Source.kotlin(
            "Foo.kt",
            """
            import androidx.room.compiler.processing.testcode.MainAnnotation
            import androidx.room.compiler.processing.testcode.OtherAnnotation

            @MainAnnotation(
                typeList = [String::class, Int::class],
                singleType = Long::class,
                intMethod = 3,
                otherAnnotationArray = [
                    OtherAnnotation(
                        value = "other list 1"
                    ),
                    OtherAnnotation(
                        value = "other list 2"
                    )
                ],
                singleOtherAnnotation = OtherAnnotation("other single")
            )
            public class Subject {
            }
            """.trimIndent()
        )
        runProcessorTest(
            listOf(mySource)
        ) { invocation ->
            val element = invocation.processingEnv.requireTypeElement("Subject")
            element.toAnnotationBox(MainAnnotation::class)!!.let { annotation ->
                assertThat(
                    annotation.getAsTypeList("typeList")
                ).containsExactly(
                    invocation.processingEnv.requireType(String::class.typeName()),
                    invocation.processingEnv.requireType(Int::class.typeName())
                )
                assertThat(
                    annotation.getAsType("singleType")
                ).isEqualTo(
                    invocation.processingEnv.requireType(Long::class.typeName())
                )

                assertThat(annotation.value.intMethod).isEqualTo(3)
                annotation.getAsAnnotationBox<OtherAnnotation>("singleOtherAnnotation")
                    .let { other ->
                        assertThat(other.value.value).isEqualTo("other single")
                    }
                annotation.getAsAnnotationBoxArray<OtherAnnotation>("otherAnnotationArray")
                    .let { boxArray ->
                        assertThat(boxArray).hasLength(2)
                        assertThat(boxArray[0].value.value).isEqualTo("other list 1")
                        assertThat(boxArray[1].value.value).isEqualTo("other list 2")
                    }
            }
        }
    }

    @Test
    fun typeReferenceArray_singleItemInJava() {
        val src = Source.java(
            "Subject",
            """
            import androidx.room.compiler.processing.testcode.JavaAnnotationWithTypeReferences;
            @JavaAnnotationWithTypeReferences(String.class)
            class Subject {
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(src)
        ) { invocation ->
            val subject = invocation.processingEnv.requireTypeElement("Subject")
            val annotationValue = subject.toAnnotationBox(
                JavaAnnotationWithTypeReferences::class
            )?.getAsTypeList("value")
            assertThat(annotationValue?.map { it.typeName }).containsExactly(
                ClassName.get(String::class.java)
            )
        }
    }

    @Test
    fun propertyAnnotations() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            import androidx.room.compiler.processing.testcode.OtherAnnotation
            class Subject {
                @SuppressWarnings("onProp1")
                var prop1:Int = TODO()

                @get:SuppressWarnings("onGetter2")
                @set:SuppressWarnings("onSetter2")
                @field:SuppressWarnings("onField2")
                @setparam:SuppressWarnings("onSetterParam2")
                var prop2:Int = TODO()

                @get:SuppressWarnings("onGetter3")
                @set:SuppressWarnings("onSetter3")
                @setparam:SuppressWarnings("onSetterParam3")
                var prop3:Int
                    @OtherAnnotation("_onGetter3")
                    get() = 3

                    @OtherAnnotation("_onSetter3")
                    set(@OtherAnnotation("_onSetterParam3") value) = Unit
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val subject = invocation.processingEnv.requireTypeElement("Subject")

            subject.getField("prop1").assertHasSuppressWithValue("onProp1")
            subject.getMethod("getProp1").assertDoesNotHaveAnnotation()
            subject.getMethod("setProp1").assertDoesNotHaveAnnotation()
            subject.getMethod("setProp1").parameters.first().assertDoesNotHaveAnnotation()

            subject.getField("prop2").assertHasSuppressWithValue("onField2")
            subject.getMethod("getProp2").assertHasSuppressWithValue("onGetter2")
            subject.getMethod("setProp2").assertHasSuppressWithValue("onSetter2")
            subject.getMethod("setProp2").parameters.first().assertHasSuppressWithValue(
                "onSetterParam2"
            )

            subject.getMethod("getProp3").assertHasSuppressWithValue("onGetter3")
            subject.getMethod("setProp3").assertHasSuppressWithValue("onSetter3")
            subject.getMethod("setProp3").parameters.first().assertHasSuppressWithValue(
                "onSetterParam3"
            )

            assertThat(
                subject.getMethod("getProp3").getOtherAnnotationValue()
            ).isEqualTo("_onGetter3")
            assertThat(
                subject.getMethod("setProp3").getOtherAnnotationValue()
            ).isEqualTo("_onSetter3")
            val otherAnnotationValue =
                subject.getMethod("setProp3").parameters.first().getOtherAnnotationValue()
            assertThat(
                otherAnnotationValue
            ).isEqualTo("_onSetterParam3")
        }
    }

    @Test
    fun methodAnnotations() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            import androidx.room.compiler.processing.testcode.OtherAnnotation
            class Subject {
                fun noAnnotations(x:Int): Unit = TODO()
                @SuppressWarnings("onMethod")
                fun methodAnnotation(
                    @SuppressWarnings("onParam") annotated:Int,
                    notAnnotated:Int
                ): Unit = TODO()
            }
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val subject = invocation.processingEnv.requireTypeElement("Subject")
            subject.getMethod("noAnnotations").let { method ->
                method.assertDoesNotHaveAnnotation()
                method.getParameter("x").assertDoesNotHaveAnnotation()
            }
            subject.getMethod("methodAnnotation").let { method ->
                method.assertHasSuppressWithValue("onMethod")
                method.getParameter("annotated").assertHasSuppressWithValue("onParam")
                method.getParameter("notAnnotated").assertDoesNotHaveAnnotation()
            }
        }
    }

    @Test
    fun constructorParameterAnnotations() {
        val src = Source.kotlin(
            "Foo.kt",
            """
            @SuppressWarnings("onClass")
            data class Subject(
                @field:SuppressWarnings("onField")
                @param:SuppressWarnings("onConstructorParam")
                @get:SuppressWarnings("onGetter")
                @set:SuppressWarnings("onSetter")
                var x:Int
            )
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(src)) { invocation ->
            val subject = invocation.processingEnv.requireTypeElement("Subject")
            subject.assertHasSuppressWithValue("onClass")
            assertThat(subject.getConstructors()).hasSize(1)
            val constructor = subject.getConstructors().single()
            constructor.getParameter("x").assertHasSuppressWithValue("onConstructorParam")
            subject.getMethod("getX").assertHasSuppressWithValue("onGetter")
            subject.getMethod("setX").assertHasSuppressWithValue("onSetter")
            subject.getField("x").assertHasSuppressWithValue("onField")
        }
    }

    @Test
    fun defaultValues() {
        val kotlinSrc = Source.kotlin(
            "KotlinClass.kt",
            """
            import androidx.room.compiler.processing.testcode.JavaAnnotationWithDefaults
            @JavaAnnotationWithDefaults
            class KotlinClass
            """.trimIndent()
        )
        val javaSrc = Source.java(
            "JavaClass.java",
            """
            import androidx.room.compiler.processing.testcode.JavaAnnotationWithDefaults;
            @JavaAnnotationWithDefaults
            class JavaClass {}
            """.trimIndent()
        )
        runProcessorTest(sources = listOf(kotlinSrc, javaSrc)) { invocation ->
            listOf("KotlinClass", "JavaClass")
                .map {
                    invocation.processingEnv.requireTypeElement(it)
                }.forEach { typeElement ->
                    val annotation =
                        typeElement.toAnnotationBox(JavaAnnotationWithDefaults::class)
                    checkNotNull(annotation)
                    assertThat(annotation.value.intVal).isEqualTo(3)
                    assertThat(annotation.value.intArrayVal).isEqualTo(intArrayOf(1, 3, 5))
                    assertThat(annotation.value.stringArrayVal).isEqualTo(arrayOf("x", "y"))
                    assertThat(annotation.value.stringVal).isEqualTo("foo")
                    assertThat(
                        annotation.getAsType("typeVal")?.rawType?.typeName
                    ).isEqualTo(
                        ClassName.get(HashMap::class.java)
                    )
                    assertThat(
                        annotation.getAsTypeList("typeArrayVal").map {
                            it.rawType.typeName
                        }
                    ).isEqualTo(
                        listOf(ClassName.get(LinkedHashMap::class.java))
                    )

                    assertThat(
                        annotation.value.enumVal
                    ).isEqualTo(
                        JavaEnum.DEFAULT
                    )

                    assertThat(
                        annotation.value.enumArrayVal
                    ).isEqualTo(
                        arrayOf(JavaEnum.VAL1, JavaEnum.VAL2)
                    )

                    assertThat(
                        annotation.getAsAnnotationBox<OtherAnnotation>("otherAnnotationVal")
                            .value.value
                    ).isEqualTo("def")

                    assertThat(
                        annotation
                            .getAsAnnotationBoxArray<OtherAnnotation>("otherAnnotationArrayVal")
                            .map {
                                it.value.value
                            }
                    ).containsExactly("v1")
                }
        }
    }

    @Test
    fun javaPrimitiveArray() {
        // TODO: expand this test for other primitive types: 179081610
        val javaSrc = Source.java(
            "JavaSubject.java",
            """
            import androidx.room.compiler.processing.testcode.*;
            class JavaSubject {
                @JavaAnnotationWithPrimitiveArray(intArray = {1, 2, 3})
                Object annotated1;
            }
            """.trimIndent()
        )
        val kotlinSrc = Source.kotlin(
            "KotlinSubject.kt",
            """
            import androidx.room.compiler.processing.testcode.*;
            class KotlinSubject {
                @JavaAnnotationWithPrimitiveArray(intArray = [1, 2, 3])
                val annotated1:Any = TODO()
            }
            """.trimIndent()
        )
        runProcessorTest(
            sources = listOf(javaSrc, kotlinSrc)
        ) { invocation ->
            arrayOf("JavaSubject", "KotlinSubject").map {
                invocation.processingEnv.requireTypeElement(it)
            }.forEach { subject ->
                val annotation = subject.getField("annotated1").toAnnotationBox(
                    JavaAnnotationWithPrimitiveArray::class
                )
                assertThat(
                    annotation?.value?.intArray
                ).isEqualTo(
                    intArrayOf(1, 2, 3)
                )
            }
        }
    }

    // helper function to read what we need
    private fun XAnnotated.getSuppressValues(): Array<String>? {
        return this.toAnnotationBox(SuppressWarnings::class)?.value?.value
    }

    private fun XAnnotated.assertHasSuppressWithValue(vararg expected: String) {
        assertWithMessage("has suppress annotation $this")
            .that(this.hasAnnotation(SuppressWarnings::class))
            .isTrue()
        assertWithMessage("$this")
            .that(this.hasAnnotationWithPackage(SuppressWarnings::class.java.packageName))
            .isTrue()
        assertWithMessage("$this")
            .that(getSuppressValues())
            .isEqualTo(expected)
    }

    private fun XAnnotated.assertDoesNotHaveAnnotation() {
        assertWithMessage("$this")
            .that(this.hasAnnotation(SuppressWarnings::class))
            .isFalse()
        assertWithMessage("$this")
            .that(this.hasAnnotationWithPackage(SuppressWarnings::class.java.packageName))
            .isFalse()
        assertWithMessage("$this")
            .that(this.getSuppressValues())
            .isNull()
    }

    private fun XAnnotated.getOtherAnnotationValue(): String? {
        return this.toAnnotationBox(OtherAnnotation::class)?.value?.value
    }
}

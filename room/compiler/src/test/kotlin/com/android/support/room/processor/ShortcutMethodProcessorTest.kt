/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.support.room.processor

import com.android.support.room.Dao
import com.android.support.room.ext.typeName
import com.android.support.room.testing.TestInvocation
import com.android.support.room.testing.TestProcessor
import com.android.support.room.vo.ShortcutMethod
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.google.common.truth.Truth
import com.google.testing.compile.CompileTester
import com.google.testing.compile.JavaFileObjects
import com.google.testing.compile.JavaSourcesSubjectFactory
import com.squareup.javapoet.ArrayTypeName
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import javax.lang.model.element.ExecutableElement
import javax.lang.model.type.DeclaredType
import kotlin.reflect.KClass

/**
 * Base test class for shortcut methods.
 */
abstract class ShortcutMethodProcessorTest<out T : ShortcutMethod>(
        val annotation: KClass<out Annotation>) {
    companion object {
        const val DAO_PREFIX = """
                package foo.bar;
                import com.android.support.room.*;
                import java.util.*;
                @Dao
                abstract class MyClass {
                """
        const val DAO_SUFFIX = "}"
        val USER_TYPE_NAME: TypeName = COMMON.USER_TYPE_NAME
    }

    @Test
    fun noParams() {
        singleShortcutMethod(
                """
                @${annotation.java.canonicalName}
                abstract public void foo();
                """) { shortcut, invocation ->
            assertThat(shortcut.name, `is`("foo"))
            assertThat(shortcut.parameters.size, `is`(0))
            assertThat(shortcut.returnCount, `is`(false))
        }.failsToCompile().withErrorContaining(noParamsError())
    }

    abstract fun noParamsError(): String

    @Test
    fun single() {
        singleShortcutMethod(
                """
                @${annotation.java.canonicalName}
                abstract public int foo(User user);
                """) { shortcut, invocation ->
            assertThat(shortcut.name, `is`("foo"))
            assertThat(shortcut.parameters.size, `is`(1))
            val param = shortcut.parameters.first()
            assertThat(param.type.typeName(), `is`(USER_TYPE_NAME))
            assertThat(param.entityType?.typeName(), `is`(USER_TYPE_NAME))
            assertThat(shortcut.entity?.typeName,
                    `is`(ClassName.get("foo.bar", "User") as TypeName))
            assertThat(shortcut.returnCount, `is`(true))
        }.compilesWithoutError()
    }

    @Test
    fun notAnEntity() {
        singleShortcutMethod(
                """
                @${annotation.java.canonicalName}
                abstract public void foo(NotAnEntity notValid);
                """) { shortcut, invocation ->
            assertThat(shortcut.name, `is`("foo"))
            assertThat(shortcut.parameters.size, `is`(1))
            val param = shortcut.parameters.first()
            assertThat(param.entityType, `is`(CoreMatchers.nullValue()))
            assertThat(shortcut.entity, `is`(CoreMatchers.nullValue()))
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.CANNOT_FIND_ENTITY_FOR_SHORTCUT_QUERY_PARAMETER
        )
    }

    @Test
    fun two() {
        singleShortcutMethod(
                """
                @${annotation.java.canonicalName}
                abstract public void foo(User u1, User u2);
                """) { shortcut, invocation ->
            assertThat(shortcut.name, `is`("foo"))

            assertThat(shortcut.parameters.size, `is`(2))
            shortcut.parameters.forEach {
                assertThat(it.type.typeName(), `is`(USER_TYPE_NAME))
                assertThat(it.entityType?.typeName(), `is`(USER_TYPE_NAME))
            }
            assertThat(shortcut.entity?.typeName, `is`(USER_TYPE_NAME))
            assertThat(shortcut.parameters.map { it.name },
                    `is`(listOf("u1", "u2")))
            assertThat(shortcut.returnCount, `is`(false))
        }.compilesWithoutError()
    }

    @Test
    fun list() {
        singleShortcutMethod(
                """
                @${annotation.java.canonicalName}
                abstract public int users(List<User> users);
                """) { shortcut, invocation ->
            assertThat(shortcut.name, `is`("users"))
            assertThat(shortcut.parameters.size, `is`(1))
            val param = shortcut.parameters.first()
            assertThat(param.type.typeName(), `is`(
                    ParameterizedTypeName.get(
                            ClassName.get("java.util", "List"), USER_TYPE_NAME) as TypeName))
            assertThat(param.entityType?.typeName(), `is`(USER_TYPE_NAME))
            assertThat(shortcut.entity?.typeName, `is`(USER_TYPE_NAME))
            assertThat(shortcut.returnCount, `is`(true))
        }.compilesWithoutError()
    }

    @Test
    fun array() {
        singleShortcutMethod(
                """
                @${annotation.java.canonicalName}
                abstract public void users(User[] users);
                """) { shortcut, invocation ->
            assertThat(shortcut.name, `is`("users"))
            assertThat(shortcut.parameters.size, `is`(1))
            val param = shortcut.parameters.first()
            assertThat(param.type.typeName(), `is`(
                    ArrayTypeName.of(COMMON.USER_TYPE_NAME) as TypeName))
            assertThat(shortcut.entity?.typeName,
                    `is`(ClassName.get("foo.bar", "User") as TypeName))
            assertThat(shortcut.returnCount, `is`(false))
        }.compilesWithoutError()
    }

    @Test
    fun set() {
        singleShortcutMethod(
                """
                @${annotation.java.canonicalName}
                abstract public void modifyUsers(Set<User> users);
                """) { shortcut, invocation ->
            assertThat(shortcut.name, `is`("modifyUsers"))
            assertThat(shortcut.parameters.size, `is`(1))
            val param = shortcut.parameters.first()
            assertThat(param.type.typeName(), `is`(
                    ParameterizedTypeName.get(ClassName.get("java.util", "Set")
                            , COMMON.USER_TYPE_NAME) as TypeName))
            assertThat(shortcut.entity?.typeName,
                    `is`(ClassName.get("foo.bar", "User") as TypeName))
            assertThat(shortcut.returnCount, `is`(false))
        }.compilesWithoutError()
    }

    @Test
    fun differentTypes() {
        singleShortcutMethod(
                """
                @${annotation.java.canonicalName}
                abstract public void foo(User u1, Book b1);
                """) { shortcut, invocation ->
            assertThat(shortcut.parameters.size, `is`(2))
            assertThat(shortcut.parameters[0].type.typeName().toString(),
                    `is`("foo.bar.User"))
            assertThat(shortcut.parameters[1].type.typeName().toString(),
                    `is`("foo.bar.Book"))
            assertThat(shortcut.parameters.map { it.name }, `is`(listOf("u1", "b1")))
            assertThat(shortcut.returnCount, `is`(false))
        }.failsToCompile().withErrorContaining(differentTypesError())
    }

    abstract fun differentTypesError(): String

    @Test
    fun invalidReturnType() {
        singleShortcutMethod(
                """
                @${annotation.java.canonicalName}
                abstract public long foo(User user);
                """) { shortcut, invocation ->
        }.failsToCompile().withErrorContaining(invalidReturnTypeError())
    }

    abstract fun invalidReturnTypeError(): String

    abstract fun process(baseContext: Context, containing: DeclaredType,
                         executableElement: ExecutableElement): T

    fun singleShortcutMethod(vararg input: String,
                             handler: (T, TestInvocation) -> Unit):
            CompileTester {
        return Truth.assertAbout(JavaSourcesSubjectFactory.javaSources())
                .that(listOf(JavaFileObjects.forSourceString("foo.bar.MyClass",
                        DAO_PREFIX + input.joinToString("\n") + DAO_SUFFIX
                ), COMMON.USER, COMMON.BOOK, COMMON.NOT_AN_ENTITY))
                .processedWith(TestProcessor.builder()
                        .forAnnotations(annotation, Dao::class)
                        .nextRunHandler { invocation ->
                            val (owner, methods) = invocation.roundEnv
                                    .getElementsAnnotatedWith(Dao::class.java)
                                    .map {
                                        Pair(it,
                                                invocation.processingEnv.elementUtils
                                                        .getAllMembers(MoreElements.asType(it))
                                                        .filter {
                                                            MoreElements.isAnnotationPresent(it,
                                                                    annotation.java)
                                                        }
                                        )
                                    }.filter { it.second.isNotEmpty() }.first()
                            val processed = process(
                                    baseContext = invocation.context,
                                    containing = MoreTypes.asDeclared(owner.asType()),
                                    executableElement = MoreElements.asExecutable(methods.first()))
                            handler(processed, invocation)
                            true
                        }
                        .build())
    }
}

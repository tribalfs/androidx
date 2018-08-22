/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.room.processor

import androidx.annotation.NonNull
import androidx.room.Embedded
import androidx.room.testing.TestInvocation
import androidx.room.testing.TestProcessor
import androidx.room.vo.FtsEntity
import com.google.auto.common.MoreElements
import com.google.common.truth.Truth
import com.google.testing.compile.CompileTester
import com.google.testing.compile.JavaFileObjects
import com.google.testing.compile.JavaSourcesSubjectFactory
import javax.tools.JavaFileObject

abstract class BaseFtsEntityParserTest {
    companion object {
        const val ENTITY_PREFIX = """
            package foo.bar;
            import androidx.room.*;
            import androidx.annotation.NonNull;
            import java.util.*;
            @Fts%sEntity%s
            public class MyEntity %s {
            """
        const val ENTITY_SUFFIX = "}"
    }

    fun singleEntity(
        input: String,
        attributes: Map<String, String> = mapOf(),
        baseClass: String = "",
        jfos: List<JavaFileObject> = emptyList(),
        classLoader: ClassLoader = javaClass.classLoader,
        handler: (FtsEntity, TestInvocation) -> Unit
    ): CompileTester {
        val ftsVersion = getFtsVersion().toString()
        val attributesReplacement: String
        if (attributes.isEmpty()) {
            attributesReplacement = ""
        } else {
            attributesReplacement = "(" +
                    attributes.entries.joinToString(",") { "${it.key} = ${it.value}" } +
                    ")".trimIndent()
        }
        val baseClassReplacement: String
        if (baseClass == "") {
            baseClassReplacement = ""
        } else {
            baseClassReplacement = " extends $baseClass"
        }
        return Truth.assertAbout(JavaSourcesSubjectFactory.javaSources())
                .that(jfos + JavaFileObjects.forSourceString("foo.bar.MyEntity",
                        ENTITY_PREFIX.format(ftsVersion, attributesReplacement,
                                baseClassReplacement) + input + ENTITY_SUFFIX))
                .withClasspathFrom(classLoader)
                .processedWith(TestProcessor.builder()
                        .forAnnotations(androidx.room.Entity::class,
                                androidx.room.Fts3Entity::class,
                                androidx.room.Fts4Entity::class,
                                androidx.room.PrimaryKey::class,
                                androidx.room.Ignore::class,
                                Embedded::class,
                                androidx.room.ColumnInfo::class,
                                NonNull::class)
                        .nextRunHandler { invocation ->
                            val fts3AnnotatedElements = invocation.roundEnv
                                    .getElementsAnnotatedWith(androidx.room.Fts3Entity::class.java)
                            val fts4AnnotatedElements = invocation.roundEnv
                                    .getElementsAnnotatedWith(androidx.room.Fts4Entity::class.java)
                            val entity = (fts3AnnotatedElements + fts4AnnotatedElements).first {
                                it.toString() == "foo.bar.MyEntity"
                            }
                            val parser = FtsTableEntityProcessor(invocation.context,
                                    MoreElements.asType(entity))
                            val parsedQuery = parser.process()
                            handler(parsedQuery, invocation)
                            true
                        }
                        .build())
    }

    abstract fun getFtsVersion(): Int
}
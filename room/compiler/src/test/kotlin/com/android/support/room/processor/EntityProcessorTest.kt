/*
 * Copyright (C) 2016 The Android Open Source Project
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

import COMMON
import com.android.support.room.parser.SQLTypeAffinity
import com.android.support.room.processor.ProcessorErrors.RELATION_IN_ENTITY
import com.android.support.room.vo.CallType
import com.android.support.room.vo.Field
import com.android.support.room.vo.FieldGetter
import com.android.support.room.vo.FieldSetter
import com.android.support.room.vo.Index
import com.android.support.room.vo.Pojo
import com.google.testing.compile.JavaFileObjects
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import javax.lang.model.type.TypeKind.INT

@RunWith(JUnit4::class)
class EntityProcessorTest : BaseEntityParserTest() {
    @Test
    fun simple() {
        singleEntity("""
                @PrimaryKey
                private int id;
                public int getId() { return id; }
                public void setId(int id) { this.id = id; }
            """) { entity, invocation ->
            assertThat(entity.type.toString(), `is`("foo.bar.MyEntity"))
            assertThat(entity.fields.size, `is`(1))
            val field = entity.fields.first()
            val intType = invocation.processingEnv.typeUtils.getPrimitiveType(INT)
            assertThat(field, `is`(Field(
                    element = field.element,
                    name = "id",
                    type = intType,
                    columnName = "id",
                    affinity = SQLTypeAffinity.INTEGER)))
            assertThat(field.setter, `is`(FieldSetter("setId", intType, CallType.METHOD)))
            assertThat(field.getter, `is`(FieldGetter("getId", intType, CallType.METHOD)))
            assertThat(entity.primaryKey.fields, `is`(listOf(field)))
        }.compilesWithoutError()
    }

    @Test
    fun noGetter() {
        singleEntity("""
                @PrimaryKey
                private int id;
                public void setId(int id) {this.id = id;}
                """) { entity, invocation -> }
                .failsToCompile()
                .withErrorContaining(ProcessorErrors.CANNOT_FIND_GETTER_FOR_FIELD)
    }

    @Test
    fun noSetter() {
        singleEntity("""
                @PrimaryKey
                private int id;
                public int getId(){ return id; }
                """) { entity, invocation -> }
                .failsToCompile()
                .withErrorContaining(ProcessorErrors.CANNOT_FIND_SETTER_FOR_FIELD)
    }

    @Test
    fun tooManyGetters() {
        singleEntity("""
                @PrimaryKey
                private int id;
                public void setId(int id) {}
                public int getId(){ return id; }
                public int id(){ return id; }
                """) { entity, invocation -> }
                .failsToCompile()
                .withErrorContaining("getId, id")
    }

    @Test
    fun tooManyGettersWithIgnore() {
        singleEntity("""
                @PrimaryKey
                private int id;
                public void setId(int id) {}
                public int getId(){ return id; }
                @Ignore public int id(){ return id; }
                """) { entity, invocation ->
            assertThat(entity.fields.first().getter.name, `is`("getId"))
        }.compilesWithoutError()
    }

    @Test
    fun tooManyGettersWithDifferentVisibility() {
        singleEntity("""
                @PrimaryKey
                private int id;
                public void setId(int id) {}
                public int getId(){ return id; }
                protected int id(){ return id; }
                """) { entity, invocation ->
            assertThat(entity.fields.first().getter.name, `is`("getId"))
        }.compilesWithoutError()
    }

    @Test
    fun tooManyGettersWithDifferentTypes() {
        singleEntity("""
                @PrimaryKey
                public int id;
                public void setId(int id) {}
                public int getId(){ return id; }
                """) { entity, invocation ->
            assertThat(entity.fields.first().getter.name, `is`("id"))
            assertThat(entity.fields.first().getter.callType, `is`(CallType.FIELD))
        }.compilesWithoutError()
    }

    @Test
    fun tooManySetters() {
        singleEntity("""
                @PrimaryKey
                private int id;
                public void setId(int id) {}
                public void id(int id) {}
                public int getId(){ return id; }
                """) { entity, invocation -> }
                .failsToCompile()
                .withErrorContaining("setId, id")
    }

    @Test
    fun tooManySettersWithIgnore() {
        singleEntity("""
                @PrimaryKey
                private int id;
                public void setId(int id) {}
                @Ignore public void id(int id) {}
                public int getId(){ return id; }
                """) { entity, invocation ->
            assertThat(entity.fields.first().setter.name, `is`("setId"))
        }.compilesWithoutError()
    }

    @Test
    fun tooManySettersWithDifferentVisibility() {
        singleEntity("""
                @PrimaryKey
                private int id;
                public void setId(int id) {}
                protected void id(int id) {}
                public int getId(){ return id; }
                """) { entity, invocation ->
            assertThat(entity.fields.first().setter.name, `is`("setId"))
        }.compilesWithoutError()
    }

    @Test
    fun tooManySettersWithDifferentTypes() {
        singleEntity("""
                @PrimaryKey
                public int id;
                public void setId(int id) {}
                public int getId(){ return id; }
                """) { entity, invocation ->
            assertThat(entity.fields.first().setter.name, `is`("id"))
            assertThat(entity.fields.first().setter.callType, `is`(CallType.FIELD))
        }.compilesWithoutError()
    }

    @Test
    fun preferPublicOverProtected() {
        singleEntity("""
                @PrimaryKey
                int id;
                public void setId(int id) {}
                public int getId(){ return id; }
                """) { entity, invocation ->
            assertThat(entity.fields.first().setter.name, `is`("setId"))
            assertThat(entity.fields.first().getter.name, `is`("getId"))
        }.compilesWithoutError()
    }

    @Test
    fun customName() {
        singleEntity("""
                @PrimaryKey
                int x;
                """, hashMapOf(Pair("tableName", "\"foo_table\""))) { entity, invocation ->
            assertThat(entity.tableName, `is`("foo_table"))
        }.compilesWithoutError()
    }

    @Test
    fun emptyCustomName() {
        singleEntity("""
                @PrimaryKey
                int x;
                """, hashMapOf(Pair("tableName", "\" \""))) { entity, invocation ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.ENTITY_TABLE_NAME_CANNOT_BE_EMPTY)
    }

    @Test
    fun missingPrimaryKey() {
        singleEntity("""
                """) { entity, invocation ->
        }.failsToCompile()
                .withErrorContaining(ProcessorErrors.MISSING_PRIMARY_KEY)
    }

    @Test
    fun missingColumnAdapter() {
        singleEntity("""
                @PrimaryKey
                public java.util.Date myDate;
                """) { entity, invocation ->

        }.failsToCompile().withErrorContaining(ProcessorErrors.CANNOT_FIND_COLUMN_TYPE_ADAPTER)
    }

    @Test
    fun dropSubPrimaryKey() {
        singleEntity(
                """
                @PrimaryKey
                int id;
                @Decompose
                Point myPoint;
                static class Point {
                    @PrimaryKey
                    int x;
                    int y;
                }
                """
        ) { entity, invocation ->
            assertThat(entity.primaryKey.fields.map { it.name }, `is`(listOf("id")))
        }.compilesWithoutError()
                .withWarningCount(1)
                .withWarningContaining(ProcessorErrors.decomposedPrimaryKeyIsDropped(
                        "foo.bar.MyEntity", "x"))
    }

    @Test
    fun ignoreDropSubPrimaryKey() {
        singleEntity(
                """
                @PrimaryKey
                int id;
                @Decompose
                @SuppressWarnings(RoomWarnings.PRIMARY_KEY_FROM_DECOMPOSED_IS_DROPPED)
                Point myPoint;
                static class Point {
                    @PrimaryKey
                    int x;
                    int y;
                }
                """
        ) { entity, invocation ->
            assertThat(entity.primaryKey.fields.map { it.name }, `is`(listOf("id")))
        }.compilesWithoutError().withWarningCount(0)
    }

    private fun fieldsByName(entity : Pojo, vararg fieldNames : String) : List<Field> {
        return fieldNames
                .map { name -> entity.fields.find { it.name == name } }
                .filterNotNull()
    }

    @Test
    fun index_simple() {
        val annotation = mapOf(
                "indices" to """@Index("foo")"""
        )
        singleEntity(
                """
                @PrimaryKey
                public int id;
                public String foo;
                """
                , annotation) { entity, invocation ->
            assertThat(entity.indices, `is`(
                    listOf(Index(name = "index_MyEntity_foo",
                            unique = false,
                            fields = fieldsByName(entity, "foo")))))
        }.compilesWithoutError()
    }

    @Test
    fun index_fromField() {
        singleEntity(
                """
                @PrimaryKey
                public int id;
                @ColumnInfo(index = true)
                public String foo;
                """) { entity, invocation ->
            assertThat(entity.indices, `is`(
                    listOf(Index(name = "index_MyEntity_foo",
                            unique = false,
                            fields = fieldsByName(entity, "foo")))
            ))
        }.compilesWithoutError()
    }

    @Test
    fun index_multiColumn() {
        val annotation = mapOf(
                "indices" to """@Index({"foo", "id"})"""
        )
        singleEntity(
                """
                @PrimaryKey
                public int id;
                public String foo;
                """
                , annotation) { entity, invocation ->
            assertThat(entity.indices, `is`(
                    listOf(Index(name = "index_MyEntity_foo_id",
                            unique = false,
                            fields = fieldsByName(entity, "foo", "id")))
            ))
        }.compilesWithoutError()
    }

    @Test
    fun index_multiple() {
        val annotation = mapOf(
                "indices" to """{@Index({"foo", "id"}), @Index({"bar_column", "foo"})}"""
        )
        singleEntity(
                """
                @PrimaryKey
                public int id;
                public String foo;
                @ColumnInfo(name = "bar_column")
                public String bar;
                """
                , annotation) { entity, invocation ->
            assertThat(entity.indices, `is`(
                    listOf(Index(name = "index_MyEntity_foo_id",
                            unique = false,
                            fields = fieldsByName(entity, "foo", "id")),
                            Index(name = "index_MyEntity_bar_column_foo",
                                    unique = false,
                                    fields = fieldsByName(entity, "bar", "foo")))
            ))
        }.compilesWithoutError()
    }

    @Test
    fun index_unique() {
        val annotation = mapOf(
                "indices" to """@Index(value = {"foo", "id"}, unique = true)"""
        )
        singleEntity(
                """
                @PrimaryKey
                public int id;
                public String foo;
                """
                , annotation) { entity, invocation ->
            assertThat(entity.indices, `is`(
                    listOf(Index(name = "index_MyEntity_foo_id",
                            unique = true,
                           fields = fieldsByName(entity, "foo", "id")))
            ))
        }.compilesWithoutError()
    }

    @Test
    fun index_customName() {
        val annotation = mapOf(
                "indices" to """@Index(value = {"foo"}, name = "myName")"""
        )
        singleEntity(
                """
                @PrimaryKey
                public int id;
                public String foo;
                """
                , annotation) { entity, invocation ->
            assertThat(entity.indices, `is`(
                    listOf(Index(name = "myName",
                            unique = false,
                            fields = fieldsByName(entity, "foo")))
            ))
        }.compilesWithoutError()
    }

    @Test
    fun index_customTableName() {
        val annotation = mapOf(
                "tableName" to "\"MyTable\"",
                "indices" to """@Index(value = {"foo"})"""
        )
        singleEntity(
                """
                @PrimaryKey
                public int id;
                public String foo;
                """
                , annotation) { entity, invocation ->
            assertThat(entity.indices, `is`(
                    listOf(Index(name = "index_MyTable_foo",
                            unique = false,
                            fields = fieldsByName(entity, "foo")))
            ))
        }.compilesWithoutError()
    }

    @Test
    fun index_empty() {
        val annotation = mapOf(
                "indices" to """@Index({})"""
        )
        singleEntity(
                """
                @PrimaryKey
                public int id;
                public String foo;
                """
                , annotation) { entity, invocation ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.INDEX_COLUMNS_CANNOT_BE_EMPTY
        )
    }

    @Test
    fun index_missingColumn() {
        val annotation = mapOf(
                "indices" to """@Index({"foo", "bar"})"""
        )
        singleEntity(
                """
                @PrimaryKey
                public int id;
                public String foo;
                """
                , annotation) { entity, invocation ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.indexColumnDoesNotExist("bar", listOf("id, foo"))
        )
    }

    @Test
    fun index_nameConflict() {
        val annotation = mapOf(
                "indices" to """@Index({"foo"})"""
        )
        singleEntity(
                """
                @PrimaryKey
                public int id;
                @ColumnInfo(index = true)
                public String foo;
                """
                , annotation) { entity, invocation ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.duplicateIndexInEntity("index_MyEntity_foo")
        )
    }

    @Test
    fun index_droppedParentFieldIndex() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                public class Base {
                    @PrimaryKey
                    long baseId;
                    @ColumnInfo(index = true)
                    String name;
                    String lastName;
                }
                """)
        singleEntity(
                """
                @PrimaryKey
                public int id;
                """, baseClass = "foo.bar.Base", jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.indices.isEmpty(), `is`(true))
        }.compilesWithoutError()
                .withWarningContaining(
                        ProcessorErrors.droppedSuperClassFieldIndex(
                                fieldName = "name",
                                childEntity = "foo.bar.MyEntity",
                                superEntity = "foo.bar.Base")
                )
    }

    @Test
    fun index_keptGrandParentEntityIndex() {
        val grandParent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                @Entity(indices = @Index({"name", "lastName"}))
                public class Base {
                    @PrimaryKey
                    long baseId;
                    String name, lastName;
                }
                """)
        val parent = JavaFileObjects.forSourceLines("foo.bar.Parent",
                """
                package foo.bar;
                import com.android.support.room.*;

                public class Parent extends Base {
                    String iHaveAField;
                }
                """)
        singleEntity(
                """
                @PrimaryKey
                public int id;
                """,
                baseClass = "foo.bar.Parent",
                attributes = hashMapOf("inheritSuperIndices" to "true"),
                jfos = listOf(parent, grandParent)) {
            entity, invocation ->
            assertThat(entity.indices.size, `is`(1))
            assertThat(entity.indices.first(),
                    `is`(Index(name = "index_MyEntity_name_lastName",
                            unique = false,
                            fields = fieldsByName(entity, "name", "lastName"))))
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun index_keptParentEntityIndex() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                @Entity(indices = @Index({"name", "lastName"}))
                public class Base {
                    @PrimaryKey
                    long baseId;
                    String name, lastName;
                }
                """)
        singleEntity(
                """
                @PrimaryKey
                public int id;
                """,
                baseClass = "foo.bar.Base",
                attributes = hashMapOf("inheritSuperIndices" to "true"),
                jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.indices.size, `is`(1))
            assertThat(entity.indices.first(),
                    `is`(Index(name = "index_MyEntity_name_lastName",
                            unique = false,
                            fields = fieldsByName(entity, "name", "lastName"))))
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun index_keptParentFieldIndex() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                public class Base {
                    @PrimaryKey
                    long baseId;
                    @ColumnInfo(index = true)
                    String name;
                    String lastName;
                }
                """)
        singleEntity(
                """
                @PrimaryKey
                public int id;
                """,
                baseClass = "foo.bar.Base",
                attributes = hashMapOf("inheritSuperIndices" to "true"),
                jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.indices.size, `is`(1))
            assertThat(entity.indices.first(),
                    `is`(Index(name = "index_MyEntity_name",
                            unique = false,
                            fields = fieldsByName(entity, "name"))))
        }.compilesWithoutError().withWarningCount(0)

    }

    @Test
    fun index_droppedGrandParentEntityIndex() {
        val grandParent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                @Entity(indices = @Index({"name", "lastName"}))
                public class Base {
                    @PrimaryKey
                    long baseId;
                    String name, lastName;
                }
                """)
        val parent = JavaFileObjects.forSourceLines("foo.bar.Parent",
                """
                package foo.bar;
                import com.android.support.room.*;

                public class Parent extends Base {
                    String iHaveAField;
                }
                """)
        singleEntity(
                """
                @PrimaryKey
                public int id;
                """, baseClass = "foo.bar.Parent", jfos = listOf(parent, grandParent)) {
            entity, invocation ->
            assertThat(entity.indices.isEmpty(), `is`(true))
        }.compilesWithoutError()
                .withWarningContaining(
                        ProcessorErrors.droppedSuperClassIndex(
                                childEntity = "foo.bar.MyEntity",
                                superEntity = "foo.bar.Base")
                )
    }

    @Test
    fun index_droppedParentEntityIndex() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                @Entity(indices = @Index({"name", "lastName"}))
                public class Base {
                    @PrimaryKey
                    long baseId;
                    String name, lastName;
                }
                """)
        singleEntity(
                """
                @PrimaryKey
                public int id;
                """, baseClass = "foo.bar.Base", jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.indices.isEmpty(), `is`(true))
        }.compilesWithoutError()
                .withWarningContaining(
                        ProcessorErrors.droppedSuperClassIndex(
                                childEntity = "foo.bar.MyEntity",
                                superEntity = "foo.bar.Base")
                )
    }

    @Test
    fun index_droppedDecomposedEntityIndex() {
        singleEntity(
                """
                @PrimaryKey
                public int id;
                @Decompose
                public Foo foo;
                @Entity(indices = {@Index("a")})
                static class Foo {
                    @PrimaryKey
                    @ColumnInfo(name = "foo_id")
                    int id;
                    @ColumnInfo(index = true)
                    public int a;
                }
                """) { entity, invocation ->
            assertThat(entity.indices.isEmpty(), `is`(true))
        }.compilesWithoutError()
                .withWarningContaining(
                        ProcessorErrors.droppedDecomposedIndex(
                                entityName = "foo.bar.MyEntity.Foo",
                                fieldPath = "foo",
                                grandParent = "foo.bar.MyEntity")
                )
    }

    @Test
    fun index_onDecomposedField() {
        singleEntity(
                """
                @PrimaryKey
                public int id;
                @Decompose
                @ColumnInfo(index = true)
                public Foo foo;
                static class Foo {
                    @ColumnInfo(index = true)
                    public int a;
                }
                """) { entity, invocation ->
            assertThat(entity.indices.isEmpty(), `is`(true))
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.CANNOT_USE_MORE_THAN_ONE_POJO_FIELD_ANNOTATION
        )
    }

    @Test
    fun index_droppedDecomposedFieldIndex() {
        singleEntity(
                """
                @PrimaryKey
                public int id;
                @Decompose
                public Foo foo;
                static class Foo {
                    @ColumnInfo(index = true)
                    public int a;
                }
                """) { entity, invocation ->
            assertThat(entity.indices.isEmpty(), `is`(true))
        }.compilesWithoutError()
                .withWarningContaining(
                        ProcessorErrors.droppedDecomposedFieldIndex("foo > a", "foo.bar.MyEntity")
                )
    }

    @Test
    fun index_referenceDecomposedField() {
        singleEntity(
                """
                @PrimaryKey
                public int id;
                @Decompose
                public Foo foo;
                static class Foo {
                    public int a;
                }
                """, attributes = mapOf("indices" to "@Index(\"a\")")) { entity, invocation ->
            assertThat(entity.indices.size, `is`(1))
            assertThat(entity.indices.first(), `is`(
                    Index(
                            name = "index_MyEntity_a",
                            unique = false,
                            fields = fieldsByName(entity, "a")
                    )
            ))
        }.compilesWithoutError()
    }

    @Test
    fun primaryKey_definedInBothWays() {
        singleEntity(
                """
                public int id;
                @PrimaryKey
                public String foo;
                """,
                attributes = mapOf("primaryKeys" to "\"id\"")) { entity, invocation ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.multiplePrimaryKeyAnnotations(
                        listOf("PrimaryKey[id]", "PrimaryKey[foo]")
                ))
    }

    @Test
    fun primaryKey_badColumnName() {
        singleEntity(
                """
                public int id;
                """,
                attributes = mapOf("primaryKeys" to "\"foo\"")) { entity, invocation ->
        }.failsToCompile().withErrorContaining(
                ProcessorErrors.primaryKeyColumnDoesNotExist("foo", listOf("id")))
    }

    @Test
    fun primaryKey_multipleAnnotations() {
        singleEntity("""
                @PrimaryKey
                int x;
                @PrimaryKey
                int y;
                """) { entity, invocation ->
            assertThat(entity.primaryKey.fields.isEmpty(), `is`(true))
        }.failsToCompile()
                .withErrorContaining(
                        ProcessorErrors.multiplePrimaryKeyAnnotations(
                                listOf("PrimaryKey[x]", "PrimaryKey[y]")))
    }

    @Test
    fun primaryKey_fromParentField() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                public class Base {
                    @PrimaryKey
                    long baseId;
                    String name, lastName;
                }
                """)
        singleEntity(
                """
                public int id;
                """,
                baseClass = "foo.bar.Base",
                jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.primaryKey.fields.firstOrNull()?.name, `is`("baseId"))

        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun primaryKey_fromParentEntity() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                @Entity(primaryKeys = "baseId")
                public class Base {
                    long baseId;
                    String name, lastName;
                }
                """)
        singleEntity(
                """
                public int id;
                """,
                baseClass = "foo.bar.Base",
                jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.primaryKey.fields.firstOrNull()?.name, `is`("baseId"))
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun primaryKey_overrideFromParentField() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                public class Base {
                    @PrimaryKey
                    long baseId;
                    String name, lastName;
                }
                """)
        singleEntity(
                """
                @PrimaryKey
                public int id;
                """,
                baseClass = "foo.bar.Base",
                jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.primaryKey.fields.size, `is`(1))
            assertThat(entity.primaryKey.fields.firstOrNull()?.name, `is`("id"))
            assertThat(entity.primaryKey.autoGenerateId, `is`(false))
        }.compilesWithoutError().withNoteContaining(
                "PrimaryKey[baseId] is overridden by PrimaryKey[id]"
        )
    }

    @Test
    fun primaryKey_overrideFromParentEntityViaField() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                @Entity(primaryKeys = "baseId")
                public class Base {
                    long baseId;
                    String name, lastName;
                }
                """)
        singleEntity(
                """
                @PrimaryKey
                public int id;
                """,
                baseClass = "foo.bar.Base",
                jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.primaryKey.fields.size, `is`(1))
            assertThat(entity.primaryKey.fields.firstOrNull()?.name, `is`("id"))
        }.compilesWithoutError().withNoteContaining(
                "PrimaryKey[baseId] is overridden by PrimaryKey[id]"
        )
    }

    @Test
    fun primaryKey_overrideFromParentEntityViaEntity() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;
                @Entity(primaryKeys = "baseId")
                public class Base {
                    long baseId;
                    String name, lastName;
                }
                """)
        singleEntity(
                """
                public int id;
                """,
                baseClass = "foo.bar.Base",
                jfos = listOf(parent),
                attributes = mapOf("primaryKeys" to "\"id\"")) { entity, invocation ->
            assertThat(entity.primaryKey.fields.size, `is`(1))
            assertThat(entity.primaryKey.fields.firstOrNull()?.name, `is`("id"))
            assertThat(entity.primaryKey.autoGenerateId, `is`(false))
        }.compilesWithoutError().withNoteContaining(
                "PrimaryKey[baseId] is overridden by PrimaryKey[id]"
        )
    }

    @Test
    fun primaryKey_autoGenerate() {
        listOf("long", "Long", "Integer", "int").forEach { type ->
            singleEntity(
                    """
                @PrimaryKey(autoGenerate = true)
                public $type id;
                """) { entity, invocation ->
                assertThat(entity.primaryKey.fields.size, `is`(1))
                assertThat(entity.primaryKey.fields.firstOrNull()?.name, `is`("id"))
                assertThat(entity.primaryKey.autoGenerateId, `is`(true))
            }.compilesWithoutError()
        }
    }

    @Test
    fun primaryKey_autoGenerateBadType() {
        listOf("String", "float", "Float", "Double", "double").forEach { type ->
            singleEntity(
                    """
                @PrimaryKey(autoGenerate = true)
                public $type id;
                """) { entity, invocation ->
                assertThat(entity.primaryKey.fields.size, `is`(1))
                assertThat(entity.primaryKey.fields.firstOrNull()?.name, `is`("id"))
                assertThat(entity.primaryKey.autoGenerateId, `is`(true))
            }.failsToCompile().withErrorContaining(
                    ProcessorErrors.AUTO_INCREMENTED_PRIMARY_KEY_IS_NOT_INT)
        }
    }

    @Test
    fun primaryKey_decomposed(){
        singleEntity(
                """
                public int id;

                @Decompose(prefix = "bar_")
                @PrimaryKey
                public Foo foo;

                static class Foo {
                    public int a;
                    public int b;
                }
                """) { entity, invocation ->
            assertThat(entity.primaryKey.fields.map { it.columnName },
                    `is`(listOf("bar_a", "bar_b")))
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun primaryKey_decomposedInherited(){
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;

                public class Base {
                    long baseId;
                    String name, lastName;
                    @Decompose(prefix = "bar_")
                    @PrimaryKey
                    public Foo foo;

                    static class Foo {
                        public int a;
                        public int b;
                    }
                }
                """)
        singleEntity(
                """
                public int id;
                """,
                baseClass = "foo.bar.Base",
                jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.primaryKey.fields.map { it.columnName },
                    `is`(listOf("bar_a", "bar_b")))
        }.compilesWithoutError().withWarningCount(0)
    }

    @Test
    fun primaryKey_overrideViaDecomposed() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;

                @Entity(primaryKeys = "baseId")
                public class Base {
                    long baseId;
                    String name, lastName;
                }
                """)
        singleEntity(
                """
                public int id;
                @Decompose(prefix = "bar_")
                @PrimaryKey
                public Foo foo;

                static class Foo {
                    public int a;
                    public int b;
                }
                """,
                baseClass = "foo.bar.Base",
                jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.primaryKey.fields.map { it.columnName },
                    `is`(listOf("bar_a", "bar_b")))
        }.compilesWithoutError().withNoteContaining(
                "PrimaryKey[baseId] is overridden by PrimaryKey[foo > a, foo > b]")
    }

    @Test
    fun primaryKey_overrideDecomposed() {
        val parent = JavaFileObjects.forSourceLines("foo.bar.Base",
                """
                package foo.bar;
                import com.android.support.room.*;

                public class Base {
                    long baseId;
                    String name, lastName;
                    @Decompose(prefix = "bar_")
                    @PrimaryKey
                    public Foo foo;

                    static class Foo {
                        public int a;
                        public int b;
                    }
                }
                """)
        singleEntity(
                """
                @PrimaryKey
                public int id;
                """,
                baseClass = "foo.bar.Base",
                jfos = listOf(parent)) { entity, invocation ->
            assertThat(entity.primaryKey.fields.map { it.columnName },
                    `is`(listOf("id")))
        }.compilesWithoutError().withNoteContaining(
                "PrimaryKey[foo > a, foo > b] is overridden by PrimaryKey[id]")
    }

    @Test
    fun relationInEntity() {
        singleEntity(
                """
                @PrimaryKey
                int id;
                @Relation(parentColumn = "id", entityColumn = "uid")
                java.util.List<User> users;
                """, jfos = listOf(COMMON.USER)
        ) { entity, invocation ->
        }.failsToCompile().withErrorContaining(RELATION_IN_ENTITY)
    }

    @Test
    fun foreignKey_invalidAction() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = "lastName",
                    childColumns = "name",
                    onDelete = 101
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ){ entity, invocation ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.INVALID_FOREIGN_KEY_ACTION)
    }

    @Test
    fun foreignKey_badEntity() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = dsa.class,
                    parentColumns = "lastName",
                    childColumns = "name"
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ){ entity, invocation ->
        }.failsToCompile().withErrorContaining("cannot find symbol")
    }

    @Test
    fun foreignKey_notAnEntity() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.NOT_AN_ENTITY_TYPE_NAME}.class,
                    parentColumns = "lastName",
                    childColumns = "name"
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.NOT_AN_ENTITY)
        ){ entity, invocation ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.foreignKeyNotAnEntity(
                COMMON.NOT_AN_ENTITY_TYPE_NAME.toString()))
    }

    @Test
    fun foreignKey_invalidChildColumn() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = "lastName",
                    childColumns = "namex"
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ){ entity, invocation ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.foreignKeyChildColumnDoesNotExist(
                "namex", listOf("id", "name")))
    }

    @Test
    fun foreignKey_columnCountMismatch() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = "lastName",
                    childColumns = {"name", "id"}
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ){ entity, invocation ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.foreignKeyColumnNumberMismatch(
                listOf("name", "id"), listOf("lastName")))
    }

    @Test
    fun foreignKey_emptyChildColumns() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = "lastName",
                    childColumns = {}
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ){ entity, invocation ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.FOREIGN_KEY_EMPTY_CHILD_COLUMN_LIST)
    }

    @Test
    fun foreignKey_emptyParentColumns() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = {},
                    childColumns = {"name"}
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ){ entity, invocation ->
        }.failsToCompile().withErrorContaining(ProcessorErrors.FOREIGN_KEY_EMPTY_PARENT_COLUMN_LIST)
    }

    @Test
    fun foreignKey_simple() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = "lastName",
                    childColumns = "name",
                    onDelete = ForeignKey.SET_NULL,
                    onUpdate = ForeignKey.CASCADE,
                    deferred = true
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ){ entity, invocation ->
            assertThat(entity.foreignKeys.size, `is`(1))
            val fKey = entity.foreignKeys.first()
            assertThat(fKey.parentTable, `is`("User"))
            assertThat(fKey.parentColumns, `is`(listOf("lastName")))
            assertThat(fKey.deferred, `is`(true))
            assertThat(fKey.childFields.size, `is`(1))
            val field = fKey.childFields.first()
            assertThat(field.name, `is`("name"))
        }.compilesWithoutError()
    }

    @Test
    fun foreignKey_dontDuplicationChildIndex_SingleColumn() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = "lastName",
                    childColumns = "name",
                    onDelete = ForeignKey.SET_NULL,
                    onUpdate = ForeignKey.CASCADE,
                    deferred = true
                )}""".trimIndent(),
                "indices" to """@Index("name")"""
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ) { entity, invocation ->
        }.compilesWithoutWarnings()
    }

    @Test
    fun foreignKey_dontDuplicationChildIndex_MultipleColumns() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = {"lastName", "name"},
                    childColumns = {"lName", "name"},
                    onDelete = ForeignKey.SET_NULL,
                    onUpdate = ForeignKey.CASCADE,
                    deferred = true
                )}""".trimIndent(),
                "indices" to """@Index({"lName", "name"})"""
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                String lName;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ) { entity, invocation ->
            assertThat(entity.indices.size, `is`(1))
        }.compilesWithoutWarnings()
    }

    @Test
    fun foreignKey_dontDuplicationChildIndex_WhenCovered() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = {"lastName"},
                    childColumns = {"name"},
                    onDelete = ForeignKey.SET_NULL,
                    onUpdate = ForeignKey.CASCADE,
                    deferred = true
                )}""".trimIndent(),
                "indices" to """@Index({"name", "lName"})"""
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                String lName;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ) { entity, invocation ->
            assertThat(entity.indices.size, `is`(1))
        }.compilesWithoutWarnings()
    }

    @Test
    fun foreignKey_warnMissingChildIndex() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = "lastName",
                    childColumns = "name",
                    onDelete = ForeignKey.SET_NULL,
                    onUpdate = ForeignKey.CASCADE,
                    deferred = true
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ){ entity, invocation ->
            assertThat(entity.indices, `is`(emptyList()))
        }.compilesWithoutError().withWarningContaining(
                ProcessorErrors.foreignKeyMissingIndexInChildColumn("name"))
    }

    @Test
    fun foreignKey_warnMissingChildrenIndex() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = {"lastName", "name"},
                    childColumns = {"lName", "name"}
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                String lName;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ){ entity, invocation ->
            assertThat(entity.indices, `is`(emptyList()))
        }.compilesWithoutError().withWarningContaining(
                ProcessorErrors.foreignKeyMissingIndexInChildColumns(listOf("lName", "name")))
    }

    @Test
    fun foreignKey_dontIndexIfAlreadyPrimaryKey() {
        val annotation = mapOf(
                "foreignKeys" to """{@ForeignKey(
                    entity = ${COMMON.USER_TYPE_NAME}.class,
                    parentColumns = "lastName",
                    childColumns = "id",
                    onDelete = ForeignKey.SET_NULL,
                    onUpdate = ForeignKey.CASCADE,
                    deferred = true
                )}""".trimIndent()
        )
        singleEntity(
                """
                @PrimaryKey
                int id;
                String name;
                """,
                attributes = annotation, jfos = listOf(COMMON.USER)
        ){ entity, invocation ->
            assertThat(entity.indices, `is`(emptyList()))
        }.compilesWithoutWarnings()
    }
}

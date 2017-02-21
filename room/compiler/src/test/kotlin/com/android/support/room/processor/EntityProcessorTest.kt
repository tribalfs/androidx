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

import com.android.support.room.parser.SQLTypeAffinity
import com.android.support.room.vo.CallType
import com.android.support.room.vo.Field
import com.android.support.room.vo.FieldGetter
import com.android.support.room.vo.FieldSetter
import com.android.support.room.vo.Index
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
                    primaryKey = true,
                    columnName = "id",
                    affinity = SQLTypeAffinity.INTEGER)))
            assertThat(field.setter, `is`(FieldSetter("setId", intType, CallType.METHOD)))
            assertThat(field.getter, `is`(FieldGetter("getId", intType, CallType.METHOD)))
            assertThat(entity.primaryKeys, `is`(listOf(field)))
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
    fun multiplePrimaryKeys() {
        singleEntity("""
                @PrimaryKey
                int x;
                @PrimaryKey
                int y;
                """) { entity, invocation ->
            assertThat(entity.primaryKeys.size, `is`(2))
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
            assertThat(entity.fields.find { it.name == "x" }!!.primaryKey, `is`(false))
            assertThat(entity.fields.filter { it.primaryKey }.map { it.name }, `is`(listOf("id")))
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
            assertThat(entity.fields.find { it.name == "x" }!!.primaryKey, `is`(false))
            assertThat(entity.fields.filter { it.primaryKey }.map { it.name }, `is`(listOf("id")))
        }.compilesWithoutError().withWarningCount(0)
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
                            columnNames = listOf("foo")))
            ))
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
                            columnNames = listOf("foo")))
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
                            columnNames = listOf("foo", "id")))
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
                            columnNames = listOf("foo", "id")),
                            Index(name = "index_MyEntity_bar_column_foo",
                                    unique = false,
                                    columnNames = listOf("bar_column", "foo")))
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
                            columnNames = listOf("foo", "id")))
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
                            columnNames = listOf("foo")))
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
                            columnNames = listOf("foo")))
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
                            columnNames = listOf("name", "lastName"))))
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
                            columnNames = listOf("name", "lastName"))))
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
                            columnNames = listOf("name"))))
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
                ProcessorErrors.FIELD_WITH_DECOMPOSE_AND_COLUMN_INFO
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
}

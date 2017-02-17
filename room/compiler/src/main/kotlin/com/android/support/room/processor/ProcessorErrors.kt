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

import com.android.support.room.Delete
import com.android.support.room.Insert
import com.android.support.room.Query
import com.android.support.room.Update
import com.android.support.room.ext.RoomTypeNames
import com.android.support.room.vo.CustomTypeConverter
import com.android.support.room.vo.Field
import com.squareup.javapoet.TypeName
import javax.lang.model.element.Element

object ProcessorErrors {
    val MISSING_QUERY_ANNOTATION = "Query methods must be annotated with ${Query::class.java}"
    val MISSING_INSERT_ANNOTATION = "Insertion methods must be annotated with ${Insert::class.java}"
    val MISSING_DELETE_ANNOTATION = "Deletion methods must be annotated with ${Delete::class.java}"
    val MISSING_UPDATE_ANNOTATION = "Update methods must be annotated with ${Update::class.java}"
    val INVALID_ON_CONFLICT_VALUE = "On conflict value must be one of @OnConflictStrategy values."
    val INVALID_INSERTION_METHOD_RETURN_TYPE = "Methods annotated with @Insert can return either" +
            " void, long, long[] or List<Long>."
    val ABSTRACT_METHOD_IN_DAO_MISSING_ANY_ANNOTATION = "Abstract method in DAO must be annotated" +
            " with ${Query::class.java} AND ${Insert::class.java}"
    val CANNOT_USE_MORE_THAN_ONE_DAO_METHOD_ANNOTATION = "A DAO method can be annotated with only" +
            " one of the following:" + DaoProcessor.PROCESSED_ANNOTATIONS.joinToString(",") {
        it.java.simpleName
    }
    val CANNOT_RESOLVE_RETURN_TYPE = "Cannot resolve return type for %s"
    val CANNOT_USE_UNBOUND_GENERICS_IN_QUERY_METHODS = "Cannot use unbound generics in query" +
            " methods. It must be bound to a type through base Dao class."
    val CANNOT_USE_UNBOUND_GENERICS_IN_INSERTION_METHODS = "Cannot use unbound generics in" +
            " insertion methods. It must be bound to a type through base Dao class."
    val CANNOT_USE_UNBOUND_GENERICS_IN_ENTITY_FIELDS = "Cannot use unbound fields in entities."
    val CANNOT_USE_UNBOUND_GENERICS_IN_DAO_CLASSES = "Cannot use unbound generics in Dao classes." +
            " If you are trying to create a base DAO, create a normal class, extend it with type" +
            " params then mark the subclass with @Dao."
    val CANNOT_FIND_GETTER_FOR_FIELD = "Cannot find getter for field."
    val CANNOT_FIND_SETTER_FOR_FIELD = "Cannot find setter for field."
    val MISSING_PRIMARY_KEY = "An entity must have at least 1 field annotated with @PrimaryKey"
    val DAO_MUST_BE_AN_ABSTRACT_CLASS_OR_AN_INTERFACE = "Dao class must be an abstract class or" +
            " an interface"
    val DATABASE_MUST_BE_ANNOTATED_WITH_DATABASE = "Database must be annotated with @Database"
    val DAO_MUST_BE_ANNOTATED_WITH_DAO = "Dao class must be annotated with @Dao"
    val ENTITY_MUST_BE_ANNOTATED_WITH_ENTITY = "Entity class must be annotated with @Entity"
    val DATABASE_ANNOTATION_MUST_HAVE_LIST_OF_ENTITIES = "@Database annotation must specify list" +
            " of entities"
    val COLUMN_NAME_CANNOT_BE_EMPTY = "Column name cannot be blank. If you don't want to set it" +
            ", just remove the @ColumnInfo annotation or use @ColumnInfo.INHERIT_FIELD_NAME."

    val ENTITY_TABLE_NAME_CANNOT_BE_EMPTY = "Entity table name cannot be blank. If you don't want" +
            " to set it, just remove the tableName property."

    val CANNOT_BIND_QUERY_PARAMETER_INTO_STMT = "Query method parameters should either be a" +
            " type that can be converted into a database column or a List / Array that contains" +
            " such type. You can consider adding a Type Adapter for this."

    val QUERY_PARAMETERS_CANNOT_START_WITH_UNDERSCORE = "Query/Insert method parameters cannot " +
            "start with underscore (_)."

    val CANNOT_FIND_QUERY_RESULT_ADAPTER = "Not sure how to convert a Cursor to this method's " +
            "return type"

    val INSERTION_DOES_NOT_HAVE_ANY_PARAMETERS_TO_INSERT = "Method annotated with" +
            " @Insert but does not have any parameters to insert."

    val INSERTION_METHOD_PARAMETERS_MUST_HAVE_THE_SAME_ENTITY_TYPE = "Parameter types in " +
            "insertion methods must be the same type. If you want to insert entities from " +
            "different types atomically, use a transaction."

    val DELETION_MISSING_PARAMS = "Method annotated with" +
            " @Delete but does not have any parameters to delete."

    val DELETION_MULTIPLE_ENTITY_TYPES = "Parameter types in " +
            "deletion methods must be the same type. If you want to delete entities from " +
            "different types atomically, use a transaction."

    val UPDATE_MISSING_PARAMS = "Method annotated with" +
            " @Update but does not have any parameters to update."

    val UPDATE_MULTIPLE_ENTITY_TYPES = "Parameter types in " +
            "update methods must be the same type. If you want to update entities from " +
            "different types atomically, use a transaction."

    val CANNOT_FIND_ENTITY_FOR_SHORTCUT_QUERY_PARAMETER = "Type of the parameter must be a class " +
            "annotated with @Entity or a collection/array of it."

    val DB_MUST_EXTEND_ROOM_DB = "Classes annotated with @Database should extend " +
            RoomTypeNames.ROOM_DB

    val LIVE_DATA_QUERY_WITHOUT_SELECT = "LiveData return type can only be used with SELECT" +
            " queries."

    private val TOO_MANY_MATCHING_GETTERS = "Ambiguous getter for %s. All of the following " +
            "match: %s. You can @Ignore the ones that you don't want to match."

    fun tooManyMatchingGetters(field: Field, methodNames: List<String>): String {
        return TOO_MANY_MATCHING_GETTERS.format(field, methodNames.joinToString(", "))
    }

    private val TOO_MANY_MATCHING_SETTERS = "Ambiguous setter for %s. All of the following " +
            "match: %s. You can @Ignore the ones that you don't want to match."

    fun tooManyMatchingSetter(field: Field, methodNames: List<String>): String {
        return TOO_MANY_MATCHING_SETTERS.format(field, methodNames.joinToString(", "))
    }

    val CANNOT_FIND_COLUMN_TYPE_ADAPTER = "Cannot figure out how to save this field into" +
            " database. You can consider adding a type converter for it."

    val CANNOT_FIND_STMT_BINDER = "Cannot figure out how to bind this field into a statement."

    val CANNOT_FIND_CURSOR_READER = "Cannot figure out how to read this field from a cursor."

    private val MISSING_PARAMETER_FOR_BIND = "Each bind variable in the query must have a" +
            " matching method parameter. Cannot find method parameters for %s."

    fun missingParameterForBindVariable(bindVarName: List<String>): String {
        return MISSING_PARAMETER_FOR_BIND.format(bindVarName.joinToString(", "))
    }

    private val UNUSED_QUERY_METHOD_PARAMETER = "Unused parameter%s: %s"
    fun unusedQueryMethodParameter(unusedParams: List<String>): String {
        return UNUSED_QUERY_METHOD_PARAMETER.format(
                if (unusedParams.size > 1) "s" else "",
                unusedParams.joinToString(","))
    }

    private val DUPLICATE_TABLES = "Table name \"%s\" is used by multiple entities: %s"
    fun duplicateTableNames(tableName: String, entityNames: List<String>): String {
        return DUPLICATE_TABLES.format(tableName, entityNames.joinToString(", "))
    }

    val DELETION_METHODS_MUST_RETURN_VOID_OR_INT = "Deletion methods must either return void or" +
            " return int (the number of deleted rows)."

    val UPDATE_METHODS_MUST_RETURN_VOID_OR_INT = "Update methods must either return void or" +
            " return int (the number of updated rows)."

    val DAO_METHOD_CONFLICTS_WITH_OTHERS = "Dao method has conflicts."

    fun duplicateDao(dao: TypeName, methodNames: List<String>): String {
        return """
                All of these functions [${methodNames.joinToString(", ")}] return the same DAO
                class [$dao].
                A database can use a DAO only once so you should remove ${methodNames.size - 1} of
                these conflicting DAO methods. If you are implementing any of these to fulfill an
                interface, don't make it abstract, instead, implement the code that calls the
                other one.
                """.trimIndent().replace(System.lineSeparator(), " ")
    }

    fun cursorPojoMismatch(pojoTypeName: TypeName,
                           unusedColumns: List<String>, allColumns: List<String>,
                           unusedFields: List<Field>, allFields: List<Field>): String {
        val unusedColumnsWarning = if (unusedColumns.isNotEmpty()) { """
                The query returns some columns [${unusedColumns.joinToString(", ")}] which are not
                use by $pojoTypeName. You can use @ColumnInfo annotation on the fields to specify
                the mapping.
            """.trimIndent().replace(System.lineSeparator(), " ")
        } else {
            ""
        }
        val unusedFieldsWarning = if (unusedFields.isNotEmpty()) { """
                $pojoTypeName has some fields
                [${unusedFields.joinToString(", ") { it.columnName }}] which are not returned by the
                query. If they are not supposed to be read from the result, you can mark them with
                @Ignore annotation.
            """.trimIndent().replace(System.lineSeparator(), " ")
        } else {
            ""
        }
        return """
            $unusedColumnsWarning
            $unusedFieldsWarning
            You can suppress this warning by annotating the method with
            @SuppressWarnings(RoomWarnings.CURSOR_MISMATCH).
            Columns returned by the query: ${allColumns.joinToString(", ")}.
            Fields in $pojoTypeName: ${allFields.joinToString(", ") { it.columnName }}.
            """.trimIndent().replace(System.lineSeparator(), " ")
    }

    val TYPE_CONVERTER_UNBOUND_GENERIC = "Cannot use unbound generics in Type Converters."
    val TYPE_CONVERTER_BAD_RETURN_TYPE = "Invalid return type for a type converter."
    val TYPE_CONVERTER_MUST_RECEIVE_1_PARAM = "Type converters must receive 1 parameter."
    val TYPE_CONVERTER_EMPTY_CLASS = "Class is referenced as a converter but it does not have any" +
            " converter methods."
    val TYPE_CONVERTER_MISSING_NOARG_CONSTRUCTOR = "Classes that are used as TypeConverters must" +
            " have no-argument public constructors."
    val TYPE_CONVERTER_MUST_BE_PUBLIC = "Type converters must be public."

    fun duplicateTypeConverters(converters : List<CustomTypeConverter>) : String {
        return "Multiple methods define the same conversion. Conflicts with these:" +
                " ${converters.joinToString(", ") { it.toString() }}"
    }

    // TODO must print field paths.
    val POJO_FIELD_HAS_DUPLICATE_COLUMN_NAME = "Field has non-unique column name."

    fun pojoDuplicateFieldNames(columnName : String, fieldPaths : List<String>) : String {
        return "Multiple fields have the same columnName: $columnName." +
                " Field names: ${fieldPaths.joinToString(", ")}."
    }

    fun decomposedPrimaryKeyIsDropped(entityQName: String, fieldName : String) : String {
        return "Primary key constraint on $fieldName is ignored when being merged into " +
                entityQName
    }
}

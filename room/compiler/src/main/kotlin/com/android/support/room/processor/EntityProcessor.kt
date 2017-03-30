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

import com.android.support.room.ext.getAsBoolean
import com.android.support.room.ext.getAsInt
import com.android.support.room.ext.getAsString
import com.android.support.room.ext.getAsStringList
import com.android.support.room.ext.toType
import com.android.support.room.parser.SQLTypeAffinity
import com.android.support.room.processor.ProcessorErrors.INDEX_COLUMNS_CANNOT_BE_EMPTY
import com.android.support.room.processor.ProcessorErrors.RELATION_IN_ENTITY
import com.android.support.room.vo.DecomposedField
import com.android.support.room.vo.Entity
import com.android.support.room.vo.Field
import com.android.support.room.vo.ForeignKey
import com.android.support.room.vo.ForeignKeyAction
import com.android.support.room.vo.Index
import com.android.support.room.vo.Pojo
import com.android.support.room.vo.PrimaryKey
import com.android.support.room.vo.Warning
import com.google.auto.common.AnnotationMirrors
import com.google.auto.common.AnnotationMirrors.getAnnotationValue
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.AnnotationValue
import javax.lang.model.element.TypeElement
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.SimpleAnnotationValueVisitor6

class EntityProcessor(baseContext: Context, val element: TypeElement) {
    val context = baseContext.fork(element)

    fun process(): Entity {
        context.checker.hasAnnotation(element, com.android.support.room.Entity::class,
                ProcessorErrors.ENTITY_MUST_BE_ANNOTATED_WITH_ENTITY)
        val pojo = PojoProcessor(
                baseContext = context,
                element = element,
                bindingScope = FieldProcessor.BindingScope.TWO_WAY,
                parent = null).process()
        context.checker.check(pojo.relations.isEmpty(), element, RELATION_IN_ENTITY)
        val annotation = MoreElements.getAnnotationMirror(element,
                com.android.support.room.Entity::class.java).orNull()
        val tableName: String
        val entityIndices: List<IndexInput>
        val foreignKeyInputs: List<ForeignKeyInput>
        val inheritSuperIndices: Boolean
        if (annotation != null) {
            tableName = extractTableName(element, annotation)
            entityIndices = extractIndices(annotation, tableName)
            inheritSuperIndices = AnnotationMirrors
                    .getAnnotationValue(annotation, "inheritSuperIndices").getAsBoolean(false)
            foreignKeyInputs = extractForeignKeys(annotation)
        } else {
            tableName = element.simpleName.toString()
            foreignKeyInputs = emptyList()
            entityIndices = emptyList()
            inheritSuperIndices = false
        }
        context.checker.notBlank(tableName, element,
                ProcessorErrors.ENTITY_TABLE_NAME_CANNOT_BE_EMPTY)

        val fieldIndices = pojo.fields
                .filter { it.indexed }
                .map {
                    if (it.parent != null) {
                        it.indexed = false
                        context.logger.w(Warning.INDEX_FROM_DECOMPOSED_FIELD_IS_DROPPED, it.element,
                                ProcessorErrors.droppedDecomposedFieldIndex(
                                        it.getPath(), element.qualifiedName.toString()))
                        null
                    } else if (it.element.enclosingElement != element && !inheritSuperIndices) {
                        it.indexed = false
                        context.logger.w(Warning.INDEX_FROM_PARENT_FIELD_IS_DROPPED,
                                ProcessorErrors.droppedSuperClassFieldIndex(
                                        it.columnName, element.toString(),
                                        it.element.enclosingElement.toString()
                                ))
                        null
                    } else {
                        IndexInput(
                                name = createIndexName(listOf(it.columnName), tableName),
                                unique = false,
                                columnNames = listOf(it.columnName)
                        )
                    }
                }.filterNotNull()
        val superIndices = loadSuperIndices(element.superclass, tableName, inheritSuperIndices)
        val indexInputs = entityIndices + fieldIndices + superIndices
        val indices = validateAndCreateIndices(indexInputs, pojo)

        val primaryKey = findPrimaryKey(pojo.fields, pojo.decomposedFields)
        val affinity = primaryKey.fields.firstOrNull()?.affinity ?: SQLTypeAffinity.TEXT
        context.checker.check(
                !primaryKey.autoGenerateId || affinity == SQLTypeAffinity.INTEGER,
                primaryKey.fields.firstOrNull()?.element ?: element,
                ProcessorErrors.AUTO_INCREMENTED_PRIMARY_KEY_IS_NOT_INT
        )

        val entityForeignKeys = validateAndCreateForeignKeyReferences(foreignKeyInputs, pojo)
        checkIndicesForForeignKeys(entityForeignKeys, primaryKey, indices)

        val entity = Entity(element = element,
                tableName = tableName,
                type = pojo.type,
                fields = pojo.fields,
                decomposedFields = pojo.decomposedFields,
                indices = indices,
                primaryKey = primaryKey,
                foreignKeys = entityForeignKeys)

        return entity
    }

    private fun checkIndicesForForeignKeys(entityForeignKeys: List<ForeignKey>,
                                           primaryKey: PrimaryKey,
                                           indices: List<Index>) {
        fun covers(columnNames: List<String>, fields : List<Field>) : Boolean =
            fields.size >= columnNames.size && columnNames.withIndex().all {
                fields[it.index].columnName == it.value
            }

        entityForeignKeys.forEach { fKey ->
            val columnNames = fKey.childFields.map { it.columnName }
            val exists = covers(columnNames, primaryKey.fields) || indices.any { index ->
                covers(columnNames, index.fields)
            }
            if (!exists) {
                if (columnNames.size == 1) {
                    context.logger.w(Warning.MISSING_INDEX_ON_FOREIGN_KEY_CHILD, element,
                            ProcessorErrors.foreignKeyMissingIndexInChildColumn(columnNames[0]))
                } else {
                    context.logger.w(Warning.MISSING_INDEX_ON_FOREIGN_KEY_CHILD, element,
                            ProcessorErrors.foreignKeyMissingIndexInChildColumns(columnNames))
                }
            }
        }
    }

    /**
     * Does a validation on foreign keys except the parent table's columns.
     */
    private fun validateAndCreateForeignKeyReferences(foreignKeyInputs: List<ForeignKeyInput>,
                                                      pojo: Pojo): List<ForeignKey> {
        return foreignKeyInputs.map {
            if (it.onUpdate == null) {
                context.logger.e(element, ProcessorErrors.INVALID_FOREIGN_KEY_ACTION)
                return@map null
            }
            if (it.onDelete == null) {
                context.logger.e(element, ProcessorErrors.INVALID_FOREIGN_KEY_ACTION)
                return@map null
            }
            if (it.childColumns.isEmpty()) {
                context.logger.e(element, ProcessorErrors.FOREIGN_KEY_EMPTY_CHILD_COLUMN_LIST)
                return@map null
            }
            if (it.parentColumns.isEmpty()) {
                context.logger.e(element, ProcessorErrors.FOREIGN_KEY_EMPTY_PARENT_COLUMN_LIST)
                return@map null
            }
            if (it.childColumns.size != it.parentColumns.size) {
                context.logger.e(element, ProcessorErrors.foreignKeyColumnNumberMismatch(
                        it.childColumns, it.parentColumns
                ))
                return@map null
            }
            val parentElement = try {
                MoreTypes.asElement(it.parent) as TypeElement
            } catch (noClass: IllegalArgumentException) {
                context.logger.e(element, ProcessorErrors.FOREIGN_KEY_CANNOT_FIND_PARENT)
                return@map null
            }
            val parentAnnotation = MoreElements.getAnnotationMirror(parentElement,
                    com.android.support.room.Entity::class.java).orNull()
            if (parentAnnotation == null) {
                context.logger.e(element,
                        ProcessorErrors.foreignKeyNotAnEntity(parentElement.toString()))
                return@map null
            }
            val tableName = extractTableName(parentElement, parentAnnotation)
            val fields = it.childColumns.map { columnName ->
                val field = pojo.fields.find { it.columnName == columnName }
                if (field == null) {
                    context.logger.e(pojo.element,
                            ProcessorErrors.foreignKeyChildColumnDoesNotExist(columnName,
                                    pojo.fields.map { it.columnName }))
                }
                field
            }.filterNotNull()
            if (fields.size != it.childColumns.size) {
                return@map null
            }
            ForeignKey(
                    parentTable = tableName,
                    childFields = fields,
                    parentColumns = it.parentColumns,
                    onDelete = it.onDelete,
                    onUpdate = it.onUpdate,
                    deferred = it.deferred
            )
        }.filterNotNull()
    }

    private fun findPrimaryKey(fields: List<Field>, decomposedFields: List<DecomposedField>)
            : PrimaryKey {
        val candidates = collectPrimaryKeysFromEntityAnnotations(element, fields) +
                collectPrimaryKeysFromPrimaryKeyAnnotations(fields) +
                collectPrimaryKeysFromDecomposedFields(decomposedFields)

        context.checker.check(candidates.isNotEmpty(), element, ProcessorErrors.MISSING_PRIMARY_KEY)
        if (candidates.size == 1) {
            // easy :)
            return candidates.first()
        }

        return choosePrimaryKey(candidates, element)
    }

    /**
     * Check fields for @PrimaryKey.
     */
    private fun collectPrimaryKeysFromPrimaryKeyAnnotations(fields: List<Field>): List<PrimaryKey> {
        return fields.map { field ->
            MoreElements.getAnnotationMirror(field.element,
                    com.android.support.room.PrimaryKey::class.java).orNull()?.let {
                if (field.parent != null) {
                    // the field in the entity that contains this error.
                    val grandParentField = field.parent.rootParent.field.element
                    // bound for entity.
                    context.fork(grandParentField).logger.w(
                            Warning.PRIMARY_KEY_FROM_DECOMPOSED_IS_DROPPED,
                            grandParentField,
                            ProcessorErrors.decomposedPrimaryKeyIsDropped(
                                    element.qualifiedName.toString(), field.name))
                    null
                } else {
                    PrimaryKey(declaredIn = field.element.enclosingElement,
                            fields = listOf(field),
                            autoGenerateId = AnnotationMirrors
                                    .getAnnotationValue(it, "autoGenerate")
                                    .getAsBoolean(false))
                }
            }
        }.filterNotNull()
    }

    /**
     * Check classes for @Entity(primaryKeys = ?).
     */
    private fun collectPrimaryKeysFromEntityAnnotations(typeElement: TypeElement,
                                                        availableFields: List<Field>)
            : List<PrimaryKey> {
        val myPkeys = MoreElements.getAnnotationMirror(typeElement,
                com.android.support.room.Entity::class.java).orNull()?.let {
            val primaryKeyColumns = AnnotationMirrors.getAnnotationValue(it, "primaryKeys")
                    .getAsStringList()
            if (primaryKeyColumns.isEmpty()) {
                emptyList<PrimaryKey>()
            } else {
                val fields = primaryKeyColumns.map { pKeyColumnName ->
                    val field = availableFields.firstOrNull { it.columnName == pKeyColumnName }
                    context.checker.check(field != null, typeElement,
                            ProcessorErrors.primaryKeyColumnDoesNotExist(pKeyColumnName,
                                    availableFields.map { it.columnName }))
                    field
                }.filterNotNull()
                listOf(PrimaryKey(declaredIn = typeElement,
                        fields = fields,
                        autoGenerateId = false))
            }
        } ?: emptyList<PrimaryKey>()
        // checks supers.
        val mySuper = typeElement.superclass
        val superPKeys = if (mySuper != null && mySuper.kind != TypeKind.NONE) {
            // my super cannot see my fields so remove them.
            val remainingFields = availableFields.filterNot {
                it.element.enclosingElement == typeElement
            }
            collectPrimaryKeysFromEntityAnnotations(
                    MoreTypes.asTypeElement(mySuper), remainingFields)
        } else {
            emptyList()
        }
        return superPKeys + myPkeys
    }

    private fun collectPrimaryKeysFromDecomposedFields(decomposedFields: List<DecomposedField>)
            : List<PrimaryKey> {
        return decomposedFields.map { decomposedField ->
            MoreElements.getAnnotationMirror(decomposedField.field.element,
                    com.android.support.room.PrimaryKey::class.java).orNull()?.let {
                val autoGenerate = AnnotationMirrors
                        .getAnnotationValue(it, "autoGenerate").getAsBoolean(false)
                context.checker.check(!autoGenerate || decomposedField.pojo.fields.size == 1,
                        decomposedField.field.element,
                        ProcessorErrors.AUTO_INCREMENT_DECOMPOSED_HAS_MULTIPLE_FIELDS)
                PrimaryKey(declaredIn = decomposedField.field.element.enclosingElement,
                        fields = decomposedField.pojo.fields,
                        autoGenerateId = autoGenerate)
            }
        }.filterNotNull()
    }

    // start from my element and check if anywhere in the list we can find the only well defined
    // pkey, if so, use it.
    private fun choosePrimaryKey(candidates: List<PrimaryKey>, typeElement: TypeElement)
            : PrimaryKey {
        // If 1 of these primary keys is declared in this class, then it is the winner. Just print
        //    a note for the others.
        // If 0 is declared, check the parent.
        // If more than 1 primary key is declared in this class, it is an error.
        val myPKeys = candidates.filter { candidate ->
            candidate.declaredIn == typeElement
        }
        return if (myPKeys.size == 1) {
            // just note, this is not worth an error or warning
            (candidates - myPKeys).forEach {
                context.logger.d(element,
                        "${it.toHumanReadableString()} is" +
                                " overridden by ${myPKeys.first().toHumanReadableString()}")
            }
            myPKeys.first()
        } else if (myPKeys.isEmpty()) {
            // i have not declared anything, delegate to super
            val mySuper = typeElement.superclass
            if (mySuper != null && mySuper.kind != TypeKind.NONE) {
                return choosePrimaryKey(candidates, MoreTypes.asTypeElement(mySuper))
            }
            PrimaryKey.MISSING
        } else {
            context.logger.e(element, ProcessorErrors.multiplePrimaryKeyAnnotations(
                    myPKeys.map(PrimaryKey::toHumanReadableString)))
            PrimaryKey.MISSING
        }
    }

    private fun validateAndCreateIndices(inputs: List<IndexInput>, pojo: Pojo): List<Index> {
        // check for columns
        val indices = inputs.map { input ->
            context.checker.check(input.columnNames.isNotEmpty(), element,
                    INDEX_COLUMNS_CANNOT_BE_EMPTY)
            val fields = input.columnNames.map { columnName ->
                val field = pojo.fields.firstOrNull {
                    it.columnName == columnName
                }
                context.checker.check(field != null, element,
                        ProcessorErrors.indexColumnDoesNotExist(
                                columnName, pojo.fields.map { it.columnName }
                        ))
                field
            }.filterNotNull()
            if (fields.isEmpty()) {
                null
            } else {
                Index(name = input.name, unique = input.unique, fields = fields)
            }
        }.filterNotNull()

        // check for duplicate indices
        indices
                .groupBy { it.name }
                .filter { it.value.size > 1 }
                .forEach {
                    context.logger.e(element, ProcessorErrors.duplicateIndexInEntity(it.key))
                }

        // see if any decomposed field is an entity with indices, if so, report a warning
        pojo.decomposedFields.forEach { decomposed ->
            val decomposedElement = decomposed.pojo.element
            val subEntityAnnotation = MoreElements.getAnnotationMirror(decomposedElement,
                    com.android.support.room.Entity::class.java).orNull()
            subEntityAnnotation?.let {
                val subIndices = extractIndices(subEntityAnnotation, "")
                if (subIndices.isNotEmpty()) {
                    context.logger.w(Warning.INDEX_FROM_DECOMPOSED_ENTITY_IS_DROPPED,
                            decomposed.field.element, ProcessorErrors.droppedDecomposedIndex(
                            entityName = decomposed.pojo.typeName.toString(),
                            fieldPath = decomposed.field.getPath(),
                            grandParent = element.qualifiedName.toString()))
                }
            }
        }
        return indices
    }

    // check if parent is an Entity, if so, report its annotation indices
    private fun loadSuperIndices(typeMirror: TypeMirror?, tableName: String, inherit: Boolean)
            : List<IndexInput> {
        if (typeMirror == null || typeMirror.kind == TypeKind.NONE) {
            return emptyList()
        }
        val parentElement = MoreTypes.asTypeElement(typeMirror)
        val myIndices = MoreElements.getAnnotationMirror(parentElement,
                com.android.support.room.Entity::class.java).orNull()?.let { annotation ->
            val indices = extractIndices(annotation, tableName = "super")
            if (indices.isEmpty()) {
                emptyList()
            } else if (inherit) {
                // rename them
                indices.map {
                    IndexInput(
                            name = createIndexName(it.columnNames, tableName),
                            unique = it.unique,
                            columnNames = it.columnNames)
                }
            } else {
                context.logger.w(Warning.INDEX_FROM_PARENT_IS_DROPPED,
                        parentElement,
                        ProcessorErrors.droppedSuperClassIndex(
                                childEntity = element.qualifiedName.toString(),
                                superEntity = parentElement.qualifiedName.toString()))
                emptyList()
            }
        } ?: emptyList()
        return myIndices + loadSuperIndices(parentElement.superclass, tableName, inherit)
    }

    companion object {
        private fun extractTableName(element: TypeElement, annotation: AnnotationMirror)
                : String {
            val annotationValue = AnnotationMirrors
                    .getAnnotationValue(annotation, "tableName").value.toString()
            return if (annotationValue == "") {
                element.simpleName.toString()
            } else {
                annotationValue
            }
        }

        private fun extractIndices(annotation: AnnotationMirror, tableName: String)
                : List<IndexInput> {
            val arrayOfIndexAnnotations = AnnotationMirrors.getAnnotationValue(annotation,
                    "indices")
            return INDEX_LIST_VISITOR.visit(arrayOfIndexAnnotations, tableName)
        }

        private val INDEX_LIST_VISITOR = object
            : SimpleAnnotationValueVisitor6<List<IndexInput>, String>() {
            override fun visitArray(values: MutableList<out AnnotationValue>?, tableName: String)
                    : List<IndexInput> {
                return values?.map {
                    INDEX_VISITOR.visit(it, tableName)
                }?.filterNotNull() ?: emptyList<IndexInput>()
            }
        }

        private val INDEX_VISITOR = object : SimpleAnnotationValueVisitor6<IndexInput?, String>() {
            override fun visitAnnotation(a: AnnotationMirror?, tableName: String): IndexInput? {
                val fieldInput = getAnnotationValue(a, "value").getAsStringList()
                val unique = getAnnotationValue(a, "unique").getAsBoolean(false)
                val nameValue = getAnnotationValue(a, "name")
                        .getAsString("")
                val name = if (nameValue == null || nameValue == "") {
                    createIndexName(fieldInput, tableName)
                } else {
                    nameValue
                }
                return IndexInput(name, unique, fieldInput)
            }
        }

        private fun createIndexName(columnNames: List<String>, tableName: String): String {
            return "index_" + tableName + "_" + columnNames.joinToString("_")
        }

        private fun extractForeignKeys(annotation: AnnotationMirror): List<ForeignKeyInput> {
            val arrayOfForeignKeyAnnotations = getAnnotationValue(annotation, "foreignKeys")
            return FOREIGN_KEY_LIST_VISITOR.visit(arrayOfForeignKeyAnnotations)
        }

        private val FOREIGN_KEY_LIST_VISITOR = object
            : SimpleAnnotationValueVisitor6<List<ForeignKeyInput>, Void?>() {
            override fun visitArray(values: MutableList<out AnnotationValue>?, void: Void?)
                    : List<ForeignKeyInput> {
                return values?.map {
                    FOREIGN_KEY_VISITOR.visit(it)
                }?.filterNotNull() ?: emptyList<ForeignKeyInput>()
            }
        }

        private val FOREIGN_KEY_VISITOR = object : SimpleAnnotationValueVisitor6<ForeignKeyInput?,
                Void?>() {
            override fun visitAnnotation(a: AnnotationMirror?, void: Void?): ForeignKeyInput? {
                val entityClass = try {
                    getAnnotationValue(a, "entity").toType()
                } catch (notPresent: TypeNotPresentException) {
                    return null
                }
                val parentColumns = getAnnotationValue(a, "parentColumns").getAsStringList()
                val childColumns = getAnnotationValue(a, "childColumns").getAsStringList()
                val onDeleteInput = getAnnotationValue(a, "onDelete").getAsInt()
                val onUpdateInput = getAnnotationValue(a, "onUpdate").getAsInt()
                val deferred = getAnnotationValue(a, "deferred").getAsBoolean(true)
                val onDelete = ForeignKeyAction.fromAnnotationValue(onDeleteInput)
                val onUpdate = ForeignKeyAction.fromAnnotationValue(onUpdateInput)
                return ForeignKeyInput(
                        parent = entityClass,
                        parentColumns = parentColumns,
                        childColumns = childColumns,
                        onDelete = onDelete,
                        onUpdate = onUpdate,
                        deferred = deferred)
            }
        }
    }

    /**
     * processed Index annotation output
     */
    data class IndexInput(val name: String, val unique: Boolean, val columnNames: List<String>)

    /**
     * ForeignKey, before it is processed in the context of a database.
     */
    data class ForeignKeyInput(val parent : TypeMirror, val parentColumns : List<String>,
                     val childColumns : List<String>, val onDelete : ForeignKeyAction?,
                     val onUpdate : ForeignKeyAction?, val deferred : Boolean)
}

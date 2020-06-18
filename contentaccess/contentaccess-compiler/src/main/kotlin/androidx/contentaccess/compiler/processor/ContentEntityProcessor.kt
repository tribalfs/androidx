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

package androidx.contentaccess.compiler.processor

import androidx.contentaccess.ContentColumn
import androidx.contentaccess.ContentEntity
import androidx.contentaccess.ContentPrimaryKey
import androidx.contentaccess.compiler.utils.ErrorReporter
import androidx.contentaccess.compiler.vo.ContentColumnVO
import androidx.contentaccess.compiler.vo.ContentEntityVO
import androidx.contentaccess.ext.getAllConstructorParamsOrPublicFields
import androidx.contentaccess.ext.hasAnnotation
import androidx.contentaccess.ext.hasMoreThanOnePublicConstructor
import asTypeElement
import com.google.auto.common.MoreTypes
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror

class ContentEntityProcessor(
    private val contentEntity: TypeMirror,
    private val processingEnv: ProcessingEnvironment,
    private val errorReporter: ErrorReporter
) {

    fun processEntity(): ContentEntityVO? {
        val entity = contentEntity.asTypeElement()
        if (entity.hasMoreThanOnePublicConstructor(processingEnv)) {
            errorReporter.reportError("Entity $contentEntity has more than one non private " +
                    "constructor. Entities should have only one non private constructor.", entity)
            return null
        }
        val columns = entity.getAllConstructorParamsOrPublicFields(processingEnv)
        if (errorReporter.errorReported) {
            return null
        }
        val contentColumns = HashMap<String, ContentColumnVO>()
        val contentPrimaryKey = ArrayList<ContentColumnVO>()
        columns.forEach { column ->
            // TODO(obenabde): handle all the checks that need to happen here (e.g supported
            //  column types)
            if (column.hasAnnotation(ContentColumn::class) &&
                column.hasAnnotation(ContentPrimaryKey::class)) {
                errorReporter.reportError("Field ${column.simpleName} in '${entity
                    .qualifiedName}' is annotated with both @ContentPrimaryKey and " +
                        "@ContentColumn, these annotations are mutually exclusive and  a field " +
                        "can only be annotated by one of the two.", entity)
            } else if (column.hasAnnotation(ContentColumn::class)) {
                val vo = ContentColumnVO(
                    column.simpleName.toString(), column.asType(),
                    column.getAnnotation(ContentColumn::class.java).columnName,
                    fieldIsNullable(column)
                )
                contentColumns.put(vo.columnName, vo)
            } else if (column.hasAnnotation(ContentPrimaryKey::class)) {
                val vo = ContentColumnVO(column.simpleName.toString(), column.asType(), column
                    .getAnnotation(ContentPrimaryKey::class.java).columnName,
                    fieldIsNullable(column)
                )
                contentColumns.put(vo.columnName, vo)
                contentPrimaryKey.add(vo)
            } else {
                errorReporter.reportError("Field ${column.simpleName} in ${entity.qualifiedName} " +
                        "is neither annotated with @ContentPrimaryKey nor with @ContentColumn, " +
                        "all fields in a content entity must be be annotated by one of the two",
                    entity)
            }
        }
        if (contentPrimaryKey.isEmpty()) {
            if (columns.isEmpty()) {
                errorReporter.reportError("Content entity ${entity.qualifiedName} has no fields, " +
                        "a content entity must have at least one field and exactly one primary " +
                        "key.", entity)
            } else {
                errorReporter.reportError("Content entity ${entity.qualifiedName} doesn't have a" +
                        " primary key, a content entity must have one field annotated with " +
                        "@ContentPrimaryKey.", entity)
            }
        }
        if (contentPrimaryKey.size > 1) {
            errorReporter.reportError("Content entity ${entity.qualifiedName} has two or more " +
                    "primary keys, a content entity must have exactly one field annotated with " +
                    "@ContentPrimaryKey.", entity)
        }
        return ContentEntityVO(entity.getAnnotation(ContentEntity::class.java).uri, MoreTypes
            .asDeclared(entity.asType()), contentColumns, contentPrimaryKey.first())
    }
}

fun fieldIsNullable(field: VariableElement): Boolean {
    return field.annotationMirrors.any { NULLABLE_ANNOTATIONS.contains(it.toString()) }
}

val NULLABLE_ANNOTATIONS = listOf(
    "@org.jetbrains.annotations.Nullable",
    "@androidx.annotation.Nullable"
)

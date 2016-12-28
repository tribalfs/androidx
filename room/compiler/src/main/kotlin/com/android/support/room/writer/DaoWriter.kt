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

package com.android.support.room.writer

import com.android.support.room.ext.AndroidTypeNames
import com.android.support.room.ext.L
import com.android.support.room.ext.N
import com.android.support.room.ext.RoomTypeNames
import com.android.support.room.ext.T
import com.android.support.room.parser.QueryType
import com.android.support.room.solver.CodeGenScope
import com.android.support.room.vo.Dao
import com.android.support.room.vo.DeletionMethod
import com.android.support.room.vo.InsertionMethod
import com.android.support.room.vo.QueryMethod
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import stripNonJava
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier.FINAL
import javax.lang.model.element.Modifier.PRIVATE
import javax.lang.model.element.Modifier.PUBLIC

/**
 * Creates the implementation for a class annotated with Dao.
 */
class DaoWriter(val dao: Dao) : ClassWriter(ClassName.get(dao.type) as ClassName) {
    companion object {
        val dbField: FieldSpec = FieldSpec
                .builder(RoomTypeNames.ROOM_DB, "__db", PRIVATE, FINAL)
                .build()
    }

    override fun createTypeSpec(): TypeSpec {
        val builder = TypeSpec.classBuilder(dao.implTypeName)
        val scope = CodeGenScope()

        val shortcutMethods = groupAndCreateInsertionMethods(scope) +
                groupAndCreateDeletionMethods(scope)
        builder.apply {
            addModifiers(PUBLIC)
            if (dao.element.kind == ElementKind.INTERFACE) {
                addSuperinterface(dao.typeName)
            } else {
                superclass(dao.typeName)
            }
            addField(dbField)
            val dbParam = ParameterSpec.builder(dbField.type, dbField.name).build()

            addMethod(createConstructor(dbParam, shortcutMethods))

            shortcutMethods.forEach {
                addMethods(it.methodImpls)
                if (it.field != null) {
                    addField(it.field)
                }
            }

            dao.queryMethods.filter { it.query.queryType == QueryType.SELECT }.forEach { method ->
                builder.addMethod(createSelectMethod(method))
            }
        }
        return builder.build()
    }

    private fun createConstructor(dbParam: ParameterSpec,
                                  shortcutMethods: List<GroupedShortcut>): MethodSpec {
        return MethodSpec.constructorBuilder().apply {
            addParameter(dbParam)
            addModifiers(PUBLIC)
            addStatement("this.$N = $N", dbField, dbParam)
            shortcutMethods.filterNot {
                it.field == null || it.fieldImpl == null
            }.forEach {
                addStatement("this.$N = $L", it.field, it.fieldImpl)
            }
        }.build()
    }

    private fun createSelectMethod(method : QueryMethod) : MethodSpec {
        return overrideWithoutAnnotations(method.element).apply {
            addCode(createQueryMethodBody(method))
        }.build()
    }

    /**
     * Groups all insertion methods based on the insert statement they will use then creates all
     * field specs, EntityInsertionAdapterWriter and actual insert methods.
     */
    private fun groupAndCreateInsertionMethods(scope : CodeGenScope): List<GroupedShortcut> {
        return dao.insertionMethods
                .groupBy {
                    Pair(it.entity?.typeName, it.onConflictText)
                }.map { entry ->
            val onConflict = entry.key.second
            val methods = entry.value
            val entity = methods.first().entity

            val fieldSpec : FieldSpec?
            val implSpec : TypeSpec?
            if (entity == null) {
                fieldSpec = null
                implSpec = null
            } else {
                val fieldName = scope
                        .getTmpVar("__insertionAdapterOf${typeNameToFieldName(entity.typeName)}")
                fieldSpec = FieldSpec.builder(RoomTypeNames.INSERTION_ADAPTER, fieldName,
                        FINAL, PRIVATE).build()
                implSpec = EntityInsertionAdapterWriter(entity, onConflict)
                        .createAnonymous(dbField.name)
            }
            val insertionMethodImpls = methods.map { method ->
                overrideWithoutAnnotations(method.element).apply {
                    addCode(createInsertionMethodBody(method, fieldSpec))
                }.build()
            }
            GroupedShortcut(fieldSpec, implSpec, insertionMethodImpls)
        }
    }

    private fun createInsertionMethodBody(method: InsertionMethod,
                                          insertionAdapter: FieldSpec?): CodeBlock {
        val insertionType = method.insertionType
        if (insertionAdapter == null || insertionType == null) {
            return CodeBlock.builder().build()
        }
        val scope = CodeGenScope()

        return scope.builder().apply {
            // TODO assert thread
            // TODO collect results
            addStatement("$N.beginTransaction()", dbField)
            beginControlFlow("try").apply {
                method.parameters.forEach { param ->
                    addStatement("$N.$L($L)", insertionAdapter, insertionType.methodName,
                            param.name)
                }
                addStatement("$N.setTransactionSuccessful()", dbField)
            }
            nextControlFlow("finally").apply {
                addStatement("$N.endTransaction()", dbField)
            }
            endControlFlow()
        }.build()
    }

    /**
     * Groups all deletion methods based on the delete statement they will use then creates all
     * field specs, EntityDeletionAdapterWriter and actual deletion methods.
     */
    private fun groupAndCreateDeletionMethods(scope : CodeGenScope): List<GroupedShortcut> {
        return dao.deletionMethods
                .groupBy {
                    it.entity?.typeName
                }.map { entry ->
            val methods = entry.value
            val entity = methods.first().entity

            val fieldSpec : FieldSpec?
            val implSpec : TypeSpec?
            if (entity == null) {
                fieldSpec = null
                implSpec = null
            } else {
                val fieldName = scope
                        .getTmpVar("__deletionAdapterOf${typeNameToFieldName(entity.typeName)}")
                fieldSpec = FieldSpec.builder(RoomTypeNames.DELETE_OR_UPDATE_ADAPTER, fieldName,
                        FINAL, PRIVATE).build()
                implSpec = EntityDeletionAdapterWriter(entity)
                        .createAnonymous(dbField.name)
            }
            val deletionMethodImpls = methods.map { method ->
                overrideWithoutAnnotations(method.element).apply {
                    addCode(createDeletionMethodBody(method, fieldSpec))
                }.build()
            }
            GroupedShortcut(fieldSpec, implSpec, deletionMethodImpls)
        }
    }

    private fun createDeletionMethodBody(method: DeletionMethod,
                                          deletionAdapter: FieldSpec?): CodeBlock {
        if (deletionAdapter == null) {
            return CodeBlock.builder().build()
        }
        val scope = CodeGenScope()
        val resultVar = if (method.returnCount) {
            scope.getTmpVar("_total")
        } else {
            null
        }
        return scope.builder().apply {
            if (resultVar != null) {
                addStatement("$T $L = 0", TypeName.INT, resultVar)
            }
            addStatement("$N.beginTransaction()", dbField)
            beginControlFlow("try").apply {
                method.parameters.forEach { param ->
                    addStatement("$L$N.$L($L)",
                            if (resultVar == null) "" else "$resultVar +=",
                            deletionAdapter, method.deletionMethodFor(param), param.name)
                }
                addStatement("$N.setTransactionSuccessful()", dbField)
                if (resultVar != null) {
                    addStatement("return $L", resultVar)
                }
            }
            nextControlFlow("finally").apply {
                addStatement("$N.endTransaction()", dbField)
            }
            endControlFlow()
        }.build()
    }

    private fun createQueryMethodBody(method: QueryMethod): CodeBlock {
        val queryWriter = QueryWriter(method)
        val scope = CodeGenScope()
        val sqlVar = scope.getTmpVar("_sql")
        val argsVar = scope.getTmpVar("_args")
        queryWriter.prepareReadQuery(sqlVar, argsVar, scope)
        scope.builder().apply {
            val cursorVar = scope.getTmpVar("_cursor")
            val outVar = scope.getTmpVar("_result")
            addStatement("final $T $L = $N.query($L, $L)", AndroidTypeNames.CURSOR, cursorVar,
                    dbField, sqlVar, argsVar)
            beginControlFlow("try")
            method.resultAdapter?.convert(outVar, cursorVar, scope)
            addStatement("return $L", outVar)
            nextControlFlow("finally")
            addStatement("$L.close()", cursorVar)
            endControlFlow()
        }
        return scope.builder().build()
    }

    private fun overrideWithoutAnnotations(elm: ExecutableElement): MethodSpec.Builder {
        val baseSpec = MethodSpec.overriding(elm).build()
        return MethodSpec.methodBuilder(baseSpec.name).apply {
            addAnnotation(Override::class.java)
            addModifiers(baseSpec.modifiers)
            addParameters(baseSpec.parameters)
            varargs(baseSpec.varargs)
            returns(baseSpec.returnType)
        }
    }

    private fun typeNameToFieldName(typeName: TypeName): String {
        if (typeName is ClassName) {
            return typeName.simpleName()
        } else {
            return typeName.toString().replace('.', '_').stripNonJava()
        }
    }

    data class GroupedShortcut(val field: FieldSpec?,
                               val fieldImpl: TypeSpec?,
                               val methodImpls: List<MethodSpec>)
}

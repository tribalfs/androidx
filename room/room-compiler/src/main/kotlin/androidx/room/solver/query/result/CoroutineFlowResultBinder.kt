/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.room.solver.query.result

import androidx.room.compiler.codegen.CodeLanguage
import androidx.room.compiler.codegen.XCodeBlock
import androidx.room.compiler.codegen.XCodeBlock.Builder.Companion.addLocalVal
import androidx.room.compiler.codegen.XMemberName.Companion.packageMember
import androidx.room.compiler.codegen.XPropertySpec
import androidx.room.compiler.codegen.XTypeName
import androidx.room.compiler.processing.XType
import androidx.room.ext.ArrayLiteral
import androidx.room.ext.CallableTypeSpecBuilder
import androidx.room.ext.CommonTypeNames
import androidx.room.ext.Function1TypeSpec
import androidx.room.ext.RoomCoroutinesTypeNames.COROUTINES_ROOM
import androidx.room.ext.RoomTypeNames
import androidx.room.ext.SQLiteDriverTypeNames
import androidx.room.solver.CodeGenScope

/**
 * Binds the result of a Kotlin Coroutine Flow<T>
 */
class CoroutineFlowResultBinder(
    val typeArg: XType,
    val tableNames: Set<String>,
    adapter: QueryResultAdapter?
) : BaseObservableQueryResultBinder(adapter) {

    override fun convertAndReturn(
        roomSQLiteQueryVar: String,
        canReleaseQuery: Boolean,
        dbProperty: XPropertySpec,
        inTransaction: Boolean,
        scope: CodeGenScope
    ) {
        val callableImpl = CallableTypeSpecBuilder(scope.language, typeArg.asTypeName()) {
            addCode(
                XCodeBlock.builder(language).apply {
                    createRunQueryAndReturnStatements(
                        builder = this,
                        roomSQLiteQueryVar = roomSQLiteQueryVar,
                        dbProperty = dbProperty,
                        inTransaction = inTransaction,
                        scope = scope,
                        cancellationSignalVar = "null"
                    )
                }.build()
            )
        }.apply {
            if (canReleaseQuery) {
                createFinalizeMethod(roomSQLiteQueryVar)
            }
        }.build()

        scope.builder.apply {
            val arrayOfTableNamesLiteral = ArrayLiteral(
                scope.language,
                CommonTypeNames.STRING,
                *tableNames.toTypedArray()
            )
            addStatement(
                "return %T.createFlow(%N, %L, %L, %L)",
                COROUTINES_ROOM,
                dbProperty,
                if (inTransaction) "true" else "false",
                arrayOfTableNamesLiteral,
                callableImpl
            )
        }
    }

    override fun isMigratedToDriver() = adapter?.isMigratedToDriver() == true

    override fun convertAndReturn(
        sqlQueryVar: String,
        dbProperty: XPropertySpec,
        bindStatement: CodeGenScope.(String) -> Unit,
        returnTypeName: XTypeName,
        inTransaction: Boolean,
        scope: CodeGenScope
    ) {
        val arrayOfTableNamesLiteral = ArrayLiteral(
            scope.language,
            CommonTypeNames.STRING,
            *tableNames.toTypedArray()
        )
        when (scope.language) {
            CodeLanguage.JAVA -> convertAndReturnJava(
                sqlQueryVar,
                dbProperty,
                bindStatement,
                inTransaction,
                arrayOfTableNamesLiteral,
                scope
            )

            CodeLanguage.KOTLIN -> convertAndReturnKotlin(
                sqlQueryVar,
                dbProperty,
                bindStatement,
                inTransaction,
                arrayOfTableNamesLiteral,
                scope
            )
        }
    }

    private fun convertAndReturnJava(
        sqlQueryVar: String,
        dbProperty: XPropertySpec,
        bindStatement: CodeGenScope.(String) -> Unit,
        inTransaction: Boolean,
        arrayOfTableNamesLiteral: XCodeBlock,
        scope: CodeGenScope
    ) {
        val connectionVar = scope.getTmpVar("_connection")
        val statementVar = scope.getTmpVar("_stmt")
        scope.builder.addStatement(
            "return %M(%N, %L, %L, %L)",
            RoomTypeNames.FLOW_UTIL.packageMember("createFlow"),
            dbProperty,
            inTransaction,
            arrayOfTableNamesLiteral,
            // TODO(b/322387497): Generate lambda syntax if possible
            Function1TypeSpec(
                language = scope.language,
                parameterTypeName = SQLiteDriverTypeNames.CONNECTION,
                parameterName = connectionVar,
                returnTypeName = typeArg.asTypeName()
            ) {
                val functionScope = scope.fork()
                val outVar = functionScope.getTmpVar("_result")
                val functionCode = functionScope.builder.apply {
                    addLocalVal(
                        statementVar,
                        SQLiteDriverTypeNames.STATEMENT,
                        "%L.prepare(%L)",
                        connectionVar,
                        sqlQueryVar
                    )
                    beginControlFlow("try")
                    bindStatement(functionScope, statementVar)
                    adapter?.convert(outVar, statementVar, functionScope)
                    addStatement("return %L", outVar)
                    nextControlFlow("finally")
                    addStatement("%L.close()", statementVar)
                    endControlFlow()
                }.build()
                this.addCode(functionCode)
            }
        )
    }

    private fun convertAndReturnKotlin(
        sqlQueryVar: String,
        dbProperty: XPropertySpec,
        bindStatement: CodeGenScope.(String) -> Unit,
        inTransaction: Boolean,
        arrayOfTableNamesLiteral: XCodeBlock,
        scope: CodeGenScope
    ) {
        val connectionVar = scope.getTmpVar("_connection")
        val statementVar = scope.getTmpVar("_stmt")
        scope.builder.apply {
            beginControlFlow(
                "return %M(%N, %L, %L) { %L ->",
                RoomTypeNames.FLOW_UTIL.packageMember("createFlow"),
                dbProperty,
                inTransaction,
                arrayOfTableNamesLiteral,
                connectionVar
            )
            addLocalVal(
                statementVar,
                SQLiteDriverTypeNames.STATEMENT,
                "%L.prepare(%L)",
                connectionVar,
                sqlQueryVar
            )
            beginControlFlow("try")
            bindStatement(scope, statementVar)
            val outVar = scope.getTmpVar("_result")
            adapter?.convert(outVar, statementVar, scope)
            addStatement("%L", outVar)
            nextControlFlow("finally")
            addStatement("%L.close()", statementVar)
            endControlFlow()
            endControlFlow()
        }
    }
}

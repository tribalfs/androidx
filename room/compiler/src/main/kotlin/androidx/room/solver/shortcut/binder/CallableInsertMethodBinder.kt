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

package androidx.room.solver.shortcut.binder

import androidx.room.ext.CallableTypeSpecBuilder
import androidx.room.ext.typeName
import androidx.room.solver.CodeGenScope
import androidx.room.solver.shortcut.result.InsertMethodAdapter
import androidx.room.vo.ShortcutQueryParameter
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.type.TypeMirror

/**
 * Binder for deferred insert methods.
 *
 * This binder will create a Callable implementation that delegates to the
 * [InsertMethodAdapter]. Usage of the Callable impl is then delegate to the [addStmntBlock]
 * function.
 */
class CallableInsertMethodBinder(
    val typeArg: TypeMirror,
    val addStmntBlock: CodeBlock.Builder.(callableImpl: TypeSpec, dbField: FieldSpec) -> Unit,
    adapter: InsertMethodAdapter?
) : InsertMethodBinder(adapter) {

        companion object {
            fun createInsertBinder(
                typeArg: TypeMirror,
                adapter: InsertMethodAdapter?,
                addCodeBlock: CodeBlock.Builder.(callableImpl: TypeSpec, dbField: FieldSpec) -> Unit
            ) = CallableInsertMethodBinder(typeArg, addCodeBlock, adapter)
        }

        override fun convertAndReturn(
            parameters: List<ShortcutQueryParameter>,
            insertionAdapters: Map<String, Pair<FieldSpec, TypeSpec>>,
            dbField: FieldSpec,
            scope: CodeGenScope
        ) {
            val adapterScope = scope.fork()
            val callableImpl = CallableTypeSpecBuilder(typeArg.typeName()) {
                adapter?.createInsertionMethodBody(
                    parameters = parameters,
                    insertionAdapters = insertionAdapters,
                    scope = adapterScope
                )
                addCode(adapterScope.generate())
            }.build()

            scope.builder().apply {
                addStmntBlock(callableImpl, dbField)
            }
        }
    }
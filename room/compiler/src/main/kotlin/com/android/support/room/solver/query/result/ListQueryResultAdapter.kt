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

package com.android.support.room.solver.query.result

import com.android.support.room.ext.L
import com.android.support.room.ext.T
import com.android.support.room.ext.typeName
import com.android.support.room.processor.Context
import com.android.support.room.solver.CodeGenScope
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import java.util.ArrayList
import javax.lang.model.element.Element

class ListQueryResultAdapter(val rowAdapter: RowAdapter) : QueryResultAdapter() {
    override fun reportErrors(context: Context, element: Element, suppressedWarnings: Set<String>) {
        rowAdapter.reportErrors(context, element, suppressedWarnings)
    }

    val type = rowAdapter.out
    override fun convert(outVarName: String, cursorVarName: String, scope: CodeGenScope) {
        scope.builder().apply {
            val converter = rowAdapter.init(cursorVarName, scope)
            val collectionType = ParameterizedTypeName
                    .get(ClassName.get(List::class.java), type.typeName())
            val arrayListType = ParameterizedTypeName
                    .get(ClassName.get(ArrayList::class.java), type.typeName())
            addStatement("final $T $L = new $T($L.getCount())",
                    collectionType, outVarName, arrayListType, cursorVarName)
            val tmpVarName = scope.getTmpVar("_item")
            beginControlFlow("while($L.moveToNext())", cursorVarName).apply {
                addStatement("final $T $L", type.typeName(), tmpVarName)
                converter.convert(tmpVarName, cursorVarName)
                addStatement("$L.add($L)", outVarName, tmpVarName)
            }
            endControlFlow()
        }
    }
}

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

package androidx.room.vo

import androidx.room.ext.CommonTypeNames
import androidx.room.ext.SupportDbTypeNames
import androidx.room.processing.XMethodElement
import androidx.room.processing.XType
import androidx.room.solver.query.result.QueryResultBinder
import com.squareup.javapoet.TypeName

/**
 * A class that holds information about a method annotated with RawQuery.
 * It is self sufficient and must have all generics etc resolved once created.
 */
data class RawQueryMethod(
    val element: XMethodElement,
    val name: String,
    val returnType: XType,
    val inTransaction: Boolean,
    val observedTableNames: Set<String>,
    val runtimeQueryParam: RuntimeQueryParameter?,
    val queryResultBinder: QueryResultBinder
) {
    val returnsValue by lazy {
        returnType.isNotVoid() && !returnType.isKotlinUnit()
    }

    data class RuntimeQueryParameter(
        val paramName: String,
        val type: TypeName
    ) {
        fun isString() = CommonTypeNames.STRING == type
        fun isSupportQuery() = SupportDbTypeNames.QUERY == type
    }
}

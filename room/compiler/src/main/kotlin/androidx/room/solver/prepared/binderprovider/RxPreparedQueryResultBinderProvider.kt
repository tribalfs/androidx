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

package androidx.room.solver.prepared.binderprovider

import androidx.room.ext.L
import androidx.room.ext.T
import androidx.room.ext.findTypeElement
import androidx.room.ext.findTypeMirror
import androidx.room.ext.typeName
import androidx.room.parser.ParsedQuery
import androidx.room.processor.Context
import androidx.room.solver.RxType
import androidx.room.solver.prepared.binder.CallablePreparedQueryResultBinder.Companion.createPreparedBinder
import androidx.room.solver.prepared.binder.PreparedQueryResultBinder
import isAssignableFrom
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeMirror

open class RxPreparedQueryResultBinderProvider internal constructor(
    val context: Context,
    private val rxType: RxType
) : PreparedQueryResultBinderProvider {

    private val hasRxJavaArtifact by lazy {
        context.processingEnv.findTypeElement(rxType.version.rxRoomClassName) != null
    }

    override fun matches(declared: DeclaredType): Boolean =
        declared.typeArguments.size == 1 && matchesRxType(declared)

    private fun matchesRxType(declared: DeclaredType): Boolean {
        val erasure = context.processingEnv.typeUtils.erasure(declared)
        return erasure.typeName() == rxType.className
    }

    override fun provide(declared: DeclaredType, query: ParsedQuery): PreparedQueryResultBinder {
        if (!hasRxJavaArtifact) {
            context.logger.e(rxType.version.missingArtifactMessage)
        }
        val typeArg = extractTypeArg(declared)
        return createPreparedBinder(
            returnType = typeArg,
            adapter = context.typeAdapterStore.findPreparedQueryResultAdapter(typeArg, query)
        ) { callableImpl, _ ->
            addStatement("return $T.fromCallable($L)", rxType.className, callableImpl)
        }
    }

    open fun extractTypeArg(declared: DeclaredType): TypeMirror = declared.typeArguments.first()

    companion object {
        fun getAll(context: Context) = listOf(
            RxPreparedQueryResultBinderProvider(context, RxType.RX2_SINGLE),
            RxPreparedQueryResultBinderProvider(context, RxType.RX2_MAYBE),
            RxCompletablePreparedQueryResultBinderProvider(context, RxType.RX2_COMPLETABLE),
            RxPreparedQueryResultBinderProvider(context, RxType.RX3_SINGLE),
            RxPreparedQueryResultBinderProvider(context, RxType.RX3_MAYBE),
            RxCompletablePreparedQueryResultBinderProvider(context, RxType.RX3_COMPLETABLE)
        )
    }
}

private class RxCompletablePreparedQueryResultBinderProvider(
    context: Context,
    rxType: RxType
) : RxPreparedQueryResultBinderProvider(context, rxType) {

    private val completableType: TypeMirror? by lazy {
        context.processingEnv.findTypeMirror(rxType.className)
    }

    override fun matches(declared: DeclaredType): Boolean {
        if (completableType == null) {
            return false
        }
        val typeUtils = context.processingEnv.typeUtils
        val erasure = typeUtils.erasure(declared)
        return erasure.isAssignableFrom(typeUtils, completableType!!)
    }

    override fun extractTypeArg(declared: DeclaredType) = context.COMMON_TYPES.VOID
}
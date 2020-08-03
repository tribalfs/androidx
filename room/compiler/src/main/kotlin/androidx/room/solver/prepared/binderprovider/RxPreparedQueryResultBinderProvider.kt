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
import androidx.room.parser.ParsedQuery
import androidx.room.processing.XDeclaredType
import androidx.room.processing.XType
import androidx.room.processor.Context
import androidx.room.solver.RxType
import androidx.room.solver.prepared.binder.CallablePreparedQueryResultBinder.Companion.createPreparedBinder
import androidx.room.solver.prepared.binder.PreparedQueryResultBinder

open class RxPreparedQueryResultBinderProvider internal constructor(
    val context: Context,
    private val rxType: RxType
) : PreparedQueryResultBinderProvider {

    private val hasRxJavaArtifact by lazy {
        context.processingEnv.findTypeElement(rxType.version.rxRoomClassName) != null
    }

    override fun matches(declared: XDeclaredType): Boolean =
        declared.typeArguments.size == 1 && matchesRxType(declared)

    private fun matchesRxType(declared: XDeclaredType): Boolean {
        val erasure = declared.erasure()
        return erasure.typeName == rxType.className
    }

    override fun provide(declared: XDeclaredType, query: ParsedQuery): PreparedQueryResultBinder {
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

    open fun extractTypeArg(declared: XDeclaredType): XType = declared.typeArguments.first()

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

    private val completableType: XType? by lazy {
        context.processingEnv.findType(rxType.className)
    }

    override fun matches(declared: XDeclaredType): Boolean {
        if (completableType == null) {
            return false
        }
        val erasure = declared.erasure()
        return erasure.isAssignableFrom(completableType!!)
    }

    override fun extractTypeArg(declared: XDeclaredType) = context.COMMON_TYPES.VOID
}
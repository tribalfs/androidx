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

package com.android.support.room.solver

import com.android.support.room.Entity
import com.android.support.room.ext.LifecyclesTypeNames
import com.android.support.room.ext.RoomTypeNames
import com.android.support.room.ext.hasAnnotation
import com.android.support.room.parser.ParsedQuery
import com.android.support.room.parser.Table
import com.android.support.room.processor.Context
import com.android.support.room.processor.PojoProcessor
import com.android.support.room.solver.query.parameter.ArrayQueryParameterAdapter
import com.android.support.room.solver.query.parameter.BasicQueryParameterAdapter
import com.android.support.room.solver.query.parameter.CollectionQueryParameterAdapter
import com.android.support.room.solver.query.parameter.QueryParameterAdapter
import com.android.support.room.solver.query.result.ArrayQueryResultAdapter
import com.android.support.room.solver.query.result.EntityRowAdapter
import com.android.support.room.solver.query.result.InstantQueryResultBinder
import com.android.support.room.solver.query.result.ListQueryResultAdapter
import com.android.support.room.solver.query.result.LiveDataQueryResultBinder
import com.android.support.room.solver.query.result.PojoRowAdapter
import com.android.support.room.solver.query.result.QueryResultAdapter
import com.android.support.room.solver.query.result.QueryResultBinder
import com.android.support.room.solver.query.result.RowAdapter
import com.android.support.room.solver.query.result.SingleColumnRowAdapter
import com.android.support.room.solver.query.result.SingleEntityQueryResultAdapter
import com.android.support.room.solver.types.BoxedBooleanToBoxedIntConverter
import com.android.support.room.solver.types.BoxedPrimitiveColumnTypeAdapter
import com.android.support.room.solver.types.BoxedPrimitiveToStringConverter
import com.android.support.room.solver.types.ColumnTypeAdapter
import com.android.support.room.solver.types.CompositeAdapter
import com.android.support.room.solver.types.CompositeTypeConverter
import com.android.support.room.solver.types.IntListConverter
import com.android.support.room.solver.types.NoOpConverter
import com.android.support.room.solver.types.PrimitiveBooleanToIntConverter
import com.android.support.room.solver.types.PrimitiveColumnTypeAdapter
import com.android.support.room.solver.types.PrimitiveToStringConverter
import com.android.support.room.solver.types.ReverseTypeConverter
import com.android.support.room.solver.types.StringColumnTypeAdapter
import com.android.support.room.solver.types.TypeConverter
import com.android.support.room.verifier.QueryResultInfo
import com.android.support.room.vo.Pojo
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import com.google.common.annotations.VisibleForTesting
import java.util.LinkedList
import javax.lang.model.type.ArrayType
import javax.lang.model.type.DeclaredType
import javax.lang.model.type.TypeKind
import javax.lang.model.type.TypeMirror

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN")
/**
 * Holds all type adapters and can create on demand composite type adapters to convert a type into a
 * database column.
 */
class TypeAdapterStore(val context: Context,
                       @VisibleForTesting vararg extras: Any) {
    private val columnTypeAdapters: List<ColumnTypeAdapter>
    private val typeConverters: List<TypeConverter>

    init {
        val adapters = arrayListOf<ColumnTypeAdapter>()
        val converters = arrayListOf<TypeConverter>()
        extras.forEach {
            when (it) {
                is TypeConverter -> converters.add(it)
                is ColumnTypeAdapter -> adapters.add(it)
                else -> throw IllegalArgumentException("unknown extra")
            }
        }
        fun addTypeConverter(converter: TypeConverter) {
            converters.add(converter)
            converters.add(ReverseTypeConverter(converter))
        }

        fun addColumnAdapter(adapter: ColumnTypeAdapter) {
            adapters.add(adapter)
        }

        val primitives = PrimitiveColumnTypeAdapter
                .createPrimitiveAdapters(context.processingEnv)
        primitives.forEach(::addColumnAdapter)
        BoxedPrimitiveColumnTypeAdapter
                .createBoxedPrimitiveAdapters(context.processingEnv, primitives)
                .forEach(::addColumnAdapter)
        addColumnAdapter(StringColumnTypeAdapter(context.processingEnv))
        addTypeConverter(IntListConverter.create(context.processingEnv))
        addTypeConverter(PrimitiveBooleanToIntConverter(context.processingEnv))
        PrimitiveToStringConverter
                .createPrimitives(context)
                .forEach(::addTypeConverter)
        BoxedPrimitiveToStringConverter
                .createBoxedPrimitives(context)
                .forEach(::addTypeConverter)
        addTypeConverter(BoxedBooleanToBoxedIntConverter(context.processingEnv))
        columnTypeAdapters = adapters
        typeConverters = converters
    }

    // type mirrors that be converted into columns w/o an extra converter
    private val knownColumnTypeMirrors by lazy {
        columnTypeAdapters.map { it.out }
    }

    fun findColumnTypeAdapter(out: TypeMirror): ColumnTypeAdapter? {
        val adapters = getAllColumnAdapters(out)
        if (adapters.isNotEmpty()) {
            return adapters.last()
        }
        val converter = findTypeConverter(out, knownColumnTypeMirrors)
        if (converter != null) {
            return CompositeAdapter(out, getAllColumnAdapters(converter.to).first(), converter)
        }
        return null
    }

    fun findTypeConverter(input: TypeMirror, output: TypeMirror): TypeConverter? {
        return findTypeConverter(input, listOf(output))
    }

    private fun isLiveData(declared: DeclaredType): Boolean {
        val typeElement = MoreElements.asType(declared.asElement())
        val qName = typeElement.qualifiedName.toString()
        // even though computable live data is internal, we still check for it as we may inherit
        // it from some internal class.
        return qName == LifecyclesTypeNames.COMPUTABLE_LIVE_DATA.toString() ||
                qName == LifecyclesTypeNames.LIVE_DATA.toString()
    }

    fun findQueryResultBinder(typeMirror: TypeMirror, query: ParsedQuery): QueryResultBinder {
        return if (typeMirror.kind == TypeKind.DECLARED) {
            val declared = MoreTypes.asDeclared(typeMirror)
            if (declared.typeArguments.isEmpty()) {
                InstantQueryResultBinder(findQueryResultAdapter(typeMirror, query))
            } else {
                if (isLiveData(declared)) {
                    val liveDataTypeArg = declared.typeArguments.first()
                    LiveDataQueryResultBinder(liveDataTypeArg, query.tables,
                            findQueryResultAdapter(liveDataTypeArg, query))
                } else {
                    InstantQueryResultBinder(findQueryResultAdapter(typeMirror, query))
                }
            }
        } else {
            InstantQueryResultBinder(findQueryResultAdapter(typeMirror, query))
        }
    }

    private fun findQueryResultAdapter(typeMirror: TypeMirror, query: ParsedQuery)
            : QueryResultAdapter? {
        if (typeMirror.kind == TypeKind.DECLARED) {
            val declared = MoreTypes.asDeclared(typeMirror)
            if (declared.typeArguments.isEmpty()) {
                val rowAdapter = findRowAdapter(typeMirror, query) ?: return null
                return SingleEntityQueryResultAdapter(rowAdapter)
            }
            if (MoreTypes.isTypeOf(java.util.List::class.java, typeMirror)) {
                val typeArg = declared.typeArguments.first()
                val rowAdapter = findRowAdapter(typeArg, query) ?: return null
                return ListQueryResultAdapter(rowAdapter)
            }
            return null
        } else if (typeMirror.kind == TypeKind.ARRAY) {
            val array = MoreTypes.asArray(typeMirror)
            val rowAdapter =
                    findRowAdapter(array.componentType, query) ?: return null
            return ArrayQueryResultAdapter(rowAdapter)
        } else {
            val rowAdapter = findRowAdapter(typeMirror, query) ?: return null
            return SingleEntityQueryResultAdapter(rowAdapter)
        }
    }

    /**
     * Find a converter from cursor to the given type mirror.
     * If there is information about the query result, we try to use it to accept *any* POJO.
     */
    @VisibleForTesting
    fun findRowAdapter(typeMirror: TypeMirror, query: ParsedQuery): RowAdapter? {
        if (typeMirror.kind == TypeKind.DECLARED) {
            val declared = MoreTypes.asDeclared(typeMirror)
            if (declared.typeArguments.isNotEmpty()) {
                // TODO one day support this
                return null
            }
            val asElement = MoreTypes.asElement(typeMirror)
            if (asElement.hasAnnotation(Entity::class)) {
                return EntityRowAdapter(typeMirror)
            }
            // if result is unknown, we are fine w/ single column result
            val resultInfo = query.resultInfo
            if ((resultInfo?.columns?.size ?: 1) == 1) {
                val singleColumn = findColumnTypeAdapter(typeMirror)
                if (singleColumn != null) {
                    return SingleColumnRowAdapter(singleColumn)
                }
            }
            if (resultInfo != null) {
                val pojo = PojoProcessor(context).parse(MoreTypes.asTypeElement(typeMirror))
                return PojoRowAdapter(
                        info = resultInfo,
                        pojo = pojo,
                        out = typeMirror)
            }
            return null
        } else {
            val singleColumn = findColumnTypeAdapter(typeMirror) ?: return null
            return SingleColumnRowAdapter(singleColumn)
        }
    }

    fun findQueryParameterAdapter(typeMirror: TypeMirror): QueryParameterAdapter? {
        if (MoreTypes.isType(typeMirror)
                && (MoreTypes.isTypeOf(java.util.List::class.java, typeMirror)
                || MoreTypes.isTypeOf(java.util.Set::class.java, typeMirror))) {
            val declared = MoreTypes.asDeclared(typeMirror)
            val bindAdapter = findColumnTypeAdapter(declared.typeArguments.first()) ?: return null
            return CollectionQueryParameterAdapter(bindAdapter)
        } else if (typeMirror is ArrayType) {
            val component = typeMirror.componentType
            val bindAdapter = findColumnTypeAdapter(component) ?: return null
            return ArrayQueryParameterAdapter(bindAdapter)
        } else {
            val bindAdapter = findColumnTypeAdapter(typeMirror) ?: return null
            return BasicQueryParameterAdapter(bindAdapter)
        }
    }

    private fun findTypeConverter(input: TypeMirror, outputs: List<TypeMirror>): TypeConverter? {
        val types = context.processingEnv.typeUtils
        // if same type, return no-op
        if (outputs.any { types.isSameType(input, it) }) {
            return NoOpConverter(input)
        }
        val excludes = arrayListOf<TypeMirror>()
        excludes.add(input)
        val queue = LinkedList<TypeConverter>()
        do {
            val prev = if (queue.isEmpty()) null else queue.pop()
            val from = prev?.to ?: input
            val candidates = getAllTypeConverters(from, excludes)
            val match = candidates.firstOrNull {
                outputs.any { output -> types.isSameType(output, it.to) }
            }
            if (match != null) {
                return if (prev == null) match else CompositeTypeConverter(prev, match)
            }
            candidates.forEach {
                excludes.add(it.to)
                queue.add(
                        if (prev == null) it else CompositeTypeConverter(prev, it)
                )
            }
        } while (queue.isNotEmpty())
        return null
    }

    private fun getAllColumnAdapters(input: TypeMirror): List<ColumnTypeAdapter> {
        return columnTypeAdapters.filter {
            context.processingEnv.typeUtils.isSameType(input, it.out)
        }
    }

    private fun getAllTypeConverters(input: TypeMirror, excludes: List<TypeMirror>):
            List<TypeConverter> {
        val types = context.processingEnv.typeUtils
        return typeConverters.filter { converter ->
            types.isSameType(input, converter.from) &&
                    !excludes.any { types.isSameType(it, converter.to) }
        }
    }
}

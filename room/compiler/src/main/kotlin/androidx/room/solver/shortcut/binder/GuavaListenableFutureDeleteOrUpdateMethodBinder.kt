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

import androidx.room.ext.L
import androidx.room.ext.N
import androidx.room.ext.RoomGuavaTypeNames
import androidx.room.ext.T
import androidx.room.ext.typeName
import androidx.room.solver.CodeGenScope
import androidx.room.solver.shortcut.result.DeleteOrUpdateMethodAdapter
import androidx.room.vo.ShortcutQueryParameter
import androidx.room.writer.DaoWriter
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier
import javax.lang.model.type.TypeMirror

/**
 * Binder that knows how to write delete and update methods that return ListenableFuture<T>.
 */
class GuavaListenableFutureDeleteOrUpdateMethodBinder(
    private val typeArg: TypeMirror,
    adapter: DeleteOrUpdateMethodAdapter?
) : DeleteOrUpdateMethodBinder(adapter) {

    private val instantDeleteOrUpdateMethodBinder = InstantDeleteOrUpdateMethodBinder(adapter)

    override fun convertAndReturn(
        parameters: List<ShortcutQueryParameter>,
        adapters: Map<String, Pair<FieldSpec, TypeSpec>>,
        scope: CodeGenScope
    ) {
        val callableImpl = createCallableOfT(parameters, adapters, scope)
        scope.builder().apply {
            addStatement(
                "return $T.createListenableFuture($N, $L)",
                RoomGuavaTypeNames.GUAVA_ROOM,
                DaoWriter.dbField,
                callableImpl)
        }
    }

    /**
     * Returns an anonymous subclass of Callable<T> whose implementation to execute the query is
     * generated by an instant adapter.
     */
    private fun createCallableOfT(
        parameters: List<ShortcutQueryParameter>,
        adapters: Map<String, Pair<FieldSpec, TypeSpec>>,
        scope: CodeGenScope
    ): TypeSpec {
        val adapterScope = scope.fork()
        return TypeSpec.anonymousClassBuilder("").apply {
            superclass(
                ParameterizedTypeName.get(java.util.concurrent.Callable::class.typeName(),
                    typeArg.typeName()))
            addMethod(
                MethodSpec.methodBuilder("call").apply {
                    // public T call() throws Exception {}
                    returns(typeArg.typeName())
                    addAnnotation(Override::class.typeName())
                    addModifiers(Modifier.PUBLIC)
                    addException(Exception::class.typeName())

                    // delegate body code generation to the instant method binder
                    instantDeleteOrUpdateMethodBinder.convertAndReturn(
                        parameters = parameters,
                        adapters = adapters,
                        scope = adapterScope
                    )
                    addCode(adapterScope.generate())
                }.build())
        }.build()
    }
}
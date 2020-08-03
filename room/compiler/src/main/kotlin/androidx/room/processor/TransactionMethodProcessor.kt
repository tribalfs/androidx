/*
 * Copyright (C) 2017 The Android Open Source Project
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

package androidx.room.processor

import androidx.room.ext.GuavaUtilConcurrentTypeNames
import androidx.room.ext.LifecyclesTypeNames
import androidx.room.ext.RxJava2TypeNames
import androidx.room.ext.RxJava3TypeNames
import androidx.room.processing.XDeclaredType
import androidx.room.processing.XMethodElement
import androidx.room.vo.TransactionMethod

class TransactionMethodProcessor(
    baseContext: Context,
    val containing: XDeclaredType,
    val executableElement: XMethodElement
) {

    val context = baseContext.fork(executableElement)

    fun process(): TransactionMethod {
        val delegate = MethodProcessorDelegate.createFor(context, containing, executableElement)
        val kotlinDefaultImpl = executableElement.findKotlinDefaultImpl()
        context.checker.check(
                executableElement.isOverrideableIgnoringContainer() &&
                        (!executableElement.isAbstract() || kotlinDefaultImpl != null),
                executableElement, ProcessorErrors.TRANSACTION_METHOD_MODIFIERS)

        val returnType = delegate.extractReturnType()
        val erasureReturnType = returnType.erasure()

        DEFERRED_TYPES.firstOrNull { className ->
            context.processingEnv.findType(className)?.let {
                erasureReturnType.isAssignableFrom(it)
            } ?: false
        }?.let { returnTypeName ->
            context.logger.e(
                ProcessorErrors.transactionMethodAsync(returnTypeName.toString()),
                executableElement
            )
        }

        val callType = when {
            executableElement.isJavaDefault() ->
                TransactionMethod.CallType.DEFAULT_JAVA8
            kotlinDefaultImpl != null ->
                TransactionMethod.CallType.DEFAULT_KOTLIN
            else ->
                TransactionMethod.CallType.CONCRETE
        }

        return TransactionMethod(
                element = executableElement,
                returnType = returnType,
                parameterNames = delegate.extractParams().map { it.name },
                callType = callType,
                methodBinder = delegate.findTransactionMethodBinder(callType))
    }

    companion object {
        val DEFERRED_TYPES = listOf(
            LifecyclesTypeNames.LIVE_DATA,
            RxJava2TypeNames.FLOWABLE,
            RxJava2TypeNames.OBSERVABLE,
            RxJava2TypeNames.MAYBE,
            RxJava2TypeNames.SINGLE,
            RxJava2TypeNames.COMPLETABLE,
            RxJava3TypeNames.FLOWABLE,
            RxJava3TypeNames.OBSERVABLE,
            RxJava3TypeNames.MAYBE,
            RxJava3TypeNames.SINGLE,
            RxJava3TypeNames.COMPLETABLE,
            GuavaUtilConcurrentTypeNames.LISTENABLE_FUTURE)
    }
}

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

package android.arch.lifecycle

import android.arch.lifecycle.model.EventMethod
import android.arch.lifecycle.model.LifecycleObserverInfo
import android.arch.lifecycle.model.getAdapterName
import com.google.auto.common.MoreElements
import com.google.auto.common.MoreTypes
import javax.annotation.processing.ProcessingEnvironment
import javax.annotation.processing.RoundEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.Modifier
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement
import javax.lang.model.type.TypeMirror
import javax.lang.model.util.ElementFilter
import javax.lang.model.util.Elements
import javax.lang.model.util.Types
import javax.tools.Diagnostic

fun collectAndVerifyInput(processingEnv: ProcessingEnvironment,
                          roundEnv: RoundEnvironment): Map<TypeElement, LifecycleObserverInfo> {
    val validator = Validator(processingEnv)
    val worldCollector = ObserversCollector(processingEnv)
    roundEnv.getElementsAnnotatedWith(OnLifecycleEvent::class.java).forEach { elem ->
        if (elem.kind != ElementKind.METHOD) {
            validator.printErrorMessage(ErrorMessages.INVALID_ANNOTATED_ELEMENT, elem)
        } else {
            val enclosingElement = elem.enclosingElement
            if (validator.validateClass(enclosingElement)) {
                worldCollector.collect(MoreElements.asType(enclosingElement))
            }
        }
    }
    return worldCollector.observers
}

class ObserversCollector(processingEnv: ProcessingEnvironment) {
    val typeUtils: Types = processingEnv.typeUtils
    val elementUtils: Elements = processingEnv.elementUtils
    val lifecycleObserverTypeMirror: TypeMirror =
            elementUtils.getTypeElement(LifecycleObserver::class.java.canonicalName).asType()
    val validator = Validator(processingEnv)
    val observers: MutableMap<TypeElement, LifecycleObserverInfo> = mutableMapOf()

    fun collect(type: TypeElement): LifecycleObserverInfo? {
        if (type in observers) {
            return observers[type]
        }
        val parents = (listOf(type.superclass) + type.interfaces)
                .filter { typeUtils.isAssignable(it, lifecycleObserverTypeMirror) }
                .filterNot { typeUtils.isSameType(it, lifecycleObserverTypeMirror) }
                .map { collect(MoreTypes.asTypeElement(it)) }
                .filterNotNull()
        val info = createObserverInfo(type, parents)
        if (info != null) {
            observers[type] = info
        }
        return info
    }

    private fun hasAdapter(type: TypeElement): Boolean {
        val packageName = if (type.getPackageQName().isEmpty()) "" else "${type.getPackageQName()}."
        return elementUtils.getTypeElement(packageName + getAdapterName(type)) != null
    }

    private fun createObserverInfo(typeElement: TypeElement,
                                   parents: List<LifecycleObserverInfo>): LifecycleObserverInfo? {
        if (!validator.validateClass(typeElement)) {
            return null
        }
        val methods = ElementFilter.methodsIn(typeElement.enclosedElements).filter { executable ->
            MoreElements.isAnnotationPresent(executable, OnLifecycleEvent::class.java)
        }.map { executable ->
            val onState = executable.getAnnotation(OnLifecycleEvent::class.java)
            if (validator.validateMethod(executable, onState.value)) {
                EventMethod(executable, onState, typeElement)
            } else {
                null
            }
        }.filterNotNull()
        return LifecycleObserverInfo(typeElement, methods, hasAdapter(typeElement), parents)
    }
}

class Validator(val processingEnv: ProcessingEnvironment) {

    fun printErrorMessage(msg: CharSequence, elem: Element) {
        processingEnv.messager.printMessage(Diagnostic.Kind.ERROR, msg, elem)
    }

    fun validateParam(param: VariableElement,
                      expectedType: Class<*>, errorMsg: String): Boolean {
        if (!MoreTypes.isTypeOf(expectedType, param.asType())) {
            printErrorMessage(errorMsg, param)
            return false
        }
        return true
    }

    fun validateMethod(method: ExecutableElement, event: Lifecycle.Event): Boolean {
        if (Modifier.PRIVATE in method.modifiers) {
            printErrorMessage(ErrorMessages.INVALID_METHOD_MODIFIER, method)
            return false
        }
        val params = method.parameters
        if ((params.size > 2)) {
            printErrorMessage(ErrorMessages.TOO_MANY_ARGS, method)
            return false
        }

        if (params.size == 2 && event != Lifecycle.Event.ON_ANY) {
            printErrorMessage(ErrorMessages.TOO_MANY_ARGS_NOT_ON_ANY, method)
            return false
        }

        if (params.size == 2 && !validateParam(params[1], Lifecycle.Event::class.java,
                ErrorMessages.INVALID_SECOND_ARGUMENT)) {
            return false
        }

        if (params.size > 0) {
            return validateParam(params[0], LifecycleOwner::class.java,
                    ErrorMessages.INVALID_FIRST_ARGUMENT)
        }
        return true
    }

    fun validateClass(classElement: Element): Boolean {
        if (!MoreElements.isType(classElement)) {
            printErrorMessage(ErrorMessages.INVALID_ENCLOSING_ELEMENT, classElement)
            return false
        }
        if (Modifier.PRIVATE in classElement.modifiers) {
            printErrorMessage(ErrorMessages.INVALID_CLASS_MODIFIER, classElement)
            return false
        }
        return true
    }
}

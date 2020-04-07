/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.hilt.work

import androidx.hilt.ClassNames
import androidx.hilt.assisted.toDependencyRequest
import com.google.auto.common.MoreElements
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.ParameterizedTypeName
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/**
 * Data class that represents a Hilt injected Worker
 */
internal data class WorkerInjectElements(
    val typeElement: TypeElement,
    val constructorElement: ExecutableElement
) {
    val className = ClassName.get(typeElement)

    val factoryClassName = ClassName.get(
        MoreElements.getPackage(typeElement).qualifiedName.toString(),
        "${className.simpleNames().joinToString("_")}_AssistedFactory")

    val factorySuperTypeName = ParameterizedTypeName.get(
        ClassNames.WORKER_ASSISTED_FACTORY,
        className)

    val moduleClassName = ClassName.get(
        MoreElements.getPackage(typeElement).qualifiedName.toString(),
        "${className.simpleNames().joinToString("_")}_HiltModule")

    val dependencyRequests = constructorElement.parameters.map { constructorArg ->
        constructorArg.toDependencyRequest { paramTypeName ->
            listOf(ClassNames.CONTEXT, ClassNames.WORKER_PARAMETERS).any { paramTypeName == it }
        }
    }
}
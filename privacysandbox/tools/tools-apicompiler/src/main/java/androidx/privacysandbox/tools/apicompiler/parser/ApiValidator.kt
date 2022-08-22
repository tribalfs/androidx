/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.privacysandbox.tools.apicompiler.parser

import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.isPublic
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.processing.KSPLogger
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSValueParameter
import com.google.devtools.ksp.symbol.Modifier

class ApiValidator(private val logger: KSPLogger, private val resolver: Resolver) {
    private val primitiveTypes = getPrimitiveTypes(resolver.builtIns)

    companion object {
        val validInterfaceModifiers = setOf(Modifier.PUBLIC)
        val validMethodModifiers = setOf(Modifier.PUBLIC, Modifier.SUSPEND)
    }

    fun validateInterface(interfaceDeclaration: KSClassDeclaration) {
        val name = interfaceDeclaration.qualifiedName?.getFullName()
            ?: interfaceDeclaration.simpleName.getFullName()
        if (!interfaceDeclaration.isPublic()) {
            logger.error("Error in $name: annotated interfaces should be public.")
        }
        if (interfaceDeclaration.getDeclaredProperties().any()) {
            logger.error("Error in $name: annotated interfaces cannot declare properties.")
        }
        if (interfaceDeclaration.declarations.filterIsInstance<KSClassDeclaration>()
                .any(KSClassDeclaration::isCompanionObject)
        ) {
            logger.error("Error in $name: annotated interfaces cannot declare companion objects.")
        }
        val invalidModifiers =
            interfaceDeclaration.modifiers.filterNot(validInterfaceModifiers::contains)
        if (invalidModifiers.isNotEmpty()) {
            logger.error(
                "Error in $name: annotated interface contains invalid modifiers (${
                    invalidModifiers.map { it.name.lowercase() }.sorted().joinToString(limit = 3)
                })."
            )
        }
        if (interfaceDeclaration.typeParameters.isNotEmpty()) {
            interfaceDeclaration.typeParameters.map { it.simpleName.getShortName() }.sorted()
                .joinToString(limit = 3)
            logger.error(
                "Error in $name: annotated interfaces cannot declare type parameters (${
                    interfaceDeclaration.typeParameters.map { it.simpleName.getShortName() }
                        .sorted()
                        .joinToString(limit = 3)
                })."
            )
        }
    }

    fun validateMethod(method: KSFunctionDeclaration) {
        val name = method.qualifiedName?.getFullName() ?: method.simpleName.getFullName()
        if (!method.isAbstract) {
            logger.error("Error in $name: method cannot have default implementation.")
        }
        if (method.typeParameters.isNotEmpty()) {
            logger.error("Error in $name: method cannot declare type parameters (<${
                method.typeParameters.joinToString(limit = 3) { it.name.getShortName() }
            }>).")
        }
        val invalidModifiers = method.modifiers.filterNot(validMethodModifiers::contains)
        if (invalidModifiers.isNotEmpty()) {
            logger.error(
                "Error in $name: method contains invalid modifiers (${
                    invalidModifiers.map { it.name.lowercase() }.sorted().joinToString(limit = 3)
                })."
            )
        }
        if (!method.modifiers.contains(Modifier.SUSPEND)) {
            logger.error("Error in $name: method should be a suspend function.")
        }
    }

    fun validateParameter(method: KSFunctionDeclaration, parameter: KSValueParameter) {
        val name = method.qualifiedName?.getFullName() ?: method.simpleName.getFullName()
        if (parameter.hasDefault) {
            logger.error("Error in $name: parameters cannot have default values.")
        }
    }

    fun validateType(method: KSFunctionDeclaration, type: KSType) {
        val name = getMethodName(method)
        if (!primitiveTypes.contains(type)) {
            logger.error("Error in $name: only primitive types are supported.")
        }
    }

    private fun getPrimitiveTypes(builtIns: KSBuiltIns) = listOf(
        builtIns.booleanType,
        builtIns.shortType,
        builtIns.intType,
        builtIns.longType,
        builtIns.floatType,
        builtIns.doubleType,
        builtIns.charType,
        builtIns.stringType,
        builtIns.unitType,
    )

    private fun getMethodName(method: KSFunctionDeclaration) =
        method.qualifiedName?.getFullName() ?: method.simpleName.getFullName()
}
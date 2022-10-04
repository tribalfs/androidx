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

package androidx.privacysandbox.tools.core.validator

import androidx.privacysandbox.tools.core.model.ParsedApi
import androidx.privacysandbox.tools.core.model.Types

class ModelValidator private constructor(val api: ParsedApi) {
    private val errors: MutableList<String> = mutableListOf()

    companion object {
        fun validate(api: ParsedApi) = ModelValidator(api).validate()
    }

    private fun validate(): ValidationResult {
        validateSingleService()
        validateNonSuspendFunctionsReturnUnit()
        validateParameterAndReturnValueTypes()
        return ValidationResult(errors)
    }

    private fun validateSingleService() {
        if (api.services.size > 1) {
            errors.add(
                "Multiple services are not supported. Found: " +
                    "${api.services.joinToString { it.type.qualifiedName }}."
            )
        }
    }

    private fun validateNonSuspendFunctionsReturnUnit() {
        for (service in api.services) {
            for (method in service.methods) {
                if (!method.isSuspend && method.returnType != Types.unit) {
                    errors.add(
                        "Error in ${service.type.qualifiedName}.${method.name}: " +
                            "functions with return values should be suspending functions."
                    )
                }
            }
        }
    }

    private fun validateParameterAndReturnValueTypes() {
        // TODO: allow data classes.
        for (service in api.services) {
            for (method in service.methods) {
                val isAnyTypeInvalid = (method.parameters.map { it.type } + method.returnType).any {
                    !Types.primitiveTypes.contains(it)
                }
                if (isAnyTypeInvalid) {
                    errors.add(
                        "Error in ${service.type.qualifiedName}.${method.name}: " +
                            "only primitive types are supported."
                    )
                }
            }
        }
    }

    // TODO: check that callback methods are fire-and-forget
}

data class ValidationResult(val errors: List<String>) {
    val isSuccess = errors.isEmpty()
    val isFailure = !isSuccess
}
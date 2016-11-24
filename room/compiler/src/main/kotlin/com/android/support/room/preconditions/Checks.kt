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

package com.android.support.room.preconditions

import com.android.support.room.errors.ElementBoundException
import com.android.support.room.processor.ProcessorErrors
import com.squareup.javapoet.ParameterizedTypeName
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeVariableName
import javax.lang.model.element.Element

/**
 * Similar to preconditions but element bound.
 */
object Checks {
    fun check(predicate: Boolean, element: Element, errorMsg: String, vararg args: Any) {
        if (!predicate) {
            throw ElementBoundException(element, String.format(errorMsg, args))
        }
    }

    fun assertNotUnbound(typeName: TypeName, element: Element, errorMsg : String,
                         vararg args : Any) {
        // TODO support bounds cases like <T extends Foo> T bar()
        Checks.check(typeName !is TypeVariableName, element, errorMsg, args)
        if (typeName is ParameterizedTypeName) {
            typeName.typeArguments.forEach { assertNotUnbound(it, element, errorMsg, args) }
        }
    }
}

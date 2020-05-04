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

package androidx.contentaccess.compiler.processor

import androidx.contentaccess.ext.getAllFieldsIncludingPrivateSupers
import asTypeElement
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.type.TypeMirror

class PojoProcessor(val typeMirror: TypeMirror, val processingEnv: ProcessingEnvironment) {
    fun process(): Map<String, TypeMirror> {
        val pojoFields = HashMap<String, TypeMirror>()
        // TODO(obenabde): change this to only consider the constructor params
        typeMirror.asTypeElement().getAllFieldsIncludingPrivateSupers(processingEnv).forEach {
            field ->
            pojoFields.put(field.simpleName.toString(), field.asType())
        }
        return pojoFields
    }
}
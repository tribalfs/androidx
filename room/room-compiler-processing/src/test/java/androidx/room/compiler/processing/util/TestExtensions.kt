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

package androidx.room.compiler.processing.util

import androidx.room.compiler.processing.XExecutableElement
import androidx.room.compiler.processing.XTypeElement

fun XTypeElement.getAllFieldNames() = getAllFieldsIncludingPrivateSupers().map {
    it.name
}.toList()

fun XTypeElement.getDeclaredField(name: String) = getDeclaredFields().first {
    it.name == name
}

fun XTypeElement.getField(name: String) = getAllFieldsIncludingPrivateSupers().first {
    it.name == name
}

fun XTypeElement.getDeclaredMethodByJvmName(jvmName: String) = getDeclaredMethods().firstOrNull {
    it.jvmName == jvmName
} ?: throw AssertionError("cannot find method with name $jvmName")

fun XTypeElement.getMethodByJvmName(jvmName: String) = getAllMethods().firstOrNull {
    it.jvmName == jvmName
} ?: throw AssertionError("cannot find method with jvmName $jvmName")

fun XExecutableElement.getParameter(name: String) = parameters.firstOrNull {
    it.name == name
} ?: throw AssertionError("cannot find parameter with name $name")

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

package com.android.support.room.vo

import android.support.annotation.NonNull
import com.android.support.room.ext.hasAnnotation

/**
 * Used when a field is decomposed inside an Entity or Pojo.
 */
class DecomposedField(val field : Field, val prefix : String = "",
                      val parent : DecomposedField?) {
    val getter by lazy { field.getter }
    val setter by lazy { field.setter }
    val nonNull = field.element.hasAnnotation(NonNull::class)
    lateinit var pojo: Pojo
    val rootParent : DecomposedField by lazy {
        parent?.rootParent ?: this
    }

    fun isDescendantOf(other : DecomposedField) : Boolean {
        if (parent == other) {
            return true
        } else if (parent == null) {
            return false
        } else {
            return parent.isDescendantOf(other)
        }
    }
}

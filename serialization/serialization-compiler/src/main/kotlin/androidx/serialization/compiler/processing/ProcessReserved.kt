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

package androidx.serialization.compiler.processing

import androidx.serialization.Reserved.IdRange
import androidx.serialization.schema.Reserved
import javax.lang.model.element.TypeElement
import androidx.serialization.Reserved as ReservedAnnotation

/**
 * Extract the data from a [androidx.serialization.Reserved] annotation on [element].
 *
 * If no annotation is present, this returns an empty reserved data class. If it encounters an
 * [IdRange] with its `from` greater than its `to`, it reverses them before converting them to an
 * [IntRange], reserving the same range of IDs as if they had been correctly placed.
 */
internal fun processReserved(element: TypeElement): Reserved {
    return when (val reserved = element.getAnnotationMirror(ReservedAnnotation::class)) {
        null -> Reserved.empty()
        else -> Reserved(
            ids = reserved[ReservedAnnotation::ids].toSet(),
            names = reserved[ReservedAnnotation::names].toSet(),
            idRanges = reserved.getAnnotationArray(ReservedAnnotation::idRanges).map { idRange ->
                val from = idRange[IdRange::from]
                val to = idRange[IdRange::to]

                when {
                    from <= to -> from..to
                    else -> to..from
                }
            }.toSet()
        )
    }
}

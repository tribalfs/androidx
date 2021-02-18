/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.resourceinspection.processor

import androidx.resourceinspection.annotation.Attribute
import com.squareup.javapoet.ClassName
import javax.lang.model.element.AnnotationMirror
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement

/** Represents a view with annotated attributes, mostly a convenience class. */
internal data class ViewIR(
    val type: TypeElement,
    val attributes: List<AttributeIR>
) {
    val className: ClassName = ClassName.get(type)
}

/** Represents an [Attribute] with its getter. */
internal data class AttributeIR(
    val getter: ExecutableElement,
    val annotation: AnnotationMirror,
    val namespace: String,
    val name: String,
    val type: AttributeTypeIR,
    val intMapping: List<IntMapIR>
)

/** Represents an [Attribute.IntMap] entry. */
internal data class IntMapIR(
    val name: String,
    val value: Int,
    val mask: Int
)

/** Represents the type of the attribute, determined from context and the annotation itself. */
internal enum class AttributeTypeIR(val apiSuffix: String) {
    BOOLEAN("Boolean"),
    BYTE("Byte"),
    CHAR("Char"),
    DOUBLE("Double"),
    FLOAT("Float"),
    INT("Int"),
    LONG("Long"),
    SHORT("Short"),
    OBJECT("Object"),

    COLOR("Color"),
    // TODO(b/180040871): Gravity support
    // GRAVITY("Gravity"),
    RESOURCE_ID("ResourceId"),
    INT_FLAG("IntFlag"),
    INT_ENUM("IntEnum")
}

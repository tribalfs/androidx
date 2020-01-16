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

package androidx.serialization.compiler.processing.steps

import androidx.serialization.EnumValue
import androidx.serialization.Reserved
import androidx.serialization.compiler.codegen.CodeGenEnvironment
import androidx.serialization.compiler.codegen.java.JavaGenEnvironment
import androidx.serialization.compiler.codegen.java.generateEnumCoder
import androidx.serialization.compiler.processing.asTypeElement
import androidx.serialization.compiler.processing.asVariableElement
import androidx.serialization.compiler.processing.error
import androidx.serialization.compiler.processing.get
import androidx.serialization.compiler.processing.getAnnotationMirror
import androidx.serialization.compiler.processing.isVisibleToPackage
import androidx.serialization.compiler.processing.isPrivate
import androidx.serialization.compiler.processing.processReserved
import androidx.serialization.compiler.schema.Enum
import javax.annotation.processing.Messager
import javax.annotation.processing.ProcessingEnvironment
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ElementKind.ENUM_CONSTANT
import javax.lang.model.element.TypeElement
import kotlin.reflect.KClass

/** Processing step that parses and validates enums, and generates enum coders. */
internal class EnumProcessingStep(
    private val processingEnv: ProcessingEnvironment,
    private val codeGenEnv: CodeGenEnvironment,
    private val onEnum: ((Enum) -> Unit)? = null
) : AbstractProcessingStep(EnumValue::class, Reserved::class) {
    private val javaGenEnv = JavaGenEnvironment(codeGenEnv)
    private val messager: Messager = processingEnv.messager

    override fun process(elementsByAnnotation: Map<KClass<out Annotation>, Set<Element>>) {
        elementsByAnnotation[EnumValue::class]
            ?.let(::processEnumValues)
            ?.forEach(::processEnumClass)
    }

    /**
     * Process [EnumValue] annotations present in a round.
     *
     * This method validates the placement of the annotations on enum constants, and returns a
     * set of type elements representing the enum classes that have at least one [EnumValue]
     * annotation on an enum constant.
     */
    private fun processEnumValues(elements: Set<Element>): Set<TypeElement> {
        if (elements.isEmpty()) return emptySet()

        val enumClasses = mutableSetOf<TypeElement>()

        for (element in elements) {
            if (element.kind == ENUM_CONSTANT) {
                enumClasses += element.enclosingElement.asTypeElement()
            } else {
                messager.error(element, EnumValue::class) {
                    "@${EnumValue::class.simpleName} must annotate an enum constant"
                }
            }
        }

        return enumClasses
    }

    /**
     * Process a type element representing a serializable enum class.
     *
     * This method operates by validating that the enum class is not private, then walking its
     * enum constants. It validates that all constants have an [EnumValue] annotation, and then
     * reads [EnumValue.id] and constructs an [Enum] and dispatches it to [onEnum]. It fills
     * [Enum.reserved] using [processReserved].
     */
    private fun processEnumClass(enumClass: TypeElement) {
        check(enumClass.kind == ElementKind.ENUM) {
            "Expected $enumClass to be an enum class"
        }

        var hasError = false

        if (!enumClass.isVisibleToPackage()) {
            if (enumClass.isPrivate()) {
                messager.error(enumClass) {
                    "Enum ${enumClass.qualifiedName} is private and cannot be serialized"
                }
            } else {
                messager.error(enumClass) {
                    "Enum ${enumClass.qualifiedName} is not visible to its package and cannot " +
                            "be serialized"
                }
            }

            hasError = true
        }

        val values = mutableSetOf<Enum.Value>()

        for (element in enumClass.enclosedElements) {
            if (element.kind == ENUM_CONSTANT) {
                val annotation = element.getAnnotationMirror(EnumValue::class)

                if (annotation != null) {
                    values += Enum.Value(element.asVariableElement(), annotation[EnumValue::id])
                } else {
                    messager.error(element) {
                        "To avoid unexpected behavior, all enum constants in a serializable enum " +
                                "must be annotated with @${EnumValue::class.simpleName}"
                    }
                    hasError = true
                }
            }
        }

        if (!hasError) {
            val enum = Enum(enumClass, values, processReserved(enumClass))
            generateEnumCoder(enum, javaGenEnv).writeTo(processingEnv.filer)
            onEnum?.invoke(enum)
        }
    }
}

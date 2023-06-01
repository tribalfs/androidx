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

package androidx.room.compiler.processing.javac.kotlin

import androidx.room.compiler.processing.XArrayType
import androidx.room.compiler.processing.XEnumTypeElement
import androidx.room.compiler.processing.XMethodElement
import androidx.room.compiler.processing.XNullability
import androidx.room.compiler.processing.javac.JavacKmAnnotation
import androidx.room.compiler.processing.javac.JavacKmAnnotationValue
import androidx.room.compiler.processing.javac.JavacProcessingEnv
import androidx.room.compiler.processing.util.sanitizeAsJavaParameterName
import javax.lang.model.element.Element
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.tools.Diagnostic
import kotlinx.metadata.Flag
import kotlinx.metadata.Flags
import kotlinx.metadata.KmAnnotation
import kotlinx.metadata.KmAnnotationArgument
import kotlinx.metadata.KmClass
import kotlinx.metadata.KmClassifier
import kotlinx.metadata.KmConstructor
import kotlinx.metadata.KmFunction
import kotlinx.metadata.KmProperty
import kotlinx.metadata.KmType
import kotlinx.metadata.KmTypeParameter
import kotlinx.metadata.KmValueParameter
import kotlinx.metadata.jvm.KotlinClassMetadata
import kotlinx.metadata.jvm.annotations
import kotlinx.metadata.jvm.getterSignature
import kotlinx.metadata.jvm.setterSignature
import kotlinx.metadata.jvm.signature

internal interface KmFlags {
    val flags: Flags
}

internal class KmClassContainer(
    private val kmClass: KmClass
) : KmFlags {
    override val flags: Flags
        get() = kmClass.flags

    val type: KmTypeContainer by lazy {
        KmTypeContainer(
            kmType = KmType(flags).apply {
                classifier = KmClassifier.Class(kmClass.name)
            },
            typeArguments = kmClass.typeParameters.map { kmTypeParameter ->
                KmTypeContainer(
                    kmType = KmType(kmTypeParameter.flags).apply {
                        classifier = KmClassifier.Class(kmTypeParameter.name)
                    },
                    typeArguments = emptyList(),
                    upperBounds = kmTypeParameter.upperBounds.map { it.asContainer() }
                )
            }
        )
    }

    val superType: KmTypeContainer? by lazy {
        kmClass.supertypes.firstOrNull()?.asContainer()
    }

    val superTypes: List<KmTypeContainer> by lazy {
        kmClass.supertypes.map { it.asContainer() }
    }

    val typeParameters: List<KmTypeParameterContainer> by lazy {
        kmClass.typeParameters.map { it.asContainer() }
    }

    private val functionList: List<KmFunctionContainer> by lazy {
        kmClass.functions.map { it.asContainer() }
    }

    private val constructorList: List<KmConstructorContainer> by lazy {
        kmClass.constructors.map { it.asContainer(type) }
    }

    private val propertyList: List<KmPropertyContainer> by lazy {
        kmClass.properties.map { it.asContainer() }
    }

    val primaryConstructorSignature: String? by lazy {
        constructorList.firstOrNull { it.isPrimary() }?.descriptor
    }

    fun isObject() = Flag.Class.IS_OBJECT(flags)
    fun isCompanionObject() = Flag.Class.IS_COMPANION_OBJECT(flags)
    fun isAnnotationClass() = Flag.Class.IS_ANNOTATION_CLASS(flags)
    fun isClass() = Flag.Class.IS_CLASS(flags)
    fun isInterface() = Flag.Class.IS_INTERFACE(flags)
    fun isDataClass() = Flag.Class.IS_DATA(flags)
    fun isValueClass() = Flag.Class.IS_VALUE(flags)
    fun isFunctionalInterface() = Flag.Class.IS_FUN(flags)
    fun isExpect() = Flag.Class.IS_EXPECT(flags)

    fun getFunctionMetadata(method: ExecutableElement): KmFunctionContainer? {
        check(method.kind == ElementKind.METHOD) {
            "must pass an element type of method"
        }
        val methodSignature = method.descriptor()
        functionList.firstOrNull { it.descriptor == methodSignature }?.let {
            return it
        }
        // might be a property getter or setter
        return propertyList.firstNotNullOfOrNull { property ->
            when {
                property.getter?.descriptor == methodSignature -> {
                    property.getter
                }

                property.setter?.descriptor == methodSignature -> {
                    property.setter
                }

                else -> {
                    null
                }
            }
        }
    }

    fun getConstructorMetadata(method: ExecutableElement): KmConstructorContainer? {
        check(method.kind == ElementKind.CONSTRUCTOR) {
            "must pass an element type of constructor"
        }
        val methodSignature = method.descriptor()
        return constructorList.firstOrNull { it.descriptor == methodSignature }
    }

    fun getPropertyMetadata(propertyName: String): KmPropertyContainer? =
        propertyList.firstOrNull { it.name == propertyName }

    companion object {
        /**
         * Creates a [KmClassContainer] for the given element if it contains Kotlin metadata,
         * otherwise this method returns null.
         *
         * Usually the [element] passed must represent a class. For example, if Kotlin metadata is
         * desired for a method, then the containing class should be used as parameter.
         */
        fun createFor(env: JavacProcessingEnv, element: Element): KmClassContainer? {
            val metadataAnnotation = getMetadataAnnotation(element) ?: return null
            val classMetadata = KotlinClassMetadata.read(metadataAnnotation)
            if (classMetadata == null) {
                env.delegate.messager.printMessage(
                    Diagnostic.Kind.WARNING,
                    "Unable to read Kotlin metadata due to unsupported metadata version.",
                    element
                )
            }
            return when (classMetadata) {
                is KotlinClassMetadata.Class -> KmClassContainer(classMetadata.toKmClass())
                // Synthetic classes generated for various Kotlin features ($DefaultImpls,
                // $WhenMappings, etc) are ignored because the data contained does not affect
                // the metadata derived APIs. These classes are never referenced by user code but
                // could be discovered by processors when inspecting inner classes.
                is KotlinClassMetadata.SyntheticClass,
                // Multi file classes are also ignored, the elements contained in these might be
                // referenced by user code in method bodies but not part of the AST, however it
                // is possible for a processor to discover them by inspecting elements under a
                // package.
                is KotlinClassMetadata.FileFacade,
                is KotlinClassMetadata.MultiFileClassFacade,
                is KotlinClassMetadata.MultiFileClassPart -> null
                else -> {
                    env.delegate.messager.printMessage(
                        Diagnostic.Kind.ERROR,
                        "Unable to read Kotlin metadata due to unsupported metadata " +
                            "kind: $classMetadata.",
                        element
                    )
                    null
                }
            }
        }

        /**
         * Search for Kotlin's Metadata annotation across the element's hierarchy.
         */
        private fun getMetadataAnnotation(element: Element?): Metadata? =
            if (element != null) {
                element.getAnnotation(Metadata::class.java)
                    ?: getMetadataAnnotation(element.enclosingElement)
            } else {
                null
            }
    }
}

internal interface KmFunctionContainer : KmFlags {
    /** Name of the function in source code **/
    val name: String
    /** Name of the function in byte code **/
    val jvmName: String
    val descriptor: String
    val typeParameters: List<KmTypeParameterContainer>
    val parameters: List<KmValueParameterContainer>
    val returnType: KmTypeContainer

    fun isPropertyFunction(): Boolean = this is KmPropertyFunctionContainerImpl
    fun isSuspend() = Flag.Function.IS_SUSPEND(flags)
    fun isExtension() =
        this is KmFunctionContainerImpl && this.kmFunction.receiverParameterType != null
}

private class KmFunctionContainerImpl(
    val kmFunction: KmFunction,
    override val returnType: KmTypeContainer,
) : KmFunctionContainer {
    override val flags: Flags
        get() = kmFunction.flags
    override val name: String
        get() = kmFunction.name
    override val jvmName: String
        get() = kmFunction.signature!!.name
    override val descriptor: String
        get() = kmFunction.signature!!.asString()
    override val typeParameters: List<KmTypeParameterContainer>
        get() = kmFunction.typeParameters.map { it.asContainer() }
    override val parameters: List<KmValueParameterContainer>
        get() = kmFunction.valueParameters.map { it.asContainer() }
}

private class KmPropertyFunctionContainerImpl(
    override val flags: Flags,
    override val name: String,
    override val jvmName: String,
    override val descriptor: String,
    override val parameters: List<KmValueParameterContainer>,
    override val returnType: KmTypeContainer,
) : KmFunctionContainer {
    override val typeParameters: List<KmTypeParameterContainer> = emptyList()
}

internal class KmConstructorContainer(
    private val kmConstructor: KmConstructor,
    override val returnType: KmTypeContainer
) : KmFunctionContainer {
    override val flags: Flags
        get() = kmConstructor.flags
    override val name: String = "<init>"
    override val jvmName: String = name
    override val descriptor: String
        get() = checkNotNull(kmConstructor.signature).asString()
    override val typeParameters: List<KmTypeParameterContainer> = emptyList()
    override val parameters: List<KmValueParameterContainer> by lazy {
        kmConstructor.valueParameters.map { it.asContainer() }
    }
    fun isPrimary() = !Flag.Constructor.IS_SECONDARY(flags)
}

internal class KmPropertyContainer(
    private val kmProperty: KmProperty,
    val type: KmTypeContainer,
    val getter: KmFunctionContainer?,
    val setter: KmFunctionContainer?
) : KmFlags {
    override val flags: Flags
        get() = kmProperty.flags
    val name: String
        get() = kmProperty.name
    val typeParameters: List<KmTypeContainer>
        get() = type.typeArguments
    fun isNullable() = type.isNullable()
}

internal class KmTypeContainer(
    private val kmType: KmType,
    val typeArguments: List<KmTypeContainer>,
    /** The extends bounds are only non-null for wildcard (i.e. in/out variant) types. */
    val extendsBound: KmTypeContainer? = null,
    /** The upper bounds are only non-null for type variable types with upper bounds. */
    val upperBounds: List<KmTypeContainer>? = null
) : KmFlags {
    override val flags: Flags
        get() = kmType.flags

    val className: String? = kmType.classifier.let {
        when (it) {
            is KmClassifier.Class -> it.name.replace('/', '.')
            else -> null
        }
    }

    val annotations = kmType.annotations.map { it.asContainer() }

    fun isExtensionType() =
        kmType.annotations.any { it.className == "kotlin/ExtensionFunctionType" }
    fun isNullable() = Flag.Type.IS_NULLABLE(flags)

    fun erasure(): KmTypeContainer = KmTypeContainer(
        kmType = kmType,
        typeArguments = emptyList(),
        extendsBound = extendsBound?.erasure(),
        // The erasure of a type variable is equal to the erasure of the first upper bound.
        upperBounds = upperBounds?.firstOrNull()?.erasure()?.let { listOf(it) },
    )
}

internal class KmAnnotationContainer(private val kmAnnotation: KmAnnotation) {
    val className = kmAnnotation.className.replace('/', '.')
    fun getArguments(env: JavacProcessingEnv): Map<String, KmAnnotationArgumentContainer> {
        return kmAnnotation.arguments.mapValues { (_, arg) ->
            arg.asContainer(env)
        }
    }
}

internal class KmAnnotationArgumentContainer(
    private val env: JavacProcessingEnv,
    private val kmAnnotationArgument: KmAnnotationArgument
) {
    fun getValue(method: XMethodElement): Any? {
        return kmAnnotationArgument.let {
            when (it) {
                is KmAnnotationArgument.LiteralValue<*> -> it.value
                is KmAnnotationArgument.ArrayValue -> {
                    it.elements.map {
                        val valueType = (method.returnType as XArrayType).componentType
                        JavacKmAnnotationValue(method, valueType, it.asContainer(env))
                    }
                }
                is KmAnnotationArgument.EnumValue -> {
                    val enumTypeElement = env.findTypeElement(
                        it.enumClassName.replace('/', '.')) as XEnumTypeElement
                    enumTypeElement.entries.associateBy { it.name }[it.enumEntryName]
                }
                is KmAnnotationArgument.AnnotationValue -> {
                    val kmAnnotation = KmAnnotation(
                        it.annotation.className,
                        it.annotation.arguments
                    ).asContainer()
                    JavacKmAnnotation(env, kmAnnotation)
                }
                is KmAnnotationArgument.KClassValue -> {
                    env.requireType(it.className.replace('/', '.'))
                }
            }
        }
    }
}

internal val KmTypeContainer.nullability: XNullability
    get() = if (isNullable()) {
        XNullability.NULLABLE
    } else {
        // if there is an upper bound information, use its nullability (e.g. it might be T : Foo?)
        if (upperBounds?.all { it.nullability == XNullability.NULLABLE } == true) {
            XNullability.NULLABLE
        } else {
            extendsBound?.nullability ?: XNullability.NONNULL
        }
    }

internal class KmTypeParameterContainer(
    private val kmTypeParameter: KmTypeParameter,
    val upperBounds: List<KmTypeContainer>
) : KmFlags {
    override val flags: Flags
        get() = kmTypeParameter.flags
    val name: String
        get() = kmTypeParameter.name
}

internal class KmValueParameterContainer(
    private val kmValueParameter: KmValueParameter,
    val type: KmTypeContainer
) : KmFlags {
    override val flags: Flags
        get() = kmValueParameter.flags
    val name: String
        get() = kmValueParameter.name
    fun isVarArgs() = kmValueParameter.varargElementType != null
    fun isNullable() = type.isNullable()
    fun hasDefault() = Flag.ValueParameter.DECLARES_DEFAULT_VALUE(flags)
}

private fun KmFunction.asContainer(): KmFunctionContainer =
    KmFunctionContainerImpl(
        kmFunction = this,
        returnType = this.returnType.asContainer()
    )

private fun KmConstructor.asContainer(returnType: KmTypeContainer): KmConstructorContainer =
    KmConstructorContainer(
        kmConstructor = this,
        returnType = returnType
    )

private fun KmProperty.asContainer(): KmPropertyContainer =
    KmPropertyContainer(
        kmProperty = this,
        type = this.returnType.asContainer(),
        getter = getterSignature?.let {
            KmPropertyFunctionContainerImpl(
                flags = this.getterFlags,
                name = JvmAbi.computeGetterName(this.name),
                jvmName = it.name,
                descriptor = it.asString(),
                parameters = emptyList(),
                returnType = this.returnType.asContainer(),
            )
        },
        setter = setterSignature?.let {
            // setter parameter visitor may not be available when not declared explicitly
            val param = this.setterParameter ?: KmValueParameter(
                flags = 0,
                // kotlinc will set this to set-? but it is better to not expose
                // it here since it is not valid name
                name = "set-?".sanitizeAsJavaParameterName(0)
            ).apply { type = this@asContainer.returnType }
            val returnType = KmType(0).apply { classifier = KmClassifier.Class("Unit") }
            KmPropertyFunctionContainerImpl(
                flags = this.setterFlags,
                name = JvmAbi.computeSetterName(this.name),
                jvmName = it.name,
                descriptor = it.asString(),
                parameters = listOf(param.asContainer()),
                returnType = returnType.asContainer(),
            )
        },
    )

private fun KmType.asContainer(): KmTypeContainer =
    KmTypeContainer(
        kmType = this,
        typeArguments = this.arguments.mapNotNull { it.type?.asContainer() }
    )

private fun KmTypeParameter.asContainer(): KmTypeParameterContainer =
    KmTypeParameterContainer(
        kmTypeParameter = this,
        upperBounds = this.upperBounds.map { it.asContainer() }
    )

private fun KmValueParameter.asContainer(): KmValueParameterContainer =
    KmValueParameterContainer(
        kmValueParameter = this,
        type = this.type.asContainer()
    )

private fun KmAnnotation.asContainer() = KmAnnotationContainer(this)

private fun KmAnnotationArgument.asContainer(env: JavacProcessingEnv) =
    KmAnnotationArgumentContainer(env, this)

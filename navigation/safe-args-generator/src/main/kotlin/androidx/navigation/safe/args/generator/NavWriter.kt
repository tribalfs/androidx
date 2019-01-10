/*
 * Copyright 2017 The Android Open Source Project
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

package androidx.navigation.safe.args.generator

import androidx.navigation.safe.args.generator.ext.BEGIN_STMT
import androidx.navigation.safe.args.generator.ext.END_STMT
import androidx.navigation.safe.args.generator.ext.L
import androidx.navigation.safe.args.generator.ext.N
import androidx.navigation.safe.args.generator.ext.S
import androidx.navigation.safe.args.generator.ext.T
import androidx.navigation.safe.args.generator.ext.toCamelCase
import androidx.navigation.safe.args.generator.ext.toCamelCaseAsVar
import androidx.navigation.safe.args.generator.models.Action
import androidx.navigation.safe.args.generator.models.Argument
import androidx.navigation.safe.args.generator.models.Destination
import androidx.navigation.safe.args.generator.models.accessor
import com.squareup.javapoet.AnnotationSpec
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.CodeBlock
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.ParameterSpec
import com.squareup.javapoet.TypeName
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

private const val NAVIGATION_PACKAGE = "androidx.navigation"
private val NAV_ARGS_CLASSNAME: ClassName = ClassName.get(NAVIGATION_PACKAGE, "NavArgs")
private val NAV_DIRECTION_CLASSNAME: ClassName = ClassName.get(NAVIGATION_PACKAGE, "NavDirections")
internal val HASHMAP_CLASSNAME: ClassName = ClassName.get("java.util", "HashMap")
internal val BUNDLE_CLASSNAME: ClassName = ClassName.get("android.os", "Bundle")

internal abstract class Annotations {
    abstract val NULLABLE_CLASSNAME: ClassName
    abstract val NONNULL_CLASSNAME: ClassName

    private object AndroidAnnotations : Annotations() {
        override val NULLABLE_CLASSNAME = ClassName.get("android.support.annotation", "Nullable")
        override val NONNULL_CLASSNAME = ClassName.get("android.support.annotation", "NonNull")
    }

    private object AndroidXAnnotations : Annotations() {
        override val NULLABLE_CLASSNAME = ClassName.get("androidx.annotation", "Nullable")
        override val NONNULL_CLASSNAME = ClassName.get("androidx.annotation", "NonNull")
    }

    companion object {
        fun getInstance(useAndroidX: Boolean): Annotations {
            if (useAndroidX) {
                return AndroidXAnnotations
            } else {
                return AndroidAnnotations
            }
        }
    }
}

private class ClassWithArgsSpecs(
    val args: List<Argument>,
    val annotations: Annotations
) {

    val suppressAnnotationSpec = AnnotationSpec.builder(SuppressWarnings::class.java)
        .addMember("value", "$S", "unchecked")
        .build()

    val hashMapFieldSpec = FieldSpec.builder(
        HASHMAP_CLASSNAME,
        "arguments",
        Modifier.PRIVATE,
        Modifier.FINAL
    ).initializer("new $T()", HASHMAP_CLASSNAME).build()

    fun setters(thisClassName: ClassName) = args.map { arg ->
        MethodSpec.methodBuilder("set${arg.sanitizedName.capitalize()}").apply {
            addAnnotation(annotations.NONNULL_CLASSNAME)
            addModifiers(Modifier.PUBLIC)
            addParameter(generateParameterSpec(arg))
            addNullCheck(arg, arg.sanitizedName)
            addStatement(
                "this.$N.put($S, $N)",
                hashMapFieldSpec.name,
                arg.name,
                arg.sanitizedName
            )
            addStatement("return this")
            returns(thisClassName)
        }.build()
    }

    fun constructor() = MethodSpec.constructorBuilder().apply {
        addModifiers(Modifier.PUBLIC)
        args.filterNot(Argument::isOptional).forEach { arg ->
            addParameter(generateParameterSpec(arg))
            addNullCheck(arg, arg.sanitizedName)
            addStatement(
                "this.$N.put($S, $N)",
                hashMapFieldSpec.name,
                arg.name,
                arg.sanitizedName
            )
        }
    }.build()

    fun toBundleMethod(
        name: String,
        addOverrideAnnotation: Boolean = false
    ) = MethodSpec.methodBuilder(name).apply {
        if (addOverrideAnnotation) {
            addAnnotation(Override::class.java)
        }
        addAnnotation(suppressAnnotationSpec)
        addAnnotation(annotations.NONNULL_CLASSNAME)
        addModifiers(Modifier.PUBLIC)
        returns(BUNDLE_CLASSNAME)
        val result = "__result"
        addStatement("$T $N = new $T()", BUNDLE_CLASSNAME, result, BUNDLE_CLASSNAME)
        args.forEach { arg ->
            beginControlFlow("if ($N.containsKey($S))", hashMapFieldSpec.name, arg.name).apply {
                addStatement(
                    "$T $N = ($T) $N.get($S)",
                    arg.type.typeName(),
                    arg.sanitizedName,
                    arg.type.typeName(),
                    hashMapFieldSpec.name,
                    arg.name
                )
                arg.type.addBundlePutStatement(this, arg, result, arg.sanitizedName)
            }
            endControlFlow()
        }
        addStatement("return $N", result)
    }.build()

    fun copyMapContents(to: String, from: String) = CodeBlock.builder()
        .addStatement(
        "$N.$N.putAll($N.$N)",
        to,
        hashMapFieldSpec.name,
        from,
        hashMapFieldSpec.name
        ).build()

    fun getters() = args.map { arg ->
        MethodSpec.methodBuilder(getterFromArgName(arg.sanitizedName)).apply {
            addModifiers(Modifier.PUBLIC)
            addAnnotation(suppressAnnotationSpec)
            if (arg.type.allowsNullable()) {
                if (arg.isNullable) {
                    addAnnotation(annotations.NULLABLE_CLASSNAME)
                } else {
                    addAnnotation(annotations.NONNULL_CLASSNAME)
                }
            }
            addStatement(
                "return ($T) $N.get($S)",
                arg.type.typeName(),
                hashMapFieldSpec.name,
                arg.name
            )
            returns(arg.type.typeName())
        }.build()
    }

    fun equalsMethod(
        className: ClassName,
        additionalCode: CodeBlock? = null
    ) = MethodSpec.methodBuilder("equals").apply {
        addAnnotation(Override::class.java)
        addModifiers(Modifier.PUBLIC)
        addParameter(TypeName.OBJECT, "object")
        addCode("""
                if (this == object) {
                    return true;
                }
                if (object == null || getClass() != object.getClass()) {
                    return false;
                }

                """.trimIndent())
        addStatement("$T that = ($T) object", className, className)
        args.forEach { (name, type, _, _, sanitizedName) ->
            beginControlFlow(
                "if ($N.containsKey($S) != that.$N.containsKey($S))",
                hashMapFieldSpec,
                name,
                hashMapFieldSpec,
                name
            ).apply {
                addStatement("return false")
            }.endControlFlow()
            val getterName = getterFromArgName(sanitizedName, "()")
            val compareExpression = when (type) {
                IntType,
                BoolType,
                ReferenceType,
                LongType -> "$getterName != that.$getterName"
                FloatType -> "Float.compare(that.$getterName, $getterName) != 0"
                StringType, IntArrayType, LongArrayType, FloatArrayType, StringArrayType,
                BoolArrayType, ReferenceArrayType, is ObjectArrayType, is ObjectType ->
                    "$getterName != null ? !$getterName.equals(that.$getterName) " +
                        ": that.$getterName != null"
            }
            beginControlFlow("if ($N)", compareExpression).apply {
                addStatement("return false")
            }
            endControlFlow()
        }
        if (additionalCode != null) {
            addCode(additionalCode)
        }
        addStatement("return true")
        returns(TypeName.BOOLEAN)
    }.build()

    private fun getterFromArgName(sanitizedName: String, suffix: String = "") =
        "get${sanitizedName.capitalize()}$suffix"

    fun hashCodeMethod(
        additionalCode: CodeBlock? = null
    ) = MethodSpec.methodBuilder("hashCode").apply {
        addAnnotation(Override::class.java)
        addModifiers(Modifier.PUBLIC)
        addStatement("int result = super.hashCode()")
        args.forEach { (_, type, _, _, sanitizedName) ->
            val getterName = getterFromArgName(sanitizedName, "()")
            val hashCodeExpression = when (type) {
                IntType, ReferenceType -> getterName
                FloatType -> "Float.floatToIntBits($getterName)"
                IntArrayType, LongArrayType, FloatArrayType, StringArrayType,
                BoolArrayType, ReferenceArrayType, is ObjectArrayType ->
                    "java.util.Arrays.hashCode($getterName)"
                StringType, is ObjectType ->
                    "($getterName != null ? $getterName.hashCode() : 0)"
                BoolType -> "($getterName ? 1 : 0)"
                LongType -> "(int)($getterName ^ ($getterName >>> 32))"
            }
            addStatement("result = 31 * result + $N", hashCodeExpression)
        }
        if (additionalCode != null) {
            addCode(additionalCode)
        }
        addStatement("return result")
        returns(TypeName.INT)
    }.build()

    fun toStringMethod(
        className: ClassName,
        toStringHeaderBlock: CodeBlock? = null
    ) = MethodSpec.methodBuilder("toString").apply {
        addAnnotation(Override::class.java)
        addModifiers(Modifier.PUBLIC)
        addCode(CodeBlock.builder().apply {
            if (toStringHeaderBlock != null) {
                add("${BEGIN_STMT}return $L", toStringHeaderBlock)
            } else {
                add("${BEGIN_STMT}return $S", "${className.simpleName()}{")
            }
            args.forEachIndexed { index, (_, _, _, _, sanitizedName) ->
                val getterName = getterFromArgName(sanitizedName, "()")
                val prefix = if (index == 0) "" else ", "
                add("\n+ $S + $L", "$prefix$sanitizedName=", getterName)
            }
            add("\n+ $S;\n$END_STMT", "}")
        }.build())
        returns(ClassName.get(String::class.java))
    }.build()

    private fun generateParameterSpec(arg: Argument): ParameterSpec {
        return ParameterSpec.builder(arg.type.typeName(), arg.sanitizedName).apply {
            if (arg.type.allowsNullable()) {
                if (arg.isNullable) {
                    addAnnotation(annotations.NULLABLE_CLASSNAME)
                } else {
                    addAnnotation(annotations.NONNULL_CLASSNAME)
                }
            }
        }.build()
    }
}

fun generateDestinationDirectionsTypeSpec(
    className: ClassName,
    superclassName: TypeName?,
    destination: Destination,
    useAndroidX: Boolean
): TypeSpec {
    val actionTypes = destination.actions.map { action ->
        action to generateDirectionsTypeSpec(action, useAndroidX)
    }

    val getters = actionTypes
            .map { (action, actionType) ->
                val annotations = Annotations.getInstance(useAndroidX)
                val constructor = actionType.methodSpecs.find(MethodSpec::isConstructor)!!
                val params = constructor.parameters.joinToString(", ") { param -> param.name }
                val actionTypeName = ClassName.get("", actionType.name)
                MethodSpec.methodBuilder(action.id.javaIdentifier.toCamelCaseAsVar())
                        .addAnnotation(annotations.NONNULL_CLASSNAME)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameters(constructor.parameters)
                        .returns(actionTypeName)
                        .addStatement("return new $T($params)", actionTypeName)
                        .build()
            }

    return TypeSpec.classBuilder(className)
            .superclass(superclassName ?: ClassName.OBJECT)
            .addModifiers(Modifier.PUBLIC)
            .addTypes(actionTypes.map { (_, actionType) -> actionType })
            .addMethods(getters)
            .build()
}

fun generateDirectionsTypeSpec(action: Action, useAndroidX: Boolean): TypeSpec {
    val annotations = Annotations.getInstance(useAndroidX)
    val specs = ClassWithArgsSpecs(action.args, annotations)
    val className = ClassName.get("", action.id.javaIdentifier.toCamelCase())

    val getDestIdMethod = MethodSpec.methodBuilder("getActionId")
            .addAnnotation(Override::class.java)
            .addModifiers(Modifier.PUBLIC)
            .returns(Int::class.java)
            .addStatement("return $N", action.id.accessor())
            .build()

    val additionalEqualsBlock = CodeBlock.builder().apply {
        beginControlFlow("if ($N() != that.$N())", getDestIdMethod, getDestIdMethod).apply {
            addStatement("return false")
        }
        endControlFlow()
    }.build()

    val additionalHashCodeBlock = CodeBlock.builder().apply {
        addStatement("result = 31 * result + $N()", getDestIdMethod)
    }.build()

    val toStringHeaderBlock = CodeBlock.builder().apply {
        add("$S + $L() + $S", "${className.simpleName()}(actionId=", getDestIdMethod.name, "){")
    }.build()

    return TypeSpec.classBuilder(className)
            .addSuperinterface(NAV_DIRECTION_CLASSNAME)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addField(specs.hashMapFieldSpec)
            .addMethod(specs.constructor())
            .addMethods(specs.setters(className))
            .addMethod(specs.toBundleMethod("getArguments", true))
            .addMethod(getDestIdMethod)
            .addMethods(specs.getters())
            .addMethod(specs.equalsMethod(className, additionalEqualsBlock))
            .addMethod(specs.hashCodeMethod(additionalHashCodeBlock))
            .addMethod(specs.toStringMethod(className, toStringHeaderBlock))
            .build()
}

internal fun generateArgsJavaFile(destination: Destination, useAndroidX: Boolean): JavaFile {
    val annotations = Annotations.getInstance(useAndroidX)
    val destName = destination.name
            ?: throw IllegalStateException("Destination with arguments must have name")
    val className = ClassName.get(destName.packageName(), "${destName.simpleName()}Args")
    val args = destination.args
    val specs = ClassWithArgsSpecs(args, annotations)

    val bundleConstructor = MethodSpec.constructorBuilder().apply {
        addModifiers(Modifier.PUBLIC)
        addAnnotation(specs.suppressAnnotationSpec)
        val bundle = "bundle"
        addParameter(
            ParameterSpec.builder(BUNDLE_CLASSNAME, bundle)
                .addAnnotation(specs.annotations.NONNULL_CLASSNAME)
                .build()
        )
        addStatement("$N.setClassLoader($T.class.getClassLoader())", bundle, className)
        args.forEach { arg ->
            beginControlFlow("if ($N.containsKey($S))", bundle, arg.name).apply {
                addStatement("$T $N", arg.type.typeName(), arg.sanitizedName)
                arg.type.addBundleGetStatement(this, arg, arg.sanitizedName, bundle)
                addNullCheck(arg, arg.sanitizedName)
                addStatement(
                    "$N.$N.put($S, $N)",
                    "this",
                    specs.hashMapFieldSpec.name,
                    arg.name,
                    arg.sanitizedName
                )
            }
            if (!arg.isOptional()) {
                nextControlFlow("else")
                addStatement("throw new $T($S)", IllegalArgumentException::class.java,
                        "Required argument \"${arg.name}\" is missing and does " +
                                "not have an android:defaultValue")
            }
            endControlFlow()
        }
    }.build()

    val copyConstructor = MethodSpec.constructorBuilder()
            .addModifiers(Modifier.PUBLIC)
            .addParameter(className, "original")
            .addCode(specs.copyMapContents("this", "original"))
            .build()

    val fromMapConstructor = MethodSpec.constructorBuilder()
        .addModifiers(Modifier.PRIVATE)
        .addParameter(HASHMAP_CLASSNAME, "argumentsMap")
        .addStatement("$N.$N.putAll($N)",
            "this",
            specs.hashMapFieldSpec.name,
            "argumentsMap")
        .build()

    val buildMethod = MethodSpec.methodBuilder("build")
            .addAnnotation(annotations.NONNULL_CLASSNAME)
            .addModifiers(Modifier.PUBLIC)
            .returns(className)
            .addStatement(
                "$T result = new $T($N)",
                className,
                className,
                specs.hashMapFieldSpec.name
            )
            .addStatement("return result")
            .build()

    val builderClassName = ClassName.get("", "Builder")
    val builderTypeSpec = TypeSpec.classBuilder("Builder")
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addField(specs.hashMapFieldSpec)
            .addMethod(copyConstructor)
            .addMethod(specs.constructor())
            .addMethod(buildMethod)
            .addMethods(specs.setters(builderClassName))
            .addMethods(specs.getters())
            .build()

    val typeSpec = TypeSpec.classBuilder(className)
            .addSuperinterface(NAV_ARGS_CLASSNAME)
            .addModifiers(Modifier.PUBLIC)
            .addField(specs.hashMapFieldSpec)
            .addMethod(fromMapConstructor)
            .addMethod(bundleConstructor)
            .addMethods(specs.getters())
            .addMethod(specs.toBundleMethod("toBundle"))
            .addMethod(specs.equalsMethod(className))
            .addMethod(specs.hashCodeMethod())
            .addMethod(specs.toStringMethod(className))
            .addType(builderTypeSpec)
            .build()

    return JavaFile.builder(className.packageName(), typeSpec).build()
}

internal fun MethodSpec.Builder.addNullCheck(
    arg: Argument,
    variableName: String
) {
    if (arg.type.allowsNullable() && !arg.isNullable) {
        beginControlFlow("if ($N == null)", variableName).apply {
            addStatement("throw new $T($S)", IllegalArgumentException::class.java,
                    "Argument \"${arg.name}\" is marked as non-null " +
                            "but was passed a null value.")
        }
        endControlFlow()
    }
}

fun generateDirectionsJavaFile(
    destination: Destination,
    parentDirectionName: ClassName?,
    useAndroidX: Boolean
): JavaFile {
    val destName = destination.name
            ?: throw IllegalStateException("Destination with actions must have name")
    val className = ClassName.get(destName.packageName(), "${destName.simpleName()}Directions")
    val typeSpec = generateDestinationDirectionsTypeSpec(className, parentDirectionName,
            destination, useAndroidX)
    return JavaFile.builder(className.packageName(), typeSpec).build()
}

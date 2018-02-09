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

package android.arch.navigation.safe.args.generator

import android.arch.navigation.safe.args.generator.ext.N
import android.arch.navigation.safe.args.generator.ext.S
import android.arch.navigation.safe.args.generator.ext.T
import android.arch.navigation.safe.args.generator.models.Action
import android.arch.navigation.safe.args.generator.models.Argument
import android.arch.navigation.safe.args.generator.models.Destination
import android.arch.navigation.safe.args.generator.models.accessor
import com.squareup.javapoet.ClassName
import com.squareup.javapoet.FieldSpec
import com.squareup.javapoet.JavaFile
import com.squareup.javapoet.MethodSpec
import com.squareup.javapoet.TypeSpec
import javax.lang.model.element.Modifier

private const val NAVIGATION_PACKAGE = "android.arch.navigation"
private val NAV_DIRECTION_CLASSNAME: ClassName = ClassName.get(NAVIGATION_PACKAGE, "NavDirections")
private val NAV_OPTIONS_CLASSNAME: ClassName = ClassName.get(NAVIGATION_PACKAGE, "NavOptions")
private val BUNDLE_CLASSNAME: ClassName = ClassName.get("android.os", "Bundle")

private class ClassWithArgsSpecs(val args: List<Argument>) {

    fun fieldSpecs() = args.map { arg ->
        FieldSpec.builder(arg.type.typeName(), arg.name)
                .apply {
                    addModifiers(Modifier.PRIVATE)
                    if (!arg.isOptional()) {
                        addModifiers(Modifier.FINAL)
                    } else {
                        initializer(arg.defaultValue!!.write())
                    }
                }
                .build()
    }

    fun setters(thisClassName: ClassName) = args.filter(Argument::isOptional).map { (name, type) ->
        MethodSpec.methodBuilder("set${name.capitalize()}")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(type.typeName(), name)
                .addStatement("this.$N = $N", name, name)
                .addStatement("return this")
                .returns(thisClassName)
                .build()
    }

    fun writeConstructor(builder: MethodSpec.Builder) = builder.apply {
        addModifiers(Modifier.PUBLIC)
        args.filterNot(Argument::isOptional).forEach { (argName, type) ->
            addParameter(type.typeName(), argName)
            addStatement("this.$N = $N", argName, argName)
        }
    }

    fun writeBundleMethod(builder: MethodSpec.Builder) = builder.apply {
        addModifiers(Modifier.PUBLIC)
        returns(BUNDLE_CLASSNAME)
        val bundleName = "__outBundle"
        addStatement("$T $N = new $T()", BUNDLE_CLASSNAME, bundleName, BUNDLE_CLASSNAME)
        args.forEach { (argName, type) ->
            addStatement("$N.$N($S, $N)", bundleName, type.bundlePutMethod(), argName, argName)
        }
        addStatement("return $N", bundleName)
    }
}

fun generateDestinationDirectionsTypeSpec(
        className: ClassName,
        destination: Destination): TypeSpec {
    val actionTypes = destination.actions.map { action ->
        action to generateDirectionsTypeSpec(action)
    }

    val getters = actionTypes
            .map { (action, actionType) ->
                val constructor = actionType.methodSpecs.find(MethodSpec::isConstructor)!!
                val params = constructor.parameters.joinToString(", ") { param -> param.name }
                val actionTypeName = ClassName.get("", actionType.name)
                MethodSpec.methodBuilder(action.id.name)
                        .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                        .addParameters(constructor.parameters)
                        .returns(actionTypeName)
                        .addStatement("return new $T($params)", actionTypeName)
                        .build()
            }

    return TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addTypes(actionTypes.map { (_, actionType) -> actionType })
            .addMethods(getters)
            .build()
}

fun generateDirectionsTypeSpec(action: Action): TypeSpec {
    val specs = ClassWithArgsSpecs(action.args)

    val constructor = MethodSpec.constructorBuilder()
            .run(specs::writeConstructor)
            .build()

    val getArgsMethod = MethodSpec.methodBuilder("getArguments")
            .run(specs::writeBundleMethod)
            .build()

    val getDestIdMethod = MethodSpec.methodBuilder("getDestinationId")
            .addModifiers(Modifier.PUBLIC)
            .returns(Int::class.java)
            .addStatement("return $N", action.destination.accessor())
            .build()

    val getNavOptions = MethodSpec.methodBuilder("getOptions")
            .returns(NAV_OPTIONS_CLASSNAME)
            .addModifiers(Modifier.PUBLIC)
            .addStatement("return null")
            .build()

    val className = ClassName.get("", action.id.name.capitalize())
    return TypeSpec.classBuilder(className)
            .addSuperinterface(NAV_DIRECTION_CLASSNAME)
            .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
            .addFields(specs.fieldSpecs())
            .addMethod(constructor)
            .addMethods(specs.setters(className))
            .addMethod(getArgsMethod)
            .addMethod(getDestIdMethod)
            .addMethod(getNavOptions)
            .build()
}

internal fun generateArgsJavaFile(destination: Destination): JavaFile {
    val destName = destination.name
            ?: throw IllegalStateException("Destination with arguments must have name")
    val className = ClassName.get(destName.packageName(), "${destName.simpleName()}Args")
    val fromBundleMethod = MethodSpec.methodBuilder("fromBundle").apply {
        addModifiers(Modifier.PUBLIC, Modifier.STATIC)
        val bundle = "bundle"
        addParameter(BUNDLE_CLASSNAME, bundle)
        returns(className)
        val result = "result"
        addStatement("$T $N = new $T()", className, result, className)
        destination.args.forEach { arg ->
            beginControlFlow("if ($N.containsKey($S))", bundle, arg.name).apply {
                addStatement("$N.$N = $N.$N($S)", result, arg.name, bundle,
                        arg.type.bundleGetMethod(), arg.name)
            }.nextControlFlow("else").apply {
                if (arg.isOptional()) {
                    addCode("$N.$N = ", result, arg.name)
                    addCode(arg.defaultValue!!.write())
                    addStatement("")
                } else {
                    addStatement("throw new $T($S)", IllegalArgumentException::class.java,
                            "Required argument \"${arg.name}\" is missing and does " +
                                    "not have an android:defaultValue")
                }
            }.endControlFlow()
        }
        addStatement("return $N", result)
    }.build()

    val fields = destination.args.map { arg ->
        FieldSpec.builder(arg.type.typeName(), arg.name, Modifier.PRIVATE).build()
    }

    val getters = destination.args.map { arg ->
        MethodSpec.methodBuilder("get${arg.name.capitalize()}")
                .addModifiers(Modifier.PUBLIC)
                .addStatement("return $N", arg.name)
                .returns(arg.type.typeName())
                .build()
    }

    val constructor = MethodSpec.constructorBuilder().addModifiers(Modifier.PRIVATE).build()

    val typeSpec = TypeSpec.classBuilder(className)
            .addModifiers(Modifier.PUBLIC)
            .addFields(fields)
            .addMethod(constructor)
            .addMethod(fromBundleMethod)
            .addMethods(getters)
            .build()

    return JavaFile.builder(className.packageName(), typeSpec).build()
}

fun generateDirectionsJavaFile(destination: Destination): JavaFile {
    val destName = destination.name
            ?: throw IllegalStateException("Destination with actions must have name")
    val className = ClassName.get(destName.packageName(), "${destName.simpleName()}Directions")
    val typeSpec = generateDestinationDirectionsTypeSpec(className, destination)
    return JavaFile.builder(className.packageName(), typeSpec).build()
}

/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.room.compiler.processing.ksp

import androidx.room.compiler.codegen.JArrayTypeName
import androidx.room.compiler.processing.javac.kotlin.typeNameFromJvmSignature
import androidx.room.compiler.processing.tryBox
import androidx.room.compiler.processing.util.ISSUE_TRACKER_LINK
import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Variance
import com.squareup.kotlinpoet.javapoet.JClassName
import com.squareup.kotlinpoet.javapoet.JParameterizedTypeName
import com.squareup.kotlinpoet.javapoet.JTypeName
import com.squareup.kotlinpoet.javapoet.JTypeVariableName
import com.squareup.kotlinpoet.javapoet.JWildcardTypeName
import kotlin.coroutines.Continuation

// Catch-all type name when we cannot resolve to anything. This is what KAPT uses as error type
// and we use the same type in KSP for consistency.
// https://kotlinlang.org/docs/reference/kapt.html#non-existent-type-correction
internal val ERROR_JTYPE_NAME = JClassName.get("error", "NonExistentClass")

/**
 * To handle self referencing types and avoid infinite recursion, we keep a lookup map for
 * TypeVariables.
 */
private typealias JTypeArgumentTypeLookup = LinkedHashMap<KSName, JTypeName>

/**
 * Turns a KSTypeReference into a TypeName in java's type system.
 */
internal fun KSTypeReference?.asJTypeName(resolver: Resolver): JTypeName =
    asJTypeName(
        resolver = resolver,
        typeArgumentTypeLookup = JTypeArgumentTypeLookup()
    )

private fun KSTypeReference?.asJTypeName(
    resolver: Resolver,
    typeArgumentTypeLookup: JTypeArgumentTypeLookup
): JTypeName {
    return if (this == null) {
        ERROR_JTYPE_NAME
    } else {
        resolve().asJTypeName(resolver, typeArgumentTypeLookup)
    }
}

/**
 * Turns a KSDeclaration into a TypeName in java's type system.
 */
internal fun KSDeclaration.asJTypeName(resolver: Resolver): JTypeName =
    asJTypeName(
        resolver = resolver,
        typeArgumentTypeLookup = JTypeArgumentTypeLookup()
    )

@OptIn(KspExperimental::class)
private fun KSDeclaration.asJTypeName(
    resolver: Resolver,
    typeArgumentTypeLookup: JTypeArgumentTypeLookup
): JTypeName {
    if (this is KSTypeAlias) {
        return this.type.asJTypeName(resolver, typeArgumentTypeLookup)
    }
    if (this is KSTypeParameter) {
        return this.asJTypeName(resolver, typeArgumentTypeLookup)
    }
    // if there is no qualified name, it is a resolution error so just return shared instance
    // KSP may improve that later and if not, we can improve it in Room
    // TODO: https://issuetracker.google.com/issues/168639183
    val qualified = qualifiedName?.asString() ?: return ERROR_JTYPE_NAME
    val jvmSignature = resolver.mapToJvmSignature(this)
    if (jvmSignature != null && jvmSignature.isNotBlank()) {
        return jvmSignature.typeNameFromJvmSignature()
    }

    // fallback to custom generation, it is very likely that this is an unresolved type
    // get the package name first, it might throw for invalid types, hence we use
    // safeGetPackageName
    val pkg = getNormalizedPackageName()
    // using qualified name and pkg, figure out the short names.
    val shortNames = if (pkg == "") {
        qualified
    } else {
        qualified.substring(pkg.length + 1)
    }.split('.')
    return JClassName.get(pkg, shortNames.first(), *(shortNames.drop(1).toTypedArray()))
}

/**
 * Turns a KSTypeArgument into a TypeName in java's type system.
 */
internal fun KSTypeArgument.asJTypeName(
    resolver: Resolver
): JTypeName = asJTypeName(
    resolver = resolver,
    typeArgumentTypeLookup = JTypeArgumentTypeLookup()
)

private fun KSTypeParameter.asJTypeName(
    resolver: Resolver,
    typeArgumentTypeLookup: JTypeArgumentTypeLookup
): JTypeName {
    // see https://github.com/square/javapoet/issues/842
    typeArgumentTypeLookup[name]?.let {
        return it
    }
    val mutableBounds = mutableListOf<JTypeName>()
    val typeName = createModifiableTypeVariableName(name = name.asString(), bounds = mutableBounds)
    typeArgumentTypeLookup[name] = typeName
    val resolvedBounds = bounds.map {
        it.asJTypeName(resolver, typeArgumentTypeLookup).tryBox()
    }.toList()
    if (resolvedBounds.isNotEmpty()) {
        mutableBounds.addAll(resolvedBounds)
        mutableBounds.remove(JTypeName.OBJECT)
    }
    typeArgumentTypeLookup.remove(name)
    return typeName
}

private fun KSTypeArgument.asJTypeName(
    resolver: Resolver,
    typeArgumentTypeLookup: JTypeArgumentTypeLookup
): JTypeName {
    fun resolveTypeName() = type.asJTypeName(resolver, typeArgumentTypeLookup).tryBox()
    return when (variance) {
        Variance.CONTRAVARIANT -> JWildcardTypeName.supertypeOf(resolveTypeName())
        Variance.COVARIANT -> JWildcardTypeName.subtypeOf(resolveTypeName())
        Variance.STAR -> {
            JWildcardTypeName.subtypeOf(JTypeName.OBJECT)
        }
        else -> {
            if (hasJvmWildcardAnnotation()) {
                JWildcardTypeName.subtypeOf(resolveTypeName())
            } else {
                resolveTypeName()
            }
        }
    }
}

/**
 * Turns a KSType into a TypeName in java's type system.
 */
internal fun KSType.asJTypeName(resolver: Resolver): JTypeName =
    asJTypeName(
        resolver = resolver,
        typeArgumentTypeLookup = JTypeArgumentTypeLookup()
    )

@OptIn(KspExperimental::class)
private fun KSType.asJTypeName(
    resolver: Resolver,
    typeArgumentTypeLookup: JTypeArgumentTypeLookup
): JTypeName {
    return if (this.arguments.isNotEmpty() && !resolver.isJavaRawType(this)) {
        val args: Array<JTypeName> = this.arguments
            .map { typeArg ->
                typeArg.asJTypeName(
                    resolver = resolver,
                    typeArgumentTypeLookup = typeArgumentTypeLookup
                )
            }
            .map { it.tryBox() }
            .let { args ->
                if (this.isSuspendFunctionType) args.convertToSuspendSignature()
                else args
            }
            .toTypedArray()

        when (
            val typeName = declaration
                .asJTypeName(resolver, typeArgumentTypeLookup).tryBox()
        ) {
            is JArrayTypeName -> JArrayTypeName.of(args.single())
            is JClassName -> JParameterizedTypeName.get(
                typeName,
                *args
            )
            else -> error("Unexpected type name for KSType: $typeName")
        }
    } else {
        this.declaration.asJTypeName(resolver, typeArgumentTypeLookup)
    }
}

/**
 * Transforms [this] list of arguments to a suspend signature. For a [suspend] functional type, we
 * need to transform it to be a FunctionX with a [Continuation] with the correct return type. A
 * transformed SuspendFunction looks like this:
 *
 * FunctionX<[? super $params], ? super Continuation<? super $ReturnType>, ?>
 */
private fun List<JTypeName>.convertToSuspendSignature(): List<JTypeName> {
    val args = this

    // The last arg is the return type, so take everything except the last arg
    val actualArgs = args.subList(0, args.size - 1)
    val continuationReturnType = JWildcardTypeName.supertypeOf(args.last())
    val continuationType = JParameterizedTypeName.get(
        JClassName.get(Continuation::class.java),
        continuationReturnType
    )
    return actualArgs + listOf(
        JWildcardTypeName.supertypeOf(continuationType),
        JWildcardTypeName.subtypeOf(JTypeName.OBJECT)
    )
}

/**
 * The private constructor of [JTypeVariableName] which receives a list.
 * We use this in [createModifiableTypeVariableName] to create a [JTypeVariableName] whose bounds
 * can be modified afterwards.
 */
private val typeVarNameConstructor by lazy {
    try {
        JTypeVariableName::class.java.getDeclaredConstructor(
            String::class.java,
            List::class.java
        ).also {
            it.trySetAccessible()
        }
    } catch (ex: NoSuchMethodException) {
        throw IllegalStateException(
            """
            Room couldn't find the constructor it is looking for in JavaPoet.
            Please file a bug at $ISSUE_TRACKER_LINK.
            """.trimIndent(),
            ex
        )
    }
}

/**
 * Creates a TypeVariableName where we can change the bounds after constructor.
 * This is used to workaround a case for self referencing type declarations.
 * see b/187572913 for more details
 */
private fun createModifiableTypeVariableName(
    name: String,
    bounds: List<JTypeName>
): JTypeVariableName = typeVarNameConstructor.newInstance(
    name,
    bounds
) as JTypeVariableName
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

package androidx.glance.appwidget.layoutgenerator

import com.squareup.kotlinpoet.AnnotationSpec
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.INT
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.KModifier.PRIVATE
import com.squareup.kotlinpoet.KModifier.INTERNAL
import com.squareup.kotlinpoet.MemberName
import com.squareup.kotlinpoet.MemberName.Companion.member
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeName
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.asTypeName
import com.squareup.kotlinpoet.buildCodeBlock
import com.squareup.kotlinpoet.joinToCode
import java.io.File

/**
 * Generate the registry: a mapping from `LayoutSelector` to `Layout`.
 *
 * For each generated layout, a selector is created describing what the layout can and cannot do.
 * It is mapped to a `Layout`, an object describing the parameters needed to act on the layout.
 */
internal fun generateRegistry(
    packageName: String,
    layouts: Map<File, List<ContainerProperties>>,
    outputSourceDir: File,
) {
    outputSourceDir.mkdirs()
    val file = FileSpec.builder(packageName, "GeneratedLayouts")

    val generatedContainerApi21 = funSpec("registerContainers") {
        returns(ContainerMap)
        addModifiers(PRIVATE)
        addStatement("val result =")
        addCode(buildInitializer(layouts, StubSizes))
        addStatement("return result")
    }

    val requireApi31 = AnnotationSpec.builder(RequiresApi).apply {
        addMember("%M", VersionCodeS)
    }.build()
    val generatedContainerApi31 = objectSpec("GeneratedContainersForApi31Impl") {
        addModifiers(PRIVATE)
        addAnnotation(requireApi31)
        addFunction(
            funSpec("registerContainers") {
                returns(ContainerMap)
                addAnnotation(DoNotInline)
                addStatement("val result =")
                addCode(buildInitializer(layouts, listOf(ValidSize.Wrap)))
                addStatement("return result")
            }
        )
    }

    val generatedLayouts = propertySpec(
        "generatedContainers",
        ContainerMap,
        INTERNAL,
    ) {
        initializer(buildCodeBlock {
            addStatement(
                """
                |if(%M >= %M) {
                |  GeneratedContainersForApi31Impl.registerContainers()
                |} else {
                |  registerContainers()
                |}""".trimMargin(),
                SdkInt, VersionCodeS
            )
        })
    }
    file.addProperty(generatedLayouts)
    file.addFunction(generatedContainerApi21)
    file.addType(generatedContainerApi31)

    val generatedComplexLayouts = propertySpec("generatedComplexLayouts", LayoutsMap, INTERNAL) {
        initializer(buildComplexInitializer())
    }
    file.addProperty(generatedComplexLayouts)

    val generatedRoots = propertySpec("generatedRootLayoutShifts", RootShiftMap, INTERNAL) {
        addKdoc("Shift per root layout before Android S, based on width, height")
        initializer(buildRootInitializer())
    }
    file.addProperty(generatedRoots)

    val firstRootAlias = propertySpec("FirstRootAlias", INT, INTERNAL) {
        initializer("R.layout.${makeRootAliasResourceName(0)}")
    }
    val lastRootAlias = propertySpec("LastRootAlias", INT, INTERNAL) {
        initializer(
            "R.layout.%L",
            makeRootAliasResourceName(generatedRootSizePairs.size * RootLayoutAliasCount - 1)
        )
    }
    val rootAliasCount = propertySpec("RootAliasCount", INT, INTERNAL) {
        initializer("%L", generatedRootSizePairs.size * RootLayoutAliasCount)
    }
    file.addProperty(firstRootAlias)
    file.addProperty(lastRootAlias)
    file.addProperty(rootAliasCount)

    file.build().writeTo(outputSourceDir)
}

private fun buildInitializer(
    layouts: Map<File, List<ContainerProperties>>,
    sizes: List<ValidSize>
): CodeBlock {
    return buildCodeBlock {
        withIndent {
            addStatement("mapOf(")
            withIndent {
                add(
                    layouts.map {
                        it.key to createFileInitializer(it.key, it.value, sizes)
                    }
                        .sortedBy { it.first.nameWithoutExtension }
                        .map { it.second }
                        .joinToCode("")
                )
            }
            addStatement(")")
        }
    }
}

private fun buildComplexInitializer(): CodeBlock {
    return buildCodeBlock {
        addStatement("mapOf(")
        withIndent {
            forEachConfiguration { width, height ->
                addStatement(
                    "%T(width = %M, height = %M) to ",
                    SizeSelector,
                    width.toValue(),
                    height.toValue(),
                )
                withIndent {
                    val resId = makeComplexResourceName(width, height)
                    addStatement("%T(layoutId = R.layout.$resId),", LayoutInfo)
                }
            }
        }
        addStatement(")")
    }
}

private fun buildRootInitializer(): CodeBlock {
    return buildCodeBlock {
        addStatement("mapOf(")
        withIndent {
            generatedRootSizePairs.forEachIndexed { index, (width, height) ->
                addStatement(
                    "%T(width = %M, height = %M) to %L,",
                    SizeSelector,
                    width.toValue(),
                    height.toValue(),
                    index,
                )
            }
        }
        addStatement(")")
    }
}

private fun createFileInitializer(
    layout: File,
    generated: List<ContainerProperties>,
    sizes: List<ValidSize>
): CodeBlock =
    buildCodeBlock {
        val viewType = layout.nameWithoutExtension.toLayoutType()
        generated.forEach { props ->
            addContainer(
                resourceName = makeContainerResourceName(layout, props.numberChildren),
                viewType = viewType,
                numChildren = props.numberChildren,
                children = generateChildren(
                    props.numberChildren,
                    props.containerOrientation,
                    sizes
                ),
            )
        }
    }

private fun generateChildren(
    numChildren: Int,
    containerOrientation: ContainerOrientation,
    sizes: List<ValidSize>
) =
    (0 until numChildren).associateWith { pos ->
        val widths = sizes + containerOrientation.extraWidths
        val heights = sizes + containerOrientation.extraHeights
        mapInCrossProduct(widths, heights) { width, height ->
            ChildProperties(
                childId = makeIdName(pos, width, height),
                width = width,
                height = height,
            )
        }
    }

private fun CodeBlock.Builder.addContainer(
    resourceName: String,
    viewType: String,
    numChildren: Int,
    children: Map<Int, List<ChildProperties>>,
) {
    addStatement(
        "%T(type = %M, numChildren = %L) to ",
        ContainerSelector,
        makeViewType(viewType),
        numChildren,
    )
    withIndent {
        addStatement("%T(", ContainerInfo)
        withIndent {
            addStatement("layoutId = R.layout.$resourceName,")
            addStatement("children = mapOf(")
            withIndent {
                children.toList()
                    .sortedBy { it.first }
                    .forEach { (pos, children) ->
                        addStatement("$pos to mapOf(")
                        withIndent {
                            children.forEach { child ->
                                addStatement(
                                    "%T(width = %M, height = %M)",
                                    SizeSelector,
                                    child.width.toValue(),
                                    child.height.toValue(),
                                )
                                withIndent {
                                    addStatement(
                                        "to R.id.${
                                            makeIdName(
                                                pos,
                                                child.width,
                                                child.height
                                            )
                                        },"
                                    )
                                }
                            }
                        }
                        addStatement("),")
                    }
            }
            addStatement("),")
        }
        addStatement("),")
    }
}

private val ContainerSelector = ClassName("androidx.glance.appwidget", "ContainerSelector")
private val SizeSelector = ClassName("androidx.glance.appwidget", "SizeSelector")
private val LayoutInfo = ClassName("androidx.glance.appwidget", "LayoutInfo")
private val ContainerInfo = ClassName("androidx.glance.appwidget", "ContainerInfo")
private val ContainerMap = Map::class.asTypeName().parameterizedBy(ContainerSelector, ContainerInfo)
private const val LayoutSpecSize = "androidx.glance.appwidget.LayoutSize"
private val WrapValue = MemberName("$LayoutSpecSize", "Wrap")
private val FixedValue = MemberName("$LayoutSpecSize", "Fixed")
private val MatchValue = MemberName("$LayoutSpecSize", "MatchParent")
private val ExpandValue = MemberName("$LayoutSpecSize", "Expand")
private val LayoutsMap = Map::class.asTypeName().parameterizedBy(SizeSelector, LayoutInfo)
private val RootShiftMap = Map::class.asTypeName().parameterizedBy(SizeSelector, INT)
private val AndroidBuildVersion = ClassName("android.os", "Build", "VERSION")
private val AndroidBuildVersionCodes = ClassName("android.os", "Build", "VERSION_CODES")
private val SdkInt = AndroidBuildVersion.member("SDK_INT")
private val VersionCodeS = AndroidBuildVersionCodes.member("S")
private val RequiresApi = ClassName("androidx.annotation", "RequiresApi")
private val DoNotInline = ClassName("androidx.annotation", "DoNotInline")

private fun makeViewType(name: String) =
    MemberName("androidx.glance.appwidget.LayoutType", name)

private fun String.toLayoutType(): String =
    snakeRegex.replace(this) {
        it.value.replace("_", "").uppercase()
    }.replaceFirstChar { it.uppercaseChar() }

private val snakeRegex = "_[a-zA-Z0-9]".toRegex()

private fun ValidSize.toValue() = when (this) {
    ValidSize.Wrap -> WrapValue
    ValidSize.Fixed -> FixedValue
    ValidSize.Expand -> ExpandValue
    ValidSize.Match -> MatchValue
}

internal fun makeComplexResourceName(width: ValidSize, height: ValidSize) =
    listOf(
        "complex",
        width.resourceName,
        height.resourceName,
    ).joinToString(separator = "_")

internal fun makeRootResourceName(width: ValidSize, height: ValidSize) =
    listOf(
        "root",
        width.resourceName,
        height.resourceName,
    ).joinToString(separator = "_")

internal fun makeRootAliasResourceName(index: Int) = "root_alias_%03d".format(index)

internal fun makeContainerResourceName(file: File, numChildren: Int) =
    listOf(
        file.nameWithoutExtension,
        "${numChildren}children"
    ).joinToString(separator = "_")

internal fun makeChildResourceName(pos: Int, containerOrientation: ContainerOrientation) =
    listOf(
        containerOrientation.resourceName,
        "child",
        "group",
        pos
    ).joinToString(separator = "_")

internal fun makeIdName(pos: Int, width: ValidSize, height: ValidSize) =
    listOf(
        "childStub$pos",
        width.resourceName,
        height.resourceName
    ).joinToString(separator = "_")

internal fun CodeBlock.Builder.withIndent(
    builderAction: CodeBlock.Builder.() -> Unit
): CodeBlock.Builder {
    indent()
    apply(builderAction)
    unindent()
    return this
}

internal fun funSpec(name: String, builder: FunSpec.Builder.() -> Unit) =
    FunSpec.builder(name).apply(builder).build()

internal fun objectSpec(name: String, builder: TypeSpec.Builder.() -> Unit) =
    TypeSpec.objectBuilder(name).apply(builder).build()

internal fun propertySpec(
    name: String,
    type: TypeName,
    vararg modifiers: KModifier,
    builder: PropertySpec.Builder.() -> Unit
) = PropertySpec.builder(name, type, *modifiers).apply(builder).build()

private val listConfigurations =
    crossProduct(ValidSize.values().toList(), ValidSize.values().toList())

private val generatedRootSizePairs = crossProduct(StubSizes, StubSizes)

internal inline fun mapConfiguration(
    function: (width: ValidSize, height: ValidSize) -> File
): List<File> =
    listConfigurations.map { (a, b) -> function(a, b) }

internal inline fun forEachConfiguration(function: (width: ValidSize, height: ValidSize) -> Unit) {
    listConfigurations.forEach { (a, b) -> function(a, b) }
}

internal inline fun <A, B, T> mapInCrossProduct(
    first: Iterable<A>,
    second: Iterable<B>,
    consumer: (A, B) -> T
): List<T> =
    first.flatMap { a ->
        second.map { b ->
            consumer(a, b)
        }
    }

internal inline fun <A, B, T> forEachInCrossProduct(
    first: Iterable<A>,
    second: Iterable<B>,
    consumer: (A, B) -> T
) {
    first.forEach { a ->
        second.forEach { b ->
            consumer(a, b)
        }
    }
}

internal fun <A, B> crossProduct(
    first: Iterable<A>,
    second: Iterable<B>,
): List<Pair<A, B>> =
    mapInCrossProduct(first, second) { a, b -> a to b }

internal fun File.resolveRes(resName: String) = resolve("$resName.xml")

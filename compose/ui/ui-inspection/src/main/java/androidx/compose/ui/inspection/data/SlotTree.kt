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

package androidx.compose.ui.inspection.data

import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.tooling.CompositionData
import androidx.compose.runtime.tooling.CompositionGroup
import androidx.compose.ui.layout.LayoutInfo
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntRect
import java.lang.reflect.Field
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * This is a excerpt of the file: androidx.compose.ui.tooling.data.SlotTree.kt
 *
 * Taken from the version in aosp/2065482/3.
 * That change cannot be merged into 1.2 since the API was frozen before the changes could be
 * finalized.
 *
 * TODO(b/230480839): Remove this file for compose 1.3 after the above change has been merged.
 */

internal data class ParameterInformation(
    val name: String,
    val value: Any?,
    val fromDefault: Boolean,
    val static: Boolean,
    val compared: Boolean,
    val inlineClass: String?,
    val stable: Boolean
)

/**
 * Source location of the call that produced the call group.
 */
internal data class SourceLocation(
    /**
     * A 0 offset line number of the source location.
     */
    val lineNumber: Int,

    /**
     * Offset into the file. The offset is calculated as the number of UTF-16 code units from
     * the beginning of the file to the first UTF-16 code unit of the call that produced the group.
     */
    val offset: Int,

    /**
     * The length of the source code. The length is calculated as the number of UTF-16 code units
     * that that make up the call expression.
     */
    val length: Int,

    /**
     * The file name (without path information) of the source file that contains the call that
     * produced the group. A source file names are not guaranteed to be unique, [packageHash] is
     * included to help disambiguate files with duplicate names.
     */
    val sourceFile: String?,

    /**
     * A hash code of the package name of the file. This hash is calculated by,
     *
     *   `packageName.fold(0) { hash, current -> hash * 31 + current.toInt() }?.absoluteValue`
     *
     * where the package name is the dotted name of the package. This can be used to disambiguate
     * which file is referenced by [sourceFile]. This number is -1 if there was no package hash
     * information generated such as when the file does not contain a package declaration.
     */
    val packageHash: Int
)

private class CompositionCallStack<T>(
    private val factory: (CompositionGroup, SourceContext, List<T>) -> T?,
    private val contexts: MutableMap<String, Any?>
) : SourceContext {
    private val stack = ArrayDeque<CompositionGroup>()
    private var currentCallIndex = 0

    fun convert(group: CompositionGroup, callIndex: Int, out: MutableList<T>): IntRect {
        val children = mutableListOf<T>()
        var box = emptyBox
        push(group)
        var childCallIndex = 0
        group.compositionGroups.forEach { child ->
            box = box.union(convert(child, childCallIndex, children))
            if (isCall(child)) {
                childCallIndex++
            }
        }
        box = (group.node as? LayoutInfo)?.let { boundsOfLayoutNode(it) } ?: box
        currentCallIndex = callIndex
        bounds = box
        factory(group, this, children)?.let { out.add(it) }
        pop()
        return box
    }

    override val name: String?
        get() {
            val info = current.sourceInfo ?: return null
            if (!info.startsWith("C(")) {
                return null
            }
            val endIndex = info.indexOf(')')
            return if (endIndex > 2) info.substring(2, endIndex) else null
        }

    override var bounds: IntRect = emptyBox
        private set

    override val location: SourceLocation?
        get() {
            val context = parentGroup(1)?.sourceInfo?.let { contextOf(it) } ?: return null
            var parentContext: SourceInformationContext? = context
            var index = 2
            while (index < stack.size && parentContext?.sourceFile == null) {
                parentContext = parentGroup(index++)?.sourceInfo?.let { contextOf(it) }
            }
            return context.sourceLocation(currentCallIndex, parentContext)
        }

    override val parameters: List<ParameterInformation>
        get() {
            val group = current
            val context = group.sourceInfo?.let { contextOf(it) } ?: return emptyList()
            val data = mutableListOf<Any?>()
            data.addAll(group.data)
            return extractParameterInfo(data, context)
        }

    override val depth: Int
        get() = stack.size

    private fun push(group: CompositionGroup) =
        stack.addLast(group)

    private fun pop() =
        stack.removeLast()

    private val current: CompositionGroup
        get() = stack.last()

    private fun parentGroup(parentDepth: Int): CompositionGroup? =
        if (stack.size > parentDepth) stack[stack.size - parentDepth - 1] else null

    private fun contextOf(information: String): SourceInformationContext? =
        contexts.getOrPut(information) { sourceInformationContextOf(information) }
            as? SourceInformationContext

    private fun isCall(group: CompositionGroup): Boolean =
        group.sourceInfo?.startsWith("C") ?: false
}

internal val emptyBox = IntRect(0, 0, 0, 0)

private val tokenizer = Regex("(\\d+)|([,])|([*])|([:])|L|(P\\([^)]*\\))|(C(\\(([^)]*)\\))?)|@")

private fun MatchResult.isNumber() = groups[1] != null
private fun MatchResult.number() = groupValues[1].parseToInt()
private val MatchResult.text get() = groupValues[0]
private fun MatchResult.isChar(c: String) = text == c
private fun MatchResult.isFileName() = groups[4] != null
private fun MatchResult.isParameterInformation() = groups[5] != null
private fun MatchResult.isCallWithName() = groups[6] != null

private class SourceLocationInfo(val lineNumber: Int?, val offset: Int?, val length: Int?)

private class SourceInformationContext(
    val sourceFile: String?,
    val packageHash: Int,
    val locations: List<SourceLocationInfo>,
    val repeatOffset: Int,
    val parameters: List<Parameter>?
) {
    fun sourceLocation(callIndex: Int, parentContext: SourceInformationContext?): SourceLocation? {
        var locationIndex = callIndex
        if (locationIndex >= locations.size && repeatOffset >= 0 && repeatOffset < locations.size) {
            locationIndex =
                (callIndex - repeatOffset) % (locations.size - repeatOffset) + repeatOffset
        }
        if (locationIndex < locations.size) {
            val location = locations[locationIndex]
            return SourceLocation(
                location.lineNumber ?: -1,
                location.offset ?: -1,
                location.length ?: -1,
                sourceFile ?: parentContext?.sourceFile,
                (if (sourceFile == null) parentContext?.packageHash else packageHash) ?: -1
            )
        }
        return null
    }
}

private val parametersInformationTokenizer = Regex("(\\d+)|,|[!P()]|:([^,!)]+)")
private val MatchResult.isANumber get() = groups[1] != null
private val MatchResult.isClassName get() = groups[2] != null

private class ParseError : Exception()

private class Parameter(
    val sortedIndex: Int,
    val inlineClass: String? = null
)

private fun String.parseToInt(): Int =
    try {
        toInt()
    } catch (_: NumberFormatException) {
        throw ParseError()
    }

private fun String.parseToInt(radix: Int): Int =
    try {
        toInt(radix)
    } catch (_: NumberFormatException) {
        throw ParseError()
    }

// The parameter information follows the following grammar:
//
//   parameters: (parameter|run) ("," parameter | run)*
//   parameter: sorted-index [":" inline-class]
//   sorted-index: <number>
//   inline-class: <chars not "," or "!">
//   run: "!" <number>
//
// The full description of this grammar can be found in the ComposableFunctionBodyTransformer of the
// compose compiler plugin.
private fun parseParameters(parameters: String): List<Parameter> {
    var currentResult = parametersInformationTokenizer.find(parameters)
    val expectedSortedIndex = mutableListOf(0, 1, 2, 3)
    var lastAdded = expectedSortedIndex.size - 1
    val result = mutableListOf<Parameter>()
    fun next(): MatchResult? {
        currentResult?.let { currentResult = it.next() }
        return currentResult
    }

    fun expectNumber(): Int {
        val mr = currentResult
        if (mr == null || !mr.isANumber) throw ParseError()
        next()
        return mr.text.parseToInt()
    }

    fun expectClassName(): String {
        val mr = currentResult
        if (mr == null || !mr.isClassName) throw ParseError()
        next()
        return mr.text
            .substring(1)
            .replacePrefix("c#", "androidx.compose.")
    }

    fun expect(value: String) {
        val mr = currentResult
        if (mr == null || mr.text != value) throw ParseError()
        next()
    }

    fun isChar(value: String): Boolean {
        val mr = currentResult
        return mr == null || mr.text == value
    }

    fun isClassName(): Boolean {
        val mr = currentResult
        return mr != null && mr.isClassName
    }

    fun ensureIndexes(index: Int) {
        val missing = index - lastAdded
        if (missing > 0) {
            val minAddAmount = 4
            val amountToAdd = if (missing < minAddAmount) minAddAmount else missing
            repeat(amountToAdd) {
                expectedSortedIndex.add(it + lastAdded + 1)
            }
            lastAdded += amountToAdd
        }
    }

    try {
        expect("P")
        expect("(")
        loop@ while (!isChar(")")) {
            when {
                isChar("!") -> {
                    // run
                    next()
                    val count = expectNumber()
                    ensureIndexes(result.size + count)
                    repeat(count) {
                        result.add(Parameter(expectedSortedIndex.first()))
                        expectedSortedIndex.removeAt(0)
                    }
                }
                isChar(",") -> next()
                else -> {
                    val index = expectNumber()
                    val inlineClass = if (isClassName()) {
                        expectClassName()
                    } else null
                    result.add(Parameter(index, inlineClass))
                    ensureIndexes(index)
                    expectedSortedIndex.remove(index)
                }
            }
        }
        expect(")")

        // Ensure there are at least as many entries as the highest referenced index.
        while (expectedSortedIndex.size > 0) {
            result.add(Parameter(expectedSortedIndex.first()))
            expectedSortedIndex.removeAt(0)
        }
        return result
    } catch (_: ParseError) {
        return emptyList()
    } catch (_: NumberFormatException) {
        return emptyList()
    }
}

private fun sourceInformationContextOf(information: String): SourceInformationContext? {
    var currentResult = tokenizer.find(information)

    fun next(): MatchResult? {
        currentResult?.let { currentResult = it.next() }
        return currentResult
    }

    fun parseLocation(): SourceLocationInfo? {
        var lineNumber: Int? = null
        var offset: Int? = null
        var length: Int? = null

        try {
            var mr = currentResult
            if (mr != null && mr.isNumber()) {
                // Offsets are 0 based in the data, we need 1 based.
                lineNumber = mr.number() + 1
                mr = next()
            }
            if (mr != null && mr.isChar("@")) {
                // Offset
                mr = next()
                if (mr == null || !mr.isNumber()) {
                    return null
                }
                offset = mr.number()
                mr = next()
                if (mr != null && mr.isChar("L")) {
                    mr = next()
                    if (mr == null || !mr.isNumber()) {
                        return null
                    }
                    length = mr.number()
                }
            }
            if (lineNumber != null && offset != null && length != null)
                return SourceLocationInfo(lineNumber, offset, length)
        } catch (_: ParseError) {
            return null
        }
        return null
    }
    val sourceLocations = mutableListOf<SourceLocationInfo>()
    var repeatOffset = -1
    var parameters: List<Parameter>? = null
    var sourceFile: String? = null
    var packageHash = -1
    loop@ while (currentResult != null) {
        val mr = currentResult!!
        when {
            mr.isNumber() || mr.isChar("@") -> {
                parseLocation()?.let { sourceLocations.add(it) }
            }
            mr.isChar("C") -> {
                next()
            }
            mr.isCallWithName() -> {
                next()
            }
            mr.isParameterInformation() -> {
                parameters = parseParameters(mr.text)
                next()
            }
            mr.isChar("*") -> {
                repeatOffset = sourceLocations.size
                next()
            }
            mr.isChar(",") -> next()
            mr.isFileName() -> {
                sourceFile = information.substring(mr.range.last + 1)
                val hashText = sourceFile.substringAfterLast("#", "")
                if (hashText.isNotEmpty()) {
                    // Remove the hash information
                    sourceFile = sourceFile
                        .substring(0 until sourceFile.length - hashText.length - 1)
                    packageHash = try {
                        hashText.parseToInt(36)
                    } catch (_: NumberFormatException) {
                        -1
                    }
                }
                break@loop
            }
            else -> break@loop
        }
        if (mr == currentResult)
            return null
    }

    return SourceInformationContext(
        sourceFile = sourceFile,
        packageHash = if (sourceFile != null) packageHash else 0,
        locations = sourceLocations,
        repeatOffset = repeatOffset,
        parameters = parameters,
    )
}

@VisibleForTesting
internal fun boundsOfLayoutNode(node: LayoutInfo): IntRect {
    if (!node.isAttached) {
        return IntRect(
            left = 0,
            top = 0,
            right = node.width,
            bottom = node.height
        )
    }
    val position = node.coordinates.positionInWindow()
    val size = node.coordinates.size
    val left = position.x.roundToInt()
    val top = position.y.roundToInt()
    val right = left + size.width
    val bottom = top + size.height
    return IntRect(left = left, top = top, right = right, bottom = bottom)
}

/**
 * A cache of [SourceInformationContext] that optionally can be specified when using [asLazyTree].
 */
internal class ContextCache {

    /**
     * Clears the cache.
     */
    fun clear() = contexts.clear()

    internal val contexts = mutableMapOf<String, Any?>()
}

/**
 * Context with data for creating group nodes.
 *
 * See the factory argument of [asLazyTree].
 */
internal interface SourceContext {
    /**
     * The name of the Composable or null if not applicable.
     */
    val name: String?

    /**
     * The bounds of the Composable if known.
     */
    val bounds: IntRect

    /**
     * The [SourceLocation] of where the Composable was called.
     */
    val location: SourceLocation?

    /**
     * The parameters of the Composable.
     */
    val parameters: List<ParameterInformation>

    /**
     * The current depth into the [CompositionGroup] tree.
     */
    val depth: Int
}

/**
 * Return a tree of custom nodes for the slot table.
 *
 * The [factory] method will be called for every [CompositionGroup] in the slot tree and can be
 * used to create custom nodes based on the passed arguments. The [SourceContext] argument gives
 * access to additional information encoded in the [CompositionGroup.sourceInfo].
 *
 * A [cache] can optionally be specified. If a client is calling [asLazyTree] multiple times,
 * this can save some time if the values of [CompositionGroup.sourceInfo] are not unique.
 */
internal fun <T> CompositionData.asLazyTree(
    factory: (CompositionGroup, SourceContext, List<T>) -> T?,
    cache: ContextCache = ContextCache()
): T? {
    val group = compositionGroups.firstOrNull() ?: return null
    val callStack = CompositionCallStack(factory, cache.contexts)
    val out = mutableListOf<T>()
    callStack.convert(group, 0, out)
    return out.firstOrNull()
}

internal fun IntRect.union(other: IntRect): IntRect {
    if (this == emptyBox) return other else if (other == emptyBox) return this

    return IntRect(
        left = min(left, other.left),
        top = min(top, other.top),
        bottom = max(bottom, other.bottom),
        right = max(right, other.right)
    )
}

private const val parameterPrefix = "${'$'}"
private const val internalFieldPrefix = parameterPrefix + parameterPrefix
private const val defaultFieldName = "${internalFieldPrefix}default"
private const val changedFieldName = "${internalFieldPrefix}changed"
private const val jacocoDataField = "${parameterPrefix}jacoco"
private const val recomposeScopeNameSuffix = ".RecomposeScopeImpl"

private fun extractParameterInfo(
    data: List<Any?>,
    context: SourceInformationContext?
): List<ParameterInformation> {
    if (data.isNotEmpty()) {
        val recomposeScope = data.firstOrNull {
            it != null && it.javaClass.name.endsWith(recomposeScopeNameSuffix)
        }
        if (recomposeScope != null) {
            try {
                val blockField = recomposeScope.javaClass.accessibleField("block")
                if (blockField != null) {
                    val block = blockField.get(recomposeScope)
                    if (block != null) {
                        val blockClass = block.javaClass
                        val defaultsField = blockClass.accessibleField(defaultFieldName)
                        val changedField = blockClass.accessibleField(changedFieldName)
                        val default =
                            if (defaultsField != null) defaultsField.get(block) as Int else 0
                        val changed =
                            if (changedField != null) changedField.get(block) as Int else 0
                        val fields = blockClass.declaredFields
                            .filter {
                                it.name.startsWith(parameterPrefix) &&
                                    !it.name.startsWith(internalFieldPrefix) &&
                                    !it.name.startsWith(jacocoDataField)
                            }.sortedBy { it.name }
                        val parameters = mutableListOf<ParameterInformation>()
                        val parametersMetadata = context?.parameters ?: emptyList()
                        repeat(fields.size) { index ->
                            val metadata = if (index < parametersMetadata.size)
                                parametersMetadata[index] else Parameter(index)
                            if (metadata.sortedIndex >= fields.size) return@repeat
                            val field = fields[metadata.sortedIndex]
                            field.isAccessible = true
                            val value = field.get(block)
                            val fromDefault = (1 shl index) and default != 0
                            val changedOffset = index * BITS_PER_SLOT + 1
                            val parameterChanged = (
                                (SLOT_MASK shl changedOffset) and changed
                                ) shr changedOffset
                            val static = parameterChanged and STATIC_BITS == STATIC_BITS
                            val compared = parameterChanged and STATIC_BITS == 0
                            val stable = parameterChanged and STABLE_BITS == 0
                            parameters.add(
                                ParameterInformation(
                                    name = field.name.substring(1),
                                    value = value,
                                    fromDefault = fromDefault,
                                    static = static,
                                    compared = compared && !fromDefault,
                                    inlineClass = metadata.inlineClass,
                                    stable = stable
                                )
                            )
                        }
                        return parameters
                    }
                }
            } catch (_: Throwable) {
            }
        }
    }
    return emptyList()
}

private const val BITS_PER_SLOT = 3
private const val SLOT_MASK = 0b111
private const val STATIC_BITS = 0b011
private const val STABLE_BITS = 0b100

private fun Class<*>.accessibleField(name: String): Field? = declaredFields.firstOrNull {
    it.name == name
}?.apply { isAccessible = true }

private fun String.replacePrefix(prefix: String, replacement: String) =
    if (startsWith(prefix)) replacement + substring(prefix.length) else this

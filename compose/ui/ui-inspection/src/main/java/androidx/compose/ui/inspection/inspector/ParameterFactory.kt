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

package androidx.compose.ui.inspection.inspector

import android.util.Log
import android.view.View
import androidx.compose.runtime.ComposeCompilerApi
import androidx.compose.runtime.internal.ComposableLambda
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.inspection.inspector.ParameterType.DimensionDp
import androidx.compose.ui.platform.InspectableValue
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontListFontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.ResourceFont
import androidx.compose.ui.text.intl.Locale
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import org.jetbrains.annotations.TestOnly
import java.lang.reflect.Field
import java.util.IdentityHashMap
import kotlin.jvm.internal.FunctionReference
import kotlin.jvm.internal.Lambda
import kotlin.math.abs
import kotlin.reflect.KClass
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty1
import kotlin.reflect.full.allSuperclasses
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaGetter
import java.lang.reflect.Modifier as JavaModifier

private const val MAX_RECURSIONS = 2
private const val MAX_ITERABLE_SIZE = 5

private val reflectionScope: ReflectionScope = ReflectionScope()

/**
 * Factory of [NodeParameter]s.
 *
 * Each parameter value is converted to a user readable value.
 */
internal class ParameterFactory(private val inlineClassConverter: InlineClassConverter) {
    /**
     * A map from known values to a user readable string representation.
     */
    private val valueLookup = mutableMapOf<Any, String>()

    /**
     * The classes we have loaded constants from.
     */
    private val valuesLoaded = mutableSetOf<Class<*>>()

    /**
     * Do not load constant names from instances of these classes.
     * We prefer showing the raw values of Color and Dimensions.
     */
    private val ignoredClasses = listOf(Color::class.java, Dp::class.java)
    private var creatorCache: ParameterCreator? = null

    /**
     * Do not decompose instances or lookup constants from these package prefixes
     *
     * The following instances are known to contain self recursion:
     * - kotlinx.coroutines.flow.StateFlowImpl
     * - androidx.compose.ui.node.LayoutNode
     */
    private val ignoredPackagePrefixes = listOf(
        "android.", "java.", "javax.", "kotlinx.", "androidx.compose.ui.node."
    )

    var density = Density(1.0f)

    @set:TestOnly
    var maxRecursions = MAX_RECURSIONS

    @set:TestOnly
    var maxIterable = MAX_ITERABLE_SIZE

    init {
        val textDecorationCombination = TextDecoration.combine(
            listOf(TextDecoration.LineThrough, TextDecoration.Underline)
        )
        valueLookup[textDecorationCombination] = "LineThrough+Underline"
        valueLookup[Color.Unspecified] = "Unspecified"
        valueLookup[RectangleShape] = "RectangleShape"
        valuesLoaded.add(Enum::class.java)
        valuesLoaded.add(Any::class.java)

        // AbsoluteAlignment is not found from an instance of BiasAbsoluteAlignment,
        // because Alignment has no file level class.
        reflectionScope.withReflectiveAccess {
            loadConstantsFromEnclosedClasses(AbsoluteAlignment::class.java)
        }
    }

    /**
     * Create a [NodeParameter] from the specified parameter [name] and [value].
     *
     * Attempt to convert the value to a user readable value.
     * For now: return null when a conversion is not possible/found.
     */
    fun create(
        rootId: Long,
        node: InspectorNode,
        name: String,
        value: Any?,
        parameterIndex: Int
    ): NodeParameter {
        val creator = creatorCache ?: ParameterCreator()
        try {
            return reflectionScope.withReflectiveAccess {
                creator.create(rootId, node, name, value, parameterIndex)
            }
        } finally {
            creatorCache = creator
        }
    }

    /**
     * Create/expand the [NodeParameter] specified by [reference].
     *
     * @param node is the [InspectorNode] with the id of [reference].nodeId.
     * @param name is the name of the [reference].parameterIndex'th parameter of [node].
     * @param value is the value of the [reference].parameterIndex'th parameter of [node].
     * @param startIndex is the index of the 1st wanted element of a List/Array.
     * @param maxElements is the max number of elements wanted from a List/Array.
     */
    fun expand(
        rootId: Long,
        node: InspectorNode,
        name: String,
        value: Any?,
        reference: NodeParameterReference,
        startIndex: Int = 0,
        maxElements: Int = maxIterable
    ): NodeParameter? {
        val creator = creatorCache ?: ParameterCreator()
        try {
            return reflectionScope.withReflectiveAccess {
                creator.expand(rootId, node, name, value, reference, startIndex, maxElements)
            }
        } finally {
            creatorCache = creator
        }
    }

    fun clearCacheFor(rootId: Long) {
        val creator = creatorCache ?: return
        creator.clearCacheFor(rootId)
    }

    private fun loadConstantsFrom(javaClass: Class<*>) {
        if (valuesLoaded.contains(javaClass) ||
            ignoredPackagePrefixes.any { javaClass.name.startsWith(it) }
        ) {
            return
        }
        val related = generateSequence(javaClass) { it.superclass }.plus(javaClass.interfaces)
        related.forEach { aClass ->
            val topClass = generateSequence(aClass) { safeEnclosingClass(it) }.last()
            loadConstantsFromEnclosedClasses(topClass)
            findPackageLevelClass(topClass)?.let { loadConstantsFromStaticFinal(it) }
        }
    }

    private fun safeEnclosingClass(klass: Class<*>): Class<*>? = try {
        klass.enclosingClass
    } catch (_: Error) {
        // Exceptions seen on API 23...
        null
    }

    private fun findPackageLevelClass(javaClass: Class<*>): Class<*>? = try {
        // Note: This doesn't work when @file.JvmName is specified
        Class.forName("${javaClass.name}Kt")
    } catch (ex: Throwable) {
        null
    }

    private fun loadConstantsFromEnclosedClasses(javaClass: Class<*>) {
        if (valuesLoaded.contains(javaClass)) {
            return
        }
        loadConstantsFromObjectInstance(javaClass.kotlin)
        loadConstantsFromStaticFinal(javaClass)
        valuesLoaded.add(javaClass)
        javaClass.declaredClasses.forEach { loadConstantsFromEnclosedClasses(it) }
    }

    /**
     * Load all constants from companion objects and singletons
     *
     * Exclude: primary types and types of ignoredClasses, open and lateinit vals.
     */
    private fun loadConstantsFromObjectInstance(kClass: KClass<*>) {
        try {
            val instance = kClass.objectInstance ?: return
            kClass.declaredMemberProperties.asSequence()
                .filter { it.isFinal && !it.isLateinit }
                .mapNotNull { constantValueOf(it, instance)?.let { key -> Pair(key, it.name) } }
                .filter { !ignoredValue(it.first) }
                .toMap(valueLookup)
        } catch (_: Throwable) {
            // KT-16479 :  kotlin reflection does currently not support packages and files.
            // We load top level values using Java reflection instead.
            // Ignore other reflection errors as well
        }
    }

    /**
     * Load all constants from top level values from Java.
     *
     * Exclude: primary types and types of ignoredClasses.
     * Since this is Java, inline types will also (unfortunately) be excluded.
     */
    private fun loadConstantsFromStaticFinal(javaClass: Class<*>) {
        try {
            javaClass.declaredMethods.asSequence()
                .filter {
                    it.returnType != Void.TYPE &&
                        JavaModifier.isStatic(it.modifiers) &&
                        JavaModifier.isFinal(it.modifiers) &&
                        !it.returnType.isPrimitive &&
                        it.parameterTypes.isEmpty() &&
                        it.name.startsWith("get")
                }
                .mapNotNull { javaClass.getDeclaredField(it.name.substring(3)) }
                .mapNotNull { constantValueOf(it)?.let { key -> Pair(key, it.name) } }
                .filter { !ignoredValue(it.first) }
                .toMap(valueLookup)
        } catch (_: ReflectiveOperationException) {
            // ignore reflection errors
        } catch (_: NoClassDefFoundError) {
            // ignore missing classes on lower level SDKs
        }
    }

    private fun constantValueOf(field: Field?): Any? = try {
        field?.isAccessible = true
        field?.get(null)
    } catch (_: ReflectiveOperationException) {
        // ignore reflection errors
        null
    }

    private fun constantValueOf(property: KProperty1<out Any, *>, instance: Any): Any? = try {
        val field = property.javaField
        field?.isAccessible = true
        inlineClassConverter.castParameterValue(inlineResultClass(property), field?.get(instance))
    } catch (_: ReflectiveOperationException) {
        // ignore reflection errors
        null
    }

    private fun inlineResultClass(property: KProperty1<out Any, *>): String? {
        // The Java getter name will be mangled if it contains parameters of an inline class.
        // The mangled part starts with a '-'.
        if (property.javaGetter?.name?.contains('-') == true) {
            return property.returnType.toString()
        }
        return null
    }

    private fun ignoredValue(value: Any?): Boolean =
        value == null ||
            ignoredClasses.any { ignored -> ignored.isInstance(value) } ||
            value::class.java.isPrimitive

    /**
     * Convenience class for building [NodeParameter]s.
     */
    private inner class ParameterCreator {
        private var rootId = 0L
        private var node: InspectorNode? = null
        private var parameterIndex = 0
        private var recursions = 0
        private val valueIndex = mutableListOf<Int>()
        private val valueLazyReferenceMap = IdentityHashMap<Any, MutableList<NodeParameter>>()
        private val rootValueIndexCache =
            mutableMapOf<Long, IdentityHashMap<Any, NodeParameterReference>>()
        private var valueIndexMap = IdentityHashMap<Any, NodeParameterReference>()

        fun create(
            rootId: Long,
            node: InspectorNode,
            name: String,
            value: Any?,
            parameterIndex: Int
        ): NodeParameter =
            try {
                setup(rootId, node, parameterIndex)
                create(name, value) ?: createEmptyParameter(name)
            } finally {
                setup()
            }

        fun expand(
            rootId: Long,
            node: InspectorNode,
            name: String,
            value: Any?,
            reference: NodeParameterReference,
            startIndex: Int,
            maxElements: Int
        ): NodeParameter? {
            setup(rootId, node, reference.parameterIndex)
            var new = Pair(name, value)
            for (i in reference.indices) {
                new = find(new.first, new.second, i) ?: return null
            }
            recursions = 0
            valueIndex.addAll(reference.indices.asSequence())
            val parameter = if (startIndex == 0) {
                create(new.first, new.second)
            } else {
                createFromCompositeValue(new.first, new.second, startIndex, maxElements)
            }
            if (parameter == null && reference.indices.isEmpty()) {
                return createEmptyParameter(name)
            }
            return parameter
        }

        fun clearCacheFor(rootId: Long) {
            rootValueIndexCache.remove(rootId)
        }

        private fun setup(
            newRootId: Long = 0,
            newNode: InspectorNode? = null,
            newParameterIndex: Int = 0
        ) {
            rootId = newRootId
            node = newNode
            parameterIndex = newParameterIndex
            recursions = 0
            valueIndex.clear()
            valueLazyReferenceMap.clear()
            valueIndexMap = rootValueIndexCache.getOrPut(newRootId) {
                IdentityHashMap()
            }
        }

        private fun create(name: String, value: Any?): NodeParameter? {
            if (value == null) {
                return null
            }
            createFromSimpleValue(name, value)?.let { return it }

            val existing = valueIndexMap[value] ?: return createFromCompositeValue(name, value)

            // Do not decompose an instance we already decomposed.
            // Instead reference the data that was already decomposed.
            return createReferenceToExistingValue(name, value, existing)
        }

        private fun createFromSimpleValue(name: String, value: Any?): NodeParameter? {
            if (value == null) {
                return null
            }
            createFromConstant(name, value)?.let { return it }
            @OptIn(ComposeCompilerApi::class)
            return when (value) {
                is AnnotatedString -> NodeParameter(name, ParameterType.String, value.text)
                is BaselineShift -> createFromBaselineShift(name, value)
                is Boolean -> NodeParameter(name, ParameterType.Boolean, value)
                is ComposableLambda -> createFromCLambda(name, value)
                is Color -> NodeParameter(name, ParameterType.Color, value.toArgb())
//              is CornerSize -> createFromCornerSize(name, value)
                is Double -> NodeParameter(name, ParameterType.Double, value)
                is Dp -> NodeParameter(name, DimensionDp, value.value)
                is Enum<*> -> NodeParameter(name, ParameterType.String, value.toString())
                is Float -> NodeParameter(name, ParameterType.Float, value)
                is FunctionReference -> createFromFunctionReference(name, value)
                is FontListFontFamily -> createFromFontListFamily(name, value)
                is FontWeight -> NodeParameter(name, ParameterType.Int32, value.weight)
                is Int -> NodeParameter(name, ParameterType.Int32, value)
                is Lambda<*> -> createFromLambda(name, value)
                is Locale -> NodeParameter(name, ParameterType.String, value.toString())
                is Long -> NodeParameter(name, ParameterType.Int64, value)
                is Offset -> createFromOffset(name, value)
                is SolidColor -> NodeParameter(name, ParameterType.Color, value.value.toArgb())
                is String -> NodeParameter(name, ParameterType.String, value)
                is TextUnit -> createFromTextUnit(name, value)
                is ImageVector -> createFromImageVector(name, value)
                is View -> NodeParameter(name, ParameterType.String, value.javaClass.simpleName)
                else -> null
            }
        }

        private fun createFromCompositeValue(
            name: String,
            value: Any?,
            startIndex: Int = 0,
            maxElements: Int = maxIterable
        ): NodeParameter? = when {
            value == null -> null
            value is Modifier -> createFromModifier(name, value)
            value is InspectableValue -> createFromInspectableValue(name, value)
            value is Sequence<*> ->
                createFromSequence(name, value, value, startIndex, maxElements)
            value is Iterable<*> ->
                createFromSequence(name, value, value.asSequence(), startIndex, maxElements)
            value.javaClass.isArray -> createFromArray(name, value, startIndex, maxElements)
            value is Shadow -> createFromShadow(name, value)
            else -> createFromKotlinReflection(name, value)
        }

        private fun find(name: String, value: Any?, index: Int): Pair<String, Any?>? = when {
            value == null -> null
            value is Modifier -> findFromModifier(name, value, index)
            value is InspectableValue -> findFromInspectableValue(value, index)
            value is Sequence<*> -> findFromSequence(value, index)
            value is Iterable<*> -> findFromSequence(value.asSequence(), index)
            value.javaClass.isArray -> findFromArray(value, index)
            value is Shadow -> findFromShadow(value, index)
            else -> findFromKotlinReflection(value, index)
        }

        private fun createRecursively(
            name: String,
            value: Any?,
            index: Int,
            elementsIndex: Int
        ): NodeParameter? {
            valueIndex.add(index)
            recursions++
            val parameter = create(name, value)?.apply {
                this.index = if (index != elementsIndex) index else -1
            }
            recursions--
            valueIndex.removeLast()
            return parameter
        }

        private fun shouldRecurseDeeper(): Boolean =
            recursions < maxRecursions

        /**
         * Create a [NodeParameter] as a reference to a previously created parameter.
         *
         * Use [createFromCompositeValue] to compute the data type and top value,
         * however no children will be created. Instead a reference to the previously
         * created parameter is specified.
         */
        private fun createReferenceToExistingValue(
            name: String,
            value: Any?,
            ref: NodeParameterReference
        ): NodeParameter? {
            val remember = recursions
            recursions = maxRecursions
            val parameter = createFromCompositeValue(name, value)?.apply { reference = ref }
            recursions = remember
            return parameter
        }

        /**
         * Store the reference of this [NodeParameter] by its [value]
         *
         * If the value is seen in other parameter values again, there is
         * no need to create child parameters a second time.
         */
        private fun NodeParameter.store(value: Any?): NodeParameter {
            if (value != null) {
                val index = valueIndexToReference()
                valueIndexMap[value] = index
                valueLazyReferenceMap.remove(value)?.forEach { it.reference = index }
            }
            return this
        }

        /**
         * Delay the creation of all child parameters of this composite parameter.
         *
         * If the child parameters are omitted because of [maxRecursions], store the
         * parameter itself such that its reference can be updated if it turns out
         * that child [NodeParameter]s need to be generated later.
         */
        private fun NodeParameter.withChildReference(value: Any): NodeParameter {
            valueLazyReferenceMap.getOrPut(value, { mutableListOf() }).add(this)
            reference = valueIndexToReference()
            return this
        }

        private fun valueIndexToReference(): NodeParameterReference =
            NodeParameterReference(node!!.id, parameterIndex, valueIndex)

        private fun createEmptyParameter(name: String): NodeParameter =
            NodeParameter(name, ParameterType.String, "")

        private fun createFromArray(
            name: String,
            value: Any,
            startIndex: Int,
            maxElements: Int
        ): NodeParameter? {
            val sequence = arrayToSequence(value) ?: return null
            return createFromSequence(name, value, sequence, startIndex, maxElements)
        }

        private fun findFromArray(value: Any, index: Int): Pair<String, Any?>? {
            val sequence = arrayToSequence(value) ?: return null
            return findFromSequence(sequence, index)
        }

        private fun arrayToSequence(value: Any): Sequence<*>? = when (value) {
            is Array<*> -> value.asSequence()
            is ByteArray -> value.asSequence()
            is IntArray -> value.asSequence()
            is LongArray -> value.asSequence()
            is FloatArray -> value.asSequence()
            is DoubleArray -> value.asSequence()
            is BooleanArray -> value.asSequence()
            is CharArray -> value.asSequence()
            else -> null
        }

        private fun createFromBaselineShift(name: String, value: BaselineShift): NodeParameter {
            val converted = when (value.multiplier) {
                BaselineShift.None.multiplier -> "None"
                BaselineShift.Subscript.multiplier -> "Subscript"
                BaselineShift.Superscript.multiplier -> "Superscript"
                else -> return NodeParameter(name, ParameterType.Float, value.multiplier)
            }
            return NodeParameter(name, ParameterType.String, converted)
        }

        @OptIn(ComposeCompilerApi::class)
        private fun createFromCLambda(name: String, value: ComposableLambda): NodeParameter? = try {
            val lambda = value.javaClass.getDeclaredField("_block")
                .apply { isAccessible = true }
                .get(value)
            NodeParameter(name, ParameterType.Lambda, arrayOf<Any?>(lambda))
        } catch (_: Throwable) {
            null
        }

        private fun createFromConstant(name: String, value: Any): NodeParameter? {
            loadConstantsFrom(value.javaClass)
            return valueLookup[value]?.let { NodeParameter(name, ParameterType.String, it) }
        }

//        private fun createFromCornerSize(name: String, value: CornerSize): NodeParameter {
//            val size = Size(node!!.width.toFloat(), node!!.height.toFloat())
//            val pixels = value.toPx(size, density)
//            return NodeParameter(name, DimensionDp, with(density) { pixels.toDp().value })
//        }

        // For now: select ResourceFontFont closest to W400 and Normal, and return the resId
        private fun createFromFontListFamily(
            name: String,
            value: FontListFontFamily
        ): NodeParameter? =
            findBestResourceFont(value)?.let {
                NodeParameter(name, ParameterType.Resource, it.resId)
            }

        private fun createFromFunctionReference(
            name: String,
            value: FunctionReference
        ): NodeParameter =
            NodeParameter(name, ParameterType.FunctionReference, arrayOf<Any>(value, value.name))

        private fun createFromKotlinReflection(name: String, value: Any): NodeParameter? {
            val simpleName = value::class.simpleName
            val properties = lookup(value) ?: return null
            val parameter = NodeParameter(name, ParameterType.String, simpleName)
            return when {
                properties.isEmpty() -> parameter
                !shouldRecurseDeeper() -> parameter.withChildReference(value)
                else -> {
                    val elements = parameter.store(value).elements
                    properties.values.mapIndexedNotNullTo(elements) { index, part ->
                        createRecursively(part.name, valueOf(part, value), index, elements.size)
                    }
                    parameter
                }
            }
        }

        private fun findFromKotlinReflection(value: Any, index: Int): Pair<String, Any?>? {
            val properties = lookup(value)?.entries?.iterator()?.asSequence() ?: return null
            val element = properties.elementAtOrNull(index)?.value ?: return null
            return Pair(element.name, valueOf(element, value))
        }

        private fun lookup(value: Any): Map<String, KProperty<*>>? {
            val kClass = value::class
            val simpleName = kClass.simpleName
            val qualifiedName = kClass.qualifiedName
            if (simpleName == null ||
                qualifiedName == null ||
                ignoredPackagePrefixes.any { qualifiedName.startsWith(it) }
            ) {
                // Exit without creating a parameter for:
                // - internal synthetic classes
                // - certain android packages
                return null
            }
            return try {
                sequenceOf(kClass).plus(kClass.allSuperclasses.asSequence())
                    .flatMap { it.declaredMemberProperties.asSequence() }
                    .associateBy { it.name }
            } catch (ex: Throwable) {
                Log.w("Compose", "Could not decompose ${kClass.simpleName}", ex)
                null
            }
        }

        private fun valueOf(property: KProperty<*>, instance: Any): Any? = try {
            property.isAccessible = true
            // Bug in kotlin reflection API: if the type is a nullable inline type with a null
            // value, we get an IllegalArgumentException in this line:
            property.getter.call(instance)
        } catch (ex: Throwable) {
            // TODO: Remove this warning since this is expected with nullable inline types
            Log.w("Compose", "Could not get value of ${property.name}")
            null
        }

        private fun createFromInspectableValue(
            name: String,
            value: InspectableValue
        ): NodeParameter {
            val tempValue = value.valueOverride ?: ""
            val parameterName = name.ifEmpty { value.nameFallback } ?: "element"
            val parameterValue = if (tempValue is InspectableValue) "" else tempValue
            val parameter = createFromSimpleValue(parameterName, parameterValue)
                ?: NodeParameter(parameterName, ParameterType.String, "")
            if (!shouldRecurseDeeper()) {
                return parameter.withChildReference(value)
            }
            val elements = parameter.store(value).elements
            value.inspectableElements.mapIndexedNotNullTo(elements) { index, element ->
                createRecursively(element.name, element.value, index, elements.size)
            }
            return parameter
        }

        private fun findFromInspectableValue(
            value: InspectableValue,
            index: Int
        ): Pair<String, Any?>? {
            val elements = value.inspectableElements.toList()
            if (index !in elements.indices) {
                return null
            }
            val element = elements[index]
            return Pair(element.name, element.value)
        }

        private fun createFromSequence(
            name: String,
            value: Any,
            sequence: Sequence<*>,
            startIndex: Int,
            maxElements: Int
        ): NodeParameter {
            val parameter = NodeParameter(name, ParameterType.Iterable, "")
            return when {
                !sequence.any() -> parameter
                !shouldRecurseDeeper() -> parameter.withChildReference(value)
                else -> {
                    val elements = parameter.store(value).elements
                    val rest = sequence.drop(startIndex)
                    rest.take(maxElements)
                        .mapIndexedNotNullTo(elements) { i, it ->
                            val index = startIndex + i
                            createRecursively("[$index]", it, index, startIndex + elements.size)
                        }
                    if (rest.drop(maxElements).any()) {
                        parameter.withChildReference(value)
                    }
                    parameter
                }
            }
        }

        private fun findFromSequence(value: Sequence<*>, index: Int): Pair<String, Any?>? {
            val element = value.elementAtOrNull(index) ?: return null
            return Pair("[$index]", element)
        }

        private fun createFromLambda(name: String, value: Lambda<*>): NodeParameter =
            NodeParameter(name, ParameterType.Lambda, arrayOf<Any>(value))

        private fun createFromModifier(name: String, value: Modifier): NodeParameter? = when {
            name.isNotEmpty() -> {
                val parameter = NodeParameter(name, ParameterType.String, "")
                val modifiers = mutableListOf<Modifier.Element>()
                value.foldIn(modifiers) { acc, m -> acc.apply { add(m) } }
                when {
                    modifiers.isEmpty() -> parameter
                    !shouldRecurseDeeper() -> parameter.withChildReference(value)
                    else -> {
                        val elements = parameter.elements
                        modifiers.mapIndexedNotNullTo(elements) { index, element ->
                            createRecursively("", element, index, elements.size)
                        }
                        parameter.store(value)
                    }
                }
            }
            value is InspectableValue -> createFromInspectableValue(name, value)
            else -> null
        }

        private fun findFromModifier(
            name: String,
            value: Modifier,
            index: Int
        ): Pair<String, Any?>? = when {
            name.isNotEmpty() -> {
                val modifiers = mutableListOf<Modifier.Element>()
                value.foldIn(modifiers) { acc, m -> acc.apply { add(m) } }
                if (index in modifiers.indices) Pair("", modifiers[index]) else null
            }
            value is InspectableValue -> findFromInspectableValue(value, index)
            else -> null
        }

        private fun createFromOffset(name: String, value: Offset): NodeParameter {
            val parameter = NodeParameter(name, ParameterType.String, Offset::class.java.simpleName)
            val elements = parameter.elements
            elements.add(NodeParameter("x", DimensionDp, with(density) { value.x.toDp().value }))
            elements.add(NodeParameter("y", DimensionDp, with(density) { value.y.toDp().value }))
            return parameter
        }

        // Special handling of blurRadius: convert to dp:
        private fun createFromShadow(name: String, value: Shadow): NodeParameter? {
            val parameter = createFromKotlinReflection(name, value) ?: return null
            val elements = parameter.elements
            val index = elements.indexOfFirst { it.name == "blurRadius" }
            if (index >= 0) {
                val existing = elements[index]
                val blurRadius = with(density) { value.blurRadius.toDp().value }
                elements[index] = NodeParameter("blurRadius", DimensionDp, blurRadius)
                elements[index].index = existing.index
            }
            return parameter
        }

        private fun findFromShadow(value: Shadow, index: Int): Pair<String, Any?>? {
            val result = findFromKotlinReflection(value, index)
            if (result == null || result.first != "blurRadius") {
                return result
            }
            return Pair("blurRadius", with(density) { value.blurRadius.toDp() })
        }

        @Suppress("DEPRECATION")
        private fun createFromTextUnit(name: String, value: TextUnit): NodeParameter =
            when (value.type) {
                TextUnitType.Sp -> NodeParameter(name, ParameterType.DimensionSp, value.value)
                TextUnitType.Em -> NodeParameter(name, ParameterType.DimensionEm, value.value)
                TextUnitType.Unspecified ->
                    NodeParameter(name, ParameterType.String, "Unspecified")
            }

        private fun createFromImageVector(name: String, value: ImageVector): NodeParameter =
            NodeParameter(name, ParameterType.String, value.name)

        /**
         * Select a resource font among the font in the family to represent the font
         *
         * Prefer the font closest to [FontWeight.Normal] and [FontStyle.Normal]
         */
        private fun findBestResourceFont(value: FontListFontFamily): ResourceFont? =
            value.fonts.asSequence().filterIsInstance<ResourceFont>().minByOrNull {
                abs(it.weight.weight - FontWeight.Normal.weight) + it.style.ordinal
            }
    }
}

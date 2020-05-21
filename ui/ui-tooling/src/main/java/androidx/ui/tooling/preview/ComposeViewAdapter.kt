/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.ui.tooling.preview

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.widget.FrameLayout
import androidx.annotation.VisibleForTesting
import androidx.compose.Composable
import androidx.compose.Composition
import androidx.compose.Providers
import androidx.compose.Recomposer
import androidx.compose.currentComposer
import androidx.ui.core.AnimationClockAmbient
import androidx.ui.core.FontLoaderAmbient
import androidx.ui.core.setContent
import androidx.ui.core.toAndroidRect
import androidx.ui.graphics.Color
import androidx.ui.graphics.toArgb
import androidx.ui.tooling.Group
import androidx.ui.tooling.Inspectable
import androidx.ui.tooling.SlotTableRecord
import androidx.ui.tooling.asTree
import androidx.ui.tooling.preview.animation.PreviewAnimationClock
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxBounds
import androidx.ui.unit.PxBounds
import androidx.ui.unit.toRect
import kotlin.reflect.KClass

const val TOOLS_NS_URI = "http://schemas.android.com/tools"

/**
 * Class containing the minimum information needed by the Preview to map components to the
 * source code and render boundaries.
 *
 * @suppress
 */
data class ViewInfo(
    val fileName: String,
    val lineNumber: Int,
    val methodName: String,
    val bounds: IntPxBounds,
    val children: List<ViewInfo>
) {
    fun hasBounds(): Boolean = bounds.bottom != IntPx.Zero && bounds.right != IntPx.Zero

    fun allChildren(): List<ViewInfo> =
        children + children.flatMap { it.allChildren() }

    override fun toString(): String =
        """($fileName:$lineNumber,
            |bounds=(top=${bounds.top.value}, left=${bounds.left.value},
            |bottom=${bounds.bottom.value}, right=${bounds.right.value}),
            |childrenCount=${children.size})""".trimMargin()
}

/**
 * Regular expression that matches and extracts the key information as serialized in
 * [KeySourceInfo#recordSourceKeyInfo]. The expression supports two formats for backwards
 * compatibility:
 *
 *  - fileName:lineNumber
 *  - methodName (fileName:lineNumber)
 *
 *  API <=21 does not support named regex but for documentation purposes, the named version
 *  of the regex would be:
 *  `(?<method>[\w\\.$]*?)\s?\(?(?<fileName>[\w.]+):(?<lineNumber>\d+)\)?`
 */
private val KEY_INFO_REGEX =
    """([\w\\.$]*?)\s?\(?([\w.]+):(\d+)\)?""".toRegex()

/**
 * View adapter that renders a `@Composable`. The `@Composable` is found by
 * reading the `tools:composableName` attribute that contains the FQN. Additional attributes can
 * be used to customize the behaviour of this view:
 *  - `tools:parameterProviderClass`: FQN of the [PreviewParameterProvider] to be instantiated by
 *  the [ComposeViewAdapter] that will be used as source for the `@Composable` parameters.
 *  - `tools:parameterProviderIndex`: The index within the [PreviewParameterProvider] of the
 *  value to be used in this particular instance.
 *  - `tools:paintBounds`: If true, the component boundaries will be painted. This is only meant
 *  for debugging purposes.
 *  - `tools:printViewInfos`: If true, the [ComposeViewAdapter] will log the tree of [ViewInfo]
 *  to logcat for debugging.
 *  - `tools:animationClockStartTime`: When set, the [AnimationClockAmbient] will provide a
 *  [PreviewAnimationClock] using this value as start time. The clock will control the animations
 *  in the [ComposeViewAdapter] context.
 *
 * @suppress
 */
@Suppress("unused")
internal class ComposeViewAdapter : FrameLayout {
    private val TAG = "ComposeViewAdapter"

    /**
     * When enabled, generate and cache [ViewInfo] tree that can be inspected by the Preview
     * to map components to source code.
     */
    private var debugViewInfos = false
    /**
     * When enabled, paint the boundaries generated by layout nodes.
     */
    private var debugPaintBounds = false
    internal var viewInfos: List<ViewInfo> = emptyList()
    private val slotTableRecord = SlotTableRecord.create()

    private val debugBoundsPaint = Paint().apply {
        pathEffect = DashPathEffect(floatArrayOf(5f, 10f, 15f, 20f), 0f)
        style = Paint.Style.STROKE
        color = Color.Red.toArgb()
    }

    private var composition: Composition? = null

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        init(attrs)
    }

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        init(attrs)
    }

    private fun walkTable(viewInfo: ViewInfo, indent: Int = 0) {
        Log.d(TAG, ("|  ".repeat(indent)) + "|-$viewInfo")
        viewInfo.children.forEach { walkTable(it, indent + 1) }
    }

    private val Group.fileName: String
        get() = (key as? String)?.substringBefore(":") ?: ""

    private val Group.lineNumber: Int
        get() = ((key as? String)?.substringAfter(":") ?: "-1").toInt()

    /**
     * Returns true if this [Group] has no source position information
     */
    private fun Group.hasNullSourcePosition(): Boolean =
        fileName.isEmpty() && lineNumber == -1

    /**
     * Returns true if this [Group] has no source position information and no children
     */
    private fun Group.isNullGroup(): Boolean =
        hasNullSourcePosition() && children.isEmpty()

    private fun Group.toViewInfo(): ViewInfo {
        if (children.size == 1 && hasNullSourcePosition()) {
            // There is no useful information in this intermediate node, remove.
            return children.single().toViewInfo()
        }

        val childrenViewInfo = children
            .filter { !it.isNullGroup() }
            .map { it.toViewInfo() }

        val match = KEY_INFO_REGEX.matchEntire(key as? String ?: "")
            ?: return ViewInfo("", -1, "", box, childrenViewInfo)

        // TODO: Use group names instead of indexing once it's supported
        return ViewInfo(
            match.groups[2]?.value ?: "",
            match.groups[3]?.value?.toInt() ?: -1,
            match.groups[1]?.value ?: "",
            box,
            childrenViewInfo
        )
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        viewInfos = slotTableRecord.store.map { it.asTree() }.map { it.toViewInfo() }.toList()

        if (debugViewInfos) {
            viewInfos.forEach {
                walkTable(it)
            }
        }
    }

    override fun dispatchDraw(canvas: Canvas?) {
        super.dispatchDraw(canvas)

        if (!debugPaintBounds) {
            return
        }

        viewInfos
            .flatMap { listOf(it) + it.allChildren() }
            .forEach {
                if (it.hasBounds()) {
                    canvas?.apply {
                        val pxBounds = PxBounds(
                            it.bounds.left.value.toFloat(),
                            it.bounds.top.value.toFloat(),
                            it.bounds.right.value.toFloat(),
                            it.bounds.bottom.value.toFloat()
                        )
                        drawRect(pxBounds.toRect().toAndroidRect(), debugBoundsPaint)
                    }
                }
            }
    }

    /**
     * Clock that controls the animations defined in the context of this [ComposeViewAdapter].
     *
     * @suppress
     */
    @VisibleForTesting
    internal lateinit var clock: PreviewAnimationClock

    /**
     * Wraps a given [Preview] method an does any necessary setup.
     */
    @Composable
    private fun WrapPreview(children: @Composable () -> Unit) {
        // We need to replace the FontResourceLoader to avoid using ResourcesCompat.
        // ResourcesCompat can not load fonts within Layoutlib and, since Layoutlib always runs
        // the latest version, we do not need it.
        Providers(FontLoaderAmbient provides LayoutlibFontResourceLoader(context)) {
            Inspectable(slotTableRecord, children)
        }
    }

    /**
     * Initializes the adapter and populates it with the given [Preview] composable.
     * @param className name of the class containing the preview function
     * @param methodName `@Preview` method name
     * @param parameterProvider [KClass] for the [PreviewParameterProvider] to be used as
     * parameter input for this call. If null, no parameters will be passed to the composable.
     * @param parameterProviderIndex when [parameterProvider] is not null, this index will
     * reference the element in the [Sequence] to be used as parameter.
     * @param debugPaintBounds if true, the view will paint the boundaries around the layout
     * elements.
     * @param debugViewInfos if true, it will generate the [ViewInfo] structures and will log it.
     * @param animationClockStartTime if positive, the [AnimationClockAmbient] will provide
     * [clock] instead of the default clock, setting this value as the clock's initial time.
     */
    @VisibleForTesting
    internal fun init(
        className: String,
        methodName: String,
        parameterProvider: KClass<out PreviewParameterProvider<*>>? = null,
        parameterProviderIndex: Int = 0,
        debugPaintBounds: Boolean = false,
        debugViewInfos: Boolean = false,
        animationClockStartTime: Long = -1
    ) {
        this.debugPaintBounds = debugPaintBounds
        this.debugViewInfos = debugViewInfos

        composition = setContent(Recomposer.current()) {
            WrapPreview {
                val composer = currentComposer
                // We need to delay the reflection instantiation of the class until we are in the
                // composable to ensure all the right initialization has happened and the Composable
                // class loads correctly.
                val composable = {
                    invokeComposableViaReflection(
                        className,
                        methodName,
                        composer,
                        *getPreviewProviderParameters(parameterProvider, parameterProviderIndex)
                    )
                }
                if (animationClockStartTime >= 0) {
                    // Provide a custom clock when animation inspection is enabled, i.e. when a
                    // valid `animationClockStartTime` is passed. This clock will control the
                    // animations defined in this `ComposeViewAdapter` from Android Studio.
                    clock = PreviewAnimationClock(animationClockStartTime)
                    Providers(AnimationClockAmbient provides clock) {
                        composable()
                    }
                } else {
                    composable()
                }
            }
        }
    }

    /**
     * Disposes the Compose elements allocated during [init]
     */
    internal fun dispose() {
        composition?.dispose()
        composition = null
    }

    /**
     * Sets the relative time of the [PreviewAnimationClock] that controls inspected animations.
     *
     * Expected to be called via reflection from Android Studio and will fail otherwise, since
     * [clock] will not be initialized in that case.
     *
     * @suppress
     */
    fun setClockTime(timeMs: Long) {
        try {
            clock.setClockTime(timeMs)
        } catch (e: UninitializedPropertyAccessException) {
            throw IllegalStateException("This method is expected to be called from Android Studio" +
                    " via reflection, otherwise 'clock' is expected to be null.")
        }
    }

    private fun init(attrs: AttributeSet) {
        val composableName = attrs.getAttributeValue(TOOLS_NS_URI, "composableName") ?: return
        val className = composableName.substringBeforeLast('.')
        val methodName = composableName.substringAfterLast('.')
        val parameterProviderIndex = attrs.getAttributeIntValue(
            TOOLS_NS_URI,
            "parameterProviderIndex", 0
        )
        val parameterProviderClass = attrs.getAttributeValue(TOOLS_NS_URI, "parameterProviderClass")
            ?.asPreviewProviderClass()

        val animationClockStartTime = try {
            attrs.getAttributeValue(TOOLS_NS_URI, "animationClockStartTime").toLong()
        } catch (e: Exception) {
            -1L
        }

        init(
            className = className,
            methodName = methodName,
            parameterProvider = parameterProviderClass,
            parameterProviderIndex = parameterProviderIndex,
            debugPaintBounds = attrs.getAttributeBooleanValue(
                TOOLS_NS_URI,
                "paintBounds",
                debugPaintBounds
            ),
            debugViewInfos = attrs.getAttributeBooleanValue(
                TOOLS_NS_URI,
                "printViewInfos",
                debugViewInfos
            ),
            animationClockStartTime = animationClockStartTime
        )
    }
}

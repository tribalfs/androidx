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
import androidx.compose.disposeComposition
import androidx.ui.core.FontLoaderAmbient
import androidx.ui.core.setContent
import androidx.ui.core.toFrameworkRect
import androidx.ui.graphics.Color
import androidx.ui.graphics.toArgb
import androidx.ui.tooling.Group
import androidx.ui.tooling.Inspectable
import androidx.ui.tooling.asTree
import androidx.ui.tooling.tables
import androidx.ui.unit.IntPx
import androidx.ui.unit.IntPxBounds
import androidx.ui.unit.PxBounds
import androidx.ui.unit.toPx
import androidx.ui.unit.toRect

const val TOOLS_NS_URI = "http://schemas.android.com/tools"

/**
 * Class containing the minimum information needed by the Preview to map components to the
 * source code and render boundaries.
 *
 * @hide
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
 */
private val KEY_INFO_REGEX =
    """(?<method>[\w\\.$]*?)\s?\(?(?<fileName>[\w.]+):(?<lineNumber>\d+)\)?""".toRegex()

/**
 * View adapter that renders a `@Composable`. The `@Composable` is found by
 * reading the `tools:composableName` attribute that contains the FQN.
 *
 * @hide
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
        return ViewInfo(match.groups[2]?.value ?: "",
            match.groups[3]?.value?.toInt() ?: -1,
            match.groups[1]?.value ?: "",
            box,
            childrenViewInfo)
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)

        viewInfos = tables.map { it.asTree() }.map { it.toViewInfo() }.toList()

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
                        val pxBounds = PxBounds(it.bounds.left.toPx(),
                            it.bounds.top.toPx(),
                            it.bounds.right.toPx(),
                            it.bounds.bottom.toPx())
                        drawRect(pxBounds.toRect().toFrameworkRect(), debugBoundsPaint)
                    }
                }
            }
    }

    /**
     * Wraps a given [Preview] method an does any necessary setup.
     */
    @Composable
    private fun WrapPreview(children: @Composable() () -> Unit) {
        // We need to replace the FontResourceLoader to avoid using ResourcesCompat.
        // ResourcesCompat can not load fonts within Layoutlib and, since Layoutlib always runs
        // the latest version, we do not need it.
        Providers(FontLoaderAmbient provides LayoutlibFontResourceLoader(context)) {
            Inspectable(children)
        }
    }

    /**
     * Initializes the adapter and populates it with the given [Preview] composable.
     * @param className name of the class containing the preview function
     * @param methodName `@Preview` method name
     * @param debugPaintBounds if true, the view will paint the boundaries around the layout
     * elements.
     * @param debugViewInfos if true, it will generate the [ViewInfo] structures and will log it.
     */
    @VisibleForTesting
    internal fun init(
        className: String,
        methodName: String,
        debugPaintBounds: Boolean = false,
        debugViewInfos: Boolean = false
    ) {
        this.debugPaintBounds = debugPaintBounds
        this.debugViewInfos = debugViewInfos
        composition = setContent {
            WrapPreview {
                // We need to delay the reflection instantiation of the class until we are in the
                // composable to ensure all the right initialization has happened and the Composable
                // class loads correctly.
                invokeComposableViaReflection(className, methodName)
            }
        }
    }

    /**
     * Disposes the Compose elements allocated during [init]
     */
    internal fun dispose() {
        disposeComposition()
        composition?.dispose()
        composition = null
    }

    private fun init(attrs: AttributeSet) {
        val composableName = attrs.getAttributeValue(TOOLS_NS_URI, "composableName") ?: return
        val className = composableName.substringBeforeLast('.')
        val methodName = composableName.substringAfterLast('.')

        init(
            className,
            methodName,
            attrs.getAttributeBooleanValue(TOOLS_NS_URI, "paintBounds", debugPaintBounds),
            attrs.getAttributeBooleanValue(TOOLS_NS_URI, "printViewInfos", debugViewInfos)
        )
    }
}

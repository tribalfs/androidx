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
import androidx.ui.unit.Px
import androidx.ui.unit.PxBounds
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
    val bounds: PxBounds,
    val children: List<ViewInfo>
) {
    fun hasBounds(): Boolean = bounds.bottom != Px.Zero && bounds.right != Px.Zero

    fun allChildren(): List<ViewInfo> =
        children + children.flatMap { it.allChildren() }

    override fun toString(): String =
        """($fileName:$lineNumber,
            |bounds=(top=${bounds.top.value}, left=${bounds.left.value},
            |bottom=${bounds.bottom.value}, right=${bounds.right.value}),
            |childrenCount=${children.size})""".trimMargin()
}

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
        val fileName = (key as? String)?.substringBefore(":") ?: ""

        if (children.size == 1 && hasNullSourcePosition()) {
            // There is no useful information in this intermediate node, remove.
            return children.single().toViewInfo()
        }

        val childrenViewInfo = children
            .filter { !it.isNullGroup() }
            .map { it.toViewInfo() }

        return ViewInfo(fileName, lineNumber, box, childrenViewInfo)
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
            .flatMap { it.allChildren() }
            .forEach {
                if (it.hasBounds()) {
                    canvas?.apply {
                        translate(paddingLeft.toFloat(), paddingTop.toFloat())
                        drawRect(it.bounds.toRect().toFrameworkRect(), debugBoundsPaint)
                        translate(-paddingLeft.toFloat(), -paddingTop.toFloat())
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
        FontLoaderAmbient.Provider(value = LayoutlibFontResourceLoader(context)) {
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
        setContent {
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

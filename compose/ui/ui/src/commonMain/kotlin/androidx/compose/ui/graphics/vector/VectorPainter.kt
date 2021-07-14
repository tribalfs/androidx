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

package androidx.compose.ui.graphics.vector

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Default identifier for the root group if a Vector graphic
 */
const val RootGroupName = "VectorRootGroup"

/**
 * Create a [VectorPainter] with the Vector defined by the provided
 * sub-composition
 *
 * @param [defaultWidth] Intrinsic width of the Vector in [Dp]
 * @param [defaultHeight] Intrinsic height of the Vector in [Dp]
 * @param [viewportWidth] Width of the viewport space. The viewport is the virtual canvas where
 * paths are drawn on.
 *  This parameter is optional. Not providing it will use the [defaultWidth] converted to pixels
 * @param [viewportHeight] Height of the viewport space. The viewport is the virtual canvas where
 * paths are drawn on.
 *  This parameter is optional. Not providing it will use the [defaultHeight] converted to pixels
 * @param [name] optional identifier used to identify the root of this vector graphic
 * @param [tintColor] optional color used to tint the root group of this vector graphic
 * @param [tintBlendMode] BlendMode used in combination with [tintColor]
 * @param [content] Composable used to define the structure and contents of the vector graphic
 */
@Composable
fun rememberVectorPainter(
    defaultWidth: Dp,
    defaultHeight: Dp,
    viewportWidth: Float = Float.NaN,
    viewportHeight: Float = Float.NaN,
    name: String = RootGroupName,
    tintColor: Color = Color.Unspecified,
    tintBlendMode: BlendMode = BlendMode.SrcIn,
    content: @Composable (viewportWidth: Float, viewportHeight: Float) -> Unit
): VectorPainter {
    val density = LocalDensity.current
    val widthPx = with(density) { defaultWidth.toPx() }
    val heightPx = with(density) { defaultHeight.toPx() }

    val vpWidth = if (viewportWidth.isNaN()) widthPx else viewportWidth
    val vpHeight = if (viewportHeight.isNaN()) heightPx else viewportHeight

    val painter = remember { VectorPainter() }.apply {
        // This assignment is thread safe as the internal Size parameter is
        // backed by a mutableState object
        size = Size(widthPx, heightPx)
        RenderVector(name, vpWidth, vpHeight, content)
    }
    SideEffect {
        // Initialize the intrinsic color filter if a tint color is provided on the
        // vector itself. Note this tint can be overridden by an explicit ColorFilter
        // provided on the Modifier.paint call
        painter.intrinsicColorFilter = if (tintColor != Color.Unspecified) {
            ColorFilter.tint(tintColor, tintBlendMode)
        } else {
            null
        }
    }
    return painter
}

/**
 * Create a [VectorPainter] with the given [ImageVector]. This will create a
 * sub-composition of the vector hierarchy given the tree structure in [ImageVector]
 *
 * @param [image] ImageVector used to create a vector graphic sub-composition
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun rememberVectorPainter(image: ImageVector) =
    rememberVectorPainter(
        defaultWidth = image.defaultWidth,
        defaultHeight = image.defaultHeight,
        viewportWidth = image.viewportWidth,
        viewportHeight = image.viewportHeight,
        name = image.name,
        tintColor = image.tintColor,
        tintBlendMode = image.tintBlendMode,
        content = { _, _ -> RenderVectorGroup(group = image.root) }
    )

/**
 * [Painter] implementation that abstracts the drawing of a Vector graphic.
 * This can be represented by either a [ImageVector] or a programmatic
 * composition of a vector
 */
class VectorPainter internal constructor() : Painter() {

    internal var size by mutableStateOf(Size.Zero)

    /**
     * configures the intrinsic tint that may be defined on a VectorPainter
     */
    internal var intrinsicColorFilter: ColorFilter?
        get() = vector.intrinsicColorFilter
        set(value) {
            vector.intrinsicColorFilter = value
        }

    private val vector = VectorComponent().apply {
        invalidateCallback = {
            isDirty = true
        }
    }

    private var composition: Composition? = null

    private fun composeVector(
        parent: CompositionContext,
        composable: @Composable (viewportWidth: Float, viewportHeight: Float) -> Unit
    ): Composition {
        val existing = composition
        val next = if (existing == null || existing.isDisposed) {
            Composition(
                VectorApplier(vector.root),
                parent
            )
        } else {
            existing
        }
        composition = next
        next.setContent {
            composable(vector.viewportWidth, vector.viewportHeight)
        }
        return next
    }

    private var isDirty by mutableStateOf(true)

    @Composable
    internal fun RenderVector(
        name: String,
        viewportWidth: Float,
        viewportHeight: Float,
        content: @Composable (viewportWidth: Float, viewportHeight: Float) -> Unit
    ) {
        vector.apply {
            this.name = name
            this.viewportWidth = viewportWidth
            this.viewportHeight = viewportHeight
        }
        val composition = composeVector(
            rememberCompositionContext(),
            content
        )

        DisposableEffect(composition) {
            onDispose {
                composition.dispose()
            }
        }
    }

    private var currentAlpha: Float = 1.0f
    private var currentColorFilter: ColorFilter? = null

    override val intrinsicSize: Size
        get() = size

    override fun DrawScope.onDraw() {
        with(vector) {
            draw(currentAlpha, currentColorFilter ?: intrinsicColorFilter)
        }
        // This conditional is necessary to obtain invalidation callbacks as the state is
        // being read here which adds this callback to the snapshot observation
        if (isDirty) {
            isDirty = false
        }
    }

    override fun applyAlpha(alpha: Float): Boolean {
        currentAlpha = alpha
        return true
    }

    override fun applyColorFilter(colorFilter: ColorFilter?): Boolean {
        currentColorFilter = colorFilter
        return true
    }
}

/**
 * Represents one of the properties for PathComponent or GroupComponent that can be overwritten
 * when it is composed and drawn with [RenderVectorGroup].
 */
@ExperimentalComposeUiApi
sealed class VectorProperty<T> {
    object Rotation : VectorProperty<Float>()
    object PivotX : VectorProperty<Float>()
    object PivotY : VectorProperty<Float>()
    object ScaleX : VectorProperty<Float>()
    object ScaleY : VectorProperty<Float>()
    object TranslateX : VectorProperty<Float>()
    object TranslateY : VectorProperty<Float>()
    object PathData : VectorProperty<List<PathNode>>()
    object Fill : VectorProperty<Brush?>()
    object FillAlpha : VectorProperty<Float>()
    object Stroke : VectorProperty<Brush?>()
    object StrokeLineWidth : VectorProperty<Float>()
    object StrokeAlpha : VectorProperty<Float>()
    object TrimPathStart : VectorProperty<Float>()
    object TrimPathEnd : VectorProperty<Float>()
    object TrimPathOffset : VectorProperty<Float>()
}

/**
 * Holds a set of values that overwrite the original property values of an [ImageVector]. This
 * allows you to dynamically change any of the property values provided as [VectorProperty].
 * This can be passed to [RenderVectorGroup] to alter some property values when the [VectorGroup]
 * is rendered.
 */
@ExperimentalComposeUiApi
interface VectorConfig {
    fun <T> getOrDefault(property: VectorProperty<T>, defaultValue: T): T {
        return defaultValue
    }
}

/**
 * Recursively creates the vector graphic composition by traversing the tree structure.
 *
 * @param group The vector group to render.
 * @param configs An optional map of [VectorConfig] to provide animation values. The keys are the
 * node names. The values are [VectorConfig] for that node.
 */
@ExperimentalComposeUiApi
@Composable
fun RenderVectorGroup(
    group: VectorGroup,
    configs: Map<String, VectorConfig> = emptyMap()
) {
    for (vectorNode in group) {
        if (vectorNode is VectorPath) {
            val config = configs[vectorNode.name] ?: object : VectorConfig {}
            Path(
                pathData = config.getOrDefault(
                    VectorProperty.PathData,
                    vectorNode.pathData
                ),
                pathFillType = vectorNode.pathFillType,
                name = vectorNode.name,
                fill = config.getOrDefault(
                    VectorProperty.Fill,
                    vectorNode.fill
                ),
                fillAlpha = config.getOrDefault(
                    VectorProperty.FillAlpha,
                    vectorNode.fillAlpha
                ),
                stroke = config.getOrDefault(
                    VectorProperty.Stroke,
                    vectorNode.stroke
                ),
                strokeAlpha = config.getOrDefault(
                    VectorProperty.StrokeAlpha,
                    vectorNode.strokeAlpha
                ),
                strokeLineWidth = config.getOrDefault(
                    VectorProperty.StrokeLineWidth,
                    vectorNode.strokeLineWidth
                ),
                strokeLineCap = vectorNode.strokeLineCap,
                strokeLineJoin = vectorNode.strokeLineJoin,
                strokeLineMiter = vectorNode.strokeLineMiter,
                trimPathStart = config.getOrDefault(
                    VectorProperty.TrimPathStart,
                    vectorNode.trimPathStart
                ),
                trimPathEnd = config.getOrDefault(
                    VectorProperty.TrimPathEnd,
                    vectorNode.trimPathEnd
                ),
                trimPathOffset = config.getOrDefault(
                    VectorProperty.TrimPathOffset,
                    vectorNode.trimPathOffset
                )
            )
        } else if (vectorNode is VectorGroup) {
            val config = configs[vectorNode.name] ?: object : VectorConfig {}
            Group(
                name = vectorNode.name,
                rotation = config.getOrDefault(
                    VectorProperty.Rotation,
                    vectorNode.rotation
                ),
                scaleX = config.getOrDefault(
                    VectorProperty.ScaleX,
                    vectorNode.scaleX
                ),
                scaleY = config.getOrDefault(
                    VectorProperty.ScaleY,
                    vectorNode.scaleY
                ),
                translationX = config.getOrDefault(
                    VectorProperty.TranslateX,
                    vectorNode.translationX
                ),
                translationY = config.getOrDefault(
                    VectorProperty.TranslateY,
                    vectorNode.translationY
                ),
                pivotX = config.getOrDefault(
                    VectorProperty.PivotX,
                    vectorNode.pivotX
                ),
                pivotY = config.getOrDefault(
                    VectorProperty.PivotY,
                    vectorNode.pivotY
                ),
                clipPathData = config.getOrDefault(
                    VectorProperty.PathData,
                    vectorNode.clipPathData
                )
            ) {
                RenderVectorGroup(group = vectorNode, configs = configs)
            }
        }
    }
}
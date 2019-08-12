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

package androidx.ui.vector

import android.graphics.Matrix
import androidx.ui.engine.geometry.Offset
import androidx.ui.painting.Canvas
import androidx.ui.painting.Image
import androidx.ui.painting.Paint
import androidx.ui.painting.PaintingStyle
import androidx.ui.painting.Path
import androidx.ui.painting.StrokeCap
import androidx.ui.painting.StrokeJoin
import androidx.ui.core.Px
import androidx.ui.graphics.Brush
import androidx.ui.graphics.EmptyBrush
import androidx.ui.graphics.vectorgraphics.PathBuilder
import androidx.ui.graphics.vectorgraphics.PathNode
import androidx.ui.graphics.vectorgraphics.PathParser
import androidx.ui.painting.withSave
import kotlin.math.ceil

const val DefaultGroupName = ""
const val DefaultRotation = 0.0f
const val DefaultPivotX = 0.0f
const val DefaultPivotY = 0.0f
const val DefaultScaleX = 1.0f
const val DefaultScaleY = 1.0f
const val DefaultTranslationX = 0.0f
const val DefaultTranslationY = 0.0f

val EmptyPath = emptyArray<PathNode>()

/**
 * paint used to draw the cached vector graphic to the provided canvas
 * TODO (njawad) Can we update the Crane Canvas API to make this paint optional?
 */
internal val EmptyPaint = Paint()

class PathDelegate(val delegate: PathBuilder.() -> Unit)

// TODO figure out how to use UNIONTYPE with a Lambda receiver. Cannot cast to KClass which is what
// UnionType is expecting
// TODO uncomment usage of UnionType when Compose can be accessed across modules
typealias PathData = /*@UnionType(String::class, PathDelegate::class, Array<PathNode>::class)*/ Any?

// TODO (njawad) change to color int
typealias BrushType = /*@UnionType(Int::class, Brush::class)*/ Any

const val DefaultPathName = ""
const val DefaultAlpha = 1.0f
const val DefaultStrokeLineWidth = 0.0f
const val DefaultStrokeLineMiter = 4.0f

val DefaultStrokeLineCap = StrokeCap.butt
val DefaultStrokeLineJoin = StrokeJoin.miter

fun addPathNodes(pathStr: String?): Array<PathNode> =
    if (pathStr == null) {
        EmptyPath
    } else {
        PathParser().parsePathString(pathStr).toNodes()
    }

sealed class VNode {
    abstract fun draw(canvas: Canvas)
}

class VectorComponent(
    val name: String = "",
    var viewportWidth: Float,
    var viewportHeight: Float,
    var defaultWidth: Px,
    var defaultHeight: Px
) : VNode() {

    val root = GroupComponent(this@VectorComponent.name).apply {
        pivotX = 0.0f
        pivotY = 0.0f
        scaleX = defaultWidth.value / viewportWidth
        scaleY = defaultHeight.value / viewportHeight
    }

    /**
     * Cached Image of the Vector Graphic to be re-used across draw calls
     * if the Vector graphic is not dirty
     */
    // TODO (njawad) add invalidation logic to re-draw into the offscreen Image
    private var cachedImage: Image? = null

    val size: Int
        get() = root.size

    override fun draw(canvas: Canvas) {
        var targetImage = cachedImage
        if (targetImage == null) {
            targetImage = Image(
                ceil(defaultWidth.value).toInt(),
                ceil(defaultHeight.value).toInt()
            )
            cachedImage = targetImage
            root.draw(Canvas(targetImage))
        }
        canvas.drawImage(targetImage, Offset.zero, EmptyPaint)
    }

    override fun toString(): String {
        return buildString {
            append("Params: ")
            append("\tname: ").append(name).append("\n")
            append("\twidth: ").append(defaultWidth).append("\n")
            append("\theight: ").append(defaultHeight).append("\n")
            append("\tviewportWidth: ").append(viewportWidth).append("\n")
            append("\tviewportHeight: ").append(viewportHeight).append("\n")
        }
    }
}

class PathComponent(val name: String) : VNode() {

    var fill: Brush = EmptyBrush
        set(value) {
            field = value
            updateFillPaint {
                field.applyBrush(this)
            }
        }

    var fillAlpha: Float = DefaultAlpha
        set(value) {
            field = value
            updateFillPaint {
                alpha = field
            }
        }

    var pathNodes: Array<PathNode> = emptyArray()
        set(value) {
            field = value
            isPathDirty = true
        }

    var strokeAlpha: Float = DefaultAlpha
        set(value) {
            field = value
            updateStrokePaint {
                alpha = field
            }
        }

    var strokeLineWidth: Float = DefaultStrokeLineWidth
        set(value) {
            field = value
            updateStrokePaint {
                strokeWidth = field
            }
        }

    var stroke: Brush = EmptyBrush
        set(value) {
            field = value
            updateStrokePaint {
                field.applyBrush(this)
            }
        }

    var strokeLineCap: StrokeCap = DefaultStrokeLineCap
        set(value) {
            field = value
            updateStrokePaint {
                strokeCap = field
            }
        }

    var strokeLineJoin: StrokeJoin = DefaultStrokeLineJoin
        set(value) {
            field = value
            updateStrokePaint {
                strokeJoin = field
            }
        }

    var strokeLineMiter: Float = DefaultStrokeLineMiter
        set(value) {
            field = value
            updateStrokePaint {
                strokeMiterLimit = field
            }
        }

    private var isPathDirty = true

    private val path = Path()

    private var fillPaint: Paint? = null
    private var strokePaint: Paint? = null

    private val parser = PathParser()

    private fun updateStrokePaint(strokePaintUpdater: Paint.() -> Unit) {
        if (strokePaint == null) {
            strokePaint = createStrokePaint()
        } else {
            strokePaint?.strokePaintUpdater()
        }
    }

    private fun createStrokePaint(): Paint = Paint().apply {
        isAntiAlias = true
        style = PaintingStyle.stroke
        alpha = strokeAlpha
        strokeWidth = strokeLineWidth
        strokeCap = strokeLineCap
        strokeJoin = strokeLineJoin
        strokeMiterLimit = strokeLineMiter
        stroke.applyBrush(this)
    }

    private fun updateFillPaint(fillPaintUpdater: Paint.() -> Unit) {
        if (fillPaint == null) {
            fillPaint = createFillPaint()
        } else {
            fillPaint?.fillPaintUpdater()
        }
    }

    private fun createFillPaint(): Paint = Paint().apply {
        isAntiAlias = true
        alpha = fillAlpha
        style = PaintingStyle.fill
        fill.applyBrush(this)
    }

    private fun updatePath() {
        parser.clear()
        path.reset()
        parser.addPathNodes(pathNodes).toPath(path)
    }

    override fun draw(canvas: Canvas) {
        if (isPathDirty) {
            updatePath()
            isPathDirty = false
        }

        val fillBrush = fill
        if (fillBrush !== EmptyBrush) {
            var targetFillPaint = fillPaint
            if (targetFillPaint == null) {
                targetFillPaint = createFillPaint()
                fillPaint = targetFillPaint
            }
            canvas.drawPath(path, targetFillPaint)
        }

        val strokeBrush = stroke
        if (strokeBrush !== EmptyBrush) {
            var targetStrokePaint = strokePaint
            if (targetStrokePaint == null) {
                targetStrokePaint = createStrokePaint()
                strokePaint = targetStrokePaint
            }
            canvas.drawPath(path, targetStrokePaint)
        }
    }

    override fun toString(): String {
        return path.toString()
    }
}

class GroupComponent(val name: String = DefaultGroupName) : VNode() {

    private var groupMatrix: Matrix? = null

    private val children = mutableListOf<VNode>()

    var clipPathNodes: Array<PathNode> = EmptyPath
        set(value) {
            field = value
            isClipPathDirty = true
        }

    private val willClipPath: Boolean
        get() = clipPathNodes.isNotEmpty()

    private var isClipPathDirty = true

    private var clipPath: Path? = null
    private var parser: PathParser? = null

    private fun updateClipPath() {
        if (willClipPath) {
            var targetParser = parser
            if (targetParser == null) {
                targetParser = PathParser()
                parser = targetParser
            } else {
                targetParser.clear()
            }

            var targetClip = clipPath
            if (targetClip == null) {
                targetClip = Path()
                clipPath = targetClip
            } else {
                targetClip.reset()
            }

            targetParser.addPathNodes(clipPathNodes).toPath(targetClip)
        }
    }

    var rotation: Float = DefaultRotation
        set(value) {
            field = value
            isMatrixDirty = true
        }

    var pivotX: Float = DefaultPivotX
        set(value) {
            field = value
            isMatrixDirty = true
        }

    var pivotY: Float = DefaultPivotY
        set(value) {
            field = value
            isMatrixDirty = true
        }

    var scaleX: Float = DefaultScaleX
        set(value) {
            field = value
            isMatrixDirty = true
        }

    var scaleY: Float = DefaultScaleY
        set(value) {
            field = value
            isMatrixDirty = true
        }

    var translationX: Float = DefaultTranslationX
        set(value) {
            field = value
            isMatrixDirty = true
        }

    var translationY: Float = DefaultTranslationY
        set(value) {
            field = value
            isMatrixDirty = true
        }

    private var isMatrixDirty = true

    private fun updateMatrix() {
        val matrix: Matrix
        val target = groupMatrix
        if (target == null) {
            matrix = Matrix()
            groupMatrix = matrix
        } else {
            matrix = target
        }
        with(matrix) {
            reset()
            postTranslate(-pivotX, -pivotY)
            postScale(scaleX, scaleY)
            postRotate(rotation, 0f, 0f)
            postTranslate(translationX + pivotX,
                translationY + pivotY)
        }
    }

    fun insertAt(index: Int, instance: VNode) {
        if (index < size) {
            children[index] = instance
        } else {
            children.add(instance)
        }
    }

    fun move(from: Int, to: Int, count: Int) {
        if (from > to) {
            var current = to
            repeat(count) {
                val node = children[from]
                children.removeAt(from)
                children.add(current, node)
                current++
            }
        } else {
            repeat(count) {
                val node = children[from]
                children.removeAt(from)
                children.add(to - 1, node)
            }
        }
    }

    fun remove(index: Int, count: Int) {
        repeat(count) {
            children.removeAt(index)
        }
    }

    override fun draw(canvas: Canvas) {
        if (isMatrixDirty) {
            updateMatrix()
            isMatrixDirty = false
        }

        if (isClipPathDirty) {
            updateClipPath()
            isClipPathDirty = false
        }

        canvas.withSave {
            val targetClip = clipPath
            if (willClipPath && targetClip != null) {
                canvas.clipPath(targetClip)
            }

            val matrix = groupMatrix
            if (matrix != null) {
                // TODO (njawad) add concat support to matrix
                canvas.nativeCanvas.concat(matrix)
            }

            for (node in children) {
                node.draw(canvas)
            }
        }
    }

    val size: Int
        get() = children.size

    override fun toString(): String {
        val sb = StringBuilder().append("VGroup: ").append(name)
        for (node in children) {
            sb.append("\t").append(node.toString()).append("\n")
        }
        return sb.toString()
    }
}

fun createPath(pathData: PathData): Array<PathNode> {
    @Suppress("UNCHECKED_CAST")
    return when (pathData) {
        is Array<*> -> pathData as Array<PathNode>
        is PathDelegate -> {
            with(PathBuilder()) {
                pathData.delegate(this)
                getNodes()
            }
        }
        else -> throw IllegalArgumentException("Must be array of PathNodes or PathDelegate")
    }
}
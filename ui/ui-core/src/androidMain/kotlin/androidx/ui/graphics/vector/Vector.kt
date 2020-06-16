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

package androidx.ui.graphics.vector

import androidx.ui.graphics.BlendMode
import androidx.ui.graphics.Brush
import androidx.ui.graphics.Color
import androidx.ui.graphics.ColorFilter
import androidx.ui.graphics.Path
import androidx.ui.graphics.StrokeCap
import androidx.ui.graphics.StrokeJoin
import androidx.ui.graphics.drawscope.DrawScope
import androidx.ui.graphics.drawscope.Stroke
import androidx.ui.graphics.drawscope.withTransform
import androidx.ui.graphics.vectormath.Matrix4
import androidx.ui.unit.IntSize
import androidx.ui.util.fastForEach
import androidx.ui.util.toRadians
import kotlin.math.ceil

const val DefaultGroupName = ""
const val DefaultRotation = 0.0f
const val DefaultPivotX = 0.0f
const val DefaultPivotY = 0.0f
const val DefaultScaleX = 1.0f
const val DefaultScaleY = 1.0f
const val DefaultTranslationX = 0.0f
const val DefaultTranslationY = 0.0f

val EmptyPath = emptyList<PathNode>()

inline fun PathData(block: PathBuilder.() -> Unit): List<PathNode> =
    with(PathBuilder()) {
        block()
        getNodes()
    }

const val DefaultPathName = ""
const val DefaultStrokeLineWidth = 0.0f
const val DefaultStrokeLineMiter = 4.0f

val DefaultStrokeLineCap = StrokeCap.butt
val DefaultStrokeLineJoin = StrokeJoin.miter
val DefaultTintBlendMode = BlendMode.srcIn
val DefaultTintColor = Color.Transparent

fun addPathNodes(pathStr: String?): List<PathNode> =
    if (pathStr == null) {
        EmptyPath
    } else {
        PathParser().parsePathString(pathStr).toNodes()
    }

sealed class VNode {

    /**
     * Callback invoked whenever the node in the vector tree is modified in a way that would
     * change the output of the Vector
     */
    internal open var invalidateListener: (() -> Unit)? = null

    fun invalidate() {
        invalidateListener?.invoke()
    }

    abstract fun DrawScope.draw()
}

internal class VectorComponent(
    var viewportWidth: Float,
    var viewportHeight: Float,
    var defaultWidth: Float,
    var defaultHeight: Float,
    val name: String = ""
) : VNode() {

    val root = GroupComponent(this@VectorComponent.name).apply {
        pivotX = 0.0f
        pivotY = 0.0f
        scaleX = defaultWidth / viewportWidth
        scaleY = defaultHeight / viewportHeight
        invalidateListener = {
            isDirty = true
        }
    }

    private var isDirty: Boolean = true

    private val cacheDrawScope = DrawCache()

    val size: Int
        get() = root.size

    /**
     * Cached lambda used to avoid allocating the lambda on each draw invocation
     */
    private val drawVectorBlock: DrawScope.() -> Unit = {
        with (root) { draw() }
    }

    fun DrawScope.draw(alpha: Float, colorFilter: ColorFilter?) {
        if (isDirty) {
            cacheDrawScope.drawCachedImage(
                IntSize(ceil(defaultWidth).toInt(), ceil(defaultHeight).toInt()),
                this@draw,
                layoutDirection,
                drawVectorBlock
            )
            isDirty = false
        }
        cacheDrawScope.drawInto(this, alpha, colorFilter)
    }

    override fun DrawScope.draw() {
        draw(1.0f, null)
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

internal data class PathComponent(val name: String) : VNode() {

    var fill: Brush? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var fillAlpha: Float = 1.0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var pathData: List<PathNode> = EmptyPath
        set(value) {
            if (field != value) {
                field = value
                isPathDirty = true
                invalidate()
            }
        }

    var strokeAlpha: Float = 1.0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var strokeLineWidth: Float = DefaultStrokeLineWidth
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var stroke: Brush? = null
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var strokeLineCap: StrokeCap = DefaultStrokeLineCap
        set(value) {
            if (field != value) {
                field = value
                isStrokeDirty = true
                invalidate()
            }
        }

    var strokeLineJoin: StrokeJoin = DefaultStrokeLineJoin
        set(value) {
            if (field != value) {
                field = value
                isStrokeDirty = true
                invalidate()
            }
        }

    var strokeLineMiter: Float = DefaultStrokeLineMiter
        set(value) {
            if (field != value) {
                field = value
                isStrokeDirty = true
                invalidate()
            }
        }

    private var isPathDirty = true
    private var isStrokeDirty = true

    private var strokeStyle: Stroke? = null

    private val path = Path()

    private val parser = PathParser()

    private fun updatePath() {
        parser.clear()
        path.reset()
        parser.addPathNodes(pathData).toPath(path)
    }

    override fun DrawScope.draw() {
        if (isPathDirty) {
            updatePath()
            isPathDirty = false
        }

        fill?.let { drawPath(path, brush = it, alpha = fillAlpha) }
        stroke?.let {
            var targetStroke = strokeStyle
            if (isStrokeDirty || targetStroke == null) {
                targetStroke =
                    Stroke(strokeLineWidth, strokeLineMiter, strokeLineCap, strokeLineJoin)
                strokeStyle = targetStroke
                isStrokeDirty = false
            }
            drawPath(path, brush = it, alpha = strokeAlpha, style = targetStroke)
        }
    }

    override fun toString(): String {
        return path.toString()
    }
}

internal data class GroupComponent(val name: String = DefaultGroupName) : VNode() {

    private var groupMatrix: Matrix4? = null

    private val children = mutableListOf<VNode>()

    var clipPathData: List<PathNode> = EmptyPath
        set(value) {
            if (field != value) {
                field = value
                isClipPathDirty = true
                invalidate()
            }
        }

    private val willClipPath: Boolean
        get() = clipPathData.isNotEmpty()

    private var isClipPathDirty = true

    private var clipPath: Path? = null
    private var parser: PathParser? = null

    override var invalidateListener: (() -> Unit)? = null
        set(value) {
            field = value
            children.fastForEach { child ->
                child.invalidateListener = value
            }
        }

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

            targetParser.addPathNodes(clipPathData).toPath(targetClip)
        }
    }

    var rotation: Float = DefaultRotation
        set(value) {
            if (field != value) {
                field = value
                isMatrixDirty = true
                invalidate()
            }
        }

    var pivotX: Float = DefaultPivotX
        set(value) {
            if (field != value) {
                field = value
                isMatrixDirty = true
                invalidate()
            }
        }

    var pivotY: Float = DefaultPivotY
        set(value) {
            if (field != value) {
                field = value
                isMatrixDirty = true
                invalidate()
            }
        }

    var scaleX: Float = DefaultScaleX
        set(value) {
            if (field != value) {
                field = value
                isMatrixDirty = true
                invalidate()
            }
        }

    var scaleY: Float = DefaultScaleY
        set(value) {
            if (field != value) {
                field = value
                isMatrixDirty = true
                invalidate()
            }
        }

    var translationX: Float = DefaultTranslationX
        set(value) {
            if (field != value) {
                field = value
                isMatrixDirty = true
                invalidate()
            }
        }

    var translationY: Float = DefaultTranslationY
        set(value) {
            if (field != value) {
                field = value
                isMatrixDirty = true
                invalidate()
            }
        }

    private var isMatrixDirty = true

    private fun updateMatrix() {
        val matrix: Matrix4
        val target = groupMatrix
        if (target == null) {
            matrix = Matrix4()
            groupMatrix = matrix
        } else {
            matrix = target
        }
        matrix.assignColumns(Matrix4.identity())
        // M = T(translationX + pivotX, translationY + pivotY) *
        //     R(rotation) * S(scaleX, scaleY) *
        //     T(-pivotX, -pivotY)
        matrix.translate(translationX + pivotX, translationY + pivotY)
        matrix.rotateZ(radians = rotation.toRadians())
        matrix.scale(scaleX, scaleY, 1f)
        matrix.translate(-pivotX, -pivotY)
    }

    fun insertAt(index: Int, instance: VNode) {
        if (index < size) {
            children[index] = instance
        } else {
            children.add(instance)
        }
        instance.invalidateListener = invalidateListener
        invalidate()
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
        invalidate()
    }

    fun remove(index: Int, count: Int) {
        repeat(count) {
            children[index].invalidateListener = null
            children.removeAt(index)
        }
        invalidate()
    }

    override fun DrawScope.draw() {
        if (isMatrixDirty) {
            updateMatrix()
            isMatrixDirty = false
        }

        if (isClipPathDirty) {
            updateClipPath()
            isClipPathDirty = false
        }

        withTransform({
            val targetClip = clipPath
            if (willClipPath && targetClip != null) {
                clipPath(targetClip)
            }
            groupMatrix?.let { transform(it) }
        }) {
            children.fastForEach { node ->
                with(node) {
                    this@draw.draw()
                }
            }
        }
    }

    val size: Int
        get() = children.size

    override fun toString(): String {
        val sb = StringBuilder().append("VGroup: ").append(name)
        children.fastForEach { node ->
            sb.append("\t").append(node.toString()).append("\n")
        }
        return sb.toString()
    }
}

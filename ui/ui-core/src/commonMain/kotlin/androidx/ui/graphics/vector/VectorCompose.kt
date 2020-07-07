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
@file:OptIn(ExperimentalComposeApi::class)
package androidx.ui.graphics.vector

import androidx.compose.AbstractApplier
import androidx.compose.Composable
import androidx.compose.CompositionReference
import androidx.compose.Composition
import androidx.compose.ExperimentalComposeApi
import androidx.compose.Recomposer
import androidx.compose.compositionFor
import androidx.compose.emit
import androidx.compose.key
import androidx.ui.graphics.Brush
import androidx.ui.graphics.StrokeCap
import androidx.ui.graphics.StrokeJoin

@Composable
fun Group(
    name: String = DefaultGroupName,
    rotation: Float = DefaultRotation,
    pivotX: Float = DefaultPivotX,
    pivotY: Float = DefaultPivotY,
    scaleX: Float = DefaultScaleX,
    scaleY: Float = DefaultScaleY,
    translationX: Float = DefaultTranslationX,
    translationY: Float = DefaultTranslationY,
    clipPathData: List<PathNode> = EmptyPath,
    children: @Composable () -> Unit
) {
    key(name) {
        emit<GroupComponent, VectorApplier>(
            ctor = { GroupComponent(name) },
            update = {
                set(rotation) { this.rotation = it }
                set(pivotX) { this.pivotX = it }
                set(pivotY) { this.pivotY = it }
                set(scaleX) { this.scaleX = it }
                set(scaleY) { this.scaleY = it }
                set(translationX) { this.translationX = it }
                set(translationY) { this.translationY = it }
                set(clipPathData) { this.clipPathData = it }
            }
        ) {
            children()
        }
    }
}

@Composable
fun Path(
    pathData: List<PathNode>,
    name: String = DefaultPathName,
    fill: Brush? = null,
    fillAlpha: Float = 1.0f,
    stroke: Brush? = null,
    strokeAlpha: Float = 1.0f,
    strokeLineWidth: Float = DefaultStrokeLineWidth,
    strokeLineCap: StrokeCap = DefaultStrokeLineCap,
    strokeLineJoin: StrokeJoin = DefaultStrokeLineJoin,
    strokeLineMiter: Float = DefaultStrokeLineMiter
) {
    key(name) {
        emit<PathComponent, VectorApplier>(
            ctor = { PathComponent(name) },
            update = {
                set(pathData) { this.pathData = it }
                set(fill) { this.fill = it }
                set(fillAlpha) { this.fillAlpha = it }
                set(stroke) { this.stroke = it }
                set(strokeAlpha) { this.strokeAlpha = it }
                set(strokeLineWidth) { this.strokeLineWidth = it }
                set(strokeLineJoin) { this.strokeLineJoin = it }
                set(strokeLineCap) { this.strokeLineCap = it }
                set(strokeLineMiter) { this.strokeLineMiter = it }
            }
        )
    }
}

@Suppress("NAME_SHADOWING")
internal fun composeVector(
    container: VectorComponent,
    recomposer: Recomposer,
    parent: CompositionReference? = null,
    composable: @Composable (viewportWidth: Float, viewportHeight: Float) -> Unit
): Composition = compositionFor(
    container,
    VectorApplier(container.root),
    recomposer,
    parent
).apply {
    setContent {
        composable(container.viewportWidth, container.viewportHeight)
    }
}

class VectorApplier(root: VNode) : AbstractApplier<VNode>(root) {
    override fun insert(index: Int, instance: VNode) {
        current.asGroup().insertAt(index, instance)
    }

    override fun remove(index: Int, count: Int) {
        current.asGroup().remove(index, count)
    }

    override fun onClear() {
        root.asGroup().let { it.remove(0, it.numChildren) }
    }

    override fun move(from: Int, to: Int, count: Int) {
        current.asGroup().move(from, to, count)
    }

    private fun VNode.asGroup(): GroupComponent {
        return when (this) {
            is GroupComponent -> this
            else -> error("Cannot only insert VNode into Group")
        }
    }
}

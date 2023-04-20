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

package androidx.compose.foundation.lazy

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutAnimateItemModifierNode
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.ParentDataModifierNode
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

internal class LazyItemScopeImpl : LazyItemScope {

    private var maxWidthState: MutableState<Int> = mutableStateOf(Int.MAX_VALUE)
    private var maxHeightState: MutableState<Int> = mutableStateOf(Int.MAX_VALUE)

    fun setMaxSize(width: Int, height: Int) {
        maxWidthState.value = width
        maxHeightState.value = height
    }

    override fun Modifier.fillParentMaxSize(fraction: Float) = then(
        ParentSizeElement(
            widthState = maxWidthState,
            heightState = maxHeightState,
            fraction = fraction,
            inspectorName = "fillParentMaxSize"
        )
    )

    override fun Modifier.fillParentMaxWidth(fraction: Float) = then(
        ParentSizeElement(
            widthState = maxWidthState,
            fraction = fraction,
            inspectorName = "fillParentMaxWidth"
        )
    )

    override fun Modifier.fillParentMaxHeight(fraction: Float) = then(
        ParentSizeElement(
            heightState = maxHeightState,
            fraction = fraction,
            inspectorName = "fillParentMaxHeight"
        )
    )

    @ExperimentalFoundationApi
    override fun Modifier.animateItemPlacement(animationSpec: FiniteAnimationSpec<IntOffset>) =
        this then AnimateItemPlacementElement(animationSpec)
}

private class ParentSizeElement(
    val fraction: Float,
    val widthState: State<Int>? = null,
    val heightState: State<Int>? = null,
    val inspectorName: String
) : ModifierNodeElement<ParentSizeNode>() {
    override fun create(): ParentSizeNode {
        return ParentSizeNode(
            fraction = fraction,
            widthState = widthState,
            heightState = heightState
        )
    }

    override fun update(node: ParentSizeNode): ParentSizeNode = node.also {
        it.fraction = fraction
        it.widthState = widthState
        it.heightState = heightState
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ParentSizeNode) return false
        return fraction == other.fraction &&
            widthState == other.widthState &&
            heightState == other.heightState
    }

    override fun hashCode(): Int {
        var result = widthState?.hashCode() ?: 0
        result = 31 * result + (heightState?.hashCode() ?: 0)
        result = 31 * result + fraction.hashCode()
        return result
    }

    override fun InspectorInfo.inspectableProperties() {
        name = inspectorName
        value = fraction
    }
}

private class ParentSizeNode(
    var fraction: Float,
    var widthState: State<Int>? = null,
    var heightState: State<Int>? = null,
) : LayoutModifierNode, Modifier.Node() {

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {
        val width = widthState?.let {
            if (it.value != Constraints.Infinity) {
                (it.value * fraction).roundToInt()
            } else {
                Constraints.Infinity
            }
        } ?: Constraints.Infinity

        val height = heightState?.let {
            if (it.value != Constraints.Infinity) {
                (it.value * fraction).roundToInt()
            } else {
                Constraints.Infinity
            }
        } ?: Constraints.Infinity
        val childConstraints = Constraints(
            minWidth = if (width != Constraints.Infinity) width else constraints.minWidth,
            minHeight = if (height != Constraints.Infinity) height else constraints.minHeight,
            maxWidth = if (width != Constraints.Infinity) width else constraints.maxWidth,
            maxHeight = if (height != Constraints.Infinity) height else constraints.maxHeight,
        )
        val placeable = measurable.measure(childConstraints)
        return layout(placeable.width, placeable.height) {
            placeable.place(0, 0)
        }
    }
}

private class AnimateItemPlacementElement(
    val animationSpec: FiniteAnimationSpec<IntOffset>
) : ModifierNodeElement<AnimateItemPlacementNode>() {

    override fun create(): AnimateItemPlacementNode = AnimateItemPlacementNode(animationSpec)

    override fun update(node: AnimateItemPlacementNode): AnimateItemPlacementNode = node.also {
        it.delegatingNode.placementAnimationSpec = animationSpec
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AnimateItemPlacementElement) return false
        return animationSpec != other.animationSpec
    }

    override fun hashCode(): Int {
        return animationSpec.hashCode()
    }

    override fun InspectorInfo.inspectableProperties() {
        name = "animateItemPlacement"
        value = animationSpec
    }
}

private class AnimateItemPlacementNode(
    animationSpec: FiniteAnimationSpec<IntOffset>
) : DelegatingNode(), ParentDataModifierNode {

    val delegatingNode = delegated { LazyLayoutAnimateItemModifierNode(animationSpec) }

    override fun Density.modifyParentData(parentData: Any?): Any = delegatingNode
}

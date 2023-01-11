/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.foundation.newtext.text.modifiers

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatingNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.node.SemanticsModifierNode
import androidx.compose.ui.node.invalidateMeasurements
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.unit.Constraints

@OptIn(ExperimentalComposeUiApi::class)
internal class SelectableStaticTextModifier(
    params: StaticTextLayoutDrawParams
) : DelegatingNode(), LayoutModifierNode, DrawModifierNode, GlobalPositionAwareModifierNode,
    SemanticsModifierNode {

    private val delegate = delegated { StaticTextModifier(params) }

    init {
        requireNotNull(params.selectionController) {
            "Do not use SelectionCapableStaticTextModifier unless selectionController != null"
        }
    }

    var params: StaticTextLayoutDrawParams = params
        set(value) {
            field = value
            delegate.params = value

            // selection means we always have to invalidate on set to redo position
            invalidateMeasurements()
        }

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        params.selectionController?.updateGlobalPosition(coordinates)
    }

    override fun ContentDrawScope.draw() = delegate.drawNonExtension(this)

    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult = delegate.measureNonExtension(this, measurable, constraints)

    override val semanticsConfiguration: SemanticsConfiguration
        get() = delegate.semanticsConfiguration

    override fun IntrinsicMeasureScope.minIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int = delegate.minIntrinsicWidthNonExtension(this, measurable, height)

    override fun IntrinsicMeasureScope.minIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int = delegate.minIntrinsicHeightNonExtension(this, measurable, width)

    override fun IntrinsicMeasureScope.maxIntrinsicWidth(
        measurable: IntrinsicMeasurable,
        height: Int
    ): Int = delegate.maxIntrinsicWidthNonExtension(this, measurable, height)

    override fun IntrinsicMeasureScope.maxIntrinsicHeight(
        measurable: IntrinsicMeasurable,
        width: Int
    ): Int = delegate.maxIntrinsicHeightNonExtension(this, measurable, width)
}
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

package androidx.compose.material3

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.platform.debugInspectorInfo
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Reserves at least 48.dp in size to disambiguate touch interactions if the element would measure
 * smaller.
 *
 * https://m3.material.io/foundations/accessible-design/accessibility-basics#28032e45-c598-450c-b355-f9fe737b1cd8
 *
 * This uses the Material recommended minimum size of 48.dp x 48.dp, which may not the same as the
 * system enforced minimum size. The minimum clickable / touch target size (48.dp by default) is
 * controlled by the system via ViewConfiguration` and automatically expanded at the touch input layer.
 *
 * This modifier is not needed for touch target expansion to happen. It only affects layout, to make
 * sure there is adequate space for touch target expansion.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Suppress("ModifierInspectorInfo")
fun Modifier.minimumInteractiveComponentSize(): Modifier = composed(
    inspectorInfo = debugInspectorInfo {
        name = "minimumInteractiveComponentSize"
        // TODO: b/214589635 - surface this information through the layout inspector in a better way
        //  - for now just add some information to help developers debug what this size represents.
        properties["README"] = "Reserves at least 48.dp in size to disambiguate touch " +
            "interactions if the element would measure smaller"
    }
) {
    if (LocalMinimumInteractiveComponentEnforcement.current) {
        MinimumInteractiveComponentSizeModifier(minimumInteractiveComponentSize)
    } else {
        Modifier
    }
}

/**
 * CompositionLocal that configures whether Material components that have a visual size that is
 * lower than the minimum touch target size for accessibility (such as Button) will include
 * extra space outside the component to ensure that they are accessible. If set to false there
 * will be no extra space, and so it is possible that if the component is placed near the edge of
 * a layout / near to another component without any padding, there will not be enough space for
 * an accessible touch target.
 */
@Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
@get:ExperimentalMaterial3Api
@ExperimentalMaterial3Api
val LocalMinimumInteractiveComponentEnforcement: ProvidableCompositionLocal<Boolean> =
    staticCompositionLocalOf { true }

/**
 * CompositionLocal that configures whether Material components that have a visual size that is
 * lower than the minimum touch target size for accessibility (such as [Button]) will include
 * extra space outside the component to ensure that they are accessible. If set to false there
 * will be no extra space, and so it is possible that if the component is placed near the edge of
 * a layout / near to another component without any padding, there will not be enough space for
 * an accessible touch target.
 */
@Suppress("OPT_IN_MARKER_ON_WRONG_TARGET")
@get:ExperimentalMaterial3Api
@ExperimentalMaterial3Api
@Deprecated(
    message = "Use LocalMinimumInteractiveComponentEnforcement instead.",
    replaceWith = ReplaceWith(
        "LocalMinimumInteractiveComponentEnforcement"
    ),
    level = DeprecationLevel.WARNING
)
val LocalMinimumTouchTargetEnforcement: ProvidableCompositionLocal<Boolean> =
    LocalMinimumInteractiveComponentEnforcement

private class MinimumInteractiveComponentSizeModifier(val size: DpSize) : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints
    ): MeasureResult {

        val placeable = measurable.measure(constraints)

        // Be at least as big as the minimum dimension in both dimensions
        val width = maxOf(placeable.width, size.width.roundToPx())
        val height = maxOf(placeable.height, size.height.roundToPx())

        return layout(width, height) {
            val centerX = ((width - placeable.width) / 2f).roundToInt()
            val centerY = ((height - placeable.height) / 2f).roundToInt()
            placeable.place(centerX, centerY)
        }
    }

    override fun equals(other: Any?): Boolean {
        val otherModifier = other as? MinimumInteractiveComponentSizeModifier ?: return false
        return size == otherModifier.size
    }

    override fun hashCode(): Int {
        return size.hashCode()
    }
}

private val minimumInteractiveComponentSize: DpSize = DpSize(48.dp, 48.dp)

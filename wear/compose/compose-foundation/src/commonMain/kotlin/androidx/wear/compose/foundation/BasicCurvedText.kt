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

package androidx.wear.compose.foundation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * Apply additional space along each edge of the content in [Dp]. Note that the start and end
 * edges will be determined by the direction (clockwise or counterclockwise)
 *
 * @param outer Padding in the outward direction from the center of the
 * [CurvedRow]
 * @param inner Padding in the inwards direction towards the center of the [CurvedRow]
 * @param start Padding added at the start of the component.
 * @param end Padding added at the end of the component.
 */
@Stable
class ArcPaddingValues(val outer: Dp, val inner: Dp, val start: Dp, val end: Dp) {
    override fun equals(other: Any?): Boolean {
        return other is ArcPaddingValues &&
            outer == other.outer &&
            inner == other.inner &&
            start == other.start &&
            end == other.end
    }

    override fun hashCode() = ((outer.hashCode() * 31 + inner.hashCode()) * 31 + start.hashCode()) *
        31 + end.hashCode()

    override fun toString(): String {
        return "ArcPaddingValues(outer=$outer, inner=$inner, start=$start, end=$end)"
    }

    companion object {
        /**
         * An arc padding value with zero magnitude.
         */
        @Stable
        val Zero = ArcPaddingValues(0.dp, 0.dp, 0.dp, 0.dp)
    }
}

/**
 * Apply [all] dp of additional space along each edge of the content.
 */
fun ArcPaddingValues(all: Dp) = ArcPaddingValues(all, all, all, all)

/**
 * Apply [radial] dp of additional space on the edges towards and away from the center, and
 * [angular] dp before and after the component.
 */
fun ArcPaddingValues(radial: Dp = 0.dp, angular: Dp = 0.dp) =
    ArcPaddingValues(radial, radial, angular, angular)

/**
 * CurvedText is a component allowing developers to easily write curved text following
 * the curvature a circle (usually at the edge of a circular screen).
 * CurvedText can be only created within the CurvedRow to ensure the best experience, like being
 * able to specify to positioning.
 *
 * @sample androidx.wear.compose.foundation.samples.CurvedAndNormalText
 *
 * @param text The text to display
 * @param style Specified the style to use.
 * @param clockwise The direction the text follows (default is true). Usually text at the top of the
 * screen goes clockwise, and text at the bottom goes counterclockwise.
 * @param contentArcPadding Allows to specify additional space along each "edge" of the content in
 * [Dp] see [ArcPaddingValues]
 */
@Composable
fun CurvedRowScope.BasicCurvedText(
    text: String,
    style: CurvedTextStyle,
    modifier: Modifier = Modifier,
    clockwise: Boolean = true,
    contentArcPadding: ArcPaddingValues = ArcPaddingValues(0.dp),
) {
    // Apply defaults when fields are not specified
    val actualStyle = DefaultCurvedTextStyles + style

    val delegate = remember { CurvedTextDelegate() }
    val fontSizePx = with(LocalDensity.current) {
        actualStyle.fontSize.toPx()
    }
    val arcPaddingPx = with(LocalDensity.current) {
        remember(contentArcPadding) {
            ArcPaddingPx(
                contentArcPadding.outer.toPx(),
                contentArcPadding.inner.toPx(),
                contentArcPadding.start.toPx(),
                contentArcPadding.end.toPx()
            )
        }
    }
    delegate.updateIfNeeded(text, clockwise, fontSizePx, arcPaddingPx)

    Layout(
        modifier = modifier
            .then(CurvedTextModifier())
            .graphicsLayer()
            .drawBehind {
                drawIntoCanvas { canvas ->
                    delegate.doDraw(canvas, size, actualStyle.color, actualStyle.background)
                }
            },
        content = {},
        // We need to report our real size to the CurvedRow, (we use intrinsic size),
        // But for compose layout we need to take the whole view.
        measurePolicy = remember {
            object : MeasurePolicy {
                override fun MeasureScope.measure(
                    measurables: List<Measurable>,
                    constraints: Constraints
                ): MeasureResult {
                    return layout(
                        constraints.maxWidth,
                        constraints.maxHeight,
                        alignmentLines = mapOf(
                            FirstBaseline to delegate.baseLinePosition.toInt()
                        )
                    ) {}
                }

                override fun IntrinsicMeasureScope.minIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int
                ) = ceil(delegate.textWidth).toInt()

                override fun IntrinsicMeasureScope.minIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int
                ) = ceil(delegate.textHeight).toInt()

                override fun IntrinsicMeasureScope.maxIntrinsicWidth(
                    measurables: List<IntrinsicMeasurable>,
                    height: Int
                ) = ceil(delegate.textWidth).toInt()

                override fun IntrinsicMeasureScope.maxIntrinsicHeight(
                    measurables: List<IntrinsicMeasurable>,
                    width: Int
                ) = ceil(delegate.textHeight).toInt()
            }
        }
    )
}

private class CurvedTextModifier() : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any {
        return (parentData as? CurvedRowParentData ?: CurvedRowParentData()).also {
            it.isCurvedComponent = true
        }
    }

    override fun hashCode(): Int = 1

    override fun equals(other: Any?): Boolean {
        return other is CurvedTextModifier
    }

    override fun toString(): String =
        "CurvedTextModifier()"
}

internal data class ArcPaddingPx(
    val outer: Float,
    val inner: Float,
    val before: Float,
    val after: Float
)

internal expect class CurvedTextDelegate() {
    var textWidth: Float
    var textHeight: Float
    var baseLinePosition: Float

    fun updateIfNeeded(
        text: String,
        clockwise: Boolean,
        fontSizePx: Float,
        arcPaddingPx: ArcPaddingPx
    )

    fun doDraw(canvas: Canvas, size: Size, color: Color, background: Color)
}
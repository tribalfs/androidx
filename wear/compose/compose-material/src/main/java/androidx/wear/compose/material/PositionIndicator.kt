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

package androidx.wear.compose.material

import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.AbsoluteAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutModifier
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListAnchorType
import androidx.wear.compose.foundation.lazy.ScalingLazyListItemInfo
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.materialcore.isRoundDevice
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Enum used by adapters to specify if the Position Indicator needs to be shown, hidden,
 * or hidden after a small delay.
 */
@kotlin.jvm.JvmInline
public value class PositionIndicatorVisibility internal constructor(internal val value: Int) {
    companion object {
        /**
         * Show the Position Indicator.
         */
        val Show = PositionIndicatorVisibility(1)

        /**
         * Hide the Position Indicator.
         */
        val Hide = PositionIndicatorVisibility(2)

        /**
         * Hide the Position Indicator after a short delay.
         */
        val AutoHide = PositionIndicatorVisibility(3)
    }
}

/**
 * An object representing the relative position of a scrollbar or rolling side button or rotating
 * bezel position. This interface is implemented by classes that adapt other state information such
 * as [ScalingLazyListState] or [ScrollState] of scrollable containers or to represent the
 * position of say a volume control that can be 'ticked' using a rolling side button or rotating
 * bezel.
 *
 * Implementing classes provide [positionFraction] to determine where in the range [0..1] that the
 * indicator should be displayed and [sizeFraction] to determine the size of the indicator in the
 * range [0..1]. E.g. If a [ScalingLazyListState] had 50 items and the last 5 were visible it
 * would have a position of 1.0f to show that the scroll is positioned at the end of the list and a
 * size of 5 / 50 = 0.1f to indicate that 10% of the visible items are currently visible.
 */
@Stable
interface PositionIndicatorState {
    /**
     * Position of the indicator in the range [0f,1f]. 0f means it is at the top|start, 1f means
     * it is positioned at the bottom|end.
     */
    @get:FloatRange(from = 0.0, to = 1.0)
    val positionFraction: Float

    /**
     * Size of the indicator in the range [0f,1f]. 1f means it takes the whole space.
     *
     * @param scrollableContainerSizePx the height or width of the container
     * in pixels depending on orientation of the indicator, (height for vertical, width for
     * horizontal)
     */
    @FloatRange(from = 0.0, to = 1.0)
    fun sizeFraction(
        @FloatRange(from = 0.0)
        scrollableContainerSizePx: Float
    ): Float

    /**
     * Should we show the Position Indicator
     *
     * @param scrollableContainerSizePx the height or width of the container
     * in pixels depending on orientation of the indicator, (height for vertical, width for
     * horizontal)
     */
    fun visibility(
        @FloatRange(from = 0.0)
        scrollableContainerSizePx: Float
    ): PositionIndicatorVisibility
}

/**
 * Creates an [PositionIndicator] based on the values in a [ScrollState] object.
 * e.g. a [Column] implementing [Modifier.verticalScroll] provides a [ScrollState].
 *
 * For more information, see the
 * [Scroll indicators](https://developer.android.com/training/wearables/components/scroll)
 * guide.
 *
 * @param scrollState The scrollState to use as the basis for the PositionIndicatorState.
 * @param modifier The modifier to be applied to the component
 * @param reverseDirection Reverses direction of PositionIndicator if true
 */
@Composable
public fun PositionIndicator(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    reverseDirection: Boolean = false
) = PositionIndicator(
    ScrollStateAdapter(scrollState),
    indicatorHeight = 50.dp,
    indicatorWidth = 4.dp,
    paddingHorizontal = 5.dp,
    modifier = modifier,
    reverseDirection = reverseDirection
)

/**
 * Creates an [PositionIndicator] based on the values in a [ScalingLazyListState] object that
 * a [ScalingLazyColumn] uses.
 *
 * For more information, see the
 * [Scroll indicators](https://developer.android.com/training/wearables/components/scroll)
 * guide.
 *
 * @param scalingLazyListState the [ScalingLazyListState] to use as the basis for the
 * PositionIndicatorState.
 * @param modifier The modifier to be applied to the component
 * @param reverseDirection Reverses direction of PositionIndicator if true
 */
@Composable
public fun PositionIndicator(
    scalingLazyListState: ScalingLazyListState,
    modifier: Modifier = Modifier,
    reverseDirection: Boolean = false
) = PositionIndicator(
    state = ScalingLazyColumnStateAdapter(
        state = scalingLazyListState
    ),
    indicatorHeight = 50.dp,
    indicatorWidth = 4.dp,
    paddingHorizontal = 5.dp,
    modifier = modifier,
    reverseDirection = reverseDirection
)

/**
 * Creates an [PositionIndicator] based on the values in a [ScalingLazyListState] object that
 * a [ScalingLazyColumn] uses.
 *
 * For more information, see the
 * [Scroll indicators](https://developer.android.com/training/wearables/components/scroll)
 * guide.
 *
 * @param scalingLazyListState the [ScalingLazyListState] to use as the basis for the
 * PositionIndicatorState.
 * @param modifier The modifier to be applied to the component
 * @param reverseDirection Reverses direction of PositionIndicator if true
 */
@Suppress("DEPRECATION")
@Deprecated(
    "This overload is provided for backwards compatibility with Compose for Wear OS 1.1." +
        "A newer overload is available which uses ScalingLazyListState from " +
        "androidx.wear.compose.foundation.lazy package", level = DeprecationLevel.WARNING
)
@Composable
public fun PositionIndicator(
    scalingLazyListState: androidx.wear.compose.material.ScalingLazyListState,
    modifier: Modifier = Modifier,
    reverseDirection: Boolean = false
) = PositionIndicator(
    state = MaterialScalingLazyColumnStateAdapter(
        state = scalingLazyListState
    ),
    indicatorHeight = 50.dp,
    indicatorWidth = 4.dp,
    paddingHorizontal = 5.dp,
    modifier = modifier,
    reverseDirection = reverseDirection
)

/**
 * Creates an [PositionIndicator] based on the values in a [LazyListState] object that
 * a [LazyColumn] uses.
 *
 * For more information, see the
 * [Scroll indicators](https://developer.android.com/training/wearables/components/scroll)
 * guide.
 *
 * @param lazyListState the [LazyListState] to use as the basis for the
 * PositionIndicatorState.
 * @param modifier The modifier to be applied to the component
 * @param reverseDirection Reverses direction of PositionIndicator if true
 */
@Composable
public fun PositionIndicator(
    lazyListState: LazyListState,
    modifier: Modifier = Modifier,
    reverseDirection: Boolean = false
) = PositionIndicator(
    state = LazyColumnStateAdapter(
        state = lazyListState
    ),
    indicatorHeight = 50.dp,
    indicatorWidth = 4.dp,
    paddingHorizontal = 5.dp,
    modifier = modifier,
    reverseDirection = reverseDirection
)

/**
 * Specifies where in the screen the Position indicator will be.
 */
@kotlin.jvm.JvmInline
value class PositionIndicatorAlignment internal constructor(internal val pos: Int) {
    companion object {
        /**
         * Position the indicator at the end of the layout (at the right for LTR and left for RTL)
         * This is the norm for scroll indicators.
         */
        val End = PositionIndicatorAlignment(0)

        // TODO(b/224770222): Add tests.
        /**
         * Position the indicator opposite to the physical rotating side button (RSB). (at the left
         * by default and at the right if the device is rotated 180 degrees)
         * This is the default for RSB indicators as we want to avoid it being obscured when the
         * user is interacting with the RSB.
         */
        val OppositeRsb = PositionIndicatorAlignment(1)

        /**
         * Position the indicator at the left of the screen.
         * This is useful to implement custom positioning, but usually
         * [PositionIndicatorAlignment#End] or [PositionIndicatorAlignment#OppositeRsb] should be
         * used.
         */
        val Left = PositionIndicatorAlignment(2)

        /**
         * Position the indicator at the right of the screen
         * This is useful to implement custom positioning, but usually
         * [PositionIndicatorAlignment#End] or [PositionIndicatorAlignment#OppositeRsb] should be
         * used.
         */
        val Right = PositionIndicatorAlignment(3)
    }

    override fun toString(): String {
        return when (this) {
            End -> "PositionIndicatorAlignment.End"
            OppositeRsb -> "PositionIndicatorAlignment.OppositeRsb"
            Left -> "PositionIndicatorAlignment.Left"
            Right -> "PositionIndicatorAlignment.Right"
            else -> "PositionIndicatorAlignment.unknown"
        }
    }
}

/**
 * Creates a [PositionIndicator] for controls like rotating side button, rotating bezel or slider.
 *
 * For more information, see the
 * [Scroll indicators](https://developer.android.com/training/wearables/components/scroll)
 * guide.
 *
 * @param value Value of the indicator in the [range] where 1 represents the
 * maximum value. E.g. If displaying a volume value from 0..11 then the [value] will be
 * volume/11.
 * @param range range of values that [value] can take
 * @param modifier Modifier to be applied to the component
 * @param color Color to draw the indicator on.
 * @param reverseDirection Reverses direction of PositionIndicator if true
 * @param position indicates where to put the PositionIndicator in the screen, default is
 * [PositionIndicatorPosition#OppositeRsb]
 */
@Composable
public fun PositionIndicator(
    value: () -> Float,
    modifier: Modifier = Modifier,
    range: ClosedFloatingPointRange<Float> = 0f..1f,
    color: Color = MaterialTheme.colors.onBackground,
    reverseDirection: Boolean = false,
    position: PositionIndicatorAlignment = PositionIndicatorAlignment.OppositeRsb
) = PositionIndicator(
    state = FractionPositionIndicatorState {
        (value() - range.start) / (range.endInclusive - range.start)
    },
    indicatorHeight = 76.dp,
    indicatorWidth = 6.dp,
    paddingHorizontal = 5.dp,
    color = color,
    modifier = modifier,
    reverseDirection = reverseDirection,
    position = position
)

/**
 * An indicator on one side of the screen to show the current [PositionIndicatorState].
 *
 * Typically used with the [Scaffold] but can be used to decorate any full screen situation.
 *
 * This composable should only be used to fill the whole screen as Wear Material Design language
 * requires the placement of the position indicator to be right center of the screen as the
 * indicator is curved on circular devices.
 *
 * It detects if the screen is round or square and draws itself as a curve or line.
 *
 * Note that the composable will take the whole screen, but it will be drawn with the given
 * dimensions [indicatorHeight] and [indicatorWidth], and position with respect to the edge of the
 * screen according to [paddingHorizontal]
 *
 * For more information, see the
 * [Scroll indicators](https://developer.android.com/training/wearables/components/scroll)
 * guide.
 *
 * @param state the [PositionIndicatorState] of the state we are displaying.
 * @param indicatorHeight the height of the position indicator in Dp.
 * @param indicatorWidth the width of the position indicator in Dp.
 * @param paddingHorizontal the padding to apply between the indicator and the border of the screen.
 * @param modifier The modifier to be applied to the component.
 * @param background the color to draw the non-active part of the position indicator.
 * @param color the color to draw the active part of the indicator in.
 * @param reverseDirection Reverses direction of PositionIndicator if true.
 * @param position indicates where to put the PositionIndicator on the screen, default is
 * [PositionIndicatorPosition#End]
 */
@Deprecated(
    "This overload is provided for backwards compatibility with " +
        "Compose for Wear OS 1.2." +
        "A newer overload is available with additional showFadeInAnimation, " +
        "showFadeOutAnimation and showPositionAnimation parameters.",
    level = DeprecationLevel.HIDDEN
)
@Composable
public fun PositionIndicator(
    state: PositionIndicatorState,
    indicatorHeight: Dp,
    indicatorWidth: Dp,
    paddingHorizontal: Dp,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colors.onBackground.copy(alpha = 0.3f),
    color: Color = MaterialTheme.colors.onBackground,
    reverseDirection: Boolean = false,
    position: PositionIndicatorAlignment = PositionIndicatorAlignment.End
) {
    PositionIndicator(
        state = state,
        indicatorHeight = indicatorHeight,
        indicatorWidth = indicatorWidth,
        paddingHorizontal = paddingHorizontal,
        modifier = modifier,
        background = background,
        color = color,
        reverseDirection = reverseDirection,
        position = position,
        showFadeInAnimation = true,
        showFadeOutAnimation = true,
        showPositionAnimation = true
    )
}

/**
 * An indicator on one side of the screen to show the current [PositionIndicatorState].
 *
 * Typically used with the [Scaffold] but can be used to decorate any full screen situation.
 *
 * This composable should only be used to fill the whole screen as Wear Material Design language
 * requires the placement of the position indicator to be right center of the screen as the
 * indicator is curved on circular devices.
 *
 * It detects if the screen is round or square and draws itself as a curve or line.
 *
 * Note that the composable will take the whole screen, but it will be drawn with the given
 * dimensions [indicatorHeight] and [indicatorWidth], and position with respect to the edge of the
 * screen according to [paddingHorizontal]
 *
 * This [PositionIndicator] has 3 separate flags to control different animations.
 * - [showFadeInAnimation] - controls fade-in animation.
 * - [showFadeOutAnimation] - controls fade-out animation.
 * - [showPositionAnimation] - controls position change animation.
 *
 * For performance reasons and for UX consistency, when [PositionIndicator] is used with scrollable
 * list, we recommend to switch off [showFadeInAnimation] and [showPositionAnimation] flags.
 * If [PositionIndicator] is used as a standalone indicator, for example as volume control,
 * then we recommend to have all 3 animations turned on.
 *
 * For more information, see the
 * [Scroll indicators](https://developer.android.com/training/wearables/components/scroll)
 * guide.
 *
 * @param state the [PositionIndicatorState] of the state we are displaying.
 * @param indicatorHeight the height of the position indicator in Dp.
 * @param indicatorWidth the width of the position indicator in Dp.
 * @param paddingHorizontal the padding to apply between the indicator and the border of the screen.
 * @param modifier The modifier to be applied to the component.
 * @param background the color to draw the non-active part of the position indicator.
 * @param color the color to draw the active part of the indicator in.
 * @param reverseDirection Reverses direction of PositionIndicator if true.
 * @param position indicates where to put the PositionIndicator on the screen, default is
 * [PositionIndicatorPosition#End]
 * @param showFadeInAnimation turns on the "Fade-in" animation of [PositionIndicator].
 * If true, the Fade-in animation is triggered when the [PositionIndicator] becomes
 * visible - either when state.visibility changes to Show, or state.visibility
 * is AutoHide and state.positionFraction/state.sizeFraction are changed.
 * @param showFadeOutAnimation turns on the "Fade-out" animation of PositionIndicator.
 * The Fade-out animation is used for hiding the [PositionIndicator] and making it invisible.
 * If true, the Fade-out animation is triggered after a delay if no changes in
 * state.positionFraction or state.sizeFraction were detected,
 * hiding the [PositionIndicator] with animation.
 * @param showPositionAnimation turns on the "Position" animation of [PositionIndicator].
 * The Position animation is used for animating changes between state.positionFraction
 * and state.sizeFraction of [PositionIndicatorState].
 * If true, the Position animation will be triggered on any change of
 * state.positionFraction or state.sizeFraction.
 */
@Composable
public fun PositionIndicator(
    state: PositionIndicatorState,
    indicatorHeight: Dp,
    indicatorWidth: Dp,
    paddingHorizontal: Dp,
    modifier: Modifier = Modifier,
    background: Color = MaterialTheme.colors.onBackground.copy(alpha = 0.3f),
    color: Color = MaterialTheme.colors.onBackground,
    reverseDirection: Boolean = false,
    position: PositionIndicatorAlignment = PositionIndicatorAlignment.End,
    showFadeInAnimation: Boolean = true,
    showFadeOutAnimation: Boolean = true,
    showPositionAnimation: Boolean = true
) {
    val isScreenRound = isRoundDevice()
    val layoutDirection = LocalLayoutDirection.current
    val leftyMode = isLeftyModeEnabled()
    var containerSize by remember { mutableStateOf(IntSize.Zero) }

    val alphaAnimatable = remember { Animatable(0f) }
    var showIndicator by remember { mutableStateOf(false) }

    val positionFractionAnimatable = remember { Animatable(0f) }
    val sizeFractionAnimatable = remember { Animatable(0f) }

    val positionChangeAnimationSpec: AnimationSpec<Float> =
        tween(
            durationMillis = 500,
            easing = CubicBezierEasing(0f, 0f, 0f, 1f)
        )

    val alphaChangeAnimationSpec: AnimationSpec<Float> =
        spring(stiffness = Spring.StiffnessMediumLow)

    val indicatorOnTheRight = when (position) {
        PositionIndicatorAlignment.End -> layoutDirection == LayoutDirection.Ltr
        PositionIndicatorAlignment.Left -> false
        PositionIndicatorAlignment.Right -> true
        PositionIndicatorAlignment.OppositeRsb -> leftyMode
        else -> true
    }

    val boundsSize: Density.() -> IntSize = {
        IntSize(
            ((if (isScreenRound) {
                // r is the distance from the center of the container to the arc we draw the
                // position indicator on (the center of the arc, which is indicatorWidth wide).
                val r = containerSize.width.toFloat() / 2 - paddingHorizontal.toPx() -
                    indicatorWidth.toPx() / 2
                // The sqrt is the size of the projection on the x axis of line between center of
                // the container and the point where we start the arc.
                // The coerceAtLeast is needed while initializing since containerSize.width is 0
                r - sqrt((sqr(r) - sqr(indicatorHeight.toPx() / 2)).coerceAtLeast(0f))
            } else 0f) +
                paddingHorizontal.toPx() + indicatorWidth.toPx()
                ).roundToInt(),
            (indicatorHeight.toPx() + indicatorWidth.toPx()).roundToInt()
        )
    }

    val boundsOffset: Density.() -> IntOffset = {
        // Note that indicators are on right or left, centered vertically.
        val indicatorSize = boundsSize()
        IntOffset(
            if (indicatorOnTheRight) containerSize.width - indicatorSize.width else 0,
            (containerSize.height - indicatorSize.height) / 2
        )
    }

    LaunchedEffect(state, showFadeInAnimation, showPositionAnimation, showFadeOutAnimation) {
        var beforeFirstAnimation = true

        launch {
            // This snapshotFlow listens to changes in position, size and visibility
            // of PositionIndicatorState and starts necessary animations if needed
            snapshotFlow {
                DisplayState(
                    state.positionFraction,
                    state.sizeFraction(containerSize.height.toFloat()),
                    state.visibility(containerSize.height.toFloat())
                )
            }.collectLatest {
                if (beforeFirstAnimation || !showPositionAnimation) {
                    sizeFractionAnimatable.snapTo(it.size)
                    positionFractionAnimatable.snapTo(it.position)
                    beforeFirstAnimation = false
                } else {
                    launch {
                        sizeFractionAnimatable
                            .animateTo(
                                it.size,
                                animationSpec = positionChangeAnimationSpec
                            )
                    }
                    launch {
                        positionFractionAnimatable
                            .animateTo(
                                it.position,
                                animationSpec = positionChangeAnimationSpec
                            )
                    }
                }

                when (it.visibility) {
                    // Visibility cases are handled in another snapshot flow
                    // by checking showIndicator value.
                    PositionIndicatorVisibility.Show -> showIndicator = true
                    PositionIndicatorVisibility.Hide -> showIndicator = false
                    else -> {
                        showIndicator = true
                        // Waiting for 2000ms and starting alpha animation to 0f
                        delay(2000)
                        showIndicator = false
                    }
                }
            }
        }

        // Skip first alpha animation only when initial visibility is SHOW
        var skipFirstAlphaAnimation =
            state.visibility(containerSize.height.toFloat()) == PositionIndicatorVisibility.Show

        // This snapshotFlow listens to changes of [showIndicator] flag and triggers
        // show or hide animations if necessary.
        launch {
            snapshotFlow { showIndicator }
                .collectLatest {
                    if (showIndicator) {
                        if (skipFirstAlphaAnimation || !showFadeInAnimation) {
                            alphaAnimatable.snapTo(1f)
                            skipFirstAlphaAnimation = false
                        } else {
                            launch {
                                alphaAnimatable.animateTo(1f, alphaChangeAnimationSpec)
                            }
                        }
                    } else {
                        if (showFadeOutAnimation) {
                            launch {
                                alphaAnimatable.animateTo(0f, alphaChangeAnimationSpec)
                            }
                        } else {
                            alphaAnimatable.snapTo(0f)
                        }
                    }
                }
        }
    }

    BoundsLimiter(boundsOffset, boundsSize, modifier, onSizeChanged = {
        containerSize = it
    }) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = alphaAnimatable.value
                }
                .drawWithCache {
                    // We need to invert reverseDirection when the screen is round and we are on
                    // the left.
                    val actualReverseDirection =
                        if (isScreenRound && !indicatorOnTheRight) {
                            !reverseDirection
                        } else {
                            reverseDirection
                        }

                    val indicatorPosition = if (actualReverseDirection) {
                        1 - positionFractionAnimatable.value
                    } else {
                        positionFractionAnimatable.value
                    }

                    val indicatorWidthPx = indicatorWidth.toPx()

                    // We want position = 0 be the indicator aligned at the top of its area and
                    // position = 1 be aligned at the bottom of the area.
                    val indicatorStart = indicatorPosition * (1 - sizeFractionAnimatable.value)

                    val diameter =
                        max(containerSize.width.toFloat(), containerSize.height.toFloat())

                    val paddingHorizontalPx = paddingHorizontal.toPx()
                    onDrawWithContent {
                        if (isScreenRound) {
                            val usableHalf = diameter / 2f - paddingHorizontalPx
                            val sweepDegrees =
                                (2 * asin((indicatorHeight.toPx() / 2) / usableHalf)).toDegrees()

                            drawCurvedIndicator(
                                color,
                                background,
                                paddingHorizontalPx,
                                indicatorOnTheRight,
                                sweepDegrees,
                                indicatorWidthPx,
                                indicatorStart,
                                sizeFractionAnimatable.value,
                            )
                        } else {
                            drawStraightIndicator(
                                color,
                                background,
                                paddingHorizontalPx,
                                indicatorOnTheRight,
                                indicatorWidthPx,
                                indicatorHeight.toPx(),
                                indicatorStart,
                                sizeFractionAnimatable.value,
                            )
                        }
                    }
                }
        )
    }
}

@Immutable
internal class DisplayState(
    val position: Float,
    val size: Float,
    val visibility: PositionIndicatorVisibility
) {
    override fun hashCode(): Int {
        var result = position.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + visibility.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null) return false
        if (this::class != other::class) return false

        other as DisplayState

        if (position != other.position) return false
        if (size != other.size) return false
        if (visibility != other.visibility) return false

        return true
    }
}

/**
 * An implementation of [PositionIndicatorState] to display a value that is being incremented or
 * decremented with a rolling side button, rotating bezel or a slider e.g. a volume control.
 *
 * @param fraction Value of the indicator in the range 0..1 where 1 represents the
 * maximum value. E.g. If displaying a volume value from 0..11 then the [fraction] will be
 * volume/11.
 *
 * @VisibleForTesting
 */
internal class FractionPositionIndicatorState(
    private val fraction: () -> Float
) : PositionIndicatorState {
    override val positionFraction = 1f // Position indicator always starts at the bottom|end

    override fun sizeFraction(scrollableContainerSizePx: Float) = fraction()

    override fun equals(other: Any?) =
        (other as? FractionPositionIndicatorState)?.fraction?.invoke() == fraction()

    override fun hashCode(): Int = fraction().hashCode()

    override fun visibility(scrollableContainerSizePx: Float) = PositionIndicatorVisibility.Show
}

/**
 * An implementation of [PositionIndicatorState] to display the amount and position of a component
 * implementing the [ScrollState] class such as a [Column] implementing [Modifier.verticalScroll].
 *
 * @param scrollState the [ScrollState] to adapt
 *
 * @VisibleForTesting
 */
internal class ScrollStateAdapter(private val scrollState: ScrollState) : PositionIndicatorState {
    override val positionFraction: Float
        get() {
            return if (scrollState.maxValue == 0) {
                0f
            } else {
                scrollState.value.toFloat() / scrollState.maxValue
            }
        }

    override fun sizeFraction(scrollableContainerSizePx: Float) =
        if (scrollableContainerSizePx + scrollState.maxValue == 0.0f) {
            1.0f
        } else {
            scrollableContainerSizePx / (scrollableContainerSizePx + scrollState.maxValue)
        }

    override fun visibility(scrollableContainerSizePx: Float) = if (scrollState.maxValue == 0) {
        PositionIndicatorVisibility.Hide
    } else if (scrollState.isScrollInProgress)
        PositionIndicatorVisibility.Show
    else
        PositionIndicatorVisibility.AutoHide

    override fun equals(other: Any?): Boolean {
        return (other as? ScrollStateAdapter)?.scrollState == scrollState
    }

    override fun hashCode(): Int {
        return scrollState.hashCode()
    }
}

/**
 * An implementation of [PositionIndicatorState] to display the amount and position of a
 * [ScalingLazyColumn] component via its [ScalingLazyListState].
 *
 * Note that size and position calculations ignore spacing between list items both for determining
 * the number and the number of visible items.

 * @param state the [ScalingLazyListState] to adapt.
 *
 * @VisibleForTesting
 */
internal class ScalingLazyColumnStateAdapter(
    private val state: ScalingLazyListState
) : BaseScalingLazyColumnStateAdapter() {

    override fun noVisibleItems(): Boolean = state.layoutInfo.visibleItemsInfo.isEmpty()

    override fun totalItemsCount(): Int = state.layoutInfo.totalItemsCount

    override fun isScrollInProgress(): Boolean = state.isScrollInProgress

    override fun hashCode(): Int {
        return state.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return (other as? ScalingLazyColumnStateAdapter)?.state == state
    }

    /**
     * Provide a float value that represents the index of the last visible list item in a scaling
     * lazy column. The value should be in the range from [n,n+1] for a given index n, where n is
     * the index of the last visible item and a value of n represents that only the very start|top
     * of the item is visible, and n+1 means that whole of the item is visible in the viewport.
     *
     * Note that decimal index calculations ignore spacing between list items both for determining
     * the number and the number of visible items.
     */
    override fun decimalLastItemIndex(): Float {
        if (state.layoutInfo.visibleItemsInfo.isEmpty()) return 0f
        val lastItem = state.layoutInfo.visibleItemsInfo.last()
        // This is the offset of the last item w.r.t. the ScalingLazyColumn coordinate system where
        // 0 in the center of the visible viewport and +/-(state.viewportHeightPx / 2f) are the
        // start and end of the viewport.
        //
        // Note that [ScalingLazyListAnchorType] determines how the list items are anchored to the
        // center of the viewport, it does not change viewport coordinates. As a result this
        // calculation needs to take the anchorType into account to calculate the correct end
        // of list item offset.
        val lastItemEndOffset = lastItem.startOffset(state.layoutInfo.anchorType) + lastItem.size
        val viewportEndOffset = state.layoutInfo.viewportSize.height / 2f
        // Coerce item size to at least 1 to avoid divide by zero for zero height items
        val lastItemVisibleFraction =
            (1f - ((lastItemEndOffset - viewportEndOffset) /
                lastItem.size.coerceAtLeast(1))).coerceAtMost(1f)

        return lastItem.index.toFloat() + lastItemVisibleFraction
    }

    /**
     * Provide a float value that represents the index of first visible list item in a scaling lazy
     * column. The value should be in the range from [n,n+1] for a given index n, where n is the
     * index of the first visible item and a value of n represents that all of the item is visible
     * in the viewport and a value of n+1 means that only the very end|bottom of the list item is
     * visible at the start|top of the viewport.
     *
     * Note that decimal index calculations ignore spacing between list items both for determining
     * the number and the number of visible items.
     */
    override fun decimalFirstItemIndex(): Float {
        if (state.layoutInfo.visibleItemsInfo.isEmpty()) return 0f
        val firstItem = state.layoutInfo.visibleItemsInfo.first()
        val firstItemStartOffset = firstItem.startOffset(state.layoutInfo.anchorType)
        val viewportStartOffset = -(state.layoutInfo.viewportSize.height / 2f)
        // Coerce item size to at least 1 to avoid divide by zero for zero height items
        val firstItemInvisibleFraction =
            ((viewportStartOffset - firstItemStartOffset) /
                firstItem.size.coerceAtLeast(1)).coerceAtLeast(0f)

        return firstItem.index.toFloat() + firstItemInvisibleFraction
    }
}

/**
 * An implementation of [PositionIndicatorState] to display the amount and position of a
 * [ScalingLazyColumn] component via its [ScalingLazyListState].
 *
 * Note that size and position calculations ignore spacing between list items both for determining
 * the number and the number of visible items.

 * @param state the [ScalingLazyListState] to adapt.
 */
@Deprecated("Use [ScalingLazyColumnStateAdapter] instead")
internal class MaterialScalingLazyColumnStateAdapter(
    @Suppress("DEPRECATION")
    private val state: androidx.wear.compose.material.ScalingLazyListState
) : BaseScalingLazyColumnStateAdapter() {

    override fun noVisibleItems(): Boolean = state.layoutInfo.visibleItemsInfo.isEmpty()

    override fun totalItemsCount(): Int = state.layoutInfo.totalItemsCount

    override fun isScrollInProgress(): Boolean = state.isScrollInProgress

    override fun hashCode(): Int {
        return state.hashCode()
    }

    @Suppress("DEPRECATION")
    override fun equals(other: Any?): Boolean {
        return (other as? MaterialScalingLazyColumnStateAdapter)?.state == state
    }

    /**
     * Provide a float value that represents the index of the last visible list item in a scaling
     * lazy column. The value should be in the range from [n,n+1] for a given index n, where n is
     * the index of the last visible item and a value of n represents that only the very start|top
     * of the item is visible, and n+1 means that whole of the item is visible in the viewport.
     *
     * Note that decimal index calculations ignore spacing between list items both for determining
     * the number and the number of visible items.
     */
    override fun decimalLastItemIndex(): Float {
        if (state.layoutInfo.visibleItemsInfo.isEmpty()) return 0f
        val lastItem = state.layoutInfo.visibleItemsInfo.last()
        // This is the offset of the last item w.r.t. the ScalingLazyColumn coordinate system where
        // 0 in the center of the visible viewport and +/-(state.viewportHeightPx / 2f) are the
        // start and end of the viewport.
        //
        // Note that [ScalingLazyListAnchorType] determines how the list items are anchored to the
        // center of the viewport, it does not change viewport coordinates. As a result this
        // calculation needs to take the anchorType into account to calculate the correct end
        // of list item offset.
        val lastItemEndOffset = lastItem.startOffset(state.anchorType.value!!) + lastItem.size
        val viewportEndOffset = state.viewportHeightPx.value!! / 2f
        // Coerce item size to at least 1 to avoid divide by zero for zero height items
        val lastItemVisibleFraction =
            (1f - ((lastItemEndOffset - viewportEndOffset) /
                lastItem.size.coerceAtLeast(1))).coerceAtMost(1f)

        return lastItem.index.toFloat() + lastItemVisibleFraction
    }

    /**
     * Provide a float value that represents the index of first visible list item in a scaling lazy
     * column. The value should be in the range from [n,n+1] for a given index n, where n is the
     * index of the first visible item and a value of n represents that all of the item is visible
     * in the viewport and a value of n+1 means that only the very end|bottom of the list item is
     * visible at the start|top of the viewport.
     *
     * Note that decimal index calculations ignore spacing between list items both for determining
     * the number and the number of visible items.
     */
    override fun decimalFirstItemIndex(): Float {
        if (state.layoutInfo.visibleItemsInfo.isEmpty()) return 0f
        val firstItem = state.layoutInfo.visibleItemsInfo.first()
        val firstItemStartOffset = firstItem.startOffset(state.anchorType.value!!)
        val viewportStartOffset = -(state.viewportHeightPx.value!! / 2f)
        // Coerce item size to at least 1 to avoid divide by zero for zero height items
        val firstItemInvisibleFraction =
            ((viewportStartOffset - firstItemStartOffset) /
                firstItem.size.coerceAtLeast(1)).coerceAtLeast(0f)

        return firstItem.index.toFloat() + firstItemInvisibleFraction
    }
}

internal abstract class BaseScalingLazyColumnStateAdapter : PositionIndicatorState {
    override val positionFraction: Float
        get() {
            return if (noVisibleItems()) {
                0.0f
            } else {
                val decimalFirstItemIndex = decimalFirstItemIndex()
                val decimalLastItemIndex = decimalLastItemIndex()
                val decimalLastItemIndexDistanceFromEnd = totalItemsCount() -
                    decimalLastItemIndex

                if (decimalFirstItemIndex + decimalLastItemIndexDistanceFromEnd == 0.0f) {
                    0.0f
                } else {
                    decimalFirstItemIndex /
                        (decimalFirstItemIndex + decimalLastItemIndexDistanceFromEnd)
                }
            }
        }

    override fun sizeFraction(scrollableContainerSizePx: Float) =
        if (totalItemsCount() == 0) {
            1.0f
        } else {
            val decimalFirstItemIndex = decimalFirstItemIndex()
            val decimalLastItemIndex = decimalLastItemIndex()

            (decimalLastItemIndex - decimalFirstItemIndex) /
                totalItemsCount().toFloat()
        }

    override fun visibility(scrollableContainerSizePx: Float): PositionIndicatorVisibility {
        val canScroll = !noVisibleItems() &&
            (decimalFirstItemIndex() > 0 ||
                decimalLastItemIndex() < totalItemsCount())
        return if (canScroll) {
            if (isScrollInProgress())
                PositionIndicatorVisibility.Show
            else
                PositionIndicatorVisibility.AutoHide
        } else {
            PositionIndicatorVisibility.Hide
        }
    }

    abstract fun noVisibleItems(): Boolean

    abstract fun totalItemsCount(): Int

    abstract fun isScrollInProgress(): Boolean

    abstract fun decimalLastItemIndex(): Float

    abstract fun decimalFirstItemIndex(): Float
}

/**
 * An implementation of [PositionIndicatorState] to display the amount and position of a
 * [LazyColumn] component via its [LazyListState].
 *
 * @param state the [LazyListState] to adapt.
 *
 * @VisibleForTesting
 */
internal class LazyColumnStateAdapter(
    private val state: LazyListState
) : PositionIndicatorState {
    override val positionFraction: Float
        get() {
            return if (state.layoutInfo.visibleItemsInfo.isEmpty()) {
                0.0f
            } else {
                val decimalFirstItemIndex = decimalFirstItemIndex()
                val decimalLastItemIndex = decimalLastItemIndex()

                val decimalLastItemIndexDistanceFromEnd = state.layoutInfo.totalItemsCount -
                    decimalLastItemIndex

                if (decimalFirstItemIndex + decimalLastItemIndexDistanceFromEnd == 0.0f) {
                    0.0f
                } else {
                    decimalFirstItemIndex /
                        (decimalFirstItemIndex + decimalLastItemIndexDistanceFromEnd)
                }
            }
        }

    override fun sizeFraction(scrollableContainerSizePx: Float) =
        if (state.layoutInfo.totalItemsCount == 0) {
            1.0f
        } else {
            val decimalFirstItemIndex = decimalFirstItemIndex()
            val decimalLastItemIndex = decimalLastItemIndex()

            (decimalLastItemIndex - decimalFirstItemIndex) /
                state.layoutInfo.totalItemsCount.toFloat()
        }

    override fun visibility(scrollableContainerSizePx: Float): PositionIndicatorVisibility {
        return if (sizeFraction(scrollableContainerSizePx) < 0.999f) {
            if (state.isScrollInProgress)
                PositionIndicatorVisibility.Show
            else
                PositionIndicatorVisibility.AutoHide
        } else {
            PositionIndicatorVisibility.Hide
        }
    }

    override fun hashCode(): Int {
        return state.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        return (other as? LazyColumnStateAdapter)?.state == state
    }

    private fun decimalLastItemIndex(): Float {
        if (state.layoutInfo.visibleItemsInfo.isEmpty()) return 0f
        val lastItem = state.layoutInfo.visibleItemsInfo.last()
        // Coerce item sizes to at least 1 to avoid divide by zero for zero height items
        val lastItemVisibleSize =
            (state.layoutInfo.viewportEndOffset - lastItem.offset)
                .coerceAtMost(lastItem.size).coerceAtLeast(1)
        return lastItem.index.toFloat() +
            lastItemVisibleSize.toFloat() / lastItem.size.coerceAtLeast(1).toFloat()
    }

    private fun decimalFirstItemIndex(): Float {
        if (state.layoutInfo.visibleItemsInfo.isEmpty()) return 0f
        val firstItem = state.layoutInfo.visibleItemsInfo.first()
        val firstItemOffset = firstItem.offset - state.layoutInfo.viewportStartOffset
        // Coerce item size to at least 1 to avoid divide by zero for zero height items
        return firstItem.index.toFloat() -
            firstItemOffset.coerceAtMost(0).toFloat() /
            firstItem.size.coerceAtLeast(1).toFloat()
    }
}

// TODO(ssancho): implement min/max thumb size (1/10 & 9/10)
private fun ContentDrawScope.drawCurvedIndicator(
    color: Color,
    background: Color,
    paddingHorizontalPx: Float,
    indicatorOnTheRight: Boolean,
    sweepDegrees: Float,
    indicatorWidthPx: Float,
    indicatorStart: Float,
    indicatorSize: Float
) {
    val diameter = max(size.width, size.height)
    val arcSize = Size(
        diameter - 2 * paddingHorizontalPx - indicatorWidthPx,
        diameter - 2 * paddingHorizontalPx - indicatorWidthPx
    )
    val arcTopLeft = Offset(
        size.width - diameter + paddingHorizontalPx + indicatorWidthPx / 2f,
        (size.height - diameter) / 2f + paddingHorizontalPx + indicatorWidthPx / 2f,
    )
    val startAngleOffset = if (indicatorOnTheRight) 0f else 180f
    drawArc(
        background,
        startAngle = startAngleOffset - sweepDegrees / 2,
        sweepDegrees,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = indicatorWidthPx, cap = StrokeCap.Round)
    )
    drawArc(
        color = color,
        startAngle = startAngleOffset + sweepDegrees * (-0.5f + indicatorStart),
        sweepAngle = sweepDegrees * indicatorSize,
        useCenter = false,
        topLeft = arcTopLeft,
        size = arcSize,
        style = Stroke(width = indicatorWidthPx, cap = StrokeCap.Round)
    )
}

private fun ContentDrawScope.drawStraightIndicator(
    color: Color,
    background: Color,
    paddingHorizontalPx: Float,
    indicatorOnTheRight: Boolean,
    indicatorWidthPx: Float,
    indicatorHeightPx: Float,
    indicatorStart: Float,
    indicatorSize: Float
) {
    val x = if (indicatorOnTheRight) {
        size.width - paddingHorizontalPx - indicatorWidthPx / 2
    } else {
        paddingHorizontalPx + indicatorWidthPx / 2
    }
    val lineTop = Offset(x, (size.height - indicatorHeightPx) / 2f)
    val lineBottom = lineTop + Offset(0f, indicatorHeightPx)
    drawLine(
        color = background,
        lineTop,
        lineBottom,
        strokeWidth = indicatorWidthPx,
        cap = StrokeCap.Round
    )
    drawLine(
        color,
        lerp(lineTop, lineBottom, indicatorStart),
        lerp(lineTop, lineBottom, indicatorStart + indicatorSize),
        strokeWidth = indicatorWidthPx,
        cap = StrokeCap.Round
    )
}

internal fun Float.toDegrees() = this * 180f / PI.toFloat()

// Make the content believe it's using the full dimensions of the parent, but limit it
// to the given bounds. This is used to limit the space used on screen for "full-screen" components
// like PositionIndicator, so it doesn't interfere with a11y on the whole screen.
@Composable
private fun BoundsLimiter(
    offset: Density.() -> IntOffset,
    size: Density.() -> IntSize,
    modifier: Modifier = Modifier,
    onSizeChanged: (IntSize) -> Unit = { },
    content: @Composable BoxScope.() -> Unit
) = Box(
    modifier = Modifier
        .fillMaxSize()
        .onSizeChanged(onSizeChanged)
        .absoluteOffset(offset),
    // We handle layout direction the main PositionIndicator function, according to the position
    // parameter.
    contentAlignment = AbsoluteAlignment.TopLeft
) {
    // This Box has the position and size we need, so any modifiers passed in should be applied
    // here. We set the size using a custom modifier (that passes the constraints transparently to
    // the content), and add a negative offset to make the content believe is drawing at the top
    // left (position 0, 0).
    Box(
        modifier
            .transparentSizeModifier(size)
            .absoluteOffset { -offset() }, content = content,
        contentAlignment = AbsoluteAlignment.TopLeft
    )
}

// Sets the size of this element, but lets the child measure using the constraints
// of the element containing this.
private fun Modifier.transparentSizeModifier(size: Density.() -> IntSize): Modifier = this.then(
    object : LayoutModifier {
        override fun MeasureScope.measure(
            measurable: Measurable,
            constraints: Constraints
        ): MeasureResult {
            val placeable = measurable.measure(constraints)
            val actualSize = size()
            return layout(actualSize.width, actualSize.height) {
                placeable.place(0, 0)
            }
        }
    }
)

private fun sqr(x: Float) = x * x

/**
 * Find the start offset of the list item w.r.t. the
 */
internal fun ScalingLazyListItemInfo.startOffset(anchorType: ScalingLazyListAnchorType) =
    offset - if (anchorType == ScalingLazyListAnchorType.ItemCenter) {
        (size / 2f)
    } else {
        0f
    }

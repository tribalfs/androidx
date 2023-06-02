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

package androidx.wear.compose.foundation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.absoluteOffset
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Standard animation length in milliseconds.
 */
internal const val STANDARD_ANIMATION = 300

/**
 * Quick animation length in milliseconds.
 */
internal const val QUICK_ANIMATION = 250

/**
 * Different values which the swipeable modifier can be configured to.
 */
@ExperimentalWearFoundationApi
@JvmInline
public value class RevealValue private constructor(val value: Int) {
    companion object {
        /**
         * The default first value which generally represents the state where the revealable
         * actions has not been revealed yet.
         */
        val Covered = RevealValue(0)

        /**
         * The value which represents the state in which all the actions are revealed and the
         * top content is stable.
         */
        val Revealing = RevealValue(1)

        /**
         * The value which represents the state in which the whole revealable content is fully
         * revealed.
         */
        val Revealed = RevealValue(2)
    }
}

/**
 * Creates the required anchors to which the top content can be swiped, to reveal the actions.
 * Each value should be in the range [0..1], where 0 represents right most end and 1 represents the
 * full width of the top content starting from right and ending on left.
 *
 * @param coveredAnchor Anchor for the [RevealValue.Covered] value
 * @param revealingAnchor Anchor for the [RevealValue.Revealing] value
 * @param revealedAnchor Anchor for the [RevealValue.Revealed] value
 */
@ExperimentalWearFoundationApi
public fun createAnchors(
    coveredAnchor: Float = 0f,
    revealingAnchor: Float = 0.7f,
    revealedAnchor: Float = 1f
): Map<RevealValue, Float> {
    return mapOf(
        RevealValue.Covered to coveredAnchor,
        RevealValue.Revealing to revealingAnchor,
        RevealValue.Revealed to revealedAnchor
    )
}

/**
 * A class to keep track of the state of the composable. It can be used to customise
 * the behaviour and state of the composable.
 *
 * @constructor Create a [RevealState].
 */
@ExperimentalWearFoundationApi
public class RevealState internal constructor(
    initialValue: RevealValue,
    animationSpec: AnimationSpec<Float>,
    @Suppress("PrimitiveInLambda")
    confirmValueChange: (RevealValue) -> Boolean,
    @Suppress("PrimitiveInLambda")
    positionalThreshold: Density.(totalDistance: Float) -> Float,
    internal val anchors: Map<RevealValue, Float>
) {
    /**
     * [SwipeableV2State] internal instance for the state.
     */
    internal val swipeableState = SwipeableV2State(
        initialValue = initialValue,
        animationSpec = animationSpec,
        confirmValueChange = confirmValueChange,
        positionalThreshold = positionalThreshold
    )

    /**
     * The current [RevealValue] based on the status of the component.
     *
     * @see Modifier.swipeableV2
     */
    public val currentValue: RevealValue
        get() = swipeableState.currentValue

    /**
     * The target [RevealValue] based on the status of the component. This will be equal to
     * the [currentValue] if there is no animation running or swiping has stopped. Otherwise, this
     * returns the next [RevealValue] based on the animation/swipe direction.
     *
     * @see Modifier.swipeableV2
     */
    public val targetValue: RevealValue
        get() = swipeableState.targetValue

    /**
     * Returns whether the animation is running or not.
     *
     * @see Modifier.swipeableV2
     */
    public val isAnimationRunning: Boolean
        get() = swipeableState.isAnimationRunning

    /**
     * The current amount by which the revealable content has been revealed by.
     *
     * @see Modifier.swipeableV2
     */
    public val offset: Float
        get() = swipeableState.offset ?: 0f

    /**
     * Defines the anchors for revealable content. These anchors are used to determine
     * the width at which the revealable content can be revealed to and stopped without requiring
     * any input from the user.
     *
     * @see Modifier.swipeableV2
     */
    public val swipeAnchors: Map<RevealValue, Float>
        get() = anchors

    /**
     * Snaps to the [targetValue] without any animation.
     *
     * @param targetValue The target [RevealValue] where the [currentValue] will be changed
     * to.
     * @see Modifier.swipeableV2
     */
    public suspend fun snapTo(targetValue: RevealValue) = swipeableState.snapTo(targetValue)

    /**
     * Animates to the [targetValue] with the animation spec provided.
     *
     * @param targetValue The target [RevealValue] where the [currentValue] will animation
     * to.
     */
    public suspend fun animateTo(targetValue: RevealValue) =
        swipeableState.animateTo(targetValue)

    /**
     * Require the current offset.
     *
     * @throws IllegalStateException If the offset has not been initialized yet
     */
    internal fun requireOffset(): Float = swipeableState.requireOffset()
}

/**
 * Create and [remember] a [RevealState].
 *
 * @param initialValue The initial value of the [RevealValue].
 * @param animationSpec The animation which will be applied on the top content.
 * @param confirmValueChange Optional callback invoked to confirm or veto a pending state change.
 * @param positionalThreshold The positional threshold to be used when calculating the target state
 * while the reveal is in progress and when settling after the revealing ends. This is the distance
 * from the start of a transition. It will be, depending on the direction of the interaction, added
 * or subtracted from/to the origin offset. It should always be a positive value.
 * @param anchors A map of [RevealValue] to the fraction where the content can be revealed to
 * reach that value. Each anchor should be between [0..1] which will be adjusted based on total
 * width.
 */
@ExperimentalWearFoundationApi
@Composable
public fun rememberRevealState(
    initialValue: RevealValue = RevealValue.Covered,
    animationSpec: AnimationSpec<Float> = SwipeToRevealDefaults.animationSpec,
    @Suppress("PrimitiveInLambda")
    confirmValueChange: (RevealValue) -> Boolean = { true },
    @Suppress("PrimitiveInLambda")
    positionalThreshold: Density.(totalDistance: Float) -> Float =
        SwipeToRevealDefaults.defaultThreshold(),
    anchors: Map<RevealValue, Float> = createAnchors()
): RevealState {
    return remember(initialValue, animationSpec) {
        RevealState(
            initialValue = initialValue,
            animationSpec = animationSpec,
            confirmValueChange = confirmValueChange,
            positionalThreshold = positionalThreshold,
            anchors = anchors
        )
    }
}

/**
 * A composable that be used to add extra actions to a composable (up to two) which will be
 * revealed when the original composable is swiped to the left. This composable mandates
 * at least a single extra action, with an optional action along with mandatory one.
 *
 * When the composable reaches to the state where all the actions are revealed and the swiping
 * continues beyond the positional threshold defined in [RevealState], the mandatory action gets
 * triggered automatically.
 *
 * An optional undo action can also be added when the actions are triggered. This undo action will
 * be visible to users once the [RevealValue] becomes [RevealValue.Revealed].
 *
 * It is strongly recommended to have icons represent the actions and maybe a text and icon for
 * the undo action.
 *
 * Example of SwipeToReveal with mandatory action and undo action
 * @sample androidx.wear.compose.foundation.samples.SwipeToRevealSample
 *
 * Example of SwipeToReveal using [RevealScope]
 * @sample androidx.wear.compose.foundation.samples.SwipeToRevealWithRevealOffset
 *
 * Example of SwipeToReveal used with Expandables
 * @sample androidx.wear.compose.foundation.samples.SwipeToRevealWithExpandables
 *
 * @param action The mandatory action that needs to be added to the component.
 * @param modifier Optional [Modifier] for this component.
 * @param state The [RevealState] of this component. It can be used to customise the anchors
 * and threshold config of the swipeable modifier which is applied.
 * @param onFullSwipe An optional lambda which will be triggered when a full swipe from either of
 * the anchors is performed.
 * @param additionalAction The optional action that can be added to the component.
 * @param undoAction The optional undo action that will be applied to the component once the
 * mandatory action has been performed.
 * @param content The content that will be initially displayed over the other actions provided.
 */
@ExperimentalWearFoundationApi
@Composable
public fun SwipeToReveal(
    action: @Composable RevealScope.() -> Unit,
    modifier: Modifier = Modifier,
    onFullSwipe: () -> Unit = {},
    state: RevealState = rememberRevealState(),
    additionalAction: (@Composable RevealScope.() -> Unit)? = null,
    undoAction: (@Composable RevealScope.() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val revealScope = remember(state) { RevealScopeImpl(state) }
    Box(
        modifier = modifier
            .swipeableV2(
                state = state.swipeableState,
                orientation = Orientation.Horizontal,
                enabled = state.currentValue != RevealValue.Revealed,
            )
            .swipeAnchors(
                state = state.swipeableState,
                possibleValues = state.swipeAnchors.keys
            ) { value, layoutSize ->
                val swipeableWidth = layoutSize.width.toFloat()
                // Update the total width which will be used to calculate the anchors
                revealScope.width.value = swipeableWidth
                // Multiply the anchor with -1f to get the actual swipeable anchor
                -state.swipeAnchors[value]!! * swipeableWidth
            }
    ) {
        val swipeCompleted by remember {
            derivedStateOf { state.currentValue == RevealValue.Revealed }
        }
        val density = LocalDensity.current

        // Total width available for the slot(s) based on the current swipe offset
        val availableWidth = if (state.offset.isNaN()) 0.dp
        else with(density) { abs(state.offset).toDp() }

        // Determines whether the additional action will be visible based on the current
        // reveal offset
        val showAdditionalAction by remember {
            derivedStateOf {
                abs(state.offset) <= revealScope.revealOffset
            }
        }
        // Animate weight for additional action slot.
        val additionalActionWeight = animateFloatAsState(
            targetValue = if (showAdditionalAction) 1f else 0f,
            animationSpec = tween(durationMillis = QUICK_ANIMATION),
            label = "AdditionalActionAnimationSpec"
        )

        Row(
            modifier = Modifier.matchParentSize(),
            horizontalArrangement = Arrangement.Absolute.Right
        ) {
            Crossfade(
                targetState = swipeCompleted && undoAction != null,
                animationSpec = tween(durationMillis = STANDARD_ANIMATION),
                label = "CrossFadeS2R"
            ) { displayUndo ->
                if (displayUndo) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        ActionSlot(revealScope, content = undoAction!!)
                    }
                } else {
                    Row(
                        modifier = Modifier.width(availableWidth),
                        horizontalArrangement = Arrangement.Absolute.Right
                    ) {
                        // weight cannot be 0 so remove the composable when weight becomes 0
                        if (additionalAction != null && additionalActionWeight.value > 0) {
                            Spacer(Modifier.size(SwipeToRevealDefaults.padding))
                            ActionSlot(
                                revealScope,
                                content = additionalAction,
                                weight = additionalActionWeight.value
                            )
                        }
                        Spacer(Modifier.size(SwipeToRevealDefaults.padding))
                        ActionSlot(revealScope, content = action)
                    }
                }
            }
        }
        Row(
            modifier = Modifier.absoluteOffset {
                IntOffset(
                    x = state.requireOffset().roundToInt().coerceAtMost(0),
                    y = 0
                )
            }
        ) {
            content()
        }
        LaunchedEffect(state.currentValue) {
            if (state.currentValue == RevealValue.Revealed) {
                onFullSwipe()
            }
        }
    }
}

@ExperimentalWearFoundationApi
public interface RevealScope {

    /**
     * The offset, in pixels, where the revealed actions are fully visible but the existing content
     * would be left in place if the reveal action was stopped. This offset is used to create the
     * anchor for [RevealValue.Revealing].
     * If there is no such anchor defined for [RevealValue.Revealing], it returns 0.0f.
     */
    public val revealOffset: Float
}

@OptIn(ExperimentalWearFoundationApi::class)
private class RevealScopeImpl constructor(
    private val revealState: RevealState
) : RevealScope {

    /**
     * The total width of the overlay content in pixels. Initialise to zero,
     * updated when the width changes.
     */
    val width = mutableFloatStateOf(0.0f)

    override val revealOffset: Float
        get() = width.value * (revealState.swipeAnchors[RevealValue.Revealing] ?: 0.0f)
}

/**
 * An internal object containing some defaults used across the Swipe to reveal component.
 */
@OptIn(ExperimentalWearFoundationApi::class)
internal object SwipeToRevealDefaults {

    internal val animationSpec = SwipeableV2Defaults.AnimationSpec

    internal val padding = 2.dp

    internal const val threshold = 0.5f

    @Suppress("PrimitiveInLambda")
    internal fun defaultThreshold() = fractionalPositionalThreshold(threshold)
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
private fun RowScope.ActionSlot(
    revealScope: RevealScope,
    modifier: Modifier = Modifier,
    weight: Float = 1f,
    content: @Composable RevealScope.() -> Unit
) {
    Box(
        modifier = modifier.fillMaxHeight().weight(weight),
        contentAlignment = Alignment.Center
    ) {
        with(revealScope) {
            content()
        }
    }
}
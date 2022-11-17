/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.foundation.gestures.snapping

import androidx.compose.animation.core.AnimationScope
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.calculateTargetValue
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.DefaultScrollMotionDurationScale
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.absoluteValue
import kotlin.math.sign
import kotlinx.coroutines.withContext

/**
 * A [FlingBehavior] that performs snapping of items to a given position. The algorithm will
 * differentiate between short/scroll snapping and long/fling snapping.
 *
 * Use [shortSnapVelocityThreshold] to provide a threshold velocity that will appropriately select
 * the desired behavior.
 *
 * A short snap usually happens after a fling with low velocity.
 *
 * When long snapping, you can use [SnapLayoutInfoProvider.calculateApproachOffset] to
 * indicate that snapping should happen after this offset. If the velocity generated by the
 * fling is high enough to get there, we'll use [highVelocityAnimationSpec] to get to that offset
 * and then we'll snap to the next bound calculated by
 * [SnapLayoutInfoProvider.calculateSnappingOffsetBounds] in the direction of the fling using
 * [snapAnimationSpec].
 *
 * If the velocity is not high enough, we'll use [lowVelocityAnimationSpec] to approach and then
 * use [snapAnimationSpec] to snap into place.
 *
 * Please refer to the sample to learn how to use this API.
 * @sample androidx.compose.foundation.samples.SnapFlingBehaviorSimpleSample
 * @sample androidx.compose.foundation.samples.SnapFlingBehaviorCustomizedSample
 *
 * @param snapLayoutInfoProvider The information about the layout being snapped.
 * @param lowVelocityAnimationSpec The animation spec used to approach the target offset. When
 * the fling velocity is not large enough. Large enough means large enough to naturally decay.
 * @param highVelocityAnimationSpec The animation spec used to approach the target offset. When
 * the fling velocity is large enough. Large enough means large enough to naturally decay.
 * @param snapAnimationSpec The animation spec used to finally snap to the correct bound.
 * @param density The screen [Density]
 * @param shortSnapVelocityThreshold Use the given velocity to determine if it's a
 * short or long snap.
 *
 */
@ExperimentalFoundationApi
class SnapFlingBehavior(
    private val snapLayoutInfoProvider: SnapLayoutInfoProvider,
    private val lowVelocityAnimationSpec: AnimationSpec<Float>,
    private val highVelocityAnimationSpec: DecayAnimationSpec<Float>,
    private val snapAnimationSpec: AnimationSpec<Float>,
    private val density: Density,
    private val shortSnapVelocityThreshold: Dp = MinFlingVelocityDp
) : FlingBehavior {

    private val velocityThreshold = with(density) { shortSnapVelocityThreshold.toPx() }
    internal var motionScaleDuration = DefaultScrollMotionDurationScale

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        // If snapping from scroll (short snap) or fling (long snap)
        withContext(motionScaleDuration) {
            if (abs(initialVelocity) <= abs(velocityThreshold)) {
                shortSnap(initialVelocity)
            } else {
                longSnap(initialVelocity)
            }
        }
        return NoVelocity
    }

    private suspend fun ScrollScope.shortSnap(velocity: Float) {
        debugLog { "Short Snapping" }
        val closestOffset = findClosestOffset(0f, snapLayoutInfoProvider, density)
        val animationState = AnimationState(NoDistance, velocity)
        animateSnap(closestOffset, closestOffset, animationState, snapAnimationSpec)
    }

    private suspend fun ScrollScope.longSnap(initialVelocity: Float) {
        debugLog { "Long Snapping" }
        val initialOffset =
            with(snapLayoutInfoProvider) { density.calculateApproachOffset(initialVelocity) }.let {
                abs(it) * sign(initialVelocity) // ensure offset sign is correct
            }

        val (remainingOffset, animationState) = runApproach(initialOffset, initialVelocity)

        debugLog { "Settling Final Bound=$remainingOffset" }

        animateSnap(
            remainingOffset,
            remainingOffset,
            animationState.copy(value = 0f),
            snapAnimationSpec
        )
    }

    private suspend fun ScrollScope.runApproach(
        initialTargetOffset: Float,
        initialVelocity: Float
    ): ApproachStepResult {

        val animation =
            if (isDecayApproachPossible(offset = initialTargetOffset, velocity = initialVelocity)) {
                debugLog { "High Velocity Approach" }
                HighVelocityApproachAnimation(highVelocityAnimationSpec)
            } else {
                debugLog { "Low Velocity Approach" }
                LowVelocityApproachAnimation(
                    lowVelocityAnimationSpec,
                    snapLayoutInfoProvider,
                    density
                )
            }

        return approach(
            initialTargetOffset,
            initialVelocity,
            animation,
            snapLayoutInfoProvider,
            density
        )
    }

    /**
     * If we can approach the target and still have velocity left
     */
    private fun isDecayApproachPossible(
        offset: Float,
        velocity: Float
    ): Boolean {
        val decayOffset = highVelocityAnimationSpec.calculateTargetValue(NoDistance, velocity)
        val snapStepSize = with(snapLayoutInfoProvider) { density.calculateSnapStepSize() }
        return decayOffset.absoluteValue >= (offset.absoluteValue + snapStepSize)
    }

    override fun equals(other: Any?): Boolean {
        return if (other is SnapFlingBehavior) {
            other.snapAnimationSpec == this.snapAnimationSpec &&
                other.highVelocityAnimationSpec == this.highVelocityAnimationSpec &&
                other.lowVelocityAnimationSpec == this.lowVelocityAnimationSpec &&
                other.snapLayoutInfoProvider == this.snapLayoutInfoProvider &&
                other.density == this.density &&
                other.shortSnapVelocityThreshold == this.shortSnapVelocityThreshold
        } else {
            false
        }
    }

    override fun hashCode(): Int = 0
        .let { 31 * it + snapAnimationSpec.hashCode() }
        .let { 31 * it + highVelocityAnimationSpec.hashCode() }
        .let { 31 * it + lowVelocityAnimationSpec.hashCode() }
        .let { 31 * it + snapLayoutInfoProvider.hashCode() }
        .let { 31 * it + density.hashCode() }
        .let { 31 * it + shortSnapVelocityThreshold.hashCode() }
}

/**
 * Creates and remember a [FlingBehavior] that performs snapping.
 * @param snapLayoutInfoProvider The information about the layout that will do snapping
 */
@ExperimentalFoundationApi
@Composable
fun rememberSnapFlingBehavior(
    snapLayoutInfoProvider: SnapLayoutInfoProvider
): SnapFlingBehavior {
    val density = LocalDensity.current
    val highVelocityApproachSpec: DecayAnimationSpec<Float> = rememberSplineBasedDecay()
    return remember(
        snapLayoutInfoProvider,
        highVelocityApproachSpec,
        density
    ) {
        SnapFlingBehavior(
            snapLayoutInfoProvider = snapLayoutInfoProvider,
            lowVelocityAnimationSpec = tween(easing = LinearEasing),
            highVelocityAnimationSpec = highVelocityApproachSpec,
            snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            density = density
        )
    }
}

/**
 * To ensure we do not overshoot, the approach animation is divided into 2 parts.
 *
 * In the initial animation we animate up until targetOffset. At this point we will have fulfilled
 * the requirement of [SnapLayoutInfoProvider.calculateApproachOffset] and we should snap to the
 * next [SnapLayoutInfoProvider.calculateSnappingOffsetBounds]. We use [findClosestOffset] to find
 * the next offset in the direction of the fling (for large enough velocities).
 *
 * The second part of the approach is a UX improvement. If the target offset is too far (in here, we
 * define too far as over half a step offset away) we continue the approach animation a bit further
 * and leave the remainder to be snapped.
 */
@OptIn(ExperimentalFoundationApi::class)
private suspend fun ScrollScope.approach(
    initialTargetOffset: Float,
    initialVelocity: Float,
    animation: ApproachAnimation<Float, AnimationVector1D>,
    snapLayoutInfoProvider: SnapLayoutInfoProvider,
    density: Density
): ApproachStepResult {

    val currentAnimationState =
        animation.approachAnimation(this, initialTargetOffset, initialVelocity)

    val remainingOffset =
        findClosestOffset(currentAnimationState.velocity, snapLayoutInfoProvider, density)

    // will snap the remainder
    return ApproachStepResult(remainingOffset, currentAnimationState)
}

private data class ApproachStepResult(
    val remainingOffset: Float,
    val currentAnimationState: AnimationState<Float, AnimationVector1D>
)

/**
 * Finds the closest offset to snap to given the Fling Direction.
 *
 * If velocity == 0 this means we'll return the smallest absolute
 * [SnapLayoutInfoProvider.calculateSnappingOffsetBounds].
 *
 * If either 1 or -1 it means we'll snap to either
 * [SnapLayoutInfoProvider.calculateSnappingOffsetBounds] upper or lower bounds respectively.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun findClosestOffset(
    velocity: Float,
    snapLayoutInfoProvider: SnapLayoutInfoProvider,
    density: Density
): Float {

    fun Float.isValidDistance(): Boolean {
        return this != Float.POSITIVE_INFINITY && this != Float.NEGATIVE_INFINITY
    }
    val (lowerBound, upperBound) = with(snapLayoutInfoProvider) {
        density.calculateSnappingOffsetBounds()
    }

    val finalDistance = when (sign(velocity)) {
        0f -> {
            if (abs(upperBound) <= abs(lowerBound)) {
                upperBound
            } else {
                lowerBound
            }
        }

        1f -> upperBound
        -1f -> lowerBound
        else -> NoDistance
    }

    return if (finalDistance.isValidDistance()) {
        finalDistance
    } else {
        NoDistance
    }
}

private operator fun <T : Comparable<T>> ClosedFloatingPointRange<T>.component1(): T = this.start
private operator fun <T : Comparable<T>> ClosedFloatingPointRange<T>.component2(): T =
    this.endInclusive

/**
 * Run a [DecayAnimationSpec] animation up to before [targetOffset] using [animationState]
 */
private suspend fun ScrollScope.animateDecay(
    targetOffset: Float,
    animationState: AnimationState<Float, AnimationVector1D>,
    decayAnimationSpec: DecayAnimationSpec<Float>
): AnimationState<Float, AnimationVector1D> {
    var previousValue = 0f

    fun AnimationScope<Float, AnimationVector1D>.consumeDelta(delta: Float) {
        val consumed = scrollBy(delta)
        if (abs(delta - consumed) > 0.5f) cancelAnimation()
    }

    animationState.animateDecay(
        decayAnimationSpec,
        sequentialAnimation = animationState.velocity != 0f
    ) {
        if (abs(value) >= abs(targetOffset)) {
            val finalValue = value.coerceToTarget(targetOffset)
            val finalDelta = finalValue - previousValue
            consumeDelta(finalDelta)
            cancelAnimation()
        } else {
            val delta = value - previousValue
            consumeDelta(delta)
            previousValue = value
        }
    }
    return animationState
}

/**
 * Runs a [AnimationSpec] to snap the list into [targetOffset]. Uses [cancelOffset] to stop this
 * animation before it reaches the target.
 */
private suspend fun ScrollScope.animateSnap(
    targetOffset: Float,
    cancelOffset: Float,
    animationState: AnimationState<Float, AnimationVector1D>,
    snapAnimationSpec: AnimationSpec<Float>
): AnimationState<Float, AnimationVector1D> {
    var consumedUpToNow = 0f
    val initialVelocity = animationState.velocity
    animationState.animateTo(
        targetOffset,
        animationSpec = snapAnimationSpec,
        sequentialAnimation = (animationState.velocity != 0f)
    ) {
        val realValue = value.coerceToTarget(cancelOffset)
        val delta = realValue - consumedUpToNow
        val consumed = scrollBy(delta)
        // stop when unconsumed or when we reach the desired value
        if (abs(delta - consumed) > 0.5f || realValue != value) {
            cancelAnimation()
        }
        consumedUpToNow += delta
    }

    // Always course correct velocity so they don't become too large.
    val finalVelocity = animationState.velocity.coerceToTarget(initialVelocity)
    return animationState.copy(velocity = finalVelocity)
}

private fun Float.coerceToTarget(target: Float): Float {
    if (target == 0f) return 0f
    return if (target > 0) coerceAtMost(target) else coerceAtLeast(target)
}

/**
 * The animations used to approach offset and approach half a step offset.
 */
private interface ApproachAnimation<T, V : AnimationVector> {
    suspend fun approachAnimation(
        scope: ScrollScope,
        offset: T,
        velocity: T
    ): AnimationState<T, V>
}

@OptIn(ExperimentalFoundationApi::class)
private class LowVelocityApproachAnimation(
    private val lowVelocityAnimationSpec: AnimationSpec<Float>,
    private val layoutInfoProvider: SnapLayoutInfoProvider,
    private val density: Density
) : ApproachAnimation<Float, AnimationVector1D> {
    override suspend fun approachAnimation(
        scope: ScrollScope,
        offset: Float,
        velocity: Float
    ): AnimationState<Float, AnimationVector1D> {
        val animationState = AnimationState(initialValue = 0f, initialVelocity = velocity)
        val targetOffset =
            (abs(offset) + with(layoutInfoProvider) { density.calculateSnapStepSize() }) * sign(
                velocity
            )
        return with(scope) {
            animateSnap(
                targetOffset = targetOffset,
                cancelOffset = offset,
                animationState = animationState,
                snapAnimationSpec = lowVelocityAnimationSpec
            )
        }
    }
}

private class HighVelocityApproachAnimation(
    private val decayAnimationSpec: DecayAnimationSpec<Float>
) : ApproachAnimation<Float, AnimationVector1D> {
    override suspend fun approachAnimation(
        scope: ScrollScope,
        offset: Float,
        velocity: Float
    ): AnimationState<Float, AnimationVector1D> {
        val animationState = AnimationState(initialValue = 0f, initialVelocity = velocity)
        return with(scope) {
            animateDecay(offset, animationState, decayAnimationSpec)
        }
    }
}

internal val MinFlingVelocityDp = 400.dp
internal const val NoDistance = 0f
internal const val NoVelocity = 0f
private const val DEBUG = false

private inline fun debugLog(generateMsg: () -> String) {
    if (DEBUG) {
        println("SnapFlingBehavior: ${generateMsg()}")
    }
}
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

package androidx.animation

import androidx.animation.Physics.Companion.DampingRatioNoBouncy
import androidx.animation.Physics.Companion.StiffnessVeryLow

const val DEBUG = false

/**
 * This animation class is intended to be stateless. Once they are configured, they know how to
 * calculate animation values at any given time, when provided with start/end values and velocity.
 * It is stateless in that it doesn't manage its own lifecycle: it doesn't know when it started, or
 * should finish. It only reacts to the given playtime (i.e. time elapsed since the start of the
 * animation). It also doesn't anticipate the input play time to be in any sort of sequence.
 * The intended use is to query [Animation] objects on what the animation value would be.This
 * design makes it straightforward to coordinate different animations, and they are more testable.
 * In our specific use case, this design also has the added benefit of reusing the same default
 * animation to createAnimation all properties.
 */
// TODO: Use Duration or TimeStamp for playtime once they are inlined.
internal interface Animation<T> {
    fun isFinished(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float
    ): Boolean

    fun getValue(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float,
        interpolator: (T, T, Float) -> T
    ): T

    fun getVelocity(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float,
        interpolator: (T, T, Float) -> T
    ): Float
}

/**
 * Used by [Tween] and [Keyframes].
 * Base interface for the animations where velocity is calculated by difference between the
 * current value and the value 1 ms ago.
 */
private interface DiffBasedVelocityAnimation<T> : Animation<T> {

    override fun getVelocity(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float,
        interpolator: (T, T, Float) -> T
    ): Float {
        if (start is Number && end is Number) {
            if (playTime <= 0) {
                return 0f
            }
            val startNum = getValue(playTime - 1, start, end, startVelocity, interpolator) as Number
            val endNum = getValue(playTime, start, end, startVelocity, interpolator) as Number
            return (endNum.toFloat() - startNum.toFloat()) * 1000f
        }
        return 0f
    }
}

/**
 * [Keyframes] class manages the animation based on the values defined at different timestamps in
 * the duration of the animation (i.e. different keyframes). Each keyframe can be provided via
 * [keyframes] parameter. [Keyframes] allows very specific animation definitions with a
 * precision to millisecond.
 *
 * Use [KeyframesBuilder] to create a [Keyframes] animation.
 * // TODO: support different easing for each keyframe interval
 */
internal class Keyframes<T>(
    private val duration: Long,
    private val keyframes: Map<Long, Pair<T, Easing>>
) : DiffBasedVelocityAnimation<T> {

    init {
        if (duration < 0) {
            throw IllegalArgumentException("Duration should be non-negative")
        }
    }

    override fun isFinished(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float
    ) = playTime >= duration

    override fun getValue(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float,
        interpolator: (T, T, Float) -> T
    ): T {
        // Find the range where playtime fits
        val playTime: Long = playTime.coerceIn(0, duration)

        if (keyframes.containsKey(playTime)) {
            return keyframes.getValue(playTime).first
        }

        var startTime = 0L
        var startVal = start
        var endVal = end
        var endTime: Long = duration
        var easing: Easing = LinearEasing
        for ((timestamp, value) in keyframes) {
            if (playTime > timestamp && timestamp >= startTime) {
                startTime = timestamp
                startVal = value.first
                easing = value.second
            } else if (playTime < timestamp && timestamp <= endTime) {
                endTime = timestamp
                endVal = value.first
            }
        }

        // Now interpolate
        val fraction = easing((playTime - startTime) / (endTime - startTime).toFloat())
        return interpolator(startVal, endVal, fraction)
    }
}

/**
 * [Tween] is responsible for animating from one value to another using a provided easing easing.
 * The duration for such an animation can be adjusted via [duration]. The animation can be
 * delayed via [delay].
 */
internal class Tween<T>(
    private val duration: Long,
    private val delay: Long = 0,
    private val easing: Easing
) : DiffBasedVelocityAnimation<T> {

    init {
        if (duration < 0) {
            throw IllegalArgumentException("Duration should be non-negative")
        }
        if (delay < 0) {
            throw IllegalArgumentException("Delay should be non-negative")
        }
    }

    override fun isFinished(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float
    ): Boolean = playTime >= delay + duration

    override fun getValue(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float,
        interpolator: (T, T, Float) -> T
    ): T {
        val playTime: Long = (playTime - delay).coerceIn(0, duration)
        val rawFraction = if (duration == 0L) 1f else playTime / duration.toFloat()
        val fraction = easing(rawFraction)
        return interpolator(start, end, fraction)
    }
}

/**
 * [Physics] animation is in its core a spring animation. It is the default animation that the
 * animation system uses to createAnimation from [TransitionState] to [TransitionState] when no
 * animations are specified. Its configuration can be tuned via adjusting the spring parameters,
 * namely [dampingRatio] and [stiffness]. By default, [Physics] animation uses a spring with
 * [dampingRatio] = [DampingRatioNoBouncy]  and [stiffness] = [StiffnessVeryLow].
 */
internal class Physics<T>(
    /**
     * Damping ratio of the spring. Defaults to [DampingRatioNoBouncy]
     */
    dampingRatio: Float = DampingRatioNoBouncy,
    /**
     * Stiffness of the spring. Defaults to [StiffnessVeryLow]
     */
    stiffness: Float = StiffnessVeryLow
) : Animation<T> {

    companion object {
        /**
         * Stiffness constant for extremely stiff spring
         */
        const val StiffnessHigh = 10_000f
        /**
         * Stiffness constant for medium stiff spring. This is the default stiffness for spring force.
         */
        const val StiffnessMedium = 1500f
        /**
         * Stiffness constant for a spring with low stiffness.
         */
        const val StiffnessLow = 200f
        /**
         * Stiffness constant for a spring with very low stiffness.
         */
        const val StiffnessVeryLow = 50f

        /**
         * Damping ratio for a very bouncy spring. Note for under-damped springs
         * (i.e. damping ratio < 1), the lower the damping ratio, the more bouncy the spring.
         */
        const val DampingRatioHighBouncy = 0.2f
        /**
         * Damping ratio for a medium bouncy spring. This is also the default damping ratio for spring
         * force. Note for under-damped springs (i.e. damping ratio < 1), the lower the damping ratio,
         * the more bouncy the spring.
         */
        const val DampingRatioMediumBouncy = 0.5f
        /**
         * Damping ratio for a spring with low bounciness. Note for under-damped springs
         * (i.e. damping ratio < 1), the lower the damping ratio, the higher the bounciness.
         */
        const val DampingRatioLowBouncy = 0.75f
        /**
         * Damping ratio for a spring with no bounciness. This damping ratio will create a critically
         * damped spring that returns to equilibrium within the shortest amount of time without
         * oscillating.
         */
        const val DampingRatioNoBouncy = 1f
    }

    private val spring = SpringSimulation(1f).also {
        it.dampingRatio = dampingRatio
        it.stiffness = stiffness
    }

    override fun isFinished(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float
    ): Boolean {
        var startFloat = 0f
        var endFloat = 1f
        if (start is Number && end is Number) {
            startFloat = start.toFloat()
            endFloat = end.toFloat()
        }
        spring.finalPosition = endFloat
        return spring.isAtEquilibrium(startFloat, startVelocity, playTime)
    }

    override fun getValue(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float,
        interpolator: (T, T, Float) -> T
    ): T {
        var startFloat = 0f
        var endFloat = 1f
        if (start is Number && end is Number) {
            startFloat = start.toFloat()
            endFloat = end.toFloat()
        }
        spring.finalPosition = endFloat
        val (value, _) = spring.updateValues(startFloat, startVelocity, playTime)
        if (startFloat == endFloat) {
            // Can't use interpolation when the range is empty. It is possible only for
            // Numbers so we can just cast the float value to the required type.
            return value.castToNumberTypeOf(start)
        } else {
            val fraction = (value - startFloat) / (endFloat - startFloat)
            return interpolator(start, end, fraction)
        }
    }

    override fun getVelocity(
        playTime: Long,
        start: T,
        end: T,
        startVelocity: Float,
        interpolator: (T, T, Float) -> T
    ): Float {
        if (start is Number && end is Number) {
            spring.finalPosition = end.toFloat()
            val (_, velocity) = spring.updateValues(start.toFloat(), startVelocity, playTime)
            return velocity
        } else {
            return 0f
        }
    }

    private fun <T> Float.castToNumberTypeOf(type: T): T {
        val result: Number = when (type) {
            is Float -> this
            is Int -> toInt()
            is Long -> toLong()
            is Double -> toDouble()
            is Short -> toShort()
            is Byte -> toByte()
            else -> throw IllegalStateException("Should never happen as $type is always Number")
        }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }
}

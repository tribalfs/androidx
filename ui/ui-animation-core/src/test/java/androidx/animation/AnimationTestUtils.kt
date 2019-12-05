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

internal fun Animation<AnimationVector1D>.at(time: Int): Float =
    getValue(
        time.toLong(),
        AnimationVector1D(0f),
        AnimationVector1D(1f),
        AnimationVector1D(0f)
    ).value

internal fun Animation<AnimationVector1D>.isFinished(time: Int): Boolean =
    isFinished(time.toLong(), AnimationVector1D(0f), AnimationVector1D(1f), AnimationVector1D(0f))

// Convenient method to build a Float animation.
internal fun AnimationBuilder<Float>.build() = this.build(FloatToVectorConverter)

internal fun Animation<AnimationVector1D>.getValue(
    playTime: Long,
    start: Number,
    end: Number,
    startVelocity: Number
) = getValue(
    playTime,
    AnimationVector1D(start.toFloat()),
    AnimationVector1D(end.toFloat()),
    AnimationVector1D(startVelocity.toFloat())
).value

internal fun Animation<AnimationVector1D>.getVelocity(
    playTime: Long,
    start: Number,
    end: Number,
    startVelocity: Number
) = getVelocity(
    playTime,
    AnimationVector1D(start.toFloat()),
    AnimationVector1D(end.toFloat()),
    AnimationVector1D(startVelocity.toFloat())
).value

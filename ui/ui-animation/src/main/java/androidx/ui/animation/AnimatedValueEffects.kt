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

package androidx.ui.animation

import androidx.animation.AnimatedFloat
import androidx.animation.AnimatedValue
import androidx.animation.AnimationClockObservable
import androidx.animation.AnimationVector
import androidx.animation.AnimationVector4D
import androidx.animation.Spring
import androidx.animation.TwoWayConverter
import androidx.compose.Composable
import androidx.compose.Model
import androidx.compose.remember
import androidx.ui.core.AnimationClockAmbient
import androidx.ui.graphics.Color

/**
 * The animatedValue effect creates an [AnimatedValue] and positionally memoizes it. When the
 * [AnimatedValue] object gets its value updated, components that rely on that value will be
 * automatically recomposed.
 *
 * @param initVal Initial value to set [AnimatedValue] to.
 * @param converter A value type converter for transforming any type T to an animatable type (i.e.
 *                  Floats, Vector2D, Vector3D, etc)
 * @param visibilityThreshold Visibility threshold for the animatedValue to consider itself
 * finished.
 */
@Composable
fun <T, V : AnimationVector> animatedValue(
    initVal: T,
    converter: TwoWayConverter<T, V>,
    visibilityThreshold: V? = null,
    clock: AnimationClockObservable = AnimationClockAmbient.current
): AnimatedValue<T, V> = clock.asDisposableClock().let { disposableClock ->
    remember(disposableClock) {
        AnimatedValueModel(initVal, converter, disposableClock, visibilityThreshold)
    }
}

/**
 * The animatedValue effect creates an [AnimatedFloat] and positionally memoizes it. When the
 * [AnimatedFloat] object gets its value updated, components that rely on that value will be
 * automatically recomposed.
 *
 * @param initVal Initial value to set [AnimatedFloat] to.
 */
@Composable
fun animatedFloat(
    initVal: Float,
    visibilityThreshold: Float = Spring.DefaultDisplacementThreshold,
    clock: AnimationClockObservable = AnimationClockAmbient.current
): AnimatedFloat = clock.asDisposableClock().let { disposableClock ->
    remember(disposableClock) { AnimatedFloatModel(initVal, disposableClock, visibilityThreshold) }
}

/**
 * The animatedValue effect creates an [AnimatedValue] of [Color] and positionally memoizes it. When
 * the [AnimatedValue] object gets its value updated, components that rely on that value will be
 * automatically recomposed.
 *
 * @param initVal Initial value to set [AnimatedValue] to.
 */
@Composable
fun animatedColor(
    initVal: Color,
    clock: AnimationClockObservable = AnimationClockAmbient.current
): AnimatedValue<Color, AnimationVector4D> = clock.asDisposableClock().let { disposableClock ->
    remember(disposableClock) {
        AnimatedValueModel(
            value = initVal,
            typeConverter = ColorToVectorConverter(initVal.colorSpace),
            clock = disposableClock
        )
    }
}

/**
 * Model class for [AnimatedValue]. This class tracks the value field change, so that composables
 * that read from this field can get promptly recomposed as the animation updates the value.
 *
 * @param value The overridden value field that can only be mutated by animation
 * @param typeConverter The converter for converting any value of type [T] to an
 *                      [AnimationVector] type
 * @param clock The animation clock that will be used to drive the animation
 */
@Model
class AnimatedValueModel<T, V : AnimationVector>(
    override var value: T,
    typeConverter: TwoWayConverter<T, V>,
    clock: AnimationClockObservable,
    visibilityThreshold: V? = null
) : AnimatedValue<T, V>(typeConverter, clock, visibilityThreshold)

/**
 * Model class for [AnimatedFloat]. This class tracks the value field change, so that composables
 * that read from this field can get promptly recomposed as the animation updates the value.
 *
 * @param value The overridden value field that can only be mutated by animation
 * @param clock The animation clock that will be used to drive the animation
 */
@Model
class AnimatedFloatModel(
    override var value: Float,
    clock: AnimationClockObservable,
    visibilityThreshold: Float = Spring.DefaultDisplacementThreshold
) : AnimatedFloat(clock, visibilityThreshold)
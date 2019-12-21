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
import androidx.animation.TwoWayConverter
import androidx.compose.Model
import androidx.animation.ValueHolder
import androidx.animation.AnimationVector
import androidx.animation.AnimationVector4D
import androidx.compose.Composable
import androidx.compose.remember
import androidx.ui.graphics.Color

/**
 * The animatedValue effect creates an [AnimatedValue] and positionally memoizes it. When the
 * [AnimatedValue] object gets its value updated, components that rely on that value will be
 * automatically recomposed.
 *
 * @param initVal Initial value to set [AnimatedValue] to.
 * @param converter A value type converter for transforming any type T to an animatable type (i.e.
 *                  Floats, Vector2D, Vector3D, etc)
 */
@Composable
fun <T, V : AnimationVector> animatedValue(
    initVal: T,
    converter: TwoWayConverter<T, V>
): AnimatedValue<T, V> = remember { AnimatedValue(AnimValueHolder(initVal), converter) }

/**
 * The animatedValue effect creates an [AnimatedFloat] and positionally memoizes it. When the
 * [AnimatedFloat] object gets its value updated, components that rely on that value will be
 * automatically recomposed.
 *
 * @param initVal Initial value to set [AnimatedFloat] to.
 */
@Composable
fun animatedFloat(initVal: Float): AnimatedFloat =
    remember { AnimatedFloat(AnimValueHolder(initVal)) }

/**
 * The animatedValue effect creates an [AnimatedValue] of [Color] and positionally memoizes it. When
 * the [AnimatedValue] object gets its value updated, components that rely on that value will be
 * automatically recomposed.
 *
 * @param initVal Initial value to set [AnimatedValue] to.
 */
@Composable
fun animatedColor(initVal: Color): AnimatedValue<Color, AnimationVector4D> =
    remember { AnimatedValue(AnimValueHolder(initVal), ColorToVectorConverter(initVal.colorSpace)) }

@Model
private class AnimValueHolder<T> (
    override var value: T
) : ValueHolder<T>

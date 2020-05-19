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

import androidx.animation.AnimationVector1D
import androidx.animation.AnimationVector2D
import androidx.animation.AnimationVector4D
import androidx.animation.PropKey
import androidx.animation.TwoWayConverter
import androidx.ui.geometry.Rect
import androidx.ui.graphics.Color
import androidx.ui.graphics.colorspace.ColorSpace
import androidx.ui.graphics.colorspace.ColorSpaces
import androidx.ui.unit.Dp
import androidx.ui.unit.Px
import androidx.ui.unit.PxPosition
import androidx.ui.unit.px

/**
 * Built-in property key for [Px] properties.
 *
 * @param label Label for distinguishing different prop keys in Android Studio.
 */
class PxPropKey(override val label: String = "PxPropKey") : PropKey<Px, AnimationVector1D> {
    override val typeConverter = PxToVectorConverter
}

/**
 * Built-in property key for [Dp] properties.
 *
 * @param label Label for distinguishing different prop keys in Android Studio.
 */
class DpPropKey(override val label: String = "DpPropKey") : PropKey<Dp, AnimationVector1D> {
    override val typeConverter = DpToVectorConverter
}

/**
 * Built-in property key for [PxPosition] properties.
 *
 * @param label Label for distinguishing different prop keys in Android Studio.
 */
class PxPositionPropKey(
    override val label: String = "PxPositionPropKey"
) : PropKey<PxPosition, AnimationVector2D> {
    override val typeConverter = PxPositionToVectorConverter
}

/**
 * Built-in property key for [Color] properties.
 *
 * @param label Label for distinguishing different prop keys in Android Studio.
 */
class ColorPropKey(
    colorSpace: ColorSpace = ColorSpaces.Srgb,
    override val label: String = "ColorPropKey"
) : PropKey<Color, AnimationVector4D> {
    override val typeConverter = ColorToVectorConverter(colorSpace)
}

/**
 * Built-in property key for [Rect] properties.
 *
 * @param label Label for distinguishing different prop keys in Android Studio.
 */
class RectPropKey(
    override val label: String = "RectPropKey"
) : PropKey<Rect, AnimationVector4D> {
    override val typeConverter = RectToVectorConverter
}

/**
 * A lambda that takes a [ColorSpace] and returns a converter that can both convert a [Color] to
 * a [AnimationVector4D], and convert a [AnimationVector4D]) back to a [Color] in the given
 * [ColorSpace].
 */
val ColorToVectorConverter: (colorSpace: ColorSpace) -> TwoWayConverter<Color, AnimationVector4D> =
    { colorSpace ->
        TwoWayConverter(
            convertToVector = {
                val linearColor = it.convert(ColorSpaces.LinearExtendedSrgb)
                AnimationVector4D(
                    linearColor.alpha, linearColor.red, linearColor.green,
                    linearColor.blue
                )
            },
            convertFromVector = {
                Color(
                    alpha = it.v1,
                    red = it.v2,
                    green = it.v3,
                    blue = it.v4,
                    colorSpace = ColorSpaces.LinearExtendedSrgb
                ).convert(colorSpace)
            }
        )
    }

/**
 * A type converter that converts a [Rect] to a [AnimationVector4D], and vice versa.
 */
val RectToVectorConverter: TwoWayConverter<Rect, AnimationVector4D> =
    TwoWayConverter(
        convertToVector = {
            AnimationVector4D(it.left, it.top, it.right, it.bottom)
        },
        convertFromVector = {
            Rect(it.v1, it.v2, it.v3, it.v4)
        }
    )

/**
 * A type converter that converts a [PxPosition] to a [AnimationVector2D], and vice versa.
 */
val PxPositionToVectorConverter: TwoWayConverter<PxPosition, AnimationVector2D> =
    TwoWayConverter(
        convertToVector = { AnimationVector2D(it.x.value, it.y.value) },
        convertFromVector = { PxPosition(it.v1.px, it.v2.px) }
    )

/**
 * A type converter that converts a [Dp] to a [AnimationVector1D], and vice versa.
 */
val DpToVectorConverter: TwoWayConverter<Dp, AnimationVector1D> = TwoWayConverter(
    convertToVector = { AnimationVector1D(it.value) },
    convertFromVector = { Dp(it.value) }
)

/**
 * A type converter that converts a [Px] to a [AnimationVector1D], and vice versa.
 */
val PxToVectorConverter: TwoWayConverter<Px, AnimationVector1D> = TwoWayConverter(
    convertToVector = { AnimationVector1D(it.value) },
    convertFromVector = { Px(it.value) }
)
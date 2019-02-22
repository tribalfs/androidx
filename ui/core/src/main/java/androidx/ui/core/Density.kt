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

package androidx.ui.core

import android.content.Context
import androidx.ui.engine.geometry.Rect

/**
 * A density of the screen. Used for convert [Dp] to pixels.
 */
// TODO(Andrey): Mark it as inline when it will work stable for us.
/*inline*/ data class Density(val density: Float)

/**
 * Creates a [Density] from this [Context]
 *
 */
// TODO(Andrey): Move to android specific module
fun Density(context: Context): Density =
    Density(context.resources.displayMetrics.density)

/**
 * Convert [Dp] to pixels. Pixels are used to paint to [Canvas].
 */
fun Dp.toPx(density: Density): Float = dp * density.density

/** Convert a [Float] pixel value to a Dp */
fun Float.toDp(density: Density): Dp = (this / density.density).dp

/** Convert a [Float] pixel value to a Dp */
fun Int.toDp(density: Density): Dp = toFloat().toDp(density)

/**
 * Convert a [Size] to a [PixelSize].
 */
fun Size.toPx(density: Density): PixelSize =
    PixelSize(width.toPx(density), height.toPx(density))

/**
 * Convert a [Bounds] to a [Rect].
 */
fun Bounds.toRect(density: Density): Rect {
    return Rect(
        left.toPx(density),
        top.toPx(density),
        right.toPx(density),
        bottom.toPx(density)
    )
}

/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.foundation.shape

import androidx.compose.ui.geometry.Size
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.util.annotation.FloatRange
import androidx.compose.ui.util.annotation.IntRange

/**
 * Defines size of a corner in pixels. For example for rounded shape it can be a corner radius.
 */
@Immutable
interface CornerSize {
    /**
     * Converts the [CornerSize] to pixels.
     *
     * @param shapeSize the size of the shape
     * @param density the current density of the screen.
     *
     * @return resolved size of the corner in pixels
     */
    fun toPx(shapeSize: Size, density: Density): Float
}

/**
 * Creates [CornerSize] with provided size.
 * @param size the corner size defined in [Dp].
 */
@Stable
fun CornerSize(size: Dp): CornerSize = DpCornerSize(size)

private data class DpCornerSize(private val size: Dp) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density) =
        with(density) { size.toPx() }
}

/**
 * Creates [CornerSize] with provided size.
 * @param size the corner size defined in pixels.
 */
@Stable
fun CornerSize(size: Float): CornerSize = PxCornerSize(size)

private data class PxCornerSize(private val size: Float) : CornerSize {
    override fun toPx(shapeSize: Size, density: Density) = size
}

/**
 * Creates [CornerSize] with provided size.
 * @param percent the corner size defined in percents of the shape's smaller side.
 * Can't be negative or larger then 100 percents.
 */
@Stable
fun CornerSize(@IntRange(from = 0, to = 100) percent: Int): CornerSize =
    PercentCornerSize(percent.toFloat())

/**
 * Creates [CornerSize] with provided size.
 * @param percent the corner size defined in float percents of the shape's smaller side.
 * Can't be negative or larger then 100 percents.
 */
private data class PercentCornerSize(
    @FloatRange(from = 0.0, to = 100.0) private val percent: Float
) : CornerSize {
    init {
        if (percent < 0 || percent > 100) {
            throw IllegalArgumentException("The percent should be in the range of [0, 100]")
        }
    }

    override fun toPx(shapeSize: Size, density: Density) =
        shapeSize.minDimension * (percent / 100f)
}

/**
 * [CornerSize] always equals to zero.
 */
@Stable
val ZeroCornerSize: CornerSize = object : CornerSize {
    override fun toPx(shapeSize: Size, density: Density) = 0.0f
}

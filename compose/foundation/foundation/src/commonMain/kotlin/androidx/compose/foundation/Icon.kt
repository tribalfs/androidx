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

package androidx.compose.foundation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.preferredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageAsset
import androidx.compose.ui.graphics.painter.ImagePainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.VectorAsset
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp

/**
 * Icon component that draws [asset] using [tint], defaulting to [AmbientContentColor].
 *
 * @param asset [VectorAsset] to draw inside this Icon
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [asset]. If [Color.Unspecified] is provided, then no
 *  tint is applied
 */
@Deprecated(
    "Icon has been moved into the Material library: androidx.compose.material.Icon",
    replaceWith = ReplaceWith("Icon(asset)", "androidx.compose.material.Icon")
)
@Composable
fun Icon(
    asset: VectorAsset,
    modifier: Modifier = Modifier,
    tint: Color = AmbientContentColor.current
) {
    @Suppress("DEPRECATION")
    Icon(
        painter = rememberVectorPainter(asset),
        modifier = modifier,
        tint = tint
    )
}

/**
 * Icon component that draws [asset] using [tint], defaulting to [AmbientContentColor].
 *
 * @param asset [ImageAsset] to draw inside this Icon
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [asset]. If [Color.Unspecified] is provided, then no
 *  tint is applied
 */
@Deprecated(
    "Icon has been moved into the Material library: androidx.compose.material.Icon",
    replaceWith = ReplaceWith("Icon(asset)", "androidx.compose.material.Icon")
)
@Composable
fun Icon(
    asset: ImageAsset,
    modifier: Modifier = Modifier,
    tint: Color = AmbientContentColor.current
) {
    val painter = remember(asset) { ImagePainter(asset) }
    @Suppress("DEPRECATION")
    Icon(
        painter = painter,
        modifier = modifier,
        tint = tint
    )
}

/**
 * Icon component that draws a [painter] using [tint], defaulting to [AmbientContentColor].
 *
 * @param painter Painter to draw inside this Icon
 * @param modifier optional [Modifier] for this Icon
 * @param tint tint to be applied to [painter]. If [Color.Unspecified] is provided, then no
 *  tint is applied
 */
@Deprecated(
    "Icon has been moved into the Material library: androidx.compose.material.Icon",
    replaceWith = ReplaceWith("Icon(asset)", "androidx.compose.material.Icon")
)
@Composable
fun Icon(
    painter: Painter,
    modifier: Modifier = Modifier,
    tint: Color = AmbientContentColor.current
) {
    // TODO: consider allowing developers to override the intrinsic size, and specify their own
    // size that this icon will be forced to take up.
    // TODO: b/149735981 semantics for content description
    val colorFilter = if (tint == Color.Unspecified) null else ColorFilter.tint(tint)
    Box(modifier.defaultSizeFor(painter).paint(painter, colorFilter = colorFilter))
}

private fun Modifier.defaultSizeFor(painter: Painter) =
    this.then(
        if (painter.intrinsicSize == Size.Unspecified || painter.intrinsicSize.isInfinite()) {
            DefaultIconSizeModifier
        } else {
            Modifier
        }
    )

private fun Size.isInfinite() = width.isInfinite() && height.isInfinite()

// Default icon size, for icons with no intrinsic size information
private val DefaultIconSizeModifier = Modifier.preferredSize(24.dp)

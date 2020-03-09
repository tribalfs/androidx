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

package androidx.ui.material

import androidx.compose.Composable
import androidx.ui.core.CurrentTextStyleProvider
import androidx.ui.core.Modifier
import androidx.ui.core.Text
import androidx.ui.foundation.Clickable
import androidx.ui.foundation.Image
import androidx.ui.foundation.shape.corner.CircleShape
import androidx.ui.graphics.Color
import androidx.ui.graphics.ImageAsset
import androidx.ui.graphics.Shape
import androidx.ui.layout.Container
import androidx.ui.layout.DpConstraints
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutWidth
import androidx.ui.layout.Row
import androidx.ui.layout.Spacer
import androidx.ui.material.ripple.Ripple
import androidx.ui.text.TextStyle
import androidx.ui.unit.Dp
import androidx.ui.unit.dp

/**
 * A floating action button (FAB) is a [Button] to represents the primary action of a screen.
 *
 * By default it uses a circle shape and centers its content.
 *
 * @sample androidx.ui.material.samples.FloatingActionButtonCustomContent
 *
 * @see FloatingActionButton overload for the variants with an icon or an icon and a text.
 *
 * @param onClick will be called when user clicked on the button
 * @param modifier Modifier to be applied to the button.
 * @param minSize Minimum size of the FAB
 * @param shape Defines the Button's shape as well its shadow. When null is provided it uses
 * the [Shapes.button] from [ShapeAmbient]
 * @param color The background color
 * @param elevation The z-coordinate at which to place this button. This controls the size
 * of the shadow below the button
 */
@Composable
fun FloatingActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier.None,
    minSize: Dp = FabSize,
    shape: Shape = CircleShape,
    color: Color = MaterialTheme.colors().primary,
    elevation: Dp = 6.dp,
    children: @Composable() () -> Unit
) {
    Surface(modifier = modifier, shape = shape, color = color, elevation = elevation) {
        Ripple(bounded = true) {
            Clickable(onClick = onClick) {
                Container(constraints = DpConstraints(minWidth = minSize, minHeight = minSize)) {
                    CurrentTextStyleProvider(MaterialTheme.typography().button, children)
                }
            }
        }
    }
}

/**
 * A floating action button (FAB) is a [Button] to represents the primary action of a screen.
 *
 * It draws the [icon] in the center of the FAB.
 *
 * @sample androidx.ui.material.samples.FloatingActionButtonSimple
 *
 * @see FloatingActionButton overload for the variants with a custom content or an icon and a text.
 *
 * @param icon Image to draw in the center
 * @param onClick will be called when user clicked on the button
 * @param modifier Modifier to be applied to the button.
 * @param color The background color
 * @param elevation The z-coordinate at which to place this button. This controls the size
 * of the shadow below the button
 */
@Composable
fun FloatingActionButton(
    icon: ImageAsset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.None,
    shape: Shape = CircleShape,
    color: Color = MaterialTheme.colors().primary,
    elevation: Dp = 6.dp
) {
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        shape = shape,
        color = color,
        elevation = elevation
    ) {
        Image(image = icon)
    }
}

/**
 * An extended [FloatingActionButton] with an [icon] and a [text].
 *
 * @sample androidx.ui.material.samples.FloatingActionButtonExtended
 *
 * @see FloatingActionButton overload for the variants with a custom content or an icon.
 *
 * @param text Text to display.
 * @param onClick will be called when user clicked on the button
 * @param modifier Modifier to be applied to the button.
 * @param icon Image to draw to the left of the text. It is optional
 * @param textStyle Optional [TextStyle] to apply for a [text]
 * @param color The background color
 * @param elevation The z-coordinate at which to place this button. This controls the size
 * if the shadow below the button.
 */
@Composable
fun FloatingActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.None,
    icon: ImageAsset? = null,
    textStyle: TextStyle? = null,
    color: Color = MaterialTheme.colors().primary,
    elevation: Dp = 6.dp
) {
    FloatingActionButton(
        modifier = modifier,
        onClick = onClick,
        color = color,
        elevation = elevation,
        minSize = ExtendedFabHeight) {
        if (icon == null) {
            Text(
                text = text,
                style = textStyle,
                modifier = LayoutPadding(
                    start = ExtendedFabTextPadding,
                    end = ExtendedFabTextPadding
                )
            )
        } else {
            Row(LayoutPadding(start = ExtendedFabIconPadding, end = ExtendedFabTextPadding)) {
                Image(image = icon)
                Spacer(LayoutWidth(ExtendedFabIconPadding))
                Text(text = text, style = textStyle)
            }
        }
    }
}

private val FabSize = 56.dp
private val ExtendedFabHeight = 48.dp
private val ExtendedFabIconPadding = 12.dp
private val ExtendedFabTextPadding = 20.dp

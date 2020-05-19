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

package androidx.ui.material

import androidx.compose.Composable
import androidx.ui.core.Modifier
import androidx.ui.foundation.Box
import androidx.ui.foundation.ContentGravity
import androidx.ui.foundation.clickable
import androidx.ui.foundation.selection.Toggleable
import androidx.ui.layout.preferredSize
import androidx.ui.material.ripple.RippleIndication
import androidx.ui.material.ripple.ripple
import androidx.ui.unit.dp

/**
 * IconButton is a clickable icon, used to represent actions. An IconButton has an overall minimum
 * touch target size of 48 x 48dp, to meet accessibility guidelines. [icon] is centered
 * inside the IconButton.
 *
 * This component is typically used inside an App Bar for the navigation icon / actions. See App
 * Bar documentation for samples of this.
 *
 * [icon] should typically be an [androidx.ui.foundation.Icon], using an icon from
 * [androidx.ui.material.icons.Icons]. If using a custom icon, note that the typical size for the
 * internal icon is 24 x 24 dp.
 *
 * @sample androidx.ui.material.samples.IconButtonSample
 *
 * @param onClick the lambda to be invoked when this icon is pressed
 * @param modifier optional [Modifier] for this IconButton
 * @param icon the content (icon) to be drawn inside the IconButton. This is typically an
 * [androidx.ui.foundation.Icon].
 */
@Composable
fun IconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clickable(
                onClick = onClick,
                indication = RippleIndication(bounded = false, radius = RippleRadius)
            )
            .plus(IconButtonSizeModifier),
        gravity = ContentGravity.Center,
        children = icon
    )
}

/**
 * An [IconButton] with two states, for icons that can be toggled 'on' and 'off', such as a
 * bookmark icon, or a navigation icon that opens a drawer.
 *
 * @sample androidx.ui.material.samples.IconToggleButtonSample
 *
 * @param checked whether this IconToggleButton is currently checked
 * @param onCheckedChange callback to be invoked when this icon is selected
 * @param enabled enabled whether or not this [IconToggleButton] will handle input events and appear
 * enabled for semantics purposes
 * @param modifier optional [Modifier] for this IconToggleButton
 * @param icon the content (icon) to be drawn inside the IconToggleButton. This is typically an
 * [androidx.ui.foundation.Icon].
 */
@Composable
fun IconToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Toggleable(
        value = checked,
        onValueChange = onCheckedChange,
        enabled = enabled,
        modifier = Modifier.ripple(bounded = false, radius = RippleRadius, enabled = enabled)
    ) {
        Box(
            modifier = modifier + IconButtonSizeModifier,
            gravity = ContentGravity.Center,
            children = icon
        )
    }
}

// Default radius of an unbounded ripple in an IconButton
private val RippleRadius = 24.dp

// TODO: b/149691127 investigate our strategy around accessibility touch targets, and remove
// per-component definitions of this size.
// Diameter of the IconButton, to allow for correct minimum touch target size for accessibility
private val IconButtonSizeModifier = Modifier.preferredSize(48.dp)

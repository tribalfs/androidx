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

package androidx.ui.material.studies.rally

import androidx.animation.LinearEasing
import androidx.animation.transitionDefinition
import androidx.compose.Composable
import androidx.compose.remember
import androidx.ui.animation.ColorPropKey
import androidx.ui.animation.Transition
import androidx.ui.core.Text
import androidx.ui.foundation.selection.MutuallyExclusiveSetItem
import androidx.ui.graphics.Color
import androidx.ui.graphics.vector.VectorAsset
import androidx.ui.layout.LayoutHeight
import androidx.ui.layout.LayoutPadding
import androidx.ui.layout.LayoutWidth
import androidx.ui.layout.Row
import androidx.ui.layout.Spacer
import androidx.ui.material.MaterialTheme
import androidx.ui.material.ripple.Ripple
import androidx.ui.material.Surface
import androidx.ui.text.TextStyle
import androidx.ui.unit.dp
import java.util.Locale

@Composable
fun RallyTopAppBar(
    allScreens: List<RallyScreenState>,
    onTabSelected: (RallyScreenState) -> Unit,
    currentScreen: RallyScreenState
) {
    Surface(modifier = LayoutHeight(TabHeight) + LayoutWidth.Fill) {
        Row {
            allScreens.forEachIndexed { index, screen ->
                RallyTab(
                    text = screen.name.toUpperCase(Locale.getDefault()),
                    icon = screen.icon,
                    onSelected = { onTabSelected(screen) },
                    selected = currentScreen.ordinal == index
                )
            }
        }
    }
}

@Composable
private fun RallyTab(
    text: String,
    icon: VectorAsset,
    onSelected: () -> Unit,
    selected: Boolean
) {
    TabTransition(selected = selected) { tabTintColor ->
        Row(LayoutPadding(16.dp) + LayoutHeight(TabHeight)) {
            Ripple(bounded = false) {
                MutuallyExclusiveSetItem(selected = selected, onClick = onSelected) {
                    Icon(vectorImage = icon, tintColor = tabTintColor)
                    if (selected) {
                        Spacer(modifier = LayoutWidth(12.dp))
                        Text(text, style = TextStyle(color = tabTintColor))
                    }
                }
            }
        }
    }
}

@Composable
private fun TabTransition(
    selected: Boolean,
    children: @Composable() (color: Color) -> Unit
) {
    val color = MaterialTheme.colors().onSurface
    val transitionDefinition = remember {
        transitionDefinition {
            state(true) {
                this[TabTintColorKey] = color
            }

            state(false) {
                this[TabTintColorKey] = color.copy(alpha = InactiveTabOpacity)
            }

            transition(fromState = false, toState = true) {
                TabTintColorKey using tween {
                    duration = TabFadeInAnimationDuration
                    delay = TabFadeInAnimationDelay
                    easing = LinearEasing
                }
            }

            transition(fromState = true, toState = false) {
                TabTintColorKey using tween {
                    duration = TabFadeOutAnimationDuration
                    delay = TabFadeInAnimationDelay
                    easing = LinearEasing
                }
            }
        }
    }
    Transition(transitionDefinition, selected) { state ->
        children(state[TabTintColorKey])
    }
}

private val TabTintColorKey = ColorPropKey()
private val TabHeight = 56.dp
private const val InactiveTabOpacity = 0.60f

private const val TabFadeInAnimationDuration = 150
private const val TabFadeInAnimationDelay = 100
private const val TabFadeOutAnimationDuration = 100
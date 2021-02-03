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

package androidx.compose.foundation.text

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.text.InternalTextApi
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

@OptIn(InternalTextApi::class)
@Suppress("ModifierInspectorInfo")
internal fun Modifier.cursor(
    state: TextFieldState,
    value: TextFieldValue,
    offsetMapping: OffsetMapping,
    cursorColor: Color,
    enabled: Boolean
) = if (enabled) composed {
    // this should be a disposable clock, but it's not available in this module
    // however, we only launch one animation and guarantee that we stop it (via snap) in dispose
    val cursorAlpha = remember { Animatable(0f) }

    if (state.hasFocus && value.selection.collapsed && cursorColor.isSpecified) {
        LaunchedEffect(cursorColor, value.annotatedString) {
            if (@Suppress("DEPRECATION_ERROR") blinkingCursorEnabled) {
                cursorAlpha.animateTo(0f, cursorAnimationSpec)
            } else {
                cursorAlpha.snapTo(1f)
            }
        }
        drawWithContent {
            this.drawContent()
            val cursorAlphaValue = cursorAlpha.value.coerceIn(0f, 1f)
            if (cursorAlphaValue != 0f) {
                val transformedOffset = offsetMapping
                    .originalToTransformed(value.selection.start)
                val cursorRect = state.layoutResult?.value?.getCursorRect(transformedOffset)
                    ?: Rect(0f, 0f, 0f, 0f)
                val cursorWidth = DefaultCursorThickness.toPx()
                val cursorX = (cursorRect.left + cursorWidth / 2)
                    .coerceAtMost(size.width - cursorWidth / 2)

                drawLine(
                    cursorColor,
                    Offset(cursorX, cursorRect.top),
                    Offset(cursorX, cursorRect.bottom),
                    alpha = cursorAlphaValue,
                    strokeWidth = cursorWidth
                )
            }
        }
    } else {
        Modifier
    }
} else this

private val cursorAnimationSpec: AnimationSpec<Float>
    get() = infiniteRepeatable(
        animation = keyframes {
            durationMillis = 1000
            1f at 0
            1f at 499
            0f at 500
            0f at 999
        }
    )

internal val DefaultCursorThickness = 2.dp

// TODO(b/151940543): Remove this variable when we have a solution for idling animations
/** @suppress */
@InternalTextApi // Used by Testing infra
@Deprecated(level = DeprecationLevel.ERROR, message = "This is internal API and should not be used")
var blinkingCursorEnabled: Boolean = true
/*@VisibleForTesting
set*/
/*
 * Copyright 2021 The Android Open Source Project
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

@file:Suppress("DEPRECATION") // gestures

package androidx.compose.foundation.text

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.legacygestures.DragObserver
import androidx.compose.foundation.legacygestures.LongPressDragObserver
import androidx.compose.foundation.legacygestures.dragGestureFilter
import androidx.compose.foundation.legacygestures.longPressDragGestureFilter
import androidx.compose.foundation.legacygestures.tapGestureFilter
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput

// Touch selection
internal fun Modifier.longPressDragGestureFilter(
    observer: LongPressDragObserver,
    enabled: Boolean
) = if (enabled) this.then(longPressDragGestureFilter(observer)) else this

internal fun Modifier.focusRequestTapModifier(onTap: (Offset) -> Unit, enabled: Boolean) =
    if (enabled) this.tapGestureFilter(onTap) else this

// Focus modifiers
internal fun Modifier.textFieldFocusModifier(
    enabled: Boolean,
    focusRequester: FocusRequester,
    interactionSource: MutableInteractionSource?,
    onFocusChanged: (FocusState) -> Unit
) = this
    .focusRequester(focusRequester)
    .onFocusChanged(onFocusChanged)
    .focusable(interactionSource = interactionSource, enabled = enabled)

// Mouse
internal fun Modifier.mouseDragGestureFilter(
    dragObserver: DragObserver,
    enabled: Boolean
) = if (enabled) this.dragGestureFilter(dragObserver, startDragImmediately = true) else this

internal fun Modifier.mouseDragGestureDetector(
    detector: suspend PointerInputScope.() -> Unit,
    enabled: Boolean
) = if (enabled) Modifier.pointerInput(Unit, detector) else this
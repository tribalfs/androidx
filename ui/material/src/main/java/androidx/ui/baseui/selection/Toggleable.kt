/*
 * Copyright 2018 The Android Open Source Project
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

package androidx.ui.baseui.selection

import androidx.ui.core.Semantics
import androidx.ui.core.gesture.PressGestureDetector
import androidx.ui.core.semantics.SemanticsAction
import androidx.ui.core.semantics.SemanticsActionType
import com.google.r4a.Children
import com.google.r4a.Composable
import com.google.r4a.composer

@Composable
fun Toggleable(
    value: ToggleableState = ToggleableState.Checked,
    onToggle: (() -> Unit)? = null,
    @Children children: () -> Unit
) {
    val actions = if (onToggle != null) {
        listOf(SemanticsAction(SemanticsActionType.Tap, onToggle))
    } else {
        emptyList()
    }
    // TODO should we use PressReleasedGestureDetector?
    <PressGestureDetector onRelease=onToggle>
        // TODO: enabled should not be hardcoded
        // TODO(pavlis): Semantics currently doesn't support 4 states (only checked / unchecked / not checkable).
        <Semantics
            checked=(value == ToggleableState.Checked)
            enabled=true
            actions>
            <children />
        </Semantics>
    </PressGestureDetector>
}
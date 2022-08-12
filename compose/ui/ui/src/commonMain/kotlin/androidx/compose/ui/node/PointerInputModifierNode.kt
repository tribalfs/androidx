/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.compose.ui.node

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize

@ExperimentalComposeUiApi
interface PointerInputModifierNode : DelegatableNode {
    fun onPointerEvent(
        pointerEvent: PointerEvent,
        pass: PointerEventPass,
        bounds: IntSize
    )

    fun onCancelPointerInput()
    fun interceptOutOfBoundsChildEvents(): Boolean = false
    fun sharePointerInputWithSiblings(): Boolean = false
}

@OptIn(ExperimentalComposeUiApi::class)
internal val PointerInputModifierNode.isAttached: Boolean
    get() = node.isAttached

@OptIn(ExperimentalComposeUiApi::class)
internal val PointerInputModifierNode.layoutCoordinates: LayoutCoordinates
    get() = requireCoordinator(Nodes.PointerInput)

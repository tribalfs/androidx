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

package androidx.compose.ui.node

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputFilter
import androidx.compose.ui.input.pointer.PointerInputModifier

internal class PointerInputDelegatingWrapper(
    wrapped: LayoutNodeWrapper,
    pointerInputModifier: PointerInputModifier
) : DelegatingLayoutNodeWrapper<PointerInputModifier>(wrapped, pointerInputModifier) {

    init {
        pointerInputModifier.pointerInputFilter.layoutCoordinates = this
    }

    override var modifier: PointerInputModifier
        get() = super.modifier
        set(value) {
            super.modifier = value
            value.pointerInputFilter.layoutCoordinates = this
        }

    override fun hitTest(
        pointerPosition: Offset,
        hitTestResult: HitTestResult<PointerInputFilter>,
        isTouchEvent: Boolean
    ) {
        hitTestInMinimumTouchTarget(
            pointerPosition,
            hitTestResult,
            modifier.pointerInputFilter.interceptOutOfBoundsChildEvents,
            isTouchEvent,
            modifier.pointerInputFilter
        ) {
            hitTestChild(pointerPosition, hitTestResult, isTouchEvent)
        }
    }

    private fun hitTestChild(
        pointerPosition: Offset,
        hitTestResult: HitTestResult<PointerInputFilter>,
        isTouchEvent: Boolean
    ) {
        // Also, keep looking to see if we also might hit any children.
        // This avoids checking layer bounds twice as when we call super.hitTest()
        val positionInWrapped = wrapped.fromParentPosition(pointerPosition)
        wrapped.hitTest(positionInWrapped, hitTestResult, isTouchEvent)
    }
}

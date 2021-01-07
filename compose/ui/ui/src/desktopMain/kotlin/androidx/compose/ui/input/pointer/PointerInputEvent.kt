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

package androidx.compose.ui.input.pointer

import androidx.compose.ui.unit.Uptime
import androidx.compose.ui.geometry.Offset

internal actual class PointerInputEvent(
    actual val uptime: Uptime,
    actual val pointers: List<PointerInputEventData>
)

/**
 * This exposes PointerInputEventData for testing purposes.
 */
class TestPointerInputEventData(
    val id: PointerId,
    val uptime: Uptime,
    val position: Offset,
    val down: Boolean
) {
    internal fun toPointerInputEventData() = PointerInputEventData(id, uptime, position, down)
}

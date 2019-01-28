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

package androidx.ui.core.pointerinput

import androidx.ui.core.Duration
import androidx.ui.engine.geometry.Offset

/**
 * The normalized data structure for pointer input event information that is taken in processed by
 * Crane (via the [PointerEventProcessor]).
 */
internal data class PointerInputEvent(
    val timeStamp: Duration,
    val pointers: List<PointerInputEventData>
)

/**
 * Data that describes a particular pointer
 */
data class PointerInputEventData(
    val id: Int,
    val pointerInputData: PointerInputData
)

/**
 * Data associated with a pointer
 */
data class PointerInputData(
    val position: Offset? = null,
    val down: Boolean = false
)
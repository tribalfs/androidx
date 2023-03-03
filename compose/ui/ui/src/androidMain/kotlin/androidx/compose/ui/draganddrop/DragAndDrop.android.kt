/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.ui.draganddrop

import android.content.ClipData
import android.view.DragEvent
import android.view.View
import androidx.compose.ui.geometry.Offset

/**
 * [DragAndDropTransfer] representation for the Android platform.
 * It provides the [ClipData] required for drag and drop.
 */
actual class DragAndDropTransfer(
    /**
     * The [ClipData] being transferred.
     */
    val clipData: ClipData,
    /**
     * Optional local state for the DnD operation
     * @see [View.startDragAndDrop]
     */
    val localState: Any? = null,
    /**
     * Flags for the drag and drop operation.
     * @see [View.startDragAndDrop]
     */
    val flags: Int = 0,
)

/**
 * Android [DragAndDropEvent] which delegates to a [DragEvent]
 */
actual class DragAndDropEvent(
    /**
     * An indication of the reason the drag and drop event was sent
     */
    actual var type: DragAndDropEventType,
    internal var dragEvent: DragEvent
)

/**
 * Returns the backing [DragEvent] to read platform specific data
 */
val DragAndDropEvent.dragEvent get() = this.dragEvent

internal actual val DragAndDropEvent.positionInRoot: Offset
    get() = Offset(
        x = dragEvent.x,
        y = dragEvent.y
    )

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

package androidx.ui.core.selection

import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.PxPosition

/**
 * An interface handling selection. Get selection from a composable by passing in the start and end of
 * selection in a selection container as a pair, and the layout coordinates of the selection
 * container.
 */
interface TextSelectionHandler {
    // TODO(qqd) add API Docs and params
    /**
     * @param startPosition
     * @param endPosition
     * @param containerLayoutCoordinates
     * @param mode
     */
    fun getSelection(
        startPosition: PxPosition,
        endPosition: PxPosition,
        containerLayoutCoordinates: LayoutCoordinates,
        mode: SelectionMode
    ): Selection?
}

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

package androidx.ui.text.selection

import androidx.ui.geometry.Rect
import androidx.ui.unit.PxPosition

/**
 * The enum class allows user to decide the selection mode.
 */
internal enum class SelectionMode {
    /**
     * When selection handles are dragged across composables, selection extends by row, for example,
     * when the end selection handle is dragged down, upper rows will be selected first, and the
     * lower rows.
     */
    Vertical {
        override fun isSelected(
            bounds: Rect,
            start: PxPosition,
            end: PxPosition
        ): Boolean {
            // When the end of the selection is above the top of the composable, the composable is outside
            // of the selection range.
            if (end.y.value < bounds.top) return false

            // When the end of the selection is on the left of the composable, and not below the bottom
            // of composable, the composable is outside of the selection range.
            if (end.x.value < bounds.left && end.y.value < bounds.bottom) return false

            // When the start of the selection is below the bottom of the composable, the composable is
            // outside of the selection range.
            if (start.y.value >= bounds.bottom) return false

            // When the start of the selection is on the right of the composable, and not above the top
            // of the composable, the composable is outside of the selection range.
            if (start.x.value >= bounds.right && start.y.value >= bounds.top) return false

            return true
        }

        override fun areHandlesCrossed(
            bounds: Rect,
            start: PxPosition,
            end: PxPosition
        ): Boolean {
            if (start.y.value >= bounds.top && start.y.value < bounds.bottom &&
                end.y.value >= bounds.top && end.y.value < bounds.bottom
            ) {
                // When the start and end of the selection are in the same row of widgets, check if
                // x coordinates of the start and end are crossed each other.
                return start.x > end.x
            } else {
                // When the start and end of the selection are not in the same row of widgets, check
                // if y coordinates of the start and end are crossed each other.
                return start.y > end.y
            }
        }
    },

    /**
     * When selection handles are dragged across composables, selection extends by column, for example,
     * when the end selection handle is dragged to the right, left columns will be selected first,
     * and the right rows.
     */
    Horizontal {
        override fun isSelected(
            bounds: Rect,
            start: PxPosition,
            end: PxPosition
        ): Boolean {
            // When the end of the selection is on the left of the composable, the composable is outside of
            // the selection range.
            if (end.x.value < bounds.left) return false

            // When the end of the selection is on the top of the composable, and the not on the right
            // of the composable, the composable is outside of the selection range.
            if (end.y.value < bounds.top && end.x.value < bounds.right) return false

            // When the start of the selection is on the right of the composable, the composable is outside
            // of the selection range.
            if (start.x.value >= bounds.right) return false

            // When the start of the selection is below the composable, and not on the left of the
            // composable, the composable is outside of the selection range.
            if (start.y.value >= bounds.bottom && start.x.value >= bounds.left) return false

            return true
        }

        override fun areHandlesCrossed(
            bounds: Rect,
            start: PxPosition,
            end: PxPosition
        ): Boolean {
            if (start.x.value >= bounds.left && start.x.value < bounds.right &&
                end.x.value >= bounds.left && end.x.value < bounds.right
            ) {
                // When the start and end of the selection are in the same column of widgets,
                // check if y coordinates of the start and end are crossed each other.
                return start.y > end.y
            } else {
                // When the start and end of the selection are not in the same column of widgets,
                // check if x coordinates of the start and end are crossed each other.
                return start.x > end.x
            }
        }
    };

    /**
     * Decides if Composable which has [bounds], should be accepted by the selection and
     * change its selected state for a selection that starts at [start] and ends at [end].
     *
     * @param bounds Composable bounds of the widget to be checked.
     * @param start The start coordinates of the selection, in SelectionContainer range.
     * @param end The end coordinates of the selection, in SelectionContainer range.
     */
    internal abstract fun isSelected(
        bounds: Rect,
        start: PxPosition,
        end: PxPosition
    ): Boolean

    /**
     * Decides if the [start] and [end] handles of the selection are crossed around a Composable
     * which has [bounds].
     * When the end handle is visually crossed the start handle, return true.
     *
     * @param bounds Composable bounds of the widget to be checked.
     * @param start The start coordinates of the selection, in SelectionContainer range.
     * @param end The end coordinates of the selection, in SelectionContainer range.
     */
    internal abstract fun areHandlesCrossed(
        bounds: Rect,
        start: PxPosition,
        end: PxPosition
    ): Boolean
}

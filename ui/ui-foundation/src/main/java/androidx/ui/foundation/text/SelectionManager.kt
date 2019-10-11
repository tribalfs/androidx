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

package androidx.ui.foundation.text

import androidx.ui.core.LayoutCoordinates
import androidx.ui.core.PxPosition
import androidx.ui.core.gesture.DragObserver
import androidx.ui.core.gesture.LongPressDragObserver
import androidx.ui.core.px
import androidx.ui.core.selection.Selection
import androidx.ui.core.selection.SelectionMode
import androidx.ui.core.selection.TextSelectionHandler

/**
 * A bridge class between user interaction to the text composables for text selection.
 */
internal class SelectionManager(private val selectionRegistrar: SelectionRegistrarImpl) {
    /**
     * The current selection.
     */
    var selection: Selection? = null

    /**
     * The manager will invoke this every time it comes to the conclusion that the selection should
     * change. The expectation is that this callback will end up causing `setSelection` to get
     * called. This is what makes this a "controlled component".
     */
    var onSelectionChange: (Selection?) -> Unit = {}

    /**
     * Selection mode to be used. Default value is Vertical.
     */
    var mode: SelectionMode = SelectionMode.Vertical

    /**
     * Layout Coordinates of the selection container.
     */
    lateinit var containerLayoutCoordinates: LayoutCoordinates

    /**
     * The beginning position of the drag gesture. Every time a new drag gesture starts, it wil be
     * recalculated.
     */
    private var dragBeginPosition = PxPosition.Origin

    /**
     * The total distance being dragged of the drag gesture. Every time a new drag gesture starts,
     * it will be zeroed out.
     */
    private var dragTotalDistance = PxPosition.Origin

    /**
     * A flag to check if the selection start or end handle is being dragged.
     * If this value is true, then onPress will not select any text.
     * This value will be set to true when either handle is being dragged, and be reset to false
     * when the dragging is stopped.
     */
    private var draggingHandle = false

    /**
     * Iterates over the handlers, gets the selection for each Composable, and merges all the
     * returned [Selection]s.
     *
     * @param startPosition [PxPosition] for the start of the selection
     * @param endPosition [PxPosition] for the end of the selection
     * @param wordSelectIfCollapsed This flag is ignored if the selection offsets anchors point
     * different location. If the selection anchors point the same location and this is true, the
     * result selection will be adjusted to word boundary. Otherwise, the selection will be adjusted
     * to keep single character selected.
     * @param selection initial selection to start with
     *
     * @return [Selection] object which is constructed by combining all Composables that are
     * selected.
     */
    private fun mergeSelections(
        startPosition: PxPosition,
        endPosition: PxPosition,
        wordSelectIfCollapsed: Boolean,
        selection: Selection? = null
    ): Selection? {
        val handlers = selectionRegistrar.handlers
        return handlers.fold(selection) { mergedSelection: Selection?,
                                          handler: TextSelectionHandler ->
            merge(
                mergedSelection,
                handler.getSelection(
                    startPosition = startPosition,
                    endPosition = endPosition,
                    containerLayoutCoordinates = containerLayoutCoordinates,
                    mode = mode,
                    wordSelectIfCollapsed = wordSelectIfCollapsed
                )
            )
        }
    }

    // This is for PressGestureDetector to cancel the selection.
    fun onRelease() {
        // Call mergeSelections with an out of boundary input to inform all text widgets to
        // cancel their individual selection.
        mergeSelections(
            startPosition = PxPosition(-1.px, -1.px),
            endPosition = PxPosition(-1.px, -1.px),
            wordSelectIfCollapsed = false)
        onSelectionChange(null)
    }

    val longPressDragObserver =
        object : LongPressDragObserver {
            override fun onLongPress(pxPosition: PxPosition) {
                if (draggingHandle) return
                val selection = mergeSelections(
                    startPosition = pxPosition,
                    endPosition = pxPosition,
                    wordSelectIfCollapsed = true
                )
                onSelectionChange(selection)
                dragBeginPosition = pxPosition
            }

            override fun onDragStart() {
                super.onDragStart()

                // Zero out the total distance that being dragged.
                dragTotalDistance = PxPosition.Origin
            }

            override fun onDrag(dragDistance: PxPosition): PxPosition {
                dragTotalDistance += dragDistance

                val selection = mergeSelections(
                    startPosition = dragBeginPosition,
                    endPosition = dragBeginPosition + dragTotalDistance,
                    wordSelectIfCollapsed = true
                )
                onSelectionChange(selection)
                return dragDistance
            }
        }

    /**
     * Adjust coordinates for given text offset.
     *
     * Currently [android.text.Layout.getLineBottom] returns y coordinates of the next
     * line's top offset, which is not included in current line's hit area. To be able to
     * hit current line, move up this y coordinates by 1 pixel.
     */
    private fun getAdjustedCoordinates(position: PxPosition): PxPosition {
        return PxPosition(position.x, position.y - 1.px)
    }

    fun handleDragObserver(isStartHandle: Boolean): DragObserver {
        return object : DragObserver {
            override fun onStart(downPosition: PxPosition) {
                val selection = selection!!
                // The LayoutCoordinates of the composable where the drag gesture should begin. This
                // is used to convert the position of the beginning of the drag gesture from the
                // composable coordinates to selection container coordinates.
                val beginLayoutCoordinates = if (isStartHandle) {
                    selection.start.layoutCoordinates!!
                } else {
                    selection.end.layoutCoordinates!!
                }

                // The position of the character where the drag gesture should begin. This is in
                // the composable coordinates.
                val beginCoordinates = getAdjustedCoordinates(
                    if (isStartHandle) {
                        selection.start.coordinates
                    } else {
                        selection.end.coordinates
                    }
                )

                // Convert the position where drag gesture begins from composable coordinates to
                // selection container coordinates.
                dragBeginPosition = containerLayoutCoordinates.childToLocal(
                    beginLayoutCoordinates,
                    beginCoordinates
                )

                // Zero out the total distance that being dragged.
                dragTotalDistance = PxPosition.Origin
                draggingHandle = true
            }

            override fun onDrag(dragDistance: PxPosition): PxPosition {
                val selection = selection!!
                dragTotalDistance += dragDistance

                val currentStart = if (isStartHandle) {
                    dragBeginPosition + dragTotalDistance
                } else {
                    containerLayoutCoordinates.childToLocal(
                        selection.start.layoutCoordinates!!,
                        getAdjustedCoordinates(selection.start.coordinates)
                    )
                }

                val currentEnd = if (isStartHandle) {
                    containerLayoutCoordinates.childToLocal(
                        selection.end.layoutCoordinates!!,
                        getAdjustedCoordinates(selection.end.coordinates)
                    )
                } else {
                    dragBeginPosition + dragTotalDistance
                }

                val finalSelection = mergeSelections(
                    startPosition = currentStart,
                    endPosition = currentEnd,
                    wordSelectIfCollapsed = false,
                    selection = selection
                )
                onSelectionChange(finalSelection)
                return dragDistance
            }

            override fun onStop(velocity: PxPosition) {
                super.onStop(velocity)
                draggingHandle = false
            }
        }
    }
}

private fun merge(lhs: Selection?, rhs: Selection?): Selection? {
    return lhs?.merge(rhs) ?: rhs
}

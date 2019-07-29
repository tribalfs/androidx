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

import androidx.ui.core.PxPosition
import androidx.ui.core.px
import androidx.ui.text.TextSelection
import androidx.ui.text.TextPainter
import kotlin.math.max

/**
 *  This class processes the text selection for given selection start and end positions, and
 *  selection mode.
 */
internal class TextSelectionProcessor(
    /** The positions of the start and end of the selection in Text widget coordinate system.*/
    val selectionCoordinates: Pair<PxPosition, PxPosition>,
    /** The mode of selection. */
    val mode: SelectionMode,
    /** The lambda contains certain behavior when selection changes. Currently this is for changing
     * the selection used for drawing in Text widget. */
    var onSelectionChange: (TextSelection?) -> Unit = {},
    /** The TextPainter object from Text widget. */
    val textPainter: TextPainter
) {
    /**
     * The coordinates of the graphical position for selection start character offset.
     *
     * This graphical position is the point at the left bottom corner for LTR
     * character, or right bottom corner for RTL character.
     *
     * This coordinates is in child widget coordinates system.
     */
    internal var startCoordinates: PxPosition = PxPosition.Origin
    /**
     * The coordinates of the graphical position for selection end character offset.
     *
     * This graphical position is the point at the left bottom corner for LTR
     * character, or right bottom corner for RTL character.
     *
     * This coordinates is in child widget coordinates system.
     */
    internal var endCoordinates: PxPosition = PxPosition.Origin
    /**
     * A flag to check if the text widget contains the whole selection's start.
     */
    internal var containsWholeSelectionStart = false
    /**
     * A flag to check if the text widget contains the whole selection's end.
     */
    internal var containsWholeSelectionEnd = false
    /**
     *  A flag to check if the text widget is selected.
     */
    internal var isSelected = false

    /** The length of the text in text widget. */
    private val length = textPainter.text?.let { it.text.length } ?: 0

    init {
        processTextSelection()
    }

    /**
     * Process text selection.
     */
    private fun processTextSelection() {
        val startPx = selectionCoordinates.first
        val endPx = selectionCoordinates.second

        if (!mode.isSelected(textPainter, start = startPx, end = endPx)) {
            onSelectionChange(null)
            return
        }

        isSelected = true
        var (textSelectionStart, containsWholeSelectionStart) =
            getSelectionBorder(textPainter, startPx, true)
        var (textSelectionEnd, containsWholeSelectionEnd) =
            getSelectionBorder(textPainter, endPx, false)

        if (textSelectionStart == textSelectionEnd) {
            val wordBoundary = textPainter.getWordBoundary(textSelectionStart)
            textSelectionStart = wordBoundary.start
            textSelectionEnd = wordBoundary.end
        }

        onSelectionChange(TextSelection(textSelectionStart, textSelectionEnd))

        startCoordinates = getSelectionHandleCoordinates(textSelectionStart)
        endCoordinates = getSelectionHandleCoordinates(textSelectionEnd)

        this.containsWholeSelectionStart = containsWholeSelectionStart
        this.containsWholeSelectionEnd = containsWholeSelectionEnd
    }

    /**
     * This function gets the border of the text selection. Border means either start or end of the
     * selection.
     */
    private fun getSelectionBorder(
        textPainter: TextPainter,
        // This position is in Text widget coordinate system.
        position: PxPosition,
        isStart: Boolean
    ): Pair<Int, Boolean> {
        // The character offset of the border of selection. The default value is set to the
        // beginning of the text widget for the start border, and the very last character offset
        // of the text widget for the end border. If the widget contains the whole selection's
        // border, this value will be reset.
        var selectionBorder = if (isStart) 0 else max(length - 1, 0)
        // Flag to check if the widget contains the whole selection's border.
        var containsWholeSelectionBorder = false

        val top = 0.px
        val bottom = textPainter.height.px
        val left = 0.px
        val right = textPainter.width.px
        // If the current text widget contains the whole selection's border, then find the exact
        // character offset of the border, and the flag checking if the widget contains the whole
        // selection's border will be set to true.
        if (position.x >= left &&
            position.x < right &&
            position.y >= top &&
            position.y < bottom
        ) {
            // Constrain the character offset of the selection border to be within the text range
            // of the current widget.
            val constrainedSelectionBorderOffset =
                textPainter.getOffsetForPosition(position).coerceIn(0, length - 1)
            selectionBorder = constrainedSelectionBorderOffset
            containsWholeSelectionBorder = true
        }
        return Pair(selectionBorder, containsWholeSelectionBorder)
    }

    private fun getSelectionHandleCoordinates(offset: Int): PxPosition {
        val left = textPainter.getPrimaryHorizontal(offset)

        val line = textPainter.getLineForOffset(offset)
        val bottom = textPainter.getLineBottom(line)

        return PxPosition(left.px, bottom.px)
    }
}

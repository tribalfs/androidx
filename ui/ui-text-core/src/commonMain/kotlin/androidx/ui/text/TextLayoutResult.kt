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

package androidx.ui.text

import androidx.ui.core.Constraints
import androidx.ui.core.LayoutDirection
import androidx.ui.geometry.Offset
import androidx.ui.geometry.Rect
import androidx.ui.graphics.Path
import androidx.ui.text.font.Font
import androidx.ui.text.style.ResolvedTextDirection
import androidx.ui.text.style.TextOverflow
import androidx.ui.unit.Density
import androidx.ui.unit.IntSize

/**
 * The data class which holds the set of parameters of the text layout computation.
 */
data class TextLayoutInput(
    /**
     * The text used for computing text layout.
     */
    val text: AnnotatedString,

    /**
     * The text layout used for computing this text layout.
     */
    val style: TextStyle,

    /**
     * A list of [Placeholder]s inserted into text layout that reserves space to embed icons or
     * custom emojis. A list of bounding boxes will be returned in
     * [TextLayoutResult.placeholderRects] that corresponds to this input.
     *
     * @see TextLayoutResult.placeholderRects
     * @see MultiParagraph
     * @see MultiParagraphIntrinsics
     */
    val placeholders: List<AnnotatedString.Range<Placeholder>>,

    /**
     * The maxLines param used for computing this text layout.
     */
    val maxLines: Int,

    /**
     * The maxLines param used for computing this text layout.
     */
    val softWrap: Boolean,

    /**
     * The overflow param used for computing this text layout
     */
    val overflow: TextOverflow,

    /**
     * The density param used for computing this text layout.
     */
    val density: Density,

    /**
     * The layout direction used for computing this text layout.
     */
    val layoutDirection: LayoutDirection,

    /**
     * The font resource loader used for computing this text layout.
     */
    val resourceLoader: Font.ResourceLoader,

    /**
     * The minimum width provided while calculating this text layout.
     */
    val constraints: Constraints
)

/**
 * The data class which holds text layout result.
 */
data class TextLayoutResult internal constructor(
    /**
     * The parameters used for computing this text layout result.
     */
    val layoutInput: TextLayoutInput,

    /**
     * The multi paragraph object.
     *
     * This is the result of the text layout computation.
     * This is expected to be used only for drawing from TextDelegate class.
     */
    internal val multiParagraph: MultiParagraph,

    /**
     * The amount of space required to paint this text in Int.
     */
    val size: IntSize
) {
    /**
     * The distance from the top to the alphabetic baseline of the first line.
     */
    val firstBaseline: Float = multiParagraph.firstBaseline

    /**
     * The distance from the top to the alphabetic baseline of the last line.
     */
    val lastBaseline: Float = multiParagraph.lastBaseline

    /**
     * Returns true if the text is too tall and couldn't fit with given height.
     */
    val didOverflowHeight: Boolean get() = multiParagraph.didExceedMaxLines

    /**
     * Returns true if the text is too wide and couldn't fit with given width.
     */
    val didOverflowWidth: Boolean get() = size.width < multiParagraph.width

    /**
     * Returns true if either vertical overflow or horizontal overflow happens.
     */
    val hasVisualOverflow: Boolean get() = didOverflowWidth || didOverflowHeight

    /**
     * Returns a list of bounding boxes that is reserved for [TextLayoutInput.placeholders].
     * Each [Rect] in this list corresponds to the [Placeholder] passed to
     * [TextLayoutInput.placeholders] and it will have the height and width specified in the
     * [Placeholder]. It's guaranteed that [TextLayoutInput.placeholders] and
     * [TextLayoutResult.placeholderRects] will have same length and order.
     *
     * @see TextLayoutInput.placeholders
     * @see Placeholder
     */
    val placeholderRects: List<Rect?> = multiParagraph.placeholderRects

    /**
     * Returns the end offset of the given line, inclusive.
     *
     * @param lineIndex the line number
     * @return the start offset of the line
     */
    fun getLineStart(lineIndex: Int): Int = multiParagraph.getLineStart(lineIndex)

    /**
     * Returns the end offset of the given line, exclusive.
     *
     * @param lineIndex the line number
     * @return the end offset of the line.
     */
    fun getLineEnd(lineIndex: Int): Int = multiParagraph.getLineEnd(lineIndex)

    /**
     * Returns the top y coordinate of the given line.
     *
     * @param lineIndex the line number
     * @return the line top y coordinate
     */
    fun getLineTop(lineIndex: Int): Float = multiParagraph.getLineTop(lineIndex)

    /**
     * Returns the bottom y coordinate of the given line.
     *
     * @param lineIndex the line number
     * @return the line bottom y coordinate
     */
    fun getLineBottom(lineIndex: Int): Float = multiParagraph.getLineBottom(lineIndex)

    /**
     * Returns the line number on which the specified text offset appears.
     *
     * If you ask for a position before 0, you get 0; if you ask for a position
     * beyond the end of the text, you get the last line.
     *
     * @param offset a character offset
     * @return the 0 origin line number.
     */
    fun getLineForOffset(offset: Int): Int = multiParagraph.getLineForOffset(offset)

    /**
     * Get the horizontal position for the specified text [offset].
     *
     * Returns the relative distance from the text starting offset. For example, if the paragraph
     * direction is Left-to-Right, this function returns positive value as a distance from the
     * left-most edge. If the paragraph direction is Right-to-Left, this function returns negative
     * value as a distance from the right-most edge.
     *
     * [usePrimaryDirection] argument is taken into account only when the offset is in the BiDi
     * directional transition point. [usePrimaryDirection] is true means use the primary
     * direction run's coordinate, and use the secondary direction's run's coordinate if false.
     *
     * @param offset a character offset
     * @param usePrimaryDirection true for using the primary run's coordinate if the given
     * offset is in the BiDi directional transition point.
     * @return the relative distance from the text starting edge.
     * @see MultiParagraph.getHorizontalPosition
     */
    fun getHorizontalPosition(offset: Int, usePrimaryDirection: Boolean): Float =
        multiParagraph.getHorizontalPosition(offset, usePrimaryDirection)

    /**
     * Get the text direction of the paragraph containing the given offset.
     *
     * @param offset a character offset
     * @return the paragraph direction
     */
    fun getParagraphDirection(offset: Int): ResolvedTextDirection =
        multiParagraph.getParagraphDirection(offset)

    /**
     * Get the text direction of the resolved BiDi run that the character at the given offset
     * associated with.
     *
     * @param offset a character offset
     * @return the direction of the BiDi run of the given character offset.
     */
    fun getBidiRunDirection(offset: Int): ResolvedTextDirection =
        multiParagraph.getBidiRunDirection(offset)

    /**
     *  Returns the character offset closest to the given graphical position.
     *
     *  @param position a graphical position in this text layout
     *  @return a character offset that is closest to the given graphical position.
     */
    fun getOffsetForPosition(position: Offset): Int =
        multiParagraph.getOffsetForPosition(position)

    /**
     * Returns the bounding box of the character for given character offset.
     *
     * @param offset a character offset
     * @return a bounding box for the character in pixels.
     */
    fun getBoundingBox(offset: Int): Rect = multiParagraph.getBoundingBox(offset)

    /**
     * Returns the text range of the word at the given character offset.
     *
     * Characters not part of a word, such as spaces, symbols, and punctuation, have word breaks on
     * both sides. In such cases, this method will return a text range that contains the given
     * character offset.
     *
     * Word boundaries are defined more precisely in Unicode Standard Annex #29
     * <http://www.unicode.org/reports/tr29/#Word_Boundaries>.
     */
    fun getWordBoundary(offset: Int): TextRange = multiParagraph.getWordBoundary(offset)

    /**
     * Returns the rectangle of the cursor area
     *
     * @param offset An character offset of the cursor
     * @return a rectangle of cursor region
     */
    fun getCursorRect(offset: Int): Rect = multiParagraph.getCursorRect(offset)

    /**
     * Returns path that enclose the given text range.
     *
     * @param start an inclusive start character offset
     * @param end an exclusive end character offset
     * @return a drawing path
     */
    fun getPathForRange(start: Int, end: Int): Path = multiParagraph.getPathForRange(start, end)
}
/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.compose.foundation.text.selection

import androidx.compose.foundation.text.TextLayoutResultProxy
import androidx.compose.foundation.text.findFollowingBreak
import androidx.compose.foundation.text.findPrecedingBreak
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.ResolvedTextDirection
import kotlin.math.max
import kotlin.math.min

/**
 * This utility class implements many selection-related operations on text (including basic
 * cursor movements and deletions) and combines them, taking into account how the text was
 * rendered. So, for example, [moveCursorToLineEnd] moves it to the visual line end.
 *
 * For many of these operations, it's particularly important to keep the difference between
 * selection start and selection end. In some systems, they are called "anchor" and "caret"
 * respectively. For example, for selection from scratch, after [moveCursorLeftByWord]
 * [moveCursorRight] will move the left side of the selection, but after [moveCursorRightByWord]
 * the right one.
 *
 * To use it in scope of text fields see [TextFieldPreparedSelection]
 */
internal abstract class BaseTextPreparedSelection<T : BaseTextPreparedSelection<T>>(
    val originalText: AnnotatedString,
    val originalSelection: TextRange,
    val layoutResult: TextLayoutResult?,
    val offsetMapping: OffsetMapping
) {
    var selection = originalSelection

    var annotatedString = originalText
    protected val text
        get() = annotatedString.text

    @Suppress("UNCHECKED_CAST")
    inline fun <U> U.apply(block: U.() -> Unit): T {
        block()
        return this as T
    }

    fun setCursor(offset: Int) = apply {
        setSelection(offset, offset)
    }

    fun setSelection(start: Int, end: Int) = apply {
        selection = TextRange(start, end)
    }

    fun selectAll() = apply {
        setSelection(0, text.length)
    }

    fun deselect() = apply {
        setCursor(selection.end)
    }

    fun moveCursorLeft() = apply {
        if (isLtr()) {
            moveCursorPrev()
        } else {
            moveCursorNext()
        }
    }

    fun moveCursorRight() = apply {
        if (isLtr()) {
            moveCursorNext()
        } else {
            moveCursorPrev()
        }
    }

    /**
     * If there is already a selection, collapse it to the left side. Otherwise, execute [or]
     */
    fun collapseLeftOr(or: T.() -> Unit) = apply {
        if (selection.collapsed) {
            @Suppress("UNCHECKED_CAST")
            or(this as T)
        } else {
            if (isLtr()) {
                setCursor(selection.min)
            } else {
                setCursor(selection.max)
            }
        }
    }

    /**
     * If there is already a selection, collapse it to the right side. Otherwise, execute [or]
     */
    fun collapseRightOr(or: T.() -> Unit) = apply {
        if (selection.collapsed) {
            @Suppress("UNCHECKED_CAST")
            or(this as T)
        } else {
            if (isLtr()) {
                setCursor(selection.max)
            } else {
                setCursor(selection.min)
            }
        }
    }

    fun moveCursorPrev() = apply {
        val prev = annotatedString.text.findPrecedingBreak(selection.end)
        if (prev != -1) setCursor(prev)
    }

    fun moveCursorNext() = apply {
        val next = annotatedString.text.findFollowingBreak(selection.end)
        if (next != -1) setCursor(next)
    }

    fun moveCursorToHome() = apply {
        setCursor(0)
    }

    fun moveCursorToEnd() = apply {
        setCursor(text.length)
    }

    fun moveCursorLeftByWord() = apply {
        if (isLtr()) {
            moveCursorPrevByWord()
        } else {
            moveCursorNextByWord()
        }
    }

    fun moveCursorRightByWord() = apply {
        if (isLtr()) {
            moveCursorNextByWord()
        } else {
            moveCursorPrevByWord()
        }
    }

    fun moveCursorNextByWord() = apply {
        layoutResult?.getNextWordOffset()?.let { setCursor(it) }
    }

    fun moveCursorPrevByWord() = apply {
        layoutResult?.getPrevWordOffset()?.let { setCursor(it) }
    }

    fun moveCursorPrevByParagraph() = apply {
        setCursor(getParagraphStart())
    }

    fun moveCursorNextByParagraph() = apply {
        setCursor(getParagraphEnd())
    }

    fun moveCursorUpByLine() = apply {
        layoutResult?.jumpByLinesOffset(-1)?.let { setCursor(it) }
    }

    fun moveCursorDownByLine() = apply {
        layoutResult?.jumpByLinesOffset(1)?.let { setCursor(it) }
    }

    fun moveCursorToLineStart() = apply {
        layoutResult?.getLineStartByOffset()?.let { setCursor(it) }
    }

    fun moveCursorToLineEnd() = apply {
        layoutResult?.getLineEndByOffset()?.let { setCursor(it) }
    }

    fun moveCursorToLineLeftSide() = apply {
        if (isLtr()) {
            moveCursorToLineStart()
        } else {
            moveCursorToLineEnd()
        }
    }

    fun moveCursorToLineRightSide() = apply {
        if (isLtr()) {
            moveCursorToLineEnd()
        } else {
            moveCursorToLineStart()
        }
    }

    // it selects a text from the original selection start to a current selection end
    fun selectMovement() = apply {
        selection = TextRange(originalSelection.start, selection.end)
    }

    // delete currently selected text and update [selection] and [annotatedString]
    fun deleteSelected() = apply {
        val maxChars = text.length
        val beforeSelection =
            annotatedString.subSequence(max(0, selection.min - maxChars), selection.min)
        val afterSelection =
            annotatedString.subSequence(selection.max, min(selection.max + maxChars, text.length))
        annotatedString = beforeSelection + afterSelection
        setCursor(selection.min)
    }

    private fun isLtr(): Boolean {
        val direction = layoutResult?.getBidiRunDirection(selection.end)
        return direction != ResolvedTextDirection.Rtl
    }

    private fun TextLayoutResult.getNextWordOffset(
        currentOffset: Int = transformedEndOffset()
    ): Int {
        if (currentOffset >= originalText.length) {
            return originalText.length
        }
        val currentWord = getWordBoundary(charOffset(currentOffset))
        return if (currentWord.end <= currentOffset) {
            getNextWordOffset(currentOffset + 1)
        } else {
            offsetMapping.transformedToOriginal(currentWord.end)
        }
    }

    private fun TextLayoutResult.getPrevWordOffset(
        currentOffset: Int = transformedEndOffset()
    ): Int {
        if (currentOffset < 0) {
            return 0
        }
        val currentWord = getWordBoundary(charOffset(currentOffset))
        return if (currentWord.start >= currentOffset) {
            getPrevWordOffset(currentOffset - 1)
        } else {
            offsetMapping.transformedToOriginal(currentWord.start)
        }
    }

    private fun TextLayoutResult.getLineStartByOffset(
        currentOffset: Int = transformedMinOffset()
    ): Int {
        val currentLine = getLineForOffset(currentOffset)
        return offsetMapping.transformedToOriginal(getLineStart(currentLine))
    }

    private fun TextLayoutResult.getLineEndByOffset(
        currentOffset: Int = transformedMaxOffset()
    ): Int {
        val currentLine = getLineForOffset(currentOffset)
        return offsetMapping.transformedToOriginal(getLineEnd(currentLine, true))
    }

    private fun TextLayoutResult.jumpByLinesOffset(linesAmount: Int): Int {
        val currentOffset = transformedEndOffset()

        val newLine = getLineForOffset(currentOffset) + linesAmount
        when {
            newLine < 0 -> {
                return 0
            }
            newLine >= lineCount -> {
                return text.length
            }
        }

        val y = getLineBottom(newLine) - 1
        val x = getCursorRect(currentOffset).left.also {
            if ((isLtr() && it >= getLineRight(newLine)) ||
                (!isLtr() && it <= getLineLeft(newLine))
            ) {
                return getLineEnd(newLine, true)
            }
        }

        val newOffset = getOffsetForPosition(Offset(x, y)).let {
            offsetMapping.transformedToOriginal(it)
        }

        return newOffset
    }

    private fun transformedEndOffset(): Int {
        return offsetMapping.originalToTransformed(originalSelection.end)
    }

    private fun transformedMinOffset(): Int {
        return offsetMapping.originalToTransformed(originalSelection.min)
    }

    private fun transformedMaxOffset(): Int {
        return offsetMapping.originalToTransformed(originalSelection.max)
    }

    private fun charOffset(offset: Int) =
        offset.coerceAtMost(originalText.length - 1)

    private fun getParagraphStart(): Int {
        var index = selection.min
        if (index > 0 && text[index - 1] == '\n') {
            index--
        }
        while (index > 0) {
            if (text[index - 1] == '\n') {
                return index
            }
            index--
        }
        return 0
    }

    private fun getParagraphEnd(): Int {
        var index = selection.max
        if (text[index] == '\n') {
            index++
        }
        while (index < text.length - 1) {
            if (text[index] == '\n') {
                return index
            }
            index++
        }
        return text.length
    }
}

internal class TextFieldPreparedSelection(
    val currentValue: TextFieldValue,
    offsetMapping: OffsetMapping = OffsetMapping.Identity,
    val layoutResultProxy: TextLayoutResultProxy?
) : BaseTextPreparedSelection<TextFieldPreparedSelection>(
    originalText = currentValue.annotatedString,
    originalSelection = currentValue.selection,
    offsetMapping = offsetMapping,
    layoutResult = layoutResultProxy?.value
) {
    val value
        get() = currentValue.copy(
            annotatedString = annotatedString,
            selection = selection
        )

    fun deleteIfSelectedOr(or: TextFieldPreparedSelection.() -> Unit) = apply {
        if (selection.collapsed) {
            or(this)
        } else {
            deleteSelected()
        }
    }

    fun moveCursorUpByPage() = apply {
        layoutResultProxy?.jumpByPagesOffset(-1)?.let { setCursor(it) }
    }

    fun moveCursorDownByPage() = apply {
        layoutResultProxy?.jumpByPagesOffset(1)?.let { setCursor(it) }
    }

    /**
     * Returns a cursor position after jumping back or forth by [pagesAmount] number of pages,
     * where `page` is the visible amount of space in the text field
     */
    private fun TextLayoutResultProxy.jumpByPagesOffset(pagesAmount: Int): Int {
        val visibleInnerTextFieldRect = innerTextFieldCoordinates?.let { inner ->
            decorationBoxCoordinates?.localBoundingBoxOf(inner)
        } ?: Rect.Zero
        val currentOffset = offsetMapping.originalToTransformed(currentValue.selection.end)
        val currentPos = value.getCursorRect(currentOffset)
        val x = currentPos.left
        val y = currentPos.top + visibleInnerTextFieldRect.size.height * pagesAmount
        return offsetMapping.transformedToOriginal(
            value.getOffsetForPosition(Offset(x, y))
        )
    }
}
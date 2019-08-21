/*
 * Copyright 2018 The Android Open Source Project
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
package androidx.text

import android.graphics.Canvas
import android.graphics.Path
import android.os.Build
import android.text.Layout
import android.text.Spanned
import android.text.TextDirectionHeuristic
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP
import androidx.text.LayoutCompat.ALIGN_CENTER
import androidx.text.LayoutCompat.ALIGN_LEFT
import androidx.text.LayoutCompat.ALIGN_NORMAL
import androidx.text.LayoutCompat.ALIGN_OPPOSITE
import androidx.text.LayoutCompat.ALIGN_RIGHT
import androidx.text.LayoutCompat.BreakStrategy
import androidx.text.LayoutCompat.DEFAULT_ALIGNMENT
import androidx.text.LayoutCompat.DEFAULT_BREAK_STRATEGY
import androidx.text.LayoutCompat.DEFAULT_HYPHENATION_FREQUENCY
import androidx.text.LayoutCompat.DEFAULT_JUSTIFICATION_MODE
import androidx.text.LayoutCompat.DEFAULT_LINESPACING_EXTRA
import androidx.text.LayoutCompat.DEFAULT_LINESPACING_MULTIPLIER
import androidx.text.LayoutCompat.DEFAULT_TEXT_DIRECTION
import androidx.text.LayoutCompat.HyphenationFrequency
import androidx.text.LayoutCompat.JustificationMode
import androidx.text.LayoutCompat.TEXT_DIRECTION_ANY_RTL_LTR
import androidx.text.LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_LTR
import androidx.text.LayoutCompat.TEXT_DIRECTION_FIRST_STRONG_RTL
import androidx.text.LayoutCompat.TEXT_DIRECTION_LOCALE
import androidx.text.LayoutCompat.TEXT_DIRECTION_LTR
import androidx.text.LayoutCompat.TEXT_DIRECTION_RTL
import androidx.text.LayoutCompat.TextDirection
import androidx.text.LayoutCompat.TextLayoutAlignment
import androidx.text.style.BaselineShiftSpan
import java.text.BreakIterator
import java.util.PriorityQueue
import kotlin.math.max

/**
 * Wrapper for Static Text Layout classes.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
class TextLayout constructor(
    charSequence: CharSequence,
    width: Float = 0.0f,
    textPaint: TextPaint,
    @TextLayoutAlignment alignment: Int = DEFAULT_ALIGNMENT,
    ellipsize: TextUtils.TruncateAt? = null,
    @TextDirection textDirectionHeuristic: Int = DEFAULT_TEXT_DIRECTION,
    lineSpacingMultiplier: Float = DEFAULT_LINESPACING_MULTIPLIER,
    lineSpacingExtra: Float = DEFAULT_LINESPACING_EXTRA,
    includePadding: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    @BreakStrategy breakStrategy: Int = DEFAULT_BREAK_STRATEGY,
    @HyphenationFrequency hyphenationFrequency: Int = DEFAULT_HYPHENATION_FREQUENCY,
    @JustificationMode justificationMode: Int = DEFAULT_JUSTIFICATION_MODE,
    leftIndents: IntArray? = null,
    rightIndents: IntArray? = null
) {
    val maxIntrinsicWidth: Float
    val layout: Layout
    val didExceedMaxLines: Boolean

    init {
        val start = 0
        val end = charSequence.length
        val frameworkTextDir = getTextDirectionHeuristic(textDirectionHeuristic)
        val boringMetrics = BoringLayoutCompat.isBoring(charSequence, textPaint, frameworkTextDir)

        // TODO(haoyuchang): we didn't pass the TextDirection to Layout.getDesiredWidth(), check if
        //  there is any behavior difference from
        //  Layout.getWidthWithLimits(charSequence, start, end, paint, dir)
        maxIntrinsicWidth = boringMetrics?.width?.toFloat()
            ?: Layout.getDesiredWidth(charSequence, start, end, textPaint)

        val finalWidth = width.toInt()
        val ellipsizeWidth = finalWidth
        val frameworkAlignment = TextAlignmentAdapter.get(alignment)

        // BoringLayout won't adjust line height for baselineShift,
        // use StaticLayout for those spans.
        val hasBaselineShiftSpans = if (charSequence is Spanned) {
            // nextSpanTransition returns limit if there isn't any span.
            charSequence.nextSpanTransition(-1, end, BaselineShiftSpan::class.java) < end
        } else {
            false
        }

        layout = if (boringMetrics != null && maxIntrinsicWidth <= width &&
            !hasBaselineShiftSpans) {
            BoringLayoutCompat.Builder(
                charSequence,
                textPaint,
                finalWidth,
                boringMetrics
            )
                .setAlignment(frameworkAlignment)
                .setIncludePad(includePadding)
                .setEllipsize(ellipsize)
                .setEllipsizedWidth(ellipsizeWidth)
                .build()
        } else {
            StaticLayoutCompat.Builder(
                charSequence,
                textPaint,
                finalWidth
            )
                .setAlignment(frameworkAlignment)
                .setTextDirection(frameworkTextDir)
                .setLineSpacingExtra(lineSpacingExtra)
                .setLineSpacingMultiplier(lineSpacingMultiplier)
                .setIncludePad(includePadding)
                .setEllipsize(ellipsize)
                .setEllipsizedWidth(ellipsizeWidth)
                .setMaxLines(maxLines)
                .setBreakStrategy(breakStrategy)
                .setHyphenationFrequency(hyphenationFrequency)
                .setJustificationMode(justificationMode)
                .setIndents(leftIndents, rightIndents)
                .build()
        }

        didExceedMaxLines = if (Build.VERSION.SDK_INT <= 25) {
            /* Before API 25, the layout.lineCount will be set to maxLines when there are more
               actual text lines in the layout.
               So in those versions, we first check if maxLines equals layout.lineCount. If true,
               we check whether the offset of the last character in Layout is the last character
               in string. */
            if (maxLines != layout.lineCount) {
                false
            } else {
                layout.getLineEnd(layout.lineCount - 1) != charSequence.length
            }
        } else {
            layout.lineCount > maxLines
        }
    }

    val lineCount: Int
        get() = layout.lineCount

    val text: CharSequence
        get() = layout.text

    fun getLineLeft(lineIndex: Int): Float = layout.getLineLeft(lineIndex)

    fun getLineRight(lineIndex: Int): Float = layout.getLineRight(lineIndex)

    fun getLineTop(line: Int): Float = layout.getLineTop(line).toFloat()

    fun getLineBottom(line: Int): Float = layout.getLineBottom(line).toFloat()

    fun getLineBaseline(line: Int): Float = layout.getLineBaseline(line).toFloat()

    fun getLineHeight(lineIndex: Int): Float =
        (layout.getLineBottom(lineIndex) - layout.getLineTop(lineIndex)).toFloat()

    fun getLineWidth(lineIndex: Int): Float = layout.getLineWidth(lineIndex)

    fun getLineForVertical(vertical: Int): Int = layout.getLineForVertical(vertical)

    fun getOffsetForHorizontal(line: Int, horizontal: Float): Int =
        layout.getOffsetForHorizontal(line, horizontal)

    fun getPrimaryHorizontal(offset: Int): Float = layout.getPrimaryHorizontal(offset)

    fun getSecondaryHorizontal(offset: Int): Float = layout.getSecondaryHorizontal(offset)

    fun getLineForOffset(offset: Int): Int = layout.getLineForOffset(offset)

    fun isRtlCharAt(offset: Int): Boolean = layout.isRtlCharAt(offset)

    fun getParagraphDirection(line: Int): Int = layout.getParagraphDirection(line)

    fun getSelectionPath(start: Int, end: Int, dest: Path) =
        layout.getSelectionPath(start, end, dest)

    /**
     * @return true if the given line is ellipsized, else false.
     */
    fun isEllipsisApplied(lineIndex: Int): Boolean = layout.getEllipsisCount(lineIndex) > 0

    fun paint(canvas: Canvas) {
        layout.draw(canvas)
    }
}

/**
 * Returns the word with the longest length. To calculate it in a performant way, it applies a heuristics where
 *  - it first finds a set of words with the longest length
 *  - finds the word with maximum width in that set
 *
 *  @hide
 */
fun minIntrinsicWidth(text: CharSequence, paint: TextPaint): Float {
    val iterator = BreakIterator.getLineInstance(paint.textLocale)
    iterator.text = CharSequenceCharacterIterator(text, 0, text.length)

    // 10 is just a random number that limits the size of the candidate list
    val heapSize = 10
    // min heap that will hold [heapSize] many words with max length
    val longestWordCandidates = PriorityQueue<Pair<Int, Int>>(
        heapSize,
        Comparator<Pair<Int, Int>> { left, right ->
            (left.second - left.first) - (right.second - right.first)
        }
    )

    var start = 0
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        if (longestWordCandidates.size < heapSize) {
            longestWordCandidates.add(Pair(start, end))
        } else {
            longestWordCandidates.peek()?.let { minPair ->
                if ((minPair.second - minPair.first) < (end - start)) {
                    longestWordCandidates.poll()
                    longestWordCandidates.add(Pair(start, end))
                }
            }
        }

        start = end
        end = iterator.next()
    }

    return longestWordCandidates.map { pair ->
        Layout.getDesiredWidth(text, pair.first, pair.second, paint)
    }.max() ?: 0f
}

@RequiresApi(api = 18)
internal fun getTextDirectionHeuristic(@TextDirection textDirectionHeuristic: Int):
        TextDirectionHeuristic {
    return when (textDirectionHeuristic) {
        TEXT_DIRECTION_LTR -> TextDirectionHeuristics.LTR
        TEXT_DIRECTION_LOCALE -> TextDirectionHeuristics.LOCALE
        TEXT_DIRECTION_RTL -> TextDirectionHeuristics.RTL
        TEXT_DIRECTION_FIRST_STRONG_RTL -> TextDirectionHeuristics.FIRSTSTRONG_RTL
        TEXT_DIRECTION_ANY_RTL_LTR -> TextDirectionHeuristics.ANYRTL_LTR
        TEXT_DIRECTION_FIRST_STRONG_LTR -> TextDirectionHeuristics.FIRSTSTRONG_LTR
        else -> TextDirectionHeuristics.FIRSTSTRONG_LTR
    }
}

/** @hide */
@RestrictTo(LIBRARY_GROUP)
object TextAlignmentAdapter {
    val ALIGN_LEFT_FRAMEWORK: Layout.Alignment
    val ALIGN_RIGHT_FRAMEWORK: Layout.Alignment

    init {
        val values = Layout.Alignment.values()
        var alignLeft = Layout.Alignment.ALIGN_NORMAL
        var alignRight = Layout.Alignment.ALIGN_NORMAL
        for (value in values) {
            if (value.name == "ALIGN_LEFT") {
                alignLeft = value
                continue
            }

            if (value.name == "ALIGN_RIGHT") {
                alignRight = value
                continue
            }
        }

        ALIGN_LEFT_FRAMEWORK = alignLeft
        ALIGN_RIGHT_FRAMEWORK = alignRight
    }

    fun get(@TextLayoutAlignment value: Int): Layout.Alignment {
        return when (value) {
            ALIGN_LEFT -> ALIGN_LEFT_FRAMEWORK
            ALIGN_RIGHT -> ALIGN_RIGHT_FRAMEWORK
            ALIGN_CENTER -> Layout.Alignment.ALIGN_CENTER
            ALIGN_OPPOSITE -> Layout.Alignment.ALIGN_OPPOSITE
            ALIGN_NORMAL -> Layout.Alignment.ALIGN_NORMAL
            else -> Layout.Alignment.ALIGN_NORMAL
        }
    }
}
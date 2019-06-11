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

package androidx.ui.painting

import androidx.annotation.VisibleForTesting
import androidx.ui.core.Constraints
import androidx.ui.core.IntPxSize
import androidx.ui.core.constrain
import androidx.ui.core.px
import androidx.ui.core.round
import androidx.ui.engine.geometry.Offset
import androidx.ui.engine.geometry.Rect
import androidx.ui.engine.geometry.Size
import androidx.ui.engine.text.Paragraph
import androidx.ui.engine.text.ParagraphBuilder
import androidx.ui.engine.text.ParagraphConstraints
import androidx.ui.engine.text.ParagraphStyle
import androidx.ui.engine.text.TextAlign
import androidx.ui.engine.text.TextDirection
import androidx.ui.engine.text.TextPosition
import androidx.ui.engine.window.Locale
import androidx.ui.graphics.Color
import androidx.ui.rendering.paragraph.TextOverflow
import androidx.ui.services.text_editing.TextRange
import androidx.ui.services.text_editing.TextSelection
import kotlin.math.ceil

/** The default selection color if none is specified. */
private val DEFAULT_SELECTION_COLOR = Color(0x6633B5E5)

/**
 * Unfortunately, using full precision floating point here causes bad layouts because floating
 * point math isn't associative. If we add and subtract padding, for example, we'll get
 * different values when we estimate sizes and when we actually compute layout because the
 * operations will end up associated differently. To work around this problem for now, we round
 * fractional pixel values up to the nearest whole pixel value. The right long-term fix is to do
 * layout using fixed precision arithmetic.
 */
fun applyFloatingPointHack(layoutValue: Float): Float {
    return ceil(layoutValue)
}

/**
 * An object that paints a [TextSpan] tree into a [Canvas].
 *
 * To use a [TextPainter], follow these steps:
 *
 * 1. Create a [TextSpan] tree and pass it to the [TextPainter] constructor.
 *
 * 2. Call [layout] to prepare the paragraph.
 *
 * 3. Call [paint] as often as desired to paint the paragraph.
 *
 * If the width of the area into which the text is being painted changes, return to step 2. If the
 *  text to be painted changes, return to step 1.
 *
 * @param text The (potentially styled) text to paint. After this is set, you must call [layout]
 *   before the next call to [paint]. This and [textDirection] must be non-null before you call
 *   [layout].
 *
 * @param textAlign How the text should be aligned horizontally. After this is set, you must call
 *    [layout] before the next call to [paint]. The [textAlign] property must not be null.
 *
 * @param textDirection The default directionality of the text. This controls how the
 *   [TextAlign.start], [TextAlign.end], and [TextAlign.justify] values of [textAlign] are resolved.
 *   After this is set, you must call [layout] before the next call to [paint]. This and [text] must
 *   be non-null before you call [layout].
 *
 * @param textScaleFactor The number of font pixels for each logical pixel.
 *   After this is set, you must call [layout] before the next call to [paint].
 *
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if necessary.
 *   If the text exceeds the given number of lines, it is truncated such that subsequent lines are
 *   dropped.
 *   After this is set, you must call [layout] before the next call to [paint].
 *
 * @param softWrap Whether the text should break at soft line breaks.
 *   If false, the glyphs in the text will be positioned as if there was unlimited horizontal space.
 *   If [softWrap] is false, [overflow] and [textAlign] may have unexpected effects.
 *
 * @param overflow How visual overflow should be handled.
 *   Specifically, the ellipsis is applied to the last line before the line truncated by [maxLines],
 *   if [maxLines] is non-null and that line overflows the width constraint.
 *   After this is set, you must call [layout] before the next call to [paint].
 *
 * @param locale The locale used to select region-specific glyphs.
 */
class TextPainter(
    text: TextSpan? = null,
    textAlign: TextAlign = TextAlign.Start,
    textDirection: TextDirection? = null,
    textScaleFactor: Float = 1.0f,
    maxLines: Int? = null,
    softWrap: Boolean = true,
    overflow: TextOverflow = TextOverflow.Clip,
    locale: Locale? = null,
    var selectionColor: Color = DEFAULT_SELECTION_COLOR
) {
    init {
        assert(maxLines == null || maxLines > 0)
    }

    @VisibleForTesting
    internal var paragraph: Paragraph? = null
        private set

    @VisibleForTesting
    internal var needsLayout = true
        private set

    @VisibleForTesting
    internal var layoutTemplate: Paragraph? = null
        private set

    private var overflowShader: Shader? = null

    @VisibleForTesting
    internal var hasVisualOverflow = false
        private set

    private var lastMinWidth: Float = 0.0f
    private var lastMaxWidth: Float = 0.0f

    var text: TextSpan? = text
        set(value) {
            if (field == value) return
            if (field?.style != value?.style) layoutTemplate = null
            field = value
            paragraph = null
            needsLayout = true
        }

    var textAlign: TextAlign = textAlign
        set(value) {
            if (field == value) return
            field = value
            paragraph = null
            needsLayout = true
        }

    var textDirection: TextDirection? = textDirection
        set(value) {
            if (field == value) return
            field = value
            paragraph = null
            layoutTemplate = null // Shouldn't really matter, but for strict correctness...
            needsLayout = true
        }

    var textScaleFactor: Float = textScaleFactor
        set(value) {
            if (field == value) return
            field = value
            paragraph = null
            layoutTemplate = null
            needsLayout = true
        }

    var maxLines: Int? = maxLines
        set(value) {
            assert(value == null || value > 0)
            if (field == value) return
            field = value
            paragraph = null
            needsLayout = true
        }

    var softWrap: Boolean = softWrap
        set(value) {
            if (field == value) return
            field = value
            paragraph = null
            needsLayout = true
        }

    var overflow: TextOverflow? = overflow
        set(value) {
            if (field == value) return
            field = value
            paragraph = null
            needsLayout = true
        }

    var locale: Locale? = locale
        set(value) {
            if (field == value) return
            field = value
            paragraph = null
            needsLayout = true
        }

    fun createParagraphStyle(defaultTextDirection: TextDirection? = null): ParagraphStyle {
        // The defaultTextDirection argument is used for preferredLineHeight in case
        // textDirection hasn't yet been set.
        assert(textDirection != null || defaultTextDirection != null) {
            "TextPainter.textDirection must be set to a non-null value before using the " +
                    "TextPainter."
        }
        return text?.style?.getParagraphStyle(
            textAlign = textAlign,
            textDirection = textDirection ?: defaultTextDirection,
            textScaleFactor = textScaleFactor,
            maxLines = maxLines,
            ellipsis = overflow == TextOverflow.Ellipsis,
            locale = locale

        ) ?: ParagraphStyle(
            textAlign = textAlign,
            textDirection = textDirection ?: defaultTextDirection,
            maxLines = maxLines,
            ellipsis = overflow == TextOverflow.Ellipsis,
            locale = locale
        )
    }

    /**
     * The height of a space in [text] in logical pixels.
     *
     * Not every line of text in [text] will have this height, but this height is "typical" for
     * text in [text] and useful for sizing other objects relative a typical line of text.
     *
     * Obtaining this value does not require calling [layout].
     *
     * The style of the [text] property is used to determine the font settings that contribute to
     * the [preferredLineHeight]. If [text] is null or if it specifies no styles, the default
     * [TextStyle] values are used (a 10 pixel sans-serif font).
     */
    val preferredLineHeight: Float
        get() {
            if (layoutTemplate == null) {
                val builder = ParagraphBuilder(
                    // TODO(Migration/qqd): The textDirection below used to be RTL.
                    createParagraphStyle(TextDirection.Ltr)
                ) // direction doesn't matter, text is just a space
                if (text?.style != null) {
                    builder.pushStyle(text?.style!!.getTextStyle(textScaleFactor = textScaleFactor))
                }
                builder.addText(" ")
                layoutTemplate = builder.build()
                layoutTemplate?.layout(ParagraphConstraints(width = Float.POSITIVE_INFINITY))
            }
            return layoutTemplate!!.height
        }

    private fun assertNeedsLayout(name: String) {
        assert(!needsLayout) {
            "TextPainter.$name should only be called after layout has been called."
        }
    }

    /**
     * The width at which decreasing the width of the text would prevent it from painting itself
     * completely within its bounds.
     *
     * Valid only after [layout] has been called.
     */
    val minIntrinsicWidth: Float
        get() {
            assertNeedsLayout("minIntrinsicWidth")
            return applyFloatingPointHack(paragraph!!.minIntrinsicWidth)
        }

    /**
     * The width at which increasing the width of the text no longer decreases the height.
     *
     * Valid only after [layout] has been called.
     */
    val maxIntrinsicWidth: Float
        get() {
            assertNeedsLayout("maxIntrinsicWidth")
            return applyFloatingPointHack(paragraph!!.maxIntrinsicWidth)
        }

    /**
     * The horizontal space required to paint this text.
     *
     * Valid only after [layout] has been called.
     */
    val width: Float
        get() {
            assertNeedsLayout("width")
            return applyFloatingPointHack(size.width)
        }

    /**
     * The vertical space required to paint this text.
     *
     * Valid only after [layout] has been called.
     */
    val height: Float
        get() {
            assertNeedsLayout("height")
            return applyFloatingPointHack(size.height)
        }

    /**
     * The amount of space required to paint this text.
     *
     * Valid only after [layout] has been called.
     */
    var size: Size = Size(0f, 0f)
        get() {
            assertNeedsLayout("size")
            return field
        }
        private set

    /**
     * Whether any text was truncated or ellipsized.
     *
     * If [maxLines] is not null, this is true if there were more lines to be drawn than the given
     * [maxLines], and thus at least one line was omitted in the output; otherwise it is false.
     *
     * If [maxLines] is null, this is true if [ellipsis] is true and there was a line that
     * overflowed the `maxWidth` argument passed to [layout]; otherwise it is false.
     *
     * Valid only after [layout] has been called.
     */
    val didExceedMaxLines: Boolean
        get() {
            assertNeedsLayout("didExceedMaxLines")
            return paragraph!!.didExceedMaxLines
        }

    /**
     * Computes the visual position of the glyphs for painting the text.
     *
     * The text will layout with a width that's as close to its max intrinsic width as possible
     * while still being greater than or equal to `minWidth` and less than or equal to `maxWidth`.
     *
     * The [text] and [textDirection] properties must be non-null before this is called.
     */
    private fun layoutText(minWidth: Float = 0.0f, maxWidth: Float = Float.POSITIVE_INFINITY) {
        assert(text != null) {
            "TextPainter.text must be set to a non-null value before using the TextPainter."
        }
        assert(textDirection != null) {
            "TextPainter.textDirection must be set to a non-null value before using the" +
                    " TextPainter."
        }

        // TODO(haoyuchang): fix that when softWarp is false and overflow is Ellipsis, ellipsis
        //  doesn't work.
        val widthMatters = softWrap || overflow == TextOverflow.Ellipsis
        val finalMaxWidth = if (widthMatters) maxWidth else Float.POSITIVE_INFINITY

        if (!needsLayout && minWidth == lastMinWidth && finalMaxWidth == lastMaxWidth) return
        needsLayout = false
        if (paragraph == null) {
            val builder = ParagraphBuilder(createParagraphStyle())
            text!!.build(builder, textScaleFactor = textScaleFactor)
            paragraph = builder.build()
        }
        lastMinWidth = minWidth
        lastMaxWidth = finalMaxWidth
        paragraph!!.layout(ParagraphConstraints(width = finalMaxWidth))
        if (minWidth != finalMaxWidth) {
            val newWidth = maxIntrinsicWidth.coerceIn(minWidth, finalMaxWidth)
            if (newWidth != paragraph!!.width) {
                paragraph!!.layout(ParagraphConstraints(width = newWidth))
            }
        }
    }

    fun layout(constraints: Constraints) {
        layoutText(constraints.minWidth.value.toFloat(), constraints.maxWidth.value.toFloat())

        val didOverflowHeight = didExceedMaxLines
        size = constraints.constrain(
            IntPxSize(paragraph!!.width.px.round(), paragraph!!.height.px.round())
        ).let {
            Size(it.width.value.toFloat(), it.height.value.toFloat())
        }
        val didOverflowWidth = size.width < paragraph!!.width
        // TODO(abarth): We're only measuring the sizes of the line boxes here. If
        // the glyphs draw outside the line boxes, we might think that there isn't
        // visual overflow when there actually is visual overflow. This can become
        // a problem if we start having horizontal overflow and introduce a clip
        // that affects the actual (but undetected) vertical overflow.
        hasVisualOverflow = didOverflowWidth || didOverflowHeight
        overflowShader = if (hasVisualOverflow && overflow == TextOverflow.Fade) {
            val fadeSizePainter = TextPainter(
                text = TextSpan(style = text?.style, text = "\u2026"),
                textDirection = textDirection,
                textScaleFactor = textScaleFactor
            )
            fadeSizePainter.layoutText()
            val fadeWidth = fadeSizePainter.paragraph!!.width
            val fadeHeight = fadeSizePainter.paragraph!!.height
            if (didOverflowWidth) {
                val (fadeStart, fadeEnd) = if (textDirection == TextDirection.Rtl) {
                    Pair(fadeWidth, 0.0f)
                } else {
                    Pair(size.width - fadeWidth, size.width)
                }
                Gradient.linear(
                    Offset(fadeStart, 0.0f),
                    Offset(fadeEnd, 0.0f),
                    listOf(Color(0xFFFFFFFF.toInt()), Color(0x00FFFFFF))
                )
            } else {
                val fadeEnd = size.height
                val fadeStart = fadeEnd - fadeHeight
                Gradient.linear(
                    Offset(0.0f, fadeStart),
                    Offset(0.0f, fadeEnd),
                    listOf(Color(0xFFFFFFFF.toInt()), Color(0x00FFFFFF))
                )
            }
        } else {
            null
        }
    }

    /**
     * Paints the text onto the given canvas at the given offset.
     *
     * Valid only after [layout] has been called.
     *
     * If you cannot see the text being painted, check that your text color does not conflict with
     * the background on which you are drawing. The default text color is white (to contrast with
     * the default black background color), so if you are writing an application with a white
     * background, the text will not be visible by default.
     *
     * To set the text style, specify a [TextStyle] when creating the [TextSpan] that you pass to
     * the [TextPainter] constructor or to the [text] property.
     */
    fun paint(canvas: Canvas, offset: Offset) {
        assert(!needsLayout) {
            "TextPainter.paint called when text geometry was not yet calculated.\n" +
                    "Please call layout() before paint() to position the text before painting it."
        }
        // Ideally we could compute the min/max intrinsic width/height with a
        // non-destructive operation. However, currently, computing these values
        // will destroy state inside the painter. If that happens, we need to
        // get back the correct state by calling layout again.
        //
        // TODO(abarth): Make computing the min/max intrinsic width/height
        // a non-destructive operation.
        //
        // If you remove this call, make sure that changing the textAlign still
        // works properly.
        // TODO(Migration/qqd): Need to figure out where this constraints come from and how to make
        // it non-null. For now Crane Text version does not need to layout text again. Comment it.
        // layoutTextWithConstraints(constraints!!)

        if (hasVisualOverflow) {
            val bounds = offset.and(size)
            if (overflowShader != null) {
                // This layer limits what the shader below blends with to be just the text
                // (as opposed to the text and its background).
                canvas.saveLayer(bounds, Paint())
            } else {
                canvas.save()
            }
            canvas.clipRect(bounds)
        }
        paragraph!!.paint(canvas, offset.dx, offset.dy)
        if (hasVisualOverflow) {
            if (overflowShader != null) {
                canvas.translate(offset.dx, offset.dy)
                val paint = Paint()
                paint.blendMode = BlendMode.multiply
                paint.shader = overflowShader
                canvas.drawRect(Offset.zero.and(size), paint)
            }
            canvas.restore()
        }
    }

    /**
     * Paint the text selection highlight of the given [selection] and [offset] on [canvas].
     *
     * This function can only be called after [layout] is called first.
     */
    fun paintSelection(selection: TextSelection, canvas: Canvas, offset: Offset) {
        assert(!needsLayout)
        val selectionPath = paragraph!!.getPathForRange(selection.start, selection.end)
        // TODO(haoyuchang): check if move this paint to parameter is better
        val selectionPaint = Paint()
        selectionPaint.color = selectionColor
        canvas.drawPath(selectionPath.shift(offset), selectionPaint)
    }

    /** Returns the position within the text for the given pixel offset. */
    fun getPositionForOffset(offset: Offset): TextPosition {
        assert(!needsLayout)
        return paragraph!!.getPositionForOffset(offset)
    }

    /**
     * Returns the Caret as a vertical bar for given text position, at which to paint the caret.
     *
     * Valid only after [layout] has been called.
     */
    fun getCaretForTextPosition(position: TextPosition): Pair<Offset, Offset> {
        assert(!needsLayout)
        return paragraph!!.getCaretForTextPosition(position)
    }

    /**
     * Returns the bounding box as Rect of the character for given TextPosition. Rect includes the
     * top, bottom, left and right of a character.
     *
     * Valid only after [layout] has been called.
     */
    internal fun getBoundingBoxForTextPosition(textPosition: TextPosition): Rect {
        assert(!needsLayout)
        return paragraph!!.getBoundingBoxForTextPosition(textPosition)
    }

    /**
     * Returns the text range of the word at the given offset. Characters not part of a word, such
     * as spaces, symbols, and punctuation, have word breaks on both sides. In such cases, this
     * method will return a text range that contains the given text position.
     *
     * Word boundaries are defined more precisely in Unicode Standard Annex #29
     * <http://www.unicode.org/reports/tr29/#Word_Boundaries>.
     */
    fun getWordBoundary(position: TextPosition): TextRange {
        assert(!needsLayout)
        return paragraph!!.getWordBoundary(position.offset)
    }
}

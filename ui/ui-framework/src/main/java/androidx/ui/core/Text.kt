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
package androidx.ui.core

import androidx.compose.Ambient
import androidx.compose.ambient
import androidx.compose.Composable
import androidx.compose.compositionReference
import androidx.compose.effectOf
import androidx.compose.memo
import androidx.compose.onCommit
import androidx.compose.onDispose
import androidx.compose.state
import androidx.compose.unaryPlus
import androidx.ui.core.selection.SelectionRegistrar
import androidx.ui.core.selection.SelectionRegistrarAmbient
import androidx.ui.core.selection.TextSelectionDelegate
import androidx.ui.graphics.Color
import androidx.ui.semantics.Semantics
import androidx.ui.semantics.accessibilityLabel
import androidx.ui.text.AnnotatedString
import androidx.ui.text.ParagraphStyle
import androidx.ui.text.TextDelegate
import androidx.ui.text.TextRange
import androidx.ui.text.TextSpan
import androidx.ui.text.TextStyle
import androidx.ui.text.style.TextAlign
import androidx.ui.text.style.TextOverflow
import androidx.ui.text.toAnnotatedString

private val DefaultSoftWrap: Boolean = true
private val DefaultOverflow: TextOverflow = TextOverflow.Clip
private val DefaultMaxLines: Int? = null

/** The default selection color if none is specified. */
internal val DefaultSelectionColor = Color(0x6633B5E5)

/**
 * The Text composable displays text that uses multiple different styles. The text to display is
 * described using a tree of [Span], each of which has an associated style that is used
 * for that subtree. The text might break across multiple lines or might all be displayed on the
 * same line depending on the layout constraints.
 *
 * @param modifier Modifier to apply to this layout node.
 * @param style Style configuration that applies at character level such as color, font etc.
 * @param paragraphStyle Style configuration that applies only to paragraphs such as text
 * alignment, or text direction.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and [TextAlign] may have unexpected effects.
 * @param overflow How visual overflow should be handled.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. If it is not null, then it must be greater than zero.
 * @param selectionColor The color used to draw selected region.
 */
@Composable
fun Text(
    modifier: Modifier = Modifier.None,
    style: TextStyle? = null,
    paragraphStyle: ParagraphStyle? = null,
    softWrap: Boolean = DefaultSoftWrap,
    overflow: TextOverflow = DefaultOverflow,
    maxLines: Int? = DefaultMaxLines,
    selectionColor: Color = DefaultSelectionColor,
    child: @Composable TextSpanScope.() -> Unit
) {
    val rootTextSpan = +memo { TextSpan() }
    val ref = +compositionReference()
    compose(rootTextSpan, ref, child)
    +onDispose { disposeComposition(rootTextSpan, ref) }

    Text(
        text = rootTextSpan.toAnnotatedString(),
        modifier = modifier,
        style = style,
        paragraphStyle = paragraphStyle,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
        selectionColor = selectionColor
    )
}

/**
 * Simplified version of [Text] component with minimal set of customizations.
 *
 * @param text The text to be displayed.
 * @param modifier Modifier to apply to this layout node.
 * @param style Style configuration that applies at character level such as color, font etc.
 * @param paragraphStyle Style configuration that applies only to paragraphs such as text
 * alignment, or text direction.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and [TextAlign] may have unexpected effects.
 * @param overflow How visual overflow should be handled.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. If it is not null, then it must be greater than zero.
 * @param selectionColor The color used to draw selected region.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier.None,
    style: TextStyle? = null,
    paragraphStyle: ParagraphStyle? = null,
    softWrap: Boolean = DefaultSoftWrap,
    overflow: TextOverflow = DefaultOverflow,
    maxLines: Int? = DefaultMaxLines,
    selectionColor: Color = DefaultSelectionColor
) {
    Text(
        text = AnnotatedString(text),
        modifier = modifier,
        style = style,
        paragraphStyle = paragraphStyle,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines,
        selectionColor = selectionColor
    )
}

/**
 * The Text composable displays text that uses multiple different styles. The text to display is
 * described using a [AnnotatedString].
 *
 * @param text AnnotatedString encoding a styled text.
 * @param modifier Modifier to apply to this layout node.
 * @param style Style configuration that applies at character level such as color, font etc.
 * @param paragraphStyle Style configuration that applies only to paragraphs such as text
 * alignment, or text direction.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and [TextAlign] may have unexpected effects.
 * @param overflow How visual overflow should be handled.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. If it is not null, then it must be greater than zero.
 * @param selectionColor The color used to draw selected region.
 */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier.None,
    style: TextStyle? = null,
    paragraphStyle: ParagraphStyle? = null,
    softWrap: Boolean = DefaultSoftWrap,
    overflow: TextOverflow = DefaultOverflow,
    maxLines: Int? = DefaultMaxLines,
    selectionColor: Color = DefaultSelectionColor
) {
    maxLines?.let {
        require(it > 0) { "maxLines should be greater than 0" }
    }
    // States
    // The selection range for this Composable, used by selection
    val selectionRange = +state<TextRange?> { null }
    // The last layout coordinates recorded for this Composable, used by selection
    val layoutCoordinates = +state<LayoutCoordinates?> { null }

    // Ambients
    // selection registrar, if no SelectionContainer is added ambient value will be null
    val selectionRegistrar: SelectionRegistrar? = +ambient(SelectionRegistrarAmbient)
    val density = +ambientDensity()
    val resourceLoader = +ambient(FontLoaderAmbient)
    val layoutDirection = +ambient(LayoutDirectionAmbient)
    val themeStyle = +ambient(CurrentTextStyleAmbient)

    val mergedStyle = themeStyle.merge(style)

    Semantics(
        properties = {
            accessibilityLabel = text.text
        }
    ) {
        val textDelegate = +memo(
            text,
            mergedStyle,
            paragraphStyle,
            softWrap,
            overflow,
            maxLines,
            density
        ) {
            TextDelegate(
                text = text,
                style = mergedStyle,
                paragraphStyle = paragraphStyle,
                softWrap = softWrap,
                overflow = overflow,
                maxLines = maxLines,
                density = density,
                layoutDirection = layoutDirection,
                resourceLoader = resourceLoader
            )
        }

        val children = @Composable {
            // Get the layout coordinates of the text composable. This is for hit test of
            // cross-composable selection.
            OnPositioned(onPositioned = { layoutCoordinates.value = it })
            Draw { canvas, _ ->
                selectionRange.value?.let {
                    textDelegate.paintBackground(
                        it.min, it.max, selectionColor, canvas
                    )
                }
                textDelegate.paint(canvas)
            }
        }
        Layout(
            children = children,
            modifier = modifier,
            minIntrinsicWidthMeasureBlock = { _, _ ->
                textDelegate.layoutIntrinsics()
                textDelegate.minIntrinsicWidth.px.round()
            },
            minIntrinsicHeightMeasureBlock = { _, width ->
                // given the width constraint, determine the min height
                textDelegate.layout(Constraints(0.ipx, width, 0.ipx, IntPx.Infinity))
                textDelegate.height.px.round()
            },
            maxIntrinsicWidthMeasureBlock = { _, _ ->
                textDelegate.layoutIntrinsics()
                textDelegate.maxIntrinsicWidth.px.round()
            },
            maxIntrinsicHeightMeasureBlock = { _, width ->
                textDelegate.layout(Constraints(0.ipx, width, 0.ipx, IntPx.Infinity))
                textDelegate.height.px.round()
            }
        ) { _, constraints ->
            textDelegate.layout(constraints)
            layout(
                textDelegate.width.px.round(),
                textDelegate.height.px.round(),
                // Provide values for the alignment lines defined by text - the first
                // and last baselines of the text. These can be used by parent layouts
                // to position this text or align this and other texts by baseline.
                mapOf(
                    FirstBaseline to textDelegate.firstBaseline.px.round(),
                    LastBaseline to textDelegate.lastBaseline.px.round()
                )
            ) {}
        }

        +onCommit(
            text,
            mergedStyle,
            paragraphStyle,
            softWrap,
            overflow,
            maxLines,
            density
        ) {
            // if no SelectionContainer is added as parent selectionRegistrar will be null
            val id: Any? = selectionRegistrar?.let {
                selectionRegistrar.subscribe(
                    TextSelectionDelegate(
                        selectionRange = selectionRange,
                        layoutCoordinates = layoutCoordinates,
                        textDelegate = textDelegate
                    )
                )
            }

            onDispose {
                // unregister only if any id was provided by SelectionRegistrar
                id?.let { selectionRegistrar.unsubscribe(id) }
            }
        }
    }
}

/**
 * [AlignmentLine] defined by the baseline of a first line of a [Text].
 */
val FirstBaseline = HorizontalAlignmentLine(::min)

/**
 * [AlignmentLine] defined by the baseline of the last line of a [Text].
 */
val LastBaseline = HorizontalAlignmentLine(::max)

internal val CurrentTextStyleAmbient = Ambient.of { TextStyle() }

/**
 * This component is used to set the current value of the Text style ambient. The given style will
 * be merged with the current style values for any missing attributes. Any [Text]
 * components included in this component's children will be styled with this style unless
 * styled explicitly.
 */
@Composable
fun CurrentTextStyleProvider(value: TextStyle, children: @Composable() () -> Unit) {
    val style = +ambient(CurrentTextStyleAmbient)
    val mergedStyle = style.merge(value)
    CurrentTextStyleAmbient.Provider(value = mergedStyle, children = children)
}

/**
 * This effect is used to read the current value of the Text style ambient. Any [Text]
 * components included in this component's children will be styled with this style unless
 * styled explicitly.
 */
fun currentTextStyle() =
    effectOf<TextStyle> { +ambient(CurrentTextStyleAmbient) }

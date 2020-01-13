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
import androidx.compose.Composable
import androidx.compose.ambient
import androidx.compose.compositionReference
import androidx.compose.onCommit
import androidx.compose.onDispose
import androidx.compose.remember
import androidx.compose.state
import androidx.ui.core.selection.Selectable
import androidx.ui.core.selection.SelectionRegistrar
import androidx.ui.core.selection.SelectionRegistrarAmbient
import androidx.ui.core.selection.TextSelectionDelegate
import androidx.ui.graphics.Color
import androidx.ui.semantics.Semantics
import androidx.ui.semantics.accessibilityLabel
import androidx.ui.text.AnnotatedString
import androidx.ui.text.TextDelegate
import androidx.ui.text.TextLayoutResult
import androidx.ui.text.TextRange
import androidx.ui.text.TextSpan
import androidx.ui.text.TextStyle
import androidx.ui.text.style.TextAlign
import androidx.ui.text.style.TextOverflow
import androidx.ui.text.toAnnotatedString
import androidx.ui.unit.IntPx
import androidx.ui.unit.ipx
import androidx.ui.unit.max
import androidx.ui.unit.min

private const val DefaultSoftWrap: Boolean = true
private const val DefaultMaxLines = Int.MAX_VALUE
private val DefaultOverflow: TextOverflow = TextOverflow.Clip

/** The default selection color if none is specified. */
internal val DefaultSelectionColor = Color(0x6633B5E5)

/**
 * The Text composable displays text that uses multiple different styles. The text to display is
 * described using a tree of [Span], each of which has an associated style that is used
 * for that subtree. The text might break across multiple lines or might all be displayed on the
 * same line depending on the layout constraints.
 *
 * @param modifier Modifier to apply to this layout node.
 * @param style Style configuration for the text such as color, font, line height etc.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and [TextAlign] may have unexpected effects.
 * @param overflow How visual overflow should be handled.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. If it is not null, then it must be greater than zero.
 */
@Composable
fun Text(
    modifier: Modifier = Modifier.None,
    style: TextStyle? = null,
    softWrap: Boolean = DefaultSoftWrap,
    overflow: TextOverflow = DefaultOverflow,
    maxLines: Int = DefaultMaxLines,
    child: @Composable TextSpanScope.() -> Unit
) {
    val rootTextSpan = remember { TextSpan() }
    val ref = compositionReference()
    compose(rootTextSpan, ref, child)
    onDispose { disposeComposition(rootTextSpan, ref) }

    Text(
        text = rootTextSpan.toAnnotatedString(),
        modifier = modifier,
        style = style,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines
    )
}

/**
 * Simplified version of [Text] component with minimal set of customizations.
 *
 * @param text The text to be displayed.
 * @param modifier Modifier to apply to this layout node.
 * @param style Style configuration for the text such as color, font, line height etc.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and [TextAlign] may have unexpected effects.
 * @param overflow How visual overflow should be handled.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. If it is not null, then it must be greater than zero.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier.None,
    style: TextStyle? = null,
    softWrap: Boolean = DefaultSoftWrap,
    overflow: TextOverflow = DefaultOverflow,
    maxLines: Int = DefaultMaxLines
) {
    Text(
        text = AnnotatedString(text),
        modifier = modifier,
        style = style,
        softWrap = softWrap,
        overflow = overflow,
        maxLines = maxLines
    )
}

/**
 * The Text composable displays text that uses multiple different styles. The text to display is
 * described using a [AnnotatedString].
 *
 * @param text AnnotatedString encoding a styled text.
 * @param modifier Modifier to apply to this layout node.
 * @param style Style configuration for the text such as color, font, line height etc.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and [TextAlign] may have unexpected effects.
 * @param overflow How visual overflow should be handled.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. If it is not null, then it must be greater than zero.
 */
@Composable
fun Text(
    text: AnnotatedString,
    modifier: Modifier = Modifier.None,
    style: TextStyle? = null,
    softWrap: Boolean = DefaultSoftWrap,
    overflow: TextOverflow = DefaultOverflow,
    maxLines: Int = DefaultMaxLines
) {
    require(maxLines > 0) { "maxLines should be greater than 0" }
    // States
    // The selection range for this Composable, used by selection
    val selectionRange = state<TextRange?> { null }
    // The last layout coordinates recorded for this Composable, used by selection
    val layoutCoordinates = state<LayoutCoordinates?> { null }

    // Ambients
    // selection registrar, if no SelectionContainer is added ambient value will be null
    val selectionRegistrar: SelectionRegistrar? = ambient(SelectionRegistrarAmbient)
    val density = ambientDensity()
    val resourceLoader = ambient(FontLoaderAmbient)
    val layoutDirection = ambient(LayoutDirectionAmbient)
    val themeStyle = ambient(CurrentTextStyleAmbient)

    val mergedStyle = themeStyle.merge(style)

    Semantics(
        properties = {
            accessibilityLabel = text.text
        }
    ) {
        val textDelegate = remember(
            text,
            mergedStyle,
            softWrap,
            overflow,
            maxLines,
            density
        ) {
            TextDelegate(
                text = text,
                style = mergedStyle,
                softWrap = softWrap,
                overflow = overflow,
                maxLines = maxLines,
                density = density,
                layoutDirection = layoutDirection,
                resourceLoader = resourceLoader
            )
        }
        val layoutResultState = state<TextLayoutResult?> { null }

        val children = @Composable {
            // Get the layout coordinates of the text composable. This is for hit test of
            // cross-composable selection.
            OnPositioned(onPositioned = { layoutCoordinates.value = it })
            Draw { canvas, _ ->
                layoutResultState.value?.let { layoutResult ->
                    selectionRange.value?.let {
                        textDelegate.paintBackground(
                            it.min, it.max, DefaultSelectionColor, canvas, layoutResult
                        )
                    }
                    textDelegate.paint(canvas, layoutResult)
                }
            }
        }
        Layout(
            children = children,
            modifier = modifier,
            minIntrinsicWidthMeasureBlock = { _, _ ->
                textDelegate.layoutIntrinsics()
                textDelegate.minIntrinsicWidth
            },
            minIntrinsicHeightMeasureBlock = { _, width ->
                // given the width constraint, determine the min height
                textDelegate.layout(Constraints(0.ipx, width, 0.ipx, IntPx.Infinity)).size.height
            },
            maxIntrinsicWidthMeasureBlock = { _, _ ->
                textDelegate.layoutIntrinsics()
                textDelegate.maxIntrinsicWidth
            },
            maxIntrinsicHeightMeasureBlock = { _, width ->
                textDelegate.layout(Constraints(0.ipx, width, 0.ipx, IntPx.Infinity)).size.height
            }
        ) { _, constraints ->

            val layoutResult = textDelegate.layout(constraints, layoutResultState.value)
            if (layoutResultState.value != layoutResult) {
                layoutResultState.value = layoutResult
            }

            layout(
                layoutResult.size.width,
                layoutResult.size.height,
                // Provide values for the alignment lines defined by text - the first
                // and last baselines of the text. These can be used by parent layouts
                // to position this text or align this and other texts by baseline.
                mapOf(
                    FirstBaseline to layoutResult.firstBaseline,
                    LastBaseline to layoutResult.lastBaseline
                )
            ) {}
        }

        onCommit(
            text,
            mergedStyle,
            softWrap,
            overflow,
            maxLines,
            density
        ) {
            // if no SelectionContainer is added as parent selectionRegistrar will be null
            val id: Selectable? = selectionRegistrar?.let {
                selectionRegistrar.subscribe(
                    TextSelectionDelegate(
                        selectionRange = selectionRange,
                        layoutCoordinates = layoutCoordinates,
                        textLayoutResult = layoutResultState
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
    val style = ambient(CurrentTextStyleAmbient)
    val mergedStyle = style.merge(value)
    CurrentTextStyleAmbient.Provider(value = mergedStyle, children = children)
}

/**
 * This effect is used to read the current value of the Text style ambient. Any [Text]
 * components included in this component's children will be styled with this style unless
 * styled explicitly.
 */
@Composable
fun currentTextStyle(): TextStyle = ambient(CurrentTextStyleAmbient)

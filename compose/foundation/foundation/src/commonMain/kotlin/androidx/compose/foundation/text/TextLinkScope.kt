/*
 * Copyright 2023 The Android Open Source Project
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

package androidx.compose.foundation.text

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.SemanticsProperties.LinkTestMarker
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.roundToIntRect
import androidx.compose.ui.util.fastForEach
import kotlin.math.min

internal typealias LinkRange = AnnotatedString.Range<LinkAnnotation>

/**
 * A scope that provides necessary information to attach a hyperlink to the text range.
 *
 * This class assumes that links exist and does not perform any additional check inside its methods.
 * Therefore this class initialisation should be guarded by the `hasLinks` check.
 */
internal class TextLinkScope(internal val initialText: AnnotatedString) {
    var textLayoutResult: TextLayoutResult? by mutableStateOf(null)

    /** [initialText] with applied links styling to it from [LinkAnnotation.styles] */
    internal var text: AnnotatedString

    init {
        text =
            initialText.flatMapAnnotations {
                // If link styles don't contain a non-null style for at least one of the states,
                // we don't add any additional style to the list of annotations
                if (
                    it.item is LinkAnnotation && !(it.item as LinkAnnotation).styles.isNullOrEmpty()
                ) {
                    arrayListOf(
                        // original link annotation
                        it,
                        // SpanStyle from the link styling object, or default SpanStyle otherwise
                        AnnotatedString.Range(
                            (it.item as LinkAnnotation).styles?.style ?: SpanStyle(),
                            it.start,
                            it.end
                        )
                    )
                } else {
                    arrayListOf(it)
                }
            }
    }

    // Additional span style annotations applied to the AnnotatedString. These SpanStyles are coming
    // from LinkAnnotation's style arguments
    private val annotators = mutableStateListOf<TextAnnotatorScope.() -> Unit>()

    // indicates whether the links should be measured or not. The latter needed to handle
    // case where translated string forces measurement before the recomposition. Recomposition in
    // this case will dispose the links altogether because translator returns plain text
    val shouldMeasureLinks: () -> Boolean
        get() = { text == textLayoutResult?.layoutInput?.text }

    /**
     * Causes the modified element to be measured with fixed constraints equal to the bounds of the
     * text range [[start], [end]) and placed over that range of text.
     */
    private fun Modifier.textRange(start: Int, end: Int): Modifier =
        this.then(
            TextRangeLayoutModifier {
                val layoutResult =
                    textLayoutResult
                        ?: return@TextRangeLayoutModifier layout(0, 0) { IntOffset.Zero }
                val bounds = layoutResult.getPathForRange(start, end).getBounds().roundToIntRect()
                layout(bounds.width, bounds.height) { bounds.topLeft }
            }
        )

    private fun shapeForRange(range: LinkRange): Shape? =
        pathForRangeInRangeCoordinates(range)?.let {
            object : Shape {
                override fun createOutline(
                    size: Size,
                    layoutDirection: LayoutDirection,
                    density: Density
                ): Outline {
                    return Outline.Generic(it)
                }
            }
        }

    private fun pathForRangeInRangeCoordinates(range: LinkRange): Path? {
        return if (!shouldMeasureLinks()) null
        else {
            textLayoutResult?.let {
                val path = it.getPathForRange(range.start, range.end)

                val firstCharBoundingBox = it.getBoundingBox(range.start)
                val lastCharBoundingBox = it.getBoundingBox(range.end - 1)

                val rangeStartLine = it.getLineForOffset(range.start)
                val rangeEndLine = it.getLineForOffset(range.end)

                val xOffset =
                    if (rangeStartLine == rangeEndLine) {
                        // if the link occupies a single line, we take the left most position of the
                        // link's range
                        minOf(lastCharBoundingBox.left, firstCharBoundingBox.left)
                    } else {
                        // if the link occupies more than one line, the left sides of the link node
                        // and
                        // text node match so we don't need to do anything
                        0f
                    }

                // the top of the top-most (first) character
                val yOffset = firstCharBoundingBox.top

                path.translate(-Offset(xOffset, yOffset))
                return path
            }
        }
    }

    /**
     * This composable responsible for creating layout nodes for each link annotation. Since
     * [TextLinkScope] object created *only* when there are links present in the text, we don't need
     * to do any additional guarding inside this composable function.
     */
    @Composable
    fun LinksComposables() {
        val uriHandler = LocalUriHandler.current

        // The paragraph with a link might not be added to the paragraphs list if it exceeds the
        // maxline. We should only add links up to the last line's end offset.
        val lineCount = textLayoutResult?.lineCount ?: return
        val lastOffset = textLayoutResult?.getLineEnd(lineCount - 1) ?: return
        val links = text.getLinkAnnotations(0, text.length)

        links.fastForEach { it ->
            if (it.start != it.end && it.start < lastOffset) {
                // The link might be clipped if we reach the maxLines so we adjust its end in that
                // case to be the last visible offset
                val range = it.copy(end = min(it.end, lastOffset))
                val shape = shapeForRange(range)
                val clipModifier = shape?.let { Modifier.clip(it) } ?: Modifier
                val interactionSource = remember { MutableInteractionSource() }

                Box(
                    clipModifier
                        .semantics {
                            // adding this to identify links in tests, see performFirstLinkClick
                            this[LinkTestMarker] = Unit
                        }
                        .textRange(range.start, range.end)
                        .hoverable(interactionSource)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .combinedClickable(
                            indication = null,
                            interactionSource = interactionSource,
                            onClick = { handleLink(range.item, uriHandler) }
                        )
                )

                if (!range.item.styles.isNullOrEmpty()) {
                    // the interaction source is not hoisted, we create and remember it in the
                    // code above. Therefore there's no need to pass it as a key to the remember and
                    // a
                    // launch effect.
                    val linkStateObserver = remember {
                        LinkStateInteractionSourceObserver(interactionSource)
                    }
                    LaunchedEffect(Unit) { linkStateObserver.collectInteractionsForLinks() }

                    StyleAnnotation(
                        linkStateObserver.isHovered,
                        linkStateObserver.isFocused,
                        linkStateObserver.isPressed,
                        range.item.styles?.style,
                        range.item.styles?.focusedStyle,
                        range.item.styles?.hoveredStyle,
                        range.item.styles?.pressedStyle,
                    ) {
                        // we calculate the latest style based on the link state and apply it to the
                        // initialText's style. This allows us to merge the style with the original
                        // instead of fully replacing it
                        val mergedStyle =
                            range.item.styles
                                ?.style
                                .mergeOrUse(
                                    if (linkStateObserver.isFocused) range.item.styles?.focusedStyle
                                    else null
                                )
                                .mergeOrUse(
                                    if (linkStateObserver.isHovered) range.item.styles?.hoveredStyle
                                    else null
                                )
                                .mergeOrUse(
                                    if (linkStateObserver.isPressed) range.item.styles?.pressedStyle
                                    else null
                                )
                        replaceStyle(it, mergedStyle)
                    }
                }
            }
        }
    }

    private fun SpanStyle?.mergeOrUse(other: SpanStyle?) = this?.merge(other) ?: other

    private fun handleLink(link: LinkAnnotation, uriHandler: UriHandler) {
        when (link) {
            is LinkAnnotation.Url ->
                link.linkInteractionListener?.onClick(link)
                    ?: try {
                        uriHandler.openUri(link.url)
                    } catch (_: IllegalArgumentException) {
                        // we choose to silently fail when the uri can't be opened to avoid crashes
                        // for users. This is the case where developer don't provide the link
                        // handlers themselves and therefore I suspect are less likely to test them
                        // manually.
                    }
            is LinkAnnotation.Clickable -> link.linkInteractionListener?.onClick(link)
        }
    }

    /** Returns [text] with additional styles from [LinkAnnotation] based on link's state */
    internal fun applyAnnotators(): AnnotatedString {
        val styledText =
            if (annotators.isEmpty()) text
            else {
                val scope = TextAnnotatorScope(text)
                annotators.fastForEach { it.invoke(scope) }
                scope.styledText
            }
        text = styledText
        return styledText
    }

    /** Adds style annotations to [text]. */
    @Composable
    private fun StyleAnnotation(vararg keys: Any?, block: TextAnnotatorScope.() -> Unit) {
        DisposableEffect(block, *keys) {
            annotators += block
            onDispose { annotators -= block }
        }
    }
}

private fun TextLinkStyles?.isNullOrEmpty(): Boolean {
    return this == null ||
        (style == null && focusedStyle == null && hoveredStyle == null && pressedStyle == null)
}

/** Interface holding the width, height and positioning logic. */
internal class TextRangeLayoutMeasureResult
internal constructor(val width: Int, val height: Int, val place: () -> IntOffset)

/**
 * The receiver scope of a text range layout's measure lambda. The return value of the measure
 * lambda is [TextRangeLayoutMeasureResult], which should be returned by [layout]
 */
internal class TextRangeLayoutMeasureScope {
    fun layout(width: Int, height: Int, place: () -> IntOffset): TextRangeLayoutMeasureResult =
        TextRangeLayoutMeasureResult(width, height, place)
}

/** Provides the size and placement for an element inside a [TextLinkScope] */
internal fun interface TextRangeScopeMeasurePolicy {
    fun TextRangeLayoutMeasureScope.measure(): TextRangeLayoutMeasureResult
}

internal class TextRangeLayoutModifier(val measurePolicy: TextRangeScopeMeasurePolicy) :
    ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?) = this@TextRangeLayoutModifier
}

/**
 * Provides methods to update styles of the text inside a [TextLinkScope.StyleAnnotation] function.
 */
private class TextAnnotatorScope(private val initialText: AnnotatedString) {
    var styledText = initialText

    fun replaceStyle(linkRange: AnnotatedString.Range<LinkAnnotation>, newStyle: SpanStyle?) {
        var linkFound = false
        styledText =
            initialText.mapAnnotations {
                // if we found a link annotation on previous iteration, we need to update the
                // SpanStyle
                // on this iteration. This SpanStyle with the same range as the link annotation
                // coming right after the link annotation corresponds to the link styling
                val annotation =
                    if (
                        linkFound &&
                            it.item is SpanStyle &&
                            it.start == linkRange.start &&
                            it.end == linkRange.end
                    ) {
                        AnnotatedString.Range(newStyle ?: SpanStyle(), it.start, it.end)
                    } else {
                        it
                    }
                linkFound = linkRange == it
                annotation
            }
    }
}

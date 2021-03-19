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

package androidx.compose.ui.text.style

/** How overflowing text should be handled. */
enum class TextOverflow {
    /**
     * Clip the overflowing text to fix its container.
     * @sample androidx.compose.ui.text.samples.TextOverflowClipSample
     */
    Clip,

    /**
     * Use an ellipsis to indicate that the text has overflowed.
     * @sample androidx.compose.ui.text.samples.TextOverflowEllipsisSample
     */
    Ellipsis,

    /**
     * Display all text, even if there is not enough space in the specified bounds.
     * When overflow is visible, text may be rendered outside the bounds of the composable
     * displaying the text. This ensures that all text is displayed to the user, and is typically
     * the right choice for most text display. It does mean that the text may visually occupy a
     * region larger than the bounds of it's composable. This can lead to situations where text
     * displays outside the bounds of the background and clickable on a Text composable with a
     * fixed height and width.
     *
     * @sample androidx.compose.ui.text.samples.TextOverflowVisibleFixedSizeSample
     *
     * To make the background and click region expand to match the size of the text, allow it to
     * expand vertically/horizontally using `Modifier.heightIn`/`Modifier.widthIn` or similar.
     *
     * @sample androidx.compose.ui.text.samples.TextOverflowVisibleMinHeightSample
     *
     * Note: text that expands past its bounds using `Visible` may be clipped by other modifiers
     * such as `Modifier.clipToBounds`.
     */
    Visible
}
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
package androidx.wear.compose.material

/**
 * Contains useful information about an individual item in a [ScalingLazyColumn].
 *
 * @see ScalingLazyListLayoutInfo
 */
public sealed interface ScalingLazyListItemInfo {
    /**
     * The index of the item in the list.
     */
    val index: Int

    /**
     * The key of the item which was passed to the item() or items() function.
     */
    val key: Any

    /**
     * The main axis offset of the item before adjustment for scaling of the items in the viewport.
     *
     * The offset is relative to the center-line of the viewport of the lazy list container and
     * takes the [ScalingLazyListAnchorType] into account.
     *
     * For [ScalingLazyListAnchorType.ItemCenter] the offset is from the center of the list item to
     * the center-line of the viewport.
     *
     * For [ScalingLazyListAnchorType.ItemStart] if is the offset
     * between the start (edge) of the item and the center-line of the viewport, for normal layout
     * this will be the top edge of the item, for reverseLayout it will be the bottom edge.
     */
    val unadjustedOffset: Int

    /**
     * The main axis offset of the item after adjustment for scaling of the items in the viewport.
     *
     * The offset is relative to the center-line of the viewport of the lazy list container and
     * takes the [ScalingLazyListAnchorType] into account.
     *
     * For [ScalingLazyListAnchorType.ItemCenter] the offset is from the center of the list item to
     * the center-line of the viewport.
     *
     * For [ScalingLazyListAnchorType.ItemStart] if is the offset
     * between the start (edge) of the item and the center-line of the viewport, for normal layout
     * this will be the top edge of the item, for reverseLayout it will be the bottom edge.
     */
    val offset: Int

    /**
     * The scaled/adjusted main axis size of the item. Note that if you emit multiple layouts in the
     * composable slot for the item then this size will be calculated as the sum of their sizes.
     */
    val size: Int

    /**
     * How much scaling has been applied to the item, between 0 and 1
     */
    val scale: Float

    /**
     * How much alpha has been applied to the item, between 0 and 1
     */
    val alpha: Float

    /**
     * The original (before scaling) size of the list item
     */
    val unadjustedSize: Int
}

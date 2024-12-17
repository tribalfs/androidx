/*
 * Copyright 2024 The Android Open Source Project
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

package androidx.wear.protolayout.material3

import androidx.wear.protolayout.ColorBuilders.ColorProp

/**
 * Represents colors used in card components, such as [titleCard].
 *
 * @param background [ColorProp] which is used to as the background color for the card.
 * @param content the content color for the card.
 * @param time the color used for time for the card.
 * @param title the color used for title for the card.
 */
public class CardColors(
    public val background: ColorProp,
    public val title: ColorProp,
    public val content: ColorProp,
    public val time: ColorProp = content
)

public object CardDefaults {
    /**
     * [CardColors] for the high-emphasis card representing the primary, most important or most
     * common action on a screen.
     *
     * These colors are using [ColorScheme.primary] for background color and [ColorScheme.onPrimary]
     * for content colors.
     */
    public fun MaterialScope.filledCardColors(): CardColors =
        CardColors(
            background = theme.colorScheme.primary,
            title = theme.colorScheme.onPrimary,
            content = theme.colorScheme.onPrimary.withOpacity(0.8f)
        )

    /**
     * [CardColors] for the medium-emphasis card.
     *
     * These colors are using [ColorScheme.surfaceContainer] for background color and
     * [ColorScheme.onSurface] and [ColorScheme.onSurfaceVariant] for content colors.
     */
    public fun MaterialScope.filledTonalCardColors(): CardColors =
        CardColors(
            background = theme.colorScheme.surfaceContainer,
            title = theme.colorScheme.onSurface,
            content = theme.colorScheme.onSurfaceVariant
        )

    /**
     * Alternative [CardColors] for the high-emphasis card.
     *
     * These colors are using [ColorScheme.primaryContainer] for background color and
     * [ColorScheme.primaryContainer] for content colors.
     */
    public fun MaterialScope.filledVariantCardColors(): CardColors =
        CardColors(
            background = theme.colorScheme.primaryContainer,
            title = theme.colorScheme.onPrimaryContainer,
            content = theme.colorScheme.onPrimaryContainer.withOpacity(0.9f)
        )

    /**
     * Alternative [CardColors] for the card with [backgroundImage] as a background.
     *
     * These colors are using [ColorScheme.onBackground] for content colors.
     */
    public fun MaterialScope.imageBackgroundCardColors(): CardColors =
        CardColors(
            background = theme.colorScheme.background,
            title = theme.colorScheme.onBackground,
            content = theme.colorScheme.onBackground
        )

    internal const val METADATA_TAG: String = "CR"
    internal const val DEFAULT_CONTENT_PADDING: Int = 4
}

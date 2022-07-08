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

package androidx.wear.tiles.material;

import static androidx.wear.tiles.DimensionBuilders.dp;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.wear.tiles.DimensionBuilders.DpProp;

/** Contains the default values used by chip Tiles components. */
public class ChipDefaults {
    private ChipDefaults() {}

    /**
     * The default height for standard {@link Chip}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static final DpProp DEFAULT_HEIGHT = dp(52);

    /**
     * The default height for standard {@link CompactChip}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static final DpProp COMPACT_HEIGHT = dp(32);

    /**
     * The default height of tappable area for standard {@link CompactChip}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static final DpProp COMPACT_HEIGHT_TAPPABLE = dp(48);

    /**
     * The default height for standard {@link TitleChip}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static final DpProp TITLE_HEIGHT = dp(60);

    /**
     * The recommended horizontal margin used for width for standard {@link Chip}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    public static final float DEFAULT_MARGIN_PERCENT = 5.2f;

    /**
     * The recommended horizontal padding for standard {@link Chip}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static final DpProp HORIZONTAL_PADDING = dp(14);

    /**
     * The recommended horizontal padding for standard {@link CompactChip}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static final DpProp COMPACT_HORIZONTAL_PADDING = dp(12);

    /**
     * The recommended horizontal padding for standard {@link TitleChip}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static final DpProp TITLE_HORIZONTAL_PADDING = dp(16);

    /**
     * The recommended vertical space between icon and text in standard {@link Chip}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static final DpProp ICON_SPACER_WIDTH = dp(6);

    /**
     * The icon size used in standard {@link Chip}
     *
     * @hide
     */
    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    public static final DpProp ICON_SIZE = dp(24);

    /** The recommended colors for a primary {@link Chip}. */
    @NonNull
    public static final ChipColors PRIMARY_COLORS = ChipColors.primaryChipColors(Colors.DEFAULT);

    /** The recommended colors for a secondary {@link Chip}. */
    @NonNull
    public static final ChipColors SECONDARY_COLORS =
            ChipColors.secondaryChipColors(Colors.DEFAULT);

    /** The recommended colors for a primary {@link CompactChip}. */
    @NonNull
    public static final ChipColors COMPACT_PRIMARY_COLORS =
            ChipColors.primaryChipColors(Colors.DEFAULT);

    /** The recommended colors for a secondary {@link CompactChip}. */
    @NonNull
    public static final ChipColors COMPACT_SECONDARY_COLORS =
            ChipColors.secondaryChipColors(Colors.DEFAULT);

    /** The recommended colors for a primary {@link TitleChip}. */
    @NonNull
    public static final ChipColors TITLE_PRIMARY_COLORS =
            ChipColors.primaryChipColors(Colors.DEFAULT);

    /** The recommended colors for a secondary {@link TitleChip}. */
    @NonNull
    public static final ChipColors TITLE_SECONDARY_COLORS =
            ChipColors.secondaryChipColors(Colors.DEFAULT);
}

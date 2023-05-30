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

package androidx.wear.protolayout.material;

import static androidx.wear.protolayout.DimensionBuilders.wrap;
import static androidx.wear.protolayout.LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER;
import static androidx.wear.protolayout.material.ChipDefaults.COMPACT_HEIGHT;
import static androidx.wear.protolayout.material.ChipDefaults.COMPACT_HORIZONTAL_PADDING;
import static androidx.wear.protolayout.material.ChipDefaults.COMPACT_PRIMARY_COLORS;
import static androidx.wear.protolayout.material.ChipDefaults.MIN_TAPPABLE_HEIGHT;
import static androidx.wear.protolayout.material.Helper.checkNotNull;
import static androidx.wear.protolayout.material.Helper.checkTag;
import static androidx.wear.protolayout.material.Helper.getTagBytes;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.annotation.RestrictTo;
import androidx.annotation.RestrictTo.Scope;
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.LayoutElementBuilders.Box;
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement;
import androidx.wear.protolayout.ModifiersBuilders.Clickable;
import androidx.wear.protolayout.ModifiersBuilders.ElementMetadata;
import androidx.wear.protolayout.ModifiersBuilders.Modifiers;
import androidx.wear.protolayout.expression.Fingerprint;
import androidx.wear.protolayout.expression.ProtoLayoutExperimental;
import androidx.wear.protolayout.proto.LayoutElementProto;

/**
 * ProtoLayout component {@link CompactChip} that represents clickable object with the text.
 *
 * <p>The Chip is Stadium shape and has a max height designed to take no more than one line of text
 * of {@link Typography#TYPOGRAPHY_CAPTION1} style. Width of the chip is adjustable to the text
 * size.
 *
 * <p>The recommended set of {@link ChipColors} styles can be obtained from {@link ChipDefaults}.,
 * e.g. {@link ChipDefaults#COMPACT_PRIMARY_COLORS} to get a color scheme for a primary {@link
 * CompactChip}.
 *
 * <p>When accessing the contents of a container for testing, note that this element can't be simply
 * casted back to the original type, i.e.:
 *
 * <pre>{@code
 * CompactChip chip = new CompactChip...
 * Box box = new Box.Builder().addContent(chip).build();
 *
 * CompactChip myChip = (CompactChip) box.getContents().get(0);
 * }</pre>
 *
 * will fail.
 *
 * <p>To be able to get {@link CompactChip} object from any layout element, {@link
 * #fromLayoutElement} method should be used, i.e.:
 *
 * <pre>{@code
 * CompactChip myChip = CompactChip.fromLayoutElement(box.getContents().get(0));
 * }</pre>
 */
public class CompactChip implements LayoutElement {
    /** Tool tag for Metadata in Modifiers, so we know that Box is actually a CompactChip. */
    static final String METADATA_TAG = "CMPCHP";

    @NonNull private final Box mImpl;
    @NonNull private final Chip mElement;

    CompactChip(@NonNull Box element) {
        this.mImpl = element;
        // We know for sure that content of the Box is Chip.
        this.mElement = new Chip((Box) element.getContents().get(0));
    }

    /** Builder class for {@link androidx.wear.protolayout.material.CompactChip}. */
    public static final class Builder implements LayoutElement.Builder {
        @NonNull private final Context mContext;
        @NonNull private final String mText;
        @NonNull private final Clickable mClickable;
        @NonNull private final DeviceParameters mDeviceParameters;
        @NonNull private ChipColors mChipColors = COMPACT_PRIMARY_COLORS;
        private boolean mIsFontPaddingExcluded = false;

        /**
         * Creates a builder for the {@link CompactChip} with associated action and the given text
         *
         * @param context The application's context.
         * @param text The text to be displayed in this compact chip.
         * @param clickable Associated {@link Clickable} for click events. When the CompactChip is
         *     clicked it will fire the associated action.
         * @param deviceParameters The device parameters used for styling text.
         */
        public Builder(
                @NonNull Context context,
                @NonNull String text,
                @NonNull Clickable clickable,
                @NonNull DeviceParameters deviceParameters) {
            this.mContext = context;
            this.mText = text;
            this.mClickable = clickable;
            this.mDeviceParameters = deviceParameters;
        }

        /**
         * Sets the colors for the {@link CompactChip}. If set, {@link
         * ChipColors#getBackgroundColor()} will be used for the background of the button and {@link
         * ChipColors#getContentColor()} for the text. If not set, {@link
         * ChipDefaults#COMPACT_PRIMARY_COLORS} will be used.
         */
        @NonNull
        public Builder setChipColors(@NonNull ChipColors chipColors) {
            mChipColors = chipColors;
            return this;
        }

        /**
         * Sets whether the font padding is excluded or not. If not set, default to false, meaning
         * that text will have font padding included.
         *
         * <p>Setting this to {@code true} will perfectly align the text label.
         */
        @NonNull
        @ProtoLayoutExperimental
        @SuppressWarnings("MissingGetterMatchingBuilder")
        public Builder setExcludeFontPadding(boolean excluded) {
            this.mIsFontPaddingExcluded = excluded;
            return this;
        }

        /** Constructs and returns {@link CompactChip} with the provided content and look. */
        @NonNull
        @Override
        @OptIn(markerClass = ProtoLayoutExperimental.class)
        public CompactChip build() {
            Chip.Builder chipBuilder =
                    new Chip.Builder(mContext, mClickable, mDeviceParameters)
                            .setMetadataTag(METADATA_TAG)
                            .setChipColors(mChipColors)
                            .setContentDescription(mText)
                            .setHorizontalAlignment(HORIZONTAL_ALIGN_CENTER)
                            .setWidth(wrap())
                            .setHeight(COMPACT_HEIGHT)
                            .setMaxLines(1)
                            .setHorizontalPadding(COMPACT_HORIZONTAL_PADDING)
                            .setPrimaryLabelContent(mText)
                            .setPrimaryLabelTypography(Typography.TYPOGRAPHY_CAPTION1)
                            .setPrimaryLabelExcludeFontPadding(mIsFontPaddingExcluded)
                            .setIsPrimaryLabelScalable(false);

            Box tappableChip =
                    new Box.Builder()
                            .setModifiers(
                                    new Modifiers.Builder()
                                            .setClickable(mClickable)
                                            .setMetadata(
                                                    new ElementMetadata.Builder()
                                                            .setTagData(getTagBytes(METADATA_TAG))
                                                            .build())
                                            .build())
                            .setWidth(wrap())
                            .setHeight(MIN_TAPPABLE_HEIGHT)
                            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                            .addContent(chipBuilder.build())
                            .build();

            return new CompactChip(tappableChip);
        }
    }

    /** Returns click event action associated with this Chip. */
    @NonNull
    public Clickable getClickable() {
        return mElement.getClickable();
    }

    /** Returns chip color of this Chip. */
    @NonNull
    public ChipColors getChipColors() {
        return mElement.getChipColors();
    }

    /** Returns text content of this Chip. */
    @NonNull
    public String getText() {
        return checkNotNull(mElement.getPrimaryLabelContent());
    }

    /** Returns metadata tag set to this CompactChip, which should be {@link #METADATA_TAG}. */
    @NonNull
    String getMetadataTag() {
        return mElement.getMetadataTag();
    }

    /**
     * Returns CompactChip object from the given LayoutElement (e.g. one retrieved from a
     * container's content with {@code container.getContents().get(index)}) if that element can be
     * converted to CompactChip. Otherwise, it will return null.
     */
    @Nullable
    public static CompactChip fromLayoutElement(@NonNull LayoutElement element) {
        if (element instanceof CompactChip) {
            return (CompactChip) element;
        }
        if (!(element instanceof Box)) {
            return null;
        }
        Box boxElement = (Box) element;
        if (!checkTag(boxElement.getModifiers(), METADATA_TAG)) {
            return null;
        }
        // Now to check that inner content of the Box is CompactChip's Chip.
        LayoutElement innerElement = boxElement.getContents().get(0);
        if (!(innerElement instanceof Box)) {
            return null;
        }
        Box innerBoxElement = (Box) innerElement;
        if (!checkTag(innerBoxElement.getModifiers(), METADATA_TAG)) {
            return null;
        }

        // Now we are sure that this element is a CompactChip.
        return new CompactChip(boxElement);
    }

    /**
     *  Returns whether the font padding for the primary label is excluded.
     */
    @ProtoLayoutExperimental
    public boolean hasExcludeFontPadding() {
        return mElement.hasPrimaryLabelExcludeFontPadding();
    }

    @RestrictTo(Scope.LIBRARY_GROUP)
    @NonNull
    @Override
    public LayoutElementProto.LayoutElement toLayoutElementProto() {
        return mImpl.toLayoutElementProto();
    }

    @Nullable
    @Override
    @RestrictTo(Scope.LIBRARY_GROUP)
    public Fingerprint getFingerprint() {
        return mImpl.getFingerprint();
    }
}

/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.car.app.model.constraints;

import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.Row;

/**
 * Encapsulates the constraints to apply when rendering a {@link Row} in different contexts.
 *
 * @hide
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class RowConstraints {
    @NonNull
    public static final RowConstraints UNCONSTRAINED = RowConstraints.builder().build();

    /** Conservative constraints for a row. */
    @NonNull
    public static final RowConstraints ROW_CONSTRAINTS_CONSERVATIVE =
            RowConstraints.builder()
                    .setMaxActionsExclusive(0)
                    .setImageAllowed(false)
                    .setMaxTextLinesPerRow(1)
                    .setOnClickListenerAllowed(true)
                    .setToggleAllowed(false)
                    .build();

    /** The constraints for a full-width row in a pane. */
    @NonNull
    public static final RowConstraints ROW_CONSTRAINTS_PANE =
            RowConstraints.builder()
                    .setMaxActionsExclusive(2)
                    .setImageAllowed(true)
                    .setMaxTextLinesPerRow(2)
                    .setToggleAllowed(false)
                    .setOnClickListenerAllowed(false)
                    .build();

    /** The constraints for a simple row (2 rows of text and 1 image */
    @NonNull
    public static final RowConstraints ROW_CONSTRAINTS_SIMPLE =
            RowConstraints.builder()
                    .setMaxActionsExclusive(0)
                    .setImageAllowed(true)
                    .setMaxTextLinesPerRow(2)
                    .setToggleAllowed(false)
                    .setOnClickListenerAllowed(true)
                    .build();

    /** The constraints for a full-width row in a list (simple + toggle support). */
    @NonNull
    public static final RowConstraints ROW_CONSTRAINTS_FULL_LIST =
            ROW_CONSTRAINTS_SIMPLE.newBuilder().setToggleAllowed(true).build();

    private final int mMaxTextLinesPerRow;
    private final int mMaxActionsExclusive;
    private final boolean mIsImageAllowed;
    private final boolean mIsToggleAllowed;
    private final boolean mIsOnClickListenerAllowed;
    private final CarIconConstraints mCarIconConstraints;

    /**
     * Returns a new {@link Builder}.
     */
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Returns a new builder that contains the same data as this {@link RowConstraints} instance.
     */
    @NonNull
    public Builder newBuilder() {
        return new Builder(this);
    }

    /** Returns whether the row can have a click listener associated with it. */
    public boolean isOnClickListenerAllowed() {
        return mIsOnClickListenerAllowed;
    }

    /** Returns the maximum number lines of text, excluding the title, to render in the row. */
    public int getMaxTextLinesPerRow() {
        return mMaxTextLinesPerRow;
    }

    /** Returns the maximum number actions to allowed in a row that consists only of actions. */
    public int getMaxActionsExclusive() {
        return mMaxActionsExclusive;
    }

    /** Returns whether a toggle can be added to the row. */
    public boolean isToggleAllowed() {
        return mIsToggleAllowed;
    }

    /** Returns whether an image can be added to the row. */
    public boolean isImageAllowed() {
        return mIsImageAllowed;
    }

    /** Returns the {@link CarIconConstraints} enforced for the row images. */
    @NonNull
    public CarIconConstraints getCarIconConstraints() {
        return mCarIconConstraints;
    }

    /**
     * Validates that the given row satisfies this {@link RowConstraints} instance.
     *
     * @throws IllegalArgumentException if the constraints are not met.
     */
    public void validateOrThrow(@NonNull Object rowObj) {
        Row row = (Row) rowObj;

        if (!mIsOnClickListenerAllowed && row.getOnClickListener() != null) {
            throw new IllegalArgumentException("A click listener is not allowed on the row");
        }

        if (!mIsToggleAllowed && row.getToggle() != null) {
            throw new IllegalArgumentException("A toggle is not allowed on the row");
        }

        CarIcon image = row.getImage();
        if (image != null) {
            if (!mIsImageAllowed) {
                throw new IllegalArgumentException("An image is not allowed on the row");
            }

            mCarIconConstraints.validateOrThrow(image);
        }

        if (row.getTexts().size() > mMaxTextLinesPerRow) {
            throw new IllegalArgumentException(
                    "The number of lines of texts for the row exceeded the supported max of "
                            + mMaxTextLinesPerRow);
        }
    }

    RowConstraints(Builder builder) {
        mIsOnClickListenerAllowed = builder.mIsOnClickListenerAllowed;
        mMaxTextLinesPerRow = builder.mMaxTextLines;
        mMaxActionsExclusive = builder.mMaxActionsExclusive;
        mIsToggleAllowed = builder.mIsToggleAllowed;
        mIsImageAllowed = builder.mIsImageAllowed;
        mCarIconConstraints = builder.mCarIconConstraints;
    }

    /** A builder of {@link RowConstraints}. */
    public static final class Builder {
        boolean mIsOnClickListenerAllowed = true;
        boolean mIsToggleAllowed = true;
        int mMaxTextLines = Integer.MAX_VALUE;
        int mMaxActionsExclusive = Integer.MAX_VALUE;
        boolean mIsImageAllowed = true;
        CarIconConstraints mCarIconConstraints = CarIconConstraints.UNCONSTRAINED;

        /** Sets whether the row can have a click listener associated with it. */
        @NonNull
        public Builder setOnClickListenerAllowed(boolean isOnClickListenerAllowed) {
            this.mIsOnClickListenerAllowed = isOnClickListenerAllowed;
            return this;
        }

        /** Sets the maximum number lines of text, excluding the title, to render in the row. */
        @NonNull
        public Builder setMaxTextLinesPerRow(int maxTextLinesPerRow) {
            this.mMaxTextLines = maxTextLinesPerRow;
            return this;
        }

        /** Sets the maximum number actions to allowed in a row that consists only of actions. */
        @NonNull
        public Builder setMaxActionsExclusive(int maxActionsExclusive) {
            this.mMaxActionsExclusive = maxActionsExclusive;
            return this;
        }

        /** Sets whether an image can be added to the row. */
        @NonNull
        public Builder setImageAllowed(boolean imageAllowed) {
            this.mIsImageAllowed = imageAllowed;
            return this;
        }

        /** Sets whether a toggle can be added to the row. */
        @NonNull
        public Builder setToggleAllowed(boolean toggleAllowed) {
            this.mIsToggleAllowed = toggleAllowed;
            return this;
        }

        /** Sets the {@link CarIconConstraints} enforced for the row images. */
        @NonNull
        public Builder setCarIconConstraints(@NonNull CarIconConstraints carIconConstraints) {
            this.mCarIconConstraints = carIconConstraints;
            return this;
        }

        /**
         * Constructs the {@link RowConstraints} defined by this builder.
         */
        @NonNull
        public RowConstraints build() {
            return new RowConstraints(this);
        }

        Builder() {
        }

        Builder(RowConstraints constraints) {
            mIsOnClickListenerAllowed = constraints.isOnClickListenerAllowed();
            mMaxTextLines = constraints.getMaxTextLinesPerRow();
            mMaxActionsExclusive = constraints.getMaxActionsExclusive();
            mIsToggleAllowed = constraints.isToggleAllowed();
            mIsImageAllowed = constraints.isImageAllowed();
            mCarIconConstraints = constraints.getCarIconConstraints();
        }
    }
}

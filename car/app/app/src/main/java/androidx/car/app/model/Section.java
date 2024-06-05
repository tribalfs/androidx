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

package androidx.car.app.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.annotations.CarProtocol;
import androidx.car.app.annotations.ExperimentalCarApi;
import androidx.car.app.annotations.KeepFields;
import androidx.car.app.model.constraints.CarTextConstraints;

import com.google.errorprone.annotations.CanIgnoreReturnValue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The generic interface for a single section within a sectioned item template. Sections only allow
 * a single type of {@link Item} to be added.
 *
 * @param <T> the type of {@link Item} that's allowed to be added to this section
 */
@KeepFields
@CarProtocol
@ExperimentalCarApi
public abstract class Section<T extends Item> {
    @NonNull
    private final List<T> mItems;
    @Nullable
    private final CarText mTitle;
    @Nullable
    private final CarText mNoItemsMessage;

    // Empty constructor for serialization
    protected Section() {
        mItems = Collections.emptyList();
        mTitle = null;
        mNoItemsMessage = null;
    }

    /** Constructor that fills out fields from any section builder. */
    protected Section(@NonNull BaseBuilder<T, ?> builder) {
        mItems = Collections.unmodifiableList(builder.mItems);
        mTitle = builder.mHeader;
        mNoItemsMessage = builder.mNoItemsMessage;
    }

    /** Returns the items added to this section. */
    @NonNull
    public List<T> getItems() {
        return mItems;
    }

    /** Returns the optional text that should appear with the items in this section. */
    @Nullable
    public CarText getTitle() {
        return mTitle;
    }

    /**
     * Returns the optional message that should appear if there are no items added to this section.
     */
    @Nullable
    public CarText getNoItemsMessage() {
        return mNoItemsMessage;
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (other == null) {
            return false;
        }
        if (!(other instanceof Section)) {
            return false;
        }
        Section<?> section = (Section<?>) other;

        return Objects.equals(mItems, section.mItems) && Objects.equals(mTitle, section.mTitle)
                && Objects.equals(mNoItemsMessage, section.mNoItemsMessage);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mItems, mTitle, mNoItemsMessage);
    }

    @NonNull
    @Override
    public String toString() {
        return "Section { items: " + mItems + ", title: " + mTitle + ", noItemsMessage: "
                + mNoItemsMessage + " }";
    }

    /**
     * Generic {@link Section} builder that contains the fields that all sections share.
     *
     * @param <T> The {@link Item} type that this section contains
     * @param <B> The builder type to return for the builder methods
     */
    @SuppressWarnings({"StaticFinalBuilder", "MissingBuildMethod"})
    protected abstract static class BaseBuilder<T extends Item, B> {
        @SuppressWarnings({"MutableBareField", "InternalField"})
        @NonNull
        protected List<T> mItems = new ArrayList<>();
        @SuppressWarnings({"MutableBareField", "InternalField"})
        @Nullable
        protected CarText mHeader;
        @SuppressWarnings({"MutableBareField", "InternalField"})
        @Nullable
        protected CarText mNoItemsMessage;

        protected BaseBuilder() {
        }

        protected BaseBuilder(@NonNull Section<T> section) {
            mItems = section.mItems;
            mHeader = section.mTitle;
            mNoItemsMessage = section.mNoItemsMessage;
        }

        /** Sets the items for this section, overwriting any other previously set items. */
        @NonNull
        @CanIgnoreReturnValue
        @SuppressWarnings({"SetterReturnsThis", "unchecked"})
        public B setItems(@NonNull List<T> items) {
            mItems = items;
            return (B) this;
        }

        /** Adds an item to this section, appending to the existing list of items. */
        @NonNull
        @CanIgnoreReturnValue
        @SuppressWarnings({"SetterReturnsThis", "unchecked"})
        public B addItem(@NonNull T item) {
            mItems.add(item);
            return (B) this;
        }

        /** Delete all items in this section. */
        @NonNull
        @CanIgnoreReturnValue
        @SuppressWarnings({"SetterReturnsThis", "unchecked"})
        public B clearItems() {
            mItems.clear();
            return (B) this;
        }

        /**
         * Sets or clears the optional title that appears above the items in this section. If not
         * set, no title shows up. The title must conform to {@link CarTextConstraints#TEXT_ONLY}
         * constraints.
         */
        @NonNull
        @CanIgnoreReturnValue
        @SuppressWarnings({"SetterReturnsThis", "unchecked"})
        public B setTitle(@Nullable CharSequence title) {
            if (title == null) {
                mHeader = null;
                return (B) this;
            }

            CarText carText = CarText.create(title);
            CarTextConstraints.TEXT_ONLY.validateOrThrow(carText);
            mHeader = carText;
            return  (B) this;
        }

        /**
         * Sets or clears the optional title that appears above the items in this section. If not
         * set, no title shows up. The title must conform to {@link CarTextConstraints#TEXT_ONLY}
         * constraints.
         */
        @NonNull
        @CanIgnoreReturnValue
        @SuppressWarnings({"SetterReturnsThis", "unchecked"})
        public B setTitle(@Nullable CarText title) {
            if (title != null) {
                CarTextConstraints.TEXT_ONLY.validateOrThrow(title);
            }
            mHeader = title;
            return (B) this;
        }

        /**
         * Sets or clears the optional message to display in this section when there are 0 items
         * added to it. If not set, this section will not show any message when there are 0 items.
         * The message must conform to {@link CarTextConstraints#TEXT_ONLY} constraints.
         */
        @NonNull
        @CanIgnoreReturnValue
        @SuppressWarnings({"SetterReturnsThis", "unchecked"})
        public B setNoItemsMessage(@Nullable CharSequence noItemsMessage) {
            if (noItemsMessage == null) {
                mNoItemsMessage = null;
                return (B) this;
            }

            CarText carText = CarText.create(noItemsMessage);
            CarTextConstraints.TEXT_ONLY.validateOrThrow(carText);
            mNoItemsMessage = carText;
            return (B) this;
        }

        /**
         * Sets or clears the optional message to display in this section when there are 0 items
         * added to it. If not set, this section will not show any message when there are 0 items.
         * The message must conform to {@link CarTextConstraints#TEXT_ONLY} constraints.
         */
        @NonNull
        @CanIgnoreReturnValue
        @SuppressWarnings({"SetterReturnsThis", "unchecked"})
        public B setNoItemsMessage(@Nullable CarText noItemsMessage) {
            if (noItemsMessage != null) {
                CarTextConstraints.TEXT_ONLY.validateOrThrow(noItemsMessage);
            }
            mNoItemsMessage = noItemsMessage;
            return (B) this;
        }
    }
}

/*
 * Copyright 2022 The Android Open Source Project
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

package androidx.car.app.messaging.model;

import static java.util.Objects.requireNonNull;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.annotations.CarProtocol;
import androidx.car.app.annotations.ExperimentalCarApi;
import androidx.car.app.annotations.RequiresCarApi;
import androidx.car.app.model.CarIcon;
import androidx.car.app.model.CarText;
import androidx.car.app.model.Item;

import java.util.ArrayList;
import java.util.List;

/** Represents a conversation */
@ExperimentalCarApi
@CarProtocol
@RequiresCarApi(6)
public class ConversationItem implements Item {
    @NonNull private final String mId;
    @NonNull private final CarText mTitle;
    @Nullable
    private final CarIcon mIcon;
    private final boolean mIsGroupConversation;
    @NonNull private final List<CarMessage> mMessages;

    ConversationItem(@NonNull Builder builder) {
        this.mId = requireNonNull(builder.mId);
        this.mTitle = requireNonNull(builder.mTitle);
        this.mIcon = builder.mIcon;
        this.mIsGroupConversation = builder.mIsGroupConversation;
        this.mMessages = requireNonNull(builder.mMessages);
    }

    /** Default constructor for serialization. */
    private ConversationItem() {
        mId = "";
        mTitle = new CarText.Builder("").build();
        mIcon = null;
        mIsGroupConversation = false;
        mMessages = new ArrayList<>();
    }

    /**
     * Returns a unique identifier for the conversation
     *
     * @see Builder#setId
     */
    public @NonNull String getId() {
        return mId;
    }

    /** Returns the title of the conversation */
    public @NonNull CarText getTitle() {
        return mTitle;
    }

    /** Returns a {@link CarIcon} for the conversation, or {@code null} if not set */
    public @Nullable CarIcon getIcon() {
        return mIcon;
    }

    /**
     * Returns whether this conversation involves 3+ participants (a "group" conversation)
     *
     * @see Builder#setGroupConversation(boolean)
     */
    public boolean isGroupConversation() {
        return mIsGroupConversation;
    }

    /** Returns a list of messages for this {@link ConversationItem} */
    public @NonNull List<CarMessage> getMessages() {
        return mMessages;
    }

    /** A builder for {@link ConversationItem} */
    public static final class Builder {
        @Nullable
        String mId;
        @Nullable
        CarText mTitle;
        @Nullable
        CarIcon mIcon;
        boolean mIsGroupConversation;
        @Nullable
        List<CarMessage> mMessages;

        /**
         * Specifies a unique identifier for the conversation
         *
         * <p> IDs may be used for a variety of purposes, including...
         * <ul>
         *     <li> Distinguishing new {@link ConversationItem}s from updated
         *     {@link ConversationItem}s in the UI, when data is refreshed
         *     <li> Identifying {@link ConversationItem}s in "mark as read" / "reply" callbacks
         * </ul>
         */
        public @NonNull Builder setId(@NonNull String id) {
            mId = id;
            return this;
        }

        /** Sets the title of the conversation */
        public @NonNull Builder setTitle(@NonNull CarText title) {
            mTitle = title;
            return this;
        }

        /** Sets a {@link CarIcon} for the conversation */
        public @NonNull Builder setIcon(@NonNull CarIcon icon) {
            mIcon = icon;
            return this;
        }

        /**
         * Specifies whether this conversation involves 3+ participants (a "group" conversation)
         *
         * <p> If unspecified, conversations are assumed to have exactly two participants (a "1:1"
         * conversation)
         *
         * <p> UX presentation may differ slightly between group and 1:1 conversations. As a
         * historical example, message readout may include sender names for group conversations, but
         * omit them for 1:1 conversations.
         */
        public @NonNull Builder setGroupConversation(boolean isGroupConversation) {
            mIsGroupConversation = isGroupConversation;
            return this;
        }

        /** Specifies a list of messages for the conversation */
        public @NonNull Builder setMessages(@NonNull List<CarMessage> messages) {
            mMessages = messages;
            return this;
        }

        /** Returns a new {@link ConversationItem} instance defined by this builder */
        public @NonNull ConversationItem build() {
            return new ConversationItem(this);
        }
    }
}

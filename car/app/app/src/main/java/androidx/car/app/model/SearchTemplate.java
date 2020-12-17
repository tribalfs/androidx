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

package androidx.car.app.model;

import static androidx.car.app.model.constraints.ActionsConstraints.ACTIONS_CONSTRAINTS_HEADER;
import static androidx.car.app.model.constraints.ActionsConstraints.ACTIONS_CONSTRAINTS_SIMPLE;
import static androidx.car.app.model.constraints.RowListConstraints.ROW_LIST_CONSTRAINTS_SIMPLE;

import android.annotation.SuppressLint;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.Screen;

import java.util.Collections;
import java.util.Objects;

/**
 * A model that allows the user to enter text searches, and can display results in a list.
 *
 * <h4>Template Restrictions</h4>
 *
 * In regards to template refreshes, as described in {@link Screen#onGetTemplate()}, this template
 * supports any content changes as refreshes. This allows apps to interactively update the search
 * results as the user types without the templates being counted against the quota.
 */
public final class SearchTemplate implements Template {

    /** A listener for search updates. */
    public interface SearchCallback {
        /**
         * Notifies the current {@code searchText}.
         *
         * <p>The host may invoke this callback as the user types a search text. The frequency of
         * these updates is not guaranteed to be after every individual keystroke. The host may
         * decide to wait for several keystrokes before sending a single update.
         *
         * @param searchText the current search text that the user has typed.
         */
        void onSearchTextChanged(@NonNull String searchText);

        /**
         * Notifies that the user has submitted the search and the given {@code searchText} is
         * the final term.
         *
         * @param searchText the search text that the user typed.
         */
        void onSearchSubmitted(@NonNull String searchText);
    }

    @Keep
    private final boolean mIsLoading;
    @Keep
    private final SearchCallbackWrapper mSearchCallback;
    @Keep
    @Nullable
    private final String mInitialSearchText;
    @Keep
    @Nullable
    private final String mSearchHint;
    @Keep
    @Nullable
    private final ItemList mItemList;
    @Keep
    private final boolean mShowKeyboardByDefault;
    @Keep
    @Nullable
    private final Action mHeaderAction;
    @Keep
    @Nullable
    private final ActionStrip mActionStrip;

    /**
     * Constructs a new builder of {@link SearchTemplate} with the input {@link SearchCallback}.
     *
     * <p>Note that the callback relates to UI events and will be executed on the main thread
     * using {@link Looper#getMainLooper()}.
     *
     * @param callback the callback to be invoked for events such as when the user types new
     *                 text, or submits a search.
     */
    @NonNull
    @SuppressLint("ExecutorRegistration")
    public static Builder builder(@NonNull SearchCallback callback) {
        return new Builder(callback);
    }

    public boolean isLoading() {
        return mIsLoading;
    }

    @Nullable
    public Action getHeaderAction() {
        return mHeaderAction;
    }

    /**
     * Returns the {@link ActionStrip} instance set in the template.
     */
    @Nullable
    public ActionStrip getActionStrip() {
        return mActionStrip;
    }

    /**
     * Returns the optional initial search text.
     *
     * @see Builder#setInitialSearchText
     */
    @Nullable
    public String getInitialSearchText() {
        return mInitialSearchText;
    }

    /**
     * Returns the optional search hint.
     *
     * @see Builder#setSearchHint
     */
    @Nullable
    public String getSearchHint() {
        return mSearchHint;
    }

    /**
     * Returns the optional {@link ItemList} for search results.
     *
     * @see Builder#getItemList
     */
    @Nullable
    public ItemList getItemList() {
        return mItemList;
    }

    /**
     * Returns the {@link SearchCallbackWrapper} for search callbacks.
     */
    @NonNull
    public SearchCallbackWrapper getSearchCallback() {
        return mSearchCallback;
    }

    /**
     * Returns whether to show the keyboard by default.
     *
     * @see Builder#setShowKeyboardByDefault
     */
    public boolean isShowKeyboardByDefault() {
        return mShowKeyboardByDefault;
    }

    @NonNull
    @Override
    public String toString() {
        return "SearchTemplate";
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mInitialSearchText,
                mIsLoading,
                mSearchHint,
                mItemList,
                mShowKeyboardByDefault,
                mHeaderAction,
                mActionStrip);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof SearchTemplate)) {
            return false;
        }
        SearchTemplate otherTemplate = (SearchTemplate) other;

        // Don't compare listener.
        return mIsLoading == otherTemplate.mIsLoading
                && Objects.equals(mInitialSearchText, otherTemplate.mInitialSearchText)
                && Objects.equals(mSearchHint, otherTemplate.mSearchHint)
                && Objects.equals(mItemList, otherTemplate.mItemList)
                && Objects.equals(mHeaderAction, otherTemplate.mHeaderAction)
                && Objects.equals(mActionStrip, otherTemplate.mActionStrip)
                && mShowKeyboardByDefault == otherTemplate.mShowKeyboardByDefault;
    }

    SearchTemplate(Builder builder) {
        mInitialSearchText = builder.mInitialSearchText;
        mSearchHint = builder.mSearchHint;
        mIsLoading = builder.mIsLoading;
        mItemList = builder.mItemList;
        mSearchCallback = builder.mSearchCallback;
        mShowKeyboardByDefault = builder.mShowKeyboardByDefault;
        mHeaderAction = builder.mHeaderAction;
        mActionStrip = builder.mActionStrip;
    }

    /** Constructs an empty instance, used by serialization code. */
    private SearchTemplate() {
        mInitialSearchText = null;
        mSearchHint = null;
        mIsLoading = false;
        mItemList = null;
        mHeaderAction = null;
        mActionStrip = null;
        mSearchCallback = null;
        mShowKeyboardByDefault = true;
    }

    /** A builder of {@link SearchTemplate}. */
    public static final class Builder {
        final SearchCallbackWrapper mSearchCallback;
        @Nullable
        String mInitialSearchText;
        @Nullable
        String mSearchHint;
        boolean mIsLoading;
        @Nullable
        ItemList mItemList;
        boolean mShowKeyboardByDefault = true;
        @Nullable
        Action mHeaderAction;
        @Nullable
        ActionStrip mActionStrip;

        Builder(SearchCallback callback) {
            mSearchCallback = SearchCallbackWrapperImpl.create(callback);
        }

        /**
         * Sets the {@link Action} that will be displayed in the header of the template, or
         * {@code null}
         * to not display an action.
         *
         * <h4>Requirements</h4>
         *
         * This template only supports either either one of {@link Action#APP_ICON} and {@link
         * Action#BACK} as a header {@link Action}.
         *
         * @throws IllegalArgumentException if {@code headerAction} does not meet the template's
         *                                  requirements.
         */
        @NonNull
        public Builder setHeaderAction(@Nullable Action headerAction) {
            ACTIONS_CONSTRAINTS_HEADER.validateOrThrow(
                    headerAction == null ? Collections.emptyList()
                            : Collections.singletonList(headerAction));
            this.mHeaderAction = headerAction;
            return this;
        }

        /**
         * Sets the {@link ActionStrip} for this template, or {@code null} to not display an {@link
         * ActionStrip}.
         *
         * <h4>Requirements</h4>
         *
         * This template allows up to 2 {@link Action}s in its {@link ActionStrip}. Of the 2 allowed
         * {@link Action}s, one of them can contain a title as set via
         * {@link Action.Builder#setTitle}. Otherwise, only {@link Action}s with icons are allowed.
         *
         * @throws IllegalArgumentException if {@code actionStrip} does not meet the template's
         *                                  requirements.
         */
        @NonNull
        public Builder setActionStrip(@Nullable ActionStrip actionStrip) {
            ACTIONS_CONSTRAINTS_SIMPLE.validateOrThrow(
                    actionStrip == null ? Collections.emptyList() : actionStrip.getActions());
            this.mActionStrip = actionStrip;
            return this;
        }

        /**
         * Sets the initial search text to display in the search box, or {@code null} to not
         * display any initial search text.
         *
         * <p>Defaults to {@code null}.
         */
        @NonNull
        public Builder setInitialSearchText(@Nullable String initialSearchText) {
            this.mInitialSearchText = initialSearchText;
            return this;
        }

        /**
         * Sets the text hint to display in the search box when it is empty, or {@code null} to
         * use a default search hint.
         *
         * <p>This is not the actual search text, and will disappear if user types any value into
         * the search.
         *
         * <p>If a non empty text is set via {@link #setInitialSearchText}, the {@code searchHint
         * } will not show, unless the user erases the search text.
         *
         * <p>Defaults to {@code null}.
         */
        @NonNull
        public Builder setSearchHint(@Nullable String searchHint) {
            this.mSearchHint = searchHint;
            return this;
        }

        /**
         * Sets whether the template is in a loading state.
         *
         * <p>If set to {@code true}, the UI will display a loading indicator where the list content
         * would be otherwise. The caller is expected to call {@link
         * androidx.car.app.Screen#invalidate()} and send the new template content
         * to the host once the data is ready. If set to {@code false}, the UI shows the {@link
         * ItemList} contents added via {@link #setItemList}.
         */
        @NonNull
        public Builder setLoading(boolean isLoading) {
            this.mIsLoading = isLoading;
            return this;
        }

        /**
         * Sets the {@link ItemList} to show for search results, or {@code null} if there are no
         * results.
         *
         * <p>The list will be shown below the search box, allowing users to click on individual
         * search results.
         *
         * <h4>Requirements</h4>
         *
         * This template allows up to 6 {@link Row}s in the {@link ItemList}. The host will
         * ignore any items over that limit. The list itself cannot be selectable as set via {@link
         * ItemList.Builder#setOnSelectedListener}. Each {@link Row} can add up to 2 lines of texts
         * via {@link Row.Builder#addText} and cannot contain a {@link Toggle}.
         *
         * @throws IllegalArgumentException if {@code itemList} does not meet the template's
         *                                  requirements.
         */
        @NonNull
        public Builder setItemList(@Nullable ItemList itemList) {
            if (itemList != null) {
                ROW_LIST_CONSTRAINTS_SIMPLE.validateOrThrow(itemList);
            }

            this.mItemList = itemList;
            return this;
        }

        /**
         * Sets if the keyboard should be displayed by default, instead of waiting until user
         * interacts with the search box.
         *
         * <p>Defaults to {@code true}.
         */
        @NonNull
        public Builder setShowKeyboardByDefault(boolean showKeyboardByDefault) {
            this.mShowKeyboardByDefault = showKeyboardByDefault;
            return this;
        }

        /**
         * Constructs the {@link SearchTemplate} model.
         *
         * @throws IllegalArgumentException if the template is in a loading state but the list is
         *                                  set.
         */
        @NonNull
        public SearchTemplate build() {
            if (mIsLoading && mItemList != null) {
                throw new IllegalArgumentException(
                        "Template is in a loading state but a list is set.");
            }

            return new SearchTemplate(this);
        }
    }
}

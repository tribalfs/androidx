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

import static java.util.Objects.requireNonNull;

import android.annotation.SuppressLint;
import android.os.RemoteException;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.car.app.IOnDoneCallback;
import androidx.car.app.IOnItemVisibilityChangedListener;
import androidx.car.app.IOnSelectedListener;
import androidx.car.app.WrappedRuntimeException;
import androidx.car.app.host.OnDoneCallback;
import androidx.car.app.host.OnItemVisibilityChangedListenerWrapper;
import androidx.car.app.host.OnSelectedListenerWrapper;
import androidx.car.app.host.model.OnClickListenerWrapper;
import androidx.car.app.utils.Logger;
import androidx.car.app.utils.RemoteUtils;
import androidx.car.app.utils.ValidationUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Represents a list of {@link Item} instances. {@link ItemList} instances are used by templates
 * that contain lists of models, such as for example, the list of {@link Row}s in a {@link
 * ListTemplate}.
 */
public final class ItemList {
    /**
     * A listener for handling selection events for lists with selectable items.
     *
     * @see Builder#setSelectable(OnSelectedListener)
     */
    public interface OnSelectedListener {
        /**
         * Notifies that an item was selected.
         *
         * <p>This event is called even if the selection did not change, for example, if the user
         * selected an already selected item.
         *
         * @param selectedIndex the index of the newly selected item.
         */
        void onSelected(int selectedIndex);
    }

    /** A listener for handling item visibility changes. */
    public interface OnItemVisibilityChangedListener {
        /**
         * Notifies that the items in the list within the specified indices have become visible.
         *
         * <p>The start index is inclusive, and the end index is exclusive. For example, if only the
         * first item in a list is visible, the start and end indices would be 0 and 1,
         * respectively. If no items are visible, the indices will be set to -1.
         *
         * @param startIndex the index of the first item that is visible.
         * @param endIndex   the index of the first item that is not visible after the visible
         *                   range.
         */
        void onItemVisibilityChanged(int startIndex, int endIndex);
    }

    @Keep
    private final int mSelectedIndex;
    @Keep
    private final List<Object> mItems;
    @Keep
    @Nullable
    private final OnSelectedListenerWrapper mOnSelectedListener;
    @Keep
    @Nullable
    private final OnItemVisibilityChangedListenerWrapper mOnItemVisibilityChangedListener;
    @Keep
    @Nullable
    private final CarText mNoItemsMessage;

    /** Constructs a new builder of {@link ItemList}. */
    @NonNull
    public static Builder builder() {
        return new Builder();
    }

    /** Returns the index of the selected item of the list. */
    public int getSelectedIndex() {
        return mSelectedIndex;
    }

    /**
     * Returns the {@link OnSelectedListenerWrapper} to be called when when an item is selected
     * by the user, or {@code null} is the list is non-selectable.
     */
    @Nullable
    public OnSelectedListenerWrapper getOnSelectedListener() {
        return mOnSelectedListener;
    }

    /** Returns the text to be displayed if the list is empty. */
    @Nullable
    public CarText getNoItemsMessage() {
        return mNoItemsMessage;
    }

    /**
     * Returns the {@link OnItemVisibilityChangedListenerWrapper} to be called when the visible
     * items in the list changes.
     */
    @Nullable
    public OnItemVisibilityChangedListenerWrapper getOnItemsVisibilityChangeListener() {
        return mOnItemVisibilityChangedListener;
    }

    /** Returns the list of items in this {@link ItemList}. */
    @NonNull
    public List<Object> getItems() {
        return mItems;
    }

    /**
     * Returns {@code true} if this {@link ItemList} instance is determined to be a refresh of the
     * given list, or {@code false} otherwise.
     *
     * <p>A list is considered a refresh if:
     *
     * <ul>
     *   <li>The other list is in a loading state, or
     *   <li>The item size and string contents of the two lists are the same. For rows that
     *   contain a
     *       {@link Toggle}, the string contents can be updated if the toggle state has changed
     *       between the previous and new rows. For grid items that contain a {@link Toggle}, string
     *       contents and images can be updated if the toggle state has changed.
     * </ul>
     */
    public boolean isRefresh(@Nullable ItemList other, @NonNull Logger logger) {
        if (other == null) {
            return false;
        }

        return ValidationUtils.itemsHaveSameContent(
                other.getItems(), other.getSelectedIndex(), getItems(), getSelectedIndex(), logger);
    }

    @Override
    @NonNull
    public String toString() {
        return "[ items: "
                + (mItems != null ? mItems.toString() : null)
                + ", selected: "
                + mSelectedIndex
                + "]";
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                mSelectedIndex,
                mItems,
                mOnSelectedListener == null,
                mOnItemVisibilityChangedListener == null,
                mNoItemsMessage);
    }

    @Override
    public boolean equals(@Nullable Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ItemList)) {
            return false;
        }
        ItemList otherList = (ItemList) other;

        // For listeners only check if they are either both null, or both set.
        return mSelectedIndex == otherList.mSelectedIndex
                && Objects.equals(mItems, otherList.mItems)
                && Objects.equals(mOnSelectedListener == null,
                otherList.mOnSelectedListener == null)
                && Objects.equals(
                mOnItemVisibilityChangedListener == null,
                otherList.mOnItemVisibilityChangedListener == null)
                && Objects.equals(mNoItemsMessage, otherList.mNoItemsMessage);
    }

    private ItemList(Builder builder) {
        mSelectedIndex = builder.mSelectedIndex;
        mItems = new ArrayList<>(builder.mItems);
        mNoItemsMessage = builder.mNoItemsMessage;
        mOnSelectedListener = builder.mOnSelectedListener;
        mOnItemVisibilityChangedListener = builder.mOnItemVisibilityChangedListener;
    }

    /** Constructs an empty instance, used by serialization code. */
    private ItemList() {
        mSelectedIndex = 0;
        mItems = Collections.emptyList();
        mNoItemsMessage = null;
        mOnSelectedListener = null;
        mOnItemVisibilityChangedListener = null;
    }

    /** A builder of {@link ItemList}. */
    public static final class Builder {
        private final List<Object> mItems = new ArrayList<>();
        private int mSelectedIndex;
        @Nullable
        private OnSelectedListenerWrapper mOnSelectedListener;
        @Nullable
        private OnItemVisibilityChangedListenerWrapper mOnItemVisibilityChangedListener;
        @Nullable
        private CarText mNoItemsMessage;

        /**
         * Sets the {@link OnItemVisibilityChangedListener} to call when the visible items in the
         * list changes.
         */
        @NonNull
        @SuppressLint("ExecutorRegistration")
        public Builder setOnItemsVisibilityChangeListener(
                @Nullable OnItemVisibilityChangedListener itemVisibilityChangedListener) {
            this.mOnItemVisibilityChangedListener =
                    itemVisibilityChangedListener == null
                            ? null
                            : createOnItemVisibilityChangedListener(itemVisibilityChangedListener);
            return this;
        }

        /**
         * Marks the list as selectable and sets the {@link OnSelectedListener} to call when an
         * item is selected by the user. Set to {@code null} to mark the list as non-selectable.
         *
         * <p>Selectable lists, where allowed by the template they are added to, automatically
         * display
         * an item in a selected state when selected by the user.
         *
         * <p>The items in the list define a mutually exclusive selection scope: only a single
         * item will
         * be selected at any given time.
         *
         * <p>The specific way in which the selection will be visualized depends on the template
         * and the
         * host implementation. For example, some templates may display the list as a radio button
         * group, while others may highlight the selected item's background.
         *
         * @see #setSelectedIndex(int)
         */
        @NonNull
        // TODO(rampara): Review if API should be updated to match getter.
        @SuppressLint({"MissingGetterMatchingBuilder", "ExecutorRegistration"})
        public Builder setSelectable(@Nullable OnSelectedListener onSelectedListener) {
            this.mOnSelectedListener =
                    onSelectedListener == null ? null : createOnSelectedListener(
                            onSelectedListener);
            return this;
        }

        /**
         * Sets the index of the item to show as selected.
         *
         * <p>By default and unless explicitly set with this method, the first item is selected.
         *
         * <p>If the list is not a selectable list set with {@link #setSelectable}, this value is
         * ignored.
         */
        @NonNull
        public Builder setSelectedIndex(int selectedIndex) {
            if (selectedIndex < 0) {
                throw new IllegalArgumentException(
                        "The item index must be larger than or equal to 0.");
            }
            this.mSelectedIndex = selectedIndex;
            return this;
        }

        /**
         * Sets the text to display if the list is empty.
         *
         * <p>If the list is empty and the app does not explicitly set the message with this
         * method, the
         * host will show a default message.
         */
        @NonNull
        public Builder setNoItemsMessage(@Nullable CharSequence noItemsMessage) {
            this.mNoItemsMessage = noItemsMessage == null ? null : CarText.create(noItemsMessage);
            return this;
        }

        /**
         * Adds an item to the list.
         *
         * @throws NullPointerException if {@code item} is {@code null}.
         */
        @NonNull
        public Builder addItem(@NonNull Item item) {
            mItems.add(requireNonNull(item));
            return this;
        }

        /** Clears any items that may have been added up to this point. */
        @NonNull
        public Builder clearItems() {
            mItems.clear();
            return this;
        }

        /**
         * Constructs the item list defined by this builder.
         *
         * @throws IllegalStateException if the list is selectable but does not have any items.
         * @throws IllegalStateException if the selected index is greater or equal to the size of
         *                               the
         *                               list.
         * @throws IllegalStateException if the list is selectable and any items have either one of
         *                               their {@link OnClickListener} or {@link Toggle} set.
         */
        @NonNull
        public ItemList build() {
            if (mOnSelectedListener != null) {
                int listSize = mItems.size();
                if (listSize == 0) {
                    throw new IllegalStateException("A selectable list cannot be empty");
                } else if (mSelectedIndex >= listSize) {
                    throw new IllegalStateException(
                            "The selected item index ("
                                    + mSelectedIndex
                                    + ") is larger than the size of the list ("
                                    + listSize
                                    + ")");
                }

                // Check that no items have disallowed elements if the list is selectable.
                for (Object item : mItems) {
                    if (getOnClickListener(item) != null) {
                        throw new IllegalStateException(
                                "Items that belong to selectable lists can't have an "
                                        + "onClickListener. Use the"
                                        + " OnSelectedListener of the list instead");
                    }

                    if (getToggle(item) != null) {
                        throw new IllegalStateException(
                                "Items that belong to selectable lists can't have a toggle");
                    }
                }
            }

            return new ItemList(this);
        }
    }

    @Nullable
    private static OnClickListenerWrapper getOnClickListener(Object item) {
        if (item instanceof Row) {
            return ((Row) item).getOnClickListener();
        } else if (item instanceof GridItem) {
            return ((GridItem) item).getOnClickListener();
        }

        return null;
    }

    @Nullable
    private static Toggle getToggle(Object item) {
        if (item instanceof Row) {
            return ((Row) item).getToggle();
        } else if (item instanceof GridItem) {
            return ((GridItem) item).getToggle();
        }

        return null;
    }

    private static OnSelectedListenerWrapper createOnSelectedListener(
            @NonNull OnSelectedListener listener) {
        return new OnSelectedListenerWrapper() {
            private final IOnSelectedListener mStubListener = new OnSelectedListenerStub(listener);

            @Override
            public void onSelected(int selectedIndex, @NonNull OnDoneCallback callback) {
                try {
                    mStubListener.onSelected(selectedIndex,
                            RemoteUtils.createOnDoneCallbackStub(callback));
                } catch (RemoteException e) {
                    throw new WrappedRuntimeException(e);
                }
            }
        };
    }

    @Keep // We need to keep these stub for Bundler serialization logic.
    private static class OnSelectedListenerStub extends IOnSelectedListener.Stub {
        private final OnSelectedListener mOnSelectedListener;

        private OnSelectedListenerStub(OnSelectedListener onSelectedListener) {
            this.mOnSelectedListener = onSelectedListener;
        }

        @Override
        public void onSelected(int index, IOnDoneCallback callback) {
            RemoteUtils.dispatchHostCall(
                    () -> mOnSelectedListener.onSelected(index), callback, "onSelectedListener");
        }
    }

    private static OnItemVisibilityChangedListenerWrapper createOnItemVisibilityChangedListener(
            @NonNull OnItemVisibilityChangedListener listener) {
        return new OnItemVisibilityChangedListenerWrapper() {
            private final IOnItemVisibilityChangedListener mStubListener =
                    new OnItemVisibilityChangedListenerStub(listener);

            @Override
            public void onItemVisibilityChanged(int startIndex, int rightIndex,
                    @NonNull OnDoneCallback callback) {
                try {
                    mStubListener.onItemVisibilityChanged(startIndex, rightIndex,
                            RemoteUtils.createOnDoneCallbackStub(callback));
                } catch (RemoteException e) {
                    throw new WrappedRuntimeException(e);
                }
            }
        };
    }

    /** Stub class for the {@link IOnItemVisibilityChangedListener} interface. */
    @Keep // We need to keep these stub for Bundler serialization logic.
    private static class OnItemVisibilityChangedListenerStub
            extends IOnItemVisibilityChangedListener.Stub {
        private final OnItemVisibilityChangedListener mOnItemVisibilityChangedListener;

        private OnItemVisibilityChangedListenerStub(
                OnItemVisibilityChangedListener onItemVisibilityChangedListener) {
            this.mOnItemVisibilityChangedListener = onItemVisibilityChangedListener;
        }

        @Override
        public void onItemVisibilityChanged(
                int startIndexInclusive, int endIndexExclusive, IOnDoneCallback callback) {
            RemoteUtils.dispatchHostCall(
                    () -> mOnItemVisibilityChangedListener.onItemVisibilityChanged(
                            startIndexInclusive, endIndexExclusive),
                    callback,
                    "onItemVisibilityChanged");
        }
    }
}

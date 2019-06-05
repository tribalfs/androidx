/*
 * Copyright 2019 The Android Open Source Project
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

package androidx.car.app;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.car.R;
import androidx.car.util.DropShadowScrollListener;
import androidx.car.widget.CheckBoxListItem;
import androidx.car.widget.ListItem;
import androidx.car.widget.ListItemAdapter;
import androidx.car.widget.ListItemProvider;
import androidx.car.widget.PagedListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A subclass of {@link Dialog} that is tailored for the car environment. This dialog can display a
 * title, body text, a fixed list of multiple choice items and up to two buttons -- a positive and
 * negative button. Multiple choice items use a checkbox to indicate selection.
 *
 * <p>Note that this dialog cannot be created with an empty list or without a positive button.
 */
public final class CarMultipleChoiceDialog extends Dialog {
    private final CharSequence mTitle;
    private final CharSequence mBodyText;
    private final CharSequence mPositiveButtonText;
    private final CharSequence mNegativeButtonText;
    private TextView mTitleView;
    private TextView mBodyTextView;

    private boolean[] mCheckedItems;

    private ListItemAdapter mAdapter;

    private PagedListView mList;

    @Nullable
    private final OnMultiChoiceClickListener mOnClickListener;

    /** Flag for if a touch on the scrim of the dialog will dismiss it. */
    private boolean mDismissOnTouchOutside;

    CarMultipleChoiceDialog(Context context, Builder builder) {
        super(context, CarDialogUtil.getDialogTheme(context));

        mTitle = builder.mTitle;
        mBodyText = builder.mSubtitle;
        mOnClickListener = builder.mOnClickListener;
        mPositiveButtonText = builder.mPositiveButtonText;
        mNegativeButtonText = builder.mNegativeButtonText;

        mCheckedItems = new boolean[builder.mItems.size()];

        initializeWithItems(builder.mItems);
    }

    @Override
    public void setTitle(CharSequence title) {
        // Ideally this method should be private; the dialog should only be modifiable through the
        // Builder. Unfortunately, this method is defined with the Dialog itself and is public.
        // So, throw an error if this method is ever called.
        throw new UnsupportedOperationException("Title should only be set from the Builder");
    }

    /**
     * @see Dialog#setCanceledOnTouchOutside(boolean)
     */
    @Override
    public void setCanceledOnTouchOutside(boolean cancel) {
        super.setCanceledOnTouchOutside(cancel);
        // Need to override this method to save the value of cancel.
        mDismissOnTouchOutside = cancel;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setContentView(R.layout.car_selection_dialog);

        // Ensure that the dialog takes up the entire window. This is needed because the scrollbar
        // needs to be drawn off the dialog.
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT;
        layoutParams.width = ViewGroup.LayoutParams.MATCH_PARENT;
        window.setAttributes(layoutParams);

        // The container for this dialog takes up the entire screen. As a result, need to manually
        // listen for clicks and dismiss the dialog when necessary.
        window.findViewById(R.id.container).setOnClickListener(v -> handleTouchOutside());

        initializeTitle();
        initializeBodyText();
        initializeList();
        initializeButtons();

        // Need to set this elevation listener last because the text and list need to be
        // initialized first.
        initializeTextElevationListener();
    }

    private void initializeButtons() {
        boolean isButtonPresent = false;
        Window window = getWindow();
        Button positiveButtonView = window.findViewById(R.id.positive_button);
        if (!TextUtils.isEmpty(mPositiveButtonText)) {
            isButtonPresent = true;
            positiveButtonView.setText(mPositiveButtonText);
            positiveButtonView.setOnClickListener(v -> {
                if (mOnClickListener != null) {
                    mOnClickListener.onClick(this,
                            Arrays.copyOf(mCheckedItems, mCheckedItems.length));
                }
                dismiss();
            });
        } else {
            positiveButtonView.setVisibility(View.GONE);
        }

        Button negativeButtonView = window.findViewById(R.id.negative_button);
        if (!TextUtils.isEmpty(mNegativeButtonText)) {
            isButtonPresent = true;
            negativeButtonView.setText(mNegativeButtonText);
            negativeButtonView.setOnClickListener(v -> dismiss());
        } else {
            negativeButtonView.setVisibility(View.GONE);
        }

        if (!isButtonPresent) {
            window.findViewById(R.id.button_panel).setVisibility(View.GONE);
        }
    }

    private void initializeTitle() {
        mTitleView = getWindow().findViewById(R.id.title);
        mTitleView.setText(mTitle);
        mTitleView.setVisibility(!TextUtils.isEmpty(mTitle) ? View.VISIBLE : View.GONE);
    }

    private void initializeBodyText() {
        mBodyTextView = getWindow().findViewById(R.id.bodyText);
        mBodyTextView.setText(mBodyText);
        mBodyTextView.setVisibility(!TextUtils.isEmpty(mBodyText) ? View.VISIBLE : View.GONE);
    }

    private void initializeTextElevationListener() {
        if (mTitleView.getVisibility() != View.GONE) {
            mList.addOnScrollListener(new DropShadowScrollListener(mTitleView));
        } else if (mBodyTextView.getVisibility() != View.GONE) {
            mList.addOnScrollListener(new DropShadowScrollListener(mBodyTextView));
        }
    }

    private void initializeList() {
        mList = getWindow().findViewById(R.id.list);
        mList.setMaxPages(PagedListView.UNLIMITED_PAGES);
        mList.setAdapter(mAdapter);
        mList.setDividerVisibilityManager(mAdapter);

        CarDialogUtil.setUpDialogList(mList, getWindow().findViewById(R.id.scrollbar));
    }

    /**
     * Handles if a touch has been detected outside of the dialog. If
     * {@link #mDismissOnTouchOutside} has been set, then the dialog will be dismissed.
     */
    private void handleTouchOutside() {
        if (mDismissOnTouchOutside) {
            dismiss();
        }
    }

    /**
     * Initializes {@link #mAdapter} to display the items in the given array by utilizing
     * {@link CheckBoxListItem}.
     */
    private void initializeWithItems(List<Item> items) {
        List<ListItem> listItems = new ArrayList<>();

        for (int i = 0; i < items.size(); i++) {
            listItems.add(createItem(/* selectionItem= */ items.get(i), /* position= */ i));
        }

        mAdapter = new ListItemAdapter(getContext(), new ListItemProvider.ListProvider(listItems));
    }

    /**
     * Creates the {@link CheckBoxListItem} that represents an item in the {@code
     * CarMultipleChoiceDialog}.
     *
     * @param {@link   Item} to display as a {@link CheckBoxListItem}.
     * @param position The position of the item in the list.
     */
    private CheckBoxListItem createItem(Item selectionItem, int position) {
        CheckBoxListItem item = new CheckBoxListItem(getContext());
        item.setTitle(selectionItem.mTitle);
        item.setBody(selectionItem.mBody);
        item.setShowCompoundButtonDivider(false);
        item.addViewBinder(vh -> {
            vh.getCompoundButton().setChecked(selectionItem.mIsChecked);
            vh.getCompoundButton().setOnCheckedChangeListener(
                    (buttonView, isChecked) -> {
                        mCheckedItems[position] = isChecked;
                    });
        });

        mCheckedItems[position] = selectionItem.mIsChecked;
        return item;
    }

    /**
     * A struct that holds data for a multiple choice item. A multiple choice item is a
     * combination of the item title and optional body text.
     */
    public static class Item {

        final CharSequence mTitle;
        final CharSequence mBody;
        final boolean mIsChecked;

        /**
         * Creates a Item.
         *
         * @param title   The title of the item. This value must be non-empty.
         * @param checked Whether the item is selected.
         */
        public Item(@NonNull CharSequence title, boolean checked) {
            this(title,  /* body= */ null, checked);
        }

        /**
         * Creates a Item.
         *
         * @param title   The title of the item. This value must be non-empty.
         * @param body    The secondary body text of the item.
         * @param checked Whether the item is selected.
         */
        public Item(@NonNull CharSequence title, @Nullable CharSequence body, boolean checked) {
            if (TextUtils.isEmpty(title)) {
                throw new IllegalArgumentException("Title cannot be empty.");
            }

            mTitle = title;
            mBody = body;
            mIsChecked = checked;
        }
    }

    /**
     * Interface used to allow the creator the dialog to run some code when the selection of items
     * is confirmed with the positive button of {@link CarMultipleChoiceDialog}.
     */
    public interface OnMultiChoiceClickListener {
        /**
         * This method will be invoked when the positive button of the dialog is clicked.
         *
         * @param dialog       the dialog where the selection was made
         * @param checkedItems specifies which items are checked.
         */
        void onClick(@NonNull DialogInterface dialog, @NonNull boolean[] checkedItems);
    }

    /**
     * Builder class that can be used to create a {@link CarMultipleChoiceDialog} by configuring
     * the options for the list and behavior of the dialog.
     */
    public static final class Builder {
        private final Context mContext;

        CharSequence mTitle;
        CharSequence mSubtitle;
        List<Item> mItems;
        OnMultiChoiceClickListener mOnClickListener;

        CharSequence mPositiveButtonText;
        CharSequence mNegativeButtonText;

        private boolean mCancelable = true;
        private OnCancelListener mOnCancelListener;
        private OnDismissListener mOnDismissListener;

        /**
         * Creates a new instance of the {@code Builder}.
         *
         * @param context The {@code Context} that the dialog is to be created in.
         */
        public Builder(@NonNull Context context) {
            mContext = context;
        }

        /**
         * Sets the title of the dialog to be the given string resource.
         *
         * @param titleId The resource id of the string to be used as the title.
         *                Text style will be retained.
         * @return This {@link Builder} object to allow for chaining of calls.
         */
        @NonNull
        public Builder setTitle(@StringRes int titleId) {
            mTitle = mContext.getText(titleId);
            return this;
        }

        /**
         * Sets the title of the dialog for be the given string.
         *
         * @param title The string to be used as the title.
         * @return This {@link Builder} object to allow for chaining of calls.
         */
        @NonNull
        public Builder setTitle(@Nullable CharSequence title) {
            mTitle = title;
            return this;
        }

        /**
         * Sets the body text of the dialog to be the given string resource.
         *
         * @param bodyTextId The resource id of the string to be used as the body.
         *                   Text style will be retained.
         * @return This {@link Builder} object to allow for chaining of calls.
         */
        @NonNull
        public Builder setBody(@StringRes int bodyTextId) {
            mSubtitle = mContext.getText(bodyTextId);
            return this;
        }

        /**
         * Sets the bodyText of the dialog for be the given string.
         *
         * @param bodyText The string to be used as the body.
         * @return This {@link Builder} object to allow for chaining of calls.
         */
        @NonNull
        public Builder setBody(@Nullable CharSequence bodyText) {
            mSubtitle = bodyText;
            return this;
        }

        /**
         * Sets the items that should appear in the list.
         *
         * <p>The provided list of items cannot be {@code null} or empty. Passing an empty list
         * to this method will throw can exception.
         *
         * @param items The items that will appear in the list.
         * @return This {@link Builder} object to allow for chaining of calls.
         */
        @NonNull
        public Builder setItems(@NonNull List<Item> items) {
            if (items.size() == 0) {
                throw new IllegalArgumentException("Provided list of items cannot be empty.");
            }

            mItems = items;
            return this;
        }

        /**
         * Configure the dialog to include a positive button.
         *
         * @param textId          The resource id of the text to display in the positive button.
         * @param onClickListener The listener that will be notified of item selection.
         * @return This {@link Builder} object to allow for chaining of calls to set methods.
         */
        @NonNull
        public Builder setPositiveButton(@StringRes int textId,
                @NonNull OnMultiChoiceClickListener onClickListener) {
            setPositiveButton(mContext.getText(textId), onClickListener);
            return this;
        }

        /**
         * Configure the dialog to include a positive button.
         *
         * @param text            The text to display in the positive button.
         * @param onClickListener The listener that will be notified of a selection.
         * @return This {@link Builder} object to allow for chaining of calls to set methods.
         */
        @NonNull
        public Builder setPositiveButton(@NonNull CharSequence text,
                @NonNull OnMultiChoiceClickListener onClickListener) {
            mPositiveButtonText = text;
            mOnClickListener = onClickListener;
            return this;
        }

        /**
         * Configure the dialog to include a negative button.
         *
         * @param textId The resource id of the text to display in the negative button.
         * @return This {@link Builder} object to allow for chaining of calls to set methods.
         */
        @NonNull
        public Builder setNegativeButton(@StringRes int textId) {
            mNegativeButtonText = mContext.getText(textId);
            return this;
        }

        /**
         * Configure the dialog to include a negative button.
         *
         * @param text The text to display in the negative button.
         * @return This {@link Builder} object to allow for chaining of calls to set methods.
         */
        @NonNull
        public Builder setNegativeButton(@NonNull CharSequence text) {
            mNegativeButtonText = text;
            return this;
        }

        /**
         * Sets whether the dialog is cancelable or not. Default is {@code true}.
         *
         * @return This {@link Builder} object to allow for chaining of calls.
         */
        @NonNull
        public Builder setCancelable(boolean cancelable) {
            mCancelable = cancelable;
            return this;
        }

        /**
         * Sets the callback that will be called if the dialog is canceled.
         *
         * <p>Even in a cancelable dialog, the dialog may be dismissed for reasons other than
         * being canceled or one of the supplied choices being selected.
         * If you are interested in listening for all cases where the dialog is dismissed
         * and not just when it is canceled, see {@link #setOnDismissListener(OnDismissListener)}.
         *
         * @param onCancelListener The listener to be invoked when this dialog is canceled.
         * @return This {@link Builder} object to allow for chaining of calls.
         * @see #setCancelable(boolean)
         * @see #setOnDismissListener(OnDismissListener)
         */
        @NonNull
        public Builder setOnCancelListener(@Nullable OnCancelListener onCancelListener) {
            mOnCancelListener = onCancelListener;
            return this;
        }

        /**
         * Sets the callback that will be called when the dialog is dismissed for any reason.
         *
         * @return This {@link Builder} object to allow for chaining of calls.
         */
        @NonNull
        public Builder setOnDismissListener(@Nullable OnDismissListener onDismissListener) {
            mOnDismissListener = onDismissListener;
            return this;
        }

        /**
         * Creates a {@link CarMultipleChoiceDialog}, which is returned as a {@link Dialog}, with
         * the arguments supplied to this {@link Builder}.
         *
         * <p>If {@link #setItems(List)} is never called, then calling this method
         * will throw an exception.
         *
         * <p>Calling this method does not display the dialog. Utilize this dialog within a
         * {@link androidx.fragment.app.DialogFragment} to show the dialog.
         */
        @NonNull
        public Dialog create() {
            // Check that the dialog was created with a list of items.
            if (mItems == null || mItems.size() == 0) {
                throw new IllegalStateException(
                        "CarMultipleChoiceDialog must be created with a non-empty list.");
            }

            if (TextUtils.isEmpty(mPositiveButtonText)) {
                throw new IllegalStateException(
                        "CarMultipleChoiceDialog cannot be created without a positive button.");
            }

            CarMultipleChoiceDialog dialog = new CarMultipleChoiceDialog(mContext,
                    /* builder= */ this);

            dialog.setCancelable(mCancelable);
            dialog.setCanceledOnTouchOutside(mCancelable);
            dialog.setOnCancelListener(mOnCancelListener);
            dialog.setOnDismissListener(mOnDismissListener);

            return dialog;
        }
    }
}

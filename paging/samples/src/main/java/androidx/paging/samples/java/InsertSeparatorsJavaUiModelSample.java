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

package androidx.paging.samples.java;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.paging.PagingData;

/**
 * NOTE - MANUALLY COPIED SAMPLE
 *
 * Since @sample from kdoc doesn't support Java, this code must manually kept in sync with
 * the @JvmStatic `PagingData.insertSeparators` method
 */
@SuppressWarnings({"unused", "WeakerAccess"})
class InsertSeparatorsJavaUiModelSample {
    @NonNull
    public PagingData<Item> itemPagingData = PagingData.empty();

    @SuppressWarnings("unused")
    public void insertSeparatorsSample() {
        /*
         * Create letter separators in an alphabetically sorted list of Items, with UiModel objects.
         *
         * For example, if the input is (each an `Item`):
         *     "apple", "apricot", "banana", "carrot"
         *
         * The operator would output a list of UiModels corresponding to:
         *     "A", "apple", "apricot", "B", "banana", "C", "carrot"
         */
        // first convert items in stream to UiModel.Item
        PagingData<UiModel.ItemModel> itemModelPagingData =
                itemPagingData.map(UiModel.ItemModel::new);

        // Now insert UiModel.Separators, which makes the PagingData of generic type UiModel
        PagingData<UiModel> pagingDataWithSeparators = PagingData.insertSeparators(
                itemModelPagingData,
                (@Nullable UiModel.ItemModel before, @Nullable UiModel.ItemModel after) -> {
                    if (after != null
                            && (before == null
                            || before.item.label.charAt(0) != after.item.label.charAt(0))) {
                        // separator - after is first item that starts with its first letter
                        return new UiModel.SeparatorModel(
                                Character.toUpperCase(after.item.label.charAt(0)));
                    } else {
                        // no separator - either end of list, or first letters of items are the same
                        return null;
                    }
                });
    }
}
